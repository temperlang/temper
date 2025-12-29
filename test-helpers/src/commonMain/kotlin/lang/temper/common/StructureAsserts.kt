package lang.temper.common

import lang.temper.common.structure.FormattingStructureSink
import lang.temper.common.structure.StructureContextKey
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

    val wantJson = FormattingStructureSink.toJsonString(extensions = true, filterKeys = filterKeys) {
        value(wantTree)
    }

    val gotJson = FormattingStructureSink.toJsonString(extensions = true, filterKeys = filterKeys) {
        value(gotTree)
    }

    if (wantJson != gotJson) {
        errorDumper(message, wantJson, gotJson)
    }

    assertStringsEqual(
        wantJson,
        gotJson,
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
