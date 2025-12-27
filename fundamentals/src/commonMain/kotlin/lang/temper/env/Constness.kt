package lang.temper.env

/**
 * Whether an [Environment] binding is `const` or not.
 */
enum class Constness {
    /** May be assigned at most once after declaration. */
    Const,

    /** May be re-assigned. */
    NotConst,
}
