package lang.temper.common.diff

import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import kotlin.math.max
import kotlin.math.min

object Diff {
    enum class ChangeType {
        Deletion,
        Addition,
        Unchanged,
    }

    data class Change<out T>(
        val type: ChangeType,
        val leftIndex: Int,
        val rightIndex: Int,
        val items: List<T>,
    ) : Structured {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("type") { value(type) }
            key("leftIndex") { value(leftIndex) }
            key("rightIndex") { value(rightIndex) }
            key("items") { value(items) }
        }
    }

    data class Patch<out T>(
        val changes: List<Change<T>>,
    ) : Structured {
        override fun destructure(structureSink: StructureSink) = structureSink.arr {
            changes.forEach { it.destructure(this) }
        }
    }

    fun differencesBetween(
        left: CharSequence,
        right: CharSequence,
        eq: (String, String) -> Boolean = { a, b -> a == b },
    ): Patch<String> = differencesBetween(
        left = left.split(lineTerminator),
        right = right.split(lineTerminator),
        eq = eq,
    )

    fun <T> differencesBetween(
        left: List<T>,
        right: List<T>,
        eq: (T, T) -> Boolean = { a, b -> a == b },
    ): Patch<T> {
        /**
         * A linked-list form of a path through the edit graph
         * in reverse order.
         */
        data class DiffPath(
            val leftIndex: Int,
            val rightIndex: Int,
            val changeType: ChangeType,
            val previous: DiffPath?,
        )

        // Comments starting with "// > " are quotes from
        // "An O(ND) Difference Algorithm and Its Variations",
        // https://www.xmailserver.org/diff2.pdf

        val leftLength = left.size
        val rightLength = right.size

        // We're doing a breadth-first search, so there's no need to
        // visit a vertex twice.
        // The reached array keeps track of which we've reached.
        val reachedRowLength = leftLength + 1
        val reachedColLength = rightLength + 1
        val reached = BooleanArray(reachedRowLength * reachedColLength)
        // This has a bit for each vertex in the edit graph.
        // > The edit graph for A and B has a vertex at each point in the grid
        // > (x,y), x∈[0,N] and y∈[0,M]. The vertices of the edit graph are
        // > connected by horizontal, vertical, and diagonal directed edges
        // > to form a directed acyclic graph.

        // Keep track of paths that we need to examine and/or expand.
        // Diagonals are free but right and down transitions cost one.
        val costZeroEdges = ArrayDeque<DiffPath?>()
        costZeroEdges.add(null)
        val costOneEdges = ArrayDeque<DiffPath>()
        // > The problem of finding the longest common subsequence (LCS) is
        // > equivalent to finding a path from (0,0) to (N,M) with the maximum
        // > number of diagonal edges
        // So we allocated an NxM array of reached edges, and we proceed
        // breadth-first, but process diagonal edges before any non-diagonals.
        // That lets us find the path with the fewest down and right transitions
        // in the edit graph that reaches (M, N).

        while (true) {
            // Prefer zero cost paths to cost-one paths so that we
            // always expand the lowest cost path first.
            val diffPath: DiffPath? = when {
                costZeroEdges.isNotEmpty() -> costZeroEdges.removeFirst()
                else -> costOneEdges.removeFirstOrNull()
            }

            val leftIndex = diffPath?.leftIndex ?: 0
            val rightIndex = diffPath?.rightIndex ?: 0
            if (leftIndex == leftLength && rightIndex == rightLength) {
                // We reached the end.
                // Replay the path through the edit graph into an edit script.

                // Unroll the linked-list that's in reverse order onto an array.
                val pathElements = mutableListOf<DiffPath>()
                var pathElement: DiffPath? = diffPath
                while (pathElement != null) {
                    pathElements.add(pathElement)
                    pathElement = pathElement.previous
                }
                pathElements.reverse()

                // Group runs of steps by changeType and turn them into
                // change elements.
                var leftPatchIndex = 0
                var rightPatchIndex = 0
                val changes = mutableListOf<Change<T>>()
                val n = pathElements.size
                var end: Int
                var i = 0
                while (i < n) {
                    val changeType = pathElements[i].changeType
                    // Find run with same changeType.
                    end = i + 1
                    while (end < n && changeType == pathElements[end].changeType) {
                        ++end
                    }
                    val nItems = end - i
                    val items: List<T>
                    var nLeftItems = 0
                    var nRightItems = 0
                    if (changeType == ChangeType.Addition) {
                        items = right.subList(rightPatchIndex, rightPatchIndex + nItems)
                        nRightItems = nItems
                    } else {
                        items = left.subList(leftPatchIndex, leftPatchIndex + nItems)
                        if (changeType == ChangeType.Unchanged) {
                            nRightItems = nItems
                        }
                        nLeftItems = nItems
                    }
                    changes.add(
                        Change(
                            changeType,
                            leftPatchIndex,
                            rightPatchIndex,
                            items,
                        ),
                    )
                    leftPatchIndex += nLeftItems
                    rightPatchIndex += nRightItems

                    i = end
                }
                return Patch(changes.toList())
            }

            // Add adjacent diffPaths for the next possible addition, deletion,
            // or unchanged transition where possible.
            if (leftIndex < leftLength) {
                if (rightIndex < rightLength) {
                    val sameReachedIndex =
                        leftIndex + 1 + ((rightIndex + 1) * reachedRowLength)
                    if (!reached[sameReachedIndex] && eq(left[leftIndex], right[rightIndex])) {
                        reached[sameReachedIndex] = true
                        costZeroEdges.add(
                            DiffPath(
                                leftIndex + 1,
                                rightIndex + 1,
                                ChangeType.Unchanged,
                                diffPath,
                            ),
                        )
                    }
                }
                val delReachedIndex: Int = leftIndex + 1 + rightIndex * reachedRowLength
                if (!reached[delReachedIndex]) {
                    reached[delReachedIndex] = true
                    costOneEdges.add(
                        DiffPath(
                            leftIndex + 1,
                            rightIndex,
                            ChangeType.Deletion,
                            diffPath,
                        ),
                    )
                }
            }
            if (rightIndex < rightLength) {
                val addReachedIndex = leftIndex + (rightIndex + 1) * reachedRowLength
                if (!reached[addReachedIndex]) {
                    reached[addReachedIndex] = true
                    costOneEdges.add(
                        DiffPath(
                            leftIndex,
                            rightIndex + 1,
                            ChangeType.Addition,
                            diffPath,
                        ),
                    )
                }
            }
        }
    }

    fun <T> formatPatch(
        patch: Patch<T>,
        /** Count of unchanged lines included as context on each side of actual changes. */
        context: Int = Int.MAX_VALUE,
        render: (T) -> CharSequence = { "$it" },
    ) = toStringViaBuilder {
        formatPatchTo(
            patch = patch,
            context = context,
            out = it,
            render = render,
        )
    }

    fun <T> formatPatchTo(
        patch: Patch<T>,
        /** Count of unchanged lines included as context on each side of actual changes. */
        context: Int = Int.MAX_VALUE,
        out: Appendable,
        render: (T) -> CharSequence = { "$it" },
    ) {
        val changes = patch.changes
        if (
            changes.isEmpty() ||
            changes.size == 1 && changes[0].type == ChangeType.Unchanged
        ) {
            // No diff
            return
        }

        // Break changes into hunks.
        // A hunk is a series of changes that can be shown together.
        // Hunks start with range information: en.wikipedia.org/wiki/Diff#Unified_format
        val hunks = mutableListOf<List<Change<T>>>()
        if (context == Int.MAX_VALUE) {
            hunks.add(changes)
        } else {
            // Split hunks around unchanged.
            var lastSplit = 0 // Index into changes
            for (index in changes.indices) {
                val change = changes[index]
                val type = change.type
                // Do we split a hunk after change?
                val endHunk: Boolean = when {
                    index == changes.lastIndex -> true
                    type != ChangeType.Unchanged -> false
                    index == 0 -> false
                    // We include the whole unchanged range if its
                    // size is not greater than the context, or if
                    // we need context before and after and its size
                    // does not exceed twice the context.
                    change.items.size.toLong() <= context.toLong() * 2L -> false
                    else -> true
                }

                if (endHunk) {
                    hunks.add(
                        (lastSplit..index).map { i ->
                            val c = changes[i]
                            val isUnchanged = c.type == ChangeType.Unchanged
                            val offset = if (isUnchanged && i == lastSplit) {
                                max(0, c.items.size - context)
                            } else {
                                0
                            }
                            val limit = if (isUnchanged && i == index) {
                                min(c.items.size, context)
                            } else {
                                c.items.size
                            }
                            c.copy(
                                leftIndex = c.leftIndex + offset,
                                rightIndex = c.rightIndex + offset,
                                items = c.items.subList(offset, limit),
                            )
                        },
                    )
                    // Store the last split index so that we can possibly reuse
                    // the tail of change as context for any next hunk.
                    lastSplit = index
                }
            }
        }

        hunks.forEach { hunk ->
            var leftLines = 0
            var rightLines = 0
            hunk.forEach {
                when (it.type) {
                    ChangeType.Deletion -> leftLines += it.items.size
                    ChangeType.Addition -> rightLines += it.items.size
                    ChangeType.Unchanged -> {
                        leftLines += it.items.size
                        rightLines += it.items.size
                    }
                }
            }

            // See range information at en.wikipedia.org/wiki/Diff#Unified_format
            out.append("@@ -")
            out.append("${hunk.first().leftIndex}")
            if (leftLines != 1) {
                out.append(',')
                out.append("$leftLines")
            }
            out.append(" +")
            out.append("${hunk.first().rightIndex}")
            if (rightLines != 1) {
                out.append(',')
                out.append("$rightLines")
            }
            out.append(" @@\n")
            // Output the elements in the hunk, prefixing each line.
            hunk.forEach { change ->
                val changeType = change.type
                val items = change.items
                val prefix = when (changeType) {
                    ChangeType.Addition -> '+'
                    ChangeType.Deletion -> '-'
                    ChangeType.Unchanged -> ' '
                }

                for (item in items) {
                    out.append(prefix)

                    // Prefix subsequent lines in rendered with ':'
                    val renderedItem = render(item)
                    var i = 0
                    var pos = 0
                    val n = renderedItem.length
                    while (i < n) {
                        val c = renderedItem[i]
                        i += when (c) {
                            '\n', '\r' -> {
                                val terminatorLen =
                                    if (c == '\r' && i + 1 < n && '\n' == renderedItem[i + 1]) {
                                        2 // CR LF
                                    } else {
                                        1
                                    }
                                out.append(renderedItem, pos, i + terminatorLen)
                                    .append(':')
                                pos = i + terminatorLen
                                terminatorLen
                            }
                            else -> 1
                        }
                    }
                    out.append(renderedItem, pos, n)

                    out.append('\n')
                }
            }
        }
    }
}

private val lineTerminator = Regex("\n|\r\n?")
