package lang.temper.fs

import kotlinx.coroutines.DelicateCoroutinesApi
import lang.temper.common.MimeType
import lang.temper.common.RSuccess
import lang.temper.common.assertStructure
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.common.runAsyncTest
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.dirPath
import lang.temper.log.filePath
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.fail

@DelicateCoroutinesApi
class MemoryFileSystemTest {
    @Test
    fun copy() {
        // Prepare.
        val message = "Hello, World!"
        val from = MemoryFileSystem.fromJson(
            """
            {
              foo: {
                bar: {
                  "baz.txt": "$message",
                  "qux.md": "# Xyzzy"
                },
                thud: "Sure"
              }
            }
            """,
        )
        val to = MemoryFileSystem()
        copyRecursive(from = from, to = to)
        // Check.
        val expectedFiles = listOf(
            filePath("foo", "bar", "baz.txt"),
            filePath("foo", "bar", "qux.md"),
            filePath("foo", "thud"),
        )
        expectedFiles.forEach { filePath ->
            assertEquals(FileClassification.File, to.classify(filePath))
            assertEquals(readString(from, filePath), readString(to, filePath))
        }
        // Check one explicitly.
        assertEquals(message, readString(to, filePath("foo", "bar", "baz.txt")))
    }

    @Test
    fun copyFileOverDirFails() {
        val from = MemoryFileSystem.fromJson("""{ a: "b" }""")
        val to = MemoryFileSystem.fromJson("""{ a: {} }""")
        assertFailsWith<IOException> {
            copyRecursive(from = from, to = to)
        }
    }

    @Test
    fun fileClassification() {
        val fs = MemoryFileSystem.fromJson(
            """
            {
              foo: {
                bar: {
                  "baz.txt": "Hello, World!"
                }
              }
            }
            """,
        )

        assertEquals(
            FileClassification.Directory,
            fs.classify(dirPath()),
        )
        assertEquals(
            FileClassification.Directory,
            fs.classify(dirPath("foo")),
        )
        assertEquals(
            FileClassification.Directory,
            fs.classify(dirPath("foo", "bar")),
        )
        assertEquals(
            FileClassification.File,
            fs.classify(filePath("foo", "bar", "baz.txt")),
        )
        assertEquals(
            FileClassification.DoesNotExist,
            fs.classify(filePath("foo", "bar", "boo.txt")),
        )
    }

    @Test
    fun read() {
        val fs = MemoryFileSystem.fromJson(
            """
            {
              foo: {
                bar: {
                  "baz.txt": "Hello, World!"
                }
              }
            }
            """,
        )
        val textFilePath = filePath("foo", "bar", "baz.txt")
        // We can look it up and read the bytes
        val file = fs.lookup(textFilePath)
            as MemoryFileSystem.File
        assertEquals(textFilePath, file.absolutePath)
        assertEquals("Hello, World!", file.textContent)
        assertEquals(
            "Hello, World!",
            fs.textualFileContent(textFilePath).result,
        )
    }

    @Test
    fun readViaSystemAccess() {
        val fs = MemoryFileSystem.fromJson(
            """
            {
              foo: {
                bar: {
                  "baz.txt": "Hello, World!"
                }
              }
            }
            """,
        )
        val cancelGroup = makeCancelGroupForTest()
        val access = fs.systemReadAccess(dirPath("foo"), cancelGroup)
        val reader = access.fileReader(filePath("bar", "baz.txt"))
        val textContent = reader.textContent().await().result
        assertEquals("Hello, World!", textContent)
    }

    @Test
    fun watchTheWatchersWatching() = runAsyncTest { // getChanges is suspendable
        val fs = MemoryFileSystem.fromJson(
            """
                |{
                |  foo: {
                |    bar: {
                |      "baz.txt": "Hello, World!"
                |    }
                |  }
                |}
            """.trimMargin(),
        )
        val watcher =
            fs.createWatchService(dirPath("foo")).result

        // This should contribute a modify event
        (fs.lookup(filePath("foo", "bar", "baz.txt")) as MemoryFileSystem.File)
            .edit("Good night and good luck.".encodeToByteArray())
        // This should contribute a create event
        (fs.lookup(dirPath("foo", "bar")) as MemoryFileSystem.DirectoryOrRoot)
            .mkdir(FilePathSegment("newDir"))
        // This should not contribute an event since it's outside the root
        assertNotNull(fs.root.mkdir(FilePathSegment("boo")))
        // Changes in nested directories also register
        (fs.lookup(dirPath("foo", "bar", "newDir")) as MemoryFileSystem.DirectoryOrRoot)
            .touch(FilePathSegment("newFile"))

        // Check the events
        watcher.flushPendingChanges()
        assertEquals(
            listOf(
                FileChange(
                    filePath("bar", "baz.txt"),
                    FileChange.Kind.Edited,
                ),
                FileChange(
                    dirPath("bar", "newDir"),
                    FileChange.Kind.Created,
                ),
                FileChange(
                    filePath("bar", "newDir", "newFile"),
                    FileChange.Kind.Created,
                ),
            ),
            watcher.getChanges(1000),
        )

        // No new events
        watcher.flushPendingChanges()
        assertEquals(
            emptyList(),
            watcher.getChanges(1000),
        )

        // Delete a directory recursively.
        // We should see deeper deletions first.
        (fs.lookup(dirPath("foo", "bar")) as MemoryFileSystem.DirectoryOrRoot)
            .unlink(FilePathSegment("newDir"))
        watcher.flushPendingChanges()
        assertEquals(
            listOf(
                FileChange(
                    filePath("bar", "newDir", "newFile"),
                    FileChange.Kind.Deleted,
                ),
                FileChange(
                    dirPath("bar", "newDir"),
                    FileChange.Kind.Deleted,
                ),
            ),
            watcher.getChanges(1000),
        )
    }

    @Test
    fun writeStoresMimeType() {
        val cancelGroup = makeCancelGroupForTest()
        val fs = MemoryFileSystem()
        val future = fs.systemAccess(filePath("foo"), cancelGroup)
            .buildFile(filePath("bar", "baz.that"), MimeType("text", "x-that"))
            .write {
                it.write("Hello, World!".encodeToByteArray())
            }

        assertEquals(RSuccess(Unit), future.await())

        assertStructure(
            """
                |{
                |  foo: {
                |    bar: {
                |      "baz.that": {
                |        content: "Hello, World!",
                |        mimeType: "text/x-that",
                |      }
                |    }
                |  }
                |}
            """.trimMargin(),
            fs,
        )
    }

    @Test
    fun multipleRootedViewsOfSameMemoryFs() {
        val rootFs = MemoryFileSystem()
        val subFs = MemoryFileSystem(rootFs, dirPath("foo", "bar"))

        rootFs.write(filePath("baz", "boo", "file0.ext"), "ABC".encodeToByteArray())
        subFs.write(filePath("file1.ext"), "DEF".encodeToByteArray())
        rootFs.write(filePath("foo", "bar", "file2.ext"), "GHI".encodeToByteArray())

        assertStructure(
            """
                |{
                |  root: {
                |    baz: {
                |      boo: {
                |        "file0.ext": { content: "ABC" },
                |      }
                |    },
                |    foo: {
                |      bar: {
                |        "file1.ext": { content: "DEF" },
                |        "file2.ext": { content: "GHI" },
                |      }
                |    },
                |  },
                |  sub: {
                |    "file1.ext": { content: "DEF" },
                |    "file2.ext": { content: "GHI" },
                |  }
                |}
            """.trimMargin(),
            object : Structured {
                override fun destructure(structureSink: StructureSink) =
                    structureSink.obj {
                        key("root") { value(rootFs) }
                        key("sub") { value(subFs) }
                    }
            },
        )
    }
}

private fun readString(from: FileSystem, filePath: FilePath) =
    from.textualFileContent(filePath).result?.toString() ?: fail("Bad read")
