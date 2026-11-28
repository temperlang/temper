//! Temper's C# backend: tmpl in, C# out.
//!
//! Works from tmpl alone (notes/tmpl/overview.md, "Boundaries"). Its
//! variants share the translation and differ only in data
//! (notes/tmpl/profiles.md, "Variants and the first profiles"); the first,
//! [`CSHARP`], is Kotlin-compatible: the same project layout (a `src`
//! project, `ROOT.csproj`, for the library, with its root module in the
//! root namespace's `ROOTGlobal` static class and `Logging.cs` in
//! `ROOT.Support`, and a `program` project to run it), `TemperLang.Core`
//! runtime, public names (Pascal case for functions and exported values),
//! and .NET 6, as Kotlin Temper's `csharp` backend, with prettier bodies:
//! no numbered names, the source's names for parameters and locals,
//! `using`s rather than one-letter namespace aliases, a file-scoped
//! namespace, and blank lines between members. Unlike Kotlin's, it never
//! leaves string or number formatting to the current culture (see
//! [`connected`]), and compiles without warnings with nullable reference
//! types on and every .NET analyzer an error but those its tests list.
//!
//! Parameter names are part of a C# API, since callers may pass arguments
//! by name, so they're exactly the source's (a keyword as `@name`), never
//! renamed for looks; only a clash with a module-level name a function
//! uses would number one. Temper means them to be positional but for
//! constructors' and factories' (notes/tmpl/surface.md, "Parameter names
//! where languages have named arguments"), which a Roslyn analyzer in the
//! generated NuGet package is to say; it isn't built yet.
//!
//! The pipeline: tmpl's lowerings, the program partitioned by reach
//! ([`temper_tmpl::reach::partition`]: what only tests reach in the tests'
//! project, what nothing does left out), the naming pass
//! ([`temper_tmpl::naming::assign`]) with C#'s rules ([`names::RULES`]),
//! then [`translate`] into C#'s output
//! tree ([`tree`]), with operators spelled by [`ops`] and core's connected
//! declarations by [`connected`], then the printer.
//!
//! It covers libraries of several modules, classes and interfaces (each in a file of its own, as
//! Kotlin's: see [`translate`]), and nullable types (`T?`, `x.Value` or
//! C#'s own narrowing), `orelse` and `catch` (a `try` and a `catch` of
//! the error class above what the body may throw), and `panic()` (and
//! code with an error) as the runtime's `Panic`, which, unlike Kotlin's,
//! no handler catches. Unlike Kotlin's catch-all, a handler catches only
//! declared errors, which builtins that may fail throw, the runtime
//! turning host errors (`FormatException`, `KeyNotFoundException`, a
//! broken promise's exception) into core's; so anything else, a host
//! exception from a builtin not declared to fail or from a callback,
//! passes every handler; declared errors (RFC 164) as exceptions, each
//! library's error types under its roots, `WherewithException :
//! Exception, IFailure`, `throw` as C#'s, the cause as `InnerException`,
//! and handlers that catch exactly what their bodies may fail with
//! (`translate`'s `declared_errors` module says how); and generics
//! (`First<T>`, `class Shelf<T> where T : Named`, and explicit type
//! arguments on `new`), with core's lists as `IReadOnlyList` and `IList`,
//! maps as `IReadOnlyDictionary` and `IDictionary` (the runtime's
//! `OrderedDictionary`), and pairs as `KeyValuePair`s, as Kotlin's;
//! anything else is [`NotYet`]. No source maps yet. A library's own
//! connected functions and classes take its host code, its `.cs` files,
//! as Kotlin's do (`translate`'s `host` module).

pub mod connected;
pub mod names;
pub mod ops;
pub mod runtime;
// Not in wasm, which runs no tools.
#[cfg(any(unix, windows))]
pub mod tools;
pub mod translate;
pub mod tree;

use std::fmt;
use temper_tmpl::coroutines::CoroutineStrategy;
use temper_tmpl::naming::{NamingRules, assign_checked};
use temper_tmpl::{Features, Profile, Program};

/// A named set of translation choices for C#.
pub struct Variant {
    /// Its name on the command line: `csharp`.
    pub name: &'static str,
    /// What it handles natively, which lowering and validation read.
    pub profile: Profile,
    pub rules: NamingRules,
    /// The .NET target framework, `<TargetFramework>` in each `.csproj`.
    pub target_framework: &'static str,
}

/// The first variant: Kotlin-compatible API and names, pretty bodies,
/// .NET 6 (C# 10). Everything is native except statements inside
/// expressions, which the hoisting pass will lower (not built yet: a
/// program that needs it is [`NotYet`]).
pub static CSHARP: Variant = Variant {
    name: "csharp",
    profile: Profile {
        native: Features::NONE,
        coroutines: CoroutineStrategy::TranslateToGenerator,
    },
    rules: names::RULES,
    target_framework: "net6.0",
};

/// Every variant, by name.
pub const VARIANTS: &[&Variant] = &[&CSHARP];

/// Something the translation doesn't cover yet.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NotYet {
    pub what: String,
}

impl fmt::Display for NotYet {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "not yet in the C# backend: {}", self.what)
    }
}

impl std::error::Error for NotYet {}

pub use temper_tmpl::OutFile;

/// This backend, as tools list it ([`temper_tmpl::Backend`]).
pub const BACKEND: temper_tmpl::Backend = temper_tmpl::Backend {
    name: "csharp",
    comment: "//",
    runtime: if cfg!(target_family = "wasm") {
        &[]
    } else {
        runtime::FILES
    },
    runtime_version: &runtime::VERSION,
    rules: &CSHARP.rules,
    translate: |p, links| {
        translate_with_lines(p, &CSHARP, links.line_prefix()).map_err(|e| e.to_string())
    },
    default: true,
    #[cfg(any(unix, windows))]
    tools: Some(&tools::TOOLS),
    #[cfg(not(any(unix, windows)))]
    tools: None,
};

/// Translates a library, laid out as Kotlin's C# backend lays it out:
///
/// ```text
/// hello-world/src/HelloWorld.csproj               the library's project, on TemperLang.Core
/// hello-world/src/HelloWorldGlobal.cs             its root module
/// hello-world/src/Logging.cs                      its logging setup, in HelloWorld.Support
/// hello-world/program/HelloWorldProgram.csproj    a program to run it
/// hello-world/program/Program.cs                  its entry point
/// hello-world/src/Helpers/HelpersGlobal.cs        another module, `helpers`
/// hello-world/tests/HelloWorldTest.csproj         the tests' project, on MSTest
/// hello-world/tests/HelloWorldTests.cs            the root module's tests
/// hello-world/tests/Helpers/HelpersTests.cs       another module's
/// hello-world/tests/TestShape.cs                  what only tests reach
/// hello-world/src/HelloWorldConnected.cs          host code, copied
/// ```
///
/// A module's test class has what only its tests reach, as statics, and
/// the classes and interfaces only they reach are in the tests' project
/// too, which sees the library's `internal` members; a library only the
/// tests use is only the tests' project's reference.
///
/// The root namespace takes the library's name in Pascal case
/// ([`names::root_namespace`]), and a module's namespace its path's
/// parts after it. Kotlin also copies `TemperLang.Core` beside the
/// library, as `temper-core/`, which the projects refer to; `tempest
/// build` does too ([`runtime`]).
/// std's modules with support code, as Kotlin's C# backend writes it
/// into std's output: each module's path, and its support files
/// (begun as Kotlin's, in `runtime/std/`) beside its own, whose static methods its
/// connected members call (`RegexSupport.CompiledFind(this, …)`).
pub const STD_SUPPORT: &[(&str, &str, &str)] = &[
    (
        "regex",
        "IntRangeSet.cs",
        include_str!("../runtime/std/Regex/IntRangeSet.cs"),
    ),
    (
        "regex",
        "RegexSupport.cs",
        include_str!("../runtime/std/Regex/RegexSupport.cs"),
    ),
    (
        "net",
        "NetSupport.cs",
        include_str!("../runtime/std/Net/NetSupport.cs"),
    ),
    (
        "temporal",
        "TemporalSupport.cs",
        include_str!("../runtime/std/Temporal/TemporalSupport.cs"),
    ),
];

pub fn translate(p: &Program, variant: &Variant) -> Result<Vec<OutFile>, NotYet> {
    translate_with_lines(p, variant, None)
}

/// [`translate`], and with `lines`, a prefix that leads from the
/// library's directory to its root, `#line` directives in its classes
/// (`temper run` and `temper test` only), so compiler errors, debuggers,
/// and stack traces (a debug build's, with its pdb) point at the Temper
/// source ([`temper_tmpl::line_directives`]): before each statement that
/// starts a new line in the source, naming it by its path relative to
/// the `.cs` file, as C# resolves it, and `#line default` before a
/// member without one, so errors in generated support code point at the
/// `.cs`.
pub fn translate_with_lines(
    p: &Program,
    variant: &Variant,
    lines: Option<&str>,
) -> Result<Vec<OutFile>, NotYet> {
    // Type-only errors run as written, or fail where they are
    // (`temper_tmpl::errors`), by the profile.
    let settled = temper_tmpl::errors::settle(p, &variant.profile);
    let p = settled.as_ref().unwrap_or(p);
    // Error parameters as their bounds in code: exceptions are
    // unchecked (`temper_tmpl::declared_errors::erase_params`).
    let erased = temper_tmpl::declared_errors::erase_params(p).map_err(|what| NotYet { what })?;
    let p = erased.as_ref().unwrap_or(p);
    // `Never<T>` as `T`, but where the profile keeps it
    // (`temper_tmpl::nevers`).
    let nevered = temper_tmpl::nevers::lower(p, &variant.profile);
    let p = nevered.as_ref().unwrap_or(p);
    // Handlers whose bodies can't fail as their bodies alone
    // (`temper_tmpl::declared_errors`).
    let alive = temper_tmpl::declared_errors::drop_dead_handlers(p);
    let p = alive.as_ref().unwrap_or(p);
    // `Switch` as `if`s (`temper_tmpl::switches`); none comes here yet.
    let chained = (!variant.profile.native.contains(Features::SWITCH))
        .then(|| temper_tmpl::switches::to_if_chains(p))
        .flatten();
    let p = chained.as_ref().unwrap_or(p);
    // Operands that run before an `orelse` or a block's value, which C#
    // writes as statements before, held first, and choices with a
    // `panic()`, which is statements too, as statements
    // (`temper_tmpl::spill`).
    let panics = |n| matches!(p.nodes.get(n), temper_tmpl::Node::Panic {});
    let spilled = temper_tmpl::spill::spill_with(p, &panics);
    let p = spilled.as_ref().unwrap_or(p);
    // What only tests reach apart, in the tests' project, and what nothing
    // reaches left out (`temper_tmpl::reach`), as the lowerings left it.
    let parted = temper_tmpl::reach::partition(p, connected::connects_std);
    let p = &parted;
    let names = assign_checked(p, &variant.rules).map_err(|what| NotYet { what })?;
    let library = &p.library.name;
    let root = translate::library_namespace(p, library);
    let mut sources = Vec::new();
    let mut tests = Vec::new();
    let mut program_loads = Vec::new();
    for module in &p.modules {
        let (namespace, base) = translate::module_names(p, module.decl);
        let global = format!("{base}Global");
        let mut translator = translate::Translator::new(p, &names, &namespace, &global);
        let dir = (namespace.strip_prefix(&root).unwrap_or_default().split('.'))
            .filter(|part| !part.is_empty())
            .map(|part| format!("{part}/"))
            .collect::<Vec<_>>()
            .concat();
        // From `src/DIR` or `tests/DIR` up to the library's directory.
        // tmpl's files are named within their module's directory.
        translator.lines = lines.map(|prefix| {
            let up = "../".repeat(1 + dir.matches('/').count());
            match module.path.as_str() {
                "" => format!("{up}{prefix}"),
                path => format!("{up}{prefix}{path}/"),
            }
        });
        let class = translator.module(module)?;
        sources.push(OutFile {
            path: format!("{library}/src/{dir}{global}.cs"),
            text: tree::class(&class),
        });
        // Each class and interface in a file of its own, as Kotlin's.
        for t in translator.take_types() {
            sources.push(OutFile {
                path: format!("{library}/src/{dir}{}.cs", t.name),
                text: tree::type_decl(&t),
            });
        }
        if let Some(class) = translator.tests(module)? {
            tests.push(OutFile {
                path: format!("{library}/tests/{dir}{}.cs", class.statics.name),
                text: tree::test_class(&class),
            });
            // What only tests reach, each class and interface a file of
            // its own there.
            for t in translator.take_types() {
                tests.push(OutFile {
                    path: format!("{library}/tests/{dir}{}.cs", t.name),
                    text: tree::type_decl(&t),
                });
            }
        }
        // std's support code for the module, beside it, as Kotlin's.
        let support = STD_SUPPORT
            .iter()
            .filter(|(m, ..)| library == "std" && module.path == *m);
        for (_, name, text) in support {
            sources.push(OutFile {
                path: format!("{library}/src/{dir}{name}"),
                text: text.to_string(),
            });
        }
        if module.path.is_empty() {
            program_loads = loads(p, module);
        }
    }
    // A library without a root module loads every module, as Lua's
    // `init.lua` requires each, so `temper run` runs them all.
    if !p.modules.iter().any(|m| m.path.is_empty()) {
        for module in &p.modules {
            for class in loads(p, module) {
                if !program_loads.contains(&class) {
                    program_loads.push(class);
                }
            }
        }
    }
    let mut files = vec![OutFile {
        path: format!("{library}/src/{root}.csproj"),
        text: library_project(p, &root, variant),
    }];
    files.extend(sources);
    files.extend(translate::host_files(p));
    files.extend([
        OutFile {
            path: format!("{library}/src/Logging.cs"),
            text: logging(&root),
        },
        OutFile {
            path: format!("{library}/program/{root}Program.csproj"),
            text: program_project(&root, &p.library.version, variant),
        },
        OutFile {
            path: format!("{library}/program/Program.cs"),
            text: program(&program_loads),
        },
    ]);
    if !tests.is_empty() {
        files.push(OutFile {
            path: format!("{library}/tests/{root}Test.csproj"),
            text: test_project(p, &root, variant),
        });
        files.extend(tests);
    }
    Ok(files)
}

/// The classes the program initializes, in order: those of other
/// libraries' modules the root module imports, then its own library's
/// modules it imports, directly or not, in the order they run, then its
/// own. Each class's static constructor is its module's init, which .NET
/// runs before the class is first used, so this runs them in Temper's
/// order, imports first, even where nothing reads them.
fn loads(p: &Program, module: &temper_tmpl::Module) -> Vec<String> {
    use temper_tmpl::analysis::module_imports;
    let mut needed = vec![module.decl];
    let mut external = Vec::new();
    let mut i = 0;
    while i < needed.len() {
        let m = p.modules.iter().find(|m| m.decl == needed[i]);
        for import in m.map(|m| module_imports(p, m)).unwrap_or_default() {
            let own = p.modules.iter().any(|m| m.decl == import.from);
            if own && !needed.contains(&import.from) {
                needed.push(import.from);
            } else if !own && !external.contains(&import.from) {
                external.push(import.from);
            }
        }
        i += 1;
    }
    external.sort();
    let order = (p.modules.iter())
        .filter(|m| needed.contains(&m.decl))
        .map(|m| m.decl);
    let class = |m| {
        let (namespace, class) = translate::global_class(p, m);
        format!("{namespace}.{class}")
    };
    external.into_iter().chain(order).map(class).collect()
}

/// A reference to a library's project beside this one's: std's as
/// Kotlin's, `../../std/src/TemperLang.Std.csproj`.
fn project_reference(p: &Program, name: &str) -> String {
    let project = translate::library_namespace(p, name);
    format!("\n    <ProjectReference Include=\"../../{name}/src/{project}.csproj\"/>")
}

/// The tests' project, as Kotlin's: on the library's project, MSTest,
/// and a JUnit logger, and on what only the tests use.
fn test_project(p: &Program, root: &str, variant: &Variant) -> String {
    let framework = variant.target_framework;
    let version = version_line(&p.library.version);
    let tests_only = temper_tmpl::analysis::test_only_libraries(p);
    let dependencies: String = (p.dependencies().iter())
        .filter(|(name, _)| tests_only.iter().any(|l| l == name))
        .map(|(name, _)| project_reference(p, name))
        .collect();
    format!(
        r#"<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>{framework}</TargetFramework>
    <RootNamespace>{root}</RootNamespace>
{version}    <IsPackable>true</IsPackable>
    <Nullable>enable</Nullable>
  </PropertyGroup>
  <ItemGroup>
    <ProjectReference Include="../src/{root}.csproj"/>{dependencies}
  </ItemGroup>
  <ItemGroup>
    <PackageReference Include="JunitXml.TestLogger" Version="3.0.134"/>
    <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.8.0"/>
    <PackageReference Include="MSTest.TestAdapter" Version="3.1.1"/>
    <PackageReference Include="MSTest.TestFramework" Version="3.1.1"/>
  </ItemGroup>
</Project>
"#
    )
}

/// A `<Version>` line, as Kotlin writes one only for a library that sets
/// its version; the frontend gives `0.0.0` to one that doesn't.
fn version_line(version: &str) -> String {
    if version == "0.0.0" {
        String::new()
    } else {
        format!("    <Version>{version}</Version>\n")
    }
}

/// The config's metadata, as Kotlin's `CsProj` writes it, a property a
/// line: the authors and description before `IsPackable`, then the
/// license and the project's URL after it. The URL is the homepage, or
/// else the repository, which also goes in `RepositoryUrl`, where Kotlin
/// gives only the repository, as the project's URL.
fn package_metadata(p: &Program) -> (String, String) {
    let meta = p.metadata();
    let line = |tag: &str, value: Option<&str>| match value {
        Some(value) => format!("    <{tag}>{}</{tag}>\n", temper_tmpl::xml_text(value)),
        None => String::new(),
    };
    let about =
        line("Authors", meta.get("authors")) + &line("Description", meta.get("description"));
    let links = line("PackageLicenseExpression", meta.get("license"))
        + &line(
            "PackageProjectUrl",
            meta.get("homepage").or(meta.get("repository")),
        )
        + &line("RepositoryUrl", meta.get("repository"));
    (about, links)
}

/// The library's project, as Kotlin's `CsProj` writes it for a library
/// without tests, and with a reference to each library it uses: its
/// tests' project may see its
/// internals, and it refers to the runtime's project beside it, which
/// packing turns into a package reference.
fn library_project(p: &Program, root: &str, variant: &Variant) -> String {
    let framework = variant.target_framework;
    let version = version_line(&p.library.version);
    let (about, links) = package_metadata(p);
    // Each library its code uses, by its project beside this one's
    // library; what only its tests use, their project refers to.
    let tests_only = temper_tmpl::analysis::test_only_libraries(p);
    let dependencies: String = (p.dependencies().iter())
        .filter(|(name, _)| !tests_only.iter().any(|l| l == name))
        .map(|(name, _)| project_reference(p, name))
        .collect();
    // std's `testing` throws MSTest's `AssertFailedException`, so std has
    // the packages its C# configuration lists.
    let packages = match p.library.name.as_str() {
        "std" => {
            r#"
  <ItemGroup>
    <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.8.0"/>
    <PackageReference Include="MSTest.TestFramework" Version="3.1.1"/>
  </ItemGroup>"#
        }
        _ => "",
    };
    format!(
        r#"<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>{framework}</TargetFramework>
    <RootNamespace>{root}</RootNamespace>
{version}{about}    <IsPackable>true</IsPackable>
{links}    <Nullable>enable</Nullable>
  </PropertyGroup>
  <ItemGroup>
    <InternalsVisibleTo Include="{root}Test"/>
  </ItemGroup>
  <ItemGroup>
    <ProjectReference Include="../../temper-core/TemperLang.Core.csproj"/>{dependencies}
  </ItemGroup>{packages}
</Project>
"#
    )
}

/// The program's project, as Kotlin's.
fn program_project(root: &str, version: &str, variant: &Variant) -> String {
    let framework = variant.target_framework;
    let version = version_line(version);
    format!(
        r#"<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>{framework}</TargetFramework>
    <OutputType>Exe</OutputType>
    <RootNamespace>{root}</RootNamespace>
{version}    <IsPackable>true</IsPackable>
    <Nullable>enable</Nullable>
  </PropertyGroup>
  <ItemGroup>
    <ProjectReference Include="../src/{root}.csproj"/>
  </ItemGroup>
</Project>
"#
    )
}

/// The entry point, Kotlin's template: simple logging to stdout, then the
/// root module's class initialized (or, with no root module, each
/// module's), after those of the modules it imports ([`loads`]), which runs their static constructors, in a top-level
/// turn, so async blocks start after them, then any async tasks waited
/// for (`Async` in full, as a library named `async` would hide it).
fn program(loads: &[String]) -> String {
    let loads = (loads.iter())
        .map(|class| {
            format!("            RuntimeHelpers.RunClassConstructor(typeof({class}).TypeHandle);\n")
        })
        .collect::<Vec<_>>()
        .concat();
    format!(
        "using System.Runtime.CompilerServices;
using TemperLang.Core;

static class Program
{{
    static void Main()
    {{
        Core.InitSimpleLogging();
        // Holds off async blocks until top-level code is done.
        global::TemperLang.Core.Async.BeginTopLevel();
        try
        {{
{loads}        }}
        finally
        {{
            global::TemperLang.Core.Async.EndTopLevel();
        }}
        global::TemperLang.Core.Async.WaitUntilSafeToExit();
    }}
}}
"
    )
}

/// Kotlin's `library-template/Logging.cs`, with the library's namespace
/// for `RootNamespaceSpot` and `Support` for `SupportNamespaceSpot`, as
/// Kotlin's, but without the template's byte order mark. Its public types
/// (`Logging.Init`, `ILoggingConsoleFactory`, `ILoggingConsole`) are how
/// a host sends a library's logs where it likes, so it's kept as it is.
pub const LOGGING_TEMPLATE: &str = r#"using System.Collections.Concurrent;
#if NET6_0_OR_GREATER
using Microsoft.Extensions.Logging;
#endif

namespace RootNamespaceSpot.SupportNamespaceSpot
{
    /// <summary>
    /// Only a single Init call of any kind can be made at most once.
    /// </summary>
    public static class Logging
    {
        public static void Init(ILoggingConsoleFactory factory)
        {
            LoggingConsoleFactory.Internal = new WrappedLoggingConsoleFactory(factory);
        }

#if NET6_0_OR_GREATER
        public static void Init(ILoggerFactory factory)
        {
            LoggingConsoleFactory.Internal = new TemperLang.Core.StandardLoggingConsoleFactory(
                factory
            );
        }
#endif

        internal static readonly TemperLang.Core.LazyInitLoggingConsoleFactory LoggingConsoleFactory =
            new TemperLang.Core.LazyInitLoggingConsoleFactory();
    }

    public interface ILoggingConsoleFactory
    {
        ILoggingConsole CreateConsole(string categoryName);
    }

    public interface ILoggingConsole
    {
        void Log(string message);
    }

    class WrappedLoggingConsoleFactory : TemperLang.Core.ILoggingConsoleFactory
    {
        readonly ILoggingConsoleFactory factory;
        readonly ConcurrentDictionary<string, WrappedLoggingConsole> consoles =
            new ConcurrentDictionary<string, WrappedLoggingConsole>();

        public WrappedLoggingConsoleFactory(ILoggingConsoleFactory factory)
        {
            this.factory = factory;
        }

        public TemperLang.Core.ILoggingConsole CreateConsole(string categoryName) =>
            // Cache because LoggerFactory also caches in similar circumstances.
            // Though we don't expect repeated calls on same categoryName.
            consoles.GetOrAdd(
                categoryName,
                (key) => new WrappedLoggingConsole(factory.CreateConsole(categoryName))
            );
    }

    class WrappedLoggingConsole : TemperLang.Core.ILoggingConsole
    {
        private ILoggingConsole console;

        public WrappedLoggingConsole(ILoggingConsole console)
        {
            this.console = console;
        }

        public void Log(string message)
        {
            console.Log(message);
        }
    }
}
"#;

/// The library's `Logging.cs`.
fn logging(root: &str) -> String {
    LOGGING_TEMPLATE
        .replace("RootNamespaceSpot", root)
        .replace("SupportNamespaceSpot", "Support")
}
