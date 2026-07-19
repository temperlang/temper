package lang.temper.frontend

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.GetStaticOp
import lang.temper.common.soleMatchingOrNull
import lang.temper.log.Position
import lang.temper.type.Abstractness
import lang.temper.type.AccessibleFilter
import lang.temper.type.CallMemberAccessor
import lang.temper.type.DotHelper
import lang.temper.type.DotMember
import lang.temper.type.ExternalGet
import lang.temper.type.ExternalMemberAccessor
import lang.temper.type.GetMemberAccessor
import lang.temper.type.InstanceExtensionResolution
import lang.temper.type.InternalGet
import lang.temper.type.InternalMemberAccessor
import lang.temper.type.InternalSet
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.OperatorMember
import lang.temper.type.PropertyShape
import lang.temper.type.StaticPropertyShape
import lang.temper.type.TypeParameterShape
import lang.temper.type.TypeShape
import lang.temper.value.CallTree
import lang.temper.value.Planting
import lang.temper.value.TreeTemplate
import lang.temper.value.Value
import lang.temper.value.freeTree
import lang.temper.value.reifiedTypeContained
import lang.temper.value.typeShapeAtLeafOrNull

/**
 * Look at dot helpers that came out of [lang.temper.frontend.syntax.DotOperationDesugarer]
 * and adjust them in light of information about the actual members available, and information
 * that may not be available until after name resolution.
 *
 * For example:
 *
 * - Internal [gets][InternalGet] and [sets][InternalSet] become calls to the special
 *   [BuiltinFuns.getpFn] or [BuiltinFuns.setpFn] functions if the referenced property is a
 *   backed property to distinguish known assignments from (otherwise catastrophically recursive)
 *   invocations of a property's associated getter/setter.
 * - `x.name` become a static property access if `x` inlines to a type value.  That cannot
 *   happen until after `x` is distinguishable from local names by name resolution.
 * - `x.f()` might become a call of a [property get][GetMemberAccessor] instead of a
 *   [method call][CallMemberAccessor] if there is no method named `f`, but there is a
 *   property named `f` with function type.
 *
 * @return true iff adjustments were made
 */
internal fun maybeAdjustDotHelper(
    t: CallTree,
    dotHelper: DotHelper,
    subjectTypeShapes: Iterable<TypeShape>? = null,
    preserveExtensions: Boolean,
): Boolean {
    val member = dotHelper.member
    val calleePos = t.child(0).pos
    val accessor = dotHelper.memberAccessor
    val hasEnclosingType = accessor.enclosingTypeIndexOrNegativeOne >= 0
    val thisType = if (hasEnclosingType) {
        t.childOrNull(accessor.enclosingTypeIndexOrNegativeOne + 1)?.typeShapeAtLeafOrNull
    } else {
        null
    }
    val edge = t.incoming!! // Safe because t is not a root block

    // If this looks like a static member access, we can filter out any non-static extensions
    var adjusted = true // TODO: this is never false
    val firstArgIndex = accessor.firstArgumentIndex + 1
    val firstArg = t.childOrNull(firstArgIndex)
    val typeReceiver = firstArg?.reifiedTypeContained?.type2
    val adjDotHelper: DotHelper = run adjustDotHelper@{
        if (typeReceiver != null) {
            val newExtensions = dotHelper.extensions.filter {
                it !is InstanceExtensionResolution
            }
            if (newExtensions.size != dotHelper.extensions.size) {
                val newDotHelper = DotHelper(dotHelper.memberAccessor, member, newExtensions)
                val calleeEdge = t.edge(0)
                val callee = calleeEdge.target
                calleeEdge.replace { pos ->
                    V(pos, Value(newDotHelper), callee.typeInferences?.type)
                }
                adjusted = true
                return@adjustDotHelper newDotHelper
            }
        }
        dotHelper
    }

    val extensionsToPreserve = preserveExtensions && adjDotHelper.extensions.isNotEmpty()

    // do_iget_p(t, this) -> getp(p__0, this) when `p` is a backed property of t
    val property = when (member) {
        is DotMember -> thisType?.properties?.soleMatchingOrNull { it.symbol == member.dotName }
        is OperatorMember -> null
    }
    if (
        !extensionsToPreserve &&
        property?.abstractness == Abstractness.Concrete &&
        accessor == InternalGet && t.size == IGET_CALL_SIZE
    ) {
        edge.replace { pos ->
            Call(pos) {
                V(calleePos, BuiltinFuns.vGetp)
                Rn(pos.rightEdge, property.name)
                Replant(freeTree(t.child(2)))
            }
        }
        return true
    }
    // do_iset_p(t, this, x) -> setp(p__0, this, x)
    if (
        !extensionsToPreserve &&
        property?.abstractness == Abstractness.Concrete &&
        accessor == InternalSet && t.size == ISET_CALL_SIZE
    ) {
        @Suppress("MagicNumber") // just list indices
        edge.replace { pos ->
            Call(pos) {
                V(calleePos, BuiltinFuns.vSetp)
                Rn(pos.rightEdge, property.name)
                Replant(freeTree(t.child(2)))
                Replant(freeTree(t.child(3)))
            }
        }
        return true
    }

    // do_iget_p( t, type (T))      -> igetStatic(T, \p)
    // do_icall_p(t, type (T), arg) -> igetStatic(T, \p)(arg)
    // do_get_p(     type (T))      -> getStatic(T, \p)
    // do_call_p(    type (T), arg) -> getStatic(T, \p)(arg)
    if (
        member is DotMember &&
        !extensionsToPreserve &&
        (
            (accessor is GetMemberAccessor && t.size == firstArgIndex + 1) ||
                (accessor is CallMemberAccessor && t.size >= firstArgIndex + 1)
            ) &&
        typeReceiver != null
    ) {
        val dotName = member.dotName
        fun Planting.makeReplacement(pos: Position): TreeTemplate<*> {
            val alt = GetStaticOp.externalStaticGot(typeReceiver, dotName)
            if (alt != null) {
                return V(calleePos, alt)
            }
            val gets = when (accessor) {
                is InternalMemberAccessor -> BuiltinFuns.vIGets
                is ExternalMemberAccessor -> BuiltinFuns.vGets
            }
            return Call(pos) {
                V(calleePos, gets)
                Replant(freeTree(firstArg))
                V(pos.rightEdge, dotName)
            }
        }
        edge.replace gets@{ pos ->
            if (accessor is CallMemberAccessor) {
                Call(pos) {
                    makeReplacement(pos)
                    for (arg in t.children.drop(firstArgIndex + 1)) {
                        Replant(freeTree(arg))
                    }
                }
            } else {
                makeReplacement(pos)
            }
        }
        return true
    }

    // When `p` is a property name, not a method name:
    // do_icall_p(t, subject, arg) -> do_iget(t, subject)(arg)
    // do_call_p(    subject, arg) -> do_get(    subject)(arg)
    if (accessor is CallMemberAccessor && member is DotMember) {
        val typeShapes = thisType?.let { listOf(it) }
            ?: subjectTypeShapes
        if (typeShapes != null && (!hasEnclosingType || thisType != null)) {
            // We have enough type information to proceed
            var methodMatching = false
            var propertyMatching = false
            topMemberLoop@
            for (typeShape in typeShapes) {
                val filter =
                    AccessibleFilter(typeShape.membersMatching(member), thisType)
                for (member in filter) {
                    when (member) {
                        is MethodShape -> if (member.methodKind == MethodKind.Normal) {
                            methodMatching = true
                            break@topMemberLoop
                        }
                        is PropertyShape -> propertyMatching = true
                        is TypeParameterShape,
                        is StaticPropertyShape,
                        -> {}
                    }
                }
            }

            if (!methodMatching && propertyMatching) {
                val endOfDotParts = accessor.firstArgumentIndex + 2
                val dotParts = t.children.subList(1, endOfDotParts)
                val newDotHelper = DotHelper(
                    if (accessor is InternalMemberAccessor) { InternalGet } else { ExternalGet },
                    member,
                    adjDotHelper.extensions,
                )
                val args = t.children.drop(endOfDotParts)
                edge.replace {
                    Call(t.pos) {
                        Call(t.pos) {
                            V(calleePos, Value(newDotHelper))
                            dotParts.forEach {
                                Replant(freeTree(it))
                            }
                        }
                        args.forEach {
                            Replant(freeTree(it))
                        }
                    }
                }

                // We might want to adjust that do_get... to a getp, so
                // call this recursively if it's internal.
                if (newDotHelper.memberAccessor == InternalGet) {
                    maybeAdjustDotHelper(
                        edge.target.child(0) as CallTree,
                        newDotHelper,
                        subjectTypeShapes,
                        preserveExtensions = preserveExtensions,
                    )
                }
                return true
            }
        }
    }

    return adjusted
}

private const val IGET_CALL_SIZE = 3
private const val ISET_CALL_SIZE = 4
