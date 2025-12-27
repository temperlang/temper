package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.value.AwaitMacroEnvironment
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.Panic
import lang.temper.value.PartialResult
import lang.temper.value.PromiseKey
import lang.temper.value.Promises
import lang.temper.value.SpecialFunction
import lang.temper.value.TClass
import lang.temper.value.Value
import lang.temper.value.YieldingFnKind

sealed class YieldingFn : SpecialFunction, NamedBuiltinFun {
    abstract val yieldingFnKind: YieldingFnKind
    override val name: String get() = yieldingFnKind.builtinName.builtinKey
}

/**
 * Called from a generator function body, pauses execution.
 *
 * <!-- snippet: builtin/yield : # `yield()` builtin -->
 * # `yield()`
 * Called from the body of a generator function, to pause the body
 * and cause [snippet/type/Generator/method/next] to return.
 *
 * The [block lambda][snippet/syntax/BlockLambda.svg] below has an
 * `extends GeneratorFn`.
 *
 * A generator function may use the no-argument version of `yield`
 * in which case its wrapper generator's [snippet/type/Generator/method/next]
 * returns the [snippet/type/Empty] value.
 *
 * If the generator function wishes to convey a value to its scheduler,
 * it may use `yield(x)` where `x` is the value returned by
 * [snippet/type/Generator/method/next].
 *
 * Note: `yield` is syntactically like `return` and `throw` in that
 * parentheses around its argument are optional.
 *
 * ```temper
 * // Two co-operating functions each have print
 * // calls which interleave because the generator
 * // function uses yield() to return control to
 * // its caller temporarily.
 *
 * //!outputs "S: Before first call"
 * //!outputs "C: In coroutine, before yield"
 * //!outputs "S: Between calls"
 * //!outputs "C: Coroutine resumed"
 * //!outputs "S: After calls"
 *
 * // scheduleTwice manually schedules execution of the generator/coroutine.
 * let scheduleTwice(generatorFactory: fn (): SafeGenerator<Empty>): Void {
 *   let generator: SafeGenerator<Empty> = generatorFactory();
 *   console.log("S: Before first call");
 *   generator.next();
 *   console.log("S: Between calls");
 *   generator.next();
 *   console.log("S: After calls");
 * }
 *
 * // The block lambda declares a coroutine constructor
 * // which yields between two print statements causing
 * // them to interleave with the statement above.
 * scheduleTwice { (): GeneratorResult<Empty> extends GeneratorFn =>
 *   console.log("C: In coroutine, before yield");
 *   yield; // Return control to the caller
 *   console.log("C: Coroutine resumed");
 * }
 * ```
 *
 * Above, the generator function has type `(): Empty extends GeneratorFn`
 * which specifies what the block lambda does.  But generator functions
 * are received by their receiver function as a factory for generator instances:
 * For example, `scheduleTwice` receives a `(fn (): Generator<Empty, Nothing>) & GeneratorFn`.
 */
object YieldFn : YieldingFn() {
    override val yieldingFnKind = YieldingFnKind.yield

    override val sigs: List<Signature2> = run {
        val (tDef, t) = makeTypeFormal(name, "T")
        listOf(
            Signature2(WellKnownTypes.voidType2, false, listOf()),
            Signature2(
                returnType2 = WellKnownTypes.voidType2,
                hasThisFormal = false,
                requiredInputTypes = listOf(t),
                typeFormals = listOf(tDef),
            ),
        )
    }
    override val callMayFailPerSe: Boolean = false

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult = when (interpMode) {
        InterpMode.Full -> {
            // This should be handled by the interpreter as a special form.
            macroEnv.explain(MessageTemplate.YieldingOutsideGeneratorFn, values = listOf(this))
            throw Panic()
        }
        InterpMode.Partial -> NotYet
    }
}

/**
 * Called from a generator function body, pauses execution.
 *
 * <!-- snippet: builtin/await : # `await` builtin -->
 * # `await(promise)`
 * TODO: document me
 */
object AwaitFn : YieldingFn() {
    override val yieldingFnKind = YieldingFnKind.await

    override val sigs: List<Signature2> = run {
        val (rDef, r) = makeTypeFormal(name, "R")
        val promiseR =
            MkType2(WellKnownTypes.promiseTypeDefinition).actuals(listOf(r)).get()
        listOf(
            Signature2(
                returnType2 = MkType2.result(r, WellKnownTypes.bubbleType2).get(),
                hasThisFormal = false,
                requiredInputTypes = listOf(promiseR),
                typeFormals = listOf(rDef),
            ),
        )
    }

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        return when (interpMode) {
            InterpMode.Full -> {
                val args = macroEnv.args
                if (args.size != 1) {
                    return Fail(
                        LogEntry(
                            level = Log.Error,
                            template = MessageTemplate.ArityMismatch,
                            pos = macroEnv.pos,
                            values = listOf(1),
                        ),
                    )
                }
                val promiseResult = args.evaluate(0, interpMode)
                when (promiseResult) {
                    is NotYet, is Fail -> return promiseResult
                    is Value<*> -> {}
                }
                val typeTag = promiseResult.typeTag
                if (typeTag !is TClass || typeTag.typeShape != WellKnownTypes.promiseTypeDefinition) {
                    return Fail(
                        LogEntry(
                            level = Log.Error,
                            template = MessageTemplate.ExpectedValueOfType,
                            pos = macroEnv.pos,
                            values = listOf(
                                MkType2(WellKnownTypes.promiseTypeDefinition).get(),
                                promiseResult,
                            ),
                        ),
                    )
                }
                val promiseInstance = typeTag.unpack(promiseResult)
                val promiseKey: PromiseKey = promiseInstance
                // This will be handled by the interpreter as a special form after registering
                // itself for awakening
                val promises = macroEnv.promises
                val result = promises[promiseKey]
                if (result != null) {
                    result
                } else {
                    val awaiter = getAwaiter(macroEnv)
                        ?: return macroEnv.fail(MessageTemplate.YieldingOutsideGeneratorFn, values = listOf(this))
                    promises.await(promiseKey, awaiter, macroEnv.args.pos(0))
                    NotYet
                }
            }
            InterpMode.Partial -> NotYet
        }
    }

    private fun getAwaiter(macroEnv: MacroEnvironment): Promises.Awaiter? =
        (macroEnv as? AwaitMacroEnvironment)?.awaiter
}
