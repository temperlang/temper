package lang.temper.frontend

import lang.temper.env.Constness
import lang.temper.env.DeclarationBinding
import lang.temper.env.DeclarationBits
import lang.temper.env.Environment
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.interp.MutableEnvironment
import lang.temper.log.Position
import lang.temper.name.TemperName
import lang.temper.value.InterpreterCallback
import lang.temper.value.PartialResult
import lang.temper.value.StaySink
import lang.temper.value.Value

class TopLevelBinding(
    val name: TemperName,
    completeness: ReferentBitSet,
    override val declarationSite: Position?,
    override val constness: Constness,
    override val referentSource: ReferentSource,
    override val reifiedType: Value<*>?,
    value: Value<*>?,
) : DeclarationBinding {
    override var completeness: ReferentBitSet = completeness
        internal set
    override var value: Value<*>? = value
        internal set
}

/**
 * An environment that includes a [Module]'s top-level bindings and which persists
 * across multiple stages.
 */
class TopLevelBindings(
    /** The environment used to resolve *free names* in module code. */
    parent: Environment,
) : MutableEnvironment<TopLevelBinding>(parent), StageExclusion {
    internal fun clearBeforeStaging() = this.clearBindings()

    override fun createBinding(
        name: TemperName,
        tracksFail: Boolean,
        type: Value<*>?,
        constness: Constness,
        completeness: ReferentBitSet,
        referentSource: ReferentSource,
        declarationSite: Position,
    ): TopLevelBinding = TopLevelBinding(
        name = name,
        completeness = completeness,
        declarationSite = declarationSite,
        constness = constness,
        referentSource = referentSource,
        reifiedType = type,
        value = null,
    )

    override val isLongLived: Boolean get() = true

    override fun setValueAndCompleteness(b: TopLevelBinding, value: Value<*>, completeness: ReferentBitSet) {
        b.value = value
        b.completeness = completeness
    }

    @Synchronized
    public override fun localDeclarationMetadata(name: TemperName): DeclarationBinding? =
        super.localDeclarationMetadata(name)

    @Synchronized
    override fun get(name: TemperName, cb: InterpreterCallback) = super.get(name, cb)

    @Suppress("AddOperatorModifier")
    @Synchronized
    override fun set(name: TemperName, newValue: Value<*>, cb: InterpreterCallback): PartialResult =
        super.set(name, newValue, cb)

    @Synchronized
    override fun declare(
        name: TemperName,
        declarationBits: DeclarationBits,
        cb: InterpreterCallback,
    ) = super.declare(name, declarationBits, cb)

    override val locallyDeclared get() = whileSynchronized { super.locallyDeclared }

    @Synchronized
    override fun addStays(s: StaySink) = super.addStays(s)

    @Synchronized
    override fun <T> whileSynchronized(action: () -> T): T {
        return action()
    }
}
