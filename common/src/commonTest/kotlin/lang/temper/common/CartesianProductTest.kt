package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals

class CartesianProductTest {
    @Test
    fun cartesianProductOfNothing() = assertEquals(
        listOf(listOf()),
        CartesianProduct<Unit>(emptyList()).toList(),
    )

    @Test
    fun simpleExample() = assertEquals(
        listOf(
            listOf("a", false, 0),
            listOf("b", false, 0),
            listOf("c", false, 0),
            listOf("a", true, 0),
            listOf("b", true, 0),
            listOf("c", true, 0),
            listOf("a", false, 1),
            listOf("b", false, 1),
            listOf("c", false, 1),
            listOf("a", true, 1),
            listOf("b", true, 1),
            listOf("c", true, 1),
        ),
        CartesianProduct<Any>(
            listOf(
                listOf("a", "b", "c"),
                listOf(false, true),
                listOf(0, 1),
            ),
        ).toList(),
    )
}
