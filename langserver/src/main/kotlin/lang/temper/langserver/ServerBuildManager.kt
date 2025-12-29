package lang.temper.langserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import lang.temper.be.cli.ShellPreferences
import lang.temper.common.ContentHash
import lang.temper.common.Log
import lang.temper.common.OpenOrClosed
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.Snapshotter
import lang.temper.common.currents.CompletableSignalRFuture
import lang.temper.common.currents.UnmanagedFuture
import lang.temper.common.ignore
import lang.temper.common.orThrow
import lang.temper.common.subListToEnd
import lang.temper.compile.TopLevelConsoleLogSink
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.fs.LayeredFileSystem
import lang.temper.fs.MemoryFileSystem
import lang.temper.fs.OutputRoot
import lang.temper.fs.RealFileSystem
import lang.temper.fs.RealWritableFileSystem
import lang.temper.fs.StitchedFileSystem
import lang.temper.fs.TEMPER_KEEP_NAME
import lang.temper.fs.TEMPER_OUT_NAME
import lang.temper.fs.getDirectories
import lang.temper.lexer.isTemperFile
import lang.temper.library.findRoot
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.MessageTemplate
import lang.temper.log.unknownPos
import lang.temper.tooling.ModuleData
import lang.temper.tooling.ModuleDataStore
import lang.temper.tooling.ModuleDataUpdate
import lang.temper.tooling.ServerBuildSnapshotter
import lang.temper.tooling.buildrun.BuildHarness
import lang.temper.tooling.buildrun.BuildRunCancelGroup
import lang.temper.tooling.buildrun.Watcher
import org.eclipse.lsp4j.WorkspaceFolder
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.relativeTo

private const val CONTEXT_HASH_SIZE = 32
internal const val MAGIC_DELAY_FOR_CHANGES_MS = 200L

@DelicateCoroutinesApi
internal class ServerBuildManager(
    val languageServer: TemperLanguageServerImpl,
    private val unexpectedClose: (ServerBuildManager) -> Unit = {},
    val workRoot: Path,
) {
    val liveFileSystem = MemoryFileSystem()
    private val fileSystem = LayeredFileSystem(
        StitchedFileSystem(mapOf(BuildHarness.workDir to liveFileSystem)),
        RealFileSystem(workRoot, BuildHarness.workDir),
    )
    private val console = languageServer.console
    private val executorService = languageServer.executorService

    internal val serverBuildScope = CoroutineScope(Dispatchers.Default)

    private val logSink = TopLevelConsoleLogSink(
        coroutineScope = serverBuildScope,
        console = console,
    )

    @Volatile
    var openOrClosed = OpenOrClosed.Open
        private set

    /** Hash value to simplify storage names for workspace roots. */
    internal val contextHash = runBlocking {
        // I'm ok with risk of collision here, so what's a more efficient hash algorithm?
        // String.hashCode() is terrible.
        val hash = ContentHash.fromChars("SHA-256", "$workRoot").orThrow()
        hash.content.hexEncode().slice(0 until CONTEXT_HASH_SIZE)
    }

    private val userCacheDir = getDirectories(fileSystem = workRoot.fileSystem).userCacheDir

    // Make `buildBase` before `build` or else `buildServerOutputRoot` can try to use it.
    internal val buildBase = run {
        val base = userCacheDir.resolve("workRoots").resolve(contextHash)
        console.log("Build base for $workRoot at $base")
        base
    }
    internal val keepBase = run {
        val base = userCacheDir.resolve("keepRoots").resolve(contextHash)
        console.log("Keep base for $workRoot at $base")
        base
    }

    private val userSignalledDone: CompletableSignalRFuture =
        UnmanagedFuture.newCompletableFuture("User signalled done", executorService)

    // TODO: Ideally we would get the cancel group from the Watcher for the workspace,
    // or better yet have the watcher kick off doc gen so that everything is scoped to a
    // build.
    private val cancelGroup = BuildRunCancelGroup(executorService)
    init {
        userSignalledDone.thenDo("cancel when user done") {
            if (it is RSuccess) {
                cancelGroup.cancelAll()
            }
        }
    }

    private var docGenJob: Job
    val docGen = ServerDocGen(
        fileSystem = fileSystem,
        languageServer = languageServer,
        cancelGroup = cancelGroup,
        logSink = logSink,
        workRoot = workRoot,
    ).also { docGen ->
        // Exits on `build.closeJoin`.
        docGenJob = serverBuildScope.launch { docGen.run() }
    }

    /** This supports only one listener, which we expect to be [languageServer].updatePublisher. */
    val moduleDataUpdateChannel = Channel<ModuleDataUpdate>(Channel.UNLIMITED)

    private val moduleDataStore = ModuleDataStore(channel = moduleDataUpdateChannel, console = console)

    private val harness = BuildHarness(
        executorService = executorService,
        shellPreferences = ShellPreferences(
            verbosity = ShellPreferences.Verbosity.Quiet,
            onFailure = ShellPreferences.OnFailure.Release,
            console = console,
        ),
        workRoot = workRoot,
        workFileSystem = fileSystem,
        ignoreFile = null,
        outDir = buildBase,
        keepDir = keepBase,
        backends = emptyList(),
    )

    val watcher = Watcher(
        harness = harness,
        limit = null,
        testBackends = emptyList(),
        moduleConfig = ModuleConfig(
            makeModuleLogSink = { name, projectLogSink ->
                runBlocking { moduleDataStore.makeModuleLogSink(name.sourceFile, projectLogSink) }
            },
            snapshotter = makeSnapshotter(),
        ),
    )

    init {
        // Copied from NewWatch. TODO Factor? There would be lots of parameters here.
        userSignalledDone.then("running watch service") {
            RResult.of {
                // TODO Really only close on success?
                if (it is RSuccess) {
                    harness.use {
                        watcher.use {
                            // using `.use` to close safely
                        }
                    }
                }
            }
        }
    }

    private val watchThread = Thread(
        {
            val allDoneFuture = watcher.start()
            watcher.startBuild()
            allDoneFuture.await()
            // If we think we're still open, then something ended wrongly.
            if (openOrClosed == OpenOrClosed.Open) {
                console.log("Unexpected finished build: $workRoot")
                // This is unexpected, so report up the chain.
                unexpectedClose(this@ServerBuildManager)
            }
        },
        "Watch $workRoot",
    )

    init {
        // TODO Defer this to some start method?
        // TODO See wrapSemiInteractive for some needed cleanup.
        Runtime.getRuntime().addShutdownHook(
            Thread(null, { userSignalledDone.completeOk(Unit) }, "End watch at shutdown"),
        )
        watchThread.start()
    }

    fun joinWatchThread() {
        runCatching { watchThread.join() }
    }

    fun expectChanges(path: FilePath) {
        // TODO Mark somewhere that we expect changes.
        ignore(path)
    }

    suspend fun awaitUnstable(path: FilePath) {
        // For now, just wait our max amount. Currently at 200ms, so not that bad.
        // TODO Go back to actually waiting for unstable.
        ignore(path)
        delay(MAGIC_DELAY_FOR_CHANGES_MS)
        // // Give timeout to be safe.
        // withTimeoutOrNull(MAGIC_DELAY_FOR_CHANGES_MS) {
        //     buildTracker.stateTracker.first(path) { it != BuildState.Stable }
        // }
    }

    suspend fun <Result> readStable(
        path: FilePath,
        cancelled: AtomicBoolean,
        default: () -> Result,
        label: String? = null,
        suggestMissingConfigDelay: SuggestionDelay = SuggestionDelay.None,
        action: suspend (ModuleData?) -> Result,
    ): Result? {
        val primedStore = accessStoreForStable(
            path = path,
            cancelled = cancelled,
            label = label,
            suggestMissingConfigDelay = suggestMissingConfigDelay,
        ) ?: return default()
        // This duplicates a read action, but in the context, it shouldn't be too expensive.
        return primedStore.read(path, awaitFinish = true, label = label) { moduleData ->
            // Could technically have changed to null by now.
            moduleData?.let { action(it) }
        }
    }

    /**
     * Provides more general access to a module data store while still primed for stability of a focus module.
     * TODO(tjp, tooling): Perhaps a more general query system might simplify usage and consistency.
     */
    suspend fun accessStoreForStable(
        path: FilePath,
        cancelled: AtomicBoolean,
        label: String? = null,
        suggestMissingConfigDelay: SuggestionDelay = SuggestionDelay.None,
    ): ModuleDataStore? {
        // TODO First wait to make sure we don't expect changes soon?
        // On timeout, wait for a finished module.
        withTimeoutOrNull(MAGIC_DELAY_FOR_CHANGES_MS) {
            moduleDataStore.read(path, awaitFinish = true, label = label) { moduleData ->
                // Here we access the focus module data just to have it primed.
                if (moduleData == null) {
                    // But while in this area, if the module is missing, do further checks.
                    maybeSuggestLibraryConfig(suggestMissingConfigDelay, path)
                }
                moduleData
            }
        } ?: return null
        return when (cancelled.get()) {
            true -> null
            false -> moduleDataStore
        }
    }

    // This start doesn't really matter as long as it's in the past.
    private var lastSuggestionTimeMillis = 0L

    internal fun maybeSuggestLibraryConfig(suggestMissingConfigDelay: SuggestionDelay, path: FilePath) {
        val yesShouldSuggest = shouldSuggest(suggestMissingConfigDelay, lastSuggestionTimeMillis)
        if (yesShouldSuggest && path.findRoot(fileSystem) == null) {
            lastSuggestionTimeMillis = System.currentTimeMillis()
            // We only want to do this occasionally or when the user gives explicit signals.
            languageServer.client?.let { client ->
                suggestLibraryConfig(
                    client = client,
                    console = console,
                    forFile = path.unprefixWork(),
                    workspaceFolder = workRoot,
                )
            }
        }
    }

    fun closeJoin() {
        runBlocking {
            openOrClosed = OpenOrClosed.Closed
            serverBuildScope.cancel()
            docGenJob.join()
            // The .send doesn't join on the language server handling, and that's ok.
            languageServer.updatePublisher.channel.send(ModuleDataStoreCloseUpdate(this@ServerBuildManager))
        }
        // We're mainly concerned here with the build being closed.
        userSignalledDone.completeOk(Unit)
        watchThread.join()
    }

    internal fun buildServerOutputRoots(): Pair<OutputRoot, OutputRoot> {
        val roots = listOf(
            buildBase.resolve(TEMPER_OUT_NAME), keepBase.resolve(TEMPER_KEEP_NAME),
        ).map { rootDir ->
            Files.createDirectories(rootDir)
            val outputFs = RealWritableFileSystem(rootDir) {
                logSink.log(Log.Error, MessageTemplate.UnexpectedException, unknownPos, listOf(it))
            }
            OutputRoot(outputFs)
        }

        return roots[0] to roots[1]
    }

    private fun makeSnapshotter(): Snapshotter = ServerBuildSnapshotter(
        launchJob = { runBlocking { it() } },
        moduleDataStore = moduleDataStore,
    )

    /**
     * @param workPrefixed whether to prepend the "-work" prefix, which depends on how the path will be used.
     */
    fun toFilePath(path: Path, workPrefixed: Boolean = true): FilePath {
        // Any path coming through here at this time ought to be relative to a workroot.
        // Check against exceptions for now.
        // TODO Maybe we'll see otherwise if we pass std or templandia or such this way.
        check(!(path.startsWith("..") || path.isAbsolute)) {
            "Only contained paths supported so far: $path"
        }
        val filePath = lang.temper.fs.toFilePath(path)
        val result = when (workPrefixed) {
            // We have actual file segments, not any pseudo, so this won't be null.
            true -> BuildHarness.workDir.resolve(filePath.segments, filePath.isDir)
            false -> filePath
        }
        return result
    }

    fun resolve(filePath: FilePath): Path? = when (BuildHarness.workDir.isAncestorOf(filePath)) {
        true -> {
            val relative = filePath.segments.subListToEnd(BuildHarness.workDir.segments.size)
            workRoot.resolve(relative.joinToString(workRoot.fileSystem.separator) { it.fullName })
        }
        // TODO(tjp, tooling): Locate file paths in templandia, etc.
        else -> null
    }

    fun resolveModuleName(loc: CodeLocation) = resolve((loc as FileRelatedCodeLocation).sourceFile)

    // TODO(tjp, tooling): Why was this leading to hanging?
    // private fun <T> runBlocking(block: suspend CoroutineScope.() -> T) =
    //     runBlocking(buildConductor.compilerScope.coroutineContext, block)

    // Skip this for now, since we're not using it yet, and it locks files in the common dir, which sometimes causes
    // trouble for fast langserver restarts.
    // TODO(tjp, tooling): Figure out if we want to reuse the cache space and how. And just a plan to use it at all.
    // TODO(tjp, tooling): See also #327: https://github.com/temperlang/temper/issues/327
    // val snapshotStore = BuildSnapshotStore(
    //     baseDirectory = getDirectories(fileSystem = workRoot.fileSystem).userCacheDir.resolve("workRoots")
    //         .resolve(contextHash),
    //     console = console,
    //     context = workRoot.toString(),
    //     name = "serve",
    // )

    init {
        languageServer.updatePublisher.channel.trySend(ModuleDataStoreOpenUpdate(this)) // unlimited -> ok
    }
}

@DelicateCoroutinesApi
internal data class ManagerPath(val manager: ServerBuildManager, val path: Path)

@DelicateCoroutinesApi
internal class WorkspaceManager(private val languageServer: TemperLanguageServerImpl) {
    /** Exposed internally for test purposes. */
    internal val builds = mutableMapOf<Path, ServerBuildManager>()

    fun builderManagerFor(uri: String, allowNonTemper: Boolean = false): ManagerPath? {
        // TODO(tjp, tooling): If not found (such as for an arbitrary open file), still try to guess context?
        // TODO(tjp, tooling): Open build conductors for ancestor library dirs of any files opened in the editor?
        // TODO(tjp, tooling): Add fictitious imports for them? See also issues #372 and #373.
        val uriObj = URI(uri)
        // Track only temper files, since that's all we'll get build info on.
        allowNonTemper || isTemperFile(uriObj.path ?: "") || return null
        if (uriObj.scheme == "file") {
            val path = Path.of(uriObj).toRealPath()
            for (entry in builds.entries) {
                if (path.startsWith(entry.key)) {
                    return ManagerPath(manager = entry.value, path = path.relativeTo(entry.key))
                }
            }
            languageServer.console.error("No build for: $uri")
        } else {
            languageServer.console.error("Unusable scheme: $uri")
        }
        return null
    }

    @Synchronized
    fun update(roots: List<WorkspaceFolder>) {
        languageServer.console.log("Got folders: $roots")
        // Find what's new and old.
        val newBuildKeys = mutableListOf<Path>()
        val unusedBuildKeys = builds.keys.toMutableSet()
        for (root in roots) {
            val uri = URI(root.uri)
            if (uri.scheme == "file") {
                val path = Path.of(uri).toRealPath()
                if (!unusedBuildKeys.remove(path)) {
                    newBuildKeys.add(path)
                }
            } else {
                languageServer.console.error("Unusable scheme: $root")
            }
        }
        // Out with the old.
        if (unusedBuildKeys.isNotEmpty()) {
            val unusedBuilds = unusedBuildKeys.map { builds.remove(it)!! }
            UnmanagedFuture.runLater(
                "Closing unused builds after workspace update", languageServer.executorService,
            ) {
                // Could split close requests and joins, but this is unlikely to be a long list.
                // Could even just close and ignore joining.
                for (build in unusedBuilds) {
                    build.closeJoin()
                    languageServer.console.log("Closed build: ${build.workRoot}")
                }
            }
        }
        // And in with the new.
        for (key in newBuildKeys) {
            languageServer.console.log("Starting build: $key")
            startBuild(key)
        }
    }

    @Synchronized
    private fun restartBuild(build: ServerBuildManager) {
        if (builds[build.workRoot] === build) {
            languageServer.console.log("Restarting build: ${build.workRoot}")
            startBuild(build.workRoot)
        }
    }

    @Synchronized
    private fun startBuild(key: Path) {
        // Don't need any backends for language server queries.
        builds[key] = ServerBuildManager(
            languageServer = languageServer,
            unexpectedClose = { restartBuild(it) },
            workRoot = key,
        )
    }
}

private fun FilePath.unprefixWork() = unprefix(BuildHarness.workDir) ?: this
