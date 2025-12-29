package lang.temper.parser

import lang.temper.common.assertStringsEqual
import lang.temper.common.testCodeLocation
import lang.temper.cst.CstComment
import lang.temper.lexer.CommentType
import lang.temper.lexer.Lexer
import lang.temper.lexer.TokenType
import lang.temper.log.LogSink
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenSourceAdapterTest {

    fun assertAdaptedTokens(
        want: String,
        input: String,
    ) {
        val got = buildString {
            val lexer = Lexer(testCodeLocation, LogSink.devNull, input)
            val tsa = TokenSourceAdapter(lexer, null)
            while (true) {
                val tok = tsa.get() ?: break
                if (isNotEmpty()) {
                    append(' ')
                }
                var tokenText = tok.tokenText
                if (tok.tokenType == TokenType.QuotedString) {
                    // Trim spaces from quoted content so we can compare it to the output
                    // when it's separated from its delimiters.
                    var start = 0
                    var end = tokenText.length
                    if (start < end && tokenText[start] == ' ') { start += 1 }
                    if (end > start && tokenText[end - 1] == ' ') { end -= 1 }
                    tokenText = tokenText.substring(start, end)
                }
                append(tokenText)
            }
        }

        assertStringsEqual(want, got, input)
    }

    fun assertAdaptedTokens(
        want: List<String>,
        input: String,
        wantedComments: List<Pair<CommentType, String>>? = null,
    ) {
        val gotCommentList = if (wantedComments == null) {
            null
        } else {
            mutableListOf<CstComment>()
        }
        val got = buildList {
            val lexer = Lexer(testCodeLocation, LogSink.devNull, input)
            val tsa = TokenSourceAdapter(lexer, comments = gotCommentList)
            while (true) {
                val tok = tsa.get() ?: break
                add(tok.tokenText)
            }
        }

        assertEquals(want, got, input)
        assertEquals(
            wantedComments,
            gotCommentList?.map {
                it.type to it.text
            },
        )
    }

    @Test
    fun parensForDoubleQuotedStringTemplate() = assertAdaptedTokens(
        input = "x=   \" .. \${ y +   \" .. \${ 0 } .. \"   } .. \" ",
        want = "x = ( \" .. \${ y + ( \" .. \${ 0 } .. \" ) } .. \" )",
    )

    @Test
    fun parensForBackQuotedStringTemplate() = assertAdaptedTokens(
        input = "x=   ` .. \${ ( y +   ` .. \${ 0 } .. `   ) } .. ` ",
        want = "x = ( ` .. \${ ( y + ( ` .. \${ 0 } .. ` ) ) } .. ` )",
    )

    @Test
    fun asi() = assertAdaptedTokens(
        input = """
        (
          {
            foo : {}
          }
        );

        x = {}
            .foo;

        do {
        }

        while (c) {
        }

        else();
        """.trimIndent(),
        want = "( { foo : { } } ) ; x = { } . foo ; do { } ; while ( c ) { } ; else ( ) ;",
    )

    @Test
    fun asiIssue72() = assertAdaptedTokens(
        input = """
        // From issue #72
        foo: { }
        (x); // <-- should probably stand alone as a parenthesized expression

        foo: {}
        [42].bar;  // <-- should probably stand alone as an operation on a list

        foo: {}
        + baz;  // <-- I could go either way on this.
        """.trimIndent(),
        want = "foo : { } ; ( x ) ; foo : { } ; [ 42 ] . bar ; foo : { } ; + baz ;",
    )

    @Test
    fun asiInBrackets() = assertAdaptedTokens(
        input = """
        x = [
          {}
          ,
          {}
        ]
        """.trimIndent(),
        want = "x = [ { } , { } ]",
    )

    @Test
    fun constIsWeird() = assertAdaptedTokens(
        // See the test-cases with `const` in BuildTreeTest to understand these in more context.
        input = """
        const x = 1;
        const f() {};
        const function f() {};
        const public x;
        public const x;
        """.trimIndent(),
        want = listOf(
            "@", "const", "let", "x", "=", "1", ";",
            "@", "const", "let", "f", "(", ")", "{", "}", ";",
            "@", "const", "function", "f", "(", ")", "{", "}", ";",
            "@", "const", "@", "public", "x", ";",
            "@", "public", "@", "const", "let", "x", ";",

        ),
    )

    @Test
    fun visibilityWithArbitraryDecorator() = assertAdaptedTokens(
        input = """
            public @test function f() {};
            @public @test function f() {};
            @test public function f() {};
            @test @public function f() {};
        """.trimIndent(),
        want = listOf(
            "@", "public", "@", "test", "function", "f", "(", ")", "{", "}", ";",
            "@", "public", "@", "test", "function", "f", "(", ")", "{", "}", ";",
            "@", "test", "@", "public", "function", "f", "(", ")", "{", "}", ";",
            "@", "test", "@", "public", "function", "f", "(", ")", "{", "}", ";",
        ),
    )

    @Test
    fun rubyBlocksCanChain() = assertAdaptedTokens(
        input = """
            |foo()
            |    .bar() { } // Semicolon not inserted here
            |    .baz { }   // Semicolon inserted here though.
            |boo()
        """.trimMargin(),
        want = listOf(
            "foo", "(", ")",
            ".", "bar", "(", ")", "{", "}", // No semicolon here
            ".", "baz", "{", "}", ";", // Semicolon
            "boo", "(", ")",
        ),
    )

    @Test
    fun infixOperatorsDoNotCallJoin() = assertAdaptedTokens(
        input = """
            |{ 0 / 0 } orelse 0;
            |({ prop: "value" } instanceof Thing);
        """.trimMargin(),
        want = listOf(
            "{", "0", "/", "0", "}", "orelse", "0", ";",
            "(", "{", "prop", ":", "(", "\"", "value", "\"", ")", "}", "instanceof", "Thing", ")", ";",
        ),
    )

    @Test
    fun charLiteral() = assertAdaptedTokens(
        input = "'a'",
        want = listOf(
            "(",
            "'",
            "a",
            "'",
            ")",
        ),
    )

    @Test
    fun multilineCommentBlocks() = assertAdaptedTokens(
        input = """
            |//   A
            |//  A A
            |// AAAAA
            |// A   A
            |
            |   //  SSSS
            |   // SS
            |   //  SSS
            |   //    SS
            |   // SSSS
            |
            | // CCCC
            |// CC
            |// CC
            |//  CCCC
            |
            |// III
            |//  I ${
            '\r' // CRLF is not two line breaks.
        }
            |//  I
            |// III
            |//${
            "" // A content-less line is not a break.
        }
            |// III
            |//  I
            |//  I
            |// III
        """.trimMargin(),
        want = emptyList(),
        wantedComments = listOf(
            CommentType.Line to
                """
                    |//   A
                    |//  A A
                    |// AAAAA
                    |// A   A
                """.trimMargin(),
            CommentType.Line to
                """
                    |//  SSSS
                    |   // SS
                    |   //  SSS
                    |   //    SS
                    |   // SSSS
                """.trimMargin(),
            CommentType.Line to
                """
                    |// CCCC
                    |// CC
                    |// CC
                    |//  CCCC
                """.trimMargin(),
            CommentType.Line to
                """
                    |// III
                    |//  I ${'\r'}
                    |//  I
                    |// III
                    |//
                    |// III
                    |//  I
                    |//  I
                    |// III
                """.trimMargin(),
        ),
    )
}
