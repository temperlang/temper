package lang.temper.tests

import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.Lexer
import lang.temper.lexer.MarkdownLanguageConfig
import lang.temper.lexer.TokenType
import lang.temper.lexer.sourceOffsetOf
import lang.temper.log.FilePath
import lang.temper.log.LogSink

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
 *     ```stdout
 *     Hello, World!
 *     ```
 */
abstract class MarkdownFileBasedFunctionalTestBase : FunctionalTestBase() {

    override val expectedOutput: String
        get() {
            // TODO: Maybe use a markdown parser
            val mainFile = this.mainFile
            require(
                mainFile.segments.last().fullName.endsWith(".md") && mainFile.isFile,
            ) {
                mainFile
            }

            val mainFileContent = temperFiles[mainFile]
            require(mainFileContent != null) { mainFile }

            val loc = mainFile
            val tokens = Lexer(loc, LogSink.devNull, mainFileContent, lang = MarkdownLanguageConfig())
            return toStringViaBuilder { out ->
                for (token in tokens) {
                    if (token.tokenType == TokenType.Comment) {
                        val tokenText = token.tokenText
                        if (tokens.sourceOffsetOf(token) == 0) {
                            appendStdoutFromCodeBlocks(loc, tokenText, out)
                        } else if (!tokenText.startsWith('/')) {
                            // Not an in-code comment like /* do not scan me */ or // do not scan me
                            // This could fail if both indentation-based and text starts with '/' by chance.
                            val skipLength = when (tokenText.startsWith(END_OF_TEMPER_CODE_BLOCK)) {
                                true -> tokenText.indexOfFirst { it != '`' } // Could be more than 3
                                false -> 0 // Indentation-based has no end to remove
                            }
                            if (skipLength >= 0) {
                                appendStdoutFromCodeBlocks(loc, tokenText.substring(skipLength), out)
                            }
                        }
                    }
                }
            }
        }
}

private fun appendStdoutFromCodeBlocks(
    loc: FilePath,
    textBetweenTemperCodeBlocks: String,
    out: StringBuilder,
) {
    val lines = textBetweenTemperCodeBlocks.split(crlfRegex)
    var inStdout = false
    for (line in lines) {
        val regex = if (inStdout) { stdoutCodeEndRegex } else { stdoutCodeStartRegex }
        if (regex.matches(line)) {
            inStdout = !inStdout
        } else if (inStdout) {
            out.append(line).append('\n')
        }
    }
    require(!inStdout) { "$loc: $textBetweenTemperCodeBlocks" }
}

private val crlfRegex = Regex("""\r?\n""")
private val stdoutCodeStartRegex = Regex("""^[ \t]*```log[ \t]*$""")
private val stdoutCodeEndRegex = Regex("""^[ \t]*```[ \t]*$""")
private const val END_OF_TEMPER_CODE_BLOCK = "```"
