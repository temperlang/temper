package lang.temper.compile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import lang.temper.common.Console

/**
 * Starts a job on the log sink that drains log messages received from multiple places to the given
 * console,
 */
@DelicateCoroutinesApi
class TopLevelConsoleLogSink(
    coroutineScope: CoroutineScope,
    private val console: Console,
) : AbstractThreadSafeLogSink(
    launch = { body -> coroutineScope.launch { body() } },
    mayWrite = { true },
) {

    override suspend fun write(entries: List<LogMessage>) {
        entries.forEach { logMessage ->
            if (!logMessage.fyi) {
                console.log(logMessage.template.format(logMessage.values), level = logMessage.level)
            }
            // TODO: Also dump position and context snippet.
        }
    }
}
