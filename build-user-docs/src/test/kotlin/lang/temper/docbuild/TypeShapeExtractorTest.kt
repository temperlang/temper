package lang.temper.docbuild

import lang.temper.common.RSuccess
import lang.temper.common.assertStructure
import lang.temper.common.newBufferingConsole
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.log.filePath
import lang.temper.type.ANY_VALUE_TYPE_NAME_TEXT
import lang.temper.value.consoleParsedName
import kotlin.test.Test
import kotlin.test.assertTrue

class TypeShapeExtractorTest {
    @Test
    fun someApiExtracted() {
        assertTrue(
            Snippets.resolveShortId(
                slashString = "type/$ANY_VALUE_TYPE_NAME_TEXT",
                from = filePath("test"),
                referenceText = "just running a test",
            ) is RSuccess,
        )
    }

    @Test
    fun backPortTypeComment() {
        val name = ANY_VALUE_TYPE_NAME_TEXT
        doBackPortComment(name = name, nameKind = "interface", slashString = "type/$name/commentary")
    }

    @Test
    fun backPortValueComment() {
        val name = consoleParsedName.nameText
        doBackPortComment(name = name, nameKind = "let", slashString = "builtin/$name")
    }

    private fun doBackPortComment(name: String, nameKind: String, slashString: String) {
        val snippet = Snippets.resolveShortId(
            slashString = slashString,
            from = filePath("test"),
            referenceText = "just running a test",
        ).result!!

        val contentBuffer = StringBuilder(ImplicitsModule.code)
        val consoleBuffer = StringBuilder()
        val problemTracker = ProblemTracker(newBufferingConsole(consoleBuffer))

        val backPortedSuccessfully = TypeShapeExtractor.backPortSnippetChange(
            snippet = snippet,
            newContent = MarkdownContent("Changed content for declaration $name\nto something different"),
            into = contentBuffer,
            problemTracker = problemTracker,
        )

        val commentBeforeAnyValue = run {
            val modifiedContent = TemperContent(
                filePath("modified", "Implicits.temper"),
                "$contentBuffer",
                StandaloneLanguageConfig,
            )

            // Find the comment before `interface AnyValue`.
            var lastComment: TemperToken? = null
            var lastWasKind = false
            for (token in modifiedContent.lexer()) {
                if (isDocumentableCommentToken(token)) {
                    lastComment = token
                }
                if (!token.tokenType.ignorable) {
                    val isWord = token.tokenType == TokenType.Word
                    if (isWord && lastWasKind && token.tokenText == name) {
                        return@run lastComment?.tokenText
                    }
                    lastWasKind = isWord && token.tokenText == nameKind
                }
            }
            null
        }

        val want = ResultBundle(
            successful = true,
            consoleOutput = "",
            problemCount = 0,
            commentBeforeAnyValue = """
                |/**
                | * Changed content for declaration $name
                | * to something different
                | */
            """.trimMargin(),
        )
        val got = ResultBundle(
            successful = backPortedSuccessfully,
            consoleOutput = "$consoleBuffer",
            problemCount = problemTracker.problemCount,
            commentBeforeAnyValue = commentBeforeAnyValue,
        )
        assertStructure(want, got)
    }

    private data class ResultBundle(
        val successful: Boolean,
        val consoleOutput: String,
        val problemCount: Int,
        val commentBeforeAnyValue: String?,
    ) : Structured {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("successful") { value(successful) }
            key("consoleOutput") { value(consoleOutput) }
            key("problemCount") { value(problemCount) }
            key("commentBeforeAnyValue") { valueOrNull(commentBeforeAnyValue) }
        }
    }
}
