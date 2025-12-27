package lang.temper.value

import lang.temper.log.Position
import lang.temper.stage.Stage

class TEdge internal constructor(source: InnerTree, target: Tree) {
    /** used to avoid redundant macro-expansion during partial interpretation */
    var breadcrumb: Stage? = null

    private var _source: InnerTree? = source
    private var _target: Tree = target

    val source get() = _source
    val target get() = _target

    /**
     * Swaps a replacement tree in for the edge target.
     *
     * @param replacement null to remove.
     * @return the prior target.
     */
    fun replace(replacement: Tree?): Tree {
        return _source!!.replaceEdgeTarget(this, replacement)
    }

    inline fun replace(
        crossinline makeReplacement:
        (Planting).(pos: Position) -> UnpositionedTreeTemplate<*>,
    ) {
        val t = target
        val pos = t.pos
        replace(
            t.document.treeFarm.grow(pos) {
                this.makeReplacement(pos)
            },
        )
    }

    /** For same-file use only. */
    internal fun unlink() {
        _source = null
    }

    /** For same-file use only. */
    internal fun setTarget(newTarget: Tree) {
        _target = newTarget
    }

    val edgeIndex: Int
        get() = when (val source = _source) {
            null -> -1
            else -> source.edges.indexOf(this)
        }
}
