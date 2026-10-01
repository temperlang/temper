package lang.temper.be.elixir

/** A plain atom, `:ok` or `:empty?`; anything else needs quotes, `:"with spaces"`. */
private val plainAtom = Regex("^[a-z_][a-zA-Z0-9_]*[?!]?$")

internal fun elixirAtomText(text: String): String =
    if (plainAtom.matches(text)) ":$text" else ":${elixirStringText(text)}"

/**
 * Quotes a string as an Elixir string literal.
 *
 * `#` is escaped everywhere, not only before `{`: `#{` interpolates, `\#` is a
 * valid escape for `#`, and escaping all of them needs no lookahead. Control
 * characters use `\u{...}`, which Elixir accepts for any code point.
 *
 * Elixir source is UTF-8, so a lone UTF-16 surrogate has no encoding at all.
 * Writing one out would silently become `?`, so it is an error instead.
 */
internal fun elixirStringText(value: String): String = buildString {
    append('"')
    var i = 0
    while (i < value.length) {
        val char = value[i]
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '#' -> append("\\#")
            char == '\n' -> append("\\n")
            char == '\t' -> append("\\t")
            char == '\r' -> append("\\r")
            char.code < 0x20 || char.code == 0x7f -> append("\\u{${char.code.toString(16)}}")
            char.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate() -> {
                append(char)
                append(value[i + 1])
                i += 1
            }
            char.isSurrogate() ->
                error("lone surrogate U+${char.code.toString(16)} at $i cannot be written as UTF-8 Elixir source")
            else -> append(char)
        }
        i += 1
    }
    append('"')
}

/**
 * Elixir needs digits on both sides of a float's point (`1.` and `.5` are
 * syntax errors, and so is `1e10`), and `1.0` must keep its `.0` to stay a
 * float. Kotlin's `1.0E10` is accepted as written.
 *
 * BEAM floats have no NaN and no infinity: `1.0 / 0.0` raises
 * ArithmeticError. There is no literal to emit for them, so they are errors
 * here rather than a stand-in value that would compute something else.
 */
internal fun elixirNumberText(value: Number): String = when (value) {
    is Double -> when {
        value.isNaN() || value.isInfinite() ->
            error("$value has no representation on the BEAM, whose floats have no NaN or infinity")
        // -0.0 == 0.0, so the whole-number branch below would drop its sign,
        // and since OTP 27 `0.0 === -0.0` is false
        value == 0.0 && 1.0 / value < 0 -> "-0.0"
        value == value.toLong().toDouble() && !value.toString().contains('E') -> "${value.toLong()}.0"
        else -> value.toString()
    }
    is Float -> elixirNumberText(value.toDouble())
    else -> value.toString()
}

/** Elixir comments run from `#` to end of line, so every line gets its own marker. */
internal fun elixirCommentText(text: String): String =
    text.trimEnd().lineSequence().joinToString("\n") { line ->
        when {
            line.isEmpty() -> "#"
            else -> "# $line"
        }
    }
