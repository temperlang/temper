package lang.temper.frontend.disambiguate

import lang.temper.builtin.isComplexTypeArg
import lang.temper.common.Log
import lang.temper.common.subListToEnd
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.type.Variance
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.RightNameLeaf
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.lookThroughDecorations
import lang.temper.value.nameContained
import lang.temper.value.superSymbol
import lang.temper.value.symbolContained

internal data class TypeFormalHelper(
    val decorated: TEdge,
    val formalName: Tree,
    val upperBounds: List<Tree>,
    val variance: Variance,
    val variancePos: Position?,
    val problems: List<LogEntry>,
)

internal fun inspectTypeFormal(
    /** A tree after a [lang.temper.value.typeArgSymbol] where a type formal (not an actual) is expected. */
    typeFormalEdge: TEdge,
): TypeFormalHelper {
    var formalName: Tree = typeFormalEdge.target // This may be a lie
    val upperBounds = mutableListOf<Tree>()
    var variance = Variance.Default
    var variancePos: Position? = null
    val problems = mutableListOf<LogEntry>()

    // Look for patterns like
    // 1. @out Name
    // 2. @in  Name
    // 3.      Name extends TypeExpression
    formalNameLoop@
    while (formalName is CallTree) {
        val callee = formalName.child(0) as? RightNameLeaf ?: break
        var builtinKey = callee.content.builtinKey
        var edges = formalName.edges
        if (builtinKey == "@") {
            // If the `@` hasn't fired yet, reassemble the decoration name
            val decoration = edges.getOrNull(1)?.nameContained?.builtinKey
            if (decoration != null) {
                edges = edges.subListToEnd(1)
                builtinKey = "@$decoration"
            }
        }
        when (builtinKey to edges.size) {
            covariantAnnotationNameText to 2, contravariantAnnotationNameText to 2 -> {
                variance = if (builtinKey == covariantAnnotationNameText) {
                    Variance.Covariant
                } else {
                    Variance.Contravariant
                }
                formalName = edges[1].target
                variancePos = callee.pos
            }
            else -> break@formalNameLoop
        }
    }

    val decorated = lookThroughDecorations(typeFormalEdge)

    // Unpack complex type arg like: { \typeArg name metadata }
    if (isComplexTypeArg(formalName)) {
        val complexTypeArgBlock: BlockTree = formalName
        formalName = complexTypeArgBlock.child(1)
        var metadataIndex = 2
        while (metadataIndex + 1 < complexTypeArgBlock.size) {
            val keyTree = complexTypeArgBlock.child(metadataIndex)
            val valueTree = complexTypeArgBlock.child(metadataIndex + 1)
            metadataIndex += 2

            when (keyTree.symbolContained) {
                superSymbol -> {
                    upperBounds.add(valueTree)
                }
                else -> {
                    val problem = LogEntry(
                        Log.Error,
                        MessageTemplate.MalformedDeclaration,
                        keyTree.pos,
                        emptyList(),
                    )
                    problems.add(problem)
                    break
                }
            }
        }
    }

    return TypeFormalHelper(
        decorated = decorated,
        formalName = formalName,
        upperBounds = upperBounds,
        variance = variance,
        variancePos = variancePos,
        problems = problems.toList(),
    )
}
