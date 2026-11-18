# Temper documentation

Temper is a programming language for writing libraries once and using
them from every other language. These docs cover the language, its
standard library, its tools, and how its compiler works.
New here? Start with the [quick tour](guide/tour.md).

## Learn

- [Introduction](guide/introduction.md): what Temper does, with
  examples in each language, and where it fits.
- [Quick tour](guide/tour.md): Temper in five short examples.
- [Why a new programming language?](guide/why.md)
- [Getting started](guide/getting-started.md): write a library and use
  it from C#, Java, JavaScript, Lua, Python, and Rust.
- [Tutorial](guide/README.md#tutorial): classes, interfaces, control
  flow, modules, builtin types, memory, and regular expressions.

## Reference

- [The language](language/README.md):
  [types and values](language/types.md),
  [builtins](language/builtins.md), [syntax](language/syntax.md), and
  [modules and files](language/modules.md).
- [The standard library](std/README.md): `std/json`, `std/regex`,
  `std/temporal`, `std/testing`, and `std/net`.
- [Target languages](backends/README.md): what each backend makes, and
  how.
- [Tools](tools/README.md): the [command line](tools/cli.md),
  [editors](tools/editors.md), and the [playground](tools/playground.md).
- [Release notes](release-notes/README.md).

## Inside

- [Contributing](contributing/README.md): building, testing, and
  sending changes, and [writing these docs](contributing/docs.md).
- [How the compiler works](internals/README.md): the compiler, stage by
  stage, with diagrams and links into the code.
- [Design notes](../notes/README.md): the plan, and the decisions
  behind it.
- [Inconsistencies](inconsistencies.md): where these docs, Temper's
  previous compiler, and this one still disagree, to be resolved.

## Where these docs come from

Most of the guide, reference, and release notes began as Temper's
documentation site, [temperlang.dev](https://temperlang.dev/), and its
sources in the [temperlang/temper](https://github.com/temperlang/temper)
repository, including reference pages generated there from doc
comments in the compiler's source. They're Markdown source here, edited
by hand ([how, and how the docs site is built](contributing/docs.md)).
The [blog](https://temperlang.dev/blog/) stays in its own repository.

Copyright The Temper Project Contributors. The documentation is
licensed under [CC BY-SA 4.0](../LICENSE-CC-BY-SA-4.0); see
[COPYRIGHT](../COPYRIGHT).

> **Note:** temperlang.dev's footer says CC BY-NC-SA 4.0, unlike
> COPYRIGHT ([inconsistencies](inconsistencies.md#i1-the-documentations-license)).
