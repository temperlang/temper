# Differences from Kotlin Temper

Part of the [rewrite plan](README.md). Where tempest deliberately differs
from Kotlin Temper as it is today, and the bugs we know of in Kotlin
Temper, with what tempest does about each. Details live in the linked
notes; the lexer's and parser's are in full here, under "Lexer and
parser". Update this when either side changes.

Each entry where tempest fixes or avoids a Kotlin bug ends with
"Test:", naming what would fail if Kotlin's behavior came back. Paths
are under `crates/`: `settle/X.cases` is
`temper-frontend/tests/settle/X.cases`, its cases named by their `===`
titles; `programs/X` is the backends' shared program in
`temper-be-testkit/programs/`; a funtest is a functional test in `temper-funtests/cases/`,
and `kit::X` a list of them, that each backend's `tests/functional.rs`
runs. Entries without one aren't fixed yet, match Kotlin, or are
proposals. A new fix gets its test in the same change.

## How far to differ

- **Stay compatible** with Kotlin Temper's tests, the functional tests
  above all, wherever at all possible.
- **Do obvious improvements** that stay compatible, such as accepting
  more of what people expect (flow typing that narrows through `&&`, or
  after an early `return`) and clearer errors where Kotlin crashes.
- **Error positions and wording:** match Kotlin's by default, but fix
  clearly bad ones (empty positions, spans in the wrong place, messages
  that count hidden arguments), and note arguable ones here for review
  ([typing.md](typing.md), "Checking semantics and messages").
- **Nothing too exciting without the Temper team:** a new rule, error,
  or language feature is proposed here first, as a recommended change.
- **Backend translation** may differ in ways that make libraries
  incompatible only for really strong reasons, recommended strongly as a
  change to Kotlin Temper's backends before Temper has many users, and
  maybe still with a compatibility mode. Its main use: running existing
  callers of Temper libraries unchanged, such as Chromarite's and
  Ordable's tours, to prove tempest is a Temper
  ([testing-and-milestones.md](testing-and-milestones.md), "Library
  tours"). Libraries update to an improved translation only once the
  team adopts it, never just to work with tempest.

## Changes we recommend to Temper

Language and frontend:

- **Target typing** for object, list and function literals: the expected
  type picks an object literal's class, ahead of matching constructor
  names. Planned, milestone 4 slice 6 (language.md, "Object literals, not
  named arguments"). Begun with list literals: `[d]` where a
  `List<Animal>` is wanted is a `List<Animal>`, and `[]` where a
  `List<Int>` is, a `List<Int32>` ([typing.md](typing.md), "List
  literals and target typing"). Function literals' parameters and
  results are typed from the type wanted as Kotlin types them, with
  trailing blocks placed while typing ([typing.md](typing.md),
  "Function literals and target typing"); a literal's result also binds
  a generic callee's type parameter, which Kotlin binds to its bound, so
  `ls.map { x => x * 2 }` is a `List<Int32>`, where Kotlin's is a
  `List<AnyValue>` (2026-11-24; typing.md, "Function literals' results
  from their bodies"), and a literal that may fail makes the call fail,
  where Kotlin reports it in the literal. Object literals take their
  class from the type wanted (2026-10-29; [typing.md](typing.md),
  "Object literals and target typing"): `let a: A = { x: 1 }` makes an
  `A` where Kotlin says "Multiple types have matching constructors: A,
  B", and where the names fit only another class, tempest says "A is
  wanted here, but these properties fit Mark's constructor" where Kotlin
  makes a `Mark` and says "Cannot assign to A from Mark", at the same
  position. Kotlin also reports "Void expressions cannot be used as
  values" after "No signature matches" for an object literal no class
  fits, a follow-on error tempest doesn't report.
  Test: settle/types.cases, "list literals typed from the type wanted",
  "function literals typed from the type wanted", "function literals'
  results and failures inferred from their bodies", and "object literals
  take their class from the type wanted".
- **Named `@fun` interfaces are nominal** (decided 2026-10-29, done;
  [typing.md](typing.md), "Fun interfaces"): a `Pred` isn't a `Check`,
  even where their `apply`s' signatures match, and the error suggests
  the adapter: "A Pred isn't a Check; to adapt it, wrap it: `fn (y) {
  p(y) }`". Kotlin accepts it, as it compares them as function types.
  Function literals and references to declared functions and methods
  take the interface wanted, so no adapter is needed there; bare
  function types stay structural. A function literal that initializes a
  `let` without a type is an error too ("f needs a declared type, such
  as a @fun interface, …"), which Kotlin accepts. And Kotlin's
  documented rules for declaring one (only `AnyValue` as a supertype,
  one `apply`, no other members, no `@json`, no `is` or `as`) are
  checked, where Kotlin checks none but `apply`'s type parameters.
  Test: settle/types.cases, "named @fun interfaces are nominal", "@fun
  interfaces keep Kotlin Temper's rules for them", and "a function
  literal needs a type wanted".
- **Fields first** (decided by the Temper team 2026-11-02, done;
  [typing.md](typing.md), "Fields first"): a constructor, and the field
  initializers before it, may use `this` only to set fields and read
  those already set until every field is set on every path, and every
  normal return sets every field. Tempest reports an error where Kotlin
  accepts the program, a deliberate, team-approved difference: "Calling
  this.sum() before b is set", "Property b not initialized in
  constructor" for an explicit constructor, and the like. Kotlin checks
  only a class without a constructor, with that last message, and its
  Rust backend fails to compile a field set after `this` is used
  (rustc's E0063), while its other backends read the unset field.
  Test: settle/fields-first.cases, every case.
- **Null and `is` narrowing from control flow,** through `&&`, `||`,
  `!`, `while`, and after an early `return`, `break`, `continue`, or
  `throw`; Kotlin narrows only the branches of an `if` on one check.
  Done for names that aren't `var` ([typing.md](typing.md), "Null-check
  and `is` narrowing: the rules"). Also, `d is I` for a `d` already an
  `I` keeps its more precise type, where Kotlin narrows it to `I`.
  Test: settle/flow.cases, "narrowed beyond Kotlin Temper" and "is
  checks narrow to types".
- **Type errors name declared types,** not the types of folded constants
  (Kotlin's `let s: String? = null; let q: Int = s` says "got Int32?",
  and a name it folds to a function, `opt`, gets `opt`'s type, "from fn
  (Int32, _?: Int32): Int32", where the name's is `fn (Int32, Int32):
  Int32`). Done ([milestone-4.md](history/milestone-4.md), first slice, and
  "Function types compared part by part").
  Kotlin checks a name it folds to null as `null` itself: `let s:
  String? = null; let m: String = s` is "Expected subtype of String, but
  got String?", where tempest says "Cannot assign to String from
  String?", as for a parameter `s`.
  Test: settle/types.cases, "null where a type isn't nullable" and
  "function types compared part by part"; settle/flow.cases, "nullable
  locals stay nullable until narrowed" (`d3`, `d5`).
- **A function literal's result reported at the result,** "Expected
  subtype of Int32, but got String" for `{ x => let y = x; "a" }` where
  a `fn (Int32): Int32` is wanted at the `"a"`, where Kotlin's position
  is empty, at the start of the body (here, the `let`); likewise a
  value it returns, at the value, each `return` on its own, where
  Kotlin's position is empty, just after the `return`, and its type is
  all the literal's results joined. Done ([typing.md](typing.md),
  "Function literals and target typing").
  Test: settle/types.cases, "function literals typed from the type
  wanted" and "returns and bubbling in function literals".
- **One type representation** (`types.rs`), where Kotlin has `type/` and
  `type2/` bridged by `hackMapOldStyleToNew`. Done.
- **Core's generic interface methods** (`Listed.map<O>`, `reduceFrom<O>`,
  `reduceFromIndex<O>`, `Mapped.toListWith<T>`) as `@extension`
  functions, so core needs no exemption from "overridable methods don't
  allow generics". Planned ([typing.md](typing.md), "To explore later").
- **Generic overrides matched by position.** Kotlin compares methods'
  type parameters by identity, so an override of a generic method, as
  `h<U>(x: U): U` for an interface's `h<T>(x: T): T`, always differs
  ("… h(x: U__5): U__5, but it should be h(x: T__1): T__1 …"), on top of
  "Overridable methods don't allow generics" for the interface's method.
  Tempest pairs them by position and reports only real differences. Done
  ([typing.md](typing.md), "Classes' types").
  Test: settle/class-types.cases, "overrides of generic supertypes'
  members" and "interfaces' generic methods, which Kotlin Temper doesn't
  allow".
- **Generators in any function form** (named functions, `fn` literals,
  methods, not only block lambdas), and `return` ends a generator, where
  Kotlin fails typing and panics. Done ([milestone-3.md](history/milestone-3.md),
  flat frames, step 2).
  Test: settle/run.cases, "generators from named functions and methods,
  which Kotlin Temper lacks" and "return ends a generator, and closures
  keep its locals".
- **Private members of other instances** of the same class are usable in
  its methods, as Kotlin Temper's #625 allows (merged to its main
  2026-11-03; before it, Kotlin said only "Cannot translate Invalid" and
  emitted code that fails). Done ([milestone-3.md](history/milestone-3.md),
  "Classes"); use outside the class is reported where it is, as Kotlin's
  typer does ("Member b defined in C not publicly accessible"); no
  longer a difference once Kotlin's next release has it.
  Test: settle/classes.cases, "private members are readable on any
  instance of their class"; programs/classes.temper.md on every backend.
- **`++ls[i]` and `--ls[i]` give the new value,** as `++x` and `++o.n`
  do. Kotlin lowers them to `ls.set(i, …)`, so their value is `Void`,
  which can't be used. `ls[i] += v` stays `Void` there and here, as
  `ls[i] = v` is.
  Test: settle/macros.cases, "compound assignment on index targets
  evaluates the list and index once"; programs/generics.temper.md.
- **`yield` and `await` in `for … of` bodies.** Kotlin lowers a body to a
  `forEach` lambda, so a `yield` there fails at run time. Idea: iterables
  provide a generator or iterator, so `yield`, `await`, `break` and
  `return` just work, backends still map lists to native loops, and
  `forEach` stays an optional fast path; costs an iterator object where
  there's no native mapping. Tempest says "Not supported yet" for now
  ([milestone-3.md](history/milestone-3.md), flat frames).
  Test: settle/run.cases, "yield in a for … of body isn't supported yet"
  (the message).
- **Top-level statements in source order.** Kotlin's `sortTopLevels`
  makes every reader of a top-level `var` wait for each statement that
  assigns it, even `n++` inside a log, so output can scramble. This is
  outright broken, so Temper must settle it. Options: reorder only
  declarations, keeping statements in source order; or require top-level
  side effects, such as assigning a top-level `var`, to be in a top-level
  `do { … }` block, as the team has discussed but not enforced (stricter,
  so maybe unfriendly, though the REPL could wrap input for people).
  Tempest matches Kotlin for now, pending agreement
  ([milestone-3.md](history/milestone-3.md), "Top-level order").
- **NFKC for every non-ASCII word.** Kotlin checks only words with a code
  point past Latin-1, so `ªb` and `ab` are different names. Tempest
  matches for now ("Lexer and parser", below, "NFKC, as Kotlin").
- **Types that may fail written as in Temper's syntax,** `Int32
  throws Oops`, in every message. Kotlin writes its internal types,
  `Result<Int32, Bubble>` in operators' and overrides' signatures and
  `fn (Int32): (Int32 | Bubble)` in function types, though Temper has no
  `Result` type or `|` in its syntax. Done ([typing.md](typing.md),
  "Checking semantics and messages").
  Test: settle/types.cases, "types that may fail are written as Temper
  writes them".
- **`x is T?` passes null in type checks too.** Kotlin's interpreter
  and generated code already make `null is T?` true and let `null as
  T?` through, but its type checker drops the `?`: `s is String?` for
  `s: String` is "Unnecessary type check to String", `s is String?` for
  `s: String?` (always true) and `bon as Boolean?` for `bon: Boolean?`
  aren't reported, `x as D?` for `x: D` is, and a false `a is T?` doesn't
  narrow `a` to non-null. Tempest's checks count null, naming the target
  as written ("Unnecessary type check to String? from expression with
  type String?"), and its interpreter, which dropped the `?` too, now
  passes null. Kotlin's stage test `generate-code/invalid-rtti` expects
  nothing for its `bon as Boolean?`, which its comment calls a no-op;
  tempest says it's unnecessary. Done ([typing.md](typing.md),
  "Nullable targets").
  Test: settle/class-types.cases, "is and as checks with nullable
  targets, which null passes".
- **Say why erased checks fail:** "Type arguments cannot be introduced
  with is or as runtime type checks" (`x is Box<Int>` from `AnyValue`)
  and "Type parameters cannot be targeted …" (`x is T`) end ", because
  a type argument's run-time type can't be checked" in tempest, before
  the types. Done ([typing.md](typing.md), "Run-time type checks").
  Test: settle/class-types.cases, "is and as checks of type parameters'
  values and generic types".
- **Division by zero and failed casts panic** (2026-11-25, a first step
  toward replacing `Bubble`, which is Kotlin's): integer `/` and `%` by
  zero and an `as` that fails panic instead of bubbling, so no `orelse`
  handles them and no function needs `throws Bubble` for them; `Float64`
  `/` and `%` give IEEE 754's results (Kotlin's docs say they bubble,
  but five of its backends gave Inf/NaN). Kotlin-era code that handled
  them (`x as T orelse y`, `a / b orelse 0`) now panics; check with `is`
  or a zero test. Kotlin's constant-divisor variants (`divSafe`) stay as
  a speed choice only. Test: settle/run.cases, "integer division by zero
  panics; Float64's gives IEEE 754's results"; testkit
  test-assert-panics, "passes when division by zero or a cast panics".
- **Core's and std's failing APIs throw declared failures, not
  `Bubble`** (2026-11-25, stage 2 of replacing `Bubble`): conversions
  (`String.toInt32/toInt64/toFloat64`, `Float64.toInt32/toInt64`,
  `Int64.toInt32/toFloat64`) throw core's `ParseError` (input, reason);
  `Mapped.get` (`m[k]`) and `MapBuilder.remove` throw `MissingKey` (key);
  code points that aren't Unicode scalar values
  (`String.fromCodePoint(s)`, `StringBuilder.appendCodePoint`) throw
  `InvalidCodePoint` (codePoint); all three under core's sealed
  `CoreError`. A radix outside 2 through 36 throws `ParseError` on every
  backend (Kotlin's JS read 0 as 10). `getOr(k, null)` is a
  `V?` (the join of the values' type and the fallback's; Kotlin rejects
  the `null`). std: `new Date(y, m, d)` throws `InvalidDate` and
  `Date.fromIsoString` `InvalidIsoDate` (std temporal's `TemporalError`);
  json's `parseJson` throws `JsonSyntaxError`, adapters and `@json`
  `JsonShapeError` (std json's `JsonError`), as does `toJsonString` on a
  partial value (`JsonSyntaxError`). `orelse` handles these as it did bubbles, but a
  function that passes one on declares it (`throws ParseError`), where
  Kotlin's said `throws Bubble`. Test: settle/run.cases, "conversions
  throw ParseError, …", "a missing key throws MissingKey, …", "invalid
  code points throw InvalidCodePoint"; settle/failures.cases, "core's
  failure types: …"; testkit programs/panics.temper.md; funtests
  types/string/read (bad radix too), types/string/build, types/map.
- **std regex's `find` gives `Match?`, and tests may throw any error**
  (2026-11-26, stage 3 of replacing `Bubble`): `find` gives `null` where
  Kotlin's bubbled. A test's function is `Void throws Error` (Kotlin's
  `throws Bubble`), so a declared failure in a test needs no handler and
  fails it with its message; `assertPanics`'s block is `throws Error`
  too ("but it threw", Kotlin's "but it bubbled"). Only the compiler (a
  test's result) and std may write `throws Error`. Test: settle-std regex.cases and
  testing.cases; library_tests `tests_may_throw_any_error`; testkit
  test-assert-panics; funtests regex/match, regex/code-sets.
- **`await` of a broken promise throws core's `BrokenPromise`**
  (2026-11-26), a fieldless `CoreError`, where Kotlin's bubbles; host
  failures that break a promise (std `net`'s) give it too. An `async`
  block handles it where it awaits, and a generator handles it or
  declares it; a `catch` around an `await` works on every backend, state
  machines (Rust, C++, Java) too. Test:
  settle/run.cases, "broken promises throw BrokenPromise, …",
  "BrokenPromise is a CoreError …", "an await that nothing handles";
  funtests control-flow/async, types/netresponse-not-found.
- **Generators throw errors, not bubbles** (2026-11-26): a generator
  body may declare `throws E`, any failure type, and its factory makes a
  `Generator<Y>`, whose `next()` throws `Error` (Kotlin's `throws
  Bubble`, any declared type standing for `Bubble`); a body that
  declares nothing makes a `SafeGenerator<Y>` (Kotlin's too), whose
  `next()` throws `Error` as Kotlin's bubbles, while `nextSafe()` can't
  fail; a throwing body's factory doesn't fit a `SafeGenerator` place.
  A generator body may declare `throws Error`, which user code can't
  elsewhere, to pass any error on, as Kotlin's bodies pass bubbles.
  Every backend translates them. Test: settle/failures.cases, "a
  generator may throw any error, …" and "a generator that may throw is
  no SafeGenerator, …"; testkit spec failure-generators on every
  backend.
- **`Bubble` and `bubble()` are gone** (2026-11-27, stage 4 of replacing
  `Bubble`): one error mechanism, declared errors. Kotlin Temper 0.6
  code's `bubble()` and `throws Bubble` are undeclared names, each
  reported as "Bubble has not been declared; Bubble is gone: declare an
  error type and throw it (sealed interface MyError extends Error {};
  throws MyError; throw new MyError()), return null, or panic() for a
  bug" (or "bubble has …"), and nothing else is reported for that
  function's failures. No never-mix rules remain, and a call that may
  fail where it can't is reported wherever it is (no folding of calls
  of constants first, as Kotlin's). `orelse` and `catch` stay. Each
  backend has one error mechanism: Python `except` of failure classes,
  JS `instanceof`, Lua `temper.catch`, Java and C# a `catch` of the
  failure's class, Rust `temper_core::Error` as `Arc<dyn Failure>`, C++
  `Expected` alone. Test: settle/failures.cases, "Bubble and bubble()
  are gone, with a message on how to migrate".
- **Code with errors panics where it runs** (2026-11-27), in the
  interpreter (`Panicked: …`) and on every backend, so `orelse` doesn't
  catch it, where Kotlin's backends each did their own thing (below,
  "Code with errors"); the interpreter's own errors (a value of the
  wrong kind) panic too. Test: settle/names.cases, "an error panics where
  it runs, which orelse doesn't catch"; settle/types.cases, "a member
  that isn't there is an error that panics where it runs".
- **An `internal` visibility** (module or library) between `public` and
  `private`, so module functions can use a class's hidden parts. Design it
  from what JS can express (only public and `#private`). Not planned yet.
- **A test-only marker** (`@forTest`, name open) for helper
  declarations: the frontend reports an error if one is exported or used
  outside tests (test blocks, test modules, or other test-only
  declarations), so readers can see which helpers exist only for tests,
  and backends can leave them out of production output. Idea from the
  user, 2026-11-02; needs team agreement. Not planned yet.

Literate source:

- **Code in markdown block quotes is prose.** It can't work past one line
  in Kotlin anyway. Done.
  Test: temper-syntax/tests/edge-cases.cases, "blockquote with indented
  code then a lazy line, which crashed Kotlin".
- **Multi-quoted strings** follow clearer rules of our own. Done
  ("Lexer and parser", below, "Multi-quoted strings").
  Test: temper-syntax/tests/kotlin-lexer-tests.cases (the multi-quoted
  cases) and edge-cases.cases.
- **A warning for an indented code block that continues a list item.**
  CommonMark makes it prose, so code silently disappears (seen: a class
  lost its first lines). Not planned yet.

Backends, planned: no numbered names where visible (design.md, "Naming";
api-compat.md proposal 1); no required `init` in Rust and C++
(api-compat.md proposal 2; in Kotlin since #636, and in tempest since
2026-10-31); fun interfaces as named function types
(language.md, "Function types and fun interfaces"); tmpl independent of the
frontend (history/milestone-3.md, "tmpl: a separate schema crate"); value ranges
so Python and Lua skip 32-bit wrapping helpers ([typing.md](typing.md),
"What flow facts are used for").
- **Shared backend tests.** Kotlin's backend tests are separate per
  backend, so most inputs are checked on only some backends. One shared
  set of inputs with an expected output per backend, plus golden files of
  generated code for the functional tests, would catch more; tempest
  plans it that way ([testing-and-milestones.md](testing-and-milestones.md),
  "Backend tests, shared from the start").

## Bugs in Kotlin Temper

### Language and types

- **An undeclared name in a branch that never runs,** `if (DEBUG) {
  console.log("${missing}"); }` with `let DEBUG = false;`, isn't
  reported by Kotlin, which folds the `if` away before it looks names
  up; outside such a branch, `missing != null` folds to `true`, with no
  error either (2026-11-06), as its typer folds a null check of a name
  whose type can't be null, and an undeclared name's `Invalid` can't.
  Tempest reports both as errors (2026-11-10), as it does any error in
  a dead branch, and recovers by dropping the branch and folding the
  check (`== null` to `false`, `!= null` to `true`), so the code it
  generates past the errors runs as Kotlin's does, but it exits 1.
  Kotlin should report them.
  Test: settle/run.cases, "a dead branch's undeclared name errs, though
  Kotlin folds the branch away", and "an undeclared name compared with
  null errs, though Kotlin folds it silently".

- **`@auto("==")`'s `eq`** (2026-10-30) is generated as Kotlin's
  `AddAutoMembers` does, with its "multiple supertype(s)" error, but
  tempest doesn't check yet that the hierarchy is sealed (Kotlin's
  "unsealed subtype(s)" and "not sealed" errors), nor make one for a
  generic class, nor rename it past another member named `eq`
  ([milestone-4.md](history/milestone-4.md), "`@auto("==")` members
  generated").
  Test: settle/run.cases, "auto equality compares public fields", "auto
  equality from a sealed supertype", and "auto equality calls each
  field's own ==".

- **`x is D` for a type parameter's value,** `x: T` with `T extends
  Animal` and `D` a class extending `Animal`, is reported as between
  unrelated types, where `a is D` for `a: Animal` is a downcast. Tempest
  judges a type parameter's value by its bound, all that's known of it
  at run time, so this is a downcast, allowed, narrowing `x` to `D`; and
  `x is Int` for an unbounded `T` is the `@mayDowncastTo(false)` error,
  "from T", where Kotlin says unrelated ([typing.md](typing.md),
  "Run-time type checks").
  Test: settle/class-types.cases, "is and as checks of type parameters'
  values and generic types".

- **A function in a field, `obj.f(x)`,** fails ("incompatible with
  usage"); Kotlin's own `functions/constructor-callbacks` keeps its use
  inert. Tempest runs it (`call_method` in `interp.rs` falls back to the
  property) and types it as a call of the field's value
  (`tests/settle/class-types.cases`).
  Test: settle/class-types.cases, "a function in a field, called as a
  method"; settle/run.cases, "a functional interface, called through a
  field".
- **A trailing block placed past a skipped optional parameter** crashes
  Kotlin's typer (a `NullPointerException` in `Typer.typeCallByParts2`,
  on the null put in for the skipped parameter): `h(1) { … }`, for `let
  h(n: Int, s: String = "d", g: (fn (): Void)? = null)`, a method's
  too. Tempest passes the block as `g`, and `s` takes its default
  ([typing.md](typing.md), "Function literals and target typing";
  `tests/settle/run.cases`).
  Test: settle/run.cases, "a trailing block goes to the last function
  parameter" and "a method's trailing block goes where typing places
  it".
- **An empty body of a non-`Void` function,** `let f(): Int { }`, isn't
  reported, and calls then fail at run time. Tempest doesn't report it
  yet either (`typing.rs` skips `Void` results).
- **`assert(list == null)`** with a nullable list fails with "Cannot
  assign to Invalid from String", and `assert(a == b)` for a class's
  values without a `toString()`, as one with only an `@operator("==")`
  method, with "No member toString in C": the message `assert` makes
  interpolates both sides (`expected a == (${b}) not (${a})`). Tempest
  (2026-11-16) makes the plain message, `expected a == b`, where typing
  finds a side it can't show, so the test translates and runs on every
  backend.
  Test: repl/specials.cases, "a failing assert(a == b) shows the values
  it can, else says the plain message"; tempest's cli.rs,
  `test_a_library_on_each_backend` (`CLASS_ASSERTS`).
- **`==` or `<` on a class without an `@operator` method** reports
  "Actual arguments do not match signature: (Int32, Int32) -> Boolean
  expected [Int32, Int32], but got [P, P]", against the first builtin
  variant (temperlang/temper#508). Tempest says what's missing, in the
  words of temperlang/temper#515: "P does not support infix `==`; give
  it a method marked `@operator("==")`", naming the operator as written
  (`<`, not `<=>`) and the one to mark (2026-11-16).
  Test: settle/class-types.cases, "operators a class doesn't define name
  its type and the method to mark".
- **An interface's backed property** isn't reported, then fails at run
  time. Tempest reports it ([milestone-3.md](history/milestone-3.md), "Classes").
  Test: settle/classes.cases, "interfaces have no constructor parameters
  or backed properties".
- **A missing setter** is hidden by an implemented getter
  (`checkAllMethodsOverridden` groups by name only). Tempest reports it.
  Test: settle/classes.cases, "a concrete class implements what its
  supertypes leave abstract" (`P`).
- **`case` patterns** give unrelated type errors, then fail when run.
  Tempest says "Not supported yet"; they come with types (history/milestone-4.md).
  Test: settle/macros.cases, "when with case patterns, which need types,
  and invalid cases" (the message).
- **A function tag on a string with `:` statement lines** crashes the
  compiler on a `TODO`. Tempest says "Not supported yet"
  ([milestone-3.md](history/milestone-3.md), "Statements in strings").
  Test: settle/macros.cases, "a tag that names a class is an accumulator
  type" (the message).
- **Labelled `continue outer`** from a nested `for … of` fails at run
  time with no message. Tempest runs it.
  Test: settle/run.cases, "for of, with jumps (Kotlin fails continue
  outer at run time)".
- **Errors about `Invalid`** follow others since #500 (in Kotlin's stage
  tests' expectations): after "No declaration for what" or "Member ap …
  not publicly accessible", also "Expected subtype of String, but got
  Invalid", and "Void expressions cannot be used as values" becomes
  "Expected subtype of List<Void>, but got Invalid". Tempest reports
  the first error only, and keeps the Void one.
  Test: settle/names.cases, "undeclared names are errors, which fail
  when run"; settle/classes.cases, "private members aren't usable
  outside their class"; settle/types.cases, "Void expressions as
  values".
- **Nullable until narrowed:** typing uses types, never values, so
  evaluation never changes typing. Kotlin folds names and calls of
  constants while compiling and checks their values, so it accepts
  `take(c)` for `let c: String? = "q"`, `n + 1` for `let n: Int? = 2`,
  `s.isEmpty` for `let s: String? = "q"`, and `take(maybe(1))` where
  `maybe(1)` gives `"a"` (rejecting it where it gives null). Tempest
  reports each as it does a parameter of that type, until a null check,
  `??`, or early exit narrows it ([typing.md](typing.md), "Nullable
  until narrowed"). Before 2026-11-19 it accepted what Kotlin might
  fold, so values folded to null passed, then failed in translation or
  at run time. A parameter with a default takes any nullable value whose
  non-null type fits, since null selects the default on every backend:
  Kotlin takes `g(noInline())` (funtest `functions/defaulting`), which it
  might fold, but rejects `fallback(a)` for a parameter `a: String?`,
  which tempest accepts. The same goes for `is` and `as` checks and type
  arguments, since 2026-11-19: Kotlin judges `s is Int` for `let s:
  AnyValue = "a"` by the `String` it folds `s` to ("Runtime type check
  from String to Int32 can never succeed"), calls `n as Int` for `let n:
  Int? = 1` unnecessary, and passes `let s: String = id<Int>("a")`, a
  call of constants it folds to `"a"`; tempest judges the `AnyValue`
  (`@mayDowncastTo(false)`), says nothing of the cast (a failed `as`
  panics), and reports the argument and the assignment, as for non-constant values.
  Test: settle/flow.cases, "a nullable constant stays nullable until
  narrowed", "nullable locals stay nullable until narrowed" (`i3`, `i4`,
  `k6` to `k10`), and "nullable values where a non-null one is wanted"
  (`b7`); settle/types.cases, "a member of a nullable value is an error
  that panics where it runs" (`r11`) and "generic calls solved per call"
  (`c4`, `d4`); settle/class-types.cases, "is and as checks" (`g1` to
  `g6`).
- **A nullable function folded to null,** `let g: fn (Int): Boolean =
  h` for `let h: (fn (Int): Boolean)? = null`, is reported with internal
  names: "Expected subtype of Fn__1006<Int32, Boolean>, but got
  Fn__1006<Int32, Boolean>?". Tempest reports it with the types as
  written, "Cannot assign to fn (Int32): Boolean from (fn (Int32):
  Boolean)?", as for a parameter of that type.
  Test: settle/flow.cases, "nullable locals stay nullable until
  narrowed" (`n1`).
- **Builtins' errors:** `1 < 1.0` names `<=>`'s signature, `(Int32,
  Int32) -> Int32`; `console.log()` says "Expected 2", counting `this`;
  "Cannot bubble …" for `x as T` has an empty position; and a `fn` with
  no return type is checked for `1 / d` but not `bubble()`. Tempest
  copies the first and says "Expected 1"; the rest are moot, as a
  failed `as` and `1 / d` panic and `bubble()` is gone. Naming `<`'s own signature rather than `<=>`'s is arguable, for
  review.
  Test: settle/builtins.cases, "operators pick a variant by their
  arguments, in order" and "builtins with one signature";
  settle/class-types.cases, "as checks, which panic where they fail".
- **Classes' errors:** messages name classes by internal names, as
  `C__0` in "Cannot assign to String from C__0" and "No callee matches
  inputs [T__10] among [(A__6) -> Int32, …]", and a method call's arity
  error counts `this` ("Expected 2" for one parameter). Tempest names
  classes as written and doesn't count `this`
  ([milestone-4.md](history/milestone-4.md), classes). Arguable, kept for now:
  a method's or constructor's parameter with a default shows as
  nullable ("Expected subtype of String?, but got Int32"), since null
  means the default, while a function's shows as `String`.
  Test: settle/class-types.cases, "values of classes and interfaces" and
  "members read, called and assigned".
- **`==` in `when`:** `when (k) { k -> … }` on a class or interface
  without `@operator("==")` is reported spanning from the subject to the
  case, and `when (k) { 4 -> … }` at the subject; tempest reports both
  at the case, the value compared. Arguable, for
  review ([milestone-4.md](history/milestone-4.md), operators defined by
  methods).
  Test: settle/class-types.cases, "operators defined by methods" (`b2`,
  `b3`).
- **`is` checks' errors,** as "Unrelated types cannot be targeted with
  is or as runtime type checks: <[J]> from I" and "Runtime type check
  from Int32 to String can never succeed", are each reported twice: at
  the check, and again at an empty position at the end of the branch it
  narrows. Tempest reports each once, at the check
  ([milestone-4.md](history/milestone-4.md), "`is` and `as` checks").
  Test: settle/class-types.cases, "is and as checks, as Kotlin Temper
  reports them".
- **`as` checks' bubbling** is reported at odd positions: at an empty
  position at `x` for `x as Int` with `x: Int?`, at both that and the
  check for `i as D` with `i: I?`, and only at the check for `i as D`
  with `i: I`; also for an unnecessary `x as AnyValue`. In tempest a
  failed `as` panics, so there's nothing to report.
  Test: settle/class-types.cases, "as checks, which panic where they
  fail".
- **A user's `@connected` type:** since #633, user code may connect a
  concrete class (bare `@connected`, or with a list of backends), but
  not an interface ("At this time, users can connect only classes or
  top-level functions"); `@connected("X")` with a key, as core's, is an
  error there ("@connected expects a list of backend names", which
  tempest also reports, 2026-11-13, connecting it as without a list); and a
  connected class can be the target of `is` or `as` only from a sealed
  supertype ("Connected types cannot be targeted with is or as runtime
  type checks …"). Tempest reports the last for core's types too, in its
  own words ([typing.md](typing.md), "Run-time type checks"), and treats
  connected classes as ordinary ones otherwise. Since Kotlin's #642 (connected fallback types), a
  connected class may have a complete Temper implementation, a body for
  every method, which the interpreter, backends not in its
  `@connected([...])` list, and `--no-connected` use. Tempest matches
  in the interpreter, and reports Kotlin's errors: a partial
  implementation ("Method two of a @connected class needs a Temper
  body, since other methods have one, …"), a backend list without one
  ("… that lists backends needs a Temper body for the backends not
  listed"), and default arguments ("Method scale of a connected class
  can't have default arguments, …"), which tempest reported nowhere
  before; and for a function (2026-11-13), a list without a body ("A
  @connected function that lists backends needs a Temper body …"), at
  its name where Kotlin's position is empty, after its signature. Kotlin reports these only while generating code; tempest
  reports them while typing, in every module, skipping std's
  connected classes and core-style ones that connect members. A bodiless
  method of a user's connected class fails in the interpreter as "No
  connected implementation of Counter.add", where it said "Abstract
  method". Every backend reads a library's host files and chooses host
  code or the Temper implementation as Kotlin's `ConnectedChoice.kt`
  does, with `--no-connected`, and Java and C# leave out a replaced
  `Counter.java` or `Counter.cs`. Where a backend needs host code that
  isn't there, tempest says so, naming the file, where Kotlin leaves it
  to the target's compiler or the first call.
  Test: settle/classes.cases, "a connected class's Temper implementation
  must be complete", "a connected class without a Temper implementation
  runs only host code", and "a connected class's constructor can't have
  defaults either", and "connected functions that list backends need
  a body, as Kotlin's connected-backends"; each backend's
  `missing_host_code` and `kit::CONNECTED` runs, as is and under
  `--no-connected`.
- **Declaration errors' positions** (2026-11-13): tempest reports
  Kotlin's "Constructor input s specified but there is already a
  constructor" without the constructors' positions Kotlin appends ("at
  test/test.temper+687-698"), as its messages don't name places; the
  error is at the parameter, from its name. "Override has lower
  visibility than in I", for a property, or a getter overriding one, is
  at the member, where Kotlin's is at its `protected` or `private`.
  Test: settle/classes.cases, "constructors beside header parameters or
  initializers, as Kotlin's constructors-and-property-initialization";
  settle/class-types.cases, "a property narrower than what it
  overrides, as Kotlin's hide-override-property".
- **Several constructors** (2026-11-13): a class has one constructor,
  by a team decision, so tempest rejects each constructor after the
  first, "A class can have only one constructor; for other ways to make
  instances, use a static method or an object literal", and keeps going
  with the first. Kotlin accepts several,
  `class Thing { public constructor(hi: Int) {} public constructor(lo:
  Int) {} }`, and generates both (two `constructor`s in one JS class, a
  syntax error), reporting "More than one constructor needs to adopt
  initializer for property b" only when a field has an initializer;
  tempest's one error per extra constructor replaces that one.
  Test: settle/classes.cases, "a class has one constructor, by a team
  decision; Kotlin accepts several".
- **Connected wrappers' null defaults in JS:** Kotlin's wrapper for
  `@connected let f(s: String? = null)` passes `undefined` to host code
  for a left-out `s`, which strict `tsc` rejects against host code
  typed `string | null`; tempest passes `null`.
  Test: temper-be-js/tests/goldens/functions-connected.js, from funtest
  functions/connected, which the JS `runs` checks with `tsc`.
- **Bubbling in operators' arguments** depends on how they fit: Kotlin
  doesn't report a call that may bubble in an argument that doesn't fit
  the operator's signature (`1 == mk(n)`), unless the first argument
  fits no variant (`k == mk(n)`, `"a" - mi(n)`). Tempest, whose failures
  are declared, reports a call that may fail either way.
  Test: settle/builtins.cases, "what may fail in operators' arguments".
- **Bubbling calls of constants:** Kotlin folds a call of a function
  that `throws Bubble` when its arguments are constants, as `g(1)`,
  and reports "Cannot bubble …" only if the folded call bubbles; one
  that doesn't becomes its value. Tempest reports a call that may fail
  where it can't wherever it is (since 2026-11-27, when Bubble went).
  Test: settle/builtins.cases, "what may fail where it can't" and
  "what may fail: constructors that say so".
- **Overrides with the wrong signature** ("Type B has method f with
  signature …, but it should be …"), like Kotlin's other
  `TypeDeclChecker` errors, are reported only while generating code,
  which a module without exports skips, so its bad overrides pass.
  Tempest checks every module. A field that overrides a getter with the
  wrong type is reported at an empty position at its name; tempest
  spans the field.
  Test: settle/class-types.cases, "overrides' signatures".
- **"Void expressions cannot be used as values"** is reported at an
  empty position after a temporary Kotlin made, for `[b()]`, `b() ==
  b()` and `"${b()}"`; tempest reports it at the expression. For
  `b().toString()`, Kotlin says "No member toString in Void"; tempest
  says this at `b()` ([typing.md](typing.md), "`Void` as a value").
  Test: settle/types.cases, "Void expressions as values, as Kotlin
  Temper reports them".
- **Generic calls:** a call with type arguments whose arguments don't
  fit, as `id<String>(n)` for `n: Int` in a function, is also "Expected
  function type, but got Invalid" at the callee, which tempest doesn't
  say. `let xs: List<String> = wrap(1)` reports the argument ("Actual
  arguments do not match signature: … expected [String], but got
  [Int32]"), solving from the type wanted first, where `let c: String =
  id(1)` reports the assignment; tempest matches both
  ([typing.md](typing.md), "Generic calls"). A call of constants Kotlin
  folds first, as `let s: String = id<Int>("a")`, is under "Nullable
  until narrowed", above. A nullable constant Kotlin folds to null, as a
  top-level `let d: Dog? = null`, passed as `id(d)` for `id<T extends
  Animal>`, is "Expected subtype of Animal, but got Animal?" at the
  argument; tempest reports it as it does any `Dog?`, "Type formal <T
  extends Animal> cannot bind to Dog? …", at the call, as Kotlin does in
  a function.
- **A generic call that's an argument** isn't checked against the
  parameter: `takeLS(wrap(1))`, for `takeLS(xs: List<String>)` and
  `wrap<T>(x: T): List<T>`, passes, where `let xs: List<String> =
  wrap(1)` is an error. Tempest reports it, "Expected subtype of
  List<String>, but got List<Int32>" ([milestone-4.md](history/milestone-4.md),
  "Generic classes and methods").
  Test: settle/types.cases, "generic calls solved from the type wanted"
  (`takeLS`).
- **A method's explicit type arguments** fail: `o.first<String>(ss)`,
  for `first<T>(items: List<T>): T`, is "Member first defined in Order
  incompatible with usage", then "Expected function type, but got
  Invalid", even where they fit, as is a builtin one's,
  `ls.map<String>(toStr)` ("Member map defined in List | Listed …").
  Tempest binds them as written, as for a function's.
  Test: settle/types.cases, "generic classes and methods"
  (`o.first<String>(ss)`).
- **A non-generic class's generic supertype** doesn't bind a generic
  call's type parameter: for `class StrHolder extends Holder<String>`
  and `unwrap<T>(x: Holder<T>): T`, `unwrap(sh)` is "Actual arguments do
  not match signature: … expected [Holder<AnyValue>], but got
  [StrHolder]", though a `GenHolder<String>` (`class GenHolder<U>
  extends Holder<U>`) binds `T` to `String`. Tempest binds both, so
  `let n: Int = unwrap(sh)` is "Cannot assign to Int32 from String"
  ([milestone-4.md](history/milestone-4.md), "Users' generic supertypes").
  Test: settle/types.cases, "users' generic supertypes".
- **A narrowed use's type error,** as `let j: J = i` in `if (i is D) {
  … }`, is reported at an empty position at the end of the branch.
  Tempest reports it at the use.
  Test: settle/types.cases, "where Kotlin Temper's errors are missing,
  misplaced, or wrong" (`n1`).
- **An `if` value that doesn't fit,** as `let s: String = if (c) { 1 }
  else { return -1; }`, is "Cannot assign to String from Int32" at an
  empty position at the end of the first branch. Tempest reports it at
  the `if` ([milestone-4.md](history/milestone-4.md), "Every expression typed,
  first part"). Where an `if`'s or `orelse`'s branches have no common
  type, as `let y: Boolean = p() orelse false` for `p(): Int64`, both
  check each branch on its own, with the same message, but Kotlin
  reports each at an empty position at the branch's end, and tempest at
  the branch.
  Test: settle/types.cases, "where Kotlin Temper's errors are missing,
  misplaced, or wrong" (`s1`), and "if and orelse values whose branches
  have no common type".
- **`m is MapBuilder`** for `m: Map<Int32, String>`, a check that can
  never succeed, is "Could not infer type actuals for (AnyValue?, Type)
  -> Boolean given [Map<Int32, String>, Type] in context unknown", a
  solver's failure, and fails to translate. Tempest says "Runtime type
  check from Map<Int32, String> to MapBuilder can never succeed", at the
  same span.
  Test: settle/run.cases, "Map, MapBuilder and Pair".
- **A member of a nullable value** gets an odd message: `xs.length`
  for `xs: List<Int>?` is "No member length in AnyValue", and `c.n` for
  `c: Counter?` "No member n in AnyValue" (or "No type for subject of
  .n"). Tempest names the value's type, "No member n in Counter?", at
  the same span (the member, not the call), once per chain, and the
  access fails where it runs. Kotlin doesn't report a value it works
  out while compiling, as `s.isEmpty` for `let s: String? = "q"`;
  tempest does ("Nullable until narrowed"). Tempest's null
  checks narrow more ("Flow facts"), so `if (c == null) { return 0 };
  c.n` is accepted. `a?.m` holds a `var` `a` in a temporary, as Kotlin
  does, so `g?.next()` for `var g: G?` is accepted.
  Test: settle/types.cases, "a member of a nullable value is an error
  that fails where it runs", and settle/flow.cases, "null checks let a
  nullable value's members be used".
- **A list literal with a null or nullable element** is rejected:
  `[1, null]` is "Expected subtype of Int32, but got Int32?", even
  where a `List<Int?>` is wanted, and `[n, 1]` for `n: Int?` there is
  "Actual arguments do not match signature". Tempest accepts both, as
  `List<Int32?>`s. A list literal as a function's argument, as `g([1])`
  for `g(xs: List<String>)`, isn't checked, though a constructor's is;
  tempest reports it. And an inner list typed from the type wanted gets
  odd errors: `[[], [1]]` where a `List<List<String>>` is wanted blames
  the `[]` ([milestone-4.md](history/milestone-4.md), "List literals and target
  typing").
  Test: settle/types.cases, "list literals' types" and "list literals
  typed from the type wanted".
- **Function literals' errors** ([typing.md](typing.md), "Function
  literals and target typing"): a block's result that isn't `Void`
  where `Void` is wanted is reported for users' functions and
  `Mapped.forEach`, but not for builtin methods with an
  `@inlineUnrealizedGoal` parameter, as `Listed.forEach`, nor users'
  methods with one; tempest reports none of them yet. A null, `Void`, or
  `panic()` result where an `Int` is wanted is "but got Never<Empty>?",
  "but got Void" (with "Void expressions cannot be used as values"), or
  "but got Top", so tempest doesn't report those yet. Some say "No type
  for expr" instead of what's wrong, as a literal whose declared result
  isn't the one wanted where that's a `String` or nullable (`chk5(true) {
  (x): Int => 1 }`, for `chk5(c: Boolean, m: fn (Int): String)`), a list
  literal result that doesn't fit, or `wiI { x => "" }`, for `wiI(m: fn
  (Int): Int): Int { 1 }`; tempest says what's wrong. And valid code
  fails: `opts(fn (x: Int): Int { x }) { x => x.length }`, for `opts(f:
  fn (Int): Int, g: fn (String): String)`, is "No type for expr" and "No
  type for subject of .length"; tempest accepts it. A `return panic()`
  in a literal is "but got Top", and `return null` "but got
  Never<Empty>?"; tempest accepts the first and says "but got Int32?"
  for the second.
  Test: settle/types.cases, "function literals typed from the type
  wanted", "returns and bubbling in function literals", and "where
  Kotlin Temper's errors are missing, misplaced, or wrong" (`opts`,
  `chk5`).
- **`return` in a block lambda** is an "unrealized goal" in Kotlin, a
  jump to the function around the lambda, which works only where the
  lambda is inlined, at an `@inlineUnrealizedGoal` parameter: so
  `ls.forEach { x => if (x == 1) { return 5; } }; 3` in a function
  returns 5, and `return "a"` there is "Cannot assign to Int32 from
  String" where that function returns an `Int`. Elsewhere, as in `apply
  { x => return 2; }`, it's "Expected function type, but got Function",
  with "Expected subtype of Int32, but got Void" and "Void expressions
  cannot be used as values" (or "Expected function type, but got Int32"
  where the lambda declares its result). Checked with `temper build` and
  `temper run -b js`, 2026-10-29. Tempest returns from the block lambda,
  as from a `fn` literal, so the `forEach` above returns 3, and checks
  the value against the lambda's result: a difference at run time.
  Decided 2026-10-29: match Kotlin at `@inlineUnrealizedGoal` parameters,
  by an equivalent mechanism, and give a clear error elsewhere; not done
  yet ([milestone-4.md](history/milestone-4.md), "Order and decisions";
  [typing.md](typing.md), "Function literals and target typing"). A `return` in a `fn` literal leaves the literal in both.
- **Function arguments go unchecked** where Kotlin can run the call
  while compiling, so `wiI(str)` or `wiI(fn (x: Int): String { "" })`,
  for `wiI` above, which never calls its argument, passes; and in
  `assert(true) { (): Int => 1 }` as the last expression of a `test`
  block (with a `;` after it, it's reported). Tempest reports each, as
  Kotlin does for a function that calls its argument.
  Test: settle/types.cases, "where Kotlin Temper's errors are missing,
  misplaced, or wrong" (`wiI`).
- **A function where a generic `@fun` interface is wanted** in a
  generic call fails: `applyM(1, isPos)`, for `let applyM<A, B>(x: A,
  m: Mapper<A, B>): B` and `@fun interface Mapper<A, B>(a: A): B;`, is
  "Expected function type, but got Invalid" and "Actual arguments do not
  match signature: … expected [Int32, Mapper__3<Int32, AnyValue>], but
  got [Int32, Fn__1006<Int32, Boolean>]"; a trailing block there works.
  And a function literal in a list literal where a `List<Mapper<Int,
  Boolean>>` is wanted isn't typed from it ("Explicit return type
  required"). Checked with `temper build`, 2026-10-29. Tempest accepts
  both ([typing.md](typing.md), "Fun interfaces").
  Test: settle/types.cases, "@fun interfaces as the function types they
  stand for"; settle/run.cases, "a trailing block goes to a @fun
  interface's parameter".
- **`@fun` interfaces' rules are documented but not checked:**
  `FunDecorator.kt` says one may extend only `AnyValue`, declare only
  `apply`, and not be used with `is` or `as`, but `temper build`
  accepts each (checked 2026-10-29), and also a `Pred` where a `Check`
  is wanted. Tempest reports them (above, "Named `@fun` interfaces are
  nominal").
  Test: settle/types.cases, "@fun interfaces keep Kotlin Temper's rules
  for them" and "named @fun interfaces are nominal".
- **Method references fail:** a method read as a value, `k.isBig` or a
  bare `isBig` in its class, is "Member isBig defined in K incompatible
  with usage" (checked 2026-10-29). Tempest types it as the method's
  function type, or as the `@fun` interface wanted, as Java's method
  references.
  Test: settle/types.cases, "named @fun interfaces are nominal"
  (`k.isBig`, `isBig`).
- **Positions and wording:** a bare `return` in a non-`Void` function is
  reported past the function's end (tempest: at the `return`); a cycle of
  inferred types says "Cannot assign to String from Top" (tempest: no
  error yet; it should say the type can't be inferred).
  Test: settle/types.cases, "function results" (`nothing`), for the
  first.

- **An override of an `@overload` method** answers to the overloaded
  name, as an escaper's `applyString`, overriding its interface's
  `@overload("apply")` method without repeating the decorator, does to
  `escaper.apply(x)`. Tempest saw only the methods marked themselves,
  so `SomeEscaper.instance.apply(x)` was "No member apply"; now a
  method overriding a marked one counts too (fixed 2026-11-10).
  Test: programs/content-tags (`Both`), settle/autoescape.cases.

- **Assigning a static member,** even a `static var` (which, as in
  Kotlin, is a static `let` in tmpl), is "Cannot assign to static
  member count of C; static members are only initialized at their
  declaration" in both. Kotlin's program runs on with the value
  unchanged; tempest's fails at the assignment, as at any error. A bare
  `count = 1` in `C` gets that error besides "Type name required for
  accessing static member" (2026-11-13).
  Test: settle/classes.cases, "static members can't be assigned, even
  a `var`, as Kotlin reports".

- **Definite assignment of locals** (2026-11-13): a `let` or `var`
  read where some path doesn't assign it is "x is not initialized along
  branches" in both; Kotlin adds where the paths that miss it are, and
  reports every such read, where tempest reports one per path. Tempest
  also checks a `for … of` body's reads of its function's locals, which
  Kotlin, making the body a function, leaves unchecked, as it does any
  function's reads of its enclosing function's locals (both do that);
  tmpl's loop would read an unassigned local there. A top-level cycle
  (`let a = b + 1; let b = a + 1;`) is the same error, where tempest
  failed at run time before.
  Test: settle/flow.cases, "locals read before they're assigned on some
  path" and "locals assigned on every path".
- **Exports that expose non-exported types** (2026-11-13) are "Export
  depends publicly on non-exported symbol Hidden" in both, for the same
  parts of a signature, at each use of the type's name, or an inferred
  type's declaration's name. Tempest's error is type-only, so the code
  runs and is written as it is. Neither checks a private constructor's
  parameters, so a non-exported parameter type there, which Kotlin's C#
  makes a public constructor's (CS0051), isn't caught.
  Test: settle/classes.cases, "exports that expose what isn't exported".
- **A setter without a getter** (2026-11-13) can be assigned, as `c.p =
  5`, and the assignment used as a value, as `c.p = c.p = v`, in both;
  tempest said "No member p in C" before. As a value, tempest translates
  it only where the object is a name that isn't assigned again, or
  `this` ("not yet in tmpl" otherwise).
  Test: settle/classes.cases, "a setter without a getter is assigned";
  programs/classes (`added`), on every backend.
- **Instance extensions** (`@extension("name")`, 2026-11-13) answer to
  `x.name(…)` where visible, declared or imported, in both, as a plain
  call of the function with `x` first; a member of `x`'s type that fits
  the arguments wins, as Kotlin prefers one over an extension that fits
  as well. Kotlin types members and extensions as one overload set;
  tempest takes a fitting member first, then the most specific fitting
  extension, then the nearest declared among equals, as Kotlin lists an
  inner block's first. Where two fit as well in one scope, Kotlin
  quietly calls the first; tempest reports "No callee matches", as for
  several that don't fit. `x.name` read alone is "No member" in both;
  tempest hid that error where an extension was visible before.
  `x.name<T>(…)` with explicit type arguments isn't an extension call
  in tempest yet. Core's generic list methods (`Listed.map<O>` and its
  kin) can become such extensions later ([typing.md](typing.md), "To
  explore later"); they're still core's methods.
  Test: settle/run.cases, "instance extensions, as Kotlin Temper's tests
  use them"; settle/types.cases, "instance extensions' errors, as
  Kotlin Temper reports them"; programs/functions and programs/modules,
  on every backend.
- **`Never<T>`** (2026-11-17), as core declares it, is a subtype only
  of itself, of its `T` and what `T` is, and of `Never<U>` for such a
  `U`. Both check it so: `if (b) { "x" } else { stopInt() }` for
  `stopInt(): Never<Int>` is "Cannot assign to String from Never<Int32>"
  where a `String` is wanted, and a `Never<Void>` value is shown as
  `Void`, as at the end of an `Int` function's body, in `p() orelse
  stop()`, `s ?? stop()`, or a `when` branch. Both require an override to
  declare the result it overrides exactly, so neither a `Never<Int>`
  method for an `Int` one nor the reverse (Kotlin shows the `Never<Int>`
  as "Never<Void>"), and both report a `Never<Void>` function whose body
  completes ("Cannot assign to Never<Void> from Void"; Kotlin's says
  "Never"). A call of such a function ends its path, as `panic()`'s
  does: code after it is unreachable, a local assigned on the other
  paths is assigned after it, and a null check that leaves through one
  narrows; Kotlin checks no definite assignment in these cases, and
  narrows after none of them, nor after `panic()` or `return`. Kotlin's
  backends translate a `Never<T>` result to a bottom type or `void`:
  C++, C#, and Java `void`, Python `NoReturn`, JS `never`, and Rust `!`,
  where it crashes ("Cannot translate Never"); so C++'s `return
  stopInt()` doesn't compile, and Kotlin's other backends run such code
  only where it folds the call away. Tempest's tmpl keeps `Never<T>`
  ([tmpl/types.md](tmpl/types.md#never)): Python (`NoReturn`) and JS
  (`never`) keep it as the result of a function or method that overrides
  nothing and that nothing overrides, and every other place and backend
  has `T`, core's fallback, with a `panic()` after a call that ends its
  block, so each compiler sees that control stops there. Kotlin also
  accepts a `Never<String>` returned where a `Never<Int>` is declared,
  which tempest reports, and rejects `Never` alone only where it's used
  ("No callee matches inputs [] among [() -> Never<Void>, …]" at a
  `panic()` in its body, "Cannot translate Never"); tempest reports it
  where it's written, "Never needs the type it stands for, as Never<Int>
  or Never<Void>", its type then unknown.
  Test: settle/types.cases, "Never<T> stands for its T" and "Never<T>
  results in overrides, and Never alone"; settle/flow.cases, "calls that
  never return end their paths, as panic() does"; temper-tmpl's
  tests/nevers.rs; the funtest control-flow/bubble ("Helpers that never
  return") and programs/classes ("Methods that never return"), on every
  backend.
- **An `await` that may fail where it can't** (2026-11-18), as in an
  `async` block without `orelse`, is "Cannot bubble from a function
  without Bubble in its return type" at the `await` in both (tempest's
  says "Cannot throw BrokenPromise …" since 2026-11-26), and so is
  `bubble()` or any other call that may bubble in a generator that
  doesn't say `throws Bubble`. Kotlin then runs the code as written (JS:
  an unhandled rejection; Python: `RuntimeError`); tempest's `await`
  panics with the message where it's reached, on the interpreter and
  every backend, as other code that may fail where it can't does, even
  when the promise is kept. Tempest also checks named generator
  functions, which Kotlin lacks.
  Test: settle/run.cases, "an await that nothing handles" and "an
  await that something handles"; to_tmpl.rs,
  `unhandled_awaits_fail_where_reached`.

### Interpreter, core and std

- **`Float64.toString` cuts at 16 digits** in the interpreter
  (`FormatDouble.kt`), so `0.30000000000000004` prints as
  `0.3000000000000000`, which doesn't read back; the Python and JS
  runtimes round-trip. Tempest matches the interpreter for now
  ([milestone-3.md](history/milestone-3.md), "Numbers"); it should round-trip.
- **C++ `Float64.toString`** prints `%g`-style at the shortest
  precision that reads back, so `-1500.0` is `-1.5e+03` and `1e6`
  `1.0e+06`. Tempest's C++ runtime writes Java's form, as the
  interpreter and the Java runtime do: plain for 1e-3 <= |x| < 1e7,
  `e+N` exponents otherwise, and the fewest digits that read back, but
  at least two (`4.9e-324`). The other runtimes still differ from each
  other where exponents start (C# from 1e15, Python and Lua from 1e16,
  JS from 1e21, and below 1e-3 to 1e-6), C# pads exponents (`1.0e-05`),
  Rust's drops the `.0` below 1 (`1e-5`), and Lua's keeps 16 digits.
  Test: temper-be-cpp/tests/functional.rs, `FLOAT_FORMAT`; funtest
  types/float/basics on every backend, where they all agree.
- **`String.toFloat64`** takes JVM syntax beyond its docs (`1f`, `-.5`,
  `0x1p3`, `02`, `+Infinity`). Tempest follows the docs.
  Test: settle/run.cases, "Float64 formatting, order and methods, as
  Kotlin Temper's".
- **`String.toInt32` and `toInt64`** take more than the documented JSON
  integers, differently per backend. Tempest takes only ASCII digits, but
  still a `+` and surrounding whitespace, as Kotlin's interpreter does.
  Test: settle/run.cases, "strings and string indices, as Kotlin
  Temper's".
- **`s[i]` past the end** bubbles in Kotlin's interpreter, which
  its signature doesn't declare; the docs and runtimes panic, as tempest
  does.
  Test: settle/run.cases, "a string index past the end panics, as
  String.get's docs say".
- **`Int.toString(radix)`** fails outside 2 to 36, but core declares no
  `throws Bubble`. Tempest panics there (the interpreter, and the
  runtimes' helpers), as core declares no failure (since stage 4 of
  replacing Bubble, 2026-11-27).
- **Deep recursion overflows the JVM's stack** in the interpreter:
  `temper test -b interp` dies with a `StackOverflowError` between 150
  and 200 nested calls of `let down(n: Int): Int { … down(n + 1) + 0 }`
  (2026-11-06). Tempest allows 2000 (500 in wasm, where the engine's
  stack is smaller), as only calls take native stack, and stops a
  runaway one cleanly ("calls nested too deeply"). Kelpto's tests need
  about 450.
  Test: settle/run.cases, "deep recursion that ends runs" and "deep
  recursion stops".
- **A failing `async` block is silent** in the interpreter
  (`interpretAndWaitForAsyncTasksToSettle` checks the wrong result).
  Tempest reports the first failure.
  Test: settle/run.cases, "a failing async block doesn't stop the
  others".
- **temper-regex-parser drops the code before a trailing dash** in a set:
  `/[a-]/` parses as `[-]`. Tempest keeps both (`regex.rs`).
  Test: temper-frontend/src/regex.rs, `parses_the_dialect`.
- **std `net`'s backends:** Rust's `send_request` and Java's
  `stdNetSend` block a turn (tempest's don't); JS's JSDoc rejects std's
  method string; Lua calls `temper.sendrequest`, which its runtime
  lacks (tempest's sends through LuaSocket). C#'s `ContentType` reads the response's headers, not its
  content's, so it throws, and Python's raises where there's no
  `Content-Type`, where std says null; tempest keeps those two for now.
  Test: funtests `types/netresponse` and `netresponse-not-found` (Rust,
  Java, strict tsc, and LuaLS); none reads `contentType`.

### Lexer and parser

Details below, in "Kotlin bugs found". Tempest fixes:
crashes on several malformed inputs (`Parse.kt`, `StringFixer`,
`buildTaggedRange`, `Lift.kt`), stack overflow at about 300 nested calls,
exponential time on nesting, odd spans from `Tree.replace`, an unclosed
fence at the end of a file losing its last line, and `"\` splitting a
surrogate pair. It copies, until the comparisons with Kotlin end, two
that affect only broken input: close brackets that can't look past
others, and semicolon insertion counting a `{` twice.
Test: the crashes in temper-syntax/tests/parse/edge-cases.cases and
ast/edge-cases.cases ("… which crashes Kotlin"), checked again under
edits by `mutated_inputs_keep_parser_properties`; the stack overflow and
exponential time in parse.rs, `deep_and_nested_inputs_parse`; the
unclosed fence and `"\` in temper-syntax/tests/edge-cases.cases.

**Bracket recovery** (ours, 2026-11-06; `temper-syntax/src/balance.rs`).
In Kotlin, one missing `)` or `]`, a stray `}`, or `a<b` without spaces
makes the rest of the file one "Expected a TopLevel here", and a missing
`}` gives only knock-on errors. Tempest, only when brackets don't
balance, closes an unclosed bracket at the nearest plausible place (a
line at or left of the opener's, a `{` that starts a body, the last `:`
of `f(x: Int: Int {`, or a `;` outside `for (...)`), drops an unmatched
closer, matches a `}` by indentation when an inner `}` is missing, and
treats a `<` still open at a close bracket, `;`, or `{` as less-than.
Each is an error at the gap: "Expected `)` to close the `(` on line N",
Kotlin's lexer wording "Close bracket matches no open bracket", or
"Expected `>` to close `<`, or did you mean `a < b`?", so code Kotlin
rejects still fails, but later declarations parse and type-check. A `<`
open at the end of input stays an angle, as in Kotlin. Balanced input
parses exactly as before (check_parse.py and check_ast.py match);
fuzzed inputs that recover are the `brackets` deviation. Kotlin also
reports "Close bracket matches no open bracket" for a `{` left open at
the end; tempest says which `}` is missing instead. Recommended for
Kotlin's parser too.
Test: temper-frontend/tests/settle.rs, `bracket_recovery_stays_local`;
`balance` unit tests; temper-wasm `compiles_with_narrow_syntax_errors`.

**Missing operators** (ours, 2026-11-09; `temper-syntax/src/grammar.rs`,
`operand`). Two operands side by side with no operator between them, as
`let part = cents  100;` for `cents % 100`, `a b`, or `f() g()`, make
Kotlin drop the whole statement: "Expected a TopLevel here" over the
`let` or assignment, then "No declaration for part" and type errors at
every later use (5 errors for the playground's case). In an argument,
a condition (`if (a b)`), or after `return`, Kotlin keeps the statement
but reports "Expected a Argument here" over both operands. Tempest, in
a `let`'s initializer, an assignment's right side, an argument, and
after `return`, `throw`, `yield`, or `await`, keeps the first operand
and makes the rest one error at the stray one: "Expected an operator
or `;` before `100`" (`,` in an argument). The value is a block of the
error then the first operand, so it fails where it runs but has the
first's type: `part` is declared, as an `Int`, and later uses are no
errors. tmpl writes a `let` of it as the error, as for any `let` of an
error, now at the top level too: there it was written as
`let x = return Err(...)` in Rust, which didn't build, and after any
top-level error later `let`s are now declared but not initialized, as
later statements were already left out. Not after `a<b, c>` (`or(a< 2, a > 0)`), where `<` and `>`
were likely comparisons, nor in a `let`'s name or a formal's
(`let f(x Int)` still loses `f`, as in Kotlin). Correct input parses
exactly as before (check_ast.py: the only new differences are 81 stage
test dumps under `expect/`, the `missing-operator` deviation).
Recommended for Kotlin's grammar too.
Test: temper-frontend/tests/settle.rs,
`missing_operator_keeps_the_statement`; temper-syntax
ast/edge-cases.cases, "missing operator ..."; temper-wasm
`compiles_with_a_missing_operator`; tempest/tests/cli.rs,
`missing_operator_is_one_error`; testkit `errors` (`dropped`) on every
backend.

#### Deliberate deviations in the lexer and parser

Each has cases in temper-syntax's `tests/*.cases` or `tests/parse/*.cases`,
and is listed in `compat/deviations.py`, which the comparison scripts use
to report those inputs as expected.

- **Code in markdown block quotes is prose,** like an `inert` block.
  Kotlin counted it as code only because commonmark visits code blocks at
  any depth, and it couldn't work past one line, since the `>` markers on
  later lines lex as operators. Nothing uses it. Code in list items stays
  code. Kotlin agrees since #632: with its CLI built from main (0db80cc8),
  the three cases that showed it (`edge-cases.cases`) match in
  `check_lex.py`, `check_parse.py`, and `check_ast.py`, so they're no
  longer listed (2026-10-31); `deviations.py` still excuses fuzzed
  inputs whose differences start in a block quote, until a fuzz run
  shows Kotlin agrees there too.
- **Multi-quoted strings** (`tests/parse/multi-quoted.cases`):
  - Blocks a scriptlet line leaves open are kept per string, and come back
    in order at the next scriptlet line. Kotlin reverses them, and its stash
    outlives the string, so a later string's scriptlet can reopen another
    string's `{`.
  - A string that ends right after a scriptlet line closes what that line
    left open. Kotlin closes only the innermost, so the string goes on and
    swallows the code after it.
  - A `${...}` on a scriptlet line is code, and the line stays a scriptlet
    line after it. Kotlin turns the rest of the line into content, and its
    adapter treats the `${...}` as a string hole.
  - A single-line string left open at a scriptlet line's end closes there,
    and the next line is read for its margin. Kotlin reads it as code.
  - `"""` on a scriptlet line is an error and opens nothing. Kotlin opens a
    nested string anyway, which takes the following lines.
  - A scriptlet line's last token is kept. Kotlin drops it, meaning to drop
    the line break, so a scriptlet at the end of input loses its last token
    (Kotlin's own test `stringsOnStringTemplateLine` expects the `}` gone).
  - A content part after a scriptlet statement starts a new statement (a
    `;` goes between, unless one is there or after `{` or `}`). Kotlin
    juxtaposes them.
  - The `}` around a string with scriptlets doesn't start a call join, as
    the `)` around other strings doesn't. Kotlin joins the next line's
    word into a call.
  - A close quote closes the innermost string past any operators without
    open brackets. Kotlin looks only one level down from a leaf, so a
    string ending in a scriptlet expression like `: w` takes the quote into
    `w`.
- **Brackets that don't balance are recovered from locally** (`balance`):
  closers inserted, unmatched ones dropped, and a `<` left open before a
  close bracket, `;`, or `{` read as less-than, each with an error
  ([kotlin-differences.md](kotlin-differences.md), "Bracket recovery").
  Balanced input is untouched; `deviations.py` excuses inputs whose
  output shows recovery (`B` lines, or its diagnostics), as `brackets`.
- **Operands with no operator between them keep their statement**
  (`operand`): the first operand is kept and the rest are one
  `MissingOperator` error ("Missing operators", above). `deviations.py`
  excuses inputs whose output shows one where Kotlin's has an error, as
  `missing-operator`.
- **`${` nests inside a hole,** as anywhere else in code. Kotlin makes the
  `$` an error and the `{` a block.
- **Spans cover all of a node's parts.** Kotlin leaves operands adopted by a
  separator out of its span, which can shift error positions, and gives hex
  digits joined across `${}` in `\u{...}` the first part's position. The
  grammar step's positions come from these spans, so its trees differ in
  positions too (12 corpus and case inputs), and once in structure: a
  comment inside an adopted operand, as in `n s //c` then `public e,`,
  is found before the statement in Kotlin, which then gets a comment call
  and a block, but not in ours.
- **Spans of rewritten call joins** cover what they wrap: the delegating
  function, its call and the temporary name used in it span the children
  after the join's symbol. Kotlin's `Tree.replace` passes an inclusive end
  to `spanningPosition`, which takes an exclusive one, so its function
  spans all but the last of them (just the start of a lone block) and the
  call and name get odd spans from that. `check_ast.py --module` masks
  those three spans; the declaration's are compared.
- **`return`, `throw`, and `yield` can be the operand an operator
  lacks** (`jump-operand`, `tests/parse/edge-cases.cases`): `x ?? return
  null`, `f() orelse return`, `a || return b`, and `f(a, return b)`
  parse with the jump as that operand. Kotlin, whose precedence puts
  them below everything but `;`, closes the operator first and reports
  too few operands, so only inputs that are errors there differ. The
  jump's own operand is what it takes anywhere: the rest of the
  statement or bracket, but for a comma (`jump-comma`): `f(return a,
  b)`, `return a, b`, and `[y ?? return 0, 1]` are errors in both, but
  where Kotlin's jump takes the comma list and finds no argument in it,
  ours ends before the comma, as if in parentheses, and reports the
  comma (`JumpBeforeComma`), so the rest is still built. With
  parentheses, as `x ?? (return null)`, both already agreed. `return`, `break`, and
  `continue` as values type as `Never`, fitting any type; Kotlin has no
  type for them, weaving them into its control flow before typing, which
  comes to the same where it translates them.
- **Declared failures' syntax** (`failures`, RFC 164, ours,
  2026-11-24; `tests/parse/failures.cases`, `tests/ast/failures.cases`):
  `try` is a prefix operator (just above `orelse`) and `catch` an infix
  one (`orelse`'s neighbor, left-associative), so neither is a call
  joining word any more: Kotlin's own cases `tryWithValue`,
  `tryWithCatchAndFinally`, `colonsAroundTry`, and `minimalTryCatch`
  parse differently, and `try`, `throw`, and `catch` can't be names
  (`throw` couldn't in Kotlin either). `x catch (e) { … }` becomes
  `catch(x, ^e, fn { … })` and `error case N(…)` `#errorCase \word ^N
  …`, which Kotlin can't parse. The frontend types them (chunk 4,
  `tests/settle/failures.cases`), with core's `Error` root, which
  Kotlin's core lacks, and reports `throws X` for a non-failure `X`,
  which Kotlin takes as `throws Bubble`; the interpreter runs them, and
  every backend translates them. `deviations.py` excuses fuzzed inputs whose
  differences start at one of these words.
- **Failure parameters** (RFC 164, ours, 2026-11-26; chunk 14,
  `tests/settle/failures.cases`): a type parameter after `throws` is
  bound by `Error` and solved per call, the join where arguments differ,
  and core's `Listed.map`, `filter`, `reduce`, `reduceFrom`, `join`,
  `sorted`, `List.forEach`, `String.forEach`, `ListBuilder.sort`, and
  `Mapped`'s `forEach`, `toListWith`, and `toListBuilderWith` are
  generic on their callbacks' failures, where Kotlin's take callbacks that can't fail (or
  `throws Bubble` for all). So a callback of the wrong type to `filter`
  is reported as a generic call's mismatched arguments ("Actual
  arguments do not match signature: <E extends Error>…"), as `map`'s
  already was.
- **Operands nested over 1000 deep are an error** (`NestedTooDeeply`),
  where Kotlin overflows its stack, already at about 300 nested calls or
  a 1000-term `a+a+...`.
- **An illegal assignment isn't performed** (a `let` or non-`var` field
  assigned again): it's reported, and its value stands in for it, where
  Kotlin fails at run time.
- **Named `@fun` interfaces are nominal** (`tests/settle/types.cases`):
  a `Pred` isn't a `Check` even where their signatures match, with an
  error suggesting the adapter; a function literal that initializes a
  `let` without a type is an error; and Kotlin's documented rules for
  declaring one, and against `is` and `as` on one, are checked. Kotlin
  accepts all of these. Newly rejected in the sources scanned: only the
  stage test `syntax-macro/let-of-fn` (two literals), already an error
  there for their missing return types (typing.md, "Fun interfaces").
- **NFKC, as Kotlin:** words and quoted names not in Unicode NFKC are
  errors (`temper_syntax::nfkc`, with tables generated from the JDK by
  `compat/gen_nfkc.py`, Unicode 15.0.0 for Java 21). Kotlin's lexer only
  checks words with a code point past Latin-1, so `ªb` (NFKC `ab`) passes
  and is a different name from `ab`; we match that, though it looks
  accidental. Quoted names are always checked.

#### Kotlin bugs found

Copied for now, to keep comparisons quiet, since they only affect broken
input; to fix when the comparisons end:

- A close bracket only closes the innermost open bracket. Kotlin means to
  look past others for closers other than `>` (`isAmbiguousCloser` in
  `BracketPartners.kt`), but its check matches every closer.
- Semicolon insertion counts a `{` twice when it inserts a `;` before it
  (`AutomaticSemicolonInserter` reprocesses the pushed-back token), so
  later `}`s may be taken for the end of a `${` hole.

Crashes, which we don't copy (inputs in the fuzzers' `found/` directories):

- `Parse.kt` throws `IndexOutOfBoundsException` on input starting with `,`
  or `|`: the follower check reads `child(-1)` of the empty root.
- `StringFixer.fixupCollectedMq` throws on a multi-quoted string whose
  close it never sees, as in markdown `    """\na\n\n    ""` (index -1), and
  on some unbalanced scriptlets: `check(fragments.isEmpty())` on
  `"""\n:{//\n\``, and a null `stack.last()` on `"""\n:{\n\`\n~`.
- `Lexer.kt:782` threw on markdown `">     ,\n    }"`; fixed on a classic
  Temper branch.
- `MarkdownSupport.buildTaggedRange` throws `NoSuchElementException` on
  the markdown `[i]:h\n"`: a link reference definition, then a line.
- `Lift.kt` throws `IllegalStateException` ("too few preceding ShiftLeft")
  on `():o`: inside `(...)`, the `Expression` garbage alternative matches
  nothing without failing, so the infix `:` has one operand to swap. Ours
  gives `(Call : o)`.
- `Tree.replace` gives what it plants the span of all but the last
  replaced child (an inclusive end passed as exclusive), as seen in
  rewritten call joins; we don't copy it (deviations above).
- Deep nesting overflows Kotlin's stack (`StackOverflowError`) at about 300
  nested calls `f(f(...))` or a 1000-term `a+a+...`.

Slow, which we don't copy: Kotlin's grammar takes exponential time on some
nesting, about doubling per level: 14 s for 22 nested `(` and similar for
`a[a[...]]`, `f<f<...>>`, `@a (@a (...))` and calls whose callee nests, as
`fn (x: fn (x: ...) {}) {}`. An alternative parses a whole operand, fails
on what follows, and the next parses it again: `Prefix` takes `(` as a
prefix operator and fails at `)`, the generic `Infix` does the same for
`[` and `<`, `TaggedString` parses the tag before checking for a string,
and the two forms of a decoration both parse its first operand. Ours checks
the failing condition first, with the same results.

Other differences we won't copy:

- An unclosed fenced code block followed by blank lines at the end of the
  file: Kotlin takes its last code line as the closing fence, so that code
  is lost to prose.
- `"\` before a character outside the BMP, like an emoji: Kotlin's escape
  takes one UTF-16 unit and splits the surrogate pair.

## Later (backends and tooling)

- **Reachability** (2026-11-17): tmpl's `reach` pass marks what the
  library needs, what only tests do, and what nothing does, as Kotlin's
  `ReachabilityTracer` does in its frontend
  ([analyses.md](tmpl/analyses.md), "Reachability"); every backend
  partitions by it (below).
  Differences:
  - An initializer is a root only if it may do something: a list literal
    of simple values, an operator that can't panic, and core's pure
    calls (`getConsole()`) aren't. Kotlin's is a root unless it's a
    value, a name, or a function after constant folding, so its
    `console = getConsole()` always is, and its `console` is always the
    library's.
  - A static field's initializer that may do something keeps its class;
    Kotlin's keeps only what the initializer names.
  - Expressions' types, and the classes of members used, are traced too,
    so a class used only through a cast or a method call is reached.
  - Not yet: Kotlin's `@test("name") let f(): Void { … }`, a test
    declared by decorating a function, which tempest's frontend reads as
    an ordinary function, so what it alone uses is unreached.
  Test: temper-tmpl/tests/reach.rs; temper-frontend/tests/to_tmpl.rs,
  `reach_as_kotlins_export_some` and
  `reach_as_kotlins_init_assignment_reachability`.
- **C++ by reach** (2026-11-18): Kotlin's C++ ignores reach, putting
  everything in the library, tests included. Tempest's partitions the
  program by tmpl's `reach` (as Kotlin's C#, Java, JS, Lua, and Python
  backends place what its tracer marks): what only tests reach,
  functions, classes and interfaces, and module values with their
  initialization, is defined (and declared in the header) only where
  `TEMPER_TESTS` is, its init at the end of the module's init function
  there; what nothing reaches isn't written, unused private classes,
  interfaces, and module values too (before, only unused private
  functions were left out). So a library built without tests has no
  test helpers, doesn't initialize test data, and doesn't need std's
  `testing` (its CMake links std only for the tests). The module's lock
  is under `TEMPER_TESTS` too where only tests use it. The Temper
  bodies of std members that C++ connects reach nothing, as C++ doesn't
  write them. Test: temper-be-cpp/tests/functional.rs,
  `test_parts_apart` (its `reach` program), `runs` and `strict` (built
  with and without `TEMPER_TESTS`), and the goldens.
- **Rust by reach** (2026-11-18): Kotlin's Rust ignores reach, writing
  everything into the library, test helpers and test data too, and
  hides the dead code and unused imports that leaves with
  `#![allow(warnings)]` in every crate. Tempest's partitions the program
  by tmpl's `reach`: what only tests reach (functions, classes and
  interfaces, module values' statics and getters, and the `use`s only
  they need) is `#[cfg(test)]`, its init a `#[cfg(test)]` block at the
  end of the module's init, in its scope; a library value that only
  tests read has its getter only for tests (a `var`'s update that
  reads only itself reads it under its lock, not through the getter);
  and what nothing reaches
  isn't written. What's kept for host code (`@keep`, `@keepTest`) but
  not exported is `#[allow(dead_code)]`, as no Temper code may use it.
  So each crate builds without tests, at clippy's `-D warnings`, with
  no dead code of test helpers, and doesn't initialize test data.
  Test: temper-be-rust/tests/functional.rs, `test_parts_apart` (the
  shared `reach` program), `runs_and_lints` (clippy with and without
  `cfg(test)`, and `reach`'s test run), and the goldens.
- **Python, JS, and Lua by reach** (2026-11-18): as Kotlin's, what only
  tests reach goes in each module's tests file and what nothing reaches
  isn't written, but by tmpl's `reach` (above), and laid out
  differently. The tests file has the module's test items, in source
  order, then its test init, which continues the module's init there,
  then the tests, where Kotlin's keeps its test top-levels in source
  order, tests among them. What they use of the module, they import:
  Python by name, private names too (Kotlin's alike); JS from the
  module's `.internal.js`, which exports what they use, with a
  `NAME$set` setter for each `var` they assign (Kotlin's imports from
  the module); Lua through the table the module adds to its exports
  for its tests, `$test`, with `NAME__get` and `NAME__set` for each
  `var`, so they see its current value (Kotlin's exports what they use
  from an `-internal` module, a `var`'s value as it was when the module
  ended). A test-only `var` is the tests file's own: Python's tests
  declare it `global` where they assign it. So a library that only its
  tests check doesn't import std's `testing` (its JS no Node `assert`,
  which a browser bundle can't load, and its Python no `temper_std`),
  as with Kotlin's. Not yet: host code for what only tests reach
  (`NotYet`, where Kotlin's Lua has none either), and tests that
  assign a local of their module's init block. Test: each backend's
  tests/functional.rs, `test_parts_apart` (the shared `reach` program,
  run as tests), and the goldens.
- **Java and C# by reach** (2026-11-18): as Kotlin's, what only tests
  reach goes in each module's test class (Java's `NAMETest`, in its
  package under `src/test/java`; C#'s `NAMETests`, in the tests'
  project), as statics, and its classes and interfaces in files of their
  own beside it, and what nothing reaches isn't written; but by tmpl's
  `reach` (above), and with its init: the test class's static
  initializer runs the module class's first (Java's `NAMEGlobal.
  initStatics()`, C#'s `RuntimeHelpers.RunClassConstructor`), then the
  test init, where Kotlin's Java collects the test init and never writes
  it, and Kotlin's C# writes it into the module class's static
  constructor, which can't see the test class's fields. The tests see
  the library's package-private (Java) or `internal` (C#) statics, as
  with Kotlin's. A library only its tests use (std, for its `testing`)
  is Maven's `test` scope and only the tests' project's reference, as
  with Kotlin's, now by reach, so a library that only its tests check no
  longer needs std at run time, and JS's `package.json` lists it under
  `devDependencies`, where Kotlin's lists every library under
  `dependencies`. Not yet: host code for what only tests reach
  (`NotYet`), and tests that use a local of their module's init block.
  Test: each backend's tests/functional.rs, `test_parts_apart` (the
  shared `reach` program), and `runs`, which builds the library first
  and then its tests against it (C#'s with `InternalsVisibleTo`), as
  Maven and MSBuild do, and the goldens.
- **Python's test dependency group** (2026-11-18): a library only the
  tests use goes in `pyproject.toml`'s `[dependency-groups]` `test`
  group (PEP 735), which installing the package skips, where Kotlin's
  lists every library under `dependencies`. Lua's rockspec lists them
  under `test_dependencies`, as Kotlin's does. Test: Python's
  tests/functional.rs, `test_only_libraries_grouped`.
- **`@keepTest` in C++** (2026-11-17): a function marked `@keepTest`,
  kept for tests' host code, and the private functions only it and tests
  call, are defined, and it's declared in the header, only where
  `TEMPER_TESTS` is, as tests are; Kotlin's C++ ignores reach and
  defines everything always. Tempest's C++ used to drop it, as nothing
  in Temper calls it, so tests' host code calling it wouldn't link.
  Every top-level declaration now carries `@keep` and `@keepTest` into
  tmpl, classes and interfaces too (`keep` and `keepTest` in its text).
  Test: temper-be-cpp/tests/functional.rs, `kept_for_tests`; funtest
  `testing/asserts` ("Kept for host code").
- **Diagnostic levels** (2026-11-10): a diagnostic is an error or it
  isn't. Kotlin's info level is tempest's hint (`Level::Hint`), advice
  about correct code, now "Unnecessary type check" and a content tag's
  problems in a test's body ("Content tags" below): the LSP sends
  it as a hint (faded in editors), the playground shows it, and the CLI
  prints it only with `-v`, where Kotlin always prints info. JSON
  records still name it `info`, as Kotlin's. Kotlin's separate info
  "You can turn an Int32 literal into an Int64…" is folded into the
  out-of-range literal's warning ("…out of representable bounds; add
  i64 at the end for an Int64"); that warning stays a warning until the
  team decides on integer literals typed from context.
  Test: crates/tempest/tests/cli.rs, `hints_only_with_verbose`.
- **Content tags worked out at compile time** (2026-11-10): as
  Kotlin's `OptimizeContextualAutoescapingBlocks`, a tagged string whose
  class implements an interface named `ContextualAutoescapingAccumulator`
  (as a library's `html` or `sql`) gets its contexts at compile
  time, so generated code calls the escapers directly
  ([compile-time.md](compile-time.md), "Content tags"). Differences,
  most of them to fold only what gives what run time does:
  - A join folds only when `sameStatePredicate` holds for both
    branches. Kotlin uses `mergeStates`' result, and a library's may
    keep the first branch's state when only nested languages' (JS,
    CSS, URL) states differ; then, where branches end in different
    states inside `<script>`, Kotlin's pass, by its code (not run),
    escapes the second branch's value for the first's context, with no
    diagnostic.
  - A path that leaves the tagged string (`return`, `throw`,
    `panic()`, or a jump to a loop around it) joins nothing, as
    Kotlin's paths don't reach the join; jumps inside it join at their
    targets, as branches do.
  - A loop folds only when each pass ends in the state it started in,
    by `sameStatePredicate`, its body folded once; otherwise the use is
    left to run time. Kotlin follows forward edges only.
  - A picked escaper must be alike its class's `instance`; Kotlin
    calls `instance` whatever the picker gave.
  - A use the library reports an error for isn't folded, and the error
    is an ordinary one; Kotlin folds it and logs the error.
  - Only allowlisted code runs at compile time (compile-time.md, "What
    runs at compile time"); a use that needs more is left to run time.
  - It runs where Kotlin's gives up for reasons not confirmed: on a
    real library with HTML and SQL tags, its SQL uses fold too, where
    Kotlin folds only HTML ones, and a `for … of` template, which
    Kotlin leaves, folds as a loop. It builds an `EventParseEffect`'s event
    where it's used rather than hoisting it to a module-level
    temporary; it writes no `exportAnalyses` data files, having no
    `dataFile`.
  - A rewrite whose new code doesn't type, as an escaper without an
    `apply` for the value, or whose `apply` takes another type than
    the append, is undone rather than reported.

  The interpreter also keeps a regex literal without interpolations
  once compiled, which the library's propagation functions rebuild on
  every call.
  Test: settle/autoescape.cases, "content tags", "content tags left
  to run time", and "content tags in loops and jumps"; tests/settle.rs, `content_tags_reach_nothing_at_compile_time`,
  `content_tags_out_of_steps`, `compile_time_allowlist`;
  programs/content-tags.

- **Package metadata** (2026-11-12): a library's `license`,
  `description`, `authors`, `homepage`, and `repository` go into each
  backend's package files as Kotlin's, with a few fixes for what those
  formats expect. pyproject.toml has the links under `[project.urls]`
  (Kotlin's `homepage` and `repository` keys in `[project]` aren't
  valid there), and splits an author's `Name <email>` into its name and
  email. package.json has the repository as git's, which Kotlin leaves
  out. A .csproj's `PackageProjectUrl` is the homepage, or else the
  repository, which also goes in `RepositoryUrl` (Kotlin's is the
  repository). The rockspec's homepage, as the pom's URL, falls back to
  the repository. CMakeLists.txt's `project` has the description, where
  Kotlin writes none. Each backend's names for a library (`pyName`,
  `jsName`, `rustName`, `javaName`, `javaPackage`, `javaGroup`,
  `javaArtifact`, and `csharpRootNamespace`) name its package and what
  code that uses it imports, as Kotlin's, but an empty `javaPackage`,
  which Kotlin takes as no package at all, counts as unset.
  Test: crates/temper-driver/tests/metadata.rs.

- **Ignore files** (2026-11-06): Kotlin reads one, the nearest
  `.temperignore` or `.gitignore` at or above where it starts (its
  "TODO Support multiple ignore files"). Tempest reads every
  `.gitignore` under the work root, and those above it in its git
  repository, as git does, so a library copied into an ignored build
  directory isn't a second one of the same name; and every
  `.temperignore` the same way, after the `.gitignore` beside it
  (2026-11-09). Kotlin's `--ignorefile FILE` adds one more, with the
  last word, relative to its own directory, as Kotlin's; `/dev/null`
  adds nothing, where in Kotlin it means no ignore file. Kotlin's `run`
  (and `repl -b`) read no ignore file at all, so `temper run` can find
  libraries that `build` and `test` skip, such as a copy in an ignored
  directory, which then clashes by name; tempest's `run` reads them as
  the others do, and takes `-ign`.
  Test: temper-frontend/tests/library_tests.rs,
  `gitignore_files_skip_what_they_list`, and `ignore.rs`'s own tests;
  tempest/tests/cli.rs, `test_flags`.

- **What tools start** (2026-11-10): Kotlin's `temper test -b` and
  `run -b` wait as long as the tools take, and what they started
  outlives `temper` if it's killed (on a library whose Lua tests grew
  past 2.5 GB, the `lua` process ran on after its run was killed).
  Tempest gives each command 600 seconds (`--timeout`,
  `TEMPER_TIMEOUT`), a tests file that runs over failing alone; runs
  each in a process group that's killed when it ends, times out, or
  tempest gets SIGINT, SIGTERM, or SIGHUP, and on Linux, that dies with
  tempest; and on Linux limits Lua's, Python's, and C++'s test programs
  to 4 GiB written (`TEMPER_MEMORY_LIMIT`)
  ([docs/tools/cli.md](../docs/tools/cli.md#what-tools-start)).
  Test: tempest/tests/cli.rs, `test_timeout` and
  `killing_tempest_kills_its_children`.

- **The command line** (2026-11-09): tempest takes `temper`'s commands
  and flags ([docs/tools/cli.md](../docs/tools/cli.md)), with these
  fixes and differences:
  - **Usage errors exit with 2**, as most Unix tools do; Kotlin's are
    127 (kotlinx-cli's), which shells use for "command not found", and
    `temper help bogus` says "Too many arguments! Couldn't process
    argument bogus!". What tempest lacks yet (`watch`, `doc`, `repl
    -b`, the `cppv`, `data`, `java8`, and `mypyc` backends, and `serve
    --port`) exits with 3, saying so.
    Test: tempest/tests/cli.rs, `usage` and `not_yet`.
  - **The work root's help text:** Kotlin's `--workroot` help says the
    default is "the farthest ancestor directory that contains a
    `temper.out` directory, or the closest ancestor directory that
    contains a `config.temper.md`", but its code (`defaultWorkdir`)
    takes the nearest `temper.out` and the farthest config, as tempest
    does; tempest's help says what the code does.
    Test: tempest/tests/cli.rs, `help`.
  - **A library that isn't there:** `temper test --library nope` passes
    with "Tests passed: 0 of 0" and exits with 0, and `temper run
    --library nope -b interp` prints nothing and exits with 0 (with
    `-b py`, a stack trace: "Missing py library name for
    RunLibraryRequest"). Tempest says "No library named nope under
    ROOT" and exits with 1.
    Test: tempest/tests/cli.rs, `test_flags` and `run_a_library`.
  - **`run`'s output:** Kotlin prints what the program printed after it
    finishes, on stderr, through its console, and on the interpreter
    twice, each line prefixed with the module (`-work/lib//:hello 42`).
    Tempest streams it to stdout, once, as it's printed.
    Test: tempest/tests/cli.rs, `run_a_library`.
  - **`init`** writes Kotlin's files byte for byte, but its message
    goes to stdout, where Kotlin's console writes everything to stderr;
    and a directory whose name isn't ASCII (`Café`) works, where
    Kotlin's JVM fails in a C locale with an `InvalidPathException`
    stack trace before it starts.
    Test: tempest/tests/cli.rs, `init_writes_kotlins_skeleton`;
    temper-driver/src/init.rs, `names_as_kotlin`.
  - **`--logformat json`** writes Kotlin's record shape, adding
    `message`; diagnostics' `template` names are tempest's (`Message`'s
    variants, `UndeclaredName` for Kotlin's `BecauseNameUndeclared`)
    and their `values` are empty, as tempest keeps a diagnostic's text,
    not its arguments.
    Test: tempest/tests/cli.rs, `logformat_json`.
  - **Tempest's additions:** `test` defaults to the interpreter (Kotlin
    requires `-b`) and takes a `DIR` where the work root's search
    starts; `build` and `run` take `-o DIR`; `--version` works (Kotlin
    has only `version`); and with several `-b`, `test` prints each
    backend's summary before Kotlin's total. The one-backend commands
    (`tempest py FILE`) are gone, as Kotlin has nothing like them.
    Test: tempest/tests/cli.rs, `test_flags`, `version`, and
    `build_writes_each_backend_to_a_directory`.
  - **A step budget for tests on the interpreter** (2026-11-09):
    Kotlin's `temper test -b interp` has none, so a test that loops
    forever hangs it.
    Tempest gives each test 10 billion steps by default (`--fuel N`, or
    `none`; the real libraries' slowest test takes 626 million), fails
    a test that runs out alone ("ran out of steps"), keeps what tests
    log up to 64 KiB with a note past it, and with `-v` shows each
    test's steps. An obvious robustness improvement; nothing that passes
    in Kotlin fails, short of a test 16 times slower than any we have.
    Test: tempest/tests/cli.rs, `test_fuel`;
    temper-frontend/tests/library_tests.rs, `runaway_tests_fail_alone`
    and `runaway_top_level_is_cut_short`.
  - **Panics exit with 101**, saying it's a bug in tempest, so a script
    can tell one from a failure; Kotlin's internal errors print a stack
    trace and fail as anything else does. The language server
    survives one, as an "internal error" diagnostic or error response.
    Test: temper-lsp/tests/lsp.rs, `panics_become_internal_errors`.

- **The REPL** (2026-11-11): `tempest repl` prints Kotlin's
  `interactive#N: value` lines and renders values as Kotlin's does,
  with these differences ([docs/tools/repl.md](../docs/tools/repl.md),
  the study in [repl.md](repl.md)):
  - **One growing module**, not a module per input importing snapshots
    of earlier exports: each input's scope chains to the previous
    input's, so a later declaration shadows an earlier one, and a
    top-level `var` carries over, typed, where Kotlin drops it ("No
    declaration for v"). A declaration whose input failed stays, and a
    read of it fails saying so ("x has no value: interactive#4
    failed"); Kotlin keeps the older binding.
    Test: temper-frontend/tests/repl/basics.cases, "top-level vars
    carry over and stay typed"; errors.cases, "a declaration whose
    input failed fails when read, saying why".
  - **Type names** print as declared, `Rectangle`, not `Rectangle__0`,
    and a class a later input hides prints with its input,
    `interactive#5.Rectangle`; Kotlin prints `Rectangle__0` for both.
    The tutorial's transcripts are tempest's now.
    Test: basics.cases, "redefinition shadows, earlier code keeping the
    old"; temper-frontend/tests/repl_docs.rs.
  - **Diagnostics** are tempest's, `interactive#N:line:column: level:
    message` and the line with the span marked, in place of Kotlin's
    `[interactive#0:1+0-9]@G:` and stage letters, each error reported
    once, where Kotlin repeats some at each stage that finds them.
  - **Kotlin's REPL bugs, fixed:** printing a cyclic value overflowed
    the JVM's stack and ended the session (tempest prints `↻` where it
    recurs, and limits depth and length); a function whose body had an
    error failed silently when a later input called it (tempest says
    why); reassigning a `let` from a later input named the wrong input
    (tempest's error is at the assignment, in its own input); a
    top-level `var` was gone in the next input; and piped input echoed
    the auto-indent into the output and the input, shifting columns
    (tempest prompts only on a terminal, and never echoes).
    Test: errors.cases, "a cyclic value prints (Kotlin's REPL
    overflowed and ended)", "a function whose body has an error says
    why when called (Kotlin fails silently)", and "reassigning a let
    from a later input names its own input (Kotlin names the wrong
    one)"; basics.cases, "top-level vars carry over and stay typed";
    tempest/tests/repl.rs, `piped_input_prints_only_results`.
  - **Robustness:** Ctrl-C stops the input running, through the
    interpreter's step check, where Kotlin's ends the process; a panic
    in tempest is caught and reported, and the session goes on, where
    Kotlin catches only `NotImplementedError`. An unclosed `"` ends
    with its line, as the lexer reads it, where Kotlin's REPL kept
    reading lines into the string.
    Test: tempest/tests/repl.rs, `ctrl_c_stops_the_input_not_the_session`;
    temper-frontend/src/repl.rs, `a_panic_is_caught_and_the_session_goes_on`;
    errors.cases, "an unclosed \" ends with its line (Kotlin's read on)".
  - **Defaults:** the work root around the current directory is loaded
    without `-w` (Kotlin loads only std then); no prompt when standard
    input isn't a terminal (Kotlin's `--prompt hide`); a failure says
    why, where Kotlin's bare bubble prints only `fail`.
    Test: tempest/tests/repl.rs, `imports_from_the_work_root` and
    `prompts`.
  - **`help`, `describe`, and `translate`** stay Temper functions, as
    Kotlin's, declared before the first input, so an input's own
    `help` shadows them. `describe(n, step)` takes tempest's steps
    (`"parsed"`, `"settled"`, `"types"`, `"module"`, `"tmpl"`), not
    Kotlin's stage names. `translate(n, lang)` prints the code in the
    REPL, where Kotlin writes files and prints their paths, and
    translates one module, input `n` after the earlier inputs it uses
    and those that change what they declare (`v = 2;`, `lb.add(1);`),
    with a name an included input declares again renamed (`x__2`) and
    an import repeated dropped; Kotlin translates input `n` alone,
    importing earlier exports. A module with errors isn't translated:
    its errors are shown, top-level `await` (REPL-only) among them,
    where Kotlin translates what it can.
    `translate(n)` gives every language. `help("name")` shows hover's
    text; Kotlin's topics (`help("unresolved")`,
    `help("available-imports")`) and `help(+)` aren't there. Ctrl-C
    stops them too.
    Test: temper-frontend/tests/repl/specials.cases;
    temper-driver/tests/repl/tools.cases.
  - **The terminal:** rustyline where Kotlin has JLine. A multi-line
    input is one buffer whose earlier lines stay editable, and one
    history entry; Kotlin's auto-indent is there, as text in the
    buffer. History is one file in the platform's data directory,
    owner-only, appended as each input runs (Kotlin's, per version,
    is saved at exit), with `--no-history`, and a line starting with a
    space isn't kept. Fish-style suggestions from history are left
    out; the hint is the signature of the call being typed. Color
    honors `NO_COLOR` and `--color auto|always|never`, which Kotlin
    lacks; `TERM=dumb` and `CI` turn it off as in Kotlin (`TF_BUILD`
    doesn't). What the REPL prints of its own has control characters
    escaped, so output can't rewrite the terminal, and so do logs when
    standard output is a terminal (Kotlin prints them raw); piped, logs
    stay raw. The history file is opened once, checked (its directory
    the user's own, no links), and written through that descriptor.
    Test: tempest/src/repl/line.rs and color.rs (unit tests);
    tempest/tests/repl.rs, `edits_lines_on_a_terminal`, `color`, and
    `control_characters_are_escaped`.
  - **Also:** `test(…)` in an input says tests don't run in the REPL
    (Kotlin reports `Test` undeclared too), and a name that an earlier
    input used before a later one declared it gets a note on how to
    declare things that use each other.
    Test: specials.cases, "test refuses with a clear message";
    basics.cases, "a forward reference to a later input fails; one input
    can recurse".
  - **Imports:** relative ones resolve from the current directory, as
    if the inputs were there; `--no-workroot` loads no work root.
    Test: tempest/tests/repl.rs, `relative_imports_from_the_current_directory`
    and `imports_from_the_work_root`.
  - **Not yet:** `-b`; and std's tutorial examples that Kotlin's REPL
    ran but tempest's language rejects are noted in
    docs/inconsistencies.md, I24.

- **Code with errors** (2026-11-05): where Kotlin's TmpL has garbage
  nodes, which each backend writes its own way (a comment in Java, a
  `panic!()` in Rust, a tuple that's `NotImplemented` in Python, which
  doesn't fail), tmpl has `Error`, which `to_tmpl` makes of an error
  node, of a value that doesn't fit its place (Kotlin keeps the ill-typed
  code, `a = "1"`, so its Rust, Java, C#, and C++ don't build
  `semantics/type-checked-locals`, its issue 58), and of a call of an
  undeclared name. Every backend panics there (since 2026-11-27; it
  failed as `bubble()` did, which an `orelse` caught); nothing after it
  in a block is translated. C++, C#, Java, and Rust stop at the first
  one where a type error alone ran on before; the interpreter, JS, Lua,
  and Python still run type-only errors as written.
  tempest also reports a body that can reach its end without a value
  where one is wanted, as Kotlin does ("Cannot assign to String from
  Void", at the closing brace), and fails there; Kotlin's Rust writes
  `return;`, which doesn't build. Both tests pass on all seven, with
  every backend's checks clean.
  Test: funtests `semantics/broken` (its run fails, as expected) and
  `semantics/type-checked-locals` on every backend.

- **Keep on trucking** (2026-11-06): errors stay local, as Kotlin's
  do, and code is written past them. `tempest build` reports every
  error, writes every backend's code anyway, and exits with 1, as
  Kotlin's `temper build` does; `tempest test` runs the tests, on the
  interpreter and on each backend, failing those that reach an error
  and exiting with 1, as Kotlin's `temper test` does, with no override
  needed; `tempest run` runs past them too, and exits with 1 (2026-11-09).
  More stays local than in Kotlin:
  - **A `let` or assignment of an error** (`let s: String = x;`) is the
    error itself, as a statement, so nothing after it is written, and
    Java's and Rust's checks stay clean.
  - **An undeclared type** (`x: Nope`, `List<Nope>`, a class's field)
    is `AnyValue` in tmpl, so the declaration is still written and
    callable; an undeclared supertype (`extends Missing`) is left out.
  - **A member that isn't there** (`s.lenght`, `p.m(1)`) is Kotlin's
    error "No member lenght in String", which tempest didn't report
    before (tmpl then stopped at it), and fails where it runs. Only for
    a class's value or a builtin type whose members core's table has in
    full (`Boolean`, `Float64`, `Int`, `Int32`, `Int64`, `List`,
    `ListBuilder`, `Map`, `MapBuilder`, and `String`), and not where an
    error is reported already (Kotlin reports only the tie for a method
    inherited twice); not yet for an interface's value or a type
    parameter's. A nullable value's member is an error of its own ("A
    member of a nullable value").
  - **A condition with an error** is a `Boolean` `Error`.
  - **The interpreter** fails at an error reported at a value, name, or
    call, as every backend does, but for a type-only error (below).
  Not yet: a library that uses what tmpl doesn't
  cover yet still isn't written for any backend; `Pair<String>` (too
  few type arguments) and `s[0]` (an `Int` for a `StringIndex`) report
  no error, where Kotlin does, so tmpl stops at them.
  Test: shared testkit program `errors` on every backend (and its log
  on the interpreter); tempest/tests/cli.rs,
  `errors_fail_only_where_they_run`; settle/types.cases, "a member that
  isn't there is an error that fails where it runs".

- **Type-only errors run on, where the language can** (2026-11-06):
  code that's ill-typed but runnable (a value that doesn't fit its
  place: a wrong argument, assignment, return value, or condition; an
  override whose signature or visibility differs) runs as written on
  the interpreter and the dynamically typed backends (JavaScript, Lua,
  Python), as Kotlin's interpreter does, and fails where it runs on the
  statically typed ones (C++, C#, Java, Rust), whose compilers would
  reject it. Kotlin writes ill-typed code for every backend, so its
  static backends' builds fail (its issue 58). An error that leaves
  nothing to run (an undeclared name, a missing member, the wrong
  number of arguments, a parse error) fails where it's reached
  everywhere. The frontend classifies them (`Message::type_only`);
  tmpl's type-only `Error` keeps the value as written, and each backend
  settles it by its profile's `RUNS_TYPE_ERRORS` (`temper_tmpl::errors`).
  Every error is still reported, and the exit status is still 1.
  Strict checkers (mypy, pyright, tsc, LuaLS) skip ill-typed
  programs' output, which they'd reject, as Kotlin's.
  Where the interpreter and the dynamic backends still differ: a body
  that can reach its end without a value fails on every backend but
  runs on in the interpreter (it returns `void`); a condition that
  isn't a `Boolean` fails in the interpreter ("Expected a Boolean")
  but tests truthiness in each dynamic language (Lua takes `0` as
  true); and ill-typed values reaching builtins can differ by language
  (Python's `int_add(1, 1.0)` adds, where the interpreter fails).
  Errors this doesn't count as type-only, though Kotlin runs on there
  too, still fail everywhere: arguments that fit no builtin's variant,
  an impossible `is` or `as`, a `@fun` interface's value as another's,
  and `Void` as a value.
  Test: shared testkit program `errors` (logs by backend kind);
  funtest `semantics/type-checked-locals`; tempest/tests/cli.rs,
  `errors_fail_only_where_they_run`; the settle cases' logs.

- **Generators and `async`** (2026-11-05), each as Kotlin's strategy
  but:
  - **Python:** a generator's `def` is typed (`do_await:
    Callable[[Future[Any]], Any]`, `-> Generator[Any, Any, None]`),
    where Kotlin's leaves `do_await` untyped and returns
    `Generator[empty, None, None]`, naming a function as a type, which
    strict mypy and pyright reject; and a name a later block declares
    again is annotated once, as mypy's `no-redef` wants.
  - **Rust:** each state's code is in an `orelse`, where Kotlin's wraps
    only an `await`'s result; a state machine with one state has no
    loop (clippy's `never_loop`); and `Promise` is a `Future`
    (api-compat.md, proposal 2).
  - **JS:** a generator is a `function*` declared before its statement,
    made a factory by `adaptAwaiter(fn)` where it's used, where Kotlin's
    is `const fn = adaptAwaiter(function* fn(…) {…})`; it's typed
    `@returns {Generator<any>}`, as what it yields is the runtime's, and
    `yield;` yields nothing, where Kotlin's `yield null` fails strict
    tsc against `Generator<{}>`.
  - **Lua:** the runtime has promises and `async` (runtime [change 0006](../docs/internals/detail/06-backends.md#changes-from-kotlin-tempers-runtimes)),
    which Kotlin's Lua calls but lacks (its issue 144); a block starts
    at once and runs to its first pause.
  - **C#:** `Async` is `global::TemperLang.Core.Async`, in Program.cs
    too, as a library named `async` hides it; a `yield` yields
    `default!`, where Kotlin's `null` warns.
  - **Java:** a state machine's
    lasting locals are cells, as tempest's captured locals are, where
    Kotlin's share one class.
  - **C++:** settling a promise from outside any `async` block drains
    the queue (runtime [change 0005](../docs/internals/detail/06-backends.md#changes-from-kotlin-tempers-runtimes)), so `async-broken-early` and
    `await-in-loop` pass, which Kotlin's C++ fails (its note 520); and
    C++20 code can `co_await` a Temper promise.
  - **State machines** (Rust, Java, C++) are tmpl's `if`/`else if`
    chains, where Kotlin's are `switch`es; as Kotlin's, a step sets its
    state to `-1` before it runs, so a step that failed is done.
  - **Closures in a generator's loop** each get their own copy of a
    loop's `let` they capture, as the interpreter and generator
    backends give each pass its own binding. Kotlin's state machines
    share one binding where the `let` is used after a pause (a bug);
    tempest moves every local up, so it copies, and fails on a captured
    `let` assigned after it's set, which a copy can't share.
  - **Python:** `nextSafe` is `next(g, None)` where its result goes
    unused, and else `next(map(lambda v: (v,), g), None)`, a 1-tuple or
    `None`, as Kotlin annotates `GeneratorResult`; Kotlin's bare `next`
    raises `StopIteration` once the generator is done (a bug).
  Test: funtests `control-flow/async`, `async-waiters`,
  `async-broken-early`, `await-in-loop`, and `actor-run`;
  programs/functions.temper.md's generator, on every backend;
  settle/run.cases, "closures made in a generator's loop, each pass its
  own as in a function"; and temper-frontend/tests/to_tmpl.rs,
  `failed_steps_are_done` and `reassigned_loop_captures_fail_clearly`.
- **Generators that yield values** (2026-11-06): Kotlin's TmpL drops
  `yield(x)`'s value (`TmpLTranslator`: "TODO: yielded value
  expression"), so on Python, JS, Lua, and C# a generator yields
  nothing, and its Python checks a step's result against
  `_value_result`, which it never defines (a `NameError`). Tempest's
  `Yield` carries the value: generators yield it natively, so host
  loops see it, and state machines return it, as Kotlin's do. A step's
  result is each language's own (Python's 1-tuple or `None`, JS's
  `IteratorResult`, Lua's `{ true, v }` or `{}`, Rust's `Option`).
  Lua's runtime passes the value on, and a factory's arguments, which
  it dropped (its TODO; runtime [change 0020](../docs/internals/detail/06-backends.md#changes-from-kotlin-tempers-runtimes)). Typing gives a literal
  its factory's type, `fn (T): SafeGenerator<Y>`, where Kotlin's
  leaves it unchecked, but not for one with optional parameters,
  which Kotlin accepts where a function type takes fewer.
  Test: programs/async-turns.temper.md's `fibonacci` and `collatz`, and
  each backend's `tests/host/` loop over them, on every backend.
- **One turn at a time, and host code's own await and loops**
  (2026-11-05), ahead of Kotlin main; Python and Java follow Temper PR
  #532 (open) as closely as fits, so its merge stays compatible:
  - **Every backend** runs one `async` step at a time and switches only
    at `await`, and settling a promise only queues its waiter, so
    `complete` returns before the waiter runs, as in the interpreter and
    JS. Generated code calls only the runtime's entry points, so the
    queue stays swappable ([concurrency.md](concurrency.md), Turns).
  - **Python** (patch 0007): #532's FIFO on one `temper-async` thread
    and `top_level_turn`, behind `set_async_runner`, with
    `asyncio_runner(loop)` (`call_soon_threadsafe`); promises are
    `TemperFuture`s, which asyncio can `await`; std/net's blocking I/O
    runs on `temper-io`. Generated code annotates promises as
    `TemperFuture` (patch 0013), where Kotlin's says `Future`, which
    strict checkers won't let host code `await`.
  - **Java** (patch 0008, codegen): #532's scheduler, `Core.awakeUpon`,
    and `beginTopLevel`/`endTopLevel` around the main class's loads,
    with `setAsyncRunner(Executor)`; `Generator` is `Iterable`.
  - **C#** (patch 0009): the same queue, thread, and turn, and
    `BeginTopLevel`/`EndTopLevel` in Program.cs, where Kotlin's C# runs
    blocks with `Task.Run` and resumes them with `ContinueWith` in
    parallel (its #516). Patch 0014 adds `SetAsyncRunner` and
    `GetPromiseResult`, and settles a promise when the turn that settles
    it ends, since a task runs its continuations inline: a host's `await`
    resumed inside the turn, found by C#'s host test.
  - **Rust** (patch 0010, codegen): a resume is queued on the runner
    (`AsyncRunner::awake_upon`), where Kotlin's runs it inside
    `complete` (its #519); `SingleThreadAsyncRunner` holds a turn lock;
    `SafeGenerator` is an `Iterator`.
  - **Lua** (patch 0011): settling queues waiters, drained as C++'s
    are; a generator is a callable table that `for … in` steps and that
    a failed step leaves done, where Kotlin's `coroutine.wrap` errors.
  - **C++** (patch 0012): range-for over a generator (C++14), and a
    failed step leaves it done. Already one thread and one queue.
  - **Blocking on an unsettled promise inside a turn** raises
    (Python's `result`, Java's `getPromiseResult`, Rust's `get`, C#'s
    `GetPromiseResult`), where Kotlin's deadlocks.
  - **JS:** an exported `@fun` interface isn't re-exported, as it
    declares nothing (a tempest bug this found).
  Test: programs/async-turns.temper.md on every backend; each backend's
  `tests/host/` (2026-11-05: `interop.py`, `.mjs`, `.lua`, `.rs`, `.cpp`
  (C++20, with `co_await`), `Interop.java`, and `Interop.cs`), host code
  using it the language's way, also stepping a generator after a failed
  step and, where a backend can block, blocking in a turn; temper-core's
  Rust tests, now in CI too
  (`awake_upon_queues_the_step_after_the_resolving_call`,
  `get_inside_a_turn_panics_rather_than_deadlocking`,
  `a_generator_is_an_iterator`).
- **std `net`** (2026-11-05): std's `sendRequest` and `NetResponse`
  connect as Kotlin's, but no request blocks a turn, and each settles
  its promise from the I/O side, which queues the waiter:
  - **Rust:** tempest's own `support.rs` sends on a `temper-io`
    thread, where Kotlin's runs `ureq` inside an `async` block, blocking
    the turn; the runner waits for it (`AsyncRunner::hold`, patch
    0017). `ureq` has no gzip, as tempest's own.
  - **Java** (patch 0016): connecting and sending run on the pool too,
    where Kotlin's run them in the caller.
  - **JS** (patch 0015): `sendRequest`'s JSDoc takes any method string,
    as std passes one; Kotlin's `"GET" | "POST"` fails strict tsc.
  - **C#:** an `orelse` whose `await` is inside an `if` splits its
    `try`s across the `if` with `goto`s.
  - **Lua** (patch 0018) and **C++** (patch 0019) send through a
    pluggable client, where Kotlin's Lua calls runtime functions it
    lacks and its C++ doesn't build std
    ([concurrency.md](concurrency.md), "What std's `net` on C++ and Lua
    taught"). Lua's default is LuaSocket's `socket.http` (no HTTPS
    without LuaSec), on non-blocking sockets the runner polls when no
    step is ready; C++'s is libcurl's multi interface on a `temper-io`
    thread, built only where the build defines `TEMPER_NET_CURL` (the
    generated CMake does where it finds libcurl), its responses posted
    back to the Temper thread. Without a client, a request gives a
    broken promise, as std allows, and says on stderr how to plug one
    in. Host code plugs in its own with `temper.set_net_client` or
    `temper::core::net::set_client`.
  - **Test server:** one in the testkit, answering as Kotlin's
    `TestWebServer`, on any free port, as Kotlin's suite picks one;
    the net tests' goldens read it as 28461.
  Test: funtests `types/netresponse` and `netresponse-not-found` on
  every backend (C++'s logs, and Lua's where no Lua loads LuaSocket,
  are what a broken promise gives, as Kotlin's suite expects them to
  fail), from host code with a client of its own on C++
  (`net_client_from_host`, under ASan and TSan) and Lua (`host/net.lua`),
  and on the interpreter
  (testkit's `net_tests_on_the_interpreter`); temper-core's Rust test
  `a_hold_keeps_run_all_blocking_waiting`.
- **Local functions that use each other or themselves** (2026-11-05):
  tmpl lets a run of function `let`s use each other, a group
  (`analysis::function_groups`), and each backend writes it its way:
  - **Rust:** a struct of the group's captures with its functions as
    methods, as Kotlin's, but only for groups (Kotlin's makes one for
    every literal) and named for the first function (`WalkerGroup`, not
    `ClosureGroup___0`); a method copies the captures it uses first.
  - **C++:** a local struct with `const` methods, where Kotlin's
    captures the `std::function`s by reference, so one that outlives
    its function calls a dead one (a bug); a lambda in a method copies
    what it uses of the group (`[=, x = x, walker_group = *this]`),
    never `this`.
  - **Java:** a local class with the functions as methods, as Kotlin's
    scope classes, but one per group.
  - **Lua:** `local putter` before the group, then `function putter`,
    as Kotlin's forward declarations; Python, JS, and C# need nothing.
  - **Captured `var` parameters** (`thresh`'s `value`) are copied to a
    `var` local first on Rust, Java, and C++
    (`cells::copy_captured_params`), as Kotlin's Java does.
  Java synthesizes a functional interface over non-scalar types
  (`Int32ListBuilderProcedure`) with its own imports.
  Test: funtest `functions/locals`, on every backend.

Bugs tempest would inherit with its backends (milestones 5 and 6) if it
copied Kotlin's:

- **`==` and `!=` between Booleans** don't translate in Lua and JS, and
  Python emits code that fails only when run.
  Test: programs/operators.temper.md (`same`), on every backend.
- **Initialization order:** type declarations go before module-level
  values, so a static can read a value not set yet (C++ reads zero,
  Python fails with `NameError`); Python's statics also precede the
  functions they use. tmpl should keep source order. Rust and C++
  needed `init` first until #636, which initializes them on first use,
  as tempest's do too (api-compat.md, proposal 2); init timing still
  differs per backend. JS can't keep a static in Kotlin's private
  `static #x` field, which the init block outside the class can't set,
  so it's a module `let`, `Class$x`, behind the same `static get`.
  Test: programs/classes.temper.md (`Rect.unit`) and funtests
  classes/static-properties and classes/static-properties-scope, on
  every backend; Rust's and C++'s `FIRST_USE` runs.
- **Types as values** (`whatever(): Type { …; Void }`) come out
  inconsistently, and mostly wrong: Python returns `NotImplemented`
  after a string describing the value, JS the string `"Void: Type"`,
  C++ `0` as a `std::shared_ptr<void*>`, Lua `nil`, and Java a raw
  `Class` (`Void.class`, which `-Xlint` warns on); only C#'s
  `typeof(void)` and Rust's `TypeId::of::<()>()` are values of a type
  type. Harmless in the one test, which discards the value. Tempest's
  (2026-11-05) give a builtin type each language's own value:
  `type(None)` (`type`), `Void.class` (`Class<?>`, not raw),
  `typeof(void)` (`System.Type`), `TypeId::of::<()>()`, and
  `std::type_index(typeid(void))`. JS and Lua, which have no value for
  one, give its `typeof` and `type()` names, `"undefined"` and `"nil"`
  (`string`). The program's own classes as values aren't translated
  yet (tmpl/types.md, "Types as values").
  Test: funtest classes/angle-call, and the testkit's `classes`
  program, on every backend.
- **Rust list indexing panics,** as `ListedTrait::get` unwraps. Not a
  bug after all: core documents `Listed.get` past the end as a panic,
  as tempest's interpreter and backends give (checked 2026-11-04).
- **Rust phantom type parameters:** a class's type parameter that no
  field has gets a `PhantomData` field, as Kotlin's, named `phantom_t`
  rather than `phantom_T`, which `non_snake_case` warns of.
  Test: funtest classes/angle-call (`Blah<T>`), with clippy.
- **Rust integer literals as receivers** (`x & (0x7F >> n)`) give
  `127.wrapping_shr(…)`, which rustc rejects ("can't call method
  `wrapping_shr` on ambiguous numeric type", E0689). Tempest's gives the
  literal a suffix, `127i32.wrapping_shr(…)`.
  Test: temper-be-rust/tests/functional.rs, `literal_shifts`.
- **Rust first use reads a library partly initialized:** since #636 a
  read of a module-level value initializes the library if the value
  isn't set (`temper_core::initialized`), but reads a set one at once,
  even while another thread's init is still running the rest of the top
  level. So a thread that calls a library while another's first use
  initializes it can run alongside the init and change what the init
  logs or computes: Kotlin's `module-state` from 8 threads with no
  `init` logged `hi 19 of 3 200` and the like, where the init's second
  `report()` should give `hi 2 of 3 13`, in 9 of 300 runs (tempest's: 0
  of 300). Tempest's reads go through the crate's
  `support::initialized`, which reads at once only where the library's
  init has finished or runs on this thread, and otherwise initializes
  it first or waits for the thread that is, through the same
  `temper_core::initialized` (done 2026-10-31). It costs one atomic
  load per read. Its locked self-update (`n = n + 1`, below) goes
  through it too, the update as the read, so a function's update
  initializes the library as Kotlin's getter read does. C++'s entry points wait in `init_once`, so Kotlin's
  C++ doesn't have this. Kotlin's Rust writes `init_on_first_use()` in
  every crate, which its `#![allow(warnings)]` keeps quiet where nothing
  calls it; tempest's, which builds without warnings, writes it only
  where something does.
  Test: temper-be-rust/tests/functional.rs, `runs_and_lints`
  (`FIRST_USE`, `module-state` from 8 threads).
- **Rust module `var`s lose concurrent updates:** `n = n + 1` reads
  `n` under one lock and writes it under another, so two threads can
  both write the same value. Safe in Rust's sense, but not what a
  counter wants. Tempest's Rust backend reads and writes such an update
  under one write lock (done 2026-10-30; [tmpl/profiles.md](tmpl/profiles.md#as-built-rust)).
  Kotlin's Rust output also fails clippy's deny-level
  `self_assignment` on `zero = zero` despite its `#![allow(warnings)]`,
  which allows only warn-level lints; tempest's too, being faithful,
  so its test allows that lint.
  Test: temper-be-rust/tests/functional.rs, `runs_and_lints`
  (`THREADS`).
- **JS source maps that tools don't find:** Kotlin writes a `.js.map`
  beside each module but no `//# sourceMappingURL=` comment, so Node's
  `--enable-source-maps` and browsers never load it; its `sourceRoot`
  is `-work/src/` (the work root's own name, not a path from the map),
  so its sources don't resolve; and its `file` is relative to
  `temper.out`, not to the map. Tempest writes the comment and names
  sources by a path from the map to them, which Node follows (tested).
  Kotlin includes `sourcesContent`; tempest leaves it out, since tmpl
  carries no source text.
  Test: temper-be-js/tests/functional.rs, `source_maps`;
  tempest/tests/cli.rs, `build_writes_each_backend_to_a_directory`.
- **String ordering by code point (intended difference):** Temper orders
  strings by code point, as the interpreter does. Kotlin's Java output
  compares strings with `compareTo`, which orders UTF-16 code units, so
  `"\u{FF61}" < "\u{1F600}"` is false there, and its `<=>` gives the
  units' difference (10020), not -1, 0, or 1. Kotlin's JS orders code
  points (`cmpString`), but its `<=>` on strings and on floats gives
  `cmpString`'s or `cmpFloat64`'s difference too (`"z" <=> "a"` is 25,
  `2.5 <=> 1.0` is 1.5). Kotlin's C# is right: it calls the runtime's
  `StringUtil.CompareStringsByCodePoint`. Tempest's Java backend calls a
  small code-point helper that each module's class gets (its
  `temper-core` has none), its JS backend takes `Math.sign` of those
  differences, and its C# backend calls the runtime's helper, as
  Kotlin's; Python, Rust, C++ (whose `core::Str` compares bytes as
  `unsigned char`, which for UTF-8 is code-point order), and Lua (whose
  `<` compares bytes with `strcoll`, the same in the C locale it starts
  in) order code points natively. One program, checked against the
  interpreter, runs on all seven (done 2026-10-30).
  Test: programs/string-order.temper.md, on every backend.
- **C# formats numbers by the current culture:** Kotlin's C# writes
  `Int32.toString()` as the runtime's `Core.ConvertToString(n)`, which
  is `n.ToString()`, so under `sv-SE` (and other cultures whose minus
  sign is U+2212) `-1` logs as `−1`; `Boolean.toString()` lowercases by
  the current culture too, harmlessly for `True` and `False`. Tempest's
  C# backend formats in the invariant culture
  (`n.ToString(CultureInfo.InvariantCulture)`), and its tests run under
  `sv-SE` with the .NET analyzers' culture rules as errors (done
  2026-10-30; [tmpl/profiles.md](tmpl/profiles.md#as-built-c)).
  Test: temper-be-csharp/tests/functional.rs, `runs` (under `sv-SE`,
  analyzers' culture rules as errors).
- **C# `Boolean` comparisons:** Kotlin's C# writes `a < b` on booleans
  (it turns `a.CompareTo(b) < 0` into `<`), which C# doesn't have, so
  the library doesn't compile (CS0019). Tempest's keeps
  `a.CompareTo(b) < 0`, and its C# tests compile and run it.
  Test: temper-be-csharp/tests/functional.rs, `HIDING` (`before`);
  programs/operators.temper.md (`same`).
- **C# dropped values:** C# takes only calls, assignments, `++`,
  `--`, `await`, and `new` as statements. Kotlin's C# passes any other
  dropped value to the runtime's `Core.Ignore(e)`, a call, and drops a
  bare name. Tempest's writes a call as itself, `_ = e;` for a value
  with effects (a getter, `new`, a cast), and nothing for one without
  (a literal, a local, a field, an operator that can't fail), after
  removing unchecked conversions (`x!`) (done 2026-11-10).
  Test: temper-be-csharp/tests/functional.rs, `DROPPED`.
- **C# bodies:** besides numbered names, Kotlin's C# wraps a function
  that returns early in labeled blocks with a `goto` and a `return__N`
  variable, keeps `zero = zero` (C# warning CS1717), and names
  namespaces by one-letter aliases (`C::Core`). Tempest's returns
  directly, drops self-assignments, and writes `using`s.
  Test: temper-be-csharp/tests/goldens/.
- **Java module `var`s are plain statics,** so a function's update from
  several threads can be lost and other threads may read a stale value.
  Tempest's Java backend makes a `var` that functions write `volatile`,
  and locks the class for an update from itself alone (done 2026-10-30;
  [tmpl/profiles.md](tmpl/profiles.md#as-built-java)).
  Test: temper-be-java/tests/functional.rs, `runs` (`THREADS`).
- **C++ module `var`s aren't thread-safe:** they're plain storage
  (since #636, `GlobalSlot`s read and written directly), so functions
  updating them from several threads race (undefined behavior in C++):
  ThreadSanitizer reports it, and 8 threads × 1,000 updates of `ticks =
  ticks + 1` give about 6,500. Tempest's C++ reads and writes such
  `var`s in functions only under a mutex, a run of updates under one
  lock (done 2026-10-30; [tmpl/profiles.md](tmpl/profiles.md#as-built-c-1)).
  Init itself was a plain `static bool` check before #636; Kotlin's is
  now `init_once`, thread-safe, which tempest's uses too (2026-10-31),
  where it had `std::call_once`, which would deadlock now that the
  init's own calls of exported functions run it again. Since #636 an
  exported `var` is read through an accessor, `x()`, giving a `const
  &`, which races with the functions' writes as the variable did;
  tempest's accessor for a `var` that functions write gives a copy,
  read under the module's lock.
  Test: temper-be-cpp/tests/functional.rs, `runs` (`module-state` from 8
  threads, under ThreadSanitizer).
- **C++ statics give `const &`s to mutable objects:** since #636 a
  public static is read through `Type::x()`, a `const &`, but a
  `shared_ptr<T>` static's object isn't `const`, so any caller can
  change a shared object through it (`Type::policy()->set_limit(0)`),
  as through a module-level `let` of a class type. Tempest's is the
  same for now (a Kotlin fix to make).
- **C++ order of evaluation:** Kotlin's C++ before #601 passed operands
  straight to calls and to its `cat`, whose order C++ leaves
  unspecified, so `module-state`'s `"${bump()} … ${total}"` logged `hi 1
  of 3 10` under g++, which reads `total` first. Kotlin's now hoists
  every call's operands into a lambda's locals; tempest's computes an
  operand into a local first only where the order shows (done
  2026-10-30).
  Test: programs/module-state.temper.md (`hi 1 of 3 11`) on C++.
- **C++ shifts and comparisons:** Kotlin's C++ writes `a << b` and
  `a >> b` for `Int`, which C++14 leaves undefined for a negative `a`
  or a count past 31, where Temper masks the count; `<=>` on two string
  literals as `Compare::cmp("b", "a")`, which compared pointers until
  its literals became `TEMPER_STR`s (#654); and
  fails on `Boolean`'s `<=>` ("unsupported C++ binary operator:
  Compare::cmp"). Tempest's uses the runtime's `Int::shl` and
  `Int::shr` (C++'s `>>` only for a literal count in range), compares
  literals as `core::Str`s, and calls `Compare::cmp` for `Boolean`.
  A count in range doesn't make `<<` defined either: `5 << 30` on an
  `Int` overflows, undefined before C++20 (checked 2026-11-04);
  `Int::shl` shifts as `uint32_t` and casts back, wrapping as Temper
  does, and never writes `<<` itself.
  Test: funtest types/int/shifty (`shl32(0x05, 30)`, `-1 << n`, and
  `Int64`'s) under UndefinedBehaviorSanitizer, which reports a signed
  shift's overflow; temper-be-cpp/tests/functional.rs, `HIDING`
  (`shifts(33)`); programs/operators.temper.md (`"b" <=> "a"`, `same`).
- **C++ argument-dependent lookup** (found 2026-11-04 in Kotlin's
  be-cpp): a library function called unqualified, with an argument
  whose type is `std`'s (`std::string`, `shared_ptr`, `vector`), is
  also looked up in `std`, which can pick `std`'s function by the same
  name, as `swap(xs, ys)` for two lists finds `std::swap(shared_ptr&,
  shared_ptr&)`, better than a `const &` parameter, and `move(xs)`
  `std::move`, both failing to compile, or worse, compiling to the wrong
  call. Tempest's C++ did the same until 2026-11-04; it now qualifies a
  call of the library's own function with its namespace
  (`hiding::swap(xs, ys)`) wherever an argument isn't a scalar, which
  turns that lookup off. `quoted(s)` for a `std::string` was safe, as
  overloading prefers the library's non-template over `std::quoted`.
  Test: temper-be-cpp/tests/functional.rs, `HIDING` (`quoted`, `swap`,
  `move`).
- **C++ bodies and headers:** besides numbered names, Kotlin's C++ wraps
  every loop and branch body in an extra block, a function that returns
  early in a labeled block with a `goto` (whose unused labels fail
  `-Wall -Werror`), keeps `zero = zero`, writes every module-level value
  as a global even if only the top level uses it, builds strings
  through a `std::stringstream` (`cat`, its literals `TEMPER_STR`s), and
  declares every function in the header, exported or not. Tempest's
  returns directly, uses `+` and `std::to_string`, with bare literals
  (`"n = " + std::to_string(n)`, a `std::string` that converts to the
  `core::Str` it's kept as), keeps top-level-only values as the init's
  locals, and declares only exports.
  Test: temper-be-cpp/tests/goldens/, and `runs` with `-Wall -Werror`
  and the rest of `STRICT`.
- **C++ override slots** (since Kotlin's #645, synced 2026-11-09): in
  a virtual member, Kotlin's takes a type parameter's value as
  `temper::core::Param<T>` (`T const &` where `T` is a `shared_ptr`,
  `std::function`, or `Str`) and an indirect parameter by `const &`
  even if assigned, so an override's `label(Str const & l)` matches
  `Labeled<L>`'s `label(Param<L> l)`. Tempest's passes a type
  parameter's value, and an override's matching parameter, by value
  (`label(core::Str l)`), which compiles and runs the same, but host
  C++ overriding such a method needs the other signature. Not on
  purpose; to follow.
- **C++ upcasts and `-Wnull-dereference`** (2026-11-09): Kotlin's C++
  converts a class's `shared_ptr` to an interface's implicitly
  (`std::shared_ptr<Labeled> labeled = Sub::make(t);`). Interfaces are
  virtual bases, so the conversion checks for null, and g++ 12 to 14
  at `-O1` and up report the null path at the next call
  (`labeled->label(...)`), failing `-Werror` builds; Kotlin's own
  `property-members` escapes only because its top-level values are
  globals, reloaded. Tempest's converts a value that isn't nullable
  through the runtime's `core::to_base<Labeled>(...)` (patch
  `0021-cpp-to-base`),
  which says it isn't null, so the check and its path are gone (two
  instructions fewer); a nullable value converts as before, and a
  class's value called through an interface is called as the class, as
  before. Logged for a Kotlin fix, which could emit the same. And the
  runtime's `List::make` skips `reserve` for Booleans (patch
  `0022-cpp-bool-list-reserve`), whose libstdc++ code g++ 12 to 14
  misread at `-O3` (`-Warray-bounds`, in `-Wall`); also logged.
  Test: temper-be-cpp/tests/functional.rs, `runs` and `strict`
  (`-Wnull-dereference` in `STRICT`), and the goldens.
- **C++ tests and what only they call** (2026-11-10): Kotlin's C++
  builds a library's tests into every build; tempest's only where
  `TEMPER_TESTS` is defined, and now the private functions that only
  tests call (Hrefine's `urlTestData`) are in the same `#ifdef`. Before,
  a build without tests had them as unused `static` functions, which
  `-Wall -Werror` fails (`-Wunused-function`), in all five libraries.
  Since 2026-11-18, tmpl's reach decides it ("C++ by reach").
  Test: temper-be-cpp/tests/functional.rs, `strict` (libraries with
  tests compiled without `TEMPER_TESTS` too), and the
  `testing-asserts` golden.
- **C++ names shared by modules** (2026-11-13, regex engine): every
  module's declarations are in the library's one namespace, so in
  Kotlin's C++ a module's private `class Engine` and another's exported
  `Engine` are one struct, defined twice (a compile error where one
  includes the other, and an ODR violation where not), as are two
  modules' private globals of one name. Tempest's naming gives a
  module's private module-level declarations names no other module's
  take (`NamingRules::modules_share_names`), so the private one is
  `Engine2`; exports keep their names, and two modules exporting one
  name are still an error. A module's init locals and tests are its
  own. And where a generic function, defined in the header, calls a
  private function of its module, the private one isn't `static` and
  the header declares it; Kotlin's declares every function in the
  header. A template that takes its module's lock is not yet
  supported, since the lock is the `.cpp`'s.
  Test: programs/modules/ (`Engine`) and programs/generics.temper.md
  (`noneIn`), on every backend.
- **C# names a namespace hides** (2026-11-13, regex engine): C# looks a
  simple name up in each enclosing namespace before `using`s, so in
  `Lib.Machine` a class `State` of module `state` is the namespace
  `Lib.State`, and `Parts` the namespace `Lib.Parts` that `parts/regex`
  is in; and two modules' classes of one name, as `regex`'s and std's
  `RegexGlobal`, are ambiguous where both are `using`ed. Kotlin's
  aliases every other namespace (`using S2 = …;`, `S2::State`);
  tempest's keeps simple names and qualifies just these from `global::`.
  Test: programs/modules/, on C#.
- **C++ temporaries:** Kotlin's C++ returns lists as `shared_ptr`s, so
  iterating one in place (`for (auto const &x : *obj->list())`) reads a
  vector already freed. Tempest's C++ will name such a list first
  ([tmpl/profiles.md](tmpl/profiles.md#as-built-c-1), "Temporaries that
  die in use"); tmpl has no lists yet. With classes (2026-10-30),
  chained calls on returned objects stay inline, safe in C++, and an
  argument read from a `var` field is copied first (below), both
  checked under ASan.
  Test: temper-be-cpp/tests/functional.rs, `LIFETIMES`, under ASan.
- **Lua exports are copied once:** Kotlin's Lua module fills its
  exports table after its top level runs (`exports.ticks = ticks`), so
  an exported `var` that the library's functions write stays at its
  first value for users: after `tick()` twice, `require("…").ticks` is
  still 0. Tempest's Lua keeps such a `var` in the exports table itself
  (`exports.ticks = …` in `tick`), and its test reads it back (done
  2026-10-30; [tmpl/profiles.md](tmpl/profiles.md#as-built-lua)).
  Test: temper-be-lua/tests/functional.rs, `runs` (`LIVE`).
- **Lua tests of a library with no root module:** as Kotlin's, a
  library whose `src` holds only subdirectories gets an `init.lua`
  that requires each module, and each module's tests go in their own
  `tests/PATH-test.lua`, with no `init-test.lua`. Tempest's `test -b lua`
  ran only `init-test.lua`, so it ran none of such a library's tests,
  nor a rooted library's other modules';
  it now runs every `tests/*-test.lua` in turn, as Kotlin's does. A
  file that gives no results, as one whose module fails as it loads,
  counts as one failure named by its file, as Python's unittest reports
  a module that won't import, and the other files' results still count
  (fixed 2026-11-10).
  Test: tempest/tests/cli.rs, `library_without_root_module_lua`.
- **C# `run` of a library with no root module:** its `Program.cs`
  initialized only the root module's class, so `run` of a library of
  only `x/`, `y/`, and `z/` ran nothing; with no root module it now
  initializes every module's, as Lua's `init.lua` requires each
  (fixed 2026-11-10). Java's `run` still runs one module's `…Main`.
  Test: tempest/tests/cli.rs, `library_without_root_module_csharp`.
- **Lua `Boolean` comparisons:** Kotlin's Lua writes `a < b` on booleans,
  as its C# does, which Lua refuses at run time ("attempt to compare two
  boolean values"). Tempest's writes the order as logic (`not a and b`),
  or through the runtime's `bool_cmp` where the right side has effects
  that `and` would skip.
  Test: temper-be-lua/tests/functional.rs, `HIDING` (`print`, `math`);
  programs/operators.temper.md (`same`).
- **Members named like printed names:** Kotlin escapes members only
  for keywords, as tempest does, but for a few names whose output would
  break: a Python method or property named `int`, `str`, `property`, or
  another name printed in a class body hides it from the annotations and
  decorators after it, a JS method named `constructor` is the
  constructor, and a Rust method `new`, `clone`, or `clone_boxed` clashes
  with the one every class has. Tempest gives those a `_`.
  Test: programs/renamed.temper.md (`Builtins`).
- **Python parameters named like builtins** (2026-11-17, the user's
  decision): Kotlin's Python gives every parameter and local named for
  a builtin a `_` (`help_`, `min_`, `type_`), so a constructor's
  parameters, which Python callers may pass by name, read `help_=`.
  Tempest's keep their names unless they're keywords (`from_`,
  `lambda_`, `class_`; soft keywords such as `type` stay), and so do
  source-named locals (`sum`, `next`); compiler-made locals, members,
  and module-level names escape as before. Where a function's own names
  hide a builtin that its code calls or annotates with, it reaches the
  builtin through `builtins`, imported once as `_builtins`
  (`_builtins.len(items)` beside a parameter `len`), so the API never
  depends on a function's body. Callers using `help_=` must use
  `help=`; this differs from Temper 0.6.0's generated API, including
  std's (`CodeRange` and `Repeat` take `min=` and `max=` in
  `temper_std.regex`).
  Test: temper-be-py/tests/programs/py-builtin-params.temper.md, its
  golden, and `runs`' host interop (`Option(help=…)`), checked by mypy
  and pyright.
- **Python names bound more than once** (2026-11-17): Kotlin's Python
  numbers every local and annotates each, so no name is bound twice.
  Tempest's locals share a name where Python's one scope per function
  allows (nullables above), and only some are annotated, so a name a
  function binds more than once (shared, or a `var` assigned again) is
  annotated only where first bound, with its widest type, and there
  only where checkers may infer the value as narrower: nullable, an
  interface's, `AnyValue`'s, a generic or function type's (a tuple for
  a `Sequence`, a list literal's length). A first binding narrower than
  that type, or a walrus (`??`), is declared on its own line before it;
  a parameter shares no name with a nullable local of its type. A cast's
  local is annotated also under `orelse`, and a builder made from a
  list literal is annotated, which pyright would take for a list of
  literals. Otherwise mypy rejected a later annotation (`no-redef`) or
  a wider value (a `Sequence` after a tuple), as in Argmas and
  Wherewith.
  Test: temper-be-py/tests/programs/py-builtin-params.temper.md, its
  golden, checked by mypy and pyright.
- **Lua's `goto`:** Kotlin's list of Lua keywords leaves out `goto`
  (reserved since Lua 5.2), so a Temper name `goto` gives Lua that
  doesn't parse; tempest reserves it. And Kotlin says it supports Lua
  5.1 (its docs, and `lua >= 5.1` in its rockspecs, which tempest keeps
  for compatibility), but its output uses `goto` for every early return
  and loop exit, which 5.1 can't parse (LuaJIT can). Tempest's uses
  `goto` only for `continue` and for leaving an outer loop, so a program
  without those parses on 5.1 too.
  Test: temper-be-lua/tests/functional.rs, `HIDING` (`end(goto: Int)`),
  for the name; Lua 5.1 isn't tested.
- **Lua names hiding standard globals:** Kotlin's Lua escapes only
  keywords, so a local, parameter, or module value named `string`,
  `table`, `type`, `error`, or another of Lua's standard globals hides
  it for the rest of its scope, from inlined host code too. Tempest's
  give those names a `_` (`string_`), Lua 5.1's and LuaJIT's globals
  (`unpack`, `bit`, `jit`) included. Members, and exports as their
  module's table keys them, keep their names (`string = string_`), so
  the API is Kotlin's.
  Test: temper-be-lua/tests/functional.rs, `GLOBALS` and
  `no_lua_global_is_declared`; temper-tmpl/tests/naming.rs,
  `exports_keep_public_names`.
- **Lua bodies:** besides numbered names, Kotlin's Lua declares every
  module-level name in one `local` line at the top and assigns each
  later, keeps a `0.0` for the console, wraps early returns in labels
  and `goto`s, logs through `temper.log`, concatenates through
  `temper.concat`, and escapes every non-ASCII byte in strings
  (`'\xc3\xa9a'`). Tempest's declares locals and local functions where
  they're first given a value, returns directly, and writes `print`,
  `..`, and strings as UTF-8. Kotlin also writes the rockspec's
  `source.url` as an absolute `file://` path to a zip it makes; tempest
  names the zip relative, as Kotlin does when it has no path, and makes
  none.
  Test: temper-be-lua/tests/goldens/.
- **Lua `Int64` constants:** Kotlin's Lua writes each `Int64` literal
  as a call where it's used (`temper.int64_constructor(1)`), so a loop
  calls the runtime for each literal each time round, several times as
  slow on Lua 5.4 and twice as slow on LuaJIT. Tempest's makes each
  distinct constant once, first in the chunk (`local int64_1 =
  temper.int64_constructor(1)`; past 32 of them, fields of one `int64s`
  table), and uses its name.
  Test: temper-be-lua/tests/goldens/ (`types-int-shifty`),
  functional.rs, `runs` (`locals`, for the table).
- **Stale files in `temper.out`** after renames break builds; record the
  files each backend writes and delete only those no longer written. Tempest empties a library's directory before writing it
  (2026-10-30), which also drops what builds and test runs left there.
  Test: tempest/tests/cli.rs, `build_writes_temper_out`.
- **`tempest build` and `temper.out` (2026-10-30):** the same layout,
  `temper.out/BACKEND/LIBRARY/…`, package files, and runtime copy
  (`temper-core`, without its dotfiles and own tests, Lua's rockspec named
  for the version). Until 2026-11-23 a test compared it with Kotlin's
  record for the functional tests; matching Kotlin is no longer a goal,
  so now a snapshot of tempest's own layout pins it
  (`crates/tempest/tests/layout.txt`). Where it differed from Kotlin's,
  and why:
  - Source maps only for JS's modules so far (`.internal.js.map`), not
    its re-exports or other backends'.
  - No zip for LuaRocks, whose rockspec names it relative (above).
  - A `CMakeLists.txt` per C++ library (decided 2026-10-30).
  - Runtime files of tempest's own: C#'s `Panic.cs`, a panic no orelse catches, where Kotlin fails fast;
    C++'s `net.hpp`, std net's pluggable client (patch 0019); and C++'s
    `stack_trace.{hpp,cpp,cmake}`, for stack traces.
  - Types Kotlin leaves out and tempest keeps: an interface nothing uses
    (C#'s `IB3_Deep.cs`, Java's `B3Deep.java`), and a class whose
    statics Kotlin inlines (static-properties-scope's `Simple`).
  - A Python package named like a standard module gets a `_` (`types_`).
  - A library's directory emptied before it's written, so no stale file
    survives (the bug above); and no `data` backend, so the default
    backends are Kotlin's but that one. A library's config is read for
    its `export let KEY = "VALUE";` lines only, where Kotlin runs it as
    Temper.
  Test: tempest/tests/layout.rs.
- **An imported `var` that its module writes later** (2026-10-30):
  Kotlin's backends disagree on what the importer reads, Python a copy
  made at import (as the interpreter's `let` gives), JS the current
  value (a live `import` binding). Tempest copies it when the import
  runs, explicitly in tmpl, so every backend reads what the interpreter
  does ([tmpl/modules.md](tmpl/modules.md)).
  Test: programs/modules/, on every backend.
- **Several modules' init** (2026-10-30): Kotlin's Java and C# load
  only the root module, leaving imported modules to lazy class
  initialization, and Lua's root doesn't require modules it doesn't
  import; C++ names each module's init for its path's last part, so
  two can clash; and C++ needs std for a library whose tests only use
  it. Tempest runs every module's init in Temper's order on every
  backend, and keeps tests' needs out of the library's own build. Its
  package files name what's imported, where Kotlin's name what's left
  after folding. Lua's root doesn't require a module that imports it,
  directly or through others, as a library's tests module may, since
  that `require` would recurse (fixed 2026-11-12, regex parser).
  Test: programs/modules/ and programs/imports-library/, on every
  backend; temper-tmpl/tests/text/problems.cases, "module init order
  (F5, R6)".
- **Importing a library by its bare name:** Kotlin resolves
  `import("other-lib/sub")` by the library's name, but, by reading its
  `ModuleAdvancer`, a bare `import("other-lib")` looks for a directory
  named `other-lib` inside that library's root, since the path after the
  name is taken from index `slash + 1` with no slash found (not run;
  `import("other-lib/")` works). Tempest takes both to mean the library's
  root module.
  Test: programs/imports-library/ (`import("modules")`), on every
  backend.
- **Generated APIs:** proposals in api-compat.md cover numbered names,
  Lua's null sentinel, Rust's optional parameters, Java's `to_` and
  `<Lib>Global`. Not yet covered: C#
  `A98_Rgb`, public C# private constructors and module-private classes,
  Java map literals too slow to infer, Lua rockspecs that don't install,
  and C++ headers exposing private members.
- **Variance (recommended change):** Kotlin Temper honors `@out` and
  `@in` on type parameters, so it accepts a `List<Dog>` as a
  `List<Animal>`. Temper plans invariant type parameters, since C++ and
  Rust, among others, can't express variance well, and tempest stays
  invariant ([typing.md](typing.md), "Type parameters are invariant").
  Recommended: Kotlin Temper drops variance too. Tempest won't report
  what only variance accepts until code relying on it is found and
  changed.
  Test: settle/types.cases, "function types compared part by part" and
  "list literals typed from the type wanted".

- **Kotlin Temper's move to its newer types** (PR #500, in review
  2026-10-29) carries `Never<T>` with its fallback type into its tmpl,
  as tempest's tmpl does, and keeps "may bubble" as a result type beside
  a separate signature, which tempest avoids by treating bubbling as an
  effect. Its ad hoc function types now also separate whether a method
  has `this`, a question to answer whenever tempest interns function
  types. Notes only; no change to tempest's plans.
- **Classes in the backends** (2026-10-30): Kotlin-compatible in names,
  constructors and factories (Java and JS builders and `new(props)`,
  Rust `builders`, C++ `make`), properties (Python `@property`, Java
  `getX`/`setX`, C# properties, JS accessors, Rust and C++ getter
  methods, Lua `T.get`/`T.set`), and layout (Java's and C#'s file per
  type), with these fixes and differences on purpose:
  Test: programs/classes.temper.md and `kit::CLASSES` on every backend,
  with each backend's goldens; the items below name more.
  - **Private members read on another instance** work on every backend;
    Kotlin's emit a getter or attribute that doesn't exist (Java
    `other.getHidden()`, Python `other.hidden`).
    Test: programs/classes.temper.md (`Counter`).
  - **Statics initialize in Temper's order,** in the module's init where
    the class is: Kotlin's Python ran them before functions they call
    (`NameError`), its C++ at namespace scope in no order across files
    (since #636 in the module's init too), and its Java and C# when the class is first touched. Java keeps
    Kotlin's `static final` field where the initializer can't tell when
    it runs; C# reads a static through a property of the module class.
    Test: programs/classes.temper.md; funtests classes/static-properties
    and classes/static-properties-scope.
  - **Abstract members are abstract:** Python `@abstractmethod`s, with
    abstract properties declared; C++ `= 0` with `override` on
    overriders; JS type-only fields, so a mixin can't copy a stub over
    an inherited default (Kotlin's JS logs `undefined` for an abstract
    `a()` beside another interface's default); Lua none, where Kotlin's
    call a `temper.virtual()` its runtime doesn't define.
    Test: funtests interfaces/pure-virtual and
    interfaces/property-members, with the type checkers and
    `-Wsuggest-override`.
  - **Diamond defaults** follow Temper's pick (nearest, breadth-first,
    then `extends` order) on every backend, by forwarders only where the
    host would pick otherwise; C++ interfaces are virtual bases (Kotlin's
    can duplicate one); C# hiding members say `new`.
    Test: temper-be-csharp/tests/functional.rs, `DISPATCH`;
    settle/run.cases, "an interface's body beats another's abstract
    member".
  - **C++ aliasing:** an argument read from a `var` field is copied into
    a local first, since Kotlin's `const &` lets a callee that reassigns
    the field read freed memory (checked under ASan).
    Test: temper-be-cpp/tests/functional.rs, `LIFETIMES`, under ASan.
  - **Lua:** `is` tells apart same-named classes of two modules (Kotlin's
    compares bare names); a static named like a runtime key (`methods`)
    gets a `_` rather than replacing it; a plain `toString` is
    `__tostring`.
    Test: temper-be-lua/tests/functional.rs, `MEMBERS` and
    `MEMBERS_READ`.
  - **Python:** a library named like a standard module (`types`) gets a
    `_` (`types_`), which Kotlin's hides; `@operator("==")` also defines
    `__eq__` (surface.md). A plain `toString` is `__str__`, as Kotlin's
    is since #641.
    Test: temper-be-py/tests/goldens/imports-types.py (`types_`).
  - **Rust:** `Display` for a plain `toString`, `PartialEq` for `==`
    (surface.md); only `var` fields are locked, each in its own
    `RwLock`, read through getters so no guard outlives a read.
  - **Java:** builders null-check reference parameters; C# keeps braces
    around every `if`, so no `else` attaches to the wrong one. C#'s
    `ToString` is an `override`, as Kotlin's is since #641.
  - **Kept, not dropped:** declarations nothing uses (an unused interface,
    a class whose statics Kotlin inlines) stay in tempest's output.
- **Nullables in the backends** (2026-10-30): Kotlin-compatible
  spellings (Python `T | None`, JS `T | null`, Rust `Option<T>`, Java
  `@Nullable` boxes, C# `T?`, C++ `NullableParam` or a null
  `shared_ptr`, Lua `temper.null`), `orelse` as each host's `try` (Rust
  a closure's `Result`, Lua `pcall`), with these fixes and differences
  on purpose:
  Test: programs/nullables.temper.md and `kit::NULLABLES` on every
  backend.
  - **`panic()` isn't caught by `orelse`:** Kotlin's Python, Java, C#,
    C++, Lua, and JS throw the same thing for `panic()` as for
    `bubble()`, so `let p(): Int throws Bubble { panic() }` and `let
    g(): Int { p() orelse 2 }` give 2, silently, where Temper's docs
    call a panic unrecoverable. Tempest's Python raises
    `BaseException`, Java an `AssertionError`, C# `Core.Panic`,
    C++ the runtime's `panic()`, and Rust `panic!()`, none of which
    `orelse` catches. JS and Lua, whose `orelse` catches anything,
    marked a bubble instead (2026-11-18; since 2026-11-27 their handlers
    catch only declared failures, JS by `instanceof` and Lua by
    `temper.catch`), and a JS panic is a plain `Error("panic")` (Lua's
    `error("panic")`, a string). So a panic or a host error (a
    `TypeError` in a callback, Lua's "attempt to index a nil value")
    passes every `orelse`, as in the other backends. Builtins that
    failed by a host error bubbled then (now each throws a declared
    failure or panics, as the 2026-11-25 entries above say): JS's
    `Int64` `/` and `%` by zero (`divInt64` and `modInt64`, where
    Kotlin's `clampInt64(a / b)` throws BigInt's `RangeError`), a code
    point out of range or a surrogate (Kotlin's `RangeError`), a
    `StringIndexOption` cast (Kotlin's `TypeError`), a regex that finds
    nothing (Kotlin's `Error`), and an awaited promise rejected with anything
    (Kotlin's throws the rejection's reason, `undefined` for
    `breakPromise()`); Lua's integer `//` and `%` by zero (Lua's own
    error, or `intold.lua`'s `assert`), its runtime's
    `error("out of range")` and `error("bad parse")` in `Float64` and
    `Int64` conversions, `String.toInt32` with a radix `tonumber`
    rejects, `String.toInt32` of what isn't a number (Kotlin's compares
    `nil` with a number), and `as` a class of `temper.null` or a
    builtin's value (Kotlin's indexes its missing type tag). C++'s
    `panic()` now flushes what was logged and prints `panic` before
    `std::terminate`, where Kotlin's (and tempest's before) lost both. Kotlin's Lua translates `panic()` as
    `temper.bubble()`, so even the type of what's raised can't tell.
    Test: programs/panics.temper.md on every backend, and each
    backend's nullables golden (`checked`).
  - **Temper catches only what Temper declares** (2026-11-18): Kotlin's
    Java `orelse` catches `RuntimeException` (its bubble is a plain
    one), C#'s is a bare `catch` (its bubble a plain `Exception`), and
    Python's `except Exception` (its bubble `RuntimeError()`), so a
    host exception anywhere under an `orelse` (a callback's
    `NullPointerException`, a `KeyError`, a list read past the end)
    became its fallback. Tempest's handlers catch only what Temper
    declares: from 2026-11-18 a bubble type of the runtime's own (Java
    `temper.core.Bubble`, C# `TemperLang.Core.Bubble`, Python
    `temper_core.Bubble`), and since 2026-11-27, with Bubble gone, only
    the declared failures' classes. Everything declared to bubble then
    raised it: `as` (Java `Core.cast(C.class, x)`,
    `Core.requireNonNull(x)`, or `(C<A>) Core.check(C.class, x)`
    where Kotlin's casts or calls `Objects.requireNonNull`; C#
    `Core.Cast<T>(x)` where Kotlin's casts), and builtins whose host
    calls failed with host errors: number parses (Java
    `NumberFormatException`, C# `FormatException` and
    `OverflowException`, Python `ValueError`), conversions out of
    range (C#'s and Python's `OverflowError`s), code points out of
    range (all three), a missing key in `Mapped.get` and
    `MapBuilder.remove` (C# `KeyNotFoundException`, Python
    `KeyError`), `Date`'s constructor and `fromIsoString` (Java
    `DateTimeException`, C# `ArgumentOutOfRangeException` and
    `FormatException`, Python `ValueError`; and JS and C++, which
    rolled invalid dates over, gave an invalid `Date`, or kept the
    fields as given), string index
    casts, a regex that finds nothing (a plain `RuntimeException`,
    `Exception`, or `RuntimeError`), Python's `Int` and `Float64`
    division by zero (`ZeroDivisionError`, where `Float64` division by
    a nonzero literal stays `/`), and an awaited promise broken by
    anything (Java's `ExecutionException` wrapped in a
    `RuntimeException`, C#'s and Python's the breaker's own). A builtin
    not declared to fail passes its host failure through every
    `orelse`, as a panic (and index errors are panics, below). Java's regex `replace` no longer
    swallows a format callback's exceptions, and Python's
    `mapDropping` dropped only elements whose callback bubbled. Host code
    fails by raising a declared failure's class; anything else passes.
    A top-level `panic()` that ends a Java module's static initializer
    calls `Core.throwAssertionError`, which javac takes, where a `throw`
    there fails to compile ("initializer must be able to complete
    normally").
    Test: programs/panics.temper.md and programs/host-errors.temper.md
    (a regex `replace` callback's panic too) on every backend, and each
    backend's host interop, which catches `async-turns`'s `halve` as the
    type api-compat.md promises; no Temper API reaches Python's
    `list_map_dropping`, a Kotlin runtime leftover, so nothing tests it.
  - **Index errors panic everywhere, as core's docs say** (2026-11-18):
    a list read out of bounds (`List.get`, `Listed.get`), a `set` or an
    insert (`add`, `addAll` at) out of bounds, `removeLast` and a
    `Deque`'s `removeFirst` when empty, `reduce` of an empty list, and
    a string read at or past its end (`String.get`). Kotlin's runtimes
    disagree: C++ and Lua bubble for all of them (Lua's `set` past the
    end silently grows the list, as JS's ignores it); JS bubbles for
    `removeFirst` but fails with a `ReferenceError` elsewhere
    (`listed.js` calls `bubble()` without importing it) and a plain
    `Error` for strings; Java, C#, and Python throw host exceptions
    (`IndexOutOfBoundsException`, `ArgumentOutOfRangeException`,
    `IndexError`, …), which Kotlin's catch-all `orelse` turns into
    bubbles; Kotlin's interpreter panics for a negative list index but
    bubbles past the end. Tempest's runtimes panic as each backend's
    `panic()` does, with the interpreter's messages (`index 5 out of
    bounds for length 2`, `string index 2 out of bounds`, `removeLast
    on an empty ListBuilder`, `removeFirst on an empty Deque`): Java an
    `AssertionError` (`Core.listGet`, and new `Core.listSet`,
    `Core.stringGet`, and `Core.listAdd` at a place, which also keeps
    Temper's argument order), C# `Core.Panic` (`Listed.Get`,
    `Set`, `Reduce`, and `RemoveFirst`, where Kotlin's indexes, calls
    `Enumerable.Aggregate`, or `Dequeue`), Python a `BaseException`
    (`list_builder_remove_last` where Kotlin's calls `pop`), JS an
    `Error` (`listedReduce` where Kotlin's calls `reduce`), Lua
    `error`, C++ `panic`, and Rust `panic!` with the same messages,
    where it unwrapped. `splice` clamps its index on Lua and Rust too,
    as the docs say and the other backends do, where Lua bubbled and
    Rust panicked past the end.
    Test: programs/host-errors.temper.md, a test per case, at the
    bounds too (a `set` at the end, Lua's old growth), each failing
    past an `orelse` with its message, on every backend; funtest
    types/list/operations for `splice`.
  - **A test that panics fails alone, saying why** (2026-11-18):
    Kotlin's C++ reports each such test ("uncaught exception: bubble")
    only because its `panic()` throws what `orelse` catches; tempest's
    terminated the whole run ("no test results", every test not run),
    and C#'s `Environment.FailFast` ended the test process, in Kotlin
    too. Now C++'s `panic()` calls a hook first, `panic_hook()`, which
    a test build's `run_tests` sets to throw `TemperPanic` (which no
    handler catches, as failures are `Expected` values) and catches test by
    test, reporting `Panicked: MESSAGE`; outside tests it still prints
    its message and terminates. C#'s `panic()` and the runtime's index
    errors always throw `TemperLang.Core.Panic`, as Java,
    JS, and Python throw theirs: MSTest reports it as the test's
    failure, host code may catch it as it may elsewhere, and uncaught
    it still ends the process with its message (an async step's too,
    which the runner no longer prints and swallows, nor does an await
    turn into a bubble). Python's test runner reports an error's
    message (a panic's `index 5 out of bounds for length 2`), where
    Kotlin's left it empty, so `temper test` showed only `Traceback
    (most recent call last):`. The other backends' runners already
    caught panics per test.
    Test: programs/test-panics.temper.md on every backend and the
    interpreter (each test's failure and that all five ran);
    repl/specials.cases, "a test that panics fails alone".
  - **A test's own failure wins** (2026-11-23): Kotlin's generated
    tests end `finally { softFailToHard() }`, so a test that panicked
    after a failed soft `assert` reported the assertion, not the panic.
    Tempest's keep the body's failure and still end the test after it:
    JS, Java, and C# keep it and call the runtime's `endTest(test,
    failure)` in `finally`, Python runs the body in `with
    end_test(test):`, each finishing the test's async work, then
    `softFailToHard`, each even if the other failed, and attaching what
    else failed to the failure that wins (Java suppressed, Python
    notes, JS `suppressed`, else stderr); Lua's `temper.test` says the
    soft failures after the panic; Rust still runs the work of a body
    that failed. JS tests' rounds use `setImmediate`, not `setTimeout`,
    which Node clamps to a millisecond a test.
    Test: programs/test-panics.temper.md (`dies after failing`) and
    test-async.temper.md (`own failure wins`) on every backend.
  - **`Test.fail(message)`, and hard failures no `orelse` catches**
    (2026-11-19): std's `Test` gains `fail(message): Never<Void>`,
    which Kotlin's lacks: it records the message and ends the test, so
    `if (n == null) { test.fail("…") }` narrows `n` after it (flow
    facts know a `Never<T>` method called on a parameter or local
    declared with its class's type, before typing). `assertHard` and
    `softFailToHard` fail through it and no longer say `throws Bubble`,
    and `bail()` is bodiless, `Never<Void>` without `throws Bubble`.
    Kotlin's `bail` bubbles on JS (before this chunk, tempest's too),
    Lua, Rust (`return Err`), C++ (a thrown bubble), and the
    interpreter, so an `orelse` in the code under test swallowed a hard
    assertion; Lua's `test_asserthard` lost its message too. Now each
    backend's `bail` throws what no `orelse` catches, with the test's
    messages, which its runner reports as the test's failure: Java's
    `AssertionError` and C#'s `AssertFailedException`, as Kotlin's;
    Python `raise AssertionError(…)`, where Kotlin's `assert False, …`
    goes on under `python -O`; JS core's `panic(messages)`, a plain
    `Error`, where Kotlin's imports Node's `assert` for `strict.fail`;
    Lua an error with LuaUnit's failure prefix; Rust `panic!("{}",
    messages)`; C++ the runtime's `TemperTestFailure`, which
    `run_tests` reports as it is; the interpreter a test failure that
    only its test runner stops, reporting the messages as they are.
    Rust's tests end `soft_fail_to_hard(); Ok(())`, and no function
    ending in a call that never returns gets an unreachable `Ok(())`.
    Test: programs/test-fails.temper.md on every backend and the
    interpreter (each test's failure, text it mustn't say, and that
    all nine ran); settle-std/testing.cases.
  - **`Test.assertPanics { … }`** (2026-11-19): std's `Test` gains
    `assertPanics(block, message = "expected a panic")`, which Kotlin's
    lacks: it passes if the block panics; if the block returns, the
    test fails with the message, and if it throws an error, with the
    message and ", but it threw" (", but it bubbled" before 2026-11-26).
    Soft, like `assert`; a `test.fail` in the
    block still ends the test (where `bail` panics, its body bails
    again). The trailing block goes to `block`, so
    `test.assertPanics("why") { … }` gives the message. Its body calls
    a bodiless `panics(block): Boolean`, each backend catching, in
    process, any failure but a declared one or a test framework's
    (2026-11-23, one rule everywhere, so a host's error counts): Java
    all but `Failure`s, opentest4j's failures, and
    `VirtualMachineError`; C# all but `Failure`s and MSTest's, NUnit's,
    and xUnit's failures; Python all but `Failure`s, `AssertionError`,
    and the `BaseException`s that are exits (`KeyboardInterrupt`,
    `SystemExit`, pytest's outcomes); JS all but declared failures and
    `AssertionError`s; C++ test builds all but `TemperTestFailure`; Rust `catch_unwind` (with the panic hook quiet
    for caught panics, so a runner reading the first panic message
    still sees the test's own), where a failed assertion is a panic
    too; and the interpreter its program's panics but not its limits
    (steps, depth, interrupts). Lua's `Test` is the runtime's, so
    `temper.test_assertpanics` does it all with `pcall`: any error but
    a declared failure or a test failure, Temper's or LuaUnit's, counts as a
    panic. Test: programs/host-error-tests.temper.md (host errors of
    three kinds and a host test framework's failure per backend). C# also writes a
    function literal whose body is just `panic()` as a local function,
    where it said "not yet: a Panic expression".
    Test: programs/test-assert-panics.temper.md on every backend and
    the interpreter.
  - **`(do { panic(); 1 }) orelse 2`** fails Kotlin's build ("does not
    export symbol return__3"); tempest translates it, Rust leaving out
    the value after the panic (2026-11-04), which rustc's
    `unreachable_code` warned of.
    Test: programs/nullables.temper.md (`stops`), on every backend.
  - **Lua `Int64.toInt32()`** calls a runtime function only Lua before
    5.3 has, so on 5.4 `x.toInt32() orelse -1` silently gives -1;
    tempest's runtime defines it for every Lua, checked by converting
    back, and its `toInt32Unsafe()` on LuaJIT and 5.1 gives the low 32
    bits signed, where Kotlin's gives them unsigned.
    Test: funtest types/int/limits (`Actually big: no`) on Lua 5.4 and
    LuaJIT.
  - **C# numbers by the invariant culture,** formatting and parsing
    (`Float64.toString`, `String.toFloat64`), where Kotlin's runtime
    under `sv-SE` logs `1.5` as `1,5.0` and fails to parse `"1.5"` (#641
    made its case folding, `StartsWith`, and `IndexOf` culture-free,
    but not these); braces kept on every `if`, so no dangling `else`
    (above).
    Test: temper-be-csharp/tests/functional.rs, `runs`, under `sv-SE`
    (funtests types/float/*), and `DISPATCH` for the braces.
  - **Java:** `==` on two narrowed boxes unboxes one, and a conditional
    mixing a null box with a scalar boxes the scalar, as Kotlin's does
    by writing `if`s; `==` with a side that may be null is
    `Objects.equals`.
    Test: programs/nullables.temper.md (`sameInt`) on Java.
  - **Narrowing after an early return** (above, "Null and `is`
    narrowing") is used by the shared `nullables` program, so Kotlin
    rejects that program as written; an intended difference.
  - **`??` on a `var`** reports "Cannot assign to Int32 from Int32?" in
    both, since the macro reuses the name and a `var` isn't narrowed.
    Recommended for both: a temporary for a `var` subject.
- **Generics in the backends** (2026-10-31): Kotlin's surface for
  generic functions, classes, and interfaces and core's lists, maps, and
  pairs (Python `TypeVar`s and `Sequence`, JS `@template` and frozen
  arrays, Rust bounded type parameters and `temper_core`'s `List`, Java
  and C# generics with `java.util` and `System.Collections.Generic`
  types, C++ templates, Lua tables), with these fixes and differences on
  purpose:
  Test: programs/generics.temper.md and `kit::GENERICS` on every
  backend.
  - **JS `getOr` of a map with null values** gives the fallback in
    Kotlin's runtime (`mappedGetOr` is `map.get(key) ?? fallback`), so a
    `MapBuilder<String, Int?>` with `"n"` set to null logs `0` for
    `mb.getOr("n", 0) ?? -2`, silently, where Temper gives null and
    `-2`. Tempest writes `m.has(k) ? m.get(k) : fallback` where the value
    may be null.
    Test: programs/generics.temper.md (`mb.getOr("n", 0) ?? -2`).
  - **Rust:** a class bound (`T extends Named`) names a trait Kotlin
    never writes (`NamedTrait`), and a string literal for a type
    parameter that's `String` stays `&str` (`Box::new("boxed")` for a
    `Box<Arc<String>>`); neither compiles. Tempest bounds a class's type
    parameter by `Into` the class, and writes the literal as the
    `Arc<String>` the type wants.
    Test: programs/generics.temper.md, under clippy.
  - **C++ `const &` aliasing of a list field:** Kotlin passes
    `this->list` by reference to a method that reassigns the field, so
    the parameter then reads the new list (`"fresh"` where Temper gives
    `"kept"`), silently. Tempest copies the `shared_ptr` first, as it
    does for objects; the ASan-checked `lifetimes` program covers lists
    too.
    Test: temper-be-cpp/tests/functional.rs, `LIFETIMES` (`Names`),
    under ASan.
  - **Java `==` on two boxes from generic code** (`a[0] == b[0]` for
    `List<Int>`s) compares references in Kotlin's (`Core.listGet(a, 0)
    == Core.listGet(b, 0)`, false for 1000); tempest unboxes one.
    Test: programs/generics.temper.md (`firstSame`) on Java.
  - **Type checkers:** Kotlin's JS fails `tsc --checkJs --strict`
    (frozen arrays as `Array<T>`, `mapBuilderConstructor()` without the
    argument its declaration wants); tempest types `List` as `readonly
    T[]`, casting for the runtime's helpers. Python's type variables are
    invariant, as Temper's are, where Kotlin makes an interface's
    covariant.
    Test: the JS `runs` (`tsc --checkJs --strict`) and Python `runs`
    (mypy and pyright, strict).
  - **C# `new TemperLang.Core.OrderedDictionary<K, V>()`,** qualified,
    since .NET 9's `System.Collections.Generic` has one too.
  - **A type parameter no parameter's type mentions** (`CONTEXT` in
    `combine<CONTEXT extends Context, T>(x: T): T`) can't be inferred by
    Rust or C#: Kotlin's Rust fails rustc's E0283 (its C# should fail
    C#'s CS0411 the same way, not tried).
    Tempest's frontend binds it, as Kotlin's does, to what the call says
    or else its bound (`AnyValue` if none), and tmpl carries every
    generic call's type arguments, so Rust and C# spell them all out
    there (`combine::<Context, i32>(1)`, `Combine<IContext, int>(1)`)
    and nowhere else, and Rust allows clippy's
    `extra_unused_type_parameters` on such a function. C++ already
    writes every call's. Where only the body mentions one (`new
    ListBuilder<T>()`), Python annotates the body with its bound, as
    mypy and pyright reject a type variable the signature doesn't bind.
    2026-11-10.
    Test: programs/generics.temper.md (`tagged`, `countOf`, `tally`)
    on every backend.
- **Core's other types in the backends** (2026-11-02):
  Kotlin's surface for string indices, `StringBuilder`, `Empty`,
  `Deque`, `DenseBitVector`, and more of `String`'s and `Float64`'s
  members ([tmpl/profiles.md](tmpl/profiles.md), "As built, core's
  other types"), with these fixes:
  Test: `kit::CORE_TYPES` on every backend, with its checkers: funtest
  types/list/empty for Python's `Empty`, types/string/indices for C++'s
  `Compare::eq`, types/string/build for Rust's `build-main`.
  - **Python annotates `Empty` as `'_empty'`,** which nothing defines,
    so mypy and pyright reject it; tempest writes `tuple[()]`. Two
    locals that share a name in a Python function now share a type too
    (with or without null), and one declared inside another's
    initializer gets its own, since checkers give a name one declared
    type, and a walrus assigns before the outer annotation.
  - **JS's JSDoc names core types by names nothing defines**
    (`Deque_2<string>`, `Console_1`), which `tsc --checkJs` rejects.
  - **C++ writes `StringIndexOption.eq` as a bare `==`,** so comparing
    an index with itself fails `-Wtautological-compare` under
    `-Werror`; tempest calls `Compare::eq`. A private function's
    default-argument overloads are written only for the argument counts
    calls use, so none is unused.
  - **Rust names a library's binary for the library,** and cargo
    forbids one named `build`; tempest names it `build-main`. Generated
    code also passes clippy where Kotlin's only allows warnings.
  - **C# analyzers** (CA1834, CA1806, CS0219): one-unit appends as
    chars, `_ = new C(…)` for a constructor called for effect, and an
    unread module `let` of a literal left out.
  - **Named arguments that skip core's optional parameters** pass
    `Omitted` in tmpl, and each connection writes the language's null
    there, as Kotlin's output does.
  - Not fixed: Java's `round` is `Math.round`, which saturates and
    makes NaN 0, and Python's rounds half to even; on LuaJIT and Lua
    5.1, Kotlin's Lua runtime misparses (`tonumber(s, 10)` takes
    `"2.0"`) and lets `float64_max` miss a NaN, so `types/string/read`
    and `types/float/ops` run only on Lua 5.3 or later. (Its miscount
    there, `countBetween` of the whole string without `utf8`, tempest's
    runtime fixes.)
- **Divisions that can't fail, and maps, in the backends** (2026-11-03):
  `/` by an integer constant other than 0, and `%` by a positive one, is
  tmpl's `divSafe` or `modSafe`, as Kotlin's `DivIntIntSafe` and the
  like, wherever it is, a speed choice (a zero divisor panics), and
  each backend spells it as Kotlin's (Java's own `/`, Rust's
  `wrapping_div`, C#'s `Core.DivSafe`, C++'s `Int::div_safe` or its own
  `/`, JS's `a / b | 0`). Typing takes a builtin generic type's
  constructor's parameters from the type arguments `new` gives it, so
  `new Map<Int, Int>([])`'s `[]` is a `List<Pair<Int32, Int32>>`, as
  Kotlin types it. With these fixes:
  Test: `kit::DIVISIONS` on every backend, with its checkers.
  - **JS:** the runtime declares `listedReduceFrom`'s and
    `mappedToListWith`'s functions with the wrong parameters, and
    `mappedToMap`'s result as a `FreezeMap`, which isn't a `Map` to
    `tsc`; tempest's runtime fixes their JSDoc
    ([change 0001](../docs/internals/detail/06-backends.md#changes-from-kotlin-tempers-runtimes)), so calls need no casts.
  - **Python:** a one-expression literal passed where core's method
    wants a function that gives back the receiver's type parameter
    (`reduce`'s `fn (T, T): T`) is a `def`, as all of Kotlin's are,
    since pyright narrows `(2, 3, 4)` to literals and then rejects the
    lambda's `int`; a local made empty by `MapBuilder` or `ListBuilder`
    is annotated, as Kotlin annotates every local.
  - **Java and C++:** a function literal in the init block doesn't take
    the name of a local of the init around it (`(sum2, n) -> sum2 + n`
    in `int sum = …`), which Java rejects and C++'s `-Wshadow` reports;
    Kotlin numbers every name. C++ names no lambda parameter the body
    doesn't read (`-Wunused-parameter`), as it already did for
    functions, and passes a concatenation where a runtime template
    deduces a type parameter as a `core::Str` (`core::Str("n" +
    s)`), since `+` makes a `std::string`.
  - **Java** reduces to another kind than the items' through Core's
    reducers (`listedReduceIntToObj`), as Kotlin's; to another object
    type, not yet. Java, C#, and C++ drop `orelse void`'s `void`.
  - **Lua** runs `types/int/shifty` on Lua 5.4 and LuaJIT: tempest's
    runtime does `Int64`'s bitwise operators and shifts on LuaJIT's
    tables of halves, and counts only `countBetween`'s range without
    `utf8`, both of which Kotlin's runtime gets wrong there. Rust's
    clippy allows `map`'s unused locals, which the test declares on
    purpose.
- **Running tests with std's `testing`, in the backends** (2026-11-03):
  std's `Test.bail()` is each backend's connection, as Kotlin's, and its
  private method isn't declared: Python's `assert False,
  str(test.messages_combined())`; Rust's `return
  Err(temper_core::Error::with_optional_message(…))`, where Kotlin's
  binds the `Err` to a local and returns it if it isn't `Ok`; JS's
  `strict.fail(test.messagesCombined() ?? undefined)`, as Node's `fail`
  takes no null, where Kotlin's passes the null on (a `tsc` error with
  `@types/node`); Java's `Core.throwAssertionError(…)`; and Lua's
  runtime functions, named from every std key as Kotlin's are
  (`temper.test_assert`). Java's is `Core.throwAssertionError(…)`; C#'s
  `throw new AssertFailedException(test.MessagesCombined() ?? "")`, as
  Kotlin's; C++'s the runtime's `testBail(test)`. Names match Kotlin's,
  Rust's `TestStruct` fields included (`passing`, `failed_on_assert`,
  `messages`; 2026-11-03, after a chunk that numbered them): Rust's
  fields are named apart from methods (`fields_apart`), and only the
  private accessor functions tempest adds for `var` fields, which
  Kotlin's Rust doesn't have, take a number where a member has the name
  (`passing2()` beside the getter `passing()`). Python's private storage
  is `_messages`, as its naming knows fields' storage and private
  members take a `_`. The
  test runners are the tests' own where Kotlin's need packages: Node's
  built-in runner, `node:test` (`it` imported from it, `package.json`
  running `node --test "test/**/*.test.js"`, no dev dependencies), where
  Kotlin's runs Mocha with `mocha-junit-reporter`, so no 2 s per-test
  time limit; tests files are `test/<module>.test.js` where Kotlin's
  are `test/<module>.js`, so `bun test` and `deno test` find them too;
  Java's JUnit launcher and C#'s small reflection runner over MSTest's
  attributes, where Kotlin's run Maven's surefire and `dotnet test`.
  std's own Java and C# names are Kotlin's: package `temper.std`, Maven
  `dev.temperlang:temper-std` with JUnit, and namespace
  `TemperLang.Std` with MSTest's packages (`TemperLang.Std.csproj` was
  `Std.csproj`). In C#, a name a namespace beside std's hides (`Core`,
  inside `TemperLang.Std`) is written `global::TemperLang.Core.Core`;
  Kotlin's grammar notes the case but doesn't handle it. C# gives a
  conditional with a block's value or an `orelse` in a branch as an
  `if` assigning the place, and `FormatFloat64` calls
  `MemoryExtensions.AsSpan` by its class, so it needs no `using
  System`.
  Test: programs/testing.temper.md and `kit::TESTS` on every backend.
- **Python's module-level names** (2026-11-04): a library module binds
  no public-looking name but its exports. Its imports are private
  aliases (`from temper_core import mapped_for_each as
  _mapped_for_each`, `from modules import sum_ as _sum_`), as are its
  `TypeVar`s (`_T`), function literals lifted to module level (`def
  _fn9`, where Kotlin writes `_fn_97`), loop labels there (`_outer`),
  and hoisted temporaries (`_t_0`, as before); names inside functions
  stay as they were. Kotlin's private declarations and temporaries
  already take `_`, but its imports (`TypeVar as TypeVar2`) and type
  variables (`T_1`) don't, so `from lib.mod import *` and completion
  offer them as if they were the library's. Each module also sets
  `__all__` to exactly its exports, which Kotlin's doesn't: compatible,
  since `import *` then gives only names that were public anyway (a
  module's submodules, which are attributes of a parent module once
  imported, are left out too). The library's `__init__.py` keeps
  star-importing the root module and naming its modules, and tests
  files, which nothing imports, keep plain names.
  Test: temper-be-py/tests/functional.rs, `runs`, which checks every
  generated library module and std's with `tests/module_names.py`.
- **std's `regex`, in Python and JS** (2026-11-03): std's
  connected regex members are each backend's runtime functions, as
  Kotlin's (`temper_core.regex.regex_compiled_found(self, compiled,
  text)`, `regexCompiledFound(this, …)`), and aren't declared in their
  classes. `doPure { … }` is its block's body in place, as Kotlin's
  `DoPureFn` erases the call. Python imports every name under a private
  alias (`from collections.abc import Sequence as _Sequence`, beside
  std's regex class `Sequence`; below, "Python's module-level names"),
  where Kotlin's aliases every import with a number (`Sequence as
  Sequence17`). Kotlin's runtime types four of its regex
  helpers wrong for `tsc` (`compiled` as a `RegExp`, `regexCompiledSplit`
  without `regexRefs`, and `out` as `[string]`); tempest's runtime fixes
  their JSDoc ([change 0001](../docs/internals/detail/06-backends.md#changes-from-kotlin-tempers-runtimes)), so std calls them plainly.
  Test: `kit::WITH_STD` (regex/match, regex/zero-advance), with `tsc`
  and the Python checkers.
- **std's `regex`, in Rust, Java, C#, and C++** (2026-11-03): each
  backend writes Kotlin's support code into std's output (Rust's
  `src/regex/support.rs`, Java's `temper/std/regex/Core.java`, C#'s
  `Regex/RegexSupport.cs` and `IntRangeSet.cs`; C++'s is in its
  runtime), and connects the same keys Kotlin's support networks do;
  std's crate's `Cargo.toml` has `regex = { version = "=1.12.2",
  optional = true }`, and a crate that imports `std/regex` asks for its
  feature, as Kotlin's. Rust writes `mod support; use support::*;`,
  where Kotlin's `pub use` re-exports nothing (the functions are
  `pub(crate)`) and rustc warns of it, and passes `self`, not `&self`,
  as clippy's `needless_borrow` wants; std's crate allows `dead_code`
  and `unused_variables` (std declares what it never uses, and
  `adjustCodeSet`'s body ignores `regexRefs`), where Kotlin's allows
  every warning in every crate. A statement `orelse` whose body jumps
  (`RegexFormatter.pushCode`'s `return`s) runs the body in place in a
  labeled block, as Kotlin's (`'ok: { 'orelse: { … } fallback }`), each
  `?` in it `if let Ok(v) = e { v } else { break 'orelse; }`. A
  constructor may run statements that don't touch `this` among its
  fields' assignments (`Regex`'s `let formatted`). Java calls
  `temper.std.regex.Core` by its full name everywhere, where Kotlin's
  does only where `temper.core.Core` is imported. Java's and C#'s naming
  keeps types apart from functions and module `let`s
  (`NamingRules::types_apart`), so std's local class `Digit` is `Digit`
  beside its module `let` `Digit`, as Kotlin's, which `RegexSupport.cs`
  names. C# names a class that a namespace this code is in hides from
  `global::` (a library `match`'s namespace `Match` and std's class
  `Match`), which Kotlin's doesn't. A Rust unused closure parameter
  named by a raw identifier is `_match`, not `_r#match`. Java connects
  neither `adjustCodeSet` nor `pushCaptureName`, whose entries Kotlin's
  Java network has commented out (tempest's table had listed them).
  Test: `kit::WITH_STD` on those backends, Rust's under clippy.
- **std's `regex`, in Lua** (2026-11-03): a
  `return`, `break`, or `continue` out of an `orelse` body returns a
  signal from the function `temper.pcall` runs, and an `if` after it
  redoes the jump, as Kotlin's `TryStatement` (`local ok, value, kind =
  temper.pcall(…)`; `return v, "return"`, `return "break", "flow"`).
  Kotlin's builds the checks for an unlabeled `break` and `continue`
  but drops them, so those fall through after the `orelse`; tempest's
  redoes every jump, in one `if … elseif …` chain, where Kotlin's nests
  them (`kind == "return"` first, then the jumps' names, where Kotlin's
  checks `kind == "flow"`).
  Test: `kit::WITH_STD` on Lua.
- **std's `temporal`, `Date` as a connected type** (2026-11-04): Python,
  JS, Java, and C# connect `Date` to `datetime.date`, `globalThis.Date`,
  `LocalDate`, and `DateTime`, and leave std's class out, as Kotlin's;
  but where Kotlin keeps its members that aren't connected
  (`encodeToJson`, `decodeFromJson`) as private module functions, with
  `@json`'s `DateJsonAdapter`, tempest has neither yet (its `@json`
  skips std's connected `Date`, so `Date.jsonAdapter()` is "No
  member"). Rust, C++, and Lua declare the class, as Kotlin's.
  Differences that fix Kotlin's bugs (Kotlin fixes to make):
  C#'s `dayOfWeek` is `((int)d.DayOfWeek + 6) % 7 + 1`, where Kotlin's
  `TemporalSupport.IsoWeekdayNum` gives Sunday 0, and C++'s adds the
  same to the runtime's `getDayOfWeek`, which also gives Sunday 0; C#'s
  `toString()` formats in the invariant culture, where Kotlin's uses the
  current culture's calendar. Rust's `today()` is tempest's own support
  code, std only, where Kotlin's uses the `time` crate (and its
  `powerfmt`), so std's crate has no new dependency; its `temporal`
  feature is declared empty, and `today()` is re-exported (`pub use
  support::*`) as Kotlin's. JS calls `dateConstructor` for every
  `new Date(…)`, where Kotlin inlines `Date.UTC` for a literal year of
  100 or more. Kotlin's bugs kept, as the runtimes' own: C++'s
  `makeDate` and JS's constructor don't check the date (Feb 30 rolls
  over rather than bubbling), JS's `fromIsoString` gives an invalid
  `Date` rather than bubbling, and C++'s `today()` is local, not UTC.
  Test: funtest types/date on every backend, C#'s under `sv-SE`.
- **std's `json`, in Java, C#, C++, and Lua** (2026-11-04): mostly as
  Kotlin's. Java boxes an override's result where the overridden
  method's is a type parameter (`Boolean decodeFromJson`) by returning
  the box, Java boxing at `return`, where Kotlin also moves the body
  into a private helper; for a boxed parameter both do, tempest's helper
  named `encodeToJsonUnboxed` (numbered past the class's names) where
  Kotlin's is the name made distinct (`encodeToJson__12`), a private
  name either way. C# has Kotlin's `Optional<T>` for `T?`, wrapping and
  unwrapping at calls; its tests allow the analyzers' CA1707 and CA1725,
  which std's names, as Kotlin gives them, break. C++ writes
  `static_cast<void>(x);` after a scalar local nothing reads, where
  `-Wunused-variable` would stop the strict build. Lua casts to `Mapped`
  with `cast_to_map`, where Kotlin calls `cast_to_mapped`, which its
  runtime lacks (its second `cast_to_map` is that check), and writes a
  cast of a cast as one `@as`, the outer, where LuaLS would read only
  the first.
  Test: funtest types/json/syntax-tree on those backends, with their
  checkers.
- **`@json`** (2026-11-13): adapters as Kotlin's `JsonInteropPass`
  generates them, with its names and JSON (`PointJsonAdapter`,
  `encodeToJson(p)`, static `decodeFromJson(t, ic)` and `jsonAdapter()`;
  the custom, concrete class, and sealed interface strategies;
  `@jsonName`, `@jsonExtra`, and nullable and list fields), written as
  Temper text and settled as written code is ([json.rs](../crates/temper-frontend/src/json.rs)).
  Differences: a sealed interface's `encodeToJson` is abstract, where
  Kotlin's switches on `this`'s class to call its adapter, as each
  `@json` subtype has its own (C++'s `const` methods can't give `this`
  out); a decoder casts each built-in field's tree into a local of its
  own (`let xJson = obj.propertyValueOrBubble("x") as JsonNumeric;`),
  where Kotlin's reads from the cast, which pyright types as its
  argument; locals are declared with their values, where Kotlin's
  assigns them after; a field outside the constructor is encoded but
  not decoded, where Kotlin's passes it as a named argument the
  constructor lacks; a known `null` from `@jsonExtra` is tested with
  `is JsonNull`, where Kotlin's reads its `content`, which it has none
  of; and a module with `@json` loads `std/json` and its names resolve
  there whatever the module imports, as Kotlin's resolved names do. Not
  yet: generic types, which Kotlin's interpreter fails on ("An operation
  is not implemented: (TypeFormal …)") and whose static members C# and
  C++ can't call yet; and std's connected `Date` (above).
  Test: settle/json.cases; programs/json-adapters.temper.md on every
  backend.
- **std's `json`, in Python, JS, and Rust** (2026-11-04): std's `json`
  is all Temper, as Kotlin connects none of it. A function that ends in
  a call that never returns (then `bubble()`) ends in its statement,
  where tempest returned its value.
  Python casts to a core type by its Python class (`cast_by_type(x,
  dict)`), as Kotlin's `argless` types, and annotates a local a cast
  initializes, which pyright otherwise types as the cast's argument.
  `stringToInt64` gives a `bigint`, which Kotlin's JSDoc says is a
  `number`; tempest's runtime says `bigint`
  ([change 0001](../docs/internals/detail/06-backends.md#changes-from-kotlin-tempers-runtimes)), so `tsc` passes std's
  `JsonNumericToken.asInt64`. Rust
  writes a value `orelse` whose fallback fails as `if let Ok(v) = … {
  v } else { … }`, and a constructor's statements that read fields
  already set (`JsonTextProducer`'s `this.stack.add(…)`) among its
  fields' locals; std's crate allows clippy's `collapsible_if`,
  `manual_range_contains`, and `question_mark`, which its json source
  trips, where Kotlin's allows every warning.
  Test: funtest types/json/syntax-tree on those backends, with their
  checkers.
- **Builtin values as `AnyValue`, and `List.of`, in the backends**
  (2026-11-04): APIs as Kotlin's (`thing1: AnyValue` is
  Rust's `temper_core::AnyValue`, C++'s `shared_ptr<AnyValueBase>`,
  Java's `Object`, C#'s `object`); bodies differ where Kotlin's don't
  pass the checkers tempest runs.
  Test: `kit::ANY_VALUES` on every backend, with its checkers.
  - **Python** annotates `AnyValue` as `object`, where Kotlin's writes
    `typing.Any`: the same values fit, and a checker makes a reader
    check what it has. A cast to an interface with abstract members
    (`y as Apple`) passes mypy's `type-abstract` on that line, since
    tempest's interfaces have `@abstractmethod`s and Kotlin's are
    empty; `x as T` for `x: T?` is `cast_none(x)`, Kotlin's an `if x is
    None: raise`.
  - **C++** boxed a string literal as a `std::string`, where Kotlin's
    boxed the `const char*`, an `AnyValueBox<const char*>` that a check
    for a boxed string wouldn't match; since #654 both box a
    `core::Str` (`any_box(TEMPER_STR("two"))`), tempest's a
    concatenation too (`any_box(core::Str(…))`).
  - **JS:** be-js's `listBuilderSplice` JSDoc takes neither a null
    `index` nor a read-only `newValues`, so strict tsc rejects Kotlin's
    own calls of it; tempest fixes the types
    ([change 0002](../docs/internals/detail/06-backends.md#changes-from-kotlin-tempers-runtimes)).
  - **Rust** casts a nullable value with `x.and_then(cast::<T>)`, as
    Kotlin's, where tempest had passed the `Option`, and clippy's
    `approx_constant` is allowed in the tests for as-expr's `3.14`.
- **Labeled loops and classes declared in functions, in the backends**
  (2026-11-03): a class declared in a function is declared at its
  module's level, as Kotlin's `ClosureConvertClasses` does, but only one
  that names none of the function's locals (Kotlin's captures them in a
  closure record; tempest's says "not yet"). Code after a `while (true)`
  that nothing breaks is left out, where javac and rustc's
  `unreachable_code` reject it. The `eq` of a field-less class below an
  `@auto("==")` interface is just `other is C`, which clippy's
  `needless_bool` wants. With these:
  Test: `kit::LOOPS_AND_CLASSES` on every backend, with its checkers.
  - **Python** jumps out of an inner loop through the runtime's `Label`
    and `LabelPair`, as Kotlin's, named `outer` (numbered past any other
    name) rather than for the source's label; a module's file is named
    as its import is (`extra_class.py` for `./extra-class`); an empty
    `Deque` is annotated, as `ListBuilder`'s are; and a local nothing
    reads has pyright's `reportUnusedVariable` ignored.
  - **Rust** builds a generic class's builder (`ChangeBuilder<T>`); a
    local nothing reads, or a function literal's parameter, is named
    with a leading `_`, where Kotlin's `#![allow(warnings)]` hides the
    warning; a local declared in a loop is `mut` only if that loop's body
    assigns it more than once on a path; and the printer follows rustfmt
    on long conditions (operators leading continuation lines, `{` on its
    own line), field chains over `chain_width`, a lone `g(…)?` argument
    (`f(g(\n…\n)?,)`), and a block under `&*` as a last argument.
  - **Java** builds a generic class's `Builder<T>`; **C#** writes a
    module function's `where` constraints, and runs a block value ahead
    of its statement past reads of `var`s that no block value assigns.
  - **JS** makes a generic class's `static new(props)` generic itself,
    as `tsc` rejects a static member naming the class's type parameters,
    and declares an interface's inherited abstract properties again,
    since `tsc` sees nothing through the runtime's `type(I)`, so `J
    extends I` satisfies a bound of `I`. Since 2026-11-12 it also
    declares inherited methods with default bodies, and those of
    generic interfaces, with the type arguments the interface supplies
    (`Labeled<L, T> extends Holder<T>` has `Holder`'s `held` as a `T`).
  - **Lua** bounds `---@generic IT: lib.I`, and types a local of a
    function's type parameter `any`, since LuaLS knows the `---@generic`
    name only in the function's own annotations.
- **Parameters' defaults in the backends** (2026-11-01):
  Kotlin's surface for optional parameters (nullable, defaulting to null
  where the language can say so, overloads in Java and C++), with
  plainer bodies: the default set in place where the language allows
  (`if end is None: end = …`, `end ??= …`, `let end =
  end.unwrap_or(…)`) or one expression into a local (`int end2 = end ??
  …`), where Kotlin writes an `if`/`else` into a fresh local for each,
  and nothing for a default of null. Rust's options builder leaves out
  the builder field nothing reads when a constructor has no required
  parameters, which rustc warns of in Kotlin's. Not yet: tempest
  doesn't report Kotlin's "Optional arguments must follow required
  arguments", and translates such a parameter as Kotlin's types would
  (backends give only the trailing ones defaults); Kotlin's Rust backend
  throws on it.
  Test: programs/defaults/ and `kit::DEFAULTS` on every backend.
- **Function values in the backends** (2026-10-31): Kotlin's surface for
  function types and `@fun` interfaces (each backend's plain function
  type, Java's `java.util.function` interfaces by signature or one
  synthesized in `PKG.function`, as Kotlin's), literals, references to
  functions, and calls of values, with prettier bodies (one-expression
  literals inline as lambdas, arrows, or one-line functions where Kotlin
  hoists each to a named function), and these fixes:
  Test: programs/functions.temper.md and `kit::FUNCTIONS` on every
  backend.
  - **C++ captures an assigned local by reference** (`[=, &count]`), so
    a closure returned from its function writes a dead stack slot:
    undefined behavior, which ASan reports as a stack use after return.
    Tempest keeps such a local in a `std::shared_ptr` the lambda copies,
    and a lambda that uses `this` copies the member's `shared_ptr` to it.
    Test: programs/functions.temper.md (the counter), under ASan.
  - **A static method as a value doesn't compile** in Rust
    (`Scaler::triple()`, a call with no arguments), Java (`triple`, after
    `import static`, which names no value), or C++
    (`Scaler::temper_statics::triple.value`). Tempest writes
    `Arc::new(Scaler::triple)`, `Scaler::triple` (a method reference), and
    `Scaler::triple`.
    Test: programs/functions.temper.md (`Scaler.triple`).
  - **Java:** a function value whose interface differs from the
    parameter's (`Float64Float64Int32` where `ToIntBiFunction<Double,
    Double>` is wanted) is adapted by a method reference, as Kotlin's,
    and so is one for a generic parameter (`IntUnaryOperator` where
    `Function<T, T>` is), which Kotlin passes as it is.
  - **C#:** a lambda's parameters are typed, so a generic callee infers
    its type arguments (`MakeHi((int i) => i + j)`), which Kotlin's
    didn't compile (its test notes it); a method group is cast to its
    delegate only where a generic method takes it.
  - **Python:** `ListBuilder.sort` casts its list for the runtime's
    `list_builder_sort`, annotated `List[T]` where a `ListBuilder` is a
    `MutableSequence`, which `mypy --strict` rejects in Kotlin's output.
  - **JS:** Kotlin exports every top-level function its hoisting makes
    (`export function fn`) and its `forEach` temporaries (`export const
    this_`, `n`, `i`); tempest's literals stay inline or local, and its
    temporaries unexported.
  - **Python closures made in a loop share its `let`s:** Kotlin's
    nested `def` reads the loop's one variable, so three closures of
    `let k = i * 10` all give `20`, silently, where Temper's `let` is
    fresh each time round (`0 10 20`). Tempest makes such a closure by a
    factory that takes the loop's locals it captures, `make_fn(k)`.
    Test: programs/functions.temper.md (`let k = i * 10`).
  - **Not yet:** a local function that calls itself; `@fun` interfaces as named Java
    or C# interfaces (the Kotlin-compatible variant uses Kotlin's
    types).

