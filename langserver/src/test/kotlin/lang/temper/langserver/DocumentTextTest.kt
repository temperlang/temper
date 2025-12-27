package lang.temper.langserver

import lang.temper.common.assertStringsEqual
import lang.temper.common.console
import lang.temper.common.jsonEscaper
import lang.temper.common.sprintf
import lang.temper.common.testCodeLocation
import lang.temper.common.withRandomForTest
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.lang.IllegalArgumentException
import java.lang.Integer.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/** Number of documents to fuzz test. */
private const val N_FUZZER_RUNS = 500

/** Number of mutations to try to each document. */
private const val N_FUZZER_STEPS = 500

@Suppress("SpellCheckingInspection") // Ooday otnay ellspay eckchay isthay
private val loremIpsum = """
Lorem ipsum dolor
sit amet, consectetur
adipiscing elit, sed do
eiusmod tempor incididunt
ut labore et dolore magna
aliqua.
Ut enim ad minim veniam,
quis nostrud exercitation
ullamco laboris nisi ut
aliquip ex ea commodo consequat.

Duis
aute
irure
dolor
in
reprehenderit

Foous${
    "\r\r"
}barra${
    "\r\n"
}baziorum

in voluptate velit esse cillum dolore
eu fugiat nulla pariatur.
Excepteur sint occaecat cupidatat non
proident, sunt in culpa qui officia
deserunt mollit anim id est laborum.
""".trim()

class DocumentTextTest {
    /**
     * Try a series of randomly generated edits and check properties like:
     * - The whole text is correct
     * - All elements of the line list are the last or end with a line break.
     * - All line breaks are at the end of entries in the line lists.
     * - No line ends with a '\r' that is immediately followed by a line that starts with a '\n'.
     */
    @Test
    fun fuzzDocumentChanges() = withRandomForTest { prng ->
        // To debug, set to (step, run) and supply the seed to withRandomForTest above
        // Also enabling the commented out assignment to DEBUG_REPLACE by adjusting it from
        // to `internal var` from `private const val` is super convenient when debugging.
        val debugAt: Pair<Int, Int>? = null

        for (run in 0 until N_FUZZER_RUNS) {
            val canon = SlowDocumentText(loremIpsum)
            val underTest = DocumentText(testCodeLocation)
            underTest.replace(underTest.wholeTextRange, loremIpsum)

            assertConsistent(canon, underTest, run, -1)

            for (step in 0 until N_FUZZER_STEPS) {
                val (range, li, ri) = canon.randomRange(prng)
                val replacementLeft = prng.nextInt(0, loremIpsum.length)
                val replacementRight = min(
                    loremIpsum.length,
                    replacementLeft + prng.nextInt(RANDOM_RANGE_LIMIT),
                )
                val replacementText = loremIpsum.substring(replacementLeft, replacementRight)
                if ((run to step) == debugAt) {
                    dumpState(run, step, canon, underTest, range, li, ri, replacementText)
                    console.log("")
                    // DEBUG_REPLACE = true
                }
                canon.text.replace(li, ri, replacementText)
                try {
                    underTest.replace(range, replacementText)
                } catch (ex: IllegalArgumentException) {
                    console.warn("Failed at run=$run, step=$step")
                    throw ex
                }
                assertConsistent(canon, underTest, run, step)
            }
        }
    }

    // We need a simple but slow implementation of a document text
    class SlowDocumentText(initialText: String) {
        val text = StringBuilder(initialText)

        fun randomRange(prng: Random): Triple<Range, Int, Int> {
            val stringOffsetLeft = prng.nextInt(text.length + 1)
            val stringOffsetRight = min(
                text.length,
                stringOffsetLeft + prng.nextInt(RANDOM_RANGE_LIMIT),
            )
            check(stringOffsetLeft <= stringOffsetRight)
            var startLine = 0
            var startChar = 0
            for (i in 0 until stringOffsetLeft) {
                if (text.lineBreakAt(i)) {
                    startLine += 1
                    startChar = 0
                } else {
                    startChar += 1
                }
            }
            var endLine = startLine
            var endChar = startChar
            for (i in stringOffsetLeft until stringOffsetRight) {
                if (text.lineBreakAt(i)) {
                    endLine += 1
                    endChar = 0
                } else {
                    endChar += 1
                }
            }

            // In "...\n...", for example
            // the empty range after the \n at index 4 could be represented several ways:
            // - Range(Position(0, 4), Position(0, 4))
            // - Range(Position(0, 4), Position(1, 0))
            // - Range(Position(1, 0), Position(1, 0))
            // TODO: generate ambiguous variants as where a (line, char) pair is at the start of
            // a line > 0 or at the end of a line not at the end while preserving the property that
            // either the start line precedes the end line or they're the same and the start char
            // is not after the end char.

            return Triple(
                Range(
                    Position(startLine, startChar),
                    Position(endLine, endChar),
                ),
                stringOffsetLeft,
                stringOffsetRight,
            )
        }
    }

    private fun assertConsistent(
        canon: SlowDocumentText,
        underTest: DocumentText,
        run: Int,
        step: Int,
    ) {
        val message = "run=$run, step=$step"
        assertStringsEqual(canon.text.toString(), underTest.fullText, message)
        val debugLines = underTest.debugLines
        val lastLineIndex = debugLines.lastIndex
        for (i in debugLines.indices) {
            val line = debugLines[i]
            for (j in 0 until line.lastIndex) {
                if (line.lineBreakAt(j)) {
                    dumpState(run, step, canon, underTest, null, null, null, null)
                    fail(
                        "$message, line $i has line break at $j not at end: ${
                            jsonEscaper.escape(line)
                        }",
                    )
                }
            }
            if (i != lastLineIndex && (line.isEmpty() || !line.lineBreakAt(line.lastIndex))) {
                dumpState(run, step, canon, underTest, null, null, null, null)
                fail(
                    "$message, line $i not at end missing line break at end: ${
                        jsonEscaper.escape(line)
                    }",
                )
            }
            if (
                line.endsWith("\r") &&
                debugLines.getOrNull(i + 1)?.startsWith("\n") == true
            ) {
                // If there is text like
                //    ___\r___\n___
                // and someone replaces the middle "___" with the empty string then DocumentText
                // must reflect the fact that \r\n is a single line break.
                fail("$message, line $i ends with CR and is followed by an orphaned LF")
            }
        }
    }

    private fun dumpState(
        run: Int,
        step: Int,
        canon: SlowDocumentText,
        underTest: DocumentText,
        range: Range?,
        li: Int?,
        ri: Int?,
        replacementText: String?,
    ) {
        console.group("run: $run, step: $step") {
            console.group("wholeText") {
                console.log(canon.text.toString())
            }
            val debugLines = underTest.debugLines
            console.group("lines") {
                for (lineIndex in debugLines.indices) {
                    val debugLine = debugLines[lineIndex]
                    console.log(
                        sprintf(
                            "%04d: %s len=%d",
                            listOf(lineIndex, jsonEscaper.escape(debugLine), debugLine.length),
                        ),
                    )
                }
            }
            if (replacementText != null) {
                console.group("replacement") {
                    console.log(replacementText)
                }
            }
            if (range != null) {
                console.log("range=$range, li=$li, ri=$ri")
            }
            if (li != null && ri != null) {
                console.group("replaced from canon") {
                    console.log(jsonEscaper.escape(canon.text.subSequence(li, ri).toString()))
                }
            }
            if (range != null) {
                console.group("replaced from underTest") {
                    val startLine = range.start.line
                    val endLine = range.end.line
                    for (i in startLine..endLine) {
                        var lineText = debugLines.getOrNull(i) ?: ""
                        if (i == endLine) {
                            val endChar = range.end.character
                            if (endChar < lineText.length) {
                                lineText = lineText.substring(0, endChar)
                            }
                        }
                        if (i == startLine) {
                            val startChar = range.start.character
                            lineText =
                                if (startChar < lineText.length) {
                                    lineText.substring(startChar)
                                } else {
                                    ""
                                }
                        }
                        console.log(
                            sprintf("%04d: %s", listOf(i, jsonEscaper.escape(lineText))),
                        )
                    }
                }
            }
        }
    }
}

private const val RANDOM_RANGE_LIMIT = 128
