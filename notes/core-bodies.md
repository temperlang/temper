# Core's Temper bodies

`lib/core/` gives many members Temper bodies, but until
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

69 members and functions have bodies; the cases run 53 of them, plus
`Float64.e` and `.pi`'s initializers. Natives are in `interp.rs` unless
named. "All" means all seven backends connect it.

| Body | Interpreter | Backends |
| --- | --- | --- |
| `String.isEmpty`, `.indexOf`, `.step`, `.toString` | `property`, `strings::string_method` | all |
| `String.forEach` | `apply`'s `forEach` on strings | none: tmpl inlines a loop, as the body |
| `StringBuilder`: constructor, `append`, `appendCodePoint`, `appendBetween`, `clear`, `end`, `toString` | `strings::builder_method`, `property` | all |
| `Listed.isEmpty`, `.toListBuilder`, `.reduce` | `property`, `list_method` | all |
| `listedMap`, `listedReduceFrom` | `list_method` | all |
| `listedReduceFromIndex` | `list_method` | all |
| `List.toList` | `list_method` | all |
| `List.forEach` | `list_method` | none: tmpl inlines a loop |
| `Mapped.length`, `.get`, `.getOr`, `.has`, `.keys`, `.values`, `.toMap`, `.toMapBuilder`, `.toList` | `maps::map_method`, `property` | all |
| `Mapped.toListBuilder` | `maps::map_method` | Python only |
| `mappedToListWith` | `maps::map_method` | all |
| `mappedToListBuilderWith` | `maps::map_method` | none: not yet in tmpl |
| `Boolean.toString` | `method` | all |
| `Int32.min`, `.max`; `Int64.min`, `.max` | `method` | all |
| `Int32.succ`, `.pred`; `Int64.succ`, `.pred` | `method` | all: tmpl writes them as `n + 1` and `n - 1` |
| `Int32.signum` | `property` | all |
| `Float64.near` | `numbers::float_method` | all |
| `Float64.succ`, `.pred` | `numbers::float_method` | none: not yet in tmpl |
| `Empty.eq`, `empty`, `ignore`, `doneResult` | `equal`, `builtin` | `empty` all; `doneResult` C++, Java, Rust, past tmpl's "not yet"; `ignore` is tmpl's own; `==` on `Empty` not yet in tmpl |
| `SafeGenerator.next` | `generator_method` | none: not yet in tmpl |
| `ValueResult`, `Pair` constructors | `apply`'s `new` | `Pair` all; `ValueResult` C++, Java, Rust |
| `getConsole` | the console | all |
| `Null`, `Empty`, `NoStringIndex`, `StringIndex` constructors | builtin values | none |
| `GlobalConsole.log` | the console | none |
| `GeneratorFnWrapper`, `SafeGeneratorFnWrapper` (8 members) | unused: generators are native | none |

The cases don't run the last four rows' bodies, nor the `ValueResult`
and `Pair` constructors' (which only set the fields the cases read):
they build or read a representation that builtin values don't have.
The generator wrappers are left from Kotlin Temper; nothing uses them.

## Fields

Core's classes declare no field that only describes a host's
representation (2026-11-21, t850): the builtins' `content` fields,
`Deque`'s, `Problem`'s, and `PromiseBuilder`'s `_promise` are gone, and
bodies that read `content` read `this`, as `Int32.min`'s `if (this <=
other) { this } else { other }`. What's left is state core's own Temper
sets and its bodies read: `StringBuilder`'s `substrings` (the
interpreter runs it as an ordinary class under
`run_tests_with_core_bodies`), the generator wrappers' fields, and
`ValueResult`'s and `Pair`'s public fields. (`DenseBitVector`, with its
`bits`, is std's `BitVector` now, whose body is the interpreter's only
implementation.)
`fields_are_cores_own_state` checks that each instance field has an
initializer or an assignment in its class.

## What the interpreter runs

Natives for `@connected` members, as host code is on backends, and for
bodiless ones (2026-11-21, t744). Core's bodies that aren't
`@connected` are few, and `unconnected_bodies_are_listed` lists each
with what runs for it: private constructors of builtin values, the
unused generator wrappers, and `GlobalConsole.log` (the console is the
host's). (`listedReduceFromIndex` was one, keeping its native for
speed, until backends connected it, 2026-11-23.) Running a body needs
core settled beside the program, as `bodies_agree_with_natives`
settles it, and runs slower:

| Program (release, 5 runs back to back) | Natives | Bodies |
| --- | --- | --- |
| Settling a small program, without and with core | 2.2-3.5 ms | 9.6-14.9 ms |
| 100 `reduceFromIndex` calls over 2000 items | 38-60 ms | 182-214 ms |
| A mix of `StringBuilder`, `min`/`max`, `isEmpty`, `DenseBitVector`, map and list members, every body run | 4.2-6.2 ms | 85-99 ms |

Body runs use t743's harness, which looks each member up per call, so
some of the gap is that lookup, but settling core is the larger cost
for short programs, as the REPL's and playground's are. So core's text
is the source of what the interpreter does through the cases, which
keep each native in step with its body, rather than by running it.

## Findings (2026-11-19)

Body against native, in the interpreter:

- `listedMap`, `listedReduceFrom`: when the callback changes the
  builder, the bodies follow its live length; natives snapshot it.

Fixed since, as decided: `listedReduceFromIndex` got a native (past the
end gives the initial value; a negative index panics, as the body
does); `Int32`'s and `Int64`'s `succ` and `pred` got natives that wrap,
as `+` and `-` do, and `Int32.signum` a native getter; and `Float64.near`
is true whenever `==` is, so NaN is near NaN and an infinity near
itself only, as `math.isclose`'s is, and otherwise compares finite
values within the margin as IEEE does, never within a NaN margin, in
the body, the interpreter, and every runtime.

`Int32.signum` and `listedReduceFromIndex` run on every backend too
(2026-11-23; testkit's `core-numbers` checks `signum` at the extremes and
an index inside, at, and past the end). A negative index fails on every
backend as `listed[i]` does there, each helper calling its runtime's own
list get, and `core-numbers` ends on one.

### Host helpers that restate core's bodies

Some backends' helpers restate a body core writes in Temper, so each
runtime is one more copy of the semantics to keep in step: `near` (7
runtimes, each with its own NaN-margin comment), `reduceFromIndex` (7),
and `listedSorted`'s copy-and-compare (7). Once tmpl translates core's
bodies, any without a host speed-up should lose `@connected` and
translate from core instead. Likely first: `reduceFromIndex` and
`listedReduceFrom` everywhere (plain loops), and `near` (arithmetic
only). `sorted` keeps host sorts for speed and stability.

Backends against the bodies (all else agrees):

- Changing a builder during `map` or `reduceFrom`: every backend
  differs somewhere, some throwing; C++'s `reduceFrom` reads storage the
  vector freed when it grew.
- `StringBuilder.appendBetween` with begin after end: JS appends the
  whole span, as `substring` swaps its arguments; Rust panics. With an end past the source:
  Rust panics; the others clamp, as the body does.
- `String.step` by `Int32.min`: Rust panics negating it.
- `DenseBitVector` with a negative index or capacity: the body reads
  false or makes it empty; Rust, Java and C# threw. Fixed on every
  backend when it moved to std as `BitVector` (2026-11-23; funtest
  types/bit-vector).
- Rust also can't compile `new ListBuilder<Int>().isEmpty` or
  `new Map<String, Int>([]).length`: the type arguments are lost.
