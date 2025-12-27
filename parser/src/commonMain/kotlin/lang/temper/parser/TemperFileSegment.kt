package lang.temper.parser

import lang.temper.common.Log
import lang.temper.common.subListToEnd
import lang.temper.lexer.LanguageConfig
import lang.temper.lexer.Lexer
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenSource
import lang.temper.lexer.TokenType
import lang.temper.log.CodeLocation
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position

const val SUPER_TOKEN_TEXT = ";;;"

enum class TemperFileSegment {
    Preface,
    Body,
}

data class TemperFileSegmentTokens(
    val tokens: TokenSource,
    val hasSuperTokens: Boolean,
)

/**
 * Get a token stream for the Temper source segment requested.
 * There are three broad structures of Temper files.
 *
 * First, a Temper file that only has a body.  It contains no tokens whose text is
 * [SUPER_TOKEN_TEXT].
 *
 *      console.log("Hello, World!");
 *
 * Second, a Temper file may have a preface: declarations that affect how the body is interpreted
 * including:
 *
 * - module parameter declarations
 * - module provides declarations
 * - declarations shared by all instances of the module
 *
 * This form has one [SUPER_TOKEN_TEXT] token in it.
 *
 * Third, a temper file may have a preface, a body, and tool-owned metadata in that order.
 * This form has two [SUPER_TOKEN_TEXT] tokens that surround the body.
 */
fun segmentTemperFile(
    codeLocation: CodeLocation,
    logSink: LogSink,
    languageConfig: LanguageConfig,
    textContent: String,
    segment: TemperFileSegment,
): TemperFileSegmentTokens {
    val logSinkWrapper = SuppressibleLogSink(logSink)

    val lexer = Lexer(
        codeLocation = codeLocation,
        logSink = logSinkWrapper,
        sourceText = textContent,
        lang = languageConfig,
    )

    // Scan looking for `;;;` tokens.  Delay publishing tokens or log messages until we know whether
    // there is one.
    val tokens = mutableListOf<TemperToken>()
    val superTokenPositions = mutableListOf<Position>()
    tokenLoop@
    while (lexer.hasNext()) {
        val token = lexer.next()
        val isSuperToken =
            token.tokenType == TokenType.Punctuation && token.tokenText == SUPER_TOKEN_TEXT
        // Non-metadata tokens should appear in exactly one segment.
        // Metadata tokens should appear in all because we need to associate metadata with
        // configuration files which do not have a preface.
        val emitToken = when {
            // Until we know whether there's a super token in the file, we don't know whether we're
            // in a preface or the body, so collect everything before seeing a first super token.
            // Above we clear collected lists when we realize this was a mistake.
            superTokenPositions.isEmpty() -> true
            // We need ;;; in the token stream so that the parse stage can distinguish between
            // code and metadata.
            isSuperToken -> true
            // For the body, keep all tokens.  We clear below when we realize we had a preface.
            segment == TemperFileSegment.Body -> true
            // Keep metadata tokens for all.
            else -> superTokenPositions.size >= 2
        }
        if (emitToken) {
            tokens.add(token)
        }

        if (isSuperToken) {
            superTokenPositions.add(token.pos)
            val countOfSuperTokens = superTokenPositions.size

            when (segment) {
                TemperFileSegment.Preface ->
                    logSinkWrapper.ignoring = countOfSuperTokens == 1
                TemperFileSegment.Body -> {
                    if (countOfSuperTokens == 1) {
                        // Discard tokens and messages related to the preface
                        tokens.clear()
                        logSinkWrapper.clear()
                    }
                }
            }
        }
    }

    // If it was a body-only module (no `;;;` token at all), and we wanted the preface, drop any
    // tokens or messages related to the body.
    if (segment == TemperFileSegment.Preface && superTokenPositions.isEmpty()) {
        tokens.clear()
        logSinkWrapper.clear()
    }

    if (superTokenPositions.size > 2) {
        logSinkWrapper.log(
            level = Log.Fatal, // Content is deeply ambiguous
            template = MessageTemplate.TooManySuperTokens,
            pos = superTokenPositions[2],
            values = emptyList(),
        )
    }

    logSinkWrapper.replay()
    return TemperFileSegmentTokens(
        ReplayTokenSource(codeLocation, tokens.toList()),
        hasSuperTokens = superTokenPositions.isNotEmpty(),
    )
}

private class ReplayTokenSource(
    override val codeLocation: CodeLocation,
    private val tokenList: List<TemperToken>,
    private var tokenIndex: Int = 0,
) : TokenSource {

    override fun toString(): String = tokenList.subListToEnd(tokenIndex).toString()
    override fun peek(): TemperToken? = tokenList.getOrNull(tokenIndex)

    override fun copy(logSink: LogSink?): TokenSource =
        ReplayTokenSource(codeLocation, tokenList, tokenIndex)

    override fun hasNext(): Boolean = tokenIndex in tokenList.indices

    override fun next(): TemperToken {
        if (tokenIndex >= tokenList.size) { throw NoSuchElementException() }
        return tokenList[tokenIndex++]
    }
}

private class SuppressibleLogSink(val logSink: LogSink) : LogSink {
    var ignoring: Boolean = false
    private val entries = mutableListOf<LogEntry>()

    fun clear() {
        entries.clear()
    }

    fun replay() {
        val toReplay = entries.toList()
        entries.clear()
        for (message in toReplay) {
            logSink.log(
                level = message.level,
                template = message.template,
                pos = message.pos,
                values = message.values,
                fyi = message.fyi,
            )
        }
    }

    override fun log(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        fyi: Boolean,
    ) {
        if (!ignoring) {
            entries.add(LogEntry(level, template, pos, values, fyi = fyi))
        }
    }

    override val hasFatal: Boolean get() =
        logSink.hasFatal || entries.any { it.level >= Log.Fatal }
}
