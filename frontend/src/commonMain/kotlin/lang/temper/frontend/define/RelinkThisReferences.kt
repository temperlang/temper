package lang.temper.frontend.define

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.BuiltinFuns
import lang.temper.interp.errorNodeFor
import lang.temper.log.FailLog
import lang.temper.log.MessageTemplate
import lang.temper.name.TemperName
import lang.temper.type.TypeShape
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.FunTree
import lang.temper.value.RightNameLeaf
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.freeTarget
import lang.temper.value.functionContained
import lang.temper.value.impliedThisSymbol
import lang.temper.value.typeShapeAtLeafOrNull

internal fun relinkThisReferences(
    root: BlockTree,
    failLog: FailLog,
) {
    val thisNameCache = mutableMapOf<Pair<FunTree, TypeShape>, TemperName?>()
    fun lookupThisName(t: Tree, typeShape: TypeShape): TemperName? {
        val containingFunction = run findFun@{
            var edge = t.incoming
            while (edge != null) {
                val target = edge.target
                if (target is FunTree) {
                    return@findFun target
                }
                edge = edge.source?.incoming
            }
            return null
        }
        return thisNameCache.getOrPut(containingFunction to typeShape) metadata@{
            val fnParts = containingFunction.parts
            if (fnParts != null) {
                // Look for a formal parameter annotated with @impliedThis(typeId)
                for (decl in fnParts.formals) {
                    val declParts = decl.parts ?: continue
                    val thisMetadata = declParts.metadataSymbolMap[impliedThisSymbol]
                    val impliedThisTypeShape = thisMetadata?.target?.typeShapeAtLeafOrNull
                    if (impliedThisTypeShape == typeShape) {
                        return@metadata declParts.name.content
                    }
                }
            }
            // Continue search upwards
            val fnParent = containingFunction.incoming?.source
            if (fnParent != null) {
                lookupThisName(fnParent, typeShape)
            } else {
                null
            }
        }
    }

    val edits = mutableListOf<Pair<TEdge, () -> Tree>>()
    TreeVisit.startingAt(root)
        .forEach { t ->
            if (t is CallTree && t.size == 2) {
                val callee = t.childOrNull(0)
                if (callee?.functionContained == BuiltinFuns.thisPlaceholder) {
                    val thisTypeShape = t.child(1).typeShapeAtLeafOrNull
                    if (thisTypeShape != null) {
                        val edge = t.incoming!! // Safe because root is not a call
                        val name = lookupThisName(t, thisTypeShape)
                        if (name != null) {
                            edits.add(edge to { RightNameLeaf(t.document, t.pos, name) })
                        } else {
                            failLog.fail(MessageTemplate.ThisOutsideClassBody, t.pos)
                            edits.add(edge to { errorNodeFor(t) })
                        }
                        VisitCue.SkipOne
                    }
                }
            }
            VisitCue.Continue
        }
        .visitPreOrder()

    for (edit in edits) {
        val (edgeToReplace, makeReplacement) = edit
        freeTarget(edgeToReplace)
        edgeToReplace.replace(makeReplacement())
    }
}
