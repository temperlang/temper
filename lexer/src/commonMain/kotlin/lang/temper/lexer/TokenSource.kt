package lang.temper.lexer

import lang.temper.log.CodeLocation
import lang.temper.log.LogSink

/**
 * Abstracts away the essentials of a Lexer so that we can more easily bridge the parser to
 * intermediate IDE abstractions.
 */
interface TokenSource : Iterator<TemperToken> {
    val codeLocation: CodeLocation

    /** `null` if this has no next token; otherwise returns without consuming the next token. */
    fun peek(): TemperToken?

    fun copy(logSink: LogSink?): TokenSource
}
