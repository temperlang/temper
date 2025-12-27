package lang.temper.common

/**
 * *ArgvQuote* from ["Everyone Quotes Command Line Arguments The Wrong Way"](https://web.archive.org/web/20190109172835/https://blogs.msdn.microsoft.com/twistylittlepassagesallalike/2011/04/23/everyone-quotes-command-line-arguments-the-wrong-way/)
 */
fun escapeForWindowsNoCmd(s: String): String {
    if (s.isEmpty()) { return "\"\"" }
    if (
        s.none {
            when (it) {
                ' ', '\t', '\n', '\u000b', '"' -> true
                else -> false
            }
        }
    ) {
        return s
    }
    return buildString {
        append('"')

        var i = 0
        val end = s.length
        while (i < end) {
            var numBackslashes = 0
            while (i < end && s[i] == '\\') {
                i += 1
                numBackslashes += 1
            }
            if (i == end) {
                // Escape all backslashes, but let the terminating
                // double quotation mark we add below be interpreted
                // as a metacharacter.
                repeat(numBackslashes * 2) {
                    append('\\')
                }
                break
            } else if (s[i] == '"') {
                // Escape all backslashes and the following
                // double quotation mark.
                repeat(numBackslashes * 2 + 1) {
                    append('\\')
                }
                append('"')
                i += 1
            } else {
                // Backslashes aren't special here.
                repeat(numBackslashes) {
                    append('\\')
                }
                append(s[i])
                i += 1
            }
        }

        append('"')
    }
}

private const val NUM_ASCII = 128

/**
 * All of cmd's transformations are triggered by the presence of one of the
 * metacharacters `(`, `)`, `%`, `!`, `^`, `"`, `<`, `>`, `&`, and `|`.
 */
private val cmdMetaCharacters = BooleanArray(NUM_ASCII).also {
    for (c in "()%!^\"<>&|") {
        it[c.code] = true
    }
}

/**
 * Applies [escapeForWindowsNoCmd] and then additionally escapes `cmd.exe`
 * metacharacters which is only appropriate if the argument is going to be
 * interpreted by `cmd.exe`.
 */
fun escapeForWindowsCmd(s: String): String {
    val sEsc = escapeForWindowsNoCmd(s)
    return if (sEsc.none { cmdMetaCharacters.getOrNull(it.code) == true }) {
        sEsc
    } else {
        buildString {
            for (c in sEsc) {
                if (cmdMetaCharacters.getOrNull(c.code) == true) {
                    append('^')
                }
                append(c)
            }
        }
    }
}
