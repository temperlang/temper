package lang.temper.kcodegen.outgrammar

import lang.temper.log.Position

internal data class Condition(
    val pos: Position,
    val propertyName: Id,
    val wanted: SimpleValue,
) {
    override fun toString() = "Cond($propertyName, $wanted)"
}
