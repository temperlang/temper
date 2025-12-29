package lang.temper.cli.repl

import lang.temper.common.compatAddFirst
import lang.temper.common.temperEscaper
import lang.temper.lexer.Lexer
import lang.temper.lexer.TokenType
import lang.temper.log.LogSink
import lang.temper.log.UnknownCodeLocation
import lang.temper.supportedBackends.supportedBackends

/**
 * Stores token sequences like:
 *
 *     describe(_,
 *
 * and relates them to possible completions like, in this case, the set of
 * logging stages that can be configured.
 */
internal class ReplCompletionTemplates(repl: Repl) {
    private val templatesByTokenCount = mutableListOf<MutableList<Template>>()

    init {
        // The second argument to describe is a log entry
        defineTemplate("describe(_, ") {
            buildList { logConfigurationNamesTrie.toStringCollection(this) }
                .map { temperEscaper.escape(it) }
        }
        // The second argument to translate is a backend id
        defineTemplate("translate(_, ") {
            supportedBackends.sorted().map { temperEscaper.escape(it.uniqueId) }
        }
        defineTemplate("help(") {
            ReplHelpFn(repl).topicKeys.map { temperEscaper.escape(it) }
        }
    }

    fun applyTemplates(completionContext: CompletionContext): List<String>? {
        // Pull tokens off non-space/comment tokens and when we find one,
        // look at the list of templates with that length.
        val tokensBeforeCompletion = ArrayDeque<Pair<String, TokenType>>()
        var tokenIndexInContext = completionContext.cursorTokenIndex
        run {
            // Skip over any partial tokens containing the cursor; they can't
            // match corresponding tokens in the template
            val token = completionContext.tokens[tokenIndexInContext]
            if (completionContext.offsetIntoTokenText != token.tokenText.length) {
                tokenIndexInContext -= 1
            }
        }
        while (tokenIndexInContext >= 0) {
            val token = completionContext.tokens[tokenIndexInContext]
            tokenIndexInContext -= 1
            if (token.synthetic || token.tokenType.ignorable) { continue }

            tokensBeforeCompletion.compatAddFirst(token.tokenText to token.tokenType)
            val tokenCount = tokensBeforeCompletion.size
            if (tokenCount >= templatesByTokenCount.size) { break }
            val templates: List<Template> = templatesByTokenCount[tokenCount]
            for (template in templates) {
                val matches = (0 until tokenCount).all { i ->
                    val toMatch = template.tokens[i]
                    toMatch == null || toMatch == tokensBeforeCompletion[i]
                }
                if (matches) {
                    return template.suggestions.value
                }
            }
        }
        return null
    }

    private fun defineTemplate(
        /** Lexed to produce the token list */
        templateText: String,
        computeSuggestions: () -> List<String>,
    ) {
        val lexer = Lexer(UnknownCodeLocation, LogSink.devNull, templateText)
        val templateTokens = buildList {
            for (token in lexer) {
                if (token.synthetic || token.tokenType.ignorable) {
                    continue
                }
                val tokenText = token.tokenText
                if (tokenText == "_") { // shorthand for any token
                    add(null)
                } else {
                    add(token.tokenText to token.tokenType)
                }
            }
        }
        addTemplate(Template(templateTokens, lazy { computeSuggestions() }))
    }

    private fun addTemplate(template: Template) {
        val tokenCount = template.tokens.size
        while (templatesByTokenCount.size <= tokenCount) {
            templatesByTokenCount.add(mutableListOf())
        }
        templatesByTokenCount[tokenCount].add(template)
    }
}

private data class Template(
    /** `null` for placeholder tokens represented as `_` in the string forms above. */
    val tokens: List<Pair<String, TokenType>?>,
    val suggestions: Lazy<List<String>>,
)
