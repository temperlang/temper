package lang.temper.builtin

import lang.temper.log.Position
import lang.temper.name.ResolvedName
import lang.temper.name.Temporary
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes
import lang.temper.type.excludeNull
import lang.temper.type2.Signature2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BlockPlanting
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.IsNullFn
import lang.temper.value.LeafTree
import lang.temper.value.NotFn
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.Tree
import lang.temper.value.TreeTemplate
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTree

@Suppress("FunctionName") // By convention, names of fns that plant things are capitalized.
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

@Suppress("FunctionName")
fun Planting.IsNullCall(
    pos: Position,
    calleePos: Position = pos.leftEdge,
    argType: StaticType? = null,
    plantArgument: Planting.() -> UnpositionedTreeTemplate<*>,
): TreeTemplate<CallTree> {
    val sig = IsNullFn.sig
    val argTypeNn = argType?.let { excludeNull(it) }
    val callType = argTypeNn?.let {
        CallTypeInferences(WellKnownTypes.booleanType, sig, mapOf(sig.typeFormals[0] to it), listOf())
    }
    return Call(pos, callType) {
        V(calleePos, Value(IsNullFn), callType?.variant)
        plantArgument()
    }
}

@Suppress("FunctionName")
fun Planting.Not(
    pos: Position,
    plantArg: Planting.() -> UnpositionedTreeTemplate<*>,
): TreeTemplate<CallTree> =
    Call(pos, notCallType) {
        V(pos.leftEdge, BuiltinFuns.vNotFn, notCallType.variant)
        plantArg()
    }

@Suppress("FunctionName")
fun Planting.MaybeNot(
    pos: Position,
    negated: Boolean,
    plantArg: Planting.() -> UnpositionedTreeTemplate<*>,
): UnpositionedTreeTemplate<*> =
    if (negated) {
        Not(pos, plantArg)
    } else {
        plantArg()
    }
private val notCallType = CallTypeInferences(WellKnownTypes.booleanType, NotFn.sig, mapOf(), listOf())

private fun typeInferencesForAssign(type: StaticType?): CallTypeInferences? {
    if (type == null) { return null }
    val type2 = hackMapOldStyleToNew(type)
    val sig = Signature2(type2, false, listOf(type2, type2))
    return CallTypeInferences(type, sig, mapOf(), listOf())
}

fun BlockPlanting.maybeCaptureInBlock(
    tree: Tree,
): LeafTree = when (tree) {
    is ValueLeaf, is RightNameLeaf -> tree
    else -> {
        val name = tree.document.nameMaker.unusedTemporaryName(Temporary.defaultNameHint)
        val type = tree.typeInferences?.type
        val left = tree.pos.leftEdge
        Decl(left) {
            Ln(left, name, type)
        }
        Assign(left, name, type) {
            Replant(freeTree(tree))
        }
        RightNameLeaf(
            tree.document,
            tree.pos,
            name,
        ).also {
            if (type != null) {
                it.typeInferences = BasicTypeInferences(type, listOf())
            }
        }
    }
}
