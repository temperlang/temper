package lang.temper.common

import kotlin.test.Test

class SPrintfTest {
    @Test
    fun simpleSubstitution() {
        assertStringsEqual(
            "(a)(b)(c)",
            sprintf("(%s)(%s)(%s)", listOf("a", "b", "c")),
        )
    }

    // These tests borrowed from https://en.wikipedia.org/wiki/Printf_format_string
    @Test
    fun oneInt() {
        assertStringsEqual(
            "Your age is 64",
            sprintf("Your age is %d", listOf(64)),
        )
    }

    @Test
    fun parameterFields() {
        assertStringsEqual(
            "17 0x11; 16 0x10",
            sprintf("%2\$d %2\$#x; %1\$d %1\$#x", listOf(16, 17)),
        )
    }

    @Test
    fun zeroFlag() {
        assertStringsEqual(
            "   3",
            sprintf("%4X", listOf(3)),
        )
        assertStringsEqual(
            "0003",
            sprintf("%04X", listOf(3)),
        )
    }

    @Test
    fun dynamicWidth() {
        assertStringsEqual(
            "   10",
            sprintf("%*d", listOf(5, 10)),
        )
    }

    @Test
    fun dynamicPrecision() {
        assertStringsEqual(
            "abc",
            sprintf("%.*s", listOf(3, "abcdef")),
        )
    }

    @Test
    fun paddingDirection() {
        assertStringsEqual(
            "     Hello",
            sprintf("%10s", listOf("Hello")),
        )
        assertStringsEqual(
            "Hello     ",
            sprintf("%-10s", listOf("Hello")),
        )
    }

    @Test
    fun decimalPoints() {
        for (
        (n, want) in listOf(
            0.0 to "0.0",
            1.0 to "1.0",
            0.5 to "0.5",
            -0.5 to "-0.5",
            (1.0 / 3.0) to "0.3333333333333333",
            (2.0 / 3.0) to "0.6666666666666666",
        )
        ) {
            assertStringsEqual(
                want,
                sprintf(SAFE_DOUBLE_FORMAT_STRING, listOf(n)),
            )
        }
    }

    @Test
    fun customFormatting() {
        val value = object : Formattable {
            override fun toString(): String = "(Object good for debugging)"

            override fun preformat(): CharSequence = "(baz)"
        }
        assertStringsEqual(
            "foo (baz) bar",
            sprintf("foo %s bar", listOf(value)),
        )
    }

    @Test
    fun looksThroughCollections() {
        assertStringsEqual(
            "ints={00: 01, 02: 03, 04: null} ; doubles=[+0.0, +0.1, +0.2, +0.3]",
            sprintf(
                "ints=%02x ; doubles=%+04.2f",
                listOf(
                    mapOf(0 to 1, 2 to 3, 4 to null),
                    listOf(0.0, 0.1, 0.2, 0.3),
                ),
            ),
        )
    }

    @Test
    fun floatExponentIndicatorCase() {
        assertStringsEqual(
            "e -> 1.0e+100, E -> 1.0E+100, f -> 1.0e+100, F -> 1.0E+100",
            sprintf(
                "e -> %e, E -> %E, f -> %f, F -> %F",
                listOf(1.0e100, 1.0e100, 1.0e100, 1.0e100),
            ),
        )
    }

    @Test
    fun decimalPointsForWholeNumbersWithFormatModeFloat() {
        assertStringsEqual(
            "5.0 ; -5.0",
            sprintf(
                "%f ; %F",
                listOf(5, -5),
            ),
        )
    }
}
