//! Tests of the translation into tmpl ([`temper_frontend::to_tmpl`]) on
//! Kotlin Temper's functional tests, in `temper-funtests`'s `cases/`.
//!
//! - `subset_a_goldens`: each test that subset A covers, and those with
//!   classes, nullable types, generics, function values, parameters'
//!   defaults, core's other types, and std's classes' members that
//!   subset B's steps cover,
//!   translates, the validator finds no problems, the text round trips
//!   (with its spans), and its canonical text, without spans, matches the
//!   golden in `tests/to_tmpl/`, named for the test's path
//!   (`algos-fibonacci.tmpl`), with Temper's version as the sentinel
//!   `0.0.0-alpha.0.0` (`temper_tmpl::version::sentinel`;
//!   `std_library`'s `std.tmpl` names it as it is).
//!   To accept new or changed output, run with `UPDATE_SNAPSHOTS=1` and
//!   review the diff.
//! - `every_functional_test_translates_or_says_why`: no functional test
//!   panics the translation or gives tmpl the validator rejects; the rest
//!   say what isn't covered yet. Each that translates, and std, is
//!   partitioned by reach, validly, so each part keeps what it uses
//!   ([`check_partition`]).
//! - `reach_as_kotlins_*`: Kotlin Temper's own reachability tests, nearly
//!   as written, reached as Kotlin marks them.

use std::{env, fs, path::PathBuf};
use temper_frontend::library::Library;
use temper_frontend::project::load_imports;
use temper_frontend::settle::{Settled, settle};
use temper_frontend::sources::Sources;
use temper_frontend::to_tmpl::{NotYet, gaps, to_tmpl};
use temper_syntax::lex::SourceKind;
use temper_tmpl::coroutines::{CoroutineStrategy, lower};
use temper_tmpl::reach::{Reach, partition, reach, reach_for};
use temper_tmpl::version::current_as_sentinel;
use temper_tmpl::{Facts, Node, NodeId, Profile, Program, difference, text, validate};

/// The functional tests subset A covers, by their paths.
const SUBSET_A: [&str; 7] = [
    "algos/fibonacci",
    "algos/hello-world",
    "control-flow/if-return",
    "control-flow/loop-reenterable",
    "names/nonascii",
    "types/int/basics",
    "types/string/is-empty",
];

/// The functional tests with classes and interfaces that translate
/// (subset B's first step), as the backends' testkit lists them.
const CLASSES: [&str; 14] = [
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

/// The functional tests of names like core's, and `core` and `builtins`
/// as ordinary names, as the backends' testkit lists them.
const NAMES: [&str; 2] = ["names/core-named", "names/core-shadows"];

/// The functional tests with nullable types, `orelse`, `bubble()`, and
/// `panic()` that translate, as the backends' testkit lists them.
const NULLABLES: [&str; 4] = [
    "control-flow/bubble",
    "semantics/mutually-referencing-types",
    "types/float/basics",
    "types/int/limits",
];

/// The functional tests with generic types and core's lists that
/// translate, as the backends' testkit lists them.
const GENERICS: [&str; 4] = [
    "casts/specific",
    "imports/namespaces",
    "imports/values",
    "types/list/empty",
];

/// The functional tests with function values (literals, references,
/// calls of values, and core's members that take functions) that
/// translate, as the backends' testkit lists them.
const FUNCTIONS: [&str; 7] = [
    "control-flow/for-of-folded",
    "functions/as-values",
    "functions/constructor-callbacks",
    "functions/locals",
    "functions/simple-locals",
    "imports/functions",
    "types/list/sorting",
];

/// The functional tests with parameters' defaults that translate, as the
/// backends' testkit lists them.
const DEFAULTS: [&str; 1] = ["classes/property-order"];

/// The functional tests with core's other types and members (string
/// indices, `StringBuilder`, `Empty`, and more of `String`'s and
/// `Float64`'s members) that translate, as the backends' testkit lists
/// them.
const CORE_TYPES: [&str; 7] = [
    "functions/defaulting",
    "functions/named-args",
    "regression/minimal-repro",
    "types/float/ops",
    "types/string/build",
    "types/string/indices",
    "types/string/read",
];

/// The functional tests of a library's own connected functions and
/// classes, with host code, that translate, as the backends' testkit
/// lists them.
const CONNECTED: [&str; 2] = ["functions/connected", "types/connected"];

/// The functional tests that divide by a constant that can't fail them,
/// where nothing may bubble, that translate, as the backends' testkit
/// lists them.
const DIVISIONS: [&str; 3] = ["types/int/shifty", "types/list/reduce", "types/map"];

/// The functional tests with jumps out of labeled loops, classes declared
/// in functions, type aliases, explicit type arguments, and `as` casts to
/// a supertype, that translate, as the backends' testkit lists them.
const LOOPS_AND_CLASSES: [&str; 4] = [
    "algos/myers-diff",
    "classes/object-literals",
    "control-flow/loops",
    "interfaces/property-members",
];

/// The functional tests that typing needed more of, that translate, as
/// the backends' testkit lists them.
const TYPING: [&str; 1] = ["semantics/constness"];

/// The functional tests with builtin values as `AnyValue`s and `List.of`,
/// that translate, as the backends' testkit lists them.
const ANY_VALUES: [&str; 2] = ["casts/as-expr", "types/list/operations"];

/// The functional tests with `is` and `as` checks from `AnyValue` among
/// core's values, that translate, as the backends' testkit lists them.
const CORE_CHECKS: [&str; 1] = ["casts/core-classes"];

/// The functional tests of a library with errors, each a tmpl `Error`
/// that fails when run, that translate, as the backends' testkit lists
/// them.
const ERRORS: [&str; 2] = ["semantics/broken", "semantics/type-checked-locals"];

/// The functional tests with types as values, that translate, as the
/// backends' testkit lists them.
const TYPE_VALUES: [&str; 1] = ["classes/angle-call"];

/// The functional tests of std's `net`, that translate, as the backends'
/// testkit lists them.
const NET: [&str; 2] = ["types/netresponse", "types/netresponse-not-found"];

/// The functional tests with generators, `async`, `await`, and `yield`,
/// that translate, as the backends' testkit lists them.
const ASYNC: [&str; 5] = [
    "control-flow/actor-run",
    "control-flow/async",
    "control-flow/async-broken-early",
    "control-flow/async-waiters",
    "control-flow/await-in-loop",
];

/// The functional tests that use std's classes' members and
/// constructors, declared in tmpl as another library's, that translate;
/// the backends run them once std translates as a library of its own.
const STD_MEMBERS: [&str; 9] = [
    "regex/match",
    "regex/zero-advance",
    "testing/asserts",
    "types/bit-vector",
    "types/bit-vector-negative-false",
    "types/date",
    "types/json-syntax-tree",
    "types/queue",
    "types/queue-empty",
];

/// The functional tests new in the 2026-11-09 sync with Kotlin's `main`
/// (from its #644, #645, and #651), which translate; the backends don't
/// list them yet (each logs what Kotlin expects on C++, checked by hand).
const SYNCED: [&str; 5] = [
    "casts/generic-is",
    "classes/getter-constness",
    "interfaces/generic-default",
    "regex/code-sets",
    "types/list/long-literal",
];

/// A functional test, settled as `check_functional.py` builds it: its
/// directory's own files, the main one first, and std's that it imports.
fn settled(test: &str) -> Settled {
    let test = temper_funtests::test(test).unwrap_or_else(|| panic!("no test {test}"));
    let mut library = Library::new();
    for source in test.sources() {
        let kind = if source.path.ends_with(".md") {
            SourceKind::Markdown
        } else {
            SourceKind::Standalone
        };
        library.add_file(&source.path, &source.text, kind);
    }
    // `testServerPort`, which std `net`'s tests read, at the testkit's
    // goldens' port; translating sends nothing.
    library.test_server_port = Some(28461);
    load_imports(&Sources::standard(), &mut library);
    settle(library)
}

/// A test's library, translated, under its directory's name.
fn translate(test: &str) -> Result<Program, NotYet> {
    let name = test.rsplit('/').next().unwrap();
    to_tmpl(&settled(test), name)
}

/// Runs a test on a thread with room for the settle loop's recursion.
fn with_stack(test: impl FnOnce() + Send + 'static) {
    std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(test)
        .unwrap()
        .join()
        .unwrap();
}

#[test]
fn subset_a_goldens() {
    with_stack(|| {
        let update = env::var_os("UPDATE_SNAPSHOTS").is_some();
        let goldens = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/to_tmpl");
        let mut differ = Vec::new();
        for test in SUBSET_A
            .iter()
            .chain(&CLASSES)
            .chain(&NULLABLES)
            .chain(&GENERICS)
            .chain(&NAMES)
            .chain(&FUNCTIONS)
            .chain(&DEFAULTS)
            .chain(&CORE_TYPES)
            .chain(&CONNECTED)
            .chain(&DIVISIONS)
            .chain(&LOOPS_AND_CLASSES)
            .chain(&TYPING)
            .chain(&ANY_VALUES)
            .chain(&CORE_CHECKS)
            .chain(&ERRORS)
            .chain(&TYPE_VALUES)
            .chain(&NET)
            .chain(&ASYNC)
            .chain(&STD_MEMBERS)
            .chain(&SYNCED)
        {
            let program = translate(test).unwrap_or_else(|e| panic!("{test}: {e}"));
            let problems = validate(&program, &Profile::full());
            assert!(problems.is_empty(), "{test}: {problems:#?}");
            assert!(program.has_spans, "{test} has no spans");

            // With spans: read back to an equal program, and the same text.
            let full = text::write(&program);
            let back = text::read(&full).unwrap_or_else(|e| panic!("{test}: {e}\n{full}"));
            assert_eq!(difference(&program, &back), None, "{test}");
            assert_eq!(text::write(&back), full, "{test}");

            let mut plain = program.clone();
            plain.has_spans = false;
            let mut outputs = vec![(String::new(), text::write(&plain))];
            // Generators as state machines, as Rust, C++, and Java write
            // them: valid, and as the golden `….state-machine.tmpl` has it.
            if ASYNC.contains(test) {
                let lowered = lower(&program, CoroutineStrategy::TranslateToRegularFunction)
                    .unwrap_or_else(|e| panic!("{test}: {e}"))
                    .unwrap_or_else(|| panic!("{test}: no generators"));
                let problems = validate(&lowered, &Profile::full());
                let shown = text::write(&lowered);
                assert!(problems.is_empty(), "{test}: {problems:#?}\n{shown}");
                let back = text::read(&shown).unwrap_or_else(|e| panic!("{test}: {e}\n{shown}"));
                assert_eq!(difference(&lowered, &back), None, "{test}");
                let mut plain = lowered;
                plain.has_spans = false;
                outputs.push((".state-machine".into(), text::write(&plain)));
            }
            for (suffix, canonical) in outputs {
                // Temper's version as the sentinel, so a release changes
                // only `std.tmpl` of these (`std_library`).
                let canonical = current_as_sentinel(&canonical);
                let name = format!("{}{suffix}.tmpl", test.replace('/', "-"));
                let path = goldens.join(name);
                let expected = fs::read_to_string(&path).unwrap_or_default();
                if canonical != expected {
                    if update {
                        fs::create_dir_all(&goldens).unwrap();
                        fs::write(&path, &canonical).unwrap();
                    } else {
                        differ.push(format!("{}:\n{canonical}", path.display()));
                    }
                }
            }
        }
        assert!(
            differ.is_empty(),
            "goldens differ (UPDATE_SNAPSHOTS=1 accepts new output):\n{}",
            differ.join("\n")
        );
    });
}

#[test]
fn every_functional_test_translates_or_says_why() {
    with_stack(|| {
        let tests: Vec<String> = temper_funtests::tests()
            .into_iter()
            .map(|t| t.path)
            .collect();
        assert!(tests.len() >= 69, "{} functional tests", tests.len());
        let mut ok = Vec::new();
        for test in &tests {
            match translate(test) {
                Ok(program) => {
                    let problems = validate(&program, &Profile::full());
                    assert!(problems.is_empty(), "{test}: {problems:#?}");
                    check_partition(test, &program);
                    ok.push(test.as_str());
                }
                Err(e) => assert!(!e.what.is_empty(), "{test}"),
            }
        }
        let mut covered: Vec<&str> = (SUBSET_A
            .iter()
            .chain(&CLASSES)
            .chain(&NULLABLES)
            .chain(&GENERICS)
            .chain(&NAMES)
            .chain(&FUNCTIONS)
            .chain(&DEFAULTS)
            .chain(&CORE_TYPES)
            .chain(&CONNECTED)
            .chain(&DIVISIONS)
            .chain(&LOOPS_AND_CLASSES)
            .chain(&TYPING)
            .chain(&ANY_VALUES)
            .chain(&CORE_CHECKS)
            .chain(&ERRORS)
            .chain(&TYPE_VALUES)
            .chain(&NET)
            .chain(&ASYNC)
            .chain(&STD_MEMBERS)
            .chain(&SYNCED))
        .copied()
        .collect();
        covered.sort();
        assert_eq!(ok, covered, "the functional tests that translate");
    });
}

/// std's modules that build as its own library
/// ([`temper_frontend::stdlib::program`]) translate, validate, round
/// trip, and match `tests/to_tmpl/std.tmpl`; their `@connected` members
/// keep their Temper bodies, for backends that don't connect them.
#[test]
fn std_library() {
    with_stack(|| {
        let program = temper_frontend::stdlib::program(&Sources::standard())
            .unwrap_or_else(|e| panic!("std: {e}"));
        let problems = validate(&program, &Profile::full());
        assert!(problems.is_empty(), "std: {problems:#?}");
        check_partition("std", &program);
        // As a backend that connects every std member it can.
        check_partition_for("std", &program, |_| true);
        let full = text::write(&program);
        let back = text::read(&full).unwrap_or_else(|e| panic!("std: {e}\n{full}"));
        assert_eq!(difference(&program, &back), None, "std");
        let mut plain = program.clone();
        plain.has_spans = false;
        let canonical = text::write(&plain);
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/to_tmpl/std.tmpl");
        if env::var_os("UPDATE_SNAPSHOTS").is_some() {
            fs::write(&path, &canonical).unwrap();
        } else {
            let expected = fs::read_to_string(&path).unwrap_or_default();
            assert!(
                canonical == expected,
                "{} differs (UPDATE_SNAPSHOTS=1 accepts new output):\n{canonical}",
                path.display()
            );
        }
    });
}

/// Each state machine's step reads its state, then sets it to `-1`
/// before anything else runs, so a step that fails stays failed: stepped
/// again, it reads `-1` and says it's done, rather than running again.
/// No Temper program can show it, since a generator's body can't bubble
/// (rule F4) and `panic()` ends the program on every backend; host code
/// stepping it again could.
#[test]
fn failed_steps_are_done() {
    with_stack(|| {
        for test in ASYNC {
            let program = translate(test).unwrap_or_else(|e| panic!("{test}: {e}"));
            let lowered = lower(&program, CoroutineStrategy::TranslateToRegularFunction)
                .unwrap_or_else(|e| panic!("{test}: {e}"))
                .unwrap_or_else(|| panic!("{test}: no generators"));
            let p = &lowered;
            let mut steps = 0;
            for m in &p.modules {
                for &root in m.items.iter().chain([&m.init]) {
                    temper_tmpl::analysis::walk(p, root, &mut |id| {
                        let Node::FunctionLiteral { decl, body, .. } = p.nodes.get(id) else {
                            return;
                        };
                        if !p.name(decl).starts_with("convertedCoroutine") {
                            return;
                        }
                        steps += 1;
                        // Its loop's body, or one state's, its own.
                        let Node::Block { stmts } = p.nodes.get(body) else {
                            panic!("{test}: a step's body isn't a block");
                        };
                        let stmts = match p.nodes.list(stmts).first().map(|&s| p.nodes.get(s)) {
                            Some(Node::While { body: each, .. }) => match p.nodes.get(each) {
                                Node::Block { stmts } => stmts,
                                _ => panic!("{test}: a step's loop body isn't a block"),
                            },
                            _ => stmts,
                        };
                        let first: Vec<Node> = p
                            .nodes
                            .list(stmts)
                            .iter()
                            .take(2)
                            .map(|&s| p.nodes.get(s))
                            .collect();
                        let [
                            Node::Let {
                                init: Some(read), ..
                            },
                            Node::Assign { local, value },
                        ] = first[..]
                        else {
                            panic!(
                                "{test}: a step doesn't start by reading its state, then setting it"
                            );
                        };
                        assert_eq!(p.nodes.get(read), Node::Local { local }, "{test}");
                        assert_eq!(p.nodes.get(value), Node::Int32Lit { value: -1 }, "{test}");
                    });
                }
            }
            assert!(steps > 0, "{test}: no steps");
        }
    });
}

/// A closure in a generator's loop that captures a loop `let` assigned
/// after it's set, which a state machine's one copy per closure can't
/// share with the pass: lowering fails, saying so, rather than giving
/// each closure a stale copy.
#[test]
fn reassigned_loop_captures_fail_clearly() {
    with_stack(|| {
        let source = "
    @fun interface Counter(): Int;
    let drive(body: fn (): SafeGenerator<Empty>): Void { body().nextSafe(); }
    drive { (): GeneratorResult<Empty> extends GeneratorFn =>
      let fs = new ListBuilder<Counter>();
      for (var i = 0; i < 2; i += 1) {
        var j = i;
        fs.add(fn (): Int { j });
        j += 10;
        yield();
      }
    }
";
        let mut library = Library::new();
        library.add_file("reassigned.temper.md", source, SourceKind::Markdown);
        load_imports(&Sources::standard(), &mut library);
        let program = to_tmpl(&settle(library), "reassigned").unwrap();
        assert!(validate(&program, &Profile::full()).is_empty());
        let error = lower(&program, CoroutineStrategy::TranslateToRegularFunction).unwrap_err();
        assert_eq!(
            error,
            "a closure in a generator's loop capturing j, which is assigned after its `let`"
        );
    });
}

#[test]
fn constant_divisors_fold_as_typing_does() {
    with_stack(|| {
        // As Hrefine's `s / (jamoVCount * jamoTCount)`: module `let`s and
        // arithmetic on them make a divisor that can't fail, which typing
        // lets a function that can't bubble use, so it's `divSafe`. One
        // that folds to 0 may fail, which typing reports.
        let source = "
    let jamoVCount = 21;
    let jamoTCount = 28;
    let none = jamoTCount - 28;
    export let split(s: Int): Int {
      s / (jamoVCount * jamoTCount) + s % (jamoTCount - 1) + s / -(1 + 1)
    }
    export let broken(s: Int): Int { s / none }
";
        let mut library = Library::new();
        library.add_file("divisors.temper.md", source, SourceKind::Markdown);
        load_imports(&Sources::standard(), &mut library);
        let settled = settle(library);
        let errors: Vec<String> = (settled.library.diagnostics.iter())
            .map(|d| d.message.text())
            .collect();
        assert_eq!(
            errors,
            ["Cannot bubble from a function without Bubble in its return type"]
        );
    });
    with_stack(|| {
        let source = "
    let jamoVCount = 21;
    let jamoTCount = 28;
    export let split(s: Int): Int {
      s / (jamoVCount * jamoTCount) + s % (jamoTCount - 1) + s / -(1 + 1)
    }
";
        let mut library = Library::new();
        library.add_file("divisors.temper.md", source, SourceKind::Markdown);
        load_imports(&Sources::standard(), &mut library);
        let program = to_tmpl(&settle(library), "divisors").unwrap();
        let written = text::write(&program);
        assert_eq!(written.matches("%Int32.divSafe(").count(), 2, "{written}");
        assert_eq!(written.matches("%Int32.modSafe(").count(), 1, "{written}");
        assert!(!written.contains("%Int32.div("), "{written}");
    });
}

/// `gaps` finds every gap, not only the first, going on past each
/// statement it can't translate, and none where `to_tmpl` succeeds.
#[test]
fn gaps_lists_every_gap() {
    with_stack(|| {
        // Two locals whose types typing leaves unknown, in two functions,
        // and a function between them that translates.
        let source = "
    export let a(): Void { let x = [].get(0) orelse panic(); }
    export let b(): Int { 1 }
    export let c(): Void { let y = [].get(0) orelse panic(); }
";
        let mut library = Library::new();
        library.add_file("gaps.temper.md", source, SourceKind::Markdown);
        load_imports(&Sources::standard(), &mut library);
        let settled = settle(library);
        let first = to_tmpl(&settled, "gaps").unwrap_err();
        let found = gaps(&settled, "gaps");
        assert!(found.len() >= 2, "{found:?}");
        assert_eq!(found[0], first);
        assert!(
            found
                .iter()
                .any(|g| g.at.as_deref().is_some_and(|at| at.contains(":4:"))),
            "{found:?}"
        );
        let mut library = Library::new();
        library.add_file(
            "fine.temper.md",
            "    export let b(): Int { 1 }\n",
            SourceKind::Markdown,
        );
        load_imports(&Sources::standard(), &mut library);
        assert!(gaps(&settle(library), "fine").is_empty());
    });
}

/// A class whose members can't all be declared, as one with a user's
/// `@connected` member, is a gap once: `gaps` leaves the class out and
/// goes on, rather than translating a class it only half declared.
#[test]
fn gaps_go_past_a_class_not_declared() {
    with_stack(|| {
        let source = "
    export class C {
      public x: Int;
      public constructor(x: Int) { this.x = x; }
      @connected public f(): Int;
    }
    export let a(): Int { new C(1).x }
    export let c(): Void { let y = [].get(0) orelse panic(); }
";
        let mut library = Library::new();
        library.add_file("half.temper.md", source, SourceKind::Markdown);
        load_imports(&Sources::standard(), &mut library);
        let settled = settle(library);
        let first = to_tmpl(&settled, "half").unwrap_err();
        assert_eq!(first.what, "a connected member", "{first}");
        let found = gaps(&settled, "half");
        assert_eq!(found[0], first);
        let lines: Vec<&str> = (found.iter()).filter_map(|g| g.at.as_deref()).collect();
        assert!(lines.iter().any(|at| at.contains(":8:")), "{found:?}");
    });
}

/// Translates one source file, with std, into tmpl, validated.
fn translate_source(name: &str, source: &str) -> Program {
    let (program, errors) = translate_with_errors(name, source);
    assert!(errors.is_empty(), "{errors:?}");
    program
}

/// Translates one source file, with std, into tmpl, validated, and the
/// errors reported.
fn translate_with_errors(name: &str, source: &str) -> (Program, Vec<String>) {
    let mut library = Library::new();
    library.add_file(&format!("{name}.temper.md"), source, SourceKind::Markdown);
    load_imports(&Sources::standard(), &mut library);
    let settled = settle(library);
    let errors: Vec<String> = (settled.library.diagnostics.iter())
        .filter(|d| d.level == temper_frontend::library::Level::Error)
        .map(|d| d.message.text())
        .collect();
    let program = to_tmpl(&settled, name).unwrap_or_else(|e| panic!("{e}"));
    let problems = validate(&program, &Profile::full());
    assert!(problems.is_empty(), "{problems:#?}");
    (program, errors)
}

/// What Kelpto needed of typing, as Kotlin Temper types it: a `when`
/// whose `is` checks cover a sealed interface's subtypes needs no `else`,
/// its value the join of its branches' and its missing `else` a panic; two
/// classes join to the interface both extend; a `[]` branch takes the
/// other branch's type; and `?.` on a bare member's name, `this`'s getter,
/// holds it in a temporary, so the call after it resolves.
#[test]
fn joins_and_sealed_whens() {
    with_stack(|| {
        let source = "
    export sealed interface E { public get n(): Int; }
    export class A(public n: Int) extends E {
      public get next(): A? { if (n > 0) { new A(n - 1) } else { null } }
      public get twice(): Int? { next?.times(2) }
      public times(k: Int): Int { n * k }
    }
    export class B(public n: Int) extends E {}
    export let name(e: E): String {
      let s = when (e) {
        is A -> \"a\";
        is B -> \"b\";
      };
      s
    }
    export let make(i: Int): E {
      let x = if (i > 0) { new A(i) } else { new B(i) };
      x
    }
    export let parts(e: E): List<Int> {
      let xs = when (e) {
        is A -> [e.n];
        else -> [];
      };
      xs
    }
";
        let program = translate_source("joins", source);
        let written = text::write(&program);
        // The `when`'s missing `else` panics; `s` is a `String`, `xs` a list.
        assert!(written.contains("panic"), "{written}");
        assert!(written.contains("let s#25: String"), "{written}");
        assert!(written.contains("let xs#29: List#16<Int32>"), "{written}");
        // `x` is an `E`.
        assert!(written.contains(": E#"), "{written}");
        // `next` held first, then called.
        assert!(written.contains("let subject#"), "{written}");
    });
}

/// A module imported as a namespace, `let s = import("./shapes")`,
/// translates exactly as it does imported by destructuring: each
/// `s.member` is the export itself, and the namespace reaches no tmpl.
/// So too std's modules, and in types, constructors, and statics.
#[test]
fn namespaces_translate_as_destructured_imports() {
    with_stack(|| {
        let shapes = "export let unit = 1;
export let twice(x: Int): Int { x * 2 }
export class Box<T>(public value: T) {}
export interface Shape { public area(): Int; }
export class Square(public side: Int) extends Shape {
  public area(): Int { side * side }
  public static sized(side: Int): Square { new Square(side) }
}
";
        let uses = |prefix: &str, ns: &str, rx: &str, t: &str| {
            format!(
                "{prefix}
export let f(n: Int): {ns}Box<Int> {{ new ({ns}Box<Int>)({ns}twice(n) + {ns}unit) }}
export let area(x: {ns}Shape): Int {{ x.area() }}
export let sq: {ns}Square = {ns}Square.sized(2);
export class Circle(public r: Int) extends {ns}Shape {{ public area(): Int {{ r }} }}
export let isSquare(x: {ns}Shape): Boolean {{ x is {ns}Square }}
export let r: {rx}Regex = new ({rx}Regex)({rx}oneOrMore(new ({rx}CodePoints)(\"ab\")));
export let day(): {t}Date throws Bubble {{ new ({t}Date)(2024, 2, 29) }}
"
            )
        };
        let namespaced = uses(
            "let s = import(\"./shapes\");
let rx = import(\"std/regex\");
let t = import(\"std/temporal\");",
            "s.",
            "rx.",
            "t.",
        );
        let destructured = uses(
            "let { unit, twice, Box, Shape, Square } = import(\"./shapes\");
let { Regex, oneOrMore, CodePoints } = import(\"std/regex\");
let { Date } = import(\"std/temporal\");",
            "",
            "",
            "",
        );
        let tmpl = |main: &str| {
            let mut library = Library::new();
            library.add_file("lib/main.temper", main, SourceKind::Standalone);
            library.add_file("lib/shapes/shapes.temper", shapes, SourceKind::Standalone);
            load_imports(&Sources::standard(), &mut library);
            let settled = settle(library);
            let errors: Vec<String> = (settled.library.diagnostics.iter())
                .filter(|d| d.level == temper_frontend::library::Level::Error)
                .map(|d| d.message.text())
                .collect();
            assert!(errors.is_empty(), "{errors:?}\n{main}");
            let program = to_tmpl(&settled, "lib").unwrap_or_else(|e| panic!("{e}"));
            let problems = validate(&program, &Profile::full());
            assert!(problems.is_empty(), "{problems:#?}");
            let written = text::write(&program);
            match written.find("\nspans {") {
                Some(at) => written[..at].to_string(),
                None => written,
            }
        };
        let (a, b) = (tmpl(&namespaced), tmpl(&destructured));
        assert!(a == b, "namespaced:\n{a}\ndestructured:\n{b}");
    });
}

/// A module whose only `console.log` is in a test declares its
/// `~console` too, which the test reads.
#[test]
fn console_first_used_in_a_test() {
    with_stack(|| {
        let source = "
    let report(): String { \"r\" }
    test(\"c\") { console.log(report()); }
";
        let program = translate_source("logged", source);
        let written = text::write(&program);
        assert!(written.contains("let ~console#"), "{written}");
        assert!(written.contains("getConsole"), "{written}");
    });
}

/// `@keep` and `@keepTest` mark every kind of top-level declaration,
/// classes and interfaces included, as Kotlin's `ReachabilityTracer`
/// reads both on any declaration: `KEEP` (kept for host code) and
/// `KEEP_TEST` (kept for tests' host code), each alone or both.
#[test]
fn keep_marks_every_top_level_kind() {
    with_stack(|| {
        let source = "
    @keep let forHost(): Int { 1 }
    @keepTest let forTests(): Int { 2 }
    @keep @keepTest let forBoth(): Int { 3 }
    @keep let hostValue = 4;
    @keepTest var testCount = 5;
    @keep class HostClass {}
    @keepTest class TestClass {}
    @keep interface HostInterface {}
    @keepTest interface TestInterface {}
    let neither(): Int { 6 }
    test(\"t\") { assert(forTests() + testCount == 7); }
";
        let program = translate_source("kept", source);
        let module = program.modules[0].decl;
        let facts = |name: &str| {
            let found = (program.decls.ids())
                .filter(|&d| program.decls.contains(d))
                .find(|&d| program.decls.get(d).owner == module && program.name(d) == name)
                .unwrap_or_else(|| panic!("no {name}"));
            let facts = program.decls.get(found).facts;
            (
                facts.contains(Facts::KEEP),
                facts.contains(Facts::KEEP_TEST),
            )
        };
        assert_eq!(facts("forHost"), (true, false));
        assert_eq!(facts("forTests"), (false, true));
        assert_eq!(facts("forBoth"), (true, true));
        assert_eq!(facts("hostValue"), (true, false));
        assert_eq!(facts("testCount"), (false, true));
        assert_eq!(facts("HostClass"), (true, false));
        assert_eq!(facts("TestClass"), (false, true));
        assert_eq!(facts("HostInterface"), (true, false));
        assert_eq!(facts("TestInterface"), (false, true));
        assert_eq!(facts("neither"), (false, false));
        // And text writes them.
        let written = text::write(&program);
        assert!(written.contains("keep keepTest fn forBoth#"), "{written}");
        assert!(written.contains("keepTest class TestClass#"), "{written}");
    });
}

/// Each of a program's own top-level declarations, by name, with its
/// reach, sorted by name.
fn reaches(program: &Program) -> Vec<(String, Reach)> {
    let reached = reach(program);
    let mut found: Vec<(String, Reach)> = (program.decls.ids())
        .filter_map(|d| Some((program.name(d).to_string(), reached.get(d)?)))
        .collect();
    found.sort_by(|a, b| a.0.cmp(&b.0));
    found
}

/// The names with a reach.
fn reached_as(found: &[(String, Reach)], reach: Reach) -> Vec<&str> {
    (found.iter())
        .filter(|(_, r)| *r == reach)
        .map(|(n, _)| n.as_str())
        .collect()
}

/// Kotlin Temper's `generate-code/export-some` stage test, its source as
/// it is but for its test, `test("testCase") { … }` where Kotlin's is
/// `@test("testCase") let testCase(): Void { … }`, a form tempest doesn't
/// read yet: what's reached as Kotlin's `ReachabilityTracer` marks it, the
/// library's unmarked, `@reach(\test)` the tests', and `@reach(\none)`
/// nothing's. Kotlin's `console` is tempest's `~console`, named `console`.
#[test]
fn reach_as_kotlins_export_some() {
    with_stack(|| {
        let source = "
    // Various reachability things.

    export let exportedInt = 1;
    let unreachableInt = 2;

    export let exportedFunction(b: Boolean): Void { if (b) { conditionallyExportReachable() } }

    let conditionallyExportReachable(): Void { console.log(\"\") }
    let transitivelyTestReachable(): Void { console.log(\"\") }
    let exportAndTestReachable(): Void { console.log(\"\") }
    let initReachable(): Void { transitivelyInitReachable(); console.log(\"\") }
    let transitivelyInitReachable(): Void { console.log(\"\") }
    let unreachableFunction(): Void { console.log(\"\") }

    export class ExportedClass(
      private let propertyOfExportedClass: UsedOnlyAsPropertyType
    ) {
      private let methodOfExportedClass(): Void { exportAndTestReachable() }
    }

    class TestReachableClass {
      private let methodOfTestReachable(): Void {
        transitivelyTestReachable();
        exportAndTestReachable();
      }
    }

    test(\"testCase\") {
      new TestReachableClass();
      testKeptAndTestCalled();
    }

    class UsedOnlyAsPropertyType {}

    initReachable();

    // Now also try out decorations for keeping.

    let keepReachable(): Void { console.log(\"\"); }
    let testKeepReachable(): Void { console.log(\"\"); }

    @keep
    let kept(): Void { keepReachable(); }

    @keep
    class KeptClass {
      public keptClassMethod(): Void {}
    }

    @keepTest
    let testKept(): Void { testKeepReachable(); }

    // Double-checking this one doesn't get marked test-reachable twice.
    @keepTest
    let testKeptAndTestCalled(): Void { console.log(\"\"); }
";
        // Kotlin's error too.
        let (program, errors) = translate_with_errors("test", source);
        assert_eq!(
            errors,
            ["Export depends publicly on non-exported symbol UsedOnlyAsPropertyType"]
        );
        let found = reaches(&program);
        assert_eq!(
            reached_as(&found, Reach::Library),
            [
                "ExportedClass",
                "KeptClass",
                "UsedOnlyAsPropertyType",
                "conditionallyExportReachable",
                "console",
                "exportAndTestReachable",
                "exportedFunction",
                "exportedInt",
                "initReachable",
                "keepReachable",
                "kept",
                "transitivelyInitReachable",
            ]
        );
        assert_eq!(
            reached_as(&found, Reach::Test),
            [
                "TestReachableClass",
                "testKeepReachable",
                "testKept",
                "testKeptAndTestCalled",
                "transitivelyTestReachable",
            ]
        );
        assert_eq!(
            reached_as(&found, Reach::Unreached),
            ["unreachableFunction", "unreachableInt"]
        );
    });
}

/// Kotlin Temper's `generate-code/init-assignment-reachability` stage
/// test, its source as it is: a `var` assigned again in the init block is
/// kept, as Kotlin doesn't drop the assignments, and a `let` nothing reads
/// isn't.
#[test]
fn reach_as_kotlins_init_assignment_reachability() {
    with_stack(|| {
        let source = "
    // We don't eliminate var reassignments, so keep associated declarations.
    var hi = 0;
    hi = 1;
    // Non-var for contrast.
    let ha = 2;
";
        let program = translate_source("test", source);
        let found = reaches(&program);
        assert_eq!(reached_as(&found, Reach::Library), ["hi"]);
        assert_eq!(reached_as(&found, Reach::Unreached), ["ha"]);
    });
}

/// The program partitioned by reach ([`temper_tmpl::reach::partition`])
/// keeps every item and init statement in the part its split says, or
/// drops it where nothing reaches it; validates, so each part uses only
/// what it may (the library's parts nothing of the tests', rule M3, and
/// nothing what's dropped); round trips as text, with its spans; and
/// partitioned again, stays as it is.
fn check_partition(test: &str, p: &Program) {
    check_partition_for(test, p, |_| false);
}

/// [`check_partition`] for a backend that connects the std members
/// `connects` takes, by their keys.
fn check_partition_for(test: &str, p: &Program, connects: fn(&str) -> bool) {
    let parted = partition(p, connects);
    let reached = reach_for(p, connects);
    for (module, parted_module) in p.modules.iter().zip(&parted.modules) {
        let split = reached.split(p, module);
        let stmts = |block: Option<NodeId>| match block.map(|b| p.nodes.get(b)) {
            Some(Node::Block { stmts }) => p.nodes.list(stmts).len(),
            _ => 0,
        };
        let parted_stmts = |block: Option<NodeId>| match block.map(|b| parted.nodes.get(b)) {
            Some(Node::Block { stmts }) => parted.nodes.list(stmts).len(),
            _ => 0,
        };
        assert_eq!(parted_module.items.len(), split.items.len(), "{test}");
        assert_eq!(
            parted_stmts(Some(parted_module.init)),
            split.init.len(),
            "{test}"
        );
        assert_eq!(
            parted_module.test_items.len(),
            split.test_items.len(),
            "{test}"
        );
        assert_eq!(
            parted_stmts(parted_module.test_init),
            split.test_init.len(),
            "{test}"
        );
        let placed = [
            &split.items,
            &split.init,
            &split.test_items,
            &split.test_init,
        ]
        .iter()
        .map(|part| part.len())
        .sum::<usize>()
            + split.unreached.len();
        assert_eq!(
            placed,
            module.items.len() + stmts(Some(module.init)),
            "{test}"
        );
    }
    let problems = validate(&parted, &Profile::full());
    assert!(problems.is_empty(), "{test}: {problems:#?}");
    let full = text::write(&parted);
    let back = text::read(&full).unwrap_or_else(|e| panic!("{test}: {e}\n{full}"));
    assert_eq!(difference(&parted, &back), None, "{test}");
    assert_eq!(text::write(&back), full, "{test}");
    let again = partition(&parted, connects);
    assert_eq!(difference(&parted, &again), None, "{test}");
}
