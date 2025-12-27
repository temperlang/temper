package lang.temper.be.csharp

import kotlin.test.Test
import kotlin.test.assertEquals

class CSharpEscaperTest {
    @Test
    fun stringTokenTextWorks() {
        assertEquals(
            """
                |""
            """.trimMargin(),
            stringTokenText(""),
        )
        assertEquals(
            """
                |"\n"
            """.trimMargin(),
            stringTokenText("\n"),
        )
        assertEquals(
            """
                |"\u0085\u2028\u2029\\\u0022"
            """.trimMargin(),
            stringTokenText("\u0085\u2028\u2029\\\""),
        )
    }
}
