package lang.temper.value

import lang.temper.lexer.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UnpackValueTest {
    @Test
    fun bracketedUEscapes() {
        // Taken from Unicode collation tests
        assertEquals(
            Value("\u0385\u0021", TString),
            unpackValue("""\u{0385,0021}""", TokenType.QuotedString),
        )

        // Trailing commas ok
        assertEquals(
            Value("\u0385\u0021", TString),
            unpackValue("""\u{0385,0021,}""", TokenType.QuotedString),
        )

        // Dupe or leading commas not
        assertIs<Fail>(
            unpackValue("""\u{,0385,0021}""", TokenType.QuotedString),
        )

        // Trailing commas ok
        assertIs<Fail>(
            unpackValue("""\u{0385,,0021}""", TokenType.QuotedString),
        )

        // Need curly at end
        assertIs<Fail>(
            unpackValue("""\u{0385,,0021""", TokenType.QuotedString),
        )

        // Too big to not fail
        assertIs<Fail>(
            unpackValue("""\u{110000}""", TokenType.QuotedString),
        )

        // Just right
        assertEquals(
            Value("\uDBFF\uDFFF", TString),
            unpackValue("""\u{10FFFF}""", TokenType.QuotedString),
        )

        // If you like zeroes, you do you.
        assertEquals(
            Value("\u0000\u0000\u0000", TString),
            unpackValue("""\u{0000,0,0000000000}""", TokenType.QuotedString),
        )
    }

    @Test
    fun radixes() {
        for (
        s in listOf(
            "0O_375",
            "0o375",
            "0xFD",
            "0Xfd",
            "253",
            "2_53",
            "0b_1111_1101",
            "0B11111101",
        )
        ) {
            assertEquals(
                Value(253, TInt),
                unpackValue(s, TokenType.Number),
                s,
            )
        }
    }

    @Test
    fun booOctal() = assertEquals(
        Fail,
        unpackValue("010", TokenType.Number),
    )

    @Test
    fun yayOctal() = assertEquals(
        Value(8, TInt),
        unpackValue("0o10", TokenType.Number),
    )

    @Test
    fun leadingZeroAllowedInFloat() = assertEquals(
        Value(0.1, TFloat64),
        unpackValue("0.1", TokenType.Number),
    )

    @Test
    fun badDoubles() {
        assertIs<Fail>(unpackValue("1e", TokenType.Number))
        assertIs<Fail>(unpackValue("1e-", TokenType.Number))
        assertIs<Fail>(unpackValue("1.0x", TokenType.Number))
    }

    @Test
    fun doubleSuffixes() {
        assertEquals(
            Value(1.0, TFloat64),
            unpackValue("1f64", TokenType.Number),
        )
    }

    @Test
    fun intSuffixes() {
        assertEquals(Value(1, TInt), unpackValue("1", TokenType.Number))
        assertEquals(Value(1, TInt), unpackValue("1i32", TokenType.Number))
        assertEquals(Value(1, TInt64), unpackValue("1i64", TokenType.Number))
        assertEquals(Value(1, TInt64), unpackValue("0x1i64", TokenType.Number))
        assertEquals(Value(10, TInt64), unpackValue("0xa_i64", TokenType.Number))
    }

    @Test
    fun binary() {
        assertEquals(
            Value(0b10101, TInt),
            unpackValue("0b10101", TokenType.Number),
        )
        assertEquals(
            Value(0b010101, TInt),
            unpackValue("0b010101", TokenType.Number),
        )
    }
}
