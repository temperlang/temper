package lang.temper.be

import lang.temper.common.toStringViaBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class VLQTest {
    @Test
    fun vlqEncode() {
        for (
        (inp, want) in listOf(
            -187 to "3L",
            -156 to "5J",
            -61 to "7D",
            -47 to "/C",
            -16 to "hB",
            -15 to "f",
            -10 to "V",
            -1 to "D",
            0 to "A",
            1 to "C",
            5 to "K",
            89 to "yF",
            90 to "0F",
            95 to "+F",
            228 to "oO",
            234 to "0O",
            255 to "+P",
        )
        ) {
            val got = toStringViaBuilder { base64VlqEncode(intArrayOf(inp), it) }
            assertEquals(want, got, "$inp")
        }
    }
}
