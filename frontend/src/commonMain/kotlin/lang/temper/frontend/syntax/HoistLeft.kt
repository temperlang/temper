package lang.temper.frontend.syntax

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.BuiltinFuns
import lang.temper.name.ExportedName
import lang.temper.name.TemperName
import lang.temper.value.BlockTree
import lang.temper.value.DeclParts
import lang.temper.value.DeclTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.hoistLeftSymbol
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.valueContained
import lang.temper.value.void

/**
 * Declaration hoisting is a way to let adjacent definitions co-reference one another without one
 * needing to be above the other.
 * ```
 * // Types can mutually reference via property types and method signatures
 * class C { d: D }
 * class D { c: C | null }
 * // co-recursive functions mutually reference
 * let f(x) { if (x == 0) { 0 } else { g(x - 1) } }
 * let g(x) { f(x / 2) }
 * ```
 *
 * In Temper, we turn type and variable definitions into declarations and initializations.
 * ```
 * class C { ... }
 * // becomes
 * //     let C = /* reified type C */
 * //     class(\word, C) { ... }
 *
 * let f() {}
 * // becomes
 * //    let f = fn f() {}
 * ```
 *
 * This micro-pass handles this for type, function, and any other kinds of definitions that need to
 * mutually reference via pre-name resolution processing in the syntax macro stage.
 *
 * ## Definitional macros
 * The type definition macro and function definition add [`@hoistLeft`][hoistLeftSymbol] to the
 * declaration nodes they create.
 *
 * ## Staging
 * The syntax macro stage identifies those declarations (and decorated declarations), left in their
 * containing blocks making sure
 * - We do not move initializers unless the metadata associated with `\hoistLeft` is true.
 * - We do not move a declaration past another declaration with the same name text.
 *   We may not move L3 past L2
 *   ```
 *   let x = 42;             // L1
 *   @hoistLeft let x = 41;  // L2
 *   f(x);                   // L3
 *   ```
 * - We may move a name past a use of that same name.
 *   We may move L3 past L2
 *   ```
 *   let x = 42;             // L1
 *   f(x);                   // L2
 *   @hoistLeft let x = 41;  // L3
 *   ```
 *   This allows a class definition to reference a later defined type.
 *
 * The intent is for hoisting to have no effect on order of evaluation, and its only effect on name
 * resolution is to allow binding to prefer later declarations that have `@hoistLeft` over
 * declarations in a containing block or builtins.
 *
 * We optimistically assume that decorations on hoisted declarations are macros that may be
 * re-ordered, and that type expressions on these declarations do not either.
 * (None of the planned creators of @hoistLeft declarations put declared types on their declarations)
 */
internal fun hoistLeft(root: BlockTree) {
    // First, scan the root to find each linear block that has one or more declarations that have
    // `@hoistLeft` on them.
    // In the process of extracting this list, it's convenient to remove the `@hoistLeft` metadata.

    data class Hoist(
        /**
         * The edge is not necessarily to [decl]; for decorated declarations,
         * it may be a call to the decorator, of which [decl] is a descendant.
         */
        val edgeToHoist: TEdge,
        /**
         * The declaration being hoisted.
         */
        val decl: DeclTree,
        val parts: DeclParts,
        val hoistInitializer: Boolean,
    )

    val blockAndHoists = mutableListOf<Pair<BlockTree, List<Hoist>>>()
    TreeVisit.startingAt(root)
        .forEachContinuing { t ->
            if (t is BlockTree && t.flow is LinearFlow) {
                val hoists = mutableListOf<Hoist>()
                for (edge in t.edges) {
                    val undecoratedEdge = lookThroughDecorations(edge)
                    val decl = undecoratedEdge.target as? DeclTree ?: continue
                    val parts = decl.parts ?: continue
                    val hoistLeftEdge = parts.metadataSymbolMap[hoistLeftSymbol] ?: continue
                    val hoistLeftEdgeIndex = hoistLeftEdge.edgeIndex
                    val hoistInitializer = true == hoistLeftEdge.target.valueContained(TBoolean)
                    decl.replace((hoistLeftEdgeIndex - 1)..hoistLeftEdgeIndex) {}
                    hoists.add(Hoist(edge, decl, parts, hoistInitializer = hoistInitializer))
                }
                if (hoists.isNotEmpty()) {
                    blockAndHoists.add(t to hoists.toList())
                }
            }
        }
        .visitPostOrder()

    // Now we know what to hoist, we need a way to tell where not to hoist.
    // When hoisting, we want to avoid hoisting past anything that introduces a name conflict.
    val cacheOfEdgeToNamesUsed = mutableMapOf<Tree, Set<TemperName>>()
    fun leftNamesUsedIn(tree: Tree): Set<TemperName> = cacheOfEdgeToNamesUsed.getOrPut(tree) {
        val names = mutableSetOf<TemperName>()
        TreeVisit.startingAt(tree)
            .forEach { t ->
                if (t is LeftNameLeaf) {
                    val name = t.content
                    names.add(name)
                    if (name is ExportedName) {
                        // ExportedNames can conflict with names that match their base names.
                        names.add(name.baseName)
                    }
                    VisitCue.Continue
                } else {
                    val cachedForIt = cacheOfEdgeToNamesUsed[t]
                    if (cachedForIt != null) {
                        names.addAll(cachedForIt)
                        VisitCue.SkipOne
                    } else {
                        VisitCue.Continue
                    }
                }
            }
            .visitPreOrder()
        names.toSet()
    }
    fun nameConflictIn(name: TemperName, tree: Tree): Boolean {
        val conflictingNames = leftNamesUsedIn(tree)
        return name in conflictingNames ||
            // An exported name can conflict with its base name.
            (name is ExportedName && name.baseName in conflictingNames)
    }

    for ((block, hoists) in blockAndHoists) {
        // For each block, we keep a limit of where we can hoist to.
        var hoistLimit = 0
        // Don't hoist past any label, as in `foo: { ... }` which is (Block \label \foo ...)
        block.parts.label?.let { hoistLimit = it.edgeIndex + 1 }
        // If we hoist one, we advance the hoist limit, so we move @hoistLeft declarations left,
        // but, among the group we're hoisting, do not reorder them.

        // We do not hoist declarations past uses with the same name, except that we can hoist it
        // past the initializer of a hoisted declaration, since the whole point of hoisting left is
        // to allow mutually referencing definitions.
        val hoistInitializingEdges = mutableSetOf<Tree>()

        for (hoist in hoists) {
            val (edge, decl, parts) = hoist
            val name = parts.name.content
            val edgeIndex = edge.edgeIndex
            val hoistedTree = edge.target

            var targetEdgeIndex = edgeIndex
            while (targetEdgeIndex > hoistLimit) {
                val preceder = block.child(targetEdgeIndex - 1)
                if (preceder !in hoistInitializingEdges && nameConflictIn(name, preceder)) {
                    break
                }
                targetEdgeIndex -= 1
            }
            hoistLimit = targetEdgeIndex + 1

            // If there is an initializer, then we've got to leave an assignment in place.
            val initEdge = parts.metadataSymbolMap[initSymbol]
            val assignment = if (initEdge != null && !hoist.hoistInitializer) {
                val initExpr = initEdge.target
                val initEdgeIndex = initEdge.edgeIndex
                decl.replace((initEdgeIndex - 1)..initEdgeIndex) {}
                val assignment = initExpr.treeFarm.grow {
                    Call(initExpr.pos, BuiltinFuns.setLocalFn) {
                        Ln(parts.name.pos.leftEdge, name)
                        Replant(initExpr)
                    }
                }
                hoistInitializingEdges.add(assignment)
                assignment
            } else {
                null
            }

            // If edge is the last edge, we need to replace it with a void,
            // since the result of evaluating a declaration is void, and we don't want to change
            // the result of evaluating the block.
            val voidIfNeeded = if (edgeIndex + 1 == block.size) {
                ValueLeaf(block.document, hoistedTree.pos.rightEdge, void)
            } else {
                null
            }

            val replacements = listOfNotNull(assignment, voidIfNeeded)

            if (targetEdgeIndex == edgeIndex) {
                // Nothing to move
                if (assignment != null) {
                    // but separate out assignment so that we can hoist later stuff over it.
                    block.replace((edgeIndex + 1) until (edgeIndex + 1)) {
                        replacements.forEach { Replant(it) }
                    }
                }
                continue
            }

            block.replace(edgeIndex..edgeIndex) {
                replacements.forEach { Replant(it) }
            }

            block.replace(targetEdgeIndex until targetEdgeIndex) {
                Replant(hoistedTree)
            }
        }
    }
}
