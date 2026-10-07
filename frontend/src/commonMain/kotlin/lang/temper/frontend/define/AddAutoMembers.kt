package lang.temper.frontend.define

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.EqMacro
import lang.temper.builtin.dotHelperForOperator
import lang.temper.common.OpenOrClosed
import lang.temper.interp.autoSymbol
import lang.temper.interp.vAutoSymbol
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
import lang.temper.value.eqBuiltinName
import lang.temper.value.qNameSymbol
import lang.temper.value.reifiedTypeContained
import lang.temper.value.staySymbol
import lang.temper.value.thisParsedName
import lang.temper.value.typeDeclSymbol
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
    private val typeDecl = typeShape.stayLeaf?.incoming?.source as? DeclTree
    private val reifiedType = typeDecl?.parts?.metadataSymbolMap?.get(typeDeclSymbol)?.reifiedTypeContained

    fun addAutosIfWanted() {
        reifiedType ?: return
        val auto = typeDecl?.parts?.metadataSymbolMap?.get(autoSymbol)?.target?.valueContained ?: return
        addWantedAutos(auto)
    }

    private fun addWantedAutos(auto: Value<*>) {
        // TODO Lists and/or rest args of autos.
        when (TString.unpackOrNull(auto)) {
            eqBuiltinName.builtinKey -> addAutoEq()
        }
    }

    private fun addAutoEq() {
        // TODO First check if one already exists.
        val source = edge.source ?: return
        val index = source.edges.indexOf(edge)
        val typeValue = Value(reifiedType!!)
        val typeQNameValue = typeDecl!!.parts?.metadataSymbolMap?.get(qNameSymbol)?.valueContained ?: return
        val qNameBuilder = QName.Builder(QName.fromString(TString.unpack(typeQNameValue)).result!!)
        val typeShape = reifiedType.type2.definition as? MutableTypeShape ?: return
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
                        V(typeValue)
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
                    Block {
                        V(vLabelSymbol)
                        Ln(nameMaker.unusedTemporaryName("fn"))
                        // Build check logic recursively for all (public for now) properties.
                        val properties = typeShape.properties
                        val eqHelper = Value(dotHelperForOperator(OperatorMember(eqSpec)))
                        fun Planting.buildIf(propertyIndex: Int) {
                            // Skip ahead until we find a readable property.
                            val (property, foundIndex) = run nextIndex@{
                                properties@ for (nextIndex in propertyIndex..<properties.size) {
                                    val property = properties[nextIndex]
                                    // For now, just check public properties.
                                    // TODO Also check private properties once we loosen rules.
                                    // TODO For autoEq classes, we should ensure other's type matches this's type.
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
                                    Rn(otherName)
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

private val eqSpec = OperatorMember.from(EqMacro.name, OperatorType.Infix).operatorSpecifier

// Copied from ClosureConvertClasses.
private val symbolOrNullList = lazy {
    MkType.nominal(
        WellKnownTypes.listTypeDefinition,
        listOf(MkType.nullable(WellKnownTypes.symbolType)),
    )
}
