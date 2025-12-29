package lang.temper.common

actual fun Throwable.printStackTraceBestEffort() {
    // TODO: figure out why
    // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/stack-trace.html
    // is unavailable.
}
