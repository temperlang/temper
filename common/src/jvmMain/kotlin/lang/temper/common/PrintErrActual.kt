package lang.temper.common

import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole

actual fun printErr(s: String) {
    System.err.println(s)
    System.err.flush()
}

actual fun printErrNoEol(s: String) {
    System.err.print(s)
    System.err.flush()
}

actual fun eraseLine() {
    System.err.print(Ansi.ansi().eraseLine(Ansi.Erase.ALL))
    System.err.print("\r")
}

actual fun terminalWidth() = AnsiConsole.getTerminalWidth()
