package lang.temper.value

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.ForwardOrBack
import lang.temper.common.LeftOrRight
import lang.temper.common.TestDocumentContext
import lang.temper.common.console
import lang.temper.common.soleElement
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.frontend.structureBlock
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
        ) {
            Do {}
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
        ) {
            Do {
                Stmt("foo")
                Stmt("bar")
            }
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
        ) {
            Do {
                Stmt("init")
                If(
                    cond = { Stmt("wot") },
                    thn = { Stmt("foo") },
                    els = { Stmt("bar") },
                )
            }
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
        ) {
            Do {
                Stmt("init")
                If({ Stmt("wot") }, { Stmt("foo") }, {})
            }
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
                |- ref#2: `beforeBreak()`
                |if (ref#3?: `x()`) -> Path#1
                |else -> Path#1
                |
                |Path#1
            """.trimMargin(),
        ) {
            // my_label: beforeBreak { if (x()) { break my_label } }
            val label = nameMaker.unusedSourceName(ParsedName("my_label"))
            Do(label = label) {
                Stmt("beforeBreak")
                If({ Stmt("x") }, { Break(label) }, {})
            }
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
        ) {
            Stmt("beforeBubble")
            If(
                cond = { Stmt("x") },
                thn = { Bubble() },
                els = {},
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
        ) {
            Stmt("beforeBubble")
            Bubble()
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
                |if (ref#0?: `bad()`) -> Path#2
                |else -> Path#1
                |
                |Path#1
                |- ref#2: `known_safe()`
                |-> Path#3
                |
                |Path#2 catches from orElse#0
                |- ref#3: `recover()`
                |-> Path#3
                |
                |Path#3
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
        ) {
            OrElse(
                or = {
                    If(
                        cond = { Stmt("bad") },
                        thn = { Bubble() },
                        els = { Stmt("known_safe") },
                    )
                },
                els = {
                    Stmt("recover")
                },
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
            Stmt("before_loop")
            While(
                cond = { Stmt("keepGoing") },
                body = {
                    Stmt("start_body")
                    If(
                        cond = { Stmt("x") },
                        thn = { Break() },
                        els = {
                            If(
                                cond = { Stmt("y") },
                                thn = { Continue() },
                                els = { Stmt("notX_notY") },
                            )
                        },
                    )
                    Stmt("end_body")
                },
            )
            Stmt("after_loop")
        }
    }

    @Test
    fun loopWithIncrement() {
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
                |if (ref#3?: `keepGoing()`) -> Path#2
                |else -> Path#3
                |
                |Path#2
                |- ref#4: `start_body()`
                |if (ref#5?: `x()`) -> Path#4
                |else -> Path#5
                |
                |Path#3
                |- ref#11: `after_loop()`
                |
                |Path#5
                |- ref#8: `not_continuing()`
                |- ref#9: `end_body()`
                |-> Path#4
                |
                |Path#4
                |- ref#10: `increment()`
                |<- Path#1
            """.trimMargin(),
        ) {
            val loopLabel = nameMaker.unusedSourceName(ParsedName("loop_label"))
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
            Stmt("before_loop")
            While(
                label = loopLabel,
                cond = { Stmt("keepGoing") },
                body = {
                    Stmt("start_body")
                    If(
                        cond = { Stmt("x") },
                        thn = { Continue(loopLabel) },
                        els = { Stmt("not_continuing") },
                    )
                    Stmt("end_body")
                },
                increment = { Stmt("increment") },
            )
            Stmt("after_loop")
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
                |- ref#1: `body()`
                |if (ref#0?: `cond()`) -> Path#1
                |else -> Path#2

                |Path#1
                |<- Path#0
                |
                |Path#2
            """.trimMargin(),
        ) {
            // do { "body" } while ("cond");
            While(
                testAt = LeftOrRight.Right,
                body = { Stmt("body") },
                cond = { Stmt("cond") },
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
            |- ref#0: `var t#2`
            |- ref#1: `var t#3`
            |- ref#4: `t#2 = x__0 / y__1`
            |if (bubbled) -> Path#2
            |else -> Path#1
            |
            |Path#1
            |- ref#5: `t#3 = t#2`
            |-> Path#3
            |
            |Path#2 catches from orelse__4
            |- ref#6: `t#3 = -1`
            |-> Path#3
            |
            |Path#3
            |- ref#7: `t#3`
        """.trimMargin(),
    ) {
        // WeaverTest.divOrElseTest produces output like this:
        val x = nameMaker.unusedSourceName(ParsedName("x"))
        val y = nameMaker.unusedSourceName(ParsedName("y"))
        val t2 = nameMaker.unusedTemporaryName("t")
        val t3 = nameMaker.unusedTemporaryName("t")
        val orelse0 = nameMaker.unusedSourceName(ParsedName("orelse"))

        // [[ var t#2 ]];
        Decl(t2) {
            V(varSymbol)
            V(void)
        }

        // [[ var t#3 ]];
        Decl(t3) {
            V(varSymbol)
            V(void)
        }
        // orelse#0: do {
        OrElse(
            label = orelse0,
            or = {
                // Use of bubbly version of `/`
                // [[ t#2 = x / y ]];
                Call(BuiltinFuns.setLocalFn) {
                    Ln(t2)
                    Call {
                        val div = BuiltinFuns.divIntIntFn
                        val divType = typeFromSignature(div.sigs!!.soleElement!!)
                        V(Value(div), divType)
                        Rn(x)
                        Rn(y)
                    }
                }
                // [[ t#3 = t#2 ]];
                Call(BuiltinFuns.setLocalFn) {
                    Ln(t3)
                    Rn(t2)
                }
            },
            // } orelse {
            //   [[ t#3 = -1 ]];
            // }
            els = {
                Call(BuiltinFuns.setLocalFn) {
                    Ln(t3)
                    V(Value(-1, TInt))
                }
            },
        )
        // [[ t#3 ]];
        Rn(t3)
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
    ) {
        If(
            cond = { Stmt("question") },
            thn = {
                While(cond = { V(TBoolean.valueTrue) }) {
                    Stmt("then_y")
                }
            },
            els = {
                While({ V(TBoolean.valueTrue) }) {
                    Stmt("else_y")
                }
            },
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
    ) {
        While({ V(TBoolean.valueTrue) }) {
            Stmt("body")
        }
    }

    @Test
    fun infDoWhileLoop() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits
            |Fail exits
            |
            |Path#0
            |- ref#1: `body()`
            |<- Path#0
        """.trimMargin(),
    ) {
        While(
            testAt = LeftOrRight.Right,
            body = { Stmt("body") },
            cond = { V(TBoolean.valueTrue) },
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
            |- ref#1: `body()`
            |<- Path#0
        """.trimMargin(),
    ) {
        While(
            testAt = LeftOrRight.Right,
            body = {
                Stmt("body")
                Continue()
            },
            cond = { V(TBoolean.valueTrue) },
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
            |if (ref#1?: `thereIsAProblem()`) -> Path#1
            |else -> Path#2
            |
            |Path#1
            |
            |Path#2
            |- ref#3: `ok()`
            |<- Path#0
        """.trimMargin(),
    ) {
        While(
            testAt = LeftOrRight.Right,
            body = {
                If(
                    cond = { Stmt("thereIsAProblem") },
                    thn = { Bubble() },
                    // If it's not from the Chimerpagne region,
                    // it's just sparkling control flow.
                    els = {},
                )
                Stmt("ok")
            },
            cond = { V(TBoolean.valueTrue) },
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
    ) {
        While(cond = { V(TBoolean.valueTrue) }) {
            If(
                cond = { Stmt("shouldBreak") },
                thn = { Break() },
                els = { Stmt("body") },
            )
        }
    }

    @Test
    fun doWhileLoopNotInfDueToBreak() = assertMaximalPaths(
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
    ) {
        While(
            testAt = LeftOrRight.Right,
            body = {
                If(
                    cond = { Stmt("shouldBreak") },
                    thn = { Break() },
                    els = { Stmt("body") },
                )
            },
            cond = { V(TBoolean.valueTrue) },
        )
    }

    @Test
    fun whileFalse() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits Path#0
            |Fail exits
            |
            |Path#0
            |- ref#0: `before()`
            |- ref#1?: `false`
            |- ref#3: `after()`
        """.trimMargin(),
    ) {
        Stmt("before")
        While(
            cond = { V(TBoolean.valueFalse) },
            body = { Stmt("body") },
        )
        Stmt("after")
    }

    @Test
    fun doWhileFalse() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits Path#0
            |Fail exits
            |
            |Path#0
            |- ref#0: `before()`
            |- ref#2: `body()`
            |- ref#3: `after()`
        """.trimMargin(),
    ) {
        Stmt("before")
        While(
            testAt = LeftOrRight.Right,
            body = { Stmt("body") },
            cond = { V(TBoolean.valueFalse) },
        )
        Stmt("after")
    }

    @Test
    fun unconvertedContinueInWrappedBody() = assertMaximalPaths(
        want = """
            |Entry Path#0
            |Exits Path#2
            |Fail exits
            |
            |Path#0
            |if (ref#0?: `keepOnLooping()`) -> Path#1
            |else -> Path#2
            |
            |Path#1
            |- ref#5: `startBody()`
            |if (ref#6?: `skipEndBody()`) -> Path#3
            |else -> Path#4
            |
            |Path#2
            |- ref#9: `afterLoop()`
            |
            |Path#4
            |- ref#7: `endBody()`
            |-> Path#3
            |
            |Path#3
            |- ref#8: `incr()`
            |<- Path#0
        """.trimMargin(),
    ) {
        val bodyLabel = nameMaker.unusedSourceName(ParsedName("body"))
        While(
            cond = { Stmt("keepOnLooping") },
            body = {
                Do(
                    label = bodyLabel,
                    continueLabel = bodyLabel,
                ) {
                    Stmt("startBody")
                    If(
                        cond = { Stmt("skipEndBody") },
                        thn = { Continue() },
                        els = {},
                    )
                    Stmt("endBody")
                }
            },
            increment = { Stmt("incr") },
        )
        Stmt("afterLoop")
    }

    private fun BlockPlanting.makeYieldExample() {
        Stmt("first")
        Call { Rn(YieldingFnKind.yield.builtinName) }
        Stmt("second")
        Call { Rn(YieldingFnKind.yield.builtinName) }
        Stmt("third")
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
        makeYieldExample()
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
        makeYieldExample()
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
        Call { Rn(YieldingFnKind.yield.builtinName) }
        Call { Rn(YieldingFnKind.yield.builtinName) }
        Call { Rn(YieldingFnKind.yield.builtinName) }
    }

    @Test
    fun elseVisited() = assertMaximalPaths(
        fails = ConservativeFailure.AtEndOfOr,
        want = """
            |Entry Path#0
            |Exits Path#2
            |Fail exits
            |
            |Path#0
            |- ref#2: `f()`
            |if (bubbled) -> Path#1
            |else -> Path#2
            |
            |Path#1 catches from orelse__0
            |- ref#3: `g()`
            |-> Path#2
            |
            |Path#2
            |
        """.trimMargin(),
    ) {
        OrElse(
            label = nameMaker.unusedSourceName(ParsedName("orelse")),
            or = { Stmt("f") },
            els = { Stmt("g") },
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
            |if (ref#6?: `fail#0`) -> Path#1
            |else -> Path#2
            |
            |Path#1
            |
            |Path#2
            |- ref#8: `void`
        """.trimMargin(),
    ) {
        val fail = nameMaker.unusedTemporaryName("fail")
        val defunctLoopLabel = nameMaker.unusedSourceName(ParsedName("loop"))
        val fakeBreakLabel = nameMaker.unusedSourceName(ParsedName("fake_break"))
        Do(label = defunctLoopLabel) {
            Do(label = fakeBreakLabel, continueLabel = defunctLoopLabel) {
                If(
                    cond = { Rn(fail) },
                    thn = { Bubble() },
                    els = {},
                )
                V(void)
                Continue()
            }
        }
    }

    @Test
    fun inStartAndEndModeNoPathIsGuardedByTwoOrs() {
        assertMaximalPaths(
            fails = ConservativeFailure.AtStartAndEndOnly,
            // f();
            // do {
            //   g();
            //   do {
            //     h();
            //     i();
            //   } orelse do {
            //     a()
            //   }
            //   j();
            //   k();
            // } orelse do {
            //   b()
            // }
            want = """
                |Entry Path#0
                |Exits Path#6
                |Fail exits
                |
                |Path#0
                |- ref#0: `f()`
                |if (bubbled) -> Path#2
                |else -> Path#1
                |
                |## g() flows directly into h(), but the bubbled below
                |## prevents them from being in the same basic block
                |## which is important because h() has a different failure
                |## handler than g().
                |##
                |## Same with f() and g().
                |Path#1
                |- ref#1: `g()`
                |if (bubbled) -> Path#4
                |else -> Path#3
                |
                |Path#3
                |- ref#2: `h()`
                |- ref#3: `i()`
                |if (bubbled) -> Path#4
                |else -> Path#5
                |
                |## After a() in the else block, control
                |## flows to j() same as from i().
                |Path#4 catches from orElse#0
                |- ref#4: `a()`
                |-> Path#5
                |
                |Path#5
                |- ref#5: `j()`
                |- ref#6: `k()`
                |if (bubbled) -> Path#2
                |else -> Path#6
                |
                |Path#2 catches from orElse#1
                |- ref#7: `b()`
                |-> Path#6
                |
                |Path#6
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
        ) {
            Stmt("f")
            OrElse(
                or = {
                    Stmt("g")
                    OrElse(
                        or = {
                            Stmt("h")
                            Stmt("i")
                        },
                        els = { Stmt("a") },
                    )
                    Stmt("j")
                    Stmt("k")
                },
                els = { Stmt("b") },
            )
        }
    }

    private fun assertMaximalPaths(
        want: String,
        yieldingCallsEndPaths: Boolean = false,
        ignoreConstantConditions: Boolean = false,
        fails: ConservativeFailure = ConservativeFailure.CalleeTypeOnly,
        dumpMermaid: Boolean = false, // Helpful for interactive debugging
        makeControlFlow: BlockPlanting.() -> Unit,
    ) {
        val documentContext = TestDocumentContext()
        val document = Document(documentContext)
        val block = document.treeFarm.grow(Position(documentContext.loc, 0, 0)) {
            Block {
                makeControlFlow()
            }
        }
        structureBlock(block)

        val maximalPaths = forwardMaximalPaths(
            root = block,
            fails = fails,
            yieldingCallsEndPaths = yieldingCallsEndPaths,
            ignoreConstantConditions = ignoreConstantConditions,
        )
        if (dumpMermaid) {
            console.log(maximalPaths.toMermaid(block))
        }
        val got = basicBlocksToString(block, maximalPaths)

        var passed = false
        try {
            checkCoherence(maximalPaths, block)

            assertEquals(want.trimEnd(), got.trimEnd())

            passed = true
        } finally {
            if (!passed) {
                console.log(maximalPaths.toMermaid(block))
            }
        }
    }
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

    fun appendElement(element: MaximalPath.AstElement) {
        appendRef(block, element.ref, isCondition = element.isCondition)
    }

    fun appendCondition(cond: MaximalPath.PathElement) {
        when (cond) {
            is MaximalPath.Bubbled -> append("bubbled")
            is MaximalPath.AstElement -> appendElement(cond)
        }
    }

    pathOrder.forEach { pi ->
        val mp = maximalPaths[pi]
        append("Path")
        append(mp.pathIndex)
        if (mp.orLabel != null) {
            append(" catches from ${mp.orLabel}")
        }
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
                appendCondition(condition)
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
                "Element ${e.toDebugString(block)} has mismatched path index",
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

@Suppress("TestFunctionName")
internal fun Planting.Stmt(nameText: String) =
    Call {
        Rn(ParsedName(nameText))
    }

@Suppress("TestFunctionName")
internal fun Planting.Bubble() =
    Call(BuiltinFuns.vBubble) {}

fun Planting.label(text: String): JumpLabel =
    nameMaker.unusedSourceName(ParsedName(text))
