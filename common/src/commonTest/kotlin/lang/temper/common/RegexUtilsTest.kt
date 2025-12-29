package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals

class RegexUtilsTest {
    @Test
    fun splittingLines() {
        val input = "foo\n\nbar\r\nbaz"
        assertEquals(
            listOf("foo\n", "\n", "bar\r\n", "baz"),
            input.splitAfter(Regex("\r?\n")),
        )
    }
}
