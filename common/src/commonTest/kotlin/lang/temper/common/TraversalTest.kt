package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals

class TraversalTest {
    @Test
    fun testBreadthFirstDequerator() {
        assertEquals(listOf(0, 1, 4, 8, 2, 5, 9, 3, 6, 7), exampleTree.breadthFirst().toList())
    }

    @Test
    fun testDepthFirstDequerator() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), exampleTree.depthFirst().toList())
    }
}

data class SimpleTree(
    val num: Int,
    val children: List<SimpleTree>,
) {
    constructor(num: Int, vararg st: SimpleTree) : this(num, st.toList())

    fun breadthFirst() = dequeIterable { deque ->
        val elem = deque.removeFirst()
        deque.addAll(elem.children)
        elem.num
    }

    fun depthFirst() = dequeIterable { deque ->
        val elem = deque.removeFirst()
        // Insert children at the top of the deque.
        deque.addAll(0, elem.children)
        elem.num
    }
}

typealias St = SimpleTree

val exampleTree =
    St(
        0,
        St(
            1,
            St(
                2,
                St(3),
            ),
        ),
        St(
            4,
            St(
                5,
                St(6),
                St(7),
            ),
        ),
        St(
            8,
            St(9),
        ),
    )
