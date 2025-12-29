package lang.temper.value

import lang.temper.common.BINARY_RADIX
import lang.temper.common.DECIMAL_RADIX
import lang.temper.common.HEX_RADIX
import lang.temper.common.OCTAL_RADIX
import lang.temper.common.ignore
import lang.temper.common.max
import lang.temper.lexer.TokenType
import lang.temper.lexer.unpackQuotedString
import lang.temper.name.Symbol
import lang.temper.name.decodeName

fun unpackValue(tokenText: String, tokenType: TokenType): Result {
    return when (tokenType) {
        /**
         * <!-- snippet: syntax/int/examples -->
         * # Int Syntax Examples
         * Integers can be runs of decimal digits.
         *
         * ```temper 123
         * 123
         * ```
         *
         * Zero is a valid number, but C-style octal literals (with zero padding) would be a source
         * of confusion.
         *
         * ```temper 0
         * 0
         * ```
         *
         * ```temper FAIL
         * let hundred = 100;
         * let ten     = 010; // <!-- No
         * let one     = 001; // <!-- Still no
         * ```
         *
         * You can't use commas in a number literal to make large numbers readable,
         * but you can use an underscore to separate digit groups.
         *
         * ```temper
         * [123,456,789] == [123 , 456 , 789] &&  // Commas separate elements
         * [123_456_789] == [123456789]
         * ```
         *
         * Exponential notation is fine for floating point values, but not for integers.
         *
         * ```temper
         * 1e2 == 100.0
         * ```
         *
         * And feel free to use a base like hexadecimal or binary when that fits what you're
         * modelling.
         *
         * ```temper
         * 0x10 == 16 && // Hex
         * 0b10 ==  2 && // Binary
         * 0o10 ==  8
         * ```
         *
         * <!-- snippet: syntax/float64/examples -->
         * # Float64 Syntax Examples
         *
         * A number with a decimal point is a Float64.
         *
         * ```temper 123.456
         * 123.456
         * ```
         *
         * You can make big numbers more readable by separating digits with underscore(`_`).
         *
         * ```temper
         * 123_456.789 == 123.456_789e3
         * ```
         *
         * A number with an exponent is a Float64 even if it does not have a decimal point.
         * The exponent follows letter 'e', either upper or lower-case.
         *
         * ```temper
         * 123e2 == 12_300.0 &&
         * 123e2 == 123E2
         * ```
         *
         * Exponents may have a sign.
         *
         * ```temper
         * 125e+2     == 12_500.0 &&
         * 1.25e+2    == 125.0 &&
         * 125e-2     == 1.25 &&
         * 125e-0_002 == 1.25  // Exponents are rarely big, but you may break them up.
         * ```
         *
         * An integer-like number with a decimal suffix is a Float64.
         *
         * ```temper
         * 1F64 == 1.0 &&
         * 1f64 == 1.0
         * ```
         *
         * Unlike in C, digits are required after a decimal point.
         *
         * ```temper FAIL
         * 1.
         * ```
         *
         * Which allows Temper to more flexibly combine numbers with class-use syntax.
         *
         * ```temper
         * 64.toString(16 /* hex radix */) == "40"
         * // That '.' is not a decimal point, so that's an integer.
         * ```
         *
         * Temper also does not recognize all C's number suffixes.
         *
         * ```temper FAIL
         * 1F
         * ```
         */
        TokenType.Number -> {
            var text = tokenText.replace("_", "")
            var trimPrefix = false
            val radix = when {
                text.startsWith("0x") || text.startsWith("0X") -> {
                    trimPrefix = true
                    HEX_RADIX
                }
                text.startsWith("0b") || text.startsWith("0B") -> {
                    trimPrefix = true
                    BINARY_RADIX
                }
                text.startsWith("0o") || text.startsWith("0O") -> {
                    trimPrefix = true
                    OCTAL_RADIX
                }
                else -> DECIMAL_RADIX
            }
            if (trimPrefix) {
                text = text.substring(2)
            }
            val typeTag = findNumericTypeSuffix(text, radix)?.let { (unsuffixed, typeTag) ->
                text = unsuffixed
                typeTag
            } ?: when {
                radix == DECIMAL_RADIX && ('.' in text || 'e' in text || 'E' in text) -> TFloat64
                // TODO Unspecified-precision (effectively int64 or bigint?) compile-time-only integer type and ops?
                else -> TInt
            }
            val isInt = typeTag == TInt || typeTag == TInt64
            if (
                isInt && text.startsWith("0") && text.any { it in '1'..'9' } &&
                !trimPrefix
            ) {
                // Left-padded numbers that do not specify zero are a least-surprise violation.
                //    const HUNDRED = 100;
                //    const TEN     = 010; // <-- LIES
                //    const ONE     = 001;
                // Enough so that JS strict mode banned them.
                //
                // There are some domain specific use cases, `chmod` masks,
                // but, as above, we allow 0o010 to the same end.
                return Fail
            }
            try {
                if (isInt) {
                    val long = try {
                        text.toLong(radix)
                    } catch (_: NumberFormatException) {
                        // For supporting -0x8000_0000_0000_0000_i64 for now.
                        // But we expect this to allocate, so only bother with this for unusual cases.
                        // TODO Actually error on any other cases for now? Gives room for compile-time semi-bigints?
                        // TODO But we can't identify here if negated outside the literal.
                        text.toBigInteger(radix).toLong()
                    }
                    when (typeTag) {
                        // TODO Actually error on Int32 beyond -0x8000_0000? Again, external negation is awkward.
                        TInt -> Value(long.toInt(), TInt)
                        TInt64 -> Value(long, TInt64)
                        else -> error("inconceivable")
                    }
                } else {
                    Value(text.toDouble(), TFloat64)
                }
            } catch (e: NumberFormatException) {
                ignore(e)
                return Fail
            }
        }
        TokenType.Word -> when (val parsedName = decodeName(tokenText)) {
            null -> Fail
            else -> Value(Symbol(parsedName.nameText), TSymbol)
        }
        TokenType.QuotedString -> {
            val (decoded, isOk) = unpackQuotedString(tokenText, skipDelimiter = false)
            if (!isOk) {
                Fail
            } else {
                Value(
                    typeTag = TString,
                    stateVector = decoded,
                )
            }
        }
        TokenType.Punctuation -> Value(tokenText, TString) // Reached via QuasiLeaf production.
        else -> Fail
    }
}

/**
 * Finds suffixes like `f64`, `i32`, or `i64` so long as [radix] doesn't allow
 * the suffix char as a digit. Result is undefined for [radix] > 16.
 * @return if suffix found, a pair of the unsuffixed text and the type tag
 */
fun findNumericTypeSuffix(text: String, radix: Int): Pair<String, TypeTag<*>>? {
    var index = text.length - 1
    val lastSuffixStartIndex = max(0, text.length - MAX_NUMERIC_SUFFIX_LENGTH)
    // Note that any return of `null` below
    while (index >= lastSuffixStartIndex) {
        val tag = when (val char = text[index]) {
            'f', 'F' -> when (radix) {
                // A letter in [A-F] may be a terminal digit in hex
                HEX_RADIX -> null
                else -> when (text.substring(index + 1)) {
                    "64" -> TFloat64
                    else -> return null
                }
            }
            'i', 'I' -> when (text.substring(index + 1)) {
                "32" -> TInt
                "64" -> TInt64
                else -> return null
            }
            else -> when (char) {
                !in '0'..'9' -> return null
                else -> null
            }
        }
        if (tag != null) {
            return text.substring(0, index) to tag
        }
        index -= 1
    }
    return null
}

private const val MAX_NUMERIC_SUFFIX_LENGTH = 3
