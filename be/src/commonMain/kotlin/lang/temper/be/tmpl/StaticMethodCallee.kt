package lang.temper.be.tmpl

import lang.temper.name.Symbol
import lang.temper.type.NominalType
import lang.temper.type.StaticPropertyShape
import lang.temper.type.TypeShape
import lang.temper.value.CallTree
import lang.temper.value.Tree
import lang.temper.value.fnSymbol
import lang.temper.value.staticTypeContained
import lang.temper.value.symbolContained

internal data class StaticMethodCallee(
    val typeShape: TypeShape,
    val methodName: Symbol,
    val member: StaticPropertyShape,
)

internal fun unpackStaticMethodCallee(callee: Tree?): StaticMethodCallee? {
    if (callee !is CallTree || callee.size != GETS_ARITY) { return null }

    val (_, typeChild, propertyChild) = callee.children
    val type = typeChild.staticTypeContained
    val definition = (type as? NominalType)?.definition
    val property = propertyChild.symbolContained
    if (definition is TypeShape && property != null) {
        val propertyShape = definition.staticProperties
            .firstOrNull { it.symbol == property }
        if (propertyShape != null && fnSymbol in propertyShape.metadata) {
            return StaticMethodCallee(definition, property, propertyShape)
        }
    }

    return null
}
