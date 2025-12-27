package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals

class WeightedAdjacencyTableTest {
    @Test
    fun weightedPartialOrder() {
        val table = WeightedAdjacencyTable(
            listOf("a", "b", "c", "d", "e", "f"),
        )

        // Heavy edges use `==>`, light use `-->`
        val lightEdge = 1
        val heavyEdge = 2

        // Cycle 1
        // a --> e
        table["a", "e"] = lightEdge
        // e --> b
        table["e", "b"] = lightEdge
        // a <== b
        table["b", "a"] = heavyEdge
        //
        // b --> f
        table["b", "f"] = lightEdge
        // f --> d
        table["f", "d"] = lightEdge
        // d ==> c
        table["d", "c"] = heavyEdge
        // d <-- c
        table["c", "d"] = lightEdge

        // Before breaking cycles, we don't get a weighted topological order.
        assertEquals(
            listOf("c", "d", "f", "b", "e", "a"),
            table.partiallyOrder(),
        )

        val broken = table.breakCycles()
        assertEquals(
            "[Edge(source=e, target=b, weight=1), Edge(source=c, target=d, weight=1)]",
            "$broken",
        )

        // After breaking cycles we get the heavy requirements
        // that "b" comes after "a" and "d" comes after "c".
        assertEquals(
            listOf("e", "a", "c", "d", "f", "b"),
            table.partiallyOrder(),
        )
    }
}
