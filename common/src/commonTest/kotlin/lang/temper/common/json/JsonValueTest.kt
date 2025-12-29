package lang.temper.common.json

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * These tests focus on the public API of [JsonValue].  Testing of parsing and un-parsing are
 * covered in
 *
 * - [lang.temper.common.structure.FormattingStructureSinkTest] and
 * - [lang.temper.common.structure.StructureParserTest]
 */
class JsonValueTest {
    @Test
    fun accessJsonObjectLikeMap() {
        val jsonObject = JsonValue.parse("""{ "foo": "bar" }""").result as JsonObject
        assertEquals(JsonString("bar"), jsonObject["foo"])
    }

    @Test
    fun unpackJsonArrayLikeTuple() {
        val jsonArray = JsonValue.parse("""["a", "b", "c"]""").result as JsonArray
        val (a, b, c) = jsonArray
        assertEquals(JsonString("a"), a)
        assertEquals(JsonString("b"), b)
        assertEquals(JsonString("c"), c)
    }

    @Test
    fun readFromArrayByIndex() {
        val jsonArray = JsonValue.parse("""["a", "b", "c"]""").result as JsonArray
        assertEquals(JsonString("b"), jsonArray[1])
    }

    @Test
    fun propertyUnpacksInSensibleOrder() {
        val jsonObject = JsonValue.parse(
            """
            |{
            |  "one": 1,
            |  "fortyTwo": 42
            |}
            """.trimMargin(),
        ).result as JsonObject

        // Intentionally avoid using type annotations here
        val pairs = jsonObject.map { (key, value) ->
            key to value
        }
        assertEquals(
            listOf(
                "one" to JsonLong(1),
                "fortyTwo" to JsonLong(42),
            ),
            pairs,
        )
    }

    @Test
    fun equalsWorks() {
        val jsonTexts = listOf(
            "null",
            "12",
            "123.45",
            "[]",
            "[1, 2, \"foo\"]",
            "{}",
            "{ \"foo\": \"bar\" }",
        )

        for (jsonTextA in jsonTexts) {
            val a = JsonValue.parse(jsonTextA)
            for (jsonTextB in jsonTexts) {
                val b = JsonValue.parse(jsonTextB)
                assertEquals(
                    jsonTextA == jsonTextB,
                    a == b,
                    "$jsonTextA\tv\t$jsonTextB",
                )
            }
        }
    }
}
