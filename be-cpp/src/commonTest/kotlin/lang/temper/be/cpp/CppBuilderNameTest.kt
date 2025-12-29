package lang.temper.be.cpp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CppBuilderNameTest {
    @Test
    fun basics() {
        assertName("TEST") {
            cpp.name("TEST")
        }
        assertFailsWith<IllegalArgumentException> {
            val cpp = CppBuilder(CppNames())
            cpp.name("int")
        }
        assertName("size_t") {
            cpp.name("size_t")
        }
        assertName("_wacky_") {
            cpp.name("_wacky_")
        }
        assertName("_Static_assert") {
            cpp.name("_Static_assert")
        }
        assertName("__builtin_trap") {
            cpp.name("__builtin_trap")
        }
    }

    @Test
    fun fromNamespace() {
        assertName("std::ptrdiff_t") {
            cpp.name("std::ptrdiff_t")
        }

        assertName("temper::core::String") {
            cpp.name("temper::core::String")
        }
    }

    @Test
    fun fromArgs() {
        assertName("std::vector") {
            cpp.name("std", "vector")
        }

        assertName("temper::core::Int") {
            cpp.name("temper", "core", "Int")
        }
    }

    @Test
    fun fromMixed() {
        assertName("temper::core::Float64::e") {
            cpp.name("temper::core", "Float64::e")
        }
    }
}

internal data class HasCpp(val cpp: CppBuilder)

internal fun assertName(expected: String, generateActual: HasCpp.() -> Cpp.Type) {
    val cpp = CppBuilder(CppNames())
    assertEquals(expected, HasCpp(cpp).generateActual().toString())
}
