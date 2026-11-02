package lang.temper.regex

import lang.temper.common.C_MAX_CODEPOINT
import lang.temper.common.IntRangeSet
import lang.temper.common.decodeUtf16Iter
import lang.temper.common.minus

val universe = IntRangeSet.new(0..C_MAX_CODEPOINT)
// Removing surrogate chars and other invalide code points might be either cleaner or messier.
// val universe = IntRangeSet.new(0..C_MAX_CODEPOINT) - IntRangeSet.new(surrogateRange)

val asciiDigitCodeSet = CodeSet(
    negated = false,
    items = listOf(
        CodeRange('0'.code, '9'.code),
    ),
)

val asciiDigitIntRangeSet = codeRangeSet(asciiDigitCodeSet)

// TODO(tjp, regex): Use IdParts.Continue from temper lexer? How does this relate to Python vs JS definitions?
// TODO(tjp, regex): What can we do efficiently across backends?
val asciiWordCodeSet = CodeSet(
    negated = false,
    items = listOf(
        CodePoints("_"),
        CodeRange('0'.code, '9'.code),
        CodeRange('A'.code, 'Z'.code),
        CodeRange('a'.code, 'z'.code),
    ),
)

val asciiWordIntRangeSet = codeRangeSet(asciiWordCodeSet)

val asciiWordNegatedIntRangeSet = universe - asciiWordIntRangeSet

val dotCodeSet = CodeSet(
    negated = true,
    items = listOf(
        CodePoints("\n"),
    ),
)

val spaceCodeSet = CodeSet(
    negated = false,
    items = listOf(
        CodePoints(" \t\n\r\u000C\u000B"), // \f\v at the end there
    ),
)

val spaceIntRangeSet = codeRangeSet(spaceCodeSet)

fun codeRangeSet(codeRange: CodeRange) = IntRangeSet.new(codeRange.min..codeRange.max)

fun codeRangeSet(codeSet: CodeSet): IntRangeSet {
    // Get ranges and merge. Negate late.
    val initial = mutableListOf<IntRange>().also {
        addCodeSetToRanges(codeSet.copy(negated = false), it)
    } as List<IntRange>
    val merged = IntRangeSet.unionRanges(initial)
    // Because hopefully late negation should be more efficient.
    val ranges = when (codeSet.negated) {
        true -> universe - merged
        false -> merged
    }
    return ranges
}

fun IntRangeSetConvertible.codeRangeSet(): IntRangeSet = when (val pattern = this) {
    is CodeRange -> codeRangeSet(pattern)
    is CodeSet -> codeRangeSet(pattern)
}

private fun addCodeSetToRanges(codeSet: CodeSet, ranges: MutableList<IntRange>) {
    if (codeSet.negated) {
        // This should apply only for special sets such as \D, \S, or \W. TODO(tjp, regex): Precalculate negations?
        codeRangeSet(codeSet).forEach { ranges.add(it) }
        return
    }
    codeSet.items.forEach { part ->
        when (part) {
            is CodePoints -> decodeUtf16Iter(part.value).forEach { ranges.add(it..it) }
            is CodeRange -> { ranges.add(part.min..part.max) }
            Digit -> addCodeSetToRanges(asciiDigitCodeSet, ranges)
            Space -> addCodeSetToRanges(spaceCodeSet, ranges)
            Word -> addCodeSetToRanges(asciiWordCodeSet, ranges)
        }
    }
}
