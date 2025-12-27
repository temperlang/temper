package lang.temper.langserver

import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.UnknownCodeLocation
import lang.temper.tooling.sequenceToolTokens
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticTokensTest {
    companion object {
        val content =
            """
            let apple = 1;
            var banana = 2;
            if (apple > 1) {
                banana = 3; /*
                    Multiline comment here.
                */
            }
            """.trimIndent()

        private val commonExpected =
            """
            0 0 3 8 0
            0 4 5 8 0
            0 6 1 21 0
            0 2 1 19 0
            0 1 1 21 0
            1 0 3 8 0
            0 4 6 8 0
            0 7 1 21 0
            0 2 1 19 0
            0 1 1 21 0
            1 0 2 8 0
            0 3 1 21 0
            0 1 5 8 0
            0 6 1 21 0
            0 2 1 19 0
            0 1 1 21 0
            0 2 1 21 0
            1 4 6 8 0
            0 7 1 21 0
            0 2 1 19 0
            0 1 1 21 0
            """.trimIndent()
    }

    @Test
    fun multilineTokensFalse() {
        val suffix =
            """
            0 2 3 16 0
            1 0 32 16 0
            1 0 6 16 0
            1 0 1 21 0
            """.trimIndent()
        checkTokens(mayUseMultilineTokens = false, expected = "$commonExpected\n$suffix")
    }

    @Test
    fun multilineTokensTrue() {
        val suffix =
            """
            0 2 41 16 0
            3 0 1 21 0
            """.trimIndent()
        checkTokens(mayUseMultilineTokens = true, expected = "$commonExpected\n$suffix")
    }

    private fun checkTokens(mayUseMultilineTokens: Boolean, expected: String) {
        val tokens = sequenceToolTokens(
            codeLocation = UnknownCodeLocation,
            content = content,
            lang = StandaloneLanguageConfig,
        )
        val actual = computeSemanticTokens(
            tokens = tokens,
            content = content,
            mayUseMultilineTokens = mayUseMultilineTokens,
            cancelled = AtomicBoolean(),
        ).toList().split(PACKED_INT_FIELD_COUNT).joinToString("\n") { it.joinToString(" ") }
        assertEquals(expected, actual)
    }
}

fun <T> List<T>.split(n: Int): List<List<T>> = (indices step n).map { slice(it until it + n) }
