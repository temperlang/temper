# tmpl: modules and initialization

Part of the [tmpl design](README.md).

## Level 1: Module initialization: order and threads

**Decided (robustness):** tempest's backends should be more robust than
Kotlin's where they can.

- **Decided (2026-10-29): module initialization runs in source order
  now,** fixing Kotlin's bug (types sorted before values, so C++ reads
  zero and Python raises `NameError`). How top-level declarations are
  sorted is left to the team; tmpl takes the frontend's order.
- **A profile picks the init strategy** (Proposed): eager on import,
  on first use behind a once-guard (api-compat.md proposal 2), or class
  initialization.
- **Module-level `var`s are marked,** with reads and writes as their own
  nodes, so a pass can make them thread-safe where a target has threads,
  fixing the race Kotlin's C++ has.

Level 2, below, has the init list's structure.

## Level 2: Modules: imports, exports, and the init list

A `Module` has its path within the library, its imports, its
declarations in source order, its init list, and its tests.

- **Imports:** each names a module of this library or an external
  library's module, and the declarations used from it. Temper rejects
  import cycles, so modules form an acyclic graph, and `Program`
  lists them in dependency order. Unused imports are dropped by the
  frontend.
- **Exports** are the declarations flagged exported; the module's
  export list is derived, in source order.
- **The init list** (Decided 2026-10-29: source order) holds, in the
  order the frontend gives: each module `let`'s initializer as an
  `InitLet`, each top-level statement, and each class's static
  initializers where the class appears. Type and function declarations
  aren't in it. An initializer that reads a module value initialized
  later is an error; through a call, a warning ([question 19](open-questions.md)).
- **Tests** are a separate list of `Test` declarations, run after
  init.

Init strategy is a profile choice ([profiles.md](profiles.md)). The first-use pass adds
`EnsureInit` where an exported function, method, or getter may reach
the module's state, by the reaches-module-state analysis; a module
with no state has no guard.

## Level 3: Modules for subset A

Proposed.

```rust
pub struct Module {
    pub decl: DeclId,          // named by its path in the library
    pub items: Vec<NodeId>,    // Function and ModuleLet nodes, in source order
    pub init: NodeId,          // a Block: InitLets and top-level statements
    // Subset B: imports and tests.
}
```

- **The init block** holds, in source order, an `InitLet` for each
  module `let` with an initializer, and each top-level statement. A
  module `var` without an initializer is assigned later, and
  definite assignment (R5) checks its reads.
- **A module `let` read or written in a function** is a
  `GetModuleLet` or `SetModuleLet`; reads in the init block are too.
- **Python's first variant initializes eagerly,** so the init block is
  the module's body, after its functions; no pass runs. A function
  that writes a module `var` gets `global`, from the module-state
  analysis.
- **Subset A has one module per test** in practice; imports between
  modules come with subset B.

## Level 3: Modules, imports, and tests, as built (2026-10-30)

Proposed, built with subset B's first step.

```rust
pub struct Module {
    pub decl: DeclId,       // a Module declaration, with no owner
    pub path: String,       // from the library's root: "", "helpers", "a/b"
    pub items: Vec<NodeId>,
    pub init: NodeId,
    pub test_items: Vec<NodeId>,   // what only tests reach, once partitioned
    pub test_init: Option<NodeId>, // a Block, which continues init
    pub tests: Vec<NodeId>, // Test nodes, in source order
}
```

- **Modules** are `Program::modules` in the order they run, each after
  those it imports, as the frontend settles them. The root module is
  named for the library, others by their paths.
- **Imports aren't stored.** A reference to another module's function
  or module `let` is the reference itself (`CallFunction`,
  `GetModuleLet`), and what a module imports is derived
  (`analysis::imports`, `module_imports`, `test_imports`): the
  top-level declarations of other modules its nodes call or read, or
  that its declared types name, grouped by module, the program's own
  first. An unused import is dropped this way, as Level 2 says. The
  frontend's `let { f } = import("./sub")` is a module-level `let f =
  f'`, which `to_tmpl` follows to `f'`.
- **An imported `var` is copied when the import runs,** as tempest's
  and Kotlin's interpreters and Kotlin's Python backend read it: its
  import becomes a module `let` of the importer's, initialized from the
  other module's `var` at the import's place in the init block, so later
  writes don't show there. Backends read another module's `var` as it is
  when read (Python through the module, `alias.calls`), so the copy is
  exact. Kotlin's backends disagree with each other here
  ([kotlin-differences.md](../kotlin-differences.md)).
- **Other libraries' modules** are `Module` declarations owned by their
  `uses` library, named by their paths there (empty for the root), and
  own what's used of them: functions, module `let`s and `var`s, classes,
  and interfaces, as external declarations. `uses std 0.6.0 { module
  testing { class Test {} } }` is how a test reaches std's `Test`.
  A class or interface comes with its type parameters and supertypes,
  and only the members used (2026-11-03): fields, methods, getters,
  setters, statics, and its constructor (its own, or the one its header
  implies), each declared the first time code uses it, `@connected`
  ones marked connected, with their parameters' names; a template's
  `toString()` declares the one it calls.
- **One library per program** ([open-questions.md](open-questions.md),
  47): each library is its own tmpl program, as each becomes its own
  package; its dependencies come in only as interfaces: the `uses`
  sections' declarations, and for each backend, the names the
  dependency's own naming gave them (`Program::interfaces`, set by the
  build; [names.md](names.md), "With modules").
- **std as a library** (2026-11-03): std's modules build as a program
  of their own, the library `std` at its config's version
  (`temper_frontend::stdlib::program`, which `stdlib::MODULES` lists:
  `testing`, `regex`, `json`, and `temporal`, whose `Date` is a
  connected class that some backends connect to a type of their own,
  `qname::connected_type`; `net` is left; `stdlib::program_of` takes
  fewer, which a backend's tests used until it translated all of std,
  as every one does since 2026-11-04). Its naming
  gives its interface, which builds, the testkit, and the wasm build
  hand to programs that use std, as any library's (open question 46);
  no name of std's is derived again. Its `@connected` members keep
  their bodies; each backend connects those its own table has
  ([open-questions.md](open-questions.md), 48): a bodiless connected
  method of a class is an `AbstractMethod`, and a static one a
  `BodilessFunction` (rule C1 allows both in a class when connected),
  and a backend leaves a member it connects out of the class
  (`qname::connected_member_node`), calling the connection instead. Its
  output isn't written by `tempest build` yet.
- **Rule M1:** a module refers to another of the program's modules only
  for its exported top-level declarations, of a module before it, and
  assigns or initializes only its own module `let`s.
- **Test parts** (2026-11-18): a module partitioned by reach
  (`reach::partition`, [analyses.md](analyses.md), "Reachability")
  keeps the library's parts in `items` and `init`, and has what only
  tests reach in `test_items` (in source order) and `test_init` (a
  `Block` of the init statements that set it, `None` where there are
  none), which runs after the init, continuing it in its scope, and
  before the tests. Before partitioning they're empty. What a module
  imports for its library (`module_imports`) comes from `items` and
  `init` alone; what its tests import (`test_imports`) from its test
  parts and tests. `Module::roots` lists every root in writing order,
  and `Module::all_items` both parts' items. **Rule M3:** the library's
  parts use nothing of the tests', which a build without tests leaves
  out. A backend that writes the test parts in a file of their own
  (Python's, JS's, and Lua's tests files) asks `analysis::test_parts`
  what they define, the file's own, and what they use of the library's
  parts, which it imports or exposes to them.
- **Tests:** each a `Test` node, a function (a compiler-made
  `Function` declaration owned by the module) taking std's `Test` and
  returning `Void`, which may bubble (rule M2), named in reports by its
  `name`, a constant string. Backends run each after the module's init
  with a fresh `Test`, and fail it if it bubbles or if the `Test` has
  failures when it ends (Kotlin's `softFailToHard`). The frontend's
  runner expression and its import of `runTestCases` aren't translated.
