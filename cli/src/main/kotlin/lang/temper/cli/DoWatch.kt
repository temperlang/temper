package lang.temper.cli

import lang.temper.be.cli.ShellPreferences
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.currents.CompletableSignalRFuture
import lang.temper.name.BackendId
import lang.temper.tooling.buildrun.BuildHarness
import lang.temper.tooling.buildrun.Watcher
import java.nio.file.Path
import java.util.concurrent.ExecutorService

/**
 * Prepares a watch service that rebuilds as source files change and starts it running.
 * Returns OK status when the user signals done or the build limit is reached.
 */
internal fun doWatch(
    executorService: ExecutorService,
    backends: List<BackendId>,
    testBackends: List<BackendId>,
    buildLimit: Int?,
    shellPreferences: ShellPreferences,
    workRoot: Path,
    ignoreFile: Path?,
    userSignalledDone: CompletableSignalRFuture,
    outDir: Path? = null,
    keepDir: Path? = null,
    onEachBuildDone: (() -> Unit)? = null,
): Boolean =
    BuildHarness(
        executorService = executorService,
        shellPreferences = shellPreferences,
        workRoot = workRoot,
        ignoreFile = ignoreFile,
        outDir = outDir,
        keepDir = keepDir,
        backends = backends,
    ).use { harness ->
        val watcher = Watcher(
            harness,
            limit = buildLimit,
            testBackends = testBackends,
            onEachBuildDone = onEachBuildDone,
        )
        userSignalledDone.then("running watch service") {
            RResult.of {
                if (it is RSuccess) {
                    harness.use {
                        watcher.use {
                            // using `.use` to close safely
                        }
                    }
                }
            }
        }
        val allDoneFuture = watcher.start()
        watcher.startBuild()
        allDoneFuture.await().throwable?.let { throwable ->
            // This should only happen for bad bugs. TODO Find better logging anyway?
            throwable.printStackTrace()
            return false
        }
        watcher.lastBuildResult?.ok ?: false
    }
