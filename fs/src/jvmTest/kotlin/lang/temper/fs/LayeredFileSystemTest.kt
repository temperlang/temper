package lang.temper.fs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import lang.temper.common.RSuccess
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class LayeredFileSystemTest {
    private val front = MemoryFileSystem.fromJson(
        """
            |{
            |  foo: {
            |    bar: {
            |      "different.txt": "¡Hola, Mundo!",
            |      "front-only.txt": "Front only"
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    private val back = MemoryFileSystem.fromJson(
        """
            |{
            |  foo: {
            |    bar: {
            |      "different.txt": "Hello, World!",
            |      "back-only.txt": "Back only"
            |    },
            |    "also-back-only.txt": "Also back only"
            |  }
            |}
        """.trimMargin(),
    )

    private val fs = LayeredFileSystem(front, back)

    private val backOnly = filePath("foo", "bar", "back-only.txt")
    private val different = filePath("foo", "bar", "different.txt")
    private val frontOnly = filePath("foo", "bar", "front-only.txt")
    private val new = filePath("new.txt")

    @Test
    fun classify() {
        assertEquals(FileClassification.Directory, fs.classify(dirPath("foo")))
        assertEquals(FileClassification.Directory, fs.classify(dirPath("foo", "bar")))
        assertEquals(FileClassification.File, fs.classify(different))
        assertEquals(FileClassification.File, fs.classify(frontOnly))
        assertEquals(FileClassification.File, fs.classify(backOnly))
        assertEquals(FileClassification.File, fs.classify(filePath("foo", "also-back-only.txt")))
        assertEquals(FileClassification.DoesNotExist, fs.classify(filePath("bar", "also-back-only.txt")))
        assertEquals(FileClassification.DoesNotExist, fs.classify(filePath("foo", "bar", "missing.txt")))
    }

    @Test
    fun readBack() {
        assertEquals(RSuccess("Back only"), fs.textualFileContent(filePath("foo", "bar", "back-only.txt")))
    }

    @Test
    fun readLayered() {
        assertEquals(RSuccess("¡Hola, Mundo!"), fs.textualFileContent(different))
        assertEquals(RSuccess("¡Hola, Mundo!"), front.textualFileContent(different))
        assertEquals(RSuccess("Hello, World!"), back.textualFileContent(different))
    }

    @Test
    fun readAfterFrontRemoved() {
        assertEquals("¡Hola, Mundo!", fs.textualFileContent(different).result)
        front.unlink(different)
        assertEquals("Hello, World!", fs.textualFileContent(different).result)
    }

    @Test
    fun watchChangeDupeBackAsEdit() {
        watchChangeAsEdit(path = different, kind = FileChange.Kind.Deleted, fileSystem = back)
        watchChangeAsEdit(path = frontOnly, kind = FileChange.Kind.Created, fileSystem = back)
    }

    @Test
    fun watchChangeDupeFrontAsEdit() {
        watchChangeAsEdit(path = different, kind = FileChange.Kind.Deleted, fileSystem = front)
        watchChangeAsEdit(path = backOnly, kind = FileChange.Kind.Created, fileSystem = front)
    }

    @Test
    fun watchChangeLoneBackAsChange() {
        watchChangeAsChange(path = backOnly, kind = FileChange.Kind.Deleted, fileSystem = back)
        watchChangeAsChange(path = new, kind = FileChange.Kind.Created, fileSystem = back)
    }

    @Test
    fun watchChangeLoneFrontAsChange() {
        watchChangeAsChange(path = frontOnly, kind = FileChange.Kind.Deleted, fileSystem = front)
        watchChangeAsChange(path = new, kind = FileChange.Kind.Created, fileSystem = front)
    }

    @Ignore("These easily turn up as edit events, which might be ok, so reconsider what this should do and how.")
    @Test
    fun watchCreateBothAsCreate() = watchChangeBothAsChange(path = new, kind = FileChange.Kind.Created)

    @Ignore("Failing in CI, so keep things clean there until we resolve it.")
    @Test
    fun watchDeleteBothAsDelete() = watchChangeBothAsChange(path = different, kind = FileChange.Kind.Deleted)

    @Test
    fun watchDeleteDupeBackAsEdit() =
        watchChangeAsEdit(path = different, kind = FileChange.Kind.Deleted, fileSystem = back)

    @Test
    fun watchDeleteDupeFrontAsEdit() =
        watchChangeAsEdit(path = different, kind = FileChange.Kind.Deleted, fileSystem = front)

    @Test
    fun watchWaitBoth() = fs.rootWatch().use { watch ->
        val changes = watchWait(watch = watch) {
            // Change both sides at once to make sure they don't get lost.
            front.unlink(frontOnly)
            front.flushPendingChanges()
            back.unlink(backOnly)
            back.flushPendingChanges()
        }
        val frontChange = FileChange(filePath = frontOnly, fileChangeKind = FileChange.Kind.Deleted)
        // Check as sets because we don't care much about the order here.
        // TODO Sometimes fails, getting only one of the changes.
        assertEquals(setOf(frontChange, frontChange.copy(filePath = backOnly)), changes.toSet())
    }

    @Test
    fun watchWaitDupes() = fs.rootWatch().use { watch ->
        val changes = watchWait(watch = watch) {
            // Map events where we have duplicates across layers.
            front.unlink(different)
            front.flushPendingChanges()
            back.touch(frontOnly)
            back.flushPendingChanges()
        }
        val expected = setOf(
            FileChange(filePath = different, fileChangeKind = FileChange.Kind.Edited),
            FileChange(filePath = frontOnly, fileChangeKind = FileChange.Kind.Edited),
        )
        assertEquals(expected, changes.toSet())
    }

    @Test
    fun watchWaitSequential() = fs.rootWatch().use { watch ->
        // Front side.
        val changesFront = watchWait(watch = watch) {
            // Change only one side to make sure we still get just these.
            front.unlink(frontOnly)
            front.flushPendingChanges()
        }
        val frontChange = FileChange(filePath = frontOnly, fileChangeKind = FileChange.Kind.Deleted)
        assertEquals(setOf(frontChange), changesFront.toSet())
        // Back side.
        val changesBack = watchWait(watch = watch) {
            // Now change the other side to make sure we get these, too.
            back.unlink(backOnly)
            back.flushPendingChanges()
        }
        assertEquals(setOf(frontChange.copy(filePath = backOnly)), changesBack.toSet())
    }

    @Test
    fun watchWaitSequentialFront() = fs.rootWatch().use { watch ->
        // First round.
        val changesA = watchWait(watch = watch) {
            front.unlink(frontOnly)
            front.flushPendingChanges()
        }
        assertEquals(listOf(FileChange(filePath = frontOnly, fileChangeKind = FileChange.Kind.Deleted)), changesA)
        // Make more changes in the same layer, to make sure they get through.
        val changesB = watchWait(watch = watch) {
            front.touch(new)
            (front.lookup(different) as MemoryFileSystem.File).edit("Hi!".toByteArray())
            front.flushPendingChanges()
        }
        val expected = setOf(
            FileChange(filePath = new, fileChangeKind = FileChange.Kind.Created),
            FileChange(filePath = different, fileChangeKind = FileChange.Kind.Edited),
        )
        assertEquals(expected, changesB.toSet())
    }

    private fun watchChange(
        path: FilePath,
        kind: FileChange.Kind,
        fileSystems: List<MemoryFileSystem>,
    ): List<FileChange> = fs.rootWatch().let { watch ->
        // Call `let` above rather than `use`, so empty lists at close time don't confuse this simple test.
        for (fs in fileSystems) {
            when (kind) {
                FileChange.Kind.Created -> {
                    assertEquals(FileClassification.DoesNotExist, fs.classify(path))
                    fs.touch(path)
                }
                FileChange.Kind.Deleted -> fs.unlink(path)
                else -> Unit
            }
            fs.flushPendingChanges()
        }
        // Give it a couple of tries in case there are stragglers.
        return@watchChange runBlocking { watch.getChanges() + watch.getChanges(timeoutMillis = 1000) }
    }

    private fun watchChangeAsChange(path: FilePath, kind: FileChange.Kind, fileSystem: MemoryFileSystem) {
        val changes = watchChange(path = path, kind = kind, fileSystems = listOf(fileSystem))
        assertEquals(listOf(FileChange(filePath = path, fileChangeKind = kind)), changes)
    }

    private fun watchChangeAsEdit(path: FilePath, kind: FileChange.Kind, fileSystem: MemoryFileSystem) {
        val changes = watchChange(path = path, kind = kind, fileSystems = listOf(fileSystem))
        assertEquals(listOf(FileChange(filePath = path, fileChangeKind = FileChange.Kind.Edited)), changes)
    }

    private fun watchChangeBothAsChange(path: FilePath, kind: FileChange.Kind) {
        val changes = watchChange(path = path, kind = kind, fileSystems = listOf(front, back))
        val change = FileChange(filePath = path, fileChangeKind = kind)
        // We get this twice at the moment, and maybe that's ok.
        assertEquals(listOf(change, change), changes)
    }

    private fun watchWait(watch: FileWatchService, action: () -> Unit): List<FileChange> {
        val coroutineScope = CoroutineScope(Dispatchers.Default)
        // Front side.
        val deferredChanges = coroutineScope.async {
            // Make sure the changes are pending rather than already available.
            // Except when running poll, sometimes this makes poll work on next call.
            // So keep this commented out. Uncomment for manual testing as needed.
            // assertEquals(listOf(), watch.pollChanges())
            watch.getChanges()
        }
        deferredChanges.start()
        runBlocking(coroutineScope.coroutineContext) {
            // Run this in the same context in case it helps to come after `getChanges` above.
            action()
        }
        return runBlocking(coroutineScope.coroutineContext) {
            var results = deferredChanges.await()
            // Also catch any stragglers. Might matter. And use delay then poll just to exercise poll.
            delay(100)
            results = results + watch.pollChanges()
            results
        }
    }
}

private fun MemoryFileSystem.getDir(path: FilePath) = lookup(path) as MemoryFileSystem.DirectoryOrRoot

private fun MemoryFileSystem.touch(path: FilePath) = getDir(path.dirName()).touch(path.lastOrNull()!!)

fun LayeredFileSystem.rootWatch() = createWatchService(dirPath()).result!!
