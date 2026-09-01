package lang.temper.be.java

import lang.temper.common.testCodeLocation
import lang.temper.log.Position
import lang.temper.name.OutName
import kotlin.test.Test
import kotlin.test.assertEquals
import lang.temper.be.java.Java as J

private val p = Position(testCodeLocation, 0, 0)

class CodeAfterWouldBeDeadByJavaRulesTest {
    @Test
    fun aBreak() = assertDeadAfter(true) {
        J.BreakStatement(p, null)
    }

    @Test
    fun emptyBlock() = assertDeadAfter(false) {
        J.BlockStatement(p, listOf())
    }

    @Test
    fun blockReturns() = assertDeadAfter(true) {
        J.BlockStatement(
            p,
            listOf(
                J.ReturnStatement(p, null),
            ),
        )
    }

    @Test
    fun ifThenBranchLive() = assertDeadAfter(false) {
        J.IfStatement(
            p,
            J.NameExpr(J.Identifier(p, OutName("b", null))),
            J.BlockStatement(
                p,
                listOf(
                    J.ReturnStatement(p, null),
                ),
            ),
            J.BlockStatement(
                p,
                listOf(
                    J.EmptyStatement(p),
                ),
            ),
        )
    }

    @Test
    fun ifElseBranchLive() = assertDeadAfter(false) {
        J.IfStatement(
            p,
            J.NameExpr(J.Identifier(p, OutName("b", null))),
            J.BlockStatement(
                p,
                listOf(
                    J.EmptyStatement(p),
                ),
            ),
            J.BlockStatement(
                p,
                listOf(
                    J.ReturnStatement(p, null),
                ),
            ),
        )
    }

    @Test
    fun ifDead() = assertDeadAfter(true) {
        J.IfStatement(
            p,
            nameExpr("b"),
            J.BlockStatement(
                p,
                listOf(
                    J.EmptyStatement(p),
                    J.BreakStatement(p),
                ),
            ),
            J.BlockStatement(
                p,
                listOf(
                    J.ReturnStatement(p, null),
                ),
            ),
        )
    }

    @Test
    fun whileTrue() = assertDeadAfter(true) {
        J.WhileStatement(
            p,
            J.BooleanLiteral(p, true),
            J.BlockStatement(
                p,
                listOf(
                    randomStmt(),
                ),
            ),
        )
    }

    @Test
    fun whileTrueContainsBreak() = assertDeadAfter(false) {
        J.WhileStatement(
            p,
            J.BooleanLiteral(p, true),
            J.BlockStatement(
                p,
                listOf(
                    J.IfStatement(
                        p, nameExpr("x"),
                        J.BlockStatement(
                            p,
                            listOf(
                                randomStmt(),
                            ),
                        ),
                        J.BlockStatement(
                            p, listOf(J.BreakStatement(p, null)),
                        ),
                    ),
                    J.EmptyStatement(p),
                ),
            ),
        )
    }

    @Test
    fun whileTrueContainsBreakToLabel() = assertDeadAfter(false) {
        J.LabeledStatement(
            p,
            ident("lbl"),
            J.WhileStatement(
                p,
                J.BooleanLiteral(p, true),
                J.BlockStatement(
                    p,
                    listOf(
                        J.IfStatement(
                            p, nameExpr("x"),
                            J.BlockStatement(
                                p,
                                listOf(
                                    randomStmt(),
                                ),
                            ),
                            J.BlockStatement(
                                p, listOf(J.BreakStatement(p, ident("lbl"))),
                            ),
                        ),
                        J.EmptyStatement(p),
                    ),
                ),
            ),
        )
    }

    @Test
    fun whileTrueContainsBreakToFreeLabel() = assertDeadAfter(true) {
        J.LabeledStatement(
            p,
            ident("lbl"),
            J.WhileStatement(
                p,
                J.BooleanLiteral(p, true),
                J.BlockStatement(
                    p,
                    listOf(
                        J.IfStatement(
                            p, nameExpr("x"),
                            J.BlockStatement(
                                p,
                                listOf(
                                    randomStmt(),
                                ),
                            ),
                            J.BlockStatement(
                                p, listOf(J.BreakStatement(p, ident("otherLabel"))),
                            ),
                        ),
                        J.EmptyStatement(p),
                    ),
                ),
            ),
        )
    }

    private fun assertDeadAfter(want: Boolean, makeJava: () -> J.BlockLevelStatement) {
        val inp = makeJava()
        val isDeadAfter = codeAfterWouldBeDeadByJavaRules(inp, null)
        assertEquals(
            want,
            isDeadAfter,
            "$inp",
        )

        // Double check that passing in a do-nothing lambda for onToC does not change the result
        assertEquals(
            want,
            codeAfterWouldBeDeadByJavaRules(inp) {},
            "$inp",
        )
    }
}

private fun ident(nameText: String) =
    J.Identifier(p, OutName(nameText, null))

private fun nameExpr(nameText: String) = J.NameExpr(ident(nameText))

private fun randomStmt() = J.ExpressionStatement(
    p,
    J.InstanceCreationExpr(
        p,
        javaLangString.toClassType(p),
        null,
        listOf(),
    ),
)
