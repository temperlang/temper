package lang.temper.value

import lang.temper.lexer.Operator
import lang.temper.name.BuiltinName
import lang.temper.name.TemperName
import lang.temper.type.DotHelper
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

// Helpers for yielding functions that don't let MaximalPaths understand them
// without depending on subprojects like builtins where those functions are
// defined.

/**
 * The `await` and `yield` builtins are special to control flow since they
 * yield control back to the caller temporarily.
 *
 * This allows computing maximal paths that end at yielding calls without
 * this package needing to reference the builtin function implementations.
 *
 * @see lang.temper.lexer.Operator.Yield
 */
@Suppress("EnumEntryName", "EnumNaming") // Less confusion if member names match function names
enum class YieldingFnKind(
    val builtinName: BuiltinName,
) {
    await(BuiltinName(Operator.Await.text!!)),

    yield(BuiltinName(Operator.Yield.text!!)),
}

/**
 * Yielding calls, like `await`, can fail (when the promise breaks)
 * and are used for their result (when the promise completes)
 */
data class YieldingCallDisassembled(
    val kind: YieldingFnKind,
    val assignedTo: TemperName?,
    val failVar: TemperName?,
    val yieldingCall: CallTree,
    val outerCall: CallTree,
)

/** Looks through intermediate calls under a top-level statement to get at any yielding call */
fun disassembleYieldingCall(stmt: ControlFlow.Stmt, block: BlockTree): YieldingCallDisassembled? =
    disassembleYieldingCall(block.dereference(stmt.ref)?.target as? CallTree)

fun disassembleYieldingCall(outerTree: Tree?): YieldingCallDisassembled? {
    if (outerTree !is CallTree) { return null }
    var tree: Tree? = outerTree // Look through hs/assignment
    var failVar: TemperName? = null
    var assignedTo: TemperName? = null
    while (tree != null) {
        if (isAssignment(tree)) {
            assignedTo = (tree.child(1) as? LeftNameLeaf)?.content
        } else if (isHandlerScopeCall(tree)) {
            failVar = (tree.child(1) as? LeftNameLeaf)?.content
        } else {
            break
        }
        tree = tree.childOrNull(2)
    }
    val kind = tree.yieldingCallKind()
    return kind?.let {
        YieldingCallDisassembled(
            kind = kind,
            assignedTo = assignedTo,
            failVar = failVar,
            yieldingCall = tree as CallTree,
            outerCall = outerTree,
        )
    }
}

@OptIn(ExperimentalContracts::class)
fun isCallOfFunction(tree: Tree, function: MacroValue): Boolean {
    contract {
        returns(true) implies (tree is CallTree)
    }
    if (tree !is CallTree || tree.size < 1) { return false }
    return tree.child(0).functionContained === function
}

@OptIn(ExperimentalContracts::class)
fun isBubbleCall(tree: Tree): Boolean {
    contract {
        returns(true) implies (tree is CallTree)
    }
    return isCallOfFunction(tree, BubbleFn)
}

@OptIn(ExperimentalContracts::class)
fun isPanicCall(tree: Tree): Boolean {
    contract {
        returns(true) implies (tree is CallTree)
    }
    return isCallOfFunction(tree, PanicFn)
}

fun MaximalPath.Element?.yieldingCallKind(block: BlockTree) = this?.ref?.yieldingCallKind(block)
fun BlockChildReference?.yieldingCallKind(block: BlockTree): YieldingFnKind? {
    if (this == null) { return null }
    return block.dereference(this)?.target?.yieldingCallKind()
}

@OptIn(ExperimentalContracts::class)
fun Tree?.calleeBuiltinName(): String? {
    contract {
        returnsNotNull() implies (this@calleeBuiltinName is CallTree)
    }
    if (this !is CallTree) {
        return null
    }
    return when (val callee = this.childOrNull(0)) {
        null -> null
        is RightNameLeaf -> (callee.content as? BuiltinName)?.builtinKey
        else -> (callee.functionContained as? NamedBuiltinFun)?.name
    }
}
@OptIn(ExperimentalContracts::class)
fun Tree?.yieldingCallKind(): YieldingFnKind? {
    contract {
        returnsNotNull() implies (this@yieldingCallKind is CallTree)
    }
    return when (calleeBuiltinName()) {
        "await" -> YieldingFnKind.await
        "yield" -> YieldingFnKind.yield
        else -> null
    }
}

@OptIn(ExperimentalContracts::class)
fun isAwaitCall(t: Tree): Boolean {
    contract {
        returns(true) implies (t is CallTree)
    }
    return t.yieldingCallKind() == YieldingFnKind.await
}
@OptIn(ExperimentalContracts::class)
fun isYieldCall(t: Tree): Boolean {
    contract {
        returns(true) implies (t is CallTree)
    }
    return t.yieldingCallKind() == YieldingFnKind.yield
}

private const val HS_ARITY = 3 // Callee, fail var, operation
private const val ASSIGN_ARITY = 3 // Callee, left, right

const val HANDLER_SCOPE_FN_NAME = "hs"

@OptIn(ExperimentalContracts::class)
fun isHandlerScopeCall(t: Tree): Boolean {
    contract {
        returns(true) implies (t is CallTree)
    }
    return t.size == HS_ARITY && t.calleeBuiltinName() == HANDLER_SCOPE_FN_NAME
}

@OptIn(ExperimentalContracts::class)
fun isAssignment(t: Tree): Boolean {
    contract {
        returns(true) implies (t is CallTree)
    }
    return t.size == ASSIGN_ARITY && t.calleeBuiltinName() == "="
}
