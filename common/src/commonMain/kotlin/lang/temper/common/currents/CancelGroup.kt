package lang.temper.common.currents

import lang.temper.common.RResult
import kotlin.coroutines.cancellation.CancellationException

expect interface ExecutorService

/** A list that accumulates [Cancellable] items as work progresses towards a target goal. */
interface CancelGroup {
    fun add(c: Cancellable)

    fun cancelAll(mayInterruptIfRunning: Boolean = false): SignalRFuture

    /** Throws if cancelled */
    fun requireNotCancelled() {
        if (isCancelled) {
            throw CancellationException()
        }
    }

    val isCancelled: Boolean

    val executorService: ExecutorService
}

expect fun <S : Any, F : Any> CancelGroup.newComputedResultFuture(
    description: String,
    body: () -> RResult<S, F>,
): RFuture<S, F>

expect fun <S : Any, F : Any> CancelGroup.newCompletableFuture(
    description: String,
): CompletableRFuture<S, F>

expect fun CancelGroup.join(futures: Iterable<RFuture<*, *>>): SignalRFuture

expect fun CancelGroup.runLater(description: String, taskBody: () -> Unit): SignalRFuture
