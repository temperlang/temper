//! A REPL session: Temper inputs settled and run one at a time against
//! what earlier inputs declared, for `temper repl`, and later the
//! playground's console and the language server.
//!
//! - **One growing library.** Each input is a file of one module, with a
//!   scope of its own chained to the previous input's, so later inputs
//!   see earlier names directly, and a later declaration shadows an
//!   earlier one: code that already resolved to the old one keeps it.
//!   Each input is settled and typed once, incrementally
//!   ([`Incremental`]); a top-level `let` is visible from its end, so
//!   `let s = "${s}!"` reads the earlier `s`, and a top-level `var`
//!   can be assigned from later inputs, checked against its type.
//! - **One interpreter** whose globals grow by each input
//!   ([`crate::interp::repl`]): code lowered once stays lowered, and
//!   `async` blocks waiting on promises stay parked until a later input
//!   resolves them. An input may `await` at its top level.
//! - **Errors never end a session.** An input with errors still settles
//!   and runs as far as it can, as libraries do; a declaration whose
//!   input failed stays declared, and a read of it fails saying why. A
//!   panic in tempest itself is caught and reported, and the session
//!   goes on. An interrupt, as from Ctrl-C, stops the input running.
//!
//! Each input reports, in order, its diagnostics, what it logs as it
//! runs, why it failed if no diagnostic says, and its result, as
//! [`ReplEvent`]s; [`ReplEvent::text`] gives each as `temper repl`
//! prints it, the result in Kotlin's form, `interactive#N: value`.
//! [`Chunker`] splits typed or piped lines into inputs.
//!
//! The REPL's own functions, `help`, `describe`, and `translate`, are
//! declared before the first input ([`specials`]).
//!
//! What the REPL prints of its own, diagnostics, failures, and its
//! functions' output, has control characters escaped
//! ([`escape_controls`]), so it can't move the cursor, recolor the
//! terminal, or reorder text; a value's strings are quoted, escaped as
//! Temper's are. What an input logs is printed as it's logged.

mod specials;

pub use specials::{ReplTools, STEPS};

/// How [`ReplTools`]' functions say a module has errors, so isn't
/// translated, before a line for each.
pub const MODULE_ERRORS: &str = "the module has errors, so it isn't translated";

/// What [`ReplTools`]' functions fail with when they're interrupted.
pub const INTERRUPTED: &str = crate::interp::INTERRUPTED;

/// What an input that ran out of its steps ([`ReplOptions::fuel`]) fails
/// with, as a [`ReplEvent::Failure`], so a front end can offer to run it
/// again with more.
pub const OUT_OF_STEPS: &str = crate::interp::OUT_OF_STEPS;

use crate::interp::repl::{Outcome, Runner, render};
use crate::library::{FileId, Level, Library, Message};
use crate::library_tests::Tally;
use crate::project::load_imports;
use crate::scope::{Lookup, ScopeId};
use crate::settle::{Incremental, Settled};
use crate::sources::Sources;
use specials::{HOST_NAME, PRELUDE, PRELUDE_PATH, Specials};
use std::borrow::Cow;
use std::cell::{Cell, RefCell};
use std::collections::{HashMap, HashSet};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::rc::Rc;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use temper_syntax::lex::{SourceKind, Synthetic, TokenKind, lex};
use temper_syntax::tree::{NodeId, NodeKind};

/// The directory the inputs' files are in, as their module's; never read,
/// so it's no library's. Their relative imports resolve from the current
/// directory, as if they were there.
const DIR: &str = "-repl-";

/// How a session is set up.
pub struct ReplOptions {
    /// Where std and the libraries that inputs import are read from.
    pub sources: Sources,
    /// The libraries an import can name, each a name and its root
    /// directory, as [`crate::project::find_libraries`] finds them under
    /// a work root.
    pub libraries: Vec<(String, String)>,
    /// Steps each input may take; `None` for no limit, as `temper run`.
    pub fuel: Option<u64>,
    /// Stops the input running once set, as by Ctrl-C; whoever sets it
    /// clears it.
    pub interrupt: Option<Arc<AtomicBool>>,
    /// Reports hints too, not only errors and warnings.
    pub hints: bool,
    /// What `help`, `describe`, and `translate` need beyond the
    /// frontend; without them, they say what's missing.
    pub tools: Option<Rc<dyn ReplTools>>,
    /// What the front end adds to `help()`, as its keys and options.
    pub help_footer: String,
}

impl Default for ReplOptions {
    fn default() -> ReplOptions {
        ReplOptions {
            sources: Sources::standard(),
            libraries: Vec::new(),
            fuel: None,
            interrupt: None,
            hints: false,
            tools: None,
            help_footer: String::new(),
        }
    }
}

/// Something an input reports, in the order it happens.
#[derive(Clone, Debug, PartialEq)]
pub enum ReplEvent {
    Diagnostic(ReplDiagnostic),
    /// A line the input logged, as it's logged.
    Log(String),
    /// Why the input, or a module it loaded, failed as it ran, where no
    /// diagnostic of its already says.
    Failure(String),
    /// The input was interrupted.
    Interrupted,
    /// The input awaits a promise nothing has resolved; it goes on if a
    /// later input resolves it.
    Unresolved,
    /// A bug in tempest, a panic, caught; the session goes on.
    InternalError(String),
    /// What one of the REPL's functions shows, as `help()`.
    Output(String),
    /// Something the REPL notes, as that an earlier input used a name
    /// before this one declared it.
    Note(String),
    /// What the input gave, as Kotlin's REPL prints it: `fail` if it
    /// failed.
    Result {
        input: usize,
        value: String,
    },
}

/// A diagnostic, located in its input (`interactive#N`) or file.
#[derive(Clone, Debug, PartialEq)]
pub struct ReplDiagnostic {
    pub level: Level,
    pub message: String,
    /// `interactive#N` for an input, else the file's path.
    pub location: String,
    /// From 1, the column in characters.
    pub line: usize,
    pub column: usize,
    /// The source line, and a line of `^` under the span.
    pub snippet: [String; 2],
}

impl ReplEvent {
    /// The event as `temper repl` prints it, a line or more, without a
    /// final newline.
    ///
    /// All but logs have control characters escaped
    /// ([`escape_controls`]).
    pub fn text(&self) -> String {
        match self {
            ReplEvent::Diagnostic(d) => {
                let level = format!("{:?}", d.level).to_lowercase();
                format!(
                    "{}:{}:{}: {level}: {}\n    {}\n    {}",
                    escape_controls(&d.location),
                    d.line,
                    d.column,
                    escape_controls(&d.message),
                    d.snippet[0],
                    d.snippet[1]
                )
            }
            ReplEvent::Log(line) => line.clone(),
            ReplEvent::Failure(text) | ReplEvent::Output(text) | ReplEvent::Note(text) => {
                escape_controls(text).into_owned()
            }
            ReplEvent::Interrupted => "Interrupted".into(),
            ReplEvent::Unresolved => "1 promise is unresolved.".into(),
            ReplEvent::InternalError(why) => format!(
                "temper: internal error (a bug in Temper, not in your code): {}",
                escape_controls(why)
            ),
            ReplEvent::Result { input, value } => {
                format!("interactive#{input}: {}", escape_controls(value))
            }
        }
    }
}

/// Where events go.
type Sink = Rc<RefCell<Box<dyn FnMut(&ReplEvent)>>>;

/// A REPL session ([module docs](self)).
pub struct ReplSession {
    settle: Incremental,
    runner: Runner,
    /// What the REPL's own functions read, shared with the interpreter's
    /// host that runs them.
    specials: Rc<RefCell<Specials>>,
    sink: Sink,
    hints: bool,
    /// Inputs fed so far.
    inputs: usize,
    /// Each input's scope, with its number.
    segments: HashMap<ScopeId, usize>,
    /// Names an input found undeclared, with the first such input, so a
    /// later input declaring one can note it.
    undeclared: HashMap<String, usize>,
    /// Input 0's text with all but its tests blanked, lines and byte
    /// offsets kept, so `runTests()` can settle them again against later
    /// inputs' declarations.
    test_source: Option<String>,
    /// Input 0's file, and the files `runTests()` settled its tests from,
    /// whose diagnostics show input 0's lines.
    test_files: (FileId, Vec<FileId>),
    /// Makes the next input panic, to test that a panic is caught.
    #[cfg(test)]
    panic_next: bool,
}

impl ReplSession {
    /// A session with nothing fed yet, giving each event to `sink` as it
    /// happens.
    pub fn new(options: ReplOptions, sink: Box<dyn FnMut(&ReplEvent)>) -> ReplSession {
        let mut library = Library::new();
        library.libraries = options.libraries;
        library.relative_from = Some((DIR.into(), ".".into()));
        let sink: Sink = Rc::new(RefCell::new(sink));
        let logs = sink.clone();
        let log =
            Box::new(move |line: &str| (*logs.borrow_mut())(&ReplEvent::Log(line.to_string())));
        let specials = Rc::new(RefCell::new(Specials {
            tools: options.tools,
            sources: options.sources,
            libraries: library.libraries.clone(),
            help_footer: options.help_footer,
            inputs: Vec::new(),
            interrupt: options.interrupt.clone(),
            prelude: None,
            host: None,
            has_tests: false,
            run_tests: Cell::new(false),
        }));
        let host = specials::host(specials.clone(), sink.clone());
        let mut session = ReplSession {
            settle: Incremental::new(library),
            runner: Runner::new(log, options.interrupt, options.fuel, Some(host)),
            specials,
            sink,
            hints: options.hints,
            inputs: 0,
            segments: HashMap::new(),
            undeclared: HashMap::new(),
            test_source: None,
            test_files: (FileId(0), Vec::new()),
            #[cfg(test)]
            panic_next: false,
        };
        session.settle_prelude();
        session
    }

    /// Declares the REPL's own functions ([`specials`]) in a scope before
    /// the first input's.
    fn settle_prelude(&mut self) {
        let library = self.settle.library_mut();
        let file = library.add_file(PRELUDE_PATH, PRELUDE, SourceKind::Standalone);
        let root = library.files[file.0 as usize].root;
        // The host's function gets a name no input can write.
        let host = library.names.find(HOST_NAME);
        let hidden = library.names.temporary(HOST_NAME);
        let mut stack = vec![root];
        while let Some(node) = stack.pop() {
            let tree = &library.tree;
            stack.extend(tree.children(node));
            if host.is_some() && library.name(node) == host {
                let kind = tree.kind(node);
                library.tree.replace(node, kind, hidden.0, &[]);
            }
        }
        let round = self.settle.settle_input(file);
        let scope = round.input.scope;
        let runner = &mut self.runner;
        self.settle.view(|s| {
            runner.add_module(s, scope);
            runner.run_input(s, &round.input, root, false, usize::MAX);
        });
        let host = (self.settle.scopes().get(scope).names.iter())
            .find(|&&(name, _)| name == hidden)
            .and_then(|&(_, symbol)| self.settle.scopes().symbol(symbol).decl);
        let mut specials = self.specials.borrow_mut();
        specials.prelude = Some(scope);
        specials.host = host;
    }

    /// Lends what's settled so far, with the scope that the next input's
    /// names are looked up from, as for completion.
    pub fn view<R>(&mut self, f: impl FnOnce(&Settled, ScopeId) -> R) -> R {
        let scope = self.settle.last_segment().unwrap_or(crate::scope::BUILTINS);
        self.settle.view(|s| f(s, scope))
    }

    /// Input `n`, with the inputs it uses, as one Temper module, as
    /// `describe(n, "module")` shows it; `None` if there's no input `n`.
    pub fn module_source(&mut self, n: usize) -> Option<String> {
        if n >= self.inputs {
            return None;
        }
        let specials = self.specials.clone();
        self.settle.view(|s| specials.borrow().module(s, n).ok())
    }

    /// Sets the steps each later input may take, as
    /// [`ReplOptions::fuel`] does from the start; `None` for no limit.
    pub fn set_fuel(&mut self, fuel: Option<u64>) {
        self.runner.set_fuel(fuel);
    }

    /// Inputs fed so far, which is the next one's number.
    pub fn inputs(&self) -> usize {
        self.inputs
    }

    /// Settles and runs one input, reporting what happens to the sink,
    /// and returns its result as printed, `None` if it failed.
    pub fn feed(&mut self, text: &str) -> Option<String> {
        let input = self.inputs;
        self.inputs += 1;
        let scopes_before = self.settle.scopes().scopes.len();
        let fed = catch_unwind(AssertUnwindSafe(|| self.feed_input(input, text)));
        let value = match fed {
            Ok(value) => value,
            Err(payload) => {
                let why = panic_text(payload);
                // Settling panicked, if the input isn't registered: the
                // round is abandoned, and the modules it loaded, never to
                // run, get globals that say why when read. A panic as it
                // ran leaves what's settled as it is.
                if self.specials.borrow().inputs.len() <= input {
                    let modules = self.settle.abandon_round(scopes_before);
                    let runner = &mut self.runner;
                    let unset = format!("loading it failed in interactive#{input}");
                    self.settle.view(|s| {
                        for scope in modules {
                            runner.add_module(s, scope);
                            runner.note_unset(s, scope, &unset);
                        }
                    });
                    self.specials.borrow_mut().inputs.push(None);
                }
                self.runner.recover();
                self.emit(&ReplEvent::InternalError(why));
                None
            }
        };
        let shown = value.clone().unwrap_or_else(|| "fail".into());
        self.emit(&ReplEvent::Result {
            input,
            value: shown,
        });
        value
    }

    fn emit(&self, event: &ReplEvent) {
        (*self.sink.borrow_mut())(event);
    }

    fn feed_input(&mut self, input: usize, text: &str) -> Option<String> {
        let path = format!("{DIR}/interactive#{input}");
        let library = self.settle.library_mut();
        let diagnostics_before = library.diagnostics.len();
        let file = library.add_file(&path, text, SourceKind::Standalone);
        load_imports(&self.specials.borrow().sources, library);
        #[cfg(test)]
        if std::mem::take(&mut self.panic_next) {
            panic!("as a test asked");
        }
        // Only input 0, the editor's code or the first typed, runs tests.
        self.settle.allow_tests(input == 0);
        let round = self.settle.settle_input(file);
        self.segments.insert(round.input.scope, input);
        (self.specials.borrow_mut().inputs).push(Some((file, round.input.scope)));
        let errors = self.report_diagnostics(input, diagnostics_before);
        self.note_late_declarations(input, round.input.scope);
        if input == 0 && round.input.tests.is_some() {
            self.test_source = Some(self.tests_only(file, &round.input.test_cases));
            self.test_files.0 = file;
            self.specials.borrow_mut().has_tests = true;
        }

        let root = self.settle.library().files[file.0 as usize].root;
        let semicolon = ends_with_semicolon(text);
        let kind = round.last.map(|n| self.settle.library().tree.kind(n));
        // The last statement's value, unless it's a declaration, or a
        // `;` follows it.
        let keep =
            !semicolon && kind.is_some_and(|k| !matches!(k, NodeKind::Let | NodeKind::Class));
        // A class or interface declared last gives itself.
        let class = round
            .last
            .filter(|_| !semicolon && kind == Some(NodeKind::Class));
        let latest = round.input.scope;
        let tests = (round.input.tests).map(|run| (latest, run, round.input.test_cases.len()));
        let (runner, segments, sink) = (&mut self.runner, &self.segments, &self.sink);
        let emit = |event: ReplEvent| (*sink.borrow_mut())(&event);
        let resumed = |runner: &mut Runner, s: &Settled| {
            for (input, why) in runner.resumed_failures() {
                let scope = (segments.iter())
                    .find(|&(_, &i)| i == input)
                    .map(|(&scope, _)| scope);
                emit(ReplEvent::Failure(match why.strip_prefix("Failed: ") {
                    _ if why == "Failed" => format!("interactive#{input} failed"),
                    Some(why) => format!("interactive#{input} failed: {why}"),
                    None => format!("interactive#{input} failed: {why}"),
                }));
                if let Some(scope) = scope {
                    runner.note_unset(s, scope, &format!("interactive#{input} failed"));
                }
            }
        };
        let value = self.settle.view(|s| {
            // Every global first, so a panic as one runs leaves none
            // missing.
            for module in &round.modules {
                runner.add_module(s, module.scope);
            }
            runner.add_module(s, latest);
            for module in &round.modules {
                let outcome = runner.run_module(s, module);
                resumed(runner, s);
                match outcome {
                    Outcome::Value(_) | Outcome::Unresolved => {}
                    Outcome::Interrupted => emit(ReplEvent::Interrupted),
                    Outcome::Failed(why) => {
                        let dir = &s.library.modules[module.id.0 as usize].dir;
                        emit(ReplEvent::Failure(format!("{dir}: {why}")));
                    }
                }
            }
            let outcome = runner.run_input(s, &round.input, root, keep, input);
            resumed(runner, s);
            let label = |class| class_label(s, segments, latest, class);
            match outcome {
                Outcome::Value(value) if keep => Some(render(s, &value, &label)),
                Outcome::Value(_) => Some(class.map_or("void".into(), label)),
                Outcome::Failed(why) => {
                    // Not again if a diagnostic of the input said it; nor
                    // a bare `Failed`, a bubble's, which `fail` says.
                    let said = why
                        .strip_prefix("Failed: ")
                        .is_some_and(|m| errors.contains(m));
                    if !said && why != "Failed" {
                        emit(ReplEvent::Failure(why));
                    }
                    runner.note_unset(s, latest, &format!("interactive#{input} failed"));
                    None
                }
                Outcome::Interrupted => {
                    emit(ReplEvent::Interrupted);
                    let why = format!("interactive#{input} was interrupted");
                    runner.note_unset(s, latest, &why);
                    None
                }
                Outcome::Unresolved => {
                    emit(ReplEvent::Unresolved);
                    None
                }
            }
        });
        if let Some((scope, run, defined)) = tests {
            self.run_tests(scope, run, defined, value.is_some());
        }
        if self.specials.borrow().run_tests.take() {
            self.rerun_tests(input);
        }
        value
    }

    /// Input 0's source, `file`, with all but its tests, `cases`, blanked,
    /// keeping lines and byte offsets, and a `;` after each test that a
    /// line doesn't end.
    fn tests_only(&self, file: FileId, cases: &[NodeId]) -> String {
        let library = self.settle.library();
        let source = library.files[file.0 as usize].parsed.source();
        let mut keep = vec![false; source.len()];
        let mut ends = Vec::new();
        for &case in cases {
            let span = library.tree.span(case);
            let (at, start) = library.locate(span.start);
            let start = start as usize;
            let end = (start + span.end.saturating_sub(span.start) as usize).min(source.len());
            if at == file && start <= end {
                keep[start..end].fill(true);
                ends.push(end);
            }
        }
        let mut out = String::with_capacity(source.len());
        for (i, c) in source.char_indices() {
            match c {
                _ if keep[i] || c == '\n' => out.push(c),
                _ => {
                    out.push(if ends.contains(&i) { ';' } else { ' ' });
                    // Each byte kept, so offsets are input 0's.
                    out.extend(std::iter::repeat_n(' ', c.len_utf8() - 1));
                }
            }
        }
        out
    }

    /// `runTests()`, once input `input` ends: input 0's tests settled
    /// again as a round of their own, chained after it, so their names
    /// mean the newest declarations, then run and reported as input 0's
    /// were. Diagnostics are located in input 0, where the tests are.
    fn rerun_tests(&mut self, input: usize) {
        let Some(source) = self.test_source.clone() else {
            return;
        };
        let scopes_before = self.settle.scopes().scopes.len();
        let settled = catch_unwind(AssertUnwindSafe(|| {
            let library = self.settle.library_mut();
            let before = library.diagnostics.len();
            let path = format!("{DIR}/interactive#0");
            let file = library.add_file(&path, &source, SourceKind::Standalone);
            self.settle.allow_tests(true);
            let round = self.settle.settle_input(file);
            self.settle.allow_tests(false);
            (file, round, before)
        }));
        if let Ok((file, ..)) = &settled {
            self.test_files.1.push(*file);
        }
        let (file, round, before) = match settled {
            Ok(settled) => settled,
            Err(payload) => {
                self.settle.allow_tests(false);
                let modules = self.settle.abandon_round(scopes_before);
                let runner = &mut self.runner;
                self.settle.view(|s| {
                    for scope in modules {
                        runner.add_module(s, scope);
                    }
                });
                self.runner.recover();
                return self.emit(&ReplEvent::InternalError(panic_text(payload)));
            }
        };
        self.report_diagnostics(input, before);
        let Some(run) = round.input.tests else {
            return;
        };
        let root = self.settle.library().files[file.0 as usize].root;
        let scope = round.input.scope;
        let (runner, sink) = (&mut self.runner, &self.sink);
        let ran = self.settle.view(|s| {
            for module in &round.modules {
                runner.add_module(s, module.scope);
            }
            runner.add_module(s, scope);
            for module in &round.modules {
                runner.run_module(s, module);
            }
            match runner.run_input(s, &round.input, root, false, input) {
                Outcome::Value(_) => true,
                Outcome::Failed(why) => {
                    (*sink.borrow_mut())(&ReplEvent::Failure(why));
                    false
                }
                Outcome::Interrupted => {
                    (*sink.borrow_mut())(&ReplEvent::Interrupted);
                    false
                }
                Outcome::Unresolved => false,
            }
        });
        self.run_tests(scope, run, round.input.test_cases.len(), ran);
    }

    /// Runs tests, `run` in `scope` ([`crate::settle::SettledModule::tests`]), `defined`
    /// of them, if `ran`, as their input ran to its end, and reports each
    /// failure and then `temper test`'s summary, as `Tests passed: 2 of
    /// 3`; if not, that none ran.
    fn run_tests(&mut self, scope: ScopeId, run: NodeId, defined: usize, ran: bool) {
        let runner = &mut self.runner;
        let results = match ran {
            true => self.settle.view(|s| runner.run_tests(s, scope, run)),
            false => Ok(Vec::new()),
        };
        let results = match results {
            Ok(results) => results,
            Err(Outcome::Interrupted) => return self.emit(&ReplEvent::Interrupted),
            Err(Outcome::Failed(why)) => return self.emit(&ReplEvent::Failure(why)),
            Err(Outcome::Value(_) | Outcome::Unresolved) => return,
        };
        for (name, why) in &results {
            if let Some(why) = why {
                self.emit(&ReplEvent::Failure(format!("Test failed: {name} - {why}")));
            }
        }
        let run = results.len();
        let tally = Tally {
            run,
            failed: results.iter().filter(|r| r.1.is_some()).count(),
            defined,
            not_run: defined.saturating_sub(run),
            unexpected: run.saturating_sub(defined),
        };
        self.emit(&ReplEvent::Note(tally.summary()));
    }

    /// Notes names an earlier input found undeclared that input `input`
    /// declares, as for mutual recursion over two inputs, once each.
    fn note_late_declarations(&mut self, input: usize, scope: ScopeId) {
        if self.undeclared.is_empty() {
            return;
        }
        let library = self.settle.library();
        let mut notes = Vec::new();
        for &(name, _) in &self.settle.scopes().get(scope).names {
            let name = library.names.get(name);
            if let Some(earlier) = self.undeclared.remove(name).filter(|&e| e < input) {
                notes.push(format!(
                    "Note: interactive#{earlier} used {name} before this input declared it. \
                     An input sees only what earlier inputs declared, so declarations that \
                     use each other go in one input: on one line, or with \
                     --separator semisemi, ended by ;;"
                ));
            }
        }
        for note in notes {
            self.emit(&ReplEvent::Note(note));
        }
    }

    /// Reports the diagnostics made since `before`, each once, returning
    /// the errors' messages, and notes the names input `input` found
    /// undeclared.
    fn report_diagnostics(&mut self, input: usize, before: usize) -> HashSet<String> {
        let lib = self.settle.library();
        let mut undeclared = Vec::new();
        let mut seen = HashSet::new();
        let mut errors = HashSet::new();
        for d in &lib.diagnostics[before..] {
            if d.level == Level::Hint && !self.hints {
                continue;
            }
            let message = d.message.text();
            if !seen.insert((d.span.start, d.span.end, message.clone())) {
                continue;
            }
            if d.level == Level::Error {
                errors.insert(message.clone());
            }
            if let Message::UndeclaredName(name) = &d.message {
                undeclared.push(name.to_string());
            }
            let (id, start) = lib.locate(d.span.start);
            let file = &lib.files[id.0 as usize];
            let location = match file.path.strip_prefix(&format!("{DIR}/")) {
                Some(name) => name.to_string(),
                None => file.path.clone(),
            };
            let source = match self.test_files.1.contains(&id) {
                true => lib.files[self.test_files.0.0 as usize].parsed.source(),
                false => file.parsed.source(),
            };
            let length = d.span.end.saturating_sub(d.span.start) as usize;
            let (line, column, snippet) = snippet(source, start as usize, length);
            self.emit(&ReplEvent::Diagnostic(ReplDiagnostic {
                level: d.level,
                message,
                location,
                line,
                column,
                snippet,
            }));
        }
        for name in undeclared {
            self.undeclared.entry(name).or_insert(input);
        }
        errors
    }
}

/// What a caught panic says.
fn panic_text(payload: Box<dyn std::any::Any + Send>) -> String {
    (payload.downcast_ref::<&str>().map(|s| s.to_string()))
        .or_else(|| payload.downcast_ref::<String>().cloned())
        .unwrap_or_else(|| "a panic".into())
}

/// A class's or interface's name as a result shows it: plain, as `C`,
/// unless a later input's `C` shadows it, then with its input's, as
/// `interactive#2.C`.
fn class_label(
    s: &Settled,
    segments: &HashMap<ScopeId, usize>,
    latest: ScopeId,
    class: NodeId,
) -> String {
    let name_node = s.library.tree.children(class)[0];
    let Some(name) = s.library.name(name_node) else {
        return "?".into();
    };
    let text = s.library.names.get(name);
    let symbol = s.scopes.resolved(name_node);
    let shadowed = match s.scopes.lookup(latest, name, false, u32::MAX) {
        Lookup::Found(b) => Some(b) != symbol,
        _ => false,
    };
    let owner = symbol.map(|b| s.scopes.symbol(b).owner);
    match owner.and_then(|o| segments.get(&o)) {
        Some(input) if shadowed => format!("interactive#{input}.{text}"),
        _ => text.to_string(),
    }
}

/// The 1-based line and column (in characters) of `start` in `source`,
/// and the line with `^` under the span, at least one, up to the line's
/// end; tabs are shown as spaces, so the marks line up, and control
/// characters escaped ([`escape_controls`]), the marks under their
/// escapes.
fn snippet(source: &str, start: usize, length: usize) -> (usize, usize, [String; 2]) {
    let mut start = start.min(source.len());
    while !source.is_char_boundary(start) {
        start -= 1;
    }
    let line_start = source[..start].rfind('\n').map_or(0, |i| i + 1);
    let line_end = source[start..]
        .find('\n')
        .map_or(source.len(), |i| start + i);
    let line = source[..start].matches('\n').count() + 1;
    let column = source[line_start..start].chars().count() + 1;
    let mut end = (start + length).min(line_end);
    while !source.is_char_boundary(end) {
        end -= 1;
    }
    // The line as shown, and where the span starts and ends in it, in
    // characters.
    let mut text = String::new();
    let (mut from, mut to) = (0, 0);
    let mut shown = 0;
    let body = source[line_start..line_end].trim_end_matches('\r');
    for ((i, _), (c, escape)) in body.char_indices().zip(escapes(body)) {
        let at = line_start + i;
        if at == start {
            from = shown;
        }
        if at == end {
            to = shown;
        }
        let before = text.len();
        match c {
            '\t' => text.push(' '),
            c if escape => text.push_str(&format!("\\u{{{:x}}}", c as u32)),
            c => text.push(c),
        }
        shown += text[before..].chars().count();
    }
    if start >= line_start + body.len() {
        from = shown;
    }
    if end >= line_start + body.len() {
        to = shown;
    }
    let (text, from, to) = clip(&text, shown, from, to);
    let marks = to.saturating_sub(from).max(1);
    let under = format!("{}{}", " ".repeat(from), "^".repeat(marks));
    (line, column, [text, under])
}

/// About how many characters of a source line a snippet shows.
const SNIPPET_WIDTH: usize = 200;

/// A snippet's line, `length` characters long, with the span from `from`
/// to `to` in it, cut to a window of about [`SNIPPET_WIDTH`] characters
/// around the span's start, `…` where it's cut; and the span in that.
fn clip(text: &str, length: usize, from: usize, to: usize) -> (String, usize, usize) {
    if length <= SNIPPET_WIDTH {
        return (text.to_string(), from, to);
    }
    let lead = SNIPPET_WIDTH / 3;
    let start = from.saturating_sub(lead).min(length - SNIPPET_WIDTH);
    let end = start + SNIPPET_WIDTH;
    let mut out = String::new();
    let shift = match start > 0 {
        true => {
            out.push('…');
            start - 1
        }
        false => 0,
    };
    out.extend(text.chars().skip(start).take(SNIPPET_WIDTH));
    if end < length {
        out.push('…');
    }
    (out, from - shift, to.min(end) - shift)
}

/// Whether a character would change the terminal, or how text around it
/// reads, if printed as is, wherever it is: a C0 control other than a
/// line break or tab, DEL, a C1 control, a bidirectional control, a line
/// or paragraph separator, or an invisible character, as zero-width
/// spaces, word joiners, invisible operators, and fillers. Joiners and
/// tag characters depend on what's around them ([`escapes`]).
fn needs_escape(c: char) -> bool {
    let n = c as u32;
    (n < 0x20 && c != '\n' && c != '\t')
        || (0x7f..=0x9f).contains(&n)
        || matches!(n, 0x61c | 0x200e | 0x200f | 0x202a..=0x202e | 0x2066..=0x2069)
        || matches!(n, 0x2028 | 0x2029 | 0x200b | 0x2060..=0x2064 | 0x206a..=0x206f)
        || matches!(
            n,
            0xad | 0x115f | 0x1160 | 0x180e | 0x3164 | 0xffa0 | 0xfeff
        )
        || matches!(n, 0xfff9..=0xfffb | 0xe0000..=0xe001f)
}

/// A zero-width joiner or non-joiner, which joins or parts the visible
/// characters around it, as in emoji sequences.
fn is_joiner(c: char) -> bool {
    matches!(c, '\u{200c}' | '\u{200d}')
}

/// A tag character, invisible, which only an emoji tag sequence uses, as
/// in a subdivision's flag.
fn is_tag(c: char) -> bool {
    (0xe0020..=0xe007f).contains(&(c as u32))
}

/// Each character of `text`, with whether it's escaped: those
/// [`needs_escape`] says; a joiner unless it's between two visible
/// characters, so an emoji sequence such as 👩🏽‍💻 stays whole; and
/// tag characters unless they're an emoji tag sequence, after U+1F3F4
/// and ended by U+E007F, so they can't hide text after a visible one.
fn escapes(text: &str) -> Vec<(char, bool)> {
    let chars: Vec<char> = text.chars().collect();
    let visible = |d: Option<&char>| {
        d.is_some_and(|&d| !d.is_whitespace() && !needs_escape(d) && !is_joiner(d) && !is_tag(d))
    };
    let mut out = Vec::with_capacity(chars.len());
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        if is_tag(c) {
            let end = (i..chars.len())
                .find(|&j| !is_tag(chars[j]))
                .unwrap_or(chars.len());
            let sequence = i > 0 && chars[i - 1] == '\u{1f3f4}' && chars[end - 1] == '\u{e007f}';
            out.extend(chars[i..end].iter().map(|&t| (t, !sequence)));
            i = end;
            continue;
        }
        let escape = match is_joiner(c) {
            true => {
                !(visible(i.checked_sub(1).and_then(|p| chars.get(p))) && visible(chars.get(i + 1)))
            }
            false => needs_escape(c),
        };
        out.push((c, escape));
        i += 1;
    }
    out
}

/// Text with the characters that could rewrite the terminal or hide text
/// escaped as Temper writes them, `\u{1b}` ([`escapes`]): C0 controls but
/// for line breaks and tabs, DEL, C1 controls, bidirectional controls,
/// line and paragraph separators, and invisible characters.
pub fn escape_controls(text: &str) -> Cow<'_, str> {
    if !text
        .chars()
        .any(|c| needs_escape(c) || is_joiner(c) || is_tag(c))
    {
        return Cow::Borrowed(text);
    }
    let mut out = String::with_capacity(text.len() + 8);
    for (c, escape) in escapes(text) {
        match escape {
            true => out.push_str(&format!("\\u{{{:x}}}", c as u32)),
            false => out.push(c),
        }
    }
    match out == text {
        true => Cow::Borrowed(text),
        false => Cow::Owned(out),
    }
}

/// Whether code's last token is `;`, which makes an input give `void`.
fn ends_with_semicolon(text: &str) -> bool {
    let tokens = lex(text, SourceKind::Standalone);
    let last = tokens.iter().rev().find(|t| !t.kind.is_ignorable());
    last.is_some_and(|t| t.kind == TokenKind::Punctuation && &text[t.range()] == ";")
}

/// What ends an input, as Kotlin's `--separator` chooses.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum Separator {
    /// A line break outside brackets, strings, and comments (the default).
    #[default]
    Newline,
    /// `;;` at the end of a line, as OCaml's, dropped from the input.
    SemiSemi,
    /// The end of all input: it's one input.
    Eof,
}

impl Separator {
    /// A separator by its name on the command line.
    pub fn named(name: &str) -> Option<Separator> {
        match name {
            "newline" => Some(Separator::Newline),
            "semisemi" => Some(Separator::SemiSemi),
            "eof" => Some(Separator::Eof),
            _ => None,
        }
    }
}

/// Splits lines into inputs, by Temper's own lexer, so strings and UTF-8
/// are exactly as the language reads them. With [`Separator::Newline`],
/// a line ends an input unless a bracket (`(`, `[`, `{`, `${`), a
/// multi-quoted string, or a block comment is still open, or the input
/// so far is only comments, as a doc comment waits for what it
/// documents. A `"` string never spans lines, so one left open ends with
/// its line, an error, rather than swallowing the lines after it.
#[derive(Debug, Default)]
pub struct Chunker {
    separator: Separator,
    pending: String,
}

impl Chunker {
    pub fn new(separator: Separator) -> Chunker {
        Chunker {
            separator,
            pending: String::new(),
        }
    }

    /// Whether an input is begun but not complete, as for a continuation
    /// prompt.
    pub fn is_pending(&self) -> bool {
        !self.pending.trim().is_empty()
    }

    /// Drops the input begun so far.
    pub fn clear(&mut self) {
        self.pending.clear();
    }

    /// Whether `text`, its lines fed to a new chunker, leaves nothing
    /// pending: what an editor's Enter asks before running a buffer of
    /// one or more lines, so typed and piped lines split alike.
    pub fn completes(separator: Separator, text: &str) -> bool {
        let mut chunker = Chunker::new(separator);
        for line in text.split('\n') {
            chunker.push_line(line);
        }
        separator != Separator::Eof && !chunker.is_pending()
    }

    /// How many brackets (`(`, `[`, `{`, `${`) are open at the end of
    /// `text`, as for indenting the line after it.
    pub fn open_brackets(text: &str) -> usize {
        let tokens = lex(text, SourceKind::Standalone);
        let mut depth = 0usize;
        for t in &tokens {
            if t.kind != TokenKind::Punctuation || t.synthetic.is_some() {
                continue;
            }
            match &text[t.range()] {
                "(" | "[" | "{" | "${" => depth += 1,
                ")" | "]" | "}" => depth = depth.saturating_sub(1),
                _ => {}
            }
        }
        depth
    }

    /// Adds a line, with or without its line break, giving the input it
    /// completes, if it completes one.
    pub fn push_line(&mut self, line: &str) -> Option<String> {
        self.pending.push_str(line);
        if !line.ends_with('\n') {
            self.pending.push('\n');
        }
        if self.pending.trim().is_empty() {
            self.pending.clear();
            return None;
        }
        match self.separator {
            Separator::Eof => None,
            Separator::Newline => complete(&self.pending).then(|| self.take()),
            Separator::SemiSemi => {
                let tokens = lex(&self.pending, SourceKind::Standalone);
                let last = tokens.iter().rev().find(|t| !t.kind.is_ignorable())?;
                let ends = last.kind == TokenKind::Punctuation
                    && last.synthetic.is_none()
                    && &self.pending[last.range()] == ";;";
                if !ends {
                    return None;
                }
                let mut input = self.take();
                input.replace_range(last.range(), "  ");
                Some(input.trim_end().to_string())
            }
        }
    }

    /// At the end of input: what's pending, if it's more than space and
    /// comments.
    pub fn finish(&mut self) -> Option<String> {
        let input = self.take();
        has_code(&input).then_some(input)
    }

    /// What's pending, without its trailing lines of only space: a line
    /// break, or the indent an editor's Enter put on the blank line that
    /// ended a multi-quoted string, so typed and piped inputs are alike.
    fn take(&mut self) -> String {
        let mut input = std::mem::take(&mut self.pending);
        loop {
            while input.ends_with(['\n', '\r']) {
                input.pop();
            }
            let last = input.rfind('\n').map_or(0, |i| i + 1);
            if last == 0 || !input[last..].trim().is_empty() {
                break;
            }
            input.truncate(last);
        }
        input
    }
}

/// Whether text's last line, before its final line break, is blank.
fn blank_last_line(text: &str) -> bool {
    let text = text.strip_suffix('\n').unwrap_or(text);
    text.rsplit('\n')
        .next()
        .is_some_and(|line| line.trim().is_empty())
}

/// Whether text has more than space and comments.
fn has_code(text: &str) -> bool {
    let tokens = lex(text, SourceKind::Standalone);
    (tokens.iter()).any(|t| !matches!(t.kind, TokenKind::Comment | TokenKind::Space))
}

/// Whether text is a complete input ([`Chunker`]).
fn complete(text: &str) -> bool {
    let tokens = lex(text, SourceKind::Standalone);
    let mut depth = 0i32;
    let mut code = false;
    for t in &tokens {
        let token = &text[t.range()];
        match t.kind {
            TokenKind::Comment if token.starts_with("/*") && !token.ends_with("*/") => {
                return false;
            }
            TokenKind::Comment | TokenKind::Space => {}
            TokenKind::Punctuation if t.synthetic.is_none() => {
                code = true;
                match token {
                    "(" | "[" | "{" | "${" => depth += 1,
                    ")" | "]" | "}" => depth -= 1,
                    _ => {}
                }
            }
            // A multi-quoted string open at the end, whose next line may
            // go on with it, unless a blank line ended it.
            TokenKind::RightDelimiter
                if t.synthetic == Some(Synthetic::TripleQuote)
                    && t.start as usize == text.len()
                    && !blank_last_line(text) =>
            {
                return false;
            }
            _ => code = true,
        }
    }
    code && depth <= 0
}

#[cfg(test)]
mod tests {
    use super::*;

    fn transcript(inputs: &[&str]) -> Vec<String> {
        let events = Rc::new(RefCell::new(Vec::new()));
        let seen = events.clone();
        let sink = Box::new(move |e: &ReplEvent| seen.borrow_mut().push(e.text()));
        let mut session = ReplSession::new(ReplOptions::default(), sink);
        for input in inputs {
            session.feed(input);
        }
        events.take()
    }

    #[test]
    fn chunks_by_brackets_strings_and_comments() {
        let mut c = Chunker::new(Separator::Newline);
        assert_eq!(c.push_line("1 + 2\n"), Some("1 + 2".into()));
        assert_eq!(c.push_line("   \n"), None);
        assert!(!c.is_pending());
        assert_eq!(c.push_line("f(1,\n"), None);
        assert!(c.is_pending());
        assert_eq!(c.push_line("  2)\n"), Some("f(1,\n  2)".into()));
        assert_eq!(c.push_line("// a doc comment\n"), None);
        assert_eq!(
            c.push_line("let x = 1\n"),
            Some("// a doc comment\nlet x = 1".into())
        );
        assert_eq!(c.push_line("/* open\n"), None);
        assert_eq!(
            c.push_line("closed */ 3\n"),
            Some("/* open\nclosed */ 3".into())
        );
        // A `"` string ends with its line.
        assert_eq!(c.push_line("\"open\n"), Some("\"open".into()));
        assert_eq!(c.push_line("let s = \"\"\"\n"), None);
        assert_eq!(c.push_line("  \"two\n"), None);
        assert_eq!(c.push_line("  \"lines\n"), None);
        assert_eq!(
            c.push_line("\n"),
            Some("let s = \"\"\"\n  \"two\n  \"lines".into())
        );
        // Ended by a line of only space, as an editor's Enter indents it,
        // the same as by a blank line.
        assert_eq!(c.push_line("let s = \"\"\"\n"), None);
        assert_eq!(c.push_line("  \"two\n"), None);
        assert_eq!(
            c.push_line("    \n"),
            Some("let s = \"\"\"\n  \"two".into())
        );
        assert_eq!(c.push_line("\"${ 1 +\n"), None);
        assert_eq!(c.push_line("2 }\"\n"), Some("\"${ 1 +\n2 }\"".into()));
        // A closer too many ends it, as an error.
        assert_eq!(c.push_line("1 }\n"), Some("1 }".into()));
        assert_eq!(c.push_line("[1,\n"), None);
        assert_eq!(c.finish(), Some("[1,".into()));
        // Comments alone at the end aren't an input.
        assert_eq!(c.push_line("// done\n"), None);
        assert_eq!(c.finish(), None);
    }

    #[test]
    fn says_whether_a_buffer_completes() {
        let newline = Separator::Newline;
        assert!(Chunker::completes(newline, "1 + 2"));
        assert!(Chunker::completes(newline, "f(1,\n  2)"));
        assert!(!Chunker::completes(newline, "f(1,"));
        assert!(!Chunker::completes(newline, "// a doc comment"));
        assert!(!Chunker::completes(newline, "let s = \"\"\"\n  \"text"));
        assert!(Chunker::completes(newline, "let s = \"\"\"\n  \"text\n"));
        assert!(Chunker::completes(newline, "1\n2"));
        assert!(!Chunker::completes(Separator::SemiSemi, "1"));
        assert!(Chunker::completes(Separator::SemiSemi, "1 ;;"));
        assert!(!Chunker::completes(Separator::Eof, "1"));
        assert_eq!(Chunker::open_brackets("f(["), 2);
        assert_eq!(Chunker::open_brackets("\"${ {"), 2);
        assert_eq!(Chunker::open_brackets("f(1)"), 0);
    }

    #[test]
    fn escapes_controls() {
        assert_eq!(escape_controls("plain\ttext\n"), "plain\ttext\n");
        assert_eq!(
            escape_controls("a\u{1b}[2Jb\u{7f}\u{9b}c\u{202e}d\u{2066}\u{61c}"),
            "a\\u{1b}[2Jb\\u{7f}\\u{9b}c\\u{202e}d\\u{2066}\\u{61c}"
        );
        // Separators and invisible characters.
        assert_eq!(
            escape_controls("a\u{2028}b\u{2029}c\u{200b}d\u{feff}e\u{2060}f\u{ad}"),
            "a\\u{2028}b\\u{2029}c\\u{200b}d\\u{feff}e\\u{2060}f\\u{ad}"
        );
        // A joiner between visible characters stays, so emoji sequences
        // print whole; alone, or at an edge, it's escaped.
        let coder = "👩🏽\u{200d}💻";
        assert_eq!(escape_controls(coder), coder);
        assert_eq!(
            escape_controls("🏳\u{fe0f}\u{200d}🌈"),
            "🏳\u{fe0f}\u{200d}🌈"
        );
        assert_eq!(escape_controls("a\u{200c}b"), "a\u{200c}b");
        assert_eq!(
            escape_controls("\u{200d}a \u{200d} b\u{200d}\u{200d}c\u{200c}"),
            "\\u{200d}a \\u{200d} b\\u{200d}\\u{200d}c\\u{200c}"
        );
        // Tags only as an emoji tag sequence, not hiding text after a
        // visible character.
        let scotland = "🏴\u{e0067}\u{e0062}\u{e0073}\u{e0063}\u{e0074}\u{e007f}";
        assert_eq!(escape_controls(scotland), scotland);
        assert_eq!(
            escape_controls("ok\u{e0068}\u{e0069}"),
            "ok\\u{e0068}\\u{e0069}"
        );
        let (_, _, lines) = snippet("x = \u{200d}y", 7, 1);
        assert_eq!(
            lines,
            ["x = \\u{200d}y".to_string(), "            ^".to_string()]
        );
        let (_, _, lines) = snippet("x = \u{1b}y", 5, 1);
        assert_eq!(
            lines,
            ["x = \\u{1b}y".to_string(), "          ^".to_string()]
        );
    }

    #[test]
    fn chunks_by_semisemi_and_eof() {
        let mut c = Chunker::new(Separator::SemiSemi);
        assert_eq!(c.push_line("let f(): Int { 1 }\n"), None);
        assert_eq!(
            c.push_line("f() ;;\n"),
            Some("let f(): Int { 1 }\nf()".into())
        );
        let mut c = Chunker::new(Separator::Eof);
        assert_eq!(c.push_line("1\n"), None);
        assert_eq!(c.push_line("2\n"), None);
        assert_eq!(c.finish(), Some("1\n2".into()));
    }

    #[test]
    fn quotes_as_kotlin_does() {
        let mut out = String::new();
        crate::interp::repl::quote("a\"b\\c\n$\u{a0}x\u{1}é😀", &mut out);
        assert_eq!(out, "\"a\\\"b\\\\c\\n\\u{24,a0}x\\u{1}é😀\"");
    }

    #[test]
    fn snippets_underline_spans() {
        let (line, column, lines) = snippet("ab\n\tcdé fg\n", 6, 4);
        assert_eq!((line, column), (2, 4));
        assert_eq!(lines, [" cdé fg".to_string(), "   ^^^".to_string()]);
        let (_, _, lines) = snippet("x = 1", 0, 0);
        assert_eq!(lines[1], "^");
        // A long line is cut to a window around the span.
        let long = format!("{} nope {}", "a".repeat(5000), "b".repeat(5000));
        let (line, column, lines) = snippet(&long, 5001, 4);
        assert_eq!((line, column), (1, 5002));
        assert_eq!(lines[0].chars().count(), SNIPPET_WIDTH + 2);
        assert!(lines[0].starts_with('…') && lines[0].ends_with('…'));
        let marks = lines[1].trim_start();
        assert_eq!(marks, "^^^^");
        let at = lines[1].len() - marks.len();
        assert_eq!(
            lines[0].chars().skip(at).take(4).collect::<String>(),
            "nope"
        );
        // At the end of a long line.
        let (_, _, lines) = snippet(&long, long.len(), 0);
        assert!(lines[0].starts_with('…') && !lines[0].ends_with('…'));
        assert_eq!(lines[1].len(), SNIPPET_WIDTH + 2);
    }

    /// Dropping a very deep value doesn't overflow the stack, on a test's
    /// thread, whose stack is small (2 MiB unless RUST_MIN_STACK says).
    #[test]
    fn a_deep_value_drops() {
        let out = transcript(&[
            "class N(public next: N?) {}",
            "var n: N? = null; for (var i = 0; i < 200000; ++i) { n = new N(n); }",
            "n = null;",
            "var l: List<AnyValue> = []; for (var i = 0; i < 200000; ++i) { l = [l]; }",
            "l = [];",
            "1",
        ]);
        assert_eq!(
            out.last().map(String::as_str),
            Some("interactive#5: 1"),
            "{out:?}"
        );
    }

    #[test]
    fn a_huge_string_prints_cut() {
        let out = transcript(&[
            "let b = new StringBuilder(); for (var i = 0; i < 200000; ++i) { b.append(\"y\"); }; b.toString()",
        ]);
        let result = &out[0];
        assert!(result.len() < (1 << 17), "{}", result.len());
        assert!(
            result.ends_with("yyy\"…"),
            "{}",
            &result[result.len() - 20..]
        );
    }

    #[test]
    fn a_panic_is_caught_and_the_session_goes_on() {
        let events = Rc::new(RefCell::new(Vec::new()));
        let seen = events.clone();
        let sink = Box::new(move |e: &ReplEvent| seen.borrow_mut().push(e.text()));
        let mut session = ReplSession::new(ReplOptions::default(), sink);
        session.feed("let x = 1");
        session.panic_next = true;
        let hook = std::panic::take_hook();
        std::panic::set_hook(Box::new(|_| {}));
        session.feed("x + 1");
        std::panic::set_hook(hook);
        session.feed("x + 2");
        assert_eq!(
            events.take(),
            [
                "interactive#0: void",
                "temper: internal error (a bug in Temper, not in your code): as a test asked",
                "interactive#1: fail",
                "interactive#2: 3",
            ]
        );
    }

    /// A panic while settling: the input's number stays its own, later
    /// inputs follow the one before it, and what it loaded, never run,
    /// says why when read.
    #[test]
    fn a_panic_while_settling_is_rolled_back() {
        let events = Rc::new(RefCell::new(Vec::new()));
        let seen = events.clone();
        let sink = Box::new(move |e: &ReplEvent| seen.borrow_mut().push(e.text()));
        let mut session = ReplSession::new(ReplOptions::default(), sink);
        session.feed("let x = 1");
        session.settle.panic_next = true;
        let hook = std::panic::take_hook();
        std::panic::set_hook(Box::new(|_| {}));
        session.feed("let { Date } = import(\"std/temporal\"); let y = 2");
        std::panic::set_hook(hook);
        for input in [
            "x + 2",
            "describe(2, \"module\")",
            "describe(1)",
            "y",
            "let { Date } = import(\"std/temporal\")",
            "new Date(2020, 1, 2)",
            "let z = x * 3",
            "z",
        ] {
            session.feed(input);
        }
        assert_eq!(
            events.take(),
            [
                "interactive#0: void",
                "temper: internal error (a bug in Temper, not in your code): as a test asked, \
                 while settling",
                "interactive#1: fail",
                "interactive#2: 3",
                "// interactive#0\nlet x = 1;\n// interactive#2\nx + 2;",
                "interactive#3: void",
                "Failed: input 1 failed as it was settled, a bug in Temper, so it has nothing \
                 to show",
                "interactive#4: fail",
                "interactive#5:1:1: error: y has not been declared\n    y\n    ^",
                "interactive#5: fail",
                "Failed: Date has no value: loading it failed in interactive#1",
                "interactive#6: fail",
                "Failed: Date has no value: interactive#6 failed",
                "interactive#7: fail",
                "interactive#8: void",
                "interactive#9: 3",
            ]
        );
    }

    /// Ctrl-C stops the REPL's own functions too: here, as `translate`
    /// translates into the first of two languages.
    #[test]
    fn the_repls_functions_can_be_interrupted() {
        struct Tools(Arc<AtomicBool>);
        impl ReplTools for Tools {
            fn symbol_help(&self, _: &Settled, _: crate::scope::SymbolId) -> Option<String> {
                None
            }
            fn tmpl(
                &self,
                _: Library,
                _: &Sources,
                _: &dyn Fn() -> bool,
            ) -> Result<String, String> {
                Ok(String::new())
            }
            fn translate(
                &self,
                _: Library,
                _: &Sources,
                language: &str,
                interrupted: &dyn Fn() -> bool,
            ) -> Result<Vec<(String, String)>, String> {
                // Ctrl-C, pressed as it works.
                self.0.store(true, std::sync::atomic::Ordering::Relaxed);
                assert!(interrupted());
                Ok(vec![(format!("{language}.txt"), "code".into())])
            }
            fn languages(&self) -> Vec<String> {
                vec!["a".into(), "b".into()]
            }
        }
        let flag = Arc::new(AtomicBool::new(false));
        let events = Rc::new(RefCell::new(Vec::new()));
        let seen = events.clone();
        let sink = Box::new(move |e: &ReplEvent| seen.borrow_mut().push(e.text()));
        let options = ReplOptions {
            interrupt: Some(flag.clone()),
            tools: Some(Rc::new(Tools(flag.clone()))),
            ..ReplOptions::default()
        };
        let mut session = ReplSession::new(options, sink);
        session.feed("let x = 1");
        session.feed("translate(0)");
        flag.store(false, std::sync::atomic::Ordering::Relaxed);
        flag.store(true, std::sync::atomic::Ordering::Relaxed);
        session.feed("describe(0, \"module\")");
        flag.store(false, std::sync::atomic::Ordering::Relaxed);
        session.feed("help(\"x\")");
        assert_eq!(
            events.take(),
            [
                "interactive#0: void",
                "Interrupted",
                "interactive#1: fail",
                "Interrupted",
                "interactive#2: fail",
                "x: Int32",
                "interactive#3: void",
            ]
        );
    }

    /// A multi-quoted string typed, its last line ended on an indented
    /// line as a terminal's Enter leaves it, is the string piped.
    #[test]
    fn typed_and_piped_strings_are_alike() {
        let lines = |text: &str| {
            let mut chunker = Chunker::new(Separator::Newline);
            let mut inputs: Vec<String> = text
                .split_inclusive('\n')
                .filter_map(|l| chunker.push_line(l))
                .collect();
            inputs.extend(chunker.finish());
            inputs
        };
        let typed = lines("let s = \"\"\"\n  \"two\n  \ns\n");
        let piped = lines("let s = \"\"\"\n  \"two\n\ns\n");
        assert_eq!(typed, piped);
        let inputs: Vec<&str> = typed.iter().map(String::as_str).collect();
        assert_eq!(transcript(&inputs)[1], "interactive#1: \"two\"");
    }

    #[test]
    fn names_carry_over_and_shadow() {
        assert_eq!(
            transcript(&["let x = 1", "x + 1", "let x = \"${x}!\"", "x"]),
            [
                "interactive#0: void",
                "interactive#1: 2",
                "interactive#2: void",
                "interactive#3: \"1!\""
            ]
        );
    }
}
