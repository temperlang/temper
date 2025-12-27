package lang.temper.common

import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.fail

expect fun stringsNotEqual(messageStr: String, wantStr: String, gotStr: String): Nothing

expect fun failWithCause(messageStr: String, cause: Throwable? = null): Nothing

internal expect fun tryRunDiff(left: String, right: String)

private const val LONG_STR_THRESHOLD = 50_000
private const val EXTRA_CONTENT_LEN = 100
private const val MIN_LEN_RATIO = 10

fun assertStringsEqual(want: String, got: String, message: String? = null) {
    if (want != got) {
        // Intellij does badly when the strings are quite long which is usually the case for
        // runaway output.
        val gotEnough =
            if (got.length > LONG_STR_THRESHOLD && got.length >= want.length * MIN_LEN_RATIO) {
                console.warn("Test result length exceeded $LONG_STR_THRESHOLD.  Abbreviating")
                val prefix = got.substring(0, min(want.length + EXTRA_CONTENT_LEN, got.length))
                "$prefix...\nEXCESS ELIDED"
            } else {
                got
            }
        tryRunDiff(want, got)
        stringsNotEqual(message ?: "", want, gotEnough)
    }
}

fun <S : Any, F : Throwable> assertSuccess(want: S, got: RResult<S, F>) {
    assertSuccess(null, want, got)
}

fun <S : Any, F : Throwable> assertSuccess(
    message: String?,
    want: S,
    got: RResult<S, F>,
) {
    when (got) {
        is RFailure ->
            failWithCause(
                "${if (message != null) "$message: " else ""}Expected success, got $got",
                got.failure,
            )
        is RSuccess -> {
            val x = got.result
            if (x != want) {
                val wantStr = "$want"
                val gotStr = "$x"
                val messageStr = message ?: "Success but value differs"
                if (wantStr != gotStr) {
                    stringsNotEqual(messageStr, wantStr, gotStr)
                }
                assertEquals(expected = want, actual = x, message = message)
            }
        }
    }
}

fun <S : Any, F : Throwable> assertFailure(
    expectedExceptionMessage: String,
    got: RResult<S, F>,
) {
    assertFailure(null, expectedExceptionMessage, got)
}

fun <S : Any, F : Throwable> assertFailure(
    message: String?,
    expectedExceptionMessage: String,
    got: RResult<S, F>,
) {
    when (got) {
        is RFailure -> {
            val gotMessage = got.failure.message
            if (gotMessage != expectedExceptionMessage) {
                val messageStr =
                    message ?: "Expected `$expectedExceptionMessage` but got `$gotMessage`"
                stringsNotEqual(messageStr, expectedExceptionMessage, gotMessage ?: "")
            }
        }
        is RSuccess ->
            fail("Expected failure not $got")
    }
}
