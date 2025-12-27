package lang.temper.be

/**
 * Pair chunks of an input file with chunks of an output file based on a source map.
 */
fun pairChunks(
    source: String,
    output: String,
    sourceMap: SourceMap,
): List<List<String>> {
    // Pair output code segments with source segments.
    val pairedChunks = mutableListOf<List<String>>()
    run {
        val sourceLines = splitAfterLineBreaks(source)
        val outputLines = splitAfterLineBreaks(output)
        sourceMap.mappings.lines.forEachIndexed { outputLineNum, group ->
            val outputLine = outputLines[outputLineNum]
            val segments = group.segments
            val nSegments = segments.size
            for (segmentIndex in segments.indices) {
                val segment = segments[segmentIndex]
                val nextSegment = if (segmentIndex + 1 < nSegments) {
                    segments[segmentIndex + 1]
                } else {
                    null
                }

                val outStartCol = segment.outputStartColumn
                val outEndCol = nextSegment?.outputStartColumn ?: outputLine.length
                val outCode = outputLine.substring(outStartCol, outEndCol)

                val sourceCodeBuf = StringBuilder()
                if (
                    segment.source != null && nextSegment != null &&
                    nextSegment.source == segment.source
                ) {
                    var col = segment.sourceStartColumn
                    for (sourceLine in segment.sourceStartLine..nextSegment.sourceStartLine) {
                        val left = col
                        val right = if (sourceLine == nextSegment.sourceStartLine) {
                            nextSegment.sourceStartColumn
                        } else {
                            sourceLines[sourceLine].length
                        }
                        if (left <= right) {
                            sourceCodeBuf.append(sourceLines[sourceLine], left, right)
                        }
                        col = 0
                    }
                }

                val sourceCode = "$sourceCodeBuf"
                if (outCode.isNotEmpty() || sourceCode.isNotEmpty()) {
                    pairedChunks.add(listOf(outCode, sourceCode))
                }
            }
        }
    }
    return pairedChunks.toList()
}

private fun splitAfterLineBreaks(s: String): List<String> {
    val lines = mutableListOf<String>()
    var off = 0
    for (i in s.indices) {
        if (s[i] == '\n') {
            lines.add(s.substring(off, i + 1))
            off = i + 1
        }
    }
    lines.add(s.substring(off))
    return lines.toList()
}
