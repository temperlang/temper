package lang.temper.interp

import lang.temper.common.Log
import lang.temper.log.MessageTemplate
import lang.temper.name.Symbol
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.MacroActuals
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.connectedSymbol
import lang.temper.value.initSymbol
import lang.temper.value.restFormalSymbol
import lang.temper.value.symbolContained
import lang.temper.value.typeDeclSymbol
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
) { args ->
    // At this stage, we don't need the location context.
    if (!(isProcessingCore || isProcessingStd(sharedLocationContext = null))) run check@{
        val metadata = (args.rawTreeList.first() as? DeclTree)?.parts?.metadataSymbolMap ?: return@check
        when {
            typeDeclSymbol in metadata -> {
                log(Log.Error, MessageTemplate.UserConnectedNotFun, pos, listOf())
            }
            else -> when (val init = metadata[initSymbol]?.target) {
                is FunTree -> when {
                    // Seems we aren't able to gather formal params yet when this macro is called.
                    init.children.any { maybeParam ->
                        when (val maybeParamParts = (maybeParam as? DeclTree)?.parts) {
                            null -> false
                            else -> restFormalSymbol in maybeParamParts.metadataSymbolMap
                        }
                    } -> {
                        log(Log.Error, MessageTemplate.UserConnectedFunHasRest, pos, listOf())
                    }
                    else -> {
                        // We support connected functions without rest params at this time.
                    }
                }
                else -> {
                    log(Log.Error, MessageTemplate.UserConnectedNotFun, pos, listOf())
                }
            }
        }
    }
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
