package lang.temper.fs

import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class FilteringFileSystemSnapshotTest {
    @Test
    fun snapshottingAMemoryFs() {
        val fs = MemoryFileSystem.fromJson(
            """
                |{
                |  "config.temper.md": ```
                |    # Hello World
                |    ```,
                |  "src": {
                |    "impl.temper": ```
                |      console.log("Hello, Wrold!");
                |      ```
                |  },
                |  "temper.out": {
                |    "some-lang": {
                |      "library.some-lang": ```
                |        CODE CODE CODEY CODE CODE
                |        ```
                |    },
                |  }
                |}
            """.trimMargin(),
        )

        val snapshot = FilteringFileSystemSnapshot(
            fs,
            fileFilterRulesFromIgnoreFile(
                """
                    |temper.out
                """.trimMargin(),
            ).result!!,
        )

        // We can read directories.
        val rootDir = snapshot[FilePath.emptyPath]
        assertEquals(
            listOf(
                filePath("config.temper.md"),
                dirPath("src"),
                // temper.out is not here because of the ignore rules
            ),
            (rootDir as? FileSnapshot.Dir)?.names,
        )
        val srcDir = snapshot[dirPath("src")]
        assertEquals(
            listOf(
                filePath("src", "impl.temper"),
            ),
            (srcDir as? FileSnapshot.Dir)?.names,
        )

        // Getting temper.out/some-lang/library.some-lang doesn't work
        val someLangFileSnapshot = snapshot[
            filePath("temper.out", "some-lang", "library.some-lang"),
        ]
        assertIs<FileSnapshot.NoSuchFile>(someLangFileSnapshot)

        // Reading files gets us the content.
        val srcImplSnapshot1 = snapshot[filePath("src", "impl.temper")]
        assertEquals(
            """
                |console.log("Hello, Wrold!");
            """.trimMargin(),
            (srcImplSnapshot1 as? FileSnapshot.UpToDateFile)
                ?.content?.decodeToString(),
            message = "$srcImplSnapshot1",
        )
        val configFileSnapshot1 = snapshot[filePath("config.temper.md")]
        assertEquals(
            """
                |# Hello World
            """.trimMargin(),
            (configFileSnapshot1 as? FileSnapshot.UpToDateFile)
                ?.content?.decodeToString(),
            message = "$configFileSnapshot1",
        )

        // We can change a file's content, clear the cache, and reread it.
        // Version skew is reflected in the choice of file snapshot.
        fs.write(
            filePath("src", "impl.temper"),
            "console.log(\"Hello, World!\");".toByteArray(),
        )
        snapshot.clearCacheForUnitTest()

        val srcImplSnapshot2 = snapshot[filePath("src", "impl.temper")]
        assertEquals(
            """
                |console.log("Hello, World!");
            """.trimMargin(),
            (srcImplSnapshot2 as? FileSnapshot.SkewedFile)
                ?.content?.decodeToString(),
            message = "$srcImplSnapshot2",
        )
        // The two files agree on the original hash.
        assertEquals(
            (srcImplSnapshot1 as? FileSnapshot.AvailableFile)?.contentHash,
            (srcImplSnapshot2 as? FileSnapshot.AvailableFile)?.snapshotHash,
        )
        // But they disagree on the content hash.
        assertNotEquals(
            (srcImplSnapshot1 as? FileSnapshot.AvailableFile)?.contentHash,
            (srcImplSnapshot2 as? FileSnapshot.AvailableFile)?.contentHash,
        )
        val configFileSnapshot2 = snapshot[filePath("config.temper.md")]
        assertEquals(
            """
                |# Hello World
            """.trimMargin(),
            (configFileSnapshot2 as? FileSnapshot.UpToDateFile)
                ?.content?.decodeToString(),
            message = "$configFileSnapshot2",
        )

        // If we delete a file it's unavailable but still listed.
        fs.deleteDirRecursively(dirPath("src"))
        snapshot.clearCacheForUnitTest()

        val srcImplSnapshot3 = snapshot[filePath("src", "impl.temper")]
        assertIs<FileSnapshot.UnavailableFile>(srcImplSnapshot3)
        assertEquals(
            listOf(
                filePath("src", "impl.temper"),
            ),
            (srcDir as? FileSnapshot.Dir)?.names,
        )
    }
}
