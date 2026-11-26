//! The C# backend on Kotlin Temper's functional tests that tmpl's
//! translation covers so far, those with classes and interfaces too
//! (`temper_be_testkit::CLASSES`), with nullable types and `orelse`
//! (`temper_be_testkit::NULLABLES`), with generics
//! (`temper_be_testkit::GENERICS`), with function values and parameters'
//! defaults (`FUNCTIONS` and `DEFAULTS`), and with core's other types and
//! members (`CORE_TYPES`), and with a library's own connected functions
//! and classes, its host code in `.cs` files beside its modules, as is and
//! under `--no-connected` (`CONNECTED`), and with divisions that can't
//! fail and maps (`DIVISIONS`), and with labeled loops' jumps and classes
//! declared in functions (`LOOPS_AND_CLASSES`), and with generators,
//! `async`, and `await`, as iterators (`ASYNC`), in `temper-funtests`'s
//! `cases/`, and on the shared programs
//! (`crates/temper-be-testkit/programs/`), which cover operators,
//! jumps, module state, string order, several modules, imports, tests,
//! classes, and nullables beyond those tests.
//!
//! - `goldens`: each program's generated library, laid out as Kotlin's,
//!   with its `ROOTGlobal.cs` matching the golden in `tests/goldens/`,
//!   named for the test's path (`algos-fibonacci.cs`), and for a library
//!   of more than one module, with classes, or with tests, its other files
//!   theirs, named
//!   for the path and the file's place (`modules.Helpers.HelpersGlobal.cs`,
//!   `testing.tests.TestingTests.cs`). To accept new or changed output,
//!   run with `UPDATE_SNAPSHOTS=1` and review the diff.
//! - `runs`: without MSBuild or NuGet, Roslyn's `csc` (from the SDK that
//!   `dotnet` finds, or `DOTNET`) compiles `TemperLang.Core`
//!   (`crates/temper-be-csharp/runtime/temper-core`) once, for .NET 6 as its
//!   `.csproj` says, into `CARGO_TARGET_TMPDIR`, reused until its sources
//!   or the SDK change; then every generated library's sources in one more
//!   `csc` run, with nullable reference types on, every warning, and every
//!   .NET analyzer an error, but for those [`ALLOWED`] lists; then one
//!   generated `Program.cs`, as its own program, and a runner of the
//!   others, which runs each library's static constructor in turn, and
//!   `module-state` from 8 threads, under a culture (`sv-SE`) whose minus
//!   sign isn't `-`, so formatting by the current culture shows. Each logs
//!   what the test expects of Kotlin's `csharp` backend. The runtime's
//!   one dependency, `Microsoft.Extensions.Logging` 8.0.0, comes from
//!   NuGet's cache (`~/.nuget/packages`, or `NUGET_PACKAGES`), never the
//!   network; `dotnet restore` of the runtime's project puts it there.
//!   Libraries that import others compile with them, in the same
//!   assembly, where a second library of a name (`types/float/basics`
//!   after `types/int/basics`) takes a number (`basics2`), but for one
//!   with host code, which names its namespace, so compiles, as strictly,
//!   into an assembly of its own, with its own `Program.cs`. A test that
//!   runs as tests (`TESTS`, and the shared programs of `AS_TESTS`)
//!   compiles with std, as strictly, against MSTest's framework from
//!   NuGet's cache, the library and then its tests' project against it,
//!   which sees its internals, so the library builds without what only
//!   tests reach, and runs by reflection
//!   ([`RUN_TESTS`]), its failures compared with its `expectedFailures`.
//!   Skipped, with a message, without `dotnet`, the .NET 6 targeting pack,
//!   or those packages; with `TEMPER_REQUIRE_CHECKERS` set, as in CI,
//!   any of them missing fails it instead.

use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::{env, fs};
use temper_be_csharp::{CSHARP, LOGGING_TEMPLATE, OutFile, names, translate};
use temper_be_testkit::{self as kit, Case, Goldens};

/// A library of names that would hide the classes the translator names,
/// for C# alone: `math` is `Math`, `core` `Core`, and `support`
/// `Support`; and a parameter `object`, a C# keyword, is `@object`.
/// Also `Boolean`'s `<`, which C# lacks (Kotlin writes it anyway), and
/// `global::` in an interpolated string's hole, which takes parentheses. (A
/// library's root namespace hides classes too, the translator's and
/// other libraries' alike, but only for the library's own code and its
/// dependents, which are separate assemblies; this test compiles every
/// library into one, so it leaves that out.)
const HIDING: &str = "# Hiding

    export let support = \"s\";
    let math(a: Int, b: Int): Int { a.min(b) }
    let twice(object: Int): Int { object + object }
    let core(a: Int, b: Int): Int throws Bubble { a / b }
    let before(a: Boolean, b: Boolean): Boolean { a < b }
    console.log(\"${math(3, 2).toString()} ${twice(2).toString()} ${core(7, 2).toString()} ${support}\");
    console.log(before(false, true).toString());
    console.log(\"${3.min(2).toString()} ${4.max(1).toString()}\");
";

/// Classes for C# alone: a call through an interface reaches the
/// member Temper finds on the class, the nearest, not the interface's
/// own default that C# would call, which `C`'s `a` hides; and an `if`
/// inside an `if` that has an `else`, which Kotlin's C# printed without
/// braces, so the `else` went with the inner `if`.
const DISPATCH: &str = "# Dispatch

    interface A {
      a(): String { \"A wins!\" }
      get b(): String { \"A's b\" }
    }
    interface C extends A {
      a(): String { \"C wins!\" }
      get b(): String { \"C's b\" }
    }
    class X extends C {}
    let v: A = new X();
    console.log(\"${v.a()} ${v.b}\");
    let pick(a: Boolean, b: Boolean): String {
      if (a) {
        if (b) { return \"ab\"; }
      } else {
        return \"not a\";
      }
      \"a only\"
    }
    console.log(\"${pick(true, true)} ${pick(true, false)} ${pick(false, true)}\");
";

/// Values dropped where C# takes only some expressions as statements, as
/// Wherewith's `if (…) { lexer.scan("\n"); }`, whose call tmpl wraps in
/// an unchecked `NonNull`: a call alone, `_ = e;` for a getter or another
/// value with effects, and nothing for a value without.
const DROPPED: &str = "# Dropped

    interface Lexer {
      scan(text: String): String?;
      get count(): Int;
    }
    class L extends Lexer {
      public var n: Int = 0;
      public scan(text: String): String? { n += 1; if (text == \"x\") { null } else { text } }
      public get count(): Int { n += 10; n }
    }
    let go(lexer: Lexer, b: Boolean, d: Int): Int throws Bubble {
      if (b) { lexer.scan(\"x\"); }
      if (b) { lexer.count; }
      if (b) { lexer.scan(\"y\") ?? \"z\"; }
      if (b) { 7 / d; }
      if (b) { d + 1; }
      if (b) { lexer is L; }
      lexer.scan(\"x\");
      0
    }
    let l = new L();
    go(l, true, 1) orelse panic();
    console.log(l.n.toString());
    console.log((go(l, true, 0) orelse -1).toString());
    console.log(l.n.toString());
";

/// The .NET analyzers' rules allowed in generated code, each with why;
/// every other rule is an error (`AnalysisMode` `All`, as warnings, and
/// warnings as errors), in the libraries' sources, Kotlin's `Logging.cs`
/// with them. Only these are allowed so far, ones that Kotlin's public
/// surface, which the first variant keeps, breaks:
///
/// - CA1822, members that don't use `this` could be static: a Temper
///   method is an instance method, whatever its body reads.
/// - CA2211, non-constant fields visible: an exported `var` is a public
///   static field in Kotlin's surface (`Total`), not a property.
/// - CA1720, names that are type names: a parameter keeps the source's
///   name, which callers may pass by name (`functions/connected`'s
///   `length(string)` is `@string`).
///
/// - CA1716, names that are other languages' keywords: Kotlin keeps the
///   source's, so std's regex class `Or` is `Or`, which VB reserves.
/// - CA1707, no underscores in names: Kotlin's Pascal case joins a digit
///   and a letter with one (std's json `Int32_Value`, `A98_Rgb`).
/// - CA1725, an override's parameter names differing from its
///   interface's: each keeps its source's, as Kotlin's (std's json
///   `ExplainJsonError(error)` for `explanation`).
///
/// `Program.cs`, Kotlin's template, and the runner
/// are compiled without analyzers: `Program` is in the global namespace
/// (CA1050). std's support code, Kotlin's own C# (`RegexSupport.cs`), is
/// compiled with the analyzers' rules off ([`std_options`]), but
/// as strictly as the rest otherwise.
const ALLOWED: &[&str] = &["CA1707", "CA1716", "CA1720", "CA1725", "CA1822", "CA2211"];

/// The tests, by key, that declare locals they never read on purpose
/// (`semantics/type-checked-locals`' `a`s), so csc's warnings of them
/// (CS0168, CS0219) are off in their files.
const UNUSED: [&str; 1] = ["semantics-type-checked-locals"];

fn cases() -> Vec<Case> {
    let tests = [
        &kit::CLASSES[..],
        &kit::NULLABLES[..],
        &kit::GENERICS[..],
        &kit::NAMES[..],
        &kit::FUNCTIONS[..],
        &kit::DEFAULTS[..],
        &kit::CORE_TYPES[..],
        &kit::CONNECTED[..],
        &kit::DIVISIONS[..],
        &kit::LOOPS_AND_CLASSES[..],
        &kit::TYPING[..],
        &kit::ANY_VALUES[..],
        &kit::CORE_CHECKS[..],
        &kit::ERRORS[..],
        &kit::TYPE_VALUES[..],
        &kit::ASYNC[..],
        &kit::TESTS[..],
        &kit::WITH_STD[..],
        &kit::NET[..],
    ]
    .concat();
    let mut cases = kit::cases_with("csharp", &kit::PROGRAMS, &tests);
    cases.push(Case::inline(
        "hiding",
        "hiding.temper.md",
        HIDING,
        vec!["2 4 3 s".into(), "true".into(), "2 4".into()],
    ));
    cases.push(Case::inline(
        "dispatch",
        "dispatch.temper.md",
        DISPATCH,
        vec!["C wins! C's b".into(), "ab a only not a".into()],
    ));
    cases.push(Case::inline(
        "dropped",
        "dropped.temper.md",
        DROPPED,
        vec!["13".into(), "-1".into(), "25".into()],
    ));
    cases
}

/// A case's generated files.
fn generate(case: &Case) -> Vec<OutFile> {
    translate(&kit::program_for(case, &CSHARP.rules), &CSHARP)
        .unwrap_or_else(|e| panic!("{}: {e}", case.key))
}

fn runtime_dir() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime")
}

#[test]
fn goldens() {
    kit::with_stack(|| {
        let mut goldens = Goldens::new(env!("CARGO_MANIFEST_DIR"));
        let template = |name: &str| {
            let text =
                fs::read_to_string(runtime_dir().join("library-template").join(name)).unwrap();
            text.trim_start_matches('\u{feff}').to_string()
        };
        let program_template = template("Program.cs");
        for case in cases() {
            let files = generate(&case);
            let library = &case.library;
            let root = names::root_namespace(library);
            let paths: Vec<&str> = files.iter().map(|f| f.path.as_str()).collect();
            let at = |path: String| {
                (paths.iter().position(|p| *p == path))
                    .unwrap_or_else(|| panic!("{}: no {path} in {paths:?}", case.key))
            };
            let order = [
                at(format!("{library}/src/{root}.csproj")),
                at(format!("{library}/src/{root}Global.cs")),
                at(format!("{library}/src/Logging.cs")),
                at(format!("{library}/program/{root}Program.csproj")),
                at(format!("{library}/program/Program.cs")),
            ];
            let single = files.len() == 5;
            if single {
                assert_eq!(order, [0, 1, 2, 3, 4], "{}", case.key);
            }
            let files_all = files.clone();
            let files: Vec<OutFile> = order.iter().map(|&i| files_all[i].clone()).collect();
            for part in [
                "<TargetFramework>net6.0</TargetFramework>".to_string(),
                format!("<RootNamespace>{root}</RootNamespace>"),
                "<Nullable>enable</Nullable>".into(),
                format!("<InternalsVisibleTo Include=\"{root}Test\"/>"),
                "<ProjectReference Include=\"../../temper-core/TemperLang.Core.csproj\"/>".into(),
            ] {
                assert!(files[0].text.contains(&part), "{}: {part}", case.key);
            }
            assert!(
                files[3].text.contains(&format!(
                    "<OutputType>Exe</OutputType>\n    <RootNamespace>{root}</RootNamespace>"
                )) && files[3].text.contains(&format!(
                    "<ProjectReference Include=\"../src/{root}.csproj\"/>"
                )),
                "{}",
                files[3].text
            );
            // Kotlin's templates, with the library's names.
            let logging = template("Logging.cs")
                .replace("RootNamespaceSpot", &root)
                .replace("SupportNamespaceSpot", "Support");
            assert_eq!(files[2].text, logging, "{}", case.key);
            // `Async` in full, as a library named `async` hides it.
            let main = program_template
                .replace("typeof(Program)", &format!("typeof({root}.{root}Global)"))
                .replace(" Async.", " global::TemperLang.Core.Async.");
            if files[4].text.matches("RunClassConstructor").count() == 1 {
                assert_eq!(files[4].text, main, "{}", case.key);
            } else if single {
                goldens.check(&format!("{}.program.Program.cs", case.key), &files[4].text);
            }
            goldens.check(&format!("{}.cs", case.key), &files[1].text);
            // Other modules' files, tests, and a program that loads
            // several classes have goldens of their own.
            if !single {
                for (i, file) in files_all.iter().enumerate() {
                    if [order[1], order[2]].contains(&i) {
                        continue;
                    }
                    let rest = &file.path[library.len() + 1..];
                    let rest = rest.strip_prefix("src/").unwrap_or(rest);
                    goldens.check(
                        &format!("{}.{}", case.key, rest.replace('/', ".")),
                        &file.text,
                    );
                }
            }
        }
        goldens.finish();
    });
}

/// What only the `reach` program's tests reach is only in the tests'
/// project, and what nothing reaches nowhere, as tmpl's
/// `reach::partition` splits the program; `runs` builds the library
/// without its tests, and runs its test, which passes only if they're
/// set.
#[test]
fn test_parts_apart() {
    kit::with_stack(|| {
        let case = (cases().into_iter()).find(|c| c.key == "reach").unwrap();
        let files = generate(&case);
        let lines = (files.iter().filter(|f| f.path.ends_with(".cs"))).flat_map(|f| {
            let tests = f.path.contains("/tests/");
            (f.text.lines()).map(move |line| (f.path.as_str(), tests, line))
        });
        let tests_only = [
            "ForTests",
            "testFactor",
            "testCount",
            "ITestShape",
            "TestSquare",
            "AddArea",
        ];
        let unreached = [
            "UnreachedFunction",
            "UnreachedClass",
            "IUnreachedInterface",
            "unreachedValue",
        ];
        kit::check_test_parts_apart(lines, &tests_only, &unreached);
    });
}

/// A helper that what only tests reach and the tests both use is the
/// test class's once.
#[test]
fn test_class_helpers_once() {
    kit::with_stack(|| {
        let text = "# Helped\n\n    let shownForTests(x: Float64): String { x.toString() }\n\n    test(\"shows\") {\n      assert(shownForTests(1.5) == 1.5.toString());\n    }\n";
        let case = Case::inline("helped", "helped.temper.md", text, Vec::new());
        let files = generate(&case);
        let tests = (files.iter())
            .find(|f| f.path.ends_with("/tests/HelpedTests.cs"))
            .unwrap();
        let defined = tests.text.matches("static string FormatFloat64(").count();
        assert_eq!(defined, 1, "{}", tests.text);
    });
}

/// Host code a library needs but lacks fails its translation, naming the
/// file, rather than C#'s compiler.
#[test]
fn missing_host_code() {
    kit::with_stack(|| {
        for (key, file) in [
            ("functions-connected", "WorkConnected.cs"),
            ("types-connected", "Counter.cs"),
        ] {
            let mut case = (cases().into_iter()).find(|c| c.key == key).unwrap();
            case.host_files.retain(|(path, _)| path != file);
            let error = translate(&kit::program_for(&case, &CSHARP.rules), &CSHARP).unwrap_err();
            assert!(error.what.contains(file), "{key}: {error}");
        }
    });
}

#[test]
fn logging_template_is_kotlins() {
    let template = fs::read_to_string(runtime_dir().join("library-template/Logging.cs")).unwrap();
    assert_eq!(LOGGING_TEMPLATE, template.trim_start_matches('\u{feff}'));
}

/// What compiling and running C# needs, from the .NET SDK and NuGet's
/// cache.
struct Dotnet {
    dotnet: String,
    /// `csc.dll`, Roslyn's compiler, run by `dotnet exec`.
    csc: PathBuf,
    /// The .NET analyzers that come with the SDK.
    analyzers: Vec<PathBuf>,
    /// .NET 6's reference assemblies, and `Microsoft.Extensions.Logging`'s
    /// and its dependencies' assemblies.
    references: Vec<PathBuf>,
    packages: Vec<PathBuf>,
    /// The SDK's version, for the runtime's cache key.
    version: String,
}

/// The packages the runtime depends on for .NET 6 (its `.csproj`'s
/// `Microsoft.Extensions.Logging` 8.0.0, and what that depends on), as
/// NuGet's cache keeps them.
const PACKAGES: &[(&str, &str)] = &[
    (
        "microsoft.extensions.dependencyinjection",
        "Microsoft.Extensions.DependencyInjection",
    ),
    (
        "microsoft.extensions.dependencyinjection.abstractions",
        "Microsoft.Extensions.DependencyInjection.Abstractions",
    ),
    (
        "microsoft.extensions.logging",
        "Microsoft.Extensions.Logging",
    ),
    (
        "microsoft.extensions.logging.abstractions",
        "Microsoft.Extensions.Logging.Abstractions",
    ),
    (
        "microsoft.extensions.options",
        "Microsoft.Extensions.Options",
    ),
    (
        "microsoft.extensions.primitives",
        "Microsoft.Extensions.Primitives",
    ),
];

/// The highest version directory under `dir` that starts with `prefix`.
fn newest(dir: &Path, prefix: &str) -> Option<PathBuf> {
    let mut found: Vec<(Vec<u32>, PathBuf)> = fs::read_dir(dir)
        .ok()?
        .filter_map(|e| {
            let path = e.ok()?.path();
            let name = path.file_name()?.to_str()?.to_string();
            name.starts_with(prefix).then(|| {
                let key = name
                    .split(['.', '-'])
                    .map(|p| p.parse().unwrap_or(0))
                    .collect();
                (key, path)
            })
        })
        .collect();
    found.sort();
    found.pop().map(|(_, p)| p)
}

/// The SDK, .NET 6's targeting pack, and the runtime's packages, or what's
/// missing.
fn find_dotnet() -> Result<Dotnet, String> {
    let dotnet = temper_tmpl::tools::tool("DOTNET", "dotnet");
    let sdks = Command::new(&dotnet)
        .arg("--list-sdks")
        .output()
        .map_err(|_| "no dotnet (on PATH, or named by DOTNET)".to_string())?;
    let sdks = String::from_utf8_lossy(&sdks.stdout).into_owned();
    // `6.0.402 [/path/to/dotnet/sdk]`, oldest first. The newest 6.x, as
    // Kotlin's projects target .NET 6, so its analyzers are the ones the
    // allowances were made for; else the newest.
    let sdks: Vec<(String, PathBuf)> = sdks
        .lines()
        .filter_map(|l| {
            let (version, dir) = l.split_once(" [")?;
            Some((
                version.to_string(),
                PathBuf::from(dir.trim_end_matches(']')),
            ))
        })
        .collect();
    let (version, sdk) = (sdks.iter().rev())
        .find(|(v, _)| v.starts_with("6."))
        .or(sdks.last())
        .cloned()
        .ok_or("no .NET SDK")?;
    let root = sdk.parent().ok_or("no .NET root")?.to_path_buf();
    let sdk = sdk.join(&version);
    let csc = sdk.join("Roslyn/bincore/csc.dll");
    if !csc.is_file() {
        return Err(format!("no csc.dll in {}", sdk.display()));
    }
    let analyzer_dir = sdk.join("Sdks/Microsoft.NET.Sdk/analyzers");
    let analyzers = [
        "Microsoft.CodeAnalysis.NetAnalyzers.dll",
        "Microsoft.CodeAnalysis.CSharp.NetAnalyzers.dll",
    ]
    .iter()
    .map(|a| analyzer_dir.join(a))
    .collect::<Vec<_>>();
    if !analyzers.iter().all(|a| a.is_file()) {
        return Err(format!("no .NET analyzers in {}", analyzer_dir.display()));
    }
    let pack = newest(&root.join("packs/Microsoft.NETCore.App.Ref"), "6.")
        .ok_or("no .NET 6 targeting pack (Microsoft.NETCore.App.Ref 6.x)")?;
    let mut references = Vec::new();
    kit::files_with(&pack.join("ref/net6.0"), "dll", &mut references);
    let cache = env::var_os("NUGET_PACKAGES")
        .map(PathBuf::from)
        .or_else(|| env::var_os("HOME").map(|h| PathBuf::from(h).join(".nuget/packages")))
        .ok_or("no NuGet cache")?;
    let mut packages = Vec::new();
    for (id, assembly) in PACKAGES {
        let dll = cache
            .join(id)
            .join("8.0.0/lib/net6.0")
            .join(format!("{assembly}.dll"));
        if !dll.is_file() {
            return Err(format!(
                "no {id} 8.0.0 in NuGet's cache ({}); `dotnet restore` of \
                 crates/temper-be-csharp/runtime/temper-core's project, copied elsewhere, puts it there",
                cache.display()
            ));
        }
        packages.push(dll);
    }
    Ok(Dotnet {
        dotnet,
        csc,
        analyzers,
        references,
        packages,
        version,
    })
}

/// The symbols MSBuild defines for a .NET 6 release build, which
/// `TemperLang.Core` and `Logging.cs` test (`TRACE` keeps `Trace`'s
/// calls, which logging falls back to).
const DEFINES: &str = "TRACE;RELEASE;NET;NET6_0;NETCOREAPP;NET5_0_OR_GREATER;NET6_0_OR_GREATER";

impl Dotnet {
    /// `csc` with the references and the options given, its sources named
    /// in a response file beside its output (`-out:`), since a command
    /// line naming them all can be more than Windows takes.
    fn csc(&self, options: &[String], extra: &[&Path], sources: &[PathBuf]) -> Output {
        let mut command = Command::new(&self.dotnet);
        command
            .env("DOTNET_CLI_TELEMETRY_OPTOUT", "1")
            .env("DOTNET_NOLOGO", "1")
            .arg("exec")
            .arg(&self.csc)
            .args([
                "-nologo",
                "-noconfig",
                "-nostdlib",
                "-langversion:10",
                "-deterministic",
                &format!("-define:{DEFINES}"),
            ])
            .args(options);
        for r in self.references.iter().chain(&self.packages) {
            command.arg(format!("-r:{}", r.display()));
        }
        for r in extra {
            command.arg(format!("-r:{}", r.display()));
        }
        let out = (options.iter())
            .find_map(|o| o.strip_prefix("-out:"))
            .expect("csc's -out:");
        let rsp = PathBuf::from(format!("{out}.sources.rsp"));
        // One quoted path to a line; a `"` can't be in a Windows path.
        let lines: Vec<String> = (sources.iter())
            .map(|s| format!("\"{}\"\n", s.display()))
            .collect();
        fs::create_dir_all(rsp.parent().unwrap()).unwrap();
        fs::write(&rsp, lines.concat()).unwrap();
        command.arg(format!("@{}", rsp.display()));
        command
            .output()
            .unwrap_or_else(|e| panic!("csc, {} files in {}: {e}", sources.len(), rsp.display()))
    }
}

/// The runtime's assembly, compiled once and kept under
/// `CARGO_TARGET_TMPDIR` until its sources or the SDK change: without
/// nullable annotations, as its `.csproj` has it, and without warnings,
/// since it isn't generated here.
fn runtime_assembly(dotnet: &Dotnet) -> PathBuf {
    let root = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-csharp-runtime");
    let dll = root.join("TemperLang.Core.dll");
    let mut sources = Vec::new();
    kit::files_with(&runtime_dir().join("temper-core"), "cs", &mut sources);
    let options = vec![
        "-target:library".to_string(),
        "-optimize+".into(),
        "-warn:0".into(),
        format!("-out:{}", dll.display()),
    ];
    kit::cached(&root, (&dotnet.version, &options), &sources, &dll, || {
        let build = dotnet.csc(&options, &[], &sources);
        assert!(
            build.status.success(),
            "csc, the runtime:\n{}",
            String::from_utf8_lossy(&build.stdout)
        );
    });
    dll
}

/// The analyzers' configuration for generated code: every rule a warning
/// (`AnalysisMode` `All`), but those [`ALLOWED`].
fn analyzer_config() -> String {
    let mut config =
        String::from("is_global = true\ndotnet_analyzer_diagnostic.severity = warning\n");
    for rule in ALLOWED {
        config.push_str(&format!("dotnet_diagnostic.{rule}.severity = none\n"));
    }
    config
}

/// A file's path as a global analyzer config's section names it: absolute,
/// with `/` between its parts on every system, as Roslyn wants section
/// names, which are `.editorconfig` paths; on Windows, as `D:/a/b.cs`,
/// which Roslyn matches with the file csc is given as `D:\a\b.cs`, its
/// separators made `/`.
fn section_path(path: &Path) -> String {
    let path = path.display().to_string();
    match cfg!(windows) {
        true => path.replace('\\', "/"),
        false => path,
    }
}

/// The options that compile std's output, written in `dir`, to `dll`:
/// `strict`'s, with [`analyzer_config`]'s rules off for std's support
/// code, Kotlin's own C# (`RegexSupport.cs`), in a configuration of its
/// own beside it.
fn std_options(
    strict: &dyn Fn(&Path) -> Vec<String>,
    dll: &Path,
    std: &[OutFile],
    dir: &Path,
) -> Vec<String> {
    let mut config = analyzer_config();
    for file in std {
        if (temper_be_csharp::STD_SUPPORT.iter()).any(|(_, name, _)| file.path.ends_with(name)) {
            config.push_str(&format!(
                "\n[{}]\ndotnet_analyzer_diagnostic.severity = none\n",
                section_path(&dir.join(&file.path))
            ));
        }
    }
    let path = dir.join("std.globalconfig");
    fs::create_dir_all(dir).unwrap();
    fs::write(&path, config).unwrap();
    let mut options = strict(dll);
    for o in &mut options {
        if o.starts_with("-analyzerconfig:") {
            *o = format!("-analyzerconfig:{}", path.display());
        }
    }
    options
}

/// The runner: runs each library named on its command line, by its
/// generated `Program.cs`'s `Main` (renamed, so they can share one
/// program), in a culture whose minus sign is U+2212; then, for
/// `threads`, `module-state`'s `Tick` and `Report` from 8 threads.
fn runner(mains: &[(String, String)]) -> String {
    let mut cases = String::new();
    for (key, class) in mains {
        cases.push_str(&format!(
            "                case \"{key}\": {class}.Run(); break;\n"
        ));
    }
    format!(
        r#"using System;
using System.Globalization;
using System.Threading;
using TemperLang.Core;

static class Runner
{{
    static void Main(string[] args)
    {{
        try
        {{
            CultureInfo culture = new CultureInfo("sv-SE");
            CultureInfo.DefaultThreadCurrentCulture = culture;
            CultureInfo.CurrentCulture = culture;
        }}
        catch (CultureNotFoundException)
        {{
            // Without ICU, .NET has only the invariant culture.
        }}
        switch (args[0])
        {{
{cases}                case "threads": Threads(); break;
        }}
    }}

    static void Threads()
    {{
        Core.InitSimpleLogging();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.Length; i++)
        {{
            threads[i] = new Thread(() =>
            {{
                for (int j = 0; j < 1000; j++)
                {{
                    ModuleState.ModuleStateGlobal.Tick();
                    ModuleState.ModuleStateGlobal.Report();
                }}
            }});
            threads[i].Start();
        }}
        foreach (Thread thread in threads)
        {{
            thread.Join();
        }}
        Console.WriteLine("ticks " + ModuleState.ModuleStateGlobal.Ticks.ToString(CultureInfo.InvariantCulture));
    }}
}}
"#
    )
}

/// A program's `runtimeconfig.json`: .NET 6, or a later one if that's
/// all there is.
const RUNTIME_CONFIG: &str = r#"{"runtimeOptions":{"tfm":"net6.0","rollForward":"Major","framework":{"name":"Microsoft.NETCore.App","version":"6.0.0"}}}
"#;

#[test]
fn runs() {
    let dotnet = match find_dotnet() {
        Ok(d) => d,
        Err(missing) => {
            kit::missing(&missing);
            return;
        }
    };
    kit::with_stack(move || {
        let runtime = runtime_assembly(&dotnet);
        let out = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-csharp");
        let _ = fs::remove_dir_all(&out);
        let bin = out.join("bin");
        fs::create_dir_all(&bin).unwrap();
        // Every library compiles into one assembly, so two of one name
        // (`types/int/basics` and `types/float/basics`) take others.
        // A library with host code names its own namespace (`namespace
        // Work`), so it can't be renamed, and compiles on its own.
        let (tested, cases): (Vec<Case>, Vec<Case>) =
            cases().into_iter().partition(|c| c.tests.is_some());
        let (hosted, mut cases): (Vec<Case>, Vec<Case>) =
            cases.into_iter().partition(|c| !c.host_files.is_empty());
        // std's `testing` needs MSTest, so std's assembly does, and the
        // libraries that import std's modules.
        let mstest = mstest(&dotnet);
        if mstest.is_none() {
            kit::missing("MSTest.TestFramework 3.1.1 in NuGet's cache, for std's assembly");
            cases.retain(|c| !c.needs_std());
        }
        // In one assembly, `regex/match`'s namespace `Match` would hide
        // std's class `Match` from every other library too, not only from
        // its own code, as apart: so it's named by its key there.
        for case in cases.iter_mut().filter(|c| c.library == "match") {
            case.library = case.key.clone();
        }
        let mut seen = std::collections::HashSet::new();
        for case in &mut cases {
            let mut n = 1;
            let base = case.library.clone();
            while !seen.insert(case.library.clone()) {
                n += 1;
                case.library = format!("{base}{n}");
            }
        }
        let mut sources = Vec::new();
        let mut mains = Vec::new();
        let mut first_program = None;
        for case in &cases {
            let root = out.join("src").join(&case.key);
            for file in generate(case) {
                let path = root.join(&file.path);
                fs::create_dir_all(path.parent().unwrap()).unwrap();
                if file.path.ends_with("/program/Program.cs") {
                    // Each `Program.cs` declares `Program`; the runner
                    // calls each as its own class.
                    let class = format!("Program_{}", case.key.replace('-', "_"));
                    let text = file
                        .text
                        .replace("static class Program", &format!("static class {class}"))
                        .replace("static void Main()", "internal static void Run()");
                    let renamed = path.with_file_name(format!("{class}.cs"));
                    fs::write(&renamed, text).unwrap();
                    mains.push((case.key.clone(), class));
                    first_program.get_or_insert(path.clone());
                    fs::write(&path, &file.text).unwrap();
                    continue;
                }
                if UNUSED.contains(&case.key.as_str()) && file.path.ends_with(".cs") {
                    let pragma = "#pragma warning disable CS0168, CS0219\n";
                    fs::write(&path, format!("{pragma}{}", file.text)).unwrap();
                } else {
                    fs::write(&path, &file.text).unwrap();
                }
                // Tests run on their own ([`run_tests`]).
                if file.path.ends_with(".cs") && !file.path.contains("/tests/") {
                    sources.push(path);
                }
            }
        }
        // Every library, strictly.
        let config = out.join("strict.globalconfig");
        fs::write(&config, analyzer_config()).unwrap();
        let generated = bin.join("Generated.dll");
        let strict = |out: &Path| {
            let mut options = vec![
                "-target:library".to_string(),
                "-nullable:enable".into(),
                "-warn:9999".into(),
                "-warnaserror+".into(),
                // MSBuild's own `NoWarn`: assembly version unification.
                "-nowarn:CS1701,CS1702".into(),
                format!("-analyzerconfig:{}", config.display()),
                format!("-out:{}", out.display()),
            ];
            for a in &dotnet.analyzers {
                options.push(format!("-analyzer:{}", a.display()));
            }
            options
        };
        // std's assembly, which the libraries that import std's modules
        // (`regex/match`) reference, strictly too, its support code and
        // all.
        let std_dll = bin.join("TemperLang.Std.dll");
        let std = translate(&kit::std_program(), &CSHARP).unwrap_or_else(|e| panic!("std: {e}"));
        let std_root = out.join("src/std");
        let mut std_sources = Vec::new();
        for file in &std {
            let path = std_root.join(&file.path);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(&path, &file.text).unwrap();
            if file.path.ends_with(".cs") && file.path.contains("/src/") {
                std_sources.push(path);
            }
        }
        if let Some(mstest) = &mstest {
            let options = std_options(&strict, &std_dll, &std, &std_root);
            let build = dotnet.csc(&options, &[&runtime, mstest], &std_sources);
            // The configuration too, whose sections, for std's support
            // code, csc may not have matched with its files.
            assert!(
                build.status.success(),
                "csc, std (nullable, every warning and analyzer an error):\n{}\n{}:\n{}",
                String::from_utf8_lossy(&build.stdout),
                std_root.join("std.globalconfig").display(),
                fs::read_to_string(std_root.join("std.globalconfig")).unwrap_or_default()
            );
            fs::copy(mstest, bin.join(mstest.file_name().unwrap())).unwrap();
        }
        let mut references = vec![runtime.as_path()];
        if mstest.is_some() {
            references.push(&std_dll);
        }
        let build = dotnet.csc(&strict(&generated), &references, &sources);
        assert!(
            build.status.success(),
            "csc, generated C# (nullable, every warning and analyzer an error):\n{}",
            String::from_utf8_lossy(&build.stdout)
        );
        // One `Program.cs` as it is, and the runner of the others.
        let first = first_program.unwrap();
        let program = bin.join("Program.dll");
        let options = [
            "-target:exe".to_string(),
            "-nullable:enable".into(),
            "-warnaserror+".into(),
            format!("-out:{}", program.display()),
        ];
        references.push(&generated);
        let build = dotnet.csc(&options, &references, &[first]);
        assert!(
            build.status.success(),
            "csc, Program.cs:\n{}",
            String::from_utf8_lossy(&build.stdout)
        );
        let mut runner_sources: Vec<PathBuf> = mains
            .iter()
            .map(|(key, class)| {
                let dir = out.join("src").join(key);
                let mut found = Vec::new();
                kit::files_with(&dir, "cs", &mut found);
                found
                    .into_iter()
                    .find(|p| p.file_stem().is_some_and(|s| *s == **class))
                    .unwrap()
            })
            .collect();
        let runner_path = out.join("Runner.cs");
        fs::write(&runner_path, runner(&mains)).unwrap();
        runner_sources.push(runner_path);
        let runner_dll = bin.join("Runner.dll");
        let options = [
            "-target:exe".to_string(),
            "-main:Runner".into(),
            "-nullable:enable".into(),
            format!("-out:{}", runner_dll.display()),
        ];
        let build = dotnet.csc(&options, &references, &runner_sources);
        assert!(
            build.status.success(),
            "csc, the runner:\n{}",
            String::from_utf8_lossy(&build.stdout)
        );
        // Beside the programs: their configuration, and the assemblies
        // they load.
        for name in ["Program", "Runner"] {
            fs::write(
                bin.join(format!("{name}.runtimeconfig.json")),
                RUNTIME_CONFIG,
            )
            .unwrap();
        }
        for dll in dotnet.packages.iter().chain([&runtime]) {
            fs::copy(dll, bin.join(dll.file_name().unwrap())).unwrap();
        }
        let mut failures = Vec::new();
        let dotnet_run = |dll: &Path, args: &[&str]| {
            Command::new(&dotnet.dotnet)
                .env("DOTNET_CLI_TELEMETRY_OPTOUT", "1")
                .env("DOTNET_TieredPGO", "0")
                .arg(dll)
                .args(args)
                .output()
                .unwrap()
        };
        for case in &cases {
            let out = dotnet_run(&runner_dll, &[&case.key]);
            kit::check_case(&mut failures, "csharp", &case.key, &out, case);
        }
        let mut run = |name: &str, dll: &Path, args: &[&str], expected: &[String]| {
            let out = dotnet_run(dll, args);
            kit::check_log(&mut failures, name, &out, expected);
        };
        run("Program.cs", &program, &[], &cases[0].expected);
        // Init logs as `module-state` does, then the threads tick.
        let expected = kit::threads_expected(&cases);
        run("module-state-threads", &runner_dll, &["threads"], &expected);
        // `tests/host/Interop.cs`, host code that uses `async-turns` the
        // standard way: it `await`s generated promises, resuming outside
        // any turn, also with a runner of its own, blocks on one inside a
        // turn, which fails clearly, and
        // loops over a generated generator with `foreach`, which a failed
        // step leaves done.
        if let Some(case) = cases.iter().find(|c| c.key == "async-turns") {
            let source = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/host/Interop.cs");
            let interop = bin.join("Interop.dll");
            let options = [
                "-target:exe".to_string(),
                "-nullable:enable".into(),
                "-warn:9999".into(),
                "-warnaserror+".into(),
                format!("-out:{}", interop.display()),
            ];
            let build = dotnet.csc(&options, &references, &[source]);
            assert!(
                build.status.success(),
                "csc, tests/host/Interop.cs:\n{}",
                String::from_utf8_lossy(&build.stdout)
            );
            fs::write(bin.join("Interop.runtimeconfig.json"), RUNTIME_CONFIG).unwrap();
            let expected: Vec<String> = (case.expected.iter().cloned())
                .chain(
                    [
                        "await 2, in a turn False",
                        "runner 6 True",
                        "in a turn: True",
                        "foreach 0,1,2",
                        "failed step",
                        "then done True",
                        "fibonacci 0,1,1,2,3,5,8,13",
                        "collatz 7,22,11,34,17,52,26,13,40,20,10,5,16,8,4,2,1",
                    ]
                    .map(String::from),
                )
                .collect();
            run("host interop", &interop, &[], &expected);
        }
        // Each library with host code, strictly, host code and all, and its
        // `Program.cs`, in a directory of its own.
        for case in &hosted {
            let dir = out.join("hosted").join(&case.key);
            let bin = dir.join("bin");
            fs::create_dir_all(&bin).unwrap();
            let mut sources = Vec::new();
            let mut program = None;
            for file in generate(case) {
                let path = dir.join(&file.path);
                fs::create_dir_all(path.parent().unwrap()).unwrap();
                fs::write(&path, &file.text).unwrap();
                if file.path.ends_with("/program/Program.cs") {
                    program = Some(path);
                } else if file.path.ends_with(".cs") && !file.path.contains("/tests/") {
                    sources.push(path);
                }
            }
            let library = bin.join(format!("{}.dll", names::root_namespace(&case.library)));
            let build = dotnet.csc(&strict(&library), &[&runtime], &sources);
            assert!(
                build.status.success(),
                "{}: csc, generated C# and host code:\n{}",
                case.key,
                String::from_utf8_lossy(&build.stdout)
            );
            let exe = bin.join("Program.dll");
            let options = [
                "-target:exe".to_string(),
                "-nullable:enable".into(),
                "-warnaserror+".into(),
                format!("-out:{}", exe.display()),
            ];
            let build = dotnet.csc(&options, &[&runtime, &library], &[program.unwrap()]);
            assert!(
                build.status.success(),
                "{}: csc, Program.cs:\n{}",
                case.key,
                String::from_utf8_lossy(&build.stdout)
            );
            fs::write(bin.join("Program.runtimeconfig.json"), RUNTIME_CONFIG).unwrap();
            for dll in dotnet.packages.iter().chain([&runtime]) {
                fs::copy(dll, bin.join(dll.file_name().unwrap())).unwrap();
            }
            run(&case.key, &exe, &[], &case.expected);
        }
        for case in &tested {
            run_tests(&mut failures, &dotnet, &runtime, &strict, &out, case);
        }
        kit::assert_none(&failures);
    });
}

/// MSTest's framework assembly in NuGet's cache, at the version Kotlin's
/// test projects and std's C# configuration name (3.1.1): what std's
/// `AssertFailedException` and the tests' attributes need.
fn mstest(dotnet: &Dotnet) -> Option<PathBuf> {
    let dll = (dotnet.packages.first()?.ancestors())
        .find(|d| d.ends_with("packages"))?
        .join("mstest.testframework/3.1.1/lib/net6.0")
        .join("Microsoft.VisualStudio.TestPlatform.TestFramework.dll");
    dll.is_file().then_some(dll)
}

/// Runs a library's MSTest methods by reflection, printing what
/// `kit::runner_failures` reads: each `[TestClass]`'s `[TestMethod]`s, in
/// order, a failure being the exception a method throws, as MSTest
/// reports it (Kotlin's `RunCSharp` reads `dotnet test`'s JUnit logger).
const RUN_TESTS: &str = r#"using System;
using System.Linq;
using System.Reflection;
using Microsoft.VisualStudio.TestTools.UnitTesting;

static class RunTests
{
    static void Main(string[] args)
    {
        Assembly assembly = Assembly.LoadFrom(args[0]);
        int ran = 0;
        foreach (Type type in assembly.GetTypes()
            .Where(t => t.GetCustomAttribute<TestClassAttribute>() != null)
            .OrderBy(t => t.FullName, StringComparer.Ordinal))
        {
            foreach (MethodInfo method in type.GetMethods()
                .Where(m => m.GetCustomAttribute<TestMethodAttribute>() != null)
                .OrderBy(m => m.MetadataToken))
            {
                ran++;
                try
                {
                    method.Invoke(Activator.CreateInstance(type), null);
                }
                catch (TargetInvocationException e)
                {
                    string message = e.InnerException?.Message ?? "";
                    Console.WriteLine(method.Name + "\t" + message.Replace("\n", "\\n"));
                }
            }
        }
        Console.WriteLine("ran " + ran);
    }
}
"#;

/// A run as tests, as Kotlin's `RunCSharp`: std, then the library, then
/// its tests against it, each compiled strictly by `csc` against MSTest's
/// framework
/// ([`mstest`]), its tests run by [`RUN_TESTS`], and their failures
/// compared with its `expectedFailures`.
fn run_tests(
    failures: &mut Vec<String>,
    dotnet: &Dotnet,
    runtime: &Path,
    strict: &dyn Fn(&Path) -> Vec<String>,
    out: &Path,
    case: &Case,
) {
    let Some(mstest) = mstest(dotnet) else {
        kit::missing(
            "MSTest.TestFramework 3.1.1 in NuGet's cache (as Kotlin's test projects name it)",
        );
        return;
    };
    let dir = out.join("tested").join(&case.key);
    let bin = dir.join("bin");
    fs::create_dir_all(&bin).unwrap();
    let write = |files: Vec<OutFile>| -> Vec<PathBuf> {
        let mut sources = Vec::new();
        for file in files {
            let path = dir.join(&file.path);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(&path, &file.text).unwrap();
            if file.path.ends_with(".cs") && !file.path.contains("/program/") {
                sources.push(path);
            }
        }
        sources
    };
    let std = translate(&kit::std_program(), &CSHARP).unwrap_or_else(|e| panic!("std: {e}"));
    let std_dll = bin.join("TemperLang.Std.dll");
    let options = std_options(strict, &std_dll, &std, &dir);
    let build = dotnet.csc(&options, &[runtime, &mstest], &write(std));
    if !build.status.success() {
        failures.push(format!(
            "{}: csc, std:\n{}",
            case.key,
            String::from_utf8_lossy(&build.stdout)
        ));
        return;
    }
    // The library, then its tests' project, against it, as MSBuild builds
    // them, so the library builds without what only its tests reach, and
    // they see its internals as its project's `InternalsVisibleTo` lets
    // them.
    let root = names::root_namespace(&case.library);
    let library = bin.join(format!("{root}.dll"));
    let tests = bin.join(format!("{root}Test.dll"));
    let (mut main, tested): (Vec<PathBuf>, Vec<PathBuf>) = (write(generate(case)).into_iter())
        .partition(|path| !path.starts_with(dir.join(&case.library).join("tests")));
    let visible = dir.join("InternalsVisibleTo.cs");
    fs::write(
        &visible,
        format!("[assembly: System.Runtime.CompilerServices.InternalsVisibleTo(\"{root}Test\")]\n"),
    )
    .unwrap();
    main.push(visible);
    let steps = [
        (
            "the library",
            &library,
            vec![runtime, mstest.as_path(), std_dll.as_path()],
            &main,
        ),
        (
            "its tests",
            &tests,
            vec![
                runtime,
                mstest.as_path(),
                std_dll.as_path(),
                library.as_path(),
            ],
            &tested,
        ),
    ];
    for (what, dll, refs, sources) in steps {
        let build = dotnet.csc(&strict(dll), &refs, sources);
        if !build.status.success() {
            failures.push(format!(
                "{}: csc, {what}:\n{}",
                case.key,
                String::from_utf8_lossy(&build.stdout)
            ));
            return;
        }
    }
    let runner = dir.join("RunTests.cs");
    fs::write(&runner, RUN_TESTS).unwrap();
    let exe = bin.join("RunTests.dll");
    let options = [
        "-target:exe".to_string(),
        "-nullable:enable".into(),
        format!("-out:{}", exe.display()),
    ];
    let build = dotnet.csc(&options, &[&mstest], &[runner]);
    assert!(
        build.status.success(),
        "csc, the test runner:\n{}",
        String::from_utf8_lossy(&build.stdout)
    );
    fs::write(bin.join("RunTests.runtimeconfig.json"), RUNTIME_CONFIG).unwrap();
    for dll in dotnet
        .packages
        .iter()
        .map(PathBuf::as_path)
        .chain([runtime, &mstest])
    {
        fs::copy(dll, bin.join(dll.file_name().unwrap())).unwrap();
    }
    let run = Command::new(&dotnet.dotnet)
        .env("DOTNET_CLI_TELEMETRY_OPTOUT", "1")
        .arg(&exe)
        .arg(&tests)
        .output()
        .unwrap();
    let actual = kit::runner_failures(&String::from_utf8_lossy(&run.stdout));
    kit::check_tests(failures, case, actual, &run, kit::test_name_words);
}
