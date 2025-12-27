package lang.temper.interp

import lang.temper.env.Constness
import lang.temper.env.DeclarationBits
import lang.temper.env.DeclarationMetadata
import lang.temper.env.Environment
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.TemperName
import lang.temper.value.Fail
import lang.temper.value.InterpreterCallback
import lang.temper.value.Result
import lang.temper.value.Stayless
import lang.temper.value.Value

/** An environment that has no bindings and never will. */
object EmptyEnvironment : Environment, Stayless {
    override val isLongLived: Boolean = true // Nothing is forever.

    override fun isLongLivedDeclaration(name: TemperName): Boolean? = null

    override fun declarationMetadata(name: TemperName): DeclarationMetadata? = null

    override fun completeness(name: TemperName): ReferentBitSet? = null

    override fun constness(name: TemperName): Constness? = null

    override fun referentSource(name: TemperName): ReferentSource? = null

    override fun declarationSite(name: TemperName): Position? = null

    override fun get(name: TemperName, cb: InterpreterCallback) =
        cb.fail(MessageTemplate.UndeclaredName, values = listOf(name))

    override fun declare(
        name: TemperName,
        declarationBits: DeclarationBits,
        cb: InterpreterCallback,
    ): Fail = rejectDeclareOnImmutableEnv(name, cb)

    override fun set(name: TemperName, newValue: Value<*>, cb: InterpreterCallback): Result {
        rejectSetOfImmutableBinding(name, cb)
        return Fail
    }

    override val depth: Int get() = 0

    override val locallyDeclared: Iterable<TemperName> get() = emptyList()

    override fun isLocallyDeclared(name: TemperName) = false
}
