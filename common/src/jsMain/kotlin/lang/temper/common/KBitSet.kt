package lang.temper.common

actual class KBitSet actual constructor() {
    private val bits: MutableSet<Int> = mutableSetOf()

    /** Set the given bit. */
    actual fun set(bit: Int) {
        bits.add(bit)
    }

    /** Set a closed-open range of bits. */
    actual fun set(start: Int, stop: Int) {
        for (bit in start until stop) {
            bits.add(bit)
        }
    }

    /** Get the given bit. */
    actual fun get(bit: Int): Boolean = bit in bits

    actual fun cardinality() = bits.size

    actual override fun equals(other: Any?): Boolean =
        other is KBitSet && bits.equals(other.bits)
    actual override fun hashCode(): Int = bits.hashCode()
}
