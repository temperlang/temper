package lang.temper.common

/**
 * Logging primitives that don't collide with stdout.
 */
expect fun printErr(s: String)

/** Like [printErr] but does not end line. */
expect fun printErrNoEol(s: String)

expect fun eraseLine()

expect fun terminalWidth(): Int
