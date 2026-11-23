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

use temper_be_testkit::{self as kit};

#[test]
fn programs_log_as_the_interpreter_does() {
    kit::with_stack(|| {
        for case in kit::cases("rust", &kit::PROGRAMS) {
            let [(path, text)] = &case.sources[..] else {
                continue;
            };
            if !path.ends_with(".temper.md") || path.contains("temper-funtests/cases/") {
                continue;
            }
            let (failure, output) = kit::interpret(&case);
            // A run that fails (`panics`) panics, or bubbles, as nothing
            // catches it, saying what its `stderr` blocks for the
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
    });
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
