package lang.temper.lexer

/**
 * Words with special meaning in the grammar.
 *
 * <!-- snippet: keywords -->
 * # Keywords
 * Some words have special syntactic meanings in Temper, and so may not be used as identifiers.
 *
 * ⎀ syntax/ReservedWord -heading
 *
 * There are fewer of these than in many other languages, as many constructs are provided via
 * [snippet/builtins].  The Temper language will probably evolve to disallow masking certain
 * builtins, effectively reserving those words from use as declaration names.
 *
 * ⎀ keyword/builtins
 *
 * ⎀ keyword/nym
 *
 * ⎀ keyword/super
 *
 * ⎀ keyword/this
 */
val reservedWords = setOf(
    /**
     * <!-- snippet: keyword/nym : <code>nym\`...\`M</code> -->
     * # <code>nym\`...\`</code> quoted name
     * Used to escape names.
     *
     *     nym`...`
     *
     * parses to the name with the unescaped text between the quotes so
     *
     *     nym`nym`
     *
     * can be used to get the name with text "nym" through the parser.
     *
     * This is meant to allow names in Temper thar directly correspond to names in other systems,
     * including dash-case names that are widely used in databases and the web platform.
     */
    LexicalDefinitions.quotedNamePrefix,
    /**
     * <!-- snippet: keyword/builtins -->
     * # `builtins` keyword
     * It's a direct reference to a name in the module's context environment record.
     *
     * This bypasses any in-module declaration of the same name.
     *
     * ```temper
     * class MyConsole extends Console {
     *   public log(str: String): Void {
     *     builtins.console.log("[ ${str} ]");
     *   }
     * }
     * let console = new MyConsole();
     *
     * builtins.console.log("builtins.console"); //!outputs "builtins.console"
     * console.log("console");                   //!outputs "[ console ]"
     * ```
     *
     * `builtins` must be followed by a `.`.  Unlike `globalThis` in JavaScript, `builtins` is not
     * an object; it's a special syntactic form.
     *
     * ```temper FAIL
     * let a = builtins;
     * ```
     */
    "builtins",
    /**
     * <!-- snippet: keyword/this -->
     * # `this` keyword
     * `this` is a way to refer to the enclosing instance.
     * So inside a method in a [snippet/builtin/class] or [snippet/builtin/interface] definition,
     * you can use `this` to refer to the instance on which the method was called.
     *
     * ```temper
     * class C {
     *   public isSame(x: C): Boolean { this == x }
     * }
     * let c = new C();
     * c.isSame(c)
     * ```
     *
     * `this` cannot be used outside a type definition.
     *
     * ```temper FAIL
     * class C { /* Inside the type definition */ }
     * // Outside
     * this
     * ```
     */
    "this",
    /**
     * <!-- snippet: keyword/super -->
     * # `super` keyword
     *
     * `super.` is reserved syntax.
     * We have yet to figure out what our goals are w.r.t. super calls
     * and whether it makes sense to do a super call without specifying which interface's
     * implementation to use given we have no plans to allow subtyping of class types.
     */
    "super",
)
