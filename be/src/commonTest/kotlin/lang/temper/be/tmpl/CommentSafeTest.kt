package lang.temper.be.tmpl

import lang.temper.common.assertStringsEqual
import lang.temper.common.charCount
import lang.temper.common.decodeUtf16
import lang.temper.common.testCodeLocation
import lang.temper.common.toHex
import lang.temper.lexer.Lexer
import lang.temper.lexer.LexicalDefinitions
import lang.temper.lexer.TokenType
import lang.temper.log.LogSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentSafeTest {
    @Test
    fun empty() {
        assertStringsEqual("", commentSafe(""))
    }

    @Test
    fun identityForAlreadySafe() {
        for (str in listOf("foo", "bar", "it was the test of times", "--x++/42*z[w]")) {
            assertStringsEqual(str, commentSafe(str))
        }
    }

    @Test
    fun safeForLineComments() {
        assertStringsEqual("%0A", commentSafe("\n"))
        assertStringsEqual("%0D", commentSafe("\r"))
        assertStringsEqual("%0D%0A", commentSafe("\r\n"))
        for (
        str in listOf(
            "foo\nbar",
            "foo\n\nbar",
            "foo\r\nbar",
            "foo\n\rbar",
            "\n\n\n\n",
            "at the end\n",
            "// comment\n",
            "foo\u2028bar\u2029baz\u0085boo",
            "\\\n",
        )
        ) {
            val safe = commentSafe(str)
            var i = 0
            while (i < safe.length) {
                val cp = decodeUtf16(safe, i)
                i += charCount(cp)
                assertTrue(
                    !LexicalDefinitions.Companion.isLineBreak(cp),
                    "`$str` -> $safe: $i:U+${cp.toHex()}",
                )
            }
        }
    }

    @Test
    fun safeForBlockComments() {
        assertStringsEqual(
            "/foo/bar/baz//boo/*.txt",
            commentSafe("/foo/bar/baz//boo/*.txt"),
        )
        assertStringsEqual(
            "%2A/README.md",
            commentSafe("*/README.md"),
        )
        assertStringsEqual(
            "/foo/bar/*%2A/README.md",
            commentSafe("/foo/bar/**/README.md"),
        )
    }

    @Test
    fun flagWereCommentsNesting() {
        // Temper does not do nesting comments a la Ocaml (* ... (* ... *) ... *).
        // But point this code out if someone moves the lexer in that direction.
        // We would need to break "/*" sequences in that case
        // or otherwise preserve star/end correspondence.
        val nestingComment = "/* foo /* bar */ stillInComment */"
        val lexer = Lexer(testCodeLocation, LogSink.devNull, nestingComment)
        val tokens = buildList {
            lexer.forEach { token ->
                add(token.tokenText to token.tokenType)
            }
        }
        assertEquals(
            listOf(
                "/* foo /* bar */" to TokenType.Comment,
                " " to TokenType.Space,
                "stillInComment" to TokenType.Word,
                " " to TokenType.Space,
                "*/" to TokenType.Punctuation,
            ),
            tokens.toList(),
        )
    }
}
