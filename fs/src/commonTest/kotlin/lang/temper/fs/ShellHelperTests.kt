package lang.temper.fs

import kotlin.test.Test
import kotlin.test.assertEquals

class ShellHelperTests {
    @Test
    fun testUnixEscape1() {
        assertEquals("foo", escapeShellString("foo"))
    }

    @Test
    fun testUnixEscape2() {
        assertEquals("'foo>bar'", escapeShellString("foo>bar"))
    }

    @Test
    fun testUnixEscape3() {
        assertEquals("'foo'\\''bar'", escapeShellString("foo'bar"))
    }

    @Test
    fun testUnixEscape4() {
        assertEquals("''", escapeShellString(""))
    }

    @Test
    fun testWindowsEscape1() {
        assertEquals("^\"\\^\"foo\\^\"^\"", escapeWindowsString("\"foo\""))
    }

    @Test
    fun testWindowsEscape2() {
        assertEquals("foo^%bar", escapeWindowsString("foo%bar"))
    }

    @Test
    fun testWindowsEscape2b() {
        assertEquals("^\"foo bar^\"", escapeWindowsString("foo bar"))
    }

    @Test
    fun testWindowsEscape3() {
        assertEquals("^\"^\"", escapeWindowsString(""))
    }

    @Test
    fun testWindowsEscape4() {
        assertEquals("^\"a ^%VAR^%^\"", escapeWindowsString("a %VAR%"))
    }

    @Test
    fun testWindowsEscapeValue1() {
        assertEquals("foo", escapeWindowsEnvValue("foo"))
    }

    @Test
    fun testWindowsEscapeValue2() {
        assertEquals("foo^%bar", escapeWindowsEnvValue("foo%bar"))
    }

    @Test
    fun testWindowsEscapeValue2b() {
        assertEquals("foo^ bar", escapeWindowsEnvValue("foo bar"))
    }

    @Test
    fun testWindowsEscapeValue3() {
        // No way to set a var to an empty string in cmd, at least.
        // TODO But quotes might be better than deleting the var?
        // Here's setting to empty, which results in nonexpansion.
        // C:\Users\work2\Documents\projects\old>set VAR=
        // C:\Users\work2\Documents\projects\old>set VAR
        // Variable de entorno VAR no definida
        // C:\Users\work2\Documents\projects\old>echo "%VAR%"
        // "%VAR%"
        // And here's setting to quotes, which results in actual quotes in the value.
        // C:\Users\work2\Documents\projects\old>set VAR=^"^"
        // C:\Users\work2\Documents\projects\old>echo "%VAR%"
        // """"
        assertEquals("", escapeWindowsEnvValue(""))
    }

    @Test
    fun testWindowsEscapeValue4() {
        assertEquals("a^ ^%VAR^%", escapeWindowsEnvValue("a %VAR%"))
    }

    @Test
    fun testCommonPrefixDifferentWords() {
        assertEquals("", commonPrefixBy('/', "foo", "bar"))
    }

    @Test
    fun testCommonPrefixNoDelimeter() {
        assertEquals("", commonPrefixBy('/', "foofoo", "foobar"))
    }

    @Test
    fun testCommonPrefixFoundDelimeter() {
        assertEquals("foo/", commonPrefixBy('/', "foo/foo", "foo/bar"))
    }

    @Test
    fun testCommonPrefixNoTrailing() {
        assertEquals("foo/", commonPrefixBy('/', "foo/foo", "foo/foo"))
    }

    @Test
    fun testCommonPrefixTrailingDelimiter() {
        assertEquals("foo/foo/", commonPrefixBy('/', "foo/foo/", "foo/foo/"))
    }
}
