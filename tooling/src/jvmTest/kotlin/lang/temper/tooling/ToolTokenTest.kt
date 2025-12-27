package lang.temper.tooling

import lang.temper.frontend.ModuleSource
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.FilePath
import lang.temper.log.UnknownCodeLocation
import kotlin.test.Test
import kotlin.test.assertEquals

/** Much of this is tracking current behavior, not necessarily ideal. */
class ToolTokenTest {
    internal companion object {
        val classesContext = FileModuleDataTestContext(
            """
            export class A {}
            class B extends A {
                public let a() {}
            }
            """.trimIndent(),
        )

        val ifContext = FileModuleDataTestContext(
            """
            do {
              if (5 > 4) { print("hi!"); }
              let if = 5;
              if (if > 4) { print("hi!"); }
            }
            """.trimIndent(),
        )

        val classesIfDirContext = DirModuleDataTestContext(
            sources = listOf(
                ModuleSource(fetchedContent = classesContext.moduleSource),
                ModuleSource(fetchedContent = ifContext.moduleSource),
            ),
        )

        val joinerContext = FileModuleDataTestContext(
            """
            let a🐻‍❄️ = 4; // 🐻‍❄️
            """.trimIndent(),
        )

        val prefaceContext = FileModuleDataTestContext(
            """
            let ab = 1;
            ;;;
            let c = 2;
            """.trimIndent(),
        )

        val unfinishedCommentContext = FileModuleDataTestContext(
            """
            if (true) {
                /* let a = 1;
            }
            let b = 2;
            """.trimIndent(),
        )

        val unfinishedMultiQuotedStringContext = FileModuleDataTestContext(
            // The string implicitly ends at the second `let` but there's no semicolon.
            """
                |let a = ${"\"\"\""}
                |  "1
                |let b = 2;
            """.trimMargin(),
        )

        val unfinishedDqStringContext = FileModuleDataTestContext(
            """
            let a = "1
            let b = 2;
            """.trimIndent(),
        )
    }

    @Test
    fun badCombo() {
        val otherContent = "let a = 5;"
        // Use content from one source and module data from another.
        // This shouldn't be done, but it's nice to know what happens.
        val tokens = classesContext.moduleData.sequenceComboToolTokens(
            filePath = classesContext.path,
            content = otherContent,
            lang = StandaloneLanguageConfig,
        ).toTokenLines()
        val expected =
            """
            kind=Variable, mods=[], range=0..2
            kind=Variable, mods=[], range=4..4
            kind=Operator, mods=[], range=6..6
            kind=Keyword, mods=[], range=7..11
            kind=Variable, mods=[Definition], range=13..13
            kind=Keyword, mods=[], range=18..22
            kind=Variable, mods=[Definition], range=24..24
            kind=Keyword, mods=[], range=26..32
            kind=Variable, mods=[DefaultLibrary], range=34..34
            kind=Keyword, mods=[], range=42..47
            kind=Keyword, mods=[], range=49..51
            kind=Variable, mods=[Definition], range=53..53
            """.trimIndent()
        assertEquals(expected, tokens)
    }

    @Test
    fun classesCombo() {
        // TODO(tjp, tooling): Remove combo tokens once the client grammar is in place?
        val tokens = classesContext.tokenLinesCombo()
        assertEquals(classesComboExpected, tokens)
    }

    private val classesComboExpected =
        """
            |kind=Keyword, mods=[], export
            |kind=Keyword, mods=[], class
            |kind=Variable, mods=[Definition], A
            |kind=Operator, mods=[], {
            |kind=Operator, mods=[], }
            |kind=Keyword, mods=[], class
            |kind=Variable, mods=[Definition], B
            |kind=Keyword, mods=[], extends
            |kind=Variable, mods=[DefaultLibrary], A
            |kind=Operator, mods=[], {
            |kind=Keyword, mods=[], public
            |kind=Keyword, mods=[], let
            |kind=Variable, mods=[Definition], a
            |kind=Operator, mods=[], (
            |kind=Operator, mods=[], )
            |kind=Operator, mods=[], {
            |kind=Operator, mods=[], }
            |kind=Operator, mods=[], }
        """.trimMargin()

    @Test
    fun classesIfCombo() {
        val classesTokens = classesIfDirContext.tokenLinesCombo(classesIfDirContext.sources[0].filePath!!)
        val ifTokens = classesIfDirContext.tokenLinesCombo(classesIfDirContext.sources[1].filePath!!)
        assertEquals(classesComboExpected, classesTokens)
        assertEquals(ifComboExpected, ifTokens)
    }

    @Test
    fun classesLexer() {
        val tokens = classesContext.tokenLinesLexer()
        val expected =
            """
            kind=Variable, mods=[], export
            kind=Variable, mods=[], class
            kind=Variable, mods=[], A
            kind=Operator, mods=[], {
            kind=Operator, mods=[], }
            kind=Variable, mods=[], class
            kind=Variable, mods=[], B
            kind=Variable, mods=[], extends
            kind=Variable, mods=[], A
            kind=Operator, mods=[], {
            kind=Variable, mods=[], public
            kind=Variable, mods=[], let
            kind=Variable, mods=[], a
            kind=Operator, mods=[], (
            kind=Operator, mods=[], )
            kind=Operator, mods=[], {
            kind=Operator, mods=[], }
            kind=Operator, mods=[], }
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun classesTree() {
        val tokens = classesContext.tokenLinesTree()
        // TODO(tjp, tooling): The `A` below shows as `DefaultLibrary` because of wrong heuristics. It shouldn't be.
        val expected =
            """
            kind=Keyword, mods=[], export
            kind=Keyword, mods=[], class
            kind=Variable, mods=[Definition], A
            kind=Keyword, mods=[], class
            kind=Variable, mods=[Definition], B
            kind=Keyword, mods=[], extends
            kind=Variable, mods=[DefaultLibrary], A
            kind=Keyword, mods=[], public
            kind=Keyword, mods=[], let
            kind=Variable, mods=[Definition], a
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun classesTreeAdd() {
        val tokens = classesContext.tokenLinesCombo(additiveOnly = true)
        // For now, additive tokens only turn presumed keywords into variables when applicable.
        val expected = ""
        assertEquals(expected, tokens)
    }

    @Test
    fun ifAdditiveUnderstandsSemantics() {
        val tokens = ifContext.tokenLinesCombo(additiveOnly = true)
        // We don't check locations here, but this still provides enough context for the gist.
        val expected =
            """
            kind=Variable, mods=[Definition], if
            kind=Variable, mods=[], if
            kind=Variable, mods=[], if
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun ifComboUnderstandsSemantics() {
        val tokens = ifContext.tokenLinesCombo()
        assertEquals(ifComboExpected, tokens)
    }

    /** Test that the meaning of `if` changes here. Also happens to include things like `print` as stdlib. */
    private val ifComboExpected =
        """
            |kind=Keyword, mods=[], do
            |kind=Operator, mods=[], {
            |kind=Keyword, mods=[], if
            |kind=Operator, mods=[], (
            |kind=Number, mods=[], 5
            |kind=Operator, mods=[], >
            |kind=Number, mods=[], 4
            |kind=Operator, mods=[], )
            |kind=Operator, mods=[], {
            |kind=Variable, mods=[DefaultLibrary], print
            |kind=Operator, mods=[], (
            |kind=String, mods=[], "
            |kind=String, mods=[], hi!
            |kind=String, mods=[], "
            |kind=Operator, mods=[], )
            |kind=Operator, mods=[], ;
            |kind=Operator, mods=[], }
            |kind=Keyword, mods=[], let
            |kind=Variable, mods=[Definition], if
            |kind=Operator, mods=[], =
            |kind=Number, mods=[], 5
            |kind=Operator, mods=[], ;
            |kind=Variable, mods=[], if
            |kind=Operator, mods=[], (
            |kind=Variable, mods=[], if
            |kind=Operator, mods=[], >
            |kind=Number, mods=[], 4
            |kind=Operator, mods=[], )
            |kind=Operator, mods=[], {
            |kind=Variable, mods=[DefaultLibrary], print
            |kind=Operator, mods=[], (
            |kind=String, mods=[], "
            |kind=String, mods=[], hi!
            |kind=String, mods=[], "
            |kind=Operator, mods=[], )
            |kind=Operator, mods=[], ;
            |kind=Operator, mods=[], }
            |kind=Operator, mods=[], }
        """.trimMargin()

    @Test
    fun joinerCombo() {
        val tokens = joinerContext.tokenLinesCombo()
        val expected =
            """
            kind=Variable, mods=[], let
            kind=Variable, mods=[], a🐻‍
            kind=Error, mods=[], ❄
            kind=Variable, mods=[], ️
            kind=Operator, mods=[], =
            kind=Number, mods=[], 4
            kind=Operator, mods=[], ;
            kind=Comment, mods=[], // 🐻‍❄️
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun joinerLexer() {
        val tokens = joinerContext.tokenLinesLexer()
        val expected =
            """
            kind=Variable, mods=[], let
            kind=Variable, mods=[], a🐻‍
            kind=Error, mods=[], ❄
            kind=Variable, mods=[], ️
            kind=Operator, mods=[], =
            kind=Number, mods=[], 4
            kind=Operator, mods=[], ;
            kind=Comment, mods=[], // 🐻‍❄️
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun joinerTree() {
        val tokens = joinerContext.tokenLinesTree()
        val expected =
            """
            kind=String, mods=[], let a🐻‍❄️ = 4
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun prefaceTree() {
        val tokens = prefaceContext.tokenLinesTree()
        // TODO(tjp, tooling): Why is only the preface coming through in the snapshots?
        val expected =
            """
            kind=Variable, mods=[Definition], ab
            kind=Operator, mods=[], =
            kind=Number, mods=[], 1
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun unfinishedCommentCombo() {
        val tokens = unfinishedCommentContext.tokenLinesCombo()
        val expected =
            """
            kind=Keyword, mods=[], if
            kind=Operator, mods=[], (
            kind=Variable, mods=[DefaultLibrary], true
            kind=Operator, mods=[], )
            kind=Operator, mods=[], {
            kind=Comment, mods=[], /* let a = 1;
            }
            let b = 2;
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun unfinishedCommentLexer() {
        val tokens = unfinishedCommentContext.tokenLinesLexer()
        val expected =
            """
            kind=Variable, mods=[], if
            kind=Operator, mods=[], (
            kind=Variable, mods=[], true
            kind=Operator, mods=[], )
            kind=Operator, mods=[], {
            kind=Comment, mods=[], /* let a = 1;
            }
            let b = 2;
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun unfinishedCommentTree() {
        val tokens = unfinishedCommentContext.tokenLinesTree()
        val expected =
            """
            kind=Keyword, mods=[], if
            kind=Variable, mods=[DefaultLibrary], true
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun unfinishedMultiQuotedStringCombo() {
        val tokens = unfinishedMultiQuotedStringContext.tokenLinesCombo()
        val expected =
            """
            kind=Variable, mods=[], let
            kind=Variable, mods=[], a
            kind=Operator, mods=[], =
            kind=String, mods=[], ${"\"\"\""}
            kind=String, mods=[], ${"\n"}
            kind=String, mods=[], 1${"\n"}
            kind=Variable, mods=[], let
            kind=Variable, mods=[], b
            kind=Operator, mods=[], =
            kind=Number, mods=[], 2
            kind=Operator, mods=[], ;
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun unfinishedDqStringCombo() {
        val tokens = unfinishedDqStringContext.tokenLinesCombo()
        val expected =
            """
            kind=Variable, mods=[], let
            kind=Variable, mods=[], a
            kind=Operator, mods=[], =
            kind=String, mods=[], "
            kind=String, mods=[], 1
            kind=Variable, mods=[], let
            kind=Variable, mods=[], b
            kind=Operator, mods=[], =
            kind=Number, mods=[], 2
            kind=Operator, mods=[], ;
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun unfinishedStringLexer() {
        val tokens = unfinishedMultiQuotedStringContext.tokenLinesLexer()
        val expected =
            """
            kind=Variable, mods=[], let
            kind=Variable, mods=[], a
            kind=Operator, mods=[], =
            kind=String, mods=[], ${"\"\"\""}
            kind=String, mods=[], ${"\n"}
            kind=String, mods=[], 1${"\n"}
            kind=Variable, mods=[], let
            kind=Variable, mods=[], b
            kind=Operator, mods=[], =
            kind=Number, mods=[], 2
            kind=Operator, mods=[], ;
            """.trimIndent().trim()
        assertEquals(expected, tokens)
    }

    @Test
    fun unfinishedStringTree() {
        val tokens = unfinishedMultiQuotedStringContext.tokenLinesTree()
        // TODO: why is the """ not included in the string part?
        // There is no `;` before let b = 2 so it gloms on.
        val expected =
            """
                |kind=String, mods=[], let a = ${"\"\"\""}
                |  "1
                |let b = 2
            """.trimMargin().trim()
        assertEquals(expected, tokens)
    }
}

private fun FileModuleDataTestContext.comboTokens(additiveOnly: Boolean = false) = moduleData.sequenceComboToolTokens(
    additiveOnly = additiveOnly,
    filePath = path,
    content = moduleSource,
    lang = StandaloneLanguageConfig,
)

private fun DirModuleDataTestContext.tokenLinesCombo(filePath: FilePath, additiveOnly: Boolean = false): String {
    val moduleSource = sources.find { it.filePath == filePath }!!
    val tokens = moduleData.sequenceComboToolTokens(
        additiveOnly = additiveOnly,
        filePath = filePath,
        content = moduleSource.fetchedContent!!,
        lang = moduleSource.languageConfig!!,
    )
    return tokens.toTokenLines(moduleSource.fetchedContent as String)
}

private fun FileModuleDataTestContext.tokenLinesCombo(additiveOnly: Boolean = false) =
    comboTokens(additiveOnly = additiveOnly).toTokenLines(moduleSource)

private fun FileModuleDataTestContext.tokenLinesLexer() = moduleSource.lexerTokens().toTokenLines(moduleSource)
private fun FileModuleDataTestContext.tokenLinesTree() = moduleData.sequenceToolTokens(path).toTokenLines(moduleSource)

private fun String.lexerTokens() = sequenceToolTokens(
    codeLocation = UnknownCodeLocation,
    content = this,
    lang = StandaloneLanguageConfig,
)

private fun <T> List<T>.toMultiline() =
    toString().replace(Regex("""\), """), ")\n").trimStart('[').trimEnd(']')

private fun <T> Sequence<T>.toTokenLines(): String {
    val lines = toList().toMultiline().replace("ToolToken", "")
    return lines.replace(Regex("""^\(|\)$""", RegexOption.MULTILINE), "")
}

private fun <T> Sequence<T>.toTokenLines(content: String): String {
    val withRanges = toTokenLines()
    fun MatchResult.int(index: Int) = groupValues[index].toInt()
    return withRanges.replace(Regex("""range=(\d+)\.\.(\d+)""")) { content.slice(it.int(1)..it.int(2)) }
}
