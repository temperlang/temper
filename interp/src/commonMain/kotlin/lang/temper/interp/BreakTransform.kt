package lang.temper.interp

import lang.temper.name.ParsedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.TemperName
import lang.temper.value.ControlFlow
import lang.temper.value.DefaultJumpSpecifier
import lang.temper.value.JumpLabel
import lang.temper.value.JumpSpecifier
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedJumpSpecifier
import lang.temper.value.Panic
import lang.temper.value.UnresolvedJumpSpecifier
import lang.temper.value.labelSymbol

/**
 * <!-- snippet: builtin/break -->
 * # `break` statement
 * `break` jumps to the end of a block.
 *
 * Without a label, `break` jumps to the narrowest surrounding [snippet/builtin/for],
 * [snippet/builtin/while], or [snippet/builtin/do] loop.
 *
 * ```temper
 * for (var i = 0; i < 10; ++i) {
 *   if (i == 3) { break; }
 *   console.log(i.toString());
 * }
 * //!outputs "0"
 * //!outputs "1"
 * //!outputs "2"
 * ```
 *
 * `break` may also be followed by a label, in which case, it jumps to just after the
 * statement with that label.
 *
 * ```temper
 * label: do {
 *   console.log("foo"); //!outputs "foo"
 *   break label;
 *   console.log("bar"); // never executed
 * }
 * ```
 *
 * A `break` that matches neither label nor loop within the same function or module body
 * is a compile-time error.
 *
 * ```temper FAIL
 * break;
 * ```
 *
 * ```temper FAIL
 * for (var i = 0; i < 10; ++i) {
 *   break label;
 * }
 * ```
 */
object BreakTransform : ControlFlowTransform("break") {
    override fun complicate(macroCursor: MacroCursor): ControlFlowSubflow? {
        val jumpSpecifier = jumpSpecifierFor(macroCursor) ?: return null
        return ControlFlowSubflow(
            ControlFlow.Break(macroCursor.macroEnvironment.pos, jumpSpecifier),
        )
    }

    fun jumpSpecifierFor(macroCursor: MacroCursor): JumpSpecifier? {
        var label: TemperName? = null
        if (macroCursor.consumeSymbol(labelSymbol)) {
            val labelTree = macroCursor.expectNameLeaf() ?: return null
            label = labelTree.content
        }
        return when {
            !macroCursor.isEmpty() -> null // garbage
            label == null -> DefaultJumpSpecifier
            label is JumpLabel -> NamedJumpSpecifier(label)
            label is ParsedName -> UnresolvedJumpSpecifier(label.toSymbol())
            label is ResolvedParsedName -> UnresolvedJumpSpecifier(label.baseName.toSymbol())
            else -> TODO("Deal with malformed label")
        }
    }

    override fun tryEmulate(env: MacroEnvironment) =
        throw Panic()
}
