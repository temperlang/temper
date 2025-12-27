package lang.temper.lexer

import lang.temper.common.Trie

val bracketPartners = mapOf(
    "(" to ")",
    "{" to "}",
    "[" to "]",
    "<" to ">",
    "</" to ">",
    "\\(" to ")",
    "\\{" to "}",
    "\${" to "}",
    "\\u{" to "}",
    "{:" to ":}",
)

val openBrackets = bracketPartners.map { it.key }.toSet()

val closeBrackets = bracketPartners.map { it.value }.toSet()

/**
 * If a close bracket might be an infix operator that could nest here, we
 * only treat as a closer if it closes at the top level.
 * Otherwise, in
 *
 *     Class<T where (T > U)>
 *
 * the nested greater-than would skip over the parentheses.
 *
 * For non-ambiguous closers, we look up the stack to find something to close, so
 *
 *     {
 *       f(123;  // Missing parenthesis
 *     } // Closes the operator stack element corresponding to `{` even though `(` is on top of it.
 *
 */
fun isAmbiguousCloser(tokenText: String) = ambiguousCloserTrie.longestPrefix(tokenText) != null
val ambiguousCloserTrie = Trie(closeBrackets.map { it to true })
