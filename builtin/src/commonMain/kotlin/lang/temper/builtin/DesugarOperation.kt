package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ParsedName
import lang.temper.name.ParsedNameOrResolvedParsedName
import lang.temper.name.SourceName
import lang.temper.name.Temporary
import lang.temper.type.DotHelper
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
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.ValueStability
import lang.temper.value.and
import lang.temper.value.freeTarget
import lang.temper.value.stability
import lang.temper.value.toPseudoCode

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
        val operands = (1..args.lastIndex).map { args.valueTree(it).incoming!! }

        val c = lang.temper.common.console // do not commit
        val isDefined = when (operator) {
            is ValueLeaf -> true
            is NameLeaf -> {
                val name = operator.content
                name.builtinKey == ".." || // Special in `@(A..B)`
                    env.declarationMetadata(name) != null
            }
            else -> false
        }
        c.log("$interpMode ${macroEnv.call?.toPseudoCode()}, isDefined=$isDefined")
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
            ).also {
                c.log("${call?.toPseudoCode()} -> $it")
            }
        }
        // It's not defined, so we need to rewrite it.

        val name = (operator as? NameLeaf)?.content
        // If we get `+=` then we need to do a lot of work as if it's `+` and
        // then roll in the assignment part later.
        val (op, isCompoundAssignment) = run {
            val nameText = (name as? ParsedNameOrResolvedParsedName)?.asParsedName()?.nameText
            if (nameText != null && operands.size == 2) {
                val simpleOp = simpleBuiltinKeyFromCompoundOperator(nameText)
                if (simpleOp != null) {
                    simpleOp to true
                } else {
                    nameText to false
                }
            } else {
                nameText to false
            }
        }

        if (op != null) {
            val operatorSpecifier = when (operands.size) {
                2 -> "_${op}_"
                1 -> "${op}_"
                else -> null
            }
            // We have a lookup list of extensions for basic types like Int32 and String
            // so that evaluation of them can work even before Implicits.temper has staged
            // to the point where we can dispatch to methods on well-known types'.
            val builtins = builtinOperatorSpecs[operatorSpecifier] ?: listOf()
            c.group("DesugarOperation ${macroEnv.stage}") {
                c.log("op=$op, isCompoundAssignment=$isCompoundAssignment")
                c.log("operatorSpecifier=$operatorSpecifier")
                c.group("operands") {
                    operands.forEach { it.target.toPseudoCode(c.textOutput) }
                }
                c.group("builtins") {
                    builtins.forEach { c.log("$it") }
                }
            }
            if (call != null && operatorSpecifier != null) {
                val exts = builtins.map { FunctionResolution(it) }
                val helper = DotHelper(ExternalCall, OperatorMember(operatorSpecifier), exts)
                val vHelper = Value(helper)
                if (interpMode == InterpMode.Partial) {
                    if (isCompoundAssignment) {
                        desugarCompoundOperation(
                            macroEnv,
                            operands[0],
                            operands[1],
                            plantSimpleOperator = {
                                // Plant the simpler operator and let
                                // a recursive invocation desugar that.
                                Rn(operator.pos, ParsedName(op))
                            },
                        ).also {
                            c.log("desugarCompound -> $it")
                        }?.let {
                            macroEnv.replaceMacroCallWith(it)
                        }
                    } else {
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
                if (!isCompoundAssignment || leftName != null) {
                    val helperTree = ValueLeaf(macroEnv.document, operator.pos, vHelper)
                    val operandTrees = operands.map { it.target }
                    var result = macroEnv.dispatchCallTo(helperTree, vHelper, operandTrees, interpMode)
                    if (isCompoundAssignment && result is Value<*>) {
                        if (interpMode == InterpMode.Full || result.stability == ValueStability.Stable) {
                            result = env.set(leftName!!.content, result, macroEnv)
                                .and { result }
                        }
                    }
                    return result
                }
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
    right: TEdge,
    plantSimpleOperator: Planting.(pos: Position) -> Unit,
): (Planting.() -> Unit)? {
    val leftTree = left.target
    if (leftTree is NameLeaf) {
        // No need to desugar
        return {
            Call(macroEnv.pos) {
                V(macroEnv.callee.pos, BuiltinFuns.vSetLocalFn)
                Replant(leftTree.copyLeft())
                Call(macroEnv.pos, vDesugarOperation) {
                    plantSimpleOperator(macroEnv.callee.pos)
                    Replant(leftTree.copyRight())
                    Replant(freeTarget(right))
                }
            }
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
            // The callee is stable not an arbitrarily large sub-tree / copyable.
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
                    Call(leftTree.pos, LeftHandOfMacro) {
                        Replant(leftTree.copy())
                        Call(macroEnv.pos, vDesugarOperation) {
                            plantSimpleOperator(macroEnv.callee.pos)
                            Replant(freeTarget(left))
                            Replant(freeTarget(right))
                        }
                    }
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
