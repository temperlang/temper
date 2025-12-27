package lang.temper.common

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.set
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

suspend fun <T> Promise<T>.await(): T = suspendCoroutine { cont ->
    then({ cont.resume(it) }, { cont.resumeWithException(it) })
}

internal object Digester {
    private var initialized = false
    private var digest: dynamic = null
    private var createHash: dynamic = null

    private fun initialize() {
        // Get crypto.subtle and/or createHash in a way that works across node and web
        if (!initialized) {
            val require = eval("require")
            val crypto = if (jsTypeOf(require) === "function") {
                require("crypto")
            } else {
                js("crypto")
            }
            val webCrypto = crypto.webcrypto
            val subtle = if (jsTypeOf(webCrypto) !== "undefined") {
                webCrypto
            } else {
                crypto
            }.subtle
            if (jsTypeOf(subtle) != "undefined") {
                digest = subtle.digest
            }
            // If the Web compatible crypto API is not available in Node, use the older createHash
            createHash = crypto.createHash

            initialized = true
        }
    }

    private fun bytesToInt8Array(
        bytes: ByteArray,
        startIndex: Int,
        endIndex: Int,
    ): Int8Array {
        val length = endIndex - startIndex
        val data = Int8Array(length = length)
        for (i in 0 until length) {
            data[i] = bytes[startIndex + i]
        }
        return data
    }

    private fun arrayBufferToBytes(
        resultArrayBuffer: ArrayBuffer,
    ): WrappedByteArray {
        // See https://youtrack.jetbrains.com/issue/KT-30098
        val resultBytes = Int8Array(resultArrayBuffer).unsafeCast<ByteArray>()
        return WrappedByteArray(resultBytes)
    }

    internal suspend fun digest(
        algorithmName: String,
        bytes: ByteArray,
        startIndex: Int,
        endIndex: Int,
    ): WrappedByteArray {
        initialize()

        val data = bytesToInt8Array(bytes, startIndex, endIndex)

        val resultArrayBuffer = if (jsTypeOf(digest) == "function") {
            val resultArrayBufferPromise = digest(
                algorithmName,
                data,
            ) as Promise<ArrayBuffer>
            resultArrayBufferPromise.await()
        } else {
            useCreateHash(data, algorithmName)
        }

        return arrayBufferToBytes(resultArrayBuffer)
    }

    private fun useCreateHash(
        data: Int8Array,
        algorithmName: String,
    ): ArrayBuffer =
        if (jsTypeOf(createHash) == "function") {
            // SHA-256 -> sha256
            // `openssl list -digest-algorithms` shows that openssl prefers un-dashed names
            val lcAlgorithmName = algorithmName.asciiLowerCase().replace("-", "")
            val hash = try {
                createHash(lcAlgorithmName)
            } catch (e: dynamic) {
                throw NotHashableException(e as? Throwable)
            }
            hash.update(data)
            hash.digest().buffer as ArrayBuffer
        } else {
            throw NotHashableException(null)
        }

    internal fun digestSync(
        algorithmName: String,
        bytes: ByteArray,
        startIndex: Int,
        endIndex: Int,
    ): WrappedByteArray {
        initialize()
        val data = bytesToInt8Array(bytes, startIndex, endIndex)

        val resultArrayBuffer = useCreateHash(data, algorithmName)

        return arrayBufferToBytes(resultArrayBuffer)
    }
}

internal actual suspend fun digest(
    algorithmName: String,
    bytes: ByteArray,
    startIndex: Int,
    endIndex: Int,
): WrappedByteArray = try {
    Digester.digest(
        algorithmName = algorithmName,
        bytes = bytes,
        startIndex = startIndex,
        endIndex = endIndex,
    )
} catch (e: dynamic) {
    throw NotHashableException(e as? Throwable)
}

internal actual fun digestSync(
    algorithmName: String,
    bytes: ByteArray,
    startIndex: Int,
    endIndex: Int,
): WrappedByteArray = try {
    Digester.digestSync(
        algorithmName = algorithmName,
        bytes = bytes,
        startIndex = startIndex,
        endIndex = endIndex,
    )
} catch (e: dynamic) {
    throw NotHashableException(e as? Throwable)
}
