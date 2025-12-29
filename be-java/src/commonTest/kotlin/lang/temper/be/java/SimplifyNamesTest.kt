package lang.temper.be.java

import org.junit.jupiter.api.Test
import lang.temper.be.java.Java as J
import lang.temper.log.unknownPos as p0

class SimplifyNamesTest : AstHelper() {
    @Test
    fun testLocalClass() {
        val ast =
            J.TopLevelClassDeclaration(
                p0,
                programMeta = J.ProgramMeta(p0),
                packageStatement = J.PackageStatement(p0, qualIdent("foo.bar")),
                classDef = J.ClassDeclaration(
                    p0,
                    name = ident("Top"),
                    body = listOf(
                        J.MethodDeclaration(
                            p0,
                            result = J.VoidType(p0),
                            name = ident("method"),
                            parameters = listOf(),
                            body = J.BlockStatement(
                                p0,
                                listOf(
                                    J.LocalClassDeclaration(
                                        p0,
                                        name = ident("Local"),
                                        body = listOf(
                                            J.FieldDeclaration(
                                                p0,
                                                type = J.PrimitiveType(p0, type = Primitive.JavaInt),
                                                variable = ident("val"),
                                                initializer = null,
                                            ),
                                        ),
                                    ),
                                    J.LocalVariableDeclaration(
                                        p0,
                                        type = type("Local"),
                                        name = ident("local"),
                                        expr = J.InstanceCreationExpr(
                                            p0,
                                            type = type("Local"),
                                            args = listOf(),
                                        ),
                                    ),
                                    J.AssignmentExpr(
                                        p0,
                                        left = name("local.val"),
                                        operator = J.Operator(p0, JavaOperator.Assign),
                                        right = JavaOperator.Addition.infix(
                                            name("local.val"),
                                            J.IntegerLiteral(p0, 3),
                                        ),
                                    ).exprStatement(),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        assertCode(
            """
            package foo.bar;
            class Top {
                void method() {
                    class Local {
                        int val;
                    }
                    Local local = new Local();
                    local.val = local.val + 3;
                }
            }
            """.trimIndent(),
            ast,
        )
        simplifyNames(ast)
        assertCode(
            """
            package foo.bar;
            class Top {
                void method() {
                    class Local {
                        int val;
                    }
                    Local local = new Local();
                    local.val = local.val + 3;
                }
            }
            """.trimIndent(),
            ast,
        )
    }

    @Test
    fun testStaticInvocation() {
        val ast =
            J.TopLevelClassDeclaration(
                p0,
                programMeta = J.ProgramMeta(p0),
                packageStatement = J.PackageStatement(p0, qualIdent("foo.bar")),
                classDef = J.ClassDeclaration(
                    p0,
                    name = ident("Top"),
                    body = listOf(
                        J.MethodDeclaration(
                            p0,
                            result = J.VoidType(p0),
                            mods = J.MethodModifiers(p0, modStatic = J.ModStatic.Static),
                            name = ident("method"),
                            parameters = listOf(),
                            body = J.BlockStatement(
                                p0,
                                listOf(
                                    J.StaticMethodInvocationExpr(
                                        p0,
                                        type = qualIdent("foo.bar.Top"),
                                        method = ident("method"),
                                        args = listOf(),
                                    ).exprStatement(),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        assertCode(
            """
            package foo.bar;
            class Top {
                static void method() {
                    foo.bar.Top.method();
                }
            }
            """.trimIndent(),
            ast,
        )
        simplifyNames(ast)
        assertCode(
            """
            package foo.bar;
            class Top {
                static void method() {
                    Top.method();
                }
            }
            """.trimIndent(),
            ast,
        )
    }
}
