package lang.temper.type2

import lang.temper.type.withTypeTestHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class Type2Test {
    @Test
    fun categorizeFunctionInterfaces() = withTypeTestHarness {
        val myFnType2 = hackMapOldStyleToNew(type("fn(Int32, _? : Int32): String"))
        assertEquals(TypeCategory.Functional, myFnType2.definition.typeCategory)
        withType(
            myFnType2,
            fallback = { fail("$it") },
            fn = { _, sig: Signature2, _ ->
                assertEquals("(Int32, Int32 = ...) -> String", "$sig")
            },
        )
    }
}
