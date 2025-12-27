package lang.temper.value

/**
 * A thing that may refer to [StayLeaf]s.
 */
interface StayReferrer {
    /**
     * Enumerate the stays referred to, deeply.
     */
    fun addStays(s: StaySink)
}

/** A subtype of [StayReferrer] that does not refer to any stays. */
interface Stayless : StayReferrer {
    override fun addStays(s: StaySink) {
        // no stays here
    }
}

/**
 * Collects stays added by referrers.
 */
class StaySink(private val document: Document) {
    private val stays = mutableSetOf<StayLeaf>()
    private val visited = mutableSetOf<StayReferrer>()

    fun add(stay: StayLeaf?) {
        // We only care about same document stays.  Other stays enter the document, often via
        // imports of types.
        if (stay?.document == document) {
            stays.add(stay)
        }
    }

    /** Allows conditionally operating on a referrer that may be cyclicly referenced. */
    fun <T> whenUnvisited(referrer: StayReferrer, whenUnvisited: () -> T): T? =
        if (referrer in visited) {
            null
        } else {
            visited.add(referrer)
            whenUnvisited()
        }

    val allStays get() = stays.toSet()
}
