package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.RResult
import lang.temper.lexer.LINK_FOUND_DEFINITION_TITLE
import lang.temper.lexer.LINK_MISSING_DEFINITION_TITLE
import lang.temper.lexer.LanguageConfig
import lang.temper.lexer.Lexer
import lang.temper.log.FilePath
import lang.temper.log.FilePosition
import lang.temper.log.LogSink
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.node.SourceSpan
import org.jetbrains.kotlin.lexer.KotlinLexer

/** Extracts [Snippet]s from source files, and or other snippets. */
internal abstract class SnippetExtractor {
    /** Called to extract snippets from a source file. */
    open fun extractSnippets(
        from: FilePath,
        content: DocSourceContent,
        mimeType: MimeType,
        onto: MutableCollection<Snippet>,
    ) {
        // Override to do a thing
    }

    /**
     * Called to extract snippets from snippets.
     * This is distinct, because it allows replacing an extracted snippet with an insertion of
     * a derived snippet, for example, decorating an extracted Temper code block with information
     * about what running it produces.
     */
    open fun extractNestedSnippets(
        from: Snippet,
        onto: MutableCollection<Snippet>,
    ) {
        // Override to do a thing
    }

    internal abstract fun backPortInsertion(
        inserted: Snippet,
        priorInsertion: TextDocContent?,
        readInlined: () -> TextDocContent,
    ): RResult<TextDocContent, IllegalStateException>

    /**
     * Takes changes to a snippet and re-incorporates them back into
     * the source content stored in [into].
     *
     * @return true if the back-porting took effect.
     */
    internal abstract fun backPortSnippetChange(
        snippet: Snippet,
        newContent: MarkdownContent,
        into: StringBuilder,
        problemTracker: ProblemTracker,
    ): Boolean

    internal open val supportsBackPorting: Boolean get() = true
}

sealed interface DocSourceContent

internal data class TextSourceContent(
    val content: String,
) : DocSourceContent

class MarkdownContent(
    fileContent: String,
) : lang.temper.lexer.MarkdownContent(fileContent), DocSourceContent

internal open class LinkUnifyingVisitor(private val markdownContent: MarkdownContent) : AbstractVisitor() {
    override fun visit(image: Image) {
        // Make images look like links to match existing behavior.
        val link = org.commonmark.node.Link(image.destination, image.title)
        // Presume the image starts with `!`, and we want to skip that to match link content ranges.
        // For now, check some basic presumptions about the image node.
        check(image.sourceSpans.size == 1 && image.sourceSpans.first().length > 0)
        val span = image.sourceSpans.first()
        link.sourceSpans = listOf(SourceSpan.of(span.lineIndex, span.columnIndex + 1, span.length - 1))
        visit(link, image)
    }

    override fun visit(link: org.commonmark.node.Link) {
        visit(link, link)
    }

    private fun visit(link: org.commonmark.node.Link, kidParent: Node) {
        val whole = markdownContent.range(link)
        val subRange = markdownContent.subRange(kidParent)
        val (kind, linked, locallyDefined) = when (link.title) {
            LINK_FOUND_DEFINITION_TITLE, LINK_MISSING_DEFINITION_TITLE -> Triple(
                Link.Kind.SquareBracketed,
                when {
                    // If we have more text than "[$text]", we must have a long form.
                    whole.last - whole.first > subRange.last - subRange.first + 2 -> subRange
                    else -> null
                },
                link.title != LINK_MISSING_DEFINITION_TITLE,
            )
            else -> Triple(Link.Kind.Parenthetical, subRange, true)
        }
        val target = link.destination
        visit(Link(whole = whole, linked = linked, kind = kind, target = target, locallyDefined = locallyDefined))
    }

    open fun visit(link: Link<IntRange>) {}
}

fun SourceSpan.startPos() = FilePosition(lineIndex + 1, columnIndex)

data class KotlinToken(
    val type: KotlinTokenType,
    val text: String,
    val sourceRange: IntRange,
) {
    val isCommentToken get() = when (type) {
        KotlinTokenType.KDoc -> true
        KotlinTokenType.EOL_COMMENT -> true
        else -> false
    }
}

@Suppress("EnumEntryName", "SpellCheckingInspection", "EnumNaming")
enum class KotlinTokenType {
    `package`,
    `fun`,
    `val`,
    `var`,
    WHITE_SPACE,
    IDENTIFIER,
    LPAR,
    RPAR,
    DOT,
    COLON,
    SEMICOLON,
    EQ,
    KDoc,
    EOL_COMMENT,
    OPEN_QUOTE,
    REGULAR_STRING_PART,
    CLOSING_QUOTE,
    INTEGER_LITERAL,
    Unknown,
    ;

    companion object {
        val byName = entries.associateBy { it.name }
    }
}

internal data class KotlinContent(
    val fileContent: String,
) : DocSourceContent {
    private var _tokens: List<KotlinToken>? = null
    val tokens get() = computeTokensList()

    @Suppress("UnstableApiUsage")
    private fun computeTokensList(): List<KotlinToken> {
        var tokens = _tokens
        if (tokens == null) {
            val lexer = KotlinLexer()
            lexer.start(fileContent)
            tokens = buildList {
                while (true) {
                    val tokenType = KotlinTokenType.byName[lexer.tokenType?.debugName ?: break]
                        ?: KotlinTokenType.Unknown
                    val tokenText = lexer.tokenText
                    val pos = lexer.tokenStart until lexer.tokenEnd
                    val token = KotlinToken(tokenType, tokenText, pos)
                    add(token)
                    lexer.advance()
                }
            }
            _tokens = tokens
        }
        return tokens
    }
}

internal data class TemperContent(
    val source: FilePath,
    val fileContent: String,
    val config: LanguageConfig,
) : DocSourceContent {
    fun lexer(): Lexer = Lexer(source, LogSink.devNull, fileContent, lang = config)
}

/**
 * Represents source files whose content has been removed.
 * UpdateGeneratedDocs may remove files as does `git rm`, but `git ls-files` still
 * lists them as source files.
 */
internal object MissingContent : DocSourceContent
