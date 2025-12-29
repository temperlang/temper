package lang.temper.docgen.parsers

import it.skrape.core.htmlDocument
import it.skrape.selects.Doc
import it.skrape.selects.DocElement
import it.skrape.selects.html5.a
import it.skrape.selects.html5.blockquote
import it.skrape.selects.html5.code
import it.skrape.selects.html5.head
import lang.temper.docgen.AddedContent
import lang.temper.docgen.BlockQuoteFragment
import lang.temper.docgen.Code
import lang.temper.docgen.CodeFragment
import lang.temper.docgen.Document
import lang.temper.docgen.DocumentParser
import lang.temper.docgen.DocumentationCodeLocation
import lang.temper.docgen.Fragment
import lang.temper.docgen.LinkFragment
import lang.temper.docgen.Prose
import lang.temper.docgen.SupplantableFragment
import lang.temper.log.Position
import java.io.File
import java.lang.Appendable

class HtmlDocumentParser : DocumentParser<Doc, Fragment, NodeBackedCodeFragment> {

    override fun parse(input: String): Document<Doc, Fragment, NodeBackedCodeFragment> {
        val doc = htmlDocument(input) {
            // By default, skrape expects all parse attempts to return something this relaxes that constraint
            relaxed = true
            this
        }

        val codeFragments = doc.code {
            findAll { this }
        }.flatMap { element ->
            val classes = element.classNames
            // This seems like it could have terrible performance characteristics, hoping it isn't too bad
            val start = input.indexOf(element.text)
            val end = start + element.text.length
            val position = Position(DocumentationCodeLocation, start, end)
            if (classes.contains("language-temper")) {
                listOf(NodeBackedCodeFragment(element, true, position))
            } else if (classes.any { clazz -> clazz.startsWith("language-") }) {
                emptyList()
            } else {
                listOf(NodeBackedCodeFragment(element, false, position))
            }
        }

        val quoteFragments = doc.blockquote { findAll { this } }.flatMap { element ->
            if (element.children.isEmpty()) {
                listOf(NodeBackedQuote(element))
            } else {
                emptyList()
            }
        }

        val linkFragments = doc.a { findAll { this } }.flatMap {
            listOf(NodeBackedLinkFragment(it))
        }

        val result = TreeBackedDocument(doc, codeFragments + quoteFragments + linkFragments)
        result.link()
        return result
    }

    override fun canParse(file: File): Boolean {
        return file.extension.lowercase() == "htm" || file.extension.lowercase() == "html"
    }
}

class TreeBackedDocument(
    override val document: Doc,
    fragments: List<Fragment>,
) : Document<Doc, Fragment, NodeBackedCodeFragment> {
    override val first: NodeBackedCodeFragment?
        get() = codeFragments.firstOrNull()

    internal fun link() {
        codeFragments.mapIndexed { index, block ->
            if (index != 0) {
                block.previousCodeFragment = codeFragments[index - 1]
                codeFragments[index - 1].nextCodeFragment = block
            }
        }
    }

    override fun supplant(location: SupplantableFragment, content: List<AddedContent>) {
        fragments = fragments - location
        val cast = location as? NodeBackedCodeFragment
        if (cast != null) {
            cast.previousCodeFragment?.nextCodeFragment = cast.nextCodeFragment
            cast.nextCodeFragment?.previousCodeFragment = cast.previousCodeFragment
        }
        val replacement = content.joinToString("\n") {
            when (it) {
                is Prose -> {
                    it.content
                }
                is Code -> {
                    val langChunk = if (it.language != null) " class=\"language-${it.language}\"" else ""
                    "<code$langChunk>${it.content}</code>"
                }
            }
        }
        (location as NodeBackedFragment).element.element.after(replacement)
        (location as NodeBackedFragment).element.element.remove()
    }

    override var fragments: List<Fragment> = fragments
        private set

    override fun prepend(content: String) {
        // data goes into the head
        document.head { findAll { this } }.first().element.prepend(content)
    }

    override val codeFragments: List<NodeBackedCodeFragment>
        get() = fragments.filterIsInstance<NodeBackedCodeFragment>()

    override fun writeTo(appendable: Appendable) {
        appendable.append(document.html)
    }
}

class NodeBackedCodeFragment(
    override val element: DocElement,
    override val isTemperCode: Boolean,
    override val position: Position,
) :
    CodeFragment<NodeBackedCodeFragment>, NodeBackedFragment() {

    override var previousCodeFragment: NodeBackedCodeFragment? = null
        internal set

    override var nextCodeFragment: NodeBackedCodeFragment? = null
        internal set

    override val sourceText: String
        get() = element.element.ownText()
}

internal class NodeBackedQuote(override val element: DocElement) :
    BlockQuoteFragment, SupplantableFragment, NodeBackedFragment() {
    override val sourceText: CharSequence
        get() = element.element.ownText()
}

abstract class NodeBackedFragment : Fragment {
    internal abstract val element: DocElement
}

internal class NodeBackedLinkFragment(override val element: DocElement) : NodeBackedFragment(), LinkFragment {
    override val sourceText: CharSequence
        get() = element.element.ownText()
    override val linkText: String
        get() = element.ownText
    override val linkTarget: String
        get() = element.attributes.getOrDefault("href", "")
    override val altText: String
        get() = element.attributes.getOrDefault("title", "")
}
