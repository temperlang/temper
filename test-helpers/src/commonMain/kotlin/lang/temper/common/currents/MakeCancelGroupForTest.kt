package lang.temper.common.currents

import java.util.concurrent.ForkJoinPool

fun makeCancelGroupForTest(): CancelGroup = ShouldNotCancelGroup

internal object ShouldNotCancelGroup : CancelGroup {
    override fun add(c: Cancellable) {
        // No need to actually keep a list for a functional test run.
    }

    override fun cancelAll(mayInterruptIfRunning: Boolean): SignalRFuture {
        error("Very suspicious.  Unit test runs should not be cancelled")
    }

    override val isCancelled: Boolean = false

    override val executorService: ExecutorService =
        ForkJoinPool.commonPool()
}
