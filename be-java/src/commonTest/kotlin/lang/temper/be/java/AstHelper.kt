package lang.temper.be.java

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedCode
import lang.temper.common.jsonEscaper
import lang.temper.format.CodeFormatter
import lang.temper.format.toStringViaTokenSink
import lang.temper.log.FilePath
import lang.temper.log.filePath
import kotlin.test.assertEquals
import lang.temper.be.java.Java as J
import lang.temper.log.unknownPos as p0

open class AstHelper {
    protected fun qualIdent(name: String) = J.QualIdentifier(p0, name.split('.').map(::ident))
    protected fun ident(name: String) = J.Identifier(p0, name)
    protected fun name(name: String) = J.NameExpr(p0, name.split('.').map(::ident))
    protected fun method() = QualifiedName.safe("method").staticMethod(listOf(), p0)
    private fun method(expr: J.Expression) = QualifiedName.safe("method").staticMethod(listOf(expr.asArgument()), p0)
    protected fun string(text: String) = J.StringLiteral(p0, text)
    private fun exprStatement(expr: J.ExpressionStatementExpr) = J.ExpressionStatement(p0, expr)
    protected fun expr(expr: J.ExpressionStatementExpr) = exprStatement(expr)
    protected fun expr(expr: J.Operation) = exprStatement(if (expr.makesStatement()) expr else method(expr))
    protected fun expr(expr: J.Expression) = exprStatement(method(expr))
    protected fun type(name: String) = QualifiedName.safe(name.split('.')).toClassType(p0)

    protected fun assertCode(expected: String, ast: J.Tree) {
        val actual = toStringViaTokenSink(formattingHints = JavaFormattingHints, singleLine = false) {
            CodeFormatter(it).format(ast)
        }
        assertEquals(expected.trimEnd(), actual.trimEnd())
    }
}

fun assertGeneratedJava(
    input: String,
    want: String,
    moduleResultNeeded: Boolean = false,
    langs: Iterable<JavaLang> = JavaLang.entries,
) {
    assertGeneratedJavaRaw(
        input = input,
        want = """
        "pom.xml": "__DO_NOT_CARE__",
        "src": { "main": { "java": { "my_test_library": { "test": {
            "TestGlobal.java": {
                "content":
```
$want
```
            },
            "TestGlobal.java.map": "__DO_NOT_CARE__",
            "TestMain.java": "__DO_NOT_CARE__",
            "TestMain.java.map": "__DO_NOT_CARE__"
        },
        "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
        "MyTestLibraryMain.java": "__DO_NOT_CARE__",
        } } } }
        """,
        moduleResultNeeded = moduleResultNeeded,
        langs = langs,
    )
}

fun assertGeneratedJavaRaw(
    input: String,
    want: String,
    moduleResultNeeded: Boolean = false,
    langs: Iterable<JavaLang> = JavaLang.entries,
) = assertGeneratedJavaRaw(
    inputs = listOf(filePath("test", "test.temper") to input),
    want = want,
    moduleResultNeeded = moduleResultNeeded,
    langs = langs,
)
fun assertGeneratedJavaRaw(
    inputs: List<Pair<FilePath, String>>,
    want: String,
    moduleResultNeeded: Boolean = false,
    langs: Iterable<JavaLang> = JavaLang.entries,
) {
    for (lang in langs) {
        val fact = lang.factory
        assertGeneratedCode(
            inputs = inputs,
            want = """
            {
                ${jsonEscaper.escape(fact.backendId.uniqueId)}: {
                    "my-test-library": {
                        $want
                    }
                }
            }
            """,
            factory = fact,
            moduleResultNeeded = moduleResultNeeded,
            backendConfig = Backend.Config.production,
        )
    }
}

fun String.javaMethod(vararg imports: String) = javaParts(imports = imports.toList(), methods = this)

fun javaParts(fields: String = "", imports: Iterable<String> = emptyList(), methods: String = "") = (
    "package my_test_library.test;\n" +
        "${imports.joinToString("") { "$it\n" } }public final class TestGlobal {\n" +
        "    private TestGlobal() {\n" +
        "    }\n" +
        fields.trimMargin().prependIndent("    ").let { formatted ->
            when (formatted.trim()) {
                "" -> ""
                else -> "$formatted\n"
            }
        } +
        methods.trimMargin().prependIndent("    ") +
        "\n}\n"
    )
