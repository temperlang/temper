package lang.temper.format

import lang.temper.common.assertStringsEqual
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.value.TemperFormattingHints
import kotlin.test.Test

class FormattingTokenSinkTest {
    @Test
    fun colonAndEqAfterCurly() = assertFormatted(
        want = """
            |let {
            |  x
            |}: T = initialX;
            |let {
            |  y
            |} = initialY;
        """.trimMargin(),
    ) {
        it.emit(OutToks.letWord)
        it.emit(OutToks.leftCurly)
        it.emit(OutputToken("x", OutputTokenType.Name))
        it.emit(OutToks.rightCurly)
        it.emit(OutToks.colon)
        it.emit(OutputToken("T", OutputTokenType.Name))
        it.emit(OutToks.eq)
        it.emit(OutputToken("initialX", OutputTokenType.Name))
        it.emit(OutToks.semi)

        it.emit(OutToks.letWord)
        it.emit(OutToks.leftCurly)
        it.emit(OutputToken("y", OutputTokenType.Name))
        it.emit(OutToks.rightCurly)
        it.emit(OutToks.eq)
        it.emit(OutputToken("initialY", OutputTokenType.Name))
        it.emit(OutToks.semi)
    }

    @Test
    fun postfixQMark() = assertFormatted(
        want = """
            |C<D>?
        """.trimMargin(),
        formattingHints = TemperFormattingHints,
    ) {
        it.name(ParsedName("C"), false)
        it.emit(OutToks.leftAngle)
        it.name(ParsedName("D"), false)
        it.emit(OutToks.rightAngle)
        it.emit(OutToks.postfixQMark)
    }

    private fun assertFormatted(
        want: String,
        formattingHints: FormattingHints = FormattingHints.Default,
        format: (TokenSink) -> Unit,
    ) {
        val got = toStringViaTokenSink(
            singleLine = false,
            isTtyLike = false,
            formattingHints = formattingHints,
        ) {
            format(it)
        }

        assertStringsEqual(
            want.trimEnd(),
            got.trimEnd(),
        )
    }
}
