package lang.temper.docbuild

import lang.temper.common.Log
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.compatReversed
import lang.temper.common.putMultiList
import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.children
import lang.temper.log.FilePath
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import kotlin.math.min

/**
 * Reverse local changes to user docs to changes to Markdown file templates and snippets in source code.
 */
internal fun AbstractUserDocsContent.reverseEngineer(problemTracker: ProblemTracker): UserDocChanges =
    ReverseEngineer(this, problemTracker).reverse()

private data class BackPortedFile(
    val relFilePath: FilePath,
    val sourceContent: MarkdownContent,
    val original: Nested.SourcedMarkdown,
    val reversed: Reversed.SourcedMarkdown,
    val locallyDefined: Set<String>,
)

private data class Reconciliation(
    val original: MdChunk,
    val reversed: Reversed.SourcedMarkdown,
    val file: BackPortedFile,
)

private class ReverseEngineer(
    val userDocsContent: AbstractUserDocsContent,
    val problemTracker: ProblemTracker,
) {
    private val reconciliationsById = mutableMapOf<SnippetId, List<Reconciliation>>()

    // Our output is a set of changes.  Some to snippets, and some to the user documentation
    // template files.
    // We collect changes to snippets and files in member maps and lists.

    /**
     * A map that lets us compute snippet changes on demand in
     *
     * We may need to re-inline snippets in other snippets.
     * But the re-inlined ones may have changed which introduces an ordering problem.
     * For example, a snippet like
     *
     *     <!-- snippet: my/example/snippet -->
     *     Lorem ipsum
     *
     *     ```temper
     *     dolor(sic, amet)
     *     ```
     *
     * The code block might have been extracted into a separate snippet,
     * my/example/snippet/0, by [TemperCodeSnippetExtractor].
     */
    private val snippetChangesById =
        mutableMapOf<SnippetId?, Pair<TextDocContent, MutableSet<UserDocChanges.SnippetChange>>>()
    private val fileChanges = mutableListOf<UserDocChanges.FileChange>()

    /**
     * Reads content from files in [UserDocsContent.templateFiles] and reverse engineers content to
     * produce a set of changes.
     */
    fun reverse(): UserDocChanges {
        // Process each generated Markdown file, grouping the original with the edited.
        val backPortedFiles = userDocsContent.templateFiles
            .mapNotNull { (relFilePath, sourceContent) ->
                val editedContent = when (val r = userDocsContent.editedContentFor(relFilePath)) {
                    is RSuccess -> r.result
                    is RFailure -> {
                        problemTracker.error("Could not load edited content for $relFilePath: ${r.failure}")
                        return@mapNotNull null
                    }
                }
                val locallyDefined = findLocallyDefinedLinkTargets(editedContent).keys
                val original = userDocsContent.extractNestedForward(sourceContent, relFilePath)
                val reversed = userDocsContent.reverseUserDocs(
                    relFilePath,
                    editedContent,
                    problemTracker,
                )
                BackPortedFile(relFilePath, sourceContent, original, reversed, locallyDefined)
            }
            .associateBy { it.relFilePath }

        // Now pair up original snippets and reverse-engineered snippets.
        // Snippet insertions might move from one place to another.
        val originals = mutableMapOf<SnippetId, MutableList<Pair<MdChunk, BackPortedFile>>>()
        val reversed = mutableMapOf<SnippetId, MutableList<Pair<Reversed.SourcedMarkdown, BackPortedFile>>>()
        for (backPorted in backPortedFiles.values) {
            findOriginalSnippets(backPorted.original.content, backPorted, originals)
            findReversedSnippets(backPorted.reversed.content, backPorted, reversed)
        }
        val snippetIds = originals.keys.intersect(reversed.keys)
        val reconciliations = mutableListOf<Reconciliation>()
        for (snippetId in snippetIds) {
            // We need to deal with several situations:
            // 1. the common case: a snippet that occurs in one file
            // 2. a snippet that occurs multiply like a standard disclaimer or caveat snippet
            // 3. a snippet that is moved from one file to another and edited
            val osByBackend = mutableMapOf<BackPortedFile, MutableList<MdChunk>>()
            val rsByBackend = mutableMapOf<BackPortedFile, MutableList<Reversed.SourcedMarkdown>>()
            originals.getValue(snippetId).forEach { (o, f) -> osByBackend.putMultiList(f, o) }
            reversed.getValue(snippetId).forEach { (r, f) -> rsByBackend.putMultiList(f, r) }

            // Match up pairs corresponding by ordinal position in file.
            // This handles case 1. and 2. above.
            for ((f, rs) in rsByBackend.entries.toList()) {
                val os = osByBackend[f] ?: mutableListOf()
                val n = min(rs.size, os.size)
                for (i in 0 until n) {
                    reconciliations.add(Reconciliation(os[i], rs[i], f))
                }
                rs.subList(0, n).clear()
                os.subList(0, n).clear()
            }

            // Handle case 3 by matching up the ones we didn't eliminate by clearing sub-lists above.
            val remainingOs = mutableListOf<MdChunk>()
            val remainingRs = mutableListOf<Pair<Reversed.SourcedMarkdown, BackPortedFile>>()
            osByBackend.values.forEach { remainingOs.addAll(it) }
            rsByBackend.entries.forEach { (f, chunks) ->
                chunks.forEach { remainingRs.add(it to f) }
            }
            if (remainingRs.isNotEmpty()) {
                val fallbackO = originals.getValue(snippetId).first().first
                remainingRs.forEachIndexed { i, (r, f) ->
                    val o = remainingOs.getOrElse(i) { fallbackO }
                    reconciliations.add(Reconciliation(o, r, f))
                }
            }
        }

        reconciliationsById.putAll(reconciliations.groupBy { it.reversed.inlinedSnippetId!! })
        reconciliationsById.keys.forEach { computeSnippetChangesForId(it) }

        for (f in backPortedFiles.values) {
            reconcileAndEmitChange(
                file = f,
                original = f.original,
                reversed = f.reversed,
                unchanged = f.sourceContent.fileContent,
                tolerant = false,
            ) {
                fileChanges.add(UserDocChanges.FileChange(f.relFilePath, it))
            }
        }

        val allSnippetChanges = snippetChangesById.values.flatMap { it.second }
        val changes = UserDocChanges(allSnippetChanges, fileChanges.toList())
        checkChangesForErrors(changes)
        return changes
    }

    /** Find all snippets for the given file. */
    fun findOriginalSnippets(
        n: MdChunk,
        f: BackPortedFile,
        out: MutableMap<SnippetId, MutableList<Pair<MdChunk, BackPortedFile>>>,
    ) {
        // Look for a SourcedMarkdown instance
        var lookThrough: MdChunk = n
        while (true) {
            when (lookThrough) {
                is Nested.Concatenation -> {
                    if (lookThrough.chunks.size == 1) {
                        lookThrough = lookThrough.chunks[0]
                    } else {
                        break
                    }
                }
                is Nested.Literal,
                is Nested.Invalid,
                is Nested.Link,
                is Nested.MarkerComment,
                is Nested.SnippetAnchor,
                is Nested.SourcedMarkdown,
                -> break
                is Nested.HeadingAdjustment -> lookThrough = lookThrough.content
                is Nested.IndentedRegion -> lookThrough = lookThrough.indented
                is Nested.StripHeading -> lookThrough = lookThrough.content
            }
        }
        if (lookThrough is Nested.SourcedMarkdown) {
            lookThrough.inlinedSnippet?.let { out.putMultiList(it.id, n to f) }
            findOriginalSnippets(lookThrough.content, f, out)
        } else if (n is Nested) {
            if (n is Nested.Concatenation) {
                for (c in n.chunks) {
                    findOriginalSnippets(c, f, out)
                }
            } else {
                for (c in n.children) {
                    findOriginalSnippets(c, f, out)
                }
            }
        }
    }

    /** Find all sourced markdown for the given file. */
    private fun findReversedSnippets(
        n: Reversed,
        f: BackPortedFile,
        out: MutableMap<SnippetId, MutableList<Pair<Reversed.SourcedMarkdown, BackPortedFile>>>,
    ) {
        if (n is Reversed.Concatenation) {
            for (c in n.chunks) {
                if (c is Reversed.SourcedMarkdown) {
                    c.inlinedSnippetId?.let { out.putMultiList(it, c to f) }
                    findReversedSnippets(c.content, f, out)
                }
            }
        }
        for (c in n.children) {
            findReversedSnippets(c, f, out)
        }
    }

    private fun computeSnippetChangesForId(id: SnippetId): TextDocContent {
        snippetChangesById[id]?.let {
            // Already computed
            return@computeSnippetChangesForId it.first
        }

        val snippet = userDocsContent.snippetWithId(id)
        if (snippet == null) {
            problemTracker.error("Cannot find nested snippet $id")
            return TextDocContent("Missing snippet $id")
        }
        val snippetContent = snippet.content
        if (snippetContent !is TextDocContent) {
            problemTracker.error("Cannot back-port non-textual content for snippet $id")
            return TextDocContent("Non textual content for $id")
        }

        val changes = mutableSetOf<UserDocChanges.SnippetChange>()
        val tolerant = !snippet.derivation.extractor.supportsBackPorting
        snippetChangesById[id] = snippetContent to changes

        for ((o, r, f) in reconciliationsById.getValue(id)) {
            val originalContent: TextDocContent = snippetContent

            reconcileAndEmitChange(
                file = f,
                original = o,
                reversed = r,
                unchanged = originalContent.text,
                tolerant = tolerant,
            ) {
                changes.add(UserDocChanges.SnippetChange(snippet, it, f.relFilePath))
            }
        }

        val content = changes.firstOrNull()?.newContent?.let { TextDocContent(it.fileContent) }
            ?: snippetContent
        snippetChangesById[id] = content to changes
        return content
    }

    /**
     * Reconcile the original and changed, and if they differ from [unchanged], call
     * [emitChange] to add an entry to the change set.
     */
    private fun reconcileAndEmitChange(
        file: BackPortedFile,
        original: MdChunk,
        reversed: Reversed.SourcedMarkdown,
        unchanged: String,
        tolerant: Boolean,
        emitChange: (MarkdownContent) -> Unit,
    ) {
        val problemCountBefore = problemTracker.problemCount
        val contentBefore = MarkdownContent(unchanged)
        userDocsContent.reconcile(
            original = original,
            reversed = reversed,
            contentBefore = contentBefore,
            locallyDefined = file.locallyDefined,
            problemTracker = problemTracker,
        ) { insertedId, containingLoc ->
            replacementInsertion(insertedId, containingLoc)
        }
        if (problemTracker.problemCount == problemCountBefore) {
            val changedContent = reversed.toMarkdown()
            if (changedContent.fileContent != unchanged) {
                // If we've got a snippet change that is auto-generated, don't try to back-port it.
                if (
                    !tolerant ||
                    hasNonTolerableChanges(contentBefore, changedContent)
                ) {
                    emitChange(changedContent)
                }
            }
        }
    }

    private fun replacementInsertion(
        insertedId: SnippetId,
        containingLoc: SnippetInsertionLocation,
    ): MarkdownContent {
        // Fetch the snippet content and use the original extractor
        // to adjust it.
        val priorInsertion = userDocsContent.findInsertionsOf(insertedId, into = containingLoc)
        val shortId = insertedId.shortCanonString(false)
        val fallback = MarkdownContent("$INSERTION_MARKER_CHAR $shortId")
        return if (priorInsertion == null) {
            fallback
        } else {
            val inserted = priorInsertion.snippet
            val reversed = when (inserted.derivation) {
                is ExtractedAndReplacedBack ->
                    inserted.derivation.extractor.backPortInsertion(
                        inserted,
                        priorInsertion.replacedContent as? TextDocContent,
                    ) {
                        computeSnippetChangesForId(inserted.id)
                    }
                is ExtractedBy -> when (val replaced = priorInsertion.replacedContent) {
                    is TextDocContent -> RSuccess(replaced)
                    is ShellCommandDocContent,
                    is ByteDocContent,
                    -> RFailure(
                        IllegalStateException("Expected textual content to embed $shortId"),
                    )
                }
            }

            when (reversed) {
                is RSuccess -> MarkdownContent(reversed.result.text)
                is RFailure -> {
                    problemTracker.error(
                        reversed.failure.message
                            ?: "Failed to compute replacement for insertion of $shortId",
                    )
                    fallback
                }
            }
        }
    }

    private fun checkChangesForErrors(changes: UserDocChanges) {
        // Make sure our changes don't collide.
        val dupes = changes.snippetChanges.groupBy { it.snippet.id }
            .values
            .filter { it.size >= 2 }
        for (dupe in dupes) {
            val snippet = dupe.first().snippet
            problemTracker.errorGroup(
                "${snippet.source}+${snippet.sourceStartOffset}: Snippet ${
                    snippet.id.shortCanonString(false)
                } has multiple conflicting edits",
            ) { console ->
                dupe.forEachIndexed { i, change ->
                    console.group("Change $i", Log.Error) {
                        console.error(change.newContent.fileContent)
                    }
                }
            }
        }
    }
}

private fun hasNonTolerableChanges(
    mdBefore: MarkdownContent,
    mdAfter: MarkdownContent,
): Boolean {
    fun getLinesTolerant(m: MarkdownContent): List<String> =
        m.cleanedUpTolerant()
            .fileContent
            .split(crLfOrLfPattern)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    val before = getLinesTolerant(mdBefore)
    val after = getLinesTolerant(mdAfter)

    return before != after
}

/** Blurs out details that are not super-significant to back-port */
private fun MarkdownContent.cleanedUpTolerant(): MarkdownContent {
    val replacements = mutableListOf<Pair<IntRange, String>>()
    fun findFuzzes(node: Node) {
        val replacement = when (node) {
            is HtmlBlock,
            is HtmlInline,
            -> ""

            // Fuzz `[...]` and `[...][label]` and `[...](url)`
            is Image,
            is Link,
            -> ""

            else -> null
        }
        if (replacement != null) {
            replacements.add(range(node) to replacement)
        } else {
            node.children().forEach {
                findFuzzes(it)
            }
        }
    }
    findFuzzes(root)
    return MarkdownContent(
        toStringViaBuilder { sb ->
            sb.append(this.fileContent)
            for ((node, replacement) in replacements.compatReversed()) {
                sb.replace(node.first, node.last + 1, replacement)
            }
        },
    )
}
