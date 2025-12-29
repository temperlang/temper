package lang.temper.common.structure

import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonDouble
import lang.temper.common.json.JsonLong
import lang.temper.common.json.JsonNull
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonProperty
import lang.temper.common.json.JsonString
import kotlin.test.Test
import kotlin.test.assertEquals

class MinimalStructureTest {
    @Test
    fun simpleValuesAreAlreadyMinimal() {
        val simpleValues = listOf(
            JsonNull,
            JsonLong(42),
            JsonDouble(-1.0),
            JsonBoolean.valueFalse,
            JsonBoolean.valueTrue,
            JsonString("foo"),
        )
        for (simpleValue in simpleValues) {
            assertEquals(simpleValue, simpleValue.toMinimalJson())
        }
    }

    @Test
    fun simpleArrayIsAlreadyMinimal() {
        val array = JsonArray(
            listOf(
                JsonString("foo"),
                JsonString("bar"),
                JsonString("baz"),
            ),
        )
        assertEquals(array, array.toMinimalJson())
    }

    @Test
    fun simpleObjectIsAlreadyMinimal() {
        val obj = JsonObject(
            listOf(
                JsonProperty("foo", JsonString("FOO"), emptySet()),
                JsonProperty("bar", JsonString("BAR"), Hints.u),
            ),
        )
        assertEquals(obj, obj.toMinimalJson())
    }

    @Test
    fun simplifySingleSufficientPropertyToValue() {
        val obj = JsonObject(
            listOf(
                JsonProperty("representative", JsonString("FOO"), Hints.s),
                JsonProperty("lotsOfExtraDetail", JsonString("BAR"), Hints.u),
            ),
        )
        assertEquals(JsonString("FOO"), obj.toMinimalJson())
    }

    @Test
    fun simplifyToArrayWhenAllNecessaryAreNaturallyOrdered() {
        val obj = JsonObject(
            listOf(
                JsonProperty("x", JsonLong(1L), Hints.n),
                JsonProperty("y", JsonLong(-1L), Hints.n),
                JsonProperty("dimensionality", JsonLong(2L), Hints.u),
            ),
        )
        assertEquals(
            JsonArray(listOf(JsonLong(1L), JsonLong(-1L))),
            obj.toMinimalJson(),
        )
    }

    @Test
    fun emptyObjectRemainsAnObject() {
        val obj = JsonObject(emptyList())
        // An empty object should not convert to an empty array.
        assertEquals(obj, obj.toMinimalJson())
    }
}
