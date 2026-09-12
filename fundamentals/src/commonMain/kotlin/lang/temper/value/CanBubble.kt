package lang.temper.value

import lang.temper.type.AndType
import lang.temper.type.FunctionType
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes
import lang.temper.type.isBubbly
import lang.temper.type2.Signature2

/**
 * True when the tree may bubble.
 *
 * This differs from [calleeReturnsResult] in that this helper is more
 * suitable for statement level constructs while [calleeReturnsResult]
 * is more suitable for identifying subtrees that need to be migrated
 * into statement position.
 *
 * Until type information is available in the AST, it's unclear whether
 * a call can bubble, and this returns false.
 *
 * "Can bubble" is defined thus:
 *
 * - An assignment can bubble when its right-hand side can bubble.
 * - A call can bubble if its [callee can bubble][calleeReturnsResult].
 *
 * For example, the first group below can bubble:
 *
 * - `x = bubble<Int32>()`
 * - `bubble()`
 * - `x / y` because division with an unknown denominator is bubbly
 *
 * This second group cannot:
 *
 * - `x = y`
 * - `console.log(str)`
 * - `x + y`
 */
fun treeCanBubble(tree: Tree): Boolean {
    var t = tree
    while (isAssignment(t)) {
        t = t.child(2)
    }
    return t is CallTree && calleeReturnsResult(t)
}

/** True if `tree` may fail without one of its sub-expressions failing. */
fun calleeReturnsResult(tree: CallTree): Boolean {
    var callee = tree.childOrNull(0) ?: return false // Error nodes panic
    if (callee is CallTree && isTypeAngleCall(callee)) {
        callee = callee.child(1)
    }
    if (callee is ValueLeaf) {
        val sigs = TFunction.unpackOrNull(callee.content)?.sigs
        val hasBubblySig = sigs?.any {
            it is Signature2 && it.returnType2.definition == WellKnownTypes.resultTypeDefinition
        }
        if (hasBubblySig == true) {
            return true
        }
    }
    val calleeType = callee.typeInferences?.type
    return calleeType != null && canBubble(calleeType)
}

private fun canBubble(calleeType: StaticType): Boolean = when (calleeType) {
    is AndType -> calleeType.members.any { canBubble(it) }
    is FunctionType -> calleeType.returnType.isBubbly
    else -> false
}
