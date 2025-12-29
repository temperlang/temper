package lang.temper.lexer

import lang.temper.common.assertStringsEqual
import kotlin.test.Test
import kotlin.test.assertEquals

class UnpackedStringTest {
    @Test
    fun decodeSlashX() {
        // Try both upper and lowercase hex digits.
        val unpackedString = unpackQuotedString("""${""}"ca\xF1\xf3n"${""}""")
        assertStringsEqual(
            "cañón",
            unpackedString.decoded,
        )
        assertEquals(true, unpackedString.isOk)
    }

    @Test
    fun decodeSlashUGood() {
        // Again both upper and lowercase.
        val unpackedString = unpackQuotedString("\"κόσμε\\u2605\\u{1F30C,1fa90}\"", skipDelimiter = true)
        assertStringsEqual(
            "κόσμε★\uD83C\uDF0C\uD83E\uDE90",
            unpackedString.decoded,
        )
        assertEquals(true, unpackedString.isOk)
    }

    @Test
    fun decodeSlashUSurrogate() {
        // Surrogate code points disallowed in Temper.
        val unpackedString = unpackQuotedString("\"κόσμε\\ud834\\ude0e\"", skipDelimiter = true)
        assertStringsEqual(
            "κόσμε",
            unpackedString.decoded,
        )
        assertEquals(false, unpackedString.isOk)
    }

    @Test
    fun decodeSlashUSurrogateCommaRun() {
        // Also check for surrogate code points in comma run.
        val unpackedString = unpackQuotedString("\"κόσμε\\u{1F30C,d834}\"", skipDelimiter = true)
        assertStringsEqual(
            "κόσμε\uD83C\uDF0C",
            unpackedString.decoded,
        )
        assertEquals(false, unpackedString.isOk)
    }
}
