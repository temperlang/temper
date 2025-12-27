package lang.temper.frontend.typestage

import lang.temper.common.Log
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.unknownPos
import lang.temper.type.AndType
import lang.temper.type.FunctionType
import lang.temper.type.MemberOverride2
import lang.temper.type.MethodShape
import lang.temper.type.MkType
import lang.temper.type.NominalType
import lang.temper.type.PropertyShape
import lang.temper.type.StaticPropertyShape
import lang.temper.type.StaticType
import lang.temper.type.SuperTypeTree
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.VisibleMemberShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.forEachSuperType
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.TypeContext2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.mapType
import kotlin.math.min

internal fun linkOverrides(
    member: VisibleMemberShape,
    typeContext: TypeContext2,
    logSink: LogSink,
) {
    if (member.visibility == Visibility.Private || member is StaticPropertyShape) {
        // Private members and statics override nothing nor are overridden.
        // Dispatch to them is non-virtual.
        member.overriddenMembers = emptySet()
        return
    }
    val overriddenMembers = mutableSetOf<MemberOverride2>()

    val enclosingTypeShape = member.enclosingType
    val enclosingType = MkType.nominal(
        enclosingTypeShape,
        enclosingTypeShape.typeParameters.map { MkType.nominal(it.definition, emptyList()) },
    )
    val superTypeTree = SuperTypeTree.of(enclosingType)
    // Walk the super-type tree breadth-first.
    // Each step through the loop below looks at the direct super-types of the type
    // on the Deque, so we put enclosingType on the deque knowing that we will not
    // look at member as possibly overriding itself.
    superTypeTree.forEachSuperType { superType ->
        val overriddenByMember = overriddenIn(member, superType, typeContext, logSink)
        if (overriddenByMember != null) {
            overriddenMembers.addAll(overriddenByMember)
            false // override found, so no need to look further
        } else {
            true
        }
    }

    member.overriddenMembers = overriddenMembers.toSet()
}

private fun overriddenIn(
    subTypeMember: VisibleMemberShape,
    superType: NominalType,
    typeContext: TypeContext2,
    logSink: LogSink,
): List<MemberOverride2>? =
    when (val d = superType.definition) {
        is TypeShape -> {
            val superType2 = MkType2(d)
                .actuals(
                    superType.bindings.map { binding ->
                        (binding as? StaticType)?.let { hackMapOldStyleToNew(it) }
                            ?: WellKnownTypes.invalidType2
                    },
                )
                .get() as DefinedNonNullType
            overriddenIn(subTypeMember, superType2, typeContext, logSink)
        }
        is TypeFormal -> null
    }

private fun overriddenIn(
    subTypeMember: VisibleMemberShape,
    superType: DefinedNonNullType,
    typeContext: TypeContext2,
    logSink: LogSink,
): List<MemberOverride2>? {
    val typeShape = superType.definition

    fun isOverrideCandidate(superTypeMember: VisibleMemberShape) =
        superTypeMember.visibility != Visibility.Private &&
            superTypeMember.symbol == subTypeMember.symbol

    // Find the same kind of thing as the sub-type member.
    val candidates = when (subTypeMember) {
        is MethodShape -> typeShape.methods.filter {
            it.methodKind == subTypeMember.methodKind && isOverrideCandidate(it)
        }
        is PropertyShape -> typeShape.properties.filter { isOverrideCandidate(it) }
        is StaticPropertyShape -> error("Static not filtered out above")
    }

    val subFnType = subTypeMember.descriptor as? Signature2
    if (subTypeMember is MethodShape && subFnType == null) {
        logSink.log(
            Log.Error,
            MessageTemplate.MissingType,
            subTypeMember.stay?.pos ?: unknownPos,
            listOf(subTypeMember.logDescription),
        )
        return null
    }

    // Build a list of overrides
    val overrides = mutableListOf<MemberOverride2>()

    candidate_loop@
    for (candidate in candidates) {
        var superFnType: Signature2? = null
        if (candidate is MethodShape) {
            val candidateDescriptor = candidate.descriptor
            check(subFnType != null)
            superFnType = candidateDescriptor
            if (superFnType == null) {
                logSink.log(
                    Log.Error,
                    MessageTemplate.MissingType,
                    candidate.stay?.pos ?: unknownPos,
                    listOf(candidate.logDescription),
                )
                continue@candidate_loop
            }
        }
        memberOverrideFor2(
            typeContext = typeContext,
            subFnType = subFnType,
            superType = superType,
            superTypeMember = candidate,
            superFnType = superFnType,
        )?.let {
            overrides.add(it)
        }
    }

    return if (overrides.isEmpty()) null else overrides.toList()
}

fun fnTypeFor(staticType: StaticType?): FunctionType? {
    if (staticType is FunctionType) {
        return staticType
    }
    if (staticType is AndType) {
        for (member in staticType.members) {
            // Return the first one which is function-y
            return fnTypeFor(member) ?: continue
        }
    }

    return null
}

private val MethodShape.logDescription: String
    get() = "${enclosingType.name}.${name}"

fun memberOverrideFor2(
    typeContext: TypeContext2,
    subFnType: Signature2?,
    superType: DefinedNonNullType,
    superTypeMember: VisibleMemberShape,
    superFnType: Signature2? = superTypeMember.descriptor as? Signature2,
): MemberOverride2? {
    val superMemberType = superTypeMember.descriptor
    // recast the super-type member's signature in terms of the sub-type's parameter bindings
    val bindingMap = mutableMapOf<TypeFormal, Type2>()
    // If the member is a method, make sure it has the same number of formals, and
    // relate its formals to the formals in the super-type version.
    if (superTypeMember is MethodShape) {
        if (superFnType == null || subFnType == null) { return null }

        // Early out of this loop iteration if the arity differs.
        if (subFnType.allValueFormals.size != superFnType.allValueFormals.size ||
            (subFnType.restInputsType == null) != (superFnType.restInputsType == null)
        ) {
            return null
        }
        val n = min(subFnType.typeFormals.size, superFnType.typeFormals.size)
        for (i in 0 until n) {
            bindingMap[superFnType.typeFormals[i]] = MkType2(subFnType.typeFormals[i]).get()
        }
        // If we add support for SELF types, they would also need to be rebound in the binding map,
        // though would handle the thisArg hack in the argument loop below.
    }
    // We need the bindings from the super-type formals to its actuals.
    val superTypeShape = superTypeMember.enclosingType
    for ((formalIndex, formal) in superTypeShape.formals.withIndex()) {
        val actual = superType.bindings.getOrNull(formalIndex)
            ?: continue
        bindingMap[formal] = actual
    }

    // Use the binding map to relate the type of the super to the type of the sub.
    // If they match, ignoring output type, then there's an override.
    val superTypeInSubContext = if (superMemberType == null) {
        return null
    } else if (bindingMap.isEmpty()) {
        superMemberType
    } else {
        when (superMemberType) {
            is Type2 -> superMemberType.mapType(bindingMap)
            is Signature2 -> superMemberType.mapType(bindingMap)
        }
    }

    when (superTypeMember) {
        is StaticPropertyShape -> error("Static not filtered out above")
        is PropertyShape -> {
            check(superTypeInSubContext is Type2)
            // Properties override properties with the same dot-name.
            // We've stored the contextualized type so that we can later
            // complain if the type is not non-strictly narrower in the super-type.
            return MemberOverride2(superTypeMember, superTypeInSubContext)
        }
        is MethodShape -> {
            check(superTypeInSubContext is Signature2)
            // If the contextualized argument types of the super type are non-strictly
            // wider, then it overrides.
            // Storing the contextualized type lets us later detect errors like the
            // return type being narrower, or differing type parameters.
            check(subFnType != null && superFnType != null) // If not, continued above
            val superFnTypeContextualized = superTypeInSubContext
            check(superFnTypeContextualized.allValueFormals.size == superFnType.allValueFormals.size)
            val thisArgIndices = 0..0 // TODO: Ideally base on arg declaration metadata
            for (i in 0 until (subFnType.requiredInputTypes.size + subFnType.optionalInputTypes.size)) {
                if (i in thisArgIndices) {
                    // The this-arg for sub-type narrows whereas the other parameters are allowed to widen.
                    continue
                }
                val subArg = subFnType.valueFormalForActual(i)!!
                val superArg = superFnTypeContextualized.valueFormalForActual(i)!!
                if (!typeContext.isSubType(superArg.type, subArg.type)) {
                    return null
                }
            }
            val subRestType = subFnType.restInputsType
            if (subRestType != null) {
                val superRestType = superFnType.restInputsType!!
                if (!typeContext.isSubType(superRestType, subRestType)) {
                    return null
                }
            }
            return MemberOverride2(superTypeMember, superTypeInSubContext)
        }
    }
}
