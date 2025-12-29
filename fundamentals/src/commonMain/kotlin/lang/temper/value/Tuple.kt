package lang.temper.value

import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.format.OutToks
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink

class Tuple private constructor(val elements: List<Value<*>>) : Structured, TokenSerializable {

    override fun destructure(structureSink: StructureSink) {
        structureSink.arr {
            for (element in elements) {
                element.destructure(structureSink)
            }
        }
    }

    override fun renderTo(tokenSink: TokenSink) = renderTo(tokenSink, false)

    fun renderTo(tokenSink: TokenSink, typeInfoIsRedundant: Boolean) {
        tokenSink.emit(OutToks.leftParen)
        for (i in elements.indices) {
            if (i != 0) {
                tokenSink.emit(OutToks.comma)
            }
            elements[i].renderTo(tokenSink, typeInfoIsRedundant)
        }
        tokenSink.emit(OutToks.rightParen)
    }

    override fun equals(other: Any?): Boolean =
        (this === other) || (other is Tuple && this.elements == other.elements)

    override fun hashCode(): Int = elements.hashCode() xor -0x4f742953

    companion object {
        operator fun invoke(elements: Iterable<Value<*>>): Tuple = Tuple(elements.toList())
    }
}
