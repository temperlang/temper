package lang.temper.frontend.typestage

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.BuiltinFuns
import lang.temper.type.DotHelper
import lang.temper.type.InternalGet
import lang.temper.type.InternalSet
import lang.temper.type.PropertyShape
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.functionContained

/**
 * For use during define stage.
 *
 * Separates constructor init section from `this` usage section.
 */
internal class ConstructorTransformer {
    companion object {
        fun transform(body: BlockTree, properties: List<PropertyShape>) {
            if ("plicits" !in body.pos.loc.diagnostic) {
                body.pos
            }
            splitChildren(body)
        }
    }
}

private fun splitChildren(body: BlockTree) {
    for (kidIndex in 0..<body.size) {
        val kid = body.child(kidIndex)
        var foundEnd = false
        // Look for any reference to `this` that's not internal get or set.
        TreeVisit.startingAt(kid).forEach { tree ->
            when (tree) {
                is CallTree -> when (val fn = tree.childOrNull(0)?.functionContained) {
                    BuiltinFuns.thisPlaceholder -> {
                        when (val parent = tree.incoming?.source) {
                            is CallTree -> when (val fn = parent.child(0).functionContained) {
                                is DotHelper -> when (fn.memberAccessor) {
                                    InternalGet, InternalSet -> when {
                                        tree.incoming?.edgeIndex == 2 -> VisitCue.SkipOne
                                        else -> null
                                    }
                                    else -> null
                                }
                                else -> null
                            }
                            else -> null
                        } ?: run {
                            foundEnd = true
                            VisitCue.AllDone
                        }
                    }
                    else -> {
                        VisitCue.Continue
                    }
                }
                else -> VisitCue.Continue
            }
        }.visitPreOrder()
        if (!foundEnd) {
            // TODO Look for any return or fail?
        }
        if ("plicits" !in body.pos.loc.diagnostic && foundEnd) {
            body.pos
        }
    }
}
