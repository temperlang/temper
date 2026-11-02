package lang.temper.be

import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.lexer.Genre
import lang.temper.log.FilePath
import lang.temper.log.filePath
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** What [generateConnectedFallbackCode] produced. */
class ConnectedFallbackCode(
    /** Generated files, except copies of connected files, by path. */
    val files: Map<FilePath, String>,
    /** Messages for errors logged while building. */
    val errors: List<String>,
) {
    /** All the generated code, for checking what it calls. */
    val allText: String get() = files.values.joinToString("\n")

    /** Like [allText] but without source maps, which quote the Temper source. */
    val codeText: String get() = files.filterKeys { it.lastOrNull()?.extension != ".map" }.values.joinToString("\n")
}

/**
 * Generates code from a module, `foo/foo.temper`, with two `@connected` functions that have
 * Temper bodies: `viaConnected`, whose backend list names [listedName], by default [factory]'s
 * backend, and `viaBody`, whose list names only some other backend. Backend tests can check
 * that `viaConnected` calls into [connectedFiles] and `viaBody` runs its body, and that both run
 * their bodies when [noConnected].
 *
 * @param connectedFiles connected files to put beside the module, by file name.
 */
fun <BACKEND : Backend<BACKEND>> generateConnectedFallbackCode(
    factory: Backend.Factory<BACKEND>,
    connectedFiles: Map<String, String>,
    noConnected: Boolean = false,
    listedName: String = factory.backendId.uniqueId,
): ConnectedFallbackCode {
    val otherName = otherBackendName(listedName)
    val temper = """
        |@connected(["$listedName"])
        |export let viaConnected(i: Int): Int { i + 1 }
        |
        |@connected(["$otherName"])
        |export let viaBody(i: Int): Int { i + 2 }
    """.trimMargin()
    return generateFooCode(factory, temper, connectedFiles, noConnected)
}

/**
 * Like [generateConnectedFallbackCode] but for `@connected` classes with complete Temper
 * implementations: `ViaConnected`, whose backend list names [listedName], and `ViaBody`, whose
 * list names only some other backend. Each has a method `get(): Int`.
 */
fun <BACKEND : Backend<BACKEND>> generateConnectedTypeFallbackCode(
    factory: Backend.Factory<BACKEND>,
    connectedFiles: Map<String, String>,
    noConnected: Boolean = false,
    listedName: String = factory.backendId.uniqueId,
): ConnectedFallbackCode {
    val otherName = otherBackendName(listedName)
    val temper = """
        |@connected(["$listedName"])
        |export class ViaConnected {
        |  public get(): Int { 1 }
        |}
        |
        |@connected(["$otherName"])
        |export class ViaBody {
        |  public get(): Int { 2 }
        |}
        |
        |export let sum(): Int { new ViaConnected().get() + new ViaBody().get() }
    """.trimMargin()
    return generateFooCode(factory, temper, connectedFiles, noConnected, byContent = true)
}

private fun otherBackendName(listedName: String) = if (listedName == "cobol") "fortran" else "cobol"

private fun <BACKEND : Backend<BACKEND>> generateFooCode(
    factory: Backend.Factory<BACKEND>,
    temper: String,
    connectedFiles: Map<String, String>,
    noConnected: Boolean,
    byContent: Boolean = false,
): ConnectedFallbackCode {
    val inputs = buildList {
        add(filePath("foo", "foo.temper") to temper)
        for ((name, content) in connectedFiles) {
            add(filePath("foo", name) to content)
        }
    }
    val logSink = ListBackedLogSink()
    val outputRoot = generateCode(
        inputs = inputs,
        factory = factory,
        backendConfig = Backend.Config.production.copy(noConnected = noConnected),
        genre = Genre.Library,
        moduleResultNeeded = false,
        logSink = logSink,
    )
    val files = when {
        // A generated class's file may share a connected file's name, as in `ViaConnected.java`,
        // so leave out connected files by content instead.
        byContent -> outputRoot.fs.gatherFilesSync { true }.filterValues { it !in connectedFiles.values }
        else -> outputRoot.fs.gatherFilesSync { path -> path.lastOrNull()?.fullName !in connectedFiles.keys }
    }
    val errors = logSink.allEntries.filter { it.level >= Log.Error }.map { it.template.format(it.values) }
    return ConnectedFallbackCode(files = files, errors = errors)
}

/**
 * Checks per [generateConnectedFallbackCode] that [factory]'s backend calls connected code for
 * a `@connected` function only when its list names the backend and not under `--no-connected`.
 *
 * @param connectedFiles define the function `viaConnected` but not `viaBody`.
 * @param listedCall the start of a call to connected `viaConnected`, as in `_connected.via_connected(`.
 * @param unlistedCall likewise for `viaBody`, which should never happen.
 */
fun <BACKEND : Backend<BACKEND>> assertConnectedFallbacks(
    factory: Backend.Factory<BACKEND>,
    connectedFiles: Map<String, String>,
    listedCall: String,
    unlistedCall: String,
) {
    val usual = generateConnectedFallbackCode(factory, connectedFiles)
    assertEquals(listOf(), usual.errors)
    assertContains(usual.allText, listedCall, message = "listed")
    assertFalse(unlistedCall in usual.allText, "unlisted")

    val noConnected = generateConnectedFallbackCode(factory, connectedFiles, noConnected = true)
    assertEquals(listOf(), noConnected.errors)
    assertFalse(listedCall in noConnected.allText, "listed under --no-connected")
    assertFalse(unlistedCall in noConnected.allText, "unlisted under --no-connected")
}

/**
 * Checks per [generateConnectedTypeFallbackCode] that [factory]'s backend uses connected code
 * for a `@connected` class only when its list names the backend and not under `--no-connected`,
 * and otherwise generates the class from its Temper implementation.
 *
 * @param connectedFiles define the class `ViaConnected` but not `ViaBody`.
 * @param generatedClass the text that starts a generated definition of the named class, as
 *   in `class ViaBody`, which the generated code has only where the class isn't connected.
 */
fun <BACKEND : Backend<BACKEND>> assertConnectedTypeFallbacks(
    factory: Backend.Factory<BACKEND>,
    connectedFiles: Map<String, String>,
    generatedClass: (String) -> String,
) {
    val usual = generateConnectedTypeFallbackCode(factory, connectedFiles)
    assertEquals(listOf(), usual.errors)
    assertFalse(generatedClass("ViaConnected") in usual.codeText, "listed")
    assertContains(usual.codeText, generatedClass("ViaBody"), message = "unlisted")

    val noConnected = generateConnectedTypeFallbackCode(factory, connectedFiles, noConnected = true)
    assertEquals(listOf(), noConnected.errors)
    assertContains(noConnected.codeText, generatedClass("ViaConnected"), message = "listed under --no-connected")
    assertContains(noConnected.codeText, generatedClass("ViaBody"), message = "unlisted under --no-connected")
}
