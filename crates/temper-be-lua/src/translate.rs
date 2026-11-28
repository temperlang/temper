//! The translation from tmpl to Lua's output tree, for subsets A and B
//! (classes and interfaces, in [`crate::classes`]).
//!
//! A module is a chunk that runs once, when it's first `require`d, as
//! Kotlin's, and returns its exports in a table. Its functions are local
//! functions, and its module `let`s and `var`s locals of the chunk, which
//! functions refer to as upvalues. Lua resolves a name where it's read, to
//! the innermost local declared before it in the text, so the chunk is
//! laid out as:
//!
//! 1. `local temper = require("temper-core")`, if it uses the runtime,
//!    and then its `Int64` constants, each made once by the runtime's
//!    `int64_constructor` (`local int64_5 =
//!    temper.int64_constructor(5)`), which literals name, so a loop
//!    doesn't call it each time (past [`INT64_LOCALS`] of them, they're
//!    one table's fields, `int64s.int64_5`);
//! 2. `local exports = {}`, if an exported `var` is written by functions
//!    (below);
//! 3. module `let`s and `var`s that functions or members use, or that the
//!    init block doesn't initialize at its top level, declared with their
//!    type (`---@type integer`) and no value
//!    ([`module_state`]);
//! 4. functions and types referred to before they're defined, declared
//!    together (`local f, g`), and then defined without `local`;
//! 5. `with_tostring`, if a class uses it, then the functions and types, in
//!    source order, but each type after its supertypes, whose members
//!    making it copies;
//! 6. the init block, where a module `let` or `var` initialized at its top
//!    level, and not declared before, is declared with its value, and
//!    static fields are set on their types' tables;
//! 7. the exports, `return { … }`.
//!
//! **Exported `var`s that functions or members write** live in the
//! exports table itself (`exports.total`), so a library's users see their
//! current value. Kotlin's copies every export into the table once, after
//! the init block, so such a `var` stays at its first value for users. A
//! type's statics are its table's, which the exports share, so they're
//! current too.
//!
//! **Modules and imports:** each module is a chunk of its own, required
//! by its path (`lib/helpers/deep`, the root module by the library's
//! name), as Kotlin's. A module's functions, `let`s, and types used from
//! another are locals there, `local greet = require('lib/helpers/deep').greet`;
//! its `var`s are read where they're used, from its exports table, so
//! the read is current. Tests go in a file of their own
//! ([`Translator::tests`]), which reaches what its module doesn't export,
//! and the `var`s its tests assign, through a table its module adds to
//! its exports for them, under `'$test'`. The root module requires the
//! library's other modules that it doesn't import, so requiring the
//! library runs them all, as Kotlin's Python and JS do, but for those
//! that import the root, directly or not, whose `require` would recurse.
//!
//! **Lua's limit on locals:** a function, the chunk included, may have at
//! most 200 locals alive at once. Past 128 module-level locals (Kotlin's
//! threshold), the module's functions and module `let`s after its first
//! 100 are fields of a `module` table instead, as Kotlin's keeps its first
//! 100 and puts the rest in a table (`env_t1`); a function with more than
//! 200 locals alive at once is not yet.
//!
//! **Jumps:** `break` is Lua's own for the innermost loop. A `continue` is
//! `goto continue`, to a label ending the loop's body (`::continue::`),
//! which runs a `repeat`'s condition, as Temper's `do … while` does; a
//! jump from an inner loop to an outer one is a `goto` to a label named
//! for the loop, ending its body (`continue_outer`) or after it
//! (`break_outer`). Labels are printed only where a jump needs one, and
//! `goto` needs Lua 5.2 or later, or LuaJIT, as Kotlin's output does.
//!
//! **Hoisting:** what Lua can't write inline goes before the statement
//! it's in, leaving a temporary (`t`, `t_1`, …, named unlike anything
//! the program names): an `orelse`, as `---@type T local t` and `if not
//! temper.pcall(function() t = body end) then t = fallback end`, as
//! Kotlin's, or straight into the local or module `let` it initializes;
//! a `BlockValue`'s statements, in place; and an `if` value, `&&`, or
//! `||` that has such a part where it runs only sometimes, or an `if`
//! value that `and` and `or` can't choose, as an `if` statement. What
//! runs before the hoisted part in Temper's order must have no effects,
//! and a loop's condition hoists nothing; otherwise it's not yet. An
//! `orelse`'s body is a function of its own, so a `return`, `break`, or
//! `continue` out of it returns a signal that an `if` after the
//! `temper.pcall` redoes, as Kotlin's ([`TryBody`]).
//!
//! **Order of evaluation:** Lua evaluates operands and arguments left to
//! right (its reference implementation and LuaJIT; the manual leaves only
//! multiple assignment's order open, which this never prints), so Temper's
//! order needs no temporaries. Where Lua's `and` and `or` would skip an
//! operand that Temper evaluates (an `if` value's spelling, `c and a or
//! b`, and `Boolean`'s order, `not a and b`), the translator checks with
//! [`Effects`] that skipping or repeating it can't show, and otherwise
//! spells it another way.

use crate::NotYet;
use crate::classes::{self, Types};
use crate::connected::{self, Connection, TypeConnection};
use crate::ops::{self, Spelling};
use crate::tree::{self, Bin, Expr, Signature, Stmt};
use std::collections::{HashMap, HashSet};
use temper_tmpl::analysis::{self, Effects, home, module_imports, module_state, walk};
use temper_tmpl::host::{HostFile, uses_host};
use temper_tmpl::naming::NameMap;
use temper_tmpl::qname::{by_connection, is_external, library_of};
use temper_tmpl::{
    DeclId, DeclKind, Facts, Module, Node, NodeId, Program, QNames, Scalar, TypeData, TypeId,
};

pub(crate) type R<T> = Result<T, NotYet>;

fn not_yet<T>(what: impl Into<String>) -> R<T> {
    Err(NotYet { what: what.into() })
}

/// The most locals Lua lets a function have alive at once
/// (`LUAI_MAXVARS`, 200 in every Lua since 5.1, and in LuaJIT).
pub const MAX_LOCALS: usize = 200;

/// Past this many module-level locals, module-level declarations after
/// the first [`KEPT_LOCALS`] go into a table, as Kotlin's do
/// (`MAX_ALLOWABLE_LOCALS`), leaving room in the chunk for the init
/// block's own locals, and for functions' upvalues.
pub const MODULE_LOCALS: usize = 128;

/// How many module-level declarations stay locals past [`MODULE_LOCALS`]
/// (Kotlin's `LOCALS_TO_KEEP`).
pub const KEPT_LOCALS: usize = 100;

/// The table that holds module-level declarations past [`MODULE_LOCALS`].
pub const MODULE_TABLE: &str = "module";

/// The table of exports, declared first when it holds live `var`s.
pub const EXPORTS: &str = "exports";

/// The most distinct `Int64` constants a chunk declares as locals; past
/// it, they're all fields of one table, [`INT64S`], so they cost the
/// chunk one local, and a function one upvalue (of the 60 that Lua 5.1
/// and LuaJIT allow).
pub const INT64_LOCALS: usize = 32;

/// The table that holds a chunk's `Int64` constants past
/// [`INT64_LOCALS`].
pub const INT64S: &str = "int64s";

/// A chunk's `Int64` constants, each made once, before anything that can
/// use it runs, by the runtime's `int64_constructor` (a call, which on
/// Lua 5.3 and later gives an integer, and on 5.1 and LuaJIT a table,
/// which nothing changes in place), and named for its value:
/// `int64_5`, `int64_neg1`.
#[derive(Debug, Default)]
struct Int64s {
    /// Whether they're [`INT64S`]'s fields instead of locals.
    table: Option<String>,
    /// Their names, by value, in order of first use.
    names: Vec<(i64, String)>,
    /// Names they mustn't take: the program's and host code's.
    taken: HashSet<String>,
}

/// Labels a loop needs, by what jumps to it.
#[derive(Clone, Debug, Default)]
struct Labels {
    /// Ending its body, for a `continue`.
    next: Option<String>,
    /// After it, for a `break` from an inner loop.
    end: Option<String>,
}

/// What a chunk declares and runs ([`Translator::part`]), in the order
/// it writes them, after its imports: the tables it keeps declarations
/// in, module `let`s and `var`s declared before the functions, functions
/// declared before they're defined, the functions and types, and the
/// init block's statements; and its declarations, in source order, and
/// whether its exports table holds any `var`s.
struct Part {
    declared: Vec<(NodeId, DeclId)>,
    live: bool,
    head: Vec<Stmt>,
    lets: Vec<Stmt>,
    forward: Vec<DeclId>,
    defs: Vec<Stmt>,
    init: Vec<Stmt>,
}

pub struct Translator<'a> {
    pub(crate) p: &'a Program,
    pub(crate) names: &'a NameMap,
    pub(crate) qnames: QNames,
    pub(crate) effects: Effects,
    /// Whether [`ty`](Self::ty) writes a local's `---@type`.
    local_types: std::cell::Cell<bool>,
    /// Module `let`s of an erased type, which aren't declared.
    pub(crate) erased: HashSet<DeclId>,
    /// Module-level declarations that are fields of a table, by the
    /// table's name.
    pub(crate) fields: HashMap<DeclId, &'static str>,
    /// Whether the module calls the runtime.
    pub(crate) uses_temper: bool,
    /// The enclosing loops, innermost last.
    loops: Vec<DeclId>,
    /// The enclosing `orelse` bodies, innermost last, in the function
    /// being translated ([`Self::or_else`]).
    tries: Vec<TryBody>,
    /// Labels printed, by loop.
    labels: HashMap<DeclId, Labels>,
    /// Whether translating a function with a result, where a bubble is
    /// `return temper.bubble()`, so LuaLS sees every path return.
    pub(crate) returns: bool,
    /// The module whose chunk this is; `NONE` for a tests file.
    pub(crate) here: DeclId,
    /// In a tests file, the module tested, reached through `tested`.
    pub(crate) tested: DeclId,
    /// In a tests file, what its module's test parts define, which are
    /// the file's own.
    test_own: HashSet<DeclId>,
    /// The program's classes and interfaces.
    pub(crate) types: Types,
    /// Module `var`s that a function or a member writes.
    pub(crate) mutable: HashSet<DeclId>,
    /// The module's functions that may do what [`Effects`] doesn't see
    /// ([`classes::classy`]).
    pub(crate) classy: HashSet<DeclId>,
    /// Statements that must run before the statement being translated:
    /// what an expression Lua can't write inline hoists ([`Self::hoist`]).
    pre: Vec<Stmt>,
    /// Parameters' defaults' locals, which take their parameters' names:
    /// the default sets the parameter in place.
    defaulted: HashMap<DeclId, DeclId>,
    /// Local functions declared before their group's first, since one
    /// before them calls them ([`Self::forward_locals`]).
    forward: HashSet<DeclId>,
    /// Temporaries made in the function being translated, for names.
    temps: usize,
    /// Every name the naming pass gave, which temporaries avoid.
    taken: HashSet<String>,
    /// The chunk's `Int64` constants.
    int64s: Int64s,
}

/// An `orelse` body, which `temper.pcall` runs as a function of its own,
/// so a jump out of it returns a signal: `return v, "return"`, or
/// `return "break", "flow"` naming the jump by what it is just outside
/// (`break`, or the label it goes to), as Kotlin's.
struct TryBody {
    /// How many loops enclose it.
    loops: usize,
    /// The jumps out of it, in order of first use.
    jumps: Vec<Jump>,
}

#[derive(Clone, PartialEq)]
enum Jump {
    Return,
    /// A `break` or a `continue` of a loop, by its signal's name.
    Loop {
        label: DeclId,
        brk: bool,
        name: String,
    },
}

/// The key of the table a module with tests adds to its exports for them.
pub const TEST_KEY: &str = "$test";

/// What a module's tests file uses of it (its tests, and what only they
/// reach, which only that file has), which the table under [`TEST_KEY`]
/// holds, and of those, the `var`s, which it reads and assigns through
/// functions there, `NAME__get` and `NAME__set`, as Kotlin's
/// `neededByTest` exposes what its tests use.
#[derive(Clone, Debug, Default)]
pub struct TestAccess {
    pub used: Vec<DeclId>,
    pub vars: Vec<DeclId>,
}

impl TestAccess {
    /// What a module's tests file uses of it: what its test parts
    /// (`analysis::test_parts`) name, and the types they cast to.
    pub fn of(p: &Program, module: &Module) -> TestAccess {
        let parts = temper_tmpl::analysis::test_parts(p, module);
        let mut used = parts.used;
        for root in module.test_roots() {
            walk(p, root, &mut |id| {
                if let Node::As { .. } = p.nodes.get(id) {
                    let cast = temper_tmpl::analysis::nominal_decl(p, p.nodes.ty(id));
                    let local = cast.filter(|&t| home(p, t) == Some(module.decl));
                    used.extend(local.filter(|t| !parts.own.contains(t)));
                }
            });
        }
        used.sort();
        used.dedup();
        let vars = (used.iter().copied()).filter(|&d| is_var(p, d)).collect();
        TestAccess { used, vars }
    }
}

/// Whether a module imports a declaration of another's as a local: a
/// function, a module `let` (but not a `var`, read where it's used), or a
/// class or interface, but not std's `testing`'s, the runtime's on Lua.
fn importable(p: &Program, decl: DeclId) -> bool {
    match p.decls.get(decl).kind {
        DeclKind::Function => true,
        DeclKind::ModuleLet => !is_var(p, decl),
        // std's `Test`, which the runtime gives a test as a table, isn't;
        // std's other classes, as regex's `Sequence`, are.
        DeclKind::Class | DeclKind::Interface => {
            !temper_tmpl::qname::std_key(p, decl).is_some_and(|k| k.starts_with("std/testing."))
        }
        _ => false,
    }
}

fn is_var(p: &Program, decl: DeclId) -> bool {
    let d = p.decls.get(decl);
    d.kind == DeclKind::ModuleLet && d.facts.contains(Facts::VAR)
}

/// What a module's chunk requires: its items' and init block's imports,
/// and the classes its casts check.
fn chunk_imports(p: &Program, module: &Module) -> Vec<analysis::Import> {
    let roots: Vec<NodeId> = (module.items.iter().copied())
        .chain([module.init])
        .collect();
    classes::with_casts(p, module.decl, &roots, module_imports(p, module))
}

/// The library's modules whose chunks require `target`'s, directly or
/// through others, which the root module mustn't require, since Lua's
/// `require` of a chunk still running recurses.
fn importers_of(p: &Program, target: DeclId) -> HashSet<DeclId> {
    let edges: Vec<(DeclId, Vec<DeclId>)> = (p.modules.iter())
        .map(|m| (m.decl, chunk_imports(p, m).iter().map(|i| i.from).collect()))
        .collect();
    let mut found = HashSet::new();
    let mut grew = true;
    while grew {
        grew = false;
        for (m, from) in &edges {
            if *m != target
                && !found.contains(m)
                && from.iter().any(|f| *f == target || found.contains(f))
            {
                found.insert(*m);
                grew = true;
            }
        }
    }
    found
}

/// What `require` names a module by: the library's name for its root
/// module, and its path after it for another, as `lib/helpers/deep`; for
/// another library's module, that library's.
pub fn require_path(p: &Program, module: DeclId) -> String {
    let d = p.decls.get(module);
    if d.kind == DeclKind::Library {
        return p.name(module).to_string();
    }
    let (library, path) = if d.owner.is_none() {
        let path = p.modules.iter().find(|m| m.decl == module);
        (
            p.library.name.as_str(),
            path.map_or("", |m| m.path.as_str()),
        )
    } else {
        (p.name(d.owner), p.name(module))
    };
    match path {
        "" => library.to_string(),
        path => format!("{library}/{path}"),
    }
}

impl<'a> Translator<'a> {
    pub fn new(p: &'a Program, names: &'a NameMap, module: &Module) -> Translator<'a> {
        Translator {
            local_types: std::cell::Cell::new(false),
            p,
            names,
            qnames: QNames::derive(p),
            effects: Effects::of(p, module),
            erased: HashSet::new(),
            fields: HashMap::new(),
            uses_temper: false,
            loops: Vec::new(),
            tries: Vec::new(),
            labels: HashMap::new(),
            returns: false,
            here: module.decl,
            tested: DeclId::NONE,
            test_own: HashSet::new(),
            types: Types::of(p, &|d| names.get(d).to_string()),
            mutable: HashSet::new(),
            classy: HashSet::new(),
            pre: Vec::new(),
            defaulted: HashMap::new(),
            forward: HashSet::new(),
            temps: 0,
            taken: p.decls.ids().map(|d| names.get(d).to_string()).collect(),
            int64s: Int64s::default(),
        }
    }

    /// A module-level declaration of another module's: in a tests file,
    /// the tested module's through `tested`; a `var`, read from its
    /// module's exports (`require('lib/helpers').calls`); else the local
    /// it's imported as, by its name. `None` for this module's own.
    fn other(&self, decl: DeclId) -> Option<Expr> {
        let p = self.p;
        // Only module-level declarations: what a module or library owns.
        let owner = p.decls.get(decl).owner;
        if owner.is_none()
            || !matches!(
                p.decls.get(owner).kind,
                DeclKind::Module | DeclKind::Library
            )
        {
            return None;
        }
        let module = home(p, decl).filter(|&m| m != self.here)?;
        // A tests file's own, as what only tests reach.
        if self.test_own.contains(&decl) {
            return None;
        }
        let name = self.name(decl);
        if module == self.tested {
            return Some(if is_var(p, decl) {
                Expr::name("tested").method(format!("{name}__get"), vec![])
            } else {
                Expr::name("tested").member(name)
            });
        }
        if is_var(p, decl) {
            let path = Expr::Str(require_path(p, module));
            let key = self.key(decl);
            return Some(Expr::name("require").call(vec![path]).member(key));
        }
        Some(Expr::name(name))
    }

    pub(crate) fn node(&self, id: NodeId) -> Node {
        self.p.nodes.get(id)
    }

    pub(crate) fn name(&self, decl: DeclId) -> String {
        let decl = self.defaulted.get(&decl).copied().unwrap_or(decl);
        self.names.get(decl).to_string()
    }

    /// An export's key in its module's table: its public name, which
    /// Lua's globals don't escape, as a table's field hides nothing.
    fn key(&self, decl: DeclId) -> String {
        let decl = self.defaulted.get(&decl).copied().unwrap_or(decl);
        self.names.public(decl).to_string()
    }

    /// A module-level declaration, or a local, as code refers to it: its
    /// name, or a table's field (an export's by its key).
    pub(crate) fn access(&self, decl: DeclId) -> Expr {
        if let Some(e) = self.other(decl) {
            return e;
        }
        match self.fields.get(&decl) {
            Some(&EXPORTS) => Expr::name(EXPORTS).member(self.key(decl)),
            Some(table) => Expr::name(*table).member(self.name(decl)),
            None => Expr::name(self.name(decl)),
        }
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

    /// A type as LuaCATS writes it, for LuaLS's checker.
    pub(crate) fn ty(&self, ty: TypeId) -> R<String> {
        Ok(match self.p.types.get(ty) {
            // `temper_tmpl::nevers` made each `Never<T>` the `T` it stands
            // for, as this profile has no bottom type.
            TypeData::Never(t) => return self.ty(t),
            TypeData::Scalar(s) => match s {
                Scalar::Void => "nil",
                Scalar::Boolean => "boolean",
                Scalar::Int32 | Scalar::Int64 => "integer",
                Scalar::Float64 => "number",
                Scalar::String => "string",
                Scalar::AnyValue => "any",
                // A type as a value is its `type()` name.
                Scalar::Type => "string",
            }
            .to_string(),
            // A `@fun` interface is its function type, as Kotlin's.
            TypeData::Nominal { decl, .. } if self.p.decls.get(decl).facts.contains(Facts::FUN) => {
                self.function_type(ty)?
            }
            // A generic class's own, without its type arguments, which
            // LuaLS's classes don't take.
            TypeData::Nominal { decl, .. } if self.is_program_type(decl) => self.lua_type(decl),
            TypeData::Nominal { decl, args } => {
                let mut texts = Vec::new();
                for &a in self.p.types.list(args) {
                    texts.push(self.ty(a)?);
                }
                match (self.qname(decl).as_str(), texts.as_slice()) {
                    ("core.type List" | "core.type Listed" | "core.type ListBuilder", [t]) => {
                        match t.contains('|') {
                            true => format!("({t})[]"),
                            false => format!("{t}[]"),
                        }
                    }
                    ("core.type Map" | "core.type Mapped" | "core.type MapBuilder", [k, v]) => {
                        format!("table<{k}, {v}>")
                    }
                    ("core.type Pair", [k, v]) => format!("{{ key: {k}, value: {v} }}"),
                    // Byte offsets from 1, or -1 for none, as Kotlin's.
                    (
                        "core.type StringIndex"
                        | "core.type StringIndexOption"
                        | "core.type NoStringIndex",
                        [],
                    ) => "integer".to_string(),
                    // The runtime's tables, typed in its LuaCATS view.
                    ("core.type StringBuilder", []) => "temper.StringBuilder".to_string(),
                    ("core.type Deque", [_]) => "temper.Deque".to_string(),
                    ("core.type DenseBitVector", []) => "temper.DenseBitVector".to_string(),
                    ("core.type Promise", [_]) => "temper.Promise".to_string(),
                    ("core.type PromiseBuilder", [_]) => "temper.PromiseBuilder".to_string(),
                    ("core.type SafeGenerator" | "core.type Generator", [_]) => {
                        "temper.Generator".to_string()
                    }
                    // A step's result, `{ true, value }` or `{}`
                    // (`connected::next_safe`).
                    ("core.type GeneratorResult" | "core.type ValueResult", [_])
                    | ("core.type DoneResult", [_]) => "table".to_string(),
                    // `{}`, as the runtime's `temper.empty()` makes.
                    ("core.type Empty", []) => "table".to_string(),
                    // The runtime's test, as `temper.test` gives a test's
                    // body, since Lua connects all of std's `Test`.
                    ("std/testing.type Test", []) => "table".to_string(),
                    // Another library's class, as std's regex `Group`,
                    // by the LuaCATS class its own output declares, a
                    // generic one, as std's `JsonAdapter<T>`, without its
                    // type arguments, as the program's own.
                    _ if library_of(self.p, decl) != "core" => self.lua_type(decl),
                    (q, _) => return not_yet(format!("a type annotation for {q}")),
                }
            }
            TypeData::Function { .. } => self.function_type(ty)?,
            // `temper.null` stands for null, as Kotlin's, since Lua's `nil`
            // can't be a table's value or tell an argument left out.
            // A function type's result would take the `|temper.Null`.
            TypeData::Nullable(t) => {
                let text = self.ty(t)?;
                let function = match self.p.types.get(t) {
                    TypeData::Function { .. } => true,
                    TypeData::Nominal { decl, .. } => {
                        self.p.decls.get(decl).facts.contains(Facts::FUN)
                    }
                    _ => false,
                };
                match function {
                    true => format!("({text})|temper.Null"),
                    false => format!("{text}|temper.Null"),
                }
            }
            // A function's, by its `---@generic` name; a class's, which
            // LuaLS's classes don't take, any.
            TypeData::TypeParam { decl } => {
                let owner = self.p.decls.get(decl).owner;
                match self.p.decls.get(owner).kind {
                    DeclKind::Function if !self.local_types.get() => self.p.name(decl).to_string(),
                    _ => "any".to_string(),
                }
            }
        })
    }

    /// A local's type, in its `---@type`: as [`ty`](Self::ty), but any for
    /// a function's type parameter, whose `---@generic` name LuaLS knows
    /// only in the function's own annotations.
    fn local_ty(&self, ty: TypeId) -> R<String> {
        let saved = self.local_types.replace(true);
        let out = self.ty(ty);
        self.local_types.set(saved);
        out
    }

    fn exported(&self, decl: DeclId) -> bool {
        self.p.decls.get(decl).facts.contains(Facts::EXPORTED)
    }

    /// Translates the module into a chunk; `access` is what its tests use
    /// of it.
    pub fn module(&mut self, module: &Module, access: &TestAccess) -> R<tree::Chunk> {
        let p = self.p;
        self.here = module.decl;
        // Host code, which the chunk inlines, and which may use the runtime.
        let host = host_file(p, module);
        if host.is_some() {
            self.uses_temper = true;
        }
        // Other modules' functions and `let`s, as locals; and in the root
        // module, the library's other modules it doesn't import, so
        // requiring the library runs every module, as in JS and Python,
        // but those that import the root, which can't run before it ends.
        let mut imports = Vec::new();
        let imported = chunk_imports(p, module);
        if module.path.is_empty() {
            let after = importers_of(p, module.decl);
            for other in &p.modules {
                if other.decl != module.decl
                    && !after.contains(&other.decl)
                    && !imported.iter().any(|i| i.from == other.decl)
                {
                    let path = Expr::Str(require_path(p, other.decl));
                    imports.push(Stmt::Expr(Expr::name("require").call(vec![path])));
                }
            }
        }
        for import in imported {
            let path = require_path(p, import.from);
            for d in import.decls {
                if !importable(p, d) {
                    continue;
                }
                let required = Expr::name("require").call(vec![Expr::Str(path.clone())]);
                imports.push(Stmt::Local {
                    name: self.name(d),
                    ty: None,
                    value: Some(required.member(self.key(d))),
                });
            }
        }
        let other_locals = imports.len() + 2 * access.vars.len();
        let Part {
            declared,
            live,
            head,
            lets,
            forward,
            defs,
            init,
        } = self.part(
            module,
            &module.items,
            Some(module.init),
            host,
            other_locals,
            &[],
        )?;

        // The exports, in source order, as Kotlin lists them.
        let exports: Vec<DeclId> = (declared.iter())
            .map(|&(_, d)| d)
            .filter(|&d| self.exported(d))
            .collect();
        let mut tail = Vec::new();
        // What its tests use, under `'$test'`, with a getter and a setter
        // for each `var`.
        let mut test_entries = Vec::new();
        for &d in access.used.iter().filter(|d| !access.vars.contains(d)) {
            test_entries.push((self.name(d), self.access(d)));
        }
        for &d in &access.vars {
            let name = self.name(d);
            let ty = self.ty(p.decls.get(d).ty)?;
            for suffix in ["__get", "__set"] {
                let f = format!("{name}{suffix}");
                if p.decls.ids().any(|o| self.names.get(o) == f) {
                    return not_yet(format!("a test helper named as a declaration, {f}"));
                }
                test_entries.push((f, Expr::name(format!("{name}{suffix}"))));
            }
            tail.push(Stmt::Function {
                local: true,
                name: format!("{name}__get"),
                signature: Signature {
                    params: Vec::new(),
                    result: Some(ty.clone()),
                },
                body: vec![Stmt::Return(Some(self.access(d)))],
            });
            tail.push(Stmt::Function {
                local: true,
                name: format!("{name}__set"),
                signature: Signature {
                    params: vec![("value".into(), ty)],
                    result: None,
                },
                body: vec![Stmt::Assign {
                    target: self.access(d),
                    value: Expr::name("value"),
                }],
            });
        }
        if !tail.is_empty() {
            tail.push(Stmt::Blank);
        }
        let test_table = (!test_entries.is_empty()).then_some(Expr::Table(test_entries));
        if live {
            if let Some(table) = test_table.clone() {
                tail.push(Stmt::Assign {
                    target: Expr::name(EXPORTS).member(TEST_KEY),
                    value: table,
                });
            }
            for &d in &exports {
                if self.fields.get(&d) != Some(&EXPORTS) {
                    tail.push(Stmt::Assign {
                        target: Expr::name(EXPORTS).member(self.key(d)),
                        value: self.access(d),
                    });
                }
            }
            tail.push(Stmt::Return(Some(Expr::name(EXPORTS))));
        } else {
            let mut entries: Vec<(String, Expr)> = (exports.iter())
                .map(|&d| (self.key(d), self.access(d)))
                .collect();
            if let Some(table) = test_table {
                entries.push((TEST_KEY.into(), table));
            }
            tail.push(Stmt::Return(Some(Expr::Table(entries))));
        }

        let mut body = Vec::new();
        if self.uses_temper {
            body.push(Stmt::Local {
                name: "temper".into(),
                ty: None,
                value: Some(Expr::name("require").call(vec![Expr::Str("temper-core".into())])),
            });
        }
        body.extend(self.int64_decls());
        body.extend(imports);
        body.extend(head);
        if !lets.is_empty() || !forward.is_empty() {
            body.push(Stmt::Blank);
        }
        body.extend(lets);
        if !forward.is_empty() {
            body.push(Stmt::Locals(
                forward.iter().map(|&d| self.name(d)).collect(),
            ));
        }
        if let Some(host) = host {
            body.push(Stmt::Blank);
            body.push(Stmt::Raw(
                host.text.trim_end().lines().map(str::to_string).collect(),
            ));
            body.push(Stmt::Blank);
        }
        body.extend(defs);
        if !body.is_empty() {
            body.push(Stmt::Blank);
        }
        body.extend(init);
        if !body.is_empty() && !matches!(body.last(), Some(Stmt::Blank)) {
            body.push(Stmt::Blank);
        }
        body.extend(tail);
        Ok(tree::Chunk { body })
    }

    /// A chunk's declarations and init: a module's items and init block,
    /// or in its tests file, its test items and test init, which
    /// continues it. `host` is the module's host code, which a tests file
    /// has none of, `other_locals` how many locals the chunk declares
    /// besides, and `tests` its tests, which use its `Int64` constants
    /// too.
    fn part(
        &mut self,
        module: &Module,
        items: &[NodeId],
        init: Option<NodeId>,
        host: Option<&HostFile>,
        other_locals: usize,
        tests: &[NodeId],
    ) -> R<Part> {
        let p = self.p;
        let state = module_state(p, module);
        for &item in items.iter().chain(&init) {
            if let Node::ModuleLet { decl } = p.nodes.get(item) {
                if self.type_connection(p.decls.get(decl).ty) == Some(TypeConnection::Erased) {
                    self.erased.insert(decl);
                }
            }
        }
        // The module's own declarations, in source order.
        let declared: Vec<(NodeId, DeclId)> = (items.iter())
            .filter_map(|&item| match p.nodes.get(item) {
                Node::Function { decl, .. }
                | Node::Class { decl, .. }
                | Node::Interface { decl, .. } => Some((item, decl)),
                Node::ModuleLet { decl } if !self.erased.contains(&decl) => Some((item, decl)),
                Node::BodilessFunction { decl, .. } => Some((item, decl)),
                _ => None,
            })
            .collect();
        if host.is_none() {
            if let Some(&(_, decl)) = (declared.iter()).find(|&&(_, d)| uses_host(p, d, NAMES)) {
                if self.here.is_none() {
                    return not_yet(format!(
                        "host code for what only tests reach, {}",
                        p.name(decl)
                    ));
                }
                return not_yet(format!(
                    "{}'s host code, with no {}",
                    p.name(decl),
                    host_path(module)
                ));
            }
        }
        // Module `var`s that functions or members write, and functions
        // that may do what `Effects` doesn't see, through members.
        self.mutable = self.effects.mutable.clone();
        let types: Vec<NodeId> = (declared.iter())
            .filter(|&&(item, _)| {
                matches!(
                    p.nodes.get(item),
                    Node::Class { .. } | Node::Interface { .. }
                )
            })
            .map(|&(item, _)| item)
            .collect();
        for &item in &types {
            self.mutable
                .extend(temper_tmpl::analysis::module_writes(p, item));
        }
        loop {
            let mut changed = false;
            for &(item, decl) in &declared {
                if matches!(p.nodes.get(item), Node::Function { .. })
                    && !self.classy.contains(&decl)
                    && classes::classy(p, item, &self.classy)
                {
                    self.classy.insert(decl);
                    changed = true;
                }
            }
            if !changed {
                break;
            }
        }
        // Exported `var`s that functions write live in the exports table.
        for &(_, decl) in &declared {
            let d = p.decls.get(decl);
            if d.facts.contains(Facts::VAR) && self.exported(decl) && self.mutable.contains(&decl) {
                self.fields.insert(decl, EXPORTS);
            }
        }
        let live = (declared.iter()).any(|(_, d)| self.fields.get(d) == Some(&EXPORTS));
        // `Int64` constants, as locals or one table's fields.
        let host_words = host.as_ref().map(|h| identifiers(&h.text));
        let roots: Vec<NodeId> = (items.iter().copied())
            .chain(init)
            .chain(tests.iter().copied())
            .collect();
        let consts = self.plan_int64s(&roots, &host_words.unwrap_or_default());
        // Past the threshold, those after the first few are a table's.
        let init_locals = init.map_or(0, |i| max_locals(p, i)) + other_locals;
        let locals = declared.len() + init_locals + consts + 3;
        let overflow = locals > MODULE_LOCALS;
        if overflow {
            for &(_, decl) in declared.iter().skip(KEPT_LOCALS) {
                self.fields.entry(decl).or_insert(MODULE_TABLE);
            }
        }
        let kept = declared
            .iter()
            .filter(|(_, d)| !self.fields.contains_key(d))
            .count();
        if kept + init_locals + consts + 3 > MAX_LOCALS {
            return not_yet(format!(
                "a module whose init block has {init_locals} locals alive at once"
            ));
        }

        let mut head = Vec::new();
        if live {
            head.push(Stmt::Local {
                name: EXPORTS.into(),
                ty: None,
                value: Some(Expr::Table(Vec::new())),
            });
        }
        if overflow {
            head.push(Stmt::Local {
                name: MODULE_TABLE.into(),
                ty: None,
                value: Some(Expr::Table(Vec::new())),
            });
        }
        // Module `let`s and `var`s declared before the functions: those
        // functions use, and those members use.
        let mut used = state.used_by_functions.clone();
        for &item in &types {
            used.extend(classes::refs(p, item));
        }
        let mut first: HashSet<DeclId> = HashSet::new();
        let mut lets = Vec::new();
        for &(item, decl) in &declared {
            if !matches!(p.nodes.get(item), Node::ModuleLet { .. })
                || self.fields.contains_key(&decl)
            {
                continue;
            }
            if used.contains(&decl) || !state.initialized_at_top.contains(&decl) {
                first.insert(decl);
                lets.push(Stmt::Local {
                    name: self.name(decl),
                    ty: Some(self.ty(p.decls.get(decl).ty)?),
                    value: None,
                });
            }
        }
        // Functions and types, in source order, but a type's supertypes
        // before it, since making it copies theirs.
        let mut defined: Vec<(NodeId, DeclId)> = Vec::new();
        fn place(
            p: &Program,
            item: (NodeId, DeclId),
            declared: &[(NodeId, DeclId)],
            defined: &mut Vec<(NodeId, DeclId)>,
        ) {
            if defined.contains(&item) {
                return;
            }
            for &s in p.decls.supers(item.1) {
                let up = temper_tmpl::analysis::nominal_decl(p, s);
                if let Some(&found) = declared.iter().find(|&&(_, d)| Some(d) == up) {
                    place(p, found, declared, defined);
                }
            }
            defined.push(item);
        }
        for &(item, decl) in &declared {
            if !matches!(p.nodes.get(item), Node::ModuleLet { .. }) {
                place(p, (item, decl), &declared, &mut defined);
            }
        }
        // Those referred to by one before them.
        let mut forward = Vec::new();
        let mut seen: HashSet<DeclId> = HashSet::new();
        for &(item, decl) in &defined {
            seen.insert(decl);
            for d in classes::refs(p, item) {
                if !seen.contains(&d)
                    && !self.fields.contains_key(&d)
                    && defined.iter().any(|&(_, f)| f == d)
                    && !forward.contains(&d)
                {
                    forward.push(d);
                }
            }
        }
        // Host code reaches the module's declarations by name, as
        // Kotlin's, which declares them all first: those it names are
        // declared before it.
        if let Some(host) = host {
            let words = identifiers(&host.text);
            for &(item, decl) in &declared {
                if self.fields.contains_key(&decl) || !words.contains(self.name(decl).as_str()) {
                    continue;
                }
                if matches!(p.nodes.get(item), Node::ModuleLet { .. }) {
                    if first.insert(decl) {
                        lets.push(Stmt::Local {
                            name: self.name(decl),
                            ty: Some(self.ty(p.decls.get(decl).ty)?),
                            value: None,
                        });
                    }
                } else if !forward.contains(&decl) {
                    forward.push(decl);
                }
            }
        }
        let mut defs = Vec::new();
        for &(item, decl) in &defined {
            let local = !forward.contains(&decl) && !self.fields.contains_key(&decl);
            let hosted = uses_host(p, decl, NAMES);
            match p.nodes.get(item) {
                Node::Function { .. } | Node::BodilessFunction { .. } if hosted => {
                    defs.push(self.host_wrapper(item, local)?);
                }
                // A connected class that host code defines is a name for
                // it, as Kotlin's: `Counter = _connected.Counter`.
                Node::Class { .. } if hosted => {
                    let value = Expr::name(HOST).member(p.name(decl));
                    defs.push(if local {
                        Stmt::Local {
                            name: self.name(decl),
                            ty: None,
                            value: Some(value),
                        }
                    } else {
                        Stmt::Assign {
                            target: self.access(decl),
                            value,
                        }
                    });
                }
                Node::Function { .. } => defs.push(self.function(item, local)?),
                // std's, which the runtime has (`temper.sendrequest`).
                Node::BodilessFunction { .. } => {}
                _ => {
                    defs.push(Stmt::Blank);
                    defs.extend(self.type_def(item, local)?);
                    defs.push(Stmt::Blank);
                }
            }
        }
        // `with_tostring`, if a class uses it.
        let shown = defs.iter().any(|s| {
            matches!(s, Stmt::Expr(Expr::Call(f, _))
                if **f == Expr::name(classes::WITH_TOSTRING))
        });
        if shown {
            defs.insert(0, classes::with_tostring_def());
        }

        // The init block.
        let mut run = Vec::new();
        let stmts: Vec<NodeId> = match init.map(|i| (i, p.nodes.get(i))) {
            Some((_, Node::Block { stmts })) => p.nodes.list(stmts).to_vec(),
            Some((i, _)) => vec![i],
            None => Vec::new(),
        };
        if let Some(init) = init {
            self.begin(init);
        }
        for s in stmts {
            match p.nodes.get(s) {
                Node::InitLet { target, value }
                    if !self.erased.contains(&target)
                        && !first.contains(&target)
                        && !self.fields.contains_key(&target) =>
                {
                    self.check_hoisting(s)?;
                    if matches!(p.nodes.get(value), Node::OrElse { .. }) {
                        run.push(Stmt::Local {
                            name: self.name(target),
                            ty: Some(self.ty(p.decls.get(target).ty)?),
                            value: None,
                        });
                        let t = self.or_else_into(Expr::name(self.name(target)), value)?;
                        run.extend(t);
                        continue;
                    }
                    let (pre, value) = self.parts(value)?;
                    run.extend(pre);
                    run.push(Stmt::Local {
                        name: self.name(target),
                        ty: None,
                        value: Some(value),
                    });
                }
                _ => self.stmt(s, &mut run)?,
            }
        }
        Ok(Part {
            declared,
            live,
            head,
            lets,
            forward,
            defs,
            init: reachable(run),
        })
    }

    /// A module's tests, as a LuaUnit file, as Kotlin's: a function of
    /// the global `Test_` per test, each running `temper.test`, then
    /// LuaUnit run on them. Lua's std `Test` is the runtime's, which
    /// `temper.test` makes, so the tests need nothing of std's. `None` if
    /// it has none.
    pub fn tests(&mut self, module: &Module) -> R<Option<tree::Chunk>> {
        if module.tests.is_empty() {
            return Ok(None);
        }
        let p = self.p;
        self.here = DeclId::NONE;
        self.tested = module.decl;
        let parts = temper_tmpl::analysis::test_parts(p, module);
        if parts.assign_init_locals(p) {
            return not_yet("tests that assign a local of their module's init block");
        }
        self.test_own = parts.own;
        self.uses_temper = true;
        let imported = temper_tmpl::analysis::test_imports(p, module);
        let roots: Vec<NodeId> = module.test_roots().collect();
        let imported = classes::with_casts(p, module.decl, &roots, imported);
        // `temper`, `tested`, `luaunit`, and the imports.
        let other_locals = 3 + imported.iter().map(|i| i.decls.len()).sum::<usize>();
        let part = self.part(
            module,
            &module.test_items,
            module.test_init,
            None,
            other_locals,
            &module.tests,
        )?;
        let mut used = Vec::new();
        let mut tests = Vec::new();
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
            let text = p.strings.get(name).to_string();
            self.begin(test);
            let mut stmts = self.stmts(body)?;
            if stmts.last() == Some(&Stmt::Return(None)) {
                stmts.pop();
            }
            tests.push(Stmt::Test {
                method: format!("Test_.{}", test_method_name(&text, &mut used)),
                name: text,
                test: self.name(param),
                body: stmts,
            });
        }
        let require = |path: String| Expr::name("require").call(vec![Expr::Str(path)]);
        let mut body = vec![Stmt::Local {
            name: "temper".into(),
            ty: None,
            value: Some(require("temper-core".into())),
        }];
        body.extend(self.int64_decls());
        body.push(Stmt::Local {
            name: "tested".into(),
            ty: None,
            value: Some(require(require_path(p, module.decl)).member(TEST_KEY)),
        });
        for import in imported {
            let path = require_path(p, import.from);
            for d in import.decls {
                if !importable(p, d) {
                    continue;
                }
                body.push(Stmt::Local {
                    name: self.name(d),
                    ty: None,
                    value: Some(require(path.clone()).member(self.key(d))),
                });
            }
        }
        body.push(Stmt::Local {
            name: "luaunit".into(),
            ty: None,
            value: Some(require("luaunit".into())),
        });
        body.push(Stmt::Assign {
            target: Expr::name("luaunit").member("FAILURE_PREFIX"),
            value: Expr::name("temper").member("test_failure_prefix"),
        });
        body.push(Stmt::Blank);
        // What only tests reach, as a module's chunk declares it.
        let Part {
            head,
            lets,
            forward,
            defs,
            init,
            ..
        } = part;
        body.extend(head);
        body.extend(lets);
        if !forward.is_empty() {
            body.push(Stmt::Locals(
                forward.iter().map(|&d| self.name(d)).collect(),
            ));
        }
        body.extend(defs);
        if !init.is_empty() {
            body.push(Stmt::Blank);
            body.extend(init);
            body.push(Stmt::Blank);
        } else if !matches!(body.last(), Some(Stmt::Blank)) {
            body.push(Stmt::Blank);
        }
        body.push(Stmt::Assign {
            target: Expr::name("Test_"),
            value: Expr::Table(Vec::new()),
        });
        body.extend(tests);
        // `luaunit.LuaUnit.run('--pattern', '^Test_%.', ...)`: the
        // tests file's own arguments after LuaUnit's pattern, as Kotlin's.
        let args = vec![
            Expr::Str("--pattern".into()),
            Expr::Str("^Test_%.".into()),
            Expr::name("..."),
        ];
        body.push(Stmt::Expr(
            Expr::name("luaunit")
                .member("LuaUnit")
                .member("run")
                .call(args),
        ));
        Ok(Some(tree::Chunk { body }))
    }

    fn function(&mut self, item: NodeId, local: bool) -> R<Stmt> {
        let p = self.p;
        let Node::Function { decl, params, body } = p.nodes.get(item) else {
            return not_yet("a function that isn't one");
        };
        let name = match self.access(decl) {
            Expr::Member(table, field) => format!("{}.{field}", tree::expr(&table, 0)),
            e => tree::expr(&e, 0),
        };
        let result = match p.signature(decl) {
            Some(sig) if sig.result != TypeId::VOID => Some(sig.result),
            _ => None,
        };
        self.function_def(item, name, local, None, p.nodes.list(params), body, result)
    }

    /// A function, method, getter, setter, or constructor, by the name it
    /// defines; a member's first parameter is `this`, of its type's class.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn function_def(
        &mut self,
        item: NodeId,
        name: String,
        local: bool,
        this: Option<&str>,
        params: &[NodeId],
        body: NodeId,
        result: Option<TypeId>,
    ) -> R<Stmt> {
        let p = self.p;
        let ps = self.signature_params(item, this, params)?;
        let alive = ps.len() + max_locals(p, body);
        if alive > MAX_LOCALS {
            return not_yet(format!(
                "a function with {alive} locals alive at once, past Lua's {MAX_LOCALS}"
            ));
        }
        let result = match result {
            Some(ty) => Some(self.ty(ty)?),
            None => None,
        };
        self.begin(item);
        self.returns = result.is_some();
        let stmts = self.stmts(body);
        self.returns = false;
        let mut stmts = stmts?;
        // A `return` without a value that ends a function says nothing.
        if result.is_none() && stmts.last() == Some(&Stmt::Return(None)) {
            stmts.pop();
        }
        Ok(Stmt::Function {
            local,
            name,
            signature: Signature { params: ps, result },
            body: stmts,
        })
    }

    /// The type parameters of functions that a type names, each once.
    fn function_type_params(&self, ty: TypeId, out: &mut Vec<DeclId>) {
        let p = self.p;
        match p.types.get(ty) {
            TypeData::TypeParam { decl } => {
                let owner = p.decls.get(decl).owner;
                if p.decls.get(owner).kind == DeclKind::Function && !out.contains(&decl) {
                    out.push(decl);
                }
            }
            TypeData::Nullable(t) | TypeData::Never(t) => self.function_type_params(t, out),
            TypeData::Function { .. } => {
                if let Some(sig) = p.types.signature(ty) {
                    for &t in sig.params.iter().chain([&sig.result]) {
                        self.function_type_params(t, out);
                    }
                }
            }
            TypeData::Nominal { .. } => {
                for &t in p.types.args(ty) {
                    self.function_type_params(t, out);
                }
            }
            TypeData::Scalar(_) => {}
        }
    }

    /// A function's parameters, each with its LuaCATS type, after a
    /// generic function's type parameters and a member's `this`.
    fn signature_params(
        &mut self,
        item: NodeId,
        this: Option<&str>,
        params: &[NodeId],
    ) -> R<Vec<(String, String)>> {
        let p = self.p;
        let mut ps = Vec::new();
        // A generic function's type parameters, for LuaLS; and those of a
        // function around it that a nested one's signature names, which
        // LuaLS doesn't see inside it otherwise.
        let decl = match p.nodes.get(item) {
            Node::Function { decl, .. } | Node::FunctionLiteral { decl, .. } => Some(decl),
            _ => None,
        };
        if let Some(decl) = decl {
            let mut generics = Vec::new();
            if p.decls.get(decl).kind == DeclKind::Function {
                generics.extend_from_slice(p.decls.type_params(decl));
            }
            if generics.is_empty() {
                self.function_type_params(p.decls.get(decl).ty, &mut generics);
            }
            for t in generics {
                // With its bound, `IT: lib.I`, so LuaLS knows its members.
                let mut bounds = Vec::new();
                for &b in p.decls.supers(t) {
                    let named = temper_tmpl::analysis::nominal_decl(p, b);
                    if named.is_some_and(|d| self.qname(d) != "core.type MapKey") {
                        bounds.push(self.ty(b)?);
                    }
                }
                let text = match bounds.as_slice() {
                    [one] => format!("{}: {one}", p.name(t)),
                    _ => p.name(t).to_string(),
                };
                ps.push(("@generic".to_string(), text));
            }
        }
        if let Some(this) = this {
            ps.push(("this".to_string(), this.to_string()));
        }
        let trailing = (params.iter().rev())
            .take_while(|&&param| {
                matches!(p.nodes.get(param), Node::Param { decl }
                    if p.decls.get(decl).facts.contains(Facts::OPTIONAL))
            })
            .count();
        for (i, &param) in params.iter().enumerate() {
            if let Node::Param { decl } = p.nodes.get(param) {
                // An optional one after the last required one may be left
                // out: `x?`.
                let optional = if i + trailing >= params.len() {
                    "?"
                } else {
                    ""
                };
                let name = format!("{}{optional}", self.name(decl));
                ps.push((name, self.ty(p.decls.get(decl).ty)?));
            }
        }
        Ok(ps)
    }

    /// A connected function whose host code runs instead of its Temper
    /// body, as Kotlin's: a nullable argument left out (`nil`) becomes
    /// `temper.null`, its parameters' defaults apply, and it calls the
    /// host's function of its Temper name (`_connected.sum(i, j,
    /// bonus)`), passing each parameter, defaulted.
    fn host_wrapper(&mut self, item: NodeId, local: bool) -> R<Stmt> {
        let p = self.p;
        let (decl, params, body) = match p.nodes.get(item) {
            Node::Function { decl, params, body } => (decl, params, Some(body)),
            Node::BodilessFunction { decl, params } => (decl, params, None),
            _ => return not_yet("host code for what isn't a function"),
        };
        let name = match self.access(decl) {
            Expr::Member(table, field) => format!("{}.{field}", tree::expr(&table, 0)),
            e => tree::expr(&e, 0),
        };
        let params = p.nodes.list(params).to_vec();
        let ps = self.signature_params(item, None, &params)?;
        let result = match p.signature(decl) {
            Some(sig) if sig.result != TypeId::VOID => Some(self.ty(sig.result)?),
            _ => None,
        };
        let defaults = body.map_or_else(Vec::new, |b| analysis::defaults(p, b));
        self.begin(item);
        let mut stmts = Vec::new();
        let mut args = Vec::new();
        for &param in &params {
            let Node::Param { decl: param } = p.nodes.get(param) else {
                continue;
            };
            let passed = (defaults.iter())
                .find(|d| d.param == param)
                .map_or(param, |d| d.local);
            let name = Expr::name(self.name(param));
            if p.types.is_nullable(p.decls.get(passed).ty) {
                self.uses_temper = true;
                stmts.push(Stmt::If {
                    cond: Expr::bin(Bin::Eq, name.clone(), Expr::Nil),
                    then: vec![Stmt::Assign {
                        target: name.clone(),
                        value: ops::temper().member("null"),
                    }],
                    els: Vec::new(),
                });
            }
            args.push(name);
        }
        for d in &defaults {
            self.stmt(d.stmt, &mut stmts)?;
        }
        let call = Expr::name(HOST).member(p.name(decl)).call(args);
        stmts.push(match result {
            Some(_) => Stmt::Return(Some(call)),
            None => Stmt::Expr(call),
        });
        Ok(Stmt::Function {
            local,
            name,
            signature: Signature { params: ps, result },
            body: stmts,
        })
    }

    /// A function type as LuaCATS writes it: `fun(arg0: integer):
    /// string`, as Kotlin's.
    fn function_type(&self, ty: TypeId) -> R<String> {
        let Some(sig) = temper_tmpl::analysis::fun_signature(self.p, ty) else {
            return not_yet("a function type of another type");
        };
        let Some(s) = self.p.types.signature(sig) else {
            return not_yet("a function type without a signature");
        };
        let mut texts = Vec::new();
        for (i, &t) in s.params.iter().enumerate() {
            texts.push(format!("arg{i}: {}", self.ty(t)?));
        }
        Ok(if s.result == TypeId::VOID {
            format!("fun({})", texts.join(", "))
        } else {
            format!("fun({}): {}", texts.join(", "), self.ty(s.result)?)
        })
    }

    /// A function literal: on one line, `function(x) return f(f(x))
    /// end`, where its body is one `return` of an expression (or for
    /// `Void`, one call) that needs no statements; else a `local
    /// function` declared before the statement it's in, named `name` if
    /// given, as Kotlin's.
    pub(crate) fn literal(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: NodeId,
        name: Option<String>,
    ) -> R<Expr> {
        let p = self.p;
        let stmts = match p.nodes.get(body) {
            Node::Block { stmts } => p.nodes.list(stmts).to_vec(),
            _ => Vec::new(),
        };
        let void = p.signature(decl).is_some_and(|s| s.result == TypeId::VOID);
        // The function around it, whose state its own replaces.
        let saved = (
            std::mem::take(&mut self.loops),
            std::mem::take(&mut self.labels),
            std::mem::take(&mut self.tries),
            self.returns,
        );
        let result = (|| -> R<Expr> {
            // A generator: a function run as a coroutine, by the factory
            // `temper.adapt_generator_fn` makes of it, as Kotlin's.
            if p.decls.get(decl).facts.contains(Facts::GENERATOR) {
                let local = name.clone().unwrap_or_else(|| self.name(decl));
                let params = p.nodes.list(params);
                let def = self.function_def(id, local.clone(), true, None, params, body, None)?;
                self.pre.push(def);
                self.uses_temper = true;
                let adapt = ops::temper().method("adapt_generator_fn", vec![Expr::name(local)]);
                return Ok(adapt);
            }
            if let ([only], None) = (stmts.as_slice(), &name) {
                let value = match p.nodes.get(*only) {
                    Node::Return { value: Some(value) } => Some(value),
                    Node::ExprStatement { expr } if void => Some(expr),
                    _ => None,
                };
                if let Some(value) = value {
                    let outer = std::mem::take(&mut self.pre);
                    let e = self.expr(value);
                    let pre = std::mem::replace(&mut self.pre, outer);
                    let e = e?;
                    if pre.is_empty() && (!void || matches!(e, Expr::Call(..) | Expr::Invoke(..))) {
                        let names = (p.nodes.list(params).iter())
                            .filter_map(|&param| match p.nodes.get(param) {
                                Node::Param { decl } => Some(self.name(decl)),
                                _ => None,
                            })
                            .collect();
                        return Ok(Expr::Function {
                            params: names,
                            body: Box::new(e),
                            returns: !void,
                        });
                    }
                }
            }
            let local = name.unwrap_or_else(|| self.name(decl));
            let result = p
                .signature(decl)
                .map(|s| s.result)
                .filter(|&r| r != TypeId::VOID);
            let def = self.function_def(
                id,
                local.clone(),
                true,
                None,
                p.nodes.list(params),
                body,
                result,
            )?;
            self.pre.push(def);
            Ok(Expr::name(local))
        })();
        let (loops, labels, tries, returns) = saved;
        self.loops = loops;
        self.labels = labels;
        self.tries = tries;
        self.returns = returns;
        result
    }

    /// Starts translating a function or the init block: plans which loops
    /// need labels. A `continue` of the innermost loop goes to
    /// `continue`, ending its body; a jump from an inner loop, to a label
    /// named for the loop it leaves or continues: its source name, or for
    /// a compiler-made one, `outer`, numbered only if another label in the
    /// function has it.
    fn begin(&mut self, scope: NodeId) {
        /// Jumps to each loop: (loop, whether a `break`, whether from an
        /// inner loop).
        fn visit(
            p: &Program,
            id: NodeId,
            loops: &mut Vec<DeclId>,
            jumps: &mut Vec<(DeclId, bool, bool)>,
        ) {
            match p.nodes.get(id) {
                Node::While { label, .. } | Node::DoWhile { label, .. } => {
                    loops.push(label);
                    for &kid in p.nodes.kids(id) {
                        visit(p, kid, loops, jumps);
                    }
                    loops.pop();
                }
                Node::Break { label } | Node::Continue { label } => {
                    let brk = matches!(p.nodes.get(id), Node::Break { .. });
                    let inner = loops.last() != Some(&label);
                    if (inner || !brk) && !jumps.contains(&(label, brk, inner)) {
                        jumps.push((label, brk, inner));
                    }
                }
                _ => {
                    for &kid in p.nodes.kids(id) {
                        visit(p, kid, loops, jumps);
                    }
                }
            }
        }
        let p = self.p;
        self.temps = 0;
        let mut jumps = Vec::new();
        visit(p, scope, &mut Vec::new(), &mut jumps);
        self.loops.clear();
        self.labels.clear();
        self.tries.clear();
        let mut taken: HashSet<String> = HashSet::new();
        for &(l, brk, inner) in &jumps {
            let labels = self.labels.entry(l).or_default();
            if brk || inner || labels.next.is_some() {
                continue;
            }
            // A `continue` only from the loop itself.
            if !jumps.contains(&(l, false, true)) {
                labels.next = Some("continue".into());
            }
        }
        for (l, brk, inner) in jumps {
            if !inner {
                continue;
            }
            let d = p.decls.get(l);
            let base = if d.facts.contains(Facts::COMPILER_MADE) || p.name(l).is_empty() {
                "outer".to_string()
            } else {
                p.name(l)
                    .chars()
                    .map(|c| if c.is_ascii_alphanumeric() { c } else { '_' })
                    .collect()
            };
            let base = format!("{}_{base}", if brk { "break" } else { "continue" });
            let name = (1..)
                .map(|n| match n {
                    1 => base.clone(),
                    n => format!("{base}{n}"),
                })
                .find(|n| !taken.contains(n))
                .unwrap_or_default();
            taken.insert(name.clone());
            let labels = self.labels.entry(l).or_default();
            if brk {
                labels.end = Some(name);
            } else {
                labels.next = Some(name);
            }
        }
    }

    fn stmts(&mut self, id: NodeId) -> R<Vec<Stmt>> {
        let mut out = Vec::new();
        match self.p.nodes.get(id) {
            Node::Block { stmts } => {
                let stmts = self.p.nodes.list(stmts);
                let forward = self.forward_locals(stmts)?;
                for &s in stmts {
                    if let Some(locals) = forward.get(&s) {
                        out.extend(locals.iter().cloned());
                    }
                    self.stmt(s, &mut out)?;
                }
            }
            _ => self.stmt(id, &mut out)?,
        }
        Ok(reachable(out))
    }

    /// The `local`s that declare, before a group of local functions, the
    /// ones an earlier one calls (`local putter`, then `function
    /// putter(…)`), by the group's first `let`; `local function` lets
    /// one call itself.
    fn forward_locals(&mut self, stmts: &[NodeId]) -> R<HashMap<NodeId, Vec<Stmt>>> {
        let p = self.p;
        let mut found = HashMap::new();
        for group in temper_tmpl::analysis::function_groups(p, stmts) {
            let mut locals = Vec::new();
            for (k, &m) in group.members.iter().enumerate() {
                let called_before = group.lets[..k].iter().any(|&l| {
                    temper_tmpl::analysis::function_let(p, l)
                        .is_some_and(|(_, lit)| p.decls.captures(lit).contains(&m))
                });
                if called_before {
                    self.forward.insert(m);
                    locals.push(Stmt::Local {
                        name: self.name(m),
                        ty: Some(self.local_ty(p.decls.get(m).ty)?),
                        value: None,
                    });
                }
            }
            if !locals.is_empty() {
                found.insert(group.lets[0], locals);
            }
        }
        Ok(found)
    }

    fn loop_body(&mut self, label: DeclId, body: NodeId) -> R<Vec<Stmt>> {
        self.loops.push(label);
        let body = self.stmts(body);
        self.loops.pop();
        body
    }

    /// A statement, after what its expressions hoist ([`Self::hoist`]).
    fn stmt(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        self.check_hoisting(id)?;
        let outer = std::mem::take(&mut self.pre);
        let mut here = Vec::new();
        let done = self.stmt_here(id, &mut here);
        let pre = std::mem::replace(&mut self.pre, outer);
        done?;
        out.extend(pre);
        out.extend(here);
        Ok(())
    }

    fn stmt_here(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            // A block inside a block keeps its scope, if it declares
            // anything.
            Node::Block { stmts }
                if !(p.nodes.list(stmts).iter())
                    .any(|&s| matches!(p.nodes.get(s), Node::Let { .. })) =>
            {
                out.extend(self.stmts(id)?)
            }
            Node::Block { .. } => out.push(Stmt::Do(self.stmts(id)?)),
            Node::Let {
                local,
                init: Some(init),
            } if matches!(p.nodes.get(init), Node::OrElse { .. }) => {
                let name = self.name(local);
                out.push(Stmt::Local {
                    name: name.clone(),
                    ty: Some(self.local_ty(p.decls.get(local).ty)?),
                    value: None,
                });
                let t = self.or_else_into(Expr::name(name), init)?;
                out.extend(t);
            }
            // A local function with statements: `local function f(…)`.
            Node::Let {
                local,
                init: Some(init),
            } if !p.decls.get(local).facts.contains(Facts::VAR)
                && matches!(p.nodes.get(init), Node::FunctionLiteral { decl, .. }
                    if !p.decls.get(decl).facts.contains(Facts::GENERATOR)) =>
            {
                let Node::FunctionLiteral { decl, params, body } = p.nodes.get(init) else {
                    unreachable!()
                };
                let name = self.name(local);
                self.literal(init, decl, params, body, Some(name))?;
                if self.forward.contains(&local) {
                    if let Some(Stmt::Function { local, .. }) = self.pre.last_mut() {
                        *local = false;
                    }
                }
                out.append(&mut self.pre);
            }
            // A parameter's default, in place: `if temper.is_null(x) then x
            // = d end`, then cast for LuaLS; for null, `if x == nil then x
            // = temper.null end`, so a field it's stored in reads as null,
            // not as a missing key, which a class's table reports.
            Node::Let {
                local,
                init: Some(init),
            } if param_default(p, init).is_some() => {
                let (param, value) = param_default(p, init).unwrap();
                self.defaulted.insert(local, param);
                if matches!(p.nodes.get(value), Node::Null {}) {
                    let name = Expr::name(self.name(param));
                    self.uses_temper = true;
                    out.push(Stmt::If {
                        cond: Expr::bin(Bin::Eq, name.clone(), Expr::Nil),
                        then: vec![Stmt::Assign {
                            target: name,
                            value: ops::temper().member("null"),
                        }],
                        els: Vec::new(),
                    });
                } else {
                    let name = Expr::name(self.name(param));
                    let mut then = Vec::new();
                    let value = self.expr(value)?;
                    then.append(&mut self.pre);
                    then.push(Stmt::Assign {
                        target: name.clone(),
                        value,
                    });
                    self.uses_temper = true;
                    out.push(Stmt::If {
                        cond: ops::temper().method("is_null", vec![name]),
                        then,
                        els: Vec::new(),
                    });
                    // Always cast, nullable types too: LuaLS doesn't see
                    // `is_null` narrow, so the optional parameter (`?`)
                    // would still read as possibly nil.
                    let ty = p.decls.get(local).ty;
                    out.push(Stmt::Cast {
                        name: self.name(param),
                        ty: self.ty(ty)?,
                    });
                }
            }
            Node::Let { local, init } => {
                let name = self.name(local);
                // A nullable one is typed, so LuaLS doesn't take it as
                // only what it starts as, as `temper.null`.
                let ty = p.decls.get(local).ty;
                let typed =
                    p.types.is_nullable(ty) && p.decls.get(local).facts.contains(Facts::VAR);
                out.push(match init {
                    Some(init) => Stmt::Local {
                        name,
                        ty: if typed { Some(self.ty(ty)?) } else { None },
                        value: Some(self.expr(init)?),
                    },
                    None => Stmt::Local {
                        name,
                        ty: Some(self.local_ty(p.decls.get(local).ty)?),
                        value: None,
                    },
                });
            }
            Node::SetModuleLet { target, value }
                if home(p, target) == Some(self.tested) && !self.test_own.contains(&target) =>
            {
                let value = self.expr(value)?;
                let setter = format!("{}__set", self.name(target));
                out.push(Stmt::Expr(Expr::name("tested").method(setter, vec![value])));
            }
            Node::Assign {
                local: target,
                value,
            }
            | Node::SetModuleLet { target, value }
            | Node::InitLet { target, value } => {
                if self.erased.contains(&target) {
                    return Ok(());
                }
                if matches!(p.nodes.get(value), Node::OrElse { .. }) {
                    let target = self.access(target);
                    let t = self.or_else_into(target, value)?;
                    out.extend(t);
                    return Ok(());
                }
                // `bubble()` and `panic()` give nothing to assign.
                if matches!(p.nodes.get(value), Node::Bubble { .. } | Node::Panic {}) {
                    let target = self.access(target);
                    out.extend(self.assigning(&target, value)?);
                    return Ok(());
                }
                let value = self.expr(value)?;
                let target = self.access(target);
                // `x = x` does nothing.
                if value != target {
                    out.push(Stmt::Assign { target, value });
                }
            }
            Node::If { cond, then, els } => {
                let cond = self.expr(cond)?;
                let then = self.stmts(then)?;
                let els = match els {
                    Some(els) => self.stmts(els)?,
                    None => Vec::new(),
                };
                out.push(Stmt::If { cond, then, els });
            }
            Node::While { label, cond, body } => {
                let cond = self.expr(cond)?;
                let body = self.loop_body(label, body)?;
                let labels = self.labels.get(&label).cloned().unwrap_or_default();
                out.push(Stmt::While {
                    cond,
                    body,
                    next: labels.next,
                });
                out.extend(labels.end.map(Stmt::Label));
            }
            Node::DoWhile { label, body, cond } => {
                let body = self.loop_body(label, body)?;
                let cond = self.expr(cond)?.negate();
                let labels = self.labels.get(&label).cloned().unwrap_or_default();
                out.push(Stmt::Repeat {
                    body,
                    cond,
                    next: labels.next,
                });
                out.extend(labels.end.map(Stmt::Label));
            }
            Node::Break { label } | Node::Continue { label } => {
                let brk = matches!(p.nodes.get(id), Node::Break { .. });
                out.push(self.jump(label, brk)?);
            }
            Node::Return { value } => match value.map(|v| (v, p.nodes.get(v))) {
                None | Some((_, Node::VoidLit {})) => out.push(self.return_stmt(None)),
                Some((_, Node::Error { .. } | Node::Bubble { .. })) => out.push(self.bubble_stmt()),
                // `error("panic")` alone, which LuaLS knows doesn't return,
                // where `return error(…)` returns its `nil` to it.
                Some((v, Node::Panic {})) => {
                    let e = self.expr(v)?;
                    out.push(Stmt::Expr(e));
                }
                Some((v, _)) => {
                    let v = self.expr(v)?;
                    out.push(self.return_stmt(Some(v)));
                }
            },
            Node::ExprStatement { expr } if matches!(p.nodes.get(expr), Node::Error { .. }) => {
                out.push(self.bubble_stmt());
            }
            Node::ExprStatement { expr } if matches!(p.nodes.get(expr), Node::OrElse { .. }) => {
                let t = self.or_else_stmt(expr)?;
                out.extend(t);
            }
            Node::ExprStatement { expr }
                if matches!(p.nodes.get(expr), Node::Bubble { .. } | Node::Panic {}) =>
            {
                let panics = matches!(p.nodes.get(expr), Node::Panic {});
                let e = self.expr(expr)?;
                out.push(match e {
                    // LuaLS knows `error(…)` alone doesn't return.
                    e if self.returns && !panics => Stmt::Return(Some(e)),
                    e => Stmt::Expr(e),
                });
            }
            Node::ExprStatement { expr } if self.setter_stmt_of(expr) => {
                out.extend(self.setter_stmt(expr)?);
            }
            Node::SetField {
                field,
                object,
                value,
            } => {
                let target = self.expr(object)?.member(self.field_key(field));
                let value = self.expr(value)?;
                out.push(Stmt::Assign { target, value });
            }
            Node::InitStatic { target, value } => {
                let owner = classes::owner_type(p, target).unwrap_or(target);
                let target = self.type_ref(owner).member(self.static_key(target));
                let value = self.expr(value)?;
                out.push(Stmt::Assign { target, value });
            }
            Node::ExprStatement { expr } => {
                let e = match self.expr(expr)? {
                    // A table of one call's results, unused, as a
                    // generator's step: the call.
                    Expr::Array(items) if items.len() == 1 && items[0].is_statement() => {
                        items.into_iter().next().unwrap_or(Expr::Nil)
                    }
                    e => e,
                };
                if e.is_statement() {
                    out.push(Stmt::Expr(e));
                } else {
                    // Lua takes only calls as statements.
                    out.push(Stmt::Local {
                        name: "_".into(),
                        ty: None,
                        value: Some(e),
                    });
                }
            }
            Node::Yield { value } => {
                self.uses_temper = true;
                let value = match value {
                    Some(v) => vec![self.expr(v)?],
                    None => Vec::new(),
                };
                out.push(Stmt::Expr(ops::temper().method("yield", value)));
            }
            other => return not_yet(format!("a {} statement", other.kind().name())),
        }
        Ok(())
    }

    /// Whether an expression statement is a call of a setter of the
    /// program's own.
    fn setter_stmt_of(&self, expr: NodeId) -> bool {
        let p = self.p;
        matches!(p.nodes.get(expr), Node::CallMethod { method, .. }
            if !by_connection(p, method, crate::connected::connects_std) && p.decls.get(method).kind == DeclKind::Setter)
    }

    /// A `break` or a `continue` of the loop `label`: Lua's `break` for
    /// the innermost loop's, a `goto` to the label [`Self::begin`] planned
    /// otherwise, or out of an `orelse` body the loop encloses, a signal
    /// ([`TryBody`]).
    fn jump(&mut self, label: DeclId, brk: bool) -> R<Stmt> {
        let target = |me: &Self| {
            let labels = me.labels.get(&label);
            match brk {
                true => labels.and_then(|l| l.end.clone()),
                false => labels.and_then(|l| l.next.clone()),
            }
        };
        let outside = (self.tries.last())
            .map(|t| t.loops)
            .filter(|&n| !self.loops[n..].contains(&label));
        if let Some(n) = outside {
            let name = match brk && self.loops[..n].last() == Some(&label) {
                true => "break".to_string(),
                false => match target(self) {
                    Some(l) => l,
                    None => return not_yet("a jump to a loop that doesn't enclose it"),
                },
            };
            let jump = Jump::Loop {
                label,
                brk,
                name: name.clone(),
            };
            let t = self.tries.last_mut().unwrap();
            if !t.jumps.contains(&jump) {
                t.jumps.push(jump);
            }
            return Ok(Stmt::Returns(vec![
                Expr::Str(name),
                Expr::Str("flow".into()),
            ]));
        }
        if brk && self.loops.last() == Some(&label) {
            return Ok(Stmt::Break);
        }
        match target(self) {
            Some(l) => Ok(Stmt::Goto(l)),
            None => not_yet("a jump to a loop that doesn't enclose it"),
        }
    }

    /// A `return`, or out of an `orelse` body, its signal ([`TryBody`]).
    fn return_stmt(&mut self, value: Option<Expr>) -> Stmt {
        match self.tries.last_mut() {
            Some(t) => {
                if !t.jumps.contains(&Jump::Return) {
                    t.jumps.push(Jump::Return);
                }
                Stmt::Returns(vec![value.unwrap_or(Expr::Nil), Expr::Str("return".into())])
            }
            None => Stmt::Return(value),
        }
    }

    fn bubble(&mut self) -> Expr {
        self.uses_temper = true;
        ops::temper().method("bubble", Vec::new())
    }

    /// A bubble as a statement: `return temper.bubble()` in a function
    /// with a result, which never returns, but LuaLS then sees every path
    /// end in a `return`; elsewhere, the call.
    fn bubble_stmt(&mut self) -> Stmt {
        let bubble = self.bubble();
        if self.returns {
            Stmt::Return(Some(bubble))
        } else {
            Stmt::Expr(bubble)
        }
    }

    /// A call of a connected declaration, by the connection table; `args`
    /// has a method's receiver first.
    fn connected(&mut self, decl: DeclId, args: &[NodeId]) -> R<Expr> {
        let qname = self.qname(decl);
        let Some(connection) = connected::lookup(&qname) else {
            // std's members, which Lua connects all of, are the runtime's
            // functions named for their keys, as Kotlin's
            // (`std/testing.type Test.assert()` is `temper.test_assert`).
            if let Some(name) = connected::std_helper(&qname) {
                let args = self.args(args)?;
                self.uses_temper = true;
                return Ok(ops::temper().method(name, args));
            }
            return not_yet(format!("a connection for {qname}"));
        };
        match connection {
            Connection::Math(name) => {
                let args = self.args(args)?;
                Ok(Expr::name("math").method(name, args))
            }
            Connection::Helper(name) => {
                let mut args = args.to_vec();
                while args
                    .last()
                    .is_some_and(|&a| matches!(self.p.nodes.get(a), Node::Omitted {}))
                {
                    args.pop();
                }
                let args = self.args(&args)?;
                self.uses_temper = true;
                Ok(ops::temper().method(name, args))
            }
            Connection::Same => match args {
                [value] => self.expr(*value),
                _ => not_yet(format!("{qname} with arguments")),
            },
            Connection::Translate(f) => f(self, args),
            Connection::Erased => not_yet(format!("a value of {qname}, whose type is erased")),
        }
    }

    /// Arguments: those `Omitted` at the end left out, as Kotlin's, and
    /// any before one given `nil`, either way the parameter's default.
    pub(crate) fn args(&mut self, args: &[NodeId]) -> R<Vec<Expr>> {
        let p = self.p;
        let given = (args.iter())
            .rposition(|&a| !matches!(p.nodes.get(a), Node::Omitted {}))
            .map_or(0, |i| i + 1);
        args[..given]
            .iter()
            .map(|&a| match p.nodes.get(a) {
                Node::Omitted {} => Ok(Expr::name("nil")),
                _ => self.expr(a),
            })
            .collect()
    }

    /// Whether evaluating an expression does anything its order, or its
    /// being skipped or repeated, could show.
    fn has_effects(&self, id: NodeId) -> bool {
        self.effects.node(self.p, id).any() || classes::classy(self.p, id, &self.classy)
    }

    /// An `Int64` literal: the chunk's constant for it ([`Int64s`]).
    fn int64(&mut self, value: i64) -> Expr {
        self.uses_temper = true;
        let consts = &mut self.int64s;
        let name = match consts.names.iter().find(|(v, _)| *v == value) {
            Some((_, name)) => name.clone(),
            None => {
                let base = match value {
                    v if v < 0 => format!("int64_neg{}", v.unsigned_abs()),
                    v => format!("int64_{v}"),
                };
                let name = fresh(&base, &consts.taken);
                consts.taken.insert(name.clone());
                consts.names.push((value, name.clone()));
                name
            }
        };
        match &consts.table {
            Some(table) => Expr::name(table).member(name),
            None => Expr::name(name),
        }
    }

    /// Starts a chunk's `Int64` constants, for the literals in `roots`,
    /// avoiding `host`'s names; gives how many locals they'll take.
    fn plan_int64s(&mut self, roots: &[NodeId], host: &HashSet<&str>) -> usize {
        let p = self.p;
        let mut values = HashSet::new();
        let mut negated = HashSet::new();
        for &root in roots {
            walk(p, root, &mut |id| match p.nodes.get(id) {
                // A negated literal is the negation's constant.
                Node::Op { op, args, .. }
                    if ops::spelling(op) == Some(Spelling::Helper("int64_unm")) =>
                {
                    if let [arg] = p.nodes.list(args)[..] {
                        if let Node::Int64Lit { value } = p.nodes.get(arg) {
                            negated.insert(arg);
                            values.insert(value.wrapping_neg());
                        }
                    }
                }
                Node::Int64Lit { value } if !negated.contains(&id) => {
                    values.insert(value);
                }
                _ => {}
            });
        }
        let mut taken = self.taken.clone();
        taken.extend(host.iter().map(|w| w.to_string()));
        let table = (values.len() > INT64_LOCALS).then(|| fresh(INT64S, &taken));
        let locals = if table.is_some() { 1 } else { values.len() };
        self.int64s = Int64s {
            table,
            names: Vec::new(),
            taken,
        };
        locals
    }

    /// The declarations of the chunk's `Int64` constants used: a local
    /// each, or one table of them all.
    fn int64_decls(&self) -> Vec<Stmt> {
        let consts = &self.int64s;
        let made = |value: i64| {
            let args = match i32::try_from(value) {
                Ok(small) => vec![Expr::Int(small.into())],
                // Its high (signed) and low 32 bits, as doubles hold them.
                Err(_) => vec![Expr::Int(value >> 32), Expr::Int(value & 0xffff_ffff)],
            };
            ops::temper().method("int64_constructor", args)
        };
        match &consts.table {
            _ if consts.names.is_empty() => Vec::new(),
            Some(table) => vec![Stmt::Local {
                name: table.clone(),
                ty: None,
                value: Some(Expr::Table(
                    (consts.names.iter())
                        .map(|(v, name)| (name.clone(), made(*v)))
                        .collect(),
                )),
            }],
            None => (consts.names.iter())
                .map(|(v, name)| Stmt::Local {
                    name: name.clone(),
                    ty: None,
                    value: Some(made(*v)),
                })
                .collect(),
        }
    }

    pub(crate) fn expr(&mut self, id: NodeId) -> R<Expr> {
        let p = self.p;
        Ok(match p.nodes.get(id) {
            Node::BoolLit { value } => Expr::Bool(value),
            Node::Int32Lit { value } => Expr::Int(value.into()),
            Node::Int64Lit { value } => self.int64(value),
            Node::Float64Lit { value } => Expr::Float(value),
            Node::StringLit { value } => Expr::Str(p.strings.get(value).to_string()),
            Node::VoidLit {} => Expr::Nil,
            // Lua has no value for a builtin type but its `type()` name.
            Node::TypeValue { of } => Expr::Str(
                match p.types.get(of) {
                    TypeData::Scalar(s) => match s {
                        Scalar::Void => "nil",
                        Scalar::Boolean => "boolean",
                        Scalar::Int32 | Scalar::Int64 | Scalar::Float64 => "number",
                        Scalar::String => "string",
                        _ => return not_yet("this type as a value"),
                    },
                    _ => return not_yet("this type as a value"),
                }
                .into(),
            ),
            Node::Local { local: decl } => self.access(decl),
            Node::GetModuleLet { target } => {
                if self.erased.contains(&target) {
                    return not_yet("a value whose type is erased");
                }
                self.access(target)
            }
            Node::CallFunction { callee, args, .. } => {
                let args = p.nodes.list(args);
                if is_external(p, callee) && library_of(p, callee) == "core"
                    || by_connection(p, callee, crate::connected::connects_std)
                {
                    return self.connected(callee, args);
                }
                let args = self.args(args)?;
                match classes::owner_type(p, callee) {
                    // A static function, of its type's table.
                    Some(owner) => (self.type_ref(owner))
                        .member(self.static_key(callee))
                        .call(args),
                    None => self.access(callee).call(args),
                }
            }
            Node::CallMethod {
                method,
                receiver,
                args,
                ..
            } => {
                if !by_connection(p, method, crate::connected::connects_std) {
                    return self.call_method(method, receiver, p.nodes.list(args));
                }
                let mut all = vec![receiver];
                all.extend_from_slice(p.nodes.list(args));
                self.connected(method, &all)?
            }
            Node::Op { op, args, .. } => {
                let Some(mut spelling) = ops::spelling(op) else {
                    return not_yet("an operator without a spelling");
                };
                if spelling == Spelling::Cat {
                    return self.cat(id);
                }
                let list = p.nodes.list(args);
                // A negated literal is a literal, wrapped as the type
                // wraps (`-(-2147483648)` is itself).
                if let [arg] = list {
                    match (spelling, p.nodes.get(*arg)) {
                        (Spelling::Helper("int32_unm"), Node::Int32Lit { value }) => {
                            return Ok(Expr::Int(value.wrapping_neg().into()));
                        }
                        (Spelling::Helper("int64_unm"), Node::Int64Lit { value }) => {
                            return Ok(self.int64(value.wrapping_neg()));
                        }
                        (Spelling::Unary(tree::Unary::Neg), Node::Float64Lit { value }) => {
                            return Ok(Expr::Float(-value));
                        }
                        _ => {}
                    }
                }
                // Lua's `and` and `or` skip their right side when the left
                // decides, so `Boolean`'s order is the runtime's where the
                // right side does something.
                if let (Spelling::BoolOrder(cmp), [_, right]) = (spelling, list) {
                    if self.has_effects(*right) {
                        spelling = Spelling::CmpWith("bool_cmp", cmp);
                    }
                }
                let args: Vec<Expr> = list.iter().map(|&a| self.expr(a)).collect::<R<_>>()?;
                if ops::helper(spelling).is_some() {
                    self.uses_temper = true;
                }
                ops::apply(spelling, args)
            }
            Node::And { .. } | Node::Or { .. } if self.needs_hoist(id) => self.hoist(id)?,
            Node::And { left, right } => Expr::bin(Bin::And, self.expr(left)?, self.expr(right)?),
            Node::Or { left, right } => Expr::bin(Bin::Or, self.expr(left)?, self.expr(right)?),
            Node::IfValue { cond, then, els } if self.needs_hoist(id) => {
                let _ = (cond, then, els);
                self.hoist(id)?
            }
            Node::IfValue { cond, then, els } => self.if_value(cond, then, els)?,
            Node::Error { .. } => self.bubble(),
            Node::This {} => Expr::name("this"),
            // std's, as `Date.year`'s `temper.date_year(d)`, as Kotlin's.
            Node::GetField { field, object }
                if library_of(p, field) == "std"
                    && by_connection(p, field, crate::connected::connects_std) =>
            {
                self.connected(field, &[object])?
            }
            // A step's value, second in its table.
            Node::GetField { field, object }
                if self.qname(field) == "core.type ValueResult.value" =>
            {
                Expr::Index(Box::new(self.expr(object)?), Box::new(Expr::Int(2)))
            }
            // A pair's, by its own name.
            Node::GetField { field, object }
                if by_connection(p, field, crate::connected::connects_std) =>
            {
                self.expr(object)?.member(p.name(field))
            }
            Node::GetField { field, object } => self.expr(object)?.member(self.field_key(field)),
            // `String.begin`, `Float64.pi`, and the like.
            Node::GetStatic { target }
                if is_external(p, target) && library_of(p, target) == "core" =>
            {
                self.connected(target, &[])?
            }
            Node::GetStatic { target } => {
                let owner = classes::owner_type(p, target).unwrap_or(target);
                self.type_ref(owner).member(self.static_key(target))
            }
            Node::New { ctor, args, .. }
                if is_external(p, ctor) && library_of(p, ctor) == "core"
                    || by_connection(p, ctor, crate::connected::connects_std) =>
            {
                self.connected(ctor, p.nodes.list(args))?
            }
            // `{ a, b }`, as Kotlin's.
            Node::ListLit { items } => Expr::Array(self.args(p.nodes.list(items))?),
            Node::New { ctor, args, .. } => {
                let owner = classes::owner_type(p, ctor).unwrap_or(ctor);
                let args = self.args(p.nodes.list(args))?;
                self.type_ref(owner).call(args)
            }
            Node::Upcast { value } => self.expr(value)?,
            // Lua doesn't check it; LuaLS takes it as the type.
            Node::Downcast { value } => {
                Expr::Cast(Box::new(self.expr(value)?), self.ty(p.nodes.ty(id))?)
            }
            Node::As { value, .. } => self.cast(id, value)?,
            Node::Is { ty, value } => self.is_type(ty, value)?,
            Node::Identical { left, right } => {
                Expr::bin(Bin::Eq, self.expr(left)?, self.expr(right)?)
            }
            Node::Null {} => {
                self.uses_temper = true;
                ops::temper().member("null")
            }
            Node::IsNull { value } => {
                let value = self.expr(value)?;
                self.uses_temper = true;
                ops::temper().method("is_null", vec![value])
            }
            // Lua doesn't check it; LuaLS takes it as the type.
            Node::NonNull { value } => {
                Expr::Cast(Box::new(self.expr(value)?), self.ty(p.nodes.ty(id))?)
            }
            Node::AsNullable { value } => self.expr(value)?,
            // Null is always `temper.null`, which the type's own equality
            // tells from every value: Lua's `==`, and the runtime's
            // `float_eq`.
            Node::EqNullable { op, left, right } => {
                let Some(spelling) = ops::spelling(op) else {
                    return not_yet("an operator without a spelling");
                };
                let args = vec![self.expr(left)?, self.expr(right)?];
                if ops::helper(spelling).is_some() {
                    self.uses_temper = true;
                }
                ops::apply(spelling, args)
            }
            Node::Bubble { .. } => self.bubble(),
            // Kotlin's is `temper.bubble()`; this says what happened, and
            // likewise stops the program if nothing catches it.
            Node::Panic {} => Expr::name("error").call(vec![Expr::Str("panic".into())]),
            Node::OrElse { .. } | Node::BlockValue { .. } => self.hoist(id)?,
            Node::FunctionLiteral { decl, params, body } => {
                self.literal(id, decl, params, body, None)?
            }
            // `promise:await()`, which parks the `async` block's coroutine
            // until the promise is settled, as Kotlin's.
            Node::Await { promise, .. } => {
                let promise = self.expr(promise)?;
                Expr::Invoke(Box::new(promise), "await".into(), Vec::new())
            }
            Node::FunctionRef { target } => match classes::owner_type(p, target) {
                // A static function, of its type's table.
                Some(owner) => (self.type_ref(owner)).member(self.static_key(target)),
                None => self.access(target),
            },
            Node::CallValue { callee, args, .. } => {
                let f = self.expr(callee)?;
                f.call(self.args(p.nodes.list(args))?)
            }
            Node::ToFun { value } | Node::FromFun { value } => self.expr(value)?,
            other => return not_yet(format!("a {} expression", other.kind().name())),
        })
    }

    /// Whether an expression has a part Lua can't write inline: an
    /// `orelse`, statements before a value, or an `if` value that `and`
    /// and `or` can't choose ([`Self::if_value`]), or one of these in a
    /// part that runs only sometimes (an `if` value's branch, `&&`'s or
    /// `||`'s right side), so the whole goes before its statement.
    fn needs_hoist(&self, id: NodeId) -> bool {
        let p = self.p;
        match p.nodes.get(id) {
            Node::OrElse { .. } | Node::BlockValue { .. } => true,
            // A function's body is its own: what it hoists stays in it.
            Node::FunctionLiteral { .. } => false,
            Node::IfValue { cond, then, els } => {
                self.needs_hoist(cond)
                    || self.needs_hoist(then)
                    || self.needs_hoist(els)
                    || !self.inline_if(cond, then)
            }
            _ => p.nodes.kids(id).iter().any(|&k| self.needs_hoist(k)),
        }
    }

    /// Whether [`Self::if_value`] writes an `if` value inline.
    fn inline_if(&self, cond: NodeId, then: NodeId) -> bool {
        let p = self.p;
        let falsy = matches!(
            p.types.get(p.nodes.ty(then)),
            TypeData::Scalar(Scalar::Boolean | Scalar::Void | Scalar::AnyValue)
        );
        !falsy
            || matches!(p.nodes.get(then), Node::BoolLit { .. })
            || (!self.has_effects(cond)
                && matches!(
                    p.types.get(p.nodes.ty(then)),
                    TypeData::Scalar(Scalar::Boolean)
                ))
    }

    /// Checks that what a statement's expressions hoist runs in Temper's
    /// order: before it, only parts with no effects go uncomputed, and
    /// a loop's condition hoists nothing, since it runs again.
    fn check_hoisting(&self, id: NodeId) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::While { cond, .. } | Node::DoWhile { cond, .. } if self.needs_hoist(cond) => {
                return not_yet("a loop's condition with an orelse or statements");
            }
            _ => {}
        }
        // A statement's own expressions, in order; its blocks' statements
        // check theirs.
        let kids: Vec<NodeId> = (p.nodes.kids(id).iter().copied())
            .filter(|&k| p.nodes.kind(k).family() == temper_tmpl::schema::Family::Expr)
            .collect();
        self.check_order(&kids)
    }

    /// Parts evaluated in order: those before the first that hoists must
    /// have no effects, which hoisting would put after it.
    fn check_order(&self, parts: &[NodeId]) -> R<()> {
        let Some(first) = parts.iter().position(|&k| self.needs_hoist(k)) else {
            return Ok(());
        };
        if parts[..first].iter().any(|&k| self.has_effects(k)) {
            return not_yet("an orelse or statements after something with effects");
        }
        let p = self.p;
        let hoisted = parts[first];
        match p.nodes.get(hoisted) {
            // Each hoists whole, its parts in place.
            Node::OrElse { .. } | Node::BlockValue { .. } => Ok(()),
            Node::IfValue { cond, .. } if !self.inline_if_or_parts(hoisted) => {
                self.check_order(&[cond])
            }
            Node::And { left, .. } | Node::Or { left, .. } => self.check_order(&[left]),
            _ => self.check_order(p.nodes.kids(hoisted)),
        }
    }

    /// Whether an `if` value hoists whole, as statements.
    fn inline_if_or_parts(&self, id: NodeId) -> bool {
        let Node::IfValue { cond, then, .. } = self.p.nodes.get(id) else {
            return true;
        };
        self.inline_if(cond, then)
            && !self.needs_hoist(then)
            && !matches!(self.p.nodes.get(id), Node::IfValue { els, .. } if self.needs_hoist(els))
    }

    /// A temporary's name, unlike every name the program has: `t`, then
    /// `t_1`, `t_2`, and so on, in a function.
    fn temp(&mut self) -> String {
        loop {
            let name = match self.temps {
                0 => "t".to_string(),
                n => format!("t_{n}"),
            };
            self.temps += 1;
            if !self.taken.contains(&name) {
                return name;
            }
        }
    }

    /// An expression's statements and value: what it hoists, then what's
    /// left.
    fn parts(&mut self, id: NodeId) -> R<(Vec<Stmt>, Expr)> {
        self.check_order(&[id])?;
        let outer = std::mem::take(&mut self.pre);
        let value = self.expr(id);
        let pre = std::mem::replace(&mut self.pre, outer);
        Ok((pre, value?))
    }

    /// An expression as statements assigning `target`.
    fn assigning(&mut self, target: &Expr, id: NodeId) -> R<Vec<Stmt>> {
        let (mut stmts, value) = self.parts(id)?;
        // `bubble()` and `panic()` give nothing to assign.
        if matches!(self.p.nodes.get(id), Node::Bubble { .. } | Node::Panic {}) {
            stmts.push(Stmt::Expr(value));
        } else if &value != target {
            stmts.push(Stmt::Assign {
                target: target.clone(),
                value,
            });
        }
        Ok(stmts)
    }

    /// An expression as statements whose value is dropped.
    fn dropping(&mut self, id: NodeId) -> R<Vec<Stmt>> {
        let mut out = Vec::new();
        let (pre, value) = self.parts(id)?;
        out.extend(pre);
        match value {
            Expr::Nil | Expr::Name(_) | Expr::Bool(_) | Expr::Int(_) | Expr::Str(_) => {}
            e if e.is_statement() => out.push(Stmt::Expr(e)),
            e => out.push(Stmt::Local {
                name: "_".into(),
                ty: None,
                value: Some(e),
            }),
        }
        Ok(out)
    }

    /// What Lua can't write inline, as statements before the statement
    /// it's in (`self.pre`), leaving a temporary holding its value:
    ///
    /// - `orelse`, as Kotlin's, by `temper.pcall` of a function assigning
    ///   the temporary, and the fallback where it fails;
    /// - statements before a value, in place;
    /// - an `if` value, `&&`, or `||` with such parts, or an `if` value
    ///   that `and` and `or` can't choose, as an `if` statement.
    fn hoist(&mut self, id: NodeId) -> R<Expr> {
        let p = self.p;
        let ty = p.nodes.ty(id);
        match p.nodes.get(id) {
            Node::BlockValue { stmts, value } => {
                let stmts = p.nodes.list(stmts);
                let forward = self.forward_locals(stmts)?;
                for &s in stmts {
                    let mut out = forward.get(&s).cloned().unwrap_or_default();
                    self.stmt(s, &mut out)?;
                    self.pre.extend(out);
                }
                self.expr(value)
            }
            Node::OrElse { .. } if ty == TypeId::VOID => {
                let t = self.or_else_stmt(id)?;
                self.pre.extend(t);
                Ok(Expr::Nil)
            }
            Node::OrElse { .. } => {
                let name = self.temp();
                self.pre.push(Stmt::Local {
                    name: name.clone(),
                    ty: Some(self.local_ty(ty)?),
                    value: None,
                });
                let t = self.or_else_into(Expr::name(&name), id)?;
                self.pre.extend(t);
                Ok(Expr::name(name))
            }
            Node::IfValue { cond, then, els } => {
                let c = self.expr(cond)?;
                let name = self.temp();
                let target = Expr::name(&name);
                let then = self.assigning(&target, then)?;
                let els = self.assigning(&target, els)?;
                self.pre.push(Stmt::Local {
                    name: name.clone(),
                    ty: Some(self.local_ty(ty)?),
                    value: None,
                });
                self.pre.push(Stmt::If { cond: c, then, els });
                Ok(target)
            }
            Node::And { left, right } | Node::Or { left, right } => {
                let and = matches!(p.nodes.get(id), Node::And { .. });
                let l = self.expr(left)?;
                let name = self.temp();
                let target = Expr::name(&name);
                let right = self.assigning(&target, right)?;
                self.pre.push(Stmt::Local {
                    name,
                    ty: Some("boolean".into()),
                    value: Some(l),
                });
                let cond = if and {
                    target.clone()
                } else {
                    target.clone().negate()
                };
                self.pre.push(Stmt::If {
                    cond,
                    then: right,
                    els: Vec::new(),
                });
                Ok(target)
            }
            other => not_yet(format!("hoisting a {}", other.kind().name())),
        }
    }

    /// `body orelse fallback`, assigning `target` with what it gives.
    fn or_else_into(&mut self, target: Expr, id: NodeId) -> R<Vec<Stmt>> {
        self.or_else(id, Some(&target))
    }

    /// `body orelse fallback`, dropping its value.
    fn or_else_stmt(&mut self, id: NodeId) -> R<Vec<Stmt>> {
        self.or_else(id, None)
    }

    /// `body orelse fallback`, as Kotlin's: `if not temper.pcall(function()
    /// … end) then … end`; or where the body jumps out of itself (a
    /// `return`, `break`, or `continue`), which it can't from a function of
    /// its own, `local ok, value, kind = temper.pcall(…)`, the body
    /// returning a signal for each jump ([`TryBody`]), and the jump redone
    /// after it:
    ///
    /// ```lua
    /// if not ok then
    ///   -- the fallback
    /// elseif kind == "return" then
    ///   return value
    /// elseif value == "break" then
    ///   break
    /// end
    /// ```
    fn or_else(&mut self, id: NodeId, target: Option<&Expr>) -> R<Vec<Stmt>> {
        let Node::OrElse { body, fallback } = self.p.nodes.get(id) else {
            return not_yet("an orelse that isn't one");
        };
        let returns = std::mem::replace(&mut self.returns, false);
        self.tries.push(TryBody {
            loops: self.loops.len(),
            jumps: Vec::new(),
        });
        let body = match target {
            Some(target) => self.assigning(target, body),
            None => self.dropping(body),
        };
        let jumps = self.tries.pop().map(|t| t.jumps).unwrap_or_default();
        self.returns = returns;
        let body = body?;
        let fallback = match target {
            Some(target) => self.assigning(target, fallback)?,
            None => self.dropping(fallback)?,
        };
        self.uses_temper = true;
        if jumps.is_empty() {
            return Ok(vec![Stmt::Try { body, fallback }]);
        }
        let names = [self.temp(), self.temp(), self.temp()];
        let [ok, value, kind] = names.clone().map(Expr::name);
        // The `if` chain, built from its end; a return's value may be a
        // jump's name, so `return` comes first.
        let mut jumps = jumps;
        jumps.sort_by_key(|j| *j != Jump::Return);
        let mut chain = Vec::new();
        for jump in jumps.into_iter().rev() {
            let (cond, redo) = match jump {
                Jump::Return => {
                    let cond = Expr::bin(Bin::Eq, kind.clone(), Expr::Str("return".into()));
                    let value = (self.returns || !self.tries.is_empty()).then(|| value.clone());
                    (cond, self.return_stmt(value))
                }
                Jump::Loop { label, brk, name } => {
                    let cond = Expr::bin(Bin::Eq, value.clone(), Expr::Str(name));
                    (cond, self.jump(label, brk)?)
                }
            };
            chain = vec![Stmt::If {
                cond,
                then: vec![redo],
                els: chain,
            }];
        }
        Ok(vec![
            Stmt::PCall {
                names: names.to_vec(),
                body,
            },
            Stmt::If {
                cond: ok.negate(),
                then: fallback,
                els: chain,
            },
        ])
    }

    /// An `if` value, as `c and a or b`, which is right when `a` can't be
    /// `false` or `nil`: when it isn't a `Boolean` (or `Void`, or
    /// `AnyValue`). A `Boolean` literal there gives `c or b`, or `not c
    /// and b`; another `Boolean`, `(c and a) or (not c and b)`, when
    /// evaluating `c` twice can't show. Otherwise it waits for the hoisting
    /// pass, which will give it a temporary.
    fn if_value(&mut self, cond: NodeId, then: NodeId, els: NodeId) -> R<Expr> {
        let p = self.p;
        let falsy = matches!(
            p.types.get(p.nodes.ty(then)),
            TypeData::Scalar(Scalar::Boolean | Scalar::Void | Scalar::AnyValue)
        );
        let c = self.expr(cond)?;
        if !falsy {
            let a = self.expr(then)?;
            let b = self.expr(els)?;
            return Ok(Expr::bin(Bin::Or, Expr::bin(Bin::And, c, a), b));
        }
        match p.nodes.get(then) {
            Node::BoolLit { value: true } => Ok(Expr::bin(Bin::Or, c, self.expr(els)?)),
            Node::BoolLit { value: false } => Ok(Expr::bin(Bin::And, c.negate(), self.expr(els)?)),
            _ if !self.has_effects(cond)
                && matches!(
                    p.types.get(p.nodes.ty(then)),
                    TypeData::Scalar(Scalar::Boolean)
                ) =>
            {
                let a = self.expr(then)?;
                let b = self.expr(els)?;
                Ok(Expr::bin(
                    Bin::Or,
                    Expr::bin(Bin::And, c.clone(), a),
                    Expr::bin(Bin::And, c.negate(), b),
                ))
            }
            _ => not_yet("an `if` value of a type Lua's `and` and `or` can't choose"),
        }
    }

    /// A chain of `String.cat` as one `..`: its literal parts as they
    /// are, an `Int32.toString()` without a radix as the integer itself,
    /// which `..` writes the same way, and a `Boolean.toString()` as
    /// `tostring(b)`. All literals give one string.
    fn cat(&mut self, id: NodeId) -> R<Expr> {
        let mut parts = Vec::new();
        self.cat_parts(id, &mut parts)?;
        if parts.iter().all(|(e, _)| matches!(e, Expr::Str(_))) {
            let text: String = parts
                .iter()
                .filter_map(|(e, _)| match e {
                    Expr::Str(s) => Some(s.as_str()),
                    _ => None,
                })
                .collect();
            return Ok(Expr::Str(text));
        }
        // Lua's `..` needs two operands; a number alone needs `tostring`.
        if let [(e, number)] = parts.as_slice() {
            return Ok(if *number {
                Expr::name("tostring").call(vec![e.clone()])
            } else {
                e.clone()
            });
        }
        Ok(Expr::Concat(parts.into_iter().map(|(e, _)| e).collect()))
    }

    /// Each part, and whether it's a number that `..` converts.
    fn cat_parts(&mut self, id: NodeId, parts: &mut Vec<(Expr, bool)>) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Op { op, args, .. } if ops::spelling(op) == Some(Spelling::Cat) => {
                for &a in p.nodes.list(args) {
                    self.cat_parts(a, parts)?;
                }
            }
            Node::StringLit { value } => {
                let text = p.strings.get(value);
                if text.is_empty() {
                    return Ok(());
                }
                if let Some((Expr::Str(last), _)) = parts.last_mut() {
                    last.push_str(text);
                } else {
                    parts.push((Expr::Str(text.to_string()), false));
                }
            }
            Node::CallMethod {
                method,
                receiver,
                args,
                ..
            } if self.qname(method) == "core.type Int32.toString()"
                && p.nodes
                    .list(args)
                    .iter()
                    .all(|&a| matches!(p.nodes.get(a), Node::Omitted {})) =>
            {
                let e = self.expr(receiver)?;
                parts.push((e, true));
            }
            _ => {
                let e = self.expr(id)?;
                parts.push((e, false));
            }
        }
        Ok(())
    }
}

/// The statements that can run: none after a `return`, `break`, or
/// `goto` in the same block, which Lua requires of `return`.
fn reachable(mut stmts: Vec<Stmt>) -> Vec<Stmt> {
    if let Some(i) = stmts.iter().position(Stmt::ends) {
        stmts.truncate(i + 1);
    }
    stmts
}

/// The most locals alive at once in a block or statement: its own, and
/// those of the blocks nested in it.
fn max_locals(p: &Program, id: NodeId) -> usize {
    match p.nodes.get(id) {
        Node::Block { stmts } => {
            let (mut alive, mut most) = (0, 0);
            for &s in p.nodes.list(stmts) {
                if let Node::Let { .. } = p.nodes.get(s) {
                    alive += 1;
                }
                most = most.max(alive + nested(p, s));
            }
            most
        }
        Node::Let { .. } => 1,
        _ => nested(p, id),
    }
}

fn nested(p: &Program, id: NodeId) -> usize {
    match p.nodes.get(id) {
        Node::Block { .. } => max_locals(p, id),
        Node::If { then, els, .. } => max_locals(p, then).max(els.map_or(0, |e| max_locals(p, e))),
        Node::While { body, .. } | Node::DoWhile { body, .. } => max_locals(p, body),
        Node::Function { body, .. } => max_locals(p, body),
        _ => 0,
    }
}

/// A test's function name: `test_` and its name in snake case, ASCII
/// letters, digits, and `_` only, numbered past those already used.
fn test_method_name(text: &str, used: &mut Vec<String>) -> String {
    let mut name = String::from("test_");
    for c in temper_tmpl::naming::snake_case(text).chars() {
        if c.is_ascii_alphanumeric() {
            name.push(c);
        } else if !name.ends_with('_') {
            name.push('_');
        }
    }
    let name = name.trim_end_matches('_').to_string();
    let name = if name == "test" {
        "test_".to_string()
    } else {
        name
    };
    let mut unique = name.clone();
    let mut n = 2;
    while used.contains(&unique) {
        unique = format!("{name}_{n}");
        n += 1;
    }
    used.push(unique.clone());
    unique
}

/// For a `let`'s initializer that's an optional parameter's value
/// coalesced with its default: the parameter and the default.
fn param_default(p: &Program, init: NodeId) -> Option<(DeclId, NodeId)> {
    let Node::Coalesce { left, right } = p.nodes.get(init) else {
        return None;
    };
    let Node::Local { local: param } = p.nodes.get(left) else {
        return None;
    };
    let d = p.decls.get(param);
    (d.kind == DeclKind::Param && d.facts.contains(Facts::OPTIONAL)).then_some((param, right))
}

/// The names by which a connected declaration's backend list can mean this
/// backend, as Kotlin's `connectedBackendNames`.
pub const NAMES: &[&str] = &["lua"];

/// The table host code defines its functions and classes in, a local of
/// its module's chunk: `local _connected = {}`.
pub const HOST: &str = "_connected";

/// Where a module's host code is, relative to the library's root:
/// `_connected.lua` in its directory, as Kotlin's.
pub fn host_path(module: &Module) -> String {
    match module.path.as_str() {
        "" => "_connected.lua".to_string(),
        dir => format!("{dir}/_connected.lua"),
    }
}

/// A module's host code, if it has any, which its chunk inlines.
fn host_file<'p>(p: &'p Program, module: &Module) -> Option<&'p HostFile> {
    let path = host_path(module);
    p.host.files.iter().find(|f| f.path == path)
}

/// `base`, or if `taken` has it, `base` with the first number after it
/// (`base_1`) that `taken` hasn't.
fn fresh(base: &str, taken: &HashSet<String>) -> String {
    if !taken.contains(base) {
        return base.to_string();
    }
    (1..)
        .map(|n| format!("{base}_{n}"))
        .find(|name| !taken.contains(name))
        .expect("a free name")
}

/// The name-like words in Lua code that may refer to a local: each not
/// after `.` or `:`, as a field's or method's name is (`_connected.sum`).
/// Words in strings and comments count too, which only declares a name
/// early.
fn identifiers(text: &str) -> HashSet<&str> {
    let word = |c: char| c == '_' || c.is_ascii_alphanumeric();
    let mut found = HashSet::new();
    let mut start = None;
    for (i, c) in text.char_indices().chain([(text.len(), ' ')]) {
        match (start, word(c)) {
            (None, true) => start = Some(i),
            (Some(s), false) => {
                let before = text[..s].trim_end().chars().last();
                if !matches!(before, Some('.' | ':')) {
                    found.insert(&text[s..i]);
                }
                start = None;
            }
            _ => {}
        }
    }
    found
}
