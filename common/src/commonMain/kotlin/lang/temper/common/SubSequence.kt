package lang.temper.common

/**
 * A subsequence that shares without copy.  Small subsequences of a large backing store will pin
 * in memory so this is best used when the SubSequence quickly becomes collectible.
 */
class SubSequence private constructor(
    private val underlying: CharSequence,
    private val offset: Int,
    override val length: Int,
) : CharSequence {
    override fun get(index: Int): Char {
        require(index in 0 until length)
        return underlying[offset + index]
    }
    override fun subSequence(startIndex: Int, endIndex: Int) = of(underlying, startIndex, endIndex)

    override fun toString() = toStringViaBuilder(length) { it.append(this) }

    companion object {
        fun of(cs: CharSequence, startIndex: Int, endIndex: Int): CharSequence {
            val csLen = cs.length
            val length = endIndex - startIndex
            require(length in 0..csLen && startIndex >= 0)
            return when {
                length == 0 -> ""
                length == csLen -> cs
                cs is SubSequence -> SubSequence(cs.underlying, cs.offset + startIndex, length)
                else -> SubSequence(cs, startIndex, length)
            }
        }
    }
}
