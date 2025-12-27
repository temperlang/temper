package lang.temper.cli.repl

import lang.temper.common.Style
import lang.temper.common.TextOutput
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TextOutputTokenSink
import lang.temper.lexer.Lexer
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.lexer.reservedWords
import lang.temper.lexer.sourceRangeOf
import lang.temper.log.LogSink
import lang.temper.log.Position
import lang.temper.log.UnknownCodeLocation
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.CompletingParsedLine
import org.jline.reader.Highlighter
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.ParsedLine
import org.jline.reader.Parser
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * Bridges jline and [Repl] so that the latter can remain free of JVM-only dependencies.
 */
internal object ReplInterop {
    class TemperCompleterImpl(private val repl: Repl) : Completer {
        override fun complete(
            reader: LineReader?,
            line: ParsedLine?,
            candidates: MutableList<Candidate>?,
        ) {
            if (line != null && candidates != null) {
                completions(line).mapTo(candidates) { Candidate(it) }
            }
        }

        fun completions(line: ParsedLine): List<String> {
            require(line is ParsedLineImpl)
            var wordIndex = line.wordIndex()
            var tokens = line.tokens
            var offsetIntoTokenText = line.wordCursor()
            if (wordIndex == tokens.size || wordIndex == -1 || probablyIsCompleteToken(tokens[wordIndex])) {
                // Add a blank token at the end if there's no token that contains the cursor
                // or if the token is a token like `(` or `,` that is probably complete and so
                // which should not be consumed as part of the completion.
                tokens = buildList {
                    addAll(tokens)
                    add(TemperToken(line.pos.rightEdge, "", TokenType.Space, mayBracket = false))
                }
                wordIndex = tokens.lastIndex
                offsetIntoTokenText = 0
            }
            val completionContext = CompletionContext(
                tokens = tokens,
                cursorTokenIndex = wordIndex,
                offsetIntoTokenText = offsetIntoTokenText,
            )
            val suggestions = ReplCompletions(repl).suggest(completionContext)
            return if (wordIndex != line.wordIndex() && line.word().isNotEmpty()) {
                // We adjusted the completion position to after the word, so
                // add the word back to the completion.
                // That way we do not complete
                //       someFunctionForWhichThereIsAReplCompletionTemplate(
                // when the first argument could be "foo" to
                //       someFunctionForWhichThereIsAReplCompletionTemplate"foo"
                // instead of
                //       someFunctionForWhichThereIsAReplCompletionTemplate("foo"
                val word = line.word()
                suggestions.map { "${word.substring(0, line.wordCursor())}$it" }
            } else {
                suggestions
            }
        }
    }

    class TemperJlineHighlighterImpl(
        private val repl: Repl,
    ) : Highlighter {
        private val console = repl.console

        override fun highlight(reader: LineReader?, buffer: String?): AttributedString {
            if (buffer == null) {
                return AttributedString.EMPTY
            }
            val sb = AttributedStringBuilder()

            // Tokenize `buffer` as Temper tokens and replay onto an AttributeStringBuilder by
            // translating TextOutput styles to JLine AttributedStyles.
            class AttributeStringBuilderTextOutput : TextOutput() {
                override val isTtyLike: Boolean = console.textOutput.isTtyLike
                override fun emitLineChunk(text: CharSequence) {
                    sb.append(text)
                }

                override fun endLine() {
                    sb.append('\n')
                }

                override fun flush() {
                    // Output already on sb
                }

                var styles = mutableListOf<Style>()
                var jlineStyle = AttributedStyle.DEFAULT
                override fun startStyle(style: Style) {
                    styles.add(style)
                    updateJlineStyle()
                }

                override fun endStyle() {
                    styles.removeLastOrNull()
                    updateJlineStyle()
                }

                private fun updateJlineStyle() {
                    val oldJlineStyle = jlineStyle
                    var newJlineStyle = AttributedStyle.DEFAULT
                    for (style in styles) {
                        newJlineStyle = style.updateJlineStyle(newJlineStyle)
                    }
                    if (oldJlineStyle != newJlineStyle) {
                        sb.style(newJlineStyle)
                    }
                }
            }
            val textOutput = AttributeStringBuilderTextOutput()
            val tokenSink = TextOutputTokenSink(textOutput)
            var bufferStart: Int
            val fullInput = buildString {
                repl.appendPending(this)
                bufferStart = length
                append(buffer)
            }
            val lexer = Lexer(
                UnknownCodeLocation,
                LogSink.devNull,
                fullInput,
                ignoreTrailingSynthetics = true,
            )
            for (token in lexer) {
                val (pos, wholeTokenText, tokenType) = token
                if (token.synthetic) { continue }
                var tokenText = wholeTokenText
                if (pos.right <= bufferStart) {
                    continue
                } else if (pos.left < bufferStart) {
                    tokenText = tokenText.substring(bufferStart - pos.left)
                }
                if (tokenType == TokenType.Error) {
                    textOutput.startStyle(Style.ErrorToken)
                }
                tokenSink.emit(
                    OutputToken(
                        tokenText,
                        when (tokenType) {
                            TokenType.Comment -> OutputTokenType.Comment
                            TokenType.Number -> OutputTokenType.NumericValue
                            TokenType.Punctuation -> OutputTokenType.Punctuation
                            TokenType.LeftDelimiter,
                            TokenType.RightDelimiter,
                            TokenType.QuotedString,
                            -> OutputTokenType.QuotedValue
                            TokenType.Space -> if (tokenText == "\"") {
                                OutputTokenType.OtherValue
                            } else {
                                OutputTokenType.Space
                            }
                            TokenType.Word -> if (isProbablyWord(tokenText)) {
                                OutputTokenType.Word
                            } else {
                                OutputTokenType.Name
                            }
                            // Don't clobber fg color from error token style above
                            TokenType.Error -> OutputTokenType.OtherValue
                        },
                    ),
                )
                if (tokenType == TokenType.Error) {
                    textOutput.endStyle()
                }
            }
            return sb.toAttributedString()
        }

        private var errorPattern: Pattern? = null
        private var errorIndex: Int = 0

        override fun setErrorPattern(errorPattern: Pattern?) {
            this.errorPattern = errorPattern
        }

        override fun setErrorIndex(errorIndex: Int) {
            this.errorIndex = errorIndex
        }
    }

    class LineParser(private val repl: Repl) : Parser {
        override fun parse(line: String?, cursor: Int, context: Parser.ParseContext?): ParsedLine {
            // TODO Throw EOFError on incomplete for multiline editing, if we want that?
            // TODO See example: https://github.com/jline/jline3/commit/d0e191f4d1c3e218a572d67e611d22df9ceda08a
            var pos = cursor
            val text = StringBuilder()
            repl.appendPendingLines(text)
            val lineStart = text.length
            pos += lineStart
            text.append(line ?: "")

            var wordCursor = 0
            var wordIndex = -1

            val loc = UnknownCodeLocation

            val lexer = Lexer(
                loc,
                LogSink.devNull,
                sourceText = text,
                lang = StandaloneLanguageConfig,
            )
            val tokens = buildList {
                for (token in lexer) {
                    val tokenRange = lexer.sourceRangeOf(token)
                    val tokenStart = tokenRange.first
                    if (tokenStart >= lineStart) {
                        add(token)
                        if (tokenRange.last + 1 >= pos) {
                            if (!token.tokenType.ignorable) {
                                wordCursor = pos - tokenStart
                                wordIndex = lastIndex
                            }
                            break
                        }
                    }
                }
            }
            return ParsedLineImpl(
                line = line ?: "",
                cursor = cursor,
                tokens = tokens,
                wordIndex = wordIndex,
                wordCursor = wordCursor,
                pos = Position(loc, 0, pos),
            )
        }

        override fun isEscapeChar(ch: Char): Boolean = false // do not strip out backslashes
    }

    private data class ParsedLineImpl(
        val line: String,
        val cursor: Int,
        val tokens: List<TemperToken>,
        private val wordIndex: Int,
        private val wordCursor: Int,
        val pos: Position,
    ) : CompletingParsedLine {
        private val words = tokens.map { it.tokenText }
        override fun word(): String = words.getOrNull(wordIndex) ?: ""
        override fun wordCursor() = wordCursor
        override fun wordIndex(): Int = wordIndex
        override fun words() = words
        override fun line(): String = line
        override fun cursor(): Int = cursor
        override fun escape(candidate: CharSequence?, complete: Boolean) = candidate ?: ""
        override fun rawWordCursor(): Int = wordCursor()
        override fun rawWordLength(): Int = word().length
    }
}

fun (LineReaderBuilder).configExpandHistory(enabled: Boolean): LineReaderBuilder = also {
    option(LineReader.Option.DISABLE_EVENT_EXPANSION, !enabled)
}

fun (LineReaderBuilder).configTemperHistory(userDataDir: Path): LineReaderBuilder = also {
    variable(LineReader.HISTORY_FILE, userDataDir.resolve("repl-history.txt"))
}

private val probableWords = run {
    val words = mutableSetOf<String>()
    ambientNames.value.toStringCollection(words)
    words.addAll(reservedWords)
    words.add("as")
    words.add("const")
    words.add("do")
    words.add("else")
    words.add("export")
    words.add("false")
    words.add("for")
    words.add("if")
    words.add("import")
    words.add("is")
    words.add("let")
    words.add("orelse")
    words.add("return")
    words.add("true")
    words.add("var")
    words.add("when")
    words.add("while")
    words.toSet()
}
private fun isProbablyWord(tokenText: String) = tokenText in probableWords

internal fun (Style).updateJlineStyle(newJlineStyle: AttributedStyle) = when (this) {
    Style.LogDebugOutput -> newJlineStyle
    Style.LogVerboseOutput -> newJlineStyle
    Style.LogInfoOutput -> newJlineStyle
    Style.LogWarningOutput -> newJlineStyle.foreground(AttributedStyle.YELLOW)
    Style.LogErrorOutput -> newJlineStyle.foreground(AttributedStyle.RED)
    Style.NormalOutput -> newJlineStyle.underlineOff()
    Style.ErrorOutput -> newJlineStyle.foreground(AttributedStyle.RED)
    Style.SystemOutput -> newJlineStyle
    Style.UserInput -> newJlineStyle
    Style.CommentToken -> newJlineStyle.foreground(AttributedStyle.GREEN)
    Style.ErrorToken -> newJlineStyle.background(AttributedStyle.RED)
    Style.IdentifierToken -> newJlineStyle.underline()
    Style.KeyWordToken -> newJlineStyle.foreground(AttributedStyle.BLUE)
    Style.NumberToken -> newJlineStyle.foreground(AttributedStyle.MAGENTA)
    Style.PunctuationToken -> newJlineStyle.foreground(AttributedStyle.CYAN)
    Style.QuotedStringToken -> newJlineStyle.foreground(AttributedStyle.MAGENTA)
    Style.ValueToken -> newJlineStyle
}

private fun probablyIsCompleteToken(t: TemperToken) = t.tokenType == TokenType.Punctuation
