package lang.temper.common

actual fun getDiagnosticTrace(description: String?): Any {
    val e: dynamic = Error(description)
    return "${ e.stack }"
}

actual fun traceStart(error: Throwable): String? {
    // TODO Use stacktrace-js if we end up caring enough.
    return "${ error.asDynamic().stack }".lineSequence().firstOrNull { lineNumberRegex in it }?.trim()
}

// Many engines have ":" followed immediately by line number (and again for column number).
private val lineNumberRegex = Regex(""":\d+""")
