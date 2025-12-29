package lang.temper.be

import lang.temper.common.LeftOrRight
import lang.temper.common.commonPrefixBy
import lang.temper.common.subListToEnd
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.log.Position

/**
 * A source map builder that receives events as an output is generated and squirrels away enough
 * information to generate a source map for that output.
 */
internal class SourceMapBuilder(
    val outPath: FilePath?,
    val lookupCodeLocation: (loc: CodeLocation) -> Pair<FilePath, FilePositions>?,
) {
    private val sources = mutableMapOf<FilePath, CodeLocation>()
    private val names = mutableSetOf<String>()
    private val outputLines = mutableListOf(mutableListOf<SourceMap.MappingSegment>())
    private val codeLocationCache = mutableMapOf<CodeLocation, Pair<FilePath, FilePositions>?>()

    private var pos: Position? = null

    private var outColumnZeroIndexed = 0

    fun position(pos: Position, side: LeftOrRight) {
        val newPos = when (side) {
            LeftOrRight.Left -> pos.leftEdge
            LeftOrRight.Right -> pos.rightEdge
        }
        val oldPos = this.pos
        // If we're shifting to a different source or backwards, drop a pin at the end.
        // TODO: Is there an easy way to detect a non-adjacent forward jump?
        if (
            oldPos != null &&
            (oldPos.loc != newPos.loc || oldPos.left > newPos.left)
        ) {
            val sourceAndPositions = sourceForLoc(oldPos.loc)
            if (sourceAndPositions != null) {
                val (source, positions) = sourceAndPositions
                // Drop a pin after the segment if there's output.
                val lastLine = outputLines.last()
                val lastSegment = lastLine.lastOrNull()
                if (
                    lastSegment != null && lastSegment.outputStartColumn < outColumnZeroIndexed &&
                    lastSegment.source == source
                ) {
                    val p = positions.filePositionAtOffset(oldPos.left)
                    lastLine.add(
                        SourceMap.MappingSegment(
                            outputStartColumn = outColumnZeroIndexed,
                            source = source,
                            sourceStartLine = p.line - 1, // zero index
                            sourceStartColumn = p.charInLine,
                            name = null,
                        ),
                    )
                }
            }
        }
        this.pos = newPos
    }

    fun wroteChars(n: Int) {
        addSegment(n, null)
    }

    fun wroteName(outputNameLength: Int, sourceName: String) {
        addSegment(outputNameLength, sourceName)
    }

    fun lineEnded() {
        outColumnZeroIndexed = 0
        outputLines.add(mutableListOf())
    }

    private fun sourceForLoc(loc: CodeLocation): Pair<FilePath, FilePositions>? =
        codeLocationCache.getOrPut(loc) {
            lookupCodeLocation(loc)?.let { (sourcePath, positions) ->
                if (sourcePath !in sources) {
                    sources[sourcePath] = loc
                }
                sourcePath to positions
            }
        }

    private fun addSegment(segmentLength: Int, sourceIdentifier: String?) {
        if (segmentLength == 0) {
            return
        }
        var sourceStartLine = 0
        var sourceStartColumn = 0
        val pos = this.pos
        val loc = pos?.loc

        var source: FilePath? = null
        if (loc != null) {
            when (val p = sourceForLoc(loc)) {
                null -> {}
                else -> {
                    source = p.first
                    val fp = p.second.filePositionAtOffset(pos.left)
                    sourceStartLine = fp.line - 1 // Convert from one-indexed to zero-indexed.
                    sourceStartColumn = fp.charInLine
                }
            }
        }

        val name = if (sourceIdentifier != null) {
            names.add(sourceIdentifier)
            sourceIdentifier
        } else {
            null
        }

        val outputStartColumn = outColumnZeroIndexed
        outColumnZeroIndexed += segmentLength

        val segments = outputLines.last()
        val lastIndex = segments.size - 1
        if (name == null && lastIndex >= 0) {
            val last = segments[lastIndex]
            if (
                last.name == null && last.source == source &&
                sourceStartLine == last.sourceStartLine &&
                sourceStartColumn == last.sourceStartColumn
            ) {
                return
            }
        }
        segments.add(
            SourceMap.MappingSegment(
                outputStartColumn = outputStartColumn,
                source = source,
                sourceStartLine = sourceStartLine,
                sourceStartColumn = sourceStartColumn,
                name = name,
            ),
        )
    }

    fun build(contentFor: (loc: CodeLocation) -> String?): SourceMap {
        val sourceList = sources.keys.toList()
        val nameList = names.toList()

        var sourcesContent: List<String?>? = sourceList.map { contentFor(sources.getValue(it)) }
        if (sourcesContent != null && sourcesContent.all { it == null }) {
            sourcesContent = null
        }

        var sourcePathSegments = commonPrefixBy(sourceList) { it.segments }
        // If we match an entire input, then back off to a directory.  We want a strict prefix.
        if (
            sourcePathSegments.isNotEmpty() &&
            sourceList.any { it.segments.size == sourcePathSegments.size }
        ) {
            sourcePathSegments = sourcePathSegments.subList(0, sourcePathSegments.size - 1)
        }

        val nSourcePathSegments = sourcePathSegments.size
        val (sourceRoot, sourceListTrimmed) =
            // Don't bother with a sourceRoot for just one file.
            if (nSourcePathSegments != 0 && sourceList.size > 1) {
                FilePath(sourcePathSegments, true) to
                    sourceList.map {
                        FilePath(it.segments.subListToEnd(nSourcePathSegments), it.isDir)
                    }
            } else {
                null to sourceList
            }
        val mappings = SourceMap.Mappings(
            this.outputLines.mapIndexedNotNull { i, segments ->
                if (segments.isEmpty() && i + 1 == this.outputLines.size) {
                    null // Skip empty last line
                } else {
                    SourceMap.MappingGroup(segments.toList())
                }
            },
        )

        return SourceMap(
            file = outPath,
            sourceRoot = sourceRoot,
            sources = sourceListTrimmed,
            sourcesContent = sourcesContent,
            names = nameList,
            mappings = mappings,
        )
    }
}
