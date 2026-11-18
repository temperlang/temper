//! REPL sessions over the transcripts in `tests/repl/*.cases`.
//!
//! The cases have the format described in temper-syntax's
//! `tests/common/mod.rs`, with a `--- transcript` line before the
//! expected output. A case's input is what's typed, or piped, into
//! `temper repl`, a line at a time: [`Chunker`] splits it into inputs,
//! each fed to one [`ReplSession`], and the output is each event's text,
//! as `temper repl` prints it. To accept new or changed output, run
//! with `UPDATE_SNAPSHOTS=1` and review the diff.

#[path = "../../temper-syntax/tests/common/mod.rs"]
mod common;

use common::check_cases;
use std::cell::RefCell;
use std::rc::Rc;
use temper_frontend::repl::{Chunker, ReplEvent, ReplOptions, ReplSession, Separator};

/// What a session prints for `text`, typed a line at a time.
fn transcript(text: &str) -> String {
    let out = Rc::new(RefCell::new(String::new()));
    let sink = out.clone();
    let options = ReplOptions {
        // So a runaway case ends.
        fuel: Some(10_000_000),
        ..ReplOptions::default()
    };
    let mut session = ReplSession::new(
        options,
        Box::new(move |e: &ReplEvent| {
            let mut out = sink.borrow_mut();
            out.push_str(&e.text());
            out.push('\n');
        }),
    );
    let mut chunker = Chunker::new(Separator::Newline);
    for line in text.split_inclusive('\n') {
        if let Some(input) = chunker.push_line(line) {
            session.feed(&input);
        }
    }
    if let Some(input) = chunker.finish() {
        session.feed(&input);
    }
    out.take()
}

#[test]
fn cases_run_as_expected() {
    std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(|| check_cases("tests/repl", "transcript", |case| transcript(&case.input)))
        .unwrap()
        .join()
        .unwrap();
}
