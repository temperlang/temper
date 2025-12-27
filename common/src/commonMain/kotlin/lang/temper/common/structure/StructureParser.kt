package lang.temper.common.structure

import lang.temper.common.BITS_PER_HEX_DIGIT
import lang.temper.common.DECIMAL_RADIX
import lang.temper.common.HEX_RADIX
import lang.temper.common.NUM_HEX_IN_U_ESCAPE
import lang.temper.common.VALUE_HEX_NUMERAL_A
import lang.temper.common.json.JsonValue
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.toStringViaBuilder
import kotlin.math.max

class StructureParser private constructor(
    private val input: String,
    private val tolerant: Boolean,
) {
    private var pos = 0
    private val limit = input.length

    private fun fromJson(sink: StructureSink) {
        skipSpace()
        require(pos != limit) {
            "Expected value, found end-of-input"
        }
        when (input[pos]) {
            '{' -> parseJsonObject(sink)
            '[' -> parseJsonArray(sink)
            in '0'..'9', '.', '+', '-' -> parseJsonNumber(sink)
            '"' -> parseJsonString(sink)
            '`' -> parseJsonExtensionString(sink)
            'f', 't' -> parseJsonBoolean(sink)
            'n' -> parseJsonNull(sink)
            else ->
                throw IllegalArgumentException("Expected value found `${ input.substring(pos) }`")
        }
    }

    private fun assertFinished() {
        skipSpace()
        require(pos == input.length) {
            "Unparsed content: `${ input.substring(pos) }`"
        }
    }

    private fun skipSpace(lineBreaks: Boolean = true) {
        while (pos < limit) {
            when (input[pos]) {
                ' ', '\t' -> pos += 1
                '\n', '\r' -> if (lineBreaks) {
                    pos += 1
                } else {
                    return
                }
                '/' -> if (!skipComment() && lineBreaks) {
                    return
                }
                else -> return
            }
        }
    }

    private fun skipComment(): Boolean {
        if (tolerant && pos + 2 <= limit && input[pos] == '/') {
            when (input[pos + 1]) {
                '/' -> {
                    pos += 2 // Skip "//"
                    while (pos < limit) {
                        val c = input[pos]
                        if (c == '\n' || c == '\r') {
                            break
                        }
                        pos += 1
                    }
                    return true
                }
                '*' -> {
                    val end = input.indexOf("*/", pos + 2)
                    if (end >= 0) {
                        pos = end + 2
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun expect(s: String) {
        require(consume(s)) {
            "Expected `$s` found `${ input.substring(pos) }`"
        }
    }

    private fun consume(s: String) = if (lookahead(s)) {
        pos += s.length
        true
    } else {
        false
    }

    private fun lookahead(s: String): Boolean =
        pos + s.length <= limit && s.regionMatches(0, input, pos, s.length)

    private fun parseJsonArray(sink: StructureSink) {
        expect("[")
        sink.arr {
            parseCommaSeparated("]") {
                fromJson(this)
            }
        }
        expect("]")
    }

    private fun parseJsonObject(sink: StructureSink) {
        expect("{")
        sink.obj {
            parseCommaSeparated("}") {
                key(expectKey()) {
                    skipSpace()
                    expect(":")
                    fromJson(this)
                }
            }
        }
        expect("}")
    }

    private fun parseCommaSeparated(stopToken: String, parseOne: () -> Unit) {
        skipSpace()
        if (!lookahead(stopToken)) {
            while (true) {
                skipSpace()
                parseOne()
                skipSpace()
                if (!consume(",")) {
                    break
                }
                if (tolerant) { // Check if trailing comma
                    skipSpace()
                    if (lookahead(stopToken)) {
                        break
                    }
                }
            }
        }
    }

    private fun parseJsonString(sink: StructureSink) {
        sink.value(expectJsonString())
    }

    private fun parseJsonExtensionString(sink: StructureSink) {
        if (!tolerant) {
            throw IllegalArgumentException(
                "Back-ticked strings not allowed.  Found ${ input.substring(pos) }",
            )
        }
        sink.value(expectBackTickedString())
    }

    private fun expectKey(): String {
        if (tolerant && pos < limit && input[pos] != '"' && allowedInTolerantUnquotedKey(input[pos])) {
            // Handle a bare key as in
            //     { foo: "value" }
            return buildString {
                append(input[pos])
                pos += 1
                while (pos < limit) {
                    val c = input[pos]
                    if (allowedInTolerantUnquotedKey(c)) {
                        pos += 1
                        append(c)
                    } else {
                        break
                    }
                }
            }
        }
        return expectJsonString()
    }

    private fun allowedInTolerantUnquotedKey(c: Char): Boolean = when (c) {
        in 'a'..'z',
        in 'A'..'Z',
        in '0'..'9',
        '-', '_', '.',
        -> true
        else -> false
    }

    private fun expectJsonString(): String {
        val sb = StringBuilder()
        expect("\"")
        while (pos < limit) {
            val c = input[pos]
            if (c == '"') { break }
            pos += 1
            if (c == '\\') {
                require(pos < limit) {
                    "Expected string character, found end of input"
                }

                sb.append(
                    when (val next = input[pos]) {
                        'b' -> '\b'
                        't' -> '\t'
                        'n' -> '\n'
                        'f' -> '\u000c'
                        'r' -> '\r'
                        'u' -> {
                            var cp = 0
                            // \uF00D
                            //  ^    ^
                            //  012345
                            // pos is at u.  hex digits at pos+1..pos+4
                            require(pos + (NUM_HEX_IN_U_ESCAPE + 1) <= limit) {
                                "Expected 4 hex digits, found end of input"
                            }
                            for (i in (pos + 1)..(pos + NUM_HEX_IN_U_ESCAPE)) {
                                val digitVal = when (val h = input[i]) {
                                    in '0'..'9' -> h.code - '0'.code
                                    in 'a'..'f' -> h.code - 'a'.code + VALUE_HEX_NUMERAL_A
                                    in 'A'..'F' -> h.code - 'A'.code + VALUE_HEX_NUMERAL_A
                                    else -> throw IllegalArgumentException(
                                        "Expected hex digit, found '$h'",
                                    )
                                }
                                cp = (cp shl BITS_PER_HEX_DIGIT) + digitVal
                            }
                            pos += NUM_HEX_IN_U_ESCAPE // The increment below covers the 'u'
                            cp.toChar()
                        }
                        else -> next
                    },
                )
                pos += 1
            } else {
                sb.append(c)
            }
        }
        expect("\"")
        return sb.toString()
    }

    private fun expectBackTickedString(): String {
        val backtickStart = pos
        while (pos < limit) {
            val c = input[pos]
            if (c != '`') {
                break
            }
            pos += 1
        }
        val backtickRunLength = pos - backtickStart
        if (backtickRunLength < MIN_BACKTICK_COUNT_FOR_SPECIAL_STRING) {
            throw IllegalArgumentException(
                "Expected 3 or more backticks.  Found ${ input.substring(pos) }",
            )
        }

        skipSpace(lineBreaks = false)
        if (pos == limit || input[pos] != '\n') {
            throw IllegalArgumentException(
                "Expected line break after ${ "`".repeat(backtickRunLength) }",
            )
        }
        val btStart = pos + 1
        var btEnd = pos
        while (btEnd < limit) {
            val c = input[btEnd]
            btEnd += 1
            if (c == '\n') {
                val beforeLineStart = btEnd - 1
                while (btEnd < limit) {
                    val d = input[btEnd]
                    if (d == ' ' || d == '\t') {
                        btEnd += 1
                    } else {
                        break
                    }
                }
                val beforeBt = btEnd
                while (btEnd < limit && input[btEnd] == '`') {
                    btEnd += 1
                }
                val btCount = btEnd - beforeBt
                if (btCount >= backtickRunLength) {
                    // Look at the indentation of the backtick to figure out the strip count.
                    val startingColumn = run {
                        val startOfBacktickLine = beforeLineStart + 1
                        var column = 0
                        for (n in startOfBacktickLine until beforeBt) {
                            if (input[n] == '\t') {
                                column = column + TAB_WIDTH - column.rem(TAB_WIDTH)
                            } else {
                                column += 1
                            }
                        }
                        column
                    }
                    pos = btEnd
                    val content = input.substring(btStart, max(btStart, beforeLineStart))
                    return stripSpaceAtEachStartLine(startingColumn, content)
                }
            }
        }
        throw IllegalArgumentException(
            "Expected ${ "`".repeat(backtickRunLength) }, but got end of input",
        )
    }

    private fun parseJsonBoolean(sink: StructureSink) {
        if (input[pos] == 't') {
            expect("true")
            sink.value(true)
        } else {
            expect("false")
            sink.value(false)
        }
    }

    private fun parseJsonNull(sink: StructureSink) {
        expect("null")
        sink.nil()
    }

    private fun parseJsonNumber(sink: StructureSink) {
        val start = pos
        var end = start
        var isFloaty = false
        var radix = DECIMAL_RADIX
        end_loop@
        while (end < limit) {
            when (val c = input[end]) {
                in '0'..'9', '+', '-' -> {}
                '.' -> isFloaty = true
                in 'A'..'Z', in 'a'..'z' -> {
                    // 'e' is exponent and hex digit
                    if (c == 'e' || c == 'E') {
                        isFloaty = radix != HEX_RADIX
                        // exponent indicator or hex digit
                    } else if (c == 'x' || c == 'X') {
                        radix = HEX_RADIX
                    }
                }
                else -> break@end_loop
            }
            end += 1
        }
        pos = end

        val token = input.substring(start, end)
        val digits =
            if (radix == HEX_RADIX) {
                require(token.startsWith("0x") || token.startsWith("0X")) {
                    "Expected '0x' or '0X' prefix"
                }
                token.substring("0x".length)
            } else {
                token
            }

        if (isFloaty && radix != HEX_RADIX) {
            sink.value(token.toDouble())
        } else {
            sink.value(digits.toLong(radix))
        }
    }

    companion object {
        /**
         * @throws IllegalArgumentException on malformed input.  Setting [tolerant] reduces the
         *    number of reasons to throw but does not prevent throwing on all inputs.
         */
        fun parseJson(
            input: String,
            tolerant: Boolean = false,
            contextMap: Map<StructureContextKey<*>, Any> = emptyMap(),
        ): JsonValue {
            val treeBuilder = JsonValueBuilder(contextMap)
            parseJsonTo(input, treeBuilder, tolerant = tolerant)
            return treeBuilder.getRoot()
        }

        /**
         * @throws IllegalArgumentException on malformed input.  Setting [tolerant] reduces the
         *    number of reasons to throw but does not prevent throwing on all inputs.
         */
        fun parseJsonTo(input: String, sink: StructureSink, tolerant: Boolean = false) {
            val p = StructureParser(input, tolerant = tolerant)
            p.fromJson(sink)
            p.assertFinished()
        }

        private fun stripSpaceAtEachStartLine(column: Int, content: String) =
            toStringViaBuilder { sb ->
                var i = -1
                val n = content.length
                while (i < n) {
                    val skipPrefix = i == -1 ||
                        run {
                            val c = content[i]
                            sb.append(c)
                            c == '\n'
                        }
                    i += 1

                    if (skipPrefix) {
                        var skipped = 0
                        skipLoop@
                        while (skipped < column && i < n) {
                            val nToSkip = when (content[i]) {
                                ' ' -> 1
                                '\t' -> skipped + TAB_WIDTH - skipped.rem(TAB_WIDTH)
                                else -> break@skipLoop
                            }
                            skipped += nToSkip
                            i += 1
                        }
                        if (skipped < column && (i == n || content[i] == '\n')) {
                            // Ok to have blank lines not fully indented
                        } else if (skipped != column) {
                            throw IllegalArgumentException(
                                "Expected $column whitespace at start of line but stripped ${
                                    ""
                                }$skipped in multiline string: $content",
                            )
                        }
                    }
                }
            }
    }
}

private const val TAB_WIDTH = 8

internal const val MIN_BACKTICK_COUNT_FOR_SPECIAL_STRING = 3
