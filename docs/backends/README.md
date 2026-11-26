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
| [JavaScript](#javascript-js) | `js` | ES modules, with JSDoc types for TypeScript | a `package.json` | Mocha |
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
[release](https://github.com/temperlang/temper/releases) carries
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

## Configuration

Each backend has a config class for its own settings, which a
library's `config.temper.md` sets in an object of that class, exported
under the backend's ID, the object taking its class from the declared
type, as any object literal does:

```temper
export let java: JavaConfig = {
  package: "com.example.geometry",
  dependencies: ["org.roaringbitmap:RoaringBitmap:1.6.20"],
};
```

`{ class: JavaConfig, … }` or `new JavaConfig(…)` names the class too.
Each setting is unset by default. A config is ordinary Temper, which
`temper` type-checks and runs, so values may be computed; a setting the
class doesn't have or a value of another type is an ordinary error,
and a class set twice, or under another backend's name, is a config
error. A config can't import anything yet, and it runs with a small
budget of steps, where each byte it makes takes a step too, so a
runaway config fails quickly; what's left when the budget runs out
doesn't run, which is an error too. A build of `temper` without some
backend leaves out that backend's config, unchecked.

Each backend checks the forms of its own settings. Names are spelled
as each language's tools allow, ASCII only: C#
namespaces and Java packages are identifiers joined by `.`; Maven IDs
and NuGet IDs are letters, digits, `.`, `_`, and `-`; npm names are
those and `~`, maybe under an `@scope/`; PyPI names are letters,
digits, `.`, `_`, and `-`, ending in a letter or digit; and crate names
are letters, digits, `_`, and `-`, starting with a letter. Each starts
with a letter or digit and has no `..`, so a name never leaves its
directory or breaks out of a package file.

`dependencies` lists packages that the library's host code (its
`@connected` classes and functions) uses, in the language's own
registry's spelling, names as above and versions plain: an exact
version, or for npm, NuGet, and PyPI a simple wildcard or range
(`^1.2.0`, `~1.2`, `1.*`), but no URLs, paths, ranges with spaces or
brackets, extras, or tool options. They go in the package files, and
`temper test` and `temper run` fetch them with the language's tools:
pip and npm into `temper.out/py/.deps` and `temper.out/js/.deps`, and
Cargo, Maven, and NuGet into their usual caches in your home directory.
They come from the public registries (PyPI, npm, crates.io, Maven
Central, and NuGet) with the tools' defaults, so pip and npm may run a
package's install scripts. A package listed twice, or one that a
library of the build, std, or `temper-core` is, is a config error, as
are two libraries whose packages share a name, ignoring case (a Python
project, an npm package, a crate, a Java package or Maven artifact, or
a C# namespace).
[Library configuration](../guide/tutorial/04-modules-and-libraries.md#library-configuration)
has a whole config file.

<!-- Generated from each backend's config class by crates/temper-driver/tests/config_classes.rs. -->
| Class | Export | Property | Type | Sets |
|---|---|---|---|---|
| `CppConfig` | `cpp` | | | Nothing yet |
| `CSharpConfig` | `csharp` | `rootNamespace` | `String` | The root namespace, which also names the project, the assembly, and the NuGet package ID; else the library's name in Pascal case. |
|  |  | `dependencies` | `List<String>` | NuGet packages that the library's host code uses, each `"Name:version"`. |
| `JavaConfig` | `java` | `name` | `String` | The library's Java name, which the package and the Maven artifact ID default to; else the library's name. |
|  |  | `package` | `String` | The Java package, as `com.example.geometry`; else the Java name, as a package. |
|  |  | `group` | `String` | The Maven group ID; else the package. |
|  |  | `artifact` | `String` | The Maven artifact ID; else the Java name. |
|  |  | `dependencies` | `List<String>` | Maven packages that the library's host code uses, each `"group:artifact:version"`. |
| `JsConfig` | `js` | `name` | `String` | The npm package's name, which code that uses the library imports, as `@example/geometry`; else the library's name. |
|  |  | `dependencies` | `List<String>` | npm packages that the library's host code uses, each `"name@version"`, as `"@scope/name@^1.0.0"`. |
| `LuaConfig` | `lua` | | | Nothing yet |
| `PyConfig` | `py` | `name` | `String` | The Python project's name, which, as a Python name, also names its package; else the library's name. |
|  |  | `dependencies` | `List<String>` | PyPI packages that the library's host code uses, each `"name==version"`. |
| `RustConfig` | `rust` | `name` | `String` | The crate's name, which code that uses the library names with `_` for `-`; else the library's name. |
|  |  | `dependencies` | `List<String>` | Crates that the library's host code uses, each `"name@version"`, for exactly that version. |
<!-- End of generated table. -->

## C++ (`cpp`)

Translates Temper to C++14: a header and a source file per module
(`LIBRARY/init.hpp` and `init.cpp`), a `main.cpp` that runs the
library, and a CMake project. Objects are `std::shared_ptr`s, and a
library's namespace is its name. A class's static properties are read
through accessor functions (`Rect::unit()`), read-only.

Generated code compiles without warnings under `-Wall -Wextra
-Wpedantic -Werror`, and is tested under AddressSanitizer, UBSan, and
TSan. C++ leaves the order of evaluation of arguments open, so the
backend keeps Temper's left-to-right order wherever it would show.
C++20 code can `co_await` a Temper promise.

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

The only .NET setting in [`config.temper.md`][CsConfig] today is
`CSharpConfig`'s `rootNamespace` ([Configuration](#configuration)). It
sets the project's root namespace, the assembly's name, and the
presumed NuGet [package ID][CsPackageId].
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
get a `Builder`, since Java has no named arguments. A class's public
static properties are read through static getters
(`Policy.getStandard()`, `isX()` for a `Boolean`), never fields, so host
code can't reassign them.

Still to write up: how function types translate to single-method
interfaces, how nullable types translate, primitive and boxed types,
overloads for optional arguments, picking a Java package and Maven
identifiers (`JavaConfig`, [Configuration](#configuration)), and the
generated POM.

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

### Tooling notes

Tests translate to [Mocha] tests, and a [`package.json`][package.json]
makes `npm test` run them.

> **Note:** Temper's older docs say the JavaScript backend targets
> ES2018, and its getting started guide says Node 14 or later, while a
> 0.x release note requires Node 18
> ([inconsistencies](../inconsistencies.md#i5-node-and-javascript-versions)).

[TS-compat]: https://www.typescriptlang.org/docs/handbook/jsdoc-supported-types.html#types-1
[temperlang-core-code]: https://www.npmjs.com/package/@temperlang/core?activeTab=code
[source map]: https://web.dev/source-maps/
[Mocha]: https://mochajs.org/
[package.json]: https://docs.npmjs.com/cli/v9/configuring-npm/package-json

## Lua (`lua`)

Translates Temper to Lua: an `init.lua` per library, a rockspec, and
LuaCATS type annotations, checked with LuaLS. Generated code runs on
Lua 5.1 and later, and on LuaJIT; it's tested on Lua 5.1, 5.4, and
LuaJIT 2.1.

> **Note:** a few features need Lua 5.3 or later: std `regex` (Lua's
> `utf8`), and on 5.1 and LuaJIT, `String.toInt32()` takes `"2.0"`, and
> `Float64.max` and `min` miss a NaN first argument
> ([inconsistencies](../inconsistencies.md#i13-lua-versions)). On 5.1
> and LuaJIT, std `net`'s LuaSocket client sends one request at a time,
> each blocking until it's done, as LuaSocket can't yield there.

## Python (`py`)

Translates Temper to Python 3 with type annotations, checked with mypy
and pyright in strict mode. Targets [Python 3.11][python-3.11.0] and
later; for best support, use CPython.

### Translation notes

Temper's [*Void*](../language/types.md#class-void) type translates to
returning Python's `None`.

Temper default expressions are evaluated as needed, so they don't
suffer the [singly evaluated default expression pitfall][default-pitfall].

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

