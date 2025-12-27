package lang.temper.kcodegen.outgrammar

import lang.temper.common.Log
import lang.temper.common.charCount
import lang.temper.common.decodeUtf16
import lang.temper.lexer.IdParts
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Positioned

internal class Id private constructor(val text: String) {
    override fun equals(other: Any?): Boolean = other is Id && this.text == other.text
    override fun hashCode(): Int = text.hashCode()
    override fun toString(): String = text

    companion object {
        fun isValidId(text: String?): Boolean {
            if (text == null || text.isEmpty()) {
                return false
            }
            val n = text.length
            var i = 0
            while (i < n) {
                val cp = decodeUtf16(text, i)
                val set = if (i == 0) {
                    IdParts.Start
                } else {
                    IdParts.Continue
                }
                if (cp !in set) {
                    break
                }
                i += charCount(cp)
            }
            return i == n
        }

        operator fun invoke(text: String?): Id {
            require(isValidId(text))
            return Id(text!!)
        }

        operator fun invoke(text: String?, p: Positioned, logSink: LogSink): Id? {
            return if (isValidId(text)) {
                Id(text!!)
            } else {
                logSink.log(
                    level = Log.Fatal,
                    template = MessageTemplate.InvalidIdentifier,
                    pos = p.pos,
                    values = emptyList(),
                )
                null
            }
        }
    }
}
