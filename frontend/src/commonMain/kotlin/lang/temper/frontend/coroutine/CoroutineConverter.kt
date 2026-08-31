package lang.temper.frontend.coroutine

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.Assign
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.BuiltinLogicalOperators
import lang.temper.builtin.NotNullFn
import lang.temper.builtin.Types
import lang.temper.common.Console
import lang.temper.common.Either
import lang.temper.common.KBitSet
import lang.temper.common.abbreviate
import lang.temper.common.addTransitiveClosure
import lang.temper.common.bitIndices
import lang.temper.common.ignore
import lang.temper.common.intersects
import lang.temper.env.Export
import lang.temper.frontend.AdaptGeneratorFn
import lang.temper.frontend.core.CoreModule
import lang.temper.frontend.structureBlock
import lang.temper.interp.LongLivedUserFunction
import lang.temper.interp.New
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.InternalModularName
import lang.temper.name.NameMaker
import lang.temper.name.ResolvedName
import lang.temper.name.Temporary
import lang.temper.stage.Stage
import lang.temper.type.FunctionType
import lang.temper.type.InvalidType
import lang.temper.type.MkType
import lang.temper.type.StaticType
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.mapType
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockPlanting
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.ConservativeFailure
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.ErrorFn
import lang.temper.value.FunTree
import lang.temper.value.InnerTree
import lang.temper.value.JumpLabel
import lang.temper.value.MacroValue
import lang.temper.value.MaximalPath
import lang.temper.value.MaximalPathIndex
import lang.temper.value.MaximalPaths
import lang.temper.value.NameLeaf
import lang.temper.value.Planting
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.TreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.YieldingFnKind
import lang.temper.value.ZeroValueRecord
import lang.temper.value.ZeroValues
import lang.temper.value.disassembleYieldingCall
import lang.temper.value.emptyValue
import lang.temper.value.fnParsedName
import lang.temper.value.fnSymbol
import lang.temper.value.forwardMaximalPaths
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.isAssignment
import lang.temper.value.isTypeAngleCall
import lang.temper.value.mapControlFlowPlanting
import lang.temper.value.simplifyControlFlow
import lang.temper.value.ssaSymbol
import lang.temper.value.toPseudoCode
import lang.temper.value.typeFromSignature
import lang.temper.value.typeSymbol
import lang.temper.value.vReturnDeclSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.vVarSymbol
import lang.temper.value.varSymbol
import lang.temper.value.void
import kotlin.math.max
import lang.temper.type.WellKnownTypes as WKT

/**
 * Performs a state machine conversion on a coroutine function body
 * so that coroutines can be lowered to simpler functions.
 *
 * This is an optional, backend-driven transform that copies trees, so
 * the caller is responsible for either using the original or the
 * transformed, but not both.
 */
fun convertCoroutineFunctionBodyToRegularFunctionBody(
    block: BlockTree,
    nameMaker: NameMaker,
    outerFnOutputName: ResolvedName,
    outputDecl: DeclTree,
    adapterFn: AdaptGeneratorFn,
    generatorType: Type2,
    generatorSig: Signature2,
    debugConsole: Console? = null,
    /**
     * True to skip simplifying the control flow of the converted function body.
     * Simplification often gets rid of the `break fn_123` turning them into
     * unlabelled breaks.
     *
     * There's no reason not to do that in production, but in the test harness
     * it's nice to be able to test exactly the code produced, so this is
     * off by default in *CoroutineConverterTest*.
     */
    skipSimplifyControlFlow: Boolean = false,
): BlockTree {
    val converter = CoroutineConverter(
        block,
        nameMaker,
        outerFnOutputName,
        outputDecl,
        adapterFn,
        generatorType,
        generatorSig,
        debugConsole,
        skipSimplifyControlFlow = skipSimplifyControlFlow,
    )
    return converter.convert()
}

private class CoroutineConverter(
    blockShared: BlockTree,
    val ccNameMaker: NameMaker,
    val outerFnOutputName: ResolvedName,
    val outputDecl: DeclTree,
    val adapterFn: AdaptGeneratorFn,
    val generatorType: Type2,
    val generatorSig: Signature2,
    val debugConsole: Console?,
    val skipSimplifyControlFlow: Boolean,
) {
    val block = blockShared.copy(copyInferences = true) as BlockTree

    fun convert(): BlockTree {
        if (debugConsole != null) {
            debugConsole.group("block") {
                block.toPseudoCode(debugConsole.textOutput)
            }
            debugConsole.log("outputName=${outputDecl.parts!!.name.content}")
            debugConsole.log("adapterFn=$adapterFn")
            debugConsole.log("generatorType=$generatorType")
        }
        return buildStateMachine()
    }

    private fun buildStateMachine(): BlockTree {
        // Step 1: Make a copy of the coro body so that we can destructively modify it.
        // For example, move declarations forward so that they are most likely to fit
        // within one basic block.
        //
        //    let x;
        //    await p;
        //    x = f();
        //    console.log(x);
        //
        // Moving `let x` after `await p` makes it more likely that a single basic
        // block contains the declaration of a local and all uses of that local.
        moveDeclarationsForward()
        debugConsole?.group("after step 1") {
            block.toPseudoCode(debugConsole.textOutput)
        }

        // Step 2: Inspect nested functions.
        // Nested functions may close over locals. If a basic block uses a
        // nested function declared in another basic block, then not only
        // does the function need to be hoisted out, but so do all locals it uses,
        // and transitively other nested functions it uses.
        //
        // When a nested function escapes by being passed as an argument,
        // the conservative assumption is that it could be called at any later point.
        //
        // From this, we compute a set of locals that each function needs if it is
        // used across basic blocks.
        inspectNestedFns()
        debugConsole?.group("inspect nested fns") {
            nestedFnToLocalsNeeded.forEach { (name, names) ->
                val init = nestedFnInitializers[name]
                debugConsole.log("$name -> $names = ${init?.toPseudoCode()}")
            }
        }

        // Step 3: Isolate sub-blocks.
        // Some nested block structures do not yield internally, so do not need to be
        // pulled apart into basic blocks.  We can just treat them as opaque subroutines.
        //
        // For example, in the below it'd be convenient to have three basic blocks
        // instead of a lot internally for the loop which must complete in one coroutine step:
        //
        //     let x = foo();
        //     yield x;
        //
        //     var y = 0;
        //     while (a()) {
        //       if (b()) { y += 1 } else { y *= 2 }
        //     }
        //
        //     bar();
        //     yield y;
        //
        // It'd be convenient to treat that loop as a member of one basic block instead of,
        // by the strict definition of basic block, basic blocks branching on the condition,
        // basic blocks internal to the body, and transitions back to the condition from the body end.
        //
        // Temporarily wrapping ControlFlow subtrees that do not internally have yielding calls
        // lets us present them as opaque boxes to the forwardMaximalPaths algo, as long as the
        // rebuild step below takes care to unpack those boxes.
        //
        //     // Basic block 0
        //     let x = foo();
        //     yield x;
        //     // -> Basic block 1
        //
        //     // Basic block 1
        //     var y = 0;
        //     do {  // <-- opaque box
        //       while (a()) {
        //         if (b()) { y += 1 } else { y *= 2 }
        //       }
        //     }
        //     bar();
        //     yield y;
        //     // -> Basic block 2
        //
        //     // Basic block 2
        //     // -> Final return of doneResult()
        isolateSubBlocks()

        // Step 4: Inspect basic blocks.
        // For each basic block, gather the information below needed by later steps.
        //
        // - names used in the block,
        // - nested functions used in the block
        // - from step 2, we can compute the conservative set of names used by the block.
        // - from the above, we can compute which names are
        // - if the basic block has a bubble transition, that target block is the
        //   exception/failure handler for that block.
        //
        // The outcome of this is a set of "cases".
        // Each basic block has one or two case indices:
        //
        // 1. The case index that starts its work.  When one path jumps to another,
        //    the jump is to the start of the destination path.
        // 2. Optionally, a follow-on index.
        //    If a basic block ends with a `yield`, AND it may conditionally jump,
        //    it has a follow-on case index that performs the extra condition
        //    checking logic AFTER resuming from the yield.
        //    The case index is set to the follow-on index BEFORE the yield so that
        //    the conditions are checked first, upon resuming.
        inspectBasicBlocks()
        debugConsole?.group("Basic blocks") {
            val details = PseudoCodeDetail(elideFunctionBodies = true)
            for (path in basicBlocks.maximalPaths) {
                debugConsole.group("Basic Block ${path.pathIndex}") {
                    for (el in path.elements) {
                        val edge = block.dereference(el.ref)
                        if (edge == null) {
                            debugConsole.log("???")
                        } else {
                            edge.target.toPseudoCode(debugConsole.textOutput, detail = details, singleLine = true)
                            debugConsole.textOutput.endLine()
                        }
                    }
                }
            }
            for (caseEntry in caseInfoMap) {
                debugConsole.group("${caseEntry.key}") {
                    val caseInfo = caseEntry.value
                    debugConsole.log(
                        "${caseInfo.basicBlockIndex} ${caseInfo.kind}, onBubble->${caseInfo.onBubble}, namesUsed=${
                            caseInfo.localNamesRequired
                        }",
                    )
                }
            }
        }

        // Step 5: Inspect names.
        // Every name used in the coro body is one of:
        //
        // - a free, externally defined name
        // - a local declared and used in one basic block
        // - a "hoisted" local, one that is shared by multiple basic blocks and which needs
        //   to be declared in a scope outside any one basic block so its state can
        //   persist across multiple coroutine turns.
        //
        // In addition to classifying each name, each in the last group is associated with a
        // set of basic blocks which end their scope.
        // To avoid preventing garbage collection of objects, at the end of a basic block,
        // each scope-ended hoisted variable is reset to its zero value.
        //
        // So for each hoisted name in the latter part, we need several pieces of information:
        //
        // - the original type.
        // - the adjusted type.  If the original's type does not have a well-known zero value,
        //   then to enable initializing and resetting it, its type in the translation is a
        //   nullable type.  This means that reads of it need to be wrapped in `notNull(...)`.
        // - the basic blocks that end its scope.
        inspectNames()
        debugConsole?.group("Local name info") {
            for ((name, info) in localNameInfo) {
                debugConsole.group("$name") {
                    when (info) {
                        is HoistedNameInfo -> debugConsole.log(
                            "hoisted: zeroValueRecord=${info.zeroValueRecord}",
                        )
                        is NotHoisted -> debugConsole.log("not hoisted")
                    }
                }
            }
        }

        // Step 6: Rebuild
        // The gross structure of a coroutine converter is:
        //
        // 1. Declare a case variable, an Int32, initially 0.
        // 2. Declare hoisted variables.
        // 3. A step function that includes:
        //
        //    a. captures the case variable and resets it to a placeholder value
        //    b. a nested function containing a large `match` over the case
        //       variable with a case for each basic block.
        //
        // For example, the built code might look like this.
        // Some manual desugaring is necessary:
        // `return` is represented as assignments to the output variable
        // and breaks to the end.
        // The `match` is represented as a nesting chain of `if`s.
        //
        // var caseIndex = 0;
        // var hoisted0 = zeroValue, ...;
        // return fn (): GeneratorResult<T> {
        //   while (caseIndex >= 0) {
        //     let caseIndex#0 = caseIndex;
        //     caseIndex = -1;
        //     match (caseIndex#0) {
        //       0 -> do {
        //         code; for; case0;
        //         if (followerCondition) { caseIndex = 1; } else { caseIndex = 2; }
        //         // The loop above allows non-yielding basic blocks to continue
        //         // to others, but some will return a result directly.
        //         return new ValueResult(...);
        //       }
        //       1 -> ...
        //       ...
        //       else ->
        //     }
        //   }
        //   return doneResult();
        // }
        //
        return buildTheThing()
    }

    private val useCache = LocalNameCache(block)
    private fun moveDeclarationsForward() {
        fun moveFor(cf: ControlFlow) {
            if (cf is ControlFlow.StmtBlock) {
                cf.withMutableStmtList { stmtList ->
                    var i = 0
                    while (i in stmtList.indices) {
                        var nextI = i + 1
                        val stmt = stmtList[i]
                        if (stmt is ControlFlow.Stmt) {
                            val target = block.dereference(stmt.ref)?.target
                            val declaredName = (target as? DeclTree)?.parts?.name?.content as? InternalModularName
                            if (declaredName != null) {
                                // Find the index to reinsert it before
                                var reinsertionPoint = i + 1
                                while (reinsertionPoint in stmtList.indices) {
                                    if (useCache.uses(stmtList[reinsertionPoint], declaredName)) {
                                        break
                                    }
                                    reinsertionPoint += 1
                                }
                                if (reinsertionPoint > i + 1) {
                                    // reinsertionPoint is the position of the element we want to
                                    // shift it before.

                                    // If we have a statement list like this:
                                    //      0:A 1:B 2:C 3:D
                                    // and i=2, that means that 2:C uses the declared name, so
                                    // we want the adjusted list to look like
                                    //      0:B 1:A 2:C 3:D
                                    // Removing it from the list gives us:
                                    //      0:B 1:C 2:D
                                    // Inserting at position (i-1) then gives us:
                                    //      0:B 1:A 2:C 3:D
                                    val needToReinsert = reinsertionPoint in stmtList.indices

                                    stmtList.removeAt(i)
                                    if (needToReinsert) {
                                        // Something actually needed it.
                                        // Scanning didn't fall off the edge.
                                        stmtList.add(reinsertionPoint - 1, stmt)
                                    }
                                    nextI = i // Revisit now something different is at `i`.
                                }
                            }
                        }
                        i = nextI
                    }
                }
            }
            for (clause in cf.clauses) {
                moveFor(clause)
            }
        }
        moveFor(structureBlock(block).controlFlow)
    }

    private var nestedFnToLocalsNeeded: Map<InternalModularName, Set<InternalModularName>> = mapOf()
    private var nestedFnInitializers: Map<InternalModularName, CallTree> = mapOf()
    private fun inspectNestedFns() {
        // From this, we compute a set of locals that each function needs if it is
        // used across basic blocks.
        val nestedFnToLocalsNeeded = mutableMapOf<InternalModularName, MutableSet<InternalModularName>>()
        val nestedFnInitializers = mutableMapOf<InternalModularName, CallTree>()
        TreeVisit.startingAt(block)
            .forEach { t ->
                if (isAssignment(t)) {
                    val (_, left, right) = t.children
                    if (right is FunTree) {
                        val leftName = (left as? NameLeaf)?.content as? InternalModularName
                        if (leftName != null && leftName in useCache.localNames) {
                            val decl = useCache.localNames[leftName]
                            val declParts = decl?.parts
                            if (
                                declParts != null &&
                                fnSymbol in declParts.metadataSymbolMap &&
                                ssaSymbol in declParts.metadataSymbolMap
                            ) {
                                nestedFnInitializers[leftName] = t
                                nestedFnToLocalsNeeded.getOrPut(leftName) { mutableSetOf() }
                                    .addAll(useCache[right])
                            }
                        }
                    }
                }
                if (t is FunTree) {
                    VisitCue.SkipOne
                } else {
                    VisitCue.Continue
                }
            }
            .visitPreOrder()

        addTransitiveClosure(nestedFnToLocalsNeeded)
        this.nestedFnToLocalsNeeded = nestedFnToLocalsNeeded.mapValues {
            it.value.toSet()
        }
        this.nestedFnInitializers = nestedFnInitializers.toMap()
    }
    private fun expandRequiredNames(localNameSet: MutableSet<InternalModularName>) {
        for (k in nestedFnToLocalsNeeded.keys) {
            if (k in localNameSet) {
                localNameSet.addAll(nestedFnToLocalsNeeded.getValue(k))
            }
        }
    }

    /**
     * When a basic block's main case ends with an `await`, there's an extra follower
     * that double-checks that the promise awaited has resolved, and if so, proceeds
     * with code that assumes promise resolution.
     */
    private enum class CaseKind {
        Main,
        Afterwards,
    }

    private data class CaseInfo(
        val basicBlockIndex: MaximalPathIndex,
        val kind: CaseKind,
        val localNamesRequired: Set<InternalModularName>,
        val onBubble: MaximalPathIndex?,
        val hasFollower: Boolean,
    ) {
        var assignedCaseIndex: Int = -1
    }

    private fun isolateSubBlocks() {
        fun isolate(parent: ControlFlow.StmtBlock, stmtIndex: Int) {
            when (val stmt = parent.stmts[stmtIndex]) {
                // Stmts just correspond to basic block path elements.
                is ControlFlow.Stmt -> {}
                // StmtBlocks, whose statements are isolated, just
                // correspond to a basic block.
                is ControlFlow.StmtBlock -> {
                    for (i in stmt.stmts.indices) {
                        isolate(stmt, i)
                    }
                }
                else -> {
                    // Wrap it in a do{...} block so that it appears as an atom.
                    val isolatedSubBlock = block.document.treeFarm.grow {
                        Block(stmt.pos) {
                            mapControlFlowPlanting(
                                sourceBlock = block,
                                cf = stmt,
                                target = this,
                                mapLabel = { it },
                            ) { ref, edge ->
                                if (edge != null) {
                                    Replant(freeTarget(edge))
                                } else {
                                    Call(ref.pos, ErrorFn) {}
                                }
                            }
                        }
                    }
                    debugConsole?.log(
                        "Isolating sub-block ${stmt.pos}:`${
                            abbreviate(isolatedSubBlock.toPseudoCode())
                        }` which has no internal yields.",
                    )
                    val subBlockRef = BlockChildReference(block.size, stmt.pos)
                    block.add(isolatedSubBlock)

                    parent.withMutableStmtList { mutStmts ->
                        mutStmts[stmtIndex] = ControlFlow.Stmt(subBlockRef)
                    }
                }
            }
        }

        // Returns true if `cf` is isolated, meaning it doesn't
        // yield.
        // If we have a StmtBlock that is not isolated but which
        // contains non-Stmt, non-StmtBlock clauses (see isolateSubBlocks above) which are, then
        // we isolate those.
        fun isolateNonYieldingSubtrees(cf: ControlFlow): Boolean {
            var isolated = true
            if (cf is ControlFlow.StmtBlock) {
                val isolatedMask = KBitSet()
                val stmts = cf.stmts
                for (i in stmts.indices) {
                    val stmt = stmts[i]
                    if (isolateNonYieldingSubtrees(stmt)) {
                        isolatedMask.set(i)
                    } else {
                        isolated = false
                    }
                }
                if (!isolated && !isolatedMask.isEmpty) {
                    // Some to isolate
                    for (i in isolatedMask.bitIndices) {
                        isolate(parent = cf, stmtIndex = i)
                    }
                }
            } else {
                val ref = cf.ref
                if (ref != null) {
                    val t = block.dereference(ref)?.target
                    if (disassembleYieldingCall(t) != null) {
                        isolated = false
                    }
                    if (cf is ControlFlow.Stmt && t != null && isReturnOfDoneResult(t)) {
                        // Just `void` out returns since we handle those for terminal paths.
                        freeTree(t)
                    }
                }
                for (clause in cf.clauses) {
                    if (!isolateNonYieldingSubtrees(clause)) {
                        isolated = false
                    }
                }
            }
            return isolated
        }
        isolateNonYieldingSubtrees(structureBlock(block).controlFlow)
    }

    private var basicBlocks: MaximalPaths = MaximalPaths.zeroValue
    private val temporaryPromiseCaptures = mutableMapOf<MaximalPath.AstElement, Temporary>()
    private var caseInfoMap: Map<Pair<MaximalPathIndex, CaseKind>, CaseInfo> = mapOf()
    private fun inspectBasicBlocks() {
        val basicBlocks = forwardMaximalPaths(
            root = block,
            fails = ConservativeFailure.AtStartAndEndOnly,
            // So we can turn await handling into a loop
            yieldingCallsEndPaths = true,
            ignoreConstantConditions = false,
        )
        this.basicBlocks = basicBlocks
        val orLabelToElseStartBlock = buildMap {
            for (basicBlock in basicBlocks.maximalPaths) {
                val orLabel = basicBlock.orLabel ?: continue
                this[orLabel] = basicBlock.pathIndex
            }
        }

        this.caseInfoMap = buildMap {
            for (path in basicBlocks.maximalPaths) {
                val lastEl = path.elements.lastOrNull()
                val yieldingCall = disassembleYieldingCall(
                    lastEl?.let { lastEl ->
                        block.dereference(lastEl.ref)?.target
                    },
                )
                // We need an "afterward" case if we need to do work afterword (promise unpacking)
                // or there is a pause, and we need to check follower conditions.
                // We don't want to check follower conditions before yielding control because
                // the correct order of operations is to check follower conditions after any
                // side effects during the pause.
                val needsAfterwardsCase = when {
                    yieldingCall == null -> false
                    yieldingCall.kind == YieldingFnKind.await -> true
                    else -> path.followers.any { it.condition is MaximalPath.AstElement }
                }
                if (needsAfterwardsCase) {
                    check(yieldingCall != null)
                    val promiseTemporary = ccNameMaker.unusedTemporaryName("awaited")
                    val promiseType = yieldingCall.yieldingCall.child(1).typeInferences?.type
                        ?: InvalidType
                    val promiseType2 = hackMapOldStyleToNew(promiseType)
                    temporaryPromiseCaptures[lastEl!!] = promiseTemporary
                    // And we'll need to hoist that baby so that it's available on
                    // both the `awakeUpon` side and the `getPromiseResultSync` side.
                    localNameInfo[promiseTemporary] = HoistedNameInfo(
                        promiseTemporary,
                        block.document.treeFarm.grow {
                            Decl(lastEl.pos.leftEdge) {
                                Ln(lastEl.pos.leftEdge, promiseTemporary, promiseType)
                                V(vTypeSymbol)
                                V(Value(ReifiedType(promiseType2), TType))
                            }
                        },
                        ZeroValues[promiseType2],
                    )
                }

                // If yieldCallingCall is not `null`, we're going to emit two cases.
                // one that checks after the yielding call.
                // First, we'll list the names used in the elements that would be part
                // of the second case, but then when generating the names for the first,
                // we'll fold them in if they're not needed.
                val afterwardsLocalNames = buildSet {
                    if (lastEl != null) {
                        addAll(useCache[lastEl.ref])
                    }
                    for (f in path.followers) {
                        when (val condition = f.condition) {
                            is MaximalPath.AstElement -> addAll(useCache[condition.ref])
                            is MaximalPath.Bubbled -> {}
                            null -> {}
                        }
                    }
                    expandRequiredNames(this)
                }

                val onBubble: MaximalPathIndex? = run findOnBubble@{
                    for (element in path.elements) {
                        val stmt = element.stmt
                        var stmtAncestor: ControlFlow? = stmt
                        while (stmtAncestor != null) {
                            val parent = stmtAncestor.parent
                            if (parent is ControlFlow.OrElse && stmtAncestor == parent.orClause) {
                                return@findOnBubble orLabelToElseStartBlock[parent.orClause.breakLabel]
                            }
                            stmtAncestor = parent
                        }
                    }
                    null
                }
                val mainLocalNames = buildSet {
                    for (i in 0..<path.elements.lastIndex) {
                        val e = path.elements[i]
                        addAll(useCache[e.ref])
                    }
                    if (!needsAfterwardsCase) {
                        addAll(afterwardsLocalNames)
                    }
                    expandRequiredNames(this)
                }

                this[path.pathIndex to CaseKind.Main] = CaseInfo(
                    path.pathIndex,
                    CaseKind.Main,
                    mainLocalNames,
                    onBubble,
                    hasFollower = needsAfterwardsCase,
                )
                if (needsAfterwardsCase) {
                    this[path.pathIndex to CaseKind.Afterwards] = CaseInfo(
                        path.pathIndex,
                        CaseKind.Afterwards,
                        afterwardsLocalNames,
                        onBubble,
                        hasFollower = false,
                    )
                }
            }
        }
    }

    private sealed interface NameInfo {
        val name: InternalModularName
        val decl: DeclTree
    }
    private data class HoistedNameInfo(
        override val name: InternalModularName,
        override val decl: DeclTree,
        val zeroValueRecord: ZeroValueRecord?,
    ) : NameInfo
    private data class NotHoisted(
        override val name: InternalModularName,
        override val decl: DeclTree,
    ) : NameInfo

    private val localNameInfo = mutableMapOf<InternalModularName, NameInfo>()
    private fun inspectNames() {
        val counts = buildMap {
            for (caseInfo in caseInfoMap.values) {
                for (name in caseInfo.localNamesRequired) {
                    this[name] = 1 + this.getOrDefault(name, 0)
                }
            }
            // Hoist any functions and the names they close over if they're multiply used.
            val hoistedFns = mutableSetOf<InternalModularName>()
            while (true) {
                var lookAgain = false
                for ((fnName, namesUsedByFn) in nestedFnToLocalsNeeded) {
                    if (fnName in hoistedFns) { continue }
                    if (this.getOrDefault(fnName, 0) > 1) {
                        hoistedFns.add(fnName)
                        for (nameUsedByFn in namesUsedByFn) {
                            val priorUseCount = this.getOrDefault(nameUsedByFn, 0)
                            this[nameUsedByFn] = max(2, priorUseCount)
                            if (priorUseCount < 2) {
                                lookAgain = true
                            }
                        }
                    }
                }
                if (!lookAgain) { break }
            }
        }

        for ((name, decl) in useCache.localNames) {
            localNameInfo[name] = if (counts.getOrDefault(name, 0) <= 1) {
                NotHoisted(name, decl)
            } else {
                val type = decl.parts?.name?.typeInferences?.type?.let {
                    hackMapOldStyleToNew(it)
                } ?: WKT.invalidType2
                val zeroValueRecord = if (name in nestedFnInitializers) {
                    // We don't need a zero value because we're going to initialize
                    // it to its const value where declared.
                    null
                } else {
                    ZeroValues[type]
                }

                HoistedNameInfo(name, decl, zeroValueRecord)
            }
        }
    }

    private val namesNeedingNullAdjustment = mutableSetOf<InternalModularName>()
    private fun buildTheThing(): BlockTree {
        // Assign indices to cases.
        var caseIndexCounter = 0
        val caseKinds = CaseKind.entries
        val caseList = mutableListOf<CaseInfo>()
        for (bb in basicBlocks.maximalPaths) {
            val pathIndex = bb.pathIndex

            for (caseKind in caseKinds) {
                val caseInfo = caseInfoMap[pathIndex to caseKind] ?: continue
                check(caseInfo.assignedCaseIndex == -1)
                caseInfo.assignedCaseIndex = caseIndexCounter++
                caseList.add(caseInfo)
            }
        }
        check(caseInfoMap[basicBlocks.entryPathIndex to CaseKind.Main]?.assignedCaseIndex == 0)

        // Allocate some names to use in the generated step function.
        val generatorInputName = ccNameMaker.unusedTemporaryName("generator")
        val caseIndexName = ccNameMaker.unusedTemporaryName("caseIndex")
        val returnLabel: JumpLabel = ccNameMaker.unusedSourceName(fnParsedName)
        val caseIndexLocalName = ccNameMaker.unusedTemporaryName("caseIndexLocal")

        val hoistedLocals = buildList {
            // First, hoist the non-functions
            for (nameInfo in localNameInfo.values) {
                if (nameInfo is HoistedNameInfo && nameInfo.name !in nestedFnToLocalsNeeded) {
                    add(nameInfo)
                }
            }

            // Then hoist the functions.
            for (nameInfo in localNameInfo.values) {
                if (nameInfo is HoistedNameInfo && nameInfo.name in nestedFnToLocalsNeeded) {
                    add(nameInfo)
                }
            }
        }
        val generatorResultType = MkType2(WKT.generatorResultTypeDefinition)
            .actuals(generatorType.bindings)
            .get()
        val generatorSigAdjusted = generatorSig.copy(
            // We are converting to a function that takes the generator that wraps it
            // as an input so it can use the awakeUpon callback to notify the scheduler
            // about the promise being awaited.
            requiredInputTypes = generatorSig.requiredInputTypes + listOf(generatorType),
        )
        val generatorFnType = typeFromSignature(generatorSigAdjusted)
        val blockPos = block.pos
        val headerPos = blockPos.leftEdge

        val casesBuilder = CasesBuilder(
            blockPos = blockPos,
            caseList = caseList,
            caseIndexName = caseIndexName,
            caseIndexLocalName = caseIndexLocalName,
            generatorResultType = generatorResultType,
            generatorInputName = generatorInputName,
            returnLabel = returnLabel,
        )

        return block.document.treeFarm.grow {
            Block(blockPos) {
                Decl(headerPos) {
                    Ln(caseIndexName, WKT.intType)
                    V(vTypeSymbol)
                    V(Types.vInt, WKT.typeType)
                    V(vVarSymbol)
                    V(void)
                }
                Assign(headerPos, caseIndexName, WKT.intType) {
                    V(vZero, WKT.intType)
                }

                // Declare hoisted variables outside the step function body.
                for (hv in hoistedLocals) {
                    val zeroValueRecord = hv.zeroValueRecord
                    val decl = hv.decl
                    val parts = decl.parts!!
                    val adjustedTypeOld = zeroValueRecord?.adjustedType?.let { hackMapNewStyleToOld(it) }
                        ?: parts.name.typeInferences?.type ?: InvalidType
                    if (zeroValueRecord?.needsNullAdjustment == true) {
                        namesNeedingNullAdjustment.add(hv.name)
                    }
                    val hoistedInitializer = nestedFnInitializers[hv.name]
                    Decl(decl.pos) {
                        Ln(parts.name.pos, hv.name, adjustedTypeOld)
                        var sawVar = false
                        for ((metadataKey, metadataValues) in parts.metadataSymbolMultimap) {
                            if (metadataKey == varSymbol) {
                                sawVar = true
                            }
                            if (metadataKey == typeSymbol && zeroValueRecord != null) {
                                continue
                            }
                            for (valueEdge in metadataValues) {
                                V(valueEdge.target.pos.leftEdge, Value(metadataKey), WKT.symbolType)
                                Replant(freeTarget(valueEdge))
                            }
                        }
                        val metadataPos = decl.pos.leftEdge
                        if (!sawVar && hoistedInitializer == null) {
                            V(metadataPos, vVarSymbol, WKT.symbolType)
                            V(metadataPos, void, WKT.voidType)
                        }
                        if (zeroValueRecord != null) {
                            V(metadataPos, vTypeSymbol, WKT.symbolType)
                            V(
                                metadataPos,
                                Value(ReifiedType(zeroValueRecord.adjustedType), TType),
                                WKT.typeType,
                            )
                        }
                    }
                    if (zeroValueRecord != null) {
                        Assign(decl.pos.rightEdge, hv.name, adjustedTypeOld) {
                            V(decl.pos.rightEdge, zeroValueRecord.value, adjustedTypeOld)
                        }
                    } else if (hoistedInitializer != null) {
                        freeTree(hoistedInitializer)
                        // hoistedInitializer is an assignment.  Just get the RHS.
                        val initializerExpr = freeTree(hoistedInitializer.child(2))
                        Assign(hoistedInitializer.pos, hv.name, initializerExpr.typeInferences?.type) {
                            Replant(freeTree(initializerExpr))
                        }
                    }
                }

                val fnName = ccNameMaker.unusedTemporaryName("convertedCoroutine")
                val generatorTypeOld = hackMapNewStyleToOld(generatorType)
                Decl(blockPos.leftEdge) {
                    Ln(fnName, generatorFnType)
                }
                val fnBody = block.document.treeFarm.grow {
                    Block(blockPos) {
                        Do(blockPos, returnLabel) {
                            fun BlockPlanting.plantCoroBody() {
                                Decl(headerPos) {
                                    Ln(caseIndexLocalName, WKT.intType)
                                    V(vTypeSymbol)
                                    V(headerPos, Types.vInt, WKT.typeType)
                                }
                                Assign(headerPos, caseIndexLocalName, WKT.intType) {
                                    Rn(headerPos, caseIndexName, WKT.intType)
                                }
                                Assign(headerPos, caseIndexName, WKT.intType) {
                                    V(headerPos, vNegOne, WKT.intType)
                                }
                                casesBuilder.unroll(this)
                            }

                            if (casesBuilder.anyCaseContinues) {
                                While(
                                    blockPos,
                                    cond = {
                                        V(headerPos, TBoolean.valueTrue, WKT.booleanType)
                                    },
                                ) {
                                    plantCoroBody()
                                }
                            } else {
                                plantCoroBody()
                            }
                        }
                    }
                }
                if (!skipSimplifyControlFlow) {
                    val simplifiedFnBodyFlow = simplifyControlFlow(
                        fnBody,
                        structureBlock(fnBody).controlFlow,
                        assumeAllJumpsResolved = true,
                        assumeResultsCaptured = true,
                        assumeUseBeforeInitChecked = true,
                        logicalOperators = BuiltinLogicalOperators,
                    )
                    fnBody.replaceFlow(simplifiedFnBodyFlow)
                }

                Assign(blockPos.leftEdge, fnName, generatorFnType) {
                    Fn(blockPos, type = generatorFnType) {
                        Decl(blockPos.leftEdge) {
                            Ln(blockPos.leftEdge, generatorInputName, generatorTypeOld)
                            V(blockPos.leftEdge, vTypeSymbol)
                            V(blockPos.leftEdge, Value(ReifiedType(generatorType)), WKT.typeType)
                        }
                        V(headerPos, vReturnDeclSymbol)
                        Replant(outputDecl.copy(copyInferences = true))
                        Replant(fnBody)
                    }
                }

                // Create a step function that does the thing.
                val sig = adapterFn.sig
                val generatorArgType = generatorType.bindings[0]
                val bindings2 = mapOf(sig.typeFormals[0] to generatorArgType)
                val bindings = bindings2.mapValues {
                    hackMapNewStyleToOld(it.value)
                }
                val callType = CallTypeInferences(
                    hackMapNewStyleToOld(sig.returnType2.mapType(bindings2)),
                    sig,
                    bindings,
                    listOf(),
                )
                Assign(blockPos, outerFnOutputName, callType.type) {
                    Call(blockPos.rightEdge, type = callType) {
                        V(blockPos.rightEdge, Value(adapterFn), callType.variant)
                        Rn(blockPos.rightEdge, fnName, generatorFnType)
                    }
                }
            }
        }
    }

    private val doneResultBuiltinName = BuiltinName("doneResult")
    private val doneResultExport = CoreModule.module.exports!!.first {
        it.name.baseName == doneResultBuiltinName.baseName
    }
    private val doneResultValue = doneResultExport.value(Stage.Run)

    private fun isReturnOfDoneResult(t: Tree): Boolean {
        if (isAssignment(t)) {
            val (_, left, right) = t.children
            if (left is NameLeaf && left.content == outputDecl.parts?.name?.content) {
                if (right is CallTree && right.size == 1) {
                    var callee = right.child(0)
                    if (isTypeAngleCall(callee) && callee.size >= 2) {
                        callee = callee.child(1)
                    }
                    val calleeFn = callee.functionContained
                    val doneResultFn = TFunction.unpackOrNull(doneResultValue)
                    if (calleeFn == doneResultFn || sameUserFn(calleeFn, doneResultFn)) {
                        return true
                    }
                    if (callee is NameLeaf && callee.content == doneResultExport.name) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Responsible for building the chain of `if (caseIndexLocal == 123)` that
     * implements the converted state machine.
     */
    private inner class CasesBuilder(
        val blockPos: Position,
        val caseList: List<CaseInfo>,
        val caseIndexName: Temporary,
        val caseIndexLocalName: Temporary,
        val generatorResultType: Type2,
        val generatorInputName: Temporary,
        val returnLabel: JumpLabel,
    ) {
        val anyCaseContinues: Boolean get() = caseList.any { ci ->
            val basicBlockIndex = ci.basicBlockIndex
            var continues = true
            if (
                ci.hasFollower || // Going to return to pause
                basicBlockIndex in basicBlocks.exitPathIndices || // Nowhere to go
                basicBlockIndex in basicBlocks.failExitPathIndices
            ) {
                continues = false
            }
            if (continues && ci.kind == CaseKind.Main) {
                val lastElement = basicBlocks[basicBlockIndex].elements.last()
                val tree = block.dereference(lastElement.ref)?.target
                if (disassembleYieldingCall(tree) != null) {
                    continues = false // Going to return to pause
                }
            }
            continues
        }
        var caseListIndex = 0
        val hoistedLocalNames = buildSet {
            for (ni in localNameInfo.values) {
                if (ni is HoistedNameInfo) {
                    add(ni.name)
                }
            }
        }

        fun unroll(planting: BlockPlanting): Unit = planting.run {
            if (caseListIndex == caseList.size) {
                // Output the else case
                val pos = blockPos.rightEdge
                returnDoneResult(this, pos)
                planting.Break(pos, returnLabel)
            } else {
                val case = caseList[caseListIndex++]
                val path = basicBlocks[case.basicBlockIndex]
                val pos = path.diagnosticPosition
                val kind = case.kind

                data class FollowerInfo(
                    val pos: Position,
                    val condition: BlockChildReference?,
                    val target: CaseInfo,
                    /**
                     * True if control should exit the outer `while (true)` loop which otherwise
                     * serves to cause one case from flowing immediately into the next.
                     */
                    val exits: Boolean,
                )

                val elements = path.elements
                var isTerminal = path.pathIndex in basicBlocks.exitPathIndices
                var isFreeBubble = path.pathIndex in basicBlocks.failExitPathIndices

                val yieldingInfo = elements.lastOrNull()?.let {
                    disassembleYieldingCall(block.dereference(it.ref)?.target)
                }

                val afterwards = if (case.hasFollower) {
                    caseInfoMap.getValue(case.basicBlockIndex to CaseKind.Afterwards)
                } else {
                    null
                }
                val followers = if (afterwards != null && kind == CaseKind.Main) {
                    isTerminal = false
                    isFreeBubble = false
                    // Dispatch to followers in the afterwards-case match code.
                    listOf(
                        FollowerInfo(
                            path.diagnosticPosition.rightEdge,
                            null,
                            afterwards,
                            exits = true,
                        ),
                    )
                } else {
                    path.followers.mapNotNull { f ->
                        if (f.condition is MaximalPath.Bubbled) {
                            // Just mark the beginning and end of `or` blocks for our purposes.
                            null
                        } else {
                            f.pathIndex?.let { destPathIndex ->
                                val cond = (f.condition as? MaximalPath.AstElement)?.ref
                                FollowerInfo(
                                    cond?.pos?.rightEdge ?: path.diagnosticPosition.rightEdge,
                                    cond,
                                    caseInfoMap.getValue(destPathIndex to CaseKind.Main),
                                    // If pausing at the end of the case, break out of the containing
                                    // `while (true)` loop instead of immediately continuing on to the
                                    // next state machine state.
                                    // CaseKind.Main is before the pause.
                                    exits = yieldingInfo != null && case.kind == CaseKind.Main,
                                )
                            }
                        }
                    }
                }

                If(
                    cond = {
                        Call(pos.leftEdge, type = eqIntCallTypeInferences) {
                            V(pos.leftEdge, BuiltinFuns.vEqIntFn, eqIntCallTypeInferences.variant)
                            Rn(pos.leftEdge, caseIndexLocalName, WKT.intType)
                            V(pos.leftEdge, Value(case.assignedCaseIndex, TInt), WKT.intType)
                        }
                    },
                    thn = {
                        // Putting `return__123 = new ValueResult(...);` right before
                        // any `break fn__124` meets TmpLTranslator expectations about
                        // how to resugar `return` statements, so when planting
                        // case instructions, we store away the value result expression
                        // used for `yield` and `await` statements.
                        var valueResultExpr: Tree? = null
                        fun Planting.maybeEmitValueResult(): Boolean {
                            val valueExpr = valueResultExpr
                            valueResultExpr = null
                            if (valueExpr != null) {
                                returnValueResult(this, valueExpr)
                                return true
                            }
                            return false
                        }

                        // Plant an assignment to the closed-over case index variable for the given
                        // case.  If `continues` is true, we want to process that case immediately via
                        // the outer `while` loop, but otherwise the caller must have set the return
                        // variable, and we break to the end of the step function.
                        fun BlockPlanting.transitionTo(pos: Position, target: CaseInfo, exits: Boolean) {
                            Assign(pos, caseIndexName, WKT.intType) {
                                V(pos, Value(target.assignedCaseIndex, TInt), WKT.intType)
                            }
                            if (exits) {
                                // Assume the return_value has been set to a GeneratorResult by the
                                // yield handling stuff.
                                maybeEmitValueResult()
                                Break(pos, returnLabel)
                            }
                        }

                        // Plant the elements first, and then any transitions for followers
                        // involving mapping follower targets to case indices.
                        // This is a function, because, for failure handling, we might need
                        // to wrap it in an OrElse.
                        fun BlockPlanting.plantCaseInstructions() {
                            when (case.kind) {
                                CaseKind.Main -> {
                                    val lastElementIndex = elements.lastIndex

                                    for (elementIndex in elements.indices) {
                                        val element = elements[elementIndex]
                                        val tree = block.dereference(element.ref)?.target
                                        if (tree == null) {
                                            Call(element.pos, ErrorFn) {}
                                            continue
                                        }

                                        if (elementIndex == lastElementIndex && yieldingInfo != null) {
                                            var yieldingExpr: Tree? = null
                                            when (yieldingInfo.kind) {
                                                YieldingFnKind.await -> {
                                                    check(afterwards != null)
                                                    val promiseTree = yieldingInfo.yieldingCall.child(1)
                                                    val promiseType = promiseTree.typeInferences?.type
                                                        ?: InvalidType
                                                    val promiseName = temporaryPromiseCaptures.getValue(element)
                                                    val promiseNameInfo = localNameInfo.getValue(promiseName)
                                                        as HoistedNameInfo
                                                    Assign(promiseTree.pos.leftEdge, promiseName, promiseType) {
                                                        Replant(freeTree(promiseTree))
                                                    }
                                                    val callType =
                                                        CoroHelperSpecials.ConvertedCoroutineAwakeUponFn
                                                            .callTypeInferences(promiseType = promiseType)
                                                    val pos = yieldingInfo.yieldingCall.pos
                                                    Call(pos, type = callType) {
                                                        V(
                                                            pos.leftEdge,
                                                            Value(CoroHelperSpecials.ConvertedCoroutineAwakeUponFn),
                                                            callType.variant,
                                                        )
                                                        NotNullCall(
                                                            promiseTree.pos,
                                                            promiseNameInfo.zeroValueRecord!!,
                                                        ) { type ->
                                                            Rn(promiseTree.pos, promiseName, type)
                                                        }
                                                        Rn(
                                                            pos.leftEdge,
                                                            generatorInputName,
                                                            hackMapNewStyleToOld(generatorType),
                                                        )
                                                    }
                                                }
                                                YieldingFnKind.yield -> {
                                                    yieldingExpr = yieldingInfo.yieldingCall.childOrNull(1)
                                                }
                                            }
                                            valueResultExpr = yieldingExpr
                                                ?: ValueLeaf(block.document, yieldingInfo.yieldingCall.pos, emptyValue)
                                                    .also {
                                                        it.typeInferences = BasicTypeInferences(WKT.emptyType, listOf())
                                                    }
                                            continue
                                        }
                                        if (tree is BlockTree) {
                                            // Reweave that which was unwoven in step 3 above.
                                            val flow = structureBlock(tree).controlFlow
                                            mapControlFlowPlanting(
                                                sourceBlock = tree, cf = flow, target = this,
                                                mapLabel = { it },
                                            ) { ref, edge ->
                                                if (edge != null) {
                                                    Replant(maybeAdjustVars(freeTarget(edge)))
                                                } else {
                                                    Call(ref.pos, ErrorFn) {}
                                                }
                                            }
                                            continue
                                        }
                                        val adjustedTree = maybeAdjustVars(freeTree(tree))
                                        if ((adjustedTree as? ValueLeaf)?.content != void) {
                                            Replant(adjustedTree)
                                        }
                                    }
                                }

                                CaseKind.Afterwards -> {
                                    val yieldingElement = path.elements.last()
                                    val tree = block.dereference(yieldingElement.ref)?.target!!
                                    val yieldingInfo = disassembleYieldingCall(tree)!!
                                    when (yieldingInfo.kind) {
                                        YieldingFnKind.await -> {
                                            val promiseTree = yieldingInfo.yieldingCall.child(1)
                                            val yieldedType = yieldingInfo.yieldingCall.typeInferences?.type
                                                ?: InvalidType
                                            val promiseName = temporaryPromiseCaptures.getValue(yieldingElement)
                                            val promiseNameInfo = localNameInfo.getValue(promiseName)
                                                as HoistedNameInfo
                                            val promiseType = MkType.nominal(
                                                WKT.promiseTypeDefinition,
                                                listOf(yieldedType),
                                            )
                                            val callType = CoroHelperSpecials.GetPromiseResultSyncFn.callTypeInferences(
                                                promiseType = promiseType,
                                            )
                                            fun Planting.plantGetPromiseResultSyncCall() =
                                                Call(tree.pos, type = callType) {
                                                    V(
                                                        tree.pos.leftEdge,
                                                        Value(CoroHelperSpecials.GetPromiseResultSyncFn),
                                                        callType.variant,
                                                    )
                                                    NotNullCall(
                                                        promiseTree.pos, promiseNameInfo.zeroValueRecord!!,
                                                    ) { adjustedPromiseType ->
                                                        Rn(promiseTree.pos, promiseName, adjustedPromiseType)
                                                    }
                                                }
                                            val assignedTo = yieldingInfo.assignedTo
                                            if (assignedTo != null) {
                                                Assign(tree.pos, assignedTo as ResolvedName, yieldedType) {
                                                    plantGetPromiseResultSyncCall()
                                                }
                                            } else {
                                                plantGetPromiseResultSyncCall()
                                            }
                                        }

                                        YieldingFnKind.yield -> {
                                            check(yieldingInfo.assignedTo == null)
                                        }
                                    }
                                }
                            }

                            fun BlockPlanting.unrollFollowers(followerIndex: Int) {
                                if (followerIndex in followers.indices) {
                                    val (fPos, condition, target, fExits) =
                                        followers[followerIndex]

                                    if (condition != null) {
                                        If(
                                            pos = condition.pos,
                                            cond = {
                                                val edge = block.dereference(condition)
                                                if (edge != null) {
                                                    Replant(maybeAdjustVars(freeTarget(edge)))
                                                } else {
                                                    Call(condition.pos, ErrorFn) {}
                                                }
                                            },
                                            thn = {
                                                transitionTo(fPos, target, exits = fExits)
                                            },
                                            els = {
                                                unrollFollowers(followerIndex + 1)
                                            },
                                        )
                                    } else {
                                        transitionTo(fPos, target, exits = fExits)
                                    }
                                } else if (isFreeBubble) {
                                    VoidBubble(path.diagnosticPosition.rightEdge)
                                } else {
                                    ignore(isTerminal)
                                    val pos = path.diagnosticPosition.rightEdge
                                    if (!maybeEmitValueResult()) {
                                        returnDoneResult(this, pos)
                                    }
                                    Break(pos, returnLabel)
                                }
                            }
                            if (followers.size >= 2) {
                                maybeEmitValueResult()
                            }
                            unrollFollowers(0)
                            check(valueResultExpr == null)
                        }
                        if (case.onBubble != null) {
                            OrElse(
                                or = { plantCaseInstructions() },
                                els = {
                                    val bubbleCase = caseInfoMap.getValue(case.onBubble to CaseKind.Main)
                                    transitionTo(path.diagnosticPosition.rightEdge, bubbleCase, exits = false)
                                },
                            )
                        } else {
                            plantCaseInstructions()
                        }
                    },
                    els = {
                        unroll(this)
                    },
                )
            }
        }

        private fun maybeAdjustRightNameLeaf(t: RightNameLeaf): Tree? {
            val nameInfo = localNameInfo[t.content]
            if (nameInfo is HoistedNameInfo && nameInfo.zeroValueRecord?.needsNullAdjustment == true) {
                return t.document.treeFarm.grow {
                    NotNullCall(t.pos, nameInfo.zeroValueRecord) { argType ->
                        Rn(t.pos, t.content, argType)
                    }
                }
            }
            return null
        }

        private fun maybeAdjustVars(t: Tree): Tree {
            if (t is DeclTree) {
                val name = t.parts?.name?.content
                if (name != null && name in hoistedLocalNames) {
                    // The declaration was output separately.
                    val replacement = ValueLeaf(t.document, t.pos, void)
                    replacement.typeInferences = BasicTypeInferences(WKT.voidType, listOf())
                    return replacement
                }
            }
            if (t is RightNameLeaf) {
                return maybeAdjustRightNameLeaf(t) ?: t
            }
            if (t is InnerTree && useCache[t].intersects(namesNeedingNullAdjustment)) {
                TreeVisit.startingAt(t)
                    .forEachContinuing { descTree ->
                        if (descTree is RightNameLeaf) {
                            val edge = descTree.incoming!!
                            val replacement = maybeAdjustRightNameLeaf(descTree)
                            if (replacement != null) {
                                edge.replace(replacement)
                            }
                        }
                    }
                    .visitPreOrder()
            }
            return t
        }

        /** Assign `doneResult()` from `Core.temper` to the return variable. */
        private fun returnDoneResult(
            planting: Planting,
            pos: Position,
        ) {
            val resultReturnName = outputDecl.parts!!.name.content as ResolvedName
            planting.Assign(pos, resultReturnName, hackMapNewStyleToOld(generatorResultType)) {
                val callType = doneResultCallTypeInferences(
                    generatorResultType.bindings[0],
                    doneResultExport,
                )
                Call(pos, type = callType) {
                    Rn(pos, doneResultExport.name, callType.variant)
                }
            }
        }

        private fun returnValueResult(
            planting: Planting,
            valueExpr: Tree,
        ) {
            val pos = valueExpr.pos
            val resultReturnName = outputDecl.parts!!.name.content as ResolvedName
            val valueResultType = MkType2(WKT.valueResultTypeDefinition)
                .actuals(listOf(generatorResultType.bindings[0]))
                .get()
            val valueResultTypeOld = hackMapNewStyleToOld(valueResultType)
            val callType = CallTypeInferences(
                valueResultTypeOld,
                Signature2(valueResultType, false, listOf(valueResultType)),
                mapOf(),
                listOf(),
            )
            planting.Assign(pos, resultReturnName, hackMapNewStyleToOld(generatorResultType)) {
                Call(pos, type = callType) {
                    V(pos.leftEdge, Value(New), WKT.functionType)
                    V(pos.leftEdge, Value(ReifiedType(valueResultType), TType), WKT.typeType)
                    Replant(freeTree(valueExpr))
                }
            }
        }
    }
}

/** Provides efficient access to which names are used in AST subtrees. */
private class LocalNameCache(
    private val block: BlockTree,
) {
    val localNames: Map<InternalModularName, DeclTree> = buildMap {
        // Scan block for declarations so we know which names are local: declared in the
        // same block, instead of free: declared in some outer scope.
        TreeVisit.startingAt(block)
            .forEach { t ->
                if (t is DeclTree) {
                    (t.parts?.name?.content as? InternalModularName)?.let { name ->
                        this[name] = t
                    }
                }
                if (t is FunTree) {
                    VisitCue.SkipOne
                } else {
                    VisitCue.Continue
                }
            }
            .visitPreOrder()
    }

    val usedIn =
        mutableMapOf<Either<ControlFlow, Tree>, Set<InternalModularName>>()

    operator fun get(cf: ControlFlow) = usedIn.getOrPut(Either.Left(cf)) {
        scan(cf)
    }

    operator fun get(t: Tree) = usedIn.getOrPut(Either.Right(t)) {
        scan(t)
    }

    operator fun get(ref: BlockChildReference): Set<InternalModularName> {
        val t = block.dereference(ref)?.target ?: return setOf()
        return get(t)
    }

    fun uses(cf: ControlFlow, name: InternalModularName) = name in get(cf)

    private fun scan(cf: ControlFlow): Set<InternalModularName> = buildSet {
        val ref = cf.ref
        if (ref != null) {
            addAll(get(ref))
        }
        for (clause in cf.clauses) {
            addAll(get(clause))
        }
    }

    private fun scan(t: Tree): Set<InternalModularName> = buildSet {
        TreeVisit.startingAt(t)
            .forEachContinuing {
                val name = (it as? NameLeaf)?.content as? InternalModularName
                if (name != null && name in localNames) {
                    add(name)
                }
            }
            .visitPreOrder()
    }
}

private val vZero = Value(0, TInt)
private val vNegOne = Value(-1, TInt)

private val eqIntCallTypeInferences = CallTypeInferences(
    WKT.booleanType,
    BuiltinFuns.eqIntFn.sigs!![0],
    mapOf(),
    listOf(),
)

private fun doneResultCallTypeInferences(typeArg: Type2, doneResultExport: Export): CallTypeInferences {
    val variant = doneResultExport.typeInferences!!.type as FunctionType
    val typeArgOld = hackMapNewStyleToOld(typeArg)
    return CallTypeInferences(
        MkType.nominal(WKT.doneResultTypeDefinition, listOf(typeArgOld)),
        variant,
        mapOf(variant.typeFormals[0] to typeArgOld),
        listOf(),
    )
}

private val voidBubbleTypeInferences = CallTypeInferences(
    WKT.voidType,
    MkType.fn(
        listOf(),
        listOf(),
        null,
        hackMapNewStyleToOld(
            MkType2(WKT.resultTypeDefinition)
                .actuals(listOf(WKT.voidType2, WKT.booleanType2))
                .get(),
        ),
    ),
    mapOf(),
    listOf(),
)

@Suppress("FunctionName")
private fun Planting.VoidBubble(pos: Position): TreeTemplate<CallTree> =
    Call(pos, voidBubbleTypeInferences) {
        V(BuiltinFuns.vBubble, voidBubbleTypeInferences.variant)
    }

private val notNullFnType = typeFromSignature(NotNullFn.sig)

@Suppress("FunctionName")
fun Planting.NotNullCall(
    pos: Position,
    zvr: ZeroValueRecord,
    plantArg: Planting.(StaticType) -> TreeTemplate<*>,
): TreeTemplate<CallTree> {
    val adjustedType = hackMapNewStyleToOld(zvr.adjustedType)
    val unadjustedType = hackMapNewStyleToOld(zvr.unadjustedType)
    val callType = CallTypeInferences(
        unadjustedType,
        notNullFnType,
        mapOf(notNullFnType.typeFormals[0] to unadjustedType),
        listOf(),
    )
    return Call(pos, type = callType) {
        V(pos.leftEdge, BuiltinFuns.vNotNullFn, callType.variant)
        plantArg(adjustedType)
    }
}

private fun sameUserFn(a: MacroValue?, b: MacroValue?) =
    a is LongLivedUserFunction && b is LongLivedUserFunction && a.stayLeaf == b.stayLeaf
