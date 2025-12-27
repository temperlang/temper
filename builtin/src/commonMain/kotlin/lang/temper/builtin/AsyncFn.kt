package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.type.MkType
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.ActualValues
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.InternalFeatureKeys
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Promises
import lang.temper.value.SpecialFunction
import lang.temper.value.TClass
import lang.temper.value.TFunction
import lang.temper.value.Value
import lang.temper.value.passedWithType
import lang.temper.value.unpackPositionedOr
import lang.temper.value.void

/**
 * <!-- snippet: builtin/async : # `async` builtin -->
 * # `async { ... }`
 *
 * The *async* builtin function takes a safe generator block and
 * runs it out of band.
 *
 * TODO: example
 */
object AsyncFn : NamedBuiltinFun, SpecialFunction {
    override val name: String = "async"
    override val builtinOperatorId: BuiltinOperatorId = BuiltinOperatorId.Async

    private val sig =
        Signature2( // Fn (Fn (): SafeGenerator<Empty>): Void
            returnType2 = WellKnownTypes.voidType2,
            requiredInputTypes = listOf(
                hackMapOldStyleToNew(
                    MkType.fnDetails(
                        emptyList(),
                        listOf(),
                        null,
                        returnType = MkType.nominal(
                            WellKnownTypes.safeGeneratorTypeDefinition,
                            listOf(WellKnownTypes.emptyType),
                        ),
                    ),
                ),
            ),
            hasThisFormal = false,
        )

    override val sigs: List<Signature2> = listOf(sig)

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        if (interpMode == InterpMode.Partial) {
            return NotYet
        }
        val promises = macroEnv.promises
        val args = macroEnv.args
        val (generatorFactory) = args.unpackPositionedOr(1, macroEnv) {
            return@invoke it
        }
        val generatorArgPos = args.pos(0)
        val generatorFactoryFn = TFunction.unpackOrNull(generatorFactory)
            as? CallableValue
            ?: return Fail(
                LogEntry(
                    Log.Error,
                    MessageTemplate.ExpectedValueOfType,
                    generatorArgPos,
                    listOf(sig.allValueFormals.first().reifiedType, generatorFactory),
                ),
            )

        val generator = generatorFactoryFn.invoke(ActualValues.from(emptyList()), macroEnv, interpMode)
        if (generator !is Value<*>) {
            return generator
        }

        if (generator.typeTag !is TClass) {
            return Fail(
                LogEntry(
                    Log.Error,
                    MessageTemplate.ExpectedValueOfType,
                    generatorArgPos,
                    listOf(WellKnownTypes.safeGeneratorTypeDefinition.name, generator),
                ),
            )
        }
        // Convert the generator to a step function so that it can be run asynchronously.
        val stepGenerator = macroEnv.getFeatureImplementation(
            InternalFeatureKeys.GeneratorStepperFn.featureKey,
        ).passedWithType(TFunction) { stepper ->
            val call = macroEnv.document.treeFarm.grow(generatorArgPos) {
                Call(stepper) {
                    V(generator)
                }
            }
            macroEnv.dispatchCallTo(
                call.child(0),
                Value(stepper),
                listOf(call.child(1)),
                interpMode,
            )
        }

        return if (stepGenerator is Value<*>) {
            // Schedule the function
            val stepGeneratorFn = TFunction.unpack(stepGenerator)
            promises.enqueueReadyTask(Promises.Awaiter(generatorArgPos, stepGeneratorFn))
            void
        } else { // propagate failure
            stepGenerator
        }
    }
}
