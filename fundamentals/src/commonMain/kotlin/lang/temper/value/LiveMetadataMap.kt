package lang.temper.value

import lang.temper.common.putMultiList
import lang.temper.name.Symbol
import kotlin.jvm.Synchronized

/**
 * A [MetadataMultimap] implementation that caches information
 * about symbol/edge pairs and which can be invalidated by [Tree]
 * implementations when their edges change.
 */
internal class LiveMetadataMap(
    val tree: Tree,
    val skipAtFront: (Int, Tree) -> Boolean,
    val nFromEndToSkip: Int,
) : Map<Symbol, List<TEdge>> {
    private var cache: MetadataMultimap = emptyMap()
    private var startEdgeIndexCached: Int = -1
    private var lastEdgeIndexCached: Int = -1
    private var upToDate = false

    @Synchronized
    internal fun invalidate() {
        cache = emptyMap()
        startEdgeIndexCached = -1
        lastEdgeIndexCached = -1
        upToDate = false
    }

    /** The index of the start of the run of metadata pairs. */
    val startEdgeIndex: Int get() = whileValid { this.startEdgeIndexCached }

    /** The index of the end of the run of metadata pairs. */
    val lastEdgeIndex: Int get() = whileValid { this.lastEdgeIndexCached }

    @Synchronized
    fun <T> whileValid(f: LiveMetadataMap.(MetadataMultimap) -> T): T {
        while (!upToDate) {
            this.upToDate = true

            val tree = this.tree
            val limit = tree.size - nFromEndToSkip

            val multimap = mutableMapOf<Symbol, MutableList<TEdge>>()
            var i = 0
            while (i < limit && skipAtFront(i, tree.child(i))) {
                i += 1
            }
            this.startEdgeIndexCached = i
            while (i + 1 < limit) {
                val possibleKey = tree.child(i)
                val key = possibleKey.symbolContained ?: break
                val valueEdge = tree.edge(i + 1)
                multimap.putMultiList(key, valueEdge)
                i += 2
            }
            this.lastEdgeIndexCached = i
            this.cache = multimap.mapValues { it.value.toList() }
        }
        return f(cache)
    }

    override val entries: Set<Map.Entry<Symbol, List<TEdge>>>
        get() = whileValid { it.entries }
    override val keys: Set<Symbol>
        get() = whileValid { it.keys }
    override val size: Int
        get() = whileValid { it.size }
    override val values: Collection<List<TEdge>>
        get() = whileValid { it.values }

    override fun isEmpty(): Boolean = whileValid { it.isEmpty() }

    override fun get(key: Symbol): List<TEdge> = whileValid { it[key] ?: emptyList() }

    /** Like [get] but instead of returning all of them, returns the last edge or null. */
    fun get1(key: Symbol) = get(key).lastOrNull()

    override fun containsValue(value: List<TEdge>): Boolean =
        whileValid { it.containsValue(value) }

    override fun containsKey(key: Symbol): Boolean =
        whileValid { it.containsKey(key) }

    override fun toString(): String = whileValid { "$it" }
}
