package lang.temper.interp

import lang.temper.builtin.Types
import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.name.Symbol
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.MacroActuals
import lang.temper.value.NotYet
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.connectedBackendsFrom
import lang.temper.value.connectedSymbol
import lang.temper.value.initSymbol
import lang.temper.value.symbolContained
import lang.temper.value.typeDeclSymbol
import lang.temper.value.void

/**
 * <!-- snippet: builtin/@connected -->
 * # `@connected` decorator
 * Connect types, members, and functions to native code. Each backend defines
 * specific conventions for connected backend code. Internal backend translation
 * logic can also key on the QName of connected entities.
 *
 * A connected function may have a Temper body, its reference implementation,
 * and list the backends that have native code for it. Other backends, and the
 * interpreter, use the body.
 *
 * ```temper inert
 * @connected(["cpp", "rust"])
 * export let foldCase(c: Int): Int {
 *   // ... pure Temper ...
 * }
 * ```
 *
 * A listed backend must supply native code, and `--no-connected` makes every
 * backend use bodies wherever they exist. Without a list, every backend must
 * supply native code, and the body, if any, serves only the interpreter.
 */
internal val connectedDecorator = MetadataDecorator(
    connectedSymbol,
    optionalArgumentTypes = listOf(Types.anyValue),
    findDecoratorInsertions = ::findConnectedDecoratorInsertions,
) decoratedValue@{ args ->
    // An optional list of backends with native code, as in `@connected(["cpp", "rust"])`,
    // gets stored as the metadata value. See FnParts.connectedBackends.
    // Partial interpretation doesn't build lists, so evaluate fully.
    val backends: Value<*> = when (args.size) {
        1 -> void
        else -> when (val listResult = args.evaluate(1, InterpMode.Full)) {
            is NotYet, is Fail -> return@decoratedValue listResult
            is Value<*> -> when (connectedBackendsFrom(listResult)) {
                null -> {
                    // Report it but keep the declaration, connected as if without a list.
                    log(Log.Error, MessageTemplate.UserConnectedBackendsNotNames, args.pos(1), listOf(listResult))
                    void
                }
                else -> listResult
            }
        }
    }
    // Whether we have errors or not, move on.
    backends.also check@{
        isProcessingCore && return@check
        // At this stage, we don't need the location context.
        isProcessingStd(sharedLocationContext = null) && return@check
        val metadata = (args.rawTreeList.first() as? DeclTree)?.parts?.metadataSymbolMap ?: return@check
        when {
            typeDeclSymbol in metadata -> {
                log(Log.Error, MessageTemplate.UserConnectedNotFun, pos, listOf())
            }
            else -> when (metadata[initSymbol]?.target) {
                is FunTree -> {
                    // Neither instance nor static methods get in here because of different tree structures.
                    // That's good for now.
                }
                else -> {
                    log(Log.Error, MessageTemplate.UserConnectedNotFun, pos, listOf())
                }
            }
        }
    }
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
