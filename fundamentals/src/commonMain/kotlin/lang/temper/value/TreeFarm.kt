package lang.temper.value

import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.spanningPosition
import lang.temper.name.NameMaker
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes

typealias FlowMaker = (BlockTree) -> BlockFlow
val linearFlowMaker: FlowMaker = { LinearFlow }

/**
 * May be invoked with a block-lambda to allow specifying a tree using a simple DSL like
 *
 *      x.treeFarm.grow(pos) { // grow produces a singe tree
 *        Block {
 *          Call(fn) {
 *            V(x)             // V is shorthand for value
 *            Rn(n)            // Rn is shorthand for right-name
 *          }
 *        }
 *      }
 *
 * which grows a block whose child list consists of a call to *fn* with value *x* and name *n* as
 * arguments.
 */
class TreeFarm( // TODO: find an excuse to rename this to Overalls
    /** The [document][Tree.document] for produced trees. */
    val document: Document,
) {
    /** Grow a single tree that. */
    fun <TREE : Tree> grow(
        plot: (Planting).() -> TreeTemplate<TREE>,
    ): TREE {
        val planting = SingleTreePlanting(document.nameMaker)
        val template = planting.plot()
        check(planting.didPlant)
        return template.toTree(document)
    }

    /** Grow a single tree using the given position as default position metadata. */
    fun <TREE : Tree> grow(
        pos: Position,
        plot: (Planting).() -> UnpositionedTreeTemplate<TREE>,
    ): TREE {
        val planting = SingleTreePlanting(document.nameMaker)
        val template = planting.plot()
        check(planting.didPlant)
        return template.toTree(document, pos)
    }

    /**
     * Grow a single tree whose position is the spanning position of its descendants.
     * The caller is responsible for ensuring that at least one descendant has position metadata.
     */
    fun <TREE : Tree> growS(
        plot: (Planting).() -> UnpositionedTreeTemplate<TREE>,
    ): TREE {
        val planting = SingleTreePlanting(document.nameMaker)
        val template = planting.plot()
        check(planting.didPlant)
        return template.toTree(document, template.spannedPosition!!)
    }

    /**
     * Grow a list of trees.  The block body may include zero or more tree constructing function
     * calls.
     */
    fun growAll(
        pos: Position,
        plot: (Planting).() -> Any?,
    ): List<Tree> {
        val planting = RowPlanting(document.nameMaker)
        planting.plot()
        return buildTreeList(document, pos, planting.childList)
    }

    /**
     * Grow a list of tree templates that may be replanted later.
     */
    fun seedAll(
        plot: (Planting).() -> Any?,
    ): List<UnpositionedTreeTemplate<*>> {
        val planting = RowPlanting(document.nameMaker)
        planting.plot()
        return planting.childList
    }
}

/**
 * Contains methods for creating tree nodes.  Used as a `this` value in lambda blocks, the method
 * names can be used bare.
 */
// The creation functions are upper-cased so that they do not conflict with common names for local
// variables holding trees, and because their primary responsibility is construction, like
// constructors whose names are upper-cased.
@Suppress("FunctionName")
abstract class Planting(
    private val nameMaker: NameMaker,
) {
    /**
     * Called for each created template with the template.
     */
    internal abstract fun <TREE : Tree, TT : UnpositionedTreeTemplate<TREE>>
    planted(t: TT): TT

    abstract val numPlanted: Int

    fun Block(
        flowMaker: FlowMaker = linearFlowMaker,
        type: StaticType? = null,
        children: (Planting).() -> Any?,
    ): UnpositionedTreeTemplate<BlockTree> {
        val row = RowPlanting(nameMaker)
        row.children()
        return planted(UnpositionedBlockTemplate(flowMaker, type, row.childList))
    }

    /** Builds a block, "spanning", inferring position metadata from the children. */
    fun BlockS(
        flowMaker: FlowMaker = linearFlowMaker,
        type: StaticType? = null,
        children: (Planting).() -> Any?,
    ): TreeTemplate<BlockTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        val childList = rowPlanting.childList
        return planted(
            BlockTemplate(
                rowPlanting.spannedPosition!!, // User is responsible for making sure somebody has a position.
                flowMaker,
                type,
                childList,
            ),
        )
    }

    fun Block(
        pos: Position,
        flowMaker: FlowMaker = linearFlowMaker,
        type: StaticType? = null,
        children: (Planting).() -> Any?,
    ): TreeTemplate<BlockTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        val childList = rowPlanting.childList
        return planted(BlockTemplate(pos, flowMaker, type, childList))
    }

    fun Call(
        callee: MacroValue,
        type: CallTypeInferences? = null,
        children: (Planting).() -> Any?,
    ): UnpositionedTreeTemplate<CallTree> = Call(Value(callee), type, children)

    fun Call(
        callee: Value<MacroValue>,
        type: CallTypeInferences? = null,
        children: (Planting).() -> Any?,
    ): UnpositionedTreeTemplate<CallTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.V(callee, type?.variant)
        rowPlanting.children()
        return planted(UnpositionedCallTemplate(type, rowPlanting.childList))
    }

    fun Call(
        type: CallTypeInferences? = null,
        children: (Planting).() -> Any?,
    ): UnpositionedTreeTemplate<CallTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        return planted(UnpositionedCallTemplate(type, rowPlanting.childList))
    }

    fun Call(
        pos: Position,
        callee: MacroValue,
        type: CallTypeInferences? = null,
        children: (Planting).() -> Any?,
    ): TreeTemplate<CallTree> = Call(pos, Value(callee), type, children)

    fun Call(
        pos: Position,
        callee: Value<MacroValue>,
        type: CallTypeInferences? = null,
        children: (Planting).() -> Any?,
    ): TreeTemplate<CallTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.V(callee, type = type?.variant)
        rowPlanting.children()
        return planted(CallTemplate(pos, type, rowPlanting.childList))
    }

    fun Call(
        pos: Position,
        type: CallTypeInferences? = null,
        children: (Planting).() -> Any?,
    ): TreeTemplate<CallTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        return planted(CallTemplate(pos, type, rowPlanting.childList))
    }

    fun CallS(
        callee: MacroValue,
        type: CallTypeInferences? = null,
        children: (Planting).() -> Any?,
    ): TreeTemplate<CallTree> = CallS(Value(callee), type, children)

    fun CallS(
        callee: Value<MacroValue>,
        type: CallTypeInferences? = null,
        children: (Planting).() -> Any?,
    ): TreeTemplate<CallTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.V(callee, type = type?.variant)
        rowPlanting.children()
        return planted(CallTemplate(rowPlanting.spannedPosition!!, type, rowPlanting.childList))
    }

    fun CallS(
        type: CallTypeInferences? = null,
        children: (Planting).() -> Any?,
    ): TreeTemplate<CallTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        return planted(CallTemplate(rowPlanting.spannedPosition!!, type, rowPlanting.childList))
    }

    fun Decl(
        name: TemperName,
        children: (Planting).() -> Any? = {},
    ): UnpositionedTreeTemplate<DeclTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.Ln(name)
        rowPlanting.children()
        return planted(UnpositionedDeclTemplate(rowPlanting.childList))
    }

    fun Decl(
        children: (Planting).() -> Any?,
    ): UnpositionedTreeTemplate<DeclTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        return planted(UnpositionedDeclTemplate(rowPlanting.childList))
    }

    fun Decl(
        pos: Position,
        name: TemperName,
        children: (Planting).() -> Any? = {},
    ): TreeTemplate<DeclTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.Ln(name)
        rowPlanting.children()
        return planted(DeclTemplate(pos, rowPlanting.childList))
    }

    fun Decl(
        pos: Position,
        children: (Planting).() -> Any?,
    ): TreeTemplate<DeclTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        return planted(DeclTemplate(pos, rowPlanting.childList))
    }

    fun DeclS(
        name: TemperName,
        children: (Planting).() -> Any? = {},
    ): TreeTemplate<DeclTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.Ln(name)
        rowPlanting.children()
        return planted(DeclTemplate(rowPlanting.spannedPosition!!, rowPlanting.childList))
    }

    fun DeclS(
        children: (Planting).() -> Any?,
    ): TreeTemplate<DeclTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        return planted(DeclTemplate(rowPlanting.spannedPosition!!, rowPlanting.childList))
    }

    fun Esc(
        children: (Planting).() -> Any?,
    ): UnpositionedTreeTemplate<EscTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        return planted(UnpositionedEscTemplate(rowPlanting.childList))
    }

    fun Esc(
        pos: Position,
        children: (Planting).() -> Any?,
    ): TreeTemplate<EscTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        return planted(EscTemplate(pos, rowPlanting.childList))
    }

    fun Fn(
        type: StaticType? = null,
        children: (Planting).() -> Any?,
    ): UnpositionedTreeTemplate<FunTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        return planted(UnpositionedFunTemplate(type, rowPlanting.childList))
    }

    fun Fn(
        pos: Position,
        type: StaticType? = null,
        children: (Planting).() -> Any?,
    ): TreeTemplate<FunTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        return planted(FunTemplate(pos, type, rowPlanting.childList))
    }

    fun FnS(
        type: StaticType? = null,
        children: (Planting).() -> Any?,
    ): TreeTemplate<FunTree> {
        val rowPlanting = RowPlanting(nameMaker)
        rowPlanting.children()
        return planted(FunTemplate(rowPlanting.spannedPosition!!, type, rowPlanting.childList))
    }

    /** ln is shorthand for left name: a name used in assignment/write position. */
    fun Ln(name: TemperName, type: StaticType? = null): UnpositionedTreeTemplate<LeftNameLeaf> =
        planted(UnpositionedLeftNameLeafTemplate(type, name))
    fun Ln(pos: Position, name: TemperName, type: StaticType? = null): TreeTemplate<LeftNameLeaf> =
        planted(LeftNameLeafTemplate(pos, type, name))
    fun Ln(type: StaticType? = null, makeName: (NameMaker) -> TemperName) =
        Ln(makeName.invoke(nameMaker), type)
    fun Ln(pos: Position, type: StaticType? = null, makeName: (NameMaker) -> TemperName) =
        Ln(pos, makeName.invoke(nameMaker), type)

    /** rn is shorthand for right name: a name used in read position. */
    fun Rn(name: TemperName, type: StaticType? = null): UnpositionedTreeTemplate<RightNameLeaf> =
        planted(UnpositionedRightNameLeafTemplate(type, name))
    fun Rn(pos: Position, name: TemperName, type: StaticType? = null): TreeTemplate<RightNameLeaf> =
        planted(RightNameLeafTemplate(pos, type, name))
    fun Rn(type: StaticType? = null, makeName: (NameMaker) -> TemperName) =
        Rn(makeName.invoke(nameMaker), type)
    fun Rn(pos: Position, type: StaticType? = null, makeName: (NameMaker) -> TemperName) =
        Rn(pos, makeName.invoke(nameMaker), type)

    fun Stay(): UnpositionedTreeTemplate<StayLeaf> =
        planted(UnpositionedStayLeafTemplate)
    fun Stay(pos: Position): TreeTemplate<StayLeaf> =
        planted(StayLeafTemplate(pos))

    fun V(value: Value<*>, type: StaticType? = null): UnpositionedTreeTemplate<ValueLeaf> =
        planted(UnpositionedValueLeafTemplate(type, value))
    fun V(symbol: Symbol): UnpositionedTreeTemplate<ValueLeaf> =
        V(Value(symbol))
    fun V(pos: Position, value: Value<*>, type: StaticType? = null): TreeTemplate<ValueLeaf> =
        planted(ValueLeafTemplate(pos, type, value))
    fun V(pos: Position, symbol: Symbol): TreeTemplate<ValueLeaf> =
        V(pos, Value(symbol))

    fun <TREE : Tree> Replant(tree: TREE): TreeTemplate<TREE> =
        planted(SingleUseTreeWrapper(tree))
    fun Replant(edge: TEdge): TreeTemplate<Tree> =
        planted(SingleUseEdgeWrapper(edge))
    fun Replant(templates: Iterable<UnpositionedTreeTemplate<*>>) {
        templates.forEach { planted(it) }
    }
}

/**
 * When planting a row of trees as children of another tree, collects them in a list.
 */
private class RowPlanting(
    nameMaker: NameMaker,
) : Planting(nameMaker) {
    private val children = mutableListOf<UnpositionedTreeTemplate<*>>()

    override fun <TREE : Tree, TT : UnpositionedTreeTemplate<TREE>>
    planted(t: TT): TT {
        children.add(t)
        return t
    }

    override val numPlanted: Int get() = children.size

    val childList get() = children.toList()

    val spannedPosition: Position? get() = spannedPositionOf(children)
}

private fun spannedPositionOf(templates: Iterable<UnpositionedTreeTemplate<*>>): Position? {
    val positions = templates.mapNotNull { it.spannedPosition }
    return if (positions.isNotEmpty()) {
        positions.spanningPosition(positions[0])
    } else {
        null
    }
}

/**
 * Used when a block is meant to produce a single tree.
 */
private class SingleTreePlanting(
    nameMaker: NameMaker,
) : Planting(nameMaker) {
    private var plantedOne = false

    override val numPlanted: Int get() = if (plantedOne) 1 else 0

    override fun <TREE : Tree, TT : UnpositionedTreeTemplate<TREE>>
    planted(t: TT): TT {
        check(!plantedOne) { "plantedOne before $t" }
        plantedOne = true
        return t
    }

    val didPlant get() = plantedOne
}

private fun buildTreeList(
    document: Document,
    pos: Position,
    templates: List<UnpositionedTreeTemplate<*>>,
): List<Tree> {
    val out = mutableListOf<Tree?>()

    fun walk(i: Int, left: Position, right: Position): Position {
        if (i == templates.size) {
            return right
        }
        return when (val template = templates[i]) {
            is TreeTemplate -> {
                val t = template.toTree(document)
                out.add(t)
                walk(i + 1, t.pos.rightEdge, right)
                t.pos.leftEdge
            }
            else -> {
                val index = out.size
                out.add(null) // Placeholder.  Will be replaced with a non-null value.
                val fromTail = walk(i + 1, left, right)
                val tPos = listOf(left, fromTail).spanningPosition(left)
                out[index] = template.toTree(document, tPos)
                fromTail
            }
        }
    }
    walk(0, pos.leftEdge, pos.rightEdge)

    return out.map { it!! }
}

/**
 * Something that, given a position, we can derive a tree.
 */
sealed class UnpositionedTreeTemplate<TREE : Tree> {
    fun toTree(document: Document, pos: Position): TREE = at(pos).toTree(document)
    abstract infix fun at(pos: Position): TreeTemplate<TREE>
    fun orAt(pos: Position): TreeTemplate<TREE> = when (this) {
        is TreeTemplate -> this
        else -> this.at(pos)
    }
    abstract val spannedPosition: Position?
}

/**
 * An [UnpositionedTreeTemplate] that also has its own position metadata.
 */
sealed class TreeTemplate<TREE : Tree>(
    override val pos: Position,
) : UnpositionedTreeTemplate<TREE>(), Positioned {
    abstract val typeInferences: TypeInferences?
    abstract fun toTree(document: Document): TREE
    override fun at(pos: Position): TreeTemplate<TREE> = this
    override val spannedPosition: Position get() = pos
}

private class UnpositionedBlockTemplate(
    val flowMaker: FlowMaker = linearFlowMaker,
    val type: StaticType?,
    val children: List<UnpositionedTreeTemplate<*>>,
) : UnpositionedTreeTemplate<BlockTree>() {
    override fun at(pos: Position): TreeTemplate<BlockTree> =
        BlockTemplate(pos, flowMaker, type, children)

    override val spannedPosition: Position?
        get() = spannedPositionOf(children)
}

private class BlockTemplate(
    pos: Position,
    val flowMaker: FlowMaker = linearFlowMaker,
    val type: StaticType?,
    val children: List<UnpositionedTreeTemplate<*>>,
) : TreeTemplate<BlockTree>(pos) {
    override val typeInferences
        get() = type?.let { BasicTypeInferences(it, listOf()) }

    override fun toTree(document: Document): BlockTree {
        val block = BlockTree(document, pos, buildTreeList(document, pos, children), LinearFlow)
        block.flow = flowMaker(block)
        block.typeInferences = typeInferences
        return block
    }
}

private class UnpositionedCallTemplate(
    val type: CallTypeInferences?,
    val children: List<UnpositionedTreeTemplate<*>>,
) : UnpositionedTreeTemplate<CallTree>() {
    override fun at(pos: Position): TreeTemplate<CallTree> =
        CallTemplate(pos, type, children)

    override val spannedPosition: Position?
        get() = spannedPositionOf(children)
}

private class CallTemplate(
    pos: Position,
    override val typeInferences: CallTypeInferences?,
    val children: List<UnpositionedTreeTemplate<*>>,
) : TreeTemplate<CallTree>(pos) {
    override fun toTree(document: Document): CallTree {
        val childList = buildTreeList(document, pos, children)
        val tree = CallTree(document, pos, childList)
        tree.typeInferences = typeInferences
        return tree
    }
}

private class UnpositionedDeclTemplate(
    val children: List<UnpositionedTreeTemplate<*>>,
) : UnpositionedTreeTemplate<DeclTree>() {
    override fun at(pos: Position): TreeTemplate<DeclTree> =
        DeclTemplate(pos, children)

    override val spannedPosition: Position?
        get() = spannedPositionOf(children)
}

private class DeclTemplate(
    pos: Position,
    val children: List<UnpositionedTreeTemplate<*>>,
) : TreeTemplate<DeclTree>(pos) {
    override val typeInferences
        get() = BasicTypeInferences(WellKnownTypes.voidType, listOf())

    override fun toTree(document: Document): DeclTree {
        val tree = DeclTree(document, pos, buildTreeList(document, pos, children))
        tree.typeInferences = typeInferences
        return tree
    }
}

private class UnpositionedEscTemplate(
    val children: List<UnpositionedTreeTemplate<*>>,
) : UnpositionedTreeTemplate<EscTree>() {
    override fun at(pos: Position): TreeTemplate<EscTree> =
        EscTemplate(pos, children)

    override val spannedPosition: Position?
        get() = spannedPositionOf(children)
}

private class EscTemplate(
    pos: Position,
    val children: List<UnpositionedTreeTemplate<*>>,
) : TreeTemplate<EscTree>(pos) {
    override val typeInferences get() = null

    override fun toTree(document: Document): EscTree =
        EscTree(document, pos, buildTreeList(document, pos, children))
}

private class UnpositionedFunTemplate(
    val type: StaticType?,
    val children: List<UnpositionedTreeTemplate<*>>,
) : UnpositionedTreeTemplate<FunTree>() {
    override fun at(pos: Position): TreeTemplate<FunTree> =
        FunTemplate(pos, type, children)

    override val spannedPosition: Position?
        get() = spannedPositionOf(children)
}

private class FunTemplate(
    pos: Position,
    override val typeInferences: BasicTypeInferences?,
    val children: List<UnpositionedTreeTemplate<*>>,
) : TreeTemplate<FunTree>(pos) {
    constructor(pos: Position, type: StaticType?, children: List<UnpositionedTreeTemplate<*>>) :
        this(pos, type?.let { BasicTypeInferences(it, listOf()) }, children)

    override fun toTree(document: Document): FunTree {
        val tree = FunTree(document, pos, buildTreeList(document, pos, children))
        tree.typeInferences = typeInferences
        return tree
    }
}

private class UnpositionedLeftNameLeafTemplate(
    val type: StaticType?,
    val name: TemperName,
) : UnpositionedTreeTemplate<LeftNameLeaf>() {
    override fun at(pos: Position): TreeTemplate<LeftNameLeaf> =
        LeftNameLeafTemplate(pos, type, name)

    override val spannedPosition: Position?
        get() = null
}

private class LeftNameLeafTemplate(
    pos: Position,
    override val typeInferences: BasicTypeInferences?,
    val name: TemperName,
) : TreeTemplate<LeftNameLeaf>(pos) {
    constructor(pos: Position, type: StaticType?, name: TemperName) :
        this(pos, type?.let { BasicTypeInferences(it, listOf()) }, name)

    override fun toTree(document: Document): LeftNameLeaf {
        val tree = LeftNameLeaf(document, pos, name)
        tree.typeInferences = typeInferences
        return tree
    }
}

private class UnpositionedRightNameLeafTemplate(
    val type: StaticType?,
    val name: TemperName,
) : UnpositionedTreeTemplate<RightNameLeaf>() {
    override fun at(pos: Position): TreeTemplate<RightNameLeaf> =
        RightNameLeafTemplate(pos, type, name)

    override val spannedPosition: Position?
        get() = null
}

private class RightNameLeafTemplate(
    pos: Position,
    override val typeInferences: BasicTypeInferences?,
    val name: TemperName,
) : TreeTemplate<RightNameLeaf>(pos) {
    constructor(pos: Position, type: StaticType?, name: TemperName) :
        this(pos, type?.let { BasicTypeInferences(it, listOf()) }, name)

    override fun toTree(document: Document): RightNameLeaf {
        val tree = RightNameLeaf(document, pos, name)
        tree.typeInferences = typeInferences
        return tree
    }
}

private object UnpositionedStayLeafTemplate : UnpositionedTreeTemplate<StayLeaf>() {
    override fun at(pos: Position): TreeTemplate<StayLeaf> = StayLeafTemplate(pos)

    override val spannedPosition: Position?
        get() = null
}

private class StayLeafTemplate(
    pos: Position,
) : TreeTemplate<StayLeaf>(pos) {
    override val typeInferences get() = null
    override fun toTree(document: Document): StayLeaf = StayLeaf(document, pos)
}

private class UnpositionedValueLeafTemplate(
    val type: StaticType?,
    val value: Value<*>,
) : UnpositionedTreeTemplate<ValueLeaf>() {
    override fun at(pos: Position) = ValueLeafTemplate(pos, type, value)

    override val spannedPosition: Position?
        get() = null
}

private class ValueLeafTemplate(
    pos: Position,
    override val typeInferences: BasicTypeInferences?,
    val value: Value<*>,
) : TreeTemplate<ValueLeaf>(pos) {
    constructor(pos: Position, type: StaticType?, value: Value<*>) :
        this(pos, type?.let { BasicTypeInferences(it, listOf()) }, value)

    override fun toTree(document: Document): ValueLeaf {
        val tree = ValueLeaf(document, pos, value)
        tree.typeInferences = typeInferences
        return tree
    }
}

private class SingleUseTreeWrapper<TREE : Tree>(
    val tree: TREE,
) : TreeTemplate<TREE>(tree.pos) {
    override val typeInferences get() = null

    override fun toTree(document: Document): TREE = tree
}

private class SingleUseEdgeWrapper(
    val edge: TEdge,
) : TreeTemplate<Tree>(edge.target.pos) {
    override val typeInferences get() = null

    override fun toTree(document: Document) = edge.target
}
