package lang.temper.testdir

import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.diff.Diff
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.common.putMultiList
import lang.temper.common.splitLinesPreservingTerminators
import lang.temper.log.FilePath

//

/**
 * Bundles enough information to take a file, relative to a test root directory,
 * convert its content to a JSON form that can be combined into one large test
 * golden along with the contributions of other files, and also to reverse that
 * process by regenerating test expectation files from the computed truth so that
 * large scale, mostly cosmetic changes to internal representations can be reviewed
 * via `git diff`.
 *
 * AssertModuleAtStage.kt is an example of how to use this to turn multiple small
 * files into test expectations and to enable opt-in regeneration for sweeping changes.
 */
data class TestResourceFileRelationship(
    val jsonProperties: List<String>,
    val relFilePath: FilePath,
    val converter: DataFileConverter,
)

/** Converts between test data file content and JSONValues in both directions. */
interface DataFileConverter {
    fun fromFileContent(content: String): RResult<JsonValue, Throwable>
    fun toFileContent(value: JsonValue, oldValue: JsonValue?): RResult<String, Throwable>
}

object FileContentStringConverter : DataFileConverter {
    private const val COMMENT_LINE_PREFIX = "##"
    private const val NON_COMMENT_IGNORED_PREFIX = "  "

    private fun shouldStripPrefixes(lines: Iterable<String>) = lines.any { it.startsWith(COMMENT_LINE_PREFIX) } &&
        lines.all {
            it.isBlank() ||
                it.startsWith(COMMENT_LINE_PREFIX) ||
                it.startsWith(NON_COMMENT_IGNORED_PREFIX)
        }

    private enum class LineClassification {
        COMMENT,
        STRIP_PREFIX,
        BLANK,

        ;

        fun transform(line: String): String =
            when (this) {
                COMMENT -> ""
                STRIP_PREFIX -> line.drop(NON_COMMENT_IGNORED_PREFIX.length)
                BLANK -> line
            }

        fun reverse(line: String): String =
            when (this) {
                COMMENT -> "$COMMENT_LINE_PREFIX\n"
                STRIP_PREFIX -> "$NON_COMMENT_IGNORED_PREFIX$line"
                BLANK -> line
            }
    }
    private fun classifyLine(line: String) = when {
        line.startsWith(NON_COMMENT_IGNORED_PREFIX) -> LineClassification.STRIP_PREFIX
        line.startsWith(COMMENT_LINE_PREFIX) -> LineClassification.COMMENT
        else -> LineClassification.BLANK // Assuming it passed the shouldStripPrefixes test above
    }

    override fun fromFileContent(content: String): RResult<JsonValue, Throwable> {
        var adjustedContent = content
        // If the file contains ## lines, and the rest are indented, remove the ## comments.
        val lines = content.splitLinesPreservingTerminators()
        if (shouldStripPrefixes(lines)) {
            adjustedContent = lines.joinToString("") {
                classifyLine(it).transform(it)
            }
        }
        return RSuccess(JsonString(adjustedContent))
    }

    override fun toFileContent(value: JsonValue, oldValue: JsonValue?): RResult<String, Throwable> {
        val newContent = (value as JsonString).s
        val oldContent = (oldValue as? JsonString)?.s
        var adjustedString: String? = null

        // If we have any `##` lines, then try to reinsert them in sensible places by diffing.
        if (oldContent != null) {
            val oldLines = oldContent.splitLinesPreservingTerminators().ensureTerminated()
            if (shouldStripPrefixes(oldLines)) {
                // Save the line numbers in oldLines after which `##` are preserved.
                val oldLinesStripped = mutableListOf<String>()
                val insertionPointsForComments = mutableMapOf<Int, MutableList<String>>()
                for (line in oldLines) {
                    when (val classification = classifyLine(line)) {
                        LineClassification.COMMENT ->
                            insertionPointsForComments
                                .putMultiList(oldLinesStripped.size, line)
                        LineClassification.STRIP_PREFIX,
                        LineClassification.BLANK,
                        -> oldLinesStripped.add(classification.transform(line))
                    }
                }

                val commentsOrdered = buildList {
                    insertionPointsForComments.map {
                        add(it.key to it.value.toList())
                    }
                }

                val newLines = newContent.splitLinesPreservingTerminators().ensureTerminated()

                val patch = Diff.differencesBetween(oldLinesStripped, newLines) { a, b -> a == b }
                val reconstructedLines = mutableListOf<String>()
                var oldCursor = 0 // indexes into oldLines
                var newCursor = 0 // indexes into newLines
                var commentCursor = 0 // indexes into commentsOrdered

                fun reconstructLine(line: String) {
                    val cl = when (line) {
                        "", "\n", "\r\n", "\r" -> LineClassification.BLANK
                        else -> LineClassification.STRIP_PREFIX
                    }
                    reconstructedLines.add(cl.reverse(line))
                }

                fun reconstructCommentsUpTo(limit: Int?) {
                    while (
                        commentCursor in commentsOrdered.indices &&
                        (limit == null || commentsOrdered[commentCursor].first < limit)
                    ) {
                        val (_, commentLines) = commentsOrdered[commentCursor]
                        commentCursor += 1
                        reconstructedLines.addAll(commentLines)
                    }
                }

                for (change in patch.changes) {
                    val (type, _, _, items) = change
                    when (type) {
                        Diff.ChangeType.Deletion -> {
                            oldCursor += items.size
                            reconstructCommentsUpTo(oldCursor)
                        }
                        Diff.ChangeType.Addition -> {
                            for (line in items) {
                                reconstructLine(line)
                            }
                            newCursor += items.size
                        }
                        Diff.ChangeType.Unchanged -> {
                            for (line in items) {
                                oldCursor += 1
                                newCursor += 1

                                reconstructCommentsUpTo(oldCursor)
                                reconstructLine(line)
                            }
                        }
                    }
                }
                reconstructCommentsUpTo(null)

                adjustedString = reconstructedLines.joinToString("")
            }
        }

        if (adjustedString == null && COMMENT_LINE_PREFIX in newContent) {
            // Having the double space prefix on on-comment lines avoids ambiguity.
            // If we organically get a generated content line that has `##`, avoid ambiguity
            // by indenting it and other lines and having one blank comment line at the start.
            //
            // foo
            // ##bar
            // baz
            //
            // ->
            //
            // ##
            //   foo
            //   ##bar
            //   baz
            val newLines = newContent.splitLinesPreservingTerminators().ensureTerminated()
            if (newLines.any { classifyLine(it) == LineClassification.COMMENT }) {
                adjustedString = newLines.joinToString("", prefix = "$COMMENT_LINE_PREFIX\n") { line ->
                    when (line) {
                        "", "\n", "\r\n", "\r" -> line
                        else -> "$NON_COMMENT_IGNORED_PREFIX$line"
                    }
                }
            }
        }

        return RResult.of(ClassCastException::class) { adjustedString ?: newContent }
    }
}

object ParseJsonTolerantConverter : DataFileConverter {
    override fun fromFileContent(content: String): RResult<JsonValue, Throwable> =
        JsonValue.parse(content, tolerant = true)

    override fun toFileContent(value: JsonValue, oldValue: JsonValue?): RResult<String, Throwable> =
        RSuccess(value.toJsonString(extensions = true))
}

private fun List<String>.ensureTerminated(): List<String> = mapIndexedNotNull { i, line ->
    when (line.lastOrNull()) {
        '\n', '\r' -> line
        null if i == lastIndex -> null
        else -> "$line\n"
    }
}
