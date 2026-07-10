package lang.temper.interp

import lang.temper.name.Symbol
import lang.temper.value.CallTree
import lang.temper.value.FunTree
import lang.temper.value.MacroActuals
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.connectedSymbol
import lang.temper.value.initSymbol
import lang.temper.value.symbolContained
import lang.temper.value.void

/**
 * <!-- snippet: builtin/@connected -->
 * # `@connected` decorator
 * Connect types, members, and functions to native code. Each backend defines
 * specific conventions for connected backend code. Internal backend translation
 * logic can also key on the QName of connected entities.
 */
internal val connectedDecorator = MetadataDecorator(
    connectedSymbol,
    findDecoratorInsertions = ::findConnectedDecoratorInsertions,
) {
    void
}

val vConnectedDecorator = Value(connectedDecorator)

private fun findConnectedDecoratorInsertions(args: MacroActuals, symbolKey: Symbol): List<Pair<Tree, Int>> {
    val result = findDefaultDecoratorInsertions(args, symbolKey).toMutableList()
    // Also decorate the function itself for easier access later.
    // TODO(tjp, interp): Use only the nested location?
    val top = args.valueTree(0)
    for ((childIndex, child) in top.children.withIndex()) {
        if (child.symbolContained == initSymbol) {
            when (val value = top.childOrNull(childIndex + 1)) {
                is CallTree -> value.children.find { it is FunTree }
                is FunTree -> value
                else -> null
            }?.let { result += it to findDefaultDecoratorInsertionPoint(it) }
            break
        }
    }
    return result
}
