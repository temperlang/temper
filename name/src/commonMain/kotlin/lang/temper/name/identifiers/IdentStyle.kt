package lang.temper.name.identifiers

import lang.temper.common.charCategory

/**
 * Identifier styles include the information necessary to parse identifiers into runs of [Segment]s and
 * format them as strings. Conversion between styles thus treats one as a parsing style and the other as a formatting
 * style. There are no guarantees that `join(S, split(S, t)) == text`, or that `t != u -> split(S, t) != split(S, u)`.
 *
 * Parsing is done in a single step, through a [WordBreaker], and recombining requires the [WordMapper] and
 * [WordDelimiter].
 */
enum class IdentStyle(
    /** The breaker parses an identifier into segments. */
    private val breaker: WordBreaker,
    /** The mapper normalizes segments by some rule. */
    private val mapper: WordMapper,
    /** The delimiter inserts delimiters between ambiguous segments. */
    private val delimiter: WordDelimiter,
) {
    /**
     * The Temper standard camel case expects parts of an identifier to be differentiated by a change in case,
     * e.g. `camelCase` but also allows `_` delimiters. This will also inject delimiters after digits for
     * readability.
     *
     * In general, abbreviations _should_ not be upper-cased, but generally _can_ be uppercase. The Temper standard
     * prefers camel case, but tolerates using underscores.
     *
     * Some separators recognized in input:
     *
     * ```
     *                                      abbrev can be followed by word
     *                                               ↓
     * foo_bar_qux   fooBarQux   foo123  fooHTML_HTTPQux
     *    ↑   ↑         ↑  ↑        ↑           ↑
     *  underscores   case change  digits  abbrevs can be delimited
     * ```
     *
     * Typical output:
     *
     * ```
     *         normalized abbrev
     *              ↓
     * fooBar123_quxHtml
     *   ↑      ↑
     *  case   uses underscores after runs of digits
     *  change
     * ```
     */
    Camel(::breakChimeric, ::mapCamel, ::delimitChimeric),

    /**
     * See [Camel]; this is identical in parsing, but formatting will always uppercase the first letter of a segment.
     *
     * Typical output:
     *
     * ```
     * initial cap  normalize abbrevs
     * ↓            ↓
     * FooBar123_QuxHtml
     *   ↑      ↑
     *  case   uses underscores after runs of digits
     *  change
     * ```
     */
    Pascal(::breakChimeric, ::mapPascal, ::delimitChimeric),

    /**
     * This version of [Camel] is strict in two regards:
     *
     * 1. on parsing, it treats underscores as unexpected (but tolerated) tokens
     * 2. on formatting, it will never output underscores to delimit indistinct tokens
     *
     * ```
     * given ["foo", "bar", "123", "777", "qux"]:
     *
     * fooBar123777qux
     *       ↑  ↑  ↑
     *      no underscores before, after, or between runs of digits
     * ```
     */
    StrictCamel(::breakOnCase, ::mapCamel, ::delimitNull),

    /**
     * Same as [StrictCamel] except, when formatting, the first letter of a run of letters is upper-cased.
     *
     * ```
     * given ["foo", "bar", "123", "777", "qux"]:
     *
     * FooBar123777Qux
     *       ↑  ↑  ↑
     *      no underscores before, after, or between runs of digits
     * ```
     */
    StrictPascal(::breakOnCase, ::mapPascal, ::delimitNull),

    /**
     * Parses and formats delimited identifiers.
     *
     * ```
     *  between normalize
     *  words    abbrevs
     *    ↓      ↓
     * foo_bar_html5_123_stuff
     *            ↑ ↑
     *          between but
     *        not before digits
     * ```
     */
    Snake(::breakOnDelimiter, ::mapLower, ::delimitUnderscore),

    /**
     * Parses and formats delimited identifiers, using dashes.
     *
     * ```
     *  between normalize
     *  words    abbrevs
     *    ↓      ↓
     * foo-bar-html5-123-stuff
     *            ↑ ↑
     *          between but
     *        not before digits
     * ```
     */
    Dash(::breakOnDelimiter, ::mapLower, ::delimitDash),

    /**
     * This parses exactly like [Snake], except the formatting is uppercase to match the C convention for constants.
     *
     * ```
     *  between normalize
     *  words    abbrevs
     *    ↓      ↓
     * FOO_BAR_HTML5_123_STUFF
     *            ↑ ↑
     *          between but
     *        not before digits
     * ```
     */
    LoudSnake(::breakOnDelimiter, ::mapUpper, ::delimitUnderscore),

    /**
     * This parses exactly like [Dash], except the formatting is all uppercase.
     *
     * ```
     *  between normalize
     *  words    abbrevs
     *    ↓      ↓
     * FOO-BAR-HTML5-123-STUFF
     *            ↑ ↑
     *          between but
     *        not before digits
     * ```
     */
    LoudDash(::breakOnDelimiter, ::mapUpper, ::delimitDash),

    /**
     * Not really an identifier style but processes strings with spaces. Doesn't change capitalization
     */
    Human(::breakOnSpace, ::mapIdentity, ::delimitSpace),
    ;

    fun convertTo(result: IdentStyle, text: String): String =
        convert(text, this.breaker, result.mapper, result.delimiter)
    internal fun split(text: String): List<Segment> =
        segments(text, this.breaker)
    internal fun join(segments: Iterable<Segment>): String =
        joining(segments, this.mapper, this.delimiter)
}

internal typealias WordBreaker = (String, WordConsumer) -> Unit
internal typealias WordConsumer = (Tok, CharSequence) -> Unit
internal typealias WordMapper = (Tok, Tok, CharSequence) -> CharSequence
internal typealias WordDelimiter = (Tok, Tok) -> CharSequence

/**
 * A [Tok], String pair; useful in diagnostics to capture a stage of identifier conversion.
 */
internal data class Segment(
    val tok: Tok,
    val wrd: String,
) {
    override fun toString(): String = "${tok.short}|$wrd"
}

/**
 * Broader categories that are useful for comparing `[Tok]` values. For example, two runs of different categories may
 * not need a delimiter.
 */
internal enum class Category {
    /** Any kind of letter, caseless or cameral. */
    Letters,

    /** Exactly the same as [Tok.Digit]. */
    Digits,

    /** Not expected within an identifier, or a delimiter that should be removed during parsing. */
    Others,

    /** A category for [Tok.Nil]. */
    NilCat,
}

/**
 * This class is _very_ specific to the implementation and should be expected to change as .
 * [Int.getToken] for the precise definition of these.
 */
internal enum class Tok(val cat: Category) {
    Lower(Category.Letters),
    Upper(Category.Letters),

    /** neither lower nor upper, e.g. caseless languages */
    Caseless(Category.Letters),
    Digit(Category.Digits),

    /** Specific delimiters recognized by this library */
    Delimiter(Category.Others),

    /** characters not expected within an identifier, but tolerated */
    Other(Category.Others),

    /** Not generated by parsing, this can be used within functions to mark e.g. beginning or end. */
    Nil(Category.NilCat),
    ;

    /** Short name for display. */
    val short: String = this.name.substring(0, 2)
    val isLetters: Boolean
        get() = this.cat == Category.Letters

    val isCasedLetters: Boolean
        get() = this == Lower || this == Upper

    fun seg(str: String): Segment = Segment(this, str)
}

/** Generic dot for breaking up identifiers in caseless languages. */
private const val MIDDLE_DOT = 0xb7

/** Dot common in Chinese identifiers */
private const val HYPHENATION_POINT = 0x2027

/** Dot common in Japanese identifiers */
private const val KATAKANA_MIDDLE_DOT = 0x30fb

/** Dot common in Japanese identifiers */
private const val HALFWIDTH_KATAKANA_MIDDLE_DOT = 0xff65

/** Dot common in Korean identifiers; technically an obsolete letter */
private const val HANGUL_LETTER_ARAEA = 0x318d

/** Characterize the kind of token a codepoint should be part of. */
internal fun Int.getToken(): Tok = when (this) {
    '_'.code, '-'.code, MIDDLE_DOT,
    HYPHENATION_POINT, HANGUL_LETTER_ARAEA,
    KATAKANA_MIDDLE_DOT, HALFWIDTH_KATAKANA_MIDDLE_DOT,
    ->
        Tok.Delimiter
    else -> when (this.charCategory) {
        CharCategory.LOWERCASE_LETTER -> Tok.Lower
        CharCategory.MODIFIER_LETTER -> Tok.Lower
        CharCategory.UPPERCASE_LETTER -> Tok.Upper
        CharCategory.TITLECASE_LETTER -> Tok.Upper
        CharCategory.OTHER_LETTER -> Tok.Caseless
        CharCategory.DECIMAL_DIGIT_NUMBER -> Tok.Digit
        else -> Tok.Other
    }
}
