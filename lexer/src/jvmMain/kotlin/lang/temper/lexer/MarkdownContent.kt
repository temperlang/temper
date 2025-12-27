package lang.temper.lexer

import lang.temper.log.FilePositions
import lang.temper.log.UnknownCodeLocation
import org.commonmark.internal.InlineParserImpl
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.Node
import org.commonmark.node.SourceSpan
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.InlineParserContext
import org.commonmark.parser.Parser
import java.util.Scanner
import kotlin.NoSuchElementException

open class MarkdownContent(
    val fileContent: String,
) {
    val root: Node = makeMarkdownParser().parse(fileContent)
    private val filePositions = FilePositions.fromSource(UnknownCodeLocation, fileContent)

    // Other fields are entirely dependent on fileContent, so base equality on that.
    override fun equals(other: Any?) = other is MarkdownContent && other.fileContent == fileContent
    override fun hashCode() = fileContent.hashCode()

    fun endIndex(node: Node): Int = node.sourceSpans.lastOrNull()?.let {
        filePositions.offsetAtFilePosition(it.startPos())!! + it.length
        // See fallback discussion on `startIndex`.
    } ?: endIndex(node.previous)

    fun range(node: Node) = startIndex(node) until endIndex(node)

    fun range(sourceSpan: SourceSpan) = filePositions.offsetAtFilePosition(sourceSpan.startPos())!!.let { offset ->
        offset until offset + sourceSpan.length
    }

    fun startIndex(node: Node) = node.sourceSpans.firstOrNull()?.toIndex(filePositions)
        // Some things like SoftLineBreak don't carry spans, so take the end of the previous.
        // Let below throw if missing. If we get in this spot, we're in trouble.
        ?: endIndex(node.previous)

    fun subRange(node: Node) = when (node.firstChild) {
        null -> startIndex(node).let { it until it }
        else -> startIndex(node.firstChild) until endIndex(node.lastChild)
    }

    fun subText(node: Node) = node.firstChild?.let { fileContent.substring(subRange(node)) } ?: ""

    fun text(node: Node) = fileContent.substring(range(node))

    fun text(sourceSpan: SourceSpan) = fileContent.subSequence(range(sourceSpan))

    /**
     * Scan for simple name-value metadata pairs in the yaml style.
     * Doesn't do actual yaml processing.
     *
     * TODO Use yaml metadata extension for commonmark-java?
     */
    fun simpleMeta(): Map<String, String> {
        return buildMap result@{
            val scanner = Scanner(fileContent)
            val ruler = Regex("^-{3,}$")
            if (!(scanner.hasNextLine() && scanner.nextLine().matches(ruler))) {
                return@result
            }
            val pair = Regex("^([-\\w]+):\\s*(.*?)\\s*$")
            lines@while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                if (line.matches(ruler)) {
                    break@lines
                }
                pair.matchEntire(line)?.let { match ->
                    put(match.groups[1]!!.value, match.groups[2]!!.value)
                }
            }
        }
    }
}

fun Node.children() = firstChild.forward()

fun Node?.forward() = object : Iterator<Node> {
    private var node = this@forward
    override fun hasNext() = node != null
    override fun next(): Node {
        val result = node ?: throw NoSuchElementException()
        node = result.next
        return result
    }
}

// We don't easily get the link kind in commonmark-java, but what matters most is if it's defined in the document.
// And if we don't resolve links, we can abuse the title field to track if it was defined or not.
const val LINK_FOUND_DEFINITION_TITLE = "!"
const val LINK_MISSING_DEFINITION_TITLE = "?"

private fun makeMarkdownParser() =
    Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).inlineParserFactory { context ->
        InlineParserImpl(
            object : InlineParserContext {
                override fun getCustomDelimiterProcessors() = context.customDelimiterProcessors
                override fun getLinkReferenceDefinition(label: String): LinkReferenceDefinition {
                    // Keep all links so we can deduce after. And keep them fairly raw because we process differently.
                    val def = context.getLinkReferenceDefinition(label)
                    return LinkReferenceDefinition(
                        label,
                        label,
                        when (def) {
                            null -> LINK_MISSING_DEFINITION_TITLE
                            else -> LINK_FOUND_DEFINITION_TITLE
                        },
                    )
                }
            },
        )
    }.build()
