package lang.temper.be.tmpl

import lang.temper.log.Position
import lang.temper.type2.Signature2
import lang.temper.value.BreakOrContinue
import lang.temper.value.JumpSpecifier

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
    val bodyFor: BodyFor
    val genre get() = translator.genre

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

internal sealed class BodyFor

/** `return` is not valid in this body context. */
internal data object BodyForModule : BodyFor()

/** `return` is valid and must comport with the signature's return type. */
internal data class BodyForFun(val sig: Signature2) : BodyFor()
