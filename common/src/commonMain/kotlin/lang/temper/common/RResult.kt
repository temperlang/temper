package lang.temper.common

import kotlin.reflect.KClass

/**
 * The result of a future which combines type checked success and failure,
 * and a [Throwable] due to an exception.
 *
 * This is meant to be unpacked in a block lambda via `{ (r, f, th) -> ... }`
 * where those three arguments are:
 *
 * - r: if not null, the result when things are successful
 * - f: if not null, the typed failure that explains why a result could not be computed
 * - th: if not null, a Throwable of unknown type that was raised when trying to compute a result
 *
 * Normally, exactly one of those arguments will be non-null, but if a fallback value
 * was specified for `r`, then `r` and one other will be non-null.
 *
 * If you have a useful upper bound on the type of exception that might be raised,
 * use [failure] with that upper bound, so you get more type checking, and use [RResult].
 * [RResult] is a subtype of [RResult3] where [throwable] is always null and may be ignored
 *
 * If no [Throwable] can be captured, perhaps because exceptions are dealt with farther
 * up the stack, also use [RResult].
 *
 * @param <S> the type of successful value.  Non-nullable, so [result] is not ambiguous.
 * @param <F> the type of value stored on failure. Non-nullable so [failure] is unambiguous.
 */
sealed interface RResult3<out S : Any, out F : Any> {
    // Allow unpacking into (r, f, th) as shown above.
    operator fun component1(): S? = null
    operator fun component2(): F? = null
    operator fun component3(): Throwable? = null

    val result: S? get() = null
    val failure: F? get() = null
    val throwable: Throwable? get() = null

    override fun toString(): String

    /**
     * Maps failure to failure but applied the given transform to successful results.
     * This does not attempt to trap exceptions raised during transformation.
     */
    fun <T : Any, E : Any> map(
        mapResult: (S) -> T,
        mapFailure: (F) -> E,
    ): RResult3<T, E> = when (this) {
        is RSimpleSuccess -> RSuccess(mapResult(result))
        is RFailure -> RFailure(mapFailure(failure))
        is RSuccessAndFailure -> RSuccessAndFailure(mapResult(result), mapFailure(failure))
        is RSuccessAndThrowable -> RSuccessAndThrowable(mapResult(result), throwable)
        is RThrowable -> this
    }

    fun <T : Any> mapResult(
        mapResult: (S) -> T,
    ) = map(mapResult = mapResult, mapFailure = { it })

    fun <E : Any> mapFailure(
        mapFailure: (F) -> E,
    ) = map(mapResult = { it }, mapFailure = mapFailure)
}

private fun equals(a: RResult3<*, *>, b: Any?) =
    if (a is RSuccess) {
        // Anna Karenina rules apply.
        // All RSuccess are equivalent even if the result
        // is a fallback, and they pack up a failure and/or throwable.
        b is RSuccess<*, *> &&
            a.result == b.result
    } else {
        b is RResult3<*, *> &&
            a.failure == b.failure && a.throwable == b.throwable
    }

@Suppress("MagicNumber") // constant factor used in hashCode
private fun hashCode(a: RResult3<*, *>): Int =
    if (a is RSuccess) {
        a.result.hashCode()
    } else {
        (a.failure?.hashCode() ?: 0) + 31 * (a.throwable?.hashCode() ?: 0)
    }

/**
 * An RResult3 that has either [result] or [failure] non-null.
 * For operations that are not trying to catch exceptions or which catch select
 * exceptions and convert them to [F].
 */
sealed class RResult<out S : Any, out F : Any> : RResult3<S, F> {
    override fun equals(other: Any?): Boolean = equals(this, other)
    override fun hashCode(): Int = hashCode(this)

    /** Maps the result if not null, otherwise presumes failure is non-null, and returns the applicable result. */
    inline fun <T> getOrElse(mapResult: (S) -> T, mapFailure: (F) -> T): T =
        result?.let(mapResult) ?: mapFailure(failure!!)

    override fun <T : Any, E : Any> map(mapResult: (S) -> T, mapFailure: (F) -> E): RResult<T, E> =
        super.map(mapResult, mapFailure) as RResult<T, E>

    override fun <T : Any> mapResult(mapResult: (S) -> T): RResult<T, F> =
        super.mapResult(mapResult) as RResult<T, F>

    override fun <E : Any> mapFailure(mapFailure: (F) -> E): RResult<S, E> =
        super.mapFailure(mapFailure) as RResult<S, E>

    fun <T : Any, E : Any> flatMap(
        mapSucc: (S) -> RResult<T, E>,
        mapFail: (F) -> RResult<T, E>,
    ): RResult<T, E> = when (this) {
        is RSuccess -> mapSucc(result)
        is RFailure -> mapFail(failure)
    }

    companion object {
        /**
         * When f succeeds, a success result with its result.
         * When f raises an exception with type t, a failure result with that error.
         * When f raises a different exception type, it propagates to the caller.
         * If there are exceptions covered by the passed class, we can specify subtypes to rethrow, especially
         * CancellationException.
         */
        inline fun <S : Any, T : Throwable> of(
            t: KClass<T>,
            rethrow: List<KClass<out T>> = emptyList(),
            f: () -> S,
        ): RResult<S, T> {
            val v: S
            try {
                v = f()
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                if (!t.isInstance(e)) {
                    throw e
                }
                if (rethrow.any { it.isInstance(e) }) {
                    throw e
                }
                @Suppress("UNCHECKED_CAST") // isInstance -> sound when F is not parameterized
                return RFailure(e as T)
            }
            return RSuccess(v)
        }

        inline fun <S : Any> of(
            rethrow: List<KClass<out Exception>> = emptyList(),
            f: () -> S,
        ): RResult<S, Exception> = of(t = Exception::class, rethrow = rethrow, f = f)
    }
}

sealed class RSuccess<out S : Any, out F : Any>(override val result: S) : RResult<S, F>() {
    override operator fun component1(): S = result

    override fun toString(): String = "RSuccess($result)"

    companion object {
        operator fun <S : Any> invoke(result: S): RSuccess<S, Nothing> = RSimpleSuccess(result)
    }
}

private class RSimpleSuccess<out S : Any>(result: S) : RSuccess<S, Nothing>(result)

class RFailure<out F : Any>(override val failure: F) : RResult<Nothing, F>() {
    override operator fun component2(): F = failure

    override fun toString(): String = "RFailure($failure)"
}

class RThrowable(override val throwable: Throwable) : RResult3<Nothing, Nothing> {
    override operator fun component3(): Throwable = throwable

    override fun equals(other: Any?): Boolean = equals(this, other)
    override fun hashCode(): Int = hashCode(this)
    override fun toString(): String = "RThrowable($throwable)"
}

private class RSuccessAndFailure<out S : Any, out F : Any>(
    result: S,
    override val failure: F,
) : RSuccess<S, F>(result) {
    override fun component2(): F = failure
    override fun toString(): String = "RSuccessAndFailure($result, $failure)"
}

private class RSuccessAndThrowable<out S : Any>(
    result: S,
    override val throwable: Throwable,
) : RSuccess<S, Nothing>(result) {
    override fun component3(): Throwable = throwable
    override fun toString(): String = "RSuccessAndThrowable($result, $throwable)"
}

/**
 * If [this] is [RSuccess], return it unchanged, else a [RSuccess]
 * with [fallback] as [RSuccess.result] but that does not lose information
 * about the failure or exception.
 */
fun <COMMON_S : Any, S : COMMON_S, T : COMMON_S, F : Any>
    RResult3<S, F>.or(
        fallback: T,
    ): RSuccess<COMMON_S, F> =
    when (this) {
        is RSuccess -> this
        is RThrowable -> RSuccessAndThrowable(fallback, throwable)
        is RFailure -> RSuccessAndFailure(fallback, failure)
    }

/**
 * Allow invoking a result with a block that handles success and failure paths
 * in a type-safe manner.
 */
inline operator fun <S, F, R : RResult3<S, F>, O : Any?> R.invoke(
    body: (R) -> O,
): O {
    // TODO: Maybe Use kotlin.contracts to establish that
    //   e == null && throwable == null -> x != null
    return body(this)
}

/**
 * Allow invoking a result with a block that handles success and failure paths
 * in a type-safe manner, but where the result is the given fallback value if
 * [this] is not [RSuccess] as per [or].
 */
inline operator fun <COMMON_S : Any, S : COMMON_S, F : Any, T : COMMON_S, O : Any?>
    RResult3<S, F>.invoke(
        fallback: T,
        body: (RSuccess<COMMON_S, F>) -> O,
    ): O = body(this.or(fallback))

fun <S : Any, F : Throwable> RResult3<S, F>.orThrow(): S = when (this) {
    is RSuccess -> result
    is RFailure -> throw failure
    is RThrowable -> throw throwable
}

fun <S : Any, F : Any, T : Any> RResult<S, F>.flatMap(
    body: (S) -> RResult<T, F>,
): RResult<T, F> = when (this) {
    is RFailure -> this
    is RSuccess -> body(result)
}
