# The settle loop in detail

The settle loop takes a library's trees from [syntax](01-syntax.md) and
rewrites them, in place, until every node is a core construct or an
error: names resolved, macros expanded, and, in a second round, types
inferred ([typing](03-typing.md)). This page is the long companion to
[2. The settle loop](../02-settle-loop.md): the decisions behind the
loop, its main types and functions, the tests that pin it, and what's
weak. It's about the compiler's architecture; for what Temper's macros
and names mean, see the [language reference](../../language/README.md).

## Contents

- [Where it sits](#where-it-sits)
- [Decisions](#decisions)
  - [Work items waiting on facts, instead of stages](#work-items-waiting-on-facts-instead-of-stages)
  - [A scheduler that knows nothing of Temper](#a-scheduler-that-knows-nothing-of-temper)
  - [Facts are only added](#facts-are-only-added)
  - [Deterministic order, and forcing the earliest stuck item](#deterministic-order-and-forcing-the-earliest-stuck-item)
  - [A step budget](#a-step-budget)
  - [Two rounds, and passes between and after](#two-rounds-and-passes-between-and-after)
  - [Reading a block before visiting it](#reading-a-block-before-visiting-it)
  - [Names as ids, symbols in one table](#names-as-ids-symbols-in-one-table)
  - [Builtin macros in Rust, rewriting in place](#builtin-macros-in-rust-rewriting-in-place)
  - [Decorators matched by name](#decorators-matched-by-name)
  - [`@json`'s members generated as Temper text](#jsons-members-generated-as-temper-text)
  - [Imported modules settled with the importer](#imported-modules-settled-with-the-importer)
  - [Top levels in dependency order](#top-levels-in-dependency-order)
- [The main types and functions](#the-main-types-and-functions)
- [Tests](#tests)
- [Known weaknesses and planned changes](#known-weaknesses-and-planned-changes)

## Where it sits

```mermaid
flowchart LR
  files["Library: every file's tree in one arena"] --> r1["Round 1: read blocks, visit expressions, inherit"]
  r1 --> dead["drop_dead_branches"]
  dead --> flow["find_flow_facts, parents"]
  flow --> r2["Round 2: infer types, decide object literals"]
  r2 --> checks["check_types, type_all_exprs"]
  checks --> order["run_order, sort_top_levels, tests"]
  order --> settled["Settled: tree, scopes, typing, module order"]
```

[`settle`](../../../crates/temper-frontend/src/settle.rs) takes a
[`Library`](../../../crates/temper-frontend/src/library.rs) and returns a
[`Settled`](../../../crates/temper-frontend/src/settle.rs): the rewritten
tree, the [`Scopes`](../../../crates/temper-frontend/src/scope.rs) its
names resolved to, the [`Typing`](../../../crates/temper-frontend/src/typing.rs)
table, and each module as a
[`SettledModule`](../../../crates/temper-frontend/src/settle.rs), in the
order modules run. The interpreter, `to_tmpl`, and the editor queries
all start from a `Settled`. Its state while running is one struct,
[`Settle`](../../../crates/temper-frontend/src/settle.rs), whose methods
are spread by topic over `settle.rs`, `macros.rs`, `classes.rs`,
`imports.rs`, `testing.rs`, `extensions.rs`, `flow.rs`, and typing's
files.

## Decisions

### Work items waiting on facts, instead of stages

**Chosen:** each piece of work is an item. A step either finishes it or
stops on one missing fact, and the item waits on exactly that fact.
Publishing the fact makes exactly its waiters ready. The facts today are
the four cases of [`Fact`](../../../crates/temper-frontend/src/settle.rs):
a scope's declarations are complete, a class's scope is complete with
what it inherits, a declaration's type is known, and an object literal's
class is decided.

**How Kotlin Temper does it:** a module advances through fixed stages
(`stage/Stage.kt`: Lex, Parse, Import, DisAmbiguate, SyntaxMacro,
Define, Type, FunctionMacro, Export, Query, GenerateCode, and Run), each
one a pass over the whole module, mostly by partially interpreting it
(`InterpretiveDanceStage.kt`), which calls macros as values found in the
environment. A module waits for the modules it imports to reach Export
before it moves on (`Module.kt`, `prerequisitesMetFor`).

**Alternatives:** fixed passes, as Kotlin; a demand-driven query system
with memoization and recomputation (salsa style); or repeated passes to
a fixed point.

**Pros:**

- Forward references need no stage order: a name used before it's
  declared waits on its scope, nothing more.
- A macro that needs a type waits for it like any other fact, rather
  than needing a later stage of its own (the object literal does today).
- Work is done once: nothing is re-walked unless something it waits on
  arrives.
- Why something hasn't settled is a question with an answer: the fact
  an item waits on ([`Engine::chain`](../../../crates/temper-frontend/src/engine.rs)).

**Cons:**

- No stage boundary to dump and diff, as Kotlin's REPL can dump each
  stage; the settled tree is the one snapshot.
- Order is subtler: every rule that keeps results deterministic and
  final has to hold for every step ([below](#facts-are-only-added)).
- Recomputation isn't there: a fact can't change, so an editor's
  incremental rebuild can't reuse this within a library.

**Why:** Temper's macros can declare names other statements need, and
can need types before they know what to produce, so any fixed stage
order is wrong for some program ([notes/design.md](../../../notes/design.md),
section 5). This compiler's design, not yet presented to the team.

### A scheduler that knows nothing of Temper

**Chosen:** [`Engine`](../../../crates/temper-frontend/src/engine.rs) is
generic over its fact type and holds only items, waits, and facts. What
an item does is [`Work`](../../../crates/temper-frontend/src/settle.rs),
dispatched in [`run`](../../../crates/temper-frontend/src/settle.rs).

**Pros:** about 250 lines, with unit tests that simulate plans of waits
without any Temper. Steps can't reach into scheduling.

**Cons:** steps return `Err(fact)` through many layers (`Result<_,
Fact>` runs through most of typing), and re-run from the start when
woken, so a step must be safe to redo. Partial progress is kept by hand,
as a block's `next` statement.

### Facts are only added

**Chosen:** a resolved name never changes, a declaration's type never
changes, and a macro rewrites only its own call node. Three rules keep
that true:

- **Outside in.** A call's arguments aren't visited until its callee
  resolves, and nothing under a macro call is touched until it expands.
- **No early publishing.** A macro call publishes nothing until it has
  expanded.
- **Lookups that passed an incomplete scope are recorded.** A statement's
  own macro name, and a forced lookup, may resolve past a scope that's
  still being read. Each such lookup is kept, and if the name is
  declared there later, that's an error, `DeclaredAfterUse`, rather than
  a changed resolution ([`declare`](../../../crates/temper-frontend/src/settle.rs)).

Debug builds check the rest: every read of a declaration and every
rewrite is logged with its step, and
[`check_reads`](../../../crates/temper-frontend/src/settle.rs) asserts no
step read a node that a later step rewrote. Tests run in debug, so the
fuzzer checks this on every input.

**Pros:** termination and determinism are easy to argue, and nothing is
ever recomputed.

**Cons:** a macro that would like to look at a sibling after it settles
can't, and typing can't revise a type it's published, so whatever's
forced stays unknown.

### Deterministic order, and forcing the earliest stuck item

**Chosen:** the ready queue is a heap ordered by (phase, source
position, creation). When it's empty and items still wait,
[`next_step`](../../../crates/temper-frontend/src/engine.rs) forces the
earliest waiting item ([`stuck`](../../../crates/temper-frontend/src/engine.rs)):
its step runs again knowing that its fact won't come, and usually makes
an error node, or for a type, leaves it unknown. A forced lookup treats
scopes as complete.

**Alternatives:** report every stuck item at once; or break only true
cycles and report the rest.

**Pros:** the same input gives the same tree and the same errors, in the
same order, every run, which the fuzzer checks. Forcing one item at a
time keeps follow-on errors down, since forcing it may let others go on.

**Cons:** a long chain of stuck items is forced one by one, and finding
each costs a scan ([below](#known-weaknesses-and-planned-changes)).

### A step budget

**Chosen:** `1000 + 100 × nodes` steps, set once from the tree's size at
the start. Running out reports `OutOfSteps` ("Gave up after N steps") at
the next item's node and stops the round.

**Pros:** a runaway expansion can't hang the compiler, in the editor
or the playground.

**Cons:** the message doesn't say what was looping; the design meant it
to name the cycle. When round 1 runs out, typing and its checks don't
run at all.

### Two rounds, and passes between and after

**Chosen:** [`settle`](../../../crates/temper-frontend/src/settle.rs) runs
the loop twice: first expansion (phases 0 and 1: read blocks, visit
expressions, inherit), then typing (phase 2: infer declarations' types
and decide object literals). Between them,
[`drop_dead_branches`](../../../crates/temper-frontend/src/dead_branches.rs)
keeps only the live branch of an `if (false)` whose dead branch has an
error, after folding an undeclared name's `!= null` to `true`. Kotlin
does both silently; this compiler reports the errors as errors and folds only
to recover, so the code generated past them runs as Kotlin's does. Then
[`find_flow_facts`](../../../crates/temper-frontend/src/flow.rs)
walks every function. After them, typing's checks and recording, then
module order and top-level order, are passes over the whole tree.

**Alternatives:** truly one loop, with typing items made as soon as a
declaration expands, as the design describes; or full stages.

**Pros:** simple and fast today. Every macro but the object literal needs
only syntax, so typing after expansion loses nothing yet. Flow facts and
the checks see the whole settled tree.

**Cons:** "no stages" overstates it. The overview and the design notes
say one loop; it's one scheduler, run twice, with passes around it. A
macro can't wait on a type in round 1, so Temper-written macros and
on-demand compile-time evaluation, which Kotlin's interpretive dance
does, need typing to interleave with expansion first.
[notes/compile-time.md](../../../notes/compile-time.md) plans that, and
names a `temper-eval` crate that doesn't exist yet.

**Why:** typing came in milestone 4, after the loop, and the second
round was the smallest step. It's a known gap, to say plainly.

### Reading a block before visiting it

**Chosen:** a block's statements are read in order first
([`read_block`](../../../crates/temper-frontend/src/settle.rs), phase 0),
each expanded only as far as needed to see what it declares
([`head`](../../../crates/temper-frontend/src/settle.rs)): a statement
whose callee is a macro is expanded at once, and again if that gives
another macro call. Then the scope is complete, and the statements are
visited (phase 1) by [`expr`](../../../crates/temper-frontend/src/settle.rs).

A statement's macro name is looked up against the declarations so far
(`so_far` in [`lookup`](../../../crates/temper-frontend/src/scope.rs)),
so `let` can expand before the block is complete; a later local of the
same name then shadows it only after its declaration, as in Kotlin. A
block's locals are visible from their declaration on, and functions and
classes all through the block, for mutual recursion
([`declare`](../../../crates/temper-frontend/src/settle.rs)), which are
Kotlin's rules.

**Pros:** forward references work without a separate declaration pass,
and macro names resolve without waiting on the block they're in.

**Cons:** the phase split is per block, so a deep tree goes through many
small reads and visits, one item per expression visited.

### Names as ids, symbols in one table

**Chosen:** a name's text is an interned
[`NameId`](../../../crates/temper-syntax/src/names.rs); resolution is a
side table from each name node to a
[`SymbolId`](../../../crates/temper-frontend/src/scope.rs); and symbols
are one flat table of [`Symbol`](../../../crates/temper-frontend/src/scope.rs)
structs, each with a [`SymbolKind`](../../../crates/temper-frontend/src/scope.rs),
owner [`Scope`](../../../crates/temper-frontend/src/scope.rs), slot,
and declaring node. A macro that means a particular builtin inserts a
name already resolved to it ([`builtin_name`](../../../crates/temper-frontend/src/settle.rs),
[`hidden`](../../../crates/temper-frontend/src/scope.rs)), so a user's
local can't capture it. Compiler temporaries are never interned, so they
can't clash with source names.

**How Kotlin Temper does it:** a hierarchy of name types
(`ParsedName`, `ResolvedName`, `SourceName`, `Temporary`, `BuiltinName`,
`ModularName`, `ExportedName`, and more), with the tree re-mapped from
one to another as stages go.

**Pros:** comparing names is comparing integers; hygiene without a
separate resolved-name type; a symbol's slot is its place in the
interpreter's frame.

**Cons:** scopes keep their symbols in a `Vec`, scanned from the end on
each lookup. That's fine for blocks, but the builtins scope has about a
hundred names, and every global lookup that reaches it scans them,
although [`Scopes`](../../../crates/temper-frontend/src/scope.rs) has a
map from name to builtin beside it.

### Builtin macros in Rust, rewriting in place

**Chosen:** every macro is builtin: a case of
[`Macro`](../../../crates/temper-frontend/src/builtins.rs), expanded by
[`expand`](../../../crates/temper-frontend/src/macros.rs) into
[core constructs](../../../crates/temper-frontend/src/core.rs) by
rewriting its own call node ([`rewrite`](../../../crates/temper-frontend/src/settle.rs)).
Whatever it produces is visited afterwards. Builtins the grammar names
directly, such as `desugarOperation` and `stringExpr`, go through
[`expand_syntax`](../../../crates/temper-frontend/src/macros.rs).

**How Kotlin Temper does it:** macros are values in the builtin
environment, written in Kotlin, and the interpreter calls them as it
walks the module in each stage. Temper-written macros run the same way.

**Pros:** each expansion costs about as much as a parser rule; the core
constructs are a small, fixed set that typing, the interpreter, and
`to_tmpl` all understand ([`core`](../../../crates/temper-frontend/src/core.rs)
documents each layout).

**Cons:** no macros written in Temper yet, and adding a macro means
adding Rust. Core constructs pack flags and counts into the node's
`data` word (`LET_VAR`, `CLASS_SEALED`, counts shifted by
`TYPE_PARAMS_SHIFT`), so layouts are read through helpers such as
[`let_parts`](../../../crates/temper-frontend/src/core.rs) and
[`fn_parts`](../../../crates/temper-frontend/src/core.rs), not types.

### Decorators matched by name

**Chosen:** `@export`, `@fun`, `@sealed`, `@keep`, `@json`,
`@connected`, `@var`, and `@auto("==")` are recognized by the
decorator's text in [`expand_decorate`](../../../crates/temper-frontend/src/macros.rs),
and member modifiers (`public`, `private`, `static`, `connected`,
`overload`, `noProperty`, and more) by text in the class macro. Each
recognized one adds its declaration to a set on
[`Settle`](../../../crates/temper-frontend/src/settle.rs) (`exports`,
`connected`, `fun_interfaces`, `sealed`, `json_decorated`, and others),
read when the declaration expands.

**How Kotlin Temper does it:** decorators are builtins in the
environment (`BuiltinEnvironment.kt`), resolved like any name.

**Cons:** a user's own `export` or `connected` doesn't shadow the
builtin, as it would in Kotlin; a misspelled decorator is silently
ignored; and a declaration's modifiers are spread over eight sets
rather than one record, which [notes/design.md](../../../notes/design.md)
("Declaration facts in one place") asks for. A few other places match
text the same way: `rgx` in [`call`](../../../crates/temper-frontend/src/settle.rs)
and `label` in [`enter_block`](../../../crates/temper-frontend/src/settle.rs).

**Status:** a known shortcut, with a plan to resolve decorators as
builtins.

### `@json`'s members generated as Temper text

**Chosen:** when the class macro lowers a `@json` type,
[`json_members`](../../../crates/temper-frontend/src/json.rs) writes its
`encodeToJson`, `decodeFromJson`, and `jsonAdapter` and its adapter
class as Temper source with placeholders, parses it
([`add_fragment`](../../../crates/temper-frontend/src/library.rs)), and
puts in for each placeholder a copy of the type's own type expression, a
value, or a name resolved in `std/json` ([`resolve_std_ref`](../../../crates/temper-frontend/src/json.rs)),
whatever the module imports. The members are lowered as written ones
are, and the adapter class is read after the type's statement. A sealed
interface's decoder needs all its subtypes, so it's a placeholder call
until the module's scope is complete
([`json_sealed_body`](../../../crates/temper-frontend/src/json.rs)). The
generated nodes are all spanned at the `@json`, with empty spans, so a
local is visible from where it's declared on.

**How Kotlin Temper does it:** `JsonInteropPass` builds the same code as
trees of resolved names, between the define and type stages.

**Cons:** text is parsed again for each type, and an error in generated
code points only at the `@json`.

### Imported modules settled with the importer

**Chosen:** a library is one [`Library`](../../../crates/temper-frontend/src/library.rs).
[`load_imports`](../../../crates/temper-frontend/src/project.rs) adds the
files of every module an import names, std's included, into the
importer's library, and the loop settles them all together. An import
([`import`](../../../crates/temper-frontend/src/imports.rs)) waits on the
imported module's scope, then becomes `let c = b`, with `b` resolved to
the exported symbol. Modules then run in import order
([`run_order`](../../../crates/temper-frontend/src/settle.rs)).

**Alternatives:** settle each library once and give importers only its
exported interface, as the design describes ("Imports across
libraries"), and as [notes/library-cache.md](../../../notes/library-cache.md)
sketches.

**Pros:** one arena, one loop, and the same code path for one file or
fifty; cross-module forward references wait like any other.

**Cons:** each importer settles its dependencies again, so cost grows
with import depth, and a dependency's errors are reported once per
importer, counted against the importer. Which library a file belongs to
is worked out afterwards from directory prefixes. No separate
compilation, and so no reuse across libraries in the language server.

**Status:** the biggest architectural gap in this stage, with the
library cache as the planned fix; reporting each diagnostic once, under
its own library, is the near-term one.

### Top levels in dependency order

**Chosen:** after settling, each module's top-level statements are put
in dependency order ([`sort_top_levels`](../../../crates/temper-frontend/src/order.rs)),
as Kotlin's `SortTopLevels.kt` arranges them: each after the
declarations it reads and their assignments, otherwise in source order,
a cycle broken where the walk enters it. The settled tree is reordered
to match, so it prints in the order it runs. This is Kotlin's language
behavior, copied.

## The main types and functions

### The scheduler

- [`Engine`](../../../crates/temper-frontend/src/engine.rs): items, a
  ready heap, `waiters` from each fact to its items, `producers` from a
  fact to the item that will publish it, and `published`.
- [`ItemId`](../../../crates/temper-frontend/src/engine.rs) and
  [`Item`](../../../crates/temper-frontend/src/engine.rs): an item's phase,
  position, fact waited on, and whether it's done or queued.
- [`Next`](../../../crates/temper-frontend/src/engine.rs): run, force,
  settled, or out of budget.
- [`add`](../../../crates/temper-frontend/src/engine.rs),
  [`wait`](../../../crates/temper-frontend/src/engine.rs),
  [`again`](../../../crates/temper-frontend/src/engine.rs),
  [`done`](../../../crates/temper-frontend/src/engine.rs),
  [`publish`](../../../crates/temper-frontend/src/engine.rs), and
  [`will_produce`](../../../crates/temper-frontend/src/engine.rs).

### The loop

- [`Fact`](../../../crates/temper-frontend/src/settle.rs),
  [`Work`](../../../crates/temper-frontend/src/settle.rs) (block, expression,
  inherit, type, object literal), and the phases `READ_BLOCKS`, `VISIT`,
  and `TYPES`.
- [`run`](../../../crates/temper-frontend/src/settle.rs) dispatches each
  item and records its [`Outcome`](../../../crates/temper-frontend/src/settle.rs).
- [`read_block`](../../../crates/temper-frontend/src/settle.rs),
  [`head`](../../../crates/temper-frontend/src/settle.rs), and
  [`declare`](../../../crates/temper-frontend/src/settle.rs): reading.
- [`expr`](../../../crates/temper-frontend/src/settle.rs),
  [`call`](../../../crates/temper-frontend/src/settle.rs),
  [`resolve_name`](../../../crates/temper-frontend/src/settle.rs), and
  [`lookup`](../../../crates/temper-frontend/src/settle.rs): visiting.
- [`enter_block`](../../../crates/temper-frontend/src/settle.rs),
  [`enter_fn`](../../../crates/temper-frontend/src/settle.rs),
  [`enter_type_params`](../../../crates/temper-frontend/src/settle.rs), and
  [`enter_class`](../../../crates/temper-frontend/src/classes.rs): scopes
  from node kinds. [`inherit`](../../../crates/temper-frontend/src/classes.rs)
  completes a class's scope once its supertypes' are.
- [`rewrite`](../../../crates/temper-frontend/src/settle.rs),
  [`set_children`](../../../crates/temper-frontend/src/settle.rs),
  [`become_node`](../../../crates/temper-frontend/src/settle.rs), and
  [`error`](../../../crates/temper-frontend/src/settle.rs): every change to
  the tree, logged for the debug check.

### Scopes and symbols

- [`Scopes`](../../../crates/temper-frontend/src/scope.rs),
  [`Scope`](../../../crates/temper-frontend/src/scope.rs), and
  [`ScopeKind`](../../../crates/temper-frontend/src/scope.rs).
- [`Symbol`](../../../crates/temper-frontend/src/scope.rs) and
  [`SymbolKind`](../../../crates/temper-frontend/src/scope.rs).
- [`Lookup`](../../../crates/temper-frontend/src/scope.rs): found, wait on
  a scope, or missing.

### Macros and builtins

- [`Builtin`](../../../crates/temper-frontend/src/builtins.rs),
  [`Macro`](../../../crates/temper-frontend/src/builtins.rs), and
  [`Function`](../../../crates/temper-frontend/src/builtins.rs); the names
  every module starts with are [`BUILTINS`](../../../crates/temper-frontend/src/builtins.rs)
  and [`TYPES`](../../../crates/temper-frontend/src/builtins.rs).
- [`expand`](../../../crates/temper-frontend/src/macros.rs) and the
  macros: [`expand_let`](../../../crates/temper-frontend/src/macros.rs),
  [`expand_fn`](../../../crates/temper-frontend/src/macros.rs),
  [`expand_when`](../../../crates/temper-frontend/src/macros.rs),
  [`expand_decorate`](../../../crates/temper-frontend/src/macros.rs),
  [`desugar_operation`](../../../crates/temper-frontend/src/macros.rs),
  [`expand_class`](../../../crates/temper-frontend/src/classes.rs),
  [`expand_test`](../../../crates/temper-frontend/src/testing.rs), and the
  object literal, [`object_literal`](../../../crates/temper-frontend/src/classes.rs).
- [`flatten_declarations`](../../../crates/temper-frontend/src/macros.rs)
  and [`destructure`](../../../crates/temper-frontend/src/macros.rs): one
  statement standing for several, spliced into the block as it's read.

### Around the loop

- [`drop_dead_branches`](../../../crates/temper-frontend/src/dead_branches.rs),
  [`run_order`](../../../crates/temper-frontend/src/settle.rs), and
  [`sort_top_levels`](../../../crates/temper-frontend/src/order.rs).
- [`write_settled`](../../../crates/temper-frontend/src/print.rs): the
  settled tree as `run-temper --tree` and the tests print it.

## Tests

| What | Where | Covers |
|---|---|---|
| Scheduler | unit tests in [`engine.rs`](../../../crates/temper-frontend/src/engine.rs) | Phase and position order, waking exactly the waiters, cycles broken at the earliest item, chains, the budget |
| Settle cases | [`names.cases`](../../../crates/temper-frontend/tests/settle/names.cases) (12), [`macros.cases`](../../../crates/temper-frontend/tests/settle/macros.cases) (23), [`builtins.cases`](../../../crates/temper-frontend/tests/settle/builtins.cases) (11), [`classes.cases`](../../../crates/temper-frontend/tests/settle/classes.cases) (18), [`run.cases`](../../../crates/temper-frontend/tests/settle/run.cases) (63) | The settled tree, diagnostics, and what running it logs, as snapshots |
| Integration | [`settle.rs`](../../../crates/temper-frontend/tests/settle.rs): `imports_across_modules`, `std_imports_and_spread`, `tests_and_asserts`, `regex_literals`, `bracket_recovery_stays_local` | Multi-module libraries, std, tests, and recovery |
| Property fuzzer | `mutated_inputs_settle` in [`settle.rs`](../../../crates/temper-frontend/tests/settle.rs) | Random edits of every settle and grammar case: no panic, the debug read check holds, within the step budget, the same output twice, and running it doesn't panic. `SETTLE_FUZZ_DIR` adds a directory of sources |
| Functional tests | `compat/check_functional.py`, in CI | Kotlin's 72 functional tests built and run, logs compared |
| Real libraries | the backends' tests and the nightly | Chromarite, Ordable, Hrefine, Kelpto, and Wherewith settled and run |

**What they don't cover:**

- The fuzzer starts from the cases only (unless `SETTLE_FUZZ_DIR` is
  set) at 20 edits each, so it's small, and it checks properties, not
  meaning.
- Nothing pins how forcing orders errors on a long chain, beyond the
  engine's unit tests and whatever cases happen to force.
- Nothing measures how the loop scales with error-heavy input, which is
  what an editor feeds it.
- No test runs a library that imports another and checks how often its
  diagnostics are reported.

## Known weaknesses and planned changes

- **Re-settling imports** ([above](#imported-modules-settled-with-the-importer)):
  each importer settles its dependencies again and repeats their errors.
  Near term, report each diagnostic once, under its own library; later,
  settle each library once and import its interface
  ([notes/library-cache.md](../../../notes/library-cache.md)).
- **The stuck search scans every item.** [`stuck`](../../../crates/temper-frontend/src/engine.rs)
  looks at every item ever added, done ones too, and walks each wait
  chain with a fresh set ([`can_end`](../../../crates/temper-frontend/src/engine.rs)),
  so error-heavy code, which forces often, costs forces × items × chain
  length. The fix keeps the waiting items in an ordered set. In fact,
  when nothing is ready, every unfinished item is waiting, so every
  chain ends at a done item, a fact nobody produces, or a loop: the
  stuck item is just the earliest waiting one, and the chain walk can go.
- **Waits aren't explained.** [`Engine::chain`](../../../crates/temper-frontend/src/engine.rs)
  answers "why is this waiting?", but only its unit test calls it: a
  forced item's error and `OutOfSteps` don't name the chain or cycle, as
  the design intends, and `producers` serves only `stuck` and `chain`.
- **Two rounds, not one loop** ([above](#two-rounds-and-passes-between-and-after)):
  the docs should say so plainly until macros can wait on types in round
  1 and compile-time evaluation exists.
- **Decorators matched by name** ([above](#decorators-matched-by-name)):
  resolve them as builtins, and keep a declaration's modifiers in one
  record.
- **Linear scans in hot paths:** the builtins scope in
  [`lookup`](../../../crates/temper-frontend/src/scope.rs); the lookups
  that passed an incomplete scope, scanned on every
  [`declare`](../../../crates/temper-frontend/src/settle.rs); and
  `unwait`'s `retain` over a fact's waiters.
- **Panics on broken invariants:** steps `unwrap` what earlier steps
  promise (a name node's name, a let's first child). The fuzzer has
  found none lately, but one panic ends a language server session, and
  nothing catches it there yet.
