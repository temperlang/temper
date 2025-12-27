package lang.temper.common

import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonDouble
import lang.temper.common.json.JsonLong
import lang.temper.common.json.JsonNull
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonProperty
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.common.structure.Hints
import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureContext
import lang.temper.common.structure.StructureContextKey
import lang.temper.common.structure.StructureHint
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.log.AbstractMaxLevelLogSink
import lang.temper.log.CodeLocation
import lang.temper.log.FilePositions
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.excerpt

class ListBackedLogSink : AbstractMaxLevelLogSink(), Structured {
    private var entries = mutableListOf<LogEntry>()

    override fun doLog(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        fyi: Boolean,
    ) {
        entries.add(LogEntry(level, template, pos, values))
    }

    val allEntries get() = entries.toList()

    val allEntriesLive get() = entries as List<LogEntry>

    override fun destructure(structureSink: StructureSink) = structureSink.arr {
        entries.forEach {
            value(it)
        }
    }

    fun clear() {
        entries.clear()
    }

    /**
     * Aids testing by wrapping a structured value in `{ result: ..., errors: ... }`
     * But if the results are an objects, and it does not have a property named `errors`, just
     * sneaks an `errors` property in at the end.
     */
    fun wrapErrorsAround(
        s: Structured,
        levelWanted: Log.LevelFilter = Log.Error,
    ): Structured = wrapErrorsAround(s) { it.level >= levelWanted }

    /**
     * Aids testing by wrapping a structured value in `{ result: ..., errors: ... }`
     * But if the results are an objects, and it does not have a property named `errors`, just
     * sneaks an `errors` property in at the end.
     */
    fun wrapErrorsAround(
        s: Structured,
        levelWanted: (LogEntry) -> Boolean,
    ): Structured {
        val filteredEntries = entries.filter(levelWanted)
        return if (filteredEntries.isNotEmpty()) {
            object : Structured {
                override fun destructure(structureSink: StructureSink) {
                    val context: StructureContext = structureSink
                    val capturer = CapturingStructureSink(context)
                    capturer.value(s)
                    val root = capturer.root
                    // If everything else fails, wrap as `{ result: root, errors: [...] }`
                    val wrapperObj = JsonObject(
                        listOf(
                            JsonProperty("result", root, emptySet()),
                            JsonProperty("errors", errorList(context), emptySet()),
                        ),
                    )
                    // Apply the following strategies.
                    // 1. If it's an array-like object, add a naturally ordered error bag at the end
                    // 2. If it's an array, add an error bag at the end
                    // 3. If it's a non-array-like object with no representative property, add an
                    //    "errors" property at the end
                    // 4. Else use the wrapper
                    val augmentedRoot = when (root) {
                        is JsonObject -> { // 1 or 3
                            // If the object can match with an array, then preserve that property.
                            val hasSufficient = root.properties.any { (_, _, hints) ->
                                StructureHint.Sufficient in hints
                            }
                            val isArrayLike = !hasSufficient &&
                                root.properties.all { (_, _, hints) ->
                                    StructureHint.Unnecessary in hints ||
                                        StructureHint.NaturallyOrdered in hints
                                }
                            if (isArrayLike) { // 1
                                JsonObject(
                                    root.properties +
                                        JsonProperty(
                                            "errors",
                                            JsonObject(
                                                listOf(
                                                    JsonProperty("errors", errorList(context), Hints.s),
                                                ),
                                            ),
                                            Hints.n,
                                        ),
                                )
                            } else if (!hasSufficient && root.properties.none { it.key == "errors" }) {
                                // 3
                                JsonObject(
                                    root.properties + JsonProperty(
                                        "errors",
                                        errorList(context),
                                        Hints.empty,
                                    ),
                                )
                            } else { // 4
                                wrapperObj
                            }
                        }
                        is JsonArray -> { // 2
                            JsonArray(
                                root.elements +
                                    listOf(
                                        JsonObject(
                                            listOf(
                                                JsonProperty("errors", errorList(context), emptySet()),
                                            ),
                                        ),
                                    ),
                            )
                        }
                        else -> // 4
                            wrapperObj
                    }
                    augmentedRoot.destructure(structureSink)
                }

                private fun errorList(context: StructureContext): JsonArray {
                    val capturer = CapturingStructureSink(context)
                    capturer.arr {
                        filteredEntries.forEach { value(it) }
                    }
                    return capturer.root as JsonArray
                }
            }
        } else {
            s
        }
    }

    fun <CL : CodeLocation> toConsole(
        console: Console,
        filterLevel: Log.LevelFilter = Log.Warn,
        sources: Map<CL, Pair<String, FilePositions>>,
    ) {
        for (message in entries) {
            if (message.level >= filterLevel) {
                val pos = message.pos
                val e = sources[pos.loc]
                if (e != null) {
                    excerpt(pos, e.first, console.textOutput)
                }
                console.log(message.messageText, message.level)
            }
        }
    }
}

private class CapturingStructureSink(
    val context: StructureContext,
) : StructureSink {
    var elements = mutableListOf<JsonValue>()

    val root
        get(): JsonValue {
            require(elements.size == 1)
            return elements[0]
        }

    override fun obj(emitProperties: PropertySink.() -> Unit) {
        val propList = mutableListOf<JsonProperty>()
        elements.add(JsonObject(propList))
        (
            object : PropertySink {
                override fun key(
                    key: String,
                    hints: Set<StructureHint>,
                    emitValue: StructureSink.() -> Unit,
                ) {
                    val valueSink = CapturingStructureSink(context)
                    valueSink.emitValue()
                    propList.add(JsonProperty(key, valueSink.root, hints))
                }

                override fun <T : Any> context(key: StructureContextKey<T>): T? =
                    context.context(key)
            }
            ).emitProperties()
    }

    override fun arr(emitElements: StructureSink.() -> Unit) {
        val elementSink = CapturingStructureSink(context)
        elementSink.emitElements()
        elements.add(JsonArray(elementSink.elements))
    }

    override fun value(s: String) {
        elements.add(JsonString(s))
    }

    override fun value(n: Int) {
        elements.add(JsonLong(n.toLong()))
    }

    override fun value(n: Long) {
        elements.add(JsonLong(n))
    }

    override fun value(n: Double) {
        elements.add(JsonDouble(n))
    }

    override fun value(b: Boolean) {
        elements.add(JsonBoolean(b))
    }

    override fun nil() {
        elements.add(JsonNull)
    }

    override fun <T : Any> context(key: StructureContextKey<T>): T? = context.context(key)
}
