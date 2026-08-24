package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.builtin.Assign
import lang.temper.builtin.AwaitFn
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.BuiltinLogicalOperators
import lang.temper.builtin.Types
import lang.temper.builtin.vTypeAngleFn
import lang.temper.common.LeftOrRight
import lang.temper.common.TestDocumentContext
import lang.temper.common.console
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.testCodeLocation
import lang.temper.common.withCapturingConsole
import lang.temper.env.InterpMode
import lang.temper.format.CollectedTokens
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.toStringViaTokenSink
import lang.temper.frontend.typestage.MakeResultsExplicit
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ParsedName
import lang.temper.type.TypeFormal
import lang.temper.type.plantCallWithTypeInfo
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockPlanting
import lang.temper.value.BlockTree
import lang.temper.value.BubbleFn
import lang.temper.value.ControlFlow
import lang.temper.value.DefaultJumpSpecifier
import lang.temper.value.Document
import lang.temper.value.JumpLabel
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NamedJumpSpecifier
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.ReifiedType
import lang.temper.value.StructuredFlow
import lang.temper.value.TBoolean
import lang.temper.value.TInt
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.TType
import lang.temper.value.UnresolvedJumpSpecifier
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.blockPartialEvaluationOrder
import lang.temper.value.fnParsedName
import lang.temper.value.freeTarget
import lang.temper.value.isEmptyBlock
import lang.temper.value.returnParsedName
import lang.temper.value.simplifyControlFlow
import lang.temper.value.toPseudoCode
import lang.temper.value.typeFromSignature
import lang.temper.value.vLabelSymbol
import lang.temper.value.vReturnDeclSymbol
import lang.temper.value.vSsaSymbol
import lang.temper.value.vStaySymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.vVarSymbol
import lang.temper.value.varSymbol
import lang.temper.value.void
import kotlin.test.Test
import kotlin.test.assertEquals
import lang.temper.type.WellKnownTypes as WKT

// Below, we use fake builtin functions to simplify testing with various kinds of calls.
// `b` and `bb` are boolean returning functions useful in conditions.  `bb` can bubble.
// `f` and `ff` are `Int32` returning. `ff` can bubble.
// `v` and `vv` are `void` returning. `vv` can bubble.

class WeaverTest {
    @Test
    fun linear() = assertWovenRoot(
        want = """
            |[[ let return__0 ]];
            |[[ f(1) ]];
            |[[ return__0 = f(1) ]];
        """.trimMargin(),
    ) {
        CallF { V(1) }
        CallF { V(1) }
    }

    @Test
    fun linearVoid() = assertWovenRoot(
        want = """
            |[[ let return__0 ]];
            |[[ v(1) ]];
            |[[ v(1) ]];
            |[[ return__0 = void ]];
        """.trimMargin(),
    ) {
        CallV { V(1) }
        CallV { V(1) }
    }

    @Test
    fun linearBubblyVoid() = assertWovenRoot(
        want = """
            |[[ let return__0 ]];
            |[[ vv(1) ]];
            |[[ vv(1) ]];
            |[[ return__0 = void ]];
        """.trimMargin(),
    ) {
        CallVV { V(1) }
        CallVV { V(1) }
    }

    @Test
    fun nestingAssignments() = assertWovenRoot(
        // var a, b, c, d;
        // a = b = c = d = f(0)
        want = """
            |[[ let return__4 ]];
            |[[ let t#5 ]];
            |[[ var a__0 ]];
            |[[ var b__1 ]];
            |[[ var c__2 ]];
            |[[ var d__3 ]];
            |[[ t#5 = f(0) ]];
            |[[ d__3 = t#5 ]];
            |[[ c__2 = t#5 ]];
            |[[ b__1 = t#5 ]];
            |[[ a__0 = t#5 ]];
            |[[ return__4 = t#5 ]];
        """.trimMargin(),
    ) {
        val vars = listOf("a", "b", "c", "d")
            .map { nameMaker.unusedSourceName(ParsedName(it)) }
        val (a, b, c, d) = vars
        Block {
            for (v in vars) {
                Decl {
                    Ln(v, WKT.intType)
                    V(vVarSymbol)
                    V(void)
                }
            }
            Assign(a, WKT.intType) {
                Assign(b, WKT.intType) {
                    Assign(c, WKT.intType) {
                        Assign(d, WKT.intType) {
                            CallF { V(0) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun nestedIfs() = assertWovenRoot(
        /*
         * if (b(1)) {
         *   f(2);
         *   if (bb(3)) {
         *     f(4)
         *   } else {
         *     ff(5)
         *   }
         * } else {
         *   f(6)
         * }
         */
        want = """
            |[[ let return__0 ]];
            |if ([[ b(1) ]]) {
            |  [[ f(2) ]];
            |  [[ let t#1 ]];
            |  [[ t#1 = bb(3) ]];
            |  if ([[ t#1 ]]) {
            |    [[ return__0 = f(4) ]];
            |  } else {
            |    [[ return__0 = ff(5) ]];
            |  }
            |} else {
            |  [[ return__0 = f(6) ]];
            |}
        """.trimMargin(),
    ) {
        If(
            { CallB { V(1) } },
            thn = {
                CallF { V(2) }
                If(
                    { CallBB { V(3) } },
                    thn = { CallF { V(4) } },
                    els = { CallFF { V(5) } },
                )
            },
            els = {
                CallF { V(6) }
            },
        )
    }

    @Test
    fun ifNoReturnVar() = assertWovenRoot(
        runMakeResultsExplicit = false,
        /*
         * if (b(0)) {
         *   "foo"
         * } else {
         *   "bar"
         * }
         */
        want = """
            |if ([[ b(0) ]]) {
            |  [[ t#0 = "foo" ]];
            |} else {
            |  [[ t#0 = "bar" ]];
            |}
            |## And the temporary is ready for MakeResultsExplicit to run after.
            |[[ t#0 ]];
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        If(
            { CallB { V(0) } },
            thn = {
                V(Value("foo", TString), WKT.stringType)
            },
            els = {
                V(Value("bar", TString), WKT.stringType)
            },
        )
    }

    @Test
    fun nestedIfsWithDifferentAssignees() = assertWovenRoot(
        buildInput = {
            val x = nameMaker.unusedSourceName(ParsedName("x"))
            val y = nameMaker.unusedSourceName(ParsedName("y"))
            val z = nameMaker.unusedSourceName(ParsedName("z"))
            If(
                { CallB { V(1) } },
                thn = {
                    CallF { V(2) }
                    If(
                        { CallFF { V(3) } },
                        thn = {
                            Assign(y, WKT.booleanType) { V(TBoolean.valueFalse) }
                            Assign(x, WKT.booleanType) { CallF { V(4) } }
                        },
                        els = {
                            Assign(x, WKT.booleanType) { V(TBoolean.valueTrue) }
                            Assign(y, WKT.booleanType) { CallFF { V(5) } }
                        },
                    )
                },
                els = {
                    Assign(x, WKT.booleanType) {
                        Assign(y, WKT.booleanType) {
                            CallF { V(6) }
                        }
                    }
                },
            )
            plantCallWithTypeInfo(BuiltinFuns.eqGenericFn) {
                plantCallWithTypeInfo(BuiltinFuns.eqGenericFn) {
                    Rn(x, WKT.booleanType)
                    Rn(y, WKT.booleanType)
                }
                Rn(z, WKT.booleanType)
            }
        },
        /*
         * let x, y, z;
         * z = if (b(1)) {
         *   f(2);
         *   if (bb(3)) {
         *     y = false;
         *     x = f(4)
         *   } else {
         *     x = true;
         *     y = ff(5)
         *   }
         * } else {
         *   x = y = f(6)
         * };
         * (x == y) == z
         */
        want = """
            |## t#5, t#6, and t#7 are assigned inside the `if`s but used outside so cannot
            |## be declared within.
            |[[ let t#5 ]];
            |[[ let return__3 ]];
            |if ([[ b(1) ]]) {
            |  [[ f(2) ]];
            |## t#4 is not needed outside this scope, so it's allocated here.
            |  [[ let t#4 ]];
            |  [[ t#4 = ff(3) ]];
            |  if ([[ t#4 ]]) {
            |    [[ y__1 = false ]];
            |    [[ x__0 = f(4) ]];
            |## Here, the result of the assignment is captured but without
            |## introducing a nesting assignment like `t#5 = x__0 = f(4)`.
            |    [[ t#5 = x__0 ]];
            |  } else {
            |    [[ x__0 = true ]];
            |    [[ y__1 = ff(5) ]];
            |    [[ t#5 = y__1 ]];
            |  }
            |} else {
            |  [[ y__1 = f(6) ]];
            |  [[ x__0 = y__1 ]];
            |  [[ t#5 = x__0 ]];
            |## Same here, and the assignment `x = y = b(6)` has flattened out.
            |}
            |[[ return__3 = x__0 == y__1 == z__2 ]];
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun orElse() = assertWovenRoot(
        // var x;
        // x =
        //   do {
        //     ff(1)
        //   } orelse do {
        //     f(2)
        //   };
        // !x
        buildInput = {
            val x = nameMaker.unusedSourceName(ParsedName("x"))
            Decl {
                Ln(x, WKT.booleanType)
                V(varSymbol)
                V(void)
            }
            Assign(x, WKT.booleanType) {
                Block {
                    OrElse(
                        or = {
                            Do {
                                CallFF { V(1) }
                            }
                        },
                        els = {
                            Do {
                                CallF { V(2) }
                            }
                        },
                    )
                }
            }
            plantCallWithTypeInfo(BuiltinFuns.notFn) {
                Rn(x, WKT.booleanType)
            }
        },
        want = """
            |[[ var t#3 ]];
            |[[ let return__2 ]];
            |[[ var x__0 ]];
            |orElse#1: do {
            |  [[ t#3 = ff(1) ]];
            |} orelse {
            |  [[ t#3 = f(2) ]];
            |}
            |[[ x__0 = t#3 ]];
            |[[ return__2 = !x__0 ]];
        """.trimMargin(),
    )

    @Test
    fun localFailsWithoutSurroundingBlocks() = assertWovenRoot(
        // ff(1) orelse f(2)
        buildInput = {
            Block {
                OrElse({ CallFF { V(1) } }, { CallF { V(2) } })
            }
        },
        // The return variable needs to be `var` so that later stages can
        // do conservative analysis of the flow assuming that the assignment
        // in the `else` clause can happen even after the first assignment
        // succeeds.
        want = """
            |[[ var return__1 ]];
            |orElse#0: do {
            |  [[ return__1 = ff(1) ]];
            |} orelse {
            |  [[ return__1 = f(2) ]];
            |}
        """.trimMargin(),
    )

    @Test
    fun ifResultVarNotUsedOutOfScope() = assertWovenRoot(
        // let y;
        // y = 1 + do {
        //   if (b(0)) {
        //     let x;
        //     if (b(1)) {
        //       x = f(2);
        //     } else {
        //       x = f(3);
        //     }
        //   } else {
        //     1
        //   }
        // };
        buildInput = {
            val x = nameMaker.unusedSourceName(ParsedName("x"))
            val y = nameMaker.unusedSourceName(ParsedName("y"))
            Block {
                Decl { Ln(y, WKT.intType) }
                Assign(y, WKT.intType) {
                    plantCallWithTypeInfo(BuiltinFuns.plusIntIntFn) {
                        V(1)
                        Block {
                            Decl { Ln(x, WKT.intType) }
                            If(
                                cond = { CallB { V(0) } },
                                thn = {
                                    If(
                                        cond = { CallB { V(1) } },
                                        thn = {
                                            Assign(x, WKT.intType) { CallF { V(2) } }
                                        },
                                        els = {
                                            Assign(x, WKT.intType) { CallF { V(3) } }
                                        },
                                    )
                                },
                                els = {
                                    V(1)
                                },
                            )
                        }
                    }
                }
                V(void, WKT.voidType)
            }
        },
        want = """
            |[[ let return__2 ]];
            |[[ return__2 = void ]];
            |[[ let t#3 ]];
            |[[ let y__1 ]];
            |[[ let x__0 ]];
            |if ([[ b(0) ]]) {
            |  if ([[ b(1) ]]) {
            |    [[ x__0 = f(2) ]];
            |  } else {
            |    [[ x__0 = f(3) ]];
            |  }
            |  [[ t#3 = x__0 ]];
            |} else {
            |  [[ t#3 = 1 ]];
            |}
            |[[ y__1 = 1 + t#3 ]];
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun loopContainingBreakAndContinue() = assertWovenRoot(
        buildInput = {
            While({ CallB { V(0) } }) {
                If(
                    { CallB { V(1) } },
                    thn = {
                        CallF { V(2) }
                        Break()
                        V(void)
                    },
                    els = {
                        If(
                            { CallB { V(3) } },
                            thn = {
                                CallF {
                                    CallFF { V(4) }
                                }
                                Continue()
                                V(void)
                            },
                            els = {
                                CallF { V(5) }
                                V(void)
                            },
                        )
                    },
                )
                CallF { V(6) }
                V(void)
            }
            CallF { V(7) }
        },
        /*
         * while (b(0)) {
         *   if (b(1)) {
         *     f(2);
         *     break;
         *   } else if (b(3)) {
         *     f(ff(4));
         *     continue;
         *   } else {
         *     f(5);
         *   }
         *   f(6);
         * }
         * f(7)
         */
        want = """
            |[[ let return__0 ]];
            |for (;
            |  [[ b(0) ]];
            |) {
            |  if ([[ b(1) ]]) {
            |    [[ f(2) ]];
            |    break;
            |  } else if ([[ b(3) ]]) {
            |## Intermediate bubbly call is captured in a temporary.
            |## But that temporary is never used outside the loop, so it
            |## doesn't need to be declared `var`.
            |    [[ let t#1 ]];
            |    [[ t#1 = ff(4) ]];
            |    [[ f(t#1) ]];
            |    continue;
            |  } else {
            |    [[ f(5) ]];
            |  }
            |  [[ f(6) ]];
            |}
            |[[ return__0 = f(7) ]];
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun simpleDoWhileLoop() = assertWovenRoot(
        /*
         * do {
         *  f(0);
         * } while (b(1));
         */
        want = """
            |[[ let return__0 ]];
            |[[ return__0 = void ]];
            |do {
            |  [[ f(0) ]];
            |} while ([[ b(1) ]]);
        """.trimMargin(),
    ) {
        While(cond = { CallB { V(1) } }, testAt = LeftOrRight.Right) {
            CallF { V(0) }
        }
    }

    @Test
    fun simpleWhileLoopWithBubblyCondition() = assertWovenRoot(
        /*
         * while (bb(1)) {
         *   f(0);
         * }
         */
        want = """
            |[[ let return__0 ]];
            |[[ return__0 = void ]];
            |for (;
            |  [[ true ]];
            |) {
            |  [[ let t#1 ]];
            |  [[ t#1 = bb(1) ]];
            |  if ([[ !t#1 ]]) {
            |    break;
            |  }
            |  [[ f(0) ]];
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        While(cond = { CallBB { V(1) } }, testAt = LeftOrRight.Left) {
            CallF { V(0) }
        }
    }

    @Test
    fun simpleWhileLoopWithTrappedBubblesInBody() = assertWovenRoot(
        /*
         * while (b(0)) {
         *   let x = ff(1) orelse -1;
         *   f(x);
         * }
         */
        want = """
            |[[ let return__2 ]];
            |[[ return__2 = void ]];
            |for (;
            |  [[ b(0) ]];
            |) {
            |  [[ var t#3 ]];
            |  [[ let x__0 ]];
            |  orElse#1: do {
            |    [[ t#3 = ff(1) ]];
            |  } orelse {
            |    [[ t#3 = -1 ]];
            |  }
            |  [[ x__0 = t#3 ]];
            |  [[ f(x__0) ]];
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        val x = nameMaker.unusedSourceName(ParsedName("x"))
        While(cond = { CallB { V(0) } }, testAt = LeftOrRight.Left) {
            Decl { Ln(x) }
            Assign(x, null) {
                Block {
                    OrElse(
                        or = { Call(ffCallee) { V(1) } },
                        els = { V(-1) },
                    )
                }
            }
            CallF { Rn(x) }
        }
    }

    @Test
    fun simpleDoWhileLoopWithBubblyCondition() = assertWovenRoot(
        /*
         * do {
         *  f(0);
         * } while (bb(1));
         */
        want = """
            |[[ let return__0 ]];
            |[[ return__0 = void ]];
            |for (;
            |  [[ true ]];
            |) {
            |  [[ let t#1 ]];
            |  [[ f(0) ]];
            |  [[ t#1 = bb(1) ]];
            |  if ([[ !t#1 ]]) {
            |    break;
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        While(cond = { CallBB { V(1) } }, testAt = LeftOrRight.Right) {
            CallF { V(0) }
        }
    }

    @Test
    fun doWhileLoopWithBubblyConditionAndNestedContinue() = assertWovenRoot(
        /*
         * lbl: do {
         *  if (b(0)) {
         *    continue;
         *  } else if (b(1)) {
         *    break;
         *  } else if (b(2)) {
         *    continue lbl;
         *  }
         *  f(3);
         * } while (bb(4));
         */
        want = """
            |[[ let return__1 ]];
            |[[ return__1 = void ]];
            |lbl__0: for (;
            |  [[ true ]];
            |) {
            |  [[ let t#3 ]];
            |  continue#2 & continue#2: do {
            |    if ([[ b(0) ]]) {
            |## Rewritten continue
            |      continue continue#2;
            |    } else if ([[ b(1) ]]) {
            |      break;
            |    } else if ([[ b(2) ]]) {
            |## Ditto
            |      continue continue#2;
            |    }
            |## Rest of body as normal
            |    [[ f(3) ]];
            |  }
            |##^ This end of block is where the continue goes to
            |## so it's queued up for the condition that follows.
            |  [[ t#3 = bb(4) ]];
            |  if ([[ !t#3 ]]) {
            |    break;
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        val lbl: JumpLabel = nameMaker.unusedSourceName(ParsedName("lbl"))
        While(cond = { CallBB { V(4) } }, testAt = LeftOrRight.Right, label = lbl) {
            If(
                cond = { CallB { V(0) } },
                thn = { Continue(label = null) },
                els = {
                    If(
                        cond = { CallB { V(1) } },
                        thn = { Break(label = null) },
                        els = {
                            If(
                                cond = { CallB { V(2) } },
                                thn = { Continue(label = lbl) },
                                els = {},
                            )
                        },
                    )
                },
            )
            CallF { V(3) }
        }
    }

    @Test
    fun matchNums() = assertWovenRoot(
        /*
         * let s = when (x) {
         *   0 -> "zero";
         *   1 -> "one";
         *   2 -> "two";
         *   else -> "many";
         * };
         * s
         *
         * That is equivalent to:
         *
         * let s = if (x == 0) {
         *   "zero"
         * } else if (x == 1) {
         *   "one"
         * } else if (x == 2) {
         *   "two"
         * } else {
         *   "many"
         * };
         * s
         */
        want = """
            |## One temporary allocated even though results are KnownValueCaptureResults
            |[[ let t#3 ]];
            |[[ let return__2 ]];
            |[[ let s__1 ]];
            |if ([[ x__0 == 0 ]]) {
            |  [[ t#3 = "one" ]];
            |} else if ([[ x__0 == 1 ]]) {
            |  [[ t#3 = "two" ]];
            |} else if ([[ x__0 == 3 ]]) {
            |  [[ t#3 = "three" ]];
            |} else {
            |  [[ t#3 = "many" ]];
            |}
            |[[ s__1 = t#3 ]];
            |[[ return__2 = s__1 ]];
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        val x = nameMaker.unusedSourceName(ParsedName("x"))
        val s = nameMaker.unusedSourceName(ParsedName("s"))
        Decl { Ln(s, WKT.stringType) }
        Assign(s, WKT.stringType) {
            Block {
                If(
                    {
                        plantCallWithTypeInfo(BuiltinFuns.eqIntFn) {
                            Rn(x)
                            V(0)
                        }
                    },
                    thn = { V(Value("one", TString), WKT.stringType) },
                    els = {
                        If(
                            {
                                plantCallWithTypeInfo(BuiltinFuns.eqIntFn) {
                                    Rn(x)
                                    V(1)
                                }
                            },
                            thn = { V(Value("two", TString), WKT.stringType) },
                            els = {
                                If(
                                    {
                                        plantCallWithTypeInfo(BuiltinFuns.eqIntFn) {
                                            Rn(x)
                                            V(3)
                                        }
                                    },
                                    thn = { V(Value("three", TString), WKT.stringType) },
                                    els = { V(Value("many", TString), WKT.stringType) },
                                )
                            },
                        )
                    },
                )
            }
        }
        Rn(s, WKT.stringType)
    }

    @Test
    fun divOrElse() = assertWovenRoot(
        // let z = (x / y) orelse -1;
        // z
        want = """
            |[[ var t#5 ]];
            |[[ let return__4 ]];
            |[[ let z__2 ]];
            |orElse#3: do {
            |  [[ t#5 = x__0 / y__1 ]];
            |} orelse {
            |  [[ t#5 = -1 ]];
            |}
            |[[ z__2 = t#5 ]];
            |[[ return__4 = z__2 ]];
        """.trimMargin(),
    ) {
        val x = nameMaker.unusedSourceName(ParsedName("x"))
        val y = nameMaker.unusedSourceName(ParsedName("y"))
        val z = nameMaker.unusedSourceName(ParsedName("z"))
        Decl { Ln(z, WKT.intType) }
        Assign(z, WKT.intType) {
            Block {
                OrElse(
                    or = {
                        plantCallWithTypeInfo(BuiltinFuns.divIntIntFn) {
                            Rn(x)
                            Rn(y)
                        }
                    },
                    els = {
                        V(-1)
                    },
                )
            }
        }
        Rn(z, WKT.intType)
    }

    @Test
    fun ifThenOrElseAssigned() = assertWovenRoot(
        /*
         * let x: Int?;
         * x = (
         *   if (b(0)) {
         *     f(1)
         *   } else {
         *     ff(2)
         *   }
         * ) orelse null
         */
        want = """
            |[[ let return__2 ]];
            |[[ let x__0: Int32? ]];
            |[[ var t#3 ]];
            |orElse#1: do {
            |  if ([[ b(0) ]]) {
            |    [[ t#3 = f(1) ]];
            |  } else {
            |    [[ t#3 = ff(2) ]];
            |  }
            |} orelse {
            |  [[ t#3 = null ]];
            |}
            |[[ x__0 = t#3 ]];
            |[[ return__2 = x__0 ]];
        """.trimMargin(),
    ) {
        val x = nameMaker.unusedSourceName(ParsedName("x"))

        val intOrNull = MkType2(WKT.intTypeDefinition).canBeNull().get()

        Block {
            Decl {
                Ln(x, type = hackMapNewStyleToOld(intOrNull))
                V(vTypeSymbol)
                V(Value(ReifiedType(intOrNull), TType))
            }

            Assign(x, hackMapNewStyleToOld(intOrNull)) {
                Block {
                    OrElse(
                        or = {
                            If(
                                cond = { CallB { V(0) } },
                                thn = {
                                    CallF { V(1) }
                                },
                                els = {
                                    CallFF { V(2) }
                                },
                            )
                        },
                        els = {
                            // This `null` was not getting assigned to the same temporary as other branches.
                            V(
                                TNull.value,
                                type = hackMapNewStyleToOld(
                                    MkType2(WKT.neverTypeDefinition)
                                        .actuals(listOf(WKT.intType2))
                                        .canBeNull(true)
                                        .get(),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    @Test
    fun returningBubblesJustBubbles() = assertWovenRoot(
        // let x: Int32;
        // x = bubble<Int32>();
        // x
        want = """
            |[[ let return__1 ]];
            |[[ let x__0: Int32 ]];
            |[[ bubble<Int32>() ]];
            |[[ x__0 = panic<Int32>() ]];
            |## return__1 captures x so x needs to be assigned even though control cannot reach here.
            |[[ return__1 = x__0 ]];
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        val x = nameMaker.unusedSourceName(ParsedName("x"))
        Decl(x) {
            V(vTypeSymbol)
            V(Types.vInt)
        }
        Assign(x, WKT.intType) {
            Call {
                Call(vTypeAngleFn) {
                    V(
                        BuiltinFuns.vBubble,
                        typeFromSignature(BubbleFn.sigs.first { it.typeFormals.isNotEmpty() }),
                    )
                    V(Types.vInt, WKT.typeType)
                }
            }
        }
        Rn(x)
    }

    @Test
    fun pureVirtualIsNotAResult() = assertWovenRoot(
        want = """
            |[[ let return__0 ]];
            |[[ pureVirtual() ]];
        """.trimMargin(),
    ) {
        plantCallWithTypeInfo(BuiltinFuns.pureVirtualFn) {}
    }

    @Test
    fun awaitIsPulledToRoot() = assertWovenRoot(
        // Calls to `await` pause, so the interpreter needs `await`s in predictable places.
        // Pausing is at the statement level, so the interpreter can handle:
        // - await calls, `await(p);`, that are statements in ControlFlow.Stmt entries.
        // - assignments of await calls, `t#1 = await(p);`, that are likewise statements.

        // let p: Promise<Int>;
        // export let sum = await p + await p;

        want = """
            |[[ let t#2 ]];
            |[[ let t#3 ]];
            |[[ let return__1 ]];
            |[[ return__1 = void ]];
            |[[ let p__0: Promise<Int32> ]];
            |[[ t#2 = await p__0 ]];
            |[[ t#3 = await p__0 ]];
            |[[ `test//`.sum = t#2 + t#3 ]];
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        val p = nameMaker.unusedSourceName(ParsedName("p"))
        val promiseInt = MkType2(WKT.promiseTypeDefinition).actuals(listOf(WKT.intType2)).get()
        val sum = ExportedName(nameMaker.namingContext, ParsedName("sum"))

        Decl {
            Ln(p, type = hackMapNewStyleToOld(promiseInt))
            V(vTypeSymbol)
            V(Value(ReifiedType(promiseInt)))
        }

        Assign(sum, WKT.intType) {
            plantCallWithTypeInfo(BuiltinFuns.plusIntIntFn) {
                plantCallWithTypeInfo(AwaitFn, listOf(WKT.intType)) {
                    Rn(p, type = hackMapNewStyleToOld(promiseInt))
                }
                plantCallWithTypeInfo(AwaitFn, listOf(WKT.intType)) {
                    Rn(p, type = hackMapNewStyleToOld(promiseInt))
                }
            }
        }

        // Splitting out an initializer always leaves a void at the end.
        V(void, WKT.voidType)
    }

    @Test
    fun nestedFn() = assertWovenRoot(
        buildInput = {
            // let x;
            // x = foo(fn /* return__123 */: String {
            //   "foo"
            // });
            val x = nameMaker.unusedSourceName(ParsedName("x"))
            val preAllocatedReturn = nameMaker.unusedSourceName(returnParsedName)
            val fnLabel = nameMaker.unusedSourceName(fnParsedName)
            Block {
                Decl {
                    Ln(x, WKT.stringType)
                }
                Assign(x, WKT.stringType) {
                    Call {
                        Rn(BuiltinName("foo"))
                        Fn {
                            V(vReturnDeclSymbol)
                            Decl {
                                Ln(preAllocatedReturn, WKT.stringType)
                                V(vTypeSymbol)
                                V(Types.vString)
                                V(vSsaSymbol)
                                V(void)
                            }
                            V(vStaySymbol)
                            Stay()
                            Block {
                                // CallF { V(0) }
                                V(vLabelSymbol)
                                Ln(fnLabel)
                                V(Value("foo", TString), WKT.stringType)
                            }
                        }
                    }
                }

                V(void)
            }
        },
        want = """
            |[[ let return__3 ]];
            |[[ return__3 = void ]];
            |[[ let x__0 ]];
            |[[ x__0 = foo(@stay fn /* return__1 */: String {
            |    fn__2: do {
            |      return__1 = "foo"
            |    };
            |}) ]];
        """.trimMargin(),
    )

    @Test
    fun mixedExternalConstantNames() = assertWovenRoot(
        buildInput = {
            val (x, y, z) = listOf("x", "y", "z")
                .map { nameMaker.unusedSourceName(ParsedName(it)) }

            Decl { Ln(x, WKT.intType) }
            Decl { Ln(y, WKT.intType) }
            Fn {
                Block {
                    Decl { Ln(z, WKT.intType) }
                    Assign(z, WKT.intType) {
                        Block {
                            If(
                                cond = { CallB { V(0) } },
                                thn = { Rn(x, WKT.intType) },
                                els = { Rn(y, WKT.intType) },
                            )
                        }
                    }
                    CallF { Rn(z, WKT.intType) }
                }
            }
        },
        // let x = 1;
        // let y = 2;
        // fn {
        //   let z = if (b(0)) {
        //     x
        //   } else {
        //     y
        //   };
        //   f(z)
        // }
        want = """
            |[[ let return__3 ]];
            |[[ let x__0 ]];
            |[[ let y__1 ]];
            |[[ return__3 = fn /* return__4 */{
            |  let t#5, z__2;
            |  {
            |    if (b(0)) {
            |      t#5 = x__0
            |    } else {
            |      t#5 = y__1
            |    }
            |  };
            |  z__2 = t#5;
            |  return__4 = f(z__2);
            |}
            |]];
        """.trimMargin(),
    )

    private fun assertWovenRoot(
        want: String,
        runMakeResultsExplicit: Boolean = true,
        verbose: Boolean = false,
        buildInput: BlockPlanting.() -> Unit,
    ): Unit = assertWovenRoot(
        want = want,
        block = run {
            Document(TestDocumentContext()).treeFarm.grow(Position(testCodeLocation, 0, 0)) {
                Block {
                    buildInput()
                }
            }
        },
        runMakeResultsExplicit = runMakeResultsExplicit,
        verbose = verbose,
    )

    private fun assertWovenRoot(
        want: String,
        block: BlockTree,
        runMakeResultsExplicit: Boolean = true,
        verbose: Boolean = false,
    ) {
        // Move each non-trivial flow-control construct into its own BlockTree,
        // the way things are in staging before the first weaving.
        val debugConsole = if (verbose) console else null
        debugConsole?.group("Before reblock") {
            block.toPseudoCode(debugConsole.textOutput)
        }
        reblock(block)
        debugConsole?.group("After reblock") {
            block.toPseudoCode(debugConsole.textOutput)
        }

        if (runMakeResultsExplicit) {
            withCapturingConsole {
                MakeResultsExplicit.makeAllResultsExplicit(
                    console = debugConsole ?: it,
                    moduleRoot = block,
                    needResultForModuleRoot = true,
                )
            }
            debugConsole?.group("After MakeResultsExplicit") {
                block.toPseudoCode(debugConsole.textOutput)
            }
        }

        Weaver.weave(
            block,
            sprinkleSecurityDust = true,
            simplifyRttiCalls = true,
            pullSpecialsRootward = true,
            nameAllFunctions = false,
            resultsAlreadyCaptured = runMakeResultsExplicit,
        )
        debugConsole?.group("After weave") {
            block.toPseudoCode(debugConsole.textOutput)
        }

        block.replaceFlow(
            simplifyControlFlow(
                block,
                structureBlock(block).controlFlow,
                assumeAllJumpsResolved = false,
                assumeResultsCaptured = true,
                assumeUseBeforeInitChecked = runMakeResultsExplicit,
                logicalOperators = BuiltinLogicalOperators,
            ),
        )

        val flow = (block.flow as StructuredFlow).controlFlow.deepCopy()
        val got = dumpStructureEmbedding(block, flow)

        assertEquals(want.trimEnd(), got.trimEnd())
    }
}

private class PlaceholderFunction(
    override val name: String,
    override val callMayFailPerSe: Boolean,
    returnType: Type2,
    typeFormals: List<TypeFormal> = emptyList(),
) : NamedBuiltinFun {
    override val sigs = listOf(
        Signature2(
            returnType2 = if (callMayFailPerSe) {
                MkType2.result(returnType, WKT.bubbleType2).get()
            } else {
                returnType
            },
            hasThisFormal = false,
            requiredInputTypes = listOf(WKT.intType2),
            typeFormals = typeFormals,
        ),
    )

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult =
        when (interpMode) {
            InterpMode.Partial -> NotYet
            InterpMode.Full -> void
        }
}

private val bCallee = Value(PlaceholderFunction("b", false, WKT.booleanType2))
private val bbCallee = Value(PlaceholderFunction("bb", true, WKT.booleanType2))
private val fCallee = Value(PlaceholderFunction("f", false, WKT.intType2))
private val ffCallee = Value(PlaceholderFunction("ff", true, WKT.intType2))
private val vCallee = Value(PlaceholderFunction("v", false, WKT.voidType2))
private val vvCallee = Value(PlaceholderFunction("vv", true, WKT.voidType2))

internal fun dumpStructureEmbedding(
    block: BlockTree,
    cf: ControlFlow,
): String {
    val referents = mutableMapOf<Int, CollectedTokens>()
    val voidReferents = mutableSetOf<Int>()
    for (childIndex in blockPartialEvaluationOrder(block)) {
        val child = block.child(childIndex)
        if (child is ValueLeaf && child.content == void) {
            voidReferents.add(childIndex)
        }
        referents[childIndex] = CollectedTokens.collect {
            child.toPseudoCode(it)
        }
    }

    return dumpStructureEmbedding(cf, referents, voidReferents)
}

private fun dumpStructureEmbedding(
    wovenControlFlow: ControlFlow,
    referents: Map<Int, CollectedTokens>,
    voidReferents: Set<Int>,
) = toStringViaTokenSink(singleLine = false) { sink ->
    fun renderRef(ref: BlockChildReference?) {
        sink.emit(OutputToken("[[", OutputTokenType.Punctuation))
        referents[ref?.index]?.replay(sink, skipLastLinebreak = true)
        sink.emit(OutputToken("]]", OutputTokenType.Punctuation))
    }
    fun render(cf: ControlFlow) {
        when (cf) {
            is ControlFlow.If -> {
                sink.emit(OutToks.ifWord)
                sink.emit(OutToks.leftParen)
                renderRef(cf.condition)
                sink.emit(OutToks.rightParen)
                render(cf.thenClause)
                val elseClause = cf.elseClause
                if (!elseClause.isEmptyBlock()) {
                    sink.emit(OutToks.elseWord)
                    if (elseClause.stmts.size == 1 && elseClause.stmts[0] is ControlFlow.If) {
                        render(elseClause.stmts[0])
                    } else {
                        render(elseClause)
                    }
                }
            }
            is ControlFlow.Loop -> {
                val label = cf.label
                if (label != null) {
                    sink.emit(label.toToken(inOperatorPosition = false))
                    sink.emit(OutToks.colon)
                }
                val isDoWhile = cf.checkPosition == LeftOrRight.Right
                sink.emit(
                    if (isDoWhile) {
                        OutToks.doWord
                    } else {
                        OutToks.forWord
                    },
                )
                if (!isDoWhile || !cf.increment.isEmptyBlock()) {
                    sink.emit(OutToks.leftParen)
                    sink.emit(OutToks.semi)
                    if (!isDoWhile) {
                        renderRef(cf.ref)
                    }
                    sink.emit(OutToks.semi)
                    if (!cf.increment.isEmptyBlock()) {
                        render(cf.increment)
                    }
                    sink.emit(OutToks.rightParen)
                }
                render(cf.body)
                if (isDoWhile) {
                    sink.emit(OutToks.whileWord)
                    sink.emit(OutToks.leftParen)
                    renderRef(cf.condition)
                    sink.emit(OutToks.rightParen)
                    sink.emit(OutToks.semi)
                }
            }
            is ControlFlow.Jump -> {
                val keyword = when (cf) {
                    is ControlFlow.Break -> OutToks.breakWord
                    is ControlFlow.Continue -> OutToks.continueWord
                }
                sink.emit(keyword)
                val targetToken = when (val target = cf.target) {
                    is DefaultJumpSpecifier -> null
                    is NamedJumpSpecifier ->
                        target.label.toToken(inOperatorPosition = false)
                    is UnresolvedJumpSpecifier -> {
                        val nameTok = ParsedName(target.symbol.text)
                            .toToken(inOperatorPosition = false)
                        nameTok.copy(text = "\\${nameTok.text}")
                    }
                }
                targetToken?.let { sink.emit(it) }
                sink.emit(OutToks.semi)
            }
            is ControlFlow.Labeled -> {
                sink.emit(cf.breakLabel.toToken(inOperatorPosition = false))
                val continueLabel = cf.continueLabel
                if (continueLabel != null) {
                    sink.emit(OutToks.amp)
                    sink.emit(continueLabel.toToken(inOperatorPosition = false))
                }
                sink.emit(OutToks.colon)
                sink.emit(OutToks.doWord)
                render(cf.stmts)
            }
            is ControlFlow.OrElse -> {
                render(cf.orClause)
                sink.emit(OutToks.orElseWord)
                render(cf.elseClause)
            }
            is ControlFlow.Stmt -> renderRef(cf.ref)
            is ControlFlow.StmtBlock -> {
                val wrap = cf.parent != null
                if (wrap) {
                    sink.emit(OutToks.leftCurly)
                }
                for (stmt in cf.stmts) {
                    if (stmt.ref?.index?.let { it in voidReferents } == true) {
                        continue
                    }
                    render(stmt)
                    if (stmt is ControlFlow.Stmt) {
                        sink.emit(OutToks.semi)
                    }
                }
                if (wrap) {
                    sink.emit(OutToks.rightCurly)
                }
            }
        }
    }
    render(wovenControlFlow)
}

@Suppress("TestFunctionName") // Match planting style
private fun Planting.CallB(args: Planting.() -> Unit) =
    plantCallWithTypeInfo(bCallee) { args() }

@Suppress("TestFunctionName")
private fun Planting.CallBB(args: Planting.() -> Unit) =
    plantCallWithTypeInfo(bbCallee) { args() }

@Suppress("TestFunctionName")
private fun Planting.CallF(args: Planting.() -> Unit) =
    plantCallWithTypeInfo(fCallee) { args() }

@Suppress("TestFunctionName")
private fun Planting.CallFF(args: Planting.() -> Unit) =
    plantCallWithTypeInfo(ffCallee) { args() }

@Suppress("TestFunctionName")
private fun Planting.CallV(args: Planting.() -> Unit) =
    plantCallWithTypeInfo(vCallee) { args() }

@Suppress("TestFunctionName")
private fun Planting.CallVV(args: Planting.() -> Unit) =
    plantCallWithTypeInfo(vvCallee) { args() }

@Suppress("TestFunctionName")
private fun Planting.V(n: Int) = V(Value(n, TInt), WKT.intType)

private fun reblock(block: BlockTree) {
    val flow = structureBlock(block)

    TreeVisit.startingAt(block)
        .forEachContinuing {
            if (it is BlockTree && it != block) {
                reblock(it)
            }
        }
        .visitPostOrder()

    fun splitOut(stmtBlock: ControlFlow.StmtBlock) {
        val stmts = stmtBlock.stmts
        for (i in stmts.indices) {
            val stmt = stmts[i]
            if (stmt is ControlFlow.Stmt) { continue }
            // Recreate the stmt inside a new BlockTree
            val indicesToTransfer = buildSet {
                fun enumerateIndices(cf: ControlFlow) {
                    val refIndex = cf.ref?.index
                    if (refIndex != null) {
                        add(refIndex)
                    }
                    for (c in cf.clauses) {
                        enumerateIndices(c)
                    }
                }
                enumerateIndices(stmt)
            }
            val indicesSorted = indicesToTransfer.sorted()
            val newBlock = block.document.treeFarm.grow(stmtBlock.pos) {
                Block(flowMaker = { StructuredFlow(ControlFlow.StmtBlock.wrap(stmt.deepCopy())) }) {
                    if (indicesSorted.isNotEmpty()) {
                        val max = indicesSorted.last()
                        for (i in 0..max) {
                            if (i !in indicesSorted) {
                                V(void)
                            } else {
                                Replant(freeTarget(block.edge(i)))
                            }
                        }
                    }
                }
            }
            val newBlockIndex = block.size
            block.add(newBlock)
            val replacement = ControlFlow.Stmt(BlockChildReference(newBlockIndex, newBlock.pos))
            stmtBlock.withMutableStmtList { mutStmtList ->
                mutStmtList[i] = replacement
            }
            reblock(newBlock)
        }
    }

    val stmtBlock = flow.controlFlow
    if (stmtBlock.stmts.size == 1) {
        val loneStmt = stmtBlock.stmts[0]
        for (clause in loneStmt.clauses) {
            if (clause is ControlFlow.StmtBlock) {
                splitOut(clause)
            }
        }
    } else {
        splitOut(stmtBlock)
    }
}
