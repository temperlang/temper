package lang.temper.interp

/**
 * What the [Interpreter] should do with the original subtree when it can be collapsed to a simple
 * value.
 *
 * The interpreter may replace several kinds of constructs with values:
 * - references that have a single initializer that itself reduces to a simple, stable value
 * - calls to pure functions whose arguments reduce to simple, stable values and where the
 *   result of the call is a simple, stable value.
 */
enum class ReplacementPolicy {
    /**
     * Discard the original and inline the value.
     * When generating a [library][lang.temper.lexer.Genre.Library], we don't need the original,
     * so can simplify away a lot of complexity by aggressively inlining.
     */
    Discard,

    /**
     * When generating [documentation][lang.temper.lexer.Genre.Documentation],
     * we need to preserve the intent of the code the author is trying to convey to the reader.
     *
     * The interpreter will preserve both via the special
     * [*preserve*][lang.temper.builtin.BuiltinFuns.preserveFn] function.
     */
    Preserve,
}
