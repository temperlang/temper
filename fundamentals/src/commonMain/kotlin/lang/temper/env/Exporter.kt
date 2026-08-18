package lang.temper.env

import lang.temper.common.AtomicCounter
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.log.Position
import lang.temper.name.ExportedName
import lang.temper.name.ModuleLocation
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.value.StayReferrer
import lang.temper.value.StaySink
import lang.temper.value.TString
import lang.temper.value.TypeInferences
import lang.temper.value.Value
import lang.temper.value.connectedSymbol
import lang.temper.value.qNameSymbol

/** That which may export [ExportedName]s to importers. */
interface Exporter {
    val loc: ModuleLocation
    val exports: List<Export>?
    fun exportMatching(exportedName: ExportedName): Export? = exports?.firstOrNull { it.name == exportedName }
}

data class Export(
    val exporter: Exporter,
    val name: ExportedName,
    /**
     * The value for any [Stage] before [Stage.Run].
     * This is the statically knowable value.
     *
     * It is likely to be `null` in many cases where, at runtime we can produce
     * a value.
     *
     * For example, `export let profilingStartStamp = MonotonicClock.now()`
     * depends on runtime information.  We cannot compute that statically
     * because there are no stable compile-time semantics for the module
     * load time.
     *
     * In some cases, macro function values, we might have this value but
     * not one for [valueFromRun].  Macros are not necessary after
     * the [Stage.GenerateCode] stage completes.
     */
    val valueFromStaging: Value<*>?,
    /**
     * Unlike [valueFromStaging], the value from runtime.
     */
    val valueFromRun: Value<*>?,
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

    /**
     * Picks the exported value based on the semantics of the stage requesting it.
     */
    fun value(stage: Stage): Value<*>? = when (stage) {
        Stage.Run -> valueFromRun
        else -> valueFromStaging
    }

    val connectedKey: String?
        get() = when {
            connectedSymbol in declarationMetadata ->
                declarationMetadata[qNameSymbol]?.lastOrNull()?.let { TString.unpackOrNull(it) }
            else -> null
        }

    override fun addStays(s: StaySink) {
        valueFromStaging?.let {
            s.whenUnvisited(it) {
                it.addStays(s)
            }
        }
        valueFromRun?.let {
            s.whenUnvisited(it) {
                it.addStays(s)
            }
        }
        declarationMetadata.forEach { (_, values) ->
            values.forEach { value ->
                value?.let {
                    s.whenUnvisited(it) {
                        it.addStays(s)
                    }
                }
            }
        }
    }

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("name") { value(name) }
        valueFromStaging.let { value ->
            key("valueFromStaging", isDefault = value == null) { value(value) }
        }
        valueFromRun.let { value ->
            key("valueFromRun", isDefault = value == null) { value(value) }
        }
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

abstract class ExportingNamingContext(counter: AtomicCounter) : BindingNamingContext(counter) {
    abstract val exporter: Exporter
}
