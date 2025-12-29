package lang.temper.cli.repl

import lang.temper.common.Trie
import lang.temper.common.buildUniqList
import lang.temper.common.unquotedTemperEscaper
import lang.temper.frontend.implicits.allImplicitlyImportedNames
import lang.temper.frontend.implicits.builtinEnvironment
import lang.temper.interp.EmptyEnvironment
import lang.temper.lexer.Genre
import lang.temper.lexer.TokenType
import lang.temper.log.logConfigurationsByName

internal class ReplCompletions(
    val repl: Repl,
) {
    private val completionTemplates = ReplCompletionTemplates(repl)

    fun suggest(completionContext: CompletionContext): List<String> {
        // Recognize patterns around REPL affordances
        run {
            val (templateCompletionContext, templateCompletionPrefix, prefixStripLen) =
                splitTemplateCompletionContextBeforeString(completionContext)
            var fromTemplate = completionTemplates.applyTemplates(templateCompletionContext)
            if (templateCompletionPrefix.isNotEmpty()) {
                fromTemplate = fromTemplate?.mapNotNull {
                    if (it.startsWith(templateCompletionPrefix)) {
                        it.substring(prefixStripLen)
                    } else {
                        null
                    }
                }
            }
            if (!fromTemplate.isNullOrEmpty()) {
                return@suggest fromTemplate
            }
        }

        val token = completionContext.tokens[completionContext.cursorTokenIndex]
        val tokenType = token.tokenType
        val prefix = token.tokenText.substring(0, completionContext.offsetIntoTokenText)
        return when {
            prefix.isEmpty() -> emptyList()
            tokenType == TokenType.QuotedString -> buildUniqList {
                val commandCount = repl.commandCount
                val loc = repl.lastLocReferenced
                    ?: if (commandCount != INITIAL_COMMAND_COUNT) {
                        ReplChunkIndex(commandCount - 1)
                    } else {
                        null
                    }
                if (loc != null) {
                    val lastLocReferencedAsStringContent = unquotedTemperEscaper.escape("$loc")
                    if (lastLocReferencedAsStringContent.startsWith(prefix)) {
                        add(lastLocReferencedAsStringContent)
                    }
                }
                logConfigurationNamesTrie.addCompletionsTo(prefix, this)
            }
            tokenType == TokenType.Comment -> emptyList()
            else -> buildUniqList {
                // First, add names from exports
                addAll(
                    repl.allExportedBaseNames
                        .mapNotNull { exportedBaseName ->
                            val nameText = exportedBaseName.nameText
                            if (nameText.startsWith(prefix)) {
                                nameText
                            } else {
                                null
                            }
                        }
                        // Suggest more recent names earlier
                        .asReversed(),
                )
                // Prefer more recent names
                // Second, add names that are ambiently available.
                ambientNames.value.addCompletionsTo(prefix, this)

                for (extraBinding in repl.extraBindings.keys) {
                    val builtinKey = extraBinding.builtinKey
                    if (builtinKey?.startsWith(prefix) == true) {
                        add(builtinKey)
                    }
                }
            }
        }
    }
}

internal val logConfigurationNamesTrie: Trie<String> = Trie(
    logConfigurationsByName.keys.mapNotNull {
        if (it != "*") { it to it } else { null }
    },
)

private fun Trie<String>.addCompletionsTo(prefix: String, suggestions: MutableSet<String>) {
    val t = this[prefix]
    if (t != null) {
        addCompletionTo(t, suggestions)
    }
}

private fun addCompletionTo(t: Trie<String>, suggestions: MutableSet<String>) {
    if (t.terminal) {
        val value = t.value
        if (value != null) {
            suggestions.add(unquotedTemperEscaper.escape(value))
        }
    }
    for (c in t.children) {
        addCompletionTo(c, suggestions)
    }
}

internal val ambientNames: Lazy<Trie<String>> = lazy {
    val names = buildSet {
        builtinEnvironment(EmptyEnvironment, Genre.Library)
            .locallyDeclared.mapNotNullTo(this) { it.builtinKey }
        allImplicitlyImportedNames.keys.mapTo(this) { name -> name.rawDiagnostic }
        // Make it easy to pass strings representing logger steps to the describe macro.
    }

    Trie(names.map { it to it })
}

/**
 * If the match is inside a string, then we need to back up the
 * completion context to before the string for the purpose of
 * applying [ReplCompletionTemplates] but then we need to use
 * that prefix we backed over to filter the results from the
 * template and also subtract it out.
 */
private fun splitTemplateCompletionContextBeforeString(
    completionContext: CompletionContext,
): SplitCompletionContext {
    val prefixRev = mutableListOf<String>()
    var prefixStripLen = 0
    var adjustedTokenIndex = completionContext.cursorTokenIndex
    while (adjustedTokenIndex >= 0) {
        val tok = completionContext.tokens[adjustedTokenIndex]
        val offset = if (adjustedTokenIndex == completionContext.cursorTokenIndex) {
            completionContext.offsetIntoTokenText
        } else {
            tok.tokenText.length
        }
        if (offset == 0) {
            adjustedTokenIndex -= 1
            continue
        }
        when (tok.tokenType) {
            TokenType.LeftDelimiter,
            TokenType.QuotedString,
            -> {
                val prefixChunk = tok.tokenText.substring(0, offset)
                prefixRev.add(prefixChunk)
                // We will need to strip off the quotation marks which are
                // not part of the JLine line parser "word" being completed
                // since JLine treats suggestions as replacements for the
                // current word but the way our custom JLine parser words
                // is by treating words as corresponding to non-synthetic
                // tokens, and the lexer treats a string literal's quotation
                // marks are separate left/right delimiter tokens.
                if (adjustedTokenIndex != completionContext.cursorTokenIndex) {
                    prefixStripLen += prefixChunk.length
                }
                adjustedTokenIndex -= 1
            }
            TokenType.Comment,
            TokenType.Number,
            TokenType.Punctuation,
            TokenType.RightDelimiter,
            TokenType.Space,
            TokenType.Word,
            TokenType.Error,
            -> break
        }
    }
    return if (prefixRev.isEmpty()) {
        SplitCompletionContext(completionContext)
    } else {
        SplitCompletionContext(
            completionContext = completionContext.copy(
                cursorTokenIndex = adjustedTokenIndex + 1,
                offsetIntoTokenText = 0,
            ),
            prefix = prefixRev.asReversed().joinToString(""),
            prefixStripLen = prefixStripLen,
        )
    }
}

private data class SplitCompletionContext(
    val completionContext: CompletionContext,
    val prefix: String = "",
    val prefixStripLen: Int = 0,
)
