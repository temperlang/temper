package lang.temper.common.currents

import lang.temper.common.RFailure
import lang.temper.common.RResult3
import lang.temper.common.RSuccess
import lang.temper.common.RThrowable
import lang.temper.common.or

/**
 * A result that may be available now or in the future.
 * Holders may delay the current thread until the result is available.
 */
interface RFuture<out S : Any, out F : Any> : DebuggableCurrent, Cancellable {
    /** Blocks until this completes and returns its result. */
    fun await(): RResult3<S, F>

    /** Without blocking, the result or null if incomplete. */
    fun resultOrNull(): RResult3<S, F>?

    val isDone: Boolean

    fun onCancellation(onCancellation: (Boolean) -> Unit)

    /** Blocks until this completes and then applies [body] to its result. */
    fun <O : Any?> await(body: (RResult3<S, F>) -> O): O = body(await())

    /**
     * Schedules [body] to run after this completes.
     * If this has already completes, body is scheduled to run soon.
     */
    fun <OS : Any, OF : Any> then(
        description: String,
        body: (RResult3<S, F>) -> RResult3<OS, OF>,
    ): RFuture<OS, OF>

    /**
     * Like [then] but does not return a future, so the [body] doesn't
     * need to produce a result.
     */
    fun thenDo(
        description: String,
        body: (RResult3<S, F>) -> Unit,
    ) {
        then(description) { result ->
            body(result)
            result
        }
    }
}

/** A future that may be explicitly completed. */
interface CompletableRFuture<S : Any, F : Any> : RFuture<S, F> {
    fun completeOk(x: S) { complete(RSuccess(x)) }
    fun completeFail(e: F) { complete(RFailure(e)) }
    fun completeError(ex: Throwable) { complete(RThrowable(ex)) }

    fun complete(result: RResult3<S, F>)

    /** Complete this with the result of [source] when it completes */
    fun completeFrom(source: RFuture<S, F>)

    fun <OS : Any> completeMappedFrom(
        source: CompletableRFuture<OS, F>,
        description: String,
        body: (OS) -> S,
    ) {
        source.thenDo(description) { result ->
            result.failure?.let { completeFail(it) }
                ?: result.throwable?.let { completeError(it) }
                ?: runCatching { body(result.result!!) }.fold(
                    onSuccess = { completeOk(it) },
                    onFailure = { completeError(it) },
                )
        }
    }
}

fun <COMMON_S : Any, S : COMMON_S, ALT_S : COMMON_S, F : Any, O : Any?> RFuture<S, F>.await(
    alternative: ALT_S,
    body: (RSuccess<COMMON_S, F>) -> O,
): O = body(await().or(alternative))

/**
 * A future that is not used to produce a value but which you can await
 * to know that an event has happened.
 */
typealias SignalRFuture = RFuture<Unit, Nothing>
typealias CompletableSignalRFuture = CompletableRFuture<Unit, Nothing>

expect fun <S : Any, F : Any> preComputedResultFuture(result: RResult3<S, F>): RFuture<S, F>
fun <S : Any> preComputedFuture(result: S): RFuture<S, Nothing> = preComputedResultFuture(
    RSuccess(result),
)
