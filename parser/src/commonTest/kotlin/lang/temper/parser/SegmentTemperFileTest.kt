package lang.temper.parser

import lang.temper.common.ListBackedLogSink
import lang.temper.common.assertStructure
import lang.temper.common.invalidUnicodeString
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.testCodeLocation
import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.LanguageConfig
import lang.temper.lexer.StandaloneLanguageConfig
import kotlin.test.Test

class SegmentTemperFileTest {

    @Test
    fun emptyFile() = assertSegmentation(
        "",
        wantedPreface = "" to listOf(),
        wantedBody = "" to listOf(),
    )

    @Test
    fun simpleBody() = assertSegmentation(
        "console.log(\"Hello, World!\")",
        wantedPreface = "" to listOf(),
        wantedBody = "console.log(\"Hello, World!\")" to listOf(),
    )

    @Test
    fun prefaceAndBody() = assertSegmentation(
        """
        module fn (worldName: String);
        ;;;
        console.log(\"Hello, ${'$'}{worldName}!\")

        """.trimIndent(),
        wantedPreface = """
        module fn (worldName: String);
        ;;;


        """.trimIndent() to listOf(),
        wantedBody = """


        console.log(\"Hello, ${'$'}{worldName}!\")

        """.trimIndent() to listOf("Missing close quote!"),
    )

    @Test
    fun errorInBody() = assertSegmentation(
        "console.log(\"Hello, \\", // escape sequence at end of input
        wantedPreface = "" to listOf(),
        wantedBody = "console.log(\"Hello, \\" to listOf(
            "Unrecognized escape sequence in quoted string!",
        ),
    )

    @Test
    fun errorInPreface() = assertSegmentation(
        invalidUnicodeString(""" "\uD800;;;" """),
        wantedPreface = invalidUnicodeString(""" "\uD800;;;" """) to listOf("Syntax error!"),
        wantedBody = "" to listOf(),
    )

    @Test
    fun metadataToo() = assertSegmentation(
        """
        // preface
        ;;;
        // body
        ;;;
        { "metadata": true }
        """.trimIndent(),
        wantedPreface = """
        // preface
        ;;;

        ;;;
        { "metadata": true }
        """.trimIndent() to listOf(),
        wantedBody = """


        // body
        ;;;
        { "metadata": true }
        """.trimIndent() to listOf(),
    )

    @Test
    fun tooSuper() = assertSegmentation(
        input = "a ;;; b ;;; c ;;; []",
        wantedPreface = Pair(
            "a ;;;   ;;; c ;;; []",
            listOf(
                "Cannot segment Temper source file with more than two `;;;` tokens!!",
            ),
        ),
        wantedBody = Pair(
            "      b ;;; c ;;; []",
            listOf(
                "Cannot segment Temper source file with more than two `;;;` tokens!!",
            ),
        ),
    )

    private fun assertSegmentation(
        input: String,
        wantedPreface: Pair<String, List<String>>,
        wantedBody: Pair<String, List<String>>,
        languageConfig: LanguageConfig = StandaloneLanguageConfig,
    ) {
        assertSegmentation(
            input = input,
            segment = TemperFileSegment.Preface,
            wantedTokens = wantedPreface.first,
            wantedMessages = wantedPreface.second,
            languageConfig = languageConfig,
        )
        assertSegmentation(
            input = input,
            segment = TemperFileSegment.Body,
            wantedTokens = wantedBody.first,
            wantedMessages = wantedBody.second,
            languageConfig = languageConfig,
        )
    }

    private fun assertSegmentation(
        input: String,
        segment: TemperFileSegment,
        wantedTokens: String,
        wantedMessages: List<String>,
        languageConfig: LanguageConfig = StandaloneLanguageConfig,
    ) {
        val logSink = ListBackedLogSink()

        val tokenSource = segmentTemperFile(
            testCodeLocation,
            logSink,
            languageConfig,
            input,
            segment,
        ).tokens

        // Look at token start positions and use that to rebuild the tokenized part of the input
        // with space where missing tokens were.
        val reconstitutedTokens = StringBuilder(input)
        for (i in reconstitutedTokens.indices) {
            when (reconstitutedTokens[i]) {
                '\n', '\r', '\t' -> {} // Preserve flow of original around
                else -> reconstitutedTokens[i] = ' '
            }
        }
        var tokenLimit = 0 // Monotonic
        while (tokenSource.hasNext()) {
            val token = tokenSource.next()
            if (token.synthetic) { continue }
            val tokenPos = token.pos
            val tokenStart = tokenPos.left
            require(tokenStart >= tokenLimit)
            val tokenText = token.tokenText
            for (i in tokenText.indices) {
                val c = tokenText[i]
                reconstitutedTokens[tokenStart + i] = c
            }
            tokenLimit = tokenPos.right
        }
        val tokensGotten = reconstitutedTokens.trimSpaceOnlyLines()
        val messagesGotten = logSink.allEntries.map {
            it.messageText
        }

        assertStructure(
            object : Structured {
                override fun destructure(structureSink: StructureSink) = structureSink.obj {
                    key("toks") {
                        value(wantedTokens)
                    }
                    key("log") {
                        arr {
                            wantedMessages.forEach { value(it) }
                        }
                    }
                }
            },
            object : Structured {
                override fun destructure(structureSink: StructureSink) = structureSink.obj {
                    key("toks") {
                        value(tokensGotten)
                    }
                    key("log") {
                        arr {
                            messagesGotten.forEach { value(it) }
                        }
                    }
                }
            },
            message = segment.name,
            emptyMap(),
        )
    }
}

private fun (CharSequence).trimSpaceOnlyLines() = toStringViaBuilder { out ->
    var nSpacesPending = 0
    var beforeFirstCharInLine = true
    for (i in this.indices) {
        when (val c = this[i]) {
            '\n', '\r' -> {
                out.append(c)
                beforeFirstCharInLine = true
                nSpacesPending = 0
            }
            ' ' -> {
                if (beforeFirstCharInLine) {
                    nSpacesPending += 1
                } else {
                    out.append(c)
                }
            }
            else -> {
                if (beforeFirstCharInLine) {
                    repeat(nSpacesPending) { out.append(' ') }
                    nSpacesPending = 0
                    beforeFirstCharInLine = false
                }
                out.append(c)
            }
        }
    }
}
