package lang.temper.be.java
import lang.temper.format.CodeFormatter
import lang.temper.format.toStringViaTokenSink
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import lang.temper.be.java.Java as J
import lang.temper.log.unknownPos as p0

class IdentifierGrammarTest {
    @Test
    fun isIdentifierSimpleWord() {
        assertTrue("foo".isIdentifier())
    }

    @Test
    fun isIdentifierInternalCapital() {
        assertTrue("fooBar".isIdentifier())
    }

    @Test
    fun isIdentifierUnderscore() {
        assertTrue("foo_bar".isIdentifier())
    }

    @Test
    fun isIdentifierLeadDigit() {
        assertFalse("4foo".isIdentifier())
    }

    @Test
    fun isIdentifierInternalSpace() {
        assertFalse("foo bar".isIdentifier())
    }

    @Test
    fun isIdentifierInternalDot() {
        assertFalse("foo.bar".isIdentifier())
    }

    @Test
    fun isIdentifierLeadDollar() {
        assertTrue("\$foo".isIdentifier())
    }

    @Test
    fun isIdentifierKeyword() {
        assertFalse("class".isIdentifier())
    }

    @Test
    fun escapeNonidentifierCharsSimpleWord() {
        assertEquals("foo", "foo".escapeNonidentifierChars())
    }

    @Test
    fun escapeNonidentifierCharsInternalCapital() {
        assertEquals("fooBar", "fooBar".escapeNonidentifierChars())
    }

    @Test
    fun escapeNonidentifierCharsUnderscore() {
        assertEquals("foo_bar", "foo_bar".escapeNonidentifierChars())
    }

    @Test
    fun escapeNonidentifierCharsLeadDigit() {
        assertEquals("$34foo", "4foo".escapeNonidentifierChars())
    }

    @Test
    fun escapeNonidentifierCharsInternalSpace() {
        assertEquals("foo$20bar", "foo bar".escapeNonidentifierChars())
    }

    @Test
    fun escapeNonidentifierCharsInternalDot() {
        assertEquals("foo$2ebar", "foo.bar".escapeNonidentifierChars())
    }

    @Test
    fun escapeNonidentifierCharsLeadDollar() {
        assertEquals("\$foo", "\$foo".escapeNonidentifierChars())
    }

    @Test
    fun escapeNonidentifierCharsKeyword() {
        assertEquals("class", "class".escapeNonidentifierChars())
    }

    @Test
    fun safeIdentifierSimpleWord() {
        assertEquals("foo", "foo".safeIdentifier())
    }

    @Test
    fun safeIdentifierInternalCapital() {
        assertEquals("fooBar", "fooBar".safeIdentifier())
    }

    @Test
    fun safeIdentifierUnderscore() {
        assertEquals("foo_bar", "foo_bar".safeIdentifier())
    }

    @Test
    fun safeIdentifierLeadDigit() {
        assertEquals("$34foo", "4foo".safeIdentifier())
    }

    @Test
    fun safeIdentifierInternalSpace() {
        assertEquals("foo$20bar", "foo bar".safeIdentifier())
    }

    @Test
    fun safeIdentifierInternalDot() {
        assertEquals("foo$2ebar", "foo.bar".safeIdentifier())
    }

    @Test
    fun safeIdentifierLeadDollar() {
        assertEquals("\$foo", "\$foo".safeIdentifier())
    }

    @Test
    fun safeIdentifierKeyword() {
        assertEquals("class_", "class".safeIdentifier())
    }

    private fun checkQualifiedName(vararg parts: String) = QualifiedName.safe(*parts).fullyQualified

    @Test
    fun qualifiedNameSimpleWord() {
        assertEquals("test.foo", checkQualifiedName("test", "foo"))
    }

    @Test
    fun qualifiedNameInternalCapital() {
        assertEquals("test.fooBar", checkQualifiedName("test", "fooBar"))
    }

    @Test
    fun qualifiedNameUnderscore() {
        assertEquals("test.foo_bar", checkQualifiedName("test", "foo_bar"))
    }

    @Test
    fun qualifiedNameLeadDigit() {
        assertEquals("test.$34foo", checkQualifiedName("test", "4foo"))
    }

    @Test
    fun qualifiedNameInternalSpace() {
        assertEquals("test.foo$20bar", checkQualifiedName("test", "foo bar"))
    }

    @Test
    fun qualifiedNameInternalDot() {
        assertEquals("test.foo$2ebar", checkQualifiedName("test", "foo.bar"))
    }

    @Test
    fun qualifiedNameLeadDollar() {
        assertEquals("test.\$foo", checkQualifiedName("test", "\$foo"))
    }

    @Test
    fun qualifiedNameKeyword() {
        assertEquals("test.class_", checkQualifiedName("test", "class"))
    }

    private fun J.Tree.render() = toStringViaTokenSink(
        formattingHints = JavaFormattingHints,
        singleLine = true,
    ) {
        CodeFormatter(it).format(this, true)
    }

    private val qnTestClassName get() = QualifiedName.safe("test", "ClassName")

    @Test
    fun qualifiedNameToClassType() {
        assertEquals("test.ClassName", qnTestClassName.toClassType(p0).render())
    }

    @Test
    fun qualifiedNameToNameExpr() {
        assertEquals("test.ClassName.field", QualifiedName.safe("test", "ClassName", "field").toNameExpr(p0).render())
    }

    @Test
    fun qualifiedNameToQualIdent() {
        assertEquals("test.pkg.name", QualifiedName.safe("test", "pkg", "name").toQualIdent(p0).render())
    }

    @Test
    fun qualifiedNameToQualIdentAndBack() {
        val original = QualifiedName.safe("test", "pkg", "name")
        val ast = original.toQualIdent(p0)
        val roundTrip = QualifiedName.fromAst(ast)
        assertEquals(original, roundTrip)
    }

    @Test
    fun qualifiedNameFromTemperPath() {
        val path = filePath("foo", "class", "great-test.temper")
        assertEquals("foo.class_.great_test", QualifiedName.fromTemperPath(path).fullyQualified)
    }

    @Test
    fun qualifiedNameFromTemperMdPath() {
        val path = filePath("foo", "class", "great-test.temper.md")
        assertEquals("foo.class_.great_test", QualifiedName.fromTemperPath(path).fullyQualified)
    }

    @Test
    fun qualifiedNameToTypeArg() {
        assertEquals(
            "test.ClassName",
            qnTestClassName
                .toTypeArg(p0, anns = listOf(), args = null)
                .render(),
        )
    }

    private val annotations get() = listOf(
        J.Annotation(p0, QualifiedName.safe("Nullable").toQualIdent(p0)),
    )

    @Test
    fun simpleQualifiedNameToTypeArgWithAnnotations() {
        assertEquals(
            "@Nullable ClassName",
            QualifiedName.safe("ClassName")
                .toTypeArg(p0, anns = annotations, args = null)
                .render(),
        )
    }

    @Test
    fun qualifiedNameToTypeArgWithAnnotations() {
        assertEquals(
            "test.@Nullable ClassName",
            qnTestClassName
                .toTypeArg(p0, anns = annotations, args = null)
                .render(),
        )
    }

    private val typeArguments get() = J.TypeArguments(
        p0,
        args = listOf("X", "Y", "Z").map { QualifiedName.safe(it).toTypeArg(p0, listOf(), null) },
    )

    @Test
    fun qualifiedNameToTypeArgWithArguments() {
        assertEquals(
            "test.ClassName<X, Y, Z>",
            qnTestClassName
                .toTypeArg(p0, anns = listOf(), args = typeArguments)
                .render(),
        )
    }

    @Test
    fun qualifiedNameToTypeArgWithBoth() {
        assertEquals(
            "test.@Nullable ClassName<X, Y, Z>",
            qnTestClassName
                .toTypeArg(p0, anns = annotations, args = typeArguments)
                .render(),
        )
    }

    @Test
    fun qualifiedNameSplit() {
        val (lead, tail) = QualifiedName.safe("test", "pkg", "ClassName").split()
        assertEquals("test.pkg", lead.fullyQualified)
        assertEquals("ClassName", tail.toString())
    }
}
