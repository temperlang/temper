package lang.temper.frontend.implicits

import lang.temper.env.InterpMode
import lang.temper.name.ModularName
import lang.temper.value.ActualValues
import lang.temper.value.Fail
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InterpreterCallback
import lang.temper.value.PartialResult
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TMap
import lang.temper.value.TMapBuilder
import lang.temper.value.Value
import lang.temper.value.void

internal object MapFns {
    object Constructor : SigFnBuilder("Map::constructor") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            // Associate preserves order.
            val map = TList.unpackContent(args[1]).associate { unpackPair(it) ?: return@invoke Fail }
            setContent(args, Value(map, TMap))
            return void
        }
    }
}

internal object MapBuilderFns {
    object Constructor : SigFnBuilder("MapBuilder::constructor") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            setContent(args, Value(linkedMapOf(), TMapBuilder))
            return void
        }
    }

    object Clear : SigFnBuilder("MapBuilder::clear") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            TMapBuilder.unpackContent(args[0]).clear()
            return void
        }
    }

    object Remove : SigFnBuilder("MapBuilder::remove") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return TMapBuilder.unpackContent(args[0]).remove(args[1]) ?: Fail
        }
    }

    object Set : SigFnBuilder("MapBuilder::set") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            TMapBuilder.unpackContent(args[0])[args[1]] = args[2]
            return void
        }
    }
}

internal object MappedFns {
    object Get : SigFnBuilder("Mapped::get") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return TMap.unpackContent(args[0]).getOrDefault(args[1], Fail)
        }
    }

    object GetOr : SigFnBuilder("Mapped::getOr") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return TMap.unpackContent(args[0]).getOrDefault(args[1], args[2])
        }
    }

    object Has : SigFnBuilder("Mapped::has") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(TMap.unpackContent(args[0]).contains(args[1]), TBoolean)
        }
    }

    object Keys : SigFnBuilder("Mapped::keys") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(TMap.unpackContent(args[0]).keys.toList(), TList)
        }
    }

    object Values : SigFnBuilder("Mapped::values") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(TMap.unpackContent(args[0]).values.toList(), TList)
        }
    }

    object ToMap : SigFnBuilder("Mapped::toMap") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(LinkedHashMap(TMap.unpackContent(args[0])), TMap)
        }
    }

    object ToMapBuilder : SigFnBuilder("Mapped::toMapBuilder") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(LinkedHashMap(TMap.unpackContent(args[0])), TMapBuilder)
        }
    }

    object ToListWith : SigFnBuilder("Mapped::toListWith") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val makeEntry = args[1]
            val entries = TMap.unpackContent(args[0]).map { entry ->
                when (val result = cb.apply(makeEntry, ActualValues.from(entry.key, entry.value), interpMode)) {
                    is Value<*> -> result
                    else -> return@invoke Fail
                }
            }
            return Value(entries, TList)
        }
    }

    object ToListBuilderWith : SigFnBuilder("Mapped::toListWith") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val makeEntry = args[1]
            val entries = TMap.unpackContent(args[0]).map { entry ->
                when (val result = cb.apply(makeEntry, ActualValues.from(entry.key, entry.value), interpMode)) {
                    is Value<*> -> result
                    else -> return@invoke Fail
                }
            }
            return Value(entries.toMutableList(), TListBuilder)
        }
    }

    object ForEach : SigFnBuilder("Mapped::forEach") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val callback = args[1]
            TMap.unpackContent(args[0]).forEach { entry ->
                if (cb.apply(callback, ActualValues.from(entry.key, entry.value), interpMode) !is Value<*>) {
                    return@invoke Fail
                }
            }
            return void
        }
    }
}

private fun setContent(args: ActualValues, content: Value<*>) {
    val thisValue = args[0]
    val thisType = args[0].typeTag as TClass
    val instanceRecord = thisType.unpack(thisValue)
    for (propertyShape in thisType.typeShape.properties) {
        val name = propertyShape.name as ModularName
        when (base(name)) {
            "content" -> instanceRecord.properties[name] = content
            else -> {}
        }
    }
}

private fun unpackPair(value: Value<*>): Pair<Value<*>, Value<*>>? {
    if (value.stateVector !is InstancePropertyRecord) {
        return null
    }
    var first: Value<*>? = null
    var second: Value<*>? = null
    val properties = value.properties()

    for ((name, propValue) in properties) {
        when (base(name)) {
            "key" -> first = propValue
            "value" -> second = propValue
            else -> unexpectedName(name)
        }
    }
    if (first == null || second == null) {
        error("No content")
    }
    return first to second
}
