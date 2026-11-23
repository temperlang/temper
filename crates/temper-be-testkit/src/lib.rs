//! What the backends' tests share: the programs they translate, how a
//! program becomes tmpl, goldens, the tools they run, and runtimes
//! cached between runs. Only the backends' tests use it, as a
//! dev-dependency; it knows no backend.
//!
//! - **Programs:** Kotlin Temper's functional tests that tmpl's
//!   translation covers ([`COVERED`]), from [`temper_funtests`]'s
//!   `cases/`, and the shared programs in this crate's `programs/`
//!   ([`PROGRAMS`]), which cover module state, operators, jumps, and
//!   string order beyond those tests, each a [`Case`] with the
//!   log a backend expects ([`cases`]). A backend adds programs of its
//!   own as more [`Case`]s.
//! - **To tmpl:** [`program`] settles a case's library and translates it,
//!   or reads it as tmpl, and validates it, then gives it the case's host
//!   files and whether it runs under `--no-connected` ([`CONNECTED`]).
//! - **Goldens:** [`Goldens`] compares output with the files in a
//!   backend's `tests/goldens/`, and with `UPDATE_SNAPSHOTS=1` writes new
//!   output there instead. Goldens show Temper's version as a sentinel,
//!   `0.0.0-alpha.0.0` in each ecosystem's spelling, but std's and a
//!   package file per backend ([`pins_version`]), so a release changes
//!   few of them.
//! - **Tools:** [`tool`] finds one named by an environment variable or on
//!   `PATH` ([`temper_tmpl::tools`], which backends' own test runners
//!   share, as they share its JUnit XML reading); a missing one skips its test with a message ([`missing`]),
//!   unless `TEMPER_REQUIRE_CHECKERS` is set, as in CI, when it fails.
//! - **Runtimes:** [`cached`] builds something once under
//!   `CARGO_TARGET_TMPDIR`, keyed by a hash of its inputs, and keeps it
//!   until they change.
//! - **Test server:** [`test_server_port`] starts, once per process, a
//!   local server answering as Kotlin's `TestWebServer`, for std `net`'s
//!   tests ([`NET`]), whose `testServerPort` is its port, any free one,
//!   as Kotlin's suite picks; goldens show it as [`TEST_SERVER_PORT`].
//! - **Runs:** [`check_log`] compares what a program logged with what's
//!   expected, its line ends as on Linux ([`printed`]), and
//!   [`check_case`] also that a run expected to fail does, and
//!   [`threads_expected`] is what `module-state` from 8 threads logs;
//!   for a test that runs as tests ([`TESTS`]), [`check_tests`] compares
//!   its failures, as a backend's runner reports them
//!   ([`junit_failures`] for JUnit XML), with its `expectedFailures`.

use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::{env, fs};
use temper_frontend::interp;
use temper_frontend::library::Library;
use temper_frontend::project::{load_imports, source_kind};
use temper_frontend::settle::settle;
use temper_frontend::sources::Sources;
use temper_frontend::to_tmpl::to_tmpl;
use temper_tmpl::host::{Host, HostFile};
use temper_tmpl::naming::{Interface, NamingRules, assign};
use temper_tmpl::{Profile, Program, text, tools, validate};

/// The functional tests tmpl's translation covers, by their paths.
pub const COVERED: [&str; 7] = [
    "algos/fibonacci",
    "algos/hello-world",
    "control-flow/if-return",
    "control-flow/loop-reenterable",
    "names/nonascii",
    "types/int/basics",
    "types/string/is-empty",
];

/// The functional tests with classes and interfaces that tmpl's
/// translation covers (subset B's first step): each backend runs them as
/// it covers classes, with [`cases_with`].
pub const CLASSES: [&str; 14] = [
    "algos/hello-from-class-to-top",
    "algos/hello-world-object",
    "classes/call-override-from-subtype",
    "classes/direct-getter",
    "classes/inherited-getter",
    "classes/object-literals/extra-class",
    "classes/private-method",
    "classes/setters",
    "classes/static-properties",
    "classes/static-properties-scope",
    "classes/write-back-through-field",
    "imports/types",
    "interfaces/empty",
    "interfaces/pure-virtual",
];

/// The functional tests of names: a module's own declarations of names
/// core has too (`String`, `List`), and `core` and `builtins` as
/// ordinary names, of a module and of variables, beside `import("core")`.
/// Each backend runs them with [`cases_with`]; Kotlin Temper has no
/// record of them, as it reserves `builtins`.
pub const NAMES: [&str; 2] = ["names/core-named", "names/core-shadows"];

/// The functional tests with nullable types, `orelse`, and `bubble()`
/// that tmpl's translation covers (subset B's third step): each backend
/// runs them as it covers nullables, with [`cases_with`].
pub const NULLABLES: [&str; 4] = [
    "control-flow/bubble",
    "semantics/mutually-referencing-types",
    "types/float/basics",
    "types/int/limits",
];

/// The functional tests with generic types and core's lists that tmpl's
/// translation covers (subset B's third step, generics), one with a
/// cast from a nullable type, and one importing a module as a namespace:
/// each backend runs them as it covers generics, with [`cases_with`].
pub const GENERICS: [&str; 4] = [
    "casts/specific",
    "imports/namespaces",
    "imports/values",
    "types/list/empty",
];

/// The functional tests with function values that tmpl's translation
/// covers: function literals with their captures, references to
/// functions, calls of function values, and core's members that take
/// functions, and local functions that use each other or themselves
/// (`functions/locals`). Each backend runs them as it covers function
/// values, with [`cases_with`].
pub const FUNCTIONS: [&str; 7] = [
    "control-flow/for-of-folded",
    "functions/as-values",
    "functions/constructor-callbacks",
    "functions/locals",
    "functions/simple-locals",
    "imports/functions",
    "types/list/sorting",
];

/// The functional tests with parameters' defaults that tmpl's translation
/// covers: optional parameters, typed nullable, whose functions' bodies
/// start with their defaults, and calls that leave them out. Each backend
/// runs them as it covers defaults, with [`cases_with`].
pub const DEFAULTS: [&str; 1] = ["classes/property-order"];

/// The functional tests with core's other types and members that tmpl's
/// translation covers: string indices and `StringIndexOption`,
/// `StringBuilder`, `Empty`, `Deque`, `DenseBitVector`, and more of
/// `String`'s and `Float64`'s members, as `split`, `countBetween`,
/// `near`, and `pi`. Each backend runs them as it covers them, with
/// [`cases_with`].
pub const CORE_TYPES: [&str; 9] = [
    "functions/defaulting",
    "functions/named-args",
    "regression/minimal-repro",
    "types/dense-bit-vector",
    "types/deque",
    "types/float/ops",
    "types/string/build",
    "types/string/indices",
    "types/string/read",
];

/// The functional tests of a library's own connected functions and
/// classes, with host code beside them for each backend: each backend
/// runs them as it reads host files, with [`cases_with`], which gives
/// each twice, as is and under `--no-connected` ([`Case::no_connected`]).
/// Their library is named `work`, as in Kotlin's functional tests, since
/// their host code names it (`package work;`).
pub const CONNECTED: [&str; 2] = ["functions/connected", "types/connected"];

/// The functional tests that divide by a constant that can't fail them,
/// tmpl's `divSafe` and `modSafe`, where nothing may bubble, and give a
/// builtin generic type's constructor a list literal its type arguments
/// type (`new Map<Int, Int>([])`). Each backend runs them, with
/// [`cases_with`].
pub const DIVISIONS: [&str; 3] = ["types/int/shifty", "types/list/reduce", "types/map"];

/// The functional tests with jumps out of labeled loops (`break outer`
/// and `continue outer`, the step of a `for` before each `continue`),
/// classes declared in functions, which tmpl declares at their module's
/// level, type aliases, explicit type arguments (`leastX<C>(a, b)`), and
/// `as` casts to a supertype, which are upcasts. Each backend runs them,
/// with [`cases_with`].
pub const LOOPS_AND_CLASSES: [&str; 4] = [
    "algos/myers-diff",
    "classes/object-literals",
    "control-flow/loops",
    "interfaces/property-members",
];

/// The functional tests that typing needed more of: locals declared
/// without a type or a value, typed from what's assigned to them first.
/// Each backend runs them, with [`cases_with`].
pub const TYPING: [&str; 1] = ["semantics/constness"];

/// The functional tests with builtin values as `AnyValue`s, which some
/// targets box (`casts/as-expr`), and a generic call's explicit type
/// arguments (`List.of<AnyValue>(…)`). Each backend runs them, with
/// [`cases_with`].
pub const ANY_VALUES: [&str; 2] = ["casts/as-expr", "types/list/operations"];

/// The functional tests with `is` and `as` checks from `AnyValue` for a
/// class, given core's values, as `Pair`s and `DenseBitVector`s, and a
/// class's without interfaces as `AnyValue`s. Each backend runs them with
/// [`cases_with`] but C++ and Rust, which can't make those values
/// `AnyValue`s yet.
pub const CORE_CHECKS: [&str; 1] = ["casts/core-classes"];

/// The functional tests of a library with errors, which translate with
/// tmpl's `Error`s, each failing where it runs: `semantics/broken`, whose
/// run fails ([`Case::fails`]), and `semantics/type-checked-locals`,
/// whose errors each fail inside an `orelse`. Each backend runs them,
/// with [`cases_with`].
pub const ERRORS: [&str; 2] = ["semantics/broken", "semantics/type-checked-locals"];

/// The functional tests with types as values (`Void` where a `Type` is
/// wanted), and generic methods, static and not, that call generic
/// functions. Each backend runs them, with [`cases_with`].
pub const TYPE_VALUES: [&str; 1] = ["classes/angle-call"];

/// The functional tests with generators: `async` blocks that `await`
/// promises, and a generator that `yield`s (`control-flow/actor-run`).
/// Each backend that runs them lists them, with [`cases_with`].
pub const ASYNC: [&str; 5] = [
    "control-flow/actor-run",
    "control-flow/async",
    "control-flow/async-broken-early",
    "control-flow/async-waiters",
    "control-flow/await-in-loop",
];

/// The functional tests that run as tests (`runAsTest = true`), with
/// std's `testing`: each backend builds std's output beside them, runs
/// them in its test runner, and compares the tests that fail with their
/// `expectedFailures` ([`check_tests`]).
pub const TESTS: [&str; 1] = ["testing/asserts"];

/// The functional tests that import std's modules (`std/regex`,
/// `std/json`, `std/temporal`), which translate as part of std: each
/// backend builds std's output beside them ([`Case::needs_std`]), with
/// [`cases_with`].
pub const WITH_STD: [&str; 4] = [
    "regex/match",
    "regex/zero-advance",
    "types/date",
    "types/json-syntax-tree",
];

/// The functional tests of std's `net`, which send requests to the test
/// server ([`test_server_port`]): each backend with a network connection
/// runs them, with [`cases_with`].
pub const NET: [&str; 2] = ["types/netresponse", "types/netresponse-not-found"];

/// For a backend without std `net`, whose `sendRequest` gives a broken
/// promise, as std lets a backend that can't send do: [`NET`]'s cases
/// expect what that logs, as Kotlin's suite expects them to fail there,
/// so support shows as a change.
pub fn without_net(cases: &mut [Case]) {
    for case in cases {
        match case.key.as_str() {
            "types-netresponse" => case.expected = vec!["failed".into()],
            "types-netresponse-not-found" => {
                case.expected = vec!["broken promise".into(), "no response from port 1".into()]
            }
            _ => {}
        }
    }
}

/// The shared programs, in this crate's `programs/`: Temper
/// (`NAME.temper.md`, with what it logs in log blocks, as the functional
/// tests have), or tmpl that the frontend doesn't make yet (`NAME.tmpl`,
/// with the lines it logs in `// log:` comments).
///
/// A program in a directory of its own (`NAME/NAME.temper.md`) is a
/// library of several modules, its other modules in subdirectories, which
/// its main file imports; such libraries may import each other by name.
/// `modules` is modules importing each other, one of them the main one, and
/// `imports-library` imports `modules`, and `renamed`, whose exports'
/// names backends' namings change. `testing` has tests, which need
/// std's `testing` to run, so backends check only their form for now.
/// `classes` has classes and interfaces, beyond what [`CLASSES`] covers.
/// `nullables` has nullable types, null checks, `??`, and `orelse` with
/// `bubble()` and `panic()`; `generics` generic functions, classes, and
/// interfaces, and core's lists, pairs, and maps; `functions` function
/// values: a closure that assigns what it captures, `@fun` interfaces
/// passed and returned, references to functions and static methods,
/// core's `map`, `filter`, `join`, `sorted`, and `forEach`, a closure in
/// a field, and closures made in a loop, in a function and in a
/// generator stepped past its end; `defaults` parameters' defaults, left out and given, on
/// functions, methods, and constructors, and called from another module
/// (two modules, so in a directory of its own); `async-turns` `async`
/// blocks that share a counter, which run one turn at a time, and a
/// `complete` that returns before its waiter resumes; `libraries` what
/// real libraries use that the others don't; `errors` code with errors
/// (type errors, undeclared names and types, misspelled members, a
/// missing bracket), whose errors each fail where they run and whose
/// good parts run as written; `triple` the architecture docs' running
/// example; `content-tags` tagged strings whose contexts are worked out
/// at compile time, so their escapers are called directly, in branches
/// and loops, and one left to run time; `reach` what only tests reach,
/// and what nothing does, which runs as tests ([`AS_TESTS`]);
/// `core-numbers` core's number members where hosts differ, `succ`,
/// `pred`, `signum`, `Float64.near` on NaN and infinities, and
/// `reduceFromIndex`; `callback-changes` builders added to and spliced
/// into themselves, and callbacks that change the builder or map they
/// walk, which must stay memory safe and never deadlock, ending with a
/// sort whose comparison grows its builder, which panics, so the run
/// fails (`expectRunFailure = true`, as functional tests say it), with
/// the message its ` ```panic ` block gives ([`check_case`]).
pub const PROGRAMS: [&str; 22] = [
    "classes",
    "nullables",
    "generics",
    "functions",
    "defaults",
    "module-state",
    "operators",
    "string-order",
    "jumps.tmpl",
    "modules",
    "imports-library",
    "testing",
    "renamed",
    "async-turns",
    "libraries",
    "errors",
    "triple",
    "content-tags",
    "json-adapters",
    "reach",
    "core-numbers",
    "callback-changes",
];

/// The shared programs that run as tests, as a functional test with
/// `runAsTest = true` does, none expected to fail: their tests check
/// what a log can't, as the library's state their init set.
pub const AS_TESTS: [&str; 1] = ["reach"];

/// What only the shared `reach` program's tests reach, by their Temper
/// names, each of which a backend spells its own way.
pub const REACH_TESTS_ONLY: [&str; 6] = [
    "forTests",
    "testFactor",
    "testCount",
    "TestShape",
    "TestSquare",
    "addArea",
];

/// What nothing in the shared `reach` program reaches.
pub const REACH_UNREACHED: [&str; 4] = [
    "unreachedFunction",
    "UnreachedClass",
    "UnreachedInterface",
    "unreachedValue",
];

/// Whether `line` mentions `name` as a whole name, not inside a longer
/// one, though a private name's leading `_` may come before it.
pub fn mentions(line: &str, name: &str) -> bool {
    let word = |c: char| c == '_' || c.is_ascii_alphanumeric();
    line.match_indices(name).any(|(at, _)| {
        let before = line[..at].trim_end_matches('_').chars().next_back();
        let after = line[at + name.len()..].chars().next();
        !before.is_some_and(word) && !after.is_some_and(word)
    })
}

/// Checks a backend's output of the shared `reach` program, each line
/// with its file's path and whether only tests have it (a tests file's,
/// or one a build without tests leaves out): that every name in
/// `tests_only` (as [`REACH_TESTS_ONLY`], spelled as the backend names
/// them) shows up, and only where tests are, and no name in `unreached`
/// ([`REACH_UNREACHED`]'s) anywhere.
pub fn check_test_parts_apart<'a>(
    lines: impl IntoIterator<Item = (&'a str, bool, &'a str)>,
    tests_only: &[&str],
    unreached: &[&str],
) {
    let mut seen = Vec::new();
    let mut misplaced = Vec::new();
    for (path, tests, line) in lines {
        for &name in tests_only {
            if mentions(line, name) {
                seen.push(name);
                if !tests {
                    misplaced.push(format!("{path}: {name}, only for tests: {line}"));
                }
            }
        }
        for &name in unreached {
            if mentions(line, name) {
                misplaced.push(format!("{path}: {name}, which nothing reaches: {line}"));
            }
        }
    }
    for &name in tests_only {
        if !seen.contains(&name) {
            misplaced.push(format!("{name}, only for tests, is missing"));
        }
    }
    assert!(misplaced.is_empty(), "{}", misplaced.join("\n"));
}

/// The shared programs that are libraries of several modules, in
/// directories of their own: what [`Case::libraries`] lists.
pub const LIBRARIES: [&str; 4] = ["modules", "imports-library", "defaults", "renamed"];

/// The shared programs' directory.
pub fn programs_dir() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("programs")
}

/// A program to translate: its library's name, its sources and their
/// directory, and the log it expects.
#[derive(Clone, Debug)]
pub struct Case {
    /// Its golden's name: the test's path with `-` for `/`.
    pub key: String,
    pub library: String,
    /// Each source's path and text.
    pub sources: Vec<(String, String)>,
    /// The sources' directory, if they're files.
    pub dir: PathBuf,
    pub expected: Vec<String>,
    /// The other libraries it may import by name, each with its root.
    pub libraries: Vec<(String, PathBuf)>,
    /// Its host files, each a path relative to `dir` and its text.
    pub host_files: Vec<(String, String)>,
    /// Whether it runs under `--no-connected`, using Temper
    /// implementations rather than host code wherever there are any; its
    /// key then ends with `-no-connected`.
    pub no_connected: bool,
    /// Whether it runs as tests (`runAsTest = true`), not as a program:
    /// then the tests it expects to fail, each its name and text its
    /// failure's cause contains ([`check_tests`]).
    pub tests: Option<Vec<(String, String)>>,
    /// Whether its run fails (`expectRunFailure = true`), after logging
    /// what's expected ([`check_case`]).
    pub fails: bool,
    /// For a shared program whose run must end in a panic, the panic's
    /// message, as its ` ```panic ` block gives it ([`check_case`]).
    pub panic: Option<String>,
}

impl Case {
    /// Whether it needs std's output beside it: it runs as tests, with
    /// std's `testing`, or imports a std module, or a shared library it
    /// imports does.
    pub fn needs_std(&self) -> bool {
        self.tests.is_some() || self.imports_std()
    }

    /// Whether any of its modules imports a std module: its source's, and
    /// for a library of several, as `modules`, its other modules', and
    /// those of the shared libraries it imports.
    fn imports_std(&self) -> bool {
        let std = |text: &str| text.contains("import(\"std/");
        if self.sources.iter().any(|(_, text)| std(text)) {
            return true;
        }
        // Only a library of several modules has shared libraries beside it.
        if self.libraries.is_empty() {
            return false;
        }
        let mut files = Vec::new();
        files_with(&self.dir, "temper", &mut files);
        files_with(&self.dir, "md", &mut files);
        (files.iter()).any(|f| fs::read_to_string(f).is_ok_and(|t| std(&t)))
            || libraries(self, "").iter().any(Case::imports_std)
    }

    /// A library of one source given as text, not a file, its name the
    /// key: `NAME.temper.md`, or `NAME.tmpl` read as tmpl.
    pub fn inline(name: &str, file: &str, text: impl Into<String>, expected: Vec<String>) -> Case {
        Case {
            key: name.to_string(),
            library: name.to_string(),
            sources: vec![(file.to_string(), text.into())],
            dir: PathBuf::new(),
            expected,
            libraries: Vec::new(),
            host_files: Vec::new(),
            no_connected: false,
            tests: None,
            fails: false,
            panic: None,
        }
    }
}

/// The functional tests that tmpl covers, then those of the shared
/// programs named in `programs` (as [`PROGRAMS`] names them), each with
/// the log it expects of `backend` (`py`, `rust`, …).
pub fn cases(backend: &str, programs: &[&str]) -> Vec<Case> {
    cases_with(backend, programs, &[])
}

/// The functional tests that backends' other runs read, besides
/// [`COVERED`]'s, which `TEMPER_CASES` keeps.
const READ_ELSEWHERE: [&str; 4] = [
    "classes/static-properties",
    "functions/connected",
    "imports/values",
    "types/connected",
];

/// [`cases`], with more functional tests: `tests`, as [`CLASSES`].
pub fn cases_with(backend: &str, programs: &[&str], tests: &[&str]) -> Vec<Case> {
    let mut cases = functional_cases(backend, tests);
    // `TEMPER_CASES=casts-as-expr,locals` runs only the functional tests
    // whose keys contain one of these, to work on a few; the shared
    // programs stay, and [`COVERED`] and [`READ_ELSEWHERE`], as other runs
    // read them.
    if let Ok(only) = env::var("TEMPER_CASES") {
        let only: Vec<&str> = only.split(',').filter(|s| !s.is_empty()).collect();
        let only = if only.is_empty() { vec![""] } else { only };
        let kept = |key: &str| {
            let path = |p: &&str| p.replace('/', "-") == key;
            COVERED.iter().chain(&READ_ELSEWHERE).any(path)
        };
        cases.retain(|c| kept(&c.key) || only.iter().any(|o| c.key.contains(o)));
    }
    cases.extend(cases_of(backend, programs));
    cases
}

/// [`cases_with`] whatever `TEMPER_CASES` keeps: each case's key and
/// library, for what must not depend on which run, as names a backend
/// gives libraries of one name (C++'s).
pub fn all_libraries(programs: &[&str], tests: &[&str]) -> Vec<(String, String)> {
    let mut cases = functional_cases("", tests);
    cases.extend(cases_of("", programs));
    cases.into_iter().map(|c| (c.key, c.library)).collect()
}

/// [`COVERED`]'s functional tests and `tests`, as [`cases_with`] gives
/// them, before `TEMPER_CASES` filters them.
pub fn functional_cases(backend: &str, tests: &[&str]) -> Vec<Case> {
    let mut cases = Vec::new();
    let suite = temper_funtests::suite_dir();
    for &path in COVERED.iter().chain(tests) {
        let test = temper_funtests::test(path).unwrap_or_else(|| panic!("no test {path}"));
        let host_files: Vec<(String, String)> = (test.host_files().into_iter())
            .map(|s| (s.path, s.text))
            .collect();
        let case = Case {
            key: path.replace('/', "-"),
            library: if host_files.is_empty() {
                test.name().to_string()
            } else {
                "work".to_string()
            },
            sources: (test.sources().into_iter())
                .map(|s| (s.path, s.text))
                .collect(),
            dir: suite.join(path),
            expected: test.expected_log(backend),
            libraries: Vec::new(),
            host_files,
            no_connected: false,
            tests: (test.runs_as_test()).then(|| test.expected_failures()),
            fails: test.expects_run_failure(),
            panic: None,
        };
        if !case.host_files.is_empty() {
            cases.push(case.clone());
            cases.push(Case {
                key: format!("{}-no-connected", case.key),
                expected: test.expected_log_no_connected(backend),
                no_connected: true,
                ..case
            });
        } else {
            cases.push(case);
        }
    }
    cases
}

/// The shared programs named in `programs`, as [`cases`] gives them.
fn cases_of(backend: &str, programs: &[&str]) -> Vec<Case> {
    let mut cases = Vec::new();
    let dir = programs_dir();
    for &file in programs {
        assert!(PROGRAMS.contains(&file), "no shared program {file}");
        let in_dir = LIBRARIES.contains(&file);
        let (name, path) = match file.strip_suffix(".tmpl") {
            Some(name) => (name, dir.join(file)),
            None if in_dir => (file, dir.join(file).join(format!("{file}.temper.md"))),
            None => (file, dir.join(format!("{file}.temper.md"))),
        };
        let text = fs::read_to_string(&path).unwrap();
        let fails = text.contains("expectRunFailure = true");
        let panic = panic_message(&text);
        let expected = if file.ends_with(".tmpl") {
            (text.lines())
                .filter_map(|l| l.strip_prefix("// log: "))
                .map(str::to_string)
                .collect()
        } else {
            temper_funtests::expected_log(&text, backend)
        };
        cases.push(Case {
            key: name.to_string(),
            library: name.to_string(),
            expected,
            sources: vec![(path.to_string_lossy().into_owned(), text)],
            dir: if in_dir { dir.join(file) } else { dir.clone() },
            libraries: if in_dir {
                (LIBRARIES.iter())
                    .filter(|&&l| l != file)
                    .map(|&l| (l.to_string(), dir.join(l)))
                    .collect()
            } else {
                Vec::new()
            },
            host_files: Vec::new(),
            no_connected: false,
            tests: AS_TESTS.contains(&file).then(Vec::new),
            fails,
            panic,
        });
    }
    cases
}

/// A case's library, settled and translated to tmpl, or read as tmpl, and
/// validated.
pub fn program(case: &Case) -> Program {
    let key = &case.key;
    if let [(path, text)] = &case.sources[..] {
        if path.ends_with(".tmpl") {
            let program = text::read(text).unwrap_or_else(|e| panic!("{key}: {e}"));
            let problems = validate(&program, &Profile::full());
            assert!(problems.is_empty(), "{key}: {problems:#?}");
            return program;
        }
    }
    let settled = settle(load(case));
    // Shown only if the test fails: what the frontend loaded and said,
    // for failures on a platform you can't run locally.
    if !settled.library.diagnostics.is_empty() {
        let dirs: Vec<&str> = (settled.library.modules.iter())
            .map(|m| m.dir.as_str())
            .collect();
        eprintln!("{key}: modules {dirs:?}");
        for d in settled.library.diagnostics.iter().take(5) {
            eprintln!("{key}: {d:?}");
        }
    }
    let mut program = to_tmpl(&settled, &case.library).unwrap_or_else(|e| panic!("{key}: {e}"));
    let problems = validate(&program, &Profile::full());
    assert!(problems.is_empty(), "{key}: {problems:#?}");
    program.host = Host {
        files: (case.host_files.iter())
            .map(|(path, text)| HostFile {
                path: path.clone(),
                text: text.clone(),
            })
            .collect(),
        no_connected: case.no_connected,
    };
    program
}

/// A case's program for a backend whose naming follows `rules`, given
/// its dependencies' interfaces: each shared library it imports, built
/// as its own program, and named by the same rules
/// (notes/tmpl/open-questions.md, 46 and 47).
pub fn program_for(case: &Case, rules: &NamingRules) -> Program {
    let mut program = program(case);
    program.interfaces = interfaces(case, rules);
    program
}

/// std's modules that build as a library of its own
/// ([`temper_frontend::stdlib::program`]), translated once, and validated.
pub fn std_program() -> Program {
    static STD: std::sync::OnceLock<Program> = std::sync::OnceLock::new();
    STD.get_or_init(|| {
        let program = temper_frontend::stdlib::program(&Sources::standard())
            .unwrap_or_else(|e| panic!("std: {e}"));
        let problems = validate(&program, &Profile::full());
        assert!(problems.is_empty(), "std: {problems:#?}");
        program
    })
    .clone()
}

/// The interfaces of std and of the shared libraries a case imports, and
/// of those they import, for a backend whose naming follows `rules`:
/// std's from its own naming, as any library's
/// (notes/tmpl/open-questions.md, 46).
pub fn interfaces(case: &Case, rules: &NamingRules) -> Vec<Interface> {
    let std = std_program();
    let mut out: Vec<Interface> = vec![Interface::of(&std, &assign(&std, rules))];
    for dependency in libraries(case, "") {
        let dependency = program_for(&dependency, rules);
        let names = assign(&dependency, rules);
        for i in (dependency.interfaces.iter().cloned()).chain([Interface::of(&dependency, &names)])
        {
            if !out.iter().any(|o| o.library == i.library) {
                out.push(i);
            }
        }
    }
    out
}

/// The shared programs that are libraries `case` may import, as cases for
/// `backend`, so a backend's run can build them beside it.
pub fn libraries(case: &Case, backend: &str) -> Vec<Case> {
    let names: Vec<&str> = (case.libraries.iter())
        .map(|(name, _)| name.as_str())
        .filter(|name| LIBRARIES.contains(name))
        .filter(|name| {
            let import = format!("import(\"{name}");
            case.sources.iter().any(|(_, text)| text.contains(&import))
        })
        .collect();
    cases_of(backend, &names)
}

/// Whether a case's program has type-only errors (`errors`,
/// `semantics/type-checked-locals`), so a dynamically typed backend's
/// code for it runs ill-typed code as written, which strict type
/// checkers (mypy, pyright, tsc, LuaLS) would reject, as they would
/// Kotlin's.
pub fn ill_typed(case: &Case) -> bool {
    temper_tmpl::errors::has_type_errors(&program(case))
}

/// What a case's Temper sources log on tempest's interpreter, as its
/// failure, if any, and its output.
pub fn interpret(case: &Case) -> (Option<String>, Vec<String>) {
    let library = load(case);
    let run = interp::run(&settle(library), Some(interp::FUEL));
    (run.failure, run.output)
}

/// A case's library, with the modules it imports, and the other
/// libraries it may import by name.
fn load(case: &Case) -> Library {
    use temper_frontend::project::slashed;
    let mut library = Library::new();
    library.libraries = (case.libraries.iter())
        .map(|(name, root)| (name.clone(), slashed(&root.to_string_lossy())))
        .collect();
    // Paths with `/` on Windows too, as the CLI gives the frontend them.
    for (path, text) in &case.sources {
        let path = slashed(path);
        library.add_file(&path, text, source_kind(&path));
    }
    if (case.sources.iter()).any(|(_, text)| text.contains("testServerPort")) {
        library.test_server_port = Some(test_server_port().into());
    }
    load_imports(&Sources::standard(), &mut library);
    // A shared library ([`LIBRARIES`]) has every module in its directory,
    // as the CLI loads it, those that nothing imports too, after the
    // others.
    if LIBRARIES.iter().any(|l| case.dir == programs_dir().join(l)) {
        let mut dirs = vec![case.dir.clone()];
        while let Some(dir) = dirs.pop() {
            let mut entries: Vec<PathBuf> = (fs::read_dir(&dir).unwrap().flatten())
                .map(|e| e.path())
                .collect();
            entries.sort();
            let name = slashed(&dir.to_string_lossy());
            let loaded = (library.modules.iter()).any(|m| m.dir == name);
            for path in entries {
                let file = slashed(&path.to_string_lossy());
                if path.is_dir() {
                    dirs.push(path);
                } else if !loaded && temper_frontend::project::is_source(&file) {
                    let text = fs::read_to_string(&path).unwrap();
                    library.add_file(&file, &text, source_kind(&file));
                }
            }
        }
        load_imports(&Sources::standard(), &mut library);
    }
    library
}

/// The port goldens show for the test server's: generated code has
/// `testServerPort` as a constant, and the server takes any free port,
/// as Kotlin's `TestWebServer` does, so [`Goldens`] reads that one as
/// this, keeping goldens stable.
pub const TEST_SERVER_PORT: u16 = 28461;

static SERVER_PORT: std::sync::OnceLock<u16> = std::sync::OnceLock::new();

/// The port of this process's test server, which it starts the first
/// time, on any free port. It answers as Kotlin
/// Temper's `TestWebServer`: for any path, 200 and `{"method": "POST",
/// "json": true}` as JSON, saying the request's method and whether its
/// `Content-Type`'s media type was JSON; for a path starting `/missing`,
/// 404 and `not here` as text. Local only, on 127.0.0.1.
pub fn test_server_port() -> u16 {
    *SERVER_PORT.get_or_init(|| {
        use std::net::TcpListener;
        let listener = TcpListener::bind(("127.0.0.1", 0)).expect("a port for the test server");
        let port = listener.local_addr().unwrap().port();
        std::thread::spawn(move || {
            for stream in listener.incoming().flatten() {
                std::thread::spawn(move || {
                    let _ = answer(stream);
                });
            }
        });
        port
    })
}

/// Answers one request as [`test_server_port`]'s server does, then closes
/// the connection.
fn answer(stream: std::net::TcpStream) -> std::io::Result<()> {
    use std::io::{BufRead, BufReader, Read, Write};
    stream.set_read_timeout(Some(std::time::Duration::from_secs(30)))?;
    let mut reader = BufReader::new(stream);
    let mut lines = Vec::new();
    loop {
        let mut line = String::new();
        if reader.read_line(&mut line)? == 0 {
            break;
        }
        match line.trim_end() {
            "" => break,
            line => lines.push(line.to_string()),
        }
    }
    let header = |name: &str| {
        lines.iter().skip(1).find_map(|l| {
            let (key, value) = l.split_once(':')?;
            key.trim()
                .eq_ignore_ascii_case(name)
                .then(|| value.trim().to_string())
        })
    };
    let length = header("content-length").and_then(|n| n.parse().ok());
    let mut body = vec![0; length.unwrap_or(0)];
    reader.read_exact(&mut body)?;
    let request = lines.first().map_or("", String::as_str);
    let mut parts = request.split(' ');
    let (method, path) = (parts.next().unwrap_or(""), parts.next().unwrap_or("/"));
    let (status, kind, text) = if path.starts_with("/missing") {
        ("404 Not Found", "text/plain", "not here".to_string())
    } else {
        // Only the media type counts, as a client may add a charset.
        let kind = header("content-type").unwrap_or_default();
        let json = kind.split(';').next().unwrap_or("").trim() == "application/json";
        let text = format!("{{\"method\": \"{method}\", \"json\": {json}}}");
        ("200 OK", "application/json", text)
    };
    let response = format!(
        "HTTP/1.1 {status}\r\nContent-Type: {kind}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{text}",
        text.len()
    );
    let mut stream = reader.into_inner();
    stream.write_all(response.as_bytes())?;
    stream.flush()
}

/// Text with the test server's port as [`TEST_SERVER_PORT`]: each whole
/// number that's the port, replaced.
pub fn as_fixed_port(text: &str) -> std::borrow::Cow<'_, str> {
    let Some(&port) = SERVER_PORT.get().filter(|&&p| p != TEST_SERVER_PORT) else {
        return text.into();
    };
    let port = port.to_string();
    let mut out = String::new();
    let mut rest = text;
    while let Some(at) = rest.find(&port) {
        let end = at + port.len();
        let digit = |c: Option<char>| c.is_some_and(|c| c.is_ascii_digit());
        let whole = !digit(rest[..at].chars().next_back()) && !digit(rest[end..].chars().next());
        out.push_str(&rest[..at]);
        out.push_str(if whole { "" } else { &port });
        if whole {
            out.push_str(&TEST_SERVER_PORT.to_string());
        }
        rest = &rest[end..];
    }
    out.push_str(rest);
    out.into()
}

/// Runs a test on a thread with room for the settle loop's recursion.
pub fn with_stack(test: impl FnOnce() + Send + 'static) {
    std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(test)
        .unwrap()
        .join()
        .unwrap();
}

/// The goldens, beside std's, that show Temper's version as it is rather
/// than as the sentinel, so each backend's spellings of it in a package
/// file stay pinned: the `modules` program's, which depends on core and
/// std. C++'s and C#'s output names no version (it refers to the runtime
/// by path); Python's is pinned by std's `std.pyproject.toml`.
pub const VERSION_PINNED: [&str; 4] = [
    "modules.Cargo.toml",
    "modules.modules-dev-1.rockspec",
    "modules.package.json",
    "modules.pom.xml",
];

/// Whether a golden shows Temper's version as it is: std's (`std.…`),
/// which change with each release anyway, and [`VERSION_PINNED`]; the
/// rest show the sentinel version in its place
/// ([`temper_tmpl::version::sentinel`]: `0.0.0-alpha.0.0`, Python's
/// `0.0.0.dev0`, a rockspec's `0.0.0.alpha.0.0-1`), which each file's
/// ecosystem takes, so goldens stay valid in their language.
pub fn pins_version(name: &str) -> bool {
    name.starts_with("std.") || VERSION_PINNED.contains(&name)
}

/// Goldens in a directory: output compared with them, or with
/// `UPDATE_SNAPSHOTS=1`, written over them.
pub struct Goldens {
    dir: PathBuf,
    update: bool,
    differ: Vec<String>,
}

impl Goldens {
    /// The goldens in a crate's `tests/goldens/`.
    pub fn new(manifest_dir: &str) -> Goldens {
        Goldens {
            dir: PathBuf::from(manifest_dir).join("tests/goldens"),
            update: env::var_os("UPDATE_SNAPSHOTS").is_some(),
            differ: Vec::new(),
        }
    }

    /// A golden's path, by its file name.
    pub fn path(&self, name: &str) -> PathBuf {
        self.dir.join(name)
    }

    /// Compares output with the golden of this file name, or updates it.
    /// In the goldens of [`NET`]'s tests, the test server's port reads as
    /// [`TEST_SERVER_PORT`] ([`as_fixed_port`]); only there, so a number
    /// elsewhere that happens to be the port stays. Temper's version
    /// reads as the sentinel `0.0.0-alpha.0.0`, in each spelling
    /// ([`temper_tmpl::version::current_as_sentinel`]),
    /// but in the goldens that pin it ([`pins_version`]), so a release
    /// doesn't change every golden; a golden named in
    /// [`VERSION_PINNED`] must name it.
    pub fn check(&mut self, name: &str, actual: &str) {
        let net = NET.iter().any(|t| name.starts_with(&t.replace('/', "-")));
        let actual = match net {
            true => as_fixed_port(actual),
            false => actual.into(),
        };
        let actual = match pins_version(name) {
            true => actual.into_owned(),
            false => temper_tmpl::version::current_as_sentinel(&actual),
        };
        if VERSION_PINNED.contains(&name) {
            let version = temper_tmpl::version::current();
            if temper_tmpl::version::as_sentinel(&actual, version) == actual {
                self.differ
                    .push(format!("{name} pins the version but names none:\n{actual}"));
            }
        }
        let actual = &*actual;
        let golden = self.path(name);
        let expected = fs::read_to_string(&golden).unwrap_or_default();
        if actual != expected {
            if self.update {
                fs::create_dir_all(&self.dir).unwrap();
                fs::write(&golden, actual).unwrap();
            } else {
                self.differ.push(format!("{}:\n{actual}", golden.display()));
            }
        }
    }

    /// Notes a difference found otherwise, such as a file not laid out as
    /// its formatter would.
    pub fn differs(&mut self, what: String) {
        self.differ.push(what);
    }

    /// Fails if any golden differs.
    pub fn finish(self) {
        assert!(
            self.differ.is_empty(),
            "goldens differ (UPDATE_SNAPSHOTS=1 accepts new output):\n{}",
            self.differ.join("\n")
        );
    }
}

/// Whether checkers are required (`TEMPER_REQUIRE_CHECKERS`, as in CI),
/// so a missing one fails rather than skips.
pub fn required() -> bool {
    env::var_os("TEMPER_REQUIRE_CHECKERS").is_some()
}

/// Something a test needs is missing: skipped, with a message, unless
/// checkers are required, when it fails; but for what
/// `TEMPER_ALLOW_MISSING` names (comma-separated, each found in `what`,
/// as `LuaLS`), which CI sets where a tool can't be set up on a platform.
pub fn missing(what: &str) {
    let allowed = env::var("TEMPER_ALLOW_MISSING").is_ok_and(|names| {
        (names.split(',').map(str::trim)).any(|name| !name.is_empty() && what.contains(name))
    });
    assert!(
        !required() || allowed,
        "{what} (needed with TEMPER_REQUIRE_CHECKERS)"
    );
    match allowed {
        true => eprintln!("skipped: {what} (allowed by TEMPER_ALLOW_MISSING)"),
        false => eprintln!("skipped: {what}"),
    }
}

/// A tool named by an environment variable, or found on `PATH` by
/// `name`, if it runs with `arg` (such as `--version`), with what that
/// printed.
pub fn tool_with(var: &str, name: &str, arg: &str) -> Option<(String, String)> {
    let candidate = tools::tool(var, name);
    let out = Command::new(&candidate).arg(arg).output().ok()?;
    let version = [out.stdout, out.stderr].concat();
    out.status
        .success()
        .then(|| (candidate, String::from_utf8_lossy(&version).into_owned()))
}

/// A tool named by an environment variable, or found on `PATH`, if it
/// runs with `--version`.
pub fn tool(var: &str, name: &str) -> Option<String> {
    tool_with(var, name, "--version").map(|(t, _)| t)
}

/// Writes generated files, each a path and its text, under a directory.
pub fn write_files<'a>(root: &Path, files: impl IntoIterator<Item = (&'a str, &'a str)>) {
    for (path, text) in files {
        let path = root.join(path);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(&path, text).unwrap();
    }
}

/// Writes a file only if its text changed, so incremental builds stay
/// incremental.
pub fn write_if_changed(path: &Path, text: &str) {
    if fs::read_to_string(path).ok().as_deref() == Some(text) {
        return;
    }
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(path, text).unwrap();
}

/// Copies a directory's files, leaving out directories named in `skip`
/// (such as `target`), and leaving files that haven't changed alone.
pub fn copy_dir(from: &Path, to: &Path, skip: &[&str]) {
    fs::create_dir_all(to).unwrap();
    for entry in fs::read_dir(from).unwrap() {
        let entry = entry.unwrap();
        let path = entry.path();
        let name = entry.file_name();
        if entry.file_type().unwrap().is_dir() {
            if !skip.iter().any(|s| name == **s) {
                copy_dir(&path, &to.join(&name), skip);
            }
        } else {
            let target = to.join(&name);
            let bytes = fs::read(&path).unwrap();
            if fs::read(&target).ok().as_deref() != Some(&bytes[..]) {
                fs::write(&target, bytes).unwrap();
            }
        }
    }
}

pub use tools::files_with;

/// Something built once, in `root`, and kept until its inputs change:
/// `key` (a tool's version and options, say) and the paths and contents
/// of `sources`. If the stamp in `root` matches and `output` is there,
/// does nothing; else empties `root` and calls `build`, then stamps it.
pub fn cached(
    root: &Path,
    key: impl Hash,
    sources: &[PathBuf],
    output: &Path,
    build: impl FnOnce(),
) {
    let mut hasher = DefaultHasher::new();
    key.hash(&mut hasher);
    for source in sources {
        source.hash(&mut hasher);
        fs::read(source).unwrap().hash(&mut hasher);
    }
    let stamp = format!("{:016x}\n", hasher.finish());
    let stamp_path = root.join("stamp");
    if fs::read_to_string(&stamp_path).ok().as_deref() == Some(&stamp) && output.exists() {
        return;
    }
    let _ = fs::remove_dir_all(root);
    fs::create_dir_all(root).unwrap();
    build();
    fs::write(stamp_path, stamp).unwrap();
}

/// What a program printed, as text: on Windows, with the `\r\n` that a
/// text-mode stream (Lua's, Python's, Java's `println`) writes for `\n`
/// as `\n`, so its lines compare as they do elsewhere; a `\r` it prints
/// itself, but for one just before a newline, is kept.
pub fn printed(bytes: &[u8]) -> String {
    let text = String::from_utf8_lossy(bytes);
    match cfg!(windows) {
        true => text.replace("\r\n", "\n"),
        false => text.into_owned(),
    }
}

/// Compares what a program logged with what's expected, adding a failure
/// if it differs or the program failed.
pub fn check_log(failures: &mut Vec<String>, name: &str, out: &Output, expected: &[String]) {
    let stdout = printed(&out.stdout);
    let actual: Vec<&str> = stdout.lines().collect();
    if !out.status.success() || actual != expected {
        failures.push(format!(
            "{name}: expected {expected:?}, got {actual:?}\n{}",
            String::from_utf8_lossy(&out.stderr)
        ));
    }
}

/// The message a shared program's run must panic with, if it must: the
/// line of its ` ```panic ` block, which a backend's panic shows
/// ([`check_case`]).
fn panic_message(text: &str) -> Option<String> {
    let mut lines = text.lines().map(str::trim);
    lines.find(|&l| l == "```panic")?;
    lines.next().map(str::to_string)
}

/// [`check_log`] for a case's run on `backend`: one that's expected to
/// fail ([`Case::fails`]) must fail, after logging what's expected, and
/// not by crashing ([`crashed`]) or a sanitizer's report
/// (AddressSanitizer's, say), which is a bug rather than the failure the
/// case means. One that must panic ([`Case::panic`]) must end as the
/// backend's panic does ([`panicked`]), not by a host exception.
pub fn check_case(
    failures: &mut Vec<String>,
    backend: &str,
    name: &str,
    out: &Output,
    case: &Case,
) {
    if !case.fails {
        return check_log(failures, name, out, &case.expected);
    }
    let stdout = printed(&out.stdout);
    let actual: Vec<&str> = stdout.lines().collect();
    let stderr = String::from_utf8_lossy(&out.stderr);
    let sanitized = stderr.contains("Sanitizer");
    let wrong_end = match &case.panic {
        Some(message) => !panicked(backend, out, message),
        None => crashed(&out.status),
    };
    if out.status.success() || actual != case.expected || sanitized || wrong_end {
        let ending = match &case.panic {
            Some(message) => format!("{backend}'s panic \"{message}\""),
            None => "a failed run".into(),
        };
        failures.push(format!(
            "{name}: expected {:?} and {ending}, got {actual:?} ({})\n{stderr}",
            case.expected, out.status,
        ));
    }
}

/// Whether a run ended as `backend`'s `panic()`, or its runtime's, ends
/// one, with `message` on stderr: how its exit looks, and the line that
/// names what ended it. Not a host exception (Python's `IndexError`,
/// JavaScript's `TypeError`, Java's other exceptions), a crash, or a
/// sanitizer's report.
pub fn panicked(backend: &str, out: &Output, message: &str) -> bool {
    let stderr = String::from_utf8_lossy(&out.stderr);
    let code = out.status.code();
    let line = |prefix: &str| (stderr.lines()).any(|l| l.contains(prefix) && l.contains(message));
    let ends = match backend {
        // `panic!`, which exits with 101.
        "rust" => code == Some(101) && stderr.contains("panicked at") && stderr.contains(message),
        // `raise BaseException(…)`, which no `except Exception` catches.
        "py" => code == Some(1) && line("BaseException: "),
        // `throw Error(…)`, the same as a bubble's.
        "js" => code == Some(1) && (stderr.lines()).any(|l| l == format!("Error: {message}")),
        // An `AssertionError`, maybe in a class's initializer.
        "java" => code == Some(1) && line("java.lang.AssertionError: "),
        // `std::terminate()` after the message, and
        // `Environment.FailFast(…)`, which abort.
        "cpp" => aborted(&out.status) && stderr.contains(message),
        "csharp" => aborted(&out.status) && line("Process terminated. "),
        _ => false,
    };
    ends && !stderr.contains("Sanitizer")
}

/// Whether a run was ended by a signal other than `SIGABRT`, which C++'s
/// `std::terminate()` and C#'s `FailFast` send: a crash, as a
/// segmentation fault, not a failure the program meant.
pub fn crashed(status: &std::process::ExitStatus) -> bool {
    #[cfg(unix)]
    {
        use std::os::unix::process::ExitStatusExt;
        status.signal().is_some_and(|s| s != 6)
    }
    #[cfg(not(unix))]
    {
        let _ = status;
        false
    }
}

/// Whether a run aborted: by `SIGABRT` on Unix, or, elsewhere, any
/// failing exit (Windows' `abort()` exits with 3, and `FailFast` with
/// its own code).
fn aborted(status: &std::process::ExitStatus) -> bool {
    #[cfg(unix)]
    {
        use std::os::unix::process::ExitStatusExt;
        status.signal() == Some(6)
    }
    #[cfg(not(unix))]
    {
        !status.success()
    }
}

/// A test's failure in a run as tests: its name, as its runner's JUnit
/// XML gives it, and its cause, as Kotlin's `parseJunitResults` takes it:
/// an `<error>`'s text, or a `<failure>`'s `message`, else its `type`,
/// else its text (Mocha's built-in `xunit` reporter's).
#[derive(Clone, Debug, PartialEq)]
pub struct TestFailure {
    pub name: String,
    pub cause: String,
}

/// The failures a small runner of this repo's prints, where a backend's
/// tests run without a build tool (Java's JUnit launcher, C#'s MSTest
/// methods by reflection): a line `ran N` once the tests ran, and each
/// failure as a line of its test's name and its message, tab separated,
/// with newlines in the message as `\n`. `None` without the `ran` line.
pub fn runner_failures(stdout: &str) -> Option<Vec<TestFailure>> {
    stdout.lines().any(|l| l.starts_with("ran ")).then(|| {
        (stdout.lines())
            .filter_map(|l| l.split_once('\t'))
            .map(|(name, cause)| TestFailure {
                name: name.to_string(),
                cause: cause.replace("\\n", "\n"),
            })
            .collect()
    })
}

/// The failures in JUnit XML ([`tools::junit`]), as Kotlin's
/// `parseJunitResults` reads them.
pub fn junit_failures(xml: &str) -> Vec<TestFailure> {
    (tools::junit(xml).into_iter())
        .filter_map(|case| {
            let cause = match (case.error, case.failure) {
                (Some(error), _) => error.text,
                (None, Some(failure)) => (failure.message).or(failure.kind).unwrap_or(failure.text),
                (None, None) => return None,
            };
            Some(TestFailure {
                name: case.name,
                cause,
            })
        })
        .collect()
}

/// Compares a run as tests with what its case expects, as Kotlin's
/// `assertTestingTestFromJunit`: the failing tests' names, through
/// `name` (which undoes a backend's naming of tests) and lowercased,
/// match the expected failures' names, and each expected cause is in
/// some failure's cause. `actual` is the run's failures, from its JUnit
/// XML ([`junit_failures`]) or its runner's output, or `None` if there
/// are no results, which fails.
pub fn check_tests(
    failures: &mut Vec<String>,
    case: &Case,
    actual: Option<Vec<TestFailure>>,
    out: &Output,
    name: impl Fn(&str) -> String,
) {
    let key = &case.key;
    let expected = case.tests.as_deref().unwrap_or_default();
    let output = || format!("{}{}", printed(&out.stdout), printed(&out.stderr));
    let Some(actual) = actual else {
        failures.push(format!("{key}: no test results\n{}", output()));
        return;
    };
    let mut names: Vec<String> = actual
        .iter()
        .map(|f| name(&f.name).to_lowercase())
        .collect();
    names.sort();
    let mut want: Vec<String> = expected.iter().map(|(n, _)| n.to_lowercase()).collect();
    want.sort();
    let causes_found = (expected.iter()).all(|(_, c)| actual.iter().any(|f| f.cause.contains(c)));
    if names != want || !causes_found {
        failures.push(format!(
            "{key}: expected failures {expected:?}, got {actual:?}\n{}",
            output()
        ));
    }
}

/// The failures in JUnit XML text, if there's any.
pub fn junit_file_failures(path: &Path) -> Option<Vec<TestFailure>> {
    let xml = fs::read_to_string(path).ok()?;
    (!xml.trim().is_empty()).then(|| junit_failures(&xml))
}

/// Kotlin's `assertTestingTest` naming undone, for backends whose tests'
/// names are their functions' or methods': the last part of a path
/// (`tests::a_failing_test`), a `__N` suffix and `()` dropped, and
/// camelCase or snake_case as words (`aFailingTest` and
/// `a_failing_test` are `a failing test`).
pub fn test_name_words(name: &str) -> String {
    let name = name.strip_suffix("()").unwrap_or(name);
    let name = name.rsplit(['.', ':', '/']).next().unwrap_or(name);
    let name = match name.rsplit_once("__") {
        Some((n, d)) if !d.is_empty() && d.chars().all(|c| c.is_ascii_digit()) => n,
        _ => name,
    };
    let mut out = String::new();
    for (i, c) in name.chars().enumerate() {
        if c == '_' {
            out.push(' ');
            continue;
        }
        if c.is_uppercase() && i > 0 {
            out.push(' ');
        }
        out.extend(c.to_lowercase());
    }
    out
}

/// What `module-state` logs when its init runs and then 8 threads each
/// call `tick` and `report` 1,000 times: its init's log, then `ticks
/// 8000`.
pub fn threads_expected(cases: &[Case]) -> Vec<String> {
    let state = cases.iter().find(|c| c.library == "module-state").unwrap();
    let mut expected = state.expected.clone();
    expected.push("ticks 8000".into());
    expected
}

/// Fails with every failure, if any.
pub fn assert_none(failures: &[String]) {
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}
