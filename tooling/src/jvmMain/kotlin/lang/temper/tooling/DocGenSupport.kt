package lang.temper.tooling

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import lang.temper.common.console
import lang.temper.common.currents.CancelGroup
import lang.temper.compile.TopLevelConsoleLogSink
import lang.temper.docgen.prepDocGen
import lang.temper.fs.fromJavaPathThrowing
import lang.temper.name.BackendId
import java.nio.file.Files
import java.nio.file.Path

@DelicateCoroutinesApi
fun runDocGen(workRoot: Path, outputDirectory: Path, backends: List<BackendId>, cancelGroup: CancelGroup) {
    val handler = CoroutineExceptionHandler { _, cause ->
        console.error(cause.stackTraceToString())
    }
    val scope = CoroutineScope(Dispatchers.Default + handler)

    val logSink = TopLevelConsoleLogSink(scope, console)
    val docGen = prepDocGen(
        libraryRoot = fromJavaPathThrowing(workRoot),
        logSink = logSink,
        backends = backends,
        cancelGroup = cancelGroup,
    )

    Files.createDirectories(outputDirectory)

    docGen.processDocTree(workRoot, outputDirectory)
}
