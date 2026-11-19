//! Compile-time evaluation for the frontend's passes: a [`Session`] runs
//! modules' top-level code once, then lets a pass call functions and
//! members on the values they made, each call under a step budget, as
//! Kotlin Temper's frontend interprets a module before its passes use the
//! values. So far only the contextual autoescaping pass uses it
//! ([`crate::autoescape`]).
//!
//! What a pass learns comes back as owned data, never as live values: a
//! value to be rebuilt in code is a [`Construction`].

use super::{Abrupt, Interp, Lowering, Object, Output, R, Val, describe};
use crate::builtins::Function;
use crate::core::{self, CLASS_INTERFACE, MEMBER_STATIC, ROLE_CONSTRUCTOR, ROLE_MASK, ROLE_PARAM};
use crate::scope::{ScopeId, SymbolId};
use crate::settle::Settled;
use std::cell::RefCell;
use std::collections::{HashMap, VecDeque};
use std::rc::Rc;
use temper_syntax::names::NameId;
use temper_syntax::tree::NodeId;

/// What compile-time runs may use, by module (notes/compile-time.md,
/// "What runs at compile time"): a library's own Temper code and core's
/// builtin types, and of std, only the pure modules listed here. Of those,
/// [`DENIED_MEMBERS`] are refused too, as clock reads. Every other std
/// module, as `net` (which would send requests), `testing`, and any
/// future one, and every `@connected` function or class outside them,
/// a library's own included, is refused ([`refused`]), so a use that
/// needs one is left to run time and nothing outside the interpreter
/// happens. Add to these deliberately; `compile_time_allowlist` in
/// this crate's tests prints them.
pub const ALLOWED_STD_MODULES: &[&str] = &["regex", "json", "temporal"];

/// Members of allowed std modules refused anyway, as (module, class,
/// member): they read the clock or the like.
pub const DENIED_MEMBERS: &[(&str, &str, &str)] = &[("temporal", "Date", "today")];

/// Where a node is: its std module's name, as `regex` for
/// `std/regex/…`, or `None` outside std.
fn std_module(s: &Settled, node: NodeId) -> Option<String> {
    let file = s.library.locate(s.library.tree.span(node).start).0;
    let path = &s.library.files[file.0 as usize].path;
    let rest = path.strip_prefix(&format!("{}/", s.library.std_root()))?;
    Some(rest.split('/').next().unwrap_or("").to_string())
}

/// Whether code declared at `node` may run at compile time: any outside
/// std, else only in [`ALLOWED_STD_MODULES`].
pub(super) fn module_allowed(s: &Settled, node: NodeId) -> bool {
    std_module(s, node).is_none_or(|m| ALLOWED_STD_MODULES.contains(&m.as_str()))
}

/// Whether a `@connected` class's member, or with `None`, the class
/// itself, as for `new`, may run at compile time: only std's, in an
/// allowed module, and not denied.
pub(super) fn connected_allowed(s: &Settled, class: NodeId, member: Option<&str>) -> bool {
    let Some(module) = std_module(s, class) else {
        return false;
    };
    let name = super::class_name(s, class);
    ALLOWED_STD_MODULES.contains(&module.as_str())
        && !DENIED_MEMBERS
            .iter()
            .any(|&(m, c, f)| m == module && c == name && member == Some(f))
}

/// Whether a builtin function may run at compile time: the language's
/// pure operations. Not `async`, which runs code from a queue later.
/// Named one by one, so a new builtin is refused until it's added here.
pub fn builtin_allowed(f: Function) -> bool {
    use Function::*;
    matches!(
        f,
        Add | Sub
            | Mul
            | Div
            | Mod
            | Pow
            | Lt
            | Le
            | Gt
            | Ge
            | Eq
            | Ne
            | Not
            | BitAnd
            | BitOr
            | BitXor
            | Shl
            | Shr
            | UShr
            | BitNot
            | Cat
            | Panic
            | Dot
            | FnType
            | Throws
            | Angle
            | Nullable
            | List
            | Index
            | New
            | As
            | Is
            | Cmp
            | Ignore
            | Char
            | Raw
            | Empty
            | DoPure
            | DoneResult
            | Rgx
    )
}

/// Why a compile-time run stopped: it reached something not on the
/// allowlist, so its result isn't known yet, as Kotlin's `NotYet`.
pub(super) fn refused(what: &str) -> Abrupt {
    Abrupt::Panic(format!("not yet: {what} isn't run at compile time"))
}

/// What a callout function was given: each problem and whether it's an
/// error, in order.
pub(crate) type Callouts = Rc<RefCell<Vec<(String, bool)>>>;

/// An interpreter whose modules' top-level code has run, for calls at
/// compile time.
pub(crate) struct Session {
    interp: Interp<'static>,
    first_globals: HashMap<ScopeId, u32>,
    /// How many of the modules, in run order, ran to the end.
    ran: usize,
    /// Whether a call failed since this was made, which may have left
    /// module state half updated.
    failed: bool,
    /// Each module's top-level code, in run order, once lowered.
    module_code: Vec<Rc<super::lower::Code>>,
}

/// A value that code can make again: what a constant, a list literal, or
/// `new C(…)` of a class whose constructor takes its fields, gives.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Construction {
    Null,
    Bool(bool),
    Int(i32),
    Int64(i64),
    Float(f64),
    Str(String),
    /// `String.begin`.
    StringBegin,
    List(Vec<Construction>),
    /// `new C(args)`, by the class's node.
    New(NodeId, Vec<Construction>),
}

impl Session {
    /// Runs the first `modules` modules' top-level code, in run order,
    /// each with `fuel` steps, stopping at the first that doesn't finish.
    /// What they log is dropped. Nothing with effects outside the
    /// interpreter runs: `async` blocks and native jobs are dropped, never
    /// run, and std `net`'s `sendRequest` panics, so code that needs one
    /// doesn't finish, and what depends on it is left to run time.
    pub(crate) fn new(s: &Settled, modules: usize, fuel: u64) -> Session {
        let mut first_globals = HashMap::new();
        let mut globals = 0;
        for module in &s.modules {
            first_globals.insert(module.scope, globals);
            globals += s.scopes.get(module.scope).names.len() as u32;
        }
        let mut session = Session {
            interp: Interp {
                output: Output {
                    lines: Vec::new(),
                    bytes: 0,
                    dropped: 0,
                    note: 0,
                    sink: None,
                },
                fuel,
                reserve: 0,
                interrupt: None,
                depth: 0,
                globals: vec![Val::Uninit; globals as usize],
                unset_why: HashMap::new(),
                lowering: Lowering::new(first_globals.clone()),
                ready: VecDeque::new(),
                cached: Vec::new(),
                compile_time: true,
                allowed_nodes: HashMap::new(),
                host: None,
                inputs_parked: Vec::new(),
                inputs_failed: Vec::new(),
                core: None,
            },
            first_globals,
            ran: 0,
            failed: false,
            module_code: Vec::new(),
        };
        session.run_modules(s, modules, fuel);
        session
    }

    /// Starts again, after a call failed: module state is dropped, then
    /// the modules' top-level code runs again, as [`Session::new`] runs
    /// it. The code lowered so far is kept, as it comes from the tree
    /// alone, which a session doesn't change, and holds no values that
    /// change; so starting again costs only the top-level runs, and the
    /// old state is gone before the new is made.
    pub(crate) fn renew(&mut self, s: &Settled, modules: usize, fuel: u64) {
        let globals = self.interp.globals.len();
        self.interp.globals.clear();
        self.interp.globals.resize(globals, Val::Uninit);
        self.interp.ready.clear();
        self.interp.output.lines.clear();
        self.ran = 0;
        self.failed = false;
        self.run_modules(s, modules, fuel);
    }

    /// Runs the first `modules` modules' top-level code, as
    /// [`Session::new`] says.
    fn run_modules(&mut self, s: &Settled, modules: usize, fuel: u64) {
        for (i, module) in s.modules.iter().take(modules).enumerate() {
            if i == self.module_code.len() {
                let lowering = &mut self.interp.lowering;
                (self.module_code).push(lowering.module(s, module.scope, &module.top_levels));
            }
            let code = self.module_code[i].clone();
            self.interp.fuel = fuel;
            self.interp.depth = 0;
            // Not `run_top`: `async` blocks and native jobs never run at
            // compile time.
            let result = self.interp.run_code(s, code, Rc::from([]), Vec::new());
            self.interp.ready.clear();
            self.interp.output.lines.clear();
            if result.is_err() {
                break;
            }
            self.ran += 1;
        }
    }

    /// How many modules, in run order, ran to the end.
    pub(crate) fn ran(&self) -> usize {
        self.ran
    }

    /// Whether a call failed, or ran out of steps, since the session was
    /// made or renewed: module state may then be half updated, so a pass
    /// renews the session before going on ([`Session::renew`]).
    pub(crate) fn failed(&self) -> bool {
        self.failed
    }

    /// Gives the calls that follow `fuel` steps in all.
    pub(crate) fn set_fuel(&mut self, fuel: u64) {
        self.interp.fuel = fuel;
    }

    /// A module-level declaration's value, if it has one.
    pub(crate) fn global(&self, s: &Settled, symbol: SymbolId) -> Option<Val> {
        let sym = s.scopes.symbol(symbol);
        let first = *self.first_globals.get(&sym.owner)?;
        match self.interp.globals.get((first + sym.slot) as usize)? {
            Val::Uninit => None,
            value => Some(value.clone()),
        }
    }

    /// Calls a function value.
    pub(crate) fn call(&mut self, s: &Settled, f: Val, args: Vec<Val>) -> Result<Val, String> {
        let result = self.interp.apply(s, f, args);
        self.finish(result)
    }

    /// Calls an object's method, or a class's static one.
    pub(crate) fn call_member(
        &mut self,
        s: &Settled,
        this: &Val,
        name: NameId,
        args: Vec<Val>,
    ) -> Result<Val, String> {
        if !matches!(this, Val::Object(_) | Val::Class(_)) {
            return Err("Not an object".into());
        }
        let result = self.interp.call_method(s, this, name, args, None);
        self.finish(result)
    }

    /// A class's static member, if it has one, as [`Session::get`], but
    /// a missing one isn't a failure.
    pub(crate) fn get_if_any(&mut self, s: &Settled, this: &Val, name: NameId) -> Option<Val> {
        let failed = self.failed;
        let value = self.get(s, this, name).ok();
        if value.is_none() && self.interp.fuel > 0 {
            self.failed = failed;
        }
        value
    }

    /// Reads an object's property, a field or a getter's result, or a
    /// class's static one.
    pub(crate) fn get(&mut self, s: &Settled, this: &Val, name: NameId) -> Result<Val, String> {
        let result = self.interp.get_property(s, this.clone(), name, None);
        self.finish(result)
    }

    fn finish(&mut self, result: R) -> Result<Val, String> {
        self.interp.depth = 0;
        self.interp.output.lines.clear();
        self.interp.ready.clear();
        self.failed |= result.is_err();
        result.map_err(describe)
    }
}

/// A function value that keeps what it's called with, as a library's
/// `DiagnosticCallout`, `fn (String, Boolean): Void`.
pub(crate) fn callout() -> (Val, Callouts) {
    let callouts = Callouts::default();
    (Val::Callout(callouts.clone()), callouts)
}

/// Calls a callout made by [`callout`].
pub(super) fn call_callout(callouts: &Callouts, args: Vec<Val>) -> R {
    match args.as_slice() {
        [Val::Str(problem), Val::Bool(is_error)] => {
            callouts.borrow_mut().push((problem.to_string(), *is_error));
            Ok(Val::Void)
        }
        _ => Err(Abrupt::Fail(Some(
            "A callout takes a String and a Boolean".into(),
        ))),
    }
}

/// A copy of a value, and of the mutable values in it, so that changes to
/// one don't show in the other, as Kotlin's `deepValueCopy` makes them for
/// each branch of a context propagation. Shared values are copied once
/// per path to them, as Kotlin's are.
pub(crate) fn deep_copy(value: &Val) -> Val {
    match value {
        Val::Object(object) => Val::Object(Rc::new(Object {
            class: object.class.clone(),
            fields: RefCell::new(object.fields.borrow().iter().map(deep_copy).collect()),
        })),
        Val::List(items) => Val::List(items.iter().map(deep_copy).collect()),
        Val::ListBuilder(items) => Val::ListBuilder(Rc::new(RefCell::new(
            items.borrow().iter().map(deep_copy).collect(),
        ))),
        Val::StringBuilder(text) => {
            Val::StringBuilder(Rc::new(RefCell::new(text.borrow().clone())))
        }
        Val::MapBuilder(entries) => {
            Val::MapBuilder(Rc::new(RefCell::new(entries.borrow().clone())))
        }
        Val::Pair(pair) => Val::Pair(Rc::new((deep_copy(&pair.0), deep_copy(&pair.1)))),
        other => other.clone(),
    }
}

/// Whether two values are alike: the same object, or objects of one
/// class whose fields are alike, or equal other values. Two escapers
/// alike this way escape alike, as their methods see only their fields.
pub(crate) fn alike(a: &Val, b: &Val) -> bool {
    alike_within(a, b, 0)
}

fn alike_within(a: &Val, b: &Val, depth: u32) -> bool {
    match (a, b) {
        (Val::Object(x), Val::Object(y)) if !Rc::ptr_eq(x, y) => {
            depth < 16
                && Rc::ptr_eq(&x.class, &y.class)
                && (x.fields.borrow().iter())
                    .zip(y.fields.borrow().iter())
                    .all(|(f, g)| alike_within(f, g, depth + 1))
        }
        (Val::List(x), Val::List(y)) => {
            x.len() == y.len()
                && x.iter()
                    .zip(y.iter())
                    .all(|(f, g)| alike_within(f, g, depth + 1))
        }
        (Val::Uninit, Val::Uninit) => true,
        _ => super::equal(a, b),
    }
}

/// An object's class, as a value, for its statics.
pub(crate) fn class_value(value: &Val) -> Option<Val> {
    match value {
        Val::Object(object) => Some(Val::Class(object.class.clone())),
        _ => None,
    }
}

/// An object's class, by its node.
pub(crate) fn class_node(value: &Val) -> Option<NodeId> {
    match value {
        Val::Object(object) => Some(object.class.node),
        Val::Class(class) => Some(class.node),
        _ => None,
    }
}

/// An object's own field, by name, not a getter's result.
pub(crate) fn field(s: &Settled, value: &Val, name: NameId) -> Option<Val> {
    let Val::Object(object) = value else {
        return None;
    };
    let tree = &s.library.tree;
    let (_, _, members) = core::class_parts(tree, object.class.node);
    let member = members.iter().copied().find(|&m| {
        let data = tree.data(m);
        matches!(data & ROLE_MASK, 0 | ROLE_PARAM)
            && data & MEMBER_STATIC == 0
            && s.library.name(core::let_parts(tree, m).0) == Some(name)
    })?;
    let symbol = s.scopes.resolved(core::let_parts(tree, member).0)?;
    match object
        .fields
        .borrow()
        .get(s.scopes.symbol(symbol).slot as usize)?
    {
        Val::Uninit => None,
        value => Some(value.clone()),
    }
}

/// How code can make a value again, if it can: as Kotlin's
/// `digestFor`, constants, `String.begin`, lists of such, and objects of
/// classes without a constructor of their own, which take their header's
/// fields, `class C(public a: A)`, from such values.
pub(crate) fn construction(s: &Settled, value: &Val) -> Option<Construction> {
    Some(match value {
        Val::Null => Construction::Null,
        Val::Bool(b) => Construction::Bool(*b),
        Val::Int(n) => Construction::Int(*n),
        Val::Int64(n) => Construction::Int64(*n),
        Val::Float(f) => Construction::Float(*f),
        Val::Str(text) => Construction::Str(text.to_string()),
        Val::StringIndex(0) => Construction::StringBegin,
        Val::List(items) => Construction::List(
            items
                .iter()
                .map(|v| construction(s, v))
                .collect::<Option<_>>()?,
        ),
        Val::Object(object) => {
            let tree = &s.library.tree;
            let class = object.class.node;
            if tree.data(class) & CLASS_INTERFACE != 0 {
                return None;
            }
            let (_, _, members) = core::class_parts(tree, class);
            let mut args = Vec::new();
            for &m in members {
                let data = tree.data(m);
                match data & ROLE_MASK {
                    ROLE_CONSTRUCTOR => return None,
                    ROLE_PARAM if data & MEMBER_STATIC == 0 => {
                        let symbol = s.scopes.resolved(core::let_parts(tree, m).0)?;
                        let slot = s.scopes.symbol(symbol).slot as usize;
                        let field = object.fields.borrow().get(slot)?.clone();
                        args.push(construction(s, &field)?);
                    }
                    _ => {}
                }
            }
            Construction::New(class, args)
        }
        _ => return None,
    })
}
