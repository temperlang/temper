package lang.temper.be.tmpl

import lang.temper.log.Position
import lang.temper.type.WellKnownTypes
import lang.temper.value.BreakOrContinue
import lang.temper.value.JumpSpecifier
import lang.temper.value.void

sealed class GoalSpecifier

data class JumpGoalSpecifier(
    val kind: BreakOrContinue,
    val target: JumpSpecifier,
) : GoalSpecifier()

// TODO Do we also want a FreePanic? And indicate kind in TmpL.ThrowStatement?
object FreeFailure : GoalSpecifier()

object ExitGoalSpecifier : GoalSpecifier()

internal interface GoalTranslator {
    val translator: TmpLTranslator
    val cfOptions get() = translator.cfOptions
    val supportNetwork: SupportNetwork get() = translator.supportNetwork

    fun translateGoal(p: Position, goalSpecifier: GoalSpecifier) = when (goalSpecifier) {
        ExitGoalSpecifier -> translateExit(p)
        FreeFailure -> translateFreeFailure(p)
        is JumpGoalSpecifier -> translateJump(p, goalSpecifier.kind, goalSpecifier.target)
    }

    fun translateJump(p: Position, kind: BreakOrContinue, target: JumpSpecifier): Stmt
    fun translateFreeFailure(p: Position): Stmt
    fun translateExit(p: Position): Stmt

    fun untranslatable(p: Position, diagnostic: String) =
        OneStmt(translator.untranslatableStmt(p, "Cannot translate $diagnostic"))
}

internal class DefaultGoalTranslator(
    override val translator: TmpLTranslator,
) : GoalTranslator {

    override fun translateJump(
        p: Position,
        kind: BreakOrContinue,
        target: JumpSpecifier,
    ): Stmt = untranslatable(p, "$kind $target")

    override fun translateFreeFailure(p: Position): Stmt =
        when (cfOptions.nrbStrategy) {
            BubbleBranchStrategy.IfHandlerScopeVar -> OneStmt(
                TmpL.ReturnStatement(p, TmpL.BubbleSentinel(p)),
            )
            BubbleBranchStrategy.CatchBubble -> OneStmt(
                TmpL.ThrowStatement(p),
            )
        }
    override fun translateExit(p: Position): Stmt {
        val expression = when (cfOptions.representationOfVoid) {
            RepresentationOfVoid.DoNotReifyVoid -> null
            RepresentationOfVoid.ReifyVoid ->
                TmpL.ValueReference(p, WellKnownTypes.voidType2, void)
        }
        return OneStmt(TmpL.ReturnStatement(p, expression))
    }
}
