package lang.temper.common.structure

import lang.temper.common.SAFE_DOUBLE_FORMAT_STRING
import lang.temper.common.jsonEscaper
import lang.temper.common.sprintf
import lang.temper.common.toStringViaBuilder
import kotlin.math.max

private const val SPACES_FOR_INDENT = "    "

class FormattingStructureSink(
    private val out: Appendable,
    private val indent: Boolean,
    private val extensions: Boolean = false,
    private val contextMap: Map<StructureContextKey<*>, Any> = emptyMap(),
    private val filterKeys: (String) -> Boolean = { true },
) : StructureSink, PropertySink {
    override fun <T : Any> context(key: StructureContextKey<T>): T? {
        val value = contextMap[key] ?: return null
        return key.asValueTypeOrNull(value)!!
    }

    private var depth = 0
    private var needsIndent = false
    private var needsComma = false
    private var valueCount = 0

    private fun maybeIndent() {
        if (needsIndent) {
            needsIndent = false
            if (indent) {
                out.append('\n')
                repeat(depth) {
                    out.append(SPACES_FOR_INDENT)
                }
            } else {
                out.append(' ')
            }
        }
    }

    private fun maybeComma() {
        if (needsComma) {
            needsComma = false
            out.append(',')
        }
    }

    private fun bracket(left: Char, emit: (FormattingStructureSink).() -> Unit, right: Char) {
        valueCount += 1
        maybeComma()
        maybeIndent()
        out.append(left)
        val writeCountBeforeProps = valueCount
        needsIndent = true
        depth += 1
        emit.invoke(this)
        needsComma = false
        depth -= 1
        if (valueCount == writeCountBeforeProps) {
            needsIndent = false
        }
        maybeIndent()
        out.append(right)
        needsComma = true
        needsIndent = true
    }

    override fun obj(emitProperties: PropertySink.() -> Unit) {
        bracket('{', emitProperties, '}')
    }

    override fun arr(emitElements: StructureSink.() -> Unit) {
        bracket('[', emitElements, ']')
    }

    override fun key(key: String, hints: Set<StructureHint>, emitValue: StructureSink.() -> Unit) {
        if (filterKeys(key)) {
            maybeComma()
            maybeIndent()
            out.append(jsonEscaper.escape(key))
            out.append(": ")
            emitValue.invoke(this)
        }
    }

    private fun valueRaw(raw: String) {
        maybeComma()
        maybeIndent()
        valueCount += 1
        out.append(raw)
        needsComma = true
        needsIndent = true
    }

    override fun value(s: String) {
        if (extensions) {
            var lineCount = 1
            var longestBacktickRunLength = 0
            val n = s.length
            for (i in s.indices) {
                val c = s[i]
                if (c == '\n') {
                    lineCount += 1
                } else if (c == '`') {
                    var end = i + 1
                    while (end < n && s[end] == '`') {
                        end += 1
                    }
                    longestBacktickRunLength = max(longestBacktickRunLength, end - i)
                }
            }
            if (lineCount >= EXTENSION_STRING_LINE_COUNT_THRESHHOLD) {
                needsIndent = true
                maybeIndent() // Start on new line so column count predicts space to strip.
                valueRaw(
                    escapeBackticked(
                        backtickCount = max(
                            MIN_BACKTICK_COUNT_FOR_SPECIAL_STRING,
                            longestBacktickRunLength + 1,
                        ),
                        content = s,
                        indentCount = (if (indent) depth else 0),
                    ),
                )
                return
            }
        }
        valueRaw(jsonEscaper.escape(s))
    }

    override fun value(n: Int) = valueRaw("$n")

    override fun value(n: Long) = valueRaw("$n")

    override fun value(n: Double) = valueRaw(
        sprintf(SAFE_DOUBLE_FORMAT_STRING, listOf(n)),
    )

    override fun value(b: Boolean) = valueRaw(if (b) "true" else "false")

    override fun nil() = valueRaw("null")

    companion object {
        fun toJsonString(
            indent: Boolean = true,
            extensions: Boolean = false,
            contextMap: Map<StructureContextKey<*>, Any> = emptyMap(),
            filterKeys: (String) -> Boolean = { true },
            f: (FormattingStructureSink).() -> Unit,
        ): String = toStringViaBuilder {
            FormattingStructureSink(
                out = it,
                indent = indent,
                extensions = extensions,
                contextMap = contextMap,
                filterKeys = filterKeys,
            ).f()
        }

        fun toJsonString(
            s: Structured?,
            indent: Boolean = true,
            extensions: Boolean = false,
            filterKeys: (String) -> Boolean = { true },
            contextMap: Map<StructureContextKey<*>, Any> = emptyMap(),
        ): String =
            toJsonString(indent = indent, extensions = extensions, contextMap = contextMap, filterKeys = filterKeys) {
                value(s)
            }
    }
}

private fun escapeBackticked(
    backtickCount: Int,
    content: CharSequence,
    indentCount: Int,
): String {
    val sb = StringBuilder()
    repeat(backtickCount) { sb.append('`') }
    sb.append('\n')
    // Always indent the first line so that the parser has a clear cue of how much space to strip.
    repeat(indentCount) { sb.append(SPACES_FOR_INDENT) }

    var written = 0
    val n = content.length
    while (written < n) {
        val lineBreakIndex = content.indexOf('\n', written)
        if (lineBreakIndex < 0) {
            break
        }
        sb.append(content, written, lineBreakIndex + 1)
        written = lineBreakIndex + 1
        if (written == n || content[written] != '\n') {
            // Indent the next line unless it's got zero content.
            repeat(indentCount) { sb.append(SPACES_FOR_INDENT) }
        }
    }
    sb.append(content, written, n)
    sb.append('\n')
    repeat(indentCount) { sb.append(SPACES_FOR_INDENT) }
    repeat(backtickCount) { sb.append('`') }
    return sb.toString()
}

private const val EXTENSION_STRING_LINE_COUNT_THRESHHOLD = 5
