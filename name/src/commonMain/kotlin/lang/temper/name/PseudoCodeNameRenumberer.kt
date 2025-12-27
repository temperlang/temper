package lang.temper.name

import lang.temper.common.C_HASH
import lang.temper.common.charCount
import lang.temper.common.decodeUtf16
import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureContextKey
import lang.temper.common.structure.StructureHint
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.IdParts
import lang.temper.lexer.LexicalDefinitions
import kotlin.math.min

typealias ProbableNameMatcher = (String) -> List<ProbableName>

private const val QUOTED_NAME_PREFIX = LexicalDefinitions.quotedNamePrefix

/**
 * A probable name matcher that matches Temper pseudocode name strings like
 * `sourceName__123` and `temporary#456`.
 */
val defaultProbableNameMatcher: ProbableNameMatcher = { string ->
    val probableNames = mutableListOf<ProbableName>()

    var i = 0
    val n = string.length
    while (i < n) {
        val cp = decodeUtf16(string, i)
        if (cp in IdParts.Start) {
            // Find a run of maximal run of name characters
            val nameStart = i
            var nameEnd = i + charCount(cp)
            while (nameEnd < n) {
                val oldNameEnd = nameEnd
                val cpNext = decodeUtf16(string, nameEnd)
                val afterCpNext = nameEnd + charCount(cpNext)
                if (cpNext in IdParts.Continue) {
                    nameEnd = afterCpNext
                } else if ((cpNext in IdParts.Medial || cpNext == C_HASH) && afterCpNext < n) {
                    // A medial has to be followed by a continuing char, and we fake '#' as a
                    // medial since that's what's used in PseudoCode for Temporaries.
                    val possibleContinue = decodeUtf16(string, afterCpNext)
                    if (possibleContinue in IdParts.Continue) {
                        nameEnd = afterCpNext + charCount(possibleContinue)
                    }
                }
                if (nameEnd == oldNameEnd) {
                    break
                }
            }

            val isQuotedNamePrefix = nameEnd - nameStart == QUOTED_NAME_PREFIX.length &&
                QUOTED_NAME_PREFIX.regionMatches(0, string, nameStart, QUOTED_NAME_PREFIX.length)

            var nAfterDistinguisher = 0
            if (isQuotedNamePrefix && nameEnd < n && string[nameEnd] == '`') {
                nameEnd += 1
                while (nameEnd < n) {
                    val c = string[nameEnd]
                    nameEnd += 1
                    when (c) {
                        '`' -> {
                            nAfterDistinguisher = 1
                            break
                        }
                        '\\' -> nameEnd = min(n, nameEnd + 1)
                    }
                }
            }

            // If it ends with ("__" digits) or ("#" digits) then it's eligible for renumbering.
            val distinguisherEnd = nameEnd - nAfterDistinguisher
            val beforeDigits = startOfDigitsBefore(string, distinguisherEnd)
            val range = nameStart until nameEnd
            if (beforeDigits in range) { // We have digits at the end
                val prefix = string.substring(nameStart, beforeDigits)
                if (prefix.endsWith("__") || prefix.endsWith("#")) {
                    probableNames.add(
                        ProbableName(
                            range = range,
                            distinguisherRange = beforeDigits until distinguisherEnd,
                        ),
                    )
                }
            }

            i = nameEnd
        } else {
            i += charCount(cp)
        }
    }

    probableNames.toList()
}

/**
 * In log outputs and test goldens, it's often super convenient to renumber temporaries and
 * resolved source names.
 *
 * We shouldn't have to rewrite unit tests every time we tweak a compiler pass so that it generates
 * more or fewer temporaries or generates them in a different order.
 * Similarly, we shouldn't have to tweak backend tests.
 *
 * This provides utilities that find things that look like Temper temporary names and source names
 * in strings and applies a consistent alpha renumbering.
 *
 * ## CAVEAT
 * It finds all maximal substrings that look like temporary names or source names.
 * Even if those substrings appear inside comment or string tokens.
 */
object PseudoCodeNameRenumberer {
    /**
     * Renumbers names in strings.  This is compatible with `assertStructured`.
     */
    fun newStructurePostProcessor(
        probableNameMatcher: ProbableNameMatcher = defaultProbableNameMatcher,
    ): (Structured) -> Structured {
        val renumberer = Renumberer(probableNameMatcher)
        return { structured ->
            ApplyRenumberingStructured(structured, renumberer)
        }
    }

    fun newStringRenumberer(
        probableNameMatcher: ProbableNameMatcher = defaultProbableNameMatcher,
    ): (String) -> String {
        val renumberer = Renumberer(probableNameMatcher)
        return { s -> renumberer.rewriteProbableNames(s) }
    }
}

private class Renumberer(
    val probableNameMatcher: ProbableNameMatcher,
) {
    /**
     * When we find a probable name, we rewrite it using the corresponding value if there
     * is one in this map.
     */
    private val probableNameToRewrite = mutableMapOf<String, String>()

    /**
     * When we find a probable name like `t#6` and we have no rewrite we bump the count for `t#`
     * to come up with a rewrite.
     */
    private val prefixToCounter = mutableMapOf<Pair<String, String>, Int>()

    fun rewriteProbableName(probableName: String, varies: IntRange): String =
        probableNameToRewrite.getOrPut(probableName) {
            val prefix = probableName.substring(0, varies.first)
            val suffix = probableName.substring(varies.last + 1)
            val key = prefix to suffix
            val count = prefixToCounter[key] ?: 0
            val bumpedCount = count + 1
            check(bumpedCount > count) // Underflow is bad
            prefixToCounter[key] = bumpedCount
            "$prefix$count$suffix"
        }

    fun rewriteProbableNames(string: String): String {
        val probableNames = probableNameMatcher(string)
        if (probableNames.isEmpty()) { return string }
        val probableNamesSorted = probableNames.sortedBy { it.range.first }
        return toStringViaBuilder { out ->
            var emittedUpTo = 0
            probableNamesSorted.forEach { probableName ->
                val before = probableName.range.first
                check(emittedUpTo <= probableName.range.first)
                out.append(string, emittedUpTo, before)
                val varies = probableName.distinguisherRange
                out.append(
                    rewriteProbableName(
                        string.substring(probableName.range),
                        (varies.first - before)..(varies.last - before),
                    ),
                )
                emittedUpTo = probableName.range.last + 1
            }
            out.append(string, emittedUpTo, string.length)
        }
    }
}

private class ApplyRenumberingStructured(
    val structured: Structured,
    val renumberer: Renumberer,
) : Structured {
    override fun destructure(structureSink: StructureSink) {
        structured.destructure(wrapStructureSink(structureSink))
    }

    private fun wrapStructureSink(structureSink: StructureSink) =
        RenumberingStructureSink(structureSink)

    private inner class RenumberingStructureSink(val sink: StructureSink) : StructureSink {
        override fun obj(emitProperties: PropertySink.() -> Unit) {
            sink.obj {
                RenumberingPropertySink(this).emitProperties()
            }
        }

        override fun arr(emitElements: StructureSink.() -> Unit) {
            sink.arr {
                RenumberingStructureSink(this).emitElements()
            }
        }

        override fun value(s: String) = sink.value(renumberer.rewriteProbableNames(s))

        override fun value(n: Int) = sink.value(n)

        override fun value(n: Long) = sink.value(n)

        override fun value(n: Double) = sink.value(n)

        override fun value(b: Boolean) = sink.value(b)

        override fun nil() = sink.nil()

        override fun <T : Any> context(key: StructureContextKey<T>): T? = sink.context(key)
    }

    private inner class RenumberingPropertySink(val sink: PropertySink) : PropertySink {
        override fun key(
            key: String,
            hints: Set<StructureHint>,
            emitValue: StructureSink.() -> Unit,
        ) {
            val adjustedKey = renumberer.rewriteProbableNames(key)
            // We only renumber values, not keys
            sink.key(adjustedKey, hints) {
                RenumberingStructureSink(this).emitValue()
            }
        }

        override fun <T : Any> context(key: StructureContextKey<T>): T? = sink.context(key)
    }
}

/**
 * The start index of a run of ASCII digits.
 *  the start is .
 *
 * @param endIndex exclusive
 * @return In "foo123" returns the index where the substring "123" starts.
 */
private fun startOfDigitsBefore(s: String, endIndex: Int = s.length): Int {
    var startIndex = endIndex
    while (startIndex != 0) {
        val c = s[startIndex - 1]
        if (c !in '0'..'9') { break }
        startIndex -= 1
    }
    return startIndex
}

data class ProbableName(
    /** The range of the name in the input text. */
    val range: IntRange,
    /**
     * The range of the auto-assigned sub-string that distinguishes the name.
     * So for a name like `foo__123` which was derived from a parsed name `foo`, the `123` is in
     * the auto-assigned part, which is eligible to be renumbered.
     */
    val distinguisherRange: IntRange,
)
