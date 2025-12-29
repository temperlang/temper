package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.builtin.isRemCall
import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.common.OpenOrClosed
import lang.temper.common.asciiTitleCase
import lang.temper.common.putMulti
import lang.temper.common.subListToEnd
import lang.temper.env.InterpMode
import lang.temper.frontend.disambiguate.getTypeShapeForCallToTypeMacro
import lang.temper.frontend.disambiguate.reifiedTypeFor
import lang.temper.frontend.maybeExtractIntoTemporary
import lang.temper.interp.convertToErrorNode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.type.Abstractness
import lang.temper.type.ExtraNonNormativeParameterInfo
import lang.temper.type.MemberShape
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.PropertyShape
import lang.temper.type.StaticPropertyShape
import lang.temper.type.TypeFormal
import lang.temper.type.Visibility
import lang.temper.type2.applyDotName
import lang.temper.value.BlockTree
import lang.temper.value.DeclParts
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.LinearFlow
import lang.temper.value.MacroEnvironment
import lang.temper.value.MetadataMap
import lang.temper.value.MetadataMultimap
import lang.temper.value.NameLeaf
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.ReifiedType
import lang.temper.value.StayLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TSymbol
import lang.temper.value.TreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.concreteSymbol
import lang.temper.value.constructorSymbol
import lang.temper.value.defaultSymbol
import lang.temper.value.functionalInterfaceSymbol
import lang.temper.value.getterSymbol
import lang.temper.value.impliedThisSymbol
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.memberTypeFormalSymbol
import lang.temper.value.methodSymbol
import lang.temper.value.propertySymbol
import lang.temper.value.reifiedTypeContained
import lang.temper.value.returnDeclSymbol
import lang.temper.value.returnParsedName
import lang.temper.value.setterSymbol
import lang.temper.value.staticPropertySymbol
import lang.temper.value.staySymbol
import lang.temper.value.symbolContained
import lang.temper.value.thisParsedName
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.typeFormalSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.vPropertySymbol
import lang.temper.value.vStaySymbol
import lang.temper.value.vVisibilitySymbol
import lang.temper.value.valueContained
import lang.temper.value.varSymbol
import lang.temper.value.visibilitySymbol
import lang.temper.value.void
import lang.temper.value.wordSymbol

private typealias DefineImpliedMethod =
    ((Symbol, (Planting).() -> TreeTemplate<DeclTree>) -> Pair<TEdge, DeclParts>)

internal fun typeShapeMacro(macroEnv: MacroEnvironment): PartialResult {
    val args = macroEnv.args
    val typeShape = getTypeShapeForCallToTypeMacro(macroEnv) ?: return Fail

    if (macroEnv.stage == Stage.after(Stage.SyntaxMacro)) {
        // After the end of the syntax macro stage, we throw out any records with ParsedNames
        typeShape.properties.removeAll { it.name is ParsedName }
        typeShape.methods.removeAll { it.name is ParsedName }
        typeShape.staticProperties.removeAll { it.name is ParsedName }
        typeShape.typeParameters.removeAll { it.name is ParsedName }
    }

    val bodyIndex = args.size - 1

    // Concrete -> values exist of the type that are not values of a sub-type?
    var typeAbstractness = Abstractness.Abstract
    // Open -> may be overridden in a sub-type.
    var memberOpenness = OpenOrClosed.Open
    var sawName = false
    val typeDefinedAsValue = Value(reifiedTypeFor(typeShape))
    for (i in 0 until bodyIndex) {
        val symbol = args.key(i) ?: break
        val tree = args.valueTree(i)
        when (symbol) {
            wordSymbol ->
                // Initialize to allow for recursive reference as in
                //     class C extends I<C>
                if (!sawName) {
                    val nameLeaf = tree as? NameLeaf
                    if (nameLeaf != null) {
                        macroEnv.setLocal(
                            nameLeaf.copyLeft(),
                            typeDefinedAsValue,
                        )
                    }
                    sawName = true
                }
            concreteSymbol -> {
                typeAbstractness = when (tree.valueContained(TBoolean)) {
                    true -> Abstractness.Concrete
                    else -> Abstractness.Abstract
                }
                memberOpenness = when (typeAbstractness) {
                    Abstractness.Abstract -> OpenOrClosed.Open
                    Abstractness.Concrete -> OpenOrClosed.Closed
                }
            }
        }
    }
    val defaultVisibility = Visibility.Public
    val isFunctionInterface = functionalInterfaceSymbol in typeShape.metadata

    // Temper, for TypeScript compatibility, spreads property definitions across
    // multiple declarations.
    //
    //     class C {
    //         x: Int;                       // A type
    //         get x() { x + 123 }           // A getter
    //         set x(newX: Int) { x = newX } // A setter
    //     }
    //
    // Group class body declarations by (memberKind, memberSymbol) so that we can consider all
    // parts of a property declaration at once.  memberKind is one of the symbols: property,
    // method, typeFormal, etc.
    val typeFormalDeclsGrouped =
        mutableMapOf<Symbol, MutableList<Pair<TEdge, DeclParts>>>()
    val propertyDeclsGrouped =
        mutableMapOf<Symbol, MutableList<Pair<TEdge, DeclParts>>>()
    val methodDeclsGrouped =
        mutableMapOf<Symbol, MutableList<Pair<TEdge, DeclParts>>>()
    val staticPropertyDeclsGrouped =
        mutableMapOf<Symbol, MutableList<Pair<TEdge, DeclParts>>>()

    var defineImpliedMethod: DefineImpliedMethod? =
        null

    // Look at declarations in the class body.
    val body: BlockTree? =
        if (bodyIndex >= 0 && args.key(bodyIndex) == null) {
            val bodyFn = args.valueTree(bodyIndex) as? FunTree
            bodyFn?.childOrNull(bodyFn.size - 1) as? BlockTree
        } else {
            null
        }
    if (body != null) {
        defineImpliedMethod = { symbol, defineDecl ->
            val decl = body.treeFarm.grow {
                defineDecl()
            }
            require(body.flow is LinearFlow)
            body.replace(body.size until body.size) {
                Replant(decl)
            }
            val pair = decl.incoming!! to decl.parts!!
            methodDeclsGrouped.putMulti(symbol, pair) {
                mutableListOf()
            }
            pair
        }

        // Evaluate loose declarations in InterpMode so that we capture any assignments
        // to temporaries needed to inline shared type expressions
        // like typeof__123 temporaries introduced by TypeSyntaxMacro.
        // Below we'll evaluate the type formal declarations, so that those are in scope
        // for any type declarations, and then evaluate these.
        //
        //     @typeFormal let T__0 = (type T__0);
        //     let typeofSomeProperty#0;
        //     typeofSomeProperty#0 = List<T__0?>;
        //
        // Then, when macroEnv.evaluateEdge is applied to a tree like (RName typeofSomeProperty#0)
        // we get a useful result.
        val needPreevaluation = mutableListOf<TEdge>()

        for (possiblyDecoratedEdge in body.edges) {
            val edge = lookThroughDecorations(possiblyDecoratedEdge)
            val child = edge.target
            if (child !is DeclTree) {
                needPreevaluation.add(edge)
                continue
            }
            val declParts = child.parts ?: continue
            val metadata = declParts.metadataSymbolMap
            val (groupMap, symbolKey) = when {
                typeFormalSymbol in metadata && memberTypeFormalSymbol in metadata ->
                    typeFormalDeclsGrouped to typeFormalSymbol
                propertySymbol in metadata -> propertyDeclsGrouped to propertySymbol
                methodSymbol in metadata ->
                    (
                        when {
                            getterSymbol in metadata || setterSymbol in metadata ->
                                propertyDeclsGrouped
                            else -> methodDeclsGrouped
                        }
                        ) to methodSymbol
                staticPropertySymbol in metadata ->
                    staticPropertyDeclsGrouped to staticPropertySymbol
                else -> {
                    needPreevaluation.add(edge)
                    continue
                }
            }
            val symbol = metadata.symbolValue(symbolKey)
            if (symbol == null) {
                convertToErrorNode(edge)
                macroEnv.failLog.fail(MessageTemplate.MalformedDeclaration, child.pos)
                continue
            }
            groupMap.getOrPut(symbol) { mutableListOf() }.add(edge to declParts)
        }

        // See comment on needPreevaluation above
        for (formalGroup in typeFormalDeclsGrouped.values) {
            for ((formalEdge) in formalGroup) {
                macroEnv.evaluateEdge(formalEdge, InterpMode.Partial)
            }
        }
        for (edge in needPreevaluation) {
            macroEnv.evaluateEdge(edge, InterpMode.Partial)
        }
    }

    for ((_, sameNameDefs) in typeFormalDeclsGrouped.entries) {
        if (sameNameDefs.size != 1) {
            invalidateConflictingDefinitions(macroEnv, sameNameDefs)
            continue
        }
        val (edge, declParts) = sameNameDefs[0]
        val metadata = declParts.metadataSymbolMultimap

        val formalDefinition = metadata.typeDefined()
        if (formalDefinition !is TypeFormal) {
            val cause = LogEntry(
                Log.Error,
                MessageTemplate.MalformedDeclaration,
                edge.target.pos,
                emptyList(),
            )
            cause.logTo(macroEnv.logSink)
            convertToErrorNode(edge, cause)
        }
    }

    for ((symbol, sameNameDefs) in propertyDeclsGrouped.entries) {
        // We should have at most one of each of these, and each def should be one of these:
        var propDef: Pair<TEdge, DeclParts>? = null // Defines the type
        var getter: Pair<TEdge, DeclParts>? = null // Method definition for a getter
        var setter: Pair<TEdge, DeclParts>? = null // Method definition for a setter
        for (pair in sameNameDefs) {
            val (edge, declParts) = pair
            val metadata = declParts.metadataSymbolMap
            val pos = edge.target.pos
            if (propertySymbol in metadata) {
                if (propDef != null) {
                    invalidateSubsequent(macroEnv, propDef.first, edge)
                } else {
                    propDef = pair
                }
            } else if (getterSymbol in metadata) {
                if (getter != null) {
                    invalidateSubsequent(macroEnv, getter.first, edge)
                } else {
                    getter = pair
                }
            } else if (setterSymbol in metadata) {
                if (setter != null) {
                    invalidateSubsequent(macroEnv, setter.first, edge)
                } else {
                    setter = pair
                }
            } else {
                macroEnv.failLog.fail(MessageTemplate.MalformedDeclaration, pos)
                convertToErrorNode(edge)
            }
        }

        // Derive visibilities for the getter and setter so that we can use that to inform the
        // abstract property definition.
        var getterVisibility =
            visibilityFor(getter?.second, macroEnv, defaultVisibility) {
                if (it != null) { convertToErrorNode(it) } // Need it now or not at all
                null
            }
        var setterVisibility =
            visibilityFor(setter?.second, macroEnv, defaultVisibility) {
                if (it != null) { convertToErrorNode(it) } // Need it now or not at all
                null
            }

        if (propDef == null) {
            // Insert a property definition before the earliest getter/setter
            val propertyVisibility: Visibility = when {
                getterVisibility == null -> setterVisibility ?: defaultVisibility
                setterVisibility == null -> getterVisibility
                else -> if (getterVisibility > setterVisibility) {
                    getterVisibility
                } else {
                    setterVisibility
                }
            }

            val edge0 = sameNameDefs[0].first
            val pos = edge0.target.pos.leftEdge
            val decl = macroEnv.treeFarm.grow(pos) {
                Decl {
                    Ln { it.unusedSourceName(ParsedName(symbol.text)) }
                    V(vPropertySymbol)
                    V(symbol)
                    V(visibilitySymbol)
                    V(propertyVisibility.toSymbol())
                }
            }
            run {
                // Insert property implied by getter &| setter.
                var insertBefore: TEdge = edge0
                while (body != null && insertBefore.source != body) {
                    insertBefore = insertBefore.source!!.incoming!!
                }
                var insertionPoint = insertBefore.edgeIndex
                val parent = insertBefore.source!!
                // Do not separate getter/setter at edge0 from its doc string.
                while (insertionPoint > 0 && isRemCall(parent.child(insertionPoint - 1))) {
                    insertionPoint -= 1
                }
                parent.add(insertionPoint, decl)
            }

            propDef = decl.incoming!! to decl.parts!!

            // If either the getter or the setter are using the type's default visibility,
            // make that explicit so the pass below, which infers getter/setter visibility
            // from property visibility doesn't override.
            if (getter != null && getterVisibility == null) {
                getterVisibility = defaultVisibility
                addVisibilityAnnotationTo(getter.first.target as DeclTree, getterVisibility)
            }
            if (setter != null && setterVisibility == null) {
                setterVisibility = defaultVisibility
                addVisibilityAnnotationTo(setter.first.target as DeclTree, setterVisibility)
            }
        }
        val memberName = propDef.second.name.content

        // If we've already decided this property is backed, then keep that decision.  This allows
        // adding public getters/setters to expose a public&backed property.
        val propertyAbstractness =
            typeShape.properties.firstOrNull { it.name == memberName }
                ?.abstractness
                ?: if (
                    typeAbstractness == Abstractness.Concrete && getter == null && setter == null
                ) {
                    Abstractness.Concrete
                } else {
                    Abstractness.Abstract
                }

        val visibility = visibilityFor(
            propDef.second,
            macroEnv,
            defaultVisibility = defaultVisibility,
        )
        val propStay = stayFor(propDef)

        // Infer getters and setters for public&backed properties.
        // This allows backends to use `private` properties for the storage of backed property's
        // values internally, and to channel external access through getters/setters.
        if (
            macroEnv.stage == Stage.Define && propertyAbstractness == Abstractness.Concrete &&
            visibility == Visibility.Public && defineImpliedMethod != null
        ) {
            // TODO: maybe capture property type in a temporary if not collapsed to a value yet
            val plantPropertyType = propDef.second.type?.let { propertyTypeEdge ->
                val propertyTypeTree = propertyTypeEdge.target
                val propertyTypePos = propertyTypeTree.pos
                val typeValue = macroEnv.evaluateEdge(propertyTypeEdge, InterpMode.Partial)

                val extractedPropertyType = if (typeValue is Value<*>) {
                    Either.Left(typeValue)
                } else {
                    maybeExtractIntoTemporary(
                        propertyTypeEdge,
                        treeFollower = propDef.first,
                    )
                }

                fun plantPropertyType(p: Planting) {
                    when (extractedPropertyType) {
                        is Either.Left -> p.V(propertyTypePos, extractedPropertyType.item)
                        is Either.Right -> p.Rn(propertyTypePos, extractedPropertyType.item)
                    }
                }

                ::plantPropertyType
            }
            if (getter == null) {
                getter = defineImpliedGetterOrSetter(
                    macroEnv,
                    symbol,
                    defineImpliedMethod,
                    propDef,
                    typeDefinedAsValue,
                    plantPropertyType,
                    isGetter = true,
                )
                sameNameDefs.add(getter)
            }
            if (setter == null && varSymbol in propDef.second.metadataSymbolMap) {
                setter = defineImpliedGetterOrSetter(
                    macroEnv,
                    symbol,
                    defineImpliedMethod,
                    propDef,
                    typeDefinedAsValue,
                    plantPropertyType,
                    isGetter = false,
                )
                sameNameDefs.add(setter)
            }
        }

        addOrReplaceNamed(
            typeShape.properties,
            PropertyShape(
                typeShape,
                memberName,
                symbol,
                propStay,
                visibility,
                abstractness = propertyAbstractness,
                getter = getter?.second?.name?.content,
                setter = setter?.second?.name?.content,
                hasSetter = setter != null || varSymbol in propDef.second.metadataSymbolMap,
            ),
        )

        for (accessor in listOfNotNull(getter, setter)) {
            var accessorVisibility = if (accessor == getter) getterVisibility else setterVisibility
            if (accessorVisibility == null) {
                accessorVisibility = visibility
                addVisibilityAnnotationTo(
                    accessor.first.target as DeclTree,
                    accessorVisibility,
                )
            }
            val stay = stayFor(accessor)
            val methodKind = if (accessor == getter) {
                MethodKind.Getter
            } else {
                MethodKind.Setter
            }
            val name = accessor.second.name.content
            val methodShape = MethodShape(
                typeShape,
                name,
                symbol,
                stay,
                accessorVisibility,
                methodKind,
                memberOpenness,
            )
            addOrReplaceNamed(typeShape.methods, methodShape)
        }
    }
    for ((symbol, sameNameDefs) in methodDeclsGrouped.entries) {
        for (def in sameNameDefs) {
            val (_, declParts) = def
            val visibility = visibilityFor(
                declParts,
                macroEnv,
                defaultVisibility,
            )
            val methodKind = when {
                getterSymbol in declParts.metadataSymbolMap -> MethodKind.Getter
                setterSymbol in declParts.metadataSymbolMap -> MethodKind.Setter
                symbol == constructorSymbol -> MethodKind.Constructor
                else -> MethodKind.Normal
            }
            val name = declParts.name.content
            val stay = stayFor(def)
            val methodShape = MethodShape(
                typeShape,
                name,
                symbol,
                stay,
                visibility,
                methodKind,
                memberOpenness,
            )
            addOrReplaceNamed(typeShape.methods, methodShape)
            val fn = declParts.metadataSymbolMap[initSymbol]?.target as? FunTree

            if (isFunctionInterface && symbol == applyDotName) {
                val fnParts = fn?.parts
                // Remove any `this` parameter since functional interfaces are stateless
                // and their `apply` methods do not require `this` parameters.
                if (fnParts != null) {
                    val thisDecl = fnParts.formals.firstOrNull {
                        it.parts?.metadataSymbolMap?.containsKey(impliedThisSymbol) == true
                    }
                    if (thisDecl != null) {
                        val thisDeclIndex = thisDecl.incoming!!.edgeIndex
                        fn.removeChildren(thisDeclIndex..thisDeclIndex)
                    }
                }
            }

            fn?.parts?.let {
                methodShape.parameterInfo = ExtraNonNormativeParameterInfo(it)
            }
        }
    }
    for ((symbol, sameNameDefs) in staticPropertyDeclsGrouped.entries) {
        for (def in sameNameDefs) {
            val (_, decl) = def
            val visibility = visibilityFor(
                decl,
                macroEnv,
                defaultVisibility,
            )
            val name = decl.name.content
            val stay = stayFor(def)
            val memberShape = StaticPropertyShape(
                enclosingType = typeShape,
                name = name,
                symbol = symbol,
                stay = stay,
                visibility = visibility,
            )
            addOrReplaceNamed(typeShape.staticProperties, memberShape)
        }
    }

    return Value(reifiedTypeFor(typeShape))
}

internal fun stayFor(
    decl: Pair<TEdge, DeclParts>,
): StayLeaf? = stayFor(decl.first.target as DeclTree)

/**
 * Gets the [StayLeaf] associated with a member declarations if present.
 * If not present, and we're late enough that stays won't be obsoleted by name resolution,
 * create one and attach it.
 */
internal fun stayFor(
    decl: DeclTree,
): StayLeaf? {
    val parts = decl.parts!!
    val stayEdge = parts.metadataSymbolMap[staySymbol]
    val stay = stayEdge?.target as? StayLeaf
    if (stay != null) {
        return stay
    }
    val doc = decl.document
    return if (doc.isResolved) {
        val newStay = StayLeaf(doc, decl.pos)
        decl.replace(decl.size until decl.size) {
            V(decl.pos.leftEdge, vStaySymbol)
            Replant(newStay)
        }
        newStay
    } else {
        null
    }
}

private fun MetadataMultimap.typeDefined() =
    this[typeDefinedSymbol]?.lastOrNull()?.target?.reifiedTypeContained?.type2?.definition

private fun MetadataMap.symbolValue(keySymbol: Symbol) =
    this[keySymbol]?.target?.symbolContained

private fun invalidateConflictingDefinitions(
    macroEnv: MacroEnvironment,
    sameNameDefs: MutableList<Pair<TEdge, DeclParts>>,
) {
    require(sameNameDefs.isNotEmpty())
    macroEnv.failLog.fail(
        MessageTemplate.ClassMemberNameConflict,
        sameNameDefs[0].first.target.pos,
        listOf(
            sameNameDefs.subListToEnd(1).map {
                it.first.target.pos
            },
        ),
    )
    for ((edge) in sameNameDefs) {
        convertToErrorNode(edge)
    }
}

private fun invalidateSubsequent(
    macroEnv: MacroEnvironment,
    first: TEdge,
    subsequent: TEdge,
) {
    macroEnv.failLog.fail(
        MessageTemplate.ClassMemberNameConflict,
        first.target.pos,
        listOf(subsequent.target.pos),
    )
    convertToErrorNode(subsequent)
}

private fun <OUT : Visibility?> visibilityFor(
    parts: DeclParts?,
    macroEnv: MacroEnvironment,
    defaultVisibility: OUT,
    onBadOrMissingMetadata: (TEdge?) -> OUT = { defaultVisibility },
): OUT {
    val metadataMap = parts?.metadataSymbolMap
        ?: return onBadOrMissingMetadata(null)
    val visibilityEdge = metadataMap[visibilitySymbol]
        ?: return defaultVisibility
    val visibilityPos = visibilityEdge.target.pos
    when (val visibilityResult = macroEnv.evaluateEdge(visibilityEdge, InterpMode.Partial)) {
        is Fail, NotYet -> {
            macroEnv.failLog.fail(
                MessageTemplate.UnexpectedMetadata,
                visibilityPos,
                values = listOf(visibilitySymbol, visibilityResult),
            )
            return onBadOrMissingMetadata(visibilityEdge)
        }
        is Value<*> -> {
            val symbol = TSymbol.unpackOrNull(visibilityResult)

            // Unsound but safe when Visibility is a subtype of all parameterizations used
            @Suppress("UNCHECKED_CAST")
            val visibilityAsOut = Visibility.fromSymbol(symbol) as OUT?
            return visibilityAsOut
                ?: if (symbol == defaultSymbol && defaultVisibility != null) {
                    // Explicit `default` does not deserve an error message
                    defaultVisibility
                } else {
                    macroEnv.failLog.fail(
                        MessageTemplate.UnexpectedMetadata,
                        visibilityPos,
                        values = listOf(visibilitySymbol, visibilityResult),
                    )
                    onBadOrMissingMetadata(visibilityEdge)
                }
        }
    }
}

private fun addVisibilityAnnotationTo(
    tree: DeclTree,
    visibility: Visibility,
) {
    // Add explicitly to tree
    val metadataPos = tree.pos.leftEdge
    val doc = tree.document
    tree.add(newChild = ValueLeaf(doc, metadataPos, vVisibilitySymbol))
    tree.add(newChild = ValueLeaf(doc, metadataPos, Value(visibility.toSymbol())))
}

private fun <T : MemberShape> addOrReplaceNamed(ls: MutableList<T>, element: T) {
    val index = ls.indexOfFirst { it.name == element.name }
    if (index >= 0) {
        if (ls[index] != element) {
            ls[index] = element
        }
    } else {
        ls.add(element)
    }
}

private fun defineImpliedGetterOrSetter(
    macroEnv: MacroEnvironment,
    symbol: Symbol,
    defineImpliedMethod: DefineImpliedMethod,
    propDef: Pair<TEdge, DeclParts>,
    containingClassAsValue: Value<ReifiedType>,
    plantPropertyType: ((Planting) -> Unit)?,
    isGetter: Boolean,
): Pair<TEdge, DeclParts> {
    val getterName = macroEnv.nameMaker.unusedSourceName(
        // TODO: is this right?
        ParsedName("${if (isGetter) "get" else "set"}${symbol.text}"),
    )
    val newValueName = if (isGetter) {
        null
    } else {
        macroEnv.nameMaker.unusedSourceName(
            ParsedName("new${symbol.text.asciiTitleCase()}"),
        )
    }
    return defineImpliedMethod(symbol) {
        Decl(propDef.first.target.pos.leftEdge) {
            Ln(getterName)

            V(if (isGetter) getterSymbol else setterSymbol)
            V(void)

            V(methodSymbol)
            V(symbol)

            V(initSymbol)
            Fn {
                val thisName = macroEnv.nameMaker.unusedSourceName(thisParsedName)
                val returnName = macroEnv.nameMaker.unusedSourceName(returnParsedName)
                Decl(thisName) {
                    V(impliedThisSymbol)
                    V(containingClassAsValue)
                    V(typeSymbol)
                    V(containingClassAsValue)
                }
                // Setters need an argument for the new value
                if (newValueName != null) {
                    Decl(newValueName) {
                        if (plantPropertyType != null) {
                            V(typeSymbol)
                            plantPropertyType(this)
                        }
                    }
                }
                V(returnDeclSymbol)
                Decl {
                    Ln(returnName)
                    when {
                        !isGetter -> {
                            V(typeSymbol)
                            V(Types.vVoid)
                        }
                        plantPropertyType != null -> {
                            V(typeSymbol)
                            plantPropertyType(this)
                        }
                        else -> {}
                    }
                }
                Block {
                    if (newValueName != null) { // If a setter, set the property
                        check(!isGetter)
                        Call(BuiltinFuns.setpFn) {
                            Rn(propDef.second.name.content)
                            Rn(thisName)
                            Rn(newValueName)
                        }
                    }
                    // Either way, the value of the property is what we return
                    Call(BuiltinFuns.setLocalFn) {
                        Ln(returnName)
                        if (isGetter) {
                            Call(BuiltinFuns.getpFn) {
                                Rn(propDef.second.name.content)
                                Rn(thisName)
                            }
                        } else {
                            V(void)
                        }
                    }
                }
            }
        }
    }
}
