package lang.temper.frontend

import lang.temper.env.ChildEnvironment
import lang.temper.env.DeclarationBinding
import lang.temper.env.DeclarationBits
import lang.temper.env.DeclarationMetadata
import lang.temper.env.Environment
import lang.temper.interp.importExport.Export
import lang.temper.log.MessageTemplate
import lang.temper.name.ExportedName
import lang.temper.name.ModularName
import lang.temper.name.NamingContext
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.value.InterpreterCallback
import lang.temper.value.NotYet

/**
 * Resolves names of exported or otherwise externally accessible top-level module constructs.
 *
 * Exports are assumed to be available, as are static properties of declared types, and
 * methods defined on interface types.
 */
internal class ModularNameEnvironment(
    parent: Environment,
) : ChildEnvironment(parent) {
    override val isLongLived: Boolean get() = true

    override fun localDeclarationMetadata(name: TemperName): DeclarationMetadata? = when (name) {
        is ExportedName -> getExport(name)
        is ModularName -> getModuleTopLevelBinding(name)
        else -> null
    }

    private fun getModule(origin: NamingContext) =
        (origin as? ModuleNamingContext)?.owner

    private fun getExport(name: ExportedName): Export? {
        val moduleNamingContext = name.origin as? ModuleNamingContext
        val module = moduleNamingContext?.owner
        val exportList = module?.exports
        return exportList?.first { it.name == name }
    }

    private fun getModuleTopLevelBinding(name: ModularName): DeclarationBinding? {
        val moduleNamingContext = name.origin as? ModuleNamingContext
        return moduleNamingContext?.getTopLevelBinding(name)
    }

    override fun get(name: TemperName, cb: InterpreterCallback) =
        if (name is ExportedName) {
            val export = getExport(name)
            if (export != null) {
                export.value ?: NotYet
            } else {
                // If the module has not reached the export stage, return NotYet.
                val stage = getModule(name.origin)?.stageCompleted
                if (stage == null || stage < Stage.Export) {
                    NotYet
                } else {
                    cb.fail(
                        MessageTemplate.NotExported,
                        values = listOf(name.origin, name.baseName),
                    )
                }
            }
        } else {
            val staticBinding =
                if (name is ModularName) { getModuleTopLevelBinding(name) } else { null }
            staticBinding?.value ?: super.get(name, cb)
        }

    override fun declare(
        name: TemperName,
        declarationBits: DeclarationBits,
        cb: InterpreterCallback,
    ) = cb.fail(MessageTemplate.BuiltinEnvironmentIsNotMutable, values = listOf(name))

    override val locallyDeclared: Iterable<TemperName>
        get() = emptyList()
}
