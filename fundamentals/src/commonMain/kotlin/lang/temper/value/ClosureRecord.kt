package lang.temper.value

import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.format.OutToks
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.name.TemperName

/**
 * A closure record relates local variables in some environment to values that can be used to
 * manipulate them.
 */
class ClosureRecord(
    val localAccessors: List<LocalAccessor>,
) : StayReferrer, Structured, TokenSerializable {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ClosureRecord && this.localAccessors == other.localAccessors)

    override fun hashCode(): Int = localAccessors.hashCode()

    override fun destructure(structureSink: StructureSink) = structureSink.arr {
        localAccessors.forEach {
            it.destructure(structureSink)
        }
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutToks.closRecWord)
        tokenSink.emit(OutToks.leftParen)
        for (i in localAccessors.indices) {
            if (i != 0) {
                tokenSink.emit(OutToks.comma)
            }
            localAccessors[i].renderTo(tokenSink)
        }
        tokenSink.emit(OutToks.rightParen)
    }

    override fun addStays(s: StaySink) {
        localAccessors.forEach { it.addStays(s) }
    }
}

data class LocalAccessor(
    val name: TemperName,
    val getter: Value<MacroValue>?,
    val setter: Value<MacroValue>?,
) : StayReferrer, Structured, TokenSerializable {
    override fun addStays(s: StaySink) {
        getter?.addStays(s)
        setter?.addStays(s)
    }

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        // In the common case, a local is read but not written
        val defaultHasGetter = true
        val defaultHasSetter = false

        val hasGetter = getter != null
        val hasSetter = setter != null
        key(
            "name",
            if (hasGetter == defaultHasGetter && hasGetter == defaultHasSetter) {
                Hints.su
            } else {
                Hints.u
            },
        ) {
            value(name)
        }
        key("getter", isDefault = hasGetter == defaultHasGetter) { value(hasGetter) }
        key("setter", isDefault = hasSetter == defaultHasSetter) { value(hasSetter) }
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(name.toToken(inOperatorPosition = false))
    }
}
