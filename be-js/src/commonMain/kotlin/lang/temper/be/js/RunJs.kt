package lang.temper.be.js

import lang.temper.be.Dependencies
import lang.temper.be.cli.Advice
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.CliTool
import lang.temper.be.cli.Command
import lang.temper.be.cli.Effort
import lang.temper.be.cli.ExecInteractiveRepl
import lang.temper.be.cli.INSTALL_FF_FETCH_RETRIES
import lang.temper.be.cli.INSTALL_FF_RETRY_PAUSE_MILLIS
import lang.temper.be.cli.INSTALL_FF_TIMEOUT_MILLIS
import lang.temper.be.cli.RunBackendSpecificCompilationStepRequest
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.installShouldFailFast
import lang.temper.be.cli.maybeLogBeforeRunning
import lang.temper.be.cli.print
import lang.temper.be.tmpl.isStdLib
import lang.temper.be.util.ConfigFromCli
import lang.temper.common.Log
import lang.temper.common.MultilineOutput
import lang.temper.common.RFailure
import lang.temper.common.TextTable
import lang.temper.common.console
import lang.temper.common.ignore
import lang.temper.common.jsonEscaper
import lang.temper.fs.OutDir
import lang.temper.fs.leaves
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.log.resolveDir
import lang.temper.log.resolveFile
import lang.temper.name.DashedIdentifier
import lang.temper.name.identifiers.IdentStyle

internal fun runJsBestEffort(
    cliEnv: CliEnv,
    request: ToolchainRequest,
    files: OutDir,
    dependencies: Dependencies<*>,
): List<ToolchainResult> {
    @Suppress("UNCHECKED_CAST") // Allow this assumption below.
    dependencies as Dependencies<JsBackend>

    val isVerbose = cliEnv.shellPreferences.verbosity == ShellPreferences.Verbosity.Verbose

    // The tools we're going to use.
    val nodeTool: CliTool = cliEnv[NodeCommand]
    val npmTool: CliTool = cliEnv[NpmCommand]

    // Unpack the request a bit.
    val mainLibraryConfig: LibraryConfiguration?
    val mainImportSpecifier: String?
    val subDirName = JsBackend.BACKEND_ID
    var libraryNames: Iterable<DashedIdentifier>? = null
    when (request) {
        is RunBackendSpecificCompilationStepRequest -> error("$request")
        is RunLibraryRequest -> {
            mainLibraryConfig = dependencies.libraryConfigurations
                .byLibraryName.getValue(request.libraryName)
            val jsLibraryName = dependencies.metadata[request.libraryName, JsMetadataKey.JsLibraryName]
                ?: return listOf(
                    ToolchainResult(result = RFailure(CliFailure("No JS library name for $request"))),
                )
            mainImportSpecifier = jsLibraryName
        }
        is RunTestsRequest -> {
            libraryNames = request.libraries
            mainImportSpecifier = null
            mainLibraryConfig = null
        }

        is ExecInteractiveRepl -> {
            mainLibraryConfig = null
            mainImportSpecifier = null
        }
    }

    val generatedIndexFile = filePath(subDirName, "index.js")
    val generatedPackageJson = filePath(subDirName, "package.json")
    if (request is RunLibraryRequest) {
        check(mainImportSpecifier != null) // See `when` case above
        // This convenience file can get overwritten on each run, but it sits outside built libraries.
        cliEnv.write(
            // TODO Logging setup here if we get fancy with it.
            """
                |await (import(${stringTokenText(mainImportSpecifier)}));
                |
            """.trimMargin(),
            generatedIndexFile,
        )
    }

    // Use workspaces to install dev dependencies of our libraries.
    // See: https://docs.npmjs.com/cli/v9/using-npm/workspaces
    val workspaceDirs = buildList {
        cliEnv.readDir(dirPath(subDirName)).forEach { libDir ->
            val packageJson = libDir.resolveFile("package.json")
            if (cliEnv.fileExists(packageJson)) {
                // Always refers to a package on the local filesystem.
                add(libDir)
            }
        }
    }
    val workspaces = workspaceDirs.joinToString(",\n") { jsonEscaper.escape("./${it.last()}") }.prependIndent("    ")
    // This workspace combo package is also outside (above) built libraries.
    val plannedContent = """
        |{
        |  "name": "",
        |  "description": "Generated package to allow running a Temper module",
        |  "version": "0.0.0",
        |  "main": ${stringTokenText("$generatedIndexFile")},
        |  "type": "module",
        |  "dependencies": {},
        |  "devDependencies": {},
        |  "workspaces": [
        |$workspaces
        |  ],
        |  "private": true
        |}
        |
    """.trimMargin()
    val existingContent = when (cliEnv.fileExists(generatedPackageJson)) {
        true -> cliEnv.readFile(generatedPackageJson)
        false -> ""
    }
    val needsNewPackageJson = plannedContent != existingContent
    if (needsNewPackageJson) {
        cliEnv.write(plannedContent, generatedPackageJson)
    }

    if (isVerbose) {
        console.group("runJsBestEffort") {
            console.log("npmTool=${npmTool.name}")
            console.log("request=$request")
            console.log("mainLibraryName=${mainLibraryConfig?.libraryName}")
            console.log("mainImportSpecifier=$mainImportSpecifier")
            console.group("dependencies.shallowDependencies") {
                dependencies.shallowDependencies.forEach {
                    console.log("${it.key} => ${it.value}")
                }
            }
            console.group("input files from ${files.path}") {
                files.leaves().forEach {
                    console.log("${it.path}")
                }
            }
            console.log("generatedIndexFile=$generatedIndexFile")
            console.log("generatedPackageJson=$generatedPackageJson")
            console.group("output root") {
                fun enumerateFiles(p: FilePath) {
                    if (p.isDir) {
                        cliEnv.readDir(p).forEach {
                            enumerateFiles(it)
                        }
                    } else {
                        console.log("$p")
                    }
                }
                enumerateFiles(FilePath.emptyPath)
            }
        }
    }

    // For speed, only install if it seems we didn't already have an installation. Can shave 1-2 seconds.
    if (needsNewPackageJson || !cliEnv.fileExists(dirPath(subDirName, "node_modules"))) {
        // TODO(logging) figure out where to put the npm install logs (if anywhere)
        val npmArgs = buildList {
            add("install")
            if (installShouldFailFast) {
                addAll(installFailFastNpmArgs)
            }
        }
        val installCommand = Command(
            npmArgs,
            cwd = generatedPackageJson.dirName(),
            reproduce = mapOf(Advice.Step to "installing the node libraries"),
        )
        val installResult = npmTool.run(installCommand)
        // TODO: should this be logging to cliConsole from caller
        installResult.print(con = console, successLevel = Log.Fine)
        if (installResult is RFailure) { return listOf(ToolchainResult(result = installResult)) }
    }
    cliEnv.announce(CliEnv.Checkpoint.postInstall)

    return when (request) {
        is RunTestsRequest -> libraryNames!!.mapNotNull { libraryName ->
            // TODO Actually introspect package.json in each.
            // TODO Even better would be: `npm run test --workspaces --if-present` in the top dir.
            // TODO Except we'd have to retrofit our reporting here on the currently single aux file for that.
            // Js library dirs are always named to match the temper library name, whatever the js package name.
            val testDir = dirPath(subDirName).resolveDir(libraryName.text)
            if (cliEnv.fileExists(testDir.resolveDir("test"))) {
                val testCommand = Command(
                    args = listOf("test"),
                    cwd = testDir,
                    aux = mapOf(Aux.JunitXml to testDir.resolveFile("test-results.xml")),
                )
                testCommand.maybeLogBeforeRunning(npmTool, cliEnv.shellPreferences)
                ToolchainResult(
                    libraryName = libraryName,
                    result = npmTool.run(testCommand),
                )
            } else {
                null
            }
        }

        is ExecInteractiveRepl -> {
            val (tool, command) = runInteractiveReplAndHalt(
                cliEnv = cliEnv,
                dependencies = dependencies,
                generatedPackageJson = generatedPackageJson,
                config = request.config,
            )
            listOf(
                ToolchainResult(
                    result = RFailure(
                        CliFailure(
                            message = "execve of ${tool.command} failed",
                            effort = Effort(command = tool.specify(command), cliEnv = cliEnv),
                        ),
                    ),
                ),
            )
        }
        else -> {
            val cmd = Command(
                args = listOf(generatedIndexFile.last().fullName),
                cwd = generatedIndexFile.dirName(),
                reproduce = mapOf(Advice.Step to "running the test itself"),
            )
            cmd.maybeLogBeforeRunning(nodeTool, cliEnv.shellPreferences)
            listOf(ToolchainResult(libraryName = mainLibraryConfig?.libraryName, result = nodeTool.run(cmd)))
        }
    }.also { results ->
        if (results.any { it.result is RFailure }) {
            cliEnv.maybeFreeze()
        }
    }
}

private fun runInteractiveReplAndHalt(
    cliEnv: CliEnv,
    dependencies: Dependencies<JsBackend>,
    generatedPackageJson: FilePath,
    config: ConfigFromCli,
): Pair<CliTool, Command> {
    ignore(config) // TODO: use it to maybe pick a tool.
    val tool: CliTool = cliEnv[NodeCommand]
    val command = Command(
        cwd = generatedPackageJson.dirName(),
        args = buildList {
            // Treat --eval as module content which allows top-level await.
            add("--input-type=module")
            // For each library, add an entry to globalThis with its export namespace
            val preloadScript = buildString {
                val identifiersAdded = mutableSetOf<Pair<DashedIdentifier, String>>()
                for ((libraryName) in dependencies.libraryConfigurations.byLibraryName) {
                    if (libraryName.isStdLib) {
                        continue
                    }
                    // add --import or --require for each library.
                    val jsLibraryName = dependencies.metadata[libraryName, JsMetadataKey.JsLibraryName]
                    if (jsLibraryName != null) {
                        var libNameAsJsIdentifier = IdentStyle.Dash.convertTo(IdentStyle.Camel, jsLibraryName)
                        if (libNameAsJsIdentifier in jsReservedWordsAndNames) {
                            libNameAsJsIdentifier = "${libNameAsJsIdentifier}Module"
                        }
                        val libNameAsQuotedString = stringTokenText(jsLibraryName)
                        append("globalThis.$libNameAsJsIdentifier = await import($libNameAsQuotedString);")

                        identifiersAdded.add(libraryName to libNameAsJsIdentifier)
                    }
                }
                // Dump some version info
                append("console.log(`Running \${process.execPath} \${process.version}`);")
                // Tell the user about the goodies they've got.
                val startupMessage = if (identifiersAdded.isEmpty()) {
                    "No modules pre-loaded"
                } else {
                    buildString {
                        append("REPL is running with these Temper libraries pre-loaded:\n")
                        append(
                            TextTable(
                                buildList {
                                    add(
                                        listOf(
                                            MultilineOutput.of("Library Name"),
                                            MultilineOutput.of("JS identifier"),
                                        ),
                                    )
                                    identifiersAdded.forEach { (libraryName, jsIdentifier) ->
                                        add(
                                            listOf(
                                                MultilineOutput.of(libraryName.text),
                                                MultilineOutput.of(jsIdentifier),
                                            ),
                                        )
                                    }
                                },
                            ),
                        )
                    }
                }
                append("console.log(${stringTokenText(startupMessage)});")
                append("console.log(`Type \".help\" for more information.`);")
                // Start the REPL now that the namespaces have been set up.
                append("(await import(\"node:repl\")).start({ useGlobal: true });")
            }
            add("--eval")
            add(preloadScript)
        },
    )
    command.maybeLogBeforeRunning(tool, cliEnv.shellPreferences)
    tool.runAsLast(command)
    return tool to command // So we can print a diagnostic on why exec failed.
}

private val installFailFastNpmArgs = listOf(
    "--fetch-retries",
    "$INSTALL_FF_FETCH_RETRIES",
    "--fetch-retry-mintimeout",
    "$INSTALL_FF_RETRY_PAUSE_MILLIS",
    "--fetch-retry-maxtimeout",
    "$INSTALL_FF_RETRY_PAUSE_MILLIS",
    "--fetch-timeout",
    "$INSTALL_FF_TIMEOUT_MILLIS",
)
