package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals

class ConsoleTest {
    @Test
    fun formatError() {
        check(
            full = console.formatMessageError(null, Throwable("there")),
            expected = """
                java.lang.Throwable: there
                    at lang.temper.common.ConsoleTest.formatError
            """.trimIndent(),
        )
    }

    @Test
    fun formatMessageError() {
        check(
            full = console.formatMessageError("Hi", Throwable("there")),
            expected = """
                Hi
                java.lang.Throwable: there
                    at lang.temper.common.ConsoleTest.formatMessageError
            """.trimIndent(),
        )
    }

    @Test
    fun linesTrimmed() {
        val line = "That's what I was thinking"
        assertEquals(line, trimLine(line, line.length))
        assertEquals("That's what I was thi...", trimLine(line, line.length - 1))
        assertEquals("...", trimLine(line, 3))
        assertEquals("", trimLine(line, -1))
    }
}

private fun String.normalize() = lines().joinToString("\n").replace("\t", "    ")

private fun check(expected: String, full: String) {
    // Check only part of the stack trace in case the full thing is inconsistent. Also, this format is specific to jvm.
    val part = full.slice(0 until full.indexOf('(')).normalize()
    assertEquals(expected.trim().normalize(), part)
}
