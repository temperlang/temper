package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.AwaitFn
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.YieldFn
import lang.temper.builtin.isHandlerScopeCall
import lang.temper.common.LeftOrRight
import lang.temper.common.Log
import lang.temper.common.allMapToSameElseNull
import lang.temper.common.compatReversed
import lang.temper.common.console
import lang.temper.common.ignore
import lang.temper.frontend.syntax.isAssignment
import lang.temper.frontend.syntax.isLeftHandSide
import lang.temper.interp.convertToErrorNode
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ResolvedName
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.BindMemberAccessor
import lang.temper.type.DotHelper
import lang.temper.type.ExternalSet
import lang.temper.type.InternalSet
import lang.temper.type.WellKnownTypes
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.DefaultJumpSpecifier
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
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.fnParsedName
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.getTerminalExpressions
import lang.temper.value.initSymbol
import lang.temper.value.invertLogicalExpr
import lang.temper.value.isBubbleCall
import lang.temper.value.matches
import lang.temper.value.returnsVoidClearly
import lang.temper.value.toLispy
import lang.temper.value.toPseudoCode
import lang.temper.value.vVarSymbol
import lang.temper.value.valueContained
import lang.temper.value.void

private const val DEBUG = false

private inline fun debug(p: Positioned, f: () -> Any) {
    @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
    if (DEBUG && p.pos.loc != ImplicitsCodeLocation) {
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
 * - local variable declarations
 * - assignments `a = b` in Python (before PEP 0572) and in Golang
 * - `yield`, `return` in C-like languages
 * - error originating operators like `throw` or Temper `bubble()`
 * - error checks or interceptions like `catch` in Java-like languages
 * - some languages, like Python, do not allow function expressions that themselves contain statements
 *   so at least some function expression need to be turned into nested, named functions.
 *
 * These also need to be woven as close as possible to the containing module/function body.
 *
 * [![](Weaver.png)](Weaver.png)
 */
class Weaver private constructor(
    private val logSink: LogSink,
    /** For debugging */
    private val filePositions: Map<FilePath, FilePositions>,
    private val root: BlockTree,
    private val failureConditionNeedsChecking: (NameLeaf) -> Boolean,
    /**
     * Whether to pull special functions like assignments and uses of handler scope
     * towards the root.
     */
    private val pullSpecialsRootward: Boolean,
    /**
     * Whether to pull free function trees into declarations.
     */
    private val nameAllFunctions: Boolean,
) {
    private val nameMaker = root.document.nameMaker
    private val temporariesAllocated = mutableMapOf<ResolvedName, Position>()

    private inline fun debug(f: () -> Any) = debug(root, f)

    private fun allocateName(
        pos: Position,
        nameHint: String = Temporary.defaultNameHint,
    ): Temporary {
        val name = nameMaker.unusedTemporaryName(nameHint)
        temporariesAllocated[name] = pos
        return name
    }

    private fun weave() {
        debug {
            console.group("Weave") {
                console.log(root.toLispy(multiline = true))
            }
        }
        checkFailureConditions(root)
        debug {
            console.group("Failure conditions checked") {
                console.log(root.toLispy(multiline = true))
            }
        }
        simplifyBlocks(root)
        debug {
            console.group("Blocks simplified") {
                console.log(root.toLispy(multiline = true))
            }
        }
        addWeightToStatementLikeExpressions(root)
        debug {
            console.group("Before pull") {
                console.log(root.toLispy(multiline = true))
            }
        }
        pullRootwards(root)
        debug {
            console.group("After pull") {
                console.log(root.toLispy(multiline = true))
            }
        }
        declareTemporariesAllocated()
        debug {
            console.group("After temporaries declared") {
                console.log(root.toLispy(multiline = true))
            }
        }
        evaporateBubbles()
    }

    private fun declareTemporariesAllocated() {
        val doc = root.document
        val decls = temporariesAllocated.map { (name, pos) ->
            DeclTree(
                doc,
                pos,
                listOf(
                    LeftNameLeaf(doc, pos, name),
                    // Temporaries declared at root level, but use may be in loop.
                    ValueLeaf(doc, pos, vVarSymbol),
                    ValueLeaf(doc, pos, void),
                ),
            )
        }
        temporariesAllocated.clear()
        prefixBlockWith(decls, root)
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

    /** Walk post-order to pull blocks as close to the root as possible. */
    private fun pullRootwards(tree: Tree) {
        var i = 0
        // This loop samples size every time and captures nextEdge early so that it's not sensitive
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
                val treeEdge = tree.incoming!! // !! safe since tree != root
                val parent = treeEdge.source!!
                val indexInParent = treeEdge.edgeIndex

                val result = if (resultMayBeUsed(tree)) {
                    when (val blockResult = storeBlockResult(tree)) {
                        null -> null
                        is NameForResult ->
                            RightNameLeaf(tree.document, tree.pos, blockResult.resultName)
                        is KnownResult ->
                            ValueLeaf(tree.document, tree.pos, blockResult.value)
                    }
                } else {
                    null
                }

                treeEdge.replace(result ?: ValueLeaf(tree.document, tree.pos, void))

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

    private fun resultMayBeUsed(block: BlockTree): Boolean {
        require(block != root)
        val incoming = block.incoming
        return when (val parent = incoming?.source) {
            is BlockTree -> if (parent.flow is LinearFlow) {
                parent.edge(parent.size - 1) == incoming
            } else {
                // maybe.
                true
            }
            else -> true
        }
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
                stitchControlFlowsTogether(
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

                swapBeforeAndKeepSneaking(
                    tree = tree,
                    pulledBlock = pulledBlock,
                    childIndex = indexInTree,
                )
            }
            is EscTree -> {
                // How did we even get here?
            }
        }
    }

    private fun swapBeforeAndKeepSneaking(
        tree: InnerTree,
        pulledBlock: BlockTree,
        childIndex: Int,
    ) {
        val document = tree.document
        val prefix = mutableListOf<Tree>()
        // Capture siblings to the left in temporaries so that we can ensure that things are
        // evaluated in order.
        for (i in 0 until childIndex) {
            if (shouldExtract(tree, i)) {
                val edge = tree.edge(i)
                val sibling = edge.target
                if (sibling is DeclTree) {
                    // If we need to swap something before a declaration, just check that any
                    // declaration types and initial values are pulled out, then forego
                    // assigning the declaration to a temporary.
                    val parts = sibling.partsIgnoringName
                    if (parts != null) {
                        val typeEdge = parts.type
                        val initEdge = parts.metadataSymbolMap[initSymbol]
                        for (declChildEdge in listOf(typeEdge, initEdge)) {
                            if (
                                declChildEdge != null &&
                                shouldExtract(sibling, declChildEdge.edgeIndex)
                            ) {
                                val declChild = declChildEdge.target
                                val pos = declChild.pos
                                val alias = allocateName(pos)
                                declChildEdge.replace(RightNameLeaf(document, pos, alias))
                                prefix.add(assign(alias, declChild))
                            }
                        }
                        continue
                    }
                }

                val alias = allocateName(sibling.pos)
                edge.replace(RightNameLeaf(document, sibling.pos, alias))
                prefix.add(assign(alias, sibling))
            }
        }
        prefixBlockWith(prefix, pulledBlock)

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

    private fun shouldExtract(parent: Tree, childIndex: Int): Boolean {
        val child = parent.child(childIndex)
        return when {
            child is StayLeaf -> false
            child is ValueLeaf -> false
            child is FunTree -> false
            child is NameLeaf -> false
            isLeftHandSide(parent, childIndex) -> false
            child is CallTree -> {
                // Some calls are intermediate steps to other calls:
                // - angle bracket calls associate type parameters with a callee.
                // - do_bind_xyz calls associate a subject with a method dot name
                // These should stay in situ.
                val callee = child.childOrNull(0)
                when (val calleeFn = callee?.functionContained) {
                    BuiltinFuns.angleFn -> child.size != 1 || shouldExtract(child, 1)
                    is DotHelper -> calleeFn.memberAccessor !is BindMemberAccessor
                    else -> true
                }
            }
            // We should not extract the name at position 1 after \label in a LinearFlow block,
            // but this is never called with a BlockTree as a parent.
            else -> true
        }
    }

    /**
     * Walks back from exit to figure out which expression is used as the block's result and
     * returns either:
     * - a temporary that aliases that
     * - the known result value
     */
    private fun storeBlockResult(b: BlockTree): BlockResult? {
        structureBlock(b)
        val (ends) = b.getTerminalExpressions(assumeFailureCanHappen = true)
        // If all the ends assign to the same temporary, great!
        val knownResult = ends.allMapToSameElseNull {
            val e = b.dereference(it.ref)
            val t = e?.target
            t?.valueContained
        }
        val existingName = ends.allMapToSameElseNull {
            val e = b.dereference(it.ref)
            if (e != null && isAssignment(e.target)) leftHandSideOf(e.target) else null
        }
        return when {
            // Temporaries tend to be single purpose so we'll not try to pull two things
            // left that coincidentally assign to the same temporary and clobber one
            // another.
            // TODO: We could check whether name as allocated by this pass, but that
            // would make this pass not idempotent.  Do we care?
            knownResult != null -> KnownResult(knownResult)
            existingName is Temporary -> NameForResult(existingName)
            ends.isNotEmpty() -> {
                val allocatedName = allocateName(ends.first().pos)
                for (end in ends) {
                    var endReference = end.ref
                    var endEdge = b.dereference(endReference) ?: continue
                    // Make sure that we don't treat declarations as value producers.
                    // Declarations need to sink to the bottom, just above blocks.
                    if (!isValidRightHandSide(endEdge.target)) {
                        val target = endEdge.target
                        // Add a void expression after the declaration.
                        val voidValueIndex = b.size
                        val voidTree =
                            ValueLeaf(target.document, target.pos.rightEdge, void)
                        val voidReference =
                            BlockChildReference(voidValueIndex, voidTree.pos)
                        b.add(voidValueIndex, voidTree)

                        // Replace the node that references the declaration with a subsystem
                        // that has the declaration followed by the expression `void`
                        end.parent!!.withMutableStmtList { stmtList ->
                            stmtList.add(ControlFlow.Stmt(voidReference))
                        }

                        // Now, overwrite endEdge so that the assignment below adds an
                        // assignment that captures void.
                        endEdge = b.edge(voidValueIndex)
                        endReference = voidReference
                    }
                    val assignment = assign(
                        allocatedName,
                        // This assumes there's only one reference to end, which should be
                        // the case.
                        // TODO: maybe add a check for this before we start weaving.
                        freeTarget(endEdge),
                    )
                    val childIndex = b.size
                    b.add(childIndex, assignment)
                    endReference.overrideIndex(childIndex)
                }
                NameForResult(allocatedName)
            }
            else -> // End is never reached.
                null
        }
    }

    private fun assign(name: ResolvedName, expr: Tree): CallTree {
        val pos = expr.pos
        return expr.treeFarm.grow {
            Call(pos) {
                V(pos.leftEdge, setLocalFnValue)
                Ln(pos.leftEdge, name)
                Replant(expr)
            }
        }
    }

    private fun leftHandSideOf(child: Tree): ResolvedName? {
        if (isAssignment(child)) {
            val c1 = child.child(1)
            if (c1 is NameLeaf) {
                return c1.content as ResolvedName?
            }
        }
        return null
    }

    private fun stitchControlFlowsTogether(
        outerBlock: BlockTree,
        pulledBlock: BlockTree,
        edgeIndex: Int,
    ) {
        // We can't splice a structured flow into a linear flow, so complicate parent.
        val outerFlow = structureBlock(outerBlock)

        // Make sure there is at most one BlockChildReference to the pulled block.
        // Then replace it.
        var ok = true
        fun tooManyReferents(old: Positioned, new: Positioned) {
            // Too many references to child.
            ok = false
            logSink.log( // TODO: too many args?
                level = Log.Error,
                template = MessageTemplate.MalformedFlow,
                pos = new.pos,
                values = listOf(old.pos),
            )
        }

        var referringCondition: ControlFlow.Conditional? = null
        var referringStmt: ControlFlow.Stmt? = null
        fun scanForEdgeIndex(cf: ControlFlow) {
            if (cf is ControlFlow.StmtBlock) { // All Stmt nodes are in a StmtBlock
                for (sub in cf.stmts) {
                    if (sub is ControlFlow.Stmt) {
                        if (sub.ref.index == edgeIndex) {
                            val old = referringStmt
                            if (old != null) {
                                tooManyReferents(old, sub)
                            } else {
                                referringStmt = sub
                            }
                        }
                    }
                }
            } else {
                if (cf is ControlFlow.Conditional && cf.condition.index == edgeIndex) {
                    val old = referringCondition
                    if (old != null) {
                        tooManyReferents(old, cf)
                    } else {
                        referringCondition = cf
                    }
                }
            }
            for (sub in cf.clauses) {
                scanForEdgeIndex(sub)
            }
        }
        scanForEdgeIndex(outerFlow.controlFlow)

        if (referringStmt != null && referringCondition != null) {
            tooManyReferents(referringStmt, referringCondition)
            referringCondition = null
        }

        if (!ok) {
            // All `ok = false` are accompanied by log calls so no need to log here.
            convertToErrorNode(outerBlock.edge(edgeIndex))
        }

        val rc = referringCondition
        val rs = referringStmt

        debug {
            console.log("\nStitching at $edgeIndex")
            console.group("Outer: ${ outerBlock.pos.toString(filePositions) }") {
                outerBlock.toPseudoCode(console.textOutput)
            }
            console.group("Pulled: ${ pulledBlock.pos.toString(filePositions) }") {
                pulledBlock.toPseudoCode(console.textOutput)
            }
            console.log(". rs=$rs")
            console.log(". rc=$rc")
        }

        when {
            rc != null -> {
                // The block was part of something that was used to compute an edge condition.
                // Make sure that the block just runs before that condition is checked.
                // The block's result was already extracted into a temporary which should be
                // incorporated into the condition.

                when (rc) {
                    is ControlFlow.If -> {
                        // Insert a blank node before the condition.
                        //
                        //     precedingSibling();
                        //     if (t#1 = BLOCKY_STUFF) { thenClause } else { elseClause }
                        //
                        //     ->
                        //
                        //     precedingSibling();
                        //     t#1 = BLOCKY_STUFF;
                        //     if (t#1) { thenClause } else { elseClause }
                        val parent = rc.parent as ControlFlow.StmtBlock
                        val indexInParent = parent.stmts.indexOf(rc)
                        val innerRemapped = remapControlFlow(pulledBlock, outerBlock)
                        parent.withMutableStmtList { siblings ->
                            siblings.addAll(indexInParent, innerRemapped)
                        }
                    }
                    is ControlFlow.Loop -> {
                        // With loops, we need to insert the extra instructions so that
                        // they run every time the loop runs, being careful that a `continue`
                        // runs those instructions instead of just running the condition.
                        //
                        //    while (t#1 = BLOCKY_STUFF) {
                        //      ...; continue; ...
                        //    }
                        //
                        // ->
                        //
                        //    while (true) {
                        //      t#1 = BLOCK_STUFF;
                        //      if (!t#1) { break }
                        //      ...; continue; ...
                        //    }
                        //
                        // There, the continue goes to the right place.
                        // But do...while loops are a bit trickier
                        //
                        //    do {
                        //      ...; continue; ...
                        //    } while (t#1 = BLOCKY_STUFF);
                        //
                        // ->
                        //
                        //    do {
                        //      continue_label: do {
                        //        ...; break continue_label; ...
                        //      }
                        //      t#1 = BLOCKY_STUFF;
                        //    } while (t#1);
                        //
                        // But we don't have a way to convert all possible `continue`s
                        // in the body to labeled `break`s, so we store the label with
                        // the loop in case we later need to resolve free `continue`s in
                        // inlined block lambdas.
                        val body = rc.body
                        val doc = outerBlock.document
                        val innerRemapped = remapControlFlow(pulledBlock, outerBlock)
                        rc.condition.invertLogicalExpr(outerBlock)
                        val breakUnlessCondition = ControlFlow.If(
                            rc.condition.pos,
                            rc.condition,
                            ControlFlow.StmtBlock.wrap(
                                ControlFlow.Break(rc.condition.pos.rightEdge, DefaultJumpSpecifier),
                            ),
                            ControlFlow.StmtBlock(rc.condition.pos.rightEdge, emptyList()),
                        )

                        val trueValueRef = BlockChildReference(
                            outerBlock.children.size,
                            rc.pos.leftEdge,
                        )
                        outerBlock.add(
                            ValueLeaf(doc, trueValueRef.pos, TBoolean.valueTrue),
                        )
                        rc.condition = trueValueRef

                        when (rc.checkPosition) {
                            LeftOrRight.Left -> {
                                val beforeBody = innerRemapped + listOf(breakUnlessCondition)
                                // Create an if (condition)
                                // Insert at front of body.
                                body.withMutableStmtList { bodyStmts ->
                                    bodyStmts.addAll(0, beforeBody)
                                }
                            }
                            LeftOrRight.Right -> {
                                val bodyStmtList = body.stmts
                                val continueLabel = doc.nameMaker.unusedTemporaryName("continue")
                                body.withMutableStmtList { bodyStmts ->
                                    bodyStmts.clear()
                                    // Wrap the *real* body so that any `continue`s in it
                                    // go to before the remapped condition.
                                    bodyStmts.add(
                                        ControlFlow.Labeled(
                                            body.pos,
                                            breakLabel = continueLabel,
                                            continueLabel = continueLabel,
                                            stmts = ControlFlow.StmtBlock(body.pos, bodyStmtList),
                                        ),
                                    )
                                    bodyStmts.addAll(innerRemapped)
                                    bodyStmts.add(breakUnlessCondition)
                                }
                            }
                        }
                    }
                }
            }
            rs != null -> {
                val parent = rs.parent!!
                val indexInParent = parent.stmts.indexOf(rs)
                parent.withMutableStmtList { siblings ->
                    val toReplace = siblings.subList(indexInParent, indexInParent)
                    toReplace.clear()
                    toReplace.addAll(
                        remapControlFlow(pulledBlock, outerBlock),
                    )
                }
            }
            else -> {
                // We can legitimately reach here.

                // One possible scenario is
                //
                //     0 orelse console.log("unreachable");
                //
                // Since the print is only reachable when (0) fails, and
                // we know that it cannot even without type info, there is
                // no path to the (0) sub-expressions sub-system to its
                // failExit.

                // Ignore pulledBlock. trimGarbageSubtrees will clean it up.
                ignore(pulledBlock)
            }
        }
    }

    private fun remapControlFlow(
        sourceBlock: BlockTree,
        destBlock: BlockTree,
    ): List<ControlFlow> {
        val sourceFlow = structureBlock(sourceBlock)
        fun remapRef(ref: BlockChildReference): BlockChildReference {
            // Move the referenced tree from sourceBlock into destBlock
            val edge = sourceBlock.dereference(ref)
            return if (edge != null) {
                val indexInDest = destBlock.size
                destBlock.add(freeTarget(edge))
                BlockChildReference(indexInDest, ref.pos)
            } else {
                BlockChildReference(null, ref.pos)
            }
        }

        lateinit var remapStmts: (ControlFlow.StmtBlock) -> ControlFlow.StmtBlock

        fun remapOne(cf: ControlFlow): ControlFlow = when (cf) {
            is ControlFlow.If -> ControlFlow.If(
                pos = cf.pos,
                condition = remapRef(cf.condition),
                thenClause = remapStmts(cf.thenClause),
                elseClause = remapStmts(cf.elseClause),
            )
            is ControlFlow.Loop -> ControlFlow.Loop(
                pos = cf.pos,
                label = cf.label,
                checkPosition = cf.checkPosition,
                condition = remapRef(cf.condition),
                body = remapStmts(cf.body),
                increment = remapStmts(cf.increment),
            )
            is ControlFlow.Jump -> cf.deepCopy()
            is ControlFlow.Labeled -> ControlFlow.Labeled(
                pos = cf.pos,
                breakLabel = cf.breakLabel,
                continueLabel = cf.continueLabel,
                stmts = remapStmts(cf.stmts),
            )
            is ControlFlow.OrElse -> ControlFlow.OrElse(
                pos = cf.pos,
                orClause = remapOne(cf.orClause) as ControlFlow.Labeled,
                elseClause = remapStmts(cf.elseClause),
            )
            is ControlFlow.Stmt -> ControlFlow.Stmt(
                ref = remapRef(cf.ref),
            )
            is ControlFlow.StmtBlock -> ControlFlow.StmtBlock(
                pos = cf.pos,
                stmts = cf.stmts.map { remapOne(it) },
            )
        }
        remapStmts = { remapOne(it) as ControlFlow.StmtBlock }

        return buildList {
            sourceFlow.controlFlow.stmts.forEach { stmt ->
                add(remapOne(stmt))
            }
        }
    }

    private fun checkFailureConditions(tree: Tree) {
        for (i in tree.indices) {
            if (ithChildUnderSameRoot(tree, i)) {
                checkFailureConditions(tree.child(i))
            }
        }
        if (tree !is CallTree) {
            return
        }
        val callee = tree.childOrNull(0)
        if (callee?.functionContained != BuiltinFuns.handlerScope) {
            return
        }
        // Convert to a block that jumps to fail here.
        // Doing this here instead of in MagicSecurityDust lets us avoid having to
        // hoist things out there.
        val failNameLeaf = tree.childOrNull(1)
        if (failNameLeaf !is NameLeaf) {
            return
        }
        if (!failureConditionNeedsChecking(failNameLeaf)) {
            return
        }
        val failName = failNameLeaf.content

        val incomingEdge = tree.incoming!! // Safe since root is not a call to hs().

        debug {
            console.group("Expanding") {
                console.log(incomingEdge.target.toLispy(multiline = true))
            }
        }

        // Insert a check that is, in spirit,
        //     if (failNameLeaf) bubble()
        val doc = tree.document
        val pos = tree.pos
        val posRight = pos.rightEdge

        val checkControlFlow = ControlFlow.StmtBlock(
            pos,
            listOf(
                ControlFlow.Stmt(
                    BlockChildReference(0, pos), // See block below for indices
                ),
                ControlFlow.If(
                    pos = posRight,
                    condition = BlockChildReference(1, posRight),
                    thenClause = ControlFlow.StmtBlock(
                        posRight,
                        listOf(ControlFlow.Stmt(BlockChildReference(2, posRight))),
                    ),
                    elseClause = ControlFlow.StmtBlock(posRight, emptyList()),
                ),
            ),
        )

        val replacement = BlockTree(
            doc,
            pos,
            listOf(
                freeTarget(incomingEdge),
                RightNameLeaf(doc, posRight, failName),
                CallTree(
                    doc,
                    posRight,
                    listOf(ValueLeaf(doc, posRight, BuiltinFuns.vBubble)),
                ),
            ),
            StructuredFlow(checkControlFlow),
        )

        incomingEdge.replace(replacement)
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
     * Not weaving these blocks unnecessarily, reduces the amount of temporaries, and leaves
     * reduced or reducible values in place.
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
     * When an assignment appears directly inside a handler scope call, it's the handler scope call
     * that gets wrapped so that assignments that may fail are moved to the root as a unit.
     *
     * `hs(fail, a = b)` -> `{ hs(fail, a = b) }`
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
        val doc = root.document
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
                    // TODO: do closure conversion to solve this problem generally for backends
                    // where we do not have first-class, anonymous functions.
                    // If not, introduce a temporary and weigh it down with a block.
                    if (isAssignment(parent) && edge.edgeIndex == 2) {
                        // ok where it is
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

                /**
                 * Helper that captures the right side of an assignment that has a simple
                 * left-hand side in a temporary so that we can refer to it as the
                 * result of the created block.
                 */
                fun (Planting).captureRightInTemporary(rightEdge: TEdge): Temporary {
                    val right = rightEdge.target
                    if (right is RightNameLeaf) {
                        val rightName = right.content
                        if (rightName is Temporary) {
                            return rightName
                        }
                    }
                    val t = allocateName(rightEdge.target.pos)
                    rightEdge.replace(RightNameLeaf(doc, right.pos, t))
                    Call(tree.pos.leftEdge, BuiltinFuns.vSetLocalFn) {
                        Ln(t)
                        Replant(freeTree(right))
                    }
                    return t
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
                    BuiltinFuns.handlerScope -> if (pullSpecialsRootward) {
                        // If the handled expression is an assignment, pull it.
                        // This relates to the distinction between HandlerScopeStatement
                        // and HandlerScopeExpression
                        val handled = tree.childOrNull(2) // tree is (hs failId handled)
                        if (handled != null && isAssignment(handled)) {
                            //     hs(fail, left = right)
                            // ->
                            //     { t = right; hs(fail, left = t); t }
                            // which is ok as long as we dive to a path that does not use
                            // the result unless !fail.
                            val leftEdge = handled.edge(1)
                            if (leftEdge.target is LeftNameLeaf) { // Not changing OoO
                                val rightEdge = handled.edge(2)
                                heavies.add(
                                    edge to {
                                        Block {
                                            val t = captureRightInTemporary(rightEdge)
                                            Replant(freeTree(tree))
                                            Rn(tree.pos.rightEdge, t)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    AwaitFn -> if (pullSpecialsRootward) {
                        // If it's in a simple assignment or a handler scope (hs) call,
                        // then it'll sink to where it's needed.
                        val alreadySinking = when {
                            parent is CallTree && isHandlerScopeCall(parent) -> true
                            isAssignment(parent) ->
                                edge.edgeIndex == 2 && parent?.childOrNull(1) is LeftNameLeaf
                            else -> false
                        }
                        if (!alreadySinking) {
                            val temporary = allocateName(tree.pos.leftEdge, "t")
                            heavies.add(
                                edge to {
                                    Block {
                                        Call(tree.pos, BuiltinFuns.setLocalFn) {
                                            Ln(temporary)
                                            Replant(freeTree(tree))
                                        }
                                    }
                                },
                            )
                        }
                    }
                    BuiltinFuns.setLocalFn,
                    BuiltinFuns.setpFn,
                    is DotHelper,
                    -> if (pullSpecialsRootward) {
                        // If the parent is not a handler scope call, pull it.
                        val parentCallee =
                            (edge.source as? CallTree)?.child(0)?.valueContained
                        val rightIndex = when (fn) {
                            is DotHelper -> when (val accessor = fn.memberAccessor) {
                                InternalSet, ExternalSet -> accessor.firstArgumentIndex + 2
                                else -> -1 // Not assignment-like
                            }
                            BuiltinFuns.setpFn -> SETP_RIGHT_INDEX
                            BuiltinFuns.setLocalFn -> SET_LOCAL_RIGHT_INDEX
                            else -> error("$fn") // fn matched above
                        }
                        if (
                            parentCallee != BuiltinFuns.vHandlerScope &&
                            rightIndex in tree.indices
                        ) {
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
                                val rightEdge = tree.edge(rightIndex)
                                //     left = right
                                // ->
                                //     { t = right; left = t; t }
                                heavies.add(
                                    edge to {
                                        Block {
                                            val t = captureRightInTemporary(rightEdge)
                                            Replant(freeTree(tree))
                                            Rn(tree.pos.rightEdge, t)
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
            module: Module,
            moduleRoot: BlockTree,
            pullSpecialsRootward: Boolean,
            nameAllFunctions: Boolean,
            failureConditionNeedsChecking: (NameLeaf) -> Boolean =
                defaultFailureConditionNeedsChecking,
        ) = weave(
            logSink = module.logSink,
            filePositions = module.filePositions,
            root = moduleRoot,
            pullSpecialsRootward = pullSpecialsRootward,
            nameAllFunctions = nameAllFunctions,
            failureConditionNeedsChecking = failureConditionNeedsChecking,
        )

        private fun weave(
            logSink: LogSink,
            filePositions: Map<FilePath, FilePositions>,
            root: BlockTree,
            pullSpecialsRootward: Boolean,
            nameAllFunctions: Boolean,
            failureConditionNeedsChecking: (NameLeaf) -> Boolean =
                defaultFailureConditionNeedsChecking,
        ) {
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
                    logSink = logSink,
                    filePositions = filePositions,
                    root = rootBlock,
                    pullSpecialsRootward = pullSpecialsRootward,
                    nameAllFunctions = nameAllFunctions,
                    failureConditionNeedsChecking = failureConditionNeedsChecking,
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
            trimLooseThreads(root)
            debug(root) {
                console.log("After trim loose threads")
                root.toPseudoCode(console.textOutput)
            }
        }

        internal fun reweaveSelected(
            edges: List<TEdge>,
            logSink: LogSink,
            filePositions: Map<FilePath, FilePositions>,
        ) {
            val selectRoots = mutableSetOf<BlockTree>()
            fun isFunctionOrModuleBody(t: Tree): Boolean {
                val edge = t.incoming ?: return true
                val parent = edge.source
                return parent is FunTree && parent.size - 1 == edge.edgeIndex
            }
            for (edge in edges) {
                var t = edge.target
                while (true) {
                    if (isFunctionOrModuleBody(t)) {
                        if (t !is BlockTree) {
                            t = BlockTree(
                                t.document,
                                t.pos,
                                listOf(freeTree(t)),
                                LinearFlow,
                            )
                            t.incoming!!.replace(t)
                        }
                        selectRoots.add(t)
                        break
                    }
                    t = t.incoming!!.source!!
                }
            }
            for (rootBlock in selectRoots) {
                val rootEdge = rootBlock.incoming
                Weaver(
                    logSink = logSink,
                    filePositions = filePositions,
                    root = rootBlock,
                    pullSpecialsRootward = false,
                    nameAllFunctions = false,
                    failureConditionNeedsChecking = { false },
                ).weave()
                require(
                    rootEdge == null ||
                        (rootEdge.target == rootBlock && rootEdge.source != null),
                )
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
        private fun trimLooseThreads(tree: Tree) {
            TreeVisit
                .startingAt(tree)
                .forEachContinuing {
                    if (it is BlockTree) {
                        when (val flow = it.flow) {
                            is LinearFlow -> {}
                            is StructuredFlow -> trimGarbageSubtrees(it, flow)
                        }
                    }
                }
                .visitPreOrder()
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

        private val defaultFailureConditionNeedsChecking: (NameLeaf) -> Boolean = { true }
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

internal fun structureBlock(block: BlockTree): StructuredFlow {
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

private val setLocalFnValue = BuiltinFuns.vSetLocalFn

internal fun isValidRightHandSide(tree: Tree): Boolean = when (tree) {
    is BlockTree -> false
    is CallTree -> true
    is DeclTree -> false
    is EscTree -> true
    is FunTree -> true
    is NameLeaf -> true
    is StayLeaf -> false
    is ValueLeaf -> true
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
        // If a block has no child that is not a noop block, and progresses linearly through them
        // then 🎶it's-a no-op🎶.
        return when (val flow = this.flow) {
            is LinearFlow -> children.all { it.isNoopBlock }
            is StructuredFlow -> flow.controlFlow.isNoopBlock(this)
        }
    }

private fun (ControlFlow).isNoopBlock(block: BlockTree): Boolean = when (this) {
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

private sealed class BlockResult

/** The blocks result is stored in a temporary variable. */
private data class NameForResult(val resultName: Temporary) : BlockResult()

/** The blocks result is a known value. */
private data class KnownResult(val value: Value<*>) : BlockResult()

private const val SET_LOCAL_RIGHT_INDEX = 2 // setLocal, left, right
private const val SETP_RIGHT_INDEX = 3 // setp, property name, this value, right
