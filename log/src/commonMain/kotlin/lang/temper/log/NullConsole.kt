package lang.temper.log

import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.Style
import lang.temper.common.TextOutput

/** The /dev/null of [TextOutput]s */
object NullTextOutput : TextOutput() {
    override val isTtyLike: Boolean = false
    override fun emitLineChunk(text: CharSequence) {
        // This method body left intentionally blank
    }
    override fun endLine() {
        // This method body left intentionally blank
    }
    override fun flush() {
        // This method body left intentionally blank
    }
    override fun startStyle(style: Style) {
        // This method body left intentionally blank
    }
    override fun endStyle() {
        // This method body left intentionally blank
    }
}

object NullConsole : Console(NullTextOutput) {
    init {
        setLogLevel(Log.None) // So logs(level) doesn't spuriously return true
    }
}
