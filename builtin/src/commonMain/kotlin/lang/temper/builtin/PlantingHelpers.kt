package lang.temper.builtin

import lang.temper.log.Position
import lang.temper.name.ResolvedName
import lang.temper.type.StaticType
import lang.temper.type2.Signature2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.Planting
import lang.temper.value.TreeTemplate
import lang.temper.value.UnpositionedTreeTemplate

@Suppress("FunctionName")
fun Planting.Assign(
    name: ResolvedName,
    type: StaticType?,
    assigned: Planting.() -> UnpositionedTreeTemplate<*>,
): UnpositionedTreeTemplate<CallTree> {
    val callType = typeInferencesForAssign(type)
    return Call(type = callType) {
        V(BuiltinFuns.vSetLocalFn, type = callType?.variant)
        Ln(name = name, type = type)
        assigned()
    }
}

@Suppress("FunctionName")
fun Planting.Assign(
    pos: Position,
    name: ResolvedName,
    type: StaticType?,
    assigned: Planting.() -> UnpositionedTreeTemplate<*>,
): TreeTemplate<CallTree> {
    val callType = typeInferencesForAssign(type)
    return Call(pos, type = callType) {
        V(BuiltinFuns.vSetLocalFn, type = callType?.variant)
        Ln(pos.leftEdge, name = name, type = type)
        assigned()
    }
}

private fun typeInferencesForAssign(type: StaticType?): CallTypeInferences? {
    if (type == null) { return null }
    val type2 = hackMapOldStyleToNew(type)
    val sig = Signature2(type2, false, listOf(type2, type2))
    return CallTypeInferences(type, sig, mapOf(), listOf())
}
