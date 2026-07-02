package lang.temper.be.cpp

import lang.temper.format.CodeFormatter
import lang.temper.format.toStringViaTokenSink
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden tests for the C++ AST formatter. The expected strings assert the formatter's *raw* token
 * stream, so some spacing looks unidiomatic for hand-written C++ (e.g. `}else {`, `if(x)`,
 * `[ = , & x]`). That is expected: generated code is passed through clang-format downstream, so the
 * formatter only needs to emit syntactically faithful tokens, not pretty ones.
 */
class CppGrammarTest {

    private fun assertCode(expected: String, ast: Cpp.Tree) {
        val actual = toStringViaTokenSink(
            formattingHints = CppFormattingHints.getInstance(),
            singleLine = false,
        ) {
            CodeFormatter(it).format(ast)
        }
        assertEquals(expected.trimEnd(), actual.trimEnd())
    }

    private fun assertCodeSingleLine(expected: String, ast: Cpp.Tree) {
        val actual = toStringViaTokenSink(
            formattingHints = CppFormattingHints.getInstance(),
            singleLine = true,
        ) {
            CodeFormatter(it).format(ast)
        }
        assertEquals(expected.trimEnd(), actual.trimEnd())
    }

    private val cpp = CppBuilder(CppNames())

    // ==================== Names ====================

    @Test
    fun singleName() {
        assertCodeSingleLine("foo", cpp.singleName("foo"))
    }

    @Test
    fun scopedName() {
        assertCodeSingleLine(
            "std::string",
            cpp.name("std", "string"),
        )
    }

    @Test
    fun deepScopedName() {
        assertCodeSingleLine(
            "temper::core::Int",
            cpp.name("temper", "core", "Int"),
        )
    }

    // ==================== Types ====================

    @Test
    fun simpleType() {
        assertCodeSingleLine("int", cpp.type("int"))
    }

    @Test
    fun templateType() {
        assertCodeSingleLine(
            "std::shared_ptr<int>",
            cpp.template(cpp.name("std", "shared_ptr"), cpp.type("int")),
        )
    }

    @Test
    fun nestedTemplateType() {
        assertCodeSingleLine(
            "std::vector<std::shared_ptr<Foo>>",
            cpp.template(
                cpp.name("std", "vector"),
                cpp.template(cpp.name("std", "shared_ptr"), cpp.singleName("Foo")),
            ),
        )
    }

    @Test
    fun pointerType() {
        assertCodeSingleLine(
            "int *",
            cpp.ptr(cpp.type("int")),
        )
    }

    // ==================== Literals ====================

    @Test
    fun intLiteral() {
        assertCodeSingleLine("42", cpp.literal(42))
    }

    @Test
    fun boolLiteral() {
        assertCodeSingleLine("true", cpp.literal(true))
        assertCodeSingleLine("false", cpp.literal(false))
    }

    @Test
    fun stringLiteral() {
        assertCodeSingleLine("\"hello\"", cpp.literal("hello"))
    }

    @Test
    fun rawLiteral() {
        assertCodeSingleLine("nullptr", cpp.literal(cpp.raw("nullptr")))
    }

    // ==================== Expressions ====================

    @Test
    fun callExpr() {
        assertCodeSingleLine(
            "foo()",
            cpp.callExpr(cpp.singleName("foo"), emptyList()),
        )
    }

    @Test
    fun callExprWithArgs() {
        assertCodeSingleLine(
            "add(1, 2)",
            cpp.callExpr(
                cpp.singleName("add"),
                listOf(cpp.literal(1), cpp.literal(2)),
            ),
        )
    }

    @Test
    fun binaryExpr() {
        assertCodeSingleLine(
            "a + b",
            cpp.op("+", cpp.singleName("a"), cpp.singleName("b")),
        )
    }

    @Test
    fun comparisonExpr() {
        assertCodeSingleLine(
            "x != nullptr",
            cpp.op("!=", listOf(cpp.singleName("x"), cpp.literal(cpp.raw("nullptr")))),
        )
    }

    @Test
    fun memberExpr() {
        assertCodeSingleLine(
            "obj.field",
            cpp.memberExpr(cpp.singleName("obj"), cpp.singleName("field")),
        )
    }

    @Test
    fun arrowExpr() {
        assertCodeSingleLine(
            "ptr->field",
            cpp.op("->", cpp.singleName("ptr"), cpp.singleName("field")),
        )
    }

    @Test
    fun castExpr() {
        assertCodeSingleLine(
            "(int)x",
            cpp.cast(cpp.type("int"), cpp.singleName("x")),
        )
    }

    @Test
    fun templateCallExpr() {
        assertCodeSingleLine(
            "std::make_shared<Foo>()",
            cpp.callExpr(
                cpp.template(cpp.name("std", "make_shared"), cpp.singleName("Foo")),
                emptyList(),
            ),
        )
    }

    // ==================== Statements ====================

    @Test
    fun exprStmt() {
        assertCode(
            "foo();",
            cpp.exprStmt(cpp.callExpr(cpp.singleName("foo"), emptyList())),
        )
    }

    @Test
    fun returnStmt() {
        assertCode("return;", cpp.returnStmt(null))
    }

    @Test
    fun returnStmtWithExpr() {
        assertCode("return 42;", cpp.returnStmt(cpp.literal(42)))
    }

    @Test
    fun varDef() {
        assertCode(
            "int x = 42;",
            cpp.varDef(cpp.type("int"), cpp.singleName("x"), cpp.literal(42)),
        )
    }

    @Test
    fun ifStmt() {
        assertCode(
            "if(x) {}",
            cpp.ifStmt(
                cpp.singleName("x"),
                cpp.blockStmt(emptyList()),
            ),
        )
    }

    @Test
    fun ifStmtWithBody() {
        assertCode(
            """
            if(x) {
              return 1;
            }
            """.trimIndent(),
            cpp.ifStmt(
                cpp.singleName("x"),
                cpp.blockStmt(listOf(cpp.returnStmt(cpp.literal(1)))),
            ),
        )
    }

    @Test
    fun ifElseStmt() {
        assertCode(
            """
            if(x) {
              return 1;
            }else {
              return 2;
            }
            """.trimIndent(),
            cpp.ifStmt(
                cpp.singleName("x"),
                cpp.blockStmt(listOf(cpp.returnStmt(cpp.literal(1)))),
                cpp.blockStmt(listOf(cpp.returnStmt(cpp.literal(2)))),
            ),
        )
    }

    @Test
    fun whileStmt() {
        assertCode(
            "while(cond) {}",
            cpp.whileStmt(
                cpp.singleName("cond"),
                cpp.blockStmt(emptyList()),
            ),
        )
    }

    @Test
    fun blockStmt() {
        assertCode(
            """
            {
              return 1;
            }
            """.trimIndent(),
            cpp.blockStmt(listOf(cpp.returnStmt(cpp.literal(1)))),
        )
    }

    @Test
    fun comment() {
        assertCode(
            "//hello",
            cpp.comment("hello"),
        )
    }

    // ==================== Declarations ====================

    @Test
    fun funcParam() {
        assertCodeSingleLine(
            "int x",
            cpp.funcParam(cpp.type("int"), cpp.singleName("x")),
        )
    }

    @Test
    fun structDef() {
        assertCode(
            "struct Foo {};",
            cpp.structDef(
                cpp.singleName("Foo"),
                emptyList(),
            ),
        )
    }

    @Test
    fun structDefWithField() {
        assertCode(
            """
            struct Foo {
              int x;
            };
            """.trimIndent(),
            cpp.structDef(
                cpp.singleName("Foo"),
                listOf(cpp.structField(cpp.type("int"), cpp.singleName("x"))),
            ),
        )
    }

    @Test
    fun derivedStructDef() {
        assertCode(
            """
            struct Dog : public Animal {};
            """.trimIndent(),
            cpp.derivedStructDef(
                cpp.singleName("Dog"),
                listOf(cpp.baseSpec(virtual = false, cpp.singleName("Animal"))),
                emptyList(),
            ),
        )
    }

    @Test
    fun derivedStructDefWithFields() {
        assertCode(
            """
            struct Dog : public Animal {
              int age;
            };
            """.trimIndent(),
            cpp.derivedStructDef(
                cpp.singleName("Dog"),
                listOf(cpp.baseSpec(virtual = false, cpp.singleName("Animal"))),
                listOf(cpp.structField(cpp.type("int"), cpp.singleName("age"))),
            ),
        )
    }

    @Test
    fun derivedStructDefVirtualBase() {
        assertCode(
            "struct Cat : virtual public temper::core::AnyValueBase {};",
            cpp.derivedStructDef(
                cpp.singleName("Cat"),
                listOf(cpp.baseSpec(virtual = true, cpp.name("temper", "core", "AnyValueBase"))),
                emptyList(),
            ),
        )
    }

    @Test
    fun derivedStructDefMultipleBases() {
        assertCode(
            "struct C : public A, public B {};",
            cpp.derivedStructDef(
                cpp.singleName("C"),
                listOf(
                    cpp.baseSpec(virtual = false, cpp.singleName("A")),
                    cpp.baseSpec(virtual = false, cpp.singleName("B")),
                ),
                emptyList(),
            ),
        )
    }

    @Test
    fun templateStructDef() {
        assertCode(
            """
            template<typename T> struct Box {
              T value;
            };
            """.trimIndent(),
            cpp.templateStructDef(
                listOf(cpp.funcParam(cpp.type("typename"), cpp.singleName("T"))),
                cpp.structDef(
                    cpp.singleName("Box"),
                    listOf(cpp.structField(cpp.type("T"), cpp.singleName("value"))),
                ),
            ),
        )
    }

    @Test
    fun templateFuncDef() {
        assertCode(
            """
            template<typename T> T identity(T x) {
              return x;
            }
            """.trimIndent(),
            cpp.templateFuncDef(
                listOf(cpp.funcParam(cpp.type("typename"), cpp.singleName("T"))),
                cpp.funcDef(
                    cpp.type("T"),
                    cpp.singleName("identity"),
                    listOf(cpp.funcParam(cpp.type("T"), cpp.singleName("x"))),
                    cpp.blockStmt(listOf(cpp.returnStmt(cpp.singleName("x")))),
                ),
            ),
        )
    }

    @Test
    fun templateFuncDecl() {
        assertCode(
            "template<typename T> T identity(T);",
            cpp.templateFuncDecl(
                listOf(cpp.funcParam(cpp.type("typename"), cpp.singleName("T"))),
                cpp.funcDecl(
                    cpp.type("T"),
                    null,
                    cpp.singleName("identity"),
                    listOf(cpp.type("T")),
                ),
            ),
        )
    }

    @Test
    fun funcDeclWithStaticMod() {
        assertCode(
            "static int foo(int);",
            cpp.funcDecl(
                Cpp.DefMod.Static,
                cpp.type("int"),
                cpp.singleName("foo"),
                listOf(cpp.type("int")),
            ),
        )
    }

    @Test
    fun funcDefWithStaticMod() {
        assertCode(
            """
            static void init() {
              return;
            }
            """.trimIndent(),
            cpp.funcDef(
                Cpp.DefMod.Static,
                cpp.type("void"),
                cpp.singleName("init"),
                emptyList(),
                cpp.blockStmt(listOf(cpp.returnStmt(null))),
            ),
        )
    }

    @Test
    fun namespace() {
        assertCode(
            """
            namespace foo {
              int x = 1;
            }
            """.trimIndent(),
            cpp.namespace(
                cpp.singleName("foo"),
                listOf(cpp.varDef(cpp.type("int"), cpp.singleName("x"), cpp.literal(1))),
            ),
        )
    }

    @Test
    fun nestedNamespace() {
        assertCode(
            """
            namespace outer {
              namespace inner {
                int x = 1;
              }
            }
            """.trimIndent(),
            cpp.namespace(
                cpp.singleName("outer"),
                listOf(
                    cpp.namespace(
                        cpp.singleName("inner"),
                        listOf(cpp.varDef(cpp.type("int"), cpp.singleName("x"), cpp.literal(1))),
                    ),
                ),
            ),
        )
    }

    // ==================== Additional Statements ====================

    @Test
    fun throwStmt() {
        assertCode(
            "throw 1;",
            cpp.throwStmt(cpp.literal(1)),
        )
    }

    @Test
    fun tryCatchStmt() {
        assertCode(
            """
            try {
              return 1;
            }catch(const temper::core::TemperBubble & ) {
              return 2;
            }
            """.trimIndent(),
            cpp.tryCatch(
                cpp.blockStmt(listOf(cpp.returnStmt(cpp.literal(1)))),
                cpp.blockStmt(listOf(cpp.returnStmt(cpp.literal(2)))),
            ),
        )
    }

    @Test
    fun breakStmt() {
        assertCode("break;", cpp.breakStmt())
    }

    @Test
    fun switchStmt() {
        assertCode(
            """
            switch(s) {
              case 0 : case 1 : {
                return 1;
              }
              default : {
                return 2;
              }
            }
            """.trimIndent(),
            cpp.switchStmt(
                cpp.singleName("s"),
                listOf(
                    cpp.switchCase(
                        listOf(cpp.caseLabel(cpp.literal(0)), cpp.caseLabel(cpp.literal(1))),
                        cpp.blockStmt(listOf(cpp.returnStmt(cpp.literal(1)))),
                    ),
                ),
                cpp.blockStmt(listOf(cpp.returnStmt(cpp.literal(2)))),
            ),
        )
    }

    @Test
    fun lambdaExpr() {
        assertCode(
            """
            [ = , & x](int32_t y)->int32_t {
              return 1;
            }
            """.trimIndent(),
            cpp.lambda(
                captures = listOf(cpp.lambdaCapture(cpp.singleName("x"))),
                params = listOf(cpp.funcParam(cpp.type("int32_t"), cpp.singleName("y"))),
                mutable = false,
                ret = cpp.type("int32_t"),
                body = cpp.blockStmt(listOf(cpp.returnStmt(cpp.literal(1)))),
            ),
        )
    }

    @Test
    fun lambdaExprMutableNoCaptures() {
        assertCode(
            "[ = ]()mutable->void {}",
            cpp.lambda(
                captures = emptyList(),
                params = emptyList(),
                mutable = true,
                ret = cpp.type("void"),
                body = cpp.blockStmt(emptyList()),
            ),
        )
    }

    @Test
    fun labelAndGoto() {
        assertCode(
            "goto done;",
            cpp.gotoStmt(cpp.singleName("done")),
        )
    }

    @Test
    fun indexExpr() {
        assertCodeSingleLine(
            "arr[0]",
            cpp.indexExpr(cpp.singleName("arr"), cpp.literal(0)),
        )
    }

    @Test
    fun unaryExpr() {
        assertCodeSingleLine(
            "! x",
            cpp.unaryExpr(cpp.unaryOp("!"), cpp.singleName("x")),
        )
    }

    @Test
    fun thisExpr() {
        assertCodeSingleLine(
            "this",
            cpp.thisExpr(),
        )
    }

    @Test
    fun structDefWithMethod() {
        assertCode(
            """
            struct Foo {
              int getValue(int);
            };
            """.trimIndent(),
            cpp.structDef(
                cpp.singleName("Foo"),
                listOf(
                    cpp.funcDecl(
                        cpp.type("int"),
                        null,
                        cpp.singleName("getValue"),
                        listOf(cpp.type("int")),
                    ),
                ),
            ),
        )
    }
}
