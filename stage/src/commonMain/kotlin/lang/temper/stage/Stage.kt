package lang.temper.stage

/** Stages of compilation.  `class Module` controls how a source file advances through stages. */
enum class Stage(val abbrev: Char) {
    /** Stage at which input is converted to tokens. */
    Lex('L'),

    /** Stage at which tokens are converted to a tree form. */
    Parse('P'),

    /** Default stage at which `import(...)` calls happen. */
    Import('I'),

    /**
     * Stage at which ambiguity is ironed out.
     * - Complex *Arg*s (see production "Arg" in Grammar) are converted to either formal parameters
     *   or actual parameters.
     * - `{...}` blocks that contain type members, like those in `class C {...}`, are pre-processed.
     *   Property declarations like `class C { p: T }` that have an AST like `(: p T)` become
     *   explicit declarations: `class C {let p:T}` and similarly for method declarations
     *   `class C {f(){...}}`.
     */
    DisAmbiguate('A'),

    /**
     * Stage at which names are resolved to declarations.
     * Syntax macros are macros that do not need type information.
     * Before and during this stage, the AST is very sensitive to lexical containment, so we're
     * restricted as to how we can move code.  Afterward, parsed names have been resolved to
     * names that do not depend solely on their textual content, so we have more flexibility.
     */
    SyntaxMacro('S'),

    /**
     * Stage at which type definitions are reified and pattern matches desugar to simpler
     * constructs.
     */
    Define('D'),

    /** Type inference and checking happens for the first time here. */
    Type('T'),

    /** Stage for macros that benefit from type information. */
    FunctionMacro('F'),

    /**
     * Stage at which `export` runs by default to make symbols defined in this module available to
     * others.  Unless some `export`s run later than usual, dependencies that `import` this module
     * will be eligible to advance past that stage after this stage completes.
     */
    Export('X'),

    /**
     * Stage for macros that look at code, may log errors and warnings, may fill in tables available
     * post-compilation, but which do not need to make intra-module changes.
     */
    Query('Q'),

    /**
     * Stage that no module enters until each module has completed its prior stages.
     * This stage is when backends receive a complete module set so can generate an output suitable
     * for the target language.
     */
    GenerateCode('G'),

    /**
     * Stage that represents everything that happens after compilation.
     * Having a stage variable for this allows us to reuse interpreter machinery to run tests on the
     * final-ish product.
     */
    Run('R'),
    ;

    override fun toString() = "@$abbrev"

    companion object {
        private val values = entries.toTypedArray()

        fun before(s: Stage?) = when (val ordinal = s?.ordinal) {
            null, 0 -> null
            else -> values[ordinal - 1]
        }

        fun after(s: Stage?) = when (val ordinal = s?.ordinal) {
            null -> values[0]
            values.size - 1 -> null
            else -> values[ordinal + 1]
        }
    }
}
