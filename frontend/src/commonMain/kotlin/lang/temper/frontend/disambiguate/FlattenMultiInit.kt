package lang.temper.frontend.disambiguate

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Log
import lang.temper.interp.asCurliesCall
import lang.temper.interp.walkDestructuring
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.name.Temporary
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.calleeBuiltinName
import lang.temper.value.dotBuiltinName
import lang.temper.value.freeTree
import lang.temper.value.importBuiltinName
import lang.temper.value.initSymbol
import lang.temper.value.surpriseMeSymbol
import lang.temper.value.symbolContained

/**
 * Transform destructuring init such as
 *
 * ```temper
 * let { x, y } = f();
 * ```
 *
 * into multiple-declaration such as
 *
 * ```temper
 * let temp = f(), x = temp.x, y = temp.y;
 * ```
 *
 * This is done before `flattenMultiDeclarations` so it can spread decorators as appropriate.
 */
internal fun flattenMultiInit(root: BlockTree, logSink: LogSink) {
    val document = root.document
    val nameMaker = document.nameMaker
    TreeVisit.startingAt(root).forEachContinuing visit@{ tree ->
        val decl = (tree as? DeclTree) ?: return@visit
        val init = decl.partsIgnoringName?.metadataSymbolMap?.get(initSymbol)
        // Do not restructure import calls until createLocalBindingsForImport can do that.
        if (init != null && isImportCall(init.target)) { return@visit }
        val targets = decl.childOrNull(0).asCurliesCall() ?: return@visit
        val incoming = decl.incoming!!
        freeTree(decl)
        freeTree(targets)
        val commaCallArgs = mutableListOf<Tree>(ValueLeaf(document, decl.pos, BuiltinFuns.vCommaFn))
        // Reuse the old decl for the temporary, since it's the aggregate.
        val temporary = nameMaker.unusedTemporaryName(Temporary.defaultNameHint)
        decl.edge(0).replace(LeftNameLeaf(document, targets.pos, temporary))
        commaCallArgs.add(decl)
        // Make new decls for individual names, then put all together.
        targets.forEachDecl(temporary, logSink) { commaCallArgs.add(it) }
        incoming.replace(CallTree(document, decl.pos, commaCallArgs.toList()))
    }.visitPostOrder()
}

private fun Tree.forEachDecl(temporary: Temporary, logSink: LogSink, action: (Tree) -> Unit) {
    walkDestructuring(logSink) targets@{ targetName, source, metaNodes ->
        if (source.symbolContained == surpriseMeSymbol) {
            logSink.log(Log.Error, MessageTemplate.WildcardWithoutImport, source.pos, emptyList())
            return@targets
        }
        targetName ?: return@targets
        val sourceSymbol = (source as? LeftNameLeaf)?.content?.toSymbol() ?: return@targets
        val decl = document.treeFarm.grow {
            Decl(source.pos) {
                // Target name and metadata.
                Replant(freeTree(targetName))
                for (metaNode in metaNodes) {
                    Replant(freeTree(metaNode))
                }
                // Init to source value.
                V(source.pos, initSymbol)
                Call(source.pos) {
                    Rn(source.pos, dotBuiltinName)
                    Rn(pos, temporary)
                    V(source.pos, sourceSymbol)
                }
            }
        }
        action(decl)
    }
}

fun isImportCall(tree: Tree?) =
    tree is CallTree && tree.calleeBuiltinName() == importBuiltinName.builtinKey
