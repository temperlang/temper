package lang.temper.common

class CartesianProduct<out T>(private val groups: List<List<T>>) : Iterable<List<T>> {
    override fun iterator(): Iterator<List<T>> = object : Iterator<List<T>> {
        private val product = groups.foldRight(1) { a, b -> a.size * b }
        private var i = 0

        override fun hasNext(): Boolean = i < product

        override fun next(): List<T> {
            if (i == product) { throw NoSuchElementException() }
            var j = i
            i += 1
            return groups.map {
                val size = it.size
                val index = j % size
                j /= size
                it[index]
            }
        }
    }
}
