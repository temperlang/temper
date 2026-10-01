package lang.temper.be.elixir

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedStructure
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.lexer.Genre
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ElixirBackendTest {
    /**
     * A class the frontend rejects leaves its values typed *Invalid*. Reading a
     * property of one must become broken code that raises where it is reached,
     * as every other garbage node does, and not a TODO that takes the whole
     * translation down. `semantics/broken` never reaches this path: it came from
     * a library written outside the suite (ormery), whose `Query` class declares
     * its inputs twice.
     */
    @Test
    fun aGetterOnAnInvalidValueIsBrokenCodeNotACompilerCrash() {
        val out = generatedText(
            """
            |class Field(public fieldType: String) {}
            |
            |class Schema() {
            |  public getField(): Field { new Field("Int") }
            |}
            |
            |class Query(public schema: Schema) {
            |  public constructor(schema: Schema) { this.schema = schema; }
            |  public kind(): String { let f = schema.getField(); f.fieldType }
            |}
            """.trimMargin(),
        )
        assertContains(out, "broken code")
    }

    /**
     * A local whose value raises is never bound, and Elixir refuses to compile
     * a later read of it even though the read can never run ("undefined
     * variable"). So nothing after a raise in the same block may be emitted.
     * ormery's generated Elixir failed `mix compile` five times this way once
     * its broken reads stopped crashing the translator. Here the raise is the
     * read itself: `f`'s declared type does not exist, which leaves it typed
     * AnyValue, and nothing raises before the read. (That read used to stop
     * the build: AnyValue was looked up as a builtin with no getter.)
     */
    @Test
    fun nothingAfterARaiseReadsTheNameItWouldHaveBound() {
        val out = generatedText(
            """
            |export let kind(f: Nope): String {
            |  let fieldKind = f.fieldType;
            |  if (fieldKind == "Int") { "int" } else { "other" }
            |}
            """.trimMargin(),
        )
        assertContains(out, "broken code: read of .fieldType on a value with no properties")
        assertFalse("fieldKind ==" in out, "a read of fieldKind survives its raise:\n$out")
    }

    /**
     * Tests, and what only tests reach, are not part of the library: they go
     * to `test/support/`, which Mix compiles for `mix test` alone, and a
     * dependency only they need (std, for std/testing) is `only: :test`. Each
     * ExUnit test names the Temper line it is on, which leads its failure.
     *
     * The helper takes its input as a parameter. A test of constants alone is
     * evaluated by the frontend at compile time, on every backend, and what it
     * called then looks unused rather than test-only.
     */
    @Test
    fun testsAndWhatOnlyTheyUseStayOutOfTheLibrary() {
        val out = generatedText(
            """
            |export let double(x: Int): Int { x * 2 }
            |class Probe(public n: Int) {}
            |let checkDouble(test: Test, p: Probe, want: Int): Void {
            |  assert(double(p.n) == want);
            |}
            |test("doubles") { test => checkDouble(test, new Probe(2), 4); }
            """.trimMargin(),
        )
        val library = fileContent(out, "temper_main.ex")
        val support = fileContent(out, "temper_tests.ex")
        val mix = fileContent(out, "mix.exs")
        for (name in listOf("checkDouble", "Probe", "doubles", "Temper.Std")) {
            assertFalse(name in library, "$name is in the library:\n$library")
        }
        assertContains(support, "def checkDouble")
        assertContains(support, "defmodule Temper.MyTestLibrary.Probe")
        assertContains(support, "Temper.MyTestLibrary.double(")
        assertContains(mix, "only: :test")
        // the JSON escapes each quote as \u0022
        assertContains(mix, "elixirc_paths(:test), do: [\\u0022lib\\u0022, \\u0022test/support\\u0022]")
        assertContains(fileContent(out, "something_test.exs"), "something/something.temper:6")
    }

    /**
     * The frontend evaluates a test of constants while compiling, and the
     * call goes with it: `foldedAway` is then referenced by nothing, which the
     * frontend counts as production, not test. `limit` is inlined into `under`,
     * so nothing reads it either. Neither is reachable from what the library
     * exports, nor from a test, so neither is generated. `helper` is reached
     * only by a test that is not folded, so it goes with the tests.
     */
    @Test
    fun whatProductionCannotReachStaysOutOfTheLibrary() {
        val out = generatedText(
            """
            |let limit = 5;
            |export let under(x: Int): Boolean { x < limit }
            |let foldedAway(x: Int): Int { x + 1 }
            |test("folds") { assert(foldedAway(2) == 3); }
            |let helper(x: Int): Int { x + 1 }
            |let check(test: Test, x: Int): Void { assert(helper(x) == x + 1); }
            |test("runs") { test => check(test, 2); }
            """.trimMargin(),
        )
        val library = fileContent(out, "temper_main.ex")
        val support = fileContent(out, "temper_tests.ex")
        assertContains(library, "def under(")
        for (name in listOf("foldedAway", "limit", "helper")) {
            assertFalse(name in library, "$name is in the library:\n$library")
        }
        // the folded assert still names it in its message; the function itself is gone
        assertFalse("def foldedAway" in support, "nothing reaches foldedAway, but it is generated:\n$support")
        assertContains(support, "def helper")
    }

    /**
     * Generated code is committed, so a new declaration should show in a diff
     * as itself, not as every name after it renumbered. Module functions,
     * values and tests get plain names, and a function's gensyms count from 0
     * in each function.
     */
    @Test
    fun aNewDeclarationRenamesNothingElse() {
        val source = """
            |let helper(x: Int): Int { var t = 0; for (var i = 0; i < x; i += 1) { t += i; } t }
            |export let total(x: Int): Int { helper(x) + helper(x + 1) }
            |let check(test: Test, x: Int): Void { assert(total(x) >= 0); }
            |test("totals") { test => check(test, 3); }
        """.trimMargin()
        val before = generatedText(source)
        val after = generatedText(
            "let added(x: Int): Int { var t = 1; for (var i = 0; i < x; i += 1) { t *= 2; } t }\n" +
                "export let alsoAdded(x: Int): Int { added(x) }\n" + source,
        )
        for (file in listOf("temper_main.ex", "temper_tests.ex")) {
            val old = fileContent(before, file)
            assertFalse(Regex("__[0-9]").containsMatchIn(old), "a numbered name in $file:\n$old")
            // every function of the old output is in the new one, as it was
            val defs = old.split("\\n  def ").drop(1).filter { !it.startsWith("__temper") && !it.startsWith("main") }
            for (def in defs) assertContains(fileContent(after, file), def)
        }
        assertContains(fileContent(before, "temper_tests.ex"), "def totals(test)")
    }

    /** A library with no tests gets no test directory and no test-only paths. */
    @Test
    fun aLibraryWithoutTestsHasNoTestTree() {
        val out = generatedText("export let double(x: Int): Int { x * 2 }")
        assertFalse("test_helper.exs" in out, out)
        assertFalse("elixirc_paths" in fileContent(out, "mix.exs"))
    }

    /**
     * A class that declares its inputs twice is rejected, and its constructor's
     * `this.schema = schema` is left writing a property the class no longer
     * has. Calling `Query.set_schema/2` there names a function that is never
     * defined: a compiler warning, and UndefinedFunctionError when Elixir code
     * constructs the class. It is broken code like the rest of the class.
     */
    @Test
    fun aWriteToAPropertyTheClassDoesNotHaveIsBrokenCode() {
        val out = generatedText(
            """
            |class Schema() {}
            |
            |class Query(public schema: Schema) {
            |  public constructor(schema: Schema) { this.schema = schema; }
            |}
            """.trimMargin(),
        )
        assertFalse("set_schema(" in out, "a call of a setter Query never defines:\n$out")
        assertContains(out, "broken code: write of .schema")
    }

    /** The same class, read: `get_schema/1` is never defined either. */
    @Test
    fun aReadOfAPropertyTheClassDoesNotHaveIsBrokenCode() {
        val out = generatedText(
            """
            |class Schema() {}
            |
            |class Query(public schema: Schema) {
            |  public constructor(schema: Schema) { }
            |  public peek(): Schema { this.schema }
            |}
            """.trimMargin(),
        )
        assertFalse("get_schema(" in out, "a call of a getter Query never defines:\n$out")
        assertContains(out, "broken code: read of .schema")
    }

    /**
     * A call the frontend could not type-check carries `invalidSig`: no fixed
     * parameters and a rest parameter of type *Invalid*. Packing by that
     * signature put every argument into one list, a call of `Query.new/1`
     * against a `new/2`. With the real arity unknown, the arguments go as
     * written, as they do in js.
     */
    @Test
    fun aCallWithNoSignatureKeepsItsArguments() {
        val out = generatedText(
            """
            |export class Query(public name: String, public conds: List<Nope>) {}
            |export let from(n: String): Query { new Query(n, []) }
            """.trimMargin(),
        )
        assertFalse("Query.new(%TemperCore.Vec" in out, "the arguments were packed into one list:\n$out")
        assertContains(out, "Query.new(n, ")
    }

    /**
     * A constructor call that did not type-check still has a constructor to
     * call, and that constructor's own signature knows its arity. Passing the
     * arguments as written called `new/2` against a `new/3` when the third
     * input has a default.
     */
    @Test
    fun aCallWithNoSignatureUsesItsConstructorsArity() {
        val out = generatedText(
            """
            |export class Query(public name: String, public conds: List<Nope>, public limit: Int = 10) {}
            |export let from(n: String): Query { new Query(n, []) }
            """.trimMargin(),
        )
        assertContains(out, "Query.new(n, %TemperCore.Vec{t: {}}, nil)")
    }

    /**
     * ExUnit refuses two tests with one name in a module, and two Temper modules
     * may each have a `test("same")`: `test/temper_test.exs` then failed to
     * compile ("test same is already defined"), so `mix test` ran nothing.
     * A repeated title is numbered.
     */
    @Test
    fun repeatedTestTitlesStillCompileUnderMixTest() {
        val out = generatedText(
            """
            |test("same") { assert(1 == 1) { "first" } }
            |test("same") { assert(2 == 2) { "second" } }
            """.trimMargin(),
        )
        assertContains(out, "test \\u0022same\\u0022 do")
        assertContains(out, "test \\u0022same (2)\\u0022 do")
    }

    /**
     * The generated entry point used to be `main/0`, so a library exporting
     * its own `main` got two `def main()`. The library's came first, so
     * running the program called it uninvited and never reached the entry
     * point's init and async drain. The entry point now has a name in the
     * backend's own `__temper_*__` space.
     */
    @Test
    fun aLibrarysOwnMainIsNotTheEntryPoint() {
        val out = generatedText(
            """
            |console.log("top level ran");
            |export let main(): Void { console.log("user main ran"); }
            """.trimMargin(),
        )
        assertEquals(1, Regex("""def main\(""").findAll(out).count(), "def main( should be the library's alone:\n$out")
        assertContains(out, "def __temper_main__(")
    }
}

/**
 * Every file the backend writes for one Temper source, as one JSON string.
 * Source maps are left out: they embed the Temper source, which would match
 * any search for a Temper name.
 */
private fun generatedText(temper: String): String {
    var text = ""
    assertGeneratedStructure(
        inputs = listOf(filePath("something", "something.temper") to temper),
        factory = ElixirBackend.Factory,
        backendConfig = Backend.Config.production,
        genre = Genre.Library,
        moduleResultNeeded = false,
    ) { text = FormattingStructureSink.toJsonString(it, filterKeys = { key -> !key.endsWith(".map") }) }
    return text
}

/** The JSON-escaped content of the generated file named [name], from [generatedText]. */
private fun fileContent(json: String, name: String): String {
    val at = json.indexOf("\"_name\": \"$name\"")
    check(at >= 0) { "no $name in:\n$json" }
    val content = json.indexOf("\"content\": \"", at) + "\"content\": \"".length
    return json.substring(content, json.indexOf("\"__DO_NOT_CARE__\"", content))
}
