package lang.temper.docbuild

import lang.temper.common.toStringViaBuilder
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.LinkReferenceDefinition

/**
 * A Markdown link.
 *
 * This describes what fields are non-null and what
 * is contained in the range.
 *
 * | link syntax | linked | kind   | target |
 * | ----------- | ------ | ------ | ------ |
 * | \[B\]       | *null* | `[`    | B      |
 * | \[A\]\[B\]  | \[A\]  | `[`    | B      |
 * | \[A\]\(B\)  | \[A\]  | `(`    | B      |
 */
data class Link<Range>(
    /** The source range for the whole link. */
    val whole: Range,
    /** The source range for the linked content. */
    val linked: Range?,
    /** The kind of brackets surrounding the target. */
    val kind: Kind,
    /** The text for the link target, if any. */
    val target: String,
    /** Whether the link is locally defined. Always true for [Kind.Parenthetical]. */
    val locallyDefined: Boolean,
) {
    enum class Kind {
        /** Kind for a markdown link like `[...](...)`. */
        Parenthetical {
            override fun bracket(target: String): String =
                "($target)"
        },

        /** Kind for a markdown link like `[...][...]` or `[...]`. */
        SquareBracketed {
            override fun bracket(target: String): String =
                "[$target]"
        },

        ;

        abstract fun bracket(target: String): String
    }
}

fun Link<IntRange>.stringifyRanges(text: String): Link<String> = Link(
    whole = text.substring(whole),
    linked = linked?.let { text.substring(it) },
    kind = kind,
    target = target,
    locallyDefined = locallyDefined,
)

/**
 * Returns a map containing entries corresponding to Markdown link definitions like
 *
 *     [short name]: https://example.com/
 */
internal fun findLocallyDefinedLinkTargets(
    content: MarkdownContent,
): Map<String, IntRange> = buildMap {
    content.root.accept(
        object : AbstractVisitor() {
            override fun visit(linkReferenceDefinition: LinkReferenceDefinition) {
                val label = "[${linkReferenceDefinition.label}]"
                this@buildMap[label] = content.range(linkReferenceDefinition)
            }
        },
    )
}

/**
 * Looks for Markdown links, and produces a list bundling sub-trees.
 */
internal fun findLinks(markdownContent: MarkdownContent): List<Link<IntRange>> = buildList {
    markdownContent.root.accept(
        object : LinkUnifyingVisitor(markdownContent) {
            override fun visit(link: Link<IntRange>) {
                add(link)
            }
        },
    )
}

/**
 * Looks for Markdown links, and maybe rewrites them.
 *
 * Markdown like
 *
 *     [FOO]
 *
 * will be rewritten to
 *
 *     [*Foo description*](../link/to/foo)
 *
 * if [rewrite]\(someRange, "Foo"\) returns `Pair("*Foo description*", "(../link/to/foo)")`,
 * and
 *
 *     [explicit link text][FOO]
 *
 * would be rewritten to
 *
 *     [explicit link text](../link/to/foo)
 */
internal fun rewriteLinkTargets(
    markdownContent: MarkdownContent,
    /**
     * Given a range of characters for the target, and the target text (not including `[` or `]`)
     * returns:
     * - suggested link text or `null` for no suggestion,
     * - and the new target which should be surrounded with `[...]` for a symbolic name, or
     *   `(...)` for a URL.
     */
    rewrite: (IntRange, String) -> Pair<String?, String>,
): String {
    val content = markdownContent.fileContent
    val links = findLinks(markdownContent)
    val replacements = mutableListOf<Pair<IntRange, String>>()

    for (link in links) {
        if (link.kind == Link.Kind.SquareBracketed) {
            if (!link.locallyDefined) {
                val (suggestedText, target) = rewrite(link.whole, link.target)
                val linkedText = link.linked
                val replacementLinkText = when {
                    linkedText != null -> linkedText.let { "[${content.substring(it)}]" }
                    suggestedText != null -> "[$suggestedText]"
                    else -> link.target
                }

                check(
                    (target.startsWith('[') && target.endsWith(']')) ||
                        (target.startsWith('(') && target.endsWith(')')),
                ) { target }

                replacements.add(link.whole to "$replacementLinkText$target")
            }
        }
    }

    replacements.sortBy { it.first.first }
    for (i in 1 until replacements.size) {
        val (prevRange) = replacements[i - 1]
        val (replRange) = replacements[i]
        check(prevRange.last <= replRange.first) {
            // If links appear in link text,
            // 1. that's unexpected
            // 2. we would have to do a serious rewrite of this code to avoid clobbering
            "Overlapping ranges $prevRange vs $replRange: `${
                markdownContent.fileContent.substring(prevRange.first, prevRange.last + 1)
                    .replace("\n", "\\n")
            }` vs `${
                markdownContent.fileContent.substring(replRange.first, replRange.last + 1)
                    .replace("\n", "\\n")
            }`"
        }
    }

    return toStringViaBuilder { rewritten ->
        rewritten.append(content)
        // Do replacements with greater indices first so later replacements can assume stable ranges
        replacements.reverse()
        replacements.forEach { (range, replacementText) ->
            rewritten.replace(range.first, range.last + 1, replacementText)
        }
    }
}
