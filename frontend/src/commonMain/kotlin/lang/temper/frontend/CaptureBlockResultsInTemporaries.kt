package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.Assign
import lang.temper.common.LeftOrRight
import lang.temper.common.Log
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.InternalModularName
import lang.temper.name.ResolvedName
import lang.temper.name.Temporary
import lang.temper.type.StaticType
import lang.temper.type.TypeContext
import lang.temper.type.WellKnownTypes
import lang.temper.type.excludeBubble
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.DefaultJumpSpecifier
import lang.temper.value.ErrorFn
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.InnerTree
import lang.temper.value.JumpLabel
import lang.temper.value.LeafTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.NamedJumpSpecifier
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TProblem
import lang.temper.value.Tree
import lang.temper.value.TreeTemplate
import lang.temper.value.UnresolvedJumpSpecifier
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.initSymbol
import lang.temper.value.isAssignment
import lang.temper.value.toPseudoCode
import lang.temper.value.vVarSymbol
import lang.temper.value.void

private enum class ResultNeeded(val yes: Boolean) {
    No(false),
    Yes(true),
}

/**
 * Allocates temporaries so that blocks whose results are needed can be replaced with
 * a reference to a temporary that has the necessary result.
 *
 * A block may have many results.  A block with nested conditionals, for example:
 *
 *     if (b1) {
 *       f()
 *     } else {
 *       if (b2) {
 *         foo();
 *         g()
 *       } else {
 *         x = do { h() }
 *       }
 *     }
 *
 * In that block, `f()`, `g()`, and `x = do { h() }` are all results.
 *
 * It is convenient to recurse into the `do { h() }` first.
 * That way, if a temporary is allocated for `h()`, we can reuse it to capture
 * `f()` and `g()`, and swap around the assignment so that we never end up creating
 * a nested assignment like `x = t#0 = h()`.
 *
 * Assuming temporary of `do { h() }` allocates the temporary `t#0`, we can associate
 * `t#0` with both the result of this block and the result of the nested block and
 * produce a weavable structure like:
 *
 *     if (b1) {
 *       t#0 = f()
 *     } else {
 *       if (b2) {
 *         foo();
 *         t#0 = g()
 *       } else {
 *         do { t#0 = h() };
 *         x = t#0
 *       }
 *     }
 *
 * Note the swap at the end to avoid nesting assignments.
 * We capture `t#0`, and ensure that `x` and `t#0` are both assigned the
 * same value, but do not require that the assignment to `t#0` is the last
 * assignment along all paths.
 *
 * The actual declaration of `t#0`, `let t#0;` does not appear here.
 * It is up to potential users of the block result to identify an appropriate scope
 * in which to declare `t#0`.
 */
internal class CaptureBlockResultsInTemporaries(
    private val root: BlockTree,
    private val typeContext: TypeContext,
    private val varNames: Set<ResolvedName>,
    private val resultsAlreadyCaptured: Boolean,
) {
    val capturedBlocks = mutableMapOf<BlockTree, CaptureResult>()

    fun capture() {
        val rn = if (resultsAlreadyCaptured) {
            ResultNeeded.No
        } else {
            ResultNeeded.Yes
        }
        var captureDetails = capture(root, rn)
        val result = captureDetails.result
        if (result != null) {
            val flow = structureBlock(root)
            addCaptureResultToClause(root, flow.controlFlow, result) { _, f ->
                f()
            }
            captureDetails = when (result) {
                is KnownValueCaptureResult -> captureDetails.copy(result = null)
                is NameCaptureResult -> captureDetails.copy(
                    result = null,
                    undeclared = captureDetails.undeclared.filter { it.name != result.capturedIn },
                )
            }
        }
        check(captureDetails.undeclared.isEmpty()) {
            "$captureDetails in ${root.toPseudoCode()} at ${root.document.context.formatPosition(root.pos)}"
        }
    }

    private fun capture(
        t: Tree,
        rn: ResultNeeded,
        suggestion: PendingDeclaration? = null,
    ): CaptureDetails = when (t) {
        is RightNameLeaf -> {
            val name = t.content as? InternalModularName
            if (rn.yes && name is ResolvedName && name !in varNames) {
                val type = t.passType
                CaptureDetails(NameCaptureResult(name, type), listOf(), setOf())
            } else {
                CaptureDetails.empty
            }
        }
        is ValueLeaf -> when (rn) {
            ResultNeeded.Yes -> CaptureDetails(
                KnownValueCaptureResult(t.content, t.passType),
                listOf(),
                setOf(),
            )
            ResultNeeded.No -> CaptureDetails.empty
        }
        is LeafTree -> CaptureDetails.empty // Nothing to do
        is BlockTree -> {
            // Blocks are what we need to capture, and we need to walk through the
            // control flow structure so that we can put temporaries in narrow scopes.
            val stmtBlock = structureBlock(t).controlFlow
            capture(t, stmtBlock, rn).also { captureDetails ->
                if (captureDetails.result != null) {
                    capturedBlocks[t] = captureDetails.result
                }
            }
        }
        is InnerTree -> {
            var result: CaptureResult? = null
            val undeclared = mutableListOf<PendingDeclaration>()
            val mightBeReassigned = mutableSetOf<InternalModularName>()

            var needToRecurse = true
            if (isAssignment(t)) {
                val (_, lhs, rhs) = t.children
                val assignedName = (lhs as? NameLeaf)?.content as? ResolvedName
                if (assignedName is InternalModularName && assignedName !in varNames) {
                    result = NameCaptureResult(assignedName, lhs.passType)
                }
                val lhsDetails = capture(lhs, ResultNeeded.No)
                val rhsDetails = capture(rhs, ResultNeeded.Yes, suggestion = suggestion)
                if (result == null && rn.yes) {
                    // This allows for chained assignments to share one temporary variable.
                    //
                    //     a = b = c = {...}
                    //
                    // ->
                    //
                    //     c = {...; t#0}
                    //     b = t#0;
                    //     a = t#0;
                    //     t#0;
                    result = rhsDetails.result
                    if (result == null) {
                        val rhsType = rhs.passType
                        val resultName = when {
                            suggestion?.type != null && suggestion.type == rhsType -> suggestion.name
                            else -> t.document.nameMaker.unusedTemporaryName(Temporary.defaultNameHint)
                                .also { undeclared.add(PendingDeclaration(it, rhsType)) }
                        }
                        val edge = t.incoming!!
                        edge.replace { pos ->
                            // t#0 = rhs;
                            // lhs = t#0
                            Block(pos) {
                                Assign(resultName, rhsType) {
                                    Replant(freeTree(rhs))
                                }
                                Replant(freeTree(t))
                            }
                        }
                        t.edge(2).replace {
                            Rn(t.pos.rightEdge, resultName, rhsType)
                        }
                        result = NameCaptureResult(resultName, rhsType)
                        capturedBlocks[edge.target as BlockTree] = result
                    }
                }
                needToRecurse = false

                for (d in listOf(lhsDetails, rhsDetails)) {
                    undeclared.addAll(d.undeclared)
                    mightBeReassigned.addAll(d.mightBeReassigned)
                }
            } else if (t is DeclTree) {
                result = CaptureResult.voidCaptureResult
            }

            if (needToRecurse) {
                val (childIndexStart, childIndexLimit, needResultForChildren) = when (t) {
                    // For FunTrees, the body is a distinct root.
                    // This pass happens after argument default expressions are incorporated
                    // into the body, so this should not pull blocks out of the
                    // default expression into the surrounding root block.
                    is FunTree -> Triple(0, t.size - 1, ResultNeeded.Yes)
                    is EscTree -> Triple(0, t.size, ResultNeeded.No)
                    is DeclTree -> Triple(1, t.size, ResultNeeded.Yes)
                    else -> Triple(0, t.size, ResultNeeded.Yes)
                }

                var siblingCaptureLimit = childIndexStart
                var childIndex = childIndexStart
                while (childIndex < childIndexLimit) {
                    val child = t.child(childIndex)
                    val details = capture(child, needResultForChildren)
                    undeclared.addAll(details.undeclared)
                    mightBeReassigned.addAll(details.mightBeReassigned)

                    val result = details.result
                    if (result is NameCaptureResult && details.undeclaredNamed(result.capturedIn) != null) {
                        // Something is being pulled through
                        val siblingCaptureDeclarations =
                            capturePrecedingSiblings(t, siblingCaptureLimit..<childIndex)
                        undeclared.addAll(siblingCaptureDeclarations)
                        siblingCaptureLimit = childIndex + 1
                    }

                    childIndex += 1
                }
            }

            CaptureDetails(result, undeclared.toList(), mightBeReassigned.toSet())
        }
    }

    /**
     * Capture siblings to the left in temporaries so that we can ensure that things are
     * evaluated in order.
     */
    private fun capturePrecedingSiblings(tree: InnerTree, siblingIndices: IntRange): List<PendingDeclaration> {
        val document = tree.document
        val undeclared = mutableListOf<PendingDeclaration>()

        fun alias(edge: TEdge) {
            val target = edge.target
            val type = target.passType
            val alias = document.nameMaker.unusedTemporaryName(Temporary.defaultNameHint)
            edge.replace { pos ->
                Block(pos) {
                    Assign(pos, alias, type) {
                        Replant(freeTree(target))
                    }
                }
            }
            capturedBlocks[edge.target as BlockTree] = NameCaptureResult(alias, type)
            undeclared.add(PendingDeclaration(alias, type))
        }

        for (i in siblingIndices) {
            if (shouldExtractForWeave(tree, i, varNames)) {
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
                                shouldExtractForWeave(sibling, declChildEdge.edgeIndex, varNames)
                            ) {
                                alias(declChildEdge)
                            }
                        }
                        continue
                    }
                }

                alias(edge)
            }
        }

        return undeclared.toList()
    }

    private fun captureCondition(
        block: BlockTree,
        cf: ControlFlow.Conditional,
    ): CaptureDetails {
        val condEdge = block.dereference(cf.condition)
            ?: return CaptureDetails.empty
        val cond = condEdge.target
        val resultAvailableWithoutCapture = when (cond) {
            is ValueLeaf, is RightNameLeaf, is CallTree, is FunTree -> true
            is BlockTree,
            is DeclTree,
            is EscTree,
            is LeftNameLeaf,
            is StayLeaf,
            -> false
        }
        val resultNeeded = if (resultAvailableWithoutCapture) {
            ResultNeeded.No
        } else {
            ResultNeeded.Yes
        }
        // Common handling for some corner cases around refs that are not in Stmts.
        return capture(cond, resultNeeded)
    }

    private fun capture(
        block: BlockTree,
        cf: ControlFlow,
        rn: ResultNeeded,
        suggestion: PendingDeclaration? = null,
    ): CaptureDetails {
        return when (cf) {
            is ControlFlow.Break,
            is ControlFlow.Continue,
            -> CaptureDetails.empty
            is ControlFlow.Labeled -> capture(block, cf.stmts, rn, suggestion)
            is ControlFlow.If -> {
                val condCaptureDetails = captureCondition(block, cf)

                val thenClause = cf.thenClause
                val elseClause = cf.elseClause
                joinClauseCaptures(
                    block,
                    condCaptureDetails,
                    thenClause,
                    elseClause,
                    rn,
                    suggestion,
                    commonDeclarationsAsVar = false,
                )
            }
            is ControlFlow.Loop -> {
                val fixedLoop = fixupLoopBeforeCapture(block, cf)
                if (fixedLoop != null) {
                    (cf.parent as ControlFlow.StmtBlock).withMutableStmtList { mutStmts ->
                        mutStmts[mutStmts.indexOf(cf)] = fixedLoop
                    }
                    return capture(block, fixedLoop, rn, suggestion)
                }

                // There's no need to capture the condition, since if there was,
                // it would've been incorporated into the loop body above.

                val allUndeclared = mutableListOf<PendingDeclaration>()
                for (clause in cf.clauses) {
                    allUndeclared.addAll(capture(block, clause, ResultNeeded.No).undeclared)
                }

                CaptureDetails(
                    CaptureResult.voidCaptureResult,
                    allUndeclared.toList(),
                    mightBeReassigned = buildSet {
                        allUndeclared.mapTo(this) { it.name }
                    },
                )
            }
            is ControlFlow.OrElse -> {
                val orClause = cf.orClause.stmts
                val elseClause = cf.elseClause
                joinClauseCaptures(
                    block,
                    null,
                    orClause,
                    elseClause,
                    rn,
                    suggestion,
                    commonDeclarationsAsVar = true,
                )
            }
            is ControlFlow.Stmt -> {
                val edge = block.dereference(cf.ref)
                if (edge != null) {
                    var captureDetails = capture(edge.target, rn, suggestion = suggestion)
                    if (captureDetails.result == null && rn.yes) {
                        // allocate a temporary if none available
                        val type = edge.target.passType
                        val tmpName = when {
                            suggestion != null && type != null && suggestion.type == type -> suggestion.name
                            else -> block.document.nameMaker.unusedTemporaryName(Temporary.defaultNameHint)
                        }

                        captureDetails = captureDetails.copy(
                            result = NameCaptureResult(tmpName, type),
                            undeclared = captureDetails.undeclared + listOf(PendingDeclaration(tmpName, type)),
                        )
                        val tree = edge.target
                        if (isAssignment(tree)) {
                            // Split an assignment in two.
                            //
                            //     nameWeCannotUseAsTheResultName = foo()
                            //
                            // ->
                            //
                            //     nameWeCannotUseAsTheResultName = foo();
                            //     t#0 = nameWeCannotUseAsTheResultName
                            //
                            // See code in the StmtBlock branch that handles iteration
                            // over stmts even if one is inserted after this.
                            val left = tree.child(1)
                            val containingStmtBlock = cf.parent!!
                            containingStmtBlock.withMutableStmtList { mutSiblings ->
                                val stmtListIndex = mutSiblings.indexOf(cf)
                                val edgeIndex = block.size
                                val pos = cf.pos.rightEdge
                                block.insert(edgeIndex) {
                                    Assign(pos, tmpName, left.passType) {
                                        if (left is NameLeaf) {
                                            Replant(
                                                left.copyRight().also {
                                                    it.typeInferences = left.typeInferences
                                                },
                                            )
                                        } else {
                                            val problem = LogEntry(
                                                Log.Error,
                                                MessageTemplate.MalformedAssignment,
                                                pos,
                                                listOf(),
                                            )
                                            Call(errorFnTypeInferences) {
                                                V(Value(ErrorFn), errorFnTypeInferences.variant)
                                                V(Value(problem, TProblem), WellKnownTypes.problemType)
                                            }
                                        }
                                    }
                                }
                                mutSiblings.add(
                                    stmtListIndex + 1,
                                    ControlFlow.Stmt(BlockChildReference(edgeIndex, pos)),
                                )
                            }
                        } else {
                            edge.replace { pos ->
                                Assign(pos, tmpName, tree.passType) {
                                    Replant(freeTarget(edge))
                                }
                            }
                        }
                    }
                    captureDetails
                } else {
                    CaptureDetails.empty
                }
            }
            is ControlFlow.StmtBlock -> {
                var result: CaptureResult? = null
                val undeclared = mutableMapOf<InternalModularName, StaticType?>()
                val mightBeReassigned = mutableSetOf<InternalModularName>()
                var i = 0
                val stmts = cf.stmts
                while (i in stmts.indices) {
                    // Iterate allowing recursive calls to insert after current
                    val stmt = stmts[i++]
                    val isLast = i == stmts.size
                    val suggestionForStmt = if (isLast) { suggestion } else { null }
                    val rnForStmt = if (isLast) { rn } else { ResultNeeded.No }
                    val stmtDetails = capture(block, stmt, rnForStmt, suggestionForStmt)
                    if (stmtDetails.result != null && isLast) {
                        result = stmtDetails.result
                    }
                    for ((tmp, type) in stmtDetails.undeclared) {
                        undeclared[tmp] = type
                    }
                    mightBeReassigned.addAll(stmtDetails.mightBeReassigned)
                }

                // We can declare some names now.
                val declaring = (result as? NameCaptureResult)?.capturedIn?.let {
                    undeclared.keys - it
                } ?: undeclared.keys.toSet()

                if (declaring.isNotEmpty()) {
                    val pos = block.pos.leftEdge
                    val newDeclStmts = declaring.map { name ->
                        val nameMightBeReassigned = name in mightBeReassigned
                        val type = undeclared.remove(name)
                        mightBeReassigned.remove(name)
                        val edgeIndex = block.size
                        block.insert(at = edgeIndex) {
                            Decl(pos) {
                                Ln(name, type = type)
                                if (nameMightBeReassigned) {
                                    V(vVarSymbol)
                                    V(void)
                                }
                            }
                        }
                        ControlFlow.Stmt(BlockChildReference(edgeIndex, pos))
                    }
                    cf.withMutableStmtList { mutStmts ->
                        mutStmts.addAll(0, newDeclStmts)
                    }
                }
                CaptureDetails(
                    result,
                    undeclared.map { PendingDeclaration(it.key, it.value) },
                    mightBeReassigned,
                )
            }
        }
    }

    private fun joinClauseCaptures(
        block: BlockTree,
        condCaptureDetails: CaptureDetails?,
        firstClause: ControlFlow.StmtBlock,
        secondClause: ControlFlow.StmtBlock,
        rn: ResultNeeded,
        firstSuggestion: PendingDeclaration?,
        commonDeclarationsAsVar: Boolean,
    ): CaptureDetails {
        val firstDetails = capture(block, firstClause, rn, firstSuggestion)

        // Try to reuse any temporary allocated for the first branch when doing the second.
        // Instead of as below, passing the suggestion allows a pattern like the second:
        //
        // if (cond) {
        //   t#0 = f();  // t#0 allocated for this clause
        //   t#2 = t#0;  // Inserted below to merge branches into one result.
        // } else {
        //   t#1 = g();  // t#1 separately allocated
        //   t#2 = t#1;  // Ditto inserted below
        // }
        //
        // Alternatively, the suggestion allows for this one third the temporaries.
        //
        // if (cond) {
        //   t#0 = f();  // t#0 allocated for this clause
        // } else {
        //   t#0 = g();  // t#0 passed in as suggestion and reused
        // }
        val suggestion = (firstDetails.result as? NameCaptureResult)?.let { r ->
            firstDetails.undeclaredNamed(r.capturedIn)
            // If it's in undeclared, then the name was allocated for firstDetails and
            // is not a reuse of an a priori name.
        }

        val secondDetails = capture(block, secondClause, rn, suggestion)

        val firstResult = firstDetails.result
        val secondResult = secondDetails.result

        val allUndeclared = mutableListOf<PendingDeclaration>()
        val allMightBeReassigned = mutableSetOf<InternalModularName>()
        for (d in listOfNotNull(condCaptureDetails, firstDetails, secondDetails)) {
            allUndeclared.addAll(d.undeclared)
            allMightBeReassigned.addAll(d.mightBeReassigned)
        }
        if (commonDeclarationsAsVar) {
            val firstUndeclared = buildSet {
                firstDetails.undeclared.mapTo(this) { it.name }
            }
            for (pd in secondDetails.undeclared) {
                if (pd.name in firstUndeclared) {
                    allMightBeReassigned.add(pd.name)
                }
            }
        }

        val needsResult = firstResult != null || secondResult != null
        // If one branch does not terminate, then it's possible only one has a terminal edge.
        val joinedResult: CaptureResult? = when {
            !needsResult -> null
            firstResult == null -> secondResult
            secondResult == null -> firstResult
            firstResult == secondResult -> firstResult
            else -> {
                /*
                 * We might have a situation like the below where two different
                 * names were reused as result names.
                 *
                 * ```temper
                 * let x, y;
                 * if (cond) {
                 *   x = f()
                 * } else {
                 *   y = g()
                 * }
                 * ```
                 *
                 * In that case, we can allocate a temporary and make changes like this.
                 *
                 * ```patch
                 *  let x, y;
                 *  if (cond) {
                 *    x = f();
                 * +  t#0 = x
                 *  } else {
                 *    y = g();
                 * +  t#0 = y
                 *  }
                 * ```
                 */
                var tmpName: InternalModularName? = null
                if (firstResult.type == null || firstResult.type != secondResult.type) {
                    // Allocate a fresh temporary unless we know that the results' types union well.
                    tmpName = block.document.nameMaker.unusedTemporaryName(Temporary.defaultNameHint)
                }
                val resultsAndClausesToAdjust =
                    buildList<Triple<CaptureDetails, CaptureResult, ControlFlow.StmtBlock>> {
                        add(Triple(firstDetails, firstResult, firstClause))
                        add(Triple(secondDetails, secondResult, secondClause))
                        // See if we can reuse an already allocated name.
                        if (tmpName == null) {
                            for (i in indices) {
                                val (details, result, _) = this[i]
                                if (
                                    result is NameCaptureResult &&
                                    // Name consistency check which assumes pre-allocation above didn't happen.
                                    details.undeclaredNamed(result.capturedIn)?.type == result.type
                                ) {
                                    tmpName = result.capturedIn
                                    // Don't adjust the one already using the name
                                    removeAt(i)
                                    break
                                }
                            }
                        }
                    }
                if (tmpName == null) {
                    tmpName = block.document.nameMaker.unusedTemporaryName(Temporary.defaultNameHint)
                }
                if (commonDeclarationsAsVar) {
                    allMightBeReassigned.add(tmpName)
                }
                for ((_, result, clause) in resultsAndClausesToAdjust) {
                    addCaptureResultToClause(
                        block, clause, result,
                        replaceLastIf = { edge ->
                            val target = edge.target
                            target is RightNameLeaf &&
                                target.content == (result as? NameCaptureResult)?.capturedIn
                        },
                    ) { pos, buildResult ->
                        Assign(pos, tmpName, result.type) {
                            buildResult()
                        }
                    }
                }

                val thenType = firstResult.type
                val elseType = secondResult.type
                val lub = when {
                    elseType == null -> thenType
                    thenType == null -> elseType
                    else -> typeContext.lub(thenType, elseType, simplify = true)
                }
                allUndeclared.add(PendingDeclaration(tmpName, lub))
                NameCaptureResult(tmpName, lub)
            }
        }

        // If any of the clauses need a temporary that is not part of the joined result,
        // declare it there.
        if ((firstResult as? NameCaptureResult)?.capturedIn != (secondResult as? NameCaptureResult)?.capturedIn) {
            for ((d, sb) in listOf(firstDetails to firstClause, secondDetails to secondClause)) {
                val result = d.result
                val name = (result as? NameCaptureResult)?.capturedIn ?: continue
                if (
                    d.undeclaredNamed(name) != null &&
                    name != (joinedResult as? NameCaptureResult)?.capturedIn &&
                    name != suggestion
                ) {
                    val isVar = name in allMightBeReassigned
                    if (isVar) { allMightBeReassigned.remove(name) }
                    // Predeclare result.
                    allUndeclared.removeIf { it.name == name }
                    sb.withMutableStmtList { mutStmts ->
                        val blockChildIndex = block.size
                        block.insert(at = blockChildIndex) {
                            Decl(sb.pos.leftEdge) {
                                Ln(sb.pos.leftEdge, name, result.type)
                                if (isVar) {
                                    V(vVarSymbol)
                                    V(void)
                                }
                            }
                        }
                        mutStmts.add(
                            0,
                            ControlFlow.Stmt(
                                BlockChildReference(blockChildIndex, sb.pos.leftEdge),
                            ),
                        )
                    }
                }
            }
        }

        return CaptureDetails(joinedResult, allUndeclared, allMightBeReassigned)
    }

    /**
     * For loops, we can't capture the condition the
     * same way we do for a `if` loop, by moving it
     * before the `if`, because it needs to be evaluated every time.
     *
     *     if (do { cond }) { ... } else { ... }
     *
     * ->
     *
     *     let t#0;
     *     do { t#0 = cond };
     *     if (t#0) { ... } else { ... }
     *
     * If we tried to do that for a loop, we'd change semantics.
     *
     *     while (do { cond }) { body }
     *
     * ->
     *
     *     let t#0;
     *     do { t#0 = cond };
     *     while (t#0) { body } // ERROR: condition not computed per iteration.
     *
     * Instead, we need to convert the condition to `true` and incorporate it into
     * the body in a way that respects the meaning of `continue`.
     *
     *     while (do { cond }) { body }
     *
     * ->
     *
     *     while (true) { if (do { cond }) { break }; body }
     *
     * For `do...while` loops, we can similarly insert the `if` after the body.
     *
     *     while (true) { body; if (do { cond }) { break } }
     *
     * But, as does CF simplification, we do it in a way that preserves the meaning
     * of `continue` in body.
     */
    private fun fixupLoopBeforeCapture(block: BlockTree, cf: ControlFlow.Loop): ControlFlow.Loop? {
        var hasNestedBlock = false
        block.dereference(cf.condition)?.target?.let { condTree ->
            TreeVisit.startingAt(condTree)
                .forEach {
                    when (it) {
                        is BlockTree -> {
                            hasNestedBlock = true
                            VisitCue.AllDone
                        }
                        is FunTree -> VisitCue.SkipOne
                        else -> VisitCue.Continue
                    }
                }
                .visitPreOrder()
        }
        if (!hasNestedBlock) { return null }

        // Two strategies:
        // Left:  `while (cond) { body }` ->
        //        `while (true) { if (cond) {} else { break }; body }
        // Right: `do { body } while (cond);` ->
        //        `while (true) { continue#123: do { body }; if (cond) {} else { break }; }`
        val sideToInsertCondition = cf.checkPosition
        val newBody = when (sideToInsertCondition) {
            LeftOrRight.Left -> cf.body.deepCopy()
            LeftOrRight.Right -> LoopBodyRewriteHelper(block, cf).rewriteBody()
        }
        val insertPos = when (sideToInsertCondition) {
            LeftOrRight.Left -> 0
            LeftOrRight.Right -> newBody.stmts.size
        }
        val condPos = cf.condition.pos
        val ifCond = ControlFlow.If(
            condPos,
            cf.condition,
            thenClause = ControlFlow.StmtBlock(condPos.rightEdge, listOf()),
            elseClause = ControlFlow.StmtBlock(
                condPos.rightEdge,
                listOf(ControlFlow.Break(condPos, DefaultJumpSpecifier)),
            ),
        )
        newBody.withMutableStmtList { mutStmts ->
            mutStmts.add(insertPos, ifCond)
        }

        val newCondEdgeIndex = block.size
        val condTree = ValueLeaf(block.document, cf.condition.pos.leftEdge, TBoolean.valueTrue)
            .also {
                it.typeInferences = BasicTypeInferences(WellKnownTypes.booleanType, listOf())
            }
        block.add(condTree)
        val newCondition = BlockChildReference(newCondEdgeIndex, condTree.pos)

        return ControlFlow.Loop(
            pos = cf.pos,
            label = cf.label,
            checkPosition = LeftOrRight.Left,
            condition = newCondition,
            body = newBody,
            increment = cf.increment.deepCopy(),
        )
    }

    private fun addCaptureResultToClause(
        block: BlockTree,
        clause: ControlFlow.StmtBlock,
        result: CaptureResult,
        replaceLastIf: (TEdge) -> Boolean = { false },
        buildInsertion: Planting.(Position, Planting.() -> TreeTemplate<*>) -> TreeTemplate<*>,
    ) {
        val last = (clause.stmts.lastOrNull() as? ControlFlow.Stmt)?.ref?.let {
            block.dereference(it)
        }
        fun Planting.emplaceResult(pos: Position): TreeTemplate<*> =
            when (result) {
                is NameCaptureResult -> Rn(pos, result.capturedIn, result.type)
                is KnownValueCaptureResult -> V(pos, result.value, result.type)
            }
        if (last != null && replaceLastIf(last)) {
            last.replace { pos ->
                buildInsertion(clause.pos.rightEdge) {
                    emplaceResult(pos)
                }
            }
        } else {
            val indexOfTmpAssignment = block.size
            val pos = clause.pos.rightEdge
            block.insert(indexOfTmpAssignment) {
                buildInsertion(pos) {
                    emplaceResult(pos)
                }
            }
            clause.withMutableStmtList {
                it.add(ControlFlow.Stmt(BlockChildReference(indexOfTmpAssignment, pos)))
            }
        }
    }
}

private data class PendingDeclaration(
    val name: InternalModularName,
    val type: StaticType?,
)

internal sealed class CaptureResult {
    abstract val type: StaticType?
    companion object {
        val voidCaptureResult = KnownValueCaptureResult(void, WellKnownTypes.voidType)
    }
}

internal data class NameCaptureResult(
    val capturedIn: InternalModularName,
    override val type: StaticType?,
) : CaptureResult()

internal data class KnownValueCaptureResult(
    val value: Value<*>,
    override val type: StaticType?,
) : CaptureResult()

private data class CaptureDetails(
    val result: CaptureResult?,
    val undeclared: List<PendingDeclaration>,
    /**
     * If a temporary is used in a loop but not declared in its body, then it needs
     * to be `var`.  This may occur in complex increment clauses.
     */
    val mightBeReassigned: Set<InternalModularName>,
) {
    operator fun plus(other: CaptureDetails): CaptureDetails {
        val sameResult = this.result == other.result
        val allUndeclared = mutableMapOf<InternalModularName, StaticType?>()
        val allMightBeReassigned = mutableSetOf<InternalModularName>()
        for (cd in listOf(this@CaptureDetails, other)) {
            for ((tmp, type) in cd.undeclared) {
                allUndeclared[tmp] = type
            }
        }
        return CaptureDetails(
            if (sameResult) { result } else { null },
            allUndeclared.map { PendingDeclaration(it.key, it.value) },
            allMightBeReassigned.toSet(),
        )
    }

    fun undeclaredNamed(name: InternalModularName): PendingDeclaration? =
        undeclared.firstOrNull { it.name == name }

    override fun toString(): String = buildString {
        append("CaptureDetails(")
        var needComma = false
        if (result != null) {
            append("result=")
            when (result) {
                is KnownValueCaptureResult ->
                    append("Known(").append(result.value).append(")")
                is NameCaptureResult -> append("Name(").append(result.capturedIn).append(")")
            }
            needComma = true
        }
        if (undeclared.isNotEmpty()) {
            if (needComma) { append(", ") }
            append("undeclared=").append(undeclared)
            needComma = true
        }
        if (mightBeReassigned.isNotEmpty()) {
            if (needComma) { append(", ") }
            append("MightBeReassigned=").append(mightBeReassigned)
        }
        append(")")
    }

    companion object {
        val empty: CaptureDetails = CaptureDetails(null, listOf(), setOf())
    }
}

private val Tree.passType: StaticType? get() =
    this.typeInferences?.type?.let {
        excludeBubble(it)
    }

private val errorFnTypeInferences: CallTypeInferences = run {
    val problemType = WellKnownTypes.problemType2
    val neverProblem = MkType2(WellKnownTypes.neverTypeDefinition)
        .actuals(listOf(problemType))
        .get()
    CallTypeInferences(
        hackMapNewStyleToOld(neverProblem),
        Signature2(
            returnType2 = neverProblem,
            hasThisFormal = false,
            requiredInputTypes = listOf(),
            optionalInputTypes = listOf(problemType),
        ),
        mapOf(),
        listOf(),
    )
}

private class LoopBodyRewriteHelper(
    val block: BlockTree,
    val loop: ControlFlow.Loop,
) {
    val continueLabel: Lazy<JumpLabel> = lazy {
        block.document.nameMaker.unusedTemporaryName("continue")
    }
    val loopLabel = loop.label

    fun rewriteBody(): ControlFlow.StmtBlock {
        val bodyCopy = loop.body.deepCopy()
        rewriteContinues(block, bodyCopy, defaultMatches = true)
        return if (continueLabel.isInitialized()) {
            ControlFlow.StmtBlock(
                bodyCopy.pos,
                listOf(
                    ControlFlow.Labeled(
                        bodyCopy.pos, continueLabel.value, continueLabel.value, bodyCopy,
                    ),
                ),
            )
        } else {
            bodyCopy
        }
    }

    fun rewriteContinues(bl: BlockTree, cf: ControlFlow, defaultMatches: Boolean) {
        if (cf is ControlFlow.Continue) {
            val shouldReplace = when (val target = cf.target) {
                is DefaultJumpSpecifier -> defaultMatches
                is NamedJumpSpecifier -> target.label == loopLabel
                is UnresolvedJumpSpecifier -> TODO("convert to error node")
            }
            if (shouldReplace) {
                val parent = cf.parent as ControlFlow.StmtBlock
                parent.withMutableStmtList { mutStmts ->
                    mutStmts[mutStmts.indexOf(cf)] = ControlFlow.Continue(
                        cf.pos, NamedJumpSpecifier(continueLabel.value),
                    )
                }
            }
        }
        var defaultMatchesForRecursion = defaultMatches
        if (cf is ControlFlow.Loop) {
            defaultMatchesForRecursion = false
        }
        cf.ref?.let {
            val edge = bl.dereference(it)
            if (edge != null) {
                rewriteContinues(edge.target, defaultMatchesForRecursion)
            }
        }
        for (clause in cf.clauses) {
            rewriteContinues(bl, clause, defaultMatchesForRecursion)
        }
    }

    fun rewriteContinues(t: Tree, defaultMatches: Boolean) {
        if (t is BlockTree) {
            rewriteContinues(t, structureBlock(t).controlFlow, defaultMatches)
        } else {
            for (c in t.children) {
                rewriteContinues(c, defaultMatches)
            }
        }
    }
}
