package lang.temper.type

import kotlin.test.Test
import kotlin.test.assertEquals

class AndTypeSimplifierTest {
    @Test
    fun andOfAnyValueAndUnionOfValueTypes() = TypeTestHarness(
        "",
    ).run {
        val t = type("String? & AnyValue")
        assertEquals(
            type("String?"),
            t,
        )
    }
}
