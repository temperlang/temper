package lang.temper.builtin

import temper.regex_parser.RegexParserGlobal
import temper.std.regex.RegexNode
import temper.std.regex.Repeat

/**
 * Rewrites counted repetition, `{m}`, `{m,}` and `{m,n}`, out of [pattern] so that
 * temper-regex-parser 0.3.0 can parse the rest.
 *
 * That parser version treats `{` and `}` as ordinary characters, so `/[0-9]{3}/`
 * would otherwise mean a digit followed by the text `{3}`. Each repeated atom
 * and its quantifier are replaced with a slot reference, `(?$name)`, and [slots]
 * gets a [Repeat] for that name. The parser already resolves slots the same way
 * for interpolated strings.
 *
 * An unescaped `{` that does not start a well-formed quantifier is a
 * [RegexSyntaxProblem] rather than literal text, as is a quantifier with
 * nothing to repeat. Inside character sets, `{` stays literal.
 *
 * The scan follows the 0.3.0 parser's lexical rules: a backslash escapes exactly
 * one character, a set runs to the first unescaped `]` that is not the end of a
 * range, and a slot reference `(?$...)` runs to the next `)`.
 */
internal fun rewriteCountedRepetition(
    pattern: String,
    slots: MutableMap<String, RegexNode>,
): String = CountedRepetitionRewriter(pattern, slots).rewriteTop()

private class CountedRepetitionRewriter(
    val pattern: String,
    val slots: MutableMap<String, RegexNode>,
) {
    var pos = 0

    fun rewriteTop(): String {
        val out = StringBuilder()
        rewriteLevel(out)
        if (pos < pattern.length) {
            // Only an unbalanced close paren stops the top level early.
            // The parser would stop reading there and silently drop the rest.
            problem("unmatched `)` at offset $pos")
        }
        return "$out"
    }

    private fun peek(): Char? = if (pos < pattern.length) pattern[pos] else null

    /** Copies one code point, so that a supplementary character is one atom. */
    private fun copyCodePoint(out: StringBuilder) {
        val end = if (
            pattern[pos].isHighSurrogate() && pos + 1 < pattern.length && pattern[pos + 1].isLowSurrogate()
        ) {
            pos + 2
        } else {
            pos + 1
        }
        out.append(pattern, pos, end)
        pos = end
    }

    /** Reads alternatives up to, but not including, an unmatched `)` or the end. */
    private fun rewriteLevel(out: StringBuilder) {
        // Start in out of the most recent atom that a quantifier could apply to.
        var atomStart = -1
        while (true) {
            val c = peek() ?: return
            when (c) {
                ')' -> return
                '|' -> {
                    out.append(c)
                    pos += 1
                    atomStart = -1
                }
                '\\' -> {
                    atomStart = out.length
                    out.append(c)
                    pos += 1
                    if (pos < pattern.length) { copyCodePoint(out) }
                }
                '[' -> {
                    atomStart = out.length
                    copyCodeSet(out)
                }
                '(' -> {
                    atomStart = out.length
                    copyGroup(out)
                }
                '*', '+', '?' -> {
                    out.append(c)
                    pos += 1
                    atomStart = -1
                }
                '^', '$' -> {
                    // Begin and End are zero width. Counting them is meaningless.
                    out.append(c)
                    pos += 1
                    atomStart = -1
                }
                '{' -> {
                    replaceRepeated(out, atomStart)
                    atomStart = -1
                }
                else -> {
                    atomStart = out.length
                    copyCodePoint(out)
                }
            }
        }
    }

    private fun copyCodeSet(out: StringBuilder) {
        out.append('[')
        pos += 1
        if (peek() == '^') {
            out.append('^')
            pos += 1
        }
        fun copyUnit() {
            if (peek() == '\\') {
                out.append('\\')
                pos += 1
            }
            if (pos < pattern.length) { copyCodePoint(out) }
        }
        while (pos < pattern.length) {
            if (peek() == ']') {
                out.append(']')
                pos += 1
                return
            }
            copyUnit()
            if (peek() == '-') {
                out.append('-')
                pos += 1
                copyUnit()
            }
        }
        // Unclosed. The parser reports that.
    }

    private fun copyGroup(out: StringBuilder) {
        out.append('(')
        pos += 1
        if (peek() == '?') {
            out.append('?')
            pos += 1
            when (peek()) {
                '$' -> {
                    // Slot reference: the name runs to the close paren with no nesting.
                    while (pos < pattern.length) {
                        val c = pattern[pos]
                        out.append(c)
                        pos += 1
                        if (c == ')') { return }
                    }
                    return
                }
                ':', '=', '!', '<' -> {
                    // Non-capturing group, or a lookaround the parser rejects.
                    out.append(pattern[pos])
                    pos += 1
                }
                else -> {
                    // Named capture: the name runs to `=`.
                    while (pos < pattern.length) {
                        val c = pattern[pos]
                        out.append(c)
                        pos += 1
                        if (c == '=') { break }
                    }
                }
            }
        }
        rewriteLevel(out)
        if (peek() == ')') {
            out.append(')')
            pos += 1
        }
        // Otherwise unclosed. The parser reports that.
    }

    /** At a `{`, reads the quantifier and replaces the atom before it with a slot. */
    private fun replaceRepeated(out: StringBuilder, atomStart: Int) {
        val braceStart = pos
        val quantifier = readBraceQuantifier()
        if (quantifier == null) {
            val shown = pattern.substring(braceStart, minOf(pattern.length, braceStart + MAX_SHOWN))
            problem(
                "`{` at offset $braceStart starts `$shown`, which is not a count like" +
                    " {3}, {2,} or {2,5}; write `\\{` to match a literal brace",
            )
        }
        val (min, max) = quantifier
        val text = pattern.substring(braceStart, pos)
        if (atomStart < 0) {
            problem("$text at offset $braceStart has nothing to repeat")
        }
        if (max != null && min > max) {
            problem("$text at offset $braceStart has a minimum greater than its maximum")
        }
        val reluctant = peek() == '?'
        if (reluctant) { pos += 1 }
        when (val next = peek()) {
            '*', '+', '?', '{' -> problem("$text at offset $braceStart is followed by another quantifier, `$next`")
            else -> {}
        }
        val atomText = out.substring(atomStart)
        out.setLength(atomStart)
        val item = runCatching { RegexParserGlobal.parseWith(atomText, slots)!! }.getOrElse {
            problem("cannot parse `$atomText`, repeated by $text at offset $braceStart")
        }
        val slotName = "$SLOT_PREFIX${slots.size}"
        slots[slotName] = Repeat(item, min, max, reluctant)
        out.append("(?\$").append(slotName).append(')')
    }

    /**
     * Reads `{m}`, `{m,}` or `{m,n}` and returns (m, n), where n is null for no
     * maximum. Returns null and leaves [pos] alone if the text is not one of those.
     */
    private fun readBraceQuantifier(): Pair<Int, Int?>? {
        val match = braceQuantifier.find(pattern, pos)?.takeIf { it.range.first == pos }
            ?: return null
        val (minDigits, comma, maxDigits) = match.destructured
        val min = count(minDigits)
        val max = when {
            comma.isEmpty() -> min
            maxDigits.isEmpty() -> null
            else -> count(maxDigits)
        }
        pos = match.range.last + 1
        return min to max
    }

    private fun count(digits: String): Int =
        digits.toIntOrNull() ?: problem("count $digits at offset $pos is too large")

    private fun problem(message: String): Nothing = throw RegexSyntaxProblem(message)

    companion object {
        /** Distinct from `arg` slots that the macro makes for interpolations. */
        const val SLOT_PREFIX = "repeat"
        const val MAX_SHOWN = 8
        val braceQuantifier = Regex("""\{([0-9]+)(?:(,)([0-9]*))?\}""")
    }
}
