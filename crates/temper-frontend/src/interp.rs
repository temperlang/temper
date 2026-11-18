//! An interpreter over core constructs, for running functional tests
//! in-process and, later, compile-time evaluation. Values are dynamically
//! typed until types are in the settle loop. Error nodes fail when run,
//! carrying their message, as a backend's generated code would.
//!
//! Each function body is lowered to a flat form, ops with jumps and
//! numbered slots ([`lower`]), on its first call, and run by one loop over
//! a frame ([`frame`]). Builtins and the class machinery (members, `new`,
//! `is`) are here and in the modules below, and call back into Temper
//! functions directly.
//!
//! A generator owns its body's frame, which pauses at each `yield`,
//! returning from the loop, and goes on from there on `next()`. That's
//! enough because `yield` appears only directly in the generator's own
//! body, never in a function it calls or passes on. An `async` block is
//! a generator too, run from a queue, which also pauses at an `await` of
//! a promise that isn't resolved yet ([`promises`]).
//!
//! Bodiless `@connected` members and functions run only where the
//! interpreter implements them, as Kotlin Temper's do: so far std's
//! `Date.today()`, `regex`'s ([`regexes`]) and `net`'s ([`net`]). A
//! user's `@connected` class with a complete Temper implementation runs
//! that, as an ordinary class, as Kotlin Temper's interpreter does.

pub mod compile_time;
mod frame;
mod lower;
mod maps;
mod net;
mod numbers;
mod promises;
mod regexes;
pub(crate) mod repl;
mod strings;

use crate::builtins::{Builtin, Constant, Function, TYPES};
use crate::core::{
    self, CLASS_CONNECTED, CLASS_INTERFACE, MEMBER_CONNECTED, MEMBER_OPERATOR_CMP,
    MEMBER_OPERATOR_EQ, MEMBER_PRIVATE, MEMBER_STATIC, ROLE_CONSTRUCTOR, ROLE_GETTER, ROLE_MASK,
    ROLE_METHOD, ROLE_PARAM, ROLE_SETTER,
};
use crate::settle::Settled;
use frame::{Cell, Frame};
use lower::{Code, GeneratorKind, Layout, Lowering};
use promises::{Promise, Task};
use std::cell::{OnceCell, RefCell};
use std::collections::{HashMap, VecDeque};
use std::rc::Rc;
use temper_syntax::ast::Value;
use temper_syntax::names::NameId;
use temper_syntax::tree::NodeId;

/// A run-time value.
#[derive(Clone, Debug)]
pub enum Val {
    Void,
    Null,
    Bool(bool),
    Int(i32),
    Int64(i64),
    Float(f64),
    Str(Rc<str>),
    /// A position in a string, as a UTF-8 offset; Temper's are opaque.
    StringIndex(u32),
    /// `StringIndex.none`, the other `StringIndexOption`.
    NoStringIndex,
    StringBuilder(Rc<RefCell<String>>),
    /// An immutable `List`.
    List(Rc<[Val]>),
    ListBuilder(Rc<RefCell<Vec<Val>>>),
    Deque(Rc<RefCell<VecDeque<Val>>>),
    /// A `DenseBitVector`; bits past the end are false.
    Bits(Rc<RefCell<Vec<bool>>>),
    Map(Rc<maps::Entries>),
    MapBuilder(Rc<RefCell<maps::Entries>>),
    /// A `Pair`'s key and value.
    Pair(Rc<(Val, Val)>),
    Fn(Rc<Closure>),
    Builtin(Function),
    /// A method of a value, as `x.toString` before it's called.
    Method(Rc<(Val, NameId)>),
    /// A class or interface, as its name's value, for `new`, `as` and `is`.
    Class(Rc<Class>),
    Object(Rc<Object>),
    Console,
    /// A type name used as a value; types aren't run yet.
    Type,
    /// A nullable type, `T?`, for `x is T?` and `x as T?`, which null
    /// passes.
    NullableType(Rc<Val>),
    /// A builtin type, by its index in [`crate::builtins::TYPES`], for
    /// `new ListBuilder()` and `List.of`.
    BuiltinType(u8),
    /// The one value of type `Empty`, from `empty()`.
    Empty,
    /// A generator, from calling a function that `extends GeneratorFn`.
    Generator(Rc<Generator>),
    /// A generator's result: a `ValueResult` with what it yielded, or a
    /// `DoneResult`.
    ValueResult(Rc<Val>),
    DoneResult,
    /// A `Promise`, and a `PromiseBuilder` with its promise.
    Promise(Rc<Promise>),
    PromiseBuilder(Rc<Promise>),
    /// A compiled regex, std's `Regex`'s connected value.
    Regex(Rc<crate::regex::Regex>),
    /// A bodiless `@connected` function, by its `Let`.
    Connected(NodeId),
    /// A response from std `net`'s `sendRequest`.
    NetResponse(Rc<net::NetResponse>),
    /// A callout a compile-time pass gives a library, keeping what it's
    /// called with ([`compile_time::callout`]).
    Callout(compile_time::Callouts),
    /// A declared local not yet given a value.
    Uninit,
}

/// Dropping a value drops the containers in it without recursing, so a
/// very deep value, as a linked list of millions of objects, can't
/// overflow the stack: the containers that only it owns are taken out
/// into a work list, each of which, dropped in turn, has had its own
/// taken out first.
impl Drop for Val {
    #[inline]
    fn drop(&mut self) {
        // Most values hold none, or share what they hold: those drop as
        // they would without this.
        if self.holds_values() {
            drop_deep(self);
        }
    }
}

/// Drops what a container holds through a work list ([`Val`]'s `Drop`).
#[inline(never)]
fn drop_deep(value: &mut Val) {
    let mut work = Vec::new();
    take_containers(value, &mut work);
    while let Some(mut value) = work.pop() {
        take_containers(&mut value, &mut work);
    }
}

impl Val {
    /// Whether it may hold other values, which its drop would drop.
    #[inline]
    fn holds_values(&self) -> bool {
        matches!(
            self,
            Val::List(_)
                | Val::ListBuilder(_)
                | Val::Deque(_)
                | Val::Map(_)
                | Val::MapBuilder(_)
                | Val::Pair(_)
                | Val::Method(_)
                | Val::NullableType(_)
                | Val::ValueResult(_)
                | Val::Object(_)
                | Val::Fn(_)
                | Val::Class(_)
        )
    }
}

/// Moves the values in `value` that hold others into `work`, if `value`
/// is a container that nothing else shares; its other values it drops
/// as it is.
fn take_containers(value: &mut Val, work: &mut Vec<Val>) {
    let mut take = |v: &mut Val| {
        if v.holds_values() {
            work.push(std::mem::replace(v, Val::Null));
        }
    };
    match value {
        Val::List(items) => Rc::get_mut(items).into_iter().flatten().for_each(take),
        Val::ListBuilder(items) => (Rc::get_mut(items).into_iter())
            .for_each(|items| items.get_mut().iter_mut().for_each(&mut take)),
        Val::Deque(items) => (Rc::get_mut(items).into_iter())
            .for_each(|items| items.get_mut().iter_mut().for_each(&mut take)),
        Val::Map(entries) => (Rc::get_mut(entries).into_iter())
            .flat_map(|e| e.pairs_mut().iter_mut())
            .for_each(|(k, v)| {
                take(k);
                take(v);
            }),
        Val::MapBuilder(entries) => (Rc::get_mut(entries).into_iter())
            .flat_map(|e| e.get_mut().pairs_mut().iter_mut())
            .for_each(|(k, v)| {
                take(k);
                take(v);
            }),
        Val::Pair(pair) => {
            if let Some((k, v)) = Rc::get_mut(pair) {
                take(k);
                take(v);
            }
        }
        Val::Method(method) => Rc::get_mut(method).into_iter().for_each(|m| take(&mut m.0)),
        Val::NullableType(inner) | Val::ValueResult(inner) => {
            Rc::get_mut(inner).into_iter().for_each(take)
        }
        Val::Object(object) => (Rc::get_mut(object).into_iter())
            .for_each(|o| o.fields.get_mut().iter_mut().for_each(&mut take)),
        Val::Fn(closure) => (Rc::get_mut(closure).into_iter())
            .filter_map(|c| Rc::get_mut(&mut c.captures))
            .flat_map(|cells| cells.iter_mut())
            .filter_map(Rc::get_mut)
            .for_each(|cell| take(cell.get_mut())),
        Val::Class(class) => {
            if let Some(class) = Rc::get_mut(class) {
                class.statics.get_mut().iter_mut().for_each(&mut take);
                (Rc::get_mut(&mut class.captures).into_iter())
                    .flat_map(|cells| cells.iter_mut())
                    .filter_map(Rc::get_mut)
                    .for_each(|cell| take(cell.get_mut()));
            }
        }
        _ => {}
    }
}

/// A function value: its `Fn` and the variables it captured.
#[derive(Debug)]
pub struct Closure {
    node: NodeId,
    captures: Rc<[Cell]>,
}

/// A class, with the variables its members use from around it.
#[derive(Debug)]
pub struct Class {
    node: NodeId,
    layout: Rc<Layout>,
    captures: Rc<[Cell]>,
    /// Static fields, indexed like an object's fields.
    statics: RefCell<Vec<Val>>,
    /// Its supertypes that are classes, found when first needed, since
    /// they may be declared after it.
    supers: OnceCell<Rc<[Rc<Class>]>>,
    /// The names of its methods marked `@operator("==")` and
    /// `@operator("<=>")`, own or inherited, found when first needed.
    operators: [OnceCell<Option<NameId>>; 2],
}

/// An instance: its fields, indexed by their symbols' slots in its class's
/// scope.
#[derive(Debug)]
pub struct Object {
    class: Rc<Class>,
    fields: RefCell<Vec<Val>>,
}

/// A generator, a `Generator` or `SafeGenerator`, with its body's frame.
#[derive(Debug)]
pub struct Generator {
    /// Paused at its start, a `yield` or an `await`. None once it's done,
    /// and while it runs, so that a `next()` from within gets a
    /// `DoneResult`, as Kotlin Temper's does.
    frame: RefCell<Option<Frame>>,
    kind: GeneratorKind,
}

/// How running stopped early: a failure or panic, a jump out of a `for …
/// of` body's call to the frame around it, or a generator's body pausing,
/// at a `yield` or an `await`.
#[derive(Debug)]
enum Abrupt {
    Break(NodeId),
    Continue(NodeId),
    Return(NodeId, Val),
    /// A `yield`, with its value, for the generator's `next()`: only a
    /// generator's own frame yields.
    Yield(Val),
    /// An `await` of a promise that isn't resolved yet.
    Await(Rc<Promise>),
    /// A failure, as from `bubble()`, with a message for error nodes.
    Fail(Option<String>),
    /// Something `orelse` can't catch: a panic, or the interpreter's limits.
    Panic(String),
}

type R = Result<Val, Abrupt>;

/// What running a library printed, and how it failed, if it did.
#[derive(Debug, Default)]
pub struct Run {
    /// What it logged, up to [`OUTPUT_LIMIT`] bytes, then a note of how
    /// many more lines it logged; then, for tests, their reports. Empty
    /// when the output went to a sink ([`run_streaming`]).
    pub output: Vec<String>,
    pub failure: Option<String>,
    /// Each test that ran, by name, with the steps it took.
    pub test_steps: Vec<(String, u64)>,
}

/// Limits: calls in progress, and in lowering, expressions' nesting
/// ([`lower`]), which bound the stack used; and if asked, steps (ops
/// run), so that any input finishes. Only calls take native stack, as a
/// frame runs its expressions flat: about 3 KB each in a release build
/// and 10 to 16 KB in a debug one (2026-11-06), so 2000 take at most a
/// half of the 64 MiB that the CLI's and tests' threads have. Kotlin's
/// interpreter overflows the JVM's stack at fewer than 200, and Kelpto's
/// tests need about 450.
#[cfg(not(target_arch = "wasm32"))]
const MAX_DEPTH: u32 = 2000;
/// In wasm, the engine's own stack is what runs out, not the 16 MiB in
/// linear memory: V8's, under Node, at 1200 to 1400 calls (2026-11-06).
#[cfg(target_arch = "wasm32")]
const MAX_DEPTH: u32 = 500;
/// Steps handed out at a time where a run can be interrupted, between
/// checks of the interrupt: about a millisecond's.
const SLICE: u64 = 1 << 16;
/// How an interrupted run fails.
pub(crate) const INTERRUPTED: &str = "interrupted";
/// A step budget for callers that must finish quickly, such as the web
/// playground and tests' own runs of small programs; compile-time
/// evaluation will need one too. About a second in a release build.
pub const FUEL: u64 = 50_000_000;
/// `temper test`'s default budget for each test, and for the top-level
/// code before them (`--fuel` sets it). Wide on purpose: at least 10
/// times the real libraries' slowest test, so none comes near it, as it
/// only has to end true runaways (memory, the real hazard, is bounded by
/// [`OUTPUT_LIMIT`]). Their slowest (2026-11-09): Kelpto's pathological
/// GFM inputs, 626 million steps; Ordable's, 83 million; Hrefine's IDNA,
/// 80 million; Wherewith's very long strings, 51 million (over the
/// playground's [`FUEL`], which once failed it); Chromarite's, 4 million.
/// A runaway loop reaches it in about 3 to 4 minutes in a release build.
pub const TEST_FUEL: u64 = 10_000_000_000;
/// How much console output a run keeps, in bytes, when it isn't streamed:
/// past it, lines are only counted, so a runaway loop's logging can't
/// fill memory, nor a failing test's report the screen. The real
/// libraries' tests log at most 12 KB (2026-11-09).
pub const OUTPUT_LIMIT: usize = 64 << 10;

/// Runs a settled library's top-level code, stopping after `fuel`
/// evaluation steps if given. Without fuel, a program runs until it ends,
/// as any language's would.
pub fn run(settled: &Settled, fuel: Option<u64>) -> Run {
    run_with(settled, fuel, false, None)
}

/// Runs a library's top-level code as [`run`] does, giving each line it
/// logs to `out` as it's logged rather than keeping it.
pub fn run_streaming(settled: &Settled, fuel: Option<u64>, out: &mut dyn FnMut(&str)) -> Run {
    run_with(settled, fuel, false, Some(Box::new(out)))
}

/// Runs a library as [`run`] does, then each module's tests, if it has
/// any, adding their reports, JUnit XML from std's `runTestCases`, to the
/// output. With `fuel`, the top-level code and each test get that many
/// steps; a test that runs out, or ends abruptly otherwise, fails alone,
/// with a report of its own, and the others still run.
pub fn run_tests(settled: &Settled, fuel: Option<u64>) -> Run {
    run_with(settled, fuel, true, None)
}

fn run_with(settled: &Settled, fuel: Option<u64>, tests: bool, sink: Option<LineSink<'_>>) -> Run {
    // `u64::MAX` steps take centuries, so no fuel needs no check of its own.
    let budget = fuel.unwrap_or(u64::MAX);
    // Modules' declarations are globals, each module's in order.
    let mut first_globals = HashMap::new();
    let mut globals = 0;
    for module in &settled.modules {
        first_globals.insert(module.scope, globals);
        globals += settled.scopes.get(module.scope).names.len() as u32;
    }
    let lowering = Lowering::new(first_globals);
    let globals = vec![Val::Uninit; globals as usize];
    let mut interp = Interp {
        output: Output {
            lines: Vec::new(),
            bytes: 0,
            dropped: 0,
            note: 0,
            sink,
        },
        fuel: budget,
        reserve: 0,
        interrupt: None,
        depth: 0,
        globals,
        unset_why: HashMap::new(),
        lowering,
        ready: VecDeque::new(),
        cached: Vec::new(),
        compile_time: false,
        allowed_nodes: HashMap::new(),
        host: None,
        inputs_parked: Vec::new(),
        inputs_failed: Vec::new(),
    };
    let mut result = Ok(Val::Void);
    for module in &settled.modules {
        let code = interp
            .lowering
            .module(settled, module.scope, &module.top_levels);
        result = interp.run_top(settled, code);
        if result.is_err() {
            break;
        }
    }
    let runs = settled.modules.iter().filter_map(|m| {
        m.tests
            .filter(|_| tests && result.is_ok())
            .map(|t| (m.scope, t))
    });
    let mut test_steps = Vec::new();
    for (scope, test_run) in runs.collect::<Vec<_>>() {
        match interp.run_test_cases(settled, scope, test_run, budget) {
            Ok(cases) => {
                for case in cases {
                    test_steps.push((case.name, case.steps));
                    interp.output.lines.extend(case.report);
                }
            }
            Err(abrupt) => {
                result = Err(abrupt);
                break;
            }
        }
    }
    let failure = result.err().map(|abrupt| match describe(abrupt) {
        why if why == OUT_OF_STEPS => format!("{why} (the top-level code may take {budget})"),
        why => why,
    });
    Run {
        output: interp.output.finish(),
        failure,
        test_steps,
    }
}

/// A test that ran ([`Interp::run_test_cases`]).
struct TestCase {
    name: String,
    /// The steps it took.
    steps: u64,
    /// Its JUnit XML report, a tag a line.
    report: Vec<String>,
}

impl Interp<'_> {
    /// Runs a module's tests, `r#([t#test, …])` ([`crate::settle::SettledModule::tests`]),
    /// each alone, as `r#([t#test])`, with `budget` steps, then what it
    /// queued. A test that ends abruptly fails alone, with a report of
    /// its own; an interrupt stops them all, as does a runner that can't
    /// be made.
    fn run_test_cases(
        &mut self,
        settled: &Settled,
        scope: crate::scope::ScopeId,
        test_run: NodeId,
        budget: u64,
    ) -> Result<Vec<TestCase>, Abrupt> {
        let [runner, list] = *settled.library.tree.children(test_run) else {
            return Err(fail("Tests gave no report"));
        };
        let code = self.lowering.expression(settled, scope, runner);
        let runner = self.run_top(settled, code)?;
        let code = self.lowering.expression(settled, scope, list);
        let Val::List(ref cases) = self.run_top(settled, code)? else {
            return Err(fail("Tests gave no report"));
        };
        let mut ran = Vec::new();
        for case in cases.iter() {
            let name = match case {
                Val::Pair(pair) => to_string(&pair.0),
                _ => String::new(),
            };
            self.fuel = budget.min(SLICE);
            self.reserve = budget - self.fuel;
            self.depth = 0;
            let report = self.apply(
                settled,
                runner.clone(),
                vec![Val::List(Rc::from([case.clone()]))],
            );
            let tasks = self.run_tasks(settled);
            let steps = budget - (self.fuel + self.reserve);
            let report = match report.and_then(|report| tasks.map(|()| report)) {
                Ok(Val::Str(ref report)) => report.lines().map(String::from).collect(),
                Ok(_) => junit_failure(&name, "Tests gave no report"),
                Err(Abrupt::Panic(why)) if why == INTERRUPTED => {
                    return Err(Abrupt::Panic(why));
                }
                Err(abrupt) => {
                    let mut why = describe(abrupt);
                    if why == OUT_OF_STEPS {
                        why = format!("{why} (each test may take {budget})");
                    }
                    junit_failure(&name, &why)
                }
            };
            ran.push(TestCase {
                name,
                steps,
                report,
            });
        }
        Ok(ran)
    }
}

/// How a run that ended abruptly failed, for people.
fn describe(abrupt: Abrupt) -> String {
    match abrupt {
        Abrupt::Fail(None) => "Failed".to_string(),
        Abrupt::Fail(Some(message)) => format!("Failed: {message}"),
        Abrupt::Panic(message) => format!("Panicked: {message}"),
        Abrupt::Return(..)
        | Abrupt::Break(_)
        | Abrupt::Continue(_)
        | Abrupt::Yield(_)
        | Abrupt::Await(_) => "Jumped out of the module".to_string(),
    }
}

/// What a run that ran out of steps says, before any budget.
pub(crate) const OUT_OF_STEPS: &str = "Panicked: ran out of steps";

/// A JUnit report, as std's `reportTestResults` writes it, of one test
/// that failed without a report of its own.
fn junit_failure(name: &str, why: &str) -> Vec<String> {
    let escape = |text: &str| {
        text.replace('&', "&amp;")
            .replace('<', "&lt;")
            .replace('>', "&gt;")
            .replace('\'', "&#39;")
            .replace('"', "&#34;")
    };
    let name = escape(name);
    vec![
        "<testsuites>".into(),
        "  <testsuite name='suite' tests='1' failures='1' time='0.0'>".into(),
        format!("    <testcase name='{name}' classname='{name}' time='0.0'>"),
        format!("      <failure message='{}' />", escape(why)),
        "    </testcase>".into(),
        "  </testsuite>".into(),
        "</testsuites>".into(),
    ]
}

/// Where console output goes: to a sink, as it's logged, or kept, up to
/// [`OUTPUT_LIMIT`] bytes, then a note where it was cut, saying how many
/// lines were dropped. Tests' reports are kept apart from the limit.
struct Output<'a> {
    lines: Vec<String>,
    bytes: usize,
    /// Lines past the limit, only counted, and where the note goes.
    dropped: usize,
    note: usize,
    sink: Option<LineSink<'a>>,
}

/// Where logged lines go as they're logged.
type LineSink<'a> = Box<dyn FnMut(&str) + 'a>;

impl Output<'_> {
    fn log(&mut self, line: String) {
        if let Some(sink) = &mut self.sink {
            sink(&line);
        } else if self.dropped > 0 || self.bytes + line.len() > OUTPUT_LIMIT {
            if self.dropped == 0 {
                self.note = self.lines.len();
                self.lines.push(String::new());
            }
            self.dropped += 1;
        } else {
            self.bytes += line.len() + 1;
            self.lines.push(line);
        }
    }

    fn finish(mut self) -> Vec<String> {
        if self.dropped > 0 {
            self.lines[self.note] = format!(
                "[output truncated: {} more lines past {OUTPUT_LIMIT} bytes]",
                self.dropped
            );
        }
        self.lines
    }
}

struct Interp<'a> {
    output: Output<'a>,
    /// Steps left before [`Interp::refuel`] is asked for more.
    fuel: u64,
    /// Steps to hand out in slices once `fuel` runs out, so that an
    /// interrupt is noticed between slices; 0 where there's no interrupt,
    /// and `fuel` is all there is.
    reserve: u64,
    /// Set from elsewhere, as by Ctrl-C, to stop what's running at the
    /// next slice ([`repl`]).
    interrupt: Option<std::sync::Arc<std::sync::atomic::AtomicBool>>,
    /// Calls in progress.
    depth: u32,
    globals: Vec<Val>,
    /// Why some globals have no value, for a read of one: their REPL
    /// input failed before setting them ([`repl`]).
    unset_why: HashMap<u32, Rc<str>>,
    lowering: Lowering,
    /// `async` blocks and native jobs ready to run.
    ready: VecDeque<Task>,
    /// Constant regex literals' values, once made (`lower::Op::Cached`).
    cached: Vec<Option<Val>>,
    /// Running at compile time ([`compile_time`]), where nothing may
    /// reach outside the interpreter, and which functions and classes,
    /// by their nodes, it may then run.
    compile_time: bool,
    allowed_nodes: HashMap<NodeId, bool>,
    /// Implements bodiless `@connected` functions declared outside std,
    /// as the REPL's own `help`, `describe`, and `translate` ([`repl::Host`]).
    host: Option<repl::Host>,
    /// `async` blocks that are REPL inputs paused at a top-level `await`,
    /// each with its input's number, and how those that failed once
    /// resumed failed, by number: their own failures, not the failure of
    /// whatever ran the queue ([`repl`]).
    inputs_parked: Vec<(Rc<Generator>, usize)>,
    inputs_failed: Vec<(usize, Abrupt)>,
}

fn fail(message: impl Into<String>) -> Abrupt {
    Abrupt::Fail(Some(message.into()))
}

impl Interp<'_> {
    /// Runs top-level code, then the `async` blocks it started, even if it
    /// failed, as Kotlin Temper does. The first failure is the result.
    fn run_top(&mut self, s: &Settled, code: Rc<Code>) -> R {
        let result = self.run_code(s, code, Rc::from([]), Vec::new());
        let tasks = self.run_tasks(s);
        result.and_then(|value| tasks.map(|()| value))
    }

    /// Runs code in a new frame, as a call does.
    fn run_code(&mut self, s: &Settled, code: Rc<Code>, captures: Rc<[Cell]>, args: Vec<Val>) -> R {
        let mut frame = Frame::new(code, captures, args);
        self.run_frame(s, &mut frame)
    }

    /// Calls a function's code with what it captured.
    fn call(&mut self, s: &Settled, f: NodeId, captures: Rc<[Cell]>, args: Vec<Val>) -> R {
        if self.compile_time && !self.module_allowed(s, f) {
            return Err(compile_time::refused("a function of a std module"));
        }
        if self.depth >= MAX_DEPTH {
            return Err(Abrupt::Panic("calls nested too deeply".into()));
        }
        let code = self.lowering.function(s, f).map_err(fail)?;
        if args.len() > code.params as usize {
            return Err(fail("Too many arguments"));
        }
        self.depth += 1;
        let result = match code.generator {
            None => self.run_code(s, code, captures, args),
            Some(kind) => self.start_generator(s, Frame::new(code, captures, args), kind),
        };
        self.depth -= 1;
        result
    }

    /// Makes a generator, running its body's frame only through its
    /// parameters, to the pause before the body.
    fn start_generator(&mut self, s: &Settled, mut frame: Frame, kind: GeneratorKind) -> R {
        match self.run_frame(s, &mut frame) {
            Err(Abrupt::Yield(_)) => Ok(Val::Generator(Rc::new(Generator {
                frame: RefCell::new(Some(frame)),
                kind,
            }))),
            other => other,
        }
    }

    /// Runs a generator's body on from where it paused, until it pauses
    /// again, at a `yield` or an `await`, keeping its frame, or ends, as
    /// it already has if it's done. A failure in the body ends it too.
    fn step(&mut self, s: &Settled, generator: &Generator) -> R {
        if self.depth >= MAX_DEPTH {
            return Err(Abrupt::Panic("calls nested too deeply".into()));
        }
        let Some(mut frame) = generator.frame.borrow_mut().take() else {
            return Ok(Val::Void);
        };
        self.depth += 1;
        let result = self.run_frame(s, &mut frame);
        self.depth -= 1;
        if let Err(Abrupt::Yield(_) | Abrupt::Await(_)) = result {
            *generator.frame.borrow_mut() = Some(frame);
        }
        result
    }

    /// `next()`, or `nextSafe()`: runs a generator's body on to its next
    /// `yield`, giving a `ValueResult`, or to its end, giving a
    /// `DoneResult`, as do later calls. A failure in the body comes out of
    /// `next()`. Only the run queue resumes a body paused at an `await`,
    /// so one that pauses there under `next()` panics.
    fn resume(&mut self, s: &Settled, generator: &Generator) -> R {
        match self.step(s, generator) {
            Err(Abrupt::Yield(value)) => Ok(Val::ValueResult(Rc::new(value))),
            Err(Abrupt::Await(_)) => {
                generator.frame.borrow_mut().take();
                Err(Abrupt::Panic(
                    "await of an unresolved promise outside an async block".into(),
                ))
            }
            Ok(_) => Ok(Val::DoneResult),
            Err(abrupt) => Err(abrupt),
        }
    }

    /// A generator's methods. `close()` ends it, as core.temper documents,
    /// where Kotlin Temper's is still a TODO that does nothing.
    fn generator_method(
        &mut self,
        s: &Settled,
        generator: Rc<Generator>,
        name: &str,
        args: Vec<Val>,
    ) -> R {
        match (name, args.as_slice()) {
            ("next", []) => self.resume(s, &generator),
            ("nextSafe", []) if generator.kind == GeneratorKind::Safe => self.resume(s, &generator),
            ("close", []) => {
                generator.frame.borrow_mut().take();
                Ok(Val::Void)
            }
            _ => no_method(&Val::Generator(generator), name),
        }
    }

    fn apply(&mut self, s: &Settled, function: Val, args: Vec<Val>) -> R {
        if self.compile_time {
            match &function {
                Val::Builtin(f) if !compile_time::builtin_allowed(*f) => {
                    return Err(compile_time::refused(Builtin::Function(*f).name()));
                }
                Val::Connected(decl) => {
                    let name = core::let_parts(&s.library.tree, *decl).0;
                    let name = s.library.name(name).map_or("?", |n| s.library.names.get(n));
                    return Err(compile_time::refused(name));
                }
                _ => {}
            }
        }
        match function {
            Val::Fn(ref closure) => self.call(s, closure.node, closure.captures.clone(), args),
            Val::Builtin(Function::New) => match args.split_first() {
                Some((Val::Class(class), rest)) => self.construct(s, class.clone(), rest.to_vec()),
                Some((Val::BuiltinType(t), [])) if TYPES[*t as usize] == "ListBuilder" => {
                    Ok(Val::ListBuilder(Rc::new(RefCell::new(Vec::new()))))
                }
                Some((Val::BuiltinType(t), [])) if TYPES[*t as usize] == "StringBuilder" => {
                    Ok(Val::StringBuilder(Rc::new(RefCell::new(String::new()))))
                }
                Some((Val::BuiltinType(t), [])) if TYPES[*t as usize] == "Deque" => {
                    Ok(Val::Deque(Rc::new(RefCell::new(VecDeque::new()))))
                }
                Some((Val::BuiltinType(t), [Val::Int(capacity)]))
                    if TYPES[*t as usize] == "DenseBitVector" =>
                {
                    Ok(Val::Bits(Rc::new(RefCell::new(vec![
                        false;
                        (*capacity).max(0)
                            as usize
                    ]))))
                }
                Some((Val::BuiltinType(t), [Val::List(pairs)])) if TYPES[*t as usize] == "Map" => {
                    Ok(Val::Map(Rc::new(maps::Entries::from_pairs(pairs)?)))
                }
                Some((Val::BuiltinType(t), [])) if TYPES[*t as usize] == "MapBuilder" => {
                    Ok(Val::MapBuilder(Rc::default()))
                }
                Some((Val::BuiltinType(t), [k, v])) if TYPES[*t as usize] == "Pair" => {
                    Ok(Val::Pair(Rc::new((k.clone(), v.clone()))))
                }
                Some((Val::BuiltinType(t), [value])) if TYPES[*t as usize] == "ValueResult" => {
                    Ok(Val::ValueResult(Rc::new(value.clone())))
                }
                Some((Val::BuiltinType(t), [])) if TYPES[*t as usize] == "DoneResult" => {
                    Ok(Val::DoneResult)
                }
                Some((Val::BuiltinType(t), [])) if TYPES[*t as usize] == "PromiseBuilder" => {
                    Ok(Val::PromiseBuilder(Rc::default()))
                }
                _ => Err(fail("new needs a class")),
            },
            Val::Builtin(f @ (Function::As | Function::Is)) => {
                let [value, ty] =
                    <[Val; 2]>::try_from(args).map_err(|_| fail("Expected a value and a type"))?;
                let (ty, or_null) = match ty {
                    Val::NullableType(ref inner) => ((**inner).clone(), true),
                    ty => (ty, false),
                };
                let is = match (&value, &ty) {
                    (Val::Null, _) if or_null => true,
                    (Val::Object(object), Val::Class(class)) => {
                        self.extends(s, &object.class, class)?
                    }
                    (Val::NetResponse(_), Val::Class(class)) => {
                        class_name(s, class.node) == "NetResponse" && in_std(s, class.node, "net")
                    }
                    (_, Val::Class(_)) => false,
                    (_, Val::BuiltinType(t)) => is_builtin_type(&value, TYPES[*t as usize]),
                    _ => true,
                };
                match f {
                    Function::Is => Ok(Val::Bool(is)),
                    _ if is => Ok(value),
                    _ => Err(Abrupt::Fail(None)),
                }
            }
            // Interpolated objects are their `toString()`.
            Val::Builtin(Function::Cat) if args.iter().any(|a| matches!(a, Val::Object(_))) => {
                let to_string = s
                    .library
                    .names
                    .find("toString")
                    .ok_or_else(|| fail("No method toString"))?;
                let mut parts = Vec::with_capacity(args.len());
                for arg in args {
                    parts.push(match arg {
                        Val::Object(_) => {
                            self.apply(s, Val::Method(Rc::new((arg, to_string))), Vec::new())?
                        }
                        other => other,
                    });
                }
                self.builtin(Function::Cat, parts)
            }
            // `a == b` and `a <=> b`, and so `a < b` and the like, call
            // `a`'s method marked `@operator`, if it has one; `a == null`
            // is a null check.
            Val::Builtin(
                f @ (Function::Eq
                | Function::Ne
                | Function::Cmp
                | Function::Lt
                | Function::Le
                | Function::Gt
                | Function::Ge),
            ) if matches!(*args, [Val::Object(_), ref b] if !matches!(b, Val::Null)) => {
                self.class_operator(s, f, args)
            }
            Val::Builtin(Function::DoPure) => match <[Val; 1]>::try_from(args) {
                Ok([f]) => self.apply(s, f, Vec::new()),
                Err(_) => Err(fail("doPure takes a function")),
            },
            Val::Builtin(Function::Async) => self.start_async(s, args),
            Val::Connected(decl) => self.connected_function(s, decl, args),
            Val::Callout(ref callouts) => compile_time::call_callout(callouts, args),
            // `m[i, j]` on an object calls its `get(i, j)`.
            Val::Builtin(Function::Index) if matches!(args.first(), Some(Val::Object(_))) => {
                let mut args = args;
                let this = args.remove(0);
                match s.library.names.find("get") {
                    Some(get) => self.call_method(s, &this, get, args, None),
                    None => no_method(&this, "get"),
                }
            }
            Val::Builtin(f) => self.builtin(f, args),
            Val::Method(ref method) => match &method.0 {
                this @ (Val::Object(_) | Val::Class(_)) => {
                    self.call_method(s, this, method.1, args, None)
                }
                list @ (Val::List(_) | Val::ListBuilder(_)) => {
                    self.list_method(s, list, s.library.names.get(method.1), args)
                }
                map @ (Val::Map(_) | Val::MapBuilder(_)) => {
                    self.map_method(s, map, s.library.names.get(method.1), args)
                }
                Val::Generator(generator) => {
                    self.generator_method(s, generator.clone(), s.library.names.get(method.1), args)
                }
                Val::Str(text) if s.library.names.get(method.1) == "forEach" => {
                    // As `core.temper`'s `String.forEach`.
                    let text = text.clone();
                    let [f] =
                        <[Val; 1]>::try_from(args).map_err(|_| fail("forEach takes a function"))?;
                    for c in text.chars() {
                        self.apply(s, f.clone(), vec![Val::Int(c as i32)])?;
                    }
                    Ok(Val::Void)
                }
                object => self.method(object, s.library.names.get(method.1), args),
            },
            other => Err(fail(format!("{} is not a function", type_name(&other)))),
        }
    }

    /// Calls an object's method, or a class's static one. `from`: the
    /// classes the call is inside, whose private members it may use, or
    /// none to allow any, for a method already got.
    fn call_method(
        &mut self,
        s: &Settled,
        this: &Val,
        name: NameId,
        args: Vec<Val>,
        from: Option<&[NodeId]>,
    ) -> R {
        let class = class_of(this);
        let wanted = |data| data & ROLE_MASK == ROLE_METHOD && member_of(this, data);
        let Some((class, decl, _)) = self.find_member(s, &class, name, &wanted)? else {
            let value = self.get_property(s, this.clone(), name, from)?;
            return self.apply(s, value, args);
        };
        check_access(s, from, &class, decl, name)?;
        self.allowed_now(s, &class, decl, name)?;
        match core::member_fn(&s.library.tree, decl) {
            Some(f) => {
                let this = receiver(this, s.library.tree.data(decl), &class);
                self.invoke(s, this, &class, f, args)
            }
            // A bodiless method of a `@connected` class, or a core-style
            // `@connected` member: the interpreter's own implementation.
            None if s.library.tree.data(decl) & MEMBER_CONNECTED != 0
                || s.library.tree.data(class.node) & CLASS_CONNECTED != 0 =>
            {
                self.connected(s, &class, name, args)
            }
            None => Err(fail("Abstract method")),
        }
    }

    fn builtin(&mut self, f: Function, args: Vec<Val>) -> R {
        use Function::*;
        match (f, args.as_slice()) {
            (Angle, [generic, ..]) | (Throws, [generic, ..]) => Ok(generic.clone()),
            (Nullable, [inner]) => Ok(Val::NullableType(Rc::new(inner.clone()))),
            (FnType, _) => Ok(Val::Type),
            (List, items) => Ok(Val::List(items.into())),
            (Index, [Val::List(items), Val::Int(i)]) => list_get(items, *i),
            (Index, [Val::ListBuilder(items), Val::Int(i)]) => list_get(&items.borrow(), *i),
            (Index, [Val::Str(text), Val::StringIndex(i)]) => strings::get(text, *i),
            (Index, [bits @ Val::Bits(_), i @ Val::Int(_)]) => {
                self.method(bits, "get", vec![i.clone()])
            }
            (Index, [map @ (Val::Map(_) | Val::MapBuilder(_)), key]) => maps::get(map, key),
            (Char, parts) => strings::char_tag(parts),
            (Raw, [Val::List(parts), Val::List(values)]) => {
                let mut text = String::new();
                for (i, part) in parts.iter().enumerate() {
                    text.push_str(&to_string(part));
                    if let Some(value) = values.get(i) {
                        text.push_str(&to_string(value));
                    }
                }
                Ok(Val::Str(text.into()))
            }
            (Ignore, [_]) => Ok(Val::Void),
            (Empty, []) => Ok(Val::Empty),
            (DoneResult, []) => Ok(Val::DoneResult),
            (Cmp, [a, b]) => {
                let ordering = compare(a, b).ok_or_else(|| fail("Can't compare these"))?;
                Ok(Val::Int(ordering as i32))
            }
            (Cat, parts) => {
                let mut text = String::new();
                for part in parts {
                    text.push_str(&to_string(part));
                }
                Ok(Val::Str(text.into()))
            }
            (Not, [Val::Bool(b)]) => Ok(Val::Bool(!b)),
            (Sub, [Val::Int(n)]) => Ok(Val::Int(n.wrapping_neg())),
            (Sub, [Val::Int64(n)]) => Ok(Val::Int64(n.wrapping_neg())),
            (Sub, [Val::Float(n)]) => Ok(Val::Float(-n)),
            (Add, [Val::Int(n)]) => Ok(Val::Int(*n)),
            (BitNot, [Val::Int(n)]) => Ok(Val::Int(!n)),
            (BitNot, [Val::Int64(n)]) => Ok(Val::Int64(!n)),
            // Shift distances wrap at the width, as in Java; an `Int64`'s is an `Int`.
            (Shl | Shr | UShr, [Val::Int(a), Val::Int(b)]) => Ok(Val::Int(match f {
                Shl => a.wrapping_shl(*b as u32),
                Shr => a.wrapping_shr(*b as u32),
                _ => (*a as u32).wrapping_shr(*b as u32) as i32,
            })),
            (Shl | Shr | UShr, [Val::Int64(a), Val::Int(b)]) => Ok(Val::Int64(match f {
                Shl => a.wrapping_shl(*b as u32),
                Shr => a.wrapping_shr(*b as u32),
                _ => (*a as u64).wrapping_shr(*b as u32) as i64,
            })),
            (Eq, [a, b]) => Ok(Val::Bool(equal(a, b))),
            (Ne, [a, b]) => Ok(Val::Bool(!equal(a, b))),
            (Lt | Le | Gt | Ge, [a, b]) => {
                let ordering = compare(a, b).ok_or_else(|| fail("Can't compare these"))?;
                Ok(Val::Bool(match f {
                    Lt => ordering.is_lt(),
                    Le => ordering.is_le(),
                    Gt => ordering.is_gt(),
                    _ => ordering.is_ge(),
                }))
            }
            // `i32::MIN / -1` overflows `i32` but not `i64`, and wraps back.
            (_, [Val::Int(a), Val::Int(b)]) => {
                int_op(f, *a as i64, *b as i64).map(|n| Val::Int(n as i32))
            }
            (_, [Val::Int64(a), Val::Int64(b)]) => int_op(f, *a, *b).map(Val::Int64),
            // Like integers', and unlike IEEE 754's, float division fails
            // for a zero divisor.
            (Div | Mod, [Val::Float(_), Val::Float(b)]) if *b == 0.0 => Err(Abrupt::Fail(None)),
            (Pow, [Val::Float(a), Val::Float(b)]) => Ok(Val::Float(a.powf(*b))),
            (Add | Sub | Mul | Div | Mod, [Val::Float(a), Val::Float(b)]) => {
                Ok(Val::Float(match f {
                    Add => a + b,
                    Sub => a - b,
                    Mul => a * b,
                    Div => a / b,
                    _ => a % b,
                }))
            }
            (Panic, _) => Err(Abrupt::Panic("panic()".into())),
            _ => Err(fail(format!(
                "Can't apply {} to these arguments",
                Builtin::Function(f).name()
            ))),
        }
    }

    fn method(&mut self, object: &Val, name: &str, args: Vec<Val>) -> R {
        match (object, name, args.as_slice()) {
            (Val::Console, "log", [message]) => {
                self.output.log(to_string(message));
                Ok(Val::Void)
            }
            (Val::StringBuilder(builder), _, _) => strings::builder_method(builder, name, &args)
                .unwrap_or_else(|| no_method(object, name)),
            (Val::Deque(deque), "add", [value]) => {
                deque.borrow_mut().push_back(value.clone());
                Ok(Val::Void)
            }
            (Val::Deque(deque), "removeFirst", []) => deque
                .borrow_mut()
                .pop_front()
                .ok_or_else(|| Abrupt::Panic("removeFirst on an empty Deque".into())),
            // As `core.temper`'s `DenseBitVector`, which grows when a bit
            // past the end is set.
            (Val::Bits(bits), "get", [Val::Int(i)]) => Ok(Val::Bool(
                usize::try_from(*i)
                    .ok()
                    .and_then(|i| bits.borrow().get(i).copied())
                    .unwrap_or(false),
            )),
            (Val::Bits(bits), "set", [Val::Int(i), Val::Bool(value)]) => {
                let mut bits = bits.borrow_mut();
                let Ok(i) = usize::try_from(*i) else {
                    return Err(Abrupt::Panic(format!(
                        "index {i} out of bounds for length {}",
                        bits.len()
                    )));
                };
                if i >= bits.len() && *value {
                    bits.resize(i + 1, false);
                }
                if let Some(bit) = bits.get_mut(i) {
                    *bit = *value;
                }
                Ok(Val::Void)
            }
            (_, "toString", []) => Ok(Val::Str(to_string(object).into())),
            (Val::Int(n), "toString", [radix]) => {
                strings::int_to_string(*n as i64, radix_or_10(radix)?)
            }
            (Val::Int64(n), "toString", [radix]) => strings::int_to_string(*n, radix_or_10(radix)?),
            (Val::StringIndex(_) | Val::NoStringIndex, "compareTo", [other]) => {
                let ordering = compare(object, other).ok_or_else(|| fail("Can't compare these"))?;
                Ok(Val::Int(ordering as i32))
            }
            (Val::BuiltinType(t), "of", items) if TYPES[*t as usize] == "List" => {
                Ok(Val::List(items.into()))
            }
            (Val::BuiltinType(t), _, _) if TYPES[*t as usize] == "String" => {
                strings::static_method(name, &args).unwrap_or_else(|| no_method(object, name))
            }
            (Val::Int(a), "min", [Val::Int(b)]) => Ok(Val::Int(*a.min(b))),
            (Val::Int(a), "max", [Val::Int(b)]) => Ok(Val::Int(*a.max(b))),
            (Val::Int64(a), "min", [Val::Int64(b)]) => Ok(Val::Int64(*a.min(b))),
            (Val::Int64(a), "max", [Val::Int64(b)]) => Ok(Val::Int64(*a.max(b))),
            (Val::PromiseBuilder(promise), _, _) => self
                .promise_builder_method(promise, name, &args)
                .unwrap_or_else(|| no_method(object, name)),
            (Val::Float(f), _, _) => {
                numbers::float_method(*f, name, &args).unwrap_or_else(|| no_method(object, name))
            }
            (Val::Int(n), _, _) => numbers::int_method(*n as i64, false, name, &args)
                .unwrap_or_else(|| no_method(object, name)),
            (Val::Int64(n), _, _) => numbers::int_method(*n, true, name, &args)
                .unwrap_or_else(|| no_method(object, name)),
            (Val::Str(text), _, _) => {
                strings::string_method(text, name, &args).unwrap_or_else(|| no_method(object, name))
            }
            _ => no_method(object, name),
        }
    }
}

impl Interp<'_> {
    /// An operator whose first operand is an object: a call of its method
    /// marked `@operator`, `==`'s or `<=>`'s, if it has one, as Kotlin
    /// Temper's typer picks it (the `eq` that `@auto("==")` makes among
    /// them); else identity for `==`.
    fn class_operator(&mut self, s: &Settled, f: Function, args: Vec<Val>) -> R {
        use Function::*;
        let [this, other] = <[Val; 2]>::try_from(args).map_err(|_| fail("Wrong arguments"))?;
        let flag = match f {
            Eq | Ne => MEMBER_OPERATOR_EQ,
            _ => MEMBER_OPERATOR_CMP,
        };
        let method = match &this {
            Val::Object(object) => self.operator_method(s, &object.class, flag)?,
            _ => None,
        };
        let Some(method) = method else {
            return match f {
                // Identity, with no `==` to call.
                Eq | Ne => Ok(Val::Bool(equal(&this, &other) == (f == Eq))),
                _ => self.builtin(f, vec![this, other]),
            };
        };
        let result = self.call_method(s, &this, method, vec![other], None)?;
        Ok(match (f, result) {
            (Eq | Cmp, result) => result,
            (Ne, Val::Bool(b)) => Val::Bool(!b),
            (_, Val::Int(n)) => Val::Bool(match f {
                Lt => n < 0,
                Le => n <= 0,
                Gt => n > 0,
                _ => n >= 0,
            }),
            _ => return Err(fail("Wrong operator result")),
        })
    }

    /// The name of the method marked `flag` ([`MEMBER_OPERATOR_EQ`] or
    /// [`MEMBER_OPERATOR_CMP`]) in a class or its supertypes, nearest
    /// first, if any. Calling it by name calls an override, too.
    fn operator_method(
        &mut self,
        s: &Settled,
        class: &Rc<Class>,
        flag: u32,
    ) -> Result<Option<NameId>, Abrupt> {
        let cache = &class.operators[usize::from(flag == MEMBER_OPERATOR_CMP)];
        if let Some(&name) = cache.get() {
            return Ok(name);
        }
        let name = self.find_operator_method(s, class, flag)?;
        Ok(*cache.get_or_init(|| name))
    }

    fn find_operator_method(
        &mut self,
        s: &Settled,
        class: &Rc<Class>,
        flag: u32,
    ) -> Result<Option<NameId>, Abrupt> {
        let tree = &s.library.tree;
        let mut queue = std::collections::VecDeque::from([class.clone()]);
        let mut seen = vec![class.node];
        while let Some(class) = queue.pop_front() {
            let members = core::class_parts(tree, class.node).2;
            let marked = members.iter().find(|&&m| {
                tree.data(m) & (flag | MEMBER_STATIC) == flag
                    && tree.data(m) & ROLE_MASK == ROLE_METHOD
            });
            if let Some(&m) = marked {
                return Ok(s.library.name(core::let_parts(tree, m).0));
            }
            for sup in self.supers(s, &class)?.iter() {
                if !seen.contains(&sup.node) {
                    seen.push(sup.node);
                    queue.push_back(sup.clone());
                }
            }
        }
        Ok(None)
    }
}

/// Whether a value has a builtin type, for `is` and `as`. Others, like
/// `Mapped`, aren't checked yet.
fn is_builtin_type(value: &Val, ty: &str) -> bool {
    match ty {
        "Int" | "Int32" => matches!(value, Val::Int(_)),
        "Int64" => matches!(value, Val::Int64(_)),
        "Float64" => matches!(value, Val::Float(_)),
        "String" => matches!(value, Val::Str(_)),
        "Boolean" => matches!(value, Val::Bool(_)),
        "List" => matches!(value, Val::List(_)),
        "ListBuilder" => matches!(value, Val::ListBuilder(_)),
        "Listed" => matches!(value, Val::List(_) | Val::ListBuilder(_)),
        "StringIndex" => matches!(value, Val::StringIndex(_)),
        "NoStringIndex" => matches!(value, Val::NoStringIndex),
        "StringIndexOption" => matches!(value, Val::StringIndex(_) | Val::NoStringIndex),
        "StringBuilder" => matches!(value, Val::StringBuilder(_)),
        "Deque" => matches!(value, Val::Deque(_)),
        "Empty" => matches!(value, Val::Empty),
        "DenseBitVector" => matches!(value, Val::Bits(_)),
        "Map" => matches!(value, Val::Map(_)),
        "MapBuilder" => matches!(value, Val::MapBuilder(_)),
        "Mapped" => matches!(value, Val::Map(_) | Val::MapBuilder(_)),
        "MapKey" => matches!(value, Val::Int(_) | Val::Str(_)),
        "Pair" => matches!(value, Val::Pair(_)),
        "Generator" => matches!(value, Val::Generator(_)),
        "SafeGenerator" => matches!(value, Val::Generator(g) if g.kind == GeneratorKind::Safe),
        "GeneratorResult" => matches!(value, Val::ValueResult(_) | Val::DoneResult),
        "ValueResult" => matches!(value, Val::ValueResult(_)),
        "DoneResult" => matches!(value, Val::DoneResult),
        "Promise" => matches!(value, Val::Promise(_)),
        "PromiseBuilder" => matches!(value, Val::PromiseBuilder(_)),
        "AnyValue" => !matches!(value, Val::Null),
        "Void" => matches!(value, Val::Void),
        "Never" => false,
        _ => !matches!(value, Val::Null),
    }
}

/// A radix argument, 10 if null.
fn radix_or_10(radix: &Val) -> Result<i32, Abrupt> {
    match radix {
        Val::Null => Ok(10),
        Val::Int(radix) => Ok(*radix),
        _ => Err(fail("The radix isn't an Int")),
    }
}

fn no_method(object: &Val, name: &str) -> R {
    Err(fail(format!("No method {name} for {}", type_name(object))))
}

impl Interp<'_> {
    /// `List` and `ListBuilder` methods, which may call back. Callbacks
    /// get a snapshot of a builder's items, so they may change it.
    fn list_method(&mut self, s: &Settled, this: &Val, name: &str, args: Vec<Val>) -> R {
        if let Val::ListBuilder(builder) = this {
            if BUILDER_METHODS.contains(&name) {
                return self.builder_method(s, builder, name, &args);
            }
        }
        let mut args = args.into_iter();
        let mut arg = || {
            args.next()
                .ok_or_else(|| fail(format!("Too few arguments to {name}")))
        };
        // Reads that don't call back borrow the items.
        match name {
            "get" => {
                return match arg()? {
                    Val::Int(i) => with_items(this, |items| list_get(items, i)),
                    _ => Err(fail("List index isn't an Int")),
                };
            }
            "getOr" => {
                return match (arg()?, arg()?) {
                    (Val::Int(i), fallback) => Ok(with_items(this, |items| {
                        usize::try_from(i)
                            .ok()
                            .and_then(|i| items.get(i).cloned())
                            .unwrap_or(fallback)
                    })),
                    _ => Err(fail("List index isn't an Int")),
                };
            }
            "slice" => {
                return match (arg()?, arg()?) {
                    (Val::Int(begin), Val::Int(end)) => Ok(Val::List(with_items(this, |items| {
                        let clamp = |i: i32| (i.max(0) as usize).min(items.len());
                        let (begin, end) = (clamp(begin), clamp(end));
                        items[begin..end.max(begin)].into()
                    }))),
                    _ => Err(fail("List indices aren't Ints")),
                };
            }
            "toList" => return Ok(Val::List(snapshot(this))),
            "toListBuilder" => {
                return Ok(Val::ListBuilder(Rc::new(RefCell::new(with_items(
                    this,
                    <[Val]>::to_vec,
                )))));
            }
            _ => {}
        }
        let items = snapshot(this);
        match name {
            "map" | "filter" => {
                let f = arg()?;
                let mut out = Vec::with_capacity(items.len());
                for item in items.iter() {
                    let result = self.apply(s, f.clone(), vec![item.clone()])?;
                    match (name, result) {
                        ("map", result) => out.push(result),
                        (_, Val::Bool(true)) => out.push(item.clone()),
                        (_, Val::Bool(false)) => {}
                        _ => return Err(fail("filter's predicate didn't return a Boolean")),
                    }
                }
                Ok(Val::List(out.into()))
            }
            // Only `List` has `forEach` so far, as in Kotlin Temper's core.
            "forEach" if matches!(this, Val::List(_)) => {
                let f = arg()?;
                for item in items.iter() {
                    self.apply(s, f.clone(), vec![item.clone()])?;
                }
                Ok(Val::Void)
            }
            "reduce" | "reduceFrom" => {
                let (mut result, start) = match name {
                    "reduce" => (list_get(&items, 0)?, 1),
                    _ => (arg()?, 0),
                };
                let f = arg()?;
                for item in &items[start..] {
                    result = self.apply(s, f.clone(), vec![result, item.clone()])?;
                }
                Ok(result)
            }
            "join" => {
                let (separator, f) = (arg()?, arg()?);
                let Val::Str(ref separator) = separator else {
                    return Err(fail("join's separator isn't a String"));
                };
                let mut text = String::new();
                for (i, item) in items.iter().enumerate() {
                    if i > 0 {
                        text.push_str(separator);
                    }
                    match self.apply(s, f.clone(), vec![item.clone()])? {
                        Val::Str(ref part) => text.push_str(part),
                        _ => return Err(fail("join's stringify didn't return a String")),
                    }
                }
                Ok(Val::Str(text.into()))
            }
            "sorted" => {
                let f = arg()?;
                Ok(Val::List(self.sort(s, items.to_vec(), &f)?.into()))
            }
            _ => self.method(this, name, args.collect()),
        }
    }

    /// One of [`BUILDER_METHODS`]. A missing or null argument takes its
    /// default.
    fn builder_method(
        &mut self,
        s: &Settled,
        builder: &Rc<RefCell<Vec<Val>>>,
        name: &str,
        args: &[Val],
    ) -> R {
        let len = builder.borrow().len();
        let int_or = |i: usize, default: usize| match args.get(i) {
            None | Some(Val::Null) => Ok(default as i64),
            Some(Val::Int(n)) => Ok(*n as i64),
            Some(_) => Err(fail(format!("{name}'s index isn't an Int"))),
        };
        let at = |i: usize| {
            let at = int_or(i, len)?;
            usize::try_from(at)
                .ok()
                .filter(|&at| at <= len)
                .ok_or_else(|| Abrupt::Panic(format!("index {at} out of bounds for length {len}")))
        };
        match (name, args) {
            ("add", [value, ..]) => {
                let at = at(1)?;
                builder.borrow_mut().insert(at, value.clone());
                Ok(Val::Void)
            }
            ("addAll", [values, ..]) => {
                let (at, values) = (at(1)?, snapshot(values));
                builder.borrow_mut().splice(at..at, values.iter().cloned());
                Ok(Val::Void)
            }
            ("set", [Val::Int(i), value]) => {
                match usize::try_from(*i).ok().and_then(|i| {
                    builder
                        .borrow_mut()
                        .get_mut(i)
                        .map(|slot| *slot = value.clone())
                }) {
                    Some(()) => Ok(Val::Void),
                    None => Err(Abrupt::Panic(format!(
                        "index {i} out of bounds for length {len}"
                    ))),
                }
            }
            ("removeLast", []) => builder
                .borrow_mut()
                .pop()
                .ok_or_else(|| Abrupt::Panic("removeLast on an empty ListBuilder".into())),
            ("reverse", []) => {
                builder.borrow_mut().reverse();
                Ok(Val::Void)
            }
            ("clear", []) => {
                builder.borrow_mut().clear();
                Ok(Val::Void)
            }
            ("sort", [f]) => {
                let items = builder.borrow().clone();
                *builder.borrow_mut() = self.sort(s, items, f)?;
                Ok(Val::Void)
            }
            ("splice", _) => {
                // The index and count are clamped.
                let index = int_or(0, 0)?.clamp(0, len as i64) as usize;
                let count = int_or(1, len)?.clamp(0, (len - index) as i64) as usize;
                let values = match args.get(2) {
                    None | Some(Val::Null) => Rc::from([]),
                    Some(values) => snapshot(values),
                };
                let removed: Vec<Val> = builder
                    .borrow_mut()
                    .splice(index..index + count, values.iter().cloned())
                    .collect();
                Ok(Val::List(removed.into()))
            }
            _ => Err(fail(format!("Wrong arguments to {name}"))),
        }
    }

    /// A stable merge sort by a Temper comparison function, which may fail
    /// or disagree with itself without upsetting the sort.
    fn sort(
        &mut self,
        s: &Settled,
        mut items: Vec<Val>,
        compare: &Val,
    ) -> Result<Vec<Val>, Abrupt> {
        let mut width = 1;
        let mut merged = Vec::with_capacity(items.len());
        while width < items.len() {
            for start in (0..items.len()).step_by(2 * width) {
                let middle = (start + width).min(items.len());
                let end = (start + 2 * width).min(items.len());
                let (mut i, mut j) = (start, middle);
                while i < middle && j < end {
                    // Take from the right only when it's strictly less.
                    match self.apply(
                        s,
                        compare.clone(),
                        vec![items[j].clone(), items[i].clone()],
                    )? {
                        Val::Int(n) if n < 0 => {
                            merged.push(items[j].clone());
                            j += 1;
                        }
                        Val::Int(_) => {
                            merged.push(items[i].clone());
                            i += 1;
                        }
                        _ => return Err(fail("The comparison didn't return an Int")),
                    }
                }
                merged.extend_from_slice(&items[i..middle]);
                merged.extend_from_slice(&items[j..end]);
            }
            std::mem::swap(&mut items, &mut merged);
            merged.clear();
            width *= 2;
        }
        Ok(items)
    }

    /// `new C(args)`: the header's fields from the arguments or their
    /// defaults, then other fields' initializers in order, then the
    /// constructor, if any.
    fn construct(&mut self, s: &Settled, class: Rc<Class>, args: Vec<Val>) -> R {
        let tree = &s.library.tree;
        let connected = tree.data(class.node) & CLASS_CONNECTED != 0;
        if self.compile_time
            && !(self.module_allowed(s, class.node)
                && (!connected || compile_time::connected_allowed(s, class.node, None)))
        {
            return Err(compile_time::refused(class_name(s, class.node)));
        }
        if tree.data(class.node) & CLASS_INTERFACE != 0 {
            return Err(fail(format!(
                "Cannot instantiate abstract type {}",
                class_name(s, class.node)
            )));
        }
        let scope = s
            .scopes
            .of_node(class.node)
            .ok_or_else(|| fail("Class not settled"))?;
        let size = s.scopes.get(scope).names.len();
        let object = Rc::new(Object {
            class: class.clone(),
            fields: RefCell::new(vec![Val::Uninit; size]),
        });
        let this = Val::Object(object.clone());
        let (_, _, members) = core::class_parts(tree, class.node);
        let constructor = members
            .iter()
            .copied()
            .find(|&m| tree.data(m) & ROLE_MASK == ROLE_CONSTRUCTOR);
        let mut args = args.into_iter();
        for &member in members {
            let (name, _, init) = core::let_parts(tree, member);
            let data = tree.data(member);
            if data & MEMBER_STATIC != 0 {
                continue;
            }
            let value = match data & ROLE_MASK {
                ROLE_PARAM if constructor.is_none() => match (args.next(), init) {
                    (None | Some(Val::Null), Some(init)) => {
                        self.initialize(s, &class, init, this.clone())?
                    }
                    (Some(value), _) => value,
                    (None, None) => return Err(fail("Too few arguments")),
                },
                0 => match init {
                    Some(init) => self.initialize(s, &class, init, this.clone())?,
                    None => continue,
                },
                _ => continue,
            };
            let slot = s
                .scopes
                .resolved(name)
                .map(|b| s.scopes.symbol(b).slot as usize);
            object.fields.borrow_mut()[slot.ok_or_else(|| fail("Unresolved name"))?] = value;
        }
        match constructor.and_then(|c| core::member_fn(tree, c)) {
            Some(f) => {
                self.invoke(s, this.clone(), &class, f, args.collect())?;
            }
            None if args.next().is_some() => return Err(fail("Too many arguments")),
            None => {}
        }
        Ok(this)
    }

    /// A bodiless `@connected` method that the interpreter implements
    /// itself, as Kotlin Temper's `standardLibraryConnecteds` do: so far
    /// std's `Date.today()` and `regex`'s ([`regexes`]).
    fn connected(&mut self, s: &Settled, class: &Rc<Class>, name: NameId, args: Vec<Val>) -> R {
        let class_name = class_name(s, class.node);
        let in_std = |module| in_std(s, class.node, module);
        match (class_name, s.library.names.get(name), args.as_slice()) {
            ("Date", "today", []) if in_std("temporal") => {
                let (year, month, day) = numbers::today_utc();
                self.construct(
                    s,
                    class.clone(),
                    vec![Val::Int(year), Val::Int(month), Val::Int(day)],
                )
            }
            (class @ ("Regex" | "RegexFormatter"), name, _) if in_std("regex") => {
                self.regex_connected(s, class, name, args)
            }
            (class, name, _) => Err(fail(format!(
                "No connected implementation of {class}.{name}"
            ))),
        }
    }

    /// A bodiless `@connected` function that the interpreter implements:
    /// std `net`'s `sendRequest`, and what its host does, as the REPL's
    /// own functions.
    fn connected_function(&mut self, s: &Settled, decl: NodeId, args: Vec<Val>) -> R {
        let name = core::let_parts(&s.library.tree, decl).0;
        match s.library.name(name).map(|n| s.library.names.get(n)) {
            Some("sendRequest") if in_std(s, decl, "net") => self.send_request(args),
            name => match self.host.clone().and_then(|host| host(s, decl, &args)) {
                // Stopped as Ctrl-C stops code, not failed.
                Some(Err(why)) if why == INTERRUPTED => Err(Abrupt::Panic(why)),
                Some(result) => result.map_err(fail),
                None => Err(fail(format!(
                    "No connected implementation of {}",
                    name.unwrap_or("?")
                ))),
            },
        }
    }

    /// At compile time, refuses a member of a class in a module not on
    /// the allowlist, or a `@connected` one not allowed
    /// ([`compile_time::ALLOWED_STD_MODULES`]).
    fn allowed_now(
        &mut self,
        s: &Settled,
        class: &Rc<Class>,
        decl: NodeId,
        name: NameId,
    ) -> Result<(), Abrupt> {
        let tree = &s.library.tree;
        let connected =
            tree.data(class.node) & CLASS_CONNECTED != 0 || tree.data(decl) & MEMBER_CONNECTED != 0;
        let name = s.library.names.get(name);
        if !self.compile_time {
            return Ok(());
        }
        let allowed = self.module_allowed(s, class.node)
            && (!connected || compile_time::connected_allowed(s, class.node, Some(name)));
        if !allowed {
            return Err(compile_time::refused(&format!(
                "{}.{name}",
                class_name(s, class.node)
            )));
        }
        Ok(())
    }

    /// Whether code declared at `node` may run at compile time, by its
    /// module, remembered per node.
    fn module_allowed(&mut self, s: &Settled, node: NodeId) -> bool {
        *(self.allowed_nodes)
            .entry(node)
            .or_insert_with(|| compile_time::module_allowed(s, node))
    }

    /// Runs code of a class outside its members' functions, with `this`:
    /// a field's initializer or a parameter's default for an object, a
    /// static field's for the class itself, or a supertype.
    fn initialize(&mut self, s: &Settled, class: &Rc<Class>, expr: NodeId, this: Val) -> R {
        let code = self
            .lowering
            .class_code(s, class.node, expr)
            .map_err(fail)?;
        self.run_code(s, code, class.captures.clone(), vec![this])
    }

    /// A `Class` statement: its static fields' initializers, in order. The
    /// class was made when its block was entered.
    fn init_statics(&mut self, s: &Settled, class: &Rc<Class>) -> Result<(), Abrupt> {
        let tree = &s.library.tree;
        for &member in core::class_parts(tree, class.node).2 {
            let (name, _, init) = core::let_parts(tree, member);
            let data = tree.data(member);
            if let (true, Some(init)) = (data & MEMBER_STATIC != 0 && data & ROLE_MASK == 0, init) {
                let value = self.initialize(s, class, init, Val::Class(class.clone()))?;
                let slot = s
                    .scopes
                    .resolved(name)
                    .map(|b| s.scopes.symbol(b).slot as usize);
                class.statics.borrow_mut()[slot.ok_or_else(|| fail("Unresolved name"))?] = value;
            }
        }
        Ok(())
    }

    /// A class's supertypes that are classes.
    fn supers(&mut self, s: &Settled, class: &Rc<Class>) -> Result<Rc<[Rc<Class>]>, Abrupt> {
        if let Some(supers) = class.supers.get() {
            return Ok(supers.clone());
        }
        let mut supers = Vec::new();
        for &sup in core::class_parts(&s.library.tree, class.node).1 {
            if let Val::Class(ref sup) =
                self.initialize(s, class, sup, Val::Class(class.clone()))?
            {
                supers.push(sup.clone());
            }
        }
        Ok(class.supers.get_or_init(|| supers.into()).clone())
    }

    /// Calls a member's `Fn` with `this`, an object or for static members,
    /// a class; `class` declares the member. What it captures comes from
    /// the class, but `this` from the call.
    fn invoke(
        &mut self,
        s: &Settled,
        this: Val,
        class: &Rc<Class>,
        f: NodeId,
        args: Vec<Val>,
    ) -> R {
        let code = self.lowering.function(s, f).map_err(fail)?;
        let mut captures = Vec::with_capacity(code.free.len());
        for &symbol in code.free.iter() {
            if Some(symbol) == class.layout.this() {
                captures.push(Rc::new(RefCell::new(this.clone())));
            } else {
                match class.layout.free.iter().position(|&f| f == symbol) {
                    Some(i) => captures.push(class.captures[i].clone()),
                    None => {
                        return Err(fail(format!(
                            "{} is not in scope here",
                            s.library.names.get(s.scopes.symbol(symbol).name)
                        )));
                    }
                }
            }
        }
        self.call(s, f, captures.into(), args)
    }

    /// The member with a name whose `Let`'s data `wanted` accepts: the
    /// class declaring it, its `Let` and its slot. A class's own members
    /// come first, then its supertypes' breadth-first, so the nearest wins,
    /// and among equally near ones, the first in `extends` order.
    fn find_member(
        &mut self,
        s: &Settled,
        class: &Rc<Class>,
        name: NameId,
        wanted: &dyn Fn(u32) -> bool,
    ) -> Result<Option<(Rc<Class>, NodeId, usize)>, Abrupt> {
        let tree = &s.library.tree;
        let mut queue = std::collections::VecDeque::from([class.clone()]);
        let mut seen = vec![class.node];
        // An abstract member found first gives way to a body found later,
        // as when one interface declares `a()` and another, below it in
        // the same class's supertypes, defines it.
        let mut abstract_found = None;
        let is_abstract = |decl: NodeId| {
            tree.data(decl) & MEMBER_CONNECTED == 0
                && core::let_parts(tree, decl).2.is_some_and(|f| {
                    tree.kind(f) == temper_syntax::tree::NodeKind::Fn
                        && core::member_fn(tree, decl).is_none()
                })
        };
        while let Some(class) = queue.pop_front() {
            let scope = s
                .scopes
                .of_node(class.node)
                .ok_or_else(|| fail("Class not settled"))?;
            // Its own members, after `this`; what it inherits comes after
            // them, found in its supertypes instead.
            let own = 1 + core::class_parts(tree, class.node).2.len();
            for &(n, b) in s.scopes.get(scope).names.iter().take(own) {
                let symbol = s.scopes.symbol(b);
                // A `@noProperty` input is no member by name
                // (`lower::Op::GetInput`).
                if symbol.input {
                    continue;
                }
                if let (true, Some(decl)) = (n == name, symbol.decl) {
                    if wanted(tree.data(decl)) {
                        let found = (class.clone(), decl, symbol.slot as usize);
                        if !is_abstract(decl) {
                            return Ok(Some(found));
                        }
                        abstract_found.get_or_insert(found);
                    }
                }
            }
            for sup in self.supers(s, &class)?.iter() {
                if !seen.contains(&sup.node) {
                    seen.push(sup.node);
                    queue.push_back(sup.clone());
                }
            }
        }
        Ok(abstract_found)
    }

    /// Whether `class` is `target` or a subtype of it.
    fn extends(
        &mut self,
        s: &Settled,
        class: &Rc<Class>,
        target: &Rc<Class>,
    ) -> Result<bool, Abrupt> {
        if class.node == target.node {
            return Ok(true);
        }
        for sup in self.supers(s, class)?.iter() {
            if self.extends(s, sup, target)? {
                return Ok(true);
            }
        }
        Ok(false)
    }

    /// `object.name`, or `Class.name` for a static member: a field, a
    /// getter's result or a method. `from`, as for [`Interp::call_method`].
    fn get_property(
        &mut self,
        s: &Settled,
        object: Val,
        name: NameId,
        from: Option<&[NodeId]>,
    ) -> R {
        if !matches!(object, Val::Object(_) | Val::Class(_)) {
            return property(object, name, s.library.names.get(name));
        }
        let class = class_of(&object);
        let readable = |data| {
            !matches!(data & ROLE_MASK, ROLE_SETTER | ROLE_CONSTRUCTOR) && member_of(&object, data)
        };
        let Some((class, decl, slot)) = self.find_member(s, &class, name, &readable)? else {
            return Err(fail(format!("No member {}", s.library.names.get(name))));
        };
        check_access(s, from, &class, decl, name)?;
        self.allowed_now(s, &class, decl, name)?;
        let data = s.library.tree.data(decl);
        match data & ROLE_MASK {
            ROLE_METHOD => Ok(Val::Method(Rc::new((object.clone(), name)))),
            ROLE_GETTER => match core::member_fn(&s.library.tree, decl) {
                Some(f) => self.invoke(s, receiver(&object, data, &class), &class, f, Vec::new()),
                None => Err(fail("Abstract getter")),
            },
            _ => match field(&object, data, &class, |fields| fields[slot].clone())? {
                Val::Uninit => Err(fail(format!(
                    "{} has not been initialized",
                    s.library.names.get(name)
                ))),
                value => Ok(value),
            },
        }
    }

    /// `object.name = value`, or `Class.name = value`: a field or a setter.
    fn set_property(
        &mut self,
        s: &Settled,
        object: Val,
        name: NameId,
        value: Val,
        from: Option<&[NodeId]>,
    ) -> Result<(), Abrupt> {
        if !matches!(object, Val::Object(_) | Val::Class(_)) {
            return Err(fail(format!(
                "Can't set {} on {}",
                s.library.names.get(name),
                type_name(&object)
            )));
        }
        let class = class_of(&object);
        let writable = |data| {
            matches!(data & ROLE_MASK, 0 | ROLE_PARAM | ROLE_SETTER) && member_of(&object, data)
        };
        let Some((class, decl, slot)) = self.find_member(s, &class, name, &writable)? else {
            return Err(fail(format!("No member {}", s.library.names.get(name))));
        };
        check_access(s, from, &class, decl, name)?;
        self.allowed_now(s, &class, decl, name)?;
        let data = s.library.tree.data(decl);
        if data & ROLE_MASK == ROLE_SETTER {
            return match core::member_fn(&s.library.tree, decl) {
                Some(f) => self
                    .invoke(s, receiver(&object, data, &class), &class, f, vec![value])
                    .map(|_| ()),
                None => Err(fail("Abstract setter")),
            };
        }
        field(&object, data, &class, |fields| fields[slot] = value)
    }
}

/// Runs `f` on where a field lives: its class's statics, or the object's
/// fields.
fn field<T>(
    object: &Val,
    data: u32,
    class: &Rc<Class>,
    f: impl FnOnce(&mut Vec<Val>) -> T,
) -> Result<T, Abrupt> {
    match object {
        _ if data & MEMBER_STATIC != 0 => Ok(f(&mut class.statics.borrow_mut())),
        Val::Object(obj) if Rc::ptr_eq(class, &obj.class) => Ok(f(&mut obj.fields.borrow_mut())),
        // Interfaces have only abstract properties.
        _ => Err(fail("Abstract property")),
    }
}

/// Panics for a private member used outside its class, which Kotlin
/// Temper's typer rejects. Code in the class may use it on any instance.
fn check_access(
    s: &Settled,
    from: Option<&[NodeId]>,
    class: &Rc<Class>,
    decl: NodeId,
    name: NameId,
) -> Result<(), Abrupt> {
    let private = s.library.tree.data(decl) & MEMBER_PRIVATE != 0;
    if !private || from.is_none_or(|from| from.contains(&class.node)) {
        return Ok(());
    }
    Err(Abrupt::Panic(format!(
        "Member {} defined in {} not publicly accessible",
        s.library.names.get(name),
        class_name(s, class.node)
    )))
}

/// A `Class` node's name.
/// Whether a node is in a std module, as `temporal` for `std/temporal`.
fn in_std(s: &Settled, node: NodeId, module: &str) -> bool {
    let file = s.library.locate(s.library.tree.span(node).start).0;
    let path = &s.library.files[file.0 as usize].path;
    path.starts_with(&format!("{}/{module}/", s.library.std_root()))
}

fn class_name(s: &Settled, class: NodeId) -> &str {
    s.library
        .names
        .get(s.library.name(s.library.tree.children(class)[0]).unwrap())
}

/// The class of an object, or a class itself, for its static members.
fn class_of(this: &Val) -> Rc<Class> {
    match this {
        Val::Object(object) => object.class.clone(),
        Val::Class(class) => class.clone(),
        _ => unreachable!("not an object or class"),
    }
}

/// Whether a member with this data can be reached from `this`: a class
/// reaches only static members.
fn member_of(this: &Val, data: u32) -> bool {
    !matches!(this, Val::Class(_)) || data & MEMBER_STATIC != 0
}

/// `this` for running a member: the declaring class for a static one.
fn receiver(this: &Val, data: u32, class: &Rc<Class>) -> Val {
    if data & MEMBER_STATIC != 0 {
        Val::Class(class.clone())
    } else {
        this.clone()
    }
}

/// `ListBuilder`'s methods that `List` lacks.
const BUILDER_METHODS: &[&str] = &[
    "add",
    "addAll",
    "set",
    "removeLast",
    "reverse",
    "clear",
    "sort",
    "splice",
];

/// Runs `f` on a list's or builder's items.
fn with_items<T>(list: &Val, f: impl FnOnce(&[Val]) -> T) -> T {
    match list {
        Val::List(items) => f(items),
        Val::ListBuilder(items) => f(&items.borrow()),
        _ => f(&[]),
    }
}

/// A list's items, or a copy of a builder's.
fn snapshot(list: &Val) -> Rc<[Val]> {
    match list {
        Val::List(items) => items.clone(),
        other => with_items(other, |items| Rc::from(items)),
    }
}

fn is_dot(s: &Settled, node: NodeId) -> bool {
    s.scopes.resolved_builtin(node) == Some(Builtin::Function(Function::Dot))
}

fn property(object: Val, name: NameId, text: &str) -> R {
    match (&object, text) {
        (Val::Str(s), "isEmpty") => Ok(Val::Bool(s.is_empty())),
        (Val::Str(s), "end") => Ok(Val::StringIndex(s.len() as u32)),
        (Val::BuiltinType(t), "begin") if TYPES[*t as usize] == "String" => Ok(Val::StringIndex(0)),
        (Val::BuiltinType(t), "none") if TYPES[*t as usize] == "StringIndex" => {
            Ok(Val::NoStringIndex)
        }
        (Val::StringBuilder(builder), "end") => Ok(Val::StringIndex(builder.borrow().len() as u32)),
        (Val::BuiltinType(t), "e") if TYPES[*t as usize] == "Float64" => {
            Ok(Val::Float(std::f64::consts::E))
        }
        (Val::BuiltinType(t), "pi") if TYPES[*t as usize] == "Float64" => {
            Ok(Val::Float(std::f64::consts::PI))
        }
        (Val::List(items), "isEmpty") => Ok(Val::Bool(items.is_empty())),
        (Val::List(items), "length") => Ok(Val::Int(items.len() as i32)),
        (Val::ListBuilder(items), "isEmpty") => Ok(Val::Bool(items.borrow().is_empty())),
        (Val::ListBuilder(items), "length") => Ok(Val::Int(items.borrow().len() as i32)),
        (Val::Deque(items), "isEmpty") => Ok(Val::Bool(items.borrow().is_empty())),
        (Val::Map(entries), "length") => Ok(Val::Int(entries.len() as i32)),
        (Val::MapBuilder(entries), "length") => Ok(Val::Int(entries.borrow().len() as i32)),
        (Val::Pair(pair), "key") => Ok(pair.0.clone()),
        (Val::Pair(pair), "value") => Ok(pair.1.clone()),
        (Val::ValueResult(value), "value") => Ok((**value).clone()),
        (Val::Generator(generator), "done") => Ok(Val::Bool(generator.frame.borrow().is_none())),
        (Val::PromiseBuilder(promise), "promise") => Ok(Val::Promise(promise.clone())),
        (Val::NetResponse(response), _) => {
            net::property(response, text).ok_or_else(|| fail(format!("No member {text}")))
        }
        (Val::BuiltinType(t), "of") if TYPES[*t as usize] == "List" => {
            Ok(Val::Builtin(Function::List))
        }
        _ => Ok(Val::Method(Rc::new((object, name)))),
    }
}

/// An element of a list, panicking out of bounds, as Temper's does.
fn list_get(items: &[Val], i: i32) -> R {
    match usize::try_from(i).ok().and_then(|i| items.get(i)) {
        Some(item) => Ok(item.clone()),
        None => Err(Abrupt::Panic(format!(
            "index {i} out of bounds for length {}",
            items.len()
        ))),
    }
}

fn literal(value: &Value) -> Option<Val> {
    Some(match value {
        Value::Int32(n) => Val::Int(*n),
        Value::Int64(n) => Val::Int64(*n),
        Value::Float64(f) => Val::Float(*f),
        Value::String(s) => Val::Str(s.as_str().into()),
        Value::Bool(b) => Val::Bool(*b),
        Value::Null => Val::Null,
        Value::Void => Val::Void,
        _ => return None,
    })
}

/// A builtin's value; macros have none.
fn builtin_value(b: Builtin) -> Option<Val> {
    Some(match b {
        Builtin::Value(Constant::True) => Val::Bool(true),
        Builtin::Value(Constant::False) => Val::Bool(false),
        Builtin::Value(Constant::Null) => Val::Null,
        Builtin::Value(Constant::Void) => Val::Void,
        Builtin::Value(Constant::Console) => Val::Console,
        Builtin::Value(Constant::NaN) => Val::Float(f64::NAN),
        Builtin::Value(Constant::Infinity) => Val::Float(f64::INFINITY),
        Builtin::Value(Constant::TestServerPort(port)) => Val::Int(port),
        Builtin::Function(f) => Val::Builtin(f),
        Builtin::Type(t) => Val::BuiltinType(t),
        Builtin::Macro(_) => return None,
    })
}

/// Integer arithmetic on operands widened to `i64`, wrapping; callers
/// truncate for `Int`, which wraps as Temper's does. Division by zero fails.
fn int_op(f: Function, a: i64, b: i64) -> Result<i64, Abrupt> {
    use Function::*;
    Ok(match f {
        Add => a.wrapping_add(b),
        Sub => a.wrapping_sub(b),
        Mul => a.wrapping_mul(b),
        Div | Mod if b == 0 => return Err(Abrupt::Fail(None)),
        Div => a.wrapping_div(b),
        Mod => a.wrapping_rem(b),
        BitAnd => a & b,
        BitOr => a | b,
        BitXor => a ^ b,
        _ => {
            return Err(fail(format!(
                "Can't apply {} to numbers",
                Builtin::Function(f).name()
            )));
        }
    })
}

fn equal(a: &Val, b: &Val) -> bool {
    match (a, b) {
        (Val::Void, Val::Void) | (Val::Null, Val::Null) | (Val::Empty, Val::Empty) => true,
        (Val::Bool(a), Val::Bool(b)) => a == b,
        (Val::Int(a), Val::Int(b)) => a == b,
        (Val::Int64(a), Val::Int64(b)) => a == b,
        (Val::Float(a), Val::Float(b)) => numbers::float_cmp(*a, *b).is_eq(),
        (Val::Str(a), Val::Str(b)) => a == b,
        (Val::StringIndex(a), Val::StringIndex(b)) => a == b,
        (Val::NoStringIndex, Val::NoStringIndex) => true,
        (Val::StringBuilder(a), Val::StringBuilder(b)) => Rc::ptr_eq(a, b),
        (Val::Fn(a), Val::Fn(b)) => Rc::ptr_eq(a, b),
        (Val::Object(a), Val::Object(b)) => Rc::ptr_eq(a, b),
        (Val::ListBuilder(a), Val::ListBuilder(b)) => Rc::ptr_eq(a, b),
        (Val::Deque(a), Val::Deque(b)) => Rc::ptr_eq(a, b),
        (Val::Bits(a), Val::Bits(b)) => Rc::ptr_eq(a, b),
        (Val::Map(a), Val::Map(b)) => Rc::ptr_eq(a, b),
        (Val::MapBuilder(a), Val::MapBuilder(b)) => Rc::ptr_eq(a, b),
        (Val::Pair(a), Val::Pair(b)) => Rc::ptr_eq(a, b),
        (Val::Regex(a), Val::Regex(b)) => Rc::ptr_eq(a, b),
        (Val::Connected(a), Val::Connected(b)) => a == b,
        (Val::NetResponse(a), Val::NetResponse(b)) => Rc::ptr_eq(a, b),
        (Val::Callout(a), Val::Callout(b)) => Rc::ptr_eq(a, b),
        (Val::Generator(a), Val::Generator(b)) => Rc::ptr_eq(a, b),
        (Val::Promise(a), Val::Promise(b)) | (Val::PromiseBuilder(a), Val::PromiseBuilder(b)) => {
            Rc::ptr_eq(a, b)
        }
        _ => false,
    }
}

fn compare(a: &Val, b: &Val) -> Option<std::cmp::Ordering> {
    match (a, b) {
        (Val::Int(a), Val::Int(b)) => Some(a.cmp(b)),
        (Val::Int64(a), Val::Int64(b)) => Some(a.cmp(b)),
        // `false < true`, as `Boolean`'s `<=>` orders them.
        (Val::Bool(a), Val::Bool(b)) => Some(a.cmp(b)),
        (Val::Float(a), Val::Float(b)) => Some(numbers::float_cmp(*a, *b)),
        (Val::Str(a), Val::Str(b)) => Some(a.cmp(b)),
        // `StringIndex.none` sorts before every index.
        (Val::StringIndex(_) | Val::NoStringIndex, Val::StringIndex(_) | Val::NoStringIndex) => {
            let position = |v: &Val| {
                if let Val::StringIndex(i) = v {
                    *i as i64
                } else {
                    -1
                }
            };
            Some(position(a).cmp(&position(b)))
        }
        _ => None,
    }
}

fn type_name(value: &Val) -> &'static str {
    match value {
        Val::Void => "Void",
        Val::Null => "Null",
        Val::Bool(_) => "Boolean",
        Val::Int(_) => "Int",
        Val::Int64(_) => "Int64",
        Val::Float(_) => "Float64",
        Val::Str(_) => "String",
        Val::StringIndex(_) => "StringIndex",
        Val::NoStringIndex => "NoStringIndex",
        Val::StringBuilder(_) => "StringBuilder",
        Val::List(_) => "List",
        Val::ListBuilder(_) => "ListBuilder",
        Val::Deque(_) => "Deque",
        Val::Bits(_) => "DenseBitVector",
        Val::Map(_) => "Map",
        Val::MapBuilder(_) => "MapBuilder",
        Val::Pair(_) => "Pair",
        Val::Fn(_) | Val::Builtin(_) | Val::Method(_) | Val::Connected(_) | Val::Callout(_) => {
            "Function"
        }
        Val::Console => "Console",
        Val::Class(_) => "Class",
        Val::Object(_) => "Object",
        Val::Type | Val::BuiltinType(_) | Val::NullableType(_) => "Type",
        Val::Empty => "Empty",
        Val::Regex(_) => "Regex",
        Val::NetResponse(_) => "NetResponse",
        Val::Generator(g) if g.kind == GeneratorKind::Safe => "SafeGenerator",
        Val::Generator(_) => "Generator",
        Val::ValueResult(_) => "ValueResult",
        Val::DoneResult => "DoneResult",
        Val::Promise(_) => "Promise",
        Val::PromiseBuilder(_) => "PromiseBuilder",
        Val::Uninit => "Uninitialized",
    }
}

/// A value as its `toString()` gives it.
pub fn to_string(value: &Val) -> String {
    match value {
        Val::Str(s) => s.to_string(),
        Val::Int(n) => n.to_string(),
        Val::Int64(n) => n.to_string(),
        Val::Float(f) => numbers::format_float(*f),
        Val::Bool(b) => b.to_string(),
        Val::Null => "null".into(),
        Val::Void => "void".into(),
        other => type_name(other).into(),
    }
}
