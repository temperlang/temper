# tmpl: analyses

Part of the [tmpl design](README.md).

## Level 2: Analyses shared by passes

Each is a function from `&Program` to a side table keyed by node or
declaration id. Programs are immutable, so a result is valid for the
program it came from; stored facts are rechecked by recomputing them.

- **Captures:** which declarations each function literal captures,
  and which it or its owner assigns after capture. As built
  (2026-10-31): `analysis::captures` recomputes a literal's stored
  list (rule D5), and `analysis::assigned_captures` gives the captured
  locals any `Assign` writes, a superset of "assigned after capture"
  that's always safe for cells.
- **Defaults** (As built, 2026-11-01): `analysis::defaults` gives the
  `let`s a function's body starts with, one per optional parameter, each
  the parameter coalesced with its default (rule N2), which backends
  print in place or as a local, and which their surfaces read.
- **Purity:** whether an expression may have side effects or bubble,
  for evaluation-order temporaries.
- **Bubbling:** may-bubble calls and their handlers.
- **Call graph and reaches-module-state:** for init guards and init
  order.
- **Definite assignment:** for `Let`s without initializers (Java, Rust,
  and C# reject reads they can't prove assigned).
- **Mutability** ([mutability.md](mutability.md)).
- **Value ranges:** where `Int32` arithmetic can't overflow, for JS,
  Lua, and Python helpers.
- **Liveness and last use:** for letting locals with lifetimes that
  don't overlap share a name, and later for Rust's clones ([question
  3](open-questions.md)).
- **Naming hints** (`naming::hints`): candidate names for any
  expression a pass hoists or copies ([names.md](names.md)), so every pass and backend
  names what it adds well, and function bodies read as hand-written.
- **Reachability** (As built, 2026-11-17): `reach::reach` says which
  of a program's own top-level declarations its library needs, which
  only its tests do, and which nothing does, as Kotlin's
  `ReachabilityTracer` marks them (`@reach(\test)`, `@reach(\none)`),
  and `Reached::split` splits a module by it: the library's items and
  init statements, the tests' (what only tests reach, and the init
  statements that set it, to run before the tests), and what's left out.
  - **Units:** functions, module `let`s and `var`s, classes and
    interfaces (each whole, members and all, as Kotlin traces them), and
    the init block's top-level locals; each with its item and the init
    statements that set it, a static field's going with its class.
  - **Roots:** the library's are exports, `keep`s, and init statements
    that may do something: any that sets no unit, and any that sets one
    with a value that isn't simple (anything but literals, names,
    function values, and list literals, conversions, operators that
    can't bubble, and core's pure calls such as `getConsole()`, of
    those). The tests' are tests and `keepTest`s. Connected
    declarations aren't roots, as in Kotlin: host code uses only what's
    exported or kept.
  - **Tracing** follows mentions, not flow: what a unit's nodes call,
    refer to, read, set, or construct; the classes their types,
    expressions' types, and members used name; supertypes; and type
    parameters' bounds. The library's roots go first, so what both reach
    is the library's. `reach::reach_for` traces as a backend sees the
    program: the Temper bodies of the std classes' members it connects,
    which it doesn't write, reach nothing (std `Date`'s, which call
    `isLeapYear` and `padTo`); an interface's still do, as defaults that
    implementing classes may inherit.
  - **Partitioned** (2026-11-18): `reach::partition(p, connects)` gives
    the program split: each module's `items` and `init` keep the
    library's parts, its `test_items` and `test_init` take the tests'
    ([modules.md](modules.md)), and what nothing reaches is dropped, with
    every declaration it defines or owns; the class members the backend
    connects lose their bodies, as connected members without one are
    (rule C1), so nothing refers to what's dropped. Partitioning again
    changes nothing. The test init runs after the init, in its scope, where the
    tests are; its statements are all simple (any other is a root), so
    moving them after the library's changes nothing the library sees.
    The validator's M3 checks that the library's parts use nothing of
    the tests'.
  - **A backend partitions the program it translates,** after its
    lowerings: errors' lowering may drop a call (C++'s `errors` program
    no longer calls `passedOn`, whose argument's type is an error).
    C++ does (2026-11-18): what only tests reach is defined, and its
    classes and storage declared, only where `TEMPER_TESTS` is, the
    test init at the end of the init function there, and what nothing
    reaches isn't written; this replaced its own liveness, which
    covered only private functions. Rust does (2026-11-18): what only
    tests reach is `#[cfg(test)]`, the test init a `#[cfg(test)]`
    block at the end of the init, in its scope, and what nothing
    reaches isn't written; this replaced its scan for module `let`s
    only tests read, which now gives a library value's getter only to
    tests where only they read it. Python, JS, and Lua don't partition
    yet, so their output is as it was.
  - **Not yet:** per-member pruning, and finer rules for which
    initializers may do something (a constructor without effects).
- **Type queries:** subtyping, substitution, member lookup, operator
  methods, override chains, and which interface method bodies a class
  inherits (for Go and Rust trait defaults).

## Level 3: Analyses for subset A

Proposed. Each is a plain function returning a side table; subset A
needs these:

- **The operator table** (`ops::OPS`, data rather than an
  analysis): each operator's name (`Int32.add`), its signature, whether
  it may bubble (`Int32.div` and `Int32.mod` on zero), and whether it's
  pure. As built (2026-10-29), the 65 operators (69 from 2026-11-02,
  with the integer types' `divSafe` and `modSafe`, which never bubble); subset A's first
  table also had core's members and `console.log`, 113 entries in all,
  which are now core's external declarations
  ([nodes.md](nodes.md#level-3-ops-and-core-as-declarations)). A
  test checks each backend's table against it.
- **Order-sensitive operands,** for hoisting: an operand to the left
  of hoisted statements stays in place only if it's a literal, or a
  read of a local that those statements don't assign; anything else
  (a call, a module `var`'s read when the statements call anything) is
  hoisted into a temporary first. In subset A no call can assign a
  local, since there are no captures. As built (2026-10-30, for C++,
  which evaluates operands in any order): `analysis::Effects`, per
  module, gives an expression's `Effect`: whether it writes module
  state or calls something that may (a function that does, through
  calls, or a connected declaration not in `PURE_CORE`), reads a `var`
  that functions write, or may bubble; two operands' order shows when
  one writes and the other writes, reads, or bubbles
  (`Effect::conflicts`). `analysis::calls_nothing` says whether an
  expression may run under a lock. Lua's first variant (2026-10-30)
  uses `Effect::any` the other way round: Lua keeps Temper's order, but
  its `and` and `or` skip their right side, so `Boolean`'s order as
  logic (`not a and b`) needs a right side without effects, and an `if`
  value spelled `(c and a) or (not c and b)` a condition without them,
  which it evaluates twice; otherwise it spells them another way.
- **May-bubble:** which calls and functions may bubble, for F3 and F4
  and for stored marks.
- **Module state:** which module `let`s each function reads and
  writes, directly or through calls; for F5, [question 19](open-questions.md)'s warning,
  Python's `global`, and later init guards.
- **Definite assignment:** for `Let`s without initializers (R5), and
  for Java, Rust, and C# later.
- **Liveness:** each local's live range, as a span of statement
  positions in its function, for names shared by locals whose
  lifetimes don't overlap ([names.md](names.md)).
- **Naming hints** (`naming::hints::for_expr`): candidates for an
  expression a pass stores, in order: the parameter it feeds (`x` for
  `new Point(x = …)`); the local or field it copies; its callee's name
  with a leading `get` or `to` dropped (`toInt32` gives `int32`,
  `getName` gives `name`); its type's name in lower camel case
  (`point`, `string`); then a role (`result`, `value`). Duplicates are
  dropped, so the list is short. As built (2026-10-30): `naming::hints`,
  without the parameter fed and the field copied yet, and with `value`
  as the only role; the C++ backend's temporaries use it.
