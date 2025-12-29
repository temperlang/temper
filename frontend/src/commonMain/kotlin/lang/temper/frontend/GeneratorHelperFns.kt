package lang.temper.frontend

import lang.temper.builtin.PureCallableValue
import lang.temper.common.subListToEnd
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModularName
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.DotHelper
import lang.temper.type.ExternalBind
import lang.temper.type.MkType
import lang.temper.type.NominalType
import lang.temper.type.TypeFormal
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.TypeParamRef
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.ActualValues
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.CallTree
import lang.temper.value.CallableValue
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InternalFeatureKeys
import lang.temper.value.InterpreterCallback
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.SpecialFunction
import lang.temper.value.StaySink
import lang.temper.value.StaylessMacroValue
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TFunction
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.and
import lang.temper.value.functionContained
import lang.temper.value.unpackPositionedOr

internal object MakeValueResult : NamedBuiltinFun, PureCallableValue {
    override val name: String = InternalFeatureKeys.MakeValueResult.featureKey
    override val sigs: List<Signature2>? = null
    override val callMayFailPerSe: Boolean = false

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        if (args.size != 1) {
            return cb.fail(MessageTemplate.ArityMismatch, cb.pos, listOf(1))
        }
        val value = args[0]
        val valueResultType = WellKnownTypes.valueResultTypeDefinition
        val valuePropertyName = valueResultType.properties[0].name as ModularName
        val propertyRecord = InstancePropertyRecord(
            mutableMapOf(valuePropertyName to value),
        )
        return Value(propertyRecord, TClass(valueResultType))
    }
}

/**
 * Returns a *Generator* instance given a *GeneratorFn* backed by [WellKnownTypes.generatorFnWrapperTypeDefinition]
 * defined in [lang.temper.frontend.implicits.ImplicitsModule].
 */
class AdaptGeneratorFn private constructor(
    val mayBubble: Boolean,
) : NamedBuiltinFun, CallableValue {
    override val name: String
        get() = if (mayBubble) {
            "adaptGeneratorFn"
        } else {
            "adaptGeneratorFnSafe"
        }
    override val builtinOperatorId
        get() = if (mayBubble) {
            BuiltinOperatorId.AdaptGeneratorFn
        } else {
            BuiltinOperatorId.SafeAdaptGeneratorFn
        }

    val sig: Signature2 = makeGeneratorSig(name) { yieldType, generatorResultType ->
        val generateFnArgType = hackMapOldStyleToNew(
            MkType.fn(
                typeFormals = listOf(),
                valueFormals = listOf(),
                restValuesFormal = null,
                returnType = hackMapNewStyleToOld(
                    if (mayBubble) {
                        MkType2.result(generatorResultType, WellKnownTypes.bubbleType2).get()
                    } else {
                        generatorResultType
                    },
                ),
            ),
        )

        val returnType = MkType2(
            if (mayBubble) {
                WellKnownTypes.generatorTypeDefinition
            } else {
                WellKnownTypes.safeGeneratorTypeDefinition
            },
        )
            .actuals(listOf(yieldType))
            .get()

        Signature2(
            returnType2 = returnType,
            hasThisFormal = false,
            // Fn (): GeneratorResult<YIELD> throws Bubble
            requiredInputTypes = listOf(generateFnArgType),
            typeFormals = listOf(yieldType.definition),
        )
    }
    override val sigs = listOf(sig)
    override val callMayFailPerSe: Boolean = false

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        if (interpMode == InterpMode.Partial) { return NotYet }
        if (args.size != 1) {
            return cb.fail(MessageTemplate.ArityMismatch, cb.pos, listOf(1))
        }
        val generatorFn = args[0]
        val wrapperType = if (mayBubble) {
            WellKnownTypes.generatorFnWrapperTypeDefinition
        } else {
            WellKnownTypes.safeGeneratorFnWrapperTypeDefinition
        }
        val propertyRecord = InstancePropertyRecord(mutableMapOf())
        wrapperType.properties.forEach {
            if (it.abstractness == Abstractness.Concrete) {
                val propertyName = it.name as ModularName
                val propertyValue = when (it.symbol.text) {
                    "_done" -> TBoolean.valueFalse
                    "generatorFn" -> generatorFn
                    else -> run {
                        val message = "Expectations of property names of ${
                            wrapperType.name
                        } by $name do not match those in Implicits"
                        return@invoke cb.fail(MessageTemplate.InternalInterpreterError, cb.pos, listOf(message))
                    }
                }
                propertyRecord.properties[propertyName] = propertyValue
            }
        }
        return Value(propertyRecord, TClass(wrapperType))
    }

    override fun toString(): String = name

    companion object {
        val bubblyInstance = AdaptGeneratorFn(mayBubble = true)
        val safeInstance = AdaptGeneratorFn(mayBubble = false)
    }
}

fun isAdaptGeneratorFnCall(t: Tree) =
    t is CallTree && t.size == 2 &&
        t.child(0).functionContained is AdaptGeneratorFn

/**
 * Given a function returns a callable that closes over the generator
 * and just invokes its next method ignoring the result.
 *
 * This is just an implementation convenience used when interpreting
 * async calls and should not persist in the AST.
 */
object GeneratorStepperFn : CallableValue, StaylessMacroValue {
    // final is not redundant because initialized in init block \m/
    @Suppress("RedundantModalityModifier")
    final override val sigs: List<Signature2> = listOf(
        makeGeneratorSig(NAME) { _, generatorType -> // Fn (Generator<YIELDED>): Fn (): Void
            Signature2(
                returnType2 = hackMapOldStyleToNew(MkType.fn(emptyList(), emptyList(), null, WellKnownTypes.voidType)),
                hasThisFormal = false,
                requiredInputTypes = listOf(generatorType),
            )
        },
    )

    private val instanceSigs: List<Signature2> = listOf(
        Signature2(WellKnownTypes.voidType2, false, listOf()),
    )

    override fun toString(): String = NAME

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val (generator) = args.unpackPositionedOr(1, cb) { return@invoke it }
        return Value(
            object : SpecialFunction {
                override val sigs: List<Signature2> get() = instanceSigs
                override fun toString(): String = "generatorStepper(?)"

                override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult =
                    if (interpMode == InterpMode.Full) {
                        val nextCallHelper = DotHelper(
                            ExternalBind,
                            Symbol("next"),
                            emptyList(),
                        )

                        val callTree = macroEnv.document.treeFarm.grow(macroEnv.pos) {
                            Call(nextCallHelper) {
                                V(generator)
                            }
                        }
                        val boundMethod = macroEnv.dispatchCallTo(
                            callTree,
                            Value(nextCallHelper),
                            callTree.children.subListToEnd(1),
                            interpMode,
                        )
                        boundMethod.and { boundMethodValue ->
                            (TFunction.unpackOrNull(boundMethodValue) as? CallableValue)
                                ?.invoke(ActualValues.Empty, cb, interpMode)
                                ?: cb.fail(
                                    MessageTemplate.ExpectedValueOfType,
                                    values = listOf(TFunction, boundMethodValue),
                                )
                        }
                    } else {
                        NotYet
                    }

                override fun addStays(s: StaySink) {
                    s.whenUnvisited(generator) {
                        generator.addStays(s)
                    }
                }
            },
        )
    }

    private const val NAME = "generatorStepper"
}

private fun makeGeneratorSig(
    name: String,
    makeSig: (TypeParamRef, DefinedNonNullType) -> Signature2,
): Signature2 {
    val counter = WellKnownTypes.voidTypeDefinition.mutationCount
    fun makeTypeFormal(formalName: String, upperBoundsList: List<NominalType>): TypeFormal {
        return TypeFormal(
            Position(ImplicitsCodeLocation, 0, 0),
            BuiltinName(formalName),
            Symbol(formalName),
            Variance.Invariant,
            counter,
            upperBounds = upperBoundsList,
        )
    }
    val yieldFormal = makeTypeFormal("${name}YIELD", listOf(WellKnownTypes.anyValueType))
    val yieldType = MkType2(yieldFormal).get()

    val generatorResultType = MkType2(WellKnownTypes.generatorResultTypeDefinition).actuals(listOf(yieldType)).get()
        as DefinedNonNullType
    return makeSig(yieldType, generatorResultType)
}
