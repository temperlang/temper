package lang.temper.interp

import lang.temper.common.LeftOrRight
import lang.temper.env.InterpMode
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.ControlFlow
import lang.temper.value.Fail
import lang.temper.value.JumpLabel
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.Result
import lang.temper.value.SpecialFunction
import lang.temper.value.TEdge
import lang.temper.value.Value
import lang.temper.value.void
import lang.temper.value.whileSymbol

/**
 * <!-- snippet: builtin/do : `do` -->
 * # `do` loops and `do` blocks
 * `do` loops are like [snippet/builtin/for] and [snippet/builtin/while] except that the body
 * runs once before the condition is checked the first time.
 * This is the same as how these loops operate in many other languages.
 *
 * `do` differs in Temper that it can be used without a condition to do something once.
 * This can be useful when you need a block in the middle of a larger expression.
 *
 * <table markdown="1"><tr markdown="1"><th markdown="1">`do`</th><th markdown="1">`while`</th></tr>
 * <tr markdown="1"><td markdown="1">
 *
 * ``` mermaid
 * stateDiagram-v2
 *   [*] --> Body
 *   Body --> Condition
 *   Condition --> Body : true
 *   Condition --> [*] : false
 * ```
 *
 * </td><td markdown="1">
 *
 * ``` mermaid
 * stateDiagram-v2
 *   [*] --> Condition
 *   Condition --> Body : true
 *   Body --> Condition
 *   Condition --> [*] : false
 * ```
 *
 * </td></tr></table>
 *
 * ```temper
 * var i = 1;
 * // Prints "Done" initially, and goes back to do it again when `i` is (1, 0)
 * do {
 *   console.log("Done");
 * } while (i-- >= 0);
 *
 * //!outputs "Done"
 * //!outputs "Done"
 * //!outputs "Done"
 * ```
 *
 * versus
 *
 * ```temper
 * var i = 1;
 * // Prints "Done" when `i` is (1, 0)
 * while (i-- >= 0) {
 *   console.log("Done");
 * }
 *
 * //!outputs "Done"
 * //!outputs "Done"
 * ```
 *
 * You can see that when the condition is initially false, the body still executes once.
 *
 * ```temper
 * do {
 *   console.log("Done once"); //!outputs "Done once"
 * } while (false);
 * ```
 *
 * `continue` jumps to just before the condition, not over the condition to the top of the loop.
 *
 * ```temper
 * do {
 *   console.log("Done once"); //!outputs "Done once"
 *   continue;                 // jumps ━━━┓
 *   console.log("Not done");  //          ┃
 *   //     ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
 *   //     ┃
 * } while (false);
 * ```
 *
 * If you just want to do something once, you can omit the `while` clause.
 *
 * ```temper
 * console.log( //!outputs "Done once"
 *   do { "Done once" }
 * );
 * ```
 *
 * This allows you to group expressions, some for their side effects,
 * followed by another to compute a result.
 *
 * ```temper 8
 * // These related statements are now grouped together, and
 * // the `do` block can be nested in a complex expression.
 * do {
 *   var i = 0;  // Scoped
 *   i += 4;     // For side-effect
 *   i * 2       // Result
 * }
 * ```
 *
 * When `do` is followed by a curly bracket, the content between the
 * brackets are treated as statements, not object properties.
 *
 * ⎀ syntax/bag-preceders
 */
internal object DoTransform : ControlFlowTransform("do") {
    override fun complicate(macroCursor: MacroCursor): ControlFlowSubflow? {
        val env = macroCursor.macroEnvironment
        val body = macroCursor.rawBody() ?: return null
        if (macroCursor.isEmpty()) {
            //     do { ... }
            // is equivalent to
            //     do { ... } while (false)
            return ControlFlowSubflow(ControlFlow.Stmt(macroCursor.referenceTo(body)))
        }
        if (!macroCursor.consumeSymbol(whileSymbol)) {
            return null
        }
        // The argument following \while is a function that takes a function and calls it with
        // the condition.
        var condition: TEdge? = null
        val getConditionMacro = object : SpecialFunction, NamedBuiltinFun {
            override val sigs: List<Signature2>? get() = null
            override val name: String = "getWhileCondition"
            override fun invoke(
                macroEnv: MacroEnvironment,
                interpMode: InterpMode,
            ): Result {
                val subCursor = MacroCursor(macroEnv)
                val subCondition = subCursor.nextTEdge() ?: return Fail
                if (!subCursor.isEmpty()) { return Fail }
                // Adopt the edges so that they do not become inoperable when the sub-macro
                // invocation completes.
                condition = subCondition
                return void // Signal success.  Not the final return value for the `do` macro.
            }
        }

        // Use goingOutOfStyle to get the interpreter to construct a function value.
        // TODO: should be redundant with Full once we re-plumb interpretFun.
        val fn = macroCursor.macroEnvironment.goingOutOfStyle {
            macroCursor.evaluate(InterpMode.Full)
        }
        val result = if (fn is Value<*>) {
            env.apply(fn, ActualValues.from(Value(getConditionMacro)), InterpMode.Full)
        } else {
            Fail
        }

        if (!macroCursor.isEmpty() || result !is Value<*> || condition == null) {
            return null
        }

        val label = env.label
        if (label != null) {
            env.consumeLabel()
        }
        val pos = macroCursor.macroEnvironment.pos
        return ControlFlowSubflow(
            ControlFlow.Loop(
                pos = pos,
                label = label as JumpLabel?,
                checkPosition = LeftOrRight.Right,
                condition = macroCursor.referenceTo(condition),
                body = ControlFlow.StmtBlock.wrap(
                    ControlFlow.Stmt(macroCursor.referenceTo(body)),
                ),
                increment = ControlFlow.StmtBlock(pos.rightEdge, emptyList()),
            ),
        )
    }
}
