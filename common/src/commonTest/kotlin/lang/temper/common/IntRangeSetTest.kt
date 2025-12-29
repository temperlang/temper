package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertTrue

const val DEBUG = false
private inline fun debug(f: () -> String) {
    @Suppress("ConstantConditionIf")
    if (DEBUG) {
        printErr(f())
    }
}

class IntRangeSetTest {
    @Test
    fun rangeIn() {
        val s = IntRangeSet.new(49..55)
        assertTrue(s.containsAll(49..51))
    }

    @Test
    fun fuzzTest() {
        withRandomForTest { prng ->
            var runsStarted = 0
            while (runsStarted < 1000) {
                runsStarted += 1

                // We compare the behavior of our MinHeap to that of a stock data structure.
                // When picking the least from list, we just walk over the whole thing.
                var rset = IntRangeSet.empty
                val mset = mutableSetOf<Int>()

                fun addRandomRange() {
                    val left = prng.nextInt(0, 1000)
                    val right = left + prng.nextInt(0, 10)
                    val r = IntRange(left, right)
                    rset += r
                    for (i in left..right) {
                        mset.add(i)
                    }
                    debug { "Add [$left, $right]" }
                }

                for (step in 0..20) {
                    debug { "step $step" }
                    if (mset.isEmpty()) {
                        addRandomRange()
                    } else {
                        when (prng.nextInt(0, 5)) {
                            0 -> addRandomRange()
                            1 -> {
                                val x = prng.nextInt(-10, 1011)
                                debug { "Check has $x" }
                                require((x in mset) == (x in rset)) {
                                    "mset=$mset\nrset=$rset\nx=$x"
                                }
                            }
                            2 -> {
                                val first = prng.nextInt(-10, 1011)
                                val last = first + prng.nextInt(0, 10)
                                val r = IntRange(first, last)
                                debug { "Check has all $r" }
                                var msetContainsAll = true
                                for (i in r) {
                                    if (i !in mset) {
                                        msetContainsAll = false
                                        break
                                    }
                                }
                                val rsetContainsAll = rset.containsAll(r)
                                require(msetContainsAll == rsetContainsAll) {
                                    "mset=$mset\nrset=$rset\nr=$r" +
                                        "\n\tmHas=$msetContainsAll" +
                                        "\n\trHas=$rsetContainsAll\n"
                                }
                            }
                            3 -> {
                                val otherRanges = IntRangeSet.unionRanges(
                                    (0..(prng.nextInt(5))).map { _ ->
                                        val first = prng.nextInt(-10, 511)
                                        val last = first + prng.nextInt(0, 510)
                                        IntRange(first, last)
                                    },
                                )
                                debug { "Intersect with $otherRanges" }
                                mset.retainAll { it in otherRanges }
                                rset = IntRangeSet.intersection(rset, otherRanges)
                            }
                            4 -> {
                                val otherRanges = IntRangeSet.unionRanges(
                                    (0..(prng.nextInt(5))).map { _ ->
                                        val first = prng.nextInt(-10, 511)
                                        val last = first + prng.nextInt(0, 510)
                                        IntRange(first, last)
                                    },
                                )
                                debug { "Subtract $otherRanges" }
                                mset.retainAll { it !in otherRanges }
                                rset -= otherRanges
                            }
                        }
                    }
                    val exploded = mutableSetOf<Int>()
                    for (r in rset) {
                        for (i in r) {
                            exploded.add(i)
                        }
                    }
                    require(mset == exploded) {
                        "mset=$mset\nrset=$rset"
                    }
                }
            }
        }
    }
}
