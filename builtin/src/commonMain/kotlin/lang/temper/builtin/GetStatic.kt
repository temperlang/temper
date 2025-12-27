package lang.temper.builtin

import lang.temper.env.BindingNamingContext
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.name.ModularName
import lang.temper.name.Symbol
import lang.temper.type.NominalType
import lang.temper.type.StaticPropertyShape
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.SpecialFunction
import lang.temper.value.TFunction
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.connectedSymbol
import lang.temper.value.getStaticBuiltinName
import lang.temper.value.internalGetStaticBuiltinName
import lang.temper.value.symbolContained
import lang.temper.value.typeFromSignature

/**
 * Reads a static member.
 * It expects as arguments:
 * 1. A reified type whose member should be read.
 * 2. A symbol specifying the name of the member to read.
 */
sealed class GetStaticOp : SpecialFunction, NamedBuiltinFun {
    override val sigs: List<Signature2>? = null

    /**
     * Static checks require that `@static` properties be initialized before read, without failure,
     * and that all static references are well-formed. TODO(mikesamuel): make this true
     * Given that, a static read may not fail at runtime.
     */
    override val callMayFailPerSe: Boolean = false

    /** Whether this operation can see the given property, typically with respect to the property's visibility. */
    abstract fun canSee(property: StaticPropertyShape): Boolean

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args
        if (args.size != 2) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(2))
        }

        val typePos = args.pos(0)
        val type = when (val typeArg = args.evaluate(0, interpMode = interpMode)) {
            is Value<*> -> asReifiedTypeOr(typeArg, macroEnv, typePos) { fail -> return@invoke fail }.type
            is NotYet, is Fail -> return typeArg
        }
        val typeShape = (type as? NominalType)?.definition as? TypeShape
            ?: return macroEnv.fail(
                template = MessageTemplate.ExpectedNominalType,
                pos = typePos,
                values = listOf(type),
            )

        val memberSymbol = args.valueTree(1).symbolContained
            ?: return macroEnv.fail(
                template = MessageTemplate.MissingName,
                pos = args.pos(1),
            )

        val member = typeShape.staticProperties.firstOrNull {
            it.symbol == memberSymbol && canSee(it)
        } ?: return macroEnv.fail(
            MessageTemplate.NoAccessibleMember,
            macroEnv.pos,
            listOf(memberSymbol.text, type),
        )

        // Post define stage, type members, including static properties,
        // are guaranteed to be top-level names.
        val memberName = member.name
        val definingContext = (memberName as? ModularName)?.origin as? BindingNamingContext
        val binding = definingContext?.getTopLevelBinding(memberName)
        if (binding != null && binding.value == null) {
            val connectedKey = member.metadata[connectedSymbol]
                ?.lastOrNull()?.let { TString.unpackOrNull(it) }
            if (connectedKey != null) {
                val connectedFnValue = macroEnv.connection(connectedKey)?.let {
                    it(
                        Signature2(
                            returnType2 = member.descriptor?.let { descriptor ->
                                when (descriptor) {
                                    is Type2 -> descriptor
                                    is Signature2 -> hackMapOldStyleToNew(
                                        typeFromSignature(descriptor),
                                    )
                                }
                            } ?: WellKnownTypes.anyValueOrNullType2,
                            requiredInputTypes = emptyList(),
                            hasThisFormal = false,
                        ),
                    )
                }
                val initializerFn = TFunction.unpackOrNull(connectedFnValue) as? CallableValue
                if (initializerFn != null) {
                    return initializerFn.invoke(ActualValues.Empty, macroEnv, interpMode)
                }
            }
        }

        return binding?.value ?: NotYet
    }

    companion object {
        fun externalStaticGot(type: Type2, symbol: Symbol): Value<*>? =
            if (
                type is DefinedNonNullType &&
                type.definition == WellKnownTypes.listTypeDefinition &&
                symbol.text == "of"
            ) {
                BuiltinFuns.vListifyFn
            } else {
                null
            }
    }
}

internal object GetStatic : GetStaticOp() {
    override val name: String = getStaticBuiltinName.builtinKey
    override fun canSee(property: StaticPropertyShape) = property.visibility == Visibility.Public
}

internal object InternalGetStatic : GetStaticOp() {
    override val name: String = internalGetStaticBuiltinName.builtinKey
    override fun canSee(property: StaticPropertyShape) = true
}
