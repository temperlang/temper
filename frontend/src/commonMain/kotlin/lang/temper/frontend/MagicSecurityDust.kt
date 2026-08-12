package lang.temper.frontend

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.isRttiCall
import lang.temper.builtin.isTypeAngleCall
import lang.temper.frontend.syntax.isAssignment
import lang.temper.frontend.syntax.isCommaCall
import lang.temper.frontend.typestage.simplifyRttiCall
import lang.temper.type.AndType
import lang.temper.type.FunctionType
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes
import lang.temper.type.isBubbly
import lang.temper.type2.TypeContext2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.mapType
import lang.temper.value.BlockTree
import lang.temper.value.BubbleFn
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.ControlFlow
import lang.temper.value.FunTree
import lang.temper.value.LinearFlow
import lang.temper.value.PanicFn
import lang.temper.value.ReifiedType
import lang.temper.value.StructuredFlow
import lang.temper.value.TEdge
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.typeFromSignature
import kotlin.collections.listOf

/**
 * Wraps calls to functions that may fail with blocks so that failing calls become top-level
 * when weaved.  This uses [type inferences][Tree.typeInferences] so is a no-op until we
 * have type information in the tree.
 *
 * This allows the [Weaver] to pull the call to the function's or module's root, which eases
 * translating failure handling, like unpacking *Result* values or inserting `try/catch` statements,
 * in languages that require statement level constructs which cannot nest inside larger expressions.
 *
 * Terminology is explained in __Cryptography Engineering__:
 *
 * > Too many engineers consider cryptography to be a sort of magic security dust that they can
 * > sprinkle over their hardware or software, and which will imbue those products with the
 * > mythical property of "security."
 *
 * Echoing Bruce Schneier:
 *
 * > In it, I described a mathematical utopia: ...
 * >
 * > The result wasn't pretty. Readers believed that cryptography was a kind of magic security dust.
 *
 * This class is responsible for magically solving all security problems in programs by making
 * failure branches explicit for later passes.
 */
internal class MagicSecurityDust {
    private val typeContext = TypeContext2()

    fun sprinkle(root: BlockTree) {
        // Goal:
        // For each function root,
        // - For each operation, o, that may fail.
        //   - Allocate a temporary, t
        //   - Wrap that operation in a block.
        // Weaver can then weave blocks into the module/function body

        val sprinkler = Sprinkler(root)
        sprinkler.sprinkle()
    }

    private inner class Sprinkler(val root: BlockTree) {
        fun sprinkle() {
            sprinkleOn(root, root.indices)
        }

        private fun sprinkleOn(edge: TEdge) {
            var tree = edge.target
            // `x = bubble()` -> `bubble()`.
            if (isAssignment(tree)) {
                val (_, lhs, rhs) = tree.children
                if (isBubbleCallMaybeParameterized(rhs)) {
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
                    // recognize that the declared return type is what is supposed to be thrown,
                    // but not all languages with `return` path checking allow that for all return
                    // types.  Specifically, when a generic type parameter cannot bind to `void`.
                    //
                    // Here, we unpack assignments like `x = bubble()` to avoid later putting a
                    // `{...}` wrapper around something that is guaranteed to bubble.
                    // This simplifies control-flow analysis, allowing more guaranteed failures
                    // to turn into simple `break` statements in the locally handled cases, and
                    // more top-level `throw` statements in the exception case.
                    edge.replace {
                        Block {
                            Replant(freeTree(rhs))
                            tree.edge(2).replace {
                                val ti = lhs.typeInferences?.type?.let { panicCallTypeInferences(it) }
                                Call(rhs.pos.rightEdge, ti) {
                                    if (ti != null) {
                                        Call(BuiltinFuns.vAngleFn) {
                                            V(BuiltinFuns.vPanic, ti.variant)
                                            for ((_, t) in ti.bindings2) {
                                                V(
                                                    Value(
                                                        ReifiedType(hackMapOldStyleToNew(t as StaticType)),
                                                        TType,
                                                    ),
                                                    WellKnownTypes.typeType,
                                                )
                                            }
                                        }
                                    } else {
                                        V(BuiltinFuns.vPanic)
                                    }
                                }
                            }
                            // Replant the assignment so that simple initialized-before-use checks
                            // work even if the assigned name is later used somewhere else as in:
                            //
                            //     x = bubble();
                            //     f(x)  // Unreachable but uses `x`.
                            Replant(freeTree(tree))
                        }
                    }
                    return
                }
            }

            if (tree is CallTree && isRttiCall(tree)) {
                // Expand runtime type checks so that they're properly woven
                simplifyRttiCall(tree, typeContext)
                tree = edge.target
            }

            if (
                tree is CallTree &&
                calleeReturnsResult(tree) &&
                !isBubbleCallMaybeParameterized(tree) &&
                !isMultiResultProducerHandledElsewhere(tree) &&
                !isAlreadyHandled(tree)
            ) {
                bedazzle(edge)
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
            // It's already handled if it's in its own block.
            val edge = tree.incoming
            when (val parent = edge?.source) {
                is BlockTree -> return !isCondition(parent, edge)
                is CallTree if isAssignment(parent) -> {
                    if (parent.edge(2) == edge) { // Is assigned
                        val parentEdge = parent.incoming
                        val grandParent = parentEdge?.source
                        if (grandParent is BlockTree) {
                            return !isCondition(grandParent, parentEdge)
                        }
                    }
                }

                else -> {}
            }
            return false
        }

        /**
         * True if edge's is used as a condition in blockTree's flow control.
         *
         * Conditions in `if` and `while` statements often have to be simple expressions
         * in target languages, so any unpacking of result objects into temporaries and
         * use of further `if`s to test the result needs to be done outside the `if`/`while`
         * condition expression.
         *
         * Above, we require more temporary capture for conditions.
         */
        private fun isCondition(blockTree: BlockTree, edge: TEdge): Boolean {
            val edgeIndices = isConditionCache.getOrPut(blockTree) {
                buildSet {
                    when (val flow = blockTree.flow) {
                        LinearFlow -> {}
                        is StructuredFlow -> {
                            fun walk(cf: ControlFlow) {
                                when (cf) {
                                    is ControlFlow.Conditional ->
                                        cf.condition.index?.let { index -> add(index) }
                                    else -> {}
                                }
                                for (clause in cf.clauses) {
                                    walk(clause)
                                }
                            }
                            walk(flow.controlFlow)
                        }
                    }
                }
            }
            return edge.source == blockTree && edge.edgeIndex in edgeIndices
        }
        private val isConditionCache = mutableMapOf<BlockTree, Set<Int>>()

        private fun bedazzle(edge: TEdge) {
            edge.replace { p ->
                Block(p) {
                    Replant(freeTarget(edge))
                }
            }
        }

        /** True if `tree` may fail without one of its sub-expressions failing. */
        private fun calleeReturnsResult(tree: CallTree): Boolean {
            var callee = tree.childOrNull(0) ?: return false // Error nodes panic
            if (callee is CallTree && isTypeAngleCall(callee)) {
                callee = callee.child(1)
            }
            val calleeType = callee.typeInferences?.type
                ?: return false // Too soon to say
            return canBubble(calleeType)
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

private fun panicCallTypeInferences(t: StaticType): CallTypeInferences {
    val sig = PanicFn.sigs[1]
    val bindings = mapOf(sig.typeFormals[0] to t)
    val bindings2 = bindings.mapValues { hackMapOldStyleToNew(it.value) }
    return CallTypeInferences(
        hackMapNewStyleToOld(sig.returnType2.mapType(bindings2)),
        typeFromSignature(sig),
        bindings,
        listOf(),
    )
}

private fun canBubble(calleeType: StaticType): Boolean = when (calleeType) {
    is AndType -> calleeType.members.any { canBubble(it) }
    is FunctionType -> calleeType.returnType.isBubbly
    else -> false
}
