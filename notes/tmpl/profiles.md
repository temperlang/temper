# tmpl: profiles and lowering

Part of the [tmpl design](README.md).

## Level 1: One model, high-level to low-level, chosen by profile

**Decided (the direction):** one data model whose constructs range from
high-level (`for … of`, generators, `orelse` and bubbling, closures,
classes with properties) to low-level (plain loops, explicit error
results, state machines, closure conversion). Each backend declares a
profile of what it handles natively; shared passes lower only what it
lacks; a validator checks a tree against a profile. State machines are
only a tmpl post-pass (../history/milestone-3.md, flat frames).

- **Decided (2026-10-29): a profile is a set of independent features
  plus a few either-or choices.** Targets don't line up on one scale:
  Go has closures but no exceptions, Lua has coroutines but no
  `continue`, C99 has `goto` but no closures. The choices are things
  like bubbling as exceptions or results (Level 2, below).
- **Passes run in one fixed order** (Proposed), each removing one
  feature and declaring what it may introduce (closure conversion may
  add classes; the state-machine pass needs labeled loops and a
  switch). `lower` picks the needed subset; the order never varies, so
  output is deterministic.
- **Lowered output is still tmpl,** checked by the validator against the
  profile after every pass in debug builds and tests.
- **Low-level nodes are defined when a target needs them.** Error codes,
  closure conversion and `goto`-like jumps serve only the [stress list](targets.md)
  today; the model leaves room rather than carrying dead code.

## Level 2: Profiles: features and choices

**Decided (2026-10-29):** a set of independent features plus a few
either-or choices. Proposed shape, plain data:

```rust
pub struct Profile {
    pub native: Features,          // bit set: each feature the target keeps
    pub failure: Failure,          // Exceptions | Results (| ErrorCodes, later)
    pub function_types: FnTypes,   // Plain | Named
    pub module_init: ModuleInit,   // Eager | FirstUse | ClassInit
    pub module_vars: ModuleVars,   // Plain | Synchronized
    pub surface: Surface,          // surface.md
}
```

`Profile::full()` keeps everything; it describes starting tmpl. A
backend crate's variant ([overview.md](overview.md#boundaries)) is a profile, `NamingRules`, and the
surface section, so a Kotlin-compatible variant and an idiomatic one
of the same language differ only in data. The tables below are each
language's first variant.

**Features, and Python's and Rust's first profiles** (native means
kept; otherwise lowered):

| Feature | Python | Rust | Others |
|---|---|---|---|
| Statements inside expressions | no: temporaries | native | only Rust |
| `ForOf` | native | native | all primary targets; `forEach` or index loops are the translator's choice |
| Labeled `break` and `continue` | no: flags or restructuring | native | Java, JS native; C# by `goto`; Lua by `repeat … until true` and flags |
| `Switch` (`SWITCH`, built 2026-11-06 for state machines) | no: `if` chains | native (`match`) | C++, Java native (Java's `long` ones as `if` chains); C#, JS, Lua `if` chains until one needs it |
| Generators | native | no: state machine | C#, JS, Lua (coroutines) native; C++, Java state machine |
| `async` | native | native ([question 20](open-questions.md)) | C#, JS native; others state machine |
| Assigned captures | native (`nonlocal`) | no: cells | C#, JS, Lua native; C++, Java cells |
| Properties | native (`@property`) | no: methods | C#, JS native; C++, Java, Lua methods |
| `Void` as a value | no | native (`()`) | JS, Lua native |
| Bottom type, `Never<T>` as a result (`NEVER`, built 2026-11-17; `nevers::lower`, [types.md](types.md#never)) | native (`NoReturn`) | no: `T` | JS native (`never`); others `T` |
| Left-to-right arguments | native | native | all but C++ |
| Null-safe `EqNullable` | native | native (`Option`) | JS for builtin types; others expanded |

| Choice | Python | Rust | Others |
|---|---|---|---|
| Failure | exceptions | results | exceptions everywhere else (Lua's `error` and `pcall`) |
| Function types | plain | plain | named in Java (by signature, as Kotlin's); plain elsewhere |
| Module init | eager | first use | C++ first use; Java, C# class init; JS, Lua eager |
| Module `var`s | plain | synchronized | C++, C#, Java synchronized; JS, Lua plain |

**As built, function values (2026-10-31, Proposed):** no profile
feature or lowering pass yet; each backend spells function values
itself, as Kotlin's do, from `analysis::assigned_captures` for cells:

| Backend | Function type | Literal | Assigned capture |
|---|---|---|---|
| Python | `Callable[[A], R]` | `lambda` for one expression, else a nested `def`; made by a factory where it captures a loop's `let`s | `nonlocal`, or `global` for the init block's |
| Rust | `Arc<dyn Fn(A) -> R + Send + Sync>` | `move` closure in an `Arc`, after clones; a plain closure where core takes `&dyn Fn` | `Arc<RwLock<T>>`, `temper_core::read_locked` |
| JS | JSDoc `(arg0: A) => R` | arrow for one expression, else a `function` | native |
| Java | `java.util.function`'s by signature, else one synthesized in `PKG.function` | lambda, or a typed local for statements | a local class's `value` |
| C# | `Func<A, R>`, `Action<A>` | lambda with typed parameters, else a local function | native |
| C++ | `std::function<R(A)>` | `[=]` lambda, or an `auto` local for statements | `std::shared_ptr<T>` |
| Lua | LuaCATS `fun(arg0: A): R` | `function(x) return … end` for one expression, else a `local function` | native |

**As built, parameters' defaults (2026-11-01, Proposed):** no profile
feature or lowering pass; each backend presents an optional parameter
as Kotlin's do and prints its default (`analysis::defaults`) itself,
more plainly than Kotlin's `if`/`else` into a fresh local:

| Backend | Optional parameter | Callers leave out | Default |
|---|---|---|---|
| Python | `end: int \| None = None` (trailing ones), positional-only | trailing ones; `None` before one given | `if end is None: end = …`, in place |
| Rust | `end: Option<i32>`, `Option<impl ToArcString>` for a string | none: `None`, `None::<Arc<String>>` | `let end = end.unwrap_or(…)`, `unwrap_or_else(…)`, or `if let Some(end) = end { end } else { … }` where it may bubble |
| JS | JSDoc `@param {number \| null} [end]` (trailing ones) | trailing ones; `undefined` before one given | `end ??= …`, in place, or `if (end == null) { … }` |
| Java | `@Nullable Integer end`, with an overload per trailing one left out, passing `null` | trailing ones, by overloads | `int end2 = end != null ? end : …`, or an `if` with statements |
| C# | `int? end = null` (trailing ones) | trailing ones; `null` before one given | `int end2 = end ?? …`, or an `if` with statements |
| C++ | `core::NullableParam<int32_t> end`, with overloads as Java's | trailing ones, by overloads | `int32_t end2 = end == nullptr ? … : end.value`, or an `if` |
| Lua | `---@param end_? integer\|temper.Null` (trailing ones) | trailing ones; `nil` before one given | `if temper.is_null(end_) then end_ = … end`, in place, then `---@cast end_ integer` |

A null default (`mark: String? = null`) prints nothing: the local is
the parameter. Builders for exported classes' constructors (Rust,
Java) leave optional parameters out: Java's builder doesn't require
them, and Rust's, as Kotlin's, puts required ones in `RectBuilder { w
}` and optional ones in `RectOptions` (`.options().h(2).build()`).

**As built, core's other types (2026-11-02, Proposed):** no profile
feature or pass; each backend's connection table writes core's string
indices, `StringBuilder`, `Empty`, `Deque`, `DenseBitVector`, and the
rest of `String`'s and `Float64`'s members as Kotlin's do, through its
runtime, and reads core's statics (`String.begin`, `Float64.pi`)
through the same table. A string index is an offset into the
language's own string, and no index is a value no offset takes, so `is`
and `as` on them are comparisons and the runtime's checks:

| Backend | `StringIndex`, `StringIndexOption`, `NoStringIndex` | `is StringIndex` | `StringBuilder` | `Deque<T>` | `DenseBitVector` | `Empty` |
|---|---|---|---|---|---|---|
| Python | `int`, none `-1` | `i >= 0` | `list[str]`, `''.join(b)` | `collections.deque` | `temper_core.DenseBitVector` | `tuple[()]`, `()` |
| Rust | `usize`, `Option<usize>`, `()` | `i.is_some()` | `Arc<RwLock<String>>` | `temper_core::Deque<T>` | `temper_core::DenseBitVector` | `()` |
| JS | `number`, none `-1` | `i >= 0` | `string[]`, `b[0] += s` | `T[]` | the runtime's | `{}` |
| Java | `int` (`Integer` boxed), none `-1` | `i >= 0` | `java.lang.StringBuilder` | `ArrayDeque`, `LinkedList` for nullable items | `java.util.BitSet` | `Optional<?>` |
| C# | `int`, none `-1` | `i >= 0` | `System.Text.StringBuilder` | `Queue<T>` | `BitArray` | `Tuple<object?>` |
| C++ | `int32_t` | `String::isStringIndex(i)` | `shared_ptr<ostringstream>` | `shared_ptr<std::deque<T>>` | `shared_ptr<vector<bool>>` | `shared_ptr<core::Empty>` |
| Lua | `integer`, byte offsets from 1, none `-1` | `i >= 1` | `temper.StringBuilder` | `temper.Deque` | `temper.DenseBitVector` | `table` |

`compareTo` is a difference or the language's compare, and `as` the
runtime's `requireStringIndex` (Rust's `cast_as_index`). `near` with a
tolerance left out before one given passes the language's null there,
as Kotlin's does. Rust narrows `if (i is StringIndex)` into `if let
Some(i) = i`, as it does null checks.

`Int32` wrapping isn't a pass: the key stays, and each translator
spells it, with helpers where the value-range analysis can't rule out
overflow (JS, Lua, Python).

**Pass order,** fixed: connected lowering (each `Lower` hook the
variant's connection table names, Proposed 2026-10-29,
[names.md](names.md#level-3-connected-declarations)); named function types; properties to methods;
generators and `async` to state machines (they add labels and
switches); `ForOf` (only for targets without loops over iterables);
failure; `EqNullable`; `Switch` to `if`; labeled jumps; statements out
of expressions; evaluation-order temporaries; cells; module `var`
synchronization; module init; `Void` values. Each declares what it
needs and what it may add, and the validator checks both.

## Level 3: Lowering for subset A

### Variants and the first profiles

**Decided (2026-10-29): every backend's first variant is
Kotlin-compatible:** its public surface (layout, public names,
signatures, and runtime library) matches Kotlin Temper's, as each
variant's API diff checks, so Chromarite's and Ordable's tours keep
working. Private names and bodies still get prettier. Other variants
follow as data, each with its own API baseline.

Proposed: variants are named on the command line, the first one by
the language's short name (`py`, `rust`, …), and later ones by a
suffix (`py-idiomatic`). A program's text header names the profile
it's lowered for.

Subset A's features, for `py`: everything native except statements
inside expressions. Its choices: exceptions, plain function types,
eager init, and plain module `var`s. Unlike the Level 2 table, Python
keeps `Void` as a value natively, as `None` ([question 30](open-questions.md)).

As built (2026-10-30, in `crates/temper-be-py`): a variant is a
`Variant { name, profile, rules, runtime_version }`, and the first is
`PY`, with no native features (statements inside expressions are
lowered) and Python's `NamingRules`. `lower` and the hoisting pass
aren't built yet: none of the 7 translated tests needs one, and a
program that does is reported as not yet. The either-or choices
(failure, module init, and the rest) wait for the passes that read
them.

### As built: Rust

As built (2026-10-30, in `crates/temper-be-rust`): the first variant is
`RUST`, a `Variant { name, profile, rules, runtime_version,
rust_version }` (`temper-core` 0.6.0 and `rust-version` 1.71.1, as
Kotlin's), with statements inside expressions native (as Rust blocks),
so subset A needs no lowering. Its choices, as Level 2's table has
them, are made by the translator for now, since no pass reads them yet:

- **Failure: results,** without a pass. In subset A only operators and
  calls bubble, and nothing catches, so a function that may bubble
  returns `temper_core::Result<T>`, each call or operator that may
  bubble takes `?`, and `Error` returns `Err`. The results pass waits
  for `orelse` (subset B and C).
- **Module init: once, on `init` or first use,** as Kotlin's since
  #636: `lib.rs`'s `init` calls the module's, inside
  `temper_core::initializing`, which runs the init block in a
  `OnceLock` (`INIT_ONCE`) and keeps its result, so later calls give
  the same `Ok` or `Err`; and a getter reads its static through the
  crate's `support::initialized`, which calls `init_on_first_use()`,
  `init(None)`, where the library isn't initialized yet, through
  `temper_core::initialized`. Unlike Kotlin's, a read on another
  thread waits for the whole init, not just for the value it reads
  (kotlin-differences.md, "Rust first use"), at one atomic load per
  read. A function that reads no module state doesn't initialize the
  library, as Kotlin's doesn't.
- **Module `var`s: synchronized,** where shared. A module `let` or
  `var` that only the init block uses is a local of it, which runs
  once on one thread. One that a function uses, or that's exported, is
  a `static` with a getter function (`pub` if exported), as Kotlin's
  "toppers": a `let` a `OnceLock<T>`, set once by init, and a `var` an
  `RwLock<Option<T>>`, `None` until init sets it. A read clones the
  value under the read lock, in the getter, so no guard outlives it.
  Rust's types make these safe across threads (no data race is
  possible in safe code), as Kotlin's are. Where Kotlin isn't quite:
  `count = count + 1` reads under one lock and writes under another,
  so concurrent updates can be lost. Here, a `var` assigned a value
  that reads it and else only locals and literals, through operators,
  is read and written under one write lock (the `module-state` test
  runs 8,000 such updates from 8 threads and gets 8,000). A value that
  calls anything, or reads other module state, is computed first and
  stored under the lock after, as Kotlin's, since holding the lock
  across a call could deadlock with a call that takes it again.

### As built: JS

As built (2026-10-30, in `crates/temper-be-js`): the first variant is
`JS`, a `Variant { name, profile, rules, runtime_version }`, with no
native features, as Python's: statements inside expressions wait for
the hoisting pass, and a program that needs it is reported as not yet.
JS keeps natively everything else subset A has: labeled `break` and
`continue`, `do … while` with `continue`, and `Void` as `undefined`.
Its choices, made by the translator for now, as Level 2's table has
them:

- **Failure: exceptions.** A bubble is a thrown error: the runtime's
  `bubble()` for `Error`, and its `divIntInt` and `modIntInt`, or
  BigInt's own `/` and `%`, for a zero divisor.
- **Module init: eager,** as Kotlin's: a module's code runs once, when
  it's first imported. A module `let` or `var` that the init block
  initializes at its top level is declared there (`const` or `let`,
  with its value); others are declared first, with a JSDoc type and no
  value (`analysis::module_state` says which, shared with Rust).
- **Module `var`s: plain,** as `let`s; JS runs a module's code on one
  thread, and an exported one is a live binding, as Kotlin's.

**Kotlin-compatible surface:** `package.json` (`"type": "module"`,
`exports` for `.` and `./NAME`, and `@temperlang/core` as a
dependency), `index.js` re-exporting the module, the module's public
file `NAME.js` re-exporting its exports, in source order, from
`NAME.internal.js`, which holds the code; names keep their case. One
difference, in what isn't public: Kotlin's `.internal.js` exports every
top-level, so other modules can import them, while this exports only
what the source does, until there are several modules. A `Console` is
erased, as Rust's is: `console.log(…)` is the global console, which
Kotlin's `globalConsole` is too.

**Naming:** block-scoped (`NamingRules::block_scoped`,
[names.md](names.md#as-built-2026-10-30)): two locals of a function share
a name only when their blocks don't nest, since `let` can't declare a
name twice in one block, and an inner one would shadow an outer one from
the start of its block. A tmpl block inside a block keeps its braces.

**Versions** (Proposed): `@temperlang/core` 0.6.0, as Kotlin's; generated
code needs ES2020 (BigInt) and ES modules, so Node 14 or later, and is
checked as ES2022 under Node's module rules (`--module nodenext`); the
tests use Node 24 locally and in CI, and TypeScript's latest (7.0.2
locally).

### As built: Java

As built (2026-10-30, in `crates/temper-be-java`): the first variant is
`JAVA`, a `Variant { name, profile, rules, runtime_version,
java_release }`, with no native features, as JS's: statements inside
expressions wait for the hoisting pass, and a program that needs it is
reported as not yet. Java keeps natively everything else subset A has:
labeled `break` and `continue`, `do … while` with `continue`, and `?:`
for `IfValue`. Its choices, made by the translator for now, as Level 2's
table has them:

- **Failure: exceptions.** A bubble is an unchecked `RuntimeException`
  from the runtime's `Core.bubble()`, thrown by `throw Core.bubble();`
  where `Error` is a statement or a `return`'s value, or by
  `Core.throwBubble()` in an expression; `Core.divIntInt` and
  `Core.modIntInt` throw it for a zero divisor.
- **Module init: class init,** as Kotlin's: a module is a final class of
  statics, and its init block the class's static initializer, which the
  JVM runs once, on one thread, when the class is first used (the main
  class loads it with `Class.forName`).
- **Module `var`s: synchronized,** lightly. Module `let`s and `var`s that
  functions use, or that are exported, are static fields (`final` for a
  `let`), declared without a value and assigned in the static
  initializer, as Kotlin's are; the rest are the initializer's locals. A
  `var` that a function writes is `volatile`, so every thread reads its
  latest value, and a function's update of one from itself alone
  (`analysis::reads_only_itself`, shared with Rust) takes the class's
  lock, so `ticks = ticks + 1` from 8 threads, 1,000 times each, gives
  8,000. Kotlin's are plain statics. Other updates aren't atomic, as in
  Rust.

**Kotlin-compatible surface:** `pom.xml` (group the package, artifact
the library, `temper-core` 0.6.0, Java 17 by `<release>`, and the main
class for the jar and `exec:java`, otherwise Kotlin's template), and a
package named for the library with `_` for `-` (`hello_world`), holding
`HelloWorldGlobal` (the module) and `HelloWorldMain` (`main`: simple
logging to stdout, the module's class loaded, async tasks waited for).
Exports are `public static`, the rest package-private, with the source's
names. Logging goes through `temper-core`'s `Console`, from
`Core.getConsole(Logger.getLogger(PACKAGE))`, as Kotlin's, not erased
as in JS and Rust, since `java.util.logging` configures where it goes.

**Naming:** block-scoped, as JS's: Java forbids a local that shadows
another local. The types the translator names (`Math`, `Integer`,
`Core`, `Logger`, …) are reserved, since a local of that name would
obscure them.

**Versions** (Proposed, 2026-10-30): generated code targets **Java 17**
(`javac --release 17`), as Kotlin's `java` backend does (its `java8`
backend is a separate one, not carried forward yet); nothing in subset
A needs more, and 17 is the oldest LTS still widely supported. The
runtime, `temper-core` 0.6.0, compiles for Java 8, as its own
`pom.xml` says. The tests use JDK 21 (Temurin 21.0.4 locally, the
latest 21 in CI).

### As built: C#

As built (2026-10-30, in `crates/temper-be-csharp`): the first variant
is `CSHARP`, a `Variant { name, profile, rules, runtime_version,
target_framework }`, with no native features, as Java's: statements
inside expressions wait for the hoisting pass, and a program that needs
it is reported as not yet. C# keeps natively everything else subset A
has but labeled jumps: a `break` or `continue` of a loop that isn't the
innermost is a `goto` to a label after the loop (`outerEnd: ;`) or at
the end of its body (`outerNext: ;`), as Level 2's table has it. Its
choices, made by the translator for now:

- **Failure: exceptions.** A bubble is the runtime's `Core.Bubble()`,
  which throws; in a function with a result, `return Core.Bubble<T>();`,
  so C# sees the function end, and in an expression `Core.Bubble<T>()`.
  `Core.Div` and `Core.Mod` bubble on a zero divisor.
- **Module init: class init,** as Kotlin's: a module is a static class,
  and its init block the class's static constructor, which .NET runs
  once, on one thread, before the class is first used (the program runs
  it with `RuntimeHelpers.RunClassConstructor`).
- **Module `var`s: synchronized,** lightly, as Java's: statics that
  functions use, or that are exported, are static fields (`readonly` for
  a `let`), assigned in the static constructor; a `var` that a function
  writes is `volatile` (but a `long` or `double`, which C# doesn't
  allow), and a function's update of one from itself alone takes the
  module's lock, a private `object` (not `lock (typeof(…))`, which other
  code could take too). 8 threads × 1,000 ticks give 8,000.

**Kotlin-compatible surface:** per library, `src/ROOT.csproj` (.NET 6,
`RootNamespace`, `IsPackable`, `Nullable` enabled, `InternalsVisibleTo`
its `ROOTTest` project, and a project reference to
`../../temper-core/TemperLang.Core.csproj`; `<Version>` only for a
library that sets one), `src/ROOTGlobal.cs` (the root module, a public
static class in namespace `ROOT`, the library's name in Pascal case),
`src/Logging.cs` (Kotlin's template, in `ROOT.Support`, through which a
host sends logs where it likes), and `program/ROOTProgram.csproj` with
Kotlin's `Program.cs`. Exports are `public static`, the rest
`internal`, as Kotlin's; functions and exported values are in Pascal
case by Kotlin's conversion (`Fib`, `Greeting`, and Kotlin's
`A98_Rgb`), the rest as the source has them. A `Console` is the
runtime's `ILoggingConsole`, from the library's
`Support.Logging.LoggingConsoleFactory.CreateConsole(ROOT)`, as
Kotlin's.

**Culture** (C# only): C#'s string and number operations follow the
current culture unless told otherwise, so the translator never leaves
it to them: `Int32.toString()` is `n.ToString(CultureInfo.
InvariantCulture)`, or with two or more numbers in a concatenation
`string.Create(CultureInfo.InvariantCulture, $"…")`, never `+` on a
number; string equality is `==` and comparison the runtime's code-point
`StringUtil.CompareStringsByCodePoint`, both ordinal; `Boolean.toString()`
is `b ? "true" : "false"`; and `isEmpty` is `s.Length == 0`. The tests'
analyzers make any culture-sensitive call an error, and their runs use a
culture whose minus sign isn't `-`.

**Parameter names** are API in C# (named arguments), so they're exactly
the source's, with `@` for a keyword. The Roslyn analyzer that is to warn
on named arguments in calls into a library, but for constructors and
factories ([surface.md](surface.md#parameter-names-where-languages-have-named-arguments-proposed-2026-10-30)),
isn't built yet: it belongs in the NuGet package, which waits for
packing.

**Versions** (Proposed, 2026-10-30; corrected 2026-11-04): Kotlin's
generated projects target **`net48;net6.0`**, as its library template
(`RootNamespaceSpot.csproj`) and runtime do; tempest's target only
`net6.0` (C# 10) so far, which the tests build, and should match
Kotlin's (roadmap, "Target versions"). .NET 6 is out of Microsoft's
support (since 2024-11), so the modern target should move to .NET 8
(LTS), whose SDK also brings C# 12 and `>>>`, together with Kotlin's. The tests use the local .NET 6.0.402 SDK (6.0.x in
CI), its Roslyn and .NET analyzers.

### As built: C++

As built (2026-10-30, in `crates/temper-be-cpp`): the first variant is
`CPP`, a `Variant { name, profile, rules, standard }`, with no native
features, as C#'s: statements inside expressions wait for the hoisting
pass, and a program that needs it is reported as not yet. C++ keeps
natively everything else subset A has but labeled jumps (`goto`, as
C#'s, to a label after the loop or after a block holding its body) and
left-to-right evaluation, which the translator keeps itself (below). Its
choices, made by the translator for now:

- **Failure: exceptions.** A bubble is the runtime's `core::bubble()`,
  which throws `TemperBubble`; in an expression, `core::bubble<T>()`.
  `Int::div_wrap` and `Int::mod_wrap` bubble on a zero divisor; a
  literal divisor other than 0 and -1 takes C++'s own `/` and `%`.
- **Module init: on first use,** as Kotlin's since #636:
  `global_init_init()`, declared first in the header, runs its body
  once through the runtime's `init_once`, so concurrent callers wait
  for one run and the init's own calls of entry points return at once
  (`std::call_once` would deadlock there). Exported functions, public
  constructors and static functions of exported types, and accessors
  call it first, where it does anything; `main.cpp` calls it too.
  Module values and statics are `GlobalSlot`s (`temper_globals::x`,
  `Type::temper_statics::x`, private to its class), constructed first
  by the init, and users
  read them through `x()` and `Type::x()`, `const &`s, except an
  exported `var` that functions write, whose accessor gives a copy read
  under the module's lock.
- **Module `var`s: synchronized,** more strictly than Java's and C#'s,
  since a data race is undefined behavior in C++: a `var` that a
  function writes is read and written in functions only under the
  module's `std::mutex`. A run of statements that use it and call
  nothing (`analysis::calls_nothing`) holds one `std::lock_guard` to
  its block's end (`bump`'s two updates and its `return`); elsewhere a
  read is `load(x)` and a store of a call's result `store(x, value)`,
  small helpers the module's `.cpp` gets when it uses them, computing
  the value first, since a call could take the lock. The init function
  takes no lock. 8 threads × 1,000 ticks give 8,000, and
  ThreadSanitizer reports nothing; Kotlin's plain globals give about
  6,500, and a race.
- **Order of evaluation: kept.** C++ leaves the order of arguments and
  most operands unspecified, so where it would show
  (`analysis::Effects`: an operand writes module state or calls
  something that may, and another does too, reads such a `var`, or may
  bubble), each operand before the last such one is computed first into
  a local named by `naming::hints` (`int32_t int32 = bump();`). A
  temporary needed where none can go before its statement (the right of
  `&&`, a branch of `?:`, a loop's condition) is not yet. This is the
  pass order's "evaluation-order temporaries", done in the translator
  until the hoisting pass exists to share it.

**Kotlin-compatible surface:** per library, `LIB/init.hpp` (the root
module's header: `#include <temper-core/core.hpp>`, then, in namespace
`LIB` with runs of other characters as `_`, exported `var`s and `let`s
as `extern` variables, exported functions, and `void
global_init_init()`), `LIB/init.cpp`, and `LIB/main.cpp`, which calls
the init, as Kotlin's. Names keep their case; keywords and names the
translator prints or the runtime defines as macros take `_`
(`explicit_`, `M_PI_`). Types are `bool`, `int32_t`, `int64_t`,
`double`, and the runtime's shared `temper::core::Str` (a `std::string`
until Temper #654), as Kotlin's; a `Console` is the
runtime's `std::shared_ptr<temper::core::Console::Type>`, from
`get_console()`, which logs to `std::cout`. Two differences: the header
declares only exports, where Kotlin's declares every function, and
names that aren't API keep their non-ASCII letters, where Kotlin spells
them in hex. No build files, as Kotlin writes none: its `temper build`
runs `g++ -I.. -std=c++14` on a library's `.cpp` files, and Kotlin's
tours bring their own `CMakeLists.txt`.

**Temporaries that die in use** (Proposed, for subset B's lists, not
reached by subset A): Kotlin returns a list as a
`std::shared_ptr<std::vector<T>>`, so its `for (auto const &x :
*obj->list())` iterates a vector whose only owner, the temporary
pointer, dies before the loop's first step (C++ extends a range-for's
range, not the temporaries inside its expression, until C++23): a
use-after-free, which crashed Hrefine's tour. The translator will never
iterate, index, or hold a reference into the result of a call in place:

- A `for … of` over anything but a local, a parameter, or a field gets
  the list into a named local first, `std::shared_ptr<std::vector<T>>
  const items = obj->list();`, whose count keeps the vector alive
  through the loop, then `for (T const &x : *items)`.
- A reference (`T const &`) is bound only to something a named local
  owns for longer than the reference; an element or member of a
  temporary is copied (a `shared_ptr`'s copy is a count), never
  referred to.
- Parameters keep Kotlin's types (`shared_ptr` by value or by `const&`),
  so a caller's temporary lives through the call, as C++ guarantees.
- A test will iterate a list a function returns, and a field's list
  through a returned object, under AddressSanitizer, which reports
  Kotlin's form as a heap-use-after-free.

Kotlin's public API keeps returning `shared_ptr`s, so users' own code
can still fall in; a later variant might return lists by value or as
`const&` to a member, and the header might say so meanwhile.

**Versions** (Proposed, 2026-10-30): generated code keeps to **C++14**
(`-std=c++14`, as Kotlin's `RunCpp.kt` compiles it, with g++ 5 the
oldest it accepts), which every current compiler has; nothing in subset
A needs more. C++17 would give `[[maybe_unused]]`, `std::optional`,
and sequenced `<<`, and C++20 `std::atomic<std::shared_ptr<T>>` and
defined signed shifts, worth weighing for a later variant. The tests use
g++ 13.3 locally and the runner's g++ (13 on ubuntu-24.04) in CI.

### As built: Lua

As built (2026-10-30, in `crates/temper-be-lua`): the first variant is
`LUA`, a `Variant { name, profile, rules, runtime_version, lua_version }`,
with no native features, as C++'s: statements inside expressions wait
for the hoisting pass, and a program that needs it is reported as not
yet. Lua keeps natively everything else subset A has: `Void` as `nil`,
left-to-right order (its reference implementation and LuaJIT evaluate
operands and arguments in order; the manual leaves open only multiple
assignment, which isn't printed), and `do … while` as `repeat … until
not …`. Labeled jumps are `break`s, without `goto`, which Lua 5.1
lacks: a `continue`'s loop has its body in a `repeat … until true` that
the `continue` leaves, which a `repeat`'s condition follows, and a jump
that leaves more than one loop (or `repeat`) sets the target loop's
flag, a local its body declares, and breaks, with `if flag then break
end` after each one it leaves. Its choices,
made by the translator for now:

- **Failure: exceptions.** A bubble is the runtime's `temper.bubble()`,
  which raises an error; `temper.int32_div` and `int32_mod` raise on a
  zero divisor (Lua's integer `//` does).
- **Module init: eager,** as Kotlin's: a module's chunk runs once, at
  its first `require`, which keeps what it returns in `package.loaded`.
  Module `let`s and `var`s are locals of the chunk, which functions use
  as upvalues: declared first, with a `---@type`, if functions use them
  or the init block doesn't initialize them at its top level
  (`analysis::module_state`), and otherwise declared where the init
  block gives their value.
- **Module `var`s: plain.** Lua has no threads. Exported `var`s that
  functions write are fields of the exports table (`exports.ticks`), so
  users read their current value; Kotlin's copies them once.

**Module state and coroutines** (Proposed): Lua's concurrency is
coroutines, which switch only where code yields. Subset A never yields,
so a module's init runs whole, and an update such as `ticks = ticks + 1`
can't interleave with another coroutine's. Two things to keep when
generators and `async` (subset D) can yield:

- **Init must not yield,** or must guard itself. Lua 5.2 and later set
  `package.loaded[name]` only when the chunk returns, with no marker
  while it loads (5.1 had one, which raised "loop or previous error
  loading module"), so a second coroutine's `require` during a yield
  would run the init again. A top-level `await` would need a guard: the
  module marks itself loading first (`package.loaded[name]`, or a local
  flag) and makes later callers wait or fail.
- **An update shouldn't span a yield.** A `var`'s read and write with a
  yield between them can lose another coroutine's write, as JS's can
  across an `await`; the state-machine pass should keep a module `var`'s
  read-modify-write within one resumption, or say it doesn't.

A host with several Lua states (separate interpreters, or libraries such
as Lanes) loads a module once per state, each with its own module state;
nothing is shared, so nothing races.

**Lua's limits on locals:** a function, the chunk included, may have at
most 200 locals alive at once (`LUAI_MAXVARS`; and at most 255
registers, which expressions' temporaries share). Past 128 module-level
locals (Kotlin's `MAX_ALLOWABLE_LOCALS`), the module's declarations
after its first 100 are fields of a `module` table, as Kotlin keeps its
first 100 and puts the rest in a table (`env_t1`); a test declares 221
and runs them. A function with more than 200 locals alive at once is
not yet. A function's upvalues are limited too, to 255 in Lua 5.2 and
later, which the 128 keeps within, but to 60 in Lua 5.1 and LuaJIT,
which a function using many module-level names can pass (Kotlin's
output too).

**Kotlin-compatible surface:** per library, `LIB/init.lua` (the root
module, which `require("LIB")` finds with Kotlin's `LUA_PATH`,
`?.lua;?/init.lua`, run from the directory that holds the libraries and
`temper-core`) and Kotlin's rockspec, `LIB/LIB-VERSION-1.rockspec`
(`dev-1` for a library without a version), depending on `lua >= 5.1`
and `temper-core == 0.6.0`. The chunk returns a table of the library's
exports in source order, by the names Kotlin gives them (the source's,
with `_` after a keyword). Kotlin also zips the library for LuaRocks,
naming the zip by its absolute path in `source.url`; this names it
relative and writes none, since `luarocks make` in the library's
directory doesn't need it. Generated code requires the runtime as
`temper-core` and calls its helpers as Kotlin's does, but logs with
`print` (the runtime's `temper.log` is `print`), concatenates with `..`
(Kotlin's `temper.concat` is `table.concat`), and writes `s == ""` for
`isEmpty`.

**Strings** are bytes in Lua: a literal is the string's UTF-8, with
control, invisible, and bidirectional characters as decimal byte
escapes, which every Lua reads. Strings compare with Lua's `==` and `<`;
`<` uses `strcoll`, which in the C locale, the one Lua starts in,
compares bytes, so UTF-8 orders by code point, as Temper's does (the
shared `string-order` program checks it, and Kotlin's `str_cmp` relies
on the same). A host that calls `os.setlocale` would change it, for
Kotlin's output too. Subset A has no lengths or indices; Kotlin's
runtime counts them in bytes from 1 (`StringIndex` is a byte offset),
with code points read from the UTF-8, which subset B's string functions
will use.

**`Int32`** values are Lua integers (on 5.3 and later), kept in range by
the runtime's helpers: `temper.int32_add` and the rest compute in 64
bits and wrap to 32, `int32_div` and `int32_mod` truncate toward zero
(Lua's `//` and `%` floor), and shifts mask their count; the `operators`
program, whose log is the interpreter's, checks wrapping, shifts, and
signed division and remainder. Being integers, they print without `.0`,
so `"n=" .. n` and `tostring(n)` write them as Temper does. `Int64`
values are whatever the runtime's `int64_constructor` makes, as
Kotlin's: Lua integers on 5.3 and later, whose own arithmetic wraps at
64 bits, and on 5.1 and LuaJIT tables of two 32-bit halves, whose
metatable gives them `+`, `-`, `*`, `==`, `<`, and `<=`. So every
distinct `Int64` constant is made once, by a call at the chunk's top
after `temper` (`local int64_5 = temper.int64_constructor(5)`, or its
high and low halves past 32 bits; past 32 constants, fields of one
`int64s` table, to spare locals and upvalues), which its literals name,
so a loop doesn't call the runtime for them; nothing changes an
`Int64` in place, so sharing one is safe. `Int32.toInt64()` is a call,
and the operators are Lua's own or the runtime's (`int64_div`, `shl64`).

**Versions** (Proposed, 2026-10-30): as Kotlin's, the rockspec accepts
**Lua 5.1 and later**, and generated code calls the runtime for integer
and bitwise work, never Lua 5.3's operators, so the runtime's
`intold.lua` can stand in on 5.1 and LuaJIT. In practice: generated code
needed **Lua 5.2 or LuaJIT** where it had `goto` (a `continue`, or a jump
out of an inner loop; Kotlin's needs it for every early return too)
until 2026-11-20, when it dropped `goto`; and it needs nothing more for
integers: on 5.1 and LuaJIT, where numbers are doubles,
`int32_mul` splits its operands to stay exact, and `Int64`s are the
runtime's tables of halves; Kotlin's CI runs Lua 5.4.
The tests use Lua 5.4.6, locally and in CI (Ubuntu 24.04's `lua5.4`,
the runner pinned for it), and LuaLS 3.19.2. CI also runs the generated Lua
under LuaJIT 2.1, built from a pinned commit, and it passes; 5.1 isn't
tested. LuaJIT's b310887b (2026-10-29) let x64 table stores of
constants and GC values reuse the register holding the store's own
address under register pressure, crashing (a SQL library's tests did in
about 2 runs in 5; Kotlin's output for it didn't, by luck of
allocation); upstream fixed it the next day in 3a658cac, so the pin must
not fall between those two commits. It's 63c8f1a9, which has the fix.
A later variant could require 5.3 and print its operators (`a & b`,
`a // b`) where they're right.

### Passes

```rust
pub struct Pass {
    pub name: &'static str,
    pub removes: Feature,       // what the profile lacks
    pub needs: Features,        // what must already be gone
    pub may_add: Features,      // checked by the validator afterwards
    pub run: fn(Program, &Profile) -> Program,
}
```

`lower` runs, in the fixed order, each pass whose feature the profile
lacks and the program uses, and validates after each in tests and
debug builds. Subset A needs one pass for Python.

### Statements out of expressions (`hoist`)

Built so far (2026-11-06), shared: step 1 below, and step 4 for
`while` conditions and, as statements in a block's value, for `And`,
`Or`, and `IfValue` parts that run only sometimes, as
`temper_tmpl::spill`, which Lua, C#, and C++ run before translating
(C++ and C# with `spill_with`, marking more that needs statements: what
C++ holds in temporaries for its order of evaluation, and C#'s
`panic()`); each backend still hoists the rest in its own way.

- **Precondition:** the profile lacks statements inside expressions.
- **Output:** no `BlockValue`, and no statement under an expression;
  `IfValue` remains only with plain expressions in both branches,
  which Python writes as `a if c else b`.
- **Method:** for a statement `S` holding an expression `E` that holds
  statements:
  1. Hoist each operand left of `E` that order requires (the
     order-sensitive operands analysis) into a `let` temporary, named
     by `naming::hints`.
  2. Turn `E` into statements that assign its value to a target, and
     put them before `S`, then put a read of the target in `E`'s place.
  3. Choose the target without a temporary where possible: a `Let x =
     E` assigns `x` itself (a `Let` without an initializer); a `Return
     E` returns in each branch; an `ExprStatement` discards the value.
     Otherwise the target is a new `let` temporary with hints.
  4. An `And` or `Or` whose right side holds statements becomes an
     `IfValue` first; a `while` condition that holds statements
     becomes `while (true) { …; if (!c) break; … }`.
- **Spans:** hoisted statements keep theirs; a temporary's
  declaration, assignments, and read take the hoisted expression's,
  marked derived.

Example: `count` is a module function, impure, so its call is hoisted
before `"counted"` is logged:

```text
~console#6.log#10(%Int32.add(count#2(), (value { ~console#6.log#10("counted"); 1 }): Int32).toString#8(omitted));
```

becomes:

```text
let ~count|int32#9: Int32 = count#2();
~console#6.log#10("counted");
~console#6.log#10(%Int32.add(~count#9, 1).toString#8(omitted));
```

The receiver `~console#6` stays in place: a module `let` that isn't
`var` can't change once initialized.

In Python, `count` is a module function the enclosing function calls,
so the temporary takes its second candidate and is named `int32`.
