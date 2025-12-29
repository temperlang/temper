package lang.temper.frontend.implicits

import lang.temper.builtin.StringIndexSupport
import lang.temper.builtin.StringIndexSupport.noStringIndexTClass
import lang.temper.builtin.StringIndexSupport.stringIndexOffsetProperty
import lang.temper.builtin.StringIndexSupport.stringIndexTClass
import lang.temper.builtin.StringIndexSupport.unpackStringIndex
import lang.temper.common.C_MAX_SURROGATE
import lang.temper.common.C_MIN_SURROGATE
import lang.temper.common.MIN_SUPPLEMENTAL_CP
import lang.temper.env.InterpMode
import lang.temper.value.ActualValues
import lang.temper.value.Fail
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InterpreterCallback
import lang.temper.value.PartialResult
import lang.temper.value.TBoolean
import lang.temper.value.TFloat64
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.unpackOrFail
import lang.temper.value.unpackPositionedOr
import kotlin.math.max
import kotlin.math.min

internal object StringFns {
    private fun packStringIndex(offset: Int): Value<*> = Value(
        InstancePropertyRecord(
            mutableMapOf(stringIndexOffsetProperty to Value(offset, TInt)),
        ),
        stringIndexTClass,
    )

    private fun packNoStringIndex(): Value<*> = Value(
        InstancePropertyRecord(mutableMapOf()),
        noStringIndexTClass,
    )

    object Get : SigFnBuilder("String::get") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val str = TString.unpackContent(args[0])
            val index = stringIndexTClass.unpackOrFail(args, 1, cb, interpMode) {
                return@invoke it
            }.let { unpackStringIndex(it) }
            val codePoint = if (index in str.indices) str.codePointAt(index) else return Fail
            return Value(codePoint, TInt)
        }
    }

    private inline fun withStringAndTwoBounds(
        arity: Int,
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
        f: (String, Int, Int) -> PartialResult,
    ): PartialResult {
        args.unpackPositionedOr(arity, cb) { return@withStringAndTwoBounds it }
        val str = TString.unpackContent(args[0])
        val left = stringIndexTClass.unpackOrFail(args, 1, cb, interpMode) {
            return@withStringAndTwoBounds it
        }.let { unpackStringIndex(it) }
        val right = stringIndexTClass.unpackOrFail(args, 2, cb, interpMode) {
            return@withStringAndTwoBounds it
        }.let { unpackStringIndex(it) }
        // Clamp to bounds
        val leftAdjusted = min(left, str.length)
        val rightAdjusted = max(leftAdjusted, min(right, str.length))
        return f(str, leftAdjusted, rightAdjusted)
    }

    object CountBetween : SigFnBuilder("String::countBetween") {
        private const val ARITY = 3

        override fun invoke(
            args: ActualValues,
            cb: InterpreterCallback,
            interpMode: InterpMode,
        ) = withStringAndTwoBounds(ARITY, args, cb, interpMode) { str, left, right ->
            Value(count(str, left, right) { false }, TInt)
        }

        internal inline fun count(str: String, left: Int, right: Int, answerSuffices: (Int) -> Boolean): Int {
            var count = 0
            var i = left
            while (i < right) {
                count += 1
                // Used by hasAtLeast to avoid unnecessary large string traversal.
                if (answerSuffices(count)) { break }

                val c = str[i]
                i += 1
                // If the current character is a leading surrogate,
                // skip counting its matching trailing surrogate, if any.
                if (
                    i < right &&
                    c in '\uD800'..'\uDBFF' &&
                    str[i] in '\uDC00'..'\uDFFF'
                ) {
                    i += 1
                }
            }
            return count
        }
    }

    object FromCodePoint : SigFnBuilder("String::fromCodePoint") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val codePoint = TInt.unpackOrFail(args, 0, cb, interpMode) {
                return@invoke it
            }
            if (codePoint in C_MIN_SURROGATE..C_MAX_SURROGATE) {
                return Fail
            }
            val string = try {
                String(Character.toChars(codePoint))
            } catch (_: IllegalArgumentException) {
                return Fail
            }
            return Value(string, TString)
        }
    }

    object FromCodePoints : SigFnBuilder("String::fromCodePoints") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            // TODO Convenient way to unpack content without throwing exceptions on failure?
            val codePoints = TList.unpackContent(args[0])
            val stringContent = buildString {
                for (codePointValue in codePoints) {
                    val codePoint = (TInt.unpackOrNull(codePointValue) ?: return@invoke Fail)
                    if (codePoint in C_MIN_SURROGATE..C_MAX_SURROGATE) {
                        return Fail
                    }
                    appendCodePoint(codePoint)
                }
            }
            return Value(stringContent, TString)
        }
    }

    object GetBegin : SigFnBuilder("String::begin") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return packStringIndex(0)
        }
    }

    object GetEnd : SigFnBuilder("String::end") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val str = TString.unpackContent(args[0])
            return packStringIndex(str.length)
        }
    }

    object HasAtLeast : SigFnBuilder("String::hasAtLeast") {
        private const val COUNT_ARG_INDEX = 3
        private const val ARITY = 4

        override fun invoke(
            args: ActualValues,
            cb: InterpreterCallback,
            interpMode: InterpMode,
        ) = withStringAndTwoBounds(ARITY, args, cb, interpMode) { str, left, right ->
            val minCount = TInt.unpackOrFail(args, COUNT_ARG_INDEX, cb, interpMode) {
                return@invoke it
            }

            val nUtf16 = right - left
            TBoolean.value(
                when {
                    // Check if there couldn't be enough even if every code-point takes one.
                    // Checking this first avoids possible underflow below.
                    nUtf16 < minCount -> false
                    // Check if there are enough even if every code-point takes two.
                    nUtf16 >= minCount * 2 -> true
                    // Fall back to accurate count, but early out.
                    else -> CountBetween.count(str, left, right) { it >= minCount } >= minCount
                },
            )
        }
    }

    object HasIndex : SigFnBuilder("String::hasIndex") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val str = TString.unpackContent(args[0])
            val index = stringIndexTClass.unpackOrFail(args, 1, cb, interpMode) {
                return@invoke it
            }.let { unpackStringIndex(it) }
            return TBoolean.value(index in str.indices)
        }
    }

    object Next : SigFnBuilder("String::next") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val str = TString.unpackContent(args[0])
            val index = stringIndexTClass.unpackOrFail(args, 1, cb, interpMode) {
                return@invoke it
            }.let { unpackStringIndex(it) }
            val newIndex = when {
                index !in str.indices -> str.length
                str.codePointAt(index) >= MIN_SUPPLEMENTAL_CP -> index + 2
                else -> index + 1
            }
            return packStringIndex(newIndex)
        }
    }

    object Prev : SigFnBuilder("String::prev") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val str = TString.unpackContent(args[0])
            val index = stringIndexTClass.unpackOrFail(args, 1, cb, interpMode) {
                return@invoke it
            }.let { unpackStringIndex(it) }
            var newIndex = min(index, str.length)
            if (newIndex > 0) {
                newIndex -= 1
                if (
                    newIndex != 0 &&
                    str[newIndex] in '\uDC00'..'\uDFFF' &&
                    str[newIndex - 1] in '\uD800'..'\uDBFF'
                ) {
                    newIndex -= 1
                }
            }
            return packStringIndex(newIndex)
        }
    }

    object Split : SigFnBuilder("String::split") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val separated = TString.unpackContent(args[0])
            val separator = TString.unpackOrFail(args, 1, cb, interpMode) { return@invoke it }
            // TODO: if separator starts or ends with an orphaned UTF-16 surrogate, then just return
            // the string containing separated
            val elements = mutableListOf<Value<*>>()
            when (separator) {
                "" -> {
                    // Turn each code point into a separate string. Default split includes edges and goes 16-bit.
                    var index = 0
                    while (index < separated.length) {
                        // This seems to work ok even with trailing unfinished pairs.
                        val end = index + Character.charCount(separated.codePointAt(index))
                        elements.add(Value(separated.substring(index, end), TString))
                        index = end
                    }
                }
                else -> separated.split(separator).mapTo(elements) { Value(it, TString) }
            }
            return Value(elements, TList)
        }
    }

    object Slice : SigFnBuilder("String::slice") {
        private const val ARITY = 3

        override fun invoke(
            args: ActualValues,
            cb: InterpreterCallback,
            interpMode: InterpMode,
        ) = withStringAndTwoBounds(ARITY, args, cb, interpMode) { str, left, right ->
            Value(str.substring(left, right), TString)
        }
    }

    object ToFloat64 : SigFnBuilder("String::toFloat64") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val string = TString.unpackContent(args[0]).trim()
            val float = when (string.startsWith('.') || string.endsWith('.')) {
                true -> null
                false -> string.toDoubleOrNull()
            }
            return float?.let { Value(it, TFloat64) } ?: Fail
        }
    }

    object ToInt : SigFnBuilder("String::toInt32") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            // This radix unpacking is the same as for `Int32::toString`.
            @Suppress("MagicNumber")
            val radix = TInt.unpackWithNullDefault(args, 1, 10, cb, interpMode) { return@invoke it }
            radix in MIN_INT_RADIX..MAX_INT_RADIX || return Fail
            // Trim to allow surrounding whitespace because json.
            return TString.unpackContent(args[0]).trim().toIntOrNull(radix)?.let { Value(it, TInt) } ?: Fail
        }
    }

    object ToInt64 : SigFnBuilder("String::toInt64") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            // This radix unpacking is the same as for `Int32::toString`.
            @Suppress("MagicNumber")
            val radix = TInt.unpackWithNullDefault(args, 1, 10, cb, interpMode) { return@invoke it }
            radix in MIN_INT_RADIX..MAX_INT_RADIX || return Fail
            // Trim to allow surrounding whitespace because json.
            return TString.unpackContent(args[0]).trim().toLongOrNull(radix)?.let { Value(it, TInt64) } ?: Fail
        }
    }

    object GetNone : SigFnBuilder("StringIndex::none") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return packNoStringIndex()
        }
    }

    object StringIndexOptionCompareTo : SigFnBuilder("StringIndexOption::compareTo") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return StringIndexSupport.compare(args, cb, interpMode)
        }
    }
}
