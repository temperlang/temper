package lang.temper.docbuild

import lang.temper.common.WrappedByteArray
import lang.temper.common.runAsyncTest
import lang.temper.common.toStringViaBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class DocContentTest {
    @Test
    fun hash() = runAsyncTest {
        val contents = listOf(
            TextDocContent("Hello, World!"),
            TextDocContent("Hello, Cosmos!"),
            @Suppress("MagicNumber") // 3 is a magic number: youtube.com/watch?v=J8lRKCw2_Pk
            ByteDocContent(WrappedByteArray(byteArrayOf(0, 1, 2, 3))),
            @Suppress("MagicNumber") // but this is just setting up a test input
            ByteDocContent(WrappedByteArray(byteArrayOf(3, 2, 1, 0))),
            ShellCommandDocContent("echo", listOf("Hello,", "World!")),
            ShellCommandDocContent("echo", listOf("Hello,", "Cosmos!")),
        )

        val hashes = contents.map { it.hash() }

        assertEquals(
            contents.size,
            hashes.toSet().size,
            message = "Expected ${contents.size} hashes, got:${
                toStringViaBuilder { sb ->
                    hashes.forEach {
                        sb.append("\n\t")
                        it.appendHex(sb)
                    }
                }
            }",
        )
    }
}
