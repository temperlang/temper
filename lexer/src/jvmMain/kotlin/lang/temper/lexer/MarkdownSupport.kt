package lang.temper.lexer

import lang.temper.common.ignore
import lang.temper.log.FilePosition
import lang.temper.log.FilePositions
import lang.temper.log.UnknownCodeLocation
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Paragraph
import org.commonmark.node.SourceSpan
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

actual fun findMarkdownCodeBlocks(text: String): List<TaggedRange> {
    val positions = FilePositions.fromSource(UnknownCodeLocation, text)
    val parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build()
    val root = parser.parse(text)
    return buildList {
        root.accept(
            object : AbstractVisitor() {
                override fun visit(node: FencedCodeBlock) = ignore(add(node.taggedRange(positions, text)))
                override fun visit(node: IndentedCodeBlock) = ignore(add(node.taggedRange(positions, text)))
                override fun visit(node: Paragraph) = ignore(add(node.taggedRange(positions, text)))
            },
        )
    }
}

/** Translate also 1-numbered lines to 0-indexed. */
fun SourceSpan.startPos() = FilePosition(lineIndex + 1, columnIndex)

fun SourceSpan.toIndex(positions: FilePositions): Int {
    return positions.offsetAtFilePosition(startPos())!!
}

private fun pastNewline(index: Int, text: String): Int {
    // For local use, we expect to be at `\r` or `\n` already, but generalized also works.
    var end = index
    while (end < text.length && text[end - 1] != '\n') {
        end += 1
    }
    return end
}

private fun pastPriorNewline(index: Int, text: String): Int {
    // For local use, we might need to backtrack across indentation.
    var begin = index
    while (begin >= 1 && text[begin - 1] != '\n') {
        begin -= 1
    }
    return begin
}

private fun buildTaggedRange(
    tags: List<String>?,
    positions: FilePositions,
    text: String,
    sourceSpans: List<SourceSpan>,
    beginEmpty: Boolean,
    endEmpty: Boolean,
): TaggedRange {
    val beginSpan = sourceSpans.first()
    val beginIndex = beginSpan.toIndex(positions)
    val beginStart = pastPriorNewline(beginIndex, text)
    val beginEnd = beginIndex + when (beginEmpty) {
        true -> 0
        false -> beginSpan.length
    }
    val endSpan = sourceSpans.last()
    val endIndex = endSpan.toIndex(positions)
    val end = when (endEmpty) {
        true -> pastNewline(endIndex + endSpan.length, text).let { it until it }
        false -> pastPriorNewline(endIndex, text) until endIndex + endSpan.length
    }
    return TaggedRange(tags, begin = beginStart until beginEnd, end = end)
}

private val whitespace = Regex("""\s+""")

private fun FencedCodeBlock.taggedRange(positions: FilePositions, text: String): TaggedRange {
    // Check the hard way for unfinished at end. I couldn't find an easy way.
    var endEmpty = false
    val last = sourceSpans.last()
    val lastBegin = last.toIndex(positions)
    if (pastNewline(lastBegin + last.length, text) == text.length) {
        // See if this code block is unfinished.
        val lastContent = text.substring(lastBegin, lastBegin + last.length).trim()
        if (!(lastContent.length == fenceLength && lastContent.all { it == fenceChar })) {
            // Unfinished, so include the last span.
            endEmpty = true
        }
    }
    // Split info for tags, which I'm not sure needs trimmed or not.
    val tags = info.trim().let { info ->
        when (info.isEmpty()) {
            true -> emptyList() // Would split to a single empty string.
            false -> info.split(whitespace)
        }
    }
    return buildTaggedRange(tags, positions, text, sourceSpans, beginEmpty = false, endEmpty)
}

private fun IndentedCodeBlock.taggedRange(positions: FilePositions, text: String): TaggedRange {
    return buildTaggedRange(emptyList(), positions, text, sourceSpans, beginEmpty = true, endEmpty = true)
}

private fun Paragraph.taggedRange(positions: FilePositions, text: String): TaggedRange {
    return buildTaggedRange(null, positions, text, sourceSpans, beginEmpty = true, endEmpty = true)
}
