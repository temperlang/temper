package lang.temper.cli.repl

import lang.temper.be.cli.ShellPreferences
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.MultilineOutput
import lang.temper.common.TextTable
import lang.temper.common.temperEscaper
import lang.temper.common.withCapturingConsole
import lang.temper.env.Exporter
import lang.temper.frontend.Module
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.library.AbstractLibraryConfigurations
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.UNIX_FILE_SEGMENT_SEPARATOR
import lang.temper.log.bannedPathSegmentNames
import lang.temper.log.plus
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.tooling.buildrun.BuildDoneResult
import lang.temper.tooling.buildrun.BuildHarness
import lang.temper.tooling.buildrun.doBuild
import lang.temper.tooling.buildrun.withTempDir
import lang.temper.value.Helpful
import java.nio.file.Path
import java.util.concurrent.ExecutorService

/**
 * Compiles std/ so that the REPL can link to it.
 * TODO: adapt this so that if we invoke `temper repl` with a work root,
 * and also compile any libraries there, so that we can use the repl to
 * debug a library we're working on.
 */
internal class ModulesExternalToRepl(
    private val console: Console,
    private val logSink: LogSink,
    private val executorService: ExecutorService,
    private val workRootInfo: WorkRootInfo?,
) {

    operator fun get(importSpecifier: PendingImportForRepl): Exporter? {
        val modules = getModulesBuildingIfNecessary()
        val specifierText = importSpecifier.resolvedModuleSpecifier.text
        // Find first slash so that we can segment `my-library-name/relative/path/to/module.temper`.
        val slash = specifierText.indexOf(UNIX_FILE_SEGMENT_SEPARATOR)
        val (libraryNameText, relPath) = if (slash < 0) {
            specifierText to FilePath.emptyPath
        } else {
            val segments = specifierText.substring(slash + 1)
                .split(UNIX_FILE_SEGMENT_SEPARATOR).filter {
                    it.isNotEmpty()
                }
                .toMutableList()
            if (segments.any { it in bannedPathSegmentNames }) {
                logSink.log(
                    Log.Error,
                    MessageTemplate.MalformedImportPathSegment,
                    importSpecifier.specifierPos,
                    listOf(segments.first { it in bannedPathSegmentNames }, specifierText),
                )
                return null
            }
            specifierText.substring(0, slash) to
                FilePath(
                    segments.map {
                        FilePathSegment(it)
                    },
                    isDir = true,
                )
        }
        val libraryName = DashedIdentifier.from(libraryNameText)
            ?: run {
                logSink.log(
                    Log.Error,
                    MessageTemplate.MalformedLibraryName,
                    importSpecifier.specifierPos,
                    listOf(libraryNameText),
                )
                return@get null
            }
        val libraryConfiguration = modules.libraryConfigurations.byLibraryName[libraryName]
            ?: run {
                logSink.log(
                    Log.Error,
                    MessageTemplate.MissingLibrary,
                    importSpecifier.specifierPos,
                    listOf(libraryNameText),
                )
                return@get null
            }
        val wantedName = ModuleName(
            sourceFile = libraryConfiguration.libraryRoot + relPath,
            libraryRootSegmentCount = libraryConfiguration.libraryRoot.segments.size,
            // We're not using this mechanism to have a module instance implicitly import its preface.
            isPreface = false,
        )

        return modules.moduleMap[wantedName]
    }

    operator fun get(loc: ModuleLocation) = cachedModules?.moduleMap?.get(loc)

    val libraryConfigurations get() = cachedModules?.libraryConfigurations

    private var cachedModules: Modules? = null

    @Synchronized
    internal fun getModulesBuildingIfNecessary(): Modules {
        val precomputed = cachedModules
        if (precomputed != null) {
            return precomputed
        }

        console.log("Building external libraries for import", Log.Fine)
        fun buildIt(workRootInfo: WorkRootInfo, buildConsole: Console): BuildDoneResult? {
            val buildResult = doBuild(
                executorService = executorService,
                backends = emptyList(),
                workRoot = workRootInfo.workRoot,
                ignoreFile = workRootInfo.ignoreFile,
                shellPreferences = ShellPreferences.default(buildConsole),
                // TODO: With the Build rebuilding as necessary, we could load this on demand
                // instead of requiring up front, but it's nice to be able to use our startup
                // time instead of incurring latency on demand.
                requiredExt = listOf(DashedIdentifier.temperStandardLibraryIdentifier),
                moduleConfig = mayRunModuleConfig,
                // Staging leaves modules at Stage.GenerateCode
                // Before closing the build result, which cancels the continue condition
                // for the build object, we need to advance modules to the run stage.
                beforeClose = { tentativeBuildResult ->
                    if (tentativeBuildResult is BuildDoneResult) {
                        tentativeBuildResult.modulesInOrder.forEach { module ->
                            if (module.canAdvance()) {
                                module.advance()
                            }
                        }
                    }
                },
            )
            if (!buildResult.ok) {
                buildConsole.warn("Building ran into problems which may affect code executed in the playground")
            }
            return buildResult as? BuildDoneResult
        }
        val result: BuildDoneResult? = if (workRootInfo != null) {
            buildIt(workRootInfo, console)
        } else {
            withTempDir("build-modules-for-repl") { workRoot ->
                withCapturingConsole { buildConsole ->
                    buildIt(WorkRootInfo(workRoot, null), buildConsole)
                }
            }.first
        }

        val libraries = result?.partitionedModules ?: emptyList()
        val libraryConfigurations = LibraryConfigurationsBundle.from(libraries.map { it.first })
        val modules = Modules(
            libraries.flatMap { it.second }.associateBy { it.loc },
            libraryConfigurations,
        )

        cachedModules = modules
        return modules
    }

    internal data class Modules(
        val moduleMap: Map<ModuleLocation, Module>,
        val libraryConfigurations: AbstractLibraryConfigurations,
    )
}

private val mayRunModuleConfig = ModuleConfig(mayRun = true)

internal data class WorkRootInfo(
    /** The native path to the work root.  Used to populate [BuildHarness.workFileSystem] */
    val workRoot: Path,
    /** A .gitignore formatted description of files under [workRoot] to ignore. */
    val ignoreFile: Path?,
)

internal data class AvailableImports(
    private val modulesExternalToRepl: ModulesExternalToRepl,
) : Helpful {
    override fun briefHelp(): String = "List of modules available for import"
    override fun longHelp(): String {
        val modules = modulesExternalToRepl.getModulesBuildingIfNecessary()
        val libraryNames = modules.libraryConfigurations.byLibraryName.entries.sortedBy { it.key }
        val modulesGrouped = modules.moduleMap.values.filter {
            val loc = it.loc
            loc is ModuleName && loc.relativePath().lastOrNull() != LibraryConfiguration.fileName
        }.groupBy {
            (it.loc as ModuleName).libraryRoot()
        }
        val rows = buildList {
            for ((libraryName, config) in libraryNames) {
                val modulesInLibrary = (modulesGrouped[config.libraryRoot] ?: listOf())
                    .sortedBy { it.loc }
                val libraryNameCell = MultilineOutput.of("$libraryName")
                if (modulesInLibrary.isNotEmpty()) {
                    for ((i, module) in modulesInLibrary.withIndex()) {
                        val left = if (i == 0) libraryNameCell else MultilineOutput.Empty
                        val moduleName = module.loc as ModuleName
                        val moduleNameCell = MultilineOutput.of("${moduleName.relativePath()}")
                        var relPathNoSlash = "${moduleName.relativePath()}"
                        if (relPathNoSlash.endsWith("/") && relPathNoSlash.length > 1) {
                            // We encourage import style where the trailing / is not part of the import
                            // string.
                            relPathNoSlash = relPathNoSlash.dropLast(1)
                        }
                        val importString = "$libraryName/$relPathNoSlash"
                        val importCell = MultilineOutput.of(
                            """let {...} = import(${temperEscaper.escape(importString)});""",
                        )
                        add(listOf(left, moduleNameCell, importCell))
                    }
                } else {
                    // Since we filter out config.temper.md above, this can happen for libraries that
                    // only have a config file.  Don't judge; everyone has to start somewhere.
                    add(listOf(libraryNameCell, MultilineOutput.Empty, MultilineOutput.Empty))
                }
            }
        }
        val textTable = TextTable(
            listOf("library", "module", "import").map { MultilineOutput.of(it) },
            rows,
        )
        return buildString {
            append("Here are the available modules.\nYou can copy/paste the import statements.\n")
            textTable.toStringBuilder(this)
        }
    }

    companion object {
        const val NAME = "available-imports"
    }
}
