package lang.temper.astbuild

import lang.temper.ast.CstToken
import lang.temper.ast.FinishTree
import lang.temper.ast.LeftParenthesis
import lang.temper.ast.RightParenthesis
import lang.temper.ast.StartTree
import lang.temper.ast.ValuePart
import lang.temper.builtin.BuiltinFuns
import lang.temper.lexer.Operator
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
 * The regex literal is a series of tokens:
 *
 * - LeftDelimiter "/"
 * - Leaf
 *   - Any number of QuotedString pieces
 * - RightDelimiter "/g" or other letter flags following "/"
 *
 * `/foo/g` -> (V ("foo": String)) (V ("g": String))
 */
internal object RegexToArgs : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        // Gather string tokens for regex text and position.
        val limit = context.input.size
        val input = context.input

        val leftDelimiter = input.getOrNull(position)
        if (
            leftDelimiter !is CstToken ||
            leftDelimiter.tokenType != TokenType.LeftDelimiter || leftDelimiter.tokenText != "/" ||
            position + 1 >= limit
        ) {
            return -1
        }
        var index = position + 1
        val leftParen = input.getOrNull(index)
        if (leftParen !is LeftParenthesis || leftParen.operator != Operator.Leaf) {
            return -1
        }
        index += 1
        val tokens = buildList {
            while (index < limit) {
                val part = input[index]
                when {
                    part is CstToken && part.tokenType == TokenType.QuotedString -> add(part)
                    // We get a RightParenthesis when the string/regex content ends.
                    else -> break
                }
                index += 1
            }
        }
        val rightParen = input.getOrNull(index)
        if (rightParen !is RightParenthesis || rightParen.operator != Operator.Leaf) {
            return -1
        }
        index += 1
        // Pass raw text along as regex content for later parsing.
        val pattern = tokens.joinToString("") { it.tokenText }
        val rightDelimiter = input.getOrNull(index)
        if (rightDelimiter !is CstToken || rightDelimiter.tokenType != TokenType.RightDelimiter ||
            !rightDelimiter.tokenText.startsWith("/")
        ) {
            return -1
        }
        index += 1

        val flags = rightDelimiter.tokenText.substring(1)
        val fullRegex = when {
            flags.isEmpty() -> pattern
            // Flags can't be any of `(?/)`, so should be fine. See `isRegexLike` handling in `Lexer`.
            // Meanwhile, we don't actually handle this syntax yet, so reconsider it later.
            // We also don't support flags at all in our regex object model yet, so meh.
            else -> "(?/$flags)$pattern"
        }
        val output = context.output
        val pos = buildList {
            add(leftDelimiter)
            addAll(tokens)
            add(rightDelimiter)
        }.spanningPosition(leftDelimiter.pos)
        // Match the same construction as `rgx"..."` for now, where no interpolation is available.
        // List of single string template content.
        output.add(StartTree(pos.leftEdge))
        output.add(StartTree(pos.leftEdge))
        output.add(ValuePart(BuiltinFuns.vListifyFn, pos.leftEdge))
        output.add(FinishTree(pos.leftEdge, LeafTreeType.Value))
        output.add(StartTree(pos.leftEdge))
        output.add(ValuePart(Value(fullRegex, TString), pos))
        output.add(FinishTree(pos.rightEdge, LeafTreeType.Value))
        output.add(FinishTree(pos.rightEdge, InnerTreeType.Call))
        // Empty list of interpolation value content.
        output.add(StartTree(pos.rightEdge))
        output.add(StartTree(pos.rightEdge))
        output.add(ValuePart(BuiltinFuns.vListifyFn, pos.rightEdge))
        output.add(FinishTree(pos.rightEdge, LeafTreeType.Value))
        output.add(FinishTree(pos.rightEdge, InnerTreeType.Call))
        return index
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        diagramContext: GrammarDoc.Context,
    ): GrammarDoc.Component = GrammarDoc.NonTerminal("RegExp")

    override val children: List<Combinator> get() = emptyList()
}
