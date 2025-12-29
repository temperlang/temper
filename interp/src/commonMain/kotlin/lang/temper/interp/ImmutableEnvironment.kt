package lang.temper.interp

import lang.temper.env.ChildEnvironment
import lang.temper.env.Constness
import lang.temper.env.DeclarationBits
import lang.temper.env.DeclarationMetadata
import lang.temper.env.Environment
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.Name
import lang.temper.name.TemperName
import lang.temper.value.InterpreterCallback
import lang.temper.value.PartialResult
import lang.temper.value.Result
import lang.temper.value.Value

internal class ImmutableEnvironment(
    parent: Environment,
    private val nameToValue: Map<TemperName, Value<*>>,
    override val isLongLived: Boolean,
) : ChildEnvironment(parent) {
    override fun get(name: TemperName, cb: InterpreterCallback) =
        nameToValue[name] ?: super.get(name, cb)

    override fun localDeclarationMetadata(name: TemperName): DeclarationMetadata? =
        if (name in nameToValue) {
            ImmutableDeclMetadata
        } else {
            null
        }

    private object ImmutableDeclMetadata : DeclarationMetadata {
        override val constness = Constness.Const
        override val referentSource = ReferentSource.SingleSourceAssigned
        override val completeness = ReferentBitSet.complete
        override val declarationSite: Position? = null
        override val reifiedType: Value<*>? get() = null
    }

    override fun set(name: TemperName, newValue: Value<*>, cb: InterpreterCallback): PartialResult =
        if (name in nameToValue) {
            rejectSetOfImmutableBinding(name, cb)
        } else {
            super.set(name, newValue, cb)
        }

    override fun declare(
        name: TemperName,
        declarationBits: DeclarationBits,
        cb: InterpreterCallback,
    ): Result = rejectDeclareOnImmutableEnv(name, cb)

    override val locallyDeclared: Iterable<TemperName> get() = nameToValue.keys
}

internal fun rejectDeclareOnImmutableEnv(name: Name, cb: InterpreterCallback) =
    cb.fail(MessageTemplate.BuiltinEnvironmentIsNotMutable, values = listOf(name))

internal fun rejectSetOfImmutableBinding(
    name: TemperName,
    cb: InterpreterCallback,
) = cb.fail(MessageTemplate.CouldNotSetLocal, values = listOf(name))
