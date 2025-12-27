package lang.temper.name

import lang.temper.common.ClosedOpenRange
import lang.temper.common.DECIMAL_RADIX
import lang.temper.common.RResult
import lang.temper.common.SimpleCoRange
import lang.temper.common.orThrow
import lang.temper.common.toStringViaBuilder
import kotlin.math.min

/**
 * A partial semantic version per https://semver.org/.
 *
 * An incomplete semantic version may omit a patch version and if it omits the patch version may
 * omit a minor version.
 *
 * Incomplete versions allow specifying ranges of semantic versions.
 * For example, `1.2` includes all semantic versions that have [major] version 1,
 * any [minor] version >= 2, and no pre-release identifiers regardless of [patch] version.
 */
sealed class PartialSemVer : Comparable<PartialSemVer> {
    abstract val major: Int
    abstract val minor: Int?
    abstract val patch: Int?
    abstract val preReleaseIdentifiers: List<SemVerPreReleaseIdentifier>
    abstract val buildIdentifiers: List<SemVerBuildIdentifier>

    /**
     * Implements the [precedence algorithm](https://semver.org/#spec-item-11).
     *
     * *Higher* precedence implies greater-than.
     */
    override fun compareTo(other: PartialSemVer): Int {
        // Step 1, 2
        var delta = this.major - other.major
        if (delta == 0) {
            delta = (this.minor ?: -1).compareTo(other.minor ?: -1)
            if (delta == 0) {
                delta = (this.patch ?: -1).compareTo(other.patch ?: -1)
                if (delta == 0) {
                    // Step 3
                    // > When major, minor, and patch are equal, a pre-release version has lower
                    // > precedence than a normal version
                    delta = this.preReleaseIdentifiers.isEmpty()
                        .compareTo(other.preReleaseIdentifiers.isEmpty())
                    if (delta == 0) {
                        // Step 4
                        delta = PreReleaseIdentifierComparator.compare(
                            this.preReleaseIdentifiers,
                            other.preReleaseIdentifiers,
                        )
                        // https://semver.org/#spec-item-10
                        // > Build metadata MUST be ignored when determining version precedence.
                    }
                }
            }
        }
        return delta
    }

    /**
     *     <valid semver> ::= <version core>
     *                      | <version core> "-" <pre-release>
     *                      | <version core> "+" <build>
     *                      | <version core> "-" <pre-release> "+" <build>
     *
     *     <version core> ::= <major> "." <minor> "." <patch>
     */
    override fun toString(): String = toStringViaBuilder { out ->
        out.append(major)
        if (minor != null) {
            out.append('.')
            out.append(minor)
            if (patch != null) {
                out.append('.')
                out.append(patch)
            }
        }
        if (preReleaseIdentifiers.isNotEmpty()) {
            out.append('-')
            preReleaseIdentifiers.joinTo(out, separator = ".")
        }
        if (buildIdentifiers.isNotEmpty()) {
            out.append('+')
            buildIdentifiers.joinTo(out, separator = ".")
        }
    }

    override fun equals(other: Any?): Boolean =
        other is PartialSemVer &&
            this.major == other.major &&
            this.minor == other.minor &&
            this.patch == other.patch &&
            this.preReleaseIdentifiers == other.preReleaseIdentifiers &&
            this.buildIdentifiers == other.buildIdentifiers

    override fun hashCode(): Int =
        major + 31 * (
            (minor ?: -1) + 31 * (
                (patch ?: -1) + 31 * (
                    preReleaseIdentifiers.hashCode() + 31 *
                        buildIdentifiers.hashCode()
                    )
                )
            )

    object PreReleaseIdentifierComparator : Comparator<List<SemVerPreReleaseIdentifier>> {
        /** Per step 4 of https://semver.org/#spec-item-11 */
        override fun compare(
            a: List<SemVerPreReleaseIdentifier>,
            b: List<SemVerPreReleaseIdentifier>,
        ): Int {
            val aSize = a.size
            val bSize = b.size
            val minLen = min(aSize, bSize)
            for (i in 0 until minLen) {
                val delta = a[i].compareTo(b[i])
                if (delta != 0) { return delta }
            }
            // > A larger set of pre-release fields has a higher precedence than a smaller set,
            // > if all the preceding identifiers are equal.
            return aSize.compareTo(bSize)
        }
    }

    infix fun until(that: PartialSemVer): ClosedOpenRange<PartialSemVer> = SimpleCoRange(this, that)
}

/** A semantic version per https://semver.org/ */
class SemVer(
    override val major: Int,
    override val minor: Int,
    override val patch: Int,
    override val preReleaseIdentifiers: List<SemVerPreReleaseIdentifier> = emptyList(),
    override val buildIdentifiers: List<SemVerBuildIdentifier> = emptyList(),
) : PartialSemVer() {
    init {
        require(
            major >= 0 &&
                minor >= 0 &&
                patch >= 0,
        ) {
            "major=$major, minor=$minor, patch=$patch"
        }
    }

    companion object {
        operator fun invoke(text: String): RResult<SemVer, IllegalArgumentException> =
            RResult.of(IllegalArgumentException::class) {
                val n = text.length
                // Find the delimiters
                val dot0 = text.indexOf('.')
                val dot1 = if (dot0 >= 0) {
                    text.indexOf('.', dot0 + 1)
                } else {
                    -1
                }
                val plus = text.lastIndexOf('+')
                val dash = if (dot1 >= 0) {
                    // Find the first dash between the end of the minor version and the start of
                    // any build labels.
                    // This separates pre-release labels from build labels.
                    (
                        (dot1 + 1) until (
                            if (plus >= 0) {
                                plus
                            } else {
                                n
                            }
                            )
                        ).firstOrNull {
                        text[it] == '-'
                    } ?: -1
                } else {
                    -1
                }
                if (dot1 < 0) {
                    throw IllegalArgumentException(text)
                }
                val major = parseUnsignedInt(text, 0, dot0)
                val minor = parseUnsignedInt(text, dot0 + 1, dot1)
                val patchEnd = when {
                    dash >= 0 -> dash
                    plus >= 0 -> plus
                    else -> n
                }
                val patch = parseUnsignedInt(text, dot1 + 1, patchEnd)
                val preReleaseIdentifiers = if (dash <= 0) {
                    emptyList()
                } else {
                    val dashEnd = if (plus >= 0) { plus } else { n }
                    text.substring(dash + 1, dashEnd).split(".").map {
                        SemVerPreReleaseIdentifier(it).orThrow()
                    }
                }
                val buildIdentifiers = if (plus <= 0) {
                    emptyList()
                } else {
                    text.substring(plus + 1, n).split(".").map {
                        SemVerBuildIdentifier(it).orThrow()
                    }
                }
                SemVer(
                    major = major,
                    minor = minor,
                    patch = patch,
                    preReleaseIdentifiers = preReleaseIdentifiers,
                    buildIdentifiers = buildIdentifiers,
                )
            }
    }
}

/**
 * A made-up invalid semantic version-like construct that allows
 * representing dependency ranges like `1.2` which allows `1.2` or a greater minor version with
 * any patch number.
 */
class SemVerPrefix(
    override val major: Int,
    override val minor: Int? = null,
    override val patch: Int? = null,
) : PartialSemVer() {
    override val buildIdentifiers: List<SemVerBuildIdentifier>
        get() = emptyList()
    override val preReleaseIdentifiers: List<SemVerPreReleaseIdentifier>
        get() = emptyList()

    init {
        require(
            major >= 0 &&
                (minor == null || minor >= 0) &&
                (patch == null || (minor != null && patch >= 0)),
        ) {
            "major=$major, minor=$minor, patch=$patch"
        }
    }
}

private fun parseUnsignedInt(text: String, left: Int, rightExclusive: Int): Int {
    var ok = true
    var value = 0
    if (left >= rightExclusive) {
        ok = false
    } else if (text[left] == '0') {
        ok = rightExclusive == left + 1 // "0" is ok, but zero followed by other digits is not.
    } else {
        value = text.substring(left, rightExclusive).toInt(DECIMAL_RADIX)
    }
    if (!ok) {
        throw IllegalArgumentException(
            "${text.substring(left, rightExclusive)} is not a version part",
        )
    }
    return value
}
