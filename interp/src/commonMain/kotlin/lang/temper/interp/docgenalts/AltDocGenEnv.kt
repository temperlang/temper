package lang.temper.interp.docgenalts

import lang.temper.env.Environment
import lang.temper.interp.immutableEnvironment
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.name.TemperName
import lang.temper.value.Value
import lang.temper.value.ifBuiltinName
import lang.temper.value.returnBuiltinName

private val altGenEnvBindings: Map<TemperName, Value<*>> = buildMap {
    putBuiltinNameAndParsedName(ifBuiltinName, Value(AltIfFn))
    putBuiltinNameAndParsedName(returnBuiltinName, Value(AltReturnFn))
    putBuiltinNameAndParsedName(BuiltinName("while"), Value(AltWhileFn))
}

fun altDocGenEnv(parent: Environment) = immutableEnvironment(
    parent = parent,
    nameToValue = altGenEnvBindings,
    isLongLived = true,
)

private fun MutableMap<TemperName, Value<*>>.putBuiltinNameAndParsedName(
    builtinName: BuiltinName,
    value: Value<*>,
) {
    val parsedName = ParsedName(builtinName.builtinKey)
    put(builtinName, value)
    put(parsedName, value)
}
