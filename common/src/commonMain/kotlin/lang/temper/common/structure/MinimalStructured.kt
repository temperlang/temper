package lang.temper.common.structure

import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonDouble
import lang.temper.common.json.JsonLong
import lang.temper.common.json.JsonNull
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonProperty
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue

fun Structured?.toMinimalJson(): JsonValue {
    data class PartialProperty(
        val key: String?,
        val hints: Set<StructureHint>,
        val value: JsonValue,
    )

    class Converter : StructureSink, PropertySink {
        private var keyInfo = PartialProperty(null, emptySet(), JsonNull)
        private val properties = mutableListOf<PartialProperty>()

        private fun add(value: JsonValue) {
            properties.add(keyInfo.copy(value = value))
        }

        override fun obj(emitProperties: PropertySink.() -> Unit) {
            val c = Converter()
            c.emitProperties()
            // If we have one, representative property, use that.
            val properties = c.properties.map { (key, hints, value) ->
                // Check that emitProperties used c as a PropertySink
                check(key != null) { "Properties in objects must have keys: $properties" }
                JsonProperty(key = key, hints = hints, value = value)
            }

            val representative = properties.firstOrNull { StructureHint.Sufficient in it.hints }
            val value: JsonValue = if (representative != null) {
                representative.value
            } else {
                val useArray =
                    properties.any { StructureHint.NaturallyOrdered in it.hints } &&
                        properties.all {
                            StructureHint.Unnecessary in it.hints ||
                                StructureHint.NaturallyOrdered in it.hints
                        }
                if (useArray) {
                    val elements = properties.mapNotNull {
                        if (StructureHint.NaturallyOrdered in it.hints) {
                            it.value
                        } else {
                            null
                        }
                    }
                    JsonArray(elements)
                } else {
                    JsonObject(properties)
                }
            }
            add(value)
        }

        override fun arr(emitElements: StructureSink.() -> Unit) {
            val c = Converter()
            c.emitElements()
            val values = c.properties.map {
                check(it.key == null)
                it.value
            }
            add(JsonArray(values))
        }

        override fun value(s: String) { add(JsonString(s)) }

        override fun value(n: Int) { add(JsonLong(n.toLong())) }

        override fun value(n: Long) { add(JsonLong(n)) }

        override fun value(n: Double) { add(JsonDouble(n)) }

        override fun value(b: Boolean) { add(JsonBoolean.value(b)) }

        override fun nil() { add(JsonNull) }

        override fun key(key: String, hints: Set<StructureHint>, emitValue: StructureSink.() -> Unit) {
            val oldKeyInfo = this.keyInfo
            this.keyInfo = PartialProperty(key, hints, JsonNull)
            try {
                emitValue()
            } finally {
                this.keyInfo = oldKeyInfo
            }
        }

        override fun <T : Any> context(key: StructureContextKey<T>): T? = null

        fun toJsonValue(): JsonValue {
            require(properties.size == 1 && properties[0].key == null) { "$properties" }
            return properties[0].value
        }
    }

    val c = Converter()
    c.value(this)
    return c.toJsonValue()
}
