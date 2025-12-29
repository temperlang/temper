package lang.temper.be.py

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.CliTool
import lang.temper.be.cli.Command
import lang.temper.be.cli.CommandDetail
import lang.temper.be.cli.CopyMode
import lang.temper.be.cli.Effort
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.ExecInteractiveRepl
import lang.temper.be.cli.INSTALL_FF_FETCH_RETRIES
import lang.temper.be.cli.INSTALL_FF_TIMEOUT_SECONDS
import lang.temper.be.cli.RunBackendSpecificCompilationStepRequest
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.composing
import lang.temper.be.cli.installShouldFailFast
import lang.temper.be.cli.maybeLogBeforeRunning
import lang.temper.be.py.helper.NilVenvBuilt
import lang.temper.be.py.helper.PythonCommand
import lang.temper.be.py.helper.PythonSpecifics
import lang.temper.be.py.helper.VenvBuilt
import lang.temper.common.Console
import lang.temper.common.MultilineOutput
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.TextTable
import lang.temper.common.console
import lang.temper.common.currents.CancelGroup
import lang.temper.common.htmlEscape
import lang.temper.common.partiallyOrder
import lang.temper.fs.OutDir
import lang.temper.fs.leaves
import lang.temper.fs.name
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.resolveDir
import lang.temper.log.resolveFile
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier

internal fun runPyBestEffort(
    pythonVersion: PythonVersion,
    cliEnv: CliEnv,
    request: ToolchainRequest,
    files: OutDir,
    dependencies: Dependencies<*>,
    backendId: BackendId,
    built: VenvBuilt = NilVenvBuilt,
    usePip: Boolean = false,
): List<ToolchainResult> =
    cliEnv.composing(cliEnv.specifics) {
        // Below we assume the dependencies and its metadata came from PyBackend
        @Suppress("UNCHECKED_CAST")
        dependencies as Dependencies<PyBackend>

        val runAsTest: Boolean = request is RunTestsRequest
        val pyDir = dirPath(backendId.uniqueId)
        val isVerbose = cliEnv.shellPreferences.verbosity == ShellPreferences.Verbosity.Verbose
        var cpython = this[PythonCommand]

        var pyLibraryName: PyIdentifierName? = null

        if (request is RunLibraryRequest) {
            pyLibraryName = dependencies.metadata[request.libraryName, PyMetadataKey.PyLibraryName(pythonVersion)]
                ?: return listOf(
                    ToolchainResult(result = RFailure(CliFailure("Missing py library name for $request"))),
                )
            write(
                """
                    |# ${hashCommentSafe(request.taskName)}
                    |from temper_core import init_simple_logging, await_safe_to_exit
                    |init_simple_logging()
                    |import $pyLibraryName as module
                    |await_safe_to_exit()
                """.trimMargin(),
                pyDir.resolveFile("$ENTRY_POINT${PyBackend.fileExtension}"),
            )
        }
        // From the cli
        cliEnv.announce(CliEnv.Checkpoint.postBuild)
        if (isVerbose) {
            console.showDetails(cliEnv, cpython, pyLibraryName, runAsTest, dependencies, files, pyDir)
        }

        val args = if (runAsTest) {
            listOf("-m", "temper_core.testing", "result.xml")
        } else {
            listOf("-m", ENTRY_POINT)
        }

        val pyVenvDir = dirPath("${backendId.uniqueId}_venv")
        // Construct a virtualenv if needed.
        if (!cliEnv.fileExistsInUserCache(pyVenvDir)) {
            val pyVenvArgs = listOf("-m", "venv", cliEnv.userCachePath(pyVenvDir))
            val venvCommand = Command(args = pyVenvArgs, cwd = pyDir)
            val result = cpython.run(venvCommand)
            if (result is RFailure) {
                maybeFreeze()
                return@composing listOf(ToolchainResult(result = result))
            }
        }
        cpython = cpython.withCommandPath(
            userCachePath(
                if (isWindows) {
                    pyVenvDir.resolveDir("Scripts").resolveFile("python.exe")
                } else {
                    pyVenvDir.resolveDir("bin").resolveFile("python")
                },
            ),
        )

        val pyLibraryPath = pyLibraryName?.let { pyDir.resolveDir(pyLibraryName.text) }

        val depPaths = buildSet {
            val core = pyDir.resolveDir(DashedIdentifier.temperCoreLibraryIdentifier.text)
            if (!built.alreadyBuilt(core)) {
                add(core)
            }
            for (libName in partiallyOrder(dependencies.transitiveDependencies)) {
                val dep = pyDir.resolveDir(libName.text)
                if (dep != pyLibraryPath && !built.alreadyBuilt(dep)) {
                    add(dep)
                }
            }
        }.toList()
        if (usePip) {
            // Install dependencies, whether bundled-mode or not (maybe just for good measure?),
            // but custom PYTHONPATH below can avoid that need for current use cases.
            // the "--report -" "--ignore-installed" and "--dry-run" flags can provide additional context
            // TODO Find if we need anything from pypi? Use a custom py env if so?
            val pipArgs = buildList {
                addAll(listOf("-m", "pip", "install"))
                if (installShouldFailFast) {
                    addAll(
                        listOf(
                            "--retries",
                            "$INSTALL_FF_FETCH_RETRIES",
                            "--timeout",
                            "$INSTALL_FF_TIMEOUT_SECONDS",
                        ),
                    )
                }
                for (dep in depPaths) {
                    add(cliEnv.envPath(dep))
                }
                if (pyLibraryPath != null) {
                    add(cliEnv.envPath(pyLibraryPath))
                }
            }
            val pipCmd = Command(args = pipArgs, cwd = pyDir)
            val result = cpython.run(pipCmd)
            if (result is RFailure) {
                maybeFreeze()
                return@composing listOf(ToolchainResult(result = result))
            }
            // Track that those libraries were built.
            built.built(depPaths)
            // Announce install.
            cliEnv.announce(CliEnv.Checkpoint.postInstall)
        }

        fun getPythonEnv() = buildMap {
            put("PYTHONIOENCODING", cliEnv.specifics.preferredEncoding.name)
            if (!usePip) {
                val allPaths = (pyLibraryPath?.let { listOf(it) } ?: listOf()) + depPaths
                put("PYTHONPATH", allPaths.joinToString(cliEnv.pathSeparator) { cliEnv.envPath(it) })
            }
        }

        fun runPy(workingDir: FilePath): RResult<EffortSuccess, CliFailure> {
            val auxPaths = mutableMapOf<Aux, FilePath>()
            if (runAsTest) {
                auxPaths[Aux.JunitXml] = workingDir.resolveFile("result.xml")
            }
            val cmd = Command(
                args = args,
                cwd = workingDir,
                env = getPythonEnv(),
                aux = auxPaths,
            )
            configurePyCharm(
                taskName = request.taskName,
                moduleName = ENTRY_POINT,
                command = cpython.specify(cmd),
                dir = workingDir,
            )
            cmd.maybeLogBeforeRunning(cpython, cliEnv.shellPreferences)
            return cpython.run(cmd)
        }

        when (request) {
            is ExecInteractiveRepl -> {
                val (tool, command) = runPythonInteractiveShellAndExit(
                    pythonVersion = pythonVersion,
                    workingDir = pyDir,
                    pythonEnv = getPythonEnv(),
                    tool = cpython,
                    dependencies = dependencies,
                )
                listOf(
                    ToolchainResult(
                        result = RFailure(
                            CliFailure(
                                message = "execve of ${tool.command} failed",
                                effort = Effort(command = cpython.specify(command), cliEnv = cliEnv),
                            ),
                        ),
                    ),
                )
            }

            is RunBackendSpecificCompilationStepRequest ->
                // Unless be-py defines subtypes of this, this is unreachable.
                throw IllegalStateException("Unexpected $request")

            is RunLibraryRequest -> listOf(ToolchainResult(result = runPy(pyDir)))
            is RunTestsRequest -> request.libraries?.mapNotNull { library ->
                val testDir = pyDir.resolveDir(library.text)
                when (cliEnv.fileExists(testDir.resolveDir("tests"))) {
                    true -> ToolchainResult(libraryName = library, result = runPy(testDir))
                    false -> null
                }
            } ?: emptyList()
        }.also { results ->
            if (results.any { it.result is RFailure }) {
                maybeFreeze()
            }
        }
    }

private fun Console.showDetails(
    cliEnv: CliEnv,
    cpython: CliTool,
    pyLibraryName: PyIdentifierName?,
    runAsTest: Boolean,
    dependencies: Dependencies<*>,
    files: OutDir,
    pyDir: FilePath,
) {
    group("runPyBestEffort") {
        log("cpython=${cpython.command}")
        log("pyLibraryName=$pyLibraryName")
        log("runAsTest=$runAsTest")
        group("dependencies.shallowDependencies") {
            dependencies.shallowDependencies.forEach {
                log("${it.key} => ${it.value}")
            }
        }
        group("input files from ${files.path}") {
            files.leaves().forEach {
                log("${it.path}")
            }
        }
        log("pyDir=$pyDir")
        group("output root") {
            fun enumerateFiles(p: FilePath) {
                if (p.isDir) {
                    cliEnv.readDir(p).forEach {
                        enumerateFiles(it)
                    }
                } else {
                    log("$p")
                }
            }
            enumerateFiles(FilePath.emptyPath)
        }
    }
}

internal fun runPySingleModule(
    module: String,
    libraries: List<FilePath>,
    shellPreferences: ShellPreferences,
    cancelGroup: CancelGroup,
): RResult<EffortSuccess, CliFailure> = CliEnv.using(PythonSpecifics, shellPreferences, cancelGroup) {
    for (lib in libraries) {
        copySourceFileTree(lib, FilePath.emptyPath, mode = CopyMode.PlaceTop)
    }

    val pythonTool = this[PythonCommand]
    val command = Command(args = listOf("-m", module), cwd = FilePath.emptyPath)
    command.maybeLogBeforeRunning(pythonTool, shellPreferences)
    val runResult = pythonTool.run(command)
    if (runResult !is RSuccess) {
        maybeFreeze()
    }
    runResult
}

fun CliEnv.configurePyCharm(
    taskName: String,
    moduleName: String,
    command: CommandDetail,
    dir: FilePath,
) {
    val envTags = (command.env + mapOf("PYTHONUNBUFFERED" to "1")).entries.joinToString { (k, v) ->
        "<env name='${htmlEscape(k)}' value='${htmlEscape(v)}' />"
    }
    // Write out enough XML that PyCharm can open this test folder, and you get a nice run button.
    listOf(
        filePath(".idea", "$taskName.iml") to $$"""<?xml version="1.0" encoding="UTF-8"?>
            <module type="PYTHON_MODULE" version="4">
              <component name="NewModuleRootManager">
                <content url="file://$MODULE_DIR$" />
                <orderEntry type="inheritedJdk" />
                <orderEntry type="sourceFolder" forTests="false" />
              </component>
            </module>
            """,
        filePath(".idea", "workspace.xml") to $$"""<?xml version="1.0" encoding="UTF-8"?>
            <project version="4">
              <component name="RunManager">
                <configuration name="run" type="PythonConfigurationType" factoryName="Python"
                    temporary="true" nameIsGenerated="true">
                  <module name="$${htmlEscape(taskName)}" />
                  <option name="WORKING_DIRECTORY" value="$PROJECT_DIR$" />
                  <option name="SDK_HOME" value="$${htmlEscape(command.command)}" />
                  <option name="SCRIPT_NAME" value="$${htmlEscape(moduleName)}" />
                  <option name="MODULE_MODE" value="true" />
                  <envs>$$envTags</envs>
                  <method v="2" />
                </configuration>
              </component>
            </project>""",
    ).forEach { (path, content) ->
        write(content, dir.resolve(path.segments, isDir = false))
    }
}

private fun runPythonInteractiveShellAndExit(
    pythonVersion: PythonVersion,
    workingDir: FilePath,
    pythonEnv: Map<String, String>,
    tool: CliTool,
    dependencies: Dependencies<PyBackend>,
): Pair<CliTool, Command> {
    val command = Command(
        cwd = workingDir,
        env = pythonEnv,
        args = buildList {
            val libraryNamePythonNamePairs =
                dependencies.libraryConfigurations.byLibraryName.keys.mapNotNull { temperLibraryName ->
                    dependencies.metadata[temperLibraryName, PyMetadataKey.PyLibraryName(pythonVersion)]
                        ?.let { pyLibraryName ->
                            temperLibraryName to pyLibraryName
                        }
                }

            val messageToUser = buildString {
                append("Running with Temper built Python pre-loaded:\n")
                val table = TextTable(
                    buildList {
                        add(
                            listOf(
                                MultilineOutput.of("Temper library"),
                                MultilineOutput.of("Python name"),
                            ),
                        )
                        libraryNamePythonNamePairs.forEach { (temperName, pyName) ->
                            add(
                                listOf(
                                    MultilineOutput.of(temperName.text),
                                    MultilineOutput.of(pyName.text),
                                ),
                            )
                        }
                    },
                )
                append("$table\n")
            }

            val preloadScript = buildString {
                libraryNamePythonNamePairs.forEach { (_, pyLibraryName) ->
                    append("import ${pyLibraryName.text}\n")
                }
                append("import sys\n")
                // python3 with -i and -c does not dump out the usual explanatory text.
                append("print(\"%s %s\\n\" % (sys.implementation.name, sys.version))\n")
                append("print('Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.\\n')\n")
                append("print(${stringTokenText(messageToUser)})\n")
            }

            add("-i") // Enter interactive mode even when -c specified
            add("-c") // Execute the next argument as python code before entering interactive mode
            add(preloadScript)
        },
    )
    tool.runAsLast(command)
    // Return these for diagnostics since runAsLast failed if we get here.
    return tool to command
}

/** A string like [s] but safe to include in a hash comment. */
internal fun hashCommentSafe(s: String): String = pyLineTerminator.replace(s, " ")

// This could be __main__ if we're trying to run it as a package.
const val ENTRY_POINT = "top"
