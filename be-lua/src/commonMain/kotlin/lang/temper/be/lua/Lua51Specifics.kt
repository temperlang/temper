package lang.temper.be.lua

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.Command
import lang.temper.be.cli.Effort
import lang.temper.be.cli.EffortBase
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.ExecInteractiveRepl
import lang.temper.be.cli.RunBackendSpecificCompilationStepRequest
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.cli.ToolSpecifics
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.VersionedTool
import lang.temper.be.cli.composing
import lang.temper.common.MultilineOutput
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.TextTable
import lang.temper.common.console
import lang.temper.common.invoke
import lang.temper.common.subListToEnd
import lang.temper.fs.OutDir
import lang.temper.fs.declareResources
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.log.plus
import lang.temper.log.resolveFile
import lang.temper.name.BackendId

class LuaLikeTool(val name: String) : VersionedTool {
    override val cliNames: List<String>
        get() = listOf(name)

    override val versionCheckArgs: List<String>
        get() = listOf("-v")

    override fun checkVersion(run: EffortSuccess): RResult<Unit, CliFailure> = RSuccess(Unit)
}

/** The path segments but skipping over the initial `lua` path segment. */
val FilePath.goodFileSegments: List<FilePathSegment>
    get() {
        var skipped = 0
        if (segments.getOrNull(skipped)?.fullName == LuaLang.Lua51.id.uniqueId) { skipped += 1 }
        return segments.subListToEnd(skipped)
    }
val FilePath.goodFilePath: FilePath
    get() = FilePath(segments = goodFileSegments, isDir = isDir)

object Lua51Specifics : RunnerSpecifics {
    private val LuaTool = LuaLikeTool("lua")

    override fun runSingleSource(
        cliEnv: CliEnv,
        code: String,
        env: Map<String, String>,
        aux: Map<Aux, FilePath>,
    ): RResult<EffortSuccess, CliFailure> =
        cliEnv.composing(this) {
            val composedEnv = this
            composedEnv.write(
                code,
                filePath("__CODE__.lua"),
            )
            val lua = composedEnv[LuaTool]
            val command = Command(
                args = listOf(
                    "__CODE__.lua",
                ),
                cwd = FilePath.emptyPath,
                env = mapOf(
                    "LUA_PATH" to LUA_PATH,
                ) + env,
                aux = aux,
            )
            lua.run(command)
        }

    override fun runBestEffort(
        cliEnv: CliEnv,
        request: ToolchainRequest,
        code: OutDir,
        dependencies: Dependencies<*>,
    ): List<ToolchainResult> {
        @Suppress("UNCHECKED_CAST")
        dependencies as Dependencies<LuaBackend>

        val res = mutableListOf<ToolchainResult>()
        cliEnv.composing(this) {
            val composedEnv = this
            val backendDir = dirPath(LuaBackend.Lua51.backendId.uniqueId)
            when (request) {
                is RunBackendSpecificCompilationStepRequest -> TODO()
                is RunLibraryRequest -> {
                    val library = request.libraryName
                    val path = dependencies.metadata[library, LuaMetadataKey.MainFilePath]
                        ?: return@runBestEffort listOf(
                            ToolchainResult(result = RFailure(CliFailure("No lua main path for $library"))),
                        )
                    val lua = composedEnv[LuaTool]
                    val stderrPath = filePath("stderr.log")
                    val command = Command(
                        args = listOf(
                            "$path",
                        ),
                        cwd = backendDir,
                        env = mapOf(
                            "LUA_PATH" to LUA_PATH,
                        ),
                        aux = mapOf(
                            Aux.Stderr to stderrPath,
                        ),
                    )
                    val runEffort = lua.run(command)
                    runEffort { (_, f) ->
                        if (f != null) {
                            print(this.readFile(stderrPath))
                        }
                    }
                    res.add(ToolchainResult(libraryName = library, result = runEffort))
                }

                is RunTestsRequest -> {
                    copyLuaUnit()
                    request.libraries?.zip(request.testFileGroups)?.forEach { (libraryName, group) ->
                        group.outputTestFiles.forEach files@{ filePath ->
                            filePath.segments.firstOrNull()?.fullName == LUA_TESTS_DIR || return@files
                            val path = (group.outputFileRoot + filePath).goodFilePath
                                .withTemperAwareExtension(".lua")
                            val lua = composedEnv[LuaTool]
                            val testLogPath = backendDir.resolveFile("test-results.xml")
                            val stderrPath = backendDir.resolveFile("stderr.log")
                            val TEMPER_LUA_DEBUG_FUNCS = false
                            this.write("", testLogPath)
                            val command = Command(
                                args = listOf(
                                    "-e",
                                    "TEMPER_LUA_DEBUG_FUNCS=$TEMPER_LUA_DEBUG_FUNCS",
                                    "$path",
                                    "-o",
                                    "junit",
                                    "-n",
                                    testLogPath.last().fullName,
                                ),
                                cwd = backendDir,
                                env = mapOf(
                                    "LUA_PATH" to LUA_PATH,
                                ),
                                aux = mapOf(
                                    Aux.JunitXml to testLogPath,
                                    Aux.Stderr to stderrPath,
                                ),
                            )
                            val runEffort = lua.run(command)
                            runEffort { (_, failure) ->
                                if (failure != null) {
                                    print(this.readFile(stderrPath))
                                }
                            }
                            val testLogText = this.readFile(testLogPath)
                            if (testLogText.isNotEmpty()) {
                                runEffort { (result, failure) ->
                                    when {
                                        result != null ->
                                            RFailure(CliFailure(testLogText, null, adjustJunitXml(result)))
                                        failure != null ->
                                            RFailure(failure)
                                        else -> null
                                    }
                                }
                            } else {
                                runEffort
                            }?.let { res.add(ToolchainResult(libraryName = libraryName, result = it)) }
                        }
                    }
                }

                is ExecInteractiveRepl -> {
                    val lua = composedEnv[LuaTool]
                    val libraryTable = TextTable(
                        buildList {
                            add(
                                listOf(
                                    MultilineOutput.of("Temper library"),
                                    MultilineOutput.of("Lua require"),
                                ),
                            )
                            dependencies.libraryConfigurations.byLibraryName.keys.mapTo(this) {
                                listOf(
                                    MultilineOutput.of(it.text),
                                    MultilineOutput.of("require(${stringTokenText(it.text)})"),
                                )
                            }
                        },
                    )
                    val message = buildString {
                        append("Starting lua interactive shell.\n")
                        append("Use these requires to access translations.\n")
                        libraryTable.toStringBuilder(this)
                    }
                    cliEnv.shellPreferences.console.log(message)
                    val command = Command(
                        args = listOf(),
                        cwd = backendDir,
                        env = mapOf(
                            "LUA_PATH" to LUA_PATH,
                        ),
                    )
                    lua.runAsLast(command)
                }
            }
        }
        return res
    }

    private fun CliEnv.copyLuaUnit() {
        val sub = dirPath("lua", "luaunit")
        val resources = this@Lua51Specifics.declareResources(
            dirPath("lang", "temper", "be").resolve(sub),
            filePath("init.lua"),
            filePath("LICENSE.txt"),
        )
        for (resource in resources) {
            write(resource.load(), sub.resolve(resource.rsrcPath))
        }
    }

    override val tools: List<ToolSpecifics>
        get() = listOf()
    override val backendId: BackendId
        get() = LuaLang.Lua51.id
}

const val LUA_PATH = "?.lua;?/$INIT_NAME"

private val failureMessageRegex = Regex("""type="[^:]+:\d+: """)

private fun adjustJunitXml(result: EffortSuccess): EffortBase {
    val effort = (result as? Effort) ?: return result
    val xml = result.auxOut[Aux.JunitXml] ?: return result
    // Match other backend test runner output. Just hack string replacement for now.
    // TODO Actually parse the xml. Adjust error elements as well as failure?
    // Example from luaunit: <failure type="test-me/tests/test-test.lua:7: Failed as expected">...
    // Example from python unittest: <failure message="Failed as expected">...
    val simplified = xml.replace(failureMessageRegex, "message=\"")
    // Use explicit copy instead of withAux, so can override the existing aux.
    return effort.copy(auxOut = effort.auxOut + mapOf(Aux.JunitXml to simplified))
}
