//! The Python backend on Kotlin Temper's functional tests that tmpl's
//! translation covers so far, in `temper-funtests`'s
//! `cases/`, and on the shared programs
//! (`crates/temper-be-testkit/programs/`) that it covers so far:
//! `string-order`, which every backend runs; `modules`,
//! `imports-library`, and `testing`, for modules, imports, and tests;
//! for classes and interfaces, `classes` and the testkit's class tests
//! (`CLASSES`); and for nullable types, `orelse`, `bubble()`, and
//! `panic()`, `nullables` and the testkit's `NULLABLES`; and for
//! generics and core's lists and maps, `generics` and the testkit's
//! `GENERICS`; for function values, `functions` and the testkit's
//! `FUNCTIONS`; for parameters' defaults, `defaults` and the testkit's
//! `DEFAULTS`; for core's other types and members, the testkit's
//! `CORE_TYPES`; and for a library's own connected functions and
//! classes, with host code in `__connected__.py`, the testkit's
//! `CONNECTED`, each as is and under `--no-connected`; and for divisions
//! that can't fail and maps, the testkit's `DIVISIONS`; and for labeled
//! loops' jumps, classes declared in functions, type aliases, explicit
//! type arguments, and upcasting `as`, the testkit's `LOOPS_AND_CLASSES`; and for
//! tests run with std's `testing`, the testkit's `TESTS`; and for std's
//! other modules, the testkit's `WITH_STD`, and `net`'s, `NET`, against
//! the testkit's test server; and for generators, `async`,
//! and `await`, as Python's generators, the testkit's `ASYNC`.
//!
//! - `goldens`: each test's generated Python, laid out as Kotlin's, with
//!   its root module matching the golden in `tests/goldens/`, named for
//!   the test's path (`algos-fibonacci.py`), and its other files, if
//!   any, theirs, named for the path and the file's place
//!   (`modules.helpers.deep.py`, `testing.tests.test_testing.py`). To
//!   accept new or changed output, run with `UPDATE_SNAPSHOTS=1` and
//!   review the diff.
//! - `runs`: each test's generated Python runs, against the
//!   `temper_core` runtime (`crates/temper-be-py/runtime/temper-core`), and logs
//!   what the test expects of Kotlin's `py` backend; and type checkers
//!   find no problems in it, in strict mode: mypy and pyright, each where
//!   installed (on `PATH`, or named by `MYPY` or `PYRIGHT`), except for
//!   a library with host code, which isn't written for them. Each runs
//!   once per batch of cases whose package names don't clash, rather than
//!   once per case; a case it finds problems in is checked again alone,
//!   and that message reported (`check_types`). Skipped, with
//!   a message, without Python 3.11 or later (`python3`, or `PYTHON`);
//!   with `TEMPER_REQUIRE_CHECKERS` set, as in CI, a missing Python or
//!   checker fails it instead. A library that imports another
//!   (`imports-library`) runs with it beside it. `python -m py_compile`
//!   checks each tests file; a test that runs as tests
//!   (`testing/asserts`) runs, with std's `temper-std` beside it, by
//!   `python -m temper_core.testing result.xml`, as Kotlin's `RunPython`,
//!   its JUnit XML's failures compared with its `expectedFailures`.
//!   Every library module generated, and std's, sets `__all__` to its
//!   exports and binds no other module-level name without a leading `_`
//!   (`tests/module_names.py`). The cases that log run in one
//!   interpreter (`tests/bulk_runner.py`), and any that doesn't log what
//!   it expects runs again alone, whose result counts; with
//!   `TEMPER_PY_BULK=0`, each runs alone (notes/test-speed.md,
//!   "Python's functional tests in bulk").

use std::path::{Path, PathBuf};
use std::process::Command;
use std::{env, fs};
use temper_be_py::{OutFile, PY, translate};
use temper_be_testkit::{self as kit, Case, Goldens};

/// The shared programs that Python's translation covers so far.
const PROGRAMS: [&str; 16] = [
    "classes",
    "nullables",
    "generics",
    "functions",
    "defaults",
    "string-order",
    "modules",
    "imports-library",
    "testing",
    "async-turns",
    "libraries",
    "errors",
    "triple",
    "content-tags",
    "json-adapters",
    "reach",
];

fn cases() -> Vec<Case> {
    let mut cases = kit::cases_with(
        "py",
        &PROGRAMS,
        &[
            &kit::CLASSES[..],
            &kit::NULLABLES[..],
            &kit::GENERICS[..],
            &kit::FUNCTIONS[..],
            &kit::DEFAULTS[..],
            &kit::CORE_TYPES[..],
            &kit::CONNECTED[..],
            &kit::DIVISIONS[..],
            &kit::LOOPS_AND_CLASSES[..],
            &kit::TYPING[..],
            &kit::ANY_VALUES[..],
            &kit::ERRORS[..],
            &kit::TYPE_VALUES[..],
            &kit::ASYNC[..],
            &kit::TESTS[..],
            &kit::WITH_STD[..],
            &kit::NET[..],
        ]
        .concat(),
    );
    // This crate's own programs, in `tests/programs/`.
    let dir = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/programs");
    for name in PY_PROGRAMS {
        let path = dir.join(format!("{name}.temper.md"));
        let text = fs::read_to_string(&path).unwrap();
        let mut case = Case::inline(name, &path.to_string_lossy(), text, Vec::new());
        case.expected = log_blocks(&case.sources[0].1);
        case.dir = dir.clone();
        cases.push(case);
    }
    cases
}

/// This crate's own programs, in `tests/programs/`: `py-builtin-params`,
/// parameters named like Python's builtins, which keep their names
/// (`host_interop` passes one by name), and names a function binds more
/// than once, annotated once.
const PY_PROGRAMS: [&str; 1] = ["py-builtin-params"];

/// The lines of a program's log blocks, in order.
fn log_blocks(text: &str) -> Vec<String> {
    let mut lines = Vec::new();
    let mut in_log = false;
    for line in text.lines() {
        if in_log && line.starts_with("```") {
            in_log = false;
        } else if in_log {
            lines.push(line.to_string());
        } else if line.starts_with("```log") {
            in_log = true;
        }
    }
    lines
}

/// A case's generated files.
fn generate(case: &Case) -> Vec<OutFile> {
    translate(&kit::program_for(case, &PY.rules), &PY)
        .unwrap_or_else(|e| panic!("{}: {e}", case.key))
}

#[test]
fn goldens() {
    kit::with_stack(|| {
        let mut goldens = Goldens::new(env!("CARGO_MANIFEST_DIR"));
        for case in cases() {
            let files = generate(&case);
            let path = &case.key;
            let library = &case.library;
            let package = temper_be_py::names::library_package(library);
            let paths: Vec<&str> = files.iter().map(|f| f.path.as_str()).collect();
            assert_eq!(
                paths[..2],
                [
                    format!("{library}/pyproject.toml"),
                    format!("{library}/{package}/__init__.py"),
                ],
                "{path}"
            );
            let root_module = format!("{library}/{package}/{package}.py");
            assert!(paths.contains(&root_module.as_str()), "{path}");
            let single = format!("from .{package} import *\n");
            if files.len() == 3 {
                assert_eq!(files[1].text, single, "{path}");
            }
            for file in &files[1..] {
                let rest = &file.path[library.len() + 1..];
                let name = if rest == format!("{package}/{package}.py") {
                    format!("{path}.py")
                } else if file.text == single {
                    continue;
                } else {
                    let rest = rest.strip_prefix(&format!("{package}/")).unwrap_or(rest);
                    format!("{path}.{}", rest.replace('/', "."))
                };
                goldens.check(&name, &file.text);
            }
        }
        goldens.finish();
    });
}

/// The shared `reach` program's test parts only in its tests file, and
/// what nothing reaches nowhere, as tmpl's `reach::partition` splits the
/// program; `runs` runs its test, which passes only if they're set.
#[test]
fn test_parts_apart() {
    kit::with_stack(|| {
        let case = (cases().into_iter()).find(|c| c.key == "reach").unwrap();
        let files = generate(&case);
        let lines = (files.iter()).flat_map(|f| {
            let tests = f.path.contains("/tests/");
            (f.text.lines()).map(move |line| (f.path.as_str(), tests, line))
        });
        let tests_only = [
            "for_tests",
            "test_factor",
            "test_count",
            "TestShape",
            "TestSquare",
            "add_area",
        ];
        let unreached = [
            "unreached_function",
            "UnreachedClass",
            "UnreachedInterface",
            "unreached_value",
        ];
        kit::check_test_parts_apart(lines, &tests_only, &unreached);
    });
}

/// A library only the tests use is in `pyproject.toml`'s `test`
/// dependency group (PEP 735), which installing the package skips, not
/// its dependencies.
#[test]
fn test_only_libraries_grouped() {
    kit::with_stack(|| {
        let case = (cases().into_iter()).find(|c| c.key == "reach").unwrap();
        let files = generate(&case);
        let pyproject = &files[0].text;
        let (dependencies, rest) = pyproject.split_once("\n]\n").unwrap();
        assert!(dependencies.contains("\"temper-core=="), "{pyproject}");
        assert!(!dependencies.contains("temper-std"), "{pyproject}");
        assert!(
            rest.contains("\n[dependency-groups]\ntest = [\n    \"temper-std=="),
            "{pyproject}"
        );
    });
}

/// Python 3.11 or later, if there is one.
fn python() -> Option<String> {
    let candidates = match env::var("PYTHON") {
        Ok(p) => vec![p],
        Err(_) => vec!["python3".to_string(), "python".to_string()],
    };
    candidates.into_iter().find(|p| {
        Command::new(p)
            .args([
                "-c",
                "import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)",
            ])
            .output()
            .is_ok_and(|o| o.status.success())
    })
}

/// Python in UTF-8 mode (`PYTHONUTF8=1`), as `temper run -b py` runs it:
/// else, on Windows, its standard streams and files are in the system's
/// code page, which can't write what cases log, as `✅`.
fn python_command(python: &str) -> Command {
    let mut command = Command::new(python);
    command.env("PYTHONUTF8", "1");
    command
}

/// A library's modules among its generated files: the `.py` files in its
/// package but the package's own `__init__.py` (which star-imports the
/// root module's exports and names its modules) and copies of host files.
fn library_modules<'f>(
    library: &str,
    files: &'f [OutFile],
    host_files: &[(String, String)],
) -> impl Iterator<Item = &'f OutFile> {
    let package = temper_be_py::names::library_package(library);
    let init = format!("{library}/{package}/__init__.py");
    let prefix = format!("{library}/{package}/");
    let host: Vec<String> = host_files.iter().map(|(_, t)| t.clone()).collect();
    files.iter().filter(move |f| {
        f.path.starts_with(&prefix)
            && f.path.ends_with(".py")
            && f.path != init
            && !host.contains(&f.text)
    })
}

/// Runs `module_names.py` over generated modules: each sets `__all__` to
/// its exports, and every other module-level name it binds starts with
/// `_`.
fn check_module_names(python: &str, modules: &[PathBuf], failures: &mut Vec<String>) {
    let script = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/module_names.py");
    let check = python_command(python)
        .arg("-B")
        .arg(&script)
        .args(modules)
        .output()
        .unwrap();
    if !check.status.success() {
        failures.push(format!(
            "module-level names:\n{}{}",
            String::from_utf8_lossy(&check.stdout),
            String::from_utf8_lossy(&check.stderr)
        ));
    }
}

/// A case that logs, run in a process of its own: its package imported
/// with the runtime's logging to stdout, in a top-level turn, so its
/// async blocks start after its top-level code, then its async work
/// awaited.
fn run_alone(
    python: &str,
    root: &Path,
    projects: &[PathBuf],
    package: &str,
) -> std::process::Output {
    let script = format!(
        "from temper_core import init_simple_logging, await_safe_to_exit, top_level_turn\n\
         init_simple_logging()\n\
         with top_level_turn():\n    import {package}\n\
         await_safe_to_exit()\n"
    );
    // -B: write no bytecode into the runtime.
    python_command(python)
        .args(["-B", "-c", &script])
        .env("PYTHONPATH", env::join_paths(projects).unwrap())
        .current_dir(root)
        .output()
        .unwrap()
}

/// A case that logs, waiting for a bulk run.
struct Logged {
    key: String,
    root: PathBuf,
    projects: Vec<PathBuf>,
    package: String,
    expected: Vec<String>,
}

fn json_string(s: &str) -> String {
    let mut out = String::from("\"");
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

/// Runs the cases that log in one interpreter (`tests/bulk_runner.py`),
/// each after a marker line, and checks each one's lines. A case that
/// doesn't log what it expects, or that a crash leaves without its
/// marker, is run again alone (`run_alone`), whose result counts, so a
/// failure's message is as precise as without the bulk run.
fn run_batch(python: &str, batch: &[Logged], failures: &mut Vec<String>) {
    if batch.is_empty() {
        return;
    }
    let runtime = runtime();
    let cases: Vec<String> = (batch.iter())
        .map(|c| {
            let path: Vec<String> = (c.projects.iter())
                .map(|p| json_string(&p.to_string_lossy()))
                .collect();
            format!(
                "{{\"key\": {}, \"cwd\": {}, \"path\": [{}], \"package\": {}}}",
                json_string(&c.key),
                json_string(&c.root.to_string_lossy()),
                path.join(", "),
                json_string(&c.package)
            )
        })
        .collect();
    let script = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/bulk_runner.py");
    let mut child = python_command(python)
        .arg("-B")
        .arg(&script)
        .env("PYTHONPATH", &runtime)
        .stdin(std::process::Stdio::piped())
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .spawn()
        .unwrap();
    {
        use std::io::Write;
        let mut stdin = child.stdin.take().unwrap();
        write!(stdin, "[{}]", cases.join(", ")).unwrap();
    }
    let out = child.wait_with_output().unwrap();
    let stdout = String::from_utf8_lossy(&out.stdout);
    // Each case's lines, by key; `None` after an exception.
    let mut logged: std::collections::HashMap<&str, Option<Vec<&str>>> = Default::default();
    let mut current: Option<&str> = None;
    for line in stdout.lines() {
        if let Some(rest) = line.strip_prefix('\x1e') {
            if rest == "!" {
                if let Some(key) = current {
                    logged.insert(key, None);
                }
                current = None;
            } else {
                logged.insert(rest, Some(Vec::new()));
                current = Some(rest);
            }
        } else if let Some(Some(lines)) = current.and_then(|k| logged.get_mut(k)) {
            lines.push(line);
        }
    }
    // The last case's lines are complete only if the runner finished.
    if !out.status.success() {
        if let Some(key) = current {
            logged.insert(key, None);
        }
    }
    let mut alone = Vec::new();
    for case in batch {
        let passed =
            matches!(logged.get(case.key.as_str()), Some(Some(lines)) if *lines == case.expected);
        if !passed {
            alone.push(case.key.as_str());
            let run = run_alone(python, &case.root, &case.projects, &case.package);
            kit::check_log(failures, &case.key, &run, &case.expected);
        }
    }
    eprintln!(
        "bulk: {} cases in one interpreter; {} run again alone: {alone:?}",
        batch.len(),
        alone.len()
    );
}

/// A case's package, waiting for its type checks (`check_types`).
struct Checked {
    key: String,
    root: PathBuf,
    /// The runtime, then the libraries it imports, then its own, then
    /// std's where it needs them.
    projects: Vec<PathBuf>,
    library: String,
    package: String,
}

impl Checked {
    fn package_dir(&self) -> PathBuf {
        self.root.join(&self.library).join(&self.package)
    }
}

/// `mypy --strict`, with its import path and cache, as the type checks
/// run it.
fn mypy_command(mypy: &str, path_var: &std::ffi::OsStr, cache: &Path) -> Command {
    let mut command = Command::new(mypy);
    command
        .args([
            "--strict",
            "--python-version",
            "3.11",
            "--follow-imports=silent",
        ])
        .arg("--cache-dir")
        .arg(cache)
        .env("MYPYPATH", path_var);
    command
}

/// `mypy --strict` on one case's package, as a failure if it finds
/// anything.
fn mypy_one(mypy: &str, case: &Checked) -> Option<String> {
    let path_var = env::join_paths(&case.projects).unwrap();
    let check = mypy_command(mypy, &path_var, &case.root.join(".mypy_cache"))
        .arg(case.package_dir())
        .current_dir(&case.root)
        .output()
        .unwrap();
    (!check.status.success()).then(|| {
        format!(
            "{}: mypy --strict:\n{}",
            case.key,
            String::from_utf8_lossy(&check.stdout)
        )
    })
}

/// pyright's strict mode, configured by a `pyrightconfig.json` written
/// in `dir`: the projects on its import path, the packages checked.
fn pyright_command(pyright: &str, dir: &Path, projects: &[PathBuf], include: &[String]) -> Command {
    let quoted = |paths: &mut dyn Iterator<Item = String>| -> String {
        let paths: Vec<String> = paths.map(|p| json_string(&p.replace('\\', "/"))).collect();
        paths.join(", ")
    };
    let extra = quoted(&mut projects.iter().map(|p| p.to_string_lossy().into_owned()));
    let include = quoted(&mut include.iter().cloned());
    let config = dir.join("pyrightconfig.json");
    fs::write(
        &config,
        format!(
            "{{\"typeCheckingMode\": \"strict\", \"pythonVersion\": \"3.11\", \
             \"extraPaths\": [{extra}], \"include\": [{include}]}}\n"
        ),
    )
    .unwrap();
    let mut command = Command::new(pyright);
    command.arg("-p").arg(&config).current_dir(dir);
    command
}

/// pyright on one case's package, as a failure if it finds anything.
fn pyright_one(pyright: &str, case: &Checked) -> Option<String> {
    let include = format!("{}/{}", case.library, case.package);
    let check = pyright_command(pyright, &case.root, &case.projects, &[include])
        .output()
        .unwrap();
    (!check.status.success()).then(|| {
        format!(
            "{}: pyright (strict):\n{}",
            case.key,
            String::from_utf8_lossy(&check.stdout)
        )
    })
}

/// A digest of a project's Python files, their paths and text, so two
/// cases' copies of one library (std's, or one both import) can share a
/// batch.
fn digest(project: &Path) -> u64 {
    use std::hash::{DefaultHasher, Hash, Hasher};
    let mut files = Vec::new();
    kit::files_with(project, "py", &mut files);
    let mut hasher = DefaultHasher::new();
    for file in files {
        file.strip_prefix(project).unwrap().hash(&mut hasher);
        fs::read(&file).unwrap().hash(&mut hasher);
    }
    hasher.finish()
}

/// Cases whose packages can be type-checked together: each top-level
/// package name stands for one project, so a case's own package is
/// alone in its batch under its name, and a library it imports shares
/// a name only with an identical copy, whose first path serves all.
#[derive(Default)]
struct CheckBatch {
    cases: Vec<usize>,
    /// The runtime, then each project once.
    projects: Vec<PathBuf>,
    /// Each package's project's digest, and whether a case checks it.
    names: std::collections::HashMap<String, (u64, bool)>,
}

fn check_batches(checked: &[Checked]) -> Vec<CheckBatch> {
    let mut digests = std::collections::HashMap::new();
    let mut batches: Vec<CheckBatch> = Vec::new();
    for (i, case) in checked.iter().enumerate() {
        let own = case.root.join(&case.library);
        let mut packages = Vec::new();
        for project in &case.projects[1..] {
            let name = project.file_name().unwrap().to_string_lossy();
            let package = temper_be_py::names::library_package(&name);
            let digest = *(digests.entry(project.clone())).or_insert_with(|| digest(project));
            packages.push((package, project, digest, *project == own));
        }
        let fits = |batch: &CheckBatch| {
            (packages.iter()).all(
                |(package, _, digest, is_own)| match batch.names.get(package) {
                    None => true,
                    Some((d, _)) => !is_own && d == digest,
                },
            )
        };
        let at = match batches.iter().position(fits) {
            Some(at) => at,
            None => {
                batches.push(CheckBatch {
                    projects: vec![case.projects[0].clone()],
                    ..Default::default()
                });
                batches.len() - 1
            }
        };
        let batch = &mut batches[at];
        batch.cases.push(i);
        for (package, project, digest, is_own) in packages {
            if !batch.names.contains_key(&package) {
                batch.projects.push(project.clone());
            }
            let entry = batch.names.entry(package).or_insert((digest, false));
            entry.1 |= is_own;
        }
    }
    batches
}

fn skipped(out: &std::process::Output) -> bool {
    let all = [&out.stdout[..], &out.stderr[..]].concat();
    let all = String::from_utf8_lossy(&all);
    all.contains("Ignoring path") || all.contains("No source files found")
}

/// A batched checker's lines, by case (by its place in `cases`): each
/// line about a file starts with its absolute path (mypy's with
/// `--show-absolute-path`), under one case's root. Other lines (a
/// summary, pyright's file headers' indented diagnostics aside) go to
/// none.
fn trace<'o>(stdout: &'o str, cases: &[&Checked]) -> Vec<Vec<&'o str>> {
    let mut found = vec![Vec::new(); cases.len()];
    for line in stdout.lines() {
        let file = line.trim_start();
        let at = cases.iter().position(|c| {
            (file.strip_prefix(c.root.to_str().unwrap()))
                .is_some_and(|rest| rest.starts_with(['/', '\\']))
        });
        if let Some(at) = at {
            found[at].push(line);
        }
    }
    found
}

/// Lines from batched checkers' runs go to the case whose files they're
/// about, and to no other, even where one case's root starts another's
/// name: a type error in one case fails that case alone.
#[test]
fn checks_traced_to_cases() {
    let case = |key: &str| Checked {
        key: key.to_string(),
        // With `/` on every platform, as the lines below have it.
        root: PathBuf::from(format!("/out/{key}")),
        projects: Vec::new(),
        library: "lib".to_string(),
        package: "lib".to_string(),
    };
    let (a, ab) = (case("a"), case("a-b"));
    let mypy = "/out/a-b/lib/lib/lib.py:3: error: Function is missing a type annotation  [no-untyped-def]\n\
                Found 1 error in 1 file (checked 2 source files)\n";
    assert_eq!(
        trace(mypy, &[&a, &ab]),
        [vec![], vec![mypy.lines().next().unwrap()]]
    );
    let pyright = "/out/a/lib/lib/lib.py\n\
                   \x20 /out/a/lib/lib/lib.py:3:5 - error: Return type is unknown\n\
                   1 error, 0 warnings, 0 informations\n";
    let lines: Vec<&str> = pyright.lines().collect();
    assert_eq!(trace(pyright, &[&a, &ab]), [lines[..2].to_vec(), vec![]]);
}

/// One checker's run over a batch: failures for the cases it finds
/// problems in, each rerun alone, whose message is the one reported
/// (or the batch's, if it passes alone). A run that fails to start,
/// crashes, or fails with nothing it says traced to a case reruns every
/// case alone.
fn check_batch(
    name: &str,
    run: std::io::Result<std::process::Output>,
    checked: &[Checked],
    batch: &CheckBatch,
    one: &dyn Fn(&Checked) -> Option<String>,
    failures: &mut Vec<String>,
) {
    let cases: Vec<&Checked> = batch.cases.iter().map(|&i| &checked[i]).collect();
    let mut found = vec![Vec::new(); cases.len()];
    let traced = match &run {
        // A checker that skips what it's given (pyright ignores some
        // paths in `include`, with a message) checks nothing.
        Ok(out) if skipped(out) => false,
        Ok(out) if out.status.success() => return,
        // Both exit with 1 for problems found, 2 or more otherwise.
        Ok(out) if out.status.code() == Some(1) => {
            found = trace(std::str::from_utf8(&out.stdout).unwrap_or(""), &cases);
            found.iter().any(|lines| !lines.is_empty())
        }
        _ => false,
    };
    if !traced {
        let what = match &run {
            Ok(out) => format!(
                "{}\n{}{}",
                out.status,
                String::from_utf8_lossy(&out.stdout),
                String::from_utf8_lossy(&out.stderr)
            ),
            Err(e) => e.to_string(),
        };
        eprintln!(
            "{name} on {} cases at once failed ({what}); checking each alone",
            cases.len()
        );
    }
    for (case, lines) in cases.iter().zip(&found) {
        if traced && lines.is_empty() {
            continue;
        }
        match one(case) {
            Some(failure) => failures.push(failure),
            None if traced => failures.push(format!(
                "{}: {name}, with {} other cases (passes alone):\n{}",
                case.key,
                cases.len() - 1,
                lines.join("\n")
            )),
            None => {}
        }
    }
}

/// Type-checks every case's package with mypy and pyright, each where
/// found: one run of each per batch of cases whose packages' names
/// don't clash (`check_batches`), rather than one per case, which is
/// most of the test's time with them. mypy's cache is kept per batch,
/// in `out/checks/N`, across runs.
fn check_types(
    out: &Path,
    mypy: Option<&str>,
    pyright: Option<&str>,
    checked: &[Checked],
    failures: &mut Vec<String>,
) {
    let batches = check_batches(checked);
    let start = std::time::Instant::now();
    let mut times = [std::time::Duration::ZERO; 2];
    for (i, batch) in batches.iter().enumerate() {
        let dir = out.join("checks").join(i.to_string());
        fs::create_dir_all(&dir).unwrap();
        let cases = batch.cases.iter().map(|&i| &checked[i]);
        if let Some(mypy) = mypy {
            let path_var = env::join_paths(&batch.projects).unwrap();
            let at = std::time::Instant::now();
            let run = mypy_command(mypy, &path_var, &dir.join(".mypy_cache"))
                .arg("--show-absolute-path")
                .args(cases.clone().map(Checked::package_dir))
                .current_dir(&dir)
                .output();
            let one = |c: &Checked| mypy_one(mypy, c);
            check_batch("mypy --strict", run, checked, batch, &one, failures);
            times[0] += at.elapsed();
        }
        if let Some(pyright) = pyright {
            // pyright ignores an absolute path in `include`.
            let include: Vec<String> = (cases.map(|c| c.package_dir()))
                .map(|p| format!("../../{}", p.strip_prefix(out).unwrap().to_string_lossy()))
                .collect();
            let at = std::time::Instant::now();
            let run = pyright_command(pyright, &dir, &batch.projects, &include).output();
            let one = |c: &Checked| pyright_one(pyright, c);
            check_batch("pyright (strict)", run, checked, batch, &one, failures);
            times[1] += at.elapsed();
        }
    }
    eprintln!(
        "checks: {} cases in {} batches, {:.1?} (mypy {:.1?}, pyright {:.1?})",
        checked.len(),
        batches.len(),
        start.elapsed(),
        times[0],
        times[1]
    );
}

fn runtime() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime/temper-core")
}

#[test]
fn runs() {
    let Some(python) = python() else {
        kit::missing("no Python 3.11 or later (set PYTHON to one)");
        return;
    };
    let mypy = kit::tool("MYPY", "mypy");
    let pyright = kit::tool("PYRIGHT", "pyright");
    for (name, found) in [("mypy", &mypy), ("pyright", &pyright)] {
        if found.is_none() {
            kit::missing(&format!(
                "type check by {name}: no {name} (on PATH, or named by its variable)"
            ));
        }
    }
    kit::with_stack(move || {
        let out = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-py");
        let mut failures = Vec::new();
        // The cases that log run in one interpreter after the others
        // (`run_batch`), unless `TEMPER_PY_BULK=0`.
        let bulk = env::var("TEMPER_PY_BULK").map_or(true, |v| v != "0");
        let mut batch = Vec::new();
        // Every library module generated, for `check_module_names`.
        let mut modules = Vec::new();
        // The cases to type-check, for `check_types`.
        let mut checked = Vec::new();
        // `async-turns`'s import path, and `py-builtin-params`'s project,
        // for `host_interop`.
        let mut interop = None;
        let mut builtin_params = None;
        for case in kit::with_huge(cases()) {
            let path = &case.key;
            let root = out.join(path);
            let _ = fs::remove_dir_all(&root);
            let mut projects = vec![runtime()];
            for case in kit::libraries(&case, "py").iter().chain([&case]) {
                let files = generate(case);
                kit::write_files(
                    &root,
                    files.iter().map(|f| (f.path.as_str(), f.text.as_str())),
                );
                projects.push(root.join(&case.library));
                let found = library_modules(&case.library, &files, &case.host_files);
                modules.extend(found.map(|f| root.join(&f.path)));
            }
            // A run as tests needs std's `testing`, and an import of std's
            // modules them: std's output beside it.
            if case.needs_std() {
                let files = translate(&kit::std_program(), &PY).unwrap();
                kit::write_files(
                    &root,
                    files.iter().map(|f| (f.path.as_str(), f.text.as_str())),
                );
                projects.push(root.join("temper-std"));
            }
            let library = &case.library;
            let package = temper_be_py::names::library_package(library);
            let project = root.join(library);
            let path_var = env::join_paths(&projects).unwrap();
            if case.key == "async-turns" {
                interop = Some((projects.clone(), case.expected.clone()));
            }
            if case.key == "py-builtin-params" {
                builtin_params = Some((project.clone(), case.expected.clone()));
            }
            let tests = project.join("tests");
            if tests.exists() {
                let mut files = Vec::new();
                kit::files_with(&tests, "py", &mut files);
                let compile = python_command(&python)
                    .args(["-B", "-m", "py_compile"])
                    .args(&files)
                    .output()
                    .unwrap();
                if !compile.status.success() {
                    failures.push(format!(
                        "{path}: tests don't compile:\n{}",
                        String::from_utf8_lossy(&compile.stderr)
                    ));
                }
            }
            if case.tests.is_some() {
                // As Kotlin's `RunPython`: `temper_core.testing` runs the
                // project's tests and writes their JUnit XML.
                let run = python_command(&python)
                    .args(["-B", "-m", "temper_core.testing", "result.xml"])
                    .env("PYTHONPATH", &path_var)
                    .current_dir(&project)
                    .output()
                    .unwrap();
                let actual = kit::junit_file_failures(&project.join("result.xml"));
                kit::check_tests(&mut failures, &case, actual, &run, |n| n.to_string());
            } else if bulk && !case.fails {
                batch.push(Logged {
                    key: path.clone(),
                    root: root.clone(),
                    projects: projects.clone(),
                    package: package.clone(),
                    expected: case.expected.clone(),
                });
            } else {
                let run = run_alone(&python, &root, &projects, &package);
                kit::check_case(&mut failures, path, &run, &case);
            }
            // Host code is the library's own, which its module's file
            // starts with, and the functional tests' isn't written for
            // strict checkers (its functions take no `self`), nor are calls
            // of it, so a library with host code isn't checked, nor is
            // ill-typed code, which runs as written.
            if case.host_files.is_empty() && !kit::ill_typed(&case) {
                checked.push(Checked {
                    key: path.clone(),
                    root: root.clone(),
                    projects,
                    library: library.clone(),
                    package,
                });
            }
        }
        run_batch(&python, &batch, &mut failures);
        if let (Some((mut projects, logged)), Some((project, then))) = (interop, builtin_params) {
            projects.push(project);
            let checkers = (mypy.as_deref(), pyright.as_deref());
            let logs = [&logged[..], &then[..]];
            host_interop(&python, checkers, &out, &projects, logs, &mut failures);
        }
        check_types(
            &out,
            mypy.as_deref(),
            pyright.as_deref(),
            &checked,
            &mut failures,
        );
        // std's modules, once.
        let std_root = out.join("std");
        let _ = fs::remove_dir_all(&std_root);
        let files = translate(&kit::std_program(), &PY).unwrap();
        kit::write_files(
            &std_root,
            files.iter().map(|f| (f.path.as_str(), f.text.as_str())),
        );
        let found = library_modules("temper-std", &files, &[]);
        modules.extend(found.map(|f| std_root.join(&f.path)));
        check_module_names(&python, &modules, &mut failures);
        kit::assert_none(&failures);
    });
}

/// `tests/host/interop.py`, host code that uses `async-turns` the
/// standard way: it awaits a generated promise with `.result()`, with
/// asyncio, and with asyncio as the runner; blocks on one inside a turn,
/// which fails clearly; and loops over a generated generator, which a
/// failed step leaves done; and, once `py-builtin-params`'s import has
/// logged `then`, passes its constructor its parameters by name
/// (`help=`), as they keep their names. mypy and pyright check it,
/// strict, as they do generated code, so a bare `await` of a generated
/// promise must type-check.
fn host_interop(
    python: &str,
    (mypy, pyright): (Option<&str>, Option<&str>),
    out: &Path,
    projects: &[PathBuf],
    [logged, then]: [&[String]; 2],
    failures: &mut Vec<String>,
) {
    let script = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/host/interop.py");
    let dir = out.join("host");
    let _ = fs::remove_dir_all(&dir);
    fs::create_dir_all(&dir).unwrap();
    fs::copy(&script, dir.join("interop.py")).unwrap();
    let path_var = env::join_paths(projects).unwrap();
    let mut checks = Vec::new();
    if let Some(mypy) = mypy {
        let command = mypy_command(mypy, &path_var, &dir.join(".mypy_cache"))
            .arg("interop.py")
            .current_dir(&dir)
            .output();
        checks.push(("mypy --strict", command));
    }
    if let Some(pyright) = pyright {
        let command = pyright_command(pyright, &dir, projects, &["interop.py".into()]).output();
        checks.push(("pyright (strict)", command));
    }
    for (name, check) in checks {
        let check = check.unwrap();
        if !check.status.success() {
            failures.push(format!(
                "host interop: {name}:\n{}",
                String::from_utf8_lossy(&check.stdout)
            ));
        }
    }
    let run = python_command(python)
        .arg("-B")
        .arg(&script)
        .env("PYTHONPATH", env::join_paths(projects).unwrap())
        .output()
        .unwrap();
    let expected: Vec<String> = (logged.iter().cloned())
        .chain(
            [
                "result 2",
                "asyncio 4",
                "asyncio runner 6 ['MainThread']",
                "in a turn: True",
                "for [0, 1, 2]",
                "failed step",
                "then done",
                "fibonacci [0, 1, 1, 2, 3, 5, 8, 13]",
                "collatz [7, 22, 11, 34, 17, 52, 26, 13, 40, 20, 10, 5, 16, 8, 4, 2, 1]",
            ]
            .map(String::from),
        )
        // `py_builtin_params`'s own log, as it's imported.
        .chain(then.iter().cloned())
        .chain(["by name flag: how 9".to_string()])
        .collect();
    kit::check_log(failures, "host interop", &run, &expected);
}

/// std's modules that build as a library of its own
/// (`temper_be_testkit::std_program`) translate to the `temper-std`
/// package, matching the goldens `std.*.py`; `runs` runs tests with
/// them.
#[test]
fn std_goldens() {
    kit::with_stack(|| {
        let mut goldens = Goldens::new(env!("CARGO_MANIFEST_DIR"));
        let files = translate(&kit::std_program(), &PY).unwrap_or_else(|e| panic!("std: {e}"));
        for file in &files {
            let rest = file.path.strip_prefix("temper-std/").unwrap_or(&file.path);
            goldens.check(&format!("std.{}", rest.replace('/', ".")), &file.text);
        }
        goldens.finish();
    });
}
