package lang.temper.cli.js

import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.js.JsBackend
import lang.temper.common.assertStringsEqual
import lang.temper.common.console
import lang.temper.common.withCapturingConsole
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.fs.MemoryFileSystem
import lang.temper.fs.RealWritableFileSystem
import lang.temper.fs.copyRecursive
import lang.temper.fs.runWithTemporaryDirectory
import lang.temper.log.FilePath
import lang.temper.log.toFileTreeString
import lang.temper.name.DashedIdentifier
import lang.temper.tooling.buildrun.Build
import lang.temper.tooling.buildrun.BuildDoneResult
import lang.temper.tooling.buildrun.BuildHarness
import lang.temper.tooling.buildrun.RunTask
import lang.temper.tooling.buildrun.doOneBuild
import lang.temper.tooling.buildrun.withTempDir
import java.util.concurrent.ForkJoinPool
import kotlin.test.Test

class JsRunFileLayoutTest {

    @Test
    fun someModules() = translateAndRun(
        libraryNameText = "library-a",
        // a depends on b but not on c, so we end up copying over the two former
        // and arrange them in node_modules so that `import ... from 'b/main'` works in node.
        inputFileTree = """
            |{
            |  "a": {
            |    "config.temper.md": ```
            |        # library a
            |        ```,
            |    "main.temper": ```
            |        let {message as bMessage} = import("../b");
            |        let {message as cMessage} = import("../c");
            |        let {Capture} = import("std/regex"); // just to build std
            |
            |        do {
            |          let sb = new StringBuilder();
            |          sb.append(bMessage);
            |          sb.append(" and ");
            |          sb.append(cMessage);
            |
            |          console.log(sb.toString());
            |        }
            |        ```,
            |  },
            |  "b": {
            |    "config.temper.md": ```
            |        # library b
            |        ```,
            |    "message.temper": ```
            |        export let { message } = import("../c");
            |        ```,
            |  },
            |  "c": {
            |    "config.temper.md": ```
            |        # library c
            |        ```,
            |    "message.temper": ```
            |        export let message = "Hello, World!";
            |        ```,
            |  },
            |  "d": {
            |    "config.temper.md": ```
            |        # library d
            |        ```,
            |    "main.temper": ```
            |        console.log("d");
            |        ```,
            |  },
            |}
        """.trimMargin(),
        wantedFileTreeUnderRunRoot = """
            |./
            |┣━-logs/
            |┃ ┗━0000.log
            |┗━js/
            |  ┣━index.js
            |  ┣━library-a/
            |  ┃ ┣━index.js
            |  ┃ ┣━library_a.js
            |  ┃ ┗━package.json
            |  ┣━library-b/
            |  ┃ ┣━index.js
            |  ┃ ┣━library_b.js
            |  ┃ ┗━package.json
            |  ┣━library-c/
            |  ┃ ┣━index.js
            |  ┃ ┣━library_c.js
            |  ┃ ┗━package.json
            |  ┣━library-d/
            |  ┃ ┣━index.js
            |  ┃ ┣━library_d.js
            |  ┃ ┗━package.json
            |  ┣━node_modules/
            |  ┃ ┣━.package-lock.json
            |${when (System.getProperty("os.name").startsWith("Windows")) {
            // No links on Windows.
            true -> """
                |  ┃ ┣━@temperlang/
                |  ┃ ┃ ┣━core/
                |  ┃ ┃ ┃ ┣━async.js
                |  ┃ ┃ ┃ ┣━bitvector.js
                |  ┃ ┃ ┃ ┣━check-type.js
                |  ┃ ┃ ┃ ┣━core.js
                |  ┃ ┃ ┃ ┣━date.js
                |  ┃ ┃ ┃ ┣━deque.js
                |  ┃ ┃ ┃ ┣━float.js
                |  ┃ ┃ ┃ ┣━index.js
                |  ┃ ┃ ┃ ┣━interface.js
                |  ┃ ┃ ┃ ┣━listed.js
                |  ┃ ┃ ┃ ┣━mapped.js
                |  ┃ ┃ ┃ ┣━net.js
                |  ┃ ┃ ┃ ┣━package.json
                |  ┃ ┃ ┃ ┣━pair.js
                |  ┃ ┃ ┃ ┣━regex.js
                |  ┃ ┃ ┃ ┣━string.js
                |  ┃ ┃ ┃ ┗━tsconfig.json
                |  ┃ ┃ ┗━std/
                |  ┃ ┃   ┣━index.js
                |  ┃ ┃   ┣━json.js
                |  ┃ ┃   ┣━net.js
                |  ┃ ┃   ┣━package.json
                |  ┃ ┃   ┣━regex.js
                |  ┃ ┃   ┣━temporal.js
                |  ┃ ┃   ┗━testing.js
                |  ┃ ┣━library-a/
                |  ┃ ┃ ┣━index.js
                |  ┃ ┃ ┣━library_a.js
                |  ┃ ┃ ┗━package.json
                |  ┃ ┣━library-b/
                |  ┃ ┃ ┣━index.js
                |  ┃ ┃ ┣━library_b.js
                |  ┃ ┃ ┗━package.json
                |  ┃ ┣━library-c/
                |  ┃ ┃ ┣━index.js
                |  ┃ ┃ ┣━library_c.js
                |  ┃ ┃ ┗━package.json
                |  ┃ ┗━library-d/
                |  ┃   ┣━index.js
                |  ┃   ┣━library_d.js
                |  ┃   ┗━package.json
            """.trimMargin()
            // For other platforms, these are links to output dirs.
            false -> """
                |  ┃ ┣━@temperlang/
                |  ┃ ┃ ┣━core
                |  ┃ ┃ ┗━std
                |  ┃ ┣━library-a
                |  ┃ ┣━library-b
                |  ┃ ┣━library-c
                |  ┃ ┗━library-d
            """.trimMargin()
        }}
            |  ┣━package-lock.json
            |  ┣━package.json
            |  ┣━std/
            |  ┃ ┣━index.js
            |  ┃ ┣━json.js
            |  ┃ ┣━net.js
            |  ┃ ┣━package.json
            |  ┃ ┣━regex.js
            |  ┃ ┣━temporal.js
            |  ┃ ┗━testing.js
            |  ┗━temper-core/
            |    ┣━async.js
            |    ┣━bitvector.js
            |    ┣━check-type.js
            |    ┣━core.js
            |    ┣━date.js
            |    ┣━deque.js
            |    ┣━float.js
            |    ┣━index.js
            |    ┣━interface.js
            |    ┣━listed.js
            |    ┣━mapped.js
            |    ┣━net.js
            |    ┣━package.json
            |    ┣━pair.js
            |    ┣━regex.js
            |    ┣━string.js
            |    ┗━tsconfig.json
        """.trimMargin(),
    ) {
        when (it.lastOrNull()?.extension) {
            ".map" -> false
            else -> true
        }
    }

    private fun translateAndRun(
        libraryNameText: String,
        inputFileTree: String,
        wantedFileTreeUnderRunRoot: String,
        fileFilter: (FilePath) -> Boolean = { true },
    ): Unit = runWithTemporaryDirectory(testName = "FileLayoutTest") { workRootDir ->
        val workFileSystem = RealWritableFileSystem(
            javaRoot = workRootDir,
            onIoException = { throw it },
        )
        copyRecursive(
            from = MemoryFileSystem.fromJson(inputFileTree),
            to = workFileSystem,
        )
        val files = mutableListOf<FilePath>()

        val (doRunResult, consoleOutput) = withCapturingConsole { testConsole ->
            val libraryName = DashedIdentifier.from(libraryNameText)
                ?: error("Bad library name $libraryNameText")
            val backends = setOf(JsBackend.Factory.backendId)
            val jobName = "testJob"
            BuildHarness(
                executorService = ForkJoinPool.commonPool(),
                shellPreferences = ShellPreferences.default(testConsole),
                workRoot = workRootDir,
                ignoreFile = null,
                backends = backends.toList(),
            ).use { buildHarness ->
                withTempDir(jobName) { tmpDir ->
                    val build = Build(
                        harness = buildHarness,
                        runTask = RunTask(
                            request = RunLibraryRequest(libraryName, taskName = jobName),
                            backends = backends,
                            currentDirectory = tmpDir,
                        ),
                        moduleConfig = ModuleConfig(mayRun = true),
                    )
                    build.checkpoints.on(CliEnv.Checkpoint.postInstall) { cliEnv: CliEnv, _ ->
                        files.addAll(cliEnv.readDir(FilePath.emptyPath, recursively = true))
                    }
                    doOneBuild(build)
                }
            }
        }
        if (!doRunResult.ok) {
            console.group("Console output") {
                console.error(consoleOutput)
            }
            console.group("Run result") {
                console.error(
                    (doRunResult as? BuildDoneResult)?.taskResults?.outputThunk?.invoke()
                        ?: "<NO OUTPUT>",
                )
            }
        }

        assertStringsEqual(
            want = wantedFileTreeUnderRunRoot,
            got = files.filter { fileFilter(it) }.toFileTreeString(),
        )
    }
}
