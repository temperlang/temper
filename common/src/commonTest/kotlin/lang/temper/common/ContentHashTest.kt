@file:Suppress("MaxLineLength", "SpellCheckingInspection") // long hex-encoded hashes

package lang.temper.common

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@DelicateCoroutinesApi
class ContentHashTest {

    @Test
    fun byteHash() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        // Derived by running:
        // $ perl -e 'print "\0\1\2\3\4\5\6\7"' | shasum -a 256 | tr 'a-z' 'A-Z'
        val want =
            "ContentHash(algorithmName=SHA-256, content=(WrappedByteArray 8A851FF82EE7048AD09EC3847F1DDF44944104D2CBD17EF4E3DB22C6785A0D45))"
        assertSuccess(
            want,
            ContentHash.fromBytes("SHA-256", bytes)
                .mapResult { "$it" },
        )
    }

    @Test
    fun byteSliceHash() {
        val bytes = byteArrayOf(-1, -1, 0, 1, 2, 3, 4, 5, 6, 7, -1, -1)
        // Derived by running:
        // $ perl -e 'print "\0\1\2\3\4\5\6\7"' | shasum -a 256 | tr 'a-z' 'A-Z'
        val want =
            "ContentHash(algorithmName=SHA-256, content=(WrappedByteArray ${
                ""
            }8A851FF82EE7048AD09EC3847F1DDF44944104D2CBD17EF4E3DB22C6785A0D45))"
        assertSuccess(
            want,
            ContentHash.fromBytes("SHA-256", bytes, startIndex = 2, endIndex = 10)
                .mapResult { "$it" },
        )
    }

    @Test
    fun textHash() {
        val input = "Hello, World!"
        // Derived by running:
        // $ perl -e 'print "Hello, World!"' | shasum -a 256 | tr 'a-z' 'A-Z'
        val want =
            "ContentHash(algorithmName=SHA-256, content=(WrappedByteArray DFFD6021BB2BD5B0AF676290809EC3A53191DD81C7F70A4B28688A362182986F))"
        assertSuccess(
            want,
            ContentHash.fromChars("SHA-256", input)
                .mapResult { "$it" },
        )
    }

    @Test
    fun badSurrogateHash() {
        val badText = invalidUnicodeString(
            """
                "foo\uD800"
            """,
        )
        val hash = ContentHash.fromChars("SHA-256", badText)
        assertIs<RFailure<*>>(hash, message = "Got $hash")
    }

    @Test
    fun badAlgorithmHash() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        val hash = ContentHash.fromBytes("SHA-123", bytes)
        assertIs<RFailure<*>>(hash, message = "Got $hash")
    }

    @Test
    fun equalsAndHashCode() {
        // ContentHash internally stores a ByteArray. Check equality is based on
        // the content of the array, not array reference identity.
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
        val hashAResult = ContentHash.fromBytes("SHA-256", bytes)
        val hashBResult = ContentHash.fromBytes("SHA-256", bytes)
        assertIs<RSuccess<ContentHash, *>>(hashAResult)
        assertIs<RSuccess<ContentHash, *>>(hashBResult)
        val hashA = hashAResult.result
        val hashB = hashBResult.result
        assertEquals(hashA, hashB)
        assertEquals(hashA.hashCode(), hashB.hashCode())
    }
}
