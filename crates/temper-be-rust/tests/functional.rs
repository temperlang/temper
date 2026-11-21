//! The Rust backend on Kotlin Temper's functional tests that tmpl's
//! translation covers so far, in `temper-funtests`'s
//! `cases/`, with the class tests (`kit::CLASSES`), those
//! with nullable types and `orelse` (`kit::NULLABLES`), those with
//! generics (`kit::GENERICS`), those with function values
//! (`kit::FUNCTIONS`), those with parameters' defaults
//! (`kit::DEFAULTS`), those with core's other types and members
//! (`kit::CORE_TYPES`), and those with a library's own connected
//! functions and classes, whose host code, `_connected.rs` and `more.rs`,
//! goes in `src/_connected/` (`kit::CONNECTED`, each as is and under
//! `--no-connected`; host files' goldens are copies, not checked against
//! rustfmt), and those of divisions that can't fail and maps
//! (`kit::DIVISIONS`), and of labeled loops' jumps and classes declared
//! in functions (`kit::LOOPS_AND_CLASSES`), and of generators, `async`,
//! and `await`, as state machines (`kit::ASYNC`), and on the shared programs
//! (`crates/temper-be-testkit/programs/`), which cover classes and
//! interfaces (`classes`), nullables and `orelse` (`nullables`),
//! generics and core's lists and maps (`generics`), function values
//! (`functions`), parameters' defaults (`defaults`), module state,
//! operators,
//! jumps, string order, modules and imports (`modules`, and
//! `imports-library`, which imports it), and tests (`testing`), and on
//! this crate's own (`tests/programs/`), which cover what's particular
//! to Rust ([`OWN`]).
//! - `goldens`: each program's generated crate, laid out as Kotlin's,
//!   with its `src/mod.rs` matching the golden in `tests/goldens/`, named
//!   for the test's path (`algos-fibonacci.rs`), its other modules'
//!   files theirs (`modules.helpers.deep.rs`), and every generated
//!   Rust file as rustfmt would lay it out, where rustfmt is installed. To
//!   accept new or changed output, run with `UPDATE_SNAPSHOTS=1` and
//!   review the diff.
//! - `runs`: the generated crates, in one cargo workspace with one target
//!   directory (under `CARGO_TARGET_TMPDIR`, kept between runs so builds
//!   are incremental), build offline against `temper-core`
//!   (`crates/temper-be-rust/runtime/temper-core`, with its `Cargo.lock`), and
//!   each program logs what the test expects of Kotlin's `rust` backend;
//!   a harness also runs `module-state` from 8 threads, with no `init`
//!   first, which their first reads run, and another calls libraries
//!   with no `init` ([`FIRST_USE`]). Then clippy finds nothing in them
//!   with `-D warnings`, each crate built with and without `cfg(test)`
//!   (`--all-targets`), so what only tests use is only for tests, apart
//!   from lints that fire on what the tests do on purpose
//!   ([`ALLOWED_LINTS`]), and dead code in a test that declares what it
//!   never uses ([`UNUSED`]). The workspace has std's crate,
//!   `temper-std`, as std builds as a library; `testing`'s tests build
//!   and run against it, and a test that runs as tests
//!   (`testing/asserts`, and the shared `reach`) runs by `cargo test`, its failures
//!   compared with its `expectedFailures`, as Kotlin's `RunRust` reads
//!   them ([`cargo_test_failures`]). Skipped, with a message, without cargo
//!   or clippy, or if `temper-core`'s one dependency, `indexmap`, isn't
//!   in cargo's cache (`cargo fetch` in `temper-core` puts
//!   it there); with `TEMPER_REQUIRE_CHECKERS` set, as in CI, any of
//!   those fails it instead.

use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::{env, fs};
use temper_be_rust::{OutFile, RUST, translate};
use temper_be_testkit::{self as kit, Case, Goldens};

/// Clippy's lints that fire on what the functional tests do on purpose:
/// `zero = zero` (`self_assignment`), `1.min(3)`
/// (`unnecessary_min_or_max`), and `infinity - infinity` (`eq_op`), as in
/// Kotlin's output too; a local named `foo` (`disallowed_names`),
/// which Kotlin's output avoids only by numbering every name (`foo__3`);
/// `if d < 0 { … } else if d == 0 { … }` on a `compareTo`'s result
/// (`comparison_chain`), as Temper's `when` is written; and std's
/// `processTestCases`, whose closure takes a pair of a name and a test
/// function (`type_complexity`), which Kotlin's `#![allow(warnings)]`
/// hides; `casts/as-expr`'s `3.14` (`approx_constant`); and
/// `semantics/type-checked-locals`' `if` whose branches do the same
/// (`if_same_then_else`).
const ALLOWED_LINTS: [&str; 8] = [
    "clippy::self_assignment",
    "clippy::unnecessary_min_or_max",
    "clippy::eq_op",
    "clippy::disallowed_names",
    "clippy::comparison_chain",
    "clippy::type_complexity",
    "clippy::approx_constant",
    "clippy::if_same_then_else",
];

/// The tests that declare what they never use, on purpose: a default
/// method that every class overrides (`call-override-from-subtype`'s),
/// a field (`mutually-referencing-types`'), or parameters
/// (`bubble`'s, `errors`' functions whose errors leave them unread, and
/// `json-adapters`' decoders, which read their `ic` only to pass it to
/// another adapter, as Kotlin's `@json` writes them), so rustc's
/// `dead_code` and `unused_variables` are allowed in them. What nothing
/// reaches isn't written (`temper_tmpl::reach`), and what only tests
/// reach is `#[cfg(test)]`, so unused functions, classes, and module
/// values are no reason to be here.
const UNUSED: [&str; 5] = [
    "call-override-from-subtype",
    "mutually-referencing-types",
    "bubble",
    "errors",
    "json-adapters",
];

/// Tests, by key, that store what they never read: `functions/connected`'s
/// `Hider(@noProperty i: Int)`, which tempest's frontend makes a private
/// field, read only by the constructor, where Kotlin's makes none. Their
/// packages are linted as [`UNUSED`]'s are.
const UNUSED_KEYS: [&str; 2] = ["functions-connected", "functions-connected-no-connected"];

/// Tests, by key, whose root module's layout differs from rustfmt's, and
/// isn't checked against it yet: std's regex built structurally, calls
/// nested several deep whose last argument is a `vec!`, where rustfmt
/// stops overflowing a nested call's last argument sooner than the
/// printer does, and breaks a `let` before its value; and state machines
/// nested deep in `async` blocks, where rustfmt lays out a closure's
/// header past its width differently, and a generic type past it, as
/// `functions`' generator has; and `json-adapters`' nested calls, whose `?`
/// call rustfmt doesn't overflow there, and a `let` of a chain whose
/// first line is just 100 wide, which rustfmt breaks inside the call;
/// and `callback-changes`' nested calls ending in a `vec!` too.
const LAYOUT_GAPS: [&str; 7] = [
    "functions",
    "regex-match",
    "regex-zero-advance",
    "control-flow-async",
    "control-flow-async-waiters",
    "json-adapters",
    "callback-changes",
];

/// This crate's own programs, in `tests/programs/`, which test what's
/// particular to Rust.
const OWN: [&str; 2] = ["class-edges", "nullable-edges"];

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
        &kit::ERRORS[..],
        &kit::TYPE_VALUES[..],
        &kit::ASYNC[..],
        &kit::TESTS[..],
        &kit::WITH_STD[..],
        &kit::NET[..],
    ]
    .concat();
    let mut cases = kit::cases_with("rust", &kit::PROGRAMS, &tests);
    // A library named a Rust keyword (`regex/match`'s `match`), which
    // cargo refuses as a package's name, by its key (`regex-match`).
    for case in &mut cases {
        if temper_be_rust::names::RAW_KEYWORDS.contains(&case.library.as_str()) {
            case.library = case.key.clone();
        }
    }
    for name in OWN {
        let path = Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("tests/programs")
            .join(format!("{name}.temper.md"));
        let text = fs::read_to_string(&path).unwrap();
        // Its one log block.
        let log = text.split("```log\n").nth(1).unwrap_or_default();
        let expected = log.split("```").next().unwrap_or_default();
        let expected = expected.lines().map(str::to_string).collect();
        let file = format!("{name}.temper.md");
        cases.push(Case::inline(name, &file, text, expected));
    }
    cases
}

/// A case's generated files.
fn generate(case: &Case) -> Vec<OutFile> {
    translate(&kit::program_for(case, &RUST.rules), &RUST)
        .unwrap_or_else(|e| panic!("{}: {e}", case.key))
}

/// Whether a tool runs, as `program args… --version`.
fn runs(program: &str, args: &[&str]) -> bool {
    Command::new(program)
        .args(args)
        .arg("--version")
        .output()
        .is_ok_and(|o| o.status.success())
}

/// The host's target triple, as rustc gives it.
fn host() -> Option<String> {
    let rustc = temper_tmpl::tools::tool("RUSTC", "rustc");
    let out = Command::new(rustc).arg("-vV").output().ok()?;
    (String::from_utf8_lossy(&out.stdout).lines())
        .find_map(|l| l.strip_prefix("host: "))
        .map(str::to_string)
}

/// rustfmt's layout of a Rust file.
fn rustfmt(text: &str) -> String {
    use std::io::Write;
    let mut child = Command::new("rustfmt")
        .args(["--edition", "2021", "--emit", "stdout"])
        .stdin(std::process::Stdio::piped())
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .spawn()
        .unwrap();
    child
        .stdin
        .take()
        .unwrap()
        .write_all(text.as_bytes())
        .unwrap();
    let out = child.wait_with_output().unwrap();
    assert!(
        out.status.success(),
        "rustfmt: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    String::from_utf8(out.stdout).unwrap()
}

#[test]
fn goldens() {
    let rustfmt_found = runs("rustfmt", &[]);
    if !rustfmt_found {
        kit::missing("no rustfmt, to check the layout");
    }
    kit::with_stack(move || {
        let mut goldens = Goldens::new(env!("CARGO_MANIFEST_DIR"));
        for case in cases() {
            let files = generate(&case);
            let library = &case.library;
            let paths: Vec<&str> = files.iter().map(|f| f.path.as_str()).collect();
            assert_eq!(
                paths[..5],
                [
                    format!("{library}/Cargo.toml"),
                    format!("{library}/src/lib.rs"),
                    format!("{library}/src/main.rs"),
                    format!("{library}/src/mod.rs"),
                    format!("{library}/src/support/mod.rs"),
                ],
                "{}",
                case.key
            );
            let krate = library.replace('-', "_");
            let main = &files[2].text;
            assert!(
                main.starts_with(&format!("fn main() {{\n    {krate}::init(None)")),
                "{main}"
            );
            let core = temper_tmpl::version::current();
            assert!(
                files[0].text.contains(&format!(
                    "\n[dependencies]\ntemper-core = {{ path = \"../temper-core\", version = \"={core}\" }}\n"
                )),
                "{}",
                files[0].text
            );
            if rustfmt_found && !LAYOUT_GAPS.contains(&case.key.as_str()) {
                // Host code is copied as the library has it.
                let generated =
                    |f: &&OutFile| f.path.ends_with(".rs") && !f.path.contains("/_connected/");
                for file in files.iter().filter(generated) {
                    let formatted = rustfmt(&file.text);
                    if formatted != file.text {
                        goldens.differs(format!(
                            "{}: not as rustfmt lays it out:\n{formatted}",
                            file.path
                        ));
                    }
                }
            }
            goldens.check(&format!("{}.rs", case.key), &files[3].text);
            // `lib.rs` and `Cargo.toml` where modules or libraries show.
            if !case.sources[0].0.contains("temper-funtests/cases/")
                && (files.len() > 5 || files[0].text.contains("std"))
                || !case.libraries.is_empty()
            {
                goldens.check(&format!("{}.lib.rs", case.key), &files[1].text);
                goldens.check(&format!("{}.Cargo.toml", case.key), &files[0].text);
            }
            for file in &files[5..] {
                let rest = &file.path[library.len() + "/src/".len()..];
                let rest = (rest.strip_suffix("/mod.rs"))
                    .or_else(|| rest.strip_suffix(".rs"))
                    .unwrap_or(rest);
                goldens.check(
                    &format!("{}.{}.rs", case.key, rest.replace('/', ".")),
                    &file.text,
                );
            }
        }
        goldens.finish();
    });
}

/// A shift of a literal, `0x7F >> n`: Kotlin's backend writes
/// `127.wrapping_shr(…)`, which rustc rejects, since it can't call an
/// inherent method of `{integer}`; here the literal gets its type's
/// suffix, `127i32.wrapping_shr(…)`.
#[test]
fn literal_shifts() {
    let text = "export let lead(first: Int, n: Int): Int { first & (0x7F >> n) }\n";
    let case = Case::inline("shifts", "shifts.temper", text, Vec::new());
    kit::with_stack(move || {
        let lib = &generate(&case)[3].text;
        assert!(
            lib.contains("first & 127i32.wrapping_shr(n as u32)"),
            "{lib}"
        );
    });
}

/// A connected function that takes host code where the library has no
/// `_connected.rs` fails, naming the file, where Kotlin's output would
/// fail in rustc.
#[test]
fn missing_host_code() {
    let text = "@connected export let f(i: Int): Int { i + 1 }\n";
    let case = Case::inline("missing", "missing.temper", text, Vec::new());
    kit::with_stack(move || {
        let error = translate(&kit::program_for(&case, &RUST.rules), &RUST).unwrap_err();
        assert!(error.what.contains("no _connected.rs"), "{error}");
    });
}

/// The shared `reach` program's test parts only in `#[cfg(test)]` items
/// and statements, and what nothing reaches nowhere, as tmpl's
/// `reach::partition` splits the program; `runs_and_lints` builds and
/// lints it with and without `cfg(test)`, and runs its test.
#[test]
fn test_parts_apart() {
    let case = (cases().into_iter()).find(|c| c.key == "reach").unwrap();
    kit::with_stack(move || {
        let file = &generate(&case)[3];
        let lines = (under_cfg_test(&file.text).into_iter())
            .map(|(tests, line)| (file.path.as_str(), tests, line));
        let tests_only = [
            "for_tests",
            "test_factor",
            "TEST_FACTOR",
            "test_count",
            "TEST_COUNT",
            "TestShape",
            "TestSquare",
            "add_area",
        ];
        let unreached = [
            "unreached_function",
            "UnreachedClass",
            "UnreachedInterface",
            "unreached_value",
            "UNREACHED_VALUE",
        ];
        kit::check_test_parts_apart(lines, &tests_only, &unreached);
        // `checked`'s static is the library's, set by its init, and its
        // getter only for tests.
        let text = &file.text;
        let getter = (text.lines()).position(|l| l.starts_with("pub(crate) fn checked("));
        assert!(
            getter.is_some_and(|at| text.lines().nth(at - 1) == Some("#[cfg(test)]")),
            "{text}"
        );
    });
}

/// Each line of a rustfmt-laid Rust file, and whether it's in an item or
/// statement marked `#[cfg(test)]`: from that attribute through the line
/// at its indent that ends what follows it (`}` or `;`), past any other
/// attributes.
fn under_cfg_test(text: &str) -> Vec<(bool, &str)> {
    let mut out = Vec::new();
    let mut inside: Option<usize> = None;
    for line in text.lines() {
        let trimmed = line.trim_start();
        let indent = line.len() - trimmed.len();
        match inside {
            Some(at) => {
                out.push((true, line));
                let ends = indent == at
                    && !trimmed.starts_with("#[")
                    && (trimmed.ends_with('}') || trimmed.ends_with(';'));
                if ends {
                    inside = None;
                }
            }
            None => {
                if trimmed == "#[cfg(test)]" {
                    inside = Some(indent);
                }
                out.push((inside.is_some(), line));
            }
        }
    }
    out
}

/// What the translation doesn't cover yet says where, from the
/// innermost node with a span.
#[test]
fn not_yet_says_where() {
    let text = "export class P(public x: Float64?) {}\n\
                export let same(a: P, b: P): Boolean {\n  a.x == b.x\n}\n";
    let case = Case::inline("where", "where.temper", text, Vec::new());
    kit::with_stack(move || {
        let error = translate(&kit::program_for(&case, &RUST.rules), &RUST).unwrap_err();
        assert_eq!(error.at.as_deref(), Some("where.temper:3:3"), "{error}");
        assert!(
            error.to_string().ends_with(" at where.temper:3:3"),
            "{error}"
        );
    });
}

fn runtime() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime/temper-core")
}

/// The harness that runs `module-state` from several threads.
const THREADS: &str = "[package]
name = \"module-state-threads\"
version = \"0.0.0\"
edition = \"2021\"

[dependencies]
module-state = { path = \"../module-state\" }
";

const THREADS_MAIN: &str = "fn main() {
    let threads: Vec<_> = (0..8)
        .map(|_| {
            std::thread::spawn(|| {
                for _ in 0..1000 {
                    module_state::tick();
                    module_state::report();
                }
            })
        })
        .collect();
    for thread in threads {
        thread.join().unwrap();
    }
    println!(\"ticks {}\", module_state::ticks());
}
";

/// A harness that calls libraries with no `init` first, which they run
/// on the first read of a module-level value: `twice` reads none, so it
/// runs no init, and `sum` initializes its library whole, each module
/// after those it imports, as `magic_number` does its own.
const FIRST_USE: &str = "[package]
name = \"first-use\"
version = \"0.0.0\"
edition = \"2021\"

[dependencies]
modules = { path = \"../modules\" }
values = { path = \"../values\" }
";

/// The crate of `tests/host/interop.rs`, host code that uses
/// `async-turns` and `class-edges`.
const HOST_INTEROP: &str = "[package]
name = \"host-interop\"
version = \"0.0.0\"
edition = \"2021\"

[dependencies]
async-turns = { path = \"../async-turns\" }
class-edges = { path = \"../class-edges\" }
temper-core = { path = \"../temper-core\" }
";

const FIRST_USE_MAIN: &str = "fn main() {
    println!(\"{}\", modules::helpers::twice(2));
    println!(\"{}\", modules::sum(3));
    println!(\"{}\", values::exporter::magic_number());
    println!(\"{}\", values::times_magic(2));
}
";

/// What [`FIRST_USE`] logs.
fn first_use_expected(cases: &[Case]) -> Vec<String> {
    let log = |library: &str| {
        let case = cases.iter().find(|c| c.library == library).unwrap();
        case.expected.clone()
    };
    let mut out = vec!["4".to_string()];
    out.extend(log("modules"));
    out.push("17".into());
    out.extend(log("values"));
    out.extend(["42".to_string(), "84".to_string()]);
    out
}

/// A run of `cargo test`'s failures, as Kotlin's `cargoTestToJunitXml`
/// reads them: each failing test's name (its path's last part) and the
/// `Error: …` lines of its output, a `MessageError("…")` as its text;
/// `None` if no test binary ran.
fn cargo_test_failures(stdout: &str) -> Option<Vec<kit::TestFailure>> {
    let mut ran = false;
    let mut found: Vec<kit::TestFailure> = Vec::new();
    let mut current: Option<usize> = None;
    for line in stdout.lines() {
        if let Some(i) = current {
            if line.trim().is_empty() {
                current = None;
            } else if let Some(entry) = line.strip_prefix("Error: ") {
                let text = match entry.find("MessageError(\"") {
                    Some(at) => {
                        let raw = &entry[at + "MessageError(".len()..];
                        let raw = raw.trim_end_matches(')');
                        raw.strip_prefix('"')
                            .and_then(|r| r.strip_suffix('"'))
                            .unwrap_or(raw)
                            .replace("\\\"", "\"")
                            .replace("\\'", "'")
                            .replace("\\\\", "\\")
                    }
                    None => entry.to_string(),
                };
                let cause = &mut found[i].cause;
                if !cause.is_empty() {
                    cause.push('\n');
                }
                cause.push_str(&text);
            }
            continue;
        }
        if line.starts_with("running ") && (line.ends_with(" test") || line.ends_with(" tests")) {
            ran = true;
        } else if let Some(name) =
            (line.strip_prefix("---- ")).and_then(|l| l.strip_suffix(" stdout ----"))
        {
            let name = name.rsplit("::").next().unwrap_or(name).to_string();
            found.push(kit::TestFailure {
                name,
                cause: String::new(),
            });
            current = Some(found.len() - 1);
        }
    }
    ran.then_some(found)
}

/// Cargo, in the workspace, offline, with its own target directory.
fn cargo(root: &Path, args: &[&str]) -> Output {
    let (command, rest) = args.split_first().unwrap();
    Command::new(temper_tmpl::tools::tool("CARGO", "cargo"))
        .arg(command)
        .arg("--offline")
        .args(rest)
        .env("CARGO_TARGET_DIR", root.join("target"))
        .env_remove("RUSTFLAGS")
        .current_dir(root)
        .output()
        .unwrap()
}

#[test]
fn runs_and_lints() {
    if !runs("cargo", &[]) {
        kit::missing("no cargo");
        return;
    }
    kit::with_stack(move || {
        let root = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-rust");
        // Each library a package of its own in one workspace, so one
        // named as another (`basics`, for `types/int/basics` and
        // `types/float/basics`) is named by its key instead, or where
        // that's taken too (the `functions` program, after
        // `imports/functions`), by its key and `-program`.
        let mut cases = cases();
        let mut seen = std::collections::HashSet::new();
        for case in &mut cases {
            if !seen.insert(case.library.clone()) {
                case.library = if seen.contains(&case.key) {
                    format!("{}-program", case.key)
                } else {
                    case.key.clone()
                };
                seen.insert(case.library.clone());
            }
        }
        let mut members: Vec<String> = Vec::new();
        for case in &cases {
            for file in generate(case) {
                kit::write_if_changed(&root.join(&file.path), &file.text);
            }
            members.push(case.library.clone());
        }
        // std's crate, `temper-std`, as std builds as a library.
        for file in translate(&kit::std_program(), &RUST).unwrap_or_else(|e| panic!("std: {e}")) {
            kit::write_if_changed(&root.join(&file.path), &file.text);
        }
        members.push("std".into());
        kit::write_if_changed(&root.join("module-state-threads/Cargo.toml"), THREADS);
        kit::write_if_changed(&root.join("module-state-threads/src/main.rs"), THREADS_MAIN);
        members.push("module-state-threads".into());
        kit::write_if_changed(&root.join("first-use/Cargo.toml"), FIRST_USE);
        kit::write_if_changed(&root.join("first-use/src/main.rs"), FIRST_USE_MAIN);
        members.push("first-use".into());
        kit::write_if_changed(&root.join("host-interop/Cargo.toml"), HOST_INTEROP);
        kit::write_if_changed(
            &root.join("host-interop/src/main.rs"),
            include_str!("host/interop.rs"),
        );
        members.push("host-interop".into());
        let list: Vec<String> = members.iter().map(|m| format!("\"{m}\"")).collect();
        kit::write_if_changed(
            &root.join("Cargo.toml"),
            &format!(
                "[workspace]\nresolver = \"2\"\nmembers = [{}]\nexclude = [\"temper-core\"]\n",
                list.join(", ")
            ),
        );
        // The runtime as `temper build` writes it, naming the version
        // the libraries depend on.
        let filled = temper_tmpl::version::fill_runtime(
            temper_be_rust::runtime::FILES,
            &temper_be_rust::runtime::VERSION,
            temper_tmpl::version::current(),
        );
        for (path, text) in &filled {
            kit::write_if_changed(&root.join("temper-core").join(path), text);
        }
        if !root.join("Cargo.lock").exists() {
            // indexmap and its dependencies at the versions temper-core
            // was tested with.
            kit::write_if_changed(
                &root.join("Cargo.lock"),
                &fs::read_to_string(runtime().join("Cargo.lock")).unwrap(),
            );
        }
        // For this host only: std `net`'s `ureq` and TLS need other
        // platforms' crates elsewhere, which the cache needn't have.
        let fetch = match host() {
            Some(host) => cargo(&root, &["fetch", "--target", &host]),
            None => cargo(&root, &["fetch"]),
        };
        if !fetch.status.success() {
            kit::missing(&format!(
                "temper-core's dependencies aren't in cargo's cache (`cargo fetch` in \
                 crates/temper-be-rust/runtime/temper-core gets them):\n{}",
                String::from_utf8_lossy(&fetch.stderr)
            ));
            return;
        }
        let build = cargo(&root, &["build", "--workspace", "--quiet"]);
        assert!(
            build.status.success(),
            "cargo build:\n{}",
            String::from_utf8_lossy(&build.stderr)
        );
        let mut failures = Vec::new();
        let bin = |name: &str| {
            root.join("target/debug")
                .join(format!("{name}{}", env::consts::EXE_SUFFIX))
        };
        for case in cases.iter().filter(|c| c.tests.is_none()) {
            let name = temper_be_rust::bin_name(&case.library);
            let out = Command::new(bin(&name)).output().unwrap();
            kit::check_case(&mut failures, &name, &out, case);
        }
        let mut run = |name: &str, expected: &[String]| {
            let out = Command::new(bin(name)).output().unwrap();
            kit::check_log(&mut failures, name, &out, expected);
        };
        // Init logs as `module-state` does, on the threads' first use,
        // then they tick.
        run("module-state-threads", &kit::threads_expected(&cases));
        run("first-use", &first_use_expected(&cases));
        // `tests/host/interop.rs`, host code that uses `async-turns` the
        // standard way: it `.await`s a generated promise on a std-only
        // executor, blocks on one inside a turn, which fails clearly, and
        // loops over a generated generator with `for`, which a failed step
        // leaves done; then it builds a generic class with its builder.
        if let Some(case) = cases.iter().find(|c| c.key == "async-turns") {
            let expected: Vec<String> = (case.expected.iter().cloned())
                .chain(
                    [
                        "await 2",
                        "in a turn: true",
                        "for [0, 1, 2]",
                        "failed step",
                        "then done true",
                        "fibonacci 0,1,1,2,3,5,8,13",
                        "collatz 7,22,11,34,17,52,26,13,40,20,10,5,16,8,4,2,1",
                        "builder Ann",
                        "options Ann + Cy",
                    ]
                    .map(String::from),
                )
                .collect();
            run("host-interop", &expected);
        }
        // The test form builds and runs, against std's crate.
        let tests = cargo(&root, &["test", "--quiet", "-p", "testing"]);
        if !tests.status.success() {
            failures.push(format!(
                "cargo test -p testing:\n{}{}",
                String::from_utf8_lossy(&tests.stdout),
                String::from_utf8_lossy(&tests.stderr)
            ));
        }
        // Runs as tests, as Kotlin's `RunRust`: `cargo test`, its failures
        // compared with what's expected.
        for case in cases.iter().filter(|c| c.tests.is_some()) {
            let out = cargo(&root, &["test", "-p", &case.library]);
            let actual = cargo_test_failures(&String::from_utf8_lossy(&out.stdout));
            kit::check_tests(&mut failures, case, actual, &out, kit::test_name_words);
        }
        if runs("cargo", &["clippy"]) {
            // Most crates strictly, then those that declare what they
            // never use, allowing that.
            let mut unused: Vec<&str> = (UNUSED.iter().copied())
                .filter(|&u| cases.iter().any(|c| c.library == u))
                .collect();
            for case in cases
                .iter()
                .filter(|c| UNUSED_KEYS.contains(&c.key.as_str()))
            {
                unused.push(&case.library);
            }
            let mut strict = vec!["clippy", "--workspace", "--all-targets", "--quiet"];
            for &package in &unused {
                strict.extend(["--exclude", package]);
            }
            let mut lax = vec!["clippy", "--all-targets", "--quiet"];
            for &package in &unused {
                lax.extend(["-p", package]);
            }
            let lints = ["dead_code", "unused_variables"];
            for (mut args, extra) in [(strict, &[][..]), (lax, &lints[..])] {
                args.extend(["--", "-D", "warnings"]);
                for lint in ALLOWED_LINTS.iter().chain(extra) {
                    args.extend(["-A", lint]);
                }
                let clippy = cargo(&root, &args);
                if !clippy.status.success() {
                    failures.push(format!(
                        "clippy:\n{}",
                        String::from_utf8_lossy(&clippy.stderr)
                    ));
                }
            }
        } else {
            kit::missing("no clippy");
        }
        kit::assert_none(&failures);
    });
}
