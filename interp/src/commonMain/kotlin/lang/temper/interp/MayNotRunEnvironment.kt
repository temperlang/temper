package lang.temper.interp

import lang.temper.env.ChildEnvironment
import lang.temper.env.DeclarationBits
import lang.temper.env.DeclarationMetadata
import lang.temper.env.Environment
import lang.temper.name.TemperName
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.ControlFlow
import lang.temper.value.DefaultJumpSpecifier
import lang.temper.value.InterpreterCallback
import lang.temper.value.LinearFlow
import lang.temper.value.NameLeaf
import lang.temper.value.NamedJumpSpecifier
import lang.temper.value.PartialResult
import lang.temper.value.StructuredFlow
import lang.temper.value.Tree
import lang.temper.value.UnresolvedJumpSpecifier
import lang.temper.value.Value
import lang.temper.value.void

/**
 * Wraps the environment used to partially evaluate code that may run zero times, or many
 * times, each time control passes through the code that declares the names it assigns:
 * a function body, a branch of an `if`, a loop body, and so on.
 *
 * Partial evaluation visits each node once, so it visits the assignment in
 *
 *     let x: Int;
 *     if (b) { x = 1; }
 *     x
 *
 * whether or not `b` holds.  If that assignment reached the binding for `x`, the read
 * of `x` that follows would be inlined as `1`, and the read of an uninitialized
 * variable when `b` is false would be gone before anything checks for it.
 *
 * So an assignment made through this environment reaches the binding only when the name
 * was declared through this environment too.  Everything else passes through to the
 * wrapped environment: reads, declarations, and metadata lookups.
 */
internal class MayNotRunEnvironment(
    private val underlying: Environment,
) : ChildEnvironment(underlying) {
    private val declaredWithin = mutableSetOf<TemperName>()

    override fun localDeclarationMetadata(name: TemperName): DeclarationMetadata? = null

    @Suppress("AddOperatorModifier")
    override fun set(name: TemperName, newValue: Value<*>, cb: InterpreterCallback): PartialResult =
        if (name in declaredWithin) {
            underlying.set(name, newValue, cb)
        } else {
            // The assignment need not happen before any particular read of name, so leave
            // the binding alone.
            void
        }

    override fun declare(
        name: TemperName,
        declarationBits: DeclarationBits,
        cb: InterpreterCallback,
    ): PartialResult {
        declaredWithin.add(name)
        return underlying.declare(name, declarationBits, cb)
    }

    override val isLongLived: Boolean get() = underlying.isLongLived

    override val locallyDeclared: Iterable<TemperName> get() = emptyList()
}

/**
 * A part of a block's control flow that might not run, or might run more than once, each time
 * control passes through the [enclosing] part: a branch of an `if`, a loop, either side of an
 * `orelse`, or a labeled block that something breaks out of.
 */
internal class MayNotRunRegion(val enclosing: MayNotRunRegion?)

/**
 * Maps the indices of the children of [block], whose flow is [flow], that are in a
 * [MayNotRunRegion] to the innermost such region.  Children that run whenever the block's own
 * flow does are absent.
 */
internal fun mayNotRunRegions(block: BlockTree, flow: StructuredFlow): Map<Int, MayNotRunRegion> = buildMap {
    fun jumpsTo(cf: ControlFlow, labeled: ControlFlow.Labeled): Boolean = when (cf) {
        is ControlFlow.Stmt -> {
            // Before weaving, the jump may still be inside a statement: a nested block,
            // or a block lambda passed to `if`.
            val stmt = block.dereference(cf.ref)?.target
            stmt != null && (
                mayJumpTo(stmt, labeled.breakLabel) ||
                    labeled.continueLabel?.let { mayJumpTo(stmt, it) } == true
                )
        }
        is ControlFlow.Jump -> controlFlowJumpsTo(cf, labeled.breakLabel) ||
            labeled.continueLabel?.let { controlFlowJumpsTo(cf, it) } == true
        else -> cf.clauses.any { jumpsTo(it, labeled) }
    }

    fun walk(cf: ControlFlow, region: MayNotRunRegion?) {
        fun put(ref: BlockChildReference, r: MayNotRunRegion?) {
            val index = ref.index
            if (r != null && index != null) { this[index] = r }
        }
        when (cf) {
            is ControlFlow.Stmt -> put(cf.ref, region)
            is ControlFlow.Jump -> Unit
            is ControlFlow.StmtBlock -> cf.stmts.forEach { walk(it, region) }
            is ControlFlow.If -> {
                put(cf.condition, region)
                walk(cf.thenClause, MayNotRunRegion(region))
                walk(cf.elseClause, MayNotRunRegion(region))
            }
            is ControlFlow.Loop -> {
                // The condition may run many times, so it shares the body's region.
                val body = MayNotRunRegion(region)
                put(cf.condition, body)
                walk(cf.body, body)
                walk(cf.increment, body)
            }
            is ControlFlow.OrElse -> {
                walk(cf.orClause, MayNotRunRegion(region))
                walk(cf.elseClause, MayNotRunRegion(region))
            }
            is ControlFlow.Labeled -> walk(
                cf.stmts,
                if (jumpsTo(cf.stmts, cf)) MayNotRunRegion(region) else region,
            )
        }
    }
    walk(flow.controlFlow, null)
}

/**
 * True if [block] is a labeled block whose statements are not woven into a [StructuredFlow] yet,
 * and something inside it may break out of it, so its statements after that may not run.
 *
 * Before weaving, the `break` is often inside a block lambda:
 *
 *     lbl: do {
 *       if (b) { break lbl; }
 *       x = 1;
 *     }
 */
internal fun isLinearBlockThatMayBreak(block: BlockTree): Boolean {
    if (block.flow !is LinearFlow) { return false }
    val label = (block.parts.label?.target as? NameLeaf)?.content ?: return false
    // Start after the label itself.
    return (block.parts.startIndex until block.size).any {
        mayJumpTo(block.child(it), label)
    }
}

/**
 * True if anything in [tree] might jump to [label]: a mention of the label's name, as in
 * `break(\label, lbl)`, or a woven jump to it.
 */
private fun mayJumpTo(tree: Tree, label: TemperName): Boolean {
    if (tree is NameLeaf && tree.content == label) { return true }
    if (tree is BlockTree) {
        val flow = tree.flow
        if (flow is StructuredFlow && controlFlowJumpsTo(flow.controlFlow, label)) { return true }
    }
    return tree.children.any { mayJumpTo(it, label) }
}

private fun controlFlowJumpsTo(cf: ControlFlow, label: TemperName): Boolean = when (cf) {
    is ControlFlow.Jump -> when (val target = cf.target) {
        is NamedJumpSpecifier -> target.label == label
        // A default jump targets a loop, not a labeled block.
        DefaultJumpSpecifier -> false
        // Be conservative about what we cannot resolve.
        is UnresolvedJumpSpecifier -> true
    }
    else -> cf.clauses.any { controlFlowJumpsTo(it, label) }
}
