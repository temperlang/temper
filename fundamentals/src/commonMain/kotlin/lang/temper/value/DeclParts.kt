package lang.temper.value

import lang.temper.common.TriState

internal interface DeclPartsI : PartsI {
    val word: TEdge? get() = metadataSymbolMultimap[wordSymbol]?.lastOrNull()
    val type: TEdge? get() = metadataSymbolMultimap[typeSymbol]?.lastOrNull()
    val optional: TEdge? get() = metadataSymbolMultimap[optionalSymbol]?.lastOrNull()
}

class DeclParts internal constructor(
    val name: NameLeaf,
    metadataSymbolMultimap: MetadataMultimap,
) : DeclPartsNameless(metadataSymbolMultimap) {
    /** Use `.elseTrue` for simple usage because [TriState.OTHER] means optional but defaulting to null. */
    val isOptional: TriState get() = optionalAsTriState(this.optional)
    val isRestFormal: Boolean get() = restFormalSymbol in metadataSymbolMap

    operator fun component1() = name
    operator fun component2() = word
    operator fun component3() = type
}

internal fun decomposeDecl(tree: DeclTree, metadataSymbolMultimap: LiveMetadataMap): DeclPartsNameless? {
    val name = tree.childOrNull(0) as? NameLeaf
    // Consistency checks
    val nameless = decomposeDeclIgnoringName(tree, metadataSymbolMultimap) ?: return null
    return if (name != null) {
        return DeclParts(
            name = name,
            metadataSymbolMultimap = nameless.metadataSymbolMultimap,
        )
    } else {
        nameless
    }
}

open class DeclPartsNameless internal constructor(
    metadataSymbolMultimap: MetadataMultimap,
) : DeclPartsI, AbstractParts(metadataSymbolMultimap)

internal fun decomposeDeclIgnoringName(tree: Tree, metadataMap: LiveMetadataMap): DeclPartsNameless? {
    val n = tree.size
    if (n == 0) {
        return null
    }
    val lastEdgeIndex = metadataMap.lastEdgeIndex
    return if (lastEdgeIndex == n) {
        DeclPartsNameless(metadataMap)
    } else {
        null
    }
}

fun optionalAsTriState(tEdge: TEdge?): TriState = optionalAsTriState(tEdge?.target?.valueContained)
fun optionalAsTriState(value: Value<*>?): TriState = when (value) {
    TBoolean.valueFalse -> TriState.FALSE
    TBoolean.valueTrue -> TriState.TRUE
    // Null means it's optional but defaults to null, so it might need special handling.
    TNull.value -> TriState.OTHER
    else -> TriState.FALSE
}
