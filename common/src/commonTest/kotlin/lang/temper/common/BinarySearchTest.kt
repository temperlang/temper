package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BinarySearchTest {

    @Test
    fun binarySearchInt() {
        val ints =
            listOf(-10, -9, 1, 5, 6, 7, 9, 11, 15, 19, 23, 72, 105, 622, 633, 634, 1011)
        val array = IntArray(ints.size) { ints[it] }
        for (target in -20..1024) {
            val x = binarySearch(array, target)
            var passed = false
            try {
                if (x >= 0) {
                    assertEquals(target, array[x])
                } else {
                    assertTrue(target !in ints)
                    val insPt = x.inv()
                    if (insPt == array.size) {
                        assertTrue(target > array[array.size - 1])
                    } else {
                        assertTrue(target < array[insPt])
                        if (insPt > 0) {
                            assertTrue(target > array[insPt - 1])
                        }
                    }
                }
                passed = true
            } finally {
                if (!passed) {
                    printErr("array=${array.asList()}\n\ttarget=$target\n\tx=$x ~ ${ x.inv() }")
                }
            }
        }
    }

    @Test
    fun binarySearchChar() {
        val chars =
            listOf(1, 5, 6, 7, 9, 11, 15, 19, 23, 72, 105, 622, 633, 634, 1011).map {
                it.toChar()
            }
        val array = CharArray(chars.size) { chars[it] }
        for (targetInt in 0..1024) {
            val target = targetInt.toChar()
            val x = binarySearch(array, target)
            var passed = false
            try {
                if (x >= 0) {
                    assertEquals(target, array[x])
                } else {
                    assertTrue(target !in chars)
                    val insPt = x.inv()
                    if (insPt == array.size) {
                        assertTrue(target > array[array.size - 1])
                    } else {
                        assertTrue(target < array[insPt])
                        if (insPt > 0) {
                            assertTrue(target > array[insPt - 1])
                        }
                    }
                }
                passed = true
            } finally {
                if (!passed) {
                    printErr("array=${array.asList()}\n\ttarget=$target\n\tx=$x ~ ${ x.inv() }")
                }
            }
        }
    }
}
