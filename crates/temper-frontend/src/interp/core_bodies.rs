//! Running core's Temper bodies in place of the interpreter's natives,
//! for testing that the two agree (`tests/core_bodies.rs`): typing reads
//! core's signatures and editors show its docs, but nothing else
//! runs its bodies, as the interpreter implements core natively and
//! backends connect it. With core settled as a module of the library
//! ([`crate::corelib::SOURCES`]), a run with [`CoreBodies`] calls core's
//! body for a member of a builtin value wherever core declares one: a
//! method, a getter, an `@extension` function, or `get` for `x[i]`.
//! Members without bodies stay native, so a body calls the natives it
//! builds on, as on a backend where only it is translated.
//!
//! Where core's classes are named, as `new ListBuilder<T>()` in a body,
//! they're the builtin types, but for those whose bodies keep their own
//! private state (`StringBuilder`), which runs as
//! ordinary Temper classes, from core's `new` too. A builtin value's
//! bodies read it as `this`.
//!
//! Nothing here runs unless asked for ([`super::run_tests_with_core_bodies`]).

use super::{Abrupt, Class, Interp, R, Val, class_name};
use crate::builtins::{Builtin, Function, TYPES};
use crate::core::{
    self, MEMBER_PRIVATE, MEMBER_STATIC, ROLE_CONSTRUCTOR, ROLE_GETTER, ROLE_MASK, ROLE_METHOD,
};
use crate::settle::{Settled, SettledModule};
use std::cell::RefCell;
use std::collections::{HashMap, HashSet, VecDeque};
use std::rc::Rc;
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind};

/// Core's classes and functions, as its module made them.
pub(super) struct CoreBodies {
    /// Core's classes, by name.
    classes: HashMap<String, Rc<Class>>,
    /// Builtin types, by their index in [`TYPES`], whose `new` makes
    /// core's class: those whose constructor has a body and that keep
    /// private state of their own.
    constructed: HashMap<u8, Rc<Class>>,
    /// Core's `@extension("name")` functions, by that name.
    extensions: HashMap<String, Val>,
    /// Core's functions with Temper bodies that builtin functions name,
    /// as `empty`, by the builtin.
    functions: HashMap<Function, Val>,
    /// The `Fn`s that ran, for checking that each body ran.
    pub(super) ran: RefCell<HashSet<NodeId>>,
}

/// A call [`CoreBodies::apply`] doesn't take, given back.
pub(super) type NotTaken = (Val, Vec<Val>);

impl CoreBodies {
    /// Reads core's module once its top level has run, and makes its
    /// globals that name builtin types those types.
    pub(super) fn new(
        interp: &mut Interp,
        s: &Settled,
        module: &SettledModule,
        first_global: u32,
    ) -> Result<CoreBodies, Abrupt> {
        let names = &s.scopes.get(module.scope).names;
        let mut classes = HashMap::new();
        let mut functions = HashMap::new();
        let mut by_symbol = HashMap::new();
        for &(name, symbol) in names {
            let slot = first_global + s.scopes.symbol(symbol).slot;
            let text = s.library.names.get(name);
            match &interp.globals[slot as usize] {
                Val::Class(class) if class_name(s, class.node) == text => {
                    classes.insert(text.to_string(), class.clone());
                }
                value @ Val::Fn(_) => {
                    by_symbol.insert(text.to_string(), value.clone());
                    if let Some(Builtin::Function(f)) = Builtin::named(text) {
                        functions.insert(f, value.clone());
                    }
                }
                _ => {}
            }
        }
        // Supertypes are read through globals, so before they change.
        for class in classes.values() {
            interp.supers(s, class)?;
        }
        let mut constructed = HashMap::new();
        for (name, class) in &classes {
            if let Some(t) = TYPES.iter().position(|t| t == name) {
                if keeps_own_state(s, class.node) {
                    constructed.insert(t as u8, class.clone());
                }
            }
        }
        // Core's names for builtin types, its globals and what its
        // classes and functions captured, as a static member's class.
        let builtin = |value: &Val| match value {
            Val::Class(class) => {
                let name = class_name(s, class.node);
                let t = TYPES.iter().position(|t| *t == name)? as u8;
                (!constructed.contains_key(&t)).then_some(Val::BuiltinType(t))
            }
            _ => None,
        };
        for &(_, symbol) in names {
            let slot = (first_global + s.scopes.symbol(symbol).slot) as usize;
            let captures = match &interp.globals[slot] {
                Val::Class(class) => class.captures.clone(),
                Val::Fn(closure) => closure.captures.clone(),
                _ => continue,
            };
            for cell in captures.iter() {
                let replaced = builtin(&cell.borrow());
                if let Some(replaced) = replaced {
                    *cell.borrow_mut() = replaced;
                }
            }
            if let Some(replaced) = builtin(&interp.globals[slot]) {
                interp.globals[slot] = replaced;
            }
        }
        let mut extensions = HashMap::new();
        for (extension, function) in extension_names(crate::corelib::code()) {
            if let Some(value) = by_symbol.get(function) {
                extensions.insert(extension.to_string(), value.clone());
            }
        }
        Ok(CoreBodies {
            classes,
            constructed,
            extensions,
            functions,
            ran: RefCell::default(),
        })
    }

    /// A call that core's body answers, or the call given back.
    pub(super) fn apply(
        &self,
        interp: &mut Interp,
        s: &Settled,
        function: Val,
        args: Vec<Val>,
    ) -> Result<R, NotTaken> {
        match &function {
            Val::Method(method) => {
                let (this, name) = (&method.0, method.1);
                if let Some((class, f)) = self.member(interp, s, this, name, ROLE_METHOD) {
                    return Ok(interp.invoke(s, this.clone(), &class, f, args));
                }
                let text = s.library.names.get(name);
                let extended = matches!(
                    this,
                    Val::List(_) | Val::ListBuilder(_) | Val::Map(_) | Val::MapBuilder(_)
                );
                if let (true, Some(extension)) = (extended, self.extensions.get(text)) {
                    let mut all = Vec::with_capacity(args.len() + 1);
                    all.push(this.clone());
                    all.extend(args);
                    return Ok(interp.apply(s, extension.clone(), all));
                }
            }
            Val::Builtin(Function::Index) => {
                let get = s.library.names.find("get");
                let found = (args.first().zip(get))
                    .and_then(|(this, get)| self.member(interp, s, this, get, ROLE_METHOD));
                if let Some((class, f)) = found {
                    let mut args = args;
                    let this = args.remove(0);
                    return Ok(interp.invoke(s, this, &class, f, args));
                }
            }
            Val::Builtin(Function::New) => {
                if let Some(Val::BuiltinType(t)) = args.first() {
                    if let Some(class) = self.constructed.get(t) {
                        let class = class.clone();
                        return Ok(interp.construct(s, class, args[1..].to_vec()));
                    }
                }
            }
            Val::Builtin(f) => {
                if let Some(value) = self.functions.get(f) {
                    return Ok(interp.apply(s, value.clone(), args));
                }
            }
            _ => {}
        }
        Err((function, args))
    }

    /// `object.name` that core answers for a builtin value: its getter's
    /// body, or a static field's value.
    pub(super) fn get(
        &self,
        interp: &mut Interp,
        s: &Settled,
        object: &Val,
        name: NameId,
    ) -> Option<R> {
        if let Val::BuiltinType(t) = object {
            let class = self.classes.get(TYPES[*t as usize])?;
            return static_value(s, class, name).map(Ok);
        }
        let (class, f) = self.member(interp, s, object, name, ROLE_GETTER)?;
        Some(interp.invoke(s, object.clone(), &class, f, Vec::new()))
    }

    /// Core's class for a builtin value.
    fn class_of(&self, value: &Val) -> Option<&Rc<Class>> {
        self.classes.get(builtin_type(value)?)
    }

    /// The nearest member with a body named `name`, with `role`, of a
    /// builtin value's class or its supertypes, breadth-first, passing
    /// over bodiless ones: a body in `Listed` is found for a `List` even
    /// though `List` connects its own.
    fn member(
        &self,
        interp: &mut Interp,
        s: &Settled,
        this: &Val,
        name: NameId,
        role: u32,
    ) -> Option<(Rc<Class>, NodeId)> {
        let tree = &s.library.tree;
        let mut queue = VecDeque::from([self.class_of(this)?.clone()]);
        let mut seen = Vec::new();
        while let Some(class) = queue.pop_front() {
            if seen.contains(&class.node) {
                continue;
            }
            seen.push(class.node);
            let scope = s.scopes.of_node(class.node)?;
            let own = 1 + core::class_parts(tree, class.node).2.len();
            for &(n, b) in s.scopes.get(scope).names.iter().take(own) {
                let Some(decl) = s.scopes.symbol(b).decl.filter(|_| n == name) else {
                    continue;
                };
                let data = tree.data(decl);
                if data & ROLE_MASK == role && data & MEMBER_STATIC == 0 {
                    if let Some(f) = core::member_fn(tree, decl) {
                        return Some((class, f));
                    }
                }
            }
            queue.extend(interp.supers(s, &class).ok()?.iter().cloned());
        }
        None
    }
}

/// Core's members and functions that have Temper bodies, each by a
/// label, as `String.indexOf`, `StringBuilder.constructor` or
/// `listedMap`, with its `Fn`, in source order: from the module of
/// `settled` that's core's text, if any.
pub fn members(s: &Settled) -> Vec<(String, NodeId)> {
    let tree = &s.library.tree;
    let name = |node: NodeId| {
        let name = s.library.name(core::let_parts(tree, node).0);
        name.map_or("?", |n| s.library.names.get(n))
    };
    let mut found = Vec::new();
    for module in s.modules.iter().filter(|m| is_core(s, m)) {
        for &top in &module.top_levels {
            match tree.kind(top) {
                NodeKind::Class => {
                    let (class, _, members) = core::class_parts(tree, top);
                    let class = s
                        .library
                        .name(class)
                        .map_or("?", |n| s.library.names.get(n));
                    for &m in members {
                        let Some(f) = core::member_fn(tree, m) else {
                            continue;
                        };
                        let member = match tree.data(m) & ROLE_MASK {
                            ROLE_CONSTRUCTOR => "constructor",
                            _ => name(m),
                        };
                        found.push((format!("{class}.{member}"), f));
                    }
                }
                NodeKind::Let => {
                    if let Some(f) = core::member_fn(tree, top) {
                        found.push((name(top).to_string(), f));
                    }
                }
                _ => {}
            }
        }
    }
    found.sort_by_key(|&(_, f)| tree.span(f).start);
    found
}

/// Whether a module is core's, its files [`crate::corelib::SOURCES`].
pub(super) fn is_core(s: &Settled, module: &SettledModule) -> bool {
    let tree = &s.library.tree;
    (module.top_levels.first()).is_some_and(|&t| s.library.in_core(tree.span(t).start))
}

/// A builtin value's type, as core names its class.
fn builtin_type(value: &Val) -> Option<&'static str> {
    Some(match value {
        Val::Bool(_) => "Boolean",
        Val::Int(_) => "Int32",
        Val::Int64(_) => "Int64",
        Val::Float(_) => "Float64",
        Val::Str(_) => "String",
        Val::StringIndex(_) => "StringIndex",
        Val::NoStringIndex => "NoStringIndex",
        Val::StringBuilder(_) => "StringBuilder",
        Val::List(_) => "List",
        Val::ListBuilder(_) => "ListBuilder",
        Val::Map(_) => "Map",
        Val::MapBuilder(_) => "MapBuilder",
        Val::Pair(_) => "Pair",
        Val::Empty => "Empty",
        Val::Generator(g) if g.kind == super::GeneratorKind::Safe => "SafeGenerator",
        Val::Generator(_) => "Generator",
        Val::ValueResult(_) => "ValueResult",
        Val::DoneResult => "DoneResult",
        Val::PromiseBuilder(_) => "PromiseBuilder",
        _ => return None,
    })
}

/// A static field's value that core's initializer gave it, as
/// `Float64.pi`'s.
fn static_value(s: &Settled, class: &Rc<Class>, name: NameId) -> Option<Val> {
    let scope = s.scopes.of_node(class.node)?;
    let (_, symbol) = *s
        .scopes
        .get(scope)
        .names
        .iter()
        .find(|&&(n, _)| n == name)?;
    let symbol = s.scopes.symbol(symbol);
    let data = s.library.tree.data(symbol.decl?);
    if data & MEMBER_STATIC == 0 || data & ROLE_MASK != 0 {
        return None;
    }
    match class.statics.borrow().get(symbol.slot as usize)? {
        Val::Uninit => None,
        value => Some(value.clone()),
    }
}

/// Whether a class's public constructor has a body and it has private
/// fields of its own: then its bodies define its state, and it runs as
/// an ordinary class.
fn keeps_own_state(s: &Settled, class: NodeId) -> bool {
    let tree = &s.library.tree;
    let members = core::class_parts(tree, class).2;
    let constructor = members.iter().any(|&m| {
        let data = tree.data(m);
        data & ROLE_MASK == ROLE_CONSTRUCTOR
            && data & MEMBER_PRIVATE == 0
            && core::member_fn(tree, m).is_some()
    });
    let own_state = members.iter().any(|&m| {
        let data = tree.data(m);
        data & ROLE_MASK == 0 && data & MEMBER_PRIVATE != 0 && data & MEMBER_STATIC == 0
    });
    constructor && own_state
}

/// Each `@extension("name") … let function` in core's text, as `("map",
/// "listedMap")`.
fn extension_names(text: &str) -> Vec<(&str, &str)> {
    let mut found = Vec::new();
    for (at, _) in text.match_indices("@extension(\"") {
        let rest = &text[at + 12..];
        let Some((extension, rest)) = rest.split_once('"') else {
            continue;
        };
        let Some(let_at) = rest.find("let ") else {
            continue;
        };
        let function = rest[let_at + 4..]
            .split(|c: char| !c.is_alphanumeric() && c != '_')
            .next()
            .unwrap_or("");
        found.push((extension, function));
    }
    found
}
