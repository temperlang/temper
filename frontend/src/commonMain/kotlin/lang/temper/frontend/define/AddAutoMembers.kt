package lang.temper.frontend.define

import lang.temper.builtin.EqMacro
import lang.temper.interp.autoSymbol
import lang.temper.interp.vAutoSymbol
import lang.temper.lexer.OperatorType
import lang.temper.log.LogSink
import lang.temper.name.ParsedName
import lang.temper.name.QName
import lang.temper.type.MkType
import lang.temper.type.OperatorMember
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.value.DeclTree
import lang.temper.value.ReifiedType
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TList
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.eqBuiltinName
import lang.temper.value.qNameSymbol
import lang.temper.value.reifiedTypeContained
import lang.temper.value.thisParsedName
import lang.temper.value.typeDeclSymbol
import lang.temper.value.vFnSymbol
import lang.temper.value.vFromTypeSymbol
import lang.temper.value.vImpliedThisSymbol
import lang.temper.value.vInitSymbol
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
        source.insert(at = index) {
            // Prep naming.
            val methodName = nameMaker.unusedSourceName(ParsedName("eq"))
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
                        // TODO Actual logic
                        V(TBoolean.valueTrue)
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
        decl.edges
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
