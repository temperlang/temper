package lang.temper.common

import kotlin.math.max

/**
 * A function from un-encoded strings to encoded strings.
 */
interface Escaper {
    fun escape(cs: CharSequence): String =
        toStringViaBuilder(capacity = cs.length + EXTRA_CAPACITY) {
            escapeTo(cs, it)
        }

    fun escapeTo(cs: CharSequence, out: StringBuilder)

    /** A quoting character */
    val quote: Char?

    /**
     * A copy of this escaper with the given extra escapes.
     * @param [quote] if supplied, the copy will use this as its quote character.
     */
    fun withExtraEscapes(
        extras: Map<Char, Escape>,
        quote: Char? = this.quote,
    ): Escaper

    /** A copy of the escaper but using the given quote character. */
    fun withQuote(quote: Char?): Escaper
}

/**
 * Base class for an [Escaper] from un-encoded strings to encoded strings.
 * This processes the string one code-unit at a time, left to right.
 */
abstract class AbstractEscaper<STATE>(
    override val quote: Char?,
    protected val asciiEscapes: Array<Escape>,
) : Escaper {
    /** Called to create a processing state for a string. */
    abstract fun createState(): STATE

    /** Called after the character content but before any close quote. */
    open fun finish(state: STATE, out: StringBuilder) {}

    abstract override fun withExtraEscapes(
        extras: Map<Char, Escape>,
        quote: Char?,
    ): Escaper

    override fun withQuote(quote: Char?) = withExtraEscapes(emptyMap(), quote)

    open fun emitChunk(cs: CharSequence, start: Int, end: Int, state: STATE, out: StringBuilder) {
        out.append(cs, start, end)
    }

    open fun applyEscape(
        codePoint: Int,
        escape: Escape,
        pos: Int,
        state: STATE,
        out: StringBuilder,
    ) {
        escape.escapeTo(codePoint, out)
    }

    abstract fun nonAsciiEscape(codePoint: Int): Escape
}

abstract class Utf16Escaper<STATE>(
    quote: Char?,
    asciiEscapes: Array<Escape>,
) : AbstractEscaper<STATE>(
    quote = quote,
    asciiEscapes = asciiEscapes,
) {
    override fun escapeTo(cs: CharSequence, out: StringBuilder) {
        if (quote != null) {
            out.append(quote)
        }

        val state = createState()

        var i = 0
        val n = cs.length
        var processed = 0
        while (i < n) {
            val before = i
            val codePoint = cs[i].code
            i += 1

            val escape = asciiEscapes.getOrNull(codePoint) ?: nonAsciiEscape(codePoint)
            if (escape !is IdentityEscape) {
                emitChunk(cs, processed, before, state, out)
                processed = i
                applyEscape(codePoint, escape, before, state, out)
            }
        }
        emitChunk(cs, processed, n, state, out)
        finish(state, out)

        if (quote != null) {
            out.append(quote)
        }
    }
}

internal abstract class Utf32Escaper<STATE>(
    quote: Char?,
    asciiEscapes: Array<Escape>,
) : AbstractEscaper<STATE>(
    quote = quote,
    asciiEscapes = asciiEscapes,
) {
    override fun escapeTo(cs: CharSequence, out: StringBuilder) {
        if (quote != null) {
            out.append(quote)
        }

        val state = createState()

        var i = 0
        val n = cs.length
        var processed = 0
        while (i < n) {
            val before = i
            val codePoint = decodeUtf16(cs, i)
            i += charCount(codePoint)
            val escape = asciiEscapes.getOrNull(codePoint) ?: nonAsciiEscape(codePoint)
            if (escape !is IdentityEscape) {
                emitChunk(cs, processed, before, state, out)
                processed = i
                applyEscape(codePoint, escape, before, state, out)
            }
        }
        emitChunk(cs, processed, n, state, out)

        finish(state, out)

        if (quote != null) {
            out.append(quote)
        }
    }
}

/**
 * Escapes code-units to the given output buffer.
 */
interface Escape {
    fun escapeTo(codePoint: Int, out: StringBuilder)
}

/**
 * An escaper that emits the given code-units unchanged.
 */
object IdentityEscape : Escape {
    override fun escapeTo(codePoint: Int, out: StringBuilder) {
        out.appendCodePoint(codePoint)
    }

    override fun toString(): String = "IdentityEscape"
}

/**
 * A fixed replacement with [replacement].
 */
data class FixedEscape(val replacement: String) : Escape {
    override fun escapeTo(codePoint: Int, out: StringBuilder) {
        out.append(replacement)
    }
}

/**
 * A hex-based escape.
 */
class HexEscape(
    /** Like `\u`, appended before hex digits. */
    val prefix: String,
    /** Minimum number of hex digits to emit. */
    val minDigits: Int,
    /** Maximum number of hex digits to emit. */
    val maxDigits: Int,
    /** Appended after the hex digits. */
    val suffix: String,
    /** True to use lower-case hex digits: `a` through `f`. */
    private val useLowerCaseDigits: Boolean = true,
) : Escape {
    override fun escapeTo(codePoint: Int, out: StringBuilder) {
        out.append(prefix)
        val digitCount = run {
            var i = codePoint
            var nDigits = 0
            while (i != 0) {
                i = i ushr BITS_PER_HEX_DIGIT
                nDigits += 1
            }
            nDigits
        }
        check(digitCount <= maxDigits)

        val digits = if (useLowerCaseDigits) {
            LOWER_CASE_HEX_DIGITS
        } else {
            UPPER_CASE_HEX_DIGITS
        }
        repeat(max(1, minDigits) - digitCount) {
            out.append(digits[0])
        }
        for (digitIndex in (digitCount - 1) downTo 0) {
            val bits = (codePoint ushr (BITS_PER_HEX_DIGIT * digitIndex)) and HEX_DIGIT_MASK
            out.append(digits[bits])
        }
        out.append(suffix)
    }
}

@Suppress("SpellCheckingInspection")
private const val LOWER_CASE_HEX_DIGITS = "0123456789abcdef"

@Suppress("SpellCheckingInspection")
private const val UPPER_CASE_HEX_DIGITS = "0123456789ABCDEF"

private const val EXTRA_CAPACITY = 16

private const val HEX_DIGIT_MASK = 0b1111

internal fun mergeExtraEscapes(
    asciiEscapes: Array<Escape>,
    extras: Map<Char, Escape>,
): Array<Escape> {
    if (extras.isEmpty()) { return asciiEscapes }
    val maxEscape = extras.keys.maxOf { it.code }
    check(maxEscape <= MAX_ASCII) { "maxEscape=$maxEscape" }
    return (0..max(maxEscape, asciiEscapes.lastIndex)).map { i ->
        extras[i.toChar()] ?: asciiEscapes.getOrNull(i) ?: IdentityEscape
    }.toTypedArray()
}
