//! Reachability: which of a program's own top-level declarations its
//! library needs, which only its tests do, and which nothing does, as
//! Kotlin Temper's `ReachabilityTracer` decides, and each module split
//! by it ([`Split`]) (notes/tmpl/analyses.md, "Reachability").
//!
//! - **Units** are the top-level declarations of the program's own
//!   modules (functions, module `let`s and `var`s, classes, and
//!   interfaces) and the locals of their init blocks' top level: each
//!   with its item, and the init statements that set it (`init x = …`,
//!   `init C.s = …` for a class's static field, a local's `let`). A
//!   class or interface is one unit with all its members, as Kotlin
//!   traces it.
//! - **The library's roots** are exported declarations, `keep`s, and
//!   the init statements that may do something: those that set no unit
//!   (calls, assignments, `if`s, …), and those that set one with a value
//!   that may do something, as Kotlin's are any but a value, a name, or
//!   a function: anything but literals, names, function values, and list
//!   literals, conversions, operators that can't bubble, and core's pure
//!   functions' calls (`getConsole()`) of those.
//! - **The tests' roots** are tests and `keepTest`s.
//! - **Tracing** follows what a unit's nodes name ([`named`]: calls,
//!   references, module `let`s, constructors, and the types of what they
//!   declare), the types of their expressions, the classes of the members
//!   they use, the init block's locals they read, and the bounds of type
//!   parameters they declare. What the library's roots reach is
//!   [`Reach::Library`], what only the tests' do [`Reach::Test`], and the
//!   rest [`Reach::Unreached`]. Like Kotlin's, it follows mentions, not
//!   flow: a call in a branch that never runs still reaches.
//! - **Connected declarations aren't roots,** as in Kotlin: host code
//!   may use only what's exported or kept. Reach is per library, so
//!   another library's declarations are never units.
//! - **A backend asks it of the program it translates,** after the
//!   lowerings it picks: one may drop a call (an argument whose type is
//!   an error fails before the call), leaving its callee unreached.

use crate::analysis::{named, nominals, pure_core, top_level, walk};
use crate::decls::Facts;
use crate::ids::{DeclId, NodeId};
use crate::schema::Node;
use crate::{Module, Program};
use std::collections::{HashMap, HashSet};

/// Whether something is reached, and from where.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Reach {
    /// From the library's roots: in its output.
    Library,
    /// From the tests' roots alone: only where the tests are.
    Test,
    /// From neither: left out.
    Unreached,
}

/// The reach of each of a program's units.
#[derive(Clone, Debug, Default)]
pub struct Reached {
    reach: HashMap<DeclId, Reach>,
}

/// A module split by reach, each list in source order.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Split {
    /// The library's items.
    pub items: Vec<NodeId>,
    /// The library's init statements: the init block's top level but
    /// those of [`Split::test_init`] and [`Split::unreached`].
    pub init: Vec<NodeId>,
    /// Items only the tests reach.
    pub test_items: Vec<NodeId>,
    /// The statements that set what only the tests reach, which run
    /// after the library's init and before the tests.
    pub test_init: Vec<NodeId>,
    /// Items and init statements that nothing reaches.
    pub unreached: Vec<NodeId>,
}

impl Reached {
    /// A unit's reach: `Some` for the program's own top-level
    /// declarations and the locals of their init blocks' top level, and
    /// `None` for anything else (a member's is its class's).
    pub fn get(&self, decl: DeclId) -> Option<Reach> {
        self.reach.get(&decl).copied()
    }

    /// `module` split by reach.
    pub fn split(&self, p: &Program, module: &Module) -> Split {
        let mut split = Split::default();
        let reach = |d: Option<DeclId>| d.and_then(|d| self.get(d)).unwrap_or(Reach::Library);
        for &item in &module.items {
            match reach(item_decl(p, item)) {
                Reach::Library => split.items.push(item),
                Reach::Test => split.test_items.push(item),
                Reach::Unreached => split.unreached.push(item),
            }
        }
        for &stmt in init_stmts(p, module) {
            match reach(sets(p, stmt).map(|(d, _)| d)) {
                Reach::Library => split.init.push(stmt),
                Reach::Test => split.test_init.push(stmt),
                Reach::Unreached => split.unreached.push(stmt),
            }
        }
        split
    }
}

/// The reach of each of `p`'s units (see the module's docs).
pub fn reach(p: &Program) -> Reached {
    let pure = pure_core(p);
    let mut units: HashMap<DeclId, Vec<NodeId>> = HashMap::new();
    let (mut library, mut tests) = (Roots::default(), Roots::default());
    for module in &p.modules {
        for &item in &module.items {
            let Some(decl) = item_decl(p, item) else {
                continue;
            };
            units.entry(decl).or_default().push(item);
            let facts = p.decls.get(decl).facts;
            if facts.contains(Facts::EXPORTED) || facts.contains(Facts::KEEP) {
                library.decls.push(decl);
            }
            if facts.contains(Facts::KEEP_TEST) {
                tests.decls.push(decl);
            }
        }
        for &stmt in init_stmts(p, module) {
            match sets(p, stmt) {
                Some((decl, value)) => {
                    units.entry(decl).or_default().push(stmt);
                    if !value.is_none_or(|v| is_simple(p, &pure, v)) {
                        library.decls.push(decl);
                    }
                }
                None => library.nodes.push(stmt),
            }
        }
        tests.nodes.extend(&module.tests);
    }
    let mut tracer = Tracer {
        p,
        reach: (units.keys()).map(|&d| (d, Reach::Unreached)).collect(),
        units,
    };
    tracer.trace(Reach::Library, library);
    tracer.trace(Reach::Test, tests);
    Reached {
        reach: tracer.reach,
    }
}

/// Where tracing starts: units, and nodes outside any.
#[derive(Default)]
struct Roots {
    decls: Vec<DeclId>,
    nodes: Vec<NodeId>,
}

struct Tracer<'a> {
    p: &'a Program,
    units: HashMap<DeclId, Vec<NodeId>>,
    reach: HashMap<DeclId, Reach>,
}

impl Tracer<'_> {
    /// Marks what `roots` reach, that nothing reached before, as `reach`.
    fn trace(&mut self, reach: Reach, roots: Roots) {
        let mut work = Vec::new();
        for decl in roots.decls {
            self.mark(decl, reach, &mut work);
        }
        for node in roots.nodes {
            for decl in self.names(node) {
                self.mark(decl, reach, &mut work);
            }
        }
        while let Some(decl) = work.pop() {
            for node in self.units[&decl].clone() {
                for found in self.names(node) {
                    self.mark(found, reach, &mut work);
                }
            }
        }
    }

    fn mark(&mut self, decl: DeclId, reach: Reach, work: &mut Vec<DeclId>) {
        if let Some(r) = self.reach.get_mut(&decl) {
            if *r == Reach::Unreached {
                *r = reach;
                work.push(decl);
            }
        }
    }

    /// The units, and maybe other declarations, that a node names.
    fn names(&self, node: NodeId) -> HashSet<DeclId> {
        let p = self.p;
        let mut found = HashSet::new();
        let mut add = |d: DeclId| {
            if let Some(top) = top_level(p, d) {
                found.insert(top);
            }
        };
        named(p, node, &mut add);
        walk(p, node, &mut |id| {
            // What each expression's type names, as a cast's.
            let mut types = Vec::new();
            nominals(p, p.nodes.ty(id), &mut types);
            types.into_iter().for_each(&mut add);
            match p.nodes.get(id) {
                // The init block's locals, and module `let`s it sets inside
                // statements, which [`named`] leaves to callers.
                Node::Local { local } | Node::Assign { local, .. } => add(local),
                Node::InitLet { target, .. } => add(target),
                // The class of a member used, which its value's type names
                // too, but maybe only in another unit.
                Node::CallMethod { method: d, .. }
                | Node::GetField { field: d, .. }
                | Node::SetField { field: d, .. } => add(d),
                // Type parameters' bounds.
                Node::Function { decl, .. }
                | Node::BodilessFunction { decl, .. }
                | Node::Method { decl, .. }
                | Node::AbstractMethod { decl, .. }
                | Node::Class { decl, .. }
                | Node::Interface { decl, .. } => {
                    for &param in p.decls.type_params(decl) {
                        let mut types = Vec::new();
                        for &bound in p.decls.supers(param) {
                            nominals(p, bound, &mut types);
                        }
                        types.into_iter().for_each(&mut add);
                    }
                }
                _ => {}
            }
        });
        found
    }
}

/// The declaration an item declares.
fn item_decl(p: &Program, item: NodeId) -> Option<DeclId> {
    match p.nodes.get(item) {
        Node::Function { decl, .. }
        | Node::BodilessFunction { decl, .. }
        | Node::ModuleLet { decl }
        | Node::Class { decl, .. }
        | Node::Interface { decl, .. } => Some(decl),
        _ => None,
    }
}

/// The statements of a module's init block's top level.
fn init_stmts<'a>(p: &'a Program, module: &Module) -> &'a [NodeId] {
    match p.nodes.get(module.init) {
        Node::Block { stmts } => p.nodes.list(stmts),
        _ => &[],
    }
}

/// The unit an init statement sets, and the value it sets it to, if any:
/// a module `let`'s `init`, a static field's (its class's), or a local's
/// `let`.
fn sets(p: &Program, stmt: NodeId) -> Option<(DeclId, Option<NodeId>)> {
    match p.nodes.get(stmt) {
        Node::InitLet { target, value } => Some((target, Some(value))),
        Node::InitStatic { target, value } => Some((top_level(p, target)?, Some(value))),
        Node::Let { local, init } => Some((local, init)),
        _ => None,
    }
}

/// Whether an initializer's value is simple, so it doesn't make its unit
/// a root: what does nothing but give a value, as Kotlin's values, names,
/// and functions don't. Literals, names, and function values (whose bodies
/// don't run here); and list literals, conversions, operators that can't
/// bubble, `&&`, `||`, and `?:`, and calls of core's pure functions
/// (`getConsole()`) that can't bubble, of simple values. Not constructors,
/// which run code.
fn is_simple(p: &Program, pure: &HashSet<DeclId>, value: NodeId) -> bool {
    let simple = |v: &NodeId| is_simple(p, pure, *v);
    let all = |list| p.nodes.list(list).iter().all(simple);
    match p.nodes.get(value) {
        Node::BoolLit { .. }
        | Node::Int32Lit { .. }
        | Node::Int64Lit { .. }
        | Node::Float64Lit { .. }
        | Node::StringLit { .. }
        | Node::VoidLit {}
        | Node::TypeValue { .. }
        | Node::Null {}
        | Node::Omitted {}
        | Node::Local { .. }
        | Node::GetModuleLet { .. }
        | Node::GetStatic { .. }
        | Node::FunctionRef { .. }
        | Node::FunctionLiteral { .. } => true,
        Node::Upcast { value }
        | Node::ToFun { value }
        | Node::FromFun { value }
        | Node::AsNullable { value } => simple(&value),
        Node::ListLit { items } => all(items),
        Node::Op {
            may_bubble: false, ..
        }
        | Node::And { .. }
        | Node::Or { .. }
        | Node::IfValue { .. } => p.nodes.kids(value).iter().all(simple),
        Node::CallFunction {
            callee: d,
            may_bubble: false,
            ..
        }
        | Node::CallMethod {
            method: d,
            may_bubble: false,
            ..
        } => pure.contains(&d) && p.nodes.kids(value).iter().all(simple),
        _ => false,
    }
}
