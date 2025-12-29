package lang.temper.fs

import kotlin.math.max
import kotlin.math.min

/** A Java *Reader* equivalent for Kotlin common. */
interface CharSource : AutoCloseable {
    /**
     * Read up to [length] chars into [arr] starting at [offset].
     * @return count of chars read.
     */
    suspend fun read(arr: CharArray, offset: Int, length: Int): Int

    /** A guess at the size of the underlying content. */
    val sizeGuess: Int

    companion object {
        fun from(charSequence: CharSequence): CharSource = CharSequenceSource(charSequence)
        fun from(charArray: CharArray): CharSource = CharArraySource(charArray)
        fun buffered(charSource: CharSource, bufferSize: Int = DEFAULT_BUFFER_SIZE): CharSource =
            BufferedCharSource(charSource, bufferSize = bufferSize)

        const val DEFAULT_BUFFER_SIZE = 1024 // byte pairs
    }
}

private class CharSequenceSource(
    var charSequence: CharSequence?,
) : CharSource {
    private var pos = 0

    override val sizeGuess: Int
        get() = charSequence?.length ?: 0

    override suspend fun read(arr: CharArray, offset: Int, length: Int): Int {
        val charSequence: CharSequence = this.charSequence ?: return 0
        val nAvailable = charSequence.length - pos
        val nToRead = max(0, min(length, nAvailable))
        for (i in 0 until nToRead) {
            arr[offset + i] = charSequence[pos + i]
        }
        pos += nToRead
        return nToRead
    }

    override fun close() {
        // Release charSequence for GC
        this.charSequence = null
    }
}

// Duplicative with ByteArraySource because using Java generics to
// abstract over Byte&Char is inefficient.
@Suppress("DuplicatedCode")
private class CharArraySource(
    var charArray: CharArray?,
) : CharSource {
    private var pos = 0

    override val sizeGuess: Int
        get() = charArray?.size ?: 0

    override suspend fun read(arr: CharArray, offset: Int, length: Int): Int {
        val charArray: CharArray = this.charArray ?: return 0
        val nAvailable = charArray.size - pos
        val nToRead = max(0, min(length, nAvailable))
        if (nToRead != 0) {
            charArray.copyInto(
                destination = arr,
                destinationOffset = offset,
                startIndex = pos,
                endIndex = pos + nToRead,
            )
        }
        pos += nToRead
        return nToRead
    }

    override fun close() {
        // Release charSequence for GC
        this.charArray = null
    }
}

// Duplicative with BufferedByteSource because using Java generics to
// abstract over Byte&Char is inefficient.
@Suppress("DuplicatedCode")
private class BufferedCharSource(
    var underlying: CharSource?,
    bufferSize: Int,
) : CharSource {
    private var buffer: CharArray? = CharArray(bufferSize)
    private var left: Int = 0
    private var right: Int = 0

    override val sizeGuess: Int
        get() = underlying?.sizeGuess ?: 0

    override suspend fun read(arr: CharArray, offset: Int, length: Int): Int {
        val buffer = this.buffer ?: return 0
        val underlying = this.underlying
        var nReadTotal = 0

        while (nReadTotal < length) {
            var nAvailable = right - left
            if (nAvailable == 0) {
                left = 0
                right = 0
                if (underlying != null) {
                    val nReadIntoBuffer = underlying.read(buffer, 0, buffer.size)
                    if (nReadIntoBuffer <= 0) {
                        break
                    }
                    right = nReadIntoBuffer
                }
                nAvailable = right - left
                if (nAvailable <= 0) {
                    break
                }
            }

            val nToRead = max(0, min(nAvailable, length - nReadTotal))
            buffer.copyInto(
                destination = arr,
                destinationOffset = offset + nReadTotal,
                startIndex = left,
                endIndex = left + nToRead,
            )
            left += nToRead
            nReadTotal += nToRead
        }
        return nReadTotal
    }

    override fun close() {
        this.buffer = null
        val underlying = this.underlying
        this.underlying = null
        underlying?.close()
    }
}
