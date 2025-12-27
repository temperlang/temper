package lang.temper.be.names

import lang.temper.common.HEX_RADIX
import lang.temper.common.UnicodeNormalForm

fun unicodeToAscii(name: String): String = run {
    when {
        name.matches(asciiNameRegex) -> name
        else -> name.replace(badAsciiNameCharRegex) { match ->
            punctuationSubstitutes.getOrElse(match.value[0]) {
                // Try normalizing but convert any further bad chars to hex.
                // TODO Instead try this? https://central.sonatype.com/artifact/net.gcardone.junidecode/junidecode
                // TODO It's about 400k and could increase ambiguities, but would be more readable by default.
                // TODO Supposedly much smaller than icu4j.
                UnicodeNormalForm.NFKD.invoke(match.value).replace(badAsciiNameCharRegex) { match2 ->
                    punctuationSubstitutes.getOrElse(match2.value[0]) {
                        match2.value.codePoints().toArray().joinToString { "x${it.toString(HEX_RADIX)}" }
                    }
                }
            }
        }
    }
}

val asciiNameRegex = Regex("[a-zA-Z_][a-zA-Z0-9_]*")
val badAsciiNameCharRegex = Regex("[^a-zA-Z0-9_]")

val punctuationSubstitutes = mapOf(
    '@' to "at",
    '#' to "hash",
    '%' to "pct",
    '^' to "caret",
    '&' to "amp",
    '*' to "start",
    '(' to "paren",
    ')' to "rparen",
    '-' to "dash",
    '+' to "plus",
    ':' to "colon",
    ',' to "comma",
    '<' to "lt",
    '>' to "gt",
    '=' to "eq",
    '.' to "dot",
    ';' to "semi",
    '/' to "div",
    '|' to "bar",
    '~' to "tilde",
    '"' to "quot",
    '\'' to "tick",
)
