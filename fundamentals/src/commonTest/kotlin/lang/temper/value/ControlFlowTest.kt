package lang.temper.value

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.LeftOrRight
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.assertStructure
import lang.temper.common.console
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.env.Environment
import lang.temper.env.InterpMode
import lang.temper.interp.EmptyEnvironment
import lang.temper.interp.Interpreter
import lang.temper.interp.blankEnvironment
import lang.temper.interp.builtinOnlyEnvironment
import lang.temper.interp.immutableEnvironment
import lang.temper.lexer.Genre
import lang.temper.log.FailLog
import lang.temper.log.MessageTemplate
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import kotlin.test.Test
import kotlin.test.assertEquals

class ControlFlowTest {
    // TODO: do we need to rewrite `break`/`continue` in the increment clause instead of incorporating?
    // What are the desired semantics?

    @Test
    fun simplifiedBlock0() = assertSimplified(
        wantJson = """
            |{
            |  simple: ```
            |    ```,
            |}
        """.trimMargin(),
    ) {
        StmtBlock()
    }

    @Test
    fun dropDeadCode() = assertSimplified(
        wantJson = """
            |{
            |  simple: ```
            |    label__0: do {
            |      one();
            |      break label__0;
            |      two()
            |    }
            |    ```,
            |
            |  simpler: ```
            |    label__0: do {
            |      one();
            |      break label__0;
            |    }
            |    ```,
            |
            |  simplest: ```
            |    one()
            |    ```,
            |}
        """.trimMargin(),
    ) {
        val label = label("label")
        Labeled(
            label,
            StmtBlock(
                Stmt("one"),
                BreakTo(label),
                Stmt("two"),
            ),
        )
    }

    @Test
    fun unreachableIfClause() = assertSimplified(
        wantJson = """
            |{
            |  simple: ```
            |    before();
            |    if (false) {
            |      notReached()
            |    } else {
            |      reached()
            |    };
            |    after()
            |    ```,
            |
            |  simpler: ```
            |    before();
            |    reached();
            |    after()
            |    ```
            |}
        """.trimMargin(),
    ) {
        StmtBlock(
            Stmt("before"),
            If(
                Ref { V(TBoolean.valueFalse) },
                Stmt("notReached"),
                Stmt("reached"),
            ),
            Stmt("after"),
        )
    }

    @Test
    fun eraseDoLoopAndRewriteContinues() = assertSimplified(
        wantJson = """
            |{
            |  simple: ```
            |    loop__0: do (;
            |      ;
            |      incr()) {
            |      if (should_break()) {
            |        breaking();
            |        break loop__0;
            |      } else if (should_continue()) {
            |        continuing();
            |        continue loop__0;
            |      } else {
            |        neither()
            |      };
            |      after_if()
            |    } while (loop_cond())
            |    ```,
            |  simplest: ```
            |    loop__0: while (true) {
            |      continue#1: do {
            |        if (should_break()) {
            |          breaking();
            |          break loop__0;
            |        } else if (should_continue()) {
            |          continuing();
            |          break continue#1;
            |        } else {
            |          neither()
            |        };
            |        after_if()
            |      };
            |      incr();
            |      if (!loop_cond()) {
            |        break;
            |      }
            |    }
            |    ```
            |}
        """.trimMargin(),
    ) {
        val label = label("loop")
        DoWhile(
            label = label,
            condition = Ref("loop_cond"),
            increment = Stmt("incr"),
            body = StmtBlock(
                If(
                    Ref("should_break"),
                    StmtBlock(
                        Stmt("breaking"),
                        BreakTo(label),
                    ),
                    If(
                        Ref("should_continue"),
                        StmtBlock(
                            Stmt("continuing"),
                            ContinueTo(label),
                        ),
                        Stmt("neither"),
                    ),
                ),
                Stmt("after_if"),
            ),
        )
    }

    @Test
    fun incorporateIncrement() = assertSimplified(
        wantJson = """
            |{
            |  simple: ```
            |    loop__0: for (;
            |      loop_cond();
            |      incr()) {
            |      if (should_break()) {
            |        breaking();
            |        break loop__0;
            |      } else if (should_continue()) {
            |        continuing();
            |        continue loop__0;
            |      } else {
            |        neither()
            |      };
            |      after_if()
            |    }
            |    ```,
            |  simplest: ```
            |    loop__0: while (loop_cond()) {
            |      continue#1: do {
            |        if (should_break()) {
            |          breaking();
            |          break loop__0;
            |        } else if (should_continue()) {
            |          continuing();
            |          break continue#1;
            |        } else {
            |          neither()
            |        };
            |        after_if()
            |      };
            |      incr()
            |    }
            |    ```
            |}
        """.trimMargin(),
    ) {
        val label = label("loop")
        While(
            label = label,
            condition = Ref("loop_cond"),
            increment = Stmt("incr"),
            body = StmtBlock(
                If(
                    Ref("should_break"),
                    StmtBlock(
                        Stmt("breaking"),
                        BreakTo(label),
                    ),
                    If(
                        Ref("should_continue"),
                        StmtBlock(
                            Stmt("continuing"),
                            ContinueTo(label),
                        ),
                        Stmt("neither"),
                    ),
                ),
                Stmt("after_if"),
            ),
        )
    }

    @Test
    fun simplifyAwayBreaksAtEnd() = assertSimplified(
        // foo: do { if ("x") { break foo; }; "bar"; if ("y") { break foo } else { "last" } }
        wantJson = """
            |{
            |  simple: ```
            |    foo__0: do {
            |      if (x()) {
            |        break foo__0;
            |      };
            |      bar();
            |      if (y()) {
            |        break foo__0;
            |      } else {
            |        last()
            |      }
            |    }
            |    ```,
            |  simpler: ```
            |    foo__0: do {
            |      if (x()) {
            |        break foo__0;
            |      };
            |      bar();
            |      if (!y()) {
            |        last()
            |      }
            |    }
            |    ```,
            |}
        """.trimMargin(),
    ) {
        val foo = label("foo")
        StmtBlock(
            Labeled(
                foo,
                StmtBlock(
                    If(
                        Ref("x"),
                        BreakTo(foo),
                    ),
                    Stmt("bar"),
                    If(
                        Ref("y"),
                        BreakTo(foo),
                        Stmt("last"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun emptyBlockInterpreted() = assertResultsOfInterpretation(
        cases = listOf(bindings() to ExpectValue(void)),
    ) {
        // do {}
        StmtBlock()
    }

    @Test
    fun blockOfTwoInterpreted() = assertResultsOfInterpretation(
        cases = listOf(bindings("x" to value(10)) to ExpectValue(10)),
    ) {
        // do { -1; x }
        StmtBlock(
            Stmt { V(value(-1)) },
            Stmt { Rn(BuiltinName("x")) },
        )
    }

    @Test
    fun conditionAfterLastStatementInterpreted() = assertResultsOfInterpretation(
        cases = listOf(bindings("x" to TBoolean.valueFalse) to ExpectValue(-1)),
    ) {
        // Make sure we don't confuse conditions with terminal expressions
        // do { -1; if (x) {} else {} }
        StmtBlock(
            Stmt { V(value(-1)) },
            If(
                Ref { Rn(BuiltinName("x")) },
                StmtBlock(),
                StmtBlock(),
            ),
        )
    }

    @Test
    fun ifInterpreted() = assertResultsOfInterpretation(
        cases = listOf(
            bindings("x" to TBoolean.valueFalse) to ExpectValue(1),
            bindings("x" to TBoolean.valueTrue) to ExpectValue(0),
        ),
    ) {
        // if (x) { 0 } else { 1 }
        If(
            Ref { Rn(BuiltinName("x")) },
            Stmt { V(Value(0, TInt)) },
            Stmt { V(Value(1, TInt)) },
        )
    }

    @Test
    fun labeledBlockBreak() = assertResultsOfInterpretation(
        cases = listOf(
            bindings("x" to TBoolean.valueFalse) to ExpectValue("foo"),
            bindings("x" to TBoolean.valueTrue) to ExpectValue("bar"),
        ),
    ) {
        // label: { if (x) { "bar"; break label }; "foo" }
        val label = label("label")
        Labeled(
            label,
            StmtBlock(
                If(
                    Ref { Rn(BuiltinName("x")) },
                    StmtBlock(
                        Stmt { V(value("bar")) },
                        BreakTo(label),
                    ),
                    StmtBlock(),
                ),
                Stmt { V(value("foo")) },
            ),
        )
    }

    /**
     * This code below is designed to shake out loop problems.
     *
     * It loops, accumulating an integer onto a local variable and returns it.
     *
     * The results were found empirically.
     *
     * ```js
     * function f(start, a) {
     *   let limit = start + 10;
     *   var total = 0;
     *   for (var i = start; i < limit; ++i) {
     *     if (i == 4 || i == 6) {
     *       total = total * a;
     *       continue;
     *     } else if (i == 7) {
     *       total = total + a;
     *     } else if (i == a) {
     *       total = -total;
     *       break;
     *     }
     *     total += i;
     *   }
     *   return total;
     * }
     *
     * function doIt(start, a) {
     *   console.log(`f(${start}, ${a})=${f(start, a)}`);
     * }
     *
     * doIt(-1, 2);
     * doIt(0, 3);
     * doIt(1, 5);
     * doIt(2, 7);
     * doIt(3, 11);
     * doIt(4, 13);
     * doIt(5, 17);
     * ```
     *
     * Run, that yields
     *
     * ```
     * f(-1, 2)=0
     * f(0, 3)=-3
     * f(1, 5)=-30
     * f(2, 7)=332
     * f(3, 11)=-463
     * f(4, 13)=-135
     * f(5, 17)=186
     * ```
     *
     * Since the condition is initially true, we test this with both a while
     * and a do...while version.
     */
    private val accumulatorExampleCases = listOf(
        bindings("start" to value(-1), "a" to value(2)) to ExpectValue(0),
        bindings("start" to value(0), "a" to value(3)) to ExpectValue(-3),
        bindings("start" to value(1), "a" to value(5)) to ExpectValue(-30),
        bindings("start" to value(2), "a" to value(7)) to ExpectValue(332),
        bindings("start" to value(3), "a" to value(11)) to ExpectValue(-463),
        bindings("start" to value(4), "a" to value(13)) to ExpectValue(-135),
        bindings("start" to value(5), "a" to value(17)) to ExpectValue(186),
    )

    private fun ControlFlowMaker.makeAccumulatorCode(checkPosition: LeftOrRight): ControlFlow {
        val start = BuiltinName("start")
        val a = BuiltinName("a")
        val limit = doc.nameMaker.unusedSourceName(ParsedName("limit"))
        val total = doc.nameMaker.unusedSourceName(ParsedName("total"))
        val i = doc.nameMaker.unusedSourceName(ParsedName("i"))
        val t0 = doc.nameMaker.unusedTemporaryName("t")
        return StmtBlock(
            // let limit = start + 10;
            Stmt {
                Decl(limit) {
                    V(initSymbol)
                    Call(BuiltinFuns.plusFn) {
                        Rn(start)
                        V(value(10))
                    }
                }
            },
            // var total = 0;
            Stmt {
                Decl(total) {
                    V(varSymbol)
                    V(void)
                    V(initSymbol)
                    V(value(0))
                }
            },
            // var i = start
            Stmt {
                Decl(i) {
                    V(varSymbol)
                    V(void)
                    V(initSymbol)
                    Rn(start)
                }
            },
            // for (; i < limit; ++i) { ...
            Loop(
                condition = Ref { // i < limit
                    Call(BuiltinFuns.lessThanFn) {
                        Rn(i)
                        Rn(limit)
                    }
                },
                checkPosition = checkPosition,
                increment = Stmt { // ++i
                    Call(BuiltinFuns.setLocalFn) {
                        Ln(i)
                        Call(BuiltinFuns.plusFn) {
                            Rn(i)
                            V(value(1))
                        }
                    }
                },
                body = StmtBlock(
                    //   if (i == 4 || i == 6) {
                    // But `||` is not a function, so fake it with an `if`.
                    Stmt { // if (i == 4) { t#0 = true } else { t#0 =
                        Decl(t0) {}
                    },
                    If(
                        Ref {
                            Call(BuiltinFuns.equalsFn) {
                                Rn(i)
                                V(value(4))
                            }
                        },
                        Stmt {
                            Call(BuiltinFuns.setLocalFn) {
                                Ln(t0)
                                V(TBoolean.valueTrue)
                            }
                        },
                        Stmt {
                            Call(BuiltinFuns.setLocalFn) {
                                Ln(t0)
                                Call(BuiltinFuns.equalsFn) {
                                    Rn(i)
                                    V(value(6))
                                }
                            }
                        },
                    ),
                    If(
                        Ref { Rn(t0) },
                        StmtBlock(
                            //     total = total * a
                            Stmt {
                                Call(BuiltinFuns.setLocalFn) {
                                    Ln(total)
                                    Call(BuiltinFuns.timesFn) {
                                        Rn(total)
                                        Rn(a)
                                    }
                                }
                            },
                            //     continue;
                            ContinueTo(null),
                        ),
                        StmtBlock(
                            If(
                                //   } else if (i == 7) {
                                Ref {
                                    Call(BuiltinFuns.equalsFn) {
                                        Rn(i)
                                        V(value(7))
                                    }
                                },
                                Stmt {
                                    //     total = total + a;
                                    Call(BuiltinFuns.setLocalFn) {
                                        Ln(total)
                                        Call(BuiltinFuns.plusFn) {
                                            Rn(total)
                                            Rn(a)
                                        }
                                    }
                                },
                                If(
                                    //   } else if (i == a) {
                                    Ref {
                                        Call(BuiltinFuns.equalsFn) {
                                            Rn(i)
                                            Rn(a)
                                        }
                                    },
                                    StmtBlock(
                                        //     total = -total;
                                        Stmt {
                                            Call(BuiltinFuns.setLocalFn) {
                                                Ln(total)
                                                Call(BuiltinFuns.minusFn) {
                                                    V(value(0))
                                                    Rn(total)
                                                }
                                            }
                                        },
                                        //     break;
                                        BreakTo(null),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    Stmt {
                        //   total += i;
                        Call(BuiltinFuns.setLocalFn) {
                            Ln(total)
                            Call(BuiltinFuns.plusFn) {
                                Rn(total)
                                Rn(i)
                            }
                        }
                    },
                ),
            ),
            // return total
            Stmt { Rn(total) },
        )
    }

    @Test
    fun accumulatorExampleLeftCondition() = assertResultsOfInterpretation(
        cases = accumulatorExampleCases,
    ) {
        makeAccumulatorCode(LeftOrRight.Left)
    }

    @Test
    fun accumulatorExampleRightCondition() = assertResultsOfInterpretation(
        cases = accumulatorExampleCases,
    ) {
        makeAccumulatorCode(LeftOrRight.Right)
    }

    @Test
    fun orElseWithExplicitBubbleInterpreted() = assertResultsOfInterpretation(
        // do { if (bubbles) { bubble(); } "ok" } orelse { "recovered" }
        cases = listOf(
            bindings("bubbles" to TBoolean.valueFalse) to ExpectValue("ok"),
            bindings("bubbles" to TBoolean.valueTrue) to ExpectValue("recovered"),
        ),
        beforeSimplest = { cf, block ->
            // Before we say we're not going to add any jumps, we need to rewrite
            // the bubble() call to a jump.
            rewriteBubbleToBreak(cf, block)
        },
    ) {
        OrElse(
            onFailLabel = null, // surprise me
            orClause = StmtBlock(
                If(
                    Ref { Rn(BuiltinName("bubbles")) },
                    Stmt { Call(BubbleFn) {} },
                ),
                Stmt { V(value("ok")) },
            ),
            elseClause = Stmt { V(value("recovered")) },
        )
    }

    @Test
    fun smallPrimesNestedLoops() = assertResultsOfInterpretation(
        cases = run {
            val somePrimes = setOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47)
            (2..49).map { n ->
                bindings("n" to value(n)) to ExpectValue(n in somePrimes)
            }
        },
    ) {
        val n = BuiltinName("n")
        val matched = ParsedName("matched")
        val prime = ParsedName("prime")
        val i = ParsedName("i")
        val j = ParsedName("j")
        val k = ParsedName("k")
        val loop1 = label("loop1")
        val loop2 = label("loop2")
        val loop3 = label("loop3")
        StmtBlock(
            // var matched = false;
            Stmt {
                Decl(matched) {
                    V(varSymbol)
                    V(void)
                    V(initSymbol)
                    V(TBoolean.valueFalse)
                }
            },
            // var prime = 1;
            Stmt {
                Decl(prime) {
                    V(varSymbol)
                    V(void)
                    V(initSymbol)
                    V(value(1))
                }
            },
            // loop1: for (var i = 0; i < 25; i++) {
            Stmt {
                Decl(i) {
                    V(varSymbol)
                    V(void)
                    V(initSymbol)
                    V(value(0))
                }
            },
            While(
                label = loop1,
                condition = Ref {
                    Call(BuiltinFuns.lessThanFn) {
                        Rn(i)
                        V(value(25))
                    }
                },
                increment = Stmt {
                    Call(BuiltinFuns.setLocalFn) {
                        Ln(i)
                        Call(BuiltinFuns.plusFn) {
                            Rn(i)
                            V(value(1))
                        }
                    }
                },
                body = StmtBlock(
                    //   loop2: for (var j = 0; j < 25; j++) {
                    Stmt {
                        Decl(j) {
                            V(varSymbol)
                            V(void)
                            V(initSymbol)
                            V(value(0))
                        }
                    },
                    While(
                        label = loop2,
                        condition = Ref {
                            Call(BuiltinFuns.lessThanFn) {
                                Rn(j)
                                V(value(25))
                            }
                        },
                        increment = Stmt {
                            Call(BuiltinFuns.setLocalFn) {
                                Ln(j)
                                Call(BuiltinFuns.plusFn) {
                                    Rn(j)
                                    V(value(1))
                                }
                            }
                        },
                        body = StmtBlock(
                            //     loop3: for (var k = 0; k < 25; k++) {
                            Stmt {
                                Decl(k) {
                                    V(varSymbol)
                                    V(void)
                                    V(initSymbol)
                                    V(value(0))
                                }
                            },
                            While(
                                label = loop3,
                                condition = Ref {
                                    Call(BuiltinFuns.lessThanFn) {
                                        Rn(k)
                                        V(value(25))
                                    }
                                },
                                increment = Stmt {
                                    Call(BuiltinFuns.setLocalFn) {
                                        Ln(k)
                                        Call(BuiltinFuns.plusFn) {
                                            Rn(k)
                                            V(value(1))
                                        }
                                    }
                                },
                                body = StmtBlock(
                                    //       prime += 1;
                                    Stmt {
                                        Call(BuiltinFuns.setLocalFn) {
                                            Ln(prime)
                                            Call(BuiltinFuns.plusFn) {
                                                Rn(prime)
                                                V(value(1))
                                            }
                                        }
                                    },
                                    //       if (prime > n) { break loop1 }
                                    If(
                                        Ref {
                                            Call(BuiltinFuns.greaterThanFn) {
                                                Rn(prime)
                                                Rn(n)
                                            }
                                        },
                                        BreakTo(loop1),
                                    ),
                                    //       if (prime != 2 && prime % 2 == 0) {
                                    //         continue loop1;
                                    //       }
                                    If(
                                        Ref {
                                            Call(BuiltinFuns.notEqualsFn) {
                                                Rn(prime)
                                                V(value(2))
                                            }
                                        },
                                        If(
                                            Ref {
                                                Call(BuiltinFuns.equalsFn) {
                                                    Call(BuiltinFuns.modIntIntSafeFn) {
                                                        Rn(prime)
                                                        V(value(2))
                                                    }
                                                    V(value(0))
                                                }
                                            },
                                            ContinueTo(loop1),
                                        ),
                                    ),
                                    //       if (prime != 3 && prime % 3 == 0) {
                                    //         continue loop2;
                                    //       }
                                    If(
                                        Ref {
                                            Call(BuiltinFuns.notEqualsFn) {
                                                Rn(prime)
                                                V(value(3))
                                            }
                                        },
                                        If(
                                            Ref {
                                                Call(BuiltinFuns.equalsFn) {
                                                    Call(BuiltinFuns.modIntIntSafeFn) {
                                                        Rn(prime)
                                                        V(value(3))
                                                    }
                                                    V(value(0))
                                                }
                                            },
                                            ContinueTo(loop2),
                                        ),
                                    ),
                                    //       if (prime != 5 && prime % 5 == 0) {
                                    //         continue loop3;
                                    //       }
                                    If(
                                        Ref {
                                            Call(BuiltinFuns.notEqualsFn) {
                                                Rn(prime)
                                                V(value(5))
                                            }
                                        },
                                        If(
                                            Ref {
                                                Call(BuiltinFuns.equalsFn) {
                                                    Call(BuiltinFuns.modIntIntSafeFn) {
                                                        Rn(prime)
                                                        V(value(5))
                                                    }
                                                    V(value(0))
                                                }
                                            },
                                            ContinueTo(loop3),
                                        ),
                                    ),
                                    //       if (prime != 7 && prime % 7 == 0) {
                                    //         continue;
                                    //       }
                                    If(
                                        Ref {
                                            Call(BuiltinFuns.notEqualsFn) {
                                                Rn(prime)
                                                V(value(7))
                                            }
                                        },
                                        If(
                                            Ref {
                                                Call(BuiltinFuns.equalsFn) {
                                                    Call(BuiltinFuns.modIntIntSafeFn) {
                                                        Rn(prime)
                                                        V(value(7))
                                                    }
                                                    V(value(0))
                                                }
                                            },
                                            ContinueTo(null),
                                        ),
                                    ),
                                    //       if (n == prime) {
                                    //         matched = true;
                                    //         break loop1;
                                    //       }
                                    If(
                                        Ref {
                                            Call(BuiltinFuns.equalsFn) {
                                                Rn(n)
                                                Rn(prime)
                                            }
                                        },
                                        StmtBlock(
                                            Stmt {
                                                Call(BuiltinFuns.setLocalFn) {
                                                    Ln(matched)
                                                    V(TBoolean.valueTrue)
                                                }
                                            },
                                            BreakTo(loop1),
                                        ),
                                    ),
                                ),
                            ),
                            //     }
                        ),
                    ),
                    //   }
                ),
                // }
            ),
            Stmt { Rn(matched) },
        )
    }

    @Test
    fun labeledLoopWithIncrementAndBodyThatDoesNotCompleteNormally() = assertSimplified(
        wantJson = """
            |{
            |  simple: ```
            |    loop__0: for (;
            |      cond();
            |      incr()) {
            |      body();
            |      while (true) {
            |        continue loop__0;
            |      }
            |    }
            |    ```,
            |  simpler: ```
            |    loop__0: for (;
            |      cond();
            |      incr()) {
            |      body();
            |      body#1: do {
            |        continue loop__0;
            |      }
            |    }
            |    ```,
            |  simplest: ```
            |    while (cond()) {
            |      body();
            |      incr()
            |    }
            |    ```,
            |}
        """.trimMargin(),
    ) {
        val outerLoopLabel = label("loop")
        While(
            label = outerLoopLabel,
            condition = Ref("cond"),
            increment = Stmt("incr"), // The body continues.  Do not eliminate this.
            body = StmtBlock(
                Stmt("body"),
                While(
                    condition = Ref { V(TBoolean.valueTrue) },
                    body = ContinueTo(outerLoopLabel),
                ),
            ),
        )
    }

    @Test
    fun nestedLoopOuterHasIncrementInnerOnlyContinues() = assertSimplified(
        wantJson = """
            |{
            |  simple: ```
            |    outer__0: for (;
            |      i__1 < 4;
            |      {
            |        let postfixReturn#2;
            |        postfixReturn#2 = i__1;
            |        i__1 = i__1 + 1;
            |        t#3 = postfixReturn#2;
            |        t#3
            |    }) {
            |      var str__4;
            |      str__4 = "row ";
            |      var j__5;
            |      j__5 = 0;
            |      while (true) {
            |        str__4 = cat(str__4);
            |        if (i__1 <= j__5) {
            |          continue outer__0;
            |          void;
            |        };
            |        void;
            |        void;
            |        j__5 = j__5 + 1;
            |      };
            |      void;
            |    }
            |    ```,
            |
            |  simpler: ```
            |    outer__0: for (;
            |      i__1 < 4;
            |      {
            |        let postfixReturn#2;
            |        postfixReturn#2 = i__1;
            |        i__1 = i__1 + 1;
            |        t#3 = postfixReturn#2;
            |        t#3
            |    }) {
            |      var str__4;
            |      str__4 = "row ";
            |      var j__5;
            |      j__5 = 0;
            |      while (true) {
            |        str__4 = cat(str__4);
            |        if (i__1 <= j__5) {
            |          continue outer__0;
            |        };
            |        j__5 = j__5 + 1
            |      }
            |    }
            |    ```,
            |
            |  simplest: ```
            |    while (i__1 < 4) {
            |      continue#6: do {
            |        var str__4;
            |        str__4 = "row ";
            |        var j__5;
            |        j__5 = 0;
            |        while (true) {
            |          str__4 = cat(str__4);
            |          if (i__1 <= j__5) {
            |            break continue#6;
            |          };
            |          j__5 = j__5 + 1
            |        }
            |      };
            |      let postfixReturn#2;
            |      postfixReturn#2 = i__1;
            |      i__1 = i__1 + 1;
            |      t#3 = postfixReturn#2;
            |      t#3
            |    }
            |    ```
            |}
        """.trimMargin(),
    ) {
        val outer = label("outer")
        val i = doc.nameMaker.unusedSourceName(ParsedName("i"))
        val postfixReturn = doc.nameMaker.unusedTemporaryName("postfixReturn")
        val t = doc.nameMaker.unusedTemporaryName("t")
        val str = doc.nameMaker.unusedSourceName(ParsedName("str"))
        val j = doc.nameMaker.unusedSourceName(ParsedName("j"))
        // outer__11: for (;
        While(
            label = outer,
            //   i__10 < 4;
            condition = Ref {
                Call(BuiltinFuns.lessThanFn) {
                    Rn(i)
                    V(value(4))
                }
            },
            increment = StmtBlock(
                //   do {
                //     let postfixReturn#0;
                Stmt { Decl(postfixReturn) },
                //     postfixReturn#0 = i__10;
                Stmt {
                    Call(BuiltinFuns.setLocalFn) {
                        Ln(postfixReturn)
                        Rn(i)
                    }
                },
                //     i__10 = i__10 + 1;
                Stmt {
                    Call(BuiltinFuns.setLocalFn) {
                        Ln(i)
                        Call(BuiltinFuns.plusFn) {
                            Rn(i)
                            V(value(1))
                        }
                    }
                },
                //     t#31 = postfixReturn#0;
                Stmt {
                    Call(BuiltinFuns.setLocalFn) {
                        Ln(t)
                        Rn(postfixReturn)
                    }
                },
                //     t#31
                Stmt { Rn(t) },
                //   }) {
            ),
            body = StmtBlock(
                //   var str__12;
                Stmt {
                    Decl(str) {
                        V(varSymbol)
                        V(void)
                    }
                },
                //   str__12 = "row ";
                Stmt {
                    Call(BuiltinFuns.setLocalFn) {
                        Ln(str)
                        V(value("row "))
                    }
                },
                //   var j__13;
                Stmt {
                    Decl(j) {
                        V(varSymbol)
                        V(void)
                    }
                },
                //   j__13 = 0;
                Stmt {
                    Call(BuiltinFuns.setLocalFn) {
                        Ln(j)
                        V(value(0))
                    }
                },
                While(
                    //   while (true) {
                    condition = Ref { V(TBoolean.valueTrue) },
                    //     str__12 = cat(str__12);
                    body = StmtBlock(
                        Stmt {
                            Call(BuiltinFuns.setLocalFn) {
                                Ln(str)
                                Call(BuiltinFuns.strCatFn) { Rn(str) }
                            }
                        },
                        If(
                            //     if (i__10 <= j__13) {
                            Ref {
                                Call(BuiltinFuns.lessEqualsFn) {
                                    Rn(i)
                                    Rn(j)
                                }
                            },
                            StmtBlock(
                                //         continue outer__11;
                                ContinueTo(outer),
                                //         void;
                                Stmt { V(void) },
                                //         void;
                                Stmt { V(void) },
                            ),
                            //     };
                        ),
                        //     void;
                        Stmt { V(void) },
                        //     void;
                        Stmt { V(void) },
                        //     j__13 = j__13 + 1;
                        Stmt {
                            Call(BuiltinFuns.setLocalFn) {
                                Ln(j)
                                Call(BuiltinFuns.plusFn) {
                                    Rn(j)
                                    V(value(1))
                                }
                            }
                        },
                        //     void;
                        Stmt { V(void) },
                        //   };
                    ),
                ),
                //   void;
                Stmt { V(void) },
                //   void;
                Stmt { V(void) },
                // }
            ),
        )
    }

    @Test
    fun returnBubbleInWhile() = assertSimplified(
        """
            |{
            |  simple: ```
            |    l__0: while (true) {
            |      bubble();
            |      break l__0;
            |    }
            |    ```,
            |  simpler: ```
            |    l__0: do {
            |      bubble()
            |    }
            |    ```,
            |  simplest: ```
            |    bubble()
            |    ```,
            |}
        """.trimMargin(),
    ) {
        val label = label("l")
        StmtBlock(
            While(
                label = label,
                condition = Ref { V(TBoolean.valueTrue) },
                body = StmtBlock(
                    Stmt { Call(BubbleFn) {} },
                    BreakTo(label),
                ),
                increment = StmtBlock(),
            ),
        )
    }

    @Test
    fun breaksRewrittenWhenErasingLoop() = assertSimplified(
        """
            |{
            |  simple: ```
            |    l__0: while (true) {
            |      f();
            |      break;
            |    }
            |    ```,
            |  simpler: ```
            |    l__0: do {
            |      f()
            |    }
            |    ```,
            |  simplest: ```
            |    f()
            |    ```,
            |}
        """.trimMargin(),
    ) {
        val label = label("l")
        StmtBlock(
            While(
                label = label,
                condition = Ref { V(TBoolean.valueTrue) },
                body = StmtBlock(
                    Stmt("f"),
                    BreakTo(null),
                ),
                increment = StmtBlock(),
            ),
        )
    }

    @Test
    fun orClauseAlwaysBubbles() = assertSimplified(
        wantJson = """
            |{
            |  simple: ```
            |    before();
            |    orelse__0: {
            |      bubble()
            |    } orelse {
            |      elseClause()
            |    };
            |    after()
            |    ```,
            |  simpler: ```
            |    before();
            |    orelse__0: {
            |      bubble()
            |    } orelse {
            |      elseClause()
            |    };
            |    after()
            |    ```,
            |  simplest: ```
            |    before();
            |    elseClause();
            |    after()
            |    ```
            |}
        """.trimMargin(),
    ) {
        StmtBlock(
            Stmt("before"),
            OrElse(
                label("orelse"),
                Stmt { Call(BubbleFn) {} },
                Stmt("elseClause"),
            ),
            Stmt("after"),
        )
    }

    @Test
    fun orClauseConditionallyBubbles() = assertSimplified(
        wantJson = """
            |{
            |  simple: ```
            |    before();
            |    orelse__0: {
            |      if (f()) {
            |        bubble()
            |      }
            |    } orelse {
            |      elseClause()
            |    };
            |    after()
            |    ```,
            |}
        """.trimMargin(),
    ) {
        StmtBlock(
            Stmt("before"),
            OrElse(
                label("orelse"),
                If(
                    Ref("f"),
                    Stmt { Call(BubbleFn) {} },
                ),
                Stmt("elseClause"),
            ),
            Stmt("after"),
        )
    }

    @Test
    fun breakingFromLabeledGroupsLotsOfWays() = assertSimplified(
        """
            |{
            |  simple: ```
            |    label__0: do {
            |      orelse#1: {
            |        if (false) {
            |          break label__0;
            |        }
            |      } orelse {
            |        break label__0;
            |      };
            |      one();
            |      break label__0;
            |      two()
            |    };
            |    three()
            |    ```,
            |   simpler: ```
            |    label__0: do {
            |      orelse#1: {} orelse {
            |        break label__0;
            |      };
            |      one();
            |      break label__0;
            |    };
            |    three()
            |    ```,
            |  simplest: ```
            |    one();
            |    three()
            |    ```
            |}
        """.trimMargin(),
    ) {
        val label = label("label")
        StmtBlock(
            Labeled(
                label,
                StmtBlock(
                    OrElse(
                        null,
                        If(
                            Ref { V(TBoolean.valueFalse) },
                            BreakTo(label),
                        ),
                        BreakTo(label),
                    ),
                    Stmt("one"),
                    BreakTo(label),
                    Stmt("two"),
                ),
            ),
            Stmt("three"),
        )
    }

    @Test
    fun doLoopThatBreaksAtEndOfBody() = assertSimplified(
        """
            |{
            |  simple: ```
            |    a__0: do {
            |      f();
            |      break a__0;
            |    } while (g())
            |    ```,
            |  simplest: ```
            |    f()
            |    ```
            |}
        """.trimMargin(),
    ) {
        val label = label("a")
        DoWhile(
            label = label,
            body = StmtBlock(
                Stmt("f"),
                BreakTo(label),
            ),
            condition = Ref("g"),
        )
    }

    @Test
    fun stmtsAfterLoopThatAlwaysTerminates() = assertSimplified(
        wantJson = """
            |{
            |  simple: ```
            |    label__0: do {
            |      a();
            |      do {
            |        b();
            |        break label__0;
            |      } while (c());
            |      d()
            |    }
            |    ```,
            |  simplest: ```
            |    label__0: do {
            |      a();
            |      b();
            |      break label__0;
            |    }
            |    ```,
            |}
        """.trimMargin(),
    ) {
        val label = label("label")
        Labeled(
            label,
            StmtBlock(
                Stmt("a"),
                DoWhile(
                    body = StmtBlock(
                        Stmt("b"),
                        BreakTo(label),
                    ),
                    condition = Ref("c"),
                ),
                Stmt("d"),
            ),
        )
    }

    @Test
    fun breakRewrittenWhenLoopAroundItErased() = assertSimplified(
        """
            |{
            |  simple: ```
            |    do {
            |      a();
            |      while (b()) {
            |        if (c()) {
            |          break;
            |        }
            |      };
            |      if (d()) {
            |        break;
            |      };
            |      e();
            |      break;
            |    } while (f())
            |    ```,
            |  simplest: ```
            |    body#0: do {
            |      a();
            |      while (b()) {
            |        if (c()) {
            |          break;
            |        }
            |      };
            |      if (d()) {
            |        break body#0;
            |      };
            |      e()
            |    }
            |    ```
            |}
        """.trimMargin(),
    ) {
        DoWhile(
            body = StmtBlock(
                Stmt("a"),
                While(
                    condition = Ref("b"),
                    body = If(
                        Ref("c"),
                        BreakTo(null),
                    ),
                ),
                If(
                    Ref("d"),
                    BreakTo(null),
                ),
                Stmt("e"),
                BreakTo(null),
            ),
            condition = Ref("f"),
        )
    }

    private fun ControlFlowMaker.makeNestedLoopsThatBumpSameCounterExample(): ControlFlow {
        val a = doc.nameMaker.unusedSourceName(ParsedName("a"))
        return StmtBlock(
            // var a__21 = 0;
            Stmt {
                Decl(a) {
                    V(varSymbol)
                    V(void)
                    V(initSymbol)
                    V(value(0))
                }
            },
            // do {
            DoWhile(
                body = StmtBlock(
                    //   a__21 = a__21 + 1;
                    Stmt {
                        Call(BuiltinFuns.setLocalFn) {
                            Ln(a)
                            Call(BuiltinFuns.plusFn) {
                                Rn(a)
                                V(value(1))
                            }
                        }
                    },
                    //   for (;
                    While(
                        //     a__21 <= 5;
                        condition = Ref {
                            Call(BuiltinFuns.lessEqualsFn) {
                                Rn(a)
                                V(value(5))
                            }
                        },
                        //     a__21 = a__21 + 1) {
                        increment = Stmt {
                            Call(BuiltinFuns.setLocalFn) {
                                Ln(a)
                                Call(BuiltinFuns.plusFn) {
                                    Rn(a)
                                    V(value(1))
                                }
                            }
                        },
                        body = StmtBlock(
                            //     a__21 = a__21 + 1;
                            Stmt {
                                Call(BuiltinFuns.setLocalFn) {
                                    Ln(a)
                                    Call(BuiltinFuns.plusFn) {
                                        Rn(a)
                                        V(value(1))
                                    }
                                }
                            },
                            //     println("65")
                            Stmt {
                                Call(PrintLnFn) { V(value("65")) }
                            },
                        ),
                        //   };
                    ),
                    //   println("66");
                    Stmt {
                        Call(PrintLnFn) { V(value("66")) }
                    },
                ),
                // } while (a__21 <= 8);
                condition = Ref {
                    Call(BuiltinFuns.lessEqualsFn) {
                        Rn(a)
                        V(value(8))
                    }
                },
            ),
        )
    }

    @Test
    fun nestedLoopsThatBumpSameCounter() = assertResultsOfInterpretation(
        cases = listOf(
            bindings() to ExpectStdout(
                """
                    |65
                    |65
                    |65
                    |66
                    |66
                    |66
                """.trimMargin(),
            ),
        ),
    ) {
        makeNestedLoopsThatBumpSameCounterExample()
    }

    private fun assertSimplified(
        wantJson: String,
        makeControlFlow: ControlFlowMaker.() -> ControlFlow,
    ) {
        val controlFlowMaker = ControlFlowMaker()
        val controlFlow = controlFlowMaker.makeControlFlow()
        val block = controlFlowMaker.buildBlockTree(controlFlow)
        fun snapshotBlock(): String =
            block.toPseudoCode(singleLine = false).trimEnd()

        val simple = snapshotBlock()
        simplifyStructuredBlock(
            block,
            block.flow as StructuredFlow,
            assumeAllJumpsResolved = false,
            assumeResultsCaptured = true,
        )
        val simpler = snapshotBlock()

        simplifyStructuredBlock(
            block,
            block.flow as StructuredFlow,
            assumeAllJumpsResolved = true,
            assumeResultsCaptured = true,
        )
        val simplest = snapshotBlock()

        assertStructure(
            wantJson,
            object : Structured {
                override fun destructure(structureSink: StructureSink) = structureSink.obj {
                    key("simple") { value(simple) }
                    key("simpler", isDefault = simpler == simple) { value(simpler) }
                    key("simplest", isDefault = simplest == simpler) { value(simplest) }
                }
            },
        )
    }

    private fun assertResultsOfInterpretation(
        cases: List<Pair<Map<TemperName, Value<*>>, Expectation>>,
        beforeSimplest: (ControlFlow, BlockTree) -> Unit = { _, _ -> },
        make: ControlFlowMaker.() -> ControlFlow,
    ) {
        val maker = ControlFlowMaker()
        val controlFlow = maker.make()
        val block = maker.buildBlockTree(controlFlow)
        val variants = listOf("simple" to null, "simpler" to false, "simplest" to true)
        for ((variantDesc, assume) in variants) { // Test simplified versions to
            if (assume == true) {
                beforeSimplest((block.flow as StructuredFlow).controlFlow, block)
            }
            when (assume) {
                null -> {}
                else -> block.replaceFlow(
                    simplifyControlFlow(
                        block,
                        (block.flow as StructuredFlow).controlFlow,
                        assumeAllJumpsResolved = assume,
                        assumeResultsCaptured = false,
                    ),
                )
            }

            var testIndex = -1
            for ((bindings, expectation) in cases) {
                testIndex += 1

                val interpretResult = interpretBlockForTest(block, bindings)
                val message = "Test $variantDesc $testIndex"
                val (got, wanted) = when (expectation) {
                    is ExpectValue -> interpretResult.result to expectation.value
                    is ExpectStdout -> interpretResult.stdout to expectation.stdout
                }

                if (wanted != got || interpretResult.logSink.hasFatal) {
                    console.group(message) {
                        console.group("block") {
                            block.toPseudoCode(console.textOutput)
                        }
                        console.group("bindings") {
                            bindings.forEach { (t, u) ->
                                console.log("$t -> $u")
                            }
                        }
                        console.group("env") {
                            val env = interpretResult.env
                            env.locallyDeclared.forEach {
                                console.log(
                                    "$it -> ${
                                        env[it, InterpreterCallback.NullInterpreterCallback]
                                    }",
                                )
                            }
                        }
                        console.group("log") {
                            interpretResult.logSink.allEntries.forEach {
                                console.log(it.messageText)
                            }
                        }
                    }
                }
                assertEquals(wanted, got, message)
            }
        }
    }
}

internal data class InterpretResult(
    val result: PartialResult,
    val stdout: String,
    val env: Environment,
    val failLog: FailLog,
    val logSink: ListBackedLogSink,
)

internal fun interpretBlockForTest(block: BlockTree, bindings: Map<TemperName, Value<*>>): InterpretResult {
    var nStepsTaken = 0
    val continueCondition = {
        nStepsTaken++
        nStepsTaken < STEP_RUN_LIMIT
    }
    val logSink = ListBackedLogSink()
    val failLog = FailLog(logSink)
    val interpreter = Interpreter(
        failLog = failLog,
        logSink = logSink,
        stage = Stage.Run,
        nameMaker = block.document.nameMaker,
        continueCondition = continueCondition,
    )
    val extraBuiltins = immutableEnvironment(
        EmptyEnvironment,
        mapOf<TemperName, Value<*>>(
            BuiltinName("println") to Value(PrintLnFn),
        ),
        isLongLived = false,
    )
    val builtins = builtinOnlyEnvironment(extraBuiltins, Genre.Library)
    val bindingEnv = immutableEnvironment(builtins, bindings, isLongLived = false)
    val env = blankEnvironment(bindingEnv)
    val result = interpreter.interpretReuseEnvironment(
        block,
        env,
        InterpMode.Full,
        mayWrapEnvironment = false,
    )
    val stdout = buildString {
        var needsLine = false
        for (entry in logSink.allEntries) {
            if (entry.template == MessageTemplate.StandardOut) {
                if (needsLine) {
                    append('\n')
                } else {
                    needsLine = true
                }
                if (entry.values.size == 1) {
                    append(entry.values.first())
                } else {
                    append("???")
                }
            }
        }
    }
    return InterpretResult(
        result,
        stdout,
        env,
        failLog,
        logSink,
    )
}

private const val STEP_RUN_LIMIT = 100

private fun bindings(vararg bs: Pair<String, Value<*>>): Map<TemperName, Value<*>> =
    buildMap {
        bs.forEach {
            this[BuiltinName(it.first)] = it.second
        }
    }

private fun value(x: Int) = Value(x, TInt)
private fun value(x: String) = Value(x, TString)

private fun rewriteBubbleToBreak(cf: ControlFlow, blockTree: BlockTree) {
    fun findBubbles(cf: ControlFlow, orElseLabel: JumpLabel?) {
        when (cf) {
            is ControlFlow.OrElse -> {
                findBubbles(cf.orClause.stmts, cf.orClause.breakLabel)
                findBubbles(cf.elseClause, orElseLabel)
                return
            }
            is ControlFlow.Stmt -> {
                val t = blockTree.dereference(cf.ref)?.target
                if (t != null && isBubbleCall(t) && orElseLabel != null) {
                    cf.parent!!.withMutableStmtList { siblings ->
                        val index = siblings.indexOf(cf)
                        siblings[index] = ControlFlow.Break(cf.pos, NamedJumpSpecifier(orElseLabel))
                    }
                }
            }
            else -> {
                for (child in cf.clauses.toList()) {
                    findBubbles(child, orElseLabel)
                }
            }
        }
    }
    findBubbles(cf, null)
}

/** Expected output from an interpreter. */
internal sealed class Expectation

internal data class ExpectValue(val value: Value<*>) : Expectation() {
    constructor(i: Int) : this(value(i))
    constructor(s: String) : this(value(s))
    constructor(b: Boolean) : this(TBoolean.value(b))
}

internal data class ExpectStdout(val stdout: String) : Expectation()

private object PrintLnFn : NamedBuiltinFun, CallableValue {
    override val name: String = "println"
    override val sigs = listOf(
        Signature2(WellKnownTypes.voidType2, false, listOf(WellKnownTypes.stringType2)),
    )

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult =
        when (interpMode) {
            InterpMode.Partial -> NotYet
            InterpMode.Full -> {
                val (arg) = args.unpackPositionedOr(1, cb) { return@invoke it }
                val str = TString.unpackOrNull(arg)
                    ?: buildString { arg.stringify(this) }
                cb.logSink.log(Log.Info, MessageTemplate.StandardOut, cb.pos, listOf(str))
                void
            }
        }
}
