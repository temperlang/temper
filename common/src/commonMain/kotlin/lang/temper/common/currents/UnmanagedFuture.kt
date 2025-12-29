package lang.temper.common.currents

import lang.temper.common.RResult3
import lang.temper.common.RThrowable

/**
 * For futures that are not managed.  For example, they are not associated with
 * a [CancelGroup] so the creator is responsible for managing cancellation if
 * necessary.
 */
expect object UnmanagedFuture {
    fun <S : Any, F : Any> newCompletableFuture(
        description: String,
        executorService: ExecutorService? = null,
    ): CompletableRFuture<S, F>

    fun <S : Any, F : Any> newComputedResultFuture(
        description: String,
        executorService: ExecutorService? = null,
        body: () -> RResult3<S, F>,
    ): RFuture<S, F>

    fun runLater(
        description: String,
        executorService: ExecutorService? = null,
        body: () -> Unit,
    ): SignalRFuture

    /** Returns a future that completes when all of [futures] have completed. */
    fun join(
        futures: Iterable<RFuture<*, *>>,
        executorService: ExecutorService? = null,
    ): SignalRFuture
}

internal inline fun <S : Any, F : Any> runGuarded(
    body: () -> RResult3<S, F>,
): RResult3<S, F> =
    try {
        body()
    } catch (
        // We need to bundle exceptional failure into a value that can be
        // used in generic error handling strategies like transitioning
        // state machines to an error state.
        @Suppress("TooGenericExceptionCaught")
        ex: Exception,
    ) {
        RThrowable(ex)
    } catch (
        // This is a JVM Error, not an exception but is a legitimate and
        // recoverable failure mode for some computations.
        ex: StackOverflowError,
    ) {
        RThrowable(ex)
    } catch (
        // Don't propagate these as they are thrown by Kotlin's TODO().
        ex: NotImplementedError,
    ) {
        RThrowable(ex)
    }
