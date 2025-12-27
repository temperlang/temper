package lang.temper.common

/** Allows representing simple, weight graphs. */
class WeightedAdjacencyTable<T>() {

    /**
     * Convenience constructor that [define]s the given nodes
     * and which also allows inferring of [T].
     */
    constructor(nodes: Iterable<T>) : this() {
        nodes.forEach { define(it) }
    }

    data class Edge<T>(
        val source: T,
        val target: T,
        val weight: Int,
    )

    private val edges = mutableMapOf<T, MutableList<Edge<T>>>()

    /** Defines a node.  Nodes that are not on the side of any edge must */
    fun define(node: T) {
        edges.getOrPut(node) {
            mutableListOf()
        }
    }

    /** Adds an edge from [source] to [target] with the given [weight]. */
    operator fun set(source: T, target: T, weight: Int) {
        edges.getOrPut(source) { mutableListOf() }
            .add(Edge(source = source, target = target, weight = weight))
        define(target)
    }

    fun copy(): WeightedAdjacencyTable<T> {
        val copy = WeightedAdjacencyTable<T>()
        for ((source, outgoing) in this.edges) {
            copy.edges[source] = outgoing.toMutableList()
        }
        return copy
    }

    operator fun get(source: T): List<Edge<T>> = edges[source] ?: emptyList()

    val nodes: Set<T> get() = edges.keys.toSet()

    /**
     * Returns nodes topologically sorted with sources after targets.
     * This is a valid weighted topological sort if you've already
     * [broken cycles][breakCycles].
     */
    fun partiallyOrder(): List<T> = partiallyOrder(
        items = edges.keys,
        afterMap = edges.mapValues { it.value.map { edge -> edge.target } },
    ) { it }

    fun breakCycles(): List<Edge<T>> {
        val broken = mutableListOf<Edge<T>>()
        val visited = mutableSetOf<T>()

        fun lookForCycle(node: T, path: Cons<Edge<T>>): Edge<T>? {
            if (node in visited) {
                // Found a cycle, break it at the minimum weight.
                var toBreak: Edge<T>? = null
                for (edge in path) {
                    if (toBreak == null || edge.weight < toBreak.weight) {
                        toBreak = edge
                    }
                }
                return toBreak!!
            }
            val edgesFromNode = edges[node]
            if (edgesFromNode != null) {
                visited.add(node)

                var i = 0
                while (i < edgesFromNode.size) {
                    val edge = edgesFromNode[i]
                    i += 1

                    val broke = lookForCycle(edge.target, Cons.NotEmpty(edge, path))
                    if (broke != null) {
                        if (broke === edge) {
                            broken.add(broke)
                            // Back up the increment above
                            // so that we revisit the next item after
                            // cutting out the broken edge
                            i -= 1
                            edgesFromNode.removeAt(i)
                        } else {
                            // Pop up to the higher stack-frame.
                            visited.remove(node)
                            return broke
                        }
                    }
                }

                visited.remove(node)
            }
            return null
        }

        for (source in edges.keys) {
            lookForCycle(source, Cons.Empty)
        }

        return broken.toList()
    }
}
