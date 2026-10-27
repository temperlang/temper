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
    val otherName = if (listedName == "cobol") "fortran" else "cobol"
    val temper = """
        |@connected(["$listedName"])
        |export let viaConnected(i: Int): Int { i + 1 }
        |
        |@connected(["$otherName"])
        |export let viaBody(i: Int): Int { i + 2 }
    """.trimMargin()
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
    val files = outputRoot.fs.gatherFilesSync { path ->
        path.lastOrNull()?.fullName !in connectedFiles.keys
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
