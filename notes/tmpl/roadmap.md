# tmpl: design roadmap

Part of the [tmpl design](README.md).

## Design roadmap

Each level unlocks code, so design and building interleave.

| Level | State | Unlocks |
|---|---|---|
| 1: purpose and decisions | Done, reviewed (2026-10-29) | The crate boundary and its rules; nothing to build alone |
| 2: structure | Done, reviewed (2026-10-29) | The `temper-tmpl` crate skeleton: schema macro, node families, type and declaration tables, builder, the text writer and reader with a round trip, and the validator's structural rules, all tested on hand-written tmpl |
| 3: details, in subsets | Subset A drafted (2026-10-29), and its model, text, and validator built, then qnames, connected declarations, and core as declarations (2026-10-29); B, C, and D sketched | Per subset: exact fields, validator rules, and passes, then the frontend's translation (`to_tmpl`) for the functional tests that subset covers, then backends |

Level 3 goes subset by subset, each just ahead of the code that needs
it (Proposed):

- **Subset A:** functions, locals, `if`, `while`, returns, calls, the
  builtin types' operators and members, `console.log`, and module init.
  Then `to_tmpl` for the functional tests that use only these, then the
  naming pass, the shared output tree, and the Python backend running
  them, as its Kotlin-compatible variant first ([question 22](open-questions.md)).
- **Subset B:** classes, properties, equality, object literals,
  bubbling and `orelse`, `for … of`, closures, and imports. Python grows
  to most functional tests, with its API diff (milestone 5).
- **Subset C, Rust:** the results lowering, cells for assigned
  captures, properties as methods, first-use init, and synchronized
  module `var`s. Then the Rust backend (milestone 6).
- **Subset D:** generators and `async`, with the state-machine pass
  for Rust; then the other backends' profiles.

**Interleaving with milestone 4** (Proposed). Milestone 4's remaining
critical path ([milestone-4.md](../history/milestone-4.md), "Order and
decisions") runs in alternating chunks with tmpl's, since `to_tmpl`
needs every expression of a test typed:

| Chunk | Milestone 4 | tmpl |
|---|---|---|
| 1 | `new Box()`, `[]`, `null`, `bubble()` (since removed), and `panic()` taking the type wanted; `Never<T>` | Crate skeleton, schema macro, type and declaration tables (done for subset A, with chunk 2's tmpl work) |
| 2 | Builtin interfaces' members inherited by users' classes; a literal's result binding a generic callee's type parameter | Text writer, reader, and round trip; structural validator (done for subset A) |
| 3 | Slice 7 begins: count expressions of unknown type; resolve every `==` ([nodes.md, "Equality"](nodes.md#equality)) | Level 3 for subset A |
| 4 | Slice 7 continues, driven by which functional tests still have unknown types | `to_tmpl` for subset A's tests (done, 2026-10-30: 7 of 69) |
| 5 | Slice 7 continues | Naming pass and Python backend for subset A (done, 2026-10-30: 7 of 7 run); then the Rust backend for subset A (done, 2026-10-30: 7 of 7 run); then the JS backend for subset A (done, 2026-10-30: 7 of 7 run); then the Java backend for subset A (done, 2026-10-30: 7 of 7 run); then the C# backend for subset A (done, 2026-10-30: 7 of 7 run); then the C++ backend for subset A (done, 2026-10-30: 7 of 7 run); then the Lua backend for subset A (done, 2026-10-30: 7 of 7 run), the last of Kotlin's seven |
| then | Kotlin's diagnostics, where cheap | Subsets B, C, and D |

A test whose expressions aren't all typed is reported by `to_tmpl` and
skipped, so the count of translated functional tests tracks slice 7.
No reference cycles (slice 8) follows tmpl, as decided.

## Lowering that backends duplicate: candidates for tmpl

Principle (2026-11-06): what more than one backend needs belongs in
tmpl (`temper-tmpl`'s passes, or `to_tmpl`), not in each backend. Shared
so far: coroutines, cells for captured `var` parameters, and
`temper_tmpl::spill`, which Lua, C#, and C++ run (C++ since 2026-11-06,
marking what its order of evaluation holds in temporaries with
`spill_with`, so a choice's part or a loop's condition holding one
becomes statements; C# marks `panic()` so). Still duplicated, to
consolidate, each in its own restructuring change:

- **Statements before an expression's statement.** Each backend hoists
  `orelse` and blocks' values on its own: JS's `pre` list
  (`temper-be-js/src/translate.rs`), Java's `spilled` and
  `spill_before`, C#'s `hoist` set and `sink`, C++'s `pending`,
  `temporary`, and `statement_value`, Lua's tree, and Python's and
  Rust's own. The rest of profiles.md's `hoist` pass (steps 2 and 3:
  values delivered to a target, `let`, `return`, or a discarded value)
  would let each backend translate only statements and plain
  expressions.
- **Values that need statements where none can go:** C++ calls a lambda
  at once (`Expr::Run`), C# and Java wrap lambdas with statements in
  their own ways (`translate/functions.rs` in each). One tmpl form, a
  function literal's body made statements, or the hoist pass above
  reaching into function literals, would do for all three.
- **Order of evaluation.** C++'s effects (`temper-be-cpp/src/effects.rs`,
  with classes, virtual calls, and core's reads) extend tmpl's
  `analysis::Effects` (functions only); one analysis in tmpl, with each
  backend's own pure and read-only connected declarations as data,
  would serve C++ and any later backend whose operands run in any
  order.
- **A class extending another library's type.** `to_tmpl` now declares
  every instance member of another library's interface that a class
  here extends, in the `uses` section (2026-11-06), but Python, C++,
  Rust, and the others each find those members and the class's own that
  fill them their own way; tmpl could list a class's inherited members,
  own and external, and which fills each, once.
- **Private statics used from the module's init.** JS (`#`), Python
  (pyright's private use), C# (`internal`), and Java (the package's)
  each widen a private static function a static field's value calls
  (2026-11-06); a tmpl fact, "used outside its class", would say so
  once.

## State

Still open:

**Target versions** (to do, 2026-11-04): C#'s generated projects should
target `net48;net6.0`, as Kotlin's library template does, not `net6.0`
alone; checking the `net48` side builds and tests needs .NET Framework
reference assemblies, a package to approve first; and the modern target
should move to .NET 8 (LTS) together with Kotlin. Java keeps 17 as its
modern floor, and may add a Java 21 target later (pattern-matching
`switch`, record patterns), as Java 8 is one. Also still open: C#'s
`Optional<T>` wrapping for a `T?` field or getter read (only calls wrap
and unwrap).

## Level 3: after subset A

Subset A's details are in the topic files' Level 3 sections. What the
later subsets will need, briefly (Proposed):

- **Subset B** (classes and the rest of Python's functional tests):
  - Nodes: `Class`, `Interface`, `Method`, `Constructor`, `Getter`,
    `Setter`, `Field`, `Test`; `This`, `GetField`, `SetField`,
    `GetStatic`, `SetStatic`, `CallMethod` (virtual or not), `New`,
    `FunctionRef`, `CallValue`, and `FunctionLiteral` with captures;
    the conversions, `Is`, and `As`; `NullLit`, `IsNull`,
    `EqNullable`, and `Identical`; `Labeled`, `OrElse`, `Bubble`
    (removed 2026-11-27), `Panic`, `ForOf`, `When`, and `Omitted`, with parameter defaults.
  - Types: `Nominal`, `TypeParam`, `Nullable`, `Never`, and subtyping
    through explicit conversions.
  - Modules: imports, tests, and `uses` sections with link keys for
    external declarations.
  - Surface: constructor and property forms, and the `Eq` and `Show`
    protocols.
  - Python's passes: labeled jumps (flags or restructuring) and
    `Switch` as `if` chains; `ForOf`, `orelse` (as `try`), and
    `EqNullable` stay native or map directly.
  - Validator: conversions in T3, captures in D5, and `@imu` in D4.
  - Text: class bodies, `uses` sections, annotations
    (`@operator("==")`), and conversions as keyword forms, such as
    `upcast(e)`.
- **Subset C** (Rust): `Result` and the results pass (`Ok`, `Fail`,
  `Propagate`, `ResultOr`); cells for assigned captures;
  properties as methods; first-use init (`EnsureInit`) and synchronized
  module `var`s; liveness and last use for clones; Rust's
  `NamingRules` (snake_case, `r#`, rebinding allowed).
- **Subset D:** `Yield` and `Await`, generator and `async` marks, and
  the state-machine pass (`Dispatch`, `Switch`, `Goto`, `Label`), with
  Rust's native `async` ([question 20](open-questions.md)); then the other backends'
  profiles and `NamingRules`: C#'s ban on shadowing, Lua's `goto` for
  `continue`, Java's named function types, C++'s evaluation-order
  temporaries, and JS.
