package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.common.OpenOrClosed
import lang.temper.common.mapFirst
import lang.temper.common.partitionByType
import lang.temper.common.soleElement
import lang.temper.env.Constness
import lang.temper.env.DeclarationBits
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.frontend.disambiguate.callSymbolPairsMutating
import lang.temper.frontend.disambiguate.getTypeShapeForCallToTypeMacro
import lang.temper.frontend.disambiguate.reifiedTypeFor
import lang.temper.frontend.prefixBlockWith
import lang.temper.frontend.syntax.rewriteFun
import lang.temper.interp.convertToErrorNode
import lang.temper.interp.isErrorNode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.Abstractness
import lang.temper.type.DotHelper
import lang.temper.type.DotMember
import lang.temper.type.InternalSet
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.PropertyShape
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclParts
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.FnParts
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
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
import lang.temper.value.fnBuiltinName
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.getterSymbol
import lang.temper.value.impliedThisSymbol
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
    val typeValue = Value(reifiedTypeFor(shape))

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

    val classBodyFn = macroCall.childOrNull(macroCall.size - 1) as? FunTree
    val classBody = classBodyFn?.childOrNull(classBodyFn.size - 1) as? BlockTree

    val cpInfo = if (isConcrete && classBody != null) {
        findConstructorsAndProperties(classBody, shape, macroEnv)
    } else {
        ConstructorsAndProperties(listOf())
    }

    val nConstructors = cpInfo.parts.count { it is ConstructorsAndProperties.ConstructorInfo }
    if (isConcrete && nConstructors == 0 && classBody != null) {
        run makeConstructor@{
            // Given backed properties where the set of constructor parameters is (a, b)
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

            // A backed property a locally declared (not inherited from an interface) property that
            // does not have a locally declared getter or setter.
            //
            // All others are abstract properties.
            // A backed property may mask an inherited abstract property.  That's a compiler error but
            // one that will be detected later.

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
            for (p in cpInfo.parts) {
                val declTree: DeclTree
                val declParts: DeclParts
                val isConstructorInput: Boolean
                val isProperty: Boolean
                when (p) {
                    is ConstructorsAndProperties.ConstructorInfo -> error("count from above")
                    is ConstructorsAndProperties.BackedPropertyInfo -> {
                        declTree = p.tree
                        declParts = p.parts
                        isConstructorInput = p.isConstructorProperty
                        isProperty = true
                    }
                    is ConstructorsAndProperties.NoPropertyArgInfo -> {
                        declTree = p.tree
                        declParts = p.parts
                        isConstructorInput = true
                        isProperty = false
                    }
                }

                fun spliceOut(metadataKey: Symbol): Tree? =
                    spliceOut(metadataKey, declParts, declTree)

                if (!isProperty) {
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
                if (isConstructorInput) {
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
                                    val typeInsertionPoint = run {
                                        var e: TEdge = typeEdge
                                        while (e.source != classBody) {
                                            e = e.source!!.incoming!!
                                        }
                                        e.edgeIndex
                                    }
                                    val simpleTypeExpr = RightNameLeaf(doc, typeTree.pos, temporary)
                                    classBody.insert(typeInsertionPoint) {
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
                        // Some wrappers that connect core types to Kotlin implementations
                        // of methods are initialized by bespoke code in DotHelper.
                        val isAllowedUninitialized = macroEnv.isProcessingCore &&
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
            classBody.insert(classBody.size) {
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
    } else if (nConstructors != 0) {
        // Check for incompatibilities between backed property declarations
        // and constructors:
        // - Parenthesized constructor inputs when there is an explicit constructor
        //   TODO: could we just add input declarations to the end?
        // - Properties with initializers when there are multiple constructors.
        //   If a property is declared with an initializer like `private let prop = veryLargeExpression()`
        //   we can't easily move that initializer into each constructors by transforming it into
        //   `this.prop = veryLargeExpression()` because that would involve copying code trees which
        //   we do not do unless they are known to be very small.
        val (constructors, otherParts) = cpInfo.parts.partitionByType<
            ConstructorsAndProperties.PartInfo,
            ConstructorsAndProperties.ConstructorInfo,
            ConstructorsAndProperties.NonConstructorInfo,
            >()
        val aConstructorPos = constructors.first().declParts.name.pos
        val initializersToAdopt = mutableListOf<Pair<PropertyShape, Tree>>()
        for (part in otherParts) {
            var error: LogEntry? = null
            when (part) {
                is ConstructorsAndProperties.BackedPropertyInfo -> {
                    if (part.isConstructorProperty) {
                        error = LogEntry(
                            MessageTemplate.ExplicitConstructorIncompatibleWithInput,
                            part.tree.pos,
                            listOf(part.parts.name.content, aConstructorPos),
                        )
                    } else {
                        val init = part.parts.metadataSymbolMap[initSymbol]
                        if (init != null) {
                            if (nConstructors == 1) {
                                // Fold it into the constructor.
                                initializersToAdopt.add(part.propertyShape to init.target)
                                spliceOut(initSymbol, part.parts, part.tree)
                            } else {
                                error = LogEntry(
                                    MessageTemplate.MultipleConstructorsIncompatibleWIthInitializer,
                                    part.tree.pos,
                                    listOf(part.parts.name.content, constructors.map { it.declParts.name.pos }),
                                )
                            }
                        }
                    }
                }
                is ConstructorsAndProperties.NoPropertyArgInfo -> {
                    error = LogEntry(
                        MessageTemplate.ExplicitConstructorIncompatibleWithInput,
                        part.tree.pos,
                        listOf(part.parts.name.content, aConstructorPos),
                    )
                }
            }
            if (error != null) {
                error.logTo(macroEnv.logSink)
                convertToErrorNode(part.tree.incoming!!, error)
            }
        }
        if (initializersToAdopt.isNotEmpty()) {
            val ctor = constructors.soleElement!!
            val thisName = ctor.parts.formals.first { impliedThisSymbol in it.parts!!.metadataSymbolMap }
                .parts!!.name
            var body = ctor.parts.body
            if (body !is BlockTree) {
                val bodyEdge = body.incoming!!
                bodyEdge.replace { pos ->
                    Block(pos) {
                        Replant(freeTree(body))
                    }
                }
                body = bodyEdge.target as BlockTree
            }
            prefixBlockWith(
                body.document.treeFarm.growAll(body.pos.leftEdge) {
                    initializersToAdopt.forEach { (propertyShape, initializer) ->
                        Call(DotHelper(InternalSet, DotMember(propertyShape.symbol))) {
                            V(typeValue)
                            Replant(thisName.copyRight())
                            Replant(initializer)
                        }
                    }
                    V(void)
                },
                body,
            )
        }
    }

    return result
}

/**
 * Collects information about property declarations and constructors to simplify both
 * generating a constructor when none was explicitly declared and folding property
 * initializers and default expressions into an existing constructor.
 */
private data class ConstructorsAndProperties(
    val parts: List<PartInfo>,
) {
    sealed class PartInfo

    data class ConstructorInfo(
        val declTree: DeclTree,
        val declParts: DeclParts,
        val tree: FunTree,
        val parts: FnParts,
        val methodShape: MethodShape,
    ) : PartInfo() {
        override fun toString() = "ConstructorInfo(${methodShape.name}, `${tree.toPseudoCode()}`)"
    }

    sealed class NonConstructorInfo : PartInfo() {
        abstract val tree: DeclTree
        abstract val parts: DeclParts
    }

    data class NoPropertyArgInfo(
        override val tree: DeclTree,
        override val parts: DeclParts,
    ) : NonConstructorInfo() {
        override fun toString() = "NoPropertyInfo(`${tree.toPseudoCode()}`)"
    }

    data class BackedPropertyInfo(
        override val tree: DeclTree,
        override val parts: DeclParts,
        val isConstructorProperty: Boolean,
        val propertyShape: PropertyShape,
    ) : NonConstructorInfo() {
        override fun toString() =
            "BackedPropertyInfo(${propertyShape.name}${
                if (isConstructorProperty) { ", isConstructorProperty" } else { "" }
            }, `${tree.toPseudoCode()}`)"
    }
}

private fun findConstructorsAndProperties(
    classBody: BlockTree,
    typeShape: TypeShape,
    macroEnv: MacroEnvironment,
): ConstructorsAndProperties {
    val parts = mutableListOf<ConstructorsAndProperties.PartInfo>()

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

    for ((decl, declParts) in declarations) {
        val metadata = declParts.metadataSymbolMap
        val name = declParts.name.content
        if (methodSymbol in metadata) {
            val symbol = metadata.getValue(methodSymbol).symbolContained
            if (symbol == constructorSymbol) {
                val initializer = metadata[initSymbol]?.let {
                    lookThroughDecorations(it)
                }
                if (initializer?.target is CallTree) {
                    // Expand `fn` macro calls.
                    val call = initializer.target as CallTree
                    val callee = call.childOrNull(0)?.functionContained
                    if (callee is NamedBuiltinFun && callee.name == fnBuiltinName.builtinKey) {
                        when (val rewritten = rewriteFun(call, isDeclaration = false)) {
                            is Either.Left<Tree> -> initializer.replace(rewritten.item)
                            is Either.Right<LogEntry> -> rewritten.item.logTo(macroEnv.logSink)
                        }
                    }
                }
                val fn = initializer?.target as? FunTree
                val shape = typeShape.members.firstOrNull { it is MethodShape && it.name == name }
                    as MethodShape?
                val fnParts = fn?.parts
                if (fnParts != null && shape != null) {
                    parts.add(
                        ConstructorsAndProperties.ConstructorInfo(
                            decl, declParts,
                            fn, fnParts, shape,
                        ),
                    )
                }
            }
        } else if (noPropertySymbol in metadata) {
            check(propertySymbol !in metadata) {
                "property=${
                    decl.toPseudoCode(singleLine = false)
                }\n\nfrom\n\n${
                    classBody.toPseudoCode(singleLine = false)
                }"
            }
            parts.add(
                ConstructorsAndProperties.NoPropertyArgInfo(decl, declParts),
            )
        } else if (propertySymbol in metadata) {
            val symbol = metadata.getValue(propertySymbol).symbolContained
            val propertyShape = typeShape.properties.firstOrNull { it.symbol == symbol && it.name == name }
            if (propertyShape?.abstractness == Abstractness.Concrete && symbol !in symbolsWithGettersSetters) {
                val isConstructorProperty = constructorPropertySymbol in metadata
                parts.add(
                    ConstructorsAndProperties.BackedPropertyInfo(
                        decl, declParts,
                        isConstructorProperty = isConstructorProperty,
                        propertyShape = propertyShape,
                    ),
                )
            }
        }
    }

    return ConstructorsAndProperties(parts.toList())
}

/**
 * Splice out metadata which is no longer needed, like a default expression
 * so that we can repurpose the declaration or part of it.
 */
private fun spliceOut(metadataKey: Symbol, declParts: DeclParts, declTree: DeclTree): Tree? =
    declParts.metadataSymbolMap[metadataKey]?.let { edge ->
        val edgeIndex = edge.edgeIndex
        val tree = edge.target
        declTree.removeChildren(edgeIndex - 1..edgeIndex)
        tree
    }
