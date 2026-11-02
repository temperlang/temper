package lang.temper.regex

import lang.temper.common.IntRangeSet
import lang.temper.common.codePointString
import lang.temper.common.minus
import kotlin.random.Random

fun Random.nextMatch(regex: RegexNode): String {
    val context = MatchGenContext(random = this)
    context.regex(regex)
    if (context.needsWordBoundary && !context.lastWasWord()) {
        error("""No word boundary after: ${context.builder}""")
    }
    return context.builder.toString()
}

private class MatchGenContext(
    val random: Random,
    val builder: StringBuilder = StringBuilder(),
    var capturesUncertain: Boolean = false,
    var ended: Boolean = false,
    var needsWordBoundary: Boolean = false,

    /**
     * Not using the captures right because they aren't reliable, but I don't feel like deleting them yet.
     * Might do some comparison later with actual capture calculation to see what the differences are.
     */
    val captures: MutableList<Pair<String, IntRange>> = mutableListOf(),
)

private fun MatchGenContext.regex(regex: RegexNode) {
    // TODO(tjp, regex): Some kind of retry logic? Locally or for whole thing? Word boundary in repeat matters, e.g.???
    when (regex) {
        is Capture -> capture(regex)
        is CodePoints -> append(regex.value)
        is CodeRange -> codeSet(CodeSet(listOf(regex), negated = false)) // Reuse codeSet to simplify restrictions.
        is CodeSet -> codeSet(regex)
        is Or -> this.regex(random.nextItem(regex.items)) // TODO(tjp, tooling): Choose carefully if `ended`?
        is Repeat -> repeat(regex)
        is Seq -> regex.items.forEach { this.regex(it) }
        Begin -> check(builder.isEmpty()) { "Begin requested after: $builder" }
        Digit -> codeSet(asciiDigitCodeSet)
        Dot -> codeSet(dotCodeSet)
        End -> ended = true
        GraphemeCluster -> codeSet(asciiWordCodeSet) // TODO(tjp, regex): Something more interesting.
        Space -> codeSet(spaceCodeSet)
        Word -> codeSet(asciiWordCodeSet)
        WordBoundary -> needsWordBoundary = true
    }
}

private fun MatchGenContext.append(chars: CharSequence) {
    check(!ended) { """Already ended after "$builder" before: $chars""" }
    if (needsWordBoundary && chars.isNotEmpty()) {
        // For now, all words are ascii, so we can likely ignore surrogates in this check for now as non-word.
        // Also, before and after string count as non-word.
        if (lastWasWord() == chars.first().code in asciiWordIntRangeSet) {
            error("""No word boundary between "$builder" and: $chars""")
        }
    }
    // TODO Check!
    builder.append(chars)
    needsWordBoundary = false
}

private fun MatchGenContext.capture(capture: Capture) {
    val begin = builder.length
    regex(capture.item)
    captures.add(capture.name to (begin until builder.length))
}

private fun MatchGenContext.codeSet(codeSet: CodeSet) {
    // TODO(tjp, regex): Cache the ranges in the context?
    val ranges = codeRangeSet(codeSet)
    val adjusted = when (needsWordBoundary) {
        true -> ranges - when (builder.last().code in asciiWordIntRangeSet) {
            true -> asciiWordIntRangeSet
            false -> asciiWordNegatedIntRangeSet
        }
        false -> ranges
    }
    var result: Int
    do {
        result = random.nextInt(adjusted)
    } while (result in surrogateRange)
    append(result.codePointString())
}

private fun MatchGenContext.lastWasWord() = builder.isNotEmpty() && builder.last().code in asciiWordIntRangeSet

private fun MatchGenContext.repeat(repeat: Repeat) {
    // Exit early if we're done and have the option.
    ended && repeat.min == 0 && return
    // Otherwise, repeat even for reluctant, since it would have been able to match that.
    // TODO(tjp, regex): Validity of capture groups depends on what follows and if reluctant or greedy.
    // TODO(tjp, regex): Uncaptured repeats before a capture group can also invalidate captures.
    // TODO(tjp, regex): Just process when finished to determine captures???
    capturesUncertain = true
    val count = when (val max = repeat.max) {
        null -> repeat.min + random.nextSizeBig()
        else -> random.nextInt(repeat.min, max + 1)
    }
    repeat(count) { regex(repeat.item) }
}

/**
 * Throws if the set is empty.
 */
internal fun Random.nextInt(ranges: IntRangeSet): Int {
    // If we keep the cumulative sum array instead of just the final sum, we could binary search the final value, but
    // that's not worth the effort here.
    val total = ranges.sumOf { it.last - it.first + 1 }
    val index = nextInt(total)
    var sum = 0
    var result = -1
    ranges.find { range ->
        val nextSum = sum + range.last - range.first + 1
        val done = index < nextSum
        if (done) {
            result = index - sum + range.first
        }
        sum = nextSum
        done
    }
    return result
}
