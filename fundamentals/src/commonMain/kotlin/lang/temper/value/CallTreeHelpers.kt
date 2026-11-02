package lang.temper.value

import lang.temper.type.DotHelper
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun isNewCall(t: Tree): Boolean {
    contract {
        returns(true) implies (t is CallTree)
    }
    if (t is CallTree && t.size >= 2) { // `new` and constructor reference required
        val callee = t.child(0).functionContained
        return callee is NamedBuiltinFun && callee.name == newBuiltinName.builtinKey
    }
    return false
}

val CallTree.firstArgumentIndex get(): Int {
    val callee = childOrNull(0) ?: return 1
    val calleeFn = callee.functionContained
    return when {
        isNewCall(this) -> 2
        calleeFn is DotHelper -> calleeFn.memberAccessor.firstArgumentIndex + 1
        else -> 1
    }
}
