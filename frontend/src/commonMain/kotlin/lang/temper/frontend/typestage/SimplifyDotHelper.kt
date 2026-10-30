package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Either
import lang.temper.common.subListToEnd
import lang.temper.frontend.maybeAdjustDotHelper
import lang.temper.name.Symbol
import lang.temper.type.DotHelper
import lang.temper.type.DotMember
import lang.temper.type.ExtensionResolution
import lang.temper.type.ExternalCall
import lang.temper.type.FunctionResolution
import lang.temper.type.InstanceExtensionResolution
import lang.temper.type.StaticExtensionResolution
import lang.temper.type.StaticType
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.VisibleMemberShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.AdHocArrowTypes
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.TypeContext2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.hackTryStaticTypeToSig
import lang.temper.value.CallTree
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.TFloat64
import lang.temper.value.TInt
import lang.temper.value.TInt64
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
    typeContext2: TypeContext2,
    retypeTree: (Tree) -> Unit,
) {
    val calleeEdge = call.edge(0)
    val callee = calleeEdge.target
    val typeInferences = call.typeInferences
    val variantMatch = typeInferences?.variant ?: return

    // Give preference to members over extensions
    var chosenVariantResolution: VariantResolution? = null
    for ((variantType, resolution) in variants) {
        val variantSig = hackTryStaticTypeToSig(variantType)
        if (variantSig != null && variantSig equivalent variantMatch) {
            chosenVariantResolution = chooseVariantResolution(chosenVariantResolution, resolution)
        }
    }

    when (chosenVariantResolution) {
        null,
        is Either.Left,
        -> {
            val updatedType: Type2? = when {
                // If the resolution is to a method, not an extension, but to a different method, refine it.
                chosenVariantResolution?.let { DotMember(it.leftOrNull.symbol) != dotHelper.member } == true -> {
                    // An overload now resolved to an individually named method.
                    variantMatch.let { AdHocArrowTypes.definedTypeForSig(it) }
                }
                else -> when {
                    dotHelper.extensions.isNotEmpty() -> chosenVariantResolution?.let {
                        // Retain variants for now-known-as-non-extension call.
                        var simpleLub: Type2? = null
                        for (variant in variants) {
                            if (variant.second is Either.Left) {
                                val variantType = hackMapOldStyleToNew(variant.first)
                                simpleLub = simpleLub?.let { typeContext2.simpleLub(simpleLub, variantType) }
                                    ?: variantType
                            }
                        }
                        simpleLub
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
                    doExtraFunctionVariantRefinement(
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

    // Inline some methods on numerics to reduce the method connect burden for backends.
    // This means `++x` when x has a builtin numeric type ends up as `x = x + 1` which
    // is more readily translated than `x = x.succ()`.
    if (call.size == 2 && dotHelper.memberAccessor == ExternalCall) {
        val subjectType = variantMatch.requiredInputTypes.getOrNull(0)
        inlineHelpersForSuccAndPred[subjectType to dotHelper.member]?.let { (newCallee, extraArg) ->
            val calleeEdge = call.edge(0)
            calleeEdge.replace { pos ->
                V(pos, Value(newCallee))
            }
            call.insert {
                V(extraArg)
            }
            retypeTree(call)
            return@simplifyDotHelper
        }
    }

    // Supply "this" types so we can figure out whether a referenced property is backed.
    val subjectTypeShapes = buildSet {
        fun addTypeShapesFrom(definition: TypeDefinition) {
            when (definition) {
                is TypeShape -> add(definition)
                is TypeFormal ->
                    definition.superTypes.forEach { addTypeShapesFrom(it.definition) }
            }
        }
        val thisType = variantMatch.requiredInputTypes.firstOrNull()
        thisType?.let { addTypeShapesFrom(it.definition) }
    }
    val callEdge = call.incoming!!
    if (maybeAdjustDotHelper(call, dotHelper, subjectTypeShapes, preserveExtensions = false)) {
        retypeTree(callEdge.target)
    }
}

private infix fun Signature2.equivalent(other: Signature2): Boolean {
    val tvf = this.allValueFormals
    val ovf = other.allValueFormals
    var same = this.returnType == other.returnType &&
        tvf.size == ovf.size &&
        this.typeFormals == other.typeFormals
    if (same) {
        for ((i, element) in tvf.withIndex()) {
            if (element.type != ovf[i].type || element.kind != ovf[i].kind) {
                same = false
                break
            }
        }
    }
    return same
}

private val succDotMember = DotMember(Symbol("succ")) // `++` desugars to this
private val predDotMember = DotMember(Symbol("pred")) // `--` desugars to this
private val inlineHelpersForSuccAndPred = mapOf(
    (WellKnownTypes.intType2 to succDotMember) to (BuiltinFuns.plusIntIntFn to Value(1, TInt)),
    (WellKnownTypes.intType2 to predDotMember) to (BuiltinFuns.minusIntIntFn to Value(1, TInt)),
    (WellKnownTypes.int64Type2 to succDotMember) to (BuiltinFuns.plusLongLongFn to Value(1L, TInt64)),
    (WellKnownTypes.int64Type2 to predDotMember) to (BuiltinFuns.minusLongLongFn to Value(1L, TInt64)),
    (WellKnownTypes.float64Type2 to succDotMember) to (BuiltinFuns.plusFloatFloatFn to Value(1.0, TFloat64)),
    (WellKnownTypes.float64Type2 to predDotMember) to (BuiltinFuns.minusFloatFloatFn to Value(1.0, TFloat64)),
)

/**
 * Given two possible resolutions, picks the higher priority one.
 *
 * We prefer builtin resolutions like [BuiltinFuns.eqIntFn] over
 * method calls like `Int32.eq`.
 */
private fun chooseVariantResolution(
    previousChoice: VariantResolution?,
    candidate: VariantResolution,
): VariantResolution {
    if (previousChoice == null) {
        return candidate
    }
    val previousClassification = classifyVariantResolution(previousChoice)
    val candidateClassification = classifyVariantResolution(candidate)
    return if (candidateClassification > previousClassification) {
        candidate
    } else {
        previousChoice
    }
}

private enum class VariantResolutionClassification {
    Extension,
    DefinedByTypeAuthor,
    Builtin,
}

private fun classifyVariantResolution(r: VariantResolution): VariantResolutionClassification =
    when (r) {
        is Either.Left<VisibleMemberShape> -> VariantResolutionClassification.DefinedByTypeAuthor
        is Either.Right<ExtensionResolution> -> {
            when (val er = r.item) {
                is FunctionResolution -> if (er.fn is NamedBuiltinFun) {
                    VariantResolutionClassification.Builtin
                } else {
                    VariantResolutionClassification.Extension
                }
                is ExtensionResolution -> VariantResolutionClassification.Extension
            }
        }
    }
