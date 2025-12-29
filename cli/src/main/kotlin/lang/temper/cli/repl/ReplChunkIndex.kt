package lang.temper.cli.repl

import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.lexer.TEMPER_FILE_EXTENSION
import lang.temper.library.LibraryConfiguration
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import kotlin.math.max

/**
 * Each time the user executes a chunk of Temper code, we bump a counter.
 * This class relates a chunk index to a virtual file used as the basis
 * for parsing, interpreting, and translating that chunk.
 *
 * The virtual files look like:
 *
 * ```
 *     -repl//
 *          i0000/
 *          i0001/
 *          ...
 *          i0123/
 * ```
 *
 * Each of these files is in a different library so the i### directories
 * are library roots.
 *
 * Having them in separate libraries lets us translate chunks in isolation.
 */
internal data class ReplChunkIndex(
    val index: Int,
) : TokenSerializable, Comparable<ReplChunkIndex> {

    val moduleName = ModuleName(
        FilePath(
            buildList {
                addAll(libraryConfiguration.libraryRoot.segments)
                add(
                    // i0000 is a valid identifier in most languages.
                    // Since we pad to four digits (for REPL sessions with fewer
                    // than 10000 entries) this file path segment's text sorts
                    // lexicographically consistently with index's int sort order.
                    FilePathSegment(
                        buildString {
                            append(INDEX_SEGMENT_PREFIX)
                            padIntTo(index, INDEX_DIGIT_COUNT, this)
                        },
                    ),
                )
            },
            isDir = true,
        ),
        libraryRootSegmentCount = libraryConfiguration.libraryRoot.segments.size,
        isPreface = false,
    )

    val filePath = moduleName.sourceFile.resolve(chunkFilePathSegment, isDir = false)

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken("$this", OutputTokenType.Name))
    }

    override fun toString(): String = "$REPL_LOC_PREFIX$index"
    override fun compareTo(other: ReplChunkIndex): Int =
        this.index.compareTo(other.index)

    companion object {
        private val replFilePathSegment = FilePathSegment("-repl")
        val chunkFilePathSegment = FilePathSegment("chunk$TEMPER_FILE_EXTENSION")
        private const val INDEX_SEGMENT_PREFIX = "i" // short for interactive
        private const val INDEX_DIGIT_COUNT = 4
        private const val LIBRARY_ROOT_SEGMENT_COUNT = 1 // -repl//i0123/

        val libraryConfiguration = LibraryConfiguration(
            libraryName = DashedIdentifier(
                buildString {
                    append("interactive")
                },
            ),
            libraryRoot = FilePath(listOf(replFilePathSegment), isDir = true),
            supportedBackendList = emptyList(), // tentative
            classifyTemperSource = { StandaloneLanguageConfig },
        )

        /**
         * *x* such that *x*.[moduleName][ReplChunkIndex.moduleName] == [loc]
         * or *x* such that *x*.[filePath] == [loc]
         * or `null` otherwise
         */
        fun from(loc: CodeLocation?): ReplChunkIndex? {
            val dirPath: FilePath? = when (loc) {
                is ModuleName -> if (loc.libraryRootSegmentCount == LIBRARY_ROOT_SEGMENT_COUNT) {
                    loc.sourceFile
                } else {
                    null
                }
                is FilePath -> if (loc.isFile && loc.lastOrNull() == chunkFilePathSegment) {
                    loc.dirName()
                } else {
                    null
                }
                else -> null
            }
            if (dirPath?.isDir == true) {
                val segments = dirPath.segments
                if (
                    segments.size == LIBRARY_ROOT_SEGMENT_COUNT + 1 &&
                    segments.first() == replFilePathSegment
                ) {
                    val indexSegment = segments[1]
                    val indexSegmentText = indexSegment.fullName
                    if (
                        indexSegmentText.startsWith(INDEX_SEGMENT_PREFIX) &&
                        indexSegmentText.length >= (INDEX_SEGMENT_PREFIX.length + INDEX_DIGIT_COUNT)
                    ) {
                        val indexChars = indexSegmentText.substring(INDEX_SEGMENT_PREFIX.length)
                        try {
                            return ReplChunkIndex(indexChars.toInt(radix = 10))
                        } catch (_: NumberFormatException) {
                            // return null below
                        }
                    }
                }
            }
            return null
        }
    }
}

private fun padIntTo(intToAppend: Int, minChars: Int, out: StringBuilder) {
    var longVal = intToAppend.toLong()
    var nAppendedNotPad = 0
    if (longVal < 0) {
        longVal = -longVal
        out.append('-')
        nAppendedNotPad += 1
    }
    val lengthBeforePad = out.length
    val nPad = minChars - 1 // appending longVal will append at least 1
    repeat(nPad) {
        out.append('0')
    }
    out.append(longVal)

    // Assuming minChars = 7
    //
    //     before  beforePad     beforeNum   after
    //       v        v            v           v
    // "foo"     "-"     "000000"    "123"
    // before    sign      pad        longVal
    //
    // nAppendedNotPad = 1 for "-" + 3 for "123" = 4
    // nPad = 6

    val nPadNeeded = max(0, minChars - nAppendedNotPad)
    val nPadToRemove = nPad - nPadNeeded
    if (nPadToRemove > 0) {
        out.replace(lengthBeforePad, lengthBeforePad + nPadToRemove, "")
    }
}
