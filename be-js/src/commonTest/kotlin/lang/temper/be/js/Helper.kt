package lang.temper.be.js

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedCode
import lang.temper.be.assertGeneratedStructure
import lang.temper.common.MimeType
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonDouble
import lang.temper.common.json.JsonLong
import lang.temper.common.json.JsonNull
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureContextKey
import lang.temper.common.structure.StructureHint
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.Genre
import lang.temper.log.FilePath
import lang.temper.log.filePath
import lang.temper.name.ProbableName
import lang.temper.name.ProbableNameMatcher
import kotlin.test.assertTrue

fun assertGeneratedCode(
    inputs: List<Pair<FilePath, String>>,
    want: String,
    moduleResultNeeded: Boolean = false,
) = assertGeneratedCode(
    inputs = inputs,
    want = want,
    factory = JsBackend.Factory,
    backendConfig = Backend.Config.production,
    probableNameMatcher = jsProbableNameMatcher,
    moduleResultNeeded = moduleResultNeeded,
    postProcess = ::ignoreFoldedCodePostProcessor,
)

fun assertGeneratedDocs(
    input: String,
    want: String,
    ignoreFolded: Boolean = false,
) = assertGeneratedCode(
    inputs = listOf(
        filePath("src", "test", "test.temper") to input,
    ),
    // The $want section looks weird, but it needs to look like that to be indented in a way that the parser likes
    want = """
            {
                "js": {
                    "my-test-library": {
                        "src": {
                            "test.js": {
                                "content":
```
$want

```
                            },
                            "test.js.map": "__DO_NOT_CARE__"
                        }
                    }
                }
            }
    """,
    backendConfig = Backend.Config.bundled,
    factory = JsBackend.Factory,
    postProcess = postProcess(ignoreFolded = ignoreFolded),
    genre = Genre.Documentation,
)

fun assertGeneratedDocsContain(
    input: String,
    target: String,
    moduleResultNeeded: Boolean = false,
) = assertGeneratedStructure(
    inputs = listOf(
        filePath("src", "test", "test.temper") to input,
    ),
    backendConfig = Backend.Config.bundled,
    factory = JsBackend.Factory,
    genre = Genre.Documentation,
    moduleResultNeeded = moduleResultNeeded,
    // Since JS interface support is wonky just asserting it generates something without error
    assertion = {
        val sink = ContainsSink(target)
        it.destructure(sink)
        val message = toStringViaBuilder { sb ->
            val messageSink = FormattingStructureSink(sb, true)
            it.destructure(messageSink)
        }
        assertTrue(sink.result, message)
    },
)

private fun postProcess(ignoreFolded: Boolean) = if (ignoreFolded) (::ignoreFoldedCodePostProcessor) else ({ it })

/**
 * Allow ignoring code between boilerplate folds by converting substrings in
 * generated file content like
 *
 *     // #region __BOILERPLATE__ {{{
 *     boilerplate code here
 *     // #endregion }}}
 *
 * by replacing the lines other than the first and last with
 *
 *     //============================
 */
fun ignoreFoldedCodePostProcessor(s: Structured): Structured {
    fun ignoreFoldedContent(content: String, mimeType: String): String = when (mimeType) {
        MimeType.javascript.toString() -> {
            jsCodeFoldRegex.replace(content, jsCodeFoldReplacement)
        }
        MimeType.json.toString() -> content
        else -> TODO("Code fold ignore for $mimeType")
    }
    fun postProcessJson(x: JsonValue): JsonValue = when (x) {
        is JsonBoolean,
        is JsonDouble,
        is JsonLong,
        is JsonNull,
        is JsonString,
        -> x
        is JsonArray -> JsonArray(x.elements.map { postProcessJson(it) })
        is JsonObject -> {
            val properties = x.properties.map rewriteProperty@{
                var value = postProcessJson(it.value)
                if (it.key == "content") {
                    val mimeType = x["mimeType"]
                    if (value is JsonString && mimeType is JsonString) {
                        value = JsonString(
                            ignoreFoldedContent(content = value.s, mimeType = mimeType.s),
                        )
                    }
                }
                it.copy(value = value)
            }
            JsonObject(properties)
        }
    }
    return postProcessJson(JsonValueBuilder.build(emptyMap()) { value(s) })
}

private val jsCodeFoldRegex = Regex(
    """// #region __BOILERPLATE__ \{\{\{\n[\s\S]*?\n// #endregion }}}\n""",
)
private val jsCodeFoldReplacement = """
    |// #region __BOILERPLATE__ {{{
    |//============================
    |// #endregion }}}
    |
""".trimMargin()

private val jsProbableNameRegex = Regex("""\b[^\W\d]\w*_(\d+)\b""")

private val jsProbableNameMatcher: ProbableNameMatcher = { string ->
    jsProbableNameRegex.findAll(string)
        .map {
            ProbableName(
                it.range,
                (it.range.first + it.value.indexOf('_') + 1)..it.range.last,
            )
        }
        .toList()
}

@Suppress("EmptyFunctionBlock")
private class ContainsSink(val target: String) : StructureSink {
    var result = false
    private val structureSink = this
    override fun <T : Any> context(key: StructureContextKey<T>): T? {
        return null
    }

    override fun obj(emitProperties: PropertySink.() -> Unit) {
        val elementSink = object : PropertySink {
            override fun key(key: String, hints: Set<StructureHint>, emitValue: StructureSink.() -> Unit) {
                structureSink.emitValue()
            }

            override fun <T : Any> context(key: StructureContextKey<T>): T? {
                return null
            }
        }
        elementSink.emitProperties()
    }

    override fun arr(emitElements: StructureSink.() -> Unit) {
        this.emitElements()
    }

    override fun value(s: String) {
        result = result || s.contains(target)
    }

    override fun value(n: Int) {
    }

    override fun value(n: Long) {
    }

    override fun value(n: Double) {
    }

    override fun value(b: Boolean) {
    }

    override fun nil() {
    }
}
