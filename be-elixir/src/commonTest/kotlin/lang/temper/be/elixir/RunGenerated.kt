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
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * What [temper], as the one module of a library, prints when its generated
 * Elixir runs: `mix compile`, then `mix run --no-compile`, as `temper run`
 * does. A test of what the code does, where a search of the generated text
 * would only say what it looks like. [then] is Elixir run after the
 * program, as code calling into the library would. Needs `mix` on the path.
 */
internal fun elixirOutput(temper: String, then: String = ""): String {
    val root = Files.createTempDirectory("be-elixir-run").toFile()
    try {
        for ((path, content) in elixirFiles(temper)) {
            File(root, path).apply { parentFile.mkdirs() }.writeText(content)
        }
        temperCoreSource().copyRecursively(File(root, "elixir/temper-core"))
        File(root, "elixir/temper-core/_build").deleteRecursively()
        val project = File(root, "elixir/$RUN_LIBRARY")
        mix(project, "compile")
        val main = "Temper.MyTestLibrary.${ElixirBackend.MAIN_FUNCTION}()"
        return mix(project, "run", "--no-compile", "-e", if (then.isEmpty()) main else "$main; $then")
    } finally {
        root.deleteRecursively()
    }
}

/** Minutes a test that runs [elixirOutput] may take: it compiles temper-core first. */
internal const val RUN_TEST_MINUTES = 5L

private const val RUN_LIBRARY = "my-test-library"

private fun mix(project: File, vararg args: String): String {
    val process = ProcessBuilder(listOf("mix") + args).directory(project).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    assertTrue(process.waitFor(RUN_TEST_MINUTES, TimeUnit.MINUTES), "mix ${args[0]} did not finish:\n$output")
    assertEquals(0, process.exitValue(), "mix ${args[0]} failed:\n$output")
    return output
}

/** Every file the backend writes for [temper], by path, source maps left out. */
private fun elixirFiles(temper: String): Map<String, String> {
    var json = ""
    assertGeneratedStructure(
        inputs = listOf(filePath("something", "something.temper") to temper),
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

/** temper-core's source, which the generated `mix.exs` expects beside the library. */
private fun temperCoreSource(): File {
    val relative = "src/commonMain/resources/lang/temper/be/elixir/temper-core"
    return generateSequence(File("").absoluteFile) { it.parentFile }
        .flatMap { sequenceOf(File(it, relative), File(it, "be-elixir/$relative")) }
        .firstOrNull { it.isDirectory }
        ?: fail("temper-core not found from ${File("").absolutePath}")
}
