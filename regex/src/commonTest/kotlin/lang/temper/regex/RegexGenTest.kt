package lang.temper.regex

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class RegexGenTest {
    /** Mostly just a chance to see what generated things look like. Changes easily, though. */
    @Test
    fun gen() {
        // See also `withRandomForTest` such as in `GenerativeGrammarFuzzTest`.
        val random = Random(4337)
        val actual = (1..10).joinToString("\n") {
            random.nextRegex().formatToString()
        }
        val expected =
            """
            \w  \t\t\t${'$'}
            \n.棩Ä¼\u{87}g¦
            \nêø\n\r
            ^(?:(?<n1>𸟬)|.|.)ùôÇ\u{88}\u{C}\r^${'$'}=@\${'$'}_
            ^\r\n\r\n\r${'$'}
            ^\ndetꕥ
            ^  \töù
            ^ \t ${""}
            ^\d${'$'}
            ^(?:[1-7]){1,3}?\(%\r\n
            """.trimIndent()
        assertEquals(expected, actual)
    }
}
