package lang.temper.log

import kotlin.math.max
import kotlin.math.min

interface Positioned {
    val pos: Position
}

fun (Iterable<Positioned>).spanningPosition(pos: Position): Position {
    val loc = pos.loc
    var left = pos.left
    var right = pos.right
    for (p in this) {
        val ppos = p.pos
        if (ppos.loc == loc) {
            left = min(left, ppos.left)
            right = max(right, ppos.right)
        }
    }
    return if (left == pos.left && right == pos.right) {
        pos
    } else {
        Position(loc, left = left, right = right)
    }
}

val unknownPos = Position(UnknownCodeLocation, 0, 0)
