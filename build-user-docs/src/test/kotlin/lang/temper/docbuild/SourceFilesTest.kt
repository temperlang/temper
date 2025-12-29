package lang.temper.docbuild

import lang.temper.log.FilePath.Companion.join
import lang.temper.log.filePath
import kotlin.streams.toList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceFilesTest {
    @Test
    fun expectedFileIsPresent() {
        assertTrue(
            filePath(
                "lexer",
                "src",
                "commonMain",
                "kotlin",
                "lang",
                "temper",
                "lexer",
                "Lexer.kt",
            ) in SourceFiles.files,
        )
    }

    @Test
    fun ignoredFilesNotPresent() {
        assertEquals(
            emptyList(),
            SourceFiles.files.filter {
                "fundamentals/build/" in it.join()
            },
        )
    }

    @Test
    fun extensionMatching() {
        val byGlob = SourceFiles.matching("**/*.kt").toList().toSet()
        val byExt = SourceFiles.withExtension(".kt").toList().toSet()
        assertEquals(byGlob, byExt)
        assertTrue(byGlob.isNotEmpty())
    }

    @Test
    fun globbing() {
        val matching = SourceFiles.matching("build-user-docs/**/SourceFilesTest.*")
        assertEquals(
            listOf(
                filePath(
                    // Unit tests are allowed to do vanity searches too.
                    "build-user-docs",
                    "src",
                    "test",
                    "kotlin",
                    "lang",
                    "temper",
                    "docbuild",
                    "SourceFilesTest.kt",
                ),
            ),
            matching.toList(),
        )
    }
}
