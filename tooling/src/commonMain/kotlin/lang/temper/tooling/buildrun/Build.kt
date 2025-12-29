package lang.temper.tooling.buildrun

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.Dependencies
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.ExecInteractiveRepl
import lang.temper.be.cli.RunBackendSpecificCompilationStepRequest
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.organizeBackends
import lang.temper.be.syncstaging.applyBackendsSynchronously
import lang.temper.builtin.OPTIONAL_PRINT_FEATURE_KEY
import lang.temper.common.CustomValueFormatter
import lang.temper.common.Log
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.RThrowable
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.ExecutorService
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.SignalRFuture
import lang.temper.common.json.JsonValue
import lang.temper.common.max
import lang.temper.common.prefixLinesWith
import lang.temper.common.printErr
import lang.temper.compile.GatheredLibrary
import lang.temper.compile.LibraryTranslation
import lang.temper.compile.makePublicationHistoryDependencyResolver
import lang.temper.format.ConsoleBackedContextualLogSink
import lang.temper.frontend.Module
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.define.testReportExportName
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.frontend.staging.SourceFilePartition
import lang.temper.frontend.staging.fallbackLibraryName
import lang.temper.frontend.staging.makeContinueCondition
import lang.temper.frontend.staging.partitionModulesIntoLibraries
import lang.temper.fs.AsyncSystemAccess
import lang.temper.fs.AsyncSystemReadAccess
import lang.temper.fs.FileSnapshot
import lang.temper.fs.FilteringFileSystemSnapshot
import lang.temper.lexer.defaultClassifyTemperSource
import lang.temper.library.DependencyResolver
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.LeveledMessageTemplate
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.unknownPos
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.name.ExportedName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.name.interpBackendId
import lang.temper.result.junit.parseJunitResults
import lang.temper.stage.Stage
import lang.temper.supportedBackends.lookupFactory
import lang.temper.supportedBackends.supportedBackends
import lang.temper.value.Abort
import lang.temper.value.DeclTree
import lang.temper.value.DependencyCategory
import lang.temper.value.Panic
import lang.temper.value.TBoolean
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.testSymbol
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates, but does not execute, a build object with its own
 * [harness][BuildHarness] or returns null if no valid harness could be constructed.
 */
fun prepareBuild(
    executorService: ExecutorService,
    backends: List<BackendId>,
    workRoot: Path,
    ignoreFile: Path?,
    shellPreferences: ShellPreferences,
    backendConfig: Backend.Config = Backend.Config.production,
    outDir: Path? = null,
    keepDir: Path? = null,
    moduleConfig: ModuleConfig,
    requiredExt: List<DashedIdentifier> = emptyList(),
    runTask: RunTask? = null,
): Build? {
    val h = try {
        BuildHarness(
            executorService = executorService,
            shellPreferences = shellPreferences,
            workRoot = workRoot,
            ignoreFile = ignoreFile,
            outDir = outDir,
            keepDir = keepDir,
            backends = backends,
            backendConfig = backendConfig,
        )
    } catch (_: BuildAbortedDueToInitFailure) {
        return null
    }

    val pluggedInConfig = moduleConfig.copy(
        moduleCustomizeHook = { module, isNew ->
            moduleConfig.moduleCustomizeHook.customize(module, isNew)
            for (backendId in supportedBackends) {
                module.addEnvironmentBindings(lookupFactory(backendId)!!.environmentBindings)
            }
        },
    )
    return Build(
        harness = h,
        requiredExt = requiredExt,
        runTask = runTask,
        moduleConfig = pluggedInConfig,
    )
}

/** [Prepare][prepareBuild]s and executes a build, closing the harness afterward. */
fun doBuild(
    executorService: ExecutorService,
    backends: List<BackendId>,
    workRoot: Path,
    ignoreFile: Path?,
    shellPreferences: ShellPreferences,
    backendConfig: Backend.Config = Backend.Config.production,
    outDir: Path? = null,
    requiredExt: List<DashedIdentifier> = emptyList(),
    runTask: RunTask? = null,
    moduleConfig: ModuleConfig = ModuleConfig.default,
): BuildResult {
    val build =
        prepareBuild(
            executorService = executorService,
            backends = backends,
            workRoot = workRoot,
            ignoreFile = ignoreFile,
            shellPreferences = shellPreferences,
            backendConfig = backendConfig,
            outDir = outDir,
            requiredExt = requiredExt,
            runTask = runTask,
            moduleConfig = moduleConfig,
        )
            ?: return BuildInitFailed(ok = false, maxLogLevel = Log.Error)
    return build.harness.use {
        build.use {
            doOneBuild(build)
        }
    }
}

private data class StagingResult(
    val modulesInOrder: List<Module>,
    val libraries: List<Pair<LibraryConfiguration, List<Module>>>,
    val libraryConfigurationsBuilder: LibraryConfigurationsBundle,
    val projectLogSink: LogSink,
    val priorBuildSuffices: Boolean,
)

/**
 * Gather source files, turns them into modules, and stages them to [Stage.GenerateCode]
 */
private fun stageLibraries(
    logLevelTracker: MaxLogLevelTracker,
    build: Build,
): StagingResult? {
    val harness = build.harness
    val cliConsole = harness.cliConsole
    val moduleConfig = build.moduleConfig

    val moduleAdvancer = ModuleAdvancer(LogSink.devNull, moduleConfig = moduleConfig)
    val projectLogSink = ConsoleBackedContextualLogSink(
        cliConsole, moduleAdvancer.sharedLocationContext, logLevelTracker, CustomValueFormatter.Nope,
    )
    moduleAdvancer.projectLogSink = projectLogSink
    fun makeCancellableContinueCondition(): () -> Boolean {
        val continueCondition = makeContinueCondition()
        return {
            !build.isCancelled && continueCondition()
        }
    }
    fun makeTentativeLibraryConfiguration(
        libraryNameGuess: DashedIdentifier,
        libraryRoot: FilePath,
    ): LibraryConfiguration = LibraryConfiguration(
        libraryName = libraryNameGuess,
        libraryRoot = libraryRoot,
        classifyTemperSource = ::defaultClassifyTemperSource,
        supportedBackendList = harness.backends,
    )

    val sourceSnapshot = FilteringFileSystemSnapshot(
        fileSystem = harness.workFileSystem,
        fileFilterRules = harness.filterRules,
        root = BuildHarness.workDir,
    )
    sourceSnapshot.error?.let { harness.onIoException(it) }
    if (sourceSnapshot[BuildHarness.workDir] is FileSnapshot.NoSuchFile) {
        cliConsole.error(
            "Ignore rules are filtering out the root ${BuildHarness.workDir} directory",
        )
        return null
    }
    val sourceFilePartition = SourceFilePartition(
        moduleAdvancer = moduleAdvancer,
        console = cliConsole,
        makeAContinueCondition = ::makeCancellableContinueCondition,
        makeTentativeLibraryConfiguration = ::makeTentativeLibraryConfiguration,
    )
    build.previouslyConfigured?.let {
        sourceFilePartition.maybeReusePreviouslyStaged(it)
    }
    sourceFilePartition.maybeReusePreviouslyStaged(build.previouslyCompiled)
    sourceFilePartition.scan(sourceSnapshot, root = BuildHarness.workDir)
    sourceFilePartition.addModulesToAdvancer()
    for (requirement in build.requiredExt) {
        moduleAdvancer.requireLibrary(requirement)
    }

    val modulesPreBuild = moduleAdvancer.getAllModules()
    // If all the modules were already built, we could reuse everything,
    // then there's no point in doing translation.
    // But if there's no modules, then we're trivially reusing everything,
    // but may need to fire up the backends at least once to create output
    // directories and metadata files.
    val priorBuildSuffices = modulesPreBuild.isNotEmpty() &&
        modulesPreBuild.all { module ->
            (module.stageCompleted ?: Stage.Lex) >= Stage.GenerateCode
        }
    moduleAdvancer.advanceModules(stopBefore = Stage.Run)

    val libraryConfigurationsByRoot = mutableMapOf<FilePath, LibraryConfiguration>()
    moduleAdvancer.getAllLibraryConfigurations().forEach {
        var libraryConfiguration = it
        // Finalize the library configurations bundle
        // For example, if `std` was auto-staged it might have an empty backends list.
        if (
            libraryConfiguration.supportedBackendList.isEmpty() &&
            libraryConfiguration.libraryName == DashedIdentifier.temperStandardLibraryIdentifier
        ) {
            libraryConfiguration = libraryConfiguration.copy(supportedBackendList = harness.backends)
        }

        libraryConfigurationsByRoot[libraryConfiguration.libraryRoot] = libraryConfiguration
    }

    val modulesInOrder = moduleAdvancer.getAllModules()

    // Group modules by library and make sure we have configurations for everything.
    val libraries = partitionModulesIntoLibraries(
        modulesInOrder,
        libraryConfigurationsByRoot.keys,
    ) { libraryRoot ->
        libraryConfigurationsByRoot.getOrPut(libraryRoot) {
            projectLogSink.log(
                Log.Error,
                MessageTemplate.MissingLibraryConfiguration,
                Position(libraryRoot, 0, 0),
                emptyList(),
            )
            LibraryConfiguration(
                fallbackLibraryName(),
                libraryRoot,
                harness.backends,
                ::defaultClassifyTemperSource,
            )
        }
    }

    val libraryConfigurationsBundle = LibraryConfigurationsBundle.from(
        libraryConfigurationsByRoot.values.toList(),
    )

    return StagingResult(
        modulesInOrder,
        libraries,
        libraryConfigurationsBundle,
        projectLogSink,
        priorBuildSuffices = priorBuildSuffices,
    )
}

/**
 * Executes a pre-prepared build returning the result.
 */
fun doOneBuild(build: Build): BuildResult {
    val harness = build.harness
    val cliConsole = harness.cliConsole
    val logLevelTracker = MaxLogLevelTracker()
    val cancelGroup = build.cancelGroup

    val (
        modulesInOrder,
        libraries,
        libraryConfigurationsBundle,
        projectLogSink,
        priorBuildSuffices,
    ) = stageLibraries(logLevelTracker, build)
        ?: return BuildInitFailed(ok = false, maxLogLevel = logLevelTracker.maxLogLevel)

    if (priorBuildSuffices) {
        val maxLogLevel = logLevelTracker.maxLogLevel
        return BuildNotNeededResult(
            ok = maxLogLevel < Log.Error &&
                !build.harness.hadIoExceptions.get(),
            maxLogLevel = maxLogLevel,
        )
    }

    // TODO: get an identifier for the build based on the hashes of sources
    // and introduce it here.  Then reuse it for other events.
    val buildListener = harness.buildListener
    buildListener?.processing()

    val dependencyResolverFuture = makePublicationHistoryDependencyResolver(
        cancelGroup,
        // TODO: just make a synchronous version of this
        harness.workFileSystem,
        libraries.map { (configuration, modules) ->
            GatheredLibrary(
                libraryConfigurationsBundle.withCurrentLibrary(configuration),
                modules,
                hasMissingModules = false,
            )
        },
        cliConsole,
        projectLogSink,
    )
    val dependencyResolverResult = dependencyResolverFuture.await()
    val dependencyResolver = when (dependencyResolverResult) {
        is RSuccess -> dependencyResolverResult.result
        is RFailure, is RThrowable -> object : DependencyResolver {
            override fun resolve(loc: ModuleLocation, backendId: BackendId, logSink: LogSink): JsonValue? {
                val libraryRoot = when (loc) {
                    is ImplicitsCodeLocation -> "unknown"
                    is ModuleName -> loc.libraryRoot()
                }
                logSink.log(
                    Log.Error,
                    MessageTemplate.BadPublicationHistory,
                    Position(loc, 0, 0),
                    listOf(libraryRoot),
                )
                return null
            }
        }
    }

    supplyCoreLibrary(harness.outputFileSystem, harness.backends, cancelGroup, harness.cliConsole)

    build.beforeStartTranslation?.await()
    // Explode the backends x libraries to backends that are each responsible for translating one library
    // for one target language.
    val backendOrganization = organizeBackends(
        backendIds = libraries.flatMap { it.first.supportedBackendList }.toSet(),
        lookupFactory = ::lookupFactory,
        onMissingFactory = { backendId ->
            if (backendId != interpBackendId) {
                projectLogSink.log(
                    Log.Error,
                    MessageTemplate.BadBackend,
                    unknownPos,
                    listOf(backendId),
                )
            }
        },
    )

    /**
     * Allows collecting all the `<BACKEND>` associated bits in a type-safe way.
     */
    class FactoryAndBackends<BACKEND : Backend<BACKEND>>(
        val backendId: BackendId,
        val factory: Backend.Factory<BACKEND>,
    ) {
        val deps = Dependencies.Builder<BACKEND>(libraryConfigurationsBundle)
        val backends = mutableMapOf<DashedIdentifier, Backend<BACKEND>>()

        fun make(
            libraryName: DashedIdentifier,
            modules: List<Module>,
            buildFileCreator: AsyncSystemAccess,
            keepFileUpdater: AsyncSystemReadAccess,
        ) {
            backends[libraryName] = factory.make(
                BackendSetup(
                    libraryName = libraryName,
                    dependenciesBuilder = deps,
                    modules = modules,
                    buildFileCreator = buildFileCreator,
                    keepFileUpdater = keepFileUpdater,
                    logSink = projectLogSink,
                    dependencyResolver = dependencyResolver,
                    config = harness.backendConfig,
                ),
            )
        }
    }
    val byBackendId: Map<BackendId, FactoryAndBackends<*>?> = buildMap {
        val byBackendId = this

        data class TranslationBits(
            val backendId: BackendId,
            val modules: List<Module>,
            val libraryName: DashedIdentifier,
        )

        fun getFactoryAndBackends(backendId: BackendId): FactoryAndBackends<*>? =
            byBackendId.getOrPut(backendId) {
                backendOrganization.factoriesById[backendId]?.let { factory ->
                    FactoryAndBackends(backendId, factory)
                }
            }

        fun <BACKEND : Backend<BACKEND>> addBackend(
            factoryAndBackends: FactoryAndBackends<BACKEND>,
            bits: TranslationBits,
        ) {
            val (backendId, modules, libraryName) = bits

            // Make sure we have access to <backend-id>/<library-name>/ dirs
            // under temper.out and temper.keep
            val relPathForOutputFiles = FilePath.emptyPath
                .resolve(FilePathSegment(backendId.uniqueId), isDir = true)
                .resolve(FilePathSegment(libraryName.text), isDir = true)
            val outSystemAccess = RevocableSystemAccess(
                harness.outputFileSystem.systemAccess(relPathForOutputFiles, cancelGroup),
                cancelGroup,
            )
            val keepSystemAccess = RevocableSystemReadAccess(
                harness.keepFileSystem.systemReadAccess(relPathForOutputFiles, cancelGroup),
                cancelGroup,
            )

            factoryAndBackends.make(
                libraryName,
                modules,
                outSystemAccess,
                keepSystemAccess,
            )
        }

        for ((configuration, modules) in libraries) {
            val requiredBackendIds = configuration.supportedBackendList.flatMap { backendId ->
                backendOrganization.backendRequirements.getOrDefault(backendId, listOf())
            }.toSet()
            for (backendId in requiredBackendIds) {
                getFactoryAndBackends(backendId)?.let { factoryAndBackends ->
                    addBackend(factoryAndBackends, TranslationBits(backendId, modules, configuration.libraryName))
                }
            }
        }
    }

    fun cancelCheck(future: RFuture<*, *>?) {
        future?.let { cancelGroup.add(it) }
        cancelGroup.requireNotCancelled()
    }

    fun <BACKEND : Backend<BACKEND>> applyOneGroup(
        factoryAndBackends: FactoryAndBackends<BACKEND>,
    ) {
        applyBackendsSynchronously(
            cancelGroup, factoryAndBackends.backends.values.toList(),
            ::cancelCheck,
        )
    }
    for (backendBucket in backendOrganization.backendBuckets) {
        for (backendId in backendBucket) {
            applyOneGroup(byBackendId[backendId] ?: continue)
        }
    }

    val dependencies: Map<BackendId, Dependencies<out Backend<*>>> = buildMap {
        for ((backendId, boundFactory) in byBackendId) {
            if (boundFactory != null) {
                this[backendId] = boundFactory.deps.build()
            }
        }
    }

    fun okCheck(runResult: DoRunResult?): Boolean {
        val maxLogLevel = logLevelTracker.maxLogLevel
        return maxLogLevel < Log.Error &&
            !build.harness.hadIoExceptions.get() &&
            runResult?.ok != false
    }

    val tentativeBuildResult = BuildDoneResult(
        ok = okCheck(null),
        maxLogLevel = logLevelTracker.maxLogLevel,
        libraryConfigurations = libraryConfigurationsBundle,
        partitionedModules = libraries,
        modulesInOrder = modulesInOrder,
        dependencies = dependencies,
        taskResults = null,
    )
    if (buildListener != null) {
        val translations = byBackendId.values.filterNotNull().map { factoryAndBackends ->
            val backendId: BackendId = factoryAndBackends.backendId
            val backends = factoryAndBackends.backends
            LibraryTranslation(
                backendId = backendId,
                dependencies = dependencies.getValue(backendId),
                libraryIds = backends.map { (libraryName, backend) ->
                    libraryName to backend.buildFileCreator.pathToFileCreatorRoot
                },
            )
        }
        buildListener.translated(translations)
    }

    // Now we've built translations, time to run tests if necessary.
    // We need to fork between running in the interpreter and running by shelling out
    // to backend specific tools.
    val runTask = build.runTask
    var runResult: DoRunResult? = null
    if (runTask != null && runTask.backends.isNotEmpty()) {
        val allRunBackends = runTask.backends
        var request = runTask.request
        val testingNeeded = request is RunTestsRequest
        if (testingNeeded) {
            val runTestsRequest = request
            if (runTestsRequest.libraries == null) {
                // The watch service needs to be able to say "run tests for the libraries that have them"
                request = runTestsRequest.copy(
                    libraries = buildSet {
                        libraries.forEach { (conf, modules) ->
                            if (
                                BuildHarness.workDir.isAncestorOf(conf.libraryRoot) && // is defined in work root
                                modules.any { m -> m.hasTests } // has tests to run
                            ) {
                                add(conf.libraryName)
                            }
                        }
                    },
                )
            }
            val workRootPos = Position(BuildHarness.workDir, 0, 0)
            projectLogSink.log(
                BuildMessageTemplate.PreparingToTest,
                workRootPos,
                listOf(request.libraries!!, allRunBackends),
            )
        }
        val nonInterpBackends = runTask.nonInterpBackends
        val runInInterpreter = runTask.needsInterpreter
        // We're going to shoehorn some interpreter results into the form that real backends produce
        // and fold them into the larger result list later.
        val interpResults: Pair<TestTally?, DoRunResultDetail>? = if (runInInterpreter) {
            runInInterpreter(logLevelTracker, build, request, libraryConfigurationsBundle)
                ?.let { interpResults ->
                    interpResults.testTally to interpResults.details
                }
        } else {
            null
        }

        // Now run any real backends
        runResult =
            if (nonInterpBackends.isNotEmpty()) {
                runNonInterp(
                    runTask.jobName,
                    harness,
                    cancelGroup,
                    tentativeBuildResult,
                    runTask.currentDirectory,
                    request,
                    nonInterpBackends,
                    build.checkpoints,
                )
            } else {
                null
            }
        // Merge the two sets of results and print out a summary
        runResult = mergeResultsFromInterpAndBackends(interpResults, runResult)
        if (testingNeeded) {
            runResult?.testTally?.let { testTally ->
                cliConsole.log(testTally.summary())
                cliConsole.textOutput.flush()
            }
        }
    }
    val maxLogLevel = logLevelTracker.maxLogLevel
    return tentativeBuildResult.copy(
        ok = okCheck(runResult),
        maxLogLevel = maxLogLevel,
        taskResults = runResult,
    )
}

data class Build(
    val harness: BuildHarness,
    val previouslyConfigured: LibraryConfigurationsBundle? = null,
    val previouslyCompiled: List<Module> = emptyList(),
    val requiredExt: List<DashedIdentifier> = emptyList(),
    val runTask: RunTask? = null,
    val moduleConfig: ModuleConfig = ModuleConfig.default,
    val cancelGroup: CancelGroup = BuildRunCancelGroup(harness.executorService),
    val checkpoints: CliEnv.Checkpoints = CliEnv.Checkpoints(),
    /**
     * After staging modules, but before starting translation, awaits this.
     * If doing multiple builds, this should occur after `temper.out` becomes
     * available for writing new files.
     */
    val beforeStartTranslation: SignalRFuture? = null,
) : AutoCloseable {
    override fun close() {
        cancelGroup.cancelAll()
    }

    val isCancelled get() = cancelGroup.isCancelled
}

private class MaxLogLevelTracker : LogSink {
    var maxLogLevel = Log.levels.first()
        private set

    override fun log(level: Log.Level, template: MessageTemplateI, pos: Position, values: List<Any>, fyi: Boolean) {
        synchronized(this) {
            if (level > maxLogLevel) {
                maxLogLevel = level
            }
        }
    }

    override val hasFatal: Boolean
        get() = Log.Fatal <= synchronized(this) { maxLogLevel }
}

internal enum class BuildMessageTemplate(
    override val formatString: String,
    override val suggestedLevel: Log.Level = Log.Fine,
) : LeveledMessageTemplate {
    PreparingToTest("Preparing to test libraries %s on backends %s"),
}

internal val Module.hasTests: Boolean
    get() = when (this.dependencyCategory) {
        DependencyCategory.Production -> false
        DependencyCategory.Test -> {
            val block = this.generatedCode
            block != null && block.children.any {
                it is DeclTree && it.parts?.metadataSymbolMultimap?.contains(testSymbol) != null
            }
        }
    }

private data class InterpreterTestResults(
    val ok: Boolean,
    /** The Junit XML report for each module that does testing. */
    val resultsByModule: Map<ModuleName, String>,
    val consoleOutput: String,
    val details: DoRunResultDetail,
    val testTally: TestTally?,
)

/**
 * Runs modules in the interpreter returning a map of module names to
 * test report XML strings assuming the module hook was
 */
private fun runInInterpreter(
    logLevelTracker: MaxLogLevelTracker,
    build: Build,
    request: ToolchainRequest,
    libraryConfigurationsBundle: LibraryConfigurationsBundle,
): InterpreterTestResults? {
    // We need to capture log output for test runs.
    val printBuffer = SynchronizedPrintBuffer()

    when (request) {
        is ExecInteractiveRepl,
        is RunBackendSpecificCompilationStepRequest,
        -> {
            build.harness.cliConsole.error("Cannot run $request in the interpreter")
            return null
        }
        is RunLibraryRequest,
        is RunTestsRequest,
        -> {
            // handled below
        }
    }

    // We need a set of libraries that we can advance through Stage.Run
    // and which have top-level statements to run tests or run a module.
    // But we don't want the backends to have to translate those statements.
    // So we stage libraries again.

    fun isTestedModule(module: Module): Boolean = request is RunTestsRequest &&
        when (val loc = module.loc) {
            is ImplicitsCodeLocation -> false
            is ModuleName -> when (module.dependencyCategory) {
                DependencyCategory.Test -> {
                    libraryConfigurationsBundle.byLibraryRoot[loc.libraryRoot()]?.libraryName in
                        request.libraries!!
                }
                DependencyCategory.Production -> false
            }
        }

    val oldCustomizeHook = build.moduleConfig.moduleCustomizeHook
    val mayRunBuild = build.copy(
        moduleConfig = build.moduleConfig.copy(
            mayRun = true,
            moduleCustomizeHook = { module, isNew ->
                oldCustomizeHook.customize(module, isNew)
                if (isNew) {
                    module.useFeatures(
                        mapOf(
                            OPTIONAL_PRINT_FEATURE_KEY to Value(
                                PrintOverrideFn { message ->
                                    val text = prefixLinesWith(whole = message, prefix = "${module.loc}:")
                                    printErr(text)
                                    printBuffer.append(text)
                                },
                            ),
                        ),
                    )
                } else if (module.stageCompleted == Stage.SyntaxMacro && isTestedModule(module)) {
                    module.addEnvironmentBindings(
                        mapOf(
                            StagingFlags.defineStageHookCreateAndRunClasses to TBoolean.valueTrue,
                        ),
                    )
                }
            },
        ),
    )
    val stageResults = stageLibraries(logLevelTracker, mayRunBuild) ?: return null

    val modulesInOrder = stageResults.modulesInOrder

    // We're going to grab the XML formatted and exported by the test macro and store it here.
    val testReports = ConcurrentHashMap<ModuleName, String>()

    var allOk = true
    for (module in modulesInOrder) {
        if (module.canAdvance()) {
            val moduleName = module.loc as ModuleName
            var interpFailure: Throwable? = null
            try {
                module.advance()
            } catch (e: Abort) {
                interpFailure = e
            } catch (e: Panic) {
                interpFailure = e
            } catch (
                // Uncaught exceptions during tests warrant reporting
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                interpFailure = e
            }

            if (isTestedModule(module)) {
                val reportName = ExportedName(module.namingContext, testReportExportName)
                val report = TString.unpackOrNull(
                    module.exports?.find { it.name == reportName }?.value,
                ) ?: interpFailure?.let { throwable ->
                    allOk = false
                    @Suppress("SpellCheckingInspection")
                    """
                    |<testsuites>
                    |  <testsuite name='suite' tests='1' failures='1' time='0.0'>
                    |    <testcase name='${throwable::class.simpleName}' time='0.0' />
                    |  </testsuite>
                    |</testsuites>
                    """.trimMargin()
                }
                if (report != null) {
                    testReports[moduleName] = report
                }
            }
        }
        allOk = allOk && module.ok && module.stageCompleted == Stage.Run
    }

    val resultsByModule = testReports.toMap()
    val output = "$printBuffer"

    var numRun = 0
    val allTestFailures = mutableListOf<Pair<String, String>>()
    val allTestNames = mutableListOf<String>()
    for ((_, resultXml) in resultsByModule) {
        val junitResult = parseJunitResults(resultXml)
        val failures = junitResult.failures
        numRun += junitResult.testsRun
        for (failure in failures) {
            allTestFailures.add(failure.name to failure.cause)
            allOk = false
        }
        for (suite in junitResult.suites) {
            for (case in suite.testCases) {
                allTestNames.add(case.name)
            }
        }
    }
    val numDefined = allTestNames.size
    val numFailed = allTestFailures.size

    return InterpreterTestResults(
        ok = allOk,
        resultsByModule = resultsByModule,
        consoleOutput = output,
        details = DoRunResultDetail(
            ok = allOk,
            maxBuildLogLevel = Log.Info,
            output = output,
            backendId = interpBackendId,
            testFailures = allTestFailures.toList(),
            testNames = allTestNames.toList(),
        ),
        testTally = if (request is RunTestsRequest) {
            TestTally(run = numRun, failed = numFailed, defined = numDefined)
        } else {
            null
        },
    )
}

private fun runNonInterp(
    jobName: String,
    harness: BuildHarness,
    cancelGroup: CancelGroup,
    buildResult: BuildDoneResult,
    currentDirectory: Path?,
    request: ToolchainRequest,
    runBackends: Set<BackendId>,
    checkpoints: CliEnv.Checkpoints,
): DoRunResult {
    val workRoot = harness.workRoot
    val args = RunArgs(
        backendIds = runBackends.toList(),
        jobName = jobName,
        workRoot = workRoot,
        currentDirectory = currentDirectory ?: workRoot,
        shellPreferences = harness.shellPreferences,
    )
    val runner = Runner(args, request, cancelGroup)
    runner.checkpoints.addAll(checkpoints)
    return runner.doRun(harness, buildResult)
}

private fun mergeResultsFromInterpAndBackends(
    interpResults: Pair<TestTally?, DoRunResultDetail>?,
    nonInterpResults: DoRunResult?,
): DoRunResult? {
    if (interpResults == null) {
        return nonInterpResults
    }
    val (interpTally, interpDetails) = interpResults
    return nonInterpResults?.copy(
        ok = nonInterpResults.ok && interpDetails.ok,
        maxBuildLogLevel = max(
            nonInterpResults.maxBuildLogLevel,
            interpDetails.maxBuildLogLevel,
        ),
        outputThunk = {
            buildString {
                append(nonInterpResults.outputThunk)
                if (interpDetails.output.isNotEmpty()) {
                    append('\n')
                    append(interpDetails.output)
                }
            }
        },
        testTally = when {
            interpTally == null -> nonInterpResults.testTally
            nonInterpResults.testTally == null -> interpTally
            else -> nonInterpResults.testTally + interpTally
        },
        details = nonInterpResults.details + listOf(interpDetails),
    )
        ?: DoRunResult(
            ok = interpDetails.ok,
            maxBuildLogLevel = interpDetails.maxBuildLogLevel,
            outputThunk = { interpDetails.output },
            testTally = interpTally,
            details = listOf(interpDetails),
        )
}

class AbortCurrentBuild : Error()
