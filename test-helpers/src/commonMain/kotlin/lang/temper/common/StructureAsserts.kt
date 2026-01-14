package lang.temper.common

import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonLeaf
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonValue
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.common.structure.StructureContextKey
import lang.temper.common.structure.StructureHint
import lang.temper.common.structure.StructureParser
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.structure.reconcileStructure

fun assertStructure(
    expected: Structured,
    input: Structured,
    message: String? = null,
    inputContext: Map<StructureContextKey<*>, Any> = emptyMap(),
    postProcessor: (Structured) -> Structured = { it },
    errorDumper: (message: String?, want: String, got: String) -> Unit = ::defaultErrorDumper,
    filterKeys: (String) -> Boolean = { true },
) {
    val (wantTreeUnprocessed: Structured, gotTreeUnprocessed: Structured) = reconcileStructure(
        sloppy = expected,
        pedantic = input,
        contextPedantic = inputContext,
    )

    val wantTree = postProcessor(wantTreeUnprocessed)
    val gotTree = postProcessor(gotTreeUnprocessed)

    val wantJson = JsonValueBuilder.build { wantTree.destructure(this) }
    val gotJson = JsonValueBuilder.build { gotTree.destructure(this) }

    fun toJson(s: Structured) =
        FormattingStructureSink.toJsonString(extensions = true, filterKeys = filterKeys) {
            value(s)
        }

    val wantJsonString = toJson(wantTree)
    var gotJsonString = toJson(gotTree)

    if (wantJsonString != gotJsonString) {
        // Maybe simplify the diff output.
        // A common approach in small diff test cases is to use arrays instead of objects,
        // so if there are no objects in wantJson, see if we can convert objects to arrays
        // in gotJson to minimize diffs.
        var hasArrays = false
        var hasObjects = false
        fun scanWanted(v: JsonValue): Boolean {
            when (v) {
                is JsonArray -> {
                    hasArrays = true
                    for (e in v.elements) {
                        if (!scanWanted(e)) { return false }
                    }
                }
                is JsonObject -> {
                    hasObjects = true
                    return false // Don't care about hasArrays
                }

                is JsonLeaf<*> -> {}
            }
            return true
        }
        scanWanted(wantJson)

        if (hasArrays && !hasObjects) {
            fun adjust(v: JsonValue): JsonValue = when (v) {
                is JsonLeaf<*> -> v
                is JsonObject -> {
                    val props = v.properties
                    if (
                        props.all { // Can it be converted to an array?
                            StructureHint.Unnecessary in it.hints || StructureHint.NaturallyOrdered in it.hints
                        }
                    ) {
                        JsonArray(
                            props.mapNotNull {
                                if (StructureHint.NaturallyOrdered in it.hints) {
                                    adjust(it.value)
                                } else {
                                    null
                                }
                            },
                        )
                    } else {
                        JsonObject(
                            props.map {
                                it.copy(value = adjust(it.value))
                            },
                        )
                    }
                }
                is JsonArray -> JsonArray(v.elements.map(::adjust))
            }
            val gotJsonAdjusted = adjust(gotJson)
            val gotJsonStringAdjusted = toJson(gotJsonAdjusted)
            if (gotJsonStringAdjusted != wantJsonString) {
                // Helps with diff
                gotJsonString = gotJsonStringAdjusted
            }
        }

        errorDumper(message, wantJsonString, gotJsonString)
    }

    assertStringsEqual(
        wantJsonString,
        gotJsonString,
        message = message,
    )
}

fun assertStructure(
    expectedJson: String,
    input: Structured,
    message: String? = null,
    inputContext: Map<StructureContextKey<*>, Any> = emptyMap(),
    postProcessor: (Structured) -> Structured = { it },
    errorDumper: (message: String?, want: String, got: String) -> Unit = ::defaultErrorDumper,
) = assertStructure(
    expected = StructureParser.parseJson(expectedJson, tolerant = true),
    input = input,
    message = message,
    inputContext = inputContext,
    postProcessor = postProcessor,
    errorDumper = errorDumper,
)

fun assertStructure(
    expectedJson: String,
    input: Iterable<Structured?>,
    message: String? = null,
    inputContext: Map<StructureContextKey<*>, Any> = emptyMap(),
    postProcessor: (Structured) -> Structured = { it },
    errorDumper: (message: String?, want: String, got: String) -> Unit = ::defaultErrorDumper,
) = assertStructure(
    expectedJson = expectedJson,
    input = IterableConverter(input),
    message = message,
    inputContext = inputContext,
    postProcessor = postProcessor,
    errorDumper = errorDumper,
)

private class IterableConverter(val iterable: Iterable<Structured?>) : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.arr {
        for (el in iterable) {
            value(el)
        }
    }
}

fun defaultErrorDumper(message: String?, want: String, got: String) {
    printErr(
        "${ if (message != null) { "$message\n\n" } else "" }want\n$want\n\ngot\n$got\n",
    )
}
