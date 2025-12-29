package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.type2.Signature2
import lang.temper.value.Fail
import lang.temper.value.LeafTreeType
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.RightNameLeaf
import lang.temper.value.SpecialFunction
import lang.temper.value.TClass
import lang.temper.value.Value

/** Gets the value of an object's backed property. */
internal object Getp : SpecialFunction, NamedBuiltinFun {
    override val name: String get() = "getp"

    override val sigs: List<Signature2>? get() = null

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        if (args.size != 2) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(2))
        }
        if (interpMode != InterpMode.Full) {
            // TODO: Reconsider once we've got partial records implemented.
            return Fail
        }
        val child0 = args.valueTree(0)
        val name = child0 as? RightNameLeaf
            ?: run {
                return@invoke macroEnv.fail(
                    MessageTemplate.ExpectedValueOfType,
                    pos = child0.pos,
                    values = listOf(LeafTreeType.RightName, child0.treeType),
                )
            }
        // The `this` value comes after the name for symmetry with setp which needs the name to be
        // in position 1 in a CallTree so its assigns-argument-1 flag makes sense.
        return when (val thisValue = args.evaluate(1, interpMode)) {
            is Fail, NotYet -> thisValue
            is Value<*> -> {
                val typeTag = thisValue.typeTag
                if (typeTag !is TClass) {
                    return macroEnv.fail(
                        MessageTemplate.ExpectedValueOfType,
                        pos = args.pos(1),
                        values = listOf(LeafTreeType.RightName, child0.treeType),
                    )
                }
                val propertyRecord = typeTag.unpack(thisValue)
                propertyRecord.properties[name.content] ?: Fail
            }
        }
    }

    override val callMayFailPerSe: Boolean get() = false
}
