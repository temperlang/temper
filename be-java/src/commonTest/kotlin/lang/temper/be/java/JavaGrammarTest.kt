package lang.temper.be.java

import kotlin.test.Test
import lang.temper.be.java.Java as J
import lang.temper.log.unknownPos as p0

class JavaGrammarTest : AstHelper() {

    @Test
    fun packageJava() {
        assertCode(
            """
            package foo.bar.qux;
            """.trimIndent(),
            J.PackageDeclaration(
                p0,
                programMeta = J.ProgramMeta(p0),
                packageStatement = J.PackageStatement(p0, qualIdent("foo.bar.qux")),
            ),
        )
    }

    @Test
    fun module1() {
        assertCode(
            """
            module foo.bar.mod {
            }
            """.trimIndent(),
            J.ModuleDeclaration(
                p0,
                programMeta = J.ProgramMeta(p0),
                moduleName = qualIdent("foo.bar.mod"),
                directives = listOf(),
            ),
        )
    }

    @Test
    fun module2() {
        assertCode(
            """
            open module myModule {
            }
            """.trimIndent(),
            J.ModuleDeclaration(
                p0,
                programMeta = J.ProgramMeta(p0),
                moduleName = qualIdent("myModule"),
                directives = listOf(),
                modOpen = J.ModOpen.Open,
            ),
        )
    }

    @Test
    fun module3() {
        assertCode(
            """
            module foo.bar.mod {
                uses foo.bar.SomeClass;
                opens foo.bar.pkg to some.other.mod, another.mod;
            }
            """.trimIndent(),
            J.ModuleDeclaration(
                p0,
                programMeta = J.ProgramMeta(p0),
                moduleName = qualIdent("foo.bar.mod"),
                directives = listOf(
                    J.UsesDirective(p0, qualIdent("foo.bar.SomeClass")),
                    J.OpensDirective(
                        p0,
                        qualIdent("foo.bar.pkg"),
                        listOf(qualIdent("some.other.mod"), qualIdent("another.mod")),
                    ),
                ),
            ),
        )
    }

    @Test
    fun module4() {
        assertCode(
            """
            module foo.bar.mod {
                requires other.mod;
                requires transitive another.mod;
                requires static that.mod;
            }
            """.trimIndent(),
            J.ModuleDeclaration(
                p0,
                programMeta = J.ProgramMeta(p0),
                moduleName = qualIdent("foo.bar.mod"),
                directives = listOf(
                    J.RequiresDirective(p0, moduleName = qualIdent("other.mod")),
                    J.RequiresDirective(
                        p0,
                        moduleName = qualIdent("another.mod"),
                        modTransitive = J.ModTransitive
                            .Transitive,
                    ),
                    J.RequiresDirective(p0, moduleName = qualIdent("that.mod"), modStatic = J.ModStatic.Static),
                ),
            ),
        )
    }

    @Test
    fun module5() {
        assertCode(
            """
            module foo.bar.mod {
                provides foo.bar.ThisClass;
                provides foo.bar.SomeInterface with foo.bar.OneImpl, foo.bar.AnotherImpl;
                exports foo.bar.pkg;
                exports foo.bar.special.pkg to qux.mod, barMod;
            }
            """.trimIndent(),
            J.ModuleDeclaration(
                p0,
                programMeta = J.ProgramMeta(p0),
                moduleName = qualIdent("foo.bar.mod"),
                directives = listOf(
                    J.ProvidesDirective(
                        p0,
                        typeName = qualIdent("foo.bar.ThisClass"),
                        withTypes = listOf(),
                    ),
                    J.ProvidesDirective(
                        p0,
                        typeName = qualIdent("foo.bar.SomeInterface"),
                        withTypes = listOf(
                            qualIdent("foo.bar.OneImpl"),
                            qualIdent("foo.bar.AnotherImpl"),
                        ),
                    ),
                    J.ExportsDirective(p0, packageName = qualIdent("foo.bar.pkg"), targetModules = listOf()),
                    J.ExportsDirective(
                        p0,
                        packageName = qualIdent("foo.bar.special.pkg"),
                        targetModules = listOf(
                            qualIdent("qux.mod"),
                            qualIdent("barMod"),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classFile1() {
        assertCode(
            """
            package foo.bar.pkg;
            import foo.bar.other.SomeClass;
            import foo.bar.other.pkg.*;
            class DeclaredClass {
            }
            """.trimIndent(),
            J.TopLevelClassDeclaration(
                p0,
                programMeta = J.ProgramMeta(p0),
                packageStatement = J.PackageStatement(p0, qualIdent("foo.bar.pkg")),
                imports = listOf(
                    J.ImportClassStatement(p0, qualifiedName = qualIdent("foo.bar.other.SomeClass")),
                    J.ImportClassOnDemand(p0, qualifiedName = qualIdent("foo.bar.other.pkg")),
                ),
                classDef = J.ClassDeclaration(
                    p0,
                    name = ident("DeclaredClass"),
                    body = listOf(),
                ),
            ),
        )
    }

    @Test
    fun classFile2() {
        assertCode(
            """
            package foo.bar.pkg;
            import static foo.bar.other.SomeClass.method;
            import static foo.bar.other.SomeClass.*;
            class DeclaredClass {
            }
            """.trimIndent(),
            J.TopLevelClassDeclaration(
                p0,
                programMeta = J.ProgramMeta(p0),
                packageStatement = J.PackageStatement(p0, qualIdent("foo.bar.pkg")),
                imports = listOf(
                    J.ImportStaticStatement(p0, qualifiedName = qualIdent("foo.bar.other.SomeClass.method")),
                    J.ImportStaticOnDemand(p0, qualifiedName = qualIdent("foo.bar.other.SomeClass")),
                ),
                classDef = J.ClassDeclaration(
                    p0,
                    name = ident("DeclaredClass"),
                    body = listOf(),
                ),
            ),
        )
    }

    @Test
    fun classDecl1() {
        assertCode(
            """
            class DeclaredClass {
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("DeclaredClass"),
                body = listOf(),
            ),
        )
    }

    @Test
    fun classDecl2() {
        assertCode(
            """
            @java.lang.Deprecated public final class DeclaredClass {
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                anns = listOf(javaLangDeprecated.toAnnotation(p0)),
                mods = J.ClassModifiers(p0, modAccess = J.ModAccess.Public, modFinal = J.ModSealedFinal.Final),
                name = ident("DeclaredClass"),
                body = listOf(),
            ),
        )
    }

    @Test
    fun classDecl3() {
        assertCode(
            """
            class DeclaredClass<P, Q> {
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("DeclaredClass"),
                params = J.TypeParameters(
                    p0,
                    listOf(
                        J.TypeParameter(p0, type = ident("P"), upperBounds = emptyList()),
                        J.TypeParameter(p0, type = ident("Q"), upperBounds = emptyList()),
                    ),
                ),
                body = listOf(),
            ),
        )
    }

    @Test
    fun classDecl4() {
        assertCode(
            """
            class DeclaredClass extends foo.bar.other.BaseClass {
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("DeclaredClass"),
                classExtends = J.ClassType(p0, type = qualIdent("foo.bar.other.BaseClass")),
                body = listOf(),
            ),
        )
    }

    @Test
    fun classDecl5() {
        assertCode(
            """
            sealed class DeclaredClass permits foo.bar.other.OtherDerivedClass, LocalDerivedClass {
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                mods = J.ClassModifiers(p0, modFinal = J.ModSealedFinal.Sealed),
                name = ident("DeclaredClass"),
                permits = listOf(
                    J.ClassType(p0, type = qualIdent("foo.bar.other.OtherDerivedClass")),
                    J.ClassType(p0, type = qualIdent("LocalDerivedClass")),
                ),
                body = listOf(),
            ),
        )
    }

    @Test
    fun classDecl6() {
        assertCode(
            """
            class DeclaredClass implements foo.bar.other.OtherInterface, LocalInterface {
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("DeclaredClass"),
                classImplements = listOf(
                    J.ClassType(p0, type = qualIdent("foo.bar.other.OtherInterface")),
                    J.ClassType(
                        p0,
                        type = qualIdent("LocalInterface"),
                    ),
                ),
                body = listOf(),
            ),
        )
    }

    @Test
    fun interfaceDecl1() {
        assertCode(
            """
            interface DeclaredInterface {
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                name = ident("DeclaredInterface"),
                body = listOf(),
            ),
        )
    }

    @Test
    fun interfaceDecl2() {
        assertCode(
            """
            @java.lang.Deprecated public non-sealed interface DeclaredInterface {
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                anns = listOf(javaLangDeprecated.toAnnotation(p0)),
                mods = J.InterfaceModifiers(p0, modAccess = J.ModAccess.Public, modSealed = J.ModSealed.NonSealed),
                name = ident("DeclaredInterface"),
                body = listOf(),
            ),
        )
    }

    @Test
    fun interfaceDecl3() {
        assertCode(
            """
            interface DeclaredInterface<P extends A & B, Q> {
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                name = ident("DeclaredInterface"),
                params = J.TypeParameters(
                    p0,
                    listOf(
                        J.TypeParameter(
                            p0,
                            type = ident("P"),
                            upperBounds = listOf(
                                ReferenceType(QualifiedName.knownSafe("A")).toTypeAst(p0),
                                ReferenceType(QualifiedName.knownSafe("B")).toTypeAst(p0),
                            ),
                        ),
                        J.TypeParameter(p0, type = ident("Q"), upperBounds = emptyList()),
                    ),
                ),
                body = listOf(),
            ),
        )
    }

    @Test
    fun interfaceDecl4() {
        assertCode(
            """
            interface DeclaredInterface extends foo.bar.SuperInterface {
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                name = ident("DeclaredInterface"),
                classExtends = listOf(J.ClassType(p0, type = qualIdent("foo.bar.SuperInterface"))),
                body = listOf(),
            ),
        )
    }

    @Test
    fun interfaceDecl5() {
        assertCode(
            """
            sealed interface DeclaredInterface permits foo.bar.Subclass, LocalSubClass {
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                mods = J.InterfaceModifiers(p0, modSealed = J.ModSealed.Sealed),
                name = ident("DeclaredInterface"),
                permits = listOf(
                    J.ClassType(p0, type = qualIdent("foo.bar.Subclass")),
                    J.ClassType(
                        p0,
                        type = qualIdent
                        ("LocalSubClass"),
                    ),
                ),
                body = listOf(),
            ),
        )
    }

    @Test
    fun classBody1() {
        assertCode(
            """
            class Example {
                float fieldName, anotherField = 42.42;
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.FieldDeclaration(
                        p0,
                        type = J.PrimitiveType(p0, Primitive.JavaFloat),
                        variables = listOf(
                            J.VariableDeclarator(p0, ident("fieldName"), initializer = null),
                            J.VariableDeclarator(
                                p0,
                                ident("anotherField"),
                                initializer = J.FloatingPointLiteral(p0, 42.42, J.Precision.Single),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classBody2() {
        assertCode(
            """
            class Example {
                @java.lang.Deprecated protected transient boolean complicatedFlag;
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.FieldDeclaration(
                        p0,
                        anns = listOf(javaLangDeprecated.toAnnotation(p0)),
                        mods = J.FieldModifiers(
                            p0,
                            modAccess = J.ModAccess.Protected,
                            modTransient = J.ModTransient.Transient,
                        ),
                        type = J.PrimitiveType(p0, Primitive.JavaBoolean),
                        variables = listOf(
                            J.VariableDeclarator(p0, ident("complicatedFlag"), initializer = null),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classBody3() {
        assertCode(
            """
            class Example {
                boolean doThing(Type first, foo.qux.Type second) {
                }
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.MethodDeclaration(
                        p0,
                        result = J.PrimitiveType(p0, Primitive.JavaBoolean),
                        name = ident("doThing"),
                        parameters = listOf(
                            J.FormalParameter(
                                p0,
                                name = ident("first"),
                                type = J.ClassType(p0, type = qualIdent("Type")),
                            ),
                            J.FormalParameter(
                                p0,
                                name = ident("second"),
                                type = J.ClassType(p0, type = qualIdent("foo.qux.Type")),
                            ),
                        ),
                        body = J.BlockStatement(p0, body = listOf()),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classBody4() {
        assertCode(
            """
            class Example {
                @java.lang.Deprecated static synchronized void doThing(int numberParam) {
                }
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.MethodDeclaration(
                        p0,
                        anns = listOf(javaLangDeprecated.toAnnotation(p0)),
                        mods = J.MethodModifiers(
                            p0,
                            modStatic = J.ModStatic.Static,
                            modSynchronized = J.ModSynchronized.Synchronized,
                        ),
                        result = J.VoidType(p0),
                        name = ident("doThing"),
                        parameters = listOf(
                            J.FormalParameter(
                                p0,
                                name = ident("numberParam"),
                                type = J.PrimitiveType(
                                    p0,
                                    Primitive
                                        .JavaInt,
                                ),
                            ),
                        ),
                        body = J.BlockStatement(p0, body = listOf()),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classBody5() {
        assertCode(
            """
            class Example {
                void doThing() throws SomeException, foo.bar.AnotherException {
                }
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.MethodDeclaration(
                        p0,
                        result = J.VoidType(p0),
                        name = ident("doThing"),
                        parameters = listOf(),
                        exceptionTypes = listOf(
                            J.ClassType(p0, type = qualIdent("SomeException")),
                            J.ClassType(p0, type = qualIdent("foo.bar.AnotherException")),
                        ),
                        body = J.BlockStatement(p0, body = listOf()),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classBody6() {
        assertCode(
            """
            class Example {
                Example() {
                }
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.ConstructorDeclaration(
                        p0,
                        name = ident("Example"),
                        parameters = listOf(),
                        body = J.BlockStatement(p0, body = listOf()),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classBody7() {
        assertCode(
            """
            class Example {
                public Example() throws SomeException {
                }
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.ConstructorDeclaration(
                        p0,
                        mods = J.ConstructorModifiers(p0, modAccess = J.ModAccess.Public),
                        name = ident("Example"),
                        parameters = listOf(),
                        body = J.BlockStatement(p0, body = listOf()),
                        exceptionTypes = listOf(
                            J.ClassType(p0, type = qualIdent("SomeException")),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classBody8() {
        assertCode(
            """
            class Example {
                /* class level comment */
                {
                    /* instance initializer */
                    throw exception;
                }
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.CommentLine(p0, "class level comment"),
                    J.Initializer(
                        p0,
                        body = J.BlockStatement(
                            p0,
                            body = listOf(
                                J.CommentLine(p0, "instance initializer"),
                                J.ThrowStatement(p0, expr = name("exception")),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classBody9() {
        assertCode(
            """
            class Example {
                class Nested {
                }
            }
            """.trimIndent(),
            J.ClassDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.ClassDeclaration(
                        p0,
                        name = ident("Nested"),
                        body = listOf(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classBody10() {
        assertCode(
            """
            {
                class Local {
                    int foo = 42;
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.LocalClassDeclaration(
                        p0,
                        name = ident("Local"),
                        body = listOf(
                            J.FieldDeclaration(
                                p0,
                                type = J.PrimitiveType(p0, Primitive.JavaInt),
                                variable = ident("foo"),
                                initializer = J.IntegerLiteral(
                                    p0,
                                    42,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun interfaceBody1() {
        assertCode(
            """
            interface Example {
                float fieldOne, fieldTwo = 42.42;
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.InterfaceFieldDeclaration(
                        p0,
                        type = J.PrimitiveType(p0, Primitive.JavaFloat),
                        variables = listOf(
                            J.VariableDeclarator(p0, ident("fieldOne"), initializer = null),
                            J.VariableDeclarator(
                                p0,
                                ident("fieldTwo"),
                                initializer = J.FloatingPointLiteral(
                                    p0,
                                    42.42,
                                    J.Precision.Single,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun interfaceBody2() {
        assertCode(
            """
            interface Example {
                @java.lang.Deprecated boolean fieldName;
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.InterfaceFieldDeclaration(
                        p0,
                        anns = listOf(javaLangDeprecated.toAnnotation(p0)),
                        type = J.PrimitiveType(p0, Primitive.JavaBoolean),
                        variables = listOf(
                            J.VariableDeclarator(p0, ident("fieldName"), initializer = null),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun interfaceBody3() {
        assertCode(
            """
            interface Example {
                boolean doThing(ParamType firstParam, foo.bar.ParamType secondParam);
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.InterfaceMethodDeclaration(
                        p0,
                        result = J.PrimitiveType(p0, Primitive.JavaBoolean),
                        name = ident("doThing"),
                        parameters = listOf(
                            J.FormalParameter(
                                p0,
                                name = ident("firstParam"),
                                type = J.ClassType(
                                    p0,
                                    type = qualIdent
                                    ("ParamType"),
                                ),
                            ),
                            J.FormalParameter(
                                p0,
                                name = ident("secondParam"),
                                type = J.ClassType(
                                    p0,
                                    type = qualIdent
                                    ("foo.bar.ParamType"),
                                ),
                            ),
                        ),
                        body = null,
                    ),
                ),
            ),
        )
    }

    @Test
    fun interfaceBody4() {
        assertCode(
            """
            interface Example {
                @java.lang.Deprecated void doThing(int paramName);
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.InterfaceMethodDeclaration(
                        p0,
                        anns = listOf(javaLangDeprecated.toAnnotation(p0)),
                        result = J.VoidType(p0),
                        name = ident("doThing"),
                        parameters = listOf(
                            J.FormalParameter(
                                p0,
                                name = ident("paramName"),
                                type = J.PrimitiveType(
                                    p0,
                                    Primitive
                                        .JavaInt,
                                ),
                            ),
                        ),
                        body = null,
                    ),
                ),
            ),
        )
    }

    @Test
    fun interfaceBody5() {
        assertCode(
            """
            interface Example {
                private static void doThing() throws SomeException, foo.bar.OtherException {
                }
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.InterfaceMethodDeclaration(
                        p0,
                        mods = J.ModInterfaceMethod.PrivateStatic,
                        result = J.VoidType(p0),
                        name = ident("doThing"),
                        parameters = listOf(),
                        exceptionTypes = listOf(
                            J.ClassType(p0, type = qualIdent("SomeException")),
                            J.ClassType(p0, type = qualIdent("foo.bar.OtherException")),
                        ),
                        body = J.BlockStatement(p0, body = listOf()),
                    ),
                ),
            ),
        )
    }

    @Test
    fun interfaceBody6() {
        assertCode(
            """
            interface Example {
                /* first comment */
                /* second comment */
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.CommentLine(p0, "first comment"),
                    J.CommentLine(p0, "second comment"),
                ),
            ),
        )
    }

    @Test
    fun interfaceBody7() {
        assertCode(
            """
            interface Example {
                class NestedClass {
                }
            }
            """.trimIndent(),
            J.InterfaceDeclaration(
                p0,
                name = ident("Example"),
                body = listOf(
                    J.ClassDeclaration(
                        p0,
                        name = ident("NestedClass"),
                        body = listOf(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun interfaceBody8() {
        assertCode(
            """
            {
                interface Local {
                    private static void doThing() throws SomeException, foo.bar.OtherException {
                    }
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.LocalInterfaceDeclaration(
                        p0,
                        name = ident("Local"),
                        body = listOf(
                            J.InterfaceMethodDeclaration(
                                p0,
                                mods = J.ModInterfaceMethod.PrivateStatic,
                                result = J.VoidType(p0),
                                name = ident("doThing"),
                                parameters = listOf(),
                                exceptionTypes = listOf(
                                    J.ClassType(p0, type = qualIdent("SomeException")),
                                    J.ClassType(p0, type = qualIdent("foo.bar.OtherException")),
                                ),
                                body = J.BlockStatement(p0, body = listOf()),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementVarDecl1() {
        assertCode(
            """
            {
                var localName = "expression";
                @temper.core.NonNull foo.bar.SomeClass otherLocalName;
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                listOf(
                    J.LocalVariableDeclaration(p0, type = null, name = ident("localName"), expr = string("expression")),
                    J.LocalVariableDeclaration(
                        p0,
                        type = J.ClassType(
                            p0,
                            anns = listOf(annNotNull.toAnnotation(p0)),
                            type = qualIdent("foo.bar.SomeClass"),
                        ),
                        name = ident("otherLocalName"),
                        expr = null,
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementEmptyExpression1() {
        assertCode(
            """
            {
                ;
                method();
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                listOf(
                    J.EmptyStatement(p0),
                    J.ExpressionStatement(p0, method()),
                ),
            ),
        )
    }

    @Test
    fun statementLabeled1() {
        assertCode(
            """
            {
                label: method();
                anotherLabel: nested: method();
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                listOf(
                    J.LabeledStatement(
                        p0,
                        label = ident("label"),
                        stmt = J.ExpressionStatement(
                            p0,
                            method(),
                        ),
                    ),
                    J.LabeledStatement(
                        p0,
                        label = ident("anotherLabel"),
                        stmt = J.LabeledStatement(
                            p0,
                            label = ident("nested"),
                            stmt = J.ExpressionStatement(
                                p0,
                                method(),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementIf1() {
        assertCode(
            """
            {
                if (test) {
                    ;
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                listOf(
                    J.IfStatement(
                        p0,
                        test = name("test"),
                        consequent = J.BlockStatement(
                            p0,
                            listOf(J.EmptyStatement(p0)),
                        ),
                        alternate = null,
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementIf2() {
        assertCode(
            """
            {
                if (test) {
                    ;
                } else {
                    ;
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                listOf(
                    J.IfStatement(
                        p0,
                        test = name("test"),
                        consequent = J.BlockStatement(
                            p0,
                            listOf(J.EmptyStatement(p0)),
                        ),
                        alternate = J.BlockStatement(
                            p0,
                            listOf(J.EmptyStatement(p0)),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementIf3() {
        assertCode(
            """
            {
                if (test) {
                    ;
                } else if (test2) {
                    ;
                } else {
                    ;
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                listOf(
                    J.IfStatement(
                        p0,
                        test = name("test"),
                        consequent = J.BlockStatement(
                            p0,
                            listOf(J.EmptyStatement(p0)),
                        ),
                        alternate = J.IfStatement(
                            p0,
                            test = name("test2"),
                            consequent = J.BlockStatement(
                                p0,
                                listOf(J.EmptyStatement(p0)),
                            ),
                            alternate = J.BlockStatement(
                                p0,
                                listOf(J.EmptyStatement(p0)),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementAssert1() {
        assertCode(
            """
            {
                assert test;
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.AssertStatement(p0, test = name("test")),
                ),
            ),
        )
    }

    @Test
    fun statementAssert2() {
        assertCode(
            """
            {
                assert test: "expression";
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.AssertStatement(p0, test = name("test"), msg = string("expression")),
                ),
            ),
        )
    }

    @Test
    fun statementSwitch1() {
        assertCode(
            """
            {
                switch (test) {
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.SwitchStatement(
                        p0,
                        selector = name("test"),
                        block = J.SwitchCaseBlock(p0, listOf()),
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementSwitch2() {
        // TODO correct case indentation and ensure newline
        assertCode(
            """
            {
                switch (test) {
                    case "expression":
                        method();
                    break;
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.SwitchStatement(
                        p0,
                        selector = name("test"),
                        block = J.SwitchCaseBlock(
                            p0,
                            listOf(
                                J.CaseStatement(
                                    p0,
                                    label = J.SwitchCaseLabel(
                                        p0,
                                        listOf(string("expression")),
                                    ),
                                    body = listOf(
                                        J.ExpressionStatement(p0, method()),
                                        J.BreakStatement(p0),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementSwitch3() {
        assertCode(
            """
            {
                switch (test) {
                    default:
                        method();
                    break;
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.SwitchStatement(
                        p0,
                        selector = name("test"),
                        block = J.SwitchCaseBlock(
                            p0,
                            listOf(
                                J.CaseStatement(
                                    p0,
                                    label = J.SwitchDefaultLabel(p0),
                                    body = listOf(
                                        J.ExpressionStatement(p0, method()),
                                        J.BreakStatement(p0),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementSwitch4() {
        assertCode(
            """
            {
                switch (test) {
                    case 42 -> "forty-two";
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.SwitchStatement(
                        p0,
                        selector = name("test"),
                        block = J.SwitchRuleBlock(
                            p0,
                            listOf(
                                J.ExpressionRuleStatement(
                                    p0,
                                    label = J.SwitchCaseLabel(
                                        p0,
                                        listOf(J.IntegerLiteral(p0, 42)),
                                    ),
                                    expr = string("forty-two"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementSwitch5() {
        assertCode(
            """
            {
                switch (test) {
                    case 42 -> {
                        yield "expression";
                    }
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.SwitchStatement(
                        p0,
                        selector = name("test"),
                        block = J.SwitchRuleBlock(
                            p0,
                            listOf(
                                J.BlockRuleStatement(
                                    p0,
                                    label = J.SwitchCaseLabel(
                                        p0,
                                        listOf(J.IntegerLiteral(p0, 42)),
                                    ),
                                    block = J.BlockStatement(
                                        p0,
                                        body = listOf(
                                            J.YieldStatement(p0, expr = string("expression")),
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

    @Test
    fun statementSwitch6() {
        assertCode(
            """
            {
                switch (test) {
                    default -> throw exception;
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.SwitchStatement(
                        p0,
                        selector = name("test"),
                        block = J.SwitchRuleBlock(
                            p0,
                            listOf(
                                J.ThrowRuleStatement(
                                    p0,
                                    label = J.SwitchDefaultLabel(p0),
                                    expr = name("exception"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementWhile1() {
        assertCode(
            """
            {
                while (test) {
                    continue;
                    continue containingLabel;
                }
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.WhileStatement(
                        p0,
                        test = name("test"),
                        body = J.BlockStatement(
                            p0,
                            listOf(
                                J.ContinueStatement(p0),
                                J.ContinueStatement(p0, target = ident("containingLabel")),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementDoWhile1() {
        assertCode(
            """
            {
                do {
                    method();
                } while (test);
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.DoStatement(
                        p0,
                        test = name("test"),
                        body = J.BlockStatement(
                            p0,
                            listOf(
                                J.ExpressionStatement(p0, method()),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun statementReturn1() {
        assertCode(
            """
            {
                return;
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.ReturnStatement(p0),
                ),
            ),
        )
    }

    @Test
    fun statementReturn2() {
        assertCode(
            """
            {
                return "expression";
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.ReturnStatement(p0, expr = string("expression")),
                ),
            ),
        )
    }

    @Test
    fun statementThrow1() {
        assertCode(
            """
            {
                throw exception;
            }
            """.trimIndent(),
            J.BlockStatement(
                p0,
                body = listOf(
                    J.ThrowStatement(p0, expr = name("exception")),
                ),
            ),
        )
    }

    @Test
    fun exprInteger1() {
        assertCode("method(47);", expr(J.IntegerLiteral(p0, 47)))
    }

    @Test
    fun exprInteger2() {
        assertCode("method(123456789123456L);", expr(J.IntegerLiteral(p0, 123_456_789_123_456L)))
    }

    @Test
    fun exprFloat1() {
        assertCode("method(47.87);", expr(J.FloatingPointLiteral(p0, 47.87, J.Precision.Single)))
    }

    @Test
    fun exprFloat2() {
        assertCode("method(47.87D);", expr(J.FloatingPointLiteral(p0, 47.87, J.Precision.Double)))
    }

    @Test
    fun exprChar1() {
        assertCode("method('x');", expr(J.CharacterLiteral(p0, 'x')))
    }

    @Test
    fun exprChar2() {
        assertCode("method('\\n');", expr(J.CharacterLiteral(p0, '\n')))
    }

    @Test
    fun exprString1() {
        assertCode(
            "method(\"demo\\nescapes \\u0000 and \\\" but not '\");",
            expr(string("demo\nescapes \u0000 and \" but not '")),
        )
    }

    @Test
    fun exprClassLit1() {
        assertCode(
            "method(byte.class);",
            expr(J.ClassLiteral(p0, J.PrimitiveType(p0, Primitive.JavaByte))),
        )
    }

    @Test
    fun exprFieldAccess() {
        assertCode(
            "method(name.field1.field2);",
            expr(
                J.FieldAccessExpr(
                    p0,
                    J.FieldAccessExpr(
                        p0,
                        name("name"),
                        ident("field1"),
                    ),
                    ident("field2"),
                ),
            ),
        )
    }

    @Test
    fun exprPrefixOperator() {
        assertCode(
            "++name;",
            expr(
                J.PrefixExpr(
                    p0,
                    J.Operator(p0, JavaOperator.PreIncrement),
                    name("name"),
                ),
            ),
        )
    }

    @Test
    fun exprPostfixOperator() {
        assertCode(
            "name--;",
            expr(
                J.PostfixExpr(
                    p0,
                    name("name"),
                    J.Operator(p0, JavaOperator.PostDecrement),
                ),
            ),
        )
    }

    @Test
    fun exprCast() {
        assertCode(
            "method((Foo) bar);",
            expr(
                J.CastExpr(
                    p0,
                    J.ClassType(p0, type = qualIdent("Foo")),
                    name("bar"),
                ),
            ),
        )
    }

    @Test
    fun exprInstanceOf() {
        assertCode(
            "method(bar instanceof Foo);",
            expr(
                J.InstanceofExpr(
                    p0,
                    name("bar"),
                    J.ClassType(p0, type = qualIdent("Foo")),
                ),
            ),
        )
    }

    private fun num(n: Long) = J.IntegerLiteral(p0, n)

    @Test
    fun exprInfixOperator() {
        assertCode(
            "method((1 + 2) * 3 * 4);",
            expr(
                J.InfixExpr(
                    p0,
                    J.InfixExpr(
                        p0,
                        J.InfixExpr(
                            p0,
                            num(1),
                            J.Operator(p0, JavaOperator.Addition),
                            num(2),
                        ),
                        J.Operator(p0, JavaOperator.Multiplication),
                        num(3),
                    ),
                    J.Operator(p0, JavaOperator.Multiplication),
                    num(4),
                ),
            ),
        )
    }

    @Test
    fun exprAssignment() {
        assertCode(
            "foo += bar = 'x';",
            expr(
                J.AssignmentExpr(
                    p0,
                    name("foo"),
                    J.Operator(p0, JavaOperator.PlusAssign),
                    J.AssignmentExpr(
                        p0,
                        name("bar"),
                        J.Operator(p0, JavaOperator.Assign),
                        J.CharacterLiteral(p0, 'x'),
                    ),
                ),
            ),
        )
    }

    @Test
    fun exprSwitch() {
        assertCode(
            """
            method("concat" + switch (test) {
                    case 42 -> "forty-two";
            });
            """.trimIndent(),
            expr(
                J.InfixExpr(
                    p0,
                    string("concat"),
                    J.Operator(p0, JavaOperator.Addition),
                    J.SwitchExpr(
                        p0,
                        selector = name("test"),
                        block = J.SwitchRuleBlock(
                            p0,
                            listOf(
                                J.ExpressionRuleStatement(
                                    p0,
                                    label = J.SwitchCaseLabel(
                                        p0,
                                        listOf(J.IntegerLiteral(p0, 42)),
                                    ),
                                    expr = string("forty-two"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun exprLambda1() {
        assertCode(
            "method(() -> \"foo\");",
            expr(
                J.LambdaExpr(
                    p0,
                    J.LambdaSimpleParams(p0, listOf()),
                    string("foo"),
                ),
            ),
        )
    }

    @Test
    fun exprLambda2() {
        assertCode(
            "method(() -> \"foo\");",
            expr(
                J.LambdaExpr(
                    p0,
                    J.LambdaComplexParams(p0, listOf()),
                    string("foo"),
                ),
            ),
        )
    }

    @Test
    fun exprLambda3() {
        assertCode(
            "method(foo -> \"foo\");",
            expr(
                J.LambdaExpr(
                    p0,
                    J.LambdaSimpleParams(p0, listOf(ident("foo"))),
                    string("foo"),
                ),
            ),
        )
    }

    @Test
    fun exprLambda4() {
        assertCode(
            "method((var foo) -> \"foo\");",
            expr(
                J.LambdaExpr(
                    p0,
                    J.LambdaComplexParams(
                        p0,
                        listOf(
                            J.LambdaParam(p0, type = null, name = ident("foo")),
                        ),
                    ),
                    string("foo"),
                ),
            ),
        )
    }
}
