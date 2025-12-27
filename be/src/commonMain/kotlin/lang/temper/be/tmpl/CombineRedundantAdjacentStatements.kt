package lang.temper.be.tmpl

import lang.temper.log.Position
import lang.temper.log.spanningPosition

/**
 * Some code generates some awkward sequences of statements that clog up the
 * printed form making the debug output harder to read.
 *
 * This step is semantically unnecessary, but makes the debug output easier to read.
 */
internal fun combineRedundantAdjacentStatements(stmts: MutableList<TmpL.Statement>) {
    for (i in (1 until stmts.size).reversed()) {
        val prev = stmts[i - 1]
        val stmt = stmts[i]

        val combined: TmpL.Statement? = when {
            prev is TmpL.IfStatement && stmt is TmpL.IfStatement -> {
                val sameTest = prev.test == stmt.test
                val oppositeTest = areInverses(prev.test, stmt.test)
                if (sameTest || oppositeTest) {
                    val pc = prev.consequent
                    val pa = prev.alternate
                    val sc = stmt.consequent
                    val sa = stmt.alternate
                    // Unlink parts so we can reuse
                    prev.consequent = TmpL.BlockStatement(prev.pos, emptyList())
                    prev.alternate = TmpL.BlockStatement(prev.pos, emptyList())
                    stmt.consequent = TmpL.BlockStatement(stmt.pos, emptyList())
                    stmt.alternate = TmpL.BlockStatement(stmt.pos, emptyList())
                    // Edit in place
                    val combinedConsequent = combineBlocks(
                        pc,
                        if (sameTest) { sc } else { sa },
                        pc.pos,
                    )
                    val combinedAlternate = combineBlocks(
                        pa,
                        if (sameTest) { sa } else { sc },
                        pa?.pos ?: prev.pos.rightEdge,
                    )
                    // Replace both with one
                    if (sameTest || inverseOrNull(prev.test) == null) {
                        prev.consequent = combinedConsequent
                        prev.alternate = combinedAlternate
                        prev
                    } else {
                        stmt.consequent = combinedAlternate
                        stmt.alternate = combinedConsequent
                        stmt
                    }
                } else {
                    null
                }
            }
            prev is TmpL.LocalDeclaration && stmt is TmpL.Assignment && prev.init == null &&
                prev.name == stmt.left && stmt.right is TmpL.Expression -> {
                val right = stmt.right as TmpL.Expression
                stmt.right = TmpL.BubbleSentinel(right.pos)
                prev.init = right
                prev
            }
            else -> null
        }

        if (combined != null) {
            stmts.removeAt(i)
            stmts[i - 1] = combined
        }
    }
}

private fun areInverses(a: TmpL.Expression, b: TmpL.Expression): Boolean =
    a == inverseOrNull(b) || inverseOrNull(a) == b

private fun inverseOrNull(e: TmpL.Expression): TmpL.Expression? =
    if (e is TmpL.PrefixOperation && e.op.kind == TmpLOperatorDefinition.Bang) {
        e.operand
    } else {
        null
    }

private fun combineBlocks(
    before: TmpL.Statement?,
    after: TmpL.Statement?,
    pos: Position,
): TmpL.BlockStatement = when {
    before != null && after != null -> {
        val beforeStatements = (before as? TmpL.BlockStatement)?.takeBody()
            ?: listOf(before)
        val afterStatements = (after as? TmpL.BlockStatement)?.takeBody()
            ?: listOf(after)
        TmpL.BlockStatement(
            listOf(before, after).spanningPosition(pos),
            beforeStatements + afterStatements,
        )
    }
    after != null ->
        after as? TmpL.BlockStatement ?: TmpL.BlockStatement(after.pos, listOf(after))
    before != null ->
        before as? TmpL.BlockStatement ?: TmpL.BlockStatement(before.pos, listOf(before))
    else -> TmpL.BlockStatement(pos, emptyList())
}
