package lang.temper.value

import lang.temper.log.Position
import lang.temper.name.Symbol

typealias MetadataMap = Map<Symbol, TEdge>
typealias MetadataMultimap = Map<Symbol, List<TEdge>>
fun MetadataMap.positionForKey(key: Symbol): Position? {
    val valueEdge = this[key] ?: return null
    val possibleKey = valueEdge.source?.childOrNull(valueEdge.edgeIndex - 1)
    if (possibleKey is ValueLeaf && possibleKey.symbolContained == key) {
        return possibleKey.pos
    }
    return valueEdge.target.pos.leftEdge
}

/**
 * A value-oriented metadata map that also allows access to the underlying
 * tree structure when position and type information is important.
 */
interface MetadataValueMultimap : Map<Symbol, List<Value<*>?>> {
    fun getEdges(symbol: Symbol): List<TEdge>

    companion object {
        val empty: MetadataValueMultimap = EmptyMetadataValueMultimap
    }
}

private object EmptyMetadataValueMultimap : AbstractMap<Symbol, List<Value<*>?>>(), MetadataValueMultimap {
    override fun getEdges(symbol: Symbol): List<TEdge> = emptyList()

    override val entries: Set<Map.Entry<Symbol, List<Value<*>?>>> = emptySet()
}

// Rather than having imports of all `get` symbols in this large package
// this namespace lets Kotlin narrow the imports scope.
object MetadataValueMapHelpers {
    operator fun <T : Any> MetadataValueMultimap.get(key: Symbol, wanted: TypeTag<T>): T? =
        wanted.unpackOrNull(this[key]?.lastOrNull())
}

object MetadataMultimapHelpers {
    operator fun <T : Any> MetadataMultimap.get(key: Symbol, wanted: TypeTag<T>): T? =
        this[key]?.lastOrNull()?.target?.valueContained(wanted)
}

internal interface PartsI {
    val metadataSymbolMap: MetadataMap
    val metadataSymbolMultimap: MetadataMultimap
}

abstract class AbstractParts(
    override val metadataSymbolMultimap: MetadataMultimap,
) : PartsI {
    private var cachedSymbolMap: MetadataMap? = null
    override val metadataSymbolMap: MetadataMap
        get() {
            var map = this.cachedSymbolMap
            if (map == null) {
                map = metadataSymbolMultimap.mapValues { it.value.last() }
                cachedSymbolMap = map
            }
            return map
        }
}
