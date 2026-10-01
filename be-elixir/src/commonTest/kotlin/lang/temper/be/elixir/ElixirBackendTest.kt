package lang.temper.be.elixir

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedStructure
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.lexer.Genre
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ElixirBackendTest {
    /**
     * A class the frontend rejects leaves its values typed *Invalid*. Reading a
     * property of one must become broken code that raises where it is reached,
     * as every other garbage node does, and not a TODO that takes the whole
     * translation down. `semantics/broken` never reaches this path: it came from
     * a library written outside the suite (ormery), whose `Query` class declares
     * its inputs twice.
     */
    @Test
    fun aGetterOnAnInvalidValueIsBrokenCodeNotACompilerCrash() {
        val out = generatedText(
            """
            |class Field(public fieldType: String) {}
            |
            |class Schema() {
            |  public getField(): Field { new Field("Int") }
            |}
            |
            |class Query(public schema: Schema) {
            |  public constructor(schema: Schema) { this.schema = schema; }
            |  public kind(): String { let f = schema.getField(); f.fieldType }
            |}
            """.trimMargin(),
        )
        assertContains(out, "broken code")
    }

    /**
     * A local whose value raises is never bound, and Elixir refuses to compile
     * a later read of it even though the read can never run ("undefined
     * variable"). So nothing after a raise in the same block may be emitted.
     * ormery's generated Elixir failed `mix compile` five times this way once
     * its broken reads stopped crashing the translator.
     */
    @Test
    fun nothingAfterARaiseReadsTheNameItWouldHaveBound() {
        val out = generatedText(
            """
            |class Field(public fieldType: String) {}
            |
            |class Schema() {
            |  public getField(): Field { new Field("Int") }
            |}
            |
            |class Query(public schema: Schema) {
            |  public constructor(schema: Schema) { this.schema = schema; }
            |  public kind(): String {
            |    let f = schema.getField();
            |    let fieldKind = f.fieldType;
            |    if (fieldKind == "Int") { "int" } else { "other" }
            |  }
            |}
            """.trimMargin(),
        )
        assertContains(out, "broken code")
        assertFalse("fieldKind ==" in out, "a read of fieldKind survives its raise:\n$out")
    }
}

/**
 * Every file the backend writes for one Temper source, as one JSON string.
 * Source maps are left out: they embed the Temper source, which would match
 * any search for a Temper name.
 */
private fun generatedText(temper: String): String {
    var text = ""
    assertGeneratedStructure(
        inputs = listOf(filePath("something", "something.temper") to temper),
        factory = ElixirBackend.Factory,
        backendConfig = Backend.Config.production,
        genre = Genre.Library,
        moduleResultNeeded = false,
    ) { text = FormattingStructureSink.toJsonString(it, filterKeys = { key -> !key.endsWith(".map") }) }
    return text
}
