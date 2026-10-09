package lang.temper.be.elixir

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedStructure
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.lexer.Genre
import lang.temper.log.filePath
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
     * A coroutine turned into a state machine starts every local it hoists
     * out of the generator at its type's zero value, and a List's is `[]`.
     * That arrived as a List value, and the build died with
     * `kotlin.NotImplementedError: value of type List: []`. It is an empty
     * Vec now, `%TemperCore.Vec{t: {}}`, as a list literal would be.
     */
    @Test
    @Timeout(value = RUN_TEST_MINUTES, unit = TimeUnit.MINUTES)
    fun aListHoistedOutOfAnAsyncBlockStartsEmpty() {
        val out = elixirOutput(
            """
            |let p = new PromiseBuilder<Int>();
            |async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |  let xs = [1, 2];
            |  let xss: List<List<Int>> = [[3], [4, 5]];
            |  let x = await p.promise orelse -1;
            |  console.log("${'$'}{x} ${'$'}{xs.length} ${'$'}{xss[1][1]}");
            |}
            |p.complete(3);
            """.trimMargin(),
        )
        assertEquals("3 2 5\n", out)
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
     * A local assigned in a `do` body before something in it bubbles must keep
     * that value in the `orelse`: js and py print `11` for this, be-elixir
     * printed `10`. An Elixir binding made inside `try` never reaches `rescue`,
     * so the local lives in a cell and the write survives the raise.
     */
    @Test
    fun anAssignmentBeforeABubbleReachesTheOrelse() {
        val out = generatedText(
            """
            |let fail(b: Boolean): Void throws Bubble { if (b) { bubble() } }
            |export let f(b: Boolean): Int {
            |  var x = 0;
            |  do { x = 1; fail(b); x = 2; } orelse do { x += 10; }
            |  x
            |}
            """.trimMargin(),
        )
        assertContains(out, "TemperCore.Heap.put(x, :v, 1)")
        assertFalse("_x = 1" in out, "the write before the bubble is a dead binding:\n$out")
    }

    /**
     * builtins.md says Float64 division by zero is a Bubble, and the
     * interpreter, py and rust agree. This backend gave js's Infinity, NaN
     * and -Infinity, on purpose. The first three lines are the docs example;
     * the rest divide by a zero the compiler cannot see, including -0.0, and
     * check that `%` follows `/` and that overflow still gives Infinity.
     */
    @Test
    @Timeout(value = RUN_TEST_MINUTES, unit = TimeUnit.MINUTES)
    fun float64DivisionAndRemainderByZeroBubble() {
        val out = elixirOutput(
            """
            |console.log("${'$'}{ (0.0 /  0.0).toString() orelse "Bubble" }");
            |console.log("${'$'}{ (1.0 /  0.0).toString() orelse "Bubble" }");
            |console.log("${'$'}{ (1.0 / -0.0).toString() orelse "Bubble" }");
            |let show(a: Float64, b: Float64): String {
            |  "${'$'}{(a / b).toString() orelse "Bubble"} ${'$'}{(a % b).toString() orelse "Bubble"}"
            |}
            |var z = 0.0;
            |z = z;
            |console.log(show(1.0, z));
            |console.log(show(0.0, z));
            |console.log(show(1.0, -z));
            |console.log(show(7.5, 2.0));
            |console.log(show(1.7976931348623157e308, 0.5));
            """.trimMargin(),
        )
        assertEquals(
            "Bubble\nBubble\nBubble\nBubble Bubble\nBubble Bubble\nBubble Bubble\n3.75 1.5\nInfinity 0.0\n",
            out,
        )
    }

    /**
     * An async block that ends with an `if`, after an `await ... orelse`,
     * printed the branch and then panicked with `broken code: (Block)`. The
     * coroutine converter dropped the `return doneResult()` at the end of
     * each branch by leaving an empty block where it was, and an empty block
     * is not a statement TmpL can translate. Java and Rust built the same
     * garbage. Neither the `if` nor the `orelse` alone was enough: the `if`
     * is only isolated as a block of its own when something before it yields.
     */
    @Test
    @Timeout(value = RUN_TEST_MINUTES, unit = TimeUnit.MINUTES)
    fun anAsyncBlockMayEndWithAnIf() {
        val out = elixirOutput(
            """
            |let p = new PromiseBuilder<Int>();
            |async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |  let v = await p.promise orelse -1;
            |  if (v < 0) { console.log("neg"); } else { console.log("pos ${'$'}{v}"); }
            |}
            |let go(n: Int): Void {
            |  async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |    let q = new PromiseBuilder<Int>();
            |    q.complete(n);
            |    let w = await q.promise orelse -1;
            |    if (w > 0) { console.log("go ${'$'}{w}"); }
            |  }
            |}
            |var n = 4;
            |n = n;
            |go(n);
            |p.complete(3);
            """.trimMargin(),
        )
        assertEquals("pos 3\ngo 4\n", out)
    }

    /**
     * A local in a cell can be assigned something that raises: the frontend
     * lowers `g(b) orelse panic()` by assigning a temporary in a `do`, and the
     * `orelse` side is `panic()`. `TemperCore.Heap.put(t, :v, raise(...))` is
     * a store of a value that never arrives, which Elixir's type checker warns
     * about, as it does for `t = raise(...)`. alloy printed 55 of these once
     * locals assigned in a `do` became cells.
     */
    @Test
    fun aRaiseStoredInACellIsJustTheRaise() {
        val out = generatedText(
            """
            |let g(b: Boolean): Int throws Bubble { if (b) { bubble() } 1 }
            |export let f(b: Boolean): Int {
            |  var x = 0;
            |  do { x = g(b) orelse panic(); } orelse do { x = 2; }
            |  x
            |}
            """.trimMargin(),
        )
        assertFalse(Regex("""Heap\.put\([^\n]*, raise\(""").containsMatchIn(out), "a raise stored into a cell:\n$out")
    }

    /**
     * `a` calls `b` before `b` is declared, so the frontend hoists `b` above
     * `base`. An Elixir closure captures values when it is made, so `b`'s
     * `fn` made there names a `base` that is not bound yet, and the module
     * did not compile ("undefined variable base"). js and py print `34 23`.
     * `b` still gets its cell at the top of the block, for `a` to call
     * through, but is stored into it after `base` exists.
     */
    @Test
    fun aHoistedFunctionIsMadeAfterTheLocalsItCaptures() {
        val out = generatedText(
            """
            |export let f(n: Int): Int {
            |  let base = n * 10;
            |  let a(k: Int): Int { b(k) + 1 }
            |  let b(k: Int): Int { base + k }
            |  a(n)
            |}
            """.trimMargin(),
        )
        val bound = out.indexOf("base = TemperCore.int32(n * 10)")
        val stored = out.indexOf("TemperCore.Heap.put(b, :v, fn")
        assertTrue(bound >= 0 && stored >= 0, "expected both a binding of base and a store of b:\n$out")
        assertTrue(bound < stored, "b's closure is made before base is bound:\n$out")
    }

    /**
     * `var f = fn ...; f = g;` at the top level arrives as a module function
     * named f and an assignment to it. JS and Python rebind a function's
     * name, but a `defp` cannot be rebound: the assignment bound a local that
     * nothing read, and every call still ran the first body (this printed
     * `5 5 5 5 3`). f is a module-level value now. `install` rebinds it from
     * inside a function, and `g` keeps the value f had when g was made.
     *
     * Statements run in source order, so `before` and `g` see the identity
     * function and `middle` sees dbl; js and py print the same
     * `5 10 15 5 3`. This used to expect `10 10 15 10 3`, which was #540:
     * the frontend hoisted `f = dbl` above `before` on every backend.
     */
    @Test
    @Timeout(value = RUN_TEST_MINUTES, unit = TimeUnit.MINUTES)
    fun aTopLevelVarHoldingAFunctionCanBeRebound() {
        val out = elixirOutput(
            """
            |let dbl(x: Int): Int { x * 2 }
            |let tpl(x: Int): Int { x * 3 }
            |var f = fn (x: Int): Int { x };
            |var calls = 0;
            |let fire(v: Int): Int { calls += 1; f(v) }
            |let install(): Void { f = tpl; }
            |var seed = 5;
            |seed = seed;
            |let before = fire(seed);
            |let g = f;
            |f = dbl;
            |let middle = fire(seed);
            |install();
            |console.log("${'$'}{before} ${'$'}{middle} ${'$'}{fire(seed)} ${'$'}{g(seed)} ${'$'}{calls}");
            """.trimMargin(),
        )
        assertEquals("5 10 15 5 3\n", out)
    }

    /**
     * An exported f that is rebound is called as `Temper.Lib.f(x)` by another
     * library, and by Elixir code. That `def f` calls whatever f holds now;
     * the first body is a `defp` of its own. Before, another library calling
     * f after `install()` still got the first body.
     */
    @Test
    @Timeout(value = RUN_TEST_MINUTES, unit = TimeUnit.MINUTES)
    fun anExportedFunctionThatIsReboundIsCalledAsItIsNow() {
        val out = elixirOutput(
            """
            |let dbl(x: Int): Int { x * 2 }
            |export var f = fn (x: Int): Int { x };
            |export let install(): Void { f = dbl; }
            """.trimMargin(),
            then = "lib = Temper.MyTestLibrary; IO.puts(lib.f(5)); lib.install(); IO.puts(lib.f(5))",
        )
        assertEquals("5\n10\n", out)
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

    /**
     * Temper no longer has a generic `==`: a class has equality only if it
     * declares an `@operator("==")` method. `==` on two instances of one
     * that does not is a type error on every backend, so this backend writes
     * the located panic it writes for any rejected expression, and the
     * struct is still a struct.
     */
    @Test
    fun equalityOnAClassWithoutOneIsBrokenCode() {
        val out = generatedText(
            """
            |@imu export class V(public x: Int) {}
            |export let same(a: V, b: V): Boolean { a == b }
            """.trimMargin(),
        )
        assertContains(out, "defstruct [:x]")
        assertContains(out, "raise(TemperCore.Panic, \\u0022broken code: ")
    }

    /**
     * The frontend writes `a < b` as `(a <=> b) < 0` for every type but
     * Int32. Strings, Int64s and Booleans order in the BEAM as in Temper, so
     * they go back to the infix operator; a Float64 does not (`-0.0`, NaN).
     */
    @Test
    fun comparisonsTheBeamOrdersRightAreInfix() {
        val out = generatedText(
            """
            |export let s(a: String, b: String): Boolean { a < b }
            |export let l(a: Int64, b: Int64): Boolean { a >= b }
            |export let f(a: Float64, b: Float64): Boolean { a < b }
            """.trimMargin(),
        )
        // generatedText is JSON: `<` is \u003c and `>` is \u003e
        assertContains(out, "a \\u003c b")
        assertContains(out, "a \\u003e= b")
        assertContains(out, "TemperCore.Float.cmp(a, b) \\u003c 0")
        assertFalse("TemperCore.cmp(" in out, out)
    }

    /**
     * What Elixir can call is what Temper exports. A non-exported function
     * only the root module calls is `defp`, called locally; one a class's
     * method calls stays `def`, since a remote call cannot reach a private
     * function, and is `@doc false`: callable, but not the library's API.
     */
    @Test
    fun onlyExportedFunctionsArePublic() {
        val out = generatedText(
            """
            |let helper(x: Int): Int { x + 1 }
            |let shared(x: Int): Int { x * 2 }
            |export class C(public n: Int) {
            |  public twice(): Int { shared(n) }
            |}
            |export let api(x: Int): Int { helper(x) + shared(x) }
            """.trimMargin(),
        )
        assertContains(out, "defp helper(")
        assertContains(out, "helper(x)")
        assertFalse("Temper.MyTestLibrary.helper(" in out, out)
        assertContains(out, "@doc false\\n  @spec shared(")
        assertContains(out, "  def shared(")
        assertContains(out, "  def api(")
    }

    /**
     * `@keep` is how a library keeps an unexported helper for its connected
     * code, which nothing in Temper calls. The frontend keeps it in the tree,
     * so it must be generated, under its plain name, and public: connected
     * Elixir code lives in another module, and a remote call cannot reach a
     * `defp`. It was dropped as unreached, the same as an unkept helper.
     */
    @Test
    fun keptFunctionsAreGeneratedAndCallable() {
        val out = generatedText(
            """
            |@keep let sumOf3(a: Int, b: Int, c: Int): Int { a + b + c }
            |let unkept(x: Int): Int { x + 1 }
            |export let api(x: Int): Int { x }
            """.trimMargin(),
        )
        assertContains(out, "@doc false\\n  @spec sumOf3(")
        assertContains(out, "  def sumOf3(a, b, c)")
        assertFalse("unkept" in out, out)
    }

    /**
     * A doc comment becomes `@doc`, or `@moduledoc` for a class, so IEx's `h`
     * and ExDoc show it. A private function's would be discarded with a
     * warning, so it gets none.
     */
    @Test
    fun docCommentsAreDocs() {
        val out = generatedText(
            """
            |/** Adds one. */
            |let helper(x: Int): Int { x + 1 }
            |/** Doubles, then adds one. */
            |export let api(x: Int): Int { helper(x * 2) }
            |/** A point on the plane. */
            |export class P(public x: Int) {}
            """.trimMargin(),
        )
        // the generated text is JSON: a quote is \\u0022 and a newline \\n
        val heredoc = "\\u0022\\u0022\\u0022"
        assertContains(out, "@doc $heredoc\\n  Doubles, then adds one.\\n  $heredoc\\n  @spec api(")
        assertContains(out, "@moduledoc $heredoc\\n  A point on the plane.")
        assertFalse("Adds one." in out, out)
    }

    /**
     * The frontend writes a `return` inside a loop as an assignment and a
     * `break` out of a block around the body. Neither is thrown: the loop
     * hands the break back to its call site, and the block's way out is the
     * function's result. A `return` in a bubble's `try` at the end of a
     * function is that function's result too, and an exit inside an `if` or
     * a `try` in the middle of a list is handed back to a `case` after it.
     */
    @Test
    fun returnsFromLoopsAreNotThrown() {
        val out = generatedText(
            """
            |export let find(xs: List<Int>, want: Int): Int {
            |  for (var i = 0; i < xs.length; ++i) {
            |    if (xs[i] == want) { return i; }
            |  }
            |  -1
            |}
            |export let pairAt(xs: List<Int>, t: Int): Int {
            |  for (var i = 0; i < xs.length; ++i) {
            |    for (var j = i + 1; j < xs.length; ++j) {
            |      if (xs[i] + xs[j] == t) { return i * 100 + j; }
            |    }
            |  }
            |  -1
            |}
            |export let intOr(s: String, d: Int): Int {
            |  return s.toInt32() orelse d;
            |}
            |export let midIf(xs: List<Int>, flag: Boolean): Int {
            |  var r = 0;
            |  if (flag) {
            |    for (var i = 0; i < xs.length; ++i) { if (xs[i] == 3) { return 333; } r += xs[i]; }
            |    r += 1000;
            |  }
            |  r
            |}
            |export let parsedSum(xs: List<String>): Int {
            |  var t = 0;
            |  for (var i = 0; i < xs.length; ++i) {
            |    do { t += xs[i].toInt32(); } orelse do { return -1; }
            |    t += 1;
            |  }
            |  t
            |}
            """.trimMargin(),
        )
        assertFalse("throw(" in out, out)
        assertFalse("catch" in out, out)
        assertContains(out, "{:temper_break, :ex_block_1, return}")
    }

    /**
     * Each relay is an actor that wakes, in a turn of its own, when the
     * promise before it settles, after the top level has finished. js runs
     * those steps on the queue it drains before exiting, so it prints all
     * three. `__temper_main__/0` used to return once its own queue was empty,
     * and `mix run` exited with the relays still working: only `main done`
     * was printed. It now waits until no actor has work left, which does not
     * depend on how long the work takes.
     */
    @Test
    @Timeout(value = RUN_TEST_MINUTES, unit = TimeUnit.MINUTES)
    fun actorWorkAfterTheTopLevelEndsIsNotCutOff() {
        val out = elixirOutput(
            """
            |@actor export class Relay(private var name: Int) {
            |  public relay(p: Promise<Int>): Promise<Int> {
            |    let out = new PromiseBuilder<Int>();
            |    async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |      let v = await p orelse -1;
            |      var sum = 0;
            |      for (var i = 0; i < 300000; ++i) { sum += i % 7; }
            |      console.log("relay ${'$'}{name.toString()} got ${'$'}{v.toString()}");
            |      out.complete(v + sum - sum + 1);
            |    }
            |    out.promise
            |  }
            |}
            |let first = new PromiseBuilder<Int>();
            |var p: Promise<Int> = first.promise;
            |for (var i = 0; i < 3; ++i) { p = new Relay(i).relay(p); }
            |console.log("main done");
            |first.complete(10);
            """.trimMargin(),
        )
        assertEquals("main done\nrelay 0 got 10\nrelay 1 got 11\nrelay 2 got 12\n", out)
    }

    /**
     * An `@actor` the checker rejects stops the build with its located message
     * and nothing else. A class that is both `@actor` and `@imu` used to go on
     * to the translator, whose TODO for that shape ended the build in a stack
     * trace under the message that had already explained it.
     */
    @Test
    fun aRejectedActorIsReportedAndNotTranslated() {
        val out = generatedText(
            """
            |@actor @imu export class Point(public x: Int) {}
            |export let one(): Int { new Point(1).x }
            """.trimMargin(),
        )
        assertContains(out, "Class Point cannot be both @actor and @imu")
        assertFalse("mix.exs" in out, "a project for a program the backend rejected:\n$out")
        assertFalse(".ex\"" in out, "Elixir for a program the backend rejected:\n$out")
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
