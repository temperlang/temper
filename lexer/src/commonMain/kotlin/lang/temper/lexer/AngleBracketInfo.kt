package lang.temper.lexer

/**
 * If we have a token like `>>=` the parser may split it into `>`, `>`, `=`
 * depending on how many `<` tokens are on the top of the open bracket stack.
 *
 * To handle wildcard types, we want users to be able to express `T<*>`.
 * Worst, case, we may see a token like `<*>>>=` as in
 *
 *     let x:T<U<V<*>>>= ...
 *
 * This looks at a token and figures out whether it potentially has an open angle bracket,
 * whether it has any potential closing brackets.
 *
 * For example:
 *
 * | Token text | Count of Openers | Count of Closers |
 * | ---------- | ---------------- | ---------------- |
 * | `<`        | 1                | 0                |
 * | `<=`       | 0                | 0                |
 * | `<<`       | 0                | 0                |
 * | `<*`       | 1                | 0                |
 * | `<*>`      | 1                | 1                |
 * | `<*>>`     | 1                | 2                |
 * | `*>`       | 0                | 1                |
 * | `>>=`      | 0                | 2                |
 * | `<*<`      | 0                | 0                |
 *
 * A token has a potential opener when it starts with a single `<` which
 * is immediately followed by an optional '*' then either
 * - end of token
 * - '>'
 *
 * A token has potential closers when it starts with one of ("<*", "<" or "") immediately
 * followed by a '>'.
 */
internal data class AngleBracketInfo(
    /** The count of opening angle brackets (`<`).  Zero or one. */
    val openerCount: Int,
    /**
     * The index of the first token closer or the length of the token text if there are no closers.
     * For example, 1 in a token with text `*>>>` since the `*` precedes the first closer,
     * and 2 in `<*>` since both `<` and `*` precede the first `>`.
     */
    val indexOfFirstCloser: Int,
    /**
     * The number of separable closers.
     * For example, 3 in a token with text `*>>>`.
     */
    val closerCount: Int,
) {
    companion object {
        val zeros = AngleBracketInfo(0, 0, 0)

        fun of(tokenText: String, type: TokenType): AngleBracketInfo {
            if (type != TokenType.Punctuation) {
                return zeros
            }

            var openerCount = 0
            if (tokenText.startsWith("<")) {
                openerCount = 1
            }

            val len = tokenText.length
            var indexOfFirstCloser = openerCount
            if (len == 2 && tokenText[indexOfFirstCloser] == '/') { // </
                indexOfFirstCloser += 1
            } else if (indexOfFirstCloser < len && tokenText[indexOfFirstCloser] == '*') {
                indexOfFirstCloser += 1
            }

            var indexOfLastCloser = indexOfFirstCloser
            while (indexOfLastCloser < len && tokenText[indexOfLastCloser] == '>') {
                indexOfLastCloser += 1
            }
            val closerCount = indexOfLastCloser - indexOfFirstCloser
            return if (
                (openerCount or closerCount) != 0 &&
                (closerCount != 0 || indexOfFirstCloser == len)
            ) {
                AngleBracketInfo(
                    openerCount = openerCount,
                    indexOfFirstCloser = indexOfFirstCloser,
                    closerCount = closerCount,
                )
            } else {
                zeros
            }
        }
    }
}
