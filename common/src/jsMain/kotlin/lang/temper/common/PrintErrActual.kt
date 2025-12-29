package lang.temper.common

private val stderr: dynamic = eval(
    "(typeof require == 'function' && require('process')?.stderr) || null",
)

actual fun printErr(s: String) {
    kotlin.js.console.error(s)
}

actual fun printErrNoEol(s: String) {
    if (stderr != null) {
        stderr.write(s)
    } else {
        // This is wrong.  It ends the line.
        kotlin.js.console.error(s)
    }
}

actual fun eraseLine() {
    // TODO Better erase.
    stderr?.write("\r")
}

// This prevents worrying about the width, which should be fine for current needs.
actual fun terminalWidth() = Int.MAX_VALUE
