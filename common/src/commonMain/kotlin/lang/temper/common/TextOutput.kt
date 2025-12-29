package lang.temper.common

import kotlin.jvm.Synchronized

/** Interfaces to a text output channel. */
abstract class TextOutput : Flushable {
    /**
     * True if the output channel is TTY-like: will it interpret UNIX formatting directives
     * properly.  This should be true if the output is backed by a file descriptor for which
     * `isatty()` is true.
     */
    abstract val isTtyLike: Boolean

    private var currentStyle: Style? = null

    /** The width of a tab character. */
    val tabWidth: Int get() = TYPICAL_TAB_WIDTH

    open fun emitLine(line: CharSequence) {
        emitLineChunk(line)
        endLine()
    }

    abstract fun emitLineChunk(text: CharSequence)

    open fun endLine() {
        emitLineChunk("\n")
    }

    /** A noop if ![isTtyLike] */
    private fun emitTty(i: Int) {
        if (isTtyLike) {
            emitLineChunk("\u001B[${i}m")
        }
    }

    /** A noop if ![isTtyLike] */
    open fun emitTty(ttyCode: TtyCode) = emitTty(ttyCode.intValue)

    @Synchronized
    open fun atomicallyWithLevelAndStyle(newLevel: Log.LevelFilter, newStyle: Style, f: () -> Unit) {
        val oldStyle = currentStyle
        if (oldStyle != null && oldStyle != newStyle) {
            startStyle(newStyle)
        }
        try {
            f()
        } finally {
            if (newStyle != oldStyle) {
                endStyle()
                if (oldStyle != null) {
                    startStyle(oldStyle)
                }
            }
        }
    }

    open fun withLevel(level: Log.LevelFilter, action: () -> Unit): Unit = action()

    @Synchronized
    open fun startStyle(style: Style) {
        endStyle()
        this.currentStyle = style
        style.applyStart(this)
    }

    @Synchronized
    open fun endStyle() {
        val styleToEnd = this.currentStyle
        this.currentStyle = null
        styleToEnd?.applyEnd(this)
    }

    /** For test and debugging code. Should use `console` for most things. */
    object Stderr : TextOutput(), Appendable {
        override val isTtyLike: Boolean = true

        override fun emitLineChunk(text: CharSequence) {
            printErrNoEol("$text")
        }

        override fun endLine() {
            printErr("")
        }

        override fun append(value: Char): Appendable =
            append("$value")

        override fun append(value: CharSequence?): Appendable =
            append(value, 0, value!!.length)

        override fun append(
            value: CharSequence?,
            startIndex: Int,
            endIndex: Int,
        ): Appendable {
            printErrNoEol(value!!.substring(startIndex, endIndex))
            return this
        }

        override fun flush() {
            // already flushed via printErr
        }
    }
}

class ForkingTextOutput(private val subs: List<TextOutput>) : TextOutput() {
    override val isTtyLike: Boolean = subs.any { it.isTtyLike }
    override fun emitTty(ttyCode: TtyCode) {
        subs.forEach { it.emitTty(ttyCode) }
    }

    override fun emitLine(line: CharSequence) {
        subs.forEach { it.emitLine(line) }
    }

    override fun emitLineChunk(text: CharSequence) {
        subs.forEach { it.emitLineChunk(text) }
    }

    override fun endLine() {
        subs.forEach { it.endLine() }
    }

    override fun atomicallyWithLevelAndStyle(newLevel: Log.LevelFilter, newStyle: Style, f: () -> Unit) {
        val n = subs.size
        fun stack(i: Int) {
            if (i == n) {
                f()
            } else {
                subs[i].atomicallyWithLevelAndStyle(newLevel, newStyle) {
                    stack(i + 1)
                }
            }
        }
        stack(0)
    }

    override fun startStyle(style: Style) {
        subs.forEach { it.startStyle(style) }
    }

    override fun endStyle() {
        subs.forEach { it.endStyle() }
    }

    override fun withLevel(level: Log.LevelFilter, action: () -> Unit) {
        val n = subs.size
        fun stack(i: Int) {
            if (i == n) {
                action()
            } else {
                subs[i].withLevel(level) { stack(i + 1) }
            }
        }
        stack(0)
    }

    override fun flush() {
        subs.forEach { it.flush() }
    }
}

/** As per man(3) */
expect fun isatty(fd: Int): Boolean

/** Semantically meaningful styles that correspond to intellij *ConsoleViewContentType*s */
@Suppress("unused") // Entries correspond to external tool styles so may be used later.
enum class Style(
    val before: TtyCode?,
    val after: TtyCode?,
    val intellijCode: String,
) {
    LogDebugOutput(TtyCode.FgWhite, TtyCode.FgDefault, "LOG_DEBUG_OUTPUT"),
    LogVerboseOutput(TtyCode.Faint, TtyCode.NormalColorIntensity, "LOG_VERBOSE_OUTPUT"),
    LogInfoOutput(TtyCode.FgDefault, TtyCode.FgDefault, "LOG_INFO_OUTPUT"),
    LogWarningOutput(TtyCode.FgYellow, TtyCode.FgDefault, "LOG_WARNING_OUTPUT"),
    LogErrorOutput(TtyCode.FgRed, TtyCode.FgDefault, "LOG_ERROR_OUTPUT"),
    NormalOutput(TtyCode.Reset, null, "NORMAL_OUTPUT"),
    ErrorOutput(TtyCode.FgRed, TtyCode.FgDefault, "ERROR_OUTPUT"),
    SystemOutput(TtyCode.FgWhite, TtyCode.FgDefault, "SYSTEM_OUTPUT"),
    UserInput(TtyCode.Underline, TtyCode.UnderlineOff, "USER_INPUT"),

    CommentToken(TtyCode.FgGreen, TtyCode.FgDefault, "TEMPER_SYNTAX_COMMENT"),
    ErrorToken(TtyCode.FgRed, TtyCode.FgDefault, "TEMPER_SYNTAX_ERROR"),
    IdentifierToken(TtyCode.Underline, TtyCode.UnderlineOff, "TEMPER_SYNTAX_IDENTIFIER"),
    KeyWordToken(TtyCode.FgBlue, TtyCode.FgDefault, "TEMPER_SYNTAX_KEYWORD"),
    NumberToken(TtyCode.FgMagenta, TtyCode.FgDefault, "TEMPER_SYNTAX_NUMBER"),
    PunctuationToken(TtyCode.FgGrey, TtyCode.FgDefault, "TEMPER_SYNTAX_PUNCTUATION"),
    QuotedStringToken(TtyCode.FgMagenta, TtyCode.FgDefault, "TEMPER_SYNTAX_QUOTED_STRING"),
    ValueToken(TtyCode.FgMagenta, TtyCode.FgDefault, "TEMPER_SYNTAX_VALUE"),
    ;

    fun applyStart(textOutput: TextOutput) {
        if (before != null) textOutput.emitTty(before)
    }

    fun applyEnd(textOutput: TextOutput) {
        if (after != null) textOutput.emitTty(after)
    }
}

@Suppress(
    // These are from an external spec. Some we may never use.
    "Unused",
    "MagicNumber",
    // Keep the table in one piece.
    // Thanks, https://stackoverflow.com/questions/4842424/list-of-ansi-color-escape-sequences
    "MaxLineLength",
    // ASCII art table
    "LongLine",
)
enum class TtyCode(val intValue: Int) {
    // ║ 0        ║  Reset / Normal                ║  all attributes off                                                     ║
    Reset(0),

    // ║ 1        ║  Bold or increased intensity   ║                                                                         ║
    Bold(1),

    // ║ 2        ║  Faint (decreased intensity)   ║  Not widely supported.                                                  ║
    Faint(2),

    // ║ 3        ║  Italic                        ║  Not widely supported. Sometimes treated as inverse.                    ║
    Italic(3),

    // ║ 4        ║  Underline                     ║                                                                         ║
    Underline(4),

    // ║ 5        ║  Slow Blink                    ║  less than 150 per minute                                               ║
    // ║ 6        ║  Rapid Blink                   ║  MS-DOS ANSI.SYS; 150+ per minute; not widely supported                 ║
    // ║ 7        ║  [[reverse video]]             ║  swap foreground and background colors                                  ║
    ReverseVideo(7),

    // ║ 8        ║  Conceal                       ║  Not widely supported.                                                  ║
    // ║ 9        ║  Crossed-out                   ║  Characters legible, but marked for deletion.  Not widely supported.    ║
    CrossedOut(9),

    // ║ 10       ║  Primary(default) font         ║                                                                         ║
    DefaultFont(10),

    // ║ 11–19    ║  Alternate font                ║  Select alternate font `n-10`                                           ║
    // ║ 20       ║  Fraktur                       ║  hardly ever supported                                                  ║
    // ║ 21       ║  Bold off or Double Underline  ║  Bold off not widely supported; double underline hardly ever supported. ║
    // ║ 22       ║  Normal color or intensity     ║  Neither bold nor faint                                                 ║
    NormalColorIntensity(22),

    // ║ 23       ║  Not italic, not Fraktur       ║                                                                         ║
    NotItalic(23),

    // ║ 24       ║  Underline off                 ║  Not singly or doubly underlined                                        ║
    UnderlineOff(24),

    // ║ 25       ║  Blink off                     ║                                                                         ║
    // ║ 27       ║  Inverse off                   ║                                                                         ║
    InverseOff(27),

    // ║ 28       ║  Reveal                        ║  conceal off                                                            ║
    // ║ 29       ║  Not crossed out               ║                                                                         ║
    NotCrossedOut(29),

    // ║ 30–37    ║  Set foreground color          ║  See color table below                                                  ║
    FgBlack(30),
    FgRed(31),
    FgGreen(32),
    FgYellow(33),
    FgBlue(34),
    FgMagenta(35),
    FgCyan(36),
    FgWhite(37),

    // ║ 38       ║  Set foreground color          ║  Next arguments are `5;<n>` or `2;<r>;<g>;<b>`, see below               ║
    // ║ 39       ║  Default foreground color      ║  implementation defined (according to standard)                         ║
    FgDefault(39),

    // ║ 40–47    ║  Set background color          ║  See color table below                                                  ║
    BgBlack(40),
    BgRed(41),
    BgGreen(42),
    BgYellow(43),
    BgBlue(44),
    BgMagenta(45),
    BgCyan(46),
    BgWhite(47),

    // ║ 48       ║  Set background color          ║  Next arguments are `5;<n>` or `2;<r>;<g>;<b>`, see below               ║
    // ║ 49       ║  Default background color      ║  implementation defined (according to standard)                         ║
    BgDefault(49),

    // ║ 51       ║  Framed                        ║                                                                         ║
    // ║ 52       ║  Encircled                     ║                                                                         ║
    // ║ 53       ║  Overlined                     ║                                                                         ║
    // ║ 54       ║  Not framed or encircled       ║                                                                         ║
    // ║ 55       ║  Not overlined                 ║                                                                         ║
    // ║ 60       ║  ideogram underline            ║  hardly ever supported                                                  ║
    // ║ 61       ║  ideogram double underline     ║  hardly ever supported                                                  ║
    // ║ 62       ║  ideogram overline             ║  hardly ever supported                                                  ║
    // ║ 63       ║  ideogram double overline      ║  hardly ever supported                                                  ║
    // ║ 64       ║  ideogram stress marking       ║  hardly ever supported                                                  ║
    // ║ 65       ║  ideogram attributes off       ║  reset the effects of all of 60-64                                      ║
    // ║ 90–97    ║  Set bright foreground color   ║  aixterm (not in standard)                                              ║
    FgGrey(90),
    FgBrightRed(91),
    FgBrightGreen(92),
    FgBrightYellow(93),
    FgBrightBlue(94),
    FgBrightMagenta(95),
    FgBrightCyan(96),
    FgBrightWhite(97),

    // ║ 100–107  ║  Set bright background color   ║  aixterm (not in standard)                                              ║
    BgGrey(100),
    BgBrightRed(101),
    BgBrightGreen(102),
    BgBrightYellow(103),
    BgBrightBlue(104),
    BgBrightMagenta(105),
    BgBrightCyan(106),
    BgBrightWhite(107),
}

private const val TYPICAL_TAB_WIDTH = 8
