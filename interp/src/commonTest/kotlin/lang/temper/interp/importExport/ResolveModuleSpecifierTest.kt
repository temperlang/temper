package lang.temper.interp.importExport

import lang.temper.common.ListBackedLogSink
import lang.temper.common.testCodeLocation
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveModuleSpecifierTest {
    private fun assertResolution(
        moduleSpecifier: String,
        basePath: FilePath,
        wanted: FilePath?,
        errorsWanted: List<String> = emptyList(),
    ) {
        val logSink = ListBackedLogSink()

        val got = resolveModuleSpecifier(
            moduleSpecifier = moduleSpecifier,
            basePath = basePath,
            logSink = logSink,
            pos = Position(testCodeLocation, 0, 0),
        )

        assertEquals(wanted, got, message = moduleSpecifier)
        val errorsGotten = logSink.allEntries.map { it.messageText }
        assertEquals(errorsWanted, errorsGotten, message = moduleSpecifier)
    }

    @Test
    fun relativeFileAgainstDir() = assertResolution(
        "./foo",
        basePath = dirPath("my-library"),
        wanted = filePath("my-library", "foo"),
    )

    @Test
    fun relativeFileAgainstFile() = assertResolution(
        "./foo",
        basePath = filePath("my-library", "bar"),
        wanted = filePath("my-library", "foo"),
    )

    @Test
    fun relativeFileWithDir() = assertResolution(
        "./src/foo",
        basePath = filePath("my-library", "bar"),
        wanted = filePath("my-library", "src", "foo"),
    )

    @Test
    fun relativeFileWithDots() = assertResolution(
        "././bar/../foo",
        basePath = filePath("my-library", "bar"),
        wanted = filePath("my-library", "foo"),
    )

    @Test
    fun otherDirSameLib() = assertResolution(
        "../dir2/foo",
        basePath = filePath("my-library", "dir1", "file"),
        wanted = filePath("my-library", "dir2", "foo"),
    )

    @Test
    fun relativeInOtherLibrary() = assertResolution(
        "../../other-library/foo",
        basePath = filePath("my-library", "src", "file"),
        wanted = filePath("other-library", "foo"),
    )

    @Test
    fun absoluteInOtherLibrary() = assertResolution(
        "/other-library/foo",
        basePath = filePath("my-library", "src", "file"),
        wanted = filePath("other-library", "foo"),
    )

    @Test
    fun outsideAnyLibrary() = assertResolution(
        "/etc/passwd",
        basePath = filePath("my-library", "src", "file"),
        // This is a known weakness that is addressed at other layers.
        // For performance reasons, our file system abstraction stitch together file systems,
        // chroot style, so files under `/etc/` shouldn't be mentionable unless the compiler
        // is run with `/` or `/etc/` as the working directory.
        // Also, the build conductor checks that resolved module paths are under a library root.
        // Either of these is sufficient to prevent exfiltration of system-wide secrets as long as
        // devs run tools from inside `$HOME`.
        wanted = filePath("etc", "passwd"),
    )

    @Test
    fun lotsOfDots() = assertResolution(
        "../../other-library/../../etc/passwd",
        basePath = filePath("my-library", "src", "file"),
        wanted = null,
        errorsWanted = listOf(
            "Import path has too many \"..\" path segments: ${
                ""
            }my-library/src/../../other-library/../../etc/passwd!",
        ),
    )

    @Test
    fun dotDotDoesNotEatDot() = assertResolution(
        "./../foo",
        basePath = filePath("my-library", "src", "file"),
        wanted = filePath("my-library", "foo"),
    )

    @Test
    fun specificationThatEndsWithSlashIsADirectory() = assertResolution(
        "./foo/",
        basePath = filePath("my-library", "src", "file"),
        wanted = dirPath("my-library", "src", "foo"),
    )
}
