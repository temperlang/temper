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
import lang.temper.common.putMultiSet
import lang.temper.common.subListToEnd
import lang.temper.env.InterpMode
import lang.temper.frontend.InterpretationContext
import lang.temper.frontend.structureBlock
import lang.temper.frontend.syntax.isAssignment
import lang.temper.interp.New
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.name.ExportedName
import lang.temper.name.ModularName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.Abstractness
import lang.temper.type.CallMemberAccessor
import lang.temper.type.DotHelper
import lang.temper.type.DotMember
import lang.temper.type.ExternalCall
import lang.temper.type.ExternalGet
import lang.temper.type.Member
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
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InterpreterCallback
import lang.temper.value.MacroValue
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
import lang.temper.value.insertBeforeAll
import lang.temper.value.isCore
import lang.temper.value.makePairValue
import lang.temper.value.overloadSymbol
import lang.temper.value.parameterNameSymbols
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
    if (root.document.isCore) { return } // Can't use libraries like secure-composition.

    // The high-level flow here is:
    // 1. Identify uses of `.accumulated`
    // 2. Filter those to find ones that extend from a type named
    //    ContextualAutoescapingAccumulator.
    //    This is not part of std or Core so we don't have a trusted path to it,
    //    but we use a heuristic to find it
    //    (see TODO above about doing this in a library-specific user-space macro).
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

    val testFunNames = buildSet {
        for (t in root.children) {
            val declParts = (t as? DeclTree)?.parts ?: continue
            if (testSymbol in declParts.metadataSymbolMultimap.keys) {
                add(declParts.name.content as ResolvedName)
            }
        }
    }

    val common = AutoescCommon(root)

    val autoescUses = buildListMultimap {
        TreeVisit.startingAt(root)
            .forEachContinuing { t ->
                if (t is ValueLeaf) {
                    val fn = t.functionContained
                    if (fn is DotHelper && fn.memberAccessor == ExternalGet && fn.member == accumulatedDotName) {
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
                                val inTestBody = run {
                                    // If we can walk up to the root and find an assignment like
                                    //     t#123 = fn { ... };
                                    // where t#123 is in the set of test function declarations above
                                    // then it's in a test body.
                                    // TODO: Once we have a general @Suppress mechanism for linty errors
                                    // in content tag uses, then we can retire this difference.
                                    var rootEdge = accumulatorDecl.incoming!!
                                    while (rootEdge.source != root) {
                                        rootEdge = rootEdge.source!!.incoming!!
                                    }
                                    val rootChild = rootEdge.target
                                    val assignedName = if (isAssignment(rootChild)) {
                                        (rootChild.child(1) as NameLeaf?)?.content
                                    } else {
                                        null
                                    }
                                    assignedName != null && assignedName in testFunNames
                                }

                                val declaringBlock = accumulatorDecl.incoming!!.source as BlockTree

                                val ai = AutoescUseInfo(
                                    name = accumulatorName,
                                    declaringBlock = declaringBlock,
                                    accumulatorDecl = accumulatorDecl,
                                    types = AutoescTypes(
                                        autoescaperSuperType,
                                        accumulatorType = accumulatorType,
                                    ),
                                    accumulated = parent,
                                    inTestBody = inTestBody,
                                    common = common.also { it.workingFor(accumulatorDecl) },
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

    common.commit()
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
    val inTestBody: Boolean,
    val common: AutoescCommon,
)

/** State that is common across optimization steps. */
private class AutoescCommon(val root: BlockTree) {
    private val eventInstanceNames = mutableMapOf<ValueConstruction, Temporary>()
    private var eventInstanceInsertionPoints = mutableSetOf<Int>()
    private val dataExports = mutableListOf<DataExport>()

    fun addDataExports(newDataExports: Iterable<DataExport>) {
        dataExports.addAll(newDataExports)
    }

    fun workingFor(first: Tree) {
        // store the offset into the top level block so we know where
        // to insert temporaries on commit
        if (first == root) {
            eventInstanceInsertionPoints.add(0)
        } else {
            var anc: Tree = first
            while (true) {
                val e = anc.incoming!!
                if (e.source == root) {
                    eventInstanceInsertionPoints.add(e.edgeIndex)
                    break
                } else {
                    anc = e.source!!
                }
            }
        }
    }

    fun commit() {
        if (eventInstanceNames.isNotEmpty() || dataExports.isNotEmpty()) {
            insertBeforeAll(root, eventInstanceInsertionPoints) {
                for (dataExport in dataExports) {
                    Call(BuiltinFuns.vDataFileMacro) {
                        V(dataExport.path)
                        V(dataExport.mimeType)
                        V(dataExport.data)
                    }
                }
                for ((construction, temporary) in eventInstanceNames) {
                    Decl {
                        Ln(temporary)
                    }
                    Call(BuiltinFuns.vSetLocalFn) {
                        Ln(temporary)
                        construction.plant(this)
                    }
                }
            }
        }
    }

    fun nameFor(eventValue: Value<*>) = digestFor(eventValue)?.let {
        eventInstanceNames.getOrPut(it) {
            val typeTag = eventValue.typeTag
            val eventName = (typeTag as? TClass)?.typeShape?.word?.text
                ?: ""
            root.document.nameMaker.unusedTemporaryName("parseEffect$eventName")
        }
    }

    private val propertyNameCache = mutableMapOf<Pair<TypeShape, Symbol>, ModularName?>()

    /** Avoid repeated linear scanning of property lists for symbols. */
    private fun propertyName(
        typeShape: TypeShape,
        propertySymbol: Symbol,
    ): ModularName? = propertyNameCache.getOrPut(typeShape to propertySymbol) {
        typeShape.properties.firstOrNull {
            it.abstractness == Abstractness.Concrete && it.symbol == propertySymbol
        }?.name as ModularName?
    }

    private fun digestFor(value: Value<*>): ValueConstruction? {
        return when (val tag = value.typeTag) {
            TBoolean,
            TFloat64,
            TInt,
            TInt64,
            TString,
            TNull,
            -> ValueConstruction.SimpleConstruction(value)

            is TClass -> {
                // Optimistically assume we can reverse construction by replaying args.
                val typeShape = tag.typeShape
                val record = tag.unpack(value)

                if (typeShape == WKT.stringIndexTypeDefinition && record.properties.size == 1) {
                    val offset = record.properties.values.first()
                    if (TInt.unpackOrNull(offset) == 0) {
                        return ValueConstruction.StaticPropertyConstruction(
                            WKT.stringType2,
                            Symbol("begin"),
                        )
                    }
                }

                val constructor = typeShape.methods.first {
                    it.methodKind == MethodKind.Constructor
                }
                var gotAll = true
                val parameterNames = constructor.parameterNameSymbols
                    ?: return null
                val args = buildList {
                    parameterNames.forEach { parameterSymbol, isOptional ->
                        val propertyName = propertyName(typeShape, parameterSymbol)
                        val parameterValue = record.properties[propertyName]
                        if (parameterValue != null) {
                            val parameterConstruction = digestFor(parameterValue)
                            if (parameterConstruction != null) {
                                add(parameterConstruction)
                            } else {
                                gotAll = false
                            }
                        } else if (isOptional) {
                            add(ValueConstruction.SimpleConstruction(TNull.value))
                        } else {
                            gotAll = false
                        }
                    }
                }

                if (gotAll) {
                    ValueConstruction.NewConstruction(typeShape, args)
                } else {
                    null
                }
            }
            TList -> {
                val ls: List<Value<*>> = TList.unpack(value)
                val items = ls.map {
                    digestFor(it) ?: return@digestFor null
                }
                ValueConstruction.CallConstruction(BuiltinFuns.listifyFn, items)
            }

            TListBuilder -> digestFor(Value(TListBuilder.unpack(value), TList))?.let {
                ValueConstruction.MethodConstruction(it, Symbol("toListBuilder"))
            }

            TMap -> {
                val ls: Map<Value<*>, Value<*>> = TMap.unpack(value)
                val pairs = ls.map { (k, v) ->
                    digestFor(makePairValue(k, v)) ?: return@digestFor null
                }
                val pairsList = ValueConstruction.CallConstruction(BuiltinFuns.listifyFn, pairs)
                ValueConstruction.NewConstruction(WKT.mapTypeDefinition, listOf(pairsList))
            }
            TMapBuilder -> digestFor(Value(TMapBuilder.unpack(value), TMap))?.let {
                ValueConstruction.MethodConstruction(it, Symbol("toMapBuilder"))
            }
            TClosureRecord,
            TFunction,
            TProblem,
            TStageRange,
            TSymbol,
            TType,
            TVoid,
            -> null
        }
    }

    sealed interface ValueConstruction {
        fun plant(p: Planting)

        data class SimpleConstruction(val value: Value<*>) : ValueConstruction {
            override fun plant(p: Planting) {
                p.V(value)
            }
        }

        data class NewConstruction(
            val typeShape: TypeShape,
            val args: List<ValueConstruction>,
        ) : ValueConstruction {
            override fun plant(p: Planting) {
                p.Call(New) {
                    V(Value(ReifiedType(MkType2(typeShape).get()), TType))
                    for (arg in args) {
                        arg.plant(this@Call)
                    }
                }
            }
        }

        data class CallConstruction(
            val fn: MacroValue,
            val args: List<ValueConstruction>,
        ) : ValueConstruction {
            override fun plant(p: Planting) {
                p.Call(fn) {
                    for (arg in args) {
                        arg.plant(this@Call)
                    }
                }
            }
        }

        data class StaticPropertyConstruction(
            val subjectType: Type2,
            val memberName: Symbol,
        ) : ValueConstruction {
            override fun plant(p: Planting) {
                p.Call {
                    V(BuiltinFuns.vGets)
                    V(Value(ReifiedType(subjectType), TType))
                    V(Value(memberName))
                }
            }
        }

        data class MethodConstruction(
            val subject: ValueConstruction,
            val methodName: Symbol,
        ) : ValueConstruction {
            override fun plant(p: Planting) {
                p.Call {
                    V(Value(DotHelper(ExternalCall, DotMember(methodName))))
                    subject.plant(this)
                }
            }
        }
    }
}

private fun optimizeAutoescaperUse(
    use: AutoescUseInfo,
    iCtx: InterpretationContext,
    escaperUnraveler: EscaperUnraveler,
    logSink: LogSink,
) {
    val block = use.declaringBlock
    val accumulatorName = use.name
    val accumulatorType = use.types.accumulatorType
    val accumulatorReifiedType = Value(ReifiedType(accumulatorType))

    val basicDefinitions =
        (use.types.contextualAutoescapingAccumulatorType.definition.name as? ExportedName ?: return)
            .origin
    val propagateOverName = ExportedName(basicDefinitions, ParsedName("propagateOver"))
    val appendParseEffectName = ExportedName(basicDefinitions, ParsedName("AppendParseEffect"))
    val eventParseEffectName = ExportedName(basicDefinitions, ParsedName("EventParseEffect"))

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
                V(accumulatorReifiedType)
                V(Symbol("initialState"))
            }
        }
    } as? Value<*> ?: return
    val contextPropagator = iCtx.interpret(init.pos) {
        Call {
            Call(BuiltinFuns.vGets) {
                V(accumulatorReifiedType)
                V(Symbol("propagator"))
            }
        }
    } as? Value<*> ?: return
    val sameStateFn = iCtx.interpret(init.pos) {
        Call {
            Call(BuiltinFuns.vGets) {
                V(accumulatorReifiedType)
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
                V(accumulatorReifiedType)
                V(Symbol("picker"))
            }
        }
    } as? Value<*> ?: return
    val collectorType = use.types.collectorType ?: return

    if (DEBUG) {
        console.log("Optimizing ${block.document.context.formatPosition(init.pos)}")
    }

    // We define a callout function for use with `propagate`, `mergeState` and others.
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
                // Downgrade static errors when used inside a test body.
                use.inTestBody -> Log.Info
                isError -> Log.Error
                else -> Log.Warn
            }

            receiver(messageText, level)
        }
    }

    // We need to track ANALYSES so that we can do dataExports when we're all done.
    var analyses: Value<*> = TNull.value
    var analysesOk = true
    // This starts off null, but if there is a foldAnalyses function, it might return
    // non-null.
    val (foldAnalyses, exportAnalyses) = TList.unpackOrNull(
        iCtx.interpret(startPath.diagnosticPosition.leftEdge) {
            Call(BuiltinFuns.vListifyFn) {
                Call(BuiltinFuns.vGets) {
                    V(accumulatorReifiedType)
                    V(Symbol("foldAnalyses"))
                }
                Call(BuiltinFuns.vGets) {
                    V(accumulatorReifiedType)
                    V(Symbol("exportAnalyses"))
                }
            }
        } as? Value<*>,
    ) ?: listOf(null, null)

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

    val methodClassifications = mutableMapOf<Member, Pair<MethodShape?, AppendClassification?>>()

    // Accumulator methods like append(...) might have been overloaded and resolved to
    // other methods, so we back-compute that by looking at @overload(...) metadata
    // to come up with a classification.
    fun methodClassification(member: Member): Pair<MethodShape?, AppendClassification?> =
        methodClassifications.getOrPut(member) {
            val method = accumulatorType.definition.membersMatching(member).firstOrNull()
                as? MethodShape

            val classification = if (method?.methodKind == MethodKind.Normal) {
                if (member == appendDotName) {
                    AppendClassification.AppendUnsafe
                } else if (member == appendSafeDotName) {
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

    fun propagateOverStmt(stateBefore: Value<*>, ref: BlockChildReference): Pair<Value<*>, List<Value<*>>?>? {
        val edge = block.dereference(ref) ?: return null
        val t = edge.target
        // merge with prior state
        var state = stateBefore
        withCallouts { problem, severity ->
            problems.add(CalledOutProblem(severity, ref.pos.leftEdge, problem))
        }
        var effectValues: List<Value<*>>? = null
        // If it's a call to append or appendSafe, update the context,
        // and remember it as something we need to change.
        if (t is CallTree && t.size >= 2) {
            val callee = t.child(0)
            val fn = callee.functionContained
            if (fn is DotHelper && fn.memberAccessor is CallMemberAccessor) {
                val subject = callee.child(fn.memberAccessor.firstArgumentIndex + 1)
                if (subject is RightNameLeaf && subject.content == accumulatorName) {
                    val (_, classification) = methodClassification(fn.member)
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
                        val effectValueList = TList.unpackOrNull(after.readField(effectsDotName))
                            ?: return null
                        val effects = buildList {
                            for (effectValue in effectValueList) {
                                val shape = (effectValue.typeTag as? TClass)?.typeShape
                                when (shape?.name) {
                                    appendParseEffectName -> {
                                        val text = TString.unpackOrNull(
                                            effectValue.readField(textDotName),
                                        ) ?: return@propagateOverStmt null
                                        add(AppendEffectDetail(text))
                                    }
                                    eventParseEffectName -> {
                                        val eventValue = effectValue.readField(eventDotName)
                                            ?: return@propagateOverStmt null
                                        val eventName = use.common.nameFor(eventValue)
                                            ?: return@propagateOverStmt null
                                        add(EventEffectDetail(eventName))
                                    }
                                    else -> return@propagateOverStmt null
                                }
                            }
                        }
                        effectValues = effectValueList
                        state = after.readField(iCtx, stateAfterGetter, argPos) ?: return null
                        val escapers = when (classification) {
                            AppendClassification.AppendUnsafe -> {
                                val escaperValue = iCtx.interpret(ref.pos) {
                                    Call(escaperForDotHelper) {
                                        V(escaperPicker)
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
                        toChange.add(ChangeDetail(edge, effects, escapers, classification))
                    }
                }
            }
        }
        return state to effectValues
    }

    val startStateMap = mutableMapOf<Pair<MaximalPathIndex, Int>, MutableSet<Value<*>>>()
    startStateMap[startPath.pathIndex to startOffset] = mutableSetOf(initialState)
    val q = ArrayDeque<Pair<MaximalPathIndex, Int>>()
    q.add(startPath.pathIndex to startOffset)
    while (q.isNotEmpty()) {
        val (pathIndex, startOffset) = q.removeFirst()
        val startStates = startStateMap.remove(pathIndex to startOffset)!!
        val path = paths[pathIndex]
        val indices = startOffset..path.elements.lastIndex

        val startState = run {
            val stateIterator = startStates.iterator()
            check(stateIterator.hasNext())
            var state = stateIterator.next()

            val pathInitPos = (path.elements.getOrNull(startOffset)?.pos ?: init.pos).leftEdge
            while (stateIterator.hasNext()) {
                val stateToMerge = stateIterator.next()
                state = iCtx.interpret(pathInitPos) {
                    Call {
                        Call(BuiltinFuns.vGets) {
                            V(accumulatorReifiedType)
                            V(Symbol("mergeStates"))
                        }
                        V(state)
                        V(stateToMerge)
                        V(vCallout)
                    }
                } as? Value<*> ?: return@optimizeAutoescaperUse
            }
            withCallouts { problem, severity ->
                problems.add(CalledOutProblem(severity, pathInitPos, problem))
            }
            state
        }

        var autoescState = startState
        var skipFollowers = false
        val allEffectValues = mutableListOf<Value<*>>()
        for (i in indices) {
            val ref = path.elements[i].ref
            if (ref.index == endChildIndex) {
                skipFollowers = true
                // Check the accumulator's end state, which should recursively
                // end delegates.
                iCtx.interpret(ref.pos) {
                    Call {
                        Call(BuiltinFuns.vGets) {
                            V(accumulatorReifiedType)
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
            val (stateAfter, effectValues) = propagateOverStmt(autoescState, ref) ?: return
            autoescState = stateAfter
            if (effectValues != null) { allEffectValues.addAll(effectValues) }
        }
        if (foldAnalyses != null && exportAnalyses != null) {
            val analysesAfter = iCtx.interpret(path.diagnosticPosition) {
                Call {
                    V(foldAnalyses)
                    V(analyses)
                    V(Value(allEffectValues.toList(), TList))
                    V(vCallout)
                }
            } as? Value<*>
            if (analysesAfter != null) {
                analyses = analysesAfter
            } else {
                analysesOk = false
            }
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
                        else -> (propagateOverStmt(stateBeforeCondition, cond.ref) ?: return).first
                    }
                    startStateMap.putMultiSet(key, stateForFollower)
                    val remaining = predecessorCount.getValue(key) - 1
                    if (remaining != 0) {
                        predecessorCount[key] = remaining
                    } else {
                        predecessorCount.remove(key)
                        q.add(Pair(followerPathIndex, 0))
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
            typeShape.membersMatching(appendSafeDotName, includeMetadata = true).any {
                it is MethodShape && it.methodKind == MethodKind.Normal && it.visibility == Visibility.Public
            }
        if (hasAppendSafeMethod) {
            appendSafeDotHelper
        } else {
            appendDotHelper
        }
    }
    val changes: List<Pair<TEdge, Planting.() -> UnpositionedTreeTemplate<*>>> = toChange.map { change ->
        val (edge, effects, escapers, classification) = change
        val ps = change.positions
        edge to {
            fun Planting.plantSafeAdjusted(safePs: AppendStmtPositions, safe: String): UnpositionedTreeTemplate<*> =
                Call(safePs.pos) {
                    V(safePs.callee, Value(collectorAppendDotHelper))
                    Rn(safePs.subject, collector)
                    V(safePs.arg, Value(safe, TString))
                }
            fun Planting.plantEvent(name: TemperName): UnpositionedTreeTemplate<*> =
                Call(ps.pos) {
                    Call(ps.callee, BuiltinFuns.vGets) {
                        V(ps.callee, accumulatorReifiedType)
                        V(ps.callee.rightEdge, Value(enactDotName))
                    }
                    Rn(ps.arg, name)
                    Rn(ps.subject, collector)
                }
            fun Planting.plantEffects(ps: AppendStmtPositions) {
                for (effect in effects) {
                    when (effect) {
                        is AppendEffectDetail -> plantSafeAdjusted(ps, effect.text)
                        is EventEffectDetail -> plantEvent(effect.eventName)
                    }
                }
            }
            when (classification) {
                AppendClassification.AppendSafe -> Block(ps.pos) {
                    plantEffects(ps)
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
                                    V(Value(applyDotHelper))
                                    Call(BuiltinFuns.vGets) {
                                        V(Value(ReifiedType(escapers.first()), TType))
                                        V(Symbol("instance"))
                                    }
                                    escapeArg(escapers.subListToEnd(1))
                                }
                            }
                        }
                        escapeArg(escapers)
                    }

                    if (effects.isNotEmpty()) {
                        Block {
                            plantEffects(AppendStmtPositions(ps.pos.leftEdge))
                            plantUnsafeAppend()
                        }
                    } else {
                        plantUnsafeAppend()
                    }
                }
            }
        }
    }

    val dataExports = mutableListOf<DataExport>()
    if (exportAnalyses != null) {
        var analysesProblem: String? = null
        if (analysesOk) {
            if (analyses != TNull.value) {
                val exportsResult = iCtx.interpret(use.accumulated.pos) {
                    Call {
                        V(exportAnalyses)
                        V(analyses)
                    }
                }
                val exports = TList.unpackOrNull(exportsResult as? Value<*>)
                if (exports != null) {
                    exports.mapNotNullTo(dataExports) {
                        val path = it.readField(pathDotName)
                        val mimeType = it.readField(mimeTypeDotName)
                        val data = it.readField(dataDotName)
                        if (path?.typeTag == TString && mimeType?.typeTag == TString && data != null) {
                            @Suppress("UNCHECKED_CAST") // tags checked above
                            DataExport(path as Value<String>, mimeType as Value<String>, data)
                        } else {
                            analysesProblem = "Invalid data export $it"
                            null
                        }
                    }
                } else {
                    analysesProblem = "$accumulatorType.exportAnalyses failed"
                }
            }
        } else {
            analysesProblem = "$accumulatorType.foldAnalyses failed"
        }
        if (analysesProblem != null) {
            problems.add(CalledOutProblem(Log.Error, use.accumulated.pos.leftEdge, analysesProblem))
        }
    }

    // Now, make the changes:
    // - change the initializer
    // - change .accumulated read.
    // - apply the statement changes from above
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

    use.common.addDataExports(dataExports)

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

private sealed class EffectDetail

private data class AppendEffectDetail(val text: String) : EffectDetail()
private data class EventEffectDetail(val eventName: Temporary) : EffectDetail()

private data class ChangeDetail(
    val edge: TEdge,
    val effects: List<EffectDetail>,
    val escapers: List<Type2>?,
    val classification: AppendClassification,
) {
    val positions: AppendStmtPositions get() = AppendStmtPositions(edge.target)
}

private val stateAfterGetter = DotHelper(ExternalGet, DotMember(Symbol("stateAfter")))
private val effectsDotName = Symbol("effects")
private val escaperForDotHelper = DotHelper(ExternalCall, DotMember(Symbol("escaperFor")))
private val appendSafeDotHelper = DotHelper(ExternalCall, appendSafeDotName)
private val appendDotHelper = DotHelper(ExternalCall, appendDotName)
private val applyDotHelper = DotHelper(ExternalCall, DotMember(Symbol("apply")))
private val enactDotName = Symbol("enact")
private val eventDotName = Symbol("event")
private val textDotName = Symbol("text")
private val pathDotName = Symbol("path")
private val mimeTypeDotName = Symbol("mimeType")
private val dataDotName = Symbol("data")

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

private data class DataExport(
    val path: Value<String>,
    val mimeType: Value<String>,
    val data: Value<*>,
)
