# Temper Core

Core definitions are partially internal to the Temper frontend, but some
definitions are also defined here in Temper source.

    export let name = "temper-core";
    export let version = "0.0.0";

core's version is Temper's own, which the compiler gives it, so this says
none.

## Layout

Core is one module, a file per topic, as std is a module per topic:

- `any.temper.md`: `AnyValue`, `Equatable`, `MapKey`, `Bubble`, `Invalid`,
  `Never`, `Empty`, `Function`, `GeneratorFn`, and `Problem`
- `console.temper.md`: `Console` and `console`
- `generators.temper.md`: generators, their results, and promises
- `language.temper.md`: `ignore`, and the bodiless `@connected` declarations
  that document what the compiler supplies, as `true`, `panic`, or `if`,
  with `Void` and `Type`
- `lists.temper.md`: `Listed`, `List`, `ListBuilder`, and their extensions
- `maps.temper.md`: `Pair`, `Mapped`, `Map`, `MapBuilder`, and their
  extensions
- `numbers.temper.md`: `Boolean`, `Int`, `Int32`, `Int64`, and `Float64`
- `strings.temper.md`: `String`, string indices, and `StringBuilder`

Top-level declarations may come in any order, in any of these files.

## Metadata

    export let authors = "Temper Contributors";
    export let description = "Builtin types and functions of the Temper language";
    export let homepage = "https://temperlang.dev/";
    export let license = "Apache-2.0 OR MIT";
    export let repository = "https://github.com/temperlang/temper";
