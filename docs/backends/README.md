# Target languages

Temper translates to other programming languages through *backends*,
one per target language. Each backend:

- translates Temper to the target language's source,
- writes source files, source maps where the language has them, and
  package files (a `.csproj`, a `pom.xml`, a `package.json`, …) under
  `temper.out/BACKEND/LIBRARY/`, and
- runs the translated tests with the language's own tools.

Each backend has an ID, which the `-b` (`--backend`) flag of
`temper build` and `temper test` takes ([the command line](../tools/cli.md)).
By default, `temper build` builds C#, Java, JavaScript, Lua, Python,
and Rust; C++ is built when asked for (`-b cpp`).

| Language | ID | Targets | Package | Tests run with |
|---|---|---|---|---|
| [C++](#c-cpp) | `cpp` | C++14 (C++20 for `co_await`) | a CMake project | its own test program |
| [C#](#c-csharp) | `csharp` | .NET 6 (`net6.0`) | a `.csproj` | MSTest |
| [Java](#java-java) | `java` | Java 17 | a Maven `pom.xml` | JUnit 5 |
| [JavaScript](#javascript-js) | `js` | ES modules, with JSDoc types for TypeScript | a `package.json` | Node's `node:test` |
| [Lua](#lua-lua) | `lua` | Lua 5.1 and later, and LuaJIT | a rockspec | LuaUnit |
| [Python](#python-py) | `py` | Python 3.11 and later | a `pyproject.toml` | `unittest` |
| [Rust](#rust-rust) | `rust` | Rust 1.71.1 and later, edition 2021 | a crate | `cargo test` |

Each library's package has the version its `config.temper.md` gives,
and depends on Temper's runtime for its language, `temper-core`, and on
`std` if it imports std, at exactly the compiler's version (`temper
version`), in each ecosystem's spelling: `0.7.0-alpha.0.123` (a dev
build, 123 commits after the last release) for npm, Cargo, NuGet, and
Maven, `0.7.0.dev123` for Python, and `0.7.0.alpha.0.123-1` for
LuaRocks. `temper build` writes both beside the library, in
`temper.out/BACKEND/`. They aren't on package registries yet; each
[release](https://github.com/temperlang/tempest/releases) carries
them, packed as each registry takes them, for installing by hand.

How each backend is built, and what it chooses for failure, null,
naming, and order of evaluation, is in
[how the compiler works: backends](../internals/06-backends.md) and its
[per-backend table](../internals/detail/06-backends.md#per-backend-choices).
Getting started with each language is in the
[getting started guide](../guide/getting-started.md).

> **Note:** Temper's earlier releases also have `java8`, `mypyc`, and
> `data` backends, and an experimental `cppv`; they aren't available
> yet ([inconsistencies](../inconsistencies.md#i4-backends-not-available-yet)).

## C++ (`cpp`)

Translates Temper to C++14: a header and a source file per module
(`LIBRARY/init.hpp` and `init.cpp`), a `main.cpp` that runs the
library, and a CMake project. Objects are `std::shared_ptr`s, and a
library's namespace is its name.

Generated code compiles without warnings under `-Wall -Wextra
-Wpedantic -Werror`, and is tested under AddressSanitizer, UBSan, and
TSan. C++ leaves the order of evaluation of arguments open, so the
backend keeps Temper's left-to-right order wherever it would show.
C++20 code can `co_await` a Temper promise. A Temper failure throws
`temper::core::TemperBubble`, a `std::exception`.

> **Note:** Temper's older docs list C++ as "coming soon", and leave it
> out of this list; the C++ backend passes every functional test
> ([inconsistencies](../inconsistencies.md#i3-c-support)).

## C# (`csharp`)

Translates Temper to C#. Targets [.NET][CsDotNet] 6.0; [.NET
Framework][CsDotNetFramework] 4.8 might be supported in the future.

### Translation notes

[Temper function types][CsFn], such as `fn (Int): Int`, translate to
[`Func` delegates][CsFunc], or [`Action` delegates][CsAction] for a
`Void` result. These have an upper limit of 16 parameters, which isn't
accounted for yet. If demand arises, Temper will likely generate new
delegate types for more parameters.

[Temper nullable types][CsOrNull], such as `A?`, translate to nullable
value types or nullable reference types. Nullable reference types need
newer versions of C#, so .NET Framework 4.8 support would need to
represent these as ordinary reference types.

To show intent clearly, Temper collection types translate to .NET
generic interfaces, including "ReadOnly" variants as appropriate. For
example, Temper [`Listed<T>`][CsListed] and `List<T>` both translate to
[`IReadOnlyList<T>`][CsIReadOnlyList], whereas `ListBuilder<T>`
translates to [`IList<T>`][CsIList]. Similar rules apply to Temper's
`Mapped` types and .NET [`Dictionary`][CsDictionary] types, except that
maps created in Temper always keep insertion order. .NET's `IList`
doesn't subtype `IReadOnlyList`, even though its [`List`][CsNetList]
implements both, and likewise for `Dictionary`, so Temper generates
overloads of functions taking a `Listed` that accept any of `List`,
`IList`, or `IReadOnlyList`, and likewise for `Mapped`.

Each Temper library is translated to a .NET [project][CsNetProject]
and [assembly][CsAssembly], and each module to a
[.NET namespace][CsNamespace], with a subdirectory for each
subnamespace. Each type defined in Temper gets its own C# file, and a
module's top level goes in a static `…Global` class. Parameter names
are part of a C# API, so they're never renamed.

A Temper failure throws `TemperLang.Core.Bubble`, an `Exception`, and
an `orelse` catches only that, so other exceptions pass it.

Temper's [logging `console`][CsConsole] translates to the
[`Microsoft.Extensions.Logging` framework][CsMsLogging] on modern .NET.
That framework usually [creates loggers by dependency
injection][CsMsLoggingDi], which doesn't suit static classes and static
initialization, so Temper generates static methods to give a library
its `ILogger`. Without one, generated libraries log through
[`System.Diagnostics.Trace`][CsTrace].

### Tooling notes

Each Temper library gets a [`.csproj` file][CsCsProj], so each builds
to its own .NET assembly.

The only .NET naming setting in [`config.temper.md`][CsConfig] today is
`csharpRootNamespace`. It sets the project's root namespace, the
assembly's name, and the presumed NuGet [package ID][CsPackageId].
Microsoft has advice on [choosing a namespace][CsNamespaceSelection].

Temper [`test` blocks][CsTestBlocks] translate to the
[MSTest framework][CsMsTest]: a test class per module, and a test
method per test. Temper provides soft assertions within tests.

[CsAction]: https://learn.microsoft.com/en-us/dotnet/api/system.action-1
[CsAssembly]: https://learn.microsoft.com/en-us/dotnet/standard/assembly/
[CsConfig]: ../guide/tutorial/04-modules-and-libraries.md#library-configuration
[CsConsole]: ../language/builtins.md#console
[CsCsProj]: https://learn.microsoft.com/en-us/aspnet/web-forms/overview/deployment/web-deployment-in-the-enterprise/understanding-the-project-file
[CsDictionary]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.dictionary-2
[CsDotNet]: https://dotnet.microsoft.com/en-us/platform/support/policy/dotnet-core
[CsDotNetFramework]: https://dotnet.microsoft.com/en-us/download/dotnet-framework
[CsFn]: ../language/types.md#function-types
[CsFunc]: https://learn.microsoft.com/en-us/dotnet/api/system.func-2
[CsIList]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.ilist-1
[CsIReadOnlyList]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.ireadonlylist-1
[CsListed]: ../language/types.md#interface-listed
[CsMsLogging]: https://learn.microsoft.com/en-us/dotnet/api/microsoft.extensions.logging
[CsMsLoggingDi]: https://learn.microsoft.com/en-us/aspnet/core/fundamentals/logging/#create-logs
[CsMsTest]: https://learn.microsoft.com/en-us/dotnet/core/testing/unit-testing-with-mstest
[CsNamespace]: https://learn.microsoft.com/en-us/dotnet/csharp/fundamentals/types/namespaces
[CsNamespaceSelection]: https://learn.microsoft.com/en-us/dotnet/standard/design-guidelines/names-of-namespaces
[CsNetList]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.list-1
[CsNetProject]: https://learn.microsoft.com/en-us/dotnet/core/tutorials/library-with-visual-studio-code
[CsOrNull]: ../language/types.md#type-relationships
[CsPackageId]: https://learn.microsoft.com/en-us/nuget/nuget-org/id-prefix-reservation
[CsTestBlocks]: ../guide/tutorial/04-modules-and-libraries.md#unit-tests
[CsTrace]: https://learn.microsoft.com/en-us/dotnet/api/system.diagnostics.trace

## Java (`java`)

Translates Temper to Java 17 source, and a Maven project
([Java 17]). A module's top level goes in a `…Global` class, with a
`…Main` class to run it, and each type gets its own file. Classes also
get a `Builder`, since Java has no named arguments. A Temper failure
throws `temper.core.Bubble`, an unchecked `RuntimeException`, and an
`orelse` catches only that.

Still to write up: how function types translate to single-method
interfaces, how nullable types translate, primitive and boxed types,
overloads for optional arguments, picking a Java package and Maven
identifiers (`javaName`, `javaPackage`), and the generated POM.

[Java 17]: https://docs.oracle.com/javase/specs/jls/se17/html/index.html

## JavaScript (`js`)

Translates Temper to JavaScript ES modules, with types in JSDoc comments
for [TypeScript][TS-compat], checked with `tsc --strict`.

### Translation notes

Temper `interface` types translate to names that work with JavaScript's
`instanceof` operator. Your JavaScript code may use
[*InterfaceType.implementedBy*][temperlang-core-code] to make classes
that implement Temper interfaces.

`temper.out/js` has a [source map] for each generated file, so a
JavaScript debugger shows the Temper code it came from.

A Temper failure throws `@temperlang/core`'s `Bubble`, an `Error`, and
an `orelse` catches only that.

### Tooling notes

Tests translate to tests for Node's own test runner, [`node:test`][node-test],
which needs no package and gives each test as long as it takes, as the
other backends' runners do. Each module's tests are a file named for
it, such as `test/hello_world.test.js`, and a
[`package.json`][package.json] makes `npm test` run them
(`node --enable-source-maps --test "test/**/*.test.js"`, which needs
Node 21 or later to expand the glob). The tests import only `it` from
`node:test`, with no `describe`, hooks, mocks, or timers, so Bun and
Deno run them too, as they run the libraries themselves, and find them
by their `.test.js` names. In the package's directory:

| Runtime | Command |
| --- | --- |
| Node | `npm test` |
| Bun | `bun test --timeout 0` (by default, Bun fails a test that runs over 5 seconds) |
| Deno | `deno test -A --no-check` (Deno's type check wants `@types/node` installed) |

Running tests needs Node 22 or later, the oldest Node line still
supported; CI tests on Node 24. `temper test -b js` runs every tests
file in one process, with `--test-isolation=none`, which Node 22 spells
`--experimental-test-isolation=none` and has from 22.8, so on the 22
line it needs 22.8 or later, and says so on older Nodes.

Tests files have source maps too, and `temper test -b js`,
`temper run -b js`, and `npm test` run Node with
[`--enable-source-maps`][node-source-maps], so a failed test's stack
trace (in `test-results.xml`) or an uncaught error's names Temper
files and lines. Deno reads the maps by itself; Bun doesn't read them.
The maps name the sources by relative paths from `temper.out`, without
their text, so they resolve only where the sources are, not in a
published package.

> **Note:** Temper's older docs say the JavaScript backend targets
> ES2018, and its getting started guide says Node 14 or later, while a
> 0.x release note requires Node 18. Which Node and JavaScript versions
> the generated libraries themselves support is still open
> ([inconsistencies](../inconsistencies.md#i5-node-and-javascript-versions)).

[TS-compat]: https://www.typescriptlang.org/docs/handbook/jsdoc-supported-types.html#types-1
[temperlang-core-code]: https://www.npmjs.com/package/@temperlang/core?activeTab=code
[source map]: https://web.dev/source-maps/
[node-test]: https://nodejs.org/api/test.html
[node-source-maps]: https://nodejs.org/api/cli.html#--enable-source-maps
[package.json]: https://docs.npmjs.com/cli/v9/configuring-npm/package-json

## Lua (`lua`)

Translates Temper to Lua: an `init.lua` per library, a rockspec, and
LuaCATS type annotations, checked with LuaLS. Generated code is
declared for Lua 5.1 and later; it's tested on Lua 5.4 and LuaJIT.
A Temper failure is an `error`, which `pcall` catches.

> **Note:** a few features need Lua 5.3 or later, such as some string
> and integer operations; Lua 5.1 itself isn't tested
> ([inconsistencies](../inconsistencies.md#i13-lua-versions)).

## Python (`py`)

Translates Temper to Python 3 with type annotations, checked with mypy
and pyright in strict mode. Targets [Python 3.11][python-3.11.0] and
later; for best support, use CPython.

### Translation notes

Temper's [*Void*](../language/types.md#class-void) type translates to
returning Python's `None`.

Temper default expressions are evaluated as needed, so they don't
suffer the [singly evaluated default expression pitfall][default-pitfall].

A Temper failure raises `temper_core.Bubble`, a `RuntimeError`, and an
`orelse` catches only that, so other exceptions pass it. A panic raises
a plain `BaseException`, which `except Exception` doesn't catch.

Names translate to Python's conventions: `snake_case` for functions,
methods, and properties, and a leading `_` for what isn't exported.
A name that's a Python keyword gets a trailing `_` (`from_`). A
parameter or local keeps its name even when a builtin has it, so a
constructor's parameters, which Python code may pass by name, are
`Option(help=…, type=…)`. Where a function's own names hide a builtin
its code needs, it reaches the builtin through the `builtins` module
(`_builtins.len(items)`). A module-level name that a builtin has gets
a trailing `_` (`max_`), as does a member named for a builtin that a
class body uses (`int_`, `property_`); other members keep theirs
(`map`).

On Windows, before Python 3.15, logging characters outside the
system's code page (as `✅`) to a pipe or file needs Python's UTF-8
mode (`PYTHONUTF8=1`), which `temper run` and `temper test` set.

[python-3.11.0]: https://docs.python.org/release/3.11.0/
[default-pitfall]: https://towardsdatascience.com/python-pitfall-mutable-default-arguments-9385e8265422

## Rust (`rust`)

Translates Temper to a Rust crate (edition 2021, `rust-version`
1.71.1). Classes are reference-counted handles (`Arc`), strings are
`Arc<String>`, failure is a `Result`, and module state is safe across
threads. Generated code passes clippy.

