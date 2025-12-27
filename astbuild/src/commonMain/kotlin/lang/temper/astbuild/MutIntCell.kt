package lang.temper.astbuild

/** A mutable cell holding an [Int]. */
internal class MutIntCell(var i: Int = 0) {
    override fun toString(): String = "MutIntCell($i)"
}
