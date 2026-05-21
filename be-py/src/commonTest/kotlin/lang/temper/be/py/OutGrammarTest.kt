@file:lang.temper.common.Generated("make_tests.py")
@file:Suppress("UnderscoresInNumericLiterals", "SpellCheckingInspection")

package lang.temper.be.py

import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.log.filePath
import lang.temper.value.DependencyCategory
import kotlin.test.Test
import lang.temper.log.unknownPos as p0

/** Runs some simple tests of the correctness of py.out-grammar */
class OutGrammarTest {
    private fun pyProgram(pos: Position, body: List<Py.Stmt>): Py.Program = Py.Program(
        pos = pos,
        body = body,
        dependencyCategory = DependencyCategory.Production,
        genre = Genre.Library,
        outputPath = filePath("some.py"),
    )

    private fun astIntegerLiteral1() =
        pyProgram(l1c0, body = listOf(Py.ExprStmt(l1c0, value = Py.Num(l1c0, n = 7))))

    @Test
    fun testIntegerLiteral1Out() {
        val expected = "7\n"
        assertEqualCode(expected, astIntegerLiteral1())
    }

    @Test
    fun testIntegerLiteral1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astIntegerLiteral1())
    }

    @Test
    fun testIntegerLiteral1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astIntegerLiteral1())
    }
    private fun astIntegerLiteral2() =
        pyProgram(
            l1c0c2,
            body = listOf(
                Py.ExprStmt(
                    l1c0c2,
                    value = Py.UnaryExpr(l1c0c2, op = UnaryOpEnum.UnarySub.atom(p0), operand = Py.Num(l1c1, n = 3)),
                ),
            ),
        )

    @Test
    fun testIntegerLiteral2Out() {
        val expected = "-3\n"
        assertEqualCode(expected, astIntegerLiteral2())
    }

    @Test
    fun testIntegerLiteral2Exports() {
        val expected = setOf<String>()
        assertExports(expected, astIntegerLiteral2())
    }

    @Test
    fun testIntegerLiteral2Imports() {
        val expected = setOf<String>()
        assertImports(expected, astIntegerLiteral2())
    }
    private fun astIntegerLiteral3() =
        pyProgram(
            l1c0c2,
            body = listOf(
                Py.ExprStmt(
                    l1c0c2,
                    value = Py.UnaryExpr(l1c0c2, op = UnaryOpEnum.UnaryAdd.atom(p0), operand = Py.Num(l1c1, n = 3)),
                ),
            ),
        )

    @Test
    fun testIntegerLiteral3Out() {
        val expected = "+3\n"
        assertEqualCode(expected, astIntegerLiteral3())
    }

    @Test
    fun testIntegerLiteral3Exports() {
        val expected = setOf<String>()
        assertExports(expected, astIntegerLiteral3())
    }

    @Test
    fun testIntegerLiteral3Imports() {
        val expected = setOf<String>()
        assertImports(expected, astIntegerLiteral3())
    }
    private fun astFloatLiteral1() =
        pyProgram(l1c0c5, body = listOf(Py.ExprStmt(l1c0c5, value = Py.Num(l1c0c5, n = 2.125))))

    @Test
    fun testFloatLiteral1Out() {
        val expected = "2.125\n"
        assertEqualCode(expected, astFloatLiteral1())
    }

    @Test
    fun testFloatLiteral1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astFloatLiteral1())
    }

    @Test
    fun testFloatLiteral1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFloatLiteral1())
    }
    private fun astFloatLiteral2() =
        pyProgram(l1c0c9, body = listOf(Py.ExprStmt(l1c0c9, value = Py.Num(l1c0c9, n = 1750000.0))))

    @Test
    fun testFloatLiteral2Out() {
        val expected = "1750000.0\n"
        assertEqualCode(expected, astFloatLiteral2())
    }

    @Test
    fun testFloatLiteral2Exports() {
        val expected = setOf<String>()
        assertExports(expected, astFloatLiteral2())
    }

    @Test
    fun testFloatLiteral2Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFloatLiteral2())
    }
    private fun astFloatLiteral3() =
        pyProgram(
            l1c0c6,
            body = listOf(
                Py.ExprStmt(
                    l1c0c6,
                    value = Py.UnaryExpr(
                        l1c0c6,
                        op = UnaryOpEnum.UnaryAdd.atom(p0),
                        operand = Py.Num(l1c1c6, n = 2.125),
                    ),
                ),
            ),
        )

    @Test
    fun testFloatLiteral3Out() {
        val expected = "+2.125\n"
        assertEqualCode(expected, astFloatLiteral3())
    }

    @Test
    fun testFloatLiteral3Exports() {
        val expected = setOf<String>()
        assertExports(expected, astFloatLiteral3())
    }

    @Test
    fun testFloatLiteral3Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFloatLiteral3())
    }
    private fun astFloatLiteral4() =
        pyProgram(
            l1c0c6,
            body = listOf(
                Py.ExprStmt(
                    l1c0c6,
                    value = Py.UnaryExpr(
                        l1c0c6,
                        op = UnaryOpEnum.UnarySub.atom(p0),
                        operand = Py.Num(l1c1c6, n = 2.125),
                    ),
                ),
            ),
        )

    @Test
    fun testFloatLiteral4Out() {
        val expected = "-2.125\n"
        assertEqualCode(expected, astFloatLiteral4())
    }

    @Test
    fun testFloatLiteral4Exports() {
        val expected = setOf<String>()
        assertExports(expected, astFloatLiteral4())
    }

    @Test
    fun testFloatLiteral4Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFloatLiteral4())
    }
    private fun astStringLiteral1() =
        pyProgram(l1c0c10, body = listOf(Py.ExprStmt(l1c0c10, value = Py.Str(l1c0c10, s = "a string"))))

    @Test
    fun testStringLiteral1Out() {
        val expected = "'a string'\n"
        assertEqualCode(expected, astStringLiteral1())
    }

    @Test
    fun testStringLiteral1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStringLiteral1())
    }

    @Test
    fun testStringLiteral1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStringLiteral1())
    }
    private fun astStringLiteralEmpty1() =
        pyProgram(l1c0c2, body = listOf(Py.ExprStmt(l1c0c2, value = Py.Str(l1c0c2, s = ""))))

    @Test
    fun testStringLiteralEmpty1Out() {
        val expected = "''\n"
        assertEqualCode(expected, astStringLiteralEmpty1())
    }

    @Test
    fun testStringLiteralEmpty1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStringLiteralEmpty1())
    }

    @Test
    fun testStringLiteralEmpty1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStringLiteralEmpty1())
    }
    private fun astStringLiteralNewline1() =
        pyProgram(l1c0c10, body = listOf(Py.ExprStmt(l1c0c10, value = Py.Str(l1c0c10, s = "foo\nbar"))))

    @Test
    fun testStringLiteralNewline1Out() {
        val expected = "'foo\\nbar'\n"
        assertEqualCode(expected, astStringLiteralNewline1())
    }

    @Test
    fun testStringLiteralNewline1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStringLiteralNewline1())
    }

    @Test
    fun testStringLiteralNewline1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStringLiteralNewline1())
    }
    private fun astStringLiteralManyNewlines1() =
        pyProgram(
            l1c0c25,
            body = listOf(Py.ExprStmt(l1c0c25, value = Py.Str(l1c0c25, s = "foo\n\n\nbar\n\n\nqux\n"))),
        )

    @Test
    fun testStringLiteralManyNewlines1Out() {
        val expected = "'foo\\n\\n\\nbar\\n\\n\\nqux\\n'\n"
        assertEqualCode(expected, astStringLiteralManyNewlines1())
    }

    @Test
    fun testStringLiteralManyNewlines1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStringLiteralManyNewlines1())
    }

    @Test
    fun testStringLiteralManyNewlines1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStringLiteralManyNewlines1())
    }
    private fun astStringLiteralSingleQuote1() =
        pyProgram(l1c0c9, body = listOf(Py.ExprStmt(l1c0c9, value = Py.Str(l1c0c9, s = "foo'bar"))))

    @Test
    fun testStringLiteralSingleQuote1Out() {
        val expected = "\"foo'bar\"\n"
        assertEqualCode(expected, astStringLiteralSingleQuote1())
    }

    @Test
    fun testStringLiteralSingleQuote1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStringLiteralSingleQuote1())
    }

    @Test
    fun testStringLiteralSingleQuote1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStringLiteralSingleQuote1())
    }
    private fun astStringLiteralDoubleQuote1() =
        pyProgram(l1c0c9, body = listOf(Py.ExprStmt(l1c0c9, value = Py.Str(l1c0c9, s = "foo\"bar"))))

    @Test
    fun testStringLiteralDoubleQuote1Out() {
        val expected = "'foo\"bar'\n"
        assertEqualCode(expected, astStringLiteralDoubleQuote1())
    }

    @Test
    fun testStringLiteralDoubleQuote1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStringLiteralDoubleQuote1())
    }

    @Test
    fun testStringLiteralDoubleQuote1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStringLiteralDoubleQuote1())
    }
    private fun astStringLiteralCr1() =
        pyProgram(l1c0c10, body = listOf(Py.ExprStmt(l1c0c10, value = Py.Str(l1c0c10, s = "foo\rbar"))))

    @Test
    fun testStringLiteralCr1Out() {
        val expected = "'foo\\rbar'\n"
        assertEqualCode(expected, astStringLiteralCr1())
    }

    @Test
    fun testStringLiteralCr1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStringLiteralCr1())
    }

    @Test
    fun testStringLiteralCr1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStringLiteralCr1())
    }
    private fun astStringLiteralTab1() =
        pyProgram(l1c0c10, body = listOf(Py.ExprStmt(l1c0c10, value = Py.Str(l1c0c10, s = "foo\tbar"))))

    @Test
    fun testStringLiteralTab1Out() {
        val expected = "'foo\\tbar'\n"
        assertEqualCode(expected, astStringLiteralTab1())
    }

    @Test
    fun testStringLiteralTab1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStringLiteralTab1())
    }

    @Test
    fun testStringLiteralTab1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStringLiteralTab1())
    }
    private fun astStringLiteralSimpleHex1() =
        pyProgram(l1c0c12, body = listOf(Py.ExprStmt(l1c0c12, value = Py.Str(l1c0c12, s = "foo\u009cbar"))))

    @Test
    fun testStringLiteralSimpleHex1Out() {
        val expected = "'foo\\x9cbar'\n"
        assertEqualCode(expected, astStringLiteralSimpleHex1())
    }

    @Test
    fun testStringLiteralSimpleHex1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStringLiteralSimpleHex1())
    }

    @Test
    fun testStringLiteralSimpleHex1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStringLiteralSimpleHex1())
    }
    private fun astStringLiteralEuroSymbol1() =
        pyProgram(l1c0c14, body = listOf(Py.ExprStmt(l1c0c14, value = Py.Str(l1c0c14, s = "foo\u20acbar"))))

    @Test
    fun testStringLiteralEuroSymbol1Out() {
        val expected = "'foo\\u20acbar'\n"
        assertEqualCode(expected, astStringLiteralEuroSymbol1())
    }

    @Test
    fun testStringLiteralEuroSymbol1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStringLiteralEuroSymbol1())
    }

    @Test
    fun testStringLiteralEuroSymbol1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStringLiteralEuroSymbol1())
    }
    private fun astStringLiteralUnicode321() =
        pyProgram(
            l1c0c18,
            body = listOf(Py.ExprStmt(l1c0c18, value = Py.Str(l1c0c18, s = "foo\ud83d\udf96bar"))),
        )

    @Test
    fun testStringLiteralUnicode321Out() {
        val expected = "'foo\\U0001f796bar'\n"
        assertEqualCode(expected, astStringLiteralUnicode321())
    }

    @Test
    fun testStringLiteralUnicode321Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStringLiteralUnicode321())
    }

    @Test
    fun testStringLiteralUnicode321Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStringLiteralUnicode321())
    }
    private fun astConstantTrue1() =
        pyProgram(
            l1c0c4,
            body = listOf(Py.ExprStmt(l1c0c4, value = Py.Constant(l1c0c4, value = PyConstant.True))),
        )

    @Test
    fun testConstantTrue1Out() {
        val expected = "True\n"
        assertEqualCode(expected, astConstantTrue1())
    }

    @Test
    fun testConstantTrue1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astConstantTrue1())
    }

    @Test
    fun testConstantTrue1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astConstantTrue1())
    }
    private fun astConstantFalse1() =
        pyProgram(
            l1c0c5,
            body = listOf(Py.ExprStmt(l1c0c5, value = Py.Constant(l1c0c5, value = PyConstant.False))),
        )

    @Test
    fun testConstantFalse1Out() {
        val expected = "False\n"
        assertEqualCode(expected, astConstantFalse1())
    }

    @Test
    fun testConstantFalse1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astConstantFalse1())
    }

    @Test
    fun testConstantFalse1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astConstantFalse1())
    }
    private fun astConstantNone1() =
        pyProgram(
            l1c0c4,
            body = listOf(Py.ExprStmt(l1c0c4, value = Py.Constant(l1c0c4, value = PyConstant.None))),
        )

    @Test
    fun testConstantNone1Out() {
        val expected = "None\n"
        assertEqualCode(expected, astConstantNone1())
    }

    @Test
    fun testConstantNone1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astConstantNone1())
    }

    @Test
    fun testConstantNone1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astConstantNone1())
    }
    private fun astConstantEllipsis1() =
        pyProgram(
            l1c0c3,
            body = listOf(Py.ExprStmt(l1c0c3, value = Py.Constant(l1c0c3, value = PyConstant.Ellipsis))),
        )

    @Test
    fun testConstantEllipsis1Out() {
        val expected = "...\n"
        assertEqualCode(expected, astConstantEllipsis1())
    }

    @Test
    fun testConstantEllipsis1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astConstantEllipsis1())
    }

    @Test
    fun testConstantEllipsis1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astConstantEllipsis1())
    }
    private fun astConstantNotImplemented1() =
        pyProgram(
            l1c0c14,
            body = listOf(Py.ExprStmt(l1c0c14, value = Py.Constant(l1c0c14, value = PyConstant.NotImplemented))),
        )

    @Test
    fun testConstantNotImplemented1Out() {
        val expected = "NotImplemented\n"
        assertEqualCode(expected, astConstantNotImplemented1())
    }

    @Test
    fun testConstantNotImplemented1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astConstantNotImplemented1())
    }

    @Test
    fun testConstantNotImplemented1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astConstantNotImplemented1())
    }
    private fun astEmptyTuple1() =
        pyProgram(l1c0c2, body = listOf(Py.ExprStmt(l1c0c2, value = Py.Tuple(l1c0c2, elts = listOf()))))

    @Test
    fun testEmptyTuple1Out() {
        val expected = "()\n"
        assertEqualCode(expected, astEmptyTuple1())
    }

    @Test
    fun testEmptyTuple1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astEmptyTuple1())
    }

    @Test
    fun testEmptyTuple1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astEmptyTuple1())
    }
    private fun astSingleTuple1() =
        pyProgram(
            l1c0c7,
            body = listOf(
                Py.ExprStmt(
                    l1c0c7,
                    value = Py.Tuple(l1c0c7, elts = listOf(Py.Constant(l1c1c5, value = PyConstant.None))),
                ),
            ),
        )

    @Test
    fun testSingleTuple1Out() {
        val expected = "(None,)\n"
        assertEqualCode(expected, astSingleTuple1())
    }

    @Test
    fun testSingleTuple1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astSingleTuple1())
    }

    @Test
    fun testSingleTuple1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astSingleTuple1())
    }
    private fun astTwoElements1() =
        pyProgram(
            l1c0c12,
            body = listOf(
                Py.ExprStmt(
                    l1c0c12,
                    value = Py.Tuple(
                        l1c0c12,
                        elts = listOf(
                            Py.Constant(l1c1c5, value = PyConstant.None),
                            Py.Constant(l1c7c11, value = PyConstant.None),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testTwoElements1Out() {
        val expected = "(None, None)\n"
        assertEqualCode(expected, astTwoElements1())
    }

    @Test
    fun testTwoElements1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astTwoElements1())
    }

    @Test
    fun testTwoElements1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astTwoElements1())
    }
    private fun astAttribute1() =
        pyProgram(
            l1c0c3,
            body = listOf(
                Py.ExprStmt(
                    l1c0c3,
                    value = Py.Attribute(
                        l1c0c3,
                        value = Py.Name(l1c0, id = PyIdentifierName("x")),
                        attr = Py.Identifier(p0, id = PyIdentifierName("a")),
                    ),
                ),
            ),
        )

    @Test
    fun testAttribute1Out() {
        val expected = "x.a\n"
        assertEqualCode(expected, astAttribute1())
    }

    @Test
    fun testAttribute1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astAttribute1())
    }

    @Test
    fun testAttribute1Imports() {
        val expected = setOf("x")
        assertImports(expected, astAttribute1())
    }
    private fun astSubscript1() =
        pyProgram(
            l1c0c4,
            body = listOf(
                Py.ExprStmt(
                    l1c0c4,
                    value = Py.Subscript(
                        l1c0c4,
                        value = Py.Name(l1c0, id = PyIdentifierName("x")),
                        slice = listOf(Py.Name(l1c2, id = PyIdentifierName("a"))),
                    ),
                ),
            ),
        )

    @Test
    fun testSubscript1Out() {
        val expected = "x[a]\n"
        assertEqualCode(expected, astSubscript1())
    }

    @Test
    fun testSubscript1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astSubscript1())
    }

    @Test
    fun testSubscript1Imports() {
        val expected = setOf("x", "a")
        assertImports(expected, astSubscript1())
    }
    private fun astSubscriptSlice1() =
        pyProgram(
            l1c0c6,
            body = listOf(
                Py.ExprStmt(
                    l1c0c6,
                    value = Py.Subscript(
                        l1c0c6,
                        value = Py.Name(l1c0, id = PyIdentifierName("x")),
                        slice = listOf(Py.Slice(l1c3)),
                    ),
                ),
            ),
        )

    @Test
    fun testSubscriptSlice1Out() {
        val expected = "x[ : ]\n"
        assertEqualCode(expected, astSubscriptSlice1())
    }

    @Test
    fun testSubscriptSlice1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astSubscriptSlice1())
    }

    @Test
    fun testSubscriptSlice1Imports() {
        val expected = setOf("x")
        assertImports(expected, astSubscriptSlice1())
    }
    private fun astSubscriptSliceStart1() =
        pyProgram(
            l1c0c7,
            body = listOf(
                Py.ExprStmt(
                    l1c0c7,
                    value = Py.Subscript(
                        l1c0c7,
                        value = Py.Name(l1c0, id = PyIdentifierName("x")),
                        slice = listOf(Py.Slice(l1c2c5, lower = Py.Name(l1c2, id = PyIdentifierName("y")))),
                    ),
                ),
            ),
        )

    @Test
    fun testSubscriptSliceStart1Out() {
        val expected = "x[y : ]\n"
        assertEqualCode(expected, astSubscriptSliceStart1())
    }

    @Test
    fun testSubscriptSliceStart1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astSubscriptSliceStart1())
    }

    @Test
    fun testSubscriptSliceStart1Imports() {
        val expected = setOf("x", "y")
        assertImports(expected, astSubscriptSliceStart1())
    }
    private fun astSubscriptSliceStop1() =
        pyProgram(
            l1c0c7,
            body = listOf(
                Py.ExprStmt(
                    l1c0c7,
                    value = Py.Subscript(
                        l1c0c7,
                        value = Py.Name(l1c0, id = PyIdentifierName("x")),
                        slice = listOf(Py.Slice(l1c3c6, upper = Py.Name(l1c5, id = PyIdentifierName("y")))),
                    ),
                ),
            ),
        )

    @Test
    fun testSubscriptSliceStop1Out() {
        val expected = "x[ : y]\n"
        assertEqualCode(expected, astSubscriptSliceStop1())
    }

    @Test
    fun testSubscriptSliceStop1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astSubscriptSliceStop1())
    }

    @Test
    fun testSubscriptSliceStop1Imports() {
        val expected = setOf("x", "y")
        assertImports(expected, astSubscriptSliceStop1())
    }
    private fun astSubscriptSliceStep1() =
        pyProgram(
            l1c0c9,
            body = listOf(
                Py.ExprStmt(
                    l1c0c9,
                    value = Py.Subscript(
                        l1c0c9,
                        value = Py.Name(l1c0, id = PyIdentifierName("x")),
                        slice = listOf(Py.Slice(l1c3c8, step = Py.Name(l1c7, id = PyIdentifierName("y")))),
                    ),
                ),
            ),
        )

    @Test
    fun testSubscriptSliceStep1Out() {
        val expected = "x[ : : y]\n"
        assertEqualCode(expected, astSubscriptSliceStep1())
    }

    @Test
    fun testSubscriptSliceStep1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astSubscriptSliceStep1())
    }

    @Test
    fun testSubscriptSliceStep1Imports() {
        val expected = setOf("x", "y")
        assertImports(expected, astSubscriptSliceStep1())
    }
    private fun astCall1() =
        pyProgram(
            l1c0c5,
            body = listOf(
                Py.ExprStmt(
                    l1c0c5,
                    value = Py.Call(l1c0c5, func = Py.Name(l1c0c3, id = PyIdentifierName("foo")), args = listOf()),
                ),
            ),
        )

    @Test
    fun testCall1Out() {
        val expected = "foo()\n"
        assertEqualCode(expected, astCall1())
    }

    @Test
    fun testCall1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astCall1())
    }

    @Test
    fun testCall1Imports() {
        val expected = setOf("foo")
        assertImports(expected, astCall1())
    }
    private fun astCallPosArg1() =
        pyProgram(
            l1c0c6,
            body = listOf(
                Py.ExprStmt(
                    l1c0c6,
                    value = Py.Call(
                        l1c0c6,
                        func = Py.Name(l1c0c3, id = PyIdentifierName("foo")),
                        args = listOf(
                            Py.CallArg(
                                l1c4,
                                arg = null,
                                value = Py.Name(l1c4, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testCallPosArg1Out() {
        val expected = "foo(x)\n"
        assertEqualCode(expected, astCallPosArg1())
    }

    @Test
    fun testCallPosArg1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astCallPosArg1())
    }

    @Test
    fun testCallPosArg1Imports() {
        val expected = setOf("foo", "x")
        assertImports(expected, astCallPosArg1())
    }
    private fun astCallPosArg21() =
        pyProgram(
            l1c0c9,
            body = listOf(
                Py.ExprStmt(
                    l1c0c9,
                    value = Py.Call(
                        l1c0c9,
                        func = Py.Name(l1c0c3, id = PyIdentifierName("foo")),
                        args = listOf(
                            Py.CallArg(
                                l1c4,
                                arg = null,
                                value = Py.Name(l1c4, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c7,
                                arg = null,
                                value = Py.Name(l1c7, id = PyIdentifierName("y")),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testCallPosArg21Out() {
        val expected = "foo(x, y)\n"
        assertEqualCode(expected, astCallPosArg21())
    }

    @Test
    fun testCallPosArg21Exports() {
        val expected = setOf<String>()
        assertExports(expected, astCallPosArg21())
    }

    @Test
    fun testCallPosArg21Imports() {
        val expected = setOf("foo", "x", "y")
        assertImports(expected, astCallPosArg21())
    }
    private fun astCallVarArg1() =
        pyProgram(
            l1c0c10,
            body = listOf(
                Py.ExprStmt(
                    l1c0c10,
                    value = Py.Call(
                        l1c0c10,
                        func = Py.Name(l1c0c3, id = PyIdentifierName("foo")),
                        args = listOf(
                            Py.CallArg(
                                l1c4,
                                arg = null,
                                value = Py.Name(l1c4, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c7c9,
                                arg = null,
                                value = Py.Starred(l1c7c9, value = Py.Name(l1c8, id = PyIdentifierName("y"))),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testCallVarArg1Out() {
        val expected = "foo(x, *y)\n"
        assertEqualCode(expected, astCallVarArg1())
    }

    @Test
    fun testCallVarArg1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astCallVarArg1())
    }

    @Test
    fun testCallVarArg1Imports() {
        val expected = setOf("foo", "x", "y")
        assertImports(expected, astCallVarArg1())
    }
    private fun astCallVarKwArg1() =
        pyProgram(
            l1c0c12,
            body = listOf(
                Py.ExprStmt(
                    l1c0c12,
                    value = Py.Call(
                        l1c0c12,
                        func = Py.Name(l1c0c3, id = PyIdentifierName("foo")),
                        args = listOf(
                            Py.CallArg(
                                l1c4c6,
                                arg = null,
                                value = Py.Starred(l1c4c6, value = Py.Name(l1c5, id = PyIdentifierName("y"))),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c8c11,
                                arg = null,
                                value = Py.Name(l1c10, id = PyIdentifierName("z")),
                                prefix = Py.ArgPrefix.DoubleStar,
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testCallVarKwArg1Out() {
        val expected = "foo(*y, **z)\n"
        assertEqualCode(expected, astCallVarKwArg1())
    }

    @Test
    fun testCallVarKwArg1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astCallVarKwArg1())
    }

    @Test
    fun testCallVarKwArg1Imports() {
        val expected = setOf("foo", "y", "z")
        assertImports(expected, astCallVarKwArg1())
    }
    private fun astCallNamedArg1() =
        pyProgram(
            l1c0c13,
            body = listOf(
                Py.ExprStmt(
                    l1c0c13,
                    value = Py.Call(
                        l1c0c13,
                        func = Py.Name(l1c0c3, id = PyIdentifierName("foo")),
                        args = listOf(
                            Py.CallArg(
                                l1c4,
                                arg = null,
                                value = Py.Name(l1c4, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c7c12,
                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                value = Py.Num(l1c11, n = 1),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testCallNamedArg1Out() {
        val expected = "foo(x, y = 1)\n"
        assertEqualCode(expected, astCallNamedArg1())
    }

    @Test
    fun testCallNamedArg1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astCallNamedArg1())
    }

    @Test
    fun testCallNamedArg1Imports() {
        val expected = setOf("foo", "x")
        assertImports(expected, astCallNamedArg1())
    }
    private fun astCallDefaultArgVar1() =
        pyProgram(
            l1c0c17,
            body = listOf(
                Py.ExprStmt(
                    l1c0c17,
                    value = Py.Call(
                        l1c0c17,
                        func = Py.Name(l1c0c3, id = PyIdentifierName("foo")),
                        args = listOf(
                            Py.CallArg(
                                l1c4,
                                arg = null,
                                value = Py.Name(l1c4, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c7c9,
                                arg = null,
                                value = Py.Starred(l1c7c9, value = Py.Name(l1c8, id = PyIdentifierName("z"))),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c11c16,
                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                value = Py.Num(l1c15, n = 1),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testCallDefaultArgVar1Out() {
        val expected = "foo(x, *z, y = 1)\n"
        assertEqualCode(expected, astCallDefaultArgVar1())
    }

    @Test
    fun testCallDefaultArgVar1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astCallDefaultArgVar1())
    }

    @Test
    fun testCallDefaultArgVar1Imports() {
        val expected = setOf("foo", "x", "z")
        assertImports(expected, astCallDefaultArgVar1())
    }
    private fun astCallDefaultArgKw1() =
        pyProgram(
            l1c0c18,
            body = listOf(
                Py.ExprStmt(
                    l1c0c18,
                    value = Py.Call(
                        l1c0c18,
                        func = Py.Name(l1c0c3, id = PyIdentifierName("foo")),
                        args = listOf(
                            Py.CallArg(
                                l1c4,
                                arg = null,
                                value = Py.Name(l1c4, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c7c12,
                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                value = Py.Num(l1c11, n = 1),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c14c17,
                                arg = null,
                                value = Py.Name(l1c16, id = PyIdentifierName("z")),
                                prefix = Py.ArgPrefix.DoubleStar,
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testCallDefaultArgKw1Out() {
        val expected = "foo(x, y = 1, **z)\n"
        assertEqualCode(expected, astCallDefaultArgKw1())
    }

    @Test
    fun testCallDefaultArgKw1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astCallDefaultArgKw1())
    }

    @Test
    fun testCallDefaultArgKw1Imports() {
        val expected = setOf("foo", "x", "z")
        assertImports(expected, astCallDefaultArgKw1())
    }
    private fun astExprAdd1() =
        pyProgram(
            l1c0c9,
            body = listOf(
                Py.ExprStmt(
                    l1c0c9,
                    value = Py.BinExpr(
                        l1c0c9,
                        left = Py.BinExpr(
                            l1c0c5,
                            left = Py.Name(l1c0, id = PyIdentifierName("x")),
                            op = BinaryOpEnum.Add.atom(p0),
                            right = Py.Name(l1c4, id = PyIdentifierName("y")),
                        ),
                        op = BinaryOpEnum.Add.atom(p0),
                        right = Py.Name(l1c8, id = PyIdentifierName("z")),
                    ),
                ),
            ),
        )

    @Test
    fun testExprAdd1Out() {
        val expected = "x + y + z\n"
        assertEqualCode(expected, astExprAdd1())
    }

    @Test
    fun testExprAdd1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astExprAdd1())
    }

    @Test
    fun testExprAdd1Imports() {
        val expected = setOf("x", "y", "z")
        assertImports(expected, astExprAdd1())
    }
    private fun astExprTimes1() =
        pyProgram(
            l1c0c9,
            body = listOf(
                Py.ExprStmt(
                    l1c0c9,
                    value = Py.BinExpr(
                        l1c0c9,
                        left = Py.BinExpr(
                            l1c0c5,
                            left = Py.Name(l1c0, id = PyIdentifierName("x")),
                            op = BinaryOpEnum.Mult.atom(p0),
                            right = Py.Name(l1c4, id = PyIdentifierName("y")),
                        ),
                        op = BinaryOpEnum.Mult.atom(p0),
                        right = Py.Name(l1c8, id = PyIdentifierName("z")),
                    ),
                ),
            ),
        )

    @Test
    fun testExprTimes1Out() {
        val expected = "x * y * z\n"
        assertEqualCode(expected, astExprTimes1())
    }

    @Test
    fun testExprTimes1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astExprTimes1())
    }

    @Test
    fun testExprTimes1Imports() {
        val expected = setOf("x", "y", "z")
        assertImports(expected, astExprTimes1())
    }
    private fun astExprMatMult1() =
        pyProgram(
            l1c0c9,
            body = listOf(
                Py.ExprStmt(
                    l1c0c9,
                    value = Py.BinExpr(
                        l1c0c9,
                        left = Py.BinExpr(
                            l1c0c5,
                            left = Py.Name(l1c0, id = PyIdentifierName("x")),
                            op = BinaryOpEnum.MatMult.atom(p0),
                            right = Py.Name(l1c4, id = PyIdentifierName("y")),
                        ),
                        op = BinaryOpEnum.MatMult.atom(p0),
                        right = Py.Name(l1c8, id = PyIdentifierName("z")),
                    ),
                ),
            ),
        )

    @Test
    fun testExprMatMult1Out() {
        val expected = "x @ y @ z\n"
        assertEqualCode(expected, astExprMatMult1())
    }

    @Test
    fun testExprMatMult1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astExprMatMult1())
    }

    @Test
    fun testExprMatMult1Imports() {
        val expected = setOf("x", "y", "z")
        assertImports(expected, astExprMatMult1())
    }
    private fun astExprPow1() =
        pyProgram(
            l1c0c11,
            body = listOf(
                Py.ExprStmt(
                    l1c0c11,
                    value = Py.BinExpr(
                        l1c0c11,
                        left = Py.Name(l1c0, id = PyIdentifierName("x")),
                        op = BinaryOpEnum.Pow.atom(p0),
                        right = Py.BinExpr(
                            l1c5c11,
                            left = Py.Name(l1c5, id = PyIdentifierName("y")),
                            op = BinaryOpEnum.Pow.atom(p0),
                            right = Py.Name(l1c10, id = PyIdentifierName("z")),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testExprPow1Out() {
        val expected = "x ** y ** z\n"
        assertEqualCode(expected, astExprPow1())
    }

    @Test
    fun testExprPow1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astExprPow1())
    }

    @Test
    fun testExprPow1Imports() {
        val expected = setOf("x", "y", "z")
        assertImports(expected, astExprPow1())
    }
    private fun astExprAddUnary1() =
        pyProgram(
            l1c0c6,
            body = listOf(
                Py.ExprStmt(
                    l1c0c6,
                    value = Py.BinExpr(
                        l1c0c6,
                        left = Py.Name(l1c0, id = PyIdentifierName("x")),
                        op = BinaryOpEnum.Add.atom(p0),
                        right = Py.UnaryExpr(
                            l1c4c6,
                            op = UnaryOpEnum.UnarySub.atom(p0),
                            operand = Py.Name(l1c5, id = PyIdentifierName("y")),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testExprAddUnary1Out() {
        val expected = "x + -y\n"
        assertEqualCode(expected, astExprAddUnary1())
    }

    @Test
    fun testExprAddUnary1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astExprAddUnary1())
    }

    @Test
    fun testExprAddUnary1Imports() {
        val expected = setOf("x", "y")
        assertImports(expected, astExprAddUnary1())
    }
    private fun astExprBool1() =
        pyProgram(
            l1c0c16,
            body = listOf(
                Py.ExprStmt(
                    l1c0c16,
                    value = Py.BinExpr(
                        l1c15,
                        op = BinaryOpEnum.BoolOr.atom(p0),
                        left = Py.BinExpr(
                            l1c6c11,
                            op = BinaryOpEnum.BoolAnd.atom(p0),
                            left = Py.Name(l1c0, id = PyIdentifierName("x")),
                            right = Py.UnaryExpr(
                                l1c6c11,
                                op = UnaryOpEnum.BoolNot.atom(p0),
                                operand = Py.Name(l1c10, id = PyIdentifierName("y")),
                            ),
                        ),
                        right = Py.Name(l1c15, id = PyIdentifierName("z")),
                    ),
                ),
            ),
        )

    @Test
    fun testExprBool1Out() {
        val expected = "x and not y or z\n"
        assertEqualCode(expected, astExprBool1())
    }

    @Test
    fun testExprBool1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astExprBool1())
    }

    @Test
    fun testExprBool1Imports() {
        val expected = setOf("x", "y", "z")
        assertImports(expected, astExprBool1())
    }
    private fun astExprBitwise1() =
        pyProgram(
            l1c0c10,
            body = listOf(
                Py.ExprStmt(
                    l1c0c10,
                    value = Py.BinExpr(
                        l1c0c10,
                        left = Py.BinExpr(
                            l1c0c6,
                            left = Py.Name(l1c0, id = PyIdentifierName("x")),
                            op = BinaryOpEnum.BitwiseAnd.atom(p0),
                            right = Py.UnaryExpr(
                                l1c4c6,
                                op = UnaryOpEnum.UnaryInvert.atom(p0),
                                operand = Py.Name(l1c5, id = PyIdentifierName("y")),
                            ),
                        ),
                        op = BinaryOpEnum.BitwiseOr.atom(p0),
                        right = Py.Name(l1c9, id = PyIdentifierName("z")),
                    ),
                ),
            ),
        )

    @Test
    fun testExprBitwise1Out() {
        val expected = "x & ~y | z\n"
        assertEqualCode(expected, astExprBitwise1())
    }

    @Test
    fun testExprBitwise1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astExprBitwise1())
    }

    @Test
    fun testExprBitwise1Imports() {
        val expected = setOf("x", "y", "z")
        assertImports(expected, astExprBitwise1())
    }
    private fun astCompare1() =
        pyProgram(
            l1c0c5,
            body = listOf(
                Py.ExprStmt(
                    l1c0c5,
                    value = Py.BinExpr(
                        l1c0c5,
                        left = Py.Name(l1c0, id = PyIdentifierName("x")),
                        op = BinaryOpEnum.Lt.atom(p0),
                        right = Py.Name(l1c4, id = PyIdentifierName("y")),
                    ),
                ),
            ),
        )

    @Test
    fun testCompare1Out() {
        val expected = "x < y\n"
        assertEqualCode(expected, astCompare1())
    }

    @Test
    fun testCompare1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astCompare1())
    }

    @Test
    fun testCompare1Imports() {
        val expected = setOf("x", "y")
        assertImports(expected, astCompare1())
    }
    private fun astMember1() =
        pyProgram(
            l1c0c6,
            body = listOf(
                Py.ExprStmt(
                    l1c0c6,
                    value = Py.BinExpr(
                        l1c0c6,
                        left = Py.Name(l1c0, id = PyIdentifierName("x")),
                        op = BinaryOpEnum.In.atom(p0),
                        right = Py.Name(l1c5, id = PyIdentifierName("y")),
                    ),
                ),
            ),
        )

    @Test
    fun testMember1Out() {
        val expected = "x in y\n"
        assertEqualCode(expected, astMember1())
    }

    @Test
    fun testMember1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astMember1())
    }

    @Test
    fun testMember1Imports() {
        val expected = setOf("x", "y")
        assertImports(expected, astMember1())
    }
    private fun astTrinary1() =
        pyProgram(
            l1c0c25,
            body = listOf(
                Py.ExprStmt(
                    l1c0c25,
                    value = Py.IfExpr(
                        l1c0c25,
                        test = Py.Name(l1c5, id = PyIdentifierName("b")),
                        body = Py.Name(l1c0, id = PyIdentifierName("a")),
                        orElse = Py.IfExpr(
                            l1c12c25,
                            test = Py.Name(l1c17, id = PyIdentifierName("d")),
                            body = Py.Name(l1c12, id = PyIdentifierName("c")),
                            orElse = Py.Name(l1c24, id = PyIdentifierName("e")),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testTrinary1Out() {
        val expected = "a if b else c if d else e\n"
        assertEqualCode(expected, astTrinary1())
    }

    @Test
    fun testTrinary1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astTrinary1())
    }

    @Test
    fun testTrinary1Imports() {
        val expected = setOf("a", "b", "c", "d", "e")
        assertImports(expected, astTrinary1())
    }
    private fun astTupleConcat1() =
        pyProgram(
            l1c0c15,
            body = listOf(
                Py.ExprStmt(
                    l1c0c15,
                    value = Py.BinExpr(
                        l1c0c15,
                        left = Py.Tuple(l1c0c6, elts = listOf(Py.Num(l1c1, n = 1), Py.Num(l1c4, n = 2))),
                        op = BinaryOpEnum.Add.atom(p0),
                        right = Py.Tuple(l1c9c15, elts = listOf(Py.Num(l1c10, n = 3), Py.Num(l1c13, n = 4))),
                    ),
                ),
            ),
        )

    @Test
    fun testTupleConcat1Out() {
        val expected = "(1, 2) + (3, 4)\n"
        assertEqualCode(expected, astTupleConcat1())
    }

    @Test
    fun testTupleConcat1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astTupleConcat1())
    }

    @Test
    fun testTupleConcat1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astTupleConcat1())
    }
    private fun astOneItemTuple1() =
        pyProgram(
            l1c0c9,
            body = listOf(
                Py.ExprStmt(
                    l1c0c9,
                    value = Py.Call(
                        l1c0c9,
                        func = Py.Name(l1c0c3, id = PyIdentifierName("foo")),
                        args = listOf(
                            Py.CallArg(
                                l1c4c8,
                                arg = null,
                                value = Py.Tuple(l1c4c8, elts = listOf(Py.Num(l1c5, n = 1))),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testOneItemTuple1Out() {
        val expected = "foo((1,))\n"
        assertEqualCode(expected, astOneItemTuple1())
    }

    @Test
    fun testOneItemTuple1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astOneItemTuple1())
    }

    @Test
    fun testOneItemTuple1Imports() {
        val expected = setOf("foo")
        assertImports(expected, astOneItemTuple1())
    }
    private fun astLambda1() =
        pyProgram(
            l1c0c11,
            body = listOf(
                Py.ExprStmt(
                    l1c0c11,
                    value = Py.Lambda(
                        l1c0c11,
                        args = Py.Arguments(p0, args = listOf()),
                        body = Py.Constant(l1c8c11, value = PyConstant.Ellipsis),
                    ),
                ),
            ),
        )

    @Test
    fun testLambda1Out() {
        val expected = "lambda: ...\n"
        assertEqualCode(expected, astLambda1())
    }

    @Test
    fun testLambda1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astLambda1())
    }

    @Test
    fun testLambda1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astLambda1())
    }
    private fun astLambdaPosArg1() =
        pyProgram(
            l1c0c13,
            body = listOf(
                Py.ExprStmt(
                    l1c0c13,
                    value = Py.Lambda(
                        l1c0c13,
                        args = Py.Arguments(
                            p0,
                            args = listOf(
                                Py.Arg(
                                    l1c7,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                            ),
                        ),
                        body = Py.Constant(l1c10c13, value = PyConstant.Ellipsis),
                    ),
                ),
            ),
        )

    @Test
    fun testLambdaPosArg1Out() {
        val expected = "lambda x: ...\n"
        assertEqualCode(expected, astLambdaPosArg1())
    }

    @Test
    fun testLambdaPosArg1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astLambdaPosArg1())
    }

    @Test
    fun testLambdaPosArg1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astLambdaPosArg1())
    }
    private fun astLambdaPosArg2() =
        pyProgram(
            l1c0c11,
            body = listOf(
                Py.ExprStmt(
                    l1c0c11,
                    value = Py.Lambda(
                        l1c0c11,
                        args = Py.Arguments(
                            p0,
                            args = listOf(
                                Py.Arg(
                                    l1c7,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                            ),
                        ),
                        body = Py.Name(l1c10, id = PyIdentifierName("x")),
                    ),
                ),
            ),
        )

    @Test
    fun testLambdaPosArg2Out() {
        val expected = "lambda x: x\n"
        assertEqualCode(expected, astLambdaPosArg2())
    }

    @Test
    fun testLambdaPosArg2Exports() {
        val expected = setOf<String>()
        assertExports(expected, astLambdaPosArg2())
    }

    @Test
    fun testLambdaPosArg2Imports() {
        val expected = setOf<String>()
        assertImports(expected, astLambdaPosArg2())
    }
    private fun astLambdaPosArg3() =
        pyProgram(
            l1c0c11,
            body = listOf(
                Py.ExprStmt(
                    l1c0c11,
                    value = Py.Lambda(
                        l1c0c11,
                        args = Py.Arguments(
                            p0,
                            args = listOf(
                                Py.Arg(
                                    l1c7,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                            ),
                        ),
                        body = Py.Name(l1c10, id = PyIdentifierName("y")),
                    ),
                ),
            ),
        )

    @Test
    fun testLambdaPosArg3Out() {
        val expected = "lambda x: y\n"
        assertEqualCode(expected, astLambdaPosArg3())
    }

    @Test
    fun testLambdaPosArg3Exports() {
        val expected = setOf<String>()
        assertExports(expected, astLambdaPosArg3())
    }

    @Test
    fun testLambdaPosArg3Imports() {
        val expected = setOf("y")
        assertImports(expected, astLambdaPosArg3())
    }
    private fun astLambdaPosArg4() =
        pyProgram(
            l1c0c16,
            body = listOf(
                Py.ExprStmt(
                    l1c0c16,
                    value = Py.Lambda(
                        l1c0c16,
                        args = Py.Arguments(
                            p0,
                            args = listOf(
                                Py.Arg(
                                    l1c7,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                                Py.Arg(
                                    l1c10,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                            ),
                        ),
                        body = Py.Constant(l1c13c16, value = PyConstant.Ellipsis),
                    ),
                ),
            ),
        )

    @Test
    fun testLambdaPosArg4Out() {
        val expected = "lambda x, y: ...\n"
        assertEqualCode(expected, astLambdaPosArg4())
    }

    @Test
    fun testLambdaPosArg4Exports() {
        val expected = setOf<String>()
        assertExports(expected, astLambdaPosArg4())
    }

    @Test
    fun testLambdaPosArg4Imports() {
        val expected = setOf<String>()
        assertImports(expected, astLambdaPosArg4())
    }
    private fun astLambdaVarArg1() =
        pyProgram(
            l1c0c17,
            body = listOf(
                Py.ExprStmt(
                    l1c0c17,
                    value = Py.Lambda(
                        l1c0c17,
                        args = Py.Arguments(
                            p0,
                            args = listOf(
                                Py.Arg(
                                    l1c7,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                                Py.Arg(
                                    l1c11,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                    prefix = Py.ArgPrefix.Star,
                                ),
                            ),
                        ),
                        body = Py.Constant(l1c14c17, value = PyConstant.Ellipsis),
                    ),
                ),
            ),
        )

    @Test
    fun testLambdaVarArg1Out() {
        val expected = "lambda x, *y: ...\n"
        assertEqualCode(expected, astLambdaVarArg1())
    }

    @Test
    fun testLambdaVarArg1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astLambdaVarArg1())
    }

    @Test
    fun testLambdaVarArg1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astLambdaVarArg1())
    }
    private fun astLambdaVarKwArg1() =
        pyProgram(
            l1c0c19,
            body = listOf(
                Py.ExprStmt(
                    l1c0c19,
                    value = Py.Lambda(
                        l1c0c19,
                        args = Py.Arguments(
                            p0,
                            args = listOf(
                                Py.Arg(
                                    l1c8,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                    prefix = Py.ArgPrefix.Star,
                                ),
                                Py.Arg(
                                    l1c13,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("z")),
                                    prefix = Py.ArgPrefix.DoubleStar,
                                ),
                            ),
                        ),
                        body = Py.Constant(l1c16c19, value = PyConstant.Ellipsis),
                    ),
                ),
            ),
        )

    @Test
    fun testLambdaVarKwArg1Out() {
        val expected = "lambda *y, **z: ...\n"
        assertEqualCode(expected, astLambdaVarKwArg1())
    }

    @Test
    fun testLambdaVarKwArg1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astLambdaVarKwArg1())
    }

    @Test
    fun testLambdaVarKwArg1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astLambdaVarKwArg1())
    }
    private fun astLambdaDefaultArg1() =
        pyProgram(
            l1c0c20,
            body = listOf(
                Py.ExprStmt(
                    l1c0c20,
                    value = Py.Lambda(
                        l1c0c20,
                        args = Py.Arguments(
                            p0,
                            args = listOf(
                                Py.Arg(
                                    l1c7,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                                Py.Arg(
                                    l1c10,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                    defaultValue = Py.Name(l1c14, id = PyIdentifierName("a")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                            ),
                        ),
                        body = Py.Constant(l1c17c20, value = PyConstant.Ellipsis),
                    ),
                ),
            ),
        )

    @Test
    fun testLambdaDefaultArg1Out() {
        val expected = "lambda x, y = a: ...\n"
        assertEqualCode(expected, astLambdaDefaultArg1())
    }

    @Test
    fun testLambdaDefaultArg1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astLambdaDefaultArg1())
    }

    @Test
    fun testLambdaDefaultArg1Imports() {
        val expected = setOf("a")
        assertImports(expected, astLambdaDefaultArg1())
    }
    private fun astFunctionDefaultArgVar1() =
        pyProgram(
            l1c0c24,
            body = listOf(
                Py.ExprStmt(
                    l1c0c24,
                    value = Py.Lambda(
                        l1c0c24,
                        args = Py.Arguments(
                            p0,
                            args = listOf(
                                Py.Arg(
                                    l1c7,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                                Py.Arg(
                                    l1c10,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                    defaultValue = Py.Num(l1c14, n = 1),
                                    prefix = Py.ArgPrefix.None,
                                ),
                                Py.Arg(
                                    l1c18,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("z")),
                                    prefix = Py.ArgPrefix.Star,
                                ),
                            ),
                        ),
                        body = Py.Constant(l1c21c24, value = PyConstant.Ellipsis),
                    ),
                ),
            ),
        )

    @Test
    fun testFunctionDefaultArgVar1Out() {
        val expected = "lambda x, y = 1, *z: ...\n"
        assertEqualCode(expected, astFunctionDefaultArgVar1())
    }

    @Test
    fun testFunctionDefaultArgVar1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astFunctionDefaultArgVar1())
    }

    @Test
    fun testFunctionDefaultArgVar1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionDefaultArgVar1())
    }
    private fun astFunctionDefaultArgKw1() =
        pyProgram(
            l1c0c25,
            body = listOf(
                Py.ExprStmt(
                    l1c0c25,
                    value = Py.Lambda(
                        l1c0c25,
                        args = Py.Arguments(
                            p0,
                            args = listOf(
                                Py.Arg(
                                    l1c7,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                                Py.Arg(
                                    l1c10,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                    defaultValue = Py.Num(l1c14, n = 1),
                                    prefix = Py.ArgPrefix.None,
                                ),
                                Py.Arg(
                                    l1c19,
                                    arg = Py.Identifier(p0, id = PyIdentifierName("z")),
                                    prefix = Py.ArgPrefix.DoubleStar,
                                ),
                            ),
                        ),
                        body = Py.Constant(l1c22c25, value = PyConstant.Ellipsis),
                    ),
                ),
            ),
        )

    @Test
    fun testFunctionDefaultArgKw1Out() {
        val expected = "lambda x, y = 1, **z: ...\n"
        assertEqualCode(expected, astFunctionDefaultArgKw1())
    }

    @Test
    fun testFunctionDefaultArgKw1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astFunctionDefaultArgKw1())
    }

    @Test
    fun testFunctionDefaultArgKw1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionDefaultArgKw1())
    }
    private fun astLambdaInFunctionArgs1() =
        pyProgram(
            l1c0c28,
            body = listOf(
                Py.ExprStmt(
                    l1c0c28,
                    value = Py.Call(
                        l1c0c28,
                        func = Py.Name(l1c0, id = PyIdentifierName("f")),
                        args = listOf(
                            Py.CallArg(
                                l1c2,
                                arg = null,
                                value = Py.Name(l1c2, id = PyIdentifierName("a")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c5c24,
                                arg = null,
                                value = Py.Lambda(
                                    l1c5c24,
                                    args = Py.Arguments(
                                        p0,
                                        args = listOf(
                                            Py.Arg(
                                                l1c12,
                                                arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                                prefix = Py.ArgPrefix.None,
                                            ),
                                            Py.Arg(
                                                l1c15,
                                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                                prefix = Py.ArgPrefix.None,
                                            ),
                                        ),
                                    ),
                                    body = Py.Tuple(
                                        l1c18c24,
                                        elts = listOf(
                                            Py.Name(l1c19, id = PyIdentifierName("x")),
                                            Py.Name(l1c22, id = PyIdentifierName("y")),
                                        ),
                                    ),
                                ),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c26,
                                arg = null,
                                value = Py.Name(l1c26, id = PyIdentifierName("b")),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testLambdaInFunctionArgs1Out() {
        val expected = "f(a, lambda x, y: (x, y), b)\n"
        assertEqualCode(expected, astLambdaInFunctionArgs1())
    }

    @Test
    fun testLambdaInFunctionArgs1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astLambdaInFunctionArgs1())
    }

    @Test
    fun testLambdaInFunctionArgs1Imports() {
        val expected = setOf("f", "a", "b")
        assertImports(expected, astLambdaInFunctionArgs1())
    }
    private fun astLambdaInFunctionArgs2() =
        pyProgram(
            l1c0c26,
            body = listOf(
                Py.ExprStmt(
                    l1c0c26,
                    value = Py.Call(
                        l1c0c26,
                        func = Py.Name(l1c0, id = PyIdentifierName("f")),
                        args = listOf(
                            Py.CallArg(
                                l1c2,
                                arg = null,
                                value = Py.Name(l1c2, id = PyIdentifierName("a")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c5c19,
                                arg = null,
                                value = Py.Lambda(
                                    l1c5c19,
                                    args = Py.Arguments(
                                        p0,
                                        args = listOf(
                                            Py.Arg(
                                                l1c12,
                                                arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                                prefix = Py.ArgPrefix.None,
                                            ),
                                            Py.Arg(
                                                l1c15,
                                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                                prefix = Py.ArgPrefix.None,
                                            ),
                                        ),
                                    ),
                                    body = Py.Name(l1c18, id = PyIdentifierName("x")),
                                ),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c21,
                                arg = null,
                                value = Py.Name(l1c21, id = PyIdentifierName("b")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.CallArg(
                                l1c24,
                                arg = null,
                                value = Py.Name(l1c24, id = PyIdentifierName("c")),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testLambdaInFunctionArgs2Out() {
        val expected = "f(a, lambda x, y: x, b, c)\n"
        assertEqualCode(expected, astLambdaInFunctionArgs2())
    }

    @Test
    fun testLambdaInFunctionArgs2Exports() {
        val expected = setOf<String>()
        assertExports(expected, astLambdaInFunctionArgs2())
    }

    @Test
    fun testLambdaInFunctionArgs2Imports() {
        val expected = setOf("f", "a", "b", "c")
        assertImports(expected, astLambdaInFunctionArgs2())
    }
    private fun astStatementAssign1() =
        pyProgram(
            l1c0c5,
            body = listOf(
                Py.Assign(
                    l1c0c5,
                    targets = listOf(Py.Name(l1c0, id = PyIdentifierName("x"))),
                    value = Py.Num(l1c4, n = 1),
                ),
            ),
        )

    @Test
    fun testStatementAssign1Out() {
        val expected = "x = 1\n"
        assertEqualCode(expected, astStatementAssign1())
    }

    @Test
    fun testStatementAssign1Exports() {
        val expected = setOf("x")
        assertExports(expected, astStatementAssign1())
    }

    @Test
    fun testStatementAssign1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementAssign1())
    }
    private fun astStatementAssignTwoVars1() =
        pyProgram(
            l1c0c9,
            body = listOf(
                Py.Assign(
                    l1c0c9,
                    targets = listOf(
                        Py.Name(l1c0, id = PyIdentifierName("x")),
                        Py.Name(l1c4, id = PyIdentifierName("y")),
                    ),
                    value = Py.Num(l1c8, n = 1),
                ),
            ),
        )

    @Test
    fun testStatementAssignTwoVars1Out() {
        val expected = "x = y = 1\n"
        assertEqualCode(expected, astStatementAssignTwoVars1())
    }

    @Test
    fun testStatementAssignTwoVars1Exports() {
        val expected = setOf("x", "y")
        assertExports(expected, astStatementAssignTwoVars1())
    }

    @Test
    fun testStatementAssignTwoVars1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementAssignTwoVars1())
    }
    private fun astStatementAssignStarred1() =
        pyProgram(
            l1c0c19,
            body = listOf(
                Py.Assign(
                    l1c0c19,
                    targets = listOf(
                        Py.Tuple(
                            l1c0c7,
                            elts = listOf(
                                Py.Name(l1c1, id = PyIdentifierName("x")),
                                Py.Starred(l1c4c6, value = Py.Name(l1c5, id = PyIdentifierName("y"))),
                            ),
                        ),
                    ),
                    value = Py.Tuple(
                        l1c10c19,
                        elts = listOf(Py.Num(l1c11, n = 1), Py.Num(l1c14, n = 2), Py.Num(l1c17, n = 3)),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementAssignStarred1Out() {
        val expected = "(x, *y) = (1, 2, 3)\n"
        assertEqualCode(expected, astStatementAssignStarred1())
    }

    @Test
    fun testStatementAssignStarred1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementAssignStarred1())
    }

    @Test
    fun testStatementAssignStarred1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementAssignStarred1())
    }
    private fun astStatementAssignStarred2() =
        pyProgram(
            l1c0l2c12,
            body = listOf(
                Py.Assign(
                    l1c0c19,
                    targets = listOf(
                        Py.Tuple(
                            l1c0c7,
                            elts = listOf(
                                Py.Name(l1c1, id = PyIdentifierName("x")),
                                Py.Starred(l1c4c6, value = Py.Name(l1c5, id = PyIdentifierName("y"))),
                            ),
                        ),
                    ),
                    value = Py.Tuple(
                        l1c10c19,
                        elts = listOf(Py.Num(l1c11, n = 1), Py.Num(l1c14, n = 2), Py.Num(l1c17, n = 3)),
                    ),
                ),
                Py.Assign(
                    l2c0c12,
                    targets = listOf(Py.Name(l2c0, id = PyIdentifierName("z"))),
                    value = Py.BinExpr(
                        l2c4c12,
                        left = Py.Name(l2c4, id = PyIdentifierName("x")),
                        op = BinaryOpEnum.Add.atom(p0),
                        right = Py.Subscript(
                            l2c8c12,
                            value = Py.Name(l2c8, id = PyIdentifierName("y")),
                            slice = listOf(Py.Num(l2c10, n = 0)),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementAssignStarred2Out() {
        val expected = "(x, *y) = (1, 2, 3)\nz = x + y[0]\n"
        assertEqualCode(expected, astStatementAssignStarred2())
    }

    @Test
    fun testStatementAssignStarred2Exports() {
        val expected = setOf("z")
        assertExports(expected, astStatementAssignStarred2())
    }

    @Test
    fun testStatementAssignStarred2Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementAssignStarred2())
    }
    private fun astStatementAssignAnnotate1() =
        pyProgram(
            l1c0c10,
            body = listOf(
                Py.AnnAssign(
                    l1c0c10,
                    target = Py.Name(l1c0, id = PyIdentifierName("x")),
                    annotation = Py.Name(l1c3c6, id = PyIdentifierName("int")),
                    value = Py.Num(l1c9, n = 1),
                ),
            ),
        )

    @Test
    fun testStatementAssignAnnotate1Out() {
        val expected = "x: int = 1\n"
        assertEqualCode(expected, astStatementAssignAnnotate1())
    }

    @Test
    fun testStatementAssignAnnotate1Exports() {
        val expected = setOf("x")
        assertExports(expected, astStatementAssignAnnotate1())
    }

    @Test
    fun testStatementAssignAnnotate1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementAssignAnnotate1())
    }
    private fun astStatementAugmentedAssign1() =
        pyProgram(
            l1c0c6,
            body = listOf(
                Py.AugAssign(
                    l1c0c6,
                    target = Py.Name(l1c0, id = PyIdentifierName("x")),
                    op = AugAssignOpEnum.Add.atom(l1c0c6),
                    value = Py.Num(l1c5, n = 1),
                ),
            ),
        )

    @Test
    fun testStatementAugmentedAssign1Out() {
        val expected = "x += 1\n"
        assertEqualCode(expected, astStatementAugmentedAssign1())
    }

    @Test
    fun testStatementAugmentedAssign1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementAugmentedAssign1())
    }

    @Test
    fun testStatementAugmentedAssign1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementAugmentedAssign1())
    }
    private fun astStatementPass1() =
        pyProgram(l1c0c4, body = listOf(Py.Pass(l1c0c4)))

    @Test
    fun testStatementPass1Out() {
        val expected = "pass\n"
        assertEqualCode(expected, astStatementPass1())
    }

    @Test
    fun testStatementPass1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementPass1())
    }

    @Test
    fun testStatementPass1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementPass1())
    }
    private fun astStatementBreak1() =
        pyProgram(l1c0c5, body = listOf(Py.Break(l1c0c5)))

    @Test
    fun testStatementBreak1Out() {
        val expected = "break\n"
        assertEqualCode(expected, astStatementBreak1())
    }

    @Test
    fun testStatementBreak1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementBreak1())
    }

    @Test
    fun testStatementBreak1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementBreak1())
    }
    private fun astStatementContinue1() =
        pyProgram(l1c0c8, body = listOf(Py.Continue(l1c0c8)))

    @Test
    fun testStatementContinue1Out() {
        val expected = "continue\n"
        assertEqualCode(expected, astStatementContinue1())
    }

    @Test
    fun testStatementContinue1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementContinue1())
    }

    @Test
    fun testStatementContinue1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementContinue1())
    }
    private fun astStatementDelete1() =
        pyProgram(
            l1c0c5,
            body = listOf(Py.Delete(l1c0c5, targets = listOf(Py.Name(l1c4, id = PyIdentifierName("x"))))),
        )

    @Test
    fun testStatementDelete1Out() {
        val expected = "del x\n"
        assertEqualCode(expected, astStatementDelete1())
    }

    @Test
    fun testStatementDelete1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementDelete1())
    }

    @Test
    fun testStatementDelete1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementDelete1())
    }
    private fun astStatementDeleteTwo1() =
        pyProgram(
            l1c0c8,
            body = listOf(
                Py.Delete(
                    l1c0c8,
                    targets = listOf(
                        Py.Name(l1c4, id = PyIdentifierName("x")),
                        Py.Name(l1c7, id = PyIdentifierName("y")),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementDeleteTwo1Out() {
        val expected = "del x, y\n"
        assertEqualCode(expected, astStatementDeleteTwo1())
    }

    @Test
    fun testStatementDeleteTwo1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementDeleteTwo1())
    }

    @Test
    fun testStatementDeleteTwo1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementDeleteTwo1())
    }
    private fun astStatementReturn1() =
        pyProgram(l1c0c6, body = listOf(Py.Return(l1c0c6)))

    @Test
    fun testStatementReturn1Out() {
        val expected = "return\n"
        assertEqualCode(expected, astStatementReturn1())
    }

    @Test
    fun testStatementReturn1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementReturn1())
    }

    @Test
    fun testStatementReturn1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementReturn1())
    }
    private fun astStatementReturnValue1() =
        pyProgram(l1c0c8, body = listOf(Py.Return(l1c0c8, value = Py.Num(l1c7, n = 1))))

    @Test
    fun testStatementReturnValue1Out() {
        val expected = "return 1\n"
        assertEqualCode(expected, astStatementReturnValue1())
    }

    @Test
    fun testStatementReturnValue1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementReturnValue1())
    }

    @Test
    fun testStatementReturnValue1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementReturnValue1())
    }
    private fun astStatementYield1() =
        pyProgram(l1c0c5, body = listOf(Py.ExprStmt(l1c0c5, value = Py.Yield(l1c0c5))))

    @Test
    fun testStatementYield1Out() {
        val expected = "yield\n"
        assertEqualCode(expected, astStatementYield1())
    }

    @Test
    fun testStatementYield1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementYield1())
    }

    @Test
    fun testStatementYield1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementYield1())
    }
    private fun astStatementYieldValue1() =
        pyProgram(
            l1c0c7,
            body = listOf(Py.ExprStmt(l1c0c7, value = Py.Yield(l1c0c7, value = Py.Num(l1c6, n = 1)))),
        )

    @Test
    fun testStatementYieldValue1Out() {
        val expected = "yield 1\n"
        assertEqualCode(expected, astStatementYieldValue1())
    }

    @Test
    fun testStatementYieldValue1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementYieldValue1())
    }

    @Test
    fun testStatementYieldValue1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementYieldValue1())
    }
    private fun astStatementYieldFrom1() =
        pyProgram(
            l1c0c12,
            body = listOf(
                Py.ExprStmt(
                    l1c0c12,
                    value = Py.YieldFrom(l1c0c12, value = Py.Name(l1c11, id = PyIdentifierName("x"))),
                ),
            ),
        )

    @Test
    fun testStatementYieldFrom1Out() {
        val expected = "yield from x\n"
        assertEqualCode(expected, astStatementYieldFrom1())
    }

    @Test
    fun testStatementYieldFrom1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementYieldFrom1())
    }

    @Test
    fun testStatementYieldFrom1Imports() {
        val expected = setOf("x")
        assertImports(expected, astStatementYieldFrom1())
    }
    private fun astStatementAssert1() =
        pyProgram(l1c0c8, body = listOf(Py.Assert(l1c0c8, test = Py.Num(l1c7, n = 1))))

    @Test
    fun testStatementAssert1Out() {
        val expected = "assert 1\n"
        assertEqualCode(expected, astStatementAssert1())
    }

    @Test
    fun testStatementAssert1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementAssert1())
    }

    @Test
    fun testStatementAssert1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementAssert1())
    }
    private fun astStatementAssertMsg1() =
        pyProgram(
            l1c0c15,
            body = listOf(Py.Assert(l1c0c15, test = Py.Num(l1c7, n = 1), msg = Py.Str(l1c10c15, s = "foo"))),
        )

    @Test
    fun testStatementAssertMsg1Out() {
        val expected = "assert 1, 'foo'\n"
        assertEqualCode(expected, astStatementAssertMsg1())
    }

    @Test
    fun testStatementAssertMsg1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementAssertMsg1())
    }

    @Test
    fun testStatementAssertMsg1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementAssertMsg1())
    }
    private fun astStatementRaise1() =
        pyProgram(l1c0c5, body = listOf(Py.Raise(l1c0c5)))

    @Test
    fun testStatementRaise1Out() {
        val expected = "raise\n"
        assertEqualCode(expected, astStatementRaise1())
    }

    @Test
    fun testStatementRaise1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementRaise1())
    }

    @Test
    fun testStatementRaise1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementRaise1())
    }
    private fun astStatementRaiseName1() =
        pyProgram(l1c0c7, body = listOf(Py.Raise(l1c0c7, exc = Py.Name(l1c6, id = PyIdentifierName("x")))))

    @Test
    fun testStatementRaiseName1Out() {
        val expected = "raise x\n"
        assertEqualCode(expected, astStatementRaiseName1())
    }

    @Test
    fun testStatementRaiseName1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementRaiseName1())
    }

    @Test
    fun testStatementRaiseName1Imports() {
        val expected = setOf("x")
        assertImports(expected, astStatementRaiseName1())
    }
    private fun astStatementRaiseNameFrom1() =
        pyProgram(
            l1c0c14,
            body = listOf(
                Py.Raise(
                    l1c0c14,
                    exc = Py.Name(l1c6, id = PyIdentifierName("x")),
                    cause = Py.Name(l1c13, id = PyIdentifierName("y")),
                ),
            ),
        )

    @Test
    fun testStatementRaiseNameFrom1Out() {
        val expected = "raise x from y\n"
        assertEqualCode(expected, astStatementRaiseNameFrom1())
    }

    @Test
    fun testStatementRaiseNameFrom1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementRaiseNameFrom1())
    }

    @Test
    fun testStatementRaiseNameFrom1Imports() {
        val expected = setOf("x", "y")
        assertImports(expected, astStatementRaiseNameFrom1())
    }
    private fun astStatementImport1() =
        pyProgram(
            l1c0c8,
            body = listOf(
                Py.Import(
                    l1c0c8,
                    names = listOf(
                        Py.ImportAlias(
                            l1c7,
                            name = Py.ImportDotted(l1c7, module = PyDottedIdentifier("x")),
                            asname = null,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementImport1Out() {
        val expected = "import x\n"
        assertEqualCode(expected, astStatementImport1())
    }

    @Test
    fun testStatementImport1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementImport1())
    }

    @Test
    fun testStatementImport1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementImport1())
    }
    private fun astStatementImport21() =
        pyProgram(
            l1c0c11,
            body = listOf(
                Py.Import(
                    l1c0c11,
                    names = listOf(
                        Py.ImportAlias(
                            l1c7,
                            name = Py.ImportDotted(l1c7, module = PyDottedIdentifier("x")),
                            asname = null,
                        ),
                        Py.ImportAlias(
                            l1c10,
                            name = Py.ImportDotted(l1c10, module = PyDottedIdentifier("y")),
                            asname = null,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementImport21Out() {
        val expected = "import x, y\n"
        assertEqualCode(expected, astStatementImport21())
    }

    @Test
    fun testStatementImport21Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementImport21())
    }

    @Test
    fun testStatementImport21Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementImport21())
    }
    private fun astStatementImportDotted1() =
        pyProgram(
            l1c0c10,
            body = listOf(
                Py.Import(
                    l1c0c10,
                    names = listOf(
                        Py.ImportAlias(
                            l1c7c10,
                            name = Py.ImportDotted(l1c7c10, module = PyDottedIdentifier("x.y")),
                            asname = null,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementImportDotted1Out() {
        val expected = "import x.y\n"
        assertEqualCode(expected, astStatementImportDotted1())
    }

    @Test
    fun testStatementImportDotted1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementImportDotted1())
    }

    @Test
    fun testStatementImportDotted1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementImportDotted1())
    }
    private fun astStatementImportAs1() =
        pyProgram(
            l1c0c13,
            body = listOf(
                Py.Import(
                    l1c0c13,
                    names = listOf(
                        Py.ImportAlias(
                            l1c7c13,
                            name = Py.ImportDotted(l1c7c13, module = PyDottedIdentifier("x")),
                            asname = Py.Identifier(p0, id = PyIdentifierName("y")),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementImportAs1Out() {
        val expected = "import x as y\n"
        assertEqualCode(expected, astStatementImportAs1())
    }

    @Test
    fun testStatementImportAs1Exports() {
        val expected = setOf("y")
        assertExports(expected, astStatementImportAs1())
    }

    @Test
    fun testStatementImportAs1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementImportAs1())
    }
    private fun astStatementFromImport1() =
        pyProgram(
            l1c0c15,
            body = listOf(
                Py.ImportFrom(
                    l1c0c15,
                    module = Py.ImportDotted(l1c0c15, module = PyDottedIdentifier("x")),
                    names = listOf(
                        Py.ImportAlias(
                            l1c14,
                            name = Py.ImportDotted(l1c14, module = PyDottedIdentifier("y")),
                            asname = null,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementFromImport1Out() {
        val expected = "from x import y\n"
        assertEqualCode(expected, astStatementFromImport1())
    }

    @Test
    fun testStatementFromImport1Exports() {
        val expected = setOf("y")
        assertExports(expected, astStatementFromImport1())
    }

    @Test
    fun testStatementFromImport1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementFromImport1())
    }
    private fun astStatementFromImportDotted1() =
        pyProgram(
            l1c0c17,
            body = listOf(
                Py.ImportFrom(
                    l1c0c17,
                    module = Py.ImportDotted(l1c0c17, module = PyDottedIdentifier("x.y")),
                    names = listOf(
                        Py.ImportAlias(
                            l1c16,
                            name = Py.ImportDotted(l1c16, module = PyDottedIdentifier("z")),
                            asname = null,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementFromImportDotted1Out() {
        val expected = "from x.y import z\n"
        assertEqualCode(expected, astStatementFromImportDotted1())
    }

    @Test
    fun testStatementFromImportDotted1Exports() {
        val expected = setOf("z")
        assertExports(expected, astStatementFromImportDotted1())
    }

    @Test
    fun testStatementFromImportDotted1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementFromImportDotted1())
    }
    private fun astStatementFromImportRelative1() =
        pyProgram(
            l1c0c19,
            body = listOf(
                Py.ImportFrom(
                    l1c0c19,
                    module = Py.ImportDotted(l1c0c19, module = PyDottedIdentifier("..x.y")),
                    names = listOf(
                        Py.ImportAlias(
                            l1c18,
                            name = Py.ImportDotted(l1c18, module = PyDottedIdentifier("z")),
                            asname = null,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementFromImportRelative1Out() {
        val expected = "from ..x.y import z\n"
        assertEqualCode(expected, astStatementFromImportRelative1())
    }

    @Test
    fun testStatementFromImportRelative1Exports() {
        val expected = setOf("z")
        assertExports(expected, astStatementFromImportRelative1())
    }

    @Test
    fun testStatementFromImportRelative1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementFromImportRelative1())
    }
    private fun astStatementGlobal1() =
        pyProgram(
            l1c0c8,
            body = listOf(Py.Global(l1c0c8, names = listOf(Py.Identifier(p0, id = PyIdentifierName("x"))))),
        )

    @Test
    fun testStatementGlobal1Out() {
        val expected = "global x\n"
        assertEqualCode(expected, astStatementGlobal1())
    }

    @Test
    fun testStatementGlobal1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementGlobal1())
    }

    @Test
    fun testStatementGlobal1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementGlobal1())
    }
    private fun astStatementGlobal21() =
        pyProgram(
            l1c0c11,
            body = listOf(
                Py.Global(
                    l1c0c11,
                    names = listOf(
                        Py.Identifier(p0, id = PyIdentifierName("x")),
                        Py.Identifier(p0, id = PyIdentifierName("y")),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementGlobal21Out() {
        val expected = "global x, y\n"
        assertEqualCode(expected, astStatementGlobal21())
    }

    @Test
    fun testStatementGlobal21Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementGlobal21())
    }

    @Test
    fun testStatementGlobal21Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementGlobal21())
    }
    private fun astStatementNonlocal1() =
        pyProgram(
            l1c0c10,
            body = listOf(Py.Nonlocal(l1c0c10, names = listOf(Py.Identifier(p0, id = PyIdentifierName("x"))))),
        )

    @Test
    fun testStatementNonlocal1Out() {
        val expected = "nonlocal x\n"
        assertEqualCode(expected, astStatementNonlocal1())
    }

    @Test
    fun testStatementNonlocal1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementNonlocal1())
    }

    @Test
    fun testStatementNonlocal1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementNonlocal1())
    }
    private fun astStatementNonlocal21() =
        pyProgram(
            l1c0c13,
            body = listOf(
                Py.Nonlocal(
                    l1c0c13,
                    names = listOf(
                        Py.Identifier(p0, id = PyIdentifierName("x")),
                        Py.Identifier(p0, id = PyIdentifierName("y")),
                    ),
                ),
            ),
        )

    @Test
    fun testStatementNonlocal21Out() {
        val expected = "nonlocal x, y\n"
        assertEqualCode(expected, astStatementNonlocal21())
    }

    @Test
    fun testStatementNonlocal21Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementNonlocal21())
    }

    @Test
    fun testStatementNonlocal21Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementNonlocal21())
    }
    private fun astStatementIf1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.If(
                    l1c0l2c6,
                    elifs = listOf(),
                    orElse = listOf(),
                    test = Py.Name(l1c3, id = PyIdentifierName("x")),
                    body = listOf(Py.Pass(l2c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementIf1Out() {
        val expected = "if x:\n    pass\n"
        assertEqualCode(expected, astStatementIf1())
    }

    @Test
    fun testStatementIf1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementIf1())
    }

    @Test
    fun testStatementIf1Imports() {
        val expected = setOf("x")
        assertImports(expected, astStatementIf1())
    }
    private fun astStatementIfElse1() =
        pyProgram(
            l1c0l4c6,
            body = listOf(
                Py.If(
                    l1c0l4c6,
                    elifs = listOf(),
                    orElse = listOf(Py.Pass(l4c2c6)),
                    test = Py.Name(l1c3, id = PyIdentifierName("x")),
                    body = listOf(Py.Pass(l2c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementIfElse1Out() {
        val expected = "if x:\n    pass\nelse:\n    pass\n"
        assertEqualCode(expected, astStatementIfElse1())
    }

    @Test
    fun testStatementIfElse1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementIfElse1())
    }

    @Test
    fun testStatementIfElse1Imports() {
        val expected = setOf("x")
        assertImports(expected, astStatementIfElse1())
    }
    private fun astStatementIfElifElse1() =
        pyProgram(
            l1c0l6c6,
            body = listOf(
                Py.If(
                    l1c0l6c6,
                    elifs = listOf(
                        Py.Elif(
                            l3c0l6c6,
                            test = Py.Name(l3c5, id = PyIdentifierName("y")),
                            body = listOf(Py.Pass(l4c2c6)),
                        ),
                    ),
                    orElse = listOf(Py.Pass(l6c2c6)),
                    test = Py.Name(l1c3, id = PyIdentifierName("x")),
                    body = listOf(Py.Pass(l2c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementIfElifElse1Out() {
        val expected = "if x:\n    pass\nelif y:\n    pass\nelse:\n    pass\n"
        assertEqualCode(expected, astStatementIfElifElse1())
    }

    @Test
    fun testStatementIfElifElse1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementIfElifElse1())
    }

    @Test
    fun testStatementIfElifElse1Imports() {
        val expected = setOf("x", "y")
        assertImports(expected, astStatementIfElifElse1())
    }
    private fun astStatementWhile1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.While(
                    l1c0l2c6,
                    test = Py.Name(l1c6, id = PyIdentifierName("x")),
                    body = listOf(Py.Pass(l2c2c6)),
                    orElse = listOf(),
                ),
            ),
        )

    @Test
    fun testStatementWhile1Out() {
        val expected = "while x:\n    pass\n"
        assertEqualCode(expected, astStatementWhile1())
    }

    @Test
    fun testStatementWhile1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementWhile1())
    }

    @Test
    fun testStatementWhile1Imports() {
        val expected = setOf("x")
        assertImports(expected, astStatementWhile1())
    }
    private fun astStatementWhileElse1() =
        pyProgram(
            l1c0l4c6,
            body = listOf(
                Py.While(
                    l1c0l4c6,
                    test = Py.Name(l1c6, id = PyIdentifierName("x")),
                    body = listOf(Py.Pass(l2c2c6)),
                    orElse = listOf(Py.Pass(l4c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementWhileElse1Out() {
        val expected = "while x:\n    pass\nelse:\n    pass\n"
        assertEqualCode(expected, astStatementWhileElse1())
    }

    @Test
    fun testStatementWhileElse1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementWhileElse1())
    }

    @Test
    fun testStatementWhileElse1Imports() {
        val expected = setOf("x")
        assertImports(expected, astStatementWhileElse1())
    }
    private fun astStatementFor1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.For(
                    l1c0l2c6,
                    target = Py.Name(l1c4, id = PyIdentifierName("x")),
                    iter = Py.Name(l1c9, id = PyIdentifierName("y")),
                    body = listOf(Py.Pass(l2c2c6)),
                    orElse = listOf(),
                ),
            ),
        )

    @Test
    fun testStatementFor1Out() {
        val expected = "for x in y:\n    pass\n"
        assertEqualCode(expected, astStatementFor1())
    }

    @Test
    fun testStatementFor1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementFor1())
    }

    @Test
    fun testStatementFor1Imports() {
        val expected = setOf("y")
        assertImports(expected, astStatementFor1())
    }
    private fun astStatementForElse1() =
        pyProgram(
            l1c0l4c6,
            body = listOf(
                Py.For(
                    l1c0l4c6,
                    target = Py.Name(l1c4, id = PyIdentifierName("x")),
                    iter = Py.Name(l1c9, id = PyIdentifierName("y")),
                    body = listOf(Py.Pass(l2c2c6)),
                    orElse = listOf(Py.Pass(l4c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementForElse1Out() {
        val expected = "for x in y:\n    pass\nelse:\n    pass\n"
        assertEqualCode(expected, astStatementForElse1())
    }

    @Test
    fun testStatementForElse1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementForElse1())
    }

    @Test
    fun testStatementForElse1Imports() {
        val expected = setOf("y")
        assertImports(expected, astStatementForElse1())
    }
    private fun astStatementAsyncFor1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.For(
                    l1c0l2c6,
                    async = true,
                    target = Py.Name(l1c10, id = PyIdentifierName("x")),
                    iter = Py.Name(l1c15, id = PyIdentifierName("y")),
                    body = listOf(Py.Pass(l2c2c6)),
                    orElse = listOf(),
                ),
            ),
        )

    @Test
    fun testStatementAsyncFor1Out() {
        val expected = "async for x in y:\n    pass\n"
        assertEqualCode(expected, astStatementAsyncFor1())
    }

    @Test
    fun testStatementAsyncFor1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementAsyncFor1())
    }

    @Test
    fun testStatementAsyncFor1Imports() {
        val expected = setOf("y")
        assertImports(expected, astStatementAsyncFor1())
    }
    private fun astStatementAsyncForElse1() =
        pyProgram(
            l1c0l4c6,
            body = listOf(
                Py.For(
                    l1c0l4c6,
                    async = true,
                    target = Py.Name(l1c10, id = PyIdentifierName("x")),
                    iter = Py.Name(l1c15, id = PyIdentifierName("y")),
                    body = listOf(Py.Pass(l2c2c6)),
                    orElse = listOf(Py.Pass(l4c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementAsyncForElse1Out() {
        val expected = "async for x in y:\n    pass\nelse:\n    pass\n"
        assertEqualCode(expected, astStatementAsyncForElse1())
    }

    @Test
    fun testStatementAsyncForElse1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementAsyncForElse1())
    }

    @Test
    fun testStatementAsyncForElse1Imports() {
        val expected = setOf("y")
        assertImports(expected, astStatementAsyncForElse1())
    }
    private fun astStatementTryExcept1() =
        pyProgram(
            l1c0l4c6,
            body = listOf(
                Py.Try(
                    l1c0l4c6,
                    body = listOf(Py.Pass(l2c2c6)),
                    handlers = listOf(Py.ExceptHandler(l3c0l4c6, name = null, body = listOf(Py.Pass(l4c2c6)))),
                    orElse = listOf(),
                    finalbody = listOf(),
                ),
            ),
        )

    @Test
    fun testStatementTryExcept1Out() {
        val expected = "try:\n    pass\nexcept:\n    pass\n"
        assertEqualCode(expected, astStatementTryExcept1())
    }

    @Test
    fun testStatementTryExcept1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementTryExcept1())
    }

    @Test
    fun testStatementTryExcept1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementTryExcept1())
    }
    private fun astStatementTryExceptFinally1() =
        pyProgram(
            l1c0l6c6,
            body = listOf(
                Py.Try(
                    l1c0l6c6,
                    body = listOf(Py.Pass(l2c2c6)),
                    handlers = listOf(Py.ExceptHandler(l3c0l4c6, name = null, body = listOf(Py.Pass(l4c2c6)))),
                    orElse = listOf(),
                    finalbody = listOf(Py.Pass(l6c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementTryExceptFinally1Out() {
        val expected = "try:\n    pass\nexcept:\n    pass\nfinally:\n    pass\n"
        assertEqualCode(expected, astStatementTryExceptFinally1())
    }

    @Test
    fun testStatementTryExceptFinally1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementTryExceptFinally1())
    }

    @Test
    fun testStatementTryExceptFinally1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementTryExceptFinally1())
    }
    private fun astStatementTryFinally1() =
        pyProgram(
            l1c0l4c6,
            body = listOf(
                Py.Try(
                    l1c0l4c6,
                    body = listOf(Py.Pass(l2c2c6)),
                    handlers = listOf(),
                    orElse = listOf(),
                    finalbody = listOf(Py.Pass(l4c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementTryFinally1Out() {
        val expected = "try:\n    pass\nfinally:\n    pass\n"
        assertEqualCode(expected, astStatementTryFinally1())
    }

    @Test
    fun testStatementTryFinally1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementTryFinally1())
    }

    @Test
    fun testStatementTryFinally1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementTryFinally1())
    }
    private fun astStatementTryElse1() =
        pyProgram(
            l1c0l6c6,
            body = listOf(
                Py.Try(
                    l1c0l6c6,
                    body = listOf(Py.Pass(l2c2c6)),
                    handlers = listOf(Py.ExceptHandler(l3c0l4c6, name = null, body = listOf(Py.Pass(l4c2c6)))),
                    orElse = listOf(Py.Pass(l6c2c6)),
                    finalbody = listOf(),
                ),
            ),
        )

    @Test
    fun testStatementTryElse1Out() {
        val expected = "try:\n    pass\nexcept:\n    pass\nelse:\n    pass\n"
        assertEqualCode(expected, astStatementTryElse1())
    }

    @Test
    fun testStatementTryElse1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementTryElse1())
    }

    @Test
    fun testStatementTryElse1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementTryElse1())
    }
    private fun astStatementTryElseFinally1() =
        pyProgram(
            l1c0l8c6,
            body = listOf(
                Py.Try(
                    l1c0l8c6,
                    body = listOf(Py.Pass(l2c2c6)),
                    handlers = listOf(Py.ExceptHandler(l3c0l4c6, name = null, body = listOf(Py.Pass(l4c2c6)))),
                    orElse = listOf(Py.Pass(l6c2c6)),
                    finalbody = listOf(Py.Pass(l8c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementTryElseFinally1Out() {
        val expected = "try:\n    pass\nexcept:\n    pass\nelse:\n    pass\nfinally:\n    pass\n"
        assertEqualCode(expected, astStatementTryElseFinally1())
    }

    @Test
    fun testStatementTryElseFinally1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementTryElseFinally1())
    }

    @Test
    fun testStatementTryElseFinally1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementTryElseFinally1())
    }
    private fun astStatementTryExcept21() =
        pyProgram(
            l1c0l6c6,
            body = listOf(
                Py.Try(
                    l1c0l6c6,
                    body = listOf(Py.Pass(l2c2c6)),
                    handlers = listOf(
                        Py.ExceptHandler(l3c0l4c6, name = null, body = listOf(Py.Pass(l4c2c6))),
                        Py.ExceptHandler(l5c0l6c6, name = null, body = listOf(Py.Pass(l6c2c6))),
                    ),
                    orElse = listOf(),
                    finalbody = listOf(),
                ),
            ),
        )

    @Test
    fun testStatementTryExcept21Out() {
        val expected = "try:\n    pass\nexcept:\n    pass\nexcept:\n    pass\n"
        assertEqualCode(expected, astStatementTryExcept21())
    }

    @Test
    fun testStatementTryExcept21Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementTryExcept21())
    }

    @Test
    fun testStatementTryExcept21Imports() {
        val expected = setOf<String>()
        assertImports(expected, astStatementTryExcept21())
    }
    private fun astStatementTryExceptException1() =
        pyProgram(
            l1c0l4c6,
            body = listOf(
                Py.Try(
                    l1c0l4c6,
                    body = listOf(Py.Pass(l2c2c6)),
                    handlers = listOf(
                        Py.ExceptHandler(
                            l3c0l4c6,
                            type = Py.Name(l3c7, id = PyIdentifierName("x")),
                            name = null,
                            body = listOf(Py.Pass(l4c2c6)),
                        ),
                    ),
                    orElse = listOf(),
                    finalbody = listOf(),
                ),
            ),
        )

    @Test
    fun testStatementTryExceptException1Out() {
        val expected = "try:\n    pass\nexcept x:\n    pass\n"
        assertEqualCode(expected, astStatementTryExceptException1())
    }

    @Test
    fun testStatementTryExceptException1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementTryExceptException1())
    }

    @Test
    fun testStatementTryExceptException1Imports() {
        val expected = setOf("x")
        assertImports(expected, astStatementTryExceptException1())
    }
    private fun astStatementTryExceptExceptionAs1() =
        pyProgram(
            l1c0l4c6,
            body = listOf(
                Py.Try(
                    l1c0l4c6,
                    body = listOf(Py.Pass(l2c2c6)),
                    handlers = listOf(
                        Py.ExceptHandler(
                            l3c0l4c6,
                            type = Py.Name(l3c7, id = PyIdentifierName("x")),
                            name = Py.Identifier(p0, id = PyIdentifierName("y")),
                            body = listOf(Py.Pass(l4c2c6)),
                        ),
                    ),
                    orElse = listOf(),
                    finalbody = listOf(),
                ),
            ),
        )

    @Test
    fun testStatementTryExceptExceptionAs1Out() {
        val expected = "try:\n    pass\nexcept x as y:\n    pass\n"
        assertEqualCode(expected, astStatementTryExceptExceptionAs1())
    }

    @Test
    fun testStatementTryExceptExceptionAs1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementTryExceptExceptionAs1())
    }

    @Test
    fun testStatementTryExceptExceptionAs1Imports() {
        val expected = setOf("x")
        assertImports(expected, astStatementTryExceptExceptionAs1())
    }
    private fun astStatementWith1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.With(
                    l1c0l2c6,
                    items = listOf(Py.WithItem(p0, contextExpr = Py.Name(l1c5, id = PyIdentifierName("x")))),
                    body = listOf(Py.Pass(l2c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementWith1Out() {
        val expected = "with x:\n    pass\n"
        assertEqualCode(expected, astStatementWith1())
    }

    @Test
    fun testStatementWith1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementWith1())
    }

    @Test
    fun testStatementWith1Imports() {
        val expected = setOf("x")
        assertImports(expected, astStatementWith1())
    }
    private fun astStatementWithAs1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.With(
                    l1c0l2c6,
                    items = listOf(
                        Py.WithItem(
                            p0,
                            contextExpr = Py.Name(l1c5, id = PyIdentifierName("x")),
                            optionalVars = Py.Name(l1c10, id = PyIdentifierName("y")),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementWithAs1Out() {
        val expected = "with x as y:\n    pass\n"
        assertEqualCode(expected, astStatementWithAs1())
    }

    @Test
    fun testStatementWithAs1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementWithAs1())
    }

    @Test
    fun testStatementWithAs1Imports() {
        val expected = setOf("x")
        assertImports(expected, astStatementWithAs1())
    }
    private fun astStatementWithAs21() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.With(
                    l1c0l2c6,
                    items = listOf(
                        Py.WithItem(
                            p0,
                            contextExpr = Py.Name(l1c5, id = PyIdentifierName("x")),
                            optionalVars = Py.Name(l1c10, id = PyIdentifierName("y")),
                        ),
                        Py.WithItem(
                            p0,
                            contextExpr = Py.Name(l1c13, id = PyIdentifierName("a")),
                            optionalVars = Py.Name(l1c18, id = PyIdentifierName("b")),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementWithAs21Out() {
        val expected = "with x as y, a as b:\n    pass\n"
        assertEqualCode(expected, astStatementWithAs21())
    }

    @Test
    fun testStatementWithAs21Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementWithAs21())
    }

    @Test
    fun testStatementWithAs21Imports() {
        val expected = setOf("x", "a")
        assertImports(expected, astStatementWithAs21())
    }
    private fun astStatementWithAsync1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.With(
                    l1c0l2c6,
                    async = true,
                    items = listOf(Py.WithItem(p0, contextExpr = Py.Name(l1c11, id = PyIdentifierName("x")))),
                    body = listOf(Py.Pass(l2c2c6)),
                ),
            ),
        )

    @Test
    fun testStatementWithAsync1Out() {
        val expected = "async with x:\n    pass\n"
        assertEqualCode(expected, astStatementWithAsync1())
    }

    @Test
    fun testStatementWithAsync1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStatementWithAsync1())
    }

    @Test
    fun testStatementWithAsync1Imports() {
        val expected = setOf("x")
        assertImports(expected, astStatementWithAsync1())
    }
    private fun astFunction1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(p0, args = listOf()),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testFunction1Out() {
        val expected = "def foo():\n    pass\n"
        assertEqualCode(expected, astFunction1())
    }

    @Test
    fun testFunction1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunction1())
    }

    @Test
    fun testFunction1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunction1())
    }
    private fun astAsyncFunction1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    async = true,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(p0, args = listOf()),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testAsyncFunction1Out() {
        val expected = "async def foo():\n    pass\n"
        assertEqualCode(expected, astAsyncFunction1())
    }

    @Test
    fun testAsyncFunction1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astAsyncFunction1())
    }

    @Test
    fun testAsyncFunction1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astAsyncFunction1())
    }
    private fun astFunctionDecorate1() =
        pyProgram(
            l1c0l3c6,
            body = listOf(
                Py.FunctionDef(
                    l2c0l3c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(p0, args = listOf()),
                    body = listOf(Py.Pass(l3c2c6)),
                    decoratorList = listOf(
                        Py.Decorator(
                            l1c1,
                            name = listOf(Py.Identifier(p0, id = PyIdentifierName("x"))),
                            args = listOf(),
                            called = false,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testFunctionDecorate1Out() {
        val expected = "@x\ndef foo():\n    pass\n"
        assertEqualCode(expected, astFunctionDecorate1())
    }

    @Test
    fun testFunctionDecorate1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionDecorate1())
    }

    @Test
    fun testFunctionDecorate1Imports() {
        val expected = setOf("x")
        assertImports(expected, astFunctionDecorate1())
    }
    private fun astFunctionDecorate21() =
        pyProgram(
            l1c0l4c6,
            body = listOf(
                Py.FunctionDef(
                    l3c0l4c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(p0, args = listOf()),
                    body = listOf(Py.Pass(l4c2c6)),
                    decoratorList = listOf(
                        Py.Decorator(
                            l1c1,
                            name = listOf(Py.Identifier(p0, id = PyIdentifierName("x"))),
                            args = listOf(),
                            called = false,
                        ),
                        Py.Decorator(
                            l2c1,
                            name = listOf(Py.Identifier(p0, id = PyIdentifierName("y"))),
                            args = listOf(),
                            called = false,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testFunctionDecorate21Out() {
        val expected = "@x\n@y\ndef foo():\n    pass\n"
        assertEqualCode(expected, astFunctionDecorate21())
    }

    @Test
    fun testFunctionDecorate21Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionDecorate21())
    }

    @Test
    fun testFunctionDecorate21Imports() {
        val expected = setOf("x", "y")
        assertImports(expected, astFunctionDecorate21())
    }
    private fun astFunctionDecorate2() =
        pyProgram(
            l1c0l3c6,
            body = listOf(
                Py.FunctionDef(
                    l2c0l3c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(p0, args = listOf()),
                    body = listOf(Py.Pass(l3c2c6)),
                    decoratorList = listOf(
                        Py.Decorator(
                            l1c1c4,
                            name = listOf(
                                Py.Identifier(p0, id = PyIdentifierName("x")),
                                Py.Identifier(p0, id = PyIdentifierName("y")),
                            ),
                            args = listOf(),
                            called = false,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testFunctionDecorate2Out() {
        val expected = "@x.y\ndef foo():\n    pass\n"
        assertEqualCode(expected, astFunctionDecorate2())
    }

    @Test
    fun testFunctionDecorate2Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionDecorate2())
    }

    @Test
    fun testFunctionDecorate2Imports() {
        val expected = setOf("x")
        assertImports(expected, astFunctionDecorate2())
    }
    private fun astFunctionDecorate3() =
        pyProgram(
            l1c0l3c6,
            body = listOf(
                Py.FunctionDef(
                    l2c0l3c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(p0, args = listOf()),
                    body = listOf(Py.Pass(l3c2c6)),
                    decoratorList = listOf(
                        Py.Decorator(
                            l1c1c7,
                            name = listOf(
                                Py.Identifier(p0, id = PyIdentifierName("x")),
                                Py.Identifier(p0, id = PyIdentifierName("y")),
                            ),
                            args = listOf(
                                Py.CallArg(
                                    l1c5,
                                    arg = null,
                                    value = Py.Name(l1c5, id = PyIdentifierName("z")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                            ),
                            called = true,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testFunctionDecorate3Out() {
        val expected = "@x.y(z)\ndef foo():\n    pass\n"
        assertEqualCode(expected, astFunctionDecorate3())
    }

    @Test
    fun testFunctionDecorate3Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionDecorate3())
    }

    @Test
    fun testFunctionDecorate3Imports() {
        val expected = setOf("x", "z")
        assertImports(expected, astFunctionDecorate3())
    }
    private fun astFunctionReturn1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(p0, args = listOf()),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                    returns = Py.Name(l1c13, id = PyIdentifierName("x")),
                ),
            ),
        )

    @Test
    fun testFunctionReturn1Out() {
        val expected = "def foo() -> x:\n    pass\n"
        assertEqualCode(expected, astFunctionReturn1())
    }

    @Test
    fun testFunctionReturn1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionReturn1())
    }

    @Test
    fun testFunctionReturn1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionReturn1())
    }
    private fun astFunctionPosArg1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(
                        p0,
                        args = listOf(
                            Py.Arg(
                                l1c8,
                                arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testFunctionPosArg1Out() {
        val expected = "def foo(x):\n    pass\n"
        assertEqualCode(expected, astFunctionPosArg1())
    }

    @Test
    fun testFunctionPosArg1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionPosArg1())
    }

    @Test
    fun testFunctionPosArg1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionPosArg1())
    }
    private fun astFunctionPosArgAnnotate1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(
                        p0,
                        args = listOf(
                            Py.Arg(
                                l1c8c12,
                                arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                annotation = Py.Name(l1c11, id = PyIdentifierName("y")),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testFunctionPosArgAnnotate1Out() {
        val expected = "def foo(x: y):\n    pass\n"
        assertEqualCode(expected, astFunctionPosArgAnnotate1())
    }

    @Test
    fun testFunctionPosArgAnnotate1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionPosArgAnnotate1())
    }

    @Test
    fun testFunctionPosArgAnnotate1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionPosArgAnnotate1())
    }
    private fun astFunctionPosArg21() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(
                        p0,
                        args = listOf(
                            Py.Arg(
                                l1c8,
                                arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.Arg(
                                l1c11,
                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testFunctionPosArg21Out() {
        val expected = "def foo(x, y):\n    pass\n"
        assertEqualCode(expected, astFunctionPosArg21())
    }

    @Test
    fun testFunctionPosArg21Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionPosArg21())
    }

    @Test
    fun testFunctionPosArg21Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionPosArg21())
    }
    private fun astFunctionVarArg1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(
                        p0,
                        args = listOf(
                            Py.Arg(
                                l1c8,
                                arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.Arg(
                                l1c12,
                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                prefix = Py.ArgPrefix.Star,
                            ),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testFunctionVarArg1Out() {
        val expected = "def foo(x, *y):\n    pass\n"
        assertEqualCode(expected, astFunctionVarArg1())
    }

    @Test
    fun testFunctionVarArg1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionVarArg1())
    }

    @Test
    fun testFunctionVarArg1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionVarArg1())
    }
    private fun astFunctionVarKwArg1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(
                        p0,
                        args = listOf(
                            Py.Arg(
                                l1c9,
                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                prefix = Py.ArgPrefix.Star,
                            ),
                            Py.Arg(
                                l1c14,
                                arg = Py.Identifier(p0, id = PyIdentifierName("z")),
                                prefix = Py.ArgPrefix.DoubleStar,
                            ),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testFunctionVarKwArg1Out() {
        val expected = "def foo(*y, **z):\n    pass\n"
        assertEqualCode(expected, astFunctionVarKwArg1())
    }

    @Test
    fun testFunctionVarKwArg1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionVarKwArg1())
    }

    @Test
    fun testFunctionVarKwArg1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionVarKwArg1())
    }
    private fun astFunctionVarKwArgAnnotate1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(
                        p0,
                        args = listOf(
                            Py.Arg(
                                l1c9c13,
                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                annotation = Py.Name(l1c12, id = PyIdentifierName("a")),
                                prefix = Py.ArgPrefix.Star,
                            ),
                            Py.Arg(
                                l1c17c21,
                                arg = Py.Identifier(p0, id = PyIdentifierName("z")),
                                annotation = Py.Name(l1c20, id = PyIdentifierName("a")),
                                prefix = Py.ArgPrefix.DoubleStar,
                            ),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testFunctionVarKwArgAnnotate1Out() {
        val expected = "def foo(*y: a, **z: a):\n    pass\n"
        assertEqualCode(expected, astFunctionVarKwArgAnnotate1())
    }

    @Test
    fun testFunctionVarKwArgAnnotate1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionVarKwArgAnnotate1())
    }

    @Test
    fun testFunctionVarKwArgAnnotate1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionVarKwArgAnnotate1())
    }
    private fun astFunctionKwArg1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(
                        p0,
                        args = listOf(
                            Py.Arg(
                                l1c8,
                                arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.Arg(
                                l1c11,
                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                defaultValue = Py.Num(l1c15, n = 1),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testFunctionKwArg1Out() {
        val expected = "def foo(x, y = 1):\n    pass\n"
        assertEqualCode(expected, astFunctionKwArg1())
    }

    @Test
    fun testFunctionKwArg1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionKwArg1())
    }

    @Test
    fun testFunctionKwArg1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionKwArg1())
    }
    private fun astFunctionKwArg2() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(
                        p0,
                        args = listOf(
                            Py.Arg(
                                l1c8,
                                arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.Arg(
                                l1c11,
                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                defaultValue = Py.Name(l1c15, id = PyIdentifierName("a")),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testFunctionKwArg2Out() {
        val expected = "def foo(x, y = a):\n    pass\n"
        assertEqualCode(expected, astFunctionKwArg2())
    }

    @Test
    fun testFunctionKwArg2Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionKwArg2())
    }

    @Test
    fun testFunctionKwArg2Imports() {
        val expected = setOf("a")
        assertImports(expected, astFunctionKwArg2())
    }
    private fun astFunctionKwArgVar1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(
                        p0,
                        args = listOf(
                            Py.Arg(
                                l1c8,
                                arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.Arg(
                                l1c11,
                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                defaultValue = Py.Num(l1c15, n = 1),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.Arg(
                                l1c19,
                                arg = Py.Identifier(p0, id = PyIdentifierName("z")),
                                prefix = Py.ArgPrefix.Star,
                            ),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testFunctionKwArgVar1Out() {
        val expected = "def foo(x, y = 1, *z):\n    pass\n"
        assertEqualCode(expected, astFunctionKwArgVar1())
    }

    @Test
    fun testFunctionKwArgVar1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionKwArgVar1())
    }

    @Test
    fun testFunctionKwArgVar1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionKwArgVar1())
    }
    private fun astFunctionKwArgKw1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.FunctionDef(
                    l1c0l2c6,
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    args = Py.Arguments(
                        p0,
                        args = listOf(
                            Py.Arg(
                                l1c8,
                                arg = Py.Identifier(p0, id = PyIdentifierName("x")),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.Arg(
                                l1c11,
                                arg = Py.Identifier(p0, id = PyIdentifierName("y")),
                                defaultValue = Py.Num(l1c15, n = 1),
                                prefix = Py.ArgPrefix.None,
                            ),
                            Py.Arg(
                                l1c20,
                                arg = Py.Identifier(p0, id = PyIdentifierName("z")),
                                prefix = Py.ArgPrefix.DoubleStar,
                            ),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testFunctionKwArgKw1Out() {
        val expected = "def foo(x, y = 1, **z):\n    pass\n"
        assertEqualCode(expected, astFunctionKwArgKw1())
    }

    @Test
    fun testFunctionKwArgKw1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astFunctionKwArgKw1())
    }

    @Test
    fun testFunctionKwArgKw1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astFunctionKwArgKw1())
    }
    private fun astClass1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.ClassDef(
                    l1c0l2c6,
                    args = listOf(),
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testClass1Out() {
        val expected = "class foo:\n    pass\n"
        assertEqualCode(expected, astClass1())
    }

    @Test
    fun testClass1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astClass1())
    }

    @Test
    fun testClass1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astClass1())
    }
    private fun astClassDecorate1() =
        pyProgram(
            l1c0l3c6,
            body = listOf(
                Py.ClassDef(
                    l2c0l3c6,
                    args = listOf(),
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    body = listOf(Py.Pass(l3c2c6)),
                    decoratorList = listOf(
                        Py.Decorator(
                            l1c1,
                            name = listOf(Py.Identifier(p0, id = PyIdentifierName("x"))),
                            args = listOf(),
                            called = false,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testClassDecorate1Out() {
        val expected = "@x\nclass foo:\n    pass\n"
        assertEqualCode(expected, astClassDecorate1())
    }

    @Test
    fun testClassDecorate1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astClassDecorate1())
    }

    @Test
    fun testClassDecorate1Imports() {
        val expected = setOf("x")
        assertImports(expected, astClassDecorate1())
    }
    private fun astClassDecorate21() =
        pyProgram(
            l1c0l4c6,
            body = listOf(
                Py.ClassDef(
                    l3c0l4c6,
                    args = listOf(),
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    body = listOf(Py.Pass(l4c2c6)),
                    decoratorList = listOf(
                        Py.Decorator(
                            l1c1,
                            name = listOf(Py.Identifier(p0, id = PyIdentifierName("x"))),
                            args = listOf(),
                            called = false,
                        ),
                        Py.Decorator(
                            l2c1,
                            name = listOf(Py.Identifier(p0, id = PyIdentifierName("y"))),
                            args = listOf(),
                            called = false,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testClassDecorate21Out() {
        val expected = "@x\n@y\nclass foo:\n    pass\n"
        assertEqualCode(expected, astClassDecorate21())
    }

    @Test
    fun testClassDecorate21Exports() {
        val expected = setOf("foo")
        assertExports(expected, astClassDecorate21())
    }

    @Test
    fun testClassDecorate21Imports() {
        val expected = setOf("x", "y")
        assertImports(expected, astClassDecorate21())
    }
    private fun astClassDecorate2() =
        pyProgram(
            l1c0l3c6,
            body = listOf(
                Py.ClassDef(
                    l2c0l3c6,
                    args = listOf(),
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    body = listOf(Py.Pass(l3c2c6)),
                    decoratorList = listOf(
                        Py.Decorator(
                            l1c1c4,
                            name = listOf(
                                Py.Identifier(p0, id = PyIdentifierName("x")),
                                Py.Identifier(p0, id = PyIdentifierName("y")),
                            ),
                            args = listOf(),
                            called = false,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testClassDecorate2Out() {
        val expected = "@x.y\nclass foo:\n    pass\n"
        assertEqualCode(expected, astClassDecorate2())
    }

    @Test
    fun testClassDecorate2Exports() {
        val expected = setOf("foo")
        assertExports(expected, astClassDecorate2())
    }

    @Test
    fun testClassDecorate2Imports() {
        val expected = setOf("x")
        assertImports(expected, astClassDecorate2())
    }
    private fun astClassDecorate3() =
        pyProgram(
            l1c0l3c6,
            body = listOf(
                Py.ClassDef(
                    l2c0l3c6,
                    args = listOf(),
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    body = listOf(Py.Pass(l3c2c6)),
                    decoratorList = listOf(
                        Py.Decorator(
                            l1c1c7,
                            name = listOf(
                                Py.Identifier(p0, id = PyIdentifierName("x")),
                                Py.Identifier(p0, id = PyIdentifierName("y")),
                            ),
                            args = listOf(
                                Py.CallArg(
                                    l1c5,
                                    arg = null,
                                    value = Py.Name(l1c5, id = PyIdentifierName("z")),
                                    prefix = Py.ArgPrefix.None,
                                ),
                            ),
                            called = true,
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testClassDecorate3Out() {
        val expected = "@x.y(z)\nclass foo:\n    pass\n"
        assertEqualCode(expected, astClassDecorate3())
    }

    @Test
    fun testClassDecorate3Exports() {
        val expected = setOf("foo")
        assertExports(expected, astClassDecorate3())
    }

    @Test
    fun testClassDecorate3Imports() {
        val expected = setOf("x", "z")
        assertImports(expected, astClassDecorate3())
    }
    private fun astClassBase1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.ClassDef(
                    l1c0l2c6,
                    args = listOf(
                        Py.CallArg(
                            l1c10,
                            arg = null,
                            value = Py.Name(l1c10, id = PyIdentifierName("x")),
                            prefix = Py.ArgPrefix.None,
                        ),
                    ),
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testClassBase1Out() {
        val expected = "class foo(x):\n    pass\n"
        assertEqualCode(expected, astClassBase1())
    }

    @Test
    fun testClassBase1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astClassBase1())
    }

    @Test
    fun testClassBase1Imports() {
        val expected = setOf("x")
        assertImports(expected, astClassBase1())
    }
    private fun astClassBases1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.ClassDef(
                    l1c0l2c6,
                    args = listOf(
                        Py.CallArg(
                            l1c10,
                            arg = null,
                            value = Py.Name(l1c10, id = PyIdentifierName("x")),
                            prefix = Py.ArgPrefix.None,
                        ),
                        Py.CallArg(
                            l1c13,
                            arg = null,
                            value = Py.Name(l1c13, id = PyIdentifierName("y")),
                            prefix = Py.ArgPrefix.None,
                        ),
                    ),
                    name = Py.Identifier(p0, id = PyIdentifierName("foo")),
                    body = listOf(Py.Pass(l2c2c6)),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testClassBases1Out() {
        val expected = "class foo(x, y):\n    pass\n"
        assertEqualCode(expected, astClassBases1())
    }

    @Test
    fun testClassBases1Exports() {
        val expected = setOf("foo")
        assertExports(expected, astClassBases1())
    }

    @Test
    fun testClassBases1Imports() {
        val expected = setOf("x", "y")
        assertImports(expected, astClassBases1())
    }
    private fun astLabeledBreakContextmgr1() =
        pyProgram(
            l1c0l8c22,
            body = listOf(
                Py.ClassDef(
                    l1c0l8c22,
                    args = listOf(
                        Py.CallArg(
                            l1c12c25,
                            arg = null,
                            value = Py.Name(l1c12c25, id = PyIdentifierName("BaseException")),
                            prefix = Py.ArgPrefix.None,
                        ),
                    ),
                    name = Py.Identifier(p0, id = PyIdentifierName("Label")),
                    body = listOf(
                        Py.Assign(
                            l2c2c16,
                            targets = listOf(Py.Name(l2c2c11, id = PyIdentifierName("__slots__"))),
                            value = Py.Tuple(l2c14c16, elts = listOf()),
                        ),
                        Py.FunctionDef(
                            l3c2l4c15,
                            name = Py.Identifier(p0, id = PyIdentifierName("__enter__")),
                            args = Py.Arguments(
                                p0,
                                args = listOf(
                                    Py.Arg(
                                        l3c16c20,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("self")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                ),
                            ),
                            body = listOf(
                                Py.Return(l4c4c15, value = Py.Name(l4c11c15, id = PyIdentifierName("self"))),
                            ),
                            decoratorList = listOf(),
                        ),
                        Py.FunctionDef(
                            l5c2l6c14,
                            name = Py.Identifier(p0, id = PyIdentifierName("brk")),
                            args = Py.Arguments(
                                p0,
                                args = listOf(
                                    Py.Arg(
                                        l5c10c14,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("self")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                ),
                            ),
                            body = listOf(
                                Py.Raise(l6c4c14, exc = Py.Name(l6c10c14, id = PyIdentifierName("self"))),
                            ),
                            decoratorList = listOf(),
                        ),
                        Py.FunctionDef(
                            l7c2l8c22,
                            name = Py.Identifier(p0, id = PyIdentifierName("__exit__")),
                            args = Py.Arguments(
                                p0,
                                args = listOf(
                                    Py.Arg(
                                        l7c15c19,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("self")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                    Py.Arg(
                                        l7c21c25,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("etyp")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                    Py.Arg(
                                        l7c27c30,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("exc")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                    Py.Arg(
                                        l7c32c34,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("tb")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                ),
                            ),
                            body = listOf(
                                Py.Return(
                                    l8c4c22,
                                    value = Py.BinExpr(
                                        l8c11c22,
                                        left = Py.Name(l8c11c14, id = PyIdentifierName("exc")),
                                        op = BinaryOpEnum.Is.atom(p0),
                                        right = Py.Name(l8c18c22, id = PyIdentifierName("self")),
                                    ),
                                ),
                            ),
                            decoratorList = listOf(),
                        ),
                    ),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testLabeledBreakContextmgr1Out() {
        val expected = joinLines(
            "class Label(BaseException):",
            "    __slots__ = ()",
            "    def __enter__(self):",
            "        return self",
            "    def brk(self):",
            "        raise self",
            "    def __exit__(self, etyp, exc, tb):",
            "        return exc is self",
        )
        assertEqualCode(expected, astLabeledBreakContextmgr1())
    }

    @Test
    fun testLabeledBreakContextmgr1Exports() {
        val expected = setOf("Label")
        assertExports(expected, astLabeledBreakContextmgr1())
    }

    @Test
    fun testLabeledBreakContextmgr1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astLabeledBreakContextmgr1())
    }
    private fun astLabeledStatement1() =
        pyProgram(
            l1c0l2c6,
            body = listOf(
                Py.With(
                    l1c0l2c6,
                    items = listOf(
                        Py.WithItem(
                            p0,
                            contextExpr = Py.Call(
                                l1c5c12,
                                func = Py.Name(l1c5c10, id = PyIdentifierName("Label")),
                                args = listOf(),
                            ),
                            optionalVars = Py.Name(l1c16c20, id = PyIdentifierName("name")),
                        ),
                    ),
                    body = listOf(Py.Pass(l2c2c6)),
                ),
            ),
        )

    @Test
    fun testLabeledStatement1Out() {
        val expected = "with Label() as name:\n    pass\n"
        assertEqualCode(expected, astLabeledStatement1())
    }

    @Test
    fun testLabeledStatement1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astLabeledStatement1())
    }

    @Test
    fun testLabeledStatement1Imports() {
        val expected = setOf("Label")
        assertImports(expected, astLabeledStatement1())
    }
    private fun astBoxedVal1() =
        pyProgram(
            l1c0l11c63,
            body = listOf(
                Py.ClassDef(
                    l1c0l11c63,
                    args = listOf(),
                    name = Py.Identifier(p0, id = PyIdentifierName("Box")),
                    body = listOf(
                        Py.Assign(
                            l2c2c32,
                            targets = listOf(Py.Name(l2c2c11, id = PyIdentifierName("__slots__"))),
                            value = Py.Tuple(
                                l2c14c32,
                                elts = listOf(Py.Str(l2c15c21, s = "type"), Py.Str(l2c23c31, s = "vector")),
                            ),
                        ),
                        Py.FunctionDef(
                            l3c2l5c24,
                            name = Py.Identifier(p0, id = PyIdentifierName("__init__")),
                            args = Py.Arguments(
                                p0,
                                args = listOf(
                                    Py.Arg(
                                        l3c15c19,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("self")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                    Py.Arg(
                                        l3c21c25,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("type")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                    Py.Arg(
                                        l3c27c33,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("vector")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                ),
                            ),
                            body = listOf(
                                Py.Assign(
                                    l4c4c20,
                                    targets = listOf(
                                        Py.Attribute(
                                            l4c4c13,
                                            value = Py.Name(l4c4c8, id = PyIdentifierName("self")),
                                            attr = Py.Identifier(p0, id = PyIdentifierName("type")),
                                        ),
                                    ),
                                    value = Py.Name(l4c16c20, id = PyIdentifierName("type")),
                                ),
                                Py.Assign(
                                    l5c4c24,
                                    targets = listOf(
                                        Py.Attribute(
                                            l5c4c15,
                                            value = Py.Name(l5c4c8, id = PyIdentifierName("self")),
                                            attr = Py.Identifier(p0, id = PyIdentifierName("vector")),
                                        ),
                                    ),
                                    value = Py.Name(l5c18c24, id = PyIdentifierName("vector")),
                                ),
                            ),
                            decoratorList = listOf(),
                        ),
                        Py.FunctionDef(
                            l6c2l7c47,
                            name = Py.Identifier(p0, id = PyIdentifierName("__getitem__")),
                            args = Py.Arguments(
                                p0,
                                args = listOf(
                                    Py.Arg(
                                        l6c18c22,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("self")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                    Py.Arg(
                                        l6c24c29,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("index")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                ),
                            ),
                            body = listOf(
                                Py.Return(
                                    l7c4c47,
                                    value = Py.Call(
                                        l7c11c47,
                                        func = Py.Name(l7c11c18, id = PyIdentifierName("getattr")),
                                        args = listOf(
                                            Py.CallArg(
                                                l7c19c23,
                                                arg = null,
                                                value = Py.Name(l7c19c23, id = PyIdentifierName("self")),
                                                prefix = Py.ArgPrefix.None,
                                            ),
                                            Py.CallArg(
                                                l7c25c46,
                                                arg = null,
                                                value = Py.Subscript(
                                                    l7c25c46,
                                                    value = Py.Attribute(
                                                        l7c25c39,
                                                        value = Py.Name(l7c25c29, id = PyIdentifierName("self")),
                                                        attr = Py.Identifier(p0, id = PyIdentifierName("__slots__")),
                                                    ),
                                                    slice = listOf(
                                                        Py.Name(l7c40c45, id = PyIdentifierName("index")),
                                                    ),
                                                ),
                                                prefix = Py.ArgPrefix.None,
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            decoratorList = listOf(),
                        ),
                        Py.FunctionDef(
                            l8c2l9c30,
                            name = Py.Identifier(p0, id = PyIdentifierName("__bool__")),
                            args = Py.Arguments(
                                p0,
                                args = listOf(
                                    Py.Arg(
                                        l8c15c19,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("self")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                ),
                            ),
                            body = listOf(
                                Py.Return(
                                    l9c4c30,
                                    value = Py.BinExpr(
                                        l9c11c30,
                                        left = Py.Attribute(
                                            l9c11c20,
                                            value = Py.Name(l9c11c15, id = PyIdentifierName("self")),
                                            attr = Py.Identifier(p0, id = PyIdentifierName("type")),
                                        ),
                                        op = BinaryOpEnum.NotEq.atom(p0),
                                        right = Py.Str(l9c24c30, s = "Void"),
                                    ),
                                ),
                            ),
                            decoratorList = listOf(),
                        ),
                        Py.FunctionDef(
                            l10c2l11c63,
                            name = Py.Identifier(p0, id = PyIdentifierName("__repr__")),
                            args = Py.Arguments(
                                p0,
                                args = listOf(
                                    Py.Arg(
                                        l10c15c19,
                                        arg = Py.Identifier(p0, id = PyIdentifierName("self")),
                                        prefix = Py.ArgPrefix.None,
                                    ),
                                ),
                            ),
                            body = listOf(
                                Py.Return(
                                    l11c4c63,
                                    value = Py.IfExpr(
                                        l11c11c63,
                                        test = Py.BinExpr(
                                            l11c21c40,
                                            left = Py.Attribute(
                                                l11c21c30,
                                                value = Py.Name(l11c21c25, id = PyIdentifierName("self")),
                                                attr = Py.Identifier(p0, id = PyIdentifierName("type")),
                                            ),
                                            op = BinaryOpEnum.Is.atom(p0),
                                            right = Py.Str(l11c34c40, s = "Void"),
                                        ),
                                        body = Py.Str(l11c11c17, s = "Void"),
                                        orElse = Py.Call(
                                            l11c46c63,
                                            func = Py.Name(l11c46c50, id = PyIdentifierName("repr")),
                                            args = listOf(
                                                Py.CallArg(
                                                    l11c51c62,
                                                    arg = null,
                                                    value = Py.Attribute(
                                                        l11c51c62,
                                                        value = Py.Name(l11c51c55, id = PyIdentifierName("self")),
                                                        attr = Py.Identifier(p0, id = PyIdentifierName("vector")),
                                                    ),
                                                    prefix = Py.ArgPrefix.None,
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            decoratorList = listOf(),
                        ),
                    ),
                    decoratorList = listOf(),
                ),
            ),
        )

    @Test
    fun testBoxedVal1Out() {
        val expected = joinLines(
            "class Box:",
            "    __slots__ = ('type', 'vector')",
            "    def __init__(self, type, vector):",
            "        self.type = type",
            "        self.vector = vector",
            "    def __getitem__(self, index):",
            "        return getattr(self, self.__slots__[index])",
            "    def __bool__(self):",
            "        return self.type != 'Void'",
            "    def __repr__(self):",
            "        return 'Void' if self.type is 'Void' else repr(self.vector)",
        )
        assertEqualCode(expected, astBoxedVal1())
    }

    @Test
    fun testBoxedVal1Exports() {
        val expected = setOf("Box")
        assertExports(expected, astBoxedVal1())
    }

    @Test
    fun testBoxedVal1Imports() {
        val expected = setOf<String>()
        assertImports(expected, astBoxedVal1())
    }
    private fun astStrJoin1() =
        pyProgram(
            l1c0c24,
            body = listOf(
                Py.ExprStmt(
                    l1c0c24,
                    value = Py.Call(
                        l1c0c24,
                        func = Py.Attribute(
                            l1c0c7,
                            value = Py.Str(l1c0c2, s = ""),
                            attr = Py.Identifier(p0, id = PyIdentifierName("join")),
                        ),
                        args = listOf(
                            Py.CallArg(
                                l1c8c23,
                                arg = null,
                                value = Py.Call(
                                    l1c8c23,
                                    func = Py.Name(l1c8c11, id = PyIdentifierName("map")),
                                    args = listOf(
                                        Py.CallArg(
                                            l1c12c15,
                                            arg = null,
                                            value = Py.Name(l1c12c15, id = PyIdentifierName("str")),
                                            prefix = Py.ArgPrefix.None,
                                        ),
                                        Py.CallArg(
                                            l1c17c22,
                                            arg = null,
                                            value = Py.Name(l1c17c22, id = PyIdentifierName("parts")),
                                            prefix = Py.ArgPrefix.None,
                                        ),
                                    ),
                                ),
                                prefix = Py.ArgPrefix.None,
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun testStrJoin1Out() {
        val expected = "''.join(map(str, parts))\n"
        assertEqualCode(expected, astStrJoin1())
    }

    @Test
    fun testStrJoin1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astStrJoin1())
    }

    @Test
    fun testStrJoin1Imports() {
        val expected = setOf("parts")
        assertImports(expected, astStrJoin1())
    }
    private fun astMethodCallChain1() =
        pyProgram(
            l1c0c17,
            body = listOf(
                Py.ExprStmt(
                    l1c0c17,
                    value = Py.Call(
                        l1c0c17,
                        func = Py.Attribute(
                            l1c0c15,
                            value = Py.Call(
                                l1c0c11,
                                func = Py.Attribute(
                                    l1c0c9,
                                    value = Py.Call(
                                        l1c0c5,
                                        func = Py.Name(l1c0c3, id = PyIdentifierName("foo")),
                                        args = listOf(),
                                    ),
                                    attr = Py.Identifier(p0, id = PyIdentifierName("bar")),
                                ),
                                args = listOf(),
                            ),
                            attr = Py.Identifier(p0, id = PyIdentifierName("qux")),
                        ),
                        args = listOf(),
                    ),
                ),
            ),
        )

    @Test
    fun testMethodCallChain1Out() {
        val expected = "foo().bar().qux()\n"
        assertEqualCode(expected, astMethodCallChain1())
    }

    @Test
    fun testMethodCallChain1Exports() {
        val expected = setOf<String>()
        assertExports(expected, astMethodCallChain1())
    }

    @Test
    fun testMethodCallChain1Imports() {
        val expected = setOf("foo")
        assertImports(expected, astMethodCallChain1())
    }

    private val l1c0 = ktPos(1, 0, 1, 1)
    private val l1c0c2 = ktPos(1, 0, 1, 2)
    private val l1c0c3 = ktPos(1, 0, 1, 3)
    private val l1c0c4 = ktPos(1, 0, 1, 4)
    private val l1c0c5 = ktPos(1, 0, 1, 5)
    private val l1c0c6 = ktPos(1, 0, 1, 6)
    private val l1c0c7 = ktPos(1, 0, 1, 7)
    private val l1c0c8 = ktPos(1, 0, 1, 8)
    private val l1c0c9 = ktPos(1, 0, 1, 9)
    private val l1c0c10 = ktPos(1, 0, 1, 10)
    private val l1c0c11 = ktPos(1, 0, 1, 11)
    private val l1c0c12 = ktPos(1, 0, 1, 12)
    private val l1c0c13 = ktPos(1, 0, 1, 13)
    private val l1c0c14 = ktPos(1, 0, 1, 14)
    private val l1c0c15 = ktPos(1, 0, 1, 15)
    private val l1c0c16 = ktPos(1, 0, 1, 16)
    private val l1c0c17 = ktPos(1, 0, 1, 17)
    private val l1c0c18 = ktPos(1, 0, 1, 18)
    private val l1c0c19 = ktPos(1, 0, 1, 19)
    private val l1c0c20 = ktPos(1, 0, 1, 20)
    private val l1c0c24 = ktPos(1, 0, 1, 24)
    private val l1c0c25 = ktPos(1, 0, 1, 25)
    private val l1c0c26 = ktPos(1, 0, 1, 26)
    private val l1c0c28 = ktPos(1, 0, 1, 28)
    private val l1c0l2c6 = ktPos(1, 0, 2, 6)
    private val l1c0l2c12 = ktPos(1, 0, 2, 12)
    private val l1c0l3c6 = ktPos(1, 0, 3, 6)
    private val l1c0l4c6 = ktPos(1, 0, 4, 6)
    private val l1c0l6c6 = ktPos(1, 0, 6, 6)
    private val l1c0l8c6 = ktPos(1, 0, 8, 6)
    private val l1c0l8c22 = ktPos(1, 0, 8, 22)
    private val l1c0l11c63 = ktPos(1, 0, 11, 63)
    private val l1c1 = ktPos(1, 1, 1, 2)
    private val l1c1c4 = ktPos(1, 1, 1, 4)
    private val l1c1c5 = ktPos(1, 1, 1, 5)
    private val l1c1c6 = ktPos(1, 1, 1, 6)
    private val l1c1c7 = ktPos(1, 1, 1, 7)
    private val l1c2 = ktPos(1, 2, 1, 3)
    private val l1c2c5 = ktPos(1, 2, 1, 5)
    private val l1c3 = ktPos(1, 3, 1, 4)
    private val l1c3c6 = ktPos(1, 3, 1, 6)
    private val l1c3c8 = ktPos(1, 3, 1, 8)
    private val l1c4 = ktPos(1, 4, 1, 5)
    private val l1c4c6 = ktPos(1, 4, 1, 6)
    private val l1c4c8 = ktPos(1, 4, 1, 8)
    private val l1c5 = ktPos(1, 5, 1, 6)
    private val l1c5c10 = ktPos(1, 5, 1, 10)
    private val l1c5c11 = ktPos(1, 5, 1, 11)
    private val l1c5c12 = ktPos(1, 5, 1, 12)
    private val l1c5c19 = ktPos(1, 5, 1, 19)
    private val l1c5c24 = ktPos(1, 5, 1, 24)
    private val l1c6 = ktPos(1, 6, 1, 7)
    private val l1c6c11 = ktPos(1, 6, 1, 11)
    private val l1c7 = ktPos(1, 7, 1, 8)
    private val l1c7c9 = ktPos(1, 7, 1, 9)
    private val l1c7c10 = ktPos(1, 7, 1, 10)
    private val l1c7c11 = ktPos(1, 7, 1, 11)
    private val l1c7c12 = ktPos(1, 7, 1, 12)
    private val l1c7c13 = ktPos(1, 7, 1, 13)
    private val l1c8 = ktPos(1, 8, 1, 9)
    private val l1c8c11 = ktPos(1, 8, 1, 11)
    private val l1c8c12 = ktPos(1, 8, 1, 12)
    private val l1c8c23 = ktPos(1, 8, 1, 23)
    private val l1c9 = ktPos(1, 9, 1, 10)
    private val l1c9c13 = ktPos(1, 9, 1, 13)
    private val l1c9c15 = ktPos(1, 9, 1, 15)
    private val l1c10 = ktPos(1, 10, 1, 11)
    private val l1c10c13 = ktPos(1, 10, 1, 13)
    private val l1c10c15 = ktPos(1, 10, 1, 15)
    private val l1c10c19 = ktPos(1, 10, 1, 19)
    private val l1c11 = ktPos(1, 11, 1, 12)
    private val l1c11c16 = ktPos(1, 11, 1, 16)
    private val l1c12 = ktPos(1, 12, 1, 13)
    private val l1c12c15 = ktPos(1, 12, 1, 15)
    private val l1c12c25 = ktPos(1, 12, 1, 25)
    private val l1c13 = ktPos(1, 13, 1, 14)
    private val l1c13c16 = ktPos(1, 13, 1, 16)
    private val l1c14 = ktPos(1, 14, 1, 15)
    private val l1c14c17 = ktPos(1, 14, 1, 17)
    private val l1c15 = ktPos(1, 15, 1, 16)
    private val l1c16 = ktPos(1, 16, 1, 17)
    private val l1c16c19 = ktPos(1, 16, 1, 19)
    private val l1c16c20 = ktPos(1, 16, 1, 20)
    private val l1c17 = ktPos(1, 17, 1, 18)
    private val l1c17c20 = ktPos(1, 17, 1, 20)
    private val l1c17c21 = ktPos(1, 17, 1, 21)
    private val l1c17c22 = ktPos(1, 17, 1, 22)
    private val l1c18 = ktPos(1, 18, 1, 19)
    private val l1c18c24 = ktPos(1, 18, 1, 24)
    private val l1c19 = ktPos(1, 19, 1, 20)
    private val l1c20 = ktPos(1, 20, 1, 21)
    private val l1c21 = ktPos(1, 21, 1, 22)
    private val l1c21c24 = ktPos(1, 21, 1, 24)
    private val l1c22 = ktPos(1, 22, 1, 23)
    private val l1c22c25 = ktPos(1, 22, 1, 25)
    private val l1c24 = ktPos(1, 24, 1, 25)
    private val l1c26 = ktPos(1, 26, 1, 27)
    private val l2c0 = ktPos(2, 0, 2, 1)
    private val l2c0c12 = ktPos(2, 0, 2, 12)
    private val l2c0l3c6 = ktPos(2, 0, 3, 6)
    private val l2c1 = ktPos(2, 1, 2, 2)
    private val l2c2c6 = ktPos(2, 2, 2, 6)
    private val l2c2c11 = ktPos(2, 2, 2, 11)
    private val l2c2c16 = ktPos(2, 2, 2, 16)
    private val l2c2c32 = ktPos(2, 2, 2, 32)
    private val l2c4 = ktPos(2, 4, 2, 5)
    private val l2c4c12 = ktPos(2, 4, 2, 12)
    private val l2c8 = ktPos(2, 8, 2, 9)
    private val l2c8c12 = ktPos(2, 8, 2, 12)
    private val l2c10 = ktPos(2, 10, 2, 11)
    private val l2c14c16 = ktPos(2, 14, 2, 16)
    private val l2c14c32 = ktPos(2, 14, 2, 32)
    private val l2c15c21 = ktPos(2, 15, 2, 21)
    private val l2c23c31 = ktPos(2, 23, 2, 31)
    private val l3c0l4c6 = ktPos(3, 0, 4, 6)
    private val l3c0l6c6 = ktPos(3, 0, 6, 6)
    private val l3c2c6 = ktPos(3, 2, 3, 6)
    private val l3c2l4c15 = ktPos(3, 2, 4, 15)
    private val l3c2l5c24 = ktPos(3, 2, 5, 24)
    private val l3c5 = ktPos(3, 5, 3, 6)
    private val l3c7 = ktPos(3, 7, 3, 8)
    private val l3c15c19 = ktPos(3, 15, 3, 19)
    private val l3c16c20 = ktPos(3, 16, 3, 20)
    private val l3c21c25 = ktPos(3, 21, 3, 25)
    private val l3c27c33 = ktPos(3, 27, 3, 33)
    private val l4c2c6 = ktPos(4, 2, 4, 6)
    private val l4c4c8 = ktPos(4, 4, 4, 8)
    private val l4c4c13 = ktPos(4, 4, 4, 13)
    private val l4c4c15 = ktPos(4, 4, 4, 15)
    private val l4c4c20 = ktPos(4, 4, 4, 20)
    private val l4c11c15 = ktPos(4, 11, 4, 15)
    private val l4c16c20 = ktPos(4, 16, 4, 20)
    private val l5c0l6c6 = ktPos(5, 0, 6, 6)
    private val l5c2l6c14 = ktPos(5, 2, 6, 14)
    private val l5c4c8 = ktPos(5, 4, 5, 8)
    private val l5c4c15 = ktPos(5, 4, 5, 15)
    private val l5c4c24 = ktPos(5, 4, 5, 24)
    private val l5c10c14 = ktPos(5, 10, 5, 14)
    private val l5c18c24 = ktPos(5, 18, 5, 24)
    private val l6c2c6 = ktPos(6, 2, 6, 6)
    private val l6c2l7c47 = ktPos(6, 2, 7, 47)
    private val l6c4c14 = ktPos(6, 4, 6, 14)
    private val l6c10c14 = ktPos(6, 10, 6, 14)
    private val l6c18c22 = ktPos(6, 18, 6, 22)
    private val l6c24c29 = ktPos(6, 24, 6, 29)
    private val l7c2l8c22 = ktPos(7, 2, 8, 22)
    private val l7c4c47 = ktPos(7, 4, 7, 47)
    private val l7c11c18 = ktPos(7, 11, 7, 18)
    private val l7c11c47 = ktPos(7, 11, 7, 47)
    private val l7c15c19 = ktPos(7, 15, 7, 19)
    private val l7c19c23 = ktPos(7, 19, 7, 23)
    private val l7c21c25 = ktPos(7, 21, 7, 25)
    private val l7c25c29 = ktPos(7, 25, 7, 29)
    private val l7c25c39 = ktPos(7, 25, 7, 39)
    private val l7c25c46 = ktPos(7, 25, 7, 46)
    private val l7c27c30 = ktPos(7, 27, 7, 30)
    private val l7c32c34 = ktPos(7, 32, 7, 34)
    private val l7c40c45 = ktPos(7, 40, 7, 45)
    private val l8c2c6 = ktPos(8, 2, 8, 6)
    private val l8c2l9c30 = ktPos(8, 2, 9, 30)
    private val l8c4c22 = ktPos(8, 4, 8, 22)
    private val l8c11c14 = ktPos(8, 11, 8, 14)
    private val l8c11c22 = ktPos(8, 11, 8, 22)
    private val l8c15c19 = ktPos(8, 15, 8, 19)
    private val l8c18c22 = ktPos(8, 18, 8, 22)
    private val l9c4c30 = ktPos(9, 4, 9, 30)
    private val l9c11c15 = ktPos(9, 11, 9, 15)
    private val l9c11c20 = ktPos(9, 11, 9, 20)
    private val l9c11c30 = ktPos(9, 11, 9, 30)
    private val l9c24c30 = ktPos(9, 24, 9, 30)
    private val l10c2l11c63 = ktPos(10, 2, 11, 63)
    private val l10c15c19 = ktPos(10, 15, 10, 19)
    private val l11c4c63 = ktPos(11, 4, 11, 63)
    private val l11c11c17 = ktPos(11, 11, 11, 17)
    private val l11c11c63 = ktPos(11, 11, 11, 63)
    private val l11c21c25 = ktPos(11, 21, 11, 25)
    private val l11c21c30 = ktPos(11, 21, 11, 30)
    private val l11c21c40 = ktPos(11, 21, 11, 40)
    private val l11c34c40 = ktPos(11, 34, 11, 40)
    private val l11c46c50 = ktPos(11, 46, 11, 50)
    private val l11c46c63 = ktPos(11, 46, 11, 63)
    private val l11c51c55 = ktPos(11, 51, 11, 55)
    private val l11c51c62 = ktPos(11, 51, 11, 62)
}
