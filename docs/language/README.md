# Language reference

The reference for Temper the language. To learn it step by step, start
with the [guide](../guide/README.md) instead.

- [Types and values](types.md): `Boolean`, the numbers, `String`, lists,
  maps, function types, generators, and how types relate.
- [Builtins](builtins.md): the constants, functions, operators, and
  macros you can use without `import`ing anything, from `if` and `when`
  to `@json` and `test`.
- [Syntax](syntax.md): how Temper is parsed, with the grammar of each
  part, semicolon insertion, block lambdas, and strings.
- [Modules and files](modules.md): `.temper` and literate `.temper.md`
  files, modules, and libraries.

Also see the [standard library](../std/README.md), and what each
[target language](../backends/README.md) makes of Temper.

These pages began as the reference on temperlang.dev
([its sources](https://github.com/temperlang/temper/tree/kotlin-final/docs/for-users/temper-docs/docs/reference)), parts of it
generated there from doc comments in the compiler's source; they're
hand-edited Markdown now ([about these docs](../contributing/docs.md)).
Grammar rules, once drawn as railroad diagrams, are written out as
text (`Rule := …`). Each example ends with a marker that says what it
should do: `// ✅` it runs (maybe followed by its value), `// ❌` it's an
error (maybe followed by the errors), and `// ⏸️` it isn't run.
