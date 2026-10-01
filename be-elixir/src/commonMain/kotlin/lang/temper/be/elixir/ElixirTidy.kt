package lang.temper.be.elixir

import lang.temper.name.OutName

/**
 * Leaves the generated Elixir compiling without warnings that mean nothing
 * to its reader. Before this, translated std printed about 200 of them on
 * every build:
 *
 * - **Unused variables.** A loop or branch hands back every variable it
 *   assigns (`{i, total} = loop.(...)`), and a temporary can be bound and
 *   never read. Elixir warns about each binding that nothing reads, so a
 *   backward liveness pass, scoped as Elixir scopes (bindings inside `if`,
 *   `case`, `cond`, `try` and `fn` do not leak), gives every unread binding
 *   Elixir's `_` prefix.
 * - **A binding of a raise.** `t1 = raise(TemperCore.Panic)` binds the result
 *   of something that never returns, which Elixir's type checker reports
 *   as a pattern that will never match. It becomes the raise alone.
 */
internal fun tidy(file: Elixir.SourceFile) {
    file.items.forEach(::tidyTopLevel)
}

private fun tidyTopLevel(item: Elixir.Tree) {
    when (item) {
        is Elixir.ModuleDef -> item.items.forEach(::tidyTopLevel)
        is Elixir.FunDef -> tidyFunction(item)
        else -> {}
    }
}

private fun tidyFunction(fn: Elixir.FunDef) {
    dropRaiseBindings(fn)
    // a function's parameters are bound once, for the whole body
    bindAll(fn.params, live(fn.body, setOf()) + (fn.guard?.let(::reads) ?: setOf()))
}

/**
 * Backward liveness over one block, as Elixir scopes it: walking from the
 * end, a binding is unused when nothing after it reads the name before it
 * is bound again. Answers the names the block reads before binding them.
 */
private fun live(block: Elixir.Block, liveOut: Set<String>): Set<String> {
    val live = liveOut.toMutableSet()
    for (item in block.exprs.asReversed()) {
        if (item is Elixir.Match) {
            val bound = patternIds(item.left)
            prefixUnused(bound, live)
            live.removeAll(bound.map { it.outName.outputNameText }.toSet())
            live.addAll(pinned(item.left))
            live.addAll(reads(item.right))
        } else {
            live.addAll(reads(item))
        }
    }
    return live
}

/**
 * The names an expression reads. Variables bound inside an `if`, `case`,
 * `cond`, `try` or `fn` do not leak out of it, so each inner block is
 * analysed with nothing live after it.
 */
private fun reads(node: Elixir.Tree): Set<String> = when (node) {
    is Elixir.Id -> setOf(node.outName.outputNameText)
    is Elixir.Block -> live(node, setOf())
    is Elixir.If -> reads(node.test) + live(node.then, setOf()) + (node.otherwise?.let { live(it, setOf()) } ?: setOf())
    is Elixir.Case -> reads(node.subject) + node.clauses.flatMap(::clauseReads)
    is Elixir.Cond -> node.arms.flatMap { reads(it.test) + live(it.body, setOf()) }.toSet()
    is Elixir.Try -> live(node.body, setOf()) + (node.rescues + node.catches).flatMap(::clauseReads)
    is Elixir.Fn -> bindAll(node.params, live(node.body, setOf()))
    // a match used as an expression: its right side is read, its bindings kept
    is Elixir.Match -> pinned(node.left) + reads(node.right)
    // names here are functions, fields and modules, not variables
    is Elixir.Call -> node.args.flatMap(::reads).toSet()
    is Elixir.RemoteCall -> reads(node.module) + node.args.flatMap(::reads)
    is Elixir.Field -> reads(node.obj)
    is Elixir.Capture, is Elixir.ModuleName, is Elixir.StructDef -> setOf()
    is Elixir.KeywordEntry -> reads(node.value)
    else -> (0 until node.childCount).flatMap { i -> node.childOrNull(i)?.let(::reads) ?: setOf() }.toSet()
}

private fun clauseReads(clause: Elixir.Clause): Set<String> {
    val inside = live(clause.body, setOf()) + (clause.guard?.let(::reads) ?: setOf())
    return bindAll(listOf(clause.pattern), inside)
}

/** Binds `patterns` over a scope whose reads are `inside`; answers what is read from outside. */
private fun bindAll(patterns: List<Elixir.Pattern>, inside: Set<String>): Set<String> {
    val bound = patterns.flatMap(::patternIds)
    prefixUnused(bound, inside)
    return inside - bound.map { it.outName.outputNameText }.toSet() + patterns.flatMap(::pinned)
}

private fun prefixUnused(bound: List<Elixir.Id>, live: Set<String>) {
    for (id in bound) {
        val text = id.outName.outputNameText
        if (text !in live && !text.startsWith("_")) id.outName = OutName("_$text", id.outName.sourceName)
    }
}

/** The variables a pattern binds: every Id in it but those under a pin. */
private fun patternIds(pattern: Elixir.Tree): List<Elixir.Id> = when (pattern) {
    is Elixir.Id -> listOf(pattern)
    is Elixir.Pin, is Elixir.ModuleName, is Elixir.Atom -> listOf()
    is Elixir.KeywordEntry -> patternIds(pattern.value)
    else -> (0 until pattern.childCount).flatMap { i -> pattern.childOrNull(i)?.let(::patternIds) ?: listOf() }
}

/** What a pattern reads: the variables under a pin, `^x`. */
private fun pinned(pattern: Elixir.Tree): Set<String> = when (pattern) {
    is Elixir.Pin -> setOf(pattern.id.outName.outputNameText)
    else -> (0 until pattern.childCount).flatMap { i -> pattern.childOrNull(i)?.let(::pinned) ?: setOf() }.toSet()
}

/** `x = raise(...)` in any block becomes `raise(...)`: a raise has no value to bind. */
private fun dropRaiseBindings(node: Elixir.Tree) {
    if (node is Elixir.Block && node.exprs.any(::isRaiseBinding)) {
        node.exprs = node.exprs.map { item ->
            if (isRaiseBinding(item)) (item as Elixir.Match).right.deepCopy() else item
        }
    }
    for (i in 0 until node.childCount) node.childOrNull(i)?.let(::dropRaiseBindings)
}

private fun isRaiseBinding(item: Elixir.BlockItem): Boolean {
    val match = item as? Elixir.Match ?: return false
    val call = match.right as? Elixir.Call ?: return false
    return call.callee.outName.outputNameText == "raise"
}
