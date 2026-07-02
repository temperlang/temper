package lang.temper.frontend.typestage

import lang.temper.common.KBitSet
import lang.temper.common.putMultiList
import lang.temper.common.removeMatching
import lang.temper.common.subListToEnd
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.NominalType
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.VisibleMemberShape
import lang.temper.type2.DefinedType
import lang.temper.type2.Descriptor
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.TypeContext2
import lang.temper.type2.TypeParamRef
import lang.temper.type2.bindingMap
import lang.temper.type2.mapType
import kotlin.math.min

internal fun filterOutMaskedMembers(
    membersGrouped: MutableMap<NominalType, MutableSet<VisibleMemberShape>>,
) {
    val masked = mutableSetOf<TemperName>()
    for (members in membersGrouped.values) {
        for (member in members) {
            for (memberOverride in (member.overriddenMembers ?: continue)) {
                masked.add(memberOverride.superTypeMember.name)
            }
        }
    }
    for (members in membersGrouped.values) {
        members.retainAll { it.name !in masked }
    }
    membersGrouped.removeMatching { it.value.isEmpty() }
}

/**
 * If we have two, non-conflicting, inherited definitions, they can unnecessarily
 * confuse the TypeSolver.
 *
 *      interface I<X> { f(): X }
 *      interface J extends I<String> {}    // J.f has return type String
 *      interface K { f(): String { "Yep" } }
 *
 *      class C extends J, K {}
 *
 * Given these two alternative signatures, `fn (K): String` and `fn (I<String>): String`
 * the type solver has no way to pick between the two leading to an unresolvable overload
 * decision.
 *
 * TODO: Maybe pre-typing analyze these and synthesize a method that uses `super`,
 * so that the Typer never encounters this in code that does not contain other errors.
 *
 *      class C extends J, K {
 *     +  public f(): String { K.super.f() }
 *      }
 *
 * In this case, we have the following type hierarchy above (Top types at top) C.
 *
 *               I<X>          depth = 2
 *                 \
 *                  J   K      depth = 1
 *                   \ /
 *                    C        depth = 0
 *
 * We can prefer K's implementation of .f() to I's because it is at a lower depth
 * from their common super-type.
 *
 * But to figure out that the two implementations, `fn (I<X>): X` and `fn (K): String`
 * are redundant, that one can be eliminated without removing options that the type
 * solver might need, we need to first examine their signatures in the context of
 * the common subtype, C.
 *
 * Every C is-a I<String>.
 * Every C is-a K.
 *
 * Using those two facts, we can contextualize the descriptors of the f methods to
 *
 * - `fn (I<String>): String` from I<K>
 * - `fn (K): String` from K
 *
 * Then we can compare the signatures for equality (ignoring the `this` arg types),
 * and since they're equivalent, drop any but the shallowest tier ones.
 */
internal fun filterOutDeeperMembers(
    membersGrouped: MutableMap<NominalType, MutableSet<VisibleMemberShape>>,
    inheritanceDepth: Map<NominalType, Int>,
    typeContext: TypeContext2,
) {
    if (membersGrouped.size < 2) { return } // Nothing to compare
    val groupedByWord = mutableMapOf<Symbol, MutableList<Pair<NominalType, VisibleMemberShape>>>()
    for ((group, members) in membersGrouped) {
        for (member in members) {
            groupedByWord.putMultiList(member.symbol, group to member)
        }
    }
    val minDepthThisTypeShapes by lazy {
        val minDepth = inheritanceDepth.values.min()
        inheritanceDepth.keys.mapNotNull {
            if (inheritanceDepth[it] == minDepth) {
                it.definition as? TypeShape
            } else {
                null
            }
        }
    }
    for (members in groupedByWord.values) {
        val n = members.size
        if (n <= 1) { continue }
        // filter out deeper ones that have the same sig, projected from a common this variant.
        val eliminated = KBitSet()
        for (i in 0..<n) {
            val (aThisType, a) = members[i]
            val aDepth = inheritanceDepth.getValue(aThisType)
            val aDesc = a.descriptor ?: continue
            for (j in i + 1..<n) {
                val (bThisType, b) = members[j]
                val bDesc = b.descriptor ?: continue
                val bDepth = inheritanceDepth.getValue(bThisType)
                val aIsShallower = when {
                    aDepth == bDepth -> continue
                    else -> aDepth < bDepth
                }
                val commonSubTypeShape = minDepthThisTypeShapes.firstOrNull {
                    aThisType.definition.name in it.rawSuperTypeNames &&
                        bThisType.definition.name in it.rawSuperTypeNames
                } ?: continue
                // Now that we have a this-type that is a super type of both, project the
                // descriptors into that
                val commonSubType = MkType2(commonSubTypeShape).actuals(
                    commonSubTypeShape.formals.map { MkType2(it).get() },
                ).get()
                val stt = typeContext.superTypeTreeOf(commonSubType)
                val aProjected = stt[aThisType.definition].firstOrNull() as? DefinedType ?: continue
                val bProjected = stt[bThisType.definition].firstOrNull() as? DefinedType ?: continue
                val aBindings = aProjected.bindingMap
                val bBindings = bProjected.bindingMap
                // Now we get A's descriptor in the context of the common subtype, and similarly B's descriptor.
                val aDescInContext = aDesc.mapType(aBindings)
                val bDescInContext = bDesc.mapType(bBindings)
                // Assuming type formals are equivalent, and ignoring this args, are they the same.
                if (equivalentForOverridePurposes(aDescInContext, bDescInContext)) {
                    if (aIsShallower) {
                        eliminated[j] = true
                    } else {
                        eliminated[i] = true
                        break
                    }
                }
            }
        }
        var k = 0
        while (true) {
            val next = eliminated.nextSetBit(k)
            if (next < 0) { break }
            val (thisType, member) = members[next]
            k = next + 1
            val group = membersGrouped[thisType]
            group?.remove(member)
            if (group?.isEmpty() == true) {
                membersGrouped.remove(thisType)
            }
        }
    }
}

internal fun equivalentForOverridePurposes(
    a: Descriptor,
    b: Descriptor,
    equivalences: Map<TypeFormal, TypeFormal>? = null,
): Boolean {
    if (a == b) {
        return true
    }
    if (a is Signature2 && b is Signature2) {
        val aReqs = if (a.hasThisFormal) { a.requiredInputTypes.subListToEnd(1) } else { a.requiredInputTypes }
        val bReqs = if (b.hasThisFormal) { b.requiredInputTypes.subListToEnd(1) } else { b.requiredInputTypes }
        if (aReqs.size != bReqs.size) { return false }
        if (a.typeFormals.size != b.typeFormals.size) { return false }
        if (a.optionalInputTypes.size != b.optionalInputTypes.size) { return false }
        if ((a.restInputsType != null) != (b.restInputsType != null)) { return false }
        val formalEquivalences = buildMap {
            for ((x, y) in a.typeFormals zip b.typeFormals) {
                this[x] = y
                this[y] = x
            }
        }
        if (!equivalentForOverridePurposes(a.returnType2, b.returnType2, formalEquivalences)) {
            return false
        }
        for (i in aReqs.indices) {
            if (!equivalentForOverridePurposes(aReqs[i], bReqs[i], formalEquivalences)) {
                return false
            }
        }
        for (i in a.optionalInputTypes.indices) {
            if (
                !equivalentForOverridePurposes(
                    a.optionalInputTypes[i], b.optionalInputTypes[i], formalEquivalences,
                )
            ) {
                return false
            }
        }
        if (
            a.restInputsType != null &&
            !equivalentForOverridePurposes(a.restInputsType!!, b.restInputsType!!, formalEquivalences)
        ) {
            return false
        }
        return true
    }
    if (a is TypeParamRef && b is TypeParamRef && equivalences != null && a.nullity == b.nullity) {
        return equivalences[a.definition] == b.definition ||
            equivalences[b.definition] == a.definition
    }
    return false
}
