package lang.temper.interp

import lang.temper.common.Log
import lang.temper.common.abbreviate
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.value.NameLeaf
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.toLispy

// Some helpers for macros and compiler stages.

/**
 * Checks that, after a stages that expands macros has partially interpreted a tree, that it was
 * all reached.
 */
fun checkInterpreterReachedAll(tree: Tree, minStage: Stage, logSink: LogSink) {
    if (isErrorNode(tree)) {
        return
    }
    for (i in tree.indices) {
        val e = tree.edge(i)
        val breadcrumb = e.breadcrumb
        val target = e.target
        if (breadcrumb == null || breadcrumb < minStage) {
            if (target !is ValueLeaf && target !is NameLeaf) {
                logSink.log(
                    level = Log.Fatal,
                    template = MessageTemplate.Unreached,
                    pos = target.pos,
                    values = listOf(AbbreviatedLispy(target)),
                )
            }
        } else {
            checkInterpreterReachedAll(target, minStage, logSink)
        }
    }
}

private class AbbreviatedLispy(val t: Tree) {
    override fun toString() = abbreviate(t.toLispy(), maxlen = 30)
}
