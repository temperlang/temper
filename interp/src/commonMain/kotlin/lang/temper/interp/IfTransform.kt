package lang.temper.interp

import lang.temper.env.InterpMode
import lang.temper.log.spanningPosition
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.ControlFlow
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Result
import lang.temper.value.SpecialFunction
import lang.temper.value.TEdge
import lang.temper.value.Value
import lang.temper.value.elseIfSymbol
import lang.temper.value.elseSymbol
import lang.temper.value.void

/**
 * <!-- snippet: builtin/if -->
 * # `if`
 * `if` allows branching on a [snippet/builtin/Boolean] predicate.
 *
 * ```temper
 * if (true) {
 *   console.log("Runs");
 * }
 * if (false) {
 *   console.log("Does not run");
 * }
 * //!outputs "Runs"
 * ```
 *
 * An `if` may include an `else` block which is run when the predicate is [snippet/builtin/false].
 *
 * ```temper
 * if (false) {
 *   console.log("Does not run")
 * } else {
 *   console.log("Runs")
 * }
 * if (true) {
 *   console.log("Runs")
 * } else {
 *   console.log("Does not run")
 * }
 * //!outputs ["Runs", "Runs"]
 * ```
 *
 * An `else` can change to another `if` statement.
 *
 * ```temper
 * let twoPlusTwo = 2 + 2;
 * if (twoPlusTwo == 3) {
 *   console.log("Does not run")
 * } else if (twoPlusTwo == 4) {
 *   console.log("Runs")
 * } else {
 *   console.log("Does not run")
 * }
 * //!outputs "Runs"
 * ```
 *
 * Temper is an expression language, so `if` may be used outside an expression context.
 *
 * ```temper
 * let x = (if (true) { 42 } else { 0 });
 * x == 42
 * ```
 *
 * In some syntactically similar languages, you can skip the brackets (`{`...`}`) around
 * `if` and `else` bodies.
 * You can't do that in Temper; always put `{`...`}` around the bodies.
 *
 * ```temper FAIL
 * if (true) console.log("Runs"); else console.log("Does not run");
 * ```
 *
 * !!! note
 *     The reason for this is that in Temper, control-flow operators like `if` are not
 *     special syntactic forms.
 *     They're macros that take blocks as arguments and phrases like `else` and `else if`
 *     are named parameters to that macro which also must be passed blocks.
 *
 * Unlike C, conditions must be [snippet/type/Boolean] or a sub-type.
 *
 * ```temper FAIL
 * if (0) { console.log("truthy") } else { console.log("falsey") }
 * ```
 */
internal object IfTransform : ControlFlowTransform("if") {
    override fun complicate(macroCursor: MacroCursor): ControlFlowSubflow? {
        val env = macroCursor.macroEnvironment
        val doc = env.document
        val condition = macroCursor.nextTEdge() ?: return null
        val thenClause = macroCursor.rawBody() ?: return null
        val branches = mutableListOf<Pair<TEdge?, TEdge>>(condition to thenClause)
        var followChain: ((cursor: MacroCursor) -> PartialResult)? = null

        // `if ... else if ... else ...`
        // desugar to nested calls.
        // Create some macros so that we can recursively invoke the interpreter to extract content
        // from those recursive calls.
        val elseIfHandler = object : SpecialFunction, NamedBuiltinFun {
            override val name: String = "elseIfHandler"
            override val sigs: List<Signature2>? get() = null
            override fun invoke(
                macroEnv: MacroEnvironment,
                interpMode: InterpMode,
            ): PartialResult {
                require(macroEnv.document == doc)
                val subCursor = MacroCursor(macroEnv)
                val subCondition = subCursor.nextTEdge() ?: return Fail
                val subClause = subCursor.rawBody() ?: return Fail
                // Adopt the edges so that they do not become inoperable when the sub-macro
                // invocation completes.
                branches.add(subCondition to subClause)
                return followChain!!(subCursor)
            }
        }

        val elseHandler = object : SpecialFunction, NamedBuiltinFun {
            override val name: String = "elseHandler"
            override val sigs: List<Signature2>? get() = null
            override fun invoke(
                macroEnv: MacroEnvironment,
                interpMode: InterpMode,
            ): Result {
                val subCursor = MacroCursor(macroEnv)

                val subClause = subCursor.rawBody()
                    ?: return Fail
                branches.add(null to subClause)
                return if (macroCursor.isEmpty()) {
                    void // Signals success.  Not the final result for the `if` macro.
                } else {
                    Fail
                }
            }
        }

        val elseIfHandlerValue = Value(elseIfHandler)
        val elseHandlerValue = Value(elseHandler)

        @Suppress("AssignedValueIsNeverRead") // It's read below.
        followChain = { subCursor ->
            val handler = when {
                subCursor.consumeSymbol(elseIfSymbol) -> elseIfHandlerValue
                subCursor.consumeSymbol(elseSymbol) -> elseHandlerValue
                else -> null
            }
            val result = when (handler) {
                null -> void
                else -> {
                    // Use goingOutOfStyle to get the interpreter to construct a function value.
                    val fn: PartialResult = subCursor.macroEnvironment.goingOutOfStyle {
                        subCursor.evaluate(InterpMode.Full)
                    }
                    when (fn) {
                        is Value<*> ->
                            env.apply(fn, ActualValues.from(handler), InterpMode.Full)
                        else -> Fail
                    }
                }
            }
            if (subCursor.isEmpty()) {
                result
            } else {
                Fail
            }
        }

        return when (followChain(macroCursor)) {
            is Fail, NotYet -> null
            is Value<*> -> {
                val wholePos = macroCursor.macroEnvironment.pos
                // Atomize in lexical order so that child order in control flow mirrors lexical.
                val branchesAtomized = branches.map { (cond, body) ->
                    cond?.let { macroCursor.referenceTo(it) } to
                        ControlFlow.Stmt(macroCursor.referenceTo(body))
                }
                // Build branching subsystems from `else` backwards to `if`.
                val hasFinalElse = branches.last().first == null
                var branchIndex = branches.size
                var controlFlow: ControlFlow? =
                    if (hasFinalElse) {
                        null
                    } else {
                        // Having a `void` here makes sure that static checks like UseBeforeInit
                        // get a diagnostic position for error logging.
                        ControlFlow.Stmt(
                            macroCursor.referenceToVoid(macroCursor.macroEnvironment.pos.rightEdge),
                        )
                    }
                while (branchIndex != 0) {
                    branchIndex -= 1
                    val (cond, body) = branchesAtomized[branchIndex]
                    val pos = if (branchIndex == 0) {
                        wholePos
                    } else {
                        listOfNotNull(cond, body).spanningPosition(wholePos)
                    }
                    controlFlow = if (cond != null) {
                        ControlFlow.If(
                            pos = pos,
                            condition = cond,
                            thenClause = ControlFlow.StmtBlock.wrap(body),
                            elseClause = if (controlFlow != null) {
                                ControlFlow.StmtBlock.wrap(controlFlow)
                            } else {
                                ControlFlow.StmtBlock(body.pos.rightEdge, emptyList())
                            },
                        )
                    } else {
                        body
                    }
                }

                // `if` chains that don't end in an `else` should be typed as Void.
                // See LoopTransform comments on typing as for the problems with control-flow
                // constructs that start with a condition and which don't reliably follow it
                // with a typeable tree.
                if (controlFlow != null && !hasFinalElse) {
                    controlFlow = ControlFlow.StmtBlock(
                        controlFlow.pos,
                        listOf(
                            controlFlow,
                            ControlFlow.Stmt(
                                macroCursor.referenceToVoid(controlFlow.pos.rightEdge),
                            ),
                        ),
                    )
                }

                controlFlow?.let { ControlFlowSubflow(it) }
            }
        }
    }
}
