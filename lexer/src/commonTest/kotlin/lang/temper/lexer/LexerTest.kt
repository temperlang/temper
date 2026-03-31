package lang.temper.lexer

import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.affectedByIssue11
import lang.temper.common.affectedByIssue1499
import lang.temper.common.asciiLowerCase
import lang.temper.common.assertStringsEqual
import lang.temper.common.kotlinBackend
import lang.temper.common.printErr
import lang.temper.common.subListToEnd
import lang.temper.common.temperEscaper
import lang.temper.common.testCodeLocation
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests in this file are pairs of lines: each line of Temper source code is followed by a
 * line of token end markers.
 * Each chunk of source code between two separators corresponds to one token from the lexer.
 *
 * The assertion filters out the odd lines to find the lexer input, then matches characters
 * in the metadata line below to come up with token metadata.
 *
 * For example,
 *
 *     >let foo  =  1;
 *     :  WS  W SP SNP
 *
 * The input line starts with `>` to indicate it is an input line.
 * The corresponding metadata line starts with `:` and is below the input line.
 *
 * The metadata lines specify how to derive the desired output:
 *
 * - The `W` below the last character of `let` indicates that the characters preceding
 *   constitute a _W_ORD token.
 * - The `S` below the spaces indicate that those characters constitute space tokens.
 *   If there were a multi-character space token, an `S` should only fall under the
 *   last character in the token.
 * - Similarly, the `W` below the last letter of `foo` indicates that the characters
 *   from just after the last metadata character (the S) to the character constitute
 *   another _W_ORD token.
 *
 * Characters in a metadata line below a character that does not end a token should be
 * spaces, or tabs if the character above is also a tab (so that things line up nicely).
 *
 * If a metadata line would be empty, for example because there is a multiline comment
 * token, it ands its `:` prefix, may be skipped.
 *
 * A `.` in a metadata line indicates a token break without specifying what type it
 * should be.
 *
 * So, for example, the test-case
 *
 *     >foo = bar***baz!
 *     :  W...  .  P   .
 *
 * corresponds to the input text
 *
 *     foo = bar***baz!
 *
 * and we expect the lexer to produce
 *
 * - token "foo"  of TokenType.Word
 * - token " "    of no particular type
 * - token "="    of no particular type
 * - token " "    of no particular type
 * - token "bar"  of no particular type
 * - token "***"  of TokenType.Punctuation
 * - token "baz!" of no particular type
 *
 * The lexer synthesizes some missing tokens so that later passes consistently
 * match brackets.
 *
 * Lower-case metadata indicates a synthesized token, so in the below, the
 * input is a string literal missing its close quote.  The `L` below the
 * first quote marks indicates that it is in the input, but the lower-case
 * `r` below the second indicates that it is inferred by the lexer.
 *
 *     >"foo"
 *     :L  Qr
 */
private data class TokenMetadata(val type: TokenType, val mayBracket: Boolean, val synthetic: Boolean)

private const val C = $$"${" // So $C in a string means ${ and takes the same space
internal const val Q3 = "\"\"\"" // So $Q3 in a string means """ and takes the same space

/**
 * Single character abbreviations for [TokenType]s used in test-case metadata lines.
 */
private val charToTokenMetadata: Map<Char, TokenMetadata> = buildMap {
    // Map common, non-bracketing types to their first letter
    TokenType.entries.forEach {
        val (key, mayBracket) = when (it) {
            TokenType.Comment -> 'C' to false
            TokenType.Number -> 'N' to false
            TokenType.Punctuation -> 'P' to false
            TokenType.Margin -> 'M' to false
            TokenType.LeftDelimiter -> 'L' to true
            TokenType.RightDelimiter -> 'R' to true
            TokenType.QuotedString -> 'Q' to false
            TokenType.Space -> 'S' to false
            TokenType.Word -> 'W' to false
            TokenType.Error -> 'E' to false
        }
        this[key] = TokenMetadata(it, mayBracket = mayBracket, synthetic = false)
    }

    // Some bracketing abbreviations
    this['B'] = TokenMetadata(TokenType.Punctuation, mayBracket = true, synthetic = false) // Bracket

    // Synthetic tokens are distinguished by lower-case variants of the above
    for ((k, v) in this.toList()) {
        this[k.asciiLowerCase()] = v.copy(synthetic = true)
    }
}

private val tokenMetadataToChar =
    charToTokenMetadata.map { (a, b) -> b to a }.toMap()

@Suppress("SpellCheckingInspection") // Metadata lines and regex flags are not English
class LexerTest {
    init {
        require(tokenMetadataToChar.size == charToTokenMetadata.size)
    }

    @Test
    fun simpleSegmentationTest() = assertTokenization(
        """
        >let x = 42;
        :  WSWSPS NPS
        >
        """.trimIndent(),
    )

    @Test
    fun simpleSegmentationTestInMarkdown() = assertTokenization(
        """
        >let x = 42;
        :           C
        >
        """.trimIndent(),
        languageConfig = MarkdownLanguageConfig(),
        affectedBy = listOf(kotlinBackend.affectedByIssue1499),
    )

    @Test
    fun codeBlocksInMarkdown() = assertTokenization(
        """
        ># Header
        >
        >Some **code**:
        >```temper
        :        CS
        >let x = "42"
        :  WSWSPSL QrS
        >```
        >
        >Some more commentary.
        >
        >```temper
        :        CS
        >f(); /* unfinished
        :WBBPS             C
        >```
        >
        >The end.
        :       C
        """.trimIndent(),
        languageConfig = MarkdownLanguageConfig(),
        wantedErrors = listOf(
            "46: Missing close quote!",
        ),
        affectedBy = listOf(kotlinBackend.affectedByIssue1499),
    )

    @Test
    fun immediateIndentedCodeBlockInMarkdown() = assertTokenization(
        """
        >    let x = 42;
        :   S  WSWSPS NP
        """.trimIndent(),
        languageConfig = MarkdownLanguageConfig(),
        affectedBy = listOf(kotlinBackend.affectedByIssue1499),
    )

    @Test
    fun stringEndingInBackslash() = assertTokenization(
        // (r) below includes a synthetic close quote that is not part of the input
        """
        >```temper
        :        CS
        >let x = "42\"
        :  WSWSPSL QErS
        >```
        >
        >The end.
        :        C
        >
        """.trimIndent(),
        languageConfig = MarkdownLanguageConfig(),
        wantedErrors = listOf(
            "21-22: `\\`: Unrecognized escape sequence in quoted string!",
            "22: Missing close quote!",
        ),
        affectedBy = listOf(kotlinBackend.affectedByIssue1499),
    )

    @Test
    fun adjacentSemilitDelimiters() = assertTokenization(
        """
        ># Header
        >```temper
        :        CS
        >```
        >
        >The end.
        :        C
        >
        """.trimIndent(),
        languageConfig = MarkdownLanguageConfig(),
        affectedBy = listOf(kotlinBackend.affectedByIssue1499),
    )

    @Test
    fun missingEndSemilitDelimiters() = assertTokenization(
        """
        ># Header
        >```temper
        :        CS
        >x
        :W
        >
        :S
        >```temper
        :LRL     Q
        >## Subheader
        :           QQ
        >```
        >
        >The end.
        :        C
        >`
        :r
        """.trimIndent(),
        languageConfig = MarkdownLanguageConfig(),
        wantedErrors = listOf(
            "59: Missing close quote!",
        ),
        affectedBy = listOf(kotlinBackend.affectedByIssue1499),
    )

    @Test
    fun markdownCodeBlockVariety() = assertTokenization(
        """
        >```temper inert
        >This is just a comment because inert.
        >```
        >Still a comment. But here is code:
        >
        :C
        >    let i = 1 + 2;
        :   S  WSWSPSNSPSNPS
        >    console.log(i.toString());
        :   S      WP  WBWP       WBBBP
        """.trimIndent(),
        languageConfig = MarkdownLanguageConfig(),
        affectedBy = listOf(kotlinBackend.affectedByIssue1499),
    )

    @Test
    fun markdownIndentedSkippedLines() = assertTokenization(
        """
            |>Here is markdown text.
            |>
            |:C
            |>    let text = $Q3
            |:   S  WS   WSPS  LQ
            |>      "Here is string content.
            |:     SM                       Q
            |>${
            // blank line in middle of quoted group contributes no chars
            ""
        }
            |:S
            |>      "
            |:     SMQ
            |>      "Here is more string content.
            |:     SM                            Q
            |>      $Q3;
            |:     S  rPS
            |>
            |>And more markdown text.
            |:                      C
        """.trimMargin(),
        languageConfig = MarkdownLanguageConfig(),
        affectedBy = listOf(kotlinBackend.affectedByIssue1499),
    )

    @Test
    fun doubleQuotedStringInterpolations() = assertTokenization(
        $$"""
        >{ foo("a=${ a } b=${ b }") }
        :BS  WBL Q BSWSB  Q BSWSBRBSB
        """.trimIndent(),
    )

    @Test
    fun backQuotedStringInterpolations() = assertTokenization(
        $$"""
        >{ foo(`a=${ a } b=${ b }`) }
        :BS  WBL Q BSWSB  Q BSWSBRBSB
        """.trimIndent(),
    )

    @Test
    fun multiQuotedStringInterpolations() = assertTokenization(
        """
        >$Q3
        :  LQ
        >"$C a }
        :M BSWSBQ
        >$Q3;
        :  rP
        """.trimIndent(),
    )

    @Test
    fun commentaryInMultiquotedString() = assertTokenization(
        """
        >$Q3
        :  LQ
        >  "Line 1
        : SM      Q
        >  // Between Line 1 and 2 is a comment
        : S                                   CS
        >  "Line 2
        : SM      Q
        >  /*
        : S
        >   * Block comments are ok too.
        >   "
        >  */
        :   CS
        >  "Line 3
        : SM      Q
        >$Q3;
        :  rP
        """.trimIndent(),
    )

    @Test
    fun multiQuotedStringWithLongEmbeddedQuoteRuns() = assertTokenization(
        """
        >$Q3
        :  LQ
        >"   not-at-start-of-line$Q3
        :M                          Q
        >"   $Q3" quotes embedded
        :M                       Q
        >"" first quote is ignored but second is content
        :M                                              Q
        >   $Q3; // Ok
        :  S  rPS    C
        """.trimIndent(),
    )

    @Test
    fun multiQuotedStringWithStatementEmbed() = assertTokenization(
        """
        >$Q3
        :  LQ
        >"line of character data
        :M                      Q
        >: embedded { statement }
        :MS       WSBS        WSBS
        >"another line of character data
        :M                              Q
        >   $Q3;
        :  S  rP
        """.trimIndent(),
    )

    @Test
    fun multiQuotedStringsMayNestQuoteRuns() = assertTokenization(
        // Triple-quoted content is allowed inside quintuple-quoted strings.
        """
        >$Q3
        :  LQ
        >"   Python doc-strings start and end with a $Q3 sequence like
        :M                                                            Q
        >"       $Q3
        :M          Q
        >"       some chars
        :M                 Q
        >"       $Q3
        :M          Q
        >    $Q3; // Ok
        :   S  rPS    C
        """.trimIndent(),
    )

    @Test
    fun interpolationInMultiQuotedStrings() = assertTokenization(
        // Triple-quoted content is allowed inside quintuple-quoted strings.
        """
        >$Q3
        :  LQ
        >"   chars $C expr } more chars$Q3
        :M        Q BS   WSB          Q  r
        """.trimIndent(),
    )

    @Test
    fun nestedMultiQuotedStrings() = assertTokenization(
        // Triple-quoted content is allowed inside quintuple-quoted strings.
        """
        >$Q3
        :  LQ
        >"   chars $C
        :M        Q BS
        >     $Q3
        :    S  LQ
        >    "nested chars
        :   SM            Q
        >   $Q3} more chars
        :  S  rB           Q
        >$Q3
        :  r
        """.trimIndent(),
    )

    @Test
    fun scriptlet() = assertTokenization(
        """
        >$Q3
        :  LQ
        >"Table of Contents:
        :M                  Q
        >: for (x in xs) {
        :MS  WSBWS WS WBSBS
        >" - $C x }
        :M  Q BSWSBQ
        >: }
        :MSBS
        >$Q3
        :  r
        """.trimIndent(),
    )

    @Test
    fun stringBrokenByNewlineThenInterpolation() = assertTokenization(
        $$"""
        >"unclosed"
        :L       QrS
        >${ a }
        : BSWS
        """.trimIndent(),
        wantedErrors = listOf(
            "9: Missing close quote!",
        ),
    )

    @Test
    fun endImmediatelyAfterInterpolation() = assertTokenization(
        $$"""
        >  "starts ok${ e }"
        : SL        Q BSWSBr
        """.trimIndent(),

        wantedErrors = listOf(
            "18: Missing close quote!",
        ),
    )

    @Test
    fun division() = assertTokenization(
        """
        >a/b
        :WPW
        """.trimIndent(),
    )

    @Test
    fun operatorsAndParentheses() = assertTokenization(
        """
        >a/(b/i)
        :WPBWPWB
        """.trimIndent(),
    )

    @Test
    fun commentsAndDivisionOperators() = assertTokenization(
        """
        >x/*a*//(y)/c/d/e
        :W    CPBWBPWPWPW
        """.trimIndent(),
    )

    @Test
    fun multiCharPunctuation() = assertTokenization(
        """
        >x += y++ => z
        :WS PSW PS PSWS
        >x+=y;
        :W PWPS
        >foo<<=bar(a,b, c=d)
        :  W  P  WBWPWPSWPWBS
        >foo::==bar*;
        :  W   P  WPP
        """.trimIndent(),
    )

    @Test
    fun splitCloseAngles() = assertTokenization(
        """
        >x:T<U<V>>= y >> z
        :WPWBWBWBBPSWS PSW
        """.trimIndent(),
    )

    @Test
    fun wildcardSeparation() = assertTokenization(
        """
        >T<*>
        :WBPB
        """.trimIndent(),
    )

    @Test
    fun angleBracketsCommentsAndExtends() = assertTokenization(
        """
        >C</*T, */U extends D/*, +V, -W*/>
        :WB      CWS      WSW           CB
        """.trimIndent(),
    )

    @Test
    fun nonAngleBracketOperators() = assertTokenization(
        """
        >a < b && c > d && [a <= b, c >= d] && e <=> f; a < b, c => d
        :WSPSWS PSWSPSWS PSBWS PSWPSWS PSWBS PSWS  PSWPSWSPSWPSWS PSW
        """.trimIndent(),
    )

    @Test
    fun shifty() = assertTokenization(
        """
        >(x <<< 1) | (y >>> 2)
        :BWS  PSNBSPSBWS  PSNB
        """.trimIndent(),
    )

    @Test
    fun shiftsNotAngles() = assertTokenization(
        """
        >T<<A | B>>(c)
        :W PWSPSW PBWB
        """.trimIndent(),
    )

    @Test
    fun oneAmpAllowedInAngles() = assertTokenization(
        """
        >T<A & B>(c)
        :WBWSPSWBBWB
        """.trimIndent(),
    )

    @Test
    fun oneBarAllowedInAngles() = assertTokenization(
        """
        >T<A | B>(c)
        :WBWSPSWBBWB
        """.trimIndent(),
    )

    @Test
    fun doubleBarAllowedInAnglesParenthesized() = assertTokenization(
        """
        >T<(A || B)>(c)
        :WBBWS PSWBBBWB
        """.trimIndent(),
    )

    @Test
    fun ltIsARegexPreceder() = assertTokenization(
        """
        >< /foo attr="value">/
        :PSL                Qr
        """.trimIndent(),

        wantedErrors = listOf(
            "20: Missing close quote!",
        ),
    )

    @Test
    fun multiSemis() = assertTokenization(
        """
        >a;;;b;;c;
        :W  PW PWP
        """.trimIndent(),
    )

    @Test
    fun equalsBrackets() = assertTokenization(
        """
        >obj={}
        :  WPBB
        """.trimIndent(),
    )

    @Test
    fun multiDots() = assertTokenization(
        """
        >a...b..c.d
        :W  PW PWPW
        """.trimIndent(),
    )

    @Test
    fun ignorables() = assertTokenization(
        """
        >/* only ignorable */ // tokens
        :                   CS        C
        """.trimIndent(),
    )

    @Test
    fun strings() = assertTokenization(
        """
        >['strings', "strings", "more\t\"strings\"", "'", '"',
        :BL      QR..L      QR..L   Q Q Q      Q QR..LQR..LQR..
        >"unterminated"
        :L           QrS
        >
        """.trimIndent(),
        wantedErrors = listOf(
            "67: Missing close quote!",
        ),
    )

    @Test
    fun stringEscapes() = assertTokenization(
        $$"""
        >"\x20Escaped at start with more later\u{20,$$C/*hi*/}b1,,  {21 }."
        :L   Q                               Q  B QP B     CB QPP QQ QQBQRS
        >$$Q3
        :  LQ
        >"Maybe\u0020here?
        :M    Q     Q     Q
        >"${hi}
        :M$${' '}B WBQ
        >$$Q3;
        :  rPS
        >raw"hi\u{$$C" t"}}here"
        :  WL Q  B BL QRBB   QRS
        >"\u{20"
        :L  B Q $${""}
        >
        :Q
        >}"
        :br
        """.trimIndent(),
        wantedErrors = listOf(
            "129: Missing close quote!",
        ),
    )

    @Test
    fun templatesEasier() = assertTokenization(
        $$"""
            |>$$Q3
            |:  LQ
            |>~Things: ${}
            |:M       Q BBQ
            |>:do {
            |:M WSBS
            |>  ~${i}, ${}
            |: SM BWB Q BBQ
            |>  // Comment inside loop after content.
            |: S                                    CS
            |>:}
            |:MBS
            |>~and so on$$Q3
            |:M        Q  r
        """.trimMargin(),
    )

    @Test
    fun templatesHarder() = assertTokenization(
        $$"""
            |>$$Q3
            |:  LQ
            |>:do {
            |:M WSBS
            |>  ~${i}
            |: SM BWBQ
            |>  ~, ${}
            |: SM Q BBQ
            |>  ~, ${}
            |: SM Q BBQ
            |>:}
            |:MBS
            |>~and so on$$Q3
            |:M        Q  r
        """.trimMargin(),
    )

    @Test
    fun templatesHarderMore() = assertTokenization(
        $$"""
            |>$$Q3
            |:  LQ
            |>:do {
            |:M WSBS
            |>  // Comment inside loop before content.
            |: S                                     CS
            |>  ~${i}
            |: SM BWBQ
            |>  :do { // Nested block.
            |: SM WSBS               CS
            |>    ~, ${}
            |:   SM Q BBQ
            |>  :}
            |: SMBS
            |>:}
            |:MBS
            |>~and so on$$Q3
            |:M        Q  r
        """.trimMargin(),
    )

    @Test
    fun lineCommentEnds() = assertTokenization(
        """
        >// line comment
        :              CS
        >ends
        :   W
        """.trimIndent(),
    )

    @Test
    fun commentsDoNotNest() = assertTokenization(
        """
        >/* comments do not /* nest */ */
        :                            CS P
        """.trimIndent(),
    )

    @Test
    fun numbers() = assertTokenization(
        """
        >/* numbers */ 0x1aEG 0 .00001 123.456 1. 1e-1 6E23
        :            CS     NSNS     NS      NSNPS   NS   NS
        > 1e+0 1L 4f 3.14159265e 1-1
        :S   NS NS NS          ESNPNS
        >
        """.trimIndent(),
        wantedErrors = listOf(
            "63-74: `3.14159265e`: Malformed number!",
        ),
    )

    @Test
    fun whenIsADotADecimalPoint() = assertTokenization(
        """
        >1.0
        :  NS
        >1.e
        :NPWS
        >1.e1
        :NP WS
        >1.e+1
        :NPWPNS
        >1D
        : NS
        >1.D
        :NPWS
        >1..toString
        :N P       WS
        >1.toString
        :NP       W
        """.trimIndent(),
    )

    @Test
    fun punctuationStopsAtCommentStart() = assertTokenization(
        """
        >foo+*/-/**/-*/
        :  W   P   C  P
        """.trimIndent(),
    )

    @Test
    fun punctuationStopsAtBracket() = assertTokenization(
        """
        > ?? ?. ? ?> ?) !(!)
        :S PS PSPSPPSPBSPBPB
        """.trimIndent(),
    )

    @Test
    fun shortComments() = assertTokenization(
        """
        >/*/      */
        :          CS
        >/**/ */
        :   CS P
        """.trimIndent(),
    )

    @Test
    fun dotBeforeNumbers() = assertTokenization(
        """
        >0...5
        :N P N
        """.trimIndent(),
    )

    @Test
    fun statementEscapes() = assertTokenization(
        $$"""
        >    foo = \(bar + ${ baz });
        :   S  WSPS B  WSPS BS  WSBBP
        """.trimIndent(),
    )

    @Test
    fun misnestedCurlies() = assertTokenization(
        """
        >{ { ... } } }
        :BSBS  PSBSBSE
        """.trimIndent(),
        wantedErrors = listOf(
            "12-13: `}`: Close bracket matches no open bracket!",
        ),
    )

    @Test
    fun nonLatinWordPersianWithZwnj() = assertTokenization(
        """
        >${"\u0646\u0627\u0645\u0647\u0627\u06CC"}
        :     W
        """.trimIndent(),
    )

    @Test
    fun nonNormalizedWord() = assertTokenization(
        // Four equivalent identifiers.  The third is in NFKC so is recognized as a word token.
        // All others are marked as error tokens.
        //
        // Examples taken from
        // ["Q: Are there any characters whose normalization forms under NFC, NFD, NFKC, and
        //   NFKD are all different?"](http://unicode.org/faq/normalization.html#6)
        """
        >${"\u03D3"}
        :ES
        >${"\u03D2\u0301"}
        : ES
        >${"\u038E"}
        :WS
        >${"\u03A5\u0301"}
        : ES
        >
        """.trimIndent(),
        wantedErrors = listOf(
            "0-1: `ϓ`: Identifier is not in Unicode normal form NFKC!",
            "2-4: `ϓ`: Identifier is not in Unicode normal form NFKC!",
            "7-9: `Ύ`: Identifier is not in Unicode normal form NFKC!",
        ),
        affectedBy = listOf(kotlinBackend.affectedByIssue11),
    )

    @Test
    fun nonLatinCaseSignificant() = assertTokenization(
        // Upper- and lower-case alpha: α != Α
        """
        >${'\u03B1'} != ${'\u0391'}
        :${'W' }S PS${'W'}
        """.trimIndent(),
    )

    @Test
    fun nonAlphabeticWords() = assertTokenization(
        // 🍪 != 😀
        """
        >${"\uD83C\uDF6A"} != ${"\uD83D\uDE00"}
        :${" W" }S PS${" W"}
        """.trimIndent(),
    )

    @Test
    fun breaksBetweenPunctuationAndWords() = assertTokenization(
        """
        >-x;
        :PWPS
        >--y;
        : PWPS
        >+z
        :PWS
        >!b
        :PWS
        >~bits
        :P   WS
        >x++
        :W PS
        >y--
        :W PS
        >let x: T = -.5;
        :  WSWPSWSPSP NPS
        >o.p.q(a,b);
        :WPWPWBWPWBPS
        >a = [1,];
        :WSPSBNPBP
        """.trimIndent(),
    )

    @Test
    fun moreIdentifiers() = assertTokenization(
        """
        >SHOUTY_CASE_IS_A_THING
        :                     WS
        >b4_nums_were_l33t
        :                WS
        >dashes-do-not-continue-words
        :     WP WP  WP       WP    WS
        >finally thereIsNoReservedKeyword/IdentifierDistinctionAtTheLexicalLevel //Phew
        :      WS                       WP                                     WS     C
        """.trimIndent(),
    )

    @Test
    fun excludedEmojisThatStartWithNumbers() = assertTokenization(
        // 5️⃣ is encoded as '5' '\uFE0F' '\u20E3'.
        // Make sure the '5' is not recognized as a legit number.
        """
        >${"5️⃣" }, ${"*️⃣" } and ${"#️⃣"}
        :${"  E"}PS${"  E"}S  WS${"  E"}
        """.trimIndent(),
        wantedErrors = listOf(
            "0-3: `5️⃣`: Emoji not allowed!",
            "5-8: `*️⃣`: Emoji not allowed!",
            "13-16: `#️⃣`: Emoji not allowed!",
        ),
    )

    @Test
    fun quotedIdentifiers() = assertTokenization(
        """
        >let `let` = "let";
        :  WSL  QRSPSL  QRPS
        >let `=` '='
        :  WSLQRSLQRS
        >
        """.trimIndent(),
    )

    @Test
    fun escapedIdentifiers() = assertTokenization(
        """
        >\i
        :PW
        """.trimIndent(),
    )

    @Test
    fun deepBracketNesting() = assertTokenization(
        """
        >{{{{{{{{{{{{{{{{{{{{{{
        :BBBBBBBBBBBBBBBBBBBBBBS
        >;
        :PS
        >}}}}}}}}}}}}}}}}}}}}}}
        :BBBBBBBBBBBBBBBBBBBBBB
        """.trimIndent(),
    )

    @Test
    fun deepStringNesting() = assertTokenization(
        $$"""
        >  "${"${"${"${"${"${"${"${"${"${
        : SL BL BL BL BL BL BL BL BL BL BS
        > "${
        :SL BS
        > 0
        :SNS
        > }"}"}"}"}"}"}"}"}"}"
        :SBRBRBRBRBRBRBRBRBRBRS
        > }"
        :SBR
        """.trimIndent(),
    )

    @Test
    fun mixingNestedString1() = assertTokenization(
        $$"""
        > ".${ `.${ x }"` }.`"
        :SLQ BSLQ BSWSBQRSB QR
        >
        """.trimIndent(),
    )

    @Test
    fun mixingNestedStrings2() = assertTokenization(
        $$"""
        > `.${ ".${ x }`" }."` .
        :SLQ BSLQ BSWSBQRSB QRSP
        """.trimIndent(),
    )

    @Test
    fun returnRegex() = assertTokenization(
        """
        >return / regex/i
        :     WSL     Q R
        """.trimIndent(),
    )

    @Test
    fun yieldRegex() = assertTokenization(
        """
        >yield / regex/i
        :    WSL     Q R
        """.trimIndent(),
    )

    @Test
    fun wordDiv() = assertTokenization(
        """
        >foo / divisor/i
        :  WSPS      WPW
        """.trimIndent(),
    )

    @Test
    fun numDiv() = assertTokenization(
        """
        >42 / divisor/i
        : NSPS      WPW
        """.trimIndent(),
    )

    @Test
    fun keywordDiv() = assertTokenization(
        """
        >false / divisor/i
        :    WSPS      WPW
        """.trimIndent(),
    )

    @Test
    fun callDiv() = assertTokenization(
        """
        >f()/divisor/i
        :WBBP      WPW
        """.trimIndent(),
    )

    @Test
    fun incrDiv() = assertTokenization(
        """
        >x++ /divisor/i
        :W PSP      WPW
        """.trimIndent(),
    )

    @Test
    fun decrDiv() = assertTokenization(
        """
        >x-- /divisor/i
        :W PSP      WPW
        """.trimIndent(),
    )

    @Test
    fun plusEqRegex() = assertTokenization(
        """
        >x += / regex/i.n
        :WS PSL     Q RPW
        """.trimIndent(),
    )

    @Test
    fun minusRegex() = assertTokenization(
        """
        >x - / regex/i.n
        :WSPSL     Q RPW
        """.trimIndent(),
    )

    @Test
    fun timesRegex() = assertTokenization(
        """
        >x * / regex/i.n
        :WSPSL     Q RPW
        """.trimIndent(),
    )

    @Test
    fun regexNoTail() = assertTokenization(
        """
        >/foo/
        :L  QR
        """.trimIndent(),
    )

    @Test
    fun regexBigTail() = assertTokenization(
        """
        >/foo/smigu
        :L  Q     R
        """.trimIndent(),
    )

    @Test
    fun regexBigOddTail() = assertTokenization(
        """
        >/foo/foo
        :L  Q   R
        """.trimIndent(),
    )

    @Test
    fun bangRegex() = assertTokenization(
        """
        >! /foo/i.something
        :PSL  Q RP        W
        """.trimIndent(),
    )

    @Test
    fun tildeRegex() = assertTokenization(
        """
        >x ~ /foo/i.something
        :WSPSL  Q RP        W
        """.trimIndent(),
    )

    @Test
    fun regexSpace() = assertTokenization(
        """
        >/a a/
        :L  QR
        """.trimIndent(),
    )

    @Test
    fun regexReplace() = assertTokenization(
        """
        >/foo/->bar/
        :L        QR
        """.trimIndent(),
    )

    @Test
    fun regexReplaceWithNothing() = assertTokenization(
        """
        >/foo/->/
        :L     QR
        """.trimIndent(),
    )

    @Test
    fun regexReplaceSpace() = assertTokenization(
        """
        >/foo/-> /
        :L      QR
        """.trimIndent(),
    )

    @Test
    fun regexThenComment() = assertTokenization(
        """
        >/foo/bar//stuff
        :L  Q   R      C
        """.trimIndent(),
    )

    @Test
    fun regexReplaceNonEnd() = assertTokenization(
        """
        >/foo/->bar/ foo
        :L        QRS  W
        """.trimIndent(),
    )

    @Test
    fun regexReplaceTail() = assertTokenization(
        """
        >/foo/->bar/i
        :L        Q R
        """.trimIndent(),
    )

    @Test
    fun regexReplaceIncomplete1() = assertTokenization(
        """
        >/foo/-
        :L  QRP
        """.trimIndent(),
    )

    @Test
    fun regexReplaceIncomplete2() = assertTokenization(
        """
        >/foo/->/
        :L     Qr
        """.trimIndent(),

        wantedErrors = listOf(
            "7: Missing close quote!",
        ),
    )

    @Test
    fun numTail() = assertTokenization(
        """
        >123D
        :   N
        """.trimIndent(),
    )

    @Test
    fun numBigTail() = assertTokenization(
        // Parse error, but not lexical error
        """
        >123Double
        :        N
        """.trimIndent(),
    )

    @Test
    fun numOddTail() = assertTokenization(
        // We used to support trailing ?! chars but don't anymore. This now just checks new behavior.
        """
        >123trouble?
        :         NP
        """.trimIndent(),
    )

    @Test
    fun quotedName() = assertTokenization(
        // nym`...` is treated specially so that it passes through the lex and parse stage as
        // a single name-like token.  Ambiguity relating to this is resolved later.
        """
        >nym`foo`
        :       W
        """.trimIndent(),
    )

    @Test
    fun escInQuotedName() = assertTokenization(
        """
        >nym`foo\`bar`
        :            W
        """.trimIndent(),
    )

    @Test
    fun quotedNamesMustClose() = assertTokenization(
        // Syntheric delimiter (r) inserted at end to match (L) delimiter.
        """
        >nym`foo\`bar`
        :  WL  Q Q  Qr
        """.trimIndent(),

        wantedErrors = listOf(
            "12: Missing close quote!",
        ),
    )

    @Test
    fun quotedNamesMustClose2() = assertTokenization(
        """
        >nym`foo\``
        :  WL  Q Qr
        """.trimIndent(),

        wantedErrors = listOf(
            "9: Missing close quote!",
        ),
    )

    @Test
    fun interpolationsNotAllowedInQuotedNames() = assertTokenization(
        $$"""
        >nym`foo ${.1}`
        :  WL   Q B NBR
        """.trimIndent(),
    )

    @Test
    fun quotedNamePrefixCaseSensitive() = assertTokenization(
        """
        >Nym`foo bar`
        :  WL      QR
        """.trimIndent(),
    )

    @Test
    fun fnTypeInAngleBrackets() = assertTokenization(
        """
            |>let x=new ListBuilder<fn (): Void>();
            |:  WSWP  WS          WB WSBBPS   WBBBP
        """.trimMargin(),
    )

    @Ignore // TODO: fix issue#495
    @Test
    fun multiCodePointEmojis() {
        val polarBear = "\uD83D\uDC3B\u200D\u2744"
        assertTokenization(
            // \uD83D\uDC3B\u200D\u2744 is 🐻‍❄️, Polar bear emoji.
            // Test polar bear as a word, and at the start and end of larger words.
            """
            >$polarBear
            :   WS
            >ZZZ$polarBear
            :      WS
            >${polarBear}ZZZ
            :      W
            """.trimMargin(),
        )
    }

    private fun assertTokenization(
        testCase: String,
        wantedErrors: List<String> = emptyList(),
        languageConfig: LanguageConfig = StandaloneLanguageConfig,
        affectedBy: List<Boolean> = emptyList(),
    ) {
        if (affectedBy.any { it }) {
            return
        }
        val want: List<Pair<String, TokenMetadata?>> = run {
            val lines = testCase.split('\n').filter { it.isNotEmpty() }
            buildList {
                val sb = StringBuilder()
                var lineIndex = 0
                while (lineIndex in lines.indices) {
                    val inputLineIndex = lineIndex
                    lineIndex += 1

                    var inputLine = lines[inputLineIndex]
                    require(inputLine.startsWith(">")) {
                        "Expected input line but line $inputLineIndex does not start with '>': $inputLine"
                    }
                    inputLine = inputLine.substring(1)
                    val metadataLine = if (lines.getOrNull(lineIndex)?.startsWith(':') == true) {
                        lines[lineIndex++].substring(1)
                    } else {
                        ""
                    }
                    // If there is another input line, then add back the '\n' that was lost from splitting.
                    if (lines.subListToEnd(lineIndex).any { it.startsWith('>') }) {
                        inputLine += "\n"
                    }
                    if (metadataLine.length > inputLine.length) {
                        val unused = metadataLine.substring(inputLine.length)
                        error("Unused metadata for input line $inputLineIndex: `$unused`")
                    }
                    for (column in inputLine.indices) {
                        val inputChar = inputLine[column]
                        val metadataChar = metadataLine.getOrNull(column) ?: ' '
                        sb.append(inputChar)
                        val tokenMetadata = when (metadataChar) {
                            ' ', '\t' -> continue // More token to find.
                            '.' -> null
                            else -> charToTokenMetadata[metadataChar]
                                ?: error("Bad metadata '$metadataChar' for line $inputLineIndex in `:$metadataLine`")
                        }
                        this.add("$sb" to tokenMetadata)
                        sb.clear()
                    }
                }
                if (sb.isNotEmpty()) {
                    this.add("$sb" to null)
                }
            }
        }

        val input = want
            .filter { it.second?.synthetic != true }
            .joinToString("") { it.first }

        /** Convert tokens and metadata back into an alternating input-line/metadata-line form. */
        fun condense(ls: List<Pair<String, TokenMetadata?>>) = buildString {
            val inputLine = StringBuilder()
            val metadataLine = StringBuilder()

            fun emitLines() {
                append('>').append(inputLine).append('\n')
                if (metadataLine.any { it > ' ' }) {
                    append(':').append(metadataLine).append('\n')
                }
                inputLine.clear()
                metadataLine.clear()
            }

            for (tokenIndex in ls.indices) {
                val (tokenText, metadata) = ls[tokenIndex]
                val isLastToken = tokenIndex + 1 == ls.size
                val lastChar = metadata?.let { tokenMetadataToChar.getValue(it) } ?: '.'
                for (charIndex in tokenText.indices) {
                    val isLastCharInToken = charIndex + 1 == tokenText.length
                    val c = tokenText[charIndex]
                    if (c == '\n') {
                        if (isLastCharInToken) {
                            metadataLine.append(lastChar)
                        }
                        emitLines()
                        if (isLastCharInToken && isLastToken) {
                            append(">")
                        }
                    } else {
                        inputLine.append(c)
                        metadataLine.append(
                            when {
                                isLastCharInToken -> lastChar
                                c == '\t' -> '\t' // So indentation lines up
                                else -> ' '
                            },
                        )
                    }
                }
            }
            if (inputLine.isNotEmpty()) {
                emitLines()
            }
            check(inputLine.isEmpty() && metadataLine.isEmpty())
        }

        val logSink = ListBackedLogSink()
        val gotWithMetadata = buildList {
            val lexer = Lexer(testCodeLocation, logSink, sourceText = input, lang = languageConfig)
            for (token in lexer) {
                if (unMassagedSemilitParagraphContent(token) != null) {
                    // Synthetic semilit comments are superfluous and tested in
                    // MarkdownLanguageConfigTest
                    continue
                }
                val (pos, tokenText, tokenType) = token
                val synthetic = token.synthetic
                val mayBracket = token.mayBracket
                val metadata = TokenMetadata(tokenType, mayBracket = mayBracket, synthetic = synthetic)
                if (synthetic) { // Other synthetic tokens should be zero-width
                    assertEquals(pos.left, pos.right)
                } else { // Non-synthetic tokens should match the source text
                    val range = lexer.sourceRangeOf(token)
                    assertStringsEqual(lexer.sourceText.substring(range), tokenText)
                }

                add(tokenText to metadata)
            }
        }

        val got =
            if (want.size == gotWithMetadata.size) {
                // Drop token types when we've nothing to compare them to.
                (want zip gotWithMetadata).map {
                    val (ws, wmd) = it.first
                    val (gs, gmd) = it.second
                    gs to
                        if (wmd == null && ws == gs) {
                            null
                        } else {
                            gmd
                        }
                }
            } else {
                gotWithMetadata
            }

        var pass = false
        try {
            assertStringsEqual(condense(want), condense(got))
            assertEquals(want, got)
            pass = true
        } finally {
            if (!pass) {
                printErr("Input: ${ temperEscaper.escape(input) }")
            }
        }

        val errorsGotten = logSink.allEntries.filter { it.level >= Log.Error }
        assertStringsEqual(
            wantedErrors.joinToString("\n"),
            errorsGotten.joinToString("\n") {
                val pos = it.pos
                buildString {
                    append(pos.left)
                    if (pos.left != pos.right) {
                        append('-').append(pos.right)
                    }
                    append(": ")
                    if (pos.left != pos.right) {
                        append("`")
                        append(input, pos.left, pos.right)
                        append("`: ")
                    }
                    append(it.messageText)
                }
            },
        )
    }
}
