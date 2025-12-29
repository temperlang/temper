package lang.temper.be

import lang.temper.common.MimeType
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import lang.temper.log.FilePath

// https://docs.google.com/document/d/1U1RGAehQwRypUTovF1KRlpiOFze0b-_2gc6fAH0KY0k/edit
private const val SOURCE_MAP_VERSION_NUMBER = 3

/**
 * https://www.bugsnag.com/blog/source-maps
 */
class SourceMap(
    val file: FilePath?,
    val sourceRoot: FilePath?,
    val sources: List<FilePath>,
    val sourcesContent: List<String?>?,
    /** Identifiers changed or removed from the output. */
    val names: List<String>?,
    val mappings: Mappings,
) : Structured {
    override fun destructure(structureSink: StructureSink) = destructure(
        structureSink,
        false,
    )

    /**
     * @param specifiedBitsOnly True to leave off diagnostic bits that are not part of the
     *     source-map JSON file format.
     */
    fun destructure(structureSink: StructureSink, specifiedBitsOnly: Boolean) = structureSink.obj {
        // If you find yourself debugging this,
        // `node.js --enable-source-maps` is a good command-line way to see if an error is remapped.
        // It swallows errors silently, and https://stackoverflow.com/a/57735193/20394 pointed me
        // at something that fails more vocally on malformed source maps.

        key("version", Hints.u) { value(SOURCE_MAP_VERSION_NUMBER) }
        if (file != null) {
            key("file") { value(file) }
        }
        if (sourceRoot != null) {
            key("sourceRoot") { value(sourceRoot.toString()) }
        }
        key("sources") {
            arr {
                for (source in sources) { value(source.toString()) }
            }
        }
        if (sourcesContent != null) {
            key("sourcesContent", Hints.u) {
                arr {
                    for (sourceContent in sourcesContent) {
                        value(sourceContent)
                    }
                }
            }
        }
        if (names != null) {
            key("names") {
                arr {
                    for (name in names) {
                        value(name)
                    }
                }
            }
        }
        key("mappings") {
            if (specifiedBitsOnly) {
                value(mappings.encode(this@SourceMap))
            } else {
                obj {
                    key("lines") {
                        arr {
                            for (line in mappings.lines) {
                                arr {
                                    for (segment in line.segments) {
                                        value(segment)
                                    }
                                }
                            }
                        }
                    }
                    key("encoded", Hints.su) {
                        value(mappings.encode(this@SourceMap))
                    }
                }
            }
        }
    }

    /**
     * The “mappings” data is broken down as follows:
     * - each group representing a line in the generated file is separated by a ”;”
     * - each segment is separated by a “,”
     * - each segment is made up of 1,4 or 5 variable length fields.
     */
    class Mappings(val lines: List<MappingGroup>) {
        @Suppress("MagicNumber") // The spec has field numbers.
        fun encode(context: SourceMap): String = toStringViaBuilder {
            // We need to keep context so we can properly VLQ encode
            val prior = intArrayOf(0, 0, 0, 0, 0)
            val sources = context.sources
            val names = context.names ?: emptyList()

            for (j in lines.indices) {
                if (j != 0) {
                    it.append(';')
                }
                val group = lines[j]

                prior[0] = 0 // The zero-th field is per line, not global like the others
                for (i in group.segments.indices) {
                    if (i != 0) {
                        it.append(',')
                    }
                    val segment = group.segments[i]
                    val field0 = segment.outputStartColumn - prior[0]

                    val source = segment.source
                    var field1 = if (source != null) {
                        sources.indexOf(source)
                    } else {
                        null
                    }
                    if (field1 != null && field1 >= 0) { field1 -= prior[1] }

                    val field2 = if (source != null) {
                        segment.sourceStartLine - prior[2]
                    } else {
                        null
                    }

                    val field3 = if (source != null) {
                        segment.sourceStartColumn - prior[3]
                    } else {
                        null
                    }

                    val name = segment.name
                    var field4 = if (name != null) {
                        names.indexOf(name)
                    } else {
                        null
                    }
                    if (field4 != null && field4 >= 0) { field4 -= prior[4] }

                    // Per the spec, encode 1, 4, or 5 numbers depending on what's available
                    val fieldNums = when {
                        field1 == null -> intArrayOf(field0)
                        field4 == null -> intArrayOf(field0, field1, field2!!, field3!!)
                        else -> intArrayOf(field0, field1, field2!!, field3!!, field4)
                    }

                    base64VlqEncode(fieldNums, it)
                    // Record context for subsequent records
                    for (ni in fieldNums.indices) {
                        prior[ni] += fieldNums[ni]
                    }
                }
            }
        }
    }
    class MappingGroup(val segments: List<MappingSegment>)
    class MappingSegment(
        /**
         * The zero-based starting column of the line in the generated code.
         * This is absolute, but on conversion to a VLQ will be adjusted as below based on context.
         *
         * > The zero-based starting column of the line in the generated code that the segment
         * > represents. If this is the first field of the first segment, or the first segment
         * > following a new generated line (“;”), then this field holds the whole base 64 VLQ.
         * > Otherwise, this field contains a base 64 VLQ that is relative to the previous
         * > occurrence of this field. Note that this is different than the fields below because the
         * > previous value is reset after every generated line.
         */
        val outputStartColumn: Int,
        /**
         * The source if known.  If non-null, it will be converted to a VLQ as below based on
         * context.
         *
         * > If present, a zero-based index into the “sources” list. This field is a base 64 VLQ
         * > relative to the previous occurrence of this field, unless this is the first occurrence
         * > of this field, in which case the whole value is represented.
         */
        val source: FilePath?,

        /**
         * The zero-based starting line in the original source represented.  Ignored if [source]
         * is null.
         *
         * This is absolute, but if [source] is not null, it will be converted to a VLQ as below
         * based on context.
         *
         * > If present, the zero-based starting line in the original source represented.
         * > This field is a base 64 VLQ relative to the previous occurrence of this field, unless
         * > this is the first occurrence of this field, in which case the whole value is
         * > represented. Always present if there is a source field.
         */
        val sourceStartLine: Int,

        /**
         * The zero-based starting column of the line in the source represented.
         *
         * This is absolute, but if [source] is not null, it will be converted to a VLQ as below
         * based on context.
         *
         * > If present, the zero-based starting column of the line in the source represented.
         * > This field is a base 64 VLQ relative to the previous occurrence of this field, unless
         * > this is the first occurrence of this field, in which case the whole value is
         * > represented. Always present if there is a source field.
         */
        val sourceStartColumn: Int,

        /**
         * An index into [SourceMap.names] indicating the original identifier that overlaps this
         * segment.
         *
         * This is absolute, but if not null it will be converted to a VLQ as below based on
         * context.
         *
         * > If present, the zero-based index into the “names” list associated with this segment.
         * > This field is a base 64 VLQ relative to the previous occurrence of this field, unless
         * > this is the first occurrence of this field, in which case the whole value is
         * > represented.
         */
        val name: String?,
    ) : Structured {
        override fun toString(): String =
            "Segment(outputCol=$outputStartColumn source=$source sourceLine=${sourceStartLine
            } sourceCol=$sourceStartColumn name=$name)"

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("outputStartColumn") { value(outputStartColumn) }
            key("source") { value(source?.toString()) }
            if (source != null) {
                key("sourceStartLine") { value(sourceStartLine) }
                key("sourceStartColumn") { value(sourceStartColumn) }
            }
            key("name") { value(name) }
        }
    }

    companion object {
        val mimeType = MimeType.json // TODO: is this right?
        const val EXTENSION = ".map"
    }
}
