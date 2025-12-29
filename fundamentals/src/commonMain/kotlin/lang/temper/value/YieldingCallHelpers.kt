package lang.temper.value

import lang.temper.lexer.Operator
import lang.temper.name.BuiltinName
import lang.temper.name.TemperName

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

fun isCallOfFunction(tree: Tree, function: MacroValue): Boolean {
    if (tree !is CallTree || tree.size < 1) { return false }
    return tree.child(0).functionContained === function
}

fun isBubbleCall(tree: Tree) = isCallOfFunction(tree, BubbleFn)
fun isPanicCall(tree: Tree) = isCallOfFunction(tree, PanicFn)

fun MaximalPath.Element?.yieldingCallKind(block: BlockTree) = this?.ref?.yieldingCallKind(block)
fun BlockChildReference?.yieldingCallKind(block: BlockTree): YieldingFnKind? {
    if (this == null) { return null }
    return block.dereference(this)?.target?.yieldingCallKind()
}

fun Tree?.calleeBuiltinName(): String? {
    if (this !is CallTree) {
        return null
    }
    return when (val callee = this.childOrNull(0)) {
        null -> null
        is RightNameLeaf -> (callee.content as? BuiltinName)?.builtinKey
        else -> (callee.functionContained as? NamedBuiltinFun)?.name
    }
}
fun Tree?.yieldingCallKind(): YieldingFnKind? {
    return when (calleeBuiltinName()) {
        "await" -> YieldingFnKind.await
        "yield" -> YieldingFnKind.yield
        else -> null
    }
}

fun isAwaitCall(t: Tree): Boolean = t.yieldingCallKind() == YieldingFnKind.await
fun isYieldCall(t: Tree): Boolean = t.yieldingCallKind() == YieldingFnKind.yield

const val HANDLER_SCOPE_FN_NAME = "hs"
private fun isHandlerScopeCall(t: Tree): Boolean =
    t.calleeBuiltinName() == HANDLER_SCOPE_FN_NAME
private fun isAssignment(t: Tree): Boolean =
    t.calleeBuiltinName() == "="
