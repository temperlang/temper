# Core's Temper bodies

`lib/core/core.temper` gives many members Temper bodies, but until
2026-11-19 nothing ran them: typing reads core's signatures, editors show
its docs, the interpreter implements core natively in Rust, and every
backend connects core's members to host code. So a body could drift from
what runs. `crates/temper-frontend/tests/core_bodies.rs` now runs each
body against the interpreter's native on shared inputs, and can write the
same cases as a library for `temper test -b …` (its module doc says how).

How: the test settles core's text as a module beside its cases, then
runs them twice, as usual and with `interp::run_tests_with_core_bodies`,
which calls core's body wherever core has one for a builtin value's
member (`interp/core_bodies.rs`). Bodiless members stay native, so each
body runs on the natives it builds on, as it would on a backend that
translated only it.

## Inventory

70 members and functions have bodies; the cases run 53 of them, plus
`Float64.e` and `.pi`'s initializers. Natives are in `interp.rs` unless
named. "All" means all seven backends connect it.

| Body | Interpreter | Backends |
| --- | --- | --- |
| `String.isEmpty`, `.indexOf`, `.step`, `.toString` | `property`, `strings::string_method` | all |
| `String.forEach` | `apply`'s `forEach` on strings | none: tmpl inlines a loop, as the body |
| `StringBuilder`: constructor, `append`, `appendCodePoint`, `appendBetween`, `clear`, `end`, `toString` | `strings::builder_method`, `property` | all |
| `Listed.isEmpty`, `.toListBuilder`, `.reduce` | `property`, `list_method` | all |
| `listedMap`, `listedReduceFrom` | `list_method` | all |
| `listedReduceFromIndex` | none | none: not yet in tmpl |
| `List.toList` | `list_method` | all |
| `List.forEach` | `list_method` | none: tmpl inlines a loop |
| `Mapped.length`, `.get`, `.getOr`, `.has`, `.keys`, `.values`, `.toMap`, `.toMapBuilder`, `.toList` | `maps::map_method`, `property` | all |
| `Mapped.toListBuilder` | `maps::map_method` | Python only |
| `mappedToListWith` | `maps::map_method` | all |
| `mappedToListBuilderWith` | `maps::map_method` | none: not yet in tmpl |
| `Boolean.toString` | `method` | all |
| `DenseBitVector`: constructor, `get`, `set` | `apply`'s `new`, `method` | all |
| `Int32.min`, `.max`; `Int64.min`, `.max` | `method` | all |
| `Int32.succ`, `.pred`, `.signum`; `Int64.succ`, `.pred` | none | none: not yet in tmpl |
| `Float64.near` | `numbers::float_method` | all |
| `Float64.succ`, `.pred` | `numbers::float_method` | none: not yet in tmpl |
| `Empty.eq`, `empty`, `ignore`, `doneResult` | `equal`, `builtin` | `empty` all; `doneResult` C++, Java, Rust, past tmpl's "not yet"; `ignore` is tmpl's own; `==` on `Empty` not yet in tmpl |
| `SafeGenerator.next` | `generator_method` | none: not yet in tmpl |
| `ValueResult`, `Pair` constructors | `apply`'s `new` | `Pair` all; `ValueResult` C++, Java, Rust |
| `PromiseBuilder.promise`, `getConsole` | `property`, the console | all |
| `Null`, `Empty`, `NoStringIndex`, `StringIndex` constructors | builtin values | none |
| `GlobalConsole.log` | the console | none |
| `GeneratorFnWrapper`, `SafeGeneratorFnWrapper` (8 members) | unused: generators are native | none |

The cases don't run the last four rows' bodies, nor the `ValueResult`
and `Pair` constructors' (which only set the fields the cases read):
they build or read a representation that builtin values don't have.
The generator wrappers are left from Kotlin Temper; nothing uses them.

## Findings (2026-11-19)

Body against native, in the interpreter:

- `listedReduceFromIndex`, `Int32.succ`/`pred`, `Int64.succ`/`pred`: no
  native ("No method"); the bodies wrap on overflow.
- `Int32.signum`: no native getter, so `x.signum` is a method value.
- `listedMap`, `listedReduceFrom`: when the callback changes the
  builder, the bodies follow its live length; natives snapshot it.
- `Float64.near`: `NaN.near(NaN)` is true in the body, as the
  interpreter's `<=` orders NaN as equal to itself, false natively.

Backends against the bodies (all else agrees):

- Changing a builder during `map` or `reduceFrom`: every backend
  differs somewhere, some throwing; C++'s `reduceFrom` reads storage the
  vector freed when it grew.
- `Float64.near`: Python gives `inf.near(inf)` true, as `math.isclose`
  does, which the docs cite; the body and the others give false.
- `StringBuilder.appendBetween` with begin after end: JS appends the
  whole span, as `substring` swaps its arguments; Rust panics. With an end past the source:
  Rust panics; the others clamp, as the body does.
- `String.step` by `Int32.min`: Rust panics negating it.
- `DenseBitVector` with a negative index or capacity: the body reads
  false or makes it empty; Rust, Java and C# throw.
- Rust also can't compile `new ListBuilder<Int>().isEmpty` or
  `new Map<String, Int>([]).length`: the type arguments are lost.
