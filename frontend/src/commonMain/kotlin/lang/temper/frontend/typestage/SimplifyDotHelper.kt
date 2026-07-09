package lang.temper.frontend.typestage

import lang.temper.common.Either
import lang.temper.common.subListToEnd
import lang.temper.frontend.maybeAdjustDotHelper
import lang.temper.type.AndType
import lang.temper.type.DotHelper
import lang.temper.type.DotMember
import lang.temper.type.ExtensionResolution
import lang.temper.type.FunctionResolution
import lang.temper.type.FunctionType
import lang.temper.type.InstanceExtensionResolution
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
    val variantMatch = typeInferences?.variant
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
        if (variant.first equivalent variantMatch || variant.first equivalent variantMatchRefined) {
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
            // Look through <> so that
            //     subject.method<Type, Actuals>(...)
            // ->
            //     (resolution<Type, Actuals>)(subject, ...)

            val extensionResolution = chosenVariantResolution.item
            val doc = calleeEdge.target.document
            val extensionCalleeLeaf = when (extensionResolution) {
                is FunctionResolution -> {
                    doExtraCoverFunctionVariantRefinement(
                        extensionResolution.fn,
                        call.children.subListToEnd(dotHelper.memberAccessor.firstArgumentIndex + 1),
                    )?.let { refinement ->
                        FunctionResolution(refinement).toLeaf(doc, callee.pos)
                    }
                }
                is InstanceExtensionResolution,
                is StaticExtensionResolution,
                -> null
            } ?: extensionResolution.toLeaf(doc, callee.pos)

            calleeEdge.replace {
                Replant(extensionCalleeLeaf)
            }

            if (extensionResolution is StaticExtensionResolution) {
                // Remove the receiver type
                call.removeChildren(1..1)
            }
            retypeTree(calleeEdge.target)
            return
        }
    }

    val functionTypes = variantMatch?.let {
        extractAtoms(it) { atom -> atom as? FunctionType }
    } ?: setOf()
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

private infix fun StaticType?.equivalent(other: StaticType?): Boolean =
    if (this is FunctionType && other is FunctionType) {
        val tvf = this.valueFormals
        val ovf = other.valueFormals
        var same = this.returnType == other.returnType &&
            this.restValuesFormal == other.restValuesFormal &&
            tvf.size == ovf.size &&
            this.typeFormals == other.typeFormals
        if (same) {
            for ((i, element) in tvf.withIndex()) {
                if (element.type != ovf[i].type) {
                    same = false
                    break
                }
            }
        }
        same
    } else {
        this == other
    }
