package lang.temper.interp

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.AwaitFn
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.GetStaticOp
import lang.temper.builtin.Types
import lang.temper.common.EnumRange
import lang.temper.common.KBitSet
import lang.temper.common.Log
import lang.temper.common.TriState
import lang.temper.common.compatRemoveLast
import lang.temper.common.compatReversed
import lang.temper.common.console
import lang.temper.common.ignore
import lang.temper.common.rangesOfSetBits
import lang.temper.common.subListToEnd
import lang.temper.env.ChildEnvironment
import lang.temper.env.Constness
import lang.temper.env.DeclarationBits
import lang.temper.env.Environment
import lang.temper.env.InterpMode
import lang.temper.env.ReferentBit
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.env.or
import lang.temper.log.FailLog
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.spanningPosition
import lang.temper.name.NameMaker
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.stage.Readiness
import lang.temper.stage.Stage
import lang.temper.type.AndType
import lang.temper.type.BubbleType
import lang.temper.type.MkType
import lang.temper.type.NominalType
import lang.temper.type.StaticType
import lang.temper.type.SuperTypeTree
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.isNeverType
import lang.temper.type.mentionsInvalid
import lang.temper.type2.AnySignature
import lang.temper.type2.InterpSignature
import lang.temper.type2.InterpValueFormal
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.ValueFormalKind
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.Abort
import lang.temper.value.ActualValues
import lang.temper.value.Actuals
import lang.temper.value.AwaitMacroEnvironment
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.CallableValue
import lang.temper.value.ControlFlow
import lang.temper.value.CoverFunction
import lang.temper.value.DeclParts
import lang.temper.value.DeclTree
import lang.temper.value.DynamicMessage
import lang.temper.value.EscTree
import lang.temper.value.Fail
import lang.temper.value.FnParts
import lang.temper.value.FunTree
import lang.temper.value.FunctionSpecies
import lang.temper.value.InternalFeatureKey
import lang.temper.value.InternalFeatureKeys
import lang.temper.value.InterpreterCallback
import lang.temper.value.JumpDestination
import lang.temper.value.JumpLabel
import lang.temper.value.LeafTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.MacroValue
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PRESERVE_FN_CALL_SIZE
import lang.temper.value.Panic
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.PostPass
import lang.temper.value.Promises
import lang.temper.value.ReifiedType
import lang.temper.value.Resolutions
import lang.temper.value.Result
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.StructuredFlow
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TFunction
import lang.temper.value.TNull
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.TypeTag
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.ValueStability
import lang.temper.value.YieldingFnKind
import lang.temper.value.and
import lang.temper.value.blockPartialEvaluationOrder
import lang.temper.value.defaultSymbol
import lang.temper.value.disassembleYieldingCall
import lang.temper.value.failSymbol
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.impliedThisSymbol
import lang.temper.value.infoOr
import lang.temper.value.initSymbol
import lang.temper.value.isImplicits
import lang.temper.value.labelSymbol
import lang.temper.value.matches
import lang.temper.value.optionalAsTriState
import lang.temper.value.optionalSymbol
import lang.temper.value.restFormalSymbol
import lang.temper.value.returnDeclSymbol
import lang.temper.value.returnParsedName
import lang.temper.value.ssaSymbol
import lang.temper.value.stability
import lang.temper.value.superSymbol
import lang.temper.value.symbolContained
import lang.temper.value.toLispy
import lang.temper.value.typeFormalSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.unholeBuiltinName
import lang.temper.value.unify
import lang.temper.value.vLabelSymbol
import lang.temper.value.vStaySymbol
import lang.temper.value.vWrappedGeneratorFnSymbol
import lang.temper.value.valueContained
import lang.temper.value.varSymbol
import lang.temper.value.void
import lang.temper.value.warnAboutUnresolved
import lang.temper.value.wordSymbol
import lang.temper.value.wrappedGeneratorFnSymbol

private const val SPAMMY_INCLUDES_IMPLICITS = false
private const val SPAMMY_DISPATCH = false
private const val SPAMMY = false

/**
 * An interpreter for Temper ASTs that can both fully evaluate Temper ASTs to produce
 * a result and partially evaluate them to transform the AST.
 */
class Interpreter(
    val failLog: FailLog,
    val logSink: LogSink,
    private val stage: Stage,
    val nameMaker: NameMaker,
    /**
     * Called periodically during long-running stages to decide whether to keep working or [Abort].
     */
    val continueCondition: () -> Boolean,
    val features: Map<InternalFeatureKey, Value<*>> = emptyMap(),
    val connecteds: Map<String, (Signature2) -> Value<*>> = emptyMap(),
    val postPasses: MutableSet<PostPass>? = null,
    private val promises: Promises = Promises(),
    private val replacementPolicy: ReplacementPolicy = ReplacementPolicy.Discard,
) {

    private var stepCount = 0
    private var goingOutOfStyle = stage == Stage.Run
    private val isProcessingImplicits = nameMaker.namingContext.isImplicits

    @Suppress("SimplifyBooleanWithConstants")
    private fun beSpammy(spammy: Boolean) =
        spammy && (SPAMMY_INCLUDES_IMPLICITS || !isProcessingImplicits)

    fun interpret(ast: Tree, env: Environment, interpMode: InterpMode): PartialResult =
        interpretTree(ast, env, interpMode)

    /**
     * interprets AST, but additionally invokes extra steps tracked by [promises].
     *
     * @param allowTopLevelAwait true iff [ast] should be allowed to `await`
     *   promises.
     */
    fun interpretAndWaitForAsyncTasksToSettle(
        ast: BlockTree,
        env: Environment,
        interpMode: InterpMode,
        mayWrapEnvironment: Boolean,
        allowTopLevelAwait: Boolean,
        /**
         * True to dump warnings if any promises are still outstanding.
         * Set this to true if noone else owns promises and is going to do their
         * own checks.
         */
        warnAboutUnresolved: Boolean = false,
    ): PartialResult {
        val promises = this.promises
        val bodyOwner = if (allowTopLevelAwait) {
            TransientUserFunction(
                ast.pos,
                null,
                closedOverEnvironment = env,
                signature = Signature2(WellKnownTypes.anyValueOrNullType2, false, listOf()),
                interpFormals = null,
                formalNamesByIndex = listOf(),
                isSelfContained = true,
                returnDecl = null,
                superTypes = SuperTypeTree.empty,
                body = ast,
            )
        } else {
            null
        }
        var blockResult = doMaybeSpammy(ast, interpMode) {
            interpretBlock(ast, env, interpMode, bodyOwner, mayWrapEnvironment = mayWrapEnvironment)
        }
        // At this point, blockResult could be NotYet because it is awaiting something.
        // We'll keep track of that and substitute a different result so that its final result
        // is what we return.

        // Keep running tasks until all promises are resolved, and async tasks executed, or
        // we run out of quota.
        while (true) {
            val (nextPos, next) = promises.nextReadyTask() ?: break
            val isRoot = (next as? TransientUserFunction)?.body === ast
            // Step the step function.
            val nextResult = interpret(
                ast.document.treeFarm.grow {
                    Block(nextPos) {
                        Call(nextPos, next) {}
                    }
                },
                env,
                interpMode,
            )

            if (blockResult is Fail && !isRoot) {
                blockResult.info?.logTo(logSink)
                logSink.log(
                    Log.Warn,
                    MessageTemplate.AsyncTaskFailedSilently,
                    nextPos,
                    listOf(),
                )
            }
            if (blockResult is NotYet && isRoot) {
                blockResult = nextResult
            }
        }

        if (warnAboutUnresolved && promises.numUnresolved != 0) {
            promises.warnAboutUnresolved(logSink)
            if (blockResult is NotYet) {
                val blocker = promises.unresolvedOn(bodyOwner)
                if (blocker != null) {
                    logSink.log(
                        Log.Warn,
                        MessageTemplate.UnresolvedPromisesPreventedCompletion,
                        blocker,
                        emptyList(),
                    )
                }
            }
        }

        return blockResult
    }

    /**
     * May be called to invoke a "top-level" block without wrapping [env].
     * @param env Unless [mayWrapEnvironment] then [env] must be an environment like that from
     *     [blankEnvironment]
     */
    fun interpretReuseEnvironment(
        ast: BlockTree,
        env: Environment,
        interpMode: InterpMode,
        mayWrapEnvironment: Boolean,
    ): PartialResult = interpretTree(ast, env, interpMode, mayWrapEnvironment = mayWrapEnvironment)

    /**
     * All recursive interpret calls MUST happen via this method so that we can keep breadcrumbs
     * and step counters up to date.
     */
    internal fun interpretEdge(
        edge: TEdge,
        env: Environment,
        im: InterpMode,
    ): PartialResult =
        if (visitEdge(edge, env, im)) {
            interpretTree(edge.target, env, im)
        } else {
            edge.target.valueContained ?: NotYet
        }

    private fun visitEdge(edge: TEdge, env: Environment, im: InterpMode): Boolean {
        if (im == InterpMode.Partial) {
            val breadcrumb = edge.breadcrumb
            if (breadcrumb != null && breadcrumb >= stage) {
                // Already partially evaluated the target, so skip to the children in order
                // to double-check that we've completely reached everything in the child tree.
                val ast = edge.target
                val childEnv = when (ast) {
                    is BlockTree, is FunTree -> BlockEnvironment(env)
                    is CallTree, is DeclTree, is LeafTree -> env
                    is EscTree -> {
                        preemptEscapedCalls(ast, env, im)
                        env
                    }
                }
                maybeVisitChildren(ast, childEnv, im)
                return false
            }
            edge.breadcrumb = stage
        }
        stepCount += 1
        if (stepCount and CONTINUE_CONDITION_STEP_MASK == 0 && !continueCondition()) {
            failLog.explain(MessageTemplate.Aborted, edge.target.pos)
            throw Abort()
        }
        return true
    }

    private inline fun <T> doMaybeSpammy(ast: Tree, im: InterpMode, f: () -> T): T {
        return if (beSpammy(SPAMMY)) {
            val result = console.group(
                "Interpreting$stage ${ast.toLispy(false)} $im",
            ) {
                f()
            }
            console.log("Got $result")
            result
        } else {
            f()
        }
    }

    private fun interpretTree(
        ast: Tree,
        env: Environment,
        im: InterpMode,
        mayWrapEnvironment: Boolean = true,
    ): PartialResult = doMaybeSpammy(ast, im) {
        interpretTreeNotSpammy(ast, env, im, mayWrapEnvironment = mayWrapEnvironment)
    }

    private fun interpretTreeNotSpammy(
        ast: Tree,
        env: Environment,
        im: InterpMode,
        mayWrapEnvironment: Boolean = true,
    ): PartialResult {
        val marker = failLog.markBeforeRecoverableFailure()
        failLog.explain(MessageTemplate.Interpreting, ast.pos)
        val result = when (ast) {
            is RightNameLeaf -> interpretRightName(ast, env, im)
            is LeftNameLeaf -> NotYet // not sensible except in context of an assignment operator
            is StayLeaf -> void
            is ValueLeaf -> interpretValue(ast)
            is BlockTree -> interpretBlock(ast, env, im, mayWrapEnvironment = mayWrapEnvironment)
            is CallTree -> interpretCall(ast, env, im)
            is DeclTree -> interpretDecl(ast, env, im)
            is EscTree -> interpretEsc(ast, env, im)
            is FunTree -> interpretFun(ast, env, im)
        }
        if (result is Value<*>) {
            // Roll back unless it was a result from an operation that might have failed.
            // In later stages, we turn failure paths into passing paths with boolean tests for
            // failure.
            if (result != void || !isFailingCallToHandlerScope(ast, env)) {
                marker.rollback()
            }
        }
        return result
    }

    // TODO: Maybe treat as unready if the value is mutable.
    private fun interpretValue(leaf: ValueLeaf) = leaf.content

    private fun interpretRightName(
        leaf: RightNameLeaf,
        env: Environment,
        im: InterpMode,
    ): PartialResult {
        val name = leaf.content
        val got =
            if (im == InterpMode.Full || goingOutOfStyle || env.isWellFormed(name)) {
                env[name, callbackFor(leaf)]
            } else {
                NotYet
            }
        if (
            im == InterpMode.Partial &&
            name is ResolvedName &&
            got is Value<*> &&
            env.constness(name) == Constness.Const
        ) {
            val stableValue = if (isStable(got)) got else null
            if (stableValue != null && !leaf.isPreserved) {
                if (beSpammy(SPAMMY)) {
                    console.log("$stage: Inlining $name -> $got")
                }
                leaf.incoming?.replace {
                    when (replacementPolicy) {
                        ReplacementPolicy.Discard -> V(leaf.pos, stableValue)
                        ReplacementPolicy.Preserve ->
                            Call(leaf.pos, BuiltinFuns.preserveFn) {
                                Replant(freeTree(leaf))
                                V(leaf.pos, stableValue)
                            }
                    }
                }
            }
        }
        return got
    }

    private fun interpretBlock(
        ast: BlockTree,
        env: Environment,
        im: InterpMode,
        bodyOwner: UserFunction? = null,
        mayWrapEnvironment: Boolean = true,
    ): PartialResult {
        val yieldedAt = bodyOwner?.yieldedAt

        val evaluation: InProgressEvaluation
        if (im == InterpMode.Full && yieldedAt != null) {
            check(bodyOwner is TransientUserFunction) // yieldedAt is null for other variant
            evaluation = yieldedAt
            bodyOwner.yieldedAt = null
        } else {
            val mutableEnv = if (mayWrapEnvironment) {
                BlockEnvironment(env)
            } else {
                // This is reached from InterpretiveDanceStage so that it can get access to top
                // level module declarations after the fact.
                env as? MutableEnvironment<*> ?: BlockEnvironment(env)
            }
            evaluation = InProgressEvaluation()
            evaluation.envStack.add(mutableEnv)
            when (val flow = ast.flow) {
                LinearFlow ->
                    if (im == InterpMode.Full) {
                        // Act as if we have a StructuredFlow so that we don't need to separate
                        // full-interpretation code below.
                        // Full-interpretation of LinearFlows is pretty much only of interest
                        // for pure function evaluation during very early stages and when the
                        // interpreter is being used standalone.
                        val parts = ast.parts
                        var controlFlowEquivalent = ControlFlow.StmtBlock(
                            ast.pos,
                            (parts.startIndex until ast.size).map { index ->
                                val e = ast.edges[index]
                                ControlFlow.Stmt(
                                    BlockChildReference(index = index, e.target.pos),
                                )
                            },
                        )
                        val jumpLabel = when (
                            val labelContent = (parts.label?.target as? NameLeaf)?.content
                        ) {
                            null -> null
                            is JumpLabel -> labelContent
                            // Allow matching UnresolvedJumpSpecifier pre-name-resolution
                            is ParsedName -> nameMaker.unusedSourceName(labelContent)
                            else -> null
                        }
                        if (jumpLabel != null) {
                            controlFlowEquivalent = ControlFlow.StmtBlock.wrap(
                                ControlFlow.Labeled(
                                    pos = ast.pos,
                                    breakLabel = jumpLabel,
                                    continueLabel = null,
                                    stmts = controlFlowEquivalent,
                                ),
                            )
                        }
                        evaluation.stack.add(InProgress(controlFlowEquivalent))
                    }
                is StructuredFlow ->
                    evaluation.stack.add(InProgress(flow.controlFlow))
            }
        }
        val mutableEnv = evaluation.envStack.first()

        // In partial evaluation mode, we visit all nodes once.
        if (im == InterpMode.Partial) {
            // TODO: for complex flows, order should make sense.
            // TODO: if there is a single terminal node in a structured flow,
            // and it flattens to a value leaf, return its result
            val (orderForNodes, useLast) = when (val flow = ast.flow) {
                is LinearFlow -> null to true
                is StructuredFlow -> {
                    val orderForNodes = blockPartialEvaluationOrder(ast)
                    val lastIndex = orderForNodes.lastOrNull()
                    val useLast = if (lastIndex == null) {
                        true
                    } else {
                        // If there is a single statement at the end, assume it's reachable.
                        // For example:
                        //     f()                             USE
                        //     while (...) { ... }             DO NOT USE.
                        //     foo: { ... }                    DO NOT USE.  Could break.
                        //     if (...) { ... } else { ... }   DO NOT USE.  Multiple possible results.
                        //     ... orelse ...                  DO NOT USE.  Multiple possible results.
                        // This leads to some possible oddities.
                        //     while (true) {}; 42             USE even though never reached.
                        // We can't determine statically if a function call or loop
                        // always/never/sometimes halts, so if we're ever to optimize anything out of
                        // blocks we just optimistically assume that all terminal nodes are reached
                        // and that any code that is optimized using this will not run if the preceding
                        // does not halt.
                        fun lookThrough(cf: ControlFlow): Boolean = when (cf) {
                            is ControlFlow.If,
                            is ControlFlow.Jump,
                            is ControlFlow.Labeled,
                            is ControlFlow.Loop,
                            is ControlFlow.OrElse,
                            -> false
                            is ControlFlow.Stmt -> cf.ref.index == lastIndex
                            is ControlFlow.StmtBlock -> cf.stmts.isNotEmpty() && lookThrough(cf.stmts.last())
                        }
                        lookThrough(flow.controlFlow)
                    }
                    orderForNodes to useLast
                }
            }
            var lastResult: PartialResult = void
            if (orderForNodes != null) {
                for (i in orderForNodes) {
                    lastResult = interpretEdge(ast.edge(i), mutableEnv, im)
                }
            } else {
                // In a LinearFlow, some macros insert in place, especially during disambiguate.
                var i = 0
                while (i < ast.size) {
                    val edge = ast.edge(i)
                    val nextEdge = ast.edgeOrNull(i + 1)
                    lastResult = interpretEdge(edge, mutableEnv, im)
                    i = when {
                        ast.edgeOrNull(i) == edge -> i + 1
                        edge.source == ast -> edge.edgeIndex + 1
                        nextEdge != null && nextEdge.source == ast -> nextEdge.edgeIndex
                        else -> i + 1
                    }
                }
            }
            return if (useLast && lastResult is Value<*>) {
                lastResult
            } else {
                NotYet
            }
        }

        check(im == InterpMode.Full) // Handled above
        var result: PartialResult = void
        fun interpretChild(
            ref: BlockChildReference,
            block: BlockTree,
            wantedType: TypeTag<*>? = null,
        ): PartialResult {
            val tree = block.dereference(ref)?.target
                ?: return Fail(
                    LogEntry(
                        Log.Error,
                        MessageTemplate.InvalidBlockContent,
                        ref.pos,
                        emptyList(),
                    ),
                )
            val r = interpretTree(tree, evaluation.envStack.last(), im)
            // Type checking the result here simplifies condition evaluation
            return if (
                wantedType != null && r is Value<*> &&
                wantedType.unpackOrNull(r) == null
            ) {
                Fail(
                    LogEntry(
                        Log.Error,
                        MessageTemplate.ExpectedValueOfType,
                        ref.pos,
                        listOf(wantedType, r),
                    ),
                )
            } else if (r is Fail && r.info == null) {
                // Add position info so that it's easier to diagnose which
                // statement in a top-level block caused failure.
                Fail(
                    LogEntry(
                        Log.Info,
                        MessageTemplate.Interpreting,
                        tree.pos,
                        emptyList(),
                    ),
                )
            } else {
                r
            }
        }

        fun popped(ip: InProgress) {
            if (ip.controlFlow is ControlFlow.StmtBlock) {
                val newTop = evaluation.stack.lastOrNull()
                if (
                    newTop?.controlFlow is ControlFlow.Loop &&
                    // When pushing the loop body we set loop state to
                    // BEFORE_INCREMENT since that's the next clause after it.
                    newTop.int == InProgress.LoopState.BEFORE_INCREMENT
                ) {
                    // Finished loop body
                    // Pop the per-loop-instance environment
                    evaluation.envStack.compatRemoveLast()
                }
            }
        }

        fun popStackUntil(predicate: (ControlFlow, InProgress) -> Boolean) {
            while (true) {
                val top = evaluation.stack.lastOrNull() ?: break
                if (predicate(top.controlFlow, top)) { break }
                popped(evaluation.stack.compatRemoveLast())
            }
        }

        fun handleFail(f: Fail) {
            popStackUntil { cf, _ ->
                cf is ControlFlow.OrElse
            }
            if (evaluation.stack.isEmpty()) {
                result = f
            } else {
                val orElseInProgress = evaluation.stack.compatRemoveLast()
                popped(orElseInProgress)
                val orElse = orElseInProgress.controlFlow as ControlFlow.OrElse
                evaluation.stack.add(InProgress(orElse.elseClause))
            }
        }
        eval_loop@
        while (result is Value<*>) {
            val top = evaluation.stack.lastOrNull()
            when (val cf = top?.controlFlow) {
                null -> break@eval_loop
                is ControlFlow.If -> {
                    // Interpret the condition and push either the then or else clauses
                    popped(evaluation.stack.compatRemoveLast())
                    when (val r = interpretChild(cf.condition, ast, TBoolean)) {
                        NotYet -> {
                            result = r
                        }
                        is Fail -> handleFail(r)
                        is Value<*> -> evaluation.stack.add(
                            InProgress(
                                if (TBoolean.unpack(r)) {
                                    cf.thenClause
                                } else {
                                    cf.elseClause
                                },
                            ),
                        )
                    }
                }
                is ControlFlow.Jump -> {
                    // Pop until we find the matching join point.
                    // If it's an OrElse's `or` clause that has not been woven, then jump to
                    // the `else` clause.
                    popStackUntil stopHere@{ it, ip ->
                        if (it is JumpDestination && it.matches(cf)) {
                            // We found a match, but if it's a loop, double check that we're
                            // coming from the body.
                            // In a case where an unlabeled `continue` occurs outside the body:
                            //
                            //   while (true) { // outer loop
                            //     while ( // block in loop condition
                            //       do { if (x) { continue } else { y } }
                            //     ) {
                            //       ...
                            //     }
                            //     ...
                            //     // The `continue` above lands here
                            //   }
                            val isInLoopOutsideBody = it is ControlFlow.Loop &&
                                ip.int != InProgress.LoopState.BEFORE_INCREMENT
                            !isInLoopOutsideBody
                        } else {
                            false
                        }
                    }
                    val newTop = evaluation.stack.lastOrNull()
                    val jumpDestination = newTop?.controlFlow
                        as? JumpDestination
                    if (jumpDestination == null) {
                        result = Fail(
                            LogEntry(
                                Log.Error,
                                MessageTemplate.UnresolvedJumpTarget,
                                cf.pos,
                                emptyList(),
                            ),
                        )
                    } else {
                        if (newTop.controlFlow is ControlFlow.Loop && cf is ControlFlow.Continue) {
                            // We came from the body, so newTop.int is good at BEFORE_INCREMENT
                        } else {
                            // A `continue` to a LabeledStmt with a continueLabel should
                            // act as a `break` to its end, so popping the last here is the
                            // right thing to do regardless.
                            popped(evaluation.stack.compatRemoveLast())
                            if (cf is ControlFlow.Break) {
                                // If the new top is an orelse, then we've just broken out of the
                                // `or` part.  Remove it and enqueue the else part.
                                val topAfterBreak = evaluation.stack.lastOrNull()?.controlFlow
                                if (topAfterBreak is ControlFlow.OrElse) {
                                    popped(evaluation.stack.compatRemoveLast())
                                    evaluation.stack.add(InProgress(topAfterBreak.elseClause))
                                }
                            }
                        }
                    }
                }
                is ControlFlow.OrElse -> {
                    val ranOr = top.int != 0
                    if (ranOr) {
                        popped(evaluation.stack.compatRemoveLast())
                    } else {
                        // Schedule the `or`.
                        evaluation.stack.add(InProgress(cf.orClause))
                        // Remember when we get back to this state, we need to pop it.
                        top.int = 1
                    }
                }
                is ControlFlow.Stmt -> {
                    // Run the statement, but pause evaluation if
                    // it's a yield statement.
                    val poppedForStmt = evaluation.stack.compatRemoveLast()
                    popped(poppedForStmt)
                    val yieldingCallDetails = disassembleYieldingCall(cf, ast)
                    if (yieldingCallDetails != null && bodyOwner !is TransientUserFunction) {
                        // No place to store yieldedAt state.
                        // Yielding functions should have an `extends GeneratorFn` or
                        // a similar clause with a sub-type thereof.
                        // That would cause creation of a transient function
                        // instead of a long-lived function which we might
                        // otherwise try to translate.
                        val cause = LogEntry(
                            Log.Error,
                            MessageTemplate.YieldingOutsideGeneratorFn,
                            yieldingCallDetails.yieldingCall.pos,
                            listOf(yieldingCallDetails.kind),
                        )
                        failLog.explain(cause)
                        result = Fail(cause)
                        break@eval_loop
                    }
                    when (yieldingCallDetails?.kind) {
                        null -> { // A regular statement
                            when (val refResult = interpretChild(cf.ref, ast)) {
                                is Value<*>, NotYet -> result = refResult
                                // handleFail will set result if appropriate
                                is Fail -> handleFail(refResult)
                            }
                        }
                        YieldingFnKind.yield -> {
                            check(bodyOwner is TransientUserFunction) // Checked above
                            bodyOwner.yieldedAt = evaluation
                            val call = yieldingCallDetails.yieldingCall
                            var yielded: PartialResult = TNull.value
                            if (call.size == 2) {
                                yielded = interpretEdge(call.edge(1), mutableEnv, im)
                            }
                            val makeValueResult = TFunction.unpack(
                                features.getValue(InternalFeatureKeys.MakeValueResult.featureKey),
                            ) as CallableValue
                            result = yielded.and {
                                makeValueResult.invoke(
                                    ActualValues.from(it),
                                    callbackFor(call.pos),
                                    im,
                                )
                            }
                            // The result is authoritative because
                            // we did not reach a return point so any
                            // value assigned to the `return__123`
                            // variable is premature.
                            break@eval_loop
                        }
                        YieldingFnKind.await -> {
                            check(bodyOwner is TransientUserFunction) // Checked above
                            val awaitCall = yieldingCallDetails.yieldingCall
                            val argValues = mutableListOf<Value<*>>()
                            for (i in 1 until awaitCall.size) {
                                when (val argResult = interpretEdge(awaitCall.edge(i), mutableEnv, im)) {
                                    NotYet -> {
                                        result = argResult
                                        break@eval_loop
                                    }
                                    is Fail -> handleFail(argResult)
                                    is Value<*> -> argValues.add(argResult)
                                }
                            }
                            val args = LazyActualsList(
                                awaitCall.children.subListToEnd(1),
                                this,
                                mutableEnv,
                                im,
                            )
                            val cb = callbackFor(awaitCall)
                            val awaiter = Promises.Awaiter(cf.pos, bodyOwner)
                            val awaitResult = withMacroEnvironment(awaitCall, awaitCall.child(0), args, cb) {
                                it.withAwaiter(awaiter) {
                                    AwaitFn.invoke(it, im)
                                }
                            }
                            val failVar = yieldingCallDetails.failVar
                            val assignedTo = yieldingCallDetails.assignedTo
                            when (awaitResult) {
                                is NotYet -> {
                                    result = awaitResult
                                    // We're pushing it back on so that next time through,
                                    // we'll check for promise resolution again.
                                    evaluation.stack.add(poppedForStmt)
                                    bodyOwner.yieldedAt = evaluation
                                    break@eval_loop
                                }
                                is Fail -> if (failVar != null) {
                                    mutableEnv.set(failVar, TBoolean.valueTrue, cb)
                                } else {
                                    handleFail(awaitResult)
                                }
                                is Value<*> -> {
                                    if (failVar != null) {
                                        (mutableEnv.set(failVar, TBoolean.valueFalse, cb) as? Fail)
                                            ?.let { handleFail(it) }
                                    }
                                    if (assignedTo != null) {
                                        (mutableEnv.set(assignedTo, awaitResult, cb) as? Fail)
                                            ?.let { handleFail(it) }
                                    }
                                    result = awaitResult
                                }
                            }
                        }
                    }
                }
                is ControlFlow.Loop -> {
                    val loopState = top.int
                    top.int = InProgress.LoopState.next(loopState)
                    when (loopState) {
                        InProgress.LoopState.BEFORE_BODY -> {
                            // Push the body
                            evaluation.stack.add(InProgress(cf.body))
                            // Add an environment which is cleaned up after by popped(...)
                            evaluation.envStack.add(BlockEnvironment(evaluation.envStack.last()))
                        }
                        InProgress.LoopState.BEFORE_INCREMENT -> {
                            evaluation.stack.add(InProgress(cf.increment))
                        }
                        InProgress.LoopState.BEFORE_CONDITION -> {
                            // Run the condition.
                            var runBody = false
                            when (val r = interpretChild(cf.condition, ast, TBoolean)) {
                                NotYet -> {
                                    result = NotYet
                                }
                                is Fail -> handleFail(r)
                                is Value<*> -> {
                                    runBody = TBoolean.unpack(r)
                                }
                            }
                            if (!runBody) {
                                // Pop the loop
                                popped(evaluation.stack.compatRemoveLast())
                            }
                        }
                    }
                }
                is ControlFlow.StmtBlock -> {
                    // If the index is at the end, pop, possibly continuing a loop.
                    // Otherwise, schedule the next.
                    val stmtIndex = top.int
                    val stmts = cf.stmts

                    if (stmtIndex < stmts.size) {
                        val child = stmts[stmtIndex]
                        top.int += 1
                        evaluation.stack.add(InProgress(child))
                    } else {
                        popped(evaluation.stack.compatRemoveLast())
                    }
                }
                is ControlFlow.Labeled -> {
                    val hasStarted = top.int != 0
                    if (!hasStarted) {
                        // Push the block.
                        evaluation.stack.add(InProgress(cf.stmts))
                        // Leave this on the stack so that we can detect it when we see a break,
                        // and set hasRun so that if the body exits without a break/continue,
                        // we go to the `else` below instead of running the body again.
                        top.int = 1
                    } else {
                        popped(evaluation.stack.compatRemoveLast())
                    }
                }
            }
        }
        return result
    }

    private fun dispatchCall(
        calleeTree: Tree,
        calleeValue: Value<*>,
        argTrees: List<Tree>,
        env: Environment,
        ast: CallTree?,
        im: InterpMode,
    ): PartialResult {
        val actuals = LazyActualsList(argTrees, this, env, im)
        val pos = ast?.pos ?: argTrees.spanningPosition(calleeTree.pos)
        val cb = callbackFor(pos)
        val (f, arguments) = when (val cf = TFunction.unpackOrNull(calleeValue)) {
            is CoverFunction ->
                try {
                    cf.uncover(actuals, cb, im)
                } catch (panic: Panic) { // TODO: is this necessary?
                    ignore(panic)
                    null
                }
                    ?: return NotYet
            else -> calleeValue to null
        }

        if (f !is Value<*>) {
            return Fail
        }

        val fUnpacked = TFunction.unpackOrNull(f)
            ?: run {
                failLog.explain(
                    MessageTemplate.ExpectedValueOfType,
                    calleeTree.pos,
                    listOf(f.typeTag),
                )
                return@dispatchCall Fail
            }
        if (ast != null && fUnpacked.assignsArgumentOne && im == InterpMode.Partial) {
            // Fixup right names to left names where necessary to better match macro signatures.
            val arg1 = argTrees.getOrNull(0)
            if (arg1 is RightNameLeaf) {
                val edge1 = ast.edgeOrNull(1)
                if (arg1 == edge1?.target) {
                    edge1.replace(arg1.copyLeft())
                }
            }
        }

        val functionSpecies = fUnpacked.functionSpecies
        val strategy = callStrategies.getValue(Pair(functionSpecies, im))
        if (beSpammy(SPAMMY_DISPATCH)) {
            console.log(
                "$stage: Using call strategy $strategy for ($f):${
                    fUnpacked.functionSpecies}/$im at ${ast?.pos}",
            )
            if (beSpammy(SPAMMY)) {
                console.log(ast?.toLispy(true) ?: "Missing AST")
            }
        }
        return when (strategy) {
            CallStrategy.CallWithActualValues, CallStrategy.CallFullAndInlineResult -> {
                check(fUnpacked is CallableValue)
                val isPureInlineAttempt = strategy == CallStrategy.CallFullAndInlineResult

                // User functions can have optional arguments without default expressions since
                // they can internally call isSet.
                val asUserFn = fUnpacked as? UserFunction

                val actualActuals = when {
                    arguments == null -> {
                        val values = actuals.indices.map {
                            when (val result = actuals.result(it)) {
                                is Fail, NotYet -> {
                                    if (ast != null) {
                                        maybeVisitChildren(ast, env, im)
                                    }
                                    return@dispatchCall result
                                }
                                is Value<*> -> result
                            }
                        }
                        // We presumably don't get here for cover functions, so sigs is likely singular.
                        PrecomputedActualValues(actuals, values, fUnpacked.sigs?.firstOrNull())
                    }
                    asUserFn != null -> arguments.toFullyNamedActualsInOrder(cb)
                    else -> arguments.toPositionalActuals(cb) ?: return Fail
                }

                if (isPureInlineAttempt) {
                    if (
                        // If there's nothing to replace, don't incur the cost of the call.
                        ast == null ||
                        (
                            // Things are fluid before name resolution
                            stage <= Stage.SyntaxMacro &&
                                // We visited all the children above and got values, so we know
                                // we've forced evaluation of any macros there.
                                // so if they're all values, we can collapse things early,
                                // but otherwise we'd better bail.
                                ast.children.any { it.valueContained == null }
                            )
                    ) {
                        if (ast != null) {
                            maybeVisitChildren(ast, env, im)
                        }
                        return NotYet
                    }
                    // We're ok calling pure functions with unstable inputs as long as they
                    // produce a stable output.  For example, tuple member access might extract
                    // a stable part from an unstable tuple.
                }
                val edge = ast?.incoming
                var shouldAttemptToInline =
                    isPureInlineAttempt && edge != null && !ast.isPreserved &&
                        calleeValue != BuiltinFuns.vPreserveFn
                val result = try {
                    when (asUserFn) {
                        null -> {
                            val actualValues = actualActuals as ActualValues
                            if (shouldAttemptToInline) {
                                shouldAttemptToInline = fUnpacked.mayReplaceCallWithArgs(actualValues)
                            }
                            fUnpacked(actualValues, cb, InterpMode.Full)
                        }
                        else -> asUserFn.invokeUnpositioned(actualActuals, cb, InterpMode.Full)
                    }
                } catch (panic: Panic) {
                    if (im == InterpMode.Partial) {
                        // This still is partial evaluation, and we tried. Don't actually panic.
                        return NotYet
                    } else {
                        throw panic
                    }
                }

                // Try to constant fold
                val valueToInline = if (shouldAttemptToInline) {
                    result as? Value<*>
                } else {
                    null
                }
                if (valueToInline?.stability == ValueStability.Stable) {
                    check(edge != null) // Otherwise shouldInline is false
                    // Inline the result of a pure function.
                    val replacementsAndBreadcrumbs = mutableListOf<Pair<Tree, Stage?>>()
                    when (replacementPolicy) {
                        ReplacementPolicy.Discard -> {
                            ast.edges.forEachIndexed { index, kidEdge ->
                                val child = kidEdge.target
                                val mustRemain = mayEffect(child, isCallee = index == 0) || hasStayLeaf(child)
                                if (mustRemain) {
                                    // if it might have an effect, preserve it
                                    replacementsAndBreadcrumbs.add(freeTarget(kidEdge) to kidEdge.breadcrumb)
                                }
                            }
                            replacementsAndBreadcrumbs.add(
                                ValueLeaf(ast.document, ast.pos, valueToInline) to null,
                            )
                        }
                        ReplacementPolicy.Preserve -> {
                            val preserveCall = ast.document.treeFarm.grow {
                                Call(ast.pos, BuiltinFuns.preserveFn) {
                                    Replant(freeTree(ast))
                                    V(ast.pos, valueToInline)
                                }
                            }
                            preserveCall.edge(1).breadcrumb = stage
                            replacementsAndBreadcrumbs.add(preserveCall to stage)
                        }
                    }
                    val replacement = if (replacementsAndBreadcrumbs.size == 1) {
                        replacementsAndBreadcrumbs[0].first
                    } else {
                        val block = BlockTree(
                            ast.document,
                            ast.pos,
                            replacementsAndBreadcrumbs.map { it.first },
                            LinearFlow,
                        )
                        block.edges.forEachIndexed { index, newEdge ->
                            newEdge.breadcrumb =
                                replacementsAndBreadcrumbs[index].second
                        }
                        block
                    }
                    edge.replace(replacement)
                }
                result
            }
            CallStrategy.CallInMacroEnvForResult ->
                withMacroEnvironment(ast, calleeTree, actuals, cb) {
                    fUnpacked(it, im)
                }
            CallStrategy.InterpretChildrenPartially -> {
                if (ast != null) {
                    maybeVisitChildren(ast, env, im)
                }
                NotYet
            }
            CallStrategy.Fail -> Fail
            CallStrategy.CallAsMacroAndMaybeReplace -> {
                val macroEdge = ast?.incoming
                val calleeNameBefore = calleeName(ast)
                val macroSignatures = fUnpacked.sigs
                if (macroSignatures != null && im == InterpMode.Partial) {
                    // If structure expectations fail, then recurse to children first.  Expanding
                    // any macros there may cause structure expectations to pass.
                    // Otherwise, do not eagerly evaluate arguments.
                    MacroCallOrderer(ast, actuals, cb, macroSignatures)
                        .preEvaluateAsNeeded()
                }
                val (result, finishedEnv) = withMacroEnvironment(ast, calleeTree, actuals, cb) {
                    fUnpacked(it, im) to it
                }
                // A macro may replace its call with something that needs to be examined.
                val targetAfter = macroEdge?.target
                if (targetAfter != null) {
                    if (targetAfter !== ast) {
                        // Macro replaced itself.
                        val calleeNameAfter = calleeName(targetAfter)
                        macroEdge.breadcrumb =
                            if (calleeNameBefore != null && calleeNameBefore == calleeNameAfter) {
                                // When a macro replaces itself with a macro of the same name, treat
                                // that as a signal that it wants to be evaluated at a later stage
                                // per bash `alias` rules:
                                // > The first word of the replacement text is tested for aliases,
                                // > but a word that is identical to an alias being expanded is not
                                // > expanded a second time. This means that one may alias ls to
                                // > “ls -F”, for instance, and Bash does not try to recursively
                                // > expand the replacement text.
                                stage
                            } else {
                                // We might need to expand any macro call in the replacement.
                                null
                            }
                    }
                    // Macro calls don't always evaluate their actuals, and we might need to expand
                    // macros in the replacement.
                    // We call interpret edge again, which, if the breadcrumb is stage will skip the
                    // call and recurse to child edges looking for unexpanded macro calls.
                    // The condition below should always be true, but just be safe before possibly
                    // infinitely recursing.
                    if (im == InterpMode.Partial) {
                        interpretEdge(macroEdge, env, im)
                    }
                }

                // A macro call might replace an ancestor in which case we should inspect that ancestor
                // replacement for synthesized nodes that need to be marked as reached.
                finishedEnv.ancestorReplaced?.let { ancestorReplaced ->
                    maybeVisitChildren(ancestorReplaced.target, env, im)
                }

                return when (functionSpecies) {
                    // Normally, macros are evaluated for their effect on the tree and not for their
                    // computed result.
                    // I wrote macros that return void to indicate not-failure and haven't yet
                    // audited them.  I worry that using void from macro calls would propagate badly
                    // into inline the results of const reads and pure function inlining.
                    FunctionSpecies.Macro -> NotYet
                    FunctionSpecies.Special -> result
                    FunctionSpecies.Normal, FunctionSpecies.Pure ->
                        error("unreachable by strategy table")
                }
            }
        }
    }

    private fun interpretCall(
        ast: CallTree,
        env: Environment,
        im: InterpMode,
    ): PartialResult {
        val n = ast.size
        if (n == 0) {
            return Fail
        }

        val callee = ast.edge(0)
        val result = when (val fResult = interpretEdge(callee, env, im)) {
            is Fail, NotYet -> {
                fResult
            }
            is Value<*> -> dispatchCall(
                callee.target,
                fResult,
                ast.children.subListToEnd(1),
                env,
                ast,
                im,
            )
        }
        // Calls may negotiate macro calls for children out of order, but as a result, dispatchCall
        // does not guarantee that it visits all children.
        maybeVisitChildren(ast, env, im)
        return result
    }

    private fun fullyEvaluateDeclParts(
        d: DeclParts,
        env: Environment,
    ): EvaluatedDeclParts {
        val (name, symbolEdge, type) = d

        var missing = 0

        val symbol: Symbol? =
            if (symbolEdge != null) {
                when (val r = interpretEdge(symbolEdge, env, InterpMode.Full)) {
                    NotYet, is Fail -> {
                        missing = ReferentBit.Symbol or missing
                        null
                    }
                    is Value<*> -> {
                        if (r.typeTag == TSymbol) {
                            TSymbol.unpack(r)
                        } else {
                            failLog.explain(
                                MessageTemplate.ExpectedValueOfType,
                                pos = symbolEdge.target.pos,
                                values = listOf(TSymbol),
                            )
                            missing = ReferentBit.Symbol or missing
                            null
                        }
                    }
                }
            } else {
                name.content.toSymbol()
            }

        val typeAsValue: Value<*>? =
            if (type != null) {
                when (val r = interpretEdge(type, env, InterpMode.Full)) {
                    NotYet, is Fail -> {
                        missing = ReferentBit.Type or missing
                        null
                    }
                    is Value<*> -> r
                }
            } else {
                null
            }

        val initializer = d.metadataSymbolMap[initSymbol]
        val initialValue: Value<*>? = initializer?.let {
            when (val r = interpretEdge(it, env, InterpMode.Full)) {
                NotYet, is Fail -> null
                is Value<*> -> r
            }
        }
        if (initializer != null && initialValue == null) {
            missing = ReferentBit.Initial or missing
        }

        val defaultExpr = d.metadataSymbolMap[defaultSymbol]
        val defaultExprValue: Value<*>? = defaultExpr?.let {
            Value(Thunk(it, env))
        }

        // TODO Could iterating (above here as well) be faster than repeated queries???
        val constness = if (varSymbol in d.metadataSymbolMap) {
            Constness.NotConst
        } else {
            // TODO: if metadata is incomplete, for example, because there is a metadata key that
            //  has not yet reduced to a value leaf, constness bit should be missing
            Constness.Const
        }

        val referentSource = if (ssaSymbol in d.metadataSymbolMap) {
            ReferentSource.SingleSourceAssigned
        } else {
            ReferentSource.Unknown
        }

        val metadataMap = d.metadataSymbolMap.mapValues {
            interpretEdge(it.value, env, InterpMode.Full) as? Value<*>
        }

        return EvaluatedDeclParts(
            name = d.name.content,
            symbol = symbol,
            type = typeAsValue,
            init = initialValue,
            defaultExpr = defaultExprValue,
            constness = constness,
            referentSource = referentSource,
            missing = ReferentBitSet.forBitMask(missing),
            metadataMap = metadataMap,
        )
    }

    private fun partiallyEvaluateDeclParts(
        t: DeclTree,
        env: Environment,
    ): EvaluatedDeclParts {
        val size = t.size
        val name = if (size != 0) {
            val edge0 = t.edge(0)
            val child0 = edge0.target
            // Make sure we don't try to interpret a misplaced right name
            if (child0 is RightNameLeaf) {
                edge0.replace(child0.copyLeft())
            }
            // Expand any macros that might convert to a name
            interpretEdge(edge0, env, InterpMode.Partial)
            val edge0Post = t.edgeOrNull(0)
            (edge0Post?.target as? LeftNameLeaf)?.content
        } else {
            null
        }

        val metadataMap = mutableMapOf<Symbol, Value<*>?>()
        var childIndex = 1
        var missing = 0 // ReferentBit bits

        var initialValue: Value<*>? = null
        var defaultExprValue: Value<*>? = null
        var symbolFromName: Symbol? = null
        var typeAsValue: Value<*>? = null

        while (childIndex + 1 < t.size) {
            val symbolEdge = t.edge(childIndex)
            val symbolResult = interpretEdge(symbolEdge, env, InterpMode.Partial)
            val symbol = TSymbol.unpackOrNull(symbolResult as? Value<*>)
            val valueEdge = t.edge(childIndex + 1)
            val valueResult = interpretEdge(valueEdge, env, InterpMode.Partial)
            val value = valueResult as? Value<*>
            childIndex += 2
            when (symbol) {
                null -> Unit
                initSymbol -> {
                    if (value == null) {
                        missing = missing or ReferentBit.Initial.bit
                    }
                    initialValue = value
                }
                defaultSymbol -> {
                    defaultExprValue = Value(Thunk(valueEdge, env))
                }
                typeSymbol -> {
                    typeAsValue = value ?: run {
                        missing = missing or ReferentBit.Type.bit
                        null
                    }
                }
                wordSymbol -> {
                    symbolFromName = TSymbol.unpackOrNull(value)
                        ?: run {
                            missing = missing or ReferentBit.Symbol.bit
                            null
                        }
                }
                else -> {
                    metadataMap[symbol] = value
                }
            }
        }

        val constness = if (varSymbol in metadataMap) {
            Constness.NotConst
        } else {
            Constness.Const
        }

        val referentSource = if (ssaSymbol in metadataMap) {
            ReferentSource.SingleSourceAssigned
        } else {
            ReferentSource.Unknown
        }

        return EvaluatedDeclParts(
            name = name,
            symbol = symbolFromName,
            type = typeAsValue,
            init = initialValue,
            defaultExpr = defaultExprValue,
            constness = constness,
            referentSource = referentSource,
            missing = ReferentBitSet.forBitMask(missing),
            metadataMap = metadataMap.toMap(),
        )
    }

    private fun interpretDecl(
        ast: DeclTree,
        env: Environment,
        im: InterpMode,
    ): PartialResult {
        val nameAndParts = when (im) {
            InterpMode.Partial -> {
                if (ast.size == 0) {
                    null
                } else {
                    val e0 = ast.edge(0)
                    val c0 = e0.target
                    if (c0 is RightNameLeaf) {
                        e0.replace(c0.copyLeft())
                    }
                    interpretEdge(e0, env, im)
                    val c0Processed = ast.edgeOrNull(0)?.target
                    val ed = partiallyEvaluateDeclParts(ast, env)
                    if (c0Processed is LeftNameLeaf) {
                        c0Processed.content to ed
                    } else {
                        null
                    }
                }
            }
            InterpMode.Full -> {
                val d = ast.parts
                if (d == null) {
                    null
                } else {
                    d.name.content to fullyEvaluateDeclParts(d, env)
                }
            }
        }
        if (nameAndParts == null) {
            val fail = if (stage > Stage.Import) {
                callbackFor(ast.pos).fail(MessageTemplate.MalformedDeclaration)
            } else {
                // Before the import stage ends, we don't assume that declarations are well-formed.
                // This could be extended through the SyntaxMacro stage.
                Fail
            }
            maybeVisitChildren(ast, env, im)
            return fail
        }
        val (name, ed) = nameAndParts
        var missing = ed.missing
        val init = when (im) {
            InterpMode.Full -> ed.init
            InterpMode.Partial -> if (
                // Don't need to worry about ReferentSource when const, since the source is the
                // initializer, but before the DisAmbiguate stage ends, we can't distinguish between
                // initializers and default expressions since we don't know which declarations are
                // function parameters.
                (ed.constness == Constness.Const && stage > Stage.DisAmbiguate) ||
                ed.referentSource == ReferentSource.SingleSourceAssigned
            ) {
                ed.init
            } else {
                // Do not actually change the environment if this is a non-const initialization
                // since changes / reads might happen out of order or not at all as partial
                // evaluation continues.
                if (ed.init != null) {
                    missing = missing or ReferentBit.Initial
                }
                null
            }
        }
        val declarationBits = DeclarationBits(
            reifiedType = ed.type,
            initial = init,
            constness = ed.constness,
            referentSource = ed.referentSource,
            missing = missing,
            declarationSite = ast.pos,
            tracksFail = failSymbol in ed.metadataMap,
        )
        return when (val result = env.declare(name, declarationBits, callbackFor(ast))) {
            is Fail -> result
            NotYet -> NotYet
            is Value<*> -> when (im) {
                InterpMode.Partial -> NotYet
                InterpMode.Full -> void
            }
        }
    }

    private fun interpretEsc(ast: EscTree, env: Environment, im: InterpMode): PartialResult {
        return if (im == InterpMode.Partial) {
            preemptEscapedCalls(ast, env, im)
            maybeVisitChildren(ast, env, im)
            NotYet
        } else {
            failLog.explain(MessageTemplate.UnsupportedByInterpreter, ast.pos)
            // TODO, skip over escaped parts but visit macros in hole expressions
            Fail
        }
    }

    private fun preemptEscapedCalls(ast: EscTree, env: Environment, im: InterpMode) {
        // Do not expand macros inside escaped sections by pre-emptively marking
        // them as done.
        val holes = mutableListOf<Tree>()
        TreeVisit.startingAt(ast)
            .forEach { descendant ->
                val descendantEdge = descendant.incoming!! // Reached from ast
                val dBreadcrumb = descendant.incoming?.breadcrumb
                val isHole = isQuasiHole(descendant)
                if (dBreadcrumb == null || dBreadcrumb < stage) {
                    descendantEdge.breadcrumb = stage
                    if (isHole) {
                        holes.add(descendant)
                    }
                }
                if (isHole) {
                    // Do not mark macros in holes done.
                    return@forEach VisitCue.SkipOne
                }
                VisitCue.Continue
            }
            .visitPreOrder()
        holes.forEach { maybeVisitChildren(it, env, im) }
    }

    private fun interpretFun(
        ast: FunTree,
        definingEnv: Environment,
        im: InterpMode,
    ): PartialResult {
        // This code mirrors that in decomposeFun, but we do not use that so that we can expand
        // macros which might, for example, replace a macro call with a formal variable declaration.

        // If we're in partial mode, descend first to children to expand any macros so that our
        // function stability check below benefits from any impurity reducing substitutions.

        if (im == InterpMode.Partial) {
            val exampleStackFrame = blankEnvironment(definingEnv)
            maybeVisitChildren(ast, exampleStackFrame, im)
        }

        // When expanding macros, we need to have a stack frame with formal/return parameters in
        // scope, masking anything in env
        val partialFunEnv = when (im) {
            InterpMode.Partial -> blankEnvironment(definingEnv)
            InterpMode.Full -> null
        }

        // Process all children capturing:
        // - the edge
        // - result
        // - any decl parts if it is a declaration and not the body
        // After processing everything, including macro expansions, we will classify these
        // as formals/return parameter declarations, or other metadata
        val postProcessedEdges =
            mutableListOf<Triple<TEdge, PartialResult, EvaluatedDeclParts?>>()
        forEachChildMacroSensitively(ast) { edge ->
            val target = edge.target
            postProcessedEdges.add(
                when {
                    edge == ast.edgeOrNull(ast.size - 1) -> { // Body edge.
                        if (im == InterpMode.Partial) { // Expand macros
                            interpretEdge(edge, partialFunEnv!!, im)
                        }
                        Triple(edge, NotYet, null)
                    }
                    target is DeclTree -> {
                        val declParts: EvaluatedDeclParts?
                        when (im) {
                            InterpMode.Partial -> {
                                declParts = partiallyEvaluateDeclParts(
                                    // Function argument initializers are not always evaluated.
                                    target,
                                    partialFunEnv!!,
                                )
                                val name = declParts.name
                                if (name != null) {
                                    partialFunEnv.declare(
                                        declParts.name,
                                        DeclarationBits(
                                            reifiedType = declParts.type,
                                            initial = null,
                                            constness = declParts.constness,
                                            referentSource = declParts.referentSource,
                                            missing = declParts.missing,
                                            declarationSite = target.pos,
                                        ),
                                        callbackFor(target),
                                    )
                                }
                            }
                            InterpMode.Full ->
                                when (val targetParts = target.parts) {
                                    null -> {
                                        failLog.explain(
                                            MessageTemplate.MalformedDeclaration,
                                            target.pos,
                                        )
                                        declParts = null
                                    }
                                    else -> declParts = fullyEvaluateDeclParts(targetParts, definingEnv)
                                }
                        }
                        Triple(edge, NotYet, declParts)
                    }
                    else -> { // Neither body nor declaration.  Probably metadata
                        Triple(edge, interpretEdge(edge, partialFunEnv ?: definingEnv, im), null)
                    }
                },
            )
        }

        // Keep track of some things as we examine children.
        var childIndex = 0
        var declsOk = true // True until we find a problem with a formal/return parameter decl
        // Map formal names to their positional index, so we can reorder named arguments in calls
        val formalNamesByIndex = mutableListOf<TemperName>()
        // Formals in positional order
        val interpFormals = mutableListOf<InterpValueFormal>()
        // Any return parameter declaration
        var returnDecl: Triple<TEdge, PartialResult, EvaluatedDeclParts?>? = null
        var fnName: Symbol? = null // Name of function for diagnostic purposes
        var symbolsOk = true // True if symbol metadata pairs have no errors
        val superTypeSet = mutableSetOf<NominalType>()
        var hasThisFormal = false

        val nPostProcessedEdges = postProcessedEdges.size
        val bodyIndex = nPostProcessedEdges - 1
        while (childIndex < bodyIndex) {
            val (edge, _, declParts) = postProcessedEdges[childIndex]
            val child = edge.target
            if (child !is DeclTree) {
                // Found the end of the run of formal declarations
                break
            }
            childIndex += 1

            val name = declParts?.name
            if (declParts == null || name == null) {
                failLog.explain(MessageTemplate.MalformedDeclaration, child.pos)
                declsOk = false
                continue
            }

            val typeAsValue = declParts.type
            val reifiedType = if (typeAsValue != null) {
                val sv = TType.unpackOrNull(typeAsValue)
                if (sv == null) {
                    val d = child.parts
                    failLog.explain(
                        MessageTemplate.ExpectedValueOfType,
                        pos = (d?.type?.target ?: ast).pos,
                        values = listOf(TType),
                    )
                    declsOk = false
                    continue
                } else {
                    sv
                }
            } else {
                null
            }

            @Suppress("UNCHECKED_CAST")
            val defaultExprFn = declParts.defaultExpr as Value<MacroValue>?
            if (!declParts.isRestFormal()) {
                if (impliedThisSymbol in declParts.metadataMap) {
                    hasThisFormal = true
                }
                interpFormals.add(
                    InterpValueFormal(
                        symbol = declParts.symbol,
                        reifiedType = reifiedType,
                        // Safe because of flag to evaluateDeclParts
                        kind = if (declParts.isOptional.elseTrue || defaultExprFn != null) {
                            ValueFormalKind.Optional
                        } else {
                            ValueFormalKind.Required
                        },
                        constness = declParts.constness,
                        defaultExpr = defaultExprFn,
                        missing = declParts.missing,
                    ),
                )
                formalNamesByIndex.add(name)
            }
        }

        // Consider metadata (symbol, value) pairs
        val typeFormals = mutableListOf<TypeFormal>()
        val metadataStart = childIndex
        while (childIndex + 2 <= bodyIndex) {
            val keyResult = postProcessedEdges[childIndex].second
            val valueEdgeParts = postProcessedEdges[childIndex + 1]
            val valueResult = valueEdgeParts.second
            childIndex += 2

            if (keyResult !is Value<*> || keyResult.typeTag != TSymbol) {
                // TODO: explain
                symbolsOk = false
            } else {
                when (TSymbol.unpack(keyResult)) {
                    returnDeclSymbol -> {
                        returnDecl = valueEdgeParts
                    }
                    wordSymbol -> {
                        fnName = TSymbol.unpackOrNull(valueResult as? Value<*>)
                    }
                    typeFormalSymbol -> {
                        val typeFormal = FnParts.unpackTypeFormal(valueEdgeParts.first.target)
                        if (typeFormal != null) {
                            typeFormals.add(typeFormal)
                        } else {
                            // TODO: explain
                            symbolsOk = false
                        }
                    }
                    superSymbol -> {
                        val superRType =
                            TType.unpackOrNull(valueResult as? Value<*>)
                        fun addSupersTo(
                            superType: StaticType?,
                            out: MutableCollection<NominalType>,
                        ): Boolean = when (superType) {
                            is NominalType -> {
                                out.add(superType)
                                // Do not allow extending a type formal as in
                                //     let f<T extends Function>(): ... {
                                //       callee { () extends T =>
                                //         ...
                                //       }
                                //     }
                                superType.definition is TypeShape
                            }

                            is AndType -> {
                                // Given `extends A & B`, recurse for `A` and `B`
                                var allOk = true
                                for (member in superType.members) {
                                    if (!addSupersTo(member, out)) {
                                        allOk = false
                                    }
                                }
                                allOk
                            }

                            else -> false
                        }
                        symbolsOk = addSupersTo(superRType?.type, superTypeSet) && symbolsOk
                    }
                    else -> Unit
                }
            }
        }

        val body = postProcessedEdges.lastOrNull()?.first

        if (childIndex != bodyIndex) {
            symbolsOk = false // TODO: explain
        }

        val returnType = when (returnDecl) {
            null -> null
            else -> {
                val parts = returnDecl.third
                var reifiedType: ReifiedType? = null
                if (parts != null) {
                    val typeAsValue = parts.type
                    if (typeAsValue != null) {
                        val sv = TType.unpackOrNull(typeAsValue)
                        if (sv == null) {
                            val dp = (returnDecl.first.target as DeclTree).parts
                            failLog.explain(
                                MessageTemplate.ExpectedValueOfType,
                                pos = (dp?.type?.target ?: ast).pos,
                                values = listOf(TFunction),
                            )
                            declsOk = false
                        } else {
                            reifiedType = sv
                        }
                    }
                } else {
                    // TODO: explain
                    declsOk = false
                }
                reifiedType
            }
        }

        val parts = ast.parts
        val restType = parts?.restFormal?.let {
            formalNamesByIndex.add(it.name)
            ReifiedType(hackMapOldStyleToNew(it.type))
        }
        val requiredInputTypes = mutableListOf<Type2>()
        val optionalInputTypes = mutableListOf<Type2>()
        for (interpFormal in interpFormals) {
            val ls = when (interpFormal.kind) {
                ValueFormalKind.Required -> requiredInputTypes
                ValueFormalKind.Optional -> optionalInputTypes
                ValueFormalKind.Rest -> error("$interpFormal")
            }
            ls.add(interpFormal.type ?: WellKnownTypes.anyValueOrNullType2)
        }
        val signature = Signature2(
            returnType2 = (returnType ?: Types.anyValueOrNull).type2,
            hasThisFormal = hasThisFormal,
            requiredInputTypes = requiredInputTypes.toList(),
            optionalInputTypes = optionalInputTypes.toList(),
            restInputsType = restType?.type2,
            typeFormals = typeFormals.toList(),
        )
        val connected = connecteds[parts?.connected]?.let { it(signature) }

        val superTypes = SuperTypeTree(superTypeSet)
        // We use decomposeFun (I know I said above we wouldn't, but now we've expanded macros) just
        // to find the stage range.
        val stageRange: EnumRange<Stage>? = parts?.stageRange
        // A generator factory is any block lambda marked as a sub-type of GeneratorFn.
        val isGeneratorFactory =
            !superTypes.byDefinition[WellKnownTypes.generatorFnTypeDefinition]
                .isNullOrEmpty()
        val isUnwrappedGeneratorFactory = isGeneratorFactory &&
            parts?.metadataSymbolMultimap?.containsKey(wrappedGeneratorFnSymbol) == false
        val isTransient =
            // functionStability assumes that it can distinguish local names from free names without
            // careful scope analysis which is reasonable after name resolution but not before.
            stage <= Stage.SyntaxMacro ||
                // This should be redundant with the stage check, but it gives us extra confidence
                // for some unchecked casts below when creating a long-lived user function.
                !ast.document.isResolved
        val functionStability = when (connected?.stability) {
            ValueStability.Stable -> FunctionStability.SelfContained
            ValueStability.Unstable -> FunctionStability.Unstable
            null -> functionStability(ast, definingEnv)
        }
        val produceFunctionValue = when {
            im == InterpMode.Full -> true
            isUnwrappedGeneratorFactory -> false
            // Calls to stable functions may be inlined and/or exported, so produce values for them.
            functionStability != FunctionStability.Unstable -> true
            // If it's a user-defined macro that's going out of style, we should try to apply it.
            goingOutOfStyle -> true
            (stageRange != null && stage in stageRange) -> true
            // Produce exports
            stage == Stage.GenerateCode -> true
            else -> false
        }

        if (isUnwrappedGeneratorFactory) {
            // If we're visiting this GeneratorFn as part of macro processing, so do
            // not yet need a function value, insert a wrapper function around it.
            // This needs to happen before typing and before using it as part of
            // a runtime stage.
            if (im != InterpMode.Partial || produceFunctionValue) {
                // We can't use an unwrapped GeneratorFn in an early pure-function
                // evaluation.  (We could if we really had to via a separate
                // UserFunction application code path.)
                return NotYet
            }
            @Suppress("SENSELESS_COMPARISON")
            check(parts != null)

            // Before wrapping:
            //
            //     _ { (x: ArgType): YieldType extends GeneratorFn =>
            //       bodyStuff();
            //     }
            //
            // After wrapping, there is a function value that returns a generator
            // backed by the inner function.
            // This allows the receiver to construct an instance without calling it.
            //
            //     _ { (x: ArgType): Generator<YieldType, Never> =>
            //       adaptGeneratorFn(
            //         @wrappedGeneratorFn fn (): YieldType extends GeneratorFn {
            //           bodyStuff();
            //         }
            //       );
            //     }
            //
            // Note that the super types (and other metadata) remain on the inner function
            // but the arguments and type parameters move onto the outer function.
            val fnEdge = ast.incoming
            if (fnEdge != null) {
                // separate out the arguments and type formals
                val argDecls = buildList { addAll(parts.formals) }
                val typeFormalsList = (
                    parts.metadataSymbolMultimap[typeFormalSymbol]
                        ?: emptyList()
                    ).map { it.target }

                val indicesOfEdgesToRemove = KBitSet(ast.size)
                argDecls.forEach {
                    indicesOfEdgesToRemove.set(it.incoming!!.edgeIndex)
                }
                typeFormalsList.forEach {
                    val edgeIndex = it.incoming!!.edgeIndex
                    indicesOfEdgesToRemove.set(edgeIndex - 1) // \formalSymbol metadata key
                    indicesOfEdgesToRemove.set(edgeIndex)
                }

                // Unlink these from the inner function.  Below we replant them in the outer function.
                indicesOfEdgesToRemove.rangesOfSetBits().compatReversed().forEach {
                    ast.removeChildren(it)
                }

                // create a return name which will get assigned the function tree
                val outerReturnName = ast.document.nameMaker.unusedSourceName(returnParsedName)
                val useSafeVariant = if (returnType != null) {
                    // If we have an explicit return type, and it does not bubble,
                    // use the SafeGenerator variant.
                    val staticType = returnType.type
                    !staticType.mentionsInvalid && MkType.and(staticType, BubbleType).isNeverType
                } else {
                    false
                }
                val adaptFn = features.getValue(
                    (
                        if (useSafeVariant) {
                            InternalFeatureKeys.SafeAdaptGeneratorFn
                        } else {
                            InternalFeatureKeys.AdaptGeneratorFn
                        }
                        ).featureKey,
                )

                fnEdge.replace {
                    Fn(ast.pos) {
                        argDecls.forEach { Replant(it) }
                        typeFormalsList.forEach {
                            V(it.pos.leftEdge, typeFormalSymbol)
                            Replant(it)
                        }
                        V(ast.pos.leftEdge, returnDeclSymbol)
                        Decl(ast.pos.leftEdge, outerReturnName) {}
                        Call(ast.pos, BuiltinFuns.vSetLocalFn) {
                            Ln(ast.pos.leftEdge, outerReturnName)
                            Call(ast.pos) {
                                V(ast.pos.leftEdge, adaptFn)
                                Replant(freeTree(ast))
                            }
                        }
                    }
                }

                // Insert the wrapped metadata before the body so that
                // we don't re-do this work, and so that the TmpLTranslator
                // can recognize the outer function as a wrapper by looking
                // at the inner function.
                ast.insert(ast.size - 1) {
                    V(ast.pos.leftEdge, vWrappedGeneratorFnSymbol)
                    V(ast.pos.leftEdge, void)
                }

                if (im == InterpMode.Partial) {
                    // Mark as visited edges in manufactured nodes.
                    visitEdge(fnEdge, definingEnv, im)
                }
            }
        }

        if (!produceFunctionValue) {
            return NotYet
        }

        return connected ?: if (body != null && symbolsOk && declsOk) {
            val closedOverEnvironment = when (functionStability) {
                FunctionStability.Unstable -> definingEnv
                FunctionStability.StableButNeedsEnvironment -> {
                    var stableEnv = definingEnv
                    while (stableEnv is ChildEnvironment && !stableEnv.isLongLived) {
                        stableEnv = stableEnv.parent
                    }
                    stableEnv
                }

                FunctionStability.SelfContained -> EmptyEnvironment
            }
            val isSelfContained = functionStability == FunctionStability.SelfContained
            val userFunction: UserFunction = when {
                isGeneratorFactory || isTransient -> TransientUserFunction(
                    pos = ast.pos,
                    fnName = fnName,
                    closedOverEnvironment = closedOverEnvironment,
                    signature = signature,
                    interpFormals = interpFormals,
                    formalNamesByIndex = formalNamesByIndex.toList(),
                    isSelfContained = isSelfContained,
                    returnDecl = returnDecl?.first,
                    superTypes = superTypes,
                    body = body.target,
                )
                else -> {
                    var stayLeaf: StayLeaf? = null
                    for (i in metadataStart until bodyIndex step 2) {
                        if (postProcessedEdges[i].second == vStaySymbol) {
                            stayLeaf = postProcessedEdges[i + 1].first.target as? StayLeaf
                            if (stayLeaf != null) {
                                break
                            }
                        }
                    }
                    if (stayLeaf == null) {
                        stayLeaf = StayLeaf(ast.document, ast.pos.leftEdge)
                        ast.insert(bodyIndex) {
                            V(ast.pos.leftEdge, vStaySymbol)
                            Replant(stayLeaf)
                        }
                        if (im == InterpMode.Partial) {
                            interpretEdge(stayLeaf.incoming!!, definingEnv, InterpMode.Partial)
                        }
                    }
                    LongLivedUserFunction(
                        pos = ast.pos,
                        fnName = fnName,
                        closedOverEnvironment = closedOverEnvironment,
                        signature = signature,
                        interpFormals = interpFormals,
                        superTypes = superTypes,
                        formalNamesByIndex = formalNamesByIndex.map { it as ResolvedName },
                        isSelfContained = isSelfContained,
                        stayLeaf = stayLeaf,
                    )
                }
            }
            Value(userFunction)
        } else {
            NotYet
        }
    }

    internal fun interpretFunctionBody(
        userFunction: UserFunction,
        pos: Position,
        @Suppress("UNUSED_PARAMETER") fnName: Symbol?,
        closedOverEnvironment: Environment,
        im: InterpMode,
        signature: Signature2,
        interpFormals: List<InterpValueFormal>?,
        returnDecl: TEdge?,
        formalNamesByIndex: List<TemperName>,
        actuals: Actuals,
        body: Tree,
        cb: InterpreterCallback,
    ): PartialResult {
        val yieldedAt = userFunction.yieldedAt
        val callFrame: Environment

        if (yieldedAt == null) {
            val dynamicMessage = DynamicMessage(valueActuals = actuals, interpMode = im)
            val resolutions = Resolutions(callbackFor(pos))
            val arguments = unify(dynamicMessage, interpSignatureOf(signature, interpFormals), resolutions)
            if (arguments == null) {
                failLog.explain(MessageTemplate.SignatureMismatch, pos)
                resolutions.problem?.logTo(failLog, pos)
                // TODO: may not have been visited
                return Fail
            }

            callFrame = BlockEnvironment(closedOverEnvironment)

            // TODO(rest formal, named arguments): How do the two interact? Issue 443
            val namedArguments = arguments.namedArguments(cb) ?: return Fail
            for ((formalIndex, inputType, initial, constness) in namedArguments) {
                val formal = signature.valueFormalForActual(formalIndex)
                    ?: return Fail
                val name = formalNamesByIndex[formalIndex]
                val typeAsValue = if (formal.kind == ValueFormalKind.Rest && inputType != null) {
                    Types.vList
                } else if (inputType is ReifiedType) {
                    Value(inputType)
                } else {
                    null
                }
                val declResult = callFrame.declare(
                    name,
                    DeclarationBits(
                        reifiedType = typeAsValue,
                        initial = initial,
                        constness = constness,
                        // Argument values can come from many sources
                        referentSource = ReferentSource.Unknown,
                        missing = formal.missing,
                        declarationSite = pos.leftEdge,
                    ),
                    cb,
                )
                if (declResult is Fail) {
                    return declResult
                }
            }
        } else {
            callFrame = yieldedAt.envStack.first().parent
        }

        val returnDeclTree = returnDecl?.target
        val returnName = if (returnDeclTree != null) {
            if (returnDeclTree !is DeclTree) {
                cb.explain(MessageTemplate.MalformedDeclaration, returnDeclTree.pos)
                return Fail
            }
            val returnParts = returnDeclTree.parts
            if (returnParts == null) {
                cb.explain(MessageTemplate.MalformedDeclaration, returnDeclTree.pos)
                return Fail
            }
            val declResult = interpretDecl(returnDeclTree, callFrame, im)
            if (declResult is Fail) {
                return declResult
            }
            returnParts.name.content
        } else {
            null
        }

        val bodyEdge = body.incoming
        val bodyResult = when (body) {
            is BlockTree ->
                // Inline interpretEdge here so we can pass userFunction to enable pausing.
                if (bodyEdge == null || visitEdge(bodyEdge, callFrame, im)) {
                    interpretBlock(body, callFrame, im, userFunction)
                } else {
                    NotYet
                }
            else -> {
                interpretEdge(bodyEdge!!, callFrame, im)
            }
        }

        return when (bodyResult) {
            NotYet, is Fail -> Fail
            is Value<*> -> {
                // If we stopped at a yield point, then we should
                // prefer the result, a *ValueResult* instance, to
                // any that was prematurely stored in the return
                // value.
                // Consider the below:
                //
                //    return__123 = doneResult()
                //    ...
                //    // resumed here
                //    ...
                //    yield(123); // paused here
                //
                // If control reached a return point, then yieldedAt
                // would be null in which case, we use the returnName
                // but if it didn't reach a return point we should
                // use the *ValueResult* instance (presumably) returned
                // by interpretBlock.
                val yielded = userFunction.yieldedAt != null

                val outputResult: PartialResult =
                    if (returnName != null && !yielded) {
                        if (ReferentBit.Value !in callFrame.completeness(returnName)!!) {
                            // If we've allocated space for a return value, but have not yet run
                            // MakeResultsExplicit, then we can use the body result.
                            // But first, we need to make sure the result would pass the
                            // return type check.
                            when (callFrame.set(returnName, bodyResult, cb)) {
                                is Value<*> -> Unit
                                NotYet -> return NotYet
                                is Fail -> return Fail
                            }
                        }
                        callFrame[returnName, cb]
                    } else {
                        bodyResult
                    }
                outputResult
            }
        }
    }

    /**
     * In partial evaluation mode, walk the children in order to expand macros.
     */
    private fun maybeVisitChildren(
        ast: Tree,
        env: Environment,
        im: InterpMode,
    ) {
        if (im == InterpMode.Partial) {
            // Loop is resistant to changes made by macros
            forEachChildMacroSensitively(ast) { edge ->
                interpretEdge(edge, env, im)
            }
        }
    }

    private fun callbackFor(p: Positioned) = InterpreterCallbackImpl(p.pos)

    private inner class InterpreterCallbackImpl(
        override val pos: Position,
    ) : BoundInterpreterCallback {
        override val readiness = if (goingOutOfStyle) {
            Readiness.GoingOutOfStyle
        } else {
            Readiness.Ready
        }

        override fun apply(f: Value<*>, args: ActualValues, interpMode: InterpMode): PartialResult {
            val fn = TFunction.unpackOrNull(f) as? CallableValue
                ?: run {
                    explain(
                        MessageTemplate.ExpectedValueOfType,
                        values = listOf(TFunction, f.typeTag),
                    )
                    return@apply Fail
                }
            return fn(args, this, interpMode)
        }

        override val stage get() = this@Interpreter.stage
        override val logSink get() = this@Interpreter.logSink
        override val failLog get() = this@Interpreter.failLog

        override fun getFeatureImplementation(key: InternalFeatureKey) =
            this@Interpreter.features[key] ?: Fail

        override val interpreter: Interpreter get() = this@Interpreter

        override val promises: Promises get() = this@Interpreter.promises
    }

    private inner class ScopedMacroEnvironment(
        override val call: CallTree?,
        val calleeTree: Tree,
        override val args: LazyActualsList,
        val cb: BoundInterpreterCallback,
    ) : AwaitMacroEnvironment, BoundInterpreterCallback by cb, AutoCloseable {
        private val env = args.environment
        override val document = callee.document
        private var callSiteReplacementMaker: ((Planting).() -> Unit)? = null
        private var callSiteAncestorToReplace: TEdge? = null
        override var awaiter: Promises.Awaiter? = null
            private set

        @Suppress("RedundantModalityModifier") // IntelliJ flags this as an error otherwise
        final override val label: TemperName? get() = labelNameLeaf?.content
        private var labelNameLeaf: NameLeaf? = null
        override fun consumeLabel() {
            val labelNameLeaf = this.labelNameLeaf
            this.labelNameLeaf = null
            if (labelNameLeaf != null) {
                val labelEdge = labelNameLeaf.incoming
                val metadataEdge = labelEdge?.source?.edge(labelEdge.edgeIndex - 1)
                labelEdge?.replace { V(void) }
                metadataEdge?.replace { V(void) }
            }
        }

        override val environment: Environment get() = env // TODO: log access to this

        fun <T> withAwaiter(awaiter: Promises.Awaiter, f: () -> T): T {
            val oldAwaiter = this.awaiter
            try {
                this.awaiter = awaiter
                return f()
            } finally {
                this.awaiter = oldAwaiter
            }
        }

        init {
            val parent = call?.incoming?.source
            @Suppress("MagicNumber") // Detekt, I can count to 3: 0 1 2
            if (
                parent is BlockTree &&
                // Just enough room for this call and a label in its parent, so we know that the
                // label applies to this call and only this call.
                // This is the difference between:
                //     foo: for (;;) { ... }
                // and
                //     foo: { for (;;) { ... } }
                // In the latter, `continue foo` in the loop body should not work.
                // TODO: do we care about this?  If so, write tests.
                parent.size == 3 && parent.child(0).symbolContained == labelSymbol
            ) {
                labelNameLeaf = parent.child(1) as? NameLeaf
            }
        }

        private var isOpen = true

        override fun close() {
            require(isOpen)
            try {
                val replacementMaker = callSiteReplacementMaker
                if (replacementMaker != null) {
                    require(call != null)
                    val macroCallEdge = call.incoming!!
                    val edgeToReplace: TEdge = callSiteAncestorToReplace ?: macroCallEdge
                    val replacedEdgeIndex = edgeToReplace.edgeIndex
                    edgeToReplace.source!!.replace(replacedEdgeIndex..replacedEdgeIndex) {
                        this.replacementMaker()
                    }
                    // Mark the macro call as complete, for this stage, even if it's relocated.
                    val postReplacementMacroCallEdge = call.incoming
                    if (postReplacementMacroCallEdge != null) {
                        val bc = postReplacementMacroCallEdge.breadcrumb
                        if (bc == null || bc < stage) {
                            postReplacementMacroCallEdge.breadcrumb = stage
                        }
                    }
                    // If it was an ancestor that was replaced, proceed to
                }
            } finally {
                isOpen = false
            }
        }

        override val callee: Tree get() = calleeTree

        override fun evaluateTree(tree: Tree, interpMode: InterpMode): PartialResult =
            when (val edge = tree.incoming) {
                // A tree manufactured by the macro.
                null -> interpretTree(tree, env, interpMode)
                else -> interpretEdge(edge, env, interpMode)
            }

        override fun dispatchCallTo(
            calleeTree: Tree,
            callee: Value<*>,
            argTrees: List<Tree>,
            interpMode: InterpMode,
        ): PartialResult = dispatchCall(calleeTree, callee, argTrees, env, null, interpMode)

        override fun orderChildMacrosEarly(signatures: List<AnySignature>) {
            MacroCallOrderer(call, args, this as InterpreterCallback, signatures)
                .preEvaluateAsNeeded()
        }

        override fun declareLocal(
            nameLeaf: Tree,
            declarationBits: DeclarationBits,
        ): PartialResult {
            val name = requireName(nameLeaf)
                ?: return cb.fail(MessageTemplate.IsNotAName, nameLeaf.pos)
            return env.declare(name, declarationBits, callbackFor(nameLeaf))
        }

        override fun setLocal(nameLeaf: LeftNameLeaf, newValue: Value<*>): Result {
            val name = nameLeaf.content
            return env.set(name, newValue, callbackFor(nameLeaf)).infoOr {
                cb.fail(MessageTemplate.CouldNotSetLocal, pos = nameLeaf.pos, values = listOf(name))
            }
        }

        override fun getLocal(nameLeaf: RightNameLeaf) =
            env[nameLeaf.content, callbackFor(nameLeaf)]

        override fun completeness(nameLeaf: NameLeaf): ReferentBitSet? {
            val name = nameLeaf.content
            return env.completeness(name)
        }

        override fun log(message: String, level: Log.Level) {
            require(isOpen)
            logSink.log(
                level = level,
                template = MessageTemplate.UserMessage,
                pos = pos,
                values = listOf(message),
            )
        }

        override fun log(
            level: Log.Level,
            messageTemplate: MessageTemplate,
            pos: Position,
            values: List<Any>,
        ) {
            require(isOpen)
            logSink.log(level = level, template = messageTemplate, pos = pos, values = values)
        }

        override fun replaceMacroCallWith(replacement: Tree) {
            replaceMacroCallWith { Replant(replacement) }
        }

        override fun replaceMacroCallWith(
            makeReplacement: (Planting).() -> Unit,
        ) {
            require(call != null)
            callSiteReplacementMaker = makeReplacement
            callSiteAncestorToReplace = null
        }

        override fun replaceMacroCallAncestorWith(edge: TEdge, makeReplacement: Planting.() -> Unit) {
            require(call != null)
            callSiteReplacementMaker = makeReplacement
            callSiteAncestorToReplace = edge
        }

        override fun replaceMacroCallWithErrorNode() = replaceMacroCallWith {
            Replant(errorNodeFor(call!!))
        }

        override fun replaceMacroCallWithErrorNode(cause: LogEntry) = replaceMacroCallWith {
            Replant(errorNodeFor(call!!, cause))
        }

        override fun runAfterReplacement(postPass: PostPass) {
            postPasses!!.add(postPass)
        }

        private fun requireName(tree: Tree): TemperName? {
            return if (tree is NameLeaf) {
                tree.content
            } else {
                failLog.explain(MessageTemplate.IsNotAName, tree.pos, emptyList())
                null
            }
        }

        override fun <T> goingOutOfStyle(action: () -> T): T {
            val oldGoingOutOfStyle = goingOutOfStyle
            goingOutOfStyle = true
            try {
                return action()
            } finally {
                goingOutOfStyle = oldGoingOutOfStyle
            }
        }

        override fun innermostContainingScope(t: Tree): Tree {
            var tree = t
            while (true) {
                tree = tree.incoming?.source ?: return tree
                if (tree is FunTree) {
                    return tree
                }
            }
        }

        override val isProcessingImplicits: Boolean
            get() = document.isImplicits

        override fun connection(connectedKey: String): ((Signature2) -> Value<*>)? = connecteds[connectedKey]

        val ancestorReplaced: TEdge? get() = callSiteAncestorToReplace
    }

    private fun <T> withMacroEnvironment(
        call: CallTree?,
        callee: Tree,
        actuals: LazyActualsList,
        cb: BoundInterpreterCallback,
        action: (macroEnv: ScopedMacroEnvironment) -> T,
    ): T {
        return ScopedMacroEnvironment(call, callee, actuals, cb).use { macroEnv ->
            action(macroEnv)
        }
    }

    companion object {
        internal fun interpreterFor(cb: InterpreterCallback): Interpreter {
            val cbi = cb as BoundInterpreterCallback
            return cbi.interpreter
        }
    }
}

private class EvaluatedDeclParts(
    val name: TemperName?,
    val symbol: Symbol?,
    val type: Value<*>?,
    val init: Value<*>?,
    val defaultExpr: Value<*>?,
    val constness: Constness,
    val referentSource: ReferentSource,
    val missing: ReferentBitSet,
    val metadataMap: Map<Symbol, Value<*>?>,
) {
    val isOptional: TriState get() = optionalAsTriState(metadataMap[optionalSymbol])
    fun isRestFormal(): Boolean = metadataMap.containsKey(restFormalSymbol)
}

private fun calleeName(tree: Tree?): TemperName? {
    if (tree is CallTree && tree.size != 0) {
        val callee = tree.child(0)
        if (callee is NameLeaf) {
            return callee.content
        }
    }
    return null
}

// Every 1024 steps, we check continueCondition.
private const val CONTINUE_CONDITION_STEP_MASK = 1023

private fun <T : Any> isStable(value: Value<T>) = value.stability == ValueStability.Stable

private fun isFailingCallToHandlerScope(
    ast: Tree,
    env: Environment,
): Boolean {
    if (ast !is CallTree || ast.size < 2) { return false }
    val macro = ast.child(0).functionContained ?: return false
    return when (macro) {
        BuiltinFuns.handlerScope -> {
            //     hs(errorName, ...)
            // assigns true to errorName if it failed.
            val errorName = ast.child(1) as? NameLeaf ?: return false
            val passed = env[errorName.content, InterpreterCallback.NullInterpreterCallback]
            passed == TBoolean.valueTrue
        }
        BuiltinFuns.setLocalFn -> {
            // We assign undefined results to temporaries returned by handler scope calls.
            // The spurious success of that assignment is not indicative of real success.
            //     t#123 = hs(fail#124, ...)
            ast.size == BINARY_OP_CALL_ARG_COUNT && isFailingCallToHandlerScope(ast.child(2), env)
        }
        else -> false
    }
}

enum class FunctionStability {
    Unstable,
    StableButNeedsEnvironment,
    SelfContained,
}

private fun functionStability(ast: FunTree, env: Environment): FunctionStability {
    val fnParts = ast.parts ?: return FunctionStability.Unstable
    val declaredLocally = mutableSetOf<TemperName>()
    val name = fnParts.word?.content
    if (name != null) { declaredLocally.add(name) }

    var stability = FunctionStability.SelfContained // Until proven otherwise
    fun updateStability(signal: FunctionStability) {
        if (signal < stability) {
            stability = signal
        }
    }
    fun scan(t: Tree?) {
        when (t) {
            null -> return
            is CallTree -> {
                val callee = t.childOrNull(0)
                if (callee is RightNameLeaf) {
                    when {
                        // Recursive calls allowed
                        callee.content == name -> Unit
                        // References to long-lived declarations allowed
                        env.isLongLivedDeclaration(callee.content) == true ->
                            updateStability(FunctionStability.StableButNeedsEnvironment)
                        else -> {
                            updateStability(FunctionStability.Unstable)
                            return
                        }
                    }
                } else {
                    val calleeValue = callee?.functionContained
                    // Calls to pure functions and stable functions allowed.
                    when (calleeValue?.functionSpecies) {
                        FunctionSpecies.Pure, FunctionSpecies.Special -> Unit
                        else -> {
                            updateStability(FunctionStability.Unstable)
                            return
                        }
                    }
                }
            }
            is ValueLeaf -> return
            is NameLeaf -> if (t.content !in declaredLocally) {
                if (env.isLongLivedDeclaration(t.content) == true) {
                    updateStability(FunctionStability.StableButNeedsEnvironment)
                } else {
                    updateStability(FunctionStability.Unstable)
                }
            }
            is DeclTree -> {
                val parts = t.parts
                if (parts == null) {
                    updateStability(FunctionStability.Unstable)
                } else {
                    declaredLocally.add(parts.name.content)
                }
            }
            is BlockTree -> {
                if (t.size >= 2 && t.flow == LinearFlow) { // Declare labels
                    val c0 = t.child(0)
                    val c1 = t.child(1)
                    if (c0 is ValueLeaf && vLabelSymbol == c0.content && c1 is NameLeaf) {
                        declaredLocally.add(c1.content)
                    }
                }
            }
            is EscTree,
            is FunTree,
            is StayLeaf,
            -> Unit
        }
        for (child in t.children) {
            scan(child)
            if (stability == FunctionStability.Unstable) {
                break
            }
        }
    }

    for (formal in fnParts.formals) {
        scan(formal)
    }
    scan(fnParts.returnDecl)
    scan(fnParts.body)
    return stability
}

/**
 * How to deal with calls to various kinds of functions in various interpreter modes.
 */
private val callStrategies = mapOf(
    Pair(FunctionSpecies.Pure, InterpMode.Partial) to CallStrategy.CallFullAndInlineResult,
    Pair(FunctionSpecies.Pure, InterpMode.Full) to CallStrategy.CallWithActualValues,
    Pair(FunctionSpecies.Special, InterpMode.Partial) to CallStrategy.CallAsMacroAndMaybeReplace,
    Pair(FunctionSpecies.Special, InterpMode.Full) to CallStrategy.CallInMacroEnvForResult,
    Pair(FunctionSpecies.Normal, InterpMode.Partial) to CallStrategy.InterpretChildrenPartially,
    Pair(FunctionSpecies.Normal, InterpMode.Full) to CallStrategy.CallWithActualValues,
    Pair(FunctionSpecies.Macro, InterpMode.Partial) to CallStrategy.CallAsMacroAndMaybeReplace,
    Pair(FunctionSpecies.Macro, InterpMode.Full) to CallStrategy.Fail,
)

private enum class CallStrategy {
    CallWithActualValues,
    CallAsMacroAndMaybeReplace,
    CallInMacroEnvForResult,
    CallFullAndInlineResult,
    InterpretChildrenPartially,
    Fail,
}

private fun forEachChildMacroSensitively(
    tree: Tree,
    action: (TEdge) -> Unit,
) {
    // Loop in a way that is less sensitive to changes macros make to their call's parent's child
    // list.
    var childIndex = 0
    while (childIndex < tree.size) {
        val edge = tree.edge(childIndex)
        action(edge)
        if (tree.edgeOrNull(childIndex) != edge && edge.source == tree) {
            childIndex = edge.edgeIndex + 1
        } else {
            childIndex += 1
        }
    }
}

/**
 * True if this is a call like that produced by the *Grammar* production `QuasiHole`
 * which represents an unescaped subtree inside an escaped subtree.
 */
private fun isQuasiHole(t: Tree): Boolean {
    if (t !is CallTree || t.size == 0) {
        return false
    }
    val name = when (val callee = t.child(0)) {
        is NameLeaf -> callee.content.builtinKey
        is ValueLeaf -> (TFunction.unpackOrNull(callee.content) as? NamedBuiltinFun)?.name
        else -> null
    }
    return name == unholeBuiltinName.builtinKey
}

/**
 * True if the tree may have a side effect so shouldn't be collapsed to a constant.
 * This assumes that needed information has been successfully extracted: it was converted to
 * an argument to a call to a pure function, so we needn't worry about whether a name is resolvable.
 */
private fun mayEffect(t: Tree, isCallee: Boolean = false): Boolean = when (t) {
    is ValueLeaf -> false
    is NameLeaf -> t.content !is ResolvedName
    is StayLeaf -> false
    is CallTree -> when (val callee = t.childOrNull(0)?.functionContained) {
        // Special case where if calling getStatic is itself the callee of something being inlined,
        // we don't need to preserve it. So, may effect if isn't the callee.
        // TODO Distinguish calling a static method vs calling on the value of a static property?
        // Looks like: (Call (Call (V getStatic) (V String) (V \whatever)) ...)
        is GetStaticOp -> !isCallee
        // Pure function calls that produce unstable values that were stabilized in context should
        // evaporate.
        else -> callee != BuiltinFuns.preserveFn && (
            callee?.functionSpecies != FunctionSpecies.Pure || t.children.any {
                mayEffect(it)
            }
            )
    }
    // They're complicated
    is BlockTree -> true
    is DeclTree -> true
    is EscTree -> true
    is FunTree -> true
}

private fun hasStayLeaf(t: Tree): Boolean {
    var hasStay = false
    TreeVisit.startingAt(t)
        .forEach {
            if (it is StayLeaf) {
                hasStay = true
                VisitCue.AllDone
            } else {
                VisitCue.Continue
            }
        }
        .visitPreOrder()
    return hasStay
}

private val Tree.isPreserved: Boolean
    get() {
        val edge = this.incoming
        val parent = edge?.source
        return parent is CallTree && parent.size == PRESERVE_FN_CALL_SIZE &&
            parent.edge(1) === edge &&
            (parent.childOrNull(0) as? ValueLeaf)?.content == BuiltinFuns.vPreserveFn
    }

internal fun interpSignatureOf(sig: Signature2, interpFormals: List<InterpValueFormal>?): AnySignature {
    if (interpFormals == null) {
        return sig
    }
    // Convey a signature with information about which declarations are not fully baked, and
    // default expressions to unify.  This allows interpretation before default expressions are
    // folded into the function body.
    return InterpSignature(
        returnType = sig.returnType,
        requiredAndOptionalValueFormals = interpFormals,
        restInputsType = sig.restInputsType,
        typeFormals = sig.typeFormals,
    )
}
