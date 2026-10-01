package lang.temper.be.elixir

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedStructure
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.lexer.Genre
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertContains
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
     * its broken reads stopped crashing the translator.
     */
    @Test
    fun nothingAfterARaiseReadsTheNameItWouldHaveBound() {
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
            |  public kind(): String {
            |    let f = schema.getField();
            |    let fieldKind = f.fieldType;
            |    if (fieldKind == "Int") { "int" } else { "other" }
            |  }
            |}
            """.trimMargin(),
        )
        assertContains(out, "broken code")
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

    /** A library with no tests gets no test directory and no test-only paths. */
    @Test
    fun aLibraryWithoutTestsHasNoTestTree() {
        val out = generatedText("export let double(x: Int): Int { x * 2 }")
        assertFalse("test_helper.exs" in out, out)
        assertFalse("elixirc_paths" in fileContent(out, "mix.exs"))
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
