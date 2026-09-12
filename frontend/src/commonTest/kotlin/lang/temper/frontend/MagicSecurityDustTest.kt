package lang.temper.frontend

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStringsEqual
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.name.PseudoCodeNameRenumberer
import lang.temper.type.WellKnownTypes
import lang.temper.type.plantCallWithTypeInfo
import lang.temper.type.plantTypedCallee
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.value.BlockTree
import lang.temper.value.BubbleFn
import lang.temper.value.Document
import lang.temper.value.TInt
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.returnParsedName
import lang.temper.value.toPseudoCode
import lang.temper.value.typeFromSignature
import lang.temper.value.vLabelSymbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MagicSecurityDustTest {
    private val pos = Position(testCodeLocation, 0, 0)
    private val intToVoid = typeFromSignature(
        Signature2(
            returnType2 = WellKnownTypes.voidType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(WellKnownTypes.intType2),
        ),
    )

    private fun runSprinkleTest(
        test: (doc: Document, sprinkler: MagicSecurityDust) -> Unit,
    ) {
        val doc = Document(TestDocumentContext(testModuleName))
        val sprinkler = MagicSecurityDust()

        test(doc, sprinkler)
    }

    private fun Document.name(nameText: String) = nameMaker.unusedSourceName(ParsedName(nameText))

    // a = f((b + c) / d)
    private fun arithmeticExpr(doc: Document) = doc.treeFarm.grow(pos) {
        Call(BuiltinFuns.vSetLocalFn) { // =
            Ln(doc.name("a")) // a
            Call {
                Rn(BuiltinName("f"), intToVoid)
                plantCallWithTypeInfo(BuiltinFuns.divIntIntFn) { // /
                    plantCallWithTypeInfo(BuiltinFuns.plusIntIntFn) { // +
                        Rn(doc.name("b")) // b
                        Rn(doc.name("c")) // c
                    }
                    Rn(doc.name("d")) // d
                }
            }
        }
    }

    @Test
    fun arithmeticThatMayFailInLinearBlock() = runSprinkleTest { doc, sprinkler ->
        val root = doc.treeFarm.grow(pos) {
            Block {
                Replant(arithmeticExpr(doc))
            }
        }

        sprinkler.sprinkle(root)

        assertPseudoCode(
            """
                |a__0 = f(do {
                |    (b__0 + c__0) / d__0
                |})
                |
            """.trimMargin(),
            root,
        )
    }

    @Test
    fun arithmeticThatMayFailInStructuredBlock() = runSprinkleTest { doc, sprinkler ->
        val root = doc.treeFarm.grow(pos) {
            Block {
                Replant(arithmeticExpr(doc))
            }
        }
        structureBlock(root)

        sprinkler.sprinkle(root)

        assertPseudoCode(
            """
                |a__0 = f(do {
                |    (b__0 + c__0) / d__0
                |})
                |
            """.trimMargin(),
            root,
        )
    }

    @Test
    fun arithmeticThatMayFailInFunctionBody() = runSprinkleTest { doc, sprinkler ->
        val fnBody = doc.treeFarm.grow(pos) {
            Block {
                V(vLabelSymbol)
                Ln(doc.name("fn"))
                Replant(arithmeticExpr(doc))
            }
        }
        structureBlock(fnBody)

        val fn = doc.treeFarm.grow(pos) {
            Fn { Replant(fnBody) }
        }

        assertNotNull(fn.parts) // Simple well-formedness check

        val root = BlockTree.wrap(fn)
        structureBlock(root)

        sprinkler.sprinkle(root)

        assertPseudoCode(
            """
                |fn {
                |  fn__0: do {
                |    a__0 = f(do {
                |## The `/` is bedazzled, but the `+` is not.
                |        (b__0 + c__0) / d__0
                |    })
                |  }
                |}
                |
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
            root,
        )
    }

    @Test
    fun bubblyCallsInConditions() = runSprinkleTest { doc, _ ->
        val intToBoolOrBubble = typeFromSignature(
            Signature2(
                MkType2(WellKnownTypes.resultTypeDefinition)
                    .actuals(
                        listOf(
                            WellKnownTypes.booleanType2,
                            WellKnownTypes.bubbleType2,
                        ),
                    ).get(),
                false,
                listOf(WellKnownTypes.intType2),
            ),
        )
        val block = doc.treeFarm.grow(pos) {
            Block {
                If(
                    {
                        Call {
                            Rn(ParsedName("f"), type = intToBoolOrBubble)
                            V(Value(1, TInt))
                        }
                    },
                    thn = {
                        Call {
                            Rn(ParsedName("f"), type = intToBoolOrBubble)
                            V(Value(2, TInt))
                        }
                    },
                    els = {
                        Call {
                            Rn(ParsedName("f"), type = intToBoolOrBubble)
                            V(Value(3, TInt))
                        }
                    },
                )
            }
        }
        MagicSecurityDust().sprinkle(block)

        assertEquals(
            """
                |if (do {
                |    f(1)
                |}) {
                |  f(2)
                |} else {
                |  f(3)
                |}
                |
            """.trimMargin(),
            block.toPseudoCode(singleLine = false),
        )
    }

    @Test
    fun kindOfIdempotent() = runSprinkleTest { doc, sprinkler ->
        // Sprinkling shouldn't sprinkle on calls that have already been
        // turned into top level statements.
        // This is necessary for us to sprinkle during the type stage and then again later to
        // capture failures for any constructs inserted later.
        // Examples of later inserted calls include implied assignments to variables that hold
        // function results.
        val nameMaker = doc.nameMaker
        val x = nameMaker.unusedSourceName(ParsedName("x"))
        val y = nameMaker.unusedSourceName(ParsedName("y"))
        val z = nameMaker.unusedSourceName(ParsedName("z"))
        val n = nameMaker.unusedSourceName(ParsedName("n"))
        val f = nameMaker.unusedSourceName(ParsedName("f"))

        val root = doc.treeFarm.grow(pos) {
            Block {
                // First, a call that's sprinkled
                Call(BuiltinFuns.vSetLocalFn) {
                    Ln(n)
                    plantCallWithTypeInfo(BuiltinFuns.divIntIntFn) {
                        Rn(x)
                        Rn(y)
                    }
                }
                // Then a call that is not at the top level.
                Call {
                    Rn(f, type = intToVoid)
                    plantCallWithTypeInfo(BuiltinFuns.divIntIntFn) {
                        Rn(x)
                        Rn(y)
                    }
                }
                // Then a nested call that doesn't need sprinkles.
                Call {
                    Rn(f, type = intToVoid)
                    plantCallWithTypeInfo(BuiltinFuns.divIntIntSafeFn) {
                        Rn(x)
                        V(Value(2, TInt))
                    }
                }
                // Then a call that's sprinkled
                plantCallWithTypeInfo(BuiltinFuns.divIntIntFn) {
                    Rn(x)
                    // with a sub-call that's not
                    plantCallWithTypeInfo(BuiltinFuns.divIntIntFn) {
                        Rn(y)
                        Rn(z)
                    }
                }
                // Then a call that's sprinkled
                plantCallWithTypeInfo(BuiltinFuns.divIntIntFn) {
                    Rn(x)
                    // with a sub-call that doesn't need sprinkles
                    plantCallWithTypeInfo(BuiltinFuns.divIntIntSafeFn) {
                        Rn(y)
                        V(Value(2, TInt))
                    }
                }
            }
        }
        structureBlock(root)

        sprinkler.sprinkle(root)

        assertPseudoCode(
            """
                |## This one does not need sprinkles because the only
                |## call between it and the root is the assignment.
                |n__0 = x__0 / y__0;
                |## The operand gets sprinkled because it's passed to f.
                |f__0(do {
                |    x__0 / y__0
                |});
                |## A safe use of `/` so no sprinkles needed.
                |f__0(x__0 / 2);
                |## Nested operation gets a block.
                |x__0 / do {
                |  y__0 / z__0
                |};
                |## This nested operation is the safe variant of division, so
                |## it doesn't get a block.
                |x__0 / y__0 / 2
                |
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
            root,
        )
    }

    @Test
    fun returnOfBubbleDoesNotAssignBubble() = runSprinkleTest { doc, sprinkler ->
        val returnName = doc.nameMaker.unusedSourceName(returnParsedName)
        val root = doc.treeFarm.grow(pos) {
            Block {
                Call(BuiltinFuns.vSetLocalFn) {
                    Ln(returnName)
                    plantCallWithTypeInfo(BubbleFn) {}
                }
            }
        }
        structureBlock(root)

        sprinkler.sprinkle(root)

        assertPseudoCode(
            """
            |do {
            |  bubble();
            |  return__0 = panic()
            |}
            """.trimMargin(),
            root,
        )
    }

    @Test
    fun returnOfParameterizedBubbleJustBubbles() = runSprinkleTest { doc, sprinkler ->
        val returnName = doc.nameMaker.unusedSourceName(returnParsedName)
        val root = doc.treeFarm.grow(pos) {
            Block {
                Call(BuiltinFuns.vSetLocalFn) {
                    Ln(returnName, WellKnownTypes.intType)
                    Call {
                        Call(BuiltinFuns.vAngleFn) {
                            plantTypedCallee(BubbleFn)
                            V(Types.vInt)
                        }
                    }
                }
            }
        }
        structureBlock(root)

        sprinkler.sprinkle(root)

        assertPseudoCode(
            """
                |do {
                |  bubble<Int32>();
                |  return__0 = panic<Int32>()
                |}
            """.trimMargin(),
            root,
        )
    }

    private fun assertPseudoCode(
        want: String,
        tree: Tree,
    ) {
        val got = tree.toPseudoCode(singleLine = false)
        val wantNormalized = PseudoCodeNameRenumberer.newStringRenumberer()(want).trimEnd()
        val gotNormalized = PseudoCodeNameRenumberer.newStringRenumberer()(got).trimEnd()

        assertStringsEqual(
            wantNormalized,
            gotNormalized,
        )
    }
}
