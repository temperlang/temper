//! The REPL's transcripts, `tests/repl/*.cases` in temper-frontend (the
//! REPL alone) and in temper-driver (with its functions' tools), through
//! the wasm's REPL API as the playground uses it, so the browser and
//! `temper repl` can't drift: the lines go into an input cell one at a
//! time, as typed, and each time `repl_complete` says the cell is whole,
//! its inputs go to `repl_input` in turn; at the end, what's left runs,
//! as Ctrl+Enter runs it. The transcript is each event's text, in
//! order, which must be the CLI's, exactly. The cases are only read
//! here, never updated (`UPDATE_SNAPSHOTS` is for their own crates'
//! tests).

#[path = "../../temper-syntax/tests/common/mod.rs"]
mod common;

use common::case_files;
use serde_json::Value;
use temper_frontend::repl::{Chunker, ReplOptions, Separator};
use temper_frontend::sources::Sources;
use temper_wasm::repl::{complete, input, start_with, streamed};

/// As the REPL's own tests, so a runaway case ends.
const FUEL: u64 = 10_000_000;

/// What a session started with `options` prints for `text`, typed a
/// line at a time into the page's input cell.
fn transcript(options: ReplOptions, text: &str) -> String {
    assert_eq!(start_with(options, ""), "null");
    let mut out = String::new();
    let run = |text: &str, out: &mut String| {
        let answer: Value = serde_json::from_str(&input(text, Some(FUEL))).unwrap();
        let mut logs = Vec::new();
        for event in answer["events"].as_array().unwrap() {
            out.push_str(event["text"].as_str().unwrap());
            out.push('\n');
            if event["kind"] == "log" {
                logs.push(event["text"].as_str().unwrap().to_string());
            }
        }
        // Each logged line also went to the page as it was logged.
        assert_eq!(streamed(), logs);
    };
    let mut cell: Vec<&str> = Vec::new();
    for line in text.split('\n') {
        cell.push(line);
        let (json, whole) = complete(&cell.join("\n"));
        if whole {
            let answer: Value = serde_json::from_str(&json).unwrap();
            for input in answer["inputs"].as_array().unwrap() {
                run(input.as_str().unwrap(), &mut out);
            }
            cell.clear();
        }
    }
    let mut rest = Chunker::new(Separator::Eof);
    rest.push_line(&cell.join("\n"));
    if let Some(input) = rest.finish() {
        run(&input, &mut out);
    }
    out
}

/// Checks each case in `dir`, relative to this crate, against its
/// transcript from a session with `options`.
fn check(dir: &str, options: impl Fn() -> ReplOptions) {
    let mut failures = Vec::new();
    let mut count = 0;
    for (path, cases) in case_files(dir, "transcript") {
        for case in cases {
            count += 1;
            let actual = transcript(options(), &case.input);
            if actual != case.expected {
                failures.push(format!(
                    "{} case {:?}:\n--- expected\n{}--- actual\n{actual}",
                    path.display(),
                    case.name,
                    case.expected
                ));
            }
        }
    }
    assert!(count > 0, "no cases in {dir}");
    assert!(
        failures.is_empty(),
        "{} of {count} cases differ:\n{}",
        failures.len(),
        failures.join("\n")
    );
}

/// On a thread with room for the settle loop's and the interpreter's
/// recursion, as natively the page's 16 MiB stack isn't there.
fn with_stack(f: impl FnOnce() + Send + 'static) {
    std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(f)
        .unwrap()
        .join()
        .unwrap();
}

#[test]
fn frontend_cases_run_as_in_the_cli() {
    with_stack(|| {
        check("../temper-frontend/tests/repl", || ReplOptions {
            sources: Sources::memory(&[]),
            ..ReplOptions::default()
        });
    });
}

/// With the tools, which translate into every language.
#[cfg(all(
    feature = "cpp",
    feature = "csharp",
    feature = "java",
    feature = "js",
    feature = "lua",
    feature = "py",
    feature = "rust"
))]
#[test]
fn driver_cases_run_as_in_the_cli() {
    use std::rc::Rc;
    use temper_driver::repl::ReplTools;
    with_stack(|| {
        check("../temper-driver/tests/repl", || ReplOptions {
            sources: Sources::memory(&[]),
            tools: Some(Rc::new(ReplTools::default())),
            help_footer: "(The front end's keys and options.)".into(),
            ..ReplOptions::default()
        });
    });
}
