//! The frontend's translation into tmpl, the backend IR
//! (notes/tmpl/README.md): a settled, typed library as a
//! [`temper_tmpl::Program`]. `console.log` goes through a compiler-made
//! `~console`, as Kotlin's does.
//!
//! Types come from [`Typing::expr`](crate::typing::Typing::expr) and
//! [`Typing::decls`](crate::typing::Typing), and what each call calls from
//! [`Typing::resolved`](crate::typing::Typing::resolved); nothing is
//! resolved again here. Core's members are copied, as used, from
//! temper-tmpl's core fixture (`tests/fixtures/core.tmpl`, which its tests
//! check against `core.temper`), so their signatures, connected marks, and
//! parameter names have one source.
//!
//! A library's own modules translate in the order they run, each after
//! those it imports, named by their paths from the library's root. An
//! import is a reference: `let { f } = import("./sub")` makes a
//! module-level `let f = f'`, which this follows to `f'`, so a call of
//! `f` calls the other module's function, and backends derive what each
//! module imports from what it refers to
//! ([`temper_tmpl::analysis::imports`]). What's used of std or of another
//! library under the work root ([`Library::libraries`](crate::library::Library))
//! becomes external declarations in a `uses` section for that library, in
//! the module of its path there, as `uses std 0.6.0 { module testing {
//! class Test {} } }`.
//!
//! Classes and interfaces are in `to_tmpl_classes.rs`, function values
//! (literals, references, calls of values, and core's inlined `forEach`)
//! in `to_tmpl_functions.rs`, and parameters' defaults in
//! `to_tmpl_defaults.rs`.
//!
//! A module's `test` blocks ([`crate::testing`]) become its `Test`s: each
//! a function taking std's `Test`, named in reports by the test's name,
//! which must be a constant string.
//!
//! Anything not covered yet gives [`NotYet`], saying what, so the
//! coverage count (`compat/count_tmpl.py`) can tally it; nothing here
//! panics on a construct it doesn't know.

use crate::builtins::{Builtin, Constant, Function};
use crate::core::{self, LET_VAR, LOOP_EACH, LOOP_NEXT};
use crate::library::{Level, ModuleId};
use crate::scope::{ScopeId, SymbolId};
use crate::settle::Settled;
use crate::types::{self as ft, TypeData as FTypeData};
use crate::typing::Resolved;
use std::cell::RefCell;
use std::collections::{HashMap, HashSet};
use std::sync::OnceLock;
use temper_syntax::ast::Value;
use temper_syntax::tree::{NodeId as FNode, NodeKind};
use temper_tmpl::ops::Op;
use temper_tmpl::{
    Builder, DeclId, DeclKind, Facts, FileId, NodeId, Program, Span, TypeData, TypeId,
};

/// Why a library isn't translated: what it uses that tmpl's translation
/// doesn't cover yet, and where.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NotYet {
    /// What isn't covered, short and general, as "a class" or "a nullable
    /// type", so the coverage count can group by it.
    pub what: String,
    /// Where, as `path:line:column`, if somewhere in particular.
    pub at: Option<String>,
}

impl std::fmt::Display for NotYet {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match &self.at {
            Some(at) => write!(f, "not yet in tmpl: {} at {at}", self.what),
            None => write!(f, "not yet in tmpl: {}", self.what),
        }
    }
}

type Tx<T> = Result<T, NotYet>;

#[path = "to_tmpl_classes.rs"]
mod classes;
#[path = "to_tmpl_defaults.rs"]
mod defaults;
#[path = "to_tmpl_failures.rs"]
mod failures;
#[path = "to_tmpl_functions.rs"]
mod functions;
use classes::{Assigned, Member};

/// Core's members that tmpl knows, as external declarations.
const CORE_FIXTURE: &str = include_str!("../../temper-tmpl/tests/fixtures/core.tmpl");

/// Core's declarations that tmpl knows, and each by its key: `Type.member`
/// for a member, as typing's resolutions name builtins, or a top-level
/// function's name.
struct Catalog {
    program: Program,
    keys: HashMap<String, DeclId>,
    version: String,
}

fn catalog() -> &'static Catalog {
    static CATALOG: OnceLock<Catalog> = OnceLock::new();
    CATALOG.get_or_init(|| {
        let program = temper_tmpl::text::read(CORE_FIXTURE).expect("core.tmpl reads");
        let mut keys = HashMap::new();
        for d in program.decls.ids() {
            if !program.decls.contains(d) {
                continue;
            }
            let decl = program.decls.get(d);
            let owner = decl.owner;
            if owner.is_none() || !program.decls.contains(owner) {
                continue;
            }
            let key = match program.decls.get(owner).kind {
                DeclKind::Library => program.name(d).to_string(),
                DeclKind::Class | DeclKind::Interface => {
                    format!("{}.{}", program.name(owner), program.name(d))
                }
                _ => continue,
            };
            keys.insert(key, d);
        }
        // Core's version is Temper's, as std's is, whatever the
        // fixture says.
        let version = temper_tmpl::version::current().to_string();
        Catalog {
            program,
            keys,
            version,
        }
    })
}

/// std's version, for `uses std` sections and std's own packages:
/// Temper's ([`temper_tmpl::version::current`]), as core's is.
pub fn std_version() -> String {
    temper_tmpl::version::current().to_string()
}

/// Where a settled module comes from: the library being translated, std,
/// or another library under the work root, with its path from that
/// library's root (empty for the root).
#[derive(Clone, Debug, PartialEq, Eq)]
enum Home {
    Own(String),
    Other { library: String, path: String },
}

/// Each module's home, by [`ModuleId`]: std's under [`stdlib::DIR`], other
/// libraries' under their roots, and the rest the library's own, whose
/// root is the directory of its first file's module (or the library root
/// holding it).
fn homes(s: &Settled, library: &str) -> Vec<Home> {
    let lib = &s.library;
    let std_root = lib.std_root();
    let main = lib
        .files
        .first()
        .map(|f| lib.modules[f.module.0 as usize].dir.as_str());
    let main = main.unwrap_or(".");
    let under = |dir: &str, root: &str| -> Option<String> {
        if dir == root {
            return Some(String::new());
        }
        let rest = dir.strip_prefix(root)?.strip_prefix('/')?;
        Some(rest.trim_end_matches('/').to_string())
    };
    let own_root = (lib.libraries.iter())
        .map(|(_, root)| root.as_str())
        .filter(|root| under(main, root).is_some())
        .max_by_key(|root| root.len())
        .unwrap_or(main)
        .to_string();
    (lib.modules.iter())
        .map(|m| {
            if let Some(path) = under(&m.dir, std_root) {
                if library == "std" {
                    return Home::Own(path);
                }
                return Home::Other {
                    library: "std".into(),
                    path,
                };
            }
            let other = (lib.libraries.iter())
                .filter(|(_, root)| *root != own_root)
                .filter_map(|(name, root)| Some((name, root, under(&m.dir, root)?)))
                .max_by_key(|(_, root, _)| root.len());
            match (other, under(&m.dir, &own_root)) {
                (Some((_, root, _)), Some(_)) if root.len() <= own_root.len() => {
                    Home::Own(under(&m.dir, &own_root).unwrap_or_default())
                }
                (Some((name, _, path)), _) => Home::Other {
                    library: name.clone(),
                    path,
                },
                (None, own) => Home::Own(own.unwrap_or_else(|| m.dir.trim_matches('/').into())),
            }
        })
        .collect()
}

/// Translates a settled library into tmpl, named `library`: its own
/// modules, with what it uses of core, std, and other libraries, its
/// errors as tmpl's `Error`s. Fails with what isn't covered yet.
pub fn to_tmpl(settled: &Settled, library: &str) -> Result<Program, NotYet> {
    translate(settled, library, None)
}

/// Every gap in a library's translation, not only the first: each
/// statement or declaration that tmpl doesn't cover yet, in order, for
/// finding what to cover next. Empty when [`to_tmpl`] succeeds.
pub fn gaps(settled: &Settled, library: &str) -> Vec<NotYet> {
    let mut gaps = Vec::new();
    if let Err(e) = translate(settled, library, Some(&mut gaps)) {
        gaps.push(e);
    }
    gaps.dedup();
    gaps
}

/// [`to_tmpl`], or with `gaps`, going on past each statement and
/// declaration that isn't covered, recording why.
fn translate(
    settled: &Settled,
    library: &str,
    gaps: Option<&mut Vec<NotYet>>,
) -> Result<Program, NotYet> {
    let lib = &settled.library;
    // Code with errors fails where it runs, as tmpl's `Error`s, each
    // with the message reported where it is (notes/tmpl/nodes.md, "Level
    // 1: Errors stay values").
    // Type-only errors keep the value as written, which dynamically
    // typed targets run.
    let errors = |type_only: bool| {
        (lib.diagnostics.iter())
            .filter(|d| d.level == Level::Error && d.message.type_only() == type_only)
            .map(|d| ((d.span.start, d.span.end), d.message.text()))
            .collect()
    };
    let mut t = Translator {
        s: settled,
        errors: errors(false),
        type_errors: errors(true),
        as_written: None,
        b: Builder::new(library, "0.0.0"),
        decls: HashMap::new(),
        functions: HashSet::new(),
        bodiless: HashSet::new(),
        loops: HashMap::new(),
        steps: HashMap::new(),
        module: DeclId::NONE,
        owner: DeclId::NONE,
        result: TypeId::NONE,
        files: HashMap::new(),
        core: None,
        externals: HashMap::new(),
        console: None,
        homes: homes(settled, library),
        scopes: (settled.modules.iter()).map(|m| (m.scope, m.id)).collect(),
        libraries: HashMap::new(),
        external_modules: HashMap::new(),
        external_lets: HashMap::new(),
        external_types: HashMap::new(),
        classes: HashMap::new(),
        class_order: Vec::new(),
        members: HashMap::new(),
        ctors: HashMap::new(),
        this_class: None,
        type_params: HashMap::new(),
        gaps: gaps.is_some().then(Vec::new),
        catching: Vec::new(),
        always_jumps: RefCell::new(HashMap::new()),
        first_module: DeclId::NONE,
        failure_base: None,
        handling: Vec::new(),
        adopted: HashSet::new(),
    };
    let result = translate_with(&mut t);
    if let (Some(out), Some(found)) = (gaps, t.gaps.take()) {
        out.extend(found);
    }
    result.map(|()| t.b.finish())
}

fn translate_with(t: &mut Translator) -> Tx<()> {
    let settled = t.s;
    // Modules first, then their classes, so that any module may name any
    // class, then the classes' members, then bodies.
    let mut own = Vec::new();
    for module in &settled.modules {
        if let Home::Own(path) = &t.homes[module.id.0 as usize] {
            let decl = if path.is_empty() {
                let name = t.b.program().library.name.clone();
                t.b.module(&name)
            } else {
                let decl = t.b.module(path);
                t.b.set_module_path(decl, path);
                decl
            };
            t.module_files(module, decl);
            if own.is_empty() {
                t.first_module = decl;
            }
            own.push((module, decl));
        }
    }
    for &(module, decl) in &own {
        let mut classes = module.top_levels.clone();
        classes.extend(
            t.local_classes(&module.top_levels)
                .into_iter()
                .map(|(c, _)| c),
        );
        t.declare_classes(decl, &classes)?;
    }
    for class in t.class_order.clone() {
        let declared = t.declare_members(class);
        t.gap(declared)?;
    }
    for (module, decl) in own {
        t.module(module, decl)?;
    }
    Ok(())
}

/// Where a library-wide position is, as `path:line:column`.
fn position(s: &Settled, pos: u32) -> String {
    let lib = &s.library;
    let (file, offset) = lib.locate(pos);
    let Some(f) = lib.files.get(file.0 as usize) else {
        return "?".into();
    };
    let source = f.parsed.source();
    let before = source.get(..offset as usize).unwrap_or(source);
    let line = before.matches('\n').count() + 1;
    let column = before.rsplit('\n').next().unwrap_or("").chars().count() + 1;
    format!("{}:{line}:{column}", f.path)
}

/// A 128-bit FNV-1a hash of a file's text, to match it later.
fn hash(text: &str) -> [u8; 16] {
    let mut out = [0u8; 16];
    for (half, seed) in [0xcbf29ce484222325u64, 0x84222325cbf29ce4]
        .iter()
        .enumerate()
    {
        let mut h = *seed;
        for &byte in text.as_bytes() {
            h ^= byte as u64;
            h = h.wrapping_mul(0x100000001b3);
        }
        out[half * 8..half * 8 + 8].copy_from_slice(&h.to_le_bytes());
    }
    out
}

struct Translator<'a> {
    s: &'a Settled,
    /// The library's errors' messages, by their spans, but type-only
    /// ones.
    errors: HashMap<(u32, u32), String>,
    /// The library's type-only errors' messages, by their spans
    /// ([`crate::library::Message::type_only`]).
    type_errors: HashMap<(u32, u32), String>,
    /// The span of the value with a type-only error being translated as
    /// written, inside its `Error`.
    as_written: Option<(u32, u32)>,
    b: Builder,
    /// The library's declarations, by their `Let`s.
    decls: HashMap<FNode, DeclId>,
    /// The `Let`s that declare functions.
    functions: HashSet<FNode>,
    /// Those that declare bodiless `@connected` functions, which host code
    /// implements.
    bodiless: HashSet<FNode>,
    /// Each `Loop`'s label.
    loops: HashMap<FNode, DeclId>,
    /// The step of each `for` loop with a `continue`, which runs before
    /// each `continue` of it, as tmpl's `while` has no step
    /// ([`temper_tmpl::exits`]).
    steps: HashMap<FNode, FNode>,
    module: DeclId,
    /// What owns locals and labels: the function being translated, or the
    /// module in its init block.
    owner: DeclId,
    /// The function's result, or `NONE` in the init block.
    result: TypeId,
    /// tmpl's files, by the library's.
    files: HashMap<u32, FileId>,
    /// `temper-core`'s declaration, once something of it is used.
    core: Option<DeclId>,
    /// Core's declarations used, by the catalog's.
    externals: HashMap<DeclId, DeclId>,
    /// The module's `~console`, and the call that first needed it.
    console: Option<(DeclId, FNode)>,
    /// Each settled module's home, by [`ModuleId`].
    homes: Vec<Home>,
    /// Modules by their scopes.
    scopes: HashMap<ScopeId, ModuleId>,
    /// Other libraries' `uses` declarations, by name.
    libraries: HashMap<String, DeclId>,
    /// Other libraries' modules, by library and path.
    external_modules: HashMap<(String, String), DeclId>,
    /// Other libraries' functions and module `let`s, by their `Let`s.
    external_lets: HashMap<FNode, DeclId>,
    /// Other libraries' classes and interfaces, by their symbols.
    external_types: HashMap<SymbolId, DeclId>,
    /// The library's classes and interfaces, by their nodes.
    classes: HashMap<FNode, DeclId>,
    /// Their nodes, in the order declared.
    class_order: Vec<FNode>,
    /// Their members, by their `Let`s.
    members: HashMap<FNode, Member>,
    /// Each class's constructor, the `Let` of its own if it has one, and
    /// its parameters' `Let`s: its own's, or its header's fields.
    ctors: HashMap<DeclId, (DeclId, Option<FNode>, Vec<FNode>)>,
    /// The class or interface whose member is being translated.
    this_class: Option<(FNode, DeclId)>,
    /// Type parameters, by their symbols.
    type_params: HashMap<SymbolId, DeclId>,
    /// What isn't covered yet, when finding every gap ([`gaps`]), rather
    /// than failing at the first.
    gaps: Option<Vec<NotYet>>,
    /// The functions (or modules, in their init blocks) inside whose
    /// `orelse` bodies the translation is, where what may bubble is
    /// caught, so a division that may fail is fine in one that can't.
    catching: Vec<DeclId>,
    /// Whether each node asked about always jumps
    /// ([`Self::always_jumps`]), kept, as it's asked of each statement
    /// and so again of each part of each.
    always_jumps: RefCell<HashMap<FNode, bool>>,
    /// The library's first module, which holds what the compiler makes
    /// for the whole library, as its base failure type.
    first_module: DeclId,
    /// The library's base failure type, once a failure type needs it
    /// ([`Self::failure_base`]).
    failure_base: Option<DeclId>,
    /// The handlers' fallbacks being translated, innermost last
    /// ([`failures::Handling`]).
    handling: Vec<failures::Handling>,
    /// The `let e = caught()`s whose `catch`es declare `e` themselves,
    /// which then translate to nothing.
    adopted: HashSet<FNode>,
}

impl Translator<'_> {
    /// When finding every gap, records one and goes on with a default;
    /// else fails with it.
    fn gap<T: Default>(&mut self, result: Tx<T>) -> Tx<T> {
        match (result, &mut self.gaps) {
            (Err(e), Some(gaps)) => {
                gaps.push(e);
                Ok(T::default())
            }
            (result, _) => result,
        }
    }

    fn not_yet<T>(&self, node: FNode, what: impl Into<String>) -> Tx<T> {
        let start = self.s.library.tree.span(node).start;
        Err(NotYet {
            what: what.into(),
            at: Some(position(self.s, start)),
        })
    }

    /// The message of the error that `node` is, or that's reported where
    /// it is, or that its callee or first statement is, if not a type-only
    /// one: an `Error` node, a member that isn't there, or a call of a name
    /// not declared,
    /// which tmpl gives as an `Error` that fails when run.
    fn error_at(&self, node: FNode) -> Option<String> {
        let lib = &self.s.library;
        if self.kind(node) == NodeKind::Error {
            return Some(lib.error_text(node));
        }
        // A block whose first statement is an error, as the parser makes
        // of `cents 100`, missing an operator, fails there.
        if self.kind(node) == NodeKind::Block {
            let first = self.children(node).first().copied();
            if let Some(message) = first.and_then(|first| self.error_at(first)) {
                return Some(message);
            }
        }
        if self.kind(node) == NodeKind::Call {
            let children = self.children(node);
            let callee = children.first().copied()?;
            if self.kind(callee) == NodeKind::Error {
                return Some(lib.error_text(callee));
            }
            // A method that isn't there, as `s.lenght()` ("No member"),
            // or one of an object that's an error, as an undeclared
            // name's `missing.toString()`, which fails before the call.
            if let Some(object) = self.dot_object(callee) {
                let span = lib.tree.span(callee);
                if let Some(message) = self.errors.get(&(span.start, span.end)) {
                    return Some(message.clone());
                }
                if let Some(message) = self.error_at(object) {
                    return Some(message);
                }
            }
            // An operator typing couldn't resolve for an operand that's an
            // error, as `missing + 1`.
            let error = children.iter().find(|&&c| self.kind(c) == NodeKind::Error);
            if let Some(&error) = error.filter(|_| self.s.typing.resolved(node).is_none()) {
                return Some(lib.error_text(error));
            }
            // An argument that's an error, after only names and values,
            // fails before the call is made, so the call is that error:
            // `f("0")`'s, where `f` takes an `Int`.
            let quiet = |c: FNode| matches!(self.kind(c), NodeKind::Value | NodeKind::RightName);
            let callee_quiet = self.kind(callee) != NodeKind::Call;
            for &arg in &children[1..] {
                if let Some(message) = self.error_at(arg) {
                    return Some(message);
                }
                if !callee_quiet || !quiet(arg) {
                    break;
                }
            }
        }
        let span = lib.tree.span(node);
        self.errors.get(&(span.start, span.end)).cloned()
    }

    /// `value`, in a type-only `Error` where one is reported at `at`, as
    /// at a whole assignment, `a = "1"`, for `a: Int`.
    pub(super) fn type_error_around(&mut self, at: FNode, value: NodeId) -> NodeId {
        let span = self.s.library.tree.span(at);
        match self.type_errors.get(&(span.start, span.end)).cloned() {
            Some(message) => {
                let made = self.b.type_error(TypeId::NONE, &message, value);
                self.made(made, at)
            }
            None => value,
        }
    }

    /// Whether the library has errors, which may leave types unknown.
    fn has_errors(&self) -> bool {
        !self.errors.is_empty() || !self.type_errors.is_empty()
    }

    /// Whether a statement always fails at an error: it is one, or it
    /// declares or assigns one, as `let s: String = 1;`, so what follows
    /// it can't run.
    fn fails(&self, node: FNode) -> bool {
        self.error_at(node).is_some() || self.stored_error(node).is_some()
    }

    /// The error a `let` or assignment stores, which fails before it
    /// does, as `let s: String = 1;`.
    fn stored_error(&self, node: FNode) -> Option<String> {
        let value = match self.kind(node) {
            NodeKind::Let => core::let_parts(&self.s.library.tree, node).2,
            NodeKind::Assign => self.children(node).get(1).copied(),
            _ => None,
        };
        value.and_then(|v| self.error_at(v))
    }

    fn kind(&self, node: FNode) -> NodeKind {
        self.s.library.tree.kind(node)
    }

    fn children(&self, node: FNode) -> &[FNode] {
        self.s.library.tree.children(node)
    }

    fn data(&self, node: FNode) -> u32 {
        self.s.library.tree.data(node)
    }

    fn name_of(&self, name: FNode) -> &str {
        let lib = &self.s.library;
        lib.name(name).map_or("", |n| lib.names.get(n))
    }

    fn function_of(&self, node: FNode) -> Option<Function> {
        match self.s.scopes.resolved_builtin(node) {
            Some(Builtin::Function(f)) => Some(f),
            _ => None,
        }
    }

    /// The `Let` a name resolved to.
    fn declaration(&self, name: FNode) -> Option<FNode> {
        let symbol = self.s.scopes.resolved(name)?;
        self.s.scopes.symbol(symbol).decl
    }

    fn is_exported(&self, decl: FNode) -> bool {
        let name = self.children(decl)[0];
        (self.s.scopes.resolved(name)).is_some_and(|s| self.s.scopes.symbol(s).exported)
    }

    // Spans.

    fn span(&self, node: FNode) -> Span {
        let lib = &self.s.library;
        let span = lib.tree.span(node);
        let (file, start) = lib.locate(span.start);
        match self.files.get(&file.0) {
            Some(&file) => Span {
                file,
                start,
                end: start + span.end.saturating_sub(span.start),
            },
            None => Span::UNKNOWN,
        }
    }

    /// Gives a node the span of what it translates.
    fn own(&mut self, node: NodeId, from: FNode) -> NodeId {
        let span = self.span(from);
        self.b.set_span(node, span, false);
        node
    }

    /// Gives a node made for `from` its span, marked derived.
    fn made(&mut self, node: NodeId, from: FNode) -> NodeId {
        let span = self.span(from);
        self.b.set_span(node, span, true);
        node
    }

    /// Gives a declaration its `Let`'s spans: the whole, and its name.
    fn decl_spans(&mut self, decl: DeclId, from: FNode) {
        let name = self.children(from).first().copied().unwrap_or(from);
        let (span, name_span) = (self.span(from), self.span(name));
        self.b.set_decl_spans(decl, span, name_span);
    }

    // Types.

    fn ty(&mut self, t: ft::TypeId, at: FNode) -> Tx<TypeId> {
        match self.s.typing.types.get(t) {
            // A function's result that never returns, as std's
            // `Test.bail()`, and its calls' type: tmpl's `Never<T>`, which
            // backends without a bottom type lower to the `T` it stands
            // for, as core's docs say (`temper_tmpl::nevers`).
            &FTypeData::NeverOf(inner) => {
                let inner = self.ty(inner, at)?;
                return Ok(self.b.never(inner));
            }
            &FTypeData::Param(symbol) => {
                return match self.type_params.get(&symbol) {
                    Some(&d) => Ok(self.b.program().decls.get(d).ty),
                    None => self.not_yet(at, "a type parameter"),
                };
            }
            FTypeData::Applied { base, args } => {
                let (base, args) = (*base, args.clone());
                let decl = match *self.s.typing.types.get(base) {
                    FTypeData::Builtin(i) => {
                        match self.core_decl(crate::builtins::TYPES[i as usize]) {
                            Some(d) => d,
                            None => return self.not_yet(at, "a builtin generic type"),
                        }
                    }
                    FTypeData::Class(symbol) => {
                        let own =
                            (self.class_node(symbol)).and_then(|c| self.classes.get(&c).copied());
                        match own {
                            Some(d) => d,
                            None => match self.external_type(symbol, at)? {
                                Some(d) => d,
                                None => return self.not_yet(at, "a generic type"),
                            },
                        }
                    }
                    _ => return self.not_yet(at, "a generic type"),
                };
                let mut made = Vec::new();
                for a in args {
                    made.push(self.ty(a, at)?);
                }
                if made.len() != self.b.program().decls.type_params(decl).len() {
                    // As written with the wrong number, an error.
                    if self.has_errors() {
                        return Ok(TypeId::ANY_VALUE);
                    }
                    return self.not_yet(at, "a generic type's arguments");
                }
                return Ok(self.b.applied(decl, &made));
            }
            _ => {}
        }
        if let FTypeData::Fn { params, result } = self.s.typing.types.get(t) {
            let (params, result) = (params.clone(), *result);
            let mut types = Vec::new();
            let mut optional = Vec::new();
            for p in &params {
                let ty = self.ty(p.ty, at)?;
                // As a function's signature has it.
                types.push(if p.optional { self.b.nullable(ty) } else { ty });
                optional.push(p.optional);
            }
            let result = self.ty(result, at)?;
            return Ok(self.b.function_type_with(&types, &optional, result));
        }
        if let FTypeData::Result { pass, fail } = *self.s.typing.types.get(t) {
            let pass = self.ty(pass, at)?;
            let fail = self.ty(fail, at)?;
            return Ok(self.b.result(pass, fail));
        }
        if let FTypeData::Class(symbol) = *self.s.typing.types.get(t) {
            let own = self
                .class_node(symbol)
                .and_then(|c| self.classes.get(&c).copied());
            if let Some(decl) = own {
                return Ok(self.b.nominal(decl));
            }
            if let Some(decl) = self.external_type(symbol, at)? {
                return Ok(self.b.nominal(decl));
            }
        }
        if let FTypeData::Builtin(i) = *self.s.typing.types.get(t) {
            // A type as a value, tmpl's own type.
            if crate::builtins::TYPES[i as usize] == "Type" {
                return Ok(TypeId::TYPE);
            }
            // One of core's classes or interfaces, as `StringBuilder`; the
            // scalars are tmpl's own types, below.
            let scalar = [
                ft::VOID,
                ft::BOOLEAN,
                ft::INT32,
                ft::INT64,
                ft::FLOAT64,
                ft::STRING,
                ft::ANY_VALUE,
                ft::BUBBLE,
            ];
            if !scalar.contains(&t) {
                if let Some(d) = self.core_decl(crate::builtins::TYPES[i as usize]) {
                    if self.b.program().decls.type_params(d).is_empty() {
                        return Ok(self.b.nominal(d));
                    }
                }
            }
        }
        Ok(match t {
            ft::VOID => TypeId::VOID,
            ft::BOOLEAN => TypeId::BOOLEAN,
            ft::INT32 => TypeId::INT32,
            ft::INT64 => TypeId::INT64,
            ft::FLOAT64 => TypeId::FLOAT64,
            ft::STRING => TypeId::STRING,
            ft::ANY_VALUE => TypeId::ANY_VALUE,
            // Only ever a result's failure type, above.
            ft::BUBBLE => TypeId::BUBBLE,
            // A type an error left unknown, as an undeclared type's name:
            // any value, so what has it still translates, and the code
            // that has the error fails where it runs.
            _ if self.has_errors() && matches!(self.s.typing.types.get(t), FTypeData::Unknown) => {
                TypeId::ANY_VALUE
            }
            _ if matches!(self.s.typing.types.get(t), FTypeData::Nullable(_)) => {
                let &FTypeData::Nullable(inner) = self.s.typing.types.get(t) else {
                    unreachable!()
                };
                let inner = self.ty(inner, at)?;
                if inner == TypeId::VOID {
                    return self.not_yet(at, "a nullable Void");
                }
                return Ok(self.b.nullable(inner));
            }
            _ => {
                let what = match self.s.typing.types.get(t) {
                    FTypeData::Unknown => "an expression of unknown type",
                    FTypeData::Never | FTypeData::NeverOf(_) => "type Never",
                    FTypeData::Null => "null",
                    FTypeData::Builtin(_) => "a builtin type core's fixture lacks",
                    FTypeData::Class(_) => "a class or interface type",
                    FTypeData::Param(_) => "a type parameter",
                    FTypeData::Applied { .. } => "a generic type",
                    FTypeData::Nullable(_) => "a nullable type",
                    FTypeData::Fn { .. } => "a function type",
                    FTypeData::Result { .. } => "a result type",
                };
                return self.not_yet(at, what);
            }
        })
    }

    /// A builtin type as a value, as `Void` where a `Type` is wanted: so
    /// far a scalar's other than `AnyValue`, which every backend has a
    /// value for.
    fn type_value(&mut self, node: FNode, i: u8) -> Tx<NodeId> {
        let of = match crate::builtins::TYPES[i as usize] {
            "Int" => TypeId::INT32,
            name => temper_tmpl::TypeTable::scalar_named(name).unwrap_or(TypeId::NONE),
        };
        let scalars = [
            TypeId::VOID,
            TypeId::BOOLEAN,
            TypeId::INT32,
            TypeId::INT64,
            TypeId::FLOAT64,
            TypeId::STRING,
        ];
        if !scalars.contains(&of) {
            return self.not_yet(node, "a type as a value, other than a builtin scalar");
        }
        let made = self.b.type_value(of);
        Ok(self.own(made, node))
    }

    /// An expression's type.
    fn expr_ty(&mut self, node: FNode) -> Tx<TypeId> {
        match self.s.typing.expr(node) {
            Some(t) => self.ty(t, node),
            None => self.unknown_ty(node),
        }
    }

    /// The type of what typing has no type for: in a library with
    /// errors, which leave types unknown (as an undeclared type's name),
    /// any value, so the declaration still translates and the error fails
    /// where it runs; else not yet covered.
    fn unknown_ty(&self, at: FNode) -> Tx<TypeId> {
        match !self.has_errors() {
            true => self.not_yet(at, "an expression of unknown type"),
            false => Ok(TypeId::ANY_VALUE),
        }
    }

    /// A declaration's type, by its `Let`.
    fn decl_ty(&mut self, decl: FNode) -> Tx<TypeId> {
        let typing = &self.s.typing;
        let init = core::let_parts(&self.s.library.tree, decl).2;
        let t = (typing.decls.get(&decl).copied()).or_else(|| init.and_then(|i| typing.expr(i)));
        match t {
            // A local, parameter, or field holds a `T`, never a
            // `Never<T>`, as `let x = stop()` would have it.
            Some(t) => {
                let ty = self.ty(t, decl)?;
                Ok(self.b.program().types.fallback(ty))
            }
            None => self.unknown_ty(decl),
        }
    }

    /// A function's signature, from its `Fn`'s type.
    fn signature(&mut self, f: FNode) -> Tx<TypeId> {
        let Some(t) = self.s.typing.expr(f) else {
            return self.not_yet(f, "an expression of unknown type");
        };
        self.signature_of(t, f)
    }

    /// A function's signature, from its type.
    fn signature_of(&mut self, t: ft::TypeId, f: FNode) -> Tx<TypeId> {
        if !matches!(self.s.typing.types.get(t), FTypeData::Fn { .. }) {
            return self.not_yet(f, "a function of unknown type");
        }
        self.ty(t, f)
    }

    // Core's declarations.

    /// Core's declaration with this key, as `Int32.toString`, copied into
    /// the program's `uses` section the first time.
    fn core_decl(&mut self, key: &str) -> Option<DeclId> {
        let &c = catalog().keys.get(key)?;
        Some(self.external(c))
    }

    fn external(&mut self, c: DeclId) -> DeclId {
        if let Some(&d) = self.externals.get(&c) {
            return d;
        }
        let cat = &catalog().program;
        let decl = *cat.decls.get(c);
        let owner = if cat.decls.get(decl.owner).kind == DeclKind::Library {
            match self.core {
                Some(core) => core,
                None => {
                    let core = self.b.uses("temper-core", &catalog().version);
                    self.core = Some(core);
                    core
                }
            }
        } else {
            self.external(decl.owner)
        };
        let name = cat.name(c);
        let d = match decl.kind {
            DeclKind::Class | DeclKind::Interface => {
                let d = self.b.declare_type(decl.kind, name, owner);
                // Before its type parameters' bounds and its supertypes,
                // which may name it, as `K extends MapKey<K>`.
                self.externals.insert(c, d);
                self.external_type_params(c, d);
                let supers: Vec<TypeId> = (cat.decls.supers(c).iter().copied())
                    .map(|t| self.core_type(t))
                    .collect();
                self.b.set_supers(d, &supers);
                d
            }
            DeclKind::TypeParam => {
                // Declared with its owner.
                self.external(decl.owner);
                return self.externals[&c];
            }
            kind => {
                // A generic method's type parameters first, which its
                // signature names, as `O` in `map<O>`.
                let d = self.b.declare(kind, name, owner, TypeId::NONE);
                if !cat.decls.type_params(c).is_empty() {
                    self.externals.insert(c, d);
                    self.external_type_params(c, d);
                }
                let ty = self.core_type(decl.ty);
                self.b.set_decl_type(d, ty);
                let names: Vec<&str> = (cat.decls.param_names(c).iter())
                    .map(|&n| cat.strings.get(n))
                    .collect();
                self.b.set_param_names(d, &names);
                d
            }
        };
        self.b.add_facts(d, decl.facts);
        let backends: Vec<&str> = (cat.decls.backends(c).iter())
            .map(|&n| cat.strings.get(n))
            .collect();
        if !backends.is_empty() {
            self.b.set_connected(d, &backends);
        }
        self.externals.insert(c, d);
        d
    }

    /// A catalog's generic declaration's type parameters, as `d`'s, with
    /// their bounds.
    fn external_type_params(&mut self, c: DeclId, d: DeclId) {
        let cat = &catalog().program;
        let params = cat.decls.type_params(c).to_vec();
        let mut made = Vec::new();
        for &tp in &params {
            let td = self.b.declare_type_param(cat.name(tp), d, &[]);
            self.externals.insert(tp, td);
            made.push(td);
        }
        self.b.set_type_params(d, &made);
        for (&tp, &td) in params.iter().zip(&made) {
            let bounds: Vec<TypeId> = (cat.decls.supers(tp).iter().copied())
                .map(|t| self.core_type(t))
                .collect();
            if !bounds.is_empty() {
                self.b.set_bounds(td, &bounds);
            }
        }
    }

    /// A catalog's type as this program's.
    fn core_type(&mut self, t: TypeId) -> TypeId {
        let cat = &catalog().program;
        match cat.types.get(t) {
            TypeData::Scalar(_) => t,
            TypeData::Nominal { decl, args } => {
                let d = self.external(decl);
                let args: Vec<TypeId> = (cat.types.list(args).iter().copied())
                    .map(|a| self.core_type(a))
                    .collect();
                self.b.applied(d, &args)
            }
            TypeData::Nullable(inner) => {
                let inner = self.core_type(inner);
                self.b.nullable(inner)
            }
            TypeData::Never(inner) => {
                let inner = self.core_type(inner);
                self.b.never(inner)
            }
            TypeData::TypeParam { decl } => {
                let d = self.external(decl);
                self.b.program().decls.get(d).ty
            }
            TypeData::Function { result, .. } => {
                let sig = cat.types.signature(t).expect("a signature");
                let params: Vec<TypeId> = sig.params.iter().map(|&p| self.core_type(p)).collect();
                let result = self.core_type(result);
                (self.b).function_type_with(&params, sig.optional, result)
            }
            TypeData::Result { pass, fail } => {
                let (pass, fail) = (self.core_type(pass), self.core_type(fail));
                self.b.result(pass, fail)
            }
        }
    }

    // Other modules' declarations.

    /// The module a top-level `Let`'s symbol is declared in, if it's
    /// top-level.
    fn module_of(&self, symbol: SymbolId) -> Option<ModuleId> {
        self.scopes
            .get(&self.s.scopes.symbol(symbol).owner)
            .copied()
    }

    /// The symbol a `Let` declares.
    fn let_symbol(&self, decl: FNode) -> Option<SymbolId> {
        let name = *self.children(decl).first()?;
        self.s.scopes.resolved(name)
    }

    /// What a top-level `Let` stands for, following imports: an import is
    /// a module-level `let f = f'`, `f'` another module's declaration. Not
    /// followed to a `var`: its import is a `let` initialized from it.
    fn chase(&self, decl: FNode) -> FNode {
        let mut d = decl;
        for _ in 0..64 {
            if self.kind(d) != NodeKind::Let {
                break;
            }
            let Some(symbol) = self.let_symbol(d) else {
                break;
            };
            let Some(here) = self.module_of(symbol) else {
                break;
            };
            let Some(init) = core::let_parts(&self.s.library.tree, d).2 else {
                break;
            };
            let Some(target) = self.s.scopes.resolved(init) else {
                break;
            };
            let target_symbol = self.s.scopes.symbol(target);
            // An imported `var` is copied when the import runs, as the
            // interpreter and Kotlin's backends read it, so it stays a `let`
            // of the importer's.
            if target_symbol.mutable {
                break;
            }
            match (self.module_of(target), target_symbol.decl) {
                (Some(there), Some(next)) if there != here => d = next,
                _ => break,
            }
        }
        d
    }

    /// Whether a top-level `Let` is an import's, standing for another
    /// module's declaration.
    fn is_import(&self, decl: FNode) -> bool {
        self.chase(decl) != decl
    }

    /// A `Let`'s declaration in tmpl, following imports: the library's
    /// own, or another library's, as an external declaration the first
    /// time it's used. `None` for what isn't top-level.
    fn top_decl(&mut self, decl: FNode, at: FNode) -> Tx<Option<DeclId>> {
        let decl = self.chase(decl);
        if let Some(&d) = self.decls.get(&decl) {
            return Ok(Some(d));
        }
        if let Some(&d) = self.external_lets.get(&decl) {
            return Ok(Some(d));
        }
        let Some(symbol) = self.let_symbol(decl) else {
            return Ok(None);
        };
        let Some(module) = self.module_of(symbol) else {
            return Ok(None);
        };
        let Home::Other { library, path } = self.homes[module.0 as usize].clone() else {
            return Ok(None);
        };
        let owner = self.external_module(&library, &path);
        let (name, _, init) = core::let_parts(&self.s.library.tree, decl);
        let name = self.name_of(name).to_string();
        let var = self.data(decl) & LET_VAR != 0;
        let d = match init.filter(|&i| self.kind(i) == NodeKind::Fn) {
            Some(f) => {
                // A generic one's type parameters first, which its
                // signature names, as std's `listJsonAdapter<T>`.
                let d = if core::type_params(&self.s.library.tree, f).is_empty() {
                    let sig = self.signature(f)?;
                    self.b.declare(DeclKind::Function, &name, owner, sig)
                } else {
                    let d = self
                        .b
                        .declare(DeclKind::Function, &name, owner, TypeId::NONE);
                    self.declare_type_params(f, d)?;
                    let sig = self.signature(f)?;
                    self.b.set_decl_type(d, sig);
                    d
                };
                let (params, ..) = core::fn_parts(&self.s.library.tree, f);
                let names: Vec<String> = (params.iter())
                    .map(|&p| {
                        let name = core::let_parts(&self.s.library.tree, p).0;
                        self.name_of(name).to_string()
                    })
                    .collect();
                let names: Vec<&str> = names.iter().map(String::as_str).collect();
                self.b.set_param_names(d, &names);
                d
            }
            None => {
                let ty = self.decl_ty(decl)?;
                let d = self.b.declare(DeclKind::ModuleLet, &name, owner, ty);
                if var {
                    self.b.add_facts(d, Facts::VAR);
                }
                d
            }
        };
        if self.s.scopes.symbol(symbol).exported {
            self.external_lets.insert(decl, d);
            Ok(Some(d))
        } else {
            self.not_yet(at, "another library's declaration that isn't exported")
        }
    }

    /// Another library's module, in its `uses` section, declared the first
    /// time.
    fn external_module(&mut self, library: &str, path: &str) -> DeclId {
        let key = (library.to_string(), path.to_string());
        if let Some(&m) = self.external_modules.get(&key) {
            return m;
        }
        let uses = match self.libraries.get(library) {
            Some(&u) => u,
            None => {
                let version = if library == "std" {
                    std_version()
                } else {
                    "0.0.0".to_string()
                };
                let u = self.b.uses(library, &version);
                self.libraries.insert(library.to_string(), u);
                u
            }
        };
        let m = self.b.external_module(uses, path);
        self.external_modules.insert(key, m);
        m
    }

    /// Another library's class or interface, by its symbol, declared the
    /// first time; `None` for the library's own.
    fn external_type(&mut self, symbol: SymbolId, at: FNode) -> Tx<Option<DeclId>> {
        if let Some(&d) = self.external_types.get(&symbol) {
            return Ok(Some(d));
        }
        let Some(module) = self.module_of(symbol) else {
            return Ok(None);
        };
        let Home::Other { library, path } = self.homes[module.0 as usize].clone() else {
            return Ok(None);
        };
        let s = self.s.scopes.symbol(symbol);
        let Some(class) = s.decl else {
            return self.not_yet(at, "a class or interface type");
        };
        let kind = if self.data(class) & core::CLASS_INTERFACE != 0 {
            DeclKind::Interface
        } else {
            DeclKind::Class
        };
        let name = self.s.library.names.get(s.name).to_string();
        let owner = self.external_module(&library, &path);
        let d = self.b.declare_type(kind, &name, owner);
        // std's connected class (`Date`), which a backend may connect to
        // a type of its own (`qname::by_connection`).
        let in_std = self.s.library.in_std(self.s.library.tree.span(class).start);
        if in_std && self.data(class) & core::CLASS_CONNECTED != 0 {
            self.b.add_facts(d, temper_tmpl::Facts::CONNECTED);
        }
        // Before its type parameters' bounds and its supertypes, which
        // may name it.
        self.external_types.insert(symbol, d);
        let formals = (self.s.typing.types.class_info(symbol))
            .map(|info| info.formals.clone())
            .unwrap_or_default();
        self.declare_formals(&formals, d, class)?;
        let supers = self.class_supers(class)?;
        self.b.set_supers(d, &supers);
        // A `@fun` interface, with its `apply`, which gives a function
        // literal or reference of its type its signature.
        if self.data(class) & core::CLASS_FUN != 0 {
            self.b.add_facts(d, temper_tmpl::Facts::FUN);
            self.external_method_named(d, "apply", at)?;
        }
        Ok(Some(d))
    }

    /// The module's `~console`, read: declared the first time.
    fn console(&mut self, call: FNode, at: FNode) -> Tx<NodeId> {
        let console = match self.console {
            Some((console, _)) => console,
            None => {
                let Some(interface) = self.core_decl("Console") else {
                    return self.not_yet(call, "console");
                };
                let ty = self.b.program().decls.get(interface).ty;
                let hints = ["console"];
                let console = self
                    .b
                    .declare_made(DeclKind::ModuleLet, &hints, self.module, ty);
                let span = self.span(call);
                self.b.set_decl_spans(console, span, span);
                self.console = Some((console, call));
                console
            }
        };
        let read = self.b.get_module_let(console);
        Ok(self.own(read, at))
    }

    // Modules.

    /// Adds a module's files, and gives the module its first file's span.
    fn module_files(&mut self, m: &crate::settle::SettledModule, module: DeclId) {
        let lib = &self.s.library;
        let file_ids = lib.modules[m.id.0 as usize].files.clone();
        for f in &file_ids {
            let file = &lib.files[f.0 as usize];
            let path = file.path.rsplit(['/', '\\']).next().unwrap_or(&file.path);
            let source = file.parsed.source();
            let fid = self.b.add_file(path, hash(source));
            self.b.set_file_text(fid, source);
            self.files.insert(f.0, fid);
        }
        if let Some(f) = file_ids.first() {
            let file = &lib.files[f.0 as usize];
            let fid = self.files[&f.0];
            let len = file.parsed.source().len() as u32;
            let whole = Span {
                file: fid,
                start: 0,
                end: len,
            };
            self.b.set_decl_spans(module, whole, whole);
        }
    }

    fn module(&mut self, m: &crate::settle::SettledModule, module: DeclId) -> Tx<()> {
        let (id, top_levels) = (m.id, &m.top_levels);
        let lib = &self.s.library;
        self.module = module;
        self.owner = module;
        self.console = None;
        let file_ids = lib.modules[id.0 as usize].files.clone();
        // Imports, tests, and type aliases aren't items.
        let top_levels: Vec<FNode> = (top_levels.iter().copied())
            .filter(|&n| {
                self.kind(n) != NodeKind::Let
                    || !(self.is_import(n) || m.test_cases.contains(&n) || self.is_type_alias(n, 0))
            })
            .collect();
        // Declare first, so a body may use what's declared after it.
        let local_classes = self.local_classes(&top_levels);
        for &node in &top_levels {
            if self.kind(node) == NodeKind::Let {
                self.declare_top_level(node)?;
            }
        }
        let mut init = Vec::new();
        let mut items = Vec::new();
        let mut failed = false;
        for &node in &top_levels {
            // Each at the module's level, even after a gap left a function
            // part done ([`gaps`]).
            self.owner = module;
            self.result = TypeId::NONE;
            self.this_class = None;
            self.catching.clear();
            // Classes declared in functions first, at the module's level.
            for &(class, top) in &local_classes {
                if top == node {
                    let item = self.class_item(class, &mut init).map(Some);
                    items.extend(self.gap(item)?);
                }
            }
            if self.kind(node) == NodeKind::Class {
                let item = self.class_item(node, &mut init).map(Some);
                items.extend(self.gap(item)?);
                continue;
            }
            if self.kind(node) != NodeKind::Let {
                // Statements after an error can't run, and Java and Rust
                // reject them; declarations stay.
                if !failed {
                    self.stmt(node, &mut init)?;
                    failed = self.fails(node);
                }
                continue;
            }
            let decl = self.decls[&node];
            if self.functions.contains(&node) {
                let item = self.function(node, decl).map(Some);
                items.extend(self.gap(item)?);
                continue;
            }
            if self.bodiless.contains(&node) {
                items.push(self.bodiless_function(node, decl)?);
                continue;
            }
            let item = self.b.module_let(decl);
            items.push(self.own(item, node));
            // A `let` of an error is that error, and what follows can't run.
            if failed || self.stored_error(node).is_some() {
                if !failed {
                    self.stmt(node, &mut init)?;
                    failed = true;
                }
                continue;
            }
            if let Some(value) = core::let_parts(&self.s.library.tree, node).2 {
                let value = self.expr(value)?;
                let value = self.fit(value, self.b.program().decls.get(decl).ty);
                let stmt = self.b.init_let(decl, value);
                init.push(self.own(stmt, node));
            }
        }
        // Tests before the `~console`, which they may be first to use.
        let mut tests = Vec::new();
        for &case in &m.test_cases {
            self.owner = module;
            let test = self.test(case).map(Some);
            tests.extend(self.gap(test)?);
        }
        if let Some((console, call)) = self.console {
            let Some(get) = self.core_decl("getConsole") else {
                return self.not_yet(call, "console");
            };
            let omitted = self.b.omitted(TypeId::STRING);
            let omitted = self.made(omitted, call);
            let value = self.b.call(get, &[omitted]);
            let value = self.made(value, call);
            let stmt = self.b.init_let(console, value);
            init.insert(0, self.made(stmt, call));
            let item = self.b.module_let(console);
            items.insert(0, self.made(item, call));
        }
        for item in items {
            self.b.add_item(module, item);
        }
        let block = self.b.block(&init);
        let block = match file_ids.first() {
            Some(f) => {
                let root = lib.files[f.0 as usize].root;
                self.made(block, root)
            }
            None => block,
        };
        self.b.set_init(module, block);
        for test in tests {
            self.b.add_test(module, test);
        }
        Ok(())
    }

    /// A test's parameter's type, which typing doesn't record: the class
    /// its written type names, std's `Test`.
    fn test_param_type(&mut self, param: FNode) -> Tx<TypeId> {
        let written = core::let_parts(&self.s.library.tree, param).1;
        let class = written
            .and_then(|w| self.s.scopes.resolved(w))
            .and_then(|s| self.s.scopes.symbol(s).decl)
            .map(|d| self.chase(d));
        let symbol = class.and_then(|c| {
            let name = *self.children(c).first()?;
            self.s.scopes.resolved(name)
        });
        match symbol {
            Some(symbol) => match self.external_type(symbol, param)? {
                Some(decl) => Ok(self.b.nominal(decl)),
                None => self.not_yet(param, "a test's parameter type"),
            },
            None => self.not_yet(param, "a test's parameter type"),
        }
    }

    /// A constant string's text, as Kotlin folds a test's name: a
    /// literal, a template of constant parts, or a module `let` holding
    /// one, as `"a test ${name}"` for `let name = "case"`.
    fn const_string(&self, node: FNode, depth: u32) -> Option<String> {
        if let Some(Value::String(text)) = self.s.library.value(node) {
            return Some(text.clone());
        }
        if depth > 16 {
            return None;
        }
        match self.kind(node) {
            NodeKind::RightName => {
                let symbol = self.s.scopes.resolved(node)?;
                let s = self.s.scopes.symbol(symbol);
                let decl = s.decl?;
                if s.mutable || self.kind(decl) != NodeKind::Let || self.data(decl) & LET_VAR != 0 {
                    return None;
                }
                let init = core::let_parts(&self.s.library.tree, decl).2?;
                self.const_string(init, depth + 1)
            }
            NodeKind::Call => {
                let Some(Resolved::Builtin(key)) = self.s.typing.resolved(node) else {
                    return None;
                };
                if key != "String.cat" {
                    return None;
                }
                let mut out = String::new();
                for &part in &self.children(node)[1..] {
                    out.push_str(&self.const_string(part, depth + 1)?);
                }
                Some(out)
            }
            _ => None,
        }
    }

    /// A test, from its `let t# = new Pair(name, fn (test: Test): Void
    /// throws Bubble { … })`.
    fn test(&mut self, case: FNode) -> Tx<NodeId> {
        let init = core::let_parts(&self.s.library.tree, case).2;
        let parts = init.map(|i| self.children(i).to_vec()).unwrap_or_default();
        let [_, _, name, f] = parts[..] else {
            return self.not_yet(case, "a test's form");
        };
        let Some(text) = self.const_string(name, 0) else {
            return self.not_yet(name, "a test's name that isn't a constant string");
        };
        if self.kind(f) != NodeKind::Fn {
            return self.not_yet(f, "a test's form");
        }
        // `fn (test: Test): Void throws Bubble`, as `testing` makes it.
        let mut types = Vec::new();
        for p in core::fn_parts(&self.s.library.tree, f).0 {
            let ty = match self.s.typing.decls.get(&p) {
                Some(_) => self.decl_ty(p)?,
                None => self.test_param_type(p)?,
            };
            types.push(ty);
        }
        let result = self.b.result(TypeId::VOID, TypeId::BUBBLE);
        let sig = self.b.function_type(&types, result);
        let decl = self
            .b
            .declare_made(DeclKind::Function, &["test"], self.module, sig);
        self.decl_spans(decl, case);
        let (params, body) = self.function_parts(f, decl)?;
        let test = self.b.test(decl, &text, &params, body);
        Ok(self.own(test, case))
    }

    /// Declares a generic function's type parameters (`node` is its
    /// `Fn`), owned by `owner`, with their bounds.
    fn declare_type_params(&mut self, node: FNode, owner: DeclId) -> Tx<()> {
        let params = core::type_params(&self.s.library.tree, node).to_vec();
        let mut symbols = Vec::new();
        for &tp in &params {
            let Some(symbol) = self.let_symbol(tp) else {
                return self.not_yet(tp, "a type parameter's form");
            };
            symbols.push(symbol);
        }
        self.declare_formals(&symbols, owner, node)
    }

    /// Declares type parameters, by their symbols, owned by `owner`, with
    /// their bounds.
    fn declare_formals(&mut self, symbols: &[SymbolId], owner: DeclId, at: FNode) -> Tx<()> {
        if symbols.is_empty() {
            return Ok(());
        }
        let mut made = Vec::new();
        for &symbol in symbols {
            let Some(tp) = self.s.scopes.symbol(symbol).decl else {
                return self.not_yet(at, "a type parameter's form");
            };
            let name = core::let_parts(&self.s.library.tree, tp).0;
            let name = self.name_of(name).to_string();
            let d = self.b.declare_type_param(&name, owner, &[]);
            self.decl_spans(d, tp);
            self.type_params.insert(symbol, d);
            made.push((d, symbol, tp));
        }
        let ids: Vec<DeclId> = made.iter().map(|m| m.0).collect();
        self.b.set_type_params(owner, &ids);
        for (d, symbol, tp) in made {
            match self.s.typing.types.bound_of(symbol) {
                Some(b) if b != ft::ANY_VALUE && self.s.typing.types.is_known(b) => {
                    let b = self.ty(b, tp)?;
                    self.b.set_bounds(d, &[b]);
                }
                _ => {}
            }
        }
        Ok(())
    }

    /// The type arguments typing solved for a generic call of `callee`,
    /// (a constructor's, its class's), as tmpl's types; none if it isn't
    /// generic.
    fn call_type_args(&mut self, call: FNode, callee: DeclId) -> Tx<Vec<TypeId>> {
        let generic = match self.b.program().decls.get(callee).kind {
            DeclKind::Constructor => self.b.program().decls.get(callee).owner,
            _ => callee,
        };
        let wanted = self.b.program().decls.type_params(generic).len();
        if wanted == 0 {
            return Ok(Vec::new());
        }
        // A constructor's class's, from the type it makes.
        if generic != callee {
            let ty = self.expr_ty(call)?;
            let args = self.b.program().types.args(ty).to_vec();
            if args.len() == wanted {
                return Ok(args);
            }
            return self.not_yet(call, "a generic call's type arguments");
        }
        let Some(args) = self.s.typing.type_args(call) else {
            return self.not_yet(call, "a generic call's type arguments");
        };
        let args = args.to_vec();
        if args.len() != wanted {
            return self.not_yet(call, "a generic call's type arguments");
        }
        let mut made = Vec::new();
        for a in args {
            if !self.s.typing.types.is_known(a) {
                return self.not_yet(call, "a generic call's type arguments");
            }
            made.push(self.ty(a, call)?);
        }
        Ok(made)
    }

    /// Declares a top-level `let`: a function, or a module `let` or `var`.
    fn declare_top_level(&mut self, node: FNode) -> Tx<()> {
        let (name, _, init) = core::let_parts(&self.s.library.tree, node);
        let var = self.data(node) & LET_VAR != 0;
        let in_std = (self.s.library).in_std(self.s.library.tree.span(node).start);
        if self.data(node) & core::MEMBER_CONNECTED != 0 {
            // A bodiless `@connected` function, which the library's own
            // host code implements, or std's, which each backend's
            // connection table does (`std/net.sendRequest()`).
            let name = self.name_of(name).to_string();
            let decl = self
                .b
                .declare(DeclKind::Function, &name, self.module, TypeId::NONE);
            let Some(&t) = self.s.typing.decls.get(&node) else {
                return self.not_yet(node, "an expression of unknown type");
            };
            let sig = self.signature_of(t, node)?;
            self.b.set_decl_type(decl, sig);
            self.b.add_facts(decl, Facts::CONNECTED);
            if self.is_exported(node) {
                self.b.add_facts(decl, Facts::EXPORTED);
            }
            self.keep_facts(decl, node);
            self.decl_spans(decl, node);
            self.decls.insert(node, decl);
            self.bodiless.insert(node);
            return Ok(());
        }
        let is_fn = init.is_some_and(|i| self.kind(i) == NodeKind::Fn);
        let name = self.name_of(name).to_string();
        let decl = if is_fn {
            if var {
                return self.not_yet(node, "a function in a var");
            }
            let f = init.unwrap();
            let decl = self
                .b
                .declare(DeclKind::Function, &name, self.module, TypeId::NONE);
            self.declare_type_params(f, decl)?;
            let sig = self.signature(f)?;
            self.b.set_decl_type(decl, sig);
            self.functions.insert(node);
            // A `@connected` function with a Temper body: connected for the
            // backends it lists, or all if it lists none; and for none if
            // its list is empty, as `@connected([])`.
            if let Some(c) = self.s.library.connected.get(&node).filter(|_| !in_std) {
                match &c.backends {
                    None => self.b.add_facts(decl, Facts::CONNECTED),
                    Some(listed) if listed.is_empty() => {}
                    Some(listed) => {
                        let listed: Vec<&str> = listed.iter().map(String::as_str).collect();
                        self.b.set_connected(decl, &listed);
                    }
                }
            }
            decl
        } else {
            let ty = self.decl_ty(node)?;
            let decl = self.b.declare(DeclKind::ModuleLet, &name, self.module, ty);
            if var {
                self.b.add_facts(decl, Facts::VAR);
            }
            decl
        };
        if self.is_exported(node) {
            self.b.add_facts(decl, Facts::EXPORTED);
        }
        self.keep_facts(decl, node);
        self.decl_spans(decl, node);
        self.decls.insert(node, decl);
        Ok(())
    }

    /// `KEEP` for a top-level declaration marked `@keep`, or any where the
    /// library keeps all
    /// ([`Library::keep_all`](crate::library::Library::keep_all)), and `KEEP_TEST` for one
    /// marked `@keepTest`.
    fn keep_facts(&mut self, decl: DeclId, node: FNode) {
        if self.s.library.keep_all || self.s.library.kept.contains(&node) {
            self.b.add_facts(decl, Facts::KEEP);
        }
        if self.s.library.kept_for_tests.contains(&node) {
            self.b.add_facts(decl, Facts::KEEP_TEST);
        }
    }

    fn function(&mut self, node: FNode, decl: DeclId) -> Tx<NodeId> {
        let f = core::let_parts(&self.s.library.tree, node).2.unwrap();
        let (params, block) = self.function_parts(f, decl)?;
        let function = self.b.function(decl, &params, block);
        Ok(self.own(function, node))
    }

    /// A bodiless `@connected` function: its parameters, named as written,
    /// typed by its signature.
    fn bodiless_function(&mut self, node: FNode, decl: DeclId) -> Tx<NodeId> {
        let names = (self.s.library.connected.get(&node))
            .map(|c| c.params.clone())
            .unwrap_or_default();
        let types: Vec<TypeId> =
            (self.b.program().signature(decl)).map_or_else(Vec::new, |s| s.params.to_vec());
        if names.len() != types.len() {
            return self.not_yet(node, "a connected function's parameters");
        }
        let mut params = Vec::new();
        for (name, ty) in names.iter().zip(types) {
            let d = self.b.declare(DeclKind::Param, name, decl, ty);
            self.decl_spans(d, node);
            let param = self.b.param(d);
            params.push(self.own(param, node));
        }
        let function = self.b.bodiless_function(decl, &params);
        Ok(self.own(function, node))
    }

    /// A function's parameters and body.
    fn function_parts(&mut self, f: FNode, decl: DeclId) -> Tx<(Vec<NodeId>, NodeId)> {
        let result = self
            .b
            .program()
            .signature(decl)
            .map_or(TypeId::NONE, |s| s.pass);
        self.function_parts_with(f, decl, result)
    }

    /// A function's parameters and body, its returns' values typed
    /// `result`: `Void` for a generator's body, whose signature's result
    /// is the generator its calls make.
    fn function_parts_with(
        &mut self,
        f: FNode,
        decl: DeclId,
        result: TypeId,
    ) -> Tx<(Vec<NodeId>, NodeId)> {
        let (params, _, body) = core::fn_parts(&self.s.library.tree, f);
        let Some(body) = body else {
            return self.not_yet(f, "a connected function");
        };
        let (owner, outer) = (
            std::mem::replace(&mut self.owner, decl),
            std::mem::replace(&mut self.result, result),
        );
        let types: Vec<TypeId> =
            (self.b.program().signature(decl)).map_or_else(Vec::new, |s| s.params.to_vec());
        let mut param_nodes = Vec::new();
        let mut defaulted = Vec::new();
        for (i, p) in params.into_iter().enumerate() {
            if self.kind(p) != NodeKind::Let {
                return self.not_yet(p, "a parameter that isn't a name");
            }
            let (name, _, default) = core::let_parts(&self.s.library.tree, p);
            let ty = match types.get(i) {
                Some(&ty) => ty,
                None => self.decl_ty(p)?,
            };
            let name = self.name_of(name).to_string();
            let d = self.b.declare(DeclKind::Param, &name, decl, ty);
            if self.data(p) & LET_VAR != 0 {
                if default.is_some() {
                    return self.not_yet(p, "a var parameter's default");
                }
                self.b.add_facts(d, Facts::VAR);
            }
            if default.is_some() {
                self.b.add_facts(d, Facts::OPTIONAL);
                let inner = self.default_ty(p)?;
                defaulted.push((p, d, inner));
            }
            self.decl_spans(d, p);
            self.decls.insert(p, d);
            let param = self.b.param(d);
            param_nodes.push(self.own(param, p));
        }
        let mut stmts = Vec::new();
        self.defaults(&defaulted, &mut stmts)?;
        // A `Never<Void>` function's body runs as a `Void` one's, its
        // end never reached.
        if self.b.program().types.fallback(result) == TypeId::VOID {
            self.stmts(body, &mut stmts)?;
        } else {
            self.tail_stmts(body, &mut stmts)?;
            // A body that can reach its end without a value, where typing
            // reports one at its closing brace, fails there, on every
            // target: there's no value to run on with (a type-only error,
            // but the interpreter runs on).
            let span = self.s.library.tree.span(body);
            let end = (span.end.saturating_sub(1), span.end);
            let found = self.errors.get(&end).or_else(|| self.type_errors.get(&end));
            if let Some(message) = found.cloned() {
                let error = self.b.error(TypeId::VOID, &message);
                let error = self.made(error, body);
                let stmt = self.b.expr_statement(error);
                stmts.push(self.made(stmt, body));
            }
        }
        let block = self.b.block(&stmts);
        let block = self.own(block, body);
        self.owner = owner;
        self.result = outer;
        Ok((param_nodes, block))
    }

    // Statements.

    /// A block's statements, or a statement's, without a block around them.
    fn stmts(&mut self, node: FNode, out: &mut Vec<NodeId>) -> Tx<()> {
        if self.s.typing.is_unreachable(node) {
            out.push(self.unreachable(node, TypeId::VOID));
            return Ok(());
        }
        if self.kind(node) == NodeKind::Block && !self.is_postfix_step(node) {
            let children = self.children(node).to_vec();
            self.predeclare_functions(&children)?;
            for &child in children.iter() {
                self.stmt(child, out)?;
                // What follows a loop that never ends, a call that never
                // returns, a jump, or an error, can't run, and Java and
                // Rust reject it.
                if self.never_ends(child)
                    || self.never_returns(child)
                    || self.always_jumps(child)
                    || self.fails(child)
                {
                    break;
                }
            }
            Ok(())
        } else {
            self.stmt(node, out)
        }
    }

    /// A block, as a statement's part: a branch or a loop's body.
    fn block(&mut self, node: FNode) -> Tx<NodeId> {
        let mut stmts = Vec::new();
        self.stmts(node, &mut stmts)?;
        let block = self.b.block(&stmts);
        Ok(self.own(block, node))
    }

    fn stmt(&mut self, node: FNode, out: &mut Vec<NodeId>) -> Tx<()> {
        let done = self.stmt_or_gap(node, out);
        self.gap(done)
    }

    fn stmt_or_gap(&mut self, node: FNode, out: &mut Vec<NodeId>) -> Tx<()> {
        // A `catch (e)`'s `let e = caught()`, whose `catch` declares `e`.
        if self.adopted.contains(&node) {
            return Ok(());
        }
        // An error, or a `let` or assignment of one, which fails first.
        if let Some(message) = self.error_at(node).or_else(|| self.stored_error(node)) {
            let error = self.b.error(TypeId::VOID, &message);
            let error = self.own(error, node);
            let stmt = self.b.expr_statement(error);
            out.push(self.own(stmt, node));
            return Ok(());
        }
        // One with a part that always jumps, as `f(a, return b)`: what
        // runs before the jump, then the jump.
        if self.jumps_from_part(node) {
            return self.jump_stmts(node, out);
        }
        let children = self.children(node).to_vec();
        let stmt = match self.kind(node) {
            // A value or a name read alone does nothing.
            NodeKind::Value | NodeKind::RightName => return Ok(()),
            // `ignore(x)` is `x` alone, its value dropped.
            NodeKind::Call
                if children.len() == 2
                    && self.function_of(children[0]) == Some(Function::Ignore) =>
            {
                return self.stmt(children[1], out);
            }
            NodeKind::Block if self.is_postfix_step(node) => {
                // `i++` alone, `{ let t = i; i = t + 1; t }`, is `i = i + 1`.
                let [t, assign, _] = children[..] else {
                    unreachable!()
                };
                let target = core::let_parts(&self.s.library.tree, t).2;
                let Some(&decl) = target
                    .and_then(|x| self.declaration(x))
                    .and_then(|d| self.decls.get(&d))
                else {
                    return self.not_yet(node, "an assignment to something not declared here");
                };
                self.decls.insert(t, decl);
                return self.stmt(assign, out);
            }
            NodeKind::Block => self.block(node)?,
            NodeKind::Let if self.jumps_in_init(node) => {
                return self.let_jumping(node, out);
            }
            NodeKind::Let => self.let_stmt(node)?,
            NodeKind::Assign => {
                let [target, value] = children[..] else {
                    return self.not_yet(node, "an assignment's form");
                };
                if let Some((object, m)) = self.member_target(node, target)? {
                    let stmt = self.assign_member(m, object, Assigned::Expr(value), node)?;
                    out.push(self.own(stmt, node));
                    return Ok(());
                }
                let decl = self.declaration(target).and_then(|d| self.decls.get(&d));
                let Some(&decl) = decl else {
                    return self.not_yet(node, "an assignment to something not declared here");
                };
                let value = self.expr(value)?;
                let value = self.type_error_around(node, value);
                let value = self.fit(value, self.b.program().decls.get(decl).ty);
                if self.b.program().decls.get(decl).kind == DeclKind::ModuleLet {
                    self.b.set_module_let(decl, value)
                } else {
                    self.b.assign(decl, value)
                }
            }
            NodeKind::If => {
                let [cond, then, els] = children[..] else {
                    return self.not_yet(node, "an if's form");
                };
                let cond = self.condition(cond)?;
                let then = self.block(then)?;
                let empty = self.kind(els) == NodeKind::Block && self.children(els).is_empty();
                let els = if empty { None } else { Some(self.block(els)?) };
                self.b.if_stmt(cond, then, els)
            }
            NodeKind::Loop => self.loop_stmt(node)?,
            NodeKind::Break | NodeKind::Continue => {
                let target = self.jump_target(FNode(self.data(node)));
                let Some(&label) = self.loops.get(&target) else {
                    return self.not_yet(node, "a jump out of a labeled block");
                };
                if self.kind(node) == NodeKind::Break {
                    self.b.break_loop(label)
                } else if let Some(&step) = self.steps.get(&target) {
                    // `{ step; continue loop; }`, a copy of the step with
                    // its own locals; a step has no `continue` of its own
                    // ([`Self::loop_stmt`]), so copies hold no copies
                    // (notes/tmpl/nodes.md, "A `for` loop's step").
                    let mut copy = Vec::new();
                    self.forget_locals(step);
                    self.stmts(step, &mut copy)?;
                    let jump = self.b.continue_loop(label);
                    copy.push(self.own(jump, node));
                    self.b.block(&copy)
                } else {
                    self.b.continue_loop(label)
                }
            }
            NodeKind::Return => {
                if self.result.is_none() {
                    return self.not_yet(node, "a return outside a function");
                }
                let value = children.first().copied();
                let value = match value {
                    Some(v) if self.result == TypeId::VOID => {
                        if !matches!(self.s.library.value(v), Some(Value::Void)) {
                            self.stmt(v, out)?;
                        }
                        None
                    }
                    Some(v) => {
                        let v = self.expr(v)?;
                        Some(self.fit(v, self.result))
                    }
                    None => None,
                };
                self.b.return_stmt(value)
            }
            NodeKind::Call if self.for_each_parts(node).is_some() => {
                let (object, f) = self.for_each_parts(node).unwrap();
                self.for_each(node, object, f, None)?
            }
            NodeKind::Call => {
                let call = self.expr(node)?;
                // `panic()` alone, typed by its place.
                let call = self.fit(call, TypeId::VOID);
                self.b.expr_statement(call)
            }
            NodeKind::Orelse if self.or_else_jumps(node) => {
                return self.or_else_jumping(node, None, out);
            }
            NodeKind::Orelse => {
                let value = self.or_else_typed(node, Some(TypeId::VOID))?;
                self.b.expr_statement(value)
            }
            NodeKind::Fail => {
                let value = self.expr(node)?;
                let value = self.fit(value, TypeId::VOID);
                self.b.expr_statement(value)
            }
            NodeKind::Yield => {
                // A bare `yield` is `yield(null)`, which an `Empty`
                // generator's tmpl writes without a value.
                let value = (children.first().copied())
                    .filter(|&v| self.s.library.value(v) != Some(&Value::Null));
                let value = match (self.yielded_type(), value) {
                    (Some(y), Some(v)) => {
                        let v = self.expr(v)?;
                        Some(self.fit(v, y))
                    }
                    (None, None) => None,
                    (None, Some(_)) => return self.not_yet(node, "a yield of Empty's value"),
                    (Some(y), None) if self.b.program().types.is_nullable(y) => {
                        Some(self.b.null(y))
                    }
                    (Some(_), None) => return self.not_yet(node, "a bare yield of null"),
                };
                self.b.yield_stmt(value)
            }
            NodeKind::Await => {
                let value = self.await_expr(node)?;
                self.b.expr_statement(value)
            }
            // Declared at the module's level (`local_classes`).
            NodeKind::Class if self.classes.contains_key(&node) => return Ok(()),
            NodeKind::Class => return self.not_yet(node, "a class"),
            NodeKind::Fn => return self.not_yet(node, "a function value"),
            kind => return self.not_yet(node, format!("{kind:?}")),
        };
        out.push(self.own(stmt, node));
        Ok(())
    }

    /// Declares, ahead of their `let`s, the local functions that a run of
    /// them in a block calls before they're declared: itself, or one after
    /// it in the run (`functions/locals`' `walker` and `putter`). tmpl
    /// lets a run of function `let`s use each other, as a group
    /// ([`temper_tmpl::analysis::function_groups`]).
    fn predeclare_functions(&mut self, children: &[FNode]) -> Tx<()> {
        let is_fn_let = |t: &Self, c: FNode| {
            t.kind(c) == NodeKind::Let
                && t.data(c) & LET_VAR == 0
                && core::let_parts(&t.s.library.tree, c)
                    .2
                    .is_some_and(|i| t.kind(i) == NodeKind::Fn)
        };
        let mut i = 0;
        while i < children.len() {
            let mut end = i;
            while end < children.len() && is_fn_let(self, children[end]) {
                end += 1;
            }
            let run = &children[i..end];
            for (j, &member) in run.iter().enumerate() {
                let early = run[..=j].iter().any(|&before| {
                    let init = core::let_parts(&self.s.library.tree, before).2.unwrap();
                    self.refers_to(init, member)
                });
                if early && !self.decls.contains_key(&member) {
                    self.declare_let(member)?;
                }
            }
            i = end.max(i + 1);
        }
        Ok(())
    }

    /// A local's declaration for a `Let`.
    fn declare_let(&mut self, node: FNode) -> Tx<DeclId> {
        let name = core::let_parts(&self.s.library.tree, node).0;
        let ty = self.decl_ty(node)?;
        let name = self.name_of(name).to_string();
        let local = self.b.declare(DeclKind::Local, &name, self.owner, ty);
        if self.data(node) & LET_VAR != 0 {
            self.b.add_facts(local, Facts::VAR);
        }
        self.decl_spans(local, node);
        self.decls.insert(node, local);
        Ok(local)
    }

    fn let_stmt(&mut self, node: FNode) -> Tx<NodeId> {
        let init = core::let_parts(&self.s.library.tree, node).2;
        if init.is_some_and(|i| self.kind(i) == NodeKind::Fn)
            && self.refers_to(init.unwrap(), node)
            && !self.decls.contains_key(&node)
        {
            return self.not_yet(node, "a local function that calls itself here");
        }
        let local = match self.decls.get(&node) {
            Some(&local) => local,
            None => self.declare_let(node)?,
        };
        let ty = self.b.program().decls.get(local).ty;
        let init = match init {
            Some(init) => {
                // An `if` or `when` typing types `Null` or `Never` is of
                // the type declared ([`Self::if_value_in`]).
                let bottom = matches!(self.s.typing.expr(init), Some(ft::NULL | ft::NEVER));
                let init = match self.kind(init) {
                    NodeKind::If => self.if_value_in(init, ty)?,
                    NodeKind::Block if bottom => self.block_value(init, ty)?,
                    _ => self.expr(init)?,
                };
                Some(self.fit(init, ty))
            }
            None => None,
        };
        Ok(self.b.let_stmt(local, init))
    }

    /// Whether a `let`'s initializer is an `if` (or a `when`, which is
    /// one) with a branch that jumps, as std's `escapeXml` has:
    /// `let esc = when (c) { … -> continue; else -> "…" }`.
    fn jumps_in_init(&self, node: FNode) -> bool {
        let init = core::let_parts(&self.s.library.tree, node).2;
        init.is_some_and(|i| self.value_jumps(i))
    }

    /// Whether a node is an `orelse` whose fallback jumps, as `x orelse
    /// continue` or `x orelse do { continue }`.
    fn or_else_jumps(&self, node: FNode) -> bool {
        self.kind(node) == NodeKind::Orelse
            && (self.children(node).get(1)).is_some_and(|&f| self.branch_jumps(f))
    }

    /// An `orelse` whose fallback jumps ([`Self::or_else_jumps`]), alone
    /// or as `local`'s initializer: tmpl has no jump as a value, so the
    /// body's value is held in a nullable local in an `orelse` of `Void`;
    /// then, where the body failed (the holder is null, or for a nullable
    /// value, a flag the `orelse`'s fallback sets says so), the fallback
    /// runs as statements, assigning `local` in any branch that doesn't
    /// jump, and otherwise `local` is what's held.
    fn or_else_jumping(
        &mut self,
        node: FNode,
        target: Option<(DeclId, TypeId)>,
        out: &mut Vec<NodeId>,
    ) -> Tx<()> {
        let [body, fallback] = self.children(node).to_vec()[..] else {
            return self.not_yet(node, "an orelse's form");
        };
        let ty = target.map_or(TypeId::VOID, |(_, ty)| ty);
        self.catching.push(self.owner);
        let value = self.block_value(body, ty);
        self.catching.pop();
        let value = value?;
        // A body that can't fail is all there is to it.
        if !self.may_bubble(value) {
            let made = match target {
                Some((local, ty)) => {
                    let value = self.fit(value, ty);
                    self.b.assign(local, value)
                }
                None => {
                    let value = self.fit_void(value);
                    self.b.expr_statement(value)
                }
            };
            out.push(self.made(made, node));
            return Ok(());
        }
        let span = self.span(node);
        let mut local_made = |t: &mut Self, hint: &str, ty: TypeId, init: NodeId| {
            let local = (t.b).declare_made(DeclKind::Local, &[hint], t.owner, ty);
            t.b.add_facts(local, Facts::VAR);
            t.b.set_decl_spans(local, span, span);
            let init = t.made(init, node);
            let declared = t.b.let_stmt(local, Some(init));
            out.push(t.made(declared, node));
            local
        };
        // Where the value can't be null, null in the holder says the body
        // failed, as a checker can follow (`if held is None: …`); else a
        // flag says so.
        let nullable = target.is_none_or(|(_, ty)| self.b.program().types.is_nullable(ty));
        let failed = match nullable {
            true => {
                let no = self.b.bool_lit(false);
                Some(local_made(self, "failed", TypeId::BOOLEAN, no))
            }
            false => None,
        };
        // A fallback that needs the failure, from a body that fails with a
        // failure type: a `catch` binds it, and keeps it in a nullable
        // local for the fallback, which runs after.
        let bound = match self.needs_failure(fallback) {
            true => self.handled_type(value),
            false => None,
        };
        let caught_held = bound.map(|ty| {
            let holder = self.b.nullable(ty);
            let null = self.b.null(holder);
            let held = local_made(self, "caught", holder, null);
            let local = (self.b).declare_made(DeclKind::Local, &["failure"], self.owner, ty);
            self.b.set_decl_spans(local, span, span);
            (local, held)
        });
        let (held, body_stmt) = match target {
            Some((_, ty)) => {
                let holder = self.b.nullable(ty);
                let null = self.b.null(holder);
                let held = local_made(self, "held", holder, null);
                let value = self.fit(value, holder);
                (Some(held), self.b.assign(held, value))
            }
            None => {
                let value = self.fit_void(value);
                (None, self.b.expr_statement(value))
            }
        };
        let body_stmt = self.made(body_stmt, node);
        let void = self.b.void();
        let void = self.made(void, node);
        let body = self.b.block_value(TypeId::VOID, &[body_stmt], void);
        let body = self.made(body, node);
        let mut caught = Vec::new();
        if let Some((local, held)) = caught_held {
            let read = self.b.local(local);
            let read = self.made(read, node);
            let read = self.b.as_nullable(read);
            let read = self.made(read, node);
            let set = self.b.assign(held, read);
            caught.push(self.made(set, node));
        }
        if let Some(failed) = failed {
            let yes = self.b.bool_lit(true);
            let yes = self.made(yes, node);
            let set = self.b.assign(failed, yes);
            caught.push(self.made(set, node));
        }
        let void = self.b.void();
        let void = self.made(void, node);
        let caught = match caught.is_empty() {
            true => void,
            false => {
                let made = self.b.block_value(TypeId::VOID, &caught, void);
                self.made(made, node)
            }
        };
        let made = match caught_held {
            Some((local, _)) => self.b.catch(TypeId::VOID, local, body, caught),
            None => self.b.or_else(TypeId::VOID, body, caught),
        };
        let made = self.made(made, node);
        let made = self.b.expr_statement(made);
        out.push(self.made(made, node));
        self.handling.push(failures::Handling {
            owner: self.owner,
            caught: caught_held.map(|(_, held)| failures::Caught::Held(held)),
        });
        let translated = self.jumping_fallback(fallback, target, held, nullable, node);
        self.handling.pop();
        let (stmts, els) = translated?;
        let then = self.b.block(&stmts);
        let then = self.made(then, fallback);
        let check = match (failed, held) {
            (Some(failed), _) => self.b.local(failed),
            (None, Some(held)) => {
                let read = self.b.local(held);
                let read = self.made(read, node);
                self.b.is_null(read)
            }
            (None, None) => unreachable!("a flag or a holder"),
        };
        let check = self.made(check, node);
        let made = self.b.if_stmt(check, then, els);
        out.push(self.own(made, node));
        Ok(())
    }

    /// A jumping `orelse`'s fallback ([`Self::or_else_jumping`]), as
    /// statements, and, where it has a target, the `else` that assigns
    /// it what's held.
    fn jumping_fallback(
        &mut self,
        fallback: FNode,
        target: Option<(DeclId, TypeId)>,
        held: Option<DeclId>,
        nullable: bool,
        node: FNode,
    ) -> Tx<(Vec<NodeId>, Option<NodeId>)> {
        let mut stmts = Vec::new();
        let els = match (target, held) {
            (Some((local, ty)), Some(held)) => {
                self.assign_branches(fallback, local, ty, &mut stmts)?;
                let read = self.b.local(held);
                let read = self.made(read, node);
                let read = if nullable {
                    read
                } else {
                    let made = self.b.non_null(read);
                    self.made(made, node)
                };
                let assign = self.b.assign(local, read);
                let assign = self.made(assign, node);
                let els = self.b.block(&[assign]);
                Some(self.made(els, node))
            }
            _ => {
                self.stmt(fallback, &mut stmts)?;
                None
            }
        };
        Ok((stmts, els))
    }

    /// Whether a value ends in a jump (a `break`, `continue`, or
    /// `return`), in any branch of its `if`s.
    fn branch_jumps(&self, node: FNode) -> bool {
        // `{ continue; void }`, as a `when`'s branch has it.
        if self.always_jumps(node) {
            return true;
        }
        match self.kind(node) {
            NodeKind::Block => (self.children(node).last()).is_some_and(|&l| self.branch_jumps(l)),
            // `&&` and `||` too, as `a || return b`.
            NodeKind::If => {
                let children = self.children(node);
                children.len() == 3 && children[1..].iter().any(|&c| self.branch_jumps(c))
            }
            _ => false,
        }
    }

    /// A `let` whose initializer jumps in some branch: the local declared
    /// without a value, then the `if` as a statement that assigns it in
    /// each branch that doesn't jump, as tmpl has no jump as a value.
    fn let_jumping(&mut self, node: FNode, out: &mut Vec<NodeId>) -> Tx<()> {
        let (name, _, init) = core::let_parts(&self.s.library.tree, node);
        let ty = self.decl_ty(node)?;
        let name = self.name_of(name).to_string();
        let local = self.b.declare(DeclKind::Local, &name, self.owner, ty);
        self.b.add_facts(local, Facts::VAR);
        self.decl_spans(local, node);
        self.decls.insert(node, local);
        let declared = self.b.let_stmt(local, None);
        out.push(self.own(declared, node));
        self.assign_jumping(init.unwrap(), local, ty, out)
    }

    /// Assigns a value that may jump ([`Self::value_jumps`]) to `local`
    /// where it doesn't.
    fn assign_jumping(
        &mut self,
        node: FNode,
        local: DeclId,
        ty: TypeId,
        out: &mut Vec<NodeId>,
    ) -> Tx<()> {
        if self.or_else_jumps(node) {
            return self.or_else_jumping(node, Some((local, ty)), out);
        }
        self.assign_branches(node, local, ty, out)
    }

    /// Whether a value may jump, so it's statements that assign a local
    /// where it doesn't ([`Self::jumping_value`]): an `if` (`&&`, `||`,
    /// and `??` among them) or a block with a branch that jumps
    /// ([`Self::branch_jumps`]), or an `orelse` whose fallback does.
    fn value_jumps(&self, node: FNode) -> bool {
        match self.kind(node) {
            NodeKind::If | NodeKind::Block => self.branch_jumps(node),
            NodeKind::Orelse => self.or_else_jumps(node),
            _ => false,
        }
    }

    /// A value that may jump ([`Self::value_jumps`]), as `y ?? return
    /// null` or `f() orelse break`, where a value goes: tmpl has no jump
    /// as a value, so a local of its type, assigned in each branch that
    /// doesn't jump and read after, in a block's value: `{ let t; if (y
    /// == null) { return null; } else { t = y; } t }`. Backends hoist it
    /// as they hoist any block's value, keeping the order of what runs.
    fn jumping_value(&mut self, node: FNode) -> Tx<NodeId> {
        // Where typing has no type for it, as a function's last value, the
        // type of its parts' values, as [`Self::or_else_typed`] finds it.
        let ty = match self.s.typing.expr(node) {
            Some(t) if t != ft::NEVER && self.s.typing.types.is_known(t) => self.ty(t, node)?,
            _ => match self.value_type(self.children(node).to_vec().as_slice()) {
                Some(t) => t,
                None => self.expr_ty(node)?,
            },
        };
        let local = self.b.declare_made(DeclKind::Local, &["t"], self.owner, ty);
        self.b.add_facts(local, Facts::VAR);
        let span = self.span(node);
        self.b.set_decl_spans(local, span, span);
        let declared = self.b.let_stmt(local, None);
        let mut stmts = vec![self.made(declared, node)];
        self.assign_jumping(node, local, ty, &mut stmts)?;
        let read = self.b.local(local);
        let read = self.made(read, node);
        let made = self.b.block_value(ty, &stmts, read);
        Ok(self.made(made, node))
    }

    /// Whether evaluating a node always ends in a jump (a `break`,
    /// `continue`, or `return`), so what follows never runs, as after
    /// `panic()`: a jump; a call, `let`, assignment, or `await` with a
    /// part that always jumps, which runs, as `f(a, return b)`; a block
    /// with a statement that does; or an `if` whose condition or both
    /// branches do. Not an `orelse`, whose fallback runs if a part before
    /// the jump bubbles.
    fn always_jumps(&self, node: FNode) -> bool {
        if let Some(&jumps) = self.always_jumps.borrow().get(&node) {
            return jumps;
        }
        let jumps = self.jumps_now(node);
        self.always_jumps.borrow_mut().insert(node, jumps);
        jumps
    }

    /// [`Self::always_jumps`], found anew for `node`.
    fn jumps_now(&self, node: FNode) -> bool {
        match self.kind(node) {
            NodeKind::Break | NodeKind::Continue | NodeKind::Return => true,
            NodeKind::Block if self.is_postfix_step(node) => false,
            NodeKind::Call
            | NodeKind::Let
            | NodeKind::Assign
            | NodeKind::Await
            | NodeKind::Block => (self.children(node).iter()).any(|&c| self.always_jumps(c)),
            NodeKind::If => match self.children(node) {
                &[cond, then, els] => {
                    self.always_jumps(cond) || (self.always_jumps(then) && self.always_jumps(els))
                }
                _ => false,
            },
            _ => false,
        }
    }

    /// Whether a node always jumps ([`Self::always_jumps`]) for a part
    /// that runs first, not for being a jump, a block, or an `if` whose
    /// branches jump, which translate as statements as they are: a
    /// `return` whose value always jumps, an `if` whose condition does,
    /// or a call, `let`, assignment, or `await` with such a part.
    fn jumps_from_part(&self, node: FNode) -> bool {
        match self.kind(node) {
            NodeKind::Return
            | NodeKind::Call
            | NodeKind::Let
            | NodeKind::Assign
            | NodeKind::Await => (self.children(node).iter()).any(|&c| self.always_jumps(c)),
            NodeKind::If => (self.children(node).first()).is_some_and(|&c| self.always_jumps(c)),
            _ => false,
        }
    }

    /// A node that always jumps ([`Self::always_jumps`]) as statements:
    /// for a part that runs first ([`Self::jumps_from_part`]), what runs
    /// before that part, for what it does, then the part's own
    /// statements; else the node as a statement. Its value is never
    /// used, as it has none.
    fn jump_stmts(&mut self, node: FNode, out: &mut Vec<NodeId>) -> Tx<()> {
        if !self.jumps_from_part(node) {
            return self.stmt(node, out);
        }
        let children = self.children(node).to_vec();
        match self.kind(node) {
            NodeKind::Let => {
                let init = core::let_parts(&self.s.library.tree, node).2;
                self.jump_stmts(init.unwrap(), out)
            }
            NodeKind::If => self.jump_stmts(children[0], out),
            _ => {
                for child in children {
                    if self.always_jumps(child) {
                        return self.jump_stmts(child, out);
                    }
                    self.effects(child, out)?;
                }
                unreachable!("a part that jumps")
            }
        }
    }

    /// What a part does whose value isn't used, as one before a jump:
    /// nothing for a value, a name, or a function, the object's for a
    /// member, as `o.m` called, else the part as a statement.
    fn effects(&mut self, node: FNode, out: &mut Vec<NodeId>) -> Tx<()> {
        match (self.kind(node), self.children(node)) {
            (NodeKind::Value | NodeKind::RightName | NodeKind::Fn, _) => Ok(()),
            (NodeKind::Call, &[dot, object, _]) if self.function_of(dot) == Some(Function::Dot) => {
                self.effects(object, out)
            }
            _ => self.stmt(node, out),
        }
    }

    /// Assigns a value to `local`, branch by branch, jumps as they are.
    fn assign_branches(
        &mut self,
        node: FNode,
        local: DeclId,
        ty: TypeId,
        out: &mut Vec<NodeId>,
    ) -> Tx<()> {
        match self.kind(node) {
            NodeKind::Break | NodeKind::Continue | NodeKind::Return => self.jump_stmts(node, out),
            _ if self.jumps_from_part(node) => self.jump_stmts(node, out),
            NodeKind::Block if !self.children(node).is_empty() => {
                let children = self.children(node).to_vec();
                self.predeclare_functions(&children)?;
                let (&last, rest) = children.split_last().unwrap();
                for &child in rest {
                    self.stmt(child, out)?;
                    if self.always_jumps(child) || self.never_returns(child) {
                        // What follows can't run.
                        return Ok(());
                    }
                }
                self.assign_branches(last, local, ty, out)
            }
            NodeKind::If if self.branch_jumps(node) => {
                let [cond, then_node, els_node] = self.children(node).to_vec()[..] else {
                    return self.not_yet(node, "an if's form");
                };
                let cond = self.condition(cond)?;
                let mut stmts = Vec::new();
                self.assign_branches(then_node, local, ty, &mut stmts)?;
                let then = self.b.block(&stmts);
                let then = self.own(then, then_node);
                let mut stmts = Vec::new();
                self.assign_branches(els_node, local, ty, &mut stmts)?;
                let els = self.b.block(&stmts);
                let els = self.own(els, els_node);
                let made = self.b.if_stmt(cond, then, Some(els));
                out.push(self.own(made, node));
                Ok(())
            }
            _ => {
                let value = self.expr(node)?;
                let value = self.fit(value, ty);
                let made = self.b.assign(local, value);
                out.push(self.own(made, node));
                Ok(())
            }
        }
    }

    /// Whether a node is `break` to `target`, maybe alone in a block.
    fn breaks_to(&self, node: FNode, target: FNode) -> bool {
        match self.kind(node) {
            NodeKind::Break => FNode(self.data(node)) == target,
            NodeKind::Block => match self.children(node) {
                &[only] => self.breaks_to(only, target),
                _ => false,
            },
            _ => false,
        }
    }

    /// Whether a statement is a `while (true)` loop that nothing breaks.
    fn never_ends(&self, node: FNode) -> bool {
        if self.kind(node) != NodeKind::Loop || self.data(node) & (LOOP_EACH | LOOP_NEXT) != 0 {
            return false;
        }
        let [body] = self.children(node)[..] else {
            return false;
        };
        let inner = match self.while_shape(body, node) {
            Some((cond, inner))
                if matches!(self.s.library.value(cond), Some(Value::Bool(true)))
                    || matches!(
                        self.s.scopes.resolved_builtin(cond),
                        Some(Builtin::Value(Constant::True))
                    ) =>
            {
                inner
            }
            Some(_) => return false,
            None => body,
        };
        let mut stack = vec![inner];
        while let Some(n) = stack.pop() {
            if self.kind(n) == NodeKind::Break && self.jump_target(FNode(self.data(n))) == node {
                return false;
            }
            stack.extend_from_slice(self.children(n));
        }
        true
    }

    /// Whether anything under a node continues `target`.
    fn continues_to(&self, node: FNode, target: FNode) -> bool {
        let mut stack = vec![node];
        while let Some(n) = stack.pop() {
            if self.kind(n) == NodeKind::Continue && FNode(self.data(n)) == target {
                return true;
            }
            stack.extend_from_slice(self.children(n));
        }
        false
    }

    /// The loop a jump leaves or continues: its target, or for a labeled
    /// block that ends in a loop, as `outer: for (…) { … }` is, that loop,
    /// since leaving the block is leaving the loop.
    fn jump_target(&self, target: FNode) -> FNode {
        let mut node = target;
        while self.kind(node) == NodeKind::Block {
            match self.children(node).last() {
                Some(&last) => node = last,
                None => return target,
            }
        }
        if self.kind(node) == NodeKind::Loop {
            node
        } else {
            target
        }
    }

    /// The classes declared in functions under a module's top levels, each
    /// with the top level it's under, which Kotlin's backends declare at
    /// the module's level, as here: those that name none of their
    /// functions' locals, parameters, or type parameters.
    pub(super) fn local_classes(&self, top_levels: &[FNode]) -> Vec<(FNode, FNode)> {
        let mut found = Vec::new();
        for &top in top_levels {
            let mut stack: Vec<FNode> = self.children(top).to_vec();
            while let Some(n) = stack.pop() {
                if self.kind(n) == NodeKind::Class && self.names_only_its_own(n) {
                    found.push((n, top));
                }
                stack.extend(self.children(n).iter().rev());
            }
        }
        found
    }

    /// Whether every name under a class that isn't top-level is the
    /// class's or declared in it.
    fn names_only_its_own(&self, class: FNode) -> bool {
        let mut inside = HashSet::new();
        let mut stack = vec![class];
        while let Some(n) = stack.pop() {
            inside.insert(n);
            stack.extend_from_slice(self.children(n));
        }
        inside.iter().all(|&n| {
            let Some(symbol) = self.s.scopes.resolved(n) else {
                return true;
            };
            let s = self.s.scopes.symbol(symbol);
            match s.kind {
                crate::scope::SymbolKind::Builtin(_) | crate::scope::SymbolKind::TopLevel => true,
                // The class's own name, a local of its function's.
                _ if s.decl == Some(class) => true,
                _ => inside.contains(&self.s.scopes.get(s.owner).node),
            }
        })
    }

    /// Whether a block is `++` or `--` after a local's or module `var`'s
    /// name, `{ let t = i; i = t + 1; t }`, as a statement, so `t` is `i`.
    fn is_postfix_step(&self, node: FNode) -> bool {
        let tree = &self.s.library.tree;
        let &[t, assign, value] = self.children(node) else {
            return false;
        };
        if self.kind(t) != NodeKind::Let
            || self.kind(assign) != NodeKind::Assign
            || self.kind(value) != NodeKind::RightName
            || self.declaration(value) != Some(t)
        {
            return false;
        }
        let (_, None, Some(x)) = core::let_parts(tree, t) else {
            return false;
        };
        let [target, step] = self.children(assign)[..] else {
            return false;
        };
        let same = |a: FNode, b: FNode| {
            self.kind(a) == NodeKind::RightName
                && self.kind(b) == NodeKind::RightName
                && self.declaration(a).is_some()
                && self.declaration(a) == self.declaration(b)
        };
        // `t + 1` or `t - 1`: `t` and a constant.
        let reads_t = (self.kind(step) == NodeKind::Call)
            && self.children(step).len() == 3
            && self.children(step)[1] != t
            && self.declaration(self.children(step)[1]) == Some(t)
            && matches!(
                self.s.library.value(self.children(step)[2]),
                Some(Value::Int32(_) | Value::Int64(_))
            );
        same(x, target) && reads_t
    }

    /// Whether a top-level `let` is a type alias, as `let ChangeType =
    /// String`, which typing reads where the alias is used and which has
    /// no value to translate: as typing's `alias_type`, an immutable `let`
    /// without a written type whose initializer names a type, or gives
    /// one arguments, or is a nullable or function type. `depth` aliases
    /// in, against cycles.
    fn is_type_alias(&self, decl: FNode, depth: u32) -> bool {
        let (name, ty, init) = core::let_parts(&self.s.library.tree, decl);
        let (None, Some(init)) = (ty, init) else {
            return false;
        };
        let top = (self.s.scopes.resolved(name)).is_some_and(|s| {
            let s = self.s.scopes.symbol(s);
            s.kind == crate::scope::SymbolKind::TopLevel && !s.mutable
        });
        if !top || depth > 8 {
            return false;
        }
        match self.kind(init) {
            NodeKind::RightName => {
                let Some(symbol) = self.s.scopes.resolved(init) else {
                    return false;
                };
                let s = self.s.scopes.symbol(symbol);
                match (s.kind, s.decl) {
                    (crate::scope::SymbolKind::Builtin(Builtin::Type(_)), _) => true,
                    (_, Some(d)) if self.kind(d) == NodeKind::Class => true,
                    (_, Some(d)) if self.kind(d) == NodeKind::Let => {
                        self.is_type_alias(d, depth + 1)
                    }
                    _ => false,
                }
            }
            NodeKind::Call => matches!(
                self.s.scopes.resolved_builtin(self.children(init)[0]),
                Some(Builtin::Function(
                    Function::Angle | Function::Nullable | Function::FnType
                ))
            ),
            _ => false,
        }
    }

    /// Forgets the tmpl locals of the `let`s under a node, as a `for`
    /// loop's step's, so translating it again, as each copy of the step
    /// is, declares its own.
    fn forget_locals(&mut self, node: FNode) {
        let mut stack = vec![node];
        while let Some(n) = stack.pop() {
            if self.kind(n) == NodeKind::Let {
                self.decls.remove(&n);
            }
            stack.extend_from_slice(self.children(n));
        }
    }

    /// Whether a `for` loop's step can be translated again before each
    /// `continue`: it has no loops, functions, or classes. Its locals, as
    /// `i++`'s temporary or `??`'s subject, are declared again in each
    /// copy ([`Self::forget_locals`]).
    fn copyable_step(&self, node: FNode) -> bool {
        let mut stack = vec![node];
        while let Some(n) = stack.pop() {
            if matches!(
                self.kind(n),
                NodeKind::Loop | NodeKind::Fn | NodeKind::Class
            ) {
                return false;
            }
            stack.extend_from_slice(self.children(n));
        }
        true
    }

    /// `if (cond) { body } else { break }`, the shape `while` takes: its
    /// condition and body.
    fn while_shape(&self, node: FNode, target: FNode) -> Option<(FNode, FNode)> {
        if self.kind(node) != NodeKind::If {
            return None;
        }
        match *self.children(node) {
            [cond, body, els] if self.breaks_to(els, target) => Some((cond, body)),
            _ => None,
        }
    }

    /// A loop: `while`, `do … while` without `continue`, or `for`, its
    /// step before each `continue` too; any other as `while (true)`.
    fn loop_stmt(&mut self, node: FNode) -> Tx<NodeId> {
        let data = self.data(node);
        if data & LOOP_EACH != 0 {
            // `Loop({ xs.forEach(fn (x) { body }); break; })`.
            let block = self.children(node).first().copied();
            let call = block.and_then(|b| self.children(b).first().copied());
            let parts = call.and_then(|c| self.for_each_parts(c).map(|p| (c, p)));
            let Some((call, (object, f))) = parts else {
                return self.not_yet(node, "a for … of loop");
            };
            return self.for_each(call, object, f, Some(node));
        }
        let label = self
            .b
            .declare_made(DeclKind::Label, &["loop"], self.owner, TypeId::NONE);
        let span = self.span(node);
        self.b.set_decl_spans(label, span, span);
        self.loops.insert(node, label);
        let children = self.children(node).to_vec();
        if data & LOOP_NEXT != 0 {
            let [body, next] = children[..] else {
                return self.not_yet(node, "a loop's form");
            };
            let continues = self.continues_to(body, node);
            // A `continue` in the step runs the step again, as the
            // interpreter has it, which has no shape here yet.
            if self.continues_to(next, node) {
                return self.not_yet(node, "continue in a for loop's step");
            }
            // `do { body } while (cond)`: `next` checks.
            if let Some((cond, then)) = self.while_shape(next, node) {
                if self.kind(then) == NodeKind::Block && self.children(then).is_empty() {
                    if continues {
                        return self.not_yet(node, "continue in a do … while loop");
                    }
                    let body = self.block(body)?;
                    let cond = self.condition(cond)?;
                    return Ok(self.b.do_while(label, body, cond));
                }
            }
            // `for (…; cond; next) { body }`, `next` copied before each
            // `continue` ([`temper_tmpl::exits`]), which a step that loops
            // can't be.
            if continues {
                if !self.copyable_step(next) {
                    return self.not_yet(node, "continue in a for loop whose step loops");
                }
                self.steps.insert(node, next);
            }
            let (cond, body) = match self.while_shape(body, node) {
                Some((cond, body)) => (Some(cond), body),
                None => (None, body),
            };
            let cond = match cond {
                Some(cond) => self.condition(cond)?,
                None => {
                    let yes = self.b.bool_lit(true);
                    self.made(yes, node)
                }
            };
            let mut stmts = Vec::new();
            self.stmts(body, &mut stmts)?;
            let ends = !(self.kind(body) == NodeKind::Block
                && self.children(body).iter().any(|&c| self.never_ends(c)));
            if ends {
                let mut step = Vec::new();
                self.forget_locals(next);
                self.stmts(next, &mut step)?;
                stmts.extend(step);
            }
            let block = self.b.block(&stmts);
            let block = self.own(block, body);
            return Ok(self.b.while_loop(label, cond, block));
        }
        let [body] = children[..] else {
            return self.not_yet(node, "a loop's form");
        };
        let (cond, body) = match self.while_shape(body, node) {
            Some((cond, body)) => (self.condition(cond)?, body),
            None => {
                let yes = self.b.bool_lit(true);
                (self.made(yes, node), body)
            }
        };
        let body = self.block(body)?;
        Ok(self.b.while_loop(label, cond, body))
    }

    /// Whether a value never completes: it returns, jumps, or fails on
    /// every path.
    /// A `panic()` statement where a missing `else` is never reached
    /// (`Typing::is_unreachable`), as Kotlin's.
    fn unreachable(&mut self, node: FNode, ty: TypeId) -> NodeId {
        let panic = self.b.panic(ty);
        let panic = self.made(panic, node);
        let stmt = self.b.expr_statement(panic);
        self.made(stmt, node)
    }

    /// Whether a node is a call that never returns: `panic()`, or a call
    /// whose result is `Never<T>` ([`crate::typing::never_returns`]).
    fn never_returns(&self, node: FNode) -> bool {
        crate::typing::never_returns(&self.s.library.tree, &self.s.scopes, &self.s.typing, node)
    }

    fn never_completes(&self, node: FNode) -> bool {
        match self.kind(node) {
            NodeKind::Fail => true,
            _ if self.always_jumps(node) => true,
            NodeKind::Block if self.s.typing.is_unreachable(node) => true,
            NodeKind::Block => (self.children(node).iter()).any(|&c| self.never_completes(c)),
            NodeKind::If if self.and_or(node).is_none() => {
                let children = self.children(node);
                children.len() == 3 && children[1..].iter().all(|&c| self.never_completes(c))
            }
            NodeKind::Call => {
                let callee = self.children(node).first();
                callee.and_then(|&c| self.s.scopes.resolved_builtin(c))
                    == Some(Builtin::Function(Function::Panic))
            }
            _ => false,
        }
    }

    /// A function's body, whose last value it returns: `return`s pushed
    /// into the branches of a trailing `if`.
    fn tail_stmts(&mut self, node: FNode, out: &mut Vec<NodeId>) -> Tx<()> {
        if self.kind(node) != NodeKind::Block {
            return self.tail(node, out);
        }
        if self.s.typing.is_unreachable(node) {
            out.push(self.unreachable(node, TypeId::VOID));
            return Ok(());
        }
        let children = self.children(node).to_vec();
        self.predeclare_functions(&children)?;
        if let Some((&last, rest)) = children.split_last() {
            for &child in rest {
                self.stmt(child, out)?;
                // Nothing after an error, a call that never returns, or
                // a jump, runs, and Java and Rust reject it.
                if self.fails(child) || self.never_returns(child) || self.always_jumps(child) {
                    return Ok(());
                }
            }
            // `{ return x; }` (or `{ bubble(); }`) ends with a `void`
            // that's never reached.
            let after_return = rest.last().is_some_and(|&r| self.never_completes(r));
            if after_return && matches!(self.s.library.value(last), Some(Value::Void)) {
                return Ok(());
            }
            self.tail(last, out)?;
        }
        Ok(())
    }

    fn tail(&mut self, node: FNode, out: &mut Vec<NodeId>) -> Tx<()> {
        match self.kind(node) {
            NodeKind::Block => {
                let mut stmts = Vec::new();
                self.tail_stmts(node, &mut stmts)?;
                let block = self.b.block(&stmts);
                out.push(self.own(block, node));
                Ok(())
            }
            NodeKind::If if self.and_or(node).is_none() => {
                let [cond, then, els] = self.children(node).to_vec()[..] else {
                    return self.not_yet(node, "an if's form");
                };
                let cond = self.condition(cond)?;
                let mut branches = Vec::new();
                for branch in [then, els] {
                    let mut stmts = Vec::new();
                    self.tail_stmts(branch, &mut stmts)?;
                    let block = self.b.block(&stmts);
                    branches.push(self.own(block, branch));
                }
                let stmt = self.b.if_stmt(cond, branches[0], Some(branches[1]));
                out.push(self.own(stmt, node));
                Ok(())
            }
            // `do { …; if (…) { return a } else { return b } } orelse
            // panic()`, as std's `parseJsonObject` ends: a statement, as
            // neither part gives a value to return.
            NodeKind::Orelse if self.children(node).iter().all(|&c| self.never_completes(c)) => {
                self.stmt(node, out)
            }
            NodeKind::Loop
            | NodeKind::Let
            | NodeKind::Assign
            | NodeKind::Return
            | NodeKind::Break
            | NodeKind::Continue => self.stmt(node, out),
            // `bubble()` or `panic()`: nothing to return.
            NodeKind::Fail | NodeKind::Call if self.never_completes(node) => self.stmt(node, out),
            // `f(a, return b)`: nothing to return either.
            _ if self.jumps_from_part(node) => self.jump_stmts(node, out),
            _ => {
                let value = self.expr(node)?;
                let value = self.fit(value, self.result);
                let stmt = self.b.return_stmt(Some(value));
                out.push(self.made(stmt, node));
                Ok(())
            }
        }
    }

    // Expressions.

    /// A condition, a `Boolean`, which an error there is typed as.
    fn condition(&mut self, node: FNode) -> Tx<NodeId> {
        let cond = self.expr(node)?;
        Ok(self.fit(cond, TypeId::BOOLEAN))
    }

    fn expr(&mut self, node: FNode) -> Tx<NodeId> {
        if let Some(message) = self.error_at(node) {
            let made = self.b.error(TypeId::NONE, &message);
            return Ok(self.own(made, node));
        }
        // A type-only error: an `Error` with the value as written, of its
        // own type, which runs on where the target runs ill-typed code.
        let span = self.s.library.tree.span(node);
        let span = (span.start, span.end);
        if self.as_written != Some(span) {
            if let Some(message) = self.type_errors.get(&span).cloned() {
                let outer = self.as_written.replace(span);
                let value = self.expr(node);
                self.as_written = outer;
                let made = self.b.type_error(TypeId::NONE, &message, value?);
                return Ok(self.own(made, node));
            }
        }
        if self.value_jumps(node) {
            return self.jumping_value(node);
        }
        let lib = &self.s.library;
        match self.kind(node) {
            NodeKind::Value => {
                let made = match lib.value(node) {
                    Some(Value::Int32(v)) => self.b.int32(*v),
                    Some(Value::Int64(v)) => self.b.int64(*v),
                    Some(Value::Float64(v)) => self.b.float64(*v),
                    Some(Value::String(v)) => self.b.string(v),
                    Some(Value::Bool(v)) => self.b.bool_lit(*v),
                    Some(Value::Void) => self.b.void(),
                    Some(Value::Null) => self.b.null(TypeId::NONE),
                    _ => return self.not_yet(node, "a value of another kind"),
                };
                Ok(self.own(made, node))
            }
            NodeKind::RightName => {
                if let Some(Builtin::Value(c)) = self.s.scopes.resolved_builtin(node) {
                    let made = match c {
                        Constant::True => self.b.bool_lit(true),
                        Constant::False => self.b.bool_lit(false),
                        Constant::Void => self.b.void(),
                        Constant::NaN => self.b.float64(f64::NAN),
                        Constant::Infinity => self.b.float64(f64::INFINITY),
                        Constant::TestServerPort(port) => self.b.int32(port),
                        Constant::Null => self.b.null(TypeId::NONE),
                        Constant::Console => return self.not_yet(node, "console as a value"),
                    };
                    return Ok(self.own(made, node));
                }
                if let Some(Builtin::Type(i)) = self.s.scopes.resolved_builtin(node) {
                    return self.type_value(node, i);
                }
                if self.is_this(node) {
                    return self.this(node);
                }
                let member = self.s.scopes.resolved(node).and_then(|s| {
                    let s = self.s.scopes.symbol(s);
                    (s.kind == crate::scope::SymbolKind::Member)
                        .then_some(s.decl)
                        .flatten()
                });
                // A header's field while its constructor's defaults are
                // made reads its parameter (`constructor`).
                if let Some(m) = member.filter(|m| !self.decls.contains_key(m)) {
                    return self.read_member(m, None, node);
                }
                let decl = match self.declaration(node) {
                    Some(d) if self.kind(d) == NodeKind::Class => {
                        return self.not_yet(node, "a class or interface as a type value");
                    }
                    Some(d) if self.decls.contains_key(&d) => Some(self.decls[&d]),
                    Some(d) => self.top_decl(d, node)?,
                    None => None,
                };
                let Some(decl) = decl else {
                    return self.not_yet(node, "a name not declared here");
                };
                let made = match self.b.program().decls.get(decl).kind {
                    DeclKind::Local | DeclKind::Param => {
                        let read = self.b.local(decl);
                        let read = self.own(read, node);
                        return self.narrowed(read, node);
                    }
                    DeclKind::ModuleLet => self.b.get_module_let(decl),
                    DeclKind::Function => return self.function_ref(decl, node),
                    _ => return self.not_yet(node, "a function as a value"),
                };
                Ok(self.own(made, node))
            }
            NodeKind::Call => self.call(node),
            NodeKind::If => self.if_value(node),
            NodeKind::Block => {
                let ty = self.expr_ty(node)?;
                self.block_value(node, ty)
            }
            NodeKind::Fn => self.literal(node),
            NodeKind::Await => self.await_expr(node),
            NodeKind::Orelse => self.or_else(node),
            NodeKind::Fail if !self.children(node).is_empty() => self.throw(node),
            NodeKind::Fail => {
                let made = self.b.bubble(TypeId::NONE);
                Ok(self.own(made, node))
            }
            // `x = v` as a value, as `--i` in `for (…; --i >= 0;)`: the
            // assignment, then `x`.
            NodeKind::Assign => {
                let target = self.children(node)[0];
                let local = match self.kind(target) {
                    NodeKind::RightName => {
                        self.declaration(target).and_then(|d| self.decls.get(&d))
                    }
                    _ => None,
                };
                let Some(&local) = local.filter(|&&d| {
                    matches!(
                        self.b.program().decls.get(d).kind,
                        DeclKind::Local | DeclKind::Param
                    )
                }) else {
                    return self.member_assignment_value(node);
                };
                let mut stmts = Vec::new();
                self.stmt(node, &mut stmts)?;
                let read = self.b.local(local);
                let read = self.made(read, node);
                let ty = self.b.program().decls.get(local).ty;
                let made = self.b.block_value(ty, &stmts, read);
                Ok(self.made(made, node))
            }
            kind => self.not_yet(node, format!("{kind:?}")),
        }
    }

    /// For an assignment to a member, `o.p = v` or a bare `p = v` in its
    /// class, the object, if any, and the member.
    fn member_target(&self, node: FNode, target: FNode) -> Tx<Option<(Option<FNode>, FNode)>> {
        if self.kind(target) != NodeKind::RightName {
            let Some((object, m)) = self.assigned_member(target) else {
                return self.not_yet(node, "an assignment to a property");
            };
            return Ok(Some((self.static_object(object), m)));
        }
        let member = self.s.scopes.resolved(target).and_then(|s| {
            let s = self.s.scopes.symbol(s);
            (s.kind == crate::scope::SymbolKind::Member)
                .then_some(s.decl)
                .flatten()
        });
        Ok(member.map(|m| (None, m)))
    }

    /// `o.p = v` as a value, as in `c.p = c.p = v`: `v` held in a local,
    /// then the assignment, then the local, as Kotlin Temper writes it.
    /// Only where reading `o` after `v` is the same as before it: `o` a
    /// name that's never assigned again, or `this`.
    fn member_assignment_value(&mut self, node: FNode) -> Tx<NodeId> {
        let &[target, value] = self.children(node) else {
            return self.not_yet(node, "an assignment as a value");
        };
        let Some((object, m)) = self.member_target(node, target)? else {
            return self.not_yet(node, "an assignment as a value");
        };
        let steady = object.is_none_or(|o| {
            self.kind(o) == NodeKind::RightName
                && (self.s.scopes.resolved(o)).is_some_and(|b| !self.s.scopes.symbol(b).mutable)
        });
        if !steady {
            return self.not_yet(node, "an assignment to a property as a value");
        }
        let held = self.expr(value)?;
        let held = self.type_error_around(node, held);
        let ty = self.b.program().nodes.ty(held);
        let local = self.b.declare_made(DeclKind::Local, &["t"], self.owner, ty);
        let span = self.span(node);
        self.b.set_decl_spans(local, span, span);
        let init = self.b.let_stmt(local, Some(held));
        let assign = self.assign_member(m, object, Assigned::Held(local), node)?;
        let stmts = [self.made(init, node), self.own(assign, node)];
        let read = self.b.local(local);
        let read = self.made(read, node);
        let made = self.b.block_value(ty, &stmts, read);
        Ok(self.made(made, node))
    }

    /// `a && b`, as `if (a) { b } else { false }`, or `a || b`, as `if (a)
    /// { true } else { b }`: the operands and whether it's `&&`.
    fn and_or(&self, node: FNode) -> Option<(FNode, FNode, bool)> {
        let &[cond, then, els] = self.children(node) else {
            return None;
        };
        if self.s.typing.expr(node) != Some(ft::BOOLEAN) {
            return None;
        }
        match (self.bool_const(then), self.bool_const(els)) {
            (_, Some(false)) => Some((cond, then, true)),
            (Some(true), _) => Some((cond, els, false)),
            _ => None,
        }
    }

    /// `true` or `false`, as a value or a name.
    fn bool_const(&self, node: FNode) -> Option<bool> {
        match self.s.library.value(node) {
            Some(Value::Bool(b)) => return Some(*b),
            Some(_) => return None,
            None => {}
        }
        match self.s.scopes.resolved_builtin(node) {
            Some(Builtin::Value(Constant::True)) => Some(true),
            Some(Builtin::Value(Constant::False)) => Some(false),
            _ => None,
        }
    }

    fn if_value(&mut self, node: FNode) -> Tx<NodeId> {
        self.if_value_in(node, TypeId::NONE)
    }

    /// [`Self::if_value`] for an `if` that's the value of a branch of
    /// another, whose type `outer` is, if it's given: an `if` typing types
    /// `Null` or `Never`, as one of a `when`'s last branches, `2 -> null;
    /// else -> panic();`, is of the type of the `if` it's in, as Kotlin
    /// Temper's solver types it.
    fn if_value_in(&mut self, node: FNode, outer: TypeId) -> Tx<NodeId> {
        if let Some((left, right, and)) = self.and_or(node) {
            let (left, right) = (self.expr(left)?, self.expr(right)?);
            let made = if and {
                self.b.and(left, right)
            } else {
                self.b.or(left, right)
            };
            return Ok(self.own(made, node));
        }
        let [cond, then, els] = self.children(node).to_vec()[..] else {
            return self.not_yet(node, "an if's form");
        };
        // Where typing has no type for it, as the last value of an
        // `orelse`'s fallback that's a function's last value, `x ?? "a"`
        // in `do { … } orelse (x ?? "a")`, its branches' type, as
        // [`Self::or_else_typed`] finds it.
        let ty = match self.s.typing.expr(node) {
            Some(ft::NULL | ft::NEVER) if !outer.is_none() && outer != TypeId::VOID => outer,
            None => match self.value_type(&[then, els]) {
                Some(t) => t,
                None => self.expr_ty(node)?,
            },
            Some(_) => self.expr_ty(node)?,
        };
        let cond = self.condition(cond)?;
        let then = self.block_value(then, ty)?;
        let then = self.fit(then, ty);
        let els = self.block_value(els, ty)?;
        let els = self.fit(els, ty);
        let made = self.b.if_value(ty, cond, then, els);
        Ok(self.own(made, node))
    }

    /// A block's value: its last part's, after its statements.
    fn block_value(&mut self, node: FNode, ty: TypeId) -> Tx<NodeId> {
        if self.kind(node) == NodeKind::Assign {
            // An assignment's value, as `(x = 1) orelse …`'s: the
            // assignment, then `void`.
            let mut stmts = Vec::new();
            self.stmt(node, &mut stmts)?;
            let value = self.b.void();
            let value = self.made(value, node);
            let made = self.b.block_value(TypeId::VOID, &stmts, value);
            return Ok(self.own(made, node));
        }
        match self.kind(node) {
            NodeKind::If => return self.if_value_in(node, ty),
            NodeKind::Block => {}
            _ => return self.expr(node),
        }
        if self.s.typing.is_unreachable(node) {
            let made = self.b.panic(ty);
            return Ok(self.made(made, node));
        }
        let children = self.children(node).to_vec();
        let Some((&last, rest)) = children.split_last() else {
            return self.not_yet(node, "an empty block's value");
        };
        if ty == TypeId::VOID && self.s.typing.expr(last).is_none() {
            // A `Void` block whose last part is a statement, as
            // `do { …; if (…) { … } } orelse panic()`: its statements,
            // then `void`.
            let mut stmts = Vec::new();
            self.stmts(node, &mut stmts)?;
            let value = self.b.void();
            let value = self.made(value, node);
            let made = self.b.block_value(TypeId::VOID, &stmts, value);
            return Ok(self.own(made, node));
        }
        if rest.is_empty() {
            // `do { x = true }`'s value is its assignment's.
            return self.block_value(last, ty);
        }
        let mut stmts = Vec::new();
        self.predeclare_functions(rest)?;
        // An error is the block's value, as nothing after it runs.
        let mut last = last;
        let mut stored = None;
        for &child in rest {
            // So is an error, or a `throw`, as nothing after it runs.
            if self.error_at(child).is_some() || self.is_throw(child) {
                last = child;
                break;
            }
            // A `let` of an error fails there, as the block's value.
            stored = self.stored_error(child);
            if stored.is_some() {
                break;
            }
            self.stmt(child, &mut stmts)?;
        }
        let value = match (stored, self.kind(last)) {
            (Some(message), _) => {
                let t = if ty.is_none() {
                    self.expr_ty(last)?
                } else {
                    ty
                };
                let error = self.b.error(t, &message);
                self.made(error, last)
            }
            (None, NodeKind::If) => self.if_value_in(last, ty)?,
            (None, _) => self.expr(last)?,
        };
        // Where its type isn't given, its value's.
        let ty = if ty.is_none() {
            self.b.program().nodes.ty(value)
        } else {
            ty
        };
        let value = self.fit(value, ty);
        // A block whose statements all went (local classes, declared at
        // the module's level), as `doPure`'s `{ class C …; new C() }`, is
        // its value.
        if stmts.is_empty() {
            return Ok(value);
        }
        let made = self.b.block_value(ty, &stmts, value);
        Ok(self.own(made, node))
    }

    /// Whether a node is a `throw` of a declared failure.
    fn is_throw(&self, node: FNode) -> bool {
        self.kind(node) == NodeKind::Fail && !self.children(node).is_empty()
    }

    /// `doPure { … }`: its block's body, in place, as Kotlin's `DoPureFn`
    /// erases the call when generating code; classes declared in it are
    /// declared at the module's level (`local_classes`).
    fn do_pure(&mut self, node: FNode, args: &[FNode]) -> Tx<NodeId> {
        let &[f] = args else {
            return self.not_yet(node, "doPure's arguments");
        };
        if self.kind(f) != NodeKind::Fn {
            return self.not_yet(node, "doPure of a function value");
        }
        let (params, _, body) = core::fn_parts(&self.s.library.tree, f);
        let Some(body) = body.filter(|_| params.is_empty()) else {
            return self.not_yet(node, "doPure's block's form");
        };
        if self.returns_in(body) {
            return self.not_yet(node, "a return in doPure's block");
        }
        let ty = self.expr_ty(node)?;
        let value = self.block_value(body, ty)?;
        Ok(self.fit(value, ty))
    }

    /// Whether a `return` under `node` leaves its function, not one
    /// declared in it.
    fn returns_in(&self, node: FNode) -> bool {
        match self.kind(node) {
            NodeKind::Return => true,
            NodeKind::Fn | NodeKind::Class => false,
            _ => self.children(node).iter().any(|&c| self.returns_in(c)),
        }
    }

    /// A call's arguments, if they're all plain values, without its
    /// explicit type arguments (`\typeArg T`, as in `f<C>(a, b)`), which
    /// typing has already read ([`Typing::type_args`](crate::typing::Typing::type_args)).
    fn plain_args(&self, call: FNode, args: &[FNode]) -> Tx<Vec<FNode>> {
        let mut values = Vec::new();
        let mut type_arg = false;
        for &arg in args {
            if std::mem::take(&mut type_arg) {
                continue;
            }
            if let Some(name) = self.s.library.symbol(arg) {
                if self.s.library.names.get(name) == "typeArg" {
                    type_arg = true;
                    continue;
                }
                return self.not_yet(call, "a named argument");
            }
            values.push(arg);
        }
        Ok(values)
    }

    fn call(&mut self, node: FNode) -> Tx<NodeId> {
        let mut children = self.children(node).to_vec();
        // A trailing block goes where typing placed it: before arguments
        // for parameters after it, which are optional. `at` counts values
        // only, not type arguments (`typeArg` and a type each).
        if let Some(&at) = self.s.typing.trailing_blocks.get(&node) {
            let mut slots = (1..children.len()).filter(|&i| {
                let is_type_arg = |j: usize| {
                    let name = self.s.library.symbol(children[j]);
                    name.is_some_and(|n| self.s.library.names.get(n) == "typeArg")
                };
                !is_type_arg(i) && !is_type_arg(i - 1)
            });
            let Some(slot) = slots.nth(at) else {
                return self.not_yet(node, "a trailing block after parameters left out");
            };
            let block = children.pop().unwrap();
            children.insert(slot, block);
        }
        let (callee, args) = (children[0], &children[1..]);
        let f = self.function_of(callee);
        if f == Some(Function::Caught) {
            return self.caught(node);
        }
        if f == Some(Function::Is) {
            return self.is_check(node, args);
        }
        if f == Some(Function::As) {
            return self.as_cast(node, args);
        }
        match self.s.typing.resolved(node) {
            Some(&Resolved::Decl(d)) if self.is_member(d) || self.kind(d) == NodeKind::Class => {
                let d = self.chase(d);
                match f {
                    Some(Function::New) => {
                        let class = if self.kind(d) == NodeKind::Class {
                            Some(d)
                        } else {
                            self.member_class(d)
                        };
                        match class {
                            Some(class) => self.new_object(class, &args[1..], node),
                            None => self.not_yet(node, "a constructor"),
                        }
                    }
                    Some(Function::Dot) => {
                        let object = self.children(node)[1];
                        let object = self.static_object(object);
                        self.read_member(d, object, node)
                    }
                    Some(
                        f @ (Function::Eq
                        | Function::Ne
                        | Function::Lt
                        | Function::Le
                        | Function::Gt
                        | Function::Ge
                        | Function::Cmp),
                    ) => self.operator_call(node, f, d, args),
                    // `object[i, j]`, its class's `get`.
                    Some(Function::Index) if !args.is_empty() => {
                        let args = self.plain_args(node, args)?;
                        self.call_member(d, Some(args[0]), &args[1..], node)
                    }
                    Some(_) => self.not_yet(node, "a call of a member or constructor"),
                    None => {
                        let args = self.plain_args(node, args)?;
                        match self.dot_object(callee) {
                            Some(object) => {
                                let object = self.static_object(object);
                                self.call_member(d, object, &args, node)
                            }
                            None if self.kind(callee) == NodeKind::RightName => {
                                self.call_member(d, None, &args, node)
                            }
                            None => self.not_yet(node, "a call of a member or constructor"),
                        }
                    }
                }
            }
            None if self.s.typing.identity_eq(node).is_some() => {
                self.identity(node, f.unwrap_or(Function::Eq), args)
            }
            // In a library with errors, one that left the callee's type
            // unknown, as `x.toString()` for `x: Undeclared`: the call
            // fails where it runs, as the error's code does.
            None if self.has_errors() => {
                let made = self.b.error(TypeId::NONE, "a call of unknown type");
                Ok(self.own(made, node))
            }
            None => self.not_yet(node, "a call typing didn't resolve"),
            Some(Resolved::Builtin(key)) => {
                let key = key.clone();
                self.builtin_call(node, f, &key, args)
            }
            Some(&Resolved::Decl(d)) if self.is_function(d) => self.call_function(node, d, args),
            Some(&Resolved::Decl(d))
                if self.decls.get(&d).is_some_and(|&l| {
                    matches!(
                        self.b.program().decls.get(l).kind,
                        DeclKind::Local | DeclKind::Param
                    )
                }) =>
            {
                // A local function, or a local holding a function value.
                self.call_value(node, callee, args)
            }
            Some(Resolved::Decl(_)) => self.not_yet(node, "a call of a member or constructor"),
            Some(Resolved::NullCheck) => self.null_check(node, f, args),
            // A bodiless `@connected` function, which typing calls as a
            // value of its type, is called as any function.
            Some(Resolved::Value)
                if self.kind(callee) == NodeKind::RightName
                    && (self.declaration(callee))
                        .is_some_and(|d| self.bodiless.contains(&self.chase(d))) =>
            {
                let d = self.declaration(callee).unwrap();
                self.call_function(node, d, args)
            }
            Some(Resolved::Value) => self.call_value(node, callee, args),
        }
    }

    /// Whether anything under `node` names the declaration `decl`.
    fn refers_to(&self, node: FNode, decl: FNode) -> bool {
        if self.kind(node) == NodeKind::RightName && self.declaration(node) == Some(decl) {
            return true;
        }
        self.children(node).iter().any(|&c| self.refers_to(c, decl))
    }

    /// A call of a function of the library's, or of another module or
    /// library, by its `Let`.
    fn call_function(&mut self, node: FNode, d: FNode, args: &[FNode]) -> Tx<NodeId> {
        let decl = self.top_decl(d, node)?.unwrap();
        let args = self.plain_args(node, args)?;
        let type_args = self.call_type_args(node, decl)?;
        let rest = self.omit_rest_of(decl, None, &type_args, args.len(), node)?;
        let mut values = Vec::new();
        for arg in args {
            values.push(self.expr(arg)?);
        }
        let mut values = self.fit_args_with(decl, None, &type_args, values);
        values.extend(rest);
        let made = self.b.call_generic(decl, &type_args, &values);
        Ok(self.own(made, node))
    }

    /// Whether a `Let` declares a function of the library's, or of another
    /// module or library, following imports.
    fn is_function(&self, decl: FNode) -> bool {
        let decl = self.chase(decl);
        if self.functions.contains(&decl) || self.bodiless.contains(&decl) {
            return true;
        }
        let top = self
            .let_symbol(decl)
            .is_some_and(|s| self.module_of(s).is_some());
        let init = core::let_parts(&self.s.library.tree, decl).2;
        top && self.kind(decl) == NodeKind::Let
            && init.is_some_and(|i| self.kind(i) == NodeKind::Fn)
    }

    /// `object` in a callee `object.name`, if it's one.
    fn dot_object(&self, callee: FNode) -> Option<FNode> {
        if self.kind(callee) != NodeKind::Call {
            return None;
        }
        match *self.children(callee) {
            [dot, object, _] if self.function_of(dot) == Some(Function::Dot) => Some(object),
            _ => None,
        }
    }

    fn builtin_call(
        &mut self,
        node: FNode,
        f: Option<Function>,
        key: &str,
        args: &[FNode],
    ) -> Tx<NodeId> {
        // `object.name`'s parts aren't arguments.
        let args = match f {
            Some(Function::Dot) => Vec::new(),
            _ => self.plain_args(node, args)?,
        };
        if key == "String.cat" {
            return self.cat(node, &args);
        }
        if key == "doPure" {
            return self.do_pure(node, &args);
        }
        if key == "async" {
            return self.async_call(node, &args);
        }
        if key == "char" {
            return self.char_tag(node, &args);
        }
        if key == "raw" {
            return self.raw_tag(node, &args);
        }
        if key == "empty" && args.is_empty() {
            let Some(empty) = self.core_decl("empty") else {
                return self.not_yet(node, "core's empty");
            };
            let made = self.b.call(empty, &[]);
            return Ok(self.own(made, node));
        }
        // `List.of(…)` is a list literal, as Kotlin's unifies them.
        if key == "list" || key == "List.of" {
            let ty = self.expr_ty(node)?;
            let element = self.b.program().types.args(ty).first().copied();
            let Some(element) = element else {
                return self.not_yet(node, "a list's type");
            };
            let mut items = Vec::new();
            for &arg in &args {
                let item = self.expr(arg)?;
                items.push(self.fit(item, element));
            }
            let made = self.b.list_lit(ty, &items);
            return Ok(self.own(made, node));
        }
        if f == Some(Function::New) {
            // `new ListBuilder<T>()` and the like: a constructor of core's.
            let Some(ctor) = self.core_decl(key) else {
                return self.not_yet(node, format!("core's {key}"));
            };
            let type_args = self.call_type_args(node, ctor)?;
            let values = self.args_for_with(node, ctor, None, &type_args, &args[1..])?;
            let made = self.b.new_generic(ctor, &type_args, &values);
            return Ok(self.own(made, node));
        }
        if f == Some(Function::Index) {
            // `object[i]`, its type's `get`.
            let Some((&object, rest)) = args.split_first() else {
                return self.not_yet(node, "an index's form");
            };
            return self.core_method(node, key, object, rest);
        }
        if key == "panic" && args.is_empty() {
            let made = self.b.panic(TypeId::NONE);
            return Ok(self.own(made, node));
        }
        if key == "console.log" {
            let [arg] = args[..] else {
                return self.not_yet(node, "console.log's arguments");
            };
            let Some(log) = self.core_decl("Console.log") else {
                return self.not_yet(node, "console");
            };
            let callee = self.children(node)[0];
            let at = self.children(callee).get(1).copied().unwrap_or(callee);
            let receiver = self.console(node, at)?;
            let arg = self.expr(arg)?;
            let made = self.b.call_method(log, receiver, &[arg]);
            return Ok(self.own(made, node));
        }
        // An operator by core's `==` or `<=>` method, as
        // `StringIndexOption.compareTo` for `<` on string indices.
        if let Some(
            f @ (Function::Eq
            | Function::Ne
            | Function::Lt
            | Function::Le
            | Function::Gt
            | Function::Ge
            | Function::Cmp),
        ) = f
        {
            let method = (Op::named(key).is_none())
                .then(|| self.core_decl(key))
                .flatten()
                .filter(|&d| self.b.program().decls.get(d).kind == DeclKind::Method);
            if let Some(method) = method {
                let [left, right] = args[..] else {
                    return self.not_yet(node, "an operator's arguments");
                };
                let receiver = self.expr(left)?;
                // As its interface, `StringIndexOption`, which isn't generic.
                let owner = self.b.program().decls.get(method).owner;
                let owner = self.b.nominal(owner);
                let receiver = self.fit(receiver, owner);
                let values = self.args_for_with(node, method, Some(receiver), &[], &[right])?;
                let call = self.b.call_method(method, receiver, &values);
                let call = self.own(call, node);
                return self.compared(node, f, call);
            }
        }
        // An operator.
        if f.is_some_and(|f| f != Function::Dot) {
            let Some(op) = Op::named(key) else {
                return self.not_yet(node, format!("builtin {key}"));
            };
            let made = self.op(node, op, None, &args)?;
            if f == Some(Function::Ne) {
                let not = Op::named("Boolean.not").unwrap();
                let made = self.b.op(not, &[made]);
                return Ok(self.made(made, node));
            }
            return Ok(made);
        }
        // `object.name`, a getter's or field's value.
        let callee = self.children(node)[0];
        let (object, method) = match f {
            Some(Function::Dot) => (self.children(node)[1], false),
            _ => match self.dot_object(callee) {
                Some(object) => (object, true),
                None => return self.not_yet(node, format!("builtin {key}")),
            },
        };
        if method {
            if let Some(op) = Op::named(key) {
                return self.op(node, op, Some(object), &args);
            }
            return self.core_method(node, key, object, &args);
        }
        let Some(member) = self.core_decl(key) else {
            return self.not_yet(node, format!("core's {key}"));
        };
        let kind = self.b.program().decls.get(member).kind;
        match (kind, method) {
            (DeclKind::Field, false) => {
                let receiver = self.expr(object)?;
                let made = self.b.get_field(member, receiver);
                Ok(self.own(made, node))
            }
            (DeclKind::StaticField, false) => {
                // `String.begin`: the type named isn't a value.
                let made = self.b.get_static(member);
                Ok(self.own(made, node))
            }
            (DeclKind::Getter, false) => {
                let receiver = self.expr(object)?;
                // As its owner, where that's a supertype, as core's
                // `Error` is a failure type's; not a generic one's.
                let owner = self.b.program().decls.get(member).owner;
                let receiver = match self.b.program().decls.type_params(owner).is_empty() {
                    true => {
                        let owner = self.b.nominal(owner);
                        self.fit(receiver, owner)
                    }
                    false => receiver,
                };
                let made = self.b.call_method(member, receiver, &[]);
                Ok(self.own(made, node))
            }
            (DeclKind::Method, true) => {
                let receiver = self.expr(object)?;
                let values = self.args_for(node, member, &args)?;
                let made = self.b.call_method(member, receiver, &values);
                Ok(self.own(made, node))
            }
            (DeclKind::Function, true) => {
                let values = self.args_for(node, member, &args)?;
                let made = self.b.call(member, &values);
                Ok(self.own(made, node))
            }
            _ => self.not_yet(node, format!("core's {key} used so")),
        }
    }

    /// `char'a'`, the code point of a single character, as an `Int32`
    /// constant, as Kotlin makes it.
    fn char_tag(&mut self, node: FNode, args: &[FNode]) -> Tx<NodeId> {
        let lib = &self.s.library;
        let parts = match args {
            [parts, _] => self.children(*parts).get(1..).unwrap_or(&[]).to_vec(),
            _ => Vec::new(),
        };
        let code = match parts[..] {
            [raw] => match lib.value(raw) {
                Some(Value::String(raw)) => {
                    let text = temper_syntax::unpack::unpack_string(raw, false);
                    let mut chars = text.as_deref().unwrap_or("").chars();
                    match (chars.next(), chars.next()) {
                        (Some(c), None) => Some(c as i32),
                        _ => None,
                    }
                }
                _ => None,
            },
            _ => None,
        };
        let Some(code) = code else {
            return self.not_yet(node, "a char tag of other than one character");
        };
        let made = self.b.int32(code);
        Ok(self.own(made, node))
    }

    /// `raw"…"`: its text as written, escapes and all, as a constant;
    /// with no interpolations so far.
    fn raw_tag(&mut self, node: FNode, args: &[FNode]) -> Tx<NodeId> {
        let lib = &self.s.library;
        let (parts, values) = match args {
            [parts, values] => (
                self.children(*parts).get(1..).unwrap_or(&[]),
                self.children(*values).get(1..).unwrap_or(&[]),
            ),
            _ => return self.not_yet(node, "a raw tag's form"),
        };
        let mut text = String::new();
        for &part in parts {
            match lib.value(part) {
                Some(Value::String(raw)) if values.is_empty() => text.push_str(raw),
                _ => return self.not_yet(node, "a raw tag with interpolations"),
            }
        }
        let made = self.b.string(&text);
        Ok(self.own(made, node))
    }

    /// A call of core's method `key` on `object`, with the type arguments
    /// typing solved, or of a static function of its type.
    fn core_method(&mut self, node: FNode, key: &str, object: FNode, args: &[FNode]) -> Tx<NodeId> {
        let Some(member) = self.core_decl(key) else {
            return self.not_yet(node, format!("core's {key}"));
        };
        match self.b.program().decls.get(member).kind {
            DeclKind::Method => {
                let mut receiver = self.expr(object)?;
                // A non-generic owner's type, a supertype's method as
                // `StringIndex`'s `compareTo`, `StringIndexOption`'s.
                let owner = self.b.program().decls.get(member).owner;
                if self.b.program().decls.type_params(owner).is_empty() {
                    receiver = self.fit(receiver, self.b.program().decls.get(owner).ty);
                }
                let type_args = self.call_type_args(node, member)?;
                let values = self.args_for_with(node, member, Some(receiver), &type_args, args)?;
                let made = self
                    .b
                    .call_method_generic(member, &type_args, receiver, &values);
                Ok(self.own(made, node))
            }
            DeclKind::Function => {
                let values = self.args_for(node, member, args)?;
                let made = self.b.call(member, &values);
                Ok(self.own(made, node))
            }
            _ => self.not_yet(node, format!("core's {key} used so")),
        }
    }

    /// An operator applied to its operands, a receiver first if any.
    fn op(&mut self, node: FNode, op: Op, receiver: Option<FNode>, args: &[FNode]) -> Tx<NodeId> {
        let arity = op.info().map_or(0, |i| i.params.len());
        if receiver.iter().count() + args.len() != arity {
            return self.not_yet(node, "an operator's arguments");
        }
        // A float division of constants, as `1.0 / 2.4`, is its value, as
        // Kotlin folds it, which can't fail.
        if matches!(
            op.info().map(|i| i.name),
            Some("Float64.div" | "Float64.mod")
        ) {
            let folded =
                crate::typing::fold_float(&self.s.library, &self.s.scopes, &self.s.typing, node);
            if let Some(value) = folded {
                let made = self.b.float64(value);
                return Ok(self.own(made, node));
            }
        }
        let mut values = Vec::new();
        for &arg in receiver.iter().chain(args) {
            values.push(self.expr(arg)?);
        }
        let params = op.info().map_or(&[][..], |i| i.params);
        let nullable = values.iter().any(|&v| {
            self.b
                .program()
                .types
                .is_nullable(self.b.program().nodes.ty(v))
        });
        if nullable && op.info().is_some_and(|i| i.name.ends_with(".eq")) {
            // `==` with either side maybe null.
            let want = self.b.nullable(params[0]);
            let [left, right] = values[..] else {
                return self.not_yet(node, "an operator's arguments");
            };
            let (left, right) = (self.fit(left, want), self.fit(right, want));
            let made = self.b.eq_nullable(op, left, right);
            return Ok(self.own(made, node));
        }
        let values: Vec<NodeId> = (values.into_iter().zip(params.to_vec()))
            .map(|(v, t)| self.fit(v, t))
            .collect();
        // `/` or `%` by a constant that can't fail, not 0, and for `%`
        // positive, is `divSafe` or `modSafe`, which never bubble, as
        // Kotlin's `DivIntIntSafe` and the like, wherever it is.
        let op = match (op.info().map(|i| i.name), args.last()) {
            (
                Some(name @ ("Int32.div" | "Int64.div" | "Int32.mod" | "Int64.mod")),
                Some(&divisor),
            ) => {
                let div = name.ends_with(".div");
                match self.constant_int(divisor) {
                    Some(n) if (div && n != 0) || n > 0 => {
                        Op::named(&format!("{name}Safe")).unwrap_or(op)
                    }
                    _ => op,
                }
            }
            _ => op,
        };
        // A divisor that typing lets a function that can't bubble use,
        // as one Kotlin might fold, but that doesn't fold here, as a
        // builtin's call, outside any `orelse`'s body: not yet.
        let bubbles = op.info().is_some_and(|i| i.bubbles);
        let owner = self.b.program().decls.get(self.owner);
        let callable = !matches!(owner.kind, DeclKind::Module);
        let can_bubble = self
            .b
            .program()
            .signature(self.owner)
            .is_some_and(|s| s.bubbles());
        let caught = self.catching.contains(&self.owner);
        if bubbles && callable && !can_bubble && !caught {
            return self.not_yet(node, "a division that can't fail, where nothing may bubble");
        }
        let made = self.b.op(op, &values);
        Ok(self.own(made, node))
    }

    /// An integer constant's value, as typing folds it
    /// ([`crate::typing::fold_int`]): what makes a division one that
    /// can't fail.
    fn constant_int(&self, node: FNode) -> Option<i64> {
        crate::typing::fold_int(&self.s.library, &self.s.scopes, &self.s.typing, node)
    }

    /// A value whose value is dropped: typed `Void` if typed by its
    /// place, else as it is.
    fn fit_void(&mut self, value: NodeId) -> NodeId {
        if self.b.program().nodes.ty(value).is_none() {
            self.b.retype(value, TypeId::VOID);
        }
        value
    }

    /// `a == null` or `a != null`, either way round, as `IsNull`.
    fn null_check(&mut self, node: FNode, f: Option<Function>, args: &[FNode]) -> Tx<NodeId> {
        let args = self.plain_args(node, args)?;
        let is_null = |s: &Self, a: FNode| {
            matches!(s.s.library.value(a), Some(Value::Null))
                || matches!(
                    s.s.scopes.resolved_builtin(a),
                    Some(Builtin::Value(Constant::Null))
                )
        };
        let value = match args[..] {
            [a, b] if is_null(self, b) => a,
            [a, b] if is_null(self, a) => b,
            _ => return self.not_yet(node, "a null check's form"),
        };
        let value = self.expr(value)?;
        let ty = self.b.program().nodes.ty(value);
        if ty.is_none() {
            // `null == null`.
            return self.not_yet(node, "a null check of null");
        }
        let want = self.b.nullable(ty);
        let value = self.fit(value, want);
        let check = self.b.is_null(value);
        let check = self.own(check, node);
        if f == Some(Function::Ne) {
            let not = Op::named("Boolean.not").unwrap();
            let made = self.b.op(not, &[check]);
            return Ok(self.made(made, node));
        }
        Ok(check)
    }

    /// `body orelse fallback`, typed as typing says, or `Void` where it
    /// stands alone.
    fn or_else(&mut self, node: FNode) -> Tx<NodeId> {
        self.or_else_typed(node, None)
    }

    /// `body orelse fallback`, typed `ty` if given.
    fn or_else_typed(&mut self, node: FNode, ty: Option<TypeId>) -> Tx<NodeId> {
        let [body, fallback] = self.children(node).to_vec()[..] else {
            return self.not_yet(node, "an orelse's form");
        };
        let typed = match self.s.typing.expr(node) {
            _ if ty.is_some() => ty,
            Some(t) if t != ft::NEVER && self.s.typing.types.is_known(t) => Some(self.ty(t, node)?),
            _ => None,
        };
        // Where typing has no type for it, as a function's last value, the
        // type of its parts' values, known before a block's value is made.
        let typed = match typed {
            Some(t) => Some(t),
            None => self.value_type(&[body, fallback]),
        };
        self.catching.push(self.owner);
        let body = self.block_value(body, typed.unwrap_or(TypeId::NONE));
        self.catching.pop();
        let body = body?;
        // A body that can't fail, as `(x = 1)`'s or an upcast's, is all
        // there is to it.
        if !self.may_bubble(body) {
            let ty = typed.unwrap_or_else(|| self.b.program().nodes.ty(body));
            return Ok(if ty == TypeId::VOID || ty.is_none() {
                self.fit_void(body)
            } else {
                self.fit(body, ty)
            });
        }
        // A fallback that needs the failure, from a body that fails with
        // a failure type, binds it, in a `catch`.
        let bound = match self.needs_failure(fallback) {
            true => self.handled_type(body),
            false => None,
        };
        let local = match bound {
            Some(ty) => Some(self.catch_local(fallback, ty)?),
            None => None,
        };
        self.handling.push(failures::Handling {
            owner: self.owner,
            caught: local.map(failures::Caught::Bound),
        });
        let fallback = self.block_value(fallback, typed.unwrap_or(TypeId::NONE));
        self.handling.pop();
        let fallback = fallback?;
        // Where typing has no type for it, as a function's last value, its
        // parts' type.
        let ty = typed.unwrap_or_else(|| {
            let p = self.b.program();
            [body, fallback]
                .into_iter()
                .map(|v| p.nodes.ty(v))
                .find(|t| !t.is_none())
                .unwrap_or(TypeId::VOID)
        });
        // A `Void` one drops its parts' values, whatever their types.
        let (body, fallback) = if ty == TypeId::VOID {
            (self.fit_void(body), self.fit_void(fallback))
        } else {
            (self.fit(body, ty), self.fit(fallback, ty))
        };
        let made = match local {
            Some(local) => self.b.catch(ty, local, body, fallback),
            None => self.b.or_else(ty, body, fallback),
        };
        Ok(self.own(made, node))
    }

    /// A `catch`'s local, of type `ty`: a `catch (e)`'s `e`, which its
    /// `let e = caught()` then doesn't declare again, or one the compiler
    /// makes, for a fallback that only throws.
    fn catch_local(&mut self, fallback: FNode, ty: TypeId) -> Tx<DeclId> {
        if let Some(bind) = self.catch_binding(fallback) {
            let local = self.declare_let(bind)?;
            self.adopted.insert(bind);
            return Ok(local);
        }
        let local = (self.b).declare_made(DeclKind::Local, &["failure"], self.owner, ty);
        let span = self.span(fallback);
        self.b.set_decl_spans(local, span, span);
        Ok(local)
    }

    /// Whether anything in a translated value may bubble.
    fn may_bubble(&self, value: NodeId) -> bool {
        let p = self.b.program();
        let mut found = false;
        temper_tmpl::analysis::walk(p, value, &mut |n| {
            // An error fails as `bubble()` does.
            found |= p.nodes.flags(n).contains(temper_tmpl::Flags::MAY_BUBBLE)
                || matches!(p.nodes.get(n), temper_tmpl::Node::Error { .. });
        });
        found
    }

    /// The first type typing knows of these values, each a block's last
    /// value if it's a block, made nullable if another is `null`, as
    /// `f() orelse null`'s.
    fn value_type(&mut self, values: &[FNode]) -> Option<TypeId> {
        let null = values.iter().any(|&v| {
            let v = self.last_value(v);
            (self.s.typing.expr(v))
                .is_some_and(|t| matches!(self.s.typing.types.get(t), FTypeData::Null))
        });
        let found = self.known_value_type(values)?;
        let nullable = self.b.program().types.is_nullable(found);
        Some(if null && !nullable {
            self.b.nullable(found)
        } else {
            found
        })
    }

    /// A value, or a block's last value, through nested blocks.
    fn last_value(&self, value: FNode) -> FNode {
        let mut value = value;
        while self.kind(value) == NodeKind::Block {
            match self.children(value).last() {
                Some(&last) => value = last,
                None => break,
            }
        }
        value
    }

    /// The first type typing knows of these values, as [`Self::value_type`].
    fn known_value_type(&mut self, values: &[FNode]) -> Option<TypeId> {
        for &value in values {
            let value = self.last_value(value);
            match self.s.typing.expr(value) {
                Some(t) if t != ft::NEVER && self.s.typing.types.is_known(t) => {
                    // A type tmpl doesn't have yet is left to the parts.
                    if let Ok(t) = self.ty(t, value) {
                        return Some(t);
                    }
                }
                _ => {}
            }
        }
        None
    }

    /// The arguments for a call of `callee`, with `Omitted` for optional
    /// parameters left out.
    fn args_for(&mut self, node: FNode, callee: DeclId, args: &[FNode]) -> Tx<Vec<NodeId>> {
        self.args_for_with(node, callee, None, &[], args)
    }

    /// [`Translator::args_for`], with the type parameters the call binds
    /// put in: its type arguments, and the receiver's.
    fn args_for_with(
        &mut self,
        node: FNode,
        callee: DeclId,
        receiver: Option<NodeId>,
        type_args: &[TypeId],
        args: &[FNode],
    ) -> Tx<Vec<NodeId>> {
        let Some(sig) = self.b.program().signature(callee) else {
            return self.not_yet(node, "a call without a signature");
        };
        let (params, optional) = (sig.params.to_vec(), sig.optional.to_vec());
        let receiver = receiver.map(|r| self.b.program().nodes.ty(r));
        let (from, to) =
            temper_tmpl::analysis::bindings(self.b.program(), callee, receiver, type_args);
        let params: Vec<TypeId> = (params.into_iter())
            .map(|p| self.b.program_mut().types.substitute(p, &from, &to))
            .collect();
        if args.len() > params.len() || optional[args.len()..].contains(&false) {
            return self.not_yet(node, "an argument left out");
        }
        let mut values = Vec::new();
        for ((&arg, &ty), &optional) in args.iter().zip(&params).zip(&optional) {
            let value = self.expr(arg)?;
            let null = self.b.program().nodes.kind(value) == temper_tmpl::Kind::Null;
            if null && optional && !self.b.program().types.is_nullable(ty) {
                // Core's optional parameter that isn't nullable, left out
                // before one given by name, which typing passes as null.
                let omitted = self.b.omitted(ty);
                values.push(self.made(omitted, arg));
                continue;
            }
            values.push(self.fit(value, ty));
        }
        for &ty in &params[args.len()..] {
            let omitted = self.b.omitted(ty);
            values.push(self.made(omitted, node));
        }
        Ok(values)
    }

    /// `left` and `value` joined by `String.cat`, or `value` first.
    fn joined(&mut self, left: Option<NodeId>, value: NodeId, node: FNode) -> NodeId {
        match left {
            None => value,
            Some(left) => {
                let cat = Op::named("String.cat").unwrap();
                let made = self.b.op(cat, &[left, value]);
                self.made(made, node)
            }
        }
    }

    /// A template's part, not null, as a `String`: itself, or its
    /// builtin type's `toString`, or its class's.
    fn part_string(&mut self, value: NodeId, part: FNode) -> Tx<NodeId> {
        let ty = self.b.program().nodes.ty(value);
        let name = match ty {
            TypeId::STRING => return Ok(value),
            TypeId::BOOLEAN => "Boolean",
            TypeId::INT32 => "Int32",
            TypeId::INT64 => "Int64",
            TypeId::FLOAT64 => "Float64",
            _ => return self.display_call(value, part),
        };
        let Some(to_string) = self.core_decl(&format!("{name}.toString")) else {
            return self.not_yet(part, "a template's part of another type");
        };
        let sig = self.b.program().signature(to_string).unwrap();
        let params = sig.params.to_vec();
        let mut args = Vec::new();
        for ty in params {
            let omitted = self.b.omitted(ty);
            args.push(self.made(omitted, part));
        }
        let made = self.b.call_method(to_string, value, &args);
        Ok(self.made(made, part))
    }

    /// A value held in a new local, named after `hint`, to read more
    /// than once: the local's `let`, and the local.
    fn held(&mut self, value: NodeId, at: FNode, hint: &str) -> (NodeId, DeclId) {
        let ty = self.b.program().nodes.ty(value);
        let local = self
            .b
            .declare_made(DeclKind::Local, &[hint], self.owner, ty);
        let span = self.span(at);
        self.b.set_decl_spans(local, span, span);
        let stmt = self.b.let_stmt(local, Some(value));
        (self.made(stmt, at), local)
    }

    /// A class's or interface's value in a template: a call of its
    /// `toString()`, its own or the nearest inherited one, as Kotlin
    /// Temper's templates call it.
    fn display_call(&mut self, value: NodeId, part: FNode) -> Tx<NodeId> {
        let ty = self.b.program().nodes.ty(value);
        let p = self.b.program();
        // A type parameter's value, by its bound's, as `T extends Shown`.
        let ty = match p.types.contains(ty).then(|| p.types.get(ty)) {
            Some(TypeData::TypeParam { decl }) => {
                (p.decls.supers(decl).first().copied()).unwrap_or(ty)
            }
            _ => ty,
        };
        let Some(class) = temper_tmpl::analysis::nominal_decl(self.b.program(), ty) else {
            // In a library with errors, a value whose type one left
            // unknown, which `unknown_ty` makes any value: the part fails
            // where it runs, as the error's code does.
            if ty == TypeId::ANY_VALUE && self.has_errors() {
                let made = self
                    .b
                    .error(TypeId::STRING, "a template's part of unknown type");
                return Ok(self.made(made, part));
            }
            return self.not_yet(part, "a template's part of another type");
        };
        let mut owners = vec![class];
        owners.extend(temper_tmpl::analysis::ancestors(self.b.program(), class));
        // Other libraries' classes declare only the members used.
        for &owner in &owners {
            self.external_method_named(owner, "toString", part)?;
        }
        let p = self.b.program();
        let found = owners.iter().find_map(|&owner| {
            p.decls.ids().find(|&d| {
                let decl = p.decls.get(d);
                decl.owner == owner
                    && decl.kind == DeclKind::Method
                    && p.name(d) == "toString"
                    && p.signature(d)
                        .is_some_and(|s| s.params.is_empty() && s.pass == TypeId::STRING)
            })
        });
        let Some(to_string) = found else {
            return self.not_yet(part, "a template's part of another type");
        };
        let owner = self.b.program().decls.get(to_string).owner;
        let receiver = if self.b.program().decls.type_params(owner).is_empty() {
            let owner_ty = self.b.program().decls.get(owner).ty;
            self.fit(value, owner_ty)
        } else {
            value
        };
        let made = self.b.call_method(to_string, receiver, &[]);
        Ok(self.made(made, part))
    }

    /// A string template, `"a${b}"`: each part as a `String`, by its
    /// `toString` if it's another builtin type, joined by `String.cat`.
    /// A nullable part is `null` where it's null, as Kotlin Temper's
    /// templates show it.
    fn cat(&mut self, node: FNode, parts: &[FNode]) -> Tx<NodeId> {
        let mut joined: Option<NodeId> = None;
        for &part in parts {
            let value = self.expr(part)?;
            let ty = self.b.program().nodes.ty(value);
            let value = if self.b.program().types.is_nullable(ty) {
                // `if (t == null) "null" else string(t)`, with `t` held in a
                // local first unless it's one: `{ let t = value; … }`.
                let (stmt, local) = match self.b.program().nodes.get(value) {
                    temper_tmpl::Node::Local { local } => (None, local),
                    _ => {
                        let (stmt, local) = self.held(value, part, "part");
                        (Some(stmt), local)
                    }
                };
                let read = self.b.local(local);
                let read = self.made(read, part);
                let check = self.b.is_null(read);
                let check = self.made(check, part);
                let null = self.b.string("null");
                let null = self.made(null, part);
                let read = self.b.local(local);
                let read = self.made(read, part);
                let read = self.b.non_null(read);
                let read = self.made(read, part);
                let shown = self.part_string(read, part)?;
                let made = self.b.if_value(TypeId::STRING, check, null, shown);
                let made = self.made(made, part);
                match stmt {
                    Some(stmt) => {
                        let made = self.b.block_value(TypeId::STRING, &[stmt], made);
                        self.made(made, part)
                    }
                    None => made,
                }
            } else {
                self.part_string(value, part)?
            };
            joined = Some(self.joined(joined, value, node));
        }
        match joined {
            Some(joined) => Ok(joined),
            None => {
                let empty = self.b.string("");
                Ok(self.own(empty, node))
            }
        }
    }
}
