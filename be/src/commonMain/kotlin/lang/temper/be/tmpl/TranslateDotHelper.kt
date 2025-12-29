package lang.temper.be.tmpl

import lang.temper.common.Either
import lang.temper.common.mapFirst
import lang.temper.common.subListToEnd
import lang.temper.frontend.typestage.BindMethodTypeHelper
import lang.temper.log.Position
import lang.temper.name.ResolvedName
import lang.temper.type.BindMemberAccessor
import lang.temper.type.DotHelper
import lang.temper.type.ExternalBind
import lang.temper.type.ExternalGet
import lang.temper.type.ExternalSet
import lang.temper.type.GetMemberAccessor
import lang.temper.type.InternalBind
import lang.temper.type.InternalGet
import lang.temper.type.InternalMemberAccessor
import lang.temper.type.InternalSet
import lang.temper.type.MemberShape
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.PropertyShape
import lang.temper.type.SetMemberAccessor
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.VisibleMemberShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.DefinedType
import lang.temper.type2.MkType2
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.TypeParamRef
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.hackTryStaticTypeToSig
import lang.temper.type2.withNullity
import lang.temper.type2.withType
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.MetadataValueMapHelpers.get
import lang.temper.value.TString
import lang.temper.value.Tree
import lang.temper.value.connectedSymbol
import lang.temper.value.functionContained
import lang.temper.value.toLispy

internal object TranslateDotHelper {
    private fun findMembers(definition: TypeDefinition, fn: DotHelper): Set<MethodShape> =
        when (definition) {
            is TypeFormal -> findMembers(
                buildSet {
                    fun explodeUpperBounds(t: Type2) {
                        when (t) {
                            is DefinedType ->
                                add(t.withNullity(Nullity.NonNull) as DefinedNonNullType)
                            is TypeParamRef -> {
                                for (ub in definition.upperBounds) {
                                    explodeUpperBounds(hackMapOldStyleToNew(ub))
                                }
                            }
                        }
                    }
                    explodeUpperBounds(MkType2(definition).get())
                }.toList(),
                fn,
            )
            is TypeShape -> if (definition == WellKnownTypes.invalidTypeDefinition) {
                emptySet()
            } else {
                fn.accessibleMembers(definition).mapNotNullTo(mutableSetOf()) {
                    // We'll get both property shapes and method shapes, but for abstract properties
                    // we need either the getter or the setter.
                    val method = it as? MethodShape
                    val appropriate = when (fn.memberAccessor) {
                        is GetMemberAccessor, is BindMemberAccessor ->
                            method?.methodKind != MethodKind.Setter

                        is SetMemberAccessor -> method?.methodKind == MethodKind.Setter
                    }
                    if (appropriate) {
                        method
                    } else {
                        null
                    }
                }
            }
        }

    /** TODO Any way to unify this with [DotHelper] lookup logic? */
    private fun findMembers(subjectTypes: List<DefinedNonNullType>, fn: DotHelper): Set<MethodShape> = buildSet {
        val commonMembers = mutableSetOf<MethodShape>()
        subjectTypes.forEachIndexed { index, memberType ->
            val members = findMembers(memberType.definition, fn)
            if (index == 0) {
                commonMembers.addAll(members)
            } else {
                commonMembers.retainAll(members)
            }
        }
        commonMembers.toSet()
    }

    private fun findConnectedMember(members: Set<MethodShape>): Pair<MethodShape, String>? {
        var result: Pair<MethodShape, String>? = null
        for (member in members) {
            val connectedMethodKey = connectedKeyForMethod(member)
            if (connectedMethodKey != null) {
                // TODO: if member is overrideable in subjectType, because it is not final
                // and subjectType is an interface type, then we cannot use this path.
                if (result != null) {
                    // Check for sub or super type. This could be inefficient in the form here, but we don't expect to
                    // go through more than a few matching candidates. Currently maybe only 2.
                    if (result.first.enclosingType.isSubOrSame(member.enclosingType)) {
                        // We already have a more specialized case, so skip this one. This is the common case.
                        continue
                    } else if (member.enclosingType.isSubOrSame(result.first.enclosingType)) {
                        // The new one is more precise, so keep on below with it instead.
                        // TODO Do we always expect subtypes first? Can we remove this case?
                    } else {
                        // Multiple incompatible matches, so no matches.
                        return null
                    }
                }
                result = member to connectedMethodKey
            }
        }
        return result
    }

    private data class Argument(
        val actualCalleeType: Signature2?,
        val declaredCalleeType: Signature2?,
        val argIndex: Int,
        val tree: Tree,
        val adjustments: SignatureAdjustments?,
    ) {
        fun translate(translator: TmpLTranslator) = translator.maybeInjectCastForInput(
            expr = translator.translateExpression(tree),
            argIndex = argIndex,
            actualCalleeType = actualCalleeType,
            declaredCalleeType = declaredCalleeType,
            adjustments = adjustments,
            builtinOperatorId = null,
        )
    }

    data class TranslatedDotHelper(
        val translation: Either<TmpL.Expression, TmpL.Statement>,
        val actualCalleeType: Signature2?,
        val declaredCalleeType: Signature2?,
        val adjustments: SignatureAdjustments?,
    )

    fun translate(
        callTree: CallTree,
        callee: Tree,
        /** For a call to a bound method, has the non-subject arguments. */
        outerCallTree: CallTree?,
        typeActuals: List<Tree>,
        translator: TmpLTranslator,
    ): TranslatedDotHelper {
        val pos = (outerCallTree ?: callTree).pos
        val calleePos = callee.pos
        val dotHelper = callee.functionContained as DotHelper

        val callType = (outerCallTree ?: callTree).typeOrInvalid
        val pool = translator.pool

        val subjectIndexInCallTree = 1 + dotHelper.memberAccessor.firstArgumentIndex
        val subjectTypeDefinition = callTree.children[subjectIndexInCallTree].typeOrInvalid.definition
        val members: Set<MethodShape> = findMembers(subjectTypeDefinition, dotHelper)
        val firstMember: MethodShape? = members.firstOrNull()
        val adjustments = firstMember?.let {
            translator.metadataFetcher().read(it.name, SignatureAdjustments.KeyFactory)
                ?.get(it.name as ResolvedName)
        }

        val declaredCalleeType: Signature2?
        val actualCalleeType: Signature2?
        if (outerCallTree == null) {
            declaredCalleeType = hackTryStaticTypeToSig(callTree.typeInferences?.variant)
            actualCalleeType = hackTryStaticTypeToSig(callTree.childOrNull(0)?.typeInferences?.type)
        } else if (dotHelper.memberAccessor is BindMemberAccessor) {
            val innerCallee = callTree.childOrNull(0)

            actualCalleeType = innerCallee?.typeInferences?.type?.let {
                hackTryStaticTypeToSig(BindMethodTypeHelper.uncurry(it))
            }
            declaredCalleeType = firstMember?.descriptor
        } else {
            TODO(callTree.toLispy())
        }
        fun translatedExpr(e: TmpL.Expression) = TranslatedDotHelper(
            Either.Left(e),
            actualCalleeType = actualCalleeType,
            declaredCalleeType = declaredCalleeType,
            adjustments = adjustments,
        )
        fun translatedStmt(e: TmpL.Statement) = TranslatedDotHelper(
            Either.Right(e),
            actualCalleeType = actualCalleeType,
            declaredCalleeType = declaredCalleeType,
            adjustments = adjustments,
        )

        val mergedArgumentList: List<Argument> = buildList {
            addAll(
                callTree.children.subListToEnd(subjectIndexInCallTree),
            )
            if (outerCallTree != null) {
                addAll(
                    outerCallTree.children.subListToEnd(1),
                )
            }
        }.mapIndexed { argIndex, argTree ->
            Argument(
                actualCalleeType = actualCalleeType,
                declaredCalleeType = declaredCalleeType,
                argIndex = argIndex,
                tree = argTree,
                adjustments = adjustments,
            )
        }
        val subject = mergedArgumentList[0]
        val otherArgs = mergedArgumentList.subListToEnd(1)

        val connectedMemberInfo = findConnectedMember(members)
        if (connectedMemberInfo != null) {
            run connected@{
                val (connectedMethod, connectedKeyString) = connectedMemberInfo
                val connectedReference = translator.supportNetwork
                    .translateConnectedReference(calleePos, connectedKeyString, subject.tree.document.context.genre)
                    ?: return@connected
                val parameters = mergedArgumentList.map { arg ->
                    TypedArg<TmpL.Tree>(
                        arg.translate(translator),
                        arg.tree.typeOrInvalid,
                    )
                }
                val calleeType = callee.typeOrInvalid
                val unboundCalleeType = when (dotHelper.memberAccessor) {
                    is BindMemberAccessor -> hackTryStaticTypeToSig(
                        BindMethodTypeHelper.uncurry(hackMapNewStyleToOld(calleeType)),
                    )
                    // Fine as-is
                    else -> withType(
                        calleeType,
                        fn = { _, sig, _ -> sig },
                        fallback = { null },
                    )
                }.orInvalid

                if (connectedReference is InlineTmpLSupportCode) {
                    return@translate translatedExpr(
                        connectedReference.inlineToTree(
                            calleePos,
                            parameters,
                            unboundCalleeType.returnType2,
                            translator,
                        ),
                    )
                }
                val name = pool.fillIfAbsent(
                    pos = calleePos,
                    supportCode = connectedReference,
                    desc = unboundCalleeType,
                    metadata = emptyMap(),
                )
                val callable = TmpL.FnReference(TmpL.Id(calleePos, name), unboundCalleeType)

                return@translate when (connectedMethod.methodKind to dotHelper.memberAccessor) {
                    MethodKind.Normal to ExternalBind,
                    MethodKind.Normal to InternalBind,
                    MethodKind.Getter to ExternalGet,
                    MethodKind.Getter to InternalGet,
                    MethodKind.Setter to ExternalSet,
                    MethodKind.Setter to InternalSet,
                    -> translatedExpr(
                        translator.maybeInline(
                            TmpL.CallExpression(
                                pos = pos,
                                fn = callable,
                                parameters = parameters.map { it.expr as TmpL.Actual },
                                type = callType,
                            ),
                        ),
                    )

                    else -> TODO("${connectedMethod.methodKind}, ${dotHelper.memberAccessor}")
                }
            }
        }
        val typeIsConnected = firstMember != null && firstMember.enclosingType.let { typeShape ->
            val connectedKey = typeShape.metadata[connectedSymbol, TString]
            if (connectedKey == null) {
                false
            } else {
                val staticType = MkType2(typeShape)
                    .actuals(typeShape.formals.map { MkType2(it).get() })
                    .get()
                null != translator.supportNetwork.translatedConnectedType(
                    typeShape.pos, connectedKey, translator.genre, staticType,
                )
            }
        }
        val isPulledOutMember = firstMember is VisibleMemberShape && shouldPullOutMember(
            firstMember,
            memberIsConnected = connectedMemberInfo != null,
            typeIsConnected = typeIsConnected,
            isConstructor = false, // Not accessed via dotHelper
        )

        // Handle disconnected members: one's whose type is connected, but it
        // is not so is pulled out to a regular function.
        if (isPulledOutMember) {
            @Suppress("USELESS_IS_CHECK")
            check(firstMember is VisibleMemberShape)
            val sig = callTree.sig.orInvalid
            return translatedExpr(
                TmpL.CallExpression(
                    pos = pos,
                    fn = TmpL.FnReference(
                        TmpL.Id(pos.leftEdge, firstMember.name as ResolvedName, null),
                        sig,
                    ),
                    typeActuals = translator.translateCallTypeActuals(
                        pos = pos.leftEdge,
                        typeActualTrees = typeActuals,
                        callInferences = callTree.typeInferences,
                        sig = sig,
                    ),
                    parameters = mergedArgumentList.map { it.translate(translator) },
                    type = callType,
                ),
            )
        }

        fun propertyId(): TmpL.PropertyId {
            val propShape = firstMember?.let { propertyShapeFor(firstMember) }
            val visibility = when {
                // If a setter, for example, is private, refer to it internally.
                firstMember?.visibility == Visibility.Private -> Visibility.Private
                // Otherwise, use the visibility of the property.
                else -> propShape?.visibility
            }
            return if (
                dotHelper.memberAccessor is InternalMemberAccessor && propShape != null &&
                visibility == Visibility.Private && translator.supportNetwork.splitComputedProperties
            ) {
                TmpL.InternalPropertyId(TmpL.Id(pos.rightEdge, propShape.name as ResolvedName))
            } else {
                TmpL.ExternalPropertyId(
                    TmpL.DotName(pos.rightEdge, dotHelper.symbol.text),
                )
            }
        }

        var translation: TmpL.Expression? = null
        when (dotHelper.memberAccessor) {
            is GetMemberAccessor -> if (otherArgs.isEmpty()) {
                translation = TmpL.GetAbstractProperty(
                    pos = pos,
                    subject = subject.translate(translator) as TmpL.Expression,
                    property = propertyId(),
                    type = callType,
                )
            }
            is SetMemberAccessor -> if (otherArgs.size == 1) {
                val newValue = otherArgs.first()
                return translatedStmt(
                    TmpL.SetAbstractProperty(
                        pos = pos,
                        left = TmpL.PropertyLValue(
                            pos = subject.tree.pos,
                            subject = subject.translate(translator) as TmpL.Expression,
                            property = propertyId(),
                        ),
                        right = newValue.translate(translator) as TmpL.Expression,
                    ),
                )
            }
            is BindMemberAccessor -> if (outerCallTree != null) {
                val method = members.firstOrNull()
                    ?: return TranslatedDotHelper(
                        Either.Left(
                            garbageExpr(pos, "No method matching .${dotHelper.symbol.text} in $subjectTypeDefinition"),
                        ),
                        null,
                        null,
                        null,
                    )
                translation = translateCall(
                    pos = pos,
                    calleePos = calleePos,
                    subject = subject,
                    dotHelper = dotHelper,
                    method = method,
                    typeActuals = typeActuals,
                    callTypeInferences = callTree.typeInferences,
                    args = otherArgs,
                    type = callType,
                    translator = translator,
                )
            }
        }
        return translatedExpr(
            translation
                ?: translator.untranslatableExpr(pos, (outerCallTree ?: callTree).toLispy()),
        )
    }

    private fun translateCall(
        pos: Position,
        calleePos: Position,
        subject: Argument,
        dotHelper: DotHelper,
        method: MethodShape,
        typeActuals: List<Tree>,
        callTypeInferences: CallTypeInferences?,
        args: List<Argument>,
        type: Type2,
        translator: TmpLTranslator,
    ): TmpL.Expression {
        val dotName = TmpL.DotName(
            calleePos,
            dotHelper.symbol.text,
        )
        val sig = method.descriptor.orInvalid
        val translation: TmpL.Expression = translator.maybeInline(
            TmpL.CallExpression(
                pos = pos,
                fn = TmpL.MethodReference(
                    calleePos,
                    subject.translate(translator) as TmpL.Expression,
                    dotName,
                    sig,
                    method,
                ),
                typeActuals = translator.translateCallTypeActuals(
                    pos = calleePos.rightEdge,
                    typeActualTrees = typeActuals,
                    callInferences = callTypeInferences,
                    sig = sig,
                ),
                parameters = args.map { it.translate(translator) },
                type = type,
            ),
        )

        return translation
    }
}

internal fun connectedKeyForMember(member: MemberShape): String? = when (member) {
    is MethodShape -> connectedKeyForMethod(member)
    is PropertyShape -> connectedMethodKeyForProperty(member) {
        GETTER_AFFIX !in it && SETTER_AFFIX !in it
    }
    is VisibleMemberShape -> member.metadata[connectedSymbol, TString]
    else -> null
}

private fun connectedKeyForMethod(methodShape: MethodShape): String? {
    val connectedMethodKey = methodShape.metadata[connectedSymbol, TString]
    if (connectedMethodKey != null) {
        return connectedMethodKey
    }
    // Look on a property definition for a getter or setters member.
    return when (methodShape.methodKind) {
        MethodKind.Normal -> null
        MethodKind.Getter -> propertyShapeFor(methodShape)?.let { propertyShape ->
            connectedMethodKeyForProperty(propertyShape) { GETTER_AFFIX in it }
        }
        MethodKind.Setter -> propertyShapeFor(methodShape)?.let { propertyShape ->
            connectedMethodKeyForProperty(propertyShape) { SETTER_AFFIX in it }
        }
        MethodKind.Constructor -> null
    }
}

/**
 * There may be multiple connected keys attached to a property.
 * Return the first one matching filter which lets us use the `::getFoo` key for
 * a getter and the `::setFoo` key for the setter.
 */
private fun connectedMethodKeyForProperty(
    propertyShape: PropertyShape,
    filter: (String) -> Boolean,
): String? = propertyShape.metadata[connectedSymbol]?.mapFirst { value ->
    val stringContent = TString.unpackOrNull(value)
    if (stringContent != null && filter(stringContent)) {
        stringContent
    } else {
        null
    }
}

private fun propertyShapeFor(methodShape: MethodShape): PropertyShape? =
    methodShape.enclosingType.properties.firstOrNull { it.symbol == methodShape.symbol }

private const val GETTER_AFFIX = "::get"
private const val SETTER_AFFIX = "::set"
