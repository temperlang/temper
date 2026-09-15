package lang.temper.interp

import lang.temper.builtin.EqMacro
import lang.temper.builtin.NeMacro
import lang.temper.builtin.builtinOperatorSpecs
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStringsEqual
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.env.InterpMode
import lang.temper.lexer.Genre
import lang.temper.log.FailLog
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.type.DotHelper
import lang.temper.type.ExternalCall
import lang.temper.type.FunctionResolution
import lang.temper.type.MkType
import lang.temper.type.OperatorMember
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.withNullity
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.Document
import lang.temper.value.Fail
import lang.temper.value.NotYet
import lang.temper.value.Planting
import lang.temper.value.TInt
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.toPseudoCode
import kotlin.test.Test

class EquivalenceDesugarMacroTest {
    @Test
    fun isNullLeft() = assertPseudoCodeAfter(
        """isNull(x)""".trimMargin(),
    ) {
        Call {
            V(Value(EqMacro))
            Rn(BuiltinName("x"))
            V(TNull.value)
            V(vDotHelper)
        }
    }

    @Test
    fun isNullRight() = assertPseudoCodeAfter(
        """isNull(x)""".trimMargin(),
    ) {
        Call {
            V(Value(EqMacro))
            V(TNull.value)
            Rn(BuiltinName("x"))
            V(vDotHelper)
        }
    }

    @Test
    fun isNotNullLeft() = assertPseudoCodeAfter(
        """!isNull(x)""".trimMargin(),
    ) {
        Call {
            V(Value(NeMacro))
            Rn(BuiltinName("x"))
            V(TNull.value)
            V(vDotHelper)
        }
    }

    @Test
    fun isNotNullRight() = assertPseudoCodeAfter(
        """!isNull(x)""".trimMargin(),
    ) {
        Call {
            V(Value(NeMacro))
            V(TNull.value)
            Rn(BuiltinName("x"))
            V(vDotHelper)
        }
    }

    @Test
    fun nullIsNull() = assertPseudoCodeAfter(
        """true""".trimMargin(),
    ) {
        Call {
            V(Value(EqMacro))
            V(TNull.value)
            V(TNull.value)
            V(vDotHelper)
        }
    }

    @Test
    fun nullIsNotNotNull() = assertPseudoCodeAfter(
        """false""".trimMargin(),
    ) {
        Call {
            V(Value(NeMacro))
            V(TNull.value)
            V(TNull.value)
            V(vDotHelper)
        }
    }

    @Test
    fun eqTwoNullableTypes() = assertPseudoCodeAfter(
        """
            |{
            |  if (isNull(x)) {
            |    isNull(y)
            |  } else if (isNull(y)) {
            |## x != null && y == null -> x != y
            |    false
            |  } else {
            |    x == y
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        Call {
            V(Value(EqMacro))
            Rn(BuiltinName("x"), MkType.nullable(WellKnownTypes.stringType))
            Rn(BuiltinName("y"), MkType.nullable(WellKnownTypes.stringType))
            V(vDotHelper)
        }
    }

    @Test
    fun neTwoNullableTypes() = assertPseudoCodeAfter(
        """
            |{
            |  if (isNull(x)) {
            |    !isNull(y)
            |  } else if (isNull(y)) {
            |    true
            |  } else {
            |    !(x == y)
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(Value(NeMacro))
            Rn(BuiltinName("x"), MkType.nullable(WellKnownTypes.stringType))
            Rn(BuiltinName("y"), MkType.nullable(WellKnownTypes.stringType))
            V(vDotHelper)
        }
    }

    private val noneToIntOrNull = CallTypeInferences(
        MkType.nullable(WellKnownTypes.intType),
        Signature2(
            returnType2 = WellKnownTypes.intType2.withNullity(Nullity.OrNull),
            hasThisFormal = false,
            requiredInputTypes = listOf(),
        ),
        mapOf(),
        listOf(),
    )

    private val noneToInt = CallTypeInferences(
        WellKnownTypes.intType,
        Signature2(
            returnType2 = WellKnownTypes.intType2.withNullity(Nullity.OrNull),
            hasThisFormal = false,
            requiredInputTypes = listOf(),
        ),
        mapOf(),
        listOf(),
    )

    @Test
    fun eqTwoNullableTypesCaptured() = assertPseudoCodeAfter(
        """
            |{
            |  let t#0;
            |  t#0 = f();
            |  let t#1;
            |  t#1 = g();
            |  if (isNull(t#0)) {
            |    isNull(t#1)
            |  } else if (isNull(t#1)) {
            |    false
            |  } else {
            |    t#0 == t#1
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(Value(EqMacro))
            Call(type = noneToIntOrNull) {
                Rn(BuiltinName("f"), noneToIntOrNull.variant)
            }
            Call(type = noneToIntOrNull) {
                Rn(BuiltinName("g"), noneToIntOrNull.variant)
            }
            V(vDotHelper)
        }
    }

    @Test
    fun neTwoNullableTypesCaptured() = assertPseudoCodeAfter(
        """
            |{
            |  let t#0;
            |  t#0 = f();
            |  let t#1;
            |  t#1 = g();
            |  if (isNull(t#0)) {
            |    !isNull(t#1)
            |  } else if (isNull(t#1)) {
            |    true
            |  } else {
            |    !(t#0 == t#1)
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(Value(NeMacro))
            Call(type = noneToIntOrNull) {
                Rn(BuiltinName("f"), noneToIntOrNull.variant)
            }
            Call(type = noneToIntOrNull) {
                Rn(BuiltinName("g"), noneToIntOrNull.variant)
            }
            V(vDotHelper)
        }
    }

    @Test
    fun eqNotNullNotCaptured() = assertPseudoCodeAfter(
        """f() == g()""".trimMargin(),
    ) {
        Call {
            V(Value(EqMacro))
            Call(type = noneToInt) {
                Rn(BuiltinName("f"), noneToInt.variant)
            }
            Call(type = noneToInt) {
                Rn(BuiltinName("g"), noneToInt.variant)
            }
            V(vDotHelper)
        }
    }

    @Test
    fun neNotNullNotCaptured() = assertPseudoCodeAfter(
        """!(f() == g())""".trimMargin(),
    ) {
        Call {
            V(Value(NeMacro))
            Call(type = noneToInt) {
                Rn(BuiltinName("f"), noneToInt.variant)
            }
            Call(type = noneToInt) {
                Rn(BuiltinName("g"), noneToInt.variant)
            }
            V(vDotHelper)
        }
    }

    @Test
    fun eqLeftNullableTyped() = assertPseudoCodeAfter(
        """
            |{
            |  if (isNull(x)) {
            |    false
            |  } else {
            |    x == y
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(Value(EqMacro))
            Rn(BuiltinName("x"), MkType.nullable(WellKnownTypes.stringType))
            Rn(BuiltinName("y"), WellKnownTypes.stringType)
            V(vDotHelper)
        }
    }

    @Test
    fun eqRightNullableTyped() = assertPseudoCodeAfter(
        """
            |{
            |  if (isNull(y)) {
            |    false
            |  } else {
            |    x == y
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(Value(EqMacro))
            Rn(BuiltinName("x"), WellKnownTypes.stringType)
            Rn(BuiltinName("y"), MkType.nullable(WellKnownTypes.stringType))
            V(vDotHelper)
        }
    }

    @Test
    fun neLeftNullableTyped() = assertPseudoCodeAfter(
        """
            |{
            |  if (isNull(x)) {
            |    true
            |  } else {
            |    !(x == y)
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(Value(NeMacro))
            Rn(BuiltinName("x"), MkType.nullable(WellKnownTypes.stringType))
            Rn(BuiltinName("y"), WellKnownTypes.stringType)
            V(vDotHelper)
        }
    }

    @Test
    fun neRightNullableTyped() = assertPseudoCodeAfter(
        """
            |{
            |  if (isNull(y)) {
            |    true
            |  } else {
            |    !(x == y)
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(Value(NeMacro))
            Rn(BuiltinName("x"), WellKnownTypes.stringType)
            Rn(BuiltinName("y"), MkType.nullable(WellKnownTypes.stringType))
            V(vDotHelper)
        }
    }

    @Test
    fun eqLeftNullableOnlyTyped() = assertPseudoCodeAfter(
        """
            |{
            |  if (isNull(x)) {
            |    isNull(y)
            |  } else if (isNull(y)) {
            |    false
            |  } else {
            |    x == y
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(Value(EqMacro))
            Rn(BuiltinName("x"), MkType.nullable(WellKnownTypes.stringType))
            Rn(BuiltinName("y"))
            V(vDotHelper)
        }
    }

    @Test
    fun eqRightNullableOnlyTyped() = assertPseudoCodeAfter(
        """
            |{
            |  if (isNull(x)) {
            |    isNull(y)
            |  } else if (isNull(y)) {
            |    false
            |  } else {
            |    x == y
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(Value(EqMacro))
            Rn(BuiltinName("x"))
            Rn(BuiltinName("y"), MkType.nullable(WellKnownTypes.stringType))
            V(vDotHelper)
        }
    }

    @Test
    fun neLeftNullableOnlyTyped() = assertPseudoCodeAfter(
        """
            |{
            |  if (isNull(x)) {
            |    !isNull(y)
            |  } else if (isNull(y)) {
            |    true
            |  } else {
            |    !(x == y)
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(Value(NeMacro))
            Rn(BuiltinName("x"), MkType.nullable(WellKnownTypes.stringType))
            Rn(BuiltinName("y"))
            V(vDotHelper)
        }
    }

    @Test
    fun neRightNullableOnlyTyped() = assertPseudoCodeAfter(
        """
            |{
            |  if (isNull(x)) {
            |    !isNull(y)
            |  } else if (isNull(y)) {
            |    true
            |  } else {
            |    !(x == y)
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(Value(NeMacro))
            Rn(BuiltinName("x"))
            Rn(BuiltinName("y"), MkType.nullable(WellKnownTypes.stringType))
            V(vDotHelper)
        }
    }

    @Test
    fun zeroEqZero() = assertPseudoCodeAfter(
        """
            |true
        """.trimMargin(),
        mapOf(
            BuiltinName("x") to Value(0, TInt),
            BuiltinName("y") to Value(0, TInt),
        ),
    ) {
        Call {
            V(Value(EqMacro))
            Rn(BuiltinName("x"), WellKnownTypes.intType)
            Rn(BuiltinName("y"), WellKnownTypes.intType)
            V(vDotHelper)
        }
    }

    @Test
    fun zeroNotNeZero() = assertPseudoCodeAfter(
        """
            |false
        """.trimMargin(),
    ) {
        Call {
            V(Value(NeMacro))
            V(Value(0, TInt), WellKnownTypes.intType)
            V(Value(0, TInt), WellKnownTypes.intType)
            V(vDotHelper)
        }
    }

    @Test
    fun zeroNotEqNull() = assertPseudoCodeAfter(
        """
            |false
        """.trimMargin(),
    ) {
        Call {
            V(Value(EqMacro))
            V(Value(0, TInt), WellKnownTypes.intType)
            V(TNull.value, MkType.nullable(WellKnownTypes.intType))
            V(vDotHelper)
        }
    }

    @Test
    fun incomparableOperands() = assertPseudoCodeAfter(
        // The typer will sort this out
        "0 == \"0\"",
    ) {
        Call {
            V(Value(EqMacro))
            V(Value(0, TInt), WellKnownTypes.intType)
            V(Value("0", TString), WellKnownTypes.stringType)
            V(vDotHelper)
        }
    }

    private val vDotHelper = Value(
        DotHelper(
            ExternalCall,
            OperatorMember("_==_"),
            builtinOperatorSpecs.getValue("_==_").map {
                FunctionResolution(it)
            },
        ),
    )

    private fun assertPseudoCodeAfter(
        want: String,
        extraBindings: Map<TemperName, Value<*>> = mapOf(),
        makeCall: Planting.() -> UnpositionedTreeTemplate<CallTree>,
    ) {
        val documentContext = TestDocumentContext()
        val doc = Document(documentContext)
        val root = doc.treeFarm.grow(Position(documentContext.loc, 0, 0)) {
            Block {
                makeCall()
            }
        }
        val logSink = ListBackedLogSink()
        val failLog = FailLog(logSink)

        val interpreter = Interpreter(
            failLog, logSink, Stage.Type, doc.nameMaker,
            continueCondition = { true },
        )
        val env = immutableEnvironment(
            builtinOnlyEnvironment(EmptyEnvironment, Genre.Library),
            extraBindings,
            isLongLived = false,
        )

        val result = interpreter.interpret(root, env, InterpMode.Partial)

        val got = buildString {
            append(root.toPseudoCode(singleLine = false))
            when (result) {
                NotYet -> {}
                is Fail, is Value<*> -> {
                    append("\n-> ")
                    append(result)
                }
            }
            failLog.logReasonForFailure(logSink)
            if (logSink.hasFatal) {
                for (e in logSink.allEntries) {
                    if (e.level >= Log.Warn) {
                        append("\n")
                        append(e.messageText)
                    }
                }
            }
        }

        assertStringsEqual(want.trimEnd(), got.trimEnd())
    }
}
