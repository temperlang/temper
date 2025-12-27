package lang.temper.be.tmpl

/**
 * How to translate generator functions which may *yield*.
 *
 * ```temper inert
 * _ { () extends GeneratorFn =>
 *   console.log("foo");
 *   yield;
 *   console.log("bar");
 * }
 * ```
 *
 * This block lambda produces *Generator*s.
 * This particular generator logs "foo" the first time its `.next()`
 * method is called, then yields control back to its caller.
 * If `.next()` is called again, it resumes after the *yield*
 * and logs "bar".
 *
 * ### Value contract
 *
 * Regardless of which strategy is used, the value produced from a
 * generator function must be a factory that takes the intended
 * arguments and returns a *Generator* that, each "turn", resumes
 * after the last *yield* point and runs until the next *yield*
 * or the final *return*.
 */
enum class CoroutineStrategy {
    /**
     * Coroutines are translated to regular functions.
     *
     * The goal of this documentation is not to explain how but it uses
     * a state machine translation and a wrapper function so the below
     * would be a valid conversion of the example coroutine in the docs
     * for [CoroutineStrategy].
     *
     * ```temper inert
     * _ { (): Generator<...> => // Wrapper function gets arguments
     *
     *   var caseIndex: Int = 0;
     *
     *   return fn : Void { // State machine translation.
     *     when (caseIndex) {
     *       0 -> do {
     *         console.log("foo");
     *         caseIndex = 1;
     *         return;
     *       }
     *       1 -> do {
     *         console.log("bar");
     *         caseIndex = 2;
     *       }
     *       2 -> do {}
     *     }
     *   }
     * }
     * ```
     *
     * The wrapper creates space for a state variable, here *caseIndex*,
     * and any locals that need to persist over a yield.
     *
     * When the inner function is called, it performs a turn as per the
     * value contract above.
     */
    TranslateToRegularFunction,

    /**
     * Coroutines are translated to a special kind of function that
     * yields.  Often these are called generator functions, and use
     * syntax like `yield` to return control, perhaps temporarily,
     * to their caller.
     *
     * The function value constructed should be a factory as described
     * in the value contract above.
     *
     * If the target language's generator function syntax produces an
     * iterable class instance instead of a callable, it may need
     * wrapping in something callable.
     */
    TranslateToGenerator,
}
