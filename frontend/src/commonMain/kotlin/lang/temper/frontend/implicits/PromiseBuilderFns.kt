package lang.temper.frontend.implicits

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.name.ModularName
import lang.temper.type.Abstractness
import lang.temper.type.WellKnownTypes
import lang.temper.value.ActualValues
import lang.temper.value.Fail
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InterpreterCallback
import lang.temper.value.PartialResult
import lang.temper.value.Result
import lang.temper.value.TClass
import lang.temper.value.Value
import lang.temper.value.unpackPositionedOr
import lang.temper.value.void

internal object PromiseBuilderFns {
    private val promiseTypeTag = TClass(WellKnownTypes.promiseTypeDefinition)
    private val promiseBuilderTypeTag = TClass(WellKnownTypes.promiseBuilderTypeDefinition)

    private val promiseField = lazy {
        WellKnownTypes.promiseBuilderTypeDefinition.properties.first { it.abstractness == Abstractness.Concrete }
    }

    object Constructor : SigFnBuilder("PromiseBuilder::constructor") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val (thisValue) = args.unpackPositionedOr(1, cb) { return@invoke it }
            val type = thisValue.typeTag as? TClass
            val propertyRecord = type?.unpackOrNull(thisValue)
                ?: return Fail(
                    LogEntry(
                        Log.Error,
                        MessageTemplate.ExpectedValueOfType,
                        args.pos(0) ?: cb.pos,
                        listOf(WellKnownTypes.promiseBuilderType, thisValue),
                    ),
                )
            val newPromise = Value(
                InstancePropertyRecord(mutableMapOf()),
                promiseTypeTag,
            )
            cb.promises?.registerNewPromise(newPromise.stateVector, cb.pos)
            propertyRecord.properties[promiseField.value.name as ModularName] = newPromise
            return void
        }
    }

    abstract class ResolveHelper(connectionKey: String) : SigFnBuilder(connectionKey) {
        protected abstract val arity: Int
        protected abstract fun getResolution(args: List<Value<*>>): Result

        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val unpacked = args.unpackPositionedOr(arity, cb) { return@invoke it }
            val promiseBuilderValue = unpacked.first()
            val resolution = getResolution(unpacked)

            val promiseBuilderRecord = promiseBuilderTypeTag.unpackOrNull(promiseBuilderValue)
                ?: return cb.fail(
                    MessageTemplate.ExpectedValueOfType,
                    args.pos(0) ?: cb.pos,
                    listOf(promiseBuilderTypeTag, promiseBuilderValue),
                )
            val promiseValue = promiseBuilderRecord.properties
                .getValue(promiseField.value.name as ModularName)
            val promiseKey = promiseTypeTag.unpack(promiseValue)
            cb.promises?.resolve(promiseKey, resolution)
            return void
        }
    }

    object BreakPromise : ResolveHelper("PromiseBuilder::breakPromise") {
        override val arity: Int = 1
        override fun getResolution(args: List<Value<*>>): Result = Fail
    }

    object Complete : ResolveHelper("PromiseBuilder::complete") {
        override val arity: Int = 2
        override fun getResolution(args: List<Value<*>>): Result = args[1]
    }
}
