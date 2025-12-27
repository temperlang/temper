package lang.temper.frontend

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStringsEqual
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.name.PseudoCodeNameRenumberer
import lang.temper.value.BlockTree
import lang.temper.value.BubbleFn
import lang.temper.value.Document
import lang.temper.value.Tree
import lang.temper.value.returnParsedName
import lang.temper.value.toPseudoCode
import lang.temper.value.vLabelSymbol
import kotlin.test.Test
import kotlin.test.assertNotNull

class MagicSecurityDustTest {
    private val pos = Position(testCodeLocation, 0, 0)

    private fun runSprinkleTest(
        test: (doc: Document, sprinkler: MagicSecurityDust) -> Unit,
    ) {
        val doc = Document(TestDocumentContext(testModuleName))
        val sprinkler = MagicSecurityDust()

        test(doc, sprinkler)
    }

    private fun Document.name(nameText: String) = nameMaker.unusedSourceName(ParsedName(nameText))

    // a = (b + c) / d
    private fun arithmeticExpr(doc: Document) = doc.treeFarm.grow(pos) {
        Call(BuiltinFuns.vSetLocalFn) { // =
            Ln(doc.name("a")) // a
            Call(BuiltinFuns.divFn) { // /
                Call(BuiltinFuns.vPlusFn) { // +
                    Rn(doc.name("b")) // b
                    Rn(doc.name("c")) // c
                }
                Rn(doc.name("d")) // d
            }
        }
    }

    @Test
    fun arithmeticThatMayFailLinearly() = runSprinkleTest { doc, sprinkler ->
        val root = doc.treeFarm.grow(pos) {
            Block {
                Replant(arithmeticExpr(doc))
            }
        }

        sprinkler.sprinkle(root)

        assertPseudoCode(
            """
            |var fail#0;
            |a__0 = hs(fail#0, (b__0 + c__0) / d__0)
            |
            """.trimMargin(),
            root,
        )
    }

    @Test
    fun arithmeticThatMayFailComplexly() = runSprinkleTest { doc, sprinkler ->
        val root = doc.treeFarm.grow(pos) {
            Block {
                Replant(arithmeticExpr(doc))
            }
        }
        structureBlock(root)

        sprinkler.sprinkle(root)

        assertPseudoCode(
            """
            |var fail#0;
            |a__0 = hs(fail#0, (b__0 + c__0) / d__0)
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
                |  var fail#0;
                |  fn__0: do {
                |    a__0 = hs(fail#0, (b__0 + c__0) / d__0)
                |  }
                |}
                |
            """.trimMargin(),
            root,
        )
    }

    @Test
    fun kindOfIdempotent() = runSprinkleTest { doc, sprinkler ->
        // Sprinkling shouldn't sprinkle on hs calls.
        // This is necessary for us to sprinkle during the type stage, and then again later to
        // capture failures for any constructs inserted later.
        // Examples of later inserted calls include implied assignments to variables that hold
        // function results.

        val nameMaker = doc.nameMaker
        val x = nameMaker.unusedSourceName(ParsedName("x"))
        val y = nameMaker.unusedSourceName(ParsedName("y"))
        val z = nameMaker.unusedSourceName(ParsedName("z"))
        val fail1 = nameMaker.unusedTemporaryName("fail")
        val fail2 = nameMaker.unusedTemporaryName("fail")

        val root = doc.treeFarm.grow(pos) {
            Block {
                // First something that's guarded
                Call(BuiltinFuns.handlerScope) {
                    Ln(fail1)
                    Call(BuiltinFuns.divFn) {
                        Rn(x)
                        Rn(y)
                    }
                }
                // Then a call that's not
                Call(BuiltinFuns.divFn) {
                    Rn(x)
                    Rn(y)
                }
                // Then a call that's guarded
                Call(BuiltinFuns.handlerScope) {
                    Ln(fail2)
                    Call(BuiltinFuns.divFn) {
                        Rn(x)
                        // with a sub-call that's not
                        Call(BuiltinFuns.divFn) {
                            Rn(y)
                            Rn(z)
                        }
                    }
                }
            }
        }
        structureBlock(root)

        sprinkler.sprinkle(root)

        assertPseudoCode(
            """
            var fail#5, fail#6;
            hs(fail#3, x__0 / y__1);
            hs(fail#5, x__0 / y__1);
            hs(fail#4, x__0 / hs(fail#6, y__1 / z__2))

            """.trimIndent(),
            root,
        )
    }

    @Test
    fun returnOfBubbleJustBubbles() = runSprinkleTest { doc, sprinkler ->
        val returnName = doc.nameMaker.unusedSourceName(returnParsedName)
        val root = doc.treeFarm.grow(pos) {
            Block {
                Call(BuiltinFuns.setLocalFn) {
                    Ln(returnName)
                    Call(BubbleFn) {}
                }
            }
        }
        structureBlock(root)

        sprinkler.sprinkle(root)

        assertPseudoCode(
            """
            |bubble()
            |
            """.trimMargin(),
            root,
        )
    }

    @Test
    fun returnOfParameterizedBubbleJustBubbles() = runSprinkleTest { doc, sprinkler ->
        val returnName = doc.nameMaker.unusedSourceName(returnParsedName)
        val root = doc.treeFarm.grow(pos) {
            Block {
                Call(BuiltinFuns.setLocalFn) {
                    Ln(returnName)
                    Call {
                        Call(BuiltinFuns.angleFn) {
                            V(BuiltinFuns.vBubble)
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
            |bubble<Int32>()
            |
            """.trimMargin(),
            root,
        )
    }

    private fun assertPseudoCode(
        want: String,
        tree: Tree,
    ) {
        val got = tree.toPseudoCode(singleLine = false)
        val wantNormalized = PseudoCodeNameRenumberer.newStringRenumberer()(want)
        val gotNormalized = PseudoCodeNameRenumberer.newStringRenumberer()(got)

        assertStringsEqual(
            wantNormalized,
            gotNormalized,
        )
    }
}
