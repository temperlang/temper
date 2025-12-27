package lang.temper.format

import lang.temper.common.AppendingTextOutput
import lang.temper.common.LeftOrRight
import lang.temper.common.toStringViaTextOutput
import lang.temper.log.FilePositions
import lang.temper.log.Position

/** Allows code artifacts to describe themselves as a series of [OutputToken]s. */
interface TokenSink {
    /**
     * Give a position hint which may be used to break lines so that output tokens end up on the
     * same lines as the input token from which the code artifact being serialized was parsed.
     */
    fun position(pos: Position, side: LeftOrRight)

    /** Explicit line break. */
    fun endLine()

    fun emit(token: OutputToken)

    /** Flush any pending output. */
    fun finish()

    fun <T> use(use: (TokenSink) -> T): T =
        try {
            use(this)
        } finally {
            finish()
        }

    fun word(str: String) = emit(OutputToken(str, OutputTokenType.Word))
    fun number(str: String) = emit(OutputToken(str, OutputTokenType.NumericValue))
    fun quoted(str: String) = emit(OutputToken(str, OutputTokenType.QuotedValue))
    fun value(str: String) = emit(OutputToken(str, OutputTokenType.OtherValue))
    fun bracket(str: String) = emit(OutputToken(str, OutputTokenType.Punctuation, TokenAssociation.Bracket))
    fun punctuation(str: String) = emit(OutputToken(str, OutputTokenType.Punctuation, TokenAssociation.Unknown))
    fun infixOp(str: String) = emit(OutputToken(str, OutputTokenType.Punctuation, TokenAssociation.Infix))
    fun prefixOp(str: String) = emit(OutputToken(str, OutputTokenType.Punctuation, TokenAssociation.Prefix))
    fun postfixOp(str: String) = emit(OutputToken(str, OutputTokenType.Punctuation, TokenAssociation.Postfix))
    fun invisible(str: String) = emit(OutputToken(str, OutputTokenType.NotEmitted))
    fun space(str: String) = emit(OutputToken(str, OutputTokenType.Space))
    fun comment(str: String) = emit(OutputToken(str, OutputTokenType.Comment))
}

fun toStringViaTokenSink(
    formattingHints: FormattingHints = FormattingHints.Default,
    filePositions: FilePositions = FilePositions.nil,
    singleLine: Boolean = formattingHints.allowSingleLine && filePositions == FilePositions.nil,
    @Suppress("KotlinConstantConditions") // parameter default expression
    isTtyLike: Boolean = AppendingTextOutput.DEFAULT_IS_TTY_LIKE,
    f: (TokenSink) -> Unit,
): String = toStringViaTextOutput(isTtyLike = isTtyLike) { textOutput ->
    val out = TextOutputTokenSink(textOutput)
    formattingHints.makeFormattingTokenSink(
        out,
        filePositions = filePositions,
        singleLine = singleLine,
    ).use { tokenSink ->
        f(tokenSink)
    }
}

fun Appendable.toAppenderViaTokenSink(
    formattingHints: FormattingHints = FormattingHints.Default,
    filePositions: FilePositions = FilePositions.nil,
    singleLine: Boolean = formattingHints.allowSingleLine && filePositions == FilePositions.nil,
    @Suppress("KotlinConstantConditions") // A parameter default
    isTtyLike: Boolean = AppendingTextOutput.DEFAULT_IS_TTY_LIKE,
    f: (TokenSink) -> Unit,
) {
    val textOutput = AppendingTextOutput(this, isTtyLike = isTtyLike)
    try {
        val sink = TextOutputTokenSink(textOutput)
        try {
            formattingHints.makeFormattingTokenSink(
                sink,
                filePositions = filePositions,
                singleLine = singleLine && formattingHints.allowSingleLine,
            ).use { tokenSink ->
                f(tokenSink)
            }
        } finally {
            sink.finish()
        }
    } finally {
        textOutput.flush()
    }
}

/**
 * Renders [left], then this iterable's elements separated by [sep], followed by [right].
 * A null [left], [right], or [sep] corresponds to no tokens.
 */
fun Iterable<TokenSerializable>.join(
    out: TokenSink,
    left: TokenSerializable? = null,
    sep: TokenSerializable? = OutToks.comma,
    right: TokenSerializable? = null,
) {
    left?.renderTo(out)
    for ((i, e) in this.withIndex()) {
        if (i != 0) {
            sep?.renderTo(out)
        }
        e.renderTo(out)
    }
    right?.renderTo(out)
}

/** `(el0, el1, el2)` */
fun Iterable<TokenSerializable>.joinParens(
    out: TokenSink,
    sep: TokenSerializable? = OutToks.comma,
) = join(out, left = OutToks.leftParen, sep = sep, right = OutToks.rightParen)

/** `[el0, el1, el2]` */
fun Iterable<TokenSerializable>.joinSquareBrackets(
    out: TokenSink,
    sep: TokenSerializable? = OutToks.comma,
) = join(out, left = OutToks.leftSquare, sep = sep, right = OutToks.rightSquare)

/** `<el0, el1, el2>` */
fun Iterable<TokenSerializable>.joinAngleBrackets(
    out: TokenSink,
    sep: TokenSerializable? = OutToks.comma,
) = join(out, left = OutToks.leftAngle, sep = sep, right = OutToks.rightAngle)

/** Makes it easy to transform a sink. */
class WrappedTokenSink(private val out: TokenSink, private val emit: TokenSink.(OutputToken) -> Unit) : TokenSink {
    override fun position(pos: Position, side: LeftOrRight) = out.position(pos, side)
    override fun endLine() = out.endLine()
    override fun finish() = out.finish()

    /** Calls the parameterized emit on the wrapped sink. */
    override fun emit(token: OutputToken) = out.(emit)(token)
}
