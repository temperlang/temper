package lang.temper.regex

import lang.temper.common.C_AMP
import lang.temper.common.C_BS
import lang.temper.common.C_CARET
import lang.temper.common.C_CR
import lang.temper.common.C_DASH
import lang.temper.common.C_DOL
import lang.temper.common.C_DOT
import lang.temper.common.C_LEFT_CURLY
import lang.temper.common.C_LEFT_ROUND
import lang.temper.common.C_LEFT_SQUARE
import lang.temper.common.C_LF
import lang.temper.common.C_PIPE
import lang.temper.common.C_PLUS
import lang.temper.common.C_QUEST
import lang.temper.common.C_RIGHT_CURLY
import lang.temper.common.C_RIGHT_ROUND
import lang.temper.common.C_RIGHT_SQUARE
import lang.temper.common.C_SLASH
import lang.temper.common.C_STAR
import lang.temper.common.C_TAB
import lang.temper.common.C_TILDE
import lang.temper.common.HEX_RADIX
import lang.temper.common.IntRangeSet
import lang.temper.common.MIN_SUPPLEMENTAL_CP
import lang.temper.common.codePointString
import lang.temper.common.decodeUtf16Iter
import lang.temper.common.toHexPadded
import lang.temper.common.toStringViaBuilder

// Public

fun RegexNode.formatToString(
    formatter: RegexFormatter = TemperRegexFormatter,
) = toStringViaBuilder { format(formatter = formatter, out = it) }

fun RegexNode.format(
    out: Appendable,
    formatter: RegexFormatter = TemperRegexFormatter,
) = PatternFormatContext(formatter = formatter, out = out).format(this)

interface RegexFormatter {
    fun adjusted(codeRange: CodeRange): RegexNode = codeRange

    fun adjusted(codeSet: CodeSet): RegexNode = codeSet

    val digit get() = "\\d"

    val dot get() = "."

    fun escapeUnicode(out: Appendable, code: Int)

    fun formatCaptureName(out: Appendable, name: String) {
        out.append("?<")
        out.append(name)
        out.append(">")
    }

    /** Return true to indicate it's been handled or false if not. */
    fun formatCode(out: Appendable, code: Int, insideCodeSet: Boolean): Boolean = false

    val space get() = "\\s"

    val word get() = "\\w"

    val wordBoundary get() = "\\b"
}

object TemperRegexFormatter : RegexFormatter {
    override fun escapeUnicode(out: Appendable, code: Int) {
        // Use the same unicode escapes as temper strings.
        out.append("\\u{")
        out.append(code.toString(HEX_RADIX).uppercase())
        out.append("}")
    }
}

object DotnetRegexFormatter : RegexFormatter {
    override fun adjusted(codeRange: CodeRange): RegexNode {
        val dashed = codeRange.pushDashToBegin()
        return when (codeRange.max < MIN_SUPPLEMENTAL_CP) {
            true -> dashed
            // Expand into multiple surrogate pair ranges as needed.
            false -> intRangeSetToUtf16CodeSet(dashed.codeRangeSet())
        }
    }

    override fun adjusted(codeSet: CodeSet) = codeSet.pushDashToBegin().utf16CodeSetEquivalent()

    // These wrapped brackets work only outside code sets.
    override val digit get() = "[0-9]"

    // Surrogate pairs are separate 16-bit units in dotnet.
    override val dot get() = """(?:.|$SURROGATE_PAIR)"""

    override fun escapeUnicode(out: Appendable, code: Int) {
        when {
            code <= UINT8_MAX -> out.append("\\x").append(code.toHexPadded(HEX8_SIZE).uppercase())
            code <= UINT16_MAX -> out.append("\\u").append(code.toHexPadded(HEX16_SIZE).uppercase())
            else -> {
                val string = code.codePointString()
                escapeUnicode(out, string[0].code)
                escapeUnicode(out, string[1].code)
            }
        }
    }

    override val space get() = """[ \f\n\r\t\v]"""

    override val word get() = "[A-Za-z0-9_]"

    override val wordBoundary get() = """(?:(?<!$word)(?=$word)|(?<=$word)(?!$word))"""
}

object JsRegexFormatter : RegexFormatter {
    override fun adjusted(codeSet: CodeSet) =
        when (codeSet.negated && codeSet.items.all { (it.max ?: 0) < MIN_SUPPLEMENTAL_CP }) {
            true -> {
                // Include supplemental code point in the set to trigger JS supplemental handling.
                // This creates /(?:a|[^a...])/ from /[^...]/, where `a` is in the supplemental space.
                // Another alternative is to create a positive set as inverse of the one given, but that seems more
                // likely to grow (needing more ranges) and harder to immediately compare with the original.
                val bonus = minSupplementalCodePoint
                // Prepend the new one so we see it immediately in the `all` loop above on the next pass.
                Or(listOf(bonus, codeSet.copy(items = listOf(bonus) + codeSet.items)))
            }
            false -> codeSet
        }

    override fun escapeUnicode(out: Appendable, code: Int) {
        out.append("\\u{")
        out.append(code.toString(HEX_RADIX).uppercase())
        out.append("}")
    }
}

object KotlinRegexFormatter : RegexFormatter {
    override fun escapeUnicode(out: Appendable, code: Int) {
        out.append("\\x{")
        out.append(code.toString(HEX_RADIX).uppercase())
        out.append("}")
    }

    /**
     * Unfortunately, Kotlin (or Java) doesn't define `\b` in terms of `\w`
     * like others do. See RegexMatchTest for examples.
     */
    override val wordBoundary get() = """(?:(?<!\w)(?=\w)|(?<=\w)(?!\w))"""
}

object PythonRegexFormatter : RegexFormatter {
    override fun escapeUnicode(out: Appendable, code: Int) {
        // We could just put everything on `\U`, but it's not hard to use condensed forms a little.
        when {
            code <= UINT8_MAX -> out.append("\\x").append(code.toHexPadded(HEX8_SIZE).uppercase())
            code <= UINT16_MAX -> out.append("\\u").append(code.toHexPadded(HEX16_SIZE).uppercase())
            else -> out.append("\\U").append(code.toHexPadded(HEX32_SIZE).uppercase())
        }
    }

    override fun formatCaptureName(out: Appendable, name: String) {
        out.append("?P<")
        out.append(name)
        out.append(">")
    }

    override fun formatCode(out: Appendable, code: Int, insideCodeSet: Boolean): Boolean = when (code) {
        C_AMP,
        C_TILDE,
        -> {
            // Python wants these escaped for potential future support of Unicode regex enhanced set operations.
            if (insideCodeSet) {
                out.append('\\')
            }
            out.append(code.toChar())
            true
        }
        else -> false
    }
}

// Private

data class PatternFormatContext(
    val formatter: RegexFormatter,
    val out: Appendable,
)

fun PatternFormatContext.format(regex: RegexNode) {
    when (regex) {
        is Capture -> return format(regex)
        is CodePoints -> return format(regex)
        is CodeRange -> return format(regex)
        is CodeSet -> return format(regex)
        is Or -> return format(regex)
        is Repeat -> return format(regex)
        is Seq -> return format(regex)
        Begin -> "^"
        Digit -> formatter.digit
        Dot -> formatter.dot
        End -> "$"
        GraphemeCluster -> "\\X"
        Space -> formatter.space
        Word -> formatter.word
        WordBoundary -> formatter.wordBoundary
    }.let { out.append(it) }
}

private fun PatternFormatContext.format(capture: Capture) {
    out.append("(")
    formatter.formatCaptureName(out, capture.name)
    format(capture.item)
    out.append(")")
}

private fun PatternFormatContext.format(codePoints: CodePoints, insideCodeSet: Boolean = false) {
    decodeUtf16Iter(codePoints.value).forEach { formatCode(it, insideCodeSet = insideCodeSet) }
}

/**
 * @param code the code point to write out
 * @param insideCodeSet whether the code point is syntactically inside a code set, which needs different escaping
 */
private fun PatternFormatContext.formatCode(code: Int, insideCodeSet: Boolean = false) {
    formatter.formatCode(out, code, insideCodeSet) && return
    when (code) {
        C_BS,
        C_CARET,
        C_DOL,
        C_DOT,
        C_LEFT_CURLY,
        C_LEFT_ROUND,
        C_LEFT_SQUARE,
        C_RIGHT_CURLY,
        C_RIGHT_ROUND,
        C_RIGHT_SQUARE,
        C_PIPE,
        C_PLUS,
        C_QUEST,
        C_SLASH,
        C_STAR,
        -> {
            out.append('\\')
            out.append(code.toChar())
        }
        C_DASH -> {
            if (insideCodeSet) {
                out.append('\\')
            }
            out.append(code.toChar())
        }
        C_LF -> out.append("\\n")
        C_CR -> out.append("\\r")
        C_TAB -> out.append("\\t")
        else -> when {
            // We can generate independent surrogate range characters for 16-bit char platforms like dotnet, so it's
            // possible to get surrogate values here.
            // TODO(tjp, regex): Consider our Int.charCategory to choose which should be escaped? What speed cost?
            code in lowControlRange || code in highControlRange || code in surrogateRange || code == UINT16_MAX -> {
                formatter.escapeUnicode(out, code)
            }
            code >= MIN_SUPPLEMENTAL_CP -> out.append(code.codePointString())
            else -> out.append(code.toChar())
        }
    }
}

private fun PatternFormatContext.format(codeRange: CodeRange) {
    when (val adjusted = formatter.adjusted(codeRange)) {
        is CodeRange -> {
            // Separate from a code set, needs its own square brackets.
            out.append('[')
            formatUnwrapped(codeRange)
            out.append(']')
        }
        else -> format(adjusted)
    }
}

private fun PatternFormatContext.formatUnwrapped(codeRange: CodeRange) {
    formatCode(codeRange.min, insideCodeSet = true)
    out.append('-')
    formatCode(codeRange.max, insideCodeSet = true)
}

private fun PatternFormatContext.format(codeSet: CodeSet) {
    when (val adjusted = formatter.adjusted(codeSet)) {
        is CodeSet -> {
            out.append('[')
            if (adjusted.negated) {
                out.append('^')
            }
            adjusted.items.forEach { formatCodeSetItem(it) }
            out.append(']')
        }
        else -> format(adjusted)
    }
}

private fun PatternFormatContext.formatCodeSetItem(codePart: CodePart) {
    when (codePart) {
        is CodePoints -> format(codePart, insideCodeSet = true)
        is CodeRange -> formatUnwrapped(codePart)
        is SpecialSet -> format(codePart)
    }
}

private fun PatternFormatContext.format(or: Or) {
    wrapUncaptured {
        if (or.items.isNotEmpty()) {
            format(or.items.first())
            or.items.slice(1 until or.items.size).forEach { pattern ->
                out.append('|')
                format(pattern)
            }
        }
    }
}

private fun PatternFormatContext.format(repeat: Repeat) {
    wrapUncaptured {
        format(repeat.item)
    }
    val min = repeat.min
    val max = repeat.max
    when {
        min == 0 && max == 1 -> out.append('?')
        min == 0 && max == null -> out.append('*')
        min == 1 && max == null -> out.append('+')
        else -> {
            out.append('{')
            out.append(min.toString())
            if (min != max) {
                out.append(',')
                if (max != null) {
                    out.append(max.toString())
                }
            }
            out.append('}')
        }
    }
    if (repeat.reluctant) {
        out.append('?')
    }
}

private fun PatternFormatContext.format(seq: Seq) {
    seq.items.forEach { format(it) }
}

private inline fun PatternFormatContext.wrapUncaptured(wrap: Boolean = true, format: () -> Unit) {
    if (wrap) {
        out.append("(?:")
    }
    format()
    if (wrap) {
        out.append(")")
    }
}

/** Might be easier to just to condense to [IntRangeSet] before this conversion. */
private fun intRangeSetToUtf16CodeSet(intRangeSet: IntRangeSet): RegexNode {
    val (smalls, bigs) = intRangeSet.partition { it.last < MIN_SUPPLEMENTAL_CP }
    val (smallIndies, smallRanges) = smalls.partition { it.last - it.first == 0 }
    val smallCodePoints = when (smallIndies.isNotEmpty()) {
        true -> listOf(CodePoints(smallIndies.map { it.first.toChar() }.toCharArray().concatToString()))
        else -> listOf()
    }
    val smallCodeRanges = smallRanges.map { CodeRange(it.first, it.last) }
    val smallCodeSet = when (smallCodePoints.isNotEmpty() || smallCodeRanges.isNotEmpty()) {
        true -> listOf(CodeSet(smallCodePoints + smallCodeRanges, negated = false))
        else -> listOf()
    }
    val (bigIndies, bigRanges) = bigs.partition { it.last - it.first == 0 }
    val bigCodePoints = bigIndies.map { CodePoints(it.first.codePointString()) }
    val bigCodeRanges = bigRanges.map { intRangeToPatterns(it) }.flatten()
    return Or(smallCodeSet + bigCodePoints + bigCodeRanges)
}

private fun intRangeToPatterns(intRange: IntRange): List<RegexNode> {
    val result = mutableListOf<RegexNode>()
    // First split out just the supplemental range, which will be all pairs.
    val bigRange = when (intRange.first < MIN_SUPPLEMENTAL_CP) {
        true -> {
            // Keep the basic range.
            result.add(CodeRange(intRange.first, MIN_SUPPLEMENTAL_CP - 1))
            MIN_SUPPLEMENTAL_CP..intRange.last
        }
        else -> intRange
    }
    when (bigRange == supplementalIntRange) {
        // Here and lower, we grab more surrogate code units than needed, just to simplify things.
        true -> result.add(surrogateCodeRangePair)
        else -> {
            val min = bigRange.first.codePointString()
            val max = bigRange.last.codePointString()
            when (min[0] == max[0]) {
                // Only 1 range needed for a single lead surrogate.
                true -> result.add(Seq(listOf(CodePoints(min.substring(0, 1)), CodeRange(min[1].code, max[1].code))))
                else -> {
                    // 2 or 3 ranges needed for more than one lead.
                    result.add(Seq(listOf(CodePoints(min.substring(0, 1)), CodeRange(min[1].code, UINT16_MAX))))
                    if (max[0].code - min[0].code > 1) {
                        // Gather up all the middle space into a sequence of 2 ranges.
                        result.add(Seq(listOf(CodeRange(min[0].code + 1, max[0].code - 1), CodeRange(0, UINT16_MAX))))
                    }
                    result.add(Seq(listOf(CodePoints(max.substring(0, 1)), CodeRange(0, max[1].code))))
                }
            }
        }
    }
    return result
}

private fun CodeRange.pushDashToBegin(): IntRangeSetConvertible {
    return if (min == C_DASH || max == C_DASH) {
        CodeSet(listOf(this), false).pushDashToBegin()
    } else {
        this
    }
}

/** Mention dash/hyphen only at the start of the set for backends that don't escape it well internally. */
private fun CodeSet.pushDashToBegin(): CodeSet {
    val hasDash = items.any { part ->
        when (part) {
            is CodePoints -> '-' in part.value
            is CodeRange -> part.min == C_DASH || part.max == C_DASH
            else -> false
        }
    }
    hasDash || return this
    // At this point, we have a dash, so clean it up.
    val parts = buildList {
        // Start with dash.
        add(CodePoints("-"))
        // Then exclude any other specific mention of it.
        for (part in items) {
            when (part) {
                is CodePoints -> {
                    val filtered = part.value.filter { it != '-' }
                    if (filtered.isNotEmpty()) {
                        add(CodePoints(filtered))
                    }
                }
                is CodeRange -> {
                    val min = part.min + (part.min == C_DASH).toInt()
                    val max = part.max - (part.max == C_DASH).toInt()
                    if (min <= max) {
                        add(CodeRange(min, max))
                    }
                }
                else -> add(part)
            }
        }
    }
    return copy(items = parts)
}

private fun CodeSet.utf16CodeSetEquivalent(): RegexNode {
    // Extract these despite potential added cost (vs `&&`) because the kotlin formatter is weak.
    val allSmall = items.all { (it.max ?: 0) < MIN_SUPPLEMENTAL_CP }
    // If special, we might do best just to coalesce, since dotnet needs lots of manual specials for ascii.
    // Also don't have to worry about nested brackets.
    val noneSpecial = items.none { it is SpecialSet }
    return when (!negated && allSmall && noneSpecial) {
        true -> this
        // A negated set technically could exclude the whole supplemental range, but don't worry it.
        else -> intRangeSetToUtf16CodeSet(codeRangeSet(this))
    }
}

private fun Boolean.toInt() = if (this) {
    1
} else {
    0
}

const val C_CONTROL8_MIN = 0x7F
const val C_CONTROL8_MAX = 0x9F
val lowControlRange = 0 until ' '.code
val highControlRange = C_CONTROL8_MIN..C_CONTROL8_MAX

const val C_SURROGATE_MIN = 0xD800
const val C_SURROGATE_MAX = 0xDFFF
const val SURROGATE_PAIR = """[\uD800-\uDBFF][\uDC00-\uDFFF]"""
val surrogateRange = C_SURROGATE_MIN..C_SURROGATE_MAX
val surrogateCodeRange = CodeRange(C_SURROGATE_MIN, C_SURROGATE_MAX)

// Broader than needed but simpler.
val surrogateCodeRangePair = Seq(listOf(surrogateCodeRange, surrogateCodeRange))

const val UINT8_MAX = 0xFF
const val UINT16_MAX = 0xFFFF
const val MAX_SUPPLEMENTAL_CP = 0x10FFFF

val minSupplementalCodePoint = CodePoints(MIN_SUPPLEMENTAL_CP.codePointString())
val supplementalIntRange = MIN_SUPPLEMENTAL_CP..MAX_SUPPLEMENTAL_CP

const val HEX8_SIZE = 2
const val HEX16_SIZE = 4
const val HEX32_SIZE = 8
