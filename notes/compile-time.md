# Compile-time evaluation: macros, constants and inlining

Part of the [rewrite plan](README.md).

Temper runs code at compile time: macros (built-in and written in Temper),
constants, and the evaluation that inlining and some typing decisions rely on.
Kotlin Temper does much of this by partially interpreting whole modules at
several stages (`InterpretiveDanceStage`), with a cap to stop infinite loops.
Tempest needs the same semantics, without paying to interpret everything,
and with a structure that stays easy to follow.

## Principle: evaluate only on demand

Nothing is interpreted unless the settle loop needs a value:

- a macro call whose macro is known and whose inputs are ready,
- a constant that something else needs (an array size, a type argument, a
  config export, a value another macro inspects),
- a call the inliner decided to fold, with known arguments.

Everything else stays as tree and goes to code generation untouched. Most code
in a library is never evaluated at compile time.

## Two kinds of macros

1. **Built-in macros** (`let`, `fn`, `if`, `while`, `class`, `when`, `test`,
   `assert`, and the rest) are Rust functions in a table:
   `fn(&mut SettleCx, NodeId) -> Step`. They inspect and rewrite tree nodes
   directly. No interpreter is involved, so they cost about as much as a
   parser rule. These are almost all of the macro calls in practice.
2. **Macros written in Temper** run in the interpreter. They receive tree
   fragments as values and return new fragments, which replace their call
   node.

Both kinds expand exactly once: the expansion replaces the call node, so it's
never revisited.

## A cheap interpreter

The interpreter is its own small crate, `temper-eval`, with one entry point
the settle loop calls:

```rust
fn eval(cx: &mut EvalCx, function: FnId, args: &[Value], fuel: &mut Fuel) -> Result<Value, EvalError>
```

What keeps it fast:

- **Compile once, run many.** The first time a function is evaluated, its
  settled body is lowered to a compact form (a flat list of simple
  instructions with local *slot numbers*, not names) and cached by `FnId`.
  Later calls reuse it. No tree walking or name lookup during execution.
- **Locals in slots,** in a `Vec<Value>` per call frame, not hash maps.
- **Small values:** `Value` is a compact enum (int, float, bool, interned
  string, `Rc` list/map/object, function id, tree fragment), so copying one
  is cheap.
- **No speculative evaluation.** Only demanded calls run (above).
- **Only evaluable code:** pure Temper code, plus connected builtins marked
  safe at compile time. Anything else (I/O, time, randomness) is a
  compile-time error with a clear message, as today.

## Budgets and loop protection

- **Fuel:** every instruction costs one unit. Each demanded evaluation gets a
  budget, and a build has an overall budget too. Running out is an error at
  the call site that started it, naming the macro or function, and the build
  still continues (errors as values).
- **Expansion depth:** macros that expand into more macro calls get a depth
  limit, with the chain of macros reported when it's hit.
- **Cycle detection:** if the same macro is expanding the same input
  (structurally identical, by hash) somewhere further up the same chain, report
  a cycle right away instead of burning fuel.

The limits are generous by default and configurable, so legitimate heavy
macros still work.

The interpreter already takes an optional step budget
(`interp::run(settled, fuel)`); plain runs (`run-temper`) default to none,
so the budget is the compile-time evaluator's to set.

## Caching

- **Within a build:** expansions of the same Temper macro on structurally
  identical input, with the same relevant facts, are memoized, so repeated
  patterns (like many similar declarations) run the macro once.
- **Across watch rebuilds:** libraries that didn't change keep their settled
  summaries, so none of their macros or constants run again. For the library
  being edited, unchanged functions keep their lowered interpreter code
  (keyed by a content hash), so only edited code is lowered again.

## Inlining

Deciding what to inline doesn't need the interpreter. It's a size and usage
heuristic on settled code: small functions, functions called once, and
functions whose arguments are known constants. Inlining itself is a tree
rewrite: copy the callee's nodes into the arena with fresh ids, then
substitute the arguments. Folding the result is ordinary constant evaluation,
on demand.

## Measuring

`--verbose` (or a stats flag) reports per build: macro expansions (built-in vs.
Temper), evaluations, instructions run, time in evaluation, and cache hits.
Check this on the functional tests, std and Chromarite from the start, so cost
regressions show up early. Compare against Kotlin Temper's time for the same
inputs, back to back, since timing on this machine is noisy.

## Why this stays clear

- One place decides *when* to evaluate (the settle loop, by demand).
- One crate decides *how* (`temper-eval`, with one entry point).
- Built-in macros are plain Rust functions over the tree, readable one at a
  time.
- Budgets, depth limits and caches live in the evaluator and the settle loop,
  not scattered through macros.

## Caching and inlining rules (decided 2026-10-22)

- Cache or fold a compile-time result only when it's immutable and
  guaranteed to be the same value it would be at run time. Anything that
  depends on run-time state, or is mutable, stays a run-time computation.
- There should eventually be a way to prevent inlining in some contexts
  (an annotation or option), for code size, debugging, or semantics that
  depend on a call staying a call.
- Folding a call can make closures that didn't exist before it ran
  (Kotlin's `control-flow/for-of-folded` test: `for … of` and `forEach`
  bodies assigning the caller's locals). Only the call's result may be
  kept, and only if it's immutable; the closures and the locals they
  captured must not leak into the folded tree.

## Content tags (contextual autoescaping), 2026-11-10

A tagged string such as a library's `html"<a href='${u}'>"`
settles to `do { let t = new C(); t.appendSafe("<a href='"); t.append(u);
…; t.accumulated }`. Run as written, each use propagates HTML contexts
at run time with the library's transition tables. Kotlin Temper
instead works the contexts out at compile time, and so does
tempest now.

**How Kotlin does it.** Not a general mechanism, such as folding a
tag's static parts: one hard-coded micro-pass in the frontend,
`OptimizeContextualAutoescapingBlocks.kt`, run after the function
macro stage's interpretation (`FunctionMacroStage`). Its doc calls it a
stand-in for a user-space macro in the library, once macros can do it.
The library only follows conventions its `ContextualAutoescapingAccumulator`
interface documents; it has no annotations or macros of its own. The pass:

1. finds `t.accumulated` reads where `t`'s type is a concrete class with
   a supertype *named* `ContextualAutoescapingAccumulator` (found by
   name, since it's not in std), declared once in the block;
2. walks the block's paths from `let t = …` to `.accumulated`,
   interpreting at compile time the class's statics `initialState()`,
   `propagator()`, `sameStatePredicate()`, `picker()`, the core module's
   `propagateOver(...)` for each fixed part (and with `null` before each
   value), `picker.escaperFor(state)`, `mergeStates(a, b)` where paths
   join (each branch on a deep copy), `checkEndState(state)` at the end,
   and `foldAnalyses`/`exportAnalyses` per basic block, with each use
   capped at about ten million steps;
3. rewrites the block: `let collector = C.newCollector()`, each fixed
   part as the effects it gave (`collector.append("…")`, or
   `appendSafe` if the collector has it, and `C.enact(event,
   collector)` for an `EventParseEffect`, the event hoisted to a
   module-level temporary), each value as
   `collector.append(E2.instance.apply(E1.instance.apply(x)))`, a
   composed escaper's `first` and `second` unravelled, and
   `C.fromCollector(collector)` for the read;
4. logs what the library reports through the `callout` it's given as
   `content tag reported:…`: errors and warnings, infos in a test's body;
   and writes `exportAnalyses`' data files with `dataFile`.

Anything that doesn't fit (a failed interpretation, a value it can't
rebuild as code) leaves the block as written. In practice, on a real
library with HTML and SQL tags, Kotlin optimizes most of the HTML
uses, leaving a `for … of` template (a closure, which tempest
follows as a loop), and none of the SQL ones; why it gives up on
those isn't confirmed.

**In tempest** (`crates/temper-frontend/src/autoescape.rs`), the same
pass runs at the end of `settle`, once typing is done and the modules
are ordered, so the interpreter, tmpl, and every backend see the
rewritten code. It folds only what it can prove gives what run time
does; anything else is left as written, which propagates contexts at
run time, path by path, and is always safe:

- **Interpreter:** `interp::compile_time::Session` runs the modules'
  top-level code once, through the latest module a use needs (the
  class's and the interface's), with what they log dropped, then lets
  the pass call functions, statics, and getters on the values made,
  each use with ten million steps. After a call fails or runs out of
  steps, the session starts again (`Session::renew`), so half-updated
  module state can't reach the next use: its module state is dropped
  first, then the top levels run again, keeping the code lowered so
  far, which comes from the tree alone. So a failure costs only the
  top-level runs, and never holds two sessions at once. Only what the allowlist below permits
  runs. A `Val::Callout` collects what the library reports. Values
  come back as owned data (`Construction`): constants,
  `String.begin`, lists, and `new C(…)` of classes without their own
  constructor.
- **Walk:** structured, over the tree, as abstract interpretation,
  rather than over paths or a control-flow graph: blocks in order, an
  `if` as two branches on deep copies, a loop once (below). Where
  paths join (after an `if`, at a labeled block's end, after a loop),
  they fold only when `sameStatePredicate` holds for each state
  arriving and the first; `mergeStates` still runs, for what it
  reports. (A library's `mergeStates` may keep the first branch's
  state, as when only nested languages' states differ, which would
  give the second branch's values the wrong escaper.) Folding past a
  join or around a loop takes states the predicate calls the same to
  propagate alike, as Kotlin's does: so the convention needs
  `sameStatePredicate` to be exact equality on everything later
  propagation and the picker read (a library's HTML and SQL predicates
  compare every context field, and nested languages' states by
  identity). A looser predicate would fold with another state's
  escapers.
  - **Jumps:** a `break` or `continue` notes the state it carries, by
    its target and kind, and the walk joins them there: a labeled
    block's `break`s with what falls through its end, a loop's
    `continue`s with its body's end, and its `break`s as the state
    after it. A jump, `return`, `bubble()`, or `panic()` never
    completes, so brings nothing to the join after it. A jump inside an
    appended value, as `${if (c) { break } else { x }}`, carries the
    state from before the append, since the value is worked out first.
  - **Leaving the string:** a path that leaves the region between
    `let t` and `t.accumulated` (by `return`, `bubble()`, `panic()`, or
    a `break` or `continue` to a loop around the tagged string) is
    dropped rather than joined. That's safe: every read of the
    accumulator is an append's or the end's, and nothing else holds
    it, so no later append runs on that path, and its state reaches no
    escaper, while each append before it was walked with that path's
    own state. Kotlin's walk over paths doesn't join them either.
    A `panic()`, or a call of a function whose result is `Never<T>`,
    ends a path as a jump does; anything else that might not complete
    is taken to complete, which can only add a state to a join, so only
    makes folding less likely.
  - **Loops** (`while`, `for`, `do … while`, and `for … of` on a
    `List` or `String`, whose core `forEach` calls the body for each
    item in order before it returns): the body is walked once, on a
    copy of the state before the loop. If every state that comes back
    to its start (the body's end, each `continue`, and after them the
    part run after each pass, as `for`'s step or `do … while`'s check)
    is the same as that state by `sameStatePredicate`, each pass starts
    in the contexts the first did, so the body folds once, and the
    loop's `break`s, its check among them, join as the state after it.
    Otherwise the whole use is left to run time, with nothing
    reported; there's no unrolling. So a loop whose body opens a quote
    or a `<script>`, or a `continue` that skips closing one, is left to
    run time, and a `break` from inside a string brings a state unlike
    the loop's end to the join after it, reported as a branch's would
    be.
  - Closures other than `for … of` bodies, `orelse`, an append in a
    condition, a fixed part that isn't a constant, or any other read of
    the accumulator leave the use as written.
- **Escapers:** each picked escaper must be alike its class's static
  `instance` (that object, or one of the same class whose fields are
  alike), since the folded code calls `instance`; a composed one,
  with fields `first` and `second`, is taken to apply as
  `second.apply(first.apply(x))`, as the library's conventions say.
- **Rewrite and typing:** the new nodes are checked and typed like the
  rest (`Settle::type_new_nodes`), so `apply` and `append` resolve to
  their `@overload`s, and the innermost escaper's `apply` must take
  the type the append it replaces took. If anything doesn't hold, the
  rewrite is undone (old children, parents, name, and type back; the
  new nodes emptied and detached), so the pass never adds an error.
  The accumulator's temporary is renamed `collector`, as Kotlin's.
  Only the new nodes are typed, and only the modules with a use
  rewritten have their top levels sorted again, for what the new code
  reads.
- **Diagnostics:** `Message::ContentTag`, at Kotlin's positions, at
  the level the library gives, hints in a test's body (Kotlin's
  infos). A use the library reports an error for isn't folded: the
  error is an ordinary error, and the code propagates at run time.
- **Speed:** a library may rebuild and compile every regex literal on
  each call of its propagation functions; the interpreter now compiles
  a regex literal without interpolations once and keeps it
  (`Library::constant_regexes`), which took one real library's uses
  from seconds to a fraction of a second in a release build.
- **On a real library** with HTML and SQL tags, all but a few uses
  fold, the SQL ones and a `for … of` template included; uses in
  corner-case tests the library reports errors for are left to run
  time. Kotlin folds most of the HTML uses and none of the SQL ones;
  why it skips those isn't confirmed (its member lookup does see
  supertypes' `@overload`s).

### What runs at compile time

The content-tag session is the only place tempest runs the
interpreter before run time (constant folding and macros are Rust
code), and it runs only what an allowlist in one place,
`interp::compile_time`, permits; anything else is refused as "not
yet", so the use it was for is left to run time and nothing outside
the interpreter happens. Effects (network, clock, randomness, I/O) are
only for `run` and `test` on the interpreter and for backends.

- Allowed: a library's own Temper code; core (builtin types and the
  builtin functions `builtin_allowed` names one by one, not `async`);
  of std, the pure modules `regex`, `json`, `temporal`, and
  `collections` (`ALLOWED_STD_MODULES`). Console output is dropped.
- Denied: `temporal`'s `Date.today()` (`DENIED_MEMBERS`); every other
  std module, as `net` and `testing`, and any added later; every
  `@connected` function or class outside the allowed std modules, a
  library's own included; native jobs (requests are queued as such,
  and never run), and `async` blocks.

A new connected function or std module is refused until it's added
on purpose; `compile_time_allowlist` in `tests/settle.rs` prints the
lists. Kotlin guards differently: builtins carry an `isPure` flag, and
`Date.today` gives `NotYet` before the run stage; its interpreter has
no implementation of std `net`'s connected functions at all (only its
backends' support code does), which is why it never sends a request
at compile time.

**Not done yet:** `exportAnalyses`' data files, since tempest has no
`dataFile` or `data` backend (`foldAnalyses` still runs, for its
diagnostics); events are built where they're used rather than hoisted
to module-level temporaries; a branch or loop inside a nested
language (`<script>`, `<style>`, URL or CSS attribute values)
doesn't fold until the library compares delegates by value. The
general mechanism Kotlin's doc points to, a user-space macro in the
library, needs macros written in Temper, which neither compiler has
yet.
