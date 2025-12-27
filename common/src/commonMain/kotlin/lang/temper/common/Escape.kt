package lang.temper.common

/**
 * HTML escape suitable for embedding a text node in a `white-space:pre` #PCDATA context.
 * Since it assumes preformatted whitespace, it does not attempt to encode newlines to `<br>` for
 * example or convert runs of spaces to `&nbsp;`.
 */
fun htmlEscapeTo(s: String, out: StringBuilder) {
    for (c in s) {
        when (c) {
            '<' -> out.append("&lt;")
            '>' -> out.append("&lt;")
            '&' -> out.append("&amp;")
            '"' -> out.append("&#34;")
            '\'' -> out.append("&#39;")
            else -> out.append(c)
        }
    }
}

/**
 * HTML escape suitable for embedding a text node in a `white-space:pre` #PCDATA context.
 * Since it assumes preformatted whitespace, it does not attempt to encode newlines to `<br>` for
 * example or convert runs of spaces to `&nbsp;`.
 */
fun htmlEscape(s: String): String = toStringViaBuilder { htmlEscapeTo(s, it) }

/**
 * Appends [s] to [onto] but `%` escapes any URL and file-path meta-characters.
 */
fun urlEscapeTo(s: String, onto: StringBuilder) {
    var pos = 0
    esc_loop@
    for (i in s.indices) {
        val replacement = when (s[i]) {
            '#' -> "%23" // URL meta-character
            '%' -> "%25" // URL escape character
            '(' -> "%28" // URL meta-character also used in module names
            ')' -> "%29" // URL meta-character
            '+' -> "%2B" // URL meta-character
            '/' -> "%2F" // UNIX file separator
            ':' -> "%3A" // UNIX path separator
            ';' -> "%3B" // Windows path separator
            '?' -> "%3F" // URL meta-character
            '\\' -> "%5C" // Windows path file separator
            // TODO: is this needed or is this just a shell convention
            '~' -> "%7E" // UNIX home directory prefix
            else -> continue@esc_loop
        }
        onto.append(s, pos, i)
        onto.append(replacement)
        pos = i + 1
    }
    onto.append(s, pos, s.length)
}

fun urlEscape(s: String) = toStringViaBuilder { urlEscapeTo(s, it) }

@Suppress("MagicNumber") // Necessary byte manipulation and char offsets
fun urlUnescape(s: String) = toStringViaBuilder {
    var i = 0
    val n = s.length
    while (i < n) {
        val c = s[i]
        i += 1
        if (c == '+') {
            it.append(' ')
            continue
        }
        if (c == '%' && i + 2 <= n) {
            val bytes = mutableListOf<Byte>()
            var j = i - 1 // At %
            while (j + 3 <= n && s[j] == '%') {
                val c1 = s[j + 1]
                val c2 = s[j + 2]
                val h1 = hexOrNeg1(c1)
                val h2 = hexOrNeg1(c2)
                if (h1 >= 0 && h2 >= 0) {
                    var b = ((h1 shl 4) or h2)
                    if (b > Byte.MAX_VALUE) { b -= 256 }
                    bytes.add(b.toByte())
                    j += 3
                } else {
                    break
                }
            }
            if (bytes.isNotEmpty()) {
                i = j
                fromUtf8Tolerant(bytes.toByteArray(), it)
                continue
            }
        }
        it.append(c)
    }
}

private const val HEX_A_VALUE = 10
private fun hexOrNeg1(c: Char): Int = when (c) {
    in '0'..'9' -> c.code - '0'.code
    in 'A'..'F' -> HEX_A_VALUE + c.code - 'A'.code
    in 'a'..'f' -> HEX_A_VALUE + c.code - 'a'.code
    else -> -1
}
