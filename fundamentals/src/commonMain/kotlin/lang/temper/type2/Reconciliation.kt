package lang.temper.type2

import lang.temper.common.buildSetMultimap
import lang.temper.common.firstOrNullAs
import lang.temper.common.partitionByType
import lang.temper.common.putMultiSet
import lang.temper.format.OutToks
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.Positioned
import lang.temper.type.Abstractness
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Variance
import lang.temper.type.Variance.Contravariant
import lang.temper.type.Variance.Covariant
import lang.temper.type.Variance.Invariant
import lang.temper.type.WellKnownTypes.neverTypeDefinition
import lang.temper.type2.BoundKind.Common
import lang.temper.type2.BoundKind.Lower
import lang.temper.type2.BoundKind.Upper
import lang.temper.type2.Nullity.NonNull
import lang.temper.type2.Nullity.OrNull
import kotlin.math.min

internal sealed class Reconciliation : TokenSerializable {
    abstract val kind: BoundKind
    protected abstract val bounded: TypeBoundary
    abstract val bound: TypeBoundary

    override fun renderTo(tokenSink: TokenSink) {
        val left: TokenSerializable
        val op: TokenSerializable
        val right: TokenSerializable
        when (kind) {
            Upper -> {
                left = bounded
                op = subTypeOrEqualTok
                right = bound
            }
            Lower -> {
                left = bound
                op = subTypeOrEqualTok
                right = bounded
            }
            Common -> {
                left = bounded
                op = OutToks.eqEq
                right = bound
            }
        }
        tokenSink.emit(OutToks.leftParen)
        tokenSink.word(displayNameForVariant)
        left.renderTo(tokenSink)
        op.renderTo(tokenSink)
        right.renderTo(tokenSink)
        tokenSink.emit(OutToks.rightParen)
    }

    protected abstract val displayNameForVariant: String
}

/** A variable reconciled with a type/partial type. */
internal data class PartialTypeReconciliation(
    val typeVar: TypeVar,
    override val bound: TypeOrPartialType,
    override val kind: BoundKind,
) : Reconciliation() {
    override val bounded: TypeBoundary
        get() = typeVar
    override val displayNameForVariant: String
        get() = "PartialTypeReconciliation"
}

/** A variable reconciled with a variable. */
internal data class VarReconciliation(
    @Suppress("RedundantVisibilityModifier") // public for tuple destructuring visibility
    public override val bounded: TypeVarRef,
    override val bound: TypeVarRef,
    override val kind: BoundKind,
) : Reconciliation() {
    override val displayNameForVariant: String
        get() = "VarReconciliation"
}

/**
 * Try to find more complete bounds.
 *
 * If we have two partial bounds for the same variable,
 * `Map<a1, Int>` and `Map<String, a2>`,
 * we can infer that:
 *
 * - `a1` is bounded on some side by `String`
 * - `a2` is bounded on some side by `Int`
 * - the variable for which we received bounds is bounded by `Map<String, Int>`.
 */
internal fun reconcilePartialTypes(
    // Bounds for the same Boundable
    lowers: Collection<TypeLike>,
    common: Collection<TypeLike>,
    uppers: Collection<TypeLike>,
    typeContext: TypeContext2,
): Set<Reconciliation> = buildSet {
    // By contrasting lowers with uppers, we can find relationships like the below:
    //     a <: x <: MyType;
    //     MyType <: x <: a
    // Comparing lower bounds with each other or upper bounds with each other does
    // not yield useful info because we don't know which way the <: goes.
    //
    // For invariant type parameters, we can find relationships within bound sets.
    //
    // We do this in several steps.
    //
    // 1. Split bound sets among variables and non-variables.
    // 2. Group non-variables by definitions.
    // 3. Compare non-vars across bound sets: lower to upper being careful to
    //    project lower bounds to super-types where applicable.
    //    Similarly lower to common, common to upper
    // 4. Compare non-vars within bound sets
    // 5. Relate common vars to any full common bound.
    // 6. If there is a full bound in lower or common, relate each upper var to it,
    //    or failing that relate each partial common and lower bound to each upper var.
    // 7. Similarly, relate lower vars to any full bound from common+upper,
    //    or failing that, to each partial bound from those
    // 8. If no full bound was found in 5, similarly, relate common vars to any full
    //    lower or upper bound or failing that to partial bounds

    // Step 1
    val (uVarsHi, uOtherHi) =
        uppers.partitionByType<TypeLike, TypeVarRef, TypeOrPartialType>()
    val (cVarsHi, cOtherHi) =
        common.partitionByType<TypeLike, TypeVarRef, TypeOrPartialType>()
    val (lVarsHi, lOtherHi) =
        lowers.partitionByType<TypeLike, TypeVarRef, TypeOrPartialType>()

    // Step 2
    val uByDefHi = groupedByShape(uOtherHi)
    val cByDefHi = groupedByShape(cOtherHi)
    val lByDefHi = groupedByShape(lOtherHi)
    val (uByDefLo, _) = groupedByShapeLo(uByDefHi, uVarsHi)
    val (cByDefLo, cVarsLo) = groupedByShapeLo(cByDefHi, cVarsHi)
    val (lByDefLo, lVarsLo) = groupedByShapeLo(lByDefHi, lVarsHi)

    // Step 3
    val step3Pairs = listOf(
        (lByDefLo to Lower) to (cByDefHi to Common),
        (lByDefLo to Lower) to (uByDefHi to Upper),
        (cByDefLo to Common) to (uByDefHi to Upper),
    )
    for ((defGroupKind, higherDefGroupKind) in step3Pairs) {
        val (defGroup, defKind) = defGroupKind
        val (higherDefGroup, higherDefKind) = higherDefGroupKind
        for ((def, ts) in defGroup) {
            for (t in ts) {
                val superTypeTree = lazy { typeContext.superTypeTreeOf(t) }
                for ((hDef, hts) in higherDefGroup) {
                    if (def == hDef) {
                        val tb = t.bindings
                        for (ht in hts) {
                            val htb = ht.bindings
                            reconcileBindings(def, tb, defKind, htb, higherDefKind, this@buildSet, typeContext)
                        }
                    } else {
                        // Project type to super type
                        val lts = superTypeTree.value[hDef]
                        for (lt in lts) {
                            val ltb = lt.bindings
                            for (ht in hts) {
                                val htb = ht.bindings
                                reconcileBindings(def, ltb, defKind, htb, higherDefKind, this@buildSet, typeContext)
                            }
                        }
                    }
                }
            }
        }
    }
    // Step 4
    val step4Lists = listOf(
        uByDefLo.values.flatten() to Upper,
        cByDefLo.values.flatten() to Common,
        lByDefLo.values.flatten() to Lower,
    )
    for ((otherList, k) in step4Lists) {
        for (i in otherList.indices) {
            val a = otherList[i]
            val aDef = a.definition as? TypeShape ?: continue
            for (j in (i + 1) until otherList.size) {
                val b = otherList[j]
                val bDef = b.definition as? TypeShape ?: continue
                if (aDef == bDef) {
                    reconcileBindings(aDef, a.bindings, k, b.bindings, k, this@buildSet, typeContext)
                } else {
                    var aTs = listOf(a)
                    var bTs = listOf(b)
                    val def = if (aDef.inheritanceDepth > bDef.inheritanceDepth) {
                        aTs = typeContext.superTypeTreeOf(a)[bDef]
                        bDef
                    } else {
                        bTs = typeContext.superTypeTreeOf(b)[aDef]
                        aDef
                    }
                    for (aT in aTs) {
                        for (bT in bTs) {
                            reconcileBindings(def, aT.bindings, k, bT.bindings, k, this@buildSet, typeContext)
                        }
                    }
                }
            }
        }
    }
    // Step 5
    val fullCBound = cOtherHi.firstOrNullAs { _: Type2 -> true }
    if (fullCBound != null) {
        for (cVar in cVarsHi) {
            addReconciliation(cVar, fullCBound, Common, typeContext)
        }
    } else {
        for (cVar in cVarsHi) {
            for (bound in cOtherHi) {
                addReconciliation(cVar, bound, Common, typeContext)
            }
        }
    }

    // Step 6-8
    val shortCList = fullCBound?.let { listOf(it) }
    val lOtherLo = lazy {
        if (lByDefLo === lByDefHi) lOtherHi else lByDefLo.values.flatten()
    }
    val cOtherLo = lazy {
        if (cByDefLo === cByDefHi) cOtherHi else cByDefLo.values.flatten()
    }
    val step68Items = listOfNotNull(
        Triple(emptyList(), uVarsHi, shortCList ?: (lOtherLo.value + cOtherLo.value)), // Step 5
        Triple(shortCList ?: (uOtherHi + cOtherHi), lVarsLo, emptyList()), // Step 6
        if (fullCBound == null) {
            Triple(uOtherHi, cVarsLo, emptyList()) // Step 7
        } else {
            null
        },
        if (fullCBound == null) {
            Triple(emptyList(), cVarsHi, lOtherLo.value) // Step 7
        } else {
            null
        },
    )
    for ((above, vars, below) in step68Items) {
        val aboveFull = above.firstOrNullAs { _: Type2 -> true }
        if (aboveFull != null) {
            for (v in vars) {
                addReconciliation(v, aboveFull, Upper, typeContext)
            }
        } else {
            for (v in vars) {
                for (t in above) {
                    addReconciliation(v, t, Upper, typeContext)
                }
            }
        }
        val belowFull = below.firstOrNullAs { _: Type2 -> true }
        if (belowFull != null) {
            for (v in vars) {
                addReconciliation(v, belowFull, Lower, typeContext)
            }
        } else {
            for (v in vars) {
                for (t in below) {
                    addReconciliation(v, t, Lower, typeContext)
                }
            }
        }
    }
}

private fun reconcileBindings(
    defn: TypeDefinition,
    aBindings: List<TypeLike>,
    aKind: BoundKind,
    bBindings: List<TypeLike>,
    bKind: BoundKind,
    out: MutableCollection<Reconciliation>,
    typeContext: TypeContext2,
) {
    val formals = defn.formals
    val arity = min(min(formals.size, aBindings.size), bBindings.size)

    for (i in 0 until arity) {
        val tf = formals[i]
        val v = tf.variance
        reconcileBinding(aBindings[i], aKind, bBindings[i], bKind, v, out, typeContext)
    }
}

private fun reconcileBinding(
    a: TypeLike,
    aKind: BoundKind,
    b: TypeLike,
    bKind: BoundKind,
    v: Variance,
    out: MutableCollection<Reconciliation>,
    typeContext: TypeContext2,
) {
    when {
        a is TypeVarRef && b is TypeVarRef ->
            if (a != b) {
                kindFor(aKind, bKind, v)?.let { k ->
                    out.add(VarReconciliation(a, b, k))
                }
            }
        a is TypeVarRef && b is TypeOrPartialType ->
            kindFor(aKind, bKind, v)?.let { k ->
                reconcileVarWithType(a, b, k, out, typeContext)
            }
        a is TypeOrPartialType && b is TypeVarRef ->
            kindFor(bKind, aKind, v)?.let { k ->
                reconcileVarWithType(b, a, k.reverse(), out, typeContext)
            }
        a is TypeOrPartialType && b is TypeOrPartialType ->
            if (a.definition == b.definition) {
                reconcileBindings(a.definition, a.bindings, aKind, b.bindings, bKind, out, typeContext)
            } else if ( // Reconcile Foo<X> with Never<Foo<X>>
                a.definition == neverTypeDefinition || b.definition == neverTypeDefinition
            ) {
                // They can't both be never because of the branch above.
                val neverType: TypeOrPartialType
                val ntKind: BoundKind
                val notNever: TypeOrPartialType
                val nnKind: BoundKind
                if (a.definition == neverTypeDefinition) {
                    neverType = a
                    notNever = b
                    ntKind = aKind
                    nnKind = bKind
                } else {
                    neverType = b
                    notNever = a
                    ntKind = bKind
                    nnKind = aKind
                }

                if (neverType.bindings.size == 1) {
                    val nv = neverTypeDefinition.formals[0].variance
                    reconcileBinding(neverType.bindings[0], ntKind, notNever, nnKind, nv, out, typeContext)
                }
            }
    }
}

private fun reconcileVarWithType(
    a: TypeVarRef,
    b: TypeOrPartialType,
    k: BoundKind,
    out: MutableCollection<Reconciliation>,
    typeContext: TypeContext2,
) {
    out.addReconciliation(a, b, k, typeContext)
}

private fun MutableCollection<Reconciliation>.addReconciliation(
    varRef: TypeVarRef,
    bound: TypeOrPartialType,
    boundKind: BoundKind,
    typeContext: TypeContext2,
) {
    when (varRef.nullity) {
        NonNull -> addVarReconciliation(varRef.typeVar, bound, boundKind)
        OrNull -> when (boundKind) {
            // V? <: B  ->  V <: B  because  V <: V? <: B
            Upper -> addVarReconciliation(varRef.typeVar, bound, boundKind)
            // B <: V?  is ok when B does not accept null.
            Lower -> {
                val boundNonNull = bound.withNullity(NonNull)
                if (!typeContext.admitsNull(boundNonNull)) {
                    addVarReconciliation(varRef.typeVar, boundNonNull, Lower)
                }
            }
            // V? == B  ->  B <: V <: B?
            //     V? == B  <->  B <: V? <: B
            //     B <: B?   ->  B <: V? <: B?
            // If B is not nullable, then B <: V <: B?
            Common -> {
                val boundNonNull = bound.withNullity(NonNull)
                if (!typeContext.admitsNull(boundNonNull)) {
                    // B <: V
                    addVarReconciliation(varRef.typeVar, boundNonNull, Lower)
                }
                // V <: B
                addVarReconciliation(varRef.typeVar, bound.withNullity(OrNull), Upper)
            }
        }
    }
}

private fun MutableCollection<Reconciliation>.addVarReconciliation(
    typeVar: TypeVar,
    bound: TypeOrPartialType,
    boundKind: BoundKind,
) {
    val bDef = bound.definition
    var k = boundKind
    if (k == Upper && bDef is TypeShape &&
        bDef.abstractness == Abstractness.Concrete &&
        bDef.formals.all { it.variance == Invariant }
    ) {
        // TODO: Can we infer a tighter bound when we know b's TypeShape
        // has no subinterfaces or classes?

        // We can't infer a common bound to MyClass, because Never<MyClass> would be a valid
        // common bound.
        // Unless of course the class is `Never`.
        // If the class is not `Never`, we can infer an additional upper bound.
        //     MyClass <: a  ->  a <: Never<MyClass>
        if (bDef == neverTypeDefinition) {
            k = Common
        } else {
            val neverB = PartialType.from(
                neverTypeDefinition, listOf(bound), NonNull,
                (bound as? Positioned)?.pos,
            )
            add(PartialTypeReconciliation(typeVar, neverB, Lower))
        }
    }
    add(PartialTypeReconciliation(typeVar, bound, k))
}

private const val AKF = 1 // A kind factor
private const val BKF = 3 // B kind factor
private const val VF = 9 // Variance factor
private const val N = 27
private fun kindFor(aKind: BoundKind, bKind: BoundKind, v: Variance): BoundKind? =
    kindLookupTable[aKind.ordinal * AKF + bKind.ordinal * BKF + v.ordinal * VF]

@Suppress("KotlinConstantConditions")
private val kindLookupTable = Array<BoundKind?>(N) { null }.also {
    check(
        BKF == AKF * BoundKind.entries.size &&
            VF == BKF * BoundKind.entries.size &&
            N == VF * Variance.entries.size,
    )
    it[Lower.ordinal * AKF + Lower.ordinal * BKF + Covariant.ordinal * VF] = null
    it[Lower.ordinal * AKF + Lower.ordinal * BKF + Invariant.ordinal * VF] = Common
    it[Lower.ordinal * AKF + Lower.ordinal * BKF + Contravariant.ordinal * VF] = null
    it[Lower.ordinal * AKF + Common.ordinal * BKF + Covariant.ordinal * VF] = Upper
    it[Lower.ordinal * AKF + Common.ordinal * BKF + Invariant.ordinal * VF] = Common
    it[Lower.ordinal * AKF + Common.ordinal * BKF + Contravariant.ordinal * VF] = Lower
    it[Lower.ordinal * AKF + Upper.ordinal * BKF + Covariant.ordinal * VF] = Upper
    it[Lower.ordinal * AKF + Upper.ordinal * BKF + Invariant.ordinal * VF] = Common
    it[Lower.ordinal * AKF + Upper.ordinal * BKF + Contravariant.ordinal * VF] = Lower
    it[Common.ordinal * AKF + Lower.ordinal * BKF + Covariant.ordinal * VF] = Upper
    it[Common.ordinal * AKF + Lower.ordinal * BKF + Invariant.ordinal * VF] = Common
    it[Common.ordinal * AKF + Lower.ordinal * BKF + Contravariant.ordinal * VF] = Lower
    it[Common.ordinal * AKF + Common.ordinal * BKF + Covariant.ordinal * VF] = Common
    it[Common.ordinal * AKF + Common.ordinal * BKF + Invariant.ordinal * VF] = Common
    it[Common.ordinal * AKF + Common.ordinal * BKF + Contravariant.ordinal * VF] = Common
    it[Common.ordinal * AKF + Upper.ordinal * BKF + Covariant.ordinal * VF] = Upper
    it[Common.ordinal * AKF + Upper.ordinal * BKF + Invariant.ordinal * VF] = Common
    it[Common.ordinal * AKF + Upper.ordinal * BKF + Contravariant.ordinal * VF] = Lower
    it[Upper.ordinal * AKF + Lower.ordinal * BKF + Covariant.ordinal * VF] = Upper
    it[Upper.ordinal * AKF + Lower.ordinal * BKF + Invariant.ordinal * VF] = Common
    it[Upper.ordinal * AKF + Lower.ordinal * BKF + Contravariant.ordinal * VF] = Lower
    it[Upper.ordinal * AKF + Common.ordinal * BKF + Covariant.ordinal * VF] = Upper
    it[Upper.ordinal * AKF + Common.ordinal * BKF + Invariant.ordinal * VF] = Common
    it[Upper.ordinal * AKF + Common.ordinal * BKF + Contravariant.ordinal * VF] = Lower
    it[Upper.ordinal * AKF + Upper.ordinal * BKF + Covariant.ordinal * VF] = null
    it[Upper.ordinal * AKF + Upper.ordinal * BKF + Invariant.ordinal * VF] = Common
    it[Upper.ordinal * AKF + Upper.ordinal * BKF + Contravariant.ordinal * VF] = null
}

/**
 * Like `ts.groupBy { it.definition }` but explodes any type formals out into
 * their super types so they can be resolved against other super types.
 */
private fun groupedByShape(ts: Iterable<TypeOrPartialType>): Map<TypeShape, Set<TypeOrPartialType>> =
    buildSetMultimap {
        val exploded = mutableSetOf<TypeFormal>()
        fun put(t: TypeOrPartialType) {
            when (t) {
                is TypeParamRef -> if (t.definition !in exploded) {
                    exploded.add(t.definition)
                    t.definition.superTypes.forEach {
                        put(hackMapOldStyleToNew(it))
                    }
                }
                is DefinedType -> putMultiSet(t.definition, t)
                is PartialType -> putMultiSet(t.definition, t)
            }
        }
        ts.forEach(::put)
    }

private fun groupedByShapeLo(
    byDef: Map<TypeShape, Set<TypeOrPartialType>>,
    vars: List<TypeVarRef>,
): Pair<Map<TypeShape, Set<TypeOrPartialType>>, List<TypeVarRef>> {
    if (neverTypeDefinition !in byDef) {
        return byDef to vars
    }

    val varsLo = vars.toMutableSet()
    val byDefLo = mutableMapOf<TypeShape, MutableSet<TypeOrPartialType>>()
    for ((shape, ts) in byDef) {
        byDefLo.getOrPut(shape) { mutableSetOf() }
            .addAll(ts)
        if (shape == neverTypeDefinition) {
            val bs = buildList {
                for (t in ts) {
                    if (t.bindings.size == 1) {
                        when (val b = t.bindings[0]) {
                            is TypeOrPartialType -> add(b)
                            is TypeVarRef -> varsLo.add(b)
                        }
                    }
                }
            }
            for ((bShape, bs) in groupedByShape(bs)) {
                byDefLo.getOrPut(bShape) { mutableSetOf() }.addAll(bs)
            }
        }
    }
    return Pair(
        byDefLo.mapValues { (_, v) -> v.toSet() },
        varsLo.toList(),
    )
}
