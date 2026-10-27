package lang.temper.tests

import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.Lexer
import lang.temper.lexer.MarkdownLanguageConfig
import lang.temper.lexer.TokenType
import lang.temper.lexer.sourceOffsetOf
import lang.temper.log.FilePath
import lang.temper.log.LogSink
import lang.temper.name.BackendId

/**
 * A functional test that loads code from files and which also gets the desired text from
 * the `main.temper.md` file by looking for mark-down code blocks like
 *
 *     ```stdout
 *     Expected output here
 *     ```
 *
 * so a full file might look like
 *
 *         console.log("Hello, World!");
 *
 *     should print the following:
 *
 *     ```log
 *     Hello, World!
 *     ```
 *
 * A `log` block may name the runs it applies to, where expected output differs between them,
 * as in `` ```log interp no-connected ``. Names are backend IDs, like `py`, or `interp` for the
 * interpreter, or `no-connected` for runs where Temper bodies stand in for a backend's `@connected`
 * code. A `~` excludes a name, as in `` ```log ~interp ~no-connected ``. A block applies to a run
 * if any of its names without `~` names the run, or it has no such names, and none of its `~`
 * names name the run. So a block without names applies to every run.
 */
abstract class MarkdownFileBasedFunctionalTestBase : FunctionalTestBase() {

    override fun expectedOutput(backendId: BackendId, noConnected: Boolean): String {
        // TODO: Maybe use a markdown parser
        val mainFile = this.mainFile
        require(
            mainFile.segments.last().fullName.endsWith(".md") && mainFile.isFile,
        ) {
            mainFile
        }

        val mainFileContent = temperFiles[mainFile]
        require(mainFileContent != null) { mainFile }

        val runNames = buildSet {
            add(backendId.uniqueId)
            if (noConnected) {
                add(NO_CONNECTED_LOG_NAME)
            }
        }
        val loc = mainFile
        val tokens = Lexer(loc, LogSink.devNull, mainFileContent, lang = MarkdownLanguageConfig())
        return toStringViaBuilder { out ->
            for (token in tokens) {
                if (token.tokenType == TokenType.Comment) {
                    val tokenText = token.tokenText
                    if (tokens.sourceOffsetOf(token) == 0) {
                        appendStdoutFromCodeBlocks(loc, tokenText, runNames, out)
                    } else if (!tokenText.startsWith('/')) {
                        // Not an in-code comment like /* do not scan me */ or // do not scan me
                        // This could fail if both indentation-based and text starts with '/' by chance.
                        val skipLength = when (tokenText.startsWith(END_OF_TEMPER_CODE_BLOCK)) {
                            true -> tokenText.indexOfFirst { it != '`' } // Could be more than 3
                            false -> 0 // Indentation-based has no end to remove
                        }
                        if (skipLength >= 0) {
                            appendStdoutFromCodeBlocks(loc, tokenText.substring(skipLength), runNames, out)
                        }
                    }
                }
            }
        }
    }
}

/** The name in `log` blocks for runs where Temper bodies stand in for a backend's connected code. */
const val NO_CONNECTED_LOG_NAME = "no-connected"

private fun appendStdoutFromCodeBlocks(
    loc: FilePath,
    textBetweenTemperCodeBlocks: String,
    runNames: Set<String>,
    out: StringBuilder,
) {
    val lines = textBetweenTemperCodeBlocks.split(crlfRegex)
    var inStdout = false
    var applies = false
    for (line in lines) {
        if (inStdout) {
            if (stdoutCodeEndRegex.matches(line)) {
                inStdout = false
            } else if (applies) {
                out.append(line).append('\n')
            }
        } else {
            val match = stdoutCodeStartRegex.matchEntire(line) ?: continue
            inStdout = true
            applies = logBlockApplies(match.groupValues[1], runNames)
        }
    }
    require(!inStdout) { "$loc: $textBetweenTemperCodeBlocks" }
}

/** Whether a `log` block with the given [names] text after `log` applies to a run with [runNames]. */
private fun logBlockApplies(names: String, runNames: Set<String>): Boolean {
    val (excludes, includes) = names.split(spacesRegex).filter { it.isNotEmpty() }.partition { it.startsWith("~") }
    return (includes.isEmpty() || includes.any { it in runNames }) &&
        excludes.none { it.substring(1) in runNames }
}

private val crlfRegex = Regex("""\r?\n""")
private val spacesRegex = Regex("""[ \t]+""")
private val stdoutCodeStartRegex = Regex("""^[ \t]*```log((?:[ \t]+~?[A-Za-z0-9_-]+)*)[ \t]*$""")
private val stdoutCodeEndRegex = Regex("""^[ \t]*```[ \t]*$""")
private const val END_OF_TEMPER_CODE_BLOCK = "```"
