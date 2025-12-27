package lang.temper.value

import lang.temper.common.invalidUnicodeString
import lang.temper.common.jsonEscaper
import lang.temper.common.toStringViaBuilder
import lang.temper.common.toUtf8Tolerant
import lang.temper.common.withRandomForTest
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class Utf8EquivalentStringComparatorTest {
    private val stringChunks = listOf(
        "",
        "\u0000",
        "a",
        "b",
        "c",
        "d",
        "e",
        "f",
        "g",
        "h",
        "i",
        "j",
        "k",
        "l",
        "m",
        "m",
        "o",
        "p",
        "q",
        "r",
        "s",
        "t",
        "u",
        "v",
        "w",
        "x",
        "y",
        "z",
        "\u007f",
        "\u0080",
        "\u00A0",
        "\u07FF",
        "\u0800",
        "\uD800\uDC00",
        "\uD800\uDD00",
        "\uD800\uDC00",
        "\uDBFF\uDEEE",
        "\uD800\uDFFF",
        invalidUnicodeString(""" "\uD800" """),
        invalidUnicodeString(""" "\uDBFF" """),
        invalidUnicodeString(""" "\uDA00" """),
        invalidUnicodeString(""" "\uDC00" """),
        invalidUnicodeString(""" "\uDC01" """),
        invalidUnicodeString(""" "\uDFFF" """),
        "\uE000",
        "\uFFFE",
        "\uFFFF",
    )

    private fun pseudoRandomString(prng: Random) = toStringViaBuilder { sb ->
        repeat(prng.nextInt(0, 10)) {
            sb.append(stringChunks[prng.nextInt(stringChunks.size)])
        }
    }

    // Compare on Javascript-IR appears to use subtraction so it's not always +-1
    private fun normalize(cmp: Int) = when {
        cmp < 0 -> -1
        cmp > 0 -> 1
        else -> 0
    }

    @Test
    fun simpleStrings() {
        assertEquals(
            -1,
            normalize(Utf8EquivalentStringComparator.compare("g\uD800", "\u00A0\udc00\uDc00l\uDc01\ud800v")),
        )
    }

    @Test
    fun equivalent() {
        withRandomForTest { prng ->
            repeat(50_000) {
                val a = pseudoRandomString(prng)
                val b = pseudoRandomString(prng)
                assertEquals(
                    normalize(Utf8ConvertingComparator.compare(a, b)),
                    normalize(Utf8EquivalentStringComparator.compare(a, b)),
                    message = "${ jsonEscaper.escape(a) } vs ${ jsonEscaper.escape(b) }",
                )
            }
        }
    }
}

internal object Utf8ConvertingComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val aBytes = toUtf8Tolerant(a)
        val bBytes = toUtf8Tolerant(b)
        val aSize = aBytes.size
        val bSize = bBytes.size
        val minSize = min(aSize, bSize)
        for (i in 0 until minSize) {
            val d = (aBytes[i].toInt() and 0xFF).compareTo(bBytes[i].toInt() and 0xFF)
            if (d != 0) { return d }
        }
        return aSize.compareTo(bSize)
    }
}
