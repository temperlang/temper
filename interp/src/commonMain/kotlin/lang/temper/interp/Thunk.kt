package lang.temper.interp

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.env.Environment
import lang.temper.env.InterpMode
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.InterpreterCallback
import lang.temper.value.StayLeaf
import lang.temper.value.StaySink
import lang.temper.value.TEdge
import lang.temper.value.or

/**
 * Used to delay evaluation of initializers.
 */
internal class Thunk(
    val edge: TEdge,
    private val closedOverEnvironment: Environment,
) : CallableValue {
    override val sigs: List<Signature2>? get() = null

    override val isPure: Boolean get() = false

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode) =
        Interpreter.interpreterFor(cb).interpretEdge(edge, closedOverEnvironment, interpMode)
            .or { Fail }

    override fun addStays(s: StaySink) {
        closedOverEnvironment.addStays(s)
        TreeVisit.startingAt(edge.target)
            .forEach {
                if (it is StayLeaf) {
                    s.add(it)
                }
                VisitCue.Continue
            }.visitPreOrder()
    }
}
