package lang.temper.cli.repl

import lang.temper.common.AppendingTextOutput
import lang.temper.common.Console
import lang.temper.common.assertStringsEqual
import lang.temper.common.temperEscaper
import lang.temper.env.InterpMode
import lang.temper.fs.getTestDirectories
import lang.temper.log.Debug
import lang.temper.log.LogConfigurations
import lang.temper.name.BuiltinName
import lang.temper.name.TemperName
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PartialResult
import lang.temper.value.Value
import java.nio.file.Files
import java.util.concurrent.ForkJoinPool
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReplTest {
    // Set up a REPL that writes to a StringBuilder.  Tests will make assertions about the
    // pending text.
    private val pending = StringBuilder()
    private val writeToPendingConsole = Console(AppendingTextOutput(pending))
    private val executorService get() = ForkJoinPool.commonPool()

    private var _repl: Repl? = null
    private val repl get() = _repl!!

    @BeforeTest
    fun setupRepl() {
        _repl = Repl(
            writeToPendingConsole,
            executorService = executorService,
            directories = testDirectories(),
        )
    }

    @AfterTest
    fun closeRepl() {
        _repl?.close()
        assertPending("") // No un-flushed content.
        pending.clear()
    }

    private fun assertPending(want: String) {
        val got = "$pending"
        pending.clear()
        assertStringsEqual(want, got)
    }

    private fun assertPendingContains(regex: Regex, message: String? = null) {
        val got = "$pending"
        pending.clear()
        val match = regex.find(got)

        assertNotNull(
            match,
            message = when (message) {
                null -> "/${regex.pattern}/ !in `$got`"
                else -> "$message\n\nGot\n```\n$got\n```"
            },
        )
    }

    private fun assertPendingContains(string: String, message: String? = null) {
        val got = "$pending"
        pending.clear()
        assertContains(got, string, message = message)
    }

    private fun assertPrompt(want: String) {
        val got = repl.promptAsString(isTtyLike = false)
        assertStringsEqual(want, got, "prompt")
    }

    @Test
    fun bracketClosing() {
        assertPrompt("$ ")
        assertPending("")
        repl.processLine("let x = (")
        // Since a bracket is open, we don't execute immediately.
        // Instead, the prompt shows brackets open.
        assertPrompt("  ")
        assertPending("")
        repl.processLine("    42")
        assertPrompt("  ")
        assertPending("")
        repl.processLine(")")
        assertPending("interactive#0: void\n")
    }

    @Test
    fun multilineStringValue() {
        assertPrompt("$ ")
        assertPending("")
        // Start a multiline string
        repl.processLine("(\"\"\"")
        assertPrompt("  ") // Margin matches initial "$ ".
        assertPending("")
        repl.processLine("\"foo")
        assertPrompt("  ")
        assertPending("")
        repl.processLine("\"bar")
        assertPrompt("  ")
        assertPending("")
        repl.processLine("\"baz")
        assertPrompt("  ")
        assertPending("")
        repl.processLine(")")
        @Suppress("SpellCheckingInspection")
        assertPending(
            """
                |interactive#0: "foo\nbar\nbaz"
                |
            """.trimMargin(),
        )
        assertPrompt("$ ")
    }

    @Test
    fun rawStringInterpolationWithPseudoEscapeChunks() {
        // Early tokenizing still breaks out escape tokens. Make sure they get handled properly.
        repl.processLine($$"raw\"hi\\u{,${\" t\"}}here\"")
        assertPending("""interactive#0: "hi\\u{, t}here"${"\n"}""")
    }

    @Suppress("SpellCheckingInspection")
    @Test
    fun reportBadUnicodeScalarValues() {
        repl.processLine("\"wanna be pair: \\ud800\\udc00 or\\u{20,d800,dc00}\";")
        // We get an extra highlight of the bad Unicode value below because it doesn't exactly match any earlier pos.
        assertPending(
            """
            |1: "wanna be pair: \ud800\udc00 or\u{20,d800,dc00}"
            |                   ┗━━━━━━━━━━┛
            |[interactive#0:1+16-28]@G: Instead of surrogate pair, use single code point: \u{10000}
            |1: ud800\udc00 or\u{20,d800,dc00}";
            |                       ┗━━━━━━━┛
            |[interactive#0:1+37-46]@G: Instead of surrogate pair, use single code point: \u{10000}
            |1: "wanna be pair: \ud800\udc00 or\u{20,d800,
            |                   ┗━━━━┛
            |[interactive#0:1+16-22]@R: Interpreter encountered error()
            |Interpretation ended due to runtime panic
            |interactive#0: fail
            |
            """.trimMargin(),
        )
        // But no repeats later because we do have exact pos matches in existing error messages.
        repl.processLine("\"surrogate, not scalar: \\ud834!\";")
        assertPending(
            """
            |1: rogate, not scalar: \ud834!";
            |                       ┗━━━━┛
            |[interactive#1:1+24-30]@G: Only Unicode scalar values are allowed, not surrogate code points
            |Interpretation ended due to runtime panic
            |interactive#1: fail
            |
            """.trimMargin(),
        )
        // We have both nine-hex-digit and non-hex-digit cases below.
        repl.processLine("\"big and bad: \\u{110000,FFFFFFFFF,20,hi}!\";")
        assertPending(
            """
            |1: "big and bad: \u{110000,FFFFFFFFF,20,hi}!";
            |                    ┗━━━━┛
            |[interactive#2:1+17-23]@G: Invalid Unicode scalar value, too large
            |1: and bad: \u{110000,FFFFFFFFF,20,hi}!";
            |                      ┗━━━━━━━┛
            |[interactive#2:1+24-33]@G: Invalid Unicode scalar value, too large
            |1: 110000,FFFFFFFFF,20,hi}!";
            |                       ┗┛
            |[interactive#2:1+37-39]@G: Invalid Unicode scalar value
            |Interpretation ended due to runtime panic
            |interactive#2: fail
            |
            """.trimMargin(),
        )
        // For bonus fun, also show that we don't interpolate inside Unicode runs. TODO Improve messaging for bad parse?
        repl.processLine($$""""\u{20,${"hi"}}";""")
        assertPending(
            $$"""
            |1: "\u{20,${"hi"}}";
            |   ┗━━━━━━━━━━━━━━┛
            |[interactive#3:1+0-16]@P: Expected a Expression here
            |Interpretation ended due to runtime panic
            |interactive#3: fail
            |
            """.trimMargin(),
        )
    }

    @Test
    fun variableAcrossChunks() {
        assertPrompt("$ ")
        repl.processLine("let i = 41")
        assertPending("interactive#0: void\n")
        assertPrompt("$ ")
        repl.processLine("i + 1")
        assertPending("interactive#1: 42\n")
    }

    @Test
    fun multipleVariablesAcrossChunks() {
        repl.processLine("let h = \"Hello\", w = \"World\"")
        assertPending("interactive#0: void\n")
        repl.processLine($$"\"${h}, ${w}!\"")
        assertPending("interactive#1: \"Hello, World!\"\n")
    }

    @Test
    fun typeAcrossChunks() {
        assertPrompt("$ ")
        repl.processLine("class C {}")
        assertPending("interactive#0: C__0\n")
        assertPrompt("$ ")
        repl.processLine("new C()")
        assertPending("interactive#1: {class: C__0}\n")
    }

    @Test
    fun redeclaration() {
        repl.processLine("let s = \"before\"")
        repl.processLine($$"let s = \"${s} then after\"")
        repl.processLine("s")

        assertPending(
            """
            |interactive#0: void
            |interactive#1: void
            |interactive#2: "before then after"
            |
            """.trimMargin(),
        )
    }

    @Test
    fun badCode() {
        repl.processLine("lettuce i; i + 1")
        assertPending(
            """
            |1: lettuce i; i + 1
            |   ┗━━━━━━━┛
            |[interactive#0:1+0-9]@P: Expected a TopLevel here
            |1: lettuce i; i + 1
            |              ⇧
            |[interactive#0:1+11-12]@G: No declaration for i
            |Interpretation ended due to runtime panic
            |interactive#0: fail
            |
            """.trimMargin(),
        )
    }

    @Test
    fun logJustLogs() {
        repl.processLine("console.log(\"Hello, World!\")")
        assertPending(
            """
            |Hello, World!
            |interactive#0: void
            |
            """.trimMargin(),
        )
    }

    @Test
    fun crossModuleStaticProperty() {
        repl.processLine("interface I { static s = \"I am static!\" }")
        assertPending(
            """
            |interactive#0: I__0
            |
            """.trimMargin(),
        )
        repl.processLine("console.log(I.s)")
        assertPending(
            """
            |I am static!
            |interactive#1: void
            |
            """.trimMargin(),
        )
    }

    @Test
    fun crossModuleInterfaceMethodUse() {
        repl.processLine(
            listOf(
                // Define an interface with a default method implementation.
                // It is a non-pure function that relies on definitions in
                // its defining module.
                $$"""let announce(s: String): Void { console.log("${s} called") }""",
                """interface I { public f(): Void { announce("I.f()") } }""",
            ).joinToString(" ; "),
        )
        assertPending(
            """
            |interactive#0: I__0
            |
            """.trimMargin(),
        )
        // Then we define a concrete sub-type of that interface that does not
        // override the method.
        repl.processLine("""class C extends I {}""")
        assertPending(
            """
            |interactive#1: C__0
            |
            """.trimMargin(),
        )
        // Create an instance.
        repl.processLine("""let c = new C()""")
        assertPending(
            """
            |interactive#2: void
            |
            """.trimMargin(),
        )
        // Call the method.
        // Since interactive#{0,1,2,3} are different modules, this
        // checks that method calls in the interpreter work even when
        // the class is defined in a different module from the type
        // that defines the method being called.
        repl.processLine("""c.f()""")
        assertPending(
            """
            |I.f() called
            |interactive#3: void
            |
            """.trimMargin(),
        )
    }

    @Test
    fun describeShowsTypeInferences() {
        repl.processLine("let i = 1")
        assertPending(
            """
            |interactive#0: void
            |
            """.trimMargin(),
        )
        describeFinalState(interactive = 0)
        assertPending(
            """
            |Describe interactive#0 @ frontend.generateCodeStage.after
            |  let return__0 ⦂ Void, @optionalImport `-repl//i0000/`.i ⦂ Int32;
            |  `-repl//i0000/`.i = 1;
            |  return__0 = void
            |
            |interactive#1: void
            |
            """.trimMargin(),
        )
    }

    @Test
    fun describeShowsInferredTypeActuals() {
        repl.processLine("let identity<T>(x: T): T { console.log(\"\"); x }") // interactive#0
        repl.processLine("identity(42)") // interactive#1
        pending.clear()
        describeFinalState(1)
        assertPendingContains(Regex("identity__0 ⋖ Int32 ⋗\\(42\\)"))
    }

    @Test
    fun exportOfGenericFn() =
        runIdentityFnTest("let identity<T>(x: T): T { x }")

    @Test
    fun exportOfGenericFnWithTypeBound() =
        runIdentityFnTest("let identity<T extends AnyValue>(x: T): T { x }")

    private fun runIdentityFnTest(identityFnDefinition: String) {
        repl.processLine(identityFnDefinition)
        assertPending("interactive#0: void\n")
        describeFinalState(interactive = 0)
        assertPendingContains(Regex("""let\b.* `-repl//i0000/`[.]identity"""))

        repl.processLine("let i = identity(42)")
        assertPending("interactive#2: void\n")
        describeFinalState(interactive = 2)
        assertPendingContains(Regex("let `-repl//i0002/`[.]i ⦂ Int"))

        repl.processLine("let s = identity(\"42\")")
        assertPending("interactive#4: void\n")
        describeFinalState(interactive = 4)
        assertPendingContains(Regex("let `-repl//i0004/`[.]s ⦂ String"))
    }

    @Test
    fun translateSemiExportedName() {
        repl.processLine("""let something(a: Mapped<String, Int>): Int { a.length }""")
        repl.processLine("""something(new Map([new Pair("a", 1)]))""")
        assertPending(
            """
                |interactive#0: void
                |interactive#1: 1
                |
            """.trimMargin(),
        )
        // C# translator was crashing here, so check that we don't.
        repl.processLine("""translate(1, "csharp")""")
        // Whatever the backend, we get this error. I don't know the cause or if there's something to fix.
        val commonError = """\[interactive#1\]: interactive#0 does not export symbol something"""
        assertPendingContains(Regex("$commonError.*I0000Global[.]Something", RegexOption.DOT_MATCHES_ALL))
    }

    @Test
    fun translateToCSharp() {
        repl.processLine("1 + 1")
        assertPending(
            """
                |interactive#0: 2
                |
            """.trimMargin(),
        )
        repl.processLine("""translate("interactive#0", "csharp")""")
        // Before we clear pending, ensure we've abbreviated files out in our translation.
        @Suppress("MagicNumber") // 50 is fairly magical, but we'd cross it if adding full Logging.cs or got sloppy.
        assertTrue(pending.count { it == '\n' } < 50)
        // And make sure we do have core expected content.
        @Suppress("RegExpRepeatedSpace")
        assertPendingContains(
            Regex(
                """
                    |^Translated csharp for interactive#0
                    |  interactive/
                    |    src/
                    |      I0000/
                    |        I0000Global.cs: text/x-csharp
                    |          namespace Interactive.I0000
                    |
                """.trimMargin(),
            ),
        )
    }

    @Suppress("SpellCheckingInspection") // Base64 encoding in source-map
    @Test
    fun translateToJs() {
        repl.processLine("1 + 1")
        assertPending(
            """
                |interactive#0: 2
                |
            """.trimMargin(),
        )
        repl.processLine("""translate("interactive#0", "js")""")
        assertPending(
            """
                |Translated js for interactive#0
                |  interactive/
                |    i0000.js: text/javascript
                |      /** @type {number} */
                |      const return_0 = 2;
                |      export default return_0;
                |    i0000.js.map: application/json
                |      { "version": 3, "file": "js/interactive/⋯A,MAAAA,QAAA,IAAK,AAAL;AAAK,eAAAA,QAAA" }
                |interactive#1: void
                |
            """.trimMargin(),
        )
    }

    @Test
    fun translateToJava() {
        repl.processLine("1 + 1")
        assertPending(
            """
                |interactive#0: 2
                |
            """.trimMargin(),
        )
        repl.processLine("""translate("interactive#0", "java")""")
        @Suppress("RegExpRepeatedSpace")
        assertPendingContains(
            Regex(
                """
                    |^Translated java for interactive#0
                    |  interactive/
                    |    src/
                    |      main/
                    |        java/
                    |          interactive/
                    |            i0000/
                    |              I0000Main[.]java: text/x-java-source
                    |                package interactive[.]i0000;
                    |                import temper[.]core[.]Core;
                    |
                """.trimMargin(),
            ),
        )
    }

    @Test
    fun translateToLua() {
        repl.processLine("1 + 1")
        assertPending(
            """
                |interactive#0: 2
                |
            """.trimMargin(),
        )
        repl.processLine("""translate(0, "lua")""")
        @Suppress("RegExpRepeatedSpace")
        assertPendingContains(
            Regex(
                """
                    |^Translated lua for interactive#0
                    |  interactive/
                    |    i0000[.]lua: text/x-lua
                    |      .*
                    |      return__0 = 2;
                    |      exports = \{};
                    |      return exports;
                """.trimMargin(),
                RegexOption.DOT_MATCHES_ALL,
            ),
        )
    }

    @Test
    @Suppress("RegExpRepeatedSpace")
    fun translateTypeAcrossEntries() {
        repl.processLine("class A { }")
        assertPending("interactive#0: A__0\n")
        repl.processLine("let b(a: A): A { a }")
        assertPending("interactive#1: void\n")
        // Go with Python here since we don't have that elsewhere.
        // All backends handle referencing type `A` poorly, by the way, but don't stress that yet.
        // See also: https://github.com/temperlang/temper/issues/34
        // Python is perhaps the ugliest in its handling of cross-entry type references.
        // We could independently clean it up, but something more thorough should improve others, too.
        repl.processLine("translate(1, \"py\")")
        assertPendingContains(
            Regex(
                """
                    |        def b\(a: 'a'\) -> 'a':
                    |            return a
                """.trimMargin(),
            ),
        )
    }

    @Test
    fun translateUndefinedCall() {
        repl.processLine("hi(5)")
        assertPending(
            """
                |1: hi(5)
                |   ┗┛
                |[interactive#0:1+0-2]@G: No declaration for hi
                |interactive#0: fail
                |
            """.trimMargin(),
        )
        // This was crashing the repl before.
        repl.processLine("""translate(0, "py")""")
        @Suppress("RegExpRepeatedSpace")
        assertPendingContains(
            Regex(
                """
                    |^Not generating code for interactive#0
                """.trimMargin(),
                RegexOption.MULTILINE,
            ),
        )
    }

    @Test
    fun describeConcreteSyntaxTree() {
        repl.processLine("let f(a, ...rest) {}")
        pending.clear()
        describeState(0, Debug.Frontend.ParseStage.Before)
        assertPending(
            """
            |Describe interactive#0 @ frontend.parseStage.before
            |  {/*Curly*/
            |    {/*Paren*/
            |      ["let", "f"];
            |      "(";
            |      {/*Comma*/
            |        ["a"];
            |        ",";
            |        {/*Ellipsis*/
            |          "...";
            |          ["rest"]
            |        }
            |      };
            |      ")"
            |    };
            |    "{";
            |    "}"
            |  }
            |
            |interactive#1: void
            |
            """.trimMargin(),
        )
    }

    @Test
    fun booleanTypeError() {
        repl.processLine("if (1) { 2 } else { 3 }")
        assertPending(
            """
                |1: if (1) { 2 } else { 3 }
                |       ⇧
                |[interactive#0:1+4-5]@G: Expected value of type Boolean not Int32
                |interactive#0: fail
                |
            """.trimMargin(),
        )
    }

    @Test
    fun matchPos() {
        repl.processLine("let hi(i: Int): Int { when (i) { 1 -> 1 } }")
        assertPending(
            """
                |1: t hi(i: Int): Int { when (i) { 1 -> 1 } }
                |                       ┗━━━━━━━━━━━━━━━━━┛
                |[interactive#0:1+22-41]@G: Cannot assign to Int32 from Void
                |interactive#0: void
                |
            """.trimMargin(),
        )
    }

    @Test
    fun objectLiteralNextEntry() {
        repl.processLine("class A(public a: Int) {}")
        assertPending(
            """
                |interactive#0: A__0
                |
            """.trimMargin(),
        )
        repl.processLine("{ a: 1 }")
        assertPending(
            """
                |interactive#1: {class: A__0, a: 1}
                |
            """.trimMargin(),
        )
    }

    @Test
    fun importFromStdTranslatesWithoutErrorMessage() {
        repl.processLine(
            """let { Date } = import("std/temporal"); ({ year: 2023, month: 1, day: 1 })""",
        )
        assertPending(
            """
                |interactive#0: {class: Date, year: 2023, month: 1, day: 1}
                |
            """.trimMargin(),
        )
        repl.processLine("""translate(0, "js")""")
        assertPendingContains(
            Regex("""^Translated js for interactive[\s\S]*\ninteractive#1: void\n$"""),
            message = """
                |
                |It should start with "Translated js for interactive" meaning
                |the translation did not log any error messages, and it
                |should end with void instead of bubbling.
            """.trimMargin(),
        )
    }

    @Test
    fun todayIsADate() {
        repl.processLine(
            """let { Date } = import("std/temporal");""",
        )
        assertPending(
            """
                |interactive#0: void
                |
            """.trimMargin(),
        )
        repl.processLine(
            """Date.today()""",
        )
        assertPendingContains(
            Regex(
                """^interactive#1: [{]class: Date, year: \d+, month: \d+, day: \d+[}]\n+$""",
            ),
        )
    }

    @Test
    fun todoHandled() {
        val help = object : NamedBuiltinFun, CallableValue {
            val replHelpFn = ReplHelpFn(repl)
            override val sigs = replHelpFn.sigs
            override val name = replHelpFn.name
            override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
                TODO("Because I don't wanna")
            }
        }
        val bindings = mapOf(BuiltinName(help.name) as TemperName to Value(help))
        // Redo the repl with the broken function.
        repl.close()
        _repl = Repl(
            writeToPendingConsole,
            directories = testDirectories(),
            executorService = executorService,
            overrideBindings = bindings,
        )
        repl.processLineRobustly("help()")
        assertPendingContains(
            Regex(
                """
                    |An operation is not implemented: Because I don't wanna at ReplTest.kt:\d+
                    |
                """.trimMargin(),
            ),
        )
    }

    @Test
    fun canImport() {
        // We can import something from std
        repl.processLine(
            """
                |let { Test } = import("std/testing");
            """.trimMargin(),
        )
        // And use it in a separate chunk
        repl.processLine(
            """
                |new Test().assert(true) { "testing truth from within a formal language. Take that Tarski!" }
            """.trimMargin(),
        )
        assertPending(
            """
                |interactive#0: void
                |interactive#1: void
                |
            """.trimMargin(),
        )
    }

    @Test
    fun canImportAndUseRegex() {
        // Import all from std/regex, not just a function or a type.
        repl.processLine(
            """
                |let { ... } = import("std/regex");
            """.trimMargin(),
        )
        // Use it directly.
        repl.processLine(
            """
                |Dot.compiled()
            """.trimMargin(),
        )
        // And in a regex literal.
        repl.processLine(
            """
                |rgx"."
            """.trimMargin(),
        )
        assertPendingContains(
            Regex(
                """
                    |interactive#0: void
                    |interactive#1: \{class: Regex, data: \{class: Dot__\d+}, compiled: ƒ}
                    |interactive#2: \{class: Regex, data: \{class: Dot__\d+}, compiled: ƒ}
                    |
                """.trimMargin(),
            ),
        )
    }

    @Test
    fun useImportedTypesWrong() {
        // This is about error reporting rather than repl, but repl is an easy place to test it.
        // This uses imports, which repl supports.
        repl.processLine("""let { CodePart, Dot } = import("std/regex");""")
        // Use wrong return type.
        repl.processLine("""let hi(): CodePart { Dot }""")
        // We want the right error, not the wrong error. Earlier, we got the error on the import.
        assertPending(
            """
                |interactive#0: void
                |1: et hi(): CodePart { Dot }
                |                       ┗━┛
                |[interactive#1:1+21-24]@G: Cannot assign to CodePart from Special
                |interactive#1: void
                |
            """.trimMargin(),
        )
    }

    @Test
    fun surviveBadImport() {
        // Use plain "std" instead of expected sub-path.
        repl.processLine(
            """
                |import("std");
            """.trimMargin(),
        )
        // We didn't throw an exception if we get past that. Hurrah!
        // But also make sure we do give an error.
        assertPending(
            """
                |1: import("std");
                |          ┗━━━┛
                |[interactive#0:1+7-12]@I: Import of std failed
                |interactive#0: void
                |
            """.trimMargin(),
        )
        // Now try explicit trailing slash, because that had a separate breakage.
        repl.processLine(
            """
                |import("std/");
            """.trimMargin(),
        )
        // If still no throw, check error again.
        assertPending(
            """
                |1: import("std/");
                |          ┗━━━━┛
                |[interactive#1:1+7-13]@I: Import of std/ failed
                |interactive#1: void
                |
            """.trimMargin(),
        )
    }

    @Test
    fun topLevelLabeledBreak() {
        // This was failing if wrapped in a block like we do for repl and
        // snippets.
        // ResolveNames was failing to resolve `label` in the break to the
        // actual label.
        repl.processLine(
            """
                |label: do {
                |  break label;
                |}
            """.trimMargin(),
        )
        // This should say void, not bubble.
        assertPending(
            """
                |interactive#0: void
                |
            """.trimMargin(),
        )
    }

    @Test
    fun replChunksDoNotImplicitlyImportTooMuch() {
        repl.processLine("let x = 1;")
        assertPending("interactive#0: void\n")
        repl.processLine("let y = 2;")
        assertPending("interactive#1: void\n")
        repl.processLine("x + 1")
        assertPending("interactive#2: 2\n")
        repl.processLine("""describe(2, "${Debug.Frontend.SyntaxMacroStage.Before.loggerName}")""")
        assertPending(
            """
                |Describe interactive#2 @ frontend.syntaxMacroStage.before
                |  do {
                |    @implicit let x = 1;${
                "" // we have `x` here but not `y`
            }
                |    x + 1
                |  }
                |
                |interactive#3: void
                |
            """.trimMargin(),
        )
        // `y` is still there when we need it though.
        repl.processLine("y + 1")
        assertPending("interactive#4: 3\n")
    }

    @Test
    fun punningAcrossEntries() {
        repl.processLine("class A(public b: Int, public c: Int, public d: Int) {}")
        assertPending("interactive#0: A__0\n")
        repl.processLine("let b = 2;")
        assertPending("interactive#1: void\n")
        repl.processLine("let c = 3;")
        assertPending("interactive#2: void\n")
        repl.processLine("let d = 4; { b, c: c, d }")
        assertPending("interactive#3: {class: A__0, b: 2, c: 3, d: 4}\n\n".trimMargin())
    }

    @Test
    fun helpHelpsHelpfulFunctionsHelpfully() {
        repl.processLine(
            """
                |/**
                | * I am helpful
                | *
                | * but helpful != useful.
                | */
                |let iAmHelpful(/** true or false */b: Boolean): Boolean { b }
                |
                |let iAmNot(b: Boolean): Boolean { !b }
            """.trimMargin(),
        )
        assertPending(
            """
                |interactive#0: void
                |
            """.trimMargin(),
        )
        repl.processLine(
            """
                |help(iAmHelpful)
            """.trimMargin(),
        )
        assertPending(
            """
                |fn iAmHelpful: Function
                |  I am helpful
                |
                |  but helpful != useful.
                |
                |  - b__2
                |    true or false
                |interactive#1: void
                |
            """.trimMargin(),
        )
        repl.processLine(
            """
                |help(iAmNot)
            """.trimMargin(),
        )
        assertPending(
            """
                |fn iAmNot: Function
                |  <<Function not documented yet>>
                |
                |interactive#2: void
                |
            """.trimMargin(),
        )
    }

    @Test
    fun helpForType() {
        repl.processLine(
            """
                |/**
                | * This is the doc string for class C.
                | */
                |class C {}
            """.trimMargin(),
        )
        assertPending(
            """
                |interactive#0: C__0
                |
            """.trimMargin(),
        )
        repl.processLine(
            """
                |help(C)
            """.trimMargin(),
        )
        assertPending(
            """
                |C__0: Type
                |  This is the doc string for class C.
                |interactive#1: void
                |
            """.trimMargin(),
        )
    }

    @Test
    fun translateJavaDoesntFailIssue70Or73() {
        assertPrompt("$ ")
        repl.processLine("class C {}")
        assertPending("interactive#0: C__0\n")
        assertPrompt("$ ")
        repl.processLine("let foo(c: C): Void { }")
        assertPending("interactive#1: void\n")
        assertPrompt("$ ")
        repl.processLine("translate(1, \"java\")")
        assertPendingContains(
            "[interactive#1:1+11-12]: interactive#0 does not export symbol C__0",
            message = "Remove this check if #73 is fixed",
        )
        // Replace with this to validate that we constructed the function:
        // assertPendingContains("public static void foo(C c__3) {")
    }

    @Test
    fun pendingInputIsFlushedAtClose() {
        assertPrompt("$ ")
        repl.processLine("(1 +")
        assertPrompt("  ")
        assertPending("")
        repl.close() // End of input flushes pending content
        // If there were pending, unprocessed input, we wouldn't get error messages.
        assertPending(
            """
                |1: (1 +
                |    ┗━┛
                |[interactive#0:1+1-4]@P: Operator Plus expects at least 2 operands but got 1
                |Interpretation ended due to runtime panic
                |interactive#0: fail
                |
            """.trimMargin(),
        )
    }

    @Test
    fun chunksNotBrokenOnLineWithSemiSemiSeparator() {
        repl.close()
        _repl = Repl(
            writeToPendingConsole,
            directories = testDirectories(),
            executorService = executorService,
            config = defaultConfig.copy(separator = ReplSeparator.SemiSemi),
        )
        assertPrompt("$ ")
        // One half of a co-recursive function group.
        // If we stopped at newlines, then we would get an `No declaration for odd` error.
        repl.processLine("let even(n: Int): Boolean { n == 0 || odd(n - 1) }")
        assertPrompt("$ ")
        assertPending("")
        repl.processLine("let odd(n: Int): Boolean { n == 1 || even(n - 1) }")
        assertPrompt("$ ")
        assertPending("")
        repl.processLine("even(2);;")
        assertPending(
            """
                |interactive#0: true
                |
            """.trimMargin(),
        )
    }

    @Test
    fun chunkingByEof() {
        repl.close()
        _repl = Repl(
            console = writeToPendingConsole,
            directories = testDirectories(),
            executorService = executorService,
            config = defaultConfig.copy(separator = ReplSeparator.Eof),
        )
        assertPrompt("$ ")
        repl.processLine("let even(n: Int): Boolean { n == 0 || odd(n - 1) }")
        assertPrompt("$ ")
        assertPending("")
        repl.processLine("let odd(n: Int): Boolean { n == 1 || even(n - 1) }")
        assertPrompt("$ ")
        assertPending("")
        repl.processLine("even(2)")
        repl.close() // Simulate eof
        assertPending(
            """
                |interactive#0: true
                |
            """.trimMargin(),
        )
    }

    @Test
    fun extensionsImplicitlyImported() {
        // Define an extension method
        repl.processLine("""@extension("half") let intHalf(x: Int): Int { x / 2 }""")
        // The line below doesn't mention `intHalf`, but it does mention `.half`.
        repl.processLine("""84.half()""")
        assertPending(
            """
                |interactive#0: void
                |interactive#1: 42
                |
            """.trimMargin(),
        )
        repl.processLine(
            """
                |@staticExtension(Int, "three")
                |let intThree(): Int { 3 }
            """.trimMargin(),
        )
        repl.processLine("""Int.three()""")
        assertPending(
            """
                |interactive#2: void
                |interactive#3: 3
                |
            """.trimMargin(),
        )
    }

    @Test
    fun badMap() {
        // This was crashing the repl before, when list items aren't pairs.
        repl.processLine("new Map([5])")
        assertPending(
            """
                |1: new Map([5])
                |           ┗━┛
                |[interactive#0:1+8-11]@G: Actual arguments do not match signature: <in K__30 extends AnyValue & MapKey, out V__31 extends AnyValue>(List<Pair<K__30, V__31>>) -> Map<K__30, V__31> expected [List<Pair<MapKey, AnyValue>>], but got [List<Int32>]
                |interactive#0: fail
                |
            """.trimMargin(),
        )
        // Missing a list in the first place wasn't crashing, but go ahead and test it, anyway.
        repl.processLine("new Map(5)")
        assertPending(
            """
                |1: new Map(5)
                |           ⇧
                |[interactive#1:1+8-9]@G: Actual arguments do not match signature: <in K__30 extends AnyValue & MapKey, out V__31 extends AnyValue>(List<Pair<K__30, V__31>>) -> Map<K__30, V__31> expected [List<Pair<MapKey, AnyValue>>], but got [Int32]
                |interactive#1: fail
                |
            """.trimMargin(),
        )
    }

    private fun describeFinalState(interactive: Int) =
        describeState(interactive = interactive, step = Debug.Frontend.GenerateCodeStage.After)

    private fun describeState(interactive: Int, step: LogConfigurations) {
        repl.processLine("describe($interactive, ${temperEscaper.escape(step.loggerName)})")
    }
}

fun testDirectories() = getTestDirectories(Files.createTempDirectory("temper-test"))
