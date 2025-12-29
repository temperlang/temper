/** Utility functions to help with shell commands. */
package lang.temper.fs

import lang.temper.common.escapeForWindowsCmd

// A safe shell word is one or more safe shell characters.
private val safeShellWord = Regex(
    "^[%+./0-9:=@A-Z_a-z-]+$",
)

/** Escape a string to be passed to a POSIX shell */
fun escapeShellString(str: String) =
    if (safeShellWord.matches(str)) {
        str
    } else {
        "'${str.replace("'", "'\\''")}'"
    }

/** Escape a string to be passed to a Windows cmd shell */
fun escapeWindowsString(value: String): String =
    escapeForWindowsCmd(value)

val windowsEnvValueSpecials = Regex("""[ %<>&|!^]""")

/** Escape a string to be used as a Windows cmd env var value */
fun escapeWindowsEnvValue(value: String): String =
    windowsEnvValueSpecials.replace(value) { m -> "^${m.value}" }

/** Find a common prefix for all strings separated by a delimiter. */
fun Iterable<String>.commonPrefix(delimiter: Char): String {
    val values = iterator()
    values.hasNext() || return ""
    var prefix = values.next()
    for (value in values) {
        prefix = commonPrefixBy(delimiter, prefix, value)
    }
    return prefix
}

/** Finds the common prefix looking at a delimiter; useful for pathnames. */
fun commonPrefixBy(delimiter: Char, left: String, right: String): String {
    var point = -1 // Start by pointing "before" the string
    while (true) {
        val ti = left.indexOf(delimiter, point + 1)
        val oi = right.indexOf(delimiter, point + 1)
        if (ti != oi || ti < 0 || left.substring(point + 1, ti) != right.substring(point + 1, oi)) {
            return left.substring(0, point + 1)
        }
        point = ti
    }
}
