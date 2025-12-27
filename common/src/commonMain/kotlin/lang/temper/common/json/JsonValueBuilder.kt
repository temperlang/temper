package lang.temper.common.json

import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureContextKey
import lang.temper.common.structure.StructureHint
import lang.temper.common.structure.StructureSink

class JsonValueBuilder(
    private val contextMap: Map<StructureContextKey<*>, Any> = emptyMap(),
) : StructureSink {
    // The methods below build a tree by compacting suffixes of content back onto contents as
    // more structured values.
    // If all goes well, at the end, exactly one value remains which can be retrieved via getRoot()
    private val contents = mutableListOf<JsonValue>()

    override fun <T : Any> context(key: StructureContextKey<T>): T? {
        val value = contextMap[key] ?: return null
        return key.asValueTypeOrNull(value)!!
    }

    fun getRoot(): JsonValue {
        require(contents.size == 1)
        return contents[0]
    }

    override fun obj(emitProperties: PropertySink.() -> Unit) {
        val propertyBuilder = PropertyBuilder()
        emitProperties.invoke(propertyBuilder)
        contents.add(JsonObject(propertyBuilder.properties.toList()))
    }

    override fun arr(emitElements: StructureSink.() -> Unit) {
        val firstElementIndex = contents.size
        emitElements.invoke(this)
        val elementList = contents.subList(firstElementIndex, contents.size)
        val arr = JsonArray(elementList.toList())
        elementList.clear()
        contents.add(arr)
    }

    override fun value(s: String) {
        contents.add(JsonString(s))
    }

    override fun value(n: Int) {
        contents.add(JsonLong(n.toLong()))
    }

    override fun value(n: Long) {
        contents.add(JsonLong(n))
    }

    override fun value(n: Double) {
        contents.add(JsonDouble(n))
    }

    override fun value(b: Boolean) {
        contents.add(JsonBoolean(b))
    }

    override fun nil() {
        contents.add(JsonNull)
    }

    private inner class PropertyBuilder : PropertySink {
        private val firstValueIndex = contents.size
        val properties = mutableListOf<JsonProperty>()

        override fun <T : Any> context(key: StructureContextKey<T>): T? =
            this@JsonValueBuilder.context(key)

        override fun key(
            key: String,
            hints: Set<StructureHint>,
            emitValue: StructureSink.() -> Unit,
        ) {
            emitValue(this@JsonValueBuilder)
            require(contents.size == firstValueIndex + 1)
            val value = contents.removeAt(firstValueIndex)
            properties.add(JsonProperty(key, value, hints))
        }
    }

    companion object {
        fun build(
            contextMap: Map<StructureContextKey<*>, Any> = emptyMap(),
            f: (StructureSink).() -> Unit,
        ): JsonValue {
            val builder = JsonValueBuilder(contextMap)
            f.invoke(builder)
            return builder.getRoot()
        }
    }
}
