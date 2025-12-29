package lang.temper.value

import kotlin.math.sign

/**
 * A comparator for floating point values that compares -0 just before zero and all NaN values just
 * above +Infinity.
 */
object ConsistentDoubleComparator : Comparator<Double> {
    /**
     * <!-- snippet: float64-comparison-details -->
     * # Float64 comparison details
     * [snippet/type/Float64]s are compared on a modified number line where
     * `-0.0` precedes `+0.0` and all `NaN` values sort above +&infin;.
     * This differs from the [IEEE-754 comparison predicate] which treats `NaN` as incomparable.
     *
     * ```temper
     * -Infinity < -1.0     &&
     *      -1.0 <  0.0     &&
     *      -0.0 < +0.0     &&  // Positive and negative zero order separately
     *       0.0 <  1.0     &&
     *       1.0 < Infinity &&
     *  Infinity < NaN          // NaN is ordered high
     * ```
     *
     * [IEEE-754 comparison predicate]: https://grouper.ieee.org/groups/msc/ANSI_IEEE-Std-754-2019/background/predicates.txt
     */
    override fun compare(a: Double, b: Double): Int =
        if (a != a) { // NaN
            if (b != b) { // NaN
                0
            } else { // Sort NaN late
                1
            }
        } else if (b != b) { // NaN
            -1
        } else if (a == 0.0 && b == 0.0) {
            // Sort -0 before +0
            (1.0 / a).sign.compareTo((1.0 / b).sign)
        } else {
            a.compareTo(b)
        }
}
