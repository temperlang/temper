package lang.temper.common

import java.nio.charset.Charset
import kotlin.math.max

/** A wrapper for a byte array that allows reading bytes without granting write access. */
data class WrappedByteArray(
    /** After being passed in, is owned by the wrapper. */
    private val bytes: ByteArray,
) : Iterable<Byte> {
    val size get() = bytes.size

    operator fun get(index: Int) = bytes[index]

    val indices get() = bytes.indices

    override fun iterator(): ByteIterator = SafeByteIterator(bytes.iterator())

    fun copyInto(
        destination: ByteArray,
        destinationOffset: Int,
        startIndex: Int,
        endIndex: Int,
    ) = bytes.copyInto(
        destination = destination,
        destinationOffset = destinationOffset,
        startIndex = startIndex,
        endIndex = endIndex,
    )

    fun copyOf() = bytes.copyOf()

    override fun equals(other: Any?): Boolean {
        return other is WrappedByteArray && this.bytes contentEquals other.bytes
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = toStringViaBuilder { sb ->
        sb.append("(WrappedByteArray ")
        hexEncode(sb)
        sb.append(')')
    }

    fun hexEncode(builder: StringBuilder) {
        for (byte in bytes) {
            val i = byte.toInt() and BYTE_MASK
            val leftOctet = (i ushr BITS_PER_HEX_DIGIT) and HEX_DIGIT_MASK
            val rightOctet = i and HEX_DIGIT_MASK
            builder.append(HEX_DIGITS[leftOctet])
            builder.append(HEX_DIGITS[rightOctet])
        }
    }

    fun hexEncode() = toStringViaBuilder { hexEncode(it) }

    fun base64EncodeTo(out: Appendable) = bytes.base64EncodeTo(out)

    fun base64Encode(): String = bytes.base64Encode()

    /**
     * @throws CharacterCodingException
     */
    fun decodeToString(
        startIndex: Int = 0,
        endIndex: Int = bytes.size,
        throwOnInvalidSequence: Boolean = false,
    ): String = bytes.decodeToString(
        startIndex = startIndex,
        endIndex = endIndex,
        throwOnInvalidSequence = throwOnInvalidSequence,
    )

    /**
     * Allows selecting a character set, but never throws on an invalid sequence.
     */
    fun decodeToStringWithCharset(
        charset: Charset,
        startIndex: Int = 0,
        endIndex: Int = bytes.size,
    ): String = String(bytes, startIndex, endIndex, charset)

    private class SafeByteIterator(private val it: ByteIterator) : ByteIterator() {
        override fun hasNext(): Boolean = it.hasNext()
        override fun nextByte(): Byte = it.nextByte()
    }

    class Builder(
        initialBufferSize: Int = DEFAULT_INITIAL_BUFFER_SIZE,
    ) {
        private var n = 0
        private var buffer = ByteArray(max(initialBufferSize, 1))

        fun build(): WrappedByteArray {
            val bytes = ByteArray(n)
            buffer.copyInto(bytes, 0, 0, n)
            return WrappedByteArray(bytes)
        }

        fun append(bytes: ByteArray, startIndex: Int = 0, endIndex: Int = bytes.size) {
            val nToCopy = endIndex - startIndex
            require(nToCopy >= 0)
            val sizeNeeded = n + nToCopy
            if (sizeNeeded > buffer.size) {
                var newSize = buffer.size
                while (newSize < sizeNeeded) {
                    newSize *= 2
                }
                val newBuffer = ByteArray(newSize)
                buffer.copyInto(newBuffer, 0, 0, n)
                buffer = newBuffer
            }
            bytes.copyInto(
                buffer,
                destinationOffset = n,
                startIndex = startIndex,
                endIndex = endIndex,
            )
            n += nToCopy
        }

        fun append(bytes: WrappedByteArray, startIndex: Int = 0, endIndex: Int = bytes.size) {
            append(bytes.bytes, startIndex = startIndex, endIndex = endIndex)
        }

        companion object {
            const val DEFAULT_INITIAL_BUFFER_SIZE = 1024 // bytes
        }
    }

    companion object {
        inline fun build(
            initialBufferSize: Int = Builder.DEFAULT_INITIAL_BUFFER_SIZE,
            f: Builder.() -> Unit,
        ): WrappedByteArray {
            val builder = Builder(initialBufferSize = initialBufferSize)
            builder.f()
            return builder.build()
        }
        val empty = WrappedByteArray(emptyByteArray)
    }
}

private const val BYTE_MASK = 0b0_1111_1111
private const val HEX_DIGIT_MASK = 0b0_1111

@Suppress("SpellCheckingInspection")
private const val HEX_DIGITS = "0123456789ABCDEF"
