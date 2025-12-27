package lang.temper.builtin

import lang.temper.value.ComparableTypeTag
import lang.temper.value.TNull
import lang.temper.value.TypeTag
import lang.temper.value.Value

/** Assumes [a] and [b] have this type tag and compares them using the type's natural order. */
internal fun <V : Any> (ComparableTypeTag<V>).compareBoth(a: Value<*>, b: Value<*>): Int {
    compareHandlingNull(a.typeTag, b.typeTag) { return@compareBoth it }
    return this.comparator.compare(this.unpack(a), this.unpack(b))
}

internal inline fun compareHandlingNull(
    aTypeTag: TypeTag<*>,
    bTypeTag: TypeTag<*>,
    onNullFound: (Int) -> Unit,
) {
    // Sort null early.
    val delta = when {
        aTypeTag == TNull -> if (bTypeTag == TNull) { 0 } else { -1 }
        bTypeTag == TNull -> 1
        else -> return
    }
    onNullFound(delta)
}
