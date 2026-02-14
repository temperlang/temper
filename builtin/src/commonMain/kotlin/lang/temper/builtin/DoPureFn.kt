package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type.MkType
import lang.temper.type2.Signature2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PartialResult
import lang.temper.value.SpecialFunction
import lang.temper.value.TFunction
import lang.temper.value.Value
import lang.temper.value.freeTree
import lang.temper.value.unpackOrFail

/**
 * <!-- snippet: builtin/doPure -->
 * # doPure
 *
 * `doPure { x }` is the same as `x` except that the result will be available to
 * macro code early.  `x` must be pure code: it can't mutate any environment bindings
 * or objects it does not create and may not print or cause other side effects.
 */
object DoPureFn : SpecialFunction, NamedBuiltinFun {
    override val sigs = run {
        val (tf, t) = makeTypeFormal("doPure", "T")
        val noneToT = hackMapOldStyleToNew(
            MkType.fn(listOf(), listOf(), null, MkType.nominal(tf)),
        )
        listOf(
            Signature2(
                returnType2 = t,
                hasThisFormal = false,
                requiredInputTypes = listOf(noneToT),
                typeFormals = listOf(tf),
            ),
        )
    }

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        if (args.size != 1) {
            val error = LogEntry(
                Log.Error,
                MessageTemplate.ArityMismatch,
                macroEnv.pos,
                listOf(1),
            )
            macroEnv.replaceMacroCallWithErrorNode(error)
            return Fail(error)
        }
        if (interpMode == InterpMode.Partial) {
            // Always visit and expand macros
            macroEnv.evaluateTree(args.valueTree(0), interpMode)
        }
        val fn =
            TFunction.unpackOrFail(args, 0, macroEnv, interpMode = InterpMode.Full) {
                return@invoke it
            }
        val result = macroEnv.dispatchCallTo(
            macroEnv.document.treeFarm.grow(macroEnv.pos) {
                Call(fn) {}
            },
            Value(fn),
            listOf(),
            InterpMode.Full,
        )

        if (macroEnv.stage === Stage.GenerateCode) {
            // Erase this call so we don't have to translate it.
            val argTree = args.valueTree(0)
            macroEnv.replaceMacroCallWith {
                Call(macroEnv.pos) {
                    Replant(freeTree(argTree))
                }
            }
        }

        return result
    }

    override val name: String = "doPure"
}
