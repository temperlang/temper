package lang.temper.value

sealed class TreeType(val name: String) {
    override fun toString(): String = name
}

sealed class InnerTreeType(name: String) : TreeType(name) {
    /** Groups trees that are considered in sequence. */
    object Block : InnerTreeType("Block")

    /** Applies a function to arguments. */
    object Call : InnerTreeType("Call")

    /** A variable declaration. */
    object Decl : InnerTreeType("Decl")

    /** An escaped tree: '(...). */
    object Esc : InnerTreeType("Esc")

    /** A lambda value. */
    object Fun : InnerTreeType("Fun")
}

sealed class LeafTreeType(name: String) : TreeType(name) {
    /** A reference used as a left-hand side, as the target of an assignment. */
    object LeftName : LeafTreeType("LeftName")

    /** A reference used as a right-hand side, for its value. */
    object RightName : LeafTreeType("RightName")

    /** Persists across stages to allow objects external to the module to refer to its parent. */
    object Stay : LeafTreeType("Stay")

    /** A value. */
    object Value : LeafTreeType("Value")
}
