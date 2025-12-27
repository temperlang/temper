package lang.temper.docbuild

import lang.temper.common.RSuccess
import lang.temper.common.assertStructure
import lang.temper.common.json.JsonValue
import lang.temper.common.structure.toMinimalJson
import lang.temper.common.toStringViaBuilder
import lang.temper.log.filePath
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class SnippetsTest {
    @Test
    fun extractedFromKotlin() {
        assertTrue(Snippets.snippetList.any { it.source.segments.last().extension == ".kt" })
    }

    @Test
    fun noUnreadable() {
        if (Snippets.unreadable.isNotEmpty()) {
            fail(
                buildString {
                    append("The snippet extractor could not extract from the below. ")
                    append("This might happen if your `git status` shows directories that have not been ")
                    append("added or which are not mentioned in `.gitignore` as not meant for addition.\n")
                    append(Snippets.unreadable)
                },
            )
        }
    }

    @Test
    fun idsAreUnique() {
        val duplicates = Snippets.snippetList
            .groupBy { it.id }
            .filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            fail(
                toStringViaBuilder { msg ->
                    msg.append("Snippets are not unique:")
                    duplicates.entries.forEach { (id, snippets) ->
                        msg.append("\n- ")
                        msg.append(id)
                        msg.append(" is defined at:")
                        snippets.forEach { snippet ->
                            msg.append("\n  - ")
                            msg.append(snippet.source)
                            msg.append(" at offset ")
                            msg.append(snippet.sourceStartOffset)
                        }
                    }
                },
            )
        }
    }

    @Test
    fun noProblemSnippets() {
        val problemSnippets = Snippets.snippetList.filter { it.problems.isNotEmpty() }
        if (problemSnippets.isNotEmpty()) {
            fail(
                toStringViaBuilder { sb ->
                    sb.append("${problemSnippets.size} snippets have problems")
                    problemSnippets.forEach { snippet ->
                        sb.append(
                            "\n- ${
                                snippet.id.shortCanonString(withExtension = true)
                            } from ${snippet.source}+${snippet.sourceStartOffset}",
                        )
                        snippet.problems.forEach { problem ->
                            sb.append("\n  - $problem")
                        }
                    }
                },
            )
        }
    }

    @Test
    fun resolvePercentCoding() {
        // '+' maps to '%2B` when percent-encoded.
        val testFilePath = filePath("my/test/file")
        val resultUnencoded =
            Snippets.resolveShortId("builtin/+", testFilePath, "[snippet/builtin/+]")
        assertIs<RSuccess<Snippet, IllegalArgumentException>>(resultUnencoded, "$resultUnencoded")
        val resultEncoded =
            Snippets.resolveShortId("builtin/%2B", testFilePath, "[snippet/builtin/%2B]")
        assertIs<RSuccess<Snippet, IllegalArgumentException>>(resultEncoded, "$resultEncoded")
        assertEquals(resultUnencoded, resultEncoded)
        val snippet = resultUnencoded.result
        assertEquals(
            "build-user-docs/build/snippet/builtin/%2B/snippet.md",
            snippet.id.filePath.toString(),
        )
    }

    @Test
    fun snippetsUpToDate() {
        val snippetContent = JsonValue.parse(
            UserDocFilesAndDirectories.snippetsJsonFile.readText(),
        )
        assertIs<RSuccess<JsonValue, IllegalArgumentException>>(snippetContent)
        val snippetJson = snippetContent.result
        assertStructure(
            Snippets.toMinimalJson(),
            snippetJson,
            inputContext = emptyMap(),
            message = """
                |Snippets should be up-to-date.
                |Maybe run `gradle build-user-docs:updateGeneratedDocs`
            """.trimMargin(),
        )
    }
}
