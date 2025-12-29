package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.Log
import lang.temper.common.OpenOrClosed
import lang.temper.common.mapFirst
import lang.temper.env.Constness
import lang.temper.env.DeclarationBits
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.frontend.disambiguate.callSymbolPairsMutating
import lang.temper.frontend.disambiguate.getTypeShapeForCallToTypeMacro
import lang.temper.frontend.disambiguate.reifiedTypeFor
import lang.temper.interp.convertToErrorNode
import lang.temper.interp.isErrorNode
import lang.temper.log.MessageTemplate
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.Abstractness
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.Visibility
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.PartialResult
import lang.temper.value.RightNameLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.concreteSymbol
import lang.temper.value.constructorPropertySymbol
import lang.temper.value.constructorSymbol
import lang.temper.value.defaultSymbol
import lang.temper.value.dotBuiltinName
import lang.temper.value.freeTarget
import lang.temper.value.getterSymbol
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.methodSymbol
import lang.temper.value.noPropertySymbol
import lang.temper.value.propertySymbol
import lang.temper.value.returnParsedName
import lang.temper.value.setterSymbol
import lang.temper.value.symbolContained
import lang.temper.value.thisParsedName
import lang.temper.value.toPseudoCode
import lang.temper.value.vConstructorSymbol
import lang.temper.value.vDefaultSymbol
import lang.temper.value.vImpliedThisSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vMethodSymbol
import lang.temper.value.vPublicSymbol
import lang.temper.value.vReturnDeclSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.vVisibilitySymbol
import lang.temper.value.vWordSymbol
import lang.temper.value.valueContained
import lang.temper.value.void
import lang.temper.value.wordSymbol

internal fun typeSyntaxMacro(macroEnv: MacroEnvironment): PartialResult {
    val args = macroEnv.args
    val doc = macroEnv.document
    val macroCall = macroEnv.call
        ?: return Fail // We can't do much if the class definition is not rooted in the AST.
    val shape = getTypeShapeForCallToTypeMacro(macroEnv) ?: return Fail

    // Figure out the name, if any, and while we're at it, reduce \word metadata to a symbol so
    // that it does not get processed during renaming.
    // We need the name so that we can wrap the class definition in a `let` declaration.
    val name: TemperName? = run {
        var wordEdge: TEdge? = null
        for (i in args.indices) {
            if (args.key(i) == wordSymbol) {
                wordEdge = args.valueTree(i).incoming!!
                break
            }
        }
        if (wordEdge == null) {
            null
        } else {
            fun badWord(): TemperName? {
                val wordTree = wordEdge.target
                if (!isErrorNode(wordTree)) {
                    macroEnv.explain(MessageTemplate.MalformedTypeDeclaration, wordTree.pos)
                    convertToErrorNode(wordEdge)
                }
                return null
            }
            val wordTree = wordEdge.target
            if (wordTree is NameLeaf) {
                val name = wordTree.content
                val symbol = name.toSymbol()
                if (symbol != null) {
                    wordEdge.replace(ValueLeaf(wordTree.document, wordTree.pos, Value(symbol)))
                } else {
                    badWord()
                }
                name
            } else {
                // Probably not reached, but makes idempotent
                when (val symbol = wordTree.symbolContained) {
                    null -> badWord()
                    else -> macroEnv.nameMaker.parsedName(symbol.text)
                }
            }
        }
    }
    val leftName = when (name) {
        null -> null
        else -> LeftNameLeaf(doc, macroEnv.pos.leftEdge, name)
    }

    // Pre-declare so that the ClassShapeMacro can set up recursive reference
    if (leftName != null) {
        val typeValue = Value(reifiedTypeFor(shape))
        macroEnv.declareLocal(
            leftName,
            DeclarationBits(
                reifiedType = null,
                initial = typeValue,
                constness = Constness.Const,
                referentSource = ReferentSource.SingleSourceAssigned,
                missing = ReferentBitSet.empty,
                declarationSite = leftName.pos,
            ),
        )
    }

    val result = typeShapeMacro(macroEnv)

    // Concrete types need a constructor
    val concreteness = callSymbolPairsMutating(macroCall).mapFirst { (s, i) ->
        if (s == concreteSymbol) {
            when (macroCall.childOrNull(i + 1)?.valueContained(TBoolean)) {
                true -> Abstractness.Concrete
                false -> Abstractness.Abstract
                else -> null
            }
        } else {
            null
        }
    } ?: Abstractness.Abstract
    val isConcrete = concreteness == Abstractness.Concrete
    if (isConcrete && shape.methods.none { it.symbol == constructorSymbol }) {
        run makeConstructor@{
            // Given backed properties where the set of constructor parameters is (a, b, c)
            //     a: A = e(); // `=` means default
            //     b: B;
            //     c: C = f(); // not a constructorProperty, so `=` means initialized to
            // hoist defaults&initializers out into a constructor parameter list to end up with
            //     a: A;
            //     b: B;    // Use temporary to capture T if it is not simple.
            //     c: C;
            //     constructor(@const this__123: C, a: A = e(), b: B) {
            //         this.a = a;
            //         this.b = b;
            //         this.c = f();
            //     }

            // A backed property a locally-declared (not inherited from an interface) property that
            // does not have a locally-declared getter or setter.
            //
            // All others are abstract properties.
            // A backed property may mask an inherited abstract property.  That's a compiler error but
            // one that will be detected later.

            val classBodyFn = macroCall.childOrNull(macroCall.size - 1) as? FunTree
                ?: return@makeConstructor
            val classBody = classBodyFn.childOrNull(classBodyFn.size - 1) as? BlockTree
                ?: return@makeConstructor

            // First, build a list of the backed property declarations.
            // To do that, we need to know about getters/setters.
            val declarations = classBody.edges.mapNotNull {
                val t = lookThroughDecorations(it).target
                if (t is DeclTree) {
                    t to (t.parts ?: return@mapNotNull null)
                } else {
                    null
                }
            }
            val symbolsWithGettersSetters = mutableSetOf<Symbol>()
            declarations.forEach { (_, declParts) ->
                val metadata = declParts.metadataSymbolMap
                if (getterSymbol in metadata || setterSymbol in metadata) {
                    val edge = metadata[methodSymbol]
                    val propertySymbol = edge?.target?.symbolContained
                    if (propertySymbol != null) {
                        symbolsWithGettersSetters.add(propertySymbol)
                    }
                }
            }
            val backedPropertyAndNoPropertyDeclarations = declarations.mapNotNull { (declTree, declParts) ->
                val metadata = declParts.metadataSymbolMultimap
                val symbol = declParts.name.content.toSymbol()
                val neededForConstructor = when {
                    noPropertySymbol in metadata -> {
                        check(propertySymbol !in metadata) {
                            "property=${
                                declTree.toPseudoCode(singleLine = false)
                            }\n\nfrom\n\n${
                                macroEnv.call?.toPseudoCode(singleLine = false)
                            }"
                        }
                        true
                    }
                    propertySymbol in metadata -> {
                        constructorPropertySymbol in metadata ||
                            (symbol != null && symbol !in symbolsWithGettersSetters)
                    }
                    else -> false
                }
                if (neededForConstructor) {
                    declTree to declParts
                } else {
                    null
                }
            }

            val nameMaker = macroEnv.nameMaker
            val detachedTypeValue = Value(reifiedTypeFor(shape))

            val thisName = nameMaker.unusedSourceName(thisParsedName)
            val classBodyLeft = classBody.pos.leftEdge
            val constructorArgumentList = mutableListOf<Tree>(
                // Like all methods, constructors take `this` as positional argument 0
                DeclTree(
                    doc,
                    classBodyLeft,
                    listOf(
                        LeftNameLeaf(doc, classBodyLeft, thisName),
                        ValueLeaf(doc, classBodyLeft, vTypeSymbol),
                        ValueLeaf(doc, classBodyLeft, detachedTypeValue),
                        ValueLeaf(doc, classBodyLeft, vImpliedThisSymbol),
                        ValueLeaf(doc, classBodyLeft, detachedTypeValue),
                    ),
                ),
            )

            val constructorBodyParts = mutableListOf<Tree>()
            for ((declTree, declParts) in backedPropertyAndNoPropertyDeclarations) {
                // Splice out metadata that is no longer needed, like a default expression
                // so that we can use it in the constructor's argument list
                fun spliceOut(metadataKey: Symbol): Tree? =
                    declParts.metadataSymbolMap[metadataKey]?.let { edge ->
                        val edgeIndex = edge.edgeIndex
                        val tree = edge.target
                        declTree.removeChildren(edgeIndex - 1..edgeIndex)
                        tree
                    }

                if (noPropertySymbol in declParts.metadataSymbolMultimap) {
                    spliceOut(noPropertySymbol)
                    var edge = declTree.incoming!!
                    while (edge.source != classBody) {
                        edge = edge.source!!.incoming!!
                    }
                    constructorArgumentList.add(freeTarget(edge))
                    continue
                }

                val propNameLeaf = declParts.name
                val propNamePos = propNameLeaf.pos
                val propParsedName = when (val propName = propNameLeaf.content) {
                    is ParsedName -> propName
                    is ResolvedParsedName -> propName.baseName
                    is Temporary -> continue
                }
                val propertyNameSymbol = propParsedName.toSymbol()

                val initExpr: Tree?
                val isConstructorProperty = constructorPropertySymbol in declParts.metadataSymbolMultimap
                if (isConstructorProperty) {
                    val defaultExpr = spliceOut(defaultSymbol)
                    spliceOut(wordSymbol) // Moved to parameter
                    // Store any complex type expression in a temporary.
                    val type = run {
                        val typeEdge = declParts.type
                        when (val typeTree = typeEdge?.target) {
                            null -> null
                            is NameLeaf -> typeTree.copyRight()
                            else -> {
                                val value = typeTree.valueContained
                                if (value != null) {
                                    ValueLeaf(typeTree.document, typeTree.pos, value)
                                } else {
                                    val temporary = nameMaker.unusedTemporaryName(
                                        "typeof_${propertyNameSymbol.text}",
                                    )
                                    val typeEdgeIndex = typeEdge.edgeIndex
                                    val simpleTypeExpr = RightNameLeaf(doc, typeTree.pos, temporary)
                                    classBody.insert(typeEdgeIndex - 1) {
                                        Decl(typeTree.pos) {
                                            Replant(simpleTypeExpr.copyLeft())
                                            V(vInitSymbol)
                                            Replant(freeTarget(typeEdge))
                                        }
                                    }
                                    typeEdge.replace(simpleTypeExpr.copyRight())
                                    simpleTypeExpr
                                }
                            }
                        }
                    }
                    // Add an entry to the argument list.
                    constructorArgumentList.add(
                        DeclTree(
                            doc,
                            declTree.pos,
                            buildList {
                                add(LeftNameLeaf(doc, propNamePos, propParsedName))
                                add(ValueLeaf(doc, propNamePos.leftEdge, vWordSymbol))
                                add(ValueLeaf(doc, propNamePos, Value(propertyNameSymbol)))
                                if (type != null) {
                                    add(ValueLeaf(doc, type.pos.leftEdge, vTypeSymbol))
                                    add(type)
                                }
                                if (defaultExpr != null) {
                                    add(ValueLeaf(doc, defaultExpr.pos.leftEdge, vDefaultSymbol))
                                    add(defaultExpr)
                                }
                            },
                        ),
                    )

                    initExpr = RightNameLeaf(doc, propNamePos, propParsedName)
                } else {
                    initExpr = spliceOut(initSymbol)
                    if (initExpr == null) {
                        // Some wrappers that connect implicits types to Kotlin implementations
                        // of methods are initialized by bespoke code in DotHelper.
                        val isAllowedUninitialized = macroEnv.isProcessingImplicits &&
                            propertyNameSymbol.text == "content"
                        if (!isAllowedUninitialized) {
                            macroEnv.logSink.log(
                                Log.Error,
                                MessageTemplate.PropertyNotInitializedInConstructor,
                                propNamePos,
                                listOf(
                                    propNameLeaf.content,
                                ),
                            )
                        }
                        continue
                    }
                }
                // Add an assignment expression to the constructor body.
                constructorBodyParts.add(
                    doc.treeFarm.grow {
                        Call(declTree.pos) { // =
                            V(propNamePos.rightEdge, BuiltinFuns.vSetLocalFn)
                            Call(propNamePos) { // dot in this.property
                                Rn(propNamePos.leftEdge) { dotBuiltinName } // .
                                Call(propNamePos.leftEdge, BuiltinFuns.vThis) {
                                    V(propNamePos.leftEdge, detachedTypeValue)
                                }
                                V(propNamePos, propertyNameSymbol)
                            }
                            Replant(initExpr)
                        }
                    },
                )
            }
            if (constructorBodyParts.isNotEmpty()) {
                // Add a void rather than keep the last expression as the return value.
                constructorBodyParts.add(doc.treeFarm.grow { V(constructorBodyParts.last().pos, void) })
            }
            // Build a method declaration for the constructor.
            val constructorName = nameMaker.parsedName(constructorSymbol.text)!!
            val constructorPos = classBody.pos.leftEdge
            val constructorReturnName = nameMaker.unusedSourceName(returnParsedName)
            classBody.replace(classBody.size until classBody.size) {
                Decl(constructorPos, constructorName) {
                    V(vInitSymbol)
                    Fn {
                        constructorArgumentList.forEach { Replant(it) }
                        V(vWordSymbol)
                        V(vConstructorSymbol)
                        V(vReturnDeclSymbol)
                        Decl(constructorReturnName) {
                            V(vTypeSymbol)
                            V(Types.vVoid)
                        }
                        Block {
                            constructorBodyParts.forEach { Replant(it) }
                            // The return type above is explicitly Void, so we don't
                            // need an explicit void here.
                        }
                    }
                    V(vMethodSymbol)
                    V(vConstructorSymbol)
                    V(vVisibilitySymbol)
                    V(vPublicSymbol)
                }
            }
            shape.methods.add(
                MethodShape(
                    enclosingType = shape,
                    name = constructorName,
                    symbol = constructorSymbol,
                    stay = null,
                    visibility = Visibility.Public,
                    methodKind = MethodKind.Constructor,
                    openness = OpenOrClosed.Closed,
                ),
            )
        }
    }

    return result
}
