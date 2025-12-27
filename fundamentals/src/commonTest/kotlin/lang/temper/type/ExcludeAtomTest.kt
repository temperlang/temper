package lang.temper.type

import kotlin.test.Test
import kotlin.test.assertEquals

class ExcludeAtomTest {
    @Test
    fun excludeBubbleTest() {
        val stringOrBubble = MkType.or(WellKnownTypes.stringType, BubbleType)
        assertEquals(
            WellKnownTypes.stringType,
            excludeBubble(stringOrBubble),
        )
    }
}
