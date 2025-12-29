package lang.temper.frontend.staging

import lang.temper.common.CustomValueFormatter
import lang.temper.common.EnumRange
import lang.temper.common.Log
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonProperty
import lang.temper.common.json.JsonValue
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.structure.Hints
import lang.temper.common.withCapturingConsole
import lang.temper.format.ConsoleBackedContextualLogSink
import lang.temper.fs.FileFilterRules
import lang.temper.fs.FilteringFileSystemSnapshot
import lang.temper.fs.MemoryFileSystem
import lang.temper.lexer.defaultClassifyTemperSource
import lang.temper.library.LibraryConfiguration
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.unknownPos
import lang.temper.name.DashedIdentifier
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.value.TStageRange
import lang.temper.value.Value
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModuleAdvancerTest {
    @Test
    fun noFilesTerminates() = assertVerboseLogOutput(
        """
            |{}
        """.trimMargin(),
        """
            |Bye
        """.trimMargin(),
    )

    @Test
    fun oneModuleAdvances() = assertVerboseLogOutput(
        """
            |{
            |  src: {
            |    "foo.temper": ```
            |      console.log("Foo");
            |      ```,
            |    "README.md": ```
            |      Nothing here is Temper code
            |
            |          console.log("Not even this");
            |      ```
            |  }
            |}
        """.trimMargin(),
        """
            |[work/]: Library found
            |## README.md is not listed in src/'s file list
            |[work//src/]: Pre-staging module from [work/src/foo.temper]
            |[work//src/]: Starting stage @L..G
            |Bye
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun oneModuleWithPrefaceAdvances() {
        val advancer = assertVerboseLogOutputReturnAdvancer(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export let inPreface = true;
                |      ;;;
                |      let inBody = true;
                |      !inPreface // Not an error.  Implicitly imported
                |      ```
                |  }
                |}
            """.trimMargin(),
            """
                |[work/]: Library found
                |[work//foo/:preface]: Pre-staging module from [work/foo/foo.temper]
                |[work//foo/]: Pre-staging module from [work/foo/foo.temper]
                |[work//foo/:preface]: Starting stage @L..X
                |[work//foo/]: Starting stage @L
                |[work//foo/:preface]: Starting stage @Q
                |[work//foo/]: Starting stage @P
                |[work//foo/:preface]: Starting stage @G
                |[work//foo/]: Starting stage @I..G
                |Bye
            """.trimMargin(),
        )
        // And `foo/` should have an import record for `inPreface` from foo/:preface
        val fooModule = advancer.getAllModules().firstOrNull {
            "work//foo/" == "${it.loc}"
        }
        assertNotNull(fooModule, "Expect module named work//foo/")
        assertEquals(1, fooModule.importRecords.size, "work//foo/ imports one")
        val importRecord = fooModule.importRecords.first()
        assertEquals(
            "work//foo/:preface",
            "${importRecord.exporterLocation}",
            "work//foo/ imports from its preface",
        )
    }

    @Test
    fun dirModuleWithConfigAdvances() = assertVerboseLogOutput(
        """
            |{
            |  "src": {
            |    "config.temper.md":
            |        ```
            |        # My Library
            |        ```,
            |    "foo.temper.md":
            |        ```
            |        console.log("Foo");
            |        ```,
            |    "bar.temper.md":
            |        ```
            |        console.log("Bar");
            |        ```,
            |  }
            |}
        """.trimMargin(),
        """
            |[work/src/]: Library found
            |[work/src//config.temper.md]: Pre-staging module from [work/src/config.temper.md]
            |[work/src//]: Pre-staging module from [work/src/bar.temper.md, work/src/foo.temper.md]
            |[work/src//config.temper.md]: Starting stage @L..X
            |[work/src/]: Configured library `my-library`
            |[work/src//config.temper.md]: Starting stage @Q..G
            |[work/src//]: Starting stage @L..G
            |Bye
        """.trimMargin(),
    )

    @Test
    fun importCycleAborted() = assertVerboseLogOutput(
        """
            |{
            |  a: {
            |    "foo.temper":
            |        ```
            |        export let a = "a";
            |        let { b } = import("../b/"); // But b also imports a
            |        ```
            |  },
            |  b: {
            |    "bar.temper":
            |        ```
            |        export let b = "b";
            |        let { a } = import("../a/");
            |        let { c } = import("../c/");
            |        ```
            |  },
            |  c: {
            |    "baz.temper":
            |        ```
            |        export let c = "c";
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
        """
            |[work/]: Library found
            |[work//a/]: Pre-staging module from [work/a/foo.temper]
            |[work//b/]: Pre-staging module from [work/b/bar.temper]
            |[work//c/]: Pre-staging module from [work/c/baz.temper]
            |[work//a/]: Starting stage @L
            |[work//b/]: Starting stage @L
            |[work//c/]: Starting stage @L
            |[work//a/]: Starting stage @P
            |[work//b/]: Starting stage @P
            |[work//c/]: Starting stage @P
            |[work//a/]: Starting stage @I
            |[work//b/]: Starting stage @I
            |[work//c/]: Starting stage @I..G
            |## a/ is waiting on b/ and vice versa.
            |2: let { b } = import("../b/"); // But b also impo
            |               ┗━━━━━━━━━━━━━┛
            |[work/a/foo.temper:2+12-27]: Module work//a/ imported itself via chain of imports [work//a/, work//b/]
            |2: let { a } = import("../a/");
            |               ┗━━━━━━━━━━━━━┛
            |[work/b/bar.temper:2+12-27]: Import was involved in cycle
            |[work//a/]: Starting stage @A..X
            |[work//b/]: Starting stage @A
            |[work//a/]: Starting stage @Q
            |[work//b/]: Starting stage @S
            |[work//a/]: Starting stage @G
            |[work//b/]: Starting stage @D..G
            |Bye
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun duplicateLibraryName() = assertVerboseLogOutput(
        """
            |{
            |  a: {
            |    "config.temper.md": "# My Library",
            |    "a.temper": "console.log('Hi')",
            |  },
            |  b: {
            |    "config.temper.md": "# My Library",
            |    "b.temper": "console.log('Hello')",
            |  }
            |}
        """.trimMargin(),
        """
            |Library name `my-library` is used by multiple libraries at [work/a/, work/b/]
        """.trimMargin(),
        Log.Warn,
    )

    @Test
    fun fireAndForgetImportsDoNotDelayImporter() = assertVerboseLogOutput(
        """
            |{
            |  "config.temper.md":
            |      ```
            |      # Example library
            |
            |          import("./dir/");
            |      ```,
            |  "dir": {
            |    "src.temper":
            |        ```
            |        export let n = 1234;
            |        ```,
            |  }
            |}
        """.trimMargin(),
        """
            |[work/]: Library found
            |[work//config.temper.md]: Pre-staging module from [work/config.temper.md]
            |[work//dir/]: Pre-staging module from [work/dir/src.temper]
            |[work//config.temper.md]: Starting stage @L..X
            |[work/]: Configured library `example-library`
            |## library was configured before starting dir/`
            |[work//config.temper.md]: Starting stage @Q..G
            |[work//dir/]: Starting stage @L..G
            |Bye
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun canImportDirModuleWithOrWithoutTrailingSlash() = assertVerboseLogOutput(
        """
            |{
            |  a: {
            |    "with-slash.temper": "let { x } = import('../c/');",
            |  },
            |  b: {
            |    "without-slash.temper": "let { x } = import('../c');",
            |  },
            |  c: {
            |    "exports-x.temper": "export let x = 1234;",
            |  },
            |}
        """.trimMargin(),
        """
            |[work/]: Library found
            |[work//a/]: Pre-staging module from [work/a/with-slash.temper]
            |[work//b/]: Pre-staging module from [work/b/without-slash.temper]
            |[work//c/]: Pre-staging module from [work/c/exports-x.temper]
            |[work//a/]: Starting stage @L
            |[work//b/]: Starting stage @L
            |[work//c/]: Starting stage @L
            |[work//a/]: Starting stage @P
            |[work//b/]: Starting stage @P
            |[work//c/]: Starting stage @P
            |[work//a/]: Starting stage @I
            |[work//b/]: Starting stage @I
            |[work//c/]: Starting stage @I..X
            |## c/ reached the export stage before a/ and b/ because it's blocking them
            |## which indicates successful resolution of both variations on naming.
            |[work//a/]: Starting stage @A
            |[work//b/]: Starting stage @A
            |[work//c/]: Starting stage @Q
            |[work//a/]: Starting stage @S
            |[work//b/]: Starting stage @S
            |[work//c/]: Starting stage @G
            |[work//a/]: Starting stage @D
            |[work//b/]: Starting stage @D
            |[work//a/]: Starting stage @T
            |[work//b/]: Starting stage @T
            |[work//a/]: Starting stage @F
            |[work//b/]: Starting stage @F
            |[work//a/]: Starting stage @X
            |[work//b/]: Starting stage @X
            |[work//a/]: Starting stage @Q
            |[work//b/]: Starting stage @Q
            |[work//a/]: Starting stage @G
            |[work//b/]: Starting stage @G
            |Bye
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun unresolvableImportDoesNotBlockCompletion() = assertVerboseLogOutput(
        """
            |{
            |  a: {
            |    "impl.temper":
            |        ```
            |        let { x } = import("../b/");
            |        let { y } = import("../c/");
            |        ```,
            |  },
            |  b: {
            |    "impl.temper":
            |        ```
            |        export let x = 123;
            |        ```,
            |  },
            |  c: {
            |    // NOTHING HERE :(
            |  }
            |}
        """.trimMargin(),
        """
            |[work/]: Library found
            |[work//a/]: Pre-staging module from [work/a/impl.temper]
            |[work//b/]: Pre-staging module from [work/b/impl.temper]
            |[work//a/]: Starting stage @L
            |[work//b/]: Starting stage @L
            |[work//a/]: Starting stage @P
            |[work//b/]: Starting stage @P
            |[work//a/]: Starting stage @I
            |2: let { y } = import("../c/");
            |               ┗━━━━━━━━━━━━━┛
            |[work/a/impl.temper:2+12-27]@I: Import of file:work/c/ failed
            |[work//b/]: Starting stage @I..X
            |[work//a/]: Starting stage @A
            |[work//b/]: Starting stage @Q
            |[work//a/]: Starting stage @S
            |[work//b/]: Starting stage @G
            |[work//a/]: Starting stage @D..G
            |Bye
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun importBySelfLibraryNameWorksWhenNameExportedFromConfig() = assertVerboseLogOutput(
        """
            |{
            |  "config.temper.md":
            |      ```
            |      # Read Me Seymour
            |
            |          export let name = "example-library";
            |      ```,
            |  a: {
            |    "impl.temper":
            |        ```
            |        // Use the library's own name to import another part
            |        // of it while we're staging it.
            |        let { x } = import("example-library/b");
            |        ```,
            |  },
            |  b: {
            |    "impl.temper":
            |        ```
            |        export let x = 123;
            |        ```,
            |  },
            |}
        """.trimMargin(),
        """
            |[work/]: Library found
            |[work//config.temper.md]: Pre-staging module from [work/config.temper.md]
            |[work//a/]: Pre-staging module from [work/a/impl.temper]
            |[work//b/]: Pre-staging module from [work/b/impl.temper]
            |[work//config.temper.md]: Starting stage @L..X
            |## Here, we found the reliable library name before processing
            |## any local imports that might need it
            |[work/]: Configured library `example-library`
            |[work//config.temper.md]: Starting stage @Q..G
            |[work//a/]: Starting stage @L
            |[work//b/]: Starting stage @L
            |[work//a/]: Starting stage @P
            |[work//b/]: Starting stage @P
            |[work//a/]: Starting stage @I
            |## Here, a/ realizes it needs b/ based on the local import
            |## and we skip b/ forward to export stage.
            |[work//b/]: Starting stage @I..X
            |## Now, a/ has linked to b/ so we can start staging a/ again.
            |[work//a/]: Starting stage @A
            |[work//b/]: Starting stage @Q
            |[work//a/]: Starting stage @S
            |[work//b/]: Starting stage @G
            |[work//a/]: Starting stage @D..G
            |Bye
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun autoStagedStdHasMetadataFromItsConfigFile() {
        val advancer = assertVerboseLogOutputReturnAdvancer(
            """
                |{
                |  "foo.temper":
                |      ```
                |      let { Capture } = import("std/regex");
                |      ```
                |}
            """.trimMargin(),
            """
                |
            """.trimMargin(),
            level = Log.Warn,
        )
        val stdConfig = advancer.getAllLibraryConfigurations().first {
            it.libraryName == DashedIdentifier.temperStandardLibraryIdentifier
        }
        assertContains(stdConfig.configExports, Symbol("version"))
    }

    @Test
    fun importRecordsCreatedForImports() {
        val advancer = assertVerboseLogOutputReturnAdvancer(
            fileTreeJson = """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export let message = "Hello, World";
                |      ```,
                |  },
                |  bar: {
                |    bar.temper: ```
                |      let { message as msg } = import("../foo");
                |      console.log(msg);
                |      ```,
                |  },
                |}
            """.trimMargin(),
            wantedLogOutput = "",
            level = Log.Warn,
        )
        val barModule = advancer.getAllModules().first {
            "bar" in "${it.loc}"
        }
        assertEquals(
            listOf("work//foo/"),
            barModule.importRecords.map { "${it.exporterLocation}" },
        )
    }

    private fun assertVerboseLogOutput(
        fileTreeJson: String,
        wantedLogOutput: String,
        level: Log.LevelFilter = Log.Fine,
    ) {
        assertVerboseLogOutputReturnAdvancer(
            fileTreeJson = fileTreeJson, wantedLogOutput = wantedLogOutput, level = level,
        )
    }

    private fun assertVerboseLogOutputReturnAdvancer(
        fileTreeJson: String,
        wantedLogOutput: String,
        level: Log.LevelFilter = Log.Fine,
    ): ModuleAdvancer {
        val workDir = dirPath("work")
        val fileTreeObj = JsonObject(
            listOf(
                JsonProperty("work", JsonValue.parse(fileTreeJson, tolerant = true).result!!, Hints.empty),
            ),
        )

        val fileSystem = MemoryFileSystem.fromStructure(fileTreeObj)
        val snapshot = FilteringFileSystemSnapshot(fileSystem, FileFilterRules.Allow, root = workDir)

        val advancer = ModuleAdvancer(LogSink.devNull)
        val (_, stdout) = withCapturingConsole { console ->
            console.setLogLevel(level)

            val logSink = run {
                val writingLogSink = ConsoleBackedContextualLogSink(
                    localConsole = console,
                    sharedLocationContext = advancer.sharedLocationContext,
                    parent = null,
                    customValueFormatter = CustomValueFormatter.Nope, simplifying = true,
                )
                LessSpammyLogSink(writingLogSink)
            }

            advancer.projectLogSink = logSink
            partitionSourceFilesIntoModules(
                snapshot, advancer, logSink, console,
                root = workDir,
                makeTentativeLibraryConfiguration = { nameGuess, root ->
                    LibraryConfiguration(nameGuess, root, emptyList(), ::defaultClassifyTemperSource)
                },
            )

            advancer.advanceModules()
            // AllDone has effect of flushing log sink
            logSink.log(Log.Fine, MessageTemplate.AllDone, unknownPos, emptyList())
        }

        assertEquals(wantedLogOutput.trimEnd(), stdout.trimEnd())
        return advancer
    }
}

/** Collapses adjacent "Starting stage ..." messages into one */
private class LessSpammyLogSink(private val logSink: LogSink) : LogSink {
    override var hasFatal: Boolean = false
    private var pending: LogEntry? = null
    override fun log(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        fyi: Boolean,
    ) {
        if (level >= Log.Fatal) {
            hasFatal = true
        }
        val entry = LogEntry(level, template, pos, values, fyi)
        val pending = this.pending
        if (
            entry.template == MessageTemplate.StartingStage &&
            pending?.template == entry.template &&
            pending.pos == entry.pos
        ) {
            val stageP = pending.values.firstOrNull()
            val stageE = entry.values.firstOrNull()
            val stageRange: EnumRange<Stage>? = TStageRange.unpackOrNull(stageP as? Value<*>)
                ?: (stageP as? Stage)?.let {
                    EnumRange(it, it)
                }
            if (stageRange != null && stageE is Stage && Stage.after(stageRange.endInclusive) == stageE) {
                val extendedStageRange = Value(
                    EnumRange(stageRange.start, stageE),
                    TStageRange,
                )
                this.pending = pending.copy(values = listOf(extendedStageRange))
                return
            }
        }
        if (pending != null) {
            this.pending = null
            pending.logTo(logSink)
        }
        if (this.pending == null && entry.template == MessageTemplate.StartingStage) {
            this.pending = entry
        } else {
            entry.logTo(logSink)
        }
    }
}
