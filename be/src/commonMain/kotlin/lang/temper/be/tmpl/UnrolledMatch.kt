package lang.temper.be.tmpl

import lang.temper.builtin.BuiltinFuns
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.CallTree
import lang.temper.value.RightNameLeaf
import lang.temper.value.TInt
import lang.temper.value.ValueLeaf
import lang.temper.value.functionContained

internal data class UnrolledMatch(
    val name: RightNameLeaf,
    val cases: List<Triple<PreTranslated.If, ValueLeaf, Int>>,
    val finalElse: PreTranslated,
)

internal fun maybeUnrollToMatch(
    ifStmt: PreTranslated.If,
): UnrolledMatch? {
    val (name, constant0) = decomposeIntComparison(ifStmt.test)
        ?: return null
    var stmt = ifStmt.alternate
    val ls = buildList {
        add(Triple(ifStmt, constant0, TInt.unpack(constant0.content)))

        while (true) {
            var next = stmt
            if (next is PreTranslated.Block && next.unfixedElements.size == 1) {
                next = next.unfixedElements[0]
            }
            if (next !is PreTranslated.If) { break }
            val (nextName, constant) = decomposeIntComparison(next.test)
                ?: break
            if (nextName.content != name.content) {
                break
            }
            add(Triple(next, constant, TInt.unpack(constant.content)))
            stmt = next.alternate
        }
    }
    if (ls.size < 2) { return null }
    return UnrolledMatch(name, ls, stmt)
}

private fun decomposeIntComparison(expr: PreTranslated): Pair<RightNameLeaf, ValueLeaf>? {
    val tree = (expr as? PreTranslated.TreeWrapper)?.tree as? CallTree
    if (
        tree?.size == BINARY_OP_CALL_ARG_COUNT &&
        tree.child(0).functionContained == BuiltinFuns.eqIntFn
    ) {
        val (_, a, b) = tree.children
        if (a is RightNameLeaf) {
            if (b is ValueLeaf && b.content.typeTag == TInt) {
                return a to b
            }
        } else if (b is RightNameLeaf) {
            if (a is ValueLeaf && a.content.typeTag == TInt) {
                return b to a
            }
        }
    }
    return null
}
