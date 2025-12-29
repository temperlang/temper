package lang.temper.frontend.typestage

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.isHandlerScopeCall
import lang.temper.common.Cons
import lang.temper.common.ignore
import lang.temper.common.putMultiList
import lang.temper.common.subListToEnd
import lang.temper.frontend.syntax.isAssignment
import lang.temper.log.LogSink
import lang.temper.name.InternalModularName
import lang.temper.name.ResolvedName
import lang.temper.name.TemperName
import lang.temper.name.unusedAnalogueFor
import lang.temper.type.Abstractness
import lang.temper.type.AndType
import lang.temper.type.DotHelper
import lang.temper.type.ExternalBind
import lang.temper.type.ExternalGet
import lang.temper.type.ExternalSet
import lang.temper.type.FunctionType
import lang.temper.type.InternalBind
import lang.temper.type.InternalGet
import lang.temper.type.InternalMemberAccessor
import lang.temper.type.InternalSet
import lang.temper.type.InvalidType
import lang.temper.type.MethodShape
import lang.temper.type.MkType
import lang.temper.type.NominalType
import lang.temper.type.StaticType
import lang.temper.type.SuperTypeTree
import lang.temper.type.TypeActual
import lang.temper.type.TypeBindingMapper
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.isVoidLike
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.mapType
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.JumpDestination
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.NameLeaf
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.StructuredFlow
import lang.temper.value.TEdge
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.TypeInferences
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.inlineUnrealizedGoalSymbol
import lang.temper.value.matches
import lang.temper.value.ssaSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.void

/**
 * Step 1. Find function trees with unrealized goals
 *         that are immediately passed to calls.
 * Step 2. Examine each call that takes one or more of those
 *         to see if it is inlineable, and inlining would
 *         allow eliminating repairing one or more goals.
 * Step 3. Do inlining.
 * Step 4. Issue errors about functions that still have
 *         unrealized goals.
 * Step 5. We introduced blocks when inlining, so return
 *         the edges containing everything that needs
 *         re-weaving.
 */
internal fun inlineToRepairUnrealizedGoals(
    root: BlockTree,
    logSink: LogSink,
): List<TEdge> {
    val inliner = InlineToRepairUnrealizedGoals(root, logSink)
    if (!inliner.findFunctionsWithUnrealizedGoals()) { return emptyList() }
    if (!inliner.findCallsToInline()) { return emptyList() }
    inliner.doInlining()
    inliner.reportRemainingUnrealizedGoals()
    return inliner.getNeedsWeaving()
}

private class InlineToRepairUnrealizedGoals(
    val root: BlockTree,
    val logSink: LogSink,
) {
    private val reparableFns = mutableSetOf<FunTree>()
    fun findFunctionsWithUnrealizedGoals(): Boolean {
        TreeVisit.startingAt(root)
            .forEachContinuing { tree ->
                if (tree !is FunTree) { return@forEachContinuing }
                val body = tree.parts?.body as? BlockTree ?: return@forEachContinuing
                val flow = body.flow
                if (flow is StructuredFlow) {
                    val controlFlows: ArrayDeque<Pair<ControlFlow, Cons<JumpDestination>>> =
                        ArrayDeque(listOf(flow.controlFlow to Cons.Empty))
                    while (true) {
                        val (cf, inScope) = controlFlows.removeFirstOrNull()
                            ?: break
                        if (cf is ControlFlow.Jump) {
                            if (inScope.none { it.matches(cf) }) {
                                reparableFns.add(tree)
                                break
                            }
                        } else if (cf is JumpDestination) {
                            val inScopeWithDest = Cons(cf, inScope)
                            cf.clauses.mapTo(controlFlows) {
                                it to inScopeWithDest
                            }
                        } else {
                            cf.clauses.mapTo(controlFlows) {
                                it to inScope
                            }
                        }
                    }
                }
            }
            .visitPreOrder()
        return reparableFns.isNotEmpty()
    }

    private data class FunArg(
        val argIndex: Int,
        val funTree: FunTree,
    )

    private data class CallToInline(
        /** The call to inline */
        val call: CallTree,
        /**
         * The curried this argument followed by the other actual arguments.
         */
        val combinedCallArgs: List<Tree>,
        /**
         * Bindings of type parameters for the `this` type in the context of the call.
         * If the method is defined on C<T> and the type of `this` in the call is `C<Foo>`,
         * then this relates <T> to Foo.
         */
        val thisTypeBindings: Map<TypeFormal, TypeActual>,
        /**
         * The method shape of the definition to inline
         */
        val methodShape: MethodShape,
        /**
         * The definition of the function or method called in [call].
         */
        val definition: FunTree,
        /**
         * Relates names of inputs to definition to block lambdas in [call].
         */
        val formalNameToFunArg: Map<TemperName, FunArg>,
        /** The function type of the callee ignoring any marker interfaces. */
        val variantFunctionType: FunctionType,
    )
    private val callsToInline = mutableMapOf<CallTree, CallToInline>()

    fun findCallsToInline(): Boolean {
        val callsReceivingReparableFns = mutableMapOf<CallTree, MutableList<FunArg>>()
        for (f in reparableFns) {
            val incoming = f.incoming
            val parent = incoming?.source as? CallTree ?: continue
            val edgeIndex = incoming.edgeIndex
            if (edgeIndex == 0) {
                // Immediately called function, not argument.
                // TODO: We could consider an alternate inlining strategy for
                // immediately called functions with unrealized goals if needed
                // but that is not what we do below.
                continue
            }
            callsReceivingReparableFns.putMultiList(
                parent,
                FunArg(
                    // edge indices include callee, but there's also a subject that needs to be curried in,
                    // so we subtract one for the callee, and add one back.
                    argIndex = edgeIndex,
                    funTree = f,
                ),
            )
        }

        // Now that we've grouped args by the call that receives them, try to
        // assemble enough information for a CallToInline.
        // A call to inline needs:
        // - a non-overridable callee with an available body.
        // - @inlineUnrealizedGoal metadata on at least one reparable arg declaration.
        // - to use a reparable arg in at most one place in an immediate call.
        call_loop@
        for ((call, args) in callsReceivingReparableFns) {
            val variantType = variantFunctionType(call.typeInferences?.variant) ?: continue
            val calleeDefinition = getDefinitionOfCallee(call)
            val definingFn = calleeDefinition?.definition
            val definingFnParts = definingFn?.parts ?: continue
            // Pair FunArgs with parameter names.
            val indexToArg = buildMap {
                args.forEach { this[it.argIndex] = it }
            }
            val formalNameToFunArg = mutableMapOf<TemperName, FunArg>()
            for ((i, formal) in definingFnParts.formals.withIndex()) {
                val formalParts = formal.parts ?: continue@call_loop
                if (inlineUnrealizedGoalSymbol in formalParts.metadataSymbolMultimap) {
                    val funArg = indexToArg[i] ?: continue
                    formalNameToFunArg[formalParts.name.content] = funArg
                }
            }

            // We've paired local names within the function body to functions to inline.
            // Now prune out anywhere the local name is over-used.
            // If the local name is `f`, then look for situations where:
            //
            // 1. `f` is passed, so calling it is delegated.  It needs to be a function value.
            //
            //    g(f);
            //
            // 2. `f` is multiply called, so inlining it might lead to an explosion in code size.
            //
            //    f();
            //    f();
            if (formalNameToFunArg.isNotEmpty()) {
                val formalNameToCallCount = mutableMapOf<TemperName, Int?>()
                formalNameToFunArg.forEach { (name) -> formalNameToCallCount[name] = 0 }
                // If we have the first situation, we set the count to null.
                TreeVisit.startingAt(definingFnParts.body)
                    .forEachContinuing { t ->
                        if (t is RightNameLeaf) {
                            val name = t.content
                            if (name in formalNameToCallCount) {
                                val edge = t.incoming!!
                                if (edge.edgeIndex != 0 || edge.source !is CallTree) { // Not a callee
                                    formalNameToCallCount[name] = null
                                } else {
                                    formalNameToCallCount[name]?.let { oldCount ->
                                        formalNameToCallCount[name] = oldCount + 1
                                    }
                                }
                            }
                        }
                    }
                    .visitPreOrder()
                for ((name, count) in formalNameToCallCount) {
                    if (count == null || count > 1) {
                        formalNameToFunArg.remove(name)
                    }
                }
                if (formalNameToFunArg.isNotEmpty()) {
                    // If the callee is a method, we need to relate type parameters on the declaring type
                    // to type actuals on the thisType
                    val definingTypeShape = calleeDefinition.definingTypeShape
                    val thisType = calleeDefinition.thisType
                    val thisTypeBindings: Map<TypeFormal, TypeActual>? = when {
                        definingTypeShape == null && thisType == null -> emptyMap()
                        definingTypeShape != null && thisType != null -> {
                            // Use a super-type tree so that if the type of `this` is List<T>
                            // and the method is defined in a super-type, Listed<T>, we get
                            // bindings in the context of the <T> on Listed because that is
                            // what will appear in the definition body.
                            val stt = SuperTypeTree.of(thisType)
                            stt[definingTypeShape].firstOrNull()?.let { contextualizedThisType ->
                                val bindingsZipFormal =
                                    contextualizedThisType.bindings zip definingTypeShape.formals
                                buildMap {
                                    for ((actual, formal) in bindingsZipFormal) {
                                        this[formal] = actual
                                    }
                                }
                            }
                        }
                        else -> null
                    }

                    if (thisTypeBindings != null) {
                        callsToInline[call] = CallToInline(
                            call = call,
                            combinedCallArgs = buildList {
                                add(calleeDefinition.thisArg)
                                addAll(call.children.subListToEnd(1))
                            },
                            thisTypeBindings = thisTypeBindings,
                            methodShape = calleeDefinition.methodShape,
                            definition = definingFn,
                            formalNameToFunArg = formalNameToFunArg.toMap(),
                            variantFunctionType = variantType,
                        )
                    }
                }
            }
        }

        return callsToInline.isNotEmpty()
    }

    private val needWeaving = mutableListOf<TEdge>()
    fun doInlining() {
        call_loop@
        for (callToInline in callsToInline.values) {
            val call = callToInline.call
            val calleeTree = callToInline.definition
            val formalNameToArg = callToInline.formalNameToFunArg
            val thisBindings = callToInline.thisTypeBindings
            callToInline.thisTypeBindings

            val combinedCallArgs = callToInline.combinedCallArgs
            val callEdge = call.incoming!!
            val calleeParts = calleeTree.parts!!
            val calleeBody = calleeParts.body
            val formalBindings = call.typeInferences!!.bindings2 +
                thisBindings
            val typeMapper = TypeBindingMapper(formalToActual = formalBindings.entries)
            val variantType = callToInline.methodShape.descriptor?.let { sig ->
                sig.mapType(
                    formalBindings.mapValues { (_, actual) ->
                        hackMapOldStyleToNew(actual as? StaticType ?: InvalidType)
                    },
                )
            } ?: continue

            val notSet = mutableSetOf<TemperName>()
            val nameReplacements = mutableMapOf<TemperName, ResolvedName>()

            val doc = call.document
            val leftPos = call.pos.leftEdge
            val rightPos = call.pos.rightEdge

            fun adaptType(t: StaticType): StaticType = MkType.map(t, typeMapper)

            fun adaptTypeActual(t: TypeActual): TypeActual = MkType.map(t, typeMapper)

            fun adaptCallTypeInferences(ti: CallTypeInferences?): CallTypeInferences? {
                if (ti == null) { return null }
                return CallTypeInferences(
                    type = adaptType(ti.type),
                    variant = adaptType(ti.variant),
                    bindings2 = ti.bindings2.mapValues { adaptTypeActual(it.value) },
                    explanations = ti.explanations,
                )
            }

            @Suppress("UNCHECKED_CAST")
            fun <TI : TypeInferences> adaptTypeInferences(ti: TI?): TI? = when (ti) {
                null -> null
                is CallTypeInferences -> adaptCallTypeInferences(ti) as TI
                is BasicTypeInferences -> ti.copy(type = adaptType(ti.type)) as TI
            }

            fun inlineCallee(t: Tree): Tree = when (t) {
                is BlockTree -> {
                    val inlinedChildren = t.children.map { inlineCallee(it) }
                    val flowCopy = when (val flow = t.flow) {
                        is StructuredFlow -> StructuredFlow(flow.controlFlow.deepCopy())
                        LinearFlow -> flow
                    }
                    BlockTree(doc, t.pos, inlinedChildren, flowCopy)
                }
                is CallTree -> {
                    val calleeAsName = t.child(0) as? RightNameLeaf
                    if (calleeAsName != null && calleeAsName.content in formalNameToArg) {
                        // Inline a block lambda in place of the call.
                        val inlinedChildren = mutableListOf<Tree>()
                        val funArg = formalNameToArg.getValue(calleeAsName.content)
                        val funTreeToInline = funArg.funTree
                        val funPartsToInline = funTreeToInline.parts!!
                        // Declare the parameters.
                        inlinedChildren.addAll(funPartsToInline.formals)
                        // Declare the return declaration unless it's void-like.
                        // If it's void-like, we need to avoid creating a local variable
                        // that we assign void to.
                        val blockReturnDecl = funPartsToInline.returnDecl
                        val blockReturnNameTree = blockReturnDecl?.parts?.name
                        val blockReturnType = blockReturnNameTree?.typeInferences?.type
                            ?: WellKnownTypes.voidType
                        val isBlockVoidLike = blockReturnType.isVoidLike
                        if (!isBlockVoidLike) {
                            inlinedChildren.add(blockReturnDecl!!)
                        }
                        for (argIndex in 0 until t.size - 1) {
                            val actual = t.child(argIndex + 1)
                            val inlinedActual = inlineCallee(actual)
                            val formal = funPartsToInline.formals.getOrNull(argIndex)
                            val formalParts = formal?.parts
                            inlinedChildren.add(
                                if (formalParts != null) {
                                    val actualLeft = inlinedActual.pos.leftEdge
                                    val left = formalParts.name.copy(doc, copyInferences = true)
                                    val typeAssigned = left.typeInferences?.type ?: InvalidType
                                    val assign = ValueLeaf(doc, actualLeft, BuiltinFuns.vSetLocalFn)
                                    val eqType = MkType.fn(
                                        typeFormals = emptyList(),
                                        valueFormals = listOf(typeAssigned, typeAssigned),
                                        restValuesFormal = null,
                                        returnType = typeAssigned,
                                    )
                                    assign.typeInferences = BasicTypeInferences(eqType, emptyList())
                                    val initializer = CallTree(
                                        doc,
                                        actual.pos,
                                        listOf(assign, left, inlinedActual),
                                    )
                                    initializer.typeInferences = CallTypeInferences(
                                        type = typeAssigned,
                                        variant = eqType,
                                        bindings2 = emptyMap(),
                                        explanations = emptyList(),
                                    )
                                    initializer
                                } else {
                                    // evaluate for side-effect even if we don't need them.
                                    // TODO: test that bundling up rest arguments has already happened.
                                    inlinedActual
                                },
                            )
                        }
                        // Inline the body.
                        val blockBody = funPartsToInline.body
                        inlinedChildren.add(blockBody)
                        // If we have a return variable, add a terminal statement.
                        val terminalPos = funPartsToInline.body.pos.rightEdge
                        if (isBlockVoidLike) {
                            val terminalValue = ValueLeaf(doc, terminalPos, void)
                            terminalValue.typeInferences = BasicTypeInferences(WellKnownTypes.voidType, emptyList())
                            inlinedChildren.add(terminalValue)

                            // Remove any `return__123 = void` statements.
                            if (blockReturnNameTree != null) {
                                eliminateAssignmentToVoidLikeResultName(
                                    blockBody,
                                    blockReturnName = blockReturnNameTree.content,
                                )
                            }
                        } else {
                            val returnRead = RightNameLeaf(doc, terminalPos, blockReturnNameTree!!.content)
                            returnRead.typeInferences = BasicTypeInferences(blockReturnType, emptyList())
                            inlinedChildren.add(returnRead)
                        }
                        inlinedChildren.forEach {
                            // disassemble the body block lambda now that we can re-parent in a block
                            freeTree(it)
                        }
                        BlockTree(doc, t.pos, inlinedChildren.toList(), LinearFlow)
                    } else {
                        val inlineChildren = mutableListOf<Tree>()
                        t.children.mapTo(inlineChildren) { inlineCallee(it) }
                        val adaptedTypeInferences = adaptCallTypeInferences(t.typeInferences)
                        // A method body may validly use *public* members via `this`.
                        // We rewrite `this` via the normal name substitution path in
                        // inlineTree(NameLeaf), but we still need to adjust
                        // icalls to calls.
                        val callee = inlineChildren.getOrNull(0)
                        if (callee is ValueLeaf) {
                            val calleeValue = callee.functionContained
                            if (calleeValue is DotHelper && calleeValue.memberAccessor is InternalMemberAccessor) {
                                convertMemberUseToExternal(calleeValue, inlineChildren)
                            }
                        }

                        val copy = CallTree(doc, t.pos, inlineChildren.toList())
                        copy.typeInferences = adaptedTypeInferences
                        copy
                    }
                }
                is DeclTree -> DeclTree(doc, t.pos, t.children.map { inlineCallee(it) })
                is EscTree -> EscTree(doc, t.pos, t.children.map { inlineCallee(it) })
                is FunTree -> {
                    val copy = FunTree(doc, t.pos, t.children.map { inlineCallee(it) })
                    copy.typeInferences = adaptTypeInferences(t.typeInferences)
                    copy
                }
                is NameLeaf -> {
                    val name = t.content as ResolvedName
                    // Call to inlined block lambda handled in Call
                    check(name !in formalNameToArg)
                    val replacementName = nameReplacements.getOrPut(name) {
                        if (name is InternalModularName) {
                            // The replacement needs to use names local to `doc`.
                            doc.nameMaker.unusedAnalogueFor(name)
                        } else {
                            name
                        }
                    }
                    val copy = when (t) {
                        is LeftNameLeaf -> LeftNameLeaf(doc, t.pos, replacementName)
                        is RightNameLeaf -> RightNameLeaf(doc, t.pos, replacementName)
                    }
                    copy.typeInferences = adaptTypeInferences(t.typeInferences)
                    copy
                }
                is StayLeaf -> StayLeaf(doc, t.pos)
                is ValueLeaf -> {
                    var value = t.content
                    val valueAsType = TType.unpackOrNull(value)
                    if (valueAsType is ReifiedType) {
                        val adaptedType = hackMapOldStyleToNew(adaptType(valueAsType.type))
                        value = Value(ReifiedType(adaptedType), TType)
                    }
                    val copy = ValueLeaf(doc, t.pos, value)
                    copy.typeInferences = adaptTypeInferences(t.typeInferences)
                    copy
                }
            }

            val isVoidLike = variantType.returnType2.isVoidLike
            val returnParts = calleeParts.returnDecl?.parts!!
            val returnName = returnParts.name.content as InternalModularName
            val variantReturnType = hackMapNewStyleToOld(variantType.returnType2)

            val localReturnName: TemperName?
            val convertingDeclarations: List<Triple<InternalModularName, StaticType, Tree?>> = buildList {
                // First, emit local declarations for each parameter.
                // For each formal parameter `x__1` and actual parameter `123`.
                //
                //     @ssa let x__2;
                //     x__2 = 123;
                //
                // In the inlined body, we rewrite `x__1` to `x__2`.
                for ((formalIndex, formal) in calleeParts.formals.withIndex()) {
                    val callChildIndex = formalIndex // Skip over callee
                    val formalName = formal.parts!!.name.content as InternalModularName
                    val paramType = hackMapNewStyleToOld(
                        variantType.valueFormalForActual(formalIndex)?.type
                            ?: WellKnownTypes.invalidType2,
                    )

                    if (formalName in formalNameToArg) {
                        // We'll inline it, so no need for a local name.
                        continue
                    }
                    val inlinedName = doc.nameMaker.unusedAnalogueFor(formalName)
                    nameReplacements[formalName] = inlinedName

                    val initializer = if (callChildIndex !in combinedCallArgs.indices) {
                        // Later we will rewrite isSet(formalName) to false
                        notSet.add(formalName)
                        null
                    } else {
                        freeTree(combinedCallArgs[callChildIndex])
                    }
                    add(Triple(inlinedName, paramType, initializer))
                }

                localReturnName = if (isVoidLike) {
                    null
                } else {
                    val localReturn = doc.nameMaker.unusedAnalogueFor(returnName)
                    nameReplacements[returnName] = localReturn
                    add(Triple(localReturn, variantReturnType, null))
                    localReturn
                }
            }

            callEdge.replace {
                Block { // We'll weave this back.
                    // Output declarations and initializers for names that represent names in the callee's context.
                    for ((inlinedName, paramType, initializer) in convertingDeclarations) {
                        Decl(leftPos) {
                            Ln(leftPos, inlinedName, type = paramType)
                            V(Value(typeSymbol), type = WellKnownTypes.symbolType)
                            V(
                                Value(ReifiedType(hackMapOldStyleToNew(paramType), hasExplicitActuals = true)),
                                type = WellKnownTypes.typeType,
                            )
                            V(Value(ssaSymbol), type = WellKnownTypes.symbolType)
                            V(void, type = WellKnownTypes.voidType)
                        }

                        if (initializer != null) {
                            Call(leftPos) {
                                V(
                                    leftPos,
                                    BuiltinFuns.vSetLocalFn,
                                    type = MkType.fn(emptyList(), listOf(paramType, paramType), null, paramType),
                                )
                                Ln(leftPos, inlinedName, paramType)
                                Replant(initializer)
                            }
                        }
                    }

                    // Inline the body
                    val inlinedCalleeBody = inlineCallee(calleeBody)
                    if (isVoidLike) {
                        nameReplacements[returnName]?.let { inlinedReturnName ->
                            eliminateAssignmentToVoidLikeResultName(inlinedCalleeBody, inlinedReturnName)
                        }
                    }
                    Replant(inlinedCalleeBody)

                    // Output the return name
                    if (localReturnName != null) {
                        Rn(rightPos, localReturnName, type = variantReturnType)
                    } else {
                        V(rightPos, void, type = WellKnownTypes.voidType)
                    }
                }
            }

            needWeaving.add(callEdge)
        }
    }

    fun reportRemainingUnrealizedGoals() {
        // TODO: Warn about unrealized goals that we couldn't repair and
        // replace with error nodes.
        ignore(logSink)
    }

    fun getNeedsWeaving() = needWeaving.toList()

    private data class CalleeDefinition(
        val definition: FunTree,
        val definingTypeShape: TypeShape?,
        val methodShape: MethodShape,
        val thisType: NominalType?,
        val thisArg: Tree,
    )

    private fun getDefinitionOfCallee(call: CallTree): CalleeDefinition? {
        val callee = call.child(0)

        // Look for a callee with the form (Call (Call DotHelper(ExternalBind) subject))
        val (dotHelper, thisArg) = run findDotHelper@{
            if (callee is CallTree) {
                val possibleDotHelper = callee.childOrNull(0)?.functionContained
                if (possibleDotHelper is DotHelper) {
                    val thisArg = callee.childOrNull(
                        possibleDotHelper.memberAccessor.enclosingTypeIndexOrNegativeOne + 2,
                    )
                    if (thisArg != null) {
                        return@findDotHelper possibleDotHelper to thisArg
                    }
                }
            }
            null to null
        }

        // We could try other strategies.  If callee is by name,
        // it would be good to have some way to follow any chain
        // of names back to a named function declaration or exported name.
        if (dotHelper != null && dotHelper.memberAccessor is ExternalBind) {
            val thisType = thisArg?.typeInferences?.type ?: return null
            val thisShape = representativeTypeShapeFor(listOf(thisType)) ?: return null

            val members = dotHelper.publicMembers(thisShape).toList() // Public since ExternalCall
            if (members.size == 1) {
                val member = members[0]
                if (member is MethodShape && member.isNotOverriddenIn(thisShape)) {
                    val decl = member.stay?.incoming?.source as? DeclTree ?: return null
                    val definition = findSoleInitializer(decl) as? FunTree
                    if (definition != null) {
                        return CalleeDefinition(
                            definition = definition,
                            definingTypeShape = member.enclosingType,
                            methodShape = member,
                            thisType = representativeNominalValueType(listOf(thisType)),
                            thisArg = thisArg,
                        )
                    }
                }
            }
        }
        return null
    }
}

private fun representativeNominalValueType(ts: Iterable<StaticType>): NominalType? {
    for (t in ts) {
        when (t) {
            is AndType -> return representativeNominalValueType(t.members) ?: continue
            is NominalType -> when (val defn = t.definition) {
                is TypeShape -> if (defn.isSubOrSame(WellKnownTypes.anyValueTypeDefinition)) {
                    return t
                }

                is TypeFormal -> representativeNominalValueType(defn.superTypes)
            }

            else -> {}
        }
    }
    return null
}

private fun representativeTypeShapeFor(ts: Iterable<StaticType>): TypeShape? =
    representativeNominalValueType(ts)?.definition as TypeShape?

/**
 * Conservative.  True if the given method is not overridden by any sub-type of [typeShape].
 *
 * Assumes [typeShape] extends whatever type defines this method and uses its implementation.
 *
 * True if, for example, this method is defined with a default, inherited implementation in
 * typeShape and typeShape cannot have a sub-type that overrides it.
 *
 * May return false even if not overridden
 */
fun MethodShape.isNotOverriddenIn(typeShape: TypeShape): Boolean {
    if (this.enclosingType.abstractness == Abstractness.Concrete) { return true }
    // TODO: for sealed interfaces, check exhaustively whether it is not
    // overridden in sub-types.
    ignore(typeShape)
    return false
}

fun findSoleInitializer(decl: DeclTree?): Tree? {
    val parts = decl?.parts ?: return null
    val metadata = parts.metadataSymbolMultimap
    if (ssaSymbol !in metadata) { return null } // Not a sole initializer
    val containingBlock = run {
        var parent = decl.incoming?.source
        while (parent != null && parent !is BlockTree) {
            parent = parent.incoming?.source
        }
        parent
    } ?: return null
    val name = parts.name.content
    for (child in containingBlock.children) {
        if (isAssignment(child)) {
            val left = child.child(1)
            if (left is LeftNameLeaf && left.content == name) {
                var right = child.child(2)
                if (isHandlerScopeCall(right)) {
                    right = right.child(2)
                }
                return right
            }
        }
    }
    return null
}

private fun variantFunctionType(variantType: StaticType?): FunctionType? = when (variantType) {
    is FunctionType -> variantType
    is AndType -> {
        var ft: FunctionType? = null
        for (member in variantType.members) {
            if (member is FunctionType) {
                if (ft != null) {
                    ft = null
                    break
                }
                ft = member
            }
        }
        ft
    }
    else -> null
}

private fun convertMemberUseToExternal(
    dotHelper: DotHelper,
    callChildren: MutableList<Tree>,
) {
    val accessor = dotHelper.memberAccessor
    if (accessor !is InternalMemberAccessor) { return }
    val externalAccessor = when (accessor) {
        InternalBind -> ExternalBind
        InternalGet -> ExternalGet
        InternalSet -> ExternalSet
    }
    val convertedDotHelper = DotHelper(externalAccessor, dotHelper.symbol, dotHelper.extensions)
    val callee = callChildren[0]
    val adjustedCallee = ValueLeaf(callee.document, callee.pos, Value(convertedDotHelper))
    adjustedCallee.typeInferences = callee.typeInferences?.let { ti ->
        BasicTypeInferences(ti.type, ti.explanations)
    }
    callChildren[0] = adjustedCallee

    if (callChildren.size > 1 && accessor.firstArgumentIndex > externalAccessor.firstArgumentIndex) {
        // Remove the reified type argument used for private accessors.
        callChildren.removeAt(1)
    }
}

private fun eliminateAssignmentToVoidLikeResultName(blockBody: Tree, blockReturnName: TemperName) =
    TreeVisit.startingAt(blockBody)
        .forEachContinuing {
            var bodyPart = it
            if (isHandlerScopeCall(bodyPart)) {
                bodyPart = bodyPart.child(2)
            }
            if (isAssignment(bodyPart)) {
                val left = bodyPart.child(1) as? NameLeaf
                if (left?.content == blockReturnName) {
                    bodyPart.incoming!!.replace {
                        Replant(freeTree(bodyPart.child(2)))
                    }
                }
            }
        }
        .visitPreOrder()
