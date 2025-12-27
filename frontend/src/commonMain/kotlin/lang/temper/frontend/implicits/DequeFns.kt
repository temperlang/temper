package lang.temper.frontend.implicits

import lang.temper.env.InterpMode
import lang.temper.name.ModularName
import lang.temper.value.ActualValues
import lang.temper.value.InterpreterCallback
import lang.temper.value.Panic
import lang.temper.value.PartialResult
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TListBuilder
import lang.temper.value.Value
import lang.temper.value.void

internal interface DequeFns {
    object Constructor : SigFnBuilder("Deque::constructor") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val content = Value(ArrayDeque(), TListBuilder)
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

    object Add : SigFnBuilder("Deque::add", impure = true) {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val deque = TListBuilder.unpackContent(args[0]) as ArrayDeque
            val element = args[1]
            deque.add(element)
            return void
        }
    }

    object IsEmpty : SigFnBuilder("Deque::isEmpty") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val deque = TListBuilder.unpackContent(args[0])
            return TBoolean.value(deque.isEmpty())
        }
    }

    object RemoveFirst : SigFnBuilder("Deque::removeFirst", impure = true) {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val deque = TListBuilder.unpackContent(args[0]) as ArrayDeque
            if (deque.isEmpty()) {
                throw Panic()
            }
            return deque.removeFirst()
        }
    }
}
