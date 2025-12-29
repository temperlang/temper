package lang.temper.common.currents

/** A concurrent task or other entity that may be cancelled. */
interface Cancellable {
    fun cancel(mayInterruptIfRunning: Boolean = false)
}

interface JoiningCancellable : Cancellable {
    override fun cancel(mayInterruptIfRunning: Boolean) {
        cancelSignalling(mayInterruptIfRunning = mayInterruptIfRunning)
    }

    /** Cancels and returns a signal indicating when the cancellation has completed. */
    fun cancelSignalling(mayInterruptIfRunning: Boolean = false): SignalRFuture
}
