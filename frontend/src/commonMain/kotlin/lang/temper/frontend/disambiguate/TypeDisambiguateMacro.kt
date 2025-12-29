package lang.temper.frontend.disambiguate

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.builtin.isComplexArg
import lang.temper.builtin.isRemCall
import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.common.isNotEmpty
import lang.temper.env.Constness
import lang.temper.env.DeclarationBits
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.frontend.define.asFunctionLike
import lang.temper.frontend.define.initAsFunctionLike
import lang.temper.frontend.prefixBlockWith
import lang.temper.frontend.typestage.stayFor
import lang.temper.interp.convertToErrorNode
import lang.temper.interp.docgenalts.AltImpliedResultFn
import lang.temper.interp.errorNodeFor
import lang.temper.interp.importExport.ExportDecorator
import lang.temper.interp.isErrorNode
import lang.temper.interp.mightBeMacroCallMeantToHappenLater
import lang.temper.interp.vExtendsFn
import lang.temper.lexer.Genre
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ModularName
import lang.temper.name.ParsedName
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.Abstractness
import lang.temper.type.MutableTypeShape
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeParameterShape
import lang.temper.type.TypeShape
import lang.temper.type.TypeShapeImpl
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.MkType2
import lang.temper.type2.Type2
import lang.temper.type2.applyDotName
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclParts
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.InnerTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.MacroActuals
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.ReifiedType
import lang.temper.value.Result
import lang.temper.value.RightNameLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TSymbol
import lang.temper.value.TVoid
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.constructorPropertySymbol
import lang.temper.value.constructorSymbol
import lang.temper.value.extendsBuiltinName
import lang.temper.value.fnBuiltinName
import lang.temper.value.fnSymbol
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.getBuiltinName
import lang.temper.value.initSymbol
import lang.temper.value.isEmptyBlock
import lang.temper.value.letBuiltinName
import lang.temper.value.lookThroughDecorations
import lang.temper.value.memberTypeFormalSymbol
import lang.temper.value.methodSymbol
import lang.temper.value.nameContained
import lang.temper.value.noPropertySymbol
import lang.temper.value.outTypeSymbol
import lang.temper.value.propertySymbol
import lang.temper.value.reifiedTypeContained
import lang.temper.value.resolutionSymbol
import lang.temper.value.setBuiltinName
import lang.temper.value.staticPropertySymbol
import lang.temper.value.staticSymbol
import lang.temper.value.superSymbol
import lang.temper.value.symbolContained
import lang.temper.value.thisParsedName
import lang.temper.value.typeArgSymbol
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.typeFormalSymbol
import lang.temper.value.typeShapeAtLeafOrNull
import lang.temper.value.unpackUnappliedDecoration
import lang.temper.value.vConcreteSymbol
import lang.temper.value.vDefaultSymbol
import lang.temper.value.vFnSymbol
import lang.temper.value.vGetterSymbol
import lang.temper.value.vHoistLeftSymbol
import lang.temper.value.vImpliedThisSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vMaybeVarSymbol
import lang.temper.value.vMemberTypeFormalSymbol
import lang.temper.value.vMethodSymbol
import lang.temper.value.vOutTypeSymbol
import lang.temper.value.vPropertySymbol
import lang.temper.value.vResolutionSymbol
import lang.temper.value.vSetterSymbol
import lang.temper.value.vStaticPropertySymbol
import lang.temper.value.vStaySymbol
import lang.temper.value.vTypeDeclSymbol
import lang.temper.value.vTypeDefinedSymbol
import lang.temper.value.vTypeFormalSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.vVarSymbol
import lang.temper.value.vVarianceSymbol
import lang.temper.value.vWithinDocFoldSymbol
import lang.temper.value.vWordSymbol
import lang.temper.value.valueContained
import lang.temper.value.varSymbol
import lang.temper.value.void
import lang.temper.value.wordSymbol
import java.util.Collections

internal fun typeDisambiguateMacro(
    /** whether the definition is defining a concrete or abstract type. */
    abstractness: Abstractness,
    macroEnv: MacroEnvironment,
): Result {
    val doc = macroEnv.document
    val macroCall = macroEnv.call
        // We can't do much if this is not a macro call rooted in the AST
        ?: return Fail

    val genre = doc.context.genre
    fun valueLeaf(pos: Position, content: Value<*>) = ValueLeaf(doc, pos, content)
    var typeDefinedEdge: TEdge?
    var isBuildingWellKnownType = false
    val isBuildingMixin: Boolean
    val classValueFormals: List<Tree> // Appear inside parens: `class C(let x: Int) {...}
    val (classBodyAsLambda, typeShape, typeLeftName) = run {
        val args = macroEnv.args
        var classBodyIndex = args.size - 1
        var classBodyAsLambda: FunTree? = null
        if (classBodyIndex >= 0 && args.key(classBodyIndex) == null) {
            classBodyAsLambda = args.valueTree(classBodyIndex) as? FunTree
            if (classBodyAsLambda != null) {
                val classBodyLambdaParts = classBodyAsLambda.parts
                if (classBodyLambdaParts == null || classBodyLambdaParts.formals.isNotEmpty()) {
                    classBodyAsLambda = null
                }
            }
        }

        if (classBodyAsLambda == null && abstractness == Abstractness.Abstract) {
            // Convert lambda using abbreviated syntax to one that does not.
            //     interface TypeName(formals): ReturnType;
            // =>
            //     interface TypeName { let apply(formals): ReturnType; }
            // For this, we synthesize a class body.
            // This is especially important for @fun interfaces.
            var hasOutType = false
            val argsAfterWord = buildList {
                for (i in args.indices) {
                    when (args.key(i)) {
                        wordSymbol -> continue // So we can use it as a type name
                        outTypeSymbol -> hasOutType = true
                    }
                    args.keyTree(i)?.let { add(it) }
                    add(args.valueTree(i))
                }
            }
            if (hasOutType) {
                // If it looks like an abbreviate method, make it quack like one.
                val lambdaBody = doc.treeFarm.grow {
                    val bodyPos = argsAfterWord.spanningPosition(macroEnv.pos.rightEdge)
                    val leftPos = bodyPos.leftEdge
                    Fn(bodyPos) {
                        Block(bodyPos) {
                            Call(bodyPos) {
                                Rn(leftPos, letBuiltinName)
                                V(leftPos, wordSymbol)
                                Ln(leftPos, ParsedName(applyDotName.text))
                                for (afterWord in argsAfterWord) {
                                    afterWord.incoming!!.replace { V(void) }
                                    Replant(afterWord)
                                }
                            }
                        }
                    }
                }

                classBodyIndex += 1 // Don't skip the function tree
                macroCall.insert(at = macroCall.size) {
                    Replant(lambdaBody)
                }
                classBodyAsLambda = lambdaBody
            }
        }

        var typeNameSymbol: Symbol? = null
        var typeLeftName: LeftNameLeaf? = null
        for (i in args.indices) {
            when (args.key(i)) {
                wordSymbol -> {
                    val nameAndSymbol = when (val wordTree = args.valueTree(i)) {
                        is NameLeaf -> wordTree.copyLeft() to wordTree.content.toSymbol()
                        else -> null
                    }
                    if (nameAndSymbol != null) {
                        typeLeftName = nameAndSymbol.first
                        typeNameSymbol = nameAndSymbol.second
                    }
                    break
                }
            }
        }
        classValueFormals = buildList {
            for (i in 0 until classBodyIndex) {
                if (args.key(i) == null) {
                    val valEdge = args.valueTree(i).incoming!!
                    val undecoratedValTree = lookThroughDecorations(valEdge).target
                    if (
                        isComplexArg(undecoratedValTree) || undecoratedValTree is NameLeaf ||
                        // May come from mixin already as a declaration
                        undecoratedValTree is DeclTree
                    ) {
                        add(valEdge.target)
                    }
                }
            }
        }

        if (classBodyAsLambda == null) {
            macroEnv.failLog.fail(MessageTemplate.MalformedTypeDeclaration, macroEnv.pos)
            macroEnv.replaceMacroCallWithErrorNode()
            return@typeDisambiguateMacro Fail
        }

        // If this type definition is a mixin based on an existing, separately
        // staged type, then we will already have a TypeShape for it.
        typeDefinedEdge = classBodyAsLambda.parts?.metadataSymbolMap?.get(typeDefinedSymbol)

        val isExported = macroCall.incoming?.let { macroEdge ->
            var isExported = false
            forEachUnappliedDecoration(macroEdge) { _, decoratorCall ->
                val decorator = decoratorCall.childOrNull(0)
                val decoratorName = (decorator as? RightNameLeaf)?.content
                val value = decorator?.valueContained
                    ?: decoratorName?.let { macroEnv.environment[it, macroEnv] } as? Value<*>
                if (TFunction.unpackOrNull(value) == ExportDecorator) {
                    isExported = true
                    VisitCue.AllDone
                } else {
                    VisitCue.Continue
                }
            }
            isExported
        } ?: false

        val typeShape = run {
            var typeShape: TypeShapeImpl?
            // If this type declaration is a mixin, then it should already have \typeDefined metadata.
            typeShape = typeDefinedEdge?.target?.typeShapeAtLeafOrNull as? TypeShapeImpl
            isBuildingMixin = typeShape != null

            // If we're building the Implicits module, fill in a well-known type.
            if (typeShape == null && macroEnv.isProcessingImplicits && typeNameSymbol != null) {
                val wellKnown = WellKnownTypes.withName(BuiltinName(typeNameSymbol.text))
                isBuildingWellKnownType = wellKnown != null
                if (wellKnown is TypeShapeImpl) {
                    wellKnown.pos = macroEnv.pos
                    typeShape = wellKnown
                }
            }
            // If the type is exported, use an exported name.
            // If this is a mixin, the code generator might have allocated
            // a resolved name already but not a TypeShape, so use that.
            if (typeShape == null && typeLeftName != null) {
                val typeName = typeLeftName.content
                val preAllocatedName = when {
                    isExported && typeName is ParsedName ->
                        ExportDecorator.convertName(typeName, macroEnv.nameMaker)
                    typeName is ModularName -> typeName
                    else -> null
                }
                if (preAllocatedName != null) {
                    typeShape = TypeShapeImpl(
                        macroEnv.pos,
                        typeNameSymbol,
                        preAllocatedName,
                        abstractness,
                        doc.context.definitionMutationCounter,
                    )
                }
            }
            // Otherwise, generate a source name based on the type name symbol.
            if (typeShape == null) {
                typeShape = TypeShapeImpl(
                    macroEnv.pos,
                    typeNameSymbol,
                    doc.nameMaker,
                    abstractness,
                    doc.context.definitionMutationCounter,
                )
            }
            typeShape
        }

        // Add metadata to the class body function so that later stages can retrieve the
        // class definition.
        // Make sure the class body function has a way to return a value, and it connects the
        // environment in which the type is run to the constructor.
        //     fn { ... }
        // ->
        //     @typeDefined(...) fn { ... }
        val pos = classBodyAsLambda.pos.leftEdge
        val typeWrapper = reifiedTypeFor(typeShape)
        val beforeBody = classBodyAsLambda.size - 1
        if (beforeBody >= 0 && typeDefinedEdge == null) {
            classBodyAsLambda.replace(beforeBody until beforeBody) {
                V(pos, vTypeDefinedSymbol)
                V(pos, Value(typeWrapper))
            }
            // So that we can update the type wrapper with formal parameters later
            typeDefinedEdge = classBodyAsLambda.edge(classBodyAsLambda.size - 2)
        }

        Triple(classBodyAsLambda, typeShape, typeLeftName)
    }

    val typeName = typeShape.name

    val classBodyEdge = classBodyAsLambda.edge(classBodyAsLambda.size - 1)
    // Block elision means that `class C { oneMember }` has no block container for members.
    if (classBodyEdge.target !is BlockTree) {
        val loneMember = freeTarget(classBodyEdge)
        classBodyEdge.replace(
            BlockTree.wrap(loneMember),
        )
    }

    // Keep track of which property declarations are implicitly `const`
    // so that
    //    class C {
    //        p;
    //        var q;
    //    }
    // we treat `q` as settable because the explicit `var` contradicts `const`
    // but we treat `p` as possibly settable until we know there's an inherited setter by
    // adding @maybeVar metadata.
    // In the case where there is a setter, the property is abstract, so we can immediately
    // conclude the constness for p below:
    //    class C {
    //        p;
    //        set p(newP) { ... }
    //    }
    val maybeVarDecls = mutableMapOf<DeclTree, Symbol>()
    val needsThisParameter = mutableListOf<Either<CallTree, FunTree>>()
    val symbolsSet = mutableSetOf<Symbol>()
    // Classify each member.
    val classBody = classBodyEdge.target as BlockTree
    // But first limit to things that aren't just simple voids.
    // And avoid valueContained, because we exclude just put voids, not decorated ones.
    val nonVoids = classBody.children.filter { (it as? ValueLeaf)?.content != void }
    classBody.removeChildren(0 until classBody.size)
    // Move constructor properties into the class body.
    // Also, store them so we can differentiate properties where the `= expr` in
    // `propName: Type = expr` means a default expression for a constructor
    // parameter vs an initializer expression for the constructor body.
    if (classValueFormals.isNotEmpty()) {
        val children = macroCall.children.toList()
        macroCall.replace(macroCall.indices) {
            for (child in children) {
                if (child !in classValueFormals) {
                    Replant(child)
                }
            }
        }
        if (abstractness == Abstractness.Abstract) {
            for (classValueFormal in classValueFormals) {
                macroEnv.logSink.log(
                    Log.Error,
                    MessageTemplate.ConstructorArgumentInInterfaceType,
                    classValueFormal.pos,
                    emptyList(),
                )
            }
        } else {
            for (classValueFormal in classValueFormals) {
                classBody.add(classValueFormal)
                val argEdge = lookThroughDecorations(classValueFormal.incoming!!)
                formalizeArg(argEdge)
                val arg = argEdge.target
                if (arg is DeclTree) {
                    val leftPos = arg.pos.leftEdge
                    arg.insert(arg.size) {
                        V(leftPos, constructorPropertySymbol)
                        V(leftPos, void)
                    }
                    val symbol = arg.parts?.name?.content?.toSymbol()
                    if (symbol != null) {
                        maybeVarDecls[arg] = symbol
                    }
                }
            }
        }
    }
    for (nonVoid in nonVoids) {
        classBody.add(nonVoid)
    }
    for (possiblyDecoratedMemberEdge in classBody.edges) {
        val memberEdge = lookThroughDecorations(possiblyDecoratedMemberEdge)
        val memberTree = memberEdge.target
        if (memberTree.valueContained == void || isRemCall(memberTree)) {
            // A NOP or embedded comment
            continue
        }
        // The `static` keyword is converted to `@static` during token pre-processing.
        // The `@static` decorator only decorates a declaration, but we're in the process of
        // converting members to declarations, so there's a good chance that `@static` and other similar annotations
        // have not applied yet, so this is the wrapped annotations with the @s in the name
        val ancestorAnnotations = run {
            val results = mutableListOf<Pair<String, CallTree>>()

            forEachUnappliedDecoration(
                memberEdge,
                stopAt = possiblyDecoratedMemberEdge,
            ) { decoratorNameText, decoration ->
                results.add(Pair(decoratorNameText, decoration))
                VisitCue.Continue
            }
            Collections.unmodifiableList(results)
        }
        val staticAnnotation = ancestorAnnotations.firstOrNull { it.first == staticDecoratorNameText }
        val hasStaticAnnotation = staticAnnotation != null
        val hasVarAnnotation = ancestorAnnotations.any { it.first == varDecoratorNameText }
        val annotationsToRemove = mutableListOf<CallTree>()
        checkAgainstVirtualGenericMethod(abstractness, isStatic = hasStaticAnnotation, memberTree, macroEnv)
        val hasNoPropertyAnnotation =
            ancestorAnnotations.any { it.first == noPropertyNameText }
        when {
            memberTree is DeclTree && (
                hasNoPropertyAnnotation ||
                    memberTree.parts?.metadataSymbolMultimap?.contains(noPropertySymbol) == true
                ) -> {
                // @noProperty let x ...
                maybeVarDecls.remove(memberTree)
                // Will be collected into a constructor during SyntaxMacro stage.
            }
            memberTree is DeclTree -> {
                // let p = fn (...) { ... }
                // ->
                // @method(\p) let p(...) { ... }
                // and
                // let p ...
                // ->
                // @property(\p) let p ...
                // or
                // @staticProperty(\p) let p ...
                val declParts = memberTree.parts
                val name = declParts?.name

                if (name == null) {
                    convertToErrorNode(
                        memberEdge,
                        LogEntry(
                            Log.Error,
                            MessageTemplate.MalformedDeclaration,
                            memberTree.pos,
                            emptyList(),
                        ),
                    )
                } else if (!declParts.isTypeMember) {
                    val isConstructorProperty = constructorPropertySymbol in declParts.metadataSymbolMap
                    val isStatic = hasStaticAnnotation ||
                        // In this case, the @static annotation may have applied already.
                        staticSymbol in declParts.metadataSymbolMap
                    val isVar = hasVarAnnotation ||
                        varSymbol in declParts.metadataSymbolMap
                    val functionDeclared = memberTree.initAsFunctionLike
                    val isFunctionLike = functionDeclared != null && !isVar && !isConstructorProperty
                    // non-static properties may convert to constructor parameters
                    if (!isStatic && !isFunctionLike && isConstructorProperty) {
                        // Convert any initializer to a default expression.
                        // The initializer should never be run during partial evaluation, and it'd
                        // convert to a default expression when it's pulled into the constructor's
                        // argument list later anyway.
                        val initEdge = declParts.metadataSymbolMap[initSymbol]
                        if (initEdge != null) {
                            val initKeyEdge = memberTree.edge(initEdge.edgeIndex - 1)
                            initKeyEdge.replace { pos -> V(pos, vDefaultSymbol) }
                        }
                    }

                    // Add property metadata
                    // Symbol could be null if a pass or macro has introduced a temporary.
                    val symbol = name.content.toSymbol()
                    if (symbol == null || (isConstructorProperty && isStatic)) {
                        maybeVarDecls.remove(memberTree)
                        macroEnv.logSink.log(
                            Log.Error,
                            MessageTemplate.MalformedDeclaration, memberTree.pos, emptyList(),
                        )
                    } else {
                        val memberKind = when {
                            isStatic -> vStaticPropertySymbol
                            // static methods are static properties that store function values
                            // but instance methods are not instance properties in any way.
                            isFunctionLike -> vMethodSymbol
                            else -> vPropertySymbol
                        }

                        val pos = memberTree.pos.leftEdge
                        memberTree.insert(at = memberTree.size) {
                            V(pos, memberKind)
                            V(pos, name.content.toSymbol()!!)
                            if (isStatic && isFunctionLike) {
                                V(pos, fnSymbol)
                                V(pos, void)
                            }
                        }

                        if (!isStatic && functionDeclared != null) {
                            needsThisParameter.add(Either.Right(functionDeclared))
                        }
                    }
                }
            }
            memberTree is NameLeaf -> {
                // foo;
                // ->
                // @property(\foo) @maybeVar let foo;
                // or
                // @staticProperty(\foo) let foo;
                val pos = memberTree.pos
                val name = memberTree.content
                val symbol = name.toSymbol()
                if (symbol != null) {
                    val memberKind =
                        if (hasStaticAnnotation) { vStaticPropertySymbol } else { vPropertySymbol }
                    val decl = macroEnv.treeFarm.grow(pos) {
                        Decl(name) {
                            V(memberKind)
                            V(symbol)
                        }
                    }
                    memberEdge.replace(decl)
                    if (!hasStaticAnnotation) {
                        maybeVarDecls[decl] = symbol
                    }
                }
            }
            isCallToNameWithArity(memberTree, ":", 2) -> {
                // p : T
                // ->
                // @property(\p) @maybeVar let p: T;
                // or
                // @staticProperty(\p) let p: T;
                val nameTree = freeTarget(memberTree.edge(1))
                val pos = memberTree.pos
                if (nameTree is NameLeaf) {
                    val memberKind =
                        if (hasStaticAnnotation) { vStaticPropertySymbol } else { vPropertySymbol }
                    val name = nameTree.content
                    val type = freeTarget(memberTree.edge(2))
                    val symbol = name.toSymbol()!!
                    val decl = macroEnv.treeFarm.grow(pos) {
                        Decl {
                            Replant(nameTree.copyLeft())
                            V(type.pos.leftEdge, vTypeSymbol)
                            Replant(type)
                            V(pos, memberKind)
                            V(symbol)
                        }
                    }
                    if (!hasStaticAnnotation) {
                        maybeVarDecls[decl] = symbol
                    }
                    memberEdge.replace(decl)
                } else {
                    convertToErrorNode(
                        memberEdge,
                        LogEntry(
                            Log.Error,
                            MessageTemplate.MalformedDeclaration,
                            pos,
                            emptyList(),
                        ),
                    )
                }
            }
            isCallToNameWithArity(memberTree, "=", 2) -> {
                // p = x
                // ->
                // @property(\p) @maybeVar let p = x;
                // or
                // @staticProperty(\p) let p = x;
                val edge1 = memberTree.edge(1)
                val child1 = edge1.target
                val (name, type) = if (isCallToNameWithArity(child1, ":", 2)) {
                    freeTarget(child1.edge(1)) to freeTarget(child1.edge(2))
                } else {
                    freeTarget(edge1) to null
                }
                val pos = memberTree.pos
                if (name is NameLeaf) {
                    val memberKind =
                        if (hasStaticAnnotation) { vStaticPropertySymbol } else { vPropertySymbol }
                    val isFunctionLike = asFunctionLike(memberTree.edge(2)) != null
                    val initExpr = freeTarget(memberTree.edge(2))
                    val symbol = name.content.toSymbol()!!
                    val decl = macroEnv.treeFarm.grow(pos) {
                        Decl(pos) {
                            Replant(name.copyLeft())
                            if (type != null) {
                                V(type.pos.leftEdge, vTypeSymbol)
                                Replant(type)
                            }
                            V(initExpr.pos.leftEdge, vInitSymbol)
                            Replant(initExpr)
                            V(pos.leftEdge, memberKind)
                            V(pos, symbol)
                            if (hasStaticAnnotation && isFunctionLike) {
                                V(pos.leftEdge, vFnSymbol)
                                V(pos.leftEdge, void)
                            }
                        }
                    }
                    if (!hasStaticAnnotation) {
                        maybeVarDecls[decl] = symbol
                    }
                    memberEdge.replace(decl)
                } else {
                    convertToErrorNode(
                        memberEdge,
                        LogEntry(Log.Error, MessageTemplate.MalformedDeclaration, pos, emptyList()),
                    )
                }
            }
            memberTree is CallTree && memberTree.childOrNull(0) is RightNameLeaf -> {
                val name = memberTree.child(0) as RightNameLeaf
                var defaultOutType: TemperName? = null

                @Suppress("MagicNumber") // 3 is min for [callee, \word symbol, word]
                val nameIsFollowedByWord =
                    memberTree.size >= 3 && memberTree.child(1).symbolContained == wordSymbol
                val extraMetadata = mutableListOf<Tree>()
                val (leftName, memberNameSymbol) = if (
                    nameIsFollowedByWord && memberTree.child(2) !is NameLeaf
                ) {
                    convertToErrorNode(
                        memberEdge,
                        LogEntry(
                            Log.Error,
                            MessageTemplate.MalformedDeclaration,
                            memberTree.pos,
                            emptyList(),
                        ),
                    )
                    null to null
                } else if (nameIsFollowedByWord && name.content.builtinKey == letBuiltinName.builtinKey) {
                    // If the function being called is the builtin `let` macro, which will not
                    // be expanded to a proper declaration until later, then
                    // let foo(...)...
                    // ->
                    // @method(\foo) let foo = fn foo(...)...
                    // or
                    // @methodLike @staticProperty(\foo) let foo = fn foo(...)...
                    val letCalleeEdge = memberTree.edge(0)
                    val wordName = memberTree.child(2) as NameLeaf
                    val letCallee = letCalleeEdge.target
                    // let(...) -> fn(...)
                    letCalleeEdge.replace {
                        Rn(letCallee.pos, fnBuiltinName)
                    }
                    if (!hasStaticAnnotation) {
                        needsThisParameter.add(Either.Left(memberTree))
                    }
                    wordName.copyLeft() to wordName.content.toSymbol()!!
                } else if (
                    nameIsFollowedByWord &&
                    (
                        name.content.builtinKey == getBuiltinName.builtinKey ||
                            name.content.builtinKey == setBuiltinName.builtinKey
                        )
                ) {
                    if (hasStaticAnnotation) { TODO("static computed property declaration") }
                    // get p() {...}
                    // ->
                    // let `get.p` = fn `get.p`() { ... }
                    val nameContent = name.content.builtinKey // get or set
                    val isSetter = nameContent == setBuiltinName.builtinKey
                    val wordNameEdge = memberTree.edge(2)
                    val wordName = wordNameEdge.target as NameLeaf
                    val wordNameContent = wordName.content
                    if (wordNameContent is ParsedName) {
                        val mangledMethodNameText =
                            "$nameContent.${wordNameContent.nameText}"
                        val letCalleeEdge = memberTree.edge(0)
                        // get p(...)... -> fn p(...)...
                        letCalleeEdge.replace {
                            Rn(letCalleeEdge.target.pos, fnBuiltinName)
                        }
                        // fn p(...)... -> fn `get.p`(...)...
                        wordNameEdge.replace {
                            Rn(letCalleeEdge.target.pos) {
                                it.parsedName(mangledMethodNameText)!!
                            }
                        }
                        extraMetadata.add(
                            valueLeaf(
                                name.pos,
                                if (isSetter) {
                                    vSetterSymbol
                                } else {
                                    vGetterSymbol
                                },
                            ),
                        )
                        extraMetadata.add(valueLeaf(name.pos.rightEdge, void))
                        needsThisParameter.add(Either.Left(memberTree))
                        // Return the name for the containing @method declaration below
                        val propertySymbol = wordNameContent.toSymbol()
                        if (isSetter) {
                            symbolsSet.add(propertySymbol)
                            defaultOutType = TVoid.name
                        }
                        macroEnv.treeFarm.grow {
                            Ln(wordName.pos) { it.parsedName(mangledMethodNameText)!! }
                        } to propertySymbol
                    } else {
                        convertToErrorNode(
                            memberEdge,
                            LogEntry(
                                Log.Error,
                                MessageTemplate.MalformedTypeMember,
                                memberTree.pos,
                                emptyList(),
                            ),
                        )
                        null to null
                    }
                } else {
                    // f() { ... }
                    // ->
                    // @method(\f) let f = fn f() { ... }
                    // or
                    // @methodLike @staticProperty(\f) let f = fn f() { ... }
                    val pos = memberTree.pos
                    // f() { ... } -> fn f() { ... }
                    memberTree.replace(atBeginning) {
                        Rn(pos.leftEdge, fnBuiltinName)
                        V(name.pos.leftEdge, vWordSymbol)
                    }
                    if (!hasStaticAnnotation) {
                        needsThisParameter.add(Either.Left(memberTree))
                    }
                    name.copyLeft() to name.content.toSymbol()!!
                }
                if (leftName != null) {
                    val pos = memberTree.pos
                    freeTarget(memberEdge)
                    // f() { ... } -> let f = f() { ... }
                    memberEdge.replace(
                        macroEnv.treeFarm.grow(pos) {
                            Decl {
                                Replant(leftName)
                                V(vInitSymbol)
                                Replant(memberTree)
                                if (hasStaticAnnotation) {
                                    V(vStaticPropertySymbol)
                                    V(memberNameSymbol!!)
                                    V(fnSymbol)
                                    V(void)
                                } else {
                                    V(vMethodSymbol)
                                    V(memberNameSymbol!!)
                                }
                                extraMetadata.forEach { Replant(it) }
                            }
                        },
                    )
                }
                // If the method has no function at the end, then mark it pure virtual.
                //     word()
                // ->
                //     fn(\word, word)
                // ->
                //     fn(\word, word, fn { pureVirtual() })
                // The `fn` macro requires a body to be able to convert it to a function.
                if (memberTree.children.lastOrNull() !is FunTree) {
                    // no trailing block
                    memberTree.add(
                        memberTree.treeFarm.grow(memberTree.pos.rightEdge) {
                            Fn {
                                Call { V(BuiltinFuns.vPureVirtual) }
                            }
                        },
                    )
                }
                // Add a default outType if available and needed.
                if (defaultOutType == null && memberNameSymbol == constructorSymbol) {
                    defaultOutType = TVoid.name
                }
                if (defaultOutType != null && memberTree.children.none { it.symbolContained == outTypeSymbol }) {
                    // We're just adding a trailing fun tree above if there wasn't before, so go in before that.
                    val index = memberTree.size - 1
                    memberTree.replace(index until index) {
                        V(vOutTypeSymbol)
                        Rn(defaultOutType)
                    }
                }
            }
            // Leave macro calls that might need to run in context and already recognized
            // problems alone
            isErrorNode(memberTree) -> Unit
            mightBeMacroCallMeantToHappenLater(memberTree) -> Unit
            else -> {
                convertToErrorNode(
                    memberEdge,
                    LogEntry(
                        Log.Error,
                        MessageTemplate.MalformedTypeMember,
                        memberTree.pos,
                        emptyList(),
                    ),
                )
            }
        }
        // Pull any annotations that got processed here from the ancestry
        annotationsToRemove.forEach { annotation ->
            run {
                forEachUnappliedDecoration(
                    memberEdge,
                    stopAt = possiblyDecoratedMemberEdge,
                ) { _, decoration ->
                    if (decoration == annotation) {
                        val replacement = decoration.children.last()
                        decoration.incoming!!.replace(freeTree(replacement))
                        VisitCue.AllDone
                    } else {
                        VisitCue.Continue
                    }
                }
            }
        }
    }

    // Now that we've processed all the members, we can mark properties write-once
    for ((decl, symbol) in maybeVarDecls) {
        val dp = decl.parts
        val hasVar = dp != null && varSymbol in dp.metadataSymbolMap
        if (!hasVar) {
            val constnessSymbol = if (symbol in symbolsSet) { vVarSymbol } else { vMaybeVarSymbol }
            val pos = decl.pos.leftEdge
            decl.add(newChild = ValueLeaf(doc, pos, constnessSymbol))
            decl.add(newChild = ValueLeaf(doc, pos, void))
        }
    }

    val predefinedFormalDecls = if (isBuildingWellKnownType) {
        typeShape.formals
    } else {
        null
    }
    // Pull type formals into body alongside other members.
    //     class C<T> { ... }  ->  class C { @typeFormal @typeDefined(T__0) let T = ...; ... }
    // Since each type formal also is a TypeDefinition, we allocate a type name.
    val formalDecls = mutableListOf<DeclTree>()
    val formalEffects = mutableListOf<CallTree>()
    val errorNodes = mutableListOf<Tree>()
    val reifiedFormals = mutableListOf<Pair<Position, ReifiedType>>()
    for ((key, keyIndex) in callSymbolPairsMutating(macroCall)) {
        if (key == typeArgSymbol) {
            val nextEdge = macroCall.edge(keyIndex + 1)
            val next = nextEdge.target
            val formalPos = next.pos
            // Splice out formal
            macroCall.removeChildren(keyIndex..keyIndex + 1)
            // Reconstitute as a declaration
            val formalDeclChildren = mutableListOf<Tree>()
            var formalName: Tree = next // This may be a lie
            val upperBounds = mutableListOf<Tree>()
            var variance = Variance.Default
            var hasUpperBound = false
            // Look for patterns like
            // 1. @out Name                           // Parsed from `out name` via TypeArgumentName
            // 2. @in  Name
            // 3.      Name extends TypeExpression
            formalNameLoop@
            while (formalName is CallTree) {
                val callee = formalName.child(0) as? RightNameLeaf ?: break
                val builtinKey = callee.content.builtinKey
                when (builtinKey to formalName.size) {
                    "@in" to 2, "@out" to 2 -> {
                        variance = if (builtinKey == "@out") {
                            Variance.Covariant
                        } else {
                            Variance.Contravariant
                        }
                        val varianceValue = Value(variance.sign, TInt)
                        formalDeclChildren.add(valueLeaf(callee.pos, vVarianceSymbol))
                        formalDeclChildren.add(valueLeaf(callee.pos, varianceValue))
                        formalName = freeTarget(formalName.edge(1))
                    }
                    ("extends" to BINARY_OP_CALL_ARG_COUNT),
                    ("implements" to BINARY_OP_CALL_ARG_COUNT),
                    -> {
                        upperBounds.add(freeTarget(formalName.edge(2)))
                        hasUpperBound = true
                        formalName = freeTarget(formalName.edge(1))
                    }
                    else -> break@formalNameLoop
                }
            }
            val formalSymbol = (formalName as? NameLeaf)?.content?.toSymbol()
            if (formalSymbol == null) {
                // We've spliced out the formal, so no need to put an error node somewhere if
                // we're going to have the AST be authoritative w.r.t. errors.
                errorNodes.add(
                    errorNodeFor(
                        nextEdge.target,
                        LogEntry(
                            Log.Error,
                            MessageTemplate.MalformedDeclaration,
                            formalPos,
                            emptyList(),
                        ),
                    ),
                )
            } else {
                @Suppress("USELESS_IS_CHECK")
                check(formalName is NameLeaf)
                val predefinedFormalDefinition = predefinedFormalDecls?.first {
                    it.word == formalSymbol
                }
                val stableFormalName = predefinedFormalDefinition?.name
                    ?: doc.nameMaker.unusedSourceName(ParsedName(formalSymbol.text))
                val formalDefinition = when {
                    predefinedFormalDefinition != null -> {
                        if (predefinedFormalDefinition.variance != variance) {
                            macroEnv.logSink.log(
                                Log.Error,
                                MessageTemplate.InternalInterpreterError,
                                formalPos,
                                listOf(
                                    "WellKnownTypes $stableFormalName variance out of sync with Implicits.temper",
                                ),
                            )
                        }
                        predefinedFormalDefinition
                    }
                    else -> {
                        TypeFormal(
                            formalPos,
                            stableFormalName,
                            formalSymbol,
                            variance,
                            doc.context.definitionMutationCounter,
                            upperBounds = if (hasUpperBound) {
                                emptyList() // Filled in later by `extends` macro
                            } else {
                                // The lack of an extends clause defaults to AnyValue.
                                // We can't always include AnyValue because it's ok to have an upper
                                // bound like `Bubble` which is disjoint from AnyValue.
                                listOf(WellKnownTypes.anyValueType)
                            },
                        )
                    }
                }
                val reifiedFormal = reifiedTypeFor(formalDefinition)
                formalDeclChildren.subList(0, 0).addAll(
                    macroEnv.treeFarm.growAll(formalPos) {
                        Replant(formalName.copyLeft())
                        V(formalPos, vTypeFormalSymbol)
                        V(formalPos, formalSymbol)
                        V(vMemberTypeFormalSymbol)
                        V(formalSymbol)
                        V(vTypeDefinedSymbol)
                        V(Value(reifiedFormal))
                        V(vResolutionSymbol)
                        Ln(formalDefinition.name)
                        V(vInitSymbol)
                        V(formalPos, Value(reifiedFormal))
                        if (genre == Genre.Documentation) {
                            V(vWithinDocFoldSymbol)
                            V(void)
                        }
                    },
                )
                formalDecls.add(DeclTree(doc, formalPos, formalDeclChildren))
                reifiedFormals.add(formalPos to reifiedFormal)
                if (predefinedFormalDefinition == null) {
                    typeShape.typeParameters.add(
                        TypeParameterShape(typeShape, formalDefinition, formalSymbol, null),
                    )
                }
                for (upperBound in upperBounds) {
                    val upperBoundPos = upperBound.pos
                    formalEffects.add(
                        CallTree(
                            doc,
                            upperBoundPos,
                            listOf(
                                valueLeaf(upperBoundPos, vExtendsFn),
                                formalName.copyRight(),
                                upperBound,
                            ),
                        ),
                    )
                }
            }
        }
    }

    // Now that we've got type parameters, we can form a full type.
    val reifiedType = reifiedTypeFor(typeShape)

    // Pull super types into body.
    // We need these to come after the formal type declarations so that super type expressions can
    // refer to formals as in
    //    class Sub<T> extends Base<T>
    // Each
    //    extends Super // aka \super, Super
    // becomes, in the body
    //    extends(Sub, Super)
    // where `extends` is a BuiltinName.
    val superCalls = buildList {
        for ((key, keyIndex) in callSymbolPairsMutating(macroCall)) {
            if (key == superSymbol) {
                val keyEdge = macroCall.edge(keyIndex)
                val nextEdge = macroCall.edge(keyIndex + 1)
                val superTypeExpr = nextEdge.target
                val superTypePos = superTypeExpr.pos
                // Splice out supertype
                macroCall.removeChildren(keyIndex..keyIndex + 1)
                // Generate `extends(reifiedType, typeExpression)
                add(
                    macroEnv.treeFarm.grow {
                        Call(superTypePos) {
                            V(keyEdge.target.pos, vExtendsFn)
                            V(superTypePos.leftEdge, Value(reifiedType))
                            Replant(superTypeExpr)
                        }
                    },
                )
            }
        }
        // Adding AnyValue as a super-type when there are no others.
        // This has the effect of making sure that all declared types have AnyType as a super-type
        // either directly or transitively except when the type declaration is broken because it is
        // its own super-type as in `class C extends C`.
        if (this.isEmpty()) {
            // Don't add AnyValue as a super type to interface AnyValue or to
            // mixins which have their own super-types.
            val extendAnyValue = !isBuildingMixin && (
                !macroEnv.isProcessingImplicits || typeShape !in extendsAnyValueExempt
                )
            if (extendAnyValue) {
                add(
                    macroEnv.treeFarm.grow {
                        Call(macroEnv.pos.leftEdge) {
                            V(vExtendsFn)
                            V(Value(reifiedType))
                            V(Value(Types.anyValue))
                        }
                    },
                )
            }
        }
    }

    // Add `this` parameters to methods.
    for (member in needsThisParameter) {
        addThisParameter(member, reifiedType)
    }

    // We need an expression for the result of a type definition.
    val typeExpr = doc.treeFarm.grow(classBody.pos.leftEdge) {
        V(
            classBody.pos.leftEdge,
            Value(
                ReifiedType(
                    MkType2(typeShape).get(),
                    hasExplicitActuals = false,
                ),
            ),
        )
    }
    // Put the formal declarations before the glue so that its type expression can refer to them.
    prefixBlockWith(superCalls, classBody)
    prefixBlockWith(formalEffects, classBody)
    prefixBlockWith(formalDecls, classBody)

    // Add metadata that indicates whether the type is concrete or abstract.
    macroCall.insert(macroCall.size - 1) {
        V(macroEnv.callee.pos, vConcreteSymbol)
        V(macroEnv.callee.pos, TBoolean.value(abstractness == Abstractness.Concrete))
    }

    macroEnv.declareLocal(
        macroEnv.treeFarm.grow {
            Ln(classBody.pos.leftEdge) { typeName }
        },
        DeclarationBits(
            reifiedType = null,
            initial = Value(reifiedType),
            constness = Constness.Const,
            missing = ReferentBitSet.empty,
            referentSource = ReferentSource.SingleSourceAssigned,
            declarationSite = macroEnv.pos,
        ),
    )

    // If defining C<T>, earlier we added metadata like
    //     \typeDefined (C)
    // Now that we've allocated type shapes for formal parameters, rewrite that to
    //     \typeDefined (C<T>)
    val internalReifiedType = Value(reifiedType)
    typeDefinedEdge?.run {
        replace(ValueLeaf(doc, target.pos, internalReifiedType))
    }

    // Mark uses of `this` in the class body legit.
    TreeVisit
        .startingAt(classBody)
        .forEach {
            if (it is CallTree && it.size == 1) {
                val callee = it.child(0)
                if (callee.functionContained == BuiltinFuns.thisPlaceholder) {
                    // This prevents the `this` macro from collapsing to an error.
                    it.add(newChild = valueLeaf(it.pos.rightEdge, internalReifiedType))
                    // TODO: Once we define type identifiers, this will point to the type
                    // identifier of the innermost enclosing class body.
                }
            }
            VisitCue.Continue
        }
        .visitPreOrder()

    // Make sure that the result of a class resolves to a super-positioned constructor/type.
    //
    // NAMED TYPE DEFINITION IN STATEMENT POSITION
    // ===========================================
    // In
    //     { class C {}; let c: C = new C(); ... }
    // we have a class definition in statement position.
    // In that block, *C* is used both to refer to the type and the constructor.
    // So we turn `class C {};` into
    //     let C = /* ReifiedType C */;
    //     class C {}
    //     C           // If the class is in result position, the constructor is the result.
    //
    // NAMED TYPE DEFINITION IN EXPRESSION POSITION
    // ============================================
    // If the constructor has a name, it may use that name internally to refer to itself as in
    //     let obj: I = new (class C extends I { f(): C { ... } })()
    // In that case, we do the above but wrap it in a block so the name does not "bleed" into
    // the containing scope.
    //
    // ANONYMOUS TYPE DEFINITION
    // =========================
    // If the constructor is anonymous, we still need to return a constructor value, so we
    // convert
    //     let obj = new (class {})()
    // into
    //     let obj = new ({ class {}; __constructor__(C__0) })()
    val breadcrumb = macroCall.incoming?.breadcrumb
    val typeCallEdgeIncludingAnnotations = run {
        // Look upwards from the call through any unprocessed annotations.
        // so in `@A @B class ...` we want the edge to `(Call @ A ...)`
        var edge = macroEnv.call?.incoming
        while (edge != null) {
            val parent = edge.source
            if (parent is CallTree && isLexicallyDecoration(parent, edge)) {
                edge = parent.incoming
            } else {
                break
            }
        }
        edge ?: return@typeDisambiguateMacro Fail
    }
    val typeDecl = if (typeLeftName == null) {
        // ANONYMOUS TYPE DEFINITION
        val constructorTemporary = macroEnv.nameMaker.unusedTemporaryName("t")
        val decl = macroEnv.treeFarm.grow {
            Decl(macroEnv.pos, constructorTemporary) {
                V(vInitSymbol)
                Replant(typeExpr)
                V(vTypeDeclSymbol)
                V(internalReifiedType)
                V(vStaySymbol)
                Stay()
                if (genre == Genre.Documentation) {
                    V(vWithinDocFoldSymbol)
                    V(void)
                }
            }
        }
        val callEdge = macroCall.incoming!!
        val freeCall = freeTarget(callEdge)
        val freeCallInclAnnotation = freeTarget(typeCallEdgeIncludingAnnotations)
        val edgeIndexToReplace = typeCallEdgeIncludingAnnotations.edgeIndex
        typeCallEdgeIncludingAnnotations.source!!
            .replace(edgeIndexToReplace..edgeIndexToReplace) {
                if (typeCallEdgeIncludingAnnotations == callEdge) {
                    Replant(decl)
                } else {
                    // Replace the call with the declaration, so that annotations on the
                    // class apply to a declaration
                    callEdge.replace(decl)
                    Replant(freeCallInclAnnotation)
                }
                Replant(freeCall)
                if (genre == Genre.Documentation) {
                    Call(AltImpliedResultFn) {
                        Rn(constructorTemporary)
                    }
                } else {
                    Rn(constructorTemporary)
                }
            }
        decl
    } else {
        val decl = macroEnv.treeFarm.grow {
            Decl(typeLeftName.pos) {
                Replant(typeLeftName.copyLeft())
                V(vInitSymbol)
                Replant(typeExpr)
                V(vTypeDeclSymbol)
                V(internalReifiedType)
                V(vHoistLeftSymbol)
                V(TBoolean.valueTrue) // Hoist initializer
                V(resolutionSymbol) // Resolve C to the type shape ID: C__0
                Ln(typeName)
                V(vStaySymbol)
                Stay()
            }
        }
        val source = typeCallEdgeIncludingAnnotations.source
        val callEdge = macroCall.incoming!!
        val freeCall = freeTarget(callEdge)
        val freeCallInclAnnotation = if (callEdge == typeCallEdgeIncludingAnnotations) {
            null
        } else {
            freeTarget(typeCallEdgeIncludingAnnotations)
        }
        if (source is BlockTree && source.flow is LinearFlow) {
            // NAMED TYPE DEFINITION IN STATEMENT POSITION
            callEdge.replace(decl)
            val afterPositionInSource = typeCallEdgeIncludingAnnotations.edgeIndex + 1
            val isInResultPosition = afterPositionInSource == source.size
            source.insert(afterPositionInSource) {
                if (freeCallInclAnnotation != null) {
                    Replant(freeCallInclAnnotation)
                }
                Replant(freeCall)
                if (isInResultPosition) {
                    if (genre == Genre.Documentation) {
                        Call(AltImpliedResultFn) {
                            Replant(typeLeftName.copyRight())
                        }
                    } else {
                        Replant(typeLeftName.copyRight())
                    }
                }
            }
        } else {
            // NAMED TYPE DEFINITION IN EXPRESSION POSITION
            typeCallEdgeIncludingAnnotations.replace {
                BlockS {
                    if (freeCallInclAnnotation == null) {
                        Replant(decl)
                    } else {
                        callEdge.replace(decl)
                        Replant(freeCallInclAnnotation)
                    }
                    Replant(freeCall)
                    Replant(typeLeftName.copyRight())
                }
            }
        }
        // The source.insert path above is nice in that it simply
        // gets things in the right order, and in position for the
        // interpreter to expand macros.
        // But it can leave things like:
        //     REM("doc string", true);
        //     {} // Used to be typeCallEdgeIncludingAnnotation
        //     @SomeUnappliedDecoration(let TypeName = ...);
        // which separates the embedded comment from the type declaration.
        //
        // We swap those first two edge targets to make sure that doesn't
        // happen.
        // TODO: this is a hack.  making decorations into barnacles instead
        // of wrapping calls would simplify a lot including obviating this.
        if (
            typeCallEdgeIncludingAnnotations.target.isEmptyBlock() &&
            typeCallEdgeIncludingAnnotations.source == source &&
            source is BlockTree && source.flow is LinearFlow
        ) {
            val blankBlockIndex = typeCallEdgeIncludingAnnotations.edgeIndex
            val precedingBlankBlock = source.edgeOrNull(blankBlockIndex - 1)
            if (precedingBlankBlock != null && isRemCall(precedingBlankBlock.target)) {
                typeCallEdgeIncludingAnnotations.replace(freeTarget(precedingBlankBlock))
            }
        }
        decl
    }
    val modifiedCallEdge = macroCall.incoming
    if (modifiedCallEdge != null) {
        modifiedCallEdge.breadcrumb = breadcrumb
    }

    // Store the stay with the type declaration
    val stay = stayFor(typeDecl)
    typeShape.stayLeaf = stay

    return Value(reifiedType)
}

private fun checkAgainstVirtualGenericMethod(
    abstractness: Abstractness,
    isStatic: Boolean,
    memberTree: Tree,
    macroEnv: MacroEnvironment,
) {
    // Class instance methods and static methods are allowed to be generic.
    abstractness == Abstractness.Concrete && return
    isStatic && return
    // Grandfather in generic interface methods from builtins/core/implicits for now.
    // TODO Stop allowing generic interface methods in builtins.
    macroEnv.isProcessingImplicits && return
    // Check against type parameters.
    for (index in 0 until memberTree.size) {
        val kid = memberTree.child(index)
        if (kid.symbolContained == typeArgSymbol) {
            // Dig out the type parameter name for better distinction in error messages.
            // TODO Move this whole validation to somewhere where things have already been worked out in advance?
            val name = when (val next = memberTree.childOrNull(index + 1)) {
                is NameLeaf -> next.content.displayName
                is CallTree -> when (next.childOrNull(0)?.nameContained?.builtinKey) {
                    extendsBuiltinName.builtinKey -> next.childOrNull(1)?.nameContained?.displayName
                    else -> null
                }
                else -> null
            } ?: "?"
            macroEnv.logSink.log(Log.Error, MessageTemplate.TypeParameterInInterfaceMethod, kid.pos, listOf(name))
        }
    }
}

private fun addThisParameter(
    callOrFn: Either<CallTree, FunTree>,
    reifiedType: ReifiedType,
) {
    var index: Int
    val member: InnerTree
    when (callOrFn) {
        is Either.Left -> { // A call like (let \word methodName(arg)
            member = callOrFn.item
            // Find index at which to insert parameter 0
            index = 1 // Skip callee
            while (index + 1 < member.size) {
                val child = member.child(index)
                // Scan forward over metadata pairs that should precede inputs.
                val precedesValueFormals = when (child.symbolContained) {
                    // `f<T>(x: T)` has symbol pairs like
                    // \word f  \typeArg T
                    // and if there is an explicit `let` macro use, we might get \typeFormal T
                    wordSymbol, typeFormalSymbol, typeArgSymbol -> true
                    else -> false
                }
                if (precedesValueFormals) {
                    index += 2
                } else {
                    break
                }
            }
        }
        is Either.Right -> {
            member = callOrFn.item
            index = 0
        }
    }
    if (index > member.size) { return }

    val doc = member.document

    // There may already be an explicit receiver parameter.  People may want to annotate it.
    //    method(@Foo this, ...)
    val thisFormalEdge: TEdge = run findThis@{
        var atReceiverPos = member.edgeOrNull(index)
        if (atReceiverPos != null) {
            // Look through unprocessed annotations
            atReceiverPos = lookThroughDecorations(atReceiverPos)
            // If it's a call to `this` then convert it to a formal.
            val treeAtReceiverPos = atReceiverPos.target
            val callee = (treeAtReceiverPos as? CallTree)?.childOrNull(0)
            if (callee?.functionContained == BuiltinFuns.thisPlaceholder) {
                return@findThis atReceiverPos
            }
        }
        // Allocate a `this` name and a formal parameter declaration.
        val pos = member.childOrNull(index)?.pos?.leftEdge ?: member.pos.rightEdge
        member.add(index, ValueLeaf(doc, pos, void))
        member.edge(index)
    }
    val detachedTypeValue = Value(reifiedType)
    // Turn it into a declaration
    val pos = thisFormalEdge.target.pos
    val rightPos = pos.rightEdge
    thisFormalEdge.replace(
        DeclTree(
            doc,
            pos,
            listOf(
                LeftNameLeaf(doc, pos, doc.nameMaker.unusedSourceName(thisParsedName)),
                ValueLeaf(doc, rightPos, vTypeSymbol),
                ValueLeaf(doc, rightPos, detachedTypeValue),
                ValueLeaf(doc, rightPos, vImpliedThisSymbol),
                ValueLeaf(doc, rightPos, detachedTypeValue),
            ),
        ),
    )
}

private fun isCallToNameWithArity(
    tree: Tree,
    nameText: String,
    arity: Int,
): Boolean {
    if (tree is CallTree && tree.size == arity + 1) {
        val callee = tree.child(0)
        val name = (callee as? RightNameLeaf)?.content
        return name?.builtinKey == nameText
    }
    return false
}

/** Same as `0 until 0` but does not set off detekt.  Used to do insertion via range replace */
internal val atBeginning = IntRange(0, -1)

/**
 * Assuming the macro call is like
 *
 *     class(\word, word, @typeDefined(Word__1234) fn { ... })
 *
 * returns the shape associated with `Word__1234`
 *
 * Allocates a new type id and attaches it to the call if there is none, or it is not valid.
 *
 * If the type definition is malformed, logs that and converts it to an error node.
 */
internal fun getTypeShapeForCallToTypeMacro(
    /** An environment for a call to a type definition macro. */
    env: MacroEnvironment,
): MutableTypeShape? {
    val typeShape = getTypeShapeForCallToTypeMacroBestEffort(env.args)
    if (typeShape == null) {
        val problem = LogEntry(Log.Error, MessageTemplate.MalformedTypeDeclaration, env.pos, listOf())
        problem.logTo(env.logSink)
        env.replaceMacroCallWithErrorNode(problem)
    }
    return typeShape
}

/** Like [getTypeShapeForCallToTypeMacro] but does not convert a malformed call to an error node. */
private fun getTypeShapeForCallToTypeMacroBestEffort(
    args: MacroActuals,
): MutableTypeShape? {
    val bodyIndex = args.lastIndex
    if (bodyIndex < 0) {
        return null
    }

    val classBodyFn = args.valueTree(bodyIndex) as? FunTree ?: return null
    val classBodyFnParts = classBodyFn.parts ?: return null
    val typeDefinedEdge = classBodyFnParts.metadataSymbolMap[typeDefinedSymbol]

    val reifiedType = typeDefinedEdge?.target?.reifiedTypeContained
    return (reifiedType?.type2 as? DefinedNonNullType)?.definition as? MutableTypeShape
}

internal fun callSymbolPairsMutating(
    call: CallTree,
    startIndex: Int = 1,
) = object : Iterable<Pair<Symbol, Int>> {
    override fun iterator() = object : Iterator<Pair<Symbol, Int>> {
        private var argIndexGuess = startIndex
        private var nextEdge: TEdge? = call.edge(startIndex)

        private fun symbolIndexPair(): Pair<Symbol, Int>? {
            val ne = nextEdge ?: return null
            val keySymbol = ne.target.symbolContained ?: return null
            val keyIndex = when (ne) {
                call.edgeOrNull(argIndexGuess) -> argIndexGuess
                call.edgeOrNull(argIndexGuess + 2) -> argIndexGuess + 2
                else -> ne.edgeIndex
            }
            return if (keyIndex >= 0) {
                keySymbol to keyIndex
            } else {
                null
            }
        }

        override fun hasNext(): Boolean {
            return symbolIndexPair() != null
        }

        override fun next(): Pair<Symbol, Int> {
            val p = symbolIndexPair() ?: throw NoSuchElementException()
            argIndexGuess = p.second + 2
            nextEdge = call.edgeOrNull(argIndexGuess)
            return p
        }
    }
}

internal class SymbolPairsNonMutating(
    private val parent: InnerTree,
    private val startIndex: Int,
    private val limit: Int? = null,
) : Iterable<Pair<Symbol?, Int>> {
    override fun iterator(): Iterator<Pair<Symbol?, Int>> = object : Iterator<Pair<Symbol, Int>> {
        private val limit = this@SymbolPairsNonMutating.limit ?: parent.size
        private var i = startIndex

        override fun hasNext(): Boolean = peek() != null

        override fun next(): Pair<Symbol, Int> {
            val pair = peek() ?: throw NoSuchElementException()
            i += 2
            return pair
        }

        private fun peek(): Pair<Symbol, Int>? {
            if (i + 1 >= limit) { return null }
            val symTree = parent.child(i)
            val symValue = symTree.valueContained(TSymbol) ?: return null
            return symValue to i + 1
        }
    }
}

internal fun reifiedTypeFor(
    typeDefinition: TypeDefinition,
): ReifiedType {
    val cache = mutableMapOf<TypeDefinition, Type2>()
    fun getStaticType(typeDefinition: TypeDefinition): Type2 {
        val extant = cache[typeDefinition]
        if (extant != null) {
            return extant
        }
        cache[typeDefinition] = WellKnownTypes.invalidType2
        return when (typeDefinition) {
            is TypeShape -> {
                val actuals = typeDefinition.formals.map { getStaticType(it) }
                MkType2(typeDefinition).actuals(actuals)
            }
            is TypeFormal -> MkType2(typeDefinition)
        }.get().also {
            cache[typeDefinition] = it
        }
    }
    // This cast is safe because getStaticType is guaranteed to return a NominalType when its input
    // is not initially in the cache, and cache is empty before the first call.
    val type = getStaticType(typeDefinition)
    // Also, this function is used for cases where we want explicit type actuals from the type formals.
    return ReifiedType(type, hasExplicitActuals = type.bindings.isNotEmpty())
}

/** True if the given call is lexically a decoration call and decorates the given edge. */
internal fun isLexicallyDecoration(call: CallTree, decorated: TEdge): Boolean {
    val lastChildIndex = call.size - 1
    if (lastChildIndex <= 0 || decorated.edgeIndex == 0) {
        // The decorated cannot be the decoration function so its index must be >= 1
        return false
    }
    val callee = call.child(0)
    val nameTextOrEmpty = when (val name = (callee as? RightNameLeaf)?.content) {
        null, is Temporary, is SourceName -> ""
        is ParsedName -> name.nameText
        is BuiltinName -> name.builtinKey
        is ExportedName -> name.baseName.nameText
    }
    return nameTextOrEmpty.startsWith("@")
}

internal val (DeclParts).isTypeMember: Boolean get() {
    val metadata = this.metadataSymbolMultimap
    return when {
        methodSymbol in metadata -> true
        propertySymbol in metadata -> true
        staticPropertySymbol in metadata -> true
        memberTypeFormalSymbol in metadata -> true
        else -> false
    }
}

private val staticDecoratorNameText = "@${staticSymbol.text}"
private val varDecoratorNameText = "@${varSymbol.text}"
private val noPropertyNameText = "@${noPropertySymbol.text}"

fun forEachUnappliedDecoration(
    decorated: TEdge,
    stopAt: TEdge? = null,
    f: (String, CallTree) -> VisitCue,
) {
    var anc = decorated
    while (true) {
        val parentEdge = anc.source?.incoming ?: break
        val parentTree = parentEdge.target
        if (parentTree !is CallTree) { break }
        val decoratorNameAndDecoratedIndex = unpackUnappliedDecoration(parentEdge)
        if (decoratorNameAndDecoratedIndex != null) {
            val (decoratorNameText, decoratedIndex) = decoratorNameAndDecoratedIndex
            if (decoratedIndex == anc.edgeIndex) {
                when (f(decoratorNameText, parentTree)) {
                    VisitCue.Continue, VisitCue.SkipOne -> Unit
                    VisitCue.AllDone -> break
                }
            }
        }
        if (anc == stopAt) { break }
        anc = parentEdge
    }
}

private val extendsAnyValueExempt = setOf(
    WellKnownTypes.anyValueTypeDefinition,
    WellKnownTypes.resultTypeDefinition,
    WellKnownTypes.invalidTypeDefinition,
    WellKnownTypes.voidTypeDefinition,
)
