package lang.temper.tooling.buildrun

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import lang.temper.be.cli.RunTestsRequest
import lang.temper.common.OpenOrClosed
import lang.temper.common.currents.CompletableSignalRFuture
import lang.temper.common.currents.SignalRFuture
import lang.temper.common.currents.UnmanagedFuture
import lang.temper.common.currents.runLater
import lang.temper.common.ignore
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.fs.FileChange
import lang.temper.fs.FileWatchService
import lang.temper.lexer.isTemperFile
import lang.temper.name.BackendId
import lang.temper.value.Abort
import lang.temper.value.Panic
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.TimeSource

/**
 * Monitors [harness]'s input file system to automatically kick off builds.
 */
class Watcher(
    val harness: BuildHarness,
    private val limit: Int?,
    private val testBackends: List<BackendId>,
    private val moduleConfig: ModuleConfig = ModuleConfig.default,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val onEachBuildDone: (() -> Unit)? = null,
) : AutoCloseable {
    private var _lastBuildResult: BuildDoneResult? = null
    private var _lastBuildIndex = -1
    private var _openOrClosed: OpenOrClosed? = null
    private var _currentBuild: Build? = null

    /** Signals when [_currentBuild] completes. */
    private var _currentDone: SignalRFuture? = null

    /** For handy delayed consideration in a potential flood of file system events. */
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable).apply { isDaemon = true }
    }

    private fun shutdownScheduler() {
        // Failure is completely unexpected and of little bother. Wrapped here just because used in sensitive places.
        runCatching { scheduler.shutdown() }
    }

    /**
     * Before any new build starts writing files to
     * [BuildHarness.outputFileSystem] or [BuildHarness.keepFileSystem]
     * or spawns child processes that might, we need previous builds to
     * reach a state where they're not going to.
     *
     * This list stores futures related to cancellation, so we can wait
     * until the output directories are clear.
     *
     * We want to avoid situations like the following:
     *
     * 1. Build 1 start translation and writes the file `temper.out/be/somelib/out.ext`
     * 2. It starts a child process, P, that reads that file and reformats them
     * 3. Build 1 is cancelled based on file system changes
     * 4. Build 2 start and writes that same file: `temper.out/be/somelib/out.ext`
     * 5. Build 2's version of process P completes quickly for whatever reason.
     * 6. Process P completes and, based on its in memory read of the file from step 1,
     *    clobbers the rewritten file from step 5.
     */
    private var mustCompleteBeforeTranslation = mutableListOf<SignalRFuture>()
    private var _fileWatcher: FileWatchService? = null

    val lastBuildResult: BuildResult?
        get() {
            synchronized(this) {
                return _lastBuildResult
            }
        }

    private var _allDoneFuture: CompletableSignalRFuture? = null
    private var _watchTask: SignalRFuture? = null
    fun start(): SignalRFuture {
        val watchTaskBody = {
            val fileWatcher = synchronized(this@Watcher) { _fileWatcher!! }
            var error: Throwable? = null
            runBlocking {
                @Suppress("TooGenericExceptionCaught") // reported and rethrown
                try {
                    while (true) {
                        val fileChanges = fileWatcher.changes.receiveCatching().getOrNull()
                            ?: break
                        processFileChanges(fileChanges)
                    }
                } catch (e: Throwable) {
                    error = e
                    throw e
                } finally {
                    shutdownScheduler()
                    synchronized(this@Watcher) { _allDoneFuture }?.let { allDoneFuture ->
                        when (error) {
                            null -> allDoneFuture.completeOk(Unit)
                            else -> allDoneFuture.completeError(error!!)
                        }
                    }
                }
            }
        }
        return synchronized(this) {
            check(_openOrClosed == null)
            _openOrClosed = OpenOrClosed.Open

            this._fileWatcher = harness.workFileSystem.createWatchService(BuildHarness.workDir).result!!
            val allDoneFuture: CompletableSignalRFuture =
                UnmanagedFuture.newCompletableFuture("watcher done", harness.executorService)
            this._allDoneFuture = allDoneFuture
            this._watchTask = UnmanagedFuture.runLater(
                description = "watch file-system for changes",
                executorService = harness.executorService,
                body = watchTaskBody,
            )
            allDoneFuture
        }
    }

    private fun processFileChanges(fileChanges: List<FileChange>) {
        // If there's a change to a temper file, then we might have to rebuild.
        val mightNeedRebuild = fileChanges.any {
            it.filePath.isTemperFile && !harness.filterRules.isIgnored(it.filePath)
        }
        if (mightNeedRebuild) {
            scheduleBuild()
        }
    }

    // Might not ever be an actual build.
    private var _maybeBuildId = 0
    private fun scheduleBuild() {
        val maybeBuildId = synchronized(this) {
            _maybeBuildId += 1
            _maybeBuildId
        }
        waitThenBuild(maybeBuildId)
    }

    private fun waitThenBuild(maybeBuildId: Int) {
        synchronized(this) {
            if (_openOrClosed != OpenOrClosed.Open) { return@waitThenBuild }
        }
        scheduler.schedule(
            {
                val shouldStillRun = synchronized(this@Watcher) {
                    _openOrClosed == OpenOrClosed.Open &&
                        maybeBuildId == _maybeBuildId
                }
                if (shouldStillRun) {
                    // Actually run builds on the main executor service.
                    harness.executorService.submit { startBuild() }
                }
            },
            BUILD_DELAY_MILLIS + BUILD_DELAY_MILLIS / 2, TimeUnit.MILLISECONDS,
        )
    }

    fun startBuild() {
        val buildDone: CompletableSignalRFuture
        val buildIndex: Int
        val lastBuild: BuildDoneResult?
        val outputDirectoriesFree: SignalRFuture
        synchronized(this) {
            if (this._openOrClosed != OpenOrClosed.Open) {
                return@startBuild
            }
            buildIndex = ++this._lastBuildIndex
            val currentBuild = this._currentBuild
            currentBuild?.cancelGroup?.cancelAll()?.let { buildCancelled ->
                // We keep a list here because there might be scenarios like the following
                // where tracking one future is not sufficient.
                //
                // 1. Build 1 stages its modules and starts translating, then is cancelled.
                // 2. Build 2 starts staging its modules but is cancelled before it starts
                //    translation so never awaits build 1's has-finished-teardown promise.
                // 3. Build 3 starts and needs to await build 1's has-finished-teardown promise.
                //    For robustness, there's no harm in awaiting build 2's too.
                mustCompleteBeforeTranslation.add(buildCancelled)
                // To avoid this list growing without bound, we just remove any promises that
                // are demonstrably done.
                mustCompleteBeforeTranslation.removeAll { it.isDone }
            }
            outputDirectoriesFree = UnmanagedFuture.join(
                mustCompleteBeforeTranslation,
                harness.executorService,
            )

            buildDone = UnmanagedFuture.newCompletableFuture("Build $buildIndex done", harness.executorService)
            this._currentDone = buildDone
            lastBuild = _lastBuildResult
        }

        buildDone.thenDo("handle end of build #$buildIndex") {
            onEachBuildDone?.let { it() }
            if (limit != null) {
                if (buildIndex + 1 >= limit) {
                    harness.cliConsole.info("Watcher reached build limit")
                    this.close()
                }
            }
        }

        val cancelGroup = BuildRunCancelGroup(harness.executorService)
        cancelGroup.runLater("Build #$buildIndex") {
            try {
                val runTask = if (testBackends.isNotEmpty()) {
                    RunTask(
                        // libraries = null tells the builder to figure it out from
                        // the set of modules found by scanning and their DependencyCategories.
                        request = RunTestsRequest(taskName = "watch", libraries = null),
                        backends = testBackends.toSet(),
                    )
                } else {
                    null
                }

                val build = Build(
                    harness = harness,
                    previouslyConfigured = lastBuild?.libraryConfigurations,
                    previouslyCompiled = lastBuild?.partitionedModules?.flatMap { it.second }
                        ?: emptyList(),
                    runTask = runTask,
                    moduleConfig = moduleConfig,
                    cancelGroup = cancelGroup,
                    beforeStartTranslation = outputDirectoriesFree,
                )
                synchronized(this@Watcher) {
                    if (_lastBuildIndex == buildIndex) { // Still current
                        _currentBuild = build
                    } else {
                        return@runLater
                    }
                }

                // Both sets of cleaned digits here hardcode to '.' for fractional.
                val startTime = Clock.System.now().toString().cleanDigits()
                harness.cliConsole.info("Watch starting build #$buildIndex at $startTime")
                var result: BuildResult? = null
                val mark = timeSource.markNow()
                try {
                    // Actually run build.
                    result = doOneBuild(build)
                } catch (e: AbortCurrentBuild) {
                    ignore(e)
                } catch (e: Abort) {
                    ignore(e)
                } catch (e: Panic) {
                    ignore(e)
                } finally {
                    // Report finish. And if slow, it's ok. This isn't a tight loop.
                    val elapsed = mark.elapsedNow().toString().cleanDigits()
                    harness.cliConsole.info("Finished build #$buildIndex in $elapsed")
                }

                // Store the result if it was not a result of cancellation
                synchronized(this@Watcher) {
                    when (result) {
                        is BuildDoneResult -> {
                            if (_lastBuildIndex == buildIndex) { // Still current
                                if (result.libraryConfigurations.byLibraryRoot.isNotEmpty()) {
                                    // The build didn't fail fast due to an egregious error, so
                                    // there's enough to be worth remembering.
                                    this._lastBuildResult = result
                                }
                            }
                        }

                        is BuildInitFailed,
                        is BuildNotNeededResult,
                        null,
                        -> {
                        }
                    }
                }
            } finally {
                // Signal done after the result is stored.
                buildDone.completeOk(Unit)
            }
        }
    }

    fun awaitCurrentBuild() {
        val future = synchronized(this) {
            _currentDone
        }
        future?.await()
    }

    override fun close() {
        val pending: SignalRFuture
        val allDone: CompletableSignalRFuture?
        shutdownScheduler()
        synchronized(this) {
            _openOrClosed = OpenOrClosed.Closed
            val allCancelled = _currentBuild?.cancelGroup?.cancelAll()
            _fileWatcher?.close()
            val watchTask = _watchTask
            watchTask?.cancel()
            allDone = _allDoneFuture
            _allDoneFuture = null
            _fileWatcher = null
            pending = UnmanagedFuture.join(listOfNotNull(allCancelled, watchTask, _currentDone))
        }
        pending.thenDo("all done") {
            allDone?.completeOk(Unit)
        }
    }
}

private const val BUILD_DELAY_MILLIS = 100L

private val digitRegex = Regex("""(\.\d{0,3})\d+""")
private fun String.cleanDigits(): String {
    return replace(digitRegex, "$1")
}
