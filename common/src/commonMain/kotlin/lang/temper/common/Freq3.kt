package lang.temper.common

/** A 3-way frequency. */
sealed class Freq3(val ordinal: Int) : Comparable<Freq3> {
    /**
     * A positive [Freq3].
     * [Never] is excluded so that absence in a map can handle that case.
     */
    sealed class Positive(ordinal: Int) : Freq3(ordinal)

    object Never : Freq3(0) {
        override fun toString(): String = "Never"
    }

    object Sometimes : Positive(1) {
        override fun toString(): String = "Sometimes"
    }

    object Always : Positive(2) {
        override fun toString(): String = "Always"
    }

    override fun compareTo(other: Freq3): Int =
        this.ordinal.compareTo(other.ordinal)

    companion object {
        fun max(a: Positive, b: Positive) = if (a.ordinal >= b.ordinal) a else b
        fun min(a: Positive, b: Positive) = if (a.ordinal <= b.ordinal) a else b
        fun max(a: Freq3, b: Freq3) = if (a.ordinal >= b.ordinal) a else b
        fun min(a: Freq3, b: Freq3) = if (a.ordinal <= b.ordinal) a else b

        /**
         * x & x -> x
         * Always & Never -> Sometimes
         * Sometimes & x -> Sometimes
         */
        fun and(a: Freq3, b: Freq3) = when {
            a == b -> a
            // Always & Never -> Sometimes
            // Sometimes & x -> Sometimes
            else -> Sometimes
        }

        fun <K> maxPositiveEntryValues(
            a: Map<K, Positive>,
            b: Map<K, Positive>,
        ): Map<K, Positive> =
            mergeMaps(Sometimes, a, b) { f1, f2 ->
                max(f1, f2)
            }

        fun <K> minPositiveEntryValues(
            a: Map<K, Positive>,
            b: Map<K, Positive>,
        ): Map<K, Positive> =
            mergeMaps(Sometimes, a, b) { f1, f2 ->
                min(f1, f2)
            }
    }
}
