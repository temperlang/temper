package lang.temper.be.csharp

import lang.temper.format.CodeFormatter
import lang.temper.format.toStringViaTokenSink
import kotlin.test.Test
import kotlin.test.assertEquals
import lang.temper.log.unknownPos as p0

class CSharpGrammarTest {
    private fun assertCode(expected: String, ast: CSharp.Tree) {
        val actual = toStringViaTokenSink(formattingHints = CSharpFormattingHints, singleLine = false) {
            CodeFormatter(it).format(ast)
        }
        assertEquals(expected.trimEnd(), actual.trimEnd())
    }

    @Test
    fun hello() {
        assertCode(
            """
                |using Sys = System;
                |[assembly: InternalsVisibleTo("Something")]
                |namespace Here.There
                |{
                |    [Example, Another]
                |    [YetAnother]
                |    class Hi
                |    {
                |        static void Main()
                |        {
                |            Sys::Console.WriteLine("Hi!");
                |        }
                |    }
                |}
            """.trimMargin(),
            CSharp.CompilationUnit(
                p0,
                usings = listOf(
                    CSharp.UsingNamespaceDirective(
                        p0,
                        alias = "Sys".toIdentifier(p0),
                        ids = listOf("System".toIdentifier(p0)),
                    ),
                ),
                attributes = listOf(
                    CSharp.AttributeSection(
                        p0,
                        target = "assembly".toIdentifier(p0),
                        attributes = listOf(
                            CSharp.Attribute(
                                p0,
                                name = "InternalsVisibleTo".toIdentifier(p0),
                                args = listOf(CSharp.StringLiteral(p0, "Something")),
                            ),
                        ),
                    ),
                ),
                decls = listOf(
                    CSharp.NamespaceDecl(
                        p0,
                        names = listOf("Here", "There").map { it.toIdentifier(p0) },
                        decls = listOf(
                            CSharp.TypeDecl(
                                p0,
                                id = "Hi".toIdentifier(p0),
                                attributes = listOf(
                                    CSharp.AttributeSection(
                                        p0,
                                        attributes = listOf(
                                            CSharp.Attribute(p0, name = "Example".toIdentifier(p0)),
                                            CSharp.Attribute(p0, name = "Another".toIdentifier(p0)),
                                        ),
                                    ),
                                    CSharp.AttributeSection(
                                        p0,
                                        attributes = listOf(
                                            CSharp.Attribute(p0, name = "YetAnother".toIdentifier(p0)),
                                        ),
                                    ),
                                ),
                                mods = CSharp.TypeModifiers(
                                    p0,
                                    modAccess = CSharp.ModAccess.Internal,
                                    modTypeKind = CSharp.ModTypeKind.Class,
                                ),
                                members = listOf(
                                    CSharp.MethodDecl(
                                        p0,
                                        id = "Main".toIdentifier(p0),
                                        result = StandardNames.keyVoid.toType(p0),
                                        mods = CSharp.MethodModifiers(
                                            p0,
                                            modAccess = CSharp.ModAccess.Private,
                                            modStatic = CSharp.ModStatic.Static,
                                        ),
                                        parameters = listOf(),
                                        body = CSharp.BlockStatement(
                                            p0,
                                            statements = listOf(
                                                CSharp.ExpressionStatement(
                                                    p0,
                                                    expr = CSharp.InvocationExpression(
                                                        p0,
                                                        expr = CSharp.MemberAccess(
                                                            p0,
                                                            expr = CSharp.UnboundType(
                                                                CSharp.QualTypeName(
                                                                    p0,
                                                                    namespaceAlias = "Sys".toIdentifier(p0),
                                                                    id = listOf("Console".toIdentifier(p0)),
                                                                ),
                                                            ),
                                                            id = "WriteLine".toIdentifier(p0),
                                                        ),
                                                        args = listOf(CSharp.StringLiteral(p0, "Hi!")),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
