package lang.temper.docbuild

import lang.temper.lexer.children
import lang.temper.lexer.forward
import org.commonmark.node.Heading
import org.commonmark.node.SoftLineBreak

/**
 * Finds suggested title text used to link to a snippet by looking for a uniquely high-precedence
 * header at the beginning.
 */
internal fun shortTitleFromMarkdownHeader(markdownText: String): String? {
    val content = MarkdownContent(markdownText)
    val root = content.root
    for (child in root.children()) {
        if (child is SoftLineBreak) { continue }
        if (isIgnorableHtmlContent(content.text(child))) {
            // Skip over HTML like `<!-- snippet: ... -->`
            continue
        }
        val level = (child as? Heading ?: break).level
        // See if there's an ATX_? node whose number is <= child's.
        for (laterSibling in child.next.forward()) {
            val laterLevel = ((laterSibling as? Heading) ?: continue).level
            if (laterLevel <= level) {
                return null
            }
        }
        return content.subText(child)
    }
    return null
}

internal fun isIgnorableHtmlContent(text: CharSequence) =
    ignorableHtml.matches(text)

private val ignorableHtml = Regex(
    "(?:[ \t\r\n]|<!--(?:[^-]|-(?!->))*-+>)*|<a name=\"[^\"]*\" class=\"$SNIPPET_ANCHOR_CLASSNAME\"></a>",
)
