//! The translation from tmpl to Rust's output tree, classes and
//! interfaces in `translate/classes.rs`, and nullable values and
//! `orelse` in `translate/nullables.rs`.
//!
//! Module state, as Kotlin's Rust backend keeps it, and safe across
//! threads: a module `let` or `var` that a function or test reads or
//! writes, or that's exported, is a `static` with a getter function of
//! its name, as Kotlin's "toppers" are: a `let` a
//! [`OnceLock`](std::sync::OnceLock) set once by the module's init, a `var` an
//! [`RwLock`](std::sync::RwLock) of an `Option`, `None` until init sets
//! it. Every other module `let` and `var` is a local of the init, which
//! runs once (`INIT_ONCE`). A getter reads its static through
//! [`initialized`](Translator::initialized), which initializes the
//! library first where it isn't yet, so `init` is optional, as Kotlin's
//! since #636. One improvement on Kotlin: a `var` assigned
//! from itself (`count = count + 1`) is read and written under one write
//! lock, so concurrent updates aren't lost, where Kotlin reads and then
//! writes under separate locks. That holds only while the value reads no
//! other module state and calls nothing, which could take another lock
//! or this one again; other assignments evaluate their value first, then
//! take the lock just to store it, as Kotlin's do.
//!
//! What only tests reach (`temper_tmpl::reach`, which the program is
//! partitioned by) is `#[cfg(test)]`: functions, classes and interfaces
//! (all their items), and module values, statics and getters, with the
//! test init, which sets them, in a `#[cfg(test)]` block at the end of
//! the init, in its scope; the `use`s only they need too. A library
//! value that only tests read has its getter only for tests, and one
//! nothing reads has none. What's kept for host code (`@keep`, and
//! `@keepTest` for its tests') but not exported is `#[allow(dead_code)]`,
//! since no Temper code may use it. So a crate builds without tests
//! with no dead code of what tests use, where Kotlin's writes everything
//! for the library, warnings hidden by `#![allow(warnings)]`.
//!
//! Modules: each of the library's modules is a Rust module of its own
//! (the root, `r#mod`), and another module's declarations, or another
//! library's, are named by their paths, as Kotlin's are
//! (`crate::helpers::twice`, `modules::sum`, `temper_std::testing::Test`);
//! another module's `let`s and `var`s by their getters, so a read is
//! current. A module's tests are `#[test]` functions in a `tests` module
//! of its file ([`Translator::tests`]).

mod classes;
mod functions;
mod groups;
pub mod host;
mod nullables;

use crate::NotYet;
use crate::connected::{self, Connection, TypeConnection};
use crate::names::{RAW_KEYWORDS, static_name};
use crate::ops::{self, Spelling};
use crate::tree::{Bin, Block, Expr, Item, Stmt, Unary};
use classes::This;
use std::collections::{BTreeSet, HashMap, HashSet};
use temper_tmpl::analysis::{home, module_refs, module_state, reads_only_itself, walk};
use temper_tmpl::naming::{NameMap, snake_case};
use temper_tmpl::qname::{is_external, library_of};
use temper_tmpl::{
    DeclId, DeclKind, Facts, Module, Node, NodeId, Program, QNames, Scalar, TypeData, TypeId,
};

type R<T> = Result<T, NotYet>;

/// What only tests build.
const CFG_TEST: &str = "#[cfg(test)]";

fn not_yet<T>(what: impl Into<String>) -> R<T> {
    Err(NotYet::new(what))
}

/// What a place wants of the value written there.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Want {
    /// A value of its own: a local that isn't `Copy` is cloned.
    Owned,
    /// A borrow will do, as for a method's receiver or `==`'s operands.
    Borrowed,
    /// Anything that displays as the value: a format string's argument,
    /// where a string literal may stay `&str`.
    Display,
    /// An argument for an `impl ToArcString` parameter, which takes
    /// `&str` as well as `Arc<String>`.
    IntoArc,
}

/// How a callable takes its parameters.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Params {
    /// A function's, or a static one's.
    Free,
    /// A method's, after `&self`.
    Method,
    /// A trait method's, after `&self`, with concrete types, as a trait
    /// object's methods must have: `Arc<String>`, not `impl ToArcString`.
    Trait,
}

/// An enclosing loop.
struct Frame {
    label: DeclId,
    /// A `do … while` with a `continue`, whose body is a labeled block
    /// that a `continue` leaves.
    labeled_body: bool,
    /// The loop's body, where its locals are assigned.
    body: NodeId,
}

pub struct Translator<'a> {
    p: &'a Program,
    names: &'a NameMap,
    qnames: QNames,
    /// Module `let`s kept in statics.
    statics: HashSet<DeclId>,
    /// The locals, parameters, and module `let`s that something reads;
    /// the others, of locals, private module `let`s, and function
    /// literals' parameters, are named with a leading `_`, as Rust's
    /// `unused_variables` wants.
    read: HashSet<DeclId>,
    /// The module `let`s that the library's parts read, and those that
    /// the tests' parts read (`temper_tmpl::reach`): a static's getter is
    /// only for tests where only they read it, since it'd be dead code
    /// outside them, and isn't written where nothing reads it.
    reads: HashSet<DeclId>,
    test_reads: HashSet<DeclId>,
    /// Whether translating what only tests build: a test, or what only
    /// tests reach ([`Translator::for_tests`]), and the `use`s that
    /// needs.
    pub(crate) for_tests: bool,
    test_uses: BTreeSet<&'static str>,
    /// Module `let`s of an erased type, which aren't declared.
    erased: HashSet<DeclId>,
    /// `use`d names, as paths.
    uses: BTreeSet<&'static str>,
    /// Whether a read initializes the library on first use
    /// ([`initialized`](Self::initialized)), outside tests and in them,
    /// so the crate needs `init_on_first_use`.
    pub first_use: bool,
    pub first_use_in_tests: bool,
    /// What the code being translated returns a `Result` failing with, a
    /// declared error type, `Result<T, F>` (core's `Error` for a test
    /// or a module's init, `temper_core::Result`); `None` where it
    /// returns no `Result`.
    fails: Option<TypeId>,
    /// The labels of the `orelse`s whose bodies run in place, in labeled
    /// blocks, innermost last, which an error breaks out of
    /// (`or_else_in_place`).
    orelse: Vec<String>,
    /// The enclosing loops, innermost last.
    loops: Vec<Frame>,
    /// Labels printed, by loop: each loop's, and a labeled body's.
    labels: HashMap<DeclId, String>,
    body_labels: HashMap<DeclId, String>,
    /// The `var` read and written under one lock, and its guard's name.
    guard: Option<(DeclId, String)>,
    /// The function or init block being translated, which says what's
    /// assigned where.
    scope: NodeId,
    /// The module being translated.
    here: DeclId,
    /// Whether in its `tests` module, which names its items `super::…`.
    pub(crate) in_tests: bool,
    /// What `this` is here.
    this: This,
    /// In a constructor before it makes its instance, the locals that
    /// hold fields it has set and reads again.
    ctor_fields: HashMap<DeclId, String>,
    /// Every class and interface, by declaration.
    classes: HashMap<DeclId, NodeId>,
    /// Core's `Error`, the root of declared error types, where the
    /// program has it (`temper_tmpl::declared_errors::root`).
    pub(crate) error_root: Option<DeclId>,
    /// The fields whose getters and setters the module defines.
    getters: HashSet<DeclId>,
    setters: HashSet<DeclId>,
    /// The module's builders, for its `builders` module.
    builders: Vec<Item>,
    /// The classes the module constructs, the interfaces it makes
    /// handles of, and those whose default methods use `clone_boxed`.
    constructed: HashSet<DeclId>,
    wrapped: HashSet<DeclId>,
    boxed: HashSet<DeclId>,
    /// Nullable locals bound by `if let Some(x) = &x`, and whether
    /// they're `Copy`, so that `x` there is the value.
    narrowed: HashMap<DeclId, bool>,
    /// Type parameters written as other types, as an interface's in a
    /// class's `impl` of its trait.
    pub(crate) subst: HashMap<DeclId, TypeId>,
    /// Locals that function literals capture and that are assigned,
    /// which are cells ([`functions`]).
    cells: HashSet<DeclId>,
    /// Function literals and references that can't fail, passed where an
    /// error parameter given `Never<…>` makes the parameter's function
    /// type return a result (RFC 164), with that error: each returns
    /// `Ok`s ([`functions`]).
    ok_results: HashMap<NodeId, TypeId>,
    /// Whether it runs `async` blocks, whose runner the crate's support
    /// module gives (`run_async`).
    pub(crate) runs_async: bool,
    /// Whether only tests, and what only they reach, run `async` blocks.
    pub(crate) runs_async_in_tests: bool,
    /// Whether it awaits, resuming a step on the runner (`awake_upon`),
    /// and whether only tests do.
    pub(crate) awaits: bool,
    pub(crate) awaits_in_tests: bool,
    /// The paths of the modules whose items take host code ([`host`]),
    /// so the crate declares their `_connected` modules.
    pub host_dirs: BTreeSet<String>,
    /// The group of local functions whose method is being translated
    /// ([`groups`]).
    group: Option<groups::GroupHere>,
}

impl<'a> Translator<'a> {
    pub fn new(p: &'a Program, names: &'a NameMap) -> Translator<'a> {
        Translator {
            p,
            names,
            qnames: QNames::derive(p),
            statics: HashSet::new(),
            read: (0..p.nodes.len())
                .filter_map(|i| match p.nodes.get(NodeId(i as u32)) {
                    Node::Local { local } | Node::GetModuleLet { target: local } => Some(local),
                    _ => None,
                })
                .collect(),
            reads: module_reads(p, p.modules.iter().flat_map(library_roots)),
            test_reads: module_reads(p, p.modules.iter().flat_map(Module::test_roots)),
            for_tests: false,
            test_uses: BTreeSet::new(),
            erased: HashSet::new(),
            uses: BTreeSet::new(),
            first_use: false,
            first_use_in_tests: false,
            fails: None,
            orelse: Vec::new(),
            loops: Vec::new(),
            labels: HashMap::new(),
            body_labels: HashMap::new(),
            guard: None,
            scope: NodeId(0),
            here: DeclId::NONE,
            in_tests: false,
            this: This::None,
            ctor_fields: HashMap::new(),
            classes: classes::index(p),
            error_root: temper_tmpl::declared_errors::root(p),
            getters: HashSet::new(),
            setters: HashSet::new(),
            builders: Vec::new(),
            constructed: HashSet::new(),
            wrapped: HashSet::new(),
            boxed: HashSet::new(),
            narrowed: HashMap::new(),
            subst: HashMap::new(),
            cells: temper_tmpl::analysis::assigned_captures(p),
            ok_results: HashMap::new(),
            runs_async: false,
            runs_async_in_tests: false,
            awaits: false,
            awaits_in_tests: false,
            host_dirs: BTreeSet::new(),
            group: None,
        }
    }

    /// A module's path: the root's `crate::r#mod`, another's
    /// `crate::helpers::deep`, and another library's from its crate,
    /// as `modules::helpers` (its root, `modules`); `None` for core's.
    pub fn module_path(&self, module: DeclId) -> Option<String> {
        let p = self.p;
        let d = p.decls.get(module);
        if d.kind == DeclKind::Library {
            return (library_of(p, module) != "core").then(|| library_crate(p, p.name(module)));
        }
        let (start, path) = if d.owner.is_none() {
            let path = p.modules.iter().find(|m| m.decl == module)?.path.as_str();
            if path.is_empty() {
                return Some("crate::r#mod".into());
            }
            ("crate".to_string(), path)
        } else {
            (library_crate(p, p.name(d.owner)), p.name(module))
        };
        let mut out = start;
        for part in path.split('/').filter(|s| !s.is_empty()) {
            out.push_str("::");
            out.push_str(&module_name(part));
        }
        Some(out)
    }

    /// How code here names a module-level declaration: by its name in its
    /// own module (`super::` it from the tests), else by its path.
    fn top_path(&self, decl: DeclId) -> R<String> {
        let name = self.name(decl);
        let from = home(self.p, decl).unwrap_or(DeclId::NONE);
        if from == self.here {
            return Ok(if self.in_tests {
                format!("super::{name}")
            } else {
                name
            });
        }
        match self.module_path(from) {
            Some(path) => Ok(format!("{path}::{name}")),
            None => not_yet(format!("a path for {}", self.qname(decl))),
        }
    }

    /// Whether a declaration is another module's, or another library's
    /// (not core's).
    fn elsewhere(&self, decl: DeclId) -> bool {
        let from = home(self.p, decl).unwrap_or(DeclId::NONE);
        from != self.here && !(is_external(self.p, decl) && library_of(self.p, decl) == "core")
    }

    /// A static's path, `super::` it from the tests.
    fn static_path(&self, decl: DeclId) -> String {
        let stat = static_name(&self.name(decl));
        if self.in_tests {
            format!("super::{stat}")
        } else {
            stat
        }
    }

    /// A node's type.
    /// Whether a type is core's `Error`, the root of error types.
    pub(crate) fn is_error_root(&self, ty: TypeId) -> bool {
        let decl = temper_tmpl::analysis::nominal_decl(self.p, ty);
        decl.is_some() && decl == self.error_root
    }

    pub(crate) fn p_ty(&self, id: NodeId) -> TypeId {
        self.p.nodes.ty(id)
    }

    pub(crate) fn node(&self, id: NodeId) -> Node {
        self.p.nodes.get(id)
    }

    fn name(&self, decl: DeclId) -> String {
        let name = self.names.get(decl);
        // A local, or a function literal's parameter; a function's are
        // read by what the backend writes for it, as forwarders.
        let d = self.p.decls.get(decl);
        // A module `let` nothing reads is a local of the init too
        // (`types/list/operations`' `typed`, read only by `ignore`).
        let local = d.kind == DeclKind::Local
            || (d.kind == DeclKind::ModuleLet
                && !d.facts.contains(Facts::EXPORTED)
                && !temper_tmpl::qname::is_external(self.p, decl))
            || (d.kind == DeclKind::Param
                && !d.owner.is_none()
                && self.p.decls.get(d.owner).kind == DeclKind::Lambda);
        if local && !self.read.contains(&decl) && !name.is_empty() && !name.starts_with('_') {
            // A raw identifier's text, which isn't a keyword after `_`
            // (`r#match` gives `_match`).
            return format!("_{}", name.trim_start_matches("r#"));
        }
        name.to_string()
    }

    fn qname(&self, decl: DeclId) -> String {
        self.qnames
            .get(decl)
            .map(|q| q.to_string())
            .unwrap_or_default()
    }

    fn type_connection(&self, ty: TypeId) -> Option<TypeConnection> {
        match self.p.types.get(ty) {
            TypeData::Nominal { decl, .. } => connected::lookup_type(&self.qname(decl)),
            _ => None,
        }
    }

    /// Whether a node reads a declaration that isn't kept, or calls a
    /// function whose result isn't.
    pub(crate) fn is_erased(&self, id: NodeId) -> bool {
        match self.node(id) {
            Node::GetModuleLet { target } => self.erased.contains(&target),
            Node::CallFunction { callee, .. } => {
                matches!(
                    connected::lookup(&self.qname(callee)),
                    Some(Connection::Erased)
                )
            }
            _ => false,
        }
    }

    fn is_copy(&self, ty: TypeId) -> bool {
        match self.p.types.get(ty) {
            TypeData::Nullable(inner) => self.is_copy(inner),
            // A step's result, `Option<T>`, or its narrowed `T` or `()`.
            TypeData::Nominal { .. } if self.step_result(ty).is_some() => {
                (self.p.types.args(ty).iter()).all(|&t| self.is_copy(t))
            }
            TypeData::Nominal { .. } if self.type_qname(ty) == "core.type GeneratorResult" => {
                (self.p.types.args(ty).iter()).all(|&t| self.is_copy(t))
            }
            // `usize`, `Option<usize>`, and `()`.
            TypeData::Nominal { .. } => matches!(
                self.type_qname(ty).as_str(),
                "core.type StringIndex"
                    | "core.type StringIndexOption"
                    | "core.type NoStringIndex"
                    | "core.type Empty"
            ),
            t => matches!(
                t,
                TypeData::Scalar(
                    Scalar::Void
                        | Scalar::Boolean
                        | Scalar::Int32
                        | Scalar::Int64
                        | Scalar::Float64
                        | Scalar::Type
                )
            ),
        }
    }

    /// `::<A, B>`, a generic call's type arguments, all of them, where
    /// Rust can't infer them from the arguments, as when a type parameter
    /// appears in no parameter's type (`analysis::infers_type_args`); its
    /// error parameters' (RFC 164) where it has any, `::<_, _, Missing>`,
    /// as rustc can't infer one from a closure that `?`s; otherwise
    /// nothing, leaving them to Rust.
    fn turbofish(&mut self, callee: DeclId, type_args: temper_tmpl::TypeArgs) -> R<String> {
        let p = self.p;
        let params = p.decls.type_params(callee).to_vec();
        let failure = |t: DeclId| temper_tmpl::declared_errors::is_error_param(p, t);
        let infers = temper_tmpl::analysis::infers_type_args(p, callee);
        if infers && !params.iter().any(|&t| failure(t)) {
            return Ok(String::new());
        }
        let mut texts = Vec::new();
        for (i, &a) in p.types.args_of(type_args).iter().enumerate() {
            let named = params.get(i).is_none_or(|&t| !infers || failure(t));
            texts.push(if named { self.ty(a)? } else { "_".into() });
        }
        if texts.is_empty() {
            return Ok(String::new());
        }
        Ok(format!("::<{}>", texts.join(", ")))
    }

    /// A call as its place takes it: `call?` where it may fail; where it
    /// can't only because an error parameter (RFC 164) is given
    /// `Never<…>`, its result's value, `temper_core::into_ok(call)`
    /// (`declared_errors::declared_error`); else the call itself.
    fn settled(&self, id: NodeId, call: Expr, may_fail: bool) -> Expr {
        let p = self.p;
        let never = temper_tmpl::declared_errors::declared_error(p, id)
            .is_some_and(|f| p.types.is_never(f));
        match (may_fail, never) {
            (true, _) => call.try_(),
            (false, true) => Expr::path("temper_core::into_ok").call(vec![call]),
            (false, false) => call,
        }
    }

    /// A receiver borrowed ([`Self::handle_ref`]), but `self`, which is a
    /// borrow already.
    fn receiver_ref(&mut self, receiver: NodeId) -> R<Expr> {
        Ok(match self.handle_ref(receiver)? {
            Expr::Unary(Unary::Ref, e) if *e == Expr::path("self") => *e,
            e => e,
        })
    }

    /// A call of core's member whose callback returns a result
    /// (`declared_errors::passes_results`), as [`connected::TRY`] connects it,
    /// its error type named: `temper_core::listed::try_map::<_, _,
    /// Missing>(&xs, &(|x| …))`.
    fn connected_try(
        &mut self,
        decl: DeclId,
        type_args: temper_tmpl::TypeArgs,
        args: &[NodeId],
    ) -> R<Expr> {
        let p = self.p;
        let qname = self.qname(decl);
        let Some(path) = connected::lookup_try(&qname) else {
            return not_yet(format!("{qname} with a callback that may fail"));
        };
        let Some((&receiver, rest)) = args.split_first() else {
            return not_yet(format!("a receiver for {qname}"));
        };
        // The receiver's type parameters, then the member's.
        let owner = p.decls.type_params(p.decls.get(decl).owner).len();
        let mut texts = vec!["_".to_string(); owner];
        for (&t, &a) in p
            .decls
            .type_params(decl)
            .iter()
            .zip(p.types.args_of(type_args))
        {
            texts.push(match temper_tmpl::declared_errors::is_error_param(p, t) {
                true => self.ty(a)?,
                false => "_".into(),
            });
        }
        let mut fargs = vec![self.receiver_ref(receiver)?];
        self.connected_args(decl, rest, &mut fargs)?;
        Ok(Expr::path(format!("{path}::<{}>", texts.join(", "))).call(fargs))
    }

    /// A type as Rust writes it.
    fn ty(&mut self, ty: TypeId) -> R<String> {
        Ok(match self.p.types.get(ty) {
            // `temper_tmpl::nevers` made each `Never<T>` the `T` it stands
            // for, as this profile has no bottom type, but an error
            // parameter's argument, "can't fail" (RFC 164).
            TypeData::Never(_) => {
                self.uses.insert("std::convert::Infallible");
                "Infallible".to_string()
            }
            // Only a signature's result, never a value's (tmpl's T5).
            TypeData::Result { .. } => return not_yet("a result type as a value's"),
            TypeData::Scalar(s) => match s {
                Scalar::Void => "()",
                Scalar::Boolean => "bool",
                Scalar::Int32 => "i32",
                Scalar::Int64 => "i64",
                Scalar::Float64 => "f64",
                Scalar::String => {
                    self.uses.insert("std::sync::Arc");
                    "Arc<String>"
                }
                Scalar::AnyValue => "temper_core::AnyValue",
                Scalar::Type => "std::any::TypeId",
            }
            .to_string(),
            TypeData::Nominal { decl, .. } if self.p.decls.get(decl).facts.contains(Facts::FUN) => {
                // A `@fun` interface is its function type, as Kotlin's.
                self.fn_type(ty)?
            }
            TypeData::Nominal { decl, args } => {
                let mut texts = Vec::new();
                for &a in self.p.types.list(args) {
                    texts.push(self.ty(a)?);
                }
                let path = if self.elsewhere(decl) || self.own(decl) {
                    self.top_path(decl)?
                } else {
                    match self.qname(decl).as_str() {
                        "core.type List" => "temper_core::List".into(),
                        "core.type Listed" => "temper_core::Listed".into(),
                        "core.type ListBuilder" => "temper_core::ListBuilder".into(),
                        "core.type Map" => "temper_core::Map".into(),
                        "core.type Mapped" => "temper_core::Mapped".into(),
                        "core.type MapBuilder" => "temper_core::MapBuilder".into(),
                        // A tuple, as Kotlin's.
                        "core.type Pair" => return Ok(format!("({})", texts.join(", "))),
                        // String indices are byte offsets, and no index is
                        // `None`, as Kotlin's.
                        "core.type StringIndex" => return Ok("usize".into()),
                        "core.type StringIndexOption" => return Ok("Option<usize>".into()),
                        "core.type NoStringIndex" | "core.type Empty" => return Ok("()".into()),
                        "core.type StringBuilder" => {
                            self.uses.insert("std::sync::Arc");
                            return Ok("Arc<std::sync::RwLock<String>>".into());
                        }
                        "core.type Deque" => "temper_core::Deque".into(),
                        "core.type Promise" => "temper_core::Promise".into(),
                        "core.type PromiseBuilder" => "temper_core::PromiseBuilder".into(),
                        "core.type SafeGenerator" => "temper_core::SafeGenerator".into(),
                        "core.type Generator" => "temper_core::Generator".into(),
                        // A generator's step's result: `Some(value)`, or
                        // `None` when it's done, as Kotlin's.
                        "core.type GeneratorResult" => {
                            return Ok(format!("Option<{}>", texts.join(", ")));
                        }
                        // Narrowed by an `is` check: the value itself, or
                        // nothing (`step_result`).
                        "core.type ValueResult" => return Ok(texts.join(", ")),
                        "core.type DoneResult" => return Ok("()".into()),
                        "core.type DenseBitVector" => "temper_core::DenseBitVector".into(),
                        // Core's own errors, the runtime's.
                        "core.type CoreError" => "temper_core::CoreError".into(),
                        "core.type MissingKey" => "temper_core::MissingKey".into(),
                        "core.type ParseError" => "temper_core::ParseError".into(),
                        "core.type InvalidCodePoint" => "temper_core::InvalidCodePoint".into(),
                        "core.type BrokenPromise" => "temper_core::BrokenPromise".into(),
                        "core.type BrokenGenerator" => "temper_core::BrokenGenerator".into(),
                        // Core's root of declared errors, any library's
                        // error, as an error's cause is.
                        "core.type Error" => {
                            self.uses.insert("std::sync::Arc");
                            return Ok("Arc<dyn temper_core::Failure>".into());
                        }
                        q => return not_yet(format!("a type for {q}")),
                    }
                };
                if texts.is_empty() {
                    path
                } else {
                    format!("{path}<{}>", texts.join(", "))
                }
            }
            TypeData::Function { .. } => self.fn_type(ty)?,
            TypeData::Nullable(inner) => format!("Option<{}>", self.ty(inner)?),
            TypeData::TypeParam { decl } => match self.subst.get(&decl) {
                Some(&t) => {
                    let saved = self.subst.remove(&decl);
                    let text = self.ty(t);
                    if let Some(s) = saved {
                        self.subst.insert(decl, s);
                    }
                    text?
                }
                None => self.p.name(decl).to_string(),
            },
        })
    }

    /// A generic declaration's type parameters, declared with their
    /// bounds (`<T: Clone + Send + Sync + 'static>`, as Kotlin's), and as
    /// used (`<T>`); both empty if it has none. A bound that's an
    /// interface is its trait; one that's a class (whose only instances
    /// are its own, since classes aren't extended) converts `Into` it;
    /// `MapKey` is `Eq + Hash`.
    pub(crate) fn type_params(&mut self, decl: DeclId) -> R<(String, String)> {
        let p = self.p;
        let params = p.decls.type_params(decl).to_vec();
        if params.is_empty() {
            return Ok((String::new(), String::new()));
        }
        let mut decls = Vec::new();
        let mut uses = Vec::new();
        for t in params {
            let name = p.name(t).to_string();
            let mut bounds = vec![
                "Clone".to_string(),
                "Send".into(),
                "Sync".into(),
                "'static".into(),
            ];
            for &b in p.decls.supers(t) {
                let Some(bd) = temper_tmpl::analysis::nominal_decl(p, b) else {
                    return not_yet("a type parameter bounded by a non-class");
                };
                if self.qname(bd) == "core.type MapKey" {
                    bounds.push("Eq".into());
                    bounds.push("std::hash::Hash".into());
                    continue;
                }
                // An error parameter's (RFC 164): any error.
                if self.qname(bd) == "core.type Error" {
                    bounds.insert(0, "temper_core::Failure".into());
                    continue;
                }
                if !self.own(bd) && !self.elsewhere(bd) {
                    return not_yet(format!("a type parameter bounded by {}", self.qname(bd)));
                }
                let ty = self.ty(b)?;
                match p.decls.get(bd).kind {
                    DeclKind::Interface => {
                        let (path, args) = match ty.split_once('<') {
                            Some((path, args)) => (path.to_string(), format!("<{args}")),
                            None => (ty, String::new()),
                        };
                        bounds.insert(0, format!("{path}Trait{args}"));
                    }
                    _ => bounds.push(format!("Into<{ty}>")),
                }
            }
            decls.push(format!("{name}: {}", bounds.join(" + ")));
            uses.push(name);
        }
        Ok((
            format!("<{}>", decls.join(", ")),
            format!("<{}>", uses.join(", ")),
        ))
    }

    /// The extra bounds of a declaration's type parameters, beyond
    /// `Clone + Send + Sync + 'static`, as a `where` clause, or empty.
    pub(crate) fn extra_bounds(&mut self, decl: DeclId) -> R<String> {
        let (decls, _) = self.type_params(decl)?;
        let inner = (decls.strip_prefix('<'))
            .and_then(|d| d.strip_suffix('>'))
            .unwrap_or_default();
        let mut clauses = Vec::new();
        for part in split_top(inner) {
            let (name, bounds) = part.split_once(": ").unwrap_or((part, ""));
            let extra: Vec<&str> = bounds
                .split(" + ")
                .filter(|b| !matches!(*b, "Clone" | "Send" | "Sync" | "'static"))
                .collect();
            if !extra.is_empty() {
                clauses.push(format!("{name}: {}", extra.join(" + ")));
            }
        }
        Ok(if clauses.is_empty() {
            String::new()
        } else {
            format!(" where {}", clauses.join(", "))
        })
    }

    /// Whether a type is one of core's with type arguments, or a generic
    /// class's or interface's, whose `let`s Rust may need annotated.
    pub(crate) fn has_args(&self, ty: TypeId) -> bool {
        match self.p.types.get(ty) {
            TypeData::Nominal { args, .. } => !self.p.types.list(args).is_empty(),
            TypeData::Nullable(inner) => self.has_args(inner),
            _ => false,
        }
    }

    /// The qname of a type's class or interface, if it has one.
    pub(crate) fn type_qname(&self, ty: TypeId) -> String {
        match self.p.types.get(ty) {
            TypeData::Nominal { decl, .. } => self.qname(decl),
            _ => String::new(),
        }
    }

    /// `Arc::new(e)`.
    pub(crate) fn arc(&mut self, e: Expr) -> Expr {
        self.uses.insert("std::sync::Arc");
        Expr::path("Arc::new").call(vec![e])
    }

    /// The module's items, and its `use`s first.
    pub fn module(&mut self, module: &Module) -> R<Vec<Item>> {
        let p = self.p;
        self.here = module.decl;
        self.uses.clear();
        self.test_uses.clear();
        // Which module `let`s are erased, and which are statics: those
        // that functions and tests use, and the library's that the test
        // init reads, whose init locals would be unused without tests.
        let mut state = module_state(p, module);
        for d in Translator::class_refs(p, module) {
            if p.decls.get(d).kind == DeclKind::ModuleLet {
                state.used_by_functions.insert(d);
            }
        }
        self.getters.clear();
        self.setters.clear();
        self.builders.clear();
        self.constructed.clear();
        self.wrapped.clear();
        self.boxed.clear();
        self.plan_accessors(module);
        for &test in &module.tests {
            for d in module_refs(p, test) {
                if p.decls.get(d).kind == DeclKind::ModuleLet {
                    state.used_by_functions.insert(d);
                }
            }
        }
        // Those this backend takes from host code.
        let mut host = HashSet::new();
        for item in module.all_items() {
            match p.nodes.get(item) {
                Node::ModuleLet { decl } => {
                    if self.type_connection(p.decls.get(decl).ty) == Some(TypeConnection::Erased) {
                        self.erased.insert(decl);
                    } else if state.used_by_functions.contains(&decl)
                        || p.decls.get(decl).facts.contains(Facts::EXPORTED)
                        || !state.initialized_at_top.contains(&decl)
                        || (self.test_reads.contains(&decl) && module.items.contains(&item))
                    {
                        self.statics.insert(decl);
                    }
                }
                Node::Function { decl, .. }
                | Node::BodilessFunction { decl, .. }
                | Node::Class { decl, .. }
                    if self.takes_host(module, decl)? =>
                {
                    host.insert(decl);
                }
                // std's, which its support code has (`send_request`).
                Node::BodilessFunction { decl, .. }
                    if temper_tmpl::qname::by_connection(
                        p,
                        decl,
                        crate::connected::connects_std,
                    ) => {}
                Node::BodilessFunction { decl, .. } => {
                    return not_yet(format!("a connected function, {}", p.name(decl)));
                }
                _ => {}
            }
        }

        let init = self.init(module.init, module.test_init)?;
        let mut items = vec![init];
        for item in module.all_items() {
            let tests = module.test_items.contains(&item);
            let made = match tests {
                true => self.for_tests(|t| t.item(module, item, &host))?,
                false => self.item(module, item, &host)?,
            };
            let attrs = self.item_attrs(item, tests);
            items.extend(made.into_iter().map(|made| attributed(&attrs, made)));
        }
        if !self.builders.is_empty() {
            let mut inner = vec![Item::Use("super::*".into())];
            inner.append(&mut self.builders);
            items.push(Item::InlineMod {
                vis: "pub",
                name: "builders".into(),
                items: inner,
            });
        }
        // The `use`s, and those only tests build use.
        let mut uses = use_items(&self.uses);
        let test_uses = use_items(&self.test_uses.difference(&self.uses).copied().collect());
        uses.extend(test_uses.into_iter().map(|u| attributed(&[CFG_TEST], u)));
        uses.extend(items);
        let tests = self.tests(module)?;
        uses.extend(tests);
        Ok(uses)
    }

    /// What a module's item is written as.
    fn item(&mut self, module: &Module, item: NodeId, host: &HashSet<DeclId>) -> R<Vec<Item>> {
        Ok(match self.p.nodes.get(item) {
            Node::Function { decl, params, .. } | Node::BodilessFunction { decl, params }
                if host.contains(&decl) =>
            {
                vec![self.host_wrapper(module, item, decl, params)?]
            }
            Node::Class { decl, .. } if host.contains(&decl) => {
                vec![self.host_class(module, decl)]
            }
            Node::Function { .. } => vec![self.function(item)?],
            Node::ModuleLet { decl } if self.statics.contains(&decl) => self.static_items(decl)?,
            // A `@fun` interface is only its function type.
            Node::Interface { decl, .. } if self.p.decls.get(decl).facts.contains(Facts::FUN) => {
                Vec::new()
            }
            Node::Class { .. } => self.class(item)?,
            Node::Interface { .. } => self.interface(item)?,
            _ => Vec::new(),
        })
    }

    /// `f`'s result, translating what only tests build, whose `use`s
    /// are only for tests too where nothing else needs them.
    fn for_tests<T>(&mut self, f: impl FnOnce(&mut Self) -> T) -> T {
        let outer = std::mem::take(&mut self.uses);
        self.for_tests = true;
        let made = f(self);
        self.for_tests = false;
        let inner = std::mem::replace(&mut self.uses, outer);
        self.test_uses.extend(inner);
        made
    }

    /// The attributes of what an item is written as: `#[cfg(test)]` for
    /// what only tests reach (`temper_tmpl::reach`), and
    /// `#[allow(dead_code)]` for what's kept for host code (`@keep`, and
    /// `@keepTest` for the tests'), which Temper code may not use, unless
    /// it's exported.
    fn item_attrs(&self, item: NodeId, tests: bool) -> Vec<&'static str> {
        let decl = match self.p.nodes.get(item) {
            Node::Function { decl, .. }
            | Node::BodilessFunction { decl, .. }
            | Node::ModuleLet { decl }
            | Node::Class { decl, .. }
            | Node::Interface { decl, .. } => decl,
            _ => return Vec::new(),
        };
        let facts = self.p.decls.get(decl).facts;
        let mut attrs = Vec::new();
        if tests {
            attrs.push(CFG_TEST);
        }
        let kept = facts.contains(Facts::KEEP) || facts.contains(Facts::KEEP_TEST);
        if kept && !facts.contains(Facts::EXPORTED) {
            attrs.push("#[allow(dead_code)]");
        }
        attrs
    }

    /// A module's tests, as Kotlin's: each a `#[test]` function that
    /// initializes the library and std, makes std's `Test`, starts the
    /// test's async work on the library's runner (`TestWork::begin`),
    /// runs the test's body, which may fail, its error winning while
    /// its work still runs, finishes the async work it started, panicking with what
    /// failed there, and ends with `soft_fail_to_hard`, which fails it if
    /// the `Test` has errors, then `Ok(())`, unless the body never
    /// finishes. Named for the test, in
    /// snake case, ASCII letters, digits, and `_` only.
    fn tests(&mut self, module: &Module) -> R<Option<Item>> {
        if module.tests.is_empty() {
            return Ok(None);
        }
        let p = self.p;
        let outer = std::mem::take(&mut self.uses);
        self.in_tests = true;
        let mut tests = Vec::new();
        let mut used = BTreeSet::new();
        for &test in &module.tests {
            let Node::Test {
                name, params, body, ..
            } = p.nodes.get(test)
            else {
                continue;
            };
            let [param] = p.nodes.list(params)[..] else {
                return not_yet("a test without one parameter");
            };
            let Node::Param { decl: param } = p.nodes.get(param) else {
                return not_yet("a test's parameter");
            };
            let class = match p.types.get(p.decls.get(param).ty) {
                TypeData::Nominal { decl, .. } if self.elsewhere(decl) => self.top_path(decl)?,
                _ => return not_yet("a test's parameter that isn't std's Test"),
            };
            let std = class.split("::").next().unwrap_or_default().to_string();
            let fails = self.core_error();
            self.begin(test, fails);
            let mut stmts = vec![
                Stmt::Expr(
                    Expr::path("crate::init")
                        .call(vec![Expr::path("None")])
                        .try_(),
                ),
                Stmt::Expr(
                    Expr::path(format!("{std}::init"))
                        .call(vec![Expr::path("None")])
                        .try_(),
                ),
            ];
            let mut run = Vec::new();
            self.block_into(body, &mut run)?;
            // No `soft_fail_to_hard` after a body that never finishes, as
            // one ending in `panic()` (rustc's `unreachable_code`), so
            // the `Test` may go unread (`unused_variables`).
            let finishes = !crate::tree::diverges(&run);
            let var = match finishes || self.read.contains(&param) {
                true => self.name(param),
                false => format!("_{}", self.name(param)),
            };
            stmts.push(Stmt::Let {
                name: var.clone(),
                mutable: false,
                ty: None,
                value: Some(Expr::path(format!("{class}::new")).call(Vec::new())),
            });
            let work = if finishes { "work" } else { "_work" };
            let runner = Expr::path("crate::support::config").call(Vec::new());
            let runner = runner.method("runner", Vec::new());
            stmts.push(Stmt::Let {
                name: work.into(),
                mutable: false,
                ty: None,
                value: Some(Expr::path("temper_core::TestWork::begin").call(vec![runner])),
            });
            stmts.extend(run);
            if finishes {
                let finish = Expr::path(work).method("finish", Vec::new());
                stmts.push(Stmt::Expr(finish));
                let soft_fail = Expr::path(var).method("soft_fail_to_hard", Vec::new());
                stmts.push(Stmt::Expr(soft_fail));
            }
            let tail = finishes.then(|| Expr::path("Ok").call(vec![Expr::tuple(Vec::new())]));
            let name = test_name(p.strings.get(name), &mut used);
            tests.push((name, Block::new(stmts, tail)));
        }
        self.in_tests = false;
        let inner = std::mem::replace(&mut self.uses, outer);
        Ok(Some(Item::Tests {
            uses: inner.iter().map(|u| u.to_string()).collect(),
            tests,
        }))
    }

    fn vis(&self, decl: DeclId) -> &'static str {
        if self.p.decls.get(decl).facts.contains(Facts::EXPORTED) {
            "pub"
        } else {
            "pub(crate)"
        }
    }

    /// `vis`, with clippy's `extra_unused_type_parameters` allowed where
    /// a type parameter of function or method `decl` appears in neither
    /// its parameters' types nor its result's, as Temper allows
    /// (`CONTEXT` in `combine<CONTEXT extends Context, T>(x: T): T`),
    /// which its calls spell out ([`Translator::turbofish`]).
    pub(crate) fn allow_unused_type_params(&self, decl: DeclId, vis: &'static str) -> &'static str {
        let p = self.p;
        let Some(sig) = p.signature(decl) else {
            return vis;
        };
        let unused = (temper_tmpl::analysis::type_param_types(p, decl).iter()).any(|&f| {
            !p.types.mentions(sig.pass, f) && !sig.params.iter().any(|&t| p.types.mentions(t, f))
        });
        match (unused, vis) {
            (true, "pub") => "#[allow(clippy::extra_unused_type_parameters)]\npub",
            (true, "pub(crate)") => "#[allow(clippy::extra_unused_type_parameters)]\npub(crate)",
            (true, "") => "#[allow(clippy::extra_unused_type_parameters)]\n",
            _ => vis,
        }
    }

    /// A module `let`'s static and its getter: only for tests where only
    /// they read it, and none where nothing reads it, as when only
    /// functions write it.
    fn static_items(&mut self, decl: DeclId) -> R<Vec<Item>> {
        let d = self.p.decls.get(decl);
        let ty = self.ty(d.ty)?;
        let name = self.name(decl);
        let stat = static_name(&name);
        let var = d.facts.contains(Facts::VAR);
        let (static_ty, value) = if var {
            self.uses.insert("std::sync::RwLock");
            (
                format!("RwLock<Option<{ty}>>"),
                Expr::path("RwLock::new").call(vec![Expr::path("None")]),
            )
        } else {
            self.uses.insert("std::sync::OnceLock");
            (
                format!("OnceLock<{ty}>"),
                Expr::path("OnceLock::new").call(Vec::new()),
            )
        };
        let mut items = vec![Item::Static {
            vis: "",
            name: stat.clone(),
            ty: static_ty,
            value,
        }];
        let tests_only = if d.facts.contains(Facts::EXPORTED) || self.reads.contains(&decl) {
            false
        } else if self.test_reads.contains(&decl) {
            true
        } else {
            return Ok(items);
        };
        let copy = self.is_copy(d.ty);
        let read = if var {
            let read = Expr::path(&stat)
                .method("read", Vec::new())
                .method("unwrap", Vec::new());
            let read = if copy {
                Expr::unary(Unary::Deref, read)
            } else {
                read.method("clone", Vec::new())
            };
            self.initialized(Block::of(read), tests_only)
        } else {
            let get = self.initialized(
                Block::of(Expr::path(&stat).method("get", Vec::new())),
                tests_only,
            );
            if copy {
                Expr::unary(Unary::Deref, get)
            } else {
                get.method("clone", Vec::new())
            }
        };
        let getter = Item::Fn {
            vis: self.vis(decl),
            name,
            params: Vec::new(),
            result: Some(ty),
            body: Block::of(read),
        };
        // What only tests reach is all for tests already.
        items.push(match tests_only && !self.for_tests {
            true => attributed(&[CFG_TEST], getter),
            false => getter,
        });
        Ok(items)
    }

    /// `crate::support::initialized(|| read)`: the value `read` gives, an
    /// `Option`, through `temper_core::initialized(|| read,
    /// crate::init_on_first_use)`, which initializes the library first
    /// where it's `None`, as Kotlin's, so users needn't call `init`; a
    /// read while the library initializes, of a value not set yet,
    /// panics. Where Kotlin's reads a value as soon as it's set, the
    /// crate's `initialized` waits for the library's whole init on other
    /// threads (`lib.rs`, `SUPPORT_FIRST`). `test` if only tests read it.
    pub(crate) fn initialized(&mut self, read: Block, test: bool) -> Expr {
        match test || self.in_tests || self.for_tests {
            true => self.first_use_in_tests = true,
            false => self.first_use = true,
        }
        Expr::path("crate::support::initialized").call(vec![Expr::Closure(Vec::new(), read)])
    }

    /// `init`, which runs the init block once, as Kotlin's, and then,
    /// in a block only tests build, the test init, which continues it in
    /// its scope (`temper_tmpl::reach`), unless the init never finishes.
    fn init(&mut self, block: NodeId, test_init: Option<NodeId>) -> R<Item> {
        self.uses.insert("std::sync::OnceLock");
        let fails = self.core_error();
        self.begin(block, fails);
        let mut stmts = Vec::new();
        self.block_into(block, &mut stmts)?;
        if let Some(test_init) = test_init.filter(|_| !crate::tree::diverges(&stmts)) {
            let mut tests = Vec::new();
            self.for_tests(|t| t.block_into(test_init, &mut tests))?;
            stmts.push(Stmt::TestsOnly(Block::new(tests, None)));
        }
        let result = "temper_core::Result<()>".to_string();
        // No `Ok(())` after an init that fails on every path (rustc's
        // `unreachable_code`), as `semantics/broken`'s.
        let tail = match stmts.last() {
            _ if !crate::tree::diverges(&stmts) => Some(Expr::path("()").ok()),
            // `Err(…)` as its value, not `return Err(…);` (clippy's
            // `needless_return`).
            Some(Stmt::Return(Some(_))) => match stmts.pop() {
                Some(Stmt::Return(e)) => e,
                _ => None,
            },
            _ => None,
        };
        let run = Expr::Closure(Vec::new(), Block::new(stmts, tail));
        let call = Expr::path("INIT_ONCE")
            .method("get_or_init", vec![run])
            .method("clone", Vec::new());
        Ok(Item::Fn {
            vis: "pub(crate)",
            name: "init".into(),
            params: Vec::new(),
            result: Some(result.clone()),
            body: Block::new(
                vec![Stmt::Static {
                    name: "INIT_ONCE".into(),
                    ty: format!("OnceLock<{result}>"),
                    value: Expr::path("OnceLock::new").call(Vec::new()),
                }],
                Some(call),
            ),
        })
    }

    /// Starts translating a function or the init block.
    fn begin(&mut self, scope: NodeId, fails: Option<TypeId>) {
        self.scope = scope;
        self.fails = fails;
        self.orelse.clear();
        self.loops.clear();
        self.labels.clear();
        self.body_labels.clear();
        self.plan_labels(scope);
    }

    fn function(&mut self, item: NodeId) -> R<Item> {
        let Node::Function { decl, params, body } = self.p.nodes.get(item) else {
            return not_yet("a function that isn't one");
        };
        let (params, result, body) = self.callable(item, decl, params, body, Params::Free)?;
        let (generics, _) = self.type_params(decl)?;
        Ok(Item::Fn {
            vis: self.allow_unused_type_params(decl, self.vis(decl)),
            name: format!("{}{generics}", self.name(decl)),
            params,
            result,
            body,
        })
    }

    /// A function's or method's parameters, written out, result, and
    /// body.
    pub(crate) fn callable(
        &mut self,
        item: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: NodeId,
        style: Params,
    ) -> R<(Vec<String>, Option<String>, Block)> {
        let p = self.p;
        let Some(sig) = p.signature(decl) else {
            return not_yet("a function without a signature");
        };
        self.begin(item, sig.fail);
        let mut ps = Vec::new();
        if style != Params::Free {
            ps.push("&self".to_string());
        }
        let mut stmts = Vec::new();
        self.params_into(item, params, style, &mut ps, &mut stmts)?;
        self.block_into(body, &mut stmts)?;
        let void = sig.pass == TypeId::VOID;
        let result = if void { None } else { Some(self.ty(sig.pass)?) };
        let (result, body) = self.finish_body(stmts, result, void)?;
        Ok((ps, result, body))
    }

    /// A `Result` of `pass` (`()` for none) failing with `fail`,
    /// `Result<T, F>`, with the declared error type's own type (RFC
    /// 164).
    pub(crate) fn result_of(&mut self, pass: Option<String>, fail: TypeId) -> R<String> {
        let pass = pass.unwrap_or_else(|| "()".into());
        Ok(format!("Result<{pass}, {}>", self.error_ty(fail)?))
    }

    /// Core's `Error`, what a test and a module's init may fail with, as
    /// `temper_core::Result` does; `None` in a program without it, where
    /// nothing fails.
    fn core_error(&self) -> Option<TypeId> {
        self.error_root.map(|d| self.p.decls.get(d).ty)
    }

    /// A declared error type, as a `Result`'s error: an error class's
    /// struct or a sealed error interface's enum, the library's roots
    /// among them, or core's `Error`, `Arc<dyn temper_core::Failure>`, for
    /// a handler's body failing with several libraries' (only a handler's,
    /// as a signature can't fail with `Error`), which each error type
    /// converts into by `From`; an error parameter (RFC 164), and
    /// `Infallible` for one given `Never<…>`, "can't fail".
    pub(crate) fn error_ty(&mut self, fail: TypeId) -> R<String> {
        let p = self.p;
        let root = temper_tmpl::declared_errors::root(p);
        if !temper_tmpl::declared_errors::is_error_type(p, root, fail) && !p.types.is_never(fail) {
            return not_yet(format!("{} as an error type", p.show_type(fail)));
        }
        self.ty(fail)
    }

    /// Parameters written out, and the statements that make a string's
    /// an `Arc<String>`.
    pub(crate) fn params_into(
        &mut self,
        item: NodeId,
        params: temper_tmpl::Kids,
        style: Params,
        ps: &mut Vec<String>,
        stmts: &mut Vec<Stmt>,
    ) -> R<()> {
        let p = self.p;
        for &param in p.nodes.list(params) {
            let Node::Param { decl: d } = p.nodes.get(param) else {
                continue;
            };
            let name = self.name(d);
            let ty = p.decls.get(d).ty;
            let mutable = self.assigned(item, d) >= 1;
            if ty == TypeId::STRING && style != Params::Trait {
                // As Kotlin's: callers may pass `&str` or `Arc<String>`.
                self.uses.insert("temper_core::ToArcString");
                ps.push(format!("{name}: impl ToArcString"));
                stmts.push(Stmt::Let {
                    name: name.clone(),
                    mutable,
                    ty: None,
                    value: Some(Expr::path(&name).method("to_arc_string", Vec::new())),
                });
            } else if self.is_nullable_string(ty) && style != Params::Trait {
                // As Kotlin's: `Option<impl ToArcString>`, mapped.
                self.uses.insert("temper_core::ToArcString");
                ps.push(format!("{name}: Option<impl ToArcString>"));
                let to_arc = Expr::Closure(
                    vec!["x".into()],
                    Block::of(Expr::path("x").method("to_arc_string", Vec::new())),
                );
                stmts.push(Stmt::Let {
                    name: name.clone(),
                    mutable,
                    ty: None,
                    value: Some(Expr::path(&name).method("map", vec![to_arc])),
                });
            } else if let (Some(conv), false) = (self.list_param(ty), style == Params::Trait) {
                // As Kotlin's: `impl temper_core::ToList<T>`, converted.
                let elem = self.ty(self.p.types.args(ty)[0])?;
                let (to, method) = conv;
                ps.push(format!("{name}: impl temper_core::{to}<{elem}>"));
                stmts.push(Stmt::Let {
                    name: name.clone(),
                    mutable,
                    ty: None,
                    value: Some(Expr::path(&name).method(method, Vec::new())),
                });
            } else {
                let m = if mutable { "mut " } else { "" };
                ps.push(format!("{m}{name}: {}", self.ty(ty)?));
            }
        }
        Ok(())
    }

    /// For a parameter of core's `List` or `Listed`, the trait it takes
    /// instead and its conversion, as Kotlin's.
    pub(crate) fn list_param(&self, ty: TypeId) -> Option<(&'static str, &'static str)> {
        match self.type_qname(ty).as_str() {
            "core.type List" => Some(("ToList", "to_list")),
            "core.type Listed" => Some(("ToListed", "to_listed")),
            _ => None,
        }
    }

    /// A body's statements as a function's block, ending in its value
    /// where it can, and its result type, a `Result` if it bubbles.
    pub(crate) fn finish_body(
        &mut self,
        mut stmts: Vec<Stmt>,
        mut result: Option<String>,
        void: bool,
    ) -> R<(Option<String>, Block)> {
        let mut tail = tail_of(&mut stmts);
        if let Some(Stmt::Return(None)) = stmts.last() {
            stmts.pop();
        }
        // `let x = e; x` is `e`, as clippy wants.
        if let (
            Some(Stmt::Let {
                name,
                value: Some(_),
                ..
            }),
            Some(Expr::Path(read)),
        ) = (stmts.last(), &tail)
        {
            if name == read {
                if let Some(Stmt::Let { value, .. }) = stmts.pop() {
                    tail = value;
                }
            }
        }
        if let Some(fail) = self.fails {
            result = Some(self.result_of(result, fail)?);
            // No `Ok(())` after statements that never finish, as a call
            // of a `Never<Void>` method, then `panic!()` (rustc's
            // `unreachable_code`).
            if void && tail.is_none() && !crate::tree::diverges(&stmts) {
                tail = Some(Expr::path("()").ok());
            }
        }
        Ok((result, Block::new(stmts, tail)))
    }

    /// How many times `decl` may be assigned on one path through `id`: 0,
    /// 1, or 2 for more, or for any assignment in a loop.
    fn assigned(&self, id: NodeId, decl: DeclId) -> u32 {
        let p = self.p;
        let sum = |kids: &[NodeId]| {
            kids.iter()
                .map(|&k| self.assigned(k, decl))
                .sum::<u32>()
                .min(2)
        };
        match p.nodes.get(id) {
            Node::Assign { local: d, value } | Node::SetModuleLet { target: d, value } => {
                (u32::from(d == decl) + self.assigned(value, decl)).min(2)
            }
            Node::If { cond, then, els } => {
                let branches = self
                    .assigned(then, decl)
                    .max(els.map_or(0, |e| self.assigned(e, decl)));
                (self.assigned(cond, decl) + branches).min(2)
            }
            Node::Switch { scrutinee, cases } => {
                let branches = (p.nodes.list(cases).iter())
                    .map(|&c| self.assigned(c, decl))
                    .max()
                    .unwrap_or(0);
                (self.assigned(scrutinee, decl) + branches).min(2)
            }
            Node::IfValue { cond, then, els } => {
                let branches = self.assigned(then, decl).max(self.assigned(els, decl));
                (self.assigned(cond, decl) + branches).min(2)
            }
            Node::While { .. } | Node::DoWhile { .. } => {
                if sum(p.nodes.kids(id)) > 0 {
                    2
                } else {
                    0
                }
            }
            _ => sum(p.nodes.kids(id)),
        }
    }

    /// Whether a local needs `mut`: assigned at all after its
    /// initializer, or, without one, more than once on some path.
    fn is_mut(&self, decl: DeclId, initialized: bool) -> bool {
        // A local declared in a loop's body is a new one each time round,
        // so only that body's assignments count.
        let scope = self.loops.last().map_or(self.scope, |f| f.body);
        let n = self.assigned(scope, decl);
        if initialized { n >= 1 } else { n >= 2 }
    }

    /// Whether a jump to `label` from here needs it: a loop's, unless
    /// it's the innermost loop's and that loop's body isn't a labeled
    /// block, inside which Rust allows only labeled jumps.
    fn jump_needs_label(loops: &[Frame], label: DeclId) -> bool {
        match loops.last() {
            Some(f) => f.label != label || f.labeled_body,
            None => true,
        }
    }

    /// Finds which loops need labels, and the labels of `do … while`
    /// bodies that a `continue` leaves.
    fn plan_labels(&mut self, scope: NodeId) {
        let p = self.p;
        let mut loops: Vec<Frame> = Vec::new();
        let mut labeled: Vec<DeclId> = Vec::new();
        let mut bodies: Vec<DeclId> = Vec::new();
        fn visit(
            p: &Program,
            id: NodeId,
            loops: &mut Vec<Frame>,
            labeled: &mut Vec<DeclId>,
            bodies: &mut Vec<DeclId>,
        ) {
            match p.nodes.get(id) {
                Node::While { label, .. } | Node::DoWhile { label, .. } => {
                    let dw = matches!(p.nodes.get(id), Node::DoWhile { .. });
                    let labeled_body = dw && continues(p, id, label);
                    if labeled_body {
                        bodies.push(label);
                    }
                    loops.push(Frame {
                        label,
                        labeled_body,
                        body: id,
                    });
                    for &kid in p.nodes.kids(id) {
                        visit(p, kid, loops, labeled, bodies);
                    }
                    loops.pop();
                }
                Node::Break { label } | Node::Continue { label } => {
                    let to_body = matches!(p.nodes.get(id), Node::Continue { .. })
                        && loops.iter().any(|f| f.label == label && f.labeled_body);
                    if !to_body
                        && Translator::jump_needs_label(loops, label)
                        && !labeled.contains(&label)
                    {
                        labeled.push(label);
                    }
                }
                _ => {
                    for &kid in p.nodes.kids(id) {
                        visit(p, kid, loops, labeled, bodies);
                    }
                }
            }
        }
        visit(p, scope, &mut loops, &mut labeled, &mut bodies);
        // A label's source name, else `'outer`, or for a body `'body`;
        // numbered only if taken.
        let mut taken: HashSet<String> = HashSet::new();
        let mut name = |base: String| {
            let base = if RAW_KEYWORDS.contains(&base.as_str()) || base == "static" {
                format!("{base}_")
            } else {
                base
            };
            let name = (1..)
                .map(|n| match n {
                    1 => format!("'{base}"),
                    n => format!("'{base}{n}"),
                })
                .find(|n| !taken.contains(n))
                .unwrap_or_default();
            taken.insert(name.clone());
            name
        };
        for l in labeled {
            let d = p.decls.get(l);
            let base = if d.facts.contains(Facts::COMPILER_MADE) || p.name(l).is_empty() {
                "outer".to_string()
            } else {
                snake_case(p.name(l))
            };
            self.labels.insert(l, name(base));
        }
        for l in bodies {
            self.body_labels.insert(l, name("body".into()));
        }
    }

    fn block_into(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        match self.p.nodes.get(id) {
            Node::Block { stmts } => {
                let stmts = self.p.nodes.list(stmts);
                let groups = self.groups_of(stmts);
                let grouped: Vec<NodeId> = groups.values().flat_map(|g| g.lets.clone()).collect();
                let mut i = 0;
                while i < stmts.len() {
                    if let Some(group) = groups.get(&stmts[i]) {
                        self.group(group, &stmts[i + 1..], out)?;
                        i += 1;
                        continue;
                    }
                    if grouped.contains(&stmts[i]) {
                        i += 1;
                        continue;
                    }
                    if let Some(value) = stmts.get(i + 1).and_then(|&n| self.late_init(stmts[i], n))
                    {
                        self.late_let(stmts[i], value, out)?;
                        i += 2;
                        continue;
                    }
                    if let Some(&next) = stmts.get(i + 1) {
                        if self.late_if(stmts[i], next, out)? {
                            if crate::tree::diverges(out) {
                                break;
                            }
                            i += 2;
                            continue;
                        }
                    }
                    if let Some(j) = self.late_if_past(stmts, i, &grouped, &groups) {
                        // The statements between first, which don't use
                        // the local, as a handler's holders and the handler.
                        let mut between = Vec::new();
                        for &s in &stmts[i + 1..j] {
                            self.stmt(s, &mut between)?;
                        }
                        let mut made = Vec::new();
                        self.late_if(stmts[i], stmts[j], &mut made)?;
                        if let Some(Stmt::Let { value: None, .. }) = made.first() {
                            out.push(made.remove(0));
                        }
                        out.extend(between);
                        out.extend(made);
                        if crate::tree::diverges(out) {
                            break;
                        }
                        i = j + 1;
                        continue;
                    }
                    self.stmt(stmts[i], out)?;
                    // What follows a `return` or `panic!()`, as a
                    // connected call may be written, can't run, and rustc
                    // warns of it (`unreachable_code`).
                    if crate::tree::diverges(out) {
                        break;
                    }
                    i += 1;
                }
                Ok(())
            }
            _ => self.stmt(id, out),
        }
    }

    /// The value of an assignment, `next`, that initializes the local
    /// `stmt` declares without a value, right after it, and doesn't read
    /// it: `let c: i32; c = 1;` is `let c: i32 = 1;`, as clippy's
    /// `needless_late_init` wants.
    fn late_init(&self, stmt: NodeId, next: NodeId) -> Option<NodeId> {
        let p = self.p;
        let Node::Let { local, init: None } = p.nodes.get(stmt) else {
            return None;
        };
        let Node::Assign {
            local: target,
            value,
        } = p.nodes.get(next)
        else {
            return None;
        };
        if target != local || self.is_cell(local) {
            return None;
        }
        let mut reads = false;
        temper_tmpl::analysis::walk(p, value, &mut |n| {
            reads |= matches!(p.nodes.get(n), Node::Local { local: l } if l == local);
        });
        (!reads).then_some(value)
    }

    /// `let x; if (c) { …; x = a; } else { …; return; }`, as a value that
    /// may jump becomes in tmpl, as `let x = if c { …; a } else { …;
    /// return };`, if each branch assigns `x` last, once, or never
    /// finishes, and nothing assigns it after, as clippy's
    /// `needless_late_init` wants (`if let` and a `mut` local it leaves
    /// alone). Whether it wrote the two.
    fn late_if(&mut self, stmt: NodeId, next: NodeId, out: &mut Vec<Stmt>) -> R<bool> {
        let p = self.p;
        let (Node::Let { local, init: None }, Node::If { .. }) =
            (p.nodes.get(stmt), p.nodes.get(next))
        else {
            return Ok(false);
        };
        if self.is_cell(local) || self.assigned(next, local) != 1 || self.is_mut(local, false) {
            return Ok(false);
        }
        let mut made = Vec::new();
        self.stmt(stmt, &mut made)?;
        self.stmt(next, &mut made)?;
        let (
            [
                Stmt::Let {
                    name,
                    mutable,
                    ty,
                    value: None,
                },
            ],
            Some(Stmt::If { cond, then, els }),
        ) = (&made[..1], made.get(1))
        else {
            out.extend(made);
            return Ok(true);
        };
        let value = match crate::tree::if_value(name, cond, then, els.as_ref()) {
            Some(value) => value,
            None => {
                out.extend(made);
                return Ok(true);
            }
        };
        out.push(Stmt::Let {
            name: name.clone(),
            mutable: *mutable,
            ty: ty.clone(),
            value: Some(value),
        });
        Ok(true)
    }

    /// Where a local `stmts[i]` declares without a value is first used,
    /// past statements that don't use it, if that's an `if` that
    /// [`Translator::late_if`] takes: a handler's `if failed { …; return
    /// } else { x = held.unwrap() }`, after its holders and the handler.
    fn late_if_past<T>(
        &self,
        stmts: &[NodeId],
        i: usize,
        grouped: &[NodeId],
        groups: &std::collections::HashMap<NodeId, T>,
    ) -> Option<usize> {
        let p = self.p;
        let Node::Let { local, init: None } = p.nodes.get(stmts[i]) else {
            return None;
        };
        let uses = |s: NodeId| {
            let mut found = false;
            temper_tmpl::analysis::walk(p, s, &mut |n| {
                found |= matches!(p.nodes.get(n), Node::Local { local: l } | Node::Assign { local: l, .. } if l == local);
            });
            found
        };
        let j = (i + 1..stmts.len()).find(|&j| uses(stmts[j]))?;
        let plain = |s: &NodeId| !grouped.contains(s) && !groups.contains_key(s);
        let fits = j > i + 1
            && matches!(p.nodes.get(stmts[j]), Node::If { .. })
            && stmts[i + 1..j].iter().all(plain)
            && self.assigned(stmts[j], local) == 1
            && !self.is_cell(local)
            && !self.is_mut(local, false);
        fits.then_some(j)
    }

    /// A local declared with its late initializer's value
    /// ([`Translator::late_init`]), `mut` only if it's assigned again.
    fn late_let(&mut self, stmt: NodeId, value: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        let Node::Let { local, .. } = p.nodes.get(stmt) else {
            unreachable!()
        };
        let value = self.expr(value, Want::Owned)?;
        let ty = p.decls.get(local).ty;
        let annotate = self.has_args(ty) || numeric_value(&value) || open_option(&value);
        let ty = if annotate { Some(self.ty(ty)?) } else { None };
        out.push(Stmt::Let {
            name: self.name(local),
            mutable: self.is_mut(local, false),
            ty,
            value: Some(value),
        });
        Ok(())
    }

    fn block(&mut self, id: NodeId) -> R<Block> {
        let mut stmts = Vec::new();
        self.block_into(id, &mut stmts)?;
        Ok(Block::new(stmts, None))
    }

    /// A loop's body, with its frame pushed.
    fn loop_body(&mut self, label: DeclId, labeled_body: bool, body: NodeId) -> R<Block> {
        self.loops.push(Frame {
            label,
            labeled_body,
            body,
        });
        let b = self.block(body);
        self.loops.pop();
        b
    }

    /// The error a `throw` throws: `value`, with `cause`, the error
    /// being handled, where it's thrown in a handler, as its cause unless
    /// it has one (`temper_core::with_cause`).
    pub(crate) fn thrown(&mut self, value: NodeId, cause: Option<NodeId>) -> R<Expr> {
        let v = self.expr(value, Want::Owned)?;
        let Some(cause) = cause else {
            return Ok(v);
        };
        let c = self.expr(cause, Want::Owned)?;
        Ok(Expr::path("temper_core::with_cause").call(vec![v, c]))
    }

    /// `throw value` as what the code returns: `Err(value)`, converted
    /// `into` the code's error type where it's another, one above it
    /// (an enum's variant) or core's `Error`.
    fn throw(&mut self, value: NodeId, cause: Option<NodeId>) -> R<Expr> {
        let Some(fail) = self.fails else {
            return not_yet("a throw where nothing handles it");
        };
        let mut v = self.thrown(value, cause)?;
        if self.p.nodes.ty(value) != fail {
            v = v.method("into", Vec::new());
        }
        Ok(Expr::path("Err").call(vec![v]))
    }

    /// What `id`'s Rust value's last `?` fails with, where it ends in
    /// one: a failing call's callee's error, a `throw`'s, or for a
    /// `catch`, its fallback's (`map_err(…)?`, `or_else(…)?`).
    fn top_error(&self, id: NodeId) -> Option<TypeId> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Catch { fallback, .. } => {
                let fails: Vec<TypeId> = (temper_tmpl::declared_errors::errors_under(p, fallback))
                    .into_iter()
                    .map(|(_, f)| f)
                    .collect();
                temper_tmpl::declared_errors::join(p, self.error_root, &fails)
            }
            _ => temper_tmpl::declared_errors::node_error(p, id),
        }
    }

    /// Whether `Ok(e?)`, for `id`'s value `e?`, may be just `e`: what
    /// its `?` fails with is what the code does; not where `?` converts
    /// it (`From`).
    pub(crate) fn try_strips(&self, id: NodeId) -> bool {
        self.top_error(id)
            .is_some_and(|fail| self.fails == Some(fail))
    }

    /// A `return` with its value, `Ok` if the code returns a `Result`, and
    /// `Ok(e?)` as `e` where `strips` says it may be.
    fn ret(&mut self, value: Option<Expr>, strips: bool) -> Stmt {
        // `return (return e)` is `return e`, as for `throw`.
        let value = match value {
            Some(Expr::Return(e)) => return Stmt::Return(e.map(|e| *e)),
            value => value,
        };
        if self.fails.is_some() {
            Stmt::Return(Some(match value {
                // `Ok(e?)` is `e`.
                Some(Expr::Try(e)) if strips => *e,
                Some(e) => e.ok(),
                None => Expr::path("()").ok(),
            }))
        } else {
            Stmt::Return(value)
        }
    }

    /// Whether a `let` is a parameter's value coalesced with null, under
    /// the parameter's name.
    fn null_default(&self, local: DeclId, init: NodeId) -> bool {
        let p = self.p;
        let Node::Coalesce { left, right } = p.nodes.get(init) else {
            return false;
        };
        matches!(p.nodes.get(left), Node::Local { local: l } if self.name(l) == self.name(local))
            && matches!(p.nodes.get(right), Node::Null {})
    }

    /// A not-yet error from translating `id`, given `id`'s place unless
    /// a node inside it gave its own.
    fn placed<T>(&self, id: NodeId, made: R<T>) -> R<T> {
        made.map_err(|mut e| {
            if e.at.is_none() {
                e.at = self.p.location(self.p.nodes.span(id));
            }
            e
        })
    }

    fn stmt(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let made = self.stmt_of(id, out);
        self.placed(id, made)
    }

    fn stmt_of(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Block { .. } => {
                let b = self.block(id)?;
                out.push(Stmt::Block(b));
            }
            Node::Let {
                local,
                init: Some(init),
            } if self.is_cell(local) => {
                let value = self.expr(init, Want::Owned)?;
                let value = self.new_cell(value);
                let ty = self.cell_type(p.decls.get(local).ty)?;
                out.push(Stmt::Let {
                    name: self.name(local),
                    mutable: false,
                    ty: Some(ty),
                    value: Some(value),
                });
            }
            Node::Let { local, .. } if self.is_cell(local) => {
                return not_yet("a captured local assigned without an initializer");
            }
            // A parameter's null default, where the local keeps the
            // parameter's name: nothing to do.
            Node::Let {
                local,
                init: Some(init),
            } if !self.is_mut(local, true) && self.null_default(local, init) => {}
            Node::Let { local, init } => {
                let value = init.map(|v| self.expr(v, Want::Owned)).transpose()?;
                let ty = p.decls.get(local).ty;
                let annotate = self.has_args(ty)
                    || value
                        .as_ref()
                        .is_none_or(|v| numeric_value(v) || open_option(v));
                let ty = if annotate { Some(self.ty(ty)?) } else { None };
                out.push(Stmt::Let {
                    name: self.name(local),
                    mutable: self.is_mut(local, init.is_some()),
                    ty,
                    value,
                });
            }
            Node::Assign { local, value } => {
                let value = self.expr(value, Want::Owned)?;
                if self.is_cell(local) {
                    if p.decls.get(local).kind == DeclKind::Param {
                        return not_yet("a captured parameter assigned");
                    }
                    out.push(self.write_cell(local, value));
                } else {
                    out.push(Stmt::Assign(Expr::path(self.name(local)), value));
                }
            }
            Node::SetModuleLet { target, value } => {
                if self.elsewhere(target) {
                    return not_yet("assigning another module's `var`");
                }
                if self.statics.contains(&target) {
                    out.push(self.write_var(target, value)?);
                } else {
                    let value = self.expr(value, Want::Owned)?;
                    out.push(Stmt::Assign(Expr::path(self.name(target)), value));
                }
            }
            Node::InitLet { target, value } => {
                if self.erased.contains(&target) {
                    if !self.is_erased(value) {
                        return not_yet("a console from elsewhere than `getConsole()`");
                    }
                } else if !self.statics.contains(&target) {
                    let v = self.expr(value, Want::Owned)?;
                    let ty = if numeric_value(&v) || self.has_args(p.decls.get(target).ty) {
                        Some(self.ty(p.decls.get(target).ty)?)
                    } else {
                        None
                    };
                    out.push(Stmt::Let {
                        name: self.name(target),
                        mutable: self.is_mut(target, true),
                        ty,
                        value: Some(v),
                    });
                } else if p.decls.get(target).facts.contains(Facts::VAR) {
                    out.push(self.write_var(target, value)?);
                } else {
                    // Set once: init runs once, unless an earlier run
                    // panicked, as in Kotlin.
                    let v = self.expr(value, Want::Owned)?;
                    let fail = Expr::Closure(
                        vec!["_".into()],
                        Block::of(Expr::Macro("panic".into(), Vec::new())),
                    );
                    let set = Expr::path(self.static_path(target))
                        .method("set", vec![v])
                        .method("unwrap_or_else", vec![fail]);
                    out.push(Stmt::Expr(set));
                }
            }
            Node::If { cond, then, els } => {
                if let Some((local, narrowed_then)) = self.if_let(cond, then, els) {
                    let (narrow, other) = if narrowed_then {
                        (then, els)
                    } else {
                        (els.unwrap_or(then), Some(then))
                    };
                    let cond = self.let_some(local);
                    self.narrowed
                        .insert(local, self.is_copy(p.decls.get(local).ty));
                    let then = self.block(narrow);
                    self.narrowed.remove(&local);
                    let then = then?;
                    let els = other.map(|e| self.block(e)).transpose()?;
                    out.push(Stmt::If { cond, then, els });
                    return Ok(());
                }
                let cond = self.expr(cond, Want::Owned)?;
                let then = self.block(then)?;
                let els = els.map(|e| self.block(e)).transpose()?;
                out.push(Stmt::If { cond, then, els });
            }
            Node::Switch { scrutinee, cases } => {
                // `match n { 0 => {…} 1 | 2 => {…} _ => {…} }`, always with
                // a `_` arm, the default's or empty.
                let scrutinee = self.expr(scrutinee, Want::Owned)?;
                let mut arms = Vec::new();
                let mut default = None;
                for &case in p.nodes.list(cases) {
                    let Node::Case { labels, body } = p.nodes.get(case) else {
                        continue;
                    };
                    let body = self.block(body)?;
                    if labels.is_empty() {
                        default = Some(body);
                        continue;
                    }
                    let pats: Vec<String> = (p.nodes.list(labels).iter())
                        .map(|&label| match p.nodes.get(label) {
                            Node::Int32Lit { value } => value.to_string(),
                            Node::Int64Lit { value } => value.to_string(),
                            _ => "_".into(),
                        })
                        .collect();
                    arms.push((pats.join(" | "), body));
                }
                // One case is an `if`, as clippy's `single_match` wants,
                // and which may end a function as its value.
                if let [(pattern, then)] = &arms[..] {
                    if !pattern.contains('|') {
                        let lit = Expr::Lit(pattern.clone());
                        let cond = Expr::Bin(Bin::Eq, Box::new(scrutinee), Box::new(lit));
                        let (then, els) = (then.clone(), default);
                        out.push(Stmt::If { cond, then, els });
                        return Ok(());
                    }
                }
                arms.push(("_".into(), default.unwrap_or_default()));
                out.push(Stmt::Match { scrutinee, arms });
            }
            Node::While { label, cond, body } => {
                let forever = matches!(p.nodes.get(cond), Node::BoolLit { value: true });
                let cond = self.expr(cond, Want::Owned)?;
                let body = self.loop_body(label, false, body)?;
                let label = self.labels.get(&label).cloned();
                out.push(if forever {
                    Stmt::Loop { label, body }
                } else {
                    Stmt::While { label, cond, body }
                });
            }
            Node::DoWhile { label, body, cond } => {
                let body_label = self.body_labels.get(&label).cloned();
                let body = self.loop_body(label, body_label.is_some(), body)?;
                let mut stmts = match body_label {
                    Some(l) => vec![Stmt::Labeled { label: l, body }],
                    None => body.stmts,
                };
                let cond = self.expr(cond, Want::Owned)?;
                stmts.push(Stmt::If {
                    cond: ops::apply(Spelling::Unary(Unary::Not), vec![cond])
                        .unwrap_or_else(|| Expr::path("false")),
                    then: Block::new(vec![Stmt::Break(None)], None),
                    els: None,
                });
                out.push(Stmt::Loop {
                    label: self.labels.get(&label).cloned(),
                    body: Block::new(stmts, None),
                });
            }
            Node::Break { label } => {
                let l = Translator::jump_needs_label(&self.loops, label)
                    .then(|| self.labels.get(&label).cloned())
                    .flatten();
                out.push(Stmt::Break(l));
            }
            Node::Continue { label } => {
                if let Some(body) = self.body_labels.get(&label) {
                    out.push(Stmt::Break(Some(body.clone())));
                } else {
                    let l = Translator::jump_needs_label(&self.loops, label)
                        .then(|| self.labels.get(&label).cloned())
                        .flatten();
                    out.push(Stmt::Continue(l));
                }
            }
            Node::Return { value } => {
                let strips = value.is_none_or(|v| self.try_strips(v));
                let value = value.map(|v| self.expr(v, Want::Owned)).transpose()?;
                let r = self.ret(value, strips);
                out.push(r);
            }
            Node::ExprStatement { expr } => {
                let void = p.nodes.ty(expr) == TypeId::VOID;
                match p.nodes.get(expr) {
                    Node::OrElse { body, fallback } if void => {
                        return self.or_else_stmt(body, fallback, out);
                    }
                    Node::Catch {
                        local,
                        body,
                        fallback,
                    } if void => return self.catch_stmt(local, body, fallback, out),
                    _ => {}
                }
                // `throw x;` is `return Err(…);`, which may end a function
                // as its value, or `break 'orelse;` in an `orelse`'s body.
                if matches!(p.nodes.get(expr), Node::Throw { .. }) {
                    if let Some(label) = self.orelse.last() {
                        out.push(Stmt::Break(Some(label.clone())));
                        return Ok(());
                    }
                }
                match self.expr(expr, Want::Owned)? {
                    Expr::Return(e) => out.push(Stmt::Return(e.map(|e| *e))),
                    e => out.push(Stmt::Expr(e)),
                }
            }
            Node::SetField {
                field,
                object,
                value,
            } => out.push(self.set_field(field, object, value)?),
            Node::InitStatic { target, value } => out.push(self.init_static(target, value)?),
            other => return not_yet(format!("a {} statement", other.kind().name())),
        }
        Ok(())
    }

    /// Assigns a static `var`: under one write lock if the value reads
    /// only it, locals, and literals, through operators, initializing
    /// the library first if it's not set ([`initialized`](Self::initialized),
    /// with the update as the read, which gives `None` while it's not);
    /// else the value first, then the lock to store it.
    fn write_var(&mut self, target: DeclId, value: NodeId) -> R<Stmt> {
        let stat = Expr::path(self.static_path(target));
        let write = stat
            .method("write", Vec::new())
            .method("unwrap", Vec::new());
        if reads_only_itself(self.p, target, value) {
            let guard = self.name(target);
            self.guard = Some((target, guard.clone()));
            let v = self.expr(value, Want::Owned);
            self.guard = None;
            let stored = Expr::path("Some").call(vec![v?]);
            let update = Block::new(
                vec![
                    Stmt::Let {
                        name: guard.clone(),
                        mutable: true,
                        ty: None,
                        value: Some(write),
                    },
                    Stmt::Assign(Expr::unary(Unary::Deref, Expr::path(guard)), stored),
                ],
                Some(Expr::path("Some").call(vec![Expr::path("()")])),
            );
            return Ok(Stmt::Expr(self.initialized(update, false)));
        }
        let v = self.expr(value, Want::Owned)?;
        Ok(Stmt::Assign(
            Expr::unary(Unary::Deref, write),
            Expr::path("Some").call(vec![v]),
        ))
    }

    /// Arguments of a call of the program's own function.
    fn args(
        &mut self,
        callee: DeclId,
        type_args: Option<temper_tmpl::TypeArgs>,
        args: &[NodeId],
    ) -> R<Vec<Expr>> {
        let params: Vec<TypeId> = self
            .p
            .signature(callee)
            .map(|s| s.params.to_vec())
            .unwrap_or_default();
        // An interface's method takes `Arc<String>`s (`Params::Trait`).
        let owner = self.p.decls.get(callee).owner;
        let concrete =
            self.p.decls.contains(owner) && self.p.decls.get(owner).kind == DeclKind::Interface;
        let mut out = Vec::new();
        for (i, &a) in args.iter().enumerate() {
            if matches!(self.p.nodes.get(a), Node::Omitted {}) {
                out.push(self.omitted(params.get(i).copied())?);
                continue;
            }
            if concrete {
                out.push(self.expr(a, Want::Owned)?);
                continue;
            }
            let wanted = (params.get(i).zip(type_args))
                .and_then(|(&t, args)| self.result_wanted(callee, args, t, a));
            if let Some(fail) = wanted {
                out.push(self.ok_fn_arg(a, fail)?);
                continue;
            }
            if params.get(i).is_some_and(|&t| self.is_nullable_string(t)) {
                out.push(self.nullable_string_arg(a)?);
                continue;
            }
            let want = if params.get(i) == Some(&TypeId::STRING) {
                Want::IntoArc
            } else {
                Want::Owned
            };
            // A list for an `impl ToListed` parameter needs no upcast.
            if let (Some(&t), Node::Upcast { value }) = (params.get(i), self.p.nodes.get(a)) {
                if self.list_param(t).is_some() {
                    out.push(self.expr(value, want)?);
                    continue;
                }
            }
            out.push(self.expr(a, want)?);
        }
        Ok(out)
    }

    /// Whether a type is `String?`.
    pub(crate) fn is_nullable_string(&self, ty: TypeId) -> bool {
        self.p.types.is_nullable(ty) && self.p.types.non_null(ty) == TypeId::STRING
    }

    /// An argument for an `Option<impl ToArcString>` parameter: `None`
    /// needs its type there, and a literal may stay `&str`.
    pub(crate) fn nullable_string_arg(&mut self, a: NodeId) -> R<Expr> {
        Ok(match self.p.nodes.get(a) {
            Node::Null {} => {
                self.uses.insert("std::sync::Arc");
                Expr::path("None::<Arc<String>>")
            }
            Node::AsNullable { value } => {
                Expr::path("Some").call(vec![self.expr(value, Want::IntoArc)?])
            }
            _ => self.expr(a, Want::Owned)?,
        })
    }

    /// A method's receiver: borrowed, and for an inherent method (not
    /// `Ord`'s `min`, `max`, or `cmp`), a number literal with its type's
    /// suffix, since Rust can't call an inherent method of `{integer}`.
    pub(crate) fn receiver(&mut self, id: NodeId, inherent: bool) -> R<Expr> {
        let e = self.expr(id, Want::Borrowed)?;
        Ok(if inherent {
            Self::typed(e, self.p.nodes.ty(id))
        } else {
            e
        })
    }

    /// A number literal with its type's suffix (`7i32`), and an `if`'s
    /// value whose first branch gives one, as Rust can't tell an
    /// `{integer}`'s type from a method of it (`(if c { 0i32 } else { 6
    /// }).wrapping_add(1)`).
    fn typed(e: Expr, ty: TypeId) -> Expr {
        let suffix = match ty {
            TypeId::INT32 => "i32",
            TypeId::INT64 => "i64",
            TypeId::FLOAT64 => "f64",
            _ => return e,
        };
        match e {
            Expr::Lit(text)
                if numeric_literal(&Expr::Lit(text.clone())) && !text.contains(['i', 'f']) =>
            {
                Expr::Lit(format!("{text}{suffix}"))
            }
            Expr::If(cond, mut then, els) => {
                if let Some(tail) = then.tail.take() {
                    then.tail = Some(Box::new(Self::typed(*tail, ty)));
                }
                Expr::If(cond, then, els)
            }
            e => e,
        }
    }

    /// A call of a connected declaration, by the connection table.
    fn connected(&mut self, decl: DeclId, args: &[NodeId], want: Want) -> R<Expr> {
        let qname = self.qname(decl);
        let Some(connection) = connected::lookup(&qname) else {
            return not_yet(format!("a connection for {qname}"));
        };
        match connection {
            Connection::Method(name) => {
                let Some((&receiver, rest)) = args.split_first() else {
                    return not_yet(format!("a receiver for {qname}"));
                };
                let inherent = !matches!(name, "min" | "max");
                let receiver = self.receiver(receiver, inherent)?;
                let mut margs = Vec::new();
                for &a in rest {
                    if matches!(self.p.nodes.get(a), Node::Omitted {}) {
                        return not_yet(format!("an argument left out of {qname}"));
                    }
                    margs.push(self.expr(a, Want::Owned)?);
                }
                Ok(receiver.method(name, margs))
            }
            Connection::Translate(hook) => hook(self, args, want),
            Connection::Function(path) => {
                let Some((&receiver, rest)) = args.split_first() else {
                    return not_yet(format!("a receiver for {qname}"));
                };
                let first = if self.p.nodes.ty(receiver) == TypeId::STRING {
                    let bound_ref = self.bound_ref(receiver);
                    match self.expr(receiver, Want::Borrowed)? {
                        // A local or a field (`&self.0.content`).
                        e @ (Expr::Path(_) | Expr::Field(..)) if !bound_ref => {
                            Expr::unary(Unary::Ref, e)
                        }
                        e => e,
                    }
                } else {
                    self.expr(receiver, Want::Owned)?
                };
                let mut fargs = vec![first];
                self.connected_args(decl, rest, &mut fargs)?;
                Ok(Expr::path(path).call(fargs))
            }
            Connection::Cast(ty) => {
                let [receiver] = args else {
                    return not_yet(format!("{qname} with arguments"));
                };
                Ok(self.expr(*receiver, Want::Owned)?.cast(ty))
            }
            Connection::Erased => not_yet(format!("{qname}'s result as a value")),
            Connection::Ufcs(path) => {
                let Some((&receiver, rest)) = args.split_first() else {
                    return not_yet(format!("a receiver for {qname}"));
                };
                let mut fargs = vec![self.handle_ref(receiver)?];
                self.connected_args(decl, rest, &mut fargs)?;
                Ok(Expr::path(path).call(fargs))
            }
            Connection::Support(name) => {
                // A method's receiver, or a static's first argument (an
                // interface's handle as its trait object), borrowed.
                let Some((&first, rest)) = args.split_first() else {
                    return not_yet(format!("a first argument for {qname}"));
                };
                let interface = temper_tmpl::analysis::nominal_decl(self.p, self.p.nodes.ty(first))
                    .is_some_and(|d| self.p.decls.get(d).kind == DeclKind::Interface);
                let first = match interface {
                    true => {
                        let e = self.expr(first, Want::Borrowed)?;
                        Expr::unary(Unary::Ref, Expr::unary(Unary::Deref, e))
                    }
                    false => self.receiver_ref(first)?,
                };
                let mut fargs = vec![first];
                self.connected_args(decl, rest, &mut fargs)?;
                Ok(Expr::path(name).call(fargs))
            }
        }
    }

    /// A connected call's arguments after its first, in order, as
    /// `decl`'s last parameters take them (a method's signature leaves
    /// out its receiver; a static's has its first argument's too): one
    /// left out as `None`, an optional one as `Some(…)`, a function as
    /// `&dyn Fn`.
    fn connected_args(&mut self, decl: DeclId, rest: &[NodeId], out: &mut Vec<Expr>) -> R<()> {
        let (optional, params, skip) = match self.p.signature(decl) {
            Some(s) => (
                s.optional.to_vec(),
                s.params.to_vec(),
                s.params.len().saturating_sub(rest.len()),
            ),
            None => (Vec::new(), Vec::new(), 0),
        };
        for (i, &a) in rest.iter().enumerate() {
            let i = i + skip;
            let opt = optional.get(i).copied().unwrap_or(false);
            // A `Listed` left out is an `impl ToListed<T>`, which rustc
            // can't infer from `None` alone (`splice`'s `newValues`).
            let listed = params.get(i).is_some_and(|&t| {
                let t = self.p.types.non_null(t);
                temper_tmpl::analysis::nominal_decl(self.p, t)
                    .is_some_and(|d| self.qname(d) == "core.type Listed")
            });
            out.push(match self.p.nodes.get(a) {
                Node::Omitted {} if listed => Expr::path("None::<temper_core::Listed<_>>"),
                Node::Omitted {} => Expr::path("None"),
                _ if opt => Expr::path("Some").call(vec![self.expr(a, Want::Owned)?]),
                // Connected members take functions as `&dyn Fn`.
                _ if self.takes_fn(decl, i) => self.fn_arg(a)?,
                _ => self.expr(a, Want::Owned)?,
            });
        }
        Ok(())
    }

    /// A borrow of a list's or map's receiver: `&x`, or `&*x` for a
    /// `Listed` or `Mapped` handle, whose trait object implements the
    /// trait.
    /// Whether a value is a local an `if let` bound by reference, so a
    /// reference already.
    fn bound_ref(&self, id: NodeId) -> bool {
        match self.p.nodes.get(id) {
            Node::NonNull { value } => matches!(
                self.p.nodes.get(value),
                Node::Local { local } if self.narrowed.get(&local) == Some(&false)
            ),
            _ => false,
        }
    }

    /// The program translated, for connections' hooks.
    pub(crate) fn program(&self) -> &'a Program {
        self.p
    }

    pub(crate) fn handle_ref(&mut self, id: NodeId) -> R<Expr> {
        let ty = self.p.nodes.ty(id);
        let bound_ref = self.bound_ref(id);
        let e = self.expr(id, Want::Borrowed)?;
        Ok(match self.type_qname(ty).as_str() {
            // `if let Some(m) = &m`'s `m`, a reference already, and for a
            // handle, its trait object, `&**m`.
            "core.type Listed" | "core.type Mapped" if bound_ref => Expr::unary(
                Unary::Ref,
                Expr::unary(Unary::Deref, Expr::unary(Unary::Deref, e)),
            ),
            _ if bound_ref => e,
            "core.type Listed" | "core.type Mapped" => {
                Expr::unary(Unary::Ref, Expr::unary(Unary::Deref, e))
            }
            // `x.as_ref().unwrap()`, a narrowed value borrowed already.
            _ if borrowed_unwrap(&e) => e,
            _ => Expr::unary(Unary::Ref, e),
        })
    }

    /// A string literal as its place wants it.
    fn string(&mut self, text: &str, want: Want) -> Expr {
        match want {
            Want::Owned if text.is_empty() => self.arc(Expr::path("String::new").call(Vec::new())),
            Want::Owned => self.arc(Expr::Str(text.into()).method("to_string", Vec::new())),
            Want::Borrowed | Want::Display | Want::IntoArc => Expr::Str(text.into()),
        }
    }

    pub(crate) fn expr(&mut self, id: NodeId, want: Want) -> R<Expr> {
        let made = self.expr_of(id, want);
        self.placed(id, made)
    }

    fn expr_of(&mut self, id: NodeId, want: Want) -> R<Expr> {
        let p = self.p;
        let ty = p.nodes.ty(id);
        let e = match p.nodes.get(id) {
            Node::BoolLit { value } => Expr::Lit(value.to_string()),
            Node::Int32Lit { value } => Expr::Lit(value.to_string()),
            Node::Int64Lit { value } => Expr::Lit(value.to_string()),
            Node::Float64Lit { value } => float(value),
            Node::StringLit { value } => {
                let text = p.strings.get(value).to_string();
                self.string(&text, want)
            }
            Node::VoidLit {} => Expr::path("()"),
            Node::TypeValue { of } => {
                let of = self.ty(of)?;
                Expr::path(format!("std::any::TypeId::of::<{of}>")).call(Vec::new())
            }
            Node::Local { local } if self.group_member(local) => {
                let access = self.group.as_ref().unwrap().access.clone();
                self.member_closure(local, &access, true)?
            }
            Node::Local { local } => self.read(local, ty, want),
            Node::GetModuleLet { target } => {
                if self.erased.contains(&target) {
                    return not_yet("a console as a value");
                }
                match &self.guard {
                    Some((g, name)) if *g == target => {
                        let read = Expr::path(name.clone());
                        let read = if self.is_copy(ty) {
                            Expr::unary(Unary::Deref, read)
                        } else {
                            read.method("clone", Vec::new())
                        };
                        read.try_()
                    }
                    _ if self.statics.contains(&target) || self.elsewhere(target) => {
                        Expr::path(self.top_path(target)?).call(Vec::new())
                    }
                    _ => self.read(target, ty, want),
                }
            }
            Node::CallFunction {
                callee,
                type_args,
                may_fail,
                args,
            } => {
                let args = p.nodes.list(args);
                if temper_tmpl::qname::by_connection(p, callee, crate::connected::connects_std) {
                    let call = self.connected(callee, args, want)?;
                    return Ok(if may_fail { call.try_() } else { call });
                }
                let args = self.args(callee, Some(type_args), args)?;
                let path = match self.static_fn_path(callee)? {
                    Some(path) => path,
                    None => self.top_path(callee)?,
                };
                let fish = self.turbofish(callee, type_args)?;
                let call = Expr::path(format!("{path}{fish}")).call(args);
                self.settled(id, call, may_fail)
            }
            Node::CallMethod {
                method,
                type_args,
                may_fail,
                receiver,
                args,
            } => {
                // Core's by the connection table; other libraries' as the
                // library's own.
                if !temper_tmpl::qname::by_connection(p, method, crate::connected::connects_std) {
                    let fish = self.turbofish(method, type_args)?;
                    let call =
                        self.call_method(method, &fish, type_args, receiver, p.nodes.list(args))?;
                    return Ok(self.settled(id, call, may_fail));
                }
                let mut all = vec![receiver];
                all.extend_from_slice(p.nodes.list(args));
                if temper_tmpl::declared_errors::passes_results(p, p.nodes.list(args)) {
                    let call = self.connected_try(method, type_args, &all)?;
                    return Ok(self.settled(id, call, may_fail));
                }
                let call = self.connected(method, &all, want)?;
                // A connection that returns (`Test.bail()`) fails itself.
                let returns = matches!(call, Expr::Return(_));
                if may_fail && !returns {
                    call.try_()
                } else {
                    call
                }
            }
            // `b == true` as `b`, and `b == false` as `!b`, as clippy
            // wants (a `when` on a `Boolean`).
            Node::Op { op, args, .. }
                if op.info().is_some_and(|i| i.name == "Boolean.eq")
                    && p.nodes
                        .list(args)
                        .iter()
                        .any(|&a| matches!(p.nodes.get(a), Node::BoolLit { .. })) =>
            {
                let [a, b] = p.nodes.list(args) else {
                    return not_yet("a Boolean.eq of other than two");
                };
                let (lit, other) = match p.nodes.get(*b) {
                    Node::BoolLit { value } => (value, *a),
                    _ => match p.nodes.get(*a) {
                        Node::BoolLit { value } => (value, *b),
                        _ => unreachable!(),
                    },
                };
                let x = self.expr(other, Want::Owned)?;
                if lit { x } else { Expr::unary(Unary::Not, x) }
            }
            Node::Op { op, args } => self.op(id, op, p.nodes.list(args), want)?,
            Node::And { left, right } => Expr::bin(
                Bin::And,
                self.expr(left, Want::Owned)?,
                self.expr(right, Want::Owned)?,
            ),
            Node::Or { left, right } => Expr::bin(
                Bin::Or,
                self.expr(left, Want::Owned)?,
                self.expr(right, Want::Owned)?,
            ),
            Node::IfValue { cond, then, els } => {
                // Literals may stay `&str` only if both branches are.
                let lits = matches!(p.nodes.get(then), Node::StringLit { .. })
                    && matches!(p.nodes.get(els), Node::StringLit { .. });
                let branch = match want {
                    Want::Display | Want::Borrowed if lits => want,
                    Want::IntoArc if lits => want,
                    _ => Want::Owned,
                };
                if let Some((local, narrowed_then)) = self.if_let(cond, then, Some(els)) {
                    let (narrow, other) = if narrowed_then {
                        (then, els)
                    } else {
                        (els, then)
                    };
                    let cond = self.let_some(local);
                    self.narrowed
                        .insert(local, self.is_copy(p.decls.get(local).ty));
                    let narrow = self.expr(narrow, branch);
                    self.narrowed.remove(&local);
                    let narrow = narrow?;
                    nullables::unwrap_or(Expr::If(
                        Box::new(cond),
                        Block::of(narrow),
                        Block::of(self.expr(other, branch)?),
                    ))
                } else {
                    Expr::If(
                        Box::new(self.expr(cond, Want::Owned)?),
                        Block::of(self.expr(then, branch)?),
                        Block::of(self.expr(els, branch)?),
                    )
                }
            }
            Node::BlockValue { stmts, value } => {
                let mut out = Vec::new();
                for &s in p.nodes.list(stmts) {
                    self.stmt(s, &mut out)?;
                }
                // A value after statements that never finish, as in `do {
                // panic(); 1 }`, is left out, as rustc warns of it
                // (`unreachable_code`); the block diverges, so it fits.
                let tail = match crate::tree::diverges(&out) {
                    true => None,
                    false => Some(self.expr(value, want)?),
                };
                Expr::Block(Block::new(out, tail))
            }
            Node::Throw { .. } if !self.orelse.is_empty() => Expr::path(format!(
                "break {}",
                self.orelse.last().cloned().unwrap_or_default()
            )),
            Node::Throw { value, cause, .. } => {
                Expr::Return(Some(Box::new(self.throw(value, cause)?)))
            }
            Node::This {} => self.this(want)?,
            Node::GetField { field, object } => self.get_field(field, object, ty, want)?,
            Node::GetStatic { target } => self.get_static(target)?,
            Node::New {
                ctor,
                may_fail,
                args,
                ..
            } => {
                let class = p.decls.get(ctor).owner;
                let call = if is_external(p, ctor) && library_of(p, ctor) == "core" {
                    self.connected(ctor, p.nodes.list(args), want)?
                } else {
                    let args = self.args(ctor, None, p.nodes.list(args))?;
                    Expr::path(format!("{}::new", self.top_path(class)?)).call(args)
                };
                self.settled(id, call, may_fail)
            }
            Node::Upcast { value } => self.upcast(id, value)?,
            Node::Downcast { value } if matches!(p.nodes.get(value), Node::Local { local } if self.narrowed.contains_key(&local)) =>
            {
                // Bound by `if let Some(i) = i`, or by reference.
                let Node::Local { local } = p.nodes.get(value) else {
                    unreachable!()
                };
                let name = Expr::path(self.name(local));
                match want {
                    Want::Owned | Want::IntoArc if !self.narrowed[&local] => {
                        name.method("clone", Vec::new())
                    }
                    _ => name,
                }
            }
            Node::Downcast { value } if self.step_result(ty).is_some() => {
                // Known to be one: `r.unwrap()`, or `()`.
                match self.step_result(ty) {
                    Some(true) => self.expr(value, Want::Owned)?.method("unwrap", Vec::new()),
                    _ => Expr::tuple(Vec::new()),
                }
            }
            Node::Downcast { value } if self.string_index(ty).is_some() => {
                // Known to be one: `i.unwrap()`, or `()`.
                let v = self.expr(value, Want::Owned)?;
                match self.string_index(ty) {
                    Some(true) => v.method("unwrap", Vec::new()),
                    _ => Expr::tuple(Vec::new()),
                }
            }
            Node::Downcast { value } => self.cast(id, value)?.method("unwrap", Vec::new()),
            Node::As { value, .. }
                if p.types.non_null(p.nodes.ty(value)) == ty && p.nodes.ty(value) != ty =>
            {
                self.as_non_null(value)?
            }
            // Panics where it fails, as `as` does.
            Node::As { value } => self.cast(id, value)?.method("unwrap", Vec::new()),
            Node::Is { ty, value } => self.is(ty, value)?,
            Node::Identical { left, right } => self.identical(left, right)?,
            Node::Null {} => Expr::path("None"),
            Node::IsNull { value } => self
                .expr(value, Want::Borrowed)?
                .method("is_none", Vec::new()),
            Node::NonNull { value } => self.non_null(id, value, want)?,
            Node::AsNullable { value } => {
                Expr::path("Some").call(vec![self.expr(value, Want::Owned)?])
            }
            Node::EqNullable { op, left, right } => self.eq_nullable(op, left, right)?,
            Node::Coalesce { left, right } => self.coalesce(left, right)?,
            Node::OrElse { body, fallback } => self.or_else(ty, body, fallback)?,
            Node::Catch {
                local,
                body,
                fallback,
            } => self.catch(ty, local, body, fallback)?,
            // Code with an error panics, as `panic()` does.
            Node::Panic {} | Node::Error { .. } => Expr::Macro("panic".into(), Vec::new()),
            // `Arc::new(vec![…])`, as Kotlin's.
            Node::ListLit { items } => {
                let mut xs = Vec::new();
                for &item in p.nodes.list(items) {
                    xs.push(self.expr(item, Want::Owned)?);
                }
                self.arc(Expr::Macro("vec".into(), xs))
            }
            Node::FunctionLiteral { decl, params, body } => self.literal(id, decl, params, body)?,
            Node::FunctionRef { target } => self.function_ref(id, target, ty)?,
            Node::CallValue {
                may_fail,
                callee,
                args,
            } => {
                let call = self.call_value(callee, args)?;
                self.settled(id, call, may_fail)
            }
            Node::ToFun { value } | Node::FromFun { value } => self.expr(value, want)?,
            other => return not_yet(format!("a {} expression", other.kind().name())),
        };
        Ok(e)
    }

    /// A local's value, cloned if its place wants one of its own and it
    /// isn't `Copy`.
    fn read(&self, decl: DeclId, ty: TypeId, want: Want) -> Expr {
        if self.is_cell(decl) {
            return self.read_cell(decl);
        }
        let name = Expr::path(self.name(decl));
        if matches!(want, Want::Owned | Want::IntoArc) && !self.is_copy(ty) {
            name.method("clone", Vec::new())
        } else {
            name
        }
    }

    fn op(&mut self, id: NodeId, op: temper_tmpl::ops::Op, args: &[NodeId], want: Want) -> R<Expr> {
        let p = self.p;
        let Some(spelling) = ops::spelling(op) else {
            return not_yet("an operator without a spelling");
        };
        if spelling == Spelling::Unary(Unary::Not) {
            if let Some(e) = self.is_some(args)? {
                return Ok(e);
            }
        }
        match (spelling, args) {
            (Spelling::Cat, _) => {
                // Only literals (`"a\u{FF61}b"`, from a template's
                // escapes) make one literal, not a `format!` without
                // arguments, which clippy flags.
                if let Some(text) = self.literal_text(id) {
                    return Ok(self.string(&text, want));
                }
                let (format, rest) = self.format(id)?;
                let mut margs = vec![Expr::Str(format)];
                margs.extend(rest);
                let f = Expr::Macro("format".into(), margs);
                Ok(match want {
                    Want::Display | Want::Borrowed => f,
                    Want::Owned | Want::IntoArc => self.arc(f),
                })
            }
            (Spelling::StrBin(op), &[a, b]) => {
                // `==` and the rest compare `Arc<String>`s as they are;
                // with a literal, `as_str()` and `&str`.
                // A literal in pieces (`"\\u"`) is one too.
                // So is a template's `format!`, a `String`.
                let lit = |n: NodeId| self.literal_text(n).is_some();
                let narrowed = |n: NodeId| matches!(p.nodes.get(n), Node::NonNull { .. });
                let (lit_a, lit_b) = (lit(a), lit(b));
                let (ea, eb) = (self.expr(a, Want::Borrowed)?, self.expr(b, Want::Borrowed)?);
                let formatted = |e: &Expr| matches!(e, Expr::Macro(name, _) if name == "format");
                let either = lit_a
                    || lit_b
                    || narrowed(a)
                    || narrowed(b)
                    || formatted(&ea)
                    || formatted(&eb);
                let side = |e: Expr, lit: bool| {
                    if either && !lit {
                        e.method("as_str", Vec::new())
                    } else {
                        e
                    }
                };
                Ok(Expr::bin(op, side(ea, lit_a), side(eb, lit_b)))
            }
            (Spelling::StrCmp, &[a, b]) => {
                let a = self.expr(a, Want::Borrowed)?;
                let b = self.expr(b, Want::Borrowed)?;
                let str = |e: Expr| match e {
                    Expr::Str(_) => e,
                    e => e.method("as_str", Vec::new()),
                };
                Ok(str(a).method("cmp", vec![str(b)]).cast("i32"))
            }
            (Spelling::Method("wrapping_neg"), &[a]) => {
                // A literal negated is a negative literal, but for the
                // least value, which has none.
                Ok(match self.expr(a, Want::Owned)? {
                    Expr::Lit(text) if text.starts_with(|c: char| c.is_ascii_digit()) => {
                        Expr::Lit(format!("-{text}"))
                    }
                    e => Self::typed(e, p.nodes.ty(a)).method("wrapping_neg", Vec::new()),
                })
            }
            _ => {
                let mut xs = Vec::new();
                for (i, &a) in args.iter().enumerate() {
                    // The first operand of an operator spelled as a method
                    // is its receiver.
                    xs.push(match spelling {
                        Spelling::Method(_) | Spelling::Shift(_) if i == 0 => {
                            self.receiver(a, true)?
                        }
                        Spelling::Cmp if i == 0 => self.receiver(a, false)?,
                        _ => self.expr(a, Want::Owned)?,
                    });
                }
                match ops::apply(spelling, xs) {
                    Some(e) => Ok(e),
                    None => not_yet("an operator with the wrong operands"),
                }
            }
        }
    }

    /// A chain of `String.cat` of literals only, as one string.
    fn literal_text(&self, id: NodeId) -> Option<String> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Op { op, args, .. } if ops::spelling(op) == Some(Spelling::Cat) => {
                let mut text = String::new();
                for &a in p.nodes.list(args) {
                    text.push_str(&self.literal_text(a)?);
                }
                Some(text)
            }
            Node::StringLit { value } => Some(p.strings.get(value).to_string()),
            _ => None,
        }
    }

    /// A string as a format string and its arguments: a chain of
    /// `String.cat`'s literal parts in the format string, an
    /// `Int32.toString()` without a radix as the int itself, and a local
    /// named in the format string (`{name}`).
    pub(crate) fn format(&mut self, id: NodeId) -> R<(String, Vec<Expr>)> {
        let mut format = String::new();
        let mut args = Vec::new();
        self.format_parts(id, &mut format, &mut args)?;
        Ok((format, args))
    }

    fn format_parts(&mut self, id: NodeId, format: &mut String, args: &mut Vec<Expr>) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Op { op, args: kids, .. } if ops::spelling(op) == Some(Spelling::Cat) => {
                for &a in p.nodes.list(kids) {
                    self.format_parts(a, format, args)?;
                }
            }
            Node::StringLit { value } => {
                format.push_str(&p.strings.get(value).replace('{', "{{").replace('}', "}}"));
            }
            _ => {
                // A class's plain `toString()` is its `Display`.
                let shown = match p.nodes.get(id) {
                    Node::CallMethod {
                        method,
                        may_fail: false,
                        receiver,
                        ..
                    } if self.display_hook(method) => receiver,
                    _ => id,
                };
                let e = self.expr(shown, Want::Display)?;
                match e {
                    Expr::Path(name) if is_plain_name(&name) => {
                        format.push_str(&format!("{{{name}}}"));
                    }
                    e => {
                        format.push_str("{}");
                        args.push(e);
                    }
                }
            }
        }
        Ok(())
    }
}

/// The parts of a comma-separated list, split at commas outside `<…>`.
fn split_top(text: &str) -> Vec<&str> {
    let mut out = Vec::new();
    let (mut depth, mut start) = (0, 0);
    for (i, c) in text.char_indices() {
        match c {
            '<' => depth += 1,
            '>' => depth -= 1,
            ',' if depth == 0 => {
                out.push(text[start..i].trim());
                start = i + 1;
            }
            _ => {}
        }
    }
    if !text[start..].trim().is_empty() {
        out.push(text[start..].trim());
    }
    out
}

/// Whether a name may go in a format string as `{name}`: a plain
/// identifier, not a raw one or a path.
fn is_plain_name(name: &str) -> bool {
    let mut chars = name.chars();
    chars.next().is_some_and(|c| c == '_' || c.is_alphabetic())
        && chars.all(|c| c == '_' || c.is_alphanumeric())
        && name != "_"
}

/// Whether an initializer is a number literal, which leaves a local's
/// type open in Rust (`{integer}`), so its methods can't be called; such
/// a local is annotated.
pub(crate) fn numeric_literal(e: &Expr) -> bool {
    matches!(e, Expr::Lit(text) if text.trim_start_matches('-').starts_with(|c: char| c.is_ascii_digit()))
}

/// [`numeric_literal`], or an `if`'s value whose first branch gives
/// one, whose type Rust leaves open too (`let n = if c { 4 } else { 7
/// };`), so a local it initializes is annotated.
pub(crate) fn numeric_value(e: &Expr) -> bool {
    match e {
        Expr::If(_, then, _) => then.tail.as_deref().is_some_and(numeric_value),
        e => numeric_literal(e),
    }
}

/// Whether an initializer is `None`, or `Some` of a number literal,
/// which leave a local's type open in Rust; such a local is annotated.
fn open_option(e: &Expr) -> bool {
    match e {
        Expr::Path(p) => p == "None",
        Expr::Call(f, args) => {
            matches!(&**f, Expr::Path(p) if p == "Some") && args.iter().all(numeric_literal)
        }
        // `if c { None } else { panic!() }`, as a `when`'s value of only
        // `null` and `panic()` is: each branch open, or never done.
        Expr::If(_, then, els) => {
            let open = |b: &Block| {
                b.tail
                    .as_deref()
                    .is_some_and(|t| open_option(t) || panics(t))
            };
            open(then) && open(els)
        }
        Expr::Block(b) => b.tail.as_deref().is_some_and(open_option),
        _ => false,
    }
}

/// Whether an expression is `panic!(…)`.
fn panics(e: &Expr) -> bool {
    matches!(e, Expr::Macro(name, _) if name == "panic")
}

/// A `Float64` literal.
fn float(value: f64) -> Expr {
    if value.is_nan() {
        Expr::path("f64::NAN")
    } else if value.is_infinite() {
        Expr::path(if value > 0.0 {
            "f64::INFINITY"
        } else {
            "f64::NEG_INFINITY"
        })
    } else {
        Expr::Lit(format!("{value:?}"))
    }
}

/// Whether a `return` continues in a function's last statement, so it
/// may end the function as its value instead: a `return` with a value,
/// or an `if` … `else` whose branches each end so, or panic, one at least
/// ending so. A block that ends the statements, as a `when`'s in an `if`'s
/// branch, needn't end before they do, so its statements join them
/// first, at any depth.
fn tail_of(stmts: &mut Vec<Stmt>) -> Option<Expr> {
    // A block last, as a statement or one dropped as a value (an
    // `orelse` whose body can't fail, as std json's), is its statements.
    if matches!(
        stmts.last(),
        Some(Stmt::Block(b) | Stmt::Expr(Expr::Block(b))) if b.tail.is_none()
    ) {
        if let Some(Stmt::Block(b) | Stmt::Expr(Expr::Block(b))) = stmts.pop() {
            stmts.extend(b.stmts);
        }
    }
    match stmts.last() {
        Some(Stmt::Return(Some(_))) => match stmts.pop() {
            Some(Stmt::Return(value)) => value,
            _ => None,
        },
        Some(Stmt::If {
            els: Some(els),
            then,
            ..
        }) => {
            // A branch's value, or none for one ending in a panic.
            let branch = |stmts: &mut Vec<Stmt>| match tail_of(stmts) {
                Some(tail) => Some(Some(Box::new(tail))),
                None => matches!(stmts.last(), Some(Stmt::Expr(e)) if panics(e)).then_some(None),
            };
            let (mut then, mut els) = (then.clone(), els.clone());
            let then_tail = branch(&mut then.stmts)?;
            let els_tail = branch(&mut els.stmts)?;
            if then_tail.is_none() && els_tail.is_none() {
                return None;
            }
            let Some(Stmt::If { cond, .. }) = stmts.pop() else {
                return None;
            };
            then.tail = then_tail;
            els.tail = els_tail;
            Some(nullables::unwrap_or(Expr::If(Box::new(cond), then, els)))
        }
        _ => None,
    }
}

/// Whether a loop has a `continue` of its own.
fn continues(p: &Program, id: NodeId, label: DeclId) -> bool {
    let mut found = false;
    walk(p, id, &mut |n| {
        found |= matches!(p.nodes.get(n), Node::Continue { label: l } if l == label);
    });
    found
}

/// A library's crate: its package's name ([`rust_name`]) with `_` for
/// `-`, as cargo names it (std's `temper_std`).
pub fn library_crate(p: &Program, name: &str) -> String {
    crate::names::crate_name(&rust_name(name, p.metadata_of(name)))
}

/// A library's package name: std's is `temper-std`, as Kotlin's, and
/// another's is its config's `rustName`, or else its own name.
pub fn rust_name(name: &str, metadata: &temper_tmpl::Metadata) -> String {
    match name {
        "std" => "temper-std".into(),
        _ => metadata.get("rustName").unwrap_or(name).into(),
    }
}

/// `item` after `attrs`, but a macro's after `#[cfg(test)]` alone: its
/// expansion isn't linted as dead code.
fn attributed(attrs: &[&'static str], item: Item) -> Item {
    let attrs: Vec<&'static str> = match item {
        Item::Macro(_) => attrs.iter().copied().filter(|&a| a == CFG_TEST).collect(),
        _ => attrs.to_vec(),
    };
    match attrs.is_empty() {
        true => item,
        false => Item::Attributed {
            attrs,
            item: Box::new(item),
        },
    }
}

/// `use`s of `paths`, `std::sync`'s together, in order, as rustfmt
/// sorts them.
fn use_items(paths: &BTreeSet<&str>) -> Vec<Item> {
    let mut uses = Vec::new();
    let std_sync: Vec<&str> = (paths.iter())
        .filter_map(|u| u.strip_prefix("std::sync::"))
        .collect();
    for u in paths {
        match u.strip_prefix("std::sync::") {
            None => uses.push(Item::Use(u.to_string())),
            Some(first) if first == std_sync[0] => uses.push(Item::Use(match std_sync[..] {
                [one] => format!("std::sync::{one}"),
                _ => format!("std::sync::{{{}}}", std_sync.join(", ")),
            })),
            Some(_) => {}
        }
    }
    uses
}

/// A module's library parts: its items and init block.
fn library_roots(module: &Module) -> impl Iterator<Item = NodeId> + '_ {
    module.items.iter().chain([&module.init]).copied()
}

/// The module `let`s that nodes under `roots` read through their
/// getters: not a `var`'s own read in an update that reads only it,
/// which reads it under the write lock ([`Translator::write_var`]).
fn module_reads(p: &Program, roots: impl IntoIterator<Item = NodeId>) -> HashSet<DeclId> {
    let mut out = HashSet::new();
    let mut under_lock = HashSet::new();
    for root in roots {
        walk(p, root, &mut |id| match p.nodes.get(id) {
            Node::SetModuleLet { target, value } if reads_only_itself(p, target, value) => {
                walk(p, value, &mut |read| {
                    if matches!(p.nodes.get(read), Node::GetModuleLet { target: t } if t == target)
                    {
                        under_lock.insert(read);
                    }
                });
            }
            Node::GetModuleLet { target } if !under_lock.contains(&id) => {
                out.insert(target);
            }
            _ => {}
        });
    }
    out
}

/// A module's name in Rust, for a part of its path: `_` for `-`, and a
/// raw identifier for a keyword.
pub fn module_name(part: &str) -> String {
    let name = crate::names::crate_name(part);
    if RAW_KEYWORDS.contains(&name.as_str()) {
        format!("r#{name}")
    } else {
        name
    }
}

/// A test's function name: its name in snake case, ASCII letters, digits,
/// and `_` only, with `test_` before one that would be a keyword or not
/// an identifier, numbered past those already used.
fn test_name(text: &str, used: &mut BTreeSet<String>) -> String {
    let mut name = String::new();
    for c in snake_case(text).chars() {
        if c.is_ascii_alphanumeric() {
            name.push(c);
        } else if !name.is_empty() && !name.ends_with('_') {
            name.push('_');
        }
    }
    let mut name = name.trim_end_matches('_').to_string();
    let reserved = RAW_KEYWORDS.contains(&name.as_str())
        || crate::names::KEYWORDS.contains(&name.as_str())
        || crate::names::PRINTED.contains(&name.as_str());
    if name.is_empty() || name.starts_with(|c: char| c.is_ascii_digit()) || reserved {
        name.insert_str(0, "test_");
    }
    let mut unique = name.clone();
    let mut n = 2;
    while !used.insert(unique.clone()) {
        unique = format!("{name}_{n}");
        n += 1;
    }
    unique
}

/// Whether an expression is `x.as_ref().unwrap()`, a narrowed value
/// borrowed already.
pub(crate) fn borrowed_unwrap(e: &Expr) -> bool {
    matches!(e, Expr::Method(inner, unwrap, _)
        if unwrap == "unwrap" && matches!(&**inner, Expr::Method(_, r, _) if r == "as_ref"))
}
