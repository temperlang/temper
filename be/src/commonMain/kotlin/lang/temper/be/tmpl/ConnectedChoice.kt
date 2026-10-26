package lang.temper.be.tmpl

import lang.temper.common.Log
import lang.temper.log.FilePath
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.ModuleName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedParsedName
import lang.temper.value.FnParts
import lang.temper.value.MetadataMap
import lang.temper.value.connectedBackendsFrom
import lang.temper.value.connectedSymbol
import lang.temper.value.isAbstractPanicBody
import lang.temper.value.isCore
import lang.temper.value.valueContained

// Pure-Temper fallbacks for user-space `@connected` functions.
//
// A user-space `@connected` function may have a Temper body, its reference implementation, and
// may list the backends that have native code for it, as in `@connected(["cpp", "rust"])`.
// While translating to TmpL, each backend decides which to use:
//
// - A listed backend uses native code from the module's connected files (`_connected.rs`,
//   `__connected__.py`, and so on) through the same generated wrapper as always, and it's an
//   error if it seems to have none.
// - Other backends use the body, just as for any function. The interpreter always does.
// - Without a list, every backend must supply native code, as before lists existed, and the
//   body, if any, serves the interpreter.
// - Under `--no-connected` ([lang.temper.be.Backend.Config.noConnected]), every backend uses
//   the body wherever there is one.
//
// Backends recognize connected functions by their `@connected` metadata, so using the body
// just means leaving that metadata off the TmpL declaration. The frontend never inlines or
// precomputes calls to these functions, since the answer depends on the backend.
//
// Core and std connected code isn't affected by any of this.

/** Which implementation a backend uses for a user-space `@connected` function. */
internal enum class ConnectedChoice {
    /** Call native code from the backend's connected files. */
    Native,

    /** Translate the Temper body like any other function. */
    Body,
}

/**
 * Decides which implementation a backend uses for a user-space `@connected` function.
 *
 * @param listedBackends from `@connected([...])`, or null for a plain `@connected`.
 * @param hasBody whether the function has a Temper body. Lists require bodies, which the
 *   frontend checks, so without one, we use native code as before lists.
 * @param backendNames the names by which [listedBackends] can mean this backend.
 */
internal fun chooseConnected(
    listedBackends: List<String>?,
    hasBody: Boolean,
    backendNames: Set<String>,
    noConnected: Boolean,
): ConnectedChoice = when {
    !hasBody -> ConnectedChoice.Native
    noConnected -> ConnectedChoice.Body
    listedBackends == null -> ConnectedChoice.Native
    listedBackends.any { it in backendNames } -> ConnectedChoice.Native
    else -> ConnectedChoice.Body
}

/**
 * Adjusts the TmpL [metadata] of a function declaration per [chooseConnected], dropping
 * `@connected` metadata when the body should be used, and reporting a listed backend that
 * seems to lack native code.
 */
internal fun TmpLTranslator.metadataForConnectedChoice(
    pos: Position,
    name: ResolvedName,
    parts: FnParts?,
    declMetadata: MetadataMap,
    metadata: List<TmpL.DeclarationMetadata>,
): List<TmpL.DeclarationMetadata> {
    if (parts == null || metadata.none { it.key.symbol == connectedSymbol }) {
        return metadata
    }
    // Leave core and std alone, as their connected code works differently.
    if (document.isCore || libraryConfigurations.currentLibraryConfiguration.libraryName.isStdLib) {
        return metadata
    }
    val backend = dependencyResolver.backend
    val listedBackends = connectedBackendsFrom(
        (declMetadata[connectedSymbol] ?: parts.metadataSymbolMap[connectedSymbol])?.target?.valueContained,
    )
    val choice = chooseConnected(
        listedBackends = listedBackends,
        hasBody = !parts.body.isAbstractPanicBody(),
        backendNames = backend.connectedBackendNames,
        noConnected = backend.config.noConnected,
    )
    return when (choice) {
        ConnectedChoice.Body -> metadata.filter { it.key.symbol != connectedSymbol }
        ConnectedChoice.Native -> {
            if (listedBackends != null) {
                checkNativeConnected(pos, name, backend.rawBackendFiles)
            }
            metadata
        }
    }
}

/**
 * Reports an error if this backend's connected files beside the module don't seem to define
 * [name]. We can't parse every backend language, so this checks only that some file of this
 * backend's types in the module's directory mentions the name as an identifier, ignoring case
 * and underscores to allow for each backend's naming style, like `sum_of3` or `SumOf3` for
 * `sumOf3`. The target language's compiler or runtime still catches anything subtler.
 */
private fun TmpLTranslator.checkNativeConnected(
    pos: Position,
    name: ResolvedName,
    rawBackendFiles: Map<FilePath, String>,
) {
    val baseName = (name as? ResolvedParsedName)?.baseName?.nameText ?: return
    val moduleDir = (document.context.namingContext.loc as? ModuleName)?.sourceFile?.let {
        if (it.isDir) it else it.dirName()
    } ?: return
    // Backends get only files with their own extensions.
    val nativeFiles = rawBackendFiles.filterKeys { it.dirName() == moduleDir }
    val problem = when {
        nativeFiles.isEmpty() -> "found no connected files for it in $moduleDir"
        nativeFiles.values.none { mentionsNormalizedIdentifier(it, normalizeConnectedName(baseName)) } ->
            "found no definition in ${nativeFiles.keys.joinToString { it.lastOrNull()?.fullName ?: "$it" }}"
        else -> return
    }
    logSink.log(
        Log.Error,
        MessageTemplate.UserConnectedNativeMissing,
        pos,
        listOf(dependencyResolver.backend.backendId, baseName, problem),
    )
}

private fun normalizeConnectedName(name: String) = name.replace("_", "").lowercase()

private fun mentionsNormalizedIdentifier(source: String, normalizedName: String): Boolean =
    identifierRegex.findAll(source).any { normalizeConnectedName(it.value) == normalizedName }

private val identifierRegex = Regex("""[A-Za-z_$][A-Za-z0-9_$]*""")
