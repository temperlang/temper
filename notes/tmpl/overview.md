# tmpl: purpose, boundaries, and core decisions

Part of the [tmpl design](README.md).

## Purpose

tmpl is the one form between the frontend and every backend: a settled,
typed, fully explicit program that a backend can translate without
asking anything of the frontend. It is also:

- **The library cache's body** ([library-cache.md](../library-cache.md)):
  the starting tmpl, before any backend's lowering, is the stable form a
  built library can ship.
- **A readable debugging and test format,** through its text form.
- **Where shared backend work lives:** lowering, naming, and the checks
  that keep each backend small (design.md section 9).
- **The source of each library's public surface,** carrying enough
  intent that every backend can present an idiomatic API ([surface.md](surface.md)).

## Boundaries

**Decided:** a standalone crate, mostly or entirely: the data model, its
formats, a validator, and passes and helpers that adapt a program to
different kinds of languages. No frontend, and no translators for real
targets.

```text
temper-syntax ─► temper-frontend ──(to_tmpl)──► temper-tmpl ◄── temper-be-py, temper-be-rust, …
                                                    ▲                    ▲
                                       (later) tmpl interpreter    shared output tree + printer
```

- **In the crate (Proposed name `temper-tmpl`,** matching the others):
  the model, builder, validator, profiles, lowering passes, analyses,
  surface helpers, the naming pass and `NamingRules` (design.md section
  8), the text reader, writer and viewer, and later the binary codec.
  No dependencies, not even on `temper-syntax`, but `temper-os`, for
  the OS calls behind the processes tools start (it has none itself on
  wasm).
- **In the frontend:** the translation from settled trees into tmpl, in
  `temper-frontend` (it depends on `temper-tmpl`, never the reverse).
- **In each backend crate** (one per language): the translator to the
  shared output tree, its tables for operators and for core's and
  std's connected declarations by qname, and its named **variants** (Decided 2026-10-29: several
  translation options per language must be cheap, as variants inside
  the one crate, not separate crates). A variant is data: a profile,
  `NamingRules`, and surface choices, over the crate's shared
  translation code. For example, `temper-be-py` might offer a variant
  compatible with Kotlin Temper's current output, for existing users,
  and another refined toward idiomatic Python, chosen by name on the
  command line. Nothing in tmpl assumes one variant per language, and
  API compatibility ([api-compat.md](../api-compat.md)) is checked per
  variant. Backends depend on `temper-tmpl` and the printing crate
  only. Decided (2026-10-30): a backend works from tmpl alone, so it
  depends on the frontend at most as a test dependency (to translate
  the functional tests into tmpl for its tests and goldens).
- **In the `tempest` crate,** the command-line program: for now, it
  pulls in the frontend and every backend and wires them together.
  Later, maybe, backends become pluggable at run time, as dynamic
  libraries behind a C ABI or as subprocesses that read tmpl (the
  binary or text form, from stdin or a file) and write files, which
  would also let backends live outside tempest, even in other
  languages. Put off for now (the user, 2026-10-30).
- **Not in tmpl:** output syntax, precedence, layout, source map
  encoding, runtime libraries, or anything chosen per backend before
  lowering.

## The crate's public API, in shape

Proposed; names are placeholders until Level 3.

```rust
/// One library's program: modules plus the tables they share.
pub struct Program {
    pub library: LibraryInfo,      // name, version, dependencies
    pub modules: Vec<Module>,      // in dependency order, then source order
    pub nodes: Nodes,              // arena: kinds, types, children, payloads
    pub types: TypeTable,          // interned: equal types, equal ids
    pub decls: DeclTable,          // every declared thing, local or external
    pub strings: Interner,
    pub spans: SpanTable,          // per node: file and byte range
    pub docs: DocTable,            // per declaration
}

pub struct Builder { … }           // the frontend's only way in; checks as it goes
pub struct Profile { … }           // profiles.md
impl Profile { pub fn full() -> Profile; } // everything high-level: the starting tmpl

pub fn validate(p: &Program, profile: &Profile) -> Vec<Problem>;
pub fn lower(p: Program, profile: &Profile) -> Program; // only the passes it needs

pub mod passes;   // each lowering, also callable alone (tests, tools)
pub mod analysis; // analyses.md
pub mod surface;  // surface.md: builder, property, and equality forms
pub mod naming;   // NameRequest, NamingRules, assign() -> NameMap
pub mod text;     // write, read, view(ViewOptions)
pub mod binary;   // write, read (later)
```

A backend variant's whole use of the crate: `lower(program,
&VARIANT.profile)`, then `naming::assign(&lowered, &VARIANT.rules)`,
then walk the result, asking `surface` and `analysis` where it needs
to.

## Core decisions

### Fully explicit

**Decided:** more explicit than the frontend, since tmpl must be enough
for every backend. Proposed meaning, item by item:

- **Types on every expression,** as interned `TypeId`s (a `u32` each).
  A backend never infers a type.
- **Resolved names:** every reference points at a `DeclId`, which
  carries exactly a `NameRequest` (design.md section 8; [names.md](names.md)).
  tmpl holds no output names; the naming pass returns them in a side
  table.
- **Conversions are nodes:** upcasts to an interface, a function
  becoming a `@fun` interface's value and back, a non-null value used as
  nullable, a checked not-null, and `Never<T>` used as a `T`. Kotlin's
  Rust translator works these out again from types; here they're in the
  tree.
- **Captures are listed** on each function literal, each marked if
  assigned after capture (Python's `nonlocal`, shared cells in Rust and
  Java, PHP's `use (&$x)`), and checked by the validator against a
  recomputation.
- **Evaluation order** is left to right, as Temper's, and a fact rather
  than a shape: tmpl keeps nested expressions, and a pass hoists into
  temporaries only where a profile says the target doesn't guarantee
  the order (C++ arguments), and only for impure operands.
- **Bubbling paths:** every call that may bubble is marked, and its
  handler is the nearest enclosing `orelse` or the function's exit, so
  every lowering of failure reads the same facts.
- **Operators are typed, and there is no generic `==`**
  (Decided 2026-10-29). Each builtin type's operators are its own
  `Op`s (`Boolean.eq`, `Int32.add`), never a
  generic operator a backend may get wrong (Kotlin's Boolean `==` bug in
  Lua, JS, and Python). A class's `==` is a call of its operator method,
  a null comparison is a null check, and identity is its own explicit
  operation ([nodes.md, "Equality"](nodes.md#equality)).
- **Source positions** on every node (file and byte range), and **doc
  comments** on every declaration, for source maps and generated docs.

### Independent of the frontend, and one starting form

**Decided:** nothing from the frontend in the model. Proposed in detail:

- **Its own types, names, values and metadata,** all ids into its own
  tables. Literals are a small enum (Boolean, the numeric types, String,
  null, Void), never interpreter values.
- **One starting tmpl for all backends.** Kotlin consults the backend's
  `SupportNetwork` while translating from the frontend, so its tmpl is
  per backend and can't be cached, compared, or tested alone. Here,
  every per-backend choice is a pass after the starting form.
- **Core and std are declarations; only operators are built in**
  (Decided 2026-10-29: customization is general, by naming, and only
  for operators and connected declarations). A call of
  `n.toString()` or `console.log(s)` is an ordinary call of core's
  external declaration ([nodes.md](nodes.md#level-3-ops-and-core-as-declarations)).
- **Every declaration has a qname** (Decided 2026-10-29: a place for
  them; Proposed: Kotlin's format exactly, derived from owners and
  names, [names.md](names.md#level-3-qnames)).
- **Connected is one fact, resolved two ways** (Decided 2026-10-29;
  [names.md](names.md#level-3-connected-declarations)): core's and
  std's connected declarations by qname, through each variant's table
  of templates, runtime names, and translation or lowering hooks; user
  code's by name, as host code defines it under the name the
  translation gives. Starting tmpl only records which, so it stays the
  same for every backend.

### Arena layout, like the tree

Proposed: nodes in arrays with `u32` ids, children as ranges, payloads
in side tables, interned strings and types, as design.md section 3's
tree. That keeps passes fast, makes the binary form a near-direct dump,
and keeps order deterministic. Passes rebuild into a fresh arena rather
than editing in place, which keeps each pass simple and the old program
intact for comparison in tests.

## Lessons from Kotlin Temper's tmpl

From Kotlin Temper's `tmpL.out-grammar`, `TmpLTranslator.kt`,
`SupportNetwork.kt`, `QName.kt`, its `EqMacro`, and the Python, Rust, and C++
backends:

- **Avoided here:** nodes holding frontend objects, so tmpl can't exist
  without the frontend; per-backend choices made during translation;
  and two of several things (old and new type nodes).
- **What backends had to recompute, now stored:** captures,
  conversions, `for … of` (a `forEach` with a lambda there, so `yield`
  in its body fails), and module state shared across threads.
- **Bugs it let through, each with a test here:** types sorted before
  values; Boolean `==`; C++'s race on lazily set module `var`s; Rust's
  indexing that panics where Temper bubbles; C++ templates declared
  after their users.
- **Kept:** qnames, in Kotlin's format, and connected keys as qnames;
  operators as a closed set; `Error` nodes; bubble and coroutine
  strategies, as profile choices; a computed jump node for state machines; equality without a
  generic operator, as Kotlin's 2026-09 relational-operator rework
  began.
