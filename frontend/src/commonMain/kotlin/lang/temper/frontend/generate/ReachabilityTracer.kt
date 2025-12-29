package lang.temper.frontend.generate

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.interp.LongLivedUserFunction
import lang.temper.name.ExportedName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.SourceName
import lang.temper.name.TemperName
import lang.temper.type.TypeShape
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.fromTypeSymbol
import lang.temper.value.functionContained
import lang.temper.value.importedSymbol
import lang.temper.value.returnParsedName
import lang.temper.value.testSymbol
import lang.temper.value.typeDefContained
import lang.temper.value.typeNameContained
import lang.temper.value.typePlaceholderSymbol
import lang.temper.value.vNoneSymbol
import lang.temper.value.vReachSymbol
import lang.temper.value.vTestSymbol
import lang.temper.value.varSymbol

/**
 * Finds which top-level declarations are reachable from exported members,
 * which from tests, and which from neither. On the presumption that most
 * declarations are reachable from exported members, doesn't mark those at
 * all.
 *
 * Analysis at the moment isn't concerned with flow. If a reference exists
 * anywhere in a function, for example, that counts as a reference. So this
 * isn't dead code elimination as much as treating mentions as intent. Note,
 * however, that prior dead code elimination or inlining prior to running
 * this tracer could result in marking things unreachable that seem to be
 * referenced in source.
 *
 * In the future, reachability marking might also extend to class members
 * and locals, but that isn't done today.
 */
internal class ReachabilityTracer {
    fun markReachability(root: BlockTree) {
        if (root.pos.loc == ImplicitsCodeLocation) {
            // We'll prune implicits manually, so skip the pass here.
            return
        }
        // Gather all (1) maybe unreached declarations, (2) export roots, & (3) test roots.
        gatherRootsAndDecls(root)
        gatherInitCode(root)
        gatherNestedTypes()
        // Prune export-reachables so we don't mark them.
        for (export in exports) {
            trace(export)
        }
        // Now that we've pruned export-reachables, mark test-only-reachables.
        for (test in tests) {
            trace(test) { tree ->
                tree.replace(tree.size until tree.size) {
                    V(vReachSymbol)
                    V(vTestSymbol)
                }
            }
        }
        // Mark remaining decls as unreachable.
        for (tree in unreachedMap.values) {
            tree.replace(tree.size until tree.size) {
                V(vReachSymbol)
                V(vNoneSymbol)
            }
        }
    }

    private fun gatherInitCode(root: BlockTree) {
        val varAssignments = mutableMapOf<TemperName, MutableList<Tree>>()
        TreeVisit.startingAt(root).forEach { tree ->
            when (tree) {
                // Blocks inside root run.
                is BlockTree -> when (tree) {
                    root -> VisitCue.Continue
                    else -> {
                        exports.add(tree)
                        VisitCue.SkipOne
                    }
                }

                // Calls can run code, but watch out for assignments.
                is CallTree -> when (val firstArg = tree.childOrNull(1)) {
                    is LeftNameLeaf -> {
                        when (val name = firstArg.content) {
                            is ExportedName, in testNames -> {
                                // We'd already have put this in some root, so don't repeat that.
                            }

                            is SourceName if name.baseName == returnParsedName -> {
                                // Hack support export reachability for any "return" var at the top level.
                                // Our main root decl pass doesn't pay attention to top-level or not.
                                exports.add(tree)
                                unreachedMap.remove(name)
                            }

                            else -> when (tree.childOrNull(2)) {
                                is FunTree, is RightNameLeaf, is ValueLeaf -> {
                                    // Simple values and names don't run init code, so exclude from roots.
                                    // Imports also end up here as assignments to exported names.
                                    if (name in vars) {
                                        // But track vars in case of multiple init assignment.
                                        varAssignments.getOrPut(name) { mutableListOf() }.add(tree)
                                    }
                                }

                                else -> exports.add(tree)
                            }
                        }
                        VisitCue.SkipOne
                    }

                    else -> {
                        exports.add(tree)
                        VisitCue.SkipOne
                    }
                }

                // Exclude code that doesn't run things on load.
                is DeclTree -> VisitCue.SkipOne
                is FunTree -> VisitCue.SkipOne
                // Dig deeper into everything else.
                else -> VisitCue.Continue
            }
        }.visitPreOrder()
        // In defs, we can already see if there are multiple assignments, but multiple here means
        // inside init code. And keep if multiple because we don't currently prune useless
        // assignments.
        for ((name, trees) in varAssignments) {
            if (trees.size > 1) {
                unreachedMap.remove(name)
                exports.addAll(trees)
            }
        }
    }

    private fun gatherNestedTypes() {
        // Methods get raised to top level already, and classes will be in tmpl, but they aren't in the frontend.
        // So dig for them here.
        // And go from defs because top-level defs hopefully are less work than walking the whole tree?
        names@ for ((name, defsForName) in defs.entries) {
            // Tests are never types, so don't bother to look in tests.
            (name in unreachedMap || name in exportNames) && continue@names
            // We don't have that name tracked as unreached or export, so see if we should add it as unreached.
            defsForName@ for (def in defsForName) {
                if (def is DeclTree) {
                    val fromType = def.parts?.metadataSymbolMap?.get(fromTypeSymbol) ?: continue@defsForName
                    // It's for a type, so dig for the type, and whatever happens here out, bail on the whole name.
                    val typeShape = fromType.target.typeDefContained() as? TypeShape ?: continue@names
                    val decl = typeShape.stayLeaf?.incoming?.source as? DeclTree ?: continue@names
                    unreachedMap[name] = decl
                    continue@names
                }
            }
        }
    }

    private fun gatherRootsAndDecls(root: BlockTree) {
        // A separate pass looks potentially imperative top-level "init" code.
        TreeVisit.startingAt(root).forEach tree@{ tree ->
            val name = when (tree) {
                is DeclTree -> (tree.childOrNull(0) as? LeftNameLeaf)?.content
                // If the following is a LeftNameLeaf, this must be an assign, but don't bother to check that.
                is CallTree -> (tree.childOrNull(1) as? LeftNameLeaf)?.content
                else -> null
            } ?: return@tree VisitCue.Continue
            // We're defining something in some fashion. Figure out where it goes.
            var added = false
            if (name is ExportedName) {
                added = true
                exports.add(tree)
            }
            // Various mutually exclusive things we care about.
            when {
                tree is DeclTree -> {
                    try {
                        val metadataSymbolMap = tree.parts?.metadataSymbolMap ?: return@tree VisitCue.SkipOne
                        if (varSymbol in metadataSymbolMap) {
                            vars.add(name)
                        }
                        when {
                            metadataSymbolMap.containsKey(testSymbol) -> {
                                added = true
                                tests.add(tree)
                                // Track so we can also find the definitions.
                                testNames.add(name)
                            }

                            else -> {
                                metadataSymbolMap[importedSymbol]?.let { imported ->
                                    (imported.target.childOrNull(0) as? RightNameLeaf)?.let { leaf ->
                                        importedNames[leaf.content] = name
                                    }
                                    return@tree VisitCue.SkipOne
                                }
                                val relatedType = metadataSymbolMap[fromTypeSymbol]?.target
                                    ?: metadataSymbolMap[typePlaceholderSymbol]?.target ?: return@tree VisitCue.SkipOne
                                val relatedTypeName = relatedType.typeNameContained() ?: return@tree VisitCue.SkipOne
                                // TODO Skip if private for `fromType`?
                                when {
                                    relatedTypeName is ExportedName -> {
                                        // If it's from a type, the name isn't exported, but it's still an export root.
                                        added = true
                                        exports.add(tree)
                                        // Track so we can also find the definitions.
                                        exportNames.add(name)
                                    }

                                    !added -> {
                                        // Associate this decl with the type name.
                                        defs.getOrPut(relatedTypeName) { mutableListOf() }.add(tree)
                                    }
                                }
                            }
                        }
                    } finally {
                        if (!added) {
                            // Not a root, so we need to evaluate later.
                            unreachedMap[name] = tree
                        }
                    }
                }

                // We put assigns after decls, so this should be fine. Also, check against exported first for speed.
                !added -> when (name) {
                    in exportNames -> exports.add(tree)
                    in testNames -> tests.add(tree)
                    else -> {
                        // Neither root nor decl, so it's something we might need to reference while tracing later.
                        defs.getOrPut(name) { mutableListOf() }.add(tree)
                    }
                }
            }
            VisitCue.SkipOne
        }.visitPreOrder()
    }

    private fun trace(tracingRoot: Tree, action: (DeclTree) -> Unit = {}) {
        // Limit recursion depth somewhat by deferring encountered defs until after the current tree.
        val nexts = buildList nexts@{
            TreeVisit.startingAt(tracingRoot).forEachContinuing { tree ->
                val names = buildList names@{
                    when (tree) {
                        is NameLeaf -> add(tree.content)
                        is ValueLeaf -> {
                            val def = tree.typeDefContained()
                            when {
                                def != null -> {
                                    // Supertypes aren't necessarily in trees, so dig for them.
                                    def.superTypes.forEach { add(it.definition.name) }
                                    add(def.name)
                                    // Also loop around to imported names if any.
                                    importedNames[def.name]?.let { add(it) }
                                }

                                else -> {
                                    // See if we can find the unique name of the function value.
                                    when (val function = tree.functionContained) {
                                        is LongLivedUserFunction -> {
                                            function.stayLeaf.assignedName()?.let { name ->
                                                // Try the name itself and any imported equivalent.
                                                add(name)
                                                importedNames[name]?.let { add(it) }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
                names@ for (name in names) {
                    val declTree = unreachedMap.remove(name) ?: continue@names
                    action(declTree)
                    defs[name]?.let { this@nexts.addAll(it) }
                }
            }.visitPreOrder()
        }
        // Make the deferred visits.
        for (next in nexts) {
            trace(next, action)
        }
    }

    private val defs = mutableMapOf<TemperName, MutableList<Tree>>()
    private val exports = mutableListOf<Tree>()
    private val exportNames = mutableSetOf<TemperName>()
    private val importedNames = mutableMapOf<TemperName, TemperName>()
    private val tests = mutableListOf<Tree>()
    private val testNames = mutableSetOf<TemperName>()
    private val unreachedMap = mutableMapOf<TemperName, DeclTree>()
    private val vars = mutableSetOf<TemperName>()
}

private fun Tree.assignedName(): TemperName? {
    var node = this
    while (true) {
        if (node is CallTree) {
            val name = (node.childOrNull(1) as? LeftNameLeaf)?.content
            if (name != null) {
                return name
            }
        }
        node = node.incoming?.source ?: break
    }
    return null
}
