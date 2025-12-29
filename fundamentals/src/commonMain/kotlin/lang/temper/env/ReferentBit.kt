package lang.temper.env

@Suppress("MagicNumber") // These are symbolic names for bit masks.
enum class ReferentBit {
    Symbol,
    Type,
    Initial,
    InOut,
    Value,
    Constness,
    ;

    val bit = 1 shl ordinal

    init {
        require(bit > 0 && (bit and (bit - 1)) == 0)
    }
}

@Suppress("unused")
infix fun (ReferentBit).or(b: ReferentBit): Int = bit or b.bit
infix fun (ReferentBit).or(mask: Int): Int = bit or mask

/** A group of [ReferentBit]s */
class ReferentBitSet private constructor(
    val bits: Int, // TODO: inline class?
) : Iterable<ReferentBit> {
    override fun equals(other: Any?) = other is ReferentBitSet && bits == other.bits

    override fun hashCode(): Int = bits

    override fun toString(): String = this.toList().toString()

    override fun iterator() = object : Iterator<ReferentBit> {
        private var i = 0
        override fun hasNext(): Boolean = peek() != null
        override fun next(): ReferentBit {
            val next = peek() ?: throw NoSuchElementException()
            i += 1
            return next
        }
        fun peek(): ReferentBit? {
            val n = referentBitValues.size
            while (i < n) {
                val bit = 1 shl i
                if ((bits and bit) != 0) {
                    return referentBitValues[i]
                }
                i += 1
            }
            return null
        }
    }

    infix fun or(b: ReferentBit) = forBitMask(bits or b.bit)

    infix fun or(other: ReferentBitSet) = forBitMask(bits or other.bits)

    infix fun and(other: ReferentBitSet) = forBitMask(bits and other.bits)

    operator fun minus(other: ReferentBitSet) = forBitMask(bits and other.bits.inv())
    operator fun minus(other: ReferentBit) = forBitMask(bits and other.bit.inv())

    operator fun contains(b: ReferentBit) = (bits and b.bit) != 0

    companion object {
        private val referentBitValues = ReferentBit.values()

        private val values = (0 until (1 shl referentBitValues.size)).map {
            ReferentBitSet(it)
        }

        fun forBitMask(bits: Int) = values[bits]

        val complete = forBitMask(values.size - 1)

        val empty = forBitMask(0)

        private val wellformedButValuelessBits =
            ReferentBit.Type.bit or ReferentBit.InOut.bit or
                ReferentBit.Initial.bit or ReferentBit.Symbol.bit

        val wellformedButValueless = forBitMask(wellformedButValuelessBits)
    }
}
