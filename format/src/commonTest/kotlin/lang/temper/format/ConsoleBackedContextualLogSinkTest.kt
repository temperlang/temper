package lang.temper.format

import lang.temper.common.AppendingTextOutput
import lang.temper.common.Console
import lang.temper.common.CustomValueFormatter
import lang.temper.common.Log
import lang.temper.common.assertStringsEqual
import lang.temper.log.LeveledMessageTemplate
import lang.temper.log.LogEntry
import lang.temper.log.unknownPos
import kotlin.test.Test

class ConsoleBackedContextualLogSinkTest {
    private enum class TestTemplate(
        override val suggestedLevel: Log.Level,
        override val formatString: String,
    ) : LeveledMessageTemplate {
        Ordinary(Log.Error, "ordinary %s"),
        Repeating(Log.Error, "repeating %s") {
            override val repeatsAtSamePosition: Boolean get() = true
        },
    }

    private fun logged(f: (ConsoleBackedContextualLogSink) -> Unit): String {
        val buffer = StringBuilder()
        val console = Console(AppendingTextOutput(buffer, isTtyLike = false))
        val logSink = ConsoleBackedContextualLogSink(console, null, null, CustomValueFormatter.Nope)
        f(logSink)
        console.textOutput.flush()
        return buffer.toString()
    }

    @Test
    fun repeatsAtUnknownPosDroppedByDefault() = assertStringsEqual(
        "ordinary 1\n",
        logged { logSink ->
            for (i in 1..3) {
                LogEntry(TestTemplate.Ordinary, unknownPos, listOf("$i")).logTo(logSink)
            }
        },
    )

    @Test
    fun templateThatRepeatsAtSamePositionIsNotDropped() = assertStringsEqual(
        """
            |repeating 1
            |repeating 2
            |ordinary 1
            |repeating 3
        """.trimMargin() + "\n",
        logged { logSink ->
            for (i in 1..3) {
                LogEntry(TestTemplate.Repeating, unknownPos, listOf("$i")).logTo(logSink)
                if (i >= 2) {
                    // Repeating messages do not use up the position for others,
                    // and do not let other repeats through.
                    LogEntry(TestTemplate.Ordinary, unknownPos, listOf("${i - 1}")).logTo(logSink)
                }
            }
        },
    )
}
