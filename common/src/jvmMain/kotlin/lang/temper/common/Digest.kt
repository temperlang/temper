package lang.temper.common

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

internal actual fun digest(
    algorithmName: String,
    bytes: ByteArray,
    startIndex: Int,
    endIndex: Int,
): WrappedByteArray {
    val messageDigest = try {
        MessageDigest.getInstance(algorithmName)
    } catch (e: NoSuchAlgorithmException) {
        throw NotHashableException(e)
    }
    messageDigest.update(bytes, startIndex, endIndex - startIndex)
    return WrappedByteArray(messageDigest.digest())
}
