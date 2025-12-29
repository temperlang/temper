package lang.temper.be.tmpl

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.AwaitFn
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.AtomicCounter
import lang.temper.common.Either
import lang.temper.env.InterpMode
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.interp.New
import lang.temper.interp.emptyValue
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.name.BuiltinName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.NameMaker
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.InvalidType
import lang.temper.type.MkType
import lang.temper.type.StaticType
import lang.temper.type.SuperTypeTree
import lang.temper.type.TypeFormal
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes
import lang.temper.type.excludeNullAndBubble
import lang.temper.type.extractNominalTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.TypeContext2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.hackMapOldStyleToNewOrNull
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.MaximalPath
import lang.temper.value.MaximalPathIndex
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.Panic
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.StructuredFlow
import lang.temper.value.TBoolean
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TNull
import lang.temper.value.Tree
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.YieldingCallDisassembled
import lang.temper.value.YieldingFnKind
import lang.temper.value.disassembleYieldingCall
import lang.temper.value.forwardMaximalPaths
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.ssaSymbol
import lang.temper.value.symbolContained
import lang.temper.value.typeFromSignature
import lang.temper.value.vVarSymbol
import lang.temper.value.varSymbol
import lang.temper.value.void

internal fun convertCoroutineToControlFlow(
    block: BlockTree,
    nameMaker: NameMaker,
    outputName: TemperName?,
    supportNetwork: SupportNetwork,
    typeContext2: TypeContext2,
    generatorType: Type2,
    translateBlockChild: (Tree) -> PreTranslated,
): PreTranslated.ConvertedCoroutine {
    val generatorName = nameMaker.unusedTemporaryName("generator")
    val tree = convertAwaitToYield(block, generatorName, generatorType, nameMaker, supportNetwork)
    val document = tree.document
    val lPos = tree.pos.leftEdge
    val rPos = tree.pos.rightEdge
    val doneResultExport = ImplicitsModule.module.exports!!.first { it.name.baseName.nameText == "doneResult" }
    val valueResultTypeExport = ImplicitsModule.module.exports!!.first { it.name.baseName.nameText == "ValueResult" }
    val doneResultType = MkType2(WellKnownTypes.doneResultTypeDefinition).get()

    val yieldedTypeActual = typeContext2.superTypeTreeOf(generatorType)[
        WellKnownTypes.generatorTypeDefinition,
    ].firstOrNull()?.bindings?.getOrNull(0)
        ?: WellKnownTypes.invalidType2

    fun doneResultExpr(): PreTranslated {
        return PreTranslated.TreeWrapper(
            document.treeFarm.grow(rPos) {
                val doneResultOldType = hackMapNewStyleToOld(doneResultType)
                val doneCallType = CallTypeInferences(
                    doneResultOldType,
                    MkType.fn(listOf(), listOf(), null, doneResultOldType),
                    mapOf(),
                    listOf(),
                )
                Call(type = doneCallType) {
                    Rn(doneResultExport.name, type = doneResultExport.typeInferences?.type)
                }
            },
        )
    }
    fun valueResultExpr(pos: Position, yielded: Either<ResolvedName, Value<*>>, type: Type2): PreTranslated {
        return PreTranslated.TreeWrapper(
            document.treeFarm.grow(pos) {
                Call(pos.leftEdge, New, type = valueResultCtorSig(type)) {
                    V(pos.leftEdge, valueResultTypeExport.value!!, WellKnownTypes.typeType)
                    when (yielded) {
                        is Either.Left -> Rn(pos, yielded.item, type = hackMapNewStyleToOld(type))
                        is Either.Right -> V(pos, yielded.item, type = hackMapNewStyleToOld(type))
                    }
                }
            },
        )
    }

    val maximalPaths = forwardMaximalPaths(tree, yieldingCallsEndPaths = true)

    // A. We need two variable declarations, one in the wrapper so that we know,
    //    after resuming, where we're resuming.  Since we're building a big
    //    `when (caseIndex) { ... }`, the variables just store `Int`s.
    //    We need a second to capture the `caseIndex` locally so that we can
    //    set the outer one to `-1` to avoid re-entering the `match`
    //    if an exception or panic causes us to not complete a turn.
    // B. For each maximal path, we need to allocate a `match` case.
    //    We may need a second one.  If the path branches conditionally to two
    //    or more others, then any side effects in those conditions need to
    //    happen after resumption, which means that we need to set the case index
    //    to another one that does the tests, and picks another case index.
    // C. We build a list of cases based on the allocations above by translating
    //    path elements, translating any `yield` to `return` after setting
    //    the case index for the follower.
    // D. Then we need to pull out declarations that are not used entirely
    //    within the same case. These end up getting defined on the wrapper
    //    function by TmpLTranslator.
    // E. Finally, we wrap in a `match` based on the `caseIndex` variable,
    //    and if we need to `continue` from any case
    //    (because it doesn't yield/return), we wrap in a while loop.

    // Step A in the plan above: get some names for case indices.
    fun combinedDeclarationForIntVar(
        nameHint: String,
        isConst: Boolean,
        makeInitial: (Planting).() -> UnpositionedTreeTemplate<*>,
    ): Pair<Temporary, PreTranslated.CombinedDeclaration> {
        val name = nameMaker.unusedTemporaryName(nameHint)

        val initial = tree.document.treeFarm.grow(lPos) {
            makeInitial()
        }
        return name to PreTranslated.CombinedDeclaration(
            pos = lPos,
            declaration = tree.document.treeFarm.grow(lPos) {
                Decl {
                    Ln(name, type = WellKnownTypes.intType)
                    if (isConst) {
                        V(ssaSymbol)
                        V(void)
                    } else {
                        V(varSymbol)
                        V(void)
                    }
                }
            },
            initializer = tree.document.treeFarm.grow(lPos) {
                Call(BuiltinFuns.vSetLocalFn, type = intAssignCallType) {
                    Ln(name, type = WellKnownTypes.intType)
                    Replant(initial)
                }
            },
            initial = initial,
        )
    }

    // Step (B): figure out what each case index means.

    // Keep track of yielding calls by path index
    val yieldingDetailsMap = mutableMapOf<MaximalPathIndex, YieldingCallDisassembled>()
    // We need some temporaries
    val temporariesAllocated: MutableMap<MaximalPathIndex, MutableList<Pair<ResolvedName, Type2>>> =
        mutableMapOf()

    // Set to false if we need an outer `while (true)` loop so that cases may
    // `continue` to another without `return`ing to simulate a yield.
    var allPathsYieldOrTerminate = true
    // Each path index has one or two case indices:
    // 1. The case index that starts its work.  When one path jumps to another,
    //    the jump is to the start of the destination path.
    // 2. Optionally, a follow-on index.
    //
    //    If a path ends with a `yield`, AND it may conditionally jump,
    //    it has a follow-on case index that performs the extra condition
    //    checking logic AFTER resuming from the yield.
    //    The case index is set to the follow-on index BEFORE the yield so that
    //    the conditions are checked first, upon resuming.
    //
    //    And `await` doesn't here because we replaced with `yield` above.
    val pathIndexToCaseIndices = buildMap {
        var caseIndexCounter = 0
        maximalPaths.maximalPaths.forEach { path ->
            val pathIndex = path.pathIndex
            val startCaseIndex = caseIndexCounter++
            val yieldingDetails = path.elements.lastOrNull()?.stmt?.let { stmt ->
                disassembleYieldingCall(stmt, tree)
            }
            if (yieldingDetails != null) {
                yieldingDetailsMap[pathIndex] = yieldingDetails
            }
            val needsFollowOn = when (yieldingDetails?.kind) {
                YieldingFnKind.yield -> path.followers.size != 1 || path.followers[0].condition != null
                else -> false
            }
            val followOnCaseIndex = if (needsFollowOn) {
                caseIndexCounter++
            } else {
                null
            }
            this[pathIndex] = startCaseIndex to followOnCaseIndex
            if (
                allPathsYieldOrTerminate && yieldingDetails == null &&
                pathIndex !in maximalPaths.exitPathIndices &&
                pathIndex !in maximalPaths.failExitPathIndices
            ) {
                allPathsYieldOrTerminate = false
            }
        }
    }
    // We initialize the case variable to 0 so this had better hold.
    val (caseIndexName, caseIndexDecl) =
        combinedDeclarationForIntVar("caseIndex", isConst = false) {
            // This should always be zero, but it's easy enough to compute.
            val (startCaseIndex) = pathIndexToCaseIndices.getValue(maximalPaths.entryPathIndex)
            V(Value(startCaseIndex, TInt), type = WellKnownTypes.intType)
        }

    // Here's where we accumulate the variables pulled out into the wrapper in (D)
    // but first, just capture the case index declaration from (A)
    val persistentDeclarations = mutableListOf<PreTranslated>(caseIndexDecl)

    // Step (C): build the case list.
    // But first, some helpers.
    // setCaseInstruction -> `caseIndex = 123;`
    fun setCaseInstruction(newCaseIndex: Int): PreTranslated {
        return PreTranslated.TreeWrapper(
            tree.document.treeFarm.grow(rPos) {
                Call(BuiltinFuns.vSetLocalFn) {
                    Ln(caseIndexName, WellKnownTypes.intType)
                    V(Value(newCaseIndex, TInt), WellKnownTypes.intType)
                }
            },
        )
    }

    // combines before and after into a block if both are not-null but flattens
    fun maybeCombineIntoBlock(before: PreTranslated?, after: PreTranslated?): PreTranslated? =
        if (before != null && after != null) {
            PreTranslated.Block(
                rPos,
                buildList {
                    for (pt in listOf(before, after)) {
                        if (pt is PreTranslated.Block) {
                            addAll(pt.unfixedElements)
                        } else {
                            add(pt)
                        }
                    }
                },
            )
        } else {
            before ?: after
        }

    // Recursively builds an if/else-if/else` based on follower conditions
    // using setCaseIndex.
    fun MaximalPath.buildFollowersNestedIfs(followerIndex: Int): PreTranslated? {
        if (followerIndex == followers.size) { return null }
        val follower = followers[followerIndex]
        val condition = follower.condition
        val nextCaseIndex = follower.pathIndex?.let {
            pathIndexToCaseIndices.getValue(it).first
        }
        val condTree = condition?.ref?.let { tree.dereference(it)?.target }
        return if (condTree == null) {
            maybeCombineIntoBlock(
                if (nextCaseIndex != null) {
                    setCaseInstruction(nextCaseIndex)
                } else {
                    null
                },
                buildFollowersNestedIfs(followerIndex + 1),
            )
        } else if (nextCaseIndex == null) {
            maybeCombineIntoBlock(
                translateBlockChild(condTree),
                buildFollowersNestedIfs(followerIndex + 1),
            )
        } else {
            PreTranslated.If(
                rPos,
                translateBlockChild(condTree),
                setCaseInstruction(nextCaseIndex),
                buildFollowersNestedIfs(followerIndex + 1)
                    ?: PreTranslated.Block(rPos, emptyList()),
            )
        }
    }

    val cases = mutableListOf<Pair<Int, PreTranslated.Block>>()
    maximalPaths.maximalPaths.forEach { path ->
        val pathIndex = path.pathIndex
        val (caseIndex, followOnCaseIndex) = pathIndexToCaseIndices.getValue(pathIndex)
        val isTerminal = pathIndex in maximalPaths.exitPathIndices ||
            pathIndex in maximalPaths.failExitPathIndices

        val (elementsNotYielding, yieldingElement) = path.elements.let {
            val last = it.lastOrNull()
            if (pathIndex in yieldingDetailsMap) {
                it.subList(0, it.lastIndex) to last
            } else {
                it to null
            }
        }

        val caseBodyElements = buildList {
            // Allocate any temporaries from above
            temporariesAllocated[pathIndex]?.let { temporaries ->
                val pos = path.diagnosticPosition.leftEdge
                temporaries.forEach { (temporary, type) ->
                    add(
                        PreTranslated.TreeWrapper(
                            document.treeFarm.grow(pos) {
                                Decl {
                                    Ln(temporary, type = hackMapNewStyleToOld(type))
                                    V(varSymbol)
                                    V(void)
                                }
                            },
                        ),
                    )
                }
            }

            // Build the instructions
            elementsNotYielding.forEach { el ->
                val t = el.ref.let { tree.dereference(it) }?.target
                val emit = when {
                    t == null -> false
                    t is ValueLeaf -> false // Don't translate left over `void`
                    // Don't translate assignments like `return__123 = void` because
                    // we're repurposing `return` for `yield`.
                    isAssignmentCall(t) && (t.child(1) as? NameLeaf)?.content == outputName -> false
                    else -> true
                }
                if (t != null && emit) {
                    add(translateBlockChild(t))
                }
            }

            // Build the transitions to the next case
            if (followOnCaseIndex != null) {
                setCaseInstruction(followOnCaseIndex)
            } else {
                path.buildFollowersNestedIfs(0)
            }?.let { followInstruction ->
                if (isAwakeUponCall(lastOrNull())) {
                    // We need to assign next case before any awakeUpon.
                    add(size - 1, followInstruction)
                } else {
                    add(followInstruction)
                }
            }
            // Return if we need to yield or if this is terminal.
            if (yieldingElement != null) {
                // TODO: to support yield with value, return value from yieldElement
                val yieldingPos = yieldingElement.pos
                val yieldingDetails = yieldingDetailsMap.getValue(pathIndex)
                val yieldingCall = yieldingDetails.yieldingCall
                val yieldingKind = yieldingDetails.kind
                val yieldingArg = yieldingCall.childOrNull(1)
                val (yielded: Either<ResolvedName, Value<*>>, yieldedType: Type2) = if (
                    yieldingArg == null || yieldingKind != YieldingFnKind.yield
                ) {
                    Either.Right(emptyValue) to yieldedTypeActual
                } else {
                    val type = yieldingArg.typeInferences?.type?.let { hackMapOldStyleToNew(it) }
                        ?: WellKnownTypes.invalidType2
                    when (yieldingArg) {
                        is ValueLeaf -> Either.Right(yieldingArg.content)
                        is RightNameLeaf -> Either.Left(yieldingArg.content as ResolvedName)
                        else -> TODO(
                            """
                            {
                            val tName = nameMaker.unusedTemporaryName("tYield")
                            add(PreTranslated.CombinedDeclaration(
                                yieldPos.leftEdge,
                                document.treeFarm.grow(yieldArg.pos.leftEdge) {
                                    Decl {
                                        Ln(tName, type = type)
                                        V(ssaSymbol)
                                        V(void)
                                    }
                                },
                                // TODO create initializer,
                                yieldArg,
                            ))
                            Either.Left(tName)
                            }
                            """,
                        )
                    } to type
                }
                add(PreTranslated.Return(yieldingPos, valueResultExpr(yieldingPos, yielded, yieldedType)))
            } else if (isTerminal) {
                add(PreTranslated.Return(rPos, doneResultExpr()))
            }

            // Combine declarations where possible.
            // This makes it easier to extract named function declarations in whole
            // below.
            val stmts = this.toList()
            clear()
            combineDeclarationsOnto(stmts, this)
        }

        cases.add(
            caseIndex to PreTranslated.Block(
                caseBodyElements.spanningPosition(rPos),
                caseBodyElements,
            ),
        )

        if (followOnCaseIndex != null) {
            val instructions = buildList {
                val chooseFollower = path.buildFollowersNestedIfs(0)
                if (chooseFollower != null) {
                    add(chooseFollower)
                }
            }
            cases.add(
                followOnCaseIndex to PreTranslated.Block(
                    instructions.spanningPosition(rPos),
                    instructions,
                ),
            )
        }
    }

    // To match, we do the following
    //    let caseIndexLocal#2 = caseIndex#1;
    //    caseIndex = -1; // If we fail, don't repeat the failure
    //    when (t#0) { cases }
    val (caseIndexLocalName, caseIndexLocalDecl) =
        combinedDeclarationForIntVar("caseIndexLocal", isConst = true) {
            Rn(caseIndexName, type = WellKnownTypes.intType)
        }

    val elseCase = PreTranslated.Return(rPos, doneResultExpr())

    // Step (D): Figure out which declarations to extract.
    // If something is used in a different case than it's declared then
    // we need to extract it.
    class StepDUseRecord(
        val caseDeclaring: Int,
        val combinedDeclaration: PreTranslated.CombinedDeclaration?,
    ) {
        val casesUsing = mutableSetOf<Int>()
    }
    val nameToCasesUsingAndDeclaring = mutableMapOf<TemperName, StepDUseRecord>()
    // Fill in the use records.
    val mentionsCache = MentionsCache()
    cases.forEach { (caseIndex, body) ->
        body.unfixedElements.forEach { bodyElement ->
            UnwrappedDecl.of(bodyElement)?.let { (decl) ->
                decl.parts?.name?.content?.let { nameDeclared ->
                    nameToCasesUsingAndDeclaring[nameDeclared] = StepDUseRecord(
                        caseDeclaring = caseIndex,
                        combinedDeclaration = bodyElement as? PreTranslated.CombinedDeclaration,
                    )
                }
            }
        }
    }
    cases.forEach { (caseIndex, body) ->
        val mentions = mentionsCache.mentions(body)
        for (nameSet in listOf(mentions.leftNames, mentions.rightNames)) {
            nameSet.forEach { mentionedName ->
                nameToCasesUsingAndDeclaring[mentionedName]?.casesUsing?.add(caseIndex)
            }
        }
    }
    // Figure out which names to extract
    val namesToExtract = buildSet {
        // When we extract a CombinedDeclaration of a named function, make sure
        // that we extract everything it needs too.
        fun extractAndMaybeExtractDependentMentions(name: TemperName, useRecord: StepDUseRecord) {
            if (name in this) { return }
            add(name)

            val cd = useRecord.combinedDeclaration
            if (cd?.initial is FunTree) {
                // It's a named function declaration
                val mentions = mentionsCache.reads(cd)
                for (mentionedName in mentions) {
                    nameToCasesUsingAndDeclaring[mentionedName]?.let {
                        extractAndMaybeExtractDependentMentions(mentionedName, it)
                    }
                }
            }
        }

        for ((name, useRecord) in nameToCasesUsingAndDeclaring) {
            val caseDeclaring = useRecord.caseDeclaring
            val shouldExtract = when (useRecord.casesUsing.size) {
                0 -> false
                // If there's one, and they're different, maybe we could move things around.
                // That might happen in a situation where a declaration is hoisted or in:
                //
                //     let x;
                //     yield;
                //     x = 1;
                1 -> caseDeclaring != useRecord.casesUsing.first()
                else -> true
            }
            if (shouldExtract) {
                extractAndMaybeExtractDependentMentions(name, useRecord)
            }
        }
    }

    val variablesToNullAdjust = mutableMapOf<TemperName, Type2>()
    if (namesToExtract.isNotEmpty()) {
        // Remove the declarations from the cases.
        // Then we copy the trees, so that \ssa metadata is not used to mark
        // them as const because we will need to assign a zero-value so that
        // we pass static languages' init-before-use checks.
        val casesAffected = buildSet {
            namesToExtract.forEach { nameToExtract ->
                add(nameToCasesUsingAndDeclaring.getValue(nameToExtract).caseDeclaring)
            }
        }
        val extracted = mutableListOf<PreTranslated>()
        for (i in cases.indices) {
            val (caseIndex, caseBody) = cases[i]
            if (caseIndex !in casesAffected) { continue }
            val adjustedBodyElements = caseBody.unfixedElements.mapNotNull { pt ->
                val (decl, initializer, initial) = UnwrappedDecl.of(pt)
                    ?: return@mapNotNull pt
                val declName = decl.parts?.name?.content
                if (declName != null && declName in namesToExtract) {
                    val extractWhole = when (initial) {
                        null -> true
                        // If it's a simple function or value, pull the whole thing out.
                        is FunTree, is ValueLeaf -> true
                        else -> false
                    }

                    if (extractWhole) {
                        extracted.add(pt)
                        null
                    } else {
                        check(initializer != null)
                        extracted.add(PreTranslated.TreeWrapper(decl))
                        // Leave the initializer in place.
                        PreTranslated.TreeWrapper(initializer)
                    }
                } else {
                    pt
                }
            }
            val adjustedCaseBody = caseBody.copy(elements = adjustedBodyElements)
            cases[i] = (caseIndex to adjustedCaseBody)
        }
        // Now remove any \ssa metadata and add the decl to the extracted list
        for (extractedElement in extracted) {
            if (extractedElement is PreTranslated.CombinedDeclaration) {
                // We've got the whole thing.  No adjustments necessary
                persistentDeclarations.add(extractedElement)
                continue
            }

            val declTree = (extractedElement as PreTranslated.TreeWrapper).tree as DeclTree
            val parts = declTree.parts!!

            val type = hackMapOldStyleToNewOrNull(parts.name.typeInferences?.type).orInvalid
            val (zeroValue, needsNullability, adjustedType) = ZeroValues[type]
            val adjustedTypeOld = hackMapNewStyleToOld(adjustedType)

            val adjustedDeclTree = if (
                ssaSymbol in parts.metadataSymbolMultimap ||
                varSymbol !in parts.metadataSymbolMultimap ||
                needsNullability
            ) {
                val copy = declTree.copy(copyInferences = true) as DeclTree
                copy.parts?.name?.let {
                    it.typeInferences = BasicTypeInferences(
                        adjustedTypeOld,
                        it.typeInferences?.explanations ?: emptyList(),
                    )
                }
                var i = 1
                while (i < copy.size) {
                    val key = copy.child(i).symbolContained
                    if (key == ssaSymbol) {
                        copy.replace(i..(i + 1)) {}
                    } else {
                        i += 2
                    }
                }
                copy.insert(copy.size) {
                    V(vVarSymbol, type = WellKnownTypes.symbolType)
                    V(void, type = WellKnownTypes.voidType)
                }
                if (type != adjustedType) {
                    copy.parts?.type?.replace {
                        V(Value(ReifiedType(adjustedType)), type = WellKnownTypes.typeType)
                    }
                }
                copy
            } else {
                declTree
            }

            // Turn into a CombinedTypeDeclaration with an initializer to the zero
            // value and note which ones need type adjustment and casting away from null for uses.
            val syntheticPos = adjustedDeclTree.pos.rightEdge
            val zeroValueTree = document.treeFarm.grow(syntheticPos) {
                V(zeroValue, type = adjustedTypeOld)
            }
            val synthesizedInitializer = document.treeFarm.grow(syntheticPos) {
                Call {
                    V(BuiltinFuns.vSetLocalFn)
                    Ln(parts.name.content, adjustedTypeOld)
                    Replant(zeroValueTree)
                }
            }

            persistentDeclarations.add(
                PreTranslated.CombinedDeclaration(
                    adjustedDeclTree.pos,
                    adjustedDeclTree,
                    synthesizedInitializer,
                    zeroValueTree,
                ),
            )

            if (needsNullability && type != WellKnownTypes.invalidType2) {
                variablesToNullAdjust[parts.name.content] = type
            }
        }
    }

    // Step (E): Build `when` and `while`
    val match = PreTranslated.WhenInt(
        pos = tree.pos,
        caseExpr = PreTranslated.TreeWrapper(
            tree.document.treeFarm.grow(lPos) {
                Rn(caseIndexLocalName)
            },
        ),
        cases = cases.map { (caseIndex, body) ->
            setOf(caseIndex) to body
        },
        elseCase = elseCase,
    )

    var body: PreTranslated = PreTranslated.Block(
        pos = tree.pos,
        elements = listOf(
            // let caseIndexLocal = caseIndex;
            caseIndexLocalDecl,
            // caseIndex = -1; // until a case decides otherwise.
            PreTranslated.TreeWrapper(
                tree.document.treeFarm.grow(lPos) {
                    Call(BuiltinFuns.setLocalFn) {
                        Ln(caseIndexName, type = WellKnownTypes.intType)
                        V(Value(-1, TInt), type = WellKnownTypes.intType)
                    }
                },
            ),
            // when (caseIndexLocal) { ... }
            match,
        ),
    )

    if (!allPathsYieldOrTerminate) {
        body = PreTranslated.WhileLoop(
            pos = tree.pos,
            test = PreTranslated.TreeWrapper(
                tree.document.treeFarm.grow(lPos) {
                    V(TBoolean.valueTrue, WellKnownTypes.booleanType)
                },
            ),
            body = body,
        )
    }

    return PreTranslated.ConvertedCoroutine(
        pos = tree.pos,
        persistentDeclarations = persistentDeclarations.toList(),
        generatorName = generatorName,
        body = body,
        variablesToNullAdjust = variablesToNullAdjust.toMap(),
    )
}

private fun isAwakeUponCall(pt: PreTranslated?) =
    pt is PreTranslated.TreeWrapper &&
        pt.tree is CallTree &&
        pt.tree.childOrNull(0)?.functionContained == ConvertedCoroutineAwakeUponFn

private fun convertAwaitToYield(
    tree: BlockTree,
    generatorName: TemperName,
    generatorType: Type2,
    nameMaker: NameMaker,
    supportNetwork: SupportNetwork,
): BlockTree {
    // First see if there's any await in the first place before doing copies.
    var anyAwait = false
    TreeVisit.startingAt(tree).forEach anyAwait@{ node ->
        node is FunTree && return@anyAwait VisitCue.SkipOne
        if (node is CallTree && node.childOrNull(0)?.functionContained == AwaitFn) {
            anyAwait = true
            return@anyAwait VisitCue.AllDone
        }
        VisitCue.Continue
    }.visitPreOrder()
    if (!anyAwait) {
        // No await, so nothing to transform.
        return tree
    }
    // Work on a copy of the tree so don't modify common data for just one backend.
    val copy = tree.copy(copyInferences = true) as BlockTree
    TreeVisit.startingAt(copy).forEach nodes@{ node ->
        node is FunTree && return@nodes VisitCue.SkipOne
        val block = (node as? BlockTree) ?: return@nodes VisitCue.Continue
        val flow = (block.flow as? StructuredFlow) ?: return@nodes VisitCue.Continue
        fun scan(controlFlow: ControlFlow) {
            val isBlock = when (controlFlow) {
                is ControlFlow.StmtBlock -> {
                    convertAwaitToYield(controlFlow, block, generatorName, generatorType, nameMaker, supportNetwork)
                    true
                }

                else -> false
            }
            for (clause in controlFlow.clauses) {
                scan(clause)
                if (!isBlock && clause is ControlFlow.Stmt) {
                    TODO("convert to StmtBlock as needed?")
                }
            }
        }
        scan(flow.controlFlow)
        VisitCue.Continue
    }.visitPreOrder()
    return copy
}

fun convertAwaitToYield(
    flow: ControlFlow.StmtBlock,
    tree: BlockTree,
    generatorName: TemperName,
    generatorType: Type2,
    nameMaker: NameMaker,
    supportNetwork: SupportNetwork,
) {
    // Gather the awaits, and replace each `await` with `getPromiseResultSync` on first pass.
    class AwaitInfo(val promiseTree: Tree, val unhandledFailVar: TemperName?)
    val awaits = mutableListOf<Pair<Int, AwaitInfo>>()
    stmts@ for ((index, stmt) in flow.stmts.withIndex()) {
        // Presume we've already extracted temporaries enough to have only one await at most per statement and that the
        // statement fits expected patterns.
        stmt is ControlFlow.Stmt || continue@stmts
        val yieldingDetails = disassembleYieldingCall(stmt, tree) ?: continue@stmts
        yieldingDetails.kind == YieldingFnKind.await || continue@stmts
        val yieldingCall = yieldingDetails.yieldingCall
        val promiseTree = yieldingCall.child(1)
        var unhandledFailVar: TemperName? = null
        val yieldingIncoming = yieldingCall.incoming!!
        yieldingIncoming.replace { _ ->
            val yielded = yieldingCall.typeInferences?.type ?: InvalidType
            val sig = GetPromiseResultSyncFn.sig
            val getPromiseResultSyncCallType = CallTypeInferences(
                yielded,
                typeFromSignature(sig),
                mapOf(sig.typeFormals[0] to yielded),
                listOf(),
            )
            Call(GetPromiseResultSyncFn, type = getPromiseResultSyncCallType) {
                when (supportNetwork.bubbleStrategy) {
                    BubbleBranchStrategy.IfHandlerScopeVar -> null
                    BubbleBranchStrategy.CatchBubble -> yieldingDetails.failVar?.let { failVar ->
                        unhandledFailVar = failVar
                        Rn(failVar, type = WellKnownTypes.booleanType)
                    }
                } ?: V(TNull.value, type = MkType.nullable(WellKnownTypes.booleanType))
                Replant(freeTree(promiseTree))
            }
        }
        awaits.add(index to AwaitInfo(promiseTree, unhandledFailVar))
    }
    // Now rebuild the stmt block if we had any awaits.
    if (awaits.isNotEmpty()) {
        var awaitIndex = 0
        var awaitPair: Pair<Int, AwaitInfo>? = awaits[awaitIndex]
        flow.withMutableStmtList { newStmts ->
            newStmts.clear()
            for ((index, stmt) in flow.stmts.withIndex()) {
                while (index == awaitPair?.first) {
                    // Extract temporary if the promise isn't a simple name reference.
                    val promiseRef = when (val promiseTree = awaitPair!!.second.promiseTree) {
                        is RightNameLeaf -> promiseTree.copy(copyInferences = true) as RightNameLeaf
                        else -> {
                            val type = promiseTree.typeInferences?.type
                            val tempName = nameMaker.unusedTemporaryName("promise")
                            // Declaration.
                            tree.insert {
                                Decl {
                                    Ln(tempName, type = type)
                                    V(ssaSymbol)
                                    V(void)
                                }
                            }
                            newStmts.add(ControlFlow.Stmt(BlockChildReference(tree.size - 1, promiseTree.pos)))
                            // Init.
                            promiseTree.incoming!!.replace { Rn(tempName, type = type) }
                            tree.insert {
                                Call(
                                    BuiltinFuns.vSetLocalFn,
                                    type = assignCallType(
                                        type ?: InvalidType,
                                        hackMapNewStyleToOld(promiseTree.typeOrInvalid),
                                    ),
                                ) {
                                    Ln(tempName, type = type)
                                    Replant(promiseTree)
                                }
                            }
                            newStmts.add(ControlFlow.Stmt(BlockChildReference(tree.size - 1, promiseTree.pos)))
                            // Leaf usable for reference.
                            RightNameLeaf(promiseTree.document, promiseTree.pos, tempName).also { nameLeaf ->
                                nameLeaf.typeInferences = type?.let { BasicTypeInferences(it, listOf()) }
                            }
                        }
                    }
                    // Insert awakeUpon and yield statements before this one. And maybe extracted temp before that.
                    val awakeUponType = typeFromSignature(ConvertedCoroutineAwakeUponFn.sig)
                    val promiseTypeArg = promiseRef.typeInferences?.type?.let { promiseSubType ->
                        extractNominalTypes(excludeNullAndBubble(promiseSubType)).firstOrNull()
                            ?.let { promiseNominalSubType ->
                                val stt = SuperTypeTree.of(promiseNominalSubType)
                                stt[WellKnownTypes.promiseTypeDefinition].firstOrNull()?.let { promiseTypeProjected ->
                                    promiseTypeProjected.bindings.firstOrNull() as StaticType
                                }
                            }
                    } ?: InvalidType
                    tree.insert {
                        Call(
                            type = CallTypeInferences(
                                WellKnownTypes.voidType,
                                variant = awakeUponType,
                                bindings2 = mapOf(
                                    awakeUponType.typeFormals[0] to promiseTypeArg,
                                ),
                                explanations = listOf(),
                            ),
                        ) {
                            V(
                                Value(ConvertedCoroutineAwakeUponFn, TFunction),
                                type = awakeUponType,
                            )
                            Replant(promiseRef)
                            Rn(promiseRef.pos.rightEdge, generatorName, type = hackMapNewStyleToOld(generatorType))
                        }
                    }
                    newStmts.add(ControlFlow.Stmt(BlockChildReference(tree.size - 1, promiseRef.pos)))
                    tree.insert { Call(BuiltinFuns.vYield, type = nothingToVoid) {} }
                    newStmts.add(ControlFlow.Stmt(BlockChildReference(tree.size - 1, promiseRef.pos)))
                    // Preassign any unhandled fail var.
                    // TODO Java backend currently expects this, but I'm unsure it's needed for correctness.
                    val unhandledFailVar = awaitPair!!.second.unhandledFailVar
                    if (unhandledFailVar != null) {
                        // Former comments:
                        // Set the fail var to false before testing.
                        // This has the side effect of making it apparent that
                        // the fail var is really a left hand side.
                        // TODO(): Use a LeftName above which would mean we
                        // need GetPromiseResultSync to have its own TmpL variant.
                        tree.insert {
                            Call(BuiltinFuns.setLocalFn, type = assignCallType(WellKnownTypes.booleanType)) {
                                Ln(unhandledFailVar, type = WellKnownTypes.booleanType)
                                V(TBoolean.valueFalse, type = WellKnownTypes.booleanType)
                            }
                        }
                        newStmts.add(ControlFlow.Stmt(BlockChildReference(tree.size - 1, promiseRef.pos)))
                    }
                    // Advance the `await` that we're on.
                    awaitIndex += 1
                    awaitPair = awaits.getOrNull(awaitIndex)
                }
                newStmts.add(stmt)
            }
        }
    }
}

internal fun nullAdjustConvertedCoroutineBody(
    body: TmpL.BlockStatement,
    variablesToNullAdjust: Map<TemperName, Type2>,
): TmpL.BlockStatement {
    val rewriter = object : TmpLTreeRewriter {
        val tc = TypeContext2()

        override fun rewriteReference(x: TmpL.Reference): TmpL.Expression {
            val typeBeforeNullAdjustment = variablesToNullAdjust[x.id.name]
            if (typeBeforeNullAdjustment != null) {
                // If the reference is already inside an expression that casts away null,
                // there's no need to make changes.
                val isAlreadyNarrow = when (val parent = x.parent) {
                    is TmpL.UncheckedNotNullExpression -> true // Idempotent
                    is TmpL.CastExpression -> {
                        parent.expr === x &&
                            // Is already narrowed to the before type.
                            !tc.isSubType(parent.type, typeBeforeNullAdjustment)
                    }
                    else -> false
                }

                if (!isAlreadyNarrow) {
                    return TmpL.UncheckedNotNullExpression(
                        pos = x.pos,
                        expression = super.rewriteReference(x),
                        type = typeBeforeNullAdjustment,
                    )
                }
            }

            return super.rewriteReference(x)
        }
    }
    return rewriter.rewriteBlockStatement(body) as TmpL.BlockStatement
}

private data class UnwrappedDecl(
    val decl: DeclTree,
    val initializer: Tree?,
    val initial: Tree?,
) {
    companion object {
        fun of(pt: PreTranslated): UnwrappedDecl? = when (pt) {
            is PreTranslated.TreeWrapper ->
                (pt.tree as? DeclTree)?.let { UnwrappedDecl(it, null, null) }
            is PreTranslated.CombinedDeclaration ->
                UnwrappedDecl(pt.declaration, pt.initializer, pt.initial)
            else -> null
        }
    }
}

/**
 * `awakeUpon(promise, generator)` indicates that *generator* should be stepped
 * upon the resolution of *promise*.
 */
object ConvertedCoroutineAwakeUponFn : NamedBuiltinFun, BuiltinStatelessMacroValue {
    override val name = "awakeUpon"

    val sig = run {
        // Fn <T>(Promise<T>, Generator<T>): Void
        val (t, tt) = makeTypeFormalHelper(name, "Y")
        Signature2(
            returnType2 = WellKnownTypes.voidType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(
                MkType2(WellKnownTypes.promiseTypeDefinition).actuals(listOf(tt)).get(),
                MkType2(WellKnownTypes.generatorTypeDefinition).actuals(listOf(tt)).get(),
            ),
            typeFormals = listOf(t),
        )
    }

    override val sigs = listOf(sig)

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        throw Panic()
    }
}

/**
 * `getPromiseResultSync(fail#1, promise)` means unpack promise.
 *
 * When it's a broken promise, set `fail#1` to true.
 * The fail is replaced with `null` when error handling doesn't require trapping exceptions,
 * as the backend can expect a normal handler scope call around the call to this function.
 */
object GetPromiseResultSyncFn : NamedBuiltinFun, BuiltinStatelessMacroValue {
    override val name = "getPromiseResultSync"

    val sig = run {
        val (t, tt) = makeTypeFormalHelper(name, "Y")
        // Fn <Y>(Boolean, Promise<Y>): Y
        Signature2(
            returnType2 = tt,
            hasThisFormal = false,
            requiredInputTypes = listOf(
                WellKnownTypes.booleanType2,
                MkType2(WellKnownTypes.promiseTypeDefinition).actuals(listOf(tt)).get(),
            ),
            typeFormals = listOf(t),
        )
    }

    override val sigs = listOf(sig)

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        throw Panic()
    }
}

internal fun makeTypeFormalHelper(
    fnName: String,
    nameSuffix: String,
): Pair<TypeFormal, Type2> {
    val nameKey = "$fnName$nameSuffix"
    val typeFormal = TypeFormal(
        Position(ImplicitsCodeLocation, 0, 0),
        BuiltinName(nameKey),
        Symbol(nameKey),
        Variance.Invariant,
        AtomicCounter(),
        upperBounds = listOf(WellKnownTypes.anyValueType),
    )
    val typeT = MkType2(typeFormal).get()
    return typeFormal to typeT
}

private fun assignCallType(left: StaticType, right: StaticType = left) =
    CallTypeInferences(
        left,
        MkType.fn(
            listOf(),
            listOf(left, right),
            null,
            left,
        ),
        mapOf(),
        listOf(),
    )

private val intAssignCallType = assignCallType(WellKnownTypes.intType)
private val nothingToVoid = CallTypeInferences(
    WellKnownTypes.voidType,
    MkType.fn(listOf(), listOf(), null, WellKnownTypes.voidType),
    mapOf(),
    listOf(),
)

private fun valueResultCtorSig(binding: Type2): CallTypeInferences {
    val def = WellKnownTypes.valueResultTypeDefinition
    val tf = def.typeParameters[0].definition
    val tft = MkType.nominal(tf)
    val bindingOld = hackMapNewStyleToOld(binding)
    return CallTypeInferences(
        MkType.nominal(def, listOf(bindingOld)),
        MkType.fn(listOf(tf), listOf(tft), null, MkType.nominal(def, listOf(tft))),
        mapOf(tf to bindingOld),
        listOf(),
    )
}
