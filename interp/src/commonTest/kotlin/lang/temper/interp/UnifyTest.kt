package lang.temper.interp

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.ListBackedLogSink
import lang.temper.common.assertStructure
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.testCodeLocation
import lang.temper.env.InterpMode
import lang.temper.log.FailLog
import lang.temper.log.LogSink
import lang.temper.log.Position
import lang.temper.name.Symbol
import lang.temper.stage.Readiness
import lang.temper.stage.Stage
import lang.temper.type.WellKnownTypes
import lang.temper.type.withTypeTestHarness
import lang.temper.type2.AnySignature
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.Signature2
import lang.temper.type2.ValueFormalKind
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.DynamicMessage
import lang.temper.value.Fail
import lang.temper.value.InternalFeatureKey
import lang.temper.value.InterpreterCallback
import lang.temper.value.Promises
import lang.temper.value.ReifiedType
import lang.temper.value.Resolutions
import lang.temper.value.Result
import lang.temper.value.StaySink
import lang.temper.value.TBoolean
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TString
import lang.temper.value.TypeTag
import lang.temper.value.Value
import lang.temper.value.passedWithType
import lang.temper.value.unify
import kotlin.test.Test

class UnifyTest {
    private fun assertResolution(
        expected: String,
        dynamicMessage: DynamicMessage,
        signature: AnySignature,
    ) {
        val cb = object : InterpreterCallback {
            override fun apply(f: Value<*>, args: ActualValues, interpMode: InterpMode) =
                f.passedWithType(TFunction) {
                    if (it is CallableValue) {
                        it.invoke(args, this, interpMode)
                    } else {
                        Fail
                    }
                }

            override val logSink: LogSink = ListBackedLogSink()

            override val failLog = FailLog(logSink = logSink)

            override val stage = Stage.Type

            override val pos = Position(testCodeLocation, 0, 1)

            override val readiness = Readiness.Ready

            override fun getFeatureImplementation(key: InternalFeatureKey): Result = Fail

            override val promises: Promises? = null
        }

        val resolutions = Resolutions(cb)
        val reorderedActuals =
            unify(dynamicMessage, signature, resolutions)
                ?.toPositionalActuals(cb)
        assertStructure(
            expected,

            object : Structured {
                override fun destructure(structureSink: StructureSink) = structureSink.obj {
                    key("args") {
                        if (reorderedActuals != null) {
                            arr {
                                for (index in reorderedActuals.indices) {
                                    arr {
                                        val key = reorderedActuals.key(index)
                                        if (key != null) {
                                            value(key.text)
                                        }
                                        value(reorderedActuals.result(index))
                                    }
                                }
                            }
                        } else {
                            nil()
                        }
                    }
                    key("resolutions") { value(resolutions) }
                    val problem = resolutions.problem
                    key("problem", isDefault = problem == null) { value(problem) }
                }
            },
        )
    }

    @Test
    fun neitherTypeArgumentsNorValueArguments() {
        assertResolution(
            """
            {
              args: [],
              resolutions: {
                contradiction: false
              }
            }
            """,
            DynamicMessage(
                valueActuals = ActualValues.Empty,
                interpMode = InterpMode.Full,
            ),
            MacroSignature(
                returnType = null,
                requiredValueFormals = emptyList(),
                typeFormals = emptyList(),
            ),
        )
    }

    @Test
    fun oneInputWithConcreteType() {
        assertResolution(
            """
            {
              args: [ ["false: Boolean"] ],
              resolutions: {
                contradiction: false
              }
            }
            """,
            DynamicMessage(
                valueActuals = ActualValues.from(TBoolean.valueFalse),
                interpMode = InterpMode.Full,
            ),
            MacroSignature(
                returnType = null,
                requiredValueFormals = listOf(
                    MacroValueFormal(
                        Symbol("b"),
                        Types.boolean,
                        kind = ValueFormalKind.Required,
                    ),
                ),
                typeFormals = emptyList(),
            ),
        )
    }

    @Test
    fun oneInputTypeMismatch() {
        assertResolution(
            """
            {
              args: null,
              resolutions: {
                contradiction: true
              },
              problem: {
                name: "TypeValueMismatch",
                actualIndex: 0,
                type: "Int32",
              }
            }
            """,
            DynamicMessage(
                valueActuals = ActualValues.from(TBoolean.valueFalse),
                interpMode = InterpMode.Full,
            ),
            MacroSignature(
                requiredValueFormals = listOf(
                    MacroValueFormal(Symbol("b"), Types.int, ValueFormalKind.Required),
                ),
                restValuesFormal = null,
                returnType = null,
                typeFormals = emptyList(),
            ),
        )
    }

    @Test
    fun tooFewArgs() {
        assertResolution(
            """
            {
              args: null,
              resolutions: {
                contradiction: true
              },
              problem: {
                name: "ArgumentListSizeMismatch",
                nPositionalActuals: 1,
                nFormals: 2,
              }
            }
            """,
            DynamicMessage(
                valueActuals = ActualValues.from(Value(0, TInt)),
                interpMode = InterpMode.Full,
            ),
            MacroSignature(
                returnType = Types.anyValueOrNull,
                requiredValueFormals = listOf(
                    MacroValueFormal(Symbol("a"), Types.int, ValueFormalKind.Required),
                    MacroValueFormal(Symbol("b"), Types.int, ValueFormalKind.Required),
                ),
                typeFormals = emptyList(),
            ),
        )
    }

    @Test
    fun tooManyArgs() {
        assertResolution(
            """
            {
              args: null,
              resolutions: {
                contradiction: true
              },
              problem: {
                name: "ArgumentListSizeMismatch",
                nPositionalActuals: 3,
                nFormals: 2
              }
            }
            """,
            DynamicMessage(
                valueActuals = ActualValues.from(
                    Value(0, TInt),
                    Value(1, TInt),
                    Value(2, TInt),
                ),
                interpMode = InterpMode.Full,
            ),
            MacroSignature(
                restValuesFormal = null,
                returnType = null,
                requiredValueFormals = listOf(
                    MacroValueFormal(Symbol("a"), Types.int, ValueFormalKind.Required),
                    MacroValueFormal(Symbol("b"), Types.int, ValueFormalKind.Required),
                ),
                typeFormals = emptyList(),
            ),
        )
    }

    private val signatureTwoOptionalStrings = MacroSignature(
        returnType = null,
        requiredValueFormals = listOf(),
        optionalValueFormals = listOf(
            MacroValueFormal(
                Symbol("a"),
                Types.string,
                kind = ValueFormalKind.Optional,
                defaultExpr = Value(
                    DelayedInitializer(Value("default-a", TString)),
                ),
            ),
            MacroValueFormal(
                Symbol("b"),
                Types.string,
                kind = ValueFormalKind.Optional,
                defaultExpr = Value(
                    DelayedInitializer(Value("default-b", TString)),
                ),
            ),
        ),
        typeFormals = emptyList(),
    )

    @Test
    fun optionalArgsNoneSupplied() {
        assertResolution(
            """
            {
              args: [
                ["\"default-a\": String"],
                ["\"default-b\": String"],
              ],
              resolutions: {
                contradiction: false
              }
            }
            """,
            DynamicMessage(
                valueActuals = ActualValues.Empty,
                interpMode = InterpMode.Full,
            ),
            signatureTwoOptionalStrings,
        )
    }

    @Test
    fun oneUnnamedArg() {
        assertResolution(
            """
            {
              args: [
                ["\"foo\": String"],
                ["\"default-b\": String"],
              ],
              resolutions: {
                contradiction: false
              }
            }
            """,
            DynamicMessage(
                valueActuals = ActualValues.from(Value("foo", TString)),
                interpMode = InterpMode.Full,
            ),
            signatureTwoOptionalStrings,
        )
    }

    @Test
    fun namedArgIsFirst() {
        assertResolution(
            """
            {
              args: [
                ["\"foo\": String"],
                ["\"default-b\": String"],
              ],
              resolutions: {
                contradiction: false
              }
            }
            """,
            DynamicMessage(
                valueActuals = TestActuals(
                    listOf(
                        Symbol("a") to Value("foo", TString),
                    ),
                ),
                interpMode = InterpMode.Full,
            ),
            signatureTwoOptionalStrings,
        )
    }

    @Test
    fun namedArgIsSecond() {
        assertResolution(
            """
            {
              args: [
                ["\"default-a\": String"],
                ["\"foo\": String"],
              ],
              resolutions: {
                contradiction: false
              }
            }
            """,
            DynamicMessage(
                valueActuals = TestActuals(
                    listOf(
                        Symbol("b") to Value("foo", TString),
                    ),
                ),
                interpMode = InterpMode.Full,
            ),
            signatureTwoOptionalStrings,
        )
    }

    @Test
    fun namedArgsInOrder() {
        assertResolution(
            """
            {
              args: [
                ["\"foo\": String"],
                ["\"bar\": String"],
              ],
              resolutions: {
                contradiction: false
              }
            }
            """,
            DynamicMessage(
                valueActuals = TestActuals(
                    listOf(
                        Symbol("a") to Value("foo", TString),
                        Symbol("b") to Value("bar", TString),
                    ),
                ),
                interpMode = InterpMode.Full,
            ),
            signatureTwoOptionalStrings,
        )
    }

    @Test
    fun namedArgsOutOfOrder() {
        assertResolution(
            """
            {
              args: [
                ["\"foo\": String"],
                ["\"bar\": String"],
              ],
              resolutions: {
                contradiction: false
              }
            }
            """,
            DynamicMessage(
                valueActuals = TestActuals(
                    listOf(
                        Symbol("b") to Value("bar", TString),
                        Symbol("a") to Value("foo", TString),
                    ),
                ),
                interpMode = InterpMode.Full,
            ),
            signatureTwoOptionalStrings,
        )
    }

    @Test
    fun duplicateNamedArgs() {
        assertResolution(
            """
            {
              args: null,
              resolutions: {
                contradiction: true
              },
              problem: {
                name: "DuplicateName",
                actualIndex: 1,
                formalIndex: 0
              }
            }
            """,
            DynamicMessage(
                valueActuals = TestActuals(
                    listOf(
                        Symbol("a") to Value("foo", TString),
                        Symbol("a") to Value("foo", TString),
                    ),
                ),
                interpMode = InterpMode.Full,
            ),
            signatureTwoOptionalStrings,
        )
    }

    @Test
    fun unrecognizedNamedArg() {
        assertResolution(
            """
            {
              args: null,
              resolutions: {
                contradiction: true
              },
              problem: {
                name: "NamedArgumentMismatch",
                actualIndex: 0,
                formalIndex: null
              }
            }
            """,
            DynamicMessage(
                valueActuals = TestActuals(
                    listOf(
                        Symbol("c") to Value("foo", TString),
                    ),
                ),
                interpMode = InterpMode.Full,
            ),
            signatureTwoOptionalStrings,
        )
    }

    @Test
    fun outputTypesNotComparedToActuals() {
        assertResolution(
            """
            {
              args: null,
              resolutions: {
                contradiction: true
              },
              problem: {
                name: "ArgumentListSizeMismatch",
                nPositionalActuals: 1,
                nFormals: 0
              }
            }
            """,
            DynamicMessage(
                valueActuals = ActualValues.from(TBoolean.valueTrue),
                interpMode = InterpMode.Full,
            ),
            Signature2(
                returnType2 = WellKnownTypes.booleanType2,
                hasThisFormal = false,
                requiredInputTypes = listOf(),
                typeFormals = emptyList(),
            ),
        )
    }

    @Test
    fun outputsNeedNotBeSatisfiedWithInputs() {
        assertResolution(
            """
            {
              args: [],
              resolutions: {
                contradiction: false
              }
            }
            """,
            DynamicMessage(
                valueActuals = ActualValues.Empty,
                interpMode = InterpMode.Full,
            ),
            Signature2(
                returnType2 = Types.boolean.type2,
                hasThisFormal = false,
                requiredInputTypes = listOf(),
            ),
        )
    }

    @Test
    fun restParameterBindsTo3() {
        assertResolution(
            """
                |{
                |  args: [
                |    ["[0, 1, 2]: List"],
                |  ],
                |  resolutions: {
                |    contradiction: false
                |  }
                |}
            """.trimMargin(),
            DynamicMessage(
                valueActuals = ActualValues.from(
                    listOf(
                        Value(0, TInt),
                        Value(1, TInt),
                        Value(2, TInt),
                    ),
                ),
                interpMode = InterpMode.Full,
            ),
            MacroSignature(
                returnType = null,
                requiredValueFormals = emptyList(),
                restValuesFormal = MacroValueFormal(null, Types.int, ValueFormalKind.Rest),
            ),
        )
    }

    @Test
    fun restParameterBindsTo2Of4() {
        assertResolution(
            """
                |{
                |  args: [
                |    [ "0: Int32" ],
                |    [ "1: Int32" ],
                |    [ "[2, 3]: List" ],
                |  ],
                |  resolutions: {
                |    contradiction: false
                |  }
                |}
            """.trimMargin(),
            DynamicMessage(
                valueActuals = ActualValues.from(
                    listOf(
                        Value(0, TInt),
                        Value(1, TInt),
                        Value(2, TInt),
                        Value(3, TInt),
                    ),
                ),
                interpMode = InterpMode.Full,
            ),
            MacroSignature(
                requiredValueFormals = listOf(
                    MacroValueFormal(null, Types.int, kind = ValueFormalKind.Required),
                    MacroValueFormal(null, Types.int, kind = ValueFormalKind.Required),
                ),
                restValuesFormal = MacroValueFormal(null, Types.int, ValueFormalKind.Rest),
                returnType = null,
            ),
        )
    }

    @Test
    fun restParameterTypeCanContradict() {
        assertResolution(
            """
                |{
                |  args: null,
                |  resolutions: {
                |    contradiction: true
                |  },
                |  problem: {
                |    name: "TypeValueMismatch",
                |    actualIndex: 1,
                |    type: "Int32"
                |  }
                |}
            """.trimMargin(),
            DynamicMessage(
                valueActuals = ActualValues.from(
                    listOf(
                        Value(0, TInt),
                        Value("1", TString), // Does not match type
                        Value(2, TInt),
                    ),
                ),
                interpMode = InterpMode.Full,
            ),
            MacroSignature(
                returnType = null,
                requiredValueFormals = emptyList(),
                restValuesFormal = MacroValueFormal(null, Types.int, ValueFormalKind.Rest),
            ),
        )
    }

    @Test
    fun optionalParameterSupersedesRest() {
        assertResolution(
            """
                |{
                |  args: [
                |    ["0: Int32"],
                |    ["[1, 2]: List"],
                |  ],
                |  resolutions: {
                |    contradiction: false
                |  }
                |}
            """.trimMargin(),
            DynamicMessage(
                valueActuals = ActualValues.from(
                    listOf(
                        Value(0, TInt),
                        Value(1, TInt),
                        Value(2, TInt),
                    ),
                ),
                interpMode = InterpMode.Full,
            ),
            MacroSignature(
                returnType = null,
                requiredValueFormals = listOf(),
                optionalValueFormals = listOf(
                    MacroValueFormal(null, Types.int, kind = ValueFormalKind.Optional),
                ),
                restValuesFormal = MacroValueFormal(null, Types.int, ValueFormalKind.Rest),
            ),
        )
    }

    @Test
    fun trailingBlockNotMatchedByRest() = withTypeTestHarness {
        // f(0, 1, 2) { x => print(x) }
        val printFnType = ReifiedType(hackMapOldStyleToNew(type("fn (String): Void")))
        assertResolution(
            """
                |{
                |  args: [
                |    ["0: Int32"],
                |    ["print: Function"],
                |    ["[1, 2]: List"],
                |  ],
                |  resolutions: {
                |    contradiction: false
                |  }
                |}
            """.trimMargin(),
            DynamicMessage(
                valueActuals = ActualValues.from(
                    listOf(
                        Value(0, TInt),
                        Value(1, TInt),
                        Value(2, TInt),
                        BuiltinFuns.vPrint,
                    ),
                ),
                interpMode = InterpMode.Full,
            ),
            MacroSignature(
                requiredValueFormals = listOf(
                    MacroValueFormal(null, Types.int, kind = ValueFormalKind.Required),
                    MacroValueFormal(null, printFnType, kind = ValueFormalKind.Required),
                ),
                restValuesFormal = MacroValueFormal(null, Types.int, ValueFormalKind.Rest),
                returnType = null,
            ),
        )
    }
}

private data class DelayedInitializer(val value: Value<*>) : CallableValue {
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ) = value
    override val sigs: List<Signature2>? get() = null
    override fun addStays(s: StaySink) {
        value.addStays(s)
    }
}

private class TestActuals(val pairs: List<Pair<Symbol?, Value<*>>>) : ActualValues {
    override fun result(index: Int, computeInOrder: Boolean) = pairs[index].second

    override val size: Int get() = pairs.size

    override fun key(index: Int) = pairs[index].first

    override fun pos(index: Int): Position? = null

    override fun peekType(index: Int): TypeTag<*> = pairs[index].second.typeTag

    override fun clearResult(index: Int) {
        // Nothing to do
    }
}
