package lang.temper.docbuild

import lang.temper.common.toStringViaBuilder
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class SnippetInsertionsTest {
    @Test
    fun insertionsFound() = assertTrue(SnippetInsertions.insertions.isNotEmpty())

    @Test
    fun allValid() {
        val invalidInsertions = SnippetInsertions.insertions.mapNotNull {
            it as? InvalidSnippetInsertion
        }
        if (invalidInsertions.isNotEmpty()) {
            fail(
                toStringViaBuilder { message ->
                    message.append("${invalidInsertions.size} invalid snippet insertions:")
                    invalidInsertions.forEach {
                        message.append(
                            "\n- `${it.text}`: ${it.range} in ${it.location}: ${it.problem}",
                        )
                    }
                },
            )
        }
    }

    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @Test
    fun insertionsDoNotOverlap() {
        val byLocation = SnippetInsertions.insertions.groupBy { it.location }
        for ((loc, insertionsUnordered) in byLocation) {
            val insertions = insertionsUnordered.sortedBy { it.range.first }
            for (i in 1 until insertions.size) {
                val before = insertions[i - 1] as? SnippetInsertion ?: continue
                val after = insertions[i] as? SnippetInsertion ?: continue
                if (before.range.last >= after.range.first) {
                    fail(
                        buildString {
                            append("For $loc out of order insertions:\n")
                            for ((index, insertion) in listOf(i - 1 to before, i to after)) {
                                append(
                                    "- #$index: Range ${insertion.range} by ${
                                        insertion.snippet.derivation.extractor
                                    }\n",
                                )
                                append("  ${"${insertion.replacedContent}".replace("\n", "\n  ")}\n")
                            }
                        },
                    )
                }
            }
        }
    }
}
