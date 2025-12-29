package lang.temper.interp.importExport

import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.env.Constness
import lang.temper.env.DeclarationMetadata
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.log.Position
import lang.temper.name.ExportedName
import lang.temper.name.ModuleLocation
import lang.temper.name.Symbol
import lang.temper.value.StayReferrer
import lang.temper.value.StaySink
import lang.temper.value.TypeInferences
import lang.temper.value.Value

/** That which may export [ExportedName]s to [Importer]s. */
interface Exporter {
    val loc: ModuleLocation
    val exports: List<Export>?
    fun exportMatching(exportedName: ExportedName): Export? = exports?.firstOrNull { it.name == exportedName }
}

data class Export(
    val exporter: Exporter,
    val name: ExportedName,
    val value: Value<*>?,
    val typeInferences: TypeInferences?,
    val declarationMetadata: Map<Symbol, List<Value<*>?>>,
    val position: Position,
) : DeclarationMetadata, StayReferrer, Structured {
    override val constness: Constness
        get() = Constness.Const
    override val referentSource: ReferentSource
        get() = ReferentSource.SingleSourceAssigned
    override val completeness: ReferentBitSet
        get() = ReferentBitSet.complete
    override val declarationSite: Position
        get() = position
    override val reifiedType: Value<*>?
        get() = null // Could we get one from typeInferences?

    override fun addStays(s: StaySink) {
        if (value != null) {
            s.whenUnvisited(value) {
                value.addStays(s)
            }
        }
        declarationMetadata.forEach { (_, values) ->
            values.forEach {
                if (value != null) {
                    s.whenUnvisited(value) {
                        value.addStays(s)
                    }
                }
            }
        }
    }

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("name") { value(name) }
        key("value") { value(value) }
        key("type") { value(typeInferences?.type) }
        key("declarationMetadata") {
            obj {
                declarationMetadata.forEach { (symbol, value) ->
                    key(symbol.text) {
                        this.value(value)
                    }
                }
            }
        }
        key("pos", Hints.u) { value(position) }
    }
}
