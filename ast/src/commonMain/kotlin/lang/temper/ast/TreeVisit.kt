package lang.temper.ast

import lang.temper.value.Tree

private val continueUnit = VisitCue.Continue to Unit
private fun ignore2(
    @Suppress("UNUSED_PARAMETER") a: Unit,
    @Suppress("UNUSED_PARAMETER") b: Unit,
) = Unit

/** Specifies what, if anything, to visit next. */
enum class VisitCue {
    /** Proceed to next in the specified order. */
    Continue,

    /**
     * Backtrack.  For pre-order, means do not visit children of the current node,
     * and instead proceed to the next sibling or ancestor if any.
     */
    SkipOne,

    /** Do not visit any more nodes. */
    AllDone,
}

object TreeVisit {
    fun startingAt(root: Tree): TreeVisitRooted =
        TreeVisitRootedImpl(root)

    interface TreeVisitRooted {
        fun forEachContinuing(
            f: (tree: Tree) -> Unit,
        ): TreeVisitComplete<Unit>

        fun forEach(
            f: (tree: Tree) -> VisitCue,
        ): TreeVisitComplete<Unit>

        fun <X> forEach(
            f: (tree: Tree) -> Pair<VisitCue, X>,
        ): TreeVisitNeedsFold<X>

        fun <X> fold(x0: X, fold: (X, X) -> X): TreeVisitNeedsVisitor<X>
    }

    interface TreeVisitNeedsVisitor<X> {
        fun forEach(f: (tree: Tree) -> Pair<VisitCue, X>): TreeVisitComplete<X>
    }

    interface TreeVisitNeedsFold<X> {
        fun fold(x0: X, fold: (X, X) -> X): TreeVisitComplete<X>
    }

    interface TreeVisitComplete<X> {
        /** visits a node before its children. */
        fun visitPreOrder(): X

        /** visits a node after its children. */
        fun visitPostOrder(): X
    }
}

private class TreeVisitRootedImpl(
    val root: Tree,
) : TreeVisit.TreeVisitRooted {
    override fun forEachContinuing(
        f: (tree: Tree) -> Unit,
    ): TreeVisit.TreeVisitComplete<Unit> = TreeVisitImpl(root, Unit, ::ignore2) {
        f(it)
        continueUnit
    }

    override fun forEach(
        f: (tree: Tree) -> VisitCue,
    ): TreeVisit.TreeVisitComplete<Unit> = TreeVisitImpl(root, Unit, ::ignore2) {
        f(it) to Unit
    }

    override fun <X> forEach(
        f: (tree: Tree) -> Pair<VisitCue, X>,
    ): TreeVisit.TreeVisitNeedsFold<X> = TreeVisitNeedsFoldImpl(root, f)

    override fun <X> fold(x0: X, fold: (X, X) -> X): TreeVisit.TreeVisitNeedsVisitor<X> =
        TreeVisitNeedsVisitorImpl(root, x0, fold)
}

private class TreeVisitImpl<X>(
    val root: Tree,
    val x0: X,
    val fold: (X, X) -> X,
    val f: (tree: Tree) -> Pair<VisitCue, X>,
) : TreeVisit.TreeVisitComplete<X> {
    override fun visitPreOrder(): X = visitPreOrder(root, f, x0, fold).second
    override fun visitPostOrder(): X = visitPostOrder(root, f, x0, fold).second
}

private class TreeVisitNeedsFoldImpl<X>(
    val root: Tree,
    val f: (tree: Tree) -> Pair<VisitCue, X>,
) : TreeVisit.TreeVisitNeedsFold<X> {
    override fun fold(x0: X, fold: (X, X) -> X) = TreeVisitImpl(root, x0, fold, f)
}

private class TreeVisitNeedsVisitorImpl<X>(
    val root: Tree,
    val x0: X,
    val fold: (X, X) -> X,
) : TreeVisit.TreeVisitNeedsVisitor<X> {
    override fun forEach(f: (tree: Tree) -> Pair<VisitCue, X>) =
        TreeVisitImpl(root, x0, fold, f)
}

private fun <X> visitPreOrder(
    t: Tree,
    f: (tree: Tree) -> Pair<VisitCue, X>,
    x0: X,
    fold: (X, X) -> X,
): Pair<VisitCue, X> {
    val (cue, xp) = f(t)
    val x = fold(x0, xp)
    return when (cue) {
        VisitCue.AllDone -> VisitCue.AllDone to x
        VisitCue.SkipOne -> VisitCue.Continue to x
        VisitCue.Continue -> visitChildren(t, f, x, fold, ::visitPreOrder)
    }
}

private fun <X> visitPostOrder(
    t: Tree,
    f: (tree: Tree) -> Pair<VisitCue, X>,
    x0: X,
    fold: (X, X) -> X,
): Pair<VisitCue, X> {
    val (cue, x) = visitChildren(t, f, x0, fold, ::visitPostOrder)
    return when (cue) {
        VisitCue.AllDone -> VisitCue.AllDone to x
        VisitCue.SkipOne -> error("$cue")
        VisitCue.Continue -> {
            val (tcue, xp) = f(t)
            tcue to fold(x, xp)
        }
    }
}

private fun <X> visitChildren(
    t: Tree,
    f: (tree: Tree) -> Pair<VisitCue, X>,
    x0: X,
    fold: (X, X) -> X,
    visitEach: (Tree, (Tree) -> Pair<VisitCue, X>, X, (X, X) -> X) -> Pair<VisitCue, X>,
): Pair<VisitCue, X> {
    var x = x0

    var cue = VisitCue.Continue
    childLoop@
    for (c in t.children) {
        val (ccue, xc) = visitEach(c, f, x, fold)
        x = xc // Recursive call will have folded
        when (ccue) {
            VisitCue.AllDone -> {
                cue = VisitCue.AllDone
                break@childLoop
            }
            VisitCue.Continue -> {}
            VisitCue.SkipOne -> error("$ccue")
        }
    }
    return cue to x
}
