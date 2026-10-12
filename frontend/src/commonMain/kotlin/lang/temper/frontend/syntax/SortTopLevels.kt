package lang.temper.frontend.syntax

import lang.temper.common.Log
import lang.temper.common.buildSetMultimap
import lang.temper.common.partiallyOrder
import lang.temper.common.putMultiList
import lang.temper.common.putMultiSet
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.name.TemperName
import lang.temper.type.NominalType
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.importedSymbol
import lang.temper.value.initSymbol
import lang.temper.value.staticTypeContained
import lang.temper.value.typeDefinedSymbol

/**
 * Sort top-level kids of root based on the dependency graph between them.
 *
 * Declarations are order-independent: a statement that uses a top-level name goes after the
 * name's declaration and after its initialization, wherever those were written. A name is
 * initialized by its declaration's initializer or, when the declaration has none, by the first
 * top-level statement that assigns it, as with `let f(...) {...}`, which declares `f` and then
 * assigns a function to it.
 *
 * Any other assignment to a name is a reassignment. Below the name's declaration, it keeps its
 * place in source order relative to the other statements that read or assign that name when
 * they run. So
 *
 *     var w = 0;
 *     console.log("${w}");
 *     w = 99;
 *
 * logs 0. A read written above the declaration is a forward reference, and as with any
 * declaration used before it is written, it sees the name after all top-level assignments to it.
 * Reads inside a function body are not reads at that point, since the body runs only
 * when called, so a function definition is not held in place by reassignments of the names it
 * mentions. A statement that uses a top-level function instead counts as reading and assigning
 * what the function's body does, and so on through the functions that body uses. Class methods
 * are not followed this way.
 *
 * If the declarations a statement needs can only be put in place by moving it across one of
 * those reassignments, the order is ambiguous and we report an error rather than pick one.
 *
 * Expected that `flattenMultiInit` and `flattenMultiDeclarations` happen
 * before here in `DisAmbiguateStage`. Even in [SyntaxMacroStage], we call
 * [simplifyMultiAssignments] before this, though perhaps some of that
 * relates to the `DisAmbiguateStage` work as well.
 */
internal fun sortTopLevels(root: BlockTree, logSink: LogSink) {
    // First find the names to look for.
    val topNames = findTopNames(root)
    // Then find what each top-level statement declares, reads, and assigns.
    val tops = trackTops(topNames = topNames, root = root)
    // TODO If chunked, make a chunkNeeds map instead of an edgeNeeds map.
    val sequenced = mutableListOf<Sequenced>()
    val edgeNeeds = buildEdgeNeeds(tops, sequenced)
    checkSequenceKept(edgeNeeds, sequenced, logSink)
    // Sort and replace old with the new order.
    val resultEdges = partiallyOrder(edgeNeeds)
    root.removeChildren(0 until root.size)
    // And put in top-level imports first, since they're sometimes more finicky.
    // We especially don't handle them well if they get put into a module init block,
    // even if their only dependents come later.
    val (imports, others) = resultEdges.partition { isImport(it) }
    for (edge in imports) {
        root.add(edge.target)
    }
    for (edge in others) {
        root.add(edge.target)
    }
}

/** What one top-level statement does with top-level names. */
private class TopUse(
    val edge: TEdge,
    /** Name declared, if this is a declaration. */
    val declared: TemperName?,
    /** Whether this is a declaration with an initializer. */
    val hasInit: Boolean,
    /** Every top-level name read, including from inside function bodies. */
    val needs: Set<TemperName>,
    /** Top-level names read when this statement runs. */
    val reads: Set<TemperName>,
    /** Top-level names assigned when this statement runs. */
    val writes: Set<TemperName>,
    /** Top-level names read inside function bodies, so when those functions are called. */
    val laterReads: Set<TemperName>,
    /** Top-level names assigned inside function bodies. */
    val laterWrites: Set<TemperName>,
    /** Type names whose class or interface definition this is. */
    val defines: Set<TemperName>,
)

/** [later] must stay after [earlier] because both use [name], and at least one assigns it. */
private data class Sequenced(val later: TEdge, val earlier: TEdge, val name: TemperName)

private fun buildEdgeNeeds(
    tops: List<TopUse>,
    sequenced: MutableList<Sequenced>,
): Map<TEdge, Set<TEdge>> {
    // In good code, there should be only one decl for any name, but be flexible for broken things.
    val decls = mutableMapOf<TemperName, MutableList<TEdge>>()
    val declIndex = mutableMapOf<TemperName, Int>()
    val initializedByDecl = mutableSetOf<TemperName>()
    val writes = mutableMapOf<TemperName, MutableList<TEdge>>()
    val definitions = mutableMapOf<TemperName, MutableList<TEdge>>()
    for ((index, top) in tops.withIndex()) {
        top.declared?.let { name ->
            decls.putMultiList(name, top.edge)
            declIndex.getOrPut(name) { index }
            if (top.hasInit) {
                initializedByDecl.add(name)
            }
        }
        for (name in top.writes) {
            writes.putMultiList(name, top.edge)
        }
        for (name in top.defines) {
            definitions.putMultiList(name, top.edge)
        }
    }
    // A name declared without an initializer is initialized by its first assignment.
    val initializers = mutableMapOf<TemperName, TEdge>()
    for ((name, edges) in writes) {
        if (name !in initializedByDecl) {
            initializers[name] = edges.first()
        }
    }
    // Calling a function runs its body, so a statement that uses a top-level function reads and
    // assigns what that body does, and what the functions it uses do in turn.
    val initialized = mutableMapOf<TEdge, MutableList<TemperName>>()
    for ((name, edge) in initializers) {
        initialized.putMultiList(edge, name)
    }
    val laterReadsOf = mutableMapOf<TemperName, MutableSet<TemperName>>()
    val laterWritesOf = mutableMapOf<TemperName, MutableSet<TemperName>>()
    for (top in tops) {
        if (top.defines.isNotEmpty()) {
            // Not class methods, since a type name is also used where nothing is called.
            continue
        }
        val names = listOfNotNull(top.declared?.takeIf { top.hasInit }) +
            (initialized[top.edge] ?: emptyList())
        for (name in names) {
            laterReadsOf.getOrPut(name) { mutableSetOf() }.addAll(top.laterReads)
            laterWritesOf.getOrPut(name) { mutableSetOf() }.addAll(top.laterWrites)
        }
    }
    fun throughCalls(top: TopUse): Pair<Set<TemperName>, Set<TemperName>> {
        val reads = top.reads.toMutableSet()
        val writes = top.writes.toMutableSet()
        val seen = mutableSetOf<TemperName>()
        val pending = ArrayDeque(top.reads)
        while (pending.isNotEmpty()) {
            val name = pending.removeFirst()
            if (!seen.add(name)) {
                continue
            }
            laterWritesOf[name]?.let { writes.addAll(it) }
            laterReadsOf[name]?.let {
                reads.addAll(it)
                pending.addAll(it)
            }
        }
        return reads to writes
    }

    val edgeNeeds = buildSetMultimap {
        // Statements since the last reassignment of each name that read it.
        val readsSince = mutableMapOf<TemperName, MutableList<TEdge>>()
        val lastWrite = mutableMapOf<TemperName, TEdge>()
        fun sequence(later: TEdge, earlier: TEdge, name: TemperName) {
            if (later != earlier) {
                putMultiSet(later, earlier)
                sequenced.add(Sequenced(later = later, earlier = earlier, name = name))
            }
        }
        // Loop through the edges in order, so that keys are in source order. Where nothing
        // forces a move, `partiallyOrder` keeps that order.
        for ((index, top) in tops.withIndex()) {
            val edge = top.edge
            // TODO Ensure any non-decl/assign, independent `do` blocks sink to the end?
            // TODO One current thought is that you're allowed to have exactly one, and it runs last.
            // TODO Could have logging in some assigned `do` block and also in the standalone.
            if (edge !in this) {
                put(edge, mutableSetOf())
            }
            // Order-independent needs: declarations, type definitions, and initialization.
            for (name in top.needs + top.writes + top.defines) {
                for (declEdge in (decls[name] ?: emptyList()) + (definitions[name] ?: emptyList())) {
                    if (declEdge != edge) {
                        putMultiSet(edge, declEdge)
                    }
                }
                initializers[name]?.let { if (it != edge) putMultiSet(edge, it) }
            }
            val (reads, writesNow) = throughCalls(top)
            for (name in reads + writesNow) {
                val nameDeclIndex = declIndex[name] ?: continue
                if (index < nameDeclIndex) {
                    // A use written above the declaration is a forward reference, which sees
                    // the name once all top-level code has set it up.
                    if (name in reads) {
                        for (writeEdge in writes[name] ?: emptyList()) {
                            if (writeEdge != edge) {
                                putMultiSet(edge, writeEdge)
                            }
                        }
                    }
                } else if (index > nameDeclIndex && initializers[name] != edge) {
                    // Below the declaration, reads and reassignments keep their relative order.
                    lastWrite[name]?.let { sequence(edge, it, name) }
                    if (name in writesNow) {
                        readsSince.remove(name)?.forEach { sequence(edge, it, name) }
                        lastWrite[name] = edge
                    } else {
                        readsSince.putMultiList(name, edge)
                    }
                }
            }
        }
    }
    return edgeNeeds
}

/**
 * A cycle through a sequencing edge means the program asks for a declaration to be ready before
 * a statement that has to come after it, so there is no order that is both. [partiallyOrder]
 * would break the cycle at whichever edge it reaches last, which need not be the sequencing one,
 * so look for the cycle itself: [Sequenced.later] must not be needed, however indirectly, by
 * [Sequenced.earlier]. Report one error per set of statements caught in such a cycle.
 */
private fun checkSequenceKept(
    edgeNeeds: Map<TEdge, Set<TEdge>>,
    sequenced: List<Sequenced>,
    logSink: LogSink,
) {
    if (sequenced.isEmpty()) {
        return
    }
    val neededBy = mutableMapOf<TEdge, MutableList<TEdge>>()
    for ((edge, needs) in edgeNeeds) {
        for (need in needs) {
            neededBy.putMultiList(need, edge)
        }
    }
    fun reach(from: TEdge, step: (TEdge) -> Collection<TEdge>?): Set<TEdge> {
        val reached = mutableSetOf<TEdge>()
        val pending = ArrayDeque(listOf(from))
        while (pending.isNotEmpty()) {
            val edge = pending.removeFirst()
            if (reached.add(edge)) {
                step(edge)?.let { pending.addAll(it) }
            }
        }
        return reached
    }
    val reported = mutableSetOf<TEdge>()
    for ((later, earlier, name) in sequenced) {
        if (later in reported || later !in reach(earlier) { edgeNeeds[it] }) {
            continue
        }
        // One error for all the statements caught in the same cycle.
        reported.addAll(reach(later) { edgeNeeds[it] } intersect reach(later) { neededBy[it] })
        logSink.log(
            Log.Error,
            MessageTemplate.TopLevelOrderConflict,
            later.target.pos,
            listOf(name.displayName),
        )
    }
}

private fun findTopNames(root: BlockTree): Set<TemperName> {
    val tops = buildSet {
        tops@ for (tree in root.children) {
            val declTree = (tree as? DeclTree) ?: continue@tops
            val left = declTree.childOrNull(0) ?: continue@tops
            if (left is LeftNameLeaf) {
                add(left.content)
            } // else if (isCommaCall(left)) ... TODO Should comma call decls exist by this point?
        }
    }
    return tops
}

fun isImport(edge: TEdge): Boolean {
    val tree = edge.target
    return tree is DeclTree && tree.parts?.metadataSymbolMap?.let { importedSymbol in it } == true
}

/** @return every top-level edge in order with the names it declares, uses, and assigns */
private fun trackTops(topNames: Set<TemperName>, root: BlockTree): List<TopUse> {
    class Found {
        val now = mutableSetOf<TemperName>()
        val later = mutableSetOf<TemperName>()
        val all = mutableSetOf<TemperName>()
        val defined = mutableSetOf<TemperName>()
    }
    fun findTopNames(tree: Tree, wantedKind: NameKind): Found {
        val found = Found()
        fun walk(sub: Tree, inFunctionBody: Boolean) {
            var kidsInFunctionBody = inFunctionBody
            when (sub) {
                is FunTree -> {
                    // Class definitions are by `class` call with a `\typeDefined` function inside,
                    // rather than by assignment to a left name. That function's body is the
                    // class body, not a function body that runs later. Like a declaration, a
                    // definition is order-independent, so it is not an assignment.
                    val type = sub.parts?.metadataSymbolMap?.get(typeDefinedSymbol)
                        ?.target?.staticTypeContained as? NominalType
                    if (type == null) {
                        kidsInFunctionBody = true
                    } else if (wantedKind == NameKind.Left && type.definition.name in topNames) {
                        found.defined.add(type.definition.name)
                    }
                }
                is NameLeaf -> if (sub.content in topNames && sub.kind == wantedKind) {
                    found.all.add(sub.content)
                    if (inFunctionBody) {
                        found.later.add(sub.content)
                    } else {
                        found.now.add(sub.content)
                    }
                }
                else -> {}
            }
            for (kid in sub.children) {
                walk(kid, kidsInFunctionBody)
            }
        }
        walk(tree, false)
        return found
    }
    return root.edges.map { edge ->
        val tree = edge.target
        val reads = findTopNames(tree, NameKind.Right)
        // A declaration declares its name, and may assign others within its initializer.
        // An assignment inside a function body happens when the function is called, so only
        // assignments made as the statement runs count.
        val init = (tree as? DeclTree)?.parts?.metadataSymbolMap?.get(initSymbol)
        val assigned = when {
            tree !is DeclTree -> findTopNames(tree, NameKind.Left)
            init != null -> findTopNames(init.target, NameKind.Left)
            else -> null
        }
        TopUse(
            edge = edge,
            declared = (tree as? DeclTree)?.parts?.name?.content,
            hasInit = init != null,
            needs = reads.all,
            reads = reads.now,
            writes = assigned?.now ?: emptySet(),
            laterReads = reads.later,
            laterWrites = assigned?.later ?: emptySet(),
            defines = assigned?.defined ?: emptySet(),
        )
    }
}

/** For ease in discussing left vs right names without LeftName or RightName instances. */
private enum class NameKind {
    Left,
    Right,
}

private val NameLeaf.kind get() = when (this) {
    is LeftNameLeaf -> NameKind.Left
    is RightNameLeaf -> NameKind.Right
}
