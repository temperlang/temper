package lang.temper.docgen.transformations

import lang.temper.common.Log
import lang.temper.docgen.AddedContent
import lang.temper.docgen.Code
import lang.temper.docgen.CodeFragment
import lang.temper.docgen.Document
import lang.temper.docgen.SimpleCodeFragment
import lang.temper.docgen.anticorruption.Compiler
import lang.temper.docgen.anticorruption.UserMessageSink
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.name.LanguageLabel

class CodeTransformer(
    private val compiler: Compiler,
    private val userMessageSink: UserMessageSink,
    private val diagnosticSink: LogSink,
) : Transformer {
    override fun transform(document: Document<*, *, *>) {
        val matchedChunk = matchCurlies(document.codeFragments)
        if (matchedChunk.isNotEmpty()) {
            attemptCompile(matchedChunk, document)
        }
        // Attempt to compile the non-curlies parts alone
        document.codeFragments.forEach { attemptCompile(listOf(it), document) }
    }

    private fun <T : CodeFragment<*>> attemptCompile(
        matchedChunk: List<T>,
        document: Document<*, *, *>,
        allowRetry: Boolean = true,
    ) {
        val compiled = compiler.compile(matchedChunk)
        compiled.resultingCode.replacements.forEach {
            document.supplant(it.key, format(it.value))
        }
        // TODO use the envelope
        if (allowRetry && compiled.resultingCode.replacements.isEmpty()) {
            // The compiler couldn't compile this, filter out potentially non-temper blocks and try again
            attemptCompile(matchedChunk.filter { it.isTemperCode }, document, allowRetry = false)
        } else {
            compiled.errors.forEach { userMessageSink.message(it) }
            compiled.returnedFragments.forEach {
                if (it.isTemperCode) {
                    diagnosticSink.log(
                        level = Log.Error,
                        template = MessageTemplate.UnableToCompileFragmentTemperCode,
                        pos = it.position,
                        values = listOf(it.sourceText),
                    )
                } else {
                    diagnosticSink.log(
                        level = Log.Error,
                        template = MessageTemplate.UnableToCompileFragmentMaybeTemperCode,
                        pos = it.position,
                        values = listOf(it.sourceText),
                    )
                }
            }
        }
    }

    private fun format(input: Map<LanguageLabel, String>): List<AddedContent> = input.map { Code(it.value, it.key) }
}

/**
 * Returns a contiguous subset of [fragments] that have matched curly braces
 */
internal fun <T : SimpleCodeFragment> matchCurlies(fragments: List<T>): List<T> {
    var opens = 0
    var closes = 0
    var finished = false
    val potential = fragments
        .dropWhile { it.countOf('{') == 0 }
        .takeWhile {
            opens += it.countOf('{')
            closes += it.countOf('}')

            if (finished) {
                false
            } else if (opens != closes) {
                true
            } else if (opens > 0) {
                finished = true
                true
            } else {
                false
            }
        }

    return if (opens == closes) potential else emptyList()
}

internal fun SimpleCodeFragment.countOf(char: Char): Int {
    return this.sourceText.count { c -> c == char }
}
