package lang.temper.frontend

import lang.temper.interp.importExport.Export
import lang.temper.type.TypeShape
import lang.temper.value.BlockTree
import lang.temper.value.PartialResult

/** Outputs from a stage. */
data class StageOutputs(
    val root: BlockTree,
    val result: PartialResult,
    val exports: List<Export>,
    val declaredTypeShapes: List<TypeShape>,
)
