package lang.temper.value

import lang.temper.common.Console
import lang.temper.common.LeftOrRight
import lang.temper.common.toStringViaTextOutput
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.InternalModularName
import lang.temper.name.Symbol

/**
 * An index into a [BlockTree]'s child list that can be adjusted.
 */
class BlockChildReference(
    index: Int?,
    override val pos: Position,
) : Positioned {
    var index: Int? = index
        internal set

    fun overrideIndex(newIndex: Int) {
        this.index = newIndex
    }

    fun copy() = BlockChildReference(index, pos)

    override fun equals(other: Any?): Boolean =
        other is BlockChildReference && this.index == other.index

    override fun hashCode(): Int = index ?: 0

    override fun toString() = "ref:$index"
}

/**
 * In `some_label: do { break some_label }`, `some_label` is used to specify
 * which block the `break` is breaking out of, and on the block to establish
 * that correspondence.
 *
 * A jump label is normally resolved to a [lang.temper.name.SourceName]
 * but macros may use a temporary for hygiene purposes if they do not
 * want a labeled statement to be a target for unresolved `break`s from
 * inlined block lambdas
 */
typealias JumpLabel = InternalModularName

/**
 * Specifies where a [ControlFlow.Jump] is going.
 *
 * In `break` and `continue`, the jump is to some
 * [default location][DefaultJumpSpecifier] based on narrowest containing.
 *
 * In `break some_label` and `continue some_label`, the jump is to a name that
 * requires resolution to a name in the same containing function/module.
 */
sealed class JumpSpecifier

/**
 * Each [loop][ControlFlow.Loop] establishes a default jump target for
 * [ControlFlow.Break] and [ControlFlow.Continue].
 */
object DefaultJumpSpecifier : JumpSpecifier() {
    override fun toString(): String = "default"
}

/** A jump specifier that has been resolved. */
data class NamedJumpSpecifier(
    val label: JumpLabel,
) : JumpSpecifier() {
    override fun toString(): String = "$label"
}

/**
 * `break some_label` holds unto `some_label` until we can resolve the name.
 */
data class UnresolvedJumpSpecifier(
    val symbol: Symbol,
) : JumpSpecifier() {
    override fun toString(): String = "?$symbol?"
}

enum class BreakOrContinue {
    Break,
    Continue,
}

val ControlFlow.Jump.jumpKind get() = when (this) {
    is ControlFlow.Break -> BreakOrContinue.Break
    is ControlFlow.Continue -> BreakOrContinue.Continue
}

// Making JumpDestination extend ControlFlow is awkward, but having a
// super-type for JumpDestination makes Kotlin's typer aware of all
// kinds of potential conflation of ControlFlow's with their containers.
sealed interface IControlFlow : Positioned

/** A [ControlFlow] whose boundaries may be the target of a [ControlFlow.Jump] */
sealed interface JumpDestination : IControlFlow {
    /** The label used for any labeled `break` that targets this. */
    val breakLabel: JumpLabel?

    /** The label used for any labeled `continue` that targets this. */
    val continueLabel: JumpLabel?

    /** Whether an unlabeled `break` or `continue` might target this */
    val isDefaultBreakTarget: Boolean

    /** Whether an unlabeled `break` or `continue` might target this */
    val isDefaultContinueTarget: Boolean
}
fun JumpDestination.matches(jump: ControlFlow.Jump): Boolean = matches(
    jump.jumpKind,
    jump.target,
)

fun JumpDestination.matches(kind: BreakOrContinue, target: JumpSpecifier): Boolean {
    val label = when (kind) {
        BreakOrContinue.Break -> {
            if (this.isDefaultBreakTarget && target is DefaultJumpSpecifier) {
                return true
            }
            breakLabel
        }
        BreakOrContinue.Continue -> {
            // Continues only intercepted by loops
            if (this.isDefaultContinueTarget && target is DefaultJumpSpecifier) {
                return true
            }
            continueLabel
        }
    }
    if (label != null) {
        val labelMatches = when (target) {
            DefaultJumpSpecifier -> false
            is NamedJumpSpecifier -> label == target.label
            is UnresolvedJumpSpecifier -> return label.toSymbol() == target.symbol
        }
        if (labelMatches) { return true }
    }
    return false
}

/**
 * A structured programming control-flow statement.
 *
 * These have limited mutability.  All [BlockChildReference]s
 * that are statements appear in [Stmt] nodes that are children
 * of [StmtBlock] which have mutable child lists, so it's possible
 * to insert instructions before and after any statement.
 * Also, [BlockChildReference]s are mutable themselves so conditions
 * may be adjusted in coordination with the containing [BlockTree]'s
 * child list.
 * (The [deepCopy] operator copies references)
 *
 * These are created by builtin macros like `if` and `while` and
 * later weaved together when statements are pulled to the root of
 * the containing function/module body so that the translator
 * can support non-expression-languages.
 */
sealed class ControlFlow : IControlFlow {
    private var _parent: ControlFlow? = null
    abstract fun deepCopy(): ControlFlow

    // These properties allow generic iteration and introspection of content
    open val ref: BlockChildReference? get() = null
    abstract val clauses: Iterable<ControlFlow>
    open val parent: ControlFlow? get() = _parent

    protected fun adopt(child: ControlFlow) {
        check(child._parent == null) {
            toStringViaTextOutput { dumpControlFlow(child, Console(it)) }.trimEnd()
        }
        child._parent = this
    }

    protected fun unAdopt(child: ControlFlow) {
        check(child._parent == this)
        child._parent = null
    }

    sealed class Conditional : ControlFlow() {
        abstract val condition: BlockChildReference
        override val ref: BlockChildReference get() = condition
    }

    /** A single statement */
    data class Stmt(
        override val ref: BlockChildReference,
    ) : ControlFlow() {
        override val pos: Position get() = ref.pos
        override fun deepCopy() = Stmt(ref.copy())
        override val clauses: Iterable<ControlFlow> get() = listOf()
        override val parent: StmtBlock? get() = super.parent as StmtBlock?
    }

    /** A jump from the current location to a location in the same containing function/module. */
    sealed class Jump(
        override val pos: Position,
        var target: JumpSpecifier,
    ) : ControlFlow() {
        override val clauses: Iterable<ControlFlow> get() = listOf()
        abstract override fun deepCopy(): Jump
    }

    /** Jumps to just after the end of the specified loop/block. */
    class Break(
        pos: Position,
        target: JumpSpecifier,
    ) : Jump(pos, target) {
        override fun deepCopy() = Break(pos, target)
        override fun toString() = "Break($target)"
    }

    /** Jumps to just before the start of the specified loop. */
    class Continue(
        pos: Position,
        target: JumpSpecifier,
    ) : Jump(pos, target) {
        override fun deepCopy() = Continue(pos, target)
        override fun toString() = "Continue($target)"
    }

    /** A run of control flow statements executed in order. */
    class StmtBlock(
        override val pos: Position,
        stmts: List<ControlFlow>,
    ) : ControlFlow() {
        var stmts: List<ControlFlow> = emptyList()
            private set

        init {
            withMutableStmtList { it.addAll(stmts) }
        }

        fun <T> withMutableStmtList(f: (MutableList<ControlFlow>) -> T): T {
            for (child in stmts) {
                unAdopt(child)
            }
            val mutableStmts = stmts.toMutableList()
            val result = f(mutableStmts)
            val newStmtList = mutableStmts.toList()
            for (child in newStmtList) {
                adopt(child)
            }
            this.stmts = newStmtList
            return result
        }

        override fun deepCopy(): StmtBlock {
            val copy = StmtBlock(pos, listOf())
            copy.withMutableStmtList { copyStmts ->
                this.stmts.forEach { copyStmts.add(it.deepCopy()) }
            }
            return copy
        }
        override val clauses: Iterable<ControlFlow> get() = stmts

        companion object {
            fun wrap(controlFlow: ControlFlow): StmtBlock = if (controlFlow is StmtBlock) {
                controlFlow
            } else {
                StmtBlock(controlFlow.pos, mutableListOf(controlFlow))
            }
        }
    }

    /**
     * A block that establishes a [Break] target and optionally a [Continue] target
     * that may be jumped to by contained statements.
     *
     * Normal labeled blocks in languages provide [Break] targets only.
     * This construct also has an optional [continueLabel].  If provided, then this
     * labeled block intercepts [Continue]s to that label and default continues so
     * may serve to allow inserting instructions after the loop body that get run
     * on continuation.  The [continueLabel] is simplified out when we know enough
     * to rewrite the [Continue]s it contains to [Break]s.
     */
    class Labeled(
        override val pos: Position,
        override val breakLabel: JumpLabel,
        override val continueLabel: JumpLabel?,
        val stmts: StmtBlock,
    ) : ControlFlow(), JumpDestination {
        init {
            adopt(stmts)
        }
        override val clauses: Iterable<ControlFlow> get() = listOf(stmts)
        override fun deepCopy() = Labeled(
            pos,
            breakLabel,
            continueLabel,
            stmts.deepCopy(),
        )
        override val isDefaultBreakTarget get() = false
        override val isDefaultContinueTarget: Boolean get() = continueLabel != null
    }

    /** A conditional branch */
    class If(
        override val pos: Position,
        override val condition: BlockChildReference,
        val thenClause: StmtBlock,
        val elseClause: StmtBlock,
    ) : Conditional() {
        init {
            adopt(thenClause)
            adopt(elseClause)
        }

        override fun deepCopy() = If(
            pos,
            condition.copy(),
            thenClause.deepCopy(),
            elseClause.deepCopy(),
        )

        override val clauses get() = listOf(thenClause, elseClause)
    }

    /** A loop that is reentered while the condition is true. */
    class Loop(
        override val pos: Position,
        val label: JumpLabel?,
        /**
         * Whether the condition is checked before (left) or after (right)
         * the first run of the body.
         *
         * [LeftOrRight.Left] is suitable for `for` and `while` loops.
         * [LeftOrRight.Right] is suitable for `do...while` loops.
         *
         * The meaning of [Continue] is to jump to the end of the body
         * before the [increment] and [condition].
         *
         * One can convert a `do` loop to a `while` loop:
         *
         *     do { body } while (cond);
         *
         *     // ->
         *
         *     while (true) { body; if(!cond) { break; } }
         *
         * But that requires converting [Continue]s in `body` to [Break]s.
         *
         *     do {
         *       if (f()) { continue; }
         *       g();
         *     while (c());
         *
         *     // ->
         *
         *     while (true) {
         *       continue_123: {
         *         if (f()) { break continue_123; }
         *         g();
         *       }
         *       if (!c()) { break; }
         *     }
         */
        val checkPosition: LeftOrRight,
        /** Before each invocation of the body checks whether it should happen */
        override var condition: BlockChildReference,
        /** Executed after the condition evaluates to true */
        val body: StmtBlock,
        /** Executed after the body, and after the loop is [Continue]d. */
        val increment: StmtBlock,
    ) : Conditional(), JumpDestination {
        init {
            adopt(body)
            adopt(increment)
        }

        override fun deepCopy() = Loop(
            pos = pos,
            label = label,
            checkPosition = checkPosition,
            condition = condition.copy(),
            body = body.deepCopy(),
            increment = increment.deepCopy(),
        )
        override val clauses get() = listOf(body, increment)
        override val isDefaultBreakTarget get() = true
        override val isDefaultContinueTarget: Boolean get() = true
        override val breakLabel get() = label
        override val continueLabel get() = label
    }

    /**
     * Marks the boundaries of statements in an `orelse` clause to aid in converting to exception
     * catching for backends that need that.
     *
     * This allows us to be cautious about function calls like the below:
     *
     *     let f(g: Fn (): Void, h: Fn(): Void throws Bubble): Void {
     *       do {
     *         g(); // Does not bubble according to its type.
     *         h(); // Might bubble according to its type.
     *       } orelse do {
     *         console.log("Oops");
     *       }
     *     }
     *
     * It'd be good to still guard the `g()` call if some target-language code passed in a value
     * for `g` that throws an exception despite `g`'s type not allowing that.
     *
     * The or clause is a labeled statement block.  After weaving, any failures should
     * [Break] to [orClause]'s [label][Labeled.breakLabel] which means proceed to [elseClause].
     *
     * So an orelse is effectively like the below where `fail_label` is the label on [orClause]:
     *
     *     ok_label: do {
     *       fail_label: do {
     *         // Start orClause
     *         var fail: Boolean;
     *         hs(fail, operationThatMayFail());
     *         if (fail) { break fail_label; }
     *         break ok_label; // Leapfrog over else clause
     *       }
     *       // Start elseClause
     *       ...
     *     }
     */
    class OrElse(
        override val pos: Position,
        val orClause: Labeled,
        val elseClause: StmtBlock,
    ) : ControlFlow() {
        init {
            adopt(orClause)
            adopt(elseClause)
        }

        override fun deepCopy() = OrElse(
            pos = pos,
            orClause = orClause.deepCopy(),
            elseClause = elseClause.deepCopy(),
        )
        override val clauses get() = listOf(orClause, elseClause)
    }
}

fun dumpControlFlow(cf: ControlFlow, console: Console, blockTree: BlockTree? = null) {
    val desc = buildString {
        append(cf::class.simpleName ?: "???")
        val labels = when (val jd = cf as? JumpDestination) {
            null -> null
            is ControlFlow.Loop -> jd.label to null
            is ControlFlow.Labeled -> jd.breakLabel to jd.continueLabel
        }
        if (labels != null) {
            val (breakLabel, continueLabel) = labels
            append(" @ ")
            append(breakLabel)
            if (continueLabel != null) {
                append('&')
                append(continueLabel)
            }
        }
    }
    console.group(desc) {
        cf.ref?.let {
            console.log(
                buildString {
                    append("$it")
                    if (blockTree != null) {
                        val t = blockTree.dereference(it)?.target
                        append(": ")
                        if (t != null) {
                            append("`")
                            append(t.toPseudoCode(detail = PseudoCodeDetail(elideFunctionBodies = true)))
                            append("`")
                        } else {
                            append("???")
                        }
                    }
                },
            )
        }
        cf.clauses.forEach { dumpControlFlow(it, console, blockTree) }
    }
}
