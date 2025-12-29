package lang.temper.fs

import lang.temper.common.RSuccess
import lang.temper.common.runAsyncTest
import lang.temper.common.toStringViaBuilder
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.dirPath
import lang.temper.log.filePath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class StitchedFileSystemTest {
    @Test
    fun testTwoFilesCreated() = runWithTemporaryDirectory("TestTwoFilesCreatedTempDir1") { tempDir1 ->
        runWithTemporaryDirectory("TestTwoFilesCreatedTempDir2") { tempDir2 ->
            runWithTemporaryDirectory("TestTwoFilesCreatedTempDir3") { tempDir3 ->
                // Stitch together three temporary directories.
                val stitchedFileSystem = StitchedFileSystem(
                    mapOf(
                        dirPath("foo", "bar", "one") to RealFileSystem(tempDir1),
                        dirPath("baz", "two") to RealFileSystem(tempDir2),
                        dirPath("three") to RealFileSystem(tempDir3),
                    ),
                )

                // Construct a structure like
                //     foo/
                //         bar/
                //             one/
                //                 1.txt
                //     baz/
                //         two/
                //             2.txt
                //     three/
                //           files/
                //              3.txt
                //              3.0.txt
                //              3/
                //           3.i
                // which corresponds to
                //     $tempDir1/
                //         1.txt
                //     $tempDir2/
                //         2.txt
                //     $tempDir3/
                //         files/
                //              3.txt
                //              3.0.txt
                //              3/
                //           3.i

                // But first, create two watchers.
                // 1. One that watches /
                // 2. Another that watches three/files/
                val rootWatcher = stitchedFileSystem.createWatchService(
                    dirPath(),
                ).result!!
                assertNotNull(rootWatcher)

                Files.createDirectory(tempDir3.resolve("files"))
                val threeFilesWatcher = stitchedFileSystem.createWatchService(
                    dirPath("three", "files"),
                ).result!!
                assertNotNull(threeFilesWatcher)

                for (
                (d, path) in listOf(
                    tempDir1 to filePath("1.txt"),
                    tempDir2 to filePath("2.txt"),
                    tempDir3 to filePath("files", "3.txt"),
                    tempDir3 to filePath("files", "3.0.txt"),
                    tempDir3 to dirPath("files", "3"),
                    tempDir3 to filePath("3.i"),
                )
                ) {
                    if (path.isDir) {
                        Files.createDirectory(d.resolve(path.join(d.fileSystem.separator)))
                    } else {
                        // This causes only create events on Linux and Mac but also causes edit events on Windows.
                        Files.writeString(d.resolve(path.join(d.fileSystem.separator)), "", Charsets.UTF_8)
                        // This version doesn't create edit events for the files on Windows, but we still get an edit
                        // event for the `files` dir because *its* parent is being watched when its contents change.
                        // Files.createFile(d.resolve(path.toString()))
                    }
                }

                // Build a tree structure
                val filePathsFound = mutableSetOf<FilePath>()
                fun walk(filePath: FilePath) {
                    when (stitchedFileSystem.classify(filePath)) {
                        FileClassification.DoesNotExist -> fail("$filePath")
                        FileClassification.File -> {
                            assertFalse(filePath.isDir, "$filePath")
                            filePathsFound.add(filePath)
                        }
                        FileClassification.Directory -> {
                            assertTrue(filePath.isDir, "$filePath")
                            filePathsFound.add(filePath)
                            for (f in stitchedFileSystem.directoryListing(filePath).result!!) {
                                walk(f)
                            }
                        }
                    }
                }
                walk(dirPath())
                assertEquals(
                    """
                    |/
                    |baz/
                    |baz/two/
                    |baz/two/2.txt
                    |foo/
                    |foo/bar/
                    |foo/bar/one/
                    |foo/bar/one/1.txt
                    |three/
                    |three/3.i
                    |three/files/
                    |three/files/3/
                    |three/files/3.0.txt
                    |three/files/3.txt
                    """.trimMargin(),
                    filePathsFound.sorted().joinToString("\n"),
                )

                runAsyncTest {
                    // Make sure the watchers translate things properly.
                    suspend fun wantWatched(
                        watchService: FileWatchService,
                        want: String,
                    ) {
                        val allChanges = mutableListOf<FileChange>()
                        while (true) {
                            val got = watchService.getChanges(500) // ms
                            if (got.isEmpty()) {
                                break
                            }
                            allChanges.addAll(got)
                        }
                        val got = allChanges.toSet()
                            .sortedBy { it.filePath }
                            // Unlike Linux and Mac, Windows causes edit events for watched files here, but that
                            // doesn't impact our use cases, so ignore edit events here.
                            .filter { it.fileChangeKind != FileChange.Kind.Edited }
                            .joinToString("\n") { fc ->
                                toStringViaBuilder { sb ->
                                    sb.append(
                                        when (fc.fileChangeKind) {
                                            FileChange.Kind.Created -> "+"
                                            FileChange.Kind.Edited -> "~"
                                            FileChange.Kind.Deleted -> "-"
                                        },
                                    )
                                    sb.append(fc.filePath)
                                }
                            }
                        assertEquals(
                            want,
                            got,
                        )
                    }

                    wantWatched(
                        rootWatcher,
                        """
                        |+baz/two/2.txt
                        |+foo/bar/one/1.txt
                        |+three/3.i
                        |+three/files/
                        |+three/files/3/
                        |+three/files/3.0.txt
                        |+three/files/3.txt
                        """.trimMargin(),
                    )
                    wantWatched(
                        threeFilesWatcher,
                        """
                        |+three/files/3/
                        |+three/files/3.0.txt
                        |+three/files/3.txt
                        """.trimMargin(),
                    )
                }

                stitchedFileSystem.close()
            }
        }
    }

    @Test
    fun readAsyncCompletes() {
        val a = MemoryFileSystem.fromJson(
            """
                |{
                |  exists.txt: "Sum ergo sum"
                |}
            """.trimMargin(),
        )
        val b = MemoryFileSystem.fromJson(
            """
                |{
                |  exists.txt: "Quoque sum"
                |}
            """.trimMargin(),
        )

        val stitchedFs = StitchedFileSystem(
            mapOf(
                dirPath("a") to a,
                dirPath("b") to b,
            ),
        )

        val aFuture = stitchedFs.readBinaryFileContent(filePath("a", "exists.txt")).then("a") {
            lang.temper.common.console.log("a got $it")
            RSuccess(it.result != null)
        }
        val bFuture = stitchedFs.readBinaryFileContent(filePath("b", "exists.txt")).then("b") {
            lang.temper.common.console.log("b got $it")
            RSuccess(it.result != null)
        }
        val bBadFuture = stitchedFs.readBinaryFileContent(filePath("b", "does-not-exist.txt")).then("b-bad") {
            lang.temper.common.console.log("b-bad got $it")
            RSuccess(it.result != null)
        }
        val cBadFuture = stitchedFs.readBinaryFileContent(filePath("c", "does-not-exist.txt")).then("c-bad") {
            lang.temper.common.console.log("c-bad got $it")
            RSuccess(it.result != null)
        }

        assertEquals(true, aFuture.await().result, "a")
        assertEquals(true, bFuture.await().result, "b")
        assertEquals(false, bBadFuture.await().result, "b-bad")
        assertEquals(false, cBadFuture.await().result, "c-bad")
    }
}
