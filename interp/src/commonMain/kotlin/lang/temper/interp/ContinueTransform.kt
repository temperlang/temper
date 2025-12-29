package lang.temper.interp

import lang.temper.value.ControlFlow
import lang.temper.value.MacroEnvironment
import lang.temper.value.Panic

/**
 * <!-- snippet: builtin/continue -->
 * # `continue` statement
 * A `continue` statement jumps back to the beginning of a loop.
 * In a [snippet/builtin/for] loop, it jumps back to the increment part.
 *
 * A `continue` without a label jumps back to the innermost containing loop.
 *
 * ```temper
 * for (var i: Int = 0; i < 4; ++i) {
 *   if (i == 1) { continue; }
 *   console.log(i.toString());
 * }
 * //!outputs "0"
 * // 1 is skipped
 * //!outputs "2"
 * //!outputs "3"
 * ```
 *
 * A `continue` with a label jumps back to the loop with that label.
 *
 * ```temper
 * outer:
 * for (var i: Int = 0; i < 2; ++i) {
 *   for (var j: Int = 0; j < 10; ++j) {
 *     if (j == 1) { continue outer; }
 *     // Reached once for each value of i, with j == 0
 *     console.log("(${i.toString()}, ${j.toString()})");
 *   }
 *   // Continue skips over this so it never runs
 *   console.log("Not reached");
 * }
 * //!outputs "(0, 0)"
 * //!outputs "(1, 0)"
 *
 * console.log("done"); //!outputs "done"
 * ```
 */
internal object ContinueTransform : ControlFlowTransform("continue") {
    override fun complicate(macroCursor: MacroCursor): ControlFlowSubflow? {
        val jumpSpecifier = BreakTransform.jumpSpecifierFor(macroCursor) ?: return null
        return ControlFlowSubflow(
            ControlFlow.Continue(
                macroCursor.macroEnvironment.pos,
                jumpSpecifier,
            ),
        )
    }

    override fun tryEmulate(env: MacroEnvironment) =
        throw Panic()
}
