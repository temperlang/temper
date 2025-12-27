package lang.temper.interp.docgenalts

import lang.temper.common.Log
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.PRESERVE_FN_CALL_SIZE
import lang.temper.value.PreserveFn
import lang.temper.value.Tree
import lang.temper.value.functionContained

/**
 * Calls [orElse] if the macro call does not appear in statement position.
 * This helps us check that constructs that correspond to statements in many
 * languages, are easily translatable to similar constructs when we
 * translate documentation.
 */
inline fun unlessInStatementPosition(macroEnv: MacroEnvironment, orElse: (Fail) -> Nothing) {
    var edge = macroEnv.call?.incoming
    while (edge != null) {
        val source = edge.source ?: break
        if (!isPreserveCall(source)) { break }
        edge = source.incoming
    }
    if (edge?.source !is BlockTree) {
        val problem = LogEntry(
            Log.Error,
            MessageTemplate.NotInStatementPosition,
            macroEnv.pos,
            emptyList(),
        )
        if (macroEnv.call != null) {
            macroEnv.replaceMacroCallWithErrorNode(problem)
        }
        orElse(Fail(problem))
    }
}

fun isPreserveCall(tree: Tree?): Boolean = when {
    tree !is CallTree -> false
    tree.size != PRESERVE_FN_CALL_SIZE -> false
    tree.child(0).functionContained != PreserveFn -> false
    else -> true
}
