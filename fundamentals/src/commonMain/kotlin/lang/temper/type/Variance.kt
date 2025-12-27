package lang.temper.type

enum class Variance(
    /** A Temper keyword reminiscent of C#/Kotlin */
    val keyword: String?,
    /** A value in [-1, 0, 1] reminiscent of Scala's sign based varriance annotations. */
    val sign: Int,
) {
    Invariant(null, 0),

    /** Upper type bound à la Java's `? extends`, Scala's `+`, and C#/Kotlin's `out` */
    Covariant("out", 1),

    /** Lower type bound à la Java's `? super`, Scala's `-`, and C#/Kotlin's `in` */
    Contravariant("in", -1),
    ;

    companion object {
        /** The variance of a type formal, unless specified otherwise. */
        val Default = Invariant
    }
}
