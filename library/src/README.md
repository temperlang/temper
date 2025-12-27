# Module library

<!-- The h1 name is specially interpreted by dokka -->

Represent high-level information about libraries such as that
needed to resolve dependencies.

Before we publish libraries to language-specific repositories,
we need to recreate parts of the Temper dependency graph in
the language-specific module graph.  That means we need to be
able to relate (temper-library, version) pairs to
(language-specific-library, version).
