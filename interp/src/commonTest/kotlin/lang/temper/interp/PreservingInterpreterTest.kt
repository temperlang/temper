package lang.temper.interp

import lang.temper.builtin.PureCallableValue
import lang.temper.common.ListBackedLogSink
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStringsEqual
import lang.temper.common.assertStructure
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.env.InterpMode
import lang.temper.log.FailLog
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.stage.Stage
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.BlockTree
import lang.temper.value.BuiltinStatelessCallableValue
import lang.temper.value.Document
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PartialResult
import lang.temper.value.TInt
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.initSymbol
import lang.temper.value.toPseudoCode
import kotlin.test.Test
import kotlin.test.assertEquals

class PreservingInterpreterTest {
    @Test
    fun interpretPreserving() {
        // When we interpret
        //     let times = nym`*`;
        //     let a = 6;
        //     let b = 7;
        //     let ab = times(a, b);
        //     ab
        // there are a number of ways that we might lose information about the original structure.
        // We could inline `a` or `b`:
        //     let ab = times(6, 7);
        // We could inline `times`:
        //     let ab = a * b;
        // We could inline `ab` at the end:
        //     42
        // This test checks that we preserve structure between calls, and that the
        // replace-with-preserved, lets us recover our original tree structure after partial
        // interpretation.
        var numberOfCallsToTimes = 0
        val timesFn = object : BuiltinStatelessCallableValue, NamedBuiltinFun, PureCallableValue {
            override fun invoke(
                args: ActualValues,
                cb: InterpreterCallback,
                interpMode: InterpMode,
            ): PartialResult {
                check(interpMode == InterpMode.Full && args.size == 2)
                numberOfCallsToTimes += 1
                // Not actual stateless but we want to avoid multiply collapsing pure function calls
                return Value(
                    TInt.unpack(args[0]) * TInt.unpack(args[1]),
                    TInt,
                )
            }

            override val name = "*"
            override val sigs: List<Signature2> = listOf(
                Signature2(
                    returnType2 = WellKnownTypes.intType2,
                    hasThisFormal = false,
                    requiredInputTypes = listOf(WellKnownTypes.intType2, WellKnownTypes.intType2),
                ),
            )
        }

        val pos = Position(testCodeLocation, 0, 0)
        val doc = Document(TestDocumentContext(testModuleName))
        val original = doc.treeFarm.grow(pos) {
            val (times, a, b, ab) = listOf("times", "a", "b", "ab").map {
                doc.nameMaker.unusedSourceName(ParsedName(it))
            }
            Block {
                Decl(times) {
                    V(initSymbol)
                    V(Value(timesFn))
                }
                Decl(a) {
                    V(initSymbol)
                    V(Value(6, TInt))
                }
                Decl(b) {
                    V(initSymbol)
                    V(Value(7, TInt))
                }
                Decl(ab) {
                    V(initSymbol)
                    Call {
                        Rn(times)
                        Rn(a)
                        Rn(b)
                    }
                }
                Rn(ab)
            }
        }

        // Before any interpretation
        assertStringsEqual(
            """
                |let times__0 = nym`*`, a__1 = 6, b__2 = 7, ab__3 = times__0(a__1, b__2); ab__3
            """.trimMargin(),
            original.toPseudoCode(),
        )
        assertEquals(0, numberOfCallsToTimes)

        fun interpret(
            t: Tree,
            replacementPolicy: ReplacementPolicy,
        ): PartialResult {
            val logSink = ListBackedLogSink()
            val failLog = FailLog(logSink)
            var ccCounter = 1000
            val continueCondition = { --ccCounter >= 0 }
            val interpreter = Interpreter(
                failLog = failLog,
                logSink = logSink,
                stage = Stage.GenerateCode,
                nameMaker = doc.nameMaker,
                continueCondition = continueCondition,
                replacementPolicy = replacementPolicy,
            )
            failLog.logReasonForFailure(logSink)
            val env = blankEnvironment(
                builtinOnlyEnvironment(
                    EmptyEnvironment,
                    t.document.context.genre,
                ),
            )

            val result = interpreter.interpret(t, env, InterpMode.Partial)
            check(!logSink.hasFatal)
            return result
        }

        // Let's test the baseline, partial interpretation of a copy without preservation.
        run {
            val withReplacedDiscarded = original.copy()
            val result = interpret(withReplacedDiscarded, ReplacementPolicy.Discard)
            assertEquals(1, numberOfCallsToTimes)
            assertStringsEqual(
                """
                    |let times__0 = nym`*`, a__1 = 6, b__2 = 7, ab__3 = 42;
                    |42
                    |
                """.trimMargin(),
                withReplacedDiscarded.toPseudoCode(singleLine = false),
            )
            assertEquals(Value(42, TInt), result)
        }

        // Now we'll partially interpret with preservation 3 times.
        // This will make sure we don't end up with nested preservations like
        // `preserve(preserve(x), y)` and that we preserve structure properly, and
        // that we don't end up re-computing the product of a*b.
        val treeAfterInterpretingWithReplacements = run {
            val tree = original.copy() as BlockTree
            repeat(times = 3) { round ->
                val result = interpret(tree, ReplacementPolicy.Preserve)
                assertEquals(2, numberOfCallsToTimes)
                assertStructure(
                    """
                        |{
                        |  code: ```
                        |    let times__0 = nym`*`, a__1 = 6, b__2 = 7,${
                        ""
                    } ab__3 = preserve(preserve(times__0, nym`*`)${
                        ""
                    }(preserve(a__1, 6), preserve(b__2, 7)), 42);
                        |    preserve(ab__3, 42)
                        |
                        |    ```,
                        |  numberOfCallsToTimes: 2, // One from original, and one from preserved
                        |  result: "42: Int32",
                        |}
                    """.trimMargin(),
                    object : Structured {
                        override fun destructure(structureSink: StructureSink) = structureSink.obj {
                            key("code") {
                                value(tree.toPseudoCode(singleLine = false))
                            }
                            key("numberOfCallsToTimes") {
                                value(numberOfCallsToTimes)
                            }
                            key("result") {
                                value(result)
                            }
                        }
                    },
                    message = "Round $round",
                )
            }
            tree
        }

        treeAfterInterpretingWithReplacements.restorePreserved()
        assertStringsEqual(
            """
                |let times__0 = nym`*`, a__1 = 6, b__2 = 7, ab__3 = times__0(a__1, b__2);
                |ab__3
                |
            """.trimMargin(),
            treeAfterInterpretingWithReplacements.toPseudoCode(singleLine = false),
        )
    }
}
