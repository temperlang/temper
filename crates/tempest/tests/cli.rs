//! Tests of the `temper` binary's command line. What `test` reports is
//! tested in temper-frontend's `tests/library_tests.rs`.

use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

/// Runs `temper`, giving its exit code, stdout and stderr.
fn tempest(args: &[&str]) -> (i32, String, String) {
    let output = Command::new(env!("CARGO_BIN_EXE_temper"))
        .args(args)
        .output()
        .unwrap();
    let text = |b: Vec<u8>| String::from_utf8(b).unwrap();
    (
        output.status.code().unwrap(),
        text(output.stdout),
        text(output.stderr),
    )
}

#[test]
fn usage() {
    // Usage errors are 2, as before; Kotlin's are 127, kotlinx-cli's.
    assert_eq!(tempest(&["build", "-b"]).0, 2);
    assert_eq!(tempest(&["build", "-b", "cobol"]).0, 2);
    assert_eq!(tempest(&["build", "-b", "interp"]).0, 2);
    assert_eq!(tempest(&["build", "dir"]).0, 2);
    assert_eq!(tempest(&["build", "--bogus"]).0, 2);
    assert_eq!(tempest(&["build", "--logformat", "xml"]).0, 2);
    assert_eq!(tempest(&["build", "-w", "a", "-w", "b"]).0, 2);
    assert_eq!(tempest(&["build", "--no-connected=yes"]).0, 2);
    assert_eq!(tempest(&["test", "-b", "cobol"]).0, 2);
    assert_eq!(tempest(&["frobnicate"]).0, 2);
    assert_eq!(tempest(&[]).0, 2);
    assert_eq!(tempest(&["test", "a", "b"]).0, 2);
    assert_eq!(tempest(&["test", "-w", "a", "b"]).0, 2);
    assert_eq!(tempest(&["test", "--std"]).0, 2);
    assert_eq!(tempest(&["test", "--bench"]).0, 2);
    assert_eq!(tempest(&["run", "-b", "py"]).0, 2);
    assert_eq!(tempest(&["run", "--library", "a"]).0, 2);
    assert_eq!(
        tempest(&["run", "--library", "a", "-b", "py", "-b", "js"]).0,
        2
    );
    assert_eq!(tempest(&["init", "dir"]).0, 2);
    assert_eq!(tempest(&["lsp", "--stdio-ish"]).0, 2);
    assert_eq!(tempest(&["serve", "--tokens", "some"]).0, 2);
    assert_eq!(tempest(&["version", "now"]).0, 2);
    assert_eq!(tempest(&["help", "frobnicate"]).0, 2);
    // The one-backend commands are gone, and say what does their work.
    for backend in temper_driver::KNOWN {
        let (code, _, err) = tempest(&[backend, "a.temper"]);
        assert_eq!(code, 2);
        let expected = format!("`temper build -b {backend}`");
        assert!(err.contains(&expected), "{err}");
    }
}

/// `help`, `--help`, and each command's `--help` print to stdout and
/// succeed, as Kotlin's.
#[test]
fn help() {
    for args in [&["help"][..], &["--help"], &["-h"]] {
        let (code, out, _) = tempest(args);
        assert_eq!(code, 0);
        assert!(
            out.starts_with("temper, Temper's command line tool"),
            "{out}"
        );
        for command in [
            "build", "doc", "help", "init", "license", "licenses", "lsp", "repl", "run", "serve",
            "test", "version", "watch",
        ] {
            assert!(
                out.contains(&format!("\n    {command} - ")),
                "{command}: {out}"
            );
        }
    }
    let (code, out, _) = tempest(&["help", "build"]);
    assert_eq!(code, 0);
    assert!(
        out.starts_with("usage: temper build [OPTION]...\n"),
        "{out}"
    );
    for flag in [
        "-w, --workroot DIR",
        "-ign, --ignorefile FILE",
        "-o, --output-directory DIR",
        "-b, --backend BACKEND",
        "--no-connected",
        "-v, --verbose",
        "--logformat text|json",
        "--std DIR",
    ] {
        assert!(out.contains(flag), "{flag}: {out}");
    }
    // The work root as Kotlin's code finds it, which its help text
    // words the other way around (notes/kotlin-differences.md).
    let rule = "for the nearest\n            directory holding a temper.out directory, or else the\n            farthest holding a config.temper.md.";
    assert!(out.contains(rule), "{out}");
    assert_eq!(
        tempest(&["build", "--help"]),
        (0, out.clone(), String::new())
    );
    assert_eq!(tempest(&["build", "-h", "-b", "cobol"]).1, out);
    for command in [
        "test", "run", "repl", "init", "serve", "lsp", "version", "license", "licenses", "help",
    ] {
        let (code, out, _) = tempest(&["help", command]);
        assert_eq!(code, 0, "{command}");
        assert!(
            out.starts_with(&format!("usage: temper {command}")),
            "{out}"
        );
    }
}

/// `version` names the build's version, and the commit where git says,
/// marked where the tree had changes: `temper 0.7.0-alpha.0.123
/// (3f2b063, modified)`.
#[test]
fn version() {
    let expected = format!("temper {}\n", temper_driver::version::describe());
    let workspace = temper_tmpl::version::workspace();
    assert!(
        temper_driver::version::built().base >= workspace,
        "{expected}"
    );
    if let Some(commit) = temper_driver::version::commit() {
        assert!(expected.contains(&format!(" ({commit}")), "{expected}");
    }
    for flag in ["version", "--version"] {
        let (code, out, err) = tempest(&[flag]);
        assert_eq!((code, err.as_str()), (0, ""));
        assert_eq!(out, expected);
    }
}

/// `license` prints the repository's legal files, each under a heading,
/// as Kotlin's `temper license` does; `licenses`, the crates built in
/// (kept current by tests/licenses.rs).
#[test]
fn license_and_licenses() {
    let (code, out, _) = tempest(&["license"]);
    assert_eq!(code, 0);
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../..");
    for file in [
        "COPYRIGHT",
        "NOTICE",
        "LICENSE-APACHE",
        "LICENSE-MIT",
        "LICENSE-CC-BY-SA-4.0",
    ] {
        let dashes = "-".repeat(file.len() + 8);
        let heading = format!("{dashes}\n--- {file} ---\n{dashes}\n");
        assert!(out.contains(&heading), "{file}");
        let text = fs::read_to_string(root.join(file)).unwrap();
        assert!(out.contains(&text), "{file}");
    }
    let (code, out, _) = tempest(&["licenses"]);
    assert_eq!(code, 0);
    let file =
        fs::read_to_string(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("src/licenses.txt"));
    assert_eq!(out, file.unwrap());
    assert!(out.contains("\n--- serde_json "), "{out}");
}

/// What isn't available yet says so, with a pointer, and exits with 3:
/// `watch` and `doc`, `repl -b`, its backends `cppv`, `data`,
/// `java8`, and `mypyc`, and its language server's `--port`.
#[test]
fn not_yet() {
    for command in ["watch", "doc"] {
        let (code, out, err) = tempest(&[command, "-w", "."]);
        assert_eq!((code, out.as_str()), (3, ""), "{command}");
        let expected = format!("temper: {command} isn't available yet; ");
        assert!(err.starts_with(&expected), "{err}");
        assert!(err.ends_with(" (docs/tools/cli.md).\n"), "{err}");
        // Its help says the same.
        let (code, out, _) = tempest(&["help", command]);
        assert_eq!(code, 0);
        assert!(out.starts_with(&expected), "{out}");
    }
    for backend in temper_driver::NOT_YET {
        for args in [&["build", "-b", backend][..], &["test", "-b", backend]] {
            let (code, _, err) = tempest(args);
            let expected = format!("temper: the {backend} backend isn't available yet ");
            assert_eq!(code, 3, "{args:?}");
            assert!(err.starts_with(&expected), "{err}");
        }
    }
    let (code, _, err) = tempest(&["repl", "-b", "py"]);
    assert_eq!(code, 3);
    assert!(
        err.starts_with("temper repl: -b py isn't available yet"),
        "{err}"
    );
    let (code, _, err) = tempest(&["serve", "--port", "4000"]);
    assert_eq!(code, 3);
    assert!(
        err.contains("over standard input and output, not a TCP port"),
        "{err}"
    );
}

/// Whether this build of `temper` has a backend (a cargo feature).
fn built_in(backend: &str) -> bool {
    temper_driver::Registry::built_in().get(backend).is_some()
}

#[test]
fn backends_left_out() {
    for backend in temper_driver::KNOWN.into_iter().filter(|b| !built_in(b)) {
        let (code, _, err) = tempest(&["build", "-b", backend]);
        let expected = format!("temper: built without the {backend} backend\n");
        assert_eq!((code, err.as_str()), (1, expected.as_str()));
    }
}

/// Tests of a class's `==`, its method marked `@operator("==")`, in
/// `assert`: with a nullable side, either way round, and a failing one,
/// whose message can't show the class's values, which have no
/// `toString()`, so it's the plain one; and a nullable list's, which
/// has none either.
const CLASS_ASSERTS: (&str, &str) = (
    "c.temper",
    "class P(public n: Int) {\n\
     \x20 @operator(\"==\")\n\
     \x20 public eq(other: P): Boolean { n == other.n }\n\
     }\n\
     let maybeP(n: Int): P? { if (n > 0) { new P(n) } else { null } }\n\
     let names(n: Int): List<String>? { if (n > 0) { [\"a\"] } else { null } }\n\
     test(\"c\") {\n\
     \x20 let p = new P(1);\n\
     \x20 assert(p == new P(1));\n\
     \x20 assert(maybeP(1) == p);\n\
     \x20 assert(p == maybeP(1));\n\
     \x20 assert(maybeP(0) == null);\n\
     \x20 assert(names(0) == null);\n\
     }\n\
     test(\"d\") { assert(new P(1) == maybeP(2)) }\n",
);

/// What a failing test of [`CLASS_ASSERTS`] says, lowercased.
const CLASS_ASSERT_FAILED: &str = "  failed  d - expected new p(1) == maybep(2)\n";

/// A fresh directory for a test, with a library of these files beside its
/// config file, removed when the test is done.
fn with_library(test: &str, files: &[(&str, &str)], f: impl FnOnce(&str)) {
    let dir = std::env::temp_dir().join(format!("tempest-cli-{}-{test}", std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    fs::create_dir_all(&dir).unwrap();
    fs::write(
        dir.join("config.temper.md"),
        "    export let name = \"a\";\n",
    )
    .unwrap();
    for (name, text) in files {
        let path = dir.join(name);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, text).unwrap();
    }
    f(&dir.to_string_lossy());
    let _ = fs::remove_dir_all(&dir);
}

#[test]
fn test_a_library() {
    let files = [("a.temper", "test(\"a\") { assert(1 + 1 == 2) }\n")];
    with_library("plain", &files, |dir| {
        let std_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../lib/std");
        let std_dir = std_dir.to_string_lossy();
        // Only the summary, as `temper test` prints when all pass.
        let (code, out, err) = tempest(&["test", "--std", &std_dir, dir]);
        assert_eq!(err, "");
        assert_eq!(out, "Tests passed: 1 of 1\n");
        assert_eq!(code, 0);
        // With --verbose, every test's result too.
        // `-b interp`, as Kotlin's `temper test` takes it, is the same.
        let (code, out, _) = tempest(&["test", "-b", "interp", "--std", &std_dir, dir]);
        assert_eq!(out, "Tests passed: 1 of 1\n");
        assert_eq!(code, 0);
        // On a backend, it builds for it and runs the tests with the
        // language's own tools, here Python's, if there's a `python3`.
        let (code, out, err) = tempest(&["test", "-b", "py", dir]);
        if !cfg!(feature = "py") {
            assert!(
                err.contains("temper: built without the py backend"),
                "{err}"
            );
            assert_eq!(code, 1);
        } else if Command::new("python3").arg("--version").output().is_ok() {
            assert_eq!((code, out.as_str()), (0, "Tests passed: 1 of 1\n"), "{err}");
            assert!(Path::new(dir).join("temper.out/py/std").is_dir());
        }
        // A failing test fails the run, saying why.
        fs::write(
            Path::new(dir).join("b.temper"),
            "test(\"b\") { assert(1 + 1 == 3) }\n",
        )
        .unwrap();
        fs::write(Path::new(dir).join(CLASS_ASSERTS.0), CLASS_ASSERTS.1).unwrap();
        let mut runs = vec![tempest(&["test", "--std", &std_dir, dir])];
        if cfg!(feature = "py") && Command::new("python3").arg("--version").output().is_ok() {
            runs.push(tempest(&["test", "-b", "py", dir]));
        }
        for (code, out, err) in runs {
            assert!(
                out.contains("  failed  b - expected 1 + 1 == (3) not (2)"),
                "{out}{err}"
            );
            assert!(out.to_lowercase().contains(CLASS_ASSERT_FAILED), "{out}");
            assert!(out.ends_with("Tests passed: 2 of 4\n"), "{out}");
            assert_eq!(code, 1);
        }
        fs::remove_file(Path::new(dir).join("b.temper")).unwrap();
        fs::remove_file(Path::new(dir).join(CLASS_ASSERTS.0)).unwrap();
        // Each with the steps it took on the interpreter.
        let (code, out, _) = tempest(&["test", "-v", "--std", &std_dir, dir]);
        assert!(out.contains("  ok      a ("), "{out}");
        assert!(out.ends_with(" steps)\nTests passed: 1 of 1\n"), "{out}");
        assert_eq!(code, 0);
    });
}

/// Hints, advice about correct code, are said only with `-v`; editors
/// show them anyway. A warning is always said.
#[test]
fn hints_only_with_verbose() {
    let source = "let z: Int = 1;\nlet b = 2147483648;\ntest(\"a\") { assert(z is Int) }\n";
    with_library("hints", &[("a.temper", source)], |dir| {
        let hint = "a.temper:3:20: hint: Unnecessary type check to Int32";
        let warn = "a.temper:2:9: warn: Int32 value, 2147483648, out of representable bounds; \
                    add i64 at the end for an Int64\n";
        let (code, _, err) = tempest(&["test", dir]);
        assert!(!err.contains("hint:"), "{err}");
        assert!(err.contains(warn), "{err}");
        assert_eq!(code, 0);
        let (code, _, err) = tempest(&["test", "-v", dir]);
        assert!(err.contains(hint), "{err}");
        assert_eq!(code, 0);
        let (_, _, err) = tempest(&["build", "-b", "js", "-o", &format!("{dir}/out"), dir]);
        assert!(!err.contains("hint:"), "{err}");
    });
}

/// A function literal and a function reference where another library's
/// `@fun` interface is wanted: `b`'s code gets the interface's `apply`,
/// so both translate and run.
#[test]
fn another_librarys_fun_interface() {
    if !built_in("py") {
        return;
    }
    let dir = std::env::temp_dir().join(format!("tempest-cli-{}-fun", std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    let files = [
        ("a/config.temper.md", "    export let name = \"a\";\n"),
        (
            "a/a.temper",
            "export @fun interface Pred(x: Int): Boolean;\n\
             export let countIf(xs: List<Int>, p: Pred): Int {\n\
             var n = 0;\n\
             for (let x of xs) { if (p(x)) { n += 1; } }\n\
             n\n\
             }\n",
        ),
        ("b/config.temper.md", "    export let name = \"b\";\n"),
        (
            "b/b.temper",
            "let { Pred, countIf } = import(\"a\");\n\
             let isOdd(x: Int): Boolean { x % 2 != 0 }\n\
             export let one(): Int { countIf([1, 2, 3], fn (x: Int): Boolean { x > 1 }) }\n\
             export let two(): Int { let p: Pred = isOdd; countIf([1, 2, 3], p) }\n\
             test(\"counts\") { assert(one() == 2); assert(two() == 2) }\n",
        ),
    ];
    for (name, text) in files {
        let path = dir.join(name);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, text).unwrap();
    }
    let root = dir.to_string_lossy().to_string();
    let out = format!("{root}-out");
    let (code, _, err) = tempest(&["build", "-w", &root, "-b", "py", "-o", &out]);
    assert_eq!((code, err.as_str()), (0, ""));
    let _ = fs::remove_dir_all(&out);
    if Command::new("python3").arg("--version").output().is_ok() {
        let (code, out, err) = tempest(&["test", "-w", &root, "-b", "py"]);
        assert!(out.ends_with("Tests passed: 1 of 1\n"), "{out}{err}");
        assert_eq!(code, 0, "{err}");
    }
    let _ = fs::remove_dir_all(&dir);
}

/// The audit's probe (scratch/deps): `b` imports `a`, which has an
/// error. Each command reports it once, under `a`, the library it's in,
/// though settling `b` finds it again; and still fails.
#[test]
fn imported_errors_are_reported_once() {
    let dir = std::env::temp_dir().join(format!("tempest-cli-{}-deps", std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    let files = [
        ("a/config.temper.md", "    export let name = \"a\";\n"),
        (
            "a/a.temper",
            "export let one(): Int { 1 }\nlet bad: String = 5;\n",
        ),
        ("b/config.temper.md", "    export let name = \"b\";\n"),
        (
            "b/b.temper",
            "let { one } = import(\"a\");\nexport let two(): Int { one() + 1 }\n",
        ),
    ];
    for (name, text) in files {
        let path = dir.join(name);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, text).unwrap();
    }
    let root = dir.to_string_lossy().to_string();
    // Diagnostics name files with `/`, on Windows too.
    let shown = root.replace('\\', "/");
    let error = format!("{shown}/a/a.temper:2:19: error: Cannot assign to String from Int32\n");
    let error = error.as_str();
    for args in [
        &["test", "-w", &root][..],
        &["test", "-w", &root, "--library", "b"],
    ] {
        let (code, out, err) = tempest(args);
        assert_eq!((code, err.as_str()), (1, error), "{args:?}");
        assert_eq!(out, "Tests passed: 0 of 0\n");
    }
    let (code, _, err) = tempest(&["run", "-w", &root, "--library", "b", "-b", "interp"]);
    assert_eq!((code, err), (1, format!("{error}a: 1 error\n")));
    if built_in("js") {
        let out = format!("{root}-out");
        let (code, _, err) = tempest(&["build", "-w", &root, "-b", "js", "-o", &out]);
        assert_eq!((code, err), (1, format!("{error}a: 1 error\n")));
        let json = [
            "build",
            "-w",
            &root,
            "-b",
            "js",
            "-o",
            &out,
            "--logformat",
            "json",
        ];
        let (_, _, err) = tempest(&json);
        assert_eq!(
            err.matches("Cannot assign to String from Int32").count(),
            1,
            "{err}"
        );
        let _ = fs::remove_dir_all(&out);
    }
    let _ = fs::remove_dir_all(&dir);
}

/// `--fuel`: each test's steps on the interpreter. The audit's probe, a
/// loop missing its `i++` at the top level, used to log until memory ran
/// out; now it stops at its budget, its output cut short, and fails.
#[test]
fn test_fuel() {
    let files = [(
        "a.temper",
        "for (var i = 0; i < 5;) { console.log(i.toString()); }\n\
             test(\"a\") { assert(true) }\n",
    )];
    with_library("fuel", &files, |dir| {
        let started = std::time::Instant::now();
        let (code, out, err) = tempest(&["test", "--fuel", "1_000_000", dir]);
        assert!(started.elapsed().as_secs() < 30);
        assert_eq!(err, "");
        assert!(out.len() < 70_000, "{} bytes", out.len());
        assert!(
            out.ends_with(
                " more lines past 65536 bytes]\n  \
                 Panicked: ran out of steps (the top-level code may take 1000000)\n\
                 Tests passed: 0 of 1 (1 not run)\n"
            ),
            "{}",
            &out[out.len().saturating_sub(300)..]
        );
        assert_eq!(code, 1);
        fs::write(
            Path::new(dir).join("a.temper"),
            "test(\"a\") {\n  var n = 0;\n  for (var i = 0; i < 100; i++) { n += i; }\n  assert(n == 4950);\n}\n",
        )
        .unwrap();
        // A test that needs more steps than given fails; none lifts the
        // limit.
        let (code, out, _) = tempest(&["test", "--fuel", "100", dir]);
        assert!(
            out.contains("  failed  a - Panicked: ran out of steps (each test may take 100)\n"),
            "{out}"
        );
        assert_eq!(code, 1);
        for fuel in ["none", "1,000,000"] {
            let (code, out, _) = tempest(&["test", "--fuel", fuel, dir]);
            assert_eq!((code, out.as_str()), (0, "Tests passed: 1 of 1\n"));
        }
        for fuel in ["0", "-5", "lots", ""] {
            let (code, _, err) = tempest(&["test", &format!("--fuel={fuel}"), dir]);
            assert_eq!(code, 2, "{fuel}");
            assert!(
                err.starts_with(&format!(
                    "temper test: --fuel is a number of steps above 0, or none, not {fuel}\n"
                )),
                "{err}"
            );
        }
    });
}

/// Std is built in, and `--std` replaces it.
#[test]
fn built_in_std() {
    let files = [(
        "a.temper",
        "let { Date } = import(\"std/temporal\");\n\
         test(\"a\") { assert((new Date(2024, 3, 5) orelse panic()).year == 2024) }\n",
    )];
    with_library("std", &files, |dir| {
        let (code, out, err) = tempest(&["test", dir]);
        assert_eq!(err, "");
        assert_eq!(out, "Tests passed: 1 of 1\n");
        assert_eq!(code, 0);
        let (code, _, err) = tempest(&["test", "--std", dir, dir]);
        assert!(err.contains("Import of std/temporal failed"), "{err}");
        assert_eq!(code, 1);
    });
}

/// Fibonacci, from Kotlin's functional tests, as a library of its name.
fn with_fibonacci(test: &str, f: impl FnOnce(&str)) {
    let fib = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../temper-funtests/cases/algos/fibonacci/fibonacci.temper.md");
    let fib = fs::read_to_string(fib).unwrap();
    let files = [
        ("config.temper.md", "    export let name = \"fibonacci\";\n"),
        ("fibonacci.temper.md", fib.as_str()),
    ];
    with_library(test, &files, f);
}

/// `build -b BACKEND -o DIR`, which replaced `tempest BACKEND --out DIR`,
/// writes each backend's files under DIR, as Kotlin lays them out under
/// `temper.out`, and nothing in the work root.
#[test]
fn build_writes_each_backend_to_a_directory() {
    // Each backend, files it writes, and a line one of them has.
    let expected: [(&str, &[&str], &str, &str); 7] = [
        (
            "py",
            &["pyproject.toml", "fibonacci/fibonacci.py"],
            "fibonacci/fibonacci.py",
            "def _fib(i: int, /) -> int:\n",
        ),
        (
            "rust",
            &[
                "Cargo.toml",
                "src/lib.rs",
                "src/main.rs",
                "src/mod.rs",
                "src/support/mod.rs",
            ],
            "src/mod.rs",
            "pub(crate) fn fib(mut i: i32) -> i32 {\n",
        ),
        (
            "js",
            &[
                "package.json",
                "index.js",
                "fibonacci.js",
                "fibonacci.internal.js",
                "fibonacci.internal.js.map",
            ],
            "fibonacci.internal.js",
            "function fib(i) {\n",
        ),
        (
            "java",
            &[
                "pom.xml",
                "src/main/java/fibonacci/FibonacciGlobal.java",
                "src/main/java/fibonacci/FibonacciMain.java",
            ],
            "src/main/java/fibonacci/FibonacciGlobal.java",
            "    static int fib(int i) {\n",
        ),
        (
            "csharp",
            &[
                "src/Fibonacci.csproj",
                "src/FibonacciGlobal.cs",
                "src/Logging.cs",
                "program/FibonacciProgram.csproj",
                "program/Program.cs",
            ],
            "src/FibonacciGlobal.cs",
            "    internal static int Fib(int i)\n",
        ),
        (
            "cpp",
            &["init.hpp", "init.cpp", "main.cpp"],
            "init.cpp",
            "static int32_t fib(int32_t i) {\n",
        ),
        (
            "lua",
            &["init.lua", "fibonacci-dev-1.rockspec"],
            "init.lua",
            "local function fib(i)\n",
        ),
    ];
    with_fibonacci("out-dir", |dir| {
        let out = std::env::temp_dir().join(format!("tempest-cli-{}-out", std::process::id()));
        let _ = fs::remove_dir_all(&out);
        for (backend, files, file, line) in expected {
            if !built_in(backend) {
                continue;
            }
            let out_dir = out.to_string_lossy();
            let (code, stdout, err) = tempest(&["build", "-w", dir, "-b", backend, "-o", &out_dir]);
            assert_eq!((code, stdout.as_str()), (0, ""), "{backend}: {err}");
            let package = out.join(backend).join("fibonacci");
            for file in files {
                assert!(package.join(file).is_file(), "{backend}: {file}");
            }
            let text = fs::read_to_string(package.join(file)).unwrap();
            assert!(text.contains(line), "{backend}: {text}");
            assert!(out.join(backend).join("temper-core").is_dir(), "{backend}");
        }
        assert!(!Path::new(dir).join("temper.out").exists());
        // JS's source map names the source by a path that leads to it.
        if built_in("js") {
            let package = out.join("js/fibonacci");
            let module = fs::read_to_string(package.join("fibonacci.internal.js")).unwrap();
            assert!(module.ends_with("\n//# sourceMappingURL=fibonacci.internal.js.map\n"));
            let map = fs::read_to_string(package.join("fibonacci.internal.js.map")).unwrap();
            let source = map.split("\"sources\":[\"").nth(1).unwrap();
            let source = &source[..source.find('"').unwrap()];
            assert!(source.ends_with("/fibonacci.temper.md"), "{map}");
            assert_eq!(
                fs::canonicalize(package.join(source)).unwrap(),
                fs::canonicalize(Path::new(dir).join("fibonacci.temper.md")).unwrap()
            );
        }
        let _ = fs::remove_dir_all(&out);
    });
}

/// `temper build` writes each library under `temper.out`, laid out as
/// Kotlin's, with the runtime beside it, and says what it can't build.
#[test]
#[cfg(all(feature = "py", feature = "lua", feature = "cpp"))]
fn build_writes_temper_out() {
    let fib = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../temper-funtests/cases/algos/fibonacci/fibonacci.temper.md");
    let files = [
        ("fib.temper.md", fs::read_to_string(fib).unwrap()),
        (
            "other/config.temper.md",
            "# Other\n\n    export let name = \"other\";\n    export let version = \"1.2.0\";\n"
                .into(),
        ),
        ("other/o.temper", "export let o = 1;\n".into()),
        (
            "user/config.temper.md",
            "# User\n\n    export let name = \"user\";\n".into(),
        ),
        (
            "user/u.temper",
            "let { o } = import(\"other\");\nconsole.log(o.toString());\n".into(),
        ),
        (
            "bad/config.temper.md",
            "# Bad\n\n    export let name = \"bad\";\n".into(),
        ),
        (
            "bad/b.temper",
            "export let f(var x: Int = 1): Int { x }\n".into(),
        ),
    ];
    let files: Vec<(&str, &str)> = files.iter().map(|(p, t)| (*p, t.as_str())).collect();
    with_library("build", &files, |dir| {
        let (code, out, err) = tempest(&[
            "build", "-w", dir, "-b", "py", "-b", "lua", "-b", "cpp", "-v",
        ]);
        // `a`, the root library, holds `fib`; `other` builds; `user`
        // imports `other`, naming it as a dependency; `bad` has a `var`
        // parameter's default, which tmpl can't translate yet.
        // With `-v`, every gap in tmpl first, then each backend's line.
        assert_eq!(code, 1, "{out}{err}");
        let lines: Vec<&str> = err.lines().collect();
        assert_eq!(lines.len(), 4, "{err}");
        assert!(
            lines[0].starts_with("bad: not yet in tmpl: a var parameter's default at "),
            "{err}"
        );
        for (line, backend) in lines[1..].iter().zip(["py", "lua", "cpp"]) {
            let start = format!("bad: {backend}: not yet in tmpl: a var parameter's default at ");
            assert!(line.starts_with(&start), "{err}");
        }
        let out_dir = Path::new(dir).join("temper.out");
        for file in [
            "py/a/pyproject.toml",
            "py/a/a/a.py",
            "py/other/pyproject.toml",
            "py/temper-core/temper_core/__init__.py",
            "lua/a/init.lua",
            "lua/other/other-1.2.0-1.rockspec",
            "cpp/a/CMakeLists.txt",
            "cpp/temper-core/core.hpp",
        ] {
            assert!(out_dir.join(file).is_file(), "{file}");
        }
        // The runtime's rockspec, named for the build's version.
        let rock = temper_driver::version::built().spell(temper_tmpl::version::Spelling::Rockspec);
        let rockspec = out_dir.join(format!("lua/temper-core/temper-core-{rock}.rockspec"));
        let text = fs::read_to_string(&rockspec).unwrap();
        assert!(text.contains(&format!("version = \"{rock}\"")), "{text}");
        let pyproject = fs::read_to_string(out_dir.join("py/other/pyproject.toml")).unwrap();
        assert!(pyproject.contains("version = \"1.2.0\"\n"), "{pyproject}");
        let pyproject = fs::read_to_string(out_dir.join("py/a/pyproject.toml")).unwrap();
        assert!(pyproject.contains("version = \"0.0.1\"\n"), "{pyproject}");
        let pyproject = fs::read_to_string(out_dir.join("py/user/pyproject.toml")).unwrap();
        assert!(pyproject.contains("\"other==1.2.0\""), "{pyproject}");
        let user = fs::read_to_string(out_dir.join("py/user/user/user.py")).unwrap();
        assert!(user.contains("from other import o as _o\n"), "{user}");
        // A file left from before is gone.
        fs::write(out_dir.join("py/a/stale.py"), "").unwrap();
        let (code, _, _) = tempest(&["build", "-w", dir, "-b", "py"]);
        assert_eq!(code, 1);
        assert!(!out_dir.join("py/a/stale.py").exists());
    });
}

/// A library's host code goes with it: Python takes `__connected__.py`
/// for a connected function, or its Temper body under `--no-connected`.
#[test]
#[cfg(feature = "py")]
fn build_reads_host_code() {
    let files = [
        (
            "c.temper",
            "@connected([\"py\"])\nexport let twice(i: Int): Int { i * 2 + 1000 }\n",
        ),
        (
            "__connected__.py",
            "class _connected:\n    def twice(i: int) -> int:\n        return i * 2\n",
        ),
    ];
    with_library("build-host", &files, |dir| {
        let out = Path::new(dir).join("temper.out/py/a/a/a.py");
        let (code, _, err) = tempest(&["build", "-w", dir, "-b", "py"]);
        assert_eq!(code, 0, "{err}");
        let text = fs::read_to_string(&out).unwrap();
        assert!(text.contains("class _connected:"), "{text}");
        assert!(text.contains("return _connected.twice(i)"), "{text}");
        let (code, _, err) = tempest(&["build", "-w", dir, "-b", "py", "--no-connected"]);
        assert_eq!(code, 0, "{err}");
        let text = fs::read_to_string(&out).unwrap();
        assert!(!text.contains("return _connected.twice(i)"), "{text}");
    });
}

/// Two libraries of one name, or none, fail the build.
#[test]
fn build_needs_libraries_with_names_of_their_own() {
    let files = [("b/config.temper.md", "    export let name = \"a\";\n")];
    with_library("build-names", &files, |dir| {
        let (code, _, err) = tempest(&["build", "-w", dir]);
        assert_eq!(code, 1);
        assert_eq!(err, format!("Two libraries are named a under {dir}\n"));
    });
    let empty = std::env::temp_dir().join(format!("tempest-cli-{}-empty", std::process::id()));
    fs::create_dir_all(&empty).unwrap();
    let (code, _, err) = tempest(&["build", "-w", &empty.to_string_lossy()]);
    assert_eq!(code, 1);
    assert!(err.starts_with("No Temper library"), "{err}");
    let _ = fs::remove_dir_all(&empty);
}

/// Each backend's config class names its packages, whichever backends
/// a build is for; flat keys (`pyName`) are ordinary values.
#[test]
#[cfg(all(
    feature = "csharp",
    feature = "java",
    feature = "js",
    feature = "py",
    feature = "rust"
))]
fn typed_config_names_packages() {
    let config = "# Geo

    export let name = \"geo\";
    export let csharp = { class: CSharpConfig, rootNamespace: \"GeometryNet\" };
    export let java = {
      class: JavaConfig,
      package: \"com.example.geo\",

The group and artifact, for Maven:

      group: \"com.example\",
      artifact: \"geo-java\",
    };
    export let js = { class: JsConfig, name: \"@example/geo\" };
    export let py = { class: PyConfig, name: \"geo-py\" };
    export let rust = { class: RustConfig, name: \"geo-rs\" };
    export let cpp = { class: CppConfig };
    export let lua = { class: LuaConfig };
    export let pyName = \"flat-py\";
";
    let files = [
        ("config.temper.md", config),
        ("g.temper", "export let g = 1;\n"),
    ];
    with_library("typed-config", &files, |dir| {
        let (code, out, err) = tempest(&["build", "-w", dir]);
        assert_eq!((code, out.as_str(), err.as_str()), (0, "", ""));
        let out = Path::new(dir).join("temper.out");
        let read = |path: &str| fs::read_to_string(out.join(path)).unwrap();
        assert!(read("py/geo/pyproject.toml").contains("name = \"geo-py\"\n"));
        assert!(read("js/geo/package.json").contains("\"name\": \"@example/geo\",\n"));
        assert!(read("rust/geo/Cargo.toml").contains("name = \"geo-rs\"\n"));
        let pom = read("java/geo/pom.xml");
        assert!(
            pom.contains("  <groupId>com.example</groupId>\n  <artifactId>geo-java</artifactId>\n"),
            "{pom}"
        );
        assert!(
            out.join("java/geo/src/main/java/com/example/geo/GeoGlobal.java")
                .is_file()
        );
        assert!(out.join("csharp/geo/src/GeometryNet.csproj").is_file());
    });
}

/// A typo or a wrong type in a backend's config class is an error, at
/// its place in the config file, for `build` and `test` alike.
// Config classes come from the backends, so these need them built in.
#[cfg(all(
    feature = "csharp",
    feature = "java",
    feature = "js",
    feature = "py",
    feature = "rust"
))]
#[test]
fn typed_config_errors() {
    let config = "    export let name = \"a\";
    export let java = { class: JavaConfig, pacakge: \"com.example\" };
    export let py = { class: PyConfig, name: 3 };
    export let js = { class: JsConf, name: \"j\" };
    export let rs = { class: RustConfig, name: \"r\" };
    export let csharp = \"Example\";
    export let lua: CppConfig = {};
    export let cpp: CppConfig = { class: LuaConfig };
    export let rust: RustConfig = { dependencies: [\"roaring\"] };
    export let java: JavaConfg = {};
    export let csharp: CSharpConfig = { dependencies: [\"Foo:$(GITHUB_TOKEN)\"] };
    export let java: JavaConfig = { dependencies: [\"g:a:1</version>\"] };
    export let js: JsConfig = { dependencies: [\"x@git+https://evil/x.git\"] };
    export let py: PyConfig = { dependencies: [\"--index-url=https://evil/simple==1\"] };
    export let rust: RustConfig = { dependencies: [\"x@1\\\", path = \\\"/x\"] };
";
    let files = [
        ("config.temper.md", config),
        ("a.temper", "export let a = 1;\n"),
    ];
    with_library("typed-config-errors", &files, |dir| {
        let path = format!("{dir}/config.temper.md");
        let expected = [
            format!(
                "{path}:2:44: error: JavaConfig has no property `pacakge`; did you mean \
                 `package`?\n"
            ),
            format!("{path}:3:46: error: PyConfig's `name` is a String, not an Int\n"),
            format!("{path}:4:30: error: `js` is for JsConfig, not `JsConf`\n"),
            format!("{path}:5:16: error: RustConfig goes in `export let rust`, not `rs`\n"),
            format!(
                "{path}:6:16: error: `csharp` is for CSharpConfig, as \
                 `export let csharp: CSharpConfig = {{ … }}`\n"
            ),
            format!("{path}:7:16: error: CppConfig goes in `export let cpp`, not `lua`\n"),
            format!(
                "{path}:8:42: error: `class: LuaConfig` disagrees with the declared type, \
                 `CppConfig`\n"
            ),
            format!(
                "{path}:9:52: error: RustConfig's `dependencies` are each `name@version`, \
                 not `roaring`\n"
            ),
            format!("{path}:10:22: error: `java` is for JavaConfig, not `JavaConfg`\n"),
            format!(
                "{path}:11:56: error: CSharpConfig's `dependencies` are each `Name:version`, \
                 not `Foo:$(GITHUB_TOKEN)`\n"
            ),
            format!(
                "{path}:12:52: error: JavaConfig's `dependencies` are each \
                 `group:artifact:version`, not `g:a:1</version>`\n"
            ),
            format!("{path}:12:16: error: `java` is set twice; set each config class once\n"),
            format!(
                "{path}:13:48: error: JsConfig's `dependencies` are each `name@version`, \
                 not `x@git+https://evil/x.git`\n"
            ),
            format!(
                "{path}:14:48: error: PyConfig's `dependencies` are each `name==version`, \
                 not `--index-url=https://evil/simple==1`\n"
            ),
            format!(
                "{path}:15:52: error: RustConfig's `dependencies` are each `name@version`, \
                 not `x@1\", path = \"/x`\n"
            ),
        ];
        for command in ["build", "test"] {
            let (code, _, err) = tempest(&[command, "-w", dir]);
            assert_eq!(code, 1, "{command}: {err}");
            for line in &expected {
                assert!(err.contains(line.as_str()), "{command}: {line}{err}");
            }
        }
    });
}

/// Config names that would leave `temper.out`, or break out of a package
/// file's markup, are config errors, and nothing is written or removed:
/// names reach paths (`ROOT.csproj`, `node_modules/NAME`) and package
/// files, so each backend allows only its tools' spellings.
// Config classes come from the backends, so these need them built in.
#[cfg(all(
    feature = "csharp",
    feature = "java",
    feature = "js",
    feature = "py",
    feature = "rust"
))]
#[test]
fn hostile_config_names_are_errors() {
    let config = "    export let name = \"a\";
    export let csharp: CSharpConfig = { rootNamespace: \"../../../escape\" };
    export let js: JsConfig = { name: \"/tmp\" };
    export let java: JavaConfig = { package: \"../p\", group: \"g/h\", artifact: \"a<b>\" };
    export let py: PyConfig = { name: \"../../escape\" };
    export let rust: RustConfig = { name: \"..\" };
    export let csharp: CSharpConfig = { rootNamespace: \"A</RootNamespace><Import Project=x/>\" };
";
    let files = [
        ("config.temper.md", config),
        ("a.temper", "export let a = 1;\n"),
    ];
    with_library("hostile-config-names", &files, |dir| {
        let path = format!("{dir}/config.temper.md");
        let expected = [
            "2:56: error: CSharpConfig's `rootNamespace` is a C# namespace, as \
             `Example.Geometry`, not `../../../escape`",
            "3:39: error: JsConfig's `name` is an npm package's name, as `geometry` or \
             `@example/geometry`, not `/tmp`",
            "4:46: error: JavaConfig's `package` is a Java package, as \
             `com.example.geometry`, not `../p`",
            "4:61: error: JavaConfig's `group` is a Maven ID, as `com.example`, not `g/h`",
            "4:78: error: JavaConfig's `artifact` is a Maven ID, as `geometry`, not `a<b>`",
            "5:39: error: PyConfig's `name` is a PyPI project's name, as `geometry-py`, \
             not `../../escape`",
            "6:43: error: RustConfig's `name` is a crate's name, as `geometry-rs`, not `..`",
            "7:56: error: CSharpConfig's `rootNamespace` is a C# namespace, as \
             `Example.Geometry`, not `A</RootNamespace><Import Project=x/>`",
        ];
        let before = |d: &Path| {
            let mut names: Vec<_> = (fs::read_dir(d).unwrap().flatten())
                .map(|e| e.file_name())
                .collect();
            names.sort();
            names
        };
        let (here, parent) = (Path::new(dir), Path::new(dir).parent().unwrap());
        let in_dir = before(here);
        for command in ["build", "test"] {
            let (code, _, err) = tempest(&[command, "-w", dir]);
            assert_eq!(code, 1, "{command}: {err}");
            for line in &expected {
                let line = format!("{path}:{line}\n");
                assert!(err.contains(&line), "{command}: {line}{err}");
            }
            let mut now = before(here);
            now.retain(|n| n != "temper.out");
            assert_eq!(now, in_dir, "{command}");
            let escaped =
                |d: &Path| (before(d).iter()).any(|n| n.to_string_lossy().starts_with("escape"));
            assert!(!escaped(parent) && !escaped(here), "{command}");
            let mut written = Vec::new();
            files_under(&here.join("temper.out"), &mut written);
            assert!(
                !written
                    .iter()
                    .any(|p| p.contains("escape") || p.contains("RootNamespace")),
                "{command}: {written:?}"
            );
        }
    });
}

/// A version that isn't SemVer, or metadata holding control characters,
/// is a config error, as both go into package files (a library's version
/// into those of each library using it too), for `build` and `test`
/// alike.
#[test]
fn hostile_versions_and_metadata_are_errors() {
    let config = "    export let name = \"a\";
    export let version = \"0.1.0\\\"<x>'\\n\";
    export let description = \"ok\\nnext line\";
    export let authors = \"A\\u{1}\";
";
    let files = [
        ("config.temper.md", config),
        ("a.temper", "export let a = 1;\n"),
    ];
    with_library("hostile-versions", &files, |dir| {
        let path = format!("{dir}/config.temper.md");
        let expected = [
            format!(
                "{path}:2:16: error: `version` is a version, as `1.2.3` or `1.2.3-beta.1`, \
                 not `0.1.0\\\"<x>\\'\\n`\n"
            ),
            format!(
                "{path}:4:16: error: `authors` holds a control character, U+0001; only tabs \
                 and line breaks may be in config text\n"
            ),
        ];
        for command in ["build", "test"] {
            let (code, _, err) = tempest(&[command, "-w", dir]);
            assert_eq!(code, 1, "{command}: {err}");
            for line in &expected {
                assert!(err.contains(line.as_str()), "{command}: {line}{err}");
            }
            assert!(!err.contains("description"), "{command}: {err}");
        }
    });
}

/// Every file's path under a directory, if it's there.
#[allow(dead_code)] // Unless every backend is left out.
fn files_under(dir: &Path, out: &mut Vec<String>) {
    for entry in fs::read_dir(dir).into_iter().flatten().flatten() {
        let path = entry.path();
        match path.is_dir() {
            true => files_under(&path, out),
            false => out.push(path.to_string_lossy().into_owned()),
        }
    }
}

/// The annotated form, `export let java: JavaConfig = { … }`, whose
/// object takes its class from the declared type, and each backend's
/// `dependencies`, the packages host code uses, in its package files as
/// Kotlin writes them.
#[test]
#[cfg(all(
    feature = "csharp",
    feature = "java",
    feature = "js",
    feature = "py",
    feature = "rust"
))]
fn config_dependencies_in_package_files() {
    let config = "    export let name = \"deps\";
    export let csharp: CSharpConfig = { dependencies: [\"Roaring.Net:1.4.1\"] };
    export let java: JavaConfig = {
      package: \"com.example.deps\",
      dependencies: [\"org.roaringbitmap:RoaringBitmap:1.6.20\"],
    };
    export let js: JsConfig = { dependencies: [\"roaring-wasm@1.1.0\", \"@scope/x@^2.0.0\"] };
    export let py: PyConfig = { name: \"deps-py\", dependencies: [\"pyroaring==1.1.0\"] };
    export let rust: RustConfig = { class: RustConfig, dependencies: [\"roaring@0.11.1\"] };
";
    let files = [
        ("config.temper.md", config),
        ("d.temper", "export let d = 1;\n"),
    ];
    with_library("config-dependencies", &files, |dir| {
        let (code, out, err) = tempest(&["build", "-w", dir]);
        assert_eq!((code, out.as_str(), err.as_str()), (0, "", ""));
        let out = Path::new(dir).join("temper.out");
        let read = |path: &str| fs::read_to_string(out.join(path)).unwrap();
        let pyproject = read("py/deps/pyproject.toml");
        assert!(
            pyproject.contains("    \"pyroaring==1.1.0\",\n"),
            "{pyproject}"
        );
        let package = read("js/deps/package.json");
        assert!(
            package.contains(
                "        \"@scope/x\": \"^2.0.0\",\n        \"roaring-wasm\": \"1.1.0\"\n"
            ),
            "{package}"
        );
        let cargo = read("rust/deps/Cargo.toml");
        assert!(
            cargo.contains("\nroaring = { version = \"=0.11.1\" }\n"),
            "{cargo}"
        );
        let pom = read("java/deps/pom.xml");
        let dependency = "    <dependency>
      <groupId>org.roaringbitmap</groupId>
      <artifactId>RoaringBitmap</artifactId>
      <version>1.6.20</version>
      <type>jar</type>
      <scope>compile</scope>
    </dependency>
";
        assert!(pom.contains(dependency), "{pom}");
        let csproj = read("csharp/deps/src/Deps.csproj");
        let reference = "  <ItemGroup>
    <PackageReference Include=\"Roaring.Net\" Version=\"1.4.1\"/>
  </ItemGroup>
";
        assert!(csproj.contains(reference), "{csproj}");
    });
}

/// A declaration named like a file a backend generates is an error, not
/// a silent replacement of that file: a class named like the module's
/// class (`AGlobal`, on Java and C#), whose functions it would drop, or
/// like C#'s `Logging.cs`; and so are two classes named alike but for
/// case, which a case-insensitive file system takes as one file.
#[test]
fn generated_name_clashes_are_errors() {
    let cases = [
        (
            "java",
            "export class AGlobal {}\n",
            "two generated files would be a/src/main/java/a/AGlobal.java",
        ),
        (
            "csharp",
            "export class AGlobal {}\n",
            "two generated files would be a/src/AGlobal.cs",
        ),
        (
            "csharp",
            "export class Logging {}\n",
            "two generated files would be a/src/Logging.cs",
        ),
        (
            "java",
            "export class Point {}\nexport class POINT {}\n",
            "generated files a/src/main/java/a/Point.java and a/src/main/java/a/POINT.java would be one file",
        ),
    ];
    for (i, (backend, class, expected)) in cases.into_iter().enumerate() {
        if !built_in(backend) {
            continue;
        }
        let text = format!("export let a(): Int {{ 1 }}\n{class}");
        let files = [("a.temper", text.as_str())];
        with_library(&format!("clash-{i}"), &files, |dir| {
            let out = Path::new(dir).join("out");
            let (code, _, err) = tempest(&[
                "build",
                "-w",
                dir,
                "-b",
                backend,
                "-o",
                &out.to_string_lossy(),
            ]);
            assert_eq!(code, 1, "{backend} {class}: {err}");
            assert!(err.contains(&format!("a: {backend}: {expected}")), "{err}");
        });
    }
}

/// A library named like a directory that a backend's tools make beside
/// the libraries' (Cargo's `target`, Node's `node_modules`), which
/// building would empty and the tools would write into, is an error on
/// that backend.
#[test]
fn libraries_named_like_tool_directories_are_errors() {
    for (backend, name) in [
        ("rust", "target"),
        ("js", "node_modules"),
        ("lua", "luaunit"),
    ] {
        if !built_in(backend) {
            continue;
        }
        let config = format!("    export let name = \"{name}\";\n");
        let files = [
            ("config.temper.md", config.as_str()),
            ("a.temper", "export let a = 1;\n"),
        ];
        with_library(&format!("tool-dir-{backend}"), &files, |dir| {
            let (code, _, err) = tempest(&["build", "-w", dir, "-b", backend]);
            assert_eq!(code, 1, "{backend}: {err}");
            let said = format!("would be in {name}, which the backend's tools use");
            assert!(err.contains(&said), "{backend}: {err}");
        });
    }
}

/// A C++ library's generated CMake project builds and runs, with g++,
/// where CMake is installed (skipped, with a message, otherwise; required
/// with `TEMPER_REQUIRE_CHECKERS`).
#[test]
#[cfg(feature = "cpp")]
fn cmake_builds_a_library() {
    let cmake_runs = Command::new("cmake")
        .arg("--version")
        .output()
        .is_ok_and(|o| o.status.success());
    if !cmake_runs {
        assert!(
            std::env::var_os("TEMPER_REQUIRE_CHECKERS").is_none(),
            "no cmake (needed with TEMPER_REQUIRE_CHECKERS)"
        );
        eprintln!("skipped: no cmake");
        return;
    }
    let fib = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../temper-funtests/cases/algos/fibonacci/fibonacci.temper.md");
    let files = [("fib.temper.md", fs::read_to_string(fib).unwrap())];
    let files: Vec<(&str, &str)> = files.iter().map(|(p, t)| (*p, t.as_str())).collect();
    with_library("cmake", &files, |dir| {
        let (code, _, err) = tempest(&["build", "-w", dir, "-b", "cpp"]);
        assert_eq!(code, 0, "{err}");
        let source = Path::new(dir).join("temper.out/cpp/a");
        let build = Path::new(dir).join("cmake-build");
        let run = |args: &[&std::ffi::OsStr]| {
            let out = Command::new("cmake").args(args).output().unwrap();
            assert!(
                out.status.success(),
                "cmake {args:?}:\n{}{}",
                String::from_utf8_lossy(&out.stdout),
                String::from_utf8_lossy(&out.stderr)
            );
        };
        run(&[
            "-S".as_ref(),
            source.as_os_str(),
            "-B".as_ref(),
            build.as_os_str(),
        ]);
        run(&[
            "--build".as_ref(),
            build.as_os_str(),
            "-j".as_ref(),
            "2".as_ref(),
        ]);
        let out = Command::new(build.join("a-main")).output().unwrap();
        let expected = temper_funtests::test("algos/fibonacci")
            .unwrap()
            .expected_log("cpp");
        let stdout = String::from_utf8_lossy(&out.stdout);
        assert_eq!(stdout.lines().collect::<Vec<_>>(), expected);
    });
}

/// `temper test -b cpp` builds a library's tests with CMake and runs
/// them, reading the JUnit XML they write, where CMake is installed
/// (skipped otherwise; required with `TEMPER_REQUIRE_CHECKERS`).
#[test]
#[cfg(feature = "cpp")]
fn test_a_library_in_cpp() {
    let cmake_runs = Command::new("cmake")
        .arg("--version")
        .output()
        .is_ok_and(|o| o.status.success());
    if !cmake_runs {
        assert!(
            std::env::var_os("TEMPER_REQUIRE_CHECKERS").is_none(),
            "no cmake (needed with TEMPER_REQUIRE_CHECKERS)"
        );
        eprintln!("skipped: no cmake");
        return;
    }
    let files = [
        ("a.temper", "test(\"a\") { assert(1 + 1 == 2) }\n"),
        ("b.temper", "test(\"b\") { assert(1 + 1 == 3) }\n"),
        CLASS_ASSERTS,
    ];
    with_library("cpp-tests", &files, |dir| {
        let (code, out, err) = tempest(&["test", "-b", "cpp", dir]);
        assert!(out.contains("  failed  b - expected"), "{out}{err}");
        assert!(
            out.to_lowercase().contains(CLASS_ASSERT_FAILED),
            "{out}{err}"
        );
        assert!(out.ends_with("Tests passed: 2 of 4\n"), "{out}{err}");
        assert_eq!(code, 1);
        assert!(
            Path::new(dir)
                .join("temper.out/cpp/a/test-results.xml")
                .is_file()
        );
    });
}

/// `temper test -b BACKEND` on a small library, two tests passing and
/// two failing, one of them [`CLASS_ASSERTS`]'s, on every backend whose
/// tools run here: it builds, runs
/// the language's own test tools, and reads their results, so each
/// backend's runner (its crate's `tools` module) is tested on every push without the
/// real libraries, which run nightly. A backend whose tools are missing is
/// skipped, with a message, unless `TEMPER_REQUIRE_CHECKERS` is set.
#[test]
fn test_a_library_on_each_backend() {
    let files = [
        ("a.temper", "test(\"a\") { assert(1 + 1 == 2) }\n"),
        ("b.temper", "test(\"b\") { assert(1 + 1 == 3) }\n"),
        CLASS_ASSERTS,
    ];
    for backend in ["js", "lua", "rust", "java", "csharp"] {
        if !built_in(backend) {
            continue;
        }
        with_library(&format!("each-{backend}"), &files, |dir| {
            let (code, out, err) = tempest(&["test", "-b", backend, dir]);
            if out.contains("couldn't run") || err.contains("couldn't run") {
                assert!(
                    std::env::var_os("TEMPER_REQUIRE_CHECKERS").is_none(),
                    "{backend}'s tools are missing (needed with TEMPER_REQUIRE_CHECKERS):\n{out}{err}"
                );
                eprintln!("skipped {backend}: its tools are missing");
                return;
            }
            // As each tool names it: `b`, or C#'s method `B`.
            let failed = out.to_lowercase().contains("  failed  b - expected");
            assert!(failed, "{backend}:\n{out}{err}");
            let failed = out.to_lowercase().contains(CLASS_ASSERT_FAILED);
            assert!(failed, "{backend}:\n{out}{err}");
            assert!(
                out.ends_with("Tests passed: 2 of 4\n"),
                "{backend}:\n{out}{err}"
            );
            assert_eq!(code, 1, "{backend}");
        });
    }
}

/// Kotlin's `connecteds` test: a connected class, `Bitset`, with no
/// Temper implementation, whose host code wraps a package that the
/// config's `dependencies` list for each backend (a Roaring bitmap),
/// tested with `temper test -b BACKEND`, which fetches the real package
/// with the language's own tools. The generated code defines `Bitset`
/// once, `defines` as written in the backend's language, in host code
/// that uses the package, `uses`. They fetch from five registries, so
/// they run only with `TEMPER_NETWORK_TESTS` set, as nightly.yml's
/// `connecteds` job sets it, not on every push; there, a backend whose
/// tools are missing is skipped, with a message, unless
/// `TEMPER_REQUIRE_CHECKERS` is set.
#[allow(dead_code)] // Unless every backend is left out.
fn connecteds(backend: &str, defines: &str, uses: &str) {
    let files = connecteds_files();
    let files: Vec<(&str, &str)> = (files.iter())
        .map(|(n, t)| (n.as_str(), t.as_str()))
        .collect();
    let skip = |why: &str| {
        assert!(
            std::env::var_os("TEMPER_REQUIRE_CHECKERS").is_none(),
            "connecteds on {backend}: {why} (needed with TEMPER_REQUIRE_CHECKERS)"
        );
        eprintln!("skipped connecteds on {backend}: {why}");
    };
    if std::env::var_os("TEMPER_NETWORK_TESTS").is_none() {
        eprintln!("skipped connecteds on {backend}: set TEMPER_NETWORK_TESTS to fetch packages");
        return;
    }
    with_library(&format!("connecteds-{backend}"), &files, |dir| {
        let (code, out, err) = tempest(&["test", "-b", backend, dir]);
        if out.contains("couldn't run") || err.contains("couldn't run") {
            return skip(&format!("its tools are missing\n{out}{err}"));
        }
        assert!(
            out.ends_with("Tests passed: 1 of 1\n"),
            "{backend}:\n{out}{err}"
        );
        assert_eq!(code, 0, "{backend}:\n{out}{err}");
        let out_dir = Path::new(dir).join("temper.out").join(backend);
        let generated = sources(&out_dir.join("connecteds"));
        let defining: Vec<_> = (generated.iter())
            .flat_map(|(path, text)| text.matches(defines).map(move |_| (path, text)))
            .collect();
        let [(path, text)] = defining[..] else {
            let paths: Vec<_> = defining.iter().map(|(p, _)| p).collect();
            panic!("{backend}: {defines:?} once, not in {paths:?}");
        };
        assert!(
            text.contains(uses),
            "{backend}: {}:\n{text}",
            path.display()
        );
    });
}

/// The `connecteds` test's files, by name, and their text.
fn connecteds_files() -> Vec<(String, String)> {
    let dir = concat!(env!("CARGO_MANIFEST_DIR"), "/tests/connecteds");
    let mut files = Vec::new();
    for entry in fs::read_dir(dir).unwrap() {
        let path = entry.unwrap().path();
        let name = path.file_name().unwrap().to_string_lossy().to_string();
        files.push((name, fs::read_to_string(&path).unwrap()));
    }
    files
}

/// The files under a backend's output for the `connecteds` library, but
/// not its packages and builds (`node_modules`, `target`, `bin`, `obj`).
#[allow(dead_code)] // Unless every backend is left out.
fn sources(dir: &Path) -> Vec<(PathBuf, String)> {
    let mut found = Vec::new();
    for entry in fs::read_dir(dir).unwrap() {
        let path = entry.unwrap().path();
        let name = path.file_name().unwrap().to_string_lossy();
        if path.is_dir() {
            if !["node_modules", "target", "bin", "obj"].contains(&name.as_ref()) {
                found.extend(sources(&path));
            }
        } else if let Ok(text) = fs::read_to_string(&path) {
            found.push((path, text));
        }
    }
    found
}

/// The `connecteds` test in the interpreter, where `Bitset` has no
/// implementation: its test panics, naming the method, as Kotlin's
/// `AbstractPanic` does.
#[test]
fn connecteds_interpreter() {
    let files = connecteds_files();
    let files: Vec<(&str, &str)> = (files.iter())
        .map(|(n, t)| (n.as_str(), t.as_str()))
        .collect();
    with_library("connecteds-interp", &files, |dir| {
        let (code, out, err) = tempest(&["test", dir]);
        assert!(
            out.contains("Panicked: No connected implementation of Bitset.add"),
            "{out}{err}"
        );
        assert!(out.ends_with("Tests passed: 0 of 1\n"), "{out}{err}");
        assert_eq!(code, 1, "{out}{err}");
    });
}

#[test]
#[cfg(feature = "csharp")]
fn connecteds_csharp() {
    connecteds("csharp", "class Bitset", "Roaring.Net");
}

#[test]
#[cfg(feature = "java")]
fn connecteds_java() {
    connecteds("java", "class Bitset", "org.roaringbitmap");
}

#[test]
#[cfg(feature = "js")]
fn connecteds_js() {
    connecteds("js", "class Bitset", "roaring-wasm");
}

#[test]
#[cfg(feature = "py")]
fn connecteds_py() {
    connecteds("py", "class Bitset", "pyroaring");
}

#[test]
#[cfg(feature = "rust")]
fn connecteds_rust() {
    connecteds("rust", "struct Bitset", "roaring::RoaringBitmap");
}

/// A library with no root module, only `x/`, `y/`, and `z/`, as some
/// real libraries are, on the interpreter and each backend whose tools
/// run here (skipped where they're missing, unless
/// `TEMPER_REQUIRE_CHECKERS` is set): `run` loads every module, and
/// `test` runs every module's tests. A module that fails as it loads
/// should be one failure by its name while the others' results still
/// count; Python names its tests module, Lua its tests file, and Java the
/// test, but the rest don't yet, nor does Java's `run` load every
/// module, so those check what they do now.
fn library_without_root_module(backend: &str) {
    let files = [
        (
            "x/x.temper",
            "export let one = 1;\nconsole.log(\"x\");\ntest(\"x\") { assert(one == 1) }\n",
        ),
        (
            "y/y.temper",
            "let { one } = import(\"../x\");\nconsole.log(\"y\");\n\
             test(\"y\") { assert(one + 1 == 2) }\ntest(\"y2\") { assert(one == 2) }\n",
        ),
        ("z/z.temper", "test(\"z\") { assert(true) }\n"),
    ];
    let mut missing = false;
    with_library(&format!("rootless-{backend}"), &files, |dir| {
        let (code, out, err) = tempest(&["run", "-w", dir, "--library", "a", "-b", backend]);
        if tools_missing(backend, &err) {
            missing = true;
            return;
        }
        // Java runs one module's `…Main`, for now.
        // The program's own line ends: CRLF on Windows for some languages.
        if backend != "java" {
            let out = out.replace("\r\n", "\n");
            assert_eq!((code, out.as_str()), (0, "x\ny\n"), "{backend}: {err}");
        }
        assert_eq!(code, 0, "{backend}: {err}");
        if backend == "lua" {
            let lua = Path::new(dir).join("temper.out/lua/a");
            assert_eq!(
                fs::read_to_string(lua.join("init.lua")).unwrap(),
                "require(\"a/x\")\nrequire(\"a/y\")\nrequire(\"a/z\")\n"
            );
            assert!(!lua.join("tests/init-test.lua").exists());
        }
        let (code, out, err) = tempest(&["test", "-b", backend, dir]);
        if tools_missing(backend, &format!("{out}{err}")) {
            missing = true;
            return;
        }
        // As each tool names it: `y2`, or C#'s method `Y2`.
        let failed = out.to_lowercase().contains("  failed  y2 - expected");
        assert!(failed, "{backend}:\n{out}{err}");
        assert!(
            out.ends_with("Tests passed: 3 of 4\n"),
            "{backend}:\n{out}{err}"
        );
        assert_eq!(code, 1, "{backend}");
    });
    if missing {
        return;
    }
    // `y`'s module failing as it loads, and so its tests.
    let files = [
        ("x/x.temper", "test(\"x\") { assert(true) }\n"),
        (
            "y/y.temper",
            "export let broken(): Int { panic() }\nlet n = broken();\n\
             test(\"y\") { assert(n == 1) }\n",
        ),
    ];
    with_library(&format!("rootless-broken-{backend}"), &files, |dir| {
        let (code, out, err) = tempest(&["test", "-b", backend, dir]);
        let (failed, summary) = match backend {
            "py" => (
                Some("  failed  unittest.loader._FailedTest.tests.test_y - ImportError: "),
                "1 of 2",
            ),
            "lua" => (
                Some("  failed  a/tests/y-test.lua - no test results: "),
                "1 of 2",
            ),
            "java" => (Some("  failed  y - panic"), "1 of 2"),
            // Every test fails, for now.
            "rust" => (None, "0 of 2"),
            // No results at all, for now.
            _ => (None, "0 of 2 (2 not run)"),
        };
        if let Some(failed) = failed {
            assert!(out.contains(failed), "{backend}:\n{out}{err}");
        }
        let summary = format!("Tests passed: {summary}\n");
        assert!(out.ends_with(&summary), "{backend}:\n{out}{err}");
        assert_eq!(code, 1, "{backend}");
    });
}

/// A library whose path has a space and non-ASCII letters, with modules
/// that import each other: found, built, tested, and run, as anywhere.
#[test]
fn paths_with_spaces_and_unicode() {
    let files = [
        ("x/x.temper", "export let one = 1;\n"),
        (
            "y/y.temper",
            "let { one } = import(\"../x\");\nconsole.log(\"y ${(one + 1).toString()}\");\n\
             test(\"y\") { assert(one + 1 == 2) }\n",
        ),
    ];
    with_library("Zoë's Lib", &files, |dir| {
        let (code, out, err) = tempest(&["test", "-b", "interp", dir]);
        assert_eq!((code, out.as_str()), (0, "Tests passed: 1 of 1\n"), "{err}");
        let (code, out, err) = tempest(&["run", "-w", dir, "--library", "a", "-b", "interp"]);
        assert_eq!(
            (code, out.replace("\r\n", "\n").as_str()),
            (0, "y 2\n"),
            "{err}"
        );
        let (code, _, err) = tempest(&["build", "-w", dir]);
        assert_eq!(code, 0, "{err}");
        for backend in ["js", "py"].into_iter().filter(|b| built_in(b)) {
            let out = Path::new(dir).join("temper.out").join(backend).join("a");
            assert!(out.is_dir(), "{backend}");
        }
    });
}

#[test]
fn library_without_root_module_interp() {
    library_without_root_module("interp");
}

#[test]
#[cfg(feature = "cpp")]
fn library_without_root_module_cpp() {
    library_without_root_module("cpp");
}

#[test]
#[cfg(feature = "csharp")]
fn library_without_root_module_csharp() {
    library_without_root_module("csharp");
}

#[test]
#[cfg(feature = "java")]
fn library_without_root_module_java() {
    library_without_root_module("java");
}

#[test]
#[cfg(feature = "js")]
fn library_without_root_module_js() {
    library_without_root_module("js");
}

#[test]
#[cfg(feature = "lua")]
fn library_without_root_module_lua() {
    library_without_root_module("lua");
}

#[test]
#[cfg(feature = "py")]
fn library_without_root_module_py() {
    library_without_root_module("py");
}

#[test]
#[cfg(feature = "rust")]
fn library_without_root_module_rust() {
    library_without_root_module("rust");
}

/// A library that doesn't build for a backend, as it uses what tmpl
/// doesn't cover yet, still counts its tests, as not run, so the summary
/// says how many there are.
#[test]
#[cfg(feature = "py")]
fn tests_of_a_library_that_does_not_build_count() {
    let files = [(
        "a.temper",
        "export let f(): Void? { null }\ntest(\"a\") { assert(true) }\ntest(\"b\") { assert(true) }\n",
    )];
    with_library("unbuilt", &files, |dir| {
        let (code, out, err) = tempest(&["test", "-b", "py", dir]);
        assert!(
            out.ends_with("Tests passed: 0 of 2 (2 not run)\n"),
            "{out}{err}"
        );
        assert_eq!(code, 1);
    });
}

/// An operator missing, as in the playground's `cents  100`, is one error
/// at the stray operand; `part` is still declared, so its later uses are
/// no errors; every backend's code is written; and the run fails there.
#[test]
#[cfg(feature = "py")]
fn missing_operator_is_one_error() {
    let files = [(
        "a.temper",
        "let cents = 1234;\nlet part = cents  100;\nconsole.log(\"${part}\");\n\
         let more = part + 1;\n",
    )];
    with_library("missing-operator", &files, |dir| {
        let (code, out, err) = tempest(&["build", "-w", dir]);
        // The one error, with its path, and the count.
        let error = "a.temper:2:19: error: Expected an operator or `;` before `100`\n";
        assert!(err.ends_with(&format!("{error}a: 1 error\n")), "{err}");
        assert_eq!(
            (code, out.as_str(), err.lines().count()),
            (1, "", 2),
            "{err}"
        );
        for backend in ["csharp", "java", "js", "lua", "py", "rust"]
            .into_iter()
            .filter(|b| built_in(b))
        {
            let written = Path::new(dir).join("temper.out").join(backend).join("a");
            assert!(written.is_dir(), "{backend}");
        }
        let (code, out, err) = tempest(&["run", "-w", dir, "--library", "a", "-b", "interp"]);
        assert!(
            err.contains("Failed: Expected an operator or `;` before `100`"),
            "{out}{err}"
        );
        assert_eq!(code, 1);
    });
}

/// Errors don't stop `build` or `test`, as in
/// Kotlin Temper: each is reported, and fails only where it runs, so
/// every backend's code is written, the tests that don't reach an error
/// pass, and the exit status is 1; a type-only error runs on as written
/// on the interpreter and Python (notes/kotlin-differences.md, "Keep on
/// trucking").
#[test]
#[cfg(feature = "py")]
fn errors_fail_only_where_they_run() {
    let files = [
        (
            "a.temper",
            "export let good(x: Int): Int { x + 1 }\n\
         export let mismatched(x: Int): Int { let s: String = x; 1 }\n\
         export let misspelled(s: String): Int { s.lenght }\n\
         export let typo(x: Nope): Int { 1 }\n\
         export let unclosed(x: Int: Int { x * 2 }\n\
         export let after(x: Int): Int { x * 3 }\n\
         export let shown(x: Gone): String { \"${x}\" + x.toString() }\n\
         export let named(): String { missing.toString() }\n",
        ),
        (
            "tests.temper",
            "test(\"good\") { assert(good(1) == 2) }\n\
         test(\"after\") { assert(after(1) + typo(1) == 4) }\n\
         test(\"mismatched\") { assert(mismatched(1) == 1) }\n\
         test(\"misspelled\") { assert(misspelled(\"a\") == 1) }\n",
        ),
    ];
    with_library("errors", &files, |dir| {
        let errors = [
            "a.temper:2:54: error: Cannot assign to String from Int32",
            "a.temper:3:41: error: No member lenght in String",
            "a.temper:4:20: error: Nope has not been declared",
            "a.temper:5:27: error: Expected `)` to close the `(` on line 5",
            "a.temper:7:21: error: Gone has not been declared",
            "a.temper:8:30: error: missing has not been declared",
        ];
        // Every backend's code, each error reported once, and 1.
        let (code, out, err) = tempest(&["build", "-w", dir]);
        for error in errors {
            assert_eq!(err.matches(error).count(), 1, "{error}:\n{err}");
        }
        assert!(err.ends_with("a: 6 errors\n"), "{err}");
        assert_eq!((code, out.as_str()), (1, ""), "{err}");
        for backend in ["csharp", "java", "js", "lua", "py", "rust"]
            .into_iter()
            .filter(|b| built_in(b))
        {
            let written = Path::new(dir).join("temper.out").join(backend).join("a");
            assert!(written.is_dir(), "{backend}");
        }
        // One backend, to a directory: the code, and the errors.
        // Outside the library, where it would be host code.
        let py = format!("{dir}-py-out");
        let (code, _, err) = tempest(&["build", "-w", dir, "-b", "py", "-o", &py]);
        let text = fs::read_to_string(format!("{py}/py/a/a/a.py")).unwrap();
        assert!(text.contains("def after(x: int, /) -> int:"), "{text}");
        assert!(err.ends_with("a: 6 errors\n"), "{err}");
        assert_eq!(code, 1);
        let _ = fs::remove_dir_all(&py);
        // Tests run, on the interpreter and on a backend, those that
        // reach an error failing, but for a type-only error, which runs
        // on as written there (`mismatched`), still exiting with 1.
        let mut runs = vec![tempest(&["test", dir])];
        if Command::new("python3").arg("--version").output().is_ok() {
            runs.push(tempest(&["test", "-b", "py", dir]));
        }
        for (code, out, err) in runs {
            assert!(out.ends_with("Tests passed: 3 of 4\n"), "{out}{err}");
            assert!(!out.contains("  failed  mismatched - "), "{out}");
            assert!(out.contains("  failed  misspelled - "), "{out}");
            assert_eq!(code, 1);
        }
    });
}

/// `--logformat json` writes Kotlin's records, a line each, all to
/// stderr: each diagnostic with its position in UTF-16 code units, each
/// failed test, the summary, and the task's result last.
#[test]
fn logformat_json() {
    let files = [(
        "a.temper",
        "export let f(n: Int): Int { n * 3 }\n\
         let g(): Int { tripel(1) }\n\
         test(\"t1\") { assert(f(2) == 6); }\n\
         test(\"t2\") { assert(f(2) == 7); }\n",
    )];
    with_library("json", &files, |dir| {
        // Records name files with `/`, on Windows too, as JSON wants.
        let shown = dir.replace('\\', "/");
        let undeclared = format!(
            "{{ \"level\": \"error\", \"template\": \"UndeclaredName\", \"pos\": {{ \"loc\": \
             \"{shown}/a.temper\", \"left\": 51, \"right\": 57 }}, \"values\": [], \"message\": \
             \"tripel has not been declared\" }}"
        );
        // A build needs a backend; one built without js (as CI's
        // --no-default-features run) skips this part.
        if built_in("js") {
            let (code, out, err) =
                tempest(&["build", "-w", dir, "-b", "js", "--logformat", "json"]);
            assert_eq!((code, out.as_str()), (1, ""), "{err}");
            assert_eq!(
                err,
                format!("{undeclared}\n{{ \"task\": \"Build\", \"ok\": false }}\n")
            );
        }
        let (code, out, err) = tempest(&["test", "-w", dir, "--logformat", "json"]);
        assert_eq!((code, out.as_str()), (1, ""), "{err}");
        let expected = [
            undeclared.as_str(),
            "{ \"level\": \"error\", \"template\": \"TestFailed\", \"values\": [ \"interp\", \
             \"t2\", \"expected f(2) == (7) not (6)\" ], \"message\": \"Test failed (interp): t2 \
             - expected f(2) == (7) not (6)\" }",
            "{ \"level\": \"summary\", \"template\": \"TestSummaryAllRun\", \"values\": [ 1, 2 ], \
             \"message\": \"Tests passed: 1 of 2\" }",
            "{ \"task\": \"Test\", \"ok\": false }",
        ];
        assert_eq!(err.lines().collect::<Vec<_>>(), expected);
        // Python's results, as Kotlin's records name the backend.
        if built_in("py") && Command::new("python3").arg("--version").output().is_ok() {
            let (code, _, err) = tempest(&["test", "-w", dir, "-b", "py", "--logformat", "json"]);
            assert_eq!(code, 1);
            assert!(err.contains("\"values\": [ \"py\", \"t2\", "), "{err}");
            assert!(
                err.ends_with("{ \"task\": \"Test\", \"ok\": false }\n"),
                "{err}"
            );
        }
        // Nothing to test is a record too.
        let (code, _, err) = tempest(&["test", "-w", dir, "--library", "b", "--logformat", "json"]);
        assert_eq!(code, 1);
        assert!(
            err.starts_with("{ \"level\": \"error\", \"template\": \"NoSuchLibrary\", "),
            "{err}"
        );
    });
}

/// `init` writes Kotlin's skeleton, byte for byte, named after its
/// directory, and won't write over a project.
#[test]
fn init_writes_kotlins_skeleton() {
    let base = std::env::temp_dir().join(format!("tempest-cli-{}-init", std::process::id()));
    let _ = fs::remove_dir_all(&base);
    fs::create_dir_all(&base).unwrap();
    let dir = base.join("my-Proj");
    let (code, out, err) = tempest(&["init", "-o", &dir.to_string_lossy()]);
    assert_eq!((code, err.as_str()), (0, ""));
    let real = fs::canonicalize(&dir).unwrap();
    assert_eq!(
        out,
        format!(
            "Initialized new Temper project \"my-proj\" in {}\n",
            real.display()
        )
    );
    // As Kotlin's `temper init -o my-Proj` writes them (2026-11-09).
    let config = "# My Proj\n\
                  \n\
                  This file is used for library configuration.\n\
                  \n\
                  The `name` export defines the library name:\n\
                  \n\
                  \x20   export let name = \"my-proj\";\n\
                  \n\
                  By default, the current directory is imported as a module. It can import\n\
                  other module directories. See Temper documentation for additional config\n\
                  options.\n";
    let module = "# Implementation for My Proj\n\
                  \n\
                  Library discussion goes here.\n\
                  \n\
                  \x20   // Library implementation code goes here.\n\
                  \n\
                  Additional documentation and code blocks may follow.\n";
    assert_eq!(
        fs::read_to_string(dir.join("src/config.temper.md")).unwrap(),
        config
    );
    assert_eq!(
        fs::read_to_string(dir.join("src/my-proj.temper.md")).unwrap(),
        module
    );
    assert_eq!(fs::read_dir(dir.join("src")).unwrap().count(), 2);
    // It builds and tests as a library.
    let (code, out, err) = tempest(&["test", "-w", &dir.to_string_lossy()]);
    assert_eq!((code, out.as_str()), (0, "Tests passed: 0 of 0\n"), "{err}");
    // Not over a project.
    let (code, out, err) = tempest(&["init", "-o", &dir.to_string_lossy()]);
    assert_eq!(
        (code, out.as_str(), err.as_str()),
        (1, "", "Directory src already exists\nInit failed\n")
    );
    let other = base.join("other");
    fs::create_dir_all(&other).unwrap();
    fs::write(other.join("config.temper.md"), "").unwrap();
    let (code, _, err) = tempest(&["init", "-o", &other.to_string_lossy()]);
    assert_eq!(
        (code, err.as_str()),
        (1, "Temper config already exists\nInit failed\n")
    );
    assert!(!other.join("src").exists());
    // `config` is `config-lib`, so it isn't the config file.
    let (code, out, _) = tempest(&["init", "-o", &base.join("config").to_string_lossy()]);
    assert_eq!(code, 0);
    assert!(
        out.starts_with("Initialized new Temper project \"config-lib\" in "),
        "{out}"
    );
    assert!(base.join("config/src/config-lib.temper.md").is_file());
    let _ = fs::remove_dir_all(&base);
}

/// `run` builds the work root and runs a library's top level on the
/// interpreter and each backend whose tools are here (skipped where
/// they're missing, unless `TEMPER_REQUIRE_CHECKERS` is set), what it
/// logs on stdout; a library that imports another runs with it.
#[test]
fn run_a_library() {
    let files = [
        ("a.temper", "export let triple(n: Int): Int { n * 3 }\n"),
        ("b/config.temper.md", "    export let name = \"b\";\n"),
        (
            "b/b.temper",
            "let { triple } = import(\"a\");\nconsole.log(\"hello ${triple(14).toString()}\");\n",
        ),
    ];
    with_library("run", &files, |dir| {
        let (code, out, err) = tempest(&["run", "-w", dir, "--library", "b", "-b", "interp"]);
        assert_eq!((code, out.as_str()), (0, "hello 42\n"), "{err}");
        assert!(!Path::new(dir).join("temper.out").exists());
        for backend in ["py", "js", "lua", "rust", "java", "cpp", "csharp"] {
            if !built_in(backend) {
                continue;
            }
            let (code, out, err) = tempest(&["run", "-w", dir, "--library", "b", "-b", backend]);
            if err.contains("couldn't run") {
                assert!(
                    std::env::var_os("TEMPER_REQUIRE_CHECKERS").is_none(),
                    "{backend}'s tools are missing (needed with TEMPER_REQUIRE_CHECKERS):\n{err}"
                );
                eprintln!("skipped {backend}: its tools are missing");
                continue;
            }
            assert_eq!((code, out.trim_end()), (0, "hello 42"), "{backend}: {err}");
            assert!(
                Path::new(dir)
                    .join("temper.out")
                    .join(backend)
                    .join("b")
                    .is_dir()
            );
        }
        // A library that isn't there.
        let (code, _, err) = tempest(&["run", "-w", dir, "--library", "c", "-b", "interp"]);
        assert_eq!(code, 1);
        assert!(err.contains("No library named c under "), "{err}");
    });
    // Errors don't stop it, but fail it.
    let files = [(
        "a.temper",
        "console.log(\"before\");\nlet x: Int = nope;\nconsole.log(\"after\");\n",
    )];
    with_library("run-errors", &files, |dir| {
        let (code, out, err) = tempest(&["run", "-w", dir, "--library", "a", "-b", "interp"]);
        assert!(out.starts_with("before\n"), "{out}");
        assert!(err.contains("nope has not been declared"), "{err}");
        assert_eq!(code, 1);
    });
}

/// `test`'s flags: `-w`, `--library`, several `-b` (each backend's
/// summary, then the total, as Kotlin's), and `--ignorefile` and
/// `.temperignore` beside `.gitignore`.
#[test]
fn test_flags() {
    let files = [
        ("a.temper", "test(\"a\") { assert(true) }\n"),
        ("b/config.temper.md", "    export let name = \"b\";\n"),
        ("b/b.temper", "test(\"b\") { assert(false) }\n"),
    ];
    with_library("test-flags", &files, |dir| {
        let (code, out, _) = tempest(&["test", "-w", dir]);
        assert!(out.ends_with("Tests passed: 1 of 2\n"), "{out}");
        assert_eq!(code, 1);
        let (code, out, _) = tempest(&["test", "-w", dir, "--library", "a"]);
        assert_eq!((code, out.as_str()), (0, "Tests passed: 1 of 1\n"));
        let (code, _, err) = tempest(&["test", "-w", dir, "--library", "c"]);
        assert_eq!(code, 1);
        assert_eq!(err, format!("No library named c under {dir}\n"));
        // Each backend in turn, then the total.
        if built_in("py") && Command::new("python3").arg("--version").output().is_ok() {
            let (code, out, err) = tempest(&[
                "test",
                "-w",
                dir,
                "--library",
                "a",
                "-b",
                "interp",
                "-b",
                "py",
            ]);
            assert_eq!(
                (code, out.as_str()),
                (
                    0,
                    "interp: Tests passed: 1 of 1\npy: Tests passed: 1 of 1\nTests passed: 2 of 2\n"
                ),
                "{err}"
            );
        }
        // An ignore file, from anywhere, relative to its own directory.
        let list = format!("{dir}-ignore");
        fs::write(&list, "b/\n").unwrap();
        let (code, out, _) = tempest(&["test", "-w", dir, "-ign", &list]);
        assert_eq!((code, out.as_str()), (0, "Tests passed: 1 of 1\n"));
        // An empty one ignores nothing. (Not /dev/null, which Windows lacks.)
        let empty = Path::new(dir).join("empty-ignore");
        fs::write(&empty, "").unwrap();
        let empty = empty.to_string_lossy();
        let (code, out, err) = tempest(&["test", "-w", dir, "--ignorefile", &empty]);
        assert!(out.ends_with("Tests passed: 1 of 2\n"), "{out}{err}");
        assert_eq!(code, 1);
        let (code, _, err) = tempest(&["test", "-w", dir, "-ign", &format!("{list}-not")]);
        assert_eq!(code, 1);
        assert!(err.starts_with("temper: --ignorefile "), "{err}");
        // A .temperignore is read as a .gitignore is, after it.
        fs::write(Path::new(dir).join(".temperignore"), "b/\n").unwrap();
        let (code, out, _) = tempest(&["test", "-w", dir]);
        assert_eq!((code, out.as_str()), (0, "Tests passed: 1 of 1\n"));
        fs::write(Path::new(dir).join(".gitignore"), "b/\n").unwrap();
        fs::write(Path::new(dir).join(".temperignore"), "!b/\n").unwrap();
        let (_, out, _) = tempest(&["test", "-w", dir]);
        assert!(out.ends_with("Tests passed: 1 of 2\n"), "{out}");
        // And --ignorefile has the last word over both.
        let (_, out, _) = tempest(&["test", "-w", dir, "-ign", &list]);
        assert_eq!(out, "Tests passed: 1 of 1\n");
        fs::write(&list, "!b/\n").unwrap();
        fs::remove_file(Path::new(dir).join(".temperignore")).unwrap();
        let (_, out, _) = tempest(&["test", "-w", dir, "-ign", &list]);
        assert!(out.ends_with("Tests passed: 1 of 2\n"), "{out}");
        // `run` and `build` take it too, where Kotlin's `run` reads no
        // ignore file at all.
        fs::write(&list, "b/\n").unwrap();
        let (code, _, err) = tempest(&[
            "run",
            "-w",
            dir,
            "--library",
            "b",
            "-b",
            "interp",
            "-ign",
            &list,
        ]);
        assert_eq!(code, 1);
        assert!(err.contains("No library named b under "), "{err}");
        if built_in("js") {
            let (code, _, err) = tempest(&["build", "-w", dir, "-b", "js", "-ign", &list]);
            assert_eq!(code, 0, "{err}");
            assert!(!Path::new(dir).join("temper.out/js/b").exists());
        }
        let _ = fs::remove_file(&list);
    });
}

/// A library with a test that never ends, in `x/`, and one that passes,
/// in `y/`.
#[cfg(all(target_os = "linux", feature = "lua", feature = "py"))]
const HANGING: [(&str, &str); 2] = [
    (
        "x/x.temper",
        "test(\"hang\") { for (var i = 0; i < 5;) {} }\n",
    ),
    ("y/y.temper", "test(\"y\") { assert(true) }\n"),
];

/// The processes whose working directory is under `dir`, which are what
/// tempest started there, by their ids.
#[cfg(all(target_os = "linux", feature = "lua", feature = "py"))]
fn processes_in(dir: &Path) -> Vec<u32> {
    let dir = fs::canonicalize(dir).unwrap();
    let mut found = Vec::new();
    for entry in fs::read_dir("/proc").unwrap().flatten() {
        let Ok(id) = entry.file_name().to_string_lossy().parse::<u32>() else {
            continue;
        };
        if fs::read_link(entry.path().join("cwd")).is_ok_and(|cwd| cwd.starts_with(&dir)) {
            found.push(id);
        }
    }
    found
}

/// Whether a process is running: there, and not a zombie.
#[cfg(all(target_os = "linux", feature = "lua", feature = "py"))]
fn alive(id: u32) -> bool {
    let stat = fs::read_to_string(format!("/proc/{id}/stat")).unwrap_or_default();
    // `ID (NAME) STATE …`, the name possibly holding spaces.
    let state = stat.rsplit_once(") ").map(|(_, rest)| &rest[..1]);
    state.is_some_and(|s| s != "Z" && s != "X")
}

/// Waits up to `secs` for none of `ids` to be running.
#[cfg(all(target_os = "linux", feature = "lua", feature = "py"))]
fn all_gone(ids: &[u32], secs: u64) -> bool {
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(secs);
    while ids.iter().any(|&id| alive(id)) {
        if std::time::Instant::now() > deadline {
            return false;
        }
        std::thread::sleep(std::time::Duration::from_millis(50));
    }
    true
}

/// Whether a backend's tools are missing, by what tempest said; which
/// fails the test with `TEMPER_REQUIRE_CHECKERS`.
fn tools_missing(backend: &str, err: &str) -> bool {
    if !err.contains("couldn't run") {
        return false;
    }
    assert!(
        std::env::var_os("TEMPER_REQUIRE_CHECKERS").is_none(),
        "{backend}'s tools are missing (needed with TEMPER_REQUIRE_CHECKERS):\n{err}"
    );
    eprintln!("skipped {backend}: its tools are missing");
    true
}

/// Runs tempest in the background, noting the processes it starts in
/// `dir`'s `temper.out` while it runs; once it's started one, `signal`,
/// if given, is sent to tempest. Its status, stdout, stderr, and what it
/// started.
#[cfg(all(target_os = "linux", feature = "lua", feature = "py"))]
fn watch_tempest(
    args: &[&str],
    dir: &str,
    signal: Option<&str>,
) -> (std::process::ExitStatus, String, String, Vec<u32>) {
    let logs = std::env::temp_dir().join(format!(
        "tempest-cli-{}-{}",
        std::process::id(),
        Path::new(dir).file_name().unwrap().to_string_lossy()
    ));
    let (out_log, err_log) = (logs.with_extension("out"), logs.with_extension("err"));
    let mut tempest = Command::new(env!("CARGO_BIN_EXE_temper"))
        .args(args)
        .stdout(fs::File::create(&out_log).unwrap())
        .stderr(fs::File::create(&err_log).unwrap())
        .spawn()
        .unwrap();
    let out_dir = Path::new(dir).join("temper.out");
    let mut started: Vec<u32> = Vec::new();
    let mut signalled = false;
    let watch = std::time::Instant::now();
    let status = loop {
        if let Some(status) = tempest.try_wait().unwrap() {
            break status;
        }
        if out_dir.is_dir() {
            for id in processes_in(&out_dir) {
                if !started.contains(&id) {
                    started.push(id);
                }
            }
        }
        if let (Some(signal), false) = (signal, started.is_empty() || signalled) {
            // Once tempest has registered it, as it does right after
            // starting it.
            std::thread::sleep(std::time::Duration::from_millis(200));
            let id = tempest.id().to_string();
            let sent = Command::new("kill").args([signal, &id]).status().unwrap();
            assert!(sent.success());
            signalled = true;
        }
        if watch.elapsed().as_secs() > 180 {
            let _ = tempest.kill();
            panic!("{args:?}: temper still running after 180 s");
        }
        std::thread::sleep(std::time::Duration::from_millis(20));
    };
    let read = |path: &Path| {
        let text = fs::read_to_string(path).unwrap_or_default();
        let _ = fs::remove_file(path);
        text
    };
    (status, read(&out_log), read(&err_log), started)
}

/// `--timeout`: a test that never ends is killed, with nothing it
/// started left running, and fails, its run's other results kept: on
/// Lua, each tests file is its own run, and on Python, a library's tests
/// are one.
#[test]
#[cfg(all(target_os = "linux", feature = "lua", feature = "py"))]
fn test_timeout() {
    with_library("timeout", &HANGING, |dir| {
        for backend in ["lua", "py"] {
            let args = ["test", "-b", backend, "--timeout", "3", dir];
            let (status, out, err, started) = watch_tempest(&args, dir, None);
            if tools_missing(backend, &format!("{out}{err}")) {
                continue;
            }
            assert!(!started.is_empty(), "{backend}: nothing seen running");
            assert!(all_gone(&started, 0), "{backend}: left running");
            match backend {
                "lua" => {
                    let failed = "  failed  a/tests/x-test.lua - timed out after 3 s\n";
                    assert!(out.contains(failed), "{out}{err}");
                    assert!(out.ends_with("Tests passed: 1 of 2\n"), "{out}{err}");
                }
                _ => {
                    assert!(out.contains("a: py: timed out after 3 s\n"), "{out}{err}");
                    let tally = "Tests passed: 0 of 2 (2 not run)\n";
                    assert!(out.ends_with(tally), "{out}{err}");
                }
            }
            assert_eq!(status.code(), Some(1), "{out}{err}");
        }
        // TEMPER_TIMEOUT is the same; a wrong one is a usage error.
        let (code, _, err) = tempest(&["test", "--timeout", "0", dir]);
        assert_eq!(code, 2);
        assert!(
            err.starts_with("temper test: --timeout is whole seconds above 0, or none, not 0\n"),
            "{err}"
        );
        let output = Command::new(env!("CARGO_BIN_EXE_temper"))
            .args(["run", "-w", dir, "--library", "a", "-b", "interp"])
            .env("TEMPER_TIMEOUT", "soon")
            .output()
            .unwrap();
        let err = String::from_utf8_lossy(&output.stderr);
        assert_eq!(output.status.code(), Some(2));
        assert!(
            err.starts_with(
                "temper run: TEMPER_TIMEOUT is whole seconds above 0, or none, not soon\n"
            ),
            "{err}"
        );
    });
}

/// Killing tempest kills what it started: by SIGTERM (or SIGINT, which
/// a test run may ignore), its handler kills each process group it
/// started; by SIGKILL, which no
/// handler sees, the parent-death signal kills each process it started.
#[test]
#[cfg(all(target_os = "linux", feature = "lua", feature = "py"))]
fn killing_tempest_kills_its_children() {
    with_library("killed", &HANGING, |dir| {
        for backend in ["lua", "py"] {
            for (signal, number) in [("-TERM", 15), ("-KILL", 9)] {
                let args = ["test", "-b", backend, "--timeout", "none", dir];
                let (status, out, err, started) = watch_tempest(&args, dir, Some(signal));
                if tools_missing(backend, &format!("{out}{err}")) {
                    break;
                }
                use std::os::unix::process::ExitStatusExt;
                assert_eq!(
                    status.signal(),
                    Some(number),
                    "{backend} {signal}: {out}{err}"
                );
                assert!(
                    !started.is_empty(),
                    "{backend} {signal}: nothing seen running"
                );
                // Orphans now, once tempest's gone, for init to reap.
                assert!(all_gone(&started, 5), "{backend} {signal}: left running");
            }
        }
    });
}
