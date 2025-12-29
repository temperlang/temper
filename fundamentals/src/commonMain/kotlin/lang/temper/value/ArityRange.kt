package lang.temper.value

import lang.temper.type.FunctionType
import lang.temper.type2.Signature2
import kotlin.math.max
import kotlin.math.min

val FunctionType.arityRange: IntRange
    get() {
        // TODO(tjp): Unify some with Signature if we can work out the differences.
        val min = valueFormals.count { !it.isOptional }
        val max = if (restValuesFormal != null) {
            Int.MAX_VALUE
        } else {
            valueFormals.size
        }
        return min..max
    }

val Iterable<Signature2>?.arityRange: IntRange get() =
    if (this == null) {
        0..Int.MAX_VALUE
    } else {
        val it = this.iterator()
        if (it.hasNext()) {
            val t0 = it.next()
            val r0 = t0.arityRange
            var minArity = r0.first
            var maxArity = r0.last
            while (it.hasNext()) {
                val r = it.next().arityRange
                minArity = min(minArity, r.first)
                maxArity = max(maxArity, r.last)
            }
            minArity..maxArity
        } else {
            0..Int.MAX_VALUE
        }
    }
