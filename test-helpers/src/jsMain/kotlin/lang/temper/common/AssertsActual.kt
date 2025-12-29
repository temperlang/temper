package lang.temper.common

import kotlin.test.assertEquals

actual fun stringsNotEqual(messageStr: String, wantStr: String, gotStr: String): Nothing {
    assertEquals(wantStr, gotStr, if (messageStr == "") null else messageStr)
    throw AssertionError("$messageStr: `$wantStr` != `$gotStr`")
}

actual fun failWithCause(messageStr: String, cause: Throwable?): Nothing {
    @Suppress("TooGenericExceptionThrown") // Figure out how to thread cause via AssertionError.
    throw Error(messageStr, cause)
}

internal actual fun tryRunDiff(left: String, right: String) {
    ignore(left)
    ignore(right)
}
