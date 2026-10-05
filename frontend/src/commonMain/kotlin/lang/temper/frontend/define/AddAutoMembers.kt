package lang.temper.frontend.define

import lang.temper.frontend.Module
import lang.temper.interp.autoSymbol
import lang.temper.log.LogSink
import lang.temper.name.ParsedName
import lang.temper.name.QName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.Symbol
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.value.DeclTree
import lang.temper.value.ReifiedType
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.eqBuiltinName
import lang.temper.value.qNameSymbol
import lang.temper.value.reifiedTypeContained
import lang.temper.value.thisParsedName
import lang.temper.value.typeDeclSymbol
import lang.temper.value.vImpliedThisSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vReturnDeclSymbol
import lang.temper.value.vReturnedFromSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.vWordSymbol
import lang.temper.value.valueContained

internal fun addAutoMembers(
    module: Module,
    convertedTypeInfo: ConvertedTypeInfo,
    logSink: LogSink,
) {
    val nameMaker = ResolvedNameMaker(module.namingContext, module.genre)
    infos@ for ((typeShape, edge) in convertedTypeInfo.typesAndEdgePastLastMember) {
        val adder = AutoMemberAdder(nameMaker, typeShape, edge ?: continue@infos, logSink)
        adder.addAutosIfWanted()
    }
}

private class AutoMemberAdder(
    private val nameMaker: ResolvedNameMaker,
    private val typeShape: TypeShape,
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
        val typeQNameValue = typeDecl!!.parts?.metadataSymbolMap?.get(qNameSymbol)?.valueContained ?: return
        val typeQName = QName.fromString(TString.unpack(typeQNameValue)).result!!
        source.insert(at = index) {
            Decl {
                val methodName = nameMaker.unusedSourceName(ParsedName("eq"))
                Ln(methodName)
                V(vInitSymbol)
                Fn {
                    val typeValue = Value(reifiedType!!)
                    // Parameters.
                    val thisName = nameMaker.unusedSourceName(thisParsedName)
                    Decl {
                        Ln(thisName)
                        V(vTypeSymbol)
                        V(typeValue)
                        V(vImpliedThisSymbol)
                        V(typeValue)
                    }
                    val otherName = nameMaker.unusedSourceName(ParsedName("other"))
                    Decl {
                        Ln(otherName)
                        V(vTypeSymbol)
                        V(typeValue)
                        V(vWordSymbol)
                        V(otherName.toSymbol())
                    }
                    // Return value.
                    V(vReturnDeclSymbol)
                    Decl {
                        nameMaker.unusedSourceName(ParsedName("return"))
                        V(vTypeSymbol)
                        V(Value(ReifiedType(WellKnownTypes.booleanType2)))
                    }
                    V(vReturnedFromSymbol)
                    V(TBoolean.valueTrue)
                    // Naming.
                    V(vWordSymbol)
                    V(methodName.toSymbol())
                }
            }
        }
    }
}
