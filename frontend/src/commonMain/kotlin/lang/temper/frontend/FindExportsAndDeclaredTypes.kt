package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.env.Constness
import lang.temper.env.Environment
import lang.temper.interp.importExport.Export
import lang.temper.interp.importExport.Exporter
import lang.temper.name.ExportedName
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.type.TypeShape
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.InterpreterCallback
import lang.temper.value.MetadataValueMultimap
import lang.temper.value.TypeInferences
import lang.temper.value.Value
import lang.temper.value.ValueStability
import lang.temper.value.stability
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typePlaceholderSymbol
import lang.temper.value.typeShapeAtLeafOrNull
import lang.temper.value.unpackPairValue
import lang.temper.value.valueContained

internal fun findExportsAndDeclaredTypes(
    exporter: Exporter,
    root: BlockTree,
    env: Environment,
    stage: Stage,
): Pair<List<Export>, List<TypeShape>> {
    val exportedNames = env.locallyDeclared.mapNotNull { name ->
        (name as? ExportedName)
    }
    val declaredTypes = mutableSetOf<TypeShape>()

    // If we have exports, try to relate them to type inferences.
    val declInfoForExports = mutableMapOf<ExportedName, DeclInfo>()
    if (exportedNames.isNotEmpty() && stage >= Stage.Type) {
        TreeVisit.startingAt(root).forEachContinuing {
            val declParts = (it as? DeclTree)?.parts
            if (declParts != null) {
                val nameLeaf = declParts.name
                val name = nameLeaf.content
                if (name is ExportedName) {
                    val newTypeInferences = if (name in declInfoForExports) {
                        DeclInfo.empty // If there are two or more exports, don't be ambiguous
                    } else {
                        val metadata = (it.parts?.metadataSymbolMultimap ?: emptyMap())
                            .mapValues { (_, edges) ->
                                edges.map { edge ->
                                    edge.target.valueContained?.let { value ->
                                        if (isStableForMetadata(value)) {
                                            value
                                        } else {
                                            null
                                        }
                                    }
                                }
                            }
                        DeclInfo(nameLeaf.typeInferences, metadata)
                    }
                    declInfoForExports[name] = newTypeInferences
                }
                for (declSymbol in listOf(typeDeclSymbol, typePlaceholderSymbol)) {
                    val typeShape = declParts.metadataSymbolMap[declSymbol]
                        ?.target
                        ?.typeShapeAtLeafOrNull
                    if (typeShape != null) {
                        declaredTypes.add(typeShape)
                    }
                }
            }
        }.visitPreOrder()
    }

    return exportedNames.map { exportedName ->
        val value = if (env.constness(exportedName) == Constness.Const) {
            env[exportedName, InterpreterCallback.NullInterpreterCallback] as? Value<*>
        } else {
            null
        }
        val pos = env.declarationSite(exportedName) ?: root.pos.leftEdge
        val (typeInferences, declarationMetadata) = declInfoForExports[exportedName] ?: DeclInfo.empty
        Export(exporter, exportedName, value, typeInferences, declarationMetadata, pos)
    } to declaredTypes.toList()
}

private data class DeclInfo(
    val typeInferences: TypeInferences?,
    val metadata: Map<Symbol, List<Value<*>?>>,
) {
    companion object {
        val empty = DeclInfo(null, MetadataValueMultimap.Companion.empty)
    }
}

fun isStableForMetadata(value: Value<*>): Boolean {
    if (value.stability == ValueStability.Stable) { return true }

    // Before we can leave values like Pairs as stable, we need to adjust the
    // interpreter&|typer to allow us to preserve type parameter info, and
    // to allow un-inlining values by reconverting values to constructor
    // expressions during TmpL translation.
    // But for metadata we don't need that, and Pairs are used importantly
    // as staticExtension metadata which needs to persist across Exports.
    val p = unpackPairValue(value)
    if (p != null) {
        val (a, b) = p
        return isStableForMetadata(a) && isStableForMetadata(b)
    }

    return false
}
