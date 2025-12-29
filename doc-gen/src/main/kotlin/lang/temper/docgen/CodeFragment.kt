package lang.temper.docgen

import lang.temper.log.CodeLocation
import lang.temper.log.Position
import lang.temper.name.LanguageLabel

/**
 * Represents part of a document. That part of the document may not be contiguous text.
 *
 * For example a reference style Markdown link is composed of two parts that may not be contiguous.
 */
interface Fragment {

    /**
     * Does not contain any wrapping in the source formatting.
     * If the fragment contains multiple parts it may contain interspersed information see [Fragment] subtypes for
     * parsing assistance
     */
    val sourceText: CharSequence
}

interface SimpleCodeFragment : Fragment {

    /**
     * True if we know it is temper code, false if it is unclear what kind of code it is.
     * There would be no fragment for code that is definitely not temper.
     */
    val isTemperCode: Boolean
}

interface LinkFragment : SupplantableFragment {
    val linkText: String
    val linkTarget: String
    val altText: String
}

interface BlockQuoteFragment : SupplantableFragment

/**
 * A singular code sample. Can be as small as a single identifier or as a large as needed to get the point across
 */
interface CodeFragment<out TFragment : CodeFragment<TFragment>> : SimpleCodeFragment, SupplantableFragment {
    val previousCodeFragment: TFragment?
    val nextCodeFragment: TFragment?
    val position: Position
}

object DocumentationCodeLocation : CodeLocation {
    override val diagnostic = "Documentation"
}

/**
 * Represents a single piece of documentation.
 *
 * @sample codeFragments Collects all of the [CodeFragment] instances in a single document etc so that they can be
 * compiled in a shared context if needed.
 */
interface Document<TDoc, TFragment : Fragment, out TCodeFragment : CodeFragment<TCodeFragment>> {
    val first: TCodeFragment?
    val codeFragments: List<TCodeFragment>

    /**
     * All fragments that are part of this document. No specific ordering guarantees but it is stable.
     */
    val fragments: List<TFragment>

    val document: TDoc

    /**
     * Removes the entirety of [location], both text and container, replacing it with [content]
     */
    fun supplant(location: SupplantableFragment, content: List<AddedContent>)

    /**
     * Add [content] to the beginningish of the document. This should be metadata and not anything that the exact
     * location of which in the final document is seen by the user.
     */
    fun prepend(content: String)

    fun writeTo(appendable: Appendable)
}

interface SupplantableFragment : Fragment

/**
 * The contravariant to [Fragment]s covariant. Represents something that should be put into a document rather than
 * making up a document already
 */
sealed interface AddedContent { // TODO there probably need to be other kinds for spacing and/or links
    val content: String
}

data class Code(override val content: String, val language: LanguageLabel?) : AddedContent

data class Prose(override val content: String) : AddedContent

/*data class Link(val destination: String, val text: String, val title: String, val styling: String): AddedContent {
    override val content: String
        get() = "<a href=$destination class=\"$styling\" title=\"$title\">$text</a>"
}*/
