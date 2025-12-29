package lang.temper.frontend.typestage

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.common.Log
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.value.BlockTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.void

internal fun typeRedundantMacro(macroEnv: MacroEnvironment): PartialResult {
    val args = macroEnv.args
    // If all the members were successfully pulled out by ClosureConvertClasses then replace with
    // a no-op.
    // Otherwise, replace with an error node.
    val bodyFn = if (args.lastIndex >= 0) { args.valueTree(args.lastIndex) } else { null }
    if (bodyFn is FunTree) {
        val parts = bodyFn.parts
        if (parts != null) {
            if (isIgnorable(parts.body)) {
                if (macroEnv.call != null) {
                    macroEnv.replaceMacroCallWith { V(macroEnv.pos, void) }
                }
                return NotYet
            }
        }
    }

    val problem = LogEntry(Log.Error, MessageTemplate.MalformedTypeDeclaration, macroEnv.pos, listOf())
    problem.logTo(macroEnv.logSink)
    if (macroEnv.call != null) {
        macroEnv.replaceMacroCallWithErrorNode(problem)
    }
    return Fail(problem)
}

private fun isIgnorable(tree: Tree): Boolean {
    var ignorable = true // Look for counter-evidence
    TreeVisit.startingAt(tree)
        .forEach {
            when (it) {
                is BlockTree, is ValueLeaf -> VisitCue.Continue
                else -> {
                    ignorable = false
                    VisitCue.AllDone
                }
            }
        }
        .visitPreOrder()
    return ignorable
}
