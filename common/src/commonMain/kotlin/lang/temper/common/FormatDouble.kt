package lang.temper.common

/**
 * Cross-platform double formatting.
 * This is not particularly efficient but is suitable for diagnostics.
 *
 * @param decimalPlaces >= 0.  The maximum number of digits to allow right of the decimal point.
 *      No rounding is done based on truncated digits.
 */
fun formatDouble(d: Double, decimalPlaces: Int): String {
    require(decimalPlaces >= 0)

    if (d == Double.POSITIVE_INFINITY) {
        return "Infinity"
    }
    if (d == Double.NEGATIVE_INFINITY) {
        return "-Infinity"
    }
    if (d.isNaN()) {
        return "NaN"
    }

    val s = run {
        // Adjust default Java formatting to Temper-standard formatting.
        val str = "$d"
        val eIndex = str.indexOfLast { (it.code or LCASE_MASK) == C_LOWER_E }
        if (eIndex >= 0) {
            toStringViaBuilder { sb ->
                // Normalize case of exponent indicator
                sb.append(str)
                sb[eIndex] = 'e'
                // Consistently have a sign
                val next = str[eIndex + 1]
                if (next != '+' && next != '-') {
                    sb.insert(eIndex + 1, '+')
                }
            }
        } else {
            str
        }
    }

    val n = s.length

    val dot = s.indexOf('.')
    val startOfFraction: Int
    val endOfFraction: Int
    // Find start and end of fraction portion
    if (dot >= 0) {
        startOfFraction = dot + 1
        var end = startOfFraction
        while (end < n && s[end] in '0'..'9') {
            end += 1
        }
        endOfFraction = end
    } else {
        val exponentStart = s.indexOf('e')
        if (exponentStart < 0) {
            // 123
            startOfFraction = n
            endOfFraction = n
        } else {
            // 1E100
            startOfFraction = exponentStart
            endOfFraction = exponentStart
        }
    }

    return if (startOfFraction == n) {
        // There's no fraction nor an exponent
        // Convert 1 to 1.0 or to 1. as appropriate
        s.replaceRange(
            startOfFraction until endOfFraction,
            replacement = if (decimalPlaces > 0) { ".0" } else { "." },
        )
    } else if (startOfFraction + decimalPlaces >= endOfFraction) {
        s
    } else {
        s.removeRange(startOfFraction + decimalPlaces, endOfFraction)
    }
}
