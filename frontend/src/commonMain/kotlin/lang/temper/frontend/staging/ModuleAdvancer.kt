package lang.temper.frontend.staging

import lang.temper.common.Cons
import lang.temper.common.Console
import lang.temper.common.CustomValueFormatter
import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.common.Snapshotter
import lang.temper.common.buildListMultimap
import lang.temper.common.compatReversed
import lang.temper.common.console
import lang.temper.common.partiallyOrder
import lang.temper.common.putMultiList
import lang.temper.env.InterpMode
import lang.temper.format.ConsoleBackedContextualLogSink
import lang.temper.frontend.Module
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.frontend.implicits.accessStdWrapped
import lang.temper.fs.FileFilterRules
import lang.temper.fs.FileSnapshot
import lang.temper.fs.FileSystemSnapshot
import lang.temper.fs.FilteringFileSystemSnapshot
import lang.temper.interp.importExport.Exporter
import lang.temper.interp.importExport.ImportMacro
import lang.temper.interp.importExport.Importer
import lang.temper.interp.importExport.LOCAL_FILE_SPECIFIER_PREFIX
import lang.temper.interp.importExport.STANDARD_LIBRARY_FILEPATH
import lang.temper.interp.importExport.STANDARD_LIBRARY_SPECIFIER_PREFIX
import lang.temper.interp.importExport.createLocalBindingsForImport
import lang.temper.interp.importExport.toValue
import lang.temper.lexer.Genre
import lang.temper.lexer.defaultClassifyTemperSource
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurationLocationKey
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.log.CodeLocation
import lang.temper.log.CodeLocationKey
import lang.temper.log.ConfigurationKey
import lang.temper.log.Debug
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.isWithin
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePositions
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.SharedLocationContext
import lang.temper.log.UNIX_FILE_SEGMENT_SEPARATOR
import lang.temper.log.unknownPos
import lang.temper.name.DashedIdentifier
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.LibraryNameLocationKey
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.stage.Stage
import lang.temper.type2.Signature2
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.StayLeaf
import lang.temper.value.StaySink
import lang.temper.value.TProblem
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.errorFn
import lang.temper.value.fileRestrictedBuiltinName
import lang.temper.value.valueContained
import lang.temper.value.void
import java.io.IOException

/** Makes an effort to resolve an imported specifier to an exporter. */
interface ImportResolver {
    fun lookup(specifier: String, collector: ModuleCollector): Exporter?

    fun lookupLibrary(
        libraryName: DashedIdentifier,
        collector: ModuleCollector,
    ): LibraryConfiguration? = null

    object ImportNone : ImportResolver {
        override fun lookup(
            specifier: String,
            collector: ModuleCollector,
        ): Nothing? = null
    }
}

fun interface ModuleCustomizeHook {
    fun customize(module: Module, isNew: Boolean)

    object CustomizeNothing : ModuleCustomizeHook {
        override fun customize(module: Module, isNew: Boolean) {
            // do nothing
        }
    }
}

/**
 * May be called into by an [ImportResolver] to add modules to the set of needed
 * modules on demand and to register any needed library configurations.
 */
interface ModuleCollector {
    fun addModule(module: Module)

    /** @return true when the library was not previously configured. */
    fun configureLibrary(libraryConfiguration: LibraryConfiguration): Boolean

    /**
     * May replace a previous configuration for a library as when a tentative library name is
     * replaced with one found by running the [LibraryConfiguration.fileName] file.
     */
    fun reconfigureLibrary(libraryConfiguration: LibraryConfiguration)

    /** @return true when the library was not previously configured. */
    fun configureLibrary(
        libraryName: DashedIdentifier,
        libraryRoot: FilePath,
    ) = configureLibrary(
        LibraryConfiguration(
            libraryName = libraryName,
            libraryRoot = libraryRoot,
            supportedBackendList = listOf(),
            classifyTemperSource = ::defaultClassifyTemperSource,
        ),
    )
}

/**
 * Allows creating and advancing modules for unit tests where later modules may import
 * modules that were advanced earlier.
 *
 * Use *ModuleBuilder* for anything production related.
 */
class ModuleAdvancer(
    /**
     * Log sink for non-module specific errors
     * and for modules created by [createModule].
     */
    var projectLogSink: LogSink,
    /**
     * Called to resolve imports that are not local path imports.
     * This is not called for std.  As a convenience and to avoid slowing down
     * tests, std modules are compiled once and cached globally.
     */
    private val nonLocalImportResolver: ImportResolver = ImportResolver.ImportNone,
    /** Affects modules created by [createModule] */
    val moduleConfig: ModuleConfig = ModuleConfig.default,
) : ModuleCollector {
    private val modules = mutableListOf<Module>()
    private val pathToModule = mutableMapOf<FilePath, Module>()
    private val locToModule = mutableMapOf<ModuleLocation, Module>()
    private val libraryConfigurations = MutLibraryConfigurations()
    private val requiredLibraries = mutableSetOf<DashedIdentifier>()

    /** Allow fetching content for error messages from disk */
    private val contentForSource = mutableMapOf<FilePath, () -> ContentLookupResult?>()

    /** Avoid repeatedly warn that a file has changed on disk so error snippet might be skewed */
    private val skewedContentWarnings = mutableSetOf<FilePath>()

    /**
     * Specifies the configuration for the library at its root.
     * Does not clobber previously given configurations for the same library root.
     *
     * If the advancer advances a configuration file to export stage, then it will
     * overwrite this configuration returned by [getAllLibraryConfigurations].
     */
    override fun configureLibrary(libraryConfiguration: LibraryConfiguration): Boolean =
        libraryConfigurations.add(libraryConfiguration, replace = false)

    /** Like [configureLibrary] but clobbers previous configurations for the same library root. */
    override fun reconfigureLibrary(libraryConfiguration: LibraryConfiguration) {
        libraryConfigurations.add(libraryConfiguration, replace = true)
    }

    /**
     * Require a named, external library even if there is no dependency from the current
     * modules to it.  This is useful for pre-building a bundle of modules starting with
     * libraries required by a REPL before the REPL user actually tries to use them.
     *
     * Required libraries are passed to [nonLocalImportResolver]'s
     * [ImportResolver.lookupLibrary] before staging starts.
     */
    fun requireLibrary(libraryName: DashedIdentifier) {
        requiredLibraries.add(libraryName)
    }

    /** Add a module to advance */
    override fun addModule(module: Module) {
        modules.add(module)
        val loc = module.loc
        check(loc !in locToModule)
        locToModule[loc] = module
        if (loc is FileRelatedCodeLocation) {
            val sourceFile = loc.sourceFile
            pathToModule[sourceFile] = module
        }
    }

    /**
     * Stores the source content associated with the given file path exposed by [sharedLocationContext].
     *
     * If content is [delivered][Module.deliverContent] as source, then this will be auto-extracted.
     */
    fun registerContentForSource(filePath: FilePath, content: CharSequence) {
        contentForSource[filePath] = { ContentLookupResult(content, false) }
    }

    /**
     * Stores the source content associated with the given file path exposed by [sharedLocationContext].
     *
     * If content is [delivered][Module.deliverContent] as source, then this will be auto-extracted.
     */
    fun registerContentForSource(filePath: FilePath, source: FileSystemSnapshot) {
        contentForSource[filePath] = {
            (source[filePath] as? FileSnapshot.AvailableFile)?.let { file ->
                ContentLookupResult(
                    content = file.content.decodeToString(),
                    isSkewed = file !is FileSnapshot.UpToDateFile,
                )
            }
        }
    }

    internal fun maybeRegisterContentForSource(module: Module) {
        for (source in module.sources) {
            val sourceFile = source.filePath
            if (sourceFile != null && sourceFile !in contentForSource) {
                val snapshot = source.snapshot
                val fetchedContent = source.fetchedContent
                if (snapshot != null) {
                    registerContentForSource(sourceFile, snapshot)
                } else if (fetchedContent != null) {
                    registerContentForSource(sourceFile, fetchedContent)
                }
            }
        }
    }

    fun createModule(
        loc: ModuleName,
        console: Console,
        continueCondition: () -> Boolean = makeContinueCondition(),
        mayRun: Boolean = moduleConfig.mayRun,
        allowDuplicateLogPositions: Boolean = false,
        genre: Genre = Genre.Library,
    ): Module {
        val module = Module(
            projectLogSink = moduleConfig.makeModuleLogSink(loc, projectLogSink),
            loc = loc,
            console = console,
            continueCondition = continueCondition,
            mayRun = mayRun,
            sharedLocationContext = sharedLocationContext,
            genre = genre,
            allowDuplicateLogPositions = allowDuplicateLogPositions,
        )
        addModule(module)
        return module
    }

    val sharedLocationContext = object : SharedLocationContext {
        private fun getSourceCode(loc: CodeLocation): CharSequence? = when (loc) {
            ImplicitsCodeLocation -> ImplicitsModule.code
            is FileRelatedCodeLocation -> {
                val sourceFile = loc.sourceFile
                contentForSource[sourceFile]?.invoke()?.let { contentResult ->
                    if (contentResult.isSkewed && sourceFile !in skewedContentWarnings) {
                        skewedContentWarnings.add(sourceFile)
                        projectLogSink.log(
                            Log.Warn, MessageTemplate.SkewedSourceContent,
                            Position(sourceFile, 0, 0),
                            emptyList(),
                        )
                    }
                    contentResult.content
                }
            }
            else -> null
        }

        override fun <T : Any> get(
            loc: CodeLocation,
            v: CodeLocationKey<T>,
        ): T? = when (v) {
            CodeLocationKey.SourceCodeKey -> getSourceCode(loc)?.let { v.cast(it) }
            CodeLocationKey.FilePositionsKey -> {
                (loc as? FileRelatedCodeLocation)?.sourceFile?.let { sourceFile ->
                    var filePositions: FilePositions? = null
                    for (p in sourceFile.ancestors(skipThis = false)) {
                        val m = pathToModule[p]
                        if (m != null) {
                            filePositions = m.filePositions[sourceFile]
                            if (filePositions != null) {
                                break
                            }
                        }
                    }
                    filePositions?.let { v.cast(it) }
                }
            }
            LibraryNameLocationKey ->
                this[loc, LibraryConfigurationLocationKey]
                    ?.libraryName
                    ?.let { v.cast(it) }
            LibraryConfigurationLocationKey -> {
                val pathInLibraryRoot = when (loc) {
                    is ModuleName -> loc.libraryRoot()
                    is FileRelatedCodeLocation -> loc.sourceFile
                    else -> null
                }
                pathInLibraryRoot?.let { sourcePath ->
                    val dir = if (sourcePath.isDir) sourcePath else sourcePath.dirName()
                    libraryConfigurations[dir]?.let { v.cast(it) }
                }
            }
            else -> null
        }
    }

    /** All modules [added][addModule] with exporters before importers. */
    fun getAllModules(): List<Module> = partiallyOrder(
        afterMap = buildMap {
            locToModule.values.forEach { module ->
                this[module] = buildSet {
                    module.importRecords.forEach { importRecord ->
                        if (importRecord is Importer.OkImportRecord) {
                            val exporter = locToModule[importRecord.exporterLocation]
                            if (exporter != null) {
                                this.add(exporter)
                            }
                        }
                    }
                }
            }
        },
    )

    /** Modules partitioned by library root. */
    fun getPartitionedModules(): Map<FilePath, Pair<LibraryConfiguration, List<Module>>> =
        partitionModulesIntoLibraries(
            locToModule.values.toList(),
            libraryConfigurations.map { it.libraryRoot },
        ) { libraryRoot ->
            libraryConfigurations[libraryRoot] ?: run {
                val tentativeConfiguration =
                    LibraryConfiguration(fallbackLibraryName(), libraryRoot, emptyList(), ::defaultClassifyTemperSource)
                check(configureLibrary(tentativeConfiguration))
                tentativeConfiguration
            }
        }.associateBy { it.first.libraryRoot }

    fun getAllLibraryConfigurations(): List<LibraryConfiguration> = libraryConfigurations.toList()
    fun getLibraryConfiguration(libraryRoot: FilePath) = libraryConfigurations[libraryRoot]

    fun advanceModules(stopBefore: Stage? = null) = advanceModules { stopBefore }

    fun advanceModules(
        /**
         * For each module, the stage to advance it to.
         * For stage tests, we want to advance a "main" module to a specific stage, but
         * other modules need to advance to at least [Stage.Export] to unblock the main
         * module, so this allows overriding for different modules.
         *
         * stopBefore is called for each should-advance check, so it should be a stable,
         * cheap, pure function.
         */
        stopBefore: (Module) -> Stage?,
    ) {
        // If any of the input modules are mayRun, then assume the caller is going
        // to want to run the std modules and don't reuse the non-may-run modules.
        val sharedStdModules = if (moduleConfig.mayRun || modules.any { it.mayRun }) {
            lazy {
                buildStdModules(
                    this,
                    console,
                    mayRun = true,
                )
            }
        } else {
            sharedStdModulesMayNotRun
        }

        val nonLocalLookup = object : ImportResolver {
            override fun lookup(specifier: String, collector: ModuleCollector): Exporter? {
                val resolver = if (specifier.startsWith(STANDARD_LIBRARY_SPECIFIER_PREFIX)) {
                    sharedStdModules.value
                } else {
                    nonLocalImportResolver
                }
                return resolver.lookup(specifier, collector)
            }

            override fun lookupLibrary(
                libraryName: DashedIdentifier,
                collector: ModuleCollector,
            ): LibraryConfiguration? {
                val resolver = if (libraryName == DashedIdentifier.temperStandardLibraryIdentifier) {
                    sharedStdModules.value
                } else {
                    nonLocalImportResolver
                }
                return resolver.lookupLibrary(libraryName, collector)
            }
        }

        for (libraryName in requiredLibraries) {
            nonLocalLookup.lookupLibrary(libraryName, this)?.let { libraryConfiguration ->
                configureLibrary(libraryConfiguration)
            }
        }

        val bySpecifier = buildMap {
            modules.forEach {
                val loc = it.loc
                if (loc is ModuleName) {
                    this["${LOCAL_FILE_SPECIFIER_PREFIX}${loc.sourceFile.join()}"] = it
                }
            }
        }

        val importHandler = ImportHandler(
            localLookup = object : ImportResolver {
                override fun lookup(specifier: String, collector: ModuleCollector): Exporter? =
                    bySpecifier[specifier]
            },
            nonLocalLookup = nonLocalLookup,
            logSink = projectLogSink,
            advancer = this,
        )

        val groupOfModulesToAdvanceTogether = GroupOfModulesToAdvanceTogether(
            modules = modules,
            importHandler = importHandler,
            advancer = this,
            moduleHook = moduleConfig.moduleCustomizeHook,
            snapshotter = moduleConfig.snapshotter,
            stopBefore = stopBefore,
        )
        groupOfModulesToAdvanceTogether.advanceModules()

        // Check that library names are unambiguous.
        val librariesByName = getAllLibraryConfigurations().groupBy { it.libraryName }
        for ((libraryName, libraries) in librariesByName) {
            if (libraries.size > 1) {
                projectLogSink.log(
                    level = Log.Fatal,
                    template = MessageTemplate.DuplicateLibraryName,
                    pos = unknownPos,
                    values = listOf(libraryName, libraries.map { it.libraryRoot }),
                )
            }
        }
    }
}

private const val STEP_QUOTA = 1_000 // Thousands

private class MutLibraryConfigurations : Iterable<LibraryConfiguration> {
    private val configurationsPossiblyUnsorted = mutableListOf<LibraryConfiguration>()
    private val roots = mutableSetOf<FilePath>()
    private var isSorted = true // since it's empty

    /** @return true if the configuration list changed. */
    fun add(configuration: LibraryConfiguration, replace: Boolean): Boolean {
        val libraryRoot = configuration.libraryRoot
        return when {
            libraryRoot !in roots -> {
                configurationsPossiblyUnsorted.add(configuration)
                roots.add(libraryRoot)
                isSorted = false
                true
            }
            replace -> {
                val index = configurationsPossiblyUnsorted.indexOfFirst { it.libraryRoot == libraryRoot }
                configurationsPossiblyUnsorted[index] = configuration
                true
            }
            else -> false
        }
    }

    override fun iterator(): Iterator<LibraryConfiguration> {
        requireSorted()
        return configurationsPossiblyUnsorted.iterator()
    }

    operator fun get(filePath: FilePath): LibraryConfiguration? {
        requireSorted()
        for (configuration in configurationsPossiblyUnsorted) {
            if (filePath.isWithin(configuration.libraryRoot)) {
                return configuration
            }
        }
        return null
    }

    /**
     * Ensures configuration are ordered with the deepest library root first so
     * that we can find the deepest library root containing by iterating
     * left to right.
     */
    private fun requireSorted() {
        if (isSorted) { return }
        configurationsPossiblyUnsorted.sortWith { a, b ->
            b.libraryRoot.segments.size - a.libraryRoot.segments.size
        }
        isSorted = true
    }
}

private data class PendingImport(
    val importer: Importer,
    val exporter: Exporter,
    val stayLeaf: StayLeaf,
    val specifierText: String,
    val logSink: LogSink,
)

private class GroupOfModulesToAdvanceTogether(
    val modules: List<Module>,
    val importHandler: ImportHandler,
    val advancer: ModuleAdvancer,
    val moduleHook: ModuleCustomizeHook = ModuleCustomizeHook.CustomizeNothing,
    val snapshotter: Snapshotter? = null,
    val stopBefore: (Module) -> Stage? = { null },
) {
    // Which modules to un-block (by decrementing countNeeded) when the key reaches Stage,Export
    private val awaitingExportStage = mutableMapOf<Exporter, MutableList<PendingImport>>()

    // Which imports to rewrite when countNeeded drops to zero.
    private val pendingByImporter = mutableMapOf<Importer, MutableList<PendingImport>>()

    // The count of modules that need to reach Stage.Export before the key can advance
    private val countNeeded = mutableMapOf<Importer, Int>()

    /**
     * For each preface module name, the modules that implicitly need that preface to export before
     * they can advance.
     */
    private val modulesByPreface: Map<ModuleName, List<Module>>

    /** For each key in [modulesByPreface], the preface module */
    private val prefacesProvided: Map<ModuleName, Module>

    init {
        for (m in modules) {
            countNeeded[m] = 0
        }
        prefacesProvided = buildMap {
            for (module in modules) {
                val loc = module.loc
                if (loc is ModuleName && loc.isPreface) {
                    this[loc] = module
                }
            }
        }
        modulesByPreface = buildListMultimap {
            for (module in modules) {
                val loc = module.loc
                if (loc is ModuleName) {
                    if (!loc.isPreface) {
                        val prefaceLoc = loc.copy(isPreface = true)
                        if (prefaceLoc in prefacesProvided) {
                            this.putMultiList(prefaceLoc, module)
                        }
                    }
                }
            }
        }

        // Any non-preface modules that have a preface present need an incremented
        // count to account for that.
        modulesByPreface.forEach { (_, nonPrefaceModules) ->
            nonPrefaceModules.forEach { nonPrefaceModule ->
                countNeeded[nonPrefaceModule] = countNeeded.getValue(nonPrefaceModule) + 1
            }
        }
    }

    fun advanceModules() {
        // Prepare modules for import tracking
        for (module in modules) {
            if (module.stageCompleted == null) {
                // We haven't previously added bindings here.
                module.addEnvironmentBindings(
                    mapOf(
                        fileRestrictedBuiltinName to
                            (module.loc as FileRelatedCodeLocation).sourceFile.toValue(),
                    ),
                )
                module.useFeatures(
                    mapOf(ImportMacro.IMPORT_PENDING_FEATURE_KEY to Value(importHandler)),
                )

                advancer.maybeRegisterContentForSource(module)
            }
        }

        val readyToAdvance = ArrayDeque(modules)
        while (true) {
            val m = readyToAdvance.removeFirstOrNull()
                ?: if (tryBreakCycle(readyToAdvance)) {
                    // If nothing was ready because of an import cycle, try again.
                    continue
                } else {
                    break
                }

            val stopBeforeForCurrentModule = stopBefore(m)
            val lastStageCompleted = m.stageCompleted
            val shouldAdvance = when {
                !m.canAdvance() -> false // Cannot be advanced
                countNeeded[m] != 0 -> false // Still waiting on imports
                (
                    stopBeforeForCurrentModule != null &&
                        lastStageCompleted != null &&
                        Stage.after(lastStageCompleted) == stopBeforeForCurrentModule
                    ) -> false // Reached limit
                else -> true
            }

            if (shouldAdvance) {
                val isNew = m.stageCompleted == null
                moduleHook.customize(m, isNew = isNew)
                Stage.after(m.stageCompleted)?.let { nextStage ->
                    m.projectLogSink.log(
                        Log.Fine,
                        MessageTemplate.StartingStage,
                        Position(m.loc, 0, 0),
                        listOf(nextStage),
                    )
                }

                withSnapshotter(m, m.console, snapshotter) {
                    m.advance()
                }

                val importsReadyNow = importHandler.consumeNewImports()
                    .filter { pendingImport ->
                        val importer = pendingImport.importer
                        val exporter = pendingImport.exporter
                        val exporterStage = (exporter as? Module)?.stageCompleted
                        val isReadyNow = exporterStage != null && exporterStage >= Stage.Export
                        if (!isReadyNow) {
                            countNeeded[importer] = countNeeded.getValue(importer) + 1
                            pendingByImporter.putMultiList(importer, pendingImport)
                            awaitingExportStage.putMultiList(exporter, pendingImport)
                        }
                        isReadyNow
                    }
                importHandler.doImports(importsReadyNow)
                if (m.stageCompleted == Stage.Export) {
                    val satisfiedImports = awaitingExportStage[m] ?: emptyList()
                    for (satisfiedImport in satisfiedImports) {
                        val importer = satisfiedImport.importer
                        decrementCountFor(importer, readyToAdvance)
                    }
                    val loc = m.loc
                    // Handle the implicit import relationship between non-preface modules and
                    // their prefaces.
                    if (
                        loc is ModuleName && loc.isPreface &&
                        // Don't decrement twice in the case that the module list has
                        // two modules with the same name.
                        m === prefacesProvided[loc]
                    ) {
                        this.modulesByPreface[loc]?.forEach { waiting ->
                            check(waiting.outer == null)
                            waiting.outer = m
                            decrementCountFor(waiting, readyToAdvance)
                        }
                    }
                    // If a config module is ready to export, use its exports
                    // to finalize the library configuration.
                    if (m.isConfigModule) {
                        val libraryRoot = (loc as ModuleName).libraryRoot()
                        val oldConfig = advancer.getLibraryConfiguration(libraryRoot)
                        val newConfiguration = libraryConfigurationFromConfigModule(m, oldConfig)
                        advancer.reconfigureLibrary(newConfiguration)
                        m.projectLogSink.log(
                            Log.Fine,
                            MessageTemplate.LibraryConfigured,
                            Position(libraryRoot, 0, 0),
                            listOf(newConfiguration.libraryName),
                        )
                    }
                }

                if (m.canAdvance() && countNeeded.getValue(m) == 0) {
                    val at = if (m.isConfigModule) {
                        // Config modules skip the queue so they complete early,
                        // and we can get their export metadata available in
                        // LibraryConfigurations quickly
                        0
                    } else {
                        readyToAdvance.size
                    }
                    readyToAdvance.add(at, m)
                }
            }
        }
    }

    private fun decrementCountFor(importer: Importer, readyToAdvance: ArrayDeque<Module>) {
        val newCount = countNeeded.getValue(importer) - 1
        countNeeded[importer] = newCount
        if (newCount == 0 && importer is Module) {
            importHandler.doImports(pendingByImporter.remove(importer))
            readyToAdvance.add(importer)
        }
    }

    private fun tryBreakCycle(readyToAdvance: ArrayDeque<Module>): Boolean {
        // Either we're all done, or there's an import cycle
        val cycle = checkForImportCycles(pendingByImporter)
            ?: return false
        val importToBreak = run {
            // Break the cycle at an edge with a minimum number of imports.
            // Ideally, if we break a cycle where the import is the only import by
            // that importer, then we're guaranteed to have a module read to advance
            // after breaking.
            var bestIndex = 0
            var minImportsByImporter = Int.MAX_VALUE
            for (i in cycle.indices) {
                val importer = cycle[i].importer
                val nImports = pendingByImporter[importer]?.size ?: continue
                if (nImports < minImportsByImporter) {
                    bestIndex = i
                    minImportsByImporter = nImports
                }
            }
            cycle[bestIndex]
        }

        val error = LogEntry(
            Log.Error,
            MessageTemplate.BreakingImportCycle,
            importToBreak.stayLeaf.pos,
            listOf(
                importToBreak.importer.loc,
                cycle.map { it.importer.loc },
            ),
        )
        val logSink = importToBreak.logSink
        error.logTo(logSink)
        for (pendingImport in cycle) {
            if (pendingImport !== importToBreak) {
                logSink.log(
                    Log.Info,
                    MessageTemplate.InImportCycle,
                    pendingImport.stayLeaf.pos,
                    listOf(),
                )
            }
        }
        resolveImportWithErrorNode(importToBreak, error)
        pendingByImporter.getValue(importToBreak.importer)
            .remove(importToBreak)
        importToBreak.importer.recordImportMetadata(
            Importer.BrokenImportRecord(importToBreak.exporter, isBlockingImport = true),
        )
        // decrementCountFor will clean up any empty list
        decrementCountFor(importToBreak.importer, readyToAdvance)
        return true
    }
}

private class ImportHandler(
    /** Looks up specifiers like `file:path/relative/to/library/root` */
    private val localLookup: ImportResolver,
    /** Looks up specifiers that do not start with [LOCAL_FILE_SPECIFIER_PREFIX] */
    private val nonLocalLookup: ImportResolver,
    private val logSink: LogSink,
    private val advancer: ModuleAdvancer,
) : MacroValue {
    private val unconsumed = mutableListOf<PendingImport>()
    private val allStays = mutableSetOf<StayLeaf>()

    override val sigs: List<Signature2>? = null

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args
        val importer = macroEnv.document.context as Importer
        val givenSpecifier = args.valueTree(0).valueContained(TString) ?: return NotYet
        val stay = if (args.size >= 2) {
            args.valueTree(1) as? StayLeaf ?: return NotYet
        } else {
            null
        }

        val specifiers = buildList {
            add(givenSpecifier)
            if (!givenSpecifier.endsWith("/")) {
                // We can import a directory without a trailing slash
                add("$givenSpecifier/")
            }
        }

        // For each specifier, try it and if a strategy succeeds, make
        // sure we know which specifier from the list above actually worked.
        fun applyExportStrategy(
            tryOneSpecifier: (String) -> Exporter?,
        ): Pair<String, Exporter>? {
            for (specifier in specifiers) {
                val exporter = tryOneSpecifier(specifier)
                if (exporter != null) {
                    return specifier to exporter
                }
            }
            return null
        }

        val (specifierUsed, exporter) =
            // Try local lookup first
            applyExportStrategy { localLookup.lookup(it, advancer) }
                // If that doesn't work, try non-local
                ?: applyExportStrategy { nonLocalLookup.lookup(it, advancer) }
                // Finally, see if a non-local specifier can work as a
                ?: applyExportStrategy { specifier ->
                    val importerLibraryRoot = (importer.loc as? ModuleName)?.libraryRoot()
                        ?: return@applyExportStrategy null
                    val localLibraryConfiguration =
                        advancer.getLibraryConfiguration(importerLibraryRoot)
                            ?: return@applyExportStrategy null
                    toLocalSpecifier(specifier, importer, localLibraryConfiguration)
                        ?.let { localSpecifier ->
                            localLookup.lookup(localSpecifier, advancer)
                        }
                }
                ?: run {
                    val logEntry = LogEntry(
                        Log.Error,
                        MessageTemplate.ImportFailed,
                        macroEnv.pos,
                        listOf(givenSpecifier),
                    )
                    logEntry.logTo(macroEnv.logSink)
                    val importCall = macroEnv.call
                    val errorTree = stay?.let { Either.Left(it) }
                        ?: importCall?.let { Either.Right(it) }
                    errorTree?.let {
                        resolveImportWithErrorNode(errorTree, macroEnv.logSink, givenSpecifier, logEntry)
                    }
                    importer.recordImportMetadata(
                        Importer.UnresolvableImportRecord(givenSpecifier, isBlockingImport = stay != null),
                    )
                    return@invoke Fail(logEntry)
                }

        if (stay != null) {
            // A blocking import
            val pendingImport = PendingImport(importer, exporter, stay, specifierUsed, logSink)
            unconsumed.add(pendingImport)
            allStays.add(stay)
        } else if (macroEnv.call != null) {
            // For a fire-and-forget import, drop the import call.
            macroEnv.replaceMacroCallWith { V(void) }
        }
        return void
    }

    override fun addStays(s: StaySink) {
        allStays.forEach { s.add(it) }
    }

    fun doImports(pending: List<PendingImport>?) {
        pending?.forEach {
            val decl = it.stayLeaf.incoming?.source as? DeclTree
            check(decl != null)
            createLocalBindingsForImport(
                declTree = decl,
                importer = it.importer,
                exporter = it.exporter,
                logSink = it.logSink,
                specifier = it.specifierText,
            )
        }
    }

    fun consumeNewImports(): List<PendingImport> {
        val newImports = unconsumed.toList()
        unconsumed.clear()
        return newImports
    }
}

private fun buildStdModules(
    advancer: ModuleAdvancer,
    console: Console,
    mayRun: Boolean,
): StdModules {
    val tentativeStdLibraryConfiguration = LibraryConfiguration(
        libraryName = DashedIdentifier.temperStandardLibraryIdentifier,
        libraryRoot = STANDARD_LIBRARY_FILEPATH,
        supportedBackendList = emptyList(),
        classifyTemperSource = ::defaultClassifyTemperSource,
    )
    advancer.configureLibrary(tentativeStdLibraryConfiguration)
    val stdModuleConfig = ModuleConfig.default.copy(mayRun = mayRun)

    val fs = accessStdWrapped() ?: throw IOException("Can't access std")
    val snapshot = FilteringFileSystemSnapshot(fs, FileFilterRules.Allow)

    val libraryRoot = tentativeStdLibraryConfiguration.libraryRoot

    val stdPartition = SourceFilePartition(advancer, console, advancer.projectLogSink, stdModuleConfig)
    stdPartition.maybeReusePreviouslyStaged(
        LibraryConfigurationsBundle.from(listOf(tentativeStdLibraryConfiguration)),
    )
    stdPartition.scan(snapshot, libraryRoot)
    val modules = stdPartition.addModulesToAdvancer()
    val modulesByFullSpecifier = buildMap {
        for (module in modules) {
            when (val loc = module.loc) {
                is ImplicitsCodeLocation -> error("implicits is not in std")
                is ModuleName -> {
                    val specifier = buildString {
                        append(tentativeStdLibraryConfiguration.libraryName)
                        append(UNIX_FILE_SEGMENT_SEPARATOR)
                        loc.relativePath().segments.joinTo(
                            this,
                            separator = UNIX_FILE_SEGMENT_SEPARATOR,
                        ) { it.fullName }
                    }
                    this[specifier] = module
                }
            }
        }
    }

    val importHandler = ImportHandler(
        localLookup = object : ImportResolver {
            override fun lookup(specifier: String, collector: ModuleCollector): Exporter? =
                if (specifier.startsWith(LOCAL_FILE_SPECIFIER_PREFIX)) {
                    val pathStr = specifier.withoutPrefix(LOCAL_FILE_SPECIFIER_PREFIX)
                    modulesByFullSpecifier[pathStr]
                } else {
                    null
                }
        },
        nonLocalLookup = ImportResolver.ImportNone,
        logSink = advancer.projectLogSink,
        advancer = advancer,
    )
    val stopBefore = { _: Module ->
        if (mayRun) {
            null
        } else {
            Stage.Run
        }
    }
    GroupOfModulesToAdvanceTogether(modules, importHandler, advancer, stopBefore = stopBefore)
        .advanceModules()

    val stdLibraryConfiguration = // After processing std/config.temper.md
        advancer.getLibraryConfiguration(tentativeStdLibraryConfiguration.libraryRoot)!!
    val stdModules = StdModules(modulesByFullSpecifier, stdLibraryConfiguration)
    return stdModules
}

private class StdModules(
    val modulesByFullSpecifier: Map<String, Module>,
    val libraryConfiguration: LibraryConfiguration,
) : ImportResolver {
    override fun lookup(specifier: String, collector: ModuleCollector): Exporter? {
        val m = modulesByFullSpecifier[specifier]
        if (m != null) {
            addTo(collector)
            return m
        }
        return null
    }

    override fun lookupLibrary(libraryName: DashedIdentifier, collector: ModuleCollector): LibraryConfiguration? {
        if (libraryName == libraryConfiguration.libraryName && addTo(collector)) {
            return libraryConfiguration
        }
        return null
    }

    private fun addTo(collector: ModuleCollector): Boolean {
        val configured = collector.configureLibrary(libraryConfiguration)
        if (configured) {
            modulesByFullSpecifier.values.forEach {
                collector.addModule(it)
            }
        }
        return configured
    }
}

private fun String.withoutPrefix(prefix: String) = if (startsWith(prefix)) {
    substring(prefix.length)
} else {
    this
}

/** Hard stop after 10k interpreter steps */
fun makeContinueCondition(): () -> Boolean {
    val count = intArrayOf(0)

    return { count[0]++ < STEP_QUOTA }
}

private val sharedStdModulesMayNotRun = lazy {
    val logSink = ConsoleBackedContextualLogSink(
        console,
        null,
        null,
        CustomValueFormatter.Nope,
    )
    val advancer = ModuleAdvancer(logSink)
    buildStdModules(advancer, console, mayRun = false)
}

/** Allows introspective access to std/ modules.  Shared by unit tests.  Do not mutate. */
fun getSharedStdModules(): List<Module> =
    sharedStdModulesMayNotRun.value.modulesByFullSpecifier.values.toList()

/** Try to convert a non-local specifier to a local specifier when the importer is in the same library. */
private fun toLocalSpecifier(
    specifier: String,
    importer: Importer,
    config: LibraryConfiguration,
): String? {
    val loc = importer.loc as? FileRelatedCodeLocation
        ?: return null

    val slash = specifier.indexOf('/')
    if (slash < 0) {
        return null
    }

    val requested = DashedIdentifier.from(specifier.substring(0, slash))
    val libraryName = config.libraryName
    if (libraryName == requested && loc.sourceFile.isWithin(config.libraryRoot)) {
        val relSpecifier = buildString {
            append(LOCAL_FILE_SPECIFIER_PREFIX)
            if (config.libraryRoot.segments.isNotEmpty()) {
                // We want a trailing `/` if there are path segments,
                // but not a starting `/` for the empty path.
                append(config.libraryRoot)
            }
            append(specifier.substring(slash + 1))
        }
        return relSpecifier
    }
    return null
}

private val Module.isConfigModule: Boolean
    get() = when (loc) {
        is ModuleName -> when {
            loc.isPreface -> false
            loc.sourceFile.segments.lastOrNull() != LibraryConfiguration.fileName -> false
            // The config file must be a regular file
            loc.sourceFile.isDir -> false
            // It has to be a child of the library's root directory.
            loc.sourceFile.segments.size == loc.libraryRootSegmentCount + 1 -> true
            else -> false
        }
        is ImplicitsCodeLocation -> false
    }

private fun checkForImportCycles(
    pendingByImporter: MutableMap<Importer, MutableList<PendingImport>>,
): List<PendingImport>? {
    if (pendingByImporter.isEmpty()) { return null }
    for (importer in pendingByImporter.keys) {
        val loc = importer.loc
        fun lookForCycle(possibleCycle: Cons.NotEmpty<PendingImport>): Cons.NotEmpty<PendingImport>? {
            val import = possibleCycle.head
            val exporter = import.exporter
            val exporterLoc = exporter.loc
            if (exporterLoc == loc) {
                return possibleCycle
            }
            // For Modules, importers are also exporters
            val importsForExporter = pendingByImporter[
                exporter as? Importer,
            ]
            if (importsForExporter != null) {
                for (nextInChain in importsForExporter) {
                    val cycle = lookForCycle(Cons(nextInChain, possibleCycle))
                    if (cycle != null) {
                        return cycle
                    }
                }
            }
            return null
        }
        for (pendingImport in pendingByImporter.getValue(importer)) {
            val cycle = lookForCycle(Cons(pendingImport))
            if (cycle != null) { return cycle.compatReversed() }
        }
    }
    return null
}

private fun resolveImportWithErrorNode(pendingImport: PendingImport, cause: LogEntry?) =
    resolveImportWithErrorNode(
        Either.Left(pendingImport.stayLeaf),
        pendingImport.logSink,
        pendingImport.specifierText,
        cause,
    )

private fun resolveImportWithErrorNode(
    importLocation: Either<StayLeaf, CallTree>,
    logSink: LogSink,
    specifierText: String,
    cause: LogEntry?,
) {
    val errPos = when (importLocation) {
        is Either.Left -> importLocation.item.pos
        is Either.Right -> importLocation.item.pos
    }

    val errorNodeContent = if (cause != null) {
        // Assume caller logged
        cause
    } else {
        val logEntry = LogEntry(
            Log.Error,
            MessageTemplate.ImportFailed,
            errPos,
            listOf(specifierText),
        )
        logEntry.logTo(logSink)
        logEntry
    }

    val parentEdge = when (importLocation) {
        is Either.Left -> {
            val stayLeaf = importLocation.item
            stayLeaf.incoming?.source?.let { parent ->
                check(parent is DeclTree)
                parent.incoming
            }
        }
        is Either.Right -> {
            val call = importLocation.item
            call.incoming
        }
    }
    parentEdge?.replace { pos ->
        Call(pos, errorFn) {
            Value(errorNodeContent, TProblem)
        }
    }
}

fun defaultTentativeLibraryConfiguration(
    libraryNameGuess: DashedIdentifier,
    libraryRoot: FilePath,
): LibraryConfiguration {
    require(libraryRoot.isDir)
    return LibraryConfiguration(libraryNameGuess, libraryRoot, emptyList(), ::defaultClassifyTemperSource)
}

private data class ContentLookupResult(
    val content: CharSequence,
    val isSkewed: Boolean,
)

private fun withSnapshotter(
    ck: ConfigurationKey,
    console: Console,
    snapshotter: Snapshotter?,
    action: () -> Unit,
) {
    if (snapshotter == null) {
        action()
    } else {
        val snapshottingConsole = Console(
            textOutput = console.rawTextOutput,
            logLevel = console.level,
            snapshotter = snapshotter,
        )
        Debug.Frontend.configure(ck, snapshottingConsole)
        try {
            action()
        } finally {
            Debug.Frontend.configure(ck, null)
        }
    }
}
