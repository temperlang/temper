package lang.temper.interp

import lang.temper.builtin.EqMacro
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
import lang.temper.type.OperatorMember
import lang.temper.type.WellKnownTypes
import lang.temper.type2.AdHocArrowTypes.definedTypeForSig
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

class EqMacroTest {
    @Test
    fun isNullLeft() = assertPseudoCodeAfter(
        """isNull(x)""".trimMargin(),
    ) {
        Call {
            V(EqMacro.value)
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
            V(EqMacro.value)
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
            V(EqMacro.value)
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
            |    notNull(x) == notNull(y)
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        Call {
            V(EqMacro.value)
            Rn(BuiltinName("x"), WellKnownTypes.stringType2.withNullity(Nullity.OrNull))
            Rn(BuiltinName("y"), WellKnownTypes.stringType2.withNullity(Nullity.OrNull))
            V(vDotHelper)
        }
    }

    private val noneToIntOrNull = CallTypeInferences(
        WellKnownTypes.intType2.withNullity(Nullity.OrNull),
        Signature2(
            returnType2 = WellKnownTypes.intType2.withNullity(Nullity.OrNull),
            hasThisFormal = false,
            requiredInputTypes = listOf(),
        ),
        mapOf(),
        listOf(),
    )

    private val noneToInt = CallTypeInferences(
        WellKnownTypes.intType2,
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
            |    notNull(t#0) == notNull(t#1)
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(EqMacro.value)
            Call(type = noneToIntOrNull) {
                Rn(BuiltinName("f"), definedTypeForSig(noneToIntOrNull.variant))
            }
            Call(type = noneToIntOrNull) {
                Rn(BuiltinName("g"), definedTypeForSig(noneToIntOrNull.variant))
            }
            V(vDotHelper)
        }
    }

    @Test
    fun eqNotNullNotCaptured() = assertPseudoCodeAfter(
        """f() == g()""".trimMargin(),
    ) {
        Call {
            V(EqMacro.value)
            Call(type = noneToInt) {
                Rn(BuiltinName("f"), definedTypeForSig(noneToInt.variant))
            }
            Call(type = noneToInt) {
                Rn(BuiltinName("g"), definedTypeForSig(noneToInt.variant))
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
            |    notNull(x) == y
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(EqMacro.value)
            Rn(BuiltinName("x"), WellKnownTypes.stringType2.withNullity(Nullity.OrNull))
            Rn(BuiltinName("y"), WellKnownTypes.stringType2)
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
            |    x == notNull(y)
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(EqMacro.value)
            Rn(BuiltinName("x"), WellKnownTypes.stringType2)
            Rn(BuiltinName("y"), WellKnownTypes.stringType2.withNullity(Nullity.OrNull))
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
            |    notNull(x) == notNull(y)
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(EqMacro.value)
            Rn(BuiltinName("x"), WellKnownTypes.stringType2.withNullity(Nullity.OrNull))
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
            |    notNull(x) == notNull(y)
            |  }
            |}
        """.trimMargin(),
    ) {
        Call {
            V(EqMacro.value)
            Rn(BuiltinName("x"))
            Rn(BuiltinName("y"), WellKnownTypes.stringType2.withNullity(Nullity.OrNull))
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
            V(EqMacro.value)
            Rn(BuiltinName("x"), WellKnownTypes.intType2)
            Rn(BuiltinName("y"), WellKnownTypes.intType2)
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
            V(EqMacro.value)
            V(Value(0, TInt), WellKnownTypes.intType2)
            V(TNull.value, WellKnownTypes.intType2.withNullity(Nullity.OrNull))
            V(vDotHelper)
        }
    }

    @Test
    fun incomparableOperands() = assertPseudoCodeAfter(
        // The typer will sort this out
        "0 == \"0\"",
    ) {
        Call {
            V(EqMacro.value)
            V(Value(0, TInt), WellKnownTypes.intType2)
            V(Value("0", TString), WellKnownTypes.stringType2)
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
