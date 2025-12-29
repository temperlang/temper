package lang.temper.common

actual fun getDiagnosticTrace(description: String?): Any {
    // Throwable().getStackTrace()  // Supposed to work per 1.3 docs.
    @Suppress("FunctionOnlyReturningConstant")
    return "TODO"
}
