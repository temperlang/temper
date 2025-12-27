package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.value.Fail
import lang.temper.value.IfThenElse
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TNull
import lang.temper.value.TVoid
import lang.temper.value.Value
import lang.temper.value.freeTree
import lang.temper.value.vInitSymbol
import lang.temper.value.vSsaSymbol

/**
 * <!-- snippet: builtin/%3F%3F : operator `??` -->
 * # Null-coalescing `??`
 * Infix `??` allows choosing a default value when the subject is null.
 *
 * ```temper
 * let prod(i: Int, j: Int?): Int { i * (j ?? 1) }
 * prod(2, 3)    == 6 &&
 * prod(2, null) == 2
 * ```
 */
internal object CoalesceMacro : BuiltinMacro("??", null) {
    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        // All this front matter is mostly boilerplate.
        if (interpMode != InterpMode.Partial) {
            return macroEnv.fail(MessageTemplate.CannotInvokeMacroAsFunction, macroEnv.pos)
        }
        if (macroEnv.stage > Stage.Define) {
            macroEnv.replaceMacroCallWithErrorNode()
            return Fail
        }
        if (macroEnv.args.size != 2) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, macroEnv.pos, listOf(2))
        }
        if (macroEnv.stage < Stage.Define) {
            return NotYet
        }
        // Get our bearings.
        val subject = macroEnv.args.valueTree(0)
        val subjectAsName = subject as? NameLeaf
        val name = subjectAsName?.content ?: macroEnv.nameMaker.unusedTemporaryName("subject")
        // Build the main conditional call.
        val ifStmt = macroEnv.treeFarm.grow(macroEnv.pos) {
            IfThenElse(
                {
                    Call {
                        V(Value(BuiltinFuns.notEqualsFn))
                        Rn(name)
                        V(TNull.value)
                    }
                },
                { Rn(name) },
                { Replant(freeTree(macroEnv.args.valueTree(1))) },
            )
        }
        // Replace the macro call.
        macroEnv.replaceMacroCallWith {
            when (subjectAsName) {
                // Use a nested block and temp var if we didn't have a simple name.
                null -> Block {
                    Decl {
                        Ln(name)
                        V(vInitSymbol)
                        Replant(freeTree(subject))
                        V(vSsaSymbol)
                        V(TVoid.value)
                    }
                    Replant(ifStmt)
                }

                // Use just the `if` statement if we did have a simple name.
                else -> Replant(ifStmt)
            }
        }
        return NotYet
    }
}

val vCoalesceMacro = Value(CoalesceMacro)
