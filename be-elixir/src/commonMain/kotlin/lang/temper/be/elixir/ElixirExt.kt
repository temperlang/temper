package lang.temper.be.elixir

import lang.temper.be.tmpl.TmpL
import lang.temper.name.CoreCodeLocation
import lang.temper.name.ResolvedParsedName

/**
 * Whether this is the module-level `console` temporary the frontend injects
 * for every reference to the global console.
 *
 * `Console.log` is inlined at its call site as `IO.puts` and drops the
 * receiver, so the temporary has no reader. be-blimp, be-rust and be-cppv
 * skip it the same way.
 */
internal fun TmpL.ModuleLevelDeclaration.isConsole(): Boolean {
    (type.ot as? TmpL.NominalType)?.typeName?.sourceDefinition?.let { typeDefinition ->
        if (typeDefinition.sourceLocation === CoreCodeLocation) {
            when ((typeDefinition.name as? ResolvedParsedName)?.baseName?.nameText) {
                "Console", "GlobalConsole" -> return true
                else -> {}
            }
        }
    }
    return false
}
