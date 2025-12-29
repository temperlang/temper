package lang.temper.log

import lang.temper.common.C_BS
import lang.temper.common.C_CARET
import lang.temper.common.C_DASH
import lang.temper.common.C_LEFT_SQUARE
import lang.temper.common.C_MAX_CODEPOINT
import lang.temper.common.C_RIGHT_SQUARE
import lang.temper.common.C_SLASH
import lang.temper.common.IntRangeSet
import lang.temper.common.charCount
import lang.temper.common.decodeUtf16
import lang.temper.common.encodeUtf16
import lang.temper.common.toStringViaBuilder

/**
 * Allows filtering the content of a single directory.
 */
interface DirectoryContentFilter {
    fun allows(fileName: FilePathSegment): Boolean

    companion object {
        /**
         * A filter that uses glob syntax.
         *
         * - `*` matches any number of characters
         * - `?` matches any one character
         * - `[123]` matches any one character listed therein
         * - `[a-c]` matches any one character in that range
         * - `[!123]` matches any one character except those listed therein
         * - `[!a-c]` matches any one character not in that range
         * - `{123,[a-c]}` matches anything that matches either of those globs.
         *
         * Because of that:
         *
         * - `[*]` matches the character `*`.
         */
        fun fromGlob(glob: String) = GlobDirectoryContentFilter(glob)

        val all = fromGlob("*")
    }
}

/** Convenience base class for potentially customized behavior. */
abstract class AbstractGlobDirectoryContentFilter(
    glob: String,
) : DirectoryContentFilter {
    private val globPattern: Regex
    init {
        val (parsed, end) = parseGlobPattern(glob)
        require(end == glob.length) { "Unused characters at index $end in `$glob`" }
        this.globPattern = Regex(
            toStringViaBuilder { sb ->
                sb.append('^')
                parsed.appendRegexTextTo(sb)
                sb.append('$')
            },
        )
    }

    override fun allows(fileName: FilePathSegment): Boolean =
        globPattern.matches(fileName.fullName)
}

/** Glob filter with equality defined by the glob string. */
data class GlobDirectoryContentFilter(
    val glob: String,
) : AbstractGlobDirectoryContentFilter(glob)

private interface Glob { fun appendRegexTextTo(sb: StringBuilder) }
private data class GlobAtom(val regexStr: String) : Glob {
    override fun appendRegexTextTo(sb: StringBuilder) {
        sb.append(regexStr)
    }
}
private data class GlobCat(val elements: List<Glob>) : Glob {
    override fun appendRegexTextTo(sb: StringBuilder) {
        elements.forEach { it.appendRegexTextTo(sb) }
    }
}
private data class GlobChars(
    val ranges: IntRangeSet,
) : Glob {
    override fun appendRegexTextTo(sb: StringBuilder) {
        val negated = ranges.min == 0 && ranges.max == C_MAX_CODEPOINT && ranges.count() > 1
        val rangesToEmit = if (negated) {
            IntRangeSet.difference(allChars, ranges)
        } else {
            ranges
        }
        sb.append('[')
        if (negated) {
            sb.append('^')
        }
        for (range in rangesToEmit) {
            val first = range.first()
            val last = range.last()
            appendCharInRange(first, sb)
            when (last - first) {
                0 -> continue
                1 -> {}
                2 -> sb.append('-')
            }
            appendCharInRange(last, sb)
        }
        sb.append(']')
    }

    private fun appendCharInRange(cp: Int, sb: StringBuilder) {
        when (cp) {
            C_DASH, C_LEFT_SQUARE, C_RIGHT_SQUARE, C_CARET, C_BS ->
                sb.append('\\')
            else -> {}
        }
        encodeUtf16(cp, sb)
    }

    companion object {
        val allChars = IntRangeSet.new(0..C_MAX_CODEPOINT)
    }
}

private data class OrGlob(
    val alternatives: List<Glob>,
) : Glob {
    override fun appendRegexTextTo(sb: StringBuilder) {
        if (alternatives.isEmpty()) {
            sb.append("(?!)")
            return
        }

        sb.append("(?:")
        alternatives.forEachIndexed { index, glob ->
            if (index != 0) { sb.append('|') }
            glob.appendRegexTextTo(sb)
        }
        sb.append(")")
    }
}

private fun parseGlobPattern(
    globStr: String,
    offset: Int = 0,
    inCommaGroup: Boolean = false,
): Pair<Glob, Int> {
    val limit = globStr.length

    val elements = mutableListOf<Glob>()
    var pos = offset
    charLoop@
    while (pos < limit) {
        when (globStr[pos]) {
            '?' -> {
                elements.add(GlobAtom("""[^/]"""))
                pos += 1
            }
            '*' -> {
                elements.add(GlobAtom("""[^/]*"""))
                pos += 1
            }
            '{' -> {
                pos += 1
                val alternatives = mutableListOf<Glob>()
                orLoop@
                while (true) {
                    val (alternative, posAfter) = parseGlobPattern(globStr, pos, inCommaGroup = true)
                    alternatives.add(alternative)
                    pos = posAfter
                    if (globStr.getOrNull(pos) == ',') {
                        pos += 1
                    } else {
                        break@orLoop
                    }
                }
                require(pos < limit && globStr[pos] == '}') {
                    "Expected '}' at index $pos in `$globStr`"
                }
                elements.add(OrGlob(alternatives.toList()))
                pos += 1
            }
            '}' -> break@charLoop
            ',' -> {
                if (inCommaGroup) { break@charLoop }
                elements.add(GlobAtom(""","""))
                pos += 1
            }
            '[' -> {
                pos += 1
                val (chars, posAfter) = parseGlobChars(globStr, pos)
                pos = posAfter
                require(globStr.getOrNull(pos) == ']') {
                    "Expected ']' at index $pos in `$globStr`"
                }
                elements.add(
                    GlobChars(
                        IntRangeSet.difference(
                            chars,
                            IntRangeSet.Companion.new(C_SLASH..C_SLASH),
                        ),
                    ),
                )
                pos += 1
            }
            else -> {
                val cp = decodeUtf16(globStr, pos)
                elements.add(GlobChars(IntRangeSet.new(cp..cp)))
                pos += charCount(cp)
            }
        }
    }
    val glob = when (elements.size) {
        1 -> elements[0]
        else -> GlobCat(elements.toList())
    }
    return glob to pos
}

private fun parseGlobChars(globStr: String, offset: Int): Pair<IntRangeSet, Int> {
    var pos = offset
    val limit = globStr.length

    var negated = false
    if (pos < limit && globStr[pos] == '!') {
        negated = true
        pos += 1
    }
    val ranges = mutableListOf<IntRange>()
    while (pos < limit) {
        if (globStr[pos] == ']') { break }
        val firstChar = decodeUtf16(globStr, pos)
        pos += charCount(firstChar)
        var lastChar = firstChar
        if (pos < limit && globStr[pos] == '-') {
            pos += 1
            require(pos != limit) { "Expected end of character range in `$globStr`" }
            lastChar = decodeUtf16(globStr, pos)
            pos += charCount(lastChar)
        }
        require(lastChar >= firstChar) {
            "Range ending at index $pos is out of order in `$globStr`"
        }
        ranges.add(firstChar..lastChar)
    }
    val rangeSet = IntRangeSet.unionRanges(ranges)
    return if (negated) {
        IntRangeSet.difference(GlobChars.allChars, rangeSet)
    } else {
        rangeSet
    } to pos
}
