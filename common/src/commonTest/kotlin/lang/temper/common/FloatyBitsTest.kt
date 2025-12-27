package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatyBitsTest {
    @Test
    fun testPosZero() {
        (0.0).let {
            assertTrue(it.isPosZero())
            assertFalse(it.isNegZero())
            assertFalse(it.isNaN())
        }
    }

    @Test
    fun testParsePosZero() {
        ParseDouble(0.0).let {
            assertEquals(ParseDouble.PositiveZero, it)
            assertTrue(it.value.isPosZero())
            assertEquals(it.value, 0.0)
            assertTrue(it.isPos)
            assertFalse(it.isNeg)
            assertTrue(it.isZero)
            assertFalse(it.isInfinite)
        }
    }

    @Test
    fun testNegZero() {
        (-0.0).let {
            assertFalse(it.isPosZero())
            assertTrue(it.isNegZero())
            assertFalse(it.isNaN())
        }
    }

    @Test
    fun testParseNegZero() {
        ParseDouble(-0.0).let {
            assertEquals(ParseDouble.NegativeZero, it)
            assertTrue(it.value.isNegZero())
            assertEquals(-0.0, it.value)
            assertFalse(it.isPos)
            assertTrue(it.isNeg)
            assertTrue(it.isZero)
            assertFalse(it.isInfinite)
        }
    }

    @Test
    fun testNaN() {
        (Double.NaN).let {
            assertFalse(it.isPosZero())
            assertFalse(it.isNegZero())
            assertTrue(it.isNaN())
        }
    }

    @Test
    fun testParseNaN() {
        ParseDouble(Double.NaN).let {
            assertEquals(ParseDouble.NaN, it)
            assertTrue(it.value.isNaN())
            assertFalse(it.isPos)
            assertFalse(it.isNeg)
            assertFalse(it.isZero)
            assertFalse(it.isInfinite)
        }
    }

    @Test
    fun testPosInfinity() {
        (Double.POSITIVE_INFINITY).let {
            assertFalse(it.isPosZero())
            assertFalse(it.isNegZero())
            assertFalse(it.isNaN())
        }
    }

    @Test
    fun testParsePosInfinity() {
        ParseDouble(Double.POSITIVE_INFINITY).let {
            assertEquals(ParseDouble.PositiveInfinity, it)
            assertEquals(Double.POSITIVE_INFINITY, it.value)
            assertTrue(it.isPos)
            assertFalse(it.isNeg)
            assertFalse(it.isZero)
            assertTrue(it.isInfinite)
        }
    }

    @Test
    fun testNegInfinity() {
        (Double.POSITIVE_INFINITY).let {
            assertFalse(it.isPosZero())
            assertFalse(it.isNegZero())
            assertFalse(it.isNaN())
        }
    }

    @Test
    fun testParseNegInfinity() {
        ParseDouble(Double.NEGATIVE_INFINITY).let {
            assertEquals(ParseDouble.NegativeInfinity, it)
            assertEquals(Double.NEGATIVE_INFINITY, it.value)
            assertFalse(it.isPos)
            assertTrue(it.isNeg)
            assertFalse(it.isZero)
            assertTrue(it.isInfinite)
        }
    }

    @Test
    fun testParseFortyTwo() {
        ParseDouble(42.0).let {
            assertTrue(it is ParseDouble.RegularPositive)
            assertEquals(42.0, it.value)
            assertTrue(it.isPos)
            assertFalse(it.isNeg)
            assertFalse(it.isZero)
            assertFalse(it.isInfinite)
        }
    }

    @Test
    fun testParseNegativeFortyTwo() {
        ParseDouble(-42.0).let {
            assertTrue(it is ParseDouble.RegularNegative)
            assertEquals(-42.0, it.value)
            assertFalse(it.isPos)
            assertTrue(it.isNeg)
            assertFalse(it.isZero)
            assertFalse(it.isInfinite)
        }
    }
}
