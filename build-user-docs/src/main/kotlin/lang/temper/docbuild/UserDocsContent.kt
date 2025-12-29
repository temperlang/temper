package lang.temper.docbuild

import lang.temper.common.Console
import lang.temper.common.LeftOrRight
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.json.JsonBoolean
import lang.temper.common.splitAfter
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.subListToEnd
import lang.temper.common.toStringViaBuilder
import lang.temper.common.urlEscape
import lang.temper.fs.resolve
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePath.Companion.joinPathTo
import lang.temper.log.NullTextOutput
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Node
import java.io.IOException
import java.nio.file.Files
import kotlin.streams.toList

internal const val PROJECT_GITHUB_URL = "https://github.com/temperlang/temper"

/** That which may be converted to a chunk of Markdown text. */
internal interface MarkdownConvertible {
    fun toMarkdown(): MarkdownContent {
        val c = ProblemTracker(Console(NullTextOutput))
        return MarkdownOutput().run {
            appendMarkdownContent(this, c)
            markdownContent
        }
    }

    fun appendMarkdownContent(
        out: MarkdownOutput,
        c: ProblemTracker,
    )
}

/** That which can be concatenated to produce a chunk of Markdown */
internal sealed interface MdChunk : Structured, MarkdownConvertible

/** That which can be produced from a chunk of reverse-engineered Markdown */
internal sealed interface RevMdChunk : Structured, MarkdownConvertible

/**
 * A tree structure that is very close to the generated Markdown
 * but which also reflects the nesting structure of snippets and
 * which includes transforming nodes that fix indentation and
 * header levels so that Markdown from snippets fits well into the
 * Markdown into which it's inserted.
 */
internal sealed class Nested : MarkdownConvertible, MdChunk {

    abstract val children: List<Nested>

    /** Records the position of something that couldn't be converted to valid markdown. */
    data class Invalid(val errorMessage: String) : Nested() {
        override val children: List<Nested> get() = emptyList()

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            c.error(errorMessage)
        }

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("errorMessage") { value(errorMessage) }
        }
    }

    /** A chunk of literal Markdown text. */
    internal data class Literal(val markdownText: String) : MdChunk, RevMdChunk {
        override fun toString(): String =
            "(Literal `${
                markdownText
                    .replace("\\", "\\\\\\\\")
                    .replace(crLfOrLfPattern, "\\\\n")
                    .replace("`", "\\\\`")
            }`)"

        override fun destructure(structureSink: StructureSink) = structureSink.value(markdownText)

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            out.append(markdownText)
        }
    }

    /**
     * If a snippet insertion appears inside a list, then it's lines
     * need to be indented.
     *
     * ```markdown
     * What's better than grilled cheese?
     *
     * - <INDENTED BY 2 SPACES>
     * - Also,
     *   - Hawaiian pizza,
     *   - <INDENTED BY 4 SPACES>
     * ```
     */
    data class IndentedRegion(
        /** A prefix string that should consist only of spaces and/or tab characters. */
        val indentation: String,
        /** The chunk whose lines should be indented. */
        val indented: Nested,
        /** True iff the first line should be indented. */
        val indentFirstLine: Boolean = false,
    ) : Nested() {
        override val children: List<Nested> = listOf(indented)

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("IndentedRegion") }
            key("indented") { value(indented) }
            key("indentFirstLine", isDefault = !indentFirstLine) { value(indentFirstLine) }
        }

        override fun appendMarkdownContent(
            out: MarkdownOutput,
            c: ProblemTracker,
        ) {
            val pastLastNonSpaceInIndent = indentation.indexOfLast { !isMarkdownSpaceChar(it) } + 1
            out.postProcessChunk(
                { indented.appendMarkdownContent(this, c) },
            ) {
                val unindentedContent = it.fileContent

                toStringViaBuilder { indented ->
                    for ((i, line) in unindentedContent.splitAfter(crLfOrLfPattern).withIndex()) {
                        if (indentFirstLine || i != 0) {
                            val hasNonSpaces = line.any { c -> !isMarkdownSpaceChar(c) }
                            val indentPrefixLen = if (hasNonSpaces) {
                                indentation.length
                            } else {
                                // If the line is blank, only emit up to the last non-space
                                // character.
                                // So if the indentation is a block quote like "> > "
                                // we output "> >" before blank lines.
                                // This allows the block quote to extend across paragraph
                                // boundaries.
                                pastLastNonSpaceInIndent
                            }
                            indented.append(indentation, 0, indentPrefixLen)
                        }
                        indented.append(line)
                    }
                }
            }
        }
    }

    /**
     * Strip any over-arching heading from a chunk of markdown.
     *
     * For example, if [content] starts with a header that has a
     * lower level (is more important than) any other header, then
     * we remove it.
     *
     * See also [removeOverArchingHeading]
     */
    data class StripHeading(
        val content: Nested,
    ) : Nested() {
        override val children: List<Nested> = listOf(content)

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("StripHeading") }
            key("content") { value(content) }
        }

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            out.postProcessChunk(
                { content.appendMarkdownContent(this, c) },
            ) { withoutHeading ->
                removeOverArchingHeading(withoutHeading)
            }
        }
    }

    data class HeadingAdjustment(
        val contextLevel: Int,
        val content: Concatenation,
    ) : Nested() {
        override val children: List<Nested> = listOf(content)

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("HeadingAdjustment") }
            key("contextLevel") { value(contextLevel) }
            key("content") { value(content) }
        }

        override fun appendMarkdownContent(
            out: MarkdownOutput,
            c: ProblemTracker,
        ) {
            out.postProcessChunk(
                { content.appendMarkdownContent(this, c) },
            ) { unadjusted ->
                adjustHeadingLevels(unadjusted, contextLevel)
            }
        }
    }

    data class SnippetAnchor(
        val anchorName: String,
    ) : Nested() {
        override val children: List<Nested> = listOf()

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("SnippetAnchor") }
            key("anchorName") { value(anchorName) }
        }

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            // <a name> is allowed but <span id> is not per
            // https://github.com/gjtorikian/html-pipeline/blob
            // /4f1aab069b982e7453be84d4cad60c9dbfa2f4f6
            // /lib/html/pipeline/sanitization_filter.rb#L43-L70
            out.breakBlock(preserveIndentation = false)
            out.append("<a name=\"")
            out.append(MarkdownEscape.htmlCompatibleEscape(anchorName))
            out.append("\" class=\"$SNIPPET_ANCHOR_CLASSNAME\"></a>\n")
            out.breakBlock()
        }

        companion object {
            /** Matches the open tag emitted by [appendMarkdownContent] */
            internal val pattern =
                Regex("""^<a\s+name="[^"]*"\s+class="$SNIPPET_ANCHOR_CLASSNAME">$""")
        }
    }

    class Concatenation(initialChunks: List<MdChunk> = emptyList()) : Nested() {
        internal val chunks = mutableListOf<MdChunk>()
        init { chunks.addAll(initialChunks) }

        override val children: List<Nested> get() = chunks.mapNotNull { it as? Nested }

        override fun destructure(structureSink: StructureSink) = structureSink.arr {
            chunks.forEach { value(it) }
        }

        override fun appendMarkdownContent(
            out: MarkdownOutput,
            c: ProblemTracker,
        ) {
            chunks.forEach { it.appendMarkdownContent(out, c) }
        }

        override fun toString() = concatenationToString(chunks)
    }

    @ConsistentCopyVisibility
    data class SourcedMarkdown internal constructor(
        val relFilePath: FilePath,
        internal val loc: SnippetInsertionLocation,
        val content: Concatenation,
        internal val inlinedSnippet: Snippet?,
    ) : MdChunk {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("SourcedMarkdown") }
            key("relFilePath") { value(relFilePath) }
            val id = (loc as? NestedInsertion)?.snippetId
            key("inlinedSnippetId", isDefault = id == null) {
                value(id)
            }
            key("content") { value(content) }
        }

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            c.console.group("Processing $loc") {
                content.appendMarkdownContent(out, c)
            }
        }

        override fun toString(): String = "(SourcedMarkdown loc=$loc content=$content)"
    }

    data class Link(
        val linked: Nested,
        val target: String,
        val originalCombinedText: String?,
    ) : Nested() {
        init {
            require(
                (target.startsWith('[') && target.endsWith(']')) ||
                    (target.startsWith('(') && target.endsWith(')')),
            ) {
                "linked=$linked, target=$target"
            }
        }

        internal constructor(
            linked: String,
            target: String,
            originalCombinedText: String?,
        ) : this(
            linked = Unit.let {
                val blocked = Concatenation()
                blocked.chunks.add(Literal(linked))
                blocked
            },
            target = target,
            originalCombinedText = originalCombinedText,
        )

        override val children: List<Nested> get() = listOf(linked)

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("Link") }
            key("linked") { value(linked) }
            key("target") { value(target) }
            key("originalCombinedText", isDefault = originalCombinedText == null) {
                value(originalCombinedText)
            }
        }
        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            out.append('[')
            linked.appendMarkdownContent(out, c)
            out.append(']')
            out.append(target)
        }
    }

    data class MarkerComment(
        val commentText: String,
    ) : MdChunk, RevMdChunk {
        // May be set to explicitly control the count of blank lines before
        // and after.  If not set, then 1.
        var blankLineCountBefore: Int = 1
        var blankLineCountAfter: Int = 1

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type", Hints.u) { value("MarkerComment") }
            key("commentText", Hints.n) { value(commentText) }
        }

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            out.breakBlock(preserveIndentation = false, lineCount = blankLineCountBefore)
            out.append(commentText)
            out.breakBlock(preserveIndentation = true, lineCount = blankLineCountAfter)
        }

        val isEndMarker: Boolean
            get() {
                val pos = commentText.indexOf("snippet")
                // Look for /snippet instead of snippet
                return pos > 0 && commentText[pos - 1] == '/'
            }
    }
}

internal sealed class Reversed : MarkdownConvertible, RevMdChunk {
    abstract val children: List<Reversed>

    class Concatenation(initialChunks: List<RevMdChunk> = emptyList()) : Reversed() {
        val chunks = mutableListOf<RevMdChunk>()
        init { chunks.addAll(initialChunks) }

        override val children: List<Reversed> get() = chunks.mapNotNull { it as? Reversed }

        override fun destructure(structureSink: StructureSink) = structureSink.arr {
            chunks.forEach { value(it) }
        }

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            chunks.forEach { it.appendMarkdownContent(out, c) }
        }

        override fun toString() = concatenationToString(chunks)
    }

    data class SourcedMarkdown(
        val relFilePath: FilePath,
        val inlinedSnippetId: SnippetId?,
        val content: Concatenation,
    ) : RevMdChunk {
        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            content.appendMarkdownContent(out, c)
        }

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("SourcedMarkdown") }
            key("inlinedSnippetId", isDefault = inlinedSnippetId == null) {
                value(inlinedSnippetId)
            }
            key("relFilePath") { value(relFilePath) }
            key("content") { value(content) }
        }
    }

    /**
     * Indicates that the [indented] was indented in the larger, edited Markdown.
     */
    data class IndentedRegion(
        /** A prefix string that should consist only of spaces and/or tab characters. */
        val indentation: String,
        /** The chunk whose lines should be indented. */
        val indented: Concatenation,
        /** True iff the first line should be indented. */
        val indentFirstLine: Boolean = false,
    ) : Reversed() {
        override val children: List<Reversed> get() = listOf(indented)

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            // Indentation is removed during reconciliation, so we don't post process here.
            indented.appendMarkdownContent(out, c)
        }

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("IndentedRegion") }
            key("indentation") { value(indentation) }
            key("indented") { value(indented) }
            key("indentFirstLine", isDefault = !indentFirstLine) { value(indentFirstLine) }
        }
    }

    data class LinkedMarkdown(
        val linked: Concatenation,
        val kind: Link.Kind,
        val target: String?,
    ) : Reversed() {
        override val children: List<Reversed> get() = listOf(linked)

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            linked.appendMarkdownContent(out, c)
            if (target != null) {
                val (left, right) = when (kind) {
                    Link.Kind.Parenthetical -> '(' to ')'
                    Link.Kind.SquareBracketed -> '[' to ']'
                }
                out.append(left)
                out.append(target)
                out.append(right)
            }
        }

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("LinkedMarkdown") }
            key("linked") { value(linked) }
            key("kind") { value(kind) }
            key("target") { value(target) }
        }
    }

    /**
     *
     */
    data class SnippetInsertion(
        val inserted: SnippetId,
        val markdownText: String,
    ) : Reversed() {
        override val children: List<Reversed> get() = emptyList()

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            out.append(markdownText)
        }

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("SnippetInsertion") }
            key("inserted") { value(inserted.shortCanonString(false)) }
            key("markdownText") { value(markdownText) }
        }
    }

    data class ReadjustHeadingLevels(
        val isFirstHeadingExempt: Boolean,
        val minLevel: Int,
        val content: Concatenation,
    ) : Reversed() {
        override val children: List<Reversed> get() = listOf(content)

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            out.postProcessChunk(
                { content.appendMarkdownContent(out, c) },
            ) { markdownContent ->
                val markdownText = markdownContent.fileContent
                val headings = findPositionsAndHeadingLevels(markdownContent)
                val minToAdjust = if (isFirstHeadingExempt) { 1 } else { 0 }
                // This is negative if we need to remove '#' characters from headings,
                // positive to add '#'s.
                val adjustmentDelta =
                    if (minToAdjust < headings.size) {
                        // Find the level among headers
                        val minLevelAmongAdjusted = headings.subListToEnd(minToAdjust)
                            .map { it.second }
                            .toSortedSet()
                            .first()
                        // Find the level we want to adjust that level above to.
                        // If we're exempting a header, it's over-arching, so we add 1.
                        val minLevelForAdjusted = minLevel + if (isFirstHeadingExempt) { 1 } else { 0 }

                        minLevelForAdjusted - minLevelAmongAdjusted
                    } else {
                        0
                    }
                if (adjustmentDelta == 0) {
                    markdownText
                } else {
                    var pos = 0 // Point in markdownText we've emitted up to
                    toStringViaBuilder { sb ->
                        fun emitTo(newPos: Int) {
                            check(newPos >= pos)
                            sb.append(markdownText, pos, newPos)
                            pos = newPos
                        }

                        for (i in minToAdjust until headings.size) {
                            val (headingOffset) = headings[i]
                            emitTo(headingOffset)
                            if (adjustmentDelta > 0) {
                                repeat(adjustmentDelta) { sb.append('#') }
                            } else {
                                check(
                                    (pos until pos - adjustmentDelta).all {
                                        markdownText[it] == '#'
                                    },
                                )
                                pos -= adjustmentDelta
                            }
                        }

                        emitTo(markdownText.length)
                    }
                }
            }
        }

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("ReadjustHeadingLevels") }
            key("isFirstHeadingExempt") { value(isFirstHeadingExempt) }
            key("minLevel") { value(minLevel) }
            key("content") { value(content) }
        }
    }

    /**
     * Makes sure that the count of blank lines at the end matches what was in the original.
     * When generating Markdown for snippets, we strip blank lines, and then we introduce an
     * end marker, later removed by reconcile, which may add some.
     */
    data class AdjustTrailingBlankLines(
        val wanted: Int,
        val content: Concatenation,
    ) : Reversed() {
        override val children: List<Reversed> get() = listOf(content)

        override fun appendMarkdownContent(out: MarkdownOutput, c: ProblemTracker) {
            out.postProcessChunk(
                { content.appendMarkdownContent(out, c) },
            ) { markdownContent ->
                val fileContent = markdownContent.fileContent
                val (offset) = countBlankLinesAtEnd(fileContent)

                buildString {
                    append(fileContent, 0, offset)
                    repeat(wanted) {
                        append('\n')
                    }
                }
            }
        }

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value("AdjustTrailingBlankLines") }
            key("wanted") { value(wanted) }
            key("content") { value(content) }
        }
    }
}

/**
 * Information about markdown files under [UserDocFilesAndDirectories.userDocRoot] and
 * how they're related to source files including snippets and [SkeletalDocsFiles].
 */
internal object UserDocsContent : AbstractUserDocsContent() {

    /** Regenerate markdown files from templates. */
    fun generate(
        problemTracker: ProblemTracker,
    ): Unit = UserDocFilesAndDirectories.run {
        val console = problemTracker.console
        // Copy snippets that are neither markdown nor intermediate to a place available to `mkdocs`
        console.groupSoft("Copying snippets to $unInlinedSnippetsRoot") {
            for (snippet in Snippets.snippetList) {
                if (snippet in snippetsAvailableAsFiles) {
                    val snippetPath = projectRoot.resolve(snippet.id.filePath)
                    val destination = unInlinedSnippetsRoot.resolve(
                        snippetsRoot.relativize(snippetPath),
                    )
                    Files.createDirectories(destination.parent)
                    Files.copy(snippetPath, destination)
                }
            }
        }

        // Do insertions and rewrite references.
        console.groupSoft("Post-processing markdown files") {
            for ((relFilePath, markdownContent) in templateFiles) {
                val nested = extractNestedForward(markdownContent, relFilePath)
                val targetFilePath = userDocRoot.resolve(relFilePath)
                val generatedMarkdown = MarkdownOutput().run {
                    nested.appendMarkdownContent(this, problemTracker)
                    this.markdownContent
                }
                Files.writeString(targetFilePath, generatedMarkdown.fileContent)
            }
        }
    }

    /**
     * Relates relative file paths to the content of a Markdown documentation template.
     */
    override val templateFiles: Map<FilePath, MarkdownContent>
        get() = SkeletalDocsFiles.markdownContent
    override val snippets: List<Snippet> get() = Snippets.snippetList

    private val insertionLocToInsertions =
        SnippetInsertions.insertions.groupBy { it.location }
            // Sort descending so that we replace later ranges before replacing ranges that would
            // affect character offsets
            .mapValues { (_, insertions) ->
                insertions.sortedByDescending { it.range.first }
            }

    /**
     * Relates snippets that are their own files instead of being inlined into a canonical
     * location in a larger file.
     */
    override val snippetsAvailableAsFiles: Map<Snippet, FilePath> = buildMap {
        for (snippet in Snippets.snippetList) {
            if (!snippet.shouldInline && !snippet.isIntermediate) {
                val snippetPath =
                    UserDocFilesAndDirectories.projectRoot.resolve(snippet.id.filePath)
                val destination = UserDocFilesAndDirectories.unInlinedSnippetsRoot.resolve(
                    UserDocFilesAndDirectories.snippetsRoot.relativize(snippetPath),
                )
                this[snippet] =
                    UserDocFilesAndDirectories.userDocRoot.relativize(destination).asFilePath
            }
        }
    }

    override fun findInsertions(loc: SnippetInsertionLocation) =
        insertionLocToInsertions[loc] ?: emptyList()

    override fun resolveSnippetId(
        slashedString: String,
        from: FilePath,
        referenceText: String,
    ) = Snippets.resolveShortId(slashedString, from, referenceText).mapResult { it.id }

    override fun snippetWithId(id: SnippetId): Snippet? = Snippets.snippetList.firstOrNull { it.id == id }

    override fun insertionsWithId(id: SnippetId): List<SnippetInsertion> =
        SnippetInsertions.insertions.mapNotNull {
            if (it is SnippetInsertion && it.snippet.id == id) {
                it
            } else {
                null
            }
        }

    override fun editedContentFor(relFilePath: FilePath): RResult<MarkdownContent, IOException> =
        RResult.of(IOException::class) {
            val locallyEditedFile = UserDocFilesAndDirectories.userDocRoot.resolve(relFilePath)
            MarkdownContent(Files.readString(locallyEditedFile))
        }
}

/**
 * Backs [UserDocsContent] but allows stubbing out the global set of source files
 * with something else for separable testing.
 */
internal abstract class AbstractUserDocsContent {
    internal abstract fun findInsertions(loc: SnippetInsertionLocation): List<AbstractSnippetInsertion>
    internal abstract val templateFiles: Map<FilePath, MarkdownContent>
    internal abstract val snippets: List<Snippet>
    internal abstract val snippetsAvailableAsFiles: Map<Snippet, FilePath>
    internal abstract fun resolveSnippetId(
        slashedString: String,
        from: FilePath,
        referenceText: String,
    ): RResult<SnippetId, IllegalArgumentException>

    internal abstract fun snippetWithId(id: SnippetId): Snippet?

    internal abstract fun insertionsWithId(id: SnippetId): List<SnippetInsertion>

    internal abstract fun editedContentFor(relFilePath: FilePath): RResult<MarkdownContent, IOException>

    private var insertionsByPathAndAnchor: Map<Pair<FilePath, String>, SnippetInsertion>? = null
    internal fun getInsertionForPathAndAnchor(relFilePath: FilePath, anchor: String): SnippetInsertion? {
        val insertionMap = this.insertionsByPathAndAnchor
            ?: run {
                val cached = buildMap {
                    for (snippet in snippets) {
                        val insertions = insertionsWithId(snippet.id)
                        for (insertion in insertions) {
                            val insertedAnchor = insertion.anchor
                            if (insertedAnchor != null) {
                                val destination = insertion.location.destinationPath
                                val key = destination to insertedAnchor
                                if (insertion.isCanonical || key !in this) {
                                    this[destination to insertedAnchor] = insertion
                                }
                            }
                        }
                    }
                }
                this.insertionsByPathAndAnchor = cached
                cached
            }

        return insertionMap[relFilePath to anchor]
    }

    /**
     * Forward convention is when we're using templates under
     * [UserDocFilesAndDirectories.skeletalDocRoot] to rebuild
     * [UserDocFilesAndDirectories.userDocRoot].
     */
    internal fun extractNestedForward(
        content: MarkdownContent,
        source: FilePath,
    ): Nested.SourcedMarkdown {
        val nested = findInsertionsInto(
            SourceFileInsertionLocation(source),
            source,
            content,
            inlinedSnippet = null,
            anchorForContent = null,
        )
        rewriteLinkTargets(nested)
        return nested
    }

    /** Recursively expand snippet insertions. */
    private fun findInsertionsInto(
        loc: SnippetInsertionLocation,
        relFilePath: FilePath,
        content: MarkdownContent,
        inlinedSnippet: Snippet?,
        anchorForContent: Nested.SnippetAnchor?,
    ): Nested.SourcedMarkdown {
        val insertions = findInsertions(loc)
        // We need to adjust heading levels of inserted snippets to match the surrounding
        // headings.
        //
        // If a snippet contains text like
        //     # Syntax
        //     ...
        // and the closest preceding heading is
        //     ## Booleans
        // then we adjust the snippet's headings up by two levels to
        //     ### Syntax
        //     ...
        // To do that we derive a mapping from character offsets in content to heading levels
        // which are integers in the range 1-6.
        val headingOffsetMap = HeadingOffsetMap(content)

        val body = Nested.Concatenation()

        val (startMarker, endMarker) = findSnippetBoundaryMarkers(content, inlinedSnippet?.id)
        val snippetMarkerIdText = inlinedSnippet?.id?.shortCanonString(withExtension = false)

        // Position into content just past that which has been appended to body.
        var insertedLeft = 0

        if (snippetMarkerIdText != null || startMarker != null) {
            body.chunks.add(
                Nested.MarkerComment(
                    if (startMarker == null) {
                        "<!-- snippet: $snippetMarkerIdText -->"
                    } else {
                        insertedLeft = startMarker.last + 1
                        content.fileContent.slice(startMarker)
                    },
                ),
            )
        }

        if (anchorForContent != null) {
            body.chunks.add(anchorForContent)
        }

        // Process insertions in order
        val insertionsInOrder = insertions.sortedBy { it.range.first }
        for (insertion in insertionsInOrder) {
            val anchor = insertion.anchor
            val anchorName = if (anchor != null) {
                check(anchor.startsWith("#"))
                anchor.substring(1)
            } else {
                null
            }
            var replacement = when (insertion) {
                is SnippetInsertion -> {
                    val snippet = insertion.snippet
                    val snippetContent = MarkdownContent(
                        (snippet.content as TextDocContent).text,
                    )
                    findInsertionsInto(
                        NestedInsertion(insertion),
                        relFilePath,
                        snippetContent,
                        snippet,
                        anchorName?.let { Nested.SnippetAnchor(it) },
                    )
                }

                is InvalidSnippetInsertion -> {
                    Nested.Invalid("Invalid insertion: ${insertion.text}")
                }
            }

            if (
                insertion.attributes[SnippetInsertionAttributeKey.Heading] ==
                JsonBoolean.valueFalse
            ) {
                replacement = Nested.StripHeading(
                    Nested.Concatenation(listOf(replacement)),
                )
            }
            val contextLevel = headingOffsetMap[insertion.range.first]
            replacement = Nested.HeadingAdjustment(
                contextLevel,
                Nested.Concatenation(listOf(replacement)),
            )
            body.chunks.add(
                try {
                    Nested.Literal(content.fileContent.substring(insertedLeft, insertion.range.first))
                } catch (ex: StringIndexOutOfBoundsException) {
                    Nested.Invalid("Problem finding text for ${insertion.location}: $ex")
                },
            )

            val indentation = markdownIndentationLevel(content, insertion.range.first)
            if (indentation.isNotEmpty()) {
                replacement = Nested.IndentedRegion(
                    indentation = indentation,
                    indented = replacement,
                    indentFirstLine = true,
                )
            }

            insertedLeft = insertion.range.last + 1
            body.chunks.add(replacement)
        }

        val contentLimit = endMarker?.first ?: content.fileContent.length
        if (insertedLeft < contentLimit) {
            body.chunks.add(
                Nested.Literal(content.fileContent.substring(insertedLeft, contentLimit)),
            )
        }

        if (snippetMarkerIdText != null) {
            body.chunks.add(
                Nested.MarkerComment("<!-- /snippet: $snippetMarkerIdText -->"),
            )
        }

        return Nested.SourcedMarkdown(relFilePath, loc, body, inlinedSnippet)
    }

    private fun rewriteLinkTargets(nested: Nested.SourcedMarkdown) {
        rewriteLinkTargetsForFile(nested.relFilePath, nested.content)
    }

    /**
     * Look through the nested structure finding links, explicit and implied, and rewrite them.
     *
     * This assumes that [Nested.Literal] are not split inside code blocks or HTML tags where
     * Markdown links could not appear.
     */
    private fun rewriteLinkTargetsForFile(
        relFilePath: FilePath,
        nested: Nested,
    ) {
        when (nested) {
            is Nested.Concatenation -> {
                val rewritten = nested.chunks.flatMap { chunk ->
                    when (chunk) {
                        is Nested.Literal ->
                            rewriteLinkTargetsInMarkdown(relFilePath, chunk)
                        is Nested.MarkerComment -> listOf(chunk)
                        is Nested.SourcedMarkdown -> {
                            rewriteLinkTargets(chunk)
                            listOf(chunk)
                        }
                        is Nested -> {
                            rewriteLinkTargetsForFile(relFilePath, chunk)
                            listOf(chunk)
                        }
                    }
                }
                nested.chunks.clear()
                nested.chunks.addAll(rewritten)
            }

            else -> nested.children.forEach {
                rewriteLinkTargetsForFile(relFilePath, it)
            }
        }
    }

    private fun rewriteLinkTargetsInMarkdown(
        relFilePath: FilePath,
        chunk: Nested.Literal,
    ): List<MdChunk> {
        val contentText = chunk.markdownText
        val markdownContent = MarkdownContent(contentText)

        // Build a list of chunks broken out of chunk.
        val splitChunks = mutableListOf<MdChunk>()
        // The index into contentText before which all characters are represented on rewritten.
        var contentTextPos = 0

        val links = findLinks(markdownContent)
        for (link in links) {
            val replacement = rewriteLinkTarget(relFilePath, link.stringifyRanges(contentText))
            if (replacement != null) {
                val beforeLink = link.whole.first
                check(contentTextPos <= beforeLink) {
                    "Nested or out of order link."
                }
                if (contentTextPos < beforeLink) {
                    splitChunks.add(
                        Nested.Literal(contentText.substring(contentTextPos, beforeLink)),
                    )
                }
                splitChunks.add(replacement)
                contentTextPos = link.whole.last + 1
            }
        }

        if (contentTextPos < contentText.length) {
            splitChunks.add(
                Nested.Literal(contentText.substring(contentTextPos)),
            )
        }

        return splitChunks.toList()
    }

    internal fun rewriteLinkTarget(
        /** The path to the content containing the link used for crafting error messages */
        relFilePath: FilePath,
        /** Link information with extracted text rather than ranges. */
        link: Link<String>,
    ): Nested? {
        val linkText = link.whole
        val linkedTextWithBrackets = link.linked?.let { "[$it]" }
        val kind = link.kind
        val target = link.target
        if (kind != Link.Kind.SquareBracketed || link.locallyDefined) {
            return null
        }

        var problem: String? = null
        var replacement: Nested? = null
        val originalCombinedText = if (linkedTextWithBrackets == null) {
            target
        } else {
            null
        }

        when {
            target.startsWith("snippet/") -> {
                val shortId = target.substring("snippet/".length)
                when (
                    val snippetResult =
                        resolveSnippetId(shortId, relFilePath, "[$target]")
                ) {
                    is RFailure -> {
                        problem = snippetResult.failure.message!!
                    }
                    is RSuccess -> {
                        val matchingSnippetId = snippetResult.result
                        val matchingSnippet: Snippet? = snippetWithId(matchingSnippetId)
                        val linkableInsertions = insertionsWithId(matchingSnippetId).filter { it.anchor != null }
                        check(
                            linkedTextWithBrackets == null || (
                                linkedTextWithBrackets.startsWith('[') &&
                                    linkedTextWithBrackets.endsWith(']')
                                ),
                        ) { "$linkedTextWithBrackets" }
                        val title = linkedTextWithBrackets?.unbracketed('[', ']')
                            ?: matchingSnippet?.shortTitle
                            ?: target
                        if (linkableInsertions.isNotEmpty()) {
                            // We can link to a place the snippet was inserted
                            val targetInsertion =
                                linkableInsertions.firstOrNull { it.isCanonical }
                                    ?: linkableInsertions.first()
                            val destinationPath = targetInsertion.location.destinationPath
                            replacement = if (destinationPath == relFilePath) {
                                // Same file.  Just do (#anchor)
                                Nested.Link(title, "(${targetInsertion.anchor!!})", originalCombinedText)
                            } else {
                                val pathToFile =
                                    relFilePath.relativePathTo(destinationPath)
                                        .join(isDir = false)
                                Nested.Link(title, "($pathToFile${targetInsertion.anchor})", originalCombinedText)
                            }
                        } else {
                            // See if we can link to a file under
                            // `temper-docs/docs/snippet/`
                            val pathToSnippet = snippetsAvailableAsFiles[matchingSnippet]
                            if (pathToSnippet != null) {
                                val relSnippetPath = relFilePath.relativePathTo(pathToSnippet)
                                val snippetTarget = toStringViaBuilder { sb ->
                                    sb.append('(')
                                    relSnippetPath.joinPathTo(isDir = false, sb = sb)
                                    sb.append(')')
                                }
                                replacement = Nested.Link(title, snippetTarget, originalCombinedText)
                            } else {
                                problem =
                                    "Cannot link $target to snippet ${
                                        matchingSnippetId
                                    }; it has not been inlined into${
                                        ""
                                    } markdown nor is it available as a file"
                            }
                        }
                    }
                }
            }
            target.startsWith("temper/") -> {
                val glob = target.substring("temper/".length)
                val matches = SourceFiles.matching(glob).toList()
                when (matches.size) {
                    0 -> {
                        problem = "Cannot find source file matching glob `$glob`"
                    }
                    1 -> {
                        val file = matches[0]
                        val tail = file.copy(segments = listOf(file.segments.last()))
                        replacement = Nested.Link(
                            "*$tail*",
                            "($PROJECT_GITHUB_URL/blob/main/$file)",
                            originalCombinedText,
                        )
                    }
                    else -> {
                        problem = toStringViaBuilder { sb ->
                            sb.append("Ambiguous file link `$target` could be")
                            matches.forEach {
                                sb.append("\n  - $it")
                            }
                        }
                    }
                }
            }
            target.startsWith("issue#") -> {
                replacement = Nested.Link(
                    target,
                    "($PROJECT_GITHUB_URL/issues/${
                        urlEscape(target.substring(target.indexOf('#') + 1))
                    })",
                    originalCombinedText,
                )
            }
            target.startsWith("PR#") -> {
                replacement = Nested.Link(
                    target,
                    "($PROJECT_GITHUB_URL/pulls/${
                        urlEscape(target.substring(target.indexOf('#') + 1))
                    })",
                    originalCombinedText,
                )
            }
            else -> Unit
        }

        if (problem != null) {
            val text = linkText.replace(crLfOrLfPattern, " ")
            replacement = Nested.Invalid("$relFilePath:`$text`: $problem")
        }

        return replacement
    }

    /**
     * Ensure that there are both start and end comments delimiting the snippet content.
     * These markers will allow us to map changes to the Markdown back to specific snippets.
     *
     * We need both:
     * - a start marker like `<!-- snippet: ... -->`
     * - an end marker like `<!-- /snippet -->`
     *
     * Snippets that are not extracted from doc comments will
     * probably have neither.
     * The latter is optional when a snippet extracted from a doc
     * comment ends at the end of the comment or ends because
     * it is followed by the start delimiter of another snippet.
     */
    private fun findSnippetBoundaryMarkers(
        content: MarkdownContent,
        id: SnippetId?,
    ): Pair<IntRange?, IntRange?> {
        if (id == null) { return null to null }

        var leftScanPos: Node? = content.root
        while (leftScanPos != null) {
            leftScanPos = when (leftScanPos) {
                is HtmlBlock -> break
                else -> leftScanPos.firstChild
            }
        }

        var rightScanPos: Node? = content.root
        while (rightScanPos != null) {
            rightScanPos = when (rightScanPos) {
                is HtmlBlock -> break
                else -> rightScanPos.lastChild
            }
        }

        fun matchingRange(node: Node?, leftOrRight: LeftOrRight) = node?.let { pos ->
            val range = content.range(pos)
            when (isSnippetMarkerComment(content.fileContent.slice(range), id, leftOrRight)) {
                true -> range
                false -> null
            }
        }
        val leftScanRange = matchingRange(leftScanPos, LeftOrRight.Left)
        val rightScanRange = matchingRange(rightScanPos, LeftOrRight.Right)

        return leftScanRange to rightScanRange
    }

    private fun isSnippetMarkerComment(
        text: CharSequence?,
        id: SnippetId,
        side: LeftOrRight,
    ): Boolean {
        if (text != null) {
            val match = markerPattern.matchEntire(text)
            if (match != null && match.groupValues[1].isEmpty() == (side == LeftOrRight.Left)) {
                val idString = match.groupValues[2]
                when (val r = resolveSnippetId(idString, FilePath(listOf(), isDir = true), "")) {
                    is RSuccess -> return r.result == id
                    is RFailure -> Unit
                }
            }
        }
        return false
    }

    fun findInsertionsOf(id: SnippetId, into: SnippetInsertionLocation): SnippetInsertion? {
        return insertionsWithId(id).firstOrNull { it.location == into }
    }
}

/**
 * Stores the snippet id in group 2 and a '/' in group 1 if it's a qualified end marker.
 *
 * Normally, an end marker in snippet text looks like
 *
 *     <!-- /snippet -->
 *
 * but our boundaries in rendered markdown are more robust if we include the snippet ID
 * this:
 *
 *     <!-- /snippet: my/snippet -->
 */
internal val markerPattern =
    Regex("""^<!--+[\s]*(/?)snippet[\s]*:[\s]*((?!--+>)\S+)[\s\S]*--+>$""")

private fun <T> concatenationToString(chunks: List<T>): String =
    toStringViaBuilder { sb ->
        sb.append("(Concatenation")
        when (chunks.size) {
            0 -> Unit
            1 -> {
                sb.append(' ')
                sb.append(chunks[0])
            }
            else -> {
                chunks.forEach {
                    sb.append('\n')
                    sb.append(it)
                }
                sb.append('\n')
            }
        }
        sb.append(")")
    }

/**
 * HTML `class` attribute identifier used to mark `<a name>` elements that are
 * link targets for inserted snippets.
 */
internal const val SNIPPET_ANCHOR_CLASSNAME = "snippet-anchor-name"
