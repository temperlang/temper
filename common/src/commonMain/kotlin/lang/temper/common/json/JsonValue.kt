package lang.temper.common.json

import lang.temper.common.RResult
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.common.structure.StructureContextKey
import lang.temper.common.structure.StructureHint
import lang.temper.common.structure.StructureParser
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder

/** A representation of JSON values that interoperates with [Structured]. */
sealed class JsonValue : Structured {
    internal abstract val structurePoints: Int

    /**
     * Looks up the named properties assuming intermediate values are [JsonObject]s, so
     *
     * [lookup]\("foo", "bar")
     *
     * is equivalent to
     *
     * ((this as? [JsonObject])?.get("foo") as? JsonObject)?.get("bar")
     */
    fun lookup(vararg propertyNames: String): JsonValue? {
        var o: JsonValue? = this
        for (propertyName in propertyNames) {
            o = (o as? JsonObject)?.get(propertyName)
            if (o == null) { break }
        }
        return o
    }

    fun appendJsonTo(
        out: Appendable,
        indent: Boolean = true,
        extensions: Boolean = false,
        contextMap: Map<StructureContextKey<*>, Any> = emptyMap(),
    ) = destructure(
        FormattingStructureSink(
            out,
            indent = indent,
            extensions = extensions,
            contextMap = contextMap,
        ),
    )

    fun toJsonString(
        indent: Boolean = true,
        extensions: Boolean = false,
        contextMap: Map<StructureContextKey<*>, Any> = emptyMap(),
    ) = toStringViaBuilder {
        appendJsonTo(it, indent = indent, extensions = extensions, contextMap = contextMap)
    }

    companion object {
        fun parse(
            jsonText: String,
            tolerant: Boolean = false,
            contextMap: Map<StructureContextKey<*>, Any> = emptyMap(),
        ): RResult<JsonValue, IllegalArgumentException> =
            RResult.of(IllegalArgumentException::class) {
                StructureParser.parseJson(
                    jsonText,
                    tolerant = tolerant,
                    contextMap = contextMap,
                )
            }
    }
}

/** Like `{ "key": "value" }`. */
data class JsonObject(
    val properties: List<JsonProperty>,
) : JsonValue(), Iterable<JsonProperty> {
    /** `jsonObject["key"]` is the value associated with that key or [JsonNull]. */
    operator fun get(key: String): JsonValue = getOrNull(key) ?: JsonNull

    fun getOrNull(key: String): JsonValue? =
        properties.firstOrNull { it.key == key }?.value

    /** `"key" in jsonObject` asks if the object has a key with that value. */
    operator fun contains(key: String) = properties.any { it.key == key }

    override fun iterator(): Iterator<JsonProperty> = properties.iterator()

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        properties.forEach { key(it.key, it.hints) { value(it.value) } }
    }

    private var sortedProperties: List<JsonProperty>? = null
    private fun needSortedProperties(): List<JsonProperty> {
        var props = sortedProperties
        if (props == null) {
            props = properties.sortedBy { it.key }
            sortedProperties = props
        }
        return props
    }

    /** Two objects are equivalent when their property lists sorted by keys are equivalent. */
    override fun hashCode(): Int = needSortedProperties().hashCode()

    /** Two objects are equivalent when their property lists sorted by keys are equivalent. */
    override fun equals(other: Any?): Boolean =
        other is JsonObject && this.needSortedProperties() == other.needSortedProperties()

    // Intentionally not getter.  StructureReconciliation may read repeatedly, so avoid tree walk.
    override val structurePoints: Int = properties.fold(1) { n, t -> n + t.value.structurePoints }
}

/** A `"key": value` pair. */
data class JsonProperty(
    val key: String,
    val value: JsonValue,
    val hints: Set<StructureHint>,
) {
    override fun equals(other: Any?): Boolean =
        // hints are non-normative
        other is JsonProperty && this.key == other.key && this.value == other.value

    override fun hashCode(): Int = value.hashCode() + 31 * key.hashCode()
}

class JsonArray(
    val elements: List<JsonValue>,
) : JsonValue(), List<JsonValue> by elements {
    override fun destructure(structureSink: StructureSink) = structureSink.arr {
        elements.forEach { value(it) }
    }

    // Intentionally not getter.  StructureReconciliation may read repeatedly, so avoid tree walk.
    override val structurePoints: Int = elements.fold(1) { n, t -> n + t.structurePoints }

    override fun equals(other: Any?) = other is JsonArray && this.elements == other.elements
    override fun hashCode(): Int = elements.hashCode()
    override fun toString(): String = elements.toString()
}

/** A JSON value that corresponds directly to a Kotlin value. */
sealed class JsonLeaf<T> : JsonValue() {
    override val structurePoints get() = 1

    abstract val content: T

    override fun toString(): String = "$content"
}

data class JsonString(
    val s: String,
) : JsonLeaf<String>() {
    override fun destructure(structureSink: StructureSink) = structureSink.value(s)

    override val content: String get() = s
}

data class JsonLong(
    val n: Long,
) : JsonLeaf<Long>() {
    override fun destructure(structureSink: StructureSink) = structureSink.value(n)

    override val content get() = n
}

data class JsonDouble(
    val n: Double,
) : JsonLeaf<Double>() {
    override fun destructure(structureSink: StructureSink) = structureSink.value(n)

    override val content get() = n
}

data class JsonBoolean(
    val b: Boolean,
) : JsonLeaf<Boolean>() {
    override fun destructure(structureSink: StructureSink) = structureSink.value(b)

    override val content get() = b

    companion object {
        val valueFalse = JsonBoolean(false)
        val valueTrue = JsonBoolean(true)
        fun value(b: Boolean) = if (b) { valueTrue } else { valueFalse }
    }
}

object JsonNull : JsonLeaf<Nothing?>() {
    override fun destructure(structureSink: StructureSink) = structureSink.nil()

    override val content: Nothing? get() = null
}
