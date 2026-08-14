package lang.temper.frontend.generate

import lang.temper.common.Log
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.FunTree
import lang.temper.value.StructuredFlow
import lang.temper.value.Tree
import lang.temper.value.calleeReturnsResult

internal fun bubblesAreNotFree(t: Tree, logSink: LogSink) {
    if (t is FunTree) {
        // Don't go into nested functions, as that's a new scope for bubble allowance.
        return
    }
    if (t is BlockTree) {
        val flow = t.flow
        if (flow is StructuredFlow) {
            bubblesAreNotFree(t, flow.controlFlow, logSink)
            return
        }
    }

    if (t is CallTree && calleeReturnsResult(t)) {
        // In the end, we only reference Bubble when it actually escapes from functions.
        // And if some logic doesn't allow a branch to execute, we should clean it out before here.
        // Given the above, we can complain here about any call to bubble.
        logSink.log(Log.Error, MessageTemplate.ExpectedNoBubble, t.pos, listOf())
        return
    }

    for (c in t.children) {
        bubblesAreNotFree(c, logSink)
    }
}

private fun bubblesAreNotFree(t: BlockTree, cf: ControlFlow, logSink: LogSink) {
    if (cf is ControlFlow.OrElse) {
        // Don't bother checking the or-clause, since its bubbles go to the else-clause.
        bubblesAreNotFree(t, cf.elseClause, logSink)
        return
    }

    val ref = cf.ref
    if (ref != null) {
        t.dereference(ref)?.target?.let {
            bubblesAreNotFree(it, logSink)
        }
    }

    for (clause in cf.clauses) {
        bubblesAreNotFree(t, clause, logSink)
    }
}

// Bubbles are not free, but do they grow on trees?
