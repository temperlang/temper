package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrlDecodeTest {
    @Test
    fun emptyString() = assertEquals("", urlDecode(""))

    @Test
    fun helloWorld() = assertEquals("Hello, World!", urlDecode("Hello, World!"))

    @Test
    fun helloPlusWorld() =
        assertEquals("Hello, World!", urlDecode("Hello,+World!"))

    // developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/decodeURIComponent
    // #decoding_a_cyrillic_url_component
    @Test
    fun mozCyrillicExample() = assertEquals(
        "JavaScript_шеллы",
        urlDecode("JavaScript_%D1%88%D0%B5%D0%BB%D0%BB%D1%8B"),
    )

    @Test
    fun mozErrorExample() = assertNull(
        urlDecode("%E0%A4%A"),
    )

    @Test
    fun mozQueryParametersExample() = assertEquals(
        "search query (correct)",
        urlDecode("search+query%20%28correct%29"),
    )

    @Test
    fun lonePct() = assertNull(urlDecode("%"))

    @Test
    fun pctOneHex() = assertNull(urlDecode("%a"))

    @Test
    fun pctNotUtf8() = assertNull(urlDecode("%80"))
}
