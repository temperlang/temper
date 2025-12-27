package lang.temper.be.csharp

import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.LibrarySupportCodeRequirement
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SeparatelyCompiledSupportCode
import lang.temper.be.tmpl.SignatureAdjustments
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportCodeRequirement
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.toSigBestEffort
import lang.temper.builtin.RuntimeTypeOperation
import lang.temper.common.subListToEnd
import lang.temper.format.TokenSink
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.DashedIdentifier
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.type.MethodShape
import lang.temper.type.TypeFormal
import lang.temper.type.WellKnownTypes
import lang.temper.type.excludeNullAndBubble
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.NonNullType
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.passTypeOf
import lang.temper.type2.withNullity
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.listBuiltinName
import lang.temper.value.pureVirtualBuiltinName
import kotlin.lazy

object CSharpSupportNetwork : SupportNetwork {
    override val backendDescription = "C# Backend"
    override val bubbleStrategy = BubbleBranchStrategy.CatchBubble
    override val coroutineStrategy = CoroutineStrategy.TranslateToGenerator
    override val functionTypeStrategy = FunctionTypeStrategy.ToFunctionType
    override fun representationOfVoid(genre: Genre) = RepresentationOfVoid.DoNotReifyVoid
    override val simplifyOrTypes: Boolean get() = true

    override fun getSupportCode(pos: Position, builtin: NamedBuiltinFun, genre: Genre): SupportCode? {
        return runCatching { supportCodeByOperatorId(builtin.builtinOperatorId) }.getOrElse {
            // Useful for placing a breakpoint.
            null
        } ?: builtinNames[builtin.name] ?: run {
            // Also useful.
            null
        }
    }

    override fun optionalSupportCode(
        optionalSupportCodeKind: OptionalSupportCodeKind,
    ): Pair<SupportCode, Signature2>? = null

    override fun translateConnectedReference(pos: Position, connectedKey: String, genre: Genre): SupportCode? {
        return connectedReferences[connectedKey] ?: run {
            // Useful for placing a breakpoint.
            null
        }
    }

    override fun translatedConnectedType(
        pos: Position,
        connectedKey: String,
        genre: Genre,
        temperType: Type2,
    ): Pair<AbstractTypeName, List<Type2>>? {
        val (type, parameterAdapter) = connectedTypes[connectedKey] ?: return null
        val parameters = temperType.bindings
        return type to (parameterAdapter?.let { parameterAdapter(parameters) } ?: parameters)
    }

    override fun translateRuntimeTypeOperation(
        pos: Position,
        rto: RuntimeTypeOperation,
        sourceType: TmpL.NominalType,
        targetType: TmpL.NominalType,
    ): SupportCode? {
        if (rto.asLike) {
            when (targetType.typeName.sourceDefinition) {
                WellKnownTypes.noStringIndexTypeDefinition -> return requireNoStringIndex
                WellKnownTypes.stringIndexTypeDefinition -> return requireStringIndex
                else -> {}
            }
        }
        return super.translateRuntimeTypeOperation(pos, rto, sourceType, targetType)
    }

    override fun maybeAdjustMethodSignature(
        unadjustedSignature: Signature2,
        method: MethodShape,
    ): SignatureAdjustments? {
        // If a method specializes a super-type method, then we need to be cognizant of a
        // potential difference between the nullability of an input and its nullability afa
        // type bindings go.
        //
        // Temper
        //    interface I<T>         { public f(x: T?): Void; }
        //    class C extends I<Int> { public f(x: Int): Void { ... } }
        //
        // C#
        //    interface II<T>        { public f(x: Optional<T>): Void; }
        //    class C : II<int> //                 ┃
        //    {            // ┏━━━━━━━━━━━━━━━━━━━━┛
        //      public void F(int? x)
        //      {
        //        ...
        //      }
        //    }
        //
        // You can see in the above that C.F takes an `int?` as it should.
        // But I.f's parameter is Optional<T>, not `T?`.
        // So we need to add an extra overload of F to C:
        //
        //     public void F(C::Optional<T> x) { return F((int?) x); }
        //
        // That second one is the actual override, and it delegates to one
        // which gets a proper `int?`.
        //
        // So if we're looking at an overriding method declaration, produce
        // a list of which arguments need to be adjusted to satisfy the overridden
        // method declaration's constraints.
        val inputAdjustments = mutableListOf<CSharpSignatureAdjustment?>()
        var outputAdjustment: CSharpSignatureAdjustment? = null
        val overrides = method.overriddenMembers ?: emptySet()
        if (overrides.isNotEmpty()) {
            val overrideTypes = overrides.map {
                lazy {
                    it.superTypeMember.enclosingType to toSigBestEffort(it.superTypeMember.descriptor)
                }
            }

            // We need to adjust types in the override that correspond to use of
            // class type formals in any overridden method.
            fun needsOptionalAdjustment(
                fromSubType: Type2,
                getFromOverridden: (Signature2) -> Type2?,
            ): Boolean {
                return !fromSubType.isOptionalTypeArg() &&
                    overrideTypes.any { overrideType ->
                        val (enclosingType, declaredFnType) = overrideType.value
                        val correspType = declaredFnType?.let { ft -> getFromOverridden(ft) }
                        val correspNType = correspType?.let {
                            excludeNullAndBubble(it)
                        } as? NonNullType
                        true == correspType?.isOptionalTypeArg() &&
                            correspNType != null &&
                            // Make sure it's a formal defined on the super type, not a formal
                            // for the method definition.
                            enclosingType.formals.any {
                                it == correspNType.definition
                            }
                    }
            }

            val valueFormals = unadjustedSignature.allValueFormals
            for (i in valueFormals.indices) {
                // Identify where a specialized sub-type method parameter is a value type
                // and the corresponding super-type method parameter is a formal in the enclosing type.
                // For example, `Int` in the subtype and `<T>` in the super.
                val paramType = valueFormals[i].type
                if (
                    i != 0 && // Skip this
                    needsOptionalAdjustment(paramType) {
                        it.valueFormalForActual(i)?.type
                    }
                ) {
                    while (inputAdjustments.size <= i) {
                        inputAdjustments.add(null)
                    }
                    inputAdjustments[i] = WrappedInOptional(
                        paramType.withNullity(Nullity.NonNull)
                            as NonNullType,
                    )
                }
            }
            val returnPassType = passTypeOf(unadjustedSignature.returnType2)
            if (needsOptionalAdjustment(returnPassType) { passTypeOf(it.returnType2) }) {
                outputAdjustment = WrappedInOptional(
                    returnPassType.withNullity(Nullity.NonNull)
                        as NonNullType,
                )
            }
        }

        return if (
            inputAdjustments.any { it != null } ||
            outputAdjustment != null
        ) {
            SignatureAdjustments(
                unadjustedType = unadjustedSignature,
                inputAdjustments = inputAdjustments.toList(),
                outputAdjustment = outputAdjustment,
            )
        } else {
            null
        }
    }

    override fun maybeInsertImplicitCast(
        fromActualType: Type2,
        fromDeclaredType: Type2,
        fromAdjustment: SignatureAdjustments.SignatureAdjustment?,
        toActualType: Type2,
        toDeclaredType: Type2,
        toAdjustment: SignatureAdjustments.SignatureAdjustment?,
        builtinOperatorId: BuiltinOperatorId?,
    ): SupportCode? {
        if (builtinOperatorId == BuiltinOperatorId.IsNull) {
            return null
        }
        val fromDeclaredPassType = passTypeOf(fromDeclaredType)
        val toDeclaredPassType = passTypeOf(toDeclaredType)
        val toActualPassType = passTypeOf(toActualType)
        val hasOptional = fromDeclaredPassType.isOptionalTypeArg() || fromAdjustment is WrappedInOptional
        val needsOptional = toDeclaredPassType.isOptionalTypeArg() || toAdjustment is WrappedInOptional
        return when {
            // do nothing when expectations match
            hasOptional == needsOptional -> null
            // wrap as an optional when needed
            needsOptional -> WrapAsOptional(
                toActualPassType.withNullity(Nullity.NonNull) as NonNullType,
            )
            // receiver has no expectations about the value.  Possibly an is-null check or other RTTI operator.
            toDeclaredPassType.definition == WellKnownTypes.anyValueTypeDefinition -> null
            // unwrap optional
            else -> UnwrapOptional(toActualPassType.withNullity(Nullity.NonNull) as NonNullType)
        }
    }
}

private fun supportCodeByOperatorId(builtinOperatorId: BuiltinOperatorId?): SupportCode? {
    return when (builtinOperatorId) {
        BuiltinOperatorId.BooleanNegation -> BooleanNegationInliner
        BuiltinOperatorId.BitwiseAnd -> bitwiseAnd
        BuiltinOperatorId.BitwiseOr -> bitwiseOr
        BuiltinOperatorId.IsNull -> IsNull
        BuiltinOperatorId.NotNull -> null
        BuiltinOperatorId.DivFltFlt -> divFltFlt
        BuiltinOperatorId.DivIntInt, BuiltinOperatorId.DivIntInt64 -> divIntInt
        BuiltinOperatorId.DivIntIntSafe, BuiltinOperatorId.DivIntInt64Safe -> divIntIntSafe
        BuiltinOperatorId.ModFltFlt -> modFltFlt
        BuiltinOperatorId.ModIntInt, BuiltinOperatorId.ModIntInt64 -> modIntInt
        BuiltinOperatorId.ModIntIntSafe, BuiltinOperatorId.ModIntInt64Safe -> modIntIntSafe
        BuiltinOperatorId.MinusFlt -> minusFlt
        BuiltinOperatorId.MinusFltFlt -> minusFltFlt
        BuiltinOperatorId.MinusInt, BuiltinOperatorId.MinusInt64 -> minusInt
        BuiltinOperatorId.MinusIntInt, BuiltinOperatorId.MinusIntInt64 -> minusIntInt
        BuiltinOperatorId.PlusFltFlt -> plusFltFlt
        BuiltinOperatorId.PlusIntInt, BuiltinOperatorId.PlusIntInt64 -> plusIntInt
        BuiltinOperatorId.TimesIntInt, BuiltinOperatorId.TimesIntInt64 -> timesIntInt
        BuiltinOperatorId.TimesFltFlt -> timesFltFlt
        BuiltinOperatorId.PowFltFlt -> powFltFlt
        BuiltinOperatorId.LtFltFlt -> ltFltFlt
        BuiltinOperatorId.LtIntInt -> ltIntInt
        BuiltinOperatorId.LtStrStr -> ltStrStr
        BuiltinOperatorId.LtGeneric -> ltGeneric
        BuiltinOperatorId.LeFltFlt -> leFltFlt
        BuiltinOperatorId.LeIntInt -> leIntInt
        BuiltinOperatorId.LeStrStr -> leStrStr
        BuiltinOperatorId.LeGeneric -> leGeneric
        BuiltinOperatorId.GtFltFlt -> gtFltFlt
        BuiltinOperatorId.GtIntInt -> gtIntInt
        BuiltinOperatorId.GtStrStr -> gtStrStr
        BuiltinOperatorId.GtGeneric -> gtGeneric
        BuiltinOperatorId.GeFltFlt -> geFltFlt
        BuiltinOperatorId.GeIntInt -> geIntInt
        BuiltinOperatorId.GeStrStr -> geStrStr
        BuiltinOperatorId.GeGeneric -> geGeneric
        BuiltinOperatorId.EqFltFlt -> eqFltFlt
        BuiltinOperatorId.EqIntInt -> eqIntInt
        BuiltinOperatorId.EqStrStr -> eqStrStr
        BuiltinOperatorId.EqGeneric -> EqGeneric // for bool, null, generics, what else?
        BuiltinOperatorId.NeFltFlt -> neFltFlt
        BuiltinOperatorId.NeIntInt -> neIntInt
        BuiltinOperatorId.NeStrStr -> neStrStr
        BuiltinOperatorId.NeGeneric -> neGeneric // only for `!= null` these days?
        BuiltinOperatorId.CmpFltFlt -> TODO()
        BuiltinOperatorId.CmpIntInt -> TODO()
        BuiltinOperatorId.CmpStrStr -> TODO()
        BuiltinOperatorId.CmpGeneric -> cmpGeneric
        BuiltinOperatorId.Bubble, BuiltinOperatorId.Panic -> bubble
        BuiltinOperatorId.Print -> TODO()
        BuiltinOperatorId.StrCat -> StrCat
        BuiltinOperatorId.Listify -> listify
        BuiltinOperatorId.Async -> launchGeneratorAsync
        // should not be used with CoroutineStrategy.TranslateToGenerator
        BuiltinOperatorId.AdaptGeneratorFn,
        BuiltinOperatorId.SafeAdaptGeneratorFn,
        -> null
        null -> null
    }
}

private val bubble =
    Throwing("bubble", StandardNames.temperCoreCoreBubble, builtinOperatorId = BuiltinOperatorId.Bubble)
internal val pureVirtualBuiltin = Throwing(pureVirtualBuiltinName.builtinKey, StandardNames.temperCoreCorePureVirtual)

private val builtinNames = listOf(
    // getStaticBuiltinName to ???,
    pureVirtualBuiltinName to pureVirtualBuiltin,
).associate { (key, value) -> key.builtinKey to value as CSharpSupportCode }

open class CSharpSupportCode(
    val connectedNames: List<String>,
    override val builtinOperatorId: BuiltinOperatorId? = null,
) : NamedSupportCode {
    override val baseName = ParsedName(connectedNames.first())
    override fun renderTo(tokenSink: TokenSink) = tokenSink.name(baseName, inOperatorPosition = false)

    final override fun hashCode(): Int = baseName.hashCode()
    final override fun toString(): String = "CSharpSupportCode($baseName)"
    final override fun equals(other: Any?): Boolean =
        this === other || (other is CSharpSupportCode && baseName == other.baseName)
}

abstract class CSharpInlineSupportCode(
    connectedNames: List<String>,
    builtinOperatorId: BuiltinOperatorId? = null,
    override val needsThisEquivalent: Boolean = false,
) : CSharpSupportCode(connectedNames, builtinOperatorId), InlineSupportCode<CSharp.Tree, CSharpTranslator> {
    constructor(
        baseName: String,
        builtinOperatorId: BuiltinOperatorId? = null,
    ) : this(listOf(baseName), builtinOperatorId)

    open val typesRequired: List<AbstractTypeName> get() = emptyList()

    override val requires: List<SupportCodeRequirement>
        get() = typesRequired.mapNotNullTo(mutableSetOf()) {
            val nameSpace = when (it) {
                is KeyTypeName -> null
                is TypeName -> it.space
            }
            if (nameSpace != null) {
                val names = nameSpace.names
                var libraryRequirement: SupportCodeRequirement? = null
                for (i in 1..names.size) {
                    val libraryName = StandardNames.libraryForNamespace[SpaceName(nameSpace.names.subList(0, i))]
                    if (libraryName != null) {
                        libraryRequirement = LibrarySupportCodeRequirement(libraryName)
                        break
                    }
                }
                libraryRequirement
            } else {
                null
            }
        }.toList()
}

open class CSharpSeparate(
    baseName: String,
    builtinOperatorId: BuiltinOperatorId? = null,
) : CSharpSupportCode(connectedNames = listOf(baseName), builtinOperatorId = builtinOperatorId),
    SeparatelyCompiledSupportCode {
    override val source: DashedIdentifier get() = DashedIdentifier.temperCoreLibraryIdentifier
    override val stableKey: ParsedName get() = baseName
}

internal abstract class CSharpDeclaredInline(
    baseName: String,
) : CSharpSeparate(baseName = baseName) {
    abstract fun inlineToTree(
        pos: Position,
        translator: CSharpTranslator,
    ): CSharp.Expression
}

private fun translateTypeArgs(pos: Position, type: Type2, translator: CSharpTranslator): List<CSharp.Type>? =
    if (type is DefinedNonNullType && type.bindings.isNotEmpty()) {
        val translatedType = translator.translateTypeFromFrontend(pos, type)
        (translatedType as CSharp.ConstructedType).args.map { it.deepCopy() }
    } else {
        null
    }

private class MethodCall(
    connectedNames: List<String>,
    val memberNameId: String,
    builtinOperatorId: BuiltinOperatorId? = null,
    val extraParameters: (Position) -> List<CSharp.Arg> = { emptyList() },
) : CSharpInlineSupportCode(connectedNames, builtinOperatorId) {
    constructor(
        baseName: String,
        memberNameId: String,
        builtinOperatorId: BuiltinOperatorId? = null,
        extraParameters: (Position) -> List<CSharp.Arg> = { emptyList() },
    ) : this(
        connectedNames = listOf(baseName),
        memberNameId = memberNameId,
        builtinOperatorId = builtinOperatorId,
        extraParameters = extraParameters,
    )

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return CSharp.InvocationExpression(
            pos = pos,
            expr = CSharp.MemberAccess(
                pos,
                expr = arguments[0].expr as CSharp.PrimaryExpression,
                id = memberNameId.toIdentifier(pos.rightEdge),
            ),
            args = buildList {
                arguments.subListToEnd(1).forEach { add(it.asExpr()) }
                addAll(extraParameters(pos.rightEdge))
            },
        )
    }
}

internal class ObjectCreation(baseName: String, val typeName: TypeName) : CSharpInlineSupportCode(baseName) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        val rawTypeName = typeName.toTypeName(pos)
        val type = translateTypeArgs(pos, passTypeOf(returnType), translator)?.let { typeArgs ->
            CSharp.ConstructedType(pos, type = rawTypeName, args = typeArgs)
        } ?: CSharp.UnboundType(rawTypeName)
        return CSharp.ObjectCreationExpression(
            pos,
            type = type,
            args = arguments.map { it.asExpr() },
        )
    }

    override val typesRequired: List<AbstractTypeName> get() = listOf(typeName)
}

private class PropertyAccess(
    connectedNames: List<String>,
    val memberNameId: String,
) : CSharpInlineSupportCode(connectedNames) {
    constructor(baseName: String, memberNameId: String) : this(
        connectedNames = listOf(baseName),
        memberNameId = memberNameId,
    )

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return CSharp.MemberAccess(
            pos,
            expr = arguments[0].expr as CSharp.PrimaryExpression,
            id = memberNameId.toIdentifier(pos.rightEdge),
        )
    }
}

internal class StaticCall(
    connectedNames: List<String>,
    val member: MemberName,
    private val guessTypeArgs: Boolean = false,
    builtinOperatorId: BuiltinOperatorId? = null,
) : CSharpInlineSupportCode(connectedNames, builtinOperatorId = builtinOperatorId) {
    constructor(
        baseName: String,
        member: MemberName,
        guessTypeArgs: Boolean = false,
        builtinOperatorId: BuiltinOperatorId? = null,
    ) : this(listOf(baseName), member, guessTypeArgs, builtinOperatorId)

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        // TODO To actually calculate type args, we need the callable's type, which we'd need to provide from tmpl.
        val typeArgs = when (guessTypeArgs) {
            true -> translateTypeArgs(pos, returnType, translator)
            false -> null
        } ?: listOf()
        return CSharp.InvocationExpression(
            pos,
            expr = member.toStaticMember(pos),
            typeArgs = typeArgs,
            args = arguments.map { it.asExpr() },
        )
    }

    override val typesRequired: List<AbstractTypeName>
        get() = listOf(member.type)
}

private class StaticMember(baseName: String, val member: MemberName) : CSharpInlineSupportCode(baseName) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return member.toStaticMember(pos)
    }

    override val typesRequired: List<AbstractTypeName>
        get() = listOf(member.type)
}

internal class Throwing(
    baseName: String,
    val member: MemberName,
    builtinOperatorId: BuiltinOperatorId? = null,
) : CSharpInlineSupportCode(baseName, builtinOperatorId = builtinOperatorId) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        // Throw works as an expression only sometimes in C#, not generally, so call a method, and C# won't infer return
        // types, so be explicit, or nothing at all for void, as we specialize non-generic for that case.
        val typeArgs = when (returnType) {
            is DefinedNonNullType -> when (returnType.definition) {
                WellKnownTypes.voidTypeDefinition -> null
                else -> listOf(translator.translateTypeFromFrontend(pos, returnType))
            }

            else -> null
        } ?: listOf()
        return CSharp.InvocationExpression(
            pos,
            expr = member.toStaticMember(pos),
            typeArgs = typeArgs,
            args = arguments.map { it.asExpr() },
        )
    }

    override val typesRequired: List<AbstractTypeName>
        get() = listOf(member.type)
}

private object BooleanToString : CSharpInlineSupportCode("Boolean::toString") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        // C# bool defaults to "True" & "False", so we need to lowercase.
        return (arguments[0].expr as CSharp.PrimaryExpression).callMethod("ToString").callMethod("ToLower")
    }
}

private object GetConsole : CSharpInlineSupportCode("::getConsole") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        val rootNamespace = translator.names.rootNamespace
        val namespace = listOf(rootNamespace, "Support").toSpaceName()
        val consoleFactory = namespace.type("Logging").member("LoggingConsoleFactory")
        val arg = when {
            arguments.isEmpty() -> CSharp.StringLiteral(pos, translator.fullNamespace.joinToString("."))
            else -> arguments.first().asExpr()
        }
        return CSharp.InvocationExpression(
            pos,
            expr = CSharp.MemberAccess(
                pos,
                expr = consoleFactory.toStaticMember(pos),
                id = "CreateConsole".toIdentifier(pos),
            ),
            args = listOf(arg),
        )
    }
}

/** Invokes StringUtil.CompareStringsByCodePoint with an optional comparison to zero where a boolean is needed */
private class StringComparison(
    override val builtinOperatorId: BuiltinOperatorId,
    val operator: CSharpOperator?,
) : CSharpInlineSupportCode(builtinOperatorId.name) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        val call = CSharp.InvocationExpression(
            pos,
            StandardNames.temperCoreStringUtilCompareStringsByCodePoint.toStaticMember(pos.leftEdge),
            args = arguments.map { it.asExpr() },
        )
        return if (operator != null) {
            // If operator is `<`, this becomes `CompareStrings(...) < 0`
            val rightPos = pos.rightEdge
            CSharp.Operation(
                pos,
                call,
                CSharp.Operator(rightPos, operator),
                CSharp.NumberLiteral(rightPos, 0),
            )
        } else {
            call
        }
    }
}
private val geStrStr = StringComparison(BuiltinOperatorId.GeStrStr, CSharpOperator.GreaterEquals)
private val gtStrStr = StringComparison(BuiltinOperatorId.GtStrStr, CSharpOperator.GreaterThan)
private val leStrStr = StringComparison(BuiltinOperatorId.LeStrStr, CSharpOperator.LessEquals)
private val ltStrStr = StringComparison(BuiltinOperatorId.LtStrStr, CSharpOperator.LessThan)

private val denseBitVectorConstructor =
    ObjectCreation("DenseBitVector::constructor", StandardNames.systemCollectionsBitArray)
private val denseBitVectorGet = StaticCall("DenseBitVector::get", StandardNames.temperCoreCoreBitGet)
private val denseBitVectorSet = StaticCall("DenseBitVector::set", StandardNames.temperCoreCoreBitSet)

private val dequeAdd = MethodCall("Deque::add", "Enqueue")
private val dequeConstructor = ObjectCreation("Deque::constructor", StandardNames.systemCollectionsGenericQueue)
private val dequeRemoveFirst = MethodCall("Deque::removeFirst", "Dequeue")
private val empty = StaticCall("empty", StandardNames.temperCoreCoreEmpty)

private class Float64Compare(
    baseName: String,
    builtinOperatorId: BuiltinOperatorId? = null,
    val operator: CSharpOperator,
) : CSharpInlineSupportCode(baseName, builtinOperatorId) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return CSharp.Operation(
            pos,
            left = CSharp.InvocationExpression(
                pos,
                expr = StandardNames.temperCoreFloat64Compare.toStaticMember(pos),
                args = arguments.map { it.asExpr() },
            ),
            operator = CSharp.Operator(pos, operator),
            right = CSharp.NumberLiteral(pos, 0.0),
        )
    }
}

private class Float64Math(name: String, backendName: String? = null) : CSharpInlineSupportCode("Float64::$name") {
    private val member = StandardNames.systemMath.member(backendName ?: name.camelToPascal())

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return CSharp.InvocationExpression(
            pos,
            expr = member.toStaticMember(pos),
            args = arguments.map { it.asExpr() },
        )
    }
}

private val float64Abs = Float64Math("abs")
private val float64Acos = Float64Math("acos")
private val float64Asin = Float64Math("asin")
private val float64Atan = Float64Math("atan")
private val float64Atan2 = Float64Math("atan2")
private val float64Ceil = Float64Math("ceil", "Ceiling")
private val float64Cos = Float64Math("cos")
private val float64Cosh = Float64Math("cosh")
private val float64E = StaticMember("Float64::e", StandardNames.systemMathE)
private val float64Exp = Float64Math("exp")
private val float64Expm1 = StaticCall("Float64::expm1", StandardNames.temperCoreFloat64ExpM1)
private val float64Floor = Float64Math("floor")
private val float64Log = Float64Math("log")
private val float64Log10 = Float64Math("log10")
private val float64Log1p = StaticCall("Float64::log1p", StandardNames.temperCoreFloat64LogP1)
private val float64Max = Float64Math("max")
private val float64Min = Float64Math("min")
private val float64Near = StaticCall("Float64::near", StandardNames.temperCoreFloat64Near)
private val float64Pi = StaticMember("Float64::pi", StandardNames.systemMathPi)
private val float64Round = Float64Math("round")
private val float64Sign = StaticCall("Float64::sign", StandardNames.temperCoreFloat64Sign)
private val float64Sin = Float64Math("sin")
private val float64Sinh = Float64Math("sinh")
private val float64Sqrt = Float64Math("sqrt")
private val float64Tan = Float64Math("tan")
private val float64Tanh = Float64Math("tanh")
private val float64ToInt = StaticCall("Float64::toInt32", StandardNames.temperCoreFloat64ToInt)
private val float64ToIntUnsafe = Cast(listOf("Float64::toInt32Unsafe"), StandardNames.keyInt)
private val float64ToInt64 = StaticCall("Float64::toInt64", StandardNames.temperCoreFloat64ToInt64)
private val float64ToInt64Unsafe = Cast(listOf("Float64::toInt64Unsafe"), StandardNames.keyLong)
private val float64ToString = StaticCall("Float64::toString", StandardNames.temperCoreFloat64Format)

private val generatorNext = StaticCall("Generator::next", StandardNames.temperCoreCoreGeneratorNext)
private val safeGeneratorNext = StaticCall("SafeGenerator::next", StandardNames.temperCoreCoreGeneratorNext)

// Might be needed for `[Pure]` calls if we ever mark those. See https://stackoverflow.com/a/36757742/2748187
// TODO Optimize away entirely where allowed?
private val ignore = StaticCall("ignore", StandardNames.temperCoreCoreIgnore)

private class Cast(
    connectedNames: List<String>,
    val type: KeyTypeName,
) : CSharpInlineSupportCode(connectedNames) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return CSharp.CastExpression(
            pos,
            type = type.toType(pos.leftEdge),
            expr = arguments[0].asExpr(),
        )
    }
}

private class UnwrapOptional(
    val targetType: NonNullType,
) : CSharpInlineSupportCode(listOf("unwrapOptional<$targetType>")) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        val csharpTargetType = translator.translateTypeFromFrontend(pos.leftEdge, targetType)
        // We need to use one method for value types and one for reference types
        val methodName = if (csharpTargetType.isBuiltinValueType) {
            "ToNullable"
        } else {
            "OrNull"
        }
        return StandardNames.temperCoreOptional.toType(pos.leftEdge).callMethod(
            methodName, arguments[0].asExpr(), typeArgs = listOf(csharpTargetType), pos = pos,
        )
    }
}

private class WrapAsOptional(
    val optionalTypeActual: NonNullType,
) : CSharpInlineSupportCode(listOf("wrapAsOptional:$optionalTypeActual")) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        val csharpTypeActual = translator.translateTypeFromFrontend(pos.leftEdge, optionalTypeActual)
        val arg = arguments[0].asExpr()
        return if (arg is CSharp.Identifier && arg.outName.outputNameText == "null") {
            CSharp.MemberAccess(
                pos = pos,
                expr = optionalTypeOf(csharpTypeActual),
                id = "None".toIdentifier(pos.rightEdge),
            )
        } else {
            CSharp.InvocationExpression(
                pos = pos,
                expr = CSharp.MemberAccess(
                    pos = pos.leftEdge,
                    expr = StandardNames.temperCoreOptional.toType(pos.leftEdge),
                    id = "Of".toIdentifier(pos.leftEdge),
                ),
                typeArgs = listOf(csharpTypeActual),
                args = listOf(arg),
            )
        }
    }
}

private val intToFloat64 = Cast(listOf("Int32::toFloat64"), StandardNames.keyDouble)
private val intToInt64 = Cast(listOf("Int32::toInt64"), StandardNames.keyLong)
private val intToString = StaticCall("Int32::toString", StandardNames.systemConvertToString)
private val intMax = StaticCall("Int32::max", StandardNames.systemMathMax)
private val intMin = StaticCall("Int32::min", StandardNames.systemMathMin)
private val int64Max = StaticCall("Int64::max", StandardNames.systemMathMax)
private val int64Min = StaticCall("Int64::min", StandardNames.systemMathMin)
private val int64ToFloat64 = StaticCall(listOf("Int64::toFloat64"), StandardNames.temperCoreFloat64ToFloat64)
private val int64ToFloat64Unsafe = Cast(listOf("Int64::toFloat64Unsafe"), StandardNames.keyDouble)
private val int64ToInt32 = StaticCall(listOf("Int64::toInt32"), StandardNames.temperCoreCoreToInt)
private val int64ToInt32Unsafe = Cast(listOf("Int64::toInt32Unsafe"), StandardNames.keyInt)
private val int64ToString = StaticCall("Int64::toString", StandardNames.systemConvertToString)

private val listedTypes = listOf("Listed", "List", "ListBuilder")

// Provide a ReadOnlyCollection for immutability, but in typing, we'll represent both List and
// Listed as IReadOnlyList because that's closer to intended Temper semantics vs copy on write.
// And this helper method returns an IReadOnlyList for clarity in typing.
// And we need to guess type args because the list might be empty, and C# needs to know the type.
internal val listify = StaticCall(
    listBuiltinName.builtinKey,
    member = StandardNames.temperCoreListedCreateReadOnlyList,
    guessTypeArgs = true,
)

private val listBuilderAdd = StaticCall("ListBuilder::add", StandardNames.temperCoreListedAdd)
private val listBuilderAddAll = StaticCall("ListBuilder::addAll", StandardNames.temperCoreListedAddAll)
private val listBuilderRemoveLast = StaticCall("ListBuilder::removeLast", StandardNames.temperCoreListedRemoveLast)
private val listBuilderReverse = StaticCall("ListBuilder::reverse", StandardNames.temperCoreListedReverse)
private val listBuilderSort = StaticCall("ListBuilder::sort", StandardNames.temperCoreListedSort)
private val listBuilderSplice = StaticCall("ListBuilder::splice", StandardNames.temperCoreListedSplice)

private object ListedFilter : CSharpInlineSupportCode("Listed::filter") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return commonListedMethod(pos, StandardNames.systemLinqEnumerableWhere, arguments)
    }
}

private val listForEach = StaticCall("List::forEach", StandardNames.temperCoreListedForEach)
private object ListedGet : CSharpInlineSupportCode(listedTypes.map { "$it::get" } + listOf("Mapped::get")) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return CSharp.ElementAccess(
            pos,
            expr = arguments[0].expr as CSharp.PrimaryExpression,
            args = arguments.subListToEnd(1).map { it.asExpr() },
        )
    }
}

private val listedGetOr = StaticCall("Listed::getOr", StandardNames.temperCoreListedGetOr)

private object ListedIsEmpty : CSharpInlineSupportCode(listOf("Deque", "Listed").map { "$it::isEmpty" }) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        // Could consider `!System.Linq.Enumerable.Any()` instead.
        return CSharp.Operation(
            pos,
            left = CSharp.MemberAccess(
                pos,
                expr = arguments[0].expr as CSharp.PrimaryExpression,
                id = "Count".toIdentifier(pos),
            ),
            operator = CSharp.Operator(pos, CSharpOperator.Equals),
            right = CSharp.NumberLiteral(pos, 0),
        )
    }
}

private val listedJoin = StaticCall("Listed::join", StandardNames.temperCoreListedJoin)
private val listedLength = PropertyAccess(listedTypes.map { "$it::length" }, "Count")

private object ListedMap : CSharpInlineSupportCode("Listed::map") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return commonListedMethod(pos, StandardNames.systemLinqEnumerableSelect, arguments)
    }
}

private val listedReduce = StaticCall(
    listOf("reduce", "reduceFrom").map { "Listed::$it" },
    StandardNames.systemLinqEnumerableAggregate,
)

private val listedSlice = StaticCall("Listed::slice", StandardNames.temperCoreListedSlice)
private val listedSorted = StaticCall("Listed::sorted", StandardNames.temperCoreListedSorted)
private val listedToList = StaticCall(listedTypes.map { "$it::toList" }, StandardNames.temperCoreListedToReadOnlyList)

private val listedToListBuilder =
    StaticCall(listedTypes.map { "$it::toListBuilder" }, StandardNames.systemLinqEnumerableToList)

private fun commonListedMethod(
    pos: Position,
    method: MemberName,
    arguments: List<TypedArg<CSharp.Tree>>,
): CSharp.InvocationExpression {
    return CSharp.InvocationExpression(
        pos,
        expr = StandardNames.temperCoreListedToReadOnlyList.toStaticMember(pos),
        args = listOf(
            CSharp.InvocationExpression(
                pos,
                expr = method.toStaticMember(pos),
                args = arguments.map { it.asExpr() },
            ),
        ),
    )
}

private val mappedLength = StaticCall(
    "Mapped::length",
    StandardNames.temperCoreMappedLength,
)
private val mappedGetOr = StaticCall(
    "Mapped::getOr",
    StandardNames.temperCoreMappedGetOr,
)
private val mappedHas = StaticCall(
    "Mapped::has",
    StandardNames.temperCoreMappedHas,
)
private val mappedKeys = StaticCall(
    "Mapped::keys",
    StandardNames.temperCoreMappedKeys,
)
private val mappedValues = StaticCall(
    "Mapped::values",
    StandardNames.temperCoreMappedValues,
)
private val mappedToMap = StaticCall(
    "Mapped::toMap",
    StandardNames.temperCoreMappedToMap,
)
private val mappedToMapBuilder = StaticCall(
    "Mapped::toMapBuilder",
    StandardNames.temperCoreMappedToMapBuilder,
)
private val mappedToList = StaticCall(
    "Mapped::toList",
    StandardNames.temperCoreMappedToList,
)
private val mappedToListBuilder = StaticCall(
    "Mapped::toListBuilder",
    StandardNames.temperCoreMappedToListBuilder,
)
private val mappedToListWith = StaticCall(
    "Mapped::toListWith",
    StandardNames.temperCoreMappedToListWith,
)
private val mappedToListBuilderWith = StaticCall(
    "Mapped::toListBuilderWith",
    StandardNames.temperCoreMappedToListBuilderWith,
)
private val mappedForEach = StaticCall("Mapped::forEach", StandardNames.temperCoreMappedForEach)
private val mapConstructor = StaticCall("Map::constructor", StandardNames.temperCoreMapConstructor)
private val mapBuilderConstructor = ObjectCreation("MapBuilder::constructor", StandardNames.temperCoreOrderedDictionary)
private val mapBuilderRemove = StaticCall("MapBuilder::remove", StandardNames.temperCoreCoreRemoveGet)
private val pairConstructor = ObjectCreation("Pair::constructor", StandardNames.systemCollectionsGenericKeyValuePair)

private object MapBuilderSet : CSharpInlineSupportCode(listOf("ListBuilder::set", "MapBuilder::set")) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return CSharp.Operation(
            pos,
            left = CSharp.ElementAccess(
                pos,
                expr = arguments[0].expr as CSharp.PrimaryExpression,
                args = arguments.subList(1, arguments.size - 1).map { it.asExpr() },
            ),
            operator = CSharp.Operator(pos, CSharpOperator.Assign),
            right = arguments.last().asExpr(),
        )
    }
}

private val powFltFlt =
    StaticCall("PowFltFlt", StandardNames.systemMathPow, builtinOperatorId = BuiltinOperatorId.PowFltFlt)

private object StrCat : CSharpInlineSupportCode("StrCat") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ) = when (arguments.size) {
        0 -> CSharp.StringLiteral(pos, "")
        else -> arguments.subListToEnd(1).fold(arguments[0].asExpr()) { a, b ->
            CSharp.Operation(
                pos,
                left = a,
                operator = CSharp.Operator(pos, CSharpOperator.Addition),
                right = b.asExpr(),
            )
        }
    }
}

private val stringFromCodePoint = StaticCall("String::fromCodePoint", StandardNames.temperCoreCoreStringFromCodePoint)
private val stringFromCodePoints =
    StaticCall("String::fromCodePoints", StandardNames.temperCoreCoreStringFromCodePoints)

// Could also compare `.Length == 0`, but this isn't much more expensive, and people might expect it more.
// See also: https://learn.microsoft.com/en-us/dotnet/fundamentals/code-analysis/quality-rules/ca1820
private val stringIsEmpty = StaticCall("String::isEmpty", StandardNames.keyStringIsNullOrEmpty)

private val stringSplit = StaticCall("String::split", StandardNames.temperCoreCoreSplit)
private val stringToFloat64 = StaticCall("String::toFloat64", StandardNames.temperCoreFloat64ToFloat64)
private val stringToInt = StaticCall("String::toInt32", StandardNames.temperCoreCoreToInt)
private val stringToInt64 = StaticCall("String::toInt64", StandardNames.temperCoreCoreToInt64)
private val stringBuilderAppend = MethodCall(
    "StringBuilder::append",
    "Append",
)
private val stringBuilderToString = MethodCall(
    "StringBuilder::toString",
    "ToString",
)
private val stringBuilderAppendBetween = StaticCall(
    "StringBuilder::appendBetween",
    StandardNames.temperCoreStringUtilAppendBetween,
)
private val stringBuilderAppendCodePoint = StaticCall(
    "StringBuilder::appendCodePoint",
    StandardNames.temperCoreStringUtilAppendCodePoint,
)
private val stringGet = StaticCall("String::get", StandardNames.temperCoreStringUtilGet)
private val stringCountBetween = StaticCall(
    "String::countBetween",
    StandardNames.temperCoreStringUtilCountBetween,
)
private val stringEnd = PropertyAccess("String::end", "Length")
private val stringForEach = StaticCall(
    "String::forEach",
    StandardNames.temperCoreStringUtilForEach,
)
private val stringHasAtLeast = StaticCall(
    "String::hasAtLeast",
    StandardNames.temperCoreStringUtilHasAtLeast,
)
private val stringHasIndex = StaticCall(
    "String::hasIndex",
    StandardNames.temperCoreStringUtilHasIndex,
)
private val stringNext = StaticCall("String::next", StandardNames.temperCoreStringUtilNext)
private val stringPrev = StaticCall("String::prev", StandardNames.temperCoreStringUtilPrev)
private val stringStep = StaticCall("String::step", StandardNames.temperCoreStringUtilStep)
private object StringBegin : CSharpInlineSupportCode("String::begin") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree = CSharp.NumberLiteral(pos, 0)
}
private object StringIndexNone : CSharpInlineSupportCode("StringIndex::none") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree = CSharp.NumberLiteral(pos, -1)
}
private val stringSlice = StaticCall(
    "String::slice",
    StandardNames.temperCoreStringUtilSlice,
)
private val stringIndexOptionCompareTo = MethodCall(
    listOf("StringIndexOption::compareTo"),
    "CompareTo",
)
private val requireStringIndex = StaticCall(
    "requireStringIndex",
    StandardNames.temperCoreStringUtilRequireStringIndex,
)
private val requireNoStringIndex = StaticCall(
    "requireNoStringIndex",
    StandardNames.temperCoreStringUtilRequireNoStringIndex,
)

private val promiseBuilderBreakPromise = StaticCall(
    "PromiseBuilder::breakPromise",
    StandardNames.temperCoreAsyncBreakPromise,
)
private val promiseBuilderCompletePromise = StaticCall(
    "PromiseBuilder::complete",
    StandardNames.temperCoreAsyncCompletePromise,
)
private val promiseBuilderGetPromise = PropertyAccess(
    "PromiseBuilder::getPromise",
    "Task",
)

private val stdNetSend = StaticCall(
    "stdNetSend",
    StandardNames.temperCoreNetCoreStdNetSend,
)

private class CSharpInfixInline(
    baseName: String,
    builtinOperatorId: BuiltinOperatorId,
    private val operator: CSharpOperator,
) : CSharpInlineSupportCode(baseName, builtinOperatorId) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return CSharp.Operation(
            pos,
            left = arguments[0].asExpr(),
            operator = CSharp.Operator(pos, operator),
            right = arguments[1].asExpr(),
        )
    }
}

private class CSharpPrefixInline(
    baseName: String,
    builtinOperatorId: BuiltinOperatorId,
    private val operator: CSharpOperator,
) : CSharpInlineSupportCode(baseName, builtinOperatorId) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return CSharp.Operation(
            pos,
            left = null,
            operator = CSharp.Operator(pos, operator),
            right = arguments[0].asExpr(),
        )
    }
}

private object BooleanNegationInliner : CSharpInlineSupportCode(
    "BooleanNegation", BuiltinOperatorId.BooleanNegation,
) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        val arg = arguments[0].asExpr()
        if (arg is CSharp.Operation && arg.operator.operator == CSharpOperator.BoolComplement) {
            val right = arg.right
            if (arg.left == null && right != null) {
                arg.right = null // release from AST
                return right
            }
        }
        return CSharp.Operation(
            pos,
            left = null,
            operator = CSharp.Operator(pos, CSharpOperator.BoolComplement),
            right = arg,
        )
    }
}

/** Applies for bool, null, generics, what else? */
private object EqGeneric : CSharpInlineSupportCode("EqGeneric", BuiltinOperatorId.EqGeneric) {
    var infixOp = CSharpInfixInline(baseName.nameText, BuiltinOperatorId.EqGeneric, CSharpOperator.Equals)

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        return when (val type = (arguments[0].type as? NonNullType)?.definition) {
            // We can't just use `==` for type parameters in C#.
            is TypeFormal -> {
                CSharp.InvocationExpression(
                    pos = pos,
                    // Two-deep member access and constructed type make this different from other cases.
                    expr = CSharp.MemberAccess(
                        pos = pos,
                        expr = CSharp.MemberAccess(
                            pos = pos,
                            expr = CSharp.ConstructedType(
                                pos = pos,
                                type = StandardNames.systemCollectionsGenericEqualityComparer.toTypeName(pos),
                                args = listOf(
                                    CSharp.TypeArgRef(translator.translateId(TmpL.Id(pos, type.name)), type),
                                ),
                            ),
                            id = "Default".toIdentifier(pos.rightEdge),
                        ),
                        id = "Equals".toIdentifier(pos.rightEdge),
                    ),
                    args = arguments.map { it.asExpr() },
                )
            }

            else -> infixOp.inlineToTree(pos, arguments, returnType, translator)
        }
    }
}

private object TestBail : CSharpInlineSupportCode("Test::bail") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ): CSharp.Tree {
        // Throwing this exception gives us raw text message. Calling Assert.Fail prepends extra text.
        return CSharp.ThrowExpression(
            pos,
            expr = CSharp.ObjectCreationExpression(
                pos,
                type = StandardNames.microsoftVisualStudioTestToolsUnitTestingAssertFailedException.toType(pos),
                args = listOf(
                    CSharp.Operation(
                        pos,
                        left = CSharp.InvocationExpression(
                            pos,
                            expr = CSharp.MemberAccess(
                                pos,
                                expr = arguments.first().asExpr() as CSharp.PrimaryExpression,
                                id = "MessagesCombined".toIdentifier(pos),
                            ),
                            args = listOf(),
                        ),
                        operator = CSharp.Operator(pos, CSharpOperator.NullCoalescing),
                        right = CSharp.StringLiteral(pos, ""),
                    ),
                ),
            ),
        )
    }
}

private val bitwiseAnd = CSharpInfixInline("BitwiseAnd", BuiltinOperatorId.BitwiseAnd, CSharpOperator.And)
private val bitwiseOr = CSharpInfixInline("BitwiseOr", BuiltinOperatorId.BitwiseOr, CSharpOperator.InclusiveOr)
private val cmpGeneric = // Does this need to do something similar to EqGeneric?
    StaticCall("CmpGeneric", StandardNames.temperCoreCoreCompare, builtinOperatorId = BuiltinOperatorId.CmpGeneric)
private val divFltFlt = CSharpInfixInline("DivFltFlt", BuiltinOperatorId.DivFltFlt, CSharpOperator.Division)
private val divIntInt =
    StaticCall("DivIntInt", StandardNames.temperCoreCoreDiv, builtinOperatorId = BuiltinOperatorId.DivIntInt)
private val divIntIntSafe = StaticCall(
    "DivIntIntSafe",
    StandardNames.temperCoreCoreDivSafe,
    builtinOperatorId = BuiltinOperatorId.DivIntIntSafe,
)
private val eqFltFlt = Float64Compare("EqFltFlt", BuiltinOperatorId.EqFltFlt, CSharpOperator.Equals)
private val eqIntInt = CSharpInfixInline("EqIntInt", BuiltinOperatorId.EqIntInt, CSharpOperator.Equals)
private val eqStrStr = CSharpInfixInline("EqStrStr", BuiltinOperatorId.EqStrStr, CSharpOperator.Equals)
private val geFltFlt = Float64Compare("GeFltFlt", BuiltinOperatorId.GeFltFlt, CSharpOperator.GreaterEquals)
private val geGeneric = CSharpInfixInline("GeGeneric", BuiltinOperatorId.GeGeneric, CSharpOperator.GreaterEquals)
private val geIntInt = CSharpInfixInline("GeIntInt", BuiltinOperatorId.GeIntInt, CSharpOperator.GreaterEquals)
private val gtFltFlt = Float64Compare("GtFltFlt", BuiltinOperatorId.GtFltFlt, CSharpOperator.GreaterThan)
private val gtGeneric = CSharpInfixInline("GtGeneric", BuiltinOperatorId.GtGeneric, CSharpOperator.GreaterThan)
private val gtIntInt = CSharpInfixInline("GtIntInt", BuiltinOperatorId.GtIntInt, CSharpOperator.GreaterThan)
private val leFltFlt = Float64Compare("LeFltFlt", BuiltinOperatorId.LeFltFlt, CSharpOperator.LessEquals)
private val leGeneric = CSharpInfixInline("LeGeneric", BuiltinOperatorId.LeGeneric, CSharpOperator.LessEquals)
private val leIntInt = CSharpInfixInline("LeIntInt", BuiltinOperatorId.LeIntInt, CSharpOperator.LessEquals)
private val ltFltFlt = Float64Compare("LtFltFlt", BuiltinOperatorId.LtFltFlt, CSharpOperator.LessThan)
private val ltGeneric = CSharpInfixInline("LtGeneric", BuiltinOperatorId.LtGeneric, CSharpOperator.LessThan)
private val ltIntInt = CSharpInfixInline("LtIntInt", BuiltinOperatorId.LtIntInt, CSharpOperator.LessThan)
private val minusFlt = CSharpPrefixInline("MinusFlt", BuiltinOperatorId.MinusInt, CSharpOperator.Minus)
private val minusInt = CSharpPrefixInline("MinusInt", BuiltinOperatorId.MinusInt, CSharpOperator.Minus)
private val minusFltFlt = CSharpInfixInline("MinusFltFlt", BuiltinOperatorId.MinusFltFlt, CSharpOperator.Subtraction)
private val minusIntInt = CSharpInfixInline("MinusIntInt", BuiltinOperatorId.MinusIntInt, CSharpOperator.Subtraction)
private val modFltFlt = CSharpInfixInline("ModFltFlt", BuiltinOperatorId.ModFltFlt, CSharpOperator.Remainder)
private val modIntInt =
    StaticCall("ModIntInt", StandardNames.temperCoreCoreMod, builtinOperatorId = BuiltinOperatorId.ModIntInt)
private val modIntIntSafe = StaticCall(
    "ModIntIntSafe",
    StandardNames.temperCoreCoreModSafe,
    builtinOperatorId = BuiltinOperatorId.ModIntIntSafe,
)
private val neFltFlt = Float64Compare("NeFltFlt", BuiltinOperatorId.NeFltFlt, CSharpOperator.NotEquals)
private val neGeneric = CSharpInfixInline("NeGeneric", BuiltinOperatorId.NeGeneric, CSharpOperator.NotEquals)
private val neIntInt = CSharpInfixInline("NeIntInt", BuiltinOperatorId.NeIntInt, CSharpOperator.NotEquals)
private val neStrStr = CSharpInfixInline("NeStrStr", BuiltinOperatorId.NeStrStr, CSharpOperator.NotEquals)
private val plusFltFlt = CSharpInfixInline("PlusFltFlt", BuiltinOperatorId.PlusFltFlt, CSharpOperator.Addition)
private val plusIntInt = CSharpInfixInline("PlusIntInt", BuiltinOperatorId.PlusIntInt, CSharpOperator.Addition)
private val timesFltFlt = CSharpInfixInline("TimesFltFlt", BuiltinOperatorId.TimesFltFlt, CSharpOperator.Multiplication)
private val timesIntInt = CSharpInfixInline("TimesIntInt", BuiltinOperatorId.TimesIntInt, CSharpOperator.Multiplication)

private object IsNull : CSharpInlineSupportCode("isNull", BuiltinOperatorId.IsNull) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<CSharp.Tree>>,
        returnType: Type2,
        translator: CSharpTranslator,
    ) = translator.translateIsNull(pos, arguments[0].expr as CSharp.Expression, arguments[0].type)
}

private val regexCompileFormatted =
    StaticCall("Regex::compileFormatted", StandardNames.temperStdRegexRegexSupport.member("CompileFormatted"))
private val regexCompiledFind =
    StaticCall("Regex::compiledFind", StandardNames.temperStdRegexRegexSupport.member("CompiledFind"))
private val regexCompiledFound =
    StaticCall("Regex::compiledFound", StandardNames.temperStdRegexRegexSupport.member("CompiledFound"))
private val regexCompiledReplace =
    StaticCall("Regex::compiledReplace", StandardNames.temperStdRegexRegexSupport.member("CompiledReplace"))
private val regexCompiledSplit =
    StaticCall("Regex::compiledSplit", StandardNames.temperStdRegexRegexSupport.member("CompiledSplit"))
private val regexFormatterAdjustCodeSet =
    StaticCall("RegexFormatter::adjustCodeSet", StandardNames.temperStdRegexRegexSupport.member("AdjustCodeSet"))
private val regexFormatterPushCodeTo =
    StaticCall("RegexFormatter::pushCodeTo", StandardNames.temperStdRegexRegexSupport.member("PushCodeTo"))

private val dateConstructor = ObjectCreation("Date::constructor", StandardNames.systemDateTime)
private val dateGetDay = PropertyAccess("Date::getDay", "Day")
private val dateGetDayOfWeek = StaticCall(
    "Date::getDayOfWeek",
    StandardNames.temperStdTemporalTemporalSupportIsoWeekdayNum,
)
private val dateGetMonth = PropertyAccess("Date::getMonth", "Month")
private val dateGetYear = PropertyAccess("Date::getYear", "Year")
private val dateToString = MethodCall(
    baseName = "Date::toString",
    memberNameId = "ToString",
    builtinOperatorId = null,
) { pos ->
    // Pass format string for ISO dates to ToString.
    listOf(CSharp.StringLiteral(pos, "yyyy-MM-dd"))
}
private val dateFromIsoString =
    StaticCall("Date::fromIsoString", StandardNames.temperStdTemporalTemporalSupportFromIsoString)
private val dateToday = StaticCall("Date::today", StandardNames.temperStdTemporalTemporalSupportToday)
private val dateYearsBetween =
    StaticCall("Date::yearsBetween", StandardNames.temperStdTemporalTemporalSupportYearsBetween)

private val launchGeneratorAsync =
    StaticCall("async", StandardNames.temperCoreAsync.member("LaunchGeneratorAsync"))

private val connectedReferences = listOf(
    BooleanToString,
    GetConsole,
    ListedFilter,
    ListedGet,
    ListedIsEmpty,
    ListedMap,
    MapBuilderSet,
    StringBegin,
    StringIndexNone,
    TestBail,
    dateConstructor,
    dateFromIsoString,
    dateGetDay,
    dateGetDayOfWeek,
    dateGetMonth,
    dateGetYear,
    dateToString,
    dateToday,
    dateYearsBetween,
    denseBitVectorConstructor,
    denseBitVectorGet,
    denseBitVectorSet,
    dequeAdd,
    dequeConstructor,
    dequeRemoveFirst,
    empty,
    float64Abs,
    float64Acos,
    float64Asin,
    float64Atan,
    float64Atan2,
    float64Ceil,
    float64Cos,
    float64Cosh,
    float64E,
    float64Exp,
    float64Expm1,
    float64Floor,
    float64Log,
    float64Log10,
    float64Log1p,
    float64Max,
    float64Min,
    float64Near,
    float64Pi,
    float64Round,
    float64Sign,
    float64Sin,
    float64Sinh,
    float64Sqrt,
    float64Tan,
    float64Tanh,
    float64ToInt,
    float64ToIntUnsafe,
    float64ToInt64,
    float64ToInt64Unsafe,
    float64ToString,
    generatorNext,
    ignore,
    intMax,
    intMin,
    intToFloat64,
    intToInt64,
    intToString,
    int64Max,
    int64Min,
    int64ToFloat64,
    int64ToFloat64Unsafe,
    int64ToInt32,
    int64ToInt32Unsafe,
    int64ToString,
    listBuilderAdd,
    listBuilderAddAll,
    listBuilderRemoveLast,
    listBuilderReverse,
    listBuilderSort,
    listBuilderSplice,
    listForEach,
    listedGetOr,
    listedJoin,
    listedLength,
    listedReduce,
    listedSlice,
    listedSorted,
    listedToList,
    listedToListBuilder,
    mapBuilderConstructor,
    mapBuilderRemove,
    mapConstructor,
    mappedForEach,
    mappedGetOr,
    mappedHas,
    mappedKeys,
    mappedLength,
    mappedToList,
    mappedToListBuilder,
    mappedToListBuilderWith,
    mappedToListWith,
    mappedToMap,
    mappedToMapBuilder,
    mappedValues,
    pairConstructor,
    promiseBuilderBreakPromise,
    promiseBuilderCompletePromise,
    promiseBuilderGetPromise,
    regexCompileFormatted,
    regexCompiledFind,
    regexCompiledFound,
    regexCompiledReplace,
    regexCompiledSplit,
    regexFormatterAdjustCodeSet,
    regexFormatterPushCodeTo,
    safeGeneratorNext,
    stringBuilderAppend,
    stringBuilderAppendBetween,
    stringBuilderAppendCodePoint,
    stringBuilderToString,
    stringCountBetween,
    stringEnd,
    stringForEach,
    stringFromCodePoint,
    stringFromCodePoints,
    stringGet,
    stringHasAtLeast,
    stringHasIndex,
    stringIndexOptionCompareTo,
    stringIsEmpty,
    stringNext,
    stringPrev,
    stringSlice,
    stringSplit,
    stringStep,
    stringToFloat64,
    stringToInt,
    stringToInt64,
    stdNetSend,
).flatMap { ref -> ref.connectedNames.map { it to ref } }.toMap()

private val connectedTypes = mapOf<String, Pair<AbstractTypeName, ((List<Type2>) -> List<Type2>)?>>(
    "Date" to (StandardNames.systemDateTime to null),
    "Empty" to (StandardNames.systemTuple to { listOf(WellKnownTypes.anyValueType2.withNullity(Nullity.OrNull)) }),
    // Task<T>
    "Promise" to (StandardNames.systemThreadingTasksTask to null),
    // TaskCompletionSource<T>
    "PromiseBuilder" to (StandardNames.systemThreadingTasksTaskCompletionSource to null),
    "StringIndexOption" to (StandardNames.keyInt to null),
    "NetResponse" to (StandardNames.temperCoreNetINetResponse to null),
    "NoStringIndex" to (StandardNames.keyInt to null),
    "StringIndex" to (StandardNames.keyInt to null),
    "StringBuilder" to (StandardNames.systemTextStringBuilder to null),
)
