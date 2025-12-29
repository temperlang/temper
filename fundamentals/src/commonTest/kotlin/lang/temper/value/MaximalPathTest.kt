package lang.temper.value

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.ForwardOrBack
import lang.temper.common.LeftOrRight
import lang.temper.common.TestDocumentContext
import lang.temper.common.console
import lang.temper.log.Position
import lang.temper.name.ParsedName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MaximalPathTest {
    @Test
    fun emptyBlock() {
        assertMaximalPaths(
            want = """
                |Entry Path#0
                |Exits Path#0
                |Fail exits
                |
                |Path#0
            """.trimMargin(),
            expectedTerminalExpressions = "Sometimes",
        ) {
            StmtBlock()
        }
    }

    @Test
    fun blockOf2() {
        assertMaximalPaths(
            want = """
                |Entry Path#0
                |Exits Path#0
                |Fail exits
                |
                |Path#0
                |- ref#0: `foo()`
                |- ref#1: `bar()`
            """.trimMargin(),
            expectedTerminalExpressions = """
                |- ref#1: `bar()`
                |Always
            """.trimMargin(),
        ) {
            StmtBlock(
                Stmt("foo"),
                Stmt("bar"),
            )
        }
    }

    @Test
    fun ifWithTwoTerminals() {
        assertMaximalPaths(
            want = """
                |Entry Path#0
                |Exits Path#3
                |Fail exits
                |
                |Path#0
                |- ref#0: `init()`
                |if (ref#1?: `wot()`) -> Path#1
                |else -> Path#2
                |
                |Path#1
                |- ref#2: `foo()`
                |-> Path#3
                |
                |Path#2
                |- ref#3: `bar()`
                |-> Path#3
                |
                |Path#3
            """.trimMargin(),
            expectedTerminalExpressions = """
                |- ref#2: `foo()`
                |- ref#3: `bar()`
                |Always
            """.trimMargin(),
        ) {
            StmtBlock(
                Stmt("init"),
                If(
                    Ref("wot"),
                    Stmt("foo"),
                    elseClause = Stmt("bar"),
                ),
            )
        }
    }

    @Test
    fun ifNoElse() {
        assertMaximalPaths(
            want = """
                |Entry Path#0
                |Exits Path#2
                |Fail exits
                |
                |Path#0
                |- ref#0: `init()`
                |if (ref#1?: `wot()`) -> Path#1
                |else -> Path#2
                |
                |Path#1
                |- ref#2: `foo()`
                |-> Path#2
                |
                |Path#2
            """.trimMargin(),
            expectedTerminalExpressions = """
                |- ref#2: `foo()`
                |Sometimes
            """.trimMargin(),
        ) {
            StmtBlock(
                Stmt("init"),
                If(Ref("wot"), Stmt("foo")),
            )
        }
    }

    @Test
    fun terminalExpressionBeforeBreak() {
        assertMaximalPaths(
            want = """
                |Entry Path#0
                |Exits Path#1
                |Fail exits
                |
                |Path#0
                |- ref#0: `beforeBreak()`
                |if (ref#1?: `x()`) -> Path#1
                |else -> Path#1
                |
                |Path#1
            """.trimMargin(),
            expectedTerminalExpressions = """
                |- ref#0: `beforeBreak()`
                |Always
            """.trimMargin(),
        ) {
            // my_label: beforeBreak { if (x()) { break my_label } }
            val label = label("my_label")
            Labeled(
                label,
                StmtBlock(
                    Stmt("beforeBreak"),
                    If(Ref("x"), BreakTo(label)),
                ),
            )
        }
    }

    @Test
    fun terminalExpressionBeforeBubble() {
        assertMaximalPaths(
            want = """
                |Entry Path#0
                |Exits Path#2
                |Fail exits Path#1
                |
                |Path#0
                |- ref#0: `beforeBubble()`
                |if (ref#1?: `x()`) -> Path#1
                |else -> Path#2
                |
                |Path#1
                |
                |Path#2
            """.trimMargin(),
            expectedTerminalExpressions = """
                |- ref#0: `beforeBubble()`
                |Sometimes
            """.trimMargin(),
        ) {
            StmtBlock(
                Stmt("beforeBubble"),
                If(Ref("x"), Bubble()),
            )
        }
    }

    @Test
    fun alwaysBubbles() {
        assertMaximalPaths(
            want = """
                |Entry Path#0
                |Exits
                |Fail exits Path#0
                |
                |Path#0
                |- ref#0: `beforeBubble()`
            """.trimMargin(),
            expectedTerminalExpressions = """
                |Never
            """.trimMargin(),
        ) {
            StmtBlock(
                Stmt("beforeBubble"),
                Bubble(),
            )
        }
    }

    @Test
    fun bubblesFlow() {
        assertMaximalPaths(
            want = """
                |Entry Path#0
                |Exits Path#3
                |Fail exits
                |
                |Path#0
                |if (ref#0?: `bad()`) -> Path#1
                |else -> Path#2
                |
                |Path#1
                |- ref#3: `recover()`
                |-> Path#3
                |
                |Path#2
                |- ref#2: `known_safe()`
                |-> Path#3
                |
                |Path#3
            """.trimMargin(),
            expectedTerminalExpressions = """
                |- ref#2: `known_safe()`
                |- ref#3: `recover()`
                |Always
            """.trimMargin(),
        ) {
            OrElse(
                onFailLabel = null, // surprise me
                orClause = StmtBlock(
                    If(Ref("bad"), Bubble()),
                    Stmt("known_safe"),
                ),
                elseClause = StmtBlock(
                    Stmt("recover"),
                ),
            )
        }
    }

    @Test
    fun whileLoopLoops() {
        assertMaximalPaths(
            want = """
                |Entry Path#0
                |Exits Path#3
                |Fail exits
                |
                |Path#0
                |- ref#0: `before_loop()`
                |-> Path#1
                |
                |Path#1
                |if (ref#1?: `keepGoing()`) -> Path#2
                |else -> Path#3
                |
                |Path#2
                |- ref#2: `start_body()`
                |if (ref#3?: `x()`) -> Path#3
                |else -> Path#4
                |
                |Path#3
                |- ref#7: `after_loop()`
                |
                |Path#4
                |if (ref#4?: `y()`) -> Path#5
                |else -> Path#6
                |
                |Path#6
                |- ref#5: `notX_notY()`
                |- ref#6: `end_body()`
                |-> Path#5
                |
                |Path#5
                |<- Path#1
            """.trimMargin(),
            expectedTerminalExpressions = """
                |- ref#7: `after_loop()`
                |Always
            """.trimMargin(),
        ) {
            // before_loop()
            // while (keepGoing()) {
            //   start_body();
            //   if (x()) {
            //     break;
            //   } else if (y) {
            //     continue;
            //   } else {
            //     notX_notY();
            //   }
            //   end_body();
            // }
            // after_loop();
            StmtBlock(
                Stmt("before_loop"),
                While(
                    Ref("keepGoing"),
                    StmtBlock(
                        Stmt("start_body"),
                        If(
                            Ref("x"),
                            BreakTo(null),
                            elseClause = If(
                                Ref("y"),
                                ContinueTo(null),
                                elseClause = Stmt("notX_notY"),
                            ),
                        ),
                        Stmt("end_body"),
                    ),
                ),
                Stmt("after_loop"),
            )
        }
    }

    @Test
    fun loopWithIncrement() {
        assertMaximalPaths(
            want = """
                |Entry Path#0
                |Exits Path#5
                |Fail exits
                |
                |Path#0
                |- ref#0: `before_loop()`
                |-> Path#1
                |
                |Path#1
                |if (ref#1?: `keepGoing()`) -> Path#2
                |else -> Path#5
                |
                |Path#2
                |- ref#2: `start_body()`
                |if (ref#3?: `x()`) -> Path#3
                |else -> Path#4
                |
                |Path#4
                |- ref#4: `not_continuing()`
                |- ref#5: `end_body()`
                |-> Path#3
                |
                |Path#3
                |- ref#6: `increment()`
                |<- Path#1
                |
                |Path#5
                |- ref#7: `after_loop()`
            """.trimMargin(),
            expectedTerminalExpressions = """
                |- ref#7: `after_loop()`
                |Always
            """.trimMargin(),
        ) {
            val loopLabel = label("loop_label")
            // before_loop();
            // label: for (; keepGoing(); increment()) {
            //   start_body();
            //   if (x()) {
            //     continue;
            //   } else {
            //     not_continuing();
            //   }
            //   end_body();
            // }
            // after_loop();
            StmtBlock(
                Stmt("before_loop"),
                While(
                    label = loopLabel,
                    condition = Ref("keepGoing"),
                    body = StmtBlock(
                        Stmt("start_body"),
                        If(
                            Ref("x"),
                            ContinueTo(loopLabel),
                            elseClause = Stmt("not_continuing"),
                        ),
                        Stmt("end_body"),
                    ),
                    increment = Stmt("increment"),
                ),
                Stmt("after_loop"),
            )
        }
    }

    @Test
    fun doLoop() {
        assertMaximalPaths(
            want = """
                |Entry Path#0
                |Exits Path#2
                |Fail exits
                |
                |Path#0
                |- ref#0: `body()`
                |if (ref#1?: `cond()`) -> Path#1
                |else -> Path#2

                |Path#1
                |<- Path#0
                |
                |Path#2
            """.trimMargin(),
            expectedTerminalExpressions = """
                |- ref#0: `body()`
                |Always
            """.trimMargin(),
        ) {
            // do { "body" } while ("cond");
            DoWhile(
                StmtBlock(
                    Stmt("body"),
                ),
                Ref("cond"),
            )
        }
    }

    @Test
    fun divThenOrElse() = assertMaximalPaths(
        """
            |Entry Path#0
            |Exits Path#3
            |Fail exits
            |
            |Path#0
            |- ref#0: `var t#3`
            |- ref#1: `var t#4`
            |- ref#2: `var fail#2`
            |- ref#3: `t#3 = hs(fail#2, x__0 / y__1)`
            |if (ref#4?: `fail#2`) -> Path#1
            |else -> Path#2
            |
            |Path#1
            |- ref#6: `t#4 = -1`
            |-> Path#3
            |
            |Path#2
            |- ref#5: `t#4 = t#3`
            |-> Path#3
            |
            |Path#3
            |- ref#7: `t#4`
        """.trimMargin(),
    ) {
        // WeaverTest.divOrElseTest produces output like this:
        val x = doc.nameMaker.unusedSourceName(ParsedName("x"))
        val y = doc.nameMaker.unusedSourceName(ParsedName("y"))
        val fail1 = doc.nameMaker.unusedTemporaryName("fail")
        val t2 = doc.nameMaker.unusedTemporaryName("t")
        val t3 = doc.nameMaker.unusedTemporaryName("t")
        val orelse0 = label("orelse")
        StmtBlock(
            // [[ var t#2 ]];
            Stmt {
                Decl(t2) {
                    V(varSymbol)
                    V(void)
                }
            },
            // [[ var t#3 ]];
            Stmt {
                Decl(t3) {
                    V(varSymbol)
                    V(void)
                }
            },
            // [[ var fail#1 ]];
            Stmt {
                Decl(fail1) {
                    V(varSymbol)
                    V(void)
                }
            },
            // orelse#0: do {
            OrElse(
                onFailLabel = orelse0,
                orClause = StmtBlock(
                    // [[ t#2 = hs(fail#1, x / y) ]];
                    Stmt {
                        Call(BuiltinFuns.setLocalFn) {
                            Ln(t2)
                            Call(BuiltinFuns.handlerScope) {
                                Ln(fail1)
                                Call(BuiltinFuns.divIntIntFn) {
                                    Rn(x)
                                    Rn(y)
                                }
                            }
                        }
                    },
                    // if ([[ fail#1 ]]) {
                    //   break orelse#0;
                    // }
                    If(
                        Ref { Rn(fail1) },
                        BreakTo(orelse0),
                    ),
                    // [[ t#3 = t#2 ]];
                    Stmt {
                        Call(BuiltinFuns.setLocalFn) {
                            Ln(t3)
                            Rn(t2)
                        }
                    },
                ),
                // } orelse {
                //   [[ t#3 = -1 ]];
                // }
                elseClause = Stmt {
                    Call(BuiltinFuns.setLocalFn) {
                        Ln(t3)
                        V(Value(-1, TInt))
                    }
                },
            ),
            // [[ t#3 ]];
            Stmt { Rn(t3) },
        )
    }

    @Test
    fun ifInfinite() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits
            |Fail exits
            |
            |Path#0
            |if (ref#0?: `question()`) -> Path#1
            |else -> Path#2
            |
            |Path#1
            |- ref#2: `then_y()`
            |<- Path#1
            |
            |Path#2
            |- ref#4: `else_y()`
            |<- Path#2
        """.trimMargin(),
        expectedTerminalExpressions = """
            |Never
        """.trimMargin(),
    ) {
        If(
            Ref("question"),
            While(
                Ref { V(TBoolean.valueTrue) },
                Stmt("then_y"),
            ),
            While(
                Ref { V(TBoolean.valueTrue) },
                Stmt("else_y"),
            ),
        )
    }

    @Test
    fun infWhileLoop() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits
            |Fail exits
            |
            |Path#0
            |- ref#1: `body()`
            |<- Path#0
        """.trimMargin(),
        expectedTerminalExpressions = """
            |Never
        """.trimMargin(),
    ) {
        While(
            Ref { V(TBoolean.valueTrue) },
            Stmt("body"),
        )
    }

    @Test
    fun infDoWhileLoop() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits
            |Fail exits
            |
            |Path#0
            |- ref#0: `body()`
            |-> Path#1
            |
            |Path#1
            |<- Path#0
        """.trimMargin(),
        expectedTerminalExpressions = """
            |Never
        """.trimMargin(),
    ) {
        DoWhile(
            Stmt("body"),
            Ref { V(TBoolean.valueTrue) },
        )
    }

    @Test
    fun infDoWhileLoopWithSillyContinue() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits
            |Fail exits
            |
            |Path#0
            |- ref#0: `body()`
            |-> Path#1
            |
            |Path#1
            |<- Path#0
        """.trimMargin(),
        expectedTerminalExpressions = """
            |Never
        """.trimMargin(),
    ) {
        DoWhile(
            StmtBlock(
                Stmt("body"),
                ContinueTo(null),
            ),
            Ref { V(TBoolean.valueTrue) },
        )
    }

    @Test
    fun doWhileThatOnlyBubblesOut() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits
            |Fail exits Path#1
            |
            |Path#0
            |if (ref#0?: `thereIsAProblem()`) -> Path#1
            |else -> Path#2
            |
            |Path#1
            |
            |Path#2
            |- ref#2: `ok()`
            |-> Path#3
            |
            |Path#3
            |<- Path#0
        """.trimMargin(),
        expectedTerminalExpressions = """
            |Never
        """.trimMargin(),
    ) {
        DoWhile(
            StmtBlock(
                If(
                    Ref("thereIsAProblem"),
                    Bubble(),
                    // If it's not from the Chimerpagne region,
                    // it's just sparkling control flow.
                ),
                Stmt("ok"),
            ),
            Ref { V(TBoolean.valueTrue) },
        )
    }

    @Test
    fun whileLoopNotInfDueToBreak() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits Path#1
            |Fail exits
            |
            |Path#0
            |if (ref#1?: `shouldBreak()`) -> Path#1
            |else -> Path#2
            |
            |Path#1
            |
            |Path#2
            |- ref#2: `body()`
            |<- Path#0
        """.trimMargin(),
        expectedTerminalExpressions = """
            |Sometimes
        """.trimMargin(),
    ) {
        While(
            Ref { V(TBoolean.valueTrue) },
            If(
                Ref("shouldBreak"),
                BreakTo(null),
                Stmt("body"),
            ),
        )
    }

    @Test
    fun doWhileLoopNotInfDueToBreak() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits Path#1
            |Fail exits
            |
            |Path#0
            |if (ref#0?: `shouldBreak()`) -> Path#1
            |else -> Path#2
            |
            |Path#1
            |
            |Path#2
            |- ref#1: `body()`
            |-> Path#3
            |
            |Path#3
            |<- Path#0
        """.trimMargin(),
        expectedTerminalExpressions = """
            |Sometimes
        """.trimMargin(),
    ) {
        DoWhile(
            If(
                Ref("shouldBreak"),
                BreakTo(null),
                Stmt("body"),
            ),
            Ref { V(TBoolean.valueTrue) },
        )
    }

    @Test
    fun whileFalse() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits Path#1
            |Fail exits
            |
            |Path#0
            |- ref#0: `before()`
            |-> Path#1
            |
            |Path#1
            |- ref#1?: `false`
            |- ref#3: `after()`
        """.trimMargin(),
        expectedTerminalExpressions = """
            |- ref#3: `after()`
            |Always
        """.trimMargin(),
    ) {
        StmtBlock(
            Stmt("before"),
            While(
                Ref { V(TBoolean.valueFalse) },
                Stmt("body"),
            ),
            Stmt("after"),
        )
    }

    @Test
    fun doWhileFalse() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits Path#1
            |Fail exits
            |
            |Path#0
            |- ref#0: `before()`
            |-> Path#1
            |
            |Path#1
            |- ref#1: `body()`
            |- ref#3: `after()`
        """.trimMargin(),
        expectedTerminalExpressions = """
            |- ref#3: `after()`
            |Always
        """.trimMargin(),
    ) {
        StmtBlock(
            Stmt("before"),
            DoWhile(
                Stmt("body"),
                Ref { V(TBoolean.valueFalse) },
            ),
            Stmt("after"),
        )
    }

    @Test
    fun unconvertedContinueInWrappedBody() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits Path#4
            |Fail exits
            |
            |Path#0
            |if (ref#0?: `keepOnLooping()`) -> Path#1
            |else -> Path#4
            |
            |Path#1
            |- ref#1: `startBody()`
            |if (ref#2?: `skipEndBody()`) -> Path#2
            |else -> Path#3
            |
            |Path#3
            |- ref#3: `endBody()`
            |-> Path#2
            |
            |Path#2
            |- ref#4: `incr()`
            |<- Path#0
            |
            |Path#4
            |- ref#5: `afterLoop()`
        """.trimMargin(),
        expectedTerminalExpressions = """
            |- ref#5: `afterLoop()`
            |Always
        """.trimMargin(),
    ) {
        val bodyLabel = label("body")
        StmtBlock(
            While(
                Ref("keepOnLooping"),
                Labeled(
                    label = bodyLabel,
                    continueLabel = bodyLabel,
                    body = StmtBlock(
                        Stmt("startBody"),
                        If(
                            Ref("skipEndBody"),
                            ContinueTo(null),
                        ),
                        Stmt("endBody"),
                    ),
                ),
                increment = Stmt("incr"),
            ),
            Stmt("afterLoop"),
        )
    }

    private fun makeYieldExample(cfm: ControlFlowMaker) = cfm.run {
        StmtBlock(
            Stmt("first"),
            Stmt { Call { Rn(YieldingFnKind.yield.builtinName) } },
            Stmt("second"),
            Stmt { Call { Rn(YieldingFnKind.yield.builtinName) } },
            Stmt("third"),
        )
    }

    @Test
    fun coroutineBodyWithoutForcingYieldToEnd() = assertMaximalPaths(
        """
            |Entry Path#0
            |Exits Path#0
            |Fail exits
            |
            |Path#0
            |- ref#0: `first()`
            |- ref#1: `yield()`
            |- ref#2: `second()`
            |- ref#3: `yield()`
            |- ref#4: `third()`
        """.trimMargin(),
    ) {
        makeYieldExample(this)
    }

    @Test
    fun coroutineBodyForcingYieldToEnd() = assertMaximalPaths(
        """
            |Entry Path#0
            |Exits Path#2
            |Fail exits
            |
            |Path#0
            |- ref#0: `first()`
            |- ref#1: `yield()`
            |-> Path#1
            |
            |Path#1
            |- ref#2: `second()`
            |- ref#3: `yield()`
            |-> Path#2
            |
            |Path#2
            |- ref#4: `third()`
        """.trimMargin(),
        yieldingCallsEndPaths = true,
    ) {
        makeYieldExample(this)
    }

    @Test
    fun adjacentYieldsNotRejoined() = assertMaximalPaths(
        yieldingCallsEndPaths = true,
        want = """
            |Entry Path#0
            |Exits Path#3
            |Fail exits
            |
            |Path#0
            |- ref#0: `yield()`
            |-> Path#1
            |
            |Path#1
            |- ref#1: `yield()`
            |-> Path#2
            |
            |Path#2
            |- ref#2: `yield()`
            |-> Path#3
            |
            |Path#3
        """.trimMargin(),
    ) {
        StmtBlock(
            Stmt { Call { Rn(YieldingFnKind.yield.builtinName) } },
            Stmt { Call { Rn(YieldingFnKind.yield.builtinName) } },
            Stmt { Call { Rn(YieldingFnKind.yield.builtinName) } },
        )
    }

    @Test
    fun elseVisited() = assertMaximalPaths(
        assumeFailureCanHappen = true,
        want = """
            |Entry Path#0
            |Exits Path#2
            |Fail exits
            |
            |Path#0
            |- ref#0: `f()`
            |-> Path#1
            |else -> Path#2
            |
            |Path#1
            |- ref#1: `g()`
            |-> Path#2
            |
            |Path#2
            |
        """.trimMargin(),
    ) {
        OrElse(
            label("orelse"),
            Stmt("f"),
            Stmt("g"),
        )
    }

    @Test
    fun continueInDoublyLabeledStmtBlock() = assertMaximalPaths(
        // do { if (fail) { bubble() }; continue } while (false);
        """
            |Entry Path#0
            |Exits Path#2
            |Fail exits Path#1
            |
            |Path#0
            |if (ref#0?: `fail#0`) -> Path#1
            |else -> Path#2
            |
            |Path#1
            |
            |Path#2
            |- ref#2: `void`
        """.trimMargin(),
        expectedTerminalExpressions = """
            |- ref#2: `void`
            |Sometimes
        """.trimMargin(),
    ) {
        val fail = doc.nameMaker.unusedTemporaryName("fail")
        val defunctLoopLabel = label("loop")
        val fakeBreakLabel = label("fake_break")
        Labeled(
            label = defunctLoopLabel,
            body = StmtBlock(
                Labeled(
                    label = fakeBreakLabel,
                    continueLabel = defunctLoopLabel,
                    body = StmtBlock(
                        If(
                            Ref { Rn(fail) },
                            Stmt { Call(BubbleFn) {} },
                        ),
                        Stmt { V(void) },
                        ContinueTo(null),
                    ),
                ),
            ),
        )
    }

    private fun assertMaximalPaths(
        want: String,
        expectedTerminalExpressions: String? = null,
        yieldingCallsEndPaths: Boolean = false,
        ignoreConstantConditions: Boolean = false,
        assumeFailureCanHappen: Boolean = false,
        makeControlFlow: ControlFlowMaker.() -> ControlFlow,
    ) {
        val maker = ControlFlowMaker()
        val controlFlow = maker.run {
            makeControlFlow()
        }
        val block = maker.buildBlockTree(controlFlow)
        check(block.flow is StructuredFlow)

        val maximalPaths = forwardMaximalPaths(
            root = block,
            yieldingCallsEndPaths = yieldingCallsEndPaths,
            ignoreConstantConditions = ignoreConstantConditions,
            assumeFailureCanHappen = assumeFailureCanHappen,
        )
        val got = basicBlocksToString(block, maximalPaths)

        var passed = false
        try {
            checkCoherence(maximalPaths, block)

            assertEquals(want.trimEnd(), got.trimEnd())

            if (expectedTerminalExpressions != null) {
                val (actualTerminalExpressions, freq) = block.getTerminalExpressions()
                val terminalExpressionsStr = buildString {
                    actualTerminalExpressions.forEach {
                        append("- ")
                        appendRef(block, it.ref, isCondition = false)
                        append('\n')
                    }
                    append(freq)
                }
                assertEquals(
                    expectedTerminalExpressions.trimEnd(),
                    terminalExpressionsStr.trimEnd(),
                )
            }

            passed = true
        } finally {
            if (!passed) {
                console.log(maximalPaths.toMermaid(block))
            }
        }
    }
}

// Methods mirror ControlFlow type names, but make parts optional where it's convenient
// for tests even where that'd be a maintenance hazard for prod code.
@Suppress("TestFunctionName")
internal class ControlFlowMaker {
    val doc = Document(TestDocumentContext())
    private val defaultPos = Position(doc.context.namingContext.loc, 0, 0)
    private val childList = mutableListOf<Tree>()

    fun buildBlockTree(controlFlow: ControlFlow, pos: Position = defaultPos) = BlockTree(
        document = doc,
        pos = pos,
        children = childList,
        flow = StructuredFlow(ControlFlow.StmtBlock.wrap(controlFlow)),
    )

    fun label(nameText: String): JumpLabel =
        doc.nameMaker.unusedSourceName(ParsedName(nameText))

    fun Ref(
        id: String,
        pos: Position = defaultPos,
    ): BlockChildReference = Ref(pos = pos) {
        Call {
            Rn(ParsedName(id))
        }
    }

    fun Ref(
        pos: Position = defaultPos,
        make: Planting.() -> UnpositionedTreeTemplate<*>,
    ): BlockChildReference {
        val index = childList.size
        childList.add(
            doc.treeFarm.grow(pos) {
                make()
            },
        )
        return BlockChildReference(index, pos)
    }

    fun Stmt(
        id: String,
        pos: Position = defaultPos,
    ) = ControlFlow.Stmt(Ref(id, pos = pos))

    fun Stmt(
        pos: Position = defaultPos,
        makeTree: Planting.() -> UnpositionedTreeTemplate<*>,
    ) = ControlFlow.Stmt(Ref(pos, makeTree))

    fun If(
        condition: BlockChildReference,
        thenClause: ControlFlow,
        elseClause: ControlFlow? = null,
        pos: Position = defaultPos,
    ): ControlFlow.If = ControlFlow.If(
        pos = pos,
        condition = condition,
        thenClause = ControlFlow.StmtBlock.wrap(thenClause),
        elseClause = elseClause?.let { ControlFlow.StmtBlock.wrap(it) }
            ?: ControlFlow.StmtBlock(pos, emptyList()),
    )

    fun OrElse(
        onFailLabel: JumpLabel?,
        orClause: ControlFlow,
        elseClause: ControlFlow,
        pos: Position = defaultPos,
    ): ControlFlow.OrElse {
        val label = onFailLabel ?: doc.nameMaker.unusedTemporaryName("orelse")
        return ControlFlow.OrElse(
            pos = pos,
            orClause = ControlFlow.Labeled(
                pos = orClause.pos,
                breakLabel = label,
                continueLabel = null,
                stmts = ControlFlow.StmtBlock.wrap(orClause),
            ),
            elseClause = ControlFlow.StmtBlock.wrap(elseClause),
        )
    }

    fun Loop(
        condition: BlockChildReference,
        body: ControlFlow,
        label: JumpLabel? = null,
        checkPosition: LeftOrRight = LeftOrRight.Left,
        increment: ControlFlow? = null,
        pos: Position = defaultPos,
    ): ControlFlow.Loop = ControlFlow.Loop(
        label = label,
        pos = pos,
        checkPosition = checkPosition,
        condition = condition,
        body = ControlFlow.StmtBlock.wrap(body),
        increment = increment?.let { ControlFlow.StmtBlock.wrap(it) }
            ?: ControlFlow.StmtBlock(condition.pos.rightEdge, emptyList()),
    )

    fun While(
        condition: BlockChildReference,
        body: ControlFlow,
        label: JumpLabel? = null,
        increment: ControlFlow? = null,
        pos: Position = defaultPos,
    ): ControlFlow.Loop = Loop(
        condition = condition,
        body = body,
        label = label,
        checkPosition = LeftOrRight.Left,
        increment = increment,
        pos = pos,
    )

    fun DoWhile(
        body: ControlFlow,
        condition: BlockChildReference,
        label: JumpLabel? = null,
        increment: ControlFlow? = null,
        pos: Position = defaultPos,
    ): ControlFlow.Loop = Loop(
        condition = condition,
        body = body,
        label = label,
        checkPosition = LeftOrRight.Right,
        increment = increment,
        pos = pos,
    )

    fun StmtBlock(
        vararg stmts: ControlFlow,
        pos: Position = defaultPos,
    ) = StmtBlock(
        stmts.toList() as Iterable<ControlFlow>,
        pos = pos,
    )

    fun StmtBlock(
        stmts: Iterable<ControlFlow>,
        pos: Position = defaultPos,
    ) = ControlFlow.StmtBlock(
        pos = pos,
        stmts = stmts.toList(),
    )

    fun Labeled(
        label: JumpLabel,
        body: ControlFlow,
        pos: Position = defaultPos,
        continueLabel: JumpLabel? = null,
    ): ControlFlow {
        if (body is ControlFlow.Loop && body.label == null) {
            return ControlFlow.Loop(
                pos = body.pos,
                label = label,
                checkPosition = body.checkPosition,
                condition = body.condition,
                body = body.body.deepCopy(),
                increment = body.increment.deepCopy(),
            )
        }
        return ControlFlow.Labeled(
            pos = pos,
            breakLabel = label,
            continueLabel = continueLabel,
            stmts = ControlFlow.StmtBlock.wrap(body),
        )
    }

    fun Bubble(pos: Position = defaultPos) = ControlFlow.Stmt(
        Ref(pos = pos) {
            Call(BuiltinFuns.bubble) {}
        },
    )

    fun BreakTo(
        label: JumpLabel?,
        pos: Position = defaultPos,
    ) = ControlFlow.Break(
        pos = pos,
        target = label?.let { NamedJumpSpecifier(it) }
            ?: DefaultJumpSpecifier,
    )

    fun ContinueTo(
        label: JumpLabel?,
        pos: Position = defaultPos,
    ) = ControlFlow.Continue(
        pos = pos,
        target = label?.let { NamedJumpSpecifier(it) }
            ?: DefaultJumpSpecifier,
    )
}

private fun basicBlocksToString(
    block: BlockTree,
    maximalPaths: MaximalPaths,
) = buildString {
    val pathOrder = orderedPathIndices(maximalPaths, ForwardOrBack.Back)
    append("Entry Path${maximalPaths.entryPathIndex}\n")

    for ((description, pathIndices) in listOf(
        "Exits" to maximalPaths.exitPathIndices,
        "Fail exits" to maximalPaths.failExitPathIndices,
    )) {
        append(description)
        pathIndices.forEachIndexed { index, maximalPathIndex ->
            if (index == 0) {
                append(' ')
            } else {
                append("\n  ")
            }
            append("Path")
            append(maximalPathIndex)
        }
        append('\n')
    }
    append('\n')

    fun appendElement(element: MaximalPath.Element) {
        appendRef(block, element.ref, isCondition = element.isCondition)
    }

    pathOrder.forEach { pi ->
        val mp = maximalPaths[pi]
        append("Path")
        append(mp.pathIndex)
        append('\n')

        for (element in mp.elements) {
            append("- ")
            appendElement(element)
            append('\n')
        }
        var before = ""
        mp.followers.forEach {
            append(before)
            before = "else "
            val condition = it.condition
            if (condition != null) {
                append("if (")
                appendElement(condition)
                append(") ")
            }
            append(
                when (it.dir) {
                    ForwardOrBack.Forward -> "->"
                    ForwardOrBack.Back -> "<-"
                },
            )
            append(" Path")
            append(it.pathIndex ?: "_")
            append('\n')
        }
        append('\n')
    }
}

private fun StringBuilder.appendRef(block: BlockTree, ref: BlockChildReference, isCondition: Boolean) {
    append(
        "ref#${ref.index}${if (isCondition) "?" else ""}: `${
            block.dereference(ref)?.target?.toPseudoCode()
        }`",
    )
}

private fun checkCoherence(maximalPaths: MaximalPaths, block: BlockTree) {
    // Check coherence
    maximalPaths.maximalPaths.forEachIndexed { i, path ->
        assertEquals(path.pathIndex.index, i)
        path.elementsAndConditions.forEach { e ->
            assertEquals(
                path.pathIndex,
                e.pathIndex,
                "Element ${
                    block.dereference(e.ref)?.target?.toPseudoCode()
                } has mismatched path index",
            )
        }
        // For each preceder, there is a follower, and vice versa
        for (f in path.followers) {
            val followerIndex = f.pathIndex ?: continue
            val followingPath = maximalPaths[followerIndex]
            assertTrue(
                followingPath.preceders.any { it.pathIndex == path.pathIndex && it.dir == f.dir },
                "No corresponding preceder for $f",
            )
        }
        for (p in path.preceders) {
            val precederIndex = p.pathIndex
            val precedingPath = maximalPaths[precederIndex]
            assertTrue(
                precedingPath.followers.any { it.pathIndex == path.pathIndex && it.dir == p.dir },
                "No corresponding follower for $p",
            )
        }
    }
    assertNotNull(maximalPaths[maximalPaths.entryPathIndex])
    for (pathIndexSet in listOf(
        maximalPaths.exitPathIndices,
        maximalPaths.failExitPathIndices,
    )) {
        for (pathIndex in pathIndexSet) {
            assertNotNull(maximalPaths[pathIndex])
        }
    }
}
