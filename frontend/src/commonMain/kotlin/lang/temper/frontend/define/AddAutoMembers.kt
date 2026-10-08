package lang.temper.frontend.define

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.EqMacro
import lang.temper.builtin.dotHelperForOperator
import lang.temper.common.OpenOrClosed
import lang.temper.interp.BreakTransform
import lang.temper.lexer.OperatorType
import lang.temper.log.LogSink
import lang.temper.name.ParsedName
import lang.temper.name.QName
import lang.temper.name.SourceName
import lang.temper.type.Abstractness
import lang.temper.type.DotHelper
import lang.temper.type.DotMember
import lang.temper.type.ExternalGet
import lang.temper.type.InternalGet
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.MkType
import lang.temper.type.MutableTypeShape
import lang.temper.type.OperatorMember
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.value.DeclTree
import lang.temper.value.Planting
import lang.temper.value.ReifiedType
import lang.temper.value.StayLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TList
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.autoSymbol
import lang.temper.value.eqBuiltinName
import lang.temper.value.qNameSymbol
import lang.temper.value.reifiedTypeContained
import lang.temper.value.sealedTypeSymbol
import lang.temper.value.staySymbol
import lang.temper.value.thisParsedName
import lang.temper.value.typeDeclSymbol
import lang.temper.value.vAutoSymbol
import lang.temper.value.vFnSymbol
import lang.temper.value.vFromTypeSymbol
import lang.temper.value.vImpliedThisSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vLabelSymbol
import lang.temper.value.vMethodSymbol
import lang.temper.value.vOperatorSymbol
import lang.temper.value.vParameterNameSymbolsListSymbol
import lang.temper.value.vPublicSymbol
import lang.temper.value.vQNameSymbol
import lang.temper.value.vReturnDeclSymbol
import lang.temper.value.vReturnedFromSymbol
import lang.temper.value.vSsaSymbol
import lang.temper.value.vStaySymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.vVisibilitySymbol
import lang.temper.value.vWordSymbol
import lang.temper.value.valueContained
import lang.temper.value.void

internal fun addAutoMembers(
    convertedTypeInfo: ConvertedTypeInfo,
    logSink: LogSink,
) {
    infos@ for ((typeShape, edge) in convertedTypeInfo.typesAndEdgePastLastMember) {
        val adder = AutoMemberAdder(typeShape, edge ?: continue@infos, logSink)
        adder.addAutosIfWanted()
    }
}

private class AutoMemberAdder(
    typeShape: TypeShape,
    private val edge: TEdge,
    private val logSink: LogSink,
) {
    private val typeDecl = typeShape.decl()
    private val reifiedType = typeDecl?.reifiedType()

    fun addAutosIfWanted() {
        reifiedType ?: return
        // First check our own auto keys.
        val autoKeys = typeDecl?.autoKeys() ?: listOf()
        for (autoKey in autoKeys) {
            when (autoKey) {
                eqBuiltinName.builtinKey -> addAutoEq()
            }
        }
        // Also check the parents' auto keys, just in case.
        addAutoEqForSuperSealedIfWanted(autoKeys)
    }

    private fun addAutoEqForSuperSealedIfWanted(autoKeys: List<String>) {
        eqBuiltinName.builtinKey in autoKeys && return
        val typeShape = reifiedType!!.type2.definition as? TypeShape ?: return
        // We care about this type only if it's concrete or sealed.
        typeShape.abstractness == Abstractness.Concrete || typeShape.decl()?.isSealed() == true || return
        val sealedAutoEqProgenitors = buildList digSealedProgenitors@{
            fun dig(dugShape: TypeShape) {
                superTypes@ for (superType in dugShape.superTypes) {
                    val superShape = superType.definition as? TypeShape ?: continue@superTypes
                    // If we're a subtype of a sealed type and we're valid (checked elsewhere) then it should have a
                    // non-null list of sealed subtypes, and we would have to be in it.
                    (superShape.sealedSubTypes ?: listOf()).isNotEmpty() || continue@superTypes
                    // So if we get this far, the supertype is sealed. Check autos.
                    val oldSize = size
                    dig(superShape)
                    if (size == oldSize) {
                        // No earlier sealed auto eq progenitors found, so see if this is auto eq.
                        // TODO Ideally we track the progenitors of each auto in one recursive dig, but sloppily just
                        // TODO seek `==` for now.
                        // TODO Would we be building a multimap instead for each key?
                        if (eqBuiltinName.builtinKey in (superShape.decl()?.autoKeys() ?: listOf())) {
                            add(superShape)
                        }
                    }
                }
            }
            dig(typeShape)
        }
        sealedAutoEqProgenitors.isEmpty() && return
        if (sealedAutoEqProgenitors.size > 1) {
            // TODO Log problem.
            return
        }
        // We have just one super type that we need to add an auto eq for.
        // TODO Use the reified type of the super for the `other` param.
        val progenitor = sealedAutoEqProgenitors.first()
        addAutoEq(progenitor.decl()?.reifiedType())
    }

    private fun addAutoEq(otherType: ReifiedType? = null) {
        val source = edge.source ?: return
        val index = source.edges.indexOf(edge)
        val typeValue = Value(reifiedType!!)
        val typeQNameValue = typeDecl!!.parts?.metadataSymbolMap?.get(qNameSymbol)?.valueContained ?: return
        val qNameBuilder = QName.Builder(QName.fromString(TString.unpack(typeQNameValue)).result!!)
        val typeShape = reifiedType.type2.definition as? MutableTypeShape ?: return
        // Check kind.
        if (typeShape.abstractness == Abstractness.Abstract) {
            // If abstract but the otherType isn't us, then we'll inherit the pure virtual.
            otherType == null || return
            // But don't support auto eq for non-sealed interfaces.
            // TODO Log error.
            sealedTypeSymbol in typeDecl.parts!!.metadataSymbolMap || return
            // We also need concrete subtypes. Don't bother checking deep because if all our sealed subtypes are valid
            // (checked elsewhere), they'll also end up getting here, so eventually everything will be checked.
            (typeShape.sealedSubTypes ?: listOf()).all { sealedSub ->
                sealedSub.abstractness == Abstractness.Concrete || sealedSub.decl()?.isSealed() == true
            } || return
        }
        // TODO Error or at least bail if an `==` already exists.
        var methodName: SourceName? = null
        source.insert(at = index) {
            // Prep naming.
            methodName = nameMaker.unusedSourceName(ParsedName("eq"))
            qNameBuilder.fn(methodName.baseName)
            val qNameBuilderStart = qNameBuilder.partCount
            fun qNameValue(): Value<String> {
                return Value("${qNameBuilder.toQName()}").also {
                    qNameBuilder.resetPartCount(qNameBuilderStart)
                }
            }
            val thisName = nameMaker.unusedSourceName(thisParsedName)
            val otherName = nameMaker.unusedSourceName(ParsedName("other"))
            val returnName = nameMaker.unusedSourceName(ParsedName("return"))
            // Start decl.
            Decl {
                Ln(methodName)
                V(vInitSymbol)
                Fn {
                    // Parameters.
                    Decl {
                        Ln(thisName)
                        V(vTypeSymbol)
                        V(typeValue)
                        V(vImpliedThisSymbol)
                        V(typeValue)
                        V(vQNameSymbol)
                        qNameBuilder.input(thisName.baseName)
                        V(qNameValue())
                    }
                    Decl {
                        Ln(otherName)
                        V(vTypeSymbol)
                        V(Value(otherType ?: reifiedType))
                        V(vWordSymbol)
                        V(otherName.toSymbol())
                        V(vQNameSymbol)
                        qNameBuilder.input(otherName.baseName)
                        V(qNameValue())
                    }
                    // Return value.
                    V(vReturnDeclSymbol)
                    Decl {
                        Ln(returnName)
                        V(vTypeSymbol)
                        V(Value(ReifiedType(WellKnownTypes.booleanType2)))
                        V(vQNameSymbol)
                        qNameBuilder.local(returnName.baseName)
                        V(qNameValue())
                        V(vSsaSymbol)
                        V(void)
                    }
                    V(vReturnedFromSymbol)
                    V(TBoolean.valueTrue)
                    // Naming.
                    V(vWordSymbol)
                    V(methodName.toSymbol())
                    V(vQNameSymbol)
                    V(qNameValue())
                    // Body.
                    Block body@{
                        if (typeShape.abstractness == Abstractness.Abstract) {
                            return@body Call { V(Value(BuiltinFuns.pureVirtualFn)) }
                        }
                        // Concrete type, so check contents.
                        V(vLabelSymbol)
                        val labelName = nameMaker.unusedTemporaryName("fn")
                        Ln(labelName)
                        // If we have a super otherType, we need to check and cast.
                        val (typedOther, initTypedOther) = when (otherType) {
                            null -> otherName to null
                            else -> {
                                // Bail early unless right type.
                                If(
                                    cond = {
                                        Call {
                                            V(BuiltinFuns.vNotFn)
                                            Call {
                                                V(BuiltinFuns.vIsFn)
                                                Rn(otherName)
                                                V(typeValue)
                                            }
                                        }
                                    },
                                    thn = {
                                        Call {
                                            V(BuiltinFuns.vSetLocalFn)
                                            Ln(returnName)
                                            V(TBoolean.valueFalse)
                                        }
                                        Call {
                                            V(Value(BreakTransform))
                                            V(vLabelSymbol)
                                            Rn(labelName)
                                        }
                                    },
                                    els = { V(void) },
                                )
                                // If right type, get an assert-cast version of it.
                                // Because we aren't embedding in an if block, we don't infer this in AutoCast.
                                val typedOther = nameMaker.unusedSourceName(ParsedName("other"))
                                typedOther to {
                                    Decl {
                                        // SimplifyDeclarations comes later, so use combo decl/init.
                                        Ln(typedOther)
                                        V(vInitSymbol)
                                        Call {
                                            V(BuiltinFuns.vAssertAsFn)
                                            Rn(otherName)
                                            V(typeValue)
                                        }
                                    }
                                }
                            }
                        }
                        // Build check logic recursively for all (public for now) properties.
                        val properties = typeShape.properties
                        val eqHelper = Value(dotHelperForOperator(OperatorMember(eqSpec)))
                        var startedChecking = false
                        fun Planting.buildIf(propertyIndex: Int) {
                            // Skip ahead until we find a readable property.
                            val (property, foundIndex) = run nextIndex@{
                                properties@ for (nextIndex in propertyIndex..<properties.size) {
                                    val property = properties[nextIndex]
                                    // For now, just check public properties.
                                    // For autoEq classes, by the time we get here, other's type matches this's type.
                                    // TODO Also check private properties once we loosen rules.
                                    // TODO Log errors if can't check all properties?
                                    property.visibility == Visibility.Public || continue@properties
                                    // Also bail out on setter-only properties.
                                    // TODO Is this the right way to detect such?
                                    property.setter == null || property.getter != null || continue@properties
                                    // Seems good now.
                                    return@nextIndex property to nextIndex
                                }
                                // No readable properties found. Should only happen if no readables are found.
                                V(TBoolean.valueTrue)
                                return@buildIf
                            }
                            if (!startedChecking) {
                                initTypedOther?.invoke()
                                startedChecking = true
                            }
                            // Build condition.
                            fun Planting.buildCond() = Call {
                                V(EqMacro.value)
                                // This property.
                                Call {
                                    // Jump hoops to match what we see elsewhere.
                                    // TODO Make/access common helpers?
                                    when (property.abstractness) {
                                        Abstractness.Abstract -> {
                                            V(Value(DotHelper(InternalGet, DotMember(property.symbol))))
                                            V(typeValue)
                                        }
                                        Abstractness.Concrete -> {
                                            V(Value(BuiltinFuns.getpFn))
                                            Rn(property.name)
                                        }
                                    }
                                    Rn(thisName)
                                }
                                // Other property.
                                Call {
                                    V(Value(DotHelper(ExternalGet, DotMember(property.symbol))))
                                    Rn(typedOther)
                                }
                                V(eqHelper)
                            }
                            // See how we want to use it.
                            when (foundIndex) {
                                // Last case can just use the condition expression directly.
                                properties.size - 1 -> buildCond()
                                // Otherwise build `if` recursively.
                                else -> If(
                                    cond = { buildCond() },
                                    thn = { buildIf(propertyIndex + 1) },
                                    els = { V(TBoolean.valueFalse) },
                                )
                            }
                        }
                        // Start at the first property.
                        buildIf(0)
                    }
                }
                // Metadata.
                V(vMethodSymbol) // key
                V(methodName.toSymbol())
                V(vVisibilitySymbol) // key
                V(vPublicSymbol)
                V(vFnSymbol) // key
                V(void)
                V(vOperatorSymbol) // key
                V(Value(eqSpec))
                V(vAutoSymbol) // key
                V(Value(EqMacro.name))
                V(vQNameSymbol) // key
                V(qNameValue())
                V(vSsaSymbol) // key
                V(void)
                V(vStaySymbol) // key
                Stay()
                V(vParameterNameSymbolsListSymbol) // key
                V(Value(listOf(Value(otherName.toSymbol()), TNull.value), TList), symbolOrNullList.value)
                V(vFromTypeSymbol) // key
                V(typeValue)
            }
        }
        val decl = source.child(index) as DeclTree
        MethodShape(
            enclosingType = typeShape,
            name = methodName!!,
            symbol = methodName.toSymbol(),
            stay = decl.parts!!.metadataSymbolMap[staySymbol]!!.target as StayLeaf,
            visibility = Visibility.Public,
            methodKind = MethodKind.Normal,
            openness = OpenOrClosed.Closed,
        ).also { typeShape.methods.add(it) }
    }
}

/**
 * TODO Support varargs or list of auto strings.
 */
private fun DeclTree.autoKeys(): List<String> {
    val autoValue = parts?.metadataSymbolMap?.get(autoSymbol)?.target?.valueContained
    return TString.unpackOrNull(autoValue)?.let { listOf(it) } ?: listOf()
}

private fun DeclTree.isSealed(): Boolean {
    return parts?.metadataSymbolMap?.let { sealedTypeSymbol in it } == true
}

private fun DeclTree.reifiedType(): ReifiedType? {
    return parts?.metadataSymbolMap?.get(typeDeclSymbol)?.reifiedTypeContained
}

private fun TypeShape.decl(): DeclTree? =
    stayLeaf?.incoming?.source as? DeclTree

private val eqSpec = OperatorMember.from(EqMacro.name, OperatorType.Infix).operatorSpecifier

// Copied from ClosureConvertClasses.
private val symbolOrNullList = lazy {
    MkType.nominal(
        WellKnownTypes.listTypeDefinition,
        listOf(MkType.nullable(WellKnownTypes.symbolType)),
    )
}
