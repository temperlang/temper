package lang.temper.interp.docgenalts

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PRESERVE_FN_CALL_SIZE
import lang.temper.value.PartialResult
import lang.temper.value.PreserveFn
import lang.temper.value.SpecialFunction
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.elseIfSymbol
import lang.temper.value.elseSymbol
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.symbolContained

object AltIfFn : NamedBuiltinFun, SpecialFunction, DocGenAltIfFn {
    override val name: String = "ifForDocGen"
    override val sigs: List<Signature2> = listOf(
        Signature2(
            returnType2 = WellKnownTypes.voidType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(WellKnownTypes.booleanType2, WellKnownTypes.anyValueOrNullType2),
            restInputsType = WellKnownTypes.anyValueOrNullType2,
        ),
    )

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        if (macroEnv.stage == Stage.Define) {
            unlessInStatementPosition(macroEnv) { return@invoke it }

            val args = macroEnv.args
            // Strip function boundaries and symbols
            val trees = args.rawTreeList
            val n = trees.size

            // Check that the symbols make sense.
            // They should follow the following patterns.
            //     args      :=          condition FnTree rest;
            //     rest      := \else_if condition FnTree rest;
            //                | \else              FnTree
            //                | ();
            //     condition := Tree;

            val symbolEdges = mutableListOf<TEdge>()
            val bodyEdges = mutableListOf<TEdge>()
            val ok = run examineEdges@{
                if (trees.any { it.incoming?.source == null }) {
                    // Cannot edit tree
                    return@examineEdges false
                }
                if (n < 2) { return@examineEdges false }
                if (preservedOrTree(trees[1]) !is FunTree) { return@examineEdges false }
                bodyEdges.add(trees[1].incoming ?: return@examineEdges false)

                fun queueValidBody(t: Tree): Boolean {
                    val body = preservedOrTree(t)
                    if (body !is FunTree) { return false }
                    if (body.parts?.formals?.isEmpty() != true) { return false }
                    bodyEdges.add(t.incoming ?: return false)
                    return true
                }

                // Processed `args := condition FnTree`
                var i = 2
                @Suppress("MagicNumber") // 3 is just a count of arguments
                while (i + 3 <= n) { // Process triplets (\else_if condition body)
                    if (trees[i].symbolContained != elseIfSymbol) { return@examineEdges false }
                    symbolEdges.add(trees[i].incoming ?: return@examineEdges false)
                    if (!queueValidBody(trees[i + 2])) { return@examineEdges false }
                    i += 3
                }
                if (i + 2 == n) { // Process pair (\else body)
                    if (trees[i].symbolContained != elseSymbol) { return@examineEdges false }
                    symbolEdges.add(trees[i].incoming ?: return@examineEdges false)
                    if (!queueValidBody(trees[i + 1])) { return@examineEdges false }
                    i += 2
                }
                i == n // Processed all
            }

            if (!ok) {
                val problem = LogEntry(
                    Log.Error,
                    MessageTemplate.MalformedSpecial,
                    macroEnv.pos,
                    listOf("if"),
                )
                macroEnv.replaceMacroCallWithErrorNode(problem)
                return Fail(problem)
            }

            // Strip function wrappers from
            bodyEdges.forEach { edge ->
                val fn = preservedOrTree(edge.target) as FunTree
                val parts = fn.parts!! // Checked above
                val body = parts.body
                val edgeIndex = edge.edgeIndex
                edge.source!!.replace(edgeIndex..edgeIndex) {
                    Replant(freeTree(body))
                }
            }

            symbolEdges.forEach { edge ->
                val edgeIndex = edge.edgeIndex
                edge.source!!.removeChildren(edgeIndex..edgeIndex)
            }
        }
        return NotYet
    }
}

internal fun preservedOrTree(t: Tree): Tree =
    if (
        t is CallTree && t.size == PRESERVE_FN_CALL_SIZE &&
        t.child(0).functionContained == PreserveFn
    ) {
        t.child(1)
    } else {
        t
    }
