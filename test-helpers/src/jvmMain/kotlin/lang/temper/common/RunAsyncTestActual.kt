package lang.temper.common

import kotlinx.coroutines.runBlocking

actual fun runAsyncTest(block: suspend () -> Unit) = runBlocking {
    block()
}
