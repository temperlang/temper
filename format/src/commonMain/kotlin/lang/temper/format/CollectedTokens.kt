package lang.temper.format

import lang.temper.common.LeftOrRight
import lang.temper.common.temperEscaper
import lang.temper.log.Position

/**
 * Allows delaying rendering to a [TokenSink] until later.
 */
class CollectedTokens private constructor(private val collected: List<Collected>) {
    fun replay(tokenSink: TokenSink, skipLastLinebreak: Boolean = false) {
        for ((i, c) in collected.withIndex()) {
            when (c) {
                is CollectedEmit -> tokenSink.emit(c.token)
                CollectedEndLine -> if (!skipLastLinebreak || i != collected.lastIndex) { tokenSink.endLine() }
                is CollectedPosition -> tokenSink.position(c.pos, c.side)
            }
        }
    }

    companion object {
        fun collect(f: (TokenSink) -> Unit): CollectedTokens {
            val collector = TokenCollector()
            f(collector)
            return CollectedTokens(collector.collected.toList())
        }
    }
}

private sealed interface Collected
private data class CollectedPosition(val pos: Position, val side: LeftOrRight) : Collected
private object CollectedEndLine : Collected {
    override fun toString(): String = "CollectedEndLine"
}
private data class CollectedEmit(val token: OutputToken) : Collected {
    override fun toString(): String = "CollectedEmit(${token.type}, ${temperEscaper.escape(token.text)})"
}

private class TokenCollector : TokenSink {
    val collected = mutableListOf<Collected>()

    override fun position(pos: Position, side: LeftOrRight) {
        collected.add(CollectedPosition(pos, side))
    }

    override fun endLine() {
        collected.add(CollectedEndLine)
    }

    override fun emit(token: OutputToken) {
        collected.add(CollectedEmit(token))
    }

    override fun finish() {
        // Nothing to do
    }
}
