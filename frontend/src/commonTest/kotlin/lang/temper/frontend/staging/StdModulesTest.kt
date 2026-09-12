package lang.temper.frontend.staging

import lang.temper.common.CustomValueFormatter
import lang.temper.common.Log
import lang.temper.common.console
import lang.temper.format.ConsoleBackedContextualLogSink
import kotlin.test.Test
import kotlin.test.fail

class StdModulesTest {
    @Test
    fun stdCompilesClean() {
        val logEntries = getSharedStdModulesLogEntries()
        val errorsAndWarnings = logEntries.filter { it.level >= Log.Warn }
        if (errorsAndWarnings.isNotEmpty()) {
            val msg = "${errorsAndWarnings.size} problems in std"
            console.group(msg) {
                val module = getSharedStdModules().first()
                val logSink = ConsoleBackedContextualLogSink(
                    console,
                    module.sharedLocationContext,
                    null,
                    CustomValueFormatter.Nope,
                )
                for (e in errorsAndWarnings) {
                    e.logTo(logSink)
                }
            }
            fail(msg)
        }
    }
}
