package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MinHeapTest {
    @Test
    fun fuzzTestMinHeap() {
        withRandomForTest { prng ->
            var runsStarted = 0
            while (runsStarted < 1_000) {
                runsStarted += 1

                // We compare the behavior of our MinHeap to that of a stock data structure.
                // When picking the least from list, we just walk over the whole thing.
                val heap = MinHeap(naturalOrder<Int>())
                val list = mutableListOf<Int>()

                val nSteps = 10 + prng.nextInt(100)
                for (step in 0..nSteps) {
                    assertEquals(list.size, heap.size)
                    if (list.isEmpty()) {
                        val e = prng.nextInt(0, 10_000)
                        list.add(e)
                        heap.add(e)
                    } else {
                        when (val action = prng.nextInt(4)) {
                            0, 1 -> {
                                val pop = action == 0
                                val heapMin = if (pop) {
                                    heap.pop()
                                } else {
                                    heap.peek()
                                }
                                var minIndex = -1
                                var listMin: Int? = null
                                for (i in list.indices) {
                                    val x = list[i]
                                    if (listMin == null || x < listMin) {
                                        listMin = x
                                        minIndex = i
                                    }
                                }
                                if (pop) {
                                    list.removeAt(minIndex)
                                }
                                require(listMin == heapMin) {
                                    "run: $runsStarted, step: $step of $nSteps\n" +
                                        "list=$list\nheap=$heap\n" +
                                        "min(list)=$listMin\nmin(heap)=$heapMin\n"
                                }
                            }
                            2, 3 -> {
                                val e = prng.nextInt(0, 10_000)
                                list.add(e)
                                heap.add(e)
                            }
                        }
                    }
                }
                list.sort()
                while (list.isNotEmpty()) {
                    assertEquals(list.size, heap.size)
                    val listMin = list.removeAt(0)
                    val heapMin = heap.pop()
                    assertEquals(listMin, heapMin)
                }
                assertTrue(list.isEmpty())
                assertTrue(heap.isEmpty())
            }
        }
    }
}
