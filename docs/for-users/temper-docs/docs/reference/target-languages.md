---
title: Target Languages
---

# Target Languages

`temper build` translates to many *target* programming languages via
*Backend*s ([*Backend.kt*](https://github.com/temperlang/temper/blob/main/be/src/commonMain/kotlin/lang/temper/be/Backend.kt)) that each embed knowledge
about one target language.  Each backend is responsible for:

- converting Temper syntax trees to *target language* syntax trees, and
- creating source files and source maps under the `temper.out` directory, and
- connecting to language specific tools to

    - post-process the source files, and
    - run the translated tests and collect results, and
    - bundle them into publishable archives.

The *Backend IDs* noted below may be used with the `-b` flag
(aka `--backend`) to the `temper` command line tool to
build, run, or test using specific backends.
See `temper help` for more information on `-b`.

## Target Language List

<!-- snippet: backends/supported -->

<a name="backends&#45;supported" class="snippet-anchor-name"></a>

<!-- snippet: backend/csharp -->

<a name="backend&#45;csharp" class="snippet-anchor-name"></a>

### C# Backend

<!-- snippet: backend/csharp/id -->

<a name="backend&#45;csharp&#45;id" class="snippet-anchor-name"></a>

BackendID: `csharp`

<!-- /snippet: backend/csharp/id -->

Translates Temper to C# source.

Targets [.NET][CsDotNet] 6.0, although [.NET Framework][CsDotNetFramework] 4.8
might be supported in the future.

To get started with this backend, see [the tutorial](../tutorial/index.md#use-csharp).

#### Translation notes

[Temper function types][CsFn], such as `fn (Int): Int` are translated into either
[`Func` delegates][CsFunc] for most return types or [`Action` delegates][CsAction] for `Void`
return type. These have an upper limit of 16 parameters, which isn't
accounted for in the current translation. In the future, if demand arises,
Temper likely will generate new delegate types in generated libraries for
larger numbers of parameters.

[Temper nullable types][CsOrNull], such as `A?` translate into either nullable
value types or nullable reference types in C#. Nullable reference types apply
only to newer versions of C#, so potential .NET Framework 4.8 support would
need to represent these as ordinary reference types.

For clearer representation of intention, Temper collection types translate
into .NET generic interfaces, including "ReadOnly" variations as appropriate.
For example, Temper [`Listed<T>`][CsListed] and `List<T>` both translate to
[`IReadOnlyList<T>`][CsIReadOnlyList], whereas `ListBuilder<T>` translates to [`IList<T>`][CsIList].
Similar rules apply to Temper `Mapped` types and .NET [`Dictionary`][CsDictionary] types,
with the additional matter that maps created in Temper always maintain
insertion order. Use of these interfaces in .NET is complicated by the fact
that `Ilist` doesn't subtype `IReadOnlyList`, even though standard .NET
[`List`][CsNetList] subtypes both. Similar issues apply to `Dictionary` types. To improve
ergonomics, usage of Temper `Listed` generate overloads that accept any of
`List`, `IList`, or `IReadOnlyList`. Again, Temper generates similar
overloads for Temper `Mapped` and .NET `Dictionary` types.

Each Temper library is translated into a .NET [project][CsNetProject]/[assembly][CsAssembly], and Temper
module is translated into a [.NET namespace][CsNamespace]. Files for each subnamespace are
produced in respective output subdirectories. Each type defined in Temper is
translated to C# in separate file. Top-level Temper modules contents are
produced in a "Global" C# static class for the namespace.

The Temper [logging `console`][CsConsole] translates in C# to the
[`Microsoft.Extensions.Logging` framework][CsMsLogging], at least for modern .NET usage.
This framework typically [initializes loggers via dependency injection (DI)][CsMsLoggingDi],
which isn't supported for static classes and static initialization. Because
of this, and to simplify general libraries that aren't built on DI, Temper
instead generates static methods for initializing an `ILogger` instance for
the output library. In the absence of any such configuration, generated
libraries fall back to using [`System.Diagnostics.Trace`][CsTrace].

#### Tooling notes

A [csproj file][CsCsProj] is automatically created for each Temper library such that
each builds to a separate .NET assembly.

The only .NET library naming configuration available today for
[`config.temper.md` is `csharpRootNamespace`][CsConfig]. If specified, this string is
configured as the root namespace for the output project as well as the name
of the .NET assembly and the presumed NuGet [package ID][CsPackageId]. More fine-grained
configuration might be provided in the future. Microsoft has some advice for
[namespace selection][CsNamespaceSelection].

Temper [`test` blocks][CsTestBlocks] are translated to use the [MSTest framework][CsMsTest]. A test class
is generated for each Temper module, and each test block becomes a test
method. Temper automatically provides infrastructure for soft assertions
within tests.

[CsAction]: https://learn.microsoft.com/en-us/dotnet/api/system.action-1
[CsAssembly]: https://learn.microsoft.com/en-us/dotnet/standard/assembly/
[CsConfig]: ../tutorial/04-modlib.md#library-configuration
[CsConsole]: builtins.md#console
[CsCsProj]: https://learn.microsoft.com/en-us/aspnet/web-forms/overview/deployment/web-deployment-in-the-enterprise/understanding-the-project-file
[CsDictionary]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.dictionary-2
[CsDotNet]: https://dotnet.microsoft.com/en-us/platform/support/policy/dotnet-core
[CsDotNetFramework]: https://dotnet.microsoft.com/en-us/download/dotnet-framework
[CsFn]: types.md#function-types
[CsFunc]: https://learn.microsoft.com/en-us/dotnet/api/system.func-2
[CsIList]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.ilist-1
[CsIReadOnlyList]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.ireadonlylist-1
[CsListed]: types.md#interface-listed
[CsMsLogging]: https://learn.microsoft.com/en-us/dotnet/api/microsoft.extensions.logging
[CsMsLoggingDi]: https://learn.microsoft.com/en-us/aspnet/core/fundamentals/logging/#create-logs
[CsMsTest]: https://learn.microsoft.com/en-us/dotnet/core/testing/unit-testing-with-mstest
[CsNamespace]: https://learn.microsoft.com/en-us/dotnet/csharp/fundamentals/types/namespaces
[CsNamespaceSelection]: https://learn.microsoft.com/en-us/dotnet/standard/design-guidelines/names-of-namespaces
[CsNetList]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.list-1
[CsNetProject]: https://learn.microsoft.com/en-us/dotnet/core/tutorials/library-with-visual-studio-code
[CsOrNull]: types.md#type-relationships
[CsPackageId]: https://learn.microsoft.com/en-us/nuget/nuget-org/id-prefix-reservation
[CsTestBlocks]: ../tutorial/04-modlib.md#unit-tests
[CsTrace]: https://learn.microsoft.com/en-us/dotnet/api/system.diagnostics.trace

<!-- /snippet: backend/csharp -->

<!-- snippet: backend/java -->

<a name="backend&#45;java" class="snippet-anchor-name"></a>

### Java Backend

<!-- snippet: backend/java/id -->

<a name="backend&#45;java&#45;id" class="snippet-anchor-name"></a>

BackendID: `java`

<!-- /snippet: backend/java/id -->

Translates Temper to Java source and later to JARs.

Targets [Java 17].

To get started with this backend, see [the tutorial](../tutorial/index.md#use-java).

There is also a [Java 8](#backend-java8) backend that
produces similar output but which does not depend on newer
Java runtime library features.  Except where specified in
the Java8 backend documentation, the notes here also apply
to that backend.

#### Translation notes

TODO: Explain how Temper function types translate to Java
SMI types.

TODO: Explain how `null`able types are translated

TODO: Explain how Temper types translate to primitive or boxed
types.

TODO: Explain overload generation for optional arguments.

TODO: Explain how Temper source files are split into Java files.

TODO: Explain how `console.log` connects.

#### Tooling notes

TODO: Explain how to pick a Java package for a library and
Maven identifiers.

TODO: Explain how Temper tests correspond to JUnit and the
version of JUnit we require

TODO: Explain the generated POM and JAR metadata.

TODO: Generating multi-version JARs is something we plan to do.

[Java 17]: https://docs.oracle.com/javase/specs/jls/se17/html/index.html

<!-- /snippet: backend/java -->

<!-- snippet: backend/java8 -->

<a name="backend&#45;java8" class="snippet-anchor-name"></a>

### Java 8 Backend

<!-- snippet: backend/java8/id -->

<a name="backend&#45;java8&#45;id" class="snippet-anchor-name"></a>

BackendID: `java8`

<!-- /snippet: backend/java8/id -->

Translates Temper to Java 8 source, and later to JARs
for compatibility with older Java Virtual Machines (JVMs).

Except where noted here, the documentation for the main
[Java](#backend-java) backend also applies to
this legacy, Java 8 backend.

Targets [Java 8].

[Java 8]: https://docs.oracle.com/javase/specs/jls/se8/html/index.html

<!-- /snippet: backend/java8 -->

<!-- snippet: backend/js -->

<a name="backend&#45;js" class="snippet-anchor-name"></a>

### JavaScript Backend

<!-- snippet: backend/js/id -->

<a name="backend&#45;js&#45;id" class="snippet-anchor-name"></a>

Backend ID: `js`

<!-- /snippet: backend/js/id -->

Translates Temper to JavaScript with types in documentation comments for
[compatibility with TypeScript][TS-compat].

Targets [ES2018 / ECMA-262, 9<sup>th</sup> edition][ES2018].

To get started with this backend, see [the tutorial](../tutorial/index.md#use-js).

#### Translation notes

Temper [`interface` type declarations] are translated to names in JavaScript
that work with JavaScript's `instanceof` operator.
Your JavaScript code may use [*InterfaceType.implementedBy*][temperlang-core-code]
to create JavaScript `class`es that implement Temper interfaces.

The `temper.out/js` output directory will contain a [source map] for each
generated JavaScript source file so that, in a JS debugger, you can see the
corresponding Temper code.

#### Tooling notes

Temper's JavaScript backend translates tests to [Mocha] tests and generates a
[*package.json* file][package.json] so that running `npm test` from the command line will run
the translated tests for a Temper built JavaScript library.

[ES2018]: https://www.ecma-international.org/publications-and-standards/standards/ecma-262/
[TS-compat]: https://www.typescriptlang.org/docs/handbook/jsdoc-supported-types.html#types-1
[temperlang-core-code]: https://www.npmjs.com/package/@temperlang/core?activeTab=code
[source map]: https://web.dev/source-maps/
[Mocha]: https://mochajs.org/
[package.json]: https://docs.npmjs.com/cli/v9/configuring-npm/package-json

<!-- /snippet: backend/js -->

<!-- snippet: backend/lua -->

<a name="backend&#45;lua" class="snippet-anchor-name"></a>

### Lua Backend

<!-- snippet: backend/lua/id -->

<a name="backend&#45;lua&#45;id" class="snippet-anchor-name"></a>

BackendID: `lua`

<!-- /snippet: backend/lua/id -->

Supported version: Lua 5.1

<!-- /snippet: backend/lua -->

<!-- snippet: backend/mypyc -->

<a name="backend&#45;mypyc" class="snippet-anchor-name"></a>

### Mypyc Backend

A variant of the [Python backend](#backend-py) which invokes mypyc to generate type-optimized
versions of modules.

<!-- snippet: backend/mypyc/id -->

<a name="backend&#45;mypyc&#45;id" class="snippet-anchor-name"></a>

Backend ID: `mypyc`

<!-- /snippet: backend/mypyc/id -->

<!-- /snippet: backend/mypyc -->

<!-- snippet: backend/py -->

<a name="backend&#45;py" class="snippet-anchor-name"></a>

### Python Backend

<!-- snippet: backend/py/id -->

<a name="backend&#45;py&#45;id" class="snippet-anchor-name"></a>

Backend ID: `py`

<!-- /snippet: backend/py/id -->

Translates Temper to Python3 with types and builtin *[mypy][mypy-lang]*
integration which helps numerics heavy code perform much better
than Python without type optimizations applied.

Targets [Python 3.11][python-3.11.0].  For best support, use CPython.

To get started with this backend, see [the tutorial](../tutorial/index.md#use-py).

#### Translation notes

Temper's [*Void*](types.md#type-Void) type translates to a return value of
Python *None*.

Temper default expressions are evaluated as needed, so do not
suffer the [singly evaluated default expression pitfall][default-pitfall].

Named arguments are translated to named arguments in Python, but
TODO: we need to remove numeric suffixes.

[python-3.11.0]: https://docs.python.org/release/3.11.0/
[mypy-lang]: https://mypy-lang.org/
[default-pitfall]: https://towardsdatascience.com/python-pitfall-mutable-default-arguments-9385e8265422

<!-- /snippet: backend/py -->

<!-- snippet: backend/rust -->

<a name="backend&#45;rust" class="snippet-anchor-name"></a>

### Rust Backend

<!-- snippet: backend/rust/id -->

<a name="backend&#45;rust&#45;id" class="snippet-anchor-name"></a>

BackendID: `rust`

<!-- /snippet: backend/rust/id -->

Translates Temper to Rust source and later to cargo crates.

Targets the [Rust Programming Language].

[Rust Programming Language]: https://doc.rust-lang.org/book/

<!-- /snippet: backend/rust -->

<!-- /snippet: backends/supported -->
