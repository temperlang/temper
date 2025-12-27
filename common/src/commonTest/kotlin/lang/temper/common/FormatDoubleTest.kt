package lang.temper.common

import kotlin.test.Test

class FormatDoubleTest {

    private fun assertFormattedDoubles(
        want: List<Pair<Int, String>>,
        inp: Double,
    ) {
        val got = want.map { (n, _) ->
            n to formatDouble(inp, n)
        }

        assertStringsEqual(
            want.joinToString("\n"),
            got.joinToString("\n"),
        )
    }

    @Test
    fun wholeNumber() {
        assertFormattedDoubles(
            inp = -3.0,
            want = listOf(
                0 to "-3.",
                1 to "-3.0",
                2 to "-3.0",
                3 to "-3.0",
                4 to "-3.0",
                5 to "-3.0",
            ),
        )
    }

    @Test
    fun lotsOfDecimals() {
        assertFormattedDoubles(
            inp = 123.456789,
            want = listOf(
                0 to "123.",
                1 to "123.4",
                2 to "123.45",
                3 to "123.456",
                4 to "123.4567",
                5 to "123.45678",
                6 to "123.456789",
                7 to "123.456789",
                8 to "123.456789",
                9 to "123.456789",
                1_000_000 to "123.456789",
            ),
        )
    }

    @Test
    fun negFewDecimals() {
        assertFormattedDoubles(
            inp = -1.0125,
            want = listOf(
                0 to "-1.",
                1 to "-1.0",
                2 to "-1.01",
                3 to "-1.012",
                4 to "-1.0125",
                5 to "-1.0125",
                6 to "-1.0125",
                7 to "-1.0125",
                8 to "-1.0125",
                9 to "-1.0125",
                1_000_000 to "-1.0125",
            ),
        )
    }

    @Test
    fun scientific() {
        assertFormattedDoubles(
            inp = 1.5e300,
            want = listOf(
                0 to "1.e+300",
                1 to "1.5e+300",
                2 to "1.5e+300",
                3 to "1.5e+300",
                9 to "1.5e+300",
                1_000_000 to "1.5e+300",
            ),
        )
    }

    @Test
    fun lessThanOneSci() {
        assertFormattedDoubles(
            inp = 1.5e-300,
            want = listOf(
                0 to "1.e-300",
                1 to "1.5e-300",
                2 to "1.5e-300",
                3 to "1.5e-300",
                9 to "1.5e-300",
                1_000_000 to "1.5e-300",
            ),
        )
    }
}
