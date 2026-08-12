package lang.temper.value

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStructure
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.testCodeLocation
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.name.PseudoCodeNameRenumberer
import kotlin.test.Test
import kotlin.test.assertEquals

class TreeFarmTest {
    @Test
    fun growTheFlow() {
        val doc = Document(TestDocumentContext())
        val loc = doc.nameMaker.namingContext.loc

        // Some names
        val b = doc.nameMaker.unusedSourceName(ParsedName("b"))
        val x = doc.nameMaker.unusedSourceName(ParsedName("x"))
        val f = doc.nameMaker.unusedSourceName(ParsedName("f"))
        val g = doc.nameMaker.unusedSourceName(ParsedName("g"))
        val h = doc.nameMaker.unusedSourceName(ParsedName("h"))
        val lbl = doc.nameMaker.unusedSourceName(ParsedName("lbl"))

        val grown = doc.treeFarm.grow(Position(loc, 0, 10)) {
            Block {
                If(
                    { Rn(b) },
                    thn = {
                        While({ Call { Rn(f) } }, label = lbl) {
                            If(
                                { Call(vNotFn) { Rn(b) } },
                                thn = {
                                    Break(label = lbl)
                                },
                                els = {},
                            )
                            Call {
                                Rn(g)
                                Rn(x)
                            }
                        }
                    },
                    els = {
                        Call {
                            Rn(h)
                            Rn(x)
                        }
                    },
                )
                Call {
                    Rn(h)
                    Call(BuiltinFuns.plusIntIntFn) {
                        Rn(x)
                        V(Value(1, TInt))
                    }
                }
            }
        }

        val renumberer = PseudoCodeNameRenumberer.newStringRenumberer()
        assertEquals(
            """
                |(Block
                |  (stmt-block
                |    (if (R b__0)
                |      (stmt-block
                |        (while@lbl__5 (Call (R f__2))
                |          (stmt-block
                |            (if (Call (V nym`!`) (R b__0))
                |              (stmt-block
                |## Break label matches the @ after the while.
                |                (break lbl__5))
                |              (stmt-block))
                |            (Call (R g__3) (R x__1)))
                |## This empty statement block is the "iterator step" instructions.
                |          (stmt-block)))
                |      (stmt-block
                |        (Call (R h__4) (R x__1))))
                |    (Call
                |      (R h__4)
                |      (Call (V nym`+`) (R x__1) (V 1))))
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow().let {
                renumberer(it)
            },
            renumberer(grown.toLispy(multiline = true)),
        )
    }

    @Test
    fun positionsInferredForControlFlow() {
        val doc = Document(TestDocumentContext())
        val loc = doc.nameMaker.namingContext.loc

        // Here's a block that is all control flow.  No embedded expressions.
        // It should propagate position information the same way trees do.
        val grown = doc.treeFarm.grow(Position(loc, 5, 20)) {
            Block {
                Do(label = doc.nameMaker.unusedTemporaryName("goto")) {}
            }
        }

        assertStructure(
            """
                |{
                |  type: "Block",
                |  left: 5,
                |  right: 20,
                |  children: [
                |    {
                |      kind: "labeled",
                |      label: "goto#0",
                |      clauses: [
                |        {
                |          kind: "stmt-block",
                |          clauses: [],
                |          left: 5,
                |          right: 20,
                |        },
                |      ],
                |      left: 5,
                |      right: 20,
                |    },
                |  ],
                |  content: "StructuredFlow",
                |}
            """.trimMargin(),
            grown,
        )
    }

    @Test
    fun misnestedControlFlow() {
        val doc = Document(TestDocumentContext())

        val grown = doc.treeFarm.grow(Position(testCodeLocation, 0, 0)) {
            Block {
                Call {
                    Rn(BuiltinName("f"))
                    // Putting this here risks problems where the `If`
                    // contributes to the Block before the Call is added
                    // instead of nesting properly within the Call as an arg.
                    If(
                        { Rn(BuiltinName("b")) },
                        thn = { V(Value(1, TInt)) },
                        els = { V(Value(2, TInt)) },
                    )
                }
            }
        }

        assertEquals(
            "f({if (b) {1} else {2}})",
            grown.toPseudoCode(),
        )
    }
}
