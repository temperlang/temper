package lang.temper.common

/** Tri-state truth value. */
enum class TriState(
    /** If this is a definite boolean value, its [Boolean] equivalent else `false`. */
    val elseFalse: Boolean,
    /** If this is a definite boolean value, its [Boolean] equivalent else `true`. */
    val elseTrue: Boolean,
    /** If this is a definite boolean value, its [Boolean] equivalent else `null`. */
    val booleanOrNull: Boolean?,
) {
    FALSE(false, false, false),
    TRUE(true, true, true),
    OTHER(false, true, null),
    ;

    operator fun not() = when (this) {
        TRUE -> FALSE
        FALSE -> TRUE
        OTHER -> OTHER
    }

    companion object {
        /** The value equivalent to the given [Boolean]. */
        fun of(b: Boolean) = if (b) TRUE else FALSE
    }
}
