package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.name.BuiltinName
import lang.temper.type.WellKnownTypes
import lang.temper.type.canBeNull
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.IsNullFn
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.RightNameLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TNull
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.typeForFunctionValue
import lang.temper.value.vIsNullFn

/**
 * `t == null` -> `false` and `t != null` -> `true` when t's type is not-nullable.
 */
internal fun simplifyNullCheck(t: CallTree) {
    val (positivity, nonNullOperand, nullOperand) = decomposeNullCheck(t)!!
    val type = nonNullOperand.typeInferences?.type ?: return
    val canBeNull = canBeNull(type)
    val edge = t.incoming!!
    val couldEffect = nonNullOperand !is ValueLeaf && nonNullOperand !is NameLeaf

    if (canBeNull || couldEffect) { // -> isNull(...) or !isNull(...)
        if (nullOperand == null) {
            // Already isNull call
            return
        }
        // nonNullOperand == nullOperand -> isNull(nonNullOperand, nullOperand)
        t.edge(0).replace { pos ->
            V(pos, vIsNullFn, typeForFunctionValue(IsNullFn))
        }
        // -> isNull(nonNullOperand, /* nothing else */)
        val nullOperandIndex = nullOperand.incoming!!.edgeIndex
        t.removeChildren(nullOperandIndex..nullOperandIndex)

        // When we started with `e != null`, isNull(e) -> !isNull(e)
        if (!positivity) {
            val notFnType = typeForFunctionValue(BuiltinFuns.notFn)
            val notCallType = CallTypeInferences(
                WellKnownTypes.booleanType,
                notFnType,
                mapOf(),
                listOf(),
            )
            edge.replace { pos ->
                Call(pos, notCallType) {
                    V(pos.leftEdge, BuiltinFuns.vNotFn, notFnType)
                    Replant(freeTree(t))
                }
            }
        }
    } else { // -> true or false
        edge.replace {
            V(t.pos, TBoolean.value(!positivity), WellKnownTypes.booleanType)
        }
    }
}

internal fun simplifyNotNull(t: CallTree) {
    val edge = t.incoming!!
    val arg = t.child(1)
    val type = arg.typeInferences?.type ?: return
    val canBeNull = canBeNull(type)
    if (!canBeNull) {
        edge.replace(freeTree(arg))
    }
}

internal fun isNullCheck(t: CallTree): Boolean =
    decomposeNullCheck(t) != null

private fun decomposeNullCheck(t: CallTree): Triple<Boolean, Tree, ValueLeaf?>? {
    val fnTree = t.childOrNull(0) ?: return null
    val fnBuiltinKey = when {
        fnTree is RightNameLeaf -> (fnTree.content as? BuiltinName)?.builtinKey
        else -> when (val fn = fnTree.functionContained) {
            IsNullFn -> {
                if (t.size == 2) {
                    return Triple(true, t.child(1), null)
                }
                null
            }
            BuiltinFuns.equalsFn -> "=="
            BuiltinFuns.notEqualsFn -> "!="
            else -> (fn as? NamedBuiltinFun)?.name
        }
    }
    val positivity = when (fnBuiltinKey) {
        "==" -> true
        "!=" -> false
        else -> return null
    }
    if (t.size == BINARY_OP_CALL_ARG_COUNT) {
        val left = t.child(1)
        val right = t.child(2)
        if (right is ValueLeaf && right.content == TNull.value) {
            return Triple(positivity, left, right)
        }
        if (left is ValueLeaf && left.content == TNull.value) {
            return Triple(positivity, right, left)
        }
    }
    return null
}

internal enum class NullOpKind {
    IsNull,
    NotNull,
}
