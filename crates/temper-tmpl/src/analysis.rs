//! Analyses that passes and backends share (notes/tmpl/analyses.md).
//!
//! So far: a generic walk, and module state: what each function writes
//! of its module's `var`s (Python's `global`), what it refers to, and per
//! module, which `let`s and `var`s functions use and which the init block
//! initializes at its top level ([`module_state`]); and whether a `var`'s
//! new value reads only itself, so the update may run under one lock
//! ([`reads_only_itself`]); whether an expression calls nothing, so it may
//! run under a lock ([`calls_nothing`]); and what evaluating an
//! expression may do that the order of evaluation would show, for
//! targets that don't evaluate operands left to right ([`Effects`]).
//! And what a subtree names ([`named`]), and what a module imports: the
//! declarations of other modules, the program's own or other libraries',
//! that its nodes and declared types name ([`imports`]), and what a
//! module's test parts define and use of its library, for backends that
//! write them apart ([`test_parts`]). And function
//! values: the signature a value of a type has ([`fun_signature`]), what
//! a function literal captures ([`captures`]), and which captured locals
//! are assigned, which some targets keep in cells ([`assigned_captures`]),
//! and the groups of local functions that use each other or themselves
//! ([`function_groups`]). And the defaults a function's body starts with
//! ([`defaults`]), and the classes a spread may copy with a target's own
//! copy form ([`copyable_classes`]).

use crate::decls::{DeclKind, Facts};
use crate::ids::TypeId;
use crate::ids::{DeclId, NodeId};
use crate::qname::{QNames, is_external};
use crate::schema::Node;
use crate::types::{Scalar, TypeData};
use crate::{Module, Program};
use std::collections::{HashMap, HashSet};

/// Calls `visit` on `id` and everything under it, parents before
/// children, children in evaluation order.
pub fn walk(p: &Program, id: NodeId, visit: &mut impl FnMut(NodeId)) {
    visit(id);
    for &kid in p.nodes.kids(id) {
        walk(p, kid, visit);
    }
}

/// [`walk`], but past the subtrees whose roots `skip` takes.
pub(crate) fn walk_but(
    p: &Program,
    id: NodeId,
    skip: &impl Fn(NodeId) -> bool,
    visit: &mut impl FnMut(NodeId),
) {
    if skip(id) {
        return;
    }
    visit(id);
    for &kid in p.nodes.kids(id) {
        walk_but(p, kid, skip, visit);
    }
}

/// The module `var`s a function (or any subtree) assigns, in the order
/// first assigned: what Python declares `global` in it.
pub fn module_writes(p: &Program, function: NodeId) -> Vec<DeclId> {
    let mut found = Vec::new();
    walk(p, function, &mut |id| {
        if let Node::SetModuleLet { target, .. } = p.nodes.get(id) {
            if !found.contains(&target) {
                found.push(target);
            }
        }
    });
    found
}

/// The module-level declarations a function (or any subtree) refers to:
/// functions it calls and module `let`s it reads or writes, in the order
/// first referred to. External declarations aren't included.
pub fn module_refs(p: &Program, function: NodeId) -> Vec<DeclId> {
    let mut found = Vec::new();
    walk(p, function, &mut |id| {
        let decl = match p.nodes.get(id) {
            Node::CallFunction { callee, .. } | Node::FunctionRef { target: callee } => callee,
            Node::GetModuleLet { target } | Node::SetModuleLet { target, .. } => target,
            _ => return,
        };
        if !crate::qname::is_external(p, decl) && !found.contains(&decl) {
            found.push(decl);
        }
    });
    found
}

/// The module a declaration belongs to: the program's own module, or
/// another library's module (a [`DeclKind::Module`] owned by a `uses`
/// library), or for a declaration owned directly by a `uses` library, as
/// core's are, that library. `None` for one that isn't owned by either.
pub fn home(p: &Program, decl: DeclId) -> Option<DeclId> {
    let mut d = decl;
    for _ in 0..p.decls.len() {
        if !p.decls.contains(d) {
            return None;
        }
        match p.decls.get(d).kind {
            DeclKind::Module | DeclKind::Library => return Some(d),
            _ => d = p.decls.get(d).owner,
        }
    }
    None
}

/// What one module imports from another, the program's own or another
/// library's.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Import {
    /// The module: the program's own, another library's (owned by its
    /// `uses` library), or a `uses` library itself for what it owns
    /// directly.
    pub from: DeclId,
    /// The top-level declarations used from it (functions, module `let`s
    /// and `var`s, classes, and interfaces), in `DeclId` order.
    pub decls: Vec<DeclId>,
}

/// What `roots` (a module's items and init block, or its tests) import:
/// the top-level declarations of modules other than `module` that they
/// call, read, or name in a declared type (of a function, a parameter, a
/// local, or a module `let`), grouped by module, the program's own
/// modules first in their order, then other libraries' in `DeclId` order.
/// Core's declarations (`temper-core`'s), which every backend reaches
/// through its runtime, aren't included.
pub fn imports(p: &Program, module: DeclId, roots: &[NodeId]) -> Vec<Import> {
    let mut found: Vec<DeclId> = Vec::new();
    for &root in roots {
        named(p, root, &mut |d| {
            if let Some(top) = top_level(p, d) {
                if !found.contains(&top) {
                    found.push(top);
                }
            }
        });
    }
    let mut groups: Vec<Import> = Vec::new();
    for d in found {
        let Some(from) = home(p, d) else { continue };
        if from == module || crate::qname::library_of(p, d) == "core" {
            continue;
        }
        match groups.iter_mut().find(|g| g.from == from) {
            Some(g) => g.decls.push(d),
            None => groups.push(Import {
                from,
                decls: vec![d],
            }),
        }
    }
    let order = |from: DeclId| match p.modules.iter().position(|m| m.decl == from) {
        Some(i) => (0, i as u32),
        None => (1, from.0),
    };
    groups.sort_by_key(|g| order(g.from));
    for g in &mut groups {
        g.decls.sort();
    }
    groups
}

/// The declarations a subtree names: the functions it calls or refers
/// to, the module `let`s it reads or writes, the constructors and static
/// fields it uses, and the classes and interfaces in the types it checks
/// (`is`, type values), writes (casts, `null`, function values), and
/// declares (of functions, parameters, locals, module `let`s, and
/// fields), and the supertypes of a class or interface it defines. A
/// member found stands for its class ([`imports`] takes each one's top
/// level). Each may come more than once.
pub fn named(p: &Program, root: NodeId, found: &mut impl FnMut(DeclId)) {
    named_but(p, root, &|_| false, found)
}

/// [`named`], but past the subtrees whose roots `skip` takes.
pub(crate) fn named_but(
    p: &Program,
    root: NodeId,
    skip: &impl Fn(NodeId) -> bool,
    found: &mut impl FnMut(DeclId),
) {
    walk_but(p, root, skip, &mut |id| {
        let node = p.nodes.get(id);
        match node {
            Node::CallFunction { callee, .. } | Node::FunctionRef { target: callee } => {
                found(callee)
            }
            Node::GetModuleLet { target } | Node::SetModuleLet { target, .. } => found(target),
            // Classes, through what's used of them.
            Node::New { ctor: d, .. }
            | Node::GetStatic { target: d }
            | Node::InitStatic { target: d, .. } => found(d),
            Node::Is { ty, .. } | Node::TypeValue { of: ty } => {
                let mut types = Vec::new();
                nominals(p, ty, &mut types);
                for t in types {
                    found(t);
                }
            }
            _ => {}
        }
        let ty = p.nodes.ty(id);
        let written = matches!(
            node,
            Node::Upcast { .. }
                | Node::Downcast { .. }
                | Node::Null {}
                | Node::NonNull { .. }
                | Node::FunctionLiteral { .. }
                | Node::FunctionRef { .. }
                | Node::ToFun { .. }
                | Node::FromFun { .. }
        );
        if written && !ty.is_none() {
            let mut types = Vec::new();
            nominals(p, ty, &mut types);
            for t in types {
                found(t);
            }
        }
        let defined = match node {
            Node::Function { decl, .. }
            | Node::BodilessFunction { decl, .. }
            | Node::Test { decl, .. }
            | Node::Param { decl }
            | Node::ModuleLet { decl }
            | Node::Field { decl }
            | Node::StaticField { decl }
            | Node::Method { decl, .. }
            | Node::AbstractMethod { decl, .. }
            | Node::FunctionLiteral { decl, .. } => decl,
            Node::Class { decl, .. } | Node::Interface { decl, .. } => {
                for &s in p.decls.supers(decl) {
                    let mut types = Vec::new();
                    nominals(p, s, &mut types);
                    for t in types {
                        found(t);
                    }
                }
                return;
            }
            Node::Let { local, .. } => local,
            _ => return,
        };
        let mut types = Vec::new();
        nominals(p, p.decls.get(defined).ty, &mut types);
        for t in types {
            found(t);
        }
    });
}

/// What a module's items and init block, the library's parts, import
/// ([`imports`]).
pub fn module_imports(p: &Program, module: &Module) -> Vec<Import> {
    let roots: Vec<NodeId> = (module.items.iter().copied())
        .chain([module.init])
        .collect();
    imports(p, module.decl, &roots)
}

/// What a module's tests and its test items and init import beyond the
/// library ([`imports`]), std's `Test` among them.
pub fn test_imports(p: &Program, module: &Module) -> Vec<Import> {
    let roots: Vec<NodeId> = module.test_roots().collect();
    imports(p, module.decl, &roots)
}

/// The libraries a program's modules use but core, by their names in
/// its `uses` sections, in first use: in their library's parts
/// ([`module_imports`]), or with `tests`, in their test parts and tests
/// ([`test_imports`]).
pub fn libraries_used(p: &Program, tests: bool) -> Vec<String> {
    let mut found: Vec<String> = Vec::new();
    for module in &p.modules {
        let imports = match tests {
            true => test_imports(p, module),
            false => module_imports(p, module),
        };
        for d in imports.into_iter().flat_map(|i| i.decls) {
            if !is_external(p, d) || crate::qname::library_of(p, d) == "core" {
                continue;
            }
            let mut library = d;
            while p.decls.contains(library) && p.decls.get(library).kind != DeclKind::Library {
                library = p.decls.get(library).owner;
            }
            let name = p.name(library);
            if !found.iter().any(|f| f == name) {
                found.push(name.to_string());
            }
        }
    }
    found
}

/// The libraries only a program's tests use ([`libraries_used`]), which a
/// package depends on for its tests alone (as Maven's `test` scope, a
/// tests' project, or npm's `devDependencies`).
pub fn test_only_libraries(p: &Program) -> Vec<String> {
    let code = libraries_used(p, false);
    (libraries_used(p, true).into_iter())
        .filter(|l| !code.contains(l))
        .collect()
}

/// What a partitioned module's test parts (its test items and init, and
/// its tests) define, and what they use of its library's parts, for a
/// backend that writes them in a file of their own, which imports or is
/// given what they use (notes/tmpl/modules.md, "Test parts").
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct TestParts {
    /// What they define at their top level: the test items' declarations,
    /// and the test init's locals.
    pub own: HashSet<DeclId>,
    /// What they use of the library's parts, in `DeclId` order: the
    /// module's top-level declarations they name ([`named`], a member
    /// standing for its class), and its init block's locals they read.
    pub used: Vec<DeclId>,
    /// Of those, the module `var`s and locals they assign, in `DeclId`
    /// order.
    pub assigned: Vec<DeclId>,
}

impl TestParts {
    /// Whether they assign a local of the module's init block, which a
    /// file of their own can't.
    pub fn assign_init_locals(&self, p: &Program) -> bool {
        (self.assigned.iter()).any(|&d| p.decls.get(d).kind == DeclKind::Local)
    }
}

/// A module's [`TestParts`].
pub fn test_parts(p: &Program, module: &Module) -> TestParts {
    let mut own: HashSet<DeclId> = (module.test_items.iter())
        .filter_map(|&item| match p.nodes.get(item) {
            Node::Function { decl, .. }
            | Node::BodilessFunction { decl, .. }
            | Node::ModuleLet { decl }
            | Node::Class { decl, .. }
            | Node::Interface { decl, .. } => Some(decl),
            _ => None,
        })
        .collect();
    if let Some(Node::Block { stmts }) = module.test_init.map(|t| p.nodes.get(t)) {
        for &s in p.nodes.list(stmts) {
            if let Node::Let { local, .. } = p.nodes.get(s) {
                own.insert(local);
            }
        }
    }
    let (mut used, mut assigned) = (Vec::new(), Vec::new());
    let library = |d: DeclId| !own.contains(&d) && home(p, d) == Some(module.decl);
    let init_local =
        |d: DeclId| p.decls.get(d).kind == DeclKind::Local && p.decls.get(d).owner == module.decl;
    for root in module.test_roots() {
        named(p, root, &mut |d| {
            if let Some(top) = top_level(p, d).filter(|&t| library(t)) {
                used.push(top);
            }
        });
        walk(p, root, &mut |id| match p.nodes.get(id) {
            Node::SetModuleLet { target, .. } if library(target) => assigned.push(target),
            Node::Local { local } if init_local(local) && library(local) => used.push(local),
            Node::Assign { local, .. } if init_local(local) && library(local) => {
                used.push(local);
                assigned.push(local);
            }
            _ => {}
        });
    }
    for list in [&mut used, &mut assigned] {
        list.sort();
        list.dedup();
    }
    TestParts {
        own,
        used,
        assigned,
    }
}

/// The top-level declaration a declaration is or is a member of: itself
/// for a function, module `let`, class, or interface owned by a module or
/// a `uses` library, else its owner's.
pub(crate) fn top_level(p: &Program, decl: DeclId) -> Option<DeclId> {
    let mut d = decl;
    for _ in 0..p.decls.len() {
        if !p.decls.contains(d) {
            return None;
        }
        let owner = p.decls.get(d).owner;
        if !p.decls.contains(owner) {
            return None;
        }
        if matches!(
            p.decls.get(owner).kind,
            DeclKind::Module | DeclKind::Library
        ) {
            return Some(d);
        }
        d = owner;
    }
    None
}

/// The classes and interfaces a type names, through function types.
pub(crate) fn nominals(p: &Program, ty: crate::ids::TypeId, out: &mut Vec<DeclId>) {
    if !p.types.contains(ty) {
        return;
    }
    match p.types.get(ty) {
        crate::types::TypeData::Nominal { decl, args } => {
            out.push(decl);
            for &a in p.types.list(args) {
                nominals(p, a, out);
            }
        }
        crate::types::TypeData::TypeParam { .. } => {}
        crate::types::TypeData::Function { .. } => {
            if let Some(sig) = p.types.signature(ty) {
                for &t in sig.params.iter().chain([&sig.result]) {
                    nominals(p, t, out);
                }
            }
        }
        crate::types::TypeData::Nullable(t) | crate::types::TypeData::Never(t) => {
            nominals(p, t, out)
        }
        crate::types::TypeData::Scalar(_) => {}
    }
}

/// What a module's functions and init block do with its module `let`s
/// and `var`s.
#[derive(Clone, Debug, Default)]
pub struct ModuleState {
    /// Those a function reads or writes, so they outlive the init block
    /// (Rust's statics).
    pub used_by_functions: HashSet<DeclId>,
    /// Those initialized by an `init` statement directly in the init
    /// block, not inside an `if` or a loop, so a declaration there can
    /// give the value (JS's `const x = …`).
    pub initialized_at_top: HashSet<DeclId>,
}

/// The module state of `module`, its test parts' included.
pub fn module_state(p: &Program, module: &Module) -> ModuleState {
    let mut state = ModuleState::default();
    for item in module.all_items() {
        if let Node::Function { .. } = p.nodes.get(item) {
            for d in module_refs(p, item) {
                if p.decls.get(d).kind == DeclKind::ModuleLet {
                    state.used_by_functions.insert(d);
                }
            }
        }
    }
    for block in [module.init].into_iter().chain(module.test_init) {
        // A function literal in the init block may run after it.
        walk(p, block, &mut |id| {
            if let Node::FunctionLiteral { .. } = p.nodes.get(id) {
                for d in module_refs(p, id) {
                    if p.decls.get(d).kind == DeclKind::ModuleLet {
                        state.used_by_functions.insert(d);
                    }
                }
            }
        });
        if let Node::Block { stmts } = p.nodes.get(block) {
            for &s in p.nodes.list(stmts) {
                if let Node::InitLet { target, .. } = p.nodes.get(s) {
                    state.initialized_at_top.insert(target);
                }
            }
        }
    }
    state
}

/// Whether a module `var`'s new value reads it, and else only locals and
/// literals, through operators: an update such as `ticks = ticks + 1`,
/// which may be computed and stored under one lock, since it calls
/// nothing that could take another (Rust's and Java's synchronized
/// module `var`s).
pub fn reads_only_itself(p: &Program, target: DeclId, value: NodeId) -> bool {
    let mut reads = false;
    let mut other = false;
    walk(p, value, &mut |id| match p.nodes.get(id) {
        Node::GetModuleLet { target: t } if t == target => reads = true,
        Node::BoolLit { .. }
        | Node::Int32Lit { .. }
        | Node::Int64Lit { .. }
        | Node::Float64Lit { .. }
        | Node::StringLit { .. }
        | Node::VoidLit {}
        | Node::TypeValue { .. }
        | Node::Local { .. }
        | Node::Op { .. }
        | Node::And { .. }
        | Node::Or { .. }
        | Node::IfValue { .. } => {}
        _ => other = true,
    });
    reads && !other
}

/// Whether an expression calls nothing: only literals, locals, module
/// `let`s, and operators, so it may run while a lock is held, as no call
/// in it could take the lock again (C++'s module `var`s).
pub fn calls_nothing(p: &Program, value: NodeId) -> bool {
    let mut other = false;
    walk(p, value, &mut |id| match p.nodes.get(id) {
        Node::BoolLit { .. }
        | Node::Int32Lit { .. }
        | Node::Int64Lit { .. }
        | Node::Float64Lit { .. }
        | Node::StringLit { .. }
        | Node::VoidLit {}
        | Node::TypeValue { .. }
        | Node::Local { .. }
        | Node::GetModuleLet { .. }
        | Node::Op { .. }
        | Node::And { .. }
        | Node::Or { .. }
        | Node::IfValue { .. } => {}
        _ => other = true,
    });
    !other
}

/// Core's connected declarations known to have no side effects, by qname,
/// sorted: calls of them need no order kept among themselves. Any other
/// connected declaration is taken to have some (`console.log` does).
pub const PURE_CORE: &[&str] = &[
    "core.getConsole()",
    "core.type Boolean.toString()",
    "core.type Float64.toString()",
    "core.type Int32.max()",
    "core.type Int32.min()",
    "core.type Int32.toString()",
    "core.type Int64.toString()",
    "core.type String.get isEmpty()",
];

/// The declarations of core's that [`PURE_CORE`] names.
pub(crate) fn pure_core(p: &Program) -> HashSet<DeclId> {
    let qnames = QNames::derive(p);
    (p.decls.ids())
        .filter(|&d| p.decls.contains(d) && is_external(p, d))
        .filter(|&d| {
            qnames
                .get(d)
                .is_some_and(|q| PURE_CORE.binary_search(&q.to_string().as_str()).is_ok())
        })
        .collect()
}

/// What evaluating an expression may do that the order of evaluation
/// would show (notes/tmpl/analyses.md, "Order-sensitive operands").
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Effect {
    /// Writes module state, or calls something that may: a function of the
    /// program that does, or a connected declaration not in
    /// [`PURE_CORE`].
    pub writes: bool,
    /// Reads a module `var` that a function writes, itself or through a
    /// call.
    pub reads: bool,
    /// May bubble.
    pub bubbles: bool,
}

impl Effect {
    /// Both effects.
    pub fn and(self, other: Effect) -> Effect {
        Effect {
            writes: self.writes || other.writes,
            reads: self.reads || other.reads,
            bubbles: self.bubbles || other.bubbles,
        }
    }

    /// Whether two operands' order shows: one writes, and the other writes
    /// too, reads what may be written, or bubbles, which would skip the
    /// write or not.
    pub fn conflicts(self, other: Effect) -> bool {
        (self.writes && (other.writes || other.reads || other.bubbles))
            || (other.writes && (self.reads || self.bubbles))
    }

    /// Whether it does anything at all that order could show.
    pub fn any(self) -> bool {
        self.writes || self.reads || self.bubbles
    }
}

/// The effects of a module's functions, for [`Effect`]s of expressions
/// in it: which module `var`s functions write (so reading them is
/// order-sensitive), and each function's own effect, through what it
/// calls. In subset A no call can assign a local, since there are no
/// captures, so reading a local is never order-sensitive.
#[derive(Clone, Debug, Default)]
pub struct Effects {
    /// Module `var`s that a function writes.
    pub mutable: HashSet<DeclId>,
    functions: HashMap<DeclId, Effect>,
    pure: HashSet<DeclId>,
}

impl Effects {
    /// The effects of `module`'s functions, its test items' included.
    pub fn of(p: &Program, module: &Module) -> Effects {
        let mut effects = Effects {
            pure: pure_core(p),
            ..Effects::default()
        };
        let mut functions = Vec::new();
        for item in module.all_items() {
            if let Node::Function { decl, .. } = p.nodes.get(item) {
                effects.mutable.extend(module_writes(p, item));
                functions.push((decl, item));
            }
        }
        // Each function's own effect, then through calls, until nothing
        // changes.
        loop {
            let mut changed = false;
            for &(decl, item) in &functions {
                let effect = effects.node(p, item);
                if effects.functions.get(&decl) != Some(&effect) {
                    effects.functions.insert(decl, effect);
                    changed = true;
                }
            }
            if !changed {
                return effects;
            }
        }
    }

    /// What evaluating `id` may do.
    pub fn node(&self, p: &Program, id: NodeId) -> Effect {
        let mut effect = Effect::default();
        walk(p, id, &mut |n| {
            let here = match p.nodes.get(n) {
                Node::SetModuleLet { .. } => Effect {
                    writes: true,
                    ..Effect::default()
                },
                Node::GetModuleLet { target } => Effect {
                    reads: self.mutable.contains(&target),
                    ..Effect::default()
                },
                Node::CallFunction {
                    callee: d,
                    may_bubble,
                    ..
                }
                | Node::CallMethod {
                    method: d,
                    may_bubble,
                    ..
                } => {
                    let called = if is_external(p, d) {
                        Effect {
                            writes: !self.pure.contains(&d),
                            ..Effect::default()
                        }
                    } else {
                        self.functions.get(&d).copied().unwrap_or_default()
                    };
                    called.and(Effect {
                        bubbles: may_bubble,
                        ..Effect::default()
                    })
                }
                Node::Op { may_bubble, .. } => Effect {
                    bubbles: may_bubble,
                    ..Effect::default()
                },
                Node::Error { .. } => Effect {
                    bubbles: true,
                    ..Effect::default()
                },
                _ => Effect::default(),
            };
            effect = effect.and(here);
        });
        effect
    }
}

/// Whether a value of type `sub` is also of type `sup`: the same type, or
/// a class or interface whose declared supertypes, theirs, and so on,
/// with type arguments put in, include `sup` (type arguments are
/// invariant); a type parameter whose bound is one; `T` for `U?` where
/// `T` is a `U`; and every class, interface, and type parameter is an
/// `AnyValue`.
pub fn is_subtype(p: &Program, sub: TypeId, sup: TypeId) -> bool {
    if sub == sup {
        return true;
    }
    if !p.types.contains(sub) || !p.types.contains(sup) {
        return false;
    }
    // `T?` fits only a nullable type, and `T` fits `U?` where it fits `U`.
    match (p.types.get(sub), p.types.get(sup)) {
        (TypeData::Nullable(a), TypeData::Nullable(b)) => return is_subtype(p, a, b),
        (TypeData::Nullable(_), _) => return false,
        (_, TypeData::Nullable(b)) => return is_subtype(p, sub, b),
        _ => {}
    }
    match p.types.get(sub) {
        TypeData::TypeParam { decl } => {
            sup == TypeId::ANY_VALUE
                || (p.decls.contains(decl)
                    && (p.decls.supers(decl).iter()).any(|&b| is_subtype(p, b, sup)))
        }
        TypeData::Nominal { .. } if sup == TypeId::ANY_VALUE => true,
        // A builtin value as `AnyValue`, which some targets box; not a
        // type value yet, which no backend boxes.
        TypeData::Scalar(s) => {
            sup == TypeId::ANY_VALUE && !matches!(s, Scalar::Void | Scalar::Type)
        }
        TypeData::Nominal { .. } => match p.types.get(sup) {
            TypeData::Nominal { decl, args } => {
                as_super(p, sub, decl).is_some_and(|found| found == p.types.list(args))
            }
            _ => false,
        },
        _ => false,
    }
}

/// The type parameters' types of a generic declaration, in order.
pub fn type_param_types(p: &Program, decl: DeclId) -> Vec<TypeId> {
    if !p.decls.contains(decl) {
        return Vec::new();
    }
    (p.decls.type_params(decl).iter())
        .map(|&t| p.decls.get(t).ty)
        .collect()
}

/// `ty`, a class's or interface's type, seen as `target`, one of its
/// supertypes or itself: `target`'s type arguments, through the declared
/// supertypes with type arguments put in. `None` if `target` isn't one
/// of them, or if putting them in makes a type the program lacks.
pub fn as_super(p: &Program, ty: TypeId, target: DeclId) -> Option<Vec<TypeId>> {
    let (decl, args) = match p.types.contains(ty).then(|| p.types.get(ty))? {
        TypeData::Nominal { decl, args } => (decl, args),
        // A type parameter, as its bounds.
        TypeData::TypeParam { decl } if p.decls.contains(decl) => {
            return (p.decls.supers(decl).iter()).find_map(|&b| as_super(p, b, target));
        }
        _ => return None,
    };
    let args = p.types.list(args).to_vec();
    let mut seen = vec![(decl, args)];
    let mut next = 0;
    while next < seen.len() {
        let (d, args) = seen[next].clone();
        next += 1;
        if d == target {
            return Some(args);
        }
        if !p.decls.contains(d) {
            continue;
        }
        let from = type_param_types(p, d);
        for &s in p.decls.supers(d) {
            // Each argument put in by itself, so the supertype itself
            // needn't be interned.
            let TypeData::Nominal {
                decl: up,
                args: up_args,
            } = p.types.get(s)
            else {
                continue;
            };
            let up_args: Option<Vec<TypeId>> = (p.types.list(up_args).iter())
                .map(|&a| p.types.substituted(a, &from, &args))
                .collect();
            if let Some(up_args) = up_args {
                if !seen.iter().any(|(seen, _)| *seen == up) {
                    seen.push((up, up_args));
                }
            }
        }
    }
    None
}

/// What a call of `callee`, or a read of a member, binds the type
/// parameters it mentions to, as `(from, to)` for
/// [`TypeTable::substitute`](crate::TypeTable::substitute): the callee's
/// own to `type_args`, or for a constructor, its class's; and a member's
/// class's or interface's to what the receiver's type gives them.
pub fn bindings(
    p: &Program,
    callee: DeclId,
    receiver: Option<TypeId>,
    type_args: &[TypeId],
) -> (Vec<TypeId>, Vec<TypeId>) {
    if !p.decls.contains(callee) {
        return (Vec::new(), Vec::new());
    }
    let d = p.decls.get(callee);
    if d.kind == DeclKind::Constructor {
        return (type_param_types(p, d.owner), type_args.to_vec());
    }
    let mut from = type_param_types(p, callee);
    let mut to = type_args.to_vec();
    to.resize(from.len(), TypeId::NONE);
    if let Some(receiver) = receiver {
        let receiver = p.types.non_null(receiver);
        let formals = type_param_types(p, d.owner);
        if !formals.is_empty() {
            if let Some(args) = as_super(p, receiver, d.owner) {
                from.extend(formals);
                to.extend(args);
            }
        }
    }
    (from, to)
}

/// Whether a call of `callee`, a generic function or method, could leave
/// out its own type arguments in a target that infers them only from
/// the arguments (Rust, C#, C++): whether each of its type parameters
/// appears in some parameter's type. Where one doesn't (`CONTEXT` in
/// `combine<CONTEXT extends Context, T>(x: T): T`), those backends print
/// the call's type arguments, all of them, as `combine::<Context, i32>(1)`
/// (notes/tmpl/nodes.md, "Generic calls"). True for a callee that isn't
/// generic.
pub fn infers_type_args(p: &Program, callee: DeclId) -> bool {
    let formals = type_param_types(p, callee);
    if formals.is_empty() {
        return true;
    }
    let params = p.signature(callee).map_or(&[][..], |s| s.params);
    (formals.iter()).all(|&f| params.iter().any(|&t| p.types.mentions(t, f)))
}

/// The class or interface a type names, if it's one.
pub fn nominal_decl(p: &Program, ty: TypeId) -> Option<DeclId> {
    match p.types.contains(ty).then(|| p.types.get(ty))? {
        TypeData::Nominal { decl, .. } => Some(decl),
        _ => None,
    }
}

/// Whether a method is its class's plain `toString()`, which a backend
/// presents as its host's display string (C#'s `ToString` override,
/// Python's `__str__`, Rust's `Display`, Lua's `__tostring`): a public
/// instance method of a class (not an interface) named `toString`, taking
/// nothing and returning `String`, as Kotlin's `isPlainToString` decides
/// (notes/tmpl/surface.md, "Show").
pub fn is_plain_to_string(p: &Program, method: DeclId) -> bool {
    if !p.decls.contains(method) {
        return false;
    }
    let d = p.decls.get(method);
    let owner_is_class = p.decls.contains(d.owner) && p.decls.get(d.owner).kind == DeclKind::Class;
    d.kind == DeclKind::Method
        && owner_is_class
        && p.name(method) == "toString"
        && !d.facts.contains(Facts::PRIVATE)
        && !d.facts.contains(Facts::PROTECTED)
        && p.signature(method)
            .is_some_and(|s| s.params.is_empty() && s.result == TypeId::STRING)
}

/// A class's or interface's members' declarations, in order, from its
/// node.
pub fn members(p: &Program, class: NodeId) -> Vec<DeclId> {
    match p.nodes.get(class) {
        Node::Class { members, .. } | Node::Interface { members, .. } => p
            .nodes
            .list(members)
            .iter()
            .filter_map(|&m| match p.nodes.get(m) {
                Node::Field { decl }
                | Node::StaticField { decl }
                | Node::Method { decl, .. }
                | Node::AbstractMethod { decl, .. }
                | Node::Function { decl, .. } => Some(decl),
                _ => None,
            })
            .collect(),
        _ => Vec::new(),
    }
}

/// A class's or interface's supertypes, theirs, and so on, breadth-first,
/// each once, not itself.
pub fn ancestors(p: &Program, class: DeclId) -> Vec<DeclId> {
    let mut found = vec![class];
    let mut next = 0;
    while next < found.len() {
        let d = found[next];
        next += 1;
        if !p.decls.contains(d) {
            continue;
        }
        for &s in p.decls.supers(d) {
            if let Some(up) = nominal_decl(p, s) {
                if !found.contains(&up) {
                    found.push(up);
                }
            }
        }
    }
    found.remove(0);
    found
}

/// A `@fun` interface's `apply` method: its one member.
pub fn fun_apply(p: &Program, iface: DeclId) -> Option<DeclId> {
    if !p.decls.contains(iface) || !p.decls.get(iface).facts.contains(Facts::FUN) {
        return None;
    }
    p.decls.ids().find(|&d| {
        p.decls.contains(d)
            && p.decls.get(d).owner == iface
            && p.decls.get(d).kind == DeclKind::Method
            && p.name(d) == "apply"
    })
}

/// The signature a function value of type `ty` has, as a function type:
/// a function type's own, or a `@fun` interface's `apply`'s with the
/// interface's type arguments put in. `None` for other types, or where
/// the signature with its arguments put in isn't interned yet (which
/// [`Builder::fun_signature`](crate::Builder::fun_signature) does).
pub fn fun_signature(p: &Program, ty: TypeId) -> Option<TypeId> {
    match p.types.contains(ty).then(|| p.types.get(ty))? {
        TypeData::Function { .. } => Some(ty),
        TypeData::Nominal { decl, args } => {
            let apply = fun_apply(p, decl)?;
            let from = type_param_types(p, decl);
            let to = p.types.list(args).to_vec();
            p.types.substituted(p.decls.get(apply).ty, &from, &to)
        }
        _ => None,
    }
}

/// Whether `decl` is owned, through its owners, by `owner`.
pub fn owned_by(p: &Program, decl: DeclId, owner: DeclId) -> bool {
    let mut d = decl;
    for _ in 0..p.decls.len() {
        if !p.decls.contains(d) {
            return false;
        }
        let up = p.decls.get(d).owner;
        if up == owner {
            return true;
        }
        d = up;
    }
    false
}

/// What a function literal captures, recomputed: the locals and
/// parameters its body, or a literal in it, reads or assigns that it
/// doesn't own, through its owners, in `DeclId` order. What the literal's
/// declaration lists ([`DeclTable::captures`](crate::DeclTable::captures))
/// must match (rule D5).
pub fn captures(p: &Program, literal: NodeId) -> Vec<DeclId> {
    let Node::FunctionLiteral { decl, .. } = p.nodes.get(literal) else {
        return Vec::new();
    };
    let mut found: Vec<DeclId> = Vec::new();
    walk(p, literal, &mut |id| {
        let local = match p.nodes.get(id) {
            Node::Local { local } | Node::Assign { local, .. } => local,
            _ => return,
        };
        let kind = p.decls.contains(local).then(|| p.decls.get(local).kind);
        let is_local = matches!(kind, Some(DeclKind::Local | DeclKind::Param));
        if is_local && !owned_by(p, local, decl) && !found.contains(&local) {
            found.push(local);
        }
    });
    found.sort();
    found
}

/// The locals and parameters that some function literal captures and
/// that are assigned anywhere after their `let` (by an `Assign`): what
/// targets whose closures copy what they capture (C++, Java, Rust) keep in
/// shared cells, and what Python's nested functions declare `nonlocal`
/// where they assign them.
pub fn assigned_captures(p: &Program) -> HashSet<DeclId> {
    let mut captured: HashSet<DeclId> = HashSet::new();
    let mut assigned: HashSet<DeclId> = HashSet::new();
    let roots = p.modules.iter().flat_map(|m| m.roots());
    for root in roots {
        walk(p, root, &mut |id| match p.nodes.get(id) {
            Node::FunctionLiteral { decl, .. } => {
                captured.extend(p.decls.captures(decl).iter().copied());
            }
            Node::Assign { local, .. } => {
                assigned.insert(local);
            }
            _ => {}
        });
    }
    captured.retain(|d| assigned.contains(d));
    captured
}

/// A parameter's default, one of the `let`s a body starts with (rule N2):
/// `let local = coalesce(param, value)`, where `param` is optional.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ParamDefault {
    /// The `Let`.
    pub stmt: NodeId,
    /// The local the body reads: the parameter's value, or its default.
    pub local: DeclId,
    /// The optional parameter, nullable, which callers pass.
    pub param: DeclId,
    /// The default, which runs only if the parameter is null.
    pub value: NodeId,
}

/// The defaults a function's body starts with, in order: what its
/// optional parameters take where callers pass null or leave them out.
pub fn defaults(p: &Program, body: NodeId) -> Vec<ParamDefault> {
    let mut found = Vec::new();
    let Node::Block { stmts } = p.nodes.get(body) else {
        return found;
    };
    for &stmt in p.nodes.list(stmts) {
        let Node::Let {
            local,
            init: Some(init),
        } = p.nodes.get(stmt)
        else {
            break;
        };
        let Node::Coalesce { left, right } = p.nodes.get(init) else {
            break;
        };
        let Node::Local { local: param } = p.nodes.get(left) else {
            break;
        };
        let d = p.decls.get(param);
        if d.kind != DeclKind::Param || !d.facts.contains(Facts::OPTIONAL) {
            break;
        }
        found.push(ParamDefault {
            stmt,
            local,
            param,
            value: right,
        });
    }
    found
}

/// Local functions in a block that use each other, or themselves: a run
/// of `let`s of function literals, each local not a `var`, may use any of
/// the run's locals in its literal, as a `letrec` (rule R3), and the
/// literals' captures list them. A group is those of a run that such uses
/// connect, where one uses itself or a local whose `let` comes after its
/// own, so the local is used before its `let` finishes: what targets
/// whose closures can't name themselves (Rust, Java, and C++) write as
/// one value with the functions its methods, and Lua declares first.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct FunctionGroup {
    /// The `Let`s, in order.
    pub lets: Vec<NodeId>,
    /// Their locals, in the same order.
    pub members: Vec<DeclId>,
    /// The members used before their `let`s finish: by their own literal,
    /// or an earlier member's.
    pub early: Vec<DeclId>,
    /// What the members' literals capture besides the members, in
    /// `DeclId` order.
    pub captures: Vec<DeclId>,
}

/// A `let` of a function literal, its local not a `var`: its local and
/// the literal's declaration.
pub fn function_let(p: &Program, stmt: NodeId) -> Option<(DeclId, DeclId)> {
    let Node::Let {
        local,
        init: Some(init),
    } = p.nodes.get(stmt)
    else {
        return None;
    };
    let Node::FunctionLiteral { decl, .. } = p.nodes.get(init) else {
        return None;
    };
    let var = p.decls.contains(local) && p.decls.get(local).facts.contains(Facts::VAR);
    (!var).then_some((local, decl))
}

/// The groups of a block's statements, in order ([`FunctionGroup`]).
pub fn function_groups(p: &Program, stmts: &[NodeId]) -> Vec<FunctionGroup> {
    let mut groups = Vec::new();
    let mut i = 0;
    while i < stmts.len() {
        let mut end = i;
        while end < stmts.len() && function_let(p, stmts[end]).is_some() {
            end += 1;
        }
        let run: Vec<(NodeId, DeclId, DeclId)> = stmts[i..end]
            .iter()
            .map(|&s| {
                let (local, lit) = function_let(p, s).unwrap();
                (s, local, lit)
            })
            .collect();
        // Union-find over the run, by the uses between its members.
        let mut parent: Vec<usize> = (0..run.len()).collect();
        fn root(parent: &mut [usize], mut i: usize) -> usize {
            while parent[i] != i {
                parent[i] = parent[parent[i]];
                i = parent[i];
            }
            i
        }
        let mut early = vec![false; run.len()];
        for (a, &(_, _, lit)) in run.iter().enumerate() {
            for &c in p.decls.captures(lit) {
                if let Some(b) = run.iter().position(|&(_, local, _)| local == c) {
                    early[b] |= b >= a;
                    let (ra, rb) = (root(&mut parent, a), root(&mut parent, b));
                    parent[ra] = rb;
                }
            }
        }
        let mut done = vec![false; run.len()];
        for a in 0..run.len() {
            let ra = root(&mut parent, a);
            if done[a] {
                continue;
            }
            let part: Vec<usize> = (a..run.len())
                .filter(|&b| root(&mut parent, b) == ra)
                .collect();
            for &b in &part {
                done[b] = true;
            }
            if !part.iter().any(|&b| early[b]) {
                continue;
            }
            let members: Vec<DeclId> = part.iter().map(|&b| run[b].1).collect();
            let mut captures: Vec<DeclId> = part
                .iter()
                .flat_map(|&b| p.decls.captures(run[b].2).iter().copied())
                .filter(|c| !members.contains(c))
                .collect();
            captures.sort();
            captures.dedup();
            groups.push(FunctionGroup {
                lets: part.iter().map(|&b| run[b].0).collect(),
                early: part
                    .iter()
                    .filter(|&&b| early[b])
                    .map(|&b| run[b].1)
                    .collect(),
                members,
                captures,
            });
        }
        i = end.max(i + 1);
    }
    groups
}

/// The most times any path through `id` sets `field`, as a constructor
/// may set a `let` field twice, a default first and then, in a branch,
/// another (Rust's `let mut` local for it, Java's field not `final`): an `if`'s branch
/// that sets it most, statements one after another adding up, and a loop
/// that sets it at all counted as twice, since it may go round again.
pub fn most_sets(p: &Program, id: NodeId, field: DeclId) -> usize {
    let mut any = false;
    walk(p, id, &mut |n| {
        any |= matches!(p.nodes.get(n), Node::SetField { field: f, .. } if f == field);
    });
    if !any {
        return 0;
    }
    match p.nodes.get(id) {
        Node::SetField {
            field: f, value, ..
        } => usize::from(f == field) + most_sets(p, value, field),
        Node::If { cond, then, els } => {
            let branches = most_sets(p, then, field).max(els.map_or(0, |e| most_sets(p, e, field)));
            most_sets(p, cond, field) + branches
        }
        Node::Block { stmts } => (p.nodes.list(stmts).iter())
            .map(|&s| most_sets(p, s, field))
            .sum(),
        Node::While { .. } => 2,
        // Elsewhere, each set counted, as on one path.
        _ => {
            let mut count = 0;
            walk(p, id, &mut |n| {
                count += usize::from(
                    matches!(p.nodes.get(n), Node::SetField { field: f, .. } if f == field),
                );
            });
            count
        }
    }
}

/// The program's own constructors' parameters, in order, by the
/// constructor: what a `New`'s arguments are passed to.
pub fn constructor_params(p: &Program) -> HashMap<DeclId, Vec<DeclId>> {
    let mut found = HashMap::new();
    for item in p.modules.iter().flat_map(|m| m.all_items()) {
        let Node::Class { members, .. } = p.nodes.get(item) else {
            continue;
        };
        for &m in p.nodes.list(members) {
            if let Node::Method { decl, params, .. } = p.nodes.get(m) {
                if p.decls.get(decl).kind == DeclKind::Constructor {
                    found.insert(decl, param_decls(p, params));
                }
            }
        }
    }
    found
}

fn param_decls(p: &Program, params: crate::nodes::Kids) -> Vec<DeclId> {
    (p.nodes.list(params).iter())
        .filter_map(|&n| match p.nodes.get(n) {
            Node::Param { decl } => Some(decl),
            _ => None,
        })
        .collect()
}

/// The program's own classes that a spread, `{ ...old, a: e }`, may copy
/// with a target's own copy form, skipping the constructor, by their
/// declarations (notes/tmpl/nodes.md, "Spreads"): the `data` ones, whose
/// constructor only stores its parameters, with no `var` field, so the
/// given values, which run first, can't change what a copy then reads.
pub fn copyable_classes(p: &Program) -> HashSet<DeclId> {
    let mut found = HashSet::new();
    for item in p.modules.iter().flat_map(|m| m.all_items()) {
        if let Node::Class { decl, members } = p.nodes.get(item) {
            let var = |&m: &NodeId| match p.nodes.get(m) {
                Node::Field { decl } => p.decls.get(decl).facts.contains(Facts::VAR),
                _ => false,
            };
            if p.decls.get(decl).facts.contains(Facts::DATA)
                && !p.nodes.list(members).iter().any(var)
            {
                found.insert(decl);
            }
        }
    }
    found
}
