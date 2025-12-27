package lang.temper.docbuild

import lang.temper.common.Console
import lang.temper.common.Log

internal typealias ProblemCount = Int

/**
 * Allows performing an operation while collecting errors and warnings
 * related to the operation, or the fidelity of the output.
 */
internal class ProblemTracker(
    val console: Console,
) {
    var problemCount: ProblemCount = 0

    fun error(message: String) {
        console.error(message)
        problemCount += 1
    }

    fun errorGroup(
        header: String,
        withConsole: (Console) -> Unit,
    ) {
        problemCount += 1
        console.group(header, level = Log.Error) {
            withConsole(console)
        }
    }
}
