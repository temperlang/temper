package lang.temper.interp.docgenalts

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.SpecialFunction
import lang.temper.value.freeTree

object AltWhileFn : NamedBuiltinFun, SpecialFunction, DocGenAltWhileFn {
    override val name: String = "whileForDocGen"
    override val sigs: List<Signature2> = listOf(
        Signature2(
            returnType2 = WellKnownTypes.voidType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(WellKnownTypes.booleanType2, WellKnownTypes.anyValueOrNullType2),
        ),
    )

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        if (macroEnv.stage == Stage.Define) {
            unlessInStatementPosition(macroEnv) { return@invoke it }

            val args = macroEnv.args
            // Strip function boundaries and symbols
            val trees = args.rawTreeList

            val conditionEdge = trees.getOrNull(0)?.incoming
            val bodyEdge = trees.getOrNull(1)?.incoming
            val bodyFn = bodyEdge?.target as? FunTree
            val bodyParts = bodyFn?.parts

            if (!(trees.size == 2 && conditionEdge != null && bodyParts != null)) {
                val problem = LogEntry(
                    Log.Error,
                    MessageTemplate.MalformedSpecial,
                    macroEnv.pos,
                    listOf("while"),
                )
                macroEnv.replaceMacroCallWithErrorNode(problem)
                return Fail(problem)
            }

            // Strip function wrappers from the body edge
            val bodyEdgeIndex = bodyEdge.edgeIndex
            bodyEdge.source!!.replace(bodyEdgeIndex..bodyEdgeIndex) {
                val body = bodyParts.body
                Replant(freeTree(body))
            }
        }
        return NotYet
    }
}
