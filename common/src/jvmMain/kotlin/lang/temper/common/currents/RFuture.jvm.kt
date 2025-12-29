package lang.temper.common.currents

import lang.temper.common.RResult3
import lang.temper.common.RThrowable
import lang.temper.common.orThrow
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool

actual fun <S : Any, F : Any> preComputedResultFuture(result: RResult3<S, F>): RFuture<S, F> =
    RFutureImpl(
        ForkJoinPool.commonPool(),
        "Precomputed",
        CompletableFuture.completedFuture(result),
    )

internal open class RFutureImpl<S : Any, F : Any>(
    val executorService: ExecutorService,
    override val description: String,
    val future: CompletableFuture<RResult3<S, F>>,
) : RFuture<S, F> {
    override fun await(): RResult3<S, F> = try {
        future.get()
    } catch (ce: CancellationException) {
        RThrowable(ce)
    }

    override fun resultOrNull(): RResult3<S, F>? =
        if (future.isDone) { await() } else { null }

    override val isDone get() = future.isDone

    private var _onCancellation: ((Boolean) -> Unit)? = null

    override fun onCancellation(onCancellation: (Boolean) -> Unit) {
        synchronized(this) {
            val oldOnCancellation = _onCancellation
            _onCancellation = if (oldOnCancellation == null) {
                onCancellation
            } else {
                {
                    oldOnCancellation(it)
                    onCancellation(it)
                }
            }
        }
    }

    override fun cancel(mayInterruptIfRunning: Boolean) {
        future.cancel(mayInterruptIfRunning)
        synchronized(this) { _onCancellation }?.invoke(mayInterruptIfRunning)
    }

    override fun <OS : Any, OF : Any> then(
        description: String,
        body: (RResult3<S, F>) -> RResult3<OS, OF>,
    ): RFuture<OS, OF> {
        val completableFuture: CompletableFuture<RResult3<OS, OF>> = future.handleAsync(
            { r, ex ->
                val result = when {
                    ex != null -> RThrowable(ex)
                    r != null -> r
                    else -> RThrowable(NullPointerException())
                }
                runGuarded {
                    body(result)
                }
            },
            executorService,
        )
        val thenFuture = RFutureImpl(
            executorService,
            description,
            future = completableFuture,
        )
        return thenFuture
    }

    override fun toString(): String = "RFuture($description)"
}

internal class CompletableRFutureImpl<S : Any, F : Any>(
    executorService: ExecutorService,
    description: String,
    completableFuture: CompletableFuture<RResult3<S, F>>,
) : RFutureImpl<S, F>(executorService, description, completableFuture),
    CompletableRFuture<S, F> {

    override fun complete(result: RResult3<S, F>) {
        future.complete(result)
    }

    /** Complete this with the result of [source] when it completes */
    override fun completeFrom(source: RFuture<S, F>) {
        check(source is RFutureImpl)
        source.future.handleAsync(
            { r, ex ->
                future.complete(
                    if (ex != null) {
                        RThrowable(ex)
                    } else {
                        r
                    },
                )
            },
            executorService,
        )
    }

    override fun toString(): String = "CompletableRFuture($description)"
}

/**
 * Bridge an [RFuture] to a JDK [CompletableFuture] for compatibility with
 * APIs that require JDK futures.
 */
fun <S : Any, T : Throwable> RFuture<S, T>.asJdkFuture(
    executorService: ExecutorService = ForkJoinPool.commonPool(),
): CompletableFuture<S> =
    // TODO: Java's standard library now provides a `.toCompletableFuture`.
    (this as RFutureImpl).future.handleAsync(
        { rResult: RResult3<S, T>?, err: Throwable? ->
            if (err != null) { throw err }
            rResult!!.orThrow()
        },
        executorService,
    )
