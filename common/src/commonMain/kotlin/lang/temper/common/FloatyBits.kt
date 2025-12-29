package lang.temper.common

// This file is a home for extension functions related to floating point values.

/** True iff this is IEEE 754 **positive** zero. */
fun Double.isPosZero(): Boolean = this == +0.0 && (1.0 / this) > 0.0

/** True iff this is IEEE 754 **negative** zero. */
@Suppress("MagicNumber") // Static analyzer, you're not wrong.
fun Double.isNegZero(): Boolean = this == -0.0 && (1.0 / this) < 0.0

/**
 * Easy `when` matching for literal doubles; e.g. `when (val x = ParseDouble(someValue))` to construct.
 *
 * Does **not** implement toString, hashCode, equals.
 */
sealed interface ParseDouble {
    /** The original value parsed */
    val value: Double

    /** Whether the value is negative including `-0.0` */
    val isNeg: Boolean get() = false

    /** Whether the value is positive including `0.0` */
    val isPos: Boolean get() = false

    /** Whether the value is `-0.0` or `0.0` */
    val isZero: Boolean get() = false

    /** Whether the value is `+inf` or `-inf` */
    val isInfinite: Boolean get() = false

    /** Implemented if the value is positive including `0.0` */
    sealed interface Positive : ParseDouble {
        override val isPos get() = true
    }

    /** Implemented if the value is negative including `-0.0` */
    sealed interface Negative : ParseDouble {
        override val isNeg get() = true
    }

    /** Implemented if the value is `+inf` or `-inf` */
    sealed interface Infinity : ParseDouble {
        override val isInfinite get() = true
    }
    object PositiveInfinity : Infinity, Positive {
        override val value: Double = Double.POSITIVE_INFINITY
    }
    object NegativeInfinity : Infinity, Negative {
        override val value: Double = Double.NEGATIVE_INFINITY
    }
    object NaN : ParseDouble {
        override val value: Double = Double.NaN
    }
    sealed interface Zero : ParseDouble {
        override val isZero get() = true
    }
    object PositiveZero : Zero, Positive {
        override val value: Double = 0.0
    }
    object NegativeZero : Zero, Negative {
        @Suppress("MagicNumber") // Kinda is.
        override val value: Double = -0.0
    }
    class RegularPositive(override val value: Double) : ParseDouble, Positive {
        init {
            require(value.isFinite() && value > 0.0)
        }
    }
    class RegularNegative(override val value: Double) : ParseDouble, Negative {
        init {
            require(value.isFinite() && value < 0.0)
        }
    }

    companion object {
        operator fun invoke(value: Double): ParseDouble =
            when {
                value == Double.POSITIVE_INFINITY -> PositiveInfinity
                value == Double.NEGATIVE_INFINITY -> NegativeInfinity
                value.isNaN() -> NaN
                value.isPosZero() -> PositiveZero
                value.isNegZero() -> NegativeZero
                value < 0.0 -> RegularNegative(value)
                else -> RegularPositive(value)
            }
    }
}
