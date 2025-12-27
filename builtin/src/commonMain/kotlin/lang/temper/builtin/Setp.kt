package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.name.ModularName
import lang.temper.type.Abstractness
import lang.temper.type2.Signature2
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.LeafTreeType
import lang.temper.value.LeftNameLeaf
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.SpecialFunction
import lang.temper.value.TClass
import lang.temper.value.Value
import lang.temper.value.classBuiltinName
import lang.temper.value.reifiedTypeContained
import lang.temper.value.typeSymbol
import lang.temper.value.varSymbol
import lang.temper.value.void

/** Sets the value of an object's backed property. */
internal object Setp : SpecialFunction, NamedBuiltinFun {
    override val name: String get() = "setp"

    override val sigs: List<Signature2>? get() = null

    override val assignsArgumentOne: Boolean
        get() = true

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        if (args.size != SETP_ARITY) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(SETP_ARITY))
        }
        if (interpMode != InterpMode.Full) {
            // TODO: if this is a set in a constructor of a const property, and we're dealing with a
            // partial record, then go ahead.
            return Fail
        }
        // The `this` value comes after the name because we need the name to be in position 1 in
        // setp CallTrees so the assigns-argument-1 flag makes sense.
        val child0 = args.valueTree(0)
        val name = child0 as? LeftNameLeaf
            ?: run {
                return@invoke macroEnv.fail(
                    MessageTemplate.ExpectedValueOfType,
                    pos = child0.pos,
                    values = listOf(LeafTreeType.LeftName, child0.treeType),
                )
            }
        val thisValue = args.evaluate(1, interpMode)
        val propertyRecord = when (thisValue) {
            is Fail, NotYet -> return thisValue
            is Value<*> -> {
                val typeTag = thisValue.typeTag
                if (typeTag !is TClass) {
                    return macroEnv.fail(
                        template = MessageTemplate.ExpectedValueOfType,
                        pos = args.pos(1),
                        values = listOf(LeafTreeType.RightName, child0.treeType),
                    )
                }
                typeTag.unpack(thisValue)
            }
        }

        val concreteType = thisValue.typeTag as? TClass
            ?: withError(
                macroEnv = macroEnv,
                template = MessageTemplate.ExpectedValueOfType,
                values = listOf(classBuiltinName, thisValue.typeTag),
            ) {
                return@invoke it
            }

        return when (val newValue = args.evaluate(2, interpMode)) {
            is Fail, NotYet -> newValue
            is Value<*> -> {
                val propertyName = name.content
                val propertyShape =
                    concreteType.typeShape.properties.firstOrNull { it.name == propertyName }
                val propertyDeclParts = (propertyShape?.stay?.incoming?.source as? DeclTree)?.parts
                if (propertyName !is ModularName || propertyDeclParts == null) {
                    withError(
                        macroEnv = macroEnv,
                        template = MessageTemplate.MissingProperty,
                        values = listOf(propertyShape ?: propertyName, concreteType),
                    ) {
                        return@invoke it
                    }
                }
                if (propertyShape.abstractness != Abstractness.Concrete) {
                    withError(macroEnv, MessageTemplate.CannotSetAbstractProperty, listOf(propertyName)) {
                        return@invoke it
                    }
                }

                val properties = propertyRecord.properties
                val isVar = varSymbol in propertyDeclParts.metadataSymbolMap
                if (!isVar && propertyName in properties) {
                    withError(
                        macroEnv,
                        MessageTemplate.CannotResetConst,
                        listOf(propertyName),
                    ) {
                        return@invoke it
                    }
                }

                val reifiedType =
                    propertyDeclParts.metadataSymbolMap[typeSymbol]?.target?.reifiedTypeContained
                if (reifiedType != null && !reifiedType.accepts(newValue, macroEnv.args, 0)) {
                    return Fail(
                        LogEntry(
                            Log.Error,
                            MessageTemplate.TypeCheckRejected,
                            macroEnv.pos,
                            listOf(reifiedType, newValue),
                        ),
                    )
                }

                properties[propertyName] = newValue
                void
            }
        }
    }

    // Static checks require that the property have the right type and that setp for a const
    // property only occurs once, during initialization, and before `this` is used as an rvalue.
    // TODO: make this true.
    override val callMayFailPerSe: Boolean get() = false
}

/** Not including callee */
const val SETP_ARITY = 3 // leftName, this, newValue

private inline fun withError(
    macroEnv: MacroEnvironment,
    template: MessageTemplateI,
    values: List<Any>,
    action: (Fail) -> Nothing,
): Nothing {
    val problem = LogEntry(
        Log.Error,
        template,
        macroEnv.pos,
        values = values,
    )
    macroEnv.failLog.explain(problem)
    action(Fail(problem))
}
