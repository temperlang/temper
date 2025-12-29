package lang.temper.interp

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.name.NameMaker
import lang.temper.name.ParsedName
import lang.temper.stage.Stage
import lang.temper.type2.Signature2
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.RightNameLeaf
import lang.temper.value.StaylessMacroValue
import lang.temper.value.TEdge
import lang.temper.value.Value
import lang.temper.value.freeTarget

/**
 * An early stage macro that moves annotations outside the namespace of non-annotations
 * by converting calls like
 *
 *     nym`@`(name AnnotatedThing)
 *
 * into
 *
 *     nym`@name`(AnnotatedThing)
 *
 * <!-- snippet: builtin/@ -->
 * # `@`
 * Decorators like `@Foo` allow adapting or adjusting the behavior of definitions.
 *
 * TODO: write me
 *
 * ⎀ legacy-decorator
 */
private object ApplyDecoratorsMacro : StaylessMacroValue {
    override val sigs: List<Signature2>? get() = null

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        if (macroEnv.stage < Stage.DisAmbiguate) { return NotYet }
        if (args.size != 2 || args.key(0) != null || args.key(1) != null) {
            val fail = macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(2))
            macroEnv.replaceMacroCallWithErrorNode()
            return fail
        }
        val nameMaker = macroEnv.nameMaker
        val decoratorEdge = args.valueTree(0).incoming
            // Cannot rewrite.  Must have been called in an invalid macro delegation context.
            ?: return Fail
        val decoratedEdge = args.valueTree(1).incoming
            ?: return Fail
        // Three cases for the first argument to `@`:
        // 1. `@f()    ...` a call.
        // 2. `@f      ...` a function.  `@f ...` should be equivalent to `@f() ...`
        // 3. `@(A..B) ...` should be equivalent to `(.. @A @B)` to support stage ranges.

        // Once we've converted the first name to a decorator, we rework.
        //     (Call @ (Call convertedDecorator decoratorArgs) decoratedThing)
        // to
        //     (Call convertedDecorator decoratedThing decoratedArgs)
        // so the decorator is consistently the first positional argument, and any
        // keyword arguments and trailing blocks are properly trailing.

        // First, rewrite the decorator expression
        val isDecoratorValid =
            when (val prepender = namePrepender(decoratorEdge, nameMaker)) {
                null -> {
                    val decorator = decoratorEdge.target
                    if (decorator is CallTree && decorator.size >= 1) {
                        val calleeEdge = decorator.edge(0)
                        val callee = calleeEdge.target
                        val handledAsCase3 = if (
                            decorator.size == BINARY_OP_CALL_ARG_COUNT && callee is NameLeaf &&
                            callee.content.builtinKey == ".."
                        ) {
                            val operand0Edge = decorator.edge(1)
                            val operand1Edge = decorator.edge(2)
                            val operand0Prepender = namePrepender(operand0Edge, nameMaker)
                            val operand1Prepender = namePrepender(operand1Edge, nameMaker)
                            if (operand0Prepender != null && operand1Prepender != null) {
                                operand0Prepender()
                                operand1Prepender()
                                wrapInCall(decoratorEdge)
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                        if (handledAsCase3) {
                            true
                        } else { // Case 1
                            val case1Prepender = namePrepender(calleeEdge, nameMaker)
                            if (case1Prepender != null) {
                                case1Prepender()
                                true
                            } else {
                                false
                            }
                        }
                    } else {
                        false
                    }
                }
                else -> { // Case 2
                    prepender()
                    wrapInCall(decoratorEdge) // To get case 1 like result
                    true
                }
            }

        if (!isDecoratorValid || decoratorEdge.target !is CallTree) {
            macroEnv.replaceMacroCallWithErrorNode(
                LogEntry(
                    Log.Error,
                    MessageTemplate.MalformedAnnotation,
                    macroEnv.pos,
                    emptyList(),
                ),
            )
            return Fail
        }

        val decoratorCall = decoratorEdge.target as CallTree
        // Shift decorated into decorator call as zero-th positional argument
        decoratorCall.add(1, freeTarget(decoratedEdge))
        macroEnv.replaceMacroCallWith {
            Replant(freeTarget(decoratorEdge))
        }
        return NotYet
    }
}

internal val vApplyDecoratorsMacro = Value(ApplyDecoratorsMacro)

/**
 * If possible to do so, returns a lambda that converts a ParsedName like `foo` to `@foo`
 * so that we get a degree of name space between builtin annotations and local variables.
 *
 * We return a lambda instead of doing it immediately so that we can get transactional guarantees
 * when distributing over the arguments to infix `..` above.
 */
private fun namePrepender(
    decoratorEdge: TEdge,
    nameMaker: NameMaker,
): (() -> Unit)? {
    val decorator = decoratorEdge.target
    if (decorator is NameLeaf) {
        val name = decorator.content
        if (name is ParsedName) {
            return {
                // Prepend '@'
                decoratorEdge.replace(
                    RightNameLeaf(
                        decorator.document,
                        decorator.pos,
                        nameMaker.parsedName(
                            "@${(decorator.content as ParsedName).nameText}",
                        )!!,
                    ),
                )
            }
        }
    }
    return null
}

private fun wrapInCall(decoratorEdge: TEdge) =
    decoratorEdge.replace { p ->
        Call(p) {
            Replant(freeTarget(decoratorEdge))
        }
    }
