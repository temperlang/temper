package lang.temper.docgen.parsers

import lang.temper.docgen.AddedContent
import lang.temper.docgen.Code
import lang.temper.docgen.Document
import lang.temper.docgen.Fragment
import lang.temper.docgen.Prose
import lang.temper.docgen.SupplantableFragment
import java.lang.Appendable
import java.lang.Integer.max

abstract class StringBackedDocFragment : Fragment {
    /**
     * Relocates this fragment as though [start] has changed size by [size]
     * So fragments to the left of [start] don't move, and fragments to the right of [start] are moved
     */
    abstract fun relocate(start: Int, size: Int)

    internal var document: StringBackedDocument? = null
}

abstract class RewritableStringBackedDocFragment : StringBackedDocFragment() {
    /**
     * An internal helper to help implement [ReplaceableFragment.replace]
     * @return the index and size to use to [relocate] the fragments with
     */
    internal abstract fun rewrite(document: StringBuilder, newContent: String): List<Pair<Int, Int>>
}

internal class StringBackedDocument(
    private val backingContent: StringBuilder,
    fragments: List<StringBackedDocFragment>,
) : Document<String, StringBackedDocFragment, LinkableCodeFragment> {

    constructor(
        initialContent: String,
        fragments: List<StringBackedDocFragment>,
    ) : this(StringBuilder(initialContent), fragments)

    override val first: LinkableCodeFragment?
        get() = codeFragments.firstOrNull()

    internal fun link() {
        codeFragments.mapIndexed { index, block ->
            if (index != 0) {
                block.previousCodeFragment = codeFragments[index - 1]
                codeFragments[index - 1].nextCodeFragment = block
            }
        }
        fragments.map { fragment -> fragment.document = this }
    }

    override val document: String
        get() = backingContent.toString()

    fun slice(location: IntRange): CharSequence {
        return backingContent.slice(location)
    }

    override fun supplant(location: SupplantableFragment, content: List<AddedContent>) {
        val replacement = content.joinToString("\n") {
            when (it) {
                is Prose -> {
                    it.content
                }
                is Code -> {
                    fun longestBacktick(input: String): Int {
                        var count = 0
                        var maximum = 0
                        input.forEach { char ->
                            if (char == '`') {
                                count += 1
                            } else {
                                count = 0
                            }
                            maximum = max(count, maximum)
                        }
                        return maximum
                    }
                    val backtickSize = longestBacktick(it.content)
                    val backtickString = if (backtickSize < MARKDOWN_DEFAULT_CODE_FENCE_SIZE) {
                        "```"
                    } else {
                        "`".repeat(backtickSize + 1)
                    }
                    """${backtickString}${it.language}
                        |${it.content}
                        |$backtickString
                    """.trimMargin()
                }
            }
        }

        val casted = location as RewritableStringBackedDocFragment
        val adjustments = casted.rewrite(backingContent, replacement)
        adjustments.forEach { adjustment -> fragments.forEach { it.relocate(adjustment.first, adjustment.second) } }
        fragments = fragments - casted
        val cast = location as? LinkableCodeFragment
        if (cast != null) {
            cast.previousCodeFragment?.nextCodeFragment = cast.nextCodeFragment
            cast.nextCodeFragment?.previousCodeFragment = cast.previousCodeFragment
        }
    }

    /**
     * String back document includes an ordering guarantee that the fragments occur in the same order the happened in
     * the underlying document
     */
    override var fragments: List<StringBackedDocFragment> = fragments
        private set

    override fun prepend(content: String) {
        backingContent.insert(0, "$content\n")
        // +1 for the new line
        fragments.forEach { it.relocate(0, content.length + 1) }
    }

    companion object {
        private const val MARKDOWN_DEFAULT_CODE_FENCE_SIZE = 3
    }

    override val codeFragments: List<LinkableCodeFragment>
        get() = fragments.filterIsInstance<LinkableCodeFragment>()

    override fun writeTo(appendable: Appendable) {
        appendable.append(backingContent)
    }
}
