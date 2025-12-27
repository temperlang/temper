package lang.temper.stage

import kotlin.test.Test
import kotlin.test.assertEquals

class StageTest {
    @Test
    fun abbreviationsNotAmbiguous() {
        val map = Stage.values().associateBy { it.abbrev }
        assertEquals(map.values.toSet(), Stage.values().toSet())
    }
}
