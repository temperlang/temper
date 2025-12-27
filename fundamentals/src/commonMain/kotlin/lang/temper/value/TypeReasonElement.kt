package lang.temper.value

import lang.temper.common.Log
import lang.temper.common.sprintf
import lang.temper.format.TokenSerializable
import lang.temper.format.toStringViaTokenSink
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplateI
import lang.temper.log.Positioned

/**
 * A type reason element explains why type inference failed.
 * @see TypeInferences.explanations
 */
interface TypeReasonElement : Positioned {
    fun logTo(logSink: LogSink)
}

abstract class AbstractTypeReasonElement : TypeReasonElement, MessageTemplateI {
    abstract val level: Log.Level
    abstract val templateFillers: List<TokenSerializable>

    private val stringValues: List<String> get() =
        templateFillers.map { templateFiller ->
            toStringViaTokenSink { templateFiller.renderTo(it) }
        }

    override fun logTo(logSink: LogSink) {
        logSink.log(level = level, template = this, pos = pos, values = templateFillers)
    }

    override fun toString(): String =
        "(TypeReasonElement $name ${level.name} `${sprintf(formatString, stringValues)}`)"
}
