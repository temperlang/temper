package lang.temper.be.tmpl

import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleActualsToNew
import lang.temper.type2.hackTryStaticTypeToSig
import lang.temper.type2.mapType
import lang.temper.value.CallTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.Tree

internal data class BubblyCall(
    override val pos: Position,
    val assigned: LeftNameLeaf?,
    val bubbles: CallTree,
    val resultType: Type2,
    val passType: Type2,
    val failType: Type2,
) : Positioned

internal fun unpackBubblyCall(t: Tree?): BubblyCall? {
    var call = t as? CallTree ?: return null

    var assigned: LeftNameLeaf? = null
    if (isAssignmentCall(call)) {
        assigned = call.childOrNull(1) as? LeftNameLeaf ?: return null
        call = call.childOrNull(2) as? CallTree ?: return null
    }

    val typeInferences = call.typeInferences ?: return null
    val variant = hackTryStaticTypeToSig(typeInferences.variant)
    if (variant?.returnType2?.definition != WellKnownTypes.resultTypeDefinition) {
        // not bubbly
        return null
    }

    val bubbles = call
    val resultType = variant.returnType2.mapType(hackMapOldStyleActualsToNew(typeInferences.bindings2))
    val (passType, failType) = resultType.bindings

    return BubblyCall(
        pos = t.pos,
        assigned = assigned,
        bubbles = bubbles,
        resultType = resultType,
        passType = passType,
        failType = failType,
    )
}
