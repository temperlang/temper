package lang.temper.frontend

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.BuiltinFuns.handlerScope
import lang.temper.builtin.isHandlerScopeCall
import lang.temper.builtin.isRttiCall
import lang.temper.frontend.syntax.isAssignment
import lang.temper.frontend.syntax.isCommaCall
import lang.temper.frontend.syntax.isLeftHandSide
import lang.temper.frontend.typestage.simplifyRttiCall
import lang.temper.name.Temporary
import lang.temper.type2.TypeContext2
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.BlockTree
import lang.temper.value.BubbleFn
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.vFailSymbol
import lang.temper.value.vVarSymbol
import lang.temper.value.void

/**
 * Sprinkles calls to [handlerScope] where control leaves the current function.
 *
 * This allows the [Weaver] to create conditional branches for failure paths, making failure
 * explicit in the control flow graph, and allowing later passes to introduce runtime checks so
 * failure implicit in the semantics of the interpreter is turned into explicit boolean checks
 * that are easily translated by backends into code that consistently handles corner cases.
 *
 * Terminology is explained in __Cryptography Engineering__:
 *
 * > Too many engineers consider cryptography to be a sort of magic security dust that they can
 * > sprinkle over their hardware or software, and which will imbue those products with the
 * > mythical property of "security."
 *
 * echoing Bruce Schneier:
 *
 * > In it, I described a mathematical utopia: ...
 * >
 * > The result wasn't pretty. Readers believed that cryptography was a kind of magic security dust.
 *
 * This class is responsible for magically solving all security problems in programs by making
 * failure branches explicit for later passes.
 */
internal class MagicSecurityDust {
    /**
     * Aggregate failure variables allocated across all root blocks.
     * This is used by the Weaver run during the GenerateCodeStage to avoid generating
     * redundant branches to fail.
     *
     * TODO: Maybe make the weaver smart enough to not insert redundant branches to avoid this
     * unnecessarily tight coupling.
     */
    private val allFailureVariables = mutableSetOf<Temporary>()
    internal val failureVariables get() = allFailureVariables.toSet()
    private val typeContext = TypeContext2()

    fun sprinkle(root: BlockTree) {
        // Goal:
        // For each function root,
        // - For each operation, o, that may fail.
        //   - Allocate a temporary, t
        //   - Wrap that operation in a call to hs(t, o) which sets t to a boolean which is true
        //     when o failed.
        //   - Follow that operation with a conditional branch: `if (t) fail()` so that the
        //     Weaver can collect all failing threads.

        val sprinkler = Sprinkler(root)
        sprinkler.sprinkle()
    }

    private inner class Sprinkler(val root: BlockTree) {
        val failureVariables = mutableListOf<Temporary>()

        fun sprinkle() {
            sprinkleOn(root, root.indices)

            val leftPos = root.pos.leftEdge
            val doc = root.document
            allFailureVariables.addAll(failureVariables)
            prefixBlockWith(
                failureVariables.map { failureVariable ->
                    DeclTree(
                        doc,
                        leftPos,
                        listOf(
                            LeftNameLeaf(doc, leftPos, failureVariable),
                            // Failure declared at root level, but hs may be called in loop.
                            ValueLeaf(doc, leftPos, vVarSymbol),
                            ValueLeaf(doc, leftPos, void),
                            ValueLeaf(doc, leftPos, vFailSymbol),
                            ValueLeaf(doc, leftPos, void),
                        ),
                    )
                },
                root,
            )
        }

        private fun sprinkleOn(edge: TEdge) {
            var tree = edge.target
            // `x = bubble()` -> `bubble()`.
            if (isAssignment(tree)) {
                val rhs = tree.childOrNull(2)
                if (rhs != null && isBubbleCallMaybeParameterized(rhs)) {
                    // We handle `bubble()` below, but there are a number of cases where
                    // `bubble()` might be assigned to a variable including source code like
                    // the below:
                    //
                    //     x = bubble();
                    //
                    //     let y = bubble();
                    //
                    //     return bubble();
                    //
                    //     let f(x: Int = bubble()): Int { x }
                    //
                    // It simplifies translation if backends can assume that bubbles are all
                    // top-level statements.
                    // Consider the Java:
                    //
                    //     if (ok) {
                    //       return 1;
                    //     } else {
                    //       throw new SomeThrowableType();
                    //     }
                    //
                    // Javac has special handling so that it knows that there is not a missing
                    // `return` statement there.
                    // That is not in the case where an expression throws.
                    //
                    //     if (ok) {
                    //       return 1;
                    //     } else {
                    //       return Core.throwSomeThrowableType();
                    //     }
                    //
                    // That can work when the language has type inference that allows it to
                    // recognize that the declared return type is what is supposed to be thrown
                    // but not all languages with `return` path checking allow that for all return
                    // types.  Specifically, when a generic type parameter cannot bind to `void`.
                    //
                    // Here, we unpack assignments like `x = bubble()` to avoid later putting an
                    // `hs(...)` wrapper around something that is guaranteed to bubble.
                    // This simplifies control flow analysis allowing more guaranteed failures
                    // to turn into simple `break` statements in the non-exception cases, and
                    // more top-level `throw` statements in the exception case.
                    edge.replace(freeTree(rhs))
                    return
                }
            }

            if (tree is CallTree && isRttiCall(tree)) {
                // Expand runtime type checks so that they're properly woven
                simplifyRttiCall(tree, typeContext)
                tree = edge.target
            }

            if (
                mayFailPerSe(tree) &&
                !isBubbleCallMaybeParameterized(tree) &&
                !isMultiResultProducerHandledElsewhere(tree) &&
                !isAlreadyHandled(tree)
            ) {
                bedazzle(edge) // !! safe because mayFailPerSe(root) is false.
            }
            if (tree is FunTree) {
                // Sprinkle the body separately so that failure
                // variables are scoped to the function.
                val bodyIndex = tree.size - 1
                if (bodyIndex >= 0) {
                    sprinkleOn(tree, 0 until bodyIndex)
                    val bodyEdge = tree.edge(bodyIndex)
                    if (bodyEdge.target !is BlockTree) {
                        bodyEdge.replace(BlockTree.wrap(freeTarget(bodyEdge)))
                    }
                    val bodyBlock = bodyEdge.target as BlockTree
                    Sprinkler(bodyBlock).sprinkle()
                }
            } else {
                sprinkleOn(tree, tree.indices)
            }
        }

        private fun sprinkleOn(parent: Tree, childIndices: IntRange) {
            for (childIndex in childIndices) {
                sprinkleOn(parent.edge(childIndex))
            }
        }

        private fun isMultiResultProducerHandledElsewhere(tree: Tree): Boolean {
            // TODO: is this needed
            if (tree is CallTree) {
                val incoming = tree.incoming!! // Root is not a call.
                val parent = incoming.source!!
                if (isAssignment(parent) && parent.edge(2) == incoming) {
                    if (isCommaCall(parent.child(1))) {
                        return true
                    }
                }
            }
            return false
        }

        private fun isAlreadyHandled(tree: Tree): Boolean {
            // It's already handled if its argument 1 to a call to hs.
            val edge = tree.incoming
            val parent = edge?.source
            return parent is CallTree &&
                isHandlerScopeCall(parent) &&
                edge.edgeIndex == 2
        }

        private fun bedazzle(edge: TEdge) {
            val doc = edge.target.document
            val failVariable = doc.nameMaker.unusedTemporaryName("fail")
            failureVariables.add(failVariable)

            edge.replace { p ->
                Call(p, BuiltinFuns.vHandlerScope) {
                    Ln(p, failVariable)
                    Replant(freeTarget(edge))
                }
            }
        }

        /** True if `tree` may fail without one of its sub-expressions failing. */
        private fun mayFailPerSe(tree: Tree): Boolean {
            if (isLeftHandSide(tree)) {
                // The assignment may fail, but the left-hand side itself does not.
                // In complex left-hand sides, like
                //     array[f()]
                // sub-expressions like f() may fail, but that is prior to the failure of the whole.
                return false
            }
            return when (tree) {
                // May fail if not declared or initialized, but we handle that out of band by
                // replacing with an error node.
                is NameLeaf -> false
                is StayLeaf -> false
                is ValueLeaf -> false
                is CallTree -> {
                    val calleeFn = tree.childOrNull(0)?.functionContained
                    if (
                        calleeFn == BuiltinFuns.setLocalFn && tree.size == BINARY_OP_CALL_ARG_COUNT
                    ) {
                        // Special case assignment since we know a little by inspection of which
                        // local variables have types.
                        val name = tree.child(1) as? LeftNameLeaf
                        // Technically, an assignment can also fail because it's a second
                        // assignment to a `const` variable, but that is caught via static
                        // analysis in later stages.
                        // It might also fail for bad type, but that's also a static check.
                        name == null
                    } else {
                        calleeFn?.callMayFailPerSe != false
                    }
                }
                // May be malformed but well-formedness should be checked statically.
                is FunTree,
                is DeclTree,
                // Optimistic.  Structured flows may be malformed or have broken references but those
                // will not survive static checks.
                is BlockTree,
                is EscTree,
                -> false
            }
        }
    }
}

fun isBubbleCallMaybeParameterized(t: Tree): Boolean {
    if (t.size != 1 || t !is CallTree) { return false }
    var callee = t.childOrNull(0)
    // Unpack (<> bubble ...typeActuals) -> bubble
    if (callee is CallTree) {
        val calleeOfCallee = callee.childOrNull(0)
        if (calleeOfCallee?.functionContained == BuiltinFuns.angleFn) {
            callee = callee.childOrNull(1)
        }
    }
    return callee?.functionContained is BubbleFn
}
