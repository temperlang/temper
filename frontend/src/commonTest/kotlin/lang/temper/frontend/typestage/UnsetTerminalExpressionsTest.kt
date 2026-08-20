package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStructure
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.testCodeLocation
import lang.temper.frontend.dumpStructureEmbedding
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.value.BlockPlanting
import lang.temper.value.Document
import lang.temper.value.JumpLabel
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.TBoolean
import lang.temper.value.TInt
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
        |  missingTerminators: ["{}"],
        |}
        """.trimMargin(),
    ) {
        Do {}
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
    ) {
        V(void)
    }

    // let x;
    @Test
    fun declarationsAreNotTerminal() = assertTerminals(
        """
        |{
        |  terminalExpressions: [],
        |  existingAssignments: [],
        |  missingTerminators: ["{let x;}"],
        |}
        """.trimMargin(),
    ) {
        Decl(ParsedName("x")) {}
    }

    // false; let x;
    @Test
    fun declarationsMaskTerminals() = assertTerminals(
        """
        |{
        |  terminalExpressions: [],
        |  existingAssignments: [],
        |  missingTerminators: [
        |    "{false; let x;}"
        |  ],
        |}
        """.trimMargin(),
    ) {
        V(TBoolean.valueFalse)
        Decl(ParsedName("x")) {}
    }

    // if (c) { x; y } else { z }
    @Test
    fun twoWayBranch() = assertTerminals(
        """
        |{
        |  terminalExpressions: ["y", "z"],
        |}
        """.trimMargin(),
    ) {
        If(
            cond = { Rn(ParsedName("c")) },
            thn = {
                Rn(ParsedName("x"))
                Rn(ParsedName("y"))
            },
            els = {
                Rn(ParsedName("z"))
            },
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
    ) {
        If(
            cond = { Rn(ParsedName("c")) },
            thn = {
                Call(BuiltinFuns.setLocalFn) {
                    Ln(outputName)
                    V(Value(0, TInt))
                }
            },
            els = {
                Call(BuiltinFuns.setLocalFn) {
                    Ln(outputName)
                    V(Value(1, TInt))
                }
            },
        )
        If(
            cond = { Rn(ParsedName("d")) },
            thn = { Rn(ParsedName("y")) },
            els = { Rn(ParsedName("z")) },
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
    ) {
        If(
            cond = { Rn(ParsedName("c")) },
            thn = {
                Call(BuiltinFuns.setLocalFn) {
                    Ln(outputName)
                    Rn(ParsedName("a"))
                }
            },
            els = { Rn(ParsedName("b")) },
        )
    }

    // fn: {
    //    if (c) { return_for_test = a; break fn }
    //    b
    // }
    @Test
    fun oneBranchAssigns() = assertTerminals(
        """
        |{
        |  terminalExpressions: ["b"],
        |  existingAssignments: ["return_for_test = a"],
        |}
        """.trimMargin(),
    ) {
        val label: JumpLabel = nameMaker.unusedSourceName(ParsedName("fn"))
        Do(label = label) {
            If(
                cond = { Rn(ParsedName("c")) },
                thn = {
                    Call(BuiltinFuns.setLocalFn) {
                        Ln(outputName)
                        Rn(ParsedName("a"))
                    }
                    Break(label)
                },
                els = { Rn(ParsedName("b")) },
            )
        }
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
    ) {
        While(cond = { V(TBoolean.valueTrue) }) {
            Call {
                Rn(ParsedName("f"))
            }
        }
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
    ) {
        Call(BuiltinFuns.setLocalFn) {
            Ln(outputName)
            V(void)
        }
        While(cond = { V(TBoolean.valueTrue) }) {
            Call {
                Rn(ParsedName("f"))
            }
        }
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
        |## It does not always follow the existing assignment.
        |    "42"
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
    ) {
        If(
            cond = { Rn(ParsedName("c")) },
            thn = {
                Call(BuiltinFuns.setLocalFn) {
                    Ln(outputName)
                    V(Value(0, TInt))
                }
            },
            els = {},
        )
        If(
            cond = { Rn(ParsedName("d")) },
            thn = { V(Value(42, TInt)) },
            els = {},
        )
    }

    private fun assertTerminals(
        want: String,
        makeAst: BlockPlanting.() -> Unit,
    ) {
        val docContext = TestDocumentContext()
        val doc = Document(docContext)
        val block = doc.treeFarm.grow(pos) {
            Block {
                makeAst()
            }
        }

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
}
