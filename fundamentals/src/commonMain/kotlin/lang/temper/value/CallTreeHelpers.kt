package lang.temper.value

import lang.temper.type.DotHelper

fun isNewCall(t: CallTree): Boolean {
    if (t.size >= 2) { // `new` and constructor reference required
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
