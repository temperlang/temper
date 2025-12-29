package lang.temper.frontend.staging

import lang.temper.common.ListBackedLogSink
import lang.temper.common.assertStructure
import lang.temper.common.console
import lang.temper.frontend.Module
import lang.temper.fs.FileFilterRules
import lang.temper.fs.FilteringFileSystemSnapshot
import lang.temper.fs.MemoryFileSystem
import lang.temper.library.AbstractLibraryConfigurations
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.dirPath
import lang.temper.name.DashedIdentifier
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

// In the body block of the test cases below, you can write to the file tree via:
//
//     writeAll("""{ path: { to: { file.temper: "file content" } } }""")
//
// You can also edit one file with
//
//     "path/to/file.temper" write "new file content"
//
// And then you can force a rebuild and inspect the relationship between
// it and any prior build with:
//
//     assertSourcePartition("""...""")
//
// This works because all of those verbs are methods on the test harness
// class at the bottom of this file.  See it for more affordances.

class SourceFilePartitionTest {
    private fun assertSourceFilePartitions(
        body: SourceFilePartitionTestHarness.() -> Unit,
    ) {
        SourceFilePartitionTestHarness().body()
    }

    @Test
    fun noFiles() = assertSourceFilePartitions {
        assertSourcePartition(
            """
                |{
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun newFiles() = assertSourceFilePartitions {
        writeAll(
            """
                |{
                |  work: {
                |    src: {
                |      foo.temper: "export let x = 1;"
                |    }
                |  }
                |}
            """.trimMargin(),
        )
        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/"],
                |  newModules:      ["work//src/"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun noChangeNothingToRebuild() = assertSourceFilePartitions {
        writeAll(
            """
                |{
                |  work: {
                |    src: {
                |      foo.temper: "export let x = 1;"
                |    }
                |  }
                |}
            """.trimMargin(),
        )
        assertSourcePartition(
            """{ newLibraryRoots: ["work/"], newModules: ["work//src/"] }""",
        )
        // This line left intentionally blank of FS changes
        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  reusedModules:      ["work//src/"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun rebuildDueToChange() = assertSourceFilePartitions {
        writeAll(
            """
                |{
                |  work: {
                |    src: {
                |      foo.temper: "export let x = 1;"
                |    }
                |  }
                |}
            """.trimMargin(),
        )
        assertSourcePartition(
            """{ newLibraryRoots: ["work/"], newModules: ["work//src/"] }""",
        )
        "work/src/foo.temper" write "export let x = 2;"
        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  dirtyModules:       ["work//src/"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun rebuildTwoBasedOnDependencyChange() = assertSourceFilePartitions {
        // c depends on b depends on a.  d depends on nothing.
        // We make these edits in sequence:
        // - changing 'a' requires rebuild of ('a', 'b', 'c')
        // - changing 'b' requires rebuild of ('b', 'c')
        // - changing 'd' requires rebuild of ('d')
        // - changing nothing requires no further rebuild
        writeAll(
            """
                |{
                |  work: {
                |    a: {
                |      a.temper: ```
                |        export let a = 1;
                |        ```,
                |    },
                |    b: {
                |      b.temper: ```
                |        let { a } = import("../a");
                |        export let b = a + 1;
                |        ```,
                |    },
                |    c: {
                |      c.temper: ```
                |        let { b } = import("../b");
                |        export let c = b * 2;
                |        ```,
                |    },
                |    d: {
                |      d.temper: ```
                |        export let d = 0;
                |        ```,
                |    },
                |  }
                |}
            """.trimMargin(),
        )
        assertSourcePartition(
            """{ newLibraryRoots: ["work/"], newModules: ["work//a/", "work//b/", "work//c/", "work//d/"] }""",
        )

        "work/a/a.temper" write "export let a = 2;"

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  dirtyModules:       ["work//a/", "work//b/", "work//c/"],
                |  reusedModules:      ["work//d/"],
                |}
            """.trimMargin(),
        )

        "work/b/b.temper" write """
            |let { a } = import("../a");
            |export let b = a - 1;
        """.trimMargin()

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  dirtyModules:       ["work//b/", "work//c/"],
                |  reusedModules:      ["work//a/", "work//d/"],
                |}
            """.trimMargin(),
        )

        "work/d/d.temper" write "export let d = 123;"

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  dirtyModules:       ["work//d/"],
                |  reusedModules:      ["work//a/", "work//b/", "work//c/"],
                |}
            """.trimMargin(),
        )

        // Nothing changed

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  dirtyModules:       [],
                |  reusedModules:      ["work//a/", "work//b/", "work//c/", "work//d/"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun creatingFileRebuildsFileWithBrokenImport() = assertSourceFilePartitions {
        "work/i/importer.temper" write """let { x } = import("../e")"""

        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/"],
                |  newModules:      ["work//i/"],
                |  errors: [
                |    "Import of file:work/e failed!",
                |  ]
                |}
            """.trimMargin(),
        )

        // Does not export x yet
        "work/e/exporter.temper" write """
            |export let y = 1;
        """.trimMargin()

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  newModules:         ["work//e/"],
                |  dirtyModules:       ["work//i/"],
                |  errors: [
                |    "work//e/ does not export symbol x!",
                |  ]
                |}
            """.trimMargin(),
        )

        // Third time lucky
        "work/e/exporter.temper" write """
            |export let x = 1;
        """.trimMargin()

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  dirtyModules:       ["work//e/", "work//i/"],
                |}
            """.trimMargin(),
        )

        // Nothing changed.  See if it settles down.

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  reusedModules:      ["work//e/", "work//i/"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun fileSwapLeadsToRebuild() = assertSourceFilePartitions {
        // Swapping files swaps their hashes, but since our tree merging depends on the order
        // of ModuleSources we need to take that into account.
        val content1 = "console.log('Hello')"
        val content2 = "console.log('World')"

        "work/foo.temper" write content2
        "work/bar.temper" write content1

        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/"],
                |  newModules:      ["work//"],
                |}
            """.trimMargin(),
        )

        "work/foo.temper" write content1
        "work/bar.temper" write content2

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  dirtyModules:       ["work//"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun dirRenamedLeadsToRebuild() = assertSourceFilePartitions {
        // QNames matter, so we need to rebuild when something happens that affect those.
        "work/foo/a.temper" write "let a = 1;"
        "work/foo/b.temper" write "let b = 1;"

        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/"],
                |  newModules: ["work//foo/"],
                |}
            """.trimMargin(),
        )

        rm("work/foo/a.temper")
        rm("work/foo/b.temper")
        "work/bar/a.temper" write "let a = 1;"
        "work/bar/b.temper" write "let b = 1;"

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  newModules: ["work//bar/"],
                |  droppedModules: ["work//foo/"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun fileAdditionLeadsToRebuild() = assertSourceFilePartitions {
        "work/foo/a.temper" write "let a = 1;"

        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/"],
                |  newModules: ["work//foo/"],
                |}
            """.trimMargin(),
        )

        "work/foo/b.temper" write "let b = 1;"

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  dirtyModules: ["work//foo/"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun fileDeletionLeadsToRebuild() = assertSourceFilePartitions {
        "work/foo/a.temper" write "let a = 1;"
        "work/foo/b.temper" write "let a = 1;"

        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/"],
                |  newModules: ["work//foo/"],
                |}
            """.trimMargin(),
        )

        rm("work/foo/b.temper")

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  dirtyModules: ["work//foo/"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun mergingLibrary() = assertSourceFilePartitions {
        // Deleting a config.temper.md re-incorporates its source files
        // into any parent configuration.
        writeAll(
            """
                |{
                |  work: {
                |    config.temper.md: "# Top",
                |    foo.temper: "export let foo = 1;",
                |    dir: {
                |      config.temper.md: "# Sub dir library",
                |      bar.temper: "export let bar = 2;",
                |    }
                |  }
                |}
            """.trimMargin(),
        )

        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/",
                |                    "work/dir/"],
                |  newModules:      ["work//",     "work//config.temper.md",
                |                    "work/dir//", "work/dir//config.temper.md"],
                |}
            """.trimMargin(),
        )
        // Correctly figured out the library name from the config file header
        assertConfiguration("work/") {
            assertEquals(DashedIdentifier("top"), it.libraryName)
        }
        assertConfiguration("work/dir/") {
            assertEquals(DashedIdentifier("sub-dir-library"), it.libraryName)
        }

        rm("work/dir/config.temper.md")

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots:  ["work/"],
                |  droppedLibraryRoots: ["work/dir/"],
                |  newModules:          ["work//dir/"],
                |  droppedModules:      ["work/dir//",
                |                        "work/dir//config.temper.md"],
                |  reusedModules:       ["work//",
                |                        "work//config.temper.md"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun splittingLibrary() = assertSourceFilePartitions {
        // Adding a config.temper.md may remove some modules from a parent library
        writeAll(
            """
                |{
                |  work: {
                |    config.temper.md: "# Top",
                |    a.temper: "export let a = 1;",
                |    dir: {
                |      // NO CONFIG HERE YET
                |      b.temper: "export let b = 2;",
                |    },
                |  }
                |}
            """.trimMargin(),
        )

        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/"],
                |  newModules: ["work//", "work//config.temper.md", "work//dir/"],
                |}
            """.trimMargin(),
        )

        "work/dir/config.temper.md" write "# Nested"

        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/dir/"],
                |  reusedLibraryRoots: ["work/"],
                |  reusedModules: ["work//", "work//config.temper.md"],
                |  droppedModules: ["work//dir/"],
                |  newModules: ["work/dir//", "work/dir//config.temper.md"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun deletingConfigRecomputesTentativeConfiguration() = assertSourceFilePartitions {
        writeAll(
            """
                |{
                |  work: {
                |    config.temper.md: ```
                |      # Named Based On Header
                |      ```,
                |    foo.temper: "// Exists",
                |  }
                |}
            """.trimMargin(),
        )

        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/"],
                |  newModules: ["work//", "work//config.temper.md"],
                |}
            """.trimMargin(),
        )
        assertConfiguration("work/") {
            assertEquals(DashedIdentifier("named-based-on-header"), it.libraryName)
        }

        rm("work/config.temper.md")

        assertSourcePartition(
            """
                |{
                |  // See comment in SourcePartition as to why this is new, not reused
                |  newLibraryRoots: ["work/"],
                |  reusedModules: ["work//"],
                |  droppedModules: ["work//config.temper.md"],
                |}
            """.trimMargin(),
        )

        assertConfiguration("work/") {
            // Name re-chosen based on relative path segments
            assertEquals(DashedIdentifier("work"), it.libraryName)
        }
    }

    @Test
    fun breakImportCycleAndRebuildWay1() = assertSourceFilePartitions {
        writeAll(
            """
                |{
                |  work: {
                |    a: {
                |      a.temper: ```
                |        let { x } = import("../b");
                |        ```
                |    },
                |    b: {
                |      b.temper: ```
                |        let { x } = import("../a");
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        )

        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/"],
                |  newModules:      ["work//a/", "work//b/"],
                |  errors: [
                |    "Module `work//a/` imported itself via chain of imports [`work//a/`, `work//b/`]!",
                |    "work//a/ does not export symbol x!",
                |  ],
                |}
            """.trimMargin(),
        )

        "work/a/a.temper" write "export let x = 1;"

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  dirtyModules:       ["work//a/", "work//b/"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun breakImportCycleAndRebuildWay2() = assertSourceFilePartitions {
        writeAll(
            """
                |{
                |  work: {
                |    a: {
                |      a.temper: ```
                |        let { x } = import("../b");
                |        ```
                |    },
                |    b: {
                |      b.temper: ```
                |        let { x } = import("../a");
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        )

        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/"],
                |  newModules:      ["work//a/", "work//b/"],
                |  errors: [
                |    "Module `work//a/` imported itself via chain of imports [`work//a/`, `work//b/`]!",
                |    "work//a/ does not export symbol x!",
                |  ],
                |}
            """.trimMargin(),
        )

        // The previous test case edited the other source file.
        "work/b/b.temper" write "export let x = 1;"

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["work/"],
                |  dirtyModules:       ["work//a/", "work//b/"],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun doNotDropStd() = assertSourceFilePartitions {
        "work/foo.temper" write """
            |let { Capture } = import("std/regex")
        """.trimMargin()

        // Advancing should get us std modules and work modules.
        assertSourcePartition(
            """
                |{
                |  newLibraryRoots: ["work/"],
                |  newModules:      ["work//"],
                |}
            """.trimMargin(),
        )

        // When we rescan without changes, we won't find the std modules
        // which means there is no dependency whose hash we can check hasn't
        // changed, but that's ok.

        assertSourcePartition(
            """
                |{
                |  reusedLibraryRoots: ["std/", "work/"],
                |  reusedModules:      [
                |                       "std//config.temper.md",
                |                       "std//json/",
                |                       "std//net/",
                |                       "std//regex/",
                |                       "std//temporal/",
                |                       "std//testing/",
                |                       "work//",
                |  ],
                |}
            """.trimMargin(),
        )

        // And our list of modules should include external modules because
        // otherwise the produced library set would be incomplete.
        // If all the reused non-external modules are advanced, their
        // import staging will not trigger fetching anything.
        assertModuleAdded("std//regex/")
    }
}

private class SourceFilePartitionTestHarness {
    private val fs = MemoryFileSystem()

    fun writeAll(jsonFileTree: String) {
        MemoryFileSystem.fromJson(jsonFileTree, fs)
    }

    // "some/path" write "FileContent"
    infix fun String.write(content: String) {
        val path: FilePath = splitOnSlash(this@write)
        fs.write(path, content.toByteArray())
    }

    fun rm(path: String) {
        fs.deleteDirRecursively(splitOnSlash(path))
    }

    private var priorModules: List<Module> = emptyList()
    private var priorConfigurations: AbstractLibraryConfigurations? = null

    fun assertSourcePartition(jsonPartition: String) {
        val projectLogSink = ListBackedLogSink()
        val moduleAdvancer = ModuleAdvancer(projectLogSink)

        val sourceFilePartition = SourceFilePartition(moduleAdvancer, console)
        if (priorModules.isNotEmpty()) {
            sourceFilePartition.maybeReusePreviouslyStaged(priorModules)
        }
        priorConfigurations?.let {
            sourceFilePartition.maybeReusePreviouslyStaged(it)
        }
        val snapshot = FilteringFileSystemSnapshot(fs, FileFilterRules.Allow, workRootDir)
        sourceFilePartition.scan(snapshot, workRootDir)
        sourceFilePartition.addModulesToAdvancer()

        moduleAdvancer.advanceModules()
        assertStructure(
            jsonPartition,
            projectLogSink.wrapErrorsAround(
                sourceFilePartition.needsRebuildPartition,
            ),
        )
        priorModules = moduleAdvancer.getAllModules()
        priorConfigurations = LibraryConfigurationsBundle.from(
            moduleAdvancer.getAllLibraryConfigurations(),
        )
    }

    fun assertConfiguration(
        pathString: String,
        checksConfiguration: (LibraryConfiguration) -> Unit,
    ) {
        val configuration =
            priorConfigurations?.byLibraryRoot?.get(splitOnSlash(pathString))
                ?: fail(
                    "No configuration for $pathString in ${
                        priorConfigurations?.byLibraryRoot?.keys
                    }",
                )
        checksConfiguration(configuration)
    }

    fun assertModuleAdded(
        moduleNameString: String,
    ) {
        assertTrue(
            {
                "No module $moduleNameString among ${priorModules.joinToString { it.loc.toString() }}"
            },
            priorModules.any { "${it.loc}" == moduleNameString },
        )
    }
}

private fun splitOnSlash(s: String): FilePath {
    var parts = s.split("/")
    var isDir = false
    if (parts.lastOrNull() == "") {
        isDir = true
        parts = parts.dropLast(1)
    }
    return FilePath(parts.map { FilePathSegment(it) }, isDir = isDir)
}

private val workRootDir = dirPath("work")
