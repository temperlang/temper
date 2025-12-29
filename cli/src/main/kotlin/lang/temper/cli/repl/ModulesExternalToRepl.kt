package lang.temper.cli.repl

import lang.temper.be.cli.ShellPreferences
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.withCapturingConsole
import lang.temper.frontend.Module
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.interp.importExport.Exporter
import lang.temper.library.AbstractLibraryConfigurations
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
import lang.temper.tooling.buildrun.doBuild
import lang.temper.tooling.buildrun.withTempDir
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
    private fun getModulesBuildingIfNecessary(): Modules {
        val precomputed = cachedModules
        if (precomputed != null) { return precomputed }

        console.log("Building external libraries for import", Log.Fine)
        val (result: BuildDoneResult?) = withTempDir("build-modules-for-repl") { workRoot ->
            withCapturingConsole { buildConsole ->
                doBuild(
                    executorService = executorService,
                    backends = emptyList(),
                    workRoot = workRoot,
                    ignoreFile = null,
                    shellPreferences = ShellPreferences.default(buildConsole),
                    // TODO: With the Build rebuilding as necessary, we could load this on demand
                    // instead of requiring up front, but it's nice to be able to use our startup
                    // time instead of incurring latency on demand.
                    requiredExt = listOf(DashedIdentifier.temperStandardLibraryIdentifier),
                    moduleConfig = mayRunModuleConfig,
                ) as? BuildDoneResult
            }
        }

        val libraries = result?.partitionedModules ?: emptyList()
        // Staging leaves modules at Stage.GenerateCode
        result?.modulesInOrder?.forEach { module ->
            if (module.canAdvance()) {
                module.advance()
            }
        }
        val libraryConfigurations = LibraryConfigurationsBundle.from(libraries.map { it.first })
        val modules = Modules(
            libraries.flatMap { it.second }.associateBy { it.loc },
            libraryConfigurations,
        )

        cachedModules = modules
        return modules
    }

    private data class Modules(
        val moduleMap: Map<ModuleLocation, Module>,
        val libraryConfigurations: AbstractLibraryConfigurations,
    )
}

private val mayRunModuleConfig = ModuleConfig(mayRun = true)
