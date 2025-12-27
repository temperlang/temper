package lang.temper.frontend.staging

import lang.temper.common.Console
import lang.temper.common.ContentHash
import lang.temper.common.Log
import lang.temper.common.putMultiList
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.frontend.Module
import lang.temper.frontend.ModuleSource
import lang.temper.fs.FileSnapshot
import lang.temper.fs.FileSystemSnapshot
import lang.temper.interp.importExport.Importer
import lang.temper.lexer.Genre
import lang.temper.lexer.LanguageConfig
import lang.temper.lexer.TokenSource
import lang.temper.lexer.defaultClassifyTemperSource
import lang.temper.library.AbstractLibraryConfigurations
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.DashedIdentifier
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleName
import lang.temper.parser.TemperFileSegment
import lang.temper.parser.segmentTemperFile

/**
 * Scans source tree snapshots and allows adding modules to [moduleAdvancer].
 */
class SourceFilePartition(
    private val moduleAdvancer: ModuleAdvancer,
    private val console: Console,
    private val projectLogSink: LogSink = moduleAdvancer.projectLogSink,
    private val moduleConfig: ModuleConfig = moduleAdvancer.moduleConfig,
    private val makeAContinueCondition: () -> () -> Boolean = ::makeContinueCondition,
    private val makeTentativeLibraryConfiguration: (DashedIdentifier, FilePath) -> LibraryConfiguration =
        ::defaultTentativeLibraryConfiguration,
) {
    private val groupedByLibraryRoot = mutableMapOf<FilePath, MutableList<SegmentedModuleSources>>()
    private val priorLibraryConfigurations = mutableMapOf<FilePath, LibraryConfiguration>()
    private val priorModules = mutableMapOf<ModuleName, Module>()
    var needsRebuildPartition: NeedsRebuildPartition = NeedsRebuildPartition.empty
        private set

    /** If some modules were staged before, we can reuse them under some conditions */
    fun maybeReusePreviouslyStaged(previouslyCompiled: Iterable<Module>) {
        for (m in previouslyCompiled) {
            when (val loc = m.loc) {
                is ModuleName -> priorModules[loc] = m
                is ImplicitsCodeLocation -> {}
            }
        }
    }

    /**
     * Configurations to use as tentative configurations if a library with that root directory
     * still exists.
     *
     * If we end up re-using a config.temper.md file then [moduleAdvancer] will not reconfigure
     * it, so those configurations should be passed here.
     */
    fun maybeReusePreviouslyStaged(libraryConfigurations: AbstractLibraryConfigurations) {
        for (c in libraryConfigurations.byLibraryRoot.values) {
            val libraryRoot = c.libraryRoot
            if (libraryRoot !in priorLibraryConfigurations) {
                priorLibraryConfigurations[libraryRoot] = c
            }
        }
    }

    private fun isExternallyDefined(libraryConfiguration: LibraryConfiguration): Boolean {
        // TODO: We could base this on whether any of the scanned roots include the library
        // root which would handle templandia when that lands.
        return libraryConfiguration.libraryName == DashedIdentifier.temperStandardLibraryIdentifier
    }

    /**
     * Identifies Temper source files in the sourceTree under [root]
     * and adds them to the internal list of sources grouped by modules.
     */
    fun scan(sourceTree: FileSystemSnapshot, root: FilePath = FilePath.emptyPath) {
        fun groupSources(p: FilePath, libraryRoot: FilePath) {
            when (val f = sourceTree[p]) {
                is FileSnapshot.Dir -> {
                    var libraryRootForChildren = libraryRoot
                    if (f.names.any { it.isFile && it.segments.lastOrNull() == LibraryConfiguration.fileName }) {
                        libraryRootForChildren = p
                    }
                    f.names.sorted().forEach {
                        groupSources(it, libraryRootForChildren)
                    }
                }
                is FileSnapshot.NoSuchFile,
                is FileSnapshot.SkewedFile,
                is FileSnapshot.UnavailableFile,
                -> {
                    // Do not load content from skewed or unreadable files.
                    // Just let the build fail in the natural course.
                    projectLogSink.log(
                        Log.Fatal,
                        // Any watch service should look for ReadFailed problems
                        // and maybe schedule a rebuild, so this choice of message
                        // may be significant to outer-loop code.
                        MessageTemplate.ReadFailed,
                        Position(p, 0, 0),
                        listOf(p),
                    )
                }
                is FileSnapshot.UpToDateFile -> {
                    val languageConfig = p.segments.lastOrNull()?.let {
                        defaultClassifyTemperSource(it)
                    }
                    if (languageConfig != null) {
                        moduleAdvancer.registerContentForSource(f.path, sourceTree)
                        try {
                            f.content.decodeToString(throwOnInvalidSequence = true)
                        } catch (_: CharacterCodingException) {
                            projectLogSink.log(
                                Log.Fatal,
                                MessageTemplate.ReadFailed,
                                Position(p, 0, 0),
                                listOf(p),
                            )
                            null
                        }?.let { content ->
                            val moduleSources =
                                moduleSources(p, content, f.contentHash, languageConfig, projectLogSink)
                            groupedByLibraryRoot.putMultiList(libraryRoot, moduleSources)
                        }
                    }
                }
            }
        }
        groupSources(root, root)
    }

    fun addModulesToAdvancer(): List<Module> {
        // For each module name for which we found files, come up with a plan for staging
        // if we cannot reuse.
        val plans = buildMap {
            for ((libraryRoot, sources) in groupedByLibraryRoot) {
                val libraryRootSegmentCount = libraryRoot.segments.size
                val configPath = libraryRoot.resolve(LibraryConfiguration.fileName, isDir = false)
                val (configSources, nonConfigSources) = sources.partition { it.filePath == configPath }
                val nonConfigsGroupedByDir = nonConfigSources.groupBy { it.filePath.dirName() }
                if (configSources.isNotEmpty()) {
                    check(configSources.size == 1)
                    val configSource = configSources.first()

                    val configLoc = ModuleName(
                        configPath,
                        libraryRootSegmentCount = libraryRootSegmentCount,
                        isPreface = false,
                    )
                    configSource.prefaceModuleSource?.let { prefaceSource ->
                        this[configLoc.copy(isPreface = true)] = ModulePlan(listOf(prefaceSource))
                    }

                    this[configLoc] = ModulePlan(listOf(configSource.bodyModuleSource), isConfig = true)
                }

                nonConfigsGroupedByDir.forEach { (dir, sources) ->
                    val loc = ModuleName(
                        sourceFile = dir,
                        libraryRootSegmentCount = libraryRootSegmentCount,
                        isPreface = false,
                    )
                    val prefaceSources = sources.mapNotNull { it.prefaceModuleSource }
                    if (prefaceSources.isNotEmpty()) {
                        this[loc.copy(isPreface = true)] = ModulePlan(prefaceSources)
                    }
                    val bodySources = sources.map { it.bodyModuleSource }
                    this[loc] = ModulePlan(bodySources, genre = moduleConfig.genre)
                }
            }
        }
        val externallyDefinedRoots = buildSet {
            for ((libraryRoot, libraryConfig) in priorLibraryConfigurations) {
                if (isExternallyDefined(libraryConfig)) {
                    add(libraryRoot)
                }
            }
        }
        val plannedLibraryRoots = buildSet {
            for (moduleName in plans.keys) {
                add(moduleName.libraryRoot())
            }
            // If we have std in previously configured which was built based on
            // implicit, as-needed inclusion, we should not drop it because we
            // did not find files in the snapshot that were never there.
            addAll(externallyDefinedRoots)
        }

        val needsRebuild = mutableMapOf<ModuleName, Boolean>()
        // Modules do not need rebuilding if they're externally defined: implicitly included like `std` or
        // previously built in templandia.
        for (moduleName in priorModules.keys) {
            val libraryConfiguration = priorLibraryConfigurations[moduleName.libraryRoot()]
            if (libraryConfiguration != null && isExternallyDefined(libraryConfiguration)) {
                needsRebuild[moduleName] = false
            }
        }
        // Modules need rebuilding in these situations:
        // 1. There is no prior module to reuse, or
        // 2. their source hashes are not the same as those of the prior module, or
        // 3. they had a failed import, and there is a new module (see 1), or
        // 4. one of their dependencies needs rebuilding, transitively.
        // Initialize needsRebuild based on situation 1
        var hasNewModule = false // Used in situation 4
        for (moduleName in plans.keys) {
            val prior = priorModules[moduleName]
            // Condition 1
            val isNew = prior == null
            if (isNew) {
                hasNewModule = true
            }
            needsRebuild[moduleName] = isNew
        }
        // Flip any needsRebuild from false to true based on situation 2
        for (moduleName in needsRebuild.keys) {
            if (needsRebuild[moduleName] == true) { continue }
            val plan = plans[moduleName] ?: continue // No plan for externally provided libraries
            val newSources = plan.sources
            val prior = priorModules.getValue(moduleName)
            val priorSources = prior.sources
            if (newSources.size != priorSources.size) {
                needsRebuild[moduleName] = true
            } else {
                for ((newSource, priorSource) in newSources zip priorSources) {
                    val newHash = newSource.contentHash
                    val priorHash = priorSource.contentHash
                    if (newHash == null || priorHash == null || newHash != priorHash) {
                        needsRebuild[moduleName] = true
                        break
                    }
                }
            }
        }
        // Flip any needsRebuild from false to true based on situation 3
        if (hasNewModule) {
            for (moduleName in needsRebuild.keys) {
                if (needsRebuild[moduleName] == true) { continue }
                val prior = priorModules.getValue(moduleName)
                if (prior.importRecords.any { it !is Importer.OkImportRecord }) {
                    needsRebuild[moduleName] = true
                } else if (!moduleName.isPreface) {
                    // There's a new import implicitly if the named module
                    // has a new preface, but did not previously.
                    // This may be redundant as there's no way to get this without a
                    // hash change due to the added `;;;` token checked in situation 2.
                    val prefaceLoc = moduleName.copy(isPreface = true)
                    if (prefaceLoc in needsRebuild && prefaceLoc !in priorModules) {
                        needsRebuild[moduleName] = true
                    }
                }
            }
        }
        // Do the full transitive analysis of import records to flip
        // rebuild from false to true based on situation 4.
        run {
            val depLists = mutableMapOf<ModuleName, Set<ModuleName>>()
            for ((moduleName, needsRebuildTentative) in needsRebuild) {
                if (!needsRebuildTentative) {
                    val prior = priorModules.getValue(moduleName)
                    depLists[moduleName] = buildSet {
                        prior.importRecords.mapNotNullTo(this) { importRecord ->
                            when (importRecord) {
                                is Importer.UnresolvableImportRecord -> null // Handled for situation 3
                                is Importer.BrokenImportRecord,
                                is Importer.OkImportRecord,
                                ->
                                    importRecord.exporterLocation as? ModuleName
                            }
                        }
                    }
                }
            }
            if (depLists.isNotEmpty()) {
                var nNewlyDirty: Int
                // Keep checking while we're dirty
                do {
                    nNewlyDirty = 0
                    for ((moduleName, depList) in depLists) {
                        if (needsRebuild[moduleName] == false && depList.any { needsRebuild[it] != false }) {
                            needsRebuild[moduleName] = true
                            nNewlyDirty += 1
                        }
                    }
                } while (nNewlyDirty != 0)
            }
        }

        // Figure out what needs building and what doesn't.
        // We store these sets for test harnesses to use, but also use them to decide how
        // to populate the module advancer with library configurations and modules.
        val newLibraryRoots = mutableSetOf<FilePath>()
        val reusedLibraryRoots = mutableSetOf<FilePath>()
        val droppedLibraryRoots = mutableSetOf<FilePath>()
        val newModules = mutableSetOf<ModuleName>()
        val reusedModules = mutableSetOf<ModuleName>()
        val dirtyModules = mutableSetOf<ModuleName>()
        val droppedModules = mutableSetOf<ModuleName>()

        for ((moduleName, moduleNeedsRebuild) in needsRebuild) {
            if (!moduleNeedsRebuild) {
                reusedModules.add(moduleName)
            } else if (moduleName !in priorModules) {
                newModules.add(moduleName)
            } else {
                dirtyModules.add(moduleName)
            }
        }

        for (moduleName in priorModules.keys) {
            if (moduleName !in plans && moduleName.libraryRoot() !in externallyDefinedRoots) {
                droppedModules.add(moduleName)
            }
        }

        for (libraryRoot in priorLibraryConfigurations.keys) {
            if (libraryRoot !in plannedLibraryRoots) {
                // Don't send library roots to the module advancer when all source files
                // have been deleted which happens for mass deletes and directory renames.
                droppedLibraryRoots.add(libraryRoot)
                notifyLibraryDropped(projectLogSink, libraryRoot)
            } else {
                // There's an odd case where the library root is reused, but
                // the configuration file was dropped.  In that case we need
                // to call back out to the defaultTentativeConfiguration maker.
                // If we didn't do that, then the next translation would not be
                // equivalent to that from running build with no prior info.
                val configLibraryName = ModuleName(
                    libraryRoot.resolve(LibraryConfiguration.fileName, isDir = false),
                    libraryRootSegmentCount = libraryRoot.segments.size,
                    isPreface = false,
                )
                if (configLibraryName in droppedModules) {
                    // New to us since we can't inherit any configuration info.
                    newLibraryRoots.add(libraryRoot)
                } else {
                    reusedLibraryRoots.add(libraryRoot)
                }
                notifyLibraryFound(projectLogSink, libraryRoot)
            }
        }

        for (libraryRoot in plannedLibraryRoots) {
            if (libraryRoot !in priorLibraryConfigurations) {
                newLibraryRoots.add(libraryRoot)
                notifyLibraryFound(projectLogSink, libraryRoot)
            }
        }

        this.needsRebuildPartition = NeedsRebuildPartition(
            newLibraryRoots = newLibraryRoots.toSet(),
            reusedLibraryRoots = reusedLibraryRoots.toSet(),
            droppedLibraryRoots = droppedLibraryRoots.toSet(),
            newModules = newModules.toSet(),
            reusedModules = reusedModules.toSet(),
            dirtyModules = dirtyModules.toSet(),
            droppedModules = droppedModules.toSet(),
        )

        for (libraryRoot in reusedLibraryRoots) {
            moduleAdvancer.configureLibrary(priorLibraryConfigurations.getValue(libraryRoot))
        }

        val newLibrariesTentativelyConfigured = mutableSetOf<FilePath>()
        val moduleList = mutableListOf<Module>()
        for (moduleName in reusedModules) {
            val reusedModule = priorModules.getValue(moduleName)
            moduleAdvancer.addModule(reusedModule)
            moduleList.add(reusedModule)
        }
        for ((moduleName, plan) in plans) {
            if (moduleName !in reusedModules) {
                val libraryRoot = moduleName.libraryRoot()
                val module = moduleAdvancer.createModule(
                    loc = moduleName,
                    console = console,
                    continueCondition = makeAContinueCondition(),
                    mayRun = moduleConfig.mayRun,
                    genre = plan.genre,
                )
                module.deliverContent(plan.sources)
                moduleList.add(module)

                if (plan.isConfig && libraryRoot in newLibraryRoots) {
                    val configSource = plan.sources.first() // Config files have exactly one source
                    val config = makeTentativeLibraryConfiguration(
                        libraryNameWithDefault(
                            configSource.fetchedContent?.toString() ?: "",
                            module,
                        ),
                        libraryRoot,
                    )
                    moduleAdvancer.configureLibrary(config)
                    newLibrariesTentativelyConfigured.add(libraryRoot)
                }
                notifyModulePreStaged(projectLogSink, module)
            }
        }

        for (libraryRoot in newLibraryRoots) {
            if (libraryRoot !in newLibrariesTentativelyConfigured) {
                moduleAdvancer.configureLibrary(
                    makeTentativeLibraryConfiguration(
                        libraryNameWithDefault("", null, libraryRoot),
                        libraryRoot,
                    ),
                )
            }
        }
        return moduleList.toList()
    }
}

fun partitionSourceFilesIntoModules(
    sourceTree: FileSystemSnapshot,
    moduleAdvancer: ModuleAdvancer,
    projectLogSink: LogSink = moduleAdvancer.projectLogSink,
    console: Console,
    genre: Genre = Genre.Library,
    mayRun: Boolean = false,
    root: FilePath = FilePath.emptyPath,
    makeAContinueCondition: () -> () -> Boolean = ::makeContinueCondition,
    makeTentativeLibraryConfiguration: (DashedIdentifier, FilePath) -> LibraryConfiguration =
        ::defaultTentativeLibraryConfiguration,
) {
    val partition = SourceFilePartition(
        moduleAdvancer = moduleAdvancer,
        projectLogSink = projectLogSink,
        console = console,
        moduleConfig = ModuleConfig.default.copy(mayRun = mayRun, genre = genre),
        makeAContinueCondition = makeAContinueCondition,
        makeTentativeLibraryConfiguration = makeTentativeLibraryConfiguration,
    )
    partition.scan(sourceTree, root)
    partition.addModulesToAdvancer()
}

private data class SegmentedModuleSources(
    val filePath: FilePath,
    val contentHash: ContentHash,
    val prefaceModuleSource: ModuleSource?,
    val bodyModuleSource: ModuleSource,
)

private fun moduleSources(
    p: FilePath,
    content: String,
    contentHash: ContentHash,
    languageConfig: LanguageConfig,
    logSink: LogSink,
): SegmentedModuleSources {
    val bodyTokens = segmentTemperFile(p, logSink, languageConfig, content, TemperFileSegment.Body)
    val prefaceTokens = if (bodyTokens.hasSuperTokens) {
        segmentTemperFile(p, LogSink.devNull, languageConfig, content, TemperFileSegment.Preface)
    } else {
        null
    }

    fun moduleSourceFor(tokens: TokenSource): ModuleSource = ModuleSource(
        filePath = p,
        contentHash = contentHash,
        fetchedContent = content,
        filePositions = FilePositions.fromSource(p, content),
        tokenSource = tokens,
        languageConfig = languageConfig,
    )

    return SegmentedModuleSources(
        filePath = p,
        contentHash = contentHash,
        prefaceModuleSource = prefaceTokens?.let { moduleSourceFor(it.tokens) },
        bodyModuleSource = moduleSourceFor(bodyTokens.tokens),
    )
}

private fun notifyLibraryFound(logSink: LogSink, libraryRoot: FilePath) {
    logSink.log(
        Log.Fine,
        MessageTemplate.LibraryFound,
        Position(libraryRoot, 0, 0),
        listOf(),
    )
}

private fun notifyLibraryDropped(logSink: LogSink, libraryRoot: FilePath) {
    logSink.log(
        Log.Fine,
        MessageTemplate.LibraryDropped,
        Position(libraryRoot, 0, 0),
        listOf(),
    )
}

private fun notifyModulePreStaged(logSink: LogSink, module: Module) {
    logSink.log(
        Log.Fine,
        MessageTemplate.PreStagingModule,
        Position(module.loc, 0, 0),
        listOf(module.sources.mapNotNull { it.filePath }),
    )
}

private data class ModulePlan(
    val sources: List<ModuleSource>,
    val isConfig: Boolean = false,
    val genre: Genre = Genre.Library,
)

/** Exported stats for test harnesses and debugging */
data class NeedsRebuildPartition(
    val newLibraryRoots: Set<FilePath>,
    val reusedLibraryRoots: Set<FilePath>,
    val droppedLibraryRoots: Set<FilePath>,
    val newModules: Set<ModuleName>,
    val reusedModules: Set<ModuleName>,
    val dirtyModules: Set<ModuleName>,
    val droppedModules: Set<ModuleName>,
) : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("newLibraryRoots", isDefault = newLibraryRoots.isEmpty()) {
            sortedStringArray(newLibraryRoots)
        }
        key("reusedLibraryRoots", isDefault = reusedLibraryRoots.isEmpty()) {
            sortedStringArray(reusedLibraryRoots)
        }
        key("droppedLibraryRoots", isDefault = droppedLibraryRoots.isEmpty()) {
            sortedStringArray(droppedLibraryRoots)
        }
        key("newModules", isDefault = newModules.isEmpty()) {
            sortedStringArray(newModules)
        }
        key("reusedModules", isDefault = reusedModules.isEmpty()) {
            sortedStringArray(reusedModules)
        }
        key("dirtyModules", isDefault = dirtyModules.isEmpty()) {
            sortedStringArray(dirtyModules)
        }
        key("droppedModules", isDefault = droppedModules.isEmpty()) {
            sortedStringArray(droppedModules)
        }
    }

    companion object {
        val empty = NeedsRebuildPartition(
            emptySet(), emptySet(), emptySet(),
            emptySet(), emptySet(), emptySet(), emptySet(),
        )
    }
}

private fun <T> StructureSink.sortedStringArray(ts: Set<T>) = arr {
    for (s in ts.map { "$it" }.sorted()) {
        value(s)
    }
}
