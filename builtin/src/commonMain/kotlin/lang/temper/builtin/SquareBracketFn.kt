package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.dotBuiltinName
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.squaresBuiltinName
import lang.temper.value.vGetSymbol
import lang.temper.value.vSetSymbol

/**
 * Desugars `x[key]` to `x.get(key)`
 *
 * <!-- snippet: builtin/[] -->
 * Square brackets are used to access an element of a list, map, or other container.
 *
 * Abbreviated syntax `container[key]` is shorthand for `container.get[key]`.
 *
 * ```temper
 * let ls = ["zero", "one"];
 * console.log("[0] -> ${ls[0]}, [1] -> ${ls[1]}");         //!outputs "[0] -> zero, [1] -> one"
 * console.log("[0] -> ${ls.get(0)}, [1] -> ${ls.get(1)}"); //!outputs "[0] -> zero, [1] -> one"
 * ```
 *
 * This syntax may also appear to the left of `=`, as in `container[key] = newValue`.
 * That's shorthand for `container.set(key, newValue)`.
 *
 * ```temper
 * let ls = ["zero", "one"].toListBuilder();
 * console.log("[0] -> ${ls[0]}, [1] -> ${ls[1]}");         //!outputs "[0] -> zero, [1] -> one"
 * ls[1] = "ONE";
 * console.log("[0] -> ${ls[0]}, [1] -> ${ls[1]}");         //!outputs "[0] -> zero, [1] -> ONE"
 * ls.set(0, "Zero");
 * console.log("[0] -> ${ls[0]}, [1] -> ${ls[1]}");         //!outputs "[0] -> Zero, [1] -> ONE"
 * ```
 */
internal object SquareBracketFn : BuiltinMacro(
    squaresBuiltinName.builtinKey, // The grammar uses this name for constructs like `x[key]`
    signature = null,
) {
    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        if (macroEnv.stage < Stage.DisAmbiguate) { return NotYet }
        val args = macroEnv.args

        if (args.size == 0) {
            val problem = LogEntry(
                level = Log.Error,
                template = MessageTemplate.MissingArgument,
                pos = macroEnv.pos,
                values = emptyList(),
            )
            macroEnv.replaceMacroCallWithErrorNode(problem)
            return Fail(problem)
        }

        if (args.key(0) != null) {
            // Container has a parameter name
            val problem = LogEntry(
                level = Log.Error,
                template = MessageTemplate.NoSignatureMatches,
                pos = macroEnv.pos,
                values = emptyList(),
            )
            macroEnv.replaceMacroCallWithErrorNode(problem)
            return Fail(problem)
        }

        val call = macroEnv.call
            // Not in a context that allows rewriting
            ?: return NotYet

        val containerTree = args.valueTree(0)
        fun Planting.buildDotCall(symbolValue: Value<Symbol>, extraArg: Tree? = null) =
            Call(call.pos) {
                Call(containerTree.pos) {
                    val rightEdge = containerTree.pos.rightEdge
                    Rn(rightEdge, dotBuiltinName)
                    Replant(freeTree(containerTree))
                    V(rightEdge, symbolValue)
                }
                for (i in 1 until args.size) {
                    val keyTree = args.keyTree(i)
                    val argTree = args.valueTree(i)
                    if (keyTree != null) {
                        Replant(freeTree(keyTree))
                    }
                    Replant(freeTree(argTree))
                }
                if (extraArg != null) {
                    Replant(extraArg)
                }
            }

        val leftHandedNess = leftHandOfMacroContext(macroEnv)
        if (leftHandedNess == null) {
            // Generate a call to .get
            macroEnv.replaceMacroCallWith {
                buildDotCall(vGetSymbol)
            }
        } else {
            // Generate a call to .set and include the assigned expression as an extra argument.
            macroEnv.replaceMacroCallAncestorWith(leftHandedNess.edgeToReplace) {
                buildDotCall(vSetSymbol, freeTarget(leftHandedNess.assigned))
            }
        }
        return NotYet
    }
}
