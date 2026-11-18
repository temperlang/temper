# Design

Part of the [rewrite plan](README.md).

## 3. The tree and its memory layout

Kotlin Temper allocates at least four objects per tree child (the node, an
edge object, a copied position, a value or name), plus back-pointers, hash
maps keyed by nodes, and two type representations. The new tree
(`temper-syntax`'s `tree`) uses a few big arrays instead:

```rust
pub struct Tree {
    kinds: Vec<NodeKind>,   // a small enum per node
    spans: Vec<Span>,       // u32 start, u32 end into the source
    first_child: Vec<u32>,  // children are a contiguous range in `children`
    child_count: Vec<u32>,
    data: Vec<u32>,         // small payload: an index into a side table
    children: Vec<NodeId>,  // NodeId is a u32
}
```

- **Ids, not pointers.** Nodes, symbols, types and literals are `u32` ids
  into arrays. No reference counting, no back-pointers, no cycles to manage.
- **Interning.** Names and string literals are interned once per build, so
  comparing names is comparing integers. Types are interned too, so equal
  types have equal ids.
- **Rewrites append.** A macro expansion builds its replacement nodes at the
  end of the arrays and points the parent at them. The old nodes simply become
  unreachable; they're freed with the arena. No per-node frees.
- **One arena per library build.** Dropping a build frees everything at once,
  so there's no way to leak earlier builds the way Kotlin's watch mode did.
- **Side tables** hold per-node facts that only some nodes need (values,
  names, origins, resolved bindings, types, diagnostics), as `Vec`s or
  maps keyed by `NodeId`, rather than fields on every node; types live in
  typing's tables, not in the tree.

Why this is fast: walking children reads contiguous memory, most data fits in
cache, and building a tree costs a handful of `Vec` growths instead of
hundreds of thousands of small allocations. Chromarite's histogram showed ~80k
edge objects alone for one build of ~900 lines.

## 4. Parsing

`temper-syntax` goes from source to a tree in five steps, each checked
against Kotlin Temper (see [testing-and-milestones.md](testing-and-milestones.md)):

- **Literate split** (`literate`): code blocks and paragraphs in
  `.temper.md` files, as commonmark finds them. Code in block quotes is prose
  here, unlike in Kotlin.
- **Lexer** (`lex`): hand-written, tokens of a kind, a byte span and flags;
  their text is the source in the span, or for synthetic tokens (inserted
  closers, paragraph doc comments) fixed by their kind. It keeps comments and
  prose, and knows no keywords beyond Kotlin's: `return`, `throw` and
  `yield` may precede a regex literal.
- **Token adapters** (`adapt`), as Kotlin's `TokenSourceAdapter`: group
  comments; wrap strings in synthetic `( )`, or `{ }` for multi-quoted
  strings with `:` scriptlet lines, whose parts become statements; recover
  from brackets that don't balance (`balance`, ours: close an unclosed `(`
  or `[` at the nearest plausible place, drop an unmatched closer, and
  treat a `<` left open as less-than, each an error, so one bad bracket
  stays local; balanced tokens are left alone); drop space and comments, inserting `;` after a `}` that ends a line and before
  a `{` that starts one; put `callJoin:` between `}` and a following word;
  and turn modifier words into decorators (`public x` is `@public x`,
  `var x` is `@var let x`).
- **Parser** (`parse`): one table-driven operator-precedence parser
  (`operator` has the table), a port of Kotlin's operator stack and its
  `canNest` rules. Temper's flexibility comes from treating almost
  everything as an operator: calls and blocks are infix brackets, and the
  call join gives `if (a) { } else { }`, `foo(x) { }` and `a.b { } c { }` with
  no special cases per keyword. Keywords like `if` are plain names. Words are
  interned (`names`). Operators with too few or too many operands stay in the
  tree, each with an error, so the rest of the file still parses.

The tree (`tree`, section 3's layout) has operator nodes whose children are
operands and the operator's own tokens, like `(Plus 1 + 2)`; a `Leaf` node
holds a run of value tokens, one for `1` or several for `let x`. Every token
the parser sees is in the tree once, in order.

Multi-quoted strings follow rules of our own where Kotlin's handling was
flaky: blocks opened on scriptlet lines carry over to later ones in order
and close when the string ends; a scriptlet line stays code after a `${...}`
in it; each content part is its own statement. The full list is in
kotlin-differences.md, "Lexer and parser", and examples in
`tests/parse/multi-quoted.cases`.

The **grammar step** (`ast`, with the productions in `grammar`) turns the
operator tree into the tree the settle loop works on, as Kotlin's
`astbuild` does:

- **Few node kinds:** `Call` (callee, then arguments), `Block`, `Decl`,
  `Fun`, `Esc` (a quoted tree), names (`LeftName` declared, `RightName`
  used, `BuiltinName` never shadowed), `Value` (numbers, strings, symbols
  like `\word` naming the next argument, builtins like `desugarOperation`),
  and `Error`. Values and names are side tables; each node has an origin
  link to the parse node it came from.
- **No keywords.** `if (a) { b } else { c }` is a call of `if` with `a`, a
  `Fun` for the block, then `\callJoin:`, `\else` and the next `Fun`;
  `let x: T = 1` is a `Decl` with `\type` and `\init` pairs; `class C
  extends A {}` is a call of `class` with `\word`, `C`, `\super`, `A` and a
  `Fun`; `a + b` is `desugarOperation(+, a, b)`; `@A let x, y` is a call of
  `@` around a comma of declarations; `"a${b}"` is a call of `stringExpr`.
  Macros give these meaning later.
- **A port of Kotlin's grammar.** The parse tree is flattened into open,
  token and close parts, and productions written as methods (each with
  Kotlin's definition in its doc comment) match them PEG-style, emitting
  start, leaf, finish and swap events; a failed alternative drops its
  events. A lift step then builds the tree from the events, reading names
  and values from tokens and reporting malformed numbers. Matching Kotlin
  production by production keeps its exact structure and error messages
  (`Expected a TopLevel here` and the like), checked against Kotlin on all
  inputs (`compat/check_ast.py`).
- **Errors are values:** unparsable parts become an `Error` node with a
  diagnostic, and the rest still builds. Operands nested more than 1000
  deep become errors too, where Kotlin runs out of stack (at about 300
  nested calls), so nothing overflows ours, including in wasm.
- **Fast where Kotlin backtracks badly.** Kotlin's grammar takes exponential
  time on some nesting (`((((x))))`, `a[a[a[x]]]`, `@a (@a (x))`), because
  an alternative parses a whole operand before failing on something after
  it. Ours checks that cheaply first, with the same result.
- **Finishing a module** (`module::build_module`), as the rest of Kotlin's
  `ParseStage`: the code's tree is wrapped in a block if it isn't one; the
  `;;;` metadata, built with Kotlin's `Json` production, becomes a `Json`
  value, with `InvalidMetadata` errors (and null) where it isn't JSON, such
  as `1i64`; and call joins are rewritten. For a library, what follows each
  `\callJoin:` and its symbol becomes a function passing it all to its
  argument, so `if (a) { b } else { c }` is
  `if(a, fn { b }, \else, fn (f#0) { f#0(fn { c }) })`, where `f#0` is a
  temporary name (never interned, so it can't clash with a source name);
  for documentation, the `\callJoin:` is just dropped.

### The parse tree and the working copy

There's one node type, in two copies:

- **The parse result** (`Parsed`) is shaped like the source and never
  changed after parsing. It serves formatting, outlines, docs and finding
  nodes by position for the language server. It keeps every lexer token, so
  comments and blank lines are found between node spans rather than stored
  in the tree, and synthetic tokens (inserted semicolons, call joins, string
  parentheses, closers) are flagged so a formatter can skip them.
- **The settle loop works on a copy** (`ast::Ast`, from the grammar step),
  without what only formatting and the language server need. Its nodes keep
  an origin link to the parse node they came from, for error spans, hover
  and go-to-definition.

There's no separate lossless syntax tree type.

## 5. The settle loop

This replaces Kotlin Temper's stages (lex, parse, import, disambiguate,
syntax macro, define, type, function macro, export, generate) with one loop.

### Work items

Each piece of work that can't finish yet is a work item waiting on at
most one fact (`engine`, a scheduler generic over the fact type). Today's
facts (`settle::Fact`): a scope's declarations are complete, a class's
scope is complete with what it inherits, a declaration's type is known,
and an object literal's class is decided. Compile-time values and other
libraries' exports would be more of the same.

The loop keeps a ready queue. When a fact is published, the items
waiting on exactly that fact move to the ready queue. Nothing else is
revisited, which is the key difference from passes over whole modules.

### Steps

Processing a ready item tries one step:

- **Resolve a name** to its declaration, or wait on the symbol.
- **Expand a macro call:** once the callee resolves to a macro, and the macro
  says which of its inputs it needs (some need only syntax, some need types or
  values), run it. It rewrites its own call node; new nodes get their own work
  items.
- **Evaluate at compile time:** constants, macro bodies written in Temper, and
  `const`-like lets, with a small interpreter over the tree.
- **Infer a type:** each expression produces constraints when its inputs are
  known. Small cases resolve immediately; generic calls are solved per call
  site once their arguments' types are known.
- **Inline** where Temper's semantics call for it (known function values,
  small helpers), when the callee has settled.

### Termination and order

- Facts are mostly **monotonic**: a binding, once found, doesn't change, and a
  type, once fixed, doesn't change. Macro expansions only replace their own
  call node. That makes termination easy to reason about.
- A **step budget** per build catches runaway macro expansion. Hitting it is
  an error that names the cycle.
- The queue is processed in **source order** within each priority, so output
  and error messages are deterministic.
- When the ready queue empties with items still waiting, the earliest
  item whose wait can't end is **forced** to go on without its fact,
  usually by making an error node (section 6), which may let others go
  on (an error type is still a type). When nothing waits, the loop is
  settled.

### Risks to design for

- **Facts only get added.** Termination and determinism depend on it. Macros
  that need types (Kotlin's late "function macros") are the risk: they may
  rewrite only their own call node, and anything that read a fact that could
  still change must wait until they've run. Otherwise facts get retracted
  and the loop can oscillate.
- **Macros must be able to wait for types.** Kotlin runs a function macro
  stage after typing (`frontend/function/FunctionMacroStage.kt`; contextual
  autoescaping of string tags runs there), meant for macros that need
  types. Tempest's first is the object literal ([language.md](language.md),
  "Object literals, not named arguments"), but core may need more, and
  user-space macros may get them someday. So the settle loop should let a macro call
  ask for a type, such as an argument's or its expected type, as a fact it
  waits on like any other: queued until typing provides it, then expanded,
  and its expansion typed in turn. Don't let typing become a final pass
  that nothing can run after. A macro that asks for a type its own
  expansion would decide is a cycle, reported like the others.
- **Cycles get a deterministic tie-break,** not only an error: for example a
  local recursive function whose type depends on itself breaks at the
  earliest item in source order, so output stays stable.
- **Debugging:** a fixed point can't be dumped between stages the way a
  pipeline can. `Engine::chain` answers "why is this item still waiting,
  and on what chain?", for errors and debugging.

### As built so far

`crates/temper-frontend` has the first slice (details and differences in
[milestone-3.md](history/milestone-3.md)): a generic scheduler (`engine`) and
kinds of work: reading a block's statements in order to find its
declarations, visiting an expression to resolve names and expand macros,
completing a class's scope with what it inherits, and inferring a
declaration's type, in a second round once expansion has settled
([milestone-4.md](history/milestone-4.md)). The object literal is the
first macro that waits for types: it waits, in typing's round, for the
type its place wants (typing.md, "Object literals and target typing").

Not built yet: the **Evaluate at compile time** and **Inline** steps
above ([compile-time.md](compile-time.md) has the plan). Built-in macros
are Rust, and the interpreter (`interp`) runs settled libraries only at
run time, for tests and the playground.

### Imports across libraries

Each library builds in its own arena. A library exports a compact summary
(names, types, macro definitions it exports) once its exported items settle.
Importers wait on that summary, not on the whole tree. Independent libraries
can build in parallel.

### Example

```temper
let x = f(1);
let f(n: Int): Int { n + 1 }
```

1. `let x` is a macro call; it declares `x` with an unknown type and queues its
   initializer. `let f` declares `f`.
2. `f(1)` resolves `f` (declared in step 1) and waits on `f`'s signature,
   which settles when `let f` processes its annotations.
3. The call's type becomes `Int`, and so does `x`'s. Nothing is waiting, so
   the loop ends. No stage ordering was needed for the forward reference.

### Declaration facts in one place

Kotlin Temper spreads facts about a declaration across stages and shapes:
`@connected` arrives as a decorator that applies after a class body is
read, so the class macro has to look for unapplied decorations to learn
that its bodiless methods are connected rather than pure virtual
(`connected-types` branch, 2026-10-23). Here, a declaration's modifiers
(connected, static, exported, abstract or concrete, visibility) should be
read once, before its members are, into one record on the declaration,
and members should see their owner's record through the context they're
settled in. Then "a bodiless method of a connected class is connected"
is one rule in one place, and backends ask one question ("is this
connected?") to skip a host-provided type and all its members.

Kotlin Temper has to guess at that rule early: the class macro runs
before `@connected` applies (the decoration wraps the class's result), so
it looks for the decoration by value around its own call. Reading
modifiers first avoids that chicken-and-egg ordering entirely.

The language's rules for connected declarations, such as no default
arguments on a connected class's methods, are checks in typing
(`crates/temper-frontend/src/typing/connected.rs`).

Longer term (user, 2026-10-23): std should use the same user-space connected
types as everyone, with only core keeping custom code generation. One
catch: std sometimes exposes per-backend native types unwrapped (dates,
for example), while user-space connected types wrap them. Per-backend code
or decorators in Temper source stay avoided, though an opt-in for people
who need them could come someday.

Core and std sources are in tempest's own tree (`lib/core/` and
`lib/std/`), built into the single binary: the binaries and the wasm
build embed `lib/std/` (temper-frontend's `stdlib`), and
`--std DIR` overrides it for testing against another copy. The frontend
reads every file through one `Sources` value (temper-frontend's
`sources`): layers mounted at path prefixes, from the disk, the built-in
std at `-std-/`, or memory, with earlier layers winning at the same
prefix. Std might be
split out someday, but for now it ships with the compiler.

## 6. Errors

- Every diagnostic has a span and message, and is collected, never thrown.
- An unresolvable node becomes an **error node**. Its type is unknown,
  which never causes an error itself, so surrounding code keeps checking
  without a cascade of follow-on errors.
- Backends translate an error node into a runtime failure carrying the
  message (`throw`, `panic!`, `raise`, …). The rest of the library still
  works, matching Temper's philosophy.

## 7. tmpl, the backend IR

A small, explicitly specified, fully settled IR:

- Modules with imports and exports; type declarations (classes, interfaces)
  with members; functions; statements (`let`, assignment, `if`, `while`,
  `return`, `throw`, `try`, labeled `break`/`continue`); expressions (calls,
  operators, member access, literals, lambdas, casts, null checks).
- Types are tmpl's own: nominal types with arguments, function types,
  nullable types, and "may fail" (Temper's bubble), never frontend objects.
- Names are tmpl's own (section 8).
- It depends on nothing in the frontend, so backends build and test against
  tmpl alone, and a tmpl dump is a readable debugging format.

Shared lowerings run on tmpl before the backends, chosen per backend:

- **Failure handling:** exceptions, result values, or error codes.
- **Coroutines:** native async/generators, or state machines.
- **Control flow:** labeled breaks for languages without them, expressions
  that must become statements, and so on.

This keeps that logic out of every individual backend.

Two language decisions shape what tmpl carries: fun interfaces as
named function types, and object literals in place of named
arguments. They're in [language.md](language.md), with the
language's other rules in [typing.md](typing.md).

## 8. Naming

People reading generated code dislike the numbered names (`values__139`,
`_fn_820`, `space_138`). The goal is generated code, including function
bodies, that reads like a person wrote it: **no numbers anywhere except to
resolve a real conflict**.

### Rules

1. **Public names** are source names in the language's convention
   (camelCase, snake_case, PascalCase), escaped for reserved words. Never
   numbered.
2. **Locals, parameters and private members** use their source names. Only an
   actual conflict in the same scope, as the target language sees it, adds a
   suffix, and then the shortest one that works: `x`, then `x2`, `x3`.
   Shadowing that the target language allows is not a conflict.
3. **Compiler-made names** (temporaries, hoisted lambdas, loop labels) are
   named for what they hold, not numbered: the variable they come from
   (`items` for a copy of `items`), the call they hold (`parsed` for
   `parse(...)`), or a short role (`result`, `index`, `done`). Same collision
   rule.
4. **Fewer temporaries in the first place.** Lowerings keep expressions as
   expressions wherever the target language allows, and introduce a temporary
   only when evaluation order or a statement-only construct requires it.

### Infrastructure, so backends get this for free

Naming happens in one shared tmpl pass, not in each backend:

- **Every name in tmpl is a `NameRequest`**: a base name (from source, or a
  role hint for compiler-made names), a kind (local, parameter, field, method,
  type, module-level), and a visibility. Backends never invent name strings.
- **tmpl knows its scopes**: function, block, class body, module, plus the
  target language's extra scoping rules (Python's class bodies don't enclose
  methods; Lua and JS `var` hoisting; C# disallowing a local that shadows an
  outer local). Each backend describes these as data, not code.
- **One resolver** walks the scopes, claims public names first, then source
  names in source order, then compiler-made names, and adds suffixes only on
  conflict. Same input, same names, so output is stable across builds.
- **Each backend supplies a small `NamingRules`**: reserved words, case
  conventions per kind, character escaping, and its scoping quirks. That's
  typically a few dozen lines.
- **Backends print resolved names** with a lookup. A backend that needs a new
  helper variable asks for a `NameRequest` with a role hint and gets a pretty,
  conflict-free name.

C++'s `CppNames.kt` already does "bare name first, suffix only on conflict"
for some names; this generalizes it to everything, everywhere.

## 9. Backends (translators)

Kotlin Temper gives each backend a generated per-language AST (~42k
generated lines across seven backends) plus a translator, a support
network, and post-passes. Those ASTs earn their keep with **correct output
structure** (precedence, statements versus expressions, blocks, valid
syntax) and **source maps** back to Temper source. Tempest keeps both
with less:

- **A small tree per backend,** hand-written (`tree.rs` in each
  `temper-be-*` crate, about 900 to 1,900 lines, printer included),
  rather than a shared output tree: the layouts share too little
  (rustfmt's widths, PEP 8's blank lines, Lua's `end`s). Each printer adds
  parentheses only where its language's precedence needs them.
- **Source maps** come from tmpl's spans through `temper_tmpl::source_map`
  (a printer marks spans as it writes); only JS writes them so far.
- **Shared lowerings** run on tmpl first, chosen by the backend's profile
  (section 7), and the shared naming pass names everything (section 8).
- **The runtime support libraries** (`temper-core` for each language) are
  Kotlin Temper's, with a few changes, in each backend crate's
  `runtime/`, which keeps runtime APIs identical.
- **Idiomatic output:** real loops and conditionals, source names, no
  needless temporaries.

So a backend is a `temper_tmpl::Backend` description (its name, runtime,
naming rules, and `translate`), its profile and lowering choices, a table
of how builtins and connected members map to its runtime, and a
translator from tmpl to its tree. Backends are pluggable: the frontend
and tmpl never list them; `temper-driver`'s registry holds the one
list.

## 11. CLI, watch and language server

- **CLI:** the same commands (`build`, `test`, `run`, `watch`, `repl`, `doc`,
  `init`, `serve`), flags and output formats, so scripts keep working.
- **Watch:** maybe not needed. If a full library build takes tens of
  milliseconds, rebuilding from scratch on each run is simpler than keeping a
  process alive, and `watch` can stay a thin loop that reruns `build` or
  `test`. If it's needed, a file-system watcher rebuilds the changed library
  and libraries that import it, each in a fresh arena, keeping unchanged
  libraries' exported summaries, so memory stays flat by construction.
  Decide once the full compiler is measured. Either way, unsaved editor
  buffers can be a memory layer of `Sources` over the disk, so builds
  see them without writing files.
- **Language server:** built on the same tree and side tables. Completions,
  hover and go-to-definition come from bindings and types already computed by
  the settle loop.
