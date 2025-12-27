package lang.temper.be.cpp

import kotlin.test.Test
import kotlin.test.assertEquals

class CppBuilderTypeTest {
    @Test
    fun basic() {
        assertType("T", "T")
        assertType("size_t", "size_t")
    }

    @Test
    fun leftConst() {
        // TODO Allow int here? assertType("int const", "const int")
        assertType("Thing const", "const Thing")
    }

    @Test
    fun rightConst() {
        assertType("Thing const", "Thing const")
    }

    @Test
    fun scopedNames() {
        assertType("std::string", "std::string")
        assertType("temper::core::Int", "temper::core::Int")
    }

    @Test
    fun singleTemplate() {
        assertType("Box<T>", "Box<T>")
    }

    @Test
    fun spaces() {
        assertType(
            "std::pair<std::string * const, std::vector<std::set<ptrdiff_t const> const>> * const *",
            "std :: pair < std:: string *const, std :: vector< const  std::set<ptrdiff_t const >> >* const*",
        )
    }
}

internal fun assertType(expected: String, actual: String) {
    val cpp = CppBuilder(CppNames())
    assertEquals(expected, cpp.type(actual).toString())
}

internal fun assertSameType(expected: String, actual: String) {
    val cpp = CppBuilder(CppNames())
    assertEquals(cpp.type(expected).toString(), cpp.type(actual).toString())
}
