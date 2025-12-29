package lang.temper.regex

import lang.temper.common.IntRangeSet
import lang.temper.common.decodeUtf16Iter

/**
 * This mirrors the data model in "regex.temper.md".
 * TODO Can we just use temper std in the future?
 */
sealed interface RegexNode

fun RegexNode.walk(action: (RegexNode) -> Unit) {
    action(this)
    when (val pattern = this) {
        is Capture -> pattern.item.walk(action)
        is CodeSet -> pattern.items.forEach { it.walk(action) }
        is Or -> pattern.items.forEach { it.walk(action) }
        is Repeat -> pattern.item.walk(action)
        is Seq -> pattern.items.forEach { it.walk(action) }
        is CodePart, is Special -> {}
    }
}

data class Capture(
    val name: String,
    val item: RegexNode,
) : RegexNode

sealed interface CodePart : RegexNode

val CodePart.max get() = when (this) {
    is CodePoints -> decodeUtf16Iter(value).maxOrNull()
    is CodeRange -> max
    is SpecialSet -> values.max
}

data class CodePoints(
    val value: String,
) : CodePart

sealed interface IntRangeSetConvertible : RegexNode

data class CodeRange(
    val min: Int,
    val max: Int,
) : CodePart, IntRangeSetConvertible

data class CodeSet(
    val items: List<CodePart>,
    val negated: Boolean,
) : RegexNode, IntRangeSetConvertible

data class Or(
    val items: List<RegexNode>,
) : RegexNode

data class Repeat(
    val item: RegexNode,
    val min: Int,
    val max: Int?,
    val reluctant: Boolean,
) : RegexNode

data class Seq(
    val items: List<RegexNode>,
) : RegexNode

// Specials that don't correspond to simple code sets.
sealed class Special : RegexNode {
    override fun toString() = this::class.simpleName ?: super.toString()
}

object Begin : Special()
object End : Special()
object GraphemeCluster : Special() // TODO(tjp, regex): Can we support grapheme cluster in core dialect?
object WordBoundary : Special()

/**
 * Dot is like a code set but not usable in CodeSet.
 * JS (MDN): "Matches any single character except line terminators: \n, \r, \u2028 or \u2029."
 * Python: "In the default mode, this matches any character except a newline."
 */
object Dot : Special()

/** SpecialSet is a tailored set of code points that's also usable in [CodeSet]. */
sealed class SpecialSet : CodePart, Special() {
    abstract val values: IntRangeSet
}

/** Expect that Indo-Arabic numerals are useful even in Unicode. */
object Digit : SpecialSet() {
    override val values get() = asciiDigitIntRangeSet
}

/**
 * Current code intends to match the Python ascii definition.
 * In JS and Java: [ \f\n\r\t\v\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]
 * In .NET: [ \f\n\r\t\v\u0085\p{Z}]
 * In Python (ascii mode): [ \t\n\r\f\v]
 * See also: https://en.wikipedia.org/wiki/Template:Whitespace_(Unicode)
 */
object Space : SpecialSet() {
    override val values get() = spaceIntRangeSet
}

/**
 * In JS and Java: [A-Za-z0-9_], for JS see also `\p{UnicodeProperty}`
 * In Python:
 * - Ascii: [a-zA-Z0-9_]
 * - Locale: "matches characters considered alphanumeric in the current locale and the underscore."
 * - Unicode: "Matches Unicode word characters; this includes most characters that can be part of a word in any
 *   language, as well as numbers and the underscore."
 * - regex: Supports `\p`, maybe compatible with JS. Doesn't support PyPy.
 * TODO(tjp, tooling): Compare whatever Python implements with our IdParts.Continue and Unicode property sets.
 */
object Word : SpecialSet() {
    // TODO(tjp, tooling): Some mode hinting for ascii vs unicode? Would need to be in SpecialSet.
    override val values get() = asciiWordIntRangeSet
}
