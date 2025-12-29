package lang.temper.value

import lang.temper.common.Either
import lang.temper.common.EnumRange
import lang.temper.common.LeftOrRight
import lang.temper.common.NoneShortOrLong
import lang.temper.common.TextOutput
import lang.temper.common.TriState
import lang.temper.common.allMapToSameElseNull
import lang.temper.common.mapInterleaving
import lang.temper.common.temperEscaper
import lang.temper.common.toStringViaTextOutput
import lang.temper.cst.InnerOperatorStackElement
import lang.temper.cst.NameConstants
import lang.temper.cst.OperatorStackElement
import lang.temper.cst.canNest
import lang.temper.env.Constness
import lang.temper.format.FormattingHints
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TextOutputTokenSink
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.lexer.Lexer
import lang.temper.lexer.Operator
import lang.temper.lexer.OperatorType
import lang.temper.lexer.TokenType
import lang.temper.lexer.nextNotSyntheticOrNull
import lang.temper.lexer.sourceOffsetOf
import lang.temper.log.FilePositions
import lang.temper.log.LogSink
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.UnknownCodeLocation
import lang.temper.log.spanningPosition
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.Name
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.type.AndType
import lang.temper.type.BindMemberAccessor
import lang.temper.type.BubbleType
import lang.temper.type.DotHelper
import lang.temper.type.FunctionType
import lang.temper.type.GetMemberAccessor
import lang.temper.type.InfiniBinding
import lang.temper.type.InvalidType
import lang.temper.type.NominalType
import lang.temper.type.OrType
import lang.temper.type.SetMemberAccessor
import lang.temper.type.StaticType
import lang.temper.type.TopType
import lang.temper.type.TypeActual
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.Wildcard
import lang.temper.type.isNullType

/**
 * Generates code that looks kind of like Temper-code for diagnostic purposes.
 */
fun (Tree).toPseudoCode(
    tokenSink: TokenSink,
    detail: PseudoCodeDetail = PseudoCodeDetail.default,
) {
    val pseudoTree = PseudoTreeBuilder(root = this, detail).build()
    val opTree = pseudoTree.reduce()

    maybeParenthesize(opTree, null)
    if (!detail.preserveOuterCurlies) {
        maybeStripCurliesFromRoot(opTree)
    }

    renderOpTree(opTree, tokenSink)
}

/**
 * Generates code that looks kind of like Temper-code for diagnostic purposes.
 */
fun Tree.toPseudoCode(
    out: TextOutput,
    positions: FilePositions = FilePositions.nil,
    singleLine: Boolean = false,
    detail: PseudoCodeDetail = PseudoCodeDetail.default,
) {
    TemperFormattingHints.makeFormattingTokenSink(
        TextOutputTokenSink(out),
        filePositions = positions,
        singleLine = singleLine,
    ).use { tokenSink ->
        this.toPseudoCode(tokenSink, detail = detail)
    }
}

@Suppress("unused") // Used during debugging.  Not in committed code.
fun Tree.toPseudoCode(
    positions: FilePositions = FilePositions.nil,
    singleLine: Boolean = true,
    detail: PseudoCodeDetail = PseudoCodeDetail.default,
): String = toStringViaTextOutput {
    toPseudoCode(
        it,
        positions = positions,
        singleLine = singleLine,
        detail = detail,
    )
}

private fun renderOpTree(t: OpTree, sink: TokenSink) {
    sink.position(t.pos, LeftOrRight.Left)
    when (t) {
        is Tok -> sink.emit(t.outputToken)
        is OpLeaf -> {
            for (tok in t.outputTokens) {
                sink.emit(tok)
            }
        }
        is OpInner -> {
            for (c in t.children) {
                renderOpTree(c, sink)
            }
        }
    }
    sink.position(t.pos, LeftOrRight.Right)
}

/** The kind of declaration which indicates how to turn metadata into syntactic elements. */
private enum class DeclKind(val afterEqualsSymbol: Symbol?) {
    Normal(initSymbol),
    Formal(defaultSymbol),
    Return(null),
}

internal class PseudoTreeBuilder(
    val root: Tree,
    val detail: PseudoCodeDetail,
) {
    fun build() = buildPseudoTree(root)

    private fun buildPseudoTree(tree: Tree): PseudoTree {
        val highlight = detail.highlight?.invoke(tree) ?: false
        val pos = tree.pos
        val pseudoTree = when (tree) {
            is BlockTree -> when (val flow = tree.content) {
                is LinearFlow -> {
                    val label =
                        if (tree.size >= 2 && tree.child(0).symbolContained == labelSymbol) {
                            tree.child(1) as? NameLeaf
                        } else {
                            null
                        }
                    if (label == null) {
                        val needsDoKeyword = run {
                            val parent = tree.incoming?.source
                            when {
                                // module roots and function bodies don't need `do`.
                                parent == null || tree === root -> false
                                parent is FunTree && parent.parts?.body == tree -> false
                                else -> true
                            }
                        }

                        PseudoBlock(
                            pos,
                            blockCaller = if (needsDoKeyword) {
                                PseudoNameLeaf(tree.pos.leftEdge, doBuiltinName)
                            } else {
                                null
                            },
                            elements = tree.children.map { buildPseudoTree(it) },
                        )
                    } else {
                        val stmts = (2 until tree.size).map {
                            buildPseudoTree(tree.child(it))
                        }
                        PseudoLabeled(
                            tree.pos,
                            PseudoNameLeaf(label.pos, label.content),
                            PseudoBlock(
                                stmts.spanningPosition(tree.pos.rightEdge),
                                blockCaller = PseudoNameLeaf(tree.pos.leftEdge, doBuiltinName),
                                elements = stmts,
                            ),
                        )
                    }
                }

                is StructuredFlow -> {
                    PseudoBlock.wrap(controlFlowToPseudoTree(flow.controlFlow, tree), pos = tree.pos)
                }
            }

            is CallTree -> {
                if (tree.size == 0) {
                    PseudoError(pos)
                } else if (detail.resugarDotHelpers && isDotHelperCall(tree)) {
                    val calleePos = tree.child(0).pos
                    val dotHelper = extractDotHelperFromCall(tree)!!
                    val memberAccessor = dotHelper.memberAccessor
                    val firstArgumentChildIndex = memberAccessor.firstArgumentIndex + 1 // skip over callee
                    val subject = tree.childOrNull(firstArgumentChildIndex) ?: return PseudoError(pos)
                    val symbol = dotHelper.symbol
                    val operation = PseudoCall(
                        pos = tree.pos,
                        callee = PseudoNameLeaf(calleePos, dotBuiltinName),
                        typeArgs = emptyList(),
                        args = listOf(
                            buildPseudoTree(subject),
                            PseudoNameLeaf(calleePos.rightEdge, ParsedName(symbol.text)),
                        ),
                    )
                    when (memberAccessor) {
                        is GetMemberAccessor -> operation
                        is SetMemberAccessor -> PseudoCall(
                            pos,
                            PseudoNameLeaf(subject.pos.rightEdge, assignBuiltinName),
                            emptyList(),
                            listOf(
                                operation,
                                buildPseudoTree(tree.child(firstArgumentChildIndex + 1)),
                            ),
                        )

                        is BindMemberAccessor -> operation
                    }
                } else {
                    var callee = buildPseudoTree(tree.child(0))
                    val typeArgs = mutableListOf<PseudoTree>()
                    var typeArgsInferred = false
                    var args: List<PseudoTree>? = null
                    var argListStart = 1 // Index into children where arguments start.
                    // Special case `.` operator since its right operand is a symbol but
                    // should render as a bare name.
                    if (
                        tree.size == BINARY_OP_CALL_ARG_COUNT &&
                        callee isProbablyBuiltinFunNamed "."
                    ) {
                        val right = tree.child(2)
                        val rightSymbol = right.symbolContained
                        if (rightSymbol != null) {
                            args = listOf(
                                buildPseudoTree(tree.child(1)),
                                PseudoNameLeaf(right.pos, ParsedName(rightSymbol.text)),
                            )
                        }
                        argListStart = tree.size // all done
                    } else if (
                        callee isProbablyBuiltinFunNamed "new" &&
                        tree.size >= 2 && symbolTextFor(tree.child(1)) == null
                    ) {
                        // Reshuffle
                        //     new(TypeToCreate, ConstructorArg0)
                        // to
                        //     new TypeToCreate(ConstructorArg0)
                        val typeArg: Tree? = run {
                            val c1 = tree.child(1)
                            // `void` in type position indicates type needs to be inferred.
                            if (c1.valueContained == void) {
                                null
                            } else {
                                c1
                            }
                        }
                        if (typeArg != null) {
                            callee = PseudoCall(
                                listOfNotNull(callee, typeArg).spanningPosition(callee.pos),
                                callee,
                                emptyList(),
                                listOf(buildPseudoTree(typeArg)),
                            )
                        }
                        argListStart = 2
                    }
                    // Consume type arguments into
                    while (argListStart + 1 < tree.size) {
                        val childAtArgListStart = tree.child(argListStart)
                        if (childAtArgListStart.symbolContained != typeArgSymbol) {
                            break
                        }
                        typeArgs.add(buildPseudoTree(tree.child(argListStart + 1)))
                        argListStart += 2
                    }
                    // Show inferred type arguments if we don't have explicit ones and the details
                    // ask for them.
                    if (typeArgs.isEmpty() && detail.showInferredTypes) {
                        val calleeType = tree.childOrNull(0)?.typeInferences?.type
                        val variant = calleeType as? FunctionType
                            ?: tree.typeInferences?.variant as? FunctionType
                        val bindings = tree.typeInferences?.bindings2
                        if (variant != null && variant.typeFormals.isNotEmpty() && bindings != null) {
                            val inferredTypeArgs = variant.typeFormals.map {
                                bindings[it]
                            }
                            if (null !in inferredTypeArgs) {
                                inferredTypeArgs.mapTo(typeArgs) {
                                    PseudoType(callee.pos.rightEdge, it!!)
                                }
                                typeArgsInferred = true
                            }
                        }
                    }
                    if (args == null) {
                        args = (argListStart until tree.size).map {
                            buildPseudoTree(tree.child(it))
                        }
                    }
                    PseudoCall(
                        pos = pos,
                        callee = callee,
                        typeArgs = typeArgs.toList(),
                        args = args,
                        typeArgsInferred = typeArgsInferred,
                    )
                }
            }

            is DeclTree -> buildDecl(tree, DeclKind.Normal)
            is EscTree ->
                if (tree.size == 1) {
                    PseudoEsc(pos, buildPseudoTree(tree.child(0)))
                } else {
                    PseudoError(pos)
                }

            is FunTree -> {
                val parts = tree.parts ?: return PseudoError(pos)

                var name: PseudoTree? = null
                var lifespan: EnumRange<Stage>? = null
                var returns: Pair<PseudoTree, ReturnKind>? = null
                val typeFormals = parts.typeFormals.map { (edge, typeFormal) ->
                    if (typeFormal != null) {
                        PseudoTypeFormal(edge.target.pos, typeFormal)
                    } else {
                        PseudoError(edge.target.pos)
                    }
                }
                val singularFormals = parts.formals.map { buildDecl(it, DeclKind.Formal) }
                val formals = parts.restFormal?.let {
                    singularFormals + buildDecl(it.tree, DeclKind.Formal)
                } ?: singularFormals
                val superTypes = mutableListOf<PseudoTree>()
                val annotations = buildAnnotationList(
                    tree.pos,
                    parts.metadataSymbolMultimap,
                    filterBySymbol = { symbol ->
                        when (symbol) {
                            in typeMemberMetadataSymbols -> detail.showTypeMemberMetadata
                            returnedFromSymbol,
                            restFormalSymbol,
                            typeFormalSymbol,
                            -> false

                            else -> true
                        }
                    },
                    processOne = { symbol, valueEdge ->
                        when (symbol) {
                            stageRangeSymbol -> {
                                lifespan = null
                                val valueTree = valueEdge.target
                                lifespan = valueTree.valueContained(TStageRange)
                                if (lifespan == null) {
                                    PseudoNameLeaf(valueTree.pos.leftEdge, ParsedName(symbol.text)) to
                                        buildPseudoTree(valueTree)
                                } else {
                                    null
                                }
                            }

                            returnDeclSymbol -> {
                                val returnTree = valueEdge.target

                                returns = if (returnTree is DeclTree) {
                                    buildDecl(returnTree, DeclKind.Return) to ReturnKind.ReturnDecl
                                } else {
                                    PseudoError(returnTree.pos) to ReturnKind.Error
                                }
                                null
                            }

                            outTypeSymbol -> {
                                returns = buildPseudoTree(valueEdge.target) to ReturnKind.ReturnType
                                null
                            }

                            wordSymbol -> {
                                name = buildPseudoTree(valueEdge.target)
                                null
                            }

                            docStringSymbol -> buildDocStringAnnotation(detail, valueEdge)

                            superSymbol -> {
                                superTypes.add(buildPseudoTree(valueEdge.target))
                                null
                            }

                            else -> {
                                buildAnnotation(symbol, valueEdge)
                            }
                        }
                    },
                )
                val body = if (detail.elideFunctionBodies) {
                    PseudoElision(parts.body.pos)
                } else {
                    PseudoBlock.wrap(buildPseudoTree(parts.body))
                }
                PseudoFun(
                    pos = pos,
                    lifespan = lifespan,
                    annotations = annotations.toList(),
                    name = name,
                    typeFormals = typeFormals.toList(),
                    formals = formals.toList(),
                    returns = returns,
                    superTypes = superTypes.toList(),
                    body = body,
                )
            }

            is NameLeaf -> PseudoNameLeaf(pos, tree.content)
            is StayLeaf -> PseudoStayLeaf(pos)
            is ValueLeaf -> buildPseudoTree(tree.pos, tree.content)
        }

        return if (highlight) {
            PseudoHighlight(pseudoTree)
        } else {
            pseudoTree
        }
    }

    private fun buildPseudoTree(pos: Position, value: Value<*>): PseudoTree =
        when (val typeTag = value.typeTag) {
            is TClass -> {
                val record = typeTag.unpack(value)
                val pseudoRecord = buildList {
                    for ((property, value) in record.properties) {
                        val propertyDisplayName = (property as? ResolvedParsedName)?.baseName
                            ?: property
                        add(PseudoNameLeaf(pos.leftEdge, propertyDisplayName) to buildPseudoTree(pos, value))
                    }
                }
                PseudoClassValue(pos, typeTag, pseudoRecord)
            }
            TType -> PseudoType(pos, TType.unpack(value).type)
            else -> PseudoValueLeaf(pos, value)
        }

    private fun buildDecl(tree: DeclTree, kind: DeclKind): PseudoDecl {
        val pos = tree.pos
        // Handle multi-assignments.
        val dp = tree.partsIgnoringName
        val name = if (tree.size > 0 && dp != null) {
            buildPseudoTree(tree.child(0))
        } else {
            PseudoError(pos)
        }
        var type: PseudoTree? = null
        var afterEquals: PseudoTree? = null
        var word: PseudoTree? = null
        var constness = Constness.Const
        var restFormal = false

        // When printing a declaration that is a formal function parameter, the default expression
        // goes after an equals sign.  Otherwise, the init expression goes there.
        val metadata = dp?.metadataSymbolMultimap ?: emptyMap()
        val annotations = buildAnnotationList(
            tree.pos,
            metadata,
            filterBySymbol = { symbol ->
                when (symbol) {
                    in typeMemberMetadataSymbols -> detail.showTypeMemberMetadata
                    qNameSymbol -> detail.showQNames
                    ssaSymbol, failSymbol, parameterNameSymbolsListSymbol -> false
                    else -> true
                }
            },
            processOne = { symbol, valueEdge ->
                when (symbol) {
                    typeSymbol -> {
                        type = buildPseudoTree(valueEdge.target)
                        null
                    }

                    kind.afterEqualsSymbol -> {
                        afterEquals = buildPseudoTree(valueEdge.target)
                        null
                    }

                    wordSymbol -> {
                        word = buildPseudoTree(valueEdge.target)
                        null
                    }

                    varSymbol -> {
                        constness = Constness.NotConst
                        null
                    }

                    restFormalSymbol -> {
                        restFormal = true
                        null
                    }

                    docStringSymbol -> buildDocStringAnnotation(detail, valueEdge)

                    else -> buildAnnotation(symbol, valueEdge)
                }
            },
        )

        var typeIsInferred = false
        if (type == null && detail.showInferredTypes) {
            val inferredType = tree.parts?.name?.typeInferences?.type
            if (inferredType != null) {
                typeIsInferred = true
                type = PseudoType(name.pos.rightEdge, inferredType)
            }
        }

        // if it is pseudo decl de-list the type
        if (restFormal) {
            val proposedNewType = type?.let {
                if (it is PseudoType) {
                    // TODO: Do we need to limit this to List/Listed
                    val underlyingType =
                        (it.type as? NominalType)?.bindings?.firstOrNull() as? StaticType
                    underlyingType?.let { itemType ->
                        PseudoType(it.pos, itemType)
                    }
                } else {
                    it
                }
            }
            proposedNewType?.let {
                type = it
            }
        }

        return PseudoDecl(
            pos,
            constness = constness,
            declared = name,
            word = word,
            type = type,
            typeIsInferred = typeIsInferred,
            afterEquals = afterEquals,
            annotations = annotations,
            restFormal = restFormal,
            isStandalone = isStandaloneDecl(metadata),
        )
    }

    private fun buildDocStringAnnotation(detail: PseudoCodeDetail, valueEdge: TEdge) =
        when (detail.docStringDetail) {
            NoneShortOrLong.Long -> buildAnnotation(docStringSymbol, valueEdge)
            NoneShortOrLong.Short -> buildAnnotation(docStringSymbol, valueEdge, elideValue = true)
            NoneShortOrLong.None -> null
        }

    /**
     * @see PseudoDecl.annotations
     * @see PseudoFun.annotations
     */
    private fun buildAnnotation(
        symbol: Symbol,
        valueEdge: TEdge,
        elideValue: Boolean = false,
    ): Pair<PseudoNameLeaf, PseudoTree> {
        val valueTree = valueEdge.target
        return PseudoNameLeaf(valueTree.pos.leftEdge, ParsedName(symbol.text)) to
            if (elideValue) {
                PseudoElision(valueTree.pos)
            } else {
                buildPseudoTree(valueTree)
            }
    }

    private fun buildAnnotationList(
        pos: Position,
        metadata: MetadataMultimap,
        filterBySymbol: (Symbol) -> Boolean,
        processOne: (Symbol, TEdge) -> Pair<PseudoNameLeaf, PseudoTree>?,
    ): List<Pair<PseudoNameLeaf, PseudoTree>> = buildList {
        var hasHiddenAnnotations = false
        val verboseMetadata = detail.verboseMetadata
        metadata.forEach { (symbol, valueEdges) ->
            if (verboseMetadata == NoneShortOrLong.Long || filterBySymbol(symbol)) {
                valueEdges.mapNotNullTo(this) { valueEdge ->
                    processOne(symbol, valueEdge)
                }
            } else {
                hasHiddenAnnotations = true
            }
        }
        if (hasHiddenAnnotations && detail.verboseMetadata != NoneShortOrLong.None) {
            val leftPos = pos.leftEdge
            add(PseudoNameLeaf(leftPos, BuiltinName("_")) to PseudoValueLeaf(leftPos, void))
        }
    }

    private fun buildReference(reference: BlockChildReference, block: BlockTree): PseudoTree {
        val edge = block.dereference(reference)
            ?: return PseudoError(reference.pos)
        return buildPseudoTree(edge.target)
    }

    private fun controlFlowToPseudoTree(
        controlFlow: ControlFlow,
        block: BlockTree,
    ): PseudoTree = when (controlFlow) {
        is ControlFlow.Jump -> {
            val jumpName = when (val target = controlFlow.target) {
                DefaultJumpSpecifier -> null
                is NamedJumpSpecifier -> target.label
                is UnresolvedJumpSpecifier -> ParsedName(target.symbol.text)
            }
            PseudoJump(
                controlFlow.pos,
                when (controlFlow) {
                    is ControlFlow.Break -> BreakOrContinue.Break
                    is ControlFlow.Continue -> BreakOrContinue.Continue
                },
                jumpName,
            )
        }
        is ControlFlow.StmtBlock -> PseudoBlock(
            controlFlow.pos,
            null,
            controlFlow.stmts.map { controlFlowToPseudoTree(it, block) },
        )
        is ControlFlow.If -> PseudoIf(
            controlFlow.pos,
            buildReference(controlFlow.condition, block),
            PseudoBlock.wrap(
                controlFlowToPseudoTree(controlFlow.thenClause, block),
                controlFlow.thenClause.pos,
            ),
            PseudoBlock.wrap(
                controlFlowToPseudoTree(controlFlow.elseClause, block),
                controlFlow.elseClause.pos,
            ),
        )
        is ControlFlow.Labeled -> {
            var body = PseudoBlock.wrap(
                controlFlowToPseudoTree(controlFlow.stmts, block),
            )
            body = PseudoBlock(
                pos = body.pos,
                blockCaller = PseudoNameLeaf(body.pos.leftEdge, doBuiltinName),
                elements = body.elements,
            )
            val continueLabel = controlFlow.continueLabel
            if (continueLabel != null) {
                // Show for debugging.
                // Note: `continue`s will eventually be rewritten to `break`s.
                body = PseudoBlock(
                    pos = body.pos,
                    blockCaller = body.blockCaller,
                    elements = buildList {
                        add(
                            PseudoComment(
                                body.pos.leftEdge,
                                "continue $continueLabel -> break ${controlFlow.breakLabel}",
                            ),
                        )
                        addAll(body.elements)
                    },
                )
            }
            PseudoLabeled(
                controlFlow.pos,
                PseudoNameLeaf(controlFlow.pos.leftEdge, controlFlow.breakLabel),
                body,
            )
        }
        is ControlFlow.Loop -> {
            var loop: PseudoTree = PseudoLoop(
                controlFlow.pos,
                checkPosition = controlFlow.checkPosition,
                condition = buildReference(controlFlow.condition, block),
                body = PseudoBlock.wrap(
                    controlFlowToPseudoTree(controlFlow.body, block),
                    controlFlow.body.pos,
                ),
                increment = if (controlFlow.increment.isEmptyBlock()) {
                    null
                } else {
                    var increment: ControlFlow = controlFlow.increment
                    if (increment is ControlFlow.StmtBlock && increment.stmts.size == 1) {
                        increment = increment.stmts.first()
                    }
                    controlFlowToPseudoTree(increment, block)
                },
            )
            val label = controlFlow.label
            if (label != null) {
                loop = PseudoLabeled(controlFlow.pos, PseudoNameLeaf(controlFlow.pos.leftEdge, label), loop)
            }
            loop
        }
        is ControlFlow.OrElse -> {
            val orClause = controlFlowToPseudoTree(controlFlow.orClause.stmts, block)
            val elseClause = controlFlowToPseudoTree(controlFlow.elseClause, block)
            PseudoOrElse(
                controlFlow.pos,
                PseudoNameLeaf(controlFlow.pos.leftEdge, controlFlow.orClause.breakLabel),
                orClause,
                elseClause,
            )
        }
        is ControlFlow.Stmt -> buildReference(controlFlow.ref, block)
    }
}

private fun maybeParenthesize(ot: OpTree, grandParentView: OperatorStackElementSubView?) {
    when (ot) {
        is Tok, is OpLeaf -> {}
        is OpInner -> {
            val inner = PseudoHighlight.unwrap(ot) ?: ot

            val children = inner.children
            val outerView = OperatorStackElementSubView(inner)
            for (i in children.indices) {
                outerView.childCount = i // Simulate the stack when we've yet to add children[i]
                val child = children[i]
                maybeParenthesize(child, outerView)
                if (
                    child !is Tok && !canNest(grandParentView, outerView, child) &&
                    child.operator != Operator.CallJoin
                ) {
                    children[i] = wrap(child)
                }
            }
            // TODO: maybe parenthesize left division operand if its last token is a regex
            // preceder as defined in Lexer.
        }
    }
}

/** This allows us to simulate parse tree building since [canNest] considers partial trees. */
private class OperatorStackElementSubView(val e: OperatorStackElement) : OperatorStackElement by e {
    override var childCount: Int = e.childCount
    override val eventualChildCount get() = childCount

    override fun toString() = "$e[:$childCount]"
}

/**
 * Returns a tree that allows [t] to nest in its parent.
 * Usually this means parentheses, but sometimes we use a different strategy:
 *
 * - when [t] is a block statement because `(` followed by `{` starts an object property bag, or
 * - when [t] is a comma operation because commas inside parentheses might be a tuple.
 */
private fun wrap(t: OpTree): OpTree {
    val pos = t.pos
    return when (t.operator) {
        Operator.CurlyGroup -> OpInner(
            pos,
            Operator.Curly,
            listOf(Tok(pos.leftEdge, OutToks.doWord), t),
        )
        Operator.Comma -> OpInner(
            pos,
            Operator.Curly,
            listOf(
                Tok(pos.leftEdge, OutToks.doWord),
                Tok(pos.leftEdge, OutToks.leftCurly),
                t,
                Tok(pos.rightEdge, OutToks.rightCurly),
            ),
        )
        else -> OpInner(
            pos,
            Operator.ParenGroup,
            listOf(
                Tok(pos.leftEdge, OutToks.leftParen),
                t,
                Tok(pos.rightEdge, OutToks.rightParen),
            ),
        )
    }
}

/** Remove unnecessary `{` and `}` from the root element. */
private fun maybeStripCurliesFromRoot(opTree: OpTree) {
    if (opTree is OpInner && opTree.operator == Operator.CurlyGroup) {
        val children = opTree.children
        val lastIndex = children.lastIndex
        if (lastIndex > 0) {
            val first = children[0]
            val last = children[lastIndex]
            if (
                first is Tok && last is Tok &&
                first.outputToken == OutToks.leftCurly && last.outputToken == OutToks.rightCurly
            ) {
                children.removeAt(lastIndex)
                children.removeAt(0)
            }
        }
    }
}

fun symbolTextFor(tree: Tree): String? = tree.symbolContained?.text

internal sealed class OpTree(
    override val pos: Position,
) : Positioned, TokenSerializable, OperatorStackElement

private class Tok(
    pos: Position,
    val outputToken: OutputToken,
) : OpTree(pos) {
    override val operator: Operator get() = Operator.Leaf
    override val childCount: Int get() = 0
    override val tokenText: String get() = when (outputToken) {
        // These tokens are generated for inferred type parameters, but we
        // need canNest to see them as angle brackets so that we don't
        // over-parenthesize type expressions that appear in angle brackets.
        zLeftAngleToken -> OutToks.leftAngle.text
        zRightAngleToken -> OutToks.rightAngle.text
        else -> outputToken.text
    }
    override fun child(i: Int): OperatorStackElement = throw NoSuchElementException()
    override val tokenType: TokenType? get() = null

    override fun toString() = "$outputToken"

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.position(pos, LeftOrRight.Left)
        tokenSink.emit(outputToken)
        tokenSink.position(pos, LeftOrRight.Right)
    }
}
internal class OpLeaf(
    pos: Position,
    val outputTokens: List<OutputToken>,
) : OpTree(pos), InnerOperatorStackElement {
    override val operator get() = Operator.Leaf
    override val childCount: Int get() = outputTokens.size
    override fun child(i: Int): OperatorStackElement = Tok(pos, outputTokens[i])

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.position(pos, LeftOrRight.Left)
        for (outputToken in outputTokens) {
            tokenSink.emit(outputToken)
        }
        tokenSink.position(pos, LeftOrRight.Right)
    }

    override fun toString() = "(OpLeaf $outputTokens)"
}
internal class OpInner(
    pos: Position,
    override val operator: Operator,
    children: List<OpTree>,
) : OpTree(pos), InnerOperatorStackElement {
    val children = children.toMutableList()

    override val childCount: Int get() = children.size
    override fun child(i: Int): OperatorStackElement = children[i]

    override fun toString() = "(OpInner $operator $children)"

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.position(pos, LeftOrRight.Left)
        for (child in children) {
            child.renderTo(tokenSink)
        }
        tokenSink.position(pos, LeftOrRight.Right)
    }
}

internal sealed class PseudoTree : Positioned {
    open fun isEmptyBlock() = false

    abstract fun reduce(): OpTree

    /** Inside a `{...}` block, whether this should be followed by a `;` */
    open val semiFollowsInStatementContext: SemiFollows get() = SemiFollows.UnlessLastInSection

    companion object {
        fun parenGroupOf(tree: OpTree) =
            bracketGroup(Operator.ParenGroup, OutToks.leftParen, tree, OutToks.rightParen)
        fun squareGroupOf(tree: OpTree) =
            bracketGroup(Operator.SquareGroup, OutToks.leftSquare, tree, OutToks.rightSquare)
        fun bracketGroup(
            operator: Operator,
            left: OutputToken,
            tree: OpTree,
            right: OutputToken,
        ): OpInner {
            val pos = tree.pos
            return OpInner(
                pos,
                operator,
                listOf(
                    Tok(pos.leftEdge, left),
                    tree,
                    Tok(pos.rightEdge, right),
                ),
            )
        }
    }
}

private val specialBuiltinNames = setOf(
    doBuiltinName,
)

internal class PseudoNameLeaf(
    override val pos: Position,
    val name: Name,
) : PseudoTree() {
    private fun reduceToToken() = if (name is BuiltinName && name in specialBuiltinNames) {
        OutputToken(name.builtinKey, OutputTokenType.Word)
    } else {
        name.toToken(inOperatorPosition = false)
    }

    override fun reduce(): OpTree {
        if (name is ExportedName) {
            val moduleLocation = name.origin.loc
            val exporterTokenList = moduleLocation.renderToTokenList()
            return OpInner(
                pos,
                Operator.Dot,
                listOf(
                    OpLeaf(pos.leftEdge, exporterTokenList.toList()),
                    OpLeaf(pos.leftEdge, listOf(OutToks.dot)),
                    PseudoNameLeaf(pos, name.baseName).reduce(),
                ),
            )
        }
        return OpLeaf(pos, listOf(reduceToToken()))
    }

    override fun toString() = "(PseudoNameLeaf $name)"
}

internal class PseudoStayLeaf(
    override val pos: Position,
) : PseudoTree() {
    override fun reduce(): OpLeaf {
        return OpLeaf(pos, listOf(stayCommentToken))
    }

    override fun toString() = "(PseudoStayLeaf)"
}

internal class PseudoElision(
    override val pos: Position,
) : PseudoTree() {
    override fun reduce(): OpTree = Tok(pos, OutToks.ellipses)
}

private val stayCommentToken = OutputToken.makeSlashStarComment("stay")

/** An interstitial comment in a block */
internal class PseudoComment(
    override val pos: Position,
    val commentText: String,
) : PseudoTree() {
    override fun reduce(): OpTree = Tok(pos, OutputToken.makeSlashStarComment(commentText))
}

internal class PseudoHighlight(
    val child: PseudoTree,
) : PseudoTree() {
    override val pos: Position get() = child.pos
    override fun reduce(): OpTree =
        when (val childReduced = child.reduce()) {
            is OpInner -> OpInner(
                pos,
                Operator.Leaf,
                listOf(
                    Tok(pos.leftEdge, pointAtLeft),
                    childReduced,
                    Tok(pos.rightEdge, pointAtRight),
                ),
            )
            is OpLeaf -> OpLeaf(
                pos,
                buildList {
                    add(pointAtLeft)
                    addAll(childReduced.outputTokens)
                    add(pointAtRight)
                },
            )
            is Tok -> OpLeaf(pos, listOf(pointAtLeft, childReduced.outputToken, pointAtRight))
        }

    companion object {
        val pointAtLeft = OutputToken("/*->*/", OutputTokenType.Comment)
        val pointAtRight = OutputToken("/*<-*/", OutputTokenType.Comment)

        private const val OP_TREE_CHILD_COUNT = 3

        /** Undoes reduction for non-leaf trees */
        fun unwrap(opTree: OpTree): OpInner? {
            if (opTree is OpInner && opTree.operator == Operator.Leaf &&
                opTree.children.size == OP_TREE_CHILD_COUNT
            ) {
                val (left, mid, right) = opTree.children
                if (
                    mid is OpInner &&
                    left is Tok && left.outputToken == pointAtLeft &&
                    right is Tok && right.outputToken == pointAtRight
                ) {
                    return mid
                }
            }
            return null
        }
    }
}

internal class PseudoValueLeaf(
    override val pos: Position,
    val value: Value<*>,
) : PseudoTree() {
    override fun isEmptyBlock(): Boolean = value == void

    override fun reduce(): OpTree = when (value.typeTag) {
        TInt -> OpLeaf(
            pos,
            listOf(OutputToken("${ TInt.unpack(value) }", OutputTokenType.NumericValue)),
        )
        TFloat64 -> OpLeaf(
            pos,
            listOf(OutputToken("${ TFloat64.unpack(value) }", OutputTokenType.NumericValue)),
        )
        TString -> {
            val stringValue = TString.unpack(value)
            val (tag, stringTokenText) = maybeTagStringValue(stringValue)
            val tokens = listOfNotNull(
                tag?.let {
                    OutputToken(it, OutputTokenType.Word)
                },
                OutputToken(
                    stringTokenText,
                    OutputTokenType.QuotedValue,
                ),
            )
            OpLeaf(pos, tokens)
        }
        TNull -> OpLeaf(pos, listOf(OutToks.nullWord))
        TBoolean -> OpLeaf(
            pos,
            listOf(if (TBoolean.unpack(value)) OutToks.trueWord else OutToks.falseWord),
        )
        TSymbol -> {
            val symbolText = TSymbol.unpack(value).text
            val t = ParsedName(symbolText).toToken(inOperatorPosition = false)
            OpLeaf(
                pos,
                listOf(
                    OutputToken(
                        "\\${t.text}",
                        t.type,
                    ),
                ),
            )
        }
        TType ->
            // Do no route complex type expressions to the "parenthesize if more than 1 token"
            // branch below.
            PseudoType(pos, TType.unpack(value).type).reduce()
        else -> reduceViaTokenList()
    }

    private fun reduceViaTokenList(): OpTree {
        val leaf = OpLeaf(pos, value.renderToTokenList())
        return if (leaf.outputTokens.size == 1) {
            leaf
        } else {
            parenGroupOf(leaf)
        }
    }

    override fun toString() = "(PseudoValueLeaf $value)"
}

internal class PseudoClassValue(
    override val pos: Position,
    val tClass: TClass,
    val properties: List<Pair<PseudoNameLeaf, PseudoTree>>,
) : PseudoTree() {
    companion object {
        /**
         * Try to keep short class values short.
         * If a class value, ignoring its `class: TypeName`, is less than that threshold,
         * then we use inline brackets which TemperFormattingHints knows about.
         */
        internal const val SHORT_OBJECT_LITERAL_MAX_CHAR_COUNT = 40
    }

    override fun reduce(): OpTree {
        val lPos = pos.leftEdge

        val classKeyword = Tok(lPos, OutToks.classWord)
        val className = PseudoNameLeaf(lPos, tClass.typeShape.name).reduceAsType()

        val betweenTwoCommas = buildList {
            // class: SomeType
            add(OpInner(lPos, Operator.LowColon, listOf(classKeyword, Tok(lPos, OutToks.colon), className)))

            for ((propertyTree, valueTree) in properties) {
                val prop = propertyTree.reduce()
                val value = valueTree.reduce()
                add(OpInner(lPos, Operator.LowColon, listOf(prop, Tok(prop.pos.rightEdge, OutToks.colon), value)))
            }
        }

        fun makeTree(
            left: OutputToken,
            comma: OutputToken,
            right: OutputToken,
        ) = OpInner(
            pos,
            Operator.CurlyGroup,
            buildList {
                add(Tok(pos.leftEdge, left))
                for ((i, ot) in betweenTwoCommas.withIndex()) {
                    if (i != 0) {
                        add(Tok(ot.pos.leftEdge, comma))
                    }
                    add(ot)
                }
                add(Tok(pos.rightEdge, right))
            },
        )

        val shortTree = makeTree(
            TemperFormattingHints.inlineLCurly,
            TemperFormattingHints.inlineComma,
            TemperFormattingHints.inlineRCurly,
        )

        // Add up the length of tokens and probably spaces between them, but
        // if the probably sum goes over the threshold, stops adding.
        fun totalLengthOrThreshold(before: Int, ot: OpTree, stopAt: Int): Int {
            var n = before
            when (ot) {
                is OpInner ->
                    for (c in ot.children) {
                        if (n > stopAt) { break }
                        n = totalLengthOrThreshold(before = n, ot = c, stopAt = stopAt)
                    }
                is OpLeaf ->
                    for (t in ot.outputTokens) {
                        if (n > stopAt) { break }
                        n += t.text.length
                    }
                is Tok -> n += ot.outputToken.text.length
            }
            return n
        }
        val n = totalLengthOrThreshold(0, shortTree, SHORT_OBJECT_LITERAL_MAX_CHAR_COUNT)
        return if (n > SHORT_OBJECT_LITERAL_MAX_CHAR_COUNT) {
            makeTree(OutToks.leftCurly, OutToks.comma, OutToks.rightCurly)
        } else {
            shortTree
        }
    }
}

internal class PseudoCall(
    override val pos: Position,
    val callee: PseudoTree,
    val typeArgs: List<PseudoTree>,
    val args: List<PseudoTree>,
    val typeArgsInferred: Boolean = false,
) : PseudoTree() {
    override fun reduce(): OpTree {
        // See if we can represent as an infix or binary operator.  If not, use normal parenthetical
        // call operator.
        val possibleOperatorName = when (callee) {
            is PseudoNameLeaf -> callee.name
            is PseudoValueLeaf -> {
                val value = callee.value
                when (val f = TFunction.unpackOrNull(value)) {
                    is NamedBuiltinFun -> BuiltinName(f.name)
                    is CoverFunction ->
                        f.covered.allMapToSameElseNull {
                            if (it is NamedBuiltinFun) BuiltinName(it.name) else null
                        }
                    else -> null
                }
            }
            else -> null
        }

        // Some builtin functions expect types as arguments.  Don't render `type(...)` around those.
        val expectedTypeArgRange = (possibleOperatorName as? BuiltinName)?.let {
            expectedTypeArgsForBuiltin[it]
        } ?: IntRange.EMPTY

        fun reduceArg(i: Int) = if (i in expectedTypeArgRange) {
            args[i].reduceAsType()
        } else {
            args[i].reduce()
        }

        if (possibleOperatorName != null) {
            val nameOutputToken = possibleOperatorName.toToken(inOperatorPosition = true)
            val lexTokenType = when (nameOutputToken.type) {
                OutputTokenType.Name, OutputTokenType.Word -> TokenType.Word
                OutputTokenType.Punctuation -> TokenType.Punctuation
                else -> null
            }
            if (lexTokenType != null) {
                val builtinKey = possibleOperatorName.builtinKey ?: ""
                // TODO: operator closers?
                when (args.size) {
                    2 -> {
                        val operators = when (builtinKey) {
                            // while is an infix operator in do...while... but that's not how
                            // it's used here.
                            // "<>", angle bracket is handled below
                            NameConstants.While, NameConstants.Angle -> emptyList()
                            // These show up here as pseudo method.
                            // The operators are special only in declaration.
                            Operator.Is.text, Operator.As.text -> emptyList()
                            else -> {
                                Operator.matching(
                                    builtinKey,
                                    lexTokenType,
                                    OperatorType.Infix,
                                )
                            }
                        }
                        if (operators.isNotEmpty()) {
                            val operator = when (val operator0 = operators[0]) {
                                Operator.Angle -> Operator.Lt // Angle maps to "<>" not "<"
                                else -> operator0
                            }
                            return OpInner(
                                pos,
                                operator,
                                listOf(
                                    reduceArg(0),
                                    Tok(args[0].pos.rightEdge, nameOutputToken),
                                    reduceArg(1),
                                ),
                            )
                        }
                    }
                    1 -> {
                        val prefixOps =
                            Operator.matching(builtinKey, lexTokenType, OperatorType.Prefix)
                        if (prefixOps.isNotEmpty()) {
                            return OpInner(
                                pos,
                                prefixOps[0],
                                listOf(
                                    Tok(
                                        pos.leftEdge,
                                        OutputToken(
                                            nameOutputToken.text, nameOutputToken.type,
                                            TokenAssociation.Prefix,
                                        ),
                                    ),
                                    reduceArg(0),
                                ),
                            )
                        }
                        val postfixOps =
                            Operator.matching(builtinKey, lexTokenType, OperatorType.Postfix)
                        if (postfixOps.isNotEmpty()) {
                            return OpInner(
                                pos,
                                postfixOps[0],
                                listOf(
                                    reduceArg(0),
                                    Tok(
                                        pos.rightEdge,
                                        OutputToken(
                                            nameOutputToken.text, nameOutputToken.type,
                                            TokenAssociation.Postfix,
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }
                if (args.size >= 2) {
                    val operators =
                        Operator.matching(builtinKey, lexTokenType, OperatorType.Separator)
                    if (operators.isNotEmpty()) {
                        return OpInner(
                            pos,
                            operators[0],
                            args.mapInterleaving(
                                { it.reduce() },
                                { a, _ -> Tok(a.pos.rightEdge, nameOutputToken) },
                            ).toList(),
                        )
                    }
                }
            }
        }

        val beforeBracket: OpTree
        val betweenBrackets: IntRange
        val leftBracket: OutputToken
        val rightBracket: OutputToken
        val bracketOp: Operator

        if (
            typeArgs.isEmpty() &&
            possibleOperatorName?.builtinKey == NameConstants.Angle && args.size >= 2
        ) {
            // Special case <...> for type expressions where ambiguous type formals/actuals have
            // been resolved to type actuals
            beforeBracket = reduceArg(0)
            betweenBrackets = 1..args.lastIndex
            leftBracket = OutToks.leftAngle
            rightBracket = OutToks.rightAngle
            bracketOp = Operator.Angle
        } else {
            beforeBracket = if (typeArgs.isEmpty()) {
                callee.reduce()
            } else {
                val combinedCalleePos =
                    (listOf(callee) + typeArgs).spanningPosition(callee.pos)
                val typeArgsCommaList = mutableListOf<OpTree>()
                typeArgs.forEachIndexed { index, typeArg ->
                    if (index != 0) {
                        typeArgsCommaList.add(Tok(typeArg.pos.leftEdge, OutToks.comma))
                    }
                    typeArgsCommaList.add(typeArg.reduceAsType())
                }
                val typeArgsReduced = OpInner(
                    typeArgs.spanningPosition(typeArgs[0].pos),
                    Operator.Comma,
                    typeArgsCommaList.toList(),
                )
                val leftAngleTok =
                    if (typeArgsInferred) { zLeftAngleToken } else { OutToks.leftAngle }
                val rightAngleTok =
                    if (typeArgsInferred) { zRightAngleToken } else { OutToks.rightAngle }
                OpInner(
                    combinedCalleePos,
                    Operator.Angle,
                    listOf(
                        callee.reduce(),
                        Tok(callee.pos.rightEdge, leftAngleTok),
                        typeArgsReduced,
                        Tok(combinedCalleePos.rightEdge, rightAngleTok),
                    ),
                )
            }
            betweenBrackets = args.indices
            leftBracket = OutToks.leftParen
            rightBracket = OutToks.rightParen
            bracketOp = Operator.Paren
        }
        val leftBracketPos = beforeBracket.pos.rightEdge
        val rightBracketPos = pos.rightEdge

        return OpInner(
            pos,
            bracketOp,
            listOf(
                beforeBracket,
                Tok(leftBracketPos, leftBracket),
                OpInner(
                    betweenBrackets.map { args[it] }.spanningPosition(pos),
                    Operator.Comma,
                    betweenBrackets.mapInterleaving(
                        { i -> reduceArg(i) },
                        { a, _ -> Tok(args[a].pos.rightEdge, OutToks.comma) },
                    ).toList(),
                ),
                Tok(rightBracketPos, rightBracket),
            ),
        )
    }

    override fun toString() = "(PseudoCall $callee $args)"
}
internal class PseudoDecl(
    override val pos: Position,
    val constness: Constness,
    val declared: PseudoTree?,
    val word: PseudoTree?,
    val type: PseudoTree?,
    val typeIsInferred: Boolean,
    val afterEquals: PseudoTree?,
    val annotations: List<Pair<PseudoNameLeaf, PseudoTree>>,
    val restFormal: Boolean,
    /** True if it should not be folded into a comma-separated declaration list */
    val isStandalone: Boolean,
) : PseudoTree() {
    override fun toString() = "(PseudoDecl $declared $word $type $afterEquals)"

    override val semiFollowsInStatementContext get() = SemiFollows.Always

    override fun reduce(): OpTree =
        reduce(emitDeclKeyword = TriState.TRUE)

    fun reduce(emitDeclKeyword: TriState): OpTree {
        val symbolToken = if (word == null) {
            null
        } else if (word is PseudoValueLeaf && word.value.typeTag == TSymbol) {
            OutputToken.makeSlashStarComment("aka ${TSymbol.unpack(word.value).text}")
        } else {
            OutputToken.makeSlashStarComment("aka ???")
        }
        val keyword = when {
            // Assume caller knows what they're doing
            emitDeclKeyword == TriState.FALSE -> null
            // The default is `let`, so when `var` is appropriate, say it.
            constness == Constness.NotConst -> OutToks.varWord
            // Don't prepend function parameters with let
            emitDeclKeyword == TriState.OTHER -> null
            else -> OutToks.letWord
        }
        val varArg = if (restFormal) {
            OutToks.prefixEllipses
        } else {
            null
        }

        val beforeList = listOfNotNull(keyword, varArg)
        var opTree: OpTree =
            if (declared is PseudoNameLeaf) {
                declared.reduce().surround(
                    declared.pos,
                    before = beforeList,
                    after = listOfNotNull(symbolToken),
                )
            } else {
                val declReduced = declared?.reduce()
                    ?: OpLeaf(pos.leftEdge, listOf(OutToks.inconceivableWord))
                if (keyword != null) {
                    val letPos = declReduced.pos.leftEdge
                    OpInner(
                        declared?.pos ?: pos.leftEdge,
                        Operator.Paren,
                        listOf(
                            OpLeaf(letPos, beforeList),
                            Tok(letPos, OutToks.leftSquare),
                            declReduced,
                            Tok(declReduced.pos.rightEdge, OutToks.rightSquare),
                        ),
                    )
                } else {
                    squareGroupOf(declReduced)
                }
            }

        if (type != null) {
            opTree = OpInner(
                listOf(opTree, type).spanningPosition(pos),
                Operator.HighColon,
                listOf(
                    opTree,
                    Tok(
                        opTree.pos.rightEdge,
                        if (typeIsInferred) { zColon } else { OutToks.colon },
                    ),
                    type.reduceAsType(),
                ),
            )
        }
        if (afterEquals != null) {
            opTree = OpInner(
                listOf(opTree, afterEquals).spanningPosition(pos),
                Operator.Eq,
                listOf(
                    opTree,
                    Tok(opTree.pos.rightEdge, OutToks.eq),
                    afterEquals.reduce(),
                ),
            )
        }

        opTree = wrapInAnnotations(opTree, annotations)
        return opTree
    }
}
internal class PseudoType(override val pos: Position, val type: TypeActual) : PseudoTree() {
    override fun toString() = "(PseudoType $type)"

    override fun reduce(): OpTree = reduce(inTypeContext = false)

    fun reduce(inTypeContext: Boolean): OpTree {
        val typeTree = reduceTypeActual(pos, type)
        return if (inTypeContext) {
            typeTree
        } else {
            OpInner(
                pos,
                Operator.Paren,
                listOf(
                    Tok(pos.leftEdge, OutToks.typeWord),
                    Tok(pos.leftEdge, OutToks.leftParen),
                    typeTree,
                    Tok(pos.rightEdge, OutToks.rightParen),
                ),
            )
        }
    }

    companion object {
        internal fun reduceTypeActual(pos: Position, t: TypeActual): OpTree = when (t) {
            Wildcard -> Tok(pos, OutToks.prefixStar)
            TopType -> Tok(pos, OutToks.topWord)
            BubbleType -> Tok(pos, OutToks.bubbleWord)
            InvalidType -> Tok(pos, OutToks.invalidWord)
            is NominalType -> {
                val definition = t.definition
                val typeName =
                    if (definition is TypeShape && WellKnownTypes.isWellKnown(definition)) {
                        Tok(pos, OutputToken(definition.word!!.text, OutputTokenType.Word))
                    } else {
                        Tok(pos, definition.name.toToken(inOperatorPosition = false))
                    }
                if (t.bindings.isEmpty()) {
                    typeName
                } else {
                    val children = mutableListOf<OpTree>()
                    children.add(typeName)
                    children.add(Tok(pos, OutToks.leftAngle))
                    t.bindings.mapOpTreeJoiningTo(children, OutToks.comma) {
                        reduceTypeActual(pos, it)
                    }
                    children.add(Tok(pos, OutToks.rightAngle))
                    OpInner(pos, Operator.Angle, children.toList())
                }
            }
            is FunctionType -> {
                var reduced: OpTree = Tok(pos, OutToks.fnWord)
                // Generic parameters between angle brackets
                if (t.typeFormals.isNotEmpty()) {
                    val angleChildren = mutableListOf(reduced, Tok(pos, OutToks.leftAngle))
                    t.typeFormals.mapOpTreeJoiningTo(angleChildren, OutToks.comma) {
                        PseudoTypeFormal(pos, it).reduce()
                    }
                    angleChildren.add(Tok(pos, OutToks.rightAngle))
                    reduced = OpInner(pos, Operator.Angle, angleChildren.toList())
                }
                // Input types between parentheses
                val parenChildren = mutableListOf(reduced, Tok(pos, OutToks.leftParen))
                t.valueFormals.mapOpTreeJoiningTo(parenChildren, OutToks.comma) {
                    reduceTypeActual(pos, it.staticType)
                }
                if (t.restValuesFormal != null) {
                    if (parenChildren.isNotEmpty()) {
                        parenChildren.add(Tok(pos, OutToks.comma))
                    }
                    parenChildren.add(
                        OpInner(
                            pos,
                            Operator.Ellipsis,
                            listOf(
                                Tok(pos, OutToks.prefixEllipses),
                                reduceTypeActual(pos, t.restValuesFormal),
                            ),
                        ),
                    )
                }
                parenChildren.add(Tok(pos, OutToks.rightParen))
                reduced = OpInner(pos, Operator.Paren, parenChildren.toList())
                // Return type
                reduced = OpInner(
                    pos,
                    Operator.HighColon,
                    listOf(
                        reduced,
                        Tok(pos, OutToks.colon),
                        reduceTypeActual(pos, t.returnType),
                    ),
                )
                reduced
            }
            is OrType -> if (t.members.isEmpty()) {
                Tok(pos, OutToks.neverWord)
            } else {
                var hasNull = false
                val members = t.members.filter { t ->
                    val isNullType = t.isNullType
                    if (isNullType) {
                        hasNull = true
                    }
                    !isNullType
                }
                val op = when (members.size) {
                    0 -> return Tok(pos, OutToks.nullTypeWord)
                    1 -> reduceTypeActual(pos, members[0])
                    else -> OpInner(
                        pos,
                        Operator.Bar,
                        t.members.mapOpTreeJoining(OutToks.bar) { reduceTypeActual(pos, it) },
                    )
                }
                if (hasNull) {
                    OpInner(
                        pos,
                        Operator.PostQuest,
                        listOf(op, Tok(pos.rightEdge, OutToks.postfixQMark)),
                    )
                } else {
                    op
                }
            }
            is AndType -> OpInner(
                pos,
                Operator.Amp,
                t.members.mapOpTreeJoining(OutToks.amp) { reduceTypeActual(pos, it) },
            )
            is InfiniBinding ->
                // TODO maybe scan first so we can refer back to rendered chunk?
                Tok(pos, OutputToken("∞", OutputTokenType.Punctuation))
        }
    }
}
internal class PseudoTypeFormal(
    override val pos: Position,
    val typeFormal: TypeFormal,
) : PseudoTree() {
    override fun reduce(): OpTree {
        var reduced: OpTree = OpLeaf(
            pos,
            listOfNotNull(
                typeFormal.variance.keyword?.let {
                    OutputToken(it, OutputTokenType.Word)
                },
                typeFormal.name.toToken(inOperatorPosition = false),
            ),
        )
        if (typeFormal.upperBounds.isNotEmpty()) {
            reduced = OpInner(
                pos,
                Operator.ExtendsNoComma,
                listOf(
                    reduced,
                    Tok(pos, OutToks.extendsWord),
                    OpInner(
                        pos,
                        Operator.Amp,
                        typeFormal.upperBounds.mapOpTreeJoining(OutToks.amp) {
                            PseudoType.reduceTypeActual(pos, it)
                        },
                    ),
                ),
            )
        }
        return reduced
    }
}
internal class PseudoFun(
    override val pos: Position,
    val lifespan: EnumRange<Stage>?,
    val annotations: List<Pair<PseudoNameLeaf, PseudoTree>>,
    val name: PseudoTree?,
    val typeFormals: List<PseudoTree>,
    val formals: List<PseudoTree>,
    val returns: Pair<PseudoTree, ReturnKind>?,
    val superTypes: List<PseudoTree>,
    val body: PseudoTree,
) : PseudoTree() {
    override fun toString() = "(PseudoFun $name $lifespan $annotations $returns $formals $body)"

    override fun reduce(): OpTree {
        var opTree: OpTree = if (name is PseudoValueLeaf && name.value.typeTag == TSymbol) {
            OpLeaf(
                name.pos,
                listOf(
                    OutToks.fnWord,
                    ParsedName(TSymbol.unpack(name.value).text).toToken(inOperatorPosition = false),
                ),
            )
        } else {
            OpLeaf(pos.leftEdge, listOf(OutToks.fnWord))
        }
        if (typeFormals.isNotEmpty()) {
            val typeFormalsPos = typeFormals.spanningPosition(pos)
            val children = mutableListOf(opTree)
            children.add(Tok(typeFormalsPos.leftEdge, OutToks.leftAngle))
            typeFormals.mapOpTreeJoiningTo(children, OutToks.comma) { it.reduce() }
            children.add(Tok(typeFormalsPos.rightEdge, OutToks.rightAngle))
            opTree = OpInner(
                typeFormalsPos,
                Operator.Angle,
                children.toList(),
            )
        }
        if (formals.isNotEmpty()) {
            val formalsPos = formals.spanningPosition(pos)
            opTree = OpInner(
                listOf(pos.leftEdge, formalsPos).spanningPosition(pos),
                Operator.Paren,
                listOf(
                    opTree,
                    Tok(formalsPos.leftEdge, OutToks.leftParen),
                    OpInner(
                        formalsPos,
                        Operator.Comma,
                        formals.mapInterleaving(
                            {
                                if (it is PseudoDecl) {
                                    it.reduce(
                                        emitDeclKeyword = TriState.OTHER, // Skip `let` if possible
                                    )
                                } else {
                                    OpLeaf(it.pos, listOf(OutToks.inconceivableWord))
                                }
                            },
                            { a, _ -> Tok(a.pos.rightEdge, OutToks.comma) },
                        ).toList(),
                    ),
                    Tok(formalsPos.rightEdge, OutToks.rightParen),
                ),
            )
        }
        val (returnTree, returnKind) = returns ?: (null to null)
        val returnDeclReduced = when {
            returns == null || returnTree == null -> null
            returnKind == ReturnKind.ReturnDecl && returnTree is PseudoDecl -> {
                //     let name : type = init
                // ->
                //     /* name */ : type /* = */ /* init */
                val nameReduced = returnTree.declared?.reduce()?.toComment()
                val typeReduced = returnTree.type?.reduceAsType()
                val afterEqualsReduced = returnTree.afterEquals?.reduce()?.toComment()
                listOfNotNull(
                    nameReduced,
                    if (typeReduced != null) {
                        Tok(typeReduced.pos.leftEdge, OutToks.colon)
                    } else {
                        null
                    },
                    typeReduced,
                    if (afterEqualsReduced != null) {
                        Tok(afterEqualsReduced.pos.leftEdge, OutputToken.makeSlashStarComment("="))
                    } else {
                        null
                    },
                    afterEqualsReduced,
                )
            }
            returnKind == ReturnKind.ReturnType && returnTree is PseudoType -> listOf(
                Tok(returnTree.pos.leftEdge, OutToks.colon),
                returnTree.reduceAsType(),
            )
            else -> listOf(
                Tok(returnTree.pos.leftEdge, OutToks.colon),
                returnTree.reduce(),
            )
        }
        if (returnDeclReduced != null) {
            opTree = OpInner(
                returnTree!!.pos,
                Operator.Paren,
                listOf(opTree) + returnDeclReduced,
            )
        }
        if (superTypes.isNotEmpty()) {
            // fn (): Void implements SuperType1, SuperType2
            //             ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            opTree = OpInner(
                (listOf(opTree) + superTypes).spanningPosition(opTree.pos),
                Operator.ImplementsComma,
                buildList {
                    add(opTree)
                    superTypes.forEachIndexed { i, superType ->
                        add(
                            Tok(
                                superType.pos.leftEdge,
                                if (i == 0) {
                                    OutputToken("implements", OutputTokenType.Word)
                                } else {
                                    OutToks.comma
                                },
                            ),
                        )
                        if (superType is PseudoType) {
                            add(superType.reduce(inTypeContext = true))
                        } else {
                            add(superType.reduce())
                        }
                    }
                },
            )
        }
        opTree = PseudoBlock.glomBlockOnto(pos, opTree, body)
        if (lifespan != null) {
            val atPos = name?.pos?.rightEdge ?: pos.leftEdge
            val start = OutputToken(lifespan.start.abbrev.toString(), OutputTokenType.Word)
            opTree = OpInner(
                opTree.pos,
                Operator.At,
                listOf(
                    Tok(atPos, OutToks.at),
                    if (lifespan.start == lifespan.endInclusive) {
                        OpLeaf(atPos, listOf(start))
                    } else {
                        val end = OutputToken(
                            lifespan.endInclusive.abbrev.toString(),
                            OutputTokenType.Word,
                        )
                        OpInner(
                            atPos,
                            Operator.DotDot,
                            listOf(
                                OpLeaf(atPos, listOf(start)),
                                Tok(atPos, OutToks.dotDot),
                                OpLeaf(atPos, listOf(end)),
                            ),
                        )
                    },
                    opTree,
                ),
            )
        }
        opTree = wrapInAnnotations(opTree, annotations)
        return opTree
    }
}

internal class PseudoBlock(
    override val pos: Position,
    val blockCaller: PseudoTree?,
    val elements: List<PseudoTree>,
) : PseudoTree() {
    override fun toString() = "(PseudoBlock $elements)"

    override fun isEmptyBlock(): Boolean = elements.all { it is PseudoValueLeaf && it.value == void }

    override fun reduce(): OpTree {
        val parts = mutableListOf<OpTree>()

        if (blockCaller != null) {
            parts.add(blockCaller.reduce())
        }
        parts.add(Tok(pos.leftEdge, OutToks.leftCurly))
        val n = elements.size

        var statementIndex = 0
        while (statementIndex < n) {
            // Group `let` statements together.
            // This reduces the visual clutter of pseudocode with lots of temporary declarations.
            // Instead of
            //     let t#0; let t#1; let t#3; let t#4;
            // spanning multiple lines we get one line:
            //     let t#0, t#1, t#2, t#3;
            var groupedDeclEnd = statementIndex
            var leadingConstness: Constness? = null
            while (groupedDeclEnd < n) {
                val element = elements[groupedDeclEnd]
                if (element !is PseudoDecl || element.isStandalone) {
                    break
                }
                if (leadingConstness == null) {
                    leadingConstness = element.constness
                } else if (leadingConstness != element.constness) {
                    break
                }
                groupedDeclEnd += 1
            }
            val nGroupedDecls = groupedDeclEnd - statementIndex
            if (nGroupedDecls < 2) {
                // Not a group of declarations.
                // Just output as a statement maybe with a semicolon at the end.
                val oneElement = elements[statementIndex]
                // Skip void when last.
                val isLastInSection = statementIndex + 1 == n ||
                    elements[statementIndex + 1] is PseudoStatementSectionMarker
                val isImpliedVoid = isLastInSection && (oneElement as? PseudoValueLeaf)?.value == void
                if (!isImpliedVoid) {
                    // Skip any void at the end of a block since it's implied by the semicolon
                    val oneReduced = oneElement.reduce()
                    parts.add(oneReduced)
                }
                val needsSemi = when (oneElement.semiFollowsInStatementContext) {
                    SemiFollows.UnlessLastInSection -> !isLastInSection
                    SemiFollows.Never -> false
                    SemiFollows.Always -> true
                }
                if (needsSemi) {
                    parts.add(Tok(oneElement.pos.rightEdge, OutToks.semi))
                }
                statementIndex += 1
            } else {
                val commaParts = mutableListOf<OpTree>()
                for (i in statementIndex until groupedDeclEnd) {
                    val isFirstInRun = i == statementIndex
                    if (!isFirstInRun) {
                        commaParts.add(Tok(elements[i - 1].pos.rightEdge, OutToks.comma))
                    }
                    val decl = elements[i] as PseudoDecl
                    commaParts.add(
                        decl.reduce(
                            emitDeclKeyword = TriState.of(isFirstInRun),
                        ),
                    )
                }
                parts.add(
                    OpInner(
                        elements.subList(0, nGroupedDecls).spanningPosition(pos),
                        Operator.Comma,
                        commaParts.toList(),
                    ),
                )
                parts.add(Tok(elements[nGroupedDecls - 1].pos.rightEdge, OutToks.semi))
                statementIndex = groupedDeclEnd
            }
        }
        parts.add(Tok(pos.rightEdge, OutToks.rightCurly))

        return OpInner(
            pos,
            Operator.CurlyGroup,
            parts,
        )
    }

    companion object {
        fun glomBlockOnto(
            pos: Position,
            opTree: OpTree,
            body: PseudoTree,
        ): OpInner {
            val bodyReduced = body.reduce()
            return OpInner(
                pos,
                Operator.Curly,
                if (bodyReduced is OpInner && bodyReduced.operator == Operator.CurlyGroup) {
                    listOf(opTree) + bodyReduced.children
                } else {
                    listOfNotNull(
                        opTree,
                        Tok(bodyReduced.pos.leftEdge, OutToks.leftCurly),
                        bodyReduced,
                        if (body.semiFollowsInStatementContext == SemiFollows.Always) {
                            Tok(bodyReduced.pos.rightEdge, OutToks.semi)
                        } else {
                            null
                        },
                        Tok(bodyReduced.pos.rightEdge, OutToks.rightCurly),
                    )
                },
            )
        }

        fun wrap(tree: PseudoTree, pos: Position = tree.pos) = when {
            tree is PseudoBlock && tree.blockCaller == null -> tree
            else -> PseudoBlock(pos, blockCaller = null, elements = listOf(tree))
        }
    }
}

/** For rendering interstitials like `mixin("label"):`. */
@Suppress("UnusedPrivateClass") // Not currently used but is recognized in needs-semicolon logic.
internal class PseudoStatementSectionMarker(
    override val pos: Position,
    val keyword: String,
) : PseudoTree() {
    override val semiFollowsInStatementContext get() = SemiFollows.Never

    override fun reduce(): OpTree = OpInner(
        pos,
        Operator.SelectivePostColon,
        listOf(
            OpInner(
                pos,
                Operator.Leaf,
                listOf(
                    Tok(pos, OutputToken(keyword, OutputTokenType.Word)),
                ),
            ),
            Tok(pos.rightEdge, OutputToken(":", OutputTokenType.Punctuation, TokenAssociation.Postfix)),
        ),
    )
}

internal class PseudoIf(
    override val pos: Position,
    val condition: PseudoTree,
    val thenClause: PseudoTree,
    val elseClause: PseudoTree,
) : PseudoTree() {
    override fun reduce(): OpTree {
        var elseClause = elseClause
        if (elseClause is PseudoBlock && elseClause.elements.size == 1) {
            val sole = elseClause.elements[0]
            if (sole is PseudoIf) {
                elseClause = sole
            }
        }
        return reduceControlFlow(
            pos,
            OutToks.ifWord,
            Either.Left(condition),
            thenClause,
            if (elseClause.isEmptyBlock()) {
                null
            } else if (elseClause is PseudoIf) {
                ContinuingClause(OutToks.elseWord, elseClause, parenthesize = false)
            } else {
                ContinuingClause(OutToks.elseWord, PseudoBlock.wrap(elseClause), parenthesize = false)
            },
        )
    }
}

private data class ContinuingClause(
    val keyword: OutputToken,
    val argument: PseudoTree,
    val parenthesize: Boolean,
)

private fun reduceControlFlow(
    pos: Position,
    keyword: OutputToken,
    parenthesized: Either<PseudoTree, Triple<PseudoTree?, PseudoTree?, PseudoTree?>>?,
    body: PseudoTree,
    /** For example, Pair("else", elseBlock) */
    continuingClause: ContinuingClause?,
): OpTree {
    var opTree: OpTree = OpLeaf(pos.leftEdge, listOf(keyword))
    val allParenthesized = when (parenthesized) {
        null -> emptyList()
        is Either.Left -> listOf(parenthesized.item)
        is Either.Right -> listOfNotNull(
            parenthesized.item.first,
            parenthesized.item.second,
            parenthesized.item.third,
        )
    }
    val parenthesizedPos = allParenthesized.spanningPosition(pos.leftEdge)
    if (parenthesized != null) {
        opTree = OpInner(
            (listOf(opTree) + allParenthesized).spanningPosition(pos),
            Operator.Paren,
            listOf(
                opTree,
                Tok(parenthesizedPos.leftEdge, OutToks.leftParen),
                when (parenthesized) {
                    is Either.Left -> parenthesized.item.reduce()
                    is Either.Right -> {
                        val (a, b, c) = parenthesized.item
                        val semi1Pos = (a?.pos?.rightEdge ?: b?.pos?.leftEdge ?: parenthesizedPos.leftEdge)
                        val semi2Pos = (b?.pos?.rightEdge ?: c?.pos?.leftEdge ?: parenthesizedPos.rightEdge)
                        OpInner(
                            parenthesizedPos,
                            Operator.Semi,
                            listOfNotNull(
                                a?.reduce(),
                                Tok(semi1Pos, OutToks.semi),
                                b?.reduce(),
                                Tok(semi2Pos, OutToks.semi),
                                c?.reduce(),
                            ),
                        )
                    }
                },
                Tok(parenthesizedPos.leftEdge, OutToks.rightParen),
            ),
        )
    }
    opTree = PseudoBlock.glomBlockOnto(
        listOf(opTree, body).spanningPosition(pos),
        opTree,
        body,
    )
    if (continuingClause != null) {
        val (continuingKeyword, continuingBlock, parenthesize) = continuingClause
        opTree = OpInner(
            pos,
            Operator.CallJoin,
            if (parenthesize) {
                listOf(
                    opTree,
                    OpInner(
                        continuingBlock.pos,
                        Operator.Paren,
                        listOf(
                            Tok(continuingBlock.pos.leftEdge, continuingKeyword),
                            Tok(continuingBlock.pos.leftEdge, OutToks.leftParen),
                            continuingBlock.reduce(),
                            Tok(continuingBlock.pos.leftEdge, OutToks.rightParen),
                        ),
                    ),
                )
            } else {
                listOf(
                    opTree,
                    Tok(continuingBlock.pos.leftEdge, continuingKeyword),
                    continuingBlock.reduce(),
                )
            },
        )
    }
    return opTree
}
internal class PseudoLoop(
    override val pos: Position,
    /** Whether to check the condition before (left) the body or after. */
    val checkPosition: LeftOrRight,
    val condition: PseudoTree,
    val body: PseudoTree,
    val increment: PseudoTree?,
) : PseudoTree() {
    override fun reduce(): OpTree {
        val keyword: OutputToken
        val parenthesized: Either<PseudoTree, Triple<PseudoTree?, PseudoTree?, PseudoTree?>>?
        val continuingClause: ContinuingClause?
        when {
            checkPosition == LeftOrRight.Right -> {
                // do {body} while (condition);
                // OR
                // do (;;increment) {body} while (condition);
                parenthesized = increment?.let { Either.Right(Triple(null, null, it)) }
                keyword = OutToks.doWord
                continuingClause = ContinuingClause(OutToks.whileWord, condition, parenthesize = true)
            }
            increment != null -> {
                // for (;condition;increment) {body}
                parenthesized = Either.Right(Triple(null, condition, increment))
                keyword = OutToks.forWord
                continuingClause = null
            }
            else -> {
                // while (condition) {body}
                parenthesized = Either.Left(condition)
                keyword = OutToks.whileWord
                continuingClause = null
            }
        }
        return reduceControlFlow(
            pos = pos,
            keyword = keyword,
            parenthesized = parenthesized,
            body = body,
            continuingClause = continuingClause,
        )
    }
}
private data class PseudoOrElse(
    override val pos: Position,
    val label: PseudoNameLeaf,
    val orClause: PseudoTree,
    val elseClause: PseudoTree,
) : PseudoTree() {
    override fun reduce(): OpTree = OpInner(
        pos,
        Operator.LowColon,
        listOf(
            label.reduce(),
            Tok(label.pos.rightEdge, OutToks.colon),
            OpInner(
                pos,
                Operator.OrElse,
                listOf(
                    orClause.reduce(),
                    Tok(orClause.pos.rightEdge, OutToks.orElseWord),
                    elseClause.reduce(),
                ),
            ),
        ),
    )
}

internal class PseudoJump(
    override val pos: Position,
    val kind: BreakOrContinue,
    val label: TemperName?,
) : PseudoTree() {
    override val semiFollowsInStatementContext get() = SemiFollows.Always
    override fun reduce(): OpTree = OpLeaf(
        pos,
        listOfNotNull(
            when (kind) {
                BreakOrContinue.Continue -> OutToks.continueWord
                BreakOrContinue.Break -> OutToks.breakWord
            },
            label?.toToken(inOperatorPosition = false),
        ),
    )
}
internal class PseudoLabeled(
    override val pos: Position,
    val name: PseudoTree,
    val statement: PseudoTree,
) : PseudoTree() {
    override val semiFollowsInStatementContext get() = statement.semiFollowsInStatementContext
    override fun reduce(): OpTree = OpInner(
        pos,
        Operator.LowColon,
        listOf(
            name.reduce(),
            Tok(statement.pos.leftEdge, OutToks.colon),
            statement.reduce(),
        ),
    )
}
internal class PseudoEsc(
    override val pos: Position,
    val child: PseudoTree,
) : PseudoTree() {
    override fun reduce(): OpTree = OpInner(
        pos,
        Operator.EscParen,
        listOf(
            Tok(pos.leftEdge, OutToks.leftEscParen),
            child.reduce(),
            Tok(pos.leftEdge, OutToks.rightParen),
        ),
    )
}
internal class PseudoError(
    override val pos: Position,
) : PseudoTree() {
    override val semiFollowsInStatementContext get() = SemiFollows.Always
    override fun reduce() = OpLeaf(pos, listOf(OutToks.inconceivableWord))
}

private fun <T> (Iterable<T>).mapOpTreeJoiningTo(
    out: MutableCollection<OpTree>,
    joiner: OutputToken,
    transform: (T) -> OpTree,
) {
    var lastPos: Position? = null
    forEachIndexed { index, element ->
        if (index != 0) {
            out.add(Tok(lastPos!!.rightEdge, joiner))
        }
        val tree = transform(element)
        lastPos = tree.pos
        out.add(tree)
    }
}

private fun <T> (Iterable<T>).mapOpTreeJoining(
    joiner: OutputToken,
    transform: (T) -> OpTree,
): List<OpTree> {
    val trees = mutableListOf<OpTree>()
    mapOpTreeJoiningTo(trees, joiner, transform)
    return trees.toList()
}

private fun (OpTree).toComment(): Tok {
    val text = toStringViaTokenSink {
        this.renderTo(it)
    }
    return Tok(this.pos, OutputToken.makeSlashStarComment(text))
}

object TemperFormattingHints : FormattingHints {
    override fun continuesStatementAfterBlock(token: OutputToken): Boolean {
        return token in extraBlockContinuers || super.continuesStatementAfterBlock(token)
    }

    override fun spaceBetween(preceding: OutputToken, following: OutputToken): Boolean {
        if (
            preceding.type == OutputTokenType.Punctuation &&
            preceding.association != TokenAssociation.Bracket &&
            following.type == OutputTokenType.Punctuation &&
            following.association != TokenAssociation.Bracket
        ) {
            // This should catch token merging conflicts like
            // - comment boundary: "/ *" -> "/*", "/ /" -> "//"
            // - adjacent same char: "+ +" -> "++", similarly "--", "||", "&&", "..", "<<", ">>"
            // - merge different into multi-char punctuator: "= ~" -> "=~", "+ =" -> "+="
            // Many should never occur when serializing valid programs, but some can: (1 - -1)
            val combined = preceding.text + following.text
            val lexer = Lexer(UnknownCodeLocation, LogSink.devNull, combined)
            val token = lexer.nextNotSyntheticOrNull
            if (token == null || lexer.sourceOffsetOf(token) != 0 || token.tokenText != preceding.text) {
                return true
            }
        }
        return super.spaceBetween(preceding, following)
    }

    override fun shouldBreakAfter(token: OutputToken): Boolean {
        if (isInlineSentinel(token)) { return false }
        return super.shouldBreakAfter(token)
    }

    override fun shouldBreakBefore(token: OutputToken): Boolean {
        if (isInlineSentinel(token)) { return false }
        return super.shouldBreakBefore(token)
    }

    override fun shouldBreakBetween(preceding: OutputToken, following: OutputToken): TriState {
        if (isInlineSentinel(preceding) || isInlineSentinel(following)) {
            return TriState.FALSE
        }
        return super.shouldBreakBetween(preceding, following)
    }

    // Some sentinel values for formatting
    val inlineLCurly = OutputToken(
        text = OutToks.leftCurly.text,
        type = OutToks.leftCurly.type,
        association = OutToks.leftCurly.association,
    )
    val inlineRCurly = OutputToken(
        text = OutToks.rightCurly.text,
        type = OutToks.rightCurly.type,
        association = OutToks.rightCurly.association,
    )
    val inlineComma = OutputToken(
        text = OutToks.rightCurly.text,
        type = OutToks.rightCurly.type,
        association = OutToks.rightCurly.association,
    )

    private fun isInlineSentinel(token: OutputToken) =
        // Intentional use of === since we're checking for a sentinel value
        inlineSentinels[token] === token

    private val inlineSentinels = mapOf(
        inlineComma to inlineComma,
        inlineLCurly to inlineLCurly,
        inlineRCurly to inlineRCurly,
    )
}

private val extraBlockContinuers = (
    Operator.entries.mapNotNull {
        if (it.continuesStatement) {
            OutputToken(it.text!!, OutputTokenType.Word)
        } else {
            null
        }
    } +
        OutToks.orElseWord
    ).toSet()

/** Wrap an OpTree in `@name(value)` given a list of annotations. */
private fun wrapInAnnotations(
    innerOpTree: OpTree,
    annotations: List<Pair<PseudoNameLeaf, PseudoTree>>,
): OpTree {
    var opTree = innerOpTree
    for ((k, v) in annotations.asReversed()) {
        val annotationPos = listOf(k, v).spanningPosition(opTree.pos)
        val hasValue = when (v) {
            is PseudoValueLeaf if v.value == void -> false
            is PseudoStayLeaf if k.name.builtinKey == staySymbol.text -> false
            else -> true
        }
        opTree = OpInner(
            listOf(annotationPos, opTree.pos).spanningPosition(opTree.pos),
            Operator.At,
            listOf(
                Tok(annotationPos.leftEdge, OutToks.at),
                OpInner(
                    annotationPos,
                    Operator.Paren,
                    (
                        listOf(k.reduce()) +
                            if (hasValue) { // = value
                                listOf(
                                    Tok(v.pos.leftEdge, OutToks.leftParen),
                                    v.reduceAsType(),
                                    Tok(v.pos.rightEdge, OutToks.rightParen),
                                )
                            } else { // skip assignment when value is void
                                emptyList()
                            }
                        ),
                ),
                opTree,
            ),
        )
    }
    return opTree
}

/** True if the declaration should not be grouped into a comma `let` declaration. */
private fun isStandaloneDecl(metadata: MetadataMultimap): Boolean =
    // Class and interface members should not be grouped into a comma list.
    typeDeclSymbol in metadata || typeMemberMetadataSymbols.any { it in metadata }

private infix fun (PseudoTree).isProbablyBuiltinFunNamed(desiredName: String): Boolean =
    when (this) {
        is PseudoNameLeaf -> name.builtinKey == desiredName
        is PseudoValueLeaf ->
            (TFunction.unpackOrNull(value) as? NamedBuiltinFun)?.name == desiredName
        else -> false
    }

private fun (OpTree).surround(
    pos: Position,
    before: List<OutputToken>,
    after: List<OutputToken>,
): OpTree = when (this) {
    is OpLeaf -> OpLeaf(
        pos,
        before + this.outputTokens + after,
    )
    is OpInner -> OpInner(
        pos,
        this.operator,
        before.toOpTreeList(pos.leftEdge) +
            this.children +
            after.toOpTreeList(pos.rightEdge),
    )
    is Tok -> OpLeaf(
        pos,
        before + listOf(this.outputToken) + after,
    )
}

private fun (List<OutputToken>).toOpTreeList(pos: Position) =
    if (this.isNotEmpty()) {
        listOf(OpLeaf(pos, this))
    } else {
        listOf()
    }

private fun (TokenSerializable).renderToTokenList(): List<OutputToken> {
    val tokens = mutableListOf<OutputToken>()
    renderTo(object : TokenSink {
        override fun position(pos: Position, side: LeftOrRight) = Unit
        override fun endLine() = Unit
        override fun emit(token: OutputToken) {
            tokens.add(token)
        }
        override fun finish() = Unit
    })
    return tokens.toList()
}

/** A visually distinct colon used for type inferences when [PseudoCodeDetail.showInferredTypes] */
private val zColon = OutputToken("⦂", OutputTokenType.Punctuation)
private val zLeftAngleToken =
    OutputToken("⋖", OutputTokenType.Punctuation, TokenAssociation.Bracket)
private val zRightAngleToken =
    OutputToken("⋗", OutputTokenType.Punctuation, TokenAssociation.Bracket)

private fun PseudoTree.reduceAsType() = if (this is PseudoType) {
    reduce(inTypeContext = true)
} else {
    reduce()
}

/** Which builtin functions expect types as arguments so should render those arguments as types. */
private val expectedTypeArgsForBuiltin = mapOf(
    BuiltinName("<>") to 1..Int.MAX_VALUE,
    BuiltinName("as") to 1..1,
    BuiltinName("assertAs") to 1..1,
    BuiltinName("extends") to 0..1,
    getStaticBuiltinName to 0..0,
    internalGetStaticBuiltinName to 0..0,
    BuiltinName("is") to 0..1,
    BuiltinName("new") to 0..0,
    BuiltinName("this") to 0..0,
)

internal enum class SemiFollows {
    UnlessLastInSection,
    Never,
    Always,
}

/**
 * Splits a string into (optional-tag, quoted-string-token-text).
 *
 * If the string can be safely presented as a `raw"..."` string, and
 * there's some readability benefit, then do so.
 */
private fun maybeTagStringValue(stringValue: String): Pair<String?, String> {
    val escapedString = temperEscaper.escape(stringValue)

    if (escapedString.length > stringValue.length + 2) {
        // There was an escape sequence, so there's some benefit
        // to using the raw form.
        var isSafeRaw = true
        var lastWasEscape = false
        for (i in stringValue.indices) {
            val c = stringValue[i]
            if (c == '\n' || c == '\r' || (c == '"' && !lastWasEscape)) {
                isSafeRaw = false
                break
            }
            if (c == '{' && i != 0 && stringValue[i - 1] == '$') {
                isSafeRaw = false
                break
            }
            lastWasEscape = !lastWasEscape && (c == '\\')
        }
        if (lastWasEscape) {
            isSafeRaw = false
        }
        if (isSafeRaw) {
            return "raw" to "\"$stringValue\""
        }
    }
    return null to escapedString
}

private fun isDotHelperCall(t: CallTree) = extractDotHelperFromCall(t) != null

private fun extractDotHelperFromCall(t: CallTree): DotHelper? {
    val callee = t.child(0)
    if (callee is ValueLeaf) {
        val fn = TFunction.unpackOrNull(callee.content)
        if (fn is DotHelper) {
            return fn
        }
    }
    return null
}

internal enum class ReturnKind {
    ReturnType,
    ReturnDecl,
    Error,
}
