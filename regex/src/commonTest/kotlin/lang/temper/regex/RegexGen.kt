package lang.temper.regex

import lang.temper.common.C_CR
import lang.temper.common.C_LF
import lang.temper.common.C_SPACE
import lang.temper.common.C_TAB
import lang.temper.common.codePointString
import kotlin.math.exp
import kotlin.random.Random

fun Random.nextRegex(): RegexNode = Context(random = this).seq()

private fun Context.regex(): RegexNode {
    val begunContext = copy(begun = true)
    // Give extra weight to the most common things.
    // TODO(tjp, regex): Instead of this hacky sequence, implement multinomial sampling with weights for each thing.
    if (coin()) {
        return begunContext.codePoints()
    }
    let mainKinds@{
        if (coin()) {
            return when (item(MainKind.values())) {
                MainKind.Capture -> capture()
                MainKind.CodePoints -> begunContext.codePoints()
                MainKind.CodeSet -> begunContext.codeSet()
                MainKind.Or -> or()
                MainKind.Repeat -> begunContext.repeat()
                MainKind.Seq -> seq()
                MainKind.Other -> return@mainKinds // Still need the option to get others.
            }
        }
    }
    // Try everything else at equal weight to main kinds.
    return when (item(OtherKind.values())) {
        OtherKind.CodeRange -> begunContext.codeRange()
        OtherKind.Begin -> Begin // Might be bad form here, but also provided more naturally in seq.
        OtherKind.Digit -> Digit
        OtherKind.Dot -> Dot
        OtherKind.End -> End
        // OtherKind.GraphemeCluster -> GraphemeCluster // Not yet properly handled for match generation.
        OtherKind.GraphemeCluster -> regex() // Reject for now. TODO(tjp, regex): Support in match generation.
        OtherKind.Space -> Space
        OtherKind.Word -> Word
        OtherKind.WordBoundary -> WordBoundary
    }
}

private fun Context.capture(): Capture {
    captureCount[0] += 1
    return Capture("n${captureCount[0]}", regex())
}

private fun Context.codePart(): CodePart = when (item(CodePartKind.values())) {
    CodePartKind.CodePoints -> codePoints()
    CodePartKind.CodeRange -> codeRange()
    CodePartKind.Space -> Space
    CodePartKind.Word -> Word
}

private fun Context.codePoint(codeKind: CodeKind) = when (codeKind) {
    CodeKind.Any8 -> nextInt(0x100)
    CodeKind.Any16 -> nextInt(0xD800) // Are 0xE000 through 0xFEFF valid and left out??? But eh.
    CodeKind.Any32 -> nextInt(0x10000, 0x110000)
    CodeKind.Control -> nextInt(0x20) // excludes 0xFF but we get that elsewhere
    CodeKind.Digit -> nextInt(0x30, 0x40)
    CodeKind.HSpace -> item(arrayOf(C_SPACE, C_TAB))
    CodeKind.Punctuation -> item(punctuation)
    CodeKind.Letter -> item(letter)
    CodeKind.Upper8 -> nextInt(0x7F, 0x100)
    CodeKind.VSpace -> item(arrayOf(C_CR, C_LF))
}

private fun Context.codePoints(): CodePoints {
    val codeKind = item(CodeKind.values())
    val value = (0 until sizeBig()).joinToString("") { codePoint(codeKind).codePointString() }
    return CodePoints(value)
}

private fun Context.codeRange(): CodeRange {
    val codeKind = item(CodeKind.values())
    val a = codePoint(codeKind)
    val b = codePoint(codeKind)
    // Discontiguous sets can make weird things happen here, but eh.
    return CodeRange(minOf(a, b), maxOf(a, b))
}

private fun Context.codeSet() = CodeSet((0 until sizeBig()).map { codePart() }, negated = coin())

private fun Context.or() = Or((0 until size() + 1).map { regex() }) // At least 2 for `or`.

private fun Context.repeat(): Repeat {
    // Bias min smaller.
    val a = poisson(MEAN_MIN_REPEAT)
    // But have at least one of size 1+.
    val b = size()
    val max = when (coin()) {
        true -> null
        false -> maxOf(a, b)
    }
    // In the end, recalculate min and max.
    return Repeat(regex(), min = minOf(a, b), max = max, reluctant = coin())
}

private fun Context.seq(): Seq {
    val begunContext = copy(begun = true)
    val patterns = mutableListOf<RegexNode>()
    if (!begun && coin()) {
        patterns.add(Begin)
    }
    repeat(sizeBig()) {
        patterns.add(
            when {
                !begun && patterns.isEmpty() -> this
                else -> begunContext
            }.regex(),
        )
    }
    if (!begun && coin()) {
        patterns.add(End)
    }
    return Seq(patterns)
}

private fun Context.coin() = nextDouble() > 0.5

private fun <T> Context.item(items: Array<T>) = random.nextItem(items)

private fun <T> Context.item(items: List<T>) = random.nextItem(items)

private fun Context.nextDouble() = random.nextDouble()

private fun Context.nextInt(until: Int) = random.nextInt(until)

private fun Context.nextInt(from: Int, until: Int) = random.nextInt(from, until)

private fun Context.size() = maxOf(1, poisson(MEAN))

private fun Context.sizeBig() = random.nextSizeBig()

private fun Context.poisson(mean: Int) = random.nextPoisson(mean)

private data class Context(
    val random: Random,
    val begun: Boolean = false,
    val captureCount: MutableList<Int> = mutableListOf(0),
)

internal fun <T> Random.nextItem(items: Array<T>) = items[nextInt(items.size)]

internal fun <T> Random.nextItem(items: List<T>) = items[nextInt(items.size)]

// Could use uniform, but Poisson might be funner.
// Knuth at https://en.wikipedia.org/wiki/Poisson_distribution#Generating_Poisson-distributed_random_variables
internal fun Random.nextPoisson(mean: Int): Int {
    val threshold = exp(-mean.toDouble())
    var count = 0
    var prob = 1.0
    while (true) {
        prob *= nextDouble()
        if (prob < threshold) {
            break
        }
        count += 1
    }
    return count
}

internal fun Random.nextSizeBig() = maxOf(1, nextPoisson(MEAN_BIG))

private enum class CodeKind {
    Any8,
    Any16,
    Any32,
    Control,
    Digit,
    HSpace,
    Punctuation,
    Letter,
    Upper8,
    VSpace,
}

private enum class CodePartKind {
    // Structured
    CodePoints,
    CodeRange,

    // Simple
    Space,
    Word,
}

// JVM only: Pattern::class.sealedSubclasses
private enum class MainKind {
    Capture,
    CodePoints,
    CodeSet,
    Or,
    Other,
    Repeat,
    Seq,
}

private enum class OtherKind {
    Begin,
    CodeRange,
    Digit,
    Dot,
    End,
    GraphemeCluster,
    Space,
    Word,
    WordBoundary,
}

private val punctuationRanges = listOf('!'..'/', ':'..'@', '\\'..'`', '{'..'~')
private val punctuation = punctuationRanges.rangesToList()

private val letterRanges = listOf('A'..'Z', 'a'..'z')
private val letter = letterRanges.rangesToList()

private fun List<CharRange>.rangesToList() = map { it.toList() }.flatten().map { it.code }

/** Mean number of times we repeat something. */
private const val MEAN = 2
private const val MEAN_BIG = 3
private const val MEAN_MIN_REPEAT = 1
