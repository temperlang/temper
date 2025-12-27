package lang.temper.tooling

import lang.temper.common.assertStringsEqual
import kotlin.test.Test

class ListLogEntriesTest {
    @Test
    fun listLogEntriesDir() {
        checkLogEntries(FindCompletionsTest.dirContext)
    }

    @Test
    fun listLogEntriesFile() {
        checkLogEntries(FindCompletionsTest.fileContext)
    }

    @Suppress("MaxLineLength")
    private fun checkLogEntries(context: ModuleDataTestContext) {
        // Don't sweat positions here. More checking is done elsewhere on messages.
        // Here, we just want to see that things approximately get through to the gathered module data.
        val found = context.moduleData.logEntries.map { it.messageText }
        val expected = listOf(
            "Cannot assign to String from Fish!",
            "No callee matches inputs [Invalid, Invalid] among [(Int32, Int32) -> Int32, (Int32) -> Int32, (Int64, Int64) -> Int64, (Int64) -> Int64, (Float64, Float64) -> Float64, (Float64) -> Float64]!",
            "No declaration for a!",
            "No declaration for card!",
            "No declaration for b!",
            "No declaration for bam!",
            "No declaration for d!",
            "No declaration for g!",
        )
        assertStringsEqual(
            expected.sorted().toSet().joinToString("\n"),
            found.sorted().toSet().joinToString("\n"),
        )
    }
}
