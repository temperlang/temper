package lang.temper.frontend.implicits

import lang.temper.common.compatRemoveLast
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.name.ModularName
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.InterpreterCallback
import lang.temper.value.NotYet
import lang.temper.value.Panic
import lang.temper.value.PartialResult
import lang.temper.value.TClass
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.unpackOrFail
import lang.temper.value.void
import kotlin.math.max
import kotlin.math.min

internal object ListFns {
    object Length : SigFnBuilder("List::length") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(TList.unpackContent(args[0]).size, TInt)
        }
    }

    object Get : SigFnBuilder("List::get") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TList.unpackContent(args[0])
            val i = TInt.unpackOrFail(args, 1, cb, interpMode) { return@invoke it }
            if (i < 0 || i > Int.MAX_VALUE) {
                throw Panic()
            }
            return ls.getOrNull(i.toInt()) ?: Fail
        }
    }

    object GetOr : SigFnBuilder("List::getOr") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TList.unpackContent(args[0])
            val i = TInt.unpackOrFail(args, 1, cb, interpMode) { return@invoke it }
            val fallback = args[2]
            return if (i < 0 || i > Int.MAX_VALUE.toLong()) {
                fallback
            } else {
                ls.getOrNull(i.toInt()) ?: fallback
            }
        }
    }

    object Slice : SigFnBuilder("List::slice") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TList.unpackContent(args[0])
            val startInclusive = TInt.unpackOrFail(args, 1, cb, interpMode) { return@invoke it }
            val endExclusive = TInt.unpackOrFail(args, 2, cb, interpMode) { return@invoke it }
            val size = ls.size
            val startInclusiveAdjusted = min(max(0, startInclusive.toInt()), size)
            val endExclusiveAdjusted = min(max(endExclusive.toInt(), startInclusiveAdjusted), size)
            val slice = (startInclusiveAdjusted until endExclusiveAdjusted).map { ls[it] }
            return Value(slice, TList)
        }
    }

    object Map : SigFnBuilder("List::map") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TList.unpackContent(args[0])
            val f = TFunction.unpackOrFail(args, 1, cb, interpMode) { return@invoke it }
            if (f !is CallableValue) {
                return Fail
            }
            val elements = ls.map {
                when (val result = f.invoke(ActualValues.from(it), cb, interpMode)) {
                    is Fail, NotYet -> return@invoke result
                    is Value<*> -> result
                }
            }
            return Value(elements, TList)
        }
    }

    object MapDropping : SigFnBuilder("List::mapDropping") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TList.unpackContent(args[0])
            val f = TFunction.unpackOrFail(args, 1, cb, interpMode) { return@invoke it }
            if (f !is CallableValue) {
                return Fail
            }
            val elements = ls.mapNotNull {
                when (val result = f.invoke(ActualValues.from(it), cb, interpMode)) {
                    is Fail, NotYet -> null
                    is Value<*> -> result
                }
            }
            return Value(elements, TList)
        }
    }

    object Filter : SigFnBuilder("List::filter") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TList.unpackContent(args[0])
            val f = TFunction.unpackOrFail(args, 1, cb, interpMode) { return@invoke it }
            if (f !is CallableValue) {
                return Fail
            }
            return filterValues(ls, f, cb)
        }
    }

    object Join : SigFnBuilder("List::join") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TList.unpackContent(args[0])
            val sep = TString.unpackOrFail(args, 1, cb, interpMode) { return@invoke it }
            val f = TFunction.unpackOrFail(args, 2, cb, interpMode) { return@invoke it }
            if (f !is CallableValue) {
                return Fail
            }
            return joinDropping(ls, sep, f, cb)
        }
    }

    object Sorted : SigFnBuilder("Listed::sorted") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val elements = TList.unpackContent(args[0]).toMutableList()
            return sort(args, cb, interpMode, elements) ?: Value(elements.toList(), TList)
        }
    }
}

internal object ListBuilderFns {
    object Add : SigFnBuilder("ListBuilder::add", impure = true) {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TListBuilder.unpackContent(args[0])
            val value = args[1]
            val at = unpackSizeBounded(args, 2, ls, cb, interpMode) { return@invoke it }
            ls.add(at, value)
            return void
        }
    }

    object Constructor : SigFnBuilder("ListBuilder::constructor") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val content = Value(mutableListOf(), TListBuilder)
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
            return void
        }
    }

    object AddAll : SigFnBuilder("ListBuilder::addAll", impure = true) {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TListBuilder.unpackContent(args[0])
            val extra = TList.unpackContent(args[1])
            val at = unpackSizeBounded(args, 2, ls, cb, interpMode) { return@invoke it }
            ls.addAll(at, extra)
            return void
        }
    }

    object Clear : SigFnBuilder("ListBuilder::clear", impure = true) {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            TListBuilder.unpackContent(args[0]).clear()
            return void
        }
    }

    object RemoveLast : SigFnBuilder("ListBuilder::removeLast", impure = true) {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TListBuilder.unpackContent(args[0])
            return when (ls.isEmpty()) {
                true -> throw Panic()
                false -> ls.compatRemoveLast()
            }
        }
    }

    object Set : SigFnBuilder("ListBuilder::set", impure = true) {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TListBuilder.unpackContent(args[0])
            val i = TInt.unpackOrFail(args, 1, cb, interpMode) { return@invoke it }
            val index = i.toInt()
            val newValue = args[2]
            if (index in ls.indices) {
                ls[index] = newValue
            }
            return void
        }
    }

    object Sort : SigFnBuilder("ListBuilder::sort") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val elements = TListBuilder.unpackContent(args[0])
            return sort(args, cb, interpMode, elements) ?: Value(elements, TListBuilder)
        }
    }

    object Splice : SigFnBuilder("ListBuilder::splice", impure = true) {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TListBuilder.unpackContent(args[0])
            val index = TInt.unpackWithNullDefault(args, 1, 0, cb, interpMode) {
                return@invoke it
            }.toInt().coerceIn(0, ls.size)
            val removeEnd = TInt.unpackWithNullDefault(args, 2, ls.size, cb, interpMode) {
                return@invoke it
            }.toInt().coerceIn(0, ls.size - index) + index

            @Suppress("MagicNumber")
            val newValues = when (val arg = args[3]) {
                TNull.value -> emptyList()
                else -> TList.unpackContent(arg)
            }
            // Get mutable view to copy out then replace.
            val subList = ls.subList(index, removeEnd)
            val result = Value(subList.toList(), TList)
            subList.clear()
            subList.addAll(newValues)
            // Good to go.
            return result
        }
    }

    object Reverse : SigFnBuilder("ListBuilder::reverse", impure = true) {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TListBuilder.unpackContent(args[0])
            val size = ls.size
            val lastIndex = size - 1
            val mid = size / 2
            for (i in 0 until mid) {
                val j = lastIndex - i
                val a = ls[i]
                ls[i] = ls[j]
                ls[j] = a
            }
            return void
        }
    }

    object ToList : SigFnBuilder("ListBuilder::toList") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val ls = TList.unpackContent(args[0])
            return Value(ls.toList(), TList)
        }
    }
}

private fun sort(
    args: ActualValues,
    cb: InterpreterCallback,
    interpMode: InterpMode,
    elements: MutableList<Value<*>>,
): PartialResult? {
    val f = TFunction.unpackOrFail(args, 1, cb, interpMode) { return@sort it }
    if (f !is CallableValue) {
        return Fail
    }
    var failure: PartialResult? = null
    runCatching {
        elements.sortWith { a, b ->
            when (val result = f.invoke(ActualValues.from(a, b), cb, interpMode)) {
                is Fail, NotYet -> {
                    failure = result
                    error("failure")
                }
                is Value<*> -> TInt.unpackOrNull(result)?.toInt() ?: run {
                    failure = cb.fail(
                        MessageTemplate.ExpectedValueOfType,
                        pos = args.pos(0) ?: cb.pos,
                        values = listOf(TList, result),
                    )
                    error("failure")
                }
            }
        }
    }.onFailure { return failure!! }
    return null
}

private inline fun unpackSizeBounded(
    args: ActualValues,
    index: Int,
    ls: MutableList<Value<*>>,
    cb: InterpreterCallback,
    interpMode: InterpMode,
    onWrongResult: (PartialResult) -> Nothing,
): Int {
    val value = TInt.unpackWithNullDefault(args, index, ls.size, cb, interpMode, onWrongResult).toInt()
    return when (value >= 0 && value <= ls.size) {
        true -> value
        false -> onWrongResult(Fail)
    }
}
