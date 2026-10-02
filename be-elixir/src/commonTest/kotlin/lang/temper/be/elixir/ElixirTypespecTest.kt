package lang.temper.be.elixir

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedStructure
import lang.temper.common.RSuccess
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.lexer.Genre
import lang.temper.log.filePath
import org.junit.jupiter.api.Timeout
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Generated code carries typespecs, and they are true.
 *
 * Every `def` has an `@spec` and every class module a `@type t`; and Dialyzer,
 * run over the generated library and temper-core, finds no spec the code
 * contradicts, no call that breaks one, and no type that does not exist. A
 * spec nothing checks can be wrong without anyone knowing, so the second test
 * is the one that matters. It needs `elixir` on the path, as the functional
 * tests need `mix`.
 */
class ElixirTypespecTest {
    @Test
    fun everyFunctionHasASpecAndEveryClassAType() {
        val main = generatedFiles().getValue("elixir/my-test-library/lib/temper_main.ex")
        val lines = main.lines()
        val unspecced = lines.withIndex().filter { (i, line) ->
            val def = Regex("""^\s*defp? ([a-zA-Z_?!0-9]+)\(""").find(line) ?: return@filter false
            val name = def.groupValues[1]
            !lines.subList(0, i).asReversed().first { it.isNotBlank() }.trimStart().startsWith("@spec $name(")
        }
        assertEquals(listOf(), unspecced.map { it.value.trim() }, "defs with no @spec just before them:\n$main")
        val modules = lines.count { it.trimStart().startsWith("defmodule ") }
        val types = lines.count { it.trimStart().startsWith("@type t() ::") }
        // the root module has functions, not values of its own
        assertEquals(modules - 1, types, main)
    }

    // The build's default is 30 s per test. On a fresh machine, Dialyzer first
    // builds its PLT of Erlang/OTP and Elixir, which takes minutes.
    @Test
    @Timeout(value = TEST_MINUTES, unit = TimeUnit.MINUTES)
    fun dialyzerFindsNoSpecTheCodeContradicts() {
        val output = dialyze(generatedFiles())
        val specWarnings = output.lines().filter { it.startsWith("SPEC ") }
        assertTrue("SPEC-TOTAL 0" in output, "Dialyzer found specs the code contradicts:\n$specWarnings\n\n$output")
    }

    /**
     * The types say which class an object is: a constructor whose spec names
     * another class is found out, and so is a call passing one class's object
     * where another's is wanted. When every heap object had the type
     * `TemperCore.Ref.t()`, neither was.
     */
    @Test
    @Timeout(value = TEST_MINUTES, unit = TimeUnit.MINUTES)
    fun dialyzerTellsOneClassFromAnother() {
        val main = "elixir/my-test-library/lib/temper_main.ex"
        val counter = "Temper.MyTestLibrary.Counter.t()"
        val square = "Temper.MyTestLibrary.Square.t()"
        for ((what, from, to) in listOf(
            Triple("a constructor returning another class", "@spec new() :: $counter", "@spec new() :: $square"),
            Triple("a method taking another class", "@spec bump($counter) :: nil", "@spec bump($square) :: nil"),
        )) {
            val files = generatedFiles()
            val source = files.getValue(main)
            assertTrue(from in source, "no `$from` in:\n$source")
            val output = dialyze(files + (main to source.replace(from, to)))
            assertTrue("SPEC-TOTAL 0" !in output && "SPEC " in output, "Dialyzer did not object to $what:\n$output")
        }
    }

    /**
     * A false spec on a function whose result comes out of a loop is found
     * out: `total`'s sum, and `upTo`'s list. When a loop was a closure passed
     * to itself, what it returned was `any()` to Dialyzer, and neither was.
     */
    @Test
    @Timeout(value = TEST_MINUTES, unit = TimeUnit.MINUTES)
    fun dialyzerSeesThroughLoops() {
        val main = "elixir/my-test-library/lib/temper_main.ex"
        for ((from, to) in listOf(
            "@spec total(TemperCore.List.list_in(integer())) :: integer()" to
                "@spec total(TemperCore.List.list_in(integer())) :: String.t()",
            "@spec upTo(integer()) :: TemperCore.Vec.t(integer())" to "@spec upTo(integer()) :: String.t()",
        )) {
            val files = generatedFiles()
            val source = files.getValue(main)
            assertTrue(from in source, "no `$from` in:\n$source")
            val output = dialyze(files + (main to source.replace(from, to)))
            assertTrue("SPEC-TOTAL 0" !in output && "SPEC " in output, "Dialyzer did not object to `$to`:\n$output")
        }
    }

    /** Dialyzer's report on [files] beside temper-core: each warning, then SPEC-TOTAL and TOTAL. */
    private fun dialyze(files: Map<String, String>): String {
        val root = Files.createTempDirectory("be-elixir-typespecs").toFile()
        for ((path, content) in files) {
            File(root, path).apply { parentFile.mkdirs() }.writeText(content)
        }
        temperCore().copyRecursively(File(root, "elixir/temper-core"))
        File(root, "elixir/temper-core/_build").deleteRecursively()
        val script = File(root, "dialyze.exs")
        script.writeText(
            ElixirTypespecTest::class.java.getResource("dialyze.exs")?.readText()
                ?: fail("no dialyze.exs beside this test"),
        )
        val plt = File("build/dialyzer/base.plt").absoluteFile
        val process = ProcessBuilder("elixir", script.path, plt.path, File(root, "elixir/my-test-library").path)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(DIALYZER_MINUTES, TimeUnit.MINUTES), "Dialyzer did not finish:\n$output")
        assertEquals(0, process.exitValue(), output)
        root.deleteRecursively()
        return output
    }

    /** temper-core's source, which the generated `mix.exs` expects beside the library. */
    private fun temperCore(): File {
        val relative = "src/commonMain/resources/lang/temper/be/elixir/temper-core"
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, relative), File(it, "be-elixir/$relative")) }
            .firstOrNull { it.isDirectory }
            ?: fail("temper-core not found from ${File("").absolutePath}")
    }
}

private const val DIALYZER_MINUTES = 10L

/** Past [DIALYZER_MINUTES], so a slow Dialyzer fails with its own output, not JUnit's timeout. */
private const val TEST_MINUTES = 15L

/**
 * One library that reaches every kind of spec: an `@imu` struct, a heap
 * class, an actor, an interface and a generic class; lists, maps, pairs and
 * builders; a nullable result, an optional parameter, a function type, a
 * function that bubbles, `Int64`, `Boolean`, a string index; and a List
 * returned as it came in, which Elixir code may pass as a plain list.
 */
private val FIXTURE = """
    |export interface Shape { public area(): Float64; }
    |
    |@imu export class Point(public x: Int, public y: Int) {
    |  public plus(o: Point): Point { new Point(x + o.x, y + o.y) }
    |  public static origin(): Point { new Point(0, 0) }
    |}
    |
    |export class Counter {
    |  public var count: Int = 0;
    |  public bump(): Void { count += 1; }
    |}
    |
    |@actor export class Tally {
    |  public var n: Int = 0;
    |  public add(k: Int): Int { n += k; n }
    |}
    |
    |export class Square(public side: Float64) extends Shape {
    |  public area(): Float64 { side * side }
    |}
    |
    |export class Box<T>(public item: T) {
    |  public get(): T { item }
    |}
    |
    |export let total(xs: List<Int>): Int {
    |  var t = 0;
    |  for (let x of xs) { t += x; }
    |  t
    |}
    |
    |export let lookup(m: Map<String, Int>, k: String): Int { m.getOr(k, -1) }
    |
    |export let firstOrNull(xs: List<String>): String? {
    |  if (xs.length > 0) { xs[0] } else { null }
    |}
    |
    |export let greet(name: String, greeting: String = "hi"): String { "${'$'}{greeting} ${'$'}{name}" }
    |
    |export let twice(f: fn (Int): Int, x: Int): Int { f(f(x)) }
    |
    |export let half(n: Int): Int throws Bubble {
    |  if (n % 2 != 0) { bubble() }
    |  n / 2
    |}
    |
    |export let big(n: Int64): Int64 { n * 2i64 }
    |
    |export let flip(b: Boolean): Boolean { !b }
    |
    |export let pairs(k: String, v: Int): List<Pair<String, Int>> { [new Pair(k, v)] }
    |
    |export let shout(s: String): String {
    |  let sb = new StringBuilder();
    |  sb.append(s);
    |  sb.append("!");
    |  sb.toString()
    |}
    |
    |export let upTo(n: Int): List<Int> {
    |  let b = new ListBuilder<Int>();
    |  for (var i = 0; i < n; ++i) { b.add(i); }
    |  b.toList()
    |}
    |
    |export let contains(s: String, t: String): Boolean { s.indexOf(t) is StringIndex }
    |
    |export let areas(shapes: List<Shape>): Float64 {
    |  var a = 0.0;
    |  for (let s of shapes) { a += s.area(); }
    |  a
    |}
    |
    |export let nonEmpty(xs: List<Int>): List<Int>? {
    |  if (xs.length > 0) { xs } else { null }
    |}
    |
    |export class Shelf(public books: List<String>) {
    |  public first(): String? { if (books.length > 0) { books[0] } else { null } }
    |}
    |
    |export let fresh(): Int {
    |  let c = new Counter();
    |  c.bump();
    |  c.count
    |}
""".trimMargin()

/** Every file the backend writes for [FIXTURE], by its path under the output root. */
private fun generatedFiles(): Map<String, String> {
    var json = ""
    assertGeneratedStructure(
        inputs = listOf(filePath("something", "something.temper") to FIXTURE),
        factory = ElixirBackend.Factory,
        backendConfig = Backend.Config.production,
        genre = Genre.Library,
        moduleResultNeeded = false,
    ) { json = FormattingStructureSink.toJsonString(it, filterKeys = { key -> !key.endsWith(".map") }) }
    val tree = (JsonValue.parse(json, tolerant = true) as? RSuccess)?.result as? JsonObject
        ?: fail("generated structure is not JSON:\n$json")
    val files = mutableMapOf<String, String>()
    fun walk(node: JsonObject, path: List<String>) {
        for (property in node) {
            if (property.key.startsWith("_")) continue
            val child = property.value as? JsonObject ?: continue
            val here = path + property.key
            when ((child.getOrNull("_type") as? JsonString)?.s) {
                "dir" -> walk(child, here)
                else -> (child.getOrNull("content") as? JsonString)?.let { files[here.joinToString("/")] = it.s }
            }
        }
    }
    walk(tree, listOf())
    return files
}
