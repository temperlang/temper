package lang.temper.format

import lang.temper.common.AppendingTextOutput
import lang.temper.common.Console
import lang.temper.common.CustomValueFormatter
import lang.temper.common.FormatSink
import lang.temper.common.Log
import lang.temper.common.MISSING_VALUE_REPLACEMENT
import lang.temper.common.StringBuildingFormatSink
import lang.temper.common.Style
import lang.temper.common.TextOutput
import lang.temper.common.sprintfTo
import lang.temper.common.style
import lang.temper.log.CodeLocation
import lang.temper.log.CodeLocationKey
import lang.temper.log.CompilationPhase
import lang.temper.log.FilePositions
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.PositionFilter
import lang.temper.log.SharedLocationContext
import lang.temper.log.SimplifiesInLogMessage
import lang.temper.log.UnknownCodeLocation
import lang.temper.log.appendReadableLineAndColumn
import lang.temper.log.excerpt

open class ConsoleBackedContextualLogSink(
    private val localConsole: Console?,
    var sharedLocationContext: SharedLocationContext?,
    val parent: LogSink?,
    override val customValueFormatter: CustomValueFormatter,
    override val messageFilter: (MessageTemplateI) -> Boolean = { true },
    val simplifying: Boolean = false,
    val allowDuplicateLogPositions: Boolean = false,
) : FormattingLogSink, FilteringLogSink {
    private fun <T> doSynchronized(f: () -> T): T {
        return f()
    }

    private val positionFilter = PositionFilter()

    /** Specifically, reset the position filter. */
    override fun resetUsage() {
        positionFilter.reset()
    }

    /**
     * String to display after the position info.
     * By default, returns nothing.
     * Overrides show during which stage a problem was recognized.
     */
    open fun metadataSuffix(template: MessageTemplateI): String? = null

    override fun log(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        fyi: Boolean,
    ) {
        if (level >= Log.Fatal) {
            hasFatal = true
        }
        if (!allowDuplicateLogPositions) {
            val phase = (template as? MessageTemplate)?.stage
            if (
                phase != CompilationPhase.Staging && // Spammy.  Uses top-of-file positions a lot.
                phase != CompilationPhase.RuntimeEmulation // Allowed to loop.  Output important for tests
            ) {
                if (!positionFilter.allow(level, pos)) {
                    return
                }
            }
        }
        val shouldDisplay = !fyi &&
            messageFilter(template) &&
            doSynchronized { localConsole?.logs(level) == true }
        dispatchToSuper(level, template, pos, values, fyi = fyi || shouldDisplay)
        if (shouldDisplay) {
            check(localConsole != null) // Because the call to ?.logs return a non-null value

            val sharedLocationContext = this.sharedLocationContext

            // Collect positions so we can show snippets.
            val positions = mutableSetOf<Position>()

            // Buffer the output so that we can output the message adjacent to the snippet for that
            // position.
            val logString = buildString {
                val textOutput = AppendingTextOutput(
                    this,
                    isTtyLike = localConsole.textOutput.isTtyLike,
                )

                val logSinkValueFormatter = LogSinkValueFormatter(
                    simplifying = simplifying,
                    sharedLocationContext = sharedLocationContext,
                    textOutput = textOutput,
                    formattedPositions = positions,
                )

                val formatSink = TextOutputFormatSink(
                    textOutput = textOutput,
                    style = level.style,
                    customValueFormatter = CustomValueFormatter.chain(
                        customValueFormatter,
                        logSinkValueFormatter,
                    ),
                )

                val renderPos = pos.loc !is UnknownCodeLocation || !pos.isPseudoPosition
                if (renderPos) {
                    formatSink.formatAsTemplateString("[")
                    formatSink.formatValue(pos)
                    formatSink.formatAsTemplateString("]")
                    positions.add(pos)

                    logSinkValueFormatter.formatPositionsInContextOf(pos)
                }

                val metadataStr = metadataSuffix(template) ?: ""
                if (metadataStr != "") {
                    formatSink.formatReplacement(metadataStr)
                }

                if (renderPos || metadataStr != "") {
                    formatSink.formatAsTemplateString(": ")
                }

                sprintfTo(template.formatString, values, formatSink)
            }

            val sources = mutableMapOf<CodeLocation, CharSequence?>()
            if (sharedLocationContext != null) {
                for (posFromMessage in positions) {
                    if (posFromMessage.isPseudoPosition) { continue }
                    val loc = posFromMessage.loc
                    sources.getOrPut(posFromMessage.loc) {
                        sharedLocationContext[loc, CodeLocationKey.SourceCodeKey]
                    }
                }
            }

            doSynchronized {
                var logged = false
                if (sources.isNotEmpty()) {
                    localConsole.textOutput.withLevel(level) {
                        for (posFromMessage in positions) {
                            if (posFromMessage.isPseudoPosition) { continue }
                            val source = sources[posFromMessage.loc] ?: continue
                            // TODO: if we have positions that are close together,
                            // can we excerpt them together
                            excerpt(posFromMessage, source, localConsole.textOutput)
                            if (!logged && posFromMessage == pos) {
                                // Output the message below the snippet for it
                                // and then any other messages
                                localConsole.log(level = level, str = logString)
                                logged = true
                            }
                        }
                    }
                }
                if (!logged) {
                    localConsole.log(level = level, str = logString)
                }
            }
        }
    }

    private fun dispatchToSuper(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        fyi: Boolean,
    ) {
        parent?.log(
            level = level,
            template = template,
            pos = pos,
            values = values,
            fyi = fyi,
        )
    }

    final override var hasFatal: Boolean = false
        private set

    fun formatPosition(pos: Position, contextLoc: CodeLocation? = null) = buildString {
        formatPosition(pos, sharedLocationContext, contextLoc, StringBuildingFormatSink(this))
    }
}

/**
 * When connected to a TTY output, allows styling values distinctly from
 * template string content.
 *
 * Since the [customValueFormatter] bottoms out onto [LogSinkValueFormatter]
 * it will also style individual tokens from a [TokenSerializable] nicely.
 */
private class TextOutputFormatSink(
    val textOutput: TextOutput,
    val style: Style,
    val customValueFormatter: CustomValueFormatter,
) : FormatSink {
    override fun formatTemplateStringFragment(formatString: String, formatStringCharRange: IntRange) {
        formatAsTemplateString(formatString.substring(formatStringCharRange))
    }

    override fun formatAsTemplateString(charSequence: CharSequence) {
        textOutput.startStyle(style)
        textOutput.emitLineChunk(charSequence)
        textOutput.endStyle()
    }

    override fun formatReplacement(charSequence: CharSequence) {
        textOutput.startStyle(Style.ValueToken)
        textOutput.emitLineChunk(charSequence)
        textOutput.endStyle()
    }

    override fun formatValue(value: Any?) {
        val formatted = customValueFormatter.maybeFormat(value, this)
        if (formatted) { return }

        // We know that value is not TokenSerializable
        // because the CustomValueFormatter would have handled that,
        // so this uses a simple fallback strategy that works well
        // for Kotlin numbers, booleans, and null.
        formatReplacement("$value")
    }

    override fun missingValueReplacement() {
        textOutput.startStyle(Style.ErrorToken)
        textOutput.emitLineChunk(MISSING_VALUE_REPLACEMENT)
        textOutput.endStyle()
    }
}

/**
 * Formats positions using any [sharedLocationContext]
 * to map character offsets to line/columns,
 * and collects positions onto [formattedPositions] so that the caller
 * can produce snippets.
 *
 * When formatting a position, it separately formats the [CodeLocation]
 * allowing any separate custom formatter that is chained to this one
 * to customize location presentation.
 * This is used by the REPL to relate virtual paths like `-repl/` to
 * a more readable form.
 *
 * This also delegates formatting to the [TokenSerializable] form.
 */
internal class LogSinkValueFormatter(
    val simplifying: Boolean,
    val sharedLocationContext: SharedLocationContext?,
    val textOutput: TextOutput?,
    val formattedPositions: MutableCollection<Position>?,
) : CustomValueFormatter {
    private var contextPos: Position? = null

    fun formatPositionsInContextOf(pos: Position) {
        contextPos = pos
    }

    override fun maybeFormat(value: Any?, sink: FormatSink): Boolean = when {
        simplifying && value is SimplifiesInLogMessage -> {
            sink.formatValue(value.simplerLoggable)
            true
        }
        value is Position -> {
            formattedPositions?.add(value)
            formatPosition(value, sharedLocationContext, contextPos?.loc, sink)
        }
        value is CodeLocation -> {
            sink.formatReplacement(value.diagnostic)
            true
        }
        value is TokenSerializable -> {
            // If we're not connected to a TextOutput, buffer and write via formatSink
            val stringBuilder =
                if (textOutput == null) { StringBuilder() } else { null }

            var tokenSink: TokenSink = FormattingHints.Default.makeFormattingTokenSink(
                TextOutputTokenSink(
                    if (stringBuilder != null) {
                        AppendingTextOutput(stringBuilder)
                    } else {
                        textOutput!!
                    },
                ),
            )
            if (simplifying) {
                tokenSink = SimplifyingTokenSink(tokenSink)
            }
            tokenSink.use {
                value.renderTo(tokenSink)
            }
            if (stringBuilder != null) {
                sink.formatReplacement(stringBuilder)
            }
            true
        }
        else -> false
    }
}

/** Pseudo-positions are those zero-width positions at the top of a file for which we don't dump snippets. */
val Position.isPseudoPosition get() = right == 0

/** @return true if the position was written as by the contract of [CustomValueFormatter.maybeFormat] */
private fun formatPosition(
    pos: Position,
    sharedLocationContext: SharedLocationContext?,
    contextLoc: CodeLocation?,
    out: FormatSink,
): Boolean {
    val loc = pos.loc
    val isPseudoPosition = pos.isPseudoPosition
    val filePositions: FilePositions? =
        if (!isPseudoPosition) {
            sharedLocationContext?.get(loc, CodeLocationKey.FilePositionsKey)
        } else {
            null
        }

    return if (isPseudoPosition) {
        if (loc is UnknownCodeLocation) {
            false
        } else {
            out.formatValue(loc)
            true
        }
    } else {
        val startAndEnd = filePositions?.spanning(pos)
        if (startAndEnd != null) {
            if (loc != contextLoc) {
                out.formatValue(loc)
            }
            out.formatReplacement(
                buildString {
                    append(':')
                    appendReadableLineAndColumn(startAndEnd.first, startAndEnd.second, this)
                },
            )
            true
        } else {
            false
        }
    }
}

/**
 * A TokenSink that [simplifies Name tokens][INameOutputToken.simplify],
 * as from `foo__123` to `foo` or `SomeModule.nameOfExport` to `nameOfExport`.
 */
class SimplifyingTokenSink(val underlying: TokenSink) : TokenSink by underlying {
    override fun emit(token: OutputToken) {
        underlying.emit(
            when (token) {
                is INameOutputToken -> token.simplify()
                else -> token
            },
        )
    }
}
