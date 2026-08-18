package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ParsedName
import lang.temper.name.ParsedNameOrResolvedParsedName
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.name.Temporary
import lang.temper.type.DotHelper
import lang.temper.type.DotMember
import lang.temper.type.ExternalCall
import lang.temper.type.FunctionResolution
import lang.temper.type.OperatorMember
import lang.temper.type2.AnySignature
import lang.temper.value.CallTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.SpecialFunction
import lang.temper.value.StaylessMacroValue
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.TreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.ValueStability
import lang.temper.value.and
import lang.temper.value.dotBuiltinName
import lang.temper.value.freeTarget
import lang.temper.value.initSymbol
import lang.temper.value.stability

/**
 * Converts operations into a form that can be easily evaluated.
 *
 * An operation is a kind of expression that applies an operator to operands.
 * E.g. `x + y` applies the `+` operator to operands `x` and `y`.
 *
 * [DotHelper] is our main mechanism for managing overloading and extensions, so
 * this desugars operations to *DotHelper* invocations, allowing operation
 * semantics to be specified by methods or extension-like functions with
 * [lang.temper.value.overloadSymbol] metadata.
 *
 * <!-- snippet: builtin/++ -->
 * # `++` operator: increment
 * `++x` reads `x` and assigns the following value back to it.
 *
 * The value assigned is `x.succ()`.
 * Builtin numeric types implement the `.succ()` method to return a value one greater,
 * so for numerics `++x` is equivalent to `x += 1`.
 *
 * `x++` has the same effect as `++x`, but produces the value of `x` before
 * assigning its successor, instead of the value after.
 *
 * ```temper true
 * var x: Int = 0;
 * // when `x` comes after  `++`, produces value after  increment
 * console.log((++x).toString()); //!outputs "1"
 * // when `x` comes before `++`, produces value before increment
 * console.log((x++).toString()); //!outputs "1"
 * x == 2
 * ```
 *
 * The effects of `++x` and `x++` differ from `x = x.succ()`, in that if `x` is a complex expression,
 * its parts are only evaluated once.
 * For example, in `++listBuilder[f()]`, the function call, `f()`, which computes the index,
 * only happens once.
 *
 * <!-- snippet: builtin/-- -->
 * # `--` operator: decrement
 * `--x` reads `x` and assigns the preceding value back to it.
 * The value assigned is `x.pred()`.
 * Builtin numeric types implement the `.pred()` method to return a value one less,
 * so for numerics `--x` is equivalent to `x -= 1`.
 *
 * `x--` has the same effect as `--x`, but produces the value of `x` before
 * assigning its predecessor, instead of the value after.
 *
 * ```temper true
 * var x: Int = 0;
 * // when `x` comes after  `--`, produces value after  decrement
 * console.log((--x).toString()); //!outputs "-1"
 * // when `x` comes before `--`, produces value before decrement
 * console.log((x--).toString()); //!outputs "-1"
 * x == -2
 * ```
 *
 * The effects of `--x` and `x--` differ from `x = x.pred()`, in that if `x` is a complex expression,
 * its parts are only evaluated once.
 * For example, in `--listBuilder[f()]`, the function call, `f()`, which computes the index,
 * only happens once.
 */
object DesugarOperation : SpecialFunction, StaylessMacroValue, NamedBuiltinFun {
    override val name = "desugarOperation"

    override val sigs: List<AnySignature>? = null

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        val call = macroEnv.call
        val env = macroEnv.environment

        val operator = args.valueTree(0)
        val operandIndices = (1..args.lastIndex)
        val nOperands = operandIndices.last - operandIndices.first + 1

        val name = (operator as? NameLeaf)?.content
        val nameText = (name as? ParsedNameOrResolvedParsedName)?.asParsedName()?.nameText
        // If we get `+=` or `++`, then we need to do a lot of work as
        // if it's a simpler operator like `+` and then roll in the assignment part later.
        val (op, cf: OpClassification) = run classify@{
            nameText ?: return@classify null to OpClassification.Simple
            when (nOperands) {
                2 -> {
                    val simpleOp = simpleBuiltinKeyFromCompoundOperator(nameText)
                    if (simpleOp != null) {
                        return@classify simpleOp to OpClassification.CompoundAssignment
                    }
                }
                1 -> when (nameText) {
                    "--", "_--" -> return@classify "pred" to OpClassification.IncrOrDecr
                    "++", "_++" -> return@classify "succ" to OpClassification.IncrOrDecr
                    else -> {}
                }
                else -> {}
            }

            nameText to OpClassification.Simple
        }

        val isDefined = cf == OpClassification.Simple && when (operator) {
            is ValueLeaf -> true
            is NameLeaf -> {
                val name = operator.content
                name.builtinKey == ".." || // Special in `@(A..B)`
                    env.declarationMetadata(name) != null
            }
            else -> false
        }

        val operands = operandIndices.map { args.valueTree(it).incoming!! }
        if (isDefined) {
            // If the name is defined, use it.
            // (desugarOp nym`+` x y) -> (nym`+` x y)
            if (interpMode == InterpMode.Partial && call != null) {
                call.removeChildren(0..0)
            }
            val callee = args.evaluate(0, interpMode) as? Value<*>
                ?: return NotYet
            return macroEnv.dispatchCallTo(
                operator,
                callee,
                operands.map { it.target },
                interpMode,
            )
        }
        // It's not defined, so we need to rewrite it.

        if (op == null || call?.incoming == null) { return NotYet }

        val operatorSpecifier = if (cf == OpClassification.IncrOrDecr) {
            null
        } else {
            when (operands.size) {
                2 -> "_${op}_"
                1 -> "${op}_"
                else -> null
            }
        }
        val member = when {
            operatorSpecifier != null -> OperatorMember(operatorSpecifier)
            cf == OpClassification.IncrOrDecr -> DotMember(Symbol(op))
            else -> null
        }

        // We have a lookup list of extensions for basic types like Int32 and String
        // so that evaluation of them can work even before core.temper has staged
        // to the point where we can dispatch to methods on well-known types'.
        val builtins = builtinOperatorSpecs[operatorSpecifier] ?: listOf()
        if (member != null) {
            val extensions = builtins.map { FunctionResolution(it) }
            val helper = DotHelper(ExternalCall, member, extensions)
            val vHelper = Value(helper)
            if (interpMode == InterpMode.Partial) {
                when (cf) {
                    OpClassification.CompoundAssignment, OpClassification.IncrOrDecr -> {
                        val preCapture = cf == OpClassification.IncrOrDecr && nameText?.startsWith("_") == true
                        val needsRecursiveDesugar = cf == OpClassification.CompoundAssignment
                        desugarCompoundOperation(
                            macroEnv,
                            operands[0],
                            operands.getOrNull(1),
                            preCapture = preCapture,
                            plantOperation = { calleePos, plantOperands ->
                                if (needsRecursiveDesugar) {
                                    // Plant the simpler operator and let
                                    // a recursive invocation desugar that.
                                    V(calleePos.leftEdge, vDesugarOperation)
                                    Rn(operator.pos, ParsedName(op))
                                    plantOperands()
                                } else {
                                    Call { // .succ() or .pred() method call
                                        Rn(calleePos.leftEdge, dotBuiltinName)
                                        plantOperands()
                                        V(calleePos, Value((helper.member as DotMember).dotName))
                                    }
                                }
                            },
                        )?.let {
                            macroEnv.replaceMacroCallWith(it)
                        }
                    }
                    OpClassification.Simple ->
                        macroEnv.replaceMacroCallWith {
                            Call(call.pos) {
                                V(operator.pos, vHelper)
                                operands.forEach {
                                    Replant(freeTarget(it))
                                }
                            }
                        }
                }
            }
            val leftName = operands.firstOrNull()?.target as? NameLeaf
            if (cf != OpClassification.IncrOrDecr && (cf == OpClassification.Simple || leftName != null)) {
                val helperTree = ValueLeaf(macroEnv.document, operator.pos, vHelper)
                val operandTrees = operands.map { it.target }
                var result = macroEnv.dispatchCallTo(helperTree, vHelper, operandTrees, interpMode)
                if (cf == OpClassification.CompoundAssignment && result is Value<*>) {
                    if (interpMode == InterpMode.Full || result.stability == ValueStability.Stable) {
                        result = env.set(leftName!!.content, result, macroEnv)
                            .and { result }
                    }
                }
                return result
            }
        }
        return NotYet
    }

    override fun toString(): String = name

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.word(name)
    }
}

val vDesugarOperation = Value(DesugarOperation)

fun desugarCompoundOperation(
    macroEnv: MacroEnvironment,
    left: TEdge,
    right: TEdge?,
    preCapture: Boolean = false,
    plantOperation: Planting.(pos: Position, plantOperands: Planting.() -> Unit) -> Unit,
): (Planting.() -> Unit)? {
    val calleePos = macroEnv.callee.pos
    val leftTree = left.target
    if (leftTree is NameLeaf) {
        // No need to desugar
        return {
            fun Planting.simpleDesugar(preCaptured: Temporary?): TreeTemplate<CallTree> =
                Call(macroEnv.pos) {
                    V(calleePos, BuiltinFuns.vSetLocalFn)
                    Replant(leftTree.copyLeft())
                    Call(macroEnv.pos) {
                        plantOperation(calleePos) {
                            if (preCaptured != null) {
                                Rn(leftTree.pos, preCaptured)
                            } else {
                                Replant(leftTree.copyRight())
                            }
                            if (right != null) {
                                Replant(freeTarget(right))
                            }
                        }
                    }
                }
            maybePrecapture(macroEnv, { simpleDesugar(it) }, preCapture) { leftTree.copyRight() }
        }
    }

    // There are a number of kinds of complex left-hand sides:
    //
    //     left.prop += right     // See DotOperationDesugarer
    //     left[index] += right   // See SquareBracketFn
    //
    // Rather than special case handling here for these two cases,
    // we have a general mechanism that lets a macro behave one way
    // in a left-hand context and in a right-hand context.
    //
    //     leftHandOf(left.prop, left.prop + right)
    //
    // DotOperationDesugarer is a left-hand aware macro, so its
    // first application knows to generate a use of left.prop's setter
    // instead of its getter as for the second use.
    //
    // But first, we pull out temporaries.
    // We don't have enough context here to know that `left` stays stable
    // across all uses above.  Consider the below:
    //
    //      left[do { left = otherLeft; i++ }] += 1
    //
    // Obviously, we would want to avoid multiply evaluating that
    // `do` block, but we can't just convert that to:
    //
    //      do {
    //        let t#1 = do { left = otherLeft; i++ };
    //        leftHandOf(left[t#1], left[t#1] + right)
    //      }
    //
    // That is wrong because the evaluation of t#1 changes `left`'s referent.
    // The correct translation is:
    //
    //      do {
    //        let t#0 = left;
    //        let t#1 = do { left = otherLeft; i++ };
    //        leftHandOf(t#0[t#1], t#0[t#1] + right)
    //      }
    //
    // So we pull all non-value, non-builtin-name expressions out into
    // temporaries.
    if (leftTree is CallTree) {
        val callee = leftTree.childOrNull(0)
        if (isKnownStable(callee)) {
            // The callee is stable, not an arbitrarily large subtree / copyable.
            // And we won't need to capture it in a temporary.
            return {
                Block(macroEnv.pos) {
                    val leftTree = left.target
                    for (i in 1 until leftTree.size) {
                        val e = leftTree.edge(i)
                        val child = e.target
                        if (!isKnownStable(child)) {
                            val t = macroEnv.nameMaker.unusedTemporaryName("t")
                            Decl(child.pos.leftEdge, t)
                            e.replace { Rn(t) }
                            Call(child.pos.leftEdge, BuiltinFuns.setLocalFn) {
                                Ln(t)
                                Replant(child)
                            }
                        }
                    }
                    fun Planting.plantOp(preCaptured: Temporary?): TreeTemplate<CallTree> =
                        Call(leftTree.pos, LeftHandOfMacro) {
                            Replant(leftTree.copy())
                            Call(macroEnv.pos) {
                                plantOperation(calleePos) {
                                    if (preCaptured != null) {
                                        Rn(leftTree.pos, preCaptured)
                                    } else {
                                        Replant(freeTarget(left))
                                    }
                                    if (right != null) {
                                        Replant(freeTarget(right))
                                    }
                                }
                            }
                        }
                    maybePrecapture(macroEnv, { plantOp(it) }, preCapture) { freeTarget(left) }
                }
            }
        }
    }
    return null
}

fun isKnownStable(t: Tree?) = when (t) {
    is ValueLeaf -> true
    is RightNameLeaf -> when (t.content) {
        is BuiltinName -> true
        is ExportedName -> true
        is Temporary, is ParsedName, is SourceName -> false
    }
    else -> false
}

private enum class OpClassification {
    /**
     * Like `+`.
     */
    Simple,

    /** Like `+=`.  The simple op is `+` */
    CompoundAssignment,

    /**
     * Like `++` or `--`.
     * The simple op is a method name: `pred` (predecessor) or `succ` (successor).
     */
    IncrOrDecr,
}

private fun Planting.maybePrecapture(
    macroEnv: MacroEnvironment,
    /** Plant the operation, but if the operatoion was [pre-read][plantPreRead], into a temporary, use that instead. */
    plantOperation: Planting.(Temporary?) -> TreeTemplate<CallTree>,
    preCapture: Boolean,
    /** If we need to read the result early, plant an expression that does that. */
    plantPreRead: () -> Tree,
): TreeTemplate<*> =
    if (preCapture) {
        val preCaptureTemporary = macroEnv.nameMaker.unusedTemporaryName("postfixReturn")
        Block(pos = macroEnv.pos) {
            Decl(preCaptureTemporary) {
                V(initSymbol)
                Replant(plantPreRead())
            }
            plantOperation(preCaptureTemporary)
            Rn(preCaptureTemporary)
        }
    } else {
        plantOperation(null)
    }
