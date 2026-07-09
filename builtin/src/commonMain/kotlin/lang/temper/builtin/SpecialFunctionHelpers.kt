package lang.temper.builtin

import lang.temper.type.CallMemberAccessor
import lang.temper.type.DotHelper
import lang.temper.type.MemberAccessor
import lang.temper.value.CallTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.Tree
import lang.temper.value.functionContained
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun isSetPropertyCall(tree: Tree): Boolean {
    contract {
        returns(true) implies (tree is CallTree)
    }
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

@OptIn(ExperimentalContracts::class)
fun accessorForCall(t: Tree): MemberAccessor? {
    contract {
        returnsNotNull() implies (t is CallTree)
    }
    if (t !is CallTree) {
        return null
    }
    return (t.childOrNull(0)?.functionContained as? DotHelper)?.memberAccessor
}

@OptIn(ExperimentalContracts::class)
fun isDotHelperCall(t: Tree): Boolean {
    contract {
        returns(true) implies (t is CallTree)
    }
    return accessorForCall(t) != null
}

@OptIn(ExperimentalContracts::class)
fun isDotMethodCall(t: Tree): Boolean {
    contract {
        returns(true) implies (t is CallTree)
    }
    return accessorForCall(t) is CallMemberAccessor
}
