package lang.temper.be.cpp

import kotlin.test.Test
import kotlin.test.assertEquals

class CppBuilderLiteralTest {
    private fun literalString(value: String): String {
        val cpp = CppBuilder(CppNames())
        return cpp.literal(value).toString()
    }

    @Test
    fun simpleAscii() {
        assertEquals("\"hello\"", literalString("hello"))
    }

    @Test
    fun emptyString() {
        assertEquals("\"\"", literalString(""))
    }

    @Test
    fun escapeQuotes() {
        assertEquals("\"say \\\"hi\\\"\"", literalString("say \"hi\""))
    }

    @Test
    fun escapeBackslash() {
        assertEquals("\"a\\\\b\"", literalString("a\\b"))
    }

    @Test
    fun escapeNewlineAndTab() {
        assertEquals("\"line1\\nline2\"", literalString("line1\nline2"))
        assertEquals("\"col1\\tcol2\"", literalString("col1\tcol2"))
    }

    @Test
    fun noTrigraphs() {
        assertEquals(""""?\?= ?\?/ ?\?\?\? ?"""", literalString("??= ??/ ???? ?"))
        assertEquals("? ?= ? ?/ ? ? ?", commentText("??= ??/ ???"))
    }

    @Test
    fun digitsAfterEscapesStayDigits() {
        // Octal escapes take at most three digits, and every escape has three, so a
        // digit after one is never read into it.
        assertEquals(""""?\?\0010"""", literalString("??\u00010"))
        assertEquals(""""?\?\n0"""", literalString("??\n0"))
        assertEquals(""""\342\202\2541"""", literalString("€1"))
    }

    @Test
    fun nullByteUsesStdString() {
        val result = literalString("ab\u0000cd")
        assertEquals("std::string(\"ab\\000cd\", 5)", result)
    }

    @Test
    fun multiByteUtf8() {
        // Euro sign U+20AC is 3 bytes in UTF-8: E2 82 AC
        val result = literalString("\u20AC")
        assertEquals("\"\\342\\202\\254\"", result)
    }

    @Test
    fun booleanLiterals() {
        val cpp = CppBuilder(CppNames())
        assertEquals("true", cpp.literal(true).toString())
        assertEquals("false", cpp.literal(false).toString())
    }

    @Test
    fun numericLiterals() {
        val cpp = CppBuilder(CppNames())
        assertEquals("42", cpp.literal(42).toString())
        assertEquals("3.14", cpp.literal(3.14).toString())
    }
}
