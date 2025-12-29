package lang.temper.common

import java.io.PrintWriter
import java.io.StringWriter

private class Trace(description: String?) : RuntimeException(description)

actual fun getDiagnosticTrace(description: String?): Any {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    Trace(description).printStackTrace(pw)
    return sw.toString()
}

actual fun traceStart(error: Throwable): String? {
    return error.stackTrace.firstOrNull()?.let { "at ${it.fileName}:${it.lineNumber}" }
}
