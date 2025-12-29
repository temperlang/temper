package lang.temper.fs

import kotlin.math.max
import kotlin.math.min

/** A Java *Reader* equivalent for Kotlin common. */
interface ByteSource : AutoCloseable {
    /**
     * Read up to [length] bytes into [arr] starting at [offset].
     * @return count of bytes read.
     */
    suspend fun read(arr: ByteArray, offset: Int, length: Int): Int

    /** A guess at the size of the underlying content. */
    val sizeGuess: Int

    companion object {
        fun from(byteArray: ByteArray): ByteSource = ByteArraySource(byteArray)
        fun buffered(byteSource: ByteSource, bufferSize: Int = DEFAULT_BUFFER_SIZE): ByteSource =
            BufferedByteSource(byteSource, bufferSize = bufferSize)

        const val DEFAULT_BUFFER_SIZE = 1024 // bytes
    }
}

// Duplicative with CharArraySource because using Java generics to
// abstract over Byte&Char is inefficient.
@Suppress("DuplicatedCode")
private class ByteArraySource(
    var byteArray: ByteArray?,
) : ByteSource {
    private var pos = 0

    override val sizeGuess: Int
        get() = byteArray?.size ?: 0

    override suspend fun read(arr: ByteArray, offset: Int, length: Int): Int {
        val byteArray: ByteArray = this.byteArray ?: return 0
        val nAvailable = byteArray.size - pos
        val nToRead = max(0, min(length, nAvailable))
        if (nToRead != 0) {
            byteArray.copyInto(
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
        // Release byteSequence for GC
        this.byteArray = null
    }
}

// Duplicative with BufferedCharSource because using Java generics to
// abstract over Byte&Char is inefficient.
@Suppress("DuplicatedCode")
private class BufferedByteSource(
    var underlying: ByteSource?,
    bufferSize: Int,
) : ByteSource {
    private var buffer: ByteArray? = ByteArray(bufferSize)
    private var left: Int = 0
    private var right: Int = 0

    override val sizeGuess: Int
        get() = underlying?.sizeGuess ?: 0

    override suspend fun read(arr: ByteArray, offset: Int, length: Int): Int {
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
