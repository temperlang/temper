package lang.temper.tooling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class RestartTimerTest {
    /** Huge buffer, and even this is sometimes exceeded. :( */
    private val expectedOverhead = 0.5.seconds

    @Test
    fun waitNone() {
        waitRestart(expectedDelay = Duration.ZERO) { timer ->
            timer.trigger()
        }
    }

    @Test
    fun waitOnce() {
        // Use sadly huge delays to hopefully address unreliability in scheduling.
        val waitTime = 1.0.seconds
        waitRestart(expectedDelay = waitTime) { timer ->
            timer.restart(waitTime)
        }
    }

    @Test
    fun waitOnceDelay() {
        val waitTime = 1.0.seconds
        waitRestart(expectedDelay = waitTime) { timer ->
            timer.restart(waitTime)
            // This should have no impact on end time.
            delay(waitTime * 0.5)
        }
    }

    @Test
    fun waitRestartAgain() {
        val waitTime = 1.0.seconds
        val pad = 0.5
        waitRestart(expectedDelay = waitTime * (1 + pad)) { timer ->
            timer.restart(waitTime)
            // Second restart picks up from here and overwrites the earlier one.
            delay(waitTime * pad)
            timer.restart(waitTime)
        }
    }

    fun waitRestart(expectedDelay: Duration, action: suspend (RestartTimer) -> Unit) {
        val coroutineScope = CoroutineScope(Dispatchers.Default)
        val channel = Channel<Unit>(Channel.UNLIMITED)
        val timer = RestartTimer(channel = channel, coroutineScope = coroutineScope)
        coroutineScope.launch { timer.run() }
        val elapsed = runBlocking(coroutineScope.coroutineContext) {
            measureTime {
                action(timer)
                channel.receive()
            }
        }
        val extra = elapsed - expectedDelay
        assertTrue(extra >= Duration.ZERO, "$extra is negative")
        assertTrue(extra < expectedOverhead, "$extra too large")
    }
}
