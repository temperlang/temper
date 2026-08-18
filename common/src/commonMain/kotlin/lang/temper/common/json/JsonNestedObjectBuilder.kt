package lang.temper.common.json

import lang.temper.common.structure.Hints
import kotlin.math.min

class JsonNestedObjectBuilder {
    private val chainsToLeaves = mutableListOf<Pair<List<String>, JsonValue>>()

    fun property(propertyChain: List<String>, value: JsonValue) {
        chainsToLeaves.add(propertyChain to value)
    }

    fun toJsonObject(): JsonObject {
        // sort the property chains in lexicographic order
        // Perhaps we have these once we're ordered:
        // - ["a", "b"]
        // - ["a", "c", "d"]
        // - ["a", "c", "e"]
        // - ["f", "g"]
        // At depth 0, we can identify a run of "a" and a run of "f",
        // so we know for one JsonObject, what it's properties are.
        // Then at depth 1 for the "a" object, we have a run of one "b" and one "c".
        // So simple linear search lets us build objects.
        chainsToLeaves.sortWith { (a), (b) ->
            lexicographicTupleComparison(a, b)
        }

        fun build(range: IntRange, depth: Int): JsonObject = JsonObject(
            buildList {
                var i = range.first
                val limit = range.last
                while (i <= limit) {
                    val (chainI, valueI) = chainsToLeaves[i]
                    val propertyName = chainI.getOrNull(depth)
                    if (propertyName == null) {
                        i += 1
                        continue
                    }
                    // Find the i..<j range for the property.
                    var j = i + 1
                    while (j <= limit) {
                        if (chainsToLeaves[j].first.getOrNull(depth) != propertyName) {
                            break
                        }
                        j += 1
                    }
                    // The property either has a singleton value or a nested object value.
                    val value = if (i + 1 == j && chainI.size == depth + 1) {
                        valueI
                    } else {
                        build(i..<j, depth + 1)
                    }
                    add(JsonProperty(propertyName, value, Hints.empty))
                    i = j
                }
            },
        )
        return build(chainsToLeaves.indices, 0)
    }
}

fun buildJsonNestedObject(
    body: (JsonNestedObjectBuilder).() -> Unit,
): JsonObject {
    val builder = JsonNestedObjectBuilder()
    builder.body()
    return builder.toJsonObject()
}

private fun lexicographicTupleComparison(a: List<String>, b: List<String>): Int {
    val aSize = a.size
    val bSize = b.size
    val minSize = min(aSize, bSize)
    for (i in 0..<minSize) {
        val aStr = a[i]
        val bStr = b[i]
        val delta = aStr.compareTo(bStr)
        if (delta != 0) { return delta }
    }

    return aSize - bSize
}
