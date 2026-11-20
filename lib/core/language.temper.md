# Language builtins

    /**
     * Ignore any value.
     */
    @connected
    export let ignore(value: AnyValue): Void {}

    // Builtins that the compiler provides: values, functions, and macros.
    // Each is `@connected` without a body: the compiler supplies it, so in
    // core the declaration declares nothing, and core's own code still names
    // the builtin. It's here for its doc comment, which editors show. Words
    // the parser treats specially are quoted, as ``nym`true` ``. Operators
    // and jumps (`return`, `break`, `continue`) aren't declared here:
    // operators wait on the operator design, and jumps are syntax, neither
    // values nor macros.

    // Values.

    /** The value `true` of type [snippet/type/Boolean]. */
    @connected
    export let nym`true`;

    /** The value `false` of type [snippet/type/Boolean]. */
    @connected
    export let nym`false`;

    /**
     * The singleton value `null`, which any nullable type admits: a type
     * followed by `?`, as *String?*. It may stand for an absent value, as an
     * unspecified, optional input.
     */
    @connected
    export let nym`null`;

    /**
     * The sole value of type [snippet/type/Void]. *Void* is disjoint from
     * [snippet/type/AnyValue], so no `void` value is directly usable.
     */
    @connected
    export let nym`void`;

    /** The [snippet/type/Float64] value for "Not a Number". */
    @connected
    export let NaN;

    /** The [snippet/type/Float64] value for infinity. */
    @connected
    export let Infinity;

    // Types.

    /**
     * *Void* is the return type for functions that are called for their
     * effect, not to produce a value. Its sole value is `void`.
     */
    export class Void {
      private constructor();
    }

    /** Type values represent a Temper type, allowing them to be inputs to macros. */
    export class Type {
      private constructor();
    }

    // Functions.

    /**
     * Immediately panics, which is unrecoverable inside Temper.
     * It might be recoverable in backend code.
     */
    @connected
    export let panic;

    /**
     * A string tag that requires a single code-point string and returns that
     * code point as an [snippet/type/Int32]: `char'a' == 97`.
     */
    @connected
    export let char;

    /** A tag for raw strings, as `raw"a\b"`, whose escape sequences aren't expanded. */
    @connected
    export let raw;

    /** Constructs a compiled regex from a regex literal, as `rgx"a+"` or `/a+/`. */
    @connected
    export let rgx;

    /**
     * `doPure { x }` is the same as `x` except that the result is available
     * to macro code early. `x` must be pure code: no side effects.
     */
    @connected
    export let doPure;

    /**
     * `async { … }` takes a safe generator block and runs it out of band,
     * later, from the run queue.
     */
    @connected
    export let async;

    // Macros.

    /** The word `let` defines names for variables and functions. */
    @connected
    export let let;

    /** `fn` defines function values, `fn (x: Int): Int { x }`, and function types, `fn (Int): Int`. */
    @connected
    export let fn;

    /**
     * `if (condition) { … } else { … }` branches on a [snippet/type/Boolean]:
     * it runs its first block when *condition* is true, and otherwise its
     * `else` block, if any.
     */
    @connected
    export let if;

    /**
     * `while (condition) { body }` runs *body* for as long as *condition*
     * is true, checking it before each run.
     */
    @connected
    export let while;

    /**
     * `for (initializer; condition; increment) { body }` is a simple loop,
     * and `for (let one of many) { body }` runs *body* for each element of
     * *many*.
     */
    @connected
    export let for;

    /**
     * `do { body } while (condition)` runs *body* once before checking
     * *condition* the first time. Without a condition, `do { … }` runs its
     * block once, as an expression.
     */
    @connected
    export let do;

    /**
     * `a orelse b` recovers from failure: if *a* fails to produce a result,
     * it bubbles, then *b* runs and its result is used instead.
     */
    @connected
    export let nym`orelse`;

    /**
     * `when (x) { 0 -> a; is T -> b; else -> c }` checks what *x* matches,
     * values or types, and is the result of the first match's branch.
     */
    @connected
    export let when;

    /**
     * Defines a concrete `class` type. Classes may extend `interface` types
     * but may not themselves be extended.
     */
    @connected
    export let nym`class`;

    /**
     * Defines an abstract `interface` type, which may declare abstract
     * properties and methods, but no constructors or backed properties.
     */
    @connected
    export let interface;

    /** `let { a, b } = import("./path")` imports names that another module exports. */
    @connected
    export let import;

    /**
     * `test("name") { … }` defines a test case. A module that defines tests
     * is a test module rather than a production module.
     */
    @connected
    export let test;

    /** `assert(condition) { "why" }`, in a test, fails the test with the message unless *condition* is true. */
    @connected
    export let assert;

    /**
     * Immediately bubbles: fails to produce a result. Typically, this runs
     * the right side of the closest containing `orelse`.
     */
    @connected
    export let bubble;

    /**
     * `await p`, in an `async` block, pauses until the [snippet/type/Promise]
     * *p* is complete, then is its value, or bubbles if it was broken.
     */
    @connected
    export let nym`await`;

    /**
     * Called from the body of a generator function to pause the body and
     * cause [snippet/type/Generator/method/next] to return: `yield(x)` with
     * the value *x*, or a bare `yield` with the [snippet/type/Empty] value.
     */
    @connected
    export let nym`yield`;
