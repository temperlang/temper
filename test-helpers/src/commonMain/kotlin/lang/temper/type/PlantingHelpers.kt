package lang.temper.type

import lang.temper.common.firstOrNullAs
import lang.temper.type2.AnySignature
import lang.temper.type2.Signature2
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.MacroValue
import lang.temper.value.Planting
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.typeFromSignature

// Helpers for growing ASTs for tests with type info

fun Planting.plantCallWithTypeInfo(
    callee: MacroValue,
    typeActuals: List<StaticType> = listOf(),
    plantArgs: Planting.() -> Any?,
): UnpositionedTreeTemplate<CallTree> {
    val nTypeActuals = typeActuals.size
    val sig: Signature2 = callee.sigs!!.first { it.typeFormals.size == nTypeActuals } as Signature2
    val type = excludeBubble(sig.returnType.type)
    val variant = typeFromSignature(sig)
    val ti = CallTypeInferences(
        type = type,
        variant = variant,
        bindings2 = buildMap {
            for ((f, a) in (sig.typeFormals zip typeActuals)) {
                this[f] = a
            }
        },
        explanations = listOf(),
    )
    return Call(type = ti) {
        plantTypedCallee(callee, sig)
        plantArgs()
    }
}

fun Planting.plantCallWithTypeInfo(
    callee: Value<MacroValue>,
    typeActuals: List<StaticType> = listOf(),
    plantArgs: Planting.() -> Any?,
): UnpositionedTreeTemplate<CallTree> =
    plantCallWithTypeInfo(callee.stateVector, typeActuals = typeActuals, plantArgs = plantArgs)

fun Planting.plantTypedCallee(
    callee: MacroValue,
    sig: Signature2? = null,
): UnpositionedTreeTemplate<ValueLeaf> {
    (sig ?: callee.sigs?.firstOrNullAs<AnySignature, Signature2> { true })?.let { sig ->
        return@plantTypedCallee V(Value(callee), type = typeFromSignature(sig))
    }
    error("No signatures: $callee")
}
