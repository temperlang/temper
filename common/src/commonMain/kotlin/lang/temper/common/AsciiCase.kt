package lang.temper.common

/**
 * Lower-case that only affects ASCII letters and that does not depend on the
 * default locale.
 */
fun (String).asciiLowerCase(): String {
    var sb: StringBuilder? = null
    var appended = 0
    val n = this.length
    for (i in 0 until n) {
        val c = this[i]
        val lc = c.asciiLowerCase()
        if (c != lc) {
            if (sb == null) {
                sb = StringBuilder(n)
            }
            sb.append(this, appended, i)
            sb.append(lc)
            appended = i + 1
        }
    }
    return sb?.append(this, appended, n)?.toString() ?: this
}

/**
 * Upper-case that only affects ASCII letters and that does not depend on the
 * default locale.
 */
fun (String).asciiUpperCase(): String {
    var sb: StringBuilder? = null
    var appended = 0
    val n = this.length
    for (i in 0 until n) {
        val c = this[i]
        val lc = c.asciiUpperCase()
        if (c != lc) {
            if (sb == null) {
                sb = StringBuilder(n)
            }
            sb.append(this, appended, i)
            sb.append(lc)
            appended = i + 1
        }
    }
    return sb?.append(this, appended, n)?.toString() ?: this
}

/**
 * Lower-case that only affects ASCII letters and that does not depend on the
 * default locale.
 */
fun (Char).asciiLowerCase(): Char =
    if (this in 'A'..'Z') { (this.code or LCASE_MASK).toChar() } else { this }

/**
 * Upper-case that only affects ASCII letters and that does not depend on the
 * default locale.
 */
fun (Char).asciiUpperCase(): Char =
    if (this in 'a'..'z') { (this.code and LCASE_MASK.inv()).toChar() } else { this }

private inline fun caseFirstChar(s: String, f: (Char) -> Char): String =
    if (s.isEmpty()) {
        s
    } else {
        val c0 = s[0]
        val uc0 = f(c0)
        if (c0 == uc0) {
            s
        } else {
            val sb = StringBuilder(s)
            sb[0] = uc0
            sb.toString()
        }
    }

/**
 * Title-case of first character that only affects ASCII letters and that does not depend on the
 * default locale.
 */
fun (String).asciiTitleCase(): String = caseFirstChar(this) { it.asciiUpperCase() }

/**
 * Reverse title-case of first character that only affects ASCII letters and that does not depend on
 * the default locale.
 */
fun (String).asciiUnTitleCase(): String = caseFirstChar(this) { it.asciiLowerCase() }

val Char.isAsciiLetter: Boolean get() = (this.code or LCASE_MASK) in C_LOWER_A..C_LOWER_Z

/** ORed with a known ASCII letter, produces an ASCII lower-case letter code. */
const val LCASE_MASK = 32
