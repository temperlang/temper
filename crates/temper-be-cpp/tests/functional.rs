//! The C++ backend on Kotlin Temper's functional tests that tmpl's
//! translation covers so far, in `temper-funtests`'s
//! `cases/`, with the class tests (`kit::CLASSES`) and those
//! with nullables and `orelse` (`kit::NULLABLES`), and those with
//! core's other types and members (`kit::CORE_TYPES`: string indices,
//! `StringBuilder`, `Empty`, `Deque`, `DenseBitVector`, and more of
//! `String`'s and `Float64`'s), and those with host code
//! (`kit::CONNECTED`, each as is and under `--no-connected`), those of
//! divisions that can't fail and maps (`kit::DIVISIONS`), those of
//! labeled loops' jumps and classes declared in functions
//! (`kit::LOOPS_AND_CLASSES`), those of generators, `async`, and `await`,
//! as state machines run by the runtime's `async_run` (`kit::ASYNC`), on the
//! shared programs (`crates/temper-be-testkit/programs/`), which cover
//! operators, jumps, module state, string order, nullables, and classes beyond
//! those tests, and on programs of C++'s own: names C++ takes
//! ([`HIDING`]), a nullable result discarded ([`DISCARDED`]),
//! `Float64.toString` across its range ([`FLOAT_FORMAT`]), and objects
//! a call frees while it runs ([`LIFETIMES`]); this crate's own programs,
//! in `tests/programs/` ([`OWN`]); and the testkit's specifications of
//! declared failures ([`SPECS`]), whose host code returns its failures
//! in `Expected`s.
//!
//! - `goldens`: each program's generated library, laid out as Kotlin's,
//!   with its `init.cpp` matching the golden in `tests/goldens/`, named
//!   for the test's path (`algos-fibonacci.cpp`), and its header and
//!   `main.cpp` as the test expects; a library of several modules, or
//!   with tests (`modules`, `imports-library`, and `testing`), has every
//!   file in its golden. To accept new or changed output, run with
//!   `UPDATE_SNAPSHOTS=1` and review the diff.
//! - `runs`: without CMake, `g++` (or `CXX`) compiles every generated
//!   `.cpp` against the runtime (`crates/temper-be-cpp/runtime`), C++14
//!   as Kotlin's, with every warning [`STRICT`] lists an error, through a
//!   precompiled header of the runtime, built once per set of options
//!   and kept under `CARGO_TARGET_TMPDIR` until the runtime, the options,
//!   or `g++` change (when it's built, the runtime is checked with every
//!   warning it tolerates, [`RUNTIME_WARNINGS`]). It links one library's
//!   `main.cpp` as Kotlin's program, and a runner of every library, which
//!   runs a library's init by name, entry points with no init first
//!   ([`FIRST_USE`]), which run it themselves, and `module-state` from 8
//!   threads, which all enter it at once with no init first; each logs
//!   what the test expects of Kotlin's `cpp` backend. Then the same under
//!   AddressSanitizer and UndefinedBehaviorSanitizer, and the threads
//!   under ThreadSanitizer (built of `module-state` alone, and with
//!   libcurl, the `netresponse` tests, whose client has a thread of its
//!   own), which catch
//!   the data races and use-after-free that Kotlin's C++ can have (the
//!   class tests and `classes` chain calls on returned objects, and
//!   `lifetimes` frees objects and lists in use). The sanitizers' build
//!   of every library compiles several files to one object
//!   ([`compile_bulk`]), since the plain build has checked each alone;
//!   with `TEMPER_CPP_BULK=0`, each compiles alone there too
//!   (notes/test-speed.md, "C++'s functional tests in bulk").
//!   `TEMPER_CXXFLAGS` adds options to every build but ThreadSanitizer's,
//!   as the nightly workflow's sanitized job does. Every library is built in
//!   one directory, so `imports-library` finds `modules` beside it, but
//!   a library with host code, which names it `work`, builds alone, its
//!   host `.cpp`s with C++14 alone, since the test's host code isn't
//!   written for the warnings. Each library with tests builds again with
//!   `TEMPER_TESTS`, with std's `testing` and its `test.cpp`, strictly,
//!   and runs, writing JUnit's XML, its failures compared with its
//!   `expectedFailures` (none but for those that run as tests, `TESTS`,
//!   which run only so). Skipped, with a message,
//!   without `g++`; with `TEMPER_REQUIRE_CHECKERS` set, as in CI, that
//!   fails it instead.
//! - `strict`: every library's generated `.cpp`s (std's too, and the
//!   generated files of those with host code) compiled, not linked, at
//!   `-O2`, as other people's release builds are, with [`STRICT`] by
//!   `g++-12` and `g++-14`, whichever are installed, or the compilers
//!   `TEMPER_STRICT_CXX` names (clang++ with [`CLANG_STRICT`]), as CI
//!   does; the newest GCC at `-O3` too. `-O2` inlines more, and GCC's
//!   path-following warnings (`-Wnull-dereference`, `-Warray-bounds`,
//!   `-Wmaybe-uninitialized`) see what `-O1`'s builds don't. In bulk,
//!   which inlines across files.
//! - `net_client_from_host`: `host/net.cpp` plugs a client of its own
//!   into std `net` and runs the `netresponse` tests' libraries, under
//!   the sanitizers. In `runs`, those tests pass where `g++` finds
//!   libcurl's headers ([`curl`]), and otherwise give Kotlin's expected
//!   failures.
//! - `co_await_interop`: `co_await.cpp`, a C++20 coroutine that awaits
//!   Temper promises, built with `-std=c++20` under the sanitizers,
//!   logs as they settle, then range-for loops over a generator, which a
//!   failed step leaves done, and `host/interop.cpp` does the same with
//!   the `async-turns` program's generated library; skipped where `g++`
//!   has no coroutines.
//! - `expected_shim`: `expected.cpp`, the runtime's `Expected` and
//!   `Failure`, built as C++14 (the shim) and as C++23 (`std::expected`,
//!   and the shim again), strictly and under the sanitizers, each
//!   printing the same lines.
//! - `failures_interop`: `host/failures.cpp`, host code using the
//!   `cpp-failures` program's declared failures, built with its generated
//!   library as C++14 and as C++23, under the sanitizers.

use std::fmt::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::sync::Mutex;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::{env, fs};
use temper_be_cpp::{CPP, OutFile, names, translate};
use temper_be_testkit::{self as kit, Case, Goldens};

/// A library of names that C++ reserves or the translator prints
/// (`std`, `core`, `int32_t`, a keyword, and a macro, `M_PI`), each
/// taking `_`; `Boolean`'s `<`; strings that C++ would read otherwise (a
/// trigraph, `??=`, and a NUL, which would end a literal); and shifts by
/// more than the width, which Temper masks and C++ leaves undefined;
/// and functions named as `std`'s, which argument-dependent lookup
/// would find for a `std::string` or `shared_ptr` argument (Kotlin's
/// `swap(xs, ys)` calls `std::swap`, and `move(xs)` `std::move`).
const HIDING: &str = "# Hiding

    let std = \"s\";
    let explicit(int32_t: Int): Int { int32_t + 1 }
    let core(a: Int, b: Int): Int throws Bubble { a / b }
    let before(a: Boolean, b: Boolean): Boolean { a < b }
    let M_PI = 3;
    let shifts(n: Int): String { \"${1 << n} ${-8 >> n} ${-8 >>> (n - 4)}\" }
    console.log(\"${std} ${explicit(1).toString()} ${core(7, 2).toString()} ${M_PI.toString()}\");
    console.log(\"${before(false, true).toString()} ${(\"??=\" < \"??>\").toString()}\");
    console.log(shifts(33));
    console.log(\"${(\"a\\u{0}b\" == \"a\\u{0}b\").toString()} ${(\"a\\u{0}b\" == \"a\\u{0}c\").toString()}\");
    let quoted(s: String): String { \"[${s}]\" }
    let swap(a: List<String>, b: List<String>): String { \"${b[0]}${a[0]}\" }
    let move(xs: List<String>): String { xs[0] }
    let xs = [\"x\"];
    let ys = [\"y\"];
    console.log(\"${quoted(std)} ${swap(xs, ys)} ${move(xs)}\");
";

/// A nullable result discarded where tmpl knows it isn't null (Wherewith's
/// `if (lexer.inLineComment) { lexer.scan("\n"); }`), which is the call
/// alone, not its `NullableParam`'s `.value`, which C++ can't take as a
/// statement.
const DISCARDED: &str = "# Discarded

    class Lexer {
      public var seen: Int = 0;
      public get inComment(): Boolean { seen > 0 }
      public scan(text: String): String? {
        seen += 1;
        if (seen > 100) { \"too long\" } else { null }
      }
    }

    let finish(texts: List<String>): String {
      let lexer = new Lexer();
      var problem: String? = null;
      for (let text of texts) {
        var found: String? = null;
        found = lexer.scan(text);
        if (problem == null) { problem = found; }
      }
      if (lexer.inComment) { lexer.scan(\"\\n\"); }
      if (problem == null) { \"ok ${lexer.seen.toString()}\" } else { \"problem\" }
    }
    console.log(finish([\"a\", \"b\"]));
";

/// `Float64.toString` across its range, as Kotlin's interpreter and the
/// Java runtime give it: plain from 1e-3 up to 1e7, shortest round-trip
/// digits, at least two and the closest such (`4.9e-324`), and at a
/// power of two the digits above where the closest below don't read back
/// (2^-1017). Other backends move where exponents start, so the shared
/// `types/float/basics` checks only where all agree; Kotlin's C++ wrote
/// `-1.5e+03`.
const FLOAT_FORMAT: &str = "# Float format

    var one = 1.0;
    one = one;
    let show(xs: List<Float64>): Void {
      console.log(xs.join(\" \") { x => (x * one).toString() });
    }
    show([-1500.0, 1e6, 9999999.0, 1e7, 1e15, 1e16, 1e20, 1e21]);
    show([0.001, 9.999999999999998e-4, 1e-4, 1e-5, 1e-6, 1e-7]);
    show([0.1, 1.0 / 3.0, 0.1 + 0.2, 123456789012345680.0, 9999999.999999998]);
    show([5e-324, 2.2250738585072014e-308, 7.120236347223045e-307]);
    show([1.7976931348623157e308, -0.0, NaN, 1e999, -1e999]);
";

/// Objects that a call frees while it runs, which C++ must keep alive:
/// a method's object that only a field held, which the method replaces;
/// arguments read from a field the callee replaces, which Kotlin's C++
/// passes as references to the field (`this_->replace(this_->box)` logs
/// `new`, not `old`); calls chained on objects nothing else holds; and
/// the same for nullables: a nullable field passed to a method that
/// replaces it, a nullable string field read then replaced, and a
/// nullable object a call returns, used after `??` and a null check.
/// ASan checks it with the rest.
/// `DenseBitVector` past its end, as core's Temper body has it: `get`
/// gives false, and `set` grows it for a true bit (the runtime bubbled,
/// as Kotlin's does, failing the regex engine's `ctype`).
const BIT_VECTOR: &str = "# Bit vector

    let v = new DenseBitVector(0);
    v.set(5, false);
    let before = v.get(5);
    v.set(9, true);
    console.log(\"${before} ${v.get(9)} ${v.get(8)} ${v.get(100)}\");
";

const LIFETIMES: &str = "# Lifetimes

    class Item(public name: String) {
      public leave(h: Holder): String {
        h.box = new Box(new Item(\"other\"));
        name
      }
    }

    class Box(public var item: Item) {}

    class Holder(public var box: Box) {
      public replace(old: Box): String {
        box = new Box(new Item(\"new\"));
        old.item.name
      }
      public replaceItem(old: Item): String {
        box = new Box(new Item(\"newer\"));
        old.name
      }
      public run(): String { replace(box) }
      public runItem(): String { replaceItem(box.item) }
      public drop(): String { box.item.leave(this) }
    }

    class Slot(public var item: Item?, public var tag: String?) {
      public swap(old: Item?): String {
        item = null;
        tag = null;
        if (old == null) { \"none\" } else { old.name }
      }
      public run(): String { swap(item) }
      public relabel(): String {
        let was = tag;
        tag = \"after\";
        was ?? \"unlabeled\"
      }
    }

    let maybeItem(make: Boolean): Item? { if (make) { new Item(\"temp\") } else { null } }

    class Names(public var list: List<String>) {
      public swap(old: List<String>): String {
        list = [\"fresh\"];
        old[0]
      }
      public run(): String { swap(list) }
      public firstThen(): String {
        let first = list[0];
        list = [\"replaced\"];
        first
      }
    }

    let made(): List<String> { [\"made\", \"list\"] }

    let h = new Holder(new Box(new Item(\"old\")));
    console.log(h.run());
    console.log(h.runItem());
    console.log(h.box.item.name);
    console.log(h.drop());
    console.log(h.box.item.name);
    console.log(new Holder(new Box(new Item(\"chained\"))).box.item.name);
    let slot = new Slot(new Item(\"slotted\"), \"before\");
    console.log(slot.run());
    console.log(slot.run());
    slot.tag = \"first\";
    console.log(slot.relabel());
    console.log(slot.relabel());
    console.log((maybeItem(true) ?? new Item(\"fallback\")).name);
    console.log((maybeItem(false) ?? new Item(\"fallback\")).name);
    let got = maybeItem(true);
    if (got != null) {
      console.log(got.name);
    }
    let names = new Names([\"kept\"]);
    console.log(names.run());
    console.log(names.firstThen());
    console.log(names.list[0]);
    let second = made()[1];
    console.log(second);
    console.log(made().length.toString());
";

/// This crate's own programs, in `tests/programs/`, which test what's
/// particular to C++: `cpp-failures`, declared failures as C++ callers
/// branch on them, with host code that does (`tests/host/failures.cpp`).
const OWN: [&str; 1] = ["cpp-failures"];

/// The testkit's specifications (`kit::SPECS`) C++ runs, as Python, Java,
/// and Rust do; the other backends don't yet.
const SPECS: [&str; 2] = ["failures", "host-failures"];

/// Building tests: plain, with the tests defined.
const TESTS: Mode = Mode {
    name: "tests",
    options: &["-O1", "-DTEMPER_TESTS"],
};

/// `g++`'s options for generated code: C++14, as Kotlin's, and every
/// warning below an error. The runtime's headers are a system directory
/// (`-isystem`), so only generated code answers to the four that the
/// runtime breaks ([`RUNTIME_WARNINGS`] has the rest). Not on: `-Weffc++`,
/// whose rules (members initialized in constructors' lists, and the like)
/// are older advice than C++11's, and which the runtime breaks too.
const STRICT: &[&str] = &[
    "-std=c++14",
    "-Wall",
    "-Wextra",
    "-Wpedantic",
    "-Werror",
    // What the runtime tolerates too.
    "-Wcast-align=strict",
    "-Wcast-qual",
    "-Wconversion",
    "-Wdate-time",
    "-Wdeprecated-copy-dtor",
    "-Wdouble-promotion",
    "-Wduplicated-branches",
    "-Wduplicated-cond",
    "-Wextra-semi",
    "-Wformat=2",
    "-Wnull-dereference",
    "-Wimplicit-fallthrough=5",
    "-Winvalid-pch",
    "-Wlogical-op",
    "-Wmissing-declarations",
    "-Wnon-virtual-dtor",
    "-Wold-style-cast",
    "-Woverloaded-virtual",
    "-Wpessimizing-move",
    "-Wredundant-decls",
    "-Wredundant-move",
    "-Wsign-promo",
    "-Wstrict-null-sentinel",
    "-Wsuggest-override",
    "-Wswitch-enum",
    "-Wundef",
    "-Wvla",
    "-Walloca",
    "-Wzero-as-null-pointer-constant",
    // What only generated code answers to: the runtime shadows names,
    // converts between signed and unsigned implicitly, casts to the same
    // type, and compares floats with `==`.
    "-Wshadow",
    "-Wsign-conversion",
    "-Wuseless-cast",
    "-Wfloat-equal",
];

/// The warnings, of [`STRICT`], that the runtime's headers compile
/// without, checked once, with the runtime included as an ordinary
/// directory (`-I`).
const RUNTIME_WARNINGS: usize = STRICT.len() - 4;

fn cases() -> Vec<Case> {
    let tests = [
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
    .concat();
    let programs = [&kit::PROGRAMS[..], &SPECS[..]].concat();
    let mut cases = kit::cases_with("cpp", &programs, &tests);
    cases.iter_mut().for_each(rename_host_code);
    let all = kit::all_libraries(&programs, &tests);
    // Kotlin's suite expects std `net`'s tests to fail on C++, which has
    // no `net`; here they pass where libcurl's headers are.
    if !curl() {
        kit::without_net(&mut cases);
    }
    // `regex/match` passes, `/a[^a-c]/` finding the one code point after
    // `a` in "a🌍", with the runtime's code sets rewritten into UTF-8
    // byte sequences (Kotlin's issue 198, fixed in its #645).
    cases.push(Case::inline(
        "hiding",
        "hiding.temper.md",
        HIDING,
        vec![
            "s 2 3 3".into(),
            "true true".into(),
            "2 -4 7".into(),
            "true false".into(),
            "[s] yx x".into(),
        ],
    ));
    cases.push(Case::inline(
        "discarded",
        "discarded.temper.md",
        DISCARDED,
        vec!["ok 3".into()],
    ));
    cases.push(Case::inline(
        "float-format",
        "float-format.temper.md",
        FLOAT_FORMAT,
        [
            "-1500.0 1000000.0 9999999.0 1.0e+7 1.0e+15 1.0e+16 1.0e+20 1.0e+21",
            "0.001 9.999999999999998e-4 1.0e-4 1.0e-5 1.0e-6 1.0e-7",
            "0.1 0.3333333333333333 0.30000000000000004 1.2345678901234568e+17 9999999.999999998",
            "4.9e-324 2.2250738585072014e-308 7.120236347223045e-307",
            "1.7976931348623157e+308 -0.0 NaN Infinity -Infinity",
        ]
        .map(String::from)
        .to_vec(),
    ));
    cases.push(Case::inline(
        "bit-vector",
        "bit-vector.temper.md",
        BIT_VECTOR,
        vec!["false true false false".into()],
    ));
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
    cases.push(Case::inline(
        "lifetimes",
        "lifetimes.temper.md",
        LIFETIMES,
        [
            "old", "new", "newer", "newer", "other", "chained", "slotted", "none", "first",
            "after", "temp", "fallback", "temp", "kept", "fresh", "replaced", "list", "2",
        ]
        .map(String::from)
        .to_vec(),
    ));
    // Libraries of one name (`types/int/basics` and `types/float/basics`)
    // would be one in a build of every library, so a later one is named
    // for its test, or where that's taken too (the `functions` program,
    // after `imports/functions`), for its test and `-program`. Named
    // among every case, whichever `TEMPER_CASES` keeps, so that the
    // goldens don't change with it.
    let mut seen = std::collections::HashSet::new();
    let mut named = std::collections::HashMap::new();
    // Libraries with host code keep their name, `work`, which the host
    // code includes, each built alone ([`runs`]).
    for (key, library) in all.into_iter().filter(|(_, l)| l != "work") {
        let library = if !seen.insert(library.clone()) {
            let renamed = if seen.contains(&key) {
                format!("{key}-program")
            } else {
                key.clone()
            };
            seen.insert(renamed.clone());
            renamed
        } else {
            library
        };
        named.insert(key, library);
    }
    for case in cases.iter_mut().filter(|c| c.host_files.is_empty()) {
        if let Some(library) = named.get(&case.key) {
            case.library = library.clone();
        } else if !seen.insert(case.library.clone()) {
            // This file's own cases, after the others.
            case.library = case.key.clone();
            seen.insert(case.library.clone());
        }
    }
    cases
}

/// A run of a program in sections (`host-failures-cause`, of
/// `host-failures`), a library of its own, with the program's host code
/// in its namespace (`host_failures_cause`).
fn rename_host_code(case: &mut Case) {
    let Some(program) = case.dir.file_name().and_then(|f| f.to_str()) else {
        return;
    };
    if case.host_files.is_empty() || program == case.library {
        return;
    }
    let from = format!("namespace {} {{", names::namespace(program));
    let to = format!("namespace {} {{", names::namespace(&case.library));
    for (_, text) in &mut case.host_files {
        *text = text.replace(&from, &to);
    }
}

/// A case's generated files.
fn generate(case: &Case) -> Vec<OutFile> {
    translate(&kit::program_for(case, &CPP.rules), &CPP)
        .unwrap_or_else(|e| panic!("{}: {e}", case.key))
}

fn runtime_dir() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime")
}

#[test]
fn goldens() {
    kit::with_stack(|| {
        let mut goldens = Goldens::new(env!("CARGO_MANIFEST_DIR"));
        for case in cases() {
            let files = generate(&case);
            let library = &case.library;
            let namespace = names::namespace(library);
            let paths: Vec<&str> = files.iter().map(|f| f.path.as_str()).collect();
            if paths.len() != 4 {
                // Several modules, tests, or host code: every file in one
                // golden, but host code's copies only by their paths.
                let mut all = String::new();
                for file in &files {
                    let rest = &file.path[library.len() + 1..];
                    if case.host_files.iter().any(|(path, _)| path == rest) {
                        assert!(
                            case.host_files
                                .contains(&(rest.to_string(), file.text.clone())),
                            "{}: {rest}",
                            case.key
                        );
                        all.push_str(&format!("// {rest}: host code, as it is\n"));
                        continue;
                    }
                    all.push_str(&format!("// {rest}\n{}\n", file.text));
                }
                goldens.check(&format!("{}.cpp", case.key), &all);
                continue;
            }
            assert_eq!(
                paths,
                [
                    format!("{library}/init.hpp"),
                    format!("{library}/init.cpp"),
                    format!("{library}/main.cpp"),
                    format!("{library}/CMakeLists.txt"),
                ],
                "{}",
                case.key
            );
            let header = &files[0].text;
            assert!(
                header.starts_with("#pragma once\n\n#include <temper-core/core.hpp>\n")
                    && header.contains(&format!("namespace {namespace} {{\n"))
                    && header.contains("void global_init_init();\n"),
                "{}: {header}",
                case.key
            );
            assert_eq!(
                files[2].text,
                format!(
                    "#include \"{library}/init.hpp\"\n#include \"temper-core/stack_trace.hpp\"\n\nint main() {{\n  temper::core::install_stack_traces();\n  {namespace}::global_init_init();\n}}\n"
                ),
                "{}",
                case.key
            );
            let module = &files[1].text;
            assert!(
                module.starts_with(&format!("#include <{library}/init.hpp>\n")),
                "{}",
                case.key
            );
            // The header and the module, one golden.
            let both = format!("// init.hpp\n{header}\n// init.cpp\n{module}");
            goldens.check(&format!("{}.cpp", case.key), &both);
        }
        goldens.finish();
    });
}

/// `g++`, or `CXX`, and its version, if it runs.
fn find_gxx() -> Option<(String, String)> {
    kit::tool_with("CXX", "g++", "--version")
}

/// Whether `g++` finds libcurl's headers, for std `net`'s default client
/// (`TEMPER_NET_CURL`, linked with `-lcurl`): where it doesn't, the
/// libraries build without it, and `net`'s requests fail at run time
/// ([`NO_CLIENT`]), as Kotlin's suite expects of C++ (`kit::without_net`).
fn curl() -> bool {
    static FOUND: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    *FOUND.get_or_init(|| {
        let Some((gxx, _)) = find_gxx() else {
            return false;
        };
        let probe = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("curl-probe.cpp");
        fs::write(&probe, "#include <curl/curl.h>\n").unwrap();
        let found = (Command::new(gxx).args(["-fsyntax-only"]).arg(&probe))
            .output()
            .is_ok_and(|o| o.status.success());
        if !found {
            eprintln!("std net's libcurl client skipped: no libcurl headers (curl/curl.h)");
        }
        found
    })
}

/// What a request says, once, on stderr, without a client: no libcurl,
/// and none plugged in.
const NO_CLIENT: &str = "std/net: no client: build with libcurl (TEMPER_NET_CURL), \
    or call temper::core::net::set_client\n";

/// A way of building: its name, and the options it adds to [`STRICT`].
struct Mode {
    name: &'static str,
    options: &'static [&'static str],
}

impl Mode {
    /// [`Mode::options`], then `TEMPER_CXXFLAGS`, split at spaces, for every
    /// mode but [`TSAN`], whose sanitizer can't be combined with others (the
    /// nightly workflow builds with AddressSanitizer this way), and
    /// [`strict`]'s, which only compile.
    fn flags(&self) -> Vec<String> {
        let mut flags: Vec<String> = self.options.iter().map(|o| o.to_string()).collect();
        let strict = [STRICT_O2, STRICT_O2_TESTS, STRICT_O3, STRICT_O3_TESTS].map(|m| m.name);
        if self.name != TSAN.name && !strict.contains(&self.name) {
            let extra = env::var("TEMPER_CXXFLAGS").unwrap_or_default();
            flags.extend(extra.split_whitespace().map(String::from));
        }
        if curl() {
            flags.push("-DTEMPER_NET_CURL".into());
        }
        flags
    }
}

const PLAIN: Mode = Mode {
    name: "plain",
    options: &["-O1"],
};

/// AddressSanitizer and UndefinedBehaviorSanitizer, any report fatal.
/// GCC 13 warns, wrongly, of a `std::function` maybe uninitialized in
/// libstdc++'s own `<regex>` (which the runtime's `regex.hpp` uses, for
/// std's regex) when sanitizing, so that warning isn't an error here.
const ASAN: Mode = Mode {
    name: "asan",
    options: &[
        "-O1",
        "-g",
        "-fno-omit-frame-pointer",
        "-fsanitize=address,undefined",
        "-fno-sanitize-recover=all",
        "-Wno-error=maybe-uninitialized",
    ],
};

/// ThreadSanitizer, without position-independent code, which leaves
/// fewer mappings where TSan's shadow memory goes (below, "tsan").
const TSAN: Mode = Mode {
    name: "tsan",
    options: &["-O1", "-g", "-fsanitize=thread", "-fno-pie", "-no-pie"],
};

/// [`strict`]'s build: compiled only, optimized as a release is (CMake's
/// `RelWithDebInfo`), where GCC's warnings that follow the code's paths
/// (`-Wnull-dereference`, `-Wmaybe-uninitialized`, `-Warray-bounds`, and
/// the like) see what `-O1` doesn't inline.
const STRICT_O2: Mode = Mode {
    name: "strict-o2",
    options: &["-O2"],
};

/// [`STRICT_O2`], with the tests defined.
const STRICT_O2_TESTS: Mode = Mode {
    name: "strict-o2-tests",
    options: &["-O2", "-DTEMPER_TESTS"],
};

/// [`STRICT_O2`] at `-O3`, CMake's `Release`, which inlines and unrolls
/// more again (g++'s `-Warray-bounds` in libstdc++'s `vector<bool>` showed
/// only here), for the newest GCC [`strict`] has.
const STRICT_O3: Mode = Mode {
    name: "strict-o3",
    options: &["-O3"],
};

/// [`STRICT_O3`], with the tests defined.
const STRICT_O3_TESTS: Mode = Mode {
    name: "strict-o3-tests",
    options: &["-O3", "-DTEMPER_TESTS"],
};

/// `clang++`'s options for generated code, for [`strict`]: [`STRICT`]'s,
/// as clang names them, less GCC's own (`-Wduplicated-branches`,
/// `-Wduplicated-cond`, `-Wlogical-op`, `-Wuseless-cast`,
/// `-Wstrict-null-sentinel`, `-Wredundant-decls`'s GCC sense, and the
/// levels of `-Wcast-align=strict` and `-Wimplicit-fallthrough=5`), with
/// `-Wmissing-prototypes`, which is GCC's `-Wmissing-declarations` for
/// C++. Checked only in CI: the container has no clang.
const CLANG_STRICT: &[&str] = &[
    "-std=c++14",
    "-Wall",
    "-Wextra",
    "-Wpedantic",
    "-Werror",
    "-Wcast-align",
    "-Wcast-qual",
    "-Wconversion",
    "-Wdate-time",
    "-Wdouble-promotion",
    "-Wextra-semi",
    "-Wfloat-equal",
    "-Wformat=2",
    "-Wimplicit-fallthrough",
    "-Wmissing-prototypes",
    "-Wnon-virtual-dtor",
    "-Wnull-dereference",
    "-Wold-style-cast",
    "-Woverloaded-virtual",
    "-Wpessimizing-move",
    "-Wredundant-move",
    "-Wshadow",
    "-Wsign-conversion",
    "-Wsuggest-override",
    "-Wswitch-enum",
    "-Wundef",
    "-Wvla",
    "-Wzero-as-null-pointer-constant",
];

/// The compilers [`strict`] builds with, each with its `--version`:
/// `TEMPER_STRICT_CXX`, split at spaces (empty for none), or else
/// `g++-12` and `g++-14`, the oldest and newest GCC that CI has, those
/// that run. With `TEMPER_REQUIRE_CHECKERS`, one that's named and
/// doesn't run fails.
fn strict_compilers() -> Vec<(String, String)> {
    let named = env::var("TEMPER_STRICT_CXX").ok();
    let names: Vec<String> = match &named {
        Some(list) => list.split_whitespace().map(String::from).collect(),
        None => vec!["g++-12".into(), "g++-14".into()],
    };
    let mut found = Vec::new();
    for name in names {
        let out = Command::new(&name).arg("--version").output();
        match out {
            Ok(out) if out.status.success() => {
                found.push((name, String::from_utf8_lossy(&out.stdout).into_owned()));
            }
            _ if named.is_some() => kit::missing(&format!("{name} (TEMPER_STRICT_CXX)")),
            _ => eprintln!("strict: skipped {name}, which isn't installed"),
        }
    }
    found
}

/// Runs a command, panicking with its errors if it fails.
fn check(what: &str, mut command: Command) -> Output {
    let out = command.output().unwrap();
    assert!(
        out.status.success(),
        "{what}:\n{}{}",
        String::from_utf8_lossy(&out.stdout),
        String::from_utf8_lossy(&out.stderr)
    );
    out
}

/// The runtime's precompiled header for a mode, built once and kept
/// under `CARGO_TARGET_TMPDIR` until the runtime's headers, the options,
/// or `g++` change: a directory holding `runtime.hpp`, which includes
/// `<temper-core/core.hpp>`, and its `.gch`, for `-include runtime.hpp`.
/// The first build also checks the runtime itself with every warning it
/// tolerates.
fn runtime_pch(gxx: &str, version: &str, mode: &Mode) -> PathBuf {
    runtime_pch_with(
        gxx,
        version,
        mode,
        STRICT,
        Some(&STRICT[..RUNTIME_WARNINGS]),
    )
}

/// [`runtime_pch`], with these warning options in place of [`STRICT`],
/// and the runtime checked with `runtime`'s, if any, when it's built.
fn runtime_pch_with(
    gxx: &str,
    version: &str,
    mode: &Mode,
    warnings: &[&str],
    runtime: Option<&[&str]>,
) -> PathBuf {
    let compiler = Path::new(gxx).file_name().unwrap().to_string_lossy();
    let root = PathBuf::from(env!("CARGO_TARGET_TMPDIR"))
        .join(format!("be-cpp-runtime-{}-{compiler}", mode.name));
    let mut headers: Vec<PathBuf> = fs::read_dir(runtime_dir().join("temper-core"))
        .unwrap()
        .map(|e| e.unwrap().path())
        .collect();
    headers.sort();
    let gch = root.join("runtime.hpp.gch");
    kit::cached(
        &root,
        (version, warnings, mode.flags()),
        &headers,
        &gch,
        || {
            // The runtime as it is, an ordinary directory.
            if let Some(runtime) = runtime {
                let probe = root.join("probe.cpp");
                fs::write(
                    &probe,
                    "#include <temper-core/core.hpp>\n#include <temper-core/net.hpp>\n\
                     #include <temper-core/test_main.hpp>\n",
                )
                .unwrap();
                let mut command = Command::new(gxx);
                command
                    .args(runtime)
                    .args(mode.flags())
                    .arg("-I")
                    .arg(runtime_dir())
                    .args(["-fsyntax-only"])
                    .arg(&probe);
                check("g++, the runtime with its warnings", command);
            }
            fs::write(
                root.join("runtime.hpp"),
                "#include <temper-core/core.hpp>\n",
            )
            .unwrap();
            let mut command = Command::new(gxx);
            command
                .args(warnings)
                .args(mode.flags())
                .arg("-isystem")
                .arg(runtime_dir())
                .args(["-x", "c++-header"])
                .arg(root.join("runtime.hpp"))
                .arg("-o")
                .arg(&gch);
            check("g++, the runtime's precompiled header", command);
        },
    );
    root
}

/// How many compilers run at once: four at most, which the sanitizers'
/// memory allows beside other work, or `TEMPER_CXX_JOBS`, fewer where
/// memory's short (an `-O3` group's `cc1plus` takes up to a gigabyte).
fn jobs() -> usize {
    let cpus = std::thread::available_parallelism().map_or(1, |n| n.get());
    let cap = (env::var("TEMPER_CXX_JOBS").ok())
        .and_then(|j| j.parse().ok())
        .unwrap_or(4);
    cpus.min(cap).max(1)
}

/// Held by [`runs`] and [`strict`] while they build, so that only one
/// has [`jobs`] compilers running at a time (an optimized group's
/// `cc1plus` takes half a gigabyte).
static BUILDS: Mutex<()> = Mutex::new(());

/// Runs `work` on every item, [`jobs`] at a time, each thread taking the
/// next item as it finishes one, giving the results in order.
fn parallel<T: Sync, R: Send>(items: &[T], work: impl Fn(&T) -> R + Sync) -> Vec<R> {
    let next = AtomicUsize::new(0);
    let results: Vec<Mutex<Option<R>>> = items.iter().map(|_| Mutex::new(None)).collect();
    std::thread::scope(|scope| {
        for _ in 0..jobs().min(items.len()) {
            scope.spawn(|| {
                loop {
                    let i = next.fetch_add(1, Ordering::Relaxed);
                    let Some(item) = items.get(i) else { break };
                    *results[i].lock().unwrap() = Some(work(item));
                }
            });
        }
    });
    (results.into_iter())
        .map(|r| r.into_inner().unwrap().unwrap())
        .collect()
}

/// Warnings that a test's own code trips on purpose, off for its library:
/// `semantics/type-checked-locals`' `if` whose branches do the same.
const ALLOWED_WARNINGS: [(&str, &str); 1] = [("type-checked-locals", "-Wno-duplicated-branches")];

/// Compiles one `.cpp` (or a bulk group's) to `object`, with the
/// generated libraries' root (`src`) as an include directory, giving
/// `g++`'s errors, under `what`, if it fails.
fn compile_one(
    gxx: &str,
    warnings: &[&str],
    mode: &Mode,
    pch: &Path,
    src: &Path,
    source: &Path,
    object: &Path,
) -> Result<(), String> {
    fs::create_dir_all(object.parent().unwrap()).unwrap();
    // A library's file, or a bulk group that includes one.
    let text = fs::read_to_string(source).unwrap_or_default();
    let allowed = (ALLOWED_WARNINGS.iter())
        .filter(|(library, _)| {
            let dir = format!("{library}/");
            source.to_string_lossy().contains(&format!("/{dir}")) || text.contains(&dir)
        })
        .map(|&(_, flag)| flag)
        // Of warnings the set has: clang++ has no `-Wduplicated-branches`.
        .filter(|flag| warnings.contains(&flag.replacen("-Wno-", "-W", 1).as_str()));
    let out = Command::new(gxx)
        .args(warnings)
        .args(allowed)
        .args(mode.flags())
        .arg("-isystem")
        .arg(runtime_dir())
        .arg("-I")
        .arg(src)
        .arg("-include")
        .arg(pch.join("runtime.hpp"))
        .arg("-c")
        .arg(source)
        .arg("-o")
        .arg(object)
        .output()
        .unwrap();
    if out.status.success() {
        return Ok(());
    }
    Err(format!(
        "g++ ({}), {}:\n{}{}",
        mode.name,
        source.display(),
        String::from_utf8_lossy(&out.stdout),
        String::from_utf8_lossy(&out.stderr)
    ))
}

/// Compiles `.cpp` files to objects beside `out`, each alone, [`jobs`]
/// at a time, failing with every file's errors.
fn compile(
    gxx: &str,
    mode: &Mode,
    pch: &Path,
    src: &Path,
    out: &Path,
    sources: &[PathBuf],
) -> Vec<PathBuf> {
    compile_with(gxx, STRICT, mode, pch, src, out, sources)
}

/// [`compile`], with these warning options in place of [`STRICT`].
fn compile_with(
    gxx: &str,
    warnings: &[&str],
    mode: &Mode,
    pch: &Path,
    src: &Path,
    out: &Path,
    sources: &[PathBuf],
) -> Vec<PathBuf> {
    let objects: Vec<PathBuf> = sources
        .iter()
        .map(|s| {
            let rel = s.strip_prefix(src).unwrap_or(s);
            out.join(rel).with_extension("o")
        })
        .collect();
    let pairs: Vec<(&PathBuf, &PathBuf)> = sources.iter().zip(&objects).collect();
    let errors: Vec<String> = parallel(&pairs, |(source, object)| {
        compile_one(gxx, warnings, mode, pch, src, source, object)
    })
    .into_iter()
    .filter_map(Result::err)
    .collect();
    assert!(errors.is_empty(), "{}", errors.join("\n"));
    objects
}

/// Whether to build the sanitizers' libraries in bulk ([`compile_bulk`]):
/// unless `TEMPER_CPP_BULK=0`, which builds each file alone.
fn bulk() -> bool {
    env::var("TEMPER_CPP_BULK").map_or(true, |v| v != "0")
}

/// [`compile`], but several files to one object, which instantiates the
/// runtime's templates once a group rather than once a file: [`jobs`]
/// groups, or more, each a `.cpp` including its files, of like sizes,
/// with at most one file of each library, whose modules share a
/// namespace and may share a private name. A group that fails is
/// compiled file by file, and those errors are the ones reported, as
/// alone; if they all pass, the group clashed only with itself. Only for
/// what the plain build has already checked file by file.
fn compile_bulk(
    gxx: &str,
    mode: &Mode,
    pch: &Path,
    src: &Path,
    out: &Path,
    sources: &[PathBuf],
) -> Vec<PathBuf> {
    compile_bulk_with(gxx, STRICT, mode, pch, src, out, sources)
}

/// [`compile_bulk`], with these warning options in place of [`STRICT`].
fn compile_bulk_with(
    gxx: &str,
    warnings: &[&str],
    mode: &Mode,
    pch: &Path,
    src: &Path,
    out: &Path,
    sources: &[PathBuf],
) -> Vec<PathBuf> {
    if !bulk() {
        return compile_with(gxx, warnings, mode, pch, src, out, sources);
    }
    let library = |s: &PathBuf| {
        s.strip_prefix(src)
            .unwrap()
            .components()
            .next()
            .map(|c| c.as_os_str().to_owned())
    };
    let size = |s: &PathBuf| fs::metadata(s).unwrap().len();
    let mut sorted: Vec<&PathBuf> = sources.iter().collect();
    sorted.sort_by_key(|s| std::cmp::Reverse(size(s)));
    let mut groups: Vec<(u64, Vec<PathBuf>)> = vec![(0, Vec::new()); jobs()];
    for source in sorted {
        let open = (groups.iter_mut())
            .filter(|(_, files)| files.iter().all(|f| library(f) != library(source)))
            .min_by_key(|(load, _)| *load);
        match open {
            Some((load, files)) => {
                *load += size(source);
                files.push(source.clone());
            }
            None => groups.push((size(source), vec![source.clone()])),
        }
    }
    groups.retain(|(_, files)| !files.is_empty());
    let units: Vec<(PathBuf, PathBuf, Vec<PathBuf>)> = (groups.into_iter().enumerate())
        .map(|(i, (_, files))| {
            let unit = out.join(format!("bulk-{i}.cpp"));
            let mut text = String::new();
            for file in &files {
                writeln!(text, "#include \"{}\"", file.display()).unwrap();
            }
            fs::create_dir_all(out).unwrap();
            fs::write(&unit, text).unwrap();
            (unit.clone(), unit.with_extension("o"), files)
        })
        .collect();
    let results = parallel(&units, |(unit, object, _)| {
        compile_one(gxx, warnings, mode, pch, src, unit, object)
    });
    let mut objects = Vec::new();
    for ((_, object, files), result) in units.iter().zip(results) {
        if result.is_ok() {
            objects.push(object.clone());
        } else {
            objects.extend(compile_with(gxx, warnings, mode, pch, src, out, files));
            eprintln!(
                "g++ ({}): a bulk group failed, but each file alone passed",
                mode.name
            );
        }
    }
    objects
}

fn link(gxx: &str, mode: &Mode, objects: &[PathBuf], exe: &Path) {
    let mut command = Command::new(gxx);
    command.args(mode.flags()).args(objects).arg("-o").arg(exe);
    if curl() {
        command.arg("-lcurl");
    }
    check(
        &format!("g++ ({}), linking {}", mode.name, exe.display()),
        command,
    );
}

/// An entry point, or a few, that users call with no init first, which
/// run it themselves.
struct FirstUse {
    /// The runner's argument.
    name: &'static str,
    /// The cases whose logs come first.
    logs: &'static [&'static str],
    /// The C++ that prints what each gives, and the lines printed after.
    prints: &'static [(&'static str, &'static str)],
}

/// [`FirstUse`] runs. `first-use-order` enters a module that another
/// imports, then that one, which initializes the first only once, before
/// it.
const FIRST_USE: &[FirstUse] = &[
    FirstUse {
        name: "first-use-function",
        logs: &["modules"],
        prints: &[("modules::sum(3)", "17")],
    },
    FirstUse {
        name: "first-use-order",
        logs: &[],
        prints: &[
            ("modules::twice(2)", "hello, helpers from deep\n4"),
            (
                "modules::sum(3)",
                "hello, modules from deep\nsum 17\nshare 3 42\nown, state 1 engine 4 parts 5\ncalls 0\ncalls now 3\ntrue true true\n17",
            ),
        ],
    },
    FirstUse {
        name: "first-use-import",
        logs: &["imports-values"],
        prints: &[("values::timesMagic(2)", "84")],
    },
    FirstUse {
        name: "first-use-value",
        logs: &[],
        prints: &[("values::magicNumber()", "42")],
    },
    FirstUse {
        name: "first-use-static",
        logs: &["classes-static-properties"],
        prints: &[("static_properties::Simple::other()", "22")],
    },
];

/// What a [`FIRST_USE`] run logs.
fn first_use_expected(cases: &[Case], run: &FirstUse) -> Vec<String> {
    let mut out = Vec::new();
    for key in run.logs {
        let case = cases.iter().find(|c| c.key == *key).unwrap();
        out.extend(case.expected.iter().cloned());
    }
    for (_, printed) in run.prints {
        out.extend(printed.lines().map(String::from));
    }
    out
}

/// The runner: runs the init of the library named on its command line;
/// a [`FIRST_USE`] run; or, for `threads`, `module-state`'s `tick` and
/// `report` from 8 threads, 1,000 times each, the first calls of which
/// run its init, once. It installs stack traces, as a generated
/// `main.cpp` does, so an uncaught failure says what it was.
fn runner(cases: &[Case], first_use: &[FirstUse]) -> String {
    let mut includes = String::new();
    let mut branches = String::new();
    for case in cases {
        includes.push_str(&format!("#include \"{}/init.hpp\"\n", case.library));
        branches.push_str(&format!(
            "  if (name == \"{}\") {{\n    {}::global_init_init();\n  }}\n",
            case.key,
            names::namespace(&case.library)
        ));
    }
    for run in first_use {
        branches.push_str(&format!("  if (name == \"{}\") {{\n", run.name));
        for (code, _) in run.prints {
            branches.push_str(&format!("    std::cout << {code} << \"\\n\";\n"));
        }
        branches.push_str("  }\n");
    }
    format!(
        r#"{includes}#include "temper-core/stack_trace.hpp"

#include <iostream>
#include <string>
#include <thread>
#include <vector>

namespace {{

void threads() {{
  std::vector<std::thread> threads;
  for (int i = 0; i < 8; i++) {{
    threads.emplace_back([] {{
      for (int j = 0; j < 1000; j++) {{
        module_state::tick();
        module_state::report();
      }}
    }});
  }}
  for (std::thread &thread : threads) {{
    thread.join();
  }}
  std::cout << "ticks " << module_state::ticks() << "\n";
}}

}}  // namespace

int main(int argc, char **argv) {{
  temper::core::install_stack_traces();
  if (argc < 2) {{
    return 2;
  }}
  std::string name = argv[1];
  if (name == "threads") {{
    threads();
    return 0;
  }}
{branches}}}
"#
    )
}

/// Runs an executable, comparing what it logs, retrying where
/// ThreadSanitizer can't place its shadow memory (it exits with 66 and
/// "unexpected memory mapping" when the kernel randomizes mappings over
/// more bits than it expects, as Linux 6's default does; disabling that
/// takes `setarch -R` or a sysctl, which a sandbox may not allow, so a
/// few tries do instead), or crashes silently as it starts.
fn run(failures: &mut Vec<String>, name: &str, exe: &Path, args: &[&str], expected: &[String]) {
    for _ in 0..40 {
        let out = Command::new(exe).args(args).output().unwrap();
        let stdout = String::from_utf8_lossy(&out.stdout);
        let stderr = String::from_utf8_lossy(&out.stderr);
        if out.status.code() == Some(66) && stderr.contains("unexpected memory mapping") {
            continue;
        }
        // Or it crashes as it starts, before the program prints or
        // ThreadSanitizer reports anything.
        let silent_crash =
            out.status.code().is_none() && out.stdout.is_empty() && stderr.is_empty();
        if name.ends_with("(tsan)") && silent_crash {
            continue;
        }
        let actual: Vec<&str> = stdout.lines().collect();
        // Without libcurl, std `net`'s requests say so.
        let stderr = stderr.strip_prefix(NO_CLIENT).unwrap_or(&stderr);
        if !out.status.success() || actual != expected || !stderr.is_empty() {
            failures.push(format!(
                "{name}: expected {expected:?}, got {actual:?} ({})\n{stderr}",
                out.status
            ));
        }
        return;
    }
    failures.push(format!(
        "{name}: ThreadSanitizer couldn't map its memory in 40 tries"
    ));
}

/// [`run`] for a case: one expected to fail ([`Case::fails`]) must fail,
/// after logging what's expected, whatever it says on stderr.
fn run_case(failures: &mut Vec<String>, name: &str, exe: &Path, args: &[&str], case: &Case) {
    if !case.fails {
        return run(failures, name, exe, args, &case.expected);
    }
    let out = Command::new(exe).args(args).output().unwrap();
    kit::check_case(failures, name, &out, case);
}

#[test]
fn runs() {
    let Some((gxx, version)) = find_gxx() else {
        kit::missing("no g++ (on PATH, or named by CXX)");
        return;
    };
    let _builds = BUILDS.lock().unwrap_or_else(|e| e.into_inner());
    kit::with_stack(move || {
        let out = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-cpp");
        let _ = fs::remove_dir_all(&out);
        let src = out.join("src");
        // Libraries with host code are each `work`, so each builds alone,
        // but those that run as tests, which are named as themselves and
        // build with std's `testing` as other tests do (their host code
        // a header, as the shared `host-error-tests`' is).
        let (connected, cases): (Vec<Case>, Vec<Case>) =
            cases().into_iter().partition(connected_alone);
        // Those that run as tests alone, and the rest also as programs.
        let (as_tests, cases): (Vec<Case>, Vec<Case>) =
            cases.into_iter().partition(|c| c.tests.is_some());
        let mut inits = Vec::new();
        let mut mains = Vec::new();
        // std, which tests and the libraries that import std's modules
        // use, beside the libraries.
        let std = translate(&kit::std_program(), &CPP).unwrap_or_else(|e| panic!("std: {e}"));
        let mut std_sources = Vec::new();
        for file in std {
            let path = src.join(&file.path);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(&path, &file.text).unwrap();
            if file.path.ends_with(".cpp") && !file.path.ends_with("/main.cpp") {
                std_sources.push(path);
            }
        }
        // Each library with tests: its modules, std's, then its `test.cpp`.
        let mut tested: Vec<(&Case, Vec<PathBuf>)> = Vec::new();
        for case in cases.iter().chain(&as_tests) {
            let files = generate(case);
            if files.iter().any(|f| f.path.ends_with("/test.cpp")) {
                let mut sources: Vec<PathBuf> = (files.iter())
                    .filter(|f| f.path.ends_with(".cpp") && !f.path.ends_with("/main.cpp"))
                    .map(|f| src.join(&f.path))
                    .chain(std_sources.iter().cloned())
                    .collect();
                sources.sort_by_key(|s| s.ends_with("test.cpp"));
                tested.push((case, sources));
            }
            if case.tests.is_some() {
                for file in files {
                    let path = src.join(&file.path);
                    fs::create_dir_all(path.parent().unwrap()).unwrap();
                    fs::write(&path, &file.text).unwrap();
                }
                continue;
            }
            for file in files {
                let path = src.join(&file.path);
                fs::create_dir_all(path.parent().unwrap()).unwrap();
                fs::write(&path, &file.text).unwrap();
                if file.path.ends_with("/main.cpp") {
                    mains.push(path);
                } else if file.path.ends_with(".cpp") && !file.path.ends_with("/test.cpp") {
                    inits.push(path);
                }
            }
        }
        // std's modules, for the runner, where a library imports them.
        if cases.iter().any(Case::needs_std) {
            inits.extend(std_sources.iter().cloned());
        }
        let runner_cpp = src.join("runner.cpp");
        fs::write(&runner_cpp, runner(&cases, FIRST_USE)).unwrap();
        let mut failures = Vec::new();

        // Every library, strictly, each `main.cpp` too, and the runner;
        // one `main.cpp` linked as Kotlin's program is.
        let pch = runtime_pch(&gxx, &version, &PLAIN);
        let bin = out.join("plain");
        let mut sources = inits.clone();
        sources.extend(mains.iter().cloned());
        sources.push(runner_cpp.clone());
        let objects = compile(&gxx, &PLAIN, &pch, &src, &bin, &sources);
        let (init_objects, rest) = objects.split_at(inits.len());
        let (main_objects, runner_object) = rest.split_at(mains.len());
        let program = bin.join("program");
        let first = src.join(&cases[0].library);
        let first_objects = (inits.iter().zip(init_objects))
            .filter(|(source, _)| source.starts_with(&first))
            .map(|(_, object)| object.clone());
        let mut program_objects = vec![main_objects[0].clone()];
        program_objects.extend(first_objects);
        link(&gxx, &PLAIN, &program_objects, &program);
        run(&mut failures, "main.cpp", &program, &[], &cases[0].expected);
        let mut all = init_objects.to_vec();
        all.extend(runner_object.iter().cloned());
        let runner_exe = bin.join("runner");
        link(&gxx, &PLAIN, &all, &runner_exe);
        let threads_expected = kit::threads_expected(&cases);
        for case in &cases {
            run_case(&mut failures, &case.key, &runner_exe, &[&case.key], case);
        }
        run(
            &mut failures,
            "module-state threads",
            &runner_exe,
            &["threads"],
            &threads_expected,
        );
        for first in FIRST_USE {
            let expected = first_use_expected(&cases, first);
            run(
                &mut failures,
                first.name,
                &runner_exe,
                &[first.name],
                &expected,
            );
        }

        // Tests, with std's `testing`, their failures compared with what
        // each case expects.
        if !tested.is_empty() {
            let pch = runtime_pch(&gxx, &version, &TESTS);
            let bin = out.join("tests");
            for (case, sources) in &tested {
                let key = &case.key;
                let objects = compile(&gxx, &TESTS, &pch, &src, &bin.join(key), sources);
                let exe = bin.join(key).join("test");
                link(&gxx, &TESTS, &objects, &exe);
                let dir = exe.parent().unwrap();
                let run = Command::new(&exe).current_dir(dir).output().unwrap();
                let actual = kit::junit_file_results(&dir.join("test-results.xml"))
                    .filter(|_| run.status.success());
                kit::check_tests(&mut failures, case, actual, &run, kit::test_name_words);
            }
        }

        // Each library with host code, alone, its host `.cpp`s with its
        // modules, as its CMake project builds them, and its `main.cpp`
        // linked as Kotlin's program, plainly and under the sanitizers.
        // Host `.cpp`s aren't written for [`STRICT`] (the functional
        // test's `length` converts `size()` to `int32_t`), so they build
        // with C++14 alone; the `_connected.hpp` that generated `.cpp`s
        // include answers to it with them.
        for case in &connected {
            let root = out.join("connected").join(&case.key);
            let src = root.join("src");
            let mut sources = Vec::new();
            let mut host = Vec::new();
            for file in generate(case) {
                let path = src.join(&file.path);
                fs::create_dir_all(path.parent().unwrap()).unwrap();
                fs::write(&path, &file.text).unwrap();
                let rest = &file.path[case.library.len() + 1..];
                if case.host_files.iter().any(|(p, _)| p == rest) {
                    if rest.ends_with(".cpp") {
                        host.push(path);
                    }
                } else if file.path.ends_with(".cpp") {
                    sources.push(path);
                }
            }
            for mode in [&PLAIN, &ASAN] {
                let pch = runtime_pch(&gxx, &version, mode);
                let bin = root.join(mode.name);
                let mut objects = compile(&gxx, mode, &pch, &src, &bin, &sources);
                objects.extend(compile_with(
                    &gxx,
                    &["-std=c++14"],
                    mode,
                    &pch,
                    &src,
                    &bin,
                    &host,
                ));
                let exe = bin.join("program");
                link(&gxx, mode, &objects, &exe);
                let name = format!("{} ({})", case.key, mode.name);
                run_case(&mut failures, &name, &exe, &[], case);
            }
        }

        // The same under AddressSanitizer and UndefinedBehaviorSanitizer.
        // In bulk, as the plain build checked each file alone.
        let pch = runtime_pch(&gxx, &version, &ASAN);
        let bin = out.join("asan");
        let mut sources = inits.clone();
        sources.push(runner_cpp.clone());
        let objects = compile_bulk(&gxx, &ASAN, &pch, &src, &bin, &sources);
        let runner_exe = bin.join("runner");
        link(&gxx, &ASAN, &objects, &runner_exe);
        for case in &cases {
            let name = format!("{} (asan, ubsan)", case.key);
            run_case(&mut failures, &name, &runner_exe, &[&case.key], case);
        }
        let name = "module-state threads (asan, ubsan)";
        run(
            &mut failures,
            name,
            &runner_exe,
            &["threads"],
            &threads_expected,
        );
        for first in FIRST_USE {
            let expected = first_use_expected(&cases, first);
            let label = format!("{} (asan, ubsan)", first.name);
            run(&mut failures, &label, &runner_exe, &[first.name], &expected);
        }

        // The threads under ThreadSanitizer, with only what they run:
        // `module-state`, std if it imports std, and a runner of its own;
        // with libcurl, std `net`'s tests too, whose default client
        // answers from a thread of its own.
        let state = (cases.iter())
            .find(|c| c.library == "module-state")
            .unwrap();
        let mut tsan_cases = vec![state.clone()];
        if curl() {
            let net = |c: &&Case| kit::NET.iter().any(|t| c.key == t.replace('/', "-"));
            tsan_cases.extend(cases.iter().filter(net).cloned());
        }
        let tsan_cpp = src.join("runner-tsan.cpp");
        fs::write(&tsan_cpp, runner(&tsan_cases, &[])).unwrap();
        let pch = runtime_pch(&gxx, &version, &TSAN);
        let bin = out.join("tsan");
        let wanted = |s: &PathBuf| {
            (tsan_cases.iter()).any(|c| {
                s.starts_with(src.join(&c.library)) || c.needs_std() && std_sources.contains(s)
            })
        };
        let mut sources: Vec<PathBuf> = inits.iter().filter(|s| wanted(s)).cloned().collect();
        sources.push(tsan_cpp);
        let objects = compile(&gxx, &TSAN, &pch, &src, &bin, &sources);
        let runner_exe = bin.join("runner");
        link(&gxx, &TSAN, &objects, &runner_exe);
        let name = "module-state threads (tsan)";
        run(
            &mut failures,
            name,
            &runner_exe,
            &["threads"],
            &threads_expected,
        );
        for case in &tsan_cases[1..] {
            let name = format!("{} (tsan)", case.key);
            run_case(&mut failures, &name, &runner_exe, &[&case.key], case);
        }

        kit::assert_none(&failures);
    });
}

/// Every library's generated C++, as other people build it: each `.cpp`
/// of every case [`runs`] builds, std's, and the generated files of the
/// libraries with host code, compiled (not linked) at `-O2` with every
/// warning an error, by each of [`strict_compilers`]: g++ with
/// [`STRICT`], clang++ with [`CLANG_STRICT`]; the newest GCC again at
/// `-O3` ([`STRICT_O3`]), but not the libraries with host code. Every
/// library compiles without `TEMPER_TESTS`, as users build it, and those
/// with tests, and std, again with it. In bulk ([`compile_bulk`]),
/// which inlines across a group's files, a library with host code alone.
#[test]
fn strict() {
    let compilers = strict_compilers();
    if compilers.is_empty() {
        eprintln!("strict: skipped, no compiler (TEMPER_STRICT_CXX, or g++-12 and g++-14)");
        return;
    }
    let _builds = BUILDS.lock().unwrap_or_else(|e| e.into_inner());
    kit::with_stack(move || {
        let out = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-cpp-strict");
        let _ = fs::remove_dir_all(&out);
        let src = out.join("src");
        let write = |root: &Path, files: Vec<OutFile>| -> Vec<PathBuf> {
            let mut written = Vec::new();
            for file in files {
                let path = root.join(&file.path);
                fs::create_dir_all(path.parent().unwrap()).unwrap();
                fs::write(&path, &file.text).unwrap();
                written.push((path, file.path));
            }
            (written.into_iter())
                .filter(|(_, p)| p.ends_with(".cpp"))
                .map(|(path, _)| path)
                .collect()
        };
        let (connected, cases): (Vec<Case>, Vec<Case>) =
            cases().into_iter().partition(connected_alone);
        let std = translate(&kit::std_program(), &CPP).unwrap_or_else(|e| panic!("std: {e}"));
        let std_sources: Vec<PathBuf> = (write(&src, std).into_iter())
            .filter(|s| !s.ends_with("main.cpp"))
            .collect();
        let mut plain = std_sources.clone();
        let mut tested = std_sources;
        // Every `main.cpp` is the same few lines, which one stands for;
        // and each defines `main`, as each `test.cpp` does, so they're
        // compiled alone, not in bulk.
        let mut main = None;
        let mut test_mains = Vec::new();
        for case in &cases {
            let sources = write(&src, generate(case));
            let main_cpp = |s: &PathBuf| s.ends_with("main.cpp");
            if sources.iter().any(|s| s.ends_with("test.cpp")) {
                let (test, rest): (Vec<PathBuf>, Vec<PathBuf>) = (sources.iter())
                    .filter(|&s| !main_cpp(s))
                    .cloned()
                    .partition(|s| s.ends_with("test.cpp"));
                tested.extend(rest);
                test_mains.extend(test);
            }
            // Every library's modules, those with tests too, as a user's
            // build without `TEMPER_TESTS` has them (what only tests call
            // is in their `#ifdef`); a `main` of one that runs as a
            // program, as in [`runs`].
            if case.tests.is_none() {
                main = main.or_else(|| sources.iter().find(|s| main_cpp(s)).cloned());
            }
            let module = |s: &PathBuf| !main_cpp(s) && !s.ends_with("test.cpp");
            plain.extend(sources.into_iter().filter(module));
        }
        // Each library with host code, its generated files alone.
        let connected: Vec<(PathBuf, Vec<PathBuf>)> = (connected.iter())
            .map(|case| {
                let root = out.join("connected").join(&case.key);
                let host = |s: &PathBuf| {
                    let rest = s
                        .strip_prefix(root.join("src").join(&case.library))
                        .unwrap();
                    (case.host_files.iter()).any(|(p, _)| Path::new(p) == rest)
                };
                let sources = write(&root.join("src"), generate(case));
                let sources = sources.into_iter().filter(|s| !host(s)).collect();
                (root, sources)
            })
            .collect();
        // The newest GCC, the last named, at `-O3` too.
        let newest = compilers.iter().rposition(|(_, v)| !v.contains("clang"));
        for (i, (cxx, version)) in compilers.iter().enumerate() {
            let clang = version.contains("clang");
            let warnings = if clang { CLANG_STRICT } else { STRICT };
            let bin = out.join(Path::new(cxx).file_name().unwrap());
            let mains = [main.clone().unwrap()];
            let mut modes = vec![
                (&STRICT_O2, &plain, &mains[..]),
                (&STRICT_O2_TESTS, &tested, &test_mains[..]),
            ];
            if Some(i) == newest {
                modes.push((&STRICT_O3, &plain, &mains[..]));
                modes.push((&STRICT_O3_TESTS, &tested, &test_mains[..]));
            }
            for (mode, sources, mains) in modes {
                let pch = runtime_pch_with(cxx, version, mode, warnings, None);
                let bin = bin.join(mode.name);
                compile_bulk_with(cxx, warnings, mode, &pch, &src, &bin, sources);
                compile_with(cxx, warnings, mode, &pch, &src, &bin.join("main"), mains);
            }
            let pch = runtime_pch_with(cxx, version, &STRICT_O2, warnings, None);
            for (root, sources) in &connected {
                let bin = root.join(Path::new(cxx).file_name().unwrap());
                compile_with(
                    cxx,
                    warnings,
                    &STRICT_O2,
                    &pch,
                    &root.join("src"),
                    &bin,
                    sources,
                );
            }
        }
    });
}

/// A file's lines, each with whether it's inside `#ifdef TEMPER_TESTS`.
fn under_tests(text: &str) -> Vec<(bool, &str)> {
    let mut depth = 0;
    let mut tests = None;
    let mut found = Vec::new();
    for line in text.lines() {
        if line.starts_with("#if") {
            depth += 1;
            if line == "#ifdef TEMPER_TESTS" && tests.is_none() {
                tests = Some(depth);
            }
        } else if line.starts_with("#endif") {
            if tests == Some(depth) {
                tests = None;
            }
            depth -= 1;
        }
        found.push((tests.is_some(), line));
    }
    found
}

/// `@keepTest` keeps a function that no Temper code calls for tests' host
/// code, as Kotlin's `ReachabilityTracer` marks it test-reachable: only
/// where `TEMPER_TESTS` is defined, not `static`, and declared in the
/// header there, as an exported function is for users; the private
/// function only it calls is test-only too. `@keep` on a class keeps it
/// as every class is kept, where host code may read it.
#[test]
fn kept_for_tests() {
    kit::with_stack(|| {
        let case = (cases().into_iter())
            .find(|c| c.key == "testing-asserts")
            .unwrap();
        let files = generate(&case);
        let file = |name: &str| {
            let file = files.iter().find(|f| f.path.ends_with(name));
            &file.unwrap_or_else(|| panic!("no {name}")).text
        };
        let (header, source) = (file("/init.hpp"), file("/init.cpp"));
        let lines = |text, needle: &str| -> Vec<bool> {
            (under_tests(text).into_iter())
                .filter(|(_, line)| line.contains(needle))
                .map(|(tests, _)| tests)
                .collect()
        };
        // Declared in the header, under `TEMPER_TESTS`.
        assert_eq!(lines(header, "int32_t keptForTests("), [true], "{header}");
        // Defined, not `static`, under `TEMPER_TESTS`; and its callee
        // too, `static` as private functions are.
        let defined = lines(source, "int32_t keptForTests(");
        assert!(
            !defined.is_empty() && defined.iter().all(|&t| t),
            "{source}"
        );
        assert!(!source.contains("static int32_t keptForTests("), "{source}");
        let callee = lines(source, "static int32_t sumOf(");
        assert!(!callee.is_empty() && callee.iter().all(|&t| t), "{source}");
        // The kept class, outside `TEMPER_TESTS`.
        assert_eq!(lines(header, "struct KeptForHost :"), [false], "{header}");
    });
}

/// The shared `reach` program's test parts, in its header and `.cpp`,
/// only where `TEMPER_TESTS` is defined, and what nothing reaches
/// nowhere, as tmpl's `reach::partition` splits the program; `runs` and
/// `strict` build it strictly with and without `TEMPER_TESTS`, and run
/// its test.
#[test]
fn test_parts_apart() {
    kit::with_stack(|| {
        let case = (cases().into_iter()).find(|c| c.key == "reach").unwrap();
        let files = generate(&case);
        let modules = (files.iter()).filter(|f| {
            (f.path.ends_with(".hpp") || f.path.ends_with(".cpp"))
                && !f.path.ends_with("/main.cpp")
                && !f.path.ends_with("/test.cpp")
        });
        let lines = modules.flat_map(|file| {
            (under_tests(&file.text).into_iter())
                .map(|(tests, line)| (file.path.as_str(), tests, line))
        });
        kit::check_test_parts_apart(lines, &kit::REACH_TESTS_ONLY, &kit::REACH_UNREACHED);
    });
}

/// Whether a case has host code and builds alone ([`runs`]): all but
/// those that run as tests.
fn connected_alone(case: &Case) -> bool {
    !case.host_files.is_empty() && case.tests.is_none()
}

/// Host code that a connected function needs, missing: `NotYet`, naming
/// the file, where Kotlin's fails in `g++`.
#[test]
fn missing_host_code() {
    kit::with_stack(|| {
        let mut case = (cases().into_iter())
            .find(|c| c.key == "functions-connected")
            .unwrap();
        case.host_files.retain(|(path, _)| path != "_connected.hpp");
        let err = translate(&kit::program_for(&case, &CPP.rules), &CPP).unwrap_err();
        assert!(err.what.contains("_connected.hpp"), "{err}");
    });
}

/// `co_await.cpp`, a C++ coroutine awaiting Temper promises, built with
/// `-std=c++20`, strictly and under the sanitizers, logs as they settle,
/// then loops over a generator with a range-for, and steps one again
/// after a failed step, which is done; then `tests/host/interop.cpp`
/// does the same with the `async-turns` program's generated library.
/// Skipped, with a message, where `g++` has no coroutines.
#[test]
fn co_await_interop() {
    let Some((gxx, _)) = find_gxx() else {
        kit::missing("no g++ (on PATH, or named by CXX)");
        return;
    };
    let out = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-cpp-co-await");
    fs::create_dir_all(&out).unwrap();
    let probe = out.join("probe.cpp");
    fs::write(
        &probe,
        "#include <coroutine>\n#ifndef __cpp_impl_coroutine\n#error no coroutines\n#endif\n",
    )
    .unwrap();
    let probed = Command::new(&gxx)
        .args(["-std=c++20", "-fsyntax-only"])
        .arg(&probe)
        .output()
        .unwrap();
    if !probed.status.success() {
        eprintln!("skipped: {gxx} has no C++20 coroutines");
        return;
    }
    let source = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/co_await.cpp");
    let exe = out.join("co_await");
    let mut command = Command::new(&gxx);
    command
        .args(STRICT)
        .arg("-std=c++20")
        .args(ASAN.flags())
        .arg("-isystem")
        .arg(runtime_dir())
        .arg(&source)
        .arg("-o")
        .arg(&exe);
    check("g++ (c++20), co_await.cpp", command);
    let mut failures = Vec::new();
    let expected: Vec<String> = [
        "got ready",
        "waiting",
        "broken",
        "done",
        "for 1",
        "for 2",
        "for 3",
        "failed step",
        "then done 1",
    ]
    .map(String::from)
    .into();
    run(
        &mut failures,
        "co_await (asan, ubsan)",
        &exe,
        &[],
        &expected,
    );
    // `tests/host/interop.cpp`, the same against generated code: the
    // `async-turns` program's library, its `init.cpp` built as C++20 too.
    kit::with_stack(move || {
        let case = cases()
            .into_iter()
            .find(|c| c.key == "async-turns")
            .unwrap();
        let src = out.join("src");
        let mut sources = Vec::new();
        for file in generate(&case) {
            let path = src.join(&file.path);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(&path, &file.text).unwrap();
            if file.path.ends_with(".cpp") && !file.path.ends_with("/main.cpp") {
                sources.push(path);
            }
        }
        sources.push(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/host/interop.cpp"));
        let exe = out.join("interop");
        let mut command = Command::new(&gxx);
        command
            .args(STRICT)
            .arg("-std=c++20")
            .args(ASAN.flags())
            .arg("-isystem")
            .arg(runtime_dir())
            .arg("-I")
            .arg(&src)
            .args(&sources)
            .arg("-o")
            .arg(&exe);
        check("g++ (c++20), host/interop.cpp", command);
        let expected: Vec<String> = (case.expected.iter().cloned())
            .chain(
                [
                    "co_await 2",
                    "range-for 0,1,2",
                    "failed step",
                    "then done 1",
                    "fibonacci 0,1,1,2,3,5,8,13",
                    "collatz 7,22,11,34,17,52,26,13,40,20,10,5,16,8,4,2,1",
                    "halve 4 caught",
                ]
                .map(String::from),
            )
            .collect();
        run(
            &mut failures,
            "host interop (asan, ubsan)",
            &exe,
            &[],
            &expected,
        );
        kit::assert_none(&failures);
    });
}

/// `tests/host/net.cpp` plugs a client of its own into std `net` from
/// host code, answering as the test server does from threads of its own,
/// then runs the `netresponse` tests' generated libraries, with std's
/// `net`, which log what the tests expect, under AddressSanitizer and
/// UndefinedBehaviorSanitizer, then ThreadSanitizer, which checks the
/// handoff from the client's threads to Temper's.
#[test]
fn net_client_from_host() {
    let Some((gxx, _)) = find_gxx() else {
        kit::missing("no g++ (on PATH, or named by CXX)");
        return;
    };
    kit::with_stack(move || {
        let out = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-cpp-net");
        let _ = fs::remove_dir_all(&out);
        let src = out.join("src");
        let mut sources = Vec::new();
        let mut write = |files: Vec<OutFile>| {
            for file in files {
                let path = src.join(&file.path);
                fs::create_dir_all(path.parent().unwrap()).unwrap();
                fs::write(&path, &file.text).unwrap();
                // Of std, only `net`, which the tests import.
                let wanted = !file.path.starts_with("std/") || file.path == "std/net.cpp";
                if file.path.ends_with(".cpp") && !file.path.ends_with("/main.cpp") && wanted {
                    sources.push(path);
                }
            }
        };
        let cases: Vec<Case> = (kit::functional_cases("cpp", &kit::NET).into_iter())
            .filter(|c| kit::NET.iter().any(|t| c.key == t.replace('/', "-")))
            .collect();
        assert_eq!(cases.len(), 2);
        for case in &cases {
            write(generate(case));
        }
        write(translate(&kit::std_program(), &CPP).unwrap_or_else(|e| panic!("std: {e}")));
        sources.push(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/host/net.cpp"));
        let expected: Vec<String> = (cases.iter())
            .flat_map(|c| c.expected.iter().cloned())
            .chain(["requests 3".to_string()])
            .collect();
        let mut failures = Vec::new();
        for mode in [&ASAN, &TSAN] {
            let exe = out.join(format!("net-{}", mode.name));
            let mut command = Command::new(&gxx);
            command
                .args(STRICT)
                .args(mode.flags())
                .arg("-isystem")
                .arg(runtime_dir())
                .arg("-I")
                .arg(&src)
                .args(&sources)
                .arg("-o")
                .arg(&exe);
            if curl() {
                command.arg("-lcurl");
            }
            check(&format!("g++ ({}), host/net.cpp", mode.name), command);
            let name = format!("host net client ({})", mode.name);
            run(&mut failures, &name, &exe, &[], &expected);
        }
        kit::assert_none(&failures);
    });
}

/// The lines `tests/expected.cpp` prints, the same with the shim and with
/// `std::expected`.
const EXPECTED_LINES: &[&str] = &[
    "value 1 1 1 1 1",
    "error 0 z 7",
    "value() threw for z",
    "wider 0 missing (key: q) 1",
    "arrow 2 hi",
    "void 1 0 check",
    "void assigned 0",
    "void value() threw for check",
    "live 2",
    "assigned an error 1 0",
    "assigned a value 2 1",
    "moved 1 1",
    "self 1",
    "all gone 0",
    "what missing (key: first)",
    "cause 1 missing (key: first)",
    "kept missing (key: first)",
    "no loop 1",
    "report missing (key: third)",
    "caused by: missing (key: second)",
    "caused by: missing (key: first)",
    "uncaught missing (key: third)",
    "caused by: missing (key: second)",
    "caused by: missing (key: first) 1",
    "cast 1",
];

/// The standards [`expected_shim`] and [`failures_interop`] build with,
/// each with what it adds to [`STRICT`], and whether it's the shim: C++14,
/// and C++23 where `g++` has `std::expected`, alone and with the shim
/// forced (`TEMPER_EXPECTED_SHIM`).
fn expected_standards(gxx: &str, out: &Path) -> Vec<(&'static str, Vec<&'static str>)> {
    let mut standards = vec![("c++14", vec!["-std=c++14"])];
    let probe = out.join("expected-probe.cpp");
    fs::write(
        &probe,
        "#include <version>\n#if !defined(__cpp_lib_expected)\n#error no std::expected\n#endif\n",
    )
    .unwrap();
    let probed = (Command::new(gxx)
        .args(["-std=c++23", "-fsyntax-only"])
        .arg(&probe))
    .output()
    .is_ok_and(|o| o.status.success());
    if probed {
        standards.push(("c++23", vec!["-std=c++23"]));
        standards.push(("c++23 shim", vec!["-std=c++23", "-DTEMPER_EXPECTED_SHIM"]));
    } else {
        eprintln!("{gxx} has no std::expected: the shim alone");
    }
    standards
}

/// `tests/expected.cpp`, the runtime's `Expected` and `Failure` on their
/// own: strictly and under the sanitizers, as C++14 (the shim) and C++23
/// (`std::expected`, and the shim again), each printing
/// [`EXPECTED_LINES`], so the shim behaves as `std::expected` does for
/// what it has.
#[test]
fn expected_shim() {
    let Some((gxx, _)) = find_gxx() else {
        kit::missing("no g++ (on PATH, or named by CXX)");
        return;
    };
    let out = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-cpp-expected");
    let _ = fs::remove_dir_all(&out);
    fs::create_dir_all(&out).unwrap();
    let source = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/expected.cpp");
    let expected: Vec<String> = EXPECTED_LINES.iter().map(|l| l.to_string()).collect();
    let mut failures = Vec::new();
    for (name, flags) in expected_standards(&gxx, &out) {
        let exe = out.join(name.replace(' ', "-"));
        let mut command = Command::new(&gxx);
        command
            .args(STRICT)
            .args(&flags)
            .args(ASAN.flags())
            .arg("-isystem")
            .arg(runtime_dir())
            .arg(&source)
            .arg("-o")
            .arg(&exe);
        check(&format!("g++ ({name}), expected.cpp"), command);
        run(
            &mut failures,
            &format!("expected ({name})"),
            &exe,
            &[],
            &expected,
        );
    }
    kit::assert_none(&failures);
    let _ = fs::remove_dir_all(&out);
}

/// `tests/host/failures.cpp`, host code using the `cpp-failures`
/// program's declared failures, built with its generated library, all
/// strictly, under the sanitizers, as C++14 (the runtime's shim) and
/// C++23 (`std::expected`): it logs what the library's top level does,
/// then a line per check.
#[test]
fn failures_interop() {
    let Some((gxx, _)) = find_gxx() else {
        kit::missing("no g++ (on PATH, or named by CXX)");
        return;
    };
    kit::with_stack(move || {
        let out = PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join("be-cpp-failures");
        let _ = fs::remove_dir_all(&out);
        let case = (cases().into_iter())
            .find(|c| c.key == "cpp-failures")
            .unwrap();
        let src = out.join("src");
        let mut sources = Vec::new();
        for file in generate(&case) {
            let path = src.join(&file.path);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(&path, &file.text).unwrap();
            if file.path.ends_with(".cpp") && !file.path.ends_with("/main.cpp") {
                sources.push(path);
            }
        }
        sources.push(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/host/failures.cpp"));
        let expected: Vec<String> = (case.expected.iter().cloned())
            .chain(
                [
                    "NotFound z: not found (key: z)",
                    "found 1 -1",
                    "base NotFound y",
                    "base BadSign ?",
                    "both 0",
                    "sign BadSign x: bad sign (text: x)",
                    "sign EmptyText: no text",
                    "LookupFailed lookup failed (name: port), caused by not found (key: port), \
                     a NotFound cause 1, what lookup failed (name: port)",
                    "made before lookup failed (name: early), caused by not found (key: early)",
                    "thrown again lookup failed (name: first), caused by not found (key: first)",
                    "panic passes panic",
                    "known subtype bad sign",
                    "unknown subtype panics panic",
                ]
                .map(String::from),
            )
            .collect();
        let mut failures = Vec::new();
        for (name, flags) in expected_standards(&gxx, &out) {
            let exe = out.join(name.replace(' ', "-"));
            let mut command = Command::new(&gxx);
            command
                .args(STRICT)
                .args(&flags)
                .args(ASAN.flags())
                .arg("-isystem")
                .arg(runtime_dir())
                .arg("-I")
                .arg(&src)
                .args(&sources)
                .arg("-o")
                .arg(&exe);
            check(&format!("g++ ({name}), host/failures.cpp"), command);
            let label = format!("failures interop ({name}, asan, ubsan)");
            run(&mut failures, &label, &exe, &[], &expected);
        }
        kit::assert_none(&failures);
        let _ = fs::remove_dir_all(&out);
    });
}
