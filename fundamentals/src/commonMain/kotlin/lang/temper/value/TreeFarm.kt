package lang.temper.value

import lang.temper.common.LeftOrRight
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.spanningPosition
import lang.temper.name.NameMaker
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes

typealias FlowMaker = (BlockTree) -> BlockFlow

/**
 * May be invoked with a block-lambda to allow specifying a tree using a simple DSL like
 *
 *      x.treeFarm.grow(pos) { // grow produces a single tree
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
     * Grow a list of trees.
     * The block body may include zero or more tree-constructing function calls.
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
    val nameMaker: NameMaker,
) {
    /**
     * Called for each created template with the template.
     */
    protected abstract fun <TREE : Tree, TT : UnpositionedTreeTemplate<TREE>>
    planted(t: TT): TT

    abstract val numPlanted: Int

    fun Block(
        flowMaker: FlowMaker? = null,
        type: StaticType? = null,
        children: (BlockPlanting).() -> Any?,
    ): UnpositionedTreeTemplate<BlockTree> {
        val planting = BlockPlanting(nameMaker)
        planting.children()
        return planted(UnpositionedBlockTemplate(flowMaker, type, planting.treeInnardsList))
    }

    /** Builds a block, "spanning", inferring position metadata from the children. */
    fun BlockS(
        flowMaker: FlowMaker? = null,
        type: StaticType? = null,
        children: (BlockPlanting).() -> Any?,
    ): TreeTemplate<BlockTree> {
        val planting = BlockPlanting(nameMaker)
        planting.children()
        return planted(
            BlockTemplate(
                planting.spannedPosition!!, // User is responsible for making sure somebody has a position.
                flowMaker,
                type,
                planting.treeInnardsList,
            ),
        )
    }

    fun Block(
        pos: Position,
        flowMaker: FlowMaker? = null,
        type: StaticType? = null,
        children: (Planting).() -> Any?,
    ): TreeTemplate<BlockTree> {
        val planting = BlockPlanting(nameMaker)
        planting.children()
        return planted(BlockTemplate(pos, flowMaker, type, planting.treeInnardsList))
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

    /** ln is shorthand for "left name": a name used in the assignment/write position. */
    fun Ln(name: TemperName, type: StaticType? = null): UnpositionedTreeTemplate<LeftNameLeaf> =
        planted(UnpositionedLeftNameLeafTemplate(type, name))

    fun Ln(pos: Position, name: TemperName, type: StaticType? = null): TreeTemplate<LeftNameLeaf> =
        planted(LeftNameLeafTemplate(pos, type, name))

    fun Ln(type: StaticType? = null, makeName: (NameMaker) -> TemperName) =
        Ln(makeName.invoke(nameMaker), type)

    fun Ln(pos: Position, type: StaticType? = null, makeName: (NameMaker) -> TemperName) =
        Ln(pos, makeName.invoke(nameMaker), type)

    /** rn is shorthand for "right name": a name used in read position. */
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

    // Control flow builders create a new block and then delegate to the BlockPlanting.
    // This solves problems like
    // Block {  <-- outer BlockPlanting
    //     Call(callee) {  <-- inner Planting
    //         OrElse(
    //             or = {  <-- needs a BlockPlanting
    //                 ...
    //             }, ...
    //         )
    //     }
    // }
    // There, the `or` needs to slot its output in a way that matches the
    // developer's intuition based on bracket structure.
    // This is solved by just having these default implementations delegate
    // to BlockPlanting by creating a block.

    /** `If({ one_tree }, thn = { trees_and_flow }, els = { trees_and_flow })` */
    open fun If(
        cond: (Planting).() -> UnpositionedTreeTemplate<*>,
        thn: (BlockPlanting).() -> Unit,
        els: (BlockPlanting).() -> Unit,
    ) {
        Block {
            If(cond, thn, els)
        }
    }

    /** `If(pos, { one_tree }, thn = { trees_and_flow }, els = { trees_and_flow })` */
    open fun If(
        pos: Position?,
        cond: (Planting).() -> UnpositionedTreeTemplate<*>,
        thn: (BlockPlanting).() -> Unit,
        els: (BlockPlanting).() -> Unit,
    ) {
        Block {
            If(pos = pos, cond = cond, thn = thn, els = els)
        }
    }

    /** `OrElse(or = { trees_and_flow }, els = { trees_and_flow })` */
    open fun OrElse(
        or: (BlockPlanting).() -> Unit,
        els: (BlockPlanting).() -> Unit,
        label: JumpLabel? = null,
    ) {
        Block {
            OrElse(or, els, label)
        }
    }

    /** `OrElse(pos, or = { trees_and_flow }, els = { trees_and_flow })` */
    open fun OrElse(
        pos: Position?,
        or: (BlockPlanting).() -> Unit,
        els: (BlockPlanting).() -> Unit,
        label: JumpLabel? = null,
    ) {
        Block {
            OrElse(pos, or, els, label)
        }
    }

    /** `While({ cond }) { body }` */
    open fun While(
        cond: (Planting).() -> UnpositionedTreeTemplate<*>,
        label: JumpLabel? = null,
        testAt: LeftOrRight = LeftOrRight.Left,
        increment: (BlockPlanting).() -> Unit = {},
        body: (BlockPlanting).() -> Unit,
    ) {
        Block {
            While(cond, label, testAt, increment, body)
        }
    }

    /** `While(pos, { cond }) { body }` */
    open fun While(
        pos: Position?,
        cond: (Planting).() -> UnpositionedTreeTemplate<*>,
        label: JumpLabel? = null,
        testAt: LeftOrRight = LeftOrRight.Left,
        increment: (BlockPlanting).() -> Unit = {},
        body: (BlockPlanting).() -> Unit,
    ) {
        Block {
            While(pos, cond, label, testAt, increment, body)
        }
    }

    /**
     * Unlike [Block], doesn't create a new block tree but instead plants a single,
     * possibly labeled, [ControlFlow.StmtBlock] within a larger block tree.
     */
    open fun Do(
        pos: Position? = null,
        label: JumpLabel? = null,
        continueLabel: JumpLabel? = null,
        body: (BlockPlanting).() -> Unit,
    ) {
        Block {
            Do(pos, label, continueLabel, body)
        }
    }

    /** `Break(label)` */
    open fun Break(label: JumpLabel?) {
        Block {
            Break(label)
        }
    }

    /** `Break(pos, label)` */
    open fun Break(pos: Position? = null, label: JumpLabel? = null) {
        Block {
            Break(pos, label)
        }
    }

    /** `Continue(pos, label)` */
    open fun Continue(label: JumpLabel?) {
        Block {
            Continue(label)
        }
    }

    /** `Continue(pos, label)` */
    open fun Continue(pos: Position? = null, label: JumpLabel? = null) {
        Block {
            Continue(pos, label)
        }
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

/**
 * When planting a row of trees as children of another tree, collects them in a list.
 */
class BlockPlanting(
    nameMaker: NameMaker,
) : Planting(nameMaker) {
    private val treeInnards = mutableListOf<TreeInnard>()

    override fun <TREE : Tree, TT : UnpositionedTreeTemplate<TREE>>
    planted(t: TT): TT {
        treeInnards.add(t)
        numPlanted += 1
        return t
    }

    private fun plantMark(m: FlowMark) {
        treeInnards.add(m)
    }

    /** Only counts trees, not control flow marks */
    override var numPlanted: Int = 0
        private set

    internal val treeInnardsList get() = treeInnards.toList()

    internal val spannedPosition: Position? get() = spannedPositionOf(treeInnards)

    /** `If({ one_tree }, thn = { trees_and_flow }, els = { trees_and_flow })` */
    override fun If(
        cond: (Planting).() -> UnpositionedTreeTemplate<*>,
        thn: (BlockPlanting).() -> Unit,
        els: (BlockPlanting).() -> Unit,
    ) = If(null, cond = cond, thn = thn, els = els)

    /** `If(pos, { one_tree }, thn = { trees_and_flow }, els = { trees_and_flow })` */
    override fun If(
        pos: Position?,
        cond: (Planting).() -> UnpositionedTreeTemplate<*>,
        thn: (BlockPlanting).() -> Unit,
        els: (BlockPlanting).() -> Unit,
    ) {
        plantMark(FlowMark(pos?.leftEdge, FlowMarkKind.StartIf))
        plantCond(cond)
        thn()
        plantMark(FlowMark(null, FlowMarkKind.Else))
        els()
        plantMark(FlowMark(pos?.rightEdge, FlowMarkKind.End))
    }

    /** `OrElse(or = { trees_and_flow }, els = { trees_and_flow })` */
    override fun OrElse(
        or: (BlockPlanting).() -> Unit,
        els: (BlockPlanting).() -> Unit,
        label: JumpLabel?,
    ) = OrElse(null, or = or, els = els, label = label)

    /** `OrElse(pos, or = { trees_and_flow }, els = { trees_and_flow })` */
    override fun OrElse(
        pos: Position?,
        or: (BlockPlanting).() -> Unit,
        els: (BlockPlanting).() -> Unit,
        label: JumpLabel?,
    ) {
        plantMark(FlowMark(pos?.leftEdge, FlowMarkKind.StartOrElse))
        plantLabel(pos?.leftEdge, label)
        or()
        plantMark(FlowMark(null, FlowMarkKind.Else))
        els()
        plantMark(FlowMark(pos?.rightEdge, FlowMarkKind.End))
    }

    /** `While({ cond }) { body }` */
    override fun While(
        cond: (Planting).() -> UnpositionedTreeTemplate<*>,
        label: JumpLabel?,
        testAt: LeftOrRight,
        increment: (BlockPlanting).() -> Unit,
        body: (BlockPlanting).() -> Unit,
    ) = While(
        null,
        cond = cond,
        label = label,
        testAt = testAt,
        increment = increment,
        body = body,
    )

    /** `While(pos, { cond }) { body }` */
    override fun While(
        pos: Position?,
        cond: (Planting).() -> UnpositionedTreeTemplate<*>,
        label: JumpLabel?,
        testAt: LeftOrRight,
        increment: (BlockPlanting).() -> Unit,
        body: (BlockPlanting).() -> Unit,
    ) {
        val start = when (testAt) {
            LeftOrRight.Left -> FlowMarkKind.StartWhile
            LeftOrRight.Right -> FlowMarkKind.StartDoWhile
        }
        plantMark(FlowMark(pos?.leftEdge, start))
        plantLabel(pos?.leftEdge, label)
        plantCond(cond)
        body()
        plantMark(FlowMark(pos?.rightEdge, FlowMarkKind.Else))
        increment()
        plantMark(FlowMark(pos?.rightEdge, FlowMarkKind.End))
    }

    /**
     * Unlike [Block], doesn't create a new block tree but instead plants a single,
     * possibly labeled, [ControlFlow.StmtBlock] within a larger block tree.
     */
    override fun Do(pos: Position?, label: JumpLabel?, continueLabel: JumpLabel?, body: (BlockPlanting).() -> Unit) {
        plantMark(FlowMark(pos?.leftEdge, FlowMarkKind.StartBlock))
        plantLabel(pos?.leftEdge, label)
        plantLabel(pos?.leftEdge, continueLabel, vContinueSymbol)
        body()
        plantMark(FlowMark(pos?.rightEdge, FlowMarkKind.End))
    }

    /** `Break(label)` */
    override fun Break(label: JumpLabel?) = Break(null, label)

    /** `Break(pos, label)` */
    override fun Break(pos: Position?, label: JumpLabel?) {
        plantMark(FlowMark(pos?.leftEdge, FlowMarkKind.StartBreak))
        plantLabel(pos?.leftEdge, label)
        plantMark(FlowMark(pos?.rightEdge, FlowMarkKind.End))
    }

    /** `Continue(label)` */
    override fun Continue(label: JumpLabel?) = Continue(null, label)

    /** `Continue(pos, label)` */
    override fun Continue(pos: Position?, label: JumpLabel?) {
        plantMark(FlowMark(pos?.leftEdge, FlowMarkKind.StartContinue))
        plantLabel(pos?.leftEdge, label)
        plantMark(FlowMark(pos?.rightEdge, FlowMarkKind.End))
    }

    /** Plants a pair consumable by [labelFor] */
    private fun plantLabel(labelPos: Position?, label: JumpLabel?, labelSymbol: Value<Symbol> = vLabelSymbol) {
        if (label != null) {
            if (labelPos != null) {
                V(labelPos.leftEdge, labelSymbol)
                Ln(labelPos, label)
            } else {
                V(labelSymbol)
                Ln(label)
            }
        }
    }

    private fun plantCond(cond: Planting.() -> UnpositionedTreeTemplate<*>) {
        val nBeforeCond = numPlanted
        cond()
        val nCond = numPlanted - nBeforeCond
        // Because of cond's receiver and return types, callers would have to
        // go out of their way to violate this condition, but downstream control-flow
        // mark processing code depends on it, and it's cheap to double-check here.
        check(nCond == 1) { "Expected one tree for condition, got $nCond" }
    }
}

private fun spannedPositionOf(templates: Iterable<TreeInnard>): Position? {
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

private sealed interface InnardInProgress
private data class CompletedInnard(
    val innard: TreeInnard,
) : InnardInProgress
private data class LeftKnown(
    val left: Position,
    val innard: TreeInnard,
) : InnardInProgress

private fun buildTreeList(
    document: Document,
    pos: Position,
    templates: List<UnpositionedTreeTemplate<*>>,
): List<Tree> = buildTreeListAndFlowMaker(document, pos, templates, null).first

private fun buildTreeListAndFlowMaker(
    document: Document,
    pos: Position,
    treeInnards: List<TreeInnard>,
    flowMaker: FlowMaker?,
): Pair<List<Tree>, FlowMaker?> {
    val out = mutableListOf<InnardInProgress>()

    var left = pos.leftEdge
    var nIncomplete = 0
    var hasStructuredFlow = false
    for (treeInnard in treeInnards) {
        when (treeInnard) {
            is TreeTemplate<*> -> {
                val pos = treeInnard.pos
                out.add(CompletedInnard(treeInnard))
                left = pos.rightEdge
            }

            is FlowMark if treeInnard.spannedPosition != null -> {
                hasStructuredFlow = true
                val pos = treeInnard.spannedPosition
                out.add(CompletedInnard(treeInnard))
                left = pos.rightEdge
            }

            else -> {
                if (treeInnard is FlowMark) {
                    hasStructuredFlow = true
                }
                out.add(LeftKnown(left, treeInnard))
                nIncomplete += 1
            }
        }
    }

    // Can't have conflicting sources of flow info
    check(!(hasStructuredFlow && flowMaker != null))

    if (nIncomplete != 0) {
        var right = pos.rightEdge
        for (i in treeInnards.lastIndex downTo 0) {
            when (val inProgress = out[i]) {
                is LeftKnown -> {
                    val pos = listOf(inProgress.left, right).spanningPosition(inProgress.left)
                    out[i] = CompletedInnard(
                        when (val innard = inProgress.innard) {
                            is FlowMark -> innard.copy(spannedPosition = pos)
                            is UnpositionedTreeTemplate<*> -> innard.orAt(pos)
                        },
                    )
                    right = inProgress.left
                    if (--nIncomplete == 0) {
                        break
                    }
                }
                is CompletedInnard -> {
                    right = inProgress.innard.spannedPosition!!
                }
            }
        }
    }

    return if (hasStructuredFlow) {
        val flowStack = mutableListOf<ControlFlowInProgress>(
            BlockInProgress(pos),
        )
        val children = mutableListOf<Tree>()
        for (ci in out) {
            val innard = (ci as CompletedInnard).innard
            val innardPos = innard.spannedPosition!!
            when (innard) {
                is UnpositionedTreeTemplate<*> -> {
                    val tree = (innard as TreeTemplate<*>).toTree(document)
                    children.add(tree)
                    flowStack.last().add(BlockChildReference(children.lastIndex, innardPos))
                }
                is FlowMark -> when (innard.flowMarkKind) {
                    FlowMarkKind.StartBlock -> flowStack.add(BlockInProgress(innardPos))
                    FlowMarkKind.StartBreak -> flowStack.add(JumpInProgress(innardPos, BreakOrContinue.Break))
                    FlowMarkKind.StartContinue -> flowStack.add(JumpInProgress(innardPos, BreakOrContinue.Continue))
                    FlowMarkKind.StartIf -> flowStack.add(IfInProgress(innardPos))
                    FlowMarkKind.StartWhile -> flowStack.add(LoopInProgress(innardPos, LeftOrRight.Left))
                    FlowMarkKind.StartDoWhile -> flowStack.add(LoopInProgress(innardPos, LeftOrRight.Right))
                    FlowMarkKind.StartOrElse -> flowStack.add(OrElseInProgress(innardPos))
                    FlowMarkKind.Else -> (flowStack.last() as ElsyControlFlowInProgress).inElse = true
                    FlowMarkKind.End -> {
                        val done = flowStack.removeLast()
                        flowStack.last().add(done.complete(innardPos, children, document))
                    }
                }
            }
        }
        val flowInProgress = flowStack.removeLast()
        check(flowStack.isEmpty())
        val trees = children.toList()
        val flow =
            ControlFlow.StmtBlock.wrap(flowInProgress.complete(pos.rightEdge, trees, document))
        trees to { StructuredFlow(flow) }
    } else {
        out.map {
            ((it as CompletedInnard).innard as TreeTemplate<*>).toTree(document)
        } to flowMaker
    }
}

private sealed class ControlFlowInProgress(val left: Position) {
    open fun add(ref: BlockChildReference) = add(ControlFlow.Stmt(ref))
    abstract fun add(cf: ControlFlow)
    abstract fun complete(right: Position, trees: List<Tree>, doc: Document): ControlFlow
}

private sealed class ElsyControlFlowInProgress(left: Position) : ControlFlowInProgress(left) {
    var inElse = false
}

private class BlockInProgress(left: Position) : ControlFlowInProgress(left) {
    val stmts = mutableListOf<ControlFlow>()

    override fun add(cf: ControlFlow) {
        stmts.add(cf)
    }

    override fun complete(right: Position, trees: List<Tree>, doc: Document): ControlFlow {
        val (label, i) = labelFor(0, stmts, trees, vLabelSymbol)
        val (continueLabel, j) = labelFor(i, stmts, trees, vContinueSymbol)

        val pos = listOf(left, right).spanningPosition(left)
        val block = ControlFlow.StmtBlock(
            pos,
            stmts.subList(j, stmts.size).toList(),
        )

        return if (label != null) {
            ControlFlow.Labeled(pos, label, continueLabel, block)
        } else {
            block
        }
    }
}

private fun completeStmtBlock(ls: MutableList<ControlFlow>, posHint: Position): ControlFlow.StmtBlock {
    return when (ls.size) {
        0 -> ControlFlow.StmtBlock(posHint, listOf())
        1 -> ControlFlow.StmtBlock.wrap(ls[0])
        else -> ControlFlow.StmtBlock(ls.spanningPosition(ls[0].pos), ls.toList())
    }
}

private class IfInProgress(left: Position) : ElsyControlFlowInProgress(left) {
    private var condition: BlockChildReference? = null
    private var thenClause = mutableListOf<ControlFlow>()
    private var elseClause = mutableListOf<ControlFlow>()

    override fun add(ref: BlockChildReference) {
        if (condition == null && !inElse) {
            condition = ref
        } else {
            super.add(ref)
        }
    }

    override fun add(cf: ControlFlow) {
        if (inElse) {
            elseClause.add(cf)
        } else {
            thenClause.add(cf)
        }
    }

    override fun complete(right: Position, trees: List<Tree>, doc: Document) =
        ControlFlow.If(
            listOf(left, right).spanningPosition(left),
            condition!!,
            completeStmtBlock(thenClause, condition!!.pos.rightEdge),
            completeStmtBlock(elseClause, right),
        )
}

private class JumpInProgress(left: Position, val kind: BreakOrContinue) : ControlFlowInProgress(left) {
    private var parts = mutableListOf<ControlFlow>()

    override fun add(cf: ControlFlow) {
        parts.add(cf)
    }

    override fun complete(right: Position, trees: List<Tree>, doc: Document): ControlFlow.Jump {
        val (label, i) = labelFor(0, parts, trees, vLabelSymbol)
        if (i != parts.size) {
            check(parts.isEmpty()) { "Malformed break or continue: $parts" }
        }
        val jumpSpec = if (label == null) {
            DefaultJumpSpecifier
        } else {
            NamedJumpSpecifier(label)
        }
        val pos = listOf(left, right).spanningPosition(left)
        return when (kind) {
            BreakOrContinue.Break -> ControlFlow.Break(pos, jumpSpec)
            BreakOrContinue.Continue -> ControlFlow.Continue(pos, jumpSpec)
        }
    }
}

private fun childFor(cf: ControlFlow?, trees: List<Tree>): Tree? {
    val index = (cf as? ControlFlow.Stmt)?.ref?.index ?: return null
    return trees.getOrNull(index)
}

private fun symbolFor(cf: ControlFlow?, trees: List<Tree>): Symbol? =
    childFor(cf, trees)?.symbolContained

/**
 * If there are statements like `\label name` at the start,
 * returns the jump label named and the index in [cfs] after those.
 *
 * Otherwise return `Pair(null, 0)` to indicate the absence.
 *
 * Either way, if there is a list of statements, the second part is the part
 * to process after any optional label.
 */
private fun labelFor(
    offset: Int,
    cfs: List<ControlFlow>,
    trees: List<Tree>,
    symbol: Value<Symbol>,
): Pair<JumpLabel?, Int> {
    if (cfs.size >= 2 + offset) {
        val part0 = cfs[offset]
        val part1 = cfs[offset + 1]
        if (symbolFor(part0, trees) == symbol.stateVector) {
            return ((childFor(part1, trees) as NameLeaf).content as JumpLabel) to offset + 2
        }
    }
    return null to offset
}

private class OrElseInProgress(left: Position) : ElsyControlFlowInProgress(left) {
    private var orClause = mutableListOf<ControlFlow>()
    private var elseClause = mutableListOf<ControlFlow>()

    override fun add(cf: ControlFlow) {
        if (inElse) {
            elseClause.add(cf)
        } else {
            orClause.add(cf)
        }
    }

    override fun complete(right: Position, trees: List<Tree>, doc: Document): ControlFlow.OrElse {
        var orClauseParts = orClause
        var (label, afterLabelIndex) = labelFor(0, orClause, trees, vLabelSymbol)
        orClauseParts = orClauseParts.subList(afterLabelIndex, orClauseParts.size)
        if (label == null) {
            label = doc.nameMaker.unusedTemporaryName("orElse")
        }
        val pos = listOf(left, right).spanningPosition(left)
        val orBlock = completeStmtBlock(orClauseParts, left)
        val orBlockLabeled = ControlFlow.Labeled(
            orBlock.pos,
            label,
            null,
            orBlock,
        )
        val elseBlock = completeStmtBlock(elseClause, right)

        return ControlFlow.OrElse(pos, orBlockLabeled, elseBlock)
    }
}

private class LoopInProgress(left: Position, val testAt: LeftOrRight) : ElsyControlFlowInProgress(left) {
    private var parts = mutableListOf<ControlFlow>()
    private var incrementParts = mutableListOf<ControlFlow>()

    override fun add(cf: ControlFlow) {
        val partsList = if (inElse) { incrementParts } else { parts }
        partsList.add(cf)
    }

    override fun complete(right: Position, trees: List<Tree>, doc: Document): ControlFlow.Loop {
        val (label, afterLabel) = labelFor(0, parts, trees, vLabelSymbol)
        var i = afterLabel
        val condition = parts.getOrNull(i) as? ControlFlow.Stmt
            ?: error("No condition in $parts at $i")
        i += 1
        val body = completeStmtBlock(parts.subList(i, parts.size), right)
        val increment = completeStmtBlock(incrementParts, condition.pos.rightEdge)
        return ControlFlow.Loop(
            pos = listOf(left, right).spanningPosition(left),
            label = label,
            condition = condition.ref,
            checkPosition = testAt,
            body = body,
            increment = increment,
        )
    }
}

/**
 * Specifies part of the content of an [InnerTree].
 * For most inner trees, this is just part of the [InnerTree.children], but
 * [BlockTree]s also have a [BlockTree.flow].
 */
sealed interface TreeInnard {
    val spannedPosition: Position?
}

/**
 * Something from which, given a position, we can derive a tree.
 */
sealed class UnpositionedTreeTemplate<TREE : Tree> : TreeInnard {
    fun toTree(document: Document, pos: Position): TREE = at(pos).toTree(document)
    abstract infix fun at(pos: Position): TreeTemplate<TREE>
    fun orAt(pos: Position): TreeTemplate<TREE> = when (this) {
        is TreeTemplate -> this
        else -> this.at(pos)
    }
}

private enum class FlowMarkKind {
    StartBlock,
    StartBreak,
    StartContinue,
    StartIf,
    StartOrElse,
    StartWhile,
    StartDoWhile,

    /** `else` in `if` and `orelse` but also the increment clause in loops. */
    Else,
    End,
}

private data class FlowMark(
    override val spannedPosition: Position?,
    val flowMarkKind: FlowMarkKind,
) : TreeInnard

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
    val flowMaker: FlowMaker?,
    val type: StaticType?,
    val children: List<TreeInnard>,
) : UnpositionedTreeTemplate<BlockTree>() {
    override fun at(pos: Position): TreeTemplate<BlockTree> =
        BlockTemplate(pos, flowMaker, type, children)

    override val spannedPosition: Position?
        get() = spannedPositionOf(children)
}

private class BlockTemplate(
    pos: Position,
    val flowMaker: FlowMaker? = null,
    val type: StaticType?,
    val children: List<TreeInnard>,
) : TreeTemplate<BlockTree>(pos) {
    override val typeInferences
        get() = type?.let { BasicTypeInferences(it, listOf()) }

    override fun toTree(document: Document): BlockTree {
        val (childList: List<Tree>, flowMaker: FlowMaker?) =
            buildTreeListAndFlowMaker(document, pos, children, flowMaker)
        val block = BlockTree(document, pos, childList, LinearFlow)
        if (flowMaker != null) {
            block.flow = flowMaker.invoke(block)
        }
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

private val vContinueSymbol = Value(Symbol("__continue__"))
