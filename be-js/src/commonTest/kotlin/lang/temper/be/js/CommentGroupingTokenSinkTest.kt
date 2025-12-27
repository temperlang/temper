package lang.temper.be.js

import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.toStringViaTokenSink
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * These tests split on underscore.
 * `\\{` and `\\}` correspond to the special comment curlies from [JsDocTokens].
 */
class CommentGroupingTokenSinkTest {

    @Test
    fun someTokens() = assertGroupedTokens(
        input = "let_x_=_1_;",
        want = "let x = 1;",
    )

    @Test
    fun oneEmbeddedComment() = assertGroupedTokens(
        input = "x_;_\n_/**_\n_foo_{_\n_bar_\n_}_\n_*/_\n_baz_(_)_;",
        want = """
            |x;
            |/**
            | * foo {
            | *   bar
            | * }
            | */
            |baz();
        """.trimMargin(),
    )

    @Test
    fun oneEmbeddedCommentUsingCommentCurlies() = assertGroupedTokens(
        input = "x_;_\n_/**_\n_@_foo_\\{_bar_\\}_\n_*/_\n_baz_(_)_;",
        want = """
            |x;
            |/** @foo {bar} */
            |baz();
        """.trimMargin(),
    )

    @Test
    fun commentInsideBrackets() = assertGroupedTokens(
        input = "{_{_\n_/*_\n_foo_:_bar_;_\n_baz_:_boo_;_\n_*/_\n_f_(_)_;_}_}",
        want = """
            |{
            |  {
            |    /*
            |     * foo: bar;
            |     * baz: boo;
            |     */
            |    f();
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun unclosedNestedComments() = assertGroupedTokens(
        input = "/**_\n_// I have comments in my comments_\n_1_+_1_==_2_/*_\n_umm_\n_*/",
        want = """
            |/**
            | * // I have comments in my comments
            | * 1 + 1 == 2 /* umm * /
        """.trimMargin(),
    )

    private fun assertGroupedTokens(
        input: String,
        want: String,
    ) {
        val tokens = input.split("_").map { guessToken(it) }

        val got = toStringViaTokenSink(formattingHints = JsFormattingHints, singleLine = false) {
            val tokenSink = CommentGroupingTokenSink(it, JsFormattingHints)
            tokenSink.use {
                for (token in tokens) {
                    if (token.text == "\n" && token.type == OutputTokenType.Space) {
                        tokenSink.endLine()
                    } else {
                        tokenSink.emit(token)
                    }
                }
            }
        }

        assertEquals(want.trimEnd(), got.trimEnd())
    }

    private fun guessToken(tokenText: String): OutputToken = specialTokens[tokenText]
        ?: OutputToken(
            tokenText,
            when {
                likelyComment.find(tokenText) != null -> OutputTokenType.Comment
                tokenText in jsReservedWordsAndNames -> OutputTokenType.Word
                likelyName.find(tokenText) != null -> OutputTokenType.Name
                likelyNumeric.find(tokenText) != null -> OutputTokenType.NumericValue
                likelyQuoted.find(tokenText) != null -> OutputTokenType.QuotedValue
                tokenText[0] <= ' ' -> OutputTokenType.Space
                else -> OutputTokenType.Punctuation
            },
        )
}

private val specialTokens = mapOf(
    JsDocTokens.commentStart.text to JsDocTokens.commentStart,
    JsDocTokens.commentEnd.text to JsDocTokens.commentEnd,
    "/*" to OutputToken(
        "/*",
        JsDocTokens.commentStart.type,
        JsDocTokens.commentStart.association,
    ),
    "\\{" to JsDocTokens.openCurly,
    "\\}" to JsDocTokens.closeCurly,
)
private val likelyComment = Regex("""^/[*/]|[*]/$""")
private val likelyName = Regex("""^[_$]*[a-zA-Z]""")
private val likelyNumeric = Regex("""^[-+]?[.]?[0-9]""")
private val likelyQuoted = Regex("""^["'`]|[$][{]$""")
