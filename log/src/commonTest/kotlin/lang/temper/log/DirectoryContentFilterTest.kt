package lang.temper.log

import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectoryContentFilterTest {
    @Test
    fun exact() {
        val glob = DirectoryContentFilter.fromGlob("foo.bar")
        assertAllows("foo.bar", glob)
        assertDisallows("_foo.bar", glob)
        assertDisallows("foo.barr", glob)
        assertDisallows("foo_bar", glob)
    }

    @Test
    fun caseSensitivity() {
        val glob = DirectoryContentFilter.fromGlob("b")
        assertAllows("b", glob)
        assertDisallows("B", glob)
        assertDisallows("a", glob)
        assertDisallows("c", glob)
    }

    @Test
    fun dotIsNotSpecial() {
        val glob = DirectoryContentFilter.fromGlob("...")
        assertAllows("...", glob)
        assertDisallows("abc", glob)
        assertDisallows("a.c", glob)
    }

    @Test
    fun star() {
        val glob = DirectoryContentFilter.fromGlob("*.bar")
        assertAllows("foo.bar", glob)
        assertAllows("bar.bar", glob)
        assertAllows("_foo.bar", glob)
        assertAllows("foo.baz.bar", glob)
        assertDisallows("foo.barr", glob)
        assertDisallows("foo_bar", glob)
        assertDisallows("bar.foo", glob)
    }

    @Test
    fun questionMark() {
        val glob = DirectoryContentFilter.fromGlob("foo.???")
        assertAllows("foo.bar", glob)
        assertAllows("foo.foo", glob)
        assertAllows("foo.faz", glob)
        assertDisallows("foo.ba", glob)
        assertDisallows("foo.bard", glob)
    }

    @Test
    fun options() {
        val glob = DirectoryContentFilter.fromGlob("{foo,bar}.baz")
        assertAllows("foo.baz", glob)
        assertAllows("bar.baz", glob)
        assertDisallows("new.baz", glob)
        assertDisallows("{foo,bar}.baz", glob)
        assertDisallows("foobar.baz", glob)
    }

    @Test
    fun charset() {
        val glob = DirectoryContentFilter.fromGlob("[b-dfg].x")
        assertDisallows("a.x", glob)
        assertAllows("b.x", glob)
        assertAllows("c.x", glob)
        assertAllows("d.x", glob)
        assertDisallows("e.x", glob)
        assertAllows("f.x", glob)
        assertAllows("g.x", glob)
        assertDisallows("h.x", glob)
    }

    @Test
    fun invertedCharset() {
        val glob = DirectoryContentFilter.fromGlob("[!b-dfg].x")
        assertAllows("a.x", glob)
        assertDisallows("b.x", glob)
        assertDisallows("c.x", glob)
        assertDisallows("d.x", glob)
        assertAllows("e.x", glob)
        assertDisallows("f.x", glob)
        assertDisallows("g.x", glob)
        assertAllows("h.x", glob)
    }

    @Test
    fun charsetEscapes() {
        val glob = DirectoryContentFilter.fromGlob("[*?]")
        assertAllows("*", glob)
        assertAllows("?", glob)
        assertDisallows("*?", glob)
        assertDisallows("_", glob)
    }

    @Test
    fun bracketsCanBeMatchedAndRegexMetaCharsAreNotConfused() {
        val globsAndSoleCharMatched = listOf(
            "[[]" to '[',
            "]" to ']',
            "," to ',',
            "[{]" to '{',
            "[}]" to '}',
            "[^]" to '^', // ! negates in glob charsets
            "!" to '!', // ! outside a charset
            "[-]" to '-', // - in first position is not a range connector
            "^" to '^',
            "$" to '$',
            "(" to '(',
            ")" to ')',
            "[(]" to '(',
            "[)]" to ')',
        )
        val chars = "[],{}^$!-a_\n()"
        for ((globStr, matches) in globsAndSoleCharMatched) {
            val glob = DirectoryContentFilter.fromGlob(globStr)
            for (char in chars) {
                if (matches == char) {
                    assertAllows("$char", glob)
                } else {
                    assertDisallows("$char", glob)
                }
            }
        }
    }

    @Test
    fun badGlobs() {
        assertFails {
            DirectoryContentFilter.fromGlob("[a-c")
        }
        assertFails {
            DirectoryContentFilter.fromGlob("[a-]")
        }
        assertFails {
            DirectoryContentFilter.fromGlob("{abc,")
        }
        assertFails {
            DirectoryContentFilter.fromGlob("[c-a]")
        }
    }

    private fun assertAllows(fileName: String, filter: DirectoryContentFilter) {
        assertTrue(
            filter.allows(FilePathSegment(fileName)),
            message = "$fileName should match $filter",
        )
    }

    private fun assertDisallows(fileName: String, filter: DirectoryContentFilter) {
        assertFalse(
            filter.allows(FilePathSegment(fileName)),
            message = "$fileName should not match $filter",
        )
    }
}
