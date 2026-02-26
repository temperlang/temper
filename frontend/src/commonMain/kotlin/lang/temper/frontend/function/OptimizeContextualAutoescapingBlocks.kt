package lang.temper.frontend.function

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.accumulatedDotName
import lang.temper.builtin.appendDotName
import lang.temper.builtin.appendSafeDotName
import lang.temper.common.ForwardOrBack
import lang.temper.common.Log
import lang.temper.common.buildListMultimap
import lang.temper.common.console
import lang.temper.common.firstOrNullAs
import lang.temper.common.putMultiList
import lang.temper.common.subListToEnd
import lang.temper.env.InterpMode
import lang.temper.frontend.InterpretationContext
import lang.temper.frontend.structureBlock
import lang.temper.frontend.syntax.isAssignment
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.name.ExportedName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.Abstractness
import lang.temper.type.BindMemberAccessor
import lang.temper.type.DotHelper
import lang.temper.type.ExternalBind
import lang.temper.type.ExternalGet
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.SuperTypeTree2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withType
import lang.temper.value.Abort
import lang.temper.value.ActualValues
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.CallableValue
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InterpreterCallback
import lang.temper.value.MaximalPath
import lang.temper.value.MaximalPathIndex
import lang.temper.value.NameLeaf
import lang.temper.value.NotYet
import lang.temper.value.Panic
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.StaylessMacroValue
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TClosureRecord
import lang.temper.value.TEdge
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TMap
import lang.temper.value.TMapBuilder
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.TVoid
import lang.temper.value.Tree
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.forwardMaximalPaths
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.isImplicits
import lang.temper.value.overloadSymbol
import lang.temper.value.ssaSymbol
import lang.temper.value.testSymbol
import lang.temper.value.toPseudoCode
import lang.temper.value.typeSymbol
import lang.temper.value.valueContained
import lang.temper.value.void
import lang.temper.type.WellKnownTypes as WKT

private const val DEBUG = false

/**
 * Erases contextual auto-escaper uses so that the results are more transparent to static analysis.
 *
 * TODO: This micro-pass is meant to be replaces with a user-space macro in the secure-composition
 * library when user-space macros become capable of doing what it does.
 *
 * Activates when given input like the below:
 *
 *     let html = SafeHtmlBuilder;  // extends ContextualAutoescapingAccumulator<...>
 *
 *     ...
 *       let accumulator = new SafeHtmlBuilder();
 *       ...
 *       accumulator.append(...);
 *       ...
 *       accumulator.appendSafe("...");
 *       ...
 *       accumulator.accumulated
 *     ...
 *
 * This pass identifies types that have ContextualAutoescapingAccumulator as a super-type.
 *
 * For each of them, we assume (TODO: we can check this) that
 * each accumulator instance has the following properties (and only these):
 *   1. `state`
 *   2. `collector`
 *
 * Our goal is to eliminate the first two and just operate directly on the last one.
 *
 *     ...
 *       // let accumulator = new SafeHtmlBuilder();
 *       let collector = SafeHtml.newCollector();
 *       ...
 *       // accumulator.append(...);
 *       collector.append(HtmlPcdataEscaper.instance.escape(...));
 *       ...
 *       // accumulator.appendSafe("...");
 *       collector.appendSafe("...");
 *       ...
 *       // accumulator.accumulated
 *       SafeHtmlBuilder.fromCollector(collector)
 *     ...
 *
 * That rewrite relies on a flow-sensitive propagation of contexts & automatonStacks
 * across safe parts and inlining of escaper decisions for non-safe parts.
 *
 * The benefits are twofold:
 *
 * 1. We don't have to propagate context at runtime, and
 * 2. third-party security tools like Semgrep can inspect the actual escaping
 *    decisions made.
 */
internal fun optimizeContextualAutoescapingBlocks(iCtx: InterpretationContext, logSink: LogSink) {
    val root = iCtx.root

    if (root.document.isImplicits) { return }
    // The high-level flow here is:
    // 1. Identify uses of `.accumulated`
    // 2. Filter those to find ones that extend from a type named
    //    ContextualAutoescapingAccumulator.
    //    This is not part of std or Implicits so we don't have a trusted path to it,
    //    but we use a heuristic to find it
    //    (see TODO above about doing this in a library specific user-space macro).
    //    Return early if none found which is the case for most modules.
    // 3. Identify blocks that create accumulators and check some properties:
    //
    //    - the accumulator variable is only used in method calls / getters
    //      (it does not escape if the accumulator impl doesn't leak it)
    //    - the accumulator is not used after .accumulated is gotten
    // 4. Do the flow-sensitive analysis to compute contexts before each unsafe
    //    append.
    // 5. If everything went well, erase the accumulator as described above.

    var contextualAutoescapingAccumulatorTypeShape: TypeShape? = null
    fun contextualAutoescapingAccumulatorSuper(t: DefinedNonNullType?): DefinedNonNullType? {
        if (t == null) { return null }
        val ts = t.definition
        if (ts.abstractness == Abstractness.Abstract) {
            // Can't optimize because we don't have a concrete type.
            return null
        }
        if (contextualAutoescapingAccumulatorTypeShape == null) {
            fun walkSupers(shape: TypeShape) {
                if (shape.word?.text == "ContextualAutoescapingAccumulator") {
                    // See secure-composition library
                    contextualAutoescapingAccumulatorTypeShape = shape
                } else {
                    for (st in shape.superTypes) {
                        walkSupers(st.definition as TypeShape)
                    }
                }
            }
            walkSupers(ts)
        }
        // If we haven't found the type shape, then it's definitely not in
        // the super-type tree
        return contextualAutoescapingAccumulatorTypeShape?.let {
            SuperTypeTree2.of(t)[it].firstOrNull() as DefinedNonNullType
        }
    }

    val autoescUses = buildListMultimap {
        TreeVisit.startingAt(root)
            .forEachContinuing { t ->
                if (t is ValueLeaf) {
                    val fn = t.functionContained
                    if (fn is DotHelper && fn.memberAccessor == ExternalGet && fn.symbol == accumulatedDotName) {
                        val parent = t.incoming?.source as? CallTree
                        if (parent != null && parent.children.size == 2) {
                            val accumulator = parent.child(1) as? RightNameLeaf
                            val accumulatorType = accumulator?.typeInferences?.type?.let { hackMapOldStyleToNew(it) }
                                as? DefinedNonNullType
                            val autoescaperSuperType = contextualAutoescapingAccumulatorSuper(accumulatorType)
                                ?: return@forEachContinuing
                            check(accumulatorType != null)
                            val accumulatorName = accumulator.content as ResolvedName
                            var anc: Tree = parent
                            var accumulatorDecl: DeclTree? = null
                            while (true) {
                                if (anc is BlockTree) {
                                    anc.children.firstOrNullAs<Tree, DeclTree> {
                                        it.parts?.name?.content == accumulatorName
                                    }?.let {
                                        accumulatorDecl = it
                                        break
                                    }
                                }
                                anc = anc.incoming?.source ?: break
                            }
                            if (accumulatorDecl != null &&
                                accumulatorDecl.parts?.metadataSymbolMap?.containsKey(ssaSymbol) == true
                            ) {
                                val declaringBlock = accumulatorDecl.incoming!!.source as BlockTree
                                val ai = AutoescUseInfo(
                                    accumulatorName,
                                    declaringBlock,
                                    accumulatorDecl,
                                    AutoescTypes(
                                        autoescaperSuperType,
                                        accumulatorType = accumulatorType,
                                    ),
                                    parent,
                                )
                                putMultiList(declaringBlock, ai)
                            }
                        }
                    }
                }
            }
            .visitPreOrder()
    }

    if (autoescUses.isEmpty()) { return }
    check(contextualAutoescapingAccumulatorTypeShape != null)

    val escaperUnraveler = EscaperUnraveler()
    for ((b, uses) in autoescUses) {
        structureBlock(b)
        for (use in uses) {
            optimizeAutoescaperUse(use, iCtx, escaperUnraveler, logSink)
        }
    }
}

private data class AutoescTypes(
    val contextualAutoescapingAccumulatorType: DefinedNonNullType,
    val accumulatorType: DefinedNonNullType,
) {
    val collectorType by lazy {
        // The accumulator type's newCollector static method initializes the collector, so
        // use its output type as the type hint for how we accumulate content
        var newCollectorDescriptor =
            accumulatorType.definition.staticProperties.firstOrNull { it.symbol.text == "newCollector" }
                ?.descriptor
        if (newCollectorDescriptor is Type2) {
            newCollectorDescriptor =
                withType(
                    newCollectorDescriptor,
                    fallback = { null },
                    fn = { _, sig, _ -> sig },
                )
        }
        (newCollectorDescriptor as? Signature2)?.returnType2 as? DefinedNonNullType
    }
}

private data class AutoescUseInfo(
    val name: ResolvedName,
    val declaringBlock: BlockTree,
    val accumulatorDecl: DeclTree,
    val types: AutoescTypes,
    val accumulated: CallTree,
)

private fun optimizeAutoescaperUse(
    use: AutoescUseInfo,
    iCtx: InterpretationContext,
    escaperUnraveler: EscaperUnraveler,
    logSink: LogSink,
) {
    val block = use.declaringBlock
    val accumulatorName = use.name
    val accumulatorType = use.types.accumulatorType

    val basicDefinitions =
        (use.types.contextualAutoescapingAccumulatorType.definition.name as? ExportedName ?: return)
            .origin
    val propagateOverName = ExportedName(basicDefinitions, ParsedName("propagateOver"))

    val paths = forwardMaximalPaths(
        block,
        yieldingCallsEndPaths = false,
        ignoreConstantConditions = true,
        assumeFailureCanHappen = true,
    )

    // Figure out where we need to start traversal.
    val (startPath: MaximalPath, startOffset: Int) = run findInitializer@{
        var startPath: MaximalPath? = null
        var startOffset = 0
        for (path in paths.maximalPaths) {
            for ((offset, pathEl) in path.elements.withIndex()) {
                val ref = pathEl.ref
                val stmt = block.dereference(ref)?.target
                if (stmt != null && assigns(stmt, accumulatorName)) {
                    startPath = path
                    startOffset = offset
                    break
                }
            }
        }
        startPath?.let { it to startOffset }
    } ?: return

    // Figure out where we need to end.
    val endChildIndex = run {
        var descendant: Tree = use.accumulated // descendent of block
        var index: Int
        while (true) {
            val incoming = descendant.incoming!!
            val parent = incoming.source!!
            if (parent == block) {
                index = incoming.edgeIndex
                break
            }
            descendant = parent
        }
        index
    }

    // Walk the paths from initialization to `.accumulated` use propagating context.

    // First, we need the state at initialization of the accumulator.
    // ContextualAutoescapingAccumulators by convention initialize their state
    // to `MyType.initialState()`.
    val init = startPath.elements[startOffset].ref
    val initialState = iCtx.interpret(init.pos) {
        Call {
            Call(BuiltinFuns.vGets) {
                V(Value(ReifiedType(accumulatorType)))
                V(Symbol("initialState"))
            }
        }
    } as? Value<*> ?: return
    val contextPropagator = iCtx.interpret(init.pos) {
        Call {
            Call(BuiltinFuns.vGets) {
                V(Value(ReifiedType(accumulatorType)))
                V(Symbol("propagator"))
            }
        }
    } as? Value<*> ?: return
    val sameStateFn = iCtx.interpret(init.pos) {
        Call {
            Call(BuiltinFuns.vGets) {
                V(Value(ReifiedType(accumulatorType)))
                V(Symbol("sameStatePredicate"))
            }
        }
    } as? Value<*> ?: return
    val propagateOver = iCtx.interpret(init.pos) {
        Rn(propagateOverName)
    } as? Value<*> ?: return
    val escaperPicker = iCtx.interpret(init.pos) {
        Call {
            Call(BuiltinFuns.vGets) {
                V(Value(ReifiedType(accumulatorType)))
                V(Symbol("picker"))
            }
        }
    } as? Value<*> ?: return
    val collectorType = use.types.collectorType ?: return

    // Downgrade static errors when use inside a test body.
    val inTestBody = run {
        var src = block.incoming
        while (src != null) {
            val tree = src.target
            src = src.source?.incoming
            if (tree is FunTree && tree.parts?.metadataSymbolMap?.containsKey(testSymbol) == true) {
                return@run true
            }
        }
        false
    }
    if (DEBUG) {
        console.log("Optimizing ${block.document.context.formatPosition(init.pos)}")
    }

    // We define a callout function for use with propagate, merge state and others.
    val problemsFromCallout = mutableListOf<Pair<String, Boolean>>()
    class CalloutFn : CallableValue, StaylessMacroValue {
        override val sigs = listOf(
            // Takes a problem string and an isError boolean
            Signature2(WKT.voidType2, false, listOf(WKT.stringType2, WKT.booleanType2)),
        )

        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val (a, b) = args.unpackPositioned(2, cb) ?: return Fail
            val problem = TString.unpackOrNull(a)
            val isError = TBoolean.unpackOrNull(b)
            return if (problem != null && isError != null) {
                problemsFromCallout.add(problem to isError)
                void
            } else {
                Fail
            }
        }
    }
    val vCallout = Value(CalloutFn())
    fun withCallouts(receiver: (String, Log.Level) -> Unit) {
        val callouts = problemsFromCallout.toList()
        problemsFromCallout.clear()
        for ((messageText, isError) in callouts) {
            val level = when {
                inTestBody -> Log.Info
                isError -> Log.Error
                else -> Log.Warn
            }

            receiver(messageText, level)
        }
    }

    // Now we need to plan out how we're going to visit statements to come up with
    // states at each appendSafe / append call site.
    // First, we compute an entry count: how many times is a block entered from
    // a preceder that we actually want to walk?
    // We walk from the start, just adding numbers. When we finally do the actual
    // walk, we can decrement followers' numbers as we reach the end of a basic
    // block.  When a basic block's count reaches zero, we know that we have all
    // the prior state info we need to process it next.
    val predecessorCount = mutableMapOf<Pair<MaximalPathIndex, Int>, Int>()
    run {
        val visited = mutableSetOf<Pair<MaximalPathIndex, Int>>()
        val q = ArrayDeque<Pair<MaximalPathIndex, Int>>()
        val initKey = startPath.pathIndex to startOffset
        q.add(initKey)
        predecessorCount[initKey] = 0

        fun countUp(pathIndex: MaximalPathIndex, startOffset: Int) {
            val key = pathIndex to startOffset
            if (key !in visited) {
                visited.add(key)
                val path = paths[pathIndex]
                for (follower in path.followers) {
                    if (follower.dir == ForwardOrBack.Forward) {
                        follower.pathIndex?.let { next ->
                            val fKey = next to 0
                            predecessorCount[fKey] = predecessorCount.getOrDefault(fKey, 0) + 1
                            q.add(fKey)
                        }
                    }
                }
            }
        }
        while (q.isNotEmpty()) {
            val (pathIndex, offset) = q.removeFirst()
            countUp(pathIndex, offset)
        }
    }

    val autoescStateBeforeRef = mutableMapOf<BlockChildReference, Value<*>>()

    val methodClassifications = mutableMapOf<Symbol, Pair<MethodShape?, AppendClassification?>>()

    // Accumulator methods like append(...) might have been overloaded and resolved to
    // other methods, so we back-compute that by looking at @overload(...) metadata
    // to come up with a classification.
    fun methodClassification(symbol: Symbol): Pair<MethodShape?, AppendClassification?> =
        methodClassifications.getOrPut(symbol) {
            val method = accumulatorType.definition.membersMatching(symbol).firstOrNull()
                as? MethodShape

            val classification = if (method?.methodKind == MethodKind.Normal) {
                if (symbol == appendDotName) {
                    AppendClassification.AppendUnsafe
                } else if (symbol == appendSafeDotName) {
                    AppendClassification.AppendSafe
                } else {
                    val isAppendOverload = method.metadata[overloadSymbol]?.any {
                        TString.unpackOrNull(it) == "append"
                    } == true
                    if (isAppendOverload) {
                        AppendClassification.AppendUnsafe
                    } else {
                        null
                    }
                }
            } else {
                null
            }

            method to classification
        }

    // We need a list of statements to rework.
    val toChange = mutableListOf<ChangeDetail>()
    data class CalledOutProblem(
        val severity: Log.Level,
        val pos: Position,
        val problem: String,
    )
    val problems = mutableSetOf<CalledOutProblem>()

    fun propagateOverStmt(stateBefore: Value<*>, ref: BlockChildReference): Value<*>? {
        val edge = block.dereference(ref) ?: return null
        val t = edge.target
        // merge with prior state
        val previouslyComputedState = autoescStateBeforeRef[ref]
        var state = stateBefore
        if (previouslyComputedState != null) {
            state = iCtx.interpret(ref.pos) {
                Call {
                    Call(BuiltinFuns.vGets) {
                        V(Value(ReifiedType(accumulatorType)))
                        V(Symbol("mergeStates"))
                    }
                    V(previouslyComputedState)
                    V(stateBefore)
                    V(vCallout)
                }
            } as? Value<*> ?: return null
        }
        withCallouts { problem, severity ->
            problems.add(CalledOutProblem(severity, ref.pos.leftEdge, problem))
        }
        // If it's a call to append or appendSafe, update the context,
        // and remember it as something we need to change.
        if (t is CallTree && t.size >= 2) {
            val callee = t.child(0)
            if (callee is CallTree && callee.size == 2) {
                val fn = callee.child(0).functionContained
                if (fn is DotHelper && fn.memberAccessor is BindMemberAccessor) {
                    val subject = callee.child(1)
                    if (subject is RightNameLeaf && subject.content == accumulatorName) {
                        val (_, classification) = methodClassification(fn.symbol)
                        if (classification != null) {
                            val arg = t.child(1)
                            val (argToPropagateOver: Value<*>, argPos) = when (classification) {
                                AppendClassification.AppendSafe ->
                                    (arg.valueContained ?: return null) to arg.pos
                                AppendClassification.AppendUnsafe -> TNull.value to arg.pos.leftEdge
                            }

                            val after = iCtx.interpret(argPos) {
                                Call {
                                    V(propagateOver)
                                    V(contextPropagator)
                                    V(state)
                                    V(argPos, argToPropagateOver)
                                    V(sameStateFn)
                                    V(vCallout)
                                }
                            } as? Value<*> ?: return null
                            withCallouts { problem, severity ->
                                problems.add(CalledOutProblem(severity, argPos, problem))
                            }
                            val adjustedString = TString.unpackOrNull(after.readField(adjustedStringDotName))
                                ?: return null
                            state = after.readField(iCtx, stateAfterGetter, argPos) ?: return null
                            val escapers = when (classification) {
                                AppendClassification.AppendUnsafe -> {
                                    val escaperValue = iCtx.interpret(ref.pos) {
                                        Call {
                                            Call(escaperForDotHelper) {
                                                V(escaperPicker)
                                            }
                                            V(state)
                                            V(vCallout)
                                        }
                                    } as? Value<*> ?: return null
                                    withCallouts { problem, severity ->
                                        problems.add(CalledOutProblem(severity, ref.pos, problem))
                                    }

                                    escaperUnraveler.escapers(escaperValue) ?: return null
                                }
                                AppendClassification.AppendSafe -> null
                            }
                            toChange.add(ChangeDetail(edge, adjustedString, escapers, classification))
                        }
                    }
                }
            }
        }
        return state
    }

    val q = ArrayDeque<Triple<MaximalPathIndex, Int, Value<*>>>()
    q.add(Triple(startPath.pathIndex, startOffset, initialState))
    while (q.isNotEmpty()) {
        val (pathIndex, startOffset, startState) = q.removeFirst()

        val path = paths[pathIndex]
        val indices = startOffset..path.elements.lastIndex
        var autoescState = startState
        var skipFollowers = false
        for (i in indices) {
            val ref = path.elements[i].ref
            if (ref.index == endChildIndex) {
                skipFollowers = true
                // Check the accumulator's end state which should recursively
                // end delegates.
                iCtx.interpret(ref.pos) {
                    Call {
                        Call(BuiltinFuns.vGets) {
                            V(Value(ReifiedType(accumulatorType)))
                            V(Symbol("checkEndState"))
                        }
                        V(autoescState)
                        V(vCallout)
                    }
                }
                withCallouts { problem, severity ->
                    problems.add(CalledOutProblem(severity, ref.pos, problem))
                }

                break
            }
            autoescState = propagateOverStmt(autoescState, ref) ?: return
        }

        if (!skipFollowers) {
            for (follower in path.followers) {
                val followerPathIndex = follower.pathIndex ?: continue
                val key = followerPathIndex to 0
                if (key in predecessorCount) {
                    // Copy values, so mutations to a delegate in one branch don't affect
                    // context propagation in another.
                    val stateBeforeCondition = deepValueCopy(autoescState)
                    val stateForFollower = when (val cond = follower.condition) {
                        null -> stateBeforeCondition
                        else -> propagateOverStmt(stateBeforeCondition, cond.ref) ?: return
                    }
                    val remaining = predecessorCount.getValue(key) - 1
                    if (remaining != 0) {
                        predecessorCount[key] = remaining
                    } else {
                        predecessorCount.remove(key)
                        q.add(Triple(followerPathIndex, 0, stateForFollower))
                    }
                }
            }
        }
    }

    // Now we have a set of changes.  Let's figure out how to make them.
    val doc = block.document
    // Allocate a replacement name for the collector since we're erasing the contextual autoescaper.
    val collector = doc.nameMaker.unusedTemporaryName("collector")
    val collectorAppendDotHelper = run {
        // Do we send safe parts by a separate append method?
        val typeShape = collectorType.definition
        val hasAppendSafeMethod =
            typeShape.membersMatching(appendSafeDotName, includeOverloads = true).any {
                it is MethodShape && it.methodKind == MethodKind.Normal && it.visibility == Visibility.Public
            }
        if (hasAppendSafeMethod) {
            appendSafeDotHelper
        } else {
            appendDotHelper
        }
    }
    val changes: List<Pair<TEdge, Planting.() -> UnpositionedTreeTemplate<*>>> = toChange.map {
        val (edge, safe, escapers, classification) = it
        val ps = it.positions
        edge to {
            fun Planting.plantSafeAdjusted(safePs: AppendStmtPositions): UnpositionedTreeTemplate<*> =
                Call(safePs.pos) {
                    Call(safePs.callee) {
                        V(safePs.callee, Value(collectorAppendDotHelper))
                        Rn(safePs.subject, collector)
                    }
                    V(safePs.arg, Value(safe, TString))
                }
            when (classification) {
                AppendClassification.AppendSafe -> if (safe.isNotEmpty()) {
                    plantSafeAdjusted(ps)
                } else {
                    V(ps.pos, void)
                }
                AppendClassification.AppendUnsafe -> {
                    check(escapers != null)
                    fun Planting.plantUnsafeAppend(): UnpositionedTreeTemplate<*> = Call(ps.pos) {
                        val arg = freeTree(edge.target.child(1))
                        Call(ps.callee) {
                            V(ps.callee, Value(appendDotHelper))
                            Rn(ps.subject, collector)
                        }
                        // Apply the escapers in order.
                        fun Planting.escapeArg(escapers: List<Type2>) {
                            if (escapers.isEmpty()) {
                                Replant(arg)
                            } else {
                                Call {
                                    Call {
                                        V(Value(applyDotHelper))
                                        Call(BuiltinFuns.vGets) {
                                            V(Value(ReifiedType(escapers.first()), TType))
                                            V(Symbol("instance"))
                                        }
                                    }
                                    escapeArg(escapers.subListToEnd(1))
                                }
                            }
                        }
                        escapeArg(escapers)
                    }

                    if (safe.isNotEmpty()) {
                        Block {
                            plantSafeAdjusted(AppendStmtPositions(ps.pos.leftEdge))
                            plantUnsafeAppend()
                        }
                    } else {
                        plantUnsafeAppend()
                    }
                }
            }
        }
    }

    // Now, make the changes:
    // - change the initializer
    // - change .accumulated read.
    // - apply the statement changes from above
    val accumulatorReifiedType = Value(ReifiedType(use.types.accumulatorType))
    val oldDecl = use.accumulatorDecl
    oldDecl.incoming!!.replace { pos ->
        Decl(pos) {
            Ln(collector)
            V(pos.rightEdge, typeSymbol)
            V(Value(ReifiedType(collectorType)))
        }
    }
    block.dereference(init)!!.replace { pos ->
        Call(pos, BuiltinFuns.vSetLocalFn) {
            Ln(pos, collector)
            Call(pos) {
                Call(pos, BuiltinFuns.vGets) {
                    V(pos.leftEdge, accumulatorReifiedType)
                    V(pos.rightEdge, Symbol("newCollector"))
                }
            }
        }
    }
    use.accumulated.incoming!!.replace { pos ->
        Call(pos) {
            Call(BuiltinFuns.vGets) {
                V(accumulatorReifiedType)
                V(Symbol("fromCollector"))
            }
            Rn(pos, collector)
        }
    }
    changes.forEach { (edge, makeReplacement) ->
        edge.replace { makeReplacement() }
    }

    if (problems.isNotEmpty()) {
        val problemsInOrder = problems.sortedWith { a, b ->
            val ap = a.pos
            val bp = b.pos
            var delta = ap.left - bp.left
            if (delta == 0) {
                delta = ap.right - bp.right
            }
            delta
        }

        for ((level, pos, text) in problemsInOrder) {
            logSink.log(
                level = level,
                template = ContextAutoescCalloutMessage,
                pos = pos,
                values = listOf(text),
            )
        }
    }
    if (DEBUG) {
        console.log(". Done optimize autoescaper use")
    }
}

object ContextAutoescCalloutMessage : MessageTemplateI {
    override val name: String = "ContextAutoescCalloutMessage"
    override val formatString: String get() = "content tag reported:%s"
}

private fun assigns(t: Tree, name: TemperName): Boolean =
    isAssignment(t) && (t.child(1) as? NameLeaf)?.content == name

private fun Value<*>.readField(name: Symbol): Value<*>? {
    if (typeTag !is TClass) {
        return null
    }
    val record = this.stateVector as InstancePropertyRecord
    return record.properties.entries.firstOrNull {
        (it.key as? ResolvedParsedName)?.baseName?.nameText == name.text
    }?.value
}

private fun Value<*>.readField(iCtx: InterpretationContext, getter: DotHelper, pos: Position): Value<*>? =
    iCtx.interpret(pos) {
        Call(getter) {
            V(this@readField)
        }
    } as? Value<*>?

private fun <TREE : Tree> InterpretationContext.interpret(
    pos: Position,
    makeTree: (Planting).() -> UnpositionedTreeTemplate<TREE>,
): PartialResult {
    val document = root.document
    val t = document.treeFarm.grow(pos, makeTree)
    return try {
        interpreter.interpret(t, env, InterpMode.Full)
    } catch (_: Panic) {
        return NotYet
    } catch (_: Abort) {
        return NotYet
    }.also {
        if (DEBUG) {
            if (it !is Value<*>) {
                console.group("Bad interpret $it") {
                    t.toPseudoCode(console.textOutput)
                }
            }
        }
    }
}

private enum class AppendClassification {
    AppendUnsafe,
    AppendSafe,
}

private data class AppendStmtPositions(
    val pos: Position,
    val callee: Position,
    val subject: Position,
    val arg: Position,
) {
    constructor(t: Tree) : this(
        pos = t.pos,
        callee = t.childOrNull(0)?.pos ?: t.pos,
        subject = t.childOrNull(0)?.childOrNull(1)?.pos ?: t.pos.leftEdge,
        arg = t.childOrNull(1)?.pos ?: t.pos.rightEdge,
    )
    constructor(p: Position) : this(p, p, p, p)
}

private data class ChangeDetail(
    val edge: TEdge,
    val adjustedString: String,
    val escapers: List<Type2>?,
    val classification: AppendClassification,
) {
    val positions: AppendStmtPositions get() = AppendStmtPositions(edge.target)
}

private val stateAfterGetter = DotHelper(ExternalGet, Symbol("stateAfter"))
private val adjustedStringDotName = Symbol("adjustedString")
private val escaperForDotHelper = DotHelper(ExternalBind, Symbol("escaperFor"))
private val appendSafeDotHelper = DotHelper(ExternalBind, appendSafeDotName)
private val appendDotHelper = DotHelper(ExternalBind, appendDotName)
private val applyDotHelper = DotHelper(ExternalBind, Symbol("apply"))

/**
 * Some escapers are compositions of others.
 * Expand those out.
 */
private class EscaperUnraveler {
    fun escapers(escaper: Value<*>): List<Type2>? {
        val tClass = escaper.typeTag as? TClass ?: return null
        val typeShape = tClass.typeShape
        val first = escaper.readField(firstSymbol)
        val second = escaper.readField(secondSymbol)
        return if (first != null && second != null) {
            escapers(second)?.let { a -> escapers(first)?.let { b -> a + b } }
        } else {
            listOf(MkType2(typeShape).get())
        }
    }

    companion object {
        val firstSymbol = Symbol("first")
        val secondSymbol = Symbol("second")
    }
}

private fun deepValueCopy(v: Value<*>): Value<*> = when (val tt = v.typeTag) {
    TBoolean,
    TFloat64,
    TInt,
    TInt64,
    TString,
    TFunction,
    TNull,
    TProblem,
    TStageRange,
    TSymbol,
    TType,
    TVoid,
    -> v

    TList -> Value(
        TList.unpack(v).map { deepValueCopy(it) },
        TList,
    )
    TListBuilder -> Value(
        mutableListOf<Value<*>>().also { ml ->
            TList.unpack(v).mapTo(ml) { deepValueCopy(it) }
        },
        TListBuilder,
    )
    TMap -> Value(
        buildMap {
            for ((k, ev) in TMap.unpack(v)) {
                this[deepValueCopy(k)] = deepValueCopy(ev)
            }
        },
        TMap,
    )
    TMapBuilder -> Value(
        LinkedHashMap<Value<*>, Value<*>>().also { mm ->
            for ((k, ev) in TMap.unpack(v)) {
                mm[deepValueCopy(k)] = deepValueCopy(ev)
            }
        },
        TMapBuilder,
    )
    is TClass -> Value(
        InstancePropertyRecord(mutableMapOf()).also { pr ->
            tt.unpack(v).properties.mapValuesTo(pr.properties) {
                deepValueCopy(it.value)
            }
        },
        tt,
    )
    TClosureRecord -> v
}
