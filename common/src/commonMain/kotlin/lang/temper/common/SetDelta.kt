package lang.temper.common

/**
 * A difference that can be applied to a *Set*.
 */
class SetDelta<T> private constructor(
    // Constructor is private so that we don't need to check the invariant
    // that added does not intersect removed
    val added: Set<T>,
    val removed: Set<T>,
) {
    val isChanged: Boolean get() = added.isNotEmpty() || removed.isNotEmpty()

    fun modify(destination: MutableCollection<T>) {
        destination.removeAll(removed)
        destination.addAll(added)
    }

    fun modified(base: Set<T>): Set<T> {
        val mutableSet = base.toMutableSet()
        modify(mutableSet)
        return mutableSet.toSet()
    }

    override fun toString(): String = "SetDelta(+$added, -$removed)"

    override fun hashCode(): Int = added.hashCode() + 31 * removed.hashCode()

    override fun equals(other: Any?): Boolean = other is SetDelta<*> &&
        added == other.added &&
        removed == other.removed

    companion object {
        fun <T> (Collection<T>).buildSetDelta(
            f: (MutableSet<T>).() -> Unit,
        ): SetDelta<T> {
            val b = Builder(this)
            b.f()
            return b.toDelta()
        }
    }

    private class Builder<T>(
        val base: Collection<T>,
    ) : AbstractMutableSet<T>() {
        override val size: Int get() = base.size + added.size - removed.size

        private val added = mutableSetOf<T>()
        private val removed = mutableSetOf<T>()

        override fun add(element: T): Boolean {
            var changed = removed.remove(element)
            if (element !in base) {
                changed = added.add(element) || changed
            }
            return changed
        }

        override fun remove(element: T): Boolean {
            var changed = added.remove(element)
            if (element in base) {
                changed = removed.add(element) || changed
            }
            return changed
        }

        override fun removeAll(elements: Collection<T>): Boolean {
            var changed = false
            for (e in elements) {
                changed = remove(e) || changed
            }
            return changed
        }

        override fun contains(element: T): Boolean =
            element in added || (element !in removed && element in base)

        override fun clear() {
            added.clear()
            removed.addAll(base)
        }

        override fun iterator(): MutableIterator<T> = BuilderIterator()

        fun toDelta() = SetDelta(
            added = this.added.toSet(),
            removed = this.removed.toSet(),
        )

        private inner class BuilderIterator : MutableIterator<T> {
            private val baseIterator = base.iterator()
            private val addedIterator = added.iterator()
            private var pending: T? = null
            private var hasPending = false
            private var last: T? = null
            private var hasLast = false

            override fun hasNext(): Boolean {
                if (!hasPending) {
                    findPending()
                }
                return hasPending
            }

            override fun next(): T {
                if (!hasPending) {
                    findPending()
                    if (!hasPending) {
                        throw NoSuchElementException()
                    }
                }
                // Pending is not null unless T is nullable when hasPending is true
                @Suppress("UNCHECKED_CAST")
                val result = pending as T
                pending = null
                hasPending = false
                last = result
                hasLast = true
                return result
            }

            private fun findPending() {
                while (baseIterator.hasNext()) {
                    val element = baseIterator.next()
                    if (element !in removed) {
                        pending = element
                        hasPending = true
                        return
                    }
                }
                if (addedIterator.hasNext()) {
                    pending = addedIterator.next()
                    hasPending = true
                }
            }

            override fun remove() {
                check(hasLast)
                @Suppress("UNCHECKED_CAST") // Sound because hasLast
                val last = this.last as T
                this.last = null
                this.hasLast = false
                this@Builder.remove(last)
            }
        }
    }
}
