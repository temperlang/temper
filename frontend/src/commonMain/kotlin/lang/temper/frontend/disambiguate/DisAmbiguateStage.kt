package lang.temper.frontend.disambiguate

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.RemUnpacked
import lang.temper.builtin.isComplexArg
import lang.temper.builtin.isRemCall
import lang.temper.builtin.pickCommentToAttach
import lang.temper.builtin.unpackAsRemCall
import lang.temper.common.LeftOrRight
import lang.temper.common.Log
import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.Module
import lang.temper.frontend.StageOutputs
import lang.temper.frontend.flipDeclaredNames
import lang.temper.frontend.interpretiveDanceStage
import lang.temper.frontend.syntax.maybeAttachEmbeddedComment
import lang.temper.interp.convertToErrorNode
import lang.temper.lexer.Genre
import lang.temper.lexer.Operator
import lang.temper.log.Debug
import lang.temper.log.FailLog
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.snapshot
import lang.temper.log.spanningPosition
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.type.MkType
import lang.temper.type.TypeFormal
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.Document
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.InnerTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.TEdge
import lang.temper.value.TSymbol
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.errorFn
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.impliedThisSymbol
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.optionalSymbol
import lang.temper.value.restFormalSymbol
import lang.temper.value.spanningPosition
import lang.temper.value.surpriseMeSymbol
import lang.temper.value.symbolContained
import lang.temper.value.typeArgSymbol
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.vDefaultSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vResolutionSymbol
import lang.temper.value.vRestFormalSymbol
import lang.temper.value.vTypeArgSymbol
import lang.temper.value.vTypeFormalSymbol
import lang.temper.value.vWithinDocFoldSymbol
import lang.temper.value.vWordSymbol
import lang.temper.value.valueContained
import lang.temper.value.void
import lang.temper.value.wordSymbol

internal class DisAmbiguateStage(
    private val module: Module,
    private val root: BlockTree,
    private val failLog: FailLog,
    private val logSink: LogSink,
) {
    fun process(callback: (StageOutputs) -> Unit) {
        val configKey = root.configurationKey
        val outputs = Debug.Frontend.DisAmbiguateStage(configKey).group("Disambiguate Stage") {
            interpretiveDanceStage(
                stage = Stage.DisAmbiguate,
                root = root,
                failLog = failLog,
                logSink = logSink,
                module = module,
                beforeInterpretation = { root, _ ->
                    Debug.Frontend.DisAmbiguateStage.Before
                        .snapshot(configKey, AstSnapshotKey, root)
                    // Flatten multi init first so multi decl can repeat decorators after.
                    flattenMultiInit(root, logSink)
                    extractFlowInitDeclarations(root, logSink)
                    flattenMultiDeclarations(root)
                },
                afterInterpretation = { (root), _ ->
                    actualizeRemainingCallArgs(root, logSink)
                    formalizeRemainingFunArgs(root)
                    hoistDecls(root)
                    flipDeclaredNames(root)
                    Debug.Frontend.DisAmbiguateStage.After.snapshot(configKey, AstSnapshotKey, root)
                },
            )
        }

        callback(outputs)
    }
}

private fun actualizeRemainingCallArgs(tree: Tree, logSink: LogSink) {
    TreeVisit.startingAt(tree)
        .forEach { t ->
            if (t is CallTree) {
                // Convert complex args that were not formalized to actual expressions
                var i = 0
                while (i < t.size) {
                    val child = t.child(i)
                    i +=
                        if (isComplexArg(child)) {
                            val replacements = actualizeGroupedFormal(child as BlockTree, logSink)
                            t.replace(i..i) {
                                replacements.forEach { Replant(it) }
                            }
                            replacements.size
                        } else {
                            1
                        }
                }
                // Actualize type parameters by converting
                //    (Call callee \typeArg T \typeArg U rest of args)
                // to
                //    (Call (Call <> callee T U) rest of args)
                val startOfTypeArgs = 1
                var endOfTypeArgs = startOfTypeArgs
                val typeArgs = mutableListOf<Tree>()
                while (endOfTypeArgs + 1 < t.size) {
                    val c = t.child(endOfTypeArgs)
                    if (c.symbolContained == typeArgSymbol) {
                        typeArgs.add(t.child(endOfTypeArgs + 1))
                        endOfTypeArgs += 2
                    } else {
                        break
                    }
                }
                if (typeArgs.isNotEmpty()) {
                    val callee = t.child(0)
                    val angleCallPos = t.children.subList(0, endOfTypeArgs)
                        .spanningPosition(callee.pos)
                    t.replace(startOfTypeArgs until endOfTypeArgs) {
                        // Replace with nothing
                    }
                    t.replace(0..0) {
                        Call(angleCallPos) {
                            V(BuiltinFuns.vAngleFn)
                            Replant(freeTree(callee))
                            typeArgs.forEach { Replant(it) }
                        }
                    }
                }
            }
            VisitCue.Continue
        }.visitPostOrder()
}

private fun formalizeRemainingFunArgs(root: BlockTree) {
    val formalEdges = mutableListOf<TEdge>()
    TreeVisit.startingAt(root).forEachContinuing {
        if (it is FunTree) {
            for (edge in it.edges) {
                if (isComplexArg(edge.target)) {
                    formalEdges.add(edge)
                }
            }
        }
    }.visitPreOrder()

    for (formalEdge in formalEdges) {
        val block = formalEdge.target
        val document = block.document

        val children = (1 until block.size).mapTo(mutableListOf()) {
            freeTarget(block.edge(it))
        }
        augmentDeclWithSymbol(children, document)
        formalEdge.replace(
            DeclTree(document = document, pos = block.pos, children = children),
        )
    }
}

private fun actualizeGroupedFormal(
    tree: BlockTree,
    logSink: LogSink,
): List<Tree> {
    run {
        // In `/** commentary */ x = 1`, a doc comments got
        // stored in a complex arg.
        // If we're using the complex arg as an actual, there'll
        // never be anything to attach the comment to, so just
        // throw it away.
        val start = 1
        var end = start
        while (true) {
            val c = tree.childOrNull(end)
            if (c != null && isRemCall(c)) {
                end += 1
            } else {
                break
            }
        }
        if (end > start) {
            tree.removeChildren(start until end)
        }
    }

    if (tree.size <= 2) {
        reportMalformedActual(tree, 0, tree.size, logSink)
        return listOf(errorTree(tree, 0, tree.size))
    }

    val e0 = tree.edge(1)
    val c0 = e0.target
    var defaultExprTree: Tree? = null

    var problemStart = if (c0 !is NameLeaf) 0 else -1

    var i = 2
    while (i < tree.size) {
        if (i + 1 == tree.size) {
            if (problemStart < 0) {
                problemStart = i
            }
            break
        }
        val symbolEdge = tree.edge(i)
        val symbolTree = symbolEdge.target
        val associatedEdge = tree.edge(i + 1)
        val associatedExpr = associatedEdge.target

        i +=
            if (symbolTree.symbolContained != null) {
                if (vDefaultSymbol == symbolTree.content) {
                    if (problemStart >= 0) {
                        reportMalformedActual(tree, problemStart, i, logSink)
                        problemStart = -1
                    }
                    associatedEdge.replace(null) // So we can add it to the call.
                    defaultExprTree = associatedExpr
                    1
                } else {
                    if (problemStart < 0) {
                        problemStart = i
                    }
                    2
                }
            } else {
                if (problemStart < 0) {
                    problemStart = i
                }
                1
            }
    }

    if (problemStart >= 0) {
        reportMalformedActual(tree, problemStart, tree.size, logSink)
    }

    return when {
        defaultExprTree != null && c0 is NameLeaf && c0.content is ParsedName -> {
            logSink.log(
                level = Log.Error,
                template = MessageTemplate.NamedActual,
                pos = tree.spanningPosition(0, tree.size),
                values = emptyList(),
            )
            // We could provide just the value as a fallback, but that might easily result in unexpected behavior.
            listOf(errorTree(tree, 0, tree.size))
        }
        else -> listOf(freeTarget(e0))
    }
}

private fun errorTree(tree: Tree, leftIndex: Int, rightIndex: Int): CallTree =
    tree.treeFarm.grow(tree.spanningPosition(leftIndex, rightIndex)) {
        Call(errorFn) {}
    }

private fun reportMalformedActual(
    tree: Tree,
    leftIndex: Int,
    rightIndex: Int,
    logSink: LogSink,
) {
    val pos = tree.spanningPosition(leftIndex, rightIndex)
    logSink.log(
        level = Log.Error,
        template = MessageTemplate.MalformedActual,
        pos = pos,
        values = emptyList(),
    )
}

internal fun formalizeArgs(
    macroEnv: MacroEnvironment,
    isDeclaration: Boolean,
): PartialResult {
    val args = macroEnv.args
    val rawTreeList = args.rawTreeList

    var result: PartialResult = NotYet

    val n = rawTreeList.size

    // If this is a `let` declaration, or it has a body, then we need to treat any arguments
    // as function inputs.  Abstract methods don't have bodies, but should have been rewritten from
    // `interface I { public      f(): Void }` to
    // `interface I { @public let f(): Void }` by now.
    //
    // Otherwise, it's probably a type expression like `fn (Types, Not, Declarations): ReturnType`.
    // We formalize those arguments differently to interact with the builtin type functions.
    val isFunctionConstructor = isDeclaration || (n != 0 && rawTreeList[n - 1] is FunTree)

    // collect changes to make based on edges before doing things that might
    // upset indexing into args then execute them after we don't care about indices.
    val pending = mutableListOf<() -> Unit>()

    var i = 0
    while (i < n) {
        val tree = rawTreeList[i]
        val value = tree.valueContained
        if (value != null) {
            if (value.typeTag == TSymbol) {
                if (i + 1 < n && value == vTypeArgSymbol) {
                    val typeFormalEdge = rawTreeList[i + 1].incoming
                    if (typeFormalEdge != null) {
                        pending.add {
                            if (formalizeTypeArg(typeFormalEdge)) {
                                // Convert \typeArg to \typeFormal so that we don't later
                                // actualize it.
                                tree.incoming?.replace(
                                    ValueLeaf(tree.document, tree.pos, vTypeFormalSymbol),
                                )
                            } else {
                                result = Fail
                                macroEnv.logSink.log(
                                    level = Log.Error,
                                    template = MessageTemplate.MalformedTypeDeclaration,
                                    pos = typeFormalEdge.target.pos,
                                    values = emptyList(),
                                )
                                convertToErrorNode(typeFormalEdge)
                            }
                        }
                    }
                }
                i += 1
            }
        } else {
            val edge = tree.incoming
            if (edge != null) {
                if (isFunctionConstructor) {
                    pending.add { formalizeArg(edge) }
                } else { // is type constructor
                    pending.add { splitComplexArgIntoWordAndType(edge) }
                }
            }
        }
        i += 1
    }

    for (change in pending) {
        change()
    }

    return result
}

internal fun formalizeArg(e: TEdge) {
    val tree = e.target
    val doc = tree.document
    when (tree) {
        is NameLeaf -> {
            // Promote a bare name to a declaration.
            val children = mutableListOf(freeTarget(e))
            val symbol = tree.content.toSymbol()
            val pos = tree.pos
            if (symbol != null) {
                // Store the symbol so that it can be used in pass-by-name
                children.add(ValueLeaf(doc, pos, vWordSymbol))
                children.add(ValueLeaf(doc, pos, Value(symbol)))
            }
            e.replace(DeclTree(doc, pos, children = children))
        }
        is CallTree -> {
            // Look through annotations
            val decorated = lookThroughDecorations(e)
            if (decorated != e) {
                formalizeArg(decorated)
            }
        }
        is DeclTree -> requireWordMetadata(tree)
        is BlockTree ->
            if (isComplexArg(tree)) {
                val comments = mutableListOf<RemUnpacked>()
                val children = mutableListOf<Tree>()
                var lastWasKeySymbol = false
                for (index in 1 until tree.size) {
                    val t = freeTarget(tree.edge(index))
                    if (isRemCall(t)) {
                        val comment = unpackAsRemCall(t)
                        if (comment?.association == LeftOrRight.Right) {
                            comments.add(comment)
                        }
                    } else {
                        // If it is a key not a value in the metadata pairs
                        // take any surpriseMeSymbol (...) and swap it for the more informative
                        // varArgsSymbol
                        val keySymbol = if (!lastWasKeySymbol) t.symbolContained else null
                        children.add(
                            if (keySymbol == surpriseMeSymbol) {
                                ValueLeaf(t.document, t.pos, vRestFormalSymbol)
                            } else {
                                t
                            },
                        )
                        lastWasKeySymbol = keySymbol != null
                    }
                }
                val soleChild = if (children.size == 1) { children[0] } else { null }
                val decl = if (soleChild is DeclTree) {
                    requireWordMetadata(soleChild)
                    soleChild
                } else {
                    augmentDeclWithSymbol(children, tree.document)
                    DeclTree(doc, tree.pos, children)
                }
                if (comments.isNotEmpty()) {
                    val comment = pickCommentToAttach(comments, decl)
                    if (comment != null) {
                        maybeAttachEmbeddedComment(comment, decl, maybeDecorateFnInitializer = false)
                    }
                }
                e.replace(decl)
            }
        else -> Unit
    }
}

/** `name: Type` -> `\word, name /* as LeftNameLeaf */, Type` */
private fun splitComplexArgIntoWordAndType(e: TEdge) {
    val tree = e.target
    if (tree is CallTree) {
        // Look through annotations
        val decorated = lookThroughDecorations(e)
        if (decorated != e) {
            splitComplexArgIntoWordAndType(decorated)
        }
    } else if (isComplexArg(tree)) {
        require(tree is InnerTree)
        val nameOrType = tree.childOrNull(1) ?: return
        var type: Tree? = null

        // Look at metadata
        // TODO: If there is a default expression, maybe that indicates
        // optionality.
        var isRest = false
        var isOptional = false
        for ((keySymbol, valueIndex) in SymbolPairsNonMutating(tree, startIndex = 2)) {
            when (keySymbol) {
                typeSymbol -> {
                    type = tree.child(valueIndex)
                    break
                }
                surpriseMeSymbol -> isRest = true
                initSymbol -> isOptional = true
            }
        }

        val edgeIndex = e.edgeIndex
        e.source!!.replace(edgeIndex..edgeIndex) {
            val pos = tree.pos
            if (type != null) { // `x: Type`
                Replant(freeTree(type))
                (nameOrType as? NameLeaf)?.content?.toSymbol()?.let { nameAsSymbol ->
                    V(pos.leftEdge, wordSymbol)
                    V(nameOrType.pos, nameAsSymbol)
                }
            } else { // Type
                Replant(freeTree(nameOrType))
            }
            val rightEdge = pos.rightEdge
            if (isRest) {
                V(rightEdge, restFormalSymbol)
                V(rightEdge, void)
            }
            if (isOptional) {
                V(optionalSymbol)
                V(rightEdge, void)
            }
        }
    }
}

/**
 * Converts type formals like
 *
 * - `<T>`
 * - `<T extends A & B>`
 *
 * that are marked with [vTypeArgSymbol] to a declaration like
 *
 *     {
 *       // Define T as a name.
 *       @typeFormal(\T) @typeDecl(T__0) @resolution(T__0) let T = T__0;
 *       // Replant the defining expression so that we can fill in super-types and variance
 *       // once A and B are resolvable.
 *       T extends A & B;
 *       T__0
 *     }
 *
 * The declaration is hoisted into the surrounded block
 * so that we can resolve uses in the function signature and body.
 *
 * @return true if we manage to do the rewrite.
 */
private fun formalizeTypeArg(e: TEdge): Boolean {
    val target = e.target
    var nameEdge = e
    var variance = Variance.Invariant
    var hasUpperBound = false
    // Walk through `extends`, etc. to find the declared name.
    // TODO: Can this be shared with similar code in TypeDisAmbiguateMacro?
    while (true) {
        val nameTree = nameEdge.target
        if (nameTree is CallTree && nameTree.size != 0) {
            val builtinNameText = when (val callee = nameTree.child(0)) {
                is RightNameLeaf -> when (val calleeName = callee.content) {
                    is BuiltinName -> calleeName.builtinKey
                    is ParsedName -> calleeName.nameText
                    is ResolvedName -> null
                }
                else -> (callee.functionContained as? NamedBuiltinFun)?.name
            }
            val nameIndex = typeFormalOperatorToNameIndex[builtinNameText]
            when (builtinNameText) {
                covariantAnnotationNameText -> variance = Variance.Covariant
                contravariantAnnotationNameText -> variance = Variance.Contravariant
                Operator.ExtendsComma.text,
                Operator.ImplementsComma.text,
                -> hasUpperBound = true
            }
            if (nameIndex != null) {
                nameEdge = nameTree.edge(nameIndex)
                continue
            }
        }
        break // We continue above if there's more work to do
    }

    val nameTree = nameEdge.target
    val document = nameTree.document
    val genre = document.context.genre

    val name = (nameTree as? RightNameLeaf)?.content as? ParsedName
        ?: return false
    val nameSymbol = Symbol(name.nameText)
    val pos = e.target.pos
    val leftPos = pos.leftEdge
    val declarationName = document.nameMaker.unusedSourceName(name)
    val typeFormal = TypeFormal(
        pos,
        declarationName,
        nameSymbol,
        variance,
        document.context.definitionMutationCounter,
        if (hasUpperBound) {
            // upperBounds to be filled in by `extends` macro
            emptyList()
        } else {
            listOf(MkType.nominal(WellKnownTypes.anyValueTypeDefinition))
        },
    )
    val typeValue = Value(ReifiedType(MkType2(typeFormal).get()))
    e.replace {
        Block {
            Decl(pos, name) {
                // When the syntax stage resolves names, use declarationName.
                V(leftPos, vResolutionSymbol)
                Ln(declarationName)
                V(leftPos, vTypeFormalSymbol)
                V(leftPos, nameSymbol)
                V(leftPos, typeDeclSymbol)
                V(leftPos, typeValue)
                V(leftPos, vInitSymbol)
                V(pos, typeValue)
                if (genre == Genre.Documentation) {
                    V(leftPos, vWithinDocFoldSymbol)
                    V(leftPos, void)
                }
            }
            // Replant so that any `extends` clauses fire to actually connect super-types.
            if (target !is RightNameLeaf) {
                Replant(freeTree(target))
            }
            V(typeValue)
        }
    }
    return true
}

private fun augmentDeclWithSymbol(
    declChildren: MutableList<Tree>,
    doc: Document,
) {
    var name = declChildren.getOrNull(0) as? NameLeaf ?: return
    if (name is RightNameLeaf) {
        name = name.copyLeft()
        declChildren[0] = name
    }
    for (i in 1 until declChildren.size step 2) {
        val child = declChildren[i]
        if (child.symbolContained == wordSymbol) {
            return
        }
    }

    val symbol = name.content.toSymbol()
    if (symbol != null) {
        val pos = name.pos
        declChildren.add(ValueLeaf(doc, pos, vWordSymbol))
        declChildren.add(ValueLeaf(doc, pos, Value(symbol, TSymbol)))
    }
}

private val contravariantAnnotationNameText = "@${Variance.Contravariant.keyword}"
private val covariantAnnotationNameText = "@${Variance.Covariant.keyword}"
private val typeFormalOperatorToNameIndex = mapOf(
    Operator.ExtendsComma.text!! to 1,
    Operator.ImplementsComma.text!! to 1,
    Operator.Instanceof.text!! to 1,
    contravariantAnnotationNameText to 1,
    covariantAnnotationNameText to 1,
    Operator.Eq.text!! to 1,
)

/**
 * Make sure formals have \word metadata.
 * This is important for constructor argument so that we
 * can choose a constructor by analysis of property keys in a property bag.
 */
private fun requireWordMetadata(tree: DeclTree) {
    val parts = tree.parts
    val symbol = parts?.name?.content?.toSymbol()
    if (
        symbol != null &&
        wordSymbol !in parts.metadataSymbolMultimap &&
        impliedThisSymbol !in parts.metadataSymbolMultimap
    ) {
        val namePos = parts.name.pos
        tree.insert(tree.size) {
            V(namePos.leftEdge, wordSymbol)
            V(namePos, symbol)
        }
    }
}
