package lang.temper.builtin

import lang.temper.value.CallTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.Tree
import lang.temper.value.functionContained

private const val HS_ARITY = 3 // Callee, fail var, operation

fun isHandlerScopeCall(tree: Tree): Boolean {
    if (tree !is CallTree || tree.size != HS_ARITY) {
        return false
    }
    val callee = tree.child(0)
    return callee.functionContained == BuiltinFuns.handlerScope && tree.child(1) is LeftNameLeaf
}

fun isSetPropertyCall(tree: Tree): Boolean {
    if (tree !is CallTree || tree.size != SETP_ARITY + 1) {
        return false
    }
    val callee = tree.child(0)
    return callee.functionContained == BuiltinFuns.setpFn && tree.child(1) is LeftNameLeaf
}

fun isNotNullCall(t: CallTree) =
    t.size >= 2 && t.child(0).functionContained == BuiltinFuns.notNullFn

fun isTypeAngleCall(t: CallTree) =
    t.size >= 2 && t.child(0).functionContained == BuiltinFuns.angleFn
