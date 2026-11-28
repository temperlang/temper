//! The shared programs (`programs/`), checked against tempest's
//! interpreter.
//!
//! - `programs_log_as_the_interpreter_does`: each Temper program logs, on
//!   the interpreter, what its log block says, so every backend that runs
//!   it is checked against the interpreter.
//! - `programs_translate_to_tmpl`: each Temper program translates to
//!   valid tmpl, as its golden shows.
//! - `tests_on_the_interpreter`: the programs that run as tests, and the
//!   functional tests that do, fail on the interpreter just the tests
//!   they expect to, saying why, and the others pass.
//! - `net_tests_on_the_interpreter`: std `net`'s functional tests
//!   (`NET`) log what they expect on the interpreter, against the shared
//!   test server.
//! - `specs_are_pending`: the specifications (`SPECS`), written ahead
//!   of what they specify, each say what they should log and still have
//!   errors, or settle but don't run yet on the interpreter ("Not
//!   supported yet"), so the change that makes one run moves it to
//!   `INTERP_SPECS`; and those don't translate to tmpl yet, so the
//!   change that makes one translate moves it to `TMPL_SPECS`, and the
//!   one that makes backends run it, to `PROGRAMS`.
//! - `specs_run_on_the_interpreter`: the specifications the interpreter
//!   runs (`INTERP_SPECS`) log what they should there, as programs do.
//! - `specs_translate_to_tmpl`: the specifications tmpl has
//!   (`TMPL_SPECS`) translate to valid tmpl, as their goldens show, a
//!   golden per run, as programs' are.

use temper_be_testkit::{self as kit};

#[test]
fn programs_log_as_the_interpreter_does() {
    kit::with_stack(|| logs_as_expected(kit::cases("rust", &kit::PROGRAMS)));
}

/// Each case of a Temper program logs, on the interpreter, what its log
/// block says, and a run that should fail does, saying what it should.
fn logs_as_expected(cases: Vec<kit::Case>) {
    for case in cases {
        let [(path, text)] = &case.sources[..] else {
            continue;
        };
        if !path.ends_with(".temper.md") || path.contains("temper-funtests/cases/") {
            continue;
        }
        let (failure, output) = kit::interpret(&case);
        // A run that fails (`panics`) panics, or ends at an error nothing
        // handles, saying what its `stderr` blocks for the
        // interpreter say, else that it panicked.
        let mut says = temper_funtests::fenced_lines(text, "stderr", &["interp"]);
        if says.is_empty() {
            says.push("Panicked".into());
        }
        // Nor what its `stderr-absent` blocks say.
        let unsaid = temper_funtests::fenced_lines(text, "stderr-absent", &["interp"]);
        match &failure {
            Some(why) => assert!(
                case.fails
                    && says.iter().all(|s| why.contains(s.trim()))
                    && !unsaid.iter().any(|s| why.contains(s.trim())),
                "{}: {why}",
                case.key
            ),
            None => assert!(!case.fails, "{}: didn't fail", case.key),
        }
        let expected = temper_funtests::expected_log(text, "interp");
        assert_eq!(output, expected, "{}", case.key);
    }
}

#[test]
fn tests_on_the_interpreter() {
    kit::with_stack(|| {
        let mut failures = Vec::new();
        for case in kit::cases_with("interp", &kit::AS_TESTS, &kit::TESTS) {
            if case.tests.is_none() {
                continue;
            }
            let (failure, tests) = kit::interpret_tests(&case);
            assert_eq!(failure, None, "{}", case.key);
            let out = std::process::Output {
                status: Default::default(),
                stdout: Vec::new(),
                stderr: Vec::new(),
            };
            kit::check_tests(&mut failures, &case, Some(tests), &out, str::to_string);
        }
        assert!(failures.is_empty(), "{}", failures.join("\n"));
    });
}

#[test]
fn net_tests_on_the_interpreter() {
    kit::with_stack(|| {
        for case in kit::functional_cases("interp", &kit::NET) {
            if !kit::NET.iter().any(|t| t.replace('/', "-") == case.key) {
                continue;
            }
            let (failure, output) = kit::interpret(&case);
            assert_eq!((failure, output), (None, case.expected), "{}", case.key);
        }
    });
}

/// Each Temper program translates to valid tmpl, which matches its golden
/// (`tests/goldens/NAME.tmpl`, without spans), so what backends start
/// from shows in review.
#[test]
fn programs_translate_to_tmpl() {
    kit::with_stack(|| {
        let mut goldens = kit::Goldens::new(env!("CARGO_MANIFEST_DIR"));
        for case in kit::cases("rust", &kit::PROGRAMS) {
            let path = &case.sources[0].0;
            if path.ends_with(".tmpl") || path.contains("temper-funtests/cases/") {
                continue;
            }
            let mut program = kit::program(&case);
            program.has_spans = false;
            goldens.check(
                &format!("{}.tmpl", case.key),
                &temper_tmpl::text::write(&program),
            );
        }
        goldens.finish();
    });
}

#[test]
fn specs_are_pending() {
    kit::with_stack(|| {
        for name in kit::SPECS {
            assert!(
                !kit::PROGRAMS.contains(&name),
                "{name} runs, so isn't pending"
            );
            let (_, text) = kit::spec_source(name);
            let log = temper_funtests::expected_log(&text, "interp");
            assert!(!log.is_empty(), "{name}: no log");
            let errors = kit::spec_errors(name);
            if kit::TMPL_SPECS.contains(&name) {
                // Translated (`specs_translate_to_tmpl`), and pending
                // while backends don't run them.
                assert!(kit::INTERP_SPECS.contains(&name), "{name}");
                assert_eq!(errors, Vec::<String>::new(), "{name}");
                continue;
            }
            if kit::INTERP_SPECS.contains(&name) {
                // Run on the interpreter (`specs_run_on_the_interpreter`),
                // but pending while tmpl doesn't translate them.
                assert_eq!(errors, Vec::<String>::new(), "{name}");
                let translated = std::panic::catch_unwind(|| kit::program(&kit::spec_case(name)));
                assert!(
                    translated.is_err(),
                    "{name} translates: move it to PROGRAMS, which backends run"
                );
                continue;
            }
            // Pending while the frontend reports errors, or once typed,
            // while the interpreter doesn't run it yet.
            if errors.is_empty() {
                let (failure, _) = kit::interpret(&kit::spec_case(name));
                assert!(
                    failure.is_some_and(|f| f.contains("Not supported yet")),
                    "{name} runs: move it to INTERP_SPECS, which checks its log"
                );
            }
        }
    });
}

/// The specifications tmpl has translate to valid tmpl, which matches
/// its golden (`tests/goldens/NAME.tmpl`, or a run's, without spans), as
/// programs do ([`programs_translate_to_tmpl`]).
#[test]
fn specs_translate_to_tmpl() {
    kit::with_stack(|| {
        let mut goldens = kit::Goldens::new(env!("CARGO_MANIFEST_DIR"));
        for name in kit::TMPL_SPECS {
            for case in kit::spec_cases("rust", name) {
                let mut program = kit::program(&case);
                program.has_spans = false;
                goldens.check(
                    &format!("{}.tmpl", case.key),
                    &temper_tmpl::text::write(&program),
                );
            }
        }
        goldens.finish();
    });
}

/// The specifications the interpreter runs already log what they
/// should there, as shared programs do.
#[test]
fn specs_run_on_the_interpreter() {
    kit::with_stack(|| {
        for name in kit::INTERP_SPECS {
            logs_as_expected(kit::spec_cases("rust", name));
        }
    });
}
