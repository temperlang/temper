package lang.temper.frontend

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Either
import lang.temper.name.Temporary
import lang.temper.value.BlockTree
import lang.temper.value.LinearFlow
import lang.temper.value.RightNameLeaf
import lang.temper.value.TEdge
import lang.temper.value.Value
import lang.temper.value.valueContained

/**
 * Extract [edge]'s tree into a temporary variable so that we can reuse its result while respecting
 * order of operations and avoiding multiple evaluation.
 *
 * @return either the known value of [edge] that may be reused directly, or the name of a temporary
 *     that holds the value.
 */
internal fun maybeExtractIntoTemporary(
    /** The edge whose value needs to be reusable */
    edge: TEdge,
    /**
     * If the edge is extracted into a temporary, the edge that should follow its declaration.
     * It must be the child of a [BlockTree] with a [LinearFlow].
     */
    treeFollower: TEdge,
): Either<Value<*>, Temporary> {
    val target = edge.target
    val knownValue = target.valueContained
    if (knownValue != null) {
        return Either.Left(knownValue)
    }
    val doc = target.document
    val pos = target.pos
    val temporary = doc.nameMaker.unusedTemporaryName(Temporary.defaultNameHint)

    val parent = treeFollower.source
    require(parent is BlockTree && parent.flow == LinearFlow)
    val treeFollowerIndex = treeFollower.edgeIndex

    edge.replace(RightNameLeaf(doc, pos, temporary))
    parent.replace(treeFollowerIndex until treeFollowerIndex) {
        Decl(pos, temporary)
        Call(pos, BuiltinFuns.vSetLocalFn) {
            Ln(temporary)
            Replant(target)
        }
    }

    return Either.Right(temporary)
}
