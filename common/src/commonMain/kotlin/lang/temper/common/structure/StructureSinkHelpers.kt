package lang.temper.common.structure

/**
 * Allows structuring an array as an object using keys
 * derived from values.
 *
 * So instead of producing a structure like
 *
 *     [["a", 1], ["b", 2]]
 *
 * one could produce
 *
 *     { "a": 1, "b": 2 }
 *
 * by using a [keyFor] that does `{ (k, _) -> k }` and a
 * [emitValueFor] that does `{ (_, v) -> v }`.
 */
fun <T> StructureSink.arrAsKeyedObj(
    elements: Iterable<T>,
    hints: Set<StructureHint> = Hints.empty,
    emitValueFor: StructureSink.(T) -> Unit =
        defaultEmitValueFor@{ this@defaultEmitValueFor.value(it) },
    keyFor: (T) -> String,
) {
    obj {
        for (element in elements) {
            key(keyFor(element), hints) {
                emitValueFor(element)
            }
        }
    }
}
