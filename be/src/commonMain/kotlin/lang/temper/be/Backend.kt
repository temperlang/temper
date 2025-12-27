package lang.temper.be

import lang.temper.ast.OutTree
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.names.NameSelection
import lang.temper.be.names.NameSelectionFile
import lang.temper.be.tmpl.LibraryRootContext
import lang.temper.be.tmpl.SignatureAdjustments
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.MimeType
import lang.temper.common.buildListMultimap
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.CompletableRFuture
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.SignalRFuture
import lang.temper.common.currents.join
import lang.temper.common.currents.newCompletableFuture
import lang.temper.common.currents.preComputedFuture
import lang.temper.common.putMultiList
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.common.toStringViaBuilder
import lang.temper.common.transitiveClosure
import lang.temper.format.TokenSink
import lang.temper.frontend.Module
import lang.temper.fs.AsyncSystemAccess
import lang.temper.fs.AsyncSystemReadAccess
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.TEMPER_KEEP_NAME
import lang.temper.fs.asAppendable
import lang.temper.lexer.temperExtensionIndex
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.library.DependencyResolver
import lang.temper.library.LibraryConfiguration
import lang.temper.log.CodeLocation
import lang.temper.log.CodeLocationKey
import lang.temper.log.FilePath
import lang.temper.log.FilePathAndMimeTypeOrNull
import lang.temper.log.FilePathSegment
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.bannedPathSegmentNames
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.log.plus
import lang.temper.log.unknownPos
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModularName
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.name.TemperName
import lang.temper.name.identifiers.IdentStyle
import lang.temper.value.Fail
import lang.temper.value.OccasionallyHelpful
import lang.temper.value.Value
import java.io.IOException

/**
 * Sibling [Backend]s are ones that work together to each translate one library
 * within a group of [co-compiled modules][lang.temper.frontend.CoCompiledModules].
 * Mostly they work independently & concurrently, but occasionally they need to
 * compare notes, for example importers may need to know names assigned to exports.
 *
 * This type groups a bunch of siblings and allows ready access, via library roots,
 * to a sibling and its notes.
 */
data class SiblingData<E>(
    val backendsByLibraryRoot: Map<FilePath, Backend<*>>,
    val dataByLibraryRoot: Map<FilePath, E>,
)

/**
 * Responsible for generating output files from Temper [Module]s that have
 * reached the code generation stage.
 */
abstract class Backend<SELF : Backend<SELF>>(
    /** Identifies this backend. */
    val backendId: BackendId,
    /** The name of the library to translate. */
    val libraryName: DashedIdentifier,
    /** The modules in the current library. */
    modules: List<Module>,
    /**
     * Lets us ground exported names.
     */
    dependencyResolver: DependencyResolver,
    /**
     * Allows the backend to create files in a location reserved for the output files.
     */
    val buildFileCreator: AsyncSystemAccess,
    /**
     * Allows the backend to create files in a location reserved for all backends to keep files.
     */
    val keepFileUpdater: AsyncSystemReadAccess,
    /**
     * Receives messages about code generation.
     */
    val logSink: LogSink,
    /** Additional configuration. */
    val config: Config,
    /**
     * Shared inferences for backends with the same target library building this and other
     * libraries in [libraryConfigurations].
     */
    val dependenciesBuilder: Dependencies.Builder<SELF>,
) {
    constructor(backendId: BackendId, setup: BackendSetup<SELF>) : this(
        backendId = backendId,
        libraryName = setup.libraryName,
        modules = setup.modules,
        dependencyResolver = setup.dependencyResolver,
        buildFileCreator = setup.buildFileCreator,
        keepFileUpdater = setup.keepFileUpdater,
        logSink = setup.logSink,
        config = setup.config,
        dependenciesBuilder = setup.dependenciesBuilder,
    )

    val libraryConfigurations = dependenciesBuilder.libraryConfigurations
        .withCurrentLibrary(libraryName = libraryName)

    private var _cancelGroup: CancelGroup? = null

    /** Called by the build orchestrator to initialize backend state */
    fun setup(cancelGroup: CancelGroup) {
        check(_cancelGroup == null)
        this._cancelGroup = cancelGroup
    }

    /**
     * The group that gets, usually via [CliEnv], the ability to cancel any processes spawned
     * if translation is found to not be necessary.
     */
    val cancelGroup: CancelGroup get() = _cancelGroup!!

    protected val readyModules: List<Module>
    init {
        val readyModules = mutableListOf<Module>()
        val brokenModules = mutableListOf<Module>()
        for (module in modules) {
            if (module.ok) {
                if (module.finished) {
                    readyModules.add(module)
                } else {
                    brokenModules.add(module)
                }
            } else {
                brokenModules.add(module)
            }
        }

        if (brokenModules.isNotEmpty()) {
            for (module in brokenModules) {
                // Include info from module result if it's a fail?
                val result = module.runResult
                if (result is Fail) {
                    result.info?.logTo(logSink)
                }
            }
            logSink.log(
                level = Log.Fatal,
                template = MessageTemplate.NotGeneratingCodeFor,
                pos = unknownPos,
                values = brokenModules.map { it.loc },
            )
        }

        this.readyModules = readyModules.toList()
    }

    private var externalNamesProvided: ExternalNamesProvided? = null

    @Suppress("LeakingThis")
    val dependencyResolver: MetadataDependencyResolver<SELF> =
        MetadataDependencyResolverImpl(dependencyResolver, this) { this.dependenciesBuilder }

    /**
     * Look at names and type declarations before trying to translate either or expressions.
     * This allows sharing information across library that informs translations from each like
     * [lang.temper.be.tmpl.SignatureAdjustments]
     */
    open fun preAnalysis(siblings: SiblingData<*>) {
        storeQNameMapping(
            libraryConfigurations.currentLibraryConfiguration,
            readyModules,
            dependenciesBuilder,
            backendId,
        )
        storeSignatureAdjustments(
            libraryConfigurations.currentLibraryConfiguration,
            readyModules,
            dependenciesBuilder,
            supportNetwork,
            SignatureAdjustments.KeyFactory.acquireKey(this),
        )
    }

    /**
     * Combines the [readyModules] with information from the version history to
     * produce a tentative [TmpL] translation.
     */
    abstract fun tentativeTmpL(): TmpL.ModuleSet

    /**
     * A joining step that lets each sibling backend consider its [tentativeTmpL]
     * in the context of its siblings tentative translations.
     *
     * This may help adjust imports and names.
     */
    open fun finishTmpL(
        tentative: TmpL.ModuleSet,
        siblings: SiblingData<TmpL.ModuleSet>,
    ): TmpL.ModuleSet {
        // Type formal names end up being referenced as part of function types when the
        // function is exported.
        fun putNonTopLevelTypes(
            module: TmpL.Module,
            trees: Iterable<TmpL.Tree?>,
            outMap: MutProviderNameMap,
        ) {
            for (tree in trees) {
                if (tree != null) {
                    if (tree is TmpL.TypeFormal) {
                        outMap.putMultiList(
                            tree.name.name as ModularName,
                            module to tree,
                        )
                    }
                    putNonTopLevelTypes(module, tree.children, outMap)
                }
            }
        }

        // For each module, add top-level names.
        fun putProvidedNames(
            modules: List<TmpL.Module>,
            outMap: MutProviderNameMap,
        ) {
            modules.forEach { module ->
                val topLevels = module.topLevels
                topLevels.forEach { topLevel ->
                    if (topLevel is TmpL.TopLevelDeclaration) {
                        val name = topLevel.name.name
                        if (name is ModularName) {
                            outMap.putMultiList(name, module to topLevel)
                        }
                    }
                }
                putNonTopLevelTypes(module, topLevels, outMap)
            }
        }

        val externalNamesProvided = computeAndShare(
            siblingData = siblings,
            get = { this.externalNamesProvided },
            set = { this.externalNamesProvided = it },
            customize = { common ->
                val sameLibraryModuleLocations = mutableSetOf<ModuleLocation>()
                tentative.modules.mapTo(sameLibraryModuleLocations) { it.codeLocation.codeLocation }
                common.copy(
                    localProviders = buildListMultimap {
                        putProvidedNames(tentative.modules, this)
                    },
                    sameLibraryModuleLocations = sameLibraryModuleLocations.toSet(),
                )
            },
        ) { siblingData ->
            val providers = buildListMultimap {
                siblingData.dataByLibraryRoot.forEach { (_, moduleSet) ->
                    putProvidedNames(moduleSet.modules, this)
                }
            }
            ExternalNamesProvided(providers, emptyMap(), emptySet())
        }

        finishTmpLImports(tentative, externalNamesProvided, logSink)
        registerCrossLibraryImports(tentative, dependenciesBuilder)
        return tentative
    }

    open fun acceptKeepFiles(data: NameSelectionFile) {
    }

    fun loadKeepFiles(): RFuture<NameSelectionFile, IOException> =
        keepFileUpdater
            .fileReader(filePath(NAME_SELECTION_JSON_FILE))
            .textContent()
            .then("Decode JSON") { dataResult ->
                dataResult.mapResult { textual ->
                    NameSelectionFile.fromJson(textual)
                }
            }

    /**
     * Sibling backends may perform this step in parallel.
     *
     * @param finished the output of [finishTmpL].
     * @return file specifications containing ASTs to be serialized to the associated file.
     */
    abstract fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification>

    /**
     * After translation, save any decidable configuration to files to be added to the output.
     * These files are stored under the [lang.temper.fs.TEMPER_KEEP_NAME] root.
     */
    fun saveKeepFiles(): List<MetadataFileSpecification> {
        val libName = libraryConfigurations.currentLibraryConfiguration.libraryName
        val data = NameSelectionFile.make(
            libraryName = libName,
            backendId = backendId,
            selections = selectNames(),
        ).decorated().toJsonString()
        return listOf(
            MetadataFileSpecification(
                path = filePath(NAME_SELECTION_JSON_FILE),
                mimeType = MimeType.json,
                content = data,
            ),
        )
    }

    /**
     * Called by [saveKeepFiles] to save name selections.
     *
     * @return select backend names to map to fully qualified temper names.
     */
    open fun selectNames(): List<NameSelection> = emptyList()

    /**
     * After [translate] allows sibling backends to compare notes and allows
     * this backend to make any final adjustments to its own output file specifications.
     *
     * After this step, the translation manager will start writing files to the
     * output file system.
     */
    open fun collate(
        outputFiles: List<OutputFileSpecification>,
        siblings: SiblingData<List<OutputFileSpecification>>,
    ): List<OutputFileSpecification> {
        dependenciesBuilder.let {
            val libraryName = libraryConfigurations.currentLibraryConfiguration.libraryName
            for (outputFile in outputFiles) {
                it.addFile(libraryName, FilePathAndMimeTypeOrNull(outputFile.path, outputFile.mimeType))
            }
        }
        return outputFiles
    }

    /**
     * This provides space to apply any information gleaned from [collate]
     * to the files produced during [translate].
     */
    open fun preWrite(outputFiles: List<OutputFileSpecification>) = outputFiles

    /**
     * After the files are written to the file system, the backend may call out to
     * target-language-specific tools to post process them.
     */
    open fun postWrite(
        outputFiles: List<OutputFileSpecification>,
        keepFiles: List<MetadataFileSpecification>,
    ): SignalRFuture = preComputedFuture(Unit)

    /** Kicks off tasks to write output files after [collate]. */
    fun writeOutputFiles(outputFiles: List<OutputFileSpecification>): SignalRFuture {
        // Verify against duplicates for more deterministic results.
        val groups = outputFiles.groupBy { it.path }
        val duplicates = groups.filterValues { group ->
            if (group.size <= 1) {
                false
            } else {
                val uniques = group.map { file ->
                    when (file) {
                        is TranslatedFileSpecification -> file.content.toString().encodeToByteArray()
                        is MetadataFileSpecification -> file.content.encodeToByteArray()
                        is ByteArrayFileSpecification -> file.content
                        is ResourceFileSpecification -> file.content.loadBinary()
                    }
                }.toSet()
                uniques.size > 1
            }
        }
        if (duplicates.isNotEmpty()) {
            // SignalRFuture doesn't allow for expressing failure, so just throw an exception.
            // And this really is just a failure of input state, not really depending on deferred tasks.
            error("Duplicate output files: ${duplicates.keys}")
        }

        val moduleByLocation = buildMap<CodeLocation, Module> {
            for (module in readyModules) {
                val loc = module.loc
                if (loc !is ModuleName || !loc.isPreface) {
                    for (source in module.sources) {
                        val filePath = source.filePath
                        if (filePath != null) {
                            this[filePath] = module
                        }
                    }
                }
            }
            for (module in readyModules) {
                val loc = module.loc
                this[loc] = module
                if (loc is ModuleName && !loc.isPreface && loc.sourceFile !in this) {
                    this[loc.sourceFile] = module
                }
            }
        }

        // Collect things we need to wait for.  We need to wait for an output file and an output
        // source map per translated module.
        val futures = mutableListOf<RFuture<Unit, IOException>>()
        for (output in outputFiles) {
            val outFilePath = output.path
            val mimeType = output.mimeType
            val sourceMapFilePath = if (output is TranslatedFileSpecification) {
                sourceMapFile(outFilePath)
            } else {
                null
            }
            val ocf = OutCodeFormatter(
                outPath = buildFileCreator.pathToFileCreatorRoot + outFilePath,
                wrapTokenSink = ::wrapTokenSink,
                lookupCodeLocation = lookupCodeLocation@{ it: CodeLocation ->
                    val sourceFile = (it as? FileRelatedCodeLocation)?.sourceFile
                    val module = moduleByLocation[it]
                        ?: sourceFile?.let { moduleByLocation[sourceFile] }
                    if (sourceFile != null && module != null) {
                        val filePositions =
                            module.filePositions[sourceFile]
                                ?: module.sharedLocationContext[it, CodeLocationKey.FilePositionsKey]
                        if (filePositions != null) {
                            return@lookupCodeLocation sourceFile to filePositions
                        }
                    }
                    null
                },
                logSink = logSink,
            )
            val sourceWriteTask = buildFileCreator.buildFile(outFilePath, mimeType)
                .write { byteSink ->
                    when (output) {
                        is TranslatedFileSpecification -> {
                            byteSink.asAppendable().use { appendable ->
                                ocf.format(output.content, appendable)
                            }
                        }

                        is MetadataFileSpecification ->
                            byteSink.write(output.content.encodeToByteArray())

                        is ByteArrayFileSpecification ->
                            byteSink.write(output.content)

                        is ResourceFileSpecification ->
                            byteSink.write(output.content.loadBinary())
                    }
                }
            futures.add(sourceWriteTask)

            if (sourceMapFilePath != null) {
                val sourceMapFileBuilder = buildFileCreator
                    .buildFile(sourceMapFilePath, SourceMap.mimeType)
                // Creating a completable future here lets us avoid having a
                // thread waiting on the write-complete future which lets us
                // work on single threaded executors.
                val writesDone: CompletableRFuture<Unit, IOException> =
                    cancelGroup.newCompletableFuture("All writes for $outFilePath")
                sourceWriteTask.thenDo("Write source map after $outFilePath") { r ->
                    val err = r.failure ?: r.throwable
                    if (err != null) {
                        logSink.log(
                            Log.Error,
                            MessageTemplate.FailedToWrite,
                            unknownPos,
                            listOf(listOf(outFilePath, err.message)),
                        )
                        writesDone.complete(r)
                        return@thenDo
                    }
                    val sourceMapDone = sourceMapFileBuilder.write { byteSink ->
                        // We don't want to write the source map until we've written the source.
                        // The process of formatting the source is what fills in the source map.
                        val sourceMap = ocf.buildSourceMap {
                            val module = moduleByLocation[it]
                            // TODO: rewrite to work with multiple ModuleSources
                            val content = module?.sharedLocationContext
                                ?.get(it, CodeLocationKey.SourceCodeKey)
                            content?.toString()
                        }

                        byteSink.asAppendable().use { appendable ->
                            val structureSink = FormattingStructureSink(appendable, indent = false)
                            sourceMap.destructure(structureSink, specifiedBitsOnly = true)
                        }
                    }
                    writesDone.completeFrom(sourceMapDone)
                }
                futures.add(writesDone)
            }
        }

        return cancelGroup.join(futures)
    }

    /** Kicks off tasks to write keep files after [collate]. */
    fun writeKeepFiles(keepFiles: List<MetadataFileSpecification>): SignalRFuture {
        // Verify against duplicates for more deterministic results.
        val groups = keepFiles.groupBy { it.path }
        val duplicates = groups.filterValues { group ->
            if (group.size <= 1) {
                false
            } else {
                val uniques = group.map { file -> file.content }.toSet()
                uniques.size > 1
            }
        }
        if (duplicates.isNotEmpty()) {
            // SignalRFuture doesn't allow for expressing failure, so just throw an exception.
            // And this really is just a failure of input state, not really depending on deferred tasks.
            error("Duplicate $TEMPER_KEEP_NAME files: ${duplicates.keys}")
        }

        // Collect things we need to wait for.  We need to wait for an output file and an output
        // source map per translated module.
        val futures = mutableListOf<RFuture<Unit, IOException>>()
        for (output in keepFiles) {
            val outFilePath = output.path
            val mimeType = output.mimeType
            val sourceWriteTask = keepFileUpdater.buildFile(outFilePath, mimeType)
                .write { byteSink ->
                    byteSink.write(output.content.encodeToByteArray())
                }
            futures.add(sourceWriteTask)
        }

        return cancelGroup.join(futures)
    }

    /**
     * Where a translated file should be put within the library.
     *
     * Typically:
     *
     *     -work/path/to/library/root//foo/bar/ -> foo/bar.ext
     *
     * In the case where the relative module path is empty, it
     * uses the library name in [snake case][IdentStyle.Snake]:
     *
     *     -work/path/to/library/root// -> my_library.ext
     *
     * Preface modules get a `_preface` affix before the extension.
     *
     *      -work/path/to/library/root//foo/bar/:preface -> foo/bar_preface.ext
     */
    protected fun filePathForSource(
        libraryConfiguration: LibraryConfiguration,
        sourceCodeLocation: ModuleName,
        outputFileExtension: String,
        defaultName: String = FALLBACK_FILE_PATH_BASENAME,
    ): FilePath = defaultFilePathForSource(
        libraryConfiguration, sourceCodeLocation,
        outputFileExtension = outputFileExtension,
        defaultName = defaultName,
    )

    companion object {
        fun defaultFilePathForSource(
            libraryConfiguration: LibraryConfiguration,
            sourceCodeLocation: ModuleName,
            outputFileExtension: String,
            defaultName: String = FALLBACK_FILE_PATH_BASENAME,
        ): FilePath {
            val isSeparateLibrary = libraryConfiguration.libraryRoot != sourceCodeLocation.libraryRoot()
            var probablyUniqueFilePath =
                if (isSeparateLibrary) {
                    // This branch is intended for the standard lib, other code with cross-library references
                    // may end up here but that isn't the goal. Keeping as a separate code branch because of that
                    val relFilePathSegments = libraryConfiguration.libraryRoot
                        .relativePathTo(sourceCodeLocation.sourceFile)
                    FilePath(
                        relFilePathSegments.mapNotNull { it as? FilePathSegment },
                        isDir = sourceCodeLocation.sourceFile.isDir,
                    )
                } else {
                    sourceCodeLocation.relativePath()
                }
            if (isSeparateLibrary) {
                // Reference as a separate library.
                probablyUniqueFilePath = FilePath(listOf(globalPathSegment), isDir = true)
                    .resolve(probablyUniqueFilePath)
            }

            if (probablyUniqueFilePath.segments.isEmpty()) {
                if (isSeparateLibrary) {
                    return FilePath(
                        listOf(FilePathSegment("$defaultName$outputFileExtension")),
                        isDir = false,
                    )
                }

                probablyUniqueFilePath = FilePath(
                    listOf(
                        FilePathSegment(
                            IdentStyle.Dash.convertTo(
                                IdentStyle.Snake,
                                libraryConfiguration.libraryName.text,
                            ),
                        ),
                    ),
                    isDir = false,
                )
            }

            return FilePath(
                buildList {
                    addAll(probablyUniqueFilePath.segments)
                    var lastSegmentText = this[this.lastIndex].fullName
                    if (probablyUniqueFilePath.isFile) {
                        // Strip file extension
                        val extensionIndex = temperExtensionIndex(lastSegmentText)
                        if (extensionIndex >= 0) {
                            lastSegmentText = lastSegmentText.substring(0, extensionIndex)
                        }
                        if (lastSegmentText.isEmpty()) {
                            lastSegmentText = defaultName
                        }
                    }
                    // Prefaces need to not collide with translated body modules
                    if (sourceCodeLocation.isPreface) {
                        lastSegmentText = "${lastSegmentText}_preface"
                    }
                    // Apply the extension
                    lastSegmentText = "$lastSegmentText$outputFileExtension"
                    check(lastSegmentText !in bannedPathSegmentNames) { lastSegmentText }

                    this[this.lastIndex] = FilePathSegment(lastSegmentText)
                },
                isDir = false,
            )
        }
    }

    /** Locates the temper source files for a library */
    open fun libraryRootContext(libraryConfiguration: LibraryConfiguration) =
        LibraryRootContext(
            inRoot = libraryConfiguration.libraryRoot,
            outRoot = dirPath(libraryConfiguration.libraryName.text),
        )

    private val allocatedFiles = mutableSetOf<FilePath>()
    protected fun allocateTextFile(
        /** Might be a dir path, if that has meaning to the backend in question. */
        outFilePath: FilePath,
        outputFileExtension: String,
        defaultName: String = FALLBACK_FILE_PATH_BASENAME,
    ): FilePath {
        val lastSegmentAdjusted =
            outFilePath.lastOrNull()?.withTemperAwareExtension(outputFileExtension)
                ?: FilePathSegment(defaultName)
        var serialNo: Int? = null
        val parentDir = outFilePath.dirName()
        while (true) {
            val candidate = parentDir + if (serialNo == null) {
                lastSegmentAdjusted
            } else {
                FilePathSegment(
                    toStringViaBuilder {
                        // foo.bar.baz -> foo123.bar.baz
                        val fullName = lastSegmentAdjusted.fullName
                        var dot = fullName.indexOf('.')
                        if (dot < 0) { dot = fullName.length }
                        it.append(fullName, 0, dot)
                        it.append(serialNo)
                        it.append(fullName, dot, fullName.length)
                    },
                )
            }
            if (candidate !in allocatedFiles) {
                allocatedFiles.add(candidate)
                return candidate
            }
            serialNo = (serialNo ?: -1) + 1
        }
    }

    protected fun allocateTextFile(
        module: Module,
        outputFileExtension: String,
        defaultName: String = FALLBACK_FILE_PATH_BASENAME,
    ): FilePath {
        val moduleName = module.loc as ModuleName
        return allocateTextFile(
            moduleName,
            libraryConfigurations.byLibraryRoot.getValue(moduleName.libraryRoot()),
            outputFileExtension = outputFileExtension,
            defaultName = defaultName,
        )
    }

    protected fun allocateTextFile(
        loc: ModuleName,
        libraryConfiguration: LibraryConfiguration,
        outputFileExtension: String,
        defaultName: String = FALLBACK_FILE_PATH_BASENAME,
    ): FilePath {
        val filePath = filePathForSource(
            libraryConfiguration, loc,
            outputFileExtension = outputFileExtension,
            defaultName = defaultName,
        )
        return allocateTextFile(
            outFilePath = filePath,
            outputFileExtension = outputFileExtension,
        )
    }

    fun getDependencies(): Dependencies<SELF> = dependenciesBuilder.build()

    abstract val supportNetwork: SupportNetwork

    /**
     * May be overridden to wrap the token sink that receives tokens from the [OutTree].
     */
    open fun wrapTokenSink(tokenSink: TokenSink): TokenSink = tokenSink

    /** Backend configuration. */
    data class Config(
        /** true to require making project metadata files; false may make them if required */
        val makeMetaDataFile: Boolean,

        /**
         * Set true to generate other libraries as nested/bundled or false to treat them as external references.
         * Not actually currently referenced anywhere, although we do still bundle some for functional tests.
         */
        val nesting: Boolean,

        /**
         * Set true to abbreviate for visual convenience, even if it means the generated code is incomplete.
         */
        val abbreviated: Boolean = false,
    ) {
        companion object {
            /** For display purposes rather than utility, such as in repl translate. */
            val abbreviated = Config(abbreviated = true, makeMetaDataFile = false, nesting = false)

            /** Bundled builds sans project metadata files are used today for testing, running, docgen, and so on. */
            val bundled = Config(makeMetaDataFile = false, nesting = true)

            /** Some bundled cases still want project metadata. */
            val bundledWithMeta = Config(makeMetaDataFile = true, nesting = true)

            /** In standard builds, we make independent libraries with project metadata files. */
            val production = Config(makeMetaDataFile = true, nesting = false)
        }
    }

    sealed class OutputFileSpecification(
        val path: FilePath,
        val mimeType: MimeType?,
    ) {
        override fun toString() = "${this.javaClass.simpleName}($path)" // for debugging convenience

        init {
            check(path.isFile) { "$path should be a file" }
        }
    }

    /** A file translated from source code. */
    open class TranslatedFileSpecification(
        path: FilePath,
        mimeType: MimeType?,
        val content: OutTree<*>,
    ) : OutputFileSpecification(path, mimeType)

    /** A metadata file generated for the build process. */
    class MetadataFileSpecification(
        path: FilePath,
        mimeType: MimeType?,
        val content: String,
    ) : OutputFileSpecification(path, mimeType)

    /** A raw data file generated for the build process. */
    class ByteArrayFileSpecification(
        path: FilePath,
        val content: ByteArray,
    ) : OutputFileSpecification(path, null)

    /** A source file copied from a resource */
    class ResourceFileSpecification(
        targetBasePath: FilePath,
        val content: ResourceDescriptor,
        mimeType: MimeType?,
    ) : OutputFileSpecification(targetBasePath + content.rsrcPath, mimeType)

    /**
     * The backend's companion object should implement this interface and be added to
     * [lang.temper.supportedBackends] under `defaultSupportedBackendList`.
     */
    interface Factory<BACKEND : Backend<BACKEND>> {
        val backendId: BackendId
        val backendMeta: BackendMeta

        /**
         * Should apply to frontend processing whenever this backend is active.
         * When the same name is defined by multiple active backends, the winner is undefined.
         * This currently is used to provide custom decorator handling.
         * TODO Ideally we replace this with imported user-space definitions at some point.
         */
        val environmentBindings: Map<TemperName, Value<*>>
            get() = emptyMap()

        /** See [BackendHelpTopicKeys] for more info. */
        val extraHelpTopics: Map<BackendHelpTopicKey, OccasionallyHelpful>
            get() = emptyMap()

        /** We may add other specifics to a backend that supports multiple compilers. */
        val specifics: RunnerSpecifics
        val coreLibraryResources: List<ResourceDescriptor>

        /** The [cliEnv] should be inside temper.out/ with backend dirs at next level. */
        fun processCoreLibraryResources(cliEnv: CliEnv, console: Console) {}

        /** Allows skipping even setting up a [CliEnv]. Set to `true` to enable [processCoreLibraryResources]. */
        val processCoreLibraryResourcesNeeded get() = false

        /**
         * Creates and returns a backend instance focused on translating the library
         * named by [setup's libraryName][BackendSetup.libraryName] using its
         * [module list][BackendSetup.modules].
         */
        fun make(setup: BackendSetup<BACKEND>): Backend<BACKEND>
    }

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    /**
     * Applies to concrete subtypes of [Factory] so that they may be loaded
     * via the *TEMPER_PLUGINS* environment variable.
     */
    annotation class PluginBackendId(
        /**
         * A valid value for [BackendId.uniqueId].
         * Must correspond to the annotated factory's [Factory.backendId].
         */
        val backendId: String,
    )

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    /**
     * Applies to concrete subtypes of [Factory] so that they may be loaded
     * via the *TEMPER_PLUGINS* environment variable.
     */
    annotation class BackendSupportLevel(
        /**
         * Whether the backend shows up in the backend list in the `temper help` documentation;
         * typically because it has graduated from experimental and not been deprecated.
         */
        val isSupported: Boolean = false,
        /** Whether the backend should be used when `temper build` and related commands are run with no `-b` flag. */
        val isDefaultSupported: Boolean = false,
        /** Whether the backend shows up in the functional-test-matrix. */
        val isTested: Boolean = false,
    )
}

/**
 * Information needed to create a [Backend].
 *
 * [Backend.Factory] implementations may thread this through the [Backend]
 * implementation to the [Backend] superclass's constructor, but it's also
 * trivially copyable, mutatable in case a factory has to do something smart.
 *
 * This class can be updated and versioned separately from individual factory
 * implementations.
 */
data class BackendSetup<BACKEND : Backend<BACKEND>>(
    /**
     * The name of the library in [dependenciesBuilder]'s
     * [configuration bundle][Dependencies.Builder.libraryConfigurations]
     */
    val libraryName: DashedIdentifier,
    /**
     * Shared by all backends with the same target for the libraries in its
     * configurations bundle.
     */
    val dependenciesBuilder: Dependencies.Builder<BACKEND>,
    /**
     * The modules for the library named by [libraryName].
     */
    val modules: List<Module>,
    val buildFileCreator: AsyncSystemAccess,
    val keepFileUpdater: AsyncSystemReadAccess,
    /** Receives translation errors */
    val logSink: LogSink,
    val dependencyResolver: DependencyResolver,
    val config: Backend.Config,
)

private fun sourceMapFile(outputSourceFile: FilePath): FilePath {
    check(outputSourceFile.isFile)
    val dir = outputSourceFile.dirName()
    val fileName = outputSourceFile.last()
    val sourceMapFileName = FilePathSegment("${fileName.fullName}${SourceMap.EXTENSION}")
    return dir + sourceMapFileName
}

val globalPathSegment = FilePathSegment("-global")

typealias ProviderEntry = Pair<TmpL.Module, TmpL.TopLevelDeclarationOrNestedTypeName>
typealias ProviderList = List<ProviderEntry>
typealias MutProviderList = MutableList<ProviderEntry>

typealias ProviderNameMap = Map<ModularName, ProviderList>
typealias MutProviderNameMap = MutableMap<ModularName, MutProviderList>

data class ExternalNamesProvided(
    val providers: ProviderNameMap,
    val localProviders: ProviderNameMap,
    val sameLibraryModuleLocations: Set<ModuleLocation>,
)

private const val FALLBACK_FILE_PATH_BASENAME = "unnamed"

const val NAME_SELECTION_JSON_FILE = "name-selection.json"

/** All groups exclude backends for which no factory is found. */
data class BackendOrganization(
    /** Groups of backends that are independent in each group and where later groups depend on earlier groups. */
    val backendBuckets: List<List<BackendId>>,

    /** The full set of backends needed for each needed backend, each including itself. */
    val backendRequirements: Map<BackendId, Set<BackendId>>,

    /** The factory for each backend. */
    val factoriesById: Map<BackendId, Backend.Factory<*>>,
)

/** Calculate transitive backend organization as needed by the given initially requested [backendIds]. */
fun organizeBackends(
    backendIds: Iterable<BackendId>,
    lookupFactory: (BackendId) -> Backend.Factory<*>?,
    onMissingFactory: (BackendId) -> Unit,
): BackendOrganization {
    // We need to order how we drive backends.
    // A backend for one target language might require translations for another target language.
    // For example, one language translations might connect to another's via an ABI:
    // - Python using C++
    // - JavaScript using Wasm binaries
    // The backendDep map us group backends together into buckets so that each bucket doesn't
    // have any intra-bucket dependencies but each bucket follows the ones that any of its bucket
    // members depend upon.
    val backendOrdering = mutableMapOf<BackendId, Set<BackendId>>()
    val factoriesById: Map<BackendId, Backend.Factory<*>> = buildMap {
        val factories = mutableMapOf<BackendId, Backend.Factory<*>?>()
        fun factoryFor(backendId: BackendId) = factories.getOrPut(backendId) {
            lookupFactory(backendId).also { factory ->
                if (factory == null) {
                    onMissingFactory(backendId)
                } else {
                    backendOrdering[backendId] = factory.backendMeta.requiredBackendIds.toSet()
                }
            }
        }
        val remainingIds = ArrayDeque<BackendId>()
        remainingIds.addAll(backendIds)
        while (true) {
            val backendId = remainingIds.removeFirstOrNull() ?: break
            val factory = factoryFor(backendId) ?: continue
            put(backendId, factory)
            for (required in factory.backendMeta.requiredBackendIds) {
                if (required !in this) {
                    remainingIds.add(required)
                }
            }
        }
    }
    val backendOrderingFounds = backendOrdering.mapValues { it.value intersect factoriesById.keys }
    val backendBuckets: List<List<BackendId>> = buildList {
        // Build the bucket list in reverse.
        // We pull off the maximal list of buckets such that they do not require anything
        // in a bucket that's already scheduled.
        // This should happen after everything that's remaining.  We add that list as a
        // bucket though and reverse at the end.
        val inBuckets = mutableSetOf<BackendId>()
        val toBucket = factoriesById.keys.toMutableSet()
        while (toBucket.isNotEmpty()) {
            val bucket = mutableSetOf<BackendId>()
            for (backendId in toBucket) {
                val requireds = backendOrderingFounds[backendId]
                if (requireds != null) {
                    // Treat any missing backends as already handled.
                    inBuckets.addAll(requireds - factoriesById.keys)
                }
                if (requireds == null || requireds.all { it in inBuckets }) {
                    bucket.add(backendId)
                }
            }
            if (bucket.isEmpty()) {
                // Cycle in backend dependencies
                toBucket.filterTo(bucket) { it !in inBuckets }
            }
            toBucket.removeAll(bucket)
            inBuckets.addAll(bucket)
            this.add(bucket.toList())
        }
    }
    val backendRequirements = transitiveClosure(backendOrderingFounds).mapValues { setOf(it.key) + it.value }
    return BackendOrganization(
        backendBuckets = backendBuckets,
        backendRequirements = backendRequirements,
        factoriesById = factoriesById,
    )
}
