package lang.temper.tooling

import lang.temper.frontend.ModuleSource
import lang.temper.lexer.MarkdownLanguageConfig
import kotlin.test.Test

class SemilitToolingTest {
    internal companion object {
        val context = DirModuleDataTestContext(
            sources = listOf(
                ModuleSource(
                    fetchedContent = """
                        |# Hi there
                        |```
                        |let /*1@+0*/message = "hi";
                        |```
                    """.trimMargin(),
                    languageConfig = MarkdownLanguageConfig(),
                ),
                ModuleSource(
                    fetchedContent = """
                        |More things to say.
                        |```
                        |console.log(/*2@+1*/message);
                        |```
                        |We don't need /* comment format */ in the md sections here, but our test support expects it.
                        |/*3@+0*/
                    """.trimMargin(),
                    languageConfig = MarkdownLanguageConfig(),
                ),
            ),
        )
    }

    @Test
    fun findDef() {
        context.assertFound(refDef = "/*2@+1*/" to "/*1@+0*/")
    }

    @Test
    fun findNoCompletionsAtEnd() {
        context.assertCompletions(spot = "/*3@+0*/", expected = emptyList())
    }
}
