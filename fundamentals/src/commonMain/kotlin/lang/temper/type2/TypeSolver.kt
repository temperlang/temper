package lang.temper.type2

import lang.temper.common.Either
import lang.temper.common.ForwardOrBack
import lang.temper.common.KBitSet
import lang.temper.common.KBitSetHelpers.contains
import lang.temper.common.LeftOrRight
import lang.temper.common.clearBitIndices
import lang.temper.common.console
import lang.temper.common.isNotEmpty
import lang.temper.common.joinedIterable
import lang.temper.common.putMultiSet
import lang.temper.common.soleElementOrNull
import lang.temper.common.subListToEnd
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.join
import lang.temper.type.Abstractness
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.WellKnownTypes.invalidTypeDefinition
import lang.temper.type.WellKnownTypes.neverTypeDefinition
import lang.temper.type.WellKnownTypes.resultTypeDefinition
import lang.temper.type.WellKnownTypes.voidTypeDefinition
import lang.temper.type2.Nullity.NonNull
import lang.temper.type2.Nullity.OrNull
import lang.temper.value.IdentityActualOrder
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TClosureRecord
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TMap
import lang.temper.value.TMapBuilder
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.TVoid
import lang.temper.value.TypeTag
import lang.temper.value.Value
import lang.temper.value.applicationOrderForActuals
import kotlin.math.max
import kotlin.math.min

private const val DEBUG = false
private inline fun debug(message: () -> String) {
    if (DEBUG) {
        console.log(message())
    }
}

private inline fun <T> debug(message: () -> String, body: () -> T): T =
    if (DEBUG) {
        console.group(message()) {
            body()
        }
    } else {
        body()
    }

/**
 * Each TypeSolver keeps a 64b counter which lets us avoid expensive rechecking of nodes
 * by comparing a last-checked stamp to modified stamps.
 *
 * - [TypeSolver.SolveNode.solvedAtStamp]
 * - [TypeSolver.TypeNode.lastCheckStamp]
 * - [MutBoundSet.lastChangedStamp]
 */
private typealias ChangeStamp = Long

class TypeSolver(
    private val solverVarNamer: SolverVarNamer = SolverVarNamer.new(),
    private val typeContext: TypeContext2 = TypeContext2(),
    private val debugHook: TypeSolverDebugHook? = null,
) {
    operator fun get(v: TypeVar): TypeSolution? = (nodes[v] as? TypeNode)?.choice
    operator fun get(v: Solvable): Solution? = nodes[v]?.choice

    /** All solutions to solver variables. This is useful for debugging but use [get] for most things. */
    val allSolutions: Map<SolverVar, Solution> get() = buildMap {
        for ((k, v) in nodes) {
            val solution = v.choice
            if (k is SolverVar && solution != null) {
                this[k] = solution
            }
        }
    }

    val allConstraintsForDebug: Iterable<TokenSerializable> get() = constraints.toList()

    // Internally, the solver builds a graph where nodes are type variables
    // and edges are bounds.
    //
    // When we get a solution or partial solution to a node, we can propagate
    // along edges to nearby nodes.
    private val nodes = mutableMapOf<Solvable, SolveNode>()
    private sealed class SolveNode {
        abstract val bound: Solvable
        abstract val choice: Solution?
        var solvedAtStamp: ChangeStamp = -1
    }
    private class SimpleNode(
        override val bound: SimpleVar,
    ) : SolveNode() {
        override var choice: Solution? = null

        override fun toString(): String = buildString {
            append("(SimpleNode ")
            append(bound)
            if (choice != null) {
                append(" choice=").append(choice)
            }
            append(")")
        }
    }

    private class TypeNode(
        override val bound: TypeBoundary,
    ) : SolveNode() {
        var typeVarsUsed = when (bound) {
            is PartialType -> bound.typeVarsUsed()
            is TypeVarRef -> setOf(bound.typeVar)
            is Type2, is TypeVar, is ValueBound -> emptySet()
        }

        /**
         * The type, if any, that we have fixed this node to
         * by choosing something between its lower and upper bounds.
         */
        override var choice: TypeSolution? = null

        var lastCheckStamp: ChangeStamp = -1

        /** Bounds that are fully realized. */
        val fullBounds = BoundSet<Type2>()

        /**
         * Bounds for types that need to be solved.
         * When adding to the partial bounds, we ensure there is a [TypeConstraint]
         * between this's [bound] and the partial type bound, so that this node gets
         * [checked][checkNode] when any of [TypeVar] in the [PartialType] is chosen.
         */
        val partialBounds = BoundSet<PartialType>()

        var boundChecked = false

        override fun toString(): String = buildString {
            append("(TypeNode ")
            append(bound)
            if (choice != null) {
                append(" choice=").append(choice)
            } else {
                if (fullBounds.isNotEmpty()) {
                    append(" ").append(fullBounds)
                }
                if (partialBounds.isNotEmpty()) {
                    append(" ").append(partialBounds)
                }
            }
            append(")")
        }
    }
    private fun node(b: Solvable): SolveNode = nodes.getOrPut(b) {
        debug { "Creating node for $b : ${b::class.simpleName}" }
        val newNode = when (b) {
            is TypeBoundary -> TypeNode(b)
            is SimpleVar -> SimpleNode(b)
        }
        if (b is TypeSolution && newNode is TypeNode) {
            newNode.choice = b
        }
        newNode
    }
    private fun node(b: TypeBoundary): TypeNode = node(b as Solvable) as TypeNode
    private fun node(b: SimpleVar): SimpleNode = node(b as Solvable) as SimpleNode

    private var changeStampCounter = -1L
    private fun nextChangeStamp(): ChangeStamp = ++changeStampCounter

    /**
     * For each node which doesn't have a solution, [SolveNode.choice] is null,
     * we have an adjacency table entry for all bounds that are adjacent to it.
     * Those which mention a type variable in it.
     * The variable might be the node's bound, or if the bound is complex like
     * a partial type, it might be mentioned in one of its type parameters.
     */
    private val adjacencyTable = mutableMapOf<Solvable, MutableSet<Constraint>>()

    /**
     * Constraints that need rechecking either because:
     *
     * - they are new OR
     * - one of their adjacent bounds got better info that needs to be
     *   propagated across the constraint
     */
    private val dirtyConstraints = mutableSetOf<Constraint>()

    /**
     * Keep track of which bounds got new info so we can mark dirty their adjacent constraints.
     */
    private val boundsChanged = mutableSetOf<TypeBoundary>()

    /**
     * When the solver realizes it has enough information to solve a group
     * of nodes, but could still receive tighter bounds, the nodes' keys are
     * added here.
     *
     * This can happen, for example, when:
     *
     * - a node has a complete upper or lower bound but not both:
     *   it is a *Listed<T>*, but maybe
     * - a node has tight bounds that are just missing nullity: is it String or String?
     */
    private val satisfiedButNotSolved = mutableSetOf<TypeBoundary>()

    // Set up constraints
    fun assignable(left: TypeBoundary, right: TypeBoundary) {
        addConstraint(SubTypeConstraint(right, left))
    }
    fun sameAs(a: TypeBoundary, b: TypeBoundary) {
        addConstraint(SameTypeConstraint(a, b))
    }
    fun relatesTo(a: TypeBoundary, b: TypeBoundary) {
        addConstraint(BivariantConstraint(a, b))
    }
    fun called(
        /**
         * The possible callees.
         * These will be filtered as information about the types of arguments becomes available.
         */
        callees: List<Callee>,
        /**
         * A variable that resolves to a solution that indicates the callee chosen.
         * May be omitted when the choice is not needed, as is often the case for unoverloaded callees.
         */
        calleeChoice: SimpleVar,
        /**
         * Any explicit type arguments.
         * `f<T>(x)` has an explicit argument `<T>`, unlike `f(x)`.
         */
        explicitTypeArgs: List<Type2>?,
        /**
         * Vars that should solve to individual type parameter actuals.
         * This allows relating reified type bounds to type parameters.
         */
        typeArgVars: List<TypeVar>?,
        /**
         * Type variables for the types of the actual arguments in order.
         */
        args: List<TypeVar>,
        /**
         * True if the last [arg][args] is a block lambda that might need reordering.
         */
        hasTrailingBlock: Boolean,
        /**
         * A variable that resolves to a list of type actuals, as a [TypeListSolution],
         * bindings for the chosen callee's type parameters.
         *
         * If the callee is `let f<T, U>(t: T, u: U): Void {...}` and the actual type of the expression
         * passed for `t` resolved to `String`, and the actual type of the expression passed for `u`
         * resolved to `Int` then the typeActuals variable would resolve to [String, Int].
         */
        typeActuals: SimpleVar?,
        /**
         * Type variable that resolves to the type of the call expression.
         *
         * If the chosen callee's (pre-contextualized) return type is a *Result* type,
         * the pass variable resolves to that result's first type parameter contextualized by the type
         * actuals.
         * Otherwise, the pass variable resolves to the contextualized return type.
         */
        callPass: TypeVar,
        /**
         * Type variable that resolves to a [TypeListSolution] of failure modes from the *Result* type,
         * or the empty list only if the pre-contextualized return type of the callee is not a *Result*
         * type.
         */
        callFail: SimpleVar?,
    ) {
        addConstraint(
            CallConstraint(
                callees = callees,
                calleeChoice = calleeChoice,
                explicitTypeArgs = explicitTypeArgs,
                typeArgVars = typeArgVars,
                args = args,
                hasTrailingBlock = hasTrailingBlock,
                typeActuals = typeActuals,
                callPass = callPass,
                callFail = callFail,
            ),
        )
    }

    private val constraints = mutableSetOf<Constraint>()
    private fun addConstraint(constraint: Constraint) {
        if (constraint !in constraints) {
            if (constraint is UsesConstraint && constraint.typeVars.isEmpty()) {
                return
            }
            debug { "Adding constraint $constraint" }
            constraints.add(constraint)
            dirtyConstraints.add(constraint)
            for (bound in constraint.bounds()) {
                val node = node(bound)
                if (node.choice == null) {
                    adjacencyTable.putMultiSet(bound, constraint)
                }
                if (bound is TypeLike) {
                    addConstraint(UsesConstraint(bound))
                }
            }
        }
    }

    private var progressMade = false
    fun solve() {
        debugHook?.begin()
        debug { "Start solve" }
        while (true) {
            progressMade = false

            maybeDumpToDebugHook()
            runSolveLoop()

            if (!progressMade && satisfiedButNotSolved.isNotEmpty()) {
                debug({ "Forcing choices" }) {
                    forceChoice()
                }
            }
            if (!progressMade) {
                break
            }

            val boundsChanged = this.boundsChanged.toList()
            this.boundsChanged.clear()
            for (bc in boundsChanged) {
                adjacencyTable[bc]?.let { adjacentConstraints ->
                    dirtyConstraints.addAll(adjacentConstraints)
                }
            }
        }
        for (node in nodes.values) {
            if (node.choice == null) {
                debug { "Unsolvable $node" }
                // TODO: store a message about why it's unsolvable.
                // If we need a partial type, but still need something
                // blame the failure of that variable and extract a position from it.
                when (node) {
                    is TypeNode -> choose(node, Unsolvable)
                    is SimpleNode -> choose(node, Unsolvable)
                }
            }
        }
        debug({ "End solve" }) {
            nodes.values.forEach { node ->
                debug { "${node.bound} -> ${node.choice}" }
            }
        }

        maybeDumpToDebugHook()
        debugHook?.end()
    }

    private fun runSolveLoop(): Unit = debug({ "Run solve loop" }) {
        debug({ "nodes" }) {
            nodes.forEach { (k, v) ->
                val adjacent = adjacencyTable[k] ?: emptySet()
                val adj = if (adjacent.isEmpty()) "" else " adjacent to:"
                debug({ "$k -> $v$adj" }) {
                    adjacent.forEach {
                        debug { "- $it" }
                    }
                }
            }
        }
        debug({ "constraints" }) {
            constraints.forEach {
                debug { "${if (it in dirtyConstraints) "*" else ""}$it" }
            }
        }

        val constraints = dirtyConstraints.toList()
        dirtyConstraints.clear()

        for (constraint in constraints) {
            debug({ "Processing $constraint" }) {
                val bounds = constraint.bounds()
                debug({ "Checking bounds" }) {
                    for (b in bounds) {
                        if (b is TypeBoundary) {
                            checkNode(node(b))
                        }
                    }
                }
                when (constraint) {
                    is TypeConstraint -> processTypeConstraint(constraint)
                    is CallConstraint -> processCallConstraint(constraint)
                    is UsesConstraint -> processUsesConstraint(constraint)
                    is PutConstraint -> processPutConstraint(constraint)
                }
                debug({ "Rechecking bounds" }) {
                    for (b in bounds) {
                        if (b is TypeBoundary) {
                            checkNode(node(b))
                        }
                    }
                }
            }
        }
        debug { "progressMade -> $progressMade" }
        debugHook?.endStep()
    }

    private fun forceChoice() {
        debug({ "satisfied" }) {
            satisfiedButNotSolved.forEach {
                debug { "- $it -> ${nodes[it]}" }
            }
        }

        // Now we need to figure out which group we're going to use.
        //
        // Our common bounds should be empty, but give preference for completeness.
        // Normally, we prefer lower bounds to upper bounds as more specificity is usually better.
        // But there's a wrinkle: Never<X> is a valid lower bound in many scenarios, but X is often
        // preferable.
        //
        // If we have upper bounds but only Never lower bounds, delay it.
        // If we have non-Never lower bounds, just use those.
        // If we have only Never lower bounds, force those.
        // To handle that, we count the lower bounds into those two buckets.
        val withCommon = mutableListOf<TypeNode>()
        val withLowerEarly = mutableListOf<TypeNode>()
        val withLowerLate = mutableListOf<TypeNode>()
        val withUpper = mutableListOf<TypeNode>()

        // The act of choosing farther down removes from satisfiedButNotSolved
        for (b in satisfiedButNotSolved) {
            val node = node(b)
            val fullBounds = node.fullBounds
            val lower = fullBounds.lowerBounds
            val common = fullBounds.commonBounds
            val upper = fullBounds.upperBounds
            if (common.isNotEmpty()) {
                withCommon.add(node)
            }
            if (lower.isNotEmpty()) {
                // Doing lower before upper means that if there is a
                // lower bound like Foo and an upper bound like Never<Foo>
                // we pick Foo.

                if (
                    lower.any {
                        it.definition != neverTypeDefinition ||
                            // True if it has a corresponding upper bound.
                            (it.bindings.size == 1 && it.bindings[0] in upper)
                    }
                ) {
                    withLowerEarly.add(node)
                }
                if (lower.any { it.definition == neverTypeDefinition }) {
                    withLowerLate.add(node)
                }
            }
            if (upper.isNotEmpty()) {
                withUpper.add(node)
            }
        }

        fun combineUpperBounds(tn: TypeNode): TypeSolution? {
            // Pick the lowest upper bound
            var upperBounds: Collection<Type2> = tn.fullBounds.upperBounds
            val commonBounds = tn.partialBounds.commonBounds
            // The common bounds may tell us which class to project to.
            if (commonBounds.isNotEmpty()) {
                val defn = commonBounds.first().definition
                // TODO: alternatively, project sub types to super types where
                // possible
                upperBounds = upperBounds.filter {
                    it.definition == defn
                }
            }
            if (upperBounds.isEmpty()) {
                return null
            }
            return upperBounds.fold(upperBounds.first()) { t, u ->
                if (typeContext.isSubType(u, t)) { u } else { t }
            }
        }

        fun combineLowerBounds(tn: TypeNode, unprocessedLowerBounds: List<Type2>): TypeSolution? {
            val commonBound = tn.partialBounds.commonBounds.firstOrNull()
            val (nullity, lowerBounds) = if (commonBound != null) {
                // If the definition is fixed by a partial common bound, as is the case when
                // the node's boundary is a partial type, then project the solutions to that.
                val defn = commonBound.definition
                val lowerBoundsProjected = unprocessedLowerBounds.mapNotNull { lb ->
                    if (lb.definition == defn) {
                        lb
                    } else {
                        val supers = typeContext.superTypeTreeOf(lb)
                        supers[defn].firstOrNull()
                    }
                }
                commonBound.nullity to lowerBoundsProjected
            } else {
                val nullity = if (
                    tn.fullBounds.lowerBounds.any { it.nullity == OrNull } ||
                    tn.partialBounds.lowerBounds.any { it.nullity == OrNull }
                ) {
                    OrNull
                } else {
                    NonNull
                }
                nullity to unprocessedLowerBounds
            }

            if (lowerBounds.isEmpty()) {
                return null
            }

            val lowerBoundsNotNull = lowerBounds.map { it.withNullity(NonNull) }
            val smallBoundListNonNull =
                typeContext.leastCommonSuperTypes(lowerBoundsNotNull).let {
                    if (it.isNotEmpty()) {
                        it
                    } else {
                        lowerBoundsNotNull
                    }
                }
            val superTypeNonNull = if (smallBoundListNonNull.size == 1) {
                smallBoundListNonNull.first()
            } else {
                // Prefer class types to interface types
                smallBoundListNonNull.firstOrNull {
                    val def = it.definition
                    def is TypeShape && def.abstractness == Abstractness.Concrete
                }
                    ?: smallBoundListNonNull.firstOrNull()
            }
            return superTypeNonNull?.withNullity(nullity) ?: Unsolvable
        }

        fun maybeChooseAll(choices: Iterable<Pair<TypeNode, TypeSolution?>>) {
            for ((tn, choice) in choices) {
                if (tn.choice == null && choice != null) {
                    choose(tn, choice)
                }
            }
        }

        if (!progressMade && withCommon.isEmpty()) {
            val choices = withCommon.map { tn ->
                tn to tn.fullBounds.commonBounds.first()
            }
            maybeChooseAll(choices)
        }

        if (!progressMade && withLowerEarly.isNotEmpty()) {
            val choices = withLowerEarly.map { tn ->
                val lowerBounds = tn.fullBounds.lowerBounds.mapNotNull {
                    if (it.definition != neverTypeDefinition) {
                        it
                    } else if (it.bindings.size == 1 && it.bindings[0] in tn.fullBounds.upperBounds) {
                        it.bindings[0]
                    } else {
                        null
                    }
                }
                tn to combineLowerBounds(tn, lowerBounds)
            }
            maybeChooseAll(choices)
        }

        if (!progressMade && withUpper.isNotEmpty()) {
            val choices = withUpper.map { tn -> tn to combineUpperBounds(tn) }
            maybeChooseAll(choices)
        }

        if (!progressMade && withLowerLate.isNotEmpty()) {
            val choices = withLowerLate.map { tn ->
                val lowerBounds = tn.fullBounds.lowerBounds.map {
                    stripNever(it)
                }
                tn to when (val lowerBound = combineLowerBounds(tn, lowerBounds)) {
                    is Type2 -> MkType2(neverTypeDefinition)
                        .actuals(listOf(lowerBound))
                        .get()

                    is Unsolvable? -> lowerBound
                }
            }
            maybeChooseAll(choices)
        }
    }

    private fun processTypeConstraint(cons: TypeConstraint) {
        val isBidirectional = when (cons) {
            is SubTypeConstraint -> false
            is SameTypeConstraint -> true
            is BivariantConstraint -> return processBivariantConstraint(cons)
        }

        val (sub, sup) = cons
        val subNode = node(sub)
        val supNode = node(sup)
        if (subNode.choice != null && supNode.choice != null) {
            return
        }
        debug { "subNode=$subNode\nsupNode=$supNode" }

        // Move bounds from one node to another
        val subChoice = subNode.choice as? Type2
        val supChoice = supNode.choice as? Type2

        val subFull = subNode.fullBounds
        val subPartial = subNode.partialBounds
        val supFull = supNode.fullBounds
        val supPartial = supNode.partialBounds

        // Move bounds from subNode to supNode
        if (supChoice == null) {
            if (subChoice != null) {
                val kind = if (isBidirectional) BoundKind.Common else BoundKind.Lower
                addBound(subChoice, supNode, kind)
            } else if (isBidirectional) {
                copyBounds(subFull, to = supNode)
                copyBounds(subPartial, to = supNode)
            } else {
                // When sub <: sup, we know that sub's lower bounds and common bounds are lower bounds of sup.
                copyBoundsAs(subFull, to = supNode, BoundKind.Lower) { _, k ->
                    k != BoundKind.Upper
                }
                copyBoundsAs(subPartial, to = supNode, BoundKind.Lower) { _, k ->
                    k != BoundKind.Upper
                }
            }
        }

        // Move bounds from supNode to subNode's uppers
        if (subChoice == null) {
            if (supChoice != null) {
                val kind = if (isBidirectional) BoundKind.Common else BoundKind.Upper
                addBound(supChoice, subNode, kind)
            } else if (isBidirectional) {
                copyBounds(supFull, to = subNode)
                copyBounds(supPartial, to = subNode)
            } else {
                copyBoundsAs(supFull, to = subNode, BoundKind.Upper) { _, k ->
                    k != BoundKind.Lower
                }
                copyBoundsAs(supPartial, to = subNode, BoundKind.Upper) { _, k ->
                    k != BoundKind.Lower
                }
            }
        }
    }

    private val bivariantsSolved = mutableSetOf<BivariantConstraint>()
    private fun processBivariantConstraint(cons: BivariantConstraint) {
        if (cons in bivariantsSolved) { return }

        val (a, b) = cons
        val aNode = node(a)
        val bNode = node(b)

        val aChoice = aNode.choice
        val bChoice = bNode.choice
        if (aChoice != null && bChoice != null) {
            // They are already solved.
            return
        }

        // Look at (lower, common) bounds of each and compare to (common, upper) bounds of the other.
        // If we can find TypeShapes, we can then use that to convert the constraint into a subtype
        // constraint that can then do the real solving work.

        // Below, we collect sets of TypeShapes so that we can then look for TypeShape pairs (C, D)
        // with relationships like the below, or the same relationship but with (a, b) swapped
        // in the diagram and the implication.

        //   C
        //   ↓
        //   a
        //   ↓
        //   b
        //   ↓
        //   D
        //
        // a <: C<*>  &&  D<*> <: b  &&  D<*> extends C<*>   ->   b <: a

        val aLowerAndCommonTypeShapes = mutableSetOf<TypeShape>()
        val aCommonAndUpperTypeShapes = mutableSetOf<TypeShape>()
        val bLowerAndCommonTypeShapes = mutableSetOf<TypeShape>()
        val bCommonAndUpperTypeShapes = mutableSetOf<TypeShape>()

        fun addTypeShapesFromBounds(typeFormal: TypeFormal, out: MutableSet<TypeShape>) {
            for (b in typeFormal.upperBounds) {
                when (val d = b.definition) {
                    is TypeShape -> out.add(d)
                    is TypeFormal -> addTypeShapesFromBounds(d, out)
                }
            }
        }

        for ((node, lowerAndCommon, commonAndUpper) in listOf(
            Triple(aNode, aLowerAndCommonTypeShapes, aCommonAndUpperTypeShapes),
            Triple(bNode, bLowerAndCommonTypeShapes, bCommonAndUpperTypeShapes),
        )) {
            val choice = node.choice
            if (choice is Type2) {
                when (val d = choice.definition) {
                    is TypeShape -> {
                        lowerAndCommon.add(d)
                        commonAndUpper.add(d)
                    }

                    is TypeFormal -> addTypeShapesFromBounds(d, commonAndUpper)
                }
            } else {
                for (boundSet in listOf(node.partialBounds, node.fullBounds)) {
                    for ((bound, kind) in boundSet) {
                        when (val d = bound.definition) {
                            is TypeShape -> {
                                if (kind != BoundKind.Lower) { commonAndUpper.add(d) }
                                if (kind != BoundKind.Upper) { lowerAndCommon.add(d) }
                            }
                            is TypeFormal -> if (kind != BoundKind.Lower) {
                                addTypeShapesFromBounds(d, commonAndUpper)
                            }
                        }
                    }
                }
            }
        }

        // Consider case where a <: b
        var aIsSubTypeOfB = false
        //   C
        //   ↓
        //   b
        //   ↓
        //   a
        //   ↓
        //   D
        subTypeLoop@
        for (d in aLowerAndCommonTypeShapes) {
            for (c in bCommonAndUpperTypeShapes) {
                val path = typeContext.extendsPath(d, c)
                if (path != null) {
                    aIsSubTypeOfB = true
                    break@subTypeLoop
                }
            }
        }

        // Consider case where b <: a
        //   C
        //   ↓
        //   a
        //   ↓
        //   b
        //   ↓
        //   D
        var bIsSubTypeOfA = false
        subTypeLoop@
        for (c in aCommonAndUpperTypeShapes) {
            for (d in bLowerAndCommonTypeShapes) {
                val path = typeContext.extendsPath(d, c)
                if (path != null) {
                    bIsSubTypeOfA = true
                    break@subTypeLoop
                }
            }
        }

        val newConstraint = if (aIsSubTypeOfB) {
            if (bIsSubTypeOfA) {
                SameTypeConstraint(a, b)
            } else {
                SubTypeConstraint(a, b)
            }
        } else if (bIsSubTypeOfA) {
            SubTypeConstraint(b, a)
        } else {
            null
        }
        if (newConstraint != null) {
            addConstraint(newConstraint)
            bivariantsSolved.add(cons)
        }
    }

    private class CallConstraintState(private val nVariants: Int) {
        /**
         * Which callees have been ruled out.
         * The last callee standing is the choice.
         */
        var rejectedCallees = KBitSet()
        // We could store information about why callees were rejected in
        // the case that all are eventually.

        val nPossible get() = nVariants - rejectedCallees.cardinality()

        /**
         * For each callee, its [applicationOrderForActuals] as applied to args.
         */
        val applicationOrders = mutableListOf<List<Int?>?>()

        /**
         * True if constraints have been added between the chosen callee's
         * type formals and the type actuals.
         */
        var calleeConstraintsAdded = false

        /**
         * The count of rejected callees the last time we tried to propagate
         * bounds on inputs/outputs based on worst-case analysis of callees.
         *
         * -1 if we have never done a worst-case analysis.
         */
        var lastWorstCaseAnalysisRejectedCount = -1

        /**
         * Type variables that correspond to the chosen callee's type formals
         * in the context of the specific call.
         *
         * Before a callee is chosen, they may be allocated to correspond
         * to type parameters that are roughly equivalent across possible callees.
         */
        val typeParameterVars = mutableListOf<TypeVar>()
    }

    private fun CallConstraintState.ensureTypeParameterVars(arity: Int) {
        while (typeParameterVars.size < arity) {
            typeParameterVars.add(unusedTypeVar("P"))
        }
    }

    private val callConstraintStates = mutableMapOf<CallConstraint, CallConstraintState>()
    private fun processCallConstraint(cons: CallConstraint) {
        val callees = cons.callees
        val state = callConstraintStates.getOrPut(cons) {
            CallConstraintState(callees.size)
                .also { state ->
                    val typeArgVars = cons.typeArgVars
                    if (typeArgVars != null) {
                        state.typeParameterVars.addAll(typeArgVars)
                    }
                }
        }
        val applicationOrders = state.applicationOrders

        val calleeChoiceNode = node(cons.calleeChoice)
        if (calleeChoiceNode.choice == null) {
            // Try to pick a callee.
            val nCallees = callees.size
            val nInputs = cons.args.size
            val possibleCalleeIndices = state.rejectedCallees.clearBitIndices(0 until nCallees)
            val nExplicitTypeArgs = cons.explicitTypeArgs?.size ?: 0

            if (applicationOrders.isEmpty() && nCallees != 0) {
                // Fill in the application orders
                val actuals = (0 until nInputs).map { i ->
                    if (i + 1 < nInputs || !cons.hasTrailingBlock) { null } else { Either.Right(TFunction) }
                }
                callees.mapTo(applicationOrders) { callee ->
                    (applicationOrderForActuals(actuals, callee.sig) as? Either.Left)?.item
                }
            }

            for (calleeIndex in possibleCalleeIndices.reverse()) {
                val callee = callees[calleeIndex]
                val applicationOrder = applicationOrders[calleeIndex]
                if (applicationOrder == null || nExplicitTypeArgs > callee.sig.typeFormals.size) {
                    state.rejectedCallees[calleeIndex] = true
                }
            }
            if (state.nPossible > 1) {
                // Try to rule out callees based on nullity and argument types.
                //
                // If an actual is nullable and a formal isn't, the callee doesn't fit.
                //
                // If an actual has a lower or common bound with a type shape
                // that doesn't have an inheritance chain to shapes required by the formal,
                // it isn't a fit.
                // This is equivalent to generic erasure argument analysis.
                //
                // We look at the argument expression's common and lower bounds
                // (and choices) but not upper bounds because, as the below shows,
                // the upper bounds could be above or below the declared type of
                // the function parameter.
                //
                //            Top
                //             ↓
                //       Upper Bound #1
                //             ↓
                //     Formal Declared Type
                //             ↓
                //       Upper Bound #2
                //             ↓
                //        Common Bound
                //             ↓
                //       Lower Bound #1
                //             ↓
                //       Lower Bound #2
                //             ↓
                //           Bottom
                argLoop@
                for (i in cons.args.indices) {
                    val argVar = cons.args[i]
                    val argNode = node(argVar)
                    if (argNode.choice is Unsolvable) {
                        continue
                    }

                    // List all the class/interface types it could be.
                    // Then we'll check that we can find any declared class/interface
                    // types in the argument declaration and make sure we have a path to them.
                    class ShapeAndNullityInfo {
                        val typeShapes = mutableSetOf<TypeShape>()
                        var canBeNull = false
                        val visited = mutableSetOf<TypeFormal>()
                    }

                    fun unpackTypeShapes(defn: TypeDefinition, out: ShapeAndNullityInfo) {
                        when (defn) {
                            is TypeShape -> out.typeShapes.add(defn)
                            is TypeFormal -> if (defn !in out.visited) {
                                out.visited.add(defn)
                                defn.superTypes.forEach {
                                    // TODO: avoid cycles
                                    // TODO: set out.canBeNull if any super-type is
                                    // nullable.
                                    unpackTypeShapes(it.definition, out)
                                }
                            }
                        }
                    }

                    val actualInfo = ShapeAndNullityInfo().also { info ->
                        when (val argChoice = argNode.choice) {
                            is TypeOrPartialType -> {
                                unpackTypeShapes(argChoice.definition, info)
                                if (argChoice.nullity == OrNull) {
                                    info.canBeNull = true
                                }
                            }

                            Unsolvable -> error(argNode) // Checked eagerly above
                            null -> {
                                val typeBoundLists = listOf(
                                    argNode.fullBounds.commonBounds,
                                    argNode.fullBounds.lowerBounds,
                                    argNode.partialBounds.commonBounds,
                                    argNode.partialBounds.lowerBounds,
                                )
                                for (boundList in typeBoundLists) {
                                    for (t in boundList) {
                                        if (t.nullity == OrNull) {
                                            info.canBeNull = true
                                        }
                                        unpackTypeShapes(t.definition, info)
                                    }
                                }
                            }
                        }
                        info.typeShapes.remove(invalidTypeDefinition)
                    }

                    for (calleeIndex in possibleCalleeIndices.reverse()) {
                        val applicationOrder = applicationOrders[calleeIndex]!!
                        val (sig) = callees[calleeIndex]
                        val formalIndex = correspondingFormal(i, applicationOrder)
                        val formalDeclaredType = sig.valueFormalForActual(formalIndex)!!.type // arity checked already
                        // As described above:
                        // 1. try to rule out based on nullity, and erased type.
                        //    If an arg can be null, and the formal does not allow it, deny.
                        // 2. try to rule out based on type shapes inheritance.
                        var possible = true
                        if (actualInfo.canBeNull) { // 1
                            val formalAdmitsNull = typeContext.admitsNull(formalDeclaredType)
                            if (!formalAdmitsNull) {
                                possible = false
                            }
                        }
                        if (possible) { // 2
                            val typeShapesInActual = actualInfo.typeShapes
                            val formalInfo = ShapeAndNullityInfo().also { info ->
                                unpackTypeShapes(formalDeclaredType.definition, info)
                            }
                            val typeShapesInFormal = formalInfo.typeShapes
                            // If there is a type shape from an actual bound that has no path up to a
                            // required type shape, deny.
                            // This means that as we get more bounds, we have more possible reasons
                            // to deny, but having fewer bounds available never leads to more denying.
                            val wellMatched = typeShapesInActual.all { actualShape ->
                                typeShapesInFormal.all { formalShape ->
                                    typeContext.extendsPath(actualShape, formalShape) != null
                                }
                            }
                            // TODO: change this to do pair filtering once we have a place to put
                            // rejection explanations
                            if (!wellMatched) {
                                possible = false
                            }
                        }

                        if (!possible) {
                            state.rejectedCallees.set(calleeIndex)
                            if (state.nPossible <= 1) {
                                break@argLoop
                            }
                        }
                    }
                }
            }
            if (state.nPossible > 1) {
                // Try to rule out callees based on contextual upper bounds.
                val passTypeNode = node(cons.callPass)
                var isPassVoidLike: Boolean? = null
                for (
                boundSet in listOf(
                    listOfNotNull((passTypeNode.choice as? Type2)?.let { it to BoundKind.Common }),
                    passTypeNode.fullBounds, passTypeNode.partialBounds,
                )
                ) {
                    for ((b, _) in boundSet) {
                        val t = maybeUnwrapNever(
                            maybeUnwrapPass(b) as? TypeOrPartialType,
                        )
                        if (t is TypeOrPartialType) {
                            isPassVoidLike = t.definition == voidTypeDefinition
                            break
                        }
                    }
                }

                if (isPassVoidLike != null) {
                    for (calleeIndex in possibleCalleeIndices.reverse()) {
                        val callee = callees[calleeIndex]
                        val returnType = callee.sig.returnType2
                        val returnTypeUnwrapped = maybeUnwrapNever(
                            maybeUnwrapPass(returnType) as? TypeOrPartialType,
                        ) as? TypeOrPartialType
                        val isReturnTypeVoidLike = returnTypeUnwrapped?.let {
                            it.definition == voidTypeDefinition
                        }
                        if (isReturnTypeVoidLike != null && isReturnTypeVoidLike != isPassVoidLike) {
                            state.rejectedCallees.set(calleeIndex)
                        }
                    }
                }
            }
            if (state.nPossible > 1) {
                // Try to rule out callees based on specificity rules.
                // If one callee has an input that is more general than the other
                // and none that are less, and all actual inputs that bind to
                // the more specific inputs have known types that are allowed by the
                // more specific callee, rule out the less specific callee.
                // I.e., if every future finding about input type bounds that would
                // rule out the specific binding also rules out the generic binding,
                // it is safe to rule out the generic binding now.
                iLoop@
                for (i in possibleCalleeIndices) {
                    val a = callees[i]
                    for (j in possibleCalleeIndices.limited(i + 1 until nCallees)) {
                        val b = callees[j]
                        val spec = typeContext.overloadSpecificity(a.sig, b.sig)
                            ?: continue

                        // If we have a type solution for every input listed in the distinguishing
                        // list, then we have satisfied the condition above that either a or b
                        // fail together.
                        if (
                            spec.distinguishingArgumentIndices.all { actualIndex ->
                                val actualArg = cons.args.getOrNull(actualIndex)
                                actualArg != null && node(actualArg).choice != null
                            }
                        ) {
                            // Reject the less specific.  If we reject `a`, then we can skip
                            // to the next iteration of its loop.
                            when (spec.moreSpecific) {
                                LeftOrRight.Left -> {
                                    // `a` is more specific, so rule out `b`.
                                    state.rejectedCallees[j] = true
                                }
                                LeftOrRight.Right -> {
                                    state.rejectedCallees[i] = true
                                    continue@iLoop
                                }
                            }
                        }
                    }
                }
            }

            // Skip worst case analysis if we've already narrowed it down, or we have done it before
            // and haven't rejected any callees since last doing it.
            if (
                state.nPossible > 1 &&
                state.rejectedCallees.cardinality() > state.lastWorstCaseAnalysisRejectedCount
            ) {
                state.lastWorstCaseAnalysisRejectedCount = state.rejectedCallees.cardinality()
                // Worst-case analysis
                // ===================
                // Often, there are commonalities among overloads.
                // For example, imagine these overloads for extension functions
                // that produce a debug string:
                //                    (Int)     => String |
                //                    (Boolean) => String |
                //                    (String)  => String |
                //     <T extends AnyValue>(T?) => String?
                // We can determine that `String?` is an upper-bound on the pass variable.
                // We can determine that the empty failure list is the solution for the fail variable.
                // After eliminating the last, we can determine that `String` is a common-bound
                // for the pass variable.
                //
                // Let's say we have some generic overloads.  Each has one type parameter,
                // and each of those type parameters are a separate definition.
                //     <T0>(T0, String ) => T0 |
                //     <T1>(T1, Float64) => T1
                // We can't eliminate either overload until we have more type information for the
                // second argument.
                // But by allocating a type variable for the type parameter, ʼP, we can unify the
                // signatures:
                //     (ʼP, ???) => ʼP
                // That might allow propagating upper bounds on the passing output to the input
                // and lower bounds on the input to the passing output bound.
                //
                // Worst-case analysis proceeds as follows:
                // Step I: possibleSigs is the list of possible signatures
                // Step II: minTypeArity is the minimum number of type parameters on sigs in
                //         possibleSigs that have the same variance
                // Step III: allocate type parameter variables reusing any from state
                // Step IV: for each actual input,
                //         - for each possible sig,
                //           - get the corresponding formal type from the sig.
                //           - transform it by replacing that sig's type parameters
                //                 with the type parameter variables from step 3,
                //           - put that transformed type in a set.
                //         - if there is a unique least upper bound for that partial type,
                //           - add it as an upper bound of that actual input's
                // Step V: now do the corresponding for the output, but we're looking for
                // a unique greatest lower bound on the pass variable.
                // Step VI: if we used bounds, save our type parameter variables in state
                // in case a further narrowing allows better worst-case analysis.

                // Step I
                val possibleCallees = possibleCalleeIndices.map { index -> index to callees[index] }
                // Step II
                val sig0TypeFormals = possibleCallees.first().second.sig.typeFormals
                var minTypeArity = sig0TypeFormals.size
                var maxTypeArity = sig0TypeFormals.size
                for ((_, c) in possibleCallees) {
                    val typeFormals = c.sig.typeFormals
                    minTypeArity = min(minTypeArity, typeFormals.size)
                    maxTypeArity = max(maxTypeArity, typeFormals.size)
                    while (minTypeArity > 0) {
                        val lastIndex = minTypeArity - 1
                        if (typeFormals[lastIndex].variance == sig0TypeFormals[lastIndex].variance) {
                            break
                        }
                        minTypeArity -= 1
                    }
                }
                // Step III
                state.ensureTypeParameterVars(minTypeArity)
                val varsForWorstCase = state.typeParameterVars.toList()
                cons.explicitTypeArgs?.forEachIndexed { i, typeActual ->
                    val v = varsForWorstCase.getOrNull(i)
                    if (v != null) {
                        sameAs(TypeVarRef(v, NonNull), typeActual)
                    }
                }
                // Step IV
                // For each callee, we have a mapping from type formals to the vars.
                // We reuse this to contextualize output types in Step V.
                data class PossibleCallee(
                    val calleeIndex: Int,
                    val callee: Callee,
                    val bindingMap: Map<TypeFormal, TypeVarRef?>,
                )
                val possibleCalleesWithBindingMaps = possibleCallees.map { (i, callee) ->
                    PossibleCallee(
                        i,
                        callee,
                        buildMap {
                            for ((i, tf) in callee.sig.typeFormals.withIndex()) {
                                val typeVarOrNull = if (i < minTypeArity) {
                                    TypeVarRef(varsForWorstCase[i], NonNull)
                                } else {
                                    null
                                }
                                put(tf, typeVarOrNull)
                            }
                        },
                    )
                }

                /**
                 * Contextualize a type from a signature, but null if
                 * typeFromSig uses type formals that can't be remapped.
                 */
                fun contextualizeWorstCase(
                    typeFromSig: Type2,
                    bindingMap: Map<TypeFormal, TypeVarRef?>,
                ): TypeLike? {
                    if (bindingMap.isEmpty()) {
                        return typeFromSig
                    }
                    var usesUnmappedTypeFormals = false
                    val contextualizedType = typeFromSig.mapType({ null }) { typeFormal ->
                        if (typeFormal in bindingMap) {
                            val varRef = bindingMap[typeFormal]
                            if (varRef == null) {
                                usesUnmappedTypeFormals = true
                            }
                            varRef
                        } else {
                            null
                        }
                    }
                    return if (usesUnmappedTypeFormals) {
                        null
                    } else {
                        contextualizedType
                    }
                }

                fun reduceTypeLikeBoundSet(
                    bounds: Set<TypeLike>,
                    reduce: (Iterable<TypeOrPartialType>) -> TypeOrPartialType?,
                ): TypeLike? {
                    if (bounds.size == 1) {
                        return bounds.first()
                    }
                    val nonVarRefBounds = bounds.map {
                        if (it !is TypeOrPartialType) {
                            return@reduceTypeLikeBoundSet null
                        }
                        it
                    }
                    return reduce(nonVarRefBounds)
                }

                step4Loop@
                for ((actualIndex, actualVar) in cons.args.withIndex()) {
                    val partialUpperBounds = mutableSetOf<TypeLike>()
                    for ((calleeIndex, callee, bindingMap) in possibleCalleesWithBindingMaps) {
                        val applicationOrder = applicationOrders[calleeIndex]!!
                        val formalIndex = correspondingFormal(actualIndex, applicationOrder)
                        val valueFormalType = callee.sig.valueFormalForActual(formalIndex)!!.type
                        val contextualizedType = contextualizeWorstCase(valueFormalType, bindingMap)
                            ?: continue@step4Loop // Can't compare it to others meaningfully
                        partialUpperBounds.add(contextualizedType)
                    }
                    // The formal for the eventually chosen callee, in the context of
                    // the current call, is an upper bound on the actual expression's result.
                    //
                    //    contextualizedFormal
                    //             |
                    //           actual
                    //
                    // So any common upper bound on the formals is an upper bound on the actual.
                    val commonUpperBound = reduceTypeLikeBoundSet(partialUpperBounds) {
                        typeContext.leastCommonSuperPartialTypes(it, optimistic = false).soleElementOrNull
                    }
                    when (commonUpperBound) {
                        anyValueType2 -> {}
                        is TypeOrPartialType -> addBound(commonUpperBound, node(actualVar), BoundKind.Upper)
                        is TypeVarRef -> assignable(commonUpperBound, actualVar)
                        null -> {}
                    }
                }

                // Step V
                run step5@{
                    val partialLowerPassBounds = mutableSetOf<TypeLike>()
                    for ((_, callee, bindingMap) in possibleCalleesWithBindingMaps) {
                        val outputType = callee.sig.returnType2
                        val passType = if (outputType.definition == resultTypeDefinition) {
                            outputType.bindings.firstOrNull() ?: return@step5
                        } else {
                            outputType
                        }
                        val contextualizedType = contextualizeWorstCase(passType, bindingMap)
                            ?: return@step5 // Can't compare it to others meaningfully
                        partialLowerPassBounds.add(contextualizedType)
                    }

                    // The return type for the eventually chosen callee, in the context of
                    // the current call, is a lower bound on the actual result.
                    //
                    //      receiverExpectation
                    //              |
                    //    contextualizedPassType
                    //
                    // So any common lower bound on the formals is a lower bound
                    // on the type of value received.

                    val commonLowerBound = reduceTypeLikeBoundSet(partialLowerPassBounds) {
                        val iterator = it.iterator()
                        if (iterator.hasNext()) {
                            var glb: TypeOrPartialType? = iterator.next()
                            while (glb != null && iterator.hasNext()) {
                                glb = typeContext.glbPartial(glb, iterator.next()) as TypeOrPartialType?
                            }
                            glb
                        } else {
                            null
                        }
                    }
                    when (commonLowerBound) {
                        is TypeOrPartialType -> addBound(commonLowerBound, node(cons.callPass), BoundKind.Lower)
                        is TypeVarRef -> assignable(cons.callPass, commonLowerBound)
                        null -> {}
                    }
                }

                // Make some choices that are obvious.
                // - if there are no result return types, the fail list is empty.
                // - if we have declared parameters for all type variables, create a put constraint.
                val failNode = cons.callFail?.let { node(it) }
                if (failNode != null && failNode.choice == null) {
                    if (possibleCallees.all { it.second.sig.returnType2.definition != resultTypeDefinition }) {
                        choose(failNode, TypeListSolution.empty)
                    }
                }
                val typeActualsNode = cons.typeActuals?.let { node(it) }
                if (
                    typeActualsNode != null &&
                    maxTypeArity == possibleCallees.firstOrNull()?.second?.sig?.typeFormals?.size
                ) {
                    if (typeActualsNode.choice == null && maxTypeArity == 0) {
                        choose(typeActualsNode, TypeListSolution.empty)
                    } else {
                        addConstraint(
                            PutConstraint(
                                typeActualsNode.bound,
                                varsForWorstCase.map { TypeVarRef(it, NonNull) },
                            ),
                        )
                    }
                }
            }

            if (state.nPossible > 1 && cons.args.all { node(it).choice != null }) {
                // We have all the arg info that we could use to rule out a high priority variant.
                // Eliminate lower priority callees if a high priority one is eligible.
                var highestPriority = CalleePriority.entries[0]
                var lowestPriority = CalleePriority.entries.last()

                for (i in possibleCalleeIndices) {
                    val (_, priority) = callees[i]
                    if (priority > highestPriority) {
                        highestPriority = priority
                    }
                    if (priority < lowestPriority) {
                        lowestPriority = priority
                    }
                }

                if (highestPriority != lowestPriority) {
                    for (i in possibleCalleeIndices) {
                        val callee = callees[i]
                        if (callee.priority < highestPriority) {
                            state.rejectedCallees[i] = true
                        }
                    }
                }
            }

            when (state.nPossible) {
                0 -> choose(calleeChoiceNode, Unsolvable) // Oops
                1 -> choose(calleeChoiceNode, IntSolution(possibleCalleeIndices.first()))
                else -> {} // Maybe next time, boyo.
            }
        }

        // If we have a callee choice, we can go ahead and add constraints.
        //
        // I. TYPE PARAMETER CONSTRAINTS
        // =============================
        // If the signature defines type formals, eg `<T extends Foo, U extends Bar<T>>`,
        // we add bounds.  From the chosen callee, assume the typeParameterVar for T is p_T.
        // Then we can come up with type parameter constraints.
        //     p_T <: Foo
        //     p_U <: Bar<p_T>
        // And we need to construct an actual parameter list.
        //     typeActuals <- p_T, p_U
        //
        // II. EXPLICIT TYPE ARG CONSTRAINTS
        // =================================
        // If we have explicit type arguments, we can add bounds on those.
        // Assume pe_0 is the 0-th such arg.
        //     pe_0 <: p_T <: pe_0
        //
        // III. VALUE PARAMETER CONSTRAINTS
        // ================================
        // For each argument, we can add a constraint between the argument's declared type
        // and the argument expression's type.
        // Where a_0 is the CallConstraint's 0-th argVar.
        // If the signature defines inputs (x: List<T>, y: Int), we end up with these constraints:
        //    a_0 == List<p_T>
        //    a_1 == Int
        // In the case where multiple actual arguments correspond to the rest parameter, we might
        // end up with multiple constraints on the rest parameter's declared type.
        //
        // IV. RETURN TYPE CONSTRAINTS
        // ===========================
        // For the return type, we have two cases.  The case where the declared return type is
        // a *Result* type, and the case where it is not.
        // If the declared type is Result<Baz<U>, Err>, we add constraints.
        // Assuming callPass is TypeVar pass, and callFail is TypeVar fail
        //    Baz<p_U> <: pass <: Baz<p_U>
        //    fail <- Err
        // If the declared type is not a result type, Baz<U>, we still have two constraints.
        //    Baz<p_U> <: pass <: Baz<p_U>
        //    fail <-
        // Any code that unions failure types to infer the failure modes of a block lambda,
        // should filter out Never types.
        if (calleeChoiceNode.choice != null && !state.calleeConstraintsAdded) {
            state.calleeConstraintsAdded = true
            val chosenCalleeIndex = when (val chosen = calleeChoiceNode.choice!!) {
                is Unsolvable -> {
                    choose(node(cons.callPass), Unsolvable)
                    cons.callFail?.let {
                        choose(node(it), Unsolvable)
                    }
                    cons.typeActuals?.let {
                        choose(node(it), Unsolvable)
                    }
                    null
                }
                is IntSolution -> chosen.n
                is TypeSolution,
                is TypeListSolution,
                -> error("$chosen")
            }
            if (chosenCalleeIndex != null) {
                val chosenCallee = callees[chosenCalleeIndex].sig
                state.ensureTypeParameterVars(chosenCallee.typeFormals.size)
                val typeParameterVars: List<TypeVar> = state.typeParameterVars.toList()
                val typeFormalToVarRef = buildMap {
                    for ((v, f) in typeParameterVars zip chosenCallee.typeFormals) {
                        this[f] = TypeVarRef(v, NonNull)
                    }
                }
                debug { "typeFormalToVarRef=$typeFormalToVarRef" }
                // rewrites T -> p_T.  This removes all types that are only meaningful in the context of the
                // callee's signature or body by replacing them with type variables that are meaningful in
                // the context of the current call.
                val contextualizeType: (TypeLike) -> TypeLike = if (typeFormalToVarRef.isEmpty()) {
                    // A common case for non-generic functions
                    { it }
                } else {
                    {
                        it.mapType(emptyMap(), typeFormalToVarRef)
                    }
                }

                // Part I
                for ((f, varRef) in typeFormalToVarRef) {
                    for (superType in f.superTypes) {
                        val superType2 = hackMapOldStyleToNew(superType)
                        addConstraint(SubTypeConstraint(varRef, contextualizeType(superType2)))
                    }
                }
                val typeActualsVar = cons.typeActuals
                if (typeActualsVar != null) { // typeActuals <- [p_A, p_B, ...]
                    if (typeParameterVars.isEmpty()) {
                        val typeActualsVarNode = node(typeActualsVar)
                        if (typeActualsVarNode.choice == null) {
                            choose(typeActualsVarNode, TypeListSolution.empty)
                        }
                    } else {
                        addConstraint(
                            PutConstraint(
                                typeActualsVar,
                                typeParameterVars.map { TypeVarRef(it, NonNull) },
                            ),
                        )
                    }
                }

                // Part II
                cons.explicitTypeArgs?.forEachIndexed { i, typeActual ->
                    val v = typeParameterVars.getOrNull(i)
                    if (v != null) {
                        sameAs(TypeVarRef(v, NonNull), typeActual)
                    }
                }

                // Part III
                val applicationOrder = applicationOrders[chosenCalleeIndex]!!
                for (argIndex in cons.args.indices) {
                    val formalIndex = correspondingFormal(argIndex, applicationOrder)
                    val formal = chosenCallee.valueFormalForActual(formalIndex)
                        ?: break
                    val formalType = contextualizeType(formal.type)
                    val argVar = cons.args[argIndex]
                    val argRef = TypeVarRef(argVar, NonNull)
                    addConstraint(SameTypeConstraint(argRef, formalType))
                }

                // Part IV
                val returnType = contextualizeType(chosenCallee.returnType2)
                val passVarRef = TypeVarRef(cons.callPass, NonNull)
                if (returnType is TypeOrPartialType && returnType.definition == resultTypeDefinition) {
                    if (returnType.bindings.isNotEmpty()) {
                        addConstraint(SameTypeConstraint(passVarRef, returnType.bindings[0]))
                        cons.callFail?.let { callFailVar ->
                            addConstraint(
                                PutConstraint(
                                    callFailVar,
                                    returnType.bindings.subListToEnd(1).map {
                                        contextualizeType(it)
                                    },
                                ),
                            )
                        }
                    } else {
                        choose(node(cons.callPass), Unsolvable)
                        cons.callFail?.let { choose(node(it), Unsolvable) }
                    }
                } else {
                    addConstraint(SameTypeConstraint(passVarRef, returnType))
                    cons.callFail?.let {
                        val callFailNode = node(it)
                        if (callFailNode.choice == null) {
                            choose(callFailNode, TypeListSolution.empty)
                        }
                    }
                }
            }
        }
    }

    private fun processUsesConstraint(cons: UsesConstraint) {
        val (typeLike, typeVars) = cons
        debug { "processing UsesConstraint $cons, partial=$typeLike, typeVars=$typeVars" }
        val typeLikeNode = node(typeLike)
        if (typeLike is TypeVarRef) {
            check(typeVars.size == 1 && typeVars[0] == typeLike.typeVar)
            processUseOfVarRef(typeLike)
            return
        }

        if (typeLikeNode.choice != null) { return }
        val bindings = buildMap {
            for (v in typeVars) {
                val vNode = node(v)
                debug { ". v=$v, node=$vNode" }
                this[v] = vNode.choice as? Type2 ?: return
            }
        }
        if (bindings.isNotEmpty()) {
            debug { ". bindings=$bindings" }
            when (val fullerType = typeLike.mapType(bindings, emptyMap())) {
                is Type2 -> choose(typeLikeNode, fullerType)

                is PartialType ->
                    addBound(fullerType, typeLikeNode, BoundKind.Common)

                is TypeVarRef -> error("$fullerType") // Fast path return above
            }
        }
    }

    private fun processUseOfVarRef(varRef: TypeVarRef) {
        val varNode = node(varRef.typeVar)
        val varRefNode = node(varRef)
        val varRefChoice = varRefNode.choice
        val varChoice = varNode.choice
        if (varRefChoice != null && varChoice != null) {
            return
        }

        if (varChoice != null) {
            when (varChoice) {
                is Unsolvable -> choose(varRefNode, Unsolvable)
                is Type2 -> choose(
                    varRefNode,
                    when (varRef.nullity) {
                        OrNull -> varChoice.withNullity(OrNull)
                        NonNull -> varChoice
                    },
                )
            }
            return
        }

        if (varRefChoice != null) {
            when (varRef.nullity) {
                NonNull -> {
                    choose(varNode, varRefChoice)
                }
                OrNull -> when (varRefChoice) {
                    is Unsolvable -> choose(varRefNode, varRefChoice)
                    is Type2 -> {
                        // T! <: T <: T?
                        addBound(varRefChoice, varNode, BoundKind.Upper)
                        val nonNullBound = varRefChoice.withNullity(NonNull)
                        if (!typeContext.admitsNull(nonNullBound)) {
                            addBound(nonNullBound, varNode, BoundKind.Lower)
                        }
                    }
                }
            }
            return
        }

        // Incorporate bounds.
        // If varNode is nullable, then we need to adjust bounds it receives and propagate some questions
        // about the
        when (varRef.nullity) {
            NonNull -> {
                copyBounds(varNode.fullBounds, varRefNode)
                copyBounds(varNode.partialBounds, varRefNode)
                copyBounds(varRefNode.fullBounds, varNode)
                copyBounds(varRefNode.partialBounds, varNode)
            }

            OrNull -> {
                // When taking non-lower bounds from V to V?, we need to add nullity
                for ((b, k) in varNode.fullBounds) {
                    val bp = b.withNullity(OrNull)
                    addBound(bp, varRefNode, k)
                }
                for ((b, k) in varNode.partialBounds) {
                    val bp = b.withNullity(OrNull)
                    addBound(bp, varRefNode, k)
                }
                // When taking nullable bounds from V? to V, we need to fudge the nullity.
                fun <T : TypeOrPartialType> incorporatePartialBounds(
                    from: BoundSet<T>,
                    mightHave: BoundSet<T>,
                    to: TypeNode,
                ) {
                    for (bk in from) {
                        if (bk !in mightHave) {
                            val (b, k) = bk
                            val (addNullUpper, addNonNullLower) = when (k) {
                                // A? is an upper bound of A
                                BoundKind.Upper -> true to false
                                // A is lower bound of A?
                                BoundKind.Lower -> false to true
                                BoundKind.Common -> true to true
                            }
                            if (addNullUpper) {
                                val bp = b.withNullity(OrNull)
                                addBound(bp, to, BoundKind.Upper)
                            }
                            if (addNonNullLower) {
                                val bp = b.withNullity(NonNull)
                                addBound(bp, to, BoundKind.Lower)
                            }
                        }
                    }
                }
                incorporatePartialBounds(varRefNode.fullBounds, varNode.fullBounds, varNode)
                incorporatePartialBounds(
                    varRefNode.partialBounds,
                    varNode.partialBounds,
                    varNode,
                )
            }
        }
    }

    private fun processPutConstraint(cons: PutConstraint) {
        val (outVar, elements) = cons
        val outNode = node(outVar)
        if (outNode.choice != null) { return }
        val elementNodes = elements.map {
            node(it)
        }
        if (elementNodes.all { it.choice != null }) {
            choose(
                outNode,
                TypeListSolution(elementNodes.map { it.choice!! }),
            )
        }
    }

    private fun checkNode(node: TypeNode) {
        // do not commit: TODO: if there's a common bound, project any lower bounds to it.
        val lastCheckStamp = node.lastCheckStamp
        val needToCheck = node.choice == null && (
            !node.boundChecked ||
                node.fullBounds.lastChangedStamp > lastCheckStamp ||
                node.partialBounds.lastChangedStamp > lastCheckStamp ||
                node.typeVarsUsed.any { node(it).solvedAtStamp > lastCheckStamp }
            )
        if (!needToCheck) {
            return
        }
        node.lastCheckStamp = nextChangeStamp()

        debug { "Checking $node" }
        // Do some initial unpacking of the bound
        if (!node.boundChecked) {
            debug { ". checking bound" }
            node.boundChecked = true
            when (val b = node.bound) {
                is TypeSolution -> choose(node, b)
                is PartialType -> addBound(b, node, BoundKind.Common)
                is TypeVar, is TypeVarRef -> {}
                is ValueBound -> {
                    var partialBound: TypeOrPartialType? = null // Partial upper bound
                    var partialBoundKind = BoundKind.Common
                    val v = b.value
                    when (val tt = v.typeTag) {
                        TBoolean -> choose(node, MkType2(WellKnownTypes.booleanTypeDefinition).get())
                        TFloat64 -> choose(node, MkType2(WellKnownTypes.float64TypeDefinition).get())
                        TInt -> choose(node, MkType2(WellKnownTypes.intTypeDefinition).get())
                        TInt64 -> choose(node, MkType2(WellKnownTypes.int64TypeDefinition).get())
                        TString -> choose(node, MkType2(WellKnownTypes.stringTypeDefinition).get())
                        is TClass -> {
                            val typeShape = tt.typeShape
                            partialBound = when (typeShape.typeCategory) {
                                TypeCategory.Never,
                                TypeCategory.Result,
                                -> null // Something is very wrong
                                else -> partialBoundFor(typeShape)
                            }
                            if (partialBound != null) {
                                // TODO: do something with the parts
                                addConstraint(SubTypeConstraint(b, partialBound))
                            }
                        }
                        TClosureRecord -> {}
                        TFunction -> {} // TODO: from signature
                        TList,
                        TListBuilder,
                        -> {
                            val defn = if (tt == TList) {
                                WellKnownTypes.listTypeDefinition
                            } else {
                                WellKnownTypes.listBuilderTypeDefinition
                            }

                            @Suppress("UNCHECKED_CAST") // It's one of two above
                            val ttt = tt as TypeTag<List<Value<*>>>
                            partialBound = partialBoundFor(defn)
                            val (elementType) = partialBound.bindings
                            ttt.unpack(v).forEach { elementValue ->
                                assignable(elementType, ValueBound(elementValue))
                            }
                        }
                        TMap, TMapBuilder -> {
                            val defn = if (tt == TMap) {
                                WellKnownTypes.mapTypeDefinition
                            } else {
                                WellKnownTypes.mapBuilderTypeDefinition
                            }

                            @Suppress("UNCHECKED_CAST") // It's one of two above
                            val ttt = tt as TypeTag<Map<Value<*>, Value<*>>>
                            partialBound = partialBoundFor(defn)
                            val (keyType, valueType) = partialBound.bindings
                            ttt.unpack(v).forEach { (key, value) ->
                                assignable(keyType, ValueBound(key))
                                assignable(valueType, ValueBound(value))
                            }
                        }
                        TNull -> {
                            partialBound = partialBoundFor(neverTypeDefinition, OrNull, listOf("nullLiteral"))
                            partialBoundKind = BoundKind.Lower
                        }
                        TProblem -> {}
                        TStageRange -> {}
                        TSymbol -> {}
                        TType -> {}
                        TVoid -> choose(node, WellKnownTypes.voidType2)
                    }
                    // If we allocated partial upper bounds, make sure the "uses" is set
                    if (partialBound != null) {
                        addBound(partialBound, node, partialBoundKind)
                        if (partialBound is PartialType) {
                            node.typeVarsUsed = partialBound.typeVarsUsed()
                        }
                    }
                }
            }
        }
        if (node.choice != null) {
            return
        }

        // If we have any nullable lower or common bounds, then make all lower bounds nullable.
        // If we have any not-Never lower or common bounds, then make all lower bounds not *Never*.
        // If we have any non-nullable upper bounds, then make all upper bounds non-nullable.
        // These are strictly narrowing changes.
        run {
            var hasNullableLowerOrCommon = false
            var hasNonNullableLower = false
            var hasNeverLower = false
            var hasNonNeverLowerOrCommon = false
            var hasNonNullableUpperOrCommon = false
            var hasNullableUpper = false

            val lowers = joinedIterable(node.partialBounds.lowerBounds, node.fullBounds.lowerBounds)
            val uppers = joinedIterable(node.partialBounds.upperBounds, node.fullBounds.upperBounds)
            val commons = joinedIterable(node.partialBounds.commonBounds, node.fullBounds.commonBounds)
            for (b in lowers) {
                when (typeContext.admitsNullFuzzingTypeParamRef(b)) {
                    true -> hasNullableLowerOrCommon = true
                    false -> hasNonNullableLower = true
                    null -> {}
                }
                if (b.definition != neverTypeDefinition) {
                    hasNonNeverLowerOrCommon = true
                } else if (b.bindings.soleElementOrNull is TypeOrPartialType) {
                    hasNeverLower = true
                }
            }
            for (b in uppers) {
                when (typeContext.admitsNullFuzzingTypeParamRef(b)) {
                    true -> hasNullableUpper = true
                    false -> hasNonNullableUpperOrCommon = true
                    null -> {}
                }
            }
            if (!hasNullableLowerOrCommon || !hasNonNullableUpperOrCommon || !hasNonNeverLowerOrCommon) {
                for (b in commons) {
                    when (typeContext.admitsNullFuzzingTypeParamRef(b)) {
                        true -> hasNullableLowerOrCommon = true
                        false -> hasNonNullableUpperOrCommon = true
                        null -> {}
                    }
                    if (b.definition != neverTypeDefinition) {
                        hasNonNeverLowerOrCommon = true
                    }
                }
            }

            /**
             * Called when a bound is no longer needed to remove it and prevent it
             * from being readded.  This adds to an obviated set so that addBound
             * will report no change from it being added.
             *
             * This prevents oscillating bound sets: when we find a more specific
             * type we obviate the old one and don't add it back to have to then
             * compute the more specific type again.
             */
            fun obviate(b: TypeOrPartialType, k: BoundKind) = when (b) {
                is PartialType -> (node.partialBounds as MutBoundSet).obviate(b, k)
                is Type2 -> (node.fullBounds as MutBoundSet).obviate(b, k)
            }
            if (hasNullableLowerOrCommon && hasNonNullableLower) {
                for (b in lowers.toList()) {
                    if (typeContext.admitsNullFuzzingTypeParamRef(b) == false) {
                        val bNullable = b.withNullity(OrNull)
                        if (b != bNullable) {
                            debug(
                                { "Promoting $b to $bNullable in $node because of nullable lower|common bound" },
                            ) {
                                if (addBound(bNullable, node, BoundKind.Lower)) {
                                    obviate(b, BoundKind.Lower)
                                }
                            }
                        }
                    }
                }
            }
            if (hasNonNeverLowerOrCommon && hasNeverLower) {
                for (b in lowers.toList()) {
                    if (b.definition == neverTypeDefinition && b.bindings.size == 1) {
                        var bNonNever = b.bindings[0]
                        if (b.nullity == OrNull) {
                            bNonNever = bNonNever.withNullity(OrNull)
                        }
                        if (bNonNever is TypeOrPartialType) {
                            debug(
                                { "Promoting $b to $bNonNever in $node because of non-Never lower|common bound" },
                            ) {
                                if (addBound(bNonNever, node, BoundKind.Lower)) {
                                    obviate(b, BoundKind.Lower)
                                }
                            }
                        }
                    }
                }
            }
            if (hasNonNullableUpperOrCommon && hasNullableUpper) {
                for (b in uppers.toList()) {
                    if (typeContext.admitsNullFuzzingTypeParamRef(b) == true) {
                        val bNonnull = b.withNullity(NonNull)
                        if (b != bNonnull) {
                            debug({ "Promoting $b to $bNonnull because of non-null upper|common bound" }) {
                                if (addBound(bNonnull, node, BoundKind.Upper)) {
                                    obviate(b, BoundKind.Upper)
                                }
                            }
                        }
                    }
                }
            }
        }

        // See if we can combine partial bounds into a full bound.
        if ((node.partialBounds.size + node.fullBounds.size) >= 2) {
            fun boundList(partialBounds: Set<PartialType>, fullBounds: Set<Type2>) = buildList {
                addAll(fullBounds)
                partialBounds.mapTo(this) {
                    (node(it).choice as? Type2) ?: it
                }
            }
            val lowers = boundList(node.partialBounds.lowerBounds, node.fullBounds.lowerBounds)
            val common = boundList(node.partialBounds.commonBounds, node.fullBounds.commonBounds)
            val uppers = boundList(node.partialBounds.upperBounds, node.fullBounds.upperBounds)
            debug({ "Reconciliations for ${node.bound}, lowers=$lowers, common=$common, uppers=$uppers:" }) {
                val reconciliations = reconcilePartialTypes(
                    lowers = lowers,
                    common = common,
                    uppers = uppers,
                    typeContext = typeContext,
                )
                reconciliations.forEach {
                    debug { "- $it" }
                }
                for (r in reconciliations) {
                    when (r) {
                        is PartialTypeReconciliation -> {
                            val (variable, reconciliation, boundKind) = r
                            val node = node(variable)
                            addBound(reconciliation, node, boundKind)
                        }

                        is VarReconciliation -> {
                            val (a, b, kind) = r
                            val (aBound, bBound) =
                                if (a.nullity == NonNull && b.nullity == NonNull) {
                                    a.typeVar to b.typeVar
                                } else {
                                    a to b
                                }
                            val c = typeConstraintFor(aBound, bBound, kind)
                            debug { "VarReconciliation $r led to $c" }
                            addConstraint(c)
                        }
                    }
                }
            }
        }

        // With a full bound we can always do one of choose, delay, or contradict.
        val fullBounds = node.fullBounds
        if (fullBounds.isNotEmpty()) {
            val common = fullBounds.commonBounds
            if (common.isNotEmpty()) {
                choose(node, common.first())
            }
        }
    }

    private fun choose(node: TypeNode, choice: TypeSolution) {
        debug({ "Choosing $choice for $node" }) {
            val bound = node.bound
            check(bound !is PartialType || choice !is Type2 || bound.definition == choice.definition) {
                buildString {
                    append("Choosing wrong definition $choice for $bound")
                    append("\nConstraints")
                    for (c in constraints) {
                        append("\n  - $c")
                    }
                    append("\nSolutions")
                    for ((k, v) in allSolutions) {
                        append("\n  - $k -> $v")
                    }
                }
            }

            check(node.choice == null)
            node.choice = choice
            if (choice is Type2 && bound is PartialType) {
                // Promote partial bounds to full bounds by looking at adjacent constraints
                val adjacentUnsolvedNodes = mutableSetOf<TypeNode>()
                for (c in adjacencyTable[bound] ?: emptySet()) {
                    for (b in c.bounds()) {
                        val bNode = node(b)
                        if (bNode is TypeNode && bNode.choice == null) {
                            adjacentUnsolvedNodes.add(bNode)
                        }
                    }
                }
                for (adjacentUnsolvedNode in adjacentUnsolvedNodes) {
                    val partialBounds = adjacentUnsolvedNode.partialBounds
                    val mutPartialBounds = partialBounds as MutBoundSet
                    val bk: BoundKind? = when (bound) {
                        in partialBounds.commonBounds -> BoundKind.Common
                        in partialBounds.lowerBounds -> BoundKind.Lower
                        in partialBounds.upperBounds -> BoundKind.Upper
                        else -> null
                    }
                    if (bk != null) {
                        addBound(choice, adjacentUnsolvedNode, bk)
                        mutPartialBounds.obviate(bound, bk)
                        debug { ". promoted bound $bound/$bk in ${adjacentUnsolvedNode.bound} to $choice" }
                    }
                }
            }
            afterChosen(node)
        }
    }

    private fun choose(node: SimpleNode, choice: Solution) {
        debug({ "Choosing $choice for $node" }) {
            check(node.choice == null)
            node.choice = choice
            afterChosen(node)
        }
    }

    private fun afterChosen(node: SolveNode) {
        adjacencyTable.remove(node.bound)?.let { adjacentConstraints ->
            dirtyConstraints.addAll(adjacentConstraints)
        }
        satisfiedButNotSolved.remove(node.bound)
        node.solvedAtStamp = nextChangeStamp()
        progressMade = true
    }

    private fun partialBoundFor(
        typeShape: TypeShape,
        nullity: Nullity = NonNull,
        prefixes: List<String?> = emptyList(),
    ): TypeOrPartialType =
        PartialType.from(
            typeShape,
            typeShape.formals.mapIndexed { i, typeFormal ->
                val prefix = prefixes.getOrNull(i)
                    ?: "${typeShape.word?.text ?: "_"}_${typeFormal.word?.text ?: "_"}"
                TypeVarRef(unusedTypeVar(prefix), NonNull)
            },
            nullity,
        )

    private fun addBound(bound: TypeOrPartialType, to: TypeNode, kind: BoundKind): Boolean =
        when (bound) {
            is Type2 -> addBound(bound, to, kind)
            is PartialType -> addBound(bound, to, kind)
        }

    private fun addBound(bound: Type2, to: TypeNode, kind: BoundKind): Boolean {
        if (bound.definition == invalidTypeDefinition) { return false }
        val changed = addBoundHelper(bound, to.fullBounds, kind, to.bound)
        if (changed) {
            satisfiedButNotSolved.add(to.bound)
        }
        return changed
    }

    private fun addBound(bound: PartialType, to: TypeNode, kind: BoundKind): Boolean {
        val partialBounds = to.partialBounds
        val changed = addBoundHelper(bound, partialBounds, kind, to.bound)
        if (changed && bound != to.bound) {
            // Check whether we need a type constraint so that `to` gets checked
            // when bound resolves.
            val tc = when {
                partialBounds.commonBounds.contains(bound) -> SameTypeConstraint(to.bound, bound)
                partialBounds.lowerBounds.contains(bound) -> SubTypeConstraint(bound, to.bound)
                partialBounds.upperBounds.contains(bound) -> SubTypeConstraint(to.bound, bound)
                else -> null
            }
            tc?.let { addConstraint(it) }
        }
        return changed
    }

    private fun <T : TypeBoundary> addBoundHelper(
        bound: T,
        to: BoundSet<T>,
        kind: BoundKind,
        origin: TypeBoundary,
    ): Boolean =
        (to as MutBoundSet).addBound(bound, kind).also { changed ->
            if (changed) {
                debug { "Added $kind bound $bound to $origin" }
                progressMade = true
                boundsChanged.add(bound)
                to.lastChangedStamp = nextChangeStamp()
                adjacencyTable[origin]?.let { adjacent ->
                    dirtyConstraints.addAll(adjacent)
                }
            }
        }

    private fun <T : TypeOrPartialType> copyBoundsAs(
        from: BoundSet<T>,
        to: TypeNode,
        toKind: BoundKind,
        shouldCopy: (T, BoundKind) -> Boolean,
    ): Boolean {
        var changed = false
        for ((b, k) in from) {
            if (shouldCopy(b, k) && addBound(b, to, toKind)) {
                changed = true
            }
        }
        return changed
    }

    private fun <T : TypeOrPartialType> copyBounds(
        from: BoundSet<T>,
        to: TypeNode,
    ): Boolean {
        var changed = false
        for (b in from.commonBounds) {
            if (addBound(b, to, BoundKind.Common)) {
                changed = true
            }
        }
        for (b in from.lowerBounds) {
            if (addBound(b, to, BoundKind.Lower)) {
                changed = true
            }
        }
        for (b in from.upperBounds) {
            if (addBound(b, to, BoundKind.Upper)) {
                changed = true
            }
        }
        return changed
    }

    private fun typeConstraintFor(a: TypeBoundary, b: TypeBoundary, kind: BoundKind) =
        when (kind) {
            BoundKind.Upper -> SubTypeConstraint(a, b)
            BoundKind.Lower -> SubTypeConstraint(b, a)
            BoundKind.Common -> SameTypeConstraint(a, b)
        }

    fun unusedTypeVar(hint: String) = solverVarNamer.unusedTypeVar(hint)
    fun unusedSimpleVar(hint: String) = solverVarNamer.unusedSimpleVar(hint)

    // Support for debug hook below
    private fun maybeDumpToDebugHook() {
        debugHook?.let { debugHook ->
            debugHook.beginStep()
            nodes.forEach { (_, v) ->
                debugHook.node(v.forDebugHook())
            }
            constraints.forEach { c ->
                c.sendToDebugHook(isDirty = c in dirtyConstraints)
            }
        }
    }

    private val debugHookSupport = lazy { DebugHookSupport() }
    private inner class DebugHookSupport {
        val debugHook = this@TypeSolver.debugHook!! // Only instantiated if safe

        /** Maps SolveNodes and Constraints to unique Graphviz IDs */
        private val debugHookNodeKeys = mutableMapOf<Any, String>()

        private fun nodeKey(node: SolveNode): String = debugHookNodeKeys.getOrPut(node) {
            "N${debugHookNodeKeys.size}"
        }

        private fun nodeKey(boundary: TypeBoundary): String = nodeKey(node(boundary))

        private fun nodeKey(solvable: Solvable): String = nodeKey(node(solvable))

        private fun nodeKey(c: Constraint): String = debugHookNodeKeys.getOrPut(c) {
            "C${debugHookNodeKeys.size}"
        }

        /** Create a Graphviz node corresponding to an internal solver node. */
        fun nodeForSolveNode(node: SolveNode): TypeSolverDebugHook.DebugHookNode {
            return object : TypeSolverDebugHook.DebugHookNode {
                override val key: String = nodeKey(node)
                override val solution: TokenSerializable? = node.choice?.let { choice ->
                    if (choice is TypeParamRef && choice == node.bound) {
                        // It helps to see the upper bounds on type variables.
                        TokenSerializable {
                            choice.renderTo(it)
                            it.word("extends")
                            choice.definition.upperBounds.join(it)
                        }
                    } else {
                        choice
                    }
                }
                override val upperBounds = buildList {
                    (node as? TypeNode)?.run {
                        addAll(fullBounds.upperBounds)
                        addAll(partialBounds.upperBounds)
                    }
                }
                override val commonBounds = buildList {
                    (node as? TypeNode)?.run {
                        addAll(fullBounds.commonBounds)
                        addAll(partialBounds.commonBounds)
                    }
                }
                override val lowerBounds = buildList {
                    (node as? TypeNode)?.run {
                        addAll(fullBounds.lowerBounds)
                        addAll(partialBounds.lowerBounds)
                    }
                }
                override val description: TokenSerializable
                    get() = node.bound
                override val details: TokenSerializable?
                    get() = null
                override val styles = TypeSolverDebugHook.DebugHookStyles()
            }
        }

        /** Create a Graphviz edge with a textual label */
        private fun debugHookEdge(
            aKey: String,
            bKey: String,
            dir: ForwardOrBack?,
            label: String,
            isDirty: Boolean = false,
        ) = debugHookEdge(aKey, bKey, dir, OutputToken(label, OutputTokenType.Word), isDirty = isDirty)

        /** Create a Graphviz edge */
        private fun debugHookEdge(
            aKey: String,
            bKey: String,
            dir: ForwardOrBack?,
            description: TokenSerializable?,
            isDirty: Boolean = false,
        ) {
            this@TypeSolver.debugHook!!.edge(
                object : TypeSolverDebugHook.DebugHookEdge {
                    override val aNodeKey = aKey
                    override val bNodeKey = bKey
                    override val dir = dir
                    override val description = description
                    override val styles = TypeSolverDebugHook.DebugHookStyles(isDirty = isDirty)
                },
            )
        }

        /**
         * Some constraints have more than two bounds, so we create a Graphviz node for
         * them and have each bound fan to it.
         */
        private fun sendNodeForConstraint(
            c: Constraint,
            label: String,
            details: TokenSerializable? = null,
            isDirty: Boolean,
        ): String = sendNodeForConstraint(
            c,
            OutputToken(label, OutputTokenType.Word),
            details,
            isDirty = isDirty,
        )

        private fun sendNodeForConstraint(
            c: Constraint,
            description: TokenSerializable,
            details: TokenSerializable? = null,
            isDirty: Boolean,
        ): String {
            val cnKey = nodeKey(c)
            debugHook.node(
                object : TypeSolverDebugHook.DebugHookNode {
                    override val key: String = cnKey
                    override val solution = null
                    override val upperBounds get() = emptyList<TokenSerializable>()
                    override val commonBounds get() = emptyList<TokenSerializable>()
                    override val lowerBounds get() = emptyList<TokenSerializable>()
                    override val description = description
                    override val details = details
                    override val styles = TypeSolverDebugHook.DebugHookStyles(isDirty = isDirty)
                },
            )
            return cnKey
        }

        /**
         * Add graphviz elements for the given constraint.
         */
        fun sendConstraint(c: Constraint, isDirty: Boolean) {
            when (c) {
                is PutConstraint -> {
                    val cnKey = sendNodeForConstraint(c, "put", isDirty = isDirty)
                    debugHookEdge(cnKey, nodeKey(c.receiver), ForwardOrBack.Forward, null)
                    c.parts.forEachIndexed { i, b ->
                        val desc = OutputToken("#$i", OutputTokenType.OtherValue)
                        debugHookEdge(nodeKey(b), cnKey, ForwardOrBack.Forward, desc)
                    }
                }
                is SameTypeConstraint -> debugHookEdge(
                    nodeKey(c.a), nodeKey(c.b), null, OutToks.eqEq, isDirty = isDirty,
                )
                is SubTypeConstraint -> debugHookEdge(
                    nodeKey(c.sub), nodeKey(c.sup), ForwardOrBack.Forward, subTypeOrEqualTok, isDirty = isDirty,
                )
                is BivariantConstraint -> debugHookEdge(
                    nodeKey(c.a), nodeKey(c.b), null, bivariantMehTok, isDirty = isDirty,
                )
                is UsesConstraint -> {
                    val cnKey = sendNodeForConstraint(c, OutputToken("uses", OutputTokenType.Word), isDirty = isDirty)
                    debugHookEdge(nodeKey(c.typeLike), cnKey, ForwardOrBack.Forward, null)
                    c.typeVars.forEach { v ->
                        debugHookEdge(
                            cnKey,
                            nodeKey(v),
                            ForwardOrBack.Forward,
                            null,
                        )
                    }
                }
                is CallConstraint -> {
                    val callees = c.callees
                    val rejected = callConstraintStates[c]?.rejectedCallees
                    val details = TokenSerializable { tokenSink ->
                        var nEmitted = 0
                        for ((i, callee) in callees.withIndex()) {
                            if (rejected?.contains(i) == true) { continue }
                            if (nEmitted != 0) {
                                tokenSink.infixOp("∩")
                            }
                            callee.renderTo(tokenSink)
                            nEmitted += 1
                        }
                        if (nEmitted == 0) {
                            tokenSink.word("no")
                            tokenSink.word("valid")
                            tokenSink.word("callee")
                        }
                    }
                    val cnKey = sendNodeForConstraint(c, "call", details, isDirty = isDirty)
                    debugHookEdge(cnKey, nodeKey(c.callPass), null, "passResult")
                    c.callFail?.let { varName ->
                        debugHookEdge(cnKey, nodeKey(varName), null, "failResult")
                    }
                    c.typeActuals?.let { varName ->
                        debugHookEdge(cnKey, nodeKey(varName), null, "typeActualsList")
                    }
                    c.explicitTypeArgs?.forEachIndexed { i, varName ->
                        debugHookEdge(cnKey, nodeKey(varName), null, "explicitTypeArg#$i")
                    }
                    c.args.forEachIndexed { i, varName ->
                        debugHookEdge(cnKey, nodeKey(varName), null, "arg#$i")
                    }
                    debugHookEdge(cnKey, nodeKey(c.calleeChoice), null, "calleeChoice")
                }
            }
        }
    }

    private fun Constraint.sendToDebugHook(isDirty: Boolean) {
        debugHookSupport.value.sendConstraint(this, isDirty = isDirty)
    }

    private fun SolveNode.forDebugHook(): TypeSolverDebugHook.DebugHookNode =
        debugHookSupport.value.nodeForSolveNode(this)
}

internal enum class BoundKind {
    Upper,
    Lower,
    Common,

    ;

    fun reverse() = when (this) {
        Upper -> Lower
        Lower -> Upper
        Common -> Common
    }
}

/**
 * For different kinds of bounds, we track upper and lower bounds.
 *
 * Having these bounds separated out lets us detect when a bound is present
 * in both lower and upper sets letting us efficiently collapse groups of
 * co-bounding type variables into one.
 *
 * It also lets us iterate over bounds separately.
 * For example, for a lower bound and an upper bound, we know that the
 * actual type solution is in-between (non-strictly) which is not true
 * for two lower bounds; for two lower bounds, which is closer to the
 * actual is unclear.
 */
private interface BoundSet<BOUND> : Iterable<Pair<BOUND, BoundKind>> {
    val size: Int

    operator fun contains(p: Pair<BOUND, BoundKind>): Boolean

    fun isEmpty() = size == 0
    fun isNotEmpty() = !isEmpty()

    val lowerBounds: Set<BOUND>
    val upperBounds: Set<BOUND>
    val commonBounds: Set<BOUND>

    val lastChangedStamp: ChangeStamp

    companion object {
        operator fun <BOUND> invoke(): BoundSet<BOUND> = MutBoundSet()
    }
}

/**
 * Every [BoundSet] is a [MutBoundSet] but we separate out the two
 * interfaces to channel changes to bound sets through code
 * paths that do necessary bookkeeping.
 */
private class MutBoundSet<BOUND> : BoundSet<BOUND> {
    private val lower = mutableSetOf<BOUND>()
    private val upper = mutableSetOf<BOUND>()
    private val common = mutableSetOf<BOUND>()
    private val obviated = mutableSetOf<Pair<BOUND, BoundKind>>()

    override val size get() = lower.size + upper.size + common.size

    // Typed as not mutable so all mutable access goes through addBound
    override val lowerBounds: Set<BOUND> get() = lower
    override val upperBounds: Set<BOUND> get() = upper
    override val commonBounds: Set<BOUND> get() = common
    override var lastChangedStamp: ChangeStamp = -1L

    /** @return true if changed */
    fun addBound(b: BOUND, k: BoundKind): Boolean {
        if (b in common) { return false }
        if ((b to k) in obviated) { return false } // Already superseded by a better kind of bound.
        var changed = false
        when (k) {
            BoundKind.Upper -> if (b !in upper) {
                if (b in lower) {
                    lower.remove(b)
                    common.add(b)
                } else {
                    upper.add(b)
                }
                changed = true
            }

            BoundKind.Lower -> if (b !in lower) {
                if (b in upper) {
                    upper.remove(b)
                    common.add(b)
                } else {
                    lower.add(b)
                }
                changed = true
            }

            BoundKind.Common -> { // in common checked above
                lower.remove(b)
                upper.remove(b)
                common.add(b)
                changed = true
            }
        }
        return changed
    }

    fun obviate(b: BOUND, k: BoundKind) {
        when (k) {
            BoundKind.Lower -> lower
            BoundKind.Common -> common
            BoundKind.Upper -> upper
        }.remove(b)
        obviated.add(b to k)
    }

    override operator fun contains(p: Pair<BOUND, BoundKind>): Boolean {
        val (b, k) = p
        when (k) {
            BoundKind.Upper -> if (b in upper) return true
            BoundKind.Lower -> if (b in lower) return true
            BoundKind.Common -> {}
        }
        return b in common
    }

    // A simple state machine using BoundKind to iterate all lowers,
    // then all uppers, then all common bounds
    private class BoundSetIterator<BOUND>(boundSet: MutBoundSet<BOUND>) : Iterator<Pair<BOUND, BoundKind>> {
        private var boundSet: MutBoundSet<BOUND>? = boundSet
        private var kind: BoundKind = BoundKind.Upper
        private var iter: Iterator<BOUND>? = boundSet.upper.iterator()

        override fun hasNext(): Boolean =
            checkIterator() != null

        override fun next(): Pair<BOUND, BoundKind> {
            val (it, k) = checkIterator() ?: throw NoSuchElementException()
            return it.next() to k
        }

        private fun checkIterator(): Pair<Iterator<BOUND>, BoundKind>? {
            while (true) {
                val iter = this.iter ?: return null
                if (iter.hasNext()) {
                    return iter to kind
                }

                // Set this.iter to the iterator for the next kind
                when (kind) {
                    BoundKind.Upper -> {
                        this.kind = BoundKind.Common
                        this.iter = boundSet!!.common.iterator()
                        continue
                    }
                    BoundKind.Common -> {
                        this.kind = BoundKind.Lower
                        this.iter = boundSet!!.lower.iterator()
                        continue
                    }
                    BoundKind.Lower -> {
                        this.iter = null
                        this.boundSet = null // Release for GC
                        return null
                    }
                }
            }
        }
    }

    override fun iterator(): Iterator<Pair<BOUND, BoundKind>> =
        BoundSetIterator(this)

    override fun toString(): String = buildString {
        append("(BoundSet")
        var sep = ""
        if (upper.isNotEmpty()) {
            append(sep)
            append(" upper=")
            upper.joinTo(this, separator = ", ", prefix = "[", postfix = "]")
            sep = ","
        }
        if (lower.isNotEmpty()) {
            append(sep)
            append(" lower=")
            lower.joinTo(this, separator = ", ", prefix = "[", postfix = "]")
            sep = ","
        }
        if (common.isNotEmpty()) {
            append(sep)
            append(" common=")
            common.joinTo(this, separator = ", ", prefix = "[", postfix = "]")
        }
        append(")")
    }
}

internal fun PartialType.typeVarsUsed(): Set<TypeVar> =
    buildSet {
        fun scan(partialType: PartialType) {
            for (typeParam in partialType.bindings) {
                when (typeParam) {
                    is TypeVarRef -> add(typeParam.typeVar)
                    is PartialType -> scan(typeParam)
                    is Type2 -> {}
                }
            }
        }
        scan(this@typeVarsUsed)
    }

/** `Never<X> -> X ; Never<X>? -> X?` */
internal fun stripNever(type: Type2): Type2 {
    var t = type
    if (type.definition == neverTypeDefinition && type.bindings.size == 1) {
        val u = type.bindings[0]
        t = if (type.nullity == OrNull) {
            u.withNullity(OrNull)
        } else {
            u
        }
    }
    return t
}

private val anyValueType2 = MkType2(WellKnownTypes.anyValueTypeDefinition).get()

private fun correspondingFormal(actualIndex: Int, applicationOrder: List<Int?>): Int {
    if (applicationOrder is IdentityActualOrder) { return actualIndex }
    return applicationOrder.indexOf(actualIndex)
}

private fun maybeUnwrapNever(t: TypeOrPartialType?): TypeLike? {
    if (t?.definition == neverTypeDefinition) {
        return t.bindings.firstOrNull()
    }
    return t
}

private fun maybeUnwrapPass(t: TypeOrPartialType?): TypeLike? {
    if (t?.definition == resultTypeDefinition) {
        return t.bindings.firstOrNull()
    }
    return t
}
