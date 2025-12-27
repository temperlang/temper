package lang.temper.fs

import lang.temper.common.RFailure
import lang.temper.common.benchmarkIf
import lang.temper.common.console
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.common.runAsyncTest
import lang.temper.log.dirPath
import lang.temper.log.filePath
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

class RealFileSystemTest {

    private val pauseForEffect = 12_000 // ms
    private val pauseForStability = 1_000 // ms

    @Test
    fun testClassify() = runWithTemporaryDirectory("TestClassify") { tmpDir ->
        // TMPDIR/
        //   |
        //   +--- hello/
        //          |
        //          +-- world.txt => "!"

        val helloDir = tmpDir.resolve("hello")
        Files.createDirectory(helloDir)
        val worldFile = helloDir.resolve("world.txt")
        Files.writeString(worldFile, "!", Charsets.UTF_8)

        val fs = RealFileSystem(tmpDir)
        assertEquals(
            FileClassification.Directory,
            fs.classify(dirPath("hello")),
        )
        assertEquals(
            FileClassification.File,
            fs.classify(filePath("hello", "world.txt")),
        )
        assertEquals(
            FileClassification.DoesNotExist,
            fs.classify(filePath("hello", "what.txt")),
        )
        assertEquals(
            FileClassification.DoesNotExist,
            fs.classify(filePath("goodbye", "world.txt")),
        )
    }

    @Test
    fun readAFile() = runWithTemporaryDirectory("ReadAFile") { tmpDir ->
        val file = tmpDir.resolve("foo.txt")
        val textContent = "The quick brown fox jumps over the lazy dog"
        Files.writeString(file, textContent, Charsets.UTF_8)

        val fs = RealFileSystem(tmpDir)
        val got = fs.textualFileContent(filePath("foo.txt"))
        assertEquals(
            textContent,
            got.result,
        )
    }

    @Test
    fun readABinaryFile() = runWithTemporaryDirectory("ReadABinaryFile") { tmpDir ->
        val file = tmpDir.resolve("foo.txt")
        val binaryContent = byteArrayOf(0x01, 0x02, 0x03, 0x42)
        Files.write(file, binaryContent)

        val fs = RealFileSystem(tmpDir)
        val got = fs.readBinaryFileContent(
            filePath("foo.txt"),
        )
        assertEquals(
            binaryContent.contentToString(),
            got.await().result?.copyOf()?.contentToString(),
        )
    }

    @Test
    fun readABinaryFileUsingRoot() = runWithTemporaryDirectory("ReadABinaryFile") { tmpDir ->
        val file = tmpDir.resolve("foo.txt")
        val binaryContent = byteArrayOf(0x01, 0x02, 0x03, 0x42)
        Files.write(file, binaryContent)

        val fs = RealFileSystem(tmpDir, basePath = dirPath("base"))
        val gotOk = fs.readBinaryFileContent(
            filePath("base", "foo.txt"),
        )
        assertEquals(
            binaryContent.contentToString(),
            gotOk.await().result?.copyOf()?.contentToString(),
        )

        // Not readable because no such file
        val gotRootedButBad = fs.readBinaryFileContent(
            filePath("base", "bar.txt"),
        ).await()
        assertIs<RFailure<IOException>>(gotRootedButBad)

        // Not readable because outside root
        val gotUnootedBad = fs.readBinaryFileContent(
            filePath("bus", "bus.txt"),
        ).await()
        assertIs<RFailure<IOException>>(gotUnootedBad)

        Unit
    }

    @Test
    fun readAFileUsingSystemAccess() = runWithTemporaryDirectory("ReadAFile") { tmpDir ->
        val cancelGroup = makeCancelGroupForTest()
        val file = tmpDir.resolve("foo.txt")
        val textContent = "The quick brown fox jumps over the lazy dog"
        Files.writeString(file, textContent, Charsets.UTF_8)

        val fs = RealWritableFileSystem(tmpDir, onIoException = console::error)
        val got = fs.systemReadAccess(filePath(), cancelGroup)
            .fileReader(filePath("foo.txt"))
            .textContent()
            .await()
        assertEquals(
            textContent,
            got.result,
        )
    }

    @Test
    fun watchServiceSeesAll() = runWithTemporaryDirectory("WatchServiceSeesAll") { tmpDir ->
        // TMPDIR/
        //   |
        //   +--- hello/
        //   |      |
        //   |      +-- world.txt => "!"
        //   |
        //   +--- goodbye/
        //          |
        //          +-- death-valley.txt => ":("

        val helloDir = tmpDir.resolve("hello")
        Files.createDirectory(helloDir)
        val worldFile = helloDir.resolve("world.txt")
        Files.writeString(worldFile, "!", Charsets.UTF_8)
        val goodbyeDir = tmpDir.resolve("goodbye")
        Files.createDirectory(goodbyeDir)
        val deathValleyFile = goodbyeDir.resolve("death-valley.txt")
        Files.writeString(deathValleyFile, ":(", Charsets.UTF_8)

        val fs = RealFileSystem(tmpDir)
        val ws = fs.createWatchService(dirPath("hello")).result!!

        runAsyncTest {
            // Before we watch the watch service, there's nothing enqueued
            assertEquals(
                emptyList(),
                console.benchmarkIf(BENCHMARK_WATCH_SERVICE, "initial") {
                    ws.getChanges(timeoutMillis = pauseForStability)
                },
            )

            // Edit world.txt and see that it changed
            checkAndConsumeChanges(
                options = setOf(listOf(FileChange(filePath("hello", "world.txt"), FileChange.Kind.Edited))),
                benchMessage = "edit world",
                watchService = ws,
            ) {
                Files.writeString(worldFile, "?", Charsets.UTF_8)
            }

            // Edit death-valley.txt, but since it's not under the watch root, "hello/",
            // we shouldn't see any edits.
            assertEquals(
                emptyList(),
                console.benchmarkIf(BENCHMARK_WATCH_SERVICE, "edit unwatched") {
                    Files.writeString(deathValleyFile, "8-(", Charsets.UTF_8)
                    ws.getChanges(timeoutMillis = pauseForStability)
                },
            )

            // Delete a file, sort of.
            checkAndConsumeChanges(
                options = setOf(listOf(FileChange(filePath("hello", "world.txt"), FileChange.Kind.Deleted))),
                benchMessage = "move file to unwatched",
                watchService = ws,
            ) {
                Files.move(worldFile, goodbyeDir.resolve("world.txt"))
            }

            // Create a file
            checkAndConsumeChanges(
                options = setOf(
                    listOf(
                        FileChange(filePath("hello", "hilo.hi"), FileChange.Kind.Created),
                    ),
                    // On Ubuntu, creation and edit are contributed separately.
                    listOf(
                        FileChange(filePath("hello", "hilo.hi"), FileChange.Kind.Created),
                        FileChange(filePath("hello", "hilo.hi"), FileChange.Kind.Edited),
                    ),
                ),
                stragglers = setOf(listOf(FileChange(filePath("hello", "hilo.hi"), FileChange.Kind.Edited))),
                benchMessage = "create a file",
                watchService = ws,
            ) {
                val hilo = helloDir.resolve("hilo.hi")
                Files.writeString(hilo, ":-)", Charsets.UTF_8)
            }
        }
    }

    private suspend fun checkAndConsumeChanges(
        benchMessage: String,
        options: Iterable<List<FileChange>>,
        watchService: FileWatchService,
        stragglers: Iterable<List<FileChange>>? = null,
        action: () -> Unit,
    ) {
        val changes = console.benchmarkIf(BENCHMARK_WATCH_SERVICE, benchMessage) {
            action()
            watchService.getChanges(timeoutMillis = pauseForEffect)
        }
        assertContains(options, changes)
        // In case we see repeat events, consume again as needed.
        repeat(2) {
            val extra = console.benchmarkIf(BENCHMARK_WATCH_SERVICE, "wait for stability") {
                watchService.getChanges(timeoutMillis = pauseForStability)
            }
            if (extra == emptyList<List<FileChange>>()) {
                return@checkAndConsumeChanges
            }
            // But any extras should be a repeat of expected.
            assertContains(stragglers ?: options, extra)
        }
        fail("too many stragglers")
    }
}

private const val BENCHMARK_WATCH_SERVICE = false
