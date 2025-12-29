package lang.temper.astbuild

import lang.temper.ast.CstToken
import lang.temper.ast.FinishTree
import lang.temper.ast.StartTree
import lang.temper.ast.ValuePart
import lang.temper.builtin.BuiltinFuns
import lang.temper.lexer.TokenType
import lang.temper.log.spanningPosition
import lang.temper.value.InnerTreeType
import lang.temper.value.LeafTreeType
import lang.temper.value.TString
import lang.temper.value.Value

/**
 * Consumes a regex literal like `/foo/g` and emits two string values: one for the portion that is
 * the pattern text between the slashes, and one for the flag.
 *
 * `/foo/g` -> (V ("foo": String)) (V ("g": String))
 */
internal object RegexToArgs : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        // Gather up string tokens for regex text and position.
        val tokens = buildList {
            val input = context.input
            tokens@ for (index in position until input.size) {
                val part = input[index]
                when {
                    part is CstToken && part.tokenType == TokenType.QuotedString -> add(part)
                    // We get a RightParenthesis when the string/regex content ends.
                    else -> break@tokens
                }
            }
        }
        // Pass raw text along as regex content for later parsing.
        val tokenText = tokens.joinToString("") { it.tokenText }
        val len = tokenText.length
        val patternStart = 1
        var patternEnd = patternStart
        patternLoop@ while (patternEnd < len) {
            patternEnd += when (tokenText[patternEnd]) {
                '/' -> break@patternLoop
                '\\' -> 2
                else -> 1
            }
        }
        if (patternEnd < len && tokenText[patternEnd] == '/') {
            val flags = tokenText.substring(patternEnd + 1)
            val pattern = tokenText.substring(patternStart, patternEnd).let { main ->
                when {
                    flags.isEmpty() -> main
                    // Flags can't be any of `(?/)`, so should be fine. See `isRegexLike` handling in `Lexer`.
                    // Meanwhile, we don't actually handle this syntax yet, so reconsider it later.
                    // We also don't support flags at all in our regex object model yet, so meh.
                    else -> "(?/$flags)$main"
                }
            }
            val output = context.output
            val pos = tokens.spanningPosition(tokens.first().pos)
            // Match same construction as `rgx"..."` for now, where no interpolation is available.
            // List of single string template content.
            output.add(StartTree(pos.leftEdge))
            output.add(StartTree(pos.leftEdge))
            output.add(ValuePart(BuiltinFuns.vListifyFn, pos.leftEdge))
            output.add(FinishTree(pos.leftEdge, LeafTreeType.Value))
            output.add(StartTree(pos.leftEdge))
            output.add(ValuePart(Value(pattern, TString), pos))
            output.add(FinishTree(pos.rightEdge, LeafTreeType.Value))
            output.add(FinishTree(pos.rightEdge, InnerTreeType.Call))
            // Empty list of interpolation value content.
            output.add(StartTree(pos.rightEdge))
            output.add(StartTree(pos.rightEdge))
            output.add(ValuePart(BuiltinFuns.vListifyFn, pos.rightEdge))
            output.add(FinishTree(pos.rightEdge, LeafTreeType.Value))
            output.add(FinishTree(pos.rightEdge, InnerTreeType.Call))
            return position + tokens.size
        }
        return -1
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = GrammarDoc.NonTerminal("RegExp")

    override val children: List<Combinator> get() = emptyList()
}
