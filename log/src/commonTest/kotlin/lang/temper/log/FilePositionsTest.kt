@file:Suppress("MagicNumber")

package lang.temper.log

import lang.temper.common.testCodeLocation
import kotlin.test.Test
import kotlin.test.assertEquals

class FilePositionsTest {
    @Test
    fun compareToSimpleLinearScan() {
        val source = """
        foo();
        bar(); // Wot
        baz\r\n

        ;
        """.trimIndent()

        val positions = FilePositions.fromSource(testCodeLocation, source)

        var line = 1
        var charInLine = 0
        for (i in 0..source.length) {
            val want = FilePosition(line = line, charInLine = charInLine)
            val got = positions.filePositionAtOffset(i)
            assertEquals(want, got, "i=$i")

            val c = source.getOrNull(i)
            if (c == '\n' || (c == '\r' && !(i + 1 < source.length && source[i + 1] == '\n'))) {
                line += 1
                charInLine = 0
            } else {
                charInLine += 1
            }
        }
    }

    @Test
    fun compareInverseCalculations() {
        val source = """
        foo();
        bar(); // Wot
        baz\r\n

        ;
        """.trimIndent()

        val positions = FilePositions.fromSource(testCodeLocation, source)

        for (i in 0..source.length) {
            val coord = positions.filePositionAtOffset(i)
            val inverse = positions.offsetAtFilePosition(coord)
            assertEquals(i, inverse)
        }
    }

    @Test
    fun crazyCoords() {
        val source = """
        foo();
        bar(); // Wot
        baz\r\n

        ;
        """.trimIndent()

        val positions = FilePositions.fromSource(testCodeLocation, source)
        fun calcOffset(line: Int, charInLine: Int) =
            positions.offsetAtFilePosition(FilePosition(line, charInLine))

        assertEquals(null, calcOffset(0, 1))
        assertEquals(null, calcOffset(1, 30))
        assertEquals(null, calcOffset(30, 1))
        // We don't actually track the last line, so for now, fun results are possible.
        assertEquals(60, calcOffset(5, 30))

        // These also are crazy. For now, just track current behavior.
        // If we get more careful, these tests will need fixed to track improved behavior.
        assertEquals(FilePosition(1, -10), positions.filePositionAtOffset(-10))
        assertEquals(FilePosition(5, 30), positions.filePositionAtOffset(60))
    }
}
