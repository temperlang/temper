package lang.temper.lexer

import lang.temper.common.asciiLowerCase
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.FilePathSegmentOrPseudoSegment

/**
 * Match methods return the character after the last character consumed by the
 * match that starts at *pos* in *text*; or -1 if no such match.
 */
interface LanguageConfig {
    /**
     * True if the lexer should start in a semilit comment context and look for an exit
     * before the first content tokens.
     */
    val isSemilit: Boolean

    fun matchSemilitCommentEntrance(text: CharSequence, pos: Int): Int
    fun matchSemilitCommentExit(text: CharSequence, pos: Int): Int

    /**
     * When a semilit comment is found, called with its text range.
     *
     * May produce a list of substrings that will be converted into *synthetic* comment tokens like:
     *
     *     /**?
     *      * LINES OF STRING
     *      */
     *
     * These may be optionally incorporated by later stages based on textual cues as javadoc for definitions.
     *
     * For example, given that the name `f` is defined here:
     *
     *     let f(...) { ... }
     *
     * If there is a preceding non-`/**?*/` comment preceding it, that's the doc comment for that definition,
     * but if there isn't and there is one like `/**? f is a function that is nice */` we include that and
     * following paragraphs.
     *
     * For this to work, implementations should split semilit comments into paragraphs based on the local
     * language convention.
     *
     * This allows for literate style association of paragraphs in markdown with definitions.
     *
     * @return ranges into text between start and end in order of paragraphs
     */
    fun massageSemilitComment(text: CharSequence, start: Int, end: Int): Iterable<IntRange> = emptyList()
}

class MarkdownLanguageConfig : LanguageConfig {
    // March code ranges forward only.
    private var _codeRanges: List<TaggedRange>? = null
    private var codeRangeIndex = 0

    override val isSemilit get() = true

    private fun codeRanges(text: CharSequence): List<TaggedRange> =
        _codeRanges ?: findMarkdownCodeBlocks(text.toString()).also {
            _codeRanges = it
        }

    override fun matchSemilitCommentEntrance(text: CharSequence, pos: Int): Int {
        // We shouldn't be able to match comment entrance (code end) until we've seen a comment exit (code begin).
        // We'll have reviewed all the text in advance on the first call to check for comment exit (code begin).
        codeRanges(text).getOrNull(codeRangeIndex)?.let { range ->
            if (range.end.first == pos) {
                return range.end.last + 1
            }
        }
        return -1
    }

    override fun matchSemilitCommentExit(text: CharSequence, pos: Int): Int {
        // Find what range we're on.
        val ranges = codeRanges(text)
        var range = ranges.getOrNull(codeRangeIndex)
        while (range != null && range.begin.first < pos) {
            codeRangeIndex += 1
            range = ranges.getOrNull(codeRangeIndex)
        }
        // See if it's an active code range. If so, we exit the semilit comment.
        if (range?.begin?.first == pos) {
            val tags = range.tags
            if (tags != null && (tags.isEmpty() || (tags[0] == "temper" && INERT_TAG !in tags))) {
                return range.begin.last + 1
            }
        }
        return -1
    }

    override fun massageSemilitComment(text: CharSequence, start: Int, end: Int): Iterable<IntRange> {
        val ranges = codeRanges(text)
        val left = ranges.indexOfFirst { it.begin.first >= start }
        if (left < 0) { return emptyList() }
        return buildList {
            for (i in left until ranges.size) {
                val range = ranges[i]
                if (range.end.last >= end) {
                    break
                }
                if (range.tags == null) {
                    add((range.begin.endInclusive + 1) until range.end.first)
                }
            }
        }
    }

    companion object {
        const val INERT_TAG = "inert"
    }
}

data class TaggedRange(
    /**
     * Such as `[]`, `["temper"]`, or `["temper", "inert"]`.
     *
     * An untagged code section will have the empty list.
     *
     * Normal paragraph content will have a null tag list.
     */
    val tags: List<String>?,

    /** The range for the beginning delimiter, possibly empty. */
    val begin: IntRange,

    /** The range for the ending delimiter, possibly empty. */
    val end: IntRange,
)

/** All begin and end ranges are expected to begin and end at line starts. */
expect fun findMarkdownCodeBlocks(text: String): List<TaggedRange>

object StandaloneLanguageConfig : LanguageConfig {
    override val isSemilit get() = false
    override fun matchSemilitCommentEntrance(text: CharSequence, pos: Int) = -1
    override fun matchSemilitCommentExit(text: CharSequence, pos: Int) = -1
}

/**
 * Heuristic that looks at a file extension (with the leading ".") and picks a language config.
 */
fun languageConfigForExtension(extension: String?) = when (extension) {
    ".md" -> MarkdownLanguageConfig()
    else -> StandaloneLanguageConfig
}

/** Combine heuristics for Temper and embedding language configuration. */
fun defaultClassifyTemperSource(fileName: FilePathSegment): LanguageConfig? =
    if (fileName.isTemperFile) {
        languageConfigForExtension(fileName.extension)
    } else {
        null
    }

/**
 * Returns the index of the dot where the Temper file extension starts, such as at ".temper.md",
 * or -1 for no match. This function never returns index 0, as extensions can't start there.
 */
fun temperExtensionIndex(baseNameWithExtension: String): Int {
    // Some file-systems (default on macOS) are case-insensitive.
    val normalized = baseNameWithExtension.asciiLowerCase()

    val lastDot = normalized.lastIndexOf(".")
    if (lastDot <= 0) {
        // Not an extension if "." makes file hidden per Unix convention.
        return -1
    }
    if (normalized.endsWith(TEMPER_FILE_EXTENSION)) {
        // A standalone file.
        return lastDot
    }

    // startIndex = lastDot - 1 is safe because of <= check above
    val secondToLastDot = normalized.lastIndexOf(".", startIndex = lastDot - 1)
    if (secondToLastDot <= 0) {
        return -1
    }
    return if (
        lastDot - secondToLastDot == TEMPER_FILE_EXTENSION.length &&
        normalized.regionMatches(
            secondToLastDot,
            TEMPER_FILE_EXTENSION,
            0,
            TEMPER_FILE_EXTENSION.length,
        )
    ) {
        // foo.temper.html is a Temper file, but foo.temper.somethingWeDoNotRecognize is not
        val ext = normalized.substring(lastDot)
        when (languageConfigForExtension(ext)) {
            StandaloneLanguageConfig -> -1
            else -> secondToLastDot
        }
    } else {
        -1
    }
}

/**
 * True if the file-name suffix indicates it is a Temper source file.
 *
 * If this changes then also change semanticTokensProviderOptions in
 * //langserver/**/SemanticTokensProviderOptions.kt
 */
fun isTemperFile(baseNameWithExtension: String) = temperExtensionIndex(baseNameWithExtension) != -1

/** Returns a pair of (base name, extension) keeping temper extensions full. */
fun temperSensitiveBaseNameAndExtension(name: String): Pair<String, String> {
    val extensionIndex = when (val temperIndex = temperExtensionIndex(name)) {
        -1 -> when (val anyIndex = name.lastIndexOf('.')) {
            -1, 0 -> name.length
            else -> anyIndex
        }
        else -> temperIndex
    }
    return name.substring(0, extensionIndex) to name.substring(extensionIndex)
}

fun FilePathSegment.temperAwareBaseName(): String {
    val temperIndex = temperExtensionIndex(fullName)
    return when {
        temperIndex < 0 -> baseName
        else -> fullName.substring(0, temperIndex)
    }
}

/** A new file path segment with the Temper-aware extension changed. */
fun FilePathSegment.withTemperAwareExtension(extension: String): FilePathSegment {
    val temperIndex = temperExtensionIndex(fullName)
    return if (temperIndex > 0) {
        val baseName = fullName.substring(0, temperIndex)
        FilePathSegment("$baseName$extension")
    } else {
        withExtension(extension)
    }
}
fun FilePath.withTemperAwareExtension(extension: String): FilePath {
    if (segments.isEmpty()) return this
    return dirName().resolve(segments.last().withTemperAwareExtension(extension), isDir = false)
}
fun List<FilePathSegmentOrPseudoSegment>.withTemperAwareExtension(
    extension: String,
): List<FilePathSegmentOrPseudoSegment> =
    if (isEmpty()) {
        this
    } else {
        val lastIndex = this.lastIndex
        val segments = this.toMutableList()
        val last = segments[lastIndex]
        if (last is FilePathSegment) {
            segments[lastIndex] = last.withTemperAwareExtension(extension)
        }
        segments.toList()
    }

/**
 * True when the given file path is a Temper file based on naming conventions.
 */
val FilePath.isTemperFile: Boolean
    get() = this.lastOrNull()?.isTemperFile == true

/**
 * True when the given file path is a Temper file based on naming conventions.
 */
val FilePathSegment.isTemperFile: Boolean
    get() = isTemperFile(this.fullName)

/**
 * The file extension (including dot) for a standalone Temper file.
 * This same file extension is used as the second to last extension for embedded Temper files.
 */
const val TEMPER_FILE_EXTENSION = ".temper"
