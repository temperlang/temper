package lang.temper.common

typealias ContentHashResult = RResult<ContentHash, NotHashableException>

/**
 * A hash of textual or byte content.
 *
 * Textual content can only be hashed when it UTF-8 encodes strictly, so there are no surrogates
 * that are not part of a well-formed surrogate pair.
 */
data class ContentHash( // TODO: does this constructor need to be public?
    /**
     * A [FIPS](https://csrc.nist.gov/projects/hash-functions) hash algorithm name.
     * Not all FIPS name are available on all platforms.
     */
    val algorithmName: String,
    /** The bytes produced by the hash function. */
    val content: WrappedByteArray,
) {
    fun appendHex(appendable: Appendable) {
        for (i in content.indices) {
            val byte = content[i].toUByte().toInt()
            appendable.append(HEX_DIGITS[(byte ushr BITS_PER_HEX_DIGIT) and HEX_DIGIT_MASK])
            appendable.append(HEX_DIGITS[byte and HEX_DIGIT_MASK])
        }
    }

    /** A short hash suitable for log strings, like git's short 7-hex-digit hashes. */
    val shortHex: String
        get() = toStringViaBuilder { sb ->
            appendHex(sb)
            if (sb.length > SHORT_HASH_DIGIT_LEN) {
                sb.setLength(SHORT_HASH_DIGIT_LEN)
            }
        }

    companion object {
        /**
         * The hash of the byte content in [bytes].slice([startIndex], [endIndex]).
         *
         * @return A failure with a [NotHashableException] when [algorithmName] is unsupported.
         */
        fun fromBytes(
            algorithmName: String,
            bytes: ByteArray,
            startIndex: Int = 0,
            endIndex: Int = bytes.size,
        ): ContentHashResult =
            RResult.of(NotHashableException::class) {
                digest(algorithmName, bytes, startIndex = startIndex, endIndex = endIndex)
            }.mapResult {
                ContentHash(algorithmName, it)
            }

        /**
         * The hash of the textual content.
         *
         * @return A failure with a [NotHashableException] when [algorithmName] is unsupported
         *     or the textual content has un-paired surrogates.
         */
        fun fromChars(
            algorithmName: String,
            chars: String,
        ): ContentHashResult {
            val encoded = RResult.of(CharacterCodingException::class) {
                chars.encodeToByteArray(
                    startIndex = 0,
                    endIndex = chars.length,
                    throwOnInvalidSequence = true,
                )
            }
            return when (encoded) {
                is RSuccess -> fromBytes(algorithmName, encoded.result)
                is RFailure -> RFailure(NotHashableException(encoded.throwable))
            }
        }
    }
}

/** Thrown when hashing is not possible */
class NotHashableException(cause: Throwable?) : RuntimeException(cause)

internal expect fun digest(
    algorithmName: String,
    bytes: ByteArray,
    startIndex: Int = 0,
    endIndex: Int = bytes.size,
): WrappedByteArray

private const val HEX_DIGITS = "0123456789ABCDEF"
private const val HEX_DIGIT_MASK = 0b0_1111
private const val SHORT_HASH_DIGIT_LEN = 7
