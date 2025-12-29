package lang.temper.be.py

import lang.temper.be.py.PyIdentifierGrammar.scrubNonIdentifierParts
import lang.temper.name.OutName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PyIdentifierTest {
    private fun diDot() = DiPart.RelDot
    private fun diMod(s: String) = DiPart.Module(OutName(s, null))

    @Test
    fun dottedIdentifierEmpty() {
        assertFails {
            PyDottedIdentifier.invoke("")
        }
    }

    @Test
    fun dottedIdentifierOnlyRelative() {
        assertFails {
            PyDottedIdentifier.invoke("...")
        }
    }

    @Test
    fun dottedIdentifierOnlyStartsWithNum() {
        assertFails {
            PyDottedIdentifier.invoke("2bad")
        }
    }

    @Test
    fun dottedIdentifierRelative() {
        val id = PyDottedIdentifier.invoke("..a.b.c")
        assertEquals(
            listOf(diDot(), diDot(), diMod("a"), diMod("b"), diMod("c")),
            id.parts.toList(),
        )
    }

    @Test
    fun dottedIdentifierRelative2() {
        val id = PyDottedIdentifier.invoke(".apple")
        assertEquals(
            listOf(diDot(), diMod("apple")),
            id.parts.toList(),
        )
    }

    @Test
    fun dottedIdentifierAbsolute() {
        val id = PyDottedIdentifier.invoke("_foo.bar2.qux")
        assertEquals(
            listOf(diMod("_foo"), diMod("bar2"), diMod("qux")),
            id.parts.toList(),
        )
    }

    @Test
    fun dottedIdentifierAbsoluteSingle() {
        val id = PyDottedIdentifier.invoke("foo")
        assertEquals(
            listOf(diMod("foo")),
            id.parts.toList(),
        )
    }

    @Test
    fun scrubValidIdentifierDoesNothing() {
        assertEquals("foo", scrubNonIdentifierParts("foo"), message = "simple identifier")
        assertEquals("x", scrubNonIdentifierParts("x"), message = "short identifier")
    }

    @Test
    fun scrubValidIdentifierPreservesUnderscores() {
        for (pattern in listOf(
            "_x", "x_", "_foo", "foo_", "foo_bar", "_foo_bar", "foo_bar_", "_x_foo", "foo_x_",
        )) {
            for (replace in listOf("_", "__", "___")) {
                val word = pattern.replace("_", replace)
                assertEquals(
                    word,
                    scrubNonIdentifierParts(word),
                    message = word,
                )
            }
        }
    }

    @Test
    fun scrubValidIdentifierReplacesNonIdParts() {
        for (pattern in listOf(
            "_x", "x_", "_foo", "foo_", "foo_bar", "_foo_bar", "foo_bar_", "_x_foo", "foo_x_",
        )) {
            for (replace in listOf("-", "--", "-_", "_-", "---", "-_-")) {
                val word = pattern.replace("_", replace)
                assertEquals(pattern, scrubNonIdentifierParts(word), message = word)
            }
        }
    }
}
