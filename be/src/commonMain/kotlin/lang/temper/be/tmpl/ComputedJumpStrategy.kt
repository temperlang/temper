package lang.temper.be.tmpl

/*
 * TODO: TmpLControlFlow could try a binary search tree strategy like the below for Py.
 *
 * matched: do {
 *   let caseExpr = ...;
 *   if (caseExpr <= 2) {
 *     if (caseExpr === 1) {
 *       x = "one"; break matched;
 *     } else if (caseExpr === 2) {
 *       x = "two"; break matched;
 *     }
 *   } else if (caseExpr <= 4) {
 *     if (caseExpr === 3) {
 *       x = "three"; break matched;
 *     } else {
 *       x = "four"; break matched;
 *     }
 *   }
 *   // Else case
 *   x = "not matched";
 * }
 *
 * That would be a semantics-preserving alternative to the below
 * which, while not constant dispatch, is O(log):
 *
 * switch (...) {
 *   case 1:  x = "one";         break;
 *   case 2:  x = "two";         break;
 *   case 3:  x = "three";       break;
 *   case 4:  x = "four";        break;
 *   default: x = "not matched"; break;
 * }
 */

/**
 * Whether and how to generate [TmpL.ComputedJumpStatement]s which would normally
 * translate to a `switch` or `match` statement in a target language.
 *
 * Computed jumps statements are important in dispatching to the right step when
 * using [CoroutineStrategy.TranslateToRegularFunction] and they can be generally
 * useful for chains of `if` statements that compare the same variable to a series
 * of numeric constants.
 */
enum class ComputedJumpStrategy {
    /** For languages like Python that do not efficiently support computed jumps. */
    NeverUse,

    /**
     * For languages like Java that have a `switch` statement which serves as an
     * efficient computed jump but which requires `break` statements.
     *
     * In code like the below, the nested `break` statement would need adjustment
     * before being used inside a `switch` that introduces a new default `break` scope.
     *
     *     while (f())
     *       match (g()) {
     *         -1   -> do { break; }
     *         0    -> do { ... }
     *         1, 2 -> do { ... }
     *         else -> do { ... }
     *       }
     *     }
     *
     * The *TmpL* translator may add labels to work around this.
     *
     *     loop0: while (f())
     *       match (g()) {
     *         -1   -> do { break loop0; }
     *         0    -> do { ... }
     *         1, 2 -> do { ... }
     *         else -> do { ... }
     *       }
     *     }
     *
     * That way the backend can translate straightforwardly to a `switch`
     * without having to be aware of surrounding loops.
     *
     *     loop0: while (f())
     *       switch (g()) {
     *         case -1: { break loop0; }
     *         case 0: { ...; break; }
     *         case 1: case 2: { ...; break; }
     *         default: { ... }
     *       }
     *     }
     */
    IsDefaultBreakScope,

    /** Like OCaml, supports an efficient integer `match` that doesn't require extra adjustments. */
    Use,
}
