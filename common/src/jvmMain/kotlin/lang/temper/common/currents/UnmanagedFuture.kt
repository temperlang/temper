@file:JvmName("UnmanagedFutureJvm")

package lang.temper.common.currents

import lang.temper.common.RResult
import lang.temper.common.RResult3
import lang.temper.common.RSuccess
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool.commonPool

actual typealias ExecutorService = ExecutorService

actual object UnmanagedFuture {
    actual fun <S : Any, F : Any> newCompletableFuture(
        description: String,
        executorService: ExecutorService?,
    ): CompletableRFuture<S, F> =
        newCompletableFutureImpl(description, executorService ?: commonPool())

    actual fun <S : Any, F : Any> newComputedResultFuture(
        description: String,
        executorService: ExecutorService?,
        body: () -> RResult3<S, F>,
    ): RFuture<S, F> = newComputedResultFutureImpl(description, executorService ?: commonPool(), body)

    actual fun runLater(
        description: String,
        executorService: ExecutorService?,
        body: () -> Unit,
    ): SignalRFuture = newComputedResultFutureImpl(description, executorService ?: commonPool()) {
        body()
        RSuccess(Unit)
    }

    actual fun join(
        futures: Iterable<RFuture<*, *>>,
        executorService: ExecutorService?,
    ): SignalRFuture = joinImpl(futures, executorService ?: commonPool())
}

private fun <S : Any, F : Any> newCompletableFutureImpl(
    description: String,
    executorService: ExecutorService,
): CompletableRFuture<S, F> =
    CompletableRFutureImpl(executorService, description, CompletableFuture<RResult3<S, F>>())

private fun <S : Any, F : Any> newComputedResultFutureImpl(
    description: String,
    executorService: ExecutorService,
    body: () -> RResult3<S, F>,
): RFuture<S, F> = RFutureImpl(
    executorService, description,
    CompletableFuture.supplyAsync(
        {
            runGuarded(body)
        },
        executorService,
    ),
)

private fun joinImpl(
    futures: Iterable<RFuture<*, *>>,
    executorService: ExecutorService,
): RFutureImpl<Unit, Nothing> {
    val futuresArray = futures.map {
        (it as RFutureImpl).future
    }.toTypedArray()

    @Suppress("SpreadOperator") // Need access to JVM API
    val signalFuture: CompletableFuture<RResult3<Unit, Nothing>> =
        CompletableFuture.allOf(*futuresArray)
            .thenApply { RSuccess(Unit) }
    return RFutureImpl(executorService, "joined ${futuresArray.size}", signalFuture)
}

actual fun <S : Any, F : Any> CancelGroup.newComputedResultFuture(
    description: String,
    body: () -> RResult<S, F>,
): RFuture<S, F> =
    newCompletableFutureImpl<S, F>(description, this.executorService).also { future ->
        add(future)
        runLater(description) {
            future.complete(runGuarded(body))
        }
    }

actual fun <S : Any, F : Any> CancelGroup.newCompletableFuture(
    description: String,
): CompletableRFuture<S, F> =
    newCompletableFutureImpl<S, F>(description, this.executorService).also { future ->
        add(future)
    }

/** Returns a future that completes when all of [futures] have completed. */
actual fun CancelGroup.join(futures: Iterable<RFuture<*, *>>): SignalRFuture =
    joinImpl(futures, executorService).also {
        add(it)
    }

actual fun CancelGroup.runLater(description: String, taskBody: () -> Unit): SignalRFuture =
    newComputedResultFutureImpl(description, executorService) {
        taskBody()
        RSuccess(Unit)
    }
