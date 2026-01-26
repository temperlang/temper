package lang.temper.docbuild

import lang.temper.common.indexOfNext
import lang.temper.lexer.unpackQuotedString

internal object AnnotationExtractor {
    fun extractAnnotations(
        content: KotlinContent,
        onto: MutableSet<SnippetId>,
    ) {
        val tokens = content.tokens

        // Look for token sequences like:
        // "@" : Unknown
        // "HelpSnippet" : IDENTIFIER
        // "(" : LPAR
        // "\"" : OPEN_QUOTE
        // "brief help" : REGULAR_STRING_PART
        // "\"" : CLOSING_QUOTE
        // "," : Unknown
        // " " : WHITE_SPACE
        // "\"" : OPEN_QUOTE
        // "builtin/cat" : REGULAR_STRING_PART
        // "\"" : CLOSING_QUOTE
        // ")" : RPAR
        //
        // Or a call like the below:
        // "helpSnippet" : IDENTIFIER
        // "(" : LPAR
        // "it" : IDENTIFIER
        // "," : Unknown
        // " " : WHITE_SPACE
        // "\"" : OPEN_QUOTE
        // "brief help" : REGULAR_STRING_PART
        // "\"" : CLOSING_QUOTE
        // "," : Unknown
        // " " : WHITE_SPACE
        // "\"" : OPEN_QUOTE
        // "builtin/+" : REGULAR_STRING_PART
        // "\"" : CLOSING_QUOTE
        // ")" : RPAR

        //
        // We scan for HelpSnippet, look back for an `@` and then scan forward to find the parentheses.
        // The last quoted string is the snippet ID.
        var i = 0
        while (i in tokens.indices) {
            val helpSnippetIdentifierIndex = tokens.indexOfNext(i) {
                it.type == KotlinTokenType.IDENTIFIER &&
                    (it.text == HELP_SNIPPET_ANNOTATION_NAME || it.text == HELP_SNIPPET_FN_NAME)
            }
            if (helpSnippetIdentifierIndex < 0) {
                break
            }
            i = helpSnippetIdentifierIndex + 1
            if (
                tokens[helpSnippetIdentifierIndex].text == HELP_SNIPPET_ANNOTATION_NAME &&
                tokens.getOrNull(helpSnippetIdentifierIndex)?.text != "@"
            ) {
                continue
            }

            var sawOpen = false
            var parenDepth = 0
            var endOfParenBlock = i
            while ((!sawOpen || parenDepth > 0) && endOfParenBlock < tokens.size) {
                val t = tokens[endOfParenBlock]
                endOfParenBlock += 1
                when (t.type) {
                    KotlinTokenType.LPAR -> {
                        sawOpen = true
                        parenDepth += 1
                    }
                    KotlinTokenType.RPAR -> parenDepth -= 1
                    else -> {}
                }
            }
            val argumentTokens = tokens.subList(helpSnippetIdentifierIndex + 1, endOfParenBlock)
            val openQuote = argumentTokens.indexOfLast { it.type == KotlinTokenType.OPEN_QUOTE }
            if (openQuote >= 0) {
                val snippetIdStr = buildString {
                    for (t in argumentTokens.subList(openQuote, argumentTokens.size)) {
                        if (t.type == KotlinTokenType.REGULAR_STRING_PART) {
                            append(t.text) // TODO: Kotlin unescape?
                        }
                    }
                }
                if (snippetIdStr.isNotEmpty()) {
                    val unpacked = unpackQuotedString(snippetIdStr, skipDelimiter = false)
                    if (unpacked.isOk) {
                        onto.add(SnippetId(unpacked.decoded.toIdParts(), "md"))
                    }
                }
            }
        }
    }
}

/** Name for an annotation that applies to MacroValue objects */
private const val HELP_SNIPPET_ANNOTATION_NAME = "HelpSnippet"

/** Name for a function that applies to builtin function values that aren't standalone classes/objects. */
private const val HELP_SNIPPET_FN_NAME = "helpSnippet"
