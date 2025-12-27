package lang.temper.interp

import lang.temper.env.Constness
import lang.temper.env.DeclarationBinding
import lang.temper.env.Environment
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.log.Position
import lang.temper.name.TemperName
import lang.temper.value.Fail
import lang.temper.value.Value

internal sealed interface Binding : DeclarationBinding {
    override var value: Value<*>?
    override val declarationSite: Position
    override var completeness: ReferentBitSet
}

private data class PlainBinding(
    override val reifiedType: Value<*>?,
    override var value: Value<*>?,
    override val constness: Constness,
    override var completeness: ReferentBitSet,
    override val referentSource: ReferentSource,
    override val declarationSite: Position,
) : Binding

private data class FailBinding(
    override val reifiedType: Value<*>?,
    override var value: Value<*>?,
    override val constness: Constness,
    override var completeness: ReferentBitSet,
    override val referentSource: ReferentSource,
    override val declarationSite: Position,
) : Binding {
    // Track extra metadata only when applicable.
    override var fail: Fail? = null
}

internal class BlockEnvironment(
    parent: Environment,
) : MutableEnvironment<Binding>(parent) {
    override fun createBinding(
        name: TemperName,
        tracksFail: Boolean,
        type: Value<*>?,
        constness: Constness,
        completeness: ReferentBitSet,
        referentSource: ReferentSource,
        declarationSite: Position,
    ): Binding = if (tracksFail) {
        FailBinding(type, null, constness, completeness, referentSource, declarationSite)
    } else {
        PlainBinding(type, null, constness, completeness, referentSource, declarationSite)
    }

    override fun setValueAndCompleteness(b: Binding, value: Value<*>, completeness: ReferentBitSet) {
        b.value = value
        b.completeness = completeness
    }

    override val isLongLived: Boolean = false
}
