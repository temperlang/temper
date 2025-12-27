package lang.temper.common.structure

import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonNull
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonProperty
import lang.temper.common.json.JsonValue
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.structure.StructureHint.NaturallyOrdered
import lang.temper.common.structure.StructureHint.Sufficient
import lang.temper.common.structure.StructureHint.Unnecessary
import kotlin.math.abs
import kotlin.math.min

/**
 * Tries to reconcile to description of the same [Structured] value:
 * - a sloppy one which may be missing non-essential data,
 * - a pedantic one with non-essential data present but marked as such via [StructureHint]s.
 *
 * @return An adjusted version of [pedantic] that more closely resembles the structure of [sloppy]
 *     so that they are `==` equal if they accurately describe the same structure, but if not,
 *     a diff of their textual forms should help a human viewer localize discrepancies.
 */
fun reconcileStructure(
    sloppy: Structured,
    pedantic: Structured,
    contextSloppy: Map<StructureContextKey<*>, Any> = emptyMap(),
    contextPedantic: Map<StructureContextKey<*>, Any> = emptyMap(),
): Pair<Structured, Structured> {
    val sloppyTree = asJsonValue(sloppy, contextSloppy)
    val pedanticTree = asJsonValue(pedantic, contextPedantic)
    val lessPedanticTree = reconcile(
        sloppyTree,
        pedanticTree,
    ).first
    return sloppyTree to lessPedanticTree
}

private fun reconcile(sloppy: JsonValue, pedantic: JsonValue): Pair<JsonValue, Boolean> {
    if (sloppy == pedantic) {
        return pedantic to true
    }
    var bestEffort = pedantic
    fun maybeBetterEffort(candidate: JsonValue) {
        val goal = sloppy.structurePoints
        val delta = abs(goal - bestEffort.structurePoints)
        val candidateDelta = abs(goal - candidate.structurePoints)
        if (candidateDelta < delta) {
            bestEffort = candidate
        }
    }

    when (pedantic) {
        is JsonObject -> {
            if (sloppy is JsonObject) {
                val reconciliation = reconcileObjects(
                    sloppy.properties,
                    pedantic = pedantic.properties,
                )
                if (reconciliation.second) {
                    return reconciliation
                }
                maybeBetterEffort(reconciliation.first)
            }
            // If any sufficient property matches, then we're done
            for (prop in pedantic.properties) {
                if (Sufficient in prop.hints) {
                    val reconciliation = reconcile(
                        sloppy,
                        pedantic = prop.value,
                    )
                    if (reconciliation.second) {
                        return reconciliation
                    }
                    if (sloppy !is JsonObject) {
                        maybeBetterEffort(reconciliation.first)
                    }
                }
            }
            if (
                sloppy is JsonArray &&
                // It's possible to derive an array like analogue for pedantic when everything
                // we care about is naturally ordered.
                pedantic.properties.all { Unnecessary in it.hints || NaturallyOrdered in it.hints }
            ) {
                // We might want to skip some NaturallyOrdered but Unnecessary values when comparing
                // to sloppy.
                // Rules of thumb:
                // - use sloppy.size as a guide to how much to pass
                // - we always pass along all necessary property values
                // - if we need some unnecessary values only consider naturally ordered ones.
                // - first fill in the gaps between necessary ones (by using those naturally ordered
                //   & unnecessary) values that are between the earliest necessary, ordered and
                //   the last.
                //   This way we're most likely to pass a contiguous block of properties.
                // - if we need more, scan from the left.
                // - if we have too many or too few, go ahead anyway.
                val ordered = pedantic.properties.map { NaturallyOrdered in it.hints }
                val used = pedantic.properties.mapTo(mutableListOf()) { Unnecessary !in it.hints }
                var nNeeded = sloppy.elements.size - used.count { it }

                fun maybeUse(i: Int) =
                    if (!used[i] && ordered[i] && nNeeded > 0) {
                        used[i] = true
                        nNeeded -= 1
                        true
                    } else {
                        false
                    }

                if (nNeeded > 0) {
                    val minSet = used.indexOf(true)
                    val maxSet = used.lastIndexOf(true)
                    if (minSet >= 0) {
                        // Fill in from the middle
                        for (i in (minSet + 1) until maxSet) {
                            if (maybeUse(i) && nNeeded == 0) {
                                break
                            }
                        }
                    }
                    // Fill in from the left
                    for (i in used.indices) {
                        if (maybeUse(i) && nNeeded == 0) {
                            break
                        }
                    }
                }

                val cherryPickedOrderedElements = pedantic.properties.mapIndexedNotNull { i, p ->
                    if (used[i]) {
                        require(NaturallyOrdered in p.hints)
                        p.value
                    } else {
                        null
                    }
                }
                val reconciliation = reconcileArrays(
                    sloppy.elements,
                    pedantic = cherryPickedOrderedElements,
                )
                if (reconciliation.second) {
                    return reconciliation
                }
                maybeBetterEffort(reconciliation.first)
            }
        }
        is JsonArray -> {
            if (sloppy is JsonArray) {
                val reconciliation = reconcileArrays(
                    sloppy.elements,
                    pedantic = pedantic.elements,
                )
                if (reconciliation.second) {
                    return reconciliation
                }
                maybeBetterEffort(reconciliation.first)
            }
        }
        else -> {}
    }
    return bestEffort to false
}

private fun reconcileArrays(
    sloppy: List<JsonValue>,
    pedantic: List<JsonValue>,
): Pair<JsonArray, Boolean> {
    val nPedantic = pedantic.size
    val nSloppy = sloppy.size
    if (nPedantic == nSloppy) {
        // Reconcile pairwise
        var allReconciled = true
        val reconciledEls = (sloppy zip pedantic).map { (s, p) ->
            val (r, ok) = reconcile(sloppy = s, pedantic = p)
            if (!ok) {
                allReconciled = false
            }
            r
        }
        return JsonArray(reconciledEls) to allReconciled
    }

    // Compare from the left and then from the right so that we localize any difference to
    // an interior portion of the array
    val prefixOk = mutableListOf<JsonValue>()
    for (i in 0 until min(nPedantic, nSloppy)) {
        val pedanticEl = pedantic[i]
        val sloppyEl = sloppy[i]
        val (reconciledEl, ok) = reconcile(
            sloppyEl,
            pedantic = pedanticEl,
        )
        if (!ok) {
            break
        }
        prefixOk.add(reconciledEl)
    }
    val suffixOkReversed = mutableListOf<JsonValue>()
    for (i in 0 until (min(nPedantic, nSloppy) - prefixOk.size)) {
        val pedanticEl = pedantic[nPedantic - 1 - i]
        val sloppyEl = sloppy[nSloppy - 1 - i]
        val (reconciledEl, ok) = reconcile(
            sloppyEl,
            pedantic = pedanticEl,
        )
        if (!ok) {
            break
        }
        suffixOkReversed.add(reconciledEl)
    }
    val startOfSloppyOkRight = nSloppy - suffixOkReversed.size
    val bestEffortMiddle = pedantic.subList(
        prefixOk.size,
        pedantic.size - suffixOkReversed.size,
    ).mapIndexed { i, p ->
        if (prefixOk.size + i < startOfSloppyOkRight) {
            reconcile(sloppy[i], pedantic = p).first
        } else {
            p
        }
    }

    return JsonArray(
        prefixOk +
            bestEffortMiddle +
            suffixOkReversed.asReversed(),
    ) to (prefixOk.size == nPedantic && nPedantic == nSloppy)
}

private fun reconcileObjects(
    sloppy: List<JsonProperty>,
    pedantic: List<JsonProperty>,
): Pair<JsonObject, Boolean> {
    val pairedProps = mutableMapOf<String, Triple<JsonValue?, JsonValue?, Set<StructureHint>>>()
    for ((k, v) in sloppy) {
        val old = pairedProps[k]
        require(old?.first == null) { "Duplicate key in sloppy: $k" }
        pairedProps[k] = Triple(v, old?.second, old?.third ?: emptySet())
    }
    for ((k, v, h) in pedantic) {
        val old = pairedProps[k]
        require(old?.second == null) { "Duplicate key in pedantic: $k" }
        pairedProps[k] = Triple(old?.first, v, h)
    }

    val keysNotQueued = pairedProps.keys.toMutableSet()

    val triples = mutableListOf<Pair<String, Triple<JsonValue?, JsonValue?, Set<StructureHint>>>>()
    // Use the same key order as sloppy.
    for ((key) in sloppy) {
        keysNotQueued.remove(key)
        triples.add(key to pairedProps[key]!!)
    }
    for (key in keysNotQueued) {
        triples.add(key to pairedProps[key]!!)
    }

    val pruned = triples.filter { (_, triple) ->
        val (sloppyValue, _, hints) = triple
        // Keep it if we've a value to match against it or its necessary.
        sloppyValue != null || Unnecessary !in hints
    }

    var allOk = true
    val reconciledProps = pruned.mapNotNull { (key, t) ->
        val (sloppyValue, pedanticValue, hints) = t
        if (pedanticValue == null) {
            allOk = false
            null
        } else {
            val p = reconcile(
                sloppyValue ?: JsonNull,
                pedantic = pedanticValue,
            )
            if (!p.second) {
                allOk = false
            }
            JsonProperty(key, p.first, hints)
        }
    }

    return JsonObject(reconciledProps) to allOk
}

private fun asJsonValue(s: Structured, contextMap: Map<StructureContextKey<*>, Any>): JsonValue {
    val b = JsonValueBuilder(contextMap)
    s.destructure(b)
    return b.getRoot()
}
