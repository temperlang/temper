package lang.temper.be.java

import lang.temper.be.tmpl.Autodoc
import lang.temper.be.tmpl.FnAutodoc
import lang.temper.be.tmpl.TmpL
import lang.temper.common.Escape
import lang.temper.common.Escaper
import lang.temper.common.FixedEscape
import lang.temper.common.IdentityEscape
import lang.temper.common.MAX_ASCII
import lang.temper.common.Utf16Escaper
import lang.temper.common.withCapturingConsole
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.name.OutName
import lang.temper.value.impliedThisSymbol

// Javadoc requires escaping some characters that can appear in Markdown
// TODO: proper markdown to Javadoc micro language that also does this for prose.
private object JavadocEscaper : Utf16Escaper<Unit>(
    quote = null,
    asciiEscapes = buildList {
        repeat(MAX_ASCII + 1) {
            add(IdentityEscape)
        }
        // '*' is escaped so that Java will not find comment
        // closers like "*/" inside the comment content.

        // '\\' is escaped so that Java's pre-lexical Unicode
        // escape pass will not treat "\\u002a" as an asterisk.

        // '@' is escaped because it is a meta-character in javadoc.

        // '{' and '}' are javadoc meta-characters and are called out
        // in `javadoc`'s docs as needing to be escaped when used in
        // a URL inside {@link ...}.  Escaping those future-proofs
        // us against turning Markdown link parts into javadoc links.
        for (c in "\u0000*\\@{}") {
            val code = c.code
            this[code] = FixedEscape("&#$code;")
        }
        // other HTML metacharacters for consistency.
        this['<'.code] = FixedEscape("&lt;")
        this['>'.code] = FixedEscape("&gt;")
        this['&'.code] = FixedEscape("&amp;")
    }.toTypedArray(),
) {
    override fun createState() = Unit
    override fun withExtraEscapes(
        extras: Map<Char, Escape>,
        quote: Char?,
    ): Escaper = error("Not supported")

    override fun nonAsciiEscape(codePoint: Int): Escape = IdentityEscape
}

/**
 * Turns doc string metadata into a javadoc comment.
 * This produce comment lines like
 *
 *     * Line 1 of function docs
 *     *
 *     * @param x
 *     *   any docs for param x
 *     * @param y
 *     *   first line of docs for param y
 *     *   subsequent line.
 */
internal fun javadoc(
    autodoc: FnAutodoc?,
    names: Map<TmpL.Id, OutName>,
): Java.JavadocComment? {
    if (autodoc?.functionDoc == null && autodoc?.parameters?.all { (_, doc: Autodoc?) -> doc == null } != false) {
        return null
    }

    val (_, javadocContent) = withCapturingConsole { javadocConsole ->
        // We can use console.group to indent inside a param.
        autodoc.functionDoc?.full?.let {
            javadocConsole.log(JavadocEscaper.escape(it))
        }
        for ((formal, paramDoc) in autodoc.parameters) {
            if (paramDoc?.full.isNullOrBlank()) {
                // Javadoc issues errors on @param entries with no text.
                // This also skips over almost all `this` parameters.
                continue
            }
            val isThis = formal.metadata.any { it.key.symbol == impliedThisSymbol }

            val nameText = if (isThis) {
                "this"
            } else {
                names[formal.name]?.outputNameText ?: continue
            }

            javadocConsole.log("")
            javadocConsole.group("@param ${JavadocEscaper.escape(nameText)}") {
                javadocConsole.log(paramDoc.full)
            }
        }
    }

    val commentLines = mutableListOf<OutputToken>()
    javadocContent.trimEnd().lines().mapTo(commentLines) {
        val line = it.trimEnd()
        val prefixed = if (line.isEmpty()) " *" else " * $line"
        OutputToken(prefixed, OutputTokenType.Comment)
    }

    return Java.JavadocComment(autodoc.pos.leftEdge, commentLines)
}

internal fun javadoc(autodoc: Autodoc?) = javadoc(
    autodoc?.let {
        FnAutodoc(autodoc.pos, autodoc, emptyList())
    },
    emptyMap(),
)
