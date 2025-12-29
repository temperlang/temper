package lang.temper.interp

import lang.temper.builtin.Types
import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.name.BuiltinName
import lang.temper.name.Symbol
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.MacroActuals
import lang.temper.value.NotYet
import lang.temper.value.TProblem
import lang.temper.value.TString
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.connectedSymbol
import lang.temper.value.initSymbol
import lang.temper.value.symbolContained

/**
 * `@connected("Context::Key") methodOrPropertyDefinition...`
 * lets us connect types and type members to native code.
 */
internal val connectedDecorator = MetadataDecorator(
    connectedSymbol,
    argumentTypes = listOf(Types.string),
    findDecoratorInsertions = ::findConnectedDecoratorInsertions,
) { args ->
    when (val r = args.evaluate(1, InterpMode.Partial)) {
        NotYet, is Fail -> r
        is Value<*> -> {
            if (r.typeTag == TString) {
                r
            } else {
                Value(
                    LogEntry(
                        level = Log.Error,
                        template = MessageTemplate.ExpectedValueOfType,
                        pos = args.pos(1),
                        values = listOf(TString, r),
                    ),
                    TProblem,
                )
            }
        }
    }
}

val vConnectedDecorator = Value(connectedDecorator)
val connectedDecoratorName = BuiltinName(connectedDecorator.name)

val connectedDecoratorBindings = mapOf(
    connectedDecoratorName to vConnectedDecorator,
    connectedDecoratorName.baseName to vConnectedDecorator,
)

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
