package lang.temper.frontend.coroutine

import lang.temper.builtin.makeTypeFormal
import lang.temper.env.InterpMode
import lang.temper.type.InvalidType
import lang.temper.type.NominalType
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.CallTypeInferences
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.Panic
import lang.temper.value.PartialResult

/**
 * When converting coroutines to regular functions, these special functions implement
 * primitive operations related to promise scheduling.
 *
 * Backends that can handle promises at a low level should connect these.
 *
 * These functions are not used when interpreting Temper code, and only serve to
 * aid in translation.
 */
object CoroHelperSpecials {
    /**
     * `awakeUpon(promise, generator)` indicates that *generator* should be stepped
     * upon the resolution of *promise*.
     */
    object ConvertedCoroutineAwakeUponFn : NamedBuiltinFun, BuiltinStatelessMacroValue {
        override val name = "awakeUpon"

        val sig = run {
            // Fn <T>(Promise<T>, Generator<T>): Void
            val (t, tt) = makeTypeFormal(name, "Y")
            Signature2(
                returnType2 = WellKnownTypes.voidType2,
                hasThisFormal = false,
                requiredInputTypes = listOf(
                    MkType2(WellKnownTypes.promiseTypeDefinition).actuals(listOf(tt)).get(),
                    MkType2(WellKnownTypes.generatorTypeDefinition).actuals(listOf(tt)).get(),
                ),
                typeFormals = listOf(t),
            )
        }

        override val sigs = listOf(sig)

        override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
            throw Panic()
        }

        fun callTypeInferences(promiseType: StaticType) = CallTypeInferences(
            WellKnownTypes.voidType,
            sig,
            mapOf(sig.typeFormals[0] to ((promiseType as? NominalType)?.bindings[0] ?: InvalidType)),
            listOf(),
        )
    }

    /**
     * `getPromiseResultSync(promise)` means unpack promise.
     */
    object GetPromiseResultSyncFn : NamedBuiltinFun, BuiltinStatelessMacroValue {
        override val name = "getPromiseResultSync"

        val sig = run {
            val (t, tt) = makeTypeFormal(name, "Y")
            // Fn <Y>(Promise<Y>): Y | Bubble
            Signature2(
                returnType2 = MkType2(WellKnownTypes.resultTypeDefinition)
                    .actuals(listOf(tt, WellKnownTypes.bubbleType2))
                    .get(),
                hasThisFormal = false,
                requiredInputTypes = listOf(
                    MkType2(WellKnownTypes.promiseTypeDefinition).actuals(listOf(tt)).get(),
                ),
                typeFormals = listOf(t),
            )
        }

        override val sigs = listOf(sig)

        override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
            throw Panic()
        }

        fun callTypeInferences(promiseType: StaticType): CallTypeInferences {
            val promiseArg = ((promiseType as? NominalType)?.bindings[0] ?: InvalidType) as StaticType
            return CallTypeInferences(
                promiseArg,
                sig,
                mapOf(sig.typeFormals[0] to promiseArg),
                listOf(),
            )
        }
    }
}
