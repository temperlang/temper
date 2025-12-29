package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals

class MapInterleavingTest {

    @Test
    fun interleaving() {
        val nums = listOf(10, 25, 32).mapInterleaving(
            { it * 10 },
            { a, b -> a - b },
        )
        assertEquals(
            listOf(
                100,
                10 - 25,
                250,
                25 - 32,
                320,
            ),
            nums.toList(),
        )
    }
}
