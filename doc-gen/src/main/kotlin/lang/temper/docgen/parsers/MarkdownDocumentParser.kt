package lang.temper.docgen.parsers

import lang.temper.common.removeMatching
import lang.temper.docgen.BlockQuoteFragment
import lang.temper.docgen.CodeFragment
import lang.temper.docgen.Document
import lang.temper.docgen.DocumentParser
import lang.temper.docgen.DocumentationCodeLocation
import lang.temper.docgen.LinkFragment
import lang.temper.docgen.SupplantableFragment
import lang.temper.lexer.LINK_FOUND_DEFINITION_TITLE
import lang.temper.lexer.LINK_MISSING_DEFINITION_TITLE
import lang.temper.lexer.MarkdownContent
import lang.temper.lexer.children
import lang.temper.log.Position
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.Text
import java.io.File

class MarkdownDocumentParser : DocumentParser<String, StringBackedDocFragment, LinkableCodeFragment> {

    override fun parse(input: String): Document<String, StringBackedDocFragment, LinkableCodeFragment> {
        val content = MarkdownContent(input)
        val fragments = process(MarkdownContent(input), content.root)
        val result = StringBackedDocument(input, fragments)
        result.link()
        return result
    }

    private fun process(
        content: MarkdownContent,
        node: Node,
        partialReferenceLinks: MutableList<ReferenceLinkFirstHalfFragment> = mutableListOf(),
    ): List<StringBackedDocFragment> {
        var result = emptyList<StringBackedDocFragment>()
        fun recurse() = node.children().asSequence().flatMap { process(content, it, partialReferenceLinks) }.toList()
        fun String.trimEndLine() = trimEnd('\r', '\n')
        fun position(): Position {
            val range = content.range(node)
            return Position(DocumentationCodeLocation, range.first, range.last + 1)
        }
        fun rangePlus(): IntRange {
            val range = content.range(node)
            // TODO Replicating earlier logic, though I suspect the logic is wrong.
            return range.first..range.last + 1
        }
        // Visitors should be faster than a long `when`, but they're void, so have to assign result.
        node.accept(object : AbstractVisitor() {
            override fun visit(blockQuote: org.commonmark.node.BlockQuote) {
                val paragraph = node.children().asSequence().firstOrNull { kid ->
                    // the blockquote can nest any arbitrary content inside the paragraph.
                    // a single line block quote with text may be what we want to replace
                    // TODO I replicated earlier logic, but I don't know why we want this.
                    kid is Paragraph && kid.firstChild != null && kid.firstChild == kid.lastChild &&
                        kid.firstChild is Text
                }
                result = if (paragraph != null) {
                    val text = (paragraph.firstChild as Text).literal
                    recurse() + BlockQuote(text, rangePlus())
                } else {
                    recurse()
                }
            }

            override fun visit(code: Code) {
                result = listOf(LinkableCodeFragment(code.literal, false, position()))
            }

            override fun visit(fencedCodeBlock: FencedCodeBlock) {
                val isTemperCode = when (fencedCodeBlock.info.isEmpty()) {
                    true -> false
                    false -> {
                        when (fencedCodeBlock.info.split(" ").first().trim()) {
                            "temper" -> true
                            else -> return // leave result empty
                        }
                    }
                }
                val code = fencedCodeBlock.literal.trimEndLine()
                result = listOf(LinkableCodeFragment(code, isTemperCode, position()))
            }

            override fun visit(indentedCodeBlock: IndentedCodeBlock) {
                val code = indentedCodeBlock.literal.trimEndLine()
                result = listOf(LinkableCodeFragment(code, false, position()))
            }

            override fun visit(link: Link) {
                when (link.title) {
                    LINK_FOUND_DEFINITION_TITLE, LINK_MISSING_DEFINITION_TITLE -> partialReferenceLinks.add(
                        ReferenceLinkFirstHalfFragment(
                            sourceText = content.text(node),
                            loc = rangePlus(),
                            label = link.destination,
                            displayText = content.subText(node),
                        ),
                    )

                    else -> result = listOf(
                        InlineLinkFragment(
                            sourceText = content.text(node),
                            loc = rangePlus(),
                            linkText = content.subText(node),
                            linkTarget = link.destination,
                            altText = link.title ?: "",
                        ),
                    )
                }
            }

            override fun visit(linkReferenceDefinition: LinkReferenceDefinition) {
                val label = linkReferenceDefinition.label
                // This ought to handle the weird case of having two links that link to the same place
                val existingHalves = partialReferenceLinks.removeMatching { partial -> partial.label == label }
                val secondHalfFragment = ReferenceLinkSecondHalfFragment(
                    sourceText = content.text(node),
                    loc = rangePlus(),
                    linkTarget = linkReferenceDefinition.destination,
                    altText = linkReferenceDefinition.title ?: "",
                    label = label,
                )
                result = existingHalves.map { firstHalfFragment ->
                    ReferenceLinkAmalgam(firstHalfFragment, secondHalfFragment)
                }
            }

            override fun visitChildren(parent: Node) {
                // Called by default in AbstractVisitor, so this our `else`.
                result = recurse()
            }
        })
        return result
    }

    override fun canParse(file: File): Boolean {
        return file.extension.lowercase() == "md"
    }
}

// TODO: This could probably be collapsed into [MultiLocationFragment] but I'm not sure that [MultiLocationFragment]
// is a good way to represent those structures
abstract class SingleLocationFragment(private var location: IntRange) : RewritableStringBackedDocFragment() {
    override fun relocate(start: Int, size: Int) {
        if (location.first >= start) {
            location = IntRange(location.first + size, location.last + size)
        } else if (location.last > start) {
            location = IntRange(location.first, location.last + size)
        }
    }

    override fun rewrite(document: StringBuilder, newContent: String): List<Pair<Int, Int>> {
        // compute the current indent and put it into newContent.
        // Callers can't reasonably know what the indent is to include it in newContent because
        // the fragment explicitly isn't aware indentation is possible
        // the '>' is for those nested in block quotes
        val indent = document.take(location.first).takeLastWhile { it == ' ' || it == '\t' || it == '>' }

        val indented = newContent.replace("\n", "\n$indent")

        val delta = indented.length - (location.last - location.first)
        document.replace(location.first, location.last, indented)
        return listOf(Pair(location.first, delta))
    }
}

abstract class MultiLocationFragment(private var locations: List<IntRange>) : StringBackedDocFragment() {
    override fun relocate(start: Int, size: Int) {
        locations = locations.flatMap { location ->
            if (location.first > start) {
                listOf(IntRange(location.first + size, location.last + size))
            } else if (location.last > start) {
                listOf(IntRange(location.first, location.last + size))
            } else {
                emptyList()
            }
        }
    }
}

class ReferenceLinkFirstHalfFragment(
    sourceText: String,
    internal val loc: IntRange,
    val label: String,
    displayText: String,
) : SingleLocationFragment(loc) {
    override var sourceText: String = sourceText
        internal set

    var displayText: String = displayText
        internal set
}

class ReferenceLinkSecondHalfFragment(
    sourceText: String,
    internal val loc: IntRange,
    linkTarget: String,
    altText: String,
    val label: String,
) : SingleLocationFragment(loc) {
    override var sourceText: String = sourceText
        internal set

    var linkTarget: String = linkTarget
        internal set

    var altText: String = altText
        internal set
}

class ReferenceLinkAmalgam(
    private val first: ReferenceLinkFirstHalfFragment,
    private val second: ReferenceLinkSecondHalfFragment,
) : MultiLocationFragment(listOf(first.loc, second.loc)), LinkFragment {
    override val sourceText: String
        // The second newline makes markdown format it correctly
        get() = "${first.sourceText}\n\n${second.sourceText}"
    override val linkText: String
        get() = first.displayText
    override val linkTarget: String
        get() = second.linkTarget
    override val altText: String
        get() = second.altText
}

class InlineLinkFragment(
    sourceText: String,
    loc: IntRange,
    linkText: String,
    linkTarget: String,
    altText: String,
) : SingleLocationFragment(loc), LinkFragment, SupplantableFragment {

    override var sourceText: String = sourceText
        internal set
    override var linkText: String = linkText
        internal set
    override var linkTarget: String = linkTarget
        internal set
    override var altText: String = altText
        internal set

    override fun rewrite(document: StringBuilder, newContent: String): List<Pair<Int, Int>> {
        val matchResult = regex.matchEntire(newContent)
        if (matchResult == null) {
            // it isn't a link blank everything out
            linkText = ""
            linkTarget = ""
            altText = ""
        } else {
            val groups = matchResult.groupValues
            linkText = groups[1]
            linkTarget = groups[2]
            val maybeAltText = groups.getOrNull(ALT_TEXT_CAPTURE_INDEX)
            if (maybeAltText != null) altText = maybeAltText
        }
        sourceText = newContent
        return super.rewrite(document, newContent)
    }

    companion object {
        const val ALT_TEXT_CAPTURE_INDEX = 3

        // Regex to parse an inline style link
        val regex = """\[([\w\s-]+)]\((/|https?://|#[\w./?=#-]+)\s?(".+")?\)""".toRegex()
    }
}

class LinkableCodeFragment(
    override val sourceText: String,
    override val isTemperCode: Boolean,
    override val position: Position,
) :
    CodeFragment<LinkableCodeFragment>, SingleLocationFragment(IntRange(position.left, position.right)) {

    override var previousCodeFragment: LinkableCodeFragment? = null
        internal set

    override var nextCodeFragment: LinkableCodeFragment? = null
        internal set
}

class BlockQuote(sourceText: String, location: IntRange) : BlockQuoteFragment, SingleLocationFragment(location) {
    override val sourceText: CharSequence = sourceText
}
