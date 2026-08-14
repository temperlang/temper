package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.AwaitFn
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.YieldFn
import lang.temper.builtin.isRttiCall
import lang.temper.common.compatReversed
import lang.temper.common.console
import lang.temper.frontend.syntax.isAssignment
import lang.temper.frontend.syntax.isLeftHandSide
import lang.temper.frontend.typestage.simplifyRttiCall
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.CoreCodeLocation
import lang.temper.name.InternalModularName
import lang.temper.name.ResolvedName
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.DotHelper
import lang.temper.type.ExternalSet
import lang.temper.type.InternalSet
import lang.temper.type.TypeContext
import lang.temper.type.WellKnownTypes
import lang.temper.type2.TypeContext2
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.InnerTree
import lang.temper.value.JumpDestination
import lang.temper.value.JumpLabel
import lang.temper.value.JumpSpecifier
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.NameLeaf
import lang.temper.value.NamedJumpSpecifier
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.StructuredFlow
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.ValueLeaf
import lang.temper.value.fnParsedName
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.isBubbleCall
import lang.temper.value.matches
import lang.temper.value.returnsVoidClearly
import lang.temper.value.toLispy
import lang.temper.value.toPseudoCode
import lang.temper.value.varSymbol
import lang.temper.value.void

private const val DEBUG = false

private inline fun debug(p: Positioned, f: () -> Any) {
    @Suppress("SimplifyBooleanWithConstants")
    if (DEBUG && p.pos.loc != CoreCodeLocation) {
        val x = f()
        if (x != Unit) {
            console.log("$x")
        }
    }
}

/**
 * Makes sure all [BlockTree]s have [StructuredFlow]s and that there is one block per
 * module/function body by stitching [ControlFlow]s together.  This stitching allows matching
 * [JumpSpecifier]s with [JumpDestination]s.
 *
 * Many languages that we want to translate Temper into make a hard distinction between statements
 * and expressions; structured programming statements (`if`, `while`) control which expressions
 * evaluate.  Statements can contain expressions but not vice versa, except where lambdas are
 * expressions and their body is a statement.
 *
 * [Weaver] is responsible for converting trees of expressions, suitable for interpretation, into
 * a form that can be decompiled to something with the hierarchy of statements and expressions that
 * many language backends need.
 *
 * Some things that are expressions in Temper can only appear in statement position in other
 * languages including:
 *
 * - Local variable declarations.
 * - Assignments `a = b` in Python (before PEP 0572) and in Golang.
 * - `yield`, `return` in C-like languages
 * - Error originating operators like `throw` or Temper `bubble()`.
 * - Error checks or interceptions like `catch` in Java-like languages.
 * - Some function expressions because some languages only allow constrained functions in
 *   expression position, for example, Python lambdas cannot contain statements.
 *
 * These also need to be woven as close as possible to the containing module/function body.
 *
 * [![](Weaver.png)](Weaver.png)
 */
class Weaver private constructor(
    private val root: BlockTree,
    /** Whether to run [MagicSecurityDust] */
    private val sprinkleSecurityDust: Boolean,
    /**
     * Whether to split RTTI calls like `as` and `is` into separate `null`
     * checks and type checks against non-null values.
     */
    private val simplifyRttiCalls: Boolean,
    /**
     * Whether to pull special functions like assignments towards the root.
     */
    private val pullSpecialsRootward: Boolean,
    /**
     * Whether to pull free function trees into declarations.
     */
    private val nameAllFunctions: Boolean,
    /**
     * True if [lang.temper.frontend.typestage.MakeResultsExplicit] has run.
     * False if we need to capture the block result implicitly so that it can
     * later use it as the return value.
     */
    private val resultsAlreadyCaptured: Boolean,
    /** All names in the module that might be reassigned within their declaration's live range'. */
    private val varNames: Set<ResolvedName>,
) {
    private val typeContext = TypeContext()
    private val typeContext2 = TypeContext2()
    private var blockResultCaptures: Map<BlockTree, CaptureResult> = mapOf()

    private inline fun debug(f: () -> Any) = debug(root, f)

    private fun weave() {
        debug {
            console.group("Weave") {
                console.log(root.toLispy(multiline = true))
            }
        }
        simplifyBlocks(root)
        debug {
            console.group("Blocks simplified") {
                console.log(root.toLispy(multiline = true))
            }
        }
        sprinkleSecurityDust(root)
        debug {
            console.group("Security sprinkled") {
                console.log(root.toLispy(multiline = true))
            }
        }
        simplifyRttiCalls(root)
        debug {
            console.group("RTTI simplified") {
                console.log(root.toLispy(multiline = true))
            }
        }
        addWeightToStatementLikeExpressions(root)
        debug {
            console.group("Statement like expressions weighted") {
                console.log(root.toLispy(multiline = true))
            }
        }
        captureBlockResultsInTemporaries(root)
        debug {
            console.group("Captured block results") {
                console.log(root.toLispy(multiline = true))
            }
        }
        pullRootwards(root)
        debug {
            console.group("After pullRootwards") {
                console.log(root.toLispy(multiline = true))
            }
        }
        evaporateBubbles()
        debug {
            console.group("After evaporateBubbles") {
                console.log(root.toLispy(multiline = true))
            }
        }
    }

    /** Look for calls to [BuiltinFuns.bubble] and turn them into [ControlFlow.Break] where possible. */
    private fun evaporateBubbles() {
        fun chaseBubbles(cf: ControlFlow, containing: ControlFlow.OrElse?) {
            if (cf is ControlFlow.OrElse) {
                chaseBubbles(cf.orClause, cf)
                chaseBubbles(cf.elseClause, containing)
            } else if (cf is ControlFlow.Stmt) {
                val t = root.dereference(cf.ref)?.target
                if (t != null && isBubbleCall(t) && containing != null) {
                    val label = containing.orClause.breakLabel
                    val parent = cf.parent!!
                    val index = parent.stmts.indexOf(cf)
                    parent.withMutableStmtList { stmtList ->
                        stmtList[index] = ControlFlow.Break(cf.pos, NamedJumpSpecifier(label))
                    }
                }
            } else {
                for (clause in cf.clauses) {
                    chaseBubbles(clause, containing)
                }
            }
        }
        chaseBubbles(structureBlock(root).controlFlow, null)
    }

    private fun captureBlockResultsInTemporaries(tree: BlockTree) {
        val capturer = CaptureBlockResultsInTemporaries(
            tree, typeContext, varNames,
            resultsAlreadyCaptured = resultsAlreadyCaptured,
        )
        capturer.capture()
        blockResultCaptures = capturer.capturedBlocks
    }

    /** Walk post-order to pull blocks as close to the root as possible. */
    private fun pullRootwards(tree: Tree) {
        var i = 0
        // This loop samples size every time and captures `nextEdge` early so that it's not sensitive
        // to nested calls modifying the child count.  When stitching pulled blocks into a block,
        // we end up adding children.
        while (i < tree.size) {
            val nextEdge = tree.edgeOrNull(i + 1)
            if (ithChildUnderSameRoot(tree, i)) {
                pullRootwards(tree.child(i))
            }
            i = if (i < tree.size && tree.edgeOrNull(i + 1) === nextEdge) {
                i + 1
            } else if (nextEdge != null) {
                val ip = tree.edges.indexOf(nextEdge)
                require(ip >= 0) // Pulling may add/reuse edges but must not remove adjacent.
                ip
            } else {
                break
            }
        }
        if (tree is BlockTree) {
            structureBlock(tree)
            if (canMoveRootwards(tree)) {
                val treeEdge = tree.incoming!! // !! safe since `tree != root`
                val parent = treeEdge.source!!
                val indexInParent = treeEdge.edgeIndex

                val result = this.blockResultCaptures[tree]
                    ?: CaptureResult.voidCaptureResult

                treeEdge.replace(
                    when (result) {
                        is NameCaptureResult -> RightNameLeaf(tree.document, tree.pos, result.capturedIn)
                        is KnownValueCaptureResult -> ValueLeaf(tree.document, tree.pos, result.value)
                    }.also {
                        val type = result.type
                        if (type != null) {
                            it.typeInferences = BasicTypeInferences(type, listOf())
                        }
                    },
                )

                pullRootwardsBeforeIndex(
                    pulledBlock = tree,
                    tree = parent,
                    indexInTree = indexInParent,
                )
            }
        }
    }

    private fun canMoveRootwards(block: BlockTree): Boolean {
        if (block == root) {
            return false
        }

        val incoming = block.incoming
        val parent = incoming?.source ?: return false
        if (parent is FunTree && parent.edge(parent.size - 1) == incoming) {
            // Do not move a block that is the function's body out of the function.
            return false
        }
        if (isLeftHandSide(block)) {
            // We can't move it without violating tree structure contracts.
            // TODO: explain
            // We can't store a name in a temporary that then gets assigned, so do nothing
            // for now.
            // If we had a dereference operator ("unhole")? maybe we could.
            return false
        }
        return true
    }

    private fun pullRootwardsBeforeIndex(
        pulledBlock: BlockTree,
        tree: InnerTree,
        indexInTree: Int,
    ) {
        when (tree) {
            is BlockTree -> {
                // Stitch the child into its parent by finding any BlockChildReference to child
                // and connecting the child subsystem to edges around it.
                // Then we're done.  Since we traverse postOrder, parent will naturally be pulled
                // rootwards where appropriate.
                stitchControlFlowsTogether2(
                    outerBlock = tree,
                    pulledBlock = pulledBlock,
                    edgeIndex = indexInTree,
                )
            }
            is CallTree, is DeclTree, is FunTree -> {
                // For calls, regardless of whether it's the callee or an actual parameter,
                // the call normally happens after the child completes.

                // For declarations, we check above in canMoveRootwards that the block does not
                // specify the name declared.

                // For function definitions, we checked above in canMoveRootwards that we're
                // not pulling the body out.

                swapBeforeAndKeepSneaking(tree = tree, pulledBlock = pulledBlock)
            }
            is EscTree -> {
                // How did we even get here?
            }
        }
    }

    private fun swapBeforeAndKeepSneaking(
        tree: InnerTree,
        pulledBlock: BlockTree,
    ) {
        // Safe because tree is not a block so cannot be root.
        val treeEdge = tree.incoming!!
        val parent = treeEdge.source!!
        val treeIndexInParent = parent.edges.indexOf(treeEdge)

        pullRootwardsBeforeIndex(
            pulledBlock = pulledBlock,
            tree = parent,
            indexInTree = treeIndexInParent,
        )
    }

    private fun stitchControlFlowsTogether2(
        outerBlock: BlockTree,
        pulledBlock: BlockTree,
        edgeIndex: Int,
    ) {
        Stitcher(outerBlock = outerBlock, pulledBlock = pulledBlock, edgeIndex = edgeIndex)
            .stitch()
    }

    private val flowAnalyzer = FlowAnalyzer()

    /**
     * Collapse blocks with linear flows like
     *
     *     { void; void; x }
     *
     * to
     *
     *     x
     *
     * Hoisted declarations and macro calls often evaporate leaving `void` droppings.
     *
     * Not weaving these blocks unnecessarily reduces the number of temporaries,
     * and leaves reduced or reducible values in place.
     */
    private fun simplifyBlocks(container: Tree) {
        // Do simplification on children so that we do not try to eliminate the root
        for (i in container.indices) {
            if (!ithChildUnderSameRoot(container, i)) { continue }
            val edge = container.edge(i)
            val tree = edge.target
            simplifyBlocks(tree)
            if (tree is BlockTree && tree.flow is LinearFlow) {
                val startStatementIndex = tree.parts.startIndex
                var statementIndex = tree.size - 1
                while (statementIndex >= startStatementIndex) {
                    val statement = tree.child(statementIndex)
                    if (
                        (statement is ValueLeaf || statement.isNoopBlock) &&
                        !flowAnalyzer.mayBeResultOfLinearFlow(tree, statementIndex)
                    ) {
                        tree.removeChildren(statementIndex..statementIndex)
                    }
                    statementIndex -= 1
                }
                if (tree.size == 1) {
                    edge.replace(freeTarget(tree.edge(0)))
                }
            }
        }
    }

    private fun sprinkleSecurityDust(root: BlockTree) {
        if (!sprinkleSecurityDust) { return }
        val duster = MagicSecurityDust()
        duster.sprinkle(root)
    }

    private fun simplifyRttiCalls(root: BlockTree) {
        if (simplifyRttiCalls) {
            val rttiCalls = mutableListOf<CallTree>()
            TreeVisit.startingAt(root)
                .forEach {
                    if (it is FunTree) {
                        VisitCue.SkipOne
                    } else {
                        if (isRttiCall(it)) {
                            rttiCalls.add(it)
                        }
                        VisitCue.Continue
                    }
                }
                .visitPreOrder()
            for (rttiCall in rttiCalls) {
                simplifyRttiCall(rttiCall, typeContext2)
            }
        }
    }

    /**
     * Some expressions need to be children of the root or close to.
     *
     * For example, uses of `=` are statement-level in Go and Python; they cannot nest inside
     * more complex expressions.
     *
     * This call wraps those in blocks in various ways so that they get pulled rootwards by the
     * next weaver step.
     *
     * It converts `yield()` -> `{ yield(); void }` so that it gets pulled root-wards without any
     * value being captured in a temporary.
     * This has two effects:
     * - The *Interpreter*'s *interpretBlock* method can special-case calls to yield when figuring
     *   out how to proceed.
     * - *TmpLTranslator* may map `yield` operations to *TmpL.YieldStatement* statements easily.
     *
     * When [pullSpecialsRootward] is true, this method additionally wraps calls to some other
     * special functions in blocks.
     *
     * `a = b` -> `{ a = b }` so that assignments in the middle of expressions, including chained
     * assignments, are effectively in statement position.
     * This is necessary since some languages treat assignments not as expressions with
     * side-effects but as statements.
     * [Go](https://golang.org/ref/spec#Assignments)
     * [Python](https://docs.python.org/3/reference/simple_stmts.html#grammar-token-assignment-stmt)
     *
     * Some expressions are assignment-like.  They correspond to uses of `=` on backends that treat
     * assignments as statements.
     * - [`setp`][BuiltinFuns.setpFn] corresponds to `this.backedPropertyName = newValue` in some
     *   backends.
     * - Uses of property setters, similarly translate to property assignment syntax on some
     *   backends.
     *
     * When [nameAllFunctions] is true,
     * `fn { ... }` -> `{ t = fn { ... }; t }`
     * TODO: Get rid of this when we've implemented proper closure conversion.
     * Some backends only allow functions with names, and some only allow defining functions as part
     * of a function declaration.
     * This has the effect of making sure that all functions are associated with a name.
     */
    private fun addWeightToStatementLikeExpressions(root: Tree) {
        // Find everything that needs to sink rootwards.  Edges and a replacement maker.
        // Later we'll check whether they're already children of a block and wrap them.
        val heavies = mutableListOf<
            Pair<
                TEdge,
                (Planting).(Position) -> UnpositionedTreeTemplate<BlockTree>,
                >,
            >()
        TreeVisit.startingAt(root)
            .forEach forEachTree@{ tree ->
                val visitCue = if (tree is FunTree) {
                    // Don't descend across function boundaries.
                    VisitCue.SkipOne
                } else {
                    VisitCue.Continue
                }

                val edge = tree.incoming ?: return@forEachTree visitCue
                val parent = edge.source
                if (parent is BlockTree) { // tree is already in a block.
                    return@forEachTree visitCue
                }

                if (tree is FunTree && nameAllFunctions) {
                    // If the function definition is already part of an assignment, assume
                    // (unsoundly) that it's initializing a const declaration.
                    // TODO: Do closure conversion to solve this problem generally for backends.
                    // where we do not have first-class, anonymous functions.
                    // If not, introduce a temporary and weigh it down with a block.
                    if (isAssignment(parent) && edge.edgeIndex == 2) {
                        // It is ok where it is.
                    } else {
                        heavies.add(
                            edge to {
                                val fnName = nameMaker.unusedSourceName(fnParsedName)
                                Block(tree.pos) {
                                    Decl(tree.pos.leftEdge, fnName)
                                    Call(tree.pos, BuiltinFuns.vSetLocalFn) {
                                        Ln(tree.pos.leftEdge, fnName)
                                        Replant(freeTree(tree))
                                    }
                                    Rn(tree.pos.rightEdge, fnName)
                                }
                            },
                        )
                    }
                    return@forEachTree visitCue
                }

                // We've handled all the non-call cases above.
                if (tree !is CallTree) {
                    return@forEachTree visitCue
                }

                val callee = tree.childOrNull(0)
                when (val fn = callee?.functionContained) {
                    YieldFn -> heavies.add(
                        edge to {
                            Block {
                                Replant(freeTree(tree))
                                V(tree.pos.leftEdge, void) // Result of yield is not used
                            }
                        },
                    )
                    AwaitFn -> if (pullSpecialsRootward) {
                        // If it's in a simple assignment, then it'll sink to where it's needed.
                        val alreadySinking = when {
                            isAssignment(parent) ->
                                edge.edgeIndex == 2 && parent?.childOrNull(1) is LeftNameLeaf
                            else -> false
                        }
                        if (!alreadySinking) {
                            heavies.add(
                                edge to {
                                    Block {
                                        Replant(freeTree(tree))
                                    }
                                },
                            )
                        }
                    }
                    BuiltinFuns.setLocalFn,
                    BuiltinFuns.setpFn,
                    is DotHelper,
                    -> if (pullSpecialsRootward) {
                        val rightIndex = when (fn) {
                            is DotHelper -> when (val accessor = fn.memberAccessor) {
                                InternalSet, ExternalSet -> accessor.firstArgumentIndex + 2
                                else -> -1 // Not assignment-like
                            }
                            BuiltinFuns.setpFn -> SETP_RIGHT_INDEX
                            BuiltinFuns.setLocalFn -> SET_LOCAL_RIGHT_INDEX
                            else -> error("$fn") // fn matched above
                        }
                        if (rightIndex in tree.indices) {
                            var assignedTemporary: Temporary? = null
                            if (fn == BuiltinFuns.setLocalFn) {
                                val leftEdge = tree.edge(1)
                                val left = leftEdge.target
                                if (left is LeftNameLeaf) {
                                    assignedTemporary = left.content as? Temporary
                                }
                            }
                            if (assignedTemporary != null) {
                                //     t = right
                                // ->
                                //     { t = right; t }
                                heavies.add(
                                    edge to {
                                        Block {
                                            Replant(freeTree(tree))
                                            Rn(tree.pos.rightEdge, assignedTemporary)
                                        }
                                    },
                                )
                            } else {
                                //     left = right
                                // ->
                                //     { left = right }

                                // Eventually, CaptureBlockResultsInTemporaries will turn that into
                                // something like the below:
                                //     { let t = right; left = t; t }
                                heavies.add(
                                    edge to {
                                        Block {
                                            Replant(freeTree(tree))
                                        }
                                    },
                                )
                            }
                        }
                    }
                    else -> {
                        if (tree.returnsVoidClearly) {
                            heavies.add(
                                edge to {
                                    Block(tree.pos.rightEdge) {
                                        Replant(freeTree(tree))
                                        V(tree.pos.rightEdge.pos, void)
                                    }
                                },
                            )
                        }
                    }
                }

                visitCue
            }
            .visitPreOrder()

        // Process deeper replacements early to avoid trying to operate on something that has been
        // disconnected by a shallower replacement.
        for ((heavyEdge, replacementMaker) in heavies.asReversed()) {
            check(heavyEdge.source != null)
            heavyEdge.replace(replacementMaker)
        }
    }

    companion object {
        internal fun weave(
            root: BlockTree,
            sprinkleSecurityDust: Boolean,
            pullSpecialsRootward: Boolean,
            nameAllFunctions: Boolean,
            simplifyRttiCalls: Boolean,
            resultsAlreadyCaptured: Boolean = true,
        ) {
            val varNames = varNamesOf(root)
            debug(root) {
                console.log("Weaving")
                root.toPseudoCode(console.textOutput)
            }
            val allRoots = allRootsOfAsBlocks(root)
                // Process roots in reverse so that we deal with deeper functions first which
                // prevents ancestor mutations from copying edges, effectively orphaning roots that
                // have yet to be processed.
                .compatReversed()
            for (rootBlock in allRoots) {
                val rootEdge = rootBlock.incoming
                Weaver(
                    root = rootBlock,
                    sprinkleSecurityDust = sprinkleSecurityDust,
                    simplifyRttiCalls = simplifyRttiCalls,
                    pullSpecialsRootward = pullSpecialsRootward,
                    nameAllFunctions = nameAllFunctions,
                    resultsAlreadyCaptured = resultsAlreadyCaptured,
                    varNames = varNames,
                ).weave()
                require(
                    rootEdge == null ||
                        (rootEdge.target == rootBlock && rootEdge.source != null),
                )
            }

            debug(root) {
                console.log("Before trim loose threads")
                root.toPseudoCode(console.textOutput)
            }
            for (rootBlock in allRoots) {
                trimLooseThreads(rootBlock, resultsAlreadyCaptured = resultsAlreadyCaptured)
            }
            debug(root) {
                console.log("After trim loose threads")
                root.toPseudoCode(console.textOutput)
            }
        }

        /**
         * After we've stitched subsystems together, it's common for [BlockTree]s to have children
         * that are not referenced within their subsystem.
         *
         * This pass walks the tree again to identify and remove such garbage subtrees†.
         *
         * † - available as a band name.
         */
        private fun trimLooseThreads(tree: BlockTree, resultsAlreadyCaptured: Boolean) {
            val flow = structureBlock(tree)
            trimGarbageSubtrees(tree, flow)
            if (resultsAlreadyCaptured) {
                val stmts = flow.controlFlow
                // We often leave a reference to a result name at the end,
                // like `result__123 = ...; result__123`.
                // If the results have already been captured, that's redundant.
                val last = stmts.stmts.lastOrNull()
                if (last is ControlFlow.Stmt) {
                    val edge = tree.dereference(last.ref)
                    val name = edge?.target as? RightNameLeaf
                    if (name?.content is InternalModularName) {
                        edge.replace {
                            V(name.pos, void, WellKnownTypes.voidType)
                        }
                    }
                }
            }
        }

        private fun trimGarbageSubtrees(block: BlockTree, flow: StructuredFlow) {
            val referenceIndices = mutableSetOf<Int>()
            fun visit(cf: ControlFlow) {
                cf.ref?.index?.let { referenceIndices.add(it) }
                for (sub in cf.clauses) {
                    visit(sub)
                }
            }
            visit(flow.controlFlow)

            val edgeToIndex = block.edges.mapIndexed { i, e -> e to i }

            for ((edge, edgeIndex) in edgeToIndex) {
                if (edgeIndex !in referenceIndices) {
                    // Garbage subtree
                    val replacement = ValueLeaf(edge.target.document, edge.target.pos, void)
                    replacement.typeInferences =
                        BasicTypeInferences(WellKnownTypes.voidType, emptyList())
                    edge.replace(replacement)
                }
            }
        }
    }
}

internal fun prefixBlockWith(prefixes: List<Tree>, block: BlockTree) {
    if (prefixes.isNotEmpty()) {
        when (val flow = block.flow) {
            is LinearFlow -> {
                val (_, startIndex) = breakLabelFor(block)
                val noneAtStart = startIndex until startIndex
                block.replace(noneAtStart) { prefixes.forEach { Replant(it) } }
            }
            is StructuredFlow -> {
                val stmts = prefixes.mapIndexed { i, t ->
                    ControlFlow.Stmt(
                        BlockChildReference(block.size + i, t.pos),
                    )
                }
                block.replace(block.size until block.size) {
                    prefixes.forEach { Replant(it) }
                }
                flow.controlFlow.withMutableStmtList { stmtList ->
                    stmtList.addAll(0, stmts)
                }
            }
        }
    }
}

internal fun prefixWith(prefixes: List<Tree>, tree: Tree) {
    if (tree is BlockTree) {
        prefixBlockWith(prefixes, tree)
    } else if (prefixes.isNotEmpty()) {
        val doc = tree.document
        val wrapper = BlockTree(doc, tree.pos, emptyList(), LinearFlow)
        tree.incoming!!.replace(wrapper)
        prefixBlockWith(prefixes + tree, wrapper)
        structureBlock(wrapper)
    }
}

fun structureBlock(block: BlockTree): StructuredFlow {
    return when (val bFlow = block.flow) {
        is StructuredFlow -> bFlow
        is LinearFlow -> {
            // If the block is a labeled block, now's a great time to incorporate that.
            val (breakLabelName, startIndex) = breakLabelFor(block)
            val breakLabel = if (breakLabelName != null) {
                breakLabelName as JumpLabel
            } else {
                null
            }

            // Replace any `\label LabelName` children with `void`
            for (i in 0 until startIndex) {
                val e = block.edge(i)
                val c = e.target
                e.replace(ValueLeaf(c.document, c.pos, void))
            }

            val stmtList = (startIndex until block.size).map { childIndex ->
                val pos = block.child(childIndex).pos
                ControlFlow.Stmt(BlockChildReference(childIndex, pos))
            }
            val stmts = ControlFlow.StmtBlock(block.pos, stmtList)
            val controlFlow = when (breakLabel) {
                null -> stmts
                else -> ControlFlow.StmtBlock.wrap(
                    ControlFlow.Labeled(
                        block.pos,
                        breakLabel = breakLabel,
                        continueLabel = null,
                        stmts = stmts,
                    ),
                )
            }

            val structuredFlow = StructuredFlow(controlFlow)
            block.replaceFlow(structuredFlow)
            structuredFlow
        }
    }
}

private fun ithChildUnderSameRoot(parent: Tree, i: Int) =
    // Nested roots are handled separately by the companion object's fun weave().
    !(parent is FunTree && i + 1 == parent.size)

private fun breakLabelFor(block: BlockTree): Pair<TemperName?, Int> {
    val parts = block.parts
    val label = parts.label
    return (label?.target as? NameLeaf)?.content to parts.startIndex
}

/**
 * Caches state to allow efficiently analyzing whether a statement might jump out of the normal
 * order via, an embedded `break`, `continue`, or failure path.
 */
private class FlowAnalyzer {
    private val mayJumpOutCache = mutableMapOf<Tree, Boolean>()

    /** True if [t] might `goto` somewhere */
    fun mayJumpOut(t: Tree): Boolean = mayJumpOutCache.getOrPut(t) {
        when (t) {
            is BlockTree -> {
                when (val flow = t.flow) {
                    is LinearFlow -> t.children.any { mayJumpOut(it) }
                    is StructuredFlow -> {
                        fun testMayJump(cf: ControlFlow): Boolean {
                            if (cf is ControlFlow.Jump) {
                                var ancestor = cf.parent
                                while (ancestor != null) {
                                    if (ancestor is JumpDestination && ancestor.matches(cf)) {
                                        return true
                                    }
                                    ancestor = ancestor.parent
                                }
                            }
                            val ref = cf.ref
                            if (ref != null) {
                                val child = t.dereference(ref)?.target
                                if (child != null && mayJumpOut(child)) { return false }
                            }
                            return cf.clauses.any { testMayJump(it) }
                        }
                        testMayJump(flow.controlFlow)
                    }
                }
            }
            is CallTree,
            is DeclTree,
            is EscTree,
            -> t.children.any { mayJumpOut(it) }
            // Assume `break`/`continue` do not cross function boundaries
            // TODO: for inlineable functions, this may not be correct.  Bound this.
            // TODO: what is the meaning of a jump appear in a default parameter expressions:
            //   `fn (x: Int = break) { ... }`
            is FunTree -> false
            is StayLeaf -> false
            is LeftNameLeaf -> false
            is RightNameLeaf -> false
            is ValueLeaf -> false
        }
    }

    fun mayBeResultOfLinearFlow(block: BlockTree, childIndex: Int): Boolean {
        val next = block.childOrNull(childIndex + 1)
            ?: return true // Last statement is result unless there's a goto earlier
        return mayJumpOut(next)
    }
}

/** True if the tree is a block that is reliably a no-op. */
private val (Tree).isNoopBlock: Boolean
    get() {
        if (this is ValueLeaf && void == this.content) { return true }
        if (this !is BlockTree) { return false }
        // If a block has no child that is not a noop block, and progresses linearly through them,
        // then 🎶it's-a no-op🎶.
        return when (val flow = this.flow) {
            is LinearFlow -> children.all { it.isNoopBlock }
            is StructuredFlow -> flow.controlFlow.isNoopBlock(this)
        }
    }

internal fun (ControlFlow).isNoopBlock(block: BlockTree): Boolean = when (this) {
    is ControlFlow.Stmt -> block.dereference(ref)?.target?.isNoopBlock == true
    is ControlFlow.StmtBlock -> stmts.all { it.isNoopBlock(block) }
    is ControlFlow.Labeled -> stmts.isNoopBlock(block)
    is ControlFlow.If,
    is ControlFlow.Break,
    is ControlFlow.Continue,
    is ControlFlow.Loop,
    is ControlFlow.OrElse,
    -> false
}

private const val SET_LOCAL_RIGHT_INDEX = 2 // setLocal, left, right
private const val SETP_RIGHT_INDEX = 3 // setp, property name, this value, right

internal fun shouldExtractForWeave(parent: Tree, childIndex: Int, varNames: Set<ResolvedName>): Boolean {
    val child = parent.child(childIndex)
    return !isLeftHandSide(parent, childIndex) && when (child) {
        is StayLeaf -> false
        is ValueLeaf -> false
        is FunTree -> false
        // We should not extract the name at position 1 after \label in a LinearFlow block,
        // but this is never called with a BlockTree as a parent.
        is RightNameLeaf -> child.content in varNames
        is LeftNameLeaf -> false
        is CallTree -> {
            // Some calls are intermediate steps to other calls:
            // - angle bracket calls associate type parameters with a callee.
            // These should stay in situ.
            when (child.childOrNull(0)?.functionContained) {
                BuiltinFuns.angleFn -> child.size != 1 || shouldExtractForWeave(child, 1, varNames)
                else -> true
            }
        }
        is DeclTree, is EscTree -> true
        is BlockTree -> false // Already marked for extraction.
    }
}

private fun varNamesOf(t: Tree) = buildSet {
    TreeVisit.startingAt(t)
        .forEachContinuing {
            val parts = (it as? DeclTree)?.parts
            if (parts != null) {
                val name = parts.name.content as? ResolvedName
                if (name != null && varSymbol in parts.metadataSymbolMultimap) {
                    add(name)
                }
            }
        }
        .visitPreOrder()
}
