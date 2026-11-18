//! The REPL in wasm, for the playground's REPL tab: one
//! [`ReplSession`] kept in the instance, as `temper repl` keeps one,
//! fed an input at a time, with the same transcript text.
//!
//! - [`repl_start`] begins a session, with the editor's source as input
//!   0 if it has any code;
//! - [`repl_complete`] says whether the text typed so far is whole, as
//!   Enter asks, and how it splits into inputs, as pasted lines split
//!   in `temper repl`;
//! - [`repl_input`] settles and runs one input;
//! - [`repl_query`] answers completion, hover, and signature help for
//!   the text being typed, against the session, without settling or
//!   running it;
//! - [`repl_reset`] drops the session.
//!
//! What an input logs also goes to the page as it's logged, through the
//! imported `host_log` ([`temper_os::host`]), so a long run's output
//! shows while it runs; the run itself can't yield to the page. Each
//! input's answer lists everything it reported, logs too, in order.

use super::{OUTPUT, built_in, input_text, json_string};
use std::cell::RefCell;
use std::fmt::Write;
use std::rc::Rc;
use temper_analysis::{Analysis, DeclKind, Position, byte_offset};
use temper_frontend::interp::OUTPUT_LIMIT;
use temper_frontend::repl::{
    Chunker, OUT_OF_STEPS, ReplEvent, ReplOptions, ReplSession, Separator,
};
use temper_frontend::sources::Sources;

/// What `help()` adds about the page: its keys and limits.
pub const HELP_FOOTER: &str = "Keys: Enter runs the input once it's whole, or else starts a new line in \
     it;\nShift+Enter always starts a new line, and Ctrl+Enter (Cmd+Enter on a Mac)\n\
     always runs; Up and Down, on the first or last line, go through earlier\n\
     inputs; Escape or Stop ends the input running, and the inputs before it\n\
     run again. On a touch screen, Enter starts a new line and Run runs.\n\
     Completion opens as you type a name or `.`, or with Ctrl+Space, with the\n\
     first option picked: Enter or Tab accepts it, and Escape closes it.\n\
     Ctrl+I (Cmd+I) shows the type and docs of the name at the cursor.\n\
     Each input may take 50 million steps; \"Keep going\" runs it again with more.\n";

/// The session and what its inputs report.
struct Repl {
    session: ReplSession,
    events: Rc<RefCell<Events>>,
}

thread_local! {
    static REPL: RefCell<Option<Repl>> = const { RefCell::new(None) };
}

/// Starts a new session, dropping any other, whose inputs may each take
/// `fuel` steps (none or less than one for no limit; the playground
/// gives [`temper_frontend::interp::FUEL`]), with the input buffer's
/// text (from `alloc`) as input 0 if it has code, as the editor's
/// source. Keeps input 0's answer, as [`repl_input`]'s, or `null`.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn repl_start(ptr: *const u8, len: usize, fuel: f64) {
    let source = input_text(ptr, len);
    let json = start_with(page_options(steps(fuel)), &source);
    OUTPUT.with_borrow_mut(|out| *out = json);
}

/// Settles and runs the input buffer's text as the session's next
/// input, which may take `fuel` steps (as for [`repl_start`]), starting
/// a session with no input 0 if there's none. Keeps its answer:
///
/// ```json
/// {"input": 2,
///  "events": [{"kind": "diagnostic", "level": "error", "message": "...",
///              "location": "interactive#2", "line": 1, "column": 11,
///              "text": "interactive#2:1:11: error: ...\n    ...\n    ^^^"},
///             {"kind": "log", "text": "hi"},
///             {"kind": "result", "text": "interactive#2: fail"}],
///  "value": null, "out_of_steps": false}
/// ```
///
/// `events` are what the input reported, in order, each with its
/// `text` as `temper repl` prints it; their kinds are `diagnostic`,
/// `log`, `failure`, `output` (the REPL's functions'), `note`,
/// `unresolved`, `interrupted`, `internal`, `truncated` (where logs
/// past 64 KiB were cut, with how many), and `result`, last. `value`
/// is the result as printed, or `null` if it failed. `out_of_steps`
/// says it failed for running out of steps, so the page can offer to
/// run it again, as the next input, with more.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn repl_input(ptr: *const u8, len: usize, fuel: f64) {
    let text = input_text(ptr, len);
    let json = input(&text, steps(fuel));
    OUTPUT.with_borrow_mut(|out| *out = json);
}

/// Whether the input buffer's text, as typed so far, is whole, so Enter
/// runs it (1) rather than starting a new line (0), by `temper repl`'s
/// rule ([`Chunker`]): a bracket, a multi-quoted string, or a block
/// comment left open, or only comments, waits. Keeps, as
/// `{"complete": true, "inputs": ["let x = 1", "x"], "brackets": 0}`,
/// the inputs it splits into, each for [`repl_input`] in turn (none if
/// it isn't whole), and how many brackets are open at its end, for the
/// next line's indent.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn repl_complete(ptr: *const u8, len: usize) -> u32 {
    let text = input_text(ptr, len);
    let (json, complete) = complete(&text);
    OUTPUT.with_borrow_mut(|out| *out = json);
    complete as u32
}

/// What [`repl_query`] answers.
pub const COMPLETIONS: u32 = 0;
pub const HOVER: u32 = 1;
pub const SIGNATURE: u32 = 2;

/// Answers a question about the input buffer's text, as the REPL's cell
/// holds it, at UTF-16 offset `at`, as if it were the next input: it's
/// read, not settled or run, against what the session's inputs
/// declared. Keeps, by `kind`:
///
/// - [`COMPLETIONS`]: `{"from": 3, "items": [{"name": "twice", "kind":
///   "function", "type": "twice(n: Int32): Int32"}, …]}`, where the word
///   being typed starts, and the names that may go there, innermost
///   first; after `x.`, `x`'s members; in a REPL function's string
///   argument, as `describe(1, "`, its words, of kind `text`;
/// - [`HOVER`]: as `hover`'s, `{"from": 0, "to": 5, "name": "twice", …}`;
/// - [`SIGNATURE`]: as `signature_help`'s.
///
/// `null` if there's no session or nothing to say.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn repl_query(kind: u32, at: u32, ptr: *const u8, len: usize) {
    let text = input_text(ptr, len);
    let json = query(kind, &text, at as usize);
    OUTPUT.with_borrow_mut(|out| *out = json);
}

/// What [`repl_query`] does, at UTF-16 offset `at` of `text`.
pub fn query(kind: u32, text: &str, at: usize) -> String {
    let offset = byte_offset(text, at);
    let utf16 = |byte: usize| Position::of(text, byte).utf16;
    let opt = |s: Option<&str>| s.map_or("null".into(), json_string);
    REPL.with_borrow_mut(|repl| {
        let Some(repl) = repl.as_mut() else {
            return "null".into();
        };
        repl.session.view(|s, scope| {
            let analysis = Analysis::of(s);
            match kind {
                COMPLETIONS => {
                    let languages: Vec<String> = built_in().map(String::from).collect();
                    let (from, items) = match analysis
                        .pending_argument_completions(scope, text, offset, &languages)
                    {
                        Some((from, words)) => {
                            let items = (words.iter())
                                .map(|w| {
                                    format!(
                                        "{{\"name\":{},\"kind\":\"text\",\"type\":null}}",
                                        json_string(w)
                                    )
                                })
                                .collect();
                            (from, items)
                        }
                        None => {
                            let (from, found) = analysis.pending_completions(scope, text, offset);
                            let items: Vec<String> = (found.iter())
                                .map(|c| {
                                    format!(
                                        "{{\"name\":{},\"kind\":{},\"type\":{}}}",
                                        json_string(&c.name),
                                        json_string(c.kind.label()),
                                        opt(c.detail.as_deref())
                                    )
                                })
                                .collect();
                            (from, items)
                        }
                    };
                    format!(
                        "{{\"from\":{},\"items\":[{}]}}",
                        utf16(from),
                        items.join(",")
                    )
                }
                HOVER => match analysis.pending_hover(scope, text, offset) {
                    Some(h) => format!(
                        "{{\"from\":{},\"to\":{},\"name\":{},\"kind\":{},\"type\":{},\"doc\":{}}}",
                        utf16(h.span.start as usize),
                        utf16(h.span.end as usize),
                        opt(h.name.as_deref()),
                        opt(h.kind.map(DeclKind::label)),
                        opt(h.ty.as_deref()),
                        opt(h.doc.as_deref()),
                    ),
                    None => "null".into(),
                },
                SIGNATURE => match analysis.pending_signature(scope, text, offset) {
                    Some(help) => {
                        let params: Vec<String> =
                            help.params.iter().map(|p| json_string(p)).collect();
                        format!(
                            "{{\"label\":{},\"params\":[{}],\"active\":{},\"doc\":{}}}",
                            json_string(&help.label),
                            params.join(","),
                            help.active,
                            opt(help.doc.as_deref())
                        )
                    }
                    None => "null".into(),
                },
                _ => "null".into(),
            }
        })
    })
}

/// Drops the session, and the memory it holds.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "C" fn repl_reset() {
    REPL.with_borrow_mut(|repl| *repl = None);
}

/// A step budget from the page's number.
fn steps(fuel: f64) -> Option<u64> {
    (fuel.is_finite() && fuel >= 1.0).then_some(fuel as u64)
}

/// The page's session: std built in, the REPL's functions with every
/// backend built in, and [`HELP_FOOTER`].
fn page_options(fuel: Option<u64>) -> ReplOptions {
    ReplOptions {
        sources: Sources::memory(&[]),
        fuel,
        tools: Some(Rc::new(temper_driver::repl::ReplTools::default())),
        help_footer: HELP_FOOTER.into(),
        ..ReplOptions::default()
    }
}

/// What [`repl_start`] does, with any options, as for tests: a new
/// session, and input 0's answer, or `null`.
pub fn start_with(options: ReplOptions, source: &str) -> String {
    let fuel = options.fuel;
    let events = Rc::new(RefCell::new(Events::default()));
    let sink = events.clone();
    let session = ReplSession::new(
        options,
        Box::new(move |e: &ReplEvent| sink.borrow_mut().add(e)),
    );
    REPL.with_borrow_mut(|repl| *repl = Some(Repl { session, events }));
    let mut whole = Chunker::new(Separator::Eof);
    whole.push_line(source);
    match whole.finish() {
        Some(source) => input(&source, fuel),
        None => "null".into(),
    }
}

/// What [`repl_input`] does: feeds `text` as the next input, which may
/// take `fuel` steps, and gives its answer.
pub fn input(text: &str, fuel: Option<u64>) -> String {
    if REPL.with_borrow(Option::is_none) {
        start_with(page_options(fuel), "");
    }
    REPL.with_borrow_mut(|repl| {
        let repl = repl.as_mut().expect("a session");
        repl.session.set_fuel(fuel);
        let number = repl.session.inputs();
        *repl.events.borrow_mut() = Events::default();
        let value = repl.session.feed(text);
        let events = std::mem::take(&mut *repl.events.borrow_mut());
        events.answer(number, value.as_deref())
    })
}

/// What [`repl_complete`] does: the JSON, and whether `text` is whole.
pub fn complete(text: &str) -> (String, bool) {
    let mut chunker = Chunker::new(Separator::Newline);
    let mut inputs = Vec::new();
    for line in text.split('\n') {
        inputs.extend(chunker.push_line(line));
    }
    let complete = !chunker.is_pending();
    if !complete {
        inputs.clear();
    }
    let inputs: Vec<String> = inputs.iter().map(|i| json_string(i)).collect();
    let json = format!(
        "{{\"complete\":{complete},\"inputs\":[{}],\"brackets\":{}}}",
        inputs.join(","),
        Chunker::open_brackets(text)
    );
    (json, complete)
}

/// What an input reports, as JSON objects, and its logs' size.
#[derive(Default)]
struct Events {
    json: Vec<String>,
    /// Bytes logged, up to [`OUTPUT_LIMIT`].
    logged: usize,
    /// Lines past the limit, only counted, and where the note of them
    /// goes.
    dropped: usize,
    cut: usize,
    out_of_steps: bool,
}

impl Events {
    fn add(&mut self, event: &ReplEvent) {
        let kind = match event {
            ReplEvent::Diagnostic(_) => "diagnostic",
            ReplEvent::Log(line) => {
                if self.dropped > 0 || self.logged + line.len() > OUTPUT_LIMIT {
                    if self.dropped == 0 {
                        self.cut = self.json.len();
                        self.json.push(String::new());
                    }
                    self.dropped += 1;
                    return;
                }
                self.logged += line.len() + 1;
                stream(line);
                "log"
            }
            ReplEvent::Failure(why) => {
                self.out_of_steps |= why == OUT_OF_STEPS;
                "failure"
            }
            ReplEvent::Interrupted => "interrupted",
            ReplEvent::Unresolved => "unresolved",
            ReplEvent::InternalError(_) => "internal",
            ReplEvent::Output(_) => "output",
            ReplEvent::Note(_) => "note",
            ReplEvent::Result { .. } => "result",
        };
        let mut json = format!("{{\"kind\":\"{kind}\"");
        if let ReplEvent::Diagnostic(d) = event {
            let level = format!("{:?}", d.level).to_lowercase();
            let _ = write!(
                json,
                ",\"level\":{},\"message\":{},\"location\":{},\"line\":{},\"column\":{}",
                json_string(&level),
                json_string(&d.message),
                json_string(&d.location),
                d.line,
                d.column
            );
        }
        let _ = write!(json, ",\"text\":{}}}", json_string(&event.text()));
        self.json.push(json);
    }

    /// The answer for input `number`, which gave `value`.
    fn answer(mut self, number: usize, value: Option<&str>) -> String {
        if self.dropped > 0 {
            let note = format!(
                "[output truncated: {} more lines past {OUTPUT_LIMIT} bytes]",
                self.dropped
            );
            self.json[self.cut] =
                format!("{{\"kind\":\"truncated\",\"text\":{}}}", json_string(&note));
        }
        format!(
            "{{\"input\":{number},\"events\":[{}],\"value\":{},\"out_of_steps\":{}}}",
            self.json.join(","),
            value.map_or("null".into(), json_string),
            self.out_of_steps
        )
    }
}

/// Gives the page a logged line as it's logged.
#[cfg(target_arch = "wasm32")]
fn stream(line: &str) {
    temper_os::host::log(line);
}

#[cfg(not(target_arch = "wasm32"))]
thread_local! {
    static STREAMED: RefCell<Vec<String>> = const { RefCell::new(Vec::new()) };
}

/// Natively, where there's no page, keeps a logged line for
/// [`streamed`].
#[cfg(not(target_arch = "wasm32"))]
fn stream(line: &str) {
    STREAMED.with_borrow_mut(|lines| lines.push(line.to_string()));
}

/// Natively, the lines given to the page so far, taken, as tests check
/// them against each input's answer.
#[cfg(not(target_arch = "wasm32"))]
pub fn streamed() -> Vec<String> {
    STREAMED.take()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{INPUT, alloc};

    /// Puts `text` in the input buffer, as JavaScript would.
    fn put(text: &str) -> (*const u8, usize) {
        let ptr = alloc(text.len());
        INPUT.with_borrow_mut(|input| input.copy_from_slice(text.as_bytes()));
        (ptr, text.len())
    }

    fn output() -> String {
        OUTPUT.with_borrow(|out| out.clone())
    }

    /// Calls the exports on a thread with room to recurse, giving each
    /// call's output.
    fn session(calls: &'static [(&'static str, &'static str, f64)]) -> Vec<String> {
        std::thread::Builder::new()
            .stack_size(64 << 20)
            .spawn(move || {
                let mut outputs = Vec::new();
                for &(export, text, fuel) in calls {
                    let (ptr, len) = put(text);
                    match export {
                        "start" => repl_start(ptr, len, fuel),
                        "input" => repl_input(ptr, len, fuel),
                        "complete" => {
                            let whole = repl_complete(ptr, len);
                            assert_eq!(whole, output().starts_with("{\"complete\":true") as u32);
                        }
                        "reset" => repl_reset(),
                        "streamed" => {
                            let lines = streamed().join("|");
                            OUTPUT.with_borrow_mut(|out| *out = lines);
                        }
                        _ => unreachable!(),
                    }
                    outputs.push(output());
                }
                outputs
            })
            .unwrap()
            .join()
            .unwrap()
    }

    /// The editor's source as input 0, later inputs using it, logs
    /// streamed, a diagnostic, and a reset that forgets it all.
    #[test]
    fn keeps_a_session() {
        let out = session(&[
            ("start", "let twice(n: Int): Int { n * 2 }\n// end\n", 0.0),
            (
                "input",
                "console.log(\"a\"); console.log(\"b\"); twice(21)",
                0.0,
            ),
            ("streamed", "", 0.0),
            ("input", "twice(\"x\")", 0.0),
            ("reset", "", 0.0),
            ("input", "twice", 0.0),
            ("start", "  // only a comment\n", 0.0),
        ]);
        assert_eq!(
            out[0],
            "{\"input\":0,\"events\":[{\"kind\":\"result\",\"text\":\"interactive#0: void\"}],\
             \"value\":\"void\",\"out_of_steps\":false}"
        );
        assert_eq!(
            out[1],
            "{\"input\":1,\"events\":[{\"kind\":\"log\",\"text\":\"a\"},\
             {\"kind\":\"log\",\"text\":\"b\"},\
             {\"kind\":\"result\",\"text\":\"interactive#1: 42\"}],\
             \"value\":\"42\",\"out_of_steps\":false}"
        );
        assert_eq!(out[2], "a|b");
        assert!(
            out[3].starts_with(
                "{\"input\":2,\"events\":[{\"kind\":\"diagnostic\",\"level\":\"error\",\
                 \"message\":\"Expected subtype of Int32, but got String\",\
                 \"location\":\"interactive#2\",\"line\":1,\"column\":7,\
                 \"text\":\"interactive#2:1:7: error: Expected subtype of Int32, but got String\\n"
            ),
            "{}",
            out[3]
        );
        // A new session, started by the input itself, without `twice`.
        assert!(
            out[5].starts_with("{\"input\":0,\"events\":[{\"kind\":\"diagnostic\""),
            "{}",
            out[5]
        );
        assert!(out[5].contains("twice has not been declared"), "{}", out[5]);
        assert_eq!(out[6], "null");
    }

    /// The editor's tests run once, as input 0, with a summary; a later
    /// input's test is refused.
    #[test]
    fn runs_the_editors_tests() {
        let out = session(&[
            (
                "start",
                "let twice(n: Int): Int { n * 2 }\ntest(\"ok\") { assert(twice(1) == 2) }\n\
                 test(\"off\") { assert(twice(2) == 5) }\n",
                0.0,
            ),
            ("input", "twice(4)", 0.0),
            ("input", "test(\"later\") { assert(true) }", 0.0),
        ]);
        assert_eq!(
            out[0],
            "{\"input\":0,\"events\":[{\"kind\":\"failure\",\
             \"text\":\"Test failed: off - expected twice(2) == (5) not (4)\"},\
             {\"kind\":\"note\",\"text\":\"Tests passed: 1 of 2\"},\
             {\"kind\":\"result\",\"text\":\"interactive#0: void\"}],\
             \"value\":\"void\",\"out_of_steps\":false}"
        );
        assert!(out[1].contains("\"interactive#1: 8\""), "{}", out[1]);
        assert!(out[2].contains("Tests don't run in the REPL"), "{}", out[2]);
    }

    /// An input that runs out of its steps says so, and runs again, as
    /// the next input, with more.
    #[test]
    fn runs_out_of_steps_then_keeps_going() {
        let out = session(&[
            (
                "start",
                "let count(): Int {\n  var n = 0;\n  for (var i = 0; i < 100000; i += 1) { n += 1; }\n  n\n}",
                0.0,
            ),
            ("input", "count()", 10_000.0),
            ("input", "count()", 1e9),
        ]);
        assert_eq!(
            out[1],
            "{\"input\":1,\"events\":[{\"kind\":\"failure\",\
             \"text\":\"Panicked: ran out of steps\"},\
             {\"kind\":\"result\",\"text\":\"interactive#1: fail\"}],\
             \"value\":null,\"out_of_steps\":true}"
        );
        assert!(
            out[2].ends_with(
                "\"text\":\"interactive#2: 100000\"}],\"value\":\"100000\",\"out_of_steps\":false}"
            ),
            "{}",
            out[2]
        );
    }

    /// Logs past 64 KiB are cut, with a note where, and not streamed.
    #[test]
    fn cuts_long_logs() {
        let out = session(&[
            (
                "input",
                "for (var i = 0; i < 2000; i += 1) { console.log(\"0123456789012345678901234567890123456789\") }",
                0.0,
            ),
            ("streamed", "", 0.0),
        ]);
        let answer: serde_json::Value = serde_json::from_str(&out[0]).unwrap();
        let events = answer["events"].as_array().unwrap();
        let logs = events.iter().filter(|e| e["kind"] == "log").count();
        assert_eq!(logs, OUTPUT_LIMIT / 41);
        assert_eq!(
            events[logs]["text"],
            format!(
                "[output truncated: {} more lines past {OUTPUT_LIMIT} bytes]",
                2000 - logs
            )
        );
        assert_eq!(events[logs + 1]["kind"], "result");
        assert_eq!(out[1].split('|').count(), logs);
    }

    /// Enter's question: whether the text is whole, its inputs, and the
    /// brackets open.
    #[test]
    fn says_when_input_is_whole() {
        let out = session(&[
            ("complete", "let x = 1\nx", 0.0),
            ("complete", "let f(a: Int,", 0.0),
            ("complete", "/** Docs. */", 0.0),
            ("complete", "", 0.0),
        ]);
        assert_eq!(
            out,
            [
                "{\"complete\":true,\"inputs\":[\"let x = 1\",\"x\"],\"brackets\":0}",
                "{\"complete\":false,\"inputs\":[],\"brackets\":1}",
                "{\"complete\":false,\"inputs\":[],\"brackets\":0}",
                "{\"complete\":true,\"inputs\":[],\"brackets\":0}",
            ]
        );
    }

    /// Runs `f` on a thread with room to recurse.
    fn deep<R: Send + 'static>(f: impl FnOnce() -> R + Send + 'static) -> R {
        std::thread::Builder::new()
            .stack_size(64 << 20)
            .spawn(f)
            .unwrap()
            .join()
            .unwrap()
    }

    /// Completion, hover, and signature help in the cell see what
    /// earlier inputs declared, and the REPL's functions, without
    /// running the text asked about.
    #[test]
    fn answers_queries_about_the_cell() {
        let out = deep(|| {
            let mut out = Vec::new();
            out.push(query(COMPLETIONS, "tw", 2));
            start_with(
                page_options(None),
                "/** Doubles. */\nlet twice(n: Int): Int { n * 2 }",
            );
            input("class Box(public width: Int) {}", None);
            input("let box = new Box(3)", None);
            let ask = |kind, text: &str, at| {
                let answer = query(kind, text, at);
                streamed();
                answer
            };
            out.push(ask(COMPLETIONS, "tw", 2));
            out.push(ask(COMPLETIONS, "box.wi", 6));
            out.push(ask(COMPLETIONS, "runT", 4));
            out.push(ask(COMPLETIONS, "describe(1, \"t", 14));
            out.push(ask(HOVER, "1 + twice(2)", 6));
            out.push(ask(HOVER, "box.width", 6));
            out.push(ask(HOVER, "let twice = 1", 6));
            out.push(ask(SIGNATURE, "twice(", 6));
            // A query runs nothing: this would log, and fail.
            out.push(ask(HOVER, "console.log(\"no\"); twice(1 / 0)", 22));
            out.push(streamed().join("|"));
            out.push(input("twice(4)", None));
            out.push(ask(COMPLETIONS, "console.", 8));
            out.push(ask(HOVER, "let s: String = \"\"", 8));
            out.push(ask(COMPLETIONS, "let f(count: Int): Int {\n  cou", 30));
            out
        });
        assert_eq!(out[0], "null");
        assert!(
            out[1].starts_with(
                "{\"from\":0,\"items\":[{\"name\":\"twice\",\"kind\":\"function\",\
                 \"type\":\"twice(n: Int32): Int32\"}"
            ),
            "{}",
            out[1]
        );
        assert!(
            out[2].starts_with("{\"from\":4,\"items\":[{\"name\":\"width\""),
            "{}",
            out[2]
        );
        assert!(out[3].contains("\"name\":\"runTests\""), "{}", out[3]);
        assert_eq!(
            out[4],
            "{\"from\":13,\"items\":[{\"name\":\"types\",\"kind\":\"text\",\"type\":null},\
             {\"name\":\"tmpl\",\"kind\":\"text\",\"type\":null}]}"
        );
        assert_eq!(
            out[5],
            "{\"from\":4,\"to\":9,\"name\":\"twice\",\"kind\":\"function\",\
             \"type\":\"twice(n: Int32): Int32\",\"doc\":\"Doubles.\"}"
        );
        assert!(
            out[6].starts_with("{\"from\":4,\"to\":9,\"name\":\"width\""),
            "{}",
            out[6]
        );
        assert_eq!(out[7], "null");
        assert_eq!(
            out[8],
            "{\"label\":\"twice(n: Int32): Int32\",\"params\":[\"n: Int32\"],\"active\":0,\
             \"doc\":\"Doubles.\"}"
        );
        assert!(out[9].contains("\"name\":\"twice\""), "{}", out[9]);
        assert_eq!(out[10], "");
        assert!(out[11].starts_with("{\"input\":3,"), "{}", out[11]);
        // `console`'s members, and a type's name.
        assert_eq!(
            out[12],
            "{\"from\":8,\"items\":[{\"name\":\"log\",\"kind\":\"method\",\
             \"type\":\"log(message: String): Void\"}]}"
        );
        assert!(
            out[13].starts_with(
                "{\"from\":7,\"to\":13,\"name\":\"String\",\"kind\":\"builtin\",\
                 \"type\":\"String\",\"doc\":\"A *String*"
            ),
            "{}",
            out[13]
        );
        // A parameter the cell declares.
        assert!(
            out[14]
                .starts_with("{\"from\":27,\"items\":[{\"name\":\"count\",\"kind\":\"parameter\""),
            "{}",
            out[14]
        );
    }

    /// `runTests()` runs input 0's tests again against a redefinition.
    #[test]
    fn runs_tests_again() {
        let out = session(&[
            (
                "start",
                "let twice(n: Int): Int { n * 2 }\ntest(\"three\") { assert(twice(3) == 6) }\n",
                0.0,
            ),
            ("input", "let twice(n: Int): Int { n + 1 }", 0.0),
            ("input", "runTests()", 0.0),
        ]);
        assert_eq!(
            out[2],
            "{\"input\":2,\"events\":[{\"kind\":\"failure\",\
             \"text\":\"Test failed: three - expected twice(3) == (6) not (4)\"},\
             {\"kind\":\"note\",\"text\":\"Tests passed: 0 of 1\"},\
             {\"kind\":\"result\",\"text\":\"interactive#2: void\"}],\
             \"value\":\"void\",\"out_of_steps\":false}"
        );
    }
}
