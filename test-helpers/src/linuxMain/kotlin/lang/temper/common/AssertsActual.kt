package lang.temper.common

actual fun stringsNotEqual(messageStr: String, wantStr: String, gotStr: String): Nothing {
    throw AssertionError("$messageStr: `$wantStr` != `$gotStr`")
}

actual fun failWithCause(
    messageStr: String,
    cause: Throwable?
): Nothing {
    throw AssertionError(messageStr, cause)
}

internal actual fun tryRunDiff(left: String, right: String) {
    ignore(left)
    ignore(right)
}
