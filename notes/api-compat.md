# Public API compatibility

Part of the [rewrite plan](README.md).

Generated libraries must keep today's **public surface**, so existing users
can't tell the difference. Private and non-exported members, function bodies,
local names and file internals may change, and should get prettier. A *few*
deliberate public changes are fine where there's good reason; each is listed
under "Proposed changes" with its reason, for the team's sign-off.

Sources: the backend tests (`JsBackendTest`, `PyBackendTest`,
`JavaBackendTest`, `RustBackendTest`, `CSharpBackendTest`, `CppBackendTest`,
`LuaBackendTest`, plus translator and module-structure tests), the naming code,
the runtimes, and real output: Chromarite's `temper.out/`, built by Kotlin Temper.

## Rules shared by all backends

- **Exports:** what Temper exports is public; everything else is private or
  internal in the target language's terms.
- **Null means "use the default"** for optional arguments, in every language.
- **Builders or named-argument forms** exist only for a *public* constructor
  with 2 or more parameters (not counting `this`): JS `static new(props)`,
  Java and Rust `Builder`s. Python always allows keywords instead.
- **Bubble (failure)** is an unchecked exception in JS, Python, Java, C# and
  C++, `Result<T>` in Rust, and `error(...)` in Lua. It never appears in
  JS/Python/Java/C# signatures. Each runtime raises a bubble type of its
  own, which `orelse` catches alone, so panics and host exceptions pass
  it (since 2026-11-18). Each subclasses what host code caught before,
  which stays API: Python `temper_core.Bubble(RuntimeError)`, Java
  `temper.core.Bubble extends RuntimeException`, C#
  `TemperLang.Core.Bubble : Exception`, JS `Bubble extends Error`, and
  C++ `TemperBubble` (unchanged). Lua's `pcall` catches it as any error.
- **Panics aren't failures:** what core's docs say panics (an index out
  of bounds, `panic()`) is Python `BaseException`, Java `AssertionError`,
  C# `TemperLang.Core.Panic`, JS and Lua a plain error, Rust `panic!`, and
  C++ the end of the process (outside test builds). Before 2026-11-18
  some index errors were host exceptions (Python `IndexError`, Java
  `IndexOutOfBoundsException`), which host code catching those no longer
  sees; that's deliberate.
- **Generated classes aren't meant to be subclassed** by host code (Java
  enforces it with `final`).
- **Runtime libraries** (`temper-core` per language) are copied unchanged, so
  container, null and async types stay identical.

## Async in connected code

Temper's `async` blocks run one turn at a time on a runner, and an `await`
ends a turn, on every backend. Connected code that does I/O or other slow
work follows two rules on every backend:

- **Never block inside a turn.** Blocking I/O (or a blocking wait on a
  promise) stalls every async block, as in Node. Start the work elsewhere
  (the platform's async API, a worker thread, or the host's executor) and
  return a promise.
- **Return the backend's own promise type, completed from anywhere.**
  Completing a promise from any thread or callback is safe; it queues the
  waiting block's next turn on the runner. The types are the runtime's:
  a C# `Task`, a Java `CompletableFuture`, a JS `Promise`, a Python
  `concurrent.futures.Future` (made by `temper_core`, so asyncio can
  `await` it), and `temper_core`'s promises in C++, Lua, and Rust.

So I/O concurrency is unlimited while Temper code runs one turn at a time.
Where to start I/O per backend: C# `Task` APIs; Java's `sendAsync`, NIO, or
virtual threads; JS's promises; Python's asyncio or threads; Rust's
executor (tokio's I/O, say) or threads; C++ threads or a library such as
Asio; Lua's host (OpenResty, luv).

Blocking on a promise that hasn't settled, from inside a turn, raises in
Python, Java, Rust, and C# (`Async.GetPromiseResult`) rather than
deadlocking; outside the runner it waits as before. C# settles a promise
when the turn that settles it ends, so host code after an `await` never
runs inside a turn (it may resume on the runner's thread, where blocking
throws too). JS, Lua, and C++ run on one thread and can't block.

**Host code's own await and loops.** Generated promises and generators
work the language's standard way:

| Backend | Await a promise | Loop over a generator |
|---|---|---|
| C++ | `co_await` (C++20) | range-for (C++14) |
| C# | `await` (a `Task`) | `foreach` |
| Java | `join`, `thenApply` | for-each (`Iterable`) |
| JS | `await` | `for…of` |
| Lua | (no standard) | `for _, v in g do` |
| Python | `.result()`, asyncio's `await` | `for` |
| Rust | `.await` (a `Future`) | `for` (`Iterator`) |

Each loop sees what the generator yields (`yield(x)`), where Kotlin's
generator backends drop it (kotlin-differences.md, "Generators that
yield values").

The runner is pluggable where that's clear: Python's
`set_async_runner(asyncio_runner(loop))`, Java's
`Core.setAsyncRunner(executor)`, C#'s `Async.SetAsyncRunner(run)`, and
Rust's `Config`'s runner. Each backend's `tests/host/` has host code
that does all this against generated code, run by its functional tests.

## What's free to change everywhere

Private and local names (which should all become source names, numbered
only on real conflicts; see [design.md](design.md), Naming); temporaries and
labels; private struct layouts (such as Rust's `XStruct`); static storage
names; how function bodies are written; helper functions; file-internal
organization not visible to importers.

## JS

**Layout.** `js/<npm name>/` with `package.json` (`"type": "module"`,
depends on `@temperlang/core`). Each Temper module `src/foo` is two files:
`src/foo.js` re-exports the public names, and `src/foo.internal.js` holds
everything. `package.json` `exports` maps `"./src/foo"` to `./src/foo.js` and
`"."` to `./index.js`; internal files and `_`-prefixed files aren't exported.
`index.js` re-exports the root module and imports the others for their side
effects. File names keep Temper's directory names.

**Mapping.**

- `let` → `export const`, `var` → `export let` (live binding), functions →
  `export function f(a)`, called positionally. Optional parameters check
  `== null` inside.
- Classes: `export class C extends type()` (from `@temperlang/core`), private
  fields `#x`, public properties as `get`/`set` accessors, positional
  constructor plus `static new(props)` for 2+ parameters.
- Interfaces: classes too; implementers use `extends type(I, J)`, which copies
  default methods; `instanceof` works.
- Statics: `static g()`, static properties as private static field plus
  `static get`, so assigning one throws. (Tempest stores the value in a
  module `let`, `Class$name`, which the init block outside the class sets.)
- Generics only in JSDoc; nullable is `T | null`; failure throws `Bubble`,
  an `Error`; promises are native; async uses the runtime's adapter.
- Declared failures (RFC 164, in progress): each failure type is a class
  under one of the library's roots, sealed interfaces it declares
  (`WherewithError extends Error`, natively, not through `type()`), a
  sealed one abstract between, so host code catches
  them with `instanceof`; `message` is a getter, `name` the class's name
  (set by the runtime's `failureBase`, beside an invisible marker
  symbol), the cause the standard `cause`, and a function failing with
  one says so with JSDoc's `@throws`. A failure type's own member named
  `name`, `stack`, or `cause` takes a `_` (`name_`).

**Naming.** Public names unchanged (camelCase). Reserved words get `_`,
including on members (`else_`, `if_`, `class catch_`), and names ending in `_`
get another. Numbers appear only in non-public places (parameter names, JSDoc
template names, import aliases, private fields).

**Keep:** everything above. **Free:** parameter names, JSDoc template names,
private field names, internal files' extra exports.

## Python

**Layout.** `py/<lib>/pyproject.toml` and a package `<lib_snake>/` (std is
`temper-std` / `temper_std`). Module `a` → `a.py`; a module with children →
`b/__init__.py` plus `b/c.py`. The root module is `<pkg>/<pkg>.py`; the
package `__init__.py` does `from .<root> import *` and imports every module.

**Mapping.**

- Functions: parameters are positional-only (`/`), so parameter names aren't
  API.
- Classes: `__slots__`, private fields `_x`, public properties via
  `@property`/`@x.setter`. Constructor parameters have clean names and accept
  keywords (`N(v=1)`); that's API.
- Interfaces: `class I(Generic[T], metaclass=ABCMeta)` with abstract
  properties and methods; default methods are ordinary methods; inheritance via
  bases.
- Statics: `@staticmethod`; static properties assigned after the class with
  `ClassVar` annotations.
- Generics via module-level `TypeVar`s; nullable is `Union[T, None]`; failure
  is `temper_core.Bubble`, a `RuntimeError`; promises are
  `concurrent.futures.Future`s, annotated as `temper_core.TemperFuture`,
  its subclass that asyncio can `await`
  (Kotlin's annotate `Future`; a host passing its own plain `Future` as
  a promise argument now needs a cast for strict checkers).
- Declared failures (RFC 164, in progress): each failure type is an
  exception class under one of the library's roots, sealed interfaces it
  declares, `WherewithError(Failure, Exception)`, with `temper_core.Failure` only as a marker (not an
  exception); `str(e)` is its message, its cause `__cause__`.

**Naming.** Values, functions, methods and properties are snake_case; types
keep their case. Reserved words get `_` (`from_`; `to` stays `to`). Internal
top levels get a leading `_`. Tempest differs: parameters and locals named
like builtins keep their names (`help=`, not Kotlin's `help_=`)
([kotlin-differences.md](kotlin-differences.md)).

**Keep:** module layout, names after snake_case conversion, constructor
keyword names, properties, `Future`, exception types.
**Free:** positional-only parameter names, private member names, `this` names.

## Java

**Layout.** Maven `pom.xml`; group, artifact and package from `JavaConfig`
(std: `dev.temperlang:temper-std`, package `temper.std`). Package = configured
package (or library name in snake_case) plus the module path. Per module:
`<Last>Global.java` (a final class of static functions, fields and
initializer), one file per type, synthetic functional interfaces in
`<pkg>.function`, a `<Last>Main` entry point, and tests.

**Mapping.**

- Exported → `public`, otherwise package-private. Classes are
  `public final class`; interfaces `public interface`.
- Public properties are a public field **and** a getter (`getX()`, or `isX()`
  for Booleans), plus `setX(v)` when settable.
- Interfaces: `default` methods; multiple inheritance forwards with
  `A.super.m()`.
- Optional parameters: telescoping overloads plus boxed `@Nullable` types;
  `Builder` classes for 2+ parameters.
- Generics `<T>`; nullable via `@temper.core.Nullable` with boxed types;
  function types use `java.util.function` where possible, else synthetic
  interfaces; failure is `temper.core.Bubble`, an unchecked
  `RuntimeException`; promises are `CompletableFuture`.
- Declared failures (RFC 164, in progress): unchecked too. Each failure
  type is a class under one of the library's roots, sealed interfaces
  it declares, `public abstract class WherewithException extends
  RuntimeException implements temper.core.Failure` (`Failure` only a marker); a sealed failure
  interface is an abstract class between, with a `protected`
  constructor; a type named `…Error` is `…Exception`. `getMessage()` is
  its message, `getCause()` its cause; stack traces kept. Functions list
  the failure they may throw in a `throws` clause, as documentation.

**Naming.** camelCase kept, types PascalCase. Reserved words *and* contextual
keywords get `_` (that's why Prismora, now Chromarite, had `to_`). Invalid characters become
`$xx`.

**Keep:** packages, class and member names, getters/setters, overloads,
builders, nullability annotations, `CompletableFuture`, failure as a
`RuntimeException`.
**Free:** private names; parameter names, though they show in Javadoc and IDE
hints, so make them pretty; type-parameter names (see Proposed changes).

## C#

**Layout.** Root namespace from config or the library name in PascalCase.
`src/<Lib>.csproj` (Kotlin's `net48;net6.0`, as its library template;
tempest's `net6.0` only, a gap; nullable enabled), `tests/`, `program/`. Module →
sub-namespace and subdirectory; one file per type; module top levels in
`public static class <Last>Global`, initialized by its static constructor; a
`Support` namespace with a library-wide init and `Logging.Init(...)`.

**Mapping.**

- Classes with private camelCase fields, public constructor, PascalCase
  properties and methods.
- Interfaces `IX : IBase`; default methods call a `protected static XDefault`
  helper.
- Optional parameters `int? i = null`; generic nullable via
  `TemperLang.Core.Optional<T>`.
- Collections: `IReadOnlyList`, `IList`, `IReadOnlyDictionary`,
  `IDictionary`; functions `Func`/`Action`; `Task<T>`; failure is
  `TemperLang.Core.Bubble`, an `Exception`.
- Exported `let`s are `public static` fields.
- Declared failures (RFC 164, in progress): each failure type is a class
  under one of the library's roots, sealed interfaces it declares,
  `public abstract class WherewithException : Exception, IFailure` (the
  runtime's marker), a sealed failure interface
  an abstract class between (no `I`), the rest `sealed`; abstract ones'
  constructors are `private protected`, so only the library extends
  them. `…Error` names say `…Exception`, as in Java. `Message` is
  overridden; the cause is `InnerException`, passed by constructor as
  hand-written C# does: each failure class's public constructor calls an
  `internal` one that also takes the cause (`: this(key, null)`), which
  a `throw` in a handler calls (`new LookupFailed(name, e)`); a failure
  made before, or another library's (its internal constructor out of
  reach), keeps its cause for Temper's `cause` alone. Functions failing with one say so in a doc comment, `///
  <exception cref="NotFound">`. A failure's member named as another of
  `Exception`'s (`Source`, `Data`, …) is "not yet".

**Naming.** Types PascalCase with `I` on interfaces; members PascalCase;
keywords escaped with `@`; a member named like its type gets `_`.

**Keep:** namespaces, type and member names, `I` prefixes, collection and task
types, init entry points, failure as an `Exception`.
**Parameter names are API in C#** (callers can use named arguments), and so are
type-parameter names in practice. Today they're numbered (`Fib(int i__0)`,
`T__0`); see Proposed changes.

## Rust

**Layout.** `Cargo.toml` (edition 2021, rust-version 1.71.1, `temper-core` and
Temper dependencies pinned with `=`), `src/lib.rs`, `src/main.rs`. Module →
`src/<snake>/mod.rs`; the root module re-exported at the crate root. Exported
items `pub`, others `pub(crate)`.

**Init.** `pub fn init(config: Option<Config>) -> Result<AsyncRunner>` must run
before anything else: statics are `OnceLock`s read with `unwrap()`.

**Mapping.**

- Classes: `pub struct C(Arc<CStruct>)`, or `Arc<RwLock<CStruct>>` when a field
  is mutable; `C::new(...)`, getters `x()`, setters `set_x()`; bubbly
  constructors return `Result<C>`.
- Interfaces: a trait `XTrait` (with a `clone_boxed`), a wrapper
  `pub struct X(Arc<dyn XTrait>)` with `X::new(impl XTrait)` and `Deref`, and
  forwarding impls of every ancestor trait. Implementing classes keep inherent
  methods and also implement the trait; upcasts are explicit
  (`A::new(b.clone())`). Downcasts via `temper_core::cast::<T>()`. Sealed
  interfaces also get an `XEnum`.
- Optional parameters are `Option<T>` or `Option<impl ToList<T>>`; builders
  (`builders::XBuilder`, `XOptions`) for public constructors with 2+ params.
- Types: `i32`, `i64`, `f64`, `Arc<String>` (params `impl ToArcString`),
  `List<T> = Arc<Vec<T>>` (params `impl ToList<T>`), `Option<T>`,
  `Arc<dyn Fn>`, `temper_core::Result<T>`, `Promise<T>`.
- Declared failures (RFC 164, in progress): a function failing with one
  returns `Result<T, F>`. A failure class is a class as above, with a
  `temper_core::Cause` field, implementing `Display` (its message),
  `Debug` (`NotFound { message: "…" }`), `std::error::Error` (its cause
  as `source()`), and `temper_core::Failure` (core's `Error`, also
  `Arc<dyn Failure>`), by `temper_core::impl_failure!`. A sealed failure
  interface is an `enum` of the types right under it, a library's root
  (`#[non_exhaustive]` where exported) among them, each `From` every type
  below it (`impl_failure_enum!`, `impl_failure_from!`), so host `?`
  converts; every failure type and enum also converts into
  `Arc<dyn temper_core::Failure>`, itself a `Failure`, so `?` gathers
  several libraries' failures there, as generated handlers of them do;
  a failure the library doesn't export is a variant whose type
  host code can't name. A library with one exported root, and no `Error`
  of its own, also has it as the crate's `Error` (`pub type Error =
  r#mod::WherewithError;`). A failure in a module's init is a
  `temper_core::Error` with it as source; `uncaught` says "uncaught
  failure" and each cause. Panics stay panics.

**Naming.** Types keep their case; functions, methods and properties snake;
statics `LOUD_SNAKE`; keywords `r#`. Public names are never numbered.

**Keep:** crate and module layout, type and function names, getters/setters,
`Result`, the container types, builders, `init` (as an entry point, even if no
longer required).

## C++

**Layout.** Per module `<lib>/<path>.hpp` and `.cpp`; root module
`<lib>/init.hpp`; a `main.cpp` that includes everything, calls every init and
runs tests; no build file. One namespace per library. Each header declares its
exports and `void global_init_<last segment>()`.

**Mapping.** Classes are structs used through `std::shared_ptr`, made by
`static make(...)`; properties are public fields plus `get_x()`/`set_x()`;
interfaces are structs with pure virtual methods and virtual bases; templates
for generics; optional parameters as overloads plus `NullableParam<T>`;
failure throws `TemperBubble`; lists are `shared_ptr<vector<T>>`.

- Declared failures (RFC 164, in progress): a function failing with one
  returns `temper::core::Expected<T, std::shared_ptr<F>>`, `std::expected`
  where the standard library has it (C++23), else the runtime's C++14 shim
  with the same API for what it has (`expected.hpp`); host code branches
  on `has_value()` and reads `error()`, and returns failures as
  `temper::core::unexpected(NotFound::make(key))`. A failure class is a
  class as above, below one of the library's roots, sealed interfaces it
  declares, `struct WherewithError : virtual temper::core::Failure` (core's `Error`, a `std::exception`: `what()` is
  its message, `get_message()` too, and `get_cause()` the failure it was
  thrown while handling); a sealed failure interface is an abstract struct
  between. Failures are never thrown, but one a module's top level
  doesn't handle leaves its init as a `temper::core::UncaughtFailure`
  exception, which the stack-trace handler reports as "uncaught failure".
  Panics and host exceptions pass every handler.

**Naming.** Temper names unchanged; keywords get a trailing `_`; collisions get
`_<n>`. Header declarations omit parameter names, but template bodies in
headers show numbered names.

**Keep:** headers, namespace, names, `make`, getters/setters, init functions.
be-cppv (experimental, unsupported) is not a contract worth keeping.

**Packaging and dependencies (Proposed, 2026-11-03; a compatible
addition).** No C++ package manager is used the way pip or npm are, so:

- **A CMake package first:** a `CMakeLists.txt` with installed and
  exported targets (`find_package(<lib>)`) and a release source archive.
  That covers `FetchContent`, CPM, `add_subdirectory`, and vendoring, and
  vcpkg and Conan build on it. Their metadata (a vcpkg portfile and
  manifest, a `conanfile.py`) can be generated as thin extras.
- **Third-party dependencies by CMake target,** never by package manager:
  `find_package(CURL)` and `CURL::libcurl`, which vcpkg, Conan, system
  packages, and vendored copies all provide, so the consumer's choice
  doesn't matter.
- **Boring, ubiquitous libraries:** libcurl for HTTP(S) when `std/net`
  comes to C++ (Kotlin's C++ has no networking today), not Boost.Beast,
  Asio, or cpp-httplib.
- **Never vendor TLS or crypto code,** so security fixes reach users
  through their own updates.
- **Optional:** the core needs no dependencies; networking sits behind an
  option (a CMake option, a vcpkg feature, a Conan option), so a library
  that never uses the network pulls in nothing.
- **Pluggable:** `std/net` stays connected with a host implementation an
  app can replace (a game engine's or embedded system's own HTTP stack),
  with libcurl as the default adapter.

## Lua

**Layout.** A rockspec, one file per module required as `'lib/module'`,
`init.lua` for the root, and `-internal.lua` files when tests need
unexported names.

**Mapping.** `temper.type('Hi', SuperA, ...)` classes with `constructor`,
`get`/`set` accessor tables, `methods`, and statics; construction is
`Hi(args)` with positional arguments; `obj.prop` and `obj:m()`; null is the
`temper.null` sentinel, which is returned to callers; failure is `error(...)`.

- Declared failures (RFC 164, in progress): each failure type is a
  `temper.type` under one of the library's roots, sealed interfaces it
  declares, `temper.type("lib.LibError", temper.Failure)`, raised with
  `error(x)`; host code catches them with `pcall` and tells them apart
  with `temper.instance_of(e, lib.NotFound)` (by own type, a root, a
  sealed interface's, or `temper.Failure`).
  `tostring(e)` and `e.message` give the message, `e.cause` the failure
  being handled where it was thrown, or `nil`; a failure type's member
  named `cause` is `cause_`. A panic stays a string.

**Naming.** Temper names unchanged; keywords get `_`; non-ASCII names are
hex-mangled. No public numbered names.

**Keep:** module paths, names, the class protocol, `temper.null` (see
Proposed changes), `temper.Failure` and failures' `message` and `cause`.

## Proposed changes

Ordered by value; each needs the team's sign-off.

1. **No numbered names where they're visible** (all backends). Parameter and
   type-parameter names become the source names: Java `@param x`, `<T>`;
   C# `Fib(int i)`, `<T>`; C++ header templates. For C# this changes names
   callers can use with named arguments, from ugly to the obvious ones, which
   seems worth it.
2. **Rust and C++: no required `init`.** Today forgetting it panics (Rust)
   or segfaults (C++), and a library's first module-level value silently
   makes it required. Proposal (2026-10-26), for predictable order:
   - **Each module initializes as a whole, on first use:** its top level
     runs in source order, after the modules it imports, the first time
     anything reads its state, much like Java's class initializers or
     Python's import. Rust: one `static INIT: Once` per module, checked
     by each exported function and getter that reaches module state (an
     atomic load; skipped where the compiler knows a function doesn't).
     C++: the same, with the module's init behind a function-local static
     (`static const bool done = (init_body(), true);`, thread-safe since
     C++11, and retried if init throws), while module variables stay at
     namespace scope with constant initialization only (null or zero), so
     there's no static initialization order problem. Today's
     `global_init_init()` guards with a plain `bool`, which isn't
     thread-safe and must be called by hand. Not the `static_init` crate
     either: its lazy statics add nothing over std's `OnceLock` and
     `LazyLock`, and its before-`main` statics share the `ctor` problems
     below (order across crates only by hand-set priorities, no way to
     report an error, no constructors on `wasm32-unknown-unknown`), as a
     dependency of every generated crate; revisit only if benchmarks
     show the first-use check costs something. No
     third-party crates: `ctor`-style before-`main` hooks have no order
     across crates, can't report errors, and are shaky on wasm.
   - Not per-static `LazyLock`: that runs initializers in whatever order
     they're first read, which breaks top levels with side effects, such
     as logging or one value built from another's mutation.
   - **Configuration apart from initialization:** today's
     `init(Some(config))` mixes them. Logging and async get their own
     optional setup, used only to change the defaults. For async, Rust's
     `Promise` implements `Future` (see history/milestone-3.md), so Rust code
     `.awaits` Temper promises on any executor; Temper's own `async`
     blocks run on a default runner unless the user hands over a spawner
     for theirs (tokio and the like). Whether `init` stays as a
     deprecated no-op for compatibility is open.
   - **Async, as built (2026-11-05):** `temper_core::Promise<T>`
     implements `std::future::Future<Output = Result<T>>`, polled with
     only `std::task`: wakers kept beside the waiters and woken on
     resolve (runtime [change 0003](../docs/internals/detail/06-backends.md#changes-from-kotlin-tempers-runtimes)). It adds no bounds, so `Send` follows the contents, as an
     actor model would want ([actors.md](actors.md)): a `Promise<T>` is
     `Send + 'static` and `Unpin` when `T` is, which today's `Arc` output
     makes it, and a `Promise<Rc<i32>>` still awaits on one thread
     (temper-core's tests check both). Temper's `async` blocks are state
     machines run by the configuration's runner (`run_async`); a user
     hands over their own by `init(Some(config))`. The machines hold no
     lock across a pause: they read locals by `read_locked`, which
     clones. **Next:** the runtime's `Task` and tempest's function values
     are `Send + Sync`, so a machine over `Rc`s wouldn't build yet; an
     adapter from a spawner (tokio's) to `AsyncRunner`.
   - **Upstream (Kotlin main, #636, 2026-10-31):** the first part is
     in. C++: each module's `global_init_<module>()` runs through
     `temper::core::init_once` (an atomic flag and a mutex, re-entry on
     the initializing thread returning at once, retried if init throws),
     module values and public static fields are `GlobalSlot`s with
     constant initialization only, and users read them through
     accessors, `x()` and `Type::x()`, which init first, as exported
     functions, constructors, and static methods do. Rust: a read of a
     module-level value goes through `temper_core::initialized`, which
     calls the crate's `init_on_first_use()`, `init(None)`, so the
     library initializes whole on first use, each module after those it
     imports; a read during init panics rather than re-entering. An
     explicit `init` still works, and configuration still goes through
     it.
   - **Tempest (2026-10-31):** both backends match it. C++: each header
     declares `global_init_<module>()` first, through `init_once`;
     module values in `temper_globals` and statics in a class's
     `temper_statics`, as `GlobalSlot`s, which the init constructs, then
     runs its imports' inits, then the rest in source order; `x()` and
     `Type::x()` accessors, giving `const &`s; and exported functions,
     public constructors and static functions of exported types, and
     accessors calling the init first (an empty init is `{}`, and its
     callers skip it). Two differences, both safer: an exported `var`
     that functions write has an accessor that gives a copy read under
     the module's lock, where a `const &` would race with their writes;
     and tempest keeps values only the top level uses as the init's
     locals. Rust: reads go through `temper_core::initialized` and
     `init_on_first_use()`, and `init`'s module inits through
     `temper_core::initializing`, but through a small `support`
     wrapper that waits for the whole init on other threads, where
     Kotlin's reads a value as soon as it's set, racing with the rest of
     the init's top level (kotlin-differences.md, "Rust first use").
     `init_on_first_use` is there only where something reads a value,
     since an unused one would warn.
3. **Rust: plain `None` for optional parameters.** Use concrete types
   (`Option<Arc<String>>`, `Option<List<T>>`) or separate functions instead of
   `Option<impl ToList<T>>`, so callers don't need `None::<Vec<f64>>`.
4. **Rust: simpler interfaces, keeping class wrappers.** Today's wrappers have a
   real advantage worth keeping: a class is always `pub struct C(...)`, so its
   source API stays the same whether it holds `Arc<CStruct>` (immutable, no
   lock) or a locked struct (mutable). A class that happens to be immutable
   skips the lock without users noticing. So:
   - **Classes keep wrappers.** Immutable classes use no lock; mutable ones use
     `Mutex` rather than `RwLock`. A `Mutex` is simpler and faster when
     uncontended, which is the common case, and Temper objects are rarely read
     concurrently enough for `RwLock` to pay off.
   - **Interfaces get simpler:** one trait per interface with supertraits, and
     `pub type X = Arc<dyn XTrait>` instead of a wrapper struct plus
     forwarding impls of every ancestor trait. Classes implement the trait
     directly; `From<C> for X` makes upcasts implicit with `.into()`, and Rust
     1.86+ trait upcasting (or a small `as_x()` helper before that) covers sub-
     to super-interface conversion. That removes the duplicated `clone_boxed`
     and the ambiguity it causes. It changes Rust users' interface types, so
     it's the most breaking item here; prototype on Chromarite before deciding.
   - **Make `Rc` vs `Arc` easy to compare:** generated code names its shared
     pointer and lock through `temper_core` aliases (such as
     `temper_core::Shared<T>` and `temper_core::Lock<T>`), chosen by a cargo
     feature. A single-threaded build can then use `Rc`/`RefCell` and be
     measured against `Arc`/`Mutex` without regenerating code.
5. **Python: add `__all__`.** Stops `from pkg import *` from leaking internal
   helpers such as `T_45`, `float34` and `list_get0`. Only removes junk.
6. **Java: keep `to` and `with` as method names.** They're contextual keywords
   only in module declarations. This changes `to_` to `to`, which is what users
   expect.
7. **C#: exported `let`s as `static readonly` fields or properties** instead
   of mutable public fields.
8. **Lua: return `nil` instead of the `temper.null` sentinel** to callers
   (it's truthy, which surprises Lua users). This changes behavior, so it's
   lower priority.
9. **C++: function-local statics instead of `global_init_*` calls**, and a
   namespace per module to avoid clashes between modules' exports. Only if C++
   users are few, since it changes headers.
10. **Java and C#: a nicer name than `<Last>Global`** for top-level
    functions. Java: `<Last>` (`ordable.Ordable`), a common pattern. C#:
    not the namespace's own name, which .NET's guidelines warn against
    since `Ordable.X` turns ambiguous; something like `Ordable.Functions`.
    A user class of the same name would collide, and choosing only when
    there's no collision renames it when a class is added, so pick once
    and record it in `temper.keep`, or let the library's config name it.
    Breaking for existing users, so only with sign-off.

Not proposed, despite being odd, because changing them would break users for
little gain: JS member-name escaping (`else_`), Java's public field plus
getter, and C++'s single namespace if item 9 is declined.

11. **Rust: a newer MSRV and edition 2024, with ranges, not exact pins**
   (queued, 2026-11-04; the user wants it sooner than later). Generated
   crates declare `rust-version = "1.71.1"` and edition 2021, and pin
   dependencies exactly (`regex = "=1.12.2"`, `temper-core = "=…"`) so
   transitive dependencies can't outgrow that MSRV. Raise the MSRV to
   1.85 (edition 2024 needs it; 1.84 has the MSRV-aware resolver),
   move to edition 2024, and use version ranges (`regex = "1.10"`),
   letting the MSRV-aware resolver pick versions that fit
   `rust-version`, so exact pins stop conflicting in users' dependency
   graphs. Raising the MSRV changes what users need, so it's a
   compatibility change to announce; Kotlin's be-rust would follow.
12. **Python: parameters keep builtins' names** (done in tempest,
   2026-11-17, the user's decision). Kotlin's give a parameter named
   for a builtin a `_`, so a constructor's parameters, which callers
   may pass by name, read `Option(help_=…)`. Tempest's keep the name
   (`help=`), escaping only keywords, and reach a builtin a function's
   own names hide through `builtins`, so the API never depends on a
   body ([kotlin-differences.md](kotlin-differences.md), "Python
   parameters named like builtins"). Callers using `help_=` must use
   `help=`; Kotlin's be-py would follow.
