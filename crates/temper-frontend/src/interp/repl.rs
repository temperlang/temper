//! The interpreter kept alive across a REPL session's inputs
//! ([`crate::repl`]): its globals grow by each module and input settled,
//! code already lowered stays cached (the tree only grows), and `async`
//! blocks waiting on promises stay parked until a later input resolves
//! them. Values print as Kotlin Temper's REPL prints them ([`render`]).

use super::frame::Frame;
use super::lower::GeneratorKind;
use super::{Abrupt, Generator, INTERRUPTED, Interp, Lowering, Output, SLICE, Val, describe};
use crate::builtins::TYPES;
use crate::core::{self, MEMBER_STATIC, ROLE_MASK, ROLE_PARAM};
use crate::scope::ScopeId;
use crate::settle::{Settled, SettledModule};
use std::cell::RefCell;
use std::collections::{HashMap, VecDeque};
use std::rc::Rc;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use temper_syntax::tree::NodeId;

/// What implements bodiless `@connected` functions outside std, as the
/// REPL's `help`: given a declaration and its arguments, its result, or
/// why it failed; `None` if it's not one of the host's.
pub(crate) type Host = Rc<dyn Fn(&Settled, NodeId, &[Val]) -> Option<Result<Val, String>>>;

/// A host function's argument, as it cares about it.
pub(crate) enum Arg<'a> {
    Null,
    Int(i64),
    Str(&'a str),
    /// A function or a class, by its declaration: a `Let`, or a `Class`.
    Decl(NodeId),
    /// A builtin function or type, by name.
    Builtin(&'static str),
    /// Anything else, by its type's name.
    Other(&'static str),
}

/// An argument as [`Arg`].
pub(crate) fn arg(value: &Val) -> Arg<'_> {
    match value {
        Val::Null | Val::Void => Arg::Null,
        Val::Int(n) => Arg::Int(*n as i64),
        Val::Int64(n) => Arg::Int(*n),
        Val::Str(text) => Arg::Str(text),
        Val::Fn(closure) => Arg::Decl(closure.node),
        Val::Class(class) => Arg::Decl(class.node),
        Val::Connected(decl) => Arg::Decl(*decl),
        Val::Builtin(f) => Arg::Builtin(crate::builtins::Builtin::Function(*f).name()),
        Val::BuiltinType(i) => Arg::Builtin(TYPES[*i as usize]),
        other => Arg::Other(super::type_name(other)),
    }
}

/// How running a module or an input ended.
pub(crate) enum Outcome {
    /// It ran to the end, giving this.
    Value(Val),
    /// It failed or panicked, as [`describe`] says.
    Failed(String),
    /// It was interrupted.
    Interrupted,
    /// It awaits a promise nothing has resolved yet; it goes on once one
    /// does, as a later input may.
    Unresolved,
}

/// An interpreter whose state outlives each input.
pub(crate) struct Runner {
    interp: Interp<'static>,
    /// Each module's first global.
    first_globals: HashMap<ScopeId, u32>,
    /// Steps each input may take; `None` for no limit.
    fuel: Option<u64>,
}

impl Runner {
    /// A runner logging each line to `sink` as it's logged, stopped by
    /// `interrupt` when it's set, each input taking at most `fuel` steps.
    pub(crate) fn new(
        sink: Box<dyn FnMut(&str)>,
        interrupt: Option<Arc<AtomicBool>>,
        fuel: Option<u64>,
        host: Option<Host>,
    ) -> Runner {
        Runner {
            interp: Interp {
                output: Output {
                    lines: Vec::new(),
                    bytes: 0,
                    dropped: 0,
                    note: 0,
                    sink: Some(sink),
                },
                fuel: 0,
                reserve: 0,
                interrupt,
                depth: 0,
                globals: Vec::new(),
                unset_why: HashMap::new(),
                lowering: Lowering::new(HashMap::new()),
                ready: VecDeque::new(),
                cached: Vec::new(),
                compile_time: false,
                allowed_nodes: HashMap::new(),
                host,
                inputs_parked: Vec::new(),
                inputs_failed: Vec::new(),
                #[cfg(feature = "core-bodies")]
                core: None,
            },
            first_globals: HashMap::new(),
            fuel,
        }
    }

    /// Sets the steps each later input may take; `None` for no limit.
    pub(crate) fn set_fuel(&mut self, fuel: Option<u64>) {
        self.fuel = fuel;
    }

    /// Makes room for a module's declarations among the globals.
    pub(crate) fn add_module(&mut self, s: &Settled, scope: ScopeId) {
        let first = self.interp.globals.len() as u32;
        let count = s.scopes.get(scope).names.len();
        (self.interp.globals).resize(first as usize + count, Val::Uninit);
        self.interp.lowering.add_globals(scope, first);
        self.first_globals.insert(scope, first);
    }

    /// Readies a run: its steps, an empty call stack, and the errors
    /// reported so far, as the library has grown.
    fn start(&mut self) {
        let budget = self.fuel.unwrap_or(u64::MAX);
        self.interp.fuel = budget.min(SLICE);
        self.interp.reserve = budget - self.interp.fuel;
        self.interp.depth = 0;
        self.interp.lowering.forget_errors();
    }

    /// Runs a loaded module's top level, then what it queued.
    pub(crate) fn run_module(&mut self, s: &Settled, module: &SettledModule) -> Outcome {
        self.start();
        let code = (self.interp.lowering).module(s, module.scope, &module.top_levels);
        let result = self.interp.run_top(s, code);
        outcome(result)
    }

    /// Runs an input's top level, giving its last statement's value with
    /// `keep`, then what's ready in the queue. At a top-level `await` of a
    /// promise not resolved yet, it runs the queue, then goes on if that
    /// resolved it; if not, the input is parked, as an `async` block
    /// awaiting it, and goes on once a later input resolves it; if it
    /// fails then, that's its own failure, input `number`'s
    /// ([`Runner::resumed_failures`]).
    pub(crate) fn run_input(
        &mut self,
        s: &Settled,
        input: &SettledModule,
        root: NodeId,
        keep: bool,
        number: usize,
    ) -> Outcome {
        self.start();
        let lowering = &mut self.interp.lowering;
        let code = lowering.repl_input(s, input.scope, &input.top_levels, keep, root);
        let mut frame = Frame::new(code, Rc::from([]), Vec::new());
        let mut failure = None;
        let result = loop {
            match self.interp.run_frame(s, &mut frame) {
                Err(Abrupt::Await(promise)) => {
                    if let Err(abrupt) = self.interp.run_tasks(s) {
                        failure.get_or_insert(abrupt);
                    }
                    if promise.outcome().is_none() {
                        let generator = Rc::new(Generator {
                            frame: RefCell::new(Some(frame)),
                            kind: GeneratorKind::Safe,
                        });
                        self.interp.inputs_parked.push((generator.clone(), number));
                        promise.park(generator);
                        return match failure {
                            Some(abrupt) => outcome(Err(abrupt)),
                            None => Outcome::Unresolved,
                        };
                    }
                }
                other => break other,
            }
        };
        let tasks = self.interp.run_tasks(s);
        let result = match failure {
            Some(abrupt) => Err(abrupt),
            None => result.and_then(|value| tasks.map(|()| value)),
        };
        outcome(result)
    }

    /// Runs an input's tests, `run` ([`SettledModule::tests`]), each with
    /// the steps an input may take, giving each test's name and why it
    /// failed, if it did; or how running them all failed, as an
    /// interrupt.
    pub(crate) fn run_tests(
        &mut self,
        s: &Settled,
        scope: ScopeId,
        run: NodeId,
    ) -> Result<Vec<(String, Option<String>)>, Outcome> {
        self.start();
        let budget = self.fuel.unwrap_or(u64::MAX);
        let cases = self.interp.run_test_cases(s, scope, run, budget);
        let cases = cases.map_err(|abrupt| outcome(Err(abrupt)))?;
        let report: Vec<String> = cases.into_iter().flat_map(|c| c.report).collect();
        Ok(crate::library_tests::parse_junit(&report))
    }

    /// Notes why the globals of a module that failed are unset, for a
    /// later read of one: `why`, after its name.
    pub(crate) fn note_unset(&mut self, s: &Settled, scope: ScopeId, why: &str) {
        let Some(&first) = self.first_globals.get(&scope) else {
            return;
        };
        for (i, &(name, _)) in s.scopes.get(scope).names.iter().enumerate() {
            let index = first + i as u32;
            if matches!(self.interp.globals[index as usize], Val::Uninit) {
                let name = s.library.names.get(name);
                let text: Rc<str> = format!("{name} has no value: {why}").into();
                self.interp.unset_why.insert(index, text);
            }
        }
    }

    /// The inputs parked at a top-level `await` that failed when a later
    /// run resumed them, each by its number, with why, as [`describe`]
    /// says it.
    pub(crate) fn resumed_failures(&mut self) -> Vec<(usize, String)> {
        (self.interp.inputs_failed.drain(..))
            .map(|(input, abrupt)| (input, describe(abrupt)))
            .collect()
    }

    /// Drops what a panic may have left half done: calls in progress.
    pub(crate) fn recover(&mut self) {
        self.interp.depth = 0;
    }
}

fn outcome(result: Result<Val, Abrupt>) -> Outcome {
    match result {
        Ok(value) => Outcome::Value(value),
        Err(Abrupt::Panic(why)) if why == INTERRUPTED => Outcome::Interrupted,
        Err(abrupt) => Outcome::Failed(describe(abrupt)),
    }
}

/// How deep [`render`] goes into nested values, and about how much it
/// writes, before it stops with `…`.
const RENDER_DEPTH: usize = 64;
const RENDER_LENGTH: usize = 1 << 16;

/// A value as Kotlin Temper's REPL prints it: `"quoted"` strings with
/// Temper's escapes, `[1,2,3]` for lists, `[(k,v)]` for maps,
/// `{class: C, a: 3}` for objects, `ƒ` for functions, `void`, `null`.
/// A class or interface prints as `class_label` names it. A value
/// within itself prints as `↻` where it recurs, so a cyclic value
/// prints rather than overflowing, as Kotlin's does; past
/// [`RENDER_DEPTH`] levels, or about [`RENDER_LENGTH`] bytes, the rest is
/// `…`.
pub(crate) fn render(s: &Settled, value: &Val, class_label: &dyn Fn(NodeId) -> String) -> String {
    let mut r = Render {
        s,
        class_label,
        out: String::new(),
        path: Vec::new(),
    };
    r.value(value);
    r.out
}

struct Render<'a> {
    s: &'a Settled,
    class_label: &'a dyn Fn(NodeId) -> String,
    out: String,
    /// The containers being printed, by address, outermost first.
    path: Vec<usize>,
}

impl Render<'_> {
    fn value(&mut self, value: &Val) {
        if self.out.len() > RENDER_LENGTH {
            if !self.out.ends_with('…') {
                self.out.push('…');
            }
            return;
        }
        match value {
            Val::Void => self.out.push_str("void"),
            Val::Null => self.out.push_str("null"),
            Val::Str(text) => quote_within(text, &mut self.out, RENDER_LENGTH),
            Val::Bool(_) | Val::Int(_) | Val::Int64(_) | Val::Float(_) => {
                self.out.push_str(&super::to_string(value))
            }
            Val::Fn(_) | Val::Builtin(_) | Val::Method(_) | Val::Connected(_) | Val::Callout(_) => {
                self.out.push('ƒ')
            }
            Val::Class(class) => self.out.push_str(&(self.class_label)(class.node)),
            Val::BuiltinType(i) => self.out.push_str(TYPES[*i as usize]),
            Val::NullableType(inner) => {
                self.value(inner);
                self.out.push('?');
            }
            Val::List(items) => {
                self.within(Rc::as_ptr(items) as *const u8 as usize, |r| r.list(items))
            }
            Val::ListBuilder(items) => {
                self.within(Rc::as_ptr(items) as usize, |r| r.list(&items.borrow()))
            }
            Val::Map(entries) => {
                self.within(Rc::as_ptr(entries) as usize, |r| r.map(entries.pairs()))
            }
            Val::MapBuilder(entries) => self.within(Rc::as_ptr(entries) as usize, |r| {
                r.map(entries.borrow().pairs())
            }),
            Val::Pair(pair) => self.within(Rc::as_ptr(pair) as usize, |r| {
                r.out.push_str("{class: Pair, key: ");
                r.value(&pair.0);
                r.out.push_str(", value: ");
                r.value(&pair.1);
                r.out.push('}');
            }),
            Val::Object(object) => self.within(Rc::as_ptr(object) as usize, |r| {
                r.object(object.class.node, &object.fields.borrow())
            }),
            Val::StringBuilder(text) => {
                self.out.push_str("{class: StringBuilder, content: ");
                quote_within(&text.borrow(), &mut self.out, RENDER_LENGTH);
                self.out.push('}');
            }
            Val::Uninit => self.out.push_str("fail"),
            other => self.out.push_str(super::type_name(other)),
        }
    }

    /// Prints a container with `f`, unless it's being printed already
    /// further out, or the nesting is too deep.
    fn within(&mut self, address: usize, f: impl FnOnce(&mut Self)) {
        if self.path.contains(&address) {
            self.out.push('↻');
        } else if self.path.len() >= RENDER_DEPTH {
            self.out.push('…');
        } else {
            self.path.push(address);
            f(self);
            self.path.pop();
        }
    }

    fn list(&mut self, items: &[Val]) {
        self.out.push('[');
        for (i, item) in items.iter().enumerate() {
            if i > 0 {
                self.out.push(',');
            }
            self.value(item);
        }
        self.out.push(']');
    }

    fn map(&mut self, pairs: &[(Val, Val)]) {
        self.out.push('[');
        for (i, (key, value)) in pairs.iter().enumerate() {
            if i > 0 {
                self.out.push(',');
            }
            self.out.push('(');
            self.value(key);
            self.out.push(',');
            self.value(value);
            self.out.push(')');
        }
        self.out.push(']');
    }

    /// `{class: C, a: 1, b: 2}`: its instance fields, in the order the
    /// class declares them.
    fn object(&mut self, class: NodeId, fields: &[Val]) {
        let (s, tree) = (self.s, &self.s.library.tree);
        self.out.push_str("{class: ");
        self.out.push_str(&(self.class_label)(class));
        for &m in core::class_parts(tree, class).2 {
            let data = tree.data(m);
            let is_field = tree.kind(m) == temper_syntax::tree::NodeKind::Let
                && matches!(data & ROLE_MASK, 0 | ROLE_PARAM)
                && data & MEMBER_STATIC == 0;
            if !is_field {
                continue;
            }
            let name = core::let_parts(tree, m).0;
            let Some(symbol) = s.scopes.resolved(name) else {
                continue;
            };
            let Some(field) = fields.get(s.scopes.symbol(symbol).slot as usize) else {
                continue;
            };
            let text = s.library.names.get(s.scopes.symbol(symbol).name);
            self.out.push_str(", ");
            self.out.push_str(text);
            self.out.push_str(": ");
            self.value(field);
        }
        self.out.push('}');
    }
}

/// A string as [`quote_within`] writes it, however long.
#[cfg(test)]
pub(crate) fn quote(text: &str, out: &mut String) {
    quote_within(text, out, usize::MAX)
}

/// A string as a Temper string literal, with Kotlin's `temperEscaper`'s
/// escapes: `\"`, `\\`, `\n`, `\r`, `\t`, and `\u{…}` for `$` and for
/// what isn't graphic, runs of them sharing one, as `\u{24,a0}`. Once
/// `out` is longer than `limit` bytes, the string ends there, its closing
/// quote followed by `…`, so one huge string doesn't make a huge line.
fn quote_within(text: &str, out: &mut String, limit: usize) {
    out.push('"');
    let mut run = false;
    let mut cut = false;
    for c in text.chars() {
        if out.len() > limit {
            cut = true;
            break;
        }
        let fixed = match c {
            '"' => Some("\\\""),
            '\\' => Some("\\\\"),
            '\n' => Some("\\n"),
            '\r' => Some("\\r"),
            '\t' => Some("\\t"),
            _ => None,
        };
        let hex = fixed.is_none() && (c == '$' || c == '\u{a0}' || !is_graphic(c));
        if hex {
            out.push_str(if run { "," } else { "\\u{" });
            out.push_str(&format!("{:x}", c as u32));
            run = true;
            continue;
        }
        if run {
            out.push('}');
            run = false;
        }
        match fixed {
            Some(escape) => out.push_str(escape),
            None => out.push(c),
        }
    }
    if run {
        out.push('}');
    }
    out.push('"');
    if cut {
        out.push('…');
    }
}

/// Whether a character prints as itself: not a control, format,
/// private-use, or noncharacter code point. (Kotlin's also escapes
/// unassigned ones, which this doesn't know.)
fn is_graphic(c: char) -> bool {
    let n = c as u32;
    !(c.is_control()
        || matches!(n, 0xad | 0x600..=0x605 | 0x61c | 0x6dd | 0x70f | 0x180e)
        || matches!(n, 0x200b..=0x200f | 0x202a..=0x202e | 0x2060..=0x2064 | 0x2066..=0x206f)
        || matches!(n, 0xfeff | 0xfff9..=0xfffb | 0x110bd | 0x1d173..=0x1d17a)
        || matches!(n, 0xe0001 | 0xe0020..=0xe007f)
        || matches!(n, 0xe000..=0xf8ff | 0xf0000..=0x10ffff)
        || matches!(n, 0xfdd0..=0xfdef)
        || n & 0xfffe == 0xfffe)
}
