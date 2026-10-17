package lang.temper.frontend.generate

import lang.temper.value.Tree

@Suppress("FunctionOnlyReturningConstant", "UnusedParameter", "unused")
fun debugMeDoNotCommit(t: Tree): Boolean {
    return false
}

/* From end of typeCallTree
run {
    val c = lang.temper.common.console // do not commit
    var callee = tree.childOrNull(0)
    val oCalleeType = ti.decisionType(callee)
    if (callee != null && isTypeAngleCall(callee)) {
        callee = callee.childOrNull(1)
    }
    val calleeType = ti.decisionType(callee)
    val calleeType2 = ti.decisionType2(callee)
    if ((
            calleeType == functionType || calleeType == null || oCalleeType == null ||
                oCalleeType == functionType
            ) && false.doNotCommit
    ) {
        val decision = ti.decision(tree)
        val variant = decision?.variant2
        val bindings = decision?.bindings2
        val odd = calleeType2 == functionType2
        c.group(
            "${tree.document.context.formatPosition(tree.pos)}: `${tree.toPseudoCode()}`, isAssignment=${
                isAssignment(tree)}, odd=$odd, bindings=$bindings",
        ) {
            c.log(
                "ocalleeType=${oCalleeType}, calleeType=${
                    calleeType
                }, calleeType2=$calleeType2, variant=$variant",
            )
        }
    }
}
*/
