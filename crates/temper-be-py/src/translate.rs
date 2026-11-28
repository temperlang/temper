//! The translation from tmpl to Python's output tree, for subset A, with
//! modules and imports: a module refers to another's functions and
//! `let`s by `from MODULE import NAME`, and to its `var`s through the
//! module (`import MODULE as ALIAS`, `ALIAS.NAME`), so a read is current
//! and a write lands there. Tests go in a file of their own
//! ([`Translator::tests`]), each a method of a `unittest.TestCase`, after
//! what only tests reach, which only that file has, as tmpl's
//! `reach::partition` splits it.
//! Classes and interfaces, and their members' uses, are in
//! [`crate::classes`].

use crate::NotYet;
use crate::connected::{self, Connection};
use crate::ops::{self, Spelling};
use crate::tree::{self, Bin, Expr, FPart, Stmt};
use std::collections::{BTreeMap, BTreeSet};
use temper_tmpl::analysis::{self, TestParts, home, module_writes, nominal_decl};
use temper_tmpl::coroutines::pauses;
use temper_tmpl::host::{HostFile, uses_host};
use temper_tmpl::naming::NameMap;
use temper_tmpl::naming::snake_case;
use temper_tmpl::qname::{is_external, library_of};
use temper_tmpl::{
    DeclId, DeclKind, Facts, Module, Node, NodeId, Program, QNames, Scalar, TypeData, TypeId,
};

pub(crate) type R<T> = Result<T, NotYet>;

fn not_yet<T>(what: impl Into<String>) -> R<T> {
    Err(NotYet { what: what.into() })
}

pub struct Translator<'a> {
    pub(crate) p: &'a Program,
    pub(crate) names: &'a NameMap,
    qnames: QNames,
    /// Imported names, by module: `name`, or `name as local`.
    imports: BTreeMap<String, BTreeSet<String>>,
    /// Each name imported, by module and name: the module's local name
    /// for it.
    aliases: BTreeMap<(String, String), String>,
    /// Module-level names, which imports of others make way for.
    own: BTreeSet<String>,
    /// Modules imported whole, for their `var`s: each module's alias.
    modules: BTreeMap<String, String>,
    /// The module whose file this is; `NONE` for a tests file.
    pub(crate) here: DeclId,
    /// For a tests file, the module tested, and what its test parts
    /// define, which are the file's own, and use of it, which the file
    /// imports.
    tested: DeclId,
    test_parts: TestParts,
    /// The class or interface whose member this is; `NONE` outside one.
    pub(crate) class: DeclId,
    /// This module's classes and interfaces defined so far, which
    /// annotations needn't quote.
    pub(crate) defined: BTreeSet<DeclId>,
    /// Each class's and interface's node, of any module, by declaration.
    pub(crate) class_nodes: BTreeMap<DeclId, NodeId>,
    /// The enclosing loops' labels, innermost last.
    loops: Vec<DeclId>,
    /// The labels of loops a jump from an inner loop leaves or continues,
    /// by loop: `outer`, numbered past any other name.
    labels: BTreeMap<DeclId, String>,
    /// The locals that something reads, once asked for.
    read_locals: Option<BTreeSet<DeclId>>,
    /// Statements that must run before the expression being translated,
    /// hoisted out of it (an `orelse`'s `try`, a block's statements), in
    /// order; the statement being translated takes them first.
    pre: Vec<Stmt>,
    /// Locals whose `let` a `??` became, to assign where first read, as
    /// `(subject := f())`.
    walrus: BTreeMap<DeclId, (Expr, TypeId)>,
    /// Temporaries made so far, for their numbers.
    temps: usize,
    /// How many functions deep the translation is: 0 at module level.
    in_def: usize,
    /// Whether the statement being translated checks a `var` local for
    /// null.
    var_null_check: bool,
    /// The module's type parameters' `TypeVar`s, by declaration.
    type_vars: BTreeMap<DeclId, String>,
    /// Type parameters annotated as other types: a supertype's, as a class
    /// that extends it gives them, while writing a member it inherits
    /// (`classes.rs`, `pick`).
    pub(crate) type_args: BTreeMap<DeclId, TypeId>,
    /// Locals declared in the loops being translated, a fresh binding
    /// each time round, which Python's closures would share.
    loop_lets: BTreeSet<DeclId>,
    /// Function literals passed where core's method wants a function that
    /// gives back its own type parameter, which are `def`s
    /// ([`Translator::literal`]).
    widen: BTreeSet<NodeId>,
    /// Nullable locals that a default set in place, which pyright then
    /// takes to be never null.
    defaulted: BTreeSet<DeclId>,
    /// The `do_await` parameters of the generators being translated,
    /// innermost last, which an `await` yields a call of.
    awaiters: Vec<String>,
    /// How the function being translated, or the module's init, binds
    /// its locals' names ([`Bindings`]).
    bindings: Bindings,
}

/// How a Python scope (a function's, or the module's init) binds its
/// locals' names. Python gives a name one type in a scope, and mypy takes
/// it from the name's first binding, its annotation or else its value's
/// type, and rejects a later annotation (`no-redef`). So a name bound more
/// than once, by several locals sharing it (as the naming pass lets those
/// of one type, with or without null) or by a `var` assigned again, is
/// annotated only where first bound, with its widest type, and only
/// where its value's type may be narrower. A first binding of a narrower
/// type (not null, where another is nullable) is declared on its own
/// line before it, so its value narrows it, as is a walrus that binds it
/// first, which can't annotate.
struct Bindings {
    /// The function or module whose locals these are.
    owner: DeclId,
    /// The names bound more than once, each with its widest type: its
    /// locals' type, nullable where any of theirs is.
    rebound: BTreeMap<String, TypeId>,
    /// The names bound so far, parameters' first.
    bound: BTreeSet<String>,
}

impl Default for Bindings {
    fn default() -> Bindings {
        Bindings {
            owner: DeclId::NONE,
            rebound: BTreeMap::new(),
            bound: BTreeSet::new(),
        }
    }
}

/// Where a value goes, for [`Translator::value_into`].
enum Sink {
    Return,
    /// Assigned to a name or attribute, with an annotation the first time.
    Assign(Expr, Option<String>),
    /// Dropped.
    Discard,
}

impl<'a> Translator<'a> {
    pub fn new(p: &'a Program, names: &'a NameMap) -> Translator<'a> {
        let mut class_nodes = BTreeMap::new();
        for module in &p.modules {
            for item in module.all_items() {
                if let Node::Class { decl, .. } | Node::Interface { decl, .. } = p.nodes.get(item) {
                    class_nodes.insert(decl, item);
                }
            }
        }
        Translator {
            p,
            names,
            qnames: QNames::derive(p),
            imports: BTreeMap::new(),
            aliases: BTreeMap::new(),
            own: BTreeSet::new(),
            modules: BTreeMap::new(),
            here: DeclId::NONE,
            tested: DeclId::NONE,
            test_parts: TestParts::default(),
            class: DeclId::NONE,
            defined: BTreeSet::new(),
            class_nodes,
            loops: Vec::new(),
            labels: BTreeMap::new(),
            read_locals: None,
            pre: Vec::new(),
            walrus: BTreeMap::new(),
            temps: 0,
            in_def: 0,
            var_null_check: false,
            type_vars: BTreeMap::new(),
            type_args: BTreeMap::new(),
            loop_lets: BTreeSet::new(),
            widen: BTreeSet::new(),
            awaiters: Vec::new(),
            bindings: Bindings::default(),
            defaulted: BTreeSet::new(),
        }
    }

    /// The names a file binds at its top level: its `items`'
    /// declarations, and those of other modules' and libraries' that its
    /// `roots` use, which it imports, so imports of Python's or the
    /// runtime's names make way.
    fn module_level_names(
        &self,
        module: &Module,
        items: &[NodeId],
        roots: &[NodeId],
    ) -> BTreeSet<String> {
        let p = self.p;
        let own = (items.iter()).filter_map(|&item| match p.nodes.get(item) {
            Node::Function { decl, .. }
            | Node::BodilessFunction { decl, .. }
            | Node::ModuleLet { decl }
            | Node::Class { decl, .. }
            | Node::Interface { decl, .. } => Some(decl),
            _ => None,
        });
        let imports = temper_tmpl::analysis::imports(p, module.decl, roots);
        let used = imports.into_iter().flat_map(|i| i.decls);
        (own.chain(used))
            .map(|d| self.name(d))
            .filter(|n| !n.is_empty())
            .collect()
    }

    /// Imports `name` from `from`, giving the name the module uses for
    /// it. A library's module binds only its exports without a leading
    /// `_`, so its imports are private aliases (`mapped_for_each as
    /// _mapped_for_each`, `Sequence as _Sequence`), with a trailing `_`
    /// where a name in use has that alias already; a name with a leading
    /// `_` stays as it is. A tests file, which nothing imports, takes the
    /// name itself, or, where one of the module's own top-level names is
    /// the same, an alias with a trailing `_`.
    pub(crate) fn import(&mut self, from: &str, name: &str) -> String {
        let key = (from.to_string(), name.to_string());
        if let Some(local) = self.aliases.get(&key) {
            return local.clone();
        }
        let private = !self.here.is_none() && !name.starts_with('_');
        let mut local = match private {
            true => format!("_{name}"),
            false => name.to_string(),
        };
        // Nor a declaration's name, as a test's local `date`, which would
        // hide `datetime`'s `date` in its function.
        while self.own.contains(&local)
            || self.names.has_name(&local)
            || self.aliases.values().any(|a| *a == local)
            || self.modules.values().any(|a| *a == local)
            || self.type_vars.values().any(|a| *a == local)
        {
            local.push('_');
        }
        self.aliases.insert(key, local.clone());
        let entry = match local == name {
            true => local.clone(),
            false => format!("{name} as {local}"),
        };
        self.imports
            .entry(from.to_string())
            .or_default()
            .insert(entry);
        local
    }

    /// The Python module a module of tmpl's is, as `pkg.helpers` (the
    /// root module, `pkg.pkg`), or another library's, as
    /// `other.helpers` (its root, `other`).
    pub fn py_module(&self, module: DeclId) -> String {
        let p = self.p;
        let d = p.decls.get(module);
        if d.kind == DeclKind::Library {
            return crate::names::library_package(&library_name(p, p.name(module)));
        }
        if !d.owner.is_none() {
            let package = crate::names::library_package(&library_name(p, p.name(d.owner)));
            return dotted(&package, p.name(module));
        }
        let package = crate::names::library_package(&library_name(p, &p.library.name));
        let path = p.modules.iter().find(|m| m.decl == module);
        match path.map(|m| m.path.as_str()) {
            None | Some("") => format!("{package}.{package}"),
            Some(path) => dotted(&package, path),
        }
    }

    /// A module-level declaration of this module's, or another's,
    /// imported: its name, or for a `var` of another module's, its
    /// module's alias and its name.
    pub(crate) fn top(&mut self, decl: DeclId) -> Expr {
        let name = self.name(decl);
        if self.is_here(decl) {
            return Expr::name(name);
        }
        let from = home(self.p, decl).unwrap_or(DeclId::NONE);
        let module = self.py_module(from);
        let d = self.p.decls.get(decl);
        if d.kind == DeclKind::ModuleLet && d.facts.contains(Facts::VAR) {
            let alias = self.module_alias(&module);
            return Expr::name(alias).attr(&name);
        }
        if self.here.is_none() {
            // A tests file's name for it is its own.
            self.imports.entry(module).or_default().insert(name.clone());
            return Expr::name(name);
        }
        Expr::name(self.import(&module, &name))
    }

    /// Whether a module-level declaration is this file's own: its module's,
    /// or in a tests file, its test parts'.
    pub(crate) fn is_here(&self, decl: DeclId) -> bool {
        match self.here.is_none() {
            true => self.test_parts.own.contains(&decl),
            false => home(self.p, decl) == Some(self.here),
        }
    }

    /// The alias a module imported whole goes by: `_m_` and its dotted
    /// name, with `_` for the dots, numbered past any name in use.
    fn module_alias(&mut self, module: &str) -> String {
        if let Some(alias) = self.modules.get(module) {
            return alias.clone();
        }
        let base = format!("_m_{}", module.replace('.', "_"));
        let taken = |n: &str| {
            (self.p.decls.ids()).any(|d| self.names.get(d) == n)
                || self.modules.values().any(|a| a == n)
                || self.aliases.values().any(|a| a == n)
        };
        let mut alias = base.clone();
        let mut n = 2;
        while taken(&alias) {
            alias = format!("{base}_{n}");
            n += 1;
        }
        self.modules.insert(module.to_string(), alias.clone());
        alias
    }

    /// The name of a `def` the translator makes, as a function literal's:
    /// at module level, private (`_fn9`), past any name in use, as every
    /// module-level name but exports; in a function, as it is.
    fn def_name(&self, name: String) -> String {
        if self.in_def > 0 || self.here.is_none() || name.starts_with('_') {
            return name;
        }
        let mut private = format!("_{name}");
        while self.names.has_name(&private)
            || self.own.contains(&private)
            || self.aliases.values().any(|a| *a == private)
            || self.modules.values().any(|a| *a == private)
        {
            private.push('_');
        }
        private
    }

    pub(crate) fn name(&self, decl: DeclId) -> String {
        self.names.get(decl).to_string()
    }

    /// Whether anything reads a local.
    fn is_read(&mut self, local: DeclId) -> bool {
        let p = self.p;
        let read = self.read_locals.get_or_insert_with(|| {
            (0..p.nodes.len())
                .filter_map(|i| match p.nodes.get(NodeId(i as u32)) {
                    Node::Local { local } => Some(local),
                    _ => None,
                })
                .collect()
        });
        read.contains(&local)
    }

    /// A loop's label, as a Python name: `outer`, or numbered, taking no
    /// declaration's name nor another label's.
    fn label(&mut self, label: DeclId) -> String {
        if let Some(name) = self.labels.get(&label) {
            return name.clone();
        }
        // Private at module level, as every module-level name but exports.
        let base = match self.in_def {
            0 => "_outer",
            _ => "outer",
        };
        let mut name = base.to_string();
        let mut n = 0;
        while self.names.has_name(&name) || self.labels.values().any(|l| *l == name) {
            n += 1;
            name = format!("{base}_{n}");
        }
        self.labels.insert(label, name.clone());
        name
    }

    pub(crate) fn qname(&self, decl: DeclId) -> String {
        self.qnames
            .get(decl)
            .map(|q| q.to_string())
            .unwrap_or_default()
    }

    /// A name `from` gives, imported unless it's a builtin.
    pub(crate) fn import_type(&mut self, from: &str, name: &str) -> String {
        if from != "builtins" {
            self.import(from, name)
        } else {
            name.to_string()
        }
    }

    /// A type as Python annotates it: quoted as a whole where it names
    /// one of the module's own classes or interfaces before it's defined.
    pub(crate) fn ann(&mut self, ty: TypeId) -> R<String> {
        let (text, quote) = self.ann_raw(ty)?;
        Ok(if quote {
            tree::string_literal(&text)
        } else {
            text
        })
    }

    /// A type's annotation, unquoted, and whether it needs quotes.
    pub(crate) fn ann_raw(&mut self, ty: TypeId) -> R<(String, bool)> {
        Ok(match self.p.types.get(ty) {
            TypeData::Scalar(s) => (
                match s {
                    Scalar::Void => "None",
                    Scalar::Boolean => "bool",
                    Scalar::Int32 | Scalar::Int64 => "int",
                    Scalar::Float64 => "float",
                    Scalar::String => "str",
                    Scalar::AnyValue => "object",
                    Scalar::Type => "type",
                }
                .to_string(),
                false,
            ),
            TypeData::Nominal { decl, args } => {
                let args = self.p.types.list(args).to_vec();
                let mut quote = false;
                let mut texts = Vec::new();
                for a in args {
                    let (t, q) = self.ann_raw(a)?;
                    quote |= q;
                    texts.push(t);
                }
                if self.p.decls.get(decl).facts.contains(Facts::FUN) {
                    // A `@fun` interface is its function type, as Kotlin's.
                    let Some(sig) = temper_tmpl::analysis::fun_signature(self.p, ty) else {
                        return not_yet("a @fun interface's signature");
                    };
                    return self.callable(sig);
                }
                let name = match self.qname(decl).as_str() {
                    "core.type Console" => self.import("temper_core", "LoggingConsole"),
                    // What a generator yields and is sent is the runtime's
                    // business, as `do_await`'s.
                    "core.type SafeGenerator" | "core.type Generator" => {
                        let generator = self.import_type("typing", "Generator");
                        let any = self.import_type("typing", "Any");
                        return Ok((format!("{generator}[{any}, {any}, None]"), quote));
                    }
                    // A step's result, as Kotlin's: a value as a 1-tuple,
                    // done as `None` (`connected::next_safe`).
                    "core.type GeneratorResult" => {
                        return Ok((format!("tuple[{}] | None", texts.join(", ")), quote));
                    }
                    "core.type ValueResult" => {
                        return Ok((format!("tuple[{}]", texts.join(", ")), quote));
                    }
                    "core.type DoneResult" => return Ok(("None".into(), false)),
                    _ if self.connected_type(decl) => match self.std_type(decl) {
                        Some(name) => name,
                        None => return not_yet("a connected type's annotation"),
                    },
                    q if is_external(self.p, decl) && library_of(self.p, decl) == "core" => {
                        let Some((from, name)) = core_type(q) else {
                            return not_yet(format!("a type annotation for {q}"));
                        };
                        self.import_type(from, name)
                    }
                    _ => match self.top(decl) {
                        Expr::Name(name) => {
                            quote |= !is_external(self.p, decl)
                                && self.is_here(decl)
                                && !self.defined.contains(&decl);
                            name
                        }
                        _ => return not_yet("a type annotation"),
                    },
                };
                if texts.is_empty() {
                    (name, quote)
                } else {
                    (format!("{name}[{}]", texts.join(", ")), quote)
                }
            }
            TypeData::Function { .. } => self.callable(ty)?,
            // A result that never returns, kept by `temper_tmpl::nevers`.
            TypeData::Never(_) => (self.import_type("typing", "NoReturn"), false),
            TypeData::Nullable(inner) => {
                let (inner, quote) = self.ann_raw(inner)?;
                (format!("{inner} | None"), quote)
            }
            TypeData::TypeParam { decl } => match self.type_vars.get(&decl) {
                _ if self.type_args.contains_key(&decl) => {
                    let arg = self.type_args[&decl];
                    // Not again, as `T` given as `List<T>`.
                    let saved = self.type_args.remove(&decl);
                    let text = self.ann_raw(arg);
                    self.type_args.insert(decl, saved.unwrap_or(arg));
                    text?
                }
                Some(name) => (name.clone(), false),
                None => return not_yet("a type parameter of another module's"),
            },
        })
    }

    /// A function type's annotation, `Callable[[A, B], R]`, and whether
    /// it needs quotes.
    fn callable(&mut self, sig: TypeId) -> R<(String, bool)> {
        let Some(s) = self.p.types.signature(sig) else {
            return not_yet("a function type without a signature");
        };
        if s.optional.contains(&true) {
            return not_yet("a function type with optional parameters");
        }
        let (params, result) = (s.params.to_vec(), s.result);
        let mut quote = false;
        let mut texts = Vec::new();
        for t in params {
            let (t, q) = self.ann_raw(t)?;
            quote |= q;
            texts.push(t);
        }
        let (result, q) = self.ann_raw(result)?;
        let callable = self.import("collections.abc", "Callable");
        Ok((
            format!("{callable}[[{}], {result}]", texts.join(", ")),
            quote | q,
        ))
    }

    /// Items' type parameters as `TypeVar`s, which Python declares
    /// at the top level: one per name and bound, numbered past names in
    /// use, but a generic method's own apart from its class's.
    fn type_vars(&mut self, items: &[NodeId]) -> R<Vec<Stmt>> {
        let p = self.p;
        self.type_vars.clear();
        let mut groups: Vec<(DeclId, Vec<DeclId>)> = Vec::new();
        for &item in items {
            match p.nodes.get(item) {
                Node::Function { decl, .. } => groups.push((DeclId::NONE, vec![decl])),
                Node::Class { decl, members } | Node::Interface { decl, members } => {
                    groups.push((DeclId::NONE, vec![decl]));
                    for &m in p.nodes.list(members) {
                        if let Node::Method { decl: md, .. }
                        | Node::AbstractMethod { decl: md, .. }
                        | Node::Function { decl: md, .. } = p.nodes.get(m)
                        {
                            groups.push((decl, vec![md]));
                        }
                    }
                }
                _ => {}
            }
        }
        // (name, bound) of each TypeVar made.
        let mut made: Vec<(String, String, Option<String>)> = Vec::new();
        let mut out = Vec::new();
        for (class, decls) in groups {
            let outer: Vec<String> = if class.is_none() {
                Vec::new()
            } else {
                (p.decls.type_params(class).iter())
                    .filter_map(|t| self.type_vars.get(t).cloned())
                    .collect()
            };
            for decl in decls {
                for &tp in p.decls.type_params(decl) {
                    let source = p.name(tp).to_string();
                    let bound = match p.decls.supers(tp) {
                        [] => None,
                        [b] if self.qname(nominal_decl(p, *b).unwrap_or(DeclId::NONE))
                            == "core.type MapKey" =>
                        {
                            None
                        }
                        [b] => Some(self.ann_raw(*b)?.0),
                        _ => return not_yet("a type parameter with more than one bound"),
                    };
                    // A function's type parameter that its signature names
                    // only as one parameter's type, as `x: T` with a
                    // `NoReturn` result, binds nothing, so it's its bound,
                    // as pyright's strict `reportInvalidTypeVarUse` wants.
                    let own = p.decls.get(decl).kind != DeclKind::Class
                        && p.decls.get(decl).kind != DeclKind::Interface;
                    if own && only_as_a_parameter(p, decl, p.decls.get(tp).ty) {
                        let plain = bound.clone().unwrap_or_else(|| "object".into());
                        self.type_vars.insert(tp, plain);
                        continue;
                    }
                    let shared = made
                        .iter()
                        .find(|(n, s, b)| *s == source && *b == bound && !outer.contains(n));
                    if let Some((name, _, _)) = shared {
                        self.type_vars.insert(tp, name.clone());
                        continue;
                    }
                    let taken = |n: &str| {
                        (p.decls.ids()).any(|d| self.names.get(d) == n)
                            || made.iter().any(|(m, _, _)| m == n)
                    };
                    // Private, as every module-level name but exports.
                    let base = match source.starts_with('_') {
                        true => source.clone(),
                        false => format!("_{source}"),
                    };
                    let mut name = base.clone();
                    let mut n = 2;
                    while taken(&name) {
                        name = format!("{base}_{n}");
                        n += 1;
                    }
                    let type_var = self.import("typing", "TypeVar");
                    let mut args = vec![Expr::Str(name.clone())];
                    if let Some(b) = &bound {
                        args.push(Expr::name(format!("bound={}", tree::string_literal(b))));
                    }
                    out.push(Stmt::Assign {
                        target: Expr::name(&name),
                        ann: None,
                        value: Expr::name(type_var).call(args),
                    });
                    self.type_vars.insert(tp, name.clone());
                    made.push((name, source, bound));
                }
            }
        }
        Ok(out)
    }

    pub fn module(&mut self, module: &Module) -> R<tree::Module> {
        self.here = module.decl;
        self.tested = DeclId::NONE;
        self.test_parts = TestParts::default();
        self.defined.clear();
        let roots: Vec<NodeId> = (module.items.iter().copied())
            .chain([module.init])
            .collect();
        self.own = self.module_level_names(module, &module.items, &roots);
        let mut body = self.type_vars(&module.items)?;
        self.items(module, &module.items, &mut body)?;
        self.init_block(module.decl, module.init, &mut body)?;
        self.qualify_builtins(&mut body);
        tree::mark_unused(&mut body);
        let exports = self.exports(module);
        let mut tree = self.finish(body);
        tree.host = host_file(self.p, module).map(|f| f.text.clone());
        tree.exports = Some(exports);
        Ok(tree)
    }

    /// A file's functions, classes, and interfaces, of `items`: a
    /// module's, or its test items, in a tests file. Module `let`s are
    /// declared where an init block initializes them.
    fn items(&mut self, module: &Module, items: &[NodeId], body: &mut Vec<Stmt>) -> R<()> {
        let mut done = BTreeSet::new();
        let host = host_file(self.p, module);
        for &item in items {
            match self.p.nodes.get(item) {
                Node::Function { decl, params, .. } | Node::BodilessFunction { decl, params }
                    if uses_host(self.p, decl, NAMES) =>
                {
                    if host.is_none() {
                        return not_yet(format!(
                            "{}'s host code, with no {}",
                            self.p.name(decl),
                            host_path(module)
                        ));
                    }
                    if self.here.is_none() {
                        return not_yet(format!(
                            "host code for what only tests reach, {}",
                            self.p.name(decl)
                        ));
                    }
                    body.push(self.host_wrapper(item, decl, params)?);
                }
                Node::Function { decl, .. } => {
                    let name = self.name(decl);
                    body.push(self.function(item, name, Vec::new())?);
                }
                // std's, which the connection table has (`std_net_send`).
                Node::BodilessFunction { decl, .. } if self.connected_call(decl) => {}
                Node::BodilessFunction { decl, .. } => {
                    return not_yet(format!("a connected function, {}", self.p.name(decl)));
                }
                // A user's connected class that host code defines is a name
                // for it, as Kotlin's: `Counter = _connected.Counter`.
                Node::Class { decl, .. } if uses_host(self.p, decl, NAMES) => {
                    if host.is_none() {
                        return not_yet(format!(
                            "{}'s host code, with no {}",
                            self.p.name(decl),
                            host_path(module)
                        ));
                    }
                    if self.here.is_none() {
                        return not_yet(format!(
                            "host code for what only tests reach, {}",
                            self.p.name(decl)
                        ));
                    }
                    let host_name = self.p.name(decl).to_string();
                    body.push(Stmt::Assign {
                        target: Expr::Name(self.name(decl)),
                        ann: None,
                        value: Expr::Attr(Box::new(Expr::Name(CONNECTED.into())), host_name),
                    });
                    self.defined.insert(decl);
                    done.insert(decl);
                }
                // A `@fun` interface is only its function type.
                Node::Interface { decl, .. }
                    if self.p.decls.get(decl).facts.contains(Facts::FUN) => {}
                // A std class connected to a Python type (`Date`, as
                // `datetime.date`) isn't declared, as Kotlin's.
                Node::Class { decl, .. } | Node::Interface { decl, .. }
                    if self.connected_type(decl) =>
                {
                    done.insert(decl);
                }
                Node::Class { .. } | Node::Interface { .. } => {
                    self.class_after_supers(item, &mut done, body)?;
                }
                // Declared where its init block initializes it.
                _ => {}
            }
        }
        Ok(())
    }

    /// An init block's statements, at a file's top level: a module's, or
    /// its test init, in a tests file, which continues it.
    fn init_block(&mut self, module: DeclId, init: NodeId, body: &mut Vec<Stmt>) -> R<()> {
        self.bindings = self.bindings_of(module, init, &[]);
        let done = self.block(init, body);
        self.bindings = Bindings::default();
        done
    }

    /// The names a module exports, for `__all__`, in order: its exported
    /// functions, classes, interfaces, and `let`s, but not a `@fun`
    /// interface, which is only its function type, nor a std class
    /// connected to a Python type, which isn't declared.
    fn exports(&self, module: &Module) -> Vec<String> {
        let p = self.p;
        (module.items.iter())
            .filter_map(|&item| match p.nodes.get(item) {
                Node::Function { decl, .. }
                | Node::BodilessFunction { decl, .. }
                | Node::ModuleLet { decl }
                | Node::Class { decl, .. }
                | Node::Interface { decl, .. } => Some(decl),
                _ => None,
            })
            .filter(|&decl| {
                let facts = p.decls.get(decl).facts;
                facts.contains(Facts::EXPORTED)
                    && !facts.contains(Facts::FUN)
                    && !self.connected_type(decl)
            })
            .map(|decl| self.name(decl))
            .collect()
    }

    /// A function whose host code runs instead of its Temper body, as
    /// Kotlin's: its parameters' defaults, then a call of the host's
    /// function of its name in snake_case (`_connected.sum(i, j, bonus)`),
    /// passing each parameter, defaulted.
    fn host_wrapper(&mut self, item: NodeId, decl: DeclId, params: temper_tmpl::Kids) -> R<Stmt> {
        let p = self.p;
        let name = self.name(decl);
        let mut def = self.def(item, decl, params, None, name, Vec::new())?;
        let defaults = match p.nodes.get(item) {
            Node::Function { body, .. } => analysis::defaults(p, body),
            _ => Vec::new(),
        };
        let mut stmts = Vec::new();
        self.in_def += 1;
        let temps = std::mem::take(&mut self.temps);
        let mut done = Ok(());
        for d in &defaults {
            done = done.and_then(|()| self.stmt(d.stmt, &mut stmts));
        }
        self.temps = temps;
        self.in_def -= 1;
        done?;
        let args: Vec<Expr> = (p.nodes.list(params).iter())
            .filter_map(|&param| match p.nodes.get(param) {
                Node::Param { decl } => Some(decl),
                _ => None,
            })
            .map(|param| {
                let local = (defaults.iter()).find(|d| d.param == param);
                Expr::Name(self.name(local.map_or(param, |d| d.local)))
            })
            .collect();
        let host = snake_case(p.name(decl));
        let callee = Expr::Attr(Box::new(Expr::Name(CONNECTED.into())), host);
        stmts.push(Stmt::Return(Some(Expr::Call(Box::new(callee), args))));
        if let Stmt::Def { body, .. } = def.uncommented_mut() {
            *body = stmts;
        }
        Ok(def)
    }

    /// The names of the file's own module `var`s that a function (or a
    /// test) assigns, which it declares `global`; another module's it
    /// assigns through that module.
    fn globals(&self, function: NodeId) -> Vec<String> {
        (module_writes(self.p, function).into_iter())
            .filter(|&d| self.is_here(d))
            .map(|d| self.name(d))
            .collect()
    }

    /// A function, named, its parameters' names and annotations first,
    /// with `self` first for a method or constructor (`params` itself not
    /// including it); its body declares any module `var` it assigns
    /// `global`.
    pub(crate) fn def(
        &mut self,
        item: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: Option<NodeId>,
        name: String,
        decorators: Vec<String>,
    ) -> R<Stmt> {
        let kind = self.p.decls.get(decl).kind;
        let is_method = matches!(
            kind,
            DeclKind::Method | DeclKind::Getter | DeclKind::Setter | DeclKind::Constructor
        );
        let mut ps = Vec::new();
        if is_method {
            ps.push(("self".to_string(), String::new()));
        }
        let list = self.p.nodes.list(params);
        let first_optional = list.len() - trailing_optional(self.p, params);
        for (i, &param) in list.iter().enumerate() {
            if let Node::Param { decl } = self.p.nodes.get(param) {
                let ty = self.p.decls.get(decl).ty;
                let mut ann = self.ann(ty)?;
                // An optional parameter defaults to null, which its body
                // replaces by its default, as Kotlin's; only those after
                // the last required one, as Python needs.
                if i >= first_optional {
                    ann.push_str(" = None");
                }
                ps.push((self.name(decl), ann));
            }
        }
        let result = match self.p.signature(decl) {
            _ if kind == DeclKind::Constructor => "None".into(),
            Some(sig) => self.ann(sig.result)?,
            None => "None".into(),
        };
        // A constructor's parameters may be passed by name, as Kotlin's.
        let positional = if kind == DeclKind::Constructor {
            1
        } else {
            ps.len()
        };
        let mut stmts = Vec::new();
        match body {
            Some(block) => {
                let globals = self.globals(item);
                if !globals.is_empty() {
                    stmts.push(Stmt::Global(globals));
                }
                self.in_def += 1;
                let temps = std::mem::take(&mut self.temps);
                let param_decls: Vec<DeclId> = (self.p.nodes.list(params).iter())
                    .filter_map(|&param| match self.p.nodes.get(param) {
                        Node::Param { decl } => Some(decl),
                        _ => None,
                    })
                    .collect();
                let bindings = self.bindings_of(decl, block, &param_decls);
                let bindings = std::mem::replace(&mut self.bindings, bindings);
                // What the statement around a literal hoists stays there,
                // out of the literal's body.
                let pre = std::mem::take(&mut self.pre);
                // A type parameter only the body mentions (`new
                // ListBuilder<T>()` in `countOf<T>(n: Int)`) is unbound
                // there for mypy and pyright, so its bound stands in.
                let saved_args = self.type_args.clone();
                for (tp, bound) in self.body_only_type_params(decl) {
                    self.type_args.insert(tp, bound);
                }
                let body = self.block(block, &mut stmts);
                self.type_args = saved_args;
                self.pre = pre;
                self.bindings = bindings;
                self.temps = temps;
                self.in_def -= 1;
                body?;
            }
            None => stmts.push(Stmt::Expr(Expr::Ellipsis)),
        }
        let def = Stmt::Def {
            decorators,
            name,
            params: ps,
            positional,
            result,
            body: stmts,
        };
        Ok(match lone_result_type_var(self.p, decl) {
            Some(rule) => tree::commented(def, rule),
            None => def,
        })
    }

    /// The bindings of the locals of `owner`, a function or module, in
    /// `root`, its body or init, after `params`, which are bound first.
    fn bindings_of(&self, owner: DeclId, root: NodeId, params: &[DeclId]) -> Bindings {
        let p = self.p;
        let mut decls: Vec<DeclId> = params.to_vec();
        let mut assigned = BTreeSet::new();
        analysis::walk(p, root, &mut |id| match p.nodes.get(id) {
            Node::Let { local, .. } if p.decls.get(local).owner == owner => decls.push(local),
            Node::Assign { local, .. } if p.decls.get(local).owner == owner => {
                assigned.insert(local);
            }
            _ => {}
        });
        let mut by_name: BTreeMap<String, Vec<DeclId>> = BTreeMap::new();
        for d in decls {
            by_name.entry(self.name(d)).or_default().push(d);
        }
        let rebound = (by_name.into_iter())
            .filter(|(_, ds)| ds.len() > 1 || ds.iter().any(|d| assigned.contains(d)))
            .map(|(name, ds)| {
                let ty = |d: &DeclId| p.decls.get(*d).ty;
                let wide = ds.iter().map(ty).find(|&t| p.types.is_nullable(t));
                (name, wide.unwrap_or_else(|| ty(&ds[0])))
            })
            .collect();
        let bound = params.iter().map(|&d| self.name(d)).collect();
        Bindings {
            owner,
            rebound,
            bound,
        }
    }

    /// A local's name's widest type, if its scope binds the name more
    /// than once.
    fn rebound(&self, local: DeclId) -> Option<TypeId> {
        if self.p.decls.get(local).owner != self.bindings.owner {
            return None;
        }
        self.bindings.rebound.get(&self.name(local)).copied()
    }

    /// Whether a type is core's `ListBuilder`'s, `MapBuilder`'s, or
    /// `Deque`'s, whose element types are invariant.
    fn mutable_collection(&self, ty: TypeId) -> bool {
        match self.p.types.get(ty) {
            TypeData::Nominal { decl, .. } => matches!(
                self.qname(decl).as_str(),
                "core.type ListBuilder" | "core.type MapBuilder" | "core.type Deque"
            ),
            _ => false,
        }
    }

    /// Whether checkers may infer a value of a type, unannotated, as a
    /// narrower type than its annotation: a nullable one's as never null;
    /// an interface's, or `AnyValue`'s, as the class of the value; a
    /// generic one's, as a tuple, a list literal's, or a class's instance
    /// with its arguments' types (`tuple[int, int]` for `Sequence[int]`,
    /// `Pair[str, tuple[int]]`); or a function type's. Temper's classes
    /// have no subclasses, so a value's type is its class.
    fn may_infer_narrower(&self, ty: TypeId) -> bool {
        let p = self.p;
        match p.types.get(ty) {
            TypeData::Nominal { decl, args } => {
                !p.types.list(args).is_empty()
                    || p.decls.get(decl).kind == DeclKind::Interface
                    || p.decls.get(decl).facts.contains(Facts::FUN)
            }
            TypeData::Scalar(s) => s == Scalar::AnyValue,
            TypeData::TypeParam { .. } => false,
            _ => true,
        }
    }

    /// A function's or method's own type parameters that appear in none
    /// of its parameters' types nor its result's, each with its bound
    /// (`AnyValue` if none).
    fn body_only_type_params(&self, decl: DeclId) -> Vec<(DeclId, TypeId)> {
        let p = self.p;
        let Some(sig) = p.signature(decl) else {
            return Vec::new();
        };
        (p.decls.type_params(decl).iter())
            .filter(|&&tp| {
                let f = p.decls.get(tp).ty;
                !p.types.mentions(sig.result, f)
                    && !sig.params.iter().any(|&t| p.types.mentions(t, f))
            })
            .map(|&tp| {
                let bound = p.decls.supers(tp).first().copied();
                (tp, bound.unwrap_or(TypeId::ANY_VALUE))
            })
            .collect()
    }

    /// A function literal: a lambda where its body is one `return` of an
    /// expression that needs no statements, else a nested `def` before
    /// the statement it's in, declaring `nonlocal` (or, for the init
    /// block's, `global`) what it assigns of the locals around it.
    fn literal(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: NodeId,
    ) -> R<Expr> {
        let p = self.p;
        if p.decls.get(decl).facts.contains(Facts::GENERATOR) {
            return self.generator(id, decl, params, body);
        }
        // A loop's locals it captures are bound by a factory, each call of
        // which makes a closure of that time round's values.
        let bound: Vec<DeclId> = (p.decls.captures(decl).iter().copied())
            .filter(|c| self.loop_lets.contains(c))
            .collect();
        if !bound.is_empty() {
            return self.bound_literal(id, decl, params, body, &bound);
        }
        let (nonlocal, global) = self.captures_assigned(decl, body);
        let stmts = match p.nodes.get(body) {
            Node::Block { stmts } => p.nodes.list(stmts).to_vec(),
            _ => Vec::new(),
        };
        let writes = module_writes(p, id);
        // One passed where core's method wants a function that gives back
        // its receiver's type parameter, as `reduce`'s `fn (T, T): T`, is a
        // `def`, as Kotlin's always are: a lambda's parameters take the
        // type pyright solves from the receiver, which it may narrow to
        // literals (`Literal[2, 3, 4]`) that the result then doesn't fit,
        // where a `def`'s annotations widen it.
        let widens = self.widen.contains(&id);
        if let ([ret], true, true, true, false) = (
            stmts.as_slice(),
            nonlocal.is_empty(),
            global.is_empty(),
            writes.is_empty(),
            widens,
        ) {
            if let Node::Return { value: Some(value) } = p.nodes.get(*ret) {
                let mark = self.pre.len();
                let walrus = std::mem::take(&mut self.walrus);
                self.in_def += 1;
                let temps = std::mem::take(&mut self.temps);
                let value = self.expr(value);
                self.temps = temps;
                self.in_def -= 1;
                self.walrus = walrus;
                let value = value?;
                if self.pre.len() == mark {
                    let names: Vec<String> = (p.nodes.list(params).iter())
                        .filter_map(|&param| match p.nodes.get(param) {
                            Node::Param { decl } => Some(self.name(decl)),
                            _ => None,
                        })
                        .collect();
                    return Ok(Expr::Lambda(names, Box::new(value)));
                }
                self.pre.truncate(mark);
            }
        }
        let name = self.def_name(self.name(decl));
        let def = self.literal_def(id, decl, params, body, name.clone())?;
        self.pre.push(def);
        Ok(Expr::name(name))
    }

    /// A function literal as a nested `def` named `name`, declaring
    /// `nonlocal` (or, for the init block's, `global`) what it assigns of
    /// the locals around it.
    fn literal_def(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: NodeId,
        name: String,
    ) -> R<Stmt> {
        let (nonlocal, global) = self.captures_assigned(decl, body);
        let mut def = self.def(id, decl, params, Some(body), name, Vec::new())?;
        if let Stmt::Def { body, .. } = def.uncommented_mut() {
            if !nonlocal.is_empty() {
                body.insert(0, Stmt::Nonlocal(nonlocal));
            }
            if !global.is_empty() {
                match body.first_mut() {
                    Some(Stmt::Global(names)) => names.extend(global),
                    _ => body.insert(0, Stmt::Global(global)),
                }
            }
        }
        Ok(def)
    }

    /// A generator's literal, as Kotlin's: a `def` decorated by
    /// `adapt_generator_factory`, whose generator takes a `do_await`
    /// first, which an `await` yields a call of, `yield do_await(p)`,
    /// and whose body has a `yield`, so Python makes it a generator.
    fn generator(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: NodeId,
    ) -> R<Expr> {
        let p = self.p;
        if !p
            .decls
            .captures(decl)
            .iter()
            .all(|c| !self.loop_lets.contains(c))
        {
            return not_yet("a generator that captures a loop's local");
        }
        let awaiter = self.fresh("do_await");
        let name = self.def_name(self.name(decl));
        self.awaiters.push(awaiter.clone());
        let def = self.literal_def(id, decl, params, body, name.clone());
        self.awaiters.pop();
        let mut def = def?;
        let adapt = self.import("temper_core", "adapt_generator_factory");
        let future = self.import_type("concurrent.futures", "Future");
        let any = self.import_type("typing", "Any");
        let callable = self.import("collections.abc", "Callable");
        if let Stmt::Def {
            decorators,
            params: ps,
            positional,
            body: stmts,
            ..
        } = def.uncommented_mut()
        {
            decorators.push(adapt);
            ps.insert(
                0,
                (awaiter, format!("{callable}[[{future}[{any}]], {any}]")),
            );
            *positional += 1;
            if !pauses(p, body) {
                stmts.insert(
                    0,
                    Stmt::If {
                        cond: Expr::Bool(false),
                        then: vec![Stmt::Expr(Expr::Yield(None))],
                        els: Vec::new(),
                    },
                );
            }
        }
        self.pre.push(def);
        Ok(Expr::name(name))
    }

    /// A name for a new local, past any name in use.
    fn fresh(&mut self, base: &str) -> String {
        let mut n = 0;
        loop {
            let name = if n == 0 {
                base.to_string()
            } else {
                format!("{base}_{n}")
            };
            n += 1;
            let taken = (self.p.decls.ids()).any(|d| self.names.get(d) == name)
                || self.modules.values().any(|a| *a == name)
                || self.awaiters.contains(&name);
            if !taken {
                return name;
            }
        }
    }

    /// A literal that captures a loop's locals: made by a factory that
    /// takes them, `def make_fn(x: int, /) -> Callable[[], int]: return
    /// lambda: x`, called where the literal is, `make_fn(x)`, since
    /// Python's closures share a loop's variable across its iterations
    /// where Temper's `let` is fresh each time.
    fn bound_literal(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: NodeId,
        bound: &[DeclId],
    ) -> R<Expr> {
        let p = self.p;
        let (nonlocal, _) = self.captures_assigned(decl, body);
        if bound.iter().any(|&c| nonlocal.contains(&self.name(c))) {
            return not_yet("a closure that assigns a loop's local");
        }
        let saved: Vec<DeclId> = bound.to_vec();
        for c in &saved {
            self.loop_lets.remove(c);
        }
        let mark = self.pre.len();
        let inner = self.literal(id, decl, params, body);
        for &c in &saved {
            self.loop_lets.insert(c);
        }
        let inner = inner?;
        let mut stmts: Vec<Stmt> = self.pre.drain(mark..).collect();
        stmts.push(Stmt::Return(Some(inner)));
        let mut ps = Vec::new();
        for &c in bound {
            ps.push((self.name(c), self.ann(p.decls.get(c).ty)?));
        }
        let name = self.def_name(format!("make_{}", self.name(decl)));
        let args = bound.iter().map(|&c| Expr::name(self.name(c))).collect();
        let result = self.ann(p.nodes.ty(id))?;
        self.pre.push(Stmt::Def {
            decorators: Vec::new(),
            name: name.clone(),
            positional: ps.len(),
            params: ps,
            result,
            body: stmts,
        });
        Ok(Expr::name(name).call(args))
    }

    /// The locals a loop's body declares, outside literals in it, now in
    /// a loop being translated; those that weren't yet.
    fn enter_loop(&mut self, body: NodeId) -> Vec<DeclId> {
        let p = self.p;
        let mut added = Vec::new();
        let mut stack = vec![body];
        while let Some(n) = stack.pop() {
            match p.nodes.get(n) {
                Node::FunctionLiteral { .. } => continue,
                Node::Let { local, .. } if self.loop_lets.insert(local) => added.push(local),
                _ => {}
            }
            stack.extend(p.nodes.kids(n));
        }
        added
    }

    fn leave_loop(&mut self, added: Vec<DeclId>) {
        for d in added {
            self.loop_lets.remove(&d);
        }
    }

    /// What a literal's body assigns, not in literals inside it, of the
    /// locals around it: those of functions, then those of the init
    /// block, which are module-level in Python.
    fn captures_assigned(&self, decl: DeclId, body: NodeId) -> (Vec<String>, Vec<String>) {
        let p = self.p;
        let (mut nonlocal, mut global) = (Vec::new(), Vec::new());
        let mut stack: Vec<NodeId> = vec![body];
        while let Some(n) = stack.pop() {
            match p.nodes.get(n) {
                Node::FunctionLiteral { .. } => continue,
                Node::Assign { local, .. } if p.decls.get(local).owner != decl => {
                    let owner = p.decls.get(local).owner;
                    let list = if p.decls.get(owner).kind == DeclKind::Module {
                        &mut global
                    } else {
                        &mut nonlocal
                    };
                    let name = self.name(local);
                    if !list.contains(&name) {
                        list.push(name);
                    }
                }
                _ => {}
            }
            stack.extend(p.nodes.kids(n));
        }
        (nonlocal, global)
    }

    /// A function node: top-level, or a class's static one.
    pub(crate) fn function(
        &mut self,
        item: NodeId,
        name: String,
        decorators: Vec<String>,
    ) -> R<Stmt> {
        let Node::Function {
            decl, params, body, ..
        } = self.p.nodes.get(item)
        else {
            return not_yet("a function that isn't one");
        };
        self.def(item, decl, params, Some(body), name, decorators)
    }

    /// Reaches the builtins a function's own names hide through
    /// `builtins` (`tree::qualify_builtins`), imported, where any is, as
    /// `_builtins` (in a tests file, `builtins`), past any name in use.
    fn qualify_builtins(&mut self, body: &mut [Stmt]) {
        let mut alias = match self.here.is_none() {
            true => "builtins".to_string(),
            false => "_builtins".to_string(),
        };
        while self.own.contains(&alias)
            || self.names.has_name(&alias)
            || self.aliases.values().any(|a| *a == alias)
            || self.modules.values().any(|a| *a == alias)
            || self.type_vars.values().any(|a| *a == alias)
        {
            alias.push('_');
        }
        if tree::qualify_builtins(body, crate::names::BUILTINS, &alias) {
            self.modules.insert("builtins".into(), alias);
        }
    }

    /// The file's imports, taken, with its body.
    fn finish(&mut self, body: Vec<Stmt>) -> tree::Module {
        let imports = std::mem::take(&mut self.imports);
        self.aliases.clear();
        let imports = (imports.into_iter())
            .map(|(from, names)| (from, names.into_iter().collect()))
            .collect();
        let modules = std::mem::take(&mut self.modules).into_iter().collect();
        tree::Module {
            modules,
            imports,
            host: None,
            exports: None,
            body,
        }
    }

    /// A module's tests, as a `unittest` file: what only tests reach (its
    /// test items, and its test init, which continues the module's init
    /// here), then one `TestCase` class, named for the module, with a
    /// method per test, as `test_adds` for "adds", whose docstring is the
    /// test's name. Each makes std's `Test`, runs the test's body, and
    /// then, even if the body raised, fails if the `Test` has failures
    /// (`softFailToHard`), as Kotlin's do. What they use of the module,
    /// its private names too, is imported from it, as Kotlin's do. `None`
    /// if the module has no tests.
    pub fn tests(&mut self, module: &Module, class: &str) -> R<Option<tree::Module>> {
        if module.tests.is_empty() {
            return Ok(None);
        }
        self.here = DeclId::NONE;
        self.tested = module.decl;
        self.test_parts = analysis::test_parts(self.p, module);
        if self.test_parts.assign_init_locals(self.p) {
            return not_yet("tests that assign a local of their module's init block");
        }
        self.defined.clear();
        let roots: Vec<NodeId> = module.test_roots().collect();
        self.own = self.module_level_names(module, &module.test_items, &roots);
        let mut body = self.type_vars(&module.test_items)?;
        self.items(module, &module.test_items, &mut body)?;
        if let Some(init) = module.test_init {
            self.init_block(module.decl, init, &mut body)?;
        }
        let mut methods = Vec::new();
        let mut used = BTreeSet::new();
        for &test in &module.tests {
            let Node::Test {
                decl,
                name,
                params,
                body,
            } = self.p.nodes.get(test)
            else {
                continue;
            };
            let text = self.p.strings.get(name).to_string();
            let [param] = self.p.nodes.list(params)[..] else {
                return not_yet("a test without one parameter");
            };
            let Node::Param { decl: param } = self.p.nodes.get(param) else {
                return not_yet("a test's parameter");
            };
            let test_class = match self.ann(self.p.decls.get(param).ty)? {
                ann if ann == "Test" => ann,
                _ => return not_yet("a test's parameter that isn't std's Test"),
            };
            let mut stmts = Vec::new();
            self.in_def += 1;
            let temps = std::mem::take(&mut self.temps);
            self.bindings = self.bindings_of(decl, body, &[param]);
            let translated = self.block(body, &mut stmts);
            self.bindings = Bindings::default();
            self.temps = temps;
            self.in_def -= 1;
            translated?;
            let test_var = self.name(param);
            let soft_fail = Expr::name(&test_var).attr("soft_fail_to_hard").call(vec![]);
            let method = test_method_name(&text, &mut used);
            let mut method_body = vec![Stmt::Expr(Expr::Str(text))];
            // The file's own `var`s it assigns, as what only tests reach.
            let globals = self.globals(test);
            if !globals.is_empty() {
                method_body.push(Stmt::Global(globals));
            }
            method_body.push(Stmt::Assign {
                target: Expr::name(test_var),
                ann: Some(test_class.clone()),
                value: Expr::name(test_class).call(vec![]),
            });
            method_body.push(Stmt::Try {
                body: stmts,
                except: None,
                finally: vec![Stmt::Expr(soft_fail)],
            });
            methods.push(Stmt::Def {
                decorators: Vec::new(),
                name: method,
                params: vec![("self".into(), String::new())],
                positional: 1,
                result: "None".into(),
                body: method_body,
            });
        }
        self.import("unittest", "TestCase");
        body.push(Stmt::Class {
            name: class.to_string(),
            bases: vec!["TestCase".into()],
            body: methods,
        });
        self.qualify_builtins(&mut body);
        tree::mark_unused(&mut body);
        let tests = self.finish(body);
        self.tested = DeclId::NONE;
        self.test_parts = TestParts::default();
        Ok(Some(tests))
    }

    pub(crate) fn block(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        match self.p.nodes.get(id) {
            Node::Block { stmts } => {
                for &s in self.p.nodes.list(stmts) {
                    self.stmt(s, out)?;
                }
                Ok(())
            }
            _ => self.stmt(id, out),
        }
    }

    /// A nested block's statements, apart from any statements hoisted
    /// for the statement around it.
    fn stmts(&mut self, id: NodeId) -> R<Vec<Stmt>> {
        let saved = std::mem::take(&mut self.pre);
        let mut out = Vec::new();
        let done = self.block(id, &mut out);
        self.pre = saved;
        done?;
        Ok(out)
    }

    /// Takes the hoisted statements into `out`, before the statement
    /// that needs them.
    fn flush(&mut self, out: &mut Vec<Stmt>) {
        out.append(&mut self.pre);
    }

    /// An expression translated apart: the statements it needs first, and
    /// it.
    fn apart(&mut self, id: NodeId) -> R<(Vec<Stmt>, Expr)> {
        let saved = std::mem::take(&mut self.pre);
        let e = self.expr(id);
        let pre = std::mem::replace(&mut self.pre, saved);
        Ok((pre, e?))
    }

    /// A fresh temporary's name: `t_N` in a function, `_t_N` at module
    /// level (private, as Kotlin's), past any name in use.
    fn temp(&mut self) -> String {
        loop {
            let n = self.temps;
            self.temps += 1;
            let name = if self.in_def > 0 {
                format!("t_{n}")
            } else {
                format!("_t_{n}")
            };
            let taken = (self.p.decls.ids()).any(|d| self.names.get(d) == name)
                || self.modules.values().any(|a| *a == name);
            if !taken {
                return name;
            }
        }
    }

    /// A value put where `sink` says, as statements: an `orelse` as
    /// `try`, `bubble()` and `panic()` as `raise`, else the value's
    /// statements and then the value.
    fn value_into(&mut self, id: NodeId, sink: Sink, out: &mut Vec<Stmt>) -> R<()> {
        let saved = std::mem::take(&mut self.pre);
        let done = self.value_into_apart(id, sink, out);
        self.pre = saved;
        done
    }

    fn value_into_apart(&mut self, id: NodeId, sink: Sink, out: &mut Vec<Stmt>) -> R<()> {
        match self.p.nodes.get(id) {
            Node::OrElse { body, fallback } => {
                let sink = match sink {
                    Sink::Assign(target, Some(ann)) => {
                        if let Expr::Name(name) = &target {
                            out.push(Stmt::Declare {
                                target: name.clone(),
                                ann,
                            });
                        }
                        Sink::Assign(target, None)
                    }
                    sink => sink,
                };
                let mut tried = Vec::new();
                let mut caught = Vec::new();
                let again = match &sink {
                    Sink::Return => Sink::Return,
                    Sink::Assign(t, ann) => Sink::Assign(t.clone(), ann.clone()),
                    Sink::Discard => Sink::Discard,
                };
                self.value_into(body, sink, &mut tried)?;
                self.value_into(fallback, again, &mut caught)?;
                out.push(Stmt::Try {
                    body: tried,
                    except: Some(caught),
                    finally: Vec::new(),
                });
            }
            Node::Bubble { .. } | Node::Error { .. } => {
                out.push(Stmt::Raise(Expr::builtin("RuntimeError").call(vec![])))
            }
            Node::Panic {} => out.push(panic()),
            _ => {
                let value = self.expr(id)?;
                self.flush(out);
                match sink {
                    Sink::Return => out.push(Stmt::Return(Some(value))),
                    Sink::Assign(target, ann) => out.push(Stmt::Assign { target, ann, value }),
                    Sink::Discard if is_simple(&value) => {}
                    Sink::Discard => out.push(Stmt::Expr(value)),
                }
            }
        }
        Ok(())
    }

    /// Expressions in evaluation order, `None` for each `Omitted`: where a
    /// later one hoists statements, earlier ones that aren't simple are
    /// kept in temporaries first, so they still run first.
    pub(crate) fn ordered(&mut self, ids: &[NodeId]) -> R<Vec<Option<Expr>>> {
        let mut out: Vec<(Option<Expr>, usize)> = Vec::new();
        for &a in ids {
            let e = match self.p.nodes.get(a) {
                Node::Omitted {} => None,
                _ => Some(self.expr(a)?),
            };
            out.push((e, self.pre.len()));
        }
        let end = self.pre.len();
        for j in (0..out.len()).rev() {
            let at = out[j].1;
            let simple = out[j].0.as_ref().is_none_or(is_simple);
            if at == end || simple || out[j + 1..].iter().all(|(_, m)| *m == at) {
                continue;
            }
            let t = self.temp();
            let value = out[j].0.take().unwrap();
            self.pre.insert(
                at,
                Stmt::Assign {
                    target: Expr::name(&t),
                    ann: None,
                    value,
                },
            );
            out[j].0 = Some(Expr::name(t));
        }
        Ok(out.into_iter().map(|(e, _)| e).collect())
    }

    /// A temporary for a value that needs statements: declared here, then
    /// set by `set`.
    fn temp_of(&mut self, ty: TypeId) -> R<String> {
        let t = self.temp();
        let ann = self.ann(ty)?;
        self.pre.push(Stmt::Declare {
            target: t.clone(),
            ann,
        });
        Ok(t)
    }

    /// A `let` of a parameter's value coalesced with its default, whose
    /// local has the parameter's name, so the default may replace the
    /// parameter's null in place: the parameter and the default.
    fn in_place_default(&self, local: DeclId, init: NodeId) -> Option<(DeclId, NodeId)> {
        let Node::Coalesce { left, right } = self.p.nodes.get(init) else {
            return None;
        };
        let Node::Local { local: param } = self.p.nodes.get(left) else {
            return None;
        };
        (self.p.decls.get(param).kind == DeclKind::Param && self.name(param) == self.name(local))
            .then_some((param, right))
    }

    fn stmt(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let outer = std::mem::take(&mut self.var_null_check);
        let start = out.len();
        let done = self.stmt_inner(id, out);
        // pyright narrows a `var` by what's assigned to it, so a later
        // null check may look unneeded to it, though another path may
        // assign null.
        if std::mem::replace(&mut self.var_null_check, outer) && out.len() > start {
            let last = out.pop().unwrap();
            let rule = "pyright: ignore[reportUnnecessaryComparison]".to_string();
            out.push(Stmt::Commented(Box::new(last), rule));
        }
        done
    }

    fn stmt_inner(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Block { .. } => self.block(id, out)?,
            // A local function: a `def` of the local's name.
            Node::Let {
                local,
                init: Some(init),
            } if !p.decls.get(local).facts.contains(Facts::VAR)
                && matches!(p.nodes.get(init), Node::FunctionLiteral { .. }) =>
            {
                let Node::FunctionLiteral { decl, params, body } = p.nodes.get(init) else {
                    unreachable!()
                };
                let name = self.name(local);
                self.bindings.bound.insert(name.clone());
                let def = self.literal_def(init, decl, params, body, name)?;
                out.append(&mut self.pre);
                out.push(def);
            }
            // A parameter's default, where the local shares the parameter's
            // name: `if x is None: x = …`, or nothing for a null default.
            Node::Let {
                local,
                init: Some(init),
            } if self.in_place_default(local, init).is_some() => {
                let (param, value) = self.in_place_default(local, init).unwrap();
                if !matches!(p.nodes.get(value), Node::Null {}) {
                    if p.types.is_nullable(p.decls.get(local).ty) {
                        self.defaulted.insert(local);
                    }
                    let name = Expr::name(self.name(param));
                    let mut then = Vec::new();
                    self.value_into(value, Sink::Assign(name.clone(), None), &mut then)?;
                    out.push(Stmt::If {
                        cond: Expr::bin(Bin::Is, name, Expr::None),
                        then,
                        els: Vec::new(),
                    });
                }
            }
            Node::Let { local, init } => {
                let target = self.name(local);
                match init {
                    Some(init) => {
                        // Nullable ones are annotated, so a later value
                        // fits, and those made empty by core's `ListBuilder`,
                        // `MapBuilder`, `Deque`, or `PromiseBuilder`, `[]`,
                        // `{}`, `deque()`, or `new_unbound_promise()`,
                        // whose element types checkers can't infer, and
                        // casts, as `cast_by_type` gives its argument's type
                        // where that's a supertype, also under `orelse`.
                        let ty = p.decls.get(local).ty;
                        let mut value = init;
                        while let Node::OrElse { body, .. } = p.nodes.get(value) {
                            value = body;
                        }
                        let empty = match p.nodes.get(value) {
                            Node::New { ctor, .. } => matches!(
                                self.qname(ctor).as_str(),
                                "core.type ListBuilder.constructor()"
                                    | "core.type MapBuilder.constructor()"
                                    | "core.type Deque.constructor()"
                                    | "core.type PromiseBuilder.constructor()"
                            ),
                            Node::As { .. } => true,
                            // Or a builder made from a list literal, which
                            // pyright may take for a list of its items'
                            // literal types (`list[Literal[0]]`).
                            _ => {
                                self.mutable_collection(ty)
                                    && (p.nodes.kids(value).iter())
                                        .any(|&k| matches!(p.nodes.get(k), Node::ListLit { .. }))
                            }
                        };
                        let ann = match self.rebound(local) {
                            // A name bound more than once: annotated only
                            // where first bound, and there only where its
                            // value may be narrower than its widest type,
                            // as a tuple is than a `Sequence`, a class than
                            // its interface, or a generic class's instance
                            // than its declared type arguments. One whose
                            // own type is narrower, never null, is declared
                            // first, so its value narrows it.
                            Some(wide) if self.bindings.bound.insert(target.clone()) => {
                                let narrower = empty
                                    || p.nodes.ty(init) != wide
                                    || self.may_infer_narrower(wide);
                                match narrower {
                                    true if ty != wide => {
                                        let ann = self.ann(wide)?;
                                        out.push(Stmt::Declare {
                                            target: target.clone(),
                                            ann,
                                        });
                                        None
                                    }
                                    true => Some(self.ann(wide)?),
                                    false => None,
                                }
                            }
                            Some(_) => None,
                            None if p.types.is_nullable(ty) || empty => Some(self.ann(ty)?),
                            None => None,
                        };
                        self.value_into(init, Sink::Assign(Expr::name(target.clone()), ann), out)?;
                        // A local nothing reads, as `whatIAm` in
                        // `classes/object-literals`, which pyright's strict
                        // mode reports.
                        if !self.is_read(local) {
                            let last = out.last_mut().filter(|s| {
                                matches!(s, Stmt::Assign { target: Expr::Name(n), .. } if *n == target)
                            });
                            if let Some(last) = last {
                                let s = std::mem::replace(last, Stmt::Break);
                                *last = Stmt::Commented(
                                    Box::new(s),
                                    "pyright: ignore[reportUnusedVariable]".to_string(),
                                );
                            }
                        }
                    }
                    // Not again where bound already.
                    None => {
                        let rebound = self.rebound(local);
                        if self.bindings.bound.insert(target.clone()) || rebound.is_none() {
                            let ann = self.ann(rebound.unwrap_or(p.decls.get(local).ty))?;
                            out.push(Stmt::Declare { target, ann });
                        }
                    }
                }
            }
            Node::Assign {
                local: target,
                value,
            } => {
                let target = Expr::name(self.name(target));
                self.value_into(value, Sink::Assign(target, None), out)?;
            }
            Node::SetModuleLet { target, value } => {
                let value = self.expr(value)?;
                let target = self.top(target);
                self.flush(out);
                out.push(Stmt::Assign {
                    target,
                    ann: None,
                    value,
                });
            }
            Node::InitLet { target, value } => {
                let ann = Some(self.ann(p.decls.get(target).ty)?);
                let target = Expr::name(self.name(target));
                self.value_into(value, Sink::Assign(target, ann), out)?;
            }
            Node::SetField {
                field,
                object,
                value,
            } => {
                let [object, value] = self.exprs(&[object, value])?;
                self.flush(out);
                out.push(Stmt::Assign {
                    target: self.field(object, field),
                    ann: None,
                    value,
                });
            }
            Node::InitStatic { target, value } => {
                // A private static function its value calls, as `last =
                // named("last")`, is used outside its class too.
                let mut used = target;
                temper_tmpl::analysis::walk(self.p, value, &mut |n| {
                    if let Node::CallFunction { callee, .. } = self.p.nodes.get(n) {
                        if self.is_member(callee) && self.private_use_needed(callee) {
                            used = callee;
                        }
                    }
                });
                let value = self.expr(value)?;
                self.flush(out);
                let stmt = Stmt::Assign {
                    target: self.static_ref(target),
                    ann: None,
                    value,
                };
                out.push(self.private_use(used, stmt));
            }
            Node::ExprStatement { expr } if self.std_statement(expr).is_some() => {
                let stmt = self.std_statement(expr).unwrap()?;
                self.flush(out);
                out.push(stmt);
            }
            Node::ExprStatement { expr } if self.is_setter_call(expr) => {
                let stmt = self.setter_call(expr)?;
                self.flush(out);
                out.push(stmt);
            }
            Node::If { cond, then, els } => {
                let cond = self.expr(cond)?;
                let then = self.stmts(then)?;
                let els = match els {
                    Some(els) => self.stmts(els)?,
                    None => Vec::new(),
                };
                self.flush(out);
                out.push(Stmt::If { cond, then, els });
            }
            Node::While { label, cond, body } => {
                // Jumps from loops inside, through the runtime's labels.
                let (breaks, continues) = outer_jumps(p, body, label);
                let (before, cond) = self.apart(cond)?;
                self.loops.push(label);
                let added = self.enter_loop(body);
                let body = self.stmts(body);
                self.leave_loop(added);
                self.loops.pop();
                let mut body = body?;
                let name = if breaks || continues {
                    self.label(label)
                } else {
                    String::new()
                };
                let context = match (breaks, continues) {
                    (true, true) => Some(Expr::name(&name).attr("continuing")),
                    (false, true) => Some(Expr::name(&name)),
                    _ => None,
                };
                if let Some(context) = context {
                    body = vec![Stmt::With {
                        context,
                        name: None,
                        body,
                    }];
                }
                let stmt = if before.is_empty() {
                    Stmt::While { cond, body }
                } else {
                    // The condition's statements run before each test.
                    let mut first = before;
                    first.push(Stmt::If {
                        cond: not(cond),
                        then: vec![Stmt::Break],
                        els: Vec::new(),
                    });
                    first.append(&mut body);
                    Stmt::While {
                        cond: Expr::Bool(true),
                        body: first,
                    }
                };
                let label_class = if breaks && continues {
                    "LabelPair"
                } else {
                    "Label"
                };
                let label_class = match breaks || continues {
                    true => self.import("temper_core", label_class),
                    false => label_class.to_string(),
                };
                let made = Expr::name(label_class).call(Vec::new());
                if breaks {
                    // `with Label() as outer: while …`.
                    out.push(Stmt::With {
                        context: made,
                        name: Some(name),
                        body: vec![stmt],
                    });
                } else {
                    if continues {
                        // `outer = Label()`, then `while …: with outer: …`.
                        out.push(Stmt::Assign {
                            target: Expr::name(name),
                            ann: None,
                            value: made,
                        });
                    }
                    out.push(stmt);
                }
            }
            Node::DoWhile { label, body, cond } => {
                let mut continues = false;
                temper_tmpl::analysis::walk(p, body, &mut |n| {
                    continues |=
                        matches!(p.nodes.get(n), Node::Continue { label: l } if l == label);
                });
                if continues {
                    return not_yet("`continue` in a `do … while` loop");
                }
                self.loops.push(label);
                let added = self.enter_loop(body);
                let body = self.stmts(body);
                self.leave_loop(added);
                self.loops.pop();
                let mut body = body?;
                let (mut before, cond) = self.apart(cond)?;
                body.append(&mut before);
                body.push(Stmt::If {
                    cond: not(cond),
                    then: vec![Stmt::Break],
                    els: Vec::new(),
                });
                out.push(Stmt::While {
                    cond: Expr::Bool(true),
                    body,
                });
            }
            Node::Break { label } | Node::Continue { label } => {
                let breaks = matches!(p.nodes.get(id), Node::Break { .. });
                if self.loops.last() != Some(&label) {
                    // Out of a loop around the innermost: `outer.break_()`.
                    if !self.loops.contains(&label) {
                        return not_yet("a jump out of a loop that isn't the innermost");
                    }
                    let method = if breaks { "break_" } else { "continue_" };
                    let jump = Expr::name(self.label(label)).attr(method).call(Vec::new());
                    out.push(Stmt::Expr(jump));
                    return Ok(());
                }
                out.push(if breaks { Stmt::Break } else { Stmt::Continue });
            }
            Node::Return { value: Some(value) } => self.value_into(value, Sink::Return, out)?,
            Node::Return { value: None } => out.push(Stmt::Return(None)),
            Node::ExprStatement { expr } => self.value_into(expr, Sink::Discard, out)?,
            Node::Yield { value } => {
                let value = match value {
                    Some(v) => Some(Box::new(self.expr(v)?)),
                    None => None,
                };
                out.push(Stmt::Expr(Expr::Yield(value)));
            }
            other => return not_yet(format!("a {} statement", other.kind().name())),
        }
        Ok(())
    }

    /// Arguments, with `None` for each left out.
    pub(crate) fn args(&mut self, args: &[NodeId]) -> R<Vec<Option<Expr>>> {
        self.ordered(args)
    }

    /// Expressions in evaluation order, none `Omitted`.
    pub(crate) fn exprs<const N: usize>(&mut self, ids: &[NodeId; N]) -> R<[Expr; N]> {
        let out = present(self.ordered(ids)?)?;
        Ok(out.try_into().unwrap_or_else(|_| unreachable!()))
    }

    /// Whether a call of a std member this backend connects goes through
    /// the connection table, a method's receiver its first argument, as
    /// `regex_compiled_found(self, compiled, text)`.
    fn connected_call(&self, decl: DeclId) -> bool {
        self.connected_std(decl)
            .is_some_and(|key| connected::lookup(&key).is_some())
    }

    /// A call of a connected declaration, by the connection table.
    fn connected(&mut self, decl: DeclId, args: Vec<Option<Expr>>) -> R<Expr> {
        // A std member by its key, as Kotlin's support network has it.
        let qname = match self.connected_std(decl) {
            Some(key) => key,
            None => self.qname(decl),
        };
        let Some(connection) = connected::lookup(&qname) else {
            return not_yet(format!("a connection for {qname}"));
        };
        let present = |args: Vec<Option<Expr>>| -> R<Vec<Expr>> {
            let mut out = Vec::new();
            let mut gap = false;
            for a in args {
                match a {
                    Some(_) if gap => return not_yet("an argument after one left out"),
                    Some(a) => out.push(a),
                    None => gap = true,
                }
            }
            Ok(out)
        };
        Ok(match connection {
            Connection::Builtin(name) => Expr::builtin(name).call(present(args)?),
            Connection::Method(name) => {
                let mut args = present(args)?;
                if args.is_empty() {
                    return not_yet(format!("a receiver for {qname}"));
                }
                let receiver = args.remove(0);
                receiver.attr(name).call(args)
            }
            Connection::Runtime(name) => {
                // The runtime's helpers take `None` for an argument left
                // out before one given (`list_builder_splice(b, None, 0)`),
                // as Kotlin's.
                let given = args.iter().rposition(Option::is_some).map_or(0, |i| i + 1);
                let args = (args.into_iter().take(given))
                    .map(|a| a.unwrap_or(Expr::None))
                    .collect();
                let name = self.import("temper_core", name);
                Expr::name(name).call(args)
            }
            Connection::Import(module, name) => {
                let name = self.import(module, name);
                Expr::name(name).call(present(args)?)
            }
            Connection::Identity => {
                let mut args = present(args)?;
                if args.is_empty() {
                    return not_yet(format!("a receiver for {qname}"));
                }
                args.remove(0)
            }
            Connection::Attribute(name) => {
                let mut args = present(args)?;
                if args.is_empty() {
                    return not_yet(format!("a receiver for {qname}"));
                }
                args.remove(0).attr(name)
            }
            Connection::Translate(f) => f(args, &mut |name: &str| match name.rsplit_once('.') {
                Some((from, name)) => self.import(from, name),
                None => self.import("temper_core", name),
            }),
        })
    }

    pub(crate) fn expr(&mut self, id: NodeId) -> R<Expr> {
        let p = self.p;
        Ok(match p.nodes.get(id) {
            Node::BoolLit { value } => Expr::Bool(value),
            Node::Int32Lit { value } => Expr::Int(value.into()),
            Node::Int64Lit { value } => Expr::Int(value),
            Node::Float64Lit { value } => Expr::Float(value),
            Node::StringLit { value } => Expr::Str(p.strings.get(value).to_string()),
            Node::VoidLit {} => Expr::None,
            // The class of the type's values: `type(None)` for `Void`.
            Node::TypeValue { of } => match p.types.get(of) {
                TypeData::Scalar(Scalar::Void) => Expr::builtin("type").call(vec![Expr::None]),
                TypeData::Scalar(s) => Expr::builtin(match s {
                    Scalar::Boolean => "bool",
                    Scalar::Int32 | Scalar::Int64 => "int",
                    Scalar::Float64 => "float",
                    Scalar::String => "str",
                    _ => return not_yet("this type as a value"),
                }),
                _ => return not_yet("this type as a value"),
            },
            Node::Local { local: decl } => match self.walrus.remove(&decl) {
                // A walrus can't annotate, so one that binds a name bound
                // again later is declared first where its value may be
                // narrower.
                Some((value, ty)) => {
                    let name = self.name(decl);
                    if let Some(wide) = self.rebound(decl) {
                        let narrower =
                            ty != wide || self.may_infer_narrower(self.p.types.non_null(wide));
                        if self.bindings.bound.insert(name.clone()) && narrower {
                            let ann = self.ann(wide)?;
                            self.pre.push(Stmt::Declare {
                                target: name.clone(),
                                ann,
                            });
                        }
                    }
                    Expr::Walrus(name, Box::new(value))
                }
                // The module's init block's, in its tests file, imported.
                None if self.test_parts.used.contains(&decl) => {
                    let name = self.name(decl);
                    let module = self.py_module(self.tested);
                    self.imports.entry(module).or_default().insert(name.clone());
                    Expr::name(name)
                }
                None => Expr::name(self.name(decl)),
            },
            Node::GetModuleLet { target: decl } => self.top(decl),
            Node::CallFunction { callee, args, .. } => {
                let args = self.args(p.nodes.list(args))?;
                if is_external(p, callee) && library_of(p, callee) == "core"
                    || self.connected_call(callee)
                {
                    return self.connected(callee, args);
                }
                let present = present(args)?;
                if self.is_member(callee) {
                    let class = self.p.decls.get(callee).owner;
                    let name = self.member_name(callee);
                    return Ok(self.class_ref(class).attr(name).call(present));
                }
                self.top(callee).call(present)
            }
            Node::CallMethod {
                method,
                receiver,
                args,
                ..
            } => {
                if self.connected_call(method) {
                    let mut ids = vec![receiver];
                    ids.extend_from_slice(p.nodes.list(args));
                    let all = self.ordered(&ids)?;
                    return self.connected(method, all);
                }
                if !is_external(p, method) || library_of(p, method) != "core" {
                    return self.method_call(id);
                }
                let mut ids = vec![receiver];
                ids.extend_from_slice(p.nodes.list(args));
                if let Some(sig) = p.types.signature(p.decls.get(method).ty) {
                    for (&arg, &want) in p.nodes.list(args).iter().zip(sig.params) {
                        let want = p.types.signature(p.types.non_null(want));
                        // The receiver's type parameter, not the method's own.
                        let solved = |t: TypeId| match p.types.get(t) {
                            TypeData::TypeParam { decl } => p.decls.get(decl).owner != method,
                            _ => false,
                        };
                        if want.is_some_and(|f| solved(f.result) && f.params.contains(&f.result)) {
                            self.widen.insert(arg);
                        }
                    }
                }
                let all = self.ordered(&ids)?;
                self.connected(method, all)?
            }
            Node::Op { op, args, .. } => {
                let Some(spelling) = ops::spelling(op) else {
                    return not_yet("an operator without a spelling");
                };
                if spelling == Spelling::Cat {
                    return self.cat(id);
                }
                let ids = p.nodes.list(args);
                // `x is not None` for `not (x is None)`.
                if let (Spelling::Unary(tree::Unary::Not), [only]) = (spelling, ids) {
                    if let Node::IsNull { .. } = p.nodes.get(*only) {
                        let Expr::Bin(_, value, none) = self.expr(*only)? else {
                            unreachable!("a null check is `is None`")
                        };
                        return Ok(Expr::Bin(Bin::IsNot, value, none));
                    }
                }
                let mut args = present(self.ordered(ids)?)?;
                // Strings' `<=>` writes each operand twice, so ones that
                // aren't simple are held in temporaries first.
                if let Spelling::Cmp { ints: false } = spelling {
                    args = args.into_iter().map(|a| self.simple(a)).collect();
                }
                // A negated literal that can't overflow, as `-1`.
                if let (Spelling::Helper(_, "int_negate" | "int64_negate"), [Expr::Int(n)]) =
                    (spelling, &args[..])
                {
                    if *n != i64::from(i32::MIN) && *n != i64::MIN {
                        return Ok(Expr::Int(-n));
                    }
                }
                let helper = match spelling {
                    Spelling::FloatCmp(_) => self.import("temper_core", "float_cmp"),
                    Spelling::Helper(from, name) => self.import(from, name),
                    _ => String::new(),
                };
                match ops::apply(spelling, args, &helper) {
                    Some(e) => e,
                    None => return not_yet("an operator's operands that aren't simple"),
                }
            }
            Node::And { left, right } | Node::Or { left, right } => {
                let and = matches!(p.nodes.get(id), Node::And { .. });
                let left = self.expr(left)?;
                let (before, right) = self.apart(right)?;
                let op = if and { Bin::And } else { Bin::Or };
                if before.is_empty() {
                    return Ok(Expr::bin(op, left, right));
                }
                // The right side's statements run only where it's needed.
                let t = self.temp_of(TypeId::BOOLEAN)?;
                self.pre.push(Stmt::Assign {
                    target: Expr::name(&t),
                    ann: None,
                    value: left,
                });
                let mut then = before;
                set_unless_raised(&mut then, &t, right);
                let cond = if and {
                    Expr::name(&t)
                } else {
                    not(Expr::name(&t))
                };
                self.pre.push(Stmt::If {
                    cond,
                    then,
                    els: Vec::new(),
                });
                Expr::name(t)
            }
            Node::IfValue { cond, then, els } => {
                let cond = self.expr(cond)?;
                let (then_pre, then) = self.apart(then)?;
                let (els_pre, els) = self.apart(els)?;
                if then_pre.is_empty() && els_pre.is_empty() {
                    return Ok(Expr::IfElse {
                        cond: Box::new(cond),
                        then: Box::new(then),
                        els: Box::new(els),
                    });
                }
                let t = self.temp_of(p.nodes.ty(id))?;
                let (mut a, mut b) = (then_pre, els_pre);
                set_unless_raised(&mut a, &t, then);
                set_unless_raised(&mut b, &t, els);
                self.pre.push(Stmt::If {
                    cond,
                    then: a,
                    els: b,
                });
                Expr::name(t)
            }
            Node::BlockValue { stmts, value } => {
                let list = p.nodes.list(stmts);
                // `??`: `d if (subject := e) is None else subject`.
                if let ([only], Node::IfValue { cond, .. }) = (list, p.nodes.get(value)) {
                    if let (
                        Node::Let {
                            local,
                            init: Some(init),
                        },
                        Node::IsNull { value: read },
                    ) = (p.nodes.get(*only), p.nodes.get(cond))
                    {
                        if matches!(p.nodes.get(read), Node::Local { local: l } if l == local) {
                            let ty = p.nodes.ty(init);
                            let init = self.expr(init)?;
                            self.walrus.insert(local, (init, ty));
                            return self.expr(value);
                        }
                    }
                }
                // After what's hoisted already, which a statement's own
                // hoisted statements would otherwise join after it:
                // `p.n++ + q.n++` reads `p.n` first.
                let mut stmts = std::mem::take(&mut self.pre);
                for &s in list {
                    self.stmt(s, &mut stmts)?;
                }
                self.pre.append(&mut stmts);
                self.expr(value)?
            }
            Node::Coalesce { left, right } => {
                let mut subject = self.expr(left)?;
                if !is_simple(&subject) {
                    let t = self.temp_of(p.nodes.ty(left))?;
                    self.pre.push(Stmt::Assign {
                        target: Expr::name(&t),
                        ann: None,
                        value: subject,
                    });
                    subject = Expr::name(t);
                }
                let (right_pre, right) = self.apart(right)?;
                let cond = Expr::bin(Bin::Is, subject.clone(), Expr::None);
                if right_pre.is_empty() {
                    return Ok(Expr::IfElse {
                        cond: Box::new(cond),
                        then: Box::new(right),
                        els: Box::new(subject),
                    });
                }
                let t = self.temp_of(p.nodes.ty(id))?;
                let mut then = right_pre;
                set_unless_raised(&mut then, &t, right);
                let mut els = Vec::new();
                set_unless_raised(&mut els, &t, subject);
                self.pre.push(Stmt::If { cond, then, els });
                Expr::name(t)
            }
            Node::Null {} => Expr::None,
            Node::IsNull { value } => {
                if let Node::Local { local } = p.nodes.get(value) {
                    self.var_null_check |= p.decls.get(local).facts.contains(Facts::VAR)
                        || self.defaulted.contains(&local);
                }
                Expr::bin(Bin::Is, self.expr(value)?, Expr::None)
            }
            Node::NonNull { value } | Node::AsNullable { value } => self.expr(value)?,
            Node::EqNullable { op, left, right } => {
                let [left, right] = self.exprs(&[left, right])?;
                match ops::spelling(op) {
                    Some(Spelling::Bin(Bin::Eq)) => Expr::bin(Bin::Eq, left, right),
                    Some(Spelling::Helper(from, name)) => {
                        // `a is b if a is None or b is None else eq(a, b)`.
                        let name = self.import(from, name);
                        let left = self.simple(left);
                        let right = self.simple(right);
                        let either = Expr::bin(
                            Bin::Or,
                            Expr::bin(Bin::Is, left.clone(), Expr::None),
                            Expr::bin(Bin::Is, right.clone(), Expr::None),
                        );
                        Expr::IfElse {
                            cond: Box::new(either),
                            then: Box::new(Expr::bin(Bin::Is, left.clone(), right.clone())),
                            els: Box::new(Expr::name(name).call(vec![left, right])),
                        }
                    }
                    _ => return not_yet("a nullable equality without a spelling"),
                }
            }
            Node::OrElse { .. } => {
                let ty = p.nodes.ty(id);
                if ty == TypeId::VOID {
                    let mut stmts = Vec::new();
                    self.value_into(id, Sink::Discard, &mut stmts)?;
                    self.pre.append(&mut stmts);
                    return Ok(Expr::None);
                }
                let t = self.temp_of(ty)?;
                let mut stmts = Vec::new();
                self.value_into(id, Sink::Assign(Expr::name(&t), None), &mut stmts)?;
                self.pre.append(&mut stmts);
                Expr::name(t)
            }
            Node::Bubble { .. } => {
                Expr::name(self.import("temper_core", "bubble")).call(Vec::new())
            }
            Node::Panic {} => {
                self.pre.push(panic());
                Expr::None
            }
            Node::Await { promise, .. } => {
                let promise = self.expr(promise)?;
                let Some(awaiter) = self.awaiters.last() else {
                    return not_yet("an await outside a generator");
                };
                let call = Expr::name(awaiter.clone()).call(vec![promise]);
                Expr::Yield(Some(Box::new(call)))
            }
            Node::Error { .. } => Expr::name(self.import("temper_core", "bubble")).call(Vec::new()),
            Node::This {} => Expr::name("self"),
            Node::GetField { field, object } => {
                let object = self.expr(object)?;
                if self.qname(field) == "core.type ValueResult.value" {
                    return Ok(Expr::Index(Box::new(object), Box::new(Expr::Int(0))));
                }
                self.field(object, field)
            }
            Node::GetStatic { target } => {
                if is_external(p, target) && library_of(p, target) == "core" {
                    // `String.begin`, `Float64.pi`, and the like.
                    return self.connected(target, Vec::new());
                }
                self.static_ref(target)
            }
            Node::New { ctor, args, .. } => {
                let args = self.args(p.nodes.list(args))?;
                if is_external(p, ctor) && library_of(p, ctor) == "core"
                    || self.connected_call(ctor)
                {
                    return self.connected(ctor, args);
                }
                let args = present(args)?;

                let class = self.p.decls.get(ctor).owner;
                self.class_ref(class).call(args)
            }
            // Python's checkers narrow by `isinstance` as Temper does.
            Node::Upcast { value } | Node::Downcast { value } => self.expr(value)?,
            Node::As { value, .. } => {
                let from = p.nodes.ty(value);
                let value = self.expr(value)?;
                if p.types.is_nullable(from) && p.types.non_null(from) == p.nodes.ty(id) {
                    // `x as T` for `x: T?`: a check that it isn't null.
                    let name = self.import("temper_core", "cast_none");
                    return Ok(Expr::name(name).call(vec![value]));
                }
                if let Some(check) = string_index_check(p, p.nodes.ty(id)) {
                    // A string index is an int: `require_string_index(i)`,
                    // as Kotlin's.
                    let name = match check {
                        Bin::Ge => "require_string_index",
                        _ => "require_no_string_index",
                    };
                    let name = self.import("temper_core", name);
                    return Ok(Expr::name(name).call(vec![value]));
                }
                let class = match p.types.get(p.nodes.ty(id)) {
                    TypeData::Scalar(s) => Expr::builtin(match s {
                        Scalar::Boolean => "bool",
                        Scalar::Int32 | Scalar::Int64 => "int",
                        Scalar::Float64 => "float",
                        Scalar::String => "str",
                        _ => return not_yet("an as cast to Void or AnyValue"),
                    }),
                    // mypy takes only a concrete class as a `type[T]`,
                    // though `isinstance` takes any.
                    _ if self.is_abstract(p.nodes.ty(id)) => {
                        Expr::Ignored(Box::new(self.type_ref(p.nodes.ty(id))?), "type-abstract")
                    }
                    _ => self.type_ref(p.nodes.ty(id))?,
                };
                Expr::name(self.import("temper_core", "cast_by_type")).call(vec![value, class])
            }
            Node::Is { ty, value } => {
                let value = self.expr(value)?;
                if let Some(check) = string_index_check(p, ty) {
                    // `i >= 0` for a string index, `i < 0` for none, as
                    // Kotlin's.
                    return Ok(Expr::bin(check, value, Expr::Int(0)));
                }
                // A step's result: a 1-tuple for a value, else `None`.
                match nominal_decl(p, ty).map(|d| self.qname(d)).as_deref() {
                    Some("core.type ValueResult") => {
                        return Ok(
                            Expr::builtin("isinstance").call(vec![value, Expr::builtin("tuple")])
                        );
                    }
                    Some("core.type DoneResult") => {
                        return Ok(Expr::bin(Bin::Is, value, Expr::None));
                    }
                    _ => {}
                }
                let class = self.type_ref(ty)?;
                Expr::builtin("isinstance").call(vec![value, class])
            }
            // A `tuple`, as Kotlin's.
            Node::ListLit { items } => Expr::Tuple(present(self.args(p.nodes.list(items))?)?),
            Node::FunctionLiteral { decl, params, body } => self.literal(id, decl, params, body)?,
            Node::FunctionRef { target } => {
                let owner = p.decls.get(target).owner;
                if matches!(
                    p.decls.get(owner).kind,
                    DeclKind::Class | DeclKind::Interface
                ) {
                    let name = self.member_name(target);
                    self.class_ref(owner).attr(name)
                } else {
                    self.top(target)
                }
            }
            Node::CallValue { callee, args, .. } => {
                let f = self.expr(callee)?;
                f.call(present(self.args(p.nodes.list(args))?)?)
            }
            Node::ToFun { value } | Node::FromFun { value } => self.expr(value)?,
            Node::Identical { left, right } => {
                Expr::bin(Bin::Is, self.expr(left)?, self.expr(right)?)
            }
            other => return not_yet(format!("a {} expression", other.kind().name())),
        })
    }

    /// A chain of `String.cat` as one f-string: its literal parts as they
    /// are, and an `Int32.toString()` without a radix as the int itself.
    fn cat(&mut self, id: NodeId) -> R<Expr> {
        let mut parts = Vec::new();
        let mut marks = Vec::new();
        self.cat_parts(id, &mut parts, &mut marks)?;
        // Where a later part hoists statements, earlier parts that aren't
        // simple are kept in temporaries first, so they still run first.
        let end = self.pre.len();
        for j in (0..marks.len()).rev() {
            let (i, at) = marks[j];
            let FPart::Expr { expr, .. } = &mut parts[i] else {
                continue;
            };
            if at == end || is_simple(expr) || marks[j + 1..].iter().all(|&(_, m)| m == at) {
                continue;
            }
            let t = self.temp();
            let value = std::mem::replace(expr, Expr::name(&t));
            self.pre.insert(
                at,
                Stmt::Assign {
                    target: Expr::name(t),
                    ann: None,
                    value,
                },
            );
        }
        let lits = parts.iter().filter(|p| matches!(p, FPart::Lit(_))).count();
        if lits == parts.len() {
            let text: String = parts
                .iter()
                .filter_map(|p| match p {
                    FPart::Lit(s) => Some(s.as_str()),
                    FPart::Expr { .. } => None,
                })
                .collect();
            return Ok(Expr::Str(text));
        }
        if lits == 0 {
            let terms = parts.into_iter().map(|p| match p {
                FPart::Expr { expr, str: true } => Expr::builtin("str").call(vec![expr]),
                FPart::Expr { expr, .. } => expr,
                FPart::Lit(s) => Expr::Str(s),
            });
            return Ok(terms
                .reduce(|a, b| Expr::bin(Bin::Add, a, b))
                .unwrap_or(Expr::Str(String::new())));
        }
        Ok(Expr::FString(parts))
    }

    fn cat_parts(
        &mut self,
        id: NodeId,
        parts: &mut Vec<FPart>,
        marks: &mut Vec<(usize, usize)>,
    ) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Op { op, args, .. } if ops::spelling(op) == Some(Spelling::Cat) => {
                for &a in p.nodes.list(args) {
                    self.cat_parts(a, parts, marks)?;
                }
            }
            Node::StringLit { value } => {
                let text = p.strings.get(value);
                if let Some(FPart::Lit(last)) = parts.last_mut() {
                    last.push_str(text);
                } else {
                    parts.push(FPart::Lit(text.to_string()));
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
                let expr = self.expr(receiver)?;
                marks.push((parts.len(), self.pre.len()));
                parts.push(FPart::Expr { expr, str: true });
            }
            _ => {
                let expr = self.expr(id)?;
                marks.push((parts.len(), self.pre.len()));
                parts.push(FPart::Expr { expr, str: false });
            }
        }
        Ok(())
    }
}

/// Arguments, each present: one left out needs its default, which waits
/// for default parameters.
/// Arguments as passed: an `Omitted` one left out at the end, or `None`
/// before one given, either way its parameter's default.
pub(crate) fn present(mut args: Vec<Option<Expr>>) -> R<Vec<Expr>> {
    while args.last().is_some_and(Option::is_none) {
        args.pop();
    }
    Ok(args.into_iter().map(|a| a.unwrap_or(Expr::None)).collect())
}

/// A library's name as its Python package is named: std's is
/// `temper-std`, as Kotlin's, and another's is its config's `pyName`, or
/// else its own.
pub fn library_name(p: &Program, name: &str) -> String {
    py_name(name, p.metadata_of(name))
}

/// [`library_name`], given what the library's config says.
pub fn py_name(name: &str, metadata: &temper_tmpl::Metadata) -> String {
    match name {
        "std" => "temper-std".into(),
        _ => metadata.get("pyName").unwrap_or(name).into(),
    }
}

/// `package.a.b` for a module's path `a/b`.
fn dotted(package: &str, path: &str) -> String {
    let mut out = package.to_string();
    for part in path.split('/').filter(|p| !p.is_empty()) {
        out.push('.');
        out.push_str(&crate::names::package_name(part));
    }
    out
}

/// A test's method name: `test_` and its name in snake case, letters,
/// digits, and `_` only (Python's identifiers take Unicode letters),
/// numbered past those already used.
fn test_method_name(text: &str, used: &mut BTreeSet<String>) -> String {
    let mut name = String::from("test_");
    for c in temper_tmpl::naming::snake_case(text).chars() {
        if c.is_alphanumeric() {
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
    while !used.insert(unique.clone()) {
        unique = format!("{name}_{n}");
        n += 1;
    }
    unique
}

/// Whether an expression may be repeated or moved freely: a name or a
/// literal.
fn is_simple(e: &Expr) -> bool {
    matches!(
        e,
        Expr::Name(_) | Expr::Int(_) | Expr::Float(_) | Expr::Str(_) | Expr::Bool(_) | Expr::None
    )
}

fn not(e: Expr) -> Expr {
    Expr::unary(tree::Unary::Not, e)
}

/// `panic()`: raised past any `orelse`, which catches only `Exception`s.
/// Kotlin's raises `RuntimeError`, as `bubble()` does, so an `orelse`
/// caught it (notes/kotlin-differences.md).
fn panic() -> Stmt {
    Stmt::Raise(Expr::builtin("BaseException").call(vec![Expr::Str("panic".into())]))
}

/// `t = value` after `stmts`, unless they end by raising.
fn set_unless_raised(stmts: &mut Vec<Stmt>, t: &str, value: Expr) {
    if !matches!(stmts.last(), Some(Stmt::Raise(_))) {
        stmts.push(Stmt::Assign {
            target: Expr::name(t),
            ann: None,
            value,
        });
    }
}

impl Translator<'_> {
    /// `e` itself if it's simple, else a temporary holding it.
    fn simple(&mut self, e: Expr) -> Expr {
        if is_simple(&e) {
            return e;
        }
        let t = self.temp();
        self.pre.push(Stmt::Assign {
            target: Expr::name(&t),
            ann: None,
            value: e,
        });
        Expr::name(t)
    }
}

/// How many of a function's last parameters are optional.
fn trailing_optional(p: &temper_tmpl::Program, params: temper_tmpl::Kids) -> usize {
    (p.nodes.list(params).iter().rev())
        .take_while(|&&param| {
            matches!(p.nodes.get(param), Node::Param { decl }
                if p.decls.get(decl).facts.contains(Facts::OPTIONAL))
        })
        .count()
}

/// For a type that's core's `StringIndex` or `NoStringIndex`, how an int
/// is checked to be one: `>= 0` or `< 0`.
fn string_index_check(p: &Program, ty: TypeId) -> Option<Bin> {
    let TypeData::Nominal { decl, .. } = p.types.get(ty) else {
        return None;
    };
    if !is_external(p, decl) || library_of(p, decl) != "core" {
        return None;
    }
    match p.name(decl) {
        "StringIndex" => Some(Bin::Ge),
        "NoStringIndex" => Some(Bin::Lt),
        _ => None,
    }
}

/// The names by which a connected declaration's backend list can mean this
/// backend, as Kotlin's `connectedBackendNames`.
pub const NAMES: &[&str] = &["py"];

/// What host code's definitions are reached through: the class that a
/// module's `__connected__.py` defines.
const CONNECTED: &str = "_connected";

/// Where a module's host code is, relative to the library's root:
/// `__connected__.py` in its directory.
pub fn host_path(module: &Module) -> String {
    match module.path.as_str() {
        "" => "__connected__.py".to_string(),
        dir => format!("{dir}/__connected__.py"),
    }
}

/// A module's host code, if it has any, which its file starts with, as
/// Kotlin inlines it.
fn host_file<'p>(p: &'p Program, module: &Module) -> Option<&'p HostFile> {
    let path = host_path(module);
    p.host.files.iter().find(|f| f.path == path)
}

/// Whether a loop's body has jumps to it, a `break` and a `continue`,
/// from loops inside it, which Python's own can't make.
fn outer_jumps(p: &Program, body: NodeId, label: DeclId) -> (bool, bool) {
    let (mut breaks, mut continues) = (false, false);
    let mut stack: Vec<(NodeId, bool)> = vec![(body, false)];
    while let Some((n, inner)) = stack.pop() {
        match p.nodes.get(n) {
            Node::FunctionLiteral { .. } => continue,
            Node::Break { label: l } if inner && l == label => breaks = true,
            Node::Continue { label: l } if inner && l == label => continues = true,
            _ => {}
        }
        let inner = inner || matches!(p.nodes.get(n), Node::While { .. } | Node::DoWhile { .. });
        stack.extend(p.nodes.kids(n).iter().map(|&k| (k, inner)));
    }
    (breaks, continues)
}

/// Where Python finds a core type, and its name there: a module and a
/// name, or `builtins` and the annotation itself.
pub(crate) fn core_type(qname: &str) -> Option<(&'static str, &'static str)> {
    Some(match qname {
        "core.type List" | "core.type Listed" => ("collections.abc", "Sequence"),
        "core.type ListBuilder" => ("collections.abc", "MutableSequence"),
        "core.type Mapped" => ("collections.abc", "Mapping"),
        "core.type Map" => ("types", "MappingProxyType"),
        "core.type MapBuilder" => ("builtins", "dict"),
        "core.type Pair" => ("temper_core", "Pair"),
        // String indices are ints, as Kotlin's.
        "core.type StringIndex" | "core.type StringIndexOption" | "core.type NoStringIndex" => {
            ("builtins", "int")
        }
        "core.type StringBuilder" => ("builtins", "list[str]"),
        "core.type Deque" => ("collections", "deque"),
        "core.type DenseBitVector" => ("temper_core", "DenseBitVector"),
        // `()`, its one value. Kotlin's `_empty` is undefined.
        "core.type Empty" => ("builtins", "tuple[()]"),
        // `temper_core`'s `concurrent.futures.Future`, which asyncio can
        // `await`, where Kotlin's annotates `Future`.
        "core.type Promise" | "core.type PromiseBuilder" => ("temper_core", "TemperFuture"),
        _ => return None,
    })
}

/// How many times a type names the type parameter whose type is `param`.
fn times_named(p: &Program, ty: TypeId, param: TypeId) -> usize {
    if ty == param {
        return 1;
    }
    if !p.types.contains(ty) {
        return 0;
    }
    match p.types.get(ty) {
        TypeData::Nullable(t) => times_named(p, t, param),
        // `NoReturn`, which names none.
        TypeData::Never(_) => 0,
        TypeData::Nominal { args, .. } => p
            .types
            .list(args)
            .iter()
            .map(|&a| times_named(p, a, param))
            .sum(),
        TypeData::Function { .. } => p.types.signature(ty).map_or(0, |sig| {
            (sig.params.iter().chain([&sig.result]))
                .map(|&t| times_named(p, t, param))
                .sum()
        }),
        TypeData::Scalar(_) | TypeData::TypeParam { .. } => 0,
    }
}

/// Whether a function's signature names the type parameter whose type
/// is `param` only once, as one parameter's whole type, `T` or `T?`.
fn only_as_a_parameter(p: &Program, decl: DeclId, param: TypeId) -> bool {
    let Some(sig) = p.signature(decl) else {
        return false;
    };
    let bare = (sig.params.iter()).any(|&t| p.types.non_null(t) == param);
    bare && times_named(p, p.decls.get(decl).ty, param) == 1
}

/// The ignore comment a function's `def` needs for an own type parameter
/// that its signature names only once, in its result (`List<B>`, `B?`):
/// a `TypeVar` all the same, which a call's expected type solves, as its
/// bound wouldn't fit a call's explicit type argument. pyright's strict
/// `reportInvalidTypeVarUse` reports it, and mypy's `type-var` too where
/// the result is just `B`.
fn lone_result_type_var(p: &Program, decl: DeclId) -> Option<&'static str> {
    let sig = p.signature(decl)?;
    let lone: Vec<TypeId> = (p.decls.type_params(decl).iter())
        .map(|&tp| p.decls.get(tp).ty)
        .filter(|&t| {
            times_named(p, sig.result, t) == 1
                && sig.params.iter().all(|&a| times_named(p, a, t) == 0)
        })
        .collect();
    match lone.first() {
        None => None,
        _ if lone.contains(&sig.result) => Some("type: ignore[type-var]"),
        _ => Some("pyright: ignore[reportInvalidTypeVarUse]"),
    }
}
