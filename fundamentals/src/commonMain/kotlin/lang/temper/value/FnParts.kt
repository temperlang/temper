package lang.temper.value

import lang.temper.common.EnumRange
import lang.temper.common.safeLet
import lang.temper.stage.Stage
import lang.temper.type.AndType
import lang.temper.type.NominalType
import lang.temper.type.RestFormal
import lang.temper.type.StaticType
import lang.temper.type.SuperTypeTree
import lang.temper.type.TypeFormal
import lang.temper.type.WellKnownTypes

/**
 * The children of a [FunTree] node decomposed by the role they play in the function definition.
 */
class FnParts internal constructor(
    val typeFormals: List<Pair<TEdge, TypeFormal?>>,
    val formals: List<DeclTree>,
    // Since the actual type of the varArg from the caller's perspective doesn't exist as a decl
    // we need a more advanced type here
    val restFormal: RestFormal?,
    val connected: String?,
    metadataSymbolMultimap: MetadataMultimap,
    val body: Tree,
) : AbstractParts(metadataSymbolMultimap) {
    private val superTypesLazy = lazy {
        val incomplete = mutableListOf<TEdge>()
        val superTypes = mutableSetOf<NominalType>()
        metadataSymbolMultimap[superSymbol]?.forEach { valueEdge ->
            fun addNominalTypesTo(t: StaticType?, out: MutableSet<NominalType>): Boolean {
                return when (t) {
                    is NominalType -> {
                        out.add(t)
                        true
                    }
                    is AndType ->
                        t.members.all { memberType ->
                            addNominalTypesTo(memberType, out)
                        }
                    else -> false
                }
            }
            val allOk = addNominalTypesTo(valueEdge.target.staticTypeContained, superTypes)
            if (!allOk) {
                incomplete.add(valueEdge)
            }
        }
        SuperTypeParts(SuperTypeTree(superTypes), incomplete = incomplete.toList())
    }
    val superTypes get() = superTypesLazy.value

    val mayYield: Boolean? get() = when {
        superTypes.superTypeTree[WellKnownTypes.generatorFnTypeDefinition].isNotEmpty() -> true
        superTypes.isComplete -> false
        else -> null
    }

    val returnDecl: DeclTree?
        get() = metadataSymbolMultimap[returnDeclSymbol]?.lastOrNull()?.target as? DeclTree
    val returnedFrom: Boolean? get() {
        val t = metadataSymbolMultimap[returnedFromSymbol]?.lastOrNull()?.target
        return t?.valueContained(TBoolean)
    }
    val stageRange: EnumRange<Stage>? get() {
        val t = metadataSymbolMultimap[stageRangeSymbol]?.lastOrNull()?.target
        return t?.valueContained(TStageRange)
    }
    val word: NameLeaf? get() = metadataSymbolMultimap[wordSymbol]?.lastOrNull()?.target as? NameLeaf

    companion object {
        /** Given a metadata value tree following [typeFormalSymbol], extract the type formal. */
        fun unpackTypeFormal(tree: Tree): TypeFormal? {
            // During early stages, target is a block of stuff to be hoisted out
            val type = tree.staticTypeContained
            return if (type is NominalType && type.bindings.isEmpty()) {
                type.definition as? TypeFormal
            } else {
                null
            }
        }
    }
}

/**
 * Given a [function][FunTree] tree, group its children by the roles they play.
 */
internal fun decomposeFun(tree: FunTree, metadataMultimap: LiveMetadataMap): FnParts? {
    var childIndex = 0
    val n = tree.size

    // Expect:
    // 1. a run of declarations of parameters.
    //    The ones in the (...) part of the signature.
    // 2. (symbol key, metadata value) tree pairs
    //    some of which are \typeFormal Value<BaseReifiedType> which are reified type formals.
    // 3. a single body

    // 1: The run of declarations of parameters
    val allFormals = mutableListOf<DeclTree>()
    while (childIndex < n) {
        val c = tree.child(childIndex)
        if (c !is DeclTree) {
            break
        }
        allFormals.add(c)
        childIndex += 1
    }

    if (childIndex != metadataMultimap.startEdgeIndex) {
        return null
    }

    // 2: metadata specified as (symbol, tree) pairs.
    childIndex = metadataMultimap.lastEdgeIndex
    val typeFormals = metadataMultimap[typeFormalSymbol].map {
        it to FnParts.unpackTypeFormal(it.target)
    }
    val connected = metadataMultimap.get1(connectedSymbol)?.target?.valueContained(TString)

    // 3: The body
    if (childIndex + 1 != n) {
        return null
    }
    val body = tree.child(childIndex)

    val (formals, restFormal) = allFormals.toList().partition { it.parts?.isRestFormal == false }

    return FnParts(
        typeFormals = typeFormals,
        formals = formals,
        restFormal = typeOfList(restFormal.firstOrNull()),
        connected = connected,
        metadataSymbolMultimap = metadataMultimap,
        body = body,
    )
}

private fun typeOfList(tree: DeclTree?): RestFormal? {
    val typeN = tree?.parts?.type?.target?.staticTypeContained?.let { listType ->
        val nominalType = listType as? NominalType
        nominalType?.bindings?.firstOrNull() as? StaticType
    }
    val nameN = tree?.parts?.name?.content

    val posN = tree?.pos

    return safeLet(typeN, nameN, posN, tree) { type, name, pos, t ->
        RestFormal(name, type, pos, t)
    }
}

/**
 * The declared super types of a function as declared in its signature's `extends` clause.
 */
data class SuperTypeParts(
    val superTypeTree: SuperTypeTree,
    /** Edges that did not resolve to valid type expressions */
    val incomplete: List<TEdge>,
) {
    val isComplete: Boolean get() = incomplete.isEmpty()
}
