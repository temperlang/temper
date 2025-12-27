package lang.temper.be.tmpl

import lang.temper.log.Position
import lang.temper.name.NameMaker

internal fun labelBreaksFromSwitchIfNeeded(
    tree: TmpL.StatementOrTopLevel,
    nameMaker: NameMaker,
): TmpL.StatementOrTopLevel {
    // Avoid extra allocations when we can. Unlabeled break from switch is expected to be uncommon.
    return when {
        needsRewrite(tree) -> rewrite(tree, nameMaker)
        else -> tree
    }
}

private fun needsRewrite(tree: TmpL.StatementOrTopLevel): Boolean {
    fun dig(tree: TmpL.Tree, insideSwitch: Boolean): Boolean {
        if (insideSwitch && tree is TmpL.BreakStatement && tree.label == null) {
            return true
        }
        val nowInsideSwitch = tree is TmpL.ComputedJumpStatement || (insideSwitch && tree !is TmpL.WhileStatement)
        // Iterating allocates an iterator object. Not sure there's a non-allocating option in out trees today, though.
        for (kid in tree.children) {
            if (dig(kid, insideSwitch = nowInsideSwitch)) {
                // Stop iterating once we have any match.
                return true
            }
        }
        return false
    }
    return dig(tree, tree is TmpL.ComputedJumpStatement)
}

private fun rewrite(tree: TmpL.StatementOrTopLevel, nameMaker: NameMaker): TmpL.StatementOrTopLevel {
    return BreakFromSwitchRewriter(nameMaker).rewriteStatementOrTopLevel(tree)
}

private sealed interface BreakTarget
private class LoopBreakTarget(val pos: Position, var label: TmpL.Id?) : BreakTarget
private data object LoopSwitchTarget : BreakTarget

private class BreakFromSwitchRewriter(val nameMaker: NameMaker) : TmpLTreeRewriter {
    private val targets = mutableListOf<BreakTarget>()

    override fun rewriteBreakStatement(x: TmpL.BreakStatement): TmpL.Statement {
        return when {
            x.label == null -> when (targets.lastOrNull()) {
                is LoopSwitchTarget -> targets.lastOrNull { it is LoopBreakTarget }?.let { target ->
                    // Nearest target is switch, so get or make a label for any nearest loop outside it.
                    val label = when (val label = (target as LoopBreakTarget).label) {
                        null -> {
                            val labelName = nameMaker.unusedTemporaryName("label")
                            TmpL.Id(target.pos, labelName).also { target.label = it }
                        }
                        else -> label.deepCopy()
                    }
                    TmpL.BreakStatement(x.pos, TmpL.JumpLabel(label.deepCopy()))
                }
                else -> null
            }
            else -> null
        } ?: super.rewriteBreakStatement(x)
    }

    override fun rewriteComputedJumpStatement(x: TmpL.ComputedJumpStatement): TmpL.Statement {
        // Use a singleton loop switch target because we don't care about labels on them.
        // We could also just walk up parents to find switches, this makes things a bit more organized.
        targets.add(LoopSwitchTarget)
        try {
            return super.rewriteComputedJumpStatement(x)
        } finally {
            targets.removeLast()
        }
    }

    override fun rewriteWhileStatement(x: TmpL.WhileStatement): TmpL.Statement {
        val label = (x.parent as? TmpL.LabeledStatement)?.label?.id
        val target = LoopBreakTarget(x.pos, label).also { targets.add(it) }
        val loop = try {
            super.rewriteWhileStatement(x)
        } finally {
            targets.removeLast()
        }
        return when {
            label == null && target.label != null ->
                TmpL.LabeledStatement(x.pos, TmpL.JumpLabel(target.label!!), loop)
            else -> loop
        }
    }
}
