package lang.temper.lexer

import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageConfigTest {
    @Test
    fun temperNamesSplit() {
        // Bundle some variety into one test, as it's still fairly short and simple.
        val cases = listOf(
            // Temper names.
            "a.temper" to ("a" to ".temper"),
            "a.temper.md" to ("a" to ".temper.md"),
            // Other names.
            ".temper" to (".temper" to ""),
            "a.b" to ("a" to ".b"),
            "a.b.c" to ("a.b" to ".c"),
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, temperSensitiveBaseNameAndExtension(input))
        }
    }
}
