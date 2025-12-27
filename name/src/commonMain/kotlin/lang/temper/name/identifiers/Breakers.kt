package lang.temper.name.identifiers

import lang.temper.common.appendCodePoint
import lang.temper.common.decodeUtf16Iter
import lang.temper.name.identifiers.Tok.Delimiter
import lang.temper.name.identifiers.Tok.Lower
import lang.temper.name.identifiers.Tok.Nil
import lang.temper.name.identifiers.Tok.Upper

internal inline fun tokenize(text: String, crossinline consumer: (Tok, Int) -> Unit) {
    for (char in decodeUtf16Iter(text)) {
        consumer(char.getToken(), char)
    }
}

internal inline fun breakOnCase(text: String, crossinline consumer: WordConsumer) {
    var sbTok = Nil
    val sb = StringBuilder()
    tokenize(text) { t, c ->
        if (t != sbTok) {
            if (sbTok == Upper && t == Lower) {
                val lastIndex = sb.length - 1
                if (lastIndex > 0) {
                    val save = sb.subSequence(lastIndex, sb.length)
                    consumer(sbTok, sb.subSequence(0, lastIndex))
                    sb.clear()
                    sb.append(save)
                }
            } else {
                if (sb.isNotEmpty()) {
                    consumer(sbTok, sb)
                    sb.clear()
                }
            }
        }
        sb.appendCodePoint(c)
        sbTok = t
    }
    if (sb.isNotEmpty()) {
        consumer(sbTok, sb)
        sb.clear()
    }
}

internal inline fun breakOnDelimiter(text: String, crossinline consumer: WordConsumer) {
    var sbTok = Nil // characterizes the word in the builder
    val sb = StringBuilder()
    tokenize(text) { t, c ->
        sbTok = if (t == Delimiter) {
            if (sb.isNotEmpty()) {
                consumer(sbTok, sb)
                sb.clear()
            }
            Nil
        } else {
            if (sb.isNotEmpty() && !equivalentTokens(t, sbTok)) {
                consumer(sbTok, sb)
                sb.clear()
            }
            sb.appendCodePoint(c)
            t
        }
    }
    if (sb.isNotEmpty()) {
        consumer(sbTok, sb)
    }
    sb.clear()
}

private fun equivalentTokens(curr: Tok, prev: Tok): Boolean = when {
    curr == prev -> true
    curr == Lower && prev == Upper -> true
    else -> false
}
private const val SPACE_CODEPOINT = 32
internal inline fun breakOnSpace(text: String, crossinline consumer: WordConsumer) {
    var sbTok = Nil // characterizes the word in the builder
    val sb = StringBuilder()

    tokenize(text) { t, c ->
        sbTok = if (c == SPACE_CODEPOINT || t == Delimiter) {
            if (sb.isNotEmpty()) {
                consumer(sbTok, sb)
                sb.clear()
            }
            Nil
        } else {
            if (sb.isNotEmpty() && !equivalentTokens(t, sbTok)) {
                consumer(sbTok, sb)
                sb.clear()
            }
            sb.appendCodePoint(c)
            t
        }
    }
    if (sb.isNotEmpty()) {
        consumer(sbTok, sb)
    }
    sb.clear()
}

internal inline fun breakChimeric(text: String, crossinline consumer: WordConsumer) {
    var sbTok = Nil
    val sb = StringBuilder()
    tokenize(text) { t, c ->
        if (t == Delimiter) {
            if (sb.isNotEmpty()) {
                consumer(sbTok, sb)
                sb.clear()
            }
            sbTok = Nil
        } else {
            if (t != sbTok) {
                if (sbTok == Upper && t == Lower) {
                    val lastIndex = sb.length - 1
                    if (lastIndex > 0) {
                        val save = sb.subSequence(lastIndex, sb.length)
                        consumer(sbTok, sb.subSequence(0, lastIndex))
                        sb.clear()
                        sb.append(save)
                    }
                } else {
                    if (sb.isNotEmpty()) {
                        consumer(sbTok, sb)
                        sb.clear()
                    }
                }
                sbTok = t
            }
            sb.appendCodePoint(c)
            sbTok = t
        }
    }
    if (sb.isNotEmpty()) {
        consumer(sbTok, sb)
        sb.clear()
    }
}
