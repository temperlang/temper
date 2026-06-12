package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Either
import lang.temper.frontend.maybeAdjustDotHelper
import lang.temper.interp.convertToErrorNode
import lang.temper.type.AndType
import lang.temper.type.BindMemberAccessor
import lang.temper.type.DotHelper
import lang.temper.type.DotMember
import lang.temper.type.ExtensionResolution
import lang.temper.type.FunctionType
import lang.temper.type.MkType
import lang.temper.type.StaticExtensionResolution
import lang.temper.type.StaticType
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.VisibleMemberShape
import lang.temper.type.extractAtoms
import lang.temper.value.CallTree
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.functionContained

private typealias VariantResolution = Either<VisibleMemberShape, ExtensionResolution>
private typealias Variant = Pair<StaticType, VariantResolution>

/**
 * Rewrite unambiguous uses of extension methods to direct calls by name and
 * eliminate unnecessary metadata where there are extensions that are not needed.
 */
internal fun simplifyDotHelper(
    call: CallTree,
    dotHelper: DotHelper,
    variants: List<Variant>,
    retypeTree: (Tree) -> Unit,
) {
    val calleeEdge = call.edge(0)
    val callee = calleeEdge.target
    val typeInferences = call.typeInferences
    val variantMatch = typeInferences?.variant ?: return
    val variantFunctionType = variantMatch as? FunctionType
    val variantMatchRefined = (variantFunctionType?.returnType as? AndType)?.let { andType ->
        when {
            typeInferences.type in andType.members -> MkType.fnDetails(
                typeFormals = variantFunctionType.typeFormals,
                valueFormals = variantFunctionType.valueFormals,
                restValuesFormal = variantFunctionType.restValuesFormal,
                // Specialize the return type to the actually determined type.
                returnType = typeInferences.type,
            )
            else -> null
        }
    }

    // Give preference to members over extensions
    var lastNonExtensionResolution: VariantResolution? = null
    var lastResolution: VariantResolution? = null
    for (variant in variants.reversed()) {
        if (variant.first == variantMatch || variant.first == variantMatchRefined) {
            lastResolution = variant.second
            if (variant.second is Either.Left) {
                lastNonExtensionResolution = variant.second
            }
        }
    }

    val chosenVariantResolution = lastNonExtensionResolution ?: lastResolution
    when (chosenVariantResolution) {
        null,
        is Either.Left,
        -> {
            val updatedType = when {
                // If the resolution is to a method, not an extension, but to a different method, refine it.
                lastNonExtensionResolution?.let { DotMember(it.leftOrNull!!.symbol) != dotHelper.member } == true -> {
                    // An overload now resolved to an individually named method.
                    variantMatchRefined ?: variantMatch
                }
                else -> when {
                    dotHelper.extensions.isNotEmpty() -> chosenVariantResolution?.let {
                        // Retain variants for now-known-as-non-extension call.
                        MkType.or(
                            variants.mapNotNull {
                                if (it.second is Either.Left) {
                                    it.first
                                } else {
                                    null
                                }
                            },
                        )
                    }
                    else -> null
                }
            }
            updatedType?.let {
                calleeEdge.replace {
                    val newMember = chosenVariantResolution?.item?.symbol?.let { DotMember(it) }
                        ?: dotHelper.member
                    val updatedDotHelper = DotHelper(dotHelper.memberAccessor, newMember, emptyList())
                    V(callee.pos, Value(updatedDotHelper), updatedType)
                }
            }
        }
        is Either.Right -> {
            var inheritsSubject: CallTree? = null
            var toReplace = calleeEdge
            // (Call
            //   (Bind subject \member)
            //   ...)
            // ->
            // (Call
            //   (RightNameName extensionResolution)
            //   subject
            //   ...)
            //
            // But also, look through <> so that
            //     subject.method<Type, Actuals>(...)
            // ->
            //     (resolution<Type, Actuals>)(subject, ...)
            if (dotHelper.memberAccessor is BindMemberAccessor) {
                val callEdge = call.incoming!!
                toReplace = callEdge
                inheritsSubject = callEdge.source as? CallTree
                if (
                    inheritsSubject?.childOrNull(0)?.functionContained == BuiltinFuns.angleFn
                ) {
                    inheritsSubject = inheritsSubject.incoming?.source as? CallTree
                }

                if (inheritsSubject == null) {
                    convertToErrorNode(callEdge)
                    return
                }
            }

            val extensionResolution = chosenVariantResolution.item
            val doc = toReplace.target.document
            toReplace.replace {
                Replant(extensionResolution.toLeaf(doc, callee.pos))
            }

            if (extensionResolution is StaticExtensionResolution) {
                // Remove the receiver type
                call.removeChildren(1..1)
            } else if (inheritsSubject != null) {
                val subject = call.child(1)
                call.removeChildren(1..1)
                inheritsSubject.add(childIndex = 1, newChild = subject)
            }
            retypeTree(toReplace.target)
            return
        }
    }

    val functionTypes = extractAtoms(variantMatch) { it as? FunctionType }
    // Supply "this" types so we can figure out whether a referenced property is backed.
    val subjectTypeShapes = buildSet {
        fun addTypeShapesFrom(definition: TypeDefinition) {
            when (definition) {
                is TypeShape -> add(definition)
                is TypeFormal ->
                    definition.superTypes.forEach { addTypeShapesFrom(it.definition) }
            }
        }
        functionTypes.forEach { functionType ->
            val thisArg = functionType.valueFormals.firstOrNull()
            thisArg?.type?.let { addTypeShapesFrom(it.definition) }
        }
    }
    val callEdge = call.incoming!!
    if (maybeAdjustDotHelper(call, dotHelper, subjectTypeShapes, preserveExtensions = false)) {
        retypeTree(callEdge.target)
    }
}
