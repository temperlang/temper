package lang.temper.tooling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import lang.temper.fs.AutoCloseable
import kotlin.time.Duration

/** Enables throttling events by pushing back a timer that hasn't yet fired. */
class RestartTimer(val channel: SendChannel<Unit>, val coroutineScope: CoroutineScope) : AutoCloseable {
    fun restart(duration: Duration) {
        readyTime = Clock.System.now() + duration
        job.cancel()
    }

    suspend fun run() {
        while (true) {
            job.join()
            if (closed) {
                break
            }
            val now = Clock.System.now()
            var remaining = readyTime - now
            if (remaining <= Duration.ZERO) {
                try {
                    channel.send(Unit)
                } catch (_: ClosedSendChannelException) {
                    // Another way to know we're closed. Checking `isClosedForSend` is experimental, and this works.
                    break
                }
                remaining = Duration.INFINITE
            }
            job = coroutineScope.launch {
                delay(remaining)
            }
        }
    }

    override fun close() = restart(-Duration.INFINITE)

    val closed: Boolean
        // TODO Base closed on channel state??? In addition to readyTime???
        get() = readyTime == Instant.DISTANT_PAST

    fun trigger() = restart(Duration.ZERO)

    var job: Job = coroutineScope.launch {
        awaitCancellation()
    }

    private var readyTime = Instant.DISTANT_FUTURE
}
