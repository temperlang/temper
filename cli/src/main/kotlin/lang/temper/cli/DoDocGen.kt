package lang.temper.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.coroutines.DelicateCoroutinesApi
import lang.temper.common.Console
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.fs.TEMPER_OUT_NAME
import lang.temper.name.BackendId
import lang.temper.tooling.buildrun.BuildRunCancelGroup
import lang.temper.tooling.runDocGen
import java.nio.file.Path

@ExperimentalCli
@DelicateCoroutinesApi
internal fun Main.doDocGen(
    workRoot: Path,
    outputDirectory: Path?,
    backends: List<BackendId>,
    cliConsole: Console,
): Boolean {
    // DocGen currently fails on general temper code, so going up to config.temper.md isn't useful yet, but this still
    // seems like the right policy, and you can use `-w .` or some such in a lower dir for now and establish that as
    // the work-root for that docgen tree.
    // TODO Make docgen more resilient and/or better able to understand what to work on.
    val effectiveOutDir = outputDirectory ?: run {
        // We can have multiple backends in the same output, so include them all in the path.
        // The order also affects the output, so retain that here.
        val backendsName = backends.joinToString("-") { it.uniqueId }
        workRoot.resolve(Path.of(TEMPER_OUT_NAME, "-docs", backendsName))
    }
    val executorService = makeExecutorServiceForBuild()
    try {
        val cancelGroup = BuildRunCancelGroup(executorService)
        val result = RResult.of {
            runDocGen(
                workRoot = workRoot,
                outputDirectory = effectiveOutDir,
                backends = backends,
                cancelGroup = cancelGroup,
            )
        }
        when (result) {
            is RSuccess -> cliConsole.info("Docs written to $effectiveOutDir")
            is RFailure -> cliConsole.error("Doc Generation failed", result.failure)
        }
        return result is RSuccess
    } finally {
        executorService.shutdown()
    }
}
