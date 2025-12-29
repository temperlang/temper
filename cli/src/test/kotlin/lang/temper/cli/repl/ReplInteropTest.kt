@file:Suppress("MagicNumber")

package lang.temper.cli.repl

import lang.temper.common.Console
import lang.temper.common.Style
import lang.temper.common.assertStringsEqual
import lang.temper.common.temperEscaper
import lang.temper.log.NullTextOutput
import lang.temper.supportedBackends.supportedBackends
import org.jline.reader.LineReaderBuilder
import org.jline.reader.Parser
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedStyle
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ForkJoinPool
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ReplInteropTest {
    private val repl: Repl get() = _repl!!

    private var _repl: Repl? = null

    @BeforeTest
    fun setupRepl() {
        _repl = Repl(Console(NullTextOutput), testDirectories(), ForkJoinPool.commonPool())
    }

    @AfterTest
    fun teardownRepl() {
        _repl?.close()
        _repl = null
    }

    @Test
    fun suggestCompletions() {
        val completer = ReplInterop.TemperCompleterImpl(repl)
        val parser = ReplInterop.LineParser(repl)
        val suggestions0 = completer.completions(
            parser.parse(
                line = "  con.",
                //      012345
                cursor = 5, // Before .
                context = Parser.ParseContext.COMPLETE,
            ),
        )
        assertEquals(
            listOf("console", "continue"),
            suggestions0,
        )
        repl.processLine("let contacts = 1")
        val suggestions1 = completer.completions(
            parser.parse(
                line = "  con.",
                //      012345
                cursor = 5, // Before .
                context = Parser.ParseContext.COMPLETE,
            ),
        )
        assertEquals(
            listOf("contacts", "console", "continue"),
            suggestions1,
        )
        repl.processLine("let condition = true")
        val suggestions2 = completer.completions(
            parser.parse(
                line = "  con.",
                //      012345
                cursor = 5, // Before .
                context = Parser.ParseContext.COMPLETE,
            ),
        )
        assertEquals(
            listOf("condition", "contacts", "console", "continue"),
            suggestions2,
        )
    }

    @Test
    fun describeStepArgAllCompletions() {
        val completer = ReplInterop.TemperCompleterImpl(repl)
        val parser = ReplInterop.LineParser(repl)

        val suggestions = completer.completions(
            parser.parse(
                line = "describe(0, \"f",
                //      012345678901 234
                //                1
                cursor = 14, // at end
                context = Parser.ParseContext.COMPLETE,
            ),
        )
        val someWanted = setOf("frontend\"", "frontend.typeStage.before\"")
        assertTrue(suggestions.containsAll(someWanted)) {
            "$suggestions should contain all of $someWanted"
        }
    }

    @Test
    fun describeAfterCommaHasSomething() {
        // The `help()` text stays hitting tab gives you a list of debug steps.
        val completer = ReplInterop.TemperCompleterImpl(repl)
        val parser = ReplInterop.LineParser(repl)

        val suggestions = completer.completions(
            parser.parse(
                line = "describe(0, ",
                //      0123456789012
                //                1
                cursor = 12, // at end
                context = Parser.ParseContext.COMPLETE,
            ),
        )
        assertContains(suggestions, "\"frontend\"")
    }

    @Test
    fun describeAfterCommaWithStartedString() {
        // The `help()` text stays hitting tab gives you a list of debug steps.
        val completer = ReplInterop.TemperCompleterImpl(repl)
        val parser = ReplInterop.LineParser(repl)

        val suggestions = completer.completions(
            parser.parse(
                line = "describe(0, \"frontend.g",
                //      012345678901 234567890123
                //                1          1
                cursor = 23, // at end
                context = Parser.ParseContext.COMPLETE,
            ),
        )
        assertContains(suggestions, "frontend.generateCodeStage\"")
    }

    @Test
    fun suggestStringsAfterHelp() {
        val completer = ReplInterop.TemperCompleterImpl(repl)
        val parser = ReplInterop.LineParser(repl)

        val suggestions = completer.completions(
            parser.parse(
                line = "help(",
                //      012345
                cursor = 5, // at end
                context = Parser.ParseContext.COMPLETE,
            ),
        )
        val someWanted = setOf("(\"${ReplHelpFn.NAME}\"", "(\"${ReplDescribeFn.NAME}\"")
        assertTrue(suggestions.containsAll(someWanted)) {
            "$suggestions should contain all of $someWanted"
        }
    }

    @Test
    fun suggestStringsAfterHelpWithSpaceAfterParen() {
        val completer = ReplInterop.TemperCompleterImpl(repl)
        val parser = ReplInterop.LineParser(repl)

        val suggestions = completer.completions(
            parser.parse(
                line = "help( ",
                //      0123456
                cursor = 6, // at end
                context = Parser.ParseContext.COMPLETE,
            ),
        )
        val someWanted = setOf("\"${ReplHelpFn.NAME}\"", "\"${ReplDescribeFn.NAME}\"")
        assertTrue(suggestions.containsAll(someWanted)) {
            "$suggestions should contain all of $someWanted"
        }
    }

    @Test
    fun translateThenCommaThenTabGivesSupportedBackends() {
        // The `help()` text stays hitting tab gives you a list of debug steps.
        val completer = ReplInterop.TemperCompleterImpl(repl)
        val parser = ReplInterop.LineParser(repl)

        val suggestions = completer.completions(
            parser.parse(
                line = "translate(0, ",
                //      01234567890123
                //                1
                cursor = 13, // at end
                context = Parser.ParseContext.COMPLETE,
            ),
        )
        val wanted = supportedBackends.sorted()
            .map { temperEscaper.escape(it.uniqueId) }
        assertEquals(wanted, suggestions)
    }

    @Test
    fun suggestedLoggerNamesStartingWithQuotes() {
        val completer = ReplInterop.TemperCompleterImpl(repl)
        val parser = ReplInterop.LineParser(repl)
        var suggestions = completer.completions(
            parser.parse("\"f", 2),
        )
        assertContains(suggestions, "frontend")
        assertFalse("backend" in suggestions)

        repl.processLine("let x;")
        suggestions = completer.completions(parser.parse("\"int", 4))
        assertEquals(listOf("interactive#0"), suggestions)

        @Suppress("MagicNumber") // test constant
        repl.lastLocReferenced = ReplChunkIndex(123)
        suggestions = completer.completions(parser.parse("\"int", 4))
        assertEquals(suggestions, listOf("interactive#123"))
    }

    @Test
    fun describeStepArgFilteredCompletions() {
        val completer = ReplInterop.TemperCompleterImpl(repl)
        val parser = ReplInterop.LineParser(repl)

        val suggestions = completer.completions(
            parser.parse(
                line = """describe(0, "frontend.typeStage.b")""",
                //        012345678901234567890123456789012345
                //                  1         2         3
                cursor = 33, // Before closing "
                context = Parser.ParseContext.COMPLETE,
            ),
        )
        assertEquals(
            listOf(
                // There are mismatched quotes here because the
                // ParsedLine word starts just after the open
                // quote, but we're generating completions of the
                // multi-token string literal.
                "frontend.typeStage.before\"",
                "frontend.typeStage.beforeInterpretation\"",
            ),
            suggestions,
        )
    }

    @Test
    fun backslashesPreserved() {
        val out = ByteArrayOutputStream()
        val lineReader = LineReaderBuilder.builder()
            .appName("ReplInteropTest")
            .parser(ReplInterop.LineParser(repl))
            .terminal(
                TerminalBuilder.builder()
                    .dumb(true)
                    .color(false)
                    .system(false)
                    .jna(false)
                    .jansi(false)
                    .jni(false)
                    .ffm(false)
                    .streams(
                        ByteArrayInputStream("\"\\u000a\"\n\r\n\u0004".toByteArray(Charsets.UTF_8)),
                        out,
                    )
                    .encoding(Charsets.UTF_8)
                    .build(),
            )
            .build()
        val line = lineReader.readLine("$ ")
        assertStringsEqual("\"\\u000a\"", line)
    }

    @Test
    fun highlightLeavesOpen() {
        val highlighter = ReplInterop.TemperJlineHighlighterImpl(repl)
        val result = highlighter.highlight(null, "\"").toString()
        // We were getting synthic close tokens. This tests against those.
        assertEquals("\"", result)
    }

    @Test
    fun finishMultilineString() {
        val highlighter = ReplInterop.TemperJlineHighlighterImpl(repl)
        // These steps replicate what happens if you type triple-quote, enter, and then again.
        highlighter.highlight(null, "")
        highlighter.highlight(null, "\"")
        highlighter.highlight(null, "\"\"")
        highlighter.highlight(null, "\"\"\"")
        highlighter.highlight(null, "\"\"\"")
        repl.processLine("\"\"\"")
        highlighter.highlight(null, "  \"")
        highlighter.highlight(null, "  \"\"")
        highlighter.highlight(null, "  \"\"\"")
        highlighter.highlight(null, "  \"\"")
        highlighter.highlight(null, "  \"")
        // This was throwing an exception before. So test that we get through.
        val lastText = "  \"a"
        highlighter.highlight(null, lastText)
        val lastStyled = highlighter.highlight(null, lastText)
        // We also had an error after styling correctly where we got nothing back. Check against empty.
        assertEquals(lastText.length, lastStyled.length, "$lastStyled")
        // We also had error styling here after the first crash fix. Check against that.
        val blankStyle = Style.NormalOutput.updateJlineStyle(AttributedStyle.DEFAULT).style
        val stringStyle = Style.QuotedStringToken.updateJlineStyle(AttributedStyle.DEFAULT).style
        val styles = (0 until lastStyled.length).map { lastStyled.styleAt(it).style }
        assertEquals(
            // ' '   ' '   '"'   'a'
            // space space space string
            listOf(blankStyle, blankStyle, blankStyle, stringStyle),
            styles,
        )
    }

    @Test
    fun highlightComment() {
        val highlighter = ReplInterop.TemperJlineHighlighterImpl(repl)
        // The comment at the end of the line was bleeding its style to the next line.
        val start = "do { // hi"
        highlighter.highlight(null, start)
        repl.processLine(start)
        val styled = highlighter.highlight(null, "5")
        // But it should be styled as a number.
        val numberStyle = Style.NumberToken.updateJlineStyle(AttributedStyle.DEFAULT).style
        assertEquals(numberStyle, styled.styleAt(0).style)
    }

    @Test
    fun lineParsingOfStrings() {
        val parser = ReplInterop.LineParser(repl)
        val line = parser.parse($$"""console.log("foo${bar}baz")""", 10)
        //                                         0123456789012345   6  7890123456
        //                                                   1              2
        line.words()
    }
}
