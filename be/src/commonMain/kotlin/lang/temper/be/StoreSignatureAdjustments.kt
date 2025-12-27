package lang.temper.be

import lang.temper.be.tmpl.MemberMap
import lang.temper.be.tmpl.SignatureAdjustments
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.frontend.Module
import lang.temper.library.LibraryConfiguration
import lang.temper.name.ResolvedName
import lang.temper.type.MethodShape

/**
 * A pre-pass before we translate type declarations that attaches signature adjustments.
 * The backend's pre-analysis pass extracts all the adjustments and make them available to
 * other libraries' backends in time for the [lang.temper.be.Backend.tentativeTmpL] pass
 * to translate definitions of and calls to adjusted methods.
 */
internal fun <BACKEND : Backend<BACKEND>> storeSignatureAdjustments(
    libraryConfiguration: LibraryConfiguration,
    modules: List<Module>,
    dependenciesBuilder: Dependencies.Builder<BACKEND>,
    supportNetwork: SupportNetwork,
    key: MetadataKey<BACKEND, MemberMap<SignatureAdjustments>>,
) {
    val libraryName = libraryConfiguration.libraryName
    val map = mutableMapOf<ResolvedName, SignatureAdjustments>()
    for (module in modules) {
        for (typeShape in module.declaredTypeShapes) {
            for (method in typeShape.methods) {
                val adjustments = adjustMethodSignature(supportNetwork, method)
                if (adjustments != null) {
                    val name = method.name as ResolvedName
                    map[name] = adjustments
                }
            }
        }
    }
    dependenciesBuilder.addMetadata(libraryName, key, map.toMap())
}

internal fun adjustMethodSignature(
    supportNetwork: SupportNetwork,
    method: MethodShape,
): SignatureAdjustments? {
    val unadjustedSignature = method.descriptor
        ?: return null

    return supportNetwork.maybeAdjustMethodSignature(
        unadjustedSignature = unadjustedSignature,
        method = method,
    )
}
