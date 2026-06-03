package lang.temper.be.cpp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CppNameExtrasTest {

    @Test
    fun safeCppNamespaceReservesStd() {
        assertEquals("temper_std", safeCppNamespace("std"))
    }

    @Test
    fun safeCppNamespaceReservesChrono() {
        assertEquals("temper_chrono", safeCppNamespace("chrono"))
    }

    @Test
    fun safeCppNamespaceReservesFilesystem() {
        assertEquals("temper_filesystem", safeCppNamespace("filesystem"))
    }

    @Test
    fun safeCppNamespacePassesThrough() {
        assertEquals("mylib", safeCppNamespace("mylib"))
        assertEquals("temper", safeCppNamespace("temper"))
    }

    @Test
    fun cppNameRejectsKeywords() {
        assertFailsWith<IllegalArgumentException> {
            CppName("class")
        }
        assertFailsWith<IllegalArgumentException> {
            CppName("int")
        }
        assertFailsWith<IllegalArgumentException> {
            CppName("nullptr")
        }
    }

    @Test
    fun cppNameAllowsKeywordsWithFlag() {
        val name = CppName("int", allowKey = true)
        assertEquals("int", name.text)
    }

    @Test
    fun cppNameRawBypasses() {
        val name = CppName("~Destructor", raw = true)
        assertEquals("~Destructor", name.text)
    }

    @Test
    fun cppNameRawAllowsEmpty() {
        val name = CppName("", raw = true)
        assertEquals("", name.text)
    }

    @Test
    fun cppNameRejectsEmpty() {
        assertFailsWith<IllegalArgumentException> {
            CppName("")
        }
    }

    @Test
    fun cppNameRejectsReservedPatterns() {
        // Exactly "_" + uppercase letter is rejected
        assertFailsWith<IllegalArgumentException> {
            CppName("_A")
        }
        // Exactly "__" is rejected
        assertFailsWith<IllegalArgumentException> {
            CppName("__")
        }
    }

    @Test
    fun cppNameAllowsUnderscoreLower() {
        val name = CppName("_wacky_")
        assertEquals("_wacky_", name.text)
    }

    @Test
    fun fixNameEscapesKeywords() {
        assertEquals("class_", fixName("class"))
        assertEquals("int_", fixName("int"))
    }

    @Test
    fun fixNamePassesNonKeywords() {
        assertEquals("myVar", fixName("myVar"))
    }

    @Test
    fun cppNameEquality() {
        val a = CppName("foo")
        val b = CppName("foo")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

}
