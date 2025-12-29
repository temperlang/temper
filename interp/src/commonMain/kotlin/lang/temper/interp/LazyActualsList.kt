package lang.temper.interp

import lang.temper.common.toStringViaBuilder
import lang.temper.env.Environment
import lang.temper.env.InterpMode
import lang.temper.name.Symbol
import lang.temper.value.CallTree
import lang.temper.value.ComputedActuals
import lang.temper.value.FunTree
import lang.temper.value.MacroActuals
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TFunction
import lang.temper.value.TSymbol
import lang.temper.value.Tree
import lang.temper.value.TypeTag
import lang.temper.value.ValueLeaf
import lang.temper.value.firstArgumentIndex
import lang.temper.value.valueContained

/**
 * Lazily evaluated arguments.
 */
class LazyActualsList(
    treeList: List<Tree>,
    private val interpreter: Interpreter?,
    override val environment: Environment,
    val interpMode: InterpMode,
) : MacroActuals, ComputedActuals {
    override val rawTreeList = treeList
    private val computed = mutableListOf<PartialResult?>()
    private var computedAllBefore = 0

    private val treeIndex: IntArray
    private val keys: List<Symbol?>
    init {
        val actualIndexToTreeIndex = mutableListOf<Int>()
        val keys = mutableListOf<Symbol?>()
        rawTreeList.forEachActual { treeIndex, _, symbol, _ ->
            keys.add(symbol?.let { TSymbol.unpack(it.content) })
            actualIndexToTreeIndex.add(treeIndex)
        }
        this.keys = keys.toList()
        this.treeIndex = actualIndexToTreeIndex.toIntArray()
    }

    override val size: Int = keys.size

    override fun clearResult(index: Int) {
        if (index in computed.indices) {
            computed[index] = null
        }
    }

    override fun key(index: Int) = keys[index]

    override fun pos(index: Int) = valueTree(index).pos

    override fun result(index: Int, computeInOrder: Boolean): PartialResult =
        result(index, interpMode, computeInOrder)

    override fun result(
        index: Int,
        interpMode: InterpMode,
        computeInOrder: Boolean,
    ): PartialResult {
        @Suppress("UnnecessaryVariable") // Keep tree and actual indices distinct
        val actualIndex = index
        require(actualIndex in 0 until size)
        val r = computed.getOrNull(actualIndex)
        if (r != null) {
            return r
        }
        while (computed.size <= actualIndex) {
            computed.add(null)
        }
        if (computeInOrder) {
            for (i in computedAllBefore until actualIndex) {
                if (computed[i] == null) {
                    val result = interpret(i, environment, interpMode)
                    computed[i] = result
                }
                computedAllBefore = i + 1
            }
        }
        val result = interpret(actualIndex, environment, interpMode)
        if (actualIndex == computed.size) {
            computed += result
        } else {
            computed[actualIndex] = result
        }
        if (computedAllBefore + 1 == actualIndex) {
            computedAllBefore += 1
        }
        return result
    }

    override fun peekType(index: Int): TypeTag<*>? = when (val tree = valueTree(index)) {
        is FunTree -> TFunction
        else -> tree.valueContained?.typeTag
    }

    override fun evaluate(index: Int, interpMode: InterpMode): PartialResult =
        interpret(index, environment, interpMode)

    private fun interpret(
        actualIndex: Int,
        env: Environment,
        interpMode: InterpMode,
    ): PartialResult {
        val tree = valueTree(actualIndex)
        if (interpreter == null) { return NotYet }
        return when (val edge = tree.incoming) {
            null -> interpreter.interpret(tree, env, interpMode)
            else -> interpreter.interpretEdge(edge, env, interpMode)
        }
    }

    override fun toString(): String = toStringViaBuilder {
        // This toString is used in error messages like MessageTemplate.NotApplicableTo
        // Debugging the actuals list should not cause interpretation.
        it.append('(')
        for (i in rawTreeList.indices) {
            if (i != 0) { it.append(", ") }
            val tree = rawTreeList[i]
            val value = computed.getOrNull(i) ?: tree.valueContained
            if (value != null) {
                it.append(value)
            } else {
                it.append('(').append(tree.treeType.name).append(')')
            }
        }
        it.append(')')
    }

    override fun keyTree(index: Int): Tree? {
        if (keys[index] == null) { return null }
        return rawTreeList[treeIndex[index] - 1]
    }

    override fun valueTree(index: Int): Tree = rawTreeList[treeIndex[index]]
}

/** Iterates through possibly named actual arguments, without explicit allocations. */
inline fun List<Tree>.forEachActual(
    firstIndex: Int = 0,
    handle: (treeIndex: Int, actualIndex: Int, symbol: ValueLeaf?, value: Tree) -> Unit,
) {
    val lastIndex = size - 1
    var treeIndex = firstIndex
    var actualIndex = 0
    while (treeIndex <= lastIndex) {
        var symbol: ValueLeaf? = null
        var value = this[treeIndex]
        if (value is ValueLeaf && treeIndex < lastIndex) {
            val content = value.content
            if (content.typeTag == TSymbol) {
                // Symbols are named args, so grab it and advance.
                symbol = value
                treeIndex += 1
                value = this[treeIndex]
            }
        }
        handle(treeIndex, actualIndex, symbol, value)
        actualIndex += 1
        treeIndex += 1
    }
}

inline fun CallTree.forEachActual(handle: (index: Int, symbol: ValueLeaf?, value: Tree) -> Unit) {
    // This still allocates a `Children` facade but that should be all.
    children.forEachActual(firstArgumentIndex) { _, actualIndex, symbol, value ->
        handle(actualIndex, symbol, value)
    }
}
