package lang.temper.type

import lang.temper.common.MappingListView
import lang.temper.log.Position
import lang.temper.name.Symbol
import lang.temper.value.DeclTree
import lang.temper.value.MetadataMultimap
import lang.temper.value.MetadataValueMultimap
import lang.temper.value.StayLeaf
import lang.temper.value.TEdge
import lang.temper.value.Value
import lang.temper.value.hoistLeftSymbol
import lang.temper.value.initSymbol
import lang.temper.value.ssaSymbol
import lang.temper.value.typeDeclSymbol
import lang.temper.value.valueContained
import lang.temper.value.visibilitySymbol

/** keys that should not end up on [lang.temper.type.TypeShape.metadata] */
val notTypeMetadataKeys =
    setOf(hoistLeftSymbol, initSymbol, ssaSymbol, typeDeclSymbol, visibilitySymbol)

/**
 * Adapts [Symbol]->[TEdge] maps from [DeclParts.metadataSymbolMultimap] to [Symbol]->[Value]?.
 *
 * The former is used in the front-end's AST.
 * The latter is more suitable for use in [MemberShape] metadata.
 */
abstract class AbstractMetadataValueMultimap : AbstractMap<Symbol, List<Value<*>?>>(), MetadataValueMultimap {
    override val entries: Set<Map.Entry<Symbol, List<Value<*>?>>> = MetadataValueMapEntriesImpl()

    protected abstract val underlying: MetadataMultimap?

    private fun edgesFor(key: Symbol): List<TEdge>? {
        if (key in notTypeMetadataKeys) { return null }
        return underlying?.get(key)
    }

    override fun getEdges(symbol: Symbol): List<TEdge> = edgesFor(symbol) ?: emptyList()

    override fun get(key: Symbol): List<Value<*>?>? =
        edgesFor(key)?.let { MappingListView(it, ::valueAtEdge) }

    override fun containsKey(key: Symbol): Boolean =
        !edgesFor(key).isNullOrEmpty()

    private inner class MetadataValueMapEntriesImpl : AbstractSet<Map.Entry<Symbol, List<Value<*>?>>>() {
        override val size: Int
            get() =
                when (val m = underlying) {
                    null -> 0
                    else -> m.keys.count { it !in notTypeMetadataKeys }
                }

        override fun iterator() = MetadataValueMultimapEntrySetIteratorImpl(
            (underlying ?: emptyMap()).entries.iterator(),
        )
    }

    private class MetadataValueMultimapEntrySetIteratorImpl(
        private val underlying: Iterator<Map.Entry<Symbol, List<TEdge>>>,
    ) : Iterator<Map.Entry<Symbol, List<Value<*>?>>> {
        private var lookahead: Map.Entry<Symbol, List<TEdge>>? = null

        private fun skipExcluded() {
            while (lookahead == null && underlying.hasNext()) {
                val next = underlying.next()
                if (next.key !in notTypeMetadataKeys) {
                    lookahead = next
                }
            }
        }

        override fun hasNext(): Boolean {
            skipExcluded()
            return lookahead != null
        }

        override fun next(): Map.Entry<Symbol, List<Value<*>?>> {
            skipExcluded()
            val next = lookahead ?: throw NoSuchElementException()
            lookahead = null
            return MetadataValueMapEntryImpl(next)
        }
    }

    private class MetadataValueMapEntryImpl(
        val underlying: Map.Entry<Symbol, List<TEdge>>,
    ) : Map.Entry<Symbol, List<Value<*>?>> {
        override val key: Symbol get() = underlying.key
        override val value: List<Value<*>?> get() = MappingListView(
            underlying.value,
            ::valueAtEdge,
        )
    }
}

internal class MetadataValueMultimapImpl(
    private val stay: StayLeaf,
) : AbstractMetadataValueMultimap() {
    override val underlying: MetadataMultimap? get() {
        val decl = stay.incoming?.source as? DeclTree
        return decl?.parts?.metadataSymbolMultimap
    }
}

private fun valueAtEdge(edge: TEdge): Value<*>? = edge.target.valueContained

fun MetadataValueMultimap.keyPosition(valueEdge: TEdge): Position {
    val parent = valueEdge.source
    val index = valueEdge.edgeIndex
    if (index != 0 && parent != null) {
        val preceder = parent.edgeOrNull(index - 1)
        if (preceder != null) {
            return preceder.target.pos
        }
    }
    return valueEdge.target.pos.leftEdge
}

val MetadataMultimap.valueMap: MetadataValueMultimap get() = MetadataValueMultimapWrapper(this)

private class MetadataValueMultimapWrapper(
    override val underlying: MetadataMultimap,
) : AbstractMetadataValueMultimap()
