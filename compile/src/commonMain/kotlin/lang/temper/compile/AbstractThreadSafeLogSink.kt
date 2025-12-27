package lang.temper.compile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import lang.temper.common.Log
import lang.temper.fs.AutoCloseable
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position

/**
 * Starts a job on the log sink that drains log messages received from multiple places to the given
 * console,
 */
@DelicateCoroutinesApi
abstract class AbstractThreadSafeLogSink(
    launch: (suspend CoroutineScope.() -> Unit) -> Unit,
    mayWrite: suspend () -> Boolean,
) : LogSink, AutoCloseable {
    abstract suspend fun write(entries: List<LogMessage>)

    private val channel = Channel<LogMessage>(Channel.UNLIMITED)

    /**
     * A join point that completes when the channel is closed for receive because it's been closed
     * for send and all pending writes have been processed.
     */
    private val finalFlushJoinPoint = CompletableDeferred<Unit>()

    override fun close() {
        channel.close()
    }

    init {
        launch {
            val toWrite = mutableListOf<LogMessage>()
            channelOpenLoop@
            while (!channel.isClosedForReceive) {
                toWrite.add(channel.receiveCatching().getOrNull() ?: break)
                if (!mayWrite()) {
                    channel.close()
                    break
                }
                while (true) {
                    toWrite.add(channel.tryReceive().getOrNull() ?: break)
                }
                if (toWrite.isNotEmpty()) {
                    val toWriteNow = toWrite.toList()
                    toWrite.clear()
                    write(toWriteNow)
                }
            }
            finalFlushJoinPoint.complete(Unit)
        }
    }

    override fun log(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        fyi: Boolean,
    ) {
        if (level >= Log.Fatal) { hasFatal = true }
        val logMessage = LogMessage(
            level = level,
            template = template,
            pos = pos,
            values = values,
            fyi = fyi,
        )
        // Since channel is unlimited must succeed unless the channel is closed for sending
        channel.trySend(logMessage)
    }

    @Volatile
    final override var hasFatal = false
        private set

    data class LogMessage(
        val level: Log.Level,
        val template: MessageTemplateI,
        val pos: Position,
        val values: List<Any>,
        val fyi: Boolean,
    )
}
