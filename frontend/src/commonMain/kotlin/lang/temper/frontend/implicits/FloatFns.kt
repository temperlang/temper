package lang.temper.frontend.implicits

import lang.temper.common.formatDouble
import lang.temper.env.InterpMode
import lang.temper.value.ActualValues
import lang.temper.value.Fail
import lang.temper.value.InterpreterCallback
import lang.temper.value.PartialResult
import lang.temper.value.TFloat64
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TString
import lang.temper.value.Value
import kotlin.math.absoluteValue
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

internal interface FloatFns {
    object ToInt : SigFnBuilder("Float64::toInt32") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val double = TFloat64.unpackContent(args[0])
            val int = double.toInt()
            return when ((double - int.toDouble()).absoluteValue < 1.0) {
                true -> Value(int, TInt)
                false -> Fail
            }
        }
    }

    object ToIntUnsafe : SigFnBuilder("Float64::toInt32Unsafe") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(TFloat64.unpackContent(args[0]).toInt(), TInt)
        }
    }

    object ToInt64 : SigFnBuilder("Float64::toInt64") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return when (val double = TFloat64.unpackContent(args[0])) {
                in -MANTISSA64_LIMIT_F64..MANTISSA64_LIMIT_F64 -> Value(double.toLong(), TInt64)
                else -> Fail
            }
        }
    }

    object ToInt64Unsafe : SigFnBuilder("Float64::toInt64Unsafe") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(TFloat64.unpackContent(args[0]).toLong(), TInt64)
        }
    }

    object ToString : SigFnBuilder("Float64::toString") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val d = TFloat64.unpackContent(args[0])
            return Value(formatDouble(d, decimalPlaces = F64_DECIMAL_DIGITS), TString)
        }
    }

    // Math ops.

    object Abs : SigFnBuilder("Float64::abs") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(TFloat64.unpackContent(args[0]).absoluteValue, TFloat64)
        }
    }

    object Acos : SigFnBuilder("Float64::acos") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(acos(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Asin : SigFnBuilder("Float64::asin") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(asin(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Atan : SigFnBuilder("Float64::atan") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(atan(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Atan2 : SigFnBuilder("Float64::atan2") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(atan2(TFloat64.unpackContent(args[0]), TFloat64.unpackContent(args[1])), TFloat64)
        }
    }

    object Ceil : SigFnBuilder("Float64::ceil") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(ceil(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Cos : SigFnBuilder("Float64::cos") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(cos(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Cosh : SigFnBuilder("Float64::cosh") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(cosh(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Exp : SigFnBuilder("Float64::exp") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(exp(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Expm1 : SigFnBuilder("Float64::expm1") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(expm1(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Floor : SigFnBuilder("Float64::floor") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(floor(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Log : SigFnBuilder("Float64::log") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(ln(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Log10 : SigFnBuilder("Float64::log10") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(log10(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Log1p : SigFnBuilder("Float64::log1p") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(ln1p(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    /** Result is NaN if either is NaN. */
    object Max : SigFnBuilder("Float64::max") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(max(TFloat64.unpackContent(args[0]), TFloat64.unpackContent(args[1])), TFloat64)
        }
    }

    /** Result is NaN if either is NaN. */
    object Min : SigFnBuilder("Float64::min") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(min(TFloat64.unpackContent(args[0]), TFloat64.unpackContent(args[1])), TFloat64)
        }
    }

    object Round : SigFnBuilder("Float64::round") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(round(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Sign : SigFnBuilder("Float64::sign") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(sign(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Sin : SigFnBuilder("Float64::sin") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(sin(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Sinh : SigFnBuilder("Float64::sinh") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(sinh(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Sqrt : SigFnBuilder("Float64::sqrt") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(sqrt(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Tan : SigFnBuilder("Float64::tan") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(tan(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }

    object Tanh : SigFnBuilder("Float64::tanh") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(tanh(TFloat64.unpackContent(args[0])), TFloat64)
        }
    }
}

/** See reasoning for `Number.MAX_SAFE_INTEGER` in ECMAScript. */
internal const val MANTISSA64_LIMIT = (1L shl 53) - 1
internal const val MANTISSA64_LIMIT_F64 = MANTISSA64_LIMIT.toDouble()
