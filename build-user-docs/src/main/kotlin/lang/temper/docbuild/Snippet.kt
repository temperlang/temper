package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.isMarkdown
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePathSegment
import lang.temper.log.applyToSegments
import lang.temper.log.dirPath
import lang.temper.log.escapeMaybeProblematicPathSegment
import lang.temper.log.plus
import lang.temper.log.resolveFile

/**
 * A snippet is identified by a sequence of URL-encoded strings that correspond to a generated
 * file path.
 *
 * For example,
 *
 * | Aspect      | Representation                                                     |
 * | ----------- | ------------------------------------------------------------------ |
 * | Strings     | `["Boolean", "toString()"]`                                        |
 * | URL-encoded | `["Boolean", "toString%28%29"]`                                    |
 * | Path        | `build-user-docs/build/snippets/Boolean/toString%28%29/snippet.md` |
 */
internal data class SnippetId(
    val parts: List<String>,
    val extension: String,
) {
    /**
     * A snippet with an [id][Snippet.id] like [SnippetId]`(listOf("foo", "bar"), extension=".ext")`
     * corresponds to content at `build-user-docs/build/snippets/foo/bar.ext`.
     */
    val filePath: FilePath = snippetPathPrefix.plus(
        dirPath(parts)
            .applyToSegments(dir = FilePathSegment::escapeMaybeProblematicPathSegment)
            .resolveFile("$SNIPPET_FILE_BASENAME$extension"),
    )

    /**
     * A string like the path but excluding the leading path segments and the `/snippet` file name.
     * This is the form used to refer to the snippet from a markdown file.
     */
    fun shortCanonString(withExtension: Boolean) = FilePath(
        parts.mapIndexed { index, part ->
            FilePathSegment(
                if (!withExtension || index < parts.lastIndex) {
                    part
                } else {
                    "$part$extension"
                },
            )
        },
        isDir = false,
    ).join()

    override fun toString() = filePath.join()
}

/** How a snippet was derived. */
internal sealed class SnippetDerivation {
    internal abstract val extractor: SnippetExtractor
}

/**
 * The snippet was marked by delimiters in its source.
 */
internal data class ExtractedBy(
    override val extractor: SnippetExtractor,
) : SnippetDerivation()

/**
 * The snippet was derived from specially formatted content, processed,
 * and written back.
 */
internal data class ExtractedAndReplacedBack(
    override val extractor: SnippetExtractor,
    /**
     * The range of characters from which the snippet was extracted AND which should be replaced
     * with the snippet content.
     *
     * This allows snippets whose content is a decorated version of some range of content, to be
     * inserted back in place of the original, undecorated content.
     *
     * [Snippet.source] for a snippet derived in this way must be in [SkeletalDocsFiles] or a
     * [snippet ID path][SnippetId.filePath] so that we may generate a [SnippetInsertion] for it.
     */
    val replaceBackRange: IntRange,
    /**
     * The original content that was extracted.
     */
    val extracted: DocContent,
) : SnippetDerivation()

/**
 * A chunk of content extracted from a [source file][SourceFiles] that will eventually be inlined
 * into the documentation with holes.
 */
internal data class Snippet(
    val id: SnippetId,
    /** Markdown text describing the contents. */
    val shortTitle: String?,
    /** The source file from which the snippet was extracted. */
    val source: FilePath,
    /** A character offset into [source]'s content. */
    val sourceStartOffset: Int,
    /** The mime-type of [content]. */
    val mimeType: MimeType,
    /** The snippet content */
    val content: DocContent,
    /**
     * True when this snippet is used to create other snippets and not meant to end up under
     * `docs/for-users/`.
     */
    val isIntermediate: Boolean,
    val derivation: SnippetDerivation,
    val problems: List<String> = emptyList(),
) : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("type") { value("Snippet") }
        key("id") { value(id.shortCanonString(true)) }
        key("shortTitle") { value(shortTitle) }
        key("source") { value(source) }
        key("sourceStartOffset") { value(sourceStartOffset) }
        key("mimeType", isDefault = mimeType == MimeType.markdown) { value(mimeType) }
        key("content") { value(content) }
        key("isIntermediate", isDefault = !isIntermediate) { value(isIntermediate) }
        key("derivation") { value(derivation) }
        key("problems", isDefault = problems.isEmpty()) { value(problems) }
    }
}

/**
 * True when references to the snippet be resolved by linking internally within the containing
 * Markdown documentation.
 */
internal val Snippet.shouldInline get() = mimeType.isMarkdown

internal val snippetPathPrefix = dirPath(
    "build-user-docs",
    "build", // Gradle output directory
    "snippet",
)

internal const val SNIPPET_FILE_BASENAME = "snippet"
