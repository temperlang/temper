package lang.temper.format

import lang.temper.common.Console
import lang.temper.common.LeftOrRight
import lang.temper.common.Log
import lang.temper.common.TextOutput
import lang.temper.log.Position

/**
 * Emits tokens to [textOutput] with styling if appropriate.
 */
class TextOutputTokenSink(
    private val textOutput: TextOutput,
) : TokenSink {
    override fun emit(token: OutputToken) {
        textOutput.startStyle(token.type.style)
        textOutput.emitLineChunk(token.text)
        textOutput.endStyle()
    }

    override fun endLine() {
        textOutput.endLine()
    }

    override fun finish() {
        // Nothing to do.
        // TODO: If Kotlin gets a Flushable type, check whether textOutput is flushable.
    }

    override fun position(pos: Position, side: LeftOrRight) {
        // Nothing to do.
    }
}

/**
 * Enable `console.logTokens(myTokenSerializable)`.
 */
fun Console.logTokens(
    obj: TokenSerializable?,
    singleLine: Boolean = false,
    level: Log.Level = Log.Info,
) {
    if (logs(level)) {
        FormattingHints.Default.makeFormattingTokenSink(
            TextOutputTokenSink(textOutput),
            singleLine = singleLine,
        ).use { tokenSink ->
            if (obj != null) {
                obj.renderTo(tokenSink)
            } else {
                tokenSink.emit(OutToks.nullWord)
            }
        }
    }
}
