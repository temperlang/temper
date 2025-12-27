package lang.temper.value

/** A logical grouping of Temper modules. */
enum class DependencyCategory {
    /**
     * Production modules may be depended upon by code from other
     * Temper libraries and by code in other languages.
     *
     * Production modules are necessary for generating
     * translated outputs that should be loadable by code that
     * depends on the enclosing library.
     */
    Production,

    /**
     * Test modules are necessary for developing and
     * validating the enclosing library but which need not be
     * available to code that depends on the enclosing library.
     */
    Test,
}
