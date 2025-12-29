package lang.temper.common

/**
 * Calls [f] with a console that captures anything written to it, and returns
 * the result of the call to [f] and any text that was written during that call.
 */
fun <T> withCapturingConsole(level: Log.LevelFilter = Log.Info, f: (Console) -> T): Pair<T, String> {
    val consoleBuffer = StringBuilder()
    val bufferingConsole = newBufferingConsole(consoleBuffer)
    bufferingConsole.setLogLevel(level)
    val result = f(bufferingConsole)
    val messages = "$consoleBuffer"
    return result to messages
}

/** Returns a non-TTY console that appends to the given buffer. */
fun newBufferingConsole(consoleBuffer: StringBuilder) = Console(
    AppendingTextOutput(consoleBuffer),
)
