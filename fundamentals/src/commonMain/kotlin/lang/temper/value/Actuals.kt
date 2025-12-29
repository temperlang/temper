package lang.temper.value

import lang.temper.common.Log
import lang.temper.env.Environment
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.Symbol

/**
 * A group of parameters in a call.
 *
 * This deals with *actual* parameters as opposed to *formal* parameters.
 *
 *     // A function's definition has formal parameters
 *     let f(x: Int) { ... }
 *     //    ^^^^^^-- Formal declaration of x
 *
 *     // which correspond to actual parameters in calls to that function.
 *     let y = f(x = 123)
 *     //        ^^^^^^^-- The actual value of x is 123 in this call.
 */
sealed interface Actuals {
    /** Parameter count */
    val size: Int
    val lastIndex: Int get() = size - 1

    /** A name associated with the index-th parameter if any. */
    fun key(index: Int): Symbol?

    /** Position metadata for the index-th parameter if available. */
    fun pos(index: Int): Position?

    val indices get() = 0 until size

    /** Generic getter for a result that may require computation. */
    fun result(index: Int, interpMode: InterpMode, computeInOrder: Boolean = true): PartialResult

    fun peekType(index: Int): TypeTag<*>?

    /** Best effort to clear any cache of the result of the index-th parameter. */
    fun clearResult(index: Int)
}

/** Allows deriving a new version by picking and choosing which arguments to include. */
val (Actuals).cherryPicker: CherryPicker<Actuals, Pair<Symbol?, PartialResult>>
    get() = CherryPicker(this) { a, indices, keyOverrides, newActuals ->
        ActualsView(a, indices, keyOverrides, newActuals)
    }

/**
 * Actuals suitable for passing to a normal function where values have been computed ahead-of-time.
 */
interface ComputedActuals : Actuals {
    fun result(index: Int, computeInOrder: Boolean = true): PartialResult

    override fun result(index: Int, interpMode: InterpMode, computeInOrder: Boolean) =
        result(index, computeInOrder)
}

val (ComputedActuals).cherryPicker: CherryPicker<ComputedActuals, Pair<Symbol?, PartialResult>>
    get() = CherryPicker(this) { a, indices, keyOverrides, newActuals ->
        ComputedActualsView(a, indices, keyOverrides, newActuals)
    }

/**
 * Actuals that have been computed and which have been [fully][lang.temper.env.InterpMode.Full]
 * computed.
 */
interface ActualValues : ComputedActuals {
    override fun result(index: Int, computeInOrder: Boolean): Value<*>
    operator fun get(index: Int): Value<*> = result(index)

    fun unpackPositioned(arity: Int, cb: InterpreterCallback): List<Value<*>>? {
        if (arity != size) {
            cb.explain(MessageTemplate.ArityMismatch, values = listOf(arity))
            return null
        }
        for (i in 0 until arity) {
            if (key(i) != null) {
                cb.explain(MessageTemplate.NoSignatureMatches, pos(i) ?: cb.pos)
                return null
            }
        }
        return (0 until arity).map { result(it) }
    }

    object Empty : ActualValues {
        override fun result(index: Int, computeInOrder: Boolean) = throw NoSuchElementException()
        override val size: Int = 0
        override fun key(index: Int) = throw NoSuchElementException()
        override fun pos(index: Int) = throw NoSuchElementException()
        override fun clearResult(index: Int) = throw NoSuchElementException()
        override fun peekType(index: Int): TypeTag<*>? = throw NoSuchElementException()
    }

    companion object {
        fun from(v: Value<*>) = from(listOf(v))
        fun from(v: Value<*>, w: Value<*>) = from(listOf(v, w))
        fun from(v: Value<*>, w: Value<*>, x: Value<*>) = from(listOf(v, w, x))
        fun from(vs: Iterable<Value<*>>, ps: List<Positioned>? = null): ActualValues =
            ActualValueListWrapper(vs.toList(), ps)
        fun cat(a: ActualValues, b: ActualValues) = when {
            a.size == 0 -> b
            b.size == 0 -> a
            else -> CatActualValues(a, b)
        }
    }
}

inline fun (Actuals).unpackPositionedOr(
    arity: Int,
    cb: InterpreterCallback,
    or: (Fail) -> Nothing,
): List<Value<*>> {
    if (arity != size) {
        or(
            Fail(
                LogEntry(
                    level = Log.Error,
                    template = MessageTemplate.ArityMismatch,
                    pos = cb.pos,
                    values = listOf(arity),
                ),
            ),
        )
    }
    for (i in 0 until arity) {
        if (key(i) != null) {
            or(
                Fail(
                    LogEntry(
                        level = Log.Error,
                        template = MessageTemplate.NoSignatureMatches,
                        pos = pos(i) ?: cb.pos,
                        values = emptyList(),
                    ),
                ),
            )
        }
    }
    return buildList {
        for (i in 0 until arity) {
            when (val result = result(i, InterpMode.Full)) {
                is Value<*> -> add(result)
                is Fail -> or(result)
                is NotYet -> or(Fail)
            }
        }
    }
}

val (ActualValues).cherryPicker: CherryPicker<ActualValues, Pair<Symbol?, Value<*>>>
    get() = CherryPicker(this) { a, indices, keyOverrides, newActuals ->
        ActualValuesView(a, indices, keyOverrides, newActuals)
    }

/**
 * Actuals suitable for passing to a macro which may want to deal with some arguments as trees and
 * some as values.
 */
interface MacroActuals : Actuals {
    fun keyTree(index: Int): Tree?
    fun valueTree(index: Int): Tree
    val environment: Environment

    /** Evaluates [valueTree]\([index]\) without any result caching. */
    fun evaluate(index: Int, interpMode: InterpMode): PartialResult

    /**
     * A tree list with [key][keyTree] and [value][valueTree] trees inter-mixed.
     */
    val rawTreeList: List<Tree>
    override fun pos(index: Int): Position
}

val (MacroActuals).cherryPicker: CherryPicker<MacroActuals, Pair<ValueLeaf?, Tree>>
    get() = CherryPicker(this) { a, indices, keyOverrides, newActuals ->
        MacroActualsView(a, indices, keyOverrides, newActuals)
    }

/**
 * Allows deriving a view of some arguments in an actuals list, to allow for flexible delegation,
 * accompanied by position metadata of function calls.
 */
class CherryPicker<out A : Actuals, N : Any>(
    private val actuals: A,
    private val builder: (A, IntArray, Map<Int, Symbol?>, Map<Int, N>) -> A,
) {
    private val picked = mutableListOf<Int>()
    private val keyOverrides = mutableMapOf<Int, Symbol?>()
    private val newActuals = mutableMapOf<Int, N>()

    fun add(index: Int): CherryPicker<A, N> {
        picked.add(index)
        return this
    }

    fun addRekeyed(index: Int, keyOverride: Symbol?): CherryPicker<A, N> {
        keyOverrides[picked.size] = keyOverride
        picked.add(index)
        return this
    }

    fun add(indices: IntRange): CherryPicker<A, N> {
        picked.addAll(indices)
        return this
    }

    fun new(newActual: N): CherryPicker<A, N> {
        val index = picked.size
        newActuals[index] = newActual
        picked.add(-1)
        return this
    }

    fun build() = builder(actuals, picked.toIntArray(), keyOverrides.toMap(), newActuals.toMap())
}

private class ActualsView(
    val underlying: Actuals,
    val indexMap: IntArray,
    val keyOverrides: Map<Int, Symbol?>,
    val newValues: Map<Int, Pair<Symbol?, PartialResult>>,
) : Actuals {
    override val size: Int get() = indexMap.size

    override fun key(index: Int): Symbol? = when (index) {
        in keyOverrides -> keyOverrides[index]
        in newValues -> newValues[index]?.first
        else -> underlying.key(indexMap[index])
    }

    override fun pos(index: Int): Position? {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) { underlying.pos(uIndex) } else { null }
    }

    override fun result(
        index: Int,
        interpMode: InterpMode,
        computeInOrder: Boolean,
    ): PartialResult {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) {
            underlying.result(uIndex, interpMode = interpMode, computeInOrder = computeInOrder)
        } else {
            newValues.getValue(index).second
        }
    }

    override fun clearResult(index: Int) {
        val uIndex = indexMap[index]
        if (uIndex >= 0) {
            underlying.clearResult(uIndex)
        }
    }

    override fun peekType(index: Int): TypeTag<*>? {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) {
            underlying.peekType(uIndex)
        } else {
            null
        }
    }
}

private class ComputedActualsView(
    val underlying: ComputedActuals,
    val indexMap: IntArray,
    val keyOverrides: Map<Int, Symbol?>,
    val newValues: Map<Int, Pair<Symbol?, PartialResult>>,
) : ComputedActuals {
    override val size: Int get() = indexMap.size

    override fun key(index: Int): Symbol? = when (index) {
        in keyOverrides -> keyOverrides[index]
        in newValues -> newValues[index]?.first
        else -> underlying.key(indexMap[index])
    }

    override fun pos(index: Int): Position? {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) { underlying.pos(uIndex) } else { null }
    }

    override fun result(index: Int, computeInOrder: Boolean): PartialResult {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) {
            underlying.result(uIndex, computeInOrder = computeInOrder)
        } else {
            newValues.getValue(index).second
        }
    }

    override fun peekType(index: Int): TypeTag<*>? {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) {
            underlying.peekType(uIndex)
        } else {
            null
        }
    }

    override fun clearResult(index: Int) {
        val uIndex = indexMap[index]
        if (uIndex >= 0) {
            underlying.clearResult(uIndex)
        }
    }
}

private class ActualValuesView(
    val underlying: ActualValues,
    val indexMap: IntArray,
    val keyOverrides: Map<Int, Symbol?>,
    val newValues: Map<Int, Pair<Symbol?, Value<*>>>,
) : ActualValues {
    override val size: Int get() = indexMap.size

    override fun key(index: Int): Symbol? = when (index) {
        in keyOverrides -> keyOverrides[index]
        in newValues -> newValues[index]?.first
        else -> underlying.key(indexMap[index])
    }

    override fun pos(index: Int): Position? {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) { underlying.pos(uIndex) } else { null }
    }

    override fun result(index: Int, computeInOrder: Boolean): Value<*> {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) {
            underlying.result(uIndex, computeInOrder = computeInOrder)
        } else {
            newValues.getValue(index).second
        }
    }

    override fun peekType(index: Int): TypeTag<*>? {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) {
            underlying.peekType(uIndex)
        } else {
            null
        }
    }

    override fun clearResult(index: Int) {
        val uIndex = indexMap[index]
        if (uIndex >= 0) {
            underlying.clearResult(uIndex)
        }
    }
}

private class MacroActualsView(
    val underlying: MacroActuals,
    val indexMap: IntArray,
    val keyOverrides: Map<Int, Symbol?>,
    val newValues: Map<Int, Pair<ValueLeaf?, Tree>>,
) : MacroActuals {
    override val size: Int get() = indexMap.size

    override fun key(index: Int): Symbol? = when (index) {
        in keyOverrides -> keyOverrides[index]
        in newValues -> TSymbol.unpackOrNull((newValues[index]?.first)?.content)
        else -> underlying.key(indexMap[index])
    }

    override fun pos(index: Int): Position = valueTree(index).pos

    override fun result(
        index: Int,
        interpMode: InterpMode,
        computeInOrder: Boolean,
    ): PartialResult {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) {
            underlying.result(uIndex, interpMode = interpMode, computeInOrder = computeInOrder)
        } else {
            TODO("Link to a macro environment so we can evaluate the value tree")
        }
    }

    override fun evaluate(index: Int, interpMode: InterpMode): PartialResult {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) {
            underlying.evaluate(uIndex, interpMode)
        } else {
            TODO("Link to a macro environment so we can evaluate the value tree")
        }
    }

    override fun keyTree(index: Int): Tree? = when (index) {
        in keyOverrides -> null
        in newValues -> newValues.getValue(index).first
        else -> underlying.keyTree(indexMap[index])
    }

    override fun valueTree(index: Int): Tree {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) {
            underlying.valueTree(uIndex)
        } else {
            newValues.getValue(index).second
        }
    }

    override fun peekType(index: Int): TypeTag<*>? {
        val uIndex = indexMap[index]
        return if (uIndex >= 0) {
            underlying.peekType(uIndex)
        } else {
            null
        }
    }

    override val environment: Environment get() = underlying.environment

    override fun clearResult(index: Int) {
        val uIndex = indexMap[index]
        if (uIndex >= 0) {
            underlying.clearResult(uIndex)
        }
    }

    override val rawTreeList: List<Tree>
        get() = underlying.rawTreeList
}

private class ActualValueListWrapper(
    val values: List<Value<*>>,
    val positioned: List<Positioned>?,
) : ActualValues {
    override fun result(index: Int, computeInOrder: Boolean): Value<*> = values[index]
    override val size: Int get() = values.size
    override fun key(index: Int): Symbol? = null
    override fun pos(index: Int): Position? = positioned?.getOrNull(index)?.pos
    override fun peekType(index: Int): TypeTag<*>? = values[index].typeTag
    override fun clearResult(index: Int) {
        // Nothing to do
    }
}

/** Concatenate two actual value lists into one */
private class CatActualValues(
    private val a: ActualValues,
    private val b: ActualValues,
) : ActualValues {
    override val size: Int = a.size + b.size
    private val aSize = a.size

    override fun result(index: Int, computeInOrder: Boolean): Value<*> =
        if (index < aSize) {
            a.result(index, computeInOrder)
        } else {
            if (computeInOrder && aSize != 0) {
                a.result(aSize - 1, computeInOrder = true)
            }
            b.result(index - aSize, computeInOrder)
        }

    override fun key(index: Int): Symbol? =
        if (index < aSize) {
            a.key(index)
        } else {
            b.key(index - aSize)
        }

    override fun pos(index: Int): Position? =
        if (index < aSize) {
            a.pos(index)
        } else {
            b.pos(index - aSize)
        }

    override fun peekType(index: Int): TypeTag<*>? =
        if (index < aSize) {
            a.peekType(index)
        } else {
            b.peekType(index - aSize)
        }

    override fun clearResult(index: Int) {
        if (index < aSize) {
            a.clearResult(index)
        } else {
            b.clearResult(index - aSize)
        }
    }
}
