package lang.temper.be.java

data class PackageLists(
    /** Package names for which the library adds classed under the `src/main` source set. */
    val main: Set<QualifiedName>,
    /** Package names for which the library adds classed under the `src/test` source set. */
    val test: Set<QualifiedName>,
)
