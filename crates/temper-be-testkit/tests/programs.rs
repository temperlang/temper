//! The shared programs (`programs/`), checked against tempest's
//! interpreter.
//!
//! - `programs_log_as_the_interpreter_does`: each Temper program logs, on
//!   the interpreter, what its log block says, so every backend that runs
//!   it is checked against the interpreter; one expected to fail
//!   (`expectRunFailure = true`) must end in a panic.
//! - `programs_translate_to_tmpl`: each Temper program translates to
//!   valid tmpl, as its golden shows.
//! - `panics_on_the_interpreter`: each functional test whose run must
//!   panic (its ` ```panic ` block) panics so on the interpreter.
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
            // A run expected to fail (`callback-changes`) ends in its
            // panic.
            if case.fails {
                let failure = failure.unwrap_or_else(|| panic!("{}: no failure", case.key));
                let message = case.panic.as_deref().unwrap_or_default();
                let panicked = failure.starts_with("Panicked") && failure.contains(message);
                assert!(panicked, "{}: {failure}", case.key);
            } else {
                assert_eq!(failure, None, "{}", case.key);
            }
            let expected = temper_funtests::expected_log(text, "interp");
            assert_eq!(output, expected, "{}", case.key);
        }
    });
}

/// Each functional test whose run must end in a panic (its ` ```panic `
/// block), as list and string indices out of bounds, panics on the
/// interpreter too, with that message, after logging what's expected.
#[test]
fn panics_on_the_interpreter() {
    kit::with_stack(|| {
        let tests = [&kit::CORE_TYPES[..], &kit::WITH_STD[..]].concat();
        let mut panics = 0;
        for case in kit::functional_cases("interp", &tests) {
            let Some(message) = &case.panic else {
                continue;
            };
            panics += 1;
            let (failure, output) = kit::interpret(&case);
            let failure = failure.unwrap_or_else(|| panic!("{}: no failure", case.key));
            let panicked = failure.starts_with("Panicked: ") && failure.contains(message);
            assert!(panicked, "{}: {failure}", case.key);
            assert_eq!(output, case.expected, "{}", case.key);
        }
        assert!(panics >= 8, "{panics} cases must panic");
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
