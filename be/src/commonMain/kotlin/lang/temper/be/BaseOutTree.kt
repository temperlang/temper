package lang.temper.be

import lang.temper.ast.OutFormattable
import lang.temper.ast.OutTree
import lang.temper.log.FilePositions
import lang.temper.log.Position

abstract class BaseOutTree<TREE : OutTree<TREE>>(override val pos: Position) : OutTree<TREE> {
    override fun toString() = toString(filePositions = FilePositions.nil)

    override val parent: OutTree<TREE>? get() = _parent

    private var _childCountCache = -1
    private var _parent: BaseOutTree<TREE>? = null

    /** Called by subtypes to update parent-child linkages. */
    protected fun <T : TREE?> updateTreeConnection(oldChild: T?, newChild: T): T {
        if ((oldChild == null) != (newChild != null)) {
            _childCountCache = -1
        }
        // Cast is unsound but safe for generated code that shares a single sealed base class
        @Suppress("UNCHECKED_CAST")
        val baseOldChild = oldChild as BaseOutTree<TREE>?
        if (baseOldChild != null) {
            baseOldChild._parent = null
        }
        if (newChild != null) {
            var passed = false
            try {
                @Suppress("UNCHECKED_CAST")
                val baseNewChild = newChild as BaseOutTree<TREE>
                baseNewChild._parent = this
                passed = true
            } finally {
                if (!passed) {
                    // Be atomic.
                    baseOldChild?._parent = this
                }
            }
        }
        return newChild
    }

    /** Called by subtypes to update parent-child linkages. */
    protected fun <T : TREE> updateTreeConnections(
        childList: MutableList<T>,
        newChildren: Iterable<T>,
    ) {
        _childCountCache = -1
        val oldChildren = childList.toList()
        var passed = false
        try {
            childList.forEach { setParent(it, null) }
            childList.clear()
            newChildren.forEach { newChild ->
                @Suppress("UNCHECKED_CAST") // See caveat above
                val baseChild = newChild as BaseOutTree<TREE>
                check(baseChild.parent == null) {
                    "${baseChild::class.simpleName} already has parent ${
                        baseChild.parent!!::class.simpleName
                    }"
                }
                childList.add(newChild)
                baseChild._parent = this
            }
            passed = true
        } finally {
            if (!passed) {
                childList.forEach { setParent(it, null) }
                childList.clear()
                childList.addAll(oldChildren)
                childList.forEach { setParent(it, this) }
            }
        }
    }

    override val childCount: Int
        get() {
            val ccc = _childCountCache
            if (ccc >= 0) {
                return ccc
            }
            var n = 0
            for (p in childMemberRelationships.accessors) {
                n += when (val value = p(this)) {
                    null -> 0
                    is List<*> -> value.size
                    is OutFormattable<*> -> 1
                    else -> throw ClassCastException("$value")
                }
            }
            _childCountCache = n
            return n
        }

    companion object {
        private fun <TREE : OutTree<TREE>, T : TREE> setParent(
            child: T,
            parent: BaseOutTree<TREE>?,
        ) {
            @Suppress("UNCHECKED_CAST") // See caveat above
            val baseChild = child as BaseOutTree<TREE>
            baseChild._parent = parent
        }
    }
}
