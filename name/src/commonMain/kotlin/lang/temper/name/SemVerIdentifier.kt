package lang.temper.name

import lang.temper.common.DECIMAL_RADIX
import lang.temper.common.RFailure
import lang.temper.common.RResult

/**
 * Identifiers as defines in semver.org.
 *
 * https://semver.org/#summary explains the purpose for these identifiers.
 *
 * > Additional labels for pre-release and build metadata are available as extensions to the
 * > MAJOR.MINOR.PATCH format.
 */
sealed interface SemVerIdentifier {
    companion object {
        /**
         * https://semver.org/#spec-item-9
         * > Identifiers MUST comprise only ASCII alphanumerics and hyphens `[0-9A-Za-z-]`.
         * > Identifiers MUST NOT be empty. Numeric identifiers MUST NOT include leading zeroes.
         */
        fun isSemVerIdentifier(s: String): Boolean {
            if (s.isEmpty()) { return false }
            for (c in s) {
                when (c) {
                    in '0'..'9',
                    in 'A'..'Z',
                    in 'a'..'z',
                    '-',
                    -> Unit
                    else -> return false
                }
            }
            return true
        }

        /**
         * https://semver.org/#spec-item-9
         * > Numeric identifiers MUST NOT include leading zeroes.
         *
         *     <numeric identifier> ::= "0"
         *                            | <positive digit>
         *                            | <positive digit> <digits>
         */
        fun isSemVerNumericIdentifier(s: String): Boolean {
            if (s.isEmpty()) { return false } // Empty not an identifier.
            // "-0" is disallowed, so no need to check for leading "-"
            if (s[0] == '0') { return s.length == 1 }
            return s.all { it in '0'..'9' }
        }
    }
}

/**
 *     <pre-release identifier> ::= <alphanumeric identifier>
 *                                | <numeric identifier>
 */
sealed interface SemVerPreReleaseIdentifier :
    SemVerIdentifier,
    Comparable<SemVerPreReleaseIdentifier> {
    /** Per step 4 of https://semver.org/#spec-item-11 */
    override fun compareTo(other: SemVerPreReleaseIdentifier): Int {
        return when (this) {
            is NumericSemVerIdentifier -> when (other) {
                // > Identifiers consisting of only digits are compared numerically.
                is NumericSemVerIdentifier -> this.numericValue.compareTo(other.numericValue)
                is AlphaNumericSemVerIdentifier -> -1
            }
            is AlphaNumericSemVerIdentifier -> when (other) {
                // > Numeric identifiers always have lower precedence than non-numeric identifiers.
                is NumericSemVerIdentifier -> 1
                // > Identifiers with letters or hyphens are compared lexically
                // > in ASCII sort order.
                is AlphaNumericSemVerIdentifier -> this.text.compareTo(other.text)
                // TODO: Kotlin doesn't document this as Locale-insensitive
            }
        }
    }

    companion object {
        /**
         * Create identifier from text.
         *
         *     <pre-release identifier> ::= <alphanumeric identifier>
         *                                | <numeric identifier>
         */
        operator fun invoke(
            text: String,
        ): RResult<SemVerPreReleaseIdentifier, IllegalArgumentException> = when {
            !SemVerIdentifier.isSemVerIdentifier(text) ->
                RFailure(IllegalArgumentException(text))
            SemVerIdentifier.isSemVerNumericIdentifier(text) ->
                RResult.of(IllegalArgumentException::class) {
                    NumericSemVerIdentifier(text.toInt(DECIMAL_RADIX))
                }
            else -> RResult.of(IllegalArgumentException::class) {
                AlphaNumericSemVerIdentifier(text)
            }
        }
    }
}

/**
 *     <build identifier> ::= <alphanumeric identifier>
 *                          | <digits>
 */
sealed interface SemVerBuildIdentifier : SemVerIdentifier {
    companion object {
        /**
         * Create identifier from text.
         *
         *     <build identifier> ::= <alphanumeric identifier>
         *                          | <digits>
         */
        operator fun invoke(
            text: String,
        ): RResult<SemVerBuildIdentifier, IllegalArgumentException> = when {
            !SemVerIdentifier.isSemVerIdentifier(text) ->
                RFailure(IllegalArgumentException(text))
            text.all { it in '0'..'9' } ->
                RResult.of(IllegalArgumentException::class) {
                    DigitsSemVerIdentifier(text)
                }
            else -> RResult.of(IllegalArgumentException::class) {
                AlphaNumericSemVerIdentifier(text)
            }
        }
    }
}

/**
 *     <alphanumeric identifier> ::= <non-digit>
 *                                 | <non-digit> <identifier characters>
 *                                 | <identifier characters> <non-digit>
 *                                 | <identifier characters> <non-digit> <identifier characters>
 */
data class AlphaNumericSemVerIdentifier(
    val text: String,
) : SemVerPreReleaseIdentifier, SemVerBuildIdentifier {
    override fun toString(): String = text

    init {
        require(SemVerIdentifier.isSemVerIdentifier(text) && text.any { it !in '0'..'9' })
    }
}

/**
 *     <numeric identifier> ::= "0"
 *                            | <positive digit>
 *                            | <positive digit> <digits>
 */
data class NumericSemVerIdentifier(val numericValue: Int) : SemVerPreReleaseIdentifier {
    override fun toString(): String = "$numericValue"

    init {
        require(numericValue >= 0)
    }
}

/** Build identifiers can just be a big run of digits. */
data class DigitsSemVerIdentifier(val digits: String) : SemVerBuildIdentifier {
    override fun toString(): String = digits

    init {
        require(digits.all { it in '0'..'9' }) { digits }
    }
}
