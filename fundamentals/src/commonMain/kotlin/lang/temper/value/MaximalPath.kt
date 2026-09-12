package lang.temper.value

import lang.temper.common.ClosedOpenRange
import lang.temper.common.Console
import lang.temper.common.Either
import lang.temper.common.Escape
import lang.temper.common.Escaper
import lang.temper.common.FixedEscape
import lang.temper.common.ForwardOrBack
import lang.temper.common.IdentityEscape
import lang.temper.common.LeftOrRight
import lang.temper.common.NonsenseGradient
import lang.temper.common.abbreviate
import lang.temper.common.charCount
import lang.temper.common.compatRemoveFirst
import lang.temper.common.compatRemoveLast
import lang.temper.common.console
import lang.temper.common.decodeUtf16
import lang.temper.common.inverse
import lang.temper.common.partiallyOrder
import lang.temper.common.putMultiList
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.spanningPosition
import lang.temper.log.unknownPos
import lang.temper.name.Temporary
import kotlin.jvm.JvmInline

sealed interface MaximalPathAdjacency {
    /** The index of the adjacent path. */
    val pathIndex: MaximalPathIndex?

    /**
     * [ForwardOrBack.Back] when the transition involves a
     * going back to the beginning of a loop.
     */
    val dir: ForwardOrBack
}

data class Follower(
    /**
     * The condition under which control jumps to the start of the path at [pathIndex]
     * or `null` if control unconditionally jumps.
     */
    val condition: MaximalPath.PathElement?,
    /** May be `null` if condition is guaranteed `false`. */
    override val pathIndex: MaximalPathIndex?,
    override val dir: ForwardOrBack,
) : MaximalPathAdjacency

data class Preceder(
    override val pathIndex: MaximalPathIndex,
    /** [ForwardOrBack.Back] when the transition involves going back to the beginning of a loop. */
    override val dir: ForwardOrBack,
) : MaximalPathAdjacency {
    override fun toString(): String = when (dir) {
        ForwardOrBack.Forward -> "$pathIndex"
        ForwardOrBack.Back -> "#<-${pathIndex.index}"
    }
}

@JvmInline
value class MaximalPathIndex(
    val index: Int,
) : Comparable<MaximalPathIndex> {
    override fun compareTo(other: MaximalPathIndex): Int = this.index.compareTo(other.index)

    override fun toString(): String = "#$index"

    data class Range(
        private val intRange: IntRange,
    ) : ClosedOpenRange<MaximalPathIndex>, ClosedRange<MaximalPathIndex>, Iterable<MaximalPathIndex> {
        override val endInclusive: MaximalPathIndex
            get() = MaximalPathIndex(intRange.last)
        override val start: MaximalPathIndex
            get() = MaximalPathIndex(intRange.first)
        override val endExclusive: MaximalPathIndex
            get() = MaximalPathIndex(intRange.last + 1)

        override fun iterator() =
            @Suppress("IteratorNotThrowingNoSuchElementException") // delegates to one that does
            object : Iterator<MaximalPathIndex> {
                private val underlying = intRange.iterator()

                override fun hasNext(): Boolean = underlying.hasNext()

                override fun next(): MaximalPathIndex = MaximalPathIndex(underlying.next())
            }

        override fun contains(value: MaximalPathIndex): Boolean = value.index in intRange

        override fun isEmpty(): Boolean = intRange.isEmpty()
    }

    companion object {
        /** Sometimes we need to track (from, to) pairs of indices. -1 is used to mean "before entry" */
        val beforeEntry = MaximalPathIndex(-1)
    }
}

data class MaximalPath(
    val pathIndex: MaximalPathIndex,
    /**
     * The child references executed as part of this path
     * in the context of the flow nodes and edges they come from.
     */
    val elements: List<AstElement>,
    /** A position to highlight the path in a log sink message. */
    val diagnosticPosition: Position,
    val preceders: List<Preceder>,
    val followers: List<Follower>,
    /**
     * If this path starts the `else` clause of an
     * [`orelse` flow construct][ControlFlow.OrElse], then this is the label
     * associated with the corresponding [`or` clause][ControlFlow.OrElse.orClause].
     *
     * In the case where an `else` clause is empty, it's nice to be able to uniquely
     * identify the `else` clause within a CFG even in cases like the below where
     * there are no instructions in the path that would distinguish it from others.
     */
    val orLabel: JumpLabel?,
) {
    /** Can serve as a reason for a transition. */
    sealed interface PathElement : Positioned {
        /**
         * The [MaximalPath.pathIndex] of the path whose [MaximalPath.elements] contains this.
         */
        val pathIndex: MaximalPathIndex

        fun toDebugString(root: BlockTree): String
    }

    /**
     * Indicates that a transition happens when a failure bubbles up
     * from one of the path's [AstElement]s.
     */
    data class Bubbled(
        override val pos: Position,
        override val pathIndex: MaximalPathIndex,
        val followerIndex: Int,
    ) : PathElement {
        override fun toDebugString(root: BlockTree): String = toString()
    }

    /**
     * Bundles the position in the block's CFG with the
     * [BlockChildReference] that may be used to get at the relevant edge.
     */
    data class AstElement(
        val ref: BlockChildReference,
        /**
         * If the block has a structured flow, and the element is not a
         * condition, points to the [ControlFlow.Stmt] to allow for easy mutation
         * of the CFG.
         */
        val stmt: ControlFlow.Stmt?,
        /**
         * The [MaximalPath.pathIndex] of the path whose [MaximalPath.elements] contains this.
         */
        override val pathIndex: MaximalPathIndex,
        val isCondition: Boolean,
    ) : PathElement, Positioned by ref {
        override fun equals(other: Any?): Boolean =
            this === other || (other is AstElement && this.ref == other.ref)

        override fun hashCode(): Int = this.ref.hashCode()

        override fun toString(): String = "MaximalPath.Element($ref @ ${ref.pos} in path$pathIndex)"

        override fun toDebugString(root: BlockTree): String =
            root.dereference(ref)?.target?.toPseudoCode() ?: "broken ref"
    }

    val elementsAndConditions: Sequence<PathElement> get() = sequenceOf(
        elements,
        followers.mapNotNull { it.condition },
    ).flatten()
}

data class MaximalPaths(
    val maximalPaths: List<MaximalPath>,
    /**
     * The index of the path that the control flow graph starts at.
     */
    val entryPathIndex: MaximalPathIndex,
    /**
     * Indices of paths that end at the control flow graph exits.
     */
    val exitPathIndices: Set<MaximalPathIndex>,
    /**
     * Indices of paths that exit the control flow graph with a failure.
     */
    val failExitPathIndices: Set<MaximalPathIndex>,
) {
    operator fun get(pathIndex: MaximalPathIndex) = maximalPaths[pathIndex.index]

    val pathIndices get() = MaximalPathIndex.Range(maximalPaths.indices)

    companion object {
        val zeroValue = MaximalPaths(
            maximalPaths = listOf(
                MaximalPath(
                    MaximalPathIndex(0),
                    emptyList(),
                    unknownPos,
                    emptyList(),
                    emptyList(),
                    null,
                ),
            ),
            entryPathIndex = MaximalPathIndex(0),
            exitPathIndices = setOf(MaximalPathIndex(0)),
            failExitPathIndices = emptySet(),
        )
    }
}

private class ProvisionalIndex {
    var index: MaximalPathIndex? = null
}

private class MutPath(
    // Bits set once we've organized everything.
    // These are included in the constructor so that we can ensure they're
    // copied properly.
    var isExit: Boolean,
    var isFailExit: Boolean,
) {
    val index = ProvisionalIndex()
    var orLabel: JumpLabel? = null

    // When we collapse one mut path into another, the receiver adopts the formers' indices.
    var indices = mutableSetOf(index)

    /** So we can generate a good diagnostic position */
    val positionHints = mutableListOf<Position>()

    val elements = mutableListOf<Either<ControlFlow.Stmt, ControlFlow.Conditional>>()

    /** Things to link to */
    val preceders = mutableListOf<MutEdge>()
    val followers = mutableListOf<MutEdge>()

    override fun toString() = "MutPath$indices"
}

private sealed class MutPathElement {
    abstract fun toPathElement(
        pathIndex: MaximalPathIndex,
        followerIndex: Int?,
    ): MaximalPath.PathElement
}

private class MutAstElement(
    val ref: BlockChildReference,
    val stmt: ControlFlow.Stmt?,
    val isCondition: Boolean,
) : MutPathElement() {
    override fun toPathElement(pathIndex: MaximalPathIndex, followerIndex: Int?) =
        MaximalPath.AstElement(ref, stmt, pathIndex, isCondition)
}

private class MutBubbled(val pos: Position) : MutPathElement() {
    override fun toPathElement(pathIndex: MaximalPathIndex, followerIndex: Int?) =
        MaximalPath.Bubbled(pos, pathIndex, followerIndex!!)
}

private class MutEdge(
    val condition: MutPathElement?,
    var from: ProvisionalIndex,
    var to: ProvisionalIndex,
    val dir: ForwardOrBack,
)

private const val BUBBLY_CALL_BIT = 1
private const val AT_START_BIT = 2
private const val AT_END_BIT = 4

enum class ConservativeFailure(val bits: Int) {
    Never(0), // Ok, that's a choice.
    CalleeTypeOnly(BUBBLY_CALL_BIT),
    AtStartOfOrOnly(AT_START_BIT),
    AtStartOfOr(AT_START_BIT or BUBBLY_CALL_BIT),
    AtEndOfOrOnly(AT_END_BIT),
    AtEndOfOr(AT_END_BIT or BUBBLY_CALL_BIT),
    AtStartAndEndOnly(AT_START_BIT or AT_END_BIT),
    AtStartAndEnd(AT_START_BIT or AT_END_BIT or BUBBLY_CALL_BIT),
    ;

    val atBubblyCall: Boolean get() = (bits and BUBBLY_CALL_BIT) != 0
    val atStart: Boolean get() = (bits and AT_START_BIT) != 0
    val atEnd: Boolean get() = (bits and AT_END_BIT) != 0

    infix fun or(other: ConservativeFailure) =
        fromBits(this.bits or other.bits)
    infix fun and(other: ConservativeFailure) =
        fromBits(this.bits and other.bits)

    companion object {
        private fun fromBits(bits: Int): ConservativeFailure = when (bits) {
            0 -> Never
            (BUBBLY_CALL_BIT) -> CalleeTypeOnly
            (AT_START_BIT) -> AtStartOfOrOnly
            (AT_START_BIT or BUBBLY_CALL_BIT) -> AtStartOfOr
            (AT_END_BIT) -> AtEndOfOrOnly
            (AT_END_BIT or BUBBLY_CALL_BIT) -> AtEndOfOr
            (AT_START_BIT or AT_END_BIT) -> AtStartAndEndOnly
            (AT_START_BIT or AT_END_BIT or BUBBLY_CALL_BIT) -> AtStartAndEnd
            else -> error("$bits")
        }
    }
}

/**
 * The minimal set of maximal paths.
 * The set is a small set of paths that are useful for flow-sensitive analyses.
 *
 * The paths are maximal in that none will be reliably followed by only one other
 * path (modulo opaque predicates).
 */
fun forwardMaximalPaths(
    root: BlockTree,
    /**
     * Whether to as if every `orelse`'s *or* clause has
     * an implicit jump straight to the *else* clause.
     * This guarantees that *else* clauses are visited.
     *
     * [ConservativeFailure.CalleeTypeOnly] looks at types of callees and adds
     * a [MaximalPath.Bubbled] transition when the callee has a *Result* type.
     * If type information is available and accurate then this is a precise option,
     * and it's good for warning about unnecessary *orelse* uses.
     *
     * [ConservativeFailure.AtStartAndEnd] introduces a [MaximalPath.Bubbled] at the
     * start and end of an `orelse`'s *or* clause.  This is the most conservative
     * option.
     * [ConservativeFailure.AtStartOfOr] only introduces such a transition at the
     * start, and [ConservativeFailure.AtEndOfOr] only at the end.
     *
     * For example, when looking at:
     *
     *     var x;
     *     do {
     *       x = ff(1);
     *     } orelse do {
     *       x = f(2);
     *     }
     *     console.log(x);
     *
     * If you only inserted one at the start (assuming `ff` has no type info),
     * you would accurately realize that `x` is reliably
     * assigned, but not that it can be multiply assigned.
     *
     * If you only inserted a bubble transition at the end, then you would not
     * realize that the `x = f(2)` assignment *is* necessary for `x` to be usable
     * in the later log statement.
     *
     * If you insert both transitions, your analysis that `x` is assigned, needs to
     * be `var`, and is reliably initialized before first read all are conservatively
     * accurate.
     */
    fails: ConservativeFailure,
    /**
     * Calls to the builtin `yield` function are sometimes significant for control
     * flow operations.
     */
    yieldingCallsEndPaths: Boolean = false,
    ignoreConstantConditions: Boolean = false,
): MaximalPaths {
    val mapMaker = MapMaker(
        root = root,
        fails = fails,
        yieldingCallsEndPaths = yieldingCallsEndPaths,
        ignoreConstantConditions = ignoreConstantConditions,
    )
    return mapMaker.makeMap()
}

private class MapMaker(
    val root: BlockTree,
    val fails: ConservativeFailure,
    val yieldingCallsEndPaths: Boolean,
    val ignoreConstantConditions: Boolean,
) {
    fun truthinessOf(ref: BlockChildReference?): Boolean? {
        if (ignoreConstantConditions || ref == null) {
            return null
        }
        val tree = root.dereference(ref)?.target
        return tree?.valueContained(TBoolean)
    }

    val mutPaths = mutableListOf<MutPath>()

    fun newPath(): MutPath {
        val p = MutPath(
            isExit = false,
            isFailExit = false,
        )
        mutPaths.add(p)
        return p
    }

    fun newPath(posHint: Position): MutPath = newPath().also { it.positionHints.add(posHint) }

    fun addFollower(
        from: MutPath,
        to: MutPath,
        condition: MutPathElement?,
        dir: ForwardOrBack = ForwardOrBack.Forward,
    ) {
        val edge = MutEdge(condition, from.index, to.index, dir)
        from.followers.add(edge)
        to.preceders.add(edge)
    }

    fun addFollower(
        from: MutPath,
        to: MutPath,
        dir: ForwardOrBack = ForwardOrBack.Forward,
    ) {
        addFollower(from = from, to = to, condition = (null as MutPathElement?), dir = dir)
    }

    fun addFollower(
        from: MutPath,
        to: MutPath,
        condition: BlockChildReference?,
        dir: ForwardOrBack = ForwardOrBack.Forward,
    ) {
        val conditionElement = condition?.let { ref ->
            MutAstElement(ref, null, isCondition = true)
        }
        addFollower(from = from, to = to, condition = conditionElement, dir = dir)
    }

    fun joinAll(preceders: Set<MutPath>): MutPath? {
        if (preceders.isEmpty()) {
            return null
        }
        val joinPath = newPath()
        preceders.forEach {
            addFollower(it, joinPath)
        }
        return joinPath
    }

    fun maybeJoinAll(preceders: Set<MutPath>): MutPath? {
        if (preceders.size == 1) {
            val only = preceders.first()
            if (only.followers.isEmpty()) {
                return only
            }
        }
        return joinAll(preceders)
    }

    private val jumpTargets = mutableMapOf<JumpTarget, Lazy<MutPath>>()

    fun <T> withJumpTargets(
        newJumps: List<Pair<JumpTarget, Lazy<MutPath>>>,
        action: () -> T,
    ): T {
        val replaced = mutableListOf<Pair<JumpTarget, Lazy<MutPath>?>>()
        for ((jumpTarget, newSet) in newJumps) {
            replaced.add(jumpTarget to jumpTargets.remove(jumpTarget))
            jumpTargets[jumpTarget] = newSet
        }
        val result = action()
        for ((jumpTarget, oldSet) in replaced) {
            if (oldSet != null) {
                jumpTargets[jumpTarget] = oldSet
            } else {
                jumpTargets.remove(jumpTarget)
            }
        }
        return result
    }

    // For each orClause being processed, the path to start the else clause.
    // The zero-th item is the target for free bubbles.
    val orClauseFailOverStack: MutableList<Lazy<MutPath>> = mutableListOf(
        lazy { newPath(root.pos.rightEdge) },
    )

    fun buildPaths(
        controlFlow: ControlFlow,
        preceders: Set<MutPath>,
    ): Set<MutPath> = when (controlFlow) {
        is ControlFlow.If -> {
            val inPath = maybeJoinAll(preceders)
            if (inPath == null) {
                emptySet()
            } else {
                when (val truthiness = truthinessOf(controlFlow.condition)) {
                    null -> {
                        val intoThen = newPath()
                        intoThen.positionHints.add(controlFlow.thenClause.pos.leftEdge)
                        val intoElse = newPath()
                        intoElse.positionHints.add(controlFlow.elseClause.pos.leftEdge)

                        addFollower(inPath, intoThen, controlFlow.ref)
                        addFollower(inPath, intoElse)
                        buildPaths(controlFlow.thenClause, setOf(intoThen)) +
                            buildPaths(controlFlow.elseClause, setOf(intoElse))
                    }

                    else -> {
                        inPath.elements.add(Either.Right(controlFlow))
                        buildPaths(
                            if (truthiness) {
                                controlFlow.thenClause
                            } else {
                                controlFlow.elseClause
                            },
                            setOf(inPath),
                        )
                    }
                }
            }
        }

        is ControlFlow.Loop -> {
            val loopStart = joinAll(preceders)
            val conditionTruthiness = truthinessOf(controlFlow.condition)
            val checkPosition = controlFlow.checkPosition
            if (loopStart == null) {
                emptySet()
            } else if (conditionTruthiness == false && checkPosition == LeftOrRight.Left) {
                // Body and increment never reached
                loopStart.elements.add(Either.Right(controlFlow))
                setOf(loopStart)
            } else {
                val specifiers = buildList {
                    add(DefaultJumpSpecifier)
                    controlFlow.label?.let { add(NamedJumpSpecifier(it)) }
                }
                val afterLoop = lazy {
                    newPath(controlFlow.pos.rightEdge)
                }
                val beforeIncrement = lazy {
                    newPath(controlFlow.increment.pos.leftEdge)
                }
                val errorJumps = lazy {
                    // Trying to continue from within the increment clause is just silly,
                    // but we need some way to detect that.
                    newPath(controlFlow.increment.pos)
                }

                val beforeBody: MutPath = when (checkPosition) {
                    LeftOrRight.Left -> {
                        val afterCond = newPath()
                        addFollower(
                            loopStart,
                            afterCond,
                            if (conditionTruthiness == null) { // false handled above
                                controlFlow.condition
                            } else {
                                null
                            },
                        )
                        if (truthinessOf(controlFlow.condition) != true) {
                            addFollower(loopStart, afterLoop.value)
                        }
                        afterCond
                    }

                    LeftOrRight.Right -> loopStart
                }

                val afterBody = withJumpTargets(
                    specifiers.map { JumpTarget(BreakOrContinue.Break, it) to afterLoop } +
                        specifiers.map { JumpTarget(BreakOrContinue.Continue, it) to beforeIncrement },
                ) {
                    buildPaths(controlFlow.body, setOf(beforeBody))
                }
                val beforeIncrementJoined = maybeJoinAll(afterBody + beforeIncrement.toSet())
                val afterIncrement = if (beforeIncrementJoined != null) {
                    withJumpTargets(
                        specifiers.map { JumpTarget(BreakOrContinue.Break, it) to afterLoop } +
                            specifiers.map { JumpTarget(BreakOrContinue.Continue, it) to errorJumps },
                    ) {
                        buildPaths(controlFlow.increment, setOf(beforeIncrementJoined))
                    }
                } else {
                    emptySet()
                }

                val beforeContinue = maybeJoinAll(afterIncrement)
                if (beforeContinue != null) {
                    when (checkPosition) {
                        LeftOrRight.Left -> {
                            addFollower(beforeContinue, loopStart, dir = ForwardOrBack.Back)
                        }

                        LeftOrRight.Right -> {
                            if (conditionTruthiness != false) {
                                val continuePath = newPath()
                                addFollower(
                                    beforeContinue,
                                    continuePath,
                                    if (conditionTruthiness == null) {
                                        controlFlow.condition
                                    } else {
                                        null
                                    },
                                )
                                addFollower(continuePath, loopStart, dir = ForwardOrBack.Back)
                            }
                            if (conditionTruthiness != true) {
                                addFollower(beforeContinue, afterLoop.value)
                            }
                        }
                    }
                }
                if (errorJumps.isInitialized()) {
                    TODO("do something with errorJumps")
                }
                afterLoop.toSet()
            }
        }

        is ControlFlow.Jump -> {
            val target = JumpTarget(controlFlow.jumpKind, controlFlow.target)
            val rightOfJump = controlFlow.pos.rightEdge
            preceders.forEach { it.positionHints.add(rightOfJump) }
            val forTarget: Lazy<MutPath>? = jumpTargets[target]
            if (forTarget != null) {
                joinAll(preceders)?.let { preceder ->
                    addFollower(preceder, forTarget.value)
                }
            }
            setOf()
        }

        is ControlFlow.Labeled -> {
            val afterLabeled = lazy {
                newPath(controlFlow.pos.rightEdge)
            }
            val targets = buildList {
                add(
                    JumpTarget(BreakOrContinue.Break, NamedJumpSpecifier(controlFlow.breakLabel))
                        to afterLabeled,
                )
                val continueLabel = controlFlow.continueLabel
                if (continueLabel != null) {
                    add(
                        JumpTarget(BreakOrContinue.Continue, NamedJumpSpecifier(continueLabel))
                            to afterLabeled,
                    )
                    add(JumpTarget(BreakOrContinue.Continue, DefaultJumpSpecifier) to afterLabeled)
                }
            }
            val afterStmts = withJumpTargets(targets) {
                buildPaths(controlFlow.stmts, preceders)
            }
            afterLabeled.toSet() + afterStmts
        }

        is ControlFlow.OrElse -> {
            val orClause = controlFlow.orClause
            val elseClause = controlFlow.elseClause

            val beforeElse = lazy {
                newPath(elseClause.pos.leftEdge).also {
                    it.orLabel = orClause.breakLabel
                }
            }

            orClauseFailOverStack.add(beforeElse)
            var beforeOr = maybeJoinAll(preceders)
            if (fails.atStart && beforeOr != null) {
                val orLeftPos = controlFlow.orClause.pos.leftEdge
                val startOfOr = newPath()
                startOfOr.positionHints.add(orLeftPos)
                addFollower(beforeOr, beforeElse.value, MutBubbled(orLeftPos))
                addFollower(beforeOr, startOfOr)
                beforeOr = startOfOr
            }

            var afterOr = withJumpTargets(
                listOf(
                    JumpTarget(BreakOrContinue.Break, NamedJumpSpecifier(controlFlow.orClause.breakLabel))
                        to beforeElse,
                ),
            ) {
                buildPaths(orClause.stmts, setOfNotNull(beforeOr))
            }

            orClauseFailOverStack.compatRemoveLast() // === beforeElse
            if (fails.atEnd) {
                // Some passes use maximal paths to traverse things in a sensible order,
                // and `fails` hint allows those passes to reach everything.
                val afterOrJoined = joinAll(afterOr)
                if (afterOrJoined != null) {
                    addFollower(
                        afterOrJoined,
                        beforeElse.value,
                        MutBubbled(orClause.pos.rightEdge),
                    )
                    afterOr = setOf(afterOrJoined)
                }
            }

            val afterElse = if (beforeElse.isInitialized()) {
                buildPaths(elseClause, setOf(beforeElse.value))
            } else {
                emptySet()
            }
            afterOr + afterElse
        }

        is ControlFlow.Stmt -> {
            val tree = root.dereference(controlFlow.ref)?.target
            var path = maybeJoinAll(preceders)
            if (path == null) {
                setOf()
            } else if (tree != null && isBubbleCall(tree)) {
                val failOver by orClauseFailOverStack.last()
                path.positionHints.add(tree.pos.leftEdge)
                path.positionHints.add(tree.pos.rightEdge)
                addFollower(path, failOver)
                setOf()
            } else {
                path.elements.add(Either.Left(controlFlow))
                val bubbles = tree != null && fails.atBubblyCall && treeCanBubble(tree)
                val yieldingCallDetails = if (yieldingCallsEndPaths) {
                    disassembleYieldingCall(controlFlow, root)
                } else {
                    null
                }

                if (bubbles || yieldingCallDetails != null) {
                    val continues = newPath()
                    continues.positionHints.add(tree!!.pos.rightEdge)

                    if (bubbles) {
                        val failOver = orClauseFailOverStack.last()
                        addFollower(path, failOver.value, MutBubbled(controlFlow.pos))
                    }
                    addFollower(path, continues)

                    path = continues
                }
                setOfNotNull(path)
            }
        }

        is ControlFlow.StmtBlock -> {
            var before = preceders
            for (stmt in controlFlow.stmts) {
                if (preceders.isEmpty()) {
                    break
                }
                before = buildPaths(stmt, before)
            }
            before
        }
    }

    fun makeMap(): MaximalPaths {
        val entryPath = newPath()

        val topControlFLow = when (val flow = root.flow) {
            LinearFlow -> ControlFlow.StmtBlock(
                root.pos,
                root.children.mapIndexed { index, tree ->
                    ControlFlow.Stmt(BlockChildReference(index, tree.pos))
                },
            )

            is StructuredFlow -> flow.controlFlow
        }

        val atExit = buildPaths(topControlFLow, setOf(entryPath))

        // join the exit nodes so downstream code doesn't get confused by exit
        // branches that also branch back to non-exit branches.
        maybeJoinAll(atExit)?.let {
            it.isExit = true
        }

        check(orClauseFailOverStack.size == 1)
        val atFailExit = orClauseFailOverStack.compatRemoveFirst().toSet()
        maybeJoinAll(atFailExit)?.let {
            it.isFailExit = true
        }

        eliminateEmptyTransitions(mutPaths, root, yieldingCallsEndPaths = yieldingCallsEndPaths)

        // Now that we've got the final list, assign final indices.
        check(entryPath in mutPaths)
        var indexCounter = 0
        fun assignIndex(mutPath: MutPath) {
            check(mutPath.index in mutPath.indices)
            if (mutPath.index.index == null) {
                val assignedIndex = MaximalPathIndex(indexCounter++)
                for (provisionalIndex in mutPath.indices) {
                    check(provisionalIndex.index == null)
                    provisionalIndex.index = assignedIndex
                }
            }
        }
        assignIndex(entryPath)
        for (p in mutPaths) {
            assignIndex(p)
        }
        check(entryPath.index.index == MaximalPaths.zeroValue.entryPathIndex) // Use zero

        val exitPathIndices = setOfNotNull(
            // We joined above, so there's at most one
            mutPaths.firstOrNull { it.isExit }?.index?.index,
        )
        val failExitPathIndices = setOfNotNull(
            // We joined above, so there's at most one
            mutPaths.firstOrNull { it.isFailExit }?.index?.index,
        )

        val maximalPaths = mutPaths.map { mutPath ->
            val pathIndex = mutPath.index.index!!
            val elements = mutPath.elements.map { e ->
                when (e) {
                    is Either.Left ->
                        MaximalPath.AstElement(e.item.ref, e.item, pathIndex, isCondition = false)

                    is Either.Right ->
                        MaximalPath.AstElement(e.item.condition, null, pathIndex, isCondition = true)
                }
            }
            val preceders = mutPath.preceders.map {
                Preceder(it.from.index!!, it.dir)
            }
            val followers = mutPath.followers.mapIndexed { followerIndex, mutFollower ->
                Follower(
                    mutFollower.condition?.toPathElement(pathIndex, followerIndex),
                    mutFollower.to.index!!,
                    mutFollower.dir,
                )
            }
            val positioned = buildList {
                mutPath.positionHints.mapTo(this) { Either.Left(it) }
                elements.mapTo(this) { Either.Right(it) }
                followers.mapNotNullTo(this) { follower ->
                    (follower.condition as? MaximalPath.AstElement)?.let { Either.Right(it) }
                }
            }
            val diagnosticPosition = diagnosticPositionForPathContents(positioned, root)
            MaximalPath(
                pathIndex = pathIndex,
                elements = elements,
                diagnosticPosition = diagnosticPosition,
                preceders = preceders,
                followers = followers,
                orLabel = mutPath.orLabel,
            )
        }

        return MaximalPaths(
            maximalPaths = maximalPaths,
            entryPathIndex = entryPath.index.index!!,
            exitPathIndices = exitPathIndices,
            failExitPathIndices = failExitPathIndices,
        )
    }
}

/**
 * Eliminate empty transitions.
 *
 * Empty transitions directly affect the runtime cost of coroutine bodies
 * that are converted into state-machines by contributing unnecessary states.
 */
private fun eliminateEmptyTransitions(
    mutPaths: MutableList<MutPath>,
    root: BlockTree,
    yieldingCallsEndPaths: Boolean,
) {
    val indexToPath = mutableMapOf<ProvisionalIndex, MutPath>()
    for (m in mutPaths) {
        indexToPath[m.index] = m
    }

    while (true) {
        // The way we allocate a MutPath for each branch in ControlFlow.If above
        // means that sometimes we have a case where one branch leads to one other
        // and is the only one that leads to it.
        val includeInto = mutableMapOf<MutPath, MutPath>()
        for (path in indexToPath.values.toList()) {
            // Before: ... -> A -> B -> ...
            // After:  ... -> AB -> ...
            // If something is preceded by one branch, that only flows into it, collapse
            // them regardless of their content
            if (path.preceders.size == 1 && path.orLabel == null) {
                val preceder = path.preceders.first()
                if (preceder.dir == ForwardOrBack.Forward) {
                    if (indexToPath[preceder.from] == indexToPath[preceder.to]) {
                        // A self-loop.
                        continue
                    }
                    val precederPath = indexToPath.getValue(preceder.from)
                    if (
                        precederPath.followers.size == 1 &&
                        (!yieldingCallsEndPaths || !endsYielding(precederPath, root))
                    ) {
                        val follower = precederPath.followers.first()
                        if (follower.condition == null && follower.dir == ForwardOrBack.Forward) {
                            check(indexToPath[follower.to] == path)
                            includeInto[path] = precederPath
                            continue
                        }
                    }
                }
            }
            // Before: ... ─> [] ─┬─> A ─> ...
            //         ... ───────┘
            // After:  ... ───────┬─> A ─> ...
            //         ... ───────┘
            if (path.followers.size == 1 && path.elements.isEmpty()) {
                val follower = path.followers.first()
                if (follower.condition == null && follower.dir == ForwardOrBack.Forward) {
                    if (path !in includeInto) { // Avoid the above clobbering this
                        // Not handled above so not a simple continuation
                        val kept = path
                        val eliminated = indexToPath.getValue(follower.to)
                        if (
                            eliminated !in includeInto && eliminated.orLabel == null &&
                            (!yieldingCallsEndPaths || !endsYielding(kept, root))
                        ) {
                            includeInto[eliminated] = kept
                            path.positionHints.clear()
                            continue
                        }
                    }
                }
            }
        }

        // Since we have computed some eliminations from follower to preceder
        // and some the other direction, break any cycles we accidentally
        // introduced arbitrarily.
        val cycleSet = mutableSetOf<MutPath>()
        for (eliminated in includeInto.keys.toList()) {
            cycleSet.clear()
            var pathIndex = eliminated
            while (true) {
                if (pathIndex in cycleSet) {
                    includeInto.remove(pathIndex)
                    break
                }
                cycleSet.add(pathIndex)
                pathIndex = includeInto[pathIndex] ?: break
            }
        }

        if (includeInto.isEmpty()) { break }

        val includedFrom = includeInto.inverse()

        for (path in mutPaths) {
            if (path in includeInto) {
                // It is flattened when the loop reached the eventual destination
                continue
            }
            val parts = buildList {
                var inclusionPath = path
                while (true) {
                    add(inclusionPath)
                    inclusionPath = includedFrom[inclusionPath] ?: break
                }
            }
            if (parts.size <= 1) { continue }

            path.isExit = parts.any { it.isExit }
            path.isFailExit = parts.any { it.isFailExit }

            path.followers.removeAll {
                it.to in parts[1].indices
            }

            for (i in 1..parts.lastIndex) {
                val part = parts[i]

                part.preceders.forEach { p ->
                    if (p.from !in path.indices) {
                        path.preceders.add(p)
                    }
                }

                path.indices.addAll(part.indices)
                part.indices.clear()

                path.elements.addAll(part.elements)
                path.positionHints.addAll(part.positionHints)

                val nextPath = parts.getOrNull(i + 1)
                part.followers.forEach { f ->
                    if (nextPath == null || f.to !in nextPath.indices) {
                        path.followers.add(f)
                    }
                }
            }

            for (index in path.indices) {
                indexToPath[index] = path
            }
        }
    }

    // Some paths can't be folded into because we only fold followers
    // into preceders and the follower might have valuable metadata.
    // But we can identify empty paths with one unconditional follower and
    // without valuable metadata and just forward their preceders to their
    // follower.
    while (true) {
        var progressMade = false
        for (p in mutPaths) {
            if (p.index in p.indices) {
                // Still the canonical path for its index
                if (
                    p.elements.isEmpty() && p.orLabel == null && p.preceders.isNotEmpty() &&
                    p.followers.size == 1 && p.followers[0].condition == null &&
                    !p.isExit && !p.isFailExit
                ) {
                    val follower = p.followers[0]
                    val allPrecedersToRelink = mutableListOf<MutEdge>()
                    if (follower.condition == null && follower.dir == ForwardOrBack.Forward) {
                        val followerNode = indexToPath.getValue(follower.to)
                        if (followerNode === p) { continue }

                        p.indices.clear()
                        for (preceder in p.preceders) {
                            preceder.to = follower.to
                            allPrecedersToRelink.add(preceder)
                        }
                        val index = followerNode.preceders.indexOf(follower)
                        followerNode.preceders.removeAt(index)
                        followerNode.preceders.addAll(index, allPrecedersToRelink)
                        progressMade = true
                    }
                }
            }
        }
        if (!progressMade) { break }
    }

    // But we can identify empty paths with one unconditional preceder.
    while (true) {
        var progressMade = false
        for (p in mutPaths) {
            if (p.index in p.indices) {
                // Still the canonical path for its index
                if (
                    p.elements.isEmpty() && p.orLabel == null &&
                    p.preceders.size == 1 && p.preceders[0].condition == null &&
                    !p.isExit && !p.isFailExit
                ) {
                    val preceder = p.preceders[0]
                    val precederNode = indexToPath.getValue(preceder.from)
                    if (precederNode === p) { continue }

                    val allFollowersToRelink = mutableListOf<MutEdge>()
                    if (preceder.condition == null && preceder.dir == ForwardOrBack.Forward) {
                        p.indices.clear()
                        for (follower in p.followers) {
                            follower.from = preceder.from
                            allFollowersToRelink.add(follower)
                        }
                        val index = precederNode.followers.indexOf(preceder)
                        precederNode.followers.removeAt(index)
                        precederNode.followers.addAll(index, allFollowersToRelink)
                        progressMade = true
                    }
                }
            }
        }
        if (!progressMade) { break }
    }

    mutPaths.removeAll { it.index !in it.indices }
}

/**
 * Dump a representation of the paths to the given [console] if it's nonnull
 * using [root] to dereference any path elements for pseudocode.
 */
fun MaximalPaths.debug(console: Console?, root: BlockTree) {
    val detail = PseudoCodeDetail(elideFunctionBodies = true)
    console?.group("paths") {
        console.log("entry #$entryPathIndex")
        if (exitPathIndices.isNotEmpty()) {
            console.log(
                exitPathIndices.joinToString(prefix = "exit ") { "$it" },
            )
        }
        if (failExitPathIndices.isNotEmpty()) {
            console.log(
                failExitPathIndices.joinToString(prefix = "failExit ") { "$it" },
            )
        }
        this.maximalPaths.forEachIndexed { index, path ->
            console.group("path $index: ${path.diagnosticPosition}") {
                fun renderReference(ref: BlockChildReference) =
                    root.dereference(ref)?.target?.toPseudoCode(singleLine = true, detail = detail)
                        ?: "<broken>"
                fun renderCondition(cond: MaximalPath.PathElement) = when (cond) {
                    is MaximalPath.Bubbled -> "bubbled"
                    is MaximalPath.AstElement -> renderReference(cond.ref)
                }
                if (path.preceders.isNotEmpty()) {
                    console.log("preceded by ${path.preceders}")
                }
                path.elements.forEach { element ->
                    console.log(". ${renderReference(element.ref)}")
                }
                if (path.followers.isNotEmpty()) {
                    console.group("followed by") {
                        path.followers.forEach { f ->
                            console.log(
                                buildString {
                                    when (f.dir) {
                                        ForwardOrBack.Forward -> {}
                                        ForwardOrBack.Back -> append("continue ")
                                    }
                                    if (f.pathIndex != null) {
                                        append(f.pathIndex)
                                    } else {
                                        append("never")
                                    }
                                    if (f.condition != null) {
                                        append(" when ")
                                        append(renderCondition(f.condition))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Topologically sort path indices, ignoring `continue` edges. */
fun orderedPathIndices(
    maximalPaths: MaximalPaths,
    /**
     * If [dir] is [ForwardOrBack.Back] then [preceders][MaximalPath.preceders]
     * will be ordered before the indices of the paths they precede.
     *
     * If [dir] is [ForwardOrBack.Forward] then [followers][MaximalPath.followers]
     * will be ordered before the indices of the paths they follow.
     */
    dir: ForwardOrBack,
): List<MaximalPathIndex> = partiallyOrder(
    maximalPaths.pathIndices,
    buildMap {
        maximalPaths.maximalPaths.forEach { p ->
            val adjacent = when (dir) {
                ForwardOrBack.Forward -> p.followers
                ForwardOrBack.Back -> p.preceders
            }
            this[p.pathIndex] = adjacent.mapNotNull {
                if (it.dir == ForwardOrBack.Forward) {
                    it.pathIndex
                } else {
                    null
                }
            }
        }
    },
) { it }

private fun endsYielding(mutPath: MutPath, root: BlockTree): Boolean {
    val ref = when (val last = mutPath.elements.lastOrNull()) {
        null -> return false
        is Either.Left -> last.item.ref
        is Either.Right -> last.item.ref
    }
    var tree = root.dereference(ref)?.target ?: return false
    if (isAssignment(tree)) {
        tree = tree.child(2)
    }
    return tree.yieldingCallKind() != null
}

fun diagnosticPositionForPathContents(
    /**
     * Each is either a zero-width position that marks the start of a block
     * or an actual code element.
     */
    positioned: List<Either<Position, MaximalPath.AstElement>>,
    root: BlockTree,
): Position {
    // A lot of positions are synthesized declarations, especially in the entry segment.
    // Partition the elements into probably-synthetic and probably not synthetic.
    // If we have some of the latter, use them to find a diagnostic position that'll be
    // useful when generating excerpts of code in error messages.
    val positionedGrouped =
        mutableMapOf<NonsenseGradient, MutableList<Positioned>>()
    for (p in positioned) {
        val (nonsensity, positionedElement) =
            positionAndConfidenceForBasicBlockPositionPart(p, root)
        positionedGrouped.putMultiList(nonsensity, positionedElement)
    }

    var minimalNonsenseGroup = emptyList<Positioned>()
    for (ng in NonsenseGradient.entries.reversed()) {
        if (minimalNonsenseGroup.isNotEmpty()) {
            break
        }
        minimalNonsenseGroup = positionedGrouped[ng] ?: emptyList()
    }
    return minimalNonsenseGroup.spanningPosition(
        minimalNonsenseGroup.firstOrNull()?.pos ?: root.pos.leftEdge,
    )
}

private fun positionAndConfidenceForBasicBlockPositionPart(
    e: Either<Position, MaximalPath.AstElement>,
    root: BlockTree,
): Pair<NonsenseGradient, Positioned> = when (e) {
    is Either.Left -> NonsenseGradient.NotSuss to e.item
    is Either.Right -> {
        // Figure out whether a positioned element is probably out of position, or
        // whether it corresponds to a lexical construct that was part of the
        // logically containing path.
        val ref = e.item.ref

        val tree = root.dereference(ref)?.target
        when {
            tree == null || tree is DeclTree && tree.parts?.name?.content is Temporary ->
                // A temporary declaration that may have been pulled from anywhere
                // to the top of a block.
                NonsenseGradient.ProbableNonsense to e.item

            tree is ValueLeaf && tree.content == void ->
                // Eliminated nodes could be nonsense
                NonsenseGradient.PossibleNonsense to e.item

            e.item.isCondition -> NonsenseGradient.NotSuss to LeftSideOf(ref)
            else -> NonsenseGradient.NotSuss to ref
        }
    }
}

private data class LeftSideOf(val p: Positioned) : Positioned by p

private data class MermaidEscaper(
    override val quote: Char? = '"',
    val extraEscapes: Map<Int, Escape>? = null,
) : Escaper {
    object HashNumSemiEscape : Escape {
        override fun escapeTo(codePoint: Int, out: StringBuilder) {
            out.append('#').append(codePoint).append(';')
        }
    }

    companion object {
        // mermaid.js.org/syntax/flowchart.html#special-characters-that-break-syntax
        private val baseEscapes = buildMap {
            for (control in 0..0x1f) {
                this[control] = HashNumSemiEscape
            }
            for (c in listOf('[', ']', '\'', ';', '\\', '#', '`')) {
                this[c.code] = HashNumSemiEscape
            }
            this['"'.code] = FixedEscape("#quot;")
            this['&'.code] = FixedEscape("#amp;")
            this['<'.code] = FixedEscape("#lt;")
            this['>'.code] = FixedEscape("#gt;")
        }

        val default = MermaidEscaper()
    }

    override fun escapeTo(cs: CharSequence, out: StringBuilder) {
        val quote = this.quote
        if (quote != null) {
            out.append(quote)
        }
        var i = 0
        val limit = cs.length
        while (i < limit) {
            val cp = decodeUtf16(cs, i)
            i += charCount(cp)
            val esc: Escape = baseEscapes[cp]
                ?: extraEscapes?.get(cp)
                ?: IdentityEscape
            esc.escapeTo(cp, out)
        }
        if (quote != null) {
            out.append(quote)
        }
    }

    override fun withExtraEscapes(extras: Map<Char, Escape>, quote: Char?): Escaper =
        this.copy(extraEscapes = extras.mapKeys { it.key.code }, quote = quote)

    override fun withQuote(quote: Char?): Escaper = this.copy(quote = quote)
}

private const val MAX_PSEUDOCODE_LEN = 60
private const val MAX_LINE_LEN = 100

/** Converts a Cfg to mermaid.js.org format for easier debugging */
fun MaximalPaths.toMermaid(root: BlockTree): String = buildString {
    val detail = PseudoCodeDetail(elideFunctionBodies = true)

    val nodeDescription = mutableMapOf<MaximalPathIndex, String>()
    // Figure out how to represent the node: via it's description or code, or as a number placeholder
    for (maximalPath in maximalPaths) {
        val elementDescriptions = maximalPath.elements.map { e ->
            root.dereference(e.ref)?.target?.toPseudoCode(singleLine = false, detail = detail)
                ?.trimEnd()?.let { abbreviate(it, MAX_PSEUDOCODE_LEN) }
                ?: "???"
        }
        val useMultiline = elementDescriptions.any { '\n' in it } ||
            elementDescriptions.fold(0) { a, b -> a + b.length } > MAX_LINE_LEN
        val description = buildString {
            for (i in elementDescriptions.indices) {
                val elementDescription = elementDescriptions[i]
                when {
                    i == 0 -> {} // No separator
                    !useMultiline -> append("; ")
                    elementDescription.endsWith('}') -> append('\n')
                    else -> append(";\n")
                }
                append(elementDescription)
            }
            if (isEmpty()) { append("\u25CB") }
        }

        nodeDescription[maximalPath.pathIndex] = description
    }
    val nodeName = mutableMapOf<MaximalPathIndex, String>()
    // Now, pick the mermaid identifiers for nodes.
    // If we have a name like `IfJoin`, use that.
    // If there are multiple descriptions, add a numeric suffix.
    // Fall back to `N123`.
    val allDescriptions = buildMap {
        nodeDescription.values.forEach {
            this[it] = (this[it] ?: 0) + 1
        }
    }
    val allocated = mutableSetOf<String>()
    for (maximalPath in maximalPaths) {
        val description = nodeDescription[maximalPath.pathIndex]
        var base = "N"
        var needSuffix = true
        val simplerDescription = description
            ?.replace("()", "")
            ?.replace("; ", "_")
        if (simplerDescription != null && isMermaidIdentifier(simplerDescription)) {
            // An ASCII identifier
            base = simplerDescription
            needSuffix = (allDescriptions[base] ?: 0) > 1
        }
        val name = if (!needSuffix && base !in allocated) {
            base
        } else {
            var counter = 0
            val suffixed: String
            while (true) {
                val candidate = "$base$counter"
                if (candidate !in allocated) {
                    suffixed = candidate
                    break
                }
                counter += 1
            }
            suffixed
        }
        nodeName[maximalPath.pathIndex] = name
        allocated.add(name)
    }

    append("flowchart\n")
    for (maximalPath in maximalPaths) {
        val description = nodeDescription.getValue(maximalPath.pathIndex)
        append("  ")
        append(nodeName.getValue(maximalPath.pathIndex))
        append("[")
        MermaidEscaper.default.escapeTo(description, this)
        append("]")
        append('\n')
    }
    for (maximalPath in maximalPaths) {
        val fromName = nodeName.getValue(maximalPath.pathIndex)
        maximalPath.followers.forEachIndexed { followerIndex, follower ->
            val desc = follower.condition?.let {
                when (it) {
                    is MaximalPath.Bubbled -> "bubbled"
                    is MaximalPath.AstElement ->
                        root.dereference(it.ref)?.target?.toPseudoCode(detail = detail) ?: "???"
                }
            }
            val head: String
            val tail: String
            val extraAroundDesc: String
            when (follower.dir) {
                ForwardOrBack.Forward -> {
                    head = "-"
                    tail = "->"
                    extraAroundDesc = "-"
                }
                ForwardOrBack.Back -> {
                    head = "-."
                    tail = ".->"
                    extraAroundDesc = ""
                }
            }
            val toName = when (val toIndex = follower.pathIndex) {
                null -> {
                    val blankName = "$fromName-$followerIndex"
                    append("  ").append(blankName).append("[\"#x2022;\"]\n")
                    blankName
                }
                else -> nodeName.getValue(toIndex)
            }
            append("  ").append(fromName).append(' ').append(head)
            if (desc != null) {
                append(extraAroundDesc)
                MermaidEscaper.default.escapeTo(desc, this)
                append(extraAroundDesc)
            }
            append(tail)
            append(' ')
            append(toName)
            append('\n')
        }
    }
}

private fun isMermaidIdentifier(s: String): Boolean {
    if (s.isEmpty()) { return false }
    for (i in s.indices) {
        when (s[i]) {
            in 'A'..'Z',
            in 'a'..'z',
            '_',
            -> {}
            in '0'..'9' -> if (i == 0) { return false }
            else -> return false
        }
    }
    return true
}

val ForwardOrBack.arrow get() = if (this == ForwardOrBack.Forward) "->" else "<-"

private fun <T> Lazy<T>.toSet() = if (isInitialized()) {
    setOf(value)
} else {
    setOf()
}
