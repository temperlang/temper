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
import lang.temper.common.allIndexed
import lang.temper.common.charCount
import lang.temper.common.compatRemoveFirst
import lang.temper.common.compatRemoveLast
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
    val condition: MaximalPath.Element?,
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
    val elements: List<Element>,
    /** A position to highlight the path in a log sink message. */
    val diagnosticPosition: Position,
    val preceders: List<Preceder>,
    val followers: List<Follower>,
) {
    /**
     * Bundles the position in the block's CFG with the
     * [BlockChildReference] that may be used to get at the relevant edge.
     */
    data class Element(
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
        val pathIndex: MaximalPathIndex,
        val isCondition: Boolean,
    ) : Positioned by ref {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Element && this.ref == other.ref)

        override fun hashCode(): Int = this.ref.hashCode()

        override fun toString(): String = "MaximalPath.Element($ref @ ${ref.pos} in path$pathIndex)"
    }

    val elementsAndConditions: Sequence<Element> get() = sequenceOf(
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
     * Indices of path that exit the control flow graph with a failure.
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
                ),
            ),
            entryPathIndex = MaximalPathIndex(0),
            exitPathIndices = setOf(MaximalPathIndex(0)),
            failExitPathIndices = emptySet(),
        )
    }
}

private class MutPath(
    val index: MaximalPathIndex,
    // Bits set once we've organized everything.
    // These are included in the constructor so that we can ensure they're
    // copied properly.
    var isExit: Boolean,
    var isFailExit: Boolean,
) {
    /** So we can generate a good diagnostic position */
    val positionHints = mutableListOf<Position>()

    val elements = mutableListOf<Either<ControlFlow.Stmt, ControlFlow.Conditional>>()

    /** Things to link to */
    val preceders = mutableListOf<Preceder>()
    val followers = mutableListOf<MutFollower>()

    override fun toString() = "MutPath$index"
}

private class MutFollower(
    val condition: BlockChildReference?,
    val toIndex: MaximalPathIndex,
    val dir: ForwardOrBack,
)

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
     * Calls to the builtin `yield` function are sometimes significant for control
     * flow operations.
     */
    yieldingCallsEndPaths: Boolean = false,
    ignoreConstantConditions: Boolean = false,
    /**
     * When true, act as if every `orelse`'s `or` clause has
     * an implicit jump straight to the `else` clause.
     * This guarantees that `else` clauses are visited.
     */
    assumeFailureCanHappen: Boolean = false,
): MaximalPaths {
    val topControlFLow = when (val flow = root.flow) {
        LinearFlow -> ControlFlow.StmtBlock(
            root.pos,
            root.children.mapIndexed { index, tree ->
                ControlFlow.Stmt(BlockChildReference(index, tree.pos))
            },
        )
        is StructuredFlow -> flow.controlFlow
    }

    fun truthinessOf(ref: BlockChildReference?): Boolean? {
        if (ignoreConstantConditions || ref == null) { return null }
        val tree = root.dereference(ref)?.target
        return tree?.valueContained(TBoolean)
    }

    val mutPaths = mutableListOf<MutPath>()

    fun newPath(): MutPath {
        val p = MutPath(
            MaximalPathIndex(mutPaths.size),
            isExit = false,
            isFailExit = false,
        )
        mutPaths.add(p)
        return p
    }

    fun addFollower(
        from: MutPath,
        to: MutPath,
        condition: BlockChildReference?,
        dir: ForwardOrBack = ForwardOrBack.Forward,
    ) {
        from.followers.add(MutFollower(condition, to.index, dir))
        to.preceders.add(Preceder(from.index, dir))
    }

    fun joinAll(preceders: Set<MutPath>): MutPath? {
        if (preceders.isEmpty()) { return null }
        val joinPath = newPath()
        preceders.forEach {
            addFollower(it, joinPath, null)
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

    val jumpPreceders = mutableMapOf<JumpTarget, MutableSet<MutPath>>()
    fun <T> withJumpPreceders(
        newJumps: List<Pair<JumpTarget, MutableSet<MutPath>>>,
        action: () -> T,
    ): T {
        val replaced = mutableListOf<Pair<JumpTarget, MutableSet<MutPath>?>>()
        for ((jumpTarget, newSet) in newJumps) {
            replaced.add(jumpTarget to jumpPreceders.remove(jumpTarget))
            jumpPreceders[jumpTarget] = newSet
        }
        val result = action()
        for ((jumpTarget, oldSet) in replaced) {
            if (oldSet != null) {
                jumpPreceders[jumpTarget] = oldSet
            } else {
                jumpPreceders.remove(jumpTarget)
            }
        }
        return result
    }

    // For each orClause being processed, the preceders for the else clause.
    // The zero-th item are the free bubbles.
    val orClauseFailOverStack = mutableListOf(mutableSetOf<MutPath>())

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
                        addFollower(inPath, intoElse, null)
                        buildPaths(controlFlow.thenClause, setOf(intoThen)) +
                            buildPaths(controlFlow.elseClause, setOf(intoElse))
                    }
                    else -> {
                        inPath.elements.add(Either.Right(controlFlow))
                        buildPaths(
                            if (truthiness) { controlFlow.thenClause } else { controlFlow.elseClause },
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
                val afterLoopPreceders = mutableSetOf<MutPath>()
                val beforeIncrementPreceders = mutableSetOf<MutPath>()
                val errorJumps = mutableSetOf<MutPath>()

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
                            afterLoopPreceders.add(loopStart)
                        }
                        afterCond
                    }
                    LeftOrRight.Right -> loopStart
                }

                val afterBody = withJumpPreceders(
                    specifiers.map { JumpTarget(BreakOrContinue.Break, it) to afterLoopPreceders } +
                        specifiers.map { JumpTarget(BreakOrContinue.Continue, it) to beforeIncrementPreceders },
                ) {
                    buildPaths(controlFlow.body, setOf(beforeBody))
                }
                val beforeIncrement = maybeJoinAll(afterBody + beforeIncrementPreceders)
                val afterIncrement = if (beforeIncrement != null) {
                    withJumpPreceders(
                        specifiers.map { JumpTarget(BreakOrContinue.Break, it) to afterLoopPreceders } +
                            specifiers.map { JumpTarget(BreakOrContinue.Continue, it) to errorJumps },
                    ) {
                        buildPaths(controlFlow.increment, setOf(beforeIncrement))
                    }
                } else {
                    emptySet()
                }

                val beforeContinue = maybeJoinAll(afterIncrement)
                if (beforeContinue != null) {
                    when (checkPosition) {
                        LeftOrRight.Left -> {
                            addFollower(beforeContinue, loopStart, null, ForwardOrBack.Back)
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
                                addFollower(continuePath, loopStart, null, ForwardOrBack.Back)
                            }
                            if (conditionTruthiness != true) {
                                afterLoopPreceders.add(beforeContinue)
                            }
                        }
                    }
                }
                if (errorJumps.isNotEmpty()) {
                    TODO("do something with errorJumps")
                }
                afterLoopPreceders.toSet()
            }
        }
        is ControlFlow.Jump -> {
            val target = JumpTarget(controlFlow.jumpKind, controlFlow.target)
            val precedersForTarget: MutableSet<MutPath>? = jumpPreceders[target]
            val rightOfJump = controlFlow.pos.rightEdge
            preceders.forEach { it.positionHints.add(rightOfJump) }
            precedersForTarget?.addAll(preceders)
            setOf()
        }
        is ControlFlow.Labeled -> {
            val afterLabeled = mutableSetOf<MutPath>()
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
            val afterStmts = withJumpPreceders(targets) {
                buildPaths(controlFlow.stmts, preceders)
            }
            afterLabeled + afterStmts
        }
        is ControlFlow.OrElse -> {
            val startOfElse = mutableSetOf<MutPath>()
            orClauseFailOverStack.add(startOfElse)
            val beforeThen = maybeJoinAll(preceders)
            val afterThen = withJumpPreceders(
                listOf(
                    JumpTarget(BreakOrContinue.Break, NamedJumpSpecifier(controlFlow.orClause.breakLabel))
                        to startOfElse,
                ),
            ) {
                buildPaths(controlFlow.orClause.stmts, setOfNotNull(beforeThen))
            }

            val beforeElse = orClauseFailOverStack.compatRemoveLast() // === startOfElse
            if (beforeElse.isEmpty() && assumeFailureCanHappen) {
                // Some passes use maximal paths to traverse things in a sensible order,
                // and assumeFailureCanHappen allows those passes to reach everything.
                if (beforeThen != null) {
                    val beforeElsePath = newPath()
                    addFollower(beforeThen, beforeElsePath, null)
                    beforeElse.add(beforeElsePath)
                }
            }

            val afterElse = if (beforeElse.isNotEmpty()) {
                buildPaths(controlFlow.elseClause, beforeElse.toSet())
            } else {
                emptySet()
            }
            afterThen + afterElse
        }
        is ControlFlow.Stmt -> {
            val tree = root.dereference(controlFlow.ref)?.target
            var path = maybeJoinAll(preceders)
            if (tree != null && isBubbleCall(tree)) {
                val failOvers = orClauseFailOverStack.last()
                path?.let {
                    it.positionHints.add(tree.pos.leftEdge)
                    it.positionHints.add(tree.pos.rightEdge)
                    failOvers.add(it)
                }
                path = null
            } else {
                path?.elements?.add(Either.Left(controlFlow))
                if (yieldingCallsEndPaths) {
                    val yieldingCallDetails = disassembleYieldingCall(controlFlow, root)
                    if (yieldingCallDetails != null) {
                        check(tree != null) // Couldn't have disassembled it otherwise
                        val followsYield = newPath()
                        followsYield.positionHints.add(tree.pos.rightEdge)
                        if (path != null) {
                            addFollower(path, followsYield, null)
                        }
                        path = followsYield
                    }
                }
            }
            setOfNotNull(path)
        }
        is ControlFlow.StmtBlock -> {
            var before = preceders
            for (stmt in controlFlow.stmts) {
                if (preceders.isEmpty()) { break }
                before = buildPaths(stmt, before)
            }
            before
        }
    }
    val entryPath = newPath()
    val entryPathIndex = entryPath.index
    check(entryPathIndex == MaximalPaths.zeroValue.entryPathIndex) // Use zero
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

    val exitPathIndices = setOfNotNull(
        // We joined above, so there's at most one
        mutPaths.firstOrNull { it.isExit }?.index,
    )
    val failExitPathIndices = setOfNotNull(
        // We joined above, so there's at most one
        mutPaths.firstOrNull { it.isFailExit }?.index,
    )

    val maximalPaths = mutPaths.map { mutPath ->
        val pathIndex = mutPath.index
        val elements = mutPath.elements.map { e ->
            when (e) {
                is Either.Left ->
                    MaximalPath.Element(e.item.ref, e.item, mutPath.index, isCondition = false)
                is Either.Right ->
                    MaximalPath.Element(e.item.condition, null, mutPath.index, isCondition = true)
            }
        }
        val preceders = mutPath.preceders.toList()
        val followers = mutPath.followers.map { mutFollower ->
            Follower(
                mutFollower.condition?.let {
                    MaximalPath.Element(it, null, pathIndex, isCondition = true)
                },
                mutFollower.toIndex,
                mutFollower.dir,
            )
        }
        val positioned = buildList {
            mutPath.positionHints.mapTo(this) { Either.Left(it) }
            elements.mapTo(this) { Either.Right(it) }
            followers.mapNotNullTo(this) { follower ->
                follower.condition?.let { Either.Right(it) }
            }
        }
        val diagnosticPosition = diagnosticPositionForPathContents(positioned, root)
        MaximalPath(
            pathIndex = pathIndex,
            elements = elements,
            diagnosticPosition = diagnosticPosition,
            preceders = preceders,
            followers = followers,
        )
    }

    return MaximalPaths(
        maximalPaths = maximalPaths,
        entryPathIndex = entryPathIndex,
        exitPathIndices = exitPathIndices,
        failExitPathIndices = failExitPathIndices,
    )
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
    check(mutPaths.allIndexed { i, mutPath -> i == mutPath.index.index })

    while (true) {
        // The way we allocate a MutPath for each branch in ControlFlow.If above
        // means that sometimes we have a case where one branch leads to one other
        // and is the only one that leads to it.
        val includeInto = mutableMapOf<MaximalPathIndex, MaximalPathIndex>()
        for (path in mutPaths) {
            // Before: ... -> A -> B -> ...
            // After:  ... -> AB -> ...
            // If something is preceded by one branch, that only flows into it, collapse
            // them regardless of their content
            if (path.preceders.size == 1) {
                val precederIndex = path.preceders.first().pathIndex
                if (precederIndex != path.index) {
                    // Not a self-loop
                    val precederPath = mutPaths[precederIndex.index]
                    if (
                        precederPath.followers.size == 1 &&
                        (!yieldingCallsEndPaths || !endsYielding(precederPath, root))
                    ) {
                        val follower = precederPath.followers.first()
                        if (follower.condition == null && follower.dir == ForwardOrBack.Forward) {
                            check(follower.toIndex == path.index)
                            val kept = path.index
                            val eliminated = follower.toIndex
                            if (eliminated !in includeInto && kept !in includeInto) {
                                includeInto[eliminated] = kept
                                continue
                            }
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
                    val followerPathIndex = follower.toIndex
                    if (path.index !in includeInto) { // Avoid the above clobbering this
                        // Not handled above so not a simple continuation
                        val kept = path.index
                        val eliminated = followerPathIndex
                        if (eliminated !in includeInto) {
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
        val cycleSet = mutableSetOf<MaximalPathIndex>()
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

        val pathIndexRemapping = buildMap {
            for (path in mutPaths) {
                val includedInto = includeInto[path.index]
                if (includedInto == null) {
                    this[path.index] = MaximalPathIndex(this.size)
                }
            }
            // Now, remap eliminated items to the path that they are eventually included into
            for ((eliminated, into) in includeInto) {
                var target = into
                // Inclusion can be transitive.  A may be included in B which is included in C.
                // Only C was allocated an index in the index-from-size loop above
                while (target in includeInto) {
                    target = includeInto.getValue(target)
                }
                this[eliminated] = this.getValue(target)
            }
        }
        fun remap(i: MaximalPathIndex) = pathIndexRemapping.getValue(i)

        val includedFrom = includeInto.inverse()

        val rebuilt = buildList {
            for (path in mutPaths) {
                val index = path.index
                if (index in includeInto) {
                    // It is flattened when the loop reached the eventual destination
                    continue
                }
                val parts = buildList {
                    var inclusionIndex = path.index
                    while (true) {
                        add(mutPaths[inclusionIndex.index])
                        inclusionIndex = includedFrom[inclusionIndex] ?: break
                    }
                }

                var isExit = false
                var isFailExit = false
                for (part in parts) {
                    isExit = isExit || part.isExit
                    isFailExit = isFailExit || part.isFailExit
                }
                val remappedPath = MutPath(remap(index), isExit = isExit, isFailExit = isFailExit)

                for (i in parts.indices) {
                    val part = parts[i]
                    val prevPathIndex = parts.getOrNull(i - 1)?.index
                    val nextPathIndex = parts.getOrNull(i + 1)?.index

                    part.preceders.forEach { p ->
                        if (p.pathIndex != prevPathIndex) {
                            remappedPath.preceders.add(
                                Preceder(remap(p.pathIndex), p.dir),
                            )
                        }
                    }
                    part.followers.forEach { f ->
                        if (f.toIndex != nextPathIndex) {
                            remappedPath.followers.add(
                                MutFollower(f.condition, remap(f.toIndex), f.dir),
                            )
                        }
                    }
                }

                for (part in parts) {
                    remappedPath.elements.addAll(part.elements)
                    remappedPath.positionHints.addAll(part.positionHints)
                }

                // Double check that the identity between index integer value
                // and offset in the list still holds.
                // (See the check at top of this function)
                check(this.size == remappedPath.index.index)
                add(remappedPath)
            }
        }
        check(mutPaths.size > rebuilt.size) // Monotonic so the `while (true)` terminates
        mutPaths.clear()
        mutPaths.addAll(rebuilt)
    }
}

/**
 * Dump a representation of the paths to the given [console] if its nonnull
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
                                        append(renderReference(f.condition.ref))
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
        null -> null
        is Either.Left -> last.item.ref
        is Either.Right -> last.item.ref
    }
    return ref != null && ref.yieldingCallKind(root) != null
}

fun diagnosticPositionForPathContents(
    /**
     * Either zero-width positions that mark start of blocks,
     * or an actual code element.
     */
    positioned: List<Either<Position, MaximalPath.Element>>,
    root: BlockTree,
): Position {
    // A lot of positions are synthesized declarations, especially in the entry segment.
    // Partition the elements into probably-synthetic and probably not synthetic.
    // If we have some of the latter, use them to find a diagnostic position that's
    // used in to generate excerpts of code in error messages.
    val positionedGrouped =
        mutableMapOf<NonsenseGradient, MutableList<Positioned>>()
    for (p in positioned) {
        val (nonsensity, positionedElement) =
            positionAndConfidenceForBasicBlockPositionPart(p, root)
        positionedGrouped.putMultiList(nonsensity, positionedElement)
    }

    var minimalNonsenseGroup = emptyList<Positioned>()
    for (ng in NonsenseGradient.values().reversed()) {
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
    e: Either<Position, MaximalPath.Element>,
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
    // If there are multiple, suffix with a number.
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
                root.dereference(it.ref)?.target?.toPseudoCode(detail = detail) ?: "???"
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
