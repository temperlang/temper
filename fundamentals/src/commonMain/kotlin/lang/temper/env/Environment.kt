package lang.temper.env

import lang.temper.log.Position
import lang.temper.name.TemperName
import lang.temper.value.Fail
import lang.temper.value.InterpreterCallback
import lang.temper.value.PartialResult
import lang.temper.value.StayReferrer
import lang.temper.value.StaySink
import lang.temper.value.Value

/**
 * Relates names to bindings.
 *
 * The interpreter creates one environment each time a new context is entered, so declarations in
 * loop bodies are uninitialized at the start of each iteration.
 *
 * A binding consists of:
 * - a value if the binding has been initialized
 * - a reified type if there is type info associated that needs to be checked on assignment
 */
interface Environment : StayReferrer {
    /** Metadata about a declaration. */
    fun declarationMetadata(name: TemperName): DeclarationMetadata?

    /** The [ReferentBitSet] for the [name]d binding or null if no binding. */
    fun completeness(name: TemperName): ReferentBitSet?

    /** The [Constness] the given name was [declare]d with. */
    fun constness(name: TemperName): Constness?

    /** The [ReferentSource] the given name was [declare]d with. */
    fun referentSource(name: TemperName): ReferentSource?

    /** The position where the name was declared if known. */
    fun declarationSite(name: TemperName): Position?

    /**
     * True if there is a binding for the given name that is complete.
     */
    fun isWellFormed(name: TemperName): Boolean = when (val c = completeness(name)) {
        null -> false
        else -> {
            val wfb = ReferentBitSet.wellformedButValueless.bits
            (c.bits and wfb) == wfb
        }
    }

    /**
     * True if the declaration is long-lived; it is a declaration like a module top-level that
     * typically persists for the length of the using program, and not a local variable in a
     * function call that is not live longer than the function is running.
     *
     * @see [isLongLived]
     */
    fun isLongLivedDeclaration(name: TemperName): Boolean?

    /**
     * True if the environment is long-lived.  It is an environment like a module top-level
     * environment and not an environment for a function activation frame whose backing storage
     * may change ownership when the call stack is popped.
     */
    val isLongLived: Boolean

    /**
     * Gets the value associated with name.
     * @return [Fail] if there is no such value.
     */
    operator fun get(name: TemperName, cb: InterpreterCallback): PartialResult

    /**
     * Sets the value associated with the given name.
     * @return a value if the assignment succeeded or failure if not.
     */
    @Suppress("AddOperatorModifier")
    fun set(name: TemperName, newValue: Value<*>, cb: InterpreterCallback): PartialResult

    /**
     * Declares a variable.  This differs from a [set] in that it operates at the current
     * environment, so would not affect a binding in a broader lexical scope.
     *
     * @return [Fail] if there is a conflicting binding or this environment does not accept new
     *     bindings.
     */
    fun declare(
        name: TemperName,
        declarationBits: DeclarationBits,
        cb: InterpreterCallback,
    ): PartialResult

    /** The number of layers of environment below this. */
    val depth: Int

    /**
     * Best effort to list the names that have been declared in this environment without reference
     * to any closing environment.
     */
    val locallyDeclared: Iterable<TemperName>

    /**
     * True if the name is locally declared; [set]s will affect this environment,
     * not an ancestor environment.
     */
    fun isLocallyDeclared(name: TemperName): Boolean
}

/**
 * An environment whose default get/set delegate to a parent environment, so that subclasses
 * can delegate to `super` when they have no strategy.
 */
abstract class ChildEnvironment(
    val parent: Environment,
) : Environment {
    @Suppress("AddOperatorModifier")
    override fun set(name: TemperName, newValue: Value<*>, cb: InterpreterCallback): PartialResult =
        parent.set(name, newValue, cb)

    override fun get(name: TemperName, cb: InterpreterCallback) = parent[name, cb]

    protected abstract fun localDeclarationMetadata(name: TemperName): DeclarationMetadata?

    override fun isLocallyDeclared(name: TemperName) = localDeclarationMetadata(name) != null

    final override fun isLongLivedDeclaration(name: TemperName): Boolean? {
        val metadata = localDeclarationMetadata(name)
            ?: return parent.isLongLivedDeclaration(name)
        if (
            metadata.constness == Constness.Const &&
            metadata.referentSource == ReferentSource.SingleSourceAssigned
        ) {
            return isLongLived
        }
        return false
    }

    final override fun declarationMetadata(name: TemperName): DeclarationMetadata? =
        localDeclarationMetadata(name) ?: parent.declarationMetadata(name)

    final override fun completeness(name: TemperName) = declarationMetadata(name)?.completeness

    final override fun constness(name: TemperName): Constness? =
        declarationMetadata(name)?.constness

    final override fun referentSource(name: TemperName): ReferentSource? =
        declarationMetadata(name)?.referentSource

    final override fun declarationSite(name: TemperName): Position? =
        declarationMetadata(name)?.declarationSite

    override val depth: Int get() = 1 + parent.depth

    override fun addStays(s: StaySink) {
        parent.addStays(s)
    }
}

interface DeclarationMetadata {
    val constness: Constness
    val referentSource: ReferentSource
    val completeness: ReferentBitSet
    val declarationSite: Position?
    val reifiedType: Value<*>?

    /**
     * Enables access to failure information associated with this declaration.
     */
    var fail: Fail?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") fail) {}
}

interface DeclarationBinding : DeclarationMetadata {
    val value: Value<*>?
}

data class DeclarationBits(
    override val reifiedType: Value<*>?,
    val initial: Value<*>?,
    override val constness: Constness,
    override val referentSource: ReferentSource,
    /**
     * For information that could not be supplied due to malformedness; for example, if the
     * type expression evaluates to [Fail] then this should include [ReferentBit.Type].
     */
    val missing: ReferentBitSet,
    override val declarationSite: Position,
    /**
     * Set to true to enable storage of a fail object.
     */
    val tracksFail: Boolean = false,
) : DeclarationMetadata {
    override val completeness: ReferentBitSet get() = ReferentBitSet.complete - missing
}
