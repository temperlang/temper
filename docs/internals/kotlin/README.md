# Temper's first compiler

These pages describe Temper's first compiler, written in Kotlin
([temperlang/temper](https://github.com/temperlang/temper)), as its
documentation site published them. They're kept for reference while
both compilers live; this compiler works differently, as the rest of
[internals](../README.md) shows.

- [Writing a new backend](writing-a-backend.md): the Kotlin backend
  guide: backend IDs, a backend's Gradle module, out-grammars, tests,
  and the functional test matrix. This compiler's backends are described in
  [backends](../06-backends.md).
- [Output grammars](out-grammars.md): the `.out-grammar` format, from
  which the Kotlin compiler generates its backends' syntax trees.
  This compiler has no generated trees; its backends write source from
  [tmpl](../05-tmpl.md) directly.

How this compiler differs from the Kotlin compiler, on purpose and where the
Kotlin compiler has bugs, is in
[notes/kotlin-differences.md](../../../notes/kotlin-differences.md).
