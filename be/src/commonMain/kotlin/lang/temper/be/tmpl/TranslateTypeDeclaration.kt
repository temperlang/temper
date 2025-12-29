package lang.temper.be.tmpl

import lang.temper.be.TargetLanguageTypeName
import lang.temper.common.OpenOrClosed
import lang.temper.common.ignore
import lang.temper.frontend.typestage.memberOverrideFor2
import lang.temper.log.Position
import lang.temper.log.unknownPos
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.type.Abstractness
import lang.temper.type.MemberOverride2
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.PropertyShape
import lang.temper.type.StaticPropertyShape
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.VisibleMemberShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Descriptor
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.SuperTypeTree2
import lang.temper.type2.Type2
import lang.temper.type2.TypeContext2
import lang.temper.type2.forEachSuperType
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.mapType
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.constructorSymbol
import lang.temper.value.enumTypeSymbol
import lang.temper.value.fnSymbol
import lang.temper.value.getBuiltinName
import lang.temper.value.getterSymbol
import lang.temper.value.impliedThisSymbol
import lang.temper.value.initSymbol
import lang.temper.value.isAnyValueType
import lang.temper.value.memberTypeFormalSymbol
import lang.temper.value.methodSymbol
import lang.temper.value.propertySymbol
import lang.temper.value.reifiedTypeContained
import lang.temper.value.setBuiltinName
import lang.temper.value.setterSymbol
import lang.temper.value.staticPropertySymbol
import lang.temper.value.symbolContained
import lang.temper.value.thisParsedName
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.typeDefinitionAtLeafOrNull
import lang.temper.value.typePlaceholderSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.varSymbol
import lang.temper.value.visibilitySymbol

internal fun translateTypeDeclaration(
    pos: Position,
    typeShape: TypeShape,
    typeIdDecl: PreTranslated.CombinedDeclaration?,
    preTranslatedMembers: List<PreTranslated>,
    connection: Pair<TargetLanguageTypeName, List<Type2>>?,
    translator: TmpLTranslator,
): Pair<TmpL.TypeDeclaration, List<TmpL.TopLevel>> {
    ignore(typeIdDecl) // TODO: should I get rid of this arg?

    val internalType = MkType2(typeShape)
        .actuals(typeShape.formals.map { MkType2(it).get() })
        .get()
    val otherTopLevels = mutableListOf<TmpL.TopLevel>()
    val typeParameterList = mutableListOf<TmpL.TypeFormal>()
    val members = mutableListOf<TmpL.MemberOrGarbage>()
    val kind: TmpL.TypeDeclarationKind = when (typeShape.abstractness) {
        Abstractness.Abstract -> TmpL.TypeDeclarationKind.Interface
        Abstractness.Concrete -> {
            if (typeShape.metadata.any { (key) -> key == enumTypeSymbol }) {
                TmpL.TypeDeclarationKind.Enum
            } else {
                TmpL.TypeDeclarationKind.Class
            }
        }
    }
    // Ensure we have support code.
    when (kind) {
        TmpL.TypeDeclarationKind.Enum -> {
            translator.pool.backendMightNeed(
                pos.leftEdge,
                OptionalSupportCodeKind.EnumTypeSupport,
            )
        }
        TmpL.TypeDeclarationKind.Interface -> {
            translator.pool.backendMightNeed(
                pos.leftEdge,
                OptionalSupportCodeKind.InterfaceTypeSupport,
            )
        }
        TmpL.TypeDeclarationKind.Class -> Unit
    }

    val superTypes = typeShape.superTypes.mapNotNull {
        if (it.definition.isAnyValueType) {
            null
        } else {
            translator.translateNominalType(pos, hackMapOldStyleToNew(it) as DefinedNonNullType)
        }
    }
    val typeIsConnected = connection != null

    fun makeSuperTypeMethod(
        memberOverride: MemberOverride2,
    ): TmpL.SuperTypeMethod {
        val superTypeMember = memberOverride.superTypeMember
        val enclosingType = superTypeMember.enclosingType
        val opos = superTypeMember.stay?.pos ?: unknownPos
        val superType =
            MkType2(enclosingType).actuals(
                enclosingType.typeParameters.map { MkType2(it.definition).get() },
            ).get() as DefinedNonNullType
        val sig: Signature2? = superTypeMember.descriptor as? Signature2
        return TmpL.SuperTypeMethod(
            pos = opos,
            superType = translator.translateNominalType(opos, superType),
            name = TmpL.DotName(opos, superTypeMember.symbol.text),
            visibility = TmpL.VisibilityModifier(
                opos,
                superTypeMember.visibility.toTmpL(),
            ),
            typeParameters = TmpL.ATypeParameters(
                TmpL.TypeParameters(
                    opos,
                    sig?.typeFormals?.map { translator.translateTypeFormal(opos, it) } ?: emptyList(),
                ),
            ),
            parameters = TmpL.ValueFormalList(
                opos,
                buildList {
                    sig?.requiredInputTypes?.mapTo(this) {
                        TmpL.ValueFormal(
                            opos, null,
                            translator.translateType(opos, it).aType,
                            isOptional = false,
                        )
                    }
                    sig?.optionalInputTypes?.mapTo(this) {
                        TmpL.ValueFormal(
                            opos, null,
                            translator.translateType(opos, it).aType,
                            isOptional = true,
                        )
                    }
                },
                sig?.restInputsType?.let {
                    translator.translateType(opos, it).aType
                },
            ),
            returnType = translator.translateType(opos, sig?.returnType2.orInvalid).aType,
            memberOverride = memberOverride,
        )
    }

    memberLoop@
    for (member in preTranslatedMembers) {
        fun bad(diagnosticText: String) = TmpL.GarbageStatement(
            TmpL.Diagnostic(member.pos, diagnosticText),
        )

        val declaration: DeclTree
        val initial: Tree?
        when (member) {
            is PreTranslated.CombinedDeclaration -> {
                declaration = member.declaration
                initial = member.initial
            }

            is PreTranslated.TreeWrapper if member.tree is DeclTree -> {
                declaration = member.tree
                initial = null
            }

            else -> {
                translator.untranslatableStmt(member.pos, "Malformed type member")
                continue@memberLoop
            }
        }
        val parts = declaration.parts
        val partName = parts?.name?.content
        val metadata = parts?.metadataSymbolMap ?: emptyMap()
        if (typePlaceholderSymbol in metadata) {
            continue
        }
        val staticPropertyEdge = metadata[staticPropertySymbol]
        val staticPropertyMeta = unpackSymbolMetadata(staticPropertyEdge)
        val propertyEdge = metadata[propertySymbol]
        val propertyMeta = unpackSymbolMetadata(propertyEdge)
        val methodEdge = metadata[methodSymbol]
        val methodMeta = unpackSymbolMetadata(methodEdge)
        val typeMemberTypeFormalEdge = metadata[memberTypeFormalSymbol]
        val typeMemberTypeFormalMeta = unpackSymbolMetadata(typeMemberTypeFormalEdge)
        val isGetter = getterSymbol in metadata
        val isSetter = setterSymbol in metadata
        val isConstructor = methodMeta == constructorSymbol

        val memberShape = when {
            partName == null -> null // error handled below
            staticPropertyMeta != null ->
                typeShape.staticProperties.firstOrNull { it.name == partName }
            propertyMeta != null ->
                typeShape.properties.firstOrNull { it.name == partName }
            methodMeta != null -> {
                val methodKind = when {
                    isConstructor -> MethodKind.Constructor
                    isGetter -> MethodKind.Getter
                    isSetter -> MethodKind.Setter
                    else -> MethodKind.Normal
                }
                typeShape.methods.firstOrNull { it.methodKind == methodKind && it.name == partName }
            }
            typeMemberTypeFormalMeta != null -> {
                typeShape.typeParameters.firstOrNull { it.name == partName }
            }
            else -> null
        }

        if (parts == null || partName == null || memberShape == null) {
            members.add(
                translator.untranslatableStmt(member.pos, "Malformed type member"),
            )
            continue@memberLoop
        }

        val visibilityPos = parts.metadataSymbolMap[visibilitySymbol]?.target?.pos
            ?: parts.name.pos.leftEdge
        val visibility = TmpL.VisibilityModifier(
            visibilityPos,
            (memberShape as? VisibleMemberShape)?.visibility?.toTmpL() ?: TmpL.Visibility.Private,
        )
        val overridden = (memberShape as? MethodShape)?.overriddenMembers?.map { memberOverride ->
            val desc = memberOverride.superTypeMemberTypeInSubTypeContext
            makeSuperTypeMethod(MemberOverride2(memberOverride.superTypeMember, desc))
        } ?: emptyList()
        val signatureAdjustments = (memberShape as? MethodShape)?.let { methodShape ->
            val fetcher = translator.metadataFetcher()
            fetcher.read(methodShape.name, SignatureAdjustments.KeyFactory)
                ?.get(methodShape.name as ResolvedName)
        }
        val nameId = TmpL.Id(parts.name.pos, parts.name.content as ResolvedName)
        val (thisName, thisType) = run findThisParameter@{
            // If this member defines a method or constructor, we'll need the
            // `this` parameter name and type below.
            if (initial is FunTree && memberShape !is StaticPropertyShape) {
                for (formal in initial.parts?.formals ?: emptyList()) {
                    val formalParts = formal.parts
                    val thisTypeEdge = formalParts?.metadataSymbolMap?.get(impliedThisSymbol)
                        ?: continue
                    val type = thisTypeEdge.target.reifiedTypeContained?.type2
                    if (type is DefinedNonNullType) {
                        return@findThisParameter Pair(
                            formalParts.name.content as ResolvedName,
                            type,
                        )
                    }
                }
            }
            null to null
        }

        val isConnected = run isConnected@{
            val connectedKey = connectedKeyForMember(memberShape)
            if (connectedKey != null) {
                val supportCode = translator.supportNetwork
                    .translateConnectedReference(declaration.pos, connectedKey, translator.genre)
                if (supportCode != null) {
                    return@isConnected true
                }
            }
            false
        }
        if (isConnected && (memberShape as? VisibleMemberShape)?.overriddenMembers.isNullOrEmpty()) {
            // Don't bother translating if the member isn't needed to override a member
            // from another type.
            // TODO: should we also skip if the overridden members are themselves skipped by this
            // calculation?
            // It seems so, but after we go ahead and do a pre-naming pass, we can choose to
            // simply not generate names for things that can be skipped, so it will be better
            // to do that transitive analysis then.
            continue
        }
        // If a member is not connected, but the containing type is, then we pull it out, and we convert any
        // references to this to regular parameters.
        val pullOutMember = memberShape is VisibleMemberShape && shouldPullOutMember(
            memberShape,
            memberIsConnected = isConnected,
            typeIsConnected = typeIsConnected,
            isConstructor = isConstructor,
        )

        when {
            staticPropertyMeta != null -> {
                check(memberShape is StaticPropertyShape)
                val isMethodLike = fnSymbol in memberShape.metadata
                if (isMethodLike && initial is FunTree) {
                    val tf =
                        translator.translateSimpleFunctionDeclaration(
                            id = nameId.deepCopy(),
                            declPos = member.pos,
                            init = initial,
                            metadata = metadata,
                        )
                    if (pullOutMember) {
                        otherTopLevels.add(
                            if (tf == null) {
                                TmpL.ModuleInitBlock(
                                    member.pos,
                                    body = TmpL.BlockStatement(
                                        member.pos,
                                        listOf(bad("untranslatable method")),
                                    ),
                                )
                            } else {
                                TmpL.ModuleFunctionDeclaration(
                                    member.pos,
                                    metadata = tf.metadata,
                                    name = nameId,
                                    typeParameters = tf.typeParameters.deepCopy(),
                                    parameters = tf.parameters.deepCopy(),
                                    returnType = tf.returnType.deepCopy(),
                                    body = tf.body.deepCopy(),
                                    mayYield = tf.mayYield,
                                    sig = tf.sig,
                                )
                            },
                        )
                    } else {
                        members.add(
                            when (tf) {
                                null -> bad("untranslatable method")
                                else ->
                                    TmpL.StaticMethod(
                                        member.pos,
                                        memberShape = memberShape,
                                        visibility = visibility,
                                        metadata = tf.metadata,
                                        dotName = TmpL.DotName(parts.name.pos, staticPropertyMeta.text),
                                        name = nameId,
                                        typeParameters = tf.typeParameters.deepCopy(),
                                        parameters = tf.parameters.deepCopy(),
                                        returnType = tf.returnType.deepCopy(),
                                        body = tf.body.deepCopy(),
                                        mayYield = tf.mayYield,
                                    )
                            },
                        )
                    }
                } else {
                    val typeMeta = metadata[typeSymbol]
                    val (descriptor, type) =
                        getDescriptorAndType(typeMeta, memberShape, pos, translator)

                    val expression =
                        (initial ?: metadata[initSymbol]?.target)?.let {
                            translator.translateExpression(it)
                        } ?: TODO("Handle missing static initializer: ${metadata.keys}")
                    if (pullOutMember) {
                        otherTopLevels.add(
                            TmpL.ModuleLevelDeclaration(
                                pos = member.pos,
                                metadata = translator.translateDeclarationMetadata(metadata),
                                name = nameId,
                                type = type.aType,
                                init = expression,
                                assignOnce = true,
                                descriptor = descriptor as Type2,
                            ),
                        )
                    } else {
                        members.add(
                            TmpL.StaticProperty(
                                pos = member.pos,
                                metadata = translator.translateDeclarationMetadata(metadata),
                                memberShape = memberShape,
                                visibility = visibility,
                                dotName = TmpL.DotName(parts.name.pos, staticPropertyMeta.text),
                                name = nameId,
                                type = type.aType,
                                expression = expression,
                                descriptor = descriptor as Type2,
                            ),
                        )
                    }
                }
            }
            propertyMeta != null -> {
                val typeMeta = metadata[typeSymbol]
                var propertyShape = memberShape as PropertyShape
                val (descriptor, type) =
                    getDescriptorAndType(typeMeta, memberShape, pos, translator)
                val assignOnce = varSymbol !in metadata
                val dotName = TmpL.DotName(parts.name.pos, propertyMeta.text)

                // HACK: The frontend should generate getters and setters where none are present.
                // For languages like Java which don't allow properties on interfaces, it's important
                // to have something abstract, like the getter/setter represented.
                var getter: TmpL.Getter? = null
                var setter: TmpL.Setter? = null
                if (propertyShape.abstractness == Abstractness.Abstract) {
                    val getterName = if (propertyShape.getter == null) {
                        accessorName(translator, propertyShape, isGetter = true)
                    } else { null }
                    val setterName = if (propertyShape.setter == null && !assignOnce) {
                        accessorName(translator, propertyShape, isGetter = false)
                    } else { null }
                    if (getterName != null || setterName != null) {
                        propertyShape = propertyShape.copy(
                            getter = getterName ?: propertyShape.getter,
                            setter = setterName ?: propertyShape.setter,
                        )
                    }
                    if (getterName != null) {
                        val getterShape = MethodShape(
                            enclosingType = propertyShape.enclosingType,
                            name = getterName,
                            symbol = propertyShape.symbol,
                            stay = null,
                            visibility = propertyShape.visibility,
                            methodKind = MethodKind.Getter,
                            openness = OpenOrClosed.Open,
                        )
                        getterShape.descriptor = Signature2(
                            returnType2 = descriptor as Type2,
                            hasThisFormal = true,
                            requiredInputTypes = listOf(internalType),
                        )

                        val getterThisName = translator.unusedName(thisParsedName)
                        val lPos = member.pos.leftEdge
                        getter = TmpL.Getter(
                            pos = member.pos,
                            metadata = emptyList(),
                            dotName = dotName.deepCopy(),
                            name = TmpL.Id(lPos, getterShape.name as ResolvedName),
                            typeParameters = TmpL.ATypeParameters(
                                TmpL.TypeParameters(lPos, emptyList()),
                            ),
                            parameters = TmpL.Parameters(
                                lPos, TmpL.Id(lPos, getterThisName),
                                listOf(
                                    TmpL.Formal(
                                        lPos, emptyList(), TmpL.Id(lPos, getterThisName),
                                        translator.translateType(lPos, internalType).aType,
                                        internalType,
                                    ),
                                ),
                                null,
                            ),
                            returnType = type.deepCopy().aType,
                            body = null,
                            visibility = visibility.deepCopy(),
                            overridden = emptyList(),
                            memberShape = getterShape,
                            propertyShape = propertyShape,
                        )
                    }
                    if (setterName != null) {
                        val setterShape = MethodShape(
                            enclosingType = propertyShape.enclosingType,
                            name = setterName,
                            symbol = propertyShape.symbol,
                            stay = null,
                            visibility = propertyShape.visibility,
                            methodKind = MethodKind.Setter,
                            openness = OpenOrClosed.Open,
                        )
                        setterShape.descriptor = Signature2(
                            returnType2 = WellKnownTypes.voidType2,
                            hasThisFormal = true,
                            requiredInputTypes = listOf(internalType, descriptor as Type2),
                        )

                        val setterThisName = translator.unusedName(thisParsedName)
                        val newValueName = translator.unusedName(ParsedName("new${dotName.dotNameText}"))
                        val lPos = member.pos.leftEdge
                        setter = TmpL.Setter(
                            pos = member.pos,
                            metadata = emptyList(),
                            dotName = dotName.deepCopy(),
                            name = TmpL.Id(lPos, setterShape.name as ResolvedName),
                            typeParameters = TmpL.ATypeParameters(
                                TmpL.TypeParameters(lPos, emptyList()),
                            ),
                            parameters = TmpL.Parameters(
                                lPos, TmpL.Id(lPos, setterThisName),
                                listOf(
                                    TmpL.Formal(
                                        lPos, emptyList(), TmpL.Id(lPos, setterThisName),
                                        translator.translateType(lPos, internalType).aType,
                                        internalType,
                                    ),
                                    TmpL.Formal(
                                        lPos, emptyList(), TmpL.Id(lPos, newValueName),
                                        type.deepCopy().aType,
                                        descriptor,
                                    ),
                                ),
                                null,
                            ),
                            returnType = translator.translateType(lPos, WellKnownTypes.voidType2).aType,
                            body = null,
                            visibility = visibility.deepCopy(),
                            overridden = emptyList(),
                            memberShape = setterShape,
                            propertyShape = propertyShape,
                        )
                    }
                }

                members.add(
                    TmpL.InstanceProperty(
                        pos = member.pos,
                        metadata = translator.translateDeclarationMetadata(metadata),
                        memberShape = propertyShape,
                        visibility = visibility,
                        dotName = dotName,
                        name = nameId,
                        type = type.aType,
                        assignOnce = assignOnce,
                        descriptor = descriptor as Type2,
                    ),
                )
                getter?.let { members.add(it) }
                setter?.let { members.add(it) }
            }
            (isGetter || isSetter) && methodMeta != null && initial is FunTree -> {
                val tf = translator.withThisName(
                    thisName, thisType, isPulledOut = pullOutMember,
                ) {
                    translator.translateSimpleFunctionDeclaration(
                        id = nameId.deepCopy(),
                        declPos = member.pos,
                        init = initial,
                        metadata = metadata,
                    )
                }
                val kindString = if (isGetter) { "getter" } else { "setter" }
                val propertyShape = propertyShapeForSetterOrGetter(memberShape as MethodShape)
                when {
                    tf == null -> members.add(bad("untranslatable $kindString"))
                    tf.mayYield -> members.add(bad("${kindString}s may not yield"))
                    tf.typeParameters.ot.typeParameters.isNotEmpty() ->
                        members.add(bad("${kindString}s may not declare type parameters"))
                    pullOutMember -> otherTopLevels.add(
                        TmpL.ModuleFunctionDeclaration(
                            member.pos,
                            metadata = tf.metadata,
                            name = nameId,
                            typeParameters = tf.typeParameters,
                            returnType = tf.returnType,
                            parameters = tf.parameters,
                            body = tf.body,
                            mayYield = tf.mayYield,
                            sig = tf.sig,
                        ),
                    )
                    isGetter -> members.add(
                        TmpL.Getter(
                            member.pos,
                            memberShape = memberShape,
                            propertyShape = propertyShape,
                            visibility = visibility,
                            metadata = tf.metadata,
                            dotName = TmpL.DotName(parts.name.pos, methodMeta.text),
                            name = nameId,
                            typeParameters = tf.typeParameters,
                            returnType = tf.returnType,
                            parameters = tf.parameters,
                            body = tf.body,
                            overridden = overridden,
                        ),
                    )
                    else -> members.add(
                        TmpL.Setter(
                            member.pos,
                            memberShape = memberShape,
                            propertyShape = propertyShape,
                            visibility = visibility,
                            metadata = tf.metadata,
                            dotName = TmpL.DotName(parts.name.pos, methodMeta.text),
                            name = nameId,
                            typeParameters = tf.typeParameters,
                            returnType = tf.returnType,
                            parameters = tf.parameters,
                            body = tf.body,
                            overridden = overridden,
                        ),
                    )
                }
            }
            methodMeta != null && initial is FunTree && isConstructor -> {
                val tf = translator.withThisName(
                    thisName, thisType, isPulledOut = false,
                ) {
                    translator.translateSimpleFunctionDeclaration(
                        id = nameId.deepCopy(),
                        declPos = member.pos,
                        init = initial,
                        metadata = metadata,
                    )
                }

                members.add(
                    when {
                        tf == null -> bad("untranslatable constructor")
                        tf.mayYield -> bad("constructors may not yield")
                        tf.typeParameters.ot.typeParameters.isNotEmpty() ->
                            bad("constructors may not declare type parameters")
                        else -> TmpL.Constructor(
                            pos = member.pos,
                            memberShape = memberShape as VisibleMemberShape,
                            metadata = tf.metadata,
                            name = tf.name.deepCopy(),
                            typeParameters = tf.typeParameters.deepCopy(),
                            parameters = tf.parameters.deepCopy(),
                            body = tf.body.deepCopy(),
                            returnType = tf.returnType.deepCopy(),
                            visibility = visibility,
                            adjustments = signatureAdjustments,
                        )
                    },
                )
            }
            methodMeta != null && initial is FunTree -> {
                val expected = initial.parts?.connected?.let { connectedName ->
                    val genre = initial.document.context.genre
                    translator.supportNetwork.translateConnectedReference(initial.pos, connectedName, genre)
                } == null
                if (expected) {
                    val tf = translator.withThisName(
                        thisName, thisType, isPulledOut = pullOutMember,
                    ) {
                        translator.translateSimpleFunctionDeclaration(
                            id = nameId.deepCopy(),
                            declPos = member.pos,
                            init = initial,
                            metadata = metadata,
                        )
                    }
                    when {
                        tf == null -> members.add(bad("untranslatable method"))
                        pullOutMember -> otherTopLevels.add(
                            TmpL.ModuleFunctionDeclaration(
                                pos = member.pos,
                                metadata = tf.metadata,
                                name = tf.name.deepCopy(),
                                typeParameters = tf.typeParameters.deepCopy(),
                                parameters = tf.parameters.deepCopy(),
                                returnType = tf.returnType.deepCopy(),
                                body = tf.body.deepCopy(),
                                mayYield = tf.mayYield,
                                sig = tf.sig,
                            ),
                        )
                        else -> members.add(
                            TmpL.NormalMethod(
                                pos = member.pos,
                                memberShape = memberShape as VisibleMemberShape,
                                visibility = visibility,
                                metadata = tf.metadata,
                                dotName = TmpL.DotName(methodEdge!!.target.pos, methodMeta.text),
                                name = tf.name.deepCopy(),
                                typeParameters = tf.typeParameters.deepCopy(),
                                parameters = tf.parameters.deepCopy(),
                                returnType = tf.returnType.deepCopy(),
                                body = tf.body.deepCopy(),
                                mayYield = tf.mayYield,
                                overridden = overridden,
                                adjustments = signatureAdjustments,
                            ),
                        )
                    }
                }
            }
            typeMemberTypeFormalMeta != null -> {
                val typeDefinition = metadata[typeDefinedSymbol]?.target
                    ?.typeDefinitionAtLeafOrNull
                if (typeDefinition !is TypeFormal) {
                    translator.untranslatableStmt(member.pos, "Malformed type formal")
                    continue@memberLoop
                }
                typeParameterList.add(translator.translateTypeFormal(member.pos, typeDefinition, nameId))
            }
            else -> TODO("$member not understood")
        }
    }

    // Walk the super-type tree figuring out which members are inherited that we need to
    // represent so that backends can easily produce bridging definitions.
    val inherited = mutableListOf<TmpL.SuperTypeMethod>()
    run {
        // Find the overrides so that we can avoid them when computing the inherited members
        val superTypeMembersSeen = mutableSetOf<VisibleMemberShape>()
        for (member in members) {
            when (member) {
                is TmpL.GarbageStatement,
                is TmpL.StaticMember,
                is TmpL.Property,
                ->
                    continue
                is TmpL.InstanceMethod -> member.overridden.mapTo(superTypeMembersSeen) {
                    it.memberOverride.superTypeMember
                }
            }
        }

        val typeTree = SuperTypeTree2.of(internalType)
        val typeContext = TypeContext2()
        typeTree.forEachSuperType { projectedSuperType ->
            check(projectedSuperType is DefinedNonNullType)
            val superTypeShape = projectedSuperType.definition
            for (memberShape in superTypeShape.members) {
                if (memberShape !is VisibleMemberShape) { continue } // Not heritable
                if (memberShape.visibility == Visibility.Private) { continue } // Not inherited
                if (memberShape !is MethodShape) { continue }

                // Don't treat as inherited the things that this member overrides
                memberShape.overriddenMembers?.forEach {
                    superTypeMembersSeen.add(it.superTypeMember)
                }
                if (memberShape in superTypeMembersSeen) { continue } // Already handled

                superTypeMembersSeen.add(memberShape)
                val superFnType = memberShape.descriptor
                val subFnType = superFnType?.let { fnT ->
                    val mapper = buildMap {
                        for ((b, f) in projectedSuperType.bindings.zip(superTypeShape.formals)) {
                            this[f] = b
                        }
                    }
                    fnT.mapType(mapper)
                }

                memberOverrideFor2(
                    typeContext = typeContext,
                    subFnType = subFnType,
                    superType = projectedSuperType,
                    superTypeMember = memberShape,
                )?.let {
                    inherited.add(makeSuperTypeMethod(it))
                }
            }
            // Continue to super-types of the super-type
            true
        }
    }

    val typeParameters = TmpL.ATypeParameters(TmpL.TypeParameters(pos, typeParameterList))
    val adjustedMembers = translator.supportNetwork.adjustTypeMembers(
        typeShape,
        members.toList(),
        translator.translationAssistant,
    )

    return TmpL.TypeDeclaration(
        pos,
        metadata = translator.translateDeclarationMetadataValueMultimap(typeShape.metadata),
        name = TmpL.Id(pos.leftEdge, typeShape.name),
        typeParameters = typeParameters,
        superTypes = superTypes,
        members = adjustedMembers,
        inherited = inherited.toList(),
        kind = kind,
        typeShape = typeShape,
    ) to otherTopLevels.toList()
}

private fun unpackSymbolMetadata(edge: TEdge?) = edge?.target?.symbolContained

private fun TmpLTranslator.translateTypeFormal(
    pos: Position,
    typeDefinition: TypeFormal,
    nameId: TmpL.Id = TmpL.Id(pos, typeDefinition.name),
): TmpL.TypeFormal {
    val upperBounds = typeDefinition.upperBounds
    return TmpL.TypeFormal(
        pos = pos,
        name = nameId,
        upperBounds = upperBounds.map {
            translateNominalType(pos, hackMapOldStyleToNew(it) as DefinedNonNullType)
        },
        definition = typeDefinition,
    )
}

internal fun propertyShapeForSetterOrGetter(memberShape: MethodShape) =
    memberShape.enclosingType.properties.first {
        it.getter == memberShape.name || it.setter == memberShape.name
    }

internal fun shouldPullOutMember(
    memberShape: VisibleMemberShape,
    memberIsConnected: Boolean,
    typeIsConnected: Boolean,
    isConstructor: Boolean,
) = !memberIsConnected && typeIsConnected &&
    (memberShape is MethodShape || memberShape is StaticPropertyShape) &&
    !isConstructor &&
    (memberShape as? MethodShape)?.isPureVirtual != true &&
    memberShape.overriddenMembers.isNullOrEmpty()

private fun accessorName(
    t: TmpLTranslator,
    propertyShape: PropertyShape,
    isGetter: Boolean,
): ResolvedName {
    val baseName = ParsedName(
        "${(if (isGetter) getBuiltinName else setBuiltinName).baseName.nameText}.${propertyShape.symbol.text}",
    )
    return t.unusedName(baseName)
}

private fun getDescriptorAndType(
    typeMeta: TEdge?,
    memberShape: VisibleMemberShape,
    pos: Position,
    translator: TmpLTranslator,
): Pair<Descriptor, TmpL.Type> =
    if (typeMeta != null) {
        val typeTree = typeMeta.target
        (typeTree.reifiedTypeContained?.type2 ?: WellKnownTypes.invalidType2) to
            translator.translateType(typeTree)
    } else {
        val t = memberShape.descriptor as? Type2 ?: WellKnownTypes.invalidType2
        t to translator.translateType(pos, t)
    }
