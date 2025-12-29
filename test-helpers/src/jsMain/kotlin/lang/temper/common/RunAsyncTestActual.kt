package lang.temper.common

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

// GlobalScope is sufficient for test code which does not need to cancel/join gracefully
@DelicateCoroutinesApi
actual fun runAsyncTest(block: suspend () -> Unit): dynamic = GlobalScope.promise {
    block()
}
