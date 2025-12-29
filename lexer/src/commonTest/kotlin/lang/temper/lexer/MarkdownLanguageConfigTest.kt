package lang.temper.lexer

import lang.temper.common.ListBackedLogSink
import lang.temper.common.testCodeLocation
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownLanguageConfigTest {
    fun withTokensFromMarkdowns(
        markdown: String,
        testBody: (tokens: List<TemperToken>) -> Unit,
    ) {
        val lang = MarkdownLanguageConfig()
        val logSink = ListBackedLogSink()
        val lexer = Lexer(testCodeLocation, logSink, markdown, lang = lang)
        val tokens = buildList {
            while (lexer.hasNext()) {
                add(lexer.next())
            }
        }
        testBody(tokens)
    }

    @Test
    fun codeSections() = withTokensFromMarkdowns(
        """
            |One
            |
            |```temper
            |1
            |```
            |
            |Two
            |
            |```temper inert
            |2
            |```
            |
            |Three
            |
            |    3
        """.trimMargin(),
    ) { tokens ->
        assertEquals(
            listOf("1", "3"),
            tokens.mapNotNull {
                if (it.tokenType.ignorable) { return@mapNotNull null }
                it.tokenText
            },
        )
    }

    @Test
    fun commentsExtracted() = withTokensFromMarkdowns(
        """
            |Before
            |
            |Before 2.
            |
            |```temper
            |lotsOf(tokens); // Inline comment
            |```
            |
            |After
            |
        """.trimMargin(),
    ) { tokens ->
        val comments = tokens.filter {
            it.tokenType == TokenType.Comment
        }
        assertEquals(
            listOf(
                // First we have a lexical comment
                """
                    |L:
                    |Before
                    |
                    |Before 2.
                    |
                    |```temper
                """.trimMargin(),
                // We extract two paragraphs from this
                """
                    |S:
                    |/**?
                    | * Before
                    |?*/
                """.trimMargin(),
                """
                    |S:
                    |/**?
                    | * Before 2.
                    |?*/
                """.trimMargin(),
                // Nothing broken out of this one which is not semilit
                "L:\n// Inline comment",
                """
                    |L:
                    |```
                    |
                    |After
                    |
                """.trimMargin(),
                """
                    |S:
                    |/**?
                    | * After
                    |?*/
                """.trimMargin(),
            ),
            comments.map {
                "${ // Synthetic or Lexical
                    if (it.synthetic) "S" else "L"
                }:\n${it.tokenText}"
            },
        )
    }

    @Test
    fun commentsNestedFourDeepBecauseLists() = withTokensFromMarkdowns(
        """
            |Here's a list.
            |
            |- One
            |- Two
            |  - Two and a half
            |    Now we're indented 4 spaces but not in an unfenced block according to Markdown rules.
            |
            |    ```temper
            |    hello();
            |    ```
            |
            |    pi is a number
            |
            |    It's well rounded.
            |
            |        let pi = 3.14;
        """.trimMargin(),
    ) { tokens ->
        assertEquals(
            listOf(
                "Here's a list.",
                "One",
                "Two",
                """
                    |Two and a half
                    |    Now we're indented 4 spaces but not in an unfenced block according to Markdown rules.
                """.trimMargin(),
                "pi is a number",
                "It's well rounded.",
            ),
            tokens.mapNotNull {
                unMassagedSemilitParagraphContent(it)
            },
        )
    }
}
