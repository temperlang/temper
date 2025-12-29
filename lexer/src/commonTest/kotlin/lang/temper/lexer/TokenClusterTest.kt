package lang.temper.lexer

import lang.temper.common.LeftOrRight
import lang.temper.common.ListBackedLogSink
import lang.temper.common.assertStringsEqual
import lang.temper.common.compatRemoveLast
import lang.temper.common.mutSubListToEnd
import lang.temper.common.subListToEnd
import lang.temper.common.testCodeLocation
import lang.temper.common.toStringViaBuilder
import kotlin.test.Test

/**
 * Tests the [TokenCluster] transitions via the lexer.
 *
 * These test cases exercise sequences of token cluster transitions,
 * Each is an input that goes through the [Lexer].
 *
 * We filter out space and comment tokens and arrange the output tokens,
 * including synthetic tokens, to match the nesting implied by the token
 * cluster algorithm.
 *
 * Finally, we double-check some properties, like proper pairing of
 * non-error tokens.
 */
class TokenClusterTest {
    private fun assertClusters(
        input: String,
        want: String,
    ) {
        val got = toStringViaBuilder { sb ->
            val logSink = ListBackedLogSink()
            val lexer = Lexer(testCodeLocation, logSink, input.replace('”', '"'))

            // Keep track of scriptlets so we can keep indent up-to-date with them.
            val openers = mutableListOf<TokenCluster.Chunk>()
            val stored = mutableListOf<List<TokenCluster.Chunk>>()

            // Merge tokens that are not token-cluster-relevant for brevity
            var pendingText = ""

            fun emit(prefix: Char, text: String) {
                repeat(openers.size) { sb.append("  ") }
                sb.append(prefix)
                sb.append('\'')
                sb.append(
                    text
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n"),
                )
                sb.append('\'')
                sb.append('\n')
            }

            for (token in lexer) {
                val (_, tokenText, tokenType) = token
                val chunk = if (tokenType != TokenType.Error) {
                    TokenCluster.Chunk.from(tokenText)
                } else {
                    null
                }

                val prefix = when { // `diff` style markers
                    tokenType == TokenType.Error -> '-' // Shouldn't be there
                    token.synthetic -> '+' // Added, not in original text
                    else -> ' '
                }
                val bracketKind = when {
                    !token.mayBracket -> null
                    tokenType == TokenType.LeftDelimiter -> LeftOrRight.Left
                    tokenType == TokenType.RightDelimiter -> LeftOrRight.Right
                    tokenText in openBrackets -> LeftOrRight.Left
                    tokenText in closeBrackets -> LeftOrRight.Right
                    else -> null
                }

                if (prefix == ' ' && bracketKind == null) {
                    pendingText += tokenText
                    continue
                }

                if (pendingText.isNotEmpty()) {
                    emit(' ', pendingText)
                    pendingText = ""
                }

                if (bracketKind == LeftOrRight.Right) {
                    when (chunk) {
                        TokenCluster.Chunk.MultiQuote -> stored.compatRemoveLast()
                        TokenCluster.Chunk.ColonRightCurly -> {
                            // Emulate store
                            val scriptlet = openers.lastIndexOf(TokenCluster.Chunk.LeftCurlyColon)
                            stored[stored.lastIndex] = openers.subListToEnd(scriptlet + 1).toList()
                            openers.mutSubListToEnd(scriptlet + 1).clear()
                        }
                        else -> Unit
                    }
                    openers.compatRemoveLast()
                }
                emit(prefix, tokenText)
                if (bracketKind == LeftOrRight.Left) {
                    openers.add(chunk!!)
                    when (chunk) {
                        TokenCluster.Chunk.MultiQuote -> stored.add(emptyList())
                        TokenCluster.Chunk.LeftCurlyColon -> {
                            // Emulate restore
                            openers.addAll(stored[stored.lastIndex])
                            stored[stored.lastIndex] = emptyList()
                        }
                        else -> Unit
                    }
                }
            }

            if (pendingText.isNotEmpty()) {
                emit(' ', pendingText)
            }

            if (sb.endsWith('\n')) { sb.setLength(sb.length - 1) }
        }

        assertStringsEqual(want.replace('”', '"'), got)
    }

    @Test
    fun noBrackets6L6K() = assertClusters(
        input = "foo bar",
        want = """
            | 'foo bar'
        """.trimMargin(),
    )

    @Test
    fun bracketsOnly6H5L5J() = assertClusters(
        input = "{ foo bar }",
        want = """
            | '{'
            |   ' foo bar '
            | '}'
        """.trimMargin(),
    )

    @Test
    fun bracketUnclosed5K() = assertClusters(
        input = "{ foo bar ",
        want = """
            | '{'
            |   ' foo bar '
            |+'}'
        """.trimMargin(),
    )

    @Test
    fun stringInBrackets5D1L1D() = assertClusters(
        input = "{ ”foo bar }”",
        want = """
            | '{'
            |   ' '
            |   '”'
            |     'foo bar }'
            |   '”'
            |+'}'
        """.trimMargin(),
    )

    @Test
    fun nestedBrackets5H() = assertClusters(
        input = "{{",
        want = """
            | '{'
            |   '{'
            |  +'}'
            |+'}'
        """.trimMargin(),
    )

    @Test
    fun redundantCloseBracket6J() = assertClusters(
        input = "{}}{",
        want = """
            | '{'
            | '}'
            |-'}'
            | '{'
            |+'}'
        """.trimMargin(),
    )

    @Test
    fun dCOutsideString5F() = assertClusters(
        input = $$"{ ${}}",
        want = $$"""
            | '{'
            |   ' '
            |   '${'
            |   '}'
            | '}'
        """.trimMargin(),
    )

    @Test
    fun emptyStringInBrackets1D() = assertClusters(
        input = "{””}",
        want = """
            | '{'
            |   '”'
            |   '”'
            | '}'
        """.trimMargin(),
    )

    @Test
    fun mQStringInBracketsNewlineInMQ5E2B2E() = assertClusters(
        input = "{”””\n}",
        want = """
            | '{'
            |   '”””'
            |     '\n'
            |  +'”””'
            | '}'
        """.trimMargin(),
    )

    @Test
    fun unclosedMQ2L() = assertClusters(
        input = "{”””\n",
        want = """
            | '{'
            |   '”””'
            |     '\n'
            |  +'”””'
            |+'}'
        """.trimMargin(),
    )

    @Test
    fun simplerUnclosedBracket() = assertClusters(
        input = "{",
        want = """
            | '{'
            |+'}'
        """.trimMargin(),
    )

    @Test
    fun stringDelimiterInCurly() = assertClusters(
        input = "{”””\n",
        want = """
            | '{'
            |   '”””'
            |     '\n'
            |  +'”””'
            |+'}'
        """.trimMargin(),
    )

    @Test
    fun escapedEOLOutsideString5A() = assertClusters(
        input = "{ \\\n",
        want = """
            | '{'
            |   ' \\\n'
            |+'}'
        """.trimMargin(),
    )

    @Test
    fun escapedCharOutsideString5C() = assertClusters(
        input = "{ \\.",
        want = """
            | '{'
            |   ' \\.'
            |+'}'
        """.trimMargin(),
    )

    @Test
    fun eOLBetweenBrackets5B() = assertClusters(
        input = "{\n}",
        want = """
            | '{'
            |   '\n'
            | '}'
        """.trimMargin(),
    )

    @Test
    fun otherInBrackets() = assertClusters(
        input = "{;}",
        want = """
            | '{'
            |   ';'
            | '}'
        """.trimMargin(),
    )

    @Test
    fun closeCurlyAsChar1H1J1D() = assertClusters(
        input = "”{}”",
        want = """
            | '”'
            |   '{}'
            | '”'
        """.trimMargin(),
    )

    @Test
    fun dCInString1I() = assertClusters(
        input = "”:}”",
        want = """
            | '”'
            |   ':}'
            | '”'
        """.trimMargin(),
    )

    @Test
    fun interpInStringAfterOpenCurly1G1F3J() = assertClusters(
        input = $$"”{${x}”",
        want = $$"""
            | '”'
            |   '{'
            |   '${'
            |     'x'
            |   '}'
            | '”'
        """.trimMargin(),
    )

    @Test
    fun stmtBlockOpenInString() = assertClusters(
        input = "”{:{x}”",
        want = """
            | '”'
            |   '{:{x}'
            | '”'
        """.trimMargin(),
    )

    @Test
    fun nestedStringBothUnclosed3D1K3K() = assertClusters(
        input = $$"”${”",
        want = $$"""
            | '”'
            |   '${'
            |     '”'
            |    +'”'
            |  +'}'
            |+'”'
        """.trimMargin(),
    )

    @Test
    fun curlyAfterStringOpenThatMatchesOuter1J() = assertClusters(
        input = $$"”${”}",
        want = $$"""
            | '”'
            |   '${'
            |     '”'
            |       '}'
            |    +'”'
            |  +'}'
            |+'”'
        """.trimMargin(),
    )

    @Test
    fun curlyTokensInInterpolation3H() = assertClusters(
        input = $$"”${{}}”}",
        want = $$"""
            | '”'
            |   '${'
            |     '{'
            |     '}'
            |   '}'
            | '”'
            |-'}'
        """.trimMargin(),
    )

    @Test
    fun quotedStringInMultiQuotedString2D() = assertClusters(
        input = "”””\n”foo”\n",
        want = """
            | '”””'
            |   '\n”foo”\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun onePairQuotesInMultiQuotedString() = assertClusters(
        input = "”””\n”foo\n",
        want = """
            | '”””'
            |   '\n”foo\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun uncloseMQWithQuotesEndsWithEol2K() = assertClusters(
        input = "”””\n”foo”\n",
        want = """
            | '”””'
            |   '\n”foo”\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun uncloseMQWithQuotesEndsWithoutEol2K() = assertClusters(
        input = "”””\n”foo”",
        want = """
            | '”””'
            |   '\n”foo”'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun curliesInMQ2H2J() = assertClusters(
        input = "”””\n”{}",
        want = """
            | '”””'
            |   '\n”{}'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun endInInterp3K2K() = assertClusters(
        input = $$"”””\n”${x",
        want = $$"""
            | '”””'
            |   '\n”'
            |   '${'
            |     'x'
            |  +'}'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun nestedMQOuterUnclosed3E() = assertClusters(
        input = $$"”””\n”${”””\n”x\n}",
        want = $$"""
            | '”””'
            |   '\n”'
            |   '${'
            |     '”””'
            |       '\n”x\n'
            |    +'”””'
            |   '}'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun nestedMQInCurlies() = assertClusters(
        input = $$"”””\n”${{”””\n",
        want = $$"""
            | '”””'
            |   '\n”'
            |   '${'
            |     '{'
            |       '”””'
            |         '\n'
            |      +'”””'
            |    +'}'
            |  +'}'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun overlongMQ2E() = assertClusters(
        input = $$"”””\n”${”””\n”foo””””bar\n}\n",
        want = $$"""
            | '”””'
            |   '\n”'
            |   '${'
            |     '”””'
            |       '\n”foo””””bar\n'
            |    +'”””'
            |   '}'
            |   '\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun mqClose2E() = assertClusters(
        input = "”””\n”””",
        want = """
            | '”””'
            |   '\n”””'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun underLong2E() = assertClusters(
        input = $$"”””\n”${”””\n  ”foo””””bar\n}\n",
        want = $$"""
            | '”””'
            |   '\n”'
            |   '${'
            |     '”””'
            |       '\n  ”foo””””bar\n'
            |    +'”””'
            |   '}'
            |   '\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun nestedMQ2E2F() = assertClusters(
        input = $$"”””\n”${”””\n””””\n}\n”",
        want = $$"""
            | '”””'
            |   '\n”'
            |   '${'
            |     '”””'
            |       '\n””””\n'
            |    +'”””'
            |   '}'
            |   '\n”'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun stmtBlockEndInCharContent2I() = assertClusters(
        input = "”””\n”:}\n",
        want = """
            | '”””'
            |   '\n”:}\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun curliesInCompletedMQ() = assertClusters(
        input = "”””\n”{}\n",
        want = """
            | '”””'
            |   '\n”{}\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun closeCurlyAfterStmtBlockOpen4J4L() = assertClusters(
        input = "”””\n”{:}",
        want = """
            | '”””'
            |   '\n”'
            |   '{:'
            |    -'}'
            |  +':}'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun stuffInStmtBlocks2G4I4H5J5I() = assertClusters(
        input = "”””\n”{: do { :}chars{: } :}\n",
        want = """
            | '”””'
            |   '\n”'
            |   '{:'
            |     ' do '
            |     '{'
            |       ' '
            |   ':}'
            |   'chars'
            |   '{:'
            |       ' '
            |     '}'
            |     ' '
            |   ':}'
            |   '\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun someClosedInStmtBlockButNotAll4H5J() = assertClusters(
        input = "”””\n”{: do {{{ :}chars{: } :}\n",
        want = """
            | '”””'
            |   '\n”'
            |   '{:'
            |     ' do '
            |     '{'
            |       '{'
            |         '{'
            |           ' '
            |   ':}'
            |   'chars'
            |   '{:'
            |           ' '
            |         '}'
            |         ' '
            |   ':}'
            |   '\n'
            |  +'{:'
            |      +'}'
            |    +'}'
            |  +':}'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun tooManyClosersInStmtBlock4J() = assertClusters(
        input = "”””\n”{: } :}\n",
        want = """
            | '”””'
            |   '\n”'
            |   '{:'
            |     ' '
            |    -'}'
            |     ' '
            |   ':}'
            |   '\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun stringWithInterpInStmtBlock2G() = assertClusters(
        input = $$"”””\n  ”{: ”${ {",
        want = $$"""
            | '”””'
            |   '\n  ”'
            |   '{:'
            |     ' '
            |     '”'
            |       '${'
            |         ' '
            |         '{'
            |        +'}'
            |      +'}'
            |    +'”'
            |  +':}'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun unclosedMQ4K() = assertClusters(
        input = $$"”””\n”{: ”${ :}",
        want = $$"""
            | '”””'
            |   '\n”'
            |   '{:'
            |     ' '
            |     '”'
            |       '${'
            |         ' '
            |      +'}'
            |    +'”'
            |   ':}'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun stmtBlockCloserInSimpleStringInterp4I() = assertClusters(
        input = $$"”${ :}”",
        want = $$"""
            | '”'
            |   '${'
            |     ' '
            |    -':'
            |   '}'
            | '”'
        """.trimMargin(),
    )

    @Test
    fun eolBreaksString1B() = assertClusters(
        input = "”foo\n{x}",
        want = """
            | '”'
            |   'foo'
            |+'”'
            | '\n'
            | '{'
            |   'x'
            | '}'
        """.trimMargin(),
    )

    @Test
    fun escEolBreaksString1A() = assertClusters(
        input = "”foo\\\n{x}",
        want = """
            | '”'
            |   'foo'
            |  -'\\'
            |+'”'
            | '\n'
            | '{'
            |   'x'
            | '}'
        """.trimMargin(),
    )

    @Test
    fun escEolInMQ2A() = assertClusters(
        input = "”””\n”\\\n",
        want = """
            | '”””'
            |   '\n”\\\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun escEolInInterp3A() = assertClusters(
        input = $$"”${ \\\n }”",
        want = $$"""
            | '”'
            |   '${'
            |     ' \\\n '
            |   '}'
            | '”'
        """.trimMargin(),
    )

    @Test
    fun emptyString() = assertClusters(
        input = "””",
        want = """
            | '”'
            | '”'
        """.trimMargin(),
    )

    @Test
    fun emptyMQ6E() = assertClusters(
        input = "”””\n",
        want = """
            | '”””'
            |   '\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun closeCurlyAtStart6J() = assertClusters(
        input = "}",
        want = """
            |-'}'
        """.trimMargin(),
    )

    @Test
    fun closeStmtBlockAtTop6I() = assertClusters(
        input = ":}",
        want = """
            |-':'
            |-'}'
        """.trimMargin(),
    )

    @Test
    fun stmtCloserInBlock5I() = assertClusters(
        input = "{ :}",
        want = """
            | '{'
            |   ' '
            |  -':'
            | '}'
        """.trimMargin(),
    )

    @Test
    fun interpAtTop6F() = assertClusters(
        input = $$"${ }",
        want = $$"""
            | '${'
            |   ' '
            | '}'
        """.trimMargin(),
    )

    @Test
    fun longQuotesInSimpleString1E() = assertClusters(
        input = "”foo””””bar”",
        want = """
            | '”'
            |   'foo'
            | '"'
            | '”””'
            |   'bar”'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun longQuotesInUnclosedString1E() = assertClusters(
        input = "”foo””””",
        want = """
            | '”'
            |   'foo'
            | '”'
            | '”””'
            |+'”””'
        """.trimMargin(),
        // TODO: Should report an error on adjacent string delimiters.
    )

    @Test
    fun escInString6D1C() = assertClusters(
        input = "”\\n”",
        want = """
            | '”'
            |   '\\n'
            | '”'
        """.trimMargin(),
    )

    @Test
    fun escInMQString2C() = assertClusters(
        input = "”””\n”\\n\n”””",
        want = """
            | '”””'
            |   '\n”\\n\n”””'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun junkInInterp3A3B3C3F3G3I3L() = assertClusters(
        input = $$"”${\n\\\n \\x y ${ } {: } :}”",
        want = $$"""
            | '”'
            |   '${'
            |     '\n\\\n \\x y '
            |    -'$'
            |     '{'
            |       ' '
            |     '}'
            |     ' '
            |     '{'
            |       ': '
            |     '}'
            |     ' '
            |    -':'
            |   '}'
            | '”'
        """.trimMargin(),
    )

    @Test
    fun junkInStmtBlock4A4B4C4D4F4G() = assertClusters(
        input = $$"”””\n”{: x \\x \n \\\n ”x” ${ } {: } :}\n",
        want = """
            | '”””'
            |   '\n”'
            |   '{:'
            |     ' x \\x \n \\\n '
            |     '”'
            |       'x'
            |     '”'
            |     ' '
            |    -'$'
            |     '{'
            |       ' '
            |     '}'
            |     ' '
            |     '{'
            |       ': '
            |     '}'
            |     ' '
            |   ':}'
            |   '\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun mQInStmtBlock4E() = assertClusters(
        input = "”””\n”{: ”””\n”x\n:}\n",
        want = """
            | '”””'
            |   '\n”'
            |   '{:'
            |     ' '
            |     '”””'
            |       '\n”x\n'
            |    +'”””'
            |   ':}'
            |   '\n'
            |+'”””'
        """.trimMargin(),
    )

    @Test
    fun stmtBlockOpenInBlock5G() = assertClusters(
        input = "{ {: } }",
        want = """
            | '{'
            |   ' '
            |   '{'
            |     ': '
            |   '}'
            |   ' '
            | '}'
        """.trimMargin(),
    )

    @Test
    fun junkAtTopLevel6A6B6C6G() = assertClusters(
        input = "\\\n\\x\n{:}",
        want = """
            | '\\\n\\x\n'
            | '{'
            |  -':'
            | '}'
        """.trimMargin(),
    )
}
