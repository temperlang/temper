package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.env.ReferentSource
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.Signature2
import lang.temper.type2.ValueFormalKind
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.LeafTreeType
import lang.temper.value.LeftNameLeaf
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.RightNameLeaf
import lang.temper.value.SpecialFunction
import lang.temper.value.Tree
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.and
import lang.temper.value.dotBuiltinName
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.getSymbol
import lang.temper.value.symbolContained
import lang.temper.value.vSetSymbol

/**
 * Implements assignment to simple left-hand sides
 * <!-- snippet: builtin/= -->
 * # assignment (`=`) builtin
 * The assignment operator allows assigning to local variables, and properties of objects.
 *
 * ```temper
 * var s = "before";
 * console.log(s); //!outputs "before"
 * s = "after"; // <-- assignment
 * console.log(s); //!outputs "after"
 * ```
 *
 * The result of assignment is the result of the right-hand, assigned expression.
 *
 * ```temper
 * let a;
 * let b;
 *
 * // The result of the nested `b = 42` is `42`
 * a = b = 42;
 *
 * a == 42
 * ```
 *
 * Assignment to a property with a setter is really a method call, under the hood.
 *
 * ```temper "a value"
 * class C {
 *   public set p(newValue: String) {
 *     console.log("Assigned ${newValue}.");
 *   }
 * }
 * (new C()).p = "a value" //!outputs "Assigned a value."
 * ```
 */
internal object SetLocalFn : SpecialFunction, NamedBuiltinFun {
    override val sigs: List<Signature2>? = null // Typer has special handling for `=`
    override val name get() = "="

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        if (args.size != 2) {
            return Fail
        }

        when (interpMode) {
            InterpMode.Full -> Unit
            // Try to coerce the left-hand side to a left name
            // Try to coerce the right to a value
            InterpMode.Partial ->
                macroEnv.orderChildMacrosEarly(partialAssignmentFunctionTypes)
        }

        val leftTree = args.valueTree(0)

        // Handle complex left-hand-sides.
        val call = macroEnv.call
        if (
            call != null &&
            leftTree is CallTree &&
            interpMode == InterpMode.Partial &&
            macroEnv.stage <= Stage.SyntaxMacro &&
            args.keyTree(0) == null &&
            args.keyTree(1) == null &&
            isDotGetCall(leftTree)
        ) {
            // This completes work by SquareFn when rewriting `x[key] = newValue`.
            // That macro leaves `x.get(key)`.  Here, we want to change that to `x.set(key)` and
            // to add the value tree to the end.
            val getLookup = leftTree.child(0) as CallTree
            getLookup.replace(2..2) {
                // Replace \get with \set
                V(getLookup.child(2).pos, vSetSymbol)
            }
            val rightTree = args.valueTree(1)
            macroEnv.replaceMacroCallWith {
                Call(call.pos) {
                    leftTree.edges.forEach { Replant(freeTarget(it)) }
                    // Pass the assigned value to the setter
                    Replant(freeTree(rightTree))
                }
            }
            return NotYet
        }

        val leftHandSide: NameLeaf = leftTree as? NameLeaf
            ?: return Fail
        return args.evaluate(1, interpMode).and { value ->
            val leftName = (leftHandSide as? LeftNameLeaf) ?: leftHandSide.copyLeft()
            val effect = when (interpMode) {
                InterpMode.Full -> true
                InterpMode.Partial -> {
                    // Don't assign in partial mode when we don't know if the right-hand side we're
                    // evaluating is the actual one that would be used.
                    val env = macroEnv.environment
                    val name = leftName.content
                    env.referentSource(name) == ReferentSource.SingleSourceAssigned
                }
            }

            if (effect) {
                macroEnv.setLocal(leftName, value).and { value }
            } else {
                value
            }
        }
    }

    override val assignsArgumentOne get() = true

    private val partialAssignmentFunctionTypes = listOf(
        MacroSignature(
            requiredValueFormals = listOf(
                MacroValueFormal(
                    symbol = Symbol("left"),
                    reifiedType = TreeTypeStructureExpectation(setOf(LeafTreeType.LeftName)),
                    kind = ValueFormalKind.Required,
                ),
                MacroValueFormal(
                    symbol = Symbol("right"),
                    reifiedType = TreeTypeStructureExpectation(setOf(LeafTreeType.Value)),
                    kind = ValueFormalKind.Required,
                ),
            ),
            restValuesFormal = null,
            returnType = null,
        ),
    )
}

private fun isDotGetCall(t: Tree): Boolean {
    // Match pattern (Call (Call '.' (...) \get ...) ...)
    val ct = t as? CallTree ?: return false
    val callee = ct.childOrNull(0) as? CallTree ?: return false
    val possibleDot = callee.childOrNull(0) as? RightNameLeaf ?: return false
    if (possibleDot.content != dotBuiltinName) { return false }
    return callee.childOrNull(2)?.symbolContained == getSymbol
}
