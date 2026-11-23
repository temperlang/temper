# Temper in Rust: rewrite plan

> [!NOTE]
> These are working design notes: plans, proposals, and the reasoning
> behind them, written as tempest was built. Some are out of date. The
> [docs](../docs/README.md) are authoritative; where they and these
> notes disagree, the docs win.

Tempest, a skunkworks reimplementation of Temper. It keeps
Temper's language, philosophy, functional-test behavior, and generated public
APIs, with a new implementation that is much simpler, smaller, and faster. Kotlin
Temper stays the product meanwhile; core fixes keep landing there.

For how tempest works today, start with [docs/internals/](../docs/internals/README.md).
This folder holds the plan in parts:

- [design.md](design.md): the tree, parsing, the settle loop, errors, tmpl,
  naming, backends, and CLI/watch/LSP.
- [api-compat.md](api-compat.md): the public API each backend must keep, what
  may change, and proposed deliberate changes.
- [size-and-speed.md](size-and-speed.md): code size today vs. target,
  speed and memory targets, and what the measurements in
  [size-and-speed.csv](size-and-speed.csv) show.
- [output-speed.md](output-speed.md): making generated code faster,
  compatibly first, then with small additions and design changes.
- [compile-time.md](compile-time.md): macros, constants and inlining:
  on-demand evaluation, a cheap interpreter, budgets and caching.
- [language.md](language.md): language decisions the design depends on:
  fun interfaces as named function types, and object literals in place
  of named arguments.
- [typing.md](typing.md): type inference, and flow facts (narrowing, value
  ranges) from structured passes without a control-flow graph.
- [testing-and-milestones.md](testing-and-milestones.md): how compatibility is
  checked, including semantics and error messages; milestones; risks.
- [history/](history/README.md): finished plans and status logs.
  - [milestone-3.md](history/milestone-3.md): the settle loop: core
    constructs, tree layout, names, and waiting and waking; and its
    status.
  - [milestone-4.md](history/milestone-4.md): types in the settle loop:
    its slices, and their status.
- [acyclic.md](acyclic.md): ruling out reference cycles at compile time,
  despite mutability (a design sketch).
- [actors.md](actors.md): actors and isolation across backends, for
  libraries the host app drives (a brainstorm).
- [concurrency.md](concurrency.md): a recommended layering for
  concurrency and parallelism, for client and server libraries (not
  decided).
- [code-style.md](code-style.md): ownership without lifetime annotations,
  structure, and the dependency policy.
- [library-cache.md](library-cache.md): a stable cache of frontend results
  (a preliminary assessment).
- [test-speed.md](test-speed.md): fast `tempest test` on every backend,
  with a fast path kept in agreement with the package path (a proposal).
- [tmpl/](tmpl/README.md): tmpl, the backend IR: its crate, nodes,
  types, profiles and lowering, formats, and targets, as a layered
  design with one file per topic.
- [repl.md](repl.md): the REPL: Kotlin's `temper repl` studied, and
  tempest's design and plan, by chunks.
- [online-repl.md](online-repl.md): the REPL in the browser, beside
  the playground: design, decisions, and plan, by chunks.
- [fmt.md](fmt.md): `temper fmt`: a census of today's layout, the
  rules, the design, and next steps.
- [kotlin-differences.md](kotlin-differences.md): where tempest differs
  from Kotlin Temper on purpose, and Kotlin Temper's known bugs.

## 1. Goals and non-goals

Goals:

- **Same language.** Every functional test passes on every backend, and
  Temper source written today keeps working, including the flexible grammar
  where chained blocks become macro or function calls.
- **Same public APIs** in generated libraries, so library users can't tell the
  difference. A *few* deliberate changes are fine where there's good reason,
  since there are few users today. Each one gets written down with its reason.
- **Much simpler.** Fewer concepts and representations, plainly written code.
  Smaller should follow from simpler, not from being terse.
- **Fast and lean.** Far fewer allocations, good data locality, a small single
  binary, little memory.
- **Same philosophy:** report errors and keep going, all the way through code
  generation.

Non-goals:

- Matching Kotlin Temper's internal representations, stages, intermediate
  forms, or private generated details. Kotlin's frontend tests are useful only
  as hints about semantics.
- Matching non-exported names, private members or function bodies in
  generated code. These should get *prettier*, for example without numbered
  names like `x__123`.

## 2. How it works, end to end

[docs/internals/](../docs/internals/README.md) walks through it, stage
by stage. In short: source becomes tokens and a tree; the settle loop
resolves names, expands macros, and infers types on that tree until
nothing changes; the settled library either runs on the interpreter or
becomes tmpl, the backend IR, which shared lowerings and naming prepare
for each backend to print. Tokens, the tree, and tmpl are the only
three data structures a reader needs to learn.
