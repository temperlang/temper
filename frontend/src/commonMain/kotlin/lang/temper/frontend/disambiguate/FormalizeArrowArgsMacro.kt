package lang.temper.frontend.disambiguate

import lang.temper.builtin.isComplexArg
import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type2.Signature2
import lang.temper.value.BlockTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.StaylessMacroValue
import lang.temper.value.fnBuiltinName
import lang.temper.value.freeTree
import lang.temper.value.symbolContained
import lang.temper.value.valueContained

/**
 * <!-- snippet: builtin/=> -->
 * # `=>` definition
 *
 * TODO: Do we need this shorthand for function value/type construction?
 * TODO: Does it need more testing?  Issue #1549
 */
internal object FormalizeArrowArgsMacro : StaylessMacroValue {
    override val sigs: List<Signature2>? get() = null

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        if (macroEnv.stage < Stage.DisAmbiguate) { return NotYet }
        if (macroEnv.stage > Stage.DisAmbiguate) {
            macroEnv.replaceMacroCallWithErrorNode(
                LogEntry(
                    level = Log.Error,
                    template = MessageTemplate.MalformedDeclaration, // TODO: or function value?
                    pos = macroEnv.pos,
                    values = emptyList(),
                ),
            )
        }

        val args = macroEnv.args
        if (args.size != 2 || args.key(0) != null || args.key(1) != null) { // Args, Body
            return macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(2))
        }
        val argTree = args.valueTree(0)
        val bodyTree = args.valueTree(1)
        if (isComplexArg(argTree)) {
            formalizeArg(argTree.incoming!!)
        } else if (argTree is BlockTree) {
            var i = 0
            val n = argTree.size
            while (i < n) {
                val child = argTree.child(i)
                val value = child.valueContained
                if (value != null) {
                    if (child.symbolContained != null) {
                        i += 1
                    }
                } else {
                    val edge = child.incoming
                    if (edge != null) {
                        formalizeArg(edge)
                    }
                }
                i += 1
            }
        }
        // Now convert (Call `=>` (Block Args) Body) to (fn Args Body)
        macroEnv.call?.let { call ->
            macroEnv.replaceMacroCallWith {
                Call(call.pos) {
                    Rn(macroEnv.callee.pos, fnBuiltinName)
                    when (argTree) {
                        // The parser produces a wrapping block when the number of arguments is != 1
                        // or there is a return type, but not otherwise.
                        is BlockTree ->
                            argTree.children.forEach { argChild ->
                                Replant(freeTree(argChild))
                            }
                        else -> {
                            Replant(freeTree(argTree))
                        }
                    }
                    Replant(freeTree(bodyTree))
                }
            }
        }
        return NotYet
    }
}
