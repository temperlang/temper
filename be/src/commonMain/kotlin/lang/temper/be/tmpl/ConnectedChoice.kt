package lang.temper.be.tmpl

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
// may list the backends that have connected code, as in `@connected(["cpp", "rust"])`.
// While translating to TmpL, each backend decides which to use:
//
// - A listed backend uses connected code from the module's connected files (`_connected.rs`,
//   `__connected__.py`, and so on) through the same generated wrapper as always. If a listed
//   backend's connected code is actually missing, its own compiler or runtime catches it at
//   build or first call, with a precise message. (A sound pre-check would instead ask each
//   backend's translator for the connected entry points it expects; that's future work.)
// - Other backends use the body, just as for any function. The interpreter always does.
// - Without a list, every backend must supply connected code, as before lists existed, and the
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
    /** Call the backend's connected code from its connected files. */
    Connected,

    /** Translate the Temper body like any other function. */
    Body,
}

/**
 * Decides which implementation a backend uses for a user-space `@connected` function.
 *
 * @param listedBackends from `@connected([...])`, or null for a plain `@connected`.
 * @param hasBody whether the function has a Temper body. Lists require bodies, which the
 *   frontend checks, so without one, we use connected code as before lists.
 * @param backendNames the names by which [listedBackends] can mean this backend.
 */
internal fun chooseConnected(
    listedBackends: List<String>?,
    hasBody: Boolean,
    backendNames: Set<String>,
    noConnected: Boolean,
): ConnectedChoice = when {
    !hasBody -> ConnectedChoice.Connected
    noConnected -> ConnectedChoice.Body
    listedBackends == null -> ConnectedChoice.Connected
    listedBackends.any { it in backendNames } -> ConnectedChoice.Connected
    else -> ConnectedChoice.Body
}

/**
 * Adjusts the TmpL [metadata] of a function declaration per [chooseConnected], dropping
 * `@connected` metadata when the body should be used.
 */
internal fun TmpLTranslator.metadataForConnectedChoice(
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
        ConnectedChoice.Connected -> metadata
    }
}
