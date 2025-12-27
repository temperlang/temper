package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TBoolean
import lang.temper.value.Tree
import lang.temper.value.elseSymbol
import lang.temper.value.freeTree
import lang.temper.value.ifBuiltinName

/**
 * Support for the short-circuiting binary operators: `&&` and `||`
 */
internal sealed class DesugarShortCircuitingLogicalOperationMacro : NamedBuiltinFun {
    override val sigs: List<Signature2> = twoBooleansToBoolean

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args
        if (args.size != 2) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(2))
        }
        val eager = args.valueTree(0)
        val lazy = args.valueTree(1)
        desugar(macroEnv, eager, lazy)
        return NotYet
    }

    protected abstract fun desugar(macroEnv: MacroEnvironment, eager: Tree, lazy: Tree)

    companion object {
        private val twoBooleansToBoolean = listOf(
            Signature2(
                returnType2 = WellKnownTypes.booleanType2,
                requiredInputTypes = listOf(WellKnownTypes.booleanType2, WellKnownTypes.booleanType2),
                hasThisFormal = false,
            ),
        )
    }
}

/**
 * <!-- snippet: builtin/&& -->
 * # `&&`
 * `a && b` performs a short-circuiting [logical-and] of its arguments as in C.
 * By short-circuiting, we mean that if `a` is [snippet/builtin/false], then `b` is never evaluated,
 * so you may assume that `a` is [snippet/builtin/true] when writing `b`.
 *
 * | a     | b     | a && b |
 * | ----- | ----- | ------ |
 * | false | false | false  |
 * | false | true  | false  |
 * | true  | false | false  |
 * | true  | true  | true   |
 *
 * ```temper
 * let isTwiceIsh(numerator: Int, denominator: Int): Void {
 *   // Here, panic asserts that the division will never fail.
 *   if (denominator != 0 && numerator / denominator == 2 orelse panic()) {
 *     console.log("yes");
 *   } else {
 *     console.log("no");
 *   }
 * }
 * isTwiceIsh(4, 2); //!outputs "yes"
 * isTwiceIsh(3, 1); //!outputs "no"
 * isTwiceIsh(1, 0); //!outputs "no"
 * // -ish might be doing a lot of work.
 * ```
 *
 * [logical-and]: https://en.wikipedia.org/wiki/Logical_conjunction#Truth_table
 */
internal object DesugarLogicalAnd : DesugarShortCircuitingLogicalOperationMacro() {
    override val name: String = "&&"

    override fun desugar(macroEnv: MacroEnvironment, eager: Tree, lazy: Tree) {
        // `if` statements like
        //     if (a) { b } else { false }
        // are parsed to a tree like
        //     if(a, fn {
        //         b;
        //     }, \else, fn (f#0) {
        //         f#0(fn {
        //             false;
        //         });
        //     });
        // so we recreate this structure below.
        macroEnv.replaceMacroCallWith {
            Call(macroEnv.pos.leftEdge) {
                Rn(macroEnv.callee.pos, ifBuiltinName)
                Replant(freeTree(eager))
                Fn(lazy.pos.leftEdge) {
                    Replant(freeTree(lazy))
                }
                val rightEdge = macroEnv.pos.rightEdge
                V(rightEdge, elseSymbol)
                Fn(rightEdge) {
                    val f0 = macroEnv.nameMaker.unusedTemporaryName("f")
                    Decl(rightEdge, f0) {}
                    Call(rightEdge) {
                        Rn(rightEdge, f0)
                        Fn(rightEdge) {
                            V(rightEdge, TBoolean.valueFalse)
                        }
                    }
                }
            }
        }
    }
}

/**
 * <!-- snippet: builtin/|| -->
 * # `||`
 * `a || b` performs a short-circuiting [logical-or] of its arguments as in C.
 * By short-circuiting, we mean that if `a` is [snippet/builtin/true], then `b` is never evaluated,
 * so you may assume that `a` is [snippet/builtin/false] when writing `b`.
 *
 * | a     | b     | a \|\| b |
 * | ----- | ----- | -------- |
 * | false | false | false    |
 * | false | true  | true     |
 * | true  | false | true     |
 * | true  | true  | true     |
 *
 * ```temper
 * let isTwiceIsh(numerator: Int, denominator: Int): Void {
 *   // Here, panic asserts that the division will never fail.
 *   if (denominator == 0 || numerator / denominator == 2 orelse panic()) {
 *     console.log("yes");
 *   } else {
 *     console.log("no");
 *   }
 * }
 * isTwiceIsh(4, 2); //!outputs "yes"
 * isTwiceIsh(0, 0); //!outputs "yes"
 * isTwiceIsh(3, 1); //!outputs "no"
 * // -ish might be doing a lot of work.
 * ```
 *
 * [logical-or]: https://en.wikipedia.org/wiki/Logical_disjunction#Semantics
 */
internal object DesugarLogicalOr : DesugarShortCircuitingLogicalOperationMacro() {
    override val name: String = "||"

    override fun desugar(macroEnv: MacroEnvironment, eager: Tree, lazy: Tree) {
        // `if` statements like
        //     if (a) { true } else { b }
        // are parsed to a tree like
        //     if(a, fn {
        //         true;
        //     }, \else, fn (f#0) {
        //         f#0(fn {
        //             b;
        //         });
        //     });
        // so we recreate this structure below.
        macroEnv.replaceMacroCallWith {
            Call(macroEnv.pos.leftEdge) {
                Rn(macroEnv.callee.pos, ifBuiltinName)
                Replant(freeTree(eager))
                Fn(eager.pos.rightEdge) {
                    V(TBoolean.valueTrue)
                }
                val lazyLeft = lazy.pos.leftEdge
                V(lazyLeft, elseSymbol)
                Fn(lazyLeft) {
                    val f0 = macroEnv.nameMaker.unusedTemporaryName("f")
                    Decl(lazyLeft, f0) {}
                    Call(lazyLeft) {
                        Rn(lazyLeft, f0)
                        Fn(lazyLeft) {
                            Replant(freeTree(lazy))
                        }
                    }
                }
            }
        }
    }
}
