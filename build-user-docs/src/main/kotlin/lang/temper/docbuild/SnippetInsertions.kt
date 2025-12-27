package lang.temper.docbuild

import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.isMarkdown
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.common.subListToEnd
import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.Lexer
import lang.temper.lexer.TokenType
import lang.temper.log.FilePath
import lang.temper.log.LogSink
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Paragraph

/**
 * Insertion locations of [snippets][Snippets] in files under `build-user-docs/skeletal-docs`.
 *
 * These are regular Markdown text nodes like
 *
 *     ⎀ foo/bar
 *
 * where `foo/bar` can be any [SnippetId.parts] with or without extension.
 *
 * The snippet id may be followed by a space and `!` to indicate that it is a canonical location.
 */
internal object SnippetInsertions {
    val insertions: List<AbstractSnippetInsertion>

    init {
        val insertions = mutableListOf<AbstractSnippetInsertion>()

        fun addInsertionsFromMarkdown(loc: SnippetInsertionLocation, content: MarkdownContent) {
            val contentText = content.fileContent
            val insertionCountBeforeScan = insertions.size

            /** Come up with an anchor so the replacement can be linked to. */
            fun anchorFor(id: SnippetId): String? {
                if (id.extension != ".md") { return null }
                // If there are no previous insertions in this file, then just use the snippet ID,
                // replacing `/` with `-`
                // Otherwise, stick a number on the end to disambiguate.
                // This is an attempt to balance stability of links across different versions of
                // the documentation with the need for specificity.
                var occurrence = 0
                for (i in insertionCountBeforeScan until insertions.size) {
                    if ((insertions[i] as? SnippetInsertion)?.snippet?.id == id) {
                        occurrence += 1
                    }
                }
                val anchorPrefix = "#${
                    id.shortCanonString(withExtension = false).replace('/', '-')
                }"
                return if (occurrence == 0) {
                    anchorPrefix
                } else {
                    "$anchorPrefix-$occurrence"
                }
            }

            InsertionScanner(loc, ::anchorFor, insertions)
                .scanForInsertions(content)

            val newInsertions = insertions.subListToEnd(insertionCountBeforeScan).toList()
            val countInsertedThisFile = newInsertions.size
            var countInsertionMarkers = 0
            for (i in contentText.indices) {
                if (contentText[i] == INSERTION_MARKER_CHAR) {
                    countInsertionMarkers += 1
                }
            }
            @Suppress("RemoveCurlyBracesFromTemplate")
            check(countInsertedThisFile == countInsertionMarkers) {
                "${loc.diagnosticPath} has $countInsertionMarkers '${
                    INSERTION_MARKER_CHAR
                }' but only ${
                    countInsertedThisFile
                } insertions were found.  Could one or more be outside a markdown text node?"
            }

            // Now, look for nested insertions.
            for (newInsertion in newInsertions) {
                if (newInsertion !is SnippetInsertion) { continue }
                if (newInsertion.snippet.shouldInline && newInsertion.snippet.mimeType.isMarkdown) {
                    // Make sure we don't have any snippet insertion cycles.
                    var outerLoc = loc
                    locLoop@
                    while (true) {
                        when (val l = outerLoc) {
                            is SourceFileInsertionLocation -> break@locLoop
                            is NestedInsertion -> {
                                if (l.snippetId == newInsertion.snippet.id) {
                                    error(
                                        "Snippet ${
                                            l.snippetId
                                        } inserts itself via insertion chain $loc",
                                    )
                                }
                                outerLoc = l.insertion.location
                            }
                        }
                    }
                    val nestedContent = when (val newContent = newInsertion.snippet.content) {
                        is ByteDocContent,
                        is ShellCommandDocContent,
                        -> null
                        is TextDocContent -> MarkdownContent(newContent.text)
                    }
                    if (nestedContent != null) {
                        addInsertionsFromMarkdown(
                            NestedInsertion(newInsertion),
                            nestedContent,
                        )
                    }
                }
            }
        }

        for ((path, content) in SkeletalDocsFiles.markdownContent) {
            addInsertionsFromMarkdown(SourceFileInsertionLocation(path), content)
        }

        // Generate insertions for replace-backs
        for (snippet in Snippets.snippetList) {
            val replaceBackRange = when (val d = snippet.derivation) {
                is ExtractedAndReplacedBack -> d.replaceBackRange
                is ExtractedBy -> continue
            }
            val replacedContent = snippet.content
            val source = snippet.source
            val locationsMatching =
                if (source in SourceFiles.files) {
                    listOf(SourceFileInsertionLocation(source))
                } else {
                    insertions.mapNotNull {
                        if (it is SnippetInsertion && it.snippet.id.filePath == source) {
                            NestedInsertion(it)
                        } else {
                            null
                        }
                    }
                }
            for (location in locationsMatching) {
                insertions.add(
                    SnippetInsertion(
                        snippet,
                        location,
                        replaceBackRange,
                        replacedContent = replacedContent,
                        attributes = emptyMap(),
                    ),
                )
            }
        }

        this.insertions = insertions.toList()
    }
}

/** Where an insertion happens. */
internal sealed class SnippetInsertionLocation {
    /** The file that the insertion textually appears in, for debugging purposes. */
    abstract val diagnosticPath: FilePath

    /**
     * The file into which the insertion will eventually appear after nested insertions are
     * flattened into one final insertion.
     */
    abstract val destinationPath: FilePath
}

/** An insertion that appears directly in a source file. */
internal data class SourceFileInsertionLocation(
    /** A path to a markdown file relative to `build-user-docs/skeletal-docs/`. */
    val filePath: FilePath,
) : SnippetInsertionLocation() {
    override val diagnosticPath get() = filePath
    override val destinationPath get() = filePath

    override fun toString(): String = "$filePath"
}

/** An insertion that appears in inserted content. */
internal data class NestedInsertion(
    val insertion: SnippetInsertion,
) : SnippetInsertionLocation() {
    val snippetId get() = insertion.snippet.id
    override val diagnosticPath get() = snippetId.filePath
    override val destinationPath get() = insertion.location.destinationPath

    override fun toString(): String = "${insertion.location} > ${insertion.snippet.id.filePath}"
}

internal typealias InsertionAttributeMap = Map<SnippetInsertionAttributeKey, JsonValue>
internal typealias MutableInsertionAttributeMap =
    MutableMap<SnippetInsertionAttributeKey, JsonValue>

/**
 * Derived from a match of a line starting with [INSERTION_MARKER_CHAR].
 */
internal sealed class AbstractSnippetInsertion {
    /** Where the insertion marker appears. */
    abstract val location: SnippetInsertionLocation

    /** The characters in [location] corresponding to the insertion */
    abstract val range: IntRange

    /**
     * Maps insertion attributes to JSON-values that control how the insertion happens.
     */
    abstract val attributes: InsertionAttributeMap

    /**
     * Whether the insertion is the "canonical" location in that it should be preferred to other
     * insertions for the same [snippet][SnippetInsertion.snippet] when rewriting links to snippets.
     */
    val isCanonical: Boolean
        get() = (getAttribute(SnippetInsertionAttributeKey.IsCanonical) as? JsonBoolean)?.b == true

    /**
     * A URL anchor (with leading `#`) that may be used to link to the insertion.
     * This is used, in conjunction with [location] to rewrite Markdown links like
     * `[snippet/foo/bar]`.
     */
    val anchor: String?
        get() {
            val anchor = getAttribute(SnippetInsertionAttributeKey.Anchor) as? JsonString
            return if (anchor?.s?.startsWith("#") == true) {
                anchor.s
            } else {
                null
            }
        }

    fun getAttribute(k: SnippetInsertionAttributeKey) = attributes[k] ?: k.defaultValue
}

/** A reference to snippet content at a particular place that can be inserted. */
internal data class SnippetInsertion(
    /** The snippet to insert. */
    val snippet: Snippet,
    override val location: SnippetInsertionLocation,
    override val range: IntRange,
    val replacedContent: DocContent,
    override val attributes: InsertionAttributeMap,
) : AbstractSnippetInsertion()

/**
 * Like a [SnippetInsertion] but indicates that a [snippet][SnippetInsertion.snippet] could not
 * be resolved from the path following [INSERTION_MARKER_CHAR].
 */
internal data class InvalidSnippetInsertion(
    val text: String,
    val problem: Throwable,
    override val location: SnippetInsertionLocation,
    override val range: IntRange,
    override val attributes: InsertionAttributeMap,
) : AbstractSnippetInsertion()

internal const val INSERTION_MARKER_CHAR = '⎀'
private val insertionRegex = Regex(
    """$INSERTION_MARKER_CHAR[ \t]*([^\u0000- ]+)(?:[ \t]+([^\n\r]*))?""",
)

internal enum class SnippetInsertionAttributeKey(
    val short: String,
    val defaultValue: JsonValue,
) {
    /** @see [SnippetInsertion.isCanonical] */
    IsCanonical("canon", JsonBoolean.valueFalse),

    /** @see [SnippetInsertion.anchor] */
    Anchor("anchor", JsonString("")),

    /**
     * Whether to strip any uniquely important heading should be removed from the snippet, usually
     * because it's redundant with a heading that precedes the insertion.
     */
    Heading("heading", JsonBoolean.valueTrue),

    ;

    companion object {
        fun fromString(key: String) =
            values().firstOrNull { it.short == key || it.name == key }
    }
}

/** General flag-like attributes for insertions. */
internal fun parseAttributesFrom(
    attributesText: String,
    onto: MutableInsertionAttributeMap,
    from: FilePath,
    context: String,
) {
    val tokens = Lexer(from, LogSink.devNull, attributesText)
    fun skip() {
        while (tokens.peek()?.tokenType?.ignorable == true) {
            tokens.next()
        }
    }

    while (true) {
        skip()
        if (!tokens.hasNext()) { break }
        var value: JsonValue? = null
        when (val prefix = tokens.peek()?.tokenText) {
            "-", "+" -> {
                // Handle:
                // -key
                // +key
                tokens.next()
                value = if (prefix == "-") {
                    JsonBoolean.valueFalse
                } else {
                    JsonBoolean.valueTrue
                }
                // Skip to where name should be.
                skip()
            }
        }
        val keyText = tokens.next().tokenText
        val key = SnippetInsertionAttributeKey.fromString(keyText)
            ?: error("Expected attribute key, not `$keyText` in $from: `$context`")
        if (key in onto) {
            error("Duplicate attribute $key in $from: `$context`")
        }
        if (value == null) {
            skip()
            value = if (tokens.peek()?.tokenText != "=") {
                JsonBoolean.valueTrue
            } else {
                tokens.next() // Over `=`
                skip()
                val valueText = toStringViaBuilder { sb ->
                    // [...] and {...} have to be whole.
                    val first = tokens.peek()
                    val firstText = first?.tokenText
                    var depth = 0
                    if (
                        firstText == "[" || firstText == "{" || first?.tokenType == TokenType.LeftDelimiter
                    ) {
                        depth += 1
                    }
                    if (first != null) {
                        sb.append(firstText)
                        tokens.next()

                        while (depth != 0 && tokens.hasNext()) {
                            skip()
                            val token = tokens.next()
                            sb.append(token.tokenText)
                            when (token.tokenText) {
                                "[", "{" -> depth += 1
                                "]", "}" -> depth -= 1
                                "\"" -> when (token.tokenType) {
                                    TokenType.LeftDelimiter -> depth += 1
                                    TokenType.RightDelimiter -> depth -= 1
                                    else -> Unit
                                }
                                else -> Unit
                            }
                        }
                    }
                }
                when (val valueResult = JsonValue.parse(valueText)) {
                    is RFailure -> error(
                        "Invalid value for $key in $from: `$context`: ${valueResult.failure.message}",
                    )
                    is RSuccess -> valueResult.result
                }
            }
        }

        onto[key] = value
    }
}

/**
 * Process snippet insertion syntax as described in
 * `temper/build-user-docs/skeletal-docs/README.md`
 */
private fun insertionFromMatchResult(
    /** A match whose groups correspond to those in [insertionRegex] */
    match: MatchResult,
    /** The location into which the insertion happens. */
    from: SnippetInsertionLocation,
    /** Diagnostic text. */
    context: String,
    /** The range of characters in [from] replaced by the insertion. */
    matchedStartOffset: Int,
    resolve: (String, FilePath, String) -> RResult<Snippet, IllegalArgumentException>,
    /** Default anchor text for the insertion */
    anchorMaker: (SnippetId) -> String?,
): AbstractSnippetInsertion {
    val replacementRange = (matchedStartOffset + match.range.first)..(matchedStartOffset + match.range.last)

    val resolution = resolve(match.groups[1]!!.value, from.diagnosticPath, context)
    val attributes = mutableMapOf<SnippetInsertionAttributeKey, JsonValue>()
    val attributesText = match.groups[2]?.value
    if (attributesText != null) {
        parseAttributesFrom(
            attributesText,
            onto = attributes,
            from = from.diagnosticPath,
            context = context,
        )
    }

    return when (resolution) {
        is RSuccess -> {
            val referent = resolution.result
            if (SnippetInsertionAttributeKey.Anchor !in attributes) {
                val anchor = anchorMaker(referent.id)
                if (anchor != null) {
                    attributes[SnippetInsertionAttributeKey.Anchor] =
                        JsonString(anchor)
                }
            }

            SnippetInsertion(
                snippet = referent,
                location = from,
                range = replacementRange,
                replacedContent = TextDocContent(match.value),
                attributes = attributes.toMap(),
            )
        }
        is RFailure -> {
            InvalidSnippetInsertion(
                text = match.value,
                problem = resolution.failure,
                location = from,
                range = replacementRange,
                attributes = attributes.toMap(),
            )
        }
    }
}

/** Walk the markdown AST looking for insertions. */
internal class InsertionScanner(
    private val loc: SnippetInsertionLocation,
    private val anchorFor: (SnippetId) -> String?,
    private val insertions: MutableCollection<AbstractSnippetInsertion>,
    private val resolve: (String, FilePath, String) -> RResult<Snippet, IllegalArgumentException> =
        Snippets::resolveShortId,
) {
    fun scanForInsertions(markdownContent: MarkdownContent) {
        markdownContent.root.accept(
            object : AbstractVisitor() {
                override fun visit(paragraph: Paragraph) {
                    val range = markdownContent.range(paragraph)
                    val text = markdownContent.fileContent.slice(range)
                    for (match in insertionRegex.findAll(text)) {
                        val ins = insertionFromMatchResult(
                            match = match,
                            from = loc,
                            context = text,
                            matchedStartOffset = range.first,
                            resolve = resolve,
                            anchorMaker = anchorFor,
                        )
                        insertions.add(ins)
                    }
                }
            },
        )
    }
}
