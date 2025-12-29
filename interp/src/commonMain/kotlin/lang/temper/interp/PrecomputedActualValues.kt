package lang.temper.interp

import lang.temper.common.toStringViaBuilder
import lang.temper.log.Position
import lang.temper.name.Symbol
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.Actuals
import lang.temper.value.Panic
import lang.temper.value.TNull
import lang.temper.value.Value
import kotlin.math.max

internal class PrecomputedActualValues(
    private val actuals: Actuals,
    private val values: List<Value<*>>,
    private val sig: Signature2? = null,
) : ActualValues {
    override fun result(index: Int, computeInOrder: Boolean) = when {
        index < values.size -> values[index]
        index < size -> {
            val formal = sig!!.valueFormalForActual(index)!!
            formal.isOptional || throw Panic("optional expected")
            TNull.value
        }
        else -> throw IndexOutOfBoundsException(index)
    }

    override val size: Int get() = max(values.size, sig?.requiredAndOptionalValueFormals?.size ?: 0)

    override fun key(index: Int): Symbol? = when {
        index < values.size -> actuals.key(index)
        index < size -> null
        else -> throw IndexOutOfBoundsException(index)
    }

    override fun pos(index: Int): Position? = when {
        index < values.size -> actuals.pos(index)
        index < size -> null
        else -> throw IndexOutOfBoundsException(index)
    }

    override fun peekType(index: Int) = result(index, false).typeTag
    override fun clearResult(index: Int) {
        // Nothing to do
    }

    override fun toString() = toStringViaBuilder { sb ->
        sb.append("(PrecomputedActualValues ")
        for (i in indices) {
            if (i != 0) { sb.append(", ") }
            val key = key(i)
            val value = values.getOrNull(i)
            if (key != null) {
                sb.append(key.text)
                sb.append('=')
            }
            if (value != null) {
                sb.append(value)
            } else {
                sb.append("<missing>")
            }
        }
        sb.append(")")
    }
}
