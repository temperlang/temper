package lang.temper.common

import kotlin.test.Test

class AsciiCaseTest {
    @Test
    fun lowerCaseStr() {
        assertStringsEqual(
            " !\"#\$%&'()*+,-./0123456789:;<=>?@abcdefghijklmnopqrstuvwxyz[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~",
            " !\"#\$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
                .asciiLowerCase(),
        )
        assertStringsEqual("", "".asciiLowerCase())
        assertStringsEqual("123", "123".asciiLowerCase())
        assertStringsEqual("foo", "foo".asciiLowerCase())
        assertStringsEqual("foo", "FOO".asciiLowerCase())
        assertStringsEqual("foo", "Foo".asciiLowerCase())
        assertStringsEqual("foo", "foO".asciiLowerCase())
        // Check that special case rules not applied.
        assertStringsEqual("\u0130", "\u0130".asciiLowerCase())
        assertStringsEqual("\u0131", "\u0131".asciiLowerCase())
    }

    @Test
    fun upperCaseStr() {
        assertStringsEqual(
            " !\"#\$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`ABCDEFGHIJKLMNOPQRSTUVWXYZ{|}~",
            " !\"#\$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
                .asciiUpperCase(),
        )
        assertStringsEqual("", "".asciiUpperCase())
        assertStringsEqual("123", "123".asciiUpperCase())
        assertStringsEqual("FOO", "foo".asciiUpperCase())
        assertStringsEqual("FOO", "FOO".asciiUpperCase())
        assertStringsEqual("FOO", "Foo".asciiUpperCase())
        assertStringsEqual("FOO", "foO".asciiUpperCase())
        assertStringsEqual("\u0130", "\u0130".asciiUpperCase())
        assertStringsEqual("\u0131", "\u0131".asciiUpperCase())
    }

    @Test
    fun titleCaseStr() {
        assertStringsEqual("", "".asciiTitleCase())
        assertStringsEqual("123", "123".asciiTitleCase())
        assertStringsEqual("Foo", "foo".asciiTitleCase())
        assertStringsEqual("Foo", "Foo".asciiTitleCase())
        assertStringsEqual("FOO", "FOO".asciiTitleCase())
        assertStringsEqual("\u0130", "\u0130".asciiTitleCase())
        assertStringsEqual("\u0131", "\u0131".asciiTitleCase())
    }

    @Test
    fun unTitleCaseStr() {
        assertStringsEqual("", "".asciiUnTitleCase())
        assertStringsEqual("123", "123".asciiUnTitleCase())
        assertStringsEqual("foo", "foo".asciiUnTitleCase())
        assertStringsEqual("foo", "Foo".asciiUnTitleCase())
        assertStringsEqual("fOO", "FOO".asciiUnTitleCase())
        assertStringsEqual("\u0130", "\u0130".asciiUnTitleCase())
        assertStringsEqual("\u0131", "\u0131".asciiUnTitleCase())
    }
}
