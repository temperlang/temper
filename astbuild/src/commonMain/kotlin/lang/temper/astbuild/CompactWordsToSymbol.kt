package lang.temper.astbuild

import lang.temper.ast.FinishTree
import lang.temper.ast.StartTree
import lang.temper.ast.TokenLeaf
import lang.temper.ast.ValuePart
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.name.Symbol
import lang.temper.value.LeafTreeType
import lang.temper.value.TSymbol
import lang.temper.value.Value

internal data class CompactWordsToSymbol(val words: Combinator) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val output = context.output
        val sizeBefore = output.size
        val after = words.apply(context, position)
        if (after < 0) {
            // Failed or there's one word that needs no joining.
            return -1
        }
        val joinedWords = StringBuilder()
        var started = false
        val positions = mutableListOf<Position>()
        for (i in sizeBefore until output.size) {
            val el = output[i]
            // Expect Start, TokenLeaf, End(RightName)
            positions.add(el.pos)
            when (el) {
                is StartTree -> {
                    if (started) { return -1 }
                    started = true
                }
                is FinishTree -> {
                    if (!started || el.type.treeType != LeafTreeType.RightName) { return -1 }
                    started = false
                }
                is TokenLeaf -> {
                    if (!started) { return -1 }
                    if (joinedWords.isNotEmpty()) { joinedWords.append('_') }
                    joinedWords.append(el.cstToken.tokenText)
                }
                else -> return -1
            }
        }
        if (started || joinedWords.isEmpty()) {
            return -1
        }
        val joinedPos = positions.spanningPosition(positions.getOrNull(0) ?: return -1)
        output.subList(sizeBefore, output.size).clear()
        output.add(StartTree(joinedPos.leftEdge))
        output.add(ValuePart(Value(Symbol(joinedWords.toString()), TSymbol), joinedPos))
        output.add(FinishTree(joinedPos.rightEdge, LeafTreeType.Value))
        return after
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = GrammarDoc.Group(
        words.toGrammarDocDiagram(g, inlinable),
        GrammarDoc.Comment("merged to symbol"),
    )

    override val children: List<Combinator> get() = listOf(words)
}
