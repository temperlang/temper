package lang.temper.lexer

import lang.temper.common.charCount
import lang.temper.common.decodeUtf16

/** True when [tokenText] is a valid Temper identifier; it may be a reserved word. */
fun isIdentifier(tokenText: String): Boolean {
    val n = tokenText.length
    var isIdentifier = n > 0
    if (isIdentifier) { // try to disprove
        val cp0 = decodeUtf16(tokenText, 0)
        if (cp0 !in IdParts.Start) {
            isIdentifier = false
        }
        var i = charCount(cp0)
        var lastWasMedial = false
        while (i < n) {
            val cp = decodeUtf16(tokenText, i)
            i += charCount(cp)
            if (cp in IdParts.Continue) {
                lastWasMedial = false
            } else if (!lastWasMedial && (cp == '#'.code || cp in IdParts.Medial)) {
                // # is used in Temporary's diagnostic form.
                lastWasMedial = true
            } else {
                isIdentifier = false
                break
            }
        }
        if (lastWasMedial) {
            isIdentifier = false
        }
    }
    return isIdentifier
}

/** True when [tokenText] is a valid Temper identifier and is not a reserved word. */
fun isUnreservedIdentifier(tokenText: String): Boolean =
    tokenText !in reservedWords && isIdentifier(tokenText)
