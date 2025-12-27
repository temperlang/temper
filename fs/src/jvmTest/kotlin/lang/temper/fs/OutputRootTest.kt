package lang.temper.fs

import lang.temper.common.MimeType
import lang.temper.common.RSuccess
import lang.temper.common.assertStructure
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.common.currents.newCompletableFuture
import lang.temper.log.FilePathSegment
import lang.temper.log.dirPath
import lang.temper.log.filePath
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OutputRootTest {
    private fun runATempDirectoryTest(
        testName: String,
        body: (root: Path, cancelGroup: CancelGroup, outputRoot: OutputRoot) -> Unit,
    ): Unit = runWithTemporaryDirectory(testName) { root ->
        val ioExceptions = Collections.synchronizedList(mutableListOf<IOException>())
        val outputRoot = OutputRoot(
            RealWritableFileSystem(root) {
                it.printStackTrace()
                ioExceptions.add(it)
            },
        )
        body(root, makeCancelGroupForTest(), outputRoot)
        assertEquals(emptyList(), ioExceptions)
    }

    @Test
    fun buildFileBuildsAFile() = runATempDirectoryTest(
        testName = "buildFileBuildsAFile",
    ) { root, cancelGroup, outputRoot ->
        val dir = outputRoot
            .makeDir(FilePathSegment("foo"))
            .makeDir(FilePathSegment("bar"))

        val future = dir
            .systemAccess(cancelGroup)
            .buildFile(
                filePath("baz", "boo", "far.txt"),
                MimeType("text", "plain"),
            )
            .write { byteSink ->
                byteSink.write("Hello!".toByteArray(Charsets.UTF_8))
            }
        future.await()

        val pathToTextFile = root.resolve(
            listOf("foo", "bar", "baz", "boo", "far.txt")
                .joinToString(root.fileSystem.separator),
        )

        val content = Files.readString(pathToTextFile, Charsets.UTF_8)
        assertEquals("Hello!", content)
    }

    @Test
    fun execChildProcess() = runATempDirectoryTest(
        testName = "execChildProcess",
    ) { _, cancelGroup, outputRoot ->
        val process = outputRoot
            .systemAccess(cancelGroup)
            .buildChildProcess("echo") {
                arg("Hello")
                arg("World")
            }!!
            .executeCapturing()
        val echoResult = process.exitFuture.await()
        assertEquals(
            RSuccess(0 to "Hello World\n"),
            echoResult,
        )
    }

    @Test
    fun childProcessesAreCancellable() = runATempDirectoryTest(
        testName = "childProcessesAreCancellable",
    ) { _, cancelGroup, outputRoot ->
        val process = outputRoot
            .systemAccess(cancelGroup)
            .buildChildProcess("cat") {
                // with no arguments, cat waits for standard input to close.
            }!!
            .execute()
        val exitFuture = process.exitFuture
        assertEquals(false, exitFuture.isDone, "isDone")
        process.cancel(mayInterruptIfRunning = true)
        val catResult = exitFuture.await()
        assertEquals(true, exitFuture.isDone, "isDone")
        assertIs<RSuccess<*, *>>(catResult) // Process shut down cleanly.  No cancellation exn.
    }

    @Test
    fun outOfBandEditDoesNotInvalidateRoot() {
        val cancelGroup = makeCancelGroupForTest()
        val mimeType = MimeType("text", "x-ext")

        val fs = MemoryFileSystem()
        val outputRoot = OutputRoot(fs)

        fun assertLeaves(want: Set<String>) {
            assertEquals(
                want,
                buildSet {
                    outputRoot.leaves().forEach {
                        add("${it.path}")
                    }
                },
            )
        }

        // Create a file via the outputRoot and one via fs.
        val dir = outputRoot.makeDir(FilePathSegment("foo"))
        val f = dir.makeRegularFile(FilePathSegment("bar.ext"), mimeType)
        val fWroteOk = cancelGroup.newCompletableFuture<Boolean, Nothing>("f finished")
        f.supplyTextContent(
            contentSupplier = { it.append("bar content") },
            onCompletion = { ok -> fWroteOk.completeOk(ok) },
        )
        assertEquals(RSuccess(true), fWroteOk.await())
        assertLeaves(setOf("foo/bar.ext"))

        val writeResult = fs.systemAccess(dirPath("baz"), cancelGroup)
            .buildFile(filePath("boo.ext"), mimeType)
            .content("boo content")
            .await()
        assertEquals(RSuccess(Unit), writeResult)
        assertLeaves(setOf("foo/bar.ext", "baz/boo.ext"))

        // Dump the file structure via the OutputRoot and see both files.
        assertStructure(
            """
                |{
                |  foo: {
                |    "bar.ext": {
                |      mimeType: "text/x-ext",
                |      content: "bar content",
                |    },
                |  },
                |  baz: {
                |    "boo.ext": {
                |      mimeType: "text/x-ext",
                |      content: "boo content",
                |    }
                |  }
                |}
            """.trimMargin(),
            outputRoot.fileTreeStructure(),
        )
    }
}
