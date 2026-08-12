package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.LeftOrRight
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStructure
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.testCodeLocation
import lang.temper.frontend.dumpStructureEmbedding
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.ControlFlow
import lang.temper.value.Document
import lang.temper.value.LinearFlow
import lang.temper.value.Planting
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.StructuredFlow
import lang.temper.value.TBoolean
import lang.temper.value.TInt
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.toPseudoCode
import lang.temper.value.void
import kotlin.test.Test

class UnsetTerminalExpressionsTest {
    private val pos = Position(testCodeLocation, 0, 0)
    private val outputName = BuiltinName("return_for_test")

    // {}
    @Test
    fun emptyBlockTest() = assertTerminals(
        """
        |{
        |  terminalExpressions: [],
        |  existingAssignments: [],
        |  missingTerminators: [""],
        |}
        """.trimMargin(),
    ) {
        emptyBlock()
    }

    // void
    @Test
    fun justVoid() = assertTerminals(
        """
        |{
        |  terminalExpressions: ["void"],
        |  existingAssignments: [],
        |  missingTerminators: []
        |}
        """.trimMargin(),
    ) { growAtom ->
        ControlFlow.StmtBlock(
            pos,
            listOf(
                ControlFlow.Stmt(growAtom { V(void) }),
            ),
        )
    }

    // let x;
    @Test
    fun declarationsAreNotTerminal() = assertTerminals(
        """
        |{
        |  terminalExpressions: [],
        |  existingAssignments: [],
        |  missingTerminators: ["[[ let x ]];"],
        |}
        """.trimMargin(),
    ) { growAtom ->
        oneStmt(
            growAtom {
                Decl(ParsedName("x")) {}
            },
        )
    }

    // false; let x;
    @Test
    fun declarationsMaskTerminals() = assertTerminals(
        """
        |{
        |  terminalExpressions: [],
        |  existingAssignments: [],
        |  missingTerminators: [
        |    ```
        |    {
        |      [[ false ]];
        |      [[ let x ]];
        |    }
        |    ```,
        |  ],
        |}
        """.trimMargin(),
    ) { growAtom ->
        ControlFlow.StmtBlock(
            pos,
            listOf(
                ControlFlow.StmtBlock(
                    pos,
                    listOf(
                        ControlFlow.Stmt(growAtom { V(TBoolean.valueFalse) }),
                        ControlFlow.Stmt(
                            growAtom {
                                Decl(ParsedName("x")) {}
                            },
                        ),
                    ),
                ),
            ),
        )
    }

    // if (c) { x; y } else { z }
    @Test
    fun twoWayBranch() = assertTerminals(
        """
        |{
        |  terminalExpressions: ["y", "z"],
        |}
        """.trimMargin(),
    ) { growAtom ->
        ControlFlow.StmtBlock(
            pos,
            listOf(
                ControlFlow.If(
                    pos,
                    condition = growAtom { Rn(ParsedName("c")) },
                    thenClause = ControlFlow.StmtBlock(
                        pos,
                        listOf(
                            ControlFlow.Stmt(growAtom { Rn(ParsedName("x")) }),
                            ControlFlow.Stmt(growAtom { Rn(ParsedName("y")) }),
                        ),
                    ),
                    elseClause = oneStmt(growAtom { Rn(ParsedName("z")) }),
                ),
            ),
        )
    }

    // if (c) {                    // Set on both branches
    //     return_for_test = 0
    // } else {
    //     return_for_test = 1
    // }
    // if (d) { y } else { z }     // So neither is terminal
    @Test
    fun twoWayBranchAfterAssignment() = assertTerminals(
        """
        |{
        |  terminalExpressions: [],
        |  existingAssignments: [
        |    "return_for_test = 0",
        |    "return_for_test = 1",
        |  ],
        |}
        """.trimMargin(),
    ) { growAtom ->
        ControlFlow.StmtBlock(
            pos,
            listOf(
                ControlFlow.If(
                    pos,
                    condition = growAtom { Rn(ParsedName("c")) },
                    thenClause = oneStmt(
                        growAtom {
                            Call(BuiltinFuns.setLocalFn) {
                                Ln(outputName)
                                V(Value(0, TInt))
                            }
                        },
                    ),
                    elseClause = oneStmt(
                        growAtom {
                            Call(BuiltinFuns.setLocalFn) {
                                Ln(outputName)
                                V(Value(1, TInt))
                            }
                        },
                    ),
                ),
                ControlFlow.If(
                    pos,
                    condition = growAtom { Rn(ParsedName("d")) },
                    thenClause = oneStmt(growAtom { Rn(ParsedName("y")) }),
                    elseClause = oneStmt(growAtom { Rn(ParsedName("z")) }),
                ),
            ),
        )
    }

    // if (c) { return_for_test = a } else { b }
    @Test
    fun twoWayBranchOneAssigns() = assertTerminals(
        """
        |{
        |  terminalExpressions: ["b"],
        |  existingAssignments: ["return_for_test = a"],
        |}
        """.trimMargin(),
    ) { growAtom ->
        ControlFlow.StmtBlock(
            pos,
            listOf(
                ControlFlow.If(
                    pos,
                    condition = growAtom { Rn(ParsedName("c")) },
                    thenClause = oneStmt(
                        growAtom {
                            Call(BuiltinFuns.setLocalFn) {
                                Ln(outputName)
                                Rn(ParsedName("a"))
                            }
                        },
                    ),
                    elseClause = oneStmt(growAtom { Rn(ParsedName("b")) }),
                ),
            ),
        )
    }

    // while (true) { f() }
    @Test
    fun noExit() = assertTerminals(
        """
        |{
        |  terminalExpressions: [],
        |  existingAssignments: [],
        |  missingTerminators: [
        |    ```
        |    for (;
        |      [[ true ]];
        |    ) {
        |      [[ f() ]];
        |    }
        |    ```,
        |  ],
        |}
        """.trimMargin(),
    ) { growAtom ->
        ControlFlow.StmtBlock(
            pos,
            listOf(
                ControlFlow.Loop(
                    pos,
                    label = null,
                    checkPosition = LeftOrRight.Left,
                    condition = growAtom { V(TBoolean.valueTrue) },
                    increment = emptyBlock(),
                    body = oneStmt(
                        growAtom {
                            Call {
                                Rn(ParsedName("f"))
                            }
                        },
                    ),
                ),
            ),
        )
    }

    // return_for_test = void; while (true) { f() }
    @Test
    fun noExitAfterSet() = assertTerminals(
        """
        |{
        |  terminalExpressions: [],
        |  existingAssignments: [
        |    "return_for_test = void"
        |  ],
        |}
        """.trimMargin(),
    ) { growAtom ->
        ControlFlow.StmtBlock(
            pos,
            listOf(
                ControlFlow.Stmt(
                    growAtom {
                        Call(BuiltinFuns.setLocalFn) {
                            Ln(outputName)
                            V(void)
                        }
                    },
                ),
                ControlFlow.Loop(
                    pos,
                    label = null,
                    checkPosition = LeftOrRight.Left,
                    condition = growAtom { V(TBoolean.valueTrue) },
                    increment = emptyBlock(),
                    body = oneStmt(
                        growAtom {
                            Call {
                                Rn(ParsedName("f"))
                            }
                        },
                    ),
                ),
            ),
        )
    }

    // if (c) {
    //   return_for_test = 0;
    // }
    // if (d) {
    //   42
    // }
    // // This example is odd.  There is an error because there is no value that can be treated
    // // as the result in all cases since (c) and (d) are not obviously disjoint.
    // // It's not obvious what should be done here, except that there is a need for an error message.
    // // This test merely documents current behavior.
    @Test
    fun incompleteAssignMaybeClobberedOccasionally() = assertTerminals(
        """
        |{
        |  terminalExpressions: [
        |## 42 is not a terminal expression because it follows the assignment below.
        |  ],
        |  existingAssignments: [
        |    "return_for_test = 0"
        |  ],
        |  missingTerminators: [
        |## This empty block is the implicit `else` clause from `if (d)`.
        |    "{}"
        |  ],
        |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) { growAtom ->
        ControlFlow.StmtBlock(
            pos,
            listOf(
                ControlFlow.If(
                    pos,
                    condition = growAtom { Rn(ParsedName("c")) },
                    thenClause = oneStmt(
                        growAtom {
                            Call(BuiltinFuns.setLocalFn) {
                                Ln(outputName)
                                V(Value(0, TInt))
                            }
                        },
                    ),
                    elseClause = emptyBlock(),
                ),
                ControlFlow.If(
                    pos,
                    condition = growAtom { Rn(ParsedName("d")) },
                    thenClause = oneStmt(growAtom { V(Value(42, TInt)) }),
                    elseClause = emptyBlock(),
                ),
            ),
        )
    }

    private fun assertTerminals(
        want: String,
        fillBlock: (
            (Planting.() -> UnpositionedTreeTemplate<*>) -> BlockChildReference,
        ) -> ControlFlow,
    ) {
        val docContext = TestDocumentContext()
        val doc = Document(docContext)
        val block = BlockTree(doc, pos, emptyList(), LinearFlow)
        val controlFlow = fillBlock { makeTemplate ->
            val tree = doc.treeFarm.grow(pos) { makeTemplate() }
            val edgeIndex = block.size
            block.add(tree)
            BlockChildReference(edgeIndex, tree.pos)
        }
        block.replaceFlow(StructuredFlow(ControlFlow.StmtBlock.wrap(controlFlow)))

        val got = findUnsetTerminalExpressions(
            root = block,
            outputName = outputName,
        )

        assertStructure(
            want,
            JsonValueBuilder.build(emptyMap()) {
                obj {
                    key("terminalExpressions") {
                        arr {
                            got.unsetTerminalExpressionEdges.forEach {
                                value(it.target.toPseudoCode())
                            }
                        }
                    }
                    key("existingAssignments", isDefault = got.existingAssignments.isEmpty()) {
                        arr {
                            got.existingAssignments.forEach {
                                value(it.toPseudoCode())
                            }
                        }
                    }
                    key("missingTerminators", isDefault = got.blocksMissingTerminators.isEmpty()) {
                        arr {
                            got.blocksMissingTerminators.forEach { (tree, cf) ->
                                val description = if (cf == null) {
                                    tree.toPseudoCode(detail = PseudoCodeDetail(preserveOuterCurlies = true))
                                } else {
                                    dumpStructureEmbedding(tree, cf).trimEnd()
                                }
                                value(description)
                            }
                        }
                    }
                    key("terminalsNeedVar", isDefault = !got.terminalsNeedVar) {
                        value(got.terminalsNeedVar)
                    }
                }
            },
        )
    }

    private fun emptyBlock() = ControlFlow.StmtBlock(pos, emptyList())
    private fun oneStmt(ref: BlockChildReference) =
        ControlFlow.StmtBlock.wrap(
            ControlFlow.Stmt(ref),
        )
}
