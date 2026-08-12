package lang.temper.frontend.typestage

import lang.temper.common.Freq3
import lang.temper.name.ResolvedName
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.NameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.StructuredFlow
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.isAssignment
import lang.temper.value.lookThroughDecorations

internal data class UnsetTerminalExpressions(
    val existingAssignments: List<CallTree>,
    val unsetTerminalExpressionEdges: List<TEdge>,
    val blocksMissingTerminators: List<Pair<BlockTree, ControlFlow.StmtBlock?>>,
    /**
     * Is true when there is an unset terminal expression inside an `or...else` clause to
     * avoid problems where it is set in the `or` clause then bubbles over to the `else` clause
     * where it is also set.  Making the `return` variable `var` helps with this since `or...else`
     * control flow tends to require conservative assumptions.
     */
    val terminalsNeedVar: Boolean,
)

/**
 * If a name is set on one path to a terminal expression, then it's the programmer's
 * responsibility to set on all paths to that terminal expression.
 *
 *     if (c) {
 *       return__123 = f();
 *       g();
 *     } else {
 *       h();
 *     }
 *
 * For example, in the above, the terminal expression are `g()` and `h()`.
 * `return__123` is assigned preceding `g()` so the only "unset" terminal expression is `h()`.
 */
internal fun findUnsetTerminalExpressions(
    root: BlockTree,
    outputName: ResolvedName?,
): UnsetTerminalExpressions {
    val finder = TerminalExpressionFinder(root, outputName)
    finder.find()
    return finder.get()
}

private class TerminalExpressionFinder(
    val root: BlockTree,
    val outputName: ResolvedName?,
) {
    val unsetTerminalExpressionEdges = mutableListOf<TEdge>()
    val blocksMissingTerminators = mutableListOf<Pair<BlockTree, ControlFlow.StmtBlock?>>()
    val existingAssignments = mutableListOf<CallTree>()
    var terminalsNeedVar = false

    data class Notes(
        val returnsPrior: Freq3,
        val exclusion: Int,
        val terminalsNeedVar: Boolean,
    ) {
        fun bumpExclusion(delta: Int) = copy(exclusion = exclusion + delta)
    }

    fun find() {
        walkBlock(
            root,
            Notes(returnsPrior = Freq3.Never, exclusion = 0, terminalsNeedVar = false),
            inTerminalPosition = true,
        )
    }

    fun get() = UnsetTerminalExpressions(
        existingAssignments = existingAssignments.toList(),
        unsetTerminalExpressionEdges = unsetTerminalExpressionEdges.toList(),
        blocksMissingTerminators = blocksMissingTerminators.toList(),
        terminalsNeedVar = terminalsNeedVar,
    )

    private fun walkBlock(t: BlockTree, notes: Notes, inTerminalPosition: Boolean): Notes {
        var notes = notes
        when (val flow = t.flow) {
            is StructuredFlow -> {
                notes = walk(t, flow.controlFlow, notes, inTerminalPosition)
            }
            is LinearFlow -> {
                val startIndex = t.parts.startIndex
                val terminalIndex = if (inTerminalPosition) { t.size - 1 } else { -1 }
                if (inTerminalPosition && startIndex > terminalIndex && notes.returnsPrior == Freq3.Never) {
                    // Empty block
                    blocksMissingTerminators.add(t to null)
                    notes = notes.copy(returnsPrior = Freq3.Always)
                } else {
                    for (i in startIndex..<t.size) {
                        notes = try {
                            walkTree(t.child(i), notes, i == terminalIndex)
                        } catch (mte: MissingTerminalExpression) {
                            blocksMissingTerminators.add(t to null)
                            mte.notes.copy(returnsPrior = Freq3.Always)
                        }
                    }
                }
            }
        }
        return notes
    }

    private fun walkTree(t: Tree, notes: Notes, inTerminalPosition: Boolean): Notes {
        if (t is FunTree) {
            return notes
        }
        if (t is BlockTree) {
            return walkBlock(t, notes, inTerminalPosition = inTerminalPosition)
        }

        var notes = notes
        if (isAssignment(t)) {
            val (_, left, _) = t.children
            if (left is NameLeaf && left.content == outputName) {
                existingAssignments.add(t)
                notes = notes.copy(returnsPrior = Freq3.Always)
            }
        }

        for (c in t.children) {
            notes = walkTree(c, notes, inTerminalPosition = false)
        }

        if (inTerminalPosition && notes.returnsPrior == Freq3.Never) {
            // t should be a terminal expression.
            val canBeTerminalExpression: Boolean = when (lookThroughDecorations(t.incoming!!).target) {
                is EscTree, is DeclTree, is StayLeaf, is LeftNameLeaf -> false
                is RightNameLeaf, is ValueLeaf, is CallTree -> notes.exclusion == 0
                is BlockTree,
                is FunTree,
                -> error("returns above")
            }

            // Either we found a return or we found a place one should be inserted.
            notes = notes.copy(returnsPrior = Freq3.Always)
            if (notes.terminalsNeedVar) {
                this.terminalsNeedVar = true
            }
            if (canBeTerminalExpression) {
                this.unsetTerminalExpressionEdges.add(t.incoming!!)
            } else {
                // Signal block handling code that they should add the block
                // to the list of blocks missing returns.
                throw MissingTerminalExpression(notes)
            }
        }

        return notes
    }

    private fun walkRef(t: BlockTree, r: BlockChildReference, notes: Notes, inTerminalPosition: Boolean): Notes {
        val edge = t.dereference(r) ?: return notes
        return walkTree(edge.target, notes, inTerminalPosition = inTerminalPosition)
    }

    private fun walk(t: BlockTree, cf: ControlFlow, notes: Notes, inTerminalPosition: Boolean): Notes {
        var notes = notes
        when (cf) {
            is ControlFlow.If -> {
                notes = walkRef(t, cf.condition, notes.bumpExclusion(+1), false)
                    .bumpExclusion(-1)
                val thenNotes = walk(t, cf.thenClause, notes, inTerminalPosition)
                val elseNotes = walk(t, cf.elseClause, notes, inTerminalPosition)
                notes = mergeNotes(thenNotes, elseNotes)
            }
            is ControlFlow.Loop -> {
                notes = notes.bumpExclusion(1)
                notes = walkRef(t, cf.condition, notes, false)
                notes = walk(t, cf.body, notes, false)
                notes = walk(t, cf.increment, notes, false)
                notes = notes.bumpExclusion(-1)
                if (inTerminalPosition && notes.returnsPrior == Freq3.Never) {
                    throw MissingTerminalExpression(notes)
                }
            }
            is ControlFlow.Jump -> if (inTerminalPosition && notes.returnsPrior == Freq3.Never) {
                throw MissingTerminalExpression(notes)
            }
            is ControlFlow.Labeled -> {
                notes = walk(t, cf.stmts, notes, inTerminalPosition)
            }
            is ControlFlow.OrElse -> {
                val oldTerminalsNeedVar = notes.terminalsNeedVar
                notes = notes.copy(terminalsNeedVar = true)
                try {
                    val orNotes = walk(t, cf.orClause, notes, inTerminalPosition)
                    val elseNotes = walk(t, cf.elseClause, notes, inTerminalPosition)
                    notes = mergeNotes(orNotes, elseNotes)
                        .copy(terminalsNeedVar = oldTerminalsNeedVar)
                } catch (m: MissingTerminalExpression) {
                    throw MissingTerminalExpression(m.notes.copy(terminalsNeedVar = oldTerminalsNeedVar))
                        .initCause(m)
                }
            }
            is ControlFlow.Stmt -> {
                notes = walkRef(t, cf.ref, notes, inTerminalPosition)
            }
            is ControlFlow.StmtBlock -> {
                val stmts = cf.stmts
                if (stmts.isEmpty() && inTerminalPosition) {
                    blocksMissingTerminators.add(t to cf)
                    notes = notes.copy(returnsPrior = Freq3.Always)
                } else {
                    for (i in stmts.indices) {
                        notes = if (inTerminalPosition && i == stmts.lastIndex) {
                            try {
                                walk(t, stmts[i], notes, true)
                            } catch (mte: MissingTerminalExpression) {
                                blocksMissingTerminators.add(t to cf)
                                mte.notes.copy(returnsPrior = Freq3.Always)
                            }
                        } else {
                            walk(t, stmts[i], notes, false)
                        }
                    }
                }
            }
        }
        return notes
    }

    private fun mergeNotes(a: Notes, b: Notes): Notes {
        check(a.exclusion == b.exclusion)
        return if (a.returnsPrior != b.returnsPrior) {
            a.copy(returnsPrior = Freq3.Sometimes)
        } else {
            a
        }
    }
}

private data class MissingTerminalExpression(val notes: TerminalExpressionFinder.Notes) : RuntimeException()
