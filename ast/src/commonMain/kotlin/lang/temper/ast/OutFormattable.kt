package lang.temper.ast

import lang.temper.common.AppendingTextOutput
import lang.temper.common.toStringViaBuilder
import lang.temper.format.CodeFormatter
import lang.temper.format.FormattableTree
import lang.temper.format.FormattingHints
import lang.temper.format.IndexableFormattableTreeElement
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.log.FilePositions

/** Base class for ASTs produced by a backend. */
interface OutFormattable<KIND : OutFormattable<KIND>> : FormattableTree {
    val content: Any? get() = null
    fun formattingHints(): FormattingHints
    val childMemberRelationships: ChildMemberRelationships

    val childCount: Int
    fun childOrNull(index: Int): KIND? {
        var n = index
        if (n < 0) {
            return null
        }
        for (p in childMemberRelationships.accessors) {
            n -= when (val value = p(this)) {
                null -> 0
                is List<*> -> {
                    val size = value.size
                    if (n < size) {
                        // Unsound but true for generated tree types
                        @Suppress("UNCHECKED_CAST")
                        return value[n] as KIND
                    }
                    size
                }
                is OutTree<*> -> if (n == 0) {
                    // Unsound but true for generated tree types
                    @Suppress("UNCHECKED_CAST")
                    return value as KIND
                } else {
                    1
                }
                else -> throw ClassCastException("$value")
            }
        }
        return null
    }

    fun child(index: Int): KIND =
        childOrNull(index) ?: throw IndexOutOfBoundsException(
            "$index of $childCount in ${this::class.simpleName}",
        )

    val children: Iterable<KIND> get() = object : Iterable<KIND> {
        override fun iterator(): Iterator<KIND> = object : Iterator<KIND> {
            private var i = 0
            private var pending: KIND? = null

            override fun hasNext(): Boolean = needPending() != null

            override fun next(): KIND {
                val next = needPending() ?: throw NoSuchElementException()
                pending = null
                return next
            }

            private fun needPending(): KIND? {
                if (pending != null) { return pending }
                val n = childCount
                while (i < n) {
                    val t = childOrNull(i)
                    i += 1
                    if (t != null) {
                        pending = t
                        break
                    }
                }
                return pending
            }
        }
    }

    override val formatElementCount: Int get() = 0
    override fun formatElement(index: Int): IndexableFormattableTreeElement =
        throw IndexOutOfBoundsException("$index")

    fun toString(
        filePositions: FilePositions = FilePositions.nil,
        singleLine: Boolean? = null,
        @Suppress("KotlinConstantConditions") // parameter default expression
        isTtyLike: Boolean = AppendingTextOutput.DEFAULT_IS_TTY_LIKE,
    ): String {
        val formattingHints = this.formattingHints()
        return toStringViaTokenSink(
            formattingHints = formattingHints,
            filePositions = filePositions,
            singleLine = singleLine
                ?: formattingHints.allowSingleLine && filePositions == FilePositions.nil,
            isTtyLike = isTtyLike,
        ) {
            formatTo(it)
        }
    }

    fun formatTo(tokenSink: TokenSink) {
        CodeFormatter(tokenSink).format(this, childCount != 0)
    }

    companion object {
        // Helpers for translations of `NodeType requires propertyName` clauses in out grammar specs
        fun <T : Any> propertyValueTruthy(x: T?) = x != null
        fun propertyValueTruthy(xs: Collection<*>?) = xs?.isEmpty() == false
    }
}

/**
 * Generic breadth-first search limited by a boundary condition. As this is inline, the search can be aborted by the
 * caller, but the descending block can also return `false` to skip the children of the current node.
 */
inline fun <KIND : OutFormattable<KIND>> KIND.boundaryDescent(descend: (KIND) -> Boolean) {
    val deque = ArrayDeque<KIND>()
    deque.addLast(this)

    while (true) {
        val node = deque.removeFirstOrNull() ?: break
        if (descend(node)) {
            for (idx in 0 until node.childCount) {
                node.childOrNull(idx)?.let(deque::addLast)
            }
        }
    }
}

/**
 * Existence check for a child node satisfying the predicate. Children of children are checked recursively.
 * Null children are ignored; if all children are null or no children, this predicate is false.
 */
fun <KIND : OutFormattable<KIND>> KIND.anyChildRecursive(predicate: (KIND) -> Boolean): Boolean =
    anyChildWithinRecursive({ true }, predicate)

/**
 * Existence check for a child node satisfying the predicate. Children of children are checked recursively.
 * The check won't consider child nodes if within(node) returns false.
 * Null children are ignored; if all children are null or no children, this predicate is false.
 */
fun <KIND : OutFormattable<KIND>> KIND.anyChildWithinRecursive(
    within: (KIND) -> Boolean,
    predicate: (KIND) -> Boolean,
): Boolean {
    this.boundaryDescent {
        if (predicate(it)) {
            return@anyChildWithinRecursive true
        }
        within(it)
    }
    return false
}

/** Like [anyChildWithinRecursive] but uses depth-first search. */
fun <KIND : OutFormattable<KIND>> KIND.anyChildDepth(
    within: (KIND) -> Boolean = { true },
    predicate: (KIND) -> Boolean = { false },
): Boolean {
    if (predicate(this)) {
        return true
    }
    if (within(this)) {
        for (index in 0 until childCount) {
            if (childOrNull(index)?.anyChildDepth(within, predicate) == true) {
                return true
            }
        }
    }
    return false
}

fun OutFormattable<*>.toLispy() = toStringViaBuilder {
    this.appendLispy(it)
}

fun OutFormattable<*>.appendLispy(sb: StringBuilder, indent: Int = 0) {
    repeat(indent) { sb.append("  ") }
    sb.append('(')
    sb.append(this::class.simpleName)
    val n = childCount
    if (n != 0) {
        for (i in 0 until n) {
            sb.append('\n')
            child(i).appendLispy(sb, indent + 1)
        }
    } else {
        sb.append(' ')
        sb.append(this.toString().replace('\n', ' '))
    }
    sb.append(')')
}
