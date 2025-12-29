package lang.temper.interp

import lang.temper.common.Log
import lang.temper.env.ChildEnvironment
import lang.temper.env.Constness
import lang.temper.env.DeclarationBinding
import lang.temper.env.DeclarationBits
import lang.temper.env.Environment
import lang.temper.env.ReferentBit
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.TemperName
import lang.temper.value.Fail
import lang.temper.value.InterpreterCallback
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.StaySink
import lang.temper.value.TType
import lang.temper.value.Value
import lang.temper.value.and
import lang.temper.value.void

/**
 * An environment whose set of bindings may change based on [declare] calls.
 */
abstract class MutableEnvironment<BINDING : DeclarationBinding> protected constructor(
    parent: Environment,
) : ChildEnvironment(parent) {
    private val bindings = mutableMapOf<TemperName, BINDING>()

    protected abstract fun createBinding(
        name: TemperName,
        tracksFail: Boolean,
        type: Value<*>?,
        constness: Constness,
        completeness: ReferentBitSet,
        referentSource: ReferentSource,
        declarationSite: Position,
    ): BINDING
    protected abstract fun setValueAndCompleteness(b: BINDING, value: Value<*>, completeness: ReferentBitSet)

    protected fun deleteBinding(name: TemperName) { bindings.remove(name) }
    protected fun clearBindings() { bindings.clear() }

    override fun localDeclarationMetadata(name: TemperName): DeclarationBinding? =
        bindings[name]

    override fun get(name: TemperName, cb: InterpreterCallback) = when (val b = bindings[name]) {
        null -> super.get(name, cb)
        else -> when (val value = b.value) {
            null -> {
                val bePushy = cb.isPushy &&
                    ReferentBitSet.wellformedButValueless.and(b.completeness) ==
                    ReferentBitSet.wellformedButValueless
                // TODO: Maybe see if there's a type with a zero value.
                if (bePushy) {
                    cb.fail(MessageTemplate.Uninitialized, values = listOf(name))
                } else {
                    cb.explain(MessageTemplate.Uninitialized, values = listOf(name))
                    NotYet
                }
            }
            else -> value
        }
    }

    @Suppress("AddOperatorModifier")
    override fun set(name: TemperName, newValue: Value<*>, cb: InterpreterCallback): PartialResult =
        when (val b = bindings[name]) {
            null -> super.set(name, newValue, cb)
            else -> {
                if (b.value == null || b.constness == Constness.NotConst) {
                    checkType(b, newValue, cb).and {
                        setValueAndCompleteness(b, newValue, b.completeness or ReferentBit.Value)
                        void
                    }
                } else {
                    cb.fail(MessageTemplate.CannotResetConst, values = listOf(name))
                }
            }
        }

    override fun declare(
        name: TemperName,
        declarationBits: DeclarationBits,
        cb: InterpreterCallback,
    ): PartialResult {
        // We deal with colliding declarations via static analysis.
        // Absent colliding declarations,
        // blowing away previous bindings happens to do the desired thing for declarations in
        // loop bodies.
        bindings.remove(name)

        val reifiedType = declarationBits.reifiedType
        val initial = declarationBits.initial
        val constness = declarationBits.constness
        val referentSource = declarationBits.referentSource
        val missing = declarationBits.missing
        val declarationSite = declarationBits.declarationSite

        val completeness = ReferentBitSet.wellformedButValueless - missing

        val b = createBinding(
            name = name,
            tracksFail = declarationBits.tracksFail,
            type = reifiedType,
            constness = constness,
            completeness = completeness,
            referentSource = referentSource,
            declarationSite = declarationSite,
        )
        bindings[name] = b
        return checkType(b, initial, cb).and {
            if (initial != null) {
                setValueAndCompleteness(b, initial, b.completeness or ReferentBit.Value)
            }
            void
        }
    }

    private fun checkType(b: BINDING, value: Value<*>?, cb: InterpreterCallback): PartialResult {
        val reifiedType = b.reifiedType
        return when {
            reifiedType == null || value == null -> {
                val wanted =
                    (
                        if (reifiedType == null) {
                            ReferentBit.Type.bit
                        } else {
                            0
                        }
                        ) or
                        (
                            if (value == null) {
                                ReferentBit.Initial.bit
                            } else {
                                0
                            }
                            )
                if (wanted == (b.completeness.bits and wanted)) {
                    void
                } else {
                    val pushy = cb.isPushy
                    cb.explain(MessageTemplate.IncompleteDeclaration, pinned = pushy)
                    if (pushy) {
                        void
                    } else {
                        NotYet
                    }
                }
            }
            else -> {
                val type = TType.unpackOrNull(reifiedType)
                if (type == null) {
                    Fail(
                        LogEntry(
                            Log.Error,
                            MessageTemplate.ExpectedType,
                            cb.pos,
                            listOf(reifiedType),
                        ),
                    )
                } else {
                    if (type.accepts(value, null, -1)) {
                        value
                    } else {
                        cb.fail(MessageTemplate.TypeCheckRejected, values = listOf(reifiedType, value))
                    }
                }
            }
        }
    }

    override val locallyDeclared: Iterable<TemperName> get() = bindings.keys.toList()

    override fun addStays(s: StaySink) {
        s.whenUnvisited(this) {
            for (b in bindings.values) {
                b.reifiedType?.addStays(s)
                b.value?.addStays(s)
            }
            super.addStays(s)
        }
    }
}
