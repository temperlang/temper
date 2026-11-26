//! The translation from tmpl to JS's output tree, classes in
//! [`classes`].
//!
//! Module state is plain, as Kotlin's: module `let`s and `var`s are a
//! module's own `const`s and `let`s, initialized as the module's code
//! runs, once, when it's first imported. One the init block initializes
//! at its top level is declared there, with its value; others are
//! declared first, typed but without a value. A `Console` is erased
//! ([`connected::TYPES`]): logging is the global `console.log`.

use crate::NotYet;
use crate::connected::{self, Connection, TypeConnection};
use crate::names::{KEYWORDS, SPECIAL, TEST_RUNNER};
use crate::ops::{self, Spelling};
use crate::tree::{
    self, Bin, Expr, Keyword, Member, MethodKind, RUNTIME, Stmt, TEST_MODULE, TPart,
};
use std::collections::{BTreeMap, BTreeSet, HashMap, HashSet};
use temper_tmpl::analysis::{self, ancestors, home, module_state, nominal_decl, walk};
use temper_tmpl::host::uses_host;
use temper_tmpl::naming::NameMap;
use temper_tmpl::qname::{by_connection, is_external, library_of};
use temper_tmpl::{
    DeclId, DeclKind, Facts, Module, Node, NodeId, Program, QNames, Scalar, TypeData, TypeId,
};

mod classes;

type R<T> = Result<T, NotYet>;

fn not_yet<T>(what: impl Into<String>) -> R<T> {
    Err(NotYet { what: what.into() })
}

pub struct Translator<'a> {
    p: &'a Program,
    names: &'a NameMap,
    qnames: QNames,
    /// `@temperlang/core` names imported.
    imports: BTreeSet<&'static str>,
    /// Module `let`s of an erased type, which aren't declared.
    erased: HashSet<DeclId>,
    /// Locals assigned after they're declared, which are `let`, not
    /// `const`.
    assigned: HashSet<DeclId>,
    /// The enclosing loops, innermost last.
    loops: Vec<DeclId>,
    /// Labels printed, by loop: only those a jump from an inner loop
    /// needs.
    labels: HashMap<DeclId, String>,
    /// The module whose file this is; `NONE` for a tests file.
    here: DeclId,
    /// The directory of the file being written, in the package, as
    /// `helpers`; empty at its root.
    dir: String,
    /// For a tests file, the module tested, whose `.internal.js` it
    /// imports from.
    tested: DeclId,
    /// For a tests file, what its module's test parts define, which are
    /// the file's own.
    test_own: HashSet<DeclId>,
    /// The module's declarations its tests use, exported from its
    /// `.internal.js`.
    test_access: HashSet<DeclId>,
    /// Names imported from other modules, by specifier.
    from: BTreeMap<String, BTreeSet<String>>,
    /// The class or interface whose body is being written, whose
    /// private members (`#name`) and fields' storage it may use; `NONE`
    /// outside one.
    class: DeclId,
    /// Each class's and interface's node, by its declaration.
    class_nodes: HashMap<DeclId, NodeId>,
    /// Statements hoisted out of the statement being translated, which
    /// run before it: an `orelse` (a `try`) or statements inside an
    /// expression, each leaving its value in a temporary.
    pre: Vec<Stmt>,
    /// Parameters' defaults' locals, which take their parameters' names:
    /// `x ??= d` sets the parameter in place.
    defaulted: HashMap<DeclId, DeclId>,
    /// Temporaries made in this file, for fresh names (`t$0`).
    temps: usize,
    /// The module's host code's path in the package, if it has a
    /// `_connected.js`, whose `.internal.js` then exports all its
    /// declarations, for host code to import, as Kotlin's.
    host: Option<String>,
    /// Whether the module uses its host code, which it then imports as
    /// `_connected`.
    uses_host: bool,
    /// The `doAwait` parameters of the generators being translated,
    /// innermost last, which an `await` yields a call of.
    awaiters: Vec<String>,
    /// The `let`s holding the static fields of the class being written,
    /// which go before it.
    static_lets: Vec<Stmt>,
    /// Type parameters written as other types: a generic supertype's,
    /// as the interface being written supplies them, while it declares
    /// that supertype's members again.
    type_args: HashMap<DeclId, String>,
}

/// What a module's tests file uses of it (its tests, and what only they
/// reach, which only that file has: `analysis::test_parts`), which its
/// `.internal.js` exports, its private declarations too, and of those,
/// the `var`s they assign, each with a setter there, `NAME$set`.
#[derive(Clone, Debug, Default)]
pub struct TestAccess {
    pub used: HashSet<DeclId>,
    pub assigned: Vec<DeclId>,
}

impl TestAccess {
    pub fn of(p: &Program, module: &Module) -> TestAccess {
        let parts = analysis::test_parts(p, module);
        TestAccess {
            used: parts.used.into_iter().collect(),
            assigned: parts.assigned,
        }
    }
}

/// A module's file in its package, without `.js`: the root module's the
/// library's name with `_` for `-`, others' their paths, as `helpers/deep`.
pub fn module_file(p: &Program, module: DeclId) -> String {
    let path = p.modules.iter().find(|m| m.decl == module);
    match path.map(|m| m.path.as_str()) {
        None | Some("") => crate::names::module_file(&p.library.name),
        Some(path) => path.to_string(),
    }
}

/// `to`, a path in the package, from directory `dir`, as an import's
/// specifier: `./helpers.js`, or `../x.js`.
pub fn relative(dir: &str, to: &str) -> String {
    let dir: Vec<&str> = dir.split('/').filter(|s| !s.is_empty()).collect();
    let to: Vec<&str> = to.split('/').filter(|s| !s.is_empty()).collect();
    let common = dir.iter().zip(&to).take_while(|(a, b)| a == b).count();
    let up = dir.len() - common;
    let mut out = if up == 0 {
        "./".to_string()
    } else {
        "../".repeat(up)
    };
    out.push_str(&to[common..].join("/"));
    out
}

impl<'a> Translator<'a> {
    pub fn new(p: &'a Program, names: &'a NameMap) -> Translator<'a> {
        Translator {
            p,
            names,
            qnames: QNames::derive(p),
            imports: BTreeSet::new(),
            erased: HashSet::new(),
            assigned: HashSet::new(),
            loops: Vec::new(),
            labels: HashMap::new(),
            here: DeclId::NONE,
            dir: String::new(),
            tested: DeclId::NONE,
            test_own: HashSet::new(),
            test_access: HashSet::new(),
            from: BTreeMap::new(),
            class: DeclId::NONE,
            class_nodes: (p.modules.iter())
                .flat_map(|m| m.all_items())
                .filter_map(|item| match p.nodes.get(item) {
                    Node::Class { decl, .. } | Node::Interface { decl, .. } => Some((decl, item)),
                    _ => None,
                })
                .collect(),
            pre: Vec::new(),
            defaulted: HashMap::new(),
            temps: 0,
            host: None,
            uses_host: false,
            awaiters: Vec::new(),
            static_lets: Vec::new(),
            type_args: HashMap::new(),
        }
    }

    /// A module-level declaration's name, imported if it's another
    /// module's or library's: from its module's public file, or in a tests
    /// file, from the tested module's `.internal.js`.
    fn top(&mut self, decl: DeclId) -> String {
        let name = self.name(decl);
        self.import_as(decl, name)
    }

    /// `name`, imported from where `decl` is, if that's another module.
    fn import_as(&mut self, decl: DeclId, name: String) -> String {
        let p = self.p;
        let Some(module) = home(p, decl) else {
            return name;
        };
        if self.is_here(decl) {
            return name;
        }
        let d = p.decls.get(module);
        let specifier = if d.kind == DeclKind::Library {
            package_of(p, p.name(module))
        } else if !d.owner.is_none() {
            let package = package_of(p, p.name(d.owner));
            match p.name(module) {
                "" => package,
                path => format!("{package}/{path}"),
            }
        } else {
            let file = module_file(p, module);
            let file = if module == self.tested {
                format!("{file}.internal.js")
            } else {
                format!("{file}.js")
            };
            relative(&self.dir, &file)
        };
        self.from.entry(specifier).or_default().insert(name.clone());
        name
    }

    /// Whether a module-level declaration is this file's own: its module's,
    /// or in a tests file, its test parts'.
    pub(crate) fn is_here(&self, decl: DeclId) -> bool {
        match self.here.is_none() {
            true => self.test_own.contains(&decl),
            false => home(self.p, decl) == Some(self.here),
        }
    }

    /// The other modules' names imported, taken.
    fn take_from(&mut self) -> Vec<(String, Vec<String>)> {
        let from = std::mem::take(&mut self.from);
        (from.into_iter())
            .map(|(f, names)| (f, names.into_iter().collect()))
            .collect()
    }

    pub(crate) fn node(&self, id: NodeId) -> Node {
        self.p.nodes.get(id)
    }

    fn name(&self, decl: DeclId) -> String {
        match self.defaulted.get(&decl) {
            Some(&param) => self.names.get(param).to_string(),
            None => self.names.get(decl).to_string(),
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

    /// A declaration's JSDoc `@type`, where its value says too little of
    /// it: `null` alone, or `[]` or `new Map()` of their type arguments.
    fn decl_ty(&mut self, ty: TypeId, value: NodeId) -> R<Option<String>> {
        let null = matches!(self.p.nodes.get(value), Node::Null {});
        Ok(match null || self.has_args(ty) {
            true => Some(self.ty(ty)?),
            false => None,
        })
    }

    /// Whether a type has type arguments, which a declaration's JSDoc
    /// gives where its value may not, as `[]`.
    fn has_args(&self, ty: TypeId) -> bool {
        match self.p.types.get(ty) {
            TypeData::Nominal { args, .. } => !self.p.types.list(args).is_empty(),
            TypeData::Nullable(inner) => self.has_args(inner),
            _ => false,
        }
    }

    /// Imports a runtime helper.
    pub(crate) fn import(&mut self, name: &'static str) {
        self.imports.insert(name);
    }

    /// `name`, an export of std's `module`, imported unless this is that
    /// module, for a connection that gives the runtime one of std's
    /// classes, which it can't import.
    pub(crate) fn import_std(&mut self, module: &str, name: &str) -> String {
        let p = self.p;
        let here = !self.here.is_none()
            && p.name(self.here) == module
            && temper_tmpl::qname::library_of(p, self.here) == "std";
        if !here {
            let specifier = format!("{}/{module}", package_of(p, "std"));
            self.from
                .entry(specifier)
                .or_default()
                .insert(name.to_string());
        }
        name.to_string()
    }

    /// A node's type as JSDoc writes it.
    pub(crate) fn type_of(&mut self, id: NodeId) -> R<String> {
        self.ty(self.p.nodes.ty(id))
    }

    /// Whether a node's value may be null: its type is nullable, or a
    /// type parameter, which may stand for one.
    pub(crate) fn may_be_null(&self, id: NodeId) -> bool {
        let ty = self.p.nodes.ty(id);
        self.p.types.is_nullable(ty) || matches!(self.p.types.get(ty), TypeData::TypeParam { .. })
    }

    /// A list as a runtime helper takes it: cast to a mutable array
    /// where it's a read-only `List` or `Listed`, as the helpers'
    /// declarations want, though they don't change it.
    pub(crate) fn list_arg(&mut self, id: NodeId) -> R<Expr> {
        let ty = self.p.nodes.ty(id);
        let e = self.expr(id)?;
        Ok(match self.p.types.get(ty) {
            TypeData::Nominal { decl, args }
                if matches!(
                    self.qname(decl).as_str(),
                    "core.type List" | "core.type Listed"
                ) =>
            {
                let item = self
                    .p
                    .types
                    .list(args)
                    .first()
                    .copied()
                    .unwrap_or(TypeId::NONE);
                let item = self.ty(item)?;
                Expr::Cast(format!("{}[]", item_in_array(&item)), Box::new(e))
            }
            _ => e,
        })
    }

    /// A declaration's type parameters, as JSDoc's `@template` takes them:
    /// `T`, or `{Named} T` with a bound (but `MapKey`, which JS's maps
    /// need nothing for); not failure parameters, as exceptions are
    /// unchecked.
    pub(crate) fn templates(&mut self, decl: DeclId) -> R<Vec<String>> {
        let p = self.p;
        let mut out = Vec::new();
        for t in temper_tmpl::failures::unchecked_type_params(p, decl) {
            let name = p.name(t).to_string();
            let mut bounds = Vec::new();
            for &b in p.decls.supers(t) {
                let core_key =
                    nominal_decl(p, b).is_some_and(|d| self.qname(d) == "core.type MapKey");
                if !core_key {
                    bounds.push(self.ty(b)?);
                }
            }
            out.push(match bounds.as_slice() {
                [] => name,
                _ => format!("{{{}}} {name}", bounds.join(" & ")),
            });
        }
        Ok(out)
    }

    /// A type as JSDoc writes it, for TypeScript's checker.
    fn ty(&mut self, ty: TypeId) -> R<String> {
        Ok(match self.p.types.get(ty) {
            TypeData::Scalar(s) => match s {
                Scalar::Void => "void",
                Scalar::Boolean => "boolean",
                Scalar::Int32 | Scalar::Float64 => "number",
                Scalar::Int64 => "bigint",
                Scalar::String => "string",
                Scalar::AnyValue => "unknown",
                // A type as a value is its `typeof` name.
                Scalar::Type => "string",
                Scalar::Bubble => return not_yet("Bubble as a value's type"),
            }
            .to_string(),
            // Only a signature's result, never a value's (tmpl's T5).
            TypeData::Result { .. } => return not_yet("a result type as a value's"),
            TypeData::Nominal { decl, args } => {
                let mut texts = Vec::new();
                let args = self.p.types.list(args);
                for a in temper_tmpl::failures::unchecked_args(self.p, decl, args) {
                    texts.push(self.ty(a)?);
                }
                let applied = |name: String| {
                    if texts.is_empty() {
                        name
                    } else {
                        format!("{name}<{}>", texts.join(", "))
                    }
                };
                if self.p.decls.get(decl).facts.contains(Facts::FUN) {
                    // A `@fun` interface is its function type, as Kotlin's.
                    let Some(sig) = temper_tmpl::analysis::fun_signature(self.p, ty) else {
                        return not_yet("a @fun interface's signature");
                    };
                    return self.fn_type(sig);
                }
                if let Some(name) = self.std_type(decl) {
                    return Ok(name.to_string());
                }
                match self.qname(decl).as_str() {
                    "core.type Console" => "Console".to_string(),
                    // Frozen arrays, as Kotlin's, read-only for TypeScript.
                    "core.type List" | "core.type Listed" => {
                        format!("readonly {}[]", item_in_array(&texts.join(", ")))
                    }
                    "core.type ListBuilder" => format!("{}[]", item_in_array(&texts.join(", "))),
                    "core.type Map" | "core.type Mapped" | "core.type MapBuilder" => {
                        applied("Map".into())
                    }
                    "core.type Pair" => applied(format!("import(\"{RUNTIME}/pair.js\").Pair")),
                    "core.type Promise" => applied("Promise".into()),
                    "core.type PromiseBuilder" => {
                        applied(format!("import(\"{RUNTIME}/async.js\").PromiseBuilder"))
                    }
                    "core.type SafeGenerator" | "core.type Generator" => {
                        applied("Generator".into())
                    }
                    // A step's result is JS's own, from `g.next()`.
                    "core.type GeneratorResult" => {
                        format!("IteratorResult<{}, void>", texts.join(", "))
                    }
                    "core.type ValueResult" => format!("IteratorYieldResult<{}>", texts.join(", ")),
                    "core.type DoneResult" => "IteratorReturnResult<void>".to_string(),
                    // String indices are UTF-16 offsets, none -1, as Kotlin's.
                    "core.type StringIndex"
                    | "core.type StringIndexOption"
                    | "core.type NoStringIndex" => "number".to_string(),
                    // `[""]`, its one string growing, as Kotlin's.
                    "core.type StringBuilder" => "string[]".to_string(),
                    // An array, as the runtime's helpers take it.
                    "core.type Deque" => format!("{}[]", item_in_array(&texts.join(", "))),
                    "core.type DenseBitVector" => {
                        format!("import(\"{RUNTIME}/bitvector.js\").DenseBitVector")
                    }
                    // `empty()`'s, as the runtime declares it.
                    "core.type Empty" => "{}".to_string(),
                    // Core's root of declared failures, which each
                    // library's failure roots extend.
                    "core.type Error" => "Error".to_string(),
                    // Core's own failures, the runtime's classes.
                    "core.type CoreError"
                    | "core.type MissingKey"
                    | "core.type ParseError"
                    | "core.type InvalidCodePoint"
                    | "core.type BrokenPromise" => {
                        let name = self.core_failure(decl).unwrap_or("Error");
                        self.import(name);
                        applied(name.to_string())
                    }
                    _ if matches!(
                        self.p.decls.get(decl).kind,
                        DeclKind::Class | DeclKind::Interface
                    ) && !(is_external(self.p, decl)
                        && library_of(self.p, decl) == "core") =>
                    {
                        let name = self.top(decl);
                        applied(name)
                    }
                    _ if is_external(self.p, decl) && library_of(self.p, decl) != "core" => {
                        let name = self.top(decl);
                        applied(name)
                    }
                    q => return not_yet(format!("a type annotation for {q}")),
                }
            }
            TypeData::Function { .. } => self.fn_type(ty)?,
            // A result that never returns, kept by `temper_tmpl::nevers`.
            TypeData::Never(_) => "never".to_string(),
            // A function type's `=>` would take the `| null`.
            TypeData::Nullable(inner) => {
                let text = self.ty(inner)?;
                let function = match self.p.types.get(inner) {
                    TypeData::Function { .. } => true,
                    TypeData::Nominal { decl, .. } => {
                        self.p.decls.get(decl).facts.contains(Facts::FUN)
                    }
                    _ => false,
                };
                match function {
                    true => format!("({text}) | null"),
                    false => format!("{text} | null"),
                }
            }
            TypeData::TypeParam { decl } => match self.type_args.get(&decl) {
                Some(text) => text.clone(),
                None => self.p.name(decl).to_string(),
            },
        })
    }

    /// For core's `StringIndex` or `NoStringIndex`, how a number is
    /// checked to be one: `>= 0` or `< 0`.
    fn index_check(&self, ty: TypeId) -> Option<Bin> {
        let decl = nominal_decl(self.p, ty)?;
        match self.qname(decl).as_str() {
            "core.type StringIndex" => Some(Bin::Ge),
            "core.type NoStringIndex" => Some(Bin::Lt),
            _ => None,
        }
    }

    /// A function type as JSDoc writes it, as Kotlin's: `(arg0: number)
    /// => string`.
    fn fn_type(&mut self, sig: TypeId) -> R<String> {
        let Some(s) = self.p.types.signature(sig) else {
            return not_yet("a function type without a signature");
        };
        if s.optional.contains(&true) {
            return not_yet("a function type with optional parameters");
        }
        let (params, result) = (s.params.to_vec(), s.pass);
        let mut texts = Vec::new();
        for (i, t) in params.into_iter().enumerate() {
            texts.push(format!("arg{i}: {}", self.ty(t)?));
        }
        Ok(format!("({}) => {}", texts.join(", "), self.ty(result)?))
    }

    /// Whether a declaration is exported from its module's
    /// `.internal.js`: exported, or used by the module's tests.
    fn exported(&self, decl: DeclId) -> bool {
        self.p.decls.get(decl).facts.contains(Facts::EXPORTED)
            || self.test_access.contains(&decl)
            || self.host.is_some()
    }

    pub fn module(&mut self, module: &Module, access: &TestAccess) -> R<tree::Module> {
        let p = self.p;
        self.here = module.decl;
        let file = module_file(p, module.decl);
        self.dir = file.rsplit_once('/').map_or("", |(d, _)| d).to_string();
        self.tested = DeclId::NONE;
        self.test_own.clear();
        self.test_access = access.used.clone();
        self.imports.clear();
        self.temps = 0;
        let host = host_path(module);
        self.host = (p.host.files.iter())
            .any(|f| f.path == host)
            .then_some(host);
        self.uses_host = false;
        let mut body = Vec::new();
        self.declarations(module, &module.items, Some(module.init), &mut body)?;
        // Setters for the `var`s its tests assign.
        for &var in &access.assigned {
            let name = self.name(var);
            let ty = self.ty(p.decls.get(var).ty)?;
            body.push(Stmt::Function {
                generator: false,
                export: true,
                name: format!("{name}$set"),
                name_span: temper_tmpl::Span::UNKNOWN,
                source_name: String::new(),
                params: vec![("value$".into(), ty)],
                result: None,
                body: vec![Stmt::Assign {
                    target: Expr::name(name),
                    value: Expr::name("value$"),
                }],
            });
        }
        let namespace = match &self.host {
            Some(host) if self.uses_host => {
                Some((CONNECTED.to_string(), relative(&self.dir, host)))
            }
            _ => None,
        };
        Ok(tree::Module {
            namespace,
            imports: self.imports.iter().map(|s| s.to_string()).collect(),
            from: self.take_from(),
            body,
        })
    }

    /// A file's declarations and init block: a module's items and init,
    /// or in its tests file, its test items and test init, which
    /// continues it. Functions, then classes and interfaces, each after
    /// its supertypes, then module `let`s and `var`s that the init block
    /// doesn't initialize at its top level, then the init block, where
    /// those it does are declared.
    fn declarations(
        &mut self,
        module: &Module,
        items: &[NodeId],
        init: Option<NodeId>,
        body: &mut Vec<Stmt>,
    ) -> R<()> {
        let p = self.p;
        let state = module_state(p, module);
        for &item in items.iter().chain(&init) {
            if let Node::ModuleLet { decl } = p.nodes.get(item) {
                if self.type_connection(p.decls.get(decl).ty) == Some(TypeConnection::Erased) {
                    self.erased.insert(decl);
                }
            }
            walk(p, item, &mut |id| {
                if let Node::Assign { local, .. } = p.nodes.get(id) {
                    self.assigned.insert(local);
                }
            });
        }
        for &item in items {
            match p.nodes.get(item) {
                Node::Function { decl, .. }
                | Node::BodilessFunction { decl, .. }
                | Node::Class { decl, .. }
                    if self.here.is_none() && uses_host(p, decl, NAMES) =>
                {
                    return not_yet(format!(
                        "host code for what only tests reach, {}",
                        p.name(decl)
                    ));
                }
                Node::Function { decl, params, .. } | Node::BodilessFunction { decl, params }
                    if uses_host(p, decl, NAMES) =>
                {
                    body.push(self.host_wrapper(item, decl, params)?);
                }
                Node::Function { .. } => body.push(self.function(item)?),
                // std's, which the connection table has (`sendRequest`).
                Node::BodilessFunction { decl, .. }
                    if by_connection(p, decl, crate::connected::connects_std) => {}
                Node::BodilessFunction { decl, .. } => {
                    return not_yet(format!("a connected function, {}", p.name(decl)));
                }
                _ => {}
            }
        }
        // Classes and interfaces, each after its supertypes, which its
        // `extends` reads.
        let mut done = HashSet::new();
        for &item in items {
            self.class_after_supers(item, &mut done, body)?;
        }
        // Module `let`s and `var`s that the init block doesn't initialize
        // at its top level are declared first.
        for &item in items {
            if let Node::ModuleLet { decl } = p.nodes.get(item) {
                if !self.erased.contains(&decl) && !state.initialized_at_top.contains(&decl) {
                    let decl_stmt = Stmt::Decl {
                        export: self.exported(decl),
                        keyword: Keyword::Let,
                        name: self.name(decl),
                        ty: Some(self.ty(p.decls.get(decl).ty)?),
                        value: None,
                    };
                    body.push(decl_stmt.spanned(p.nodes.span(item)));
                }
            }
        }
        let Some(init) = init else {
            return Ok(());
        };
        self.begin(init);
        match p.nodes.get(init) {
            Node::Block { stmts } => {
                for &s in p.nodes.list(stmts) {
                    match p.nodes.get(s) {
                        Node::InitLet { target, value } if !self.erased.contains(&target) => {
                            let var = p.decls.get(target).facts.contains(Facts::VAR);
                            let name = self.name(target);
                            let export = self.exported(target);
                            let span = p.nodes.span(s);
                            if is_handler(p, value) {
                                // Declared, then assigned in a `try`, as Kotlin's.
                                let decl = Stmt::Decl {
                                    export,
                                    keyword: Keyword::Let,
                                    name: name.clone(),
                                    ty: Some(self.ty(p.decls.get(target).ty)?),
                                    value: None,
                                };
                                body.push(decl.spanned(span));
                                let target = Expr::name(name);
                                let made = self.try_assign(target, value)?;
                                body.push(made.spanned(span));
                                continue;
                            }
                            let ty = self.decl_ty(p.decls.get(target).ty, value)?;
                            let saved = std::mem::take(&mut self.pre);
                            let value = self.expr(value)?;
                            let pre = std::mem::replace(&mut self.pre, saved);
                            body.extend(pre.into_iter().map(|s| s.spanned(span)));
                            let decl = Stmt::Decl {
                                export,
                                keyword: if var { Keyword::Let } else { Keyword::Const },
                                name,
                                ty,
                                value: Some(value),
                            };
                            body.push(decl.spanned(span));
                        }
                        // A local its tests use, exported for them.
                        Node::Let { local, .. } if self.test_access.contains(&local) => {
                            let from = body.len();
                            self.stmt(s, body)?;
                            let name = self.name(local);
                            body[from..].iter_mut().for_each(|s| s.export_named(&name));
                        }
                        _ => self.stmt(s, body)?,
                    }
                }
            }
            _ => self.stmt(init, body)?,
        }
        Ok(())
    }

    /// The module's host code, as `_connected`, marked used; `NotYet`,
    /// naming the file, if the module has none.
    fn host_code(&mut self, decl: DeclId) -> R<Expr> {
        if self.host.is_none() {
            let module = self.p.modules.iter().find(|m| m.decl == self.here);
            return not_yet(format!(
                "{}'s host code, with no {}",
                self.p.name(decl),
                module.map_or_else(|| "_connected.js".into(), host_path)
            ));
        }
        self.uses_host = true;
        Ok(Expr::name(CONNECTED))
    }

    /// A function whose host code runs instead of its Temper body, as
    /// Kotlin's: its parameters' defaults, then a call of the host's
    /// function of its source name, passing each parameter:
    /// `return _connected.sum(i, j, bonus);`.
    fn host_wrapper(&mut self, item: NodeId, decl: DeclId, params: temper_tmpl::Kids) -> R<Stmt> {
        let p = self.p;
        let host = self.host_code(decl)?;
        let mut ps: Vec<(String, String)> = (self.templates(decl)?.into_iter())
            .map(|t| ("@template".to_string(), t))
            .collect();
        ps.extend(self.params(params)?);
        let result = match p.signature(decl) {
            Some(sig) if sig.pass != TypeId::VOID => Some(self.ty(sig.pass)?),
            _ => None,
        };
        ps.extend(self.throws(decl)?);
        self.begin(item);
        let mut body = Vec::new();
        let defaults = match p.nodes.get(item) {
            Node::Function { body: block, .. } => analysis::defaults(p, block),
            _ => Vec::new(),
        };
        for d in &defaults {
            self.stmt(d.stmt, &mut body)?;
        }
        // Defaults set their parameters in place (`bonus ??= 0`); one left
        // out that defaults to null is passed as null, not `undefined`,
        // as host code's type says (`string ?? null`), unlike Kotlin's.
        let args = (p.nodes.list(params).iter())
            .filter_map(|&param| match p.nodes.get(param) {
                Node::Param { decl } => Some(decl),
                _ => None,
            })
            .map(|param| {
                let name = Expr::name(self.name(param));
                let optional = p.decls.get(param).facts.contains(Facts::OPTIONAL);
                let set = (defaults.iter())
                    .any(|d| d.param == param && !matches!(p.nodes.get(d.value), Node::Null {}));
                if optional && !set {
                    Expr::bin(Bin::Coalesce, name, Expr::Null)
                } else {
                    name
                }
            })
            .collect();
        let call = host.member(p.name(decl)).call(args);
        body.push(match result {
            Some(_) => Stmt::Return(Some(call)),
            None => Stmt::Expr(call),
        });
        Ok(Stmt::Function {
            generator: false,
            export: self.exported(decl),
            name: self.name(decl),
            name_span: p.decls.get(decl).name_span,
            source_name: p.name(decl).to_string(),
            params: ps,
            result,
            body,
        }
        .spanned(p.nodes.span(item)))
    }

    /// A user's connected class that host code defines, a name for it,
    /// as Kotlin's: `export const Counter = _connected.Counter;`.
    pub(crate) fn host_class(&mut self, item: NodeId, decl: DeclId) -> R<Stmt> {
        let host = self.host_code(decl)?;
        Ok(Stmt::Decl {
            export: self.exported(decl),
            keyword: Keyword::Const,
            name: self.name(decl),
            ty: None,
            value: Some(host.member(self.p.name(decl))),
        }
        .spanned(self.p.nodes.span(item)))
    }

    /// A module's tests, as a file in `test/` for Node's test runner
    /// (`node:test`), named as the module's file, as Kotlin's: what only
    /// tests reach (its test items, and its test init, which continues
    /// the module's init here), then an `it` per test, imported from
    /// `node:test`. `None` if it has none.
    pub fn tests(&mut self, module: &Module) -> R<Option<tree::Module>> {
        if module.tests.is_empty() {
            return Ok(None);
        }
        let p = self.p;
        self.here = DeclId::NONE;
        self.tested = module.decl;
        let parts = analysis::test_parts(p, module);
        if parts.assign_init_locals(p) {
            return not_yet("tests that assign a local of their module's init block");
        }
        self.test_own = parts.own;
        let file = format!("test/{}", module_file(p, module.decl));
        self.dir = file.rsplit_once('/').map_or("", |(d, _)| d).to_string();
        self.test_access.clear();
        self.imports.clear();
        // Nothing here is exported, for host code or another module.
        self.host = None;
        // What tests assign is `let`, not `const`.
        for &test in &module.tests {
            walk(p, test, &mut |id| {
                if let Node::Assign { local, .. } = p.nodes.get(id) {
                    self.assigned.insert(local);
                }
            });
        }
        // What only tests reach, first.
        let mut body = Vec::new();
        self.declarations(module, &module.test_items, module.test_init, &mut body)?;
        for &test in &module.tests {
            let Node::Test {
                name,
                params,
                body: block,
                ..
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
            let class = self.ty(p.decls.get(param).ty)?;
            self.begin(test);
            let mut stmts = self.stmts(block)?;
            if stmts.last().map(Stmt::bare) == Some(&Stmt::Return(None)) {
                stmts.pop();
            }
            // Each test ends by awaiting the async work it started.
            self.imports.insert("endTest");
            // Where the body's failure is kept, a name no local has.
            let failure = self.fresh("failure");
            body.push(
                Stmt::Test {
                    name: p.strings.get(name).to_string(),
                    test: self.name(param),
                    class,
                    failure,
                    body: stmts,
                }
                .spanned(p.nodes.span(test)),
            );
        }
        // Node's runner's `it`, first among the other modules' names.
        let mut from = vec![(TEST_MODULE.to_string(), vec![TEST_RUNNER[0].to_string()])];
        from.extend(self.take_from());
        Ok(Some(tree::Module {
            namespace: None,
            imports: self.imports.iter().map(|s| s.to_string()).collect(),
            from,
            body,
        }))
    }

    fn function(&mut self, item: NodeId) -> R<Stmt> {
        let p = self.p;
        let Node::Function { decl, params, body } = p.nodes.get(item) else {
            return not_yet("a function that isn't one");
        };
        let (params, result, body) = self.function_parts(item, decl, params, body)?;
        Ok(Stmt::Function {
            generator: false,
            export: self.exported(decl),
            name: self.name(decl),
            name_span: p.decls.get(decl).name_span,
            source_name: p.name(decl).to_string(),
            params,
            result,
            body,
        }
        .spanned(p.nodes.span(item)))
    }

    /// The declared failure type a function or method fails with, for
    /// JSDoc's `@throws`, as a parameter named `@throws` the printer takes
    /// for one; nothing for one that bubbles, or fails with a type
    /// parameter.
    fn throws(&mut self, decl: DeclId) -> R<Option<(String, String)>> {
        match temper_tmpl::failures::declared(self.p, decl) {
            Some(fail) => Ok(Some(("@throws".to_string(), self.ty(fail)?))),
            None => Ok(None),
        }
    }

    /// A function's or method's parameters with their types, its result's
    /// type, if not `Void` (or a constructor's), and its body.
    #[allow(clippy::type_complexity)]
    fn function_parts(
        &mut self,
        item: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: NodeId,
    ) -> R<(Vec<(String, String)>, Option<String>, Vec<Stmt>)> {
        let p = self.p;
        let mut ps: Vec<(String, String)> = (self.templates(decl)?.into_iter())
            .map(|t| ("@template".to_string(), t))
            .collect();
        ps.extend(self.params(params)?);
        let result = match p.signature(decl) {
            Some(_) if p.decls.get(decl).kind == DeclKind::Constructor => None,
            Some(sig) if sig.pass != TypeId::VOID => Some(self.ty(sig.pass)?),
            _ => None,
        };
        ps.extend(self.throws(decl)?);
        self.begin(item);
        let mut stmts = self.stmts(body)?;
        // A `return` without a value that ends a function says nothing.
        if result.is_none() && stmts.last().map(Stmt::bare) == Some(&Stmt::Return(None)) {
            stmts.pop();
        }
        Ok((ps, result, stmts))
    }

    /// A function literal: an arrow function where its body is one
    /// `return` of an expression that needs no statements, else a
    /// `function` declared before the statement it's in, named, as
    /// Kotlin's are.
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
        let stmts = match p.nodes.get(body) {
            Node::Block { stmts } => p.nodes.list(stmts).to_vec(),
            _ => Vec::new(),
        };
        // The function around it, whose loops and labels its own replace.
        let saved = (
            std::mem::take(&mut self.loops),
            std::mem::take(&mut self.labels),
        );
        let result = (|| -> R<Expr> {
            if let [ret] = stmts.as_slice() {
                if let Node::Return { value: Some(value) } = p.nodes.get(*ret) {
                    let outer = std::mem::take(&mut self.pre);
                    self.begin(id);
                    let value = self.expr(value);
                    let pre = std::mem::replace(&mut self.pre, outer);
                    let value = value?;
                    if pre.is_empty() {
                        let names = (self.params(params)?.into_iter()).map(|(n, _)| n).collect();
                        return Ok(Expr::Arrow(names, Box::new(value)));
                    }
                }
            }
            let name = self.name(decl);
            let function = self.literal_function(id, decl, params, body, None)?;
            self.pre.push(function);
            Ok(Expr::name(name))
        })();
        let (loops, labels) = saved;
        self.loops = loops;
        self.labels = labels;
        result
    }

    /// A generator's literal, as Kotlin's: a `function*` whose first
    /// parameter is a `doAwait`, which an `await` yields a call of, `yield
    /// doAwait(p)`, made a factory by the runtime's `adaptAwaiter`, which
    /// steps the generator when the promise is resolved.
    fn generator(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: NodeId,
    ) -> R<Expr> {
        let awaiter = self.fresh("doAwait");
        self.awaiters.push(awaiter.clone());
        let function = self.literal_function(id, decl, params, body, None);
        self.awaiters.pop();
        let mut function = function?;
        let bare = match &mut function {
            Stmt::Spanned(_, s) => s.as_mut(),
            s => s,
        };
        if let Stmt::Function {
            generator,
            name,
            params,
            result,
            ..
        } = bare
        {
            *generator = true;
            // What it yields is the runtime's business: `doAwait`'s
            // results, and a `yield`'s nothing.
            *result = Some("Generator<any>".into());
            let at = params.iter().take_while(|(n, _)| n == "@template").count();
            let ty = "(p: Promise<unknown>) => void".to_string();
            params.insert(at, (awaiter, ty));
            let name = name.clone();
            self.pre.push(function);
            self.import("adaptAwaiter");
            return Ok(Expr::name("adaptAwaiter").call(vec![Expr::name(name)]));
        }
        not_yet("a generator's function")
    }

    /// A name for a new local, past any name in use.
    fn fresh(&self, base: &str) -> String {
        (0..)
            .map(|n| match n {
                0 => base.to_string(),
                n => format!("{base}_{n}"),
            })
            .find(|name| {
                !(self.p.decls.ids()).any(|d| self.names.get(d) == name)
                    && !self.awaiters.contains(name)
            })
            .unwrap_or_default()
    }

    /// A function literal as a `function` declaration named `name`, its
    /// body translated as a function's of its own.
    fn literal_function(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: NodeId,
        named: Option<DeclId>,
    ) -> R<Stmt> {
        let p = self.p;
        let saved = (
            std::mem::take(&mut self.loops),
            std::mem::take(&mut self.labels),
        );
        let parts = self.function_parts(id, decl, params, body);
        let (loops, labels) = saved;
        self.loops = loops;
        self.labels = labels;
        let (params, result, body) = parts?;
        // A local function's name maps to the local's; a literal's has no
        // name in the source.
        let (name, name_span, source_name) = match named {
            Some(local) => (
                self.name(local),
                p.decls.get(local).name_span,
                p.name(local).to_string(),
            ),
            None => (self.name(decl), temper_tmpl::Span::UNKNOWN, String::new()),
        };
        Ok(Stmt::Function {
            generator: false,
            export: false,
            name,
            name_span,
            source_name,
            params,
            result,
            body,
        }
        .spanned(p.nodes.span(id)))
    }

    /// Parameters' names and types.
    fn params(&mut self, params: temper_tmpl::Kids) -> R<Vec<(String, String)>> {
        let p = self.p;
        let mut ps = Vec::new();
        let list = p.nodes.list(params);
        let trailing = (list.iter().rev())
            .take_while(|&&param| {
                matches!(p.nodes.get(param), Node::Param { decl }
                    if p.decls.get(decl).facts.contains(Facts::OPTIONAL))
            })
            .count();
        for (i, &param) in list.iter().enumerate() {
            if let Node::Param { decl } = p.nodes.get(param) {
                let ty = self.ty(p.decls.get(decl).ty)?;
                // An optional one after the last required one may be left
                // out, as Kotlin's: `[x]`.
                if i + trailing >= list.len() {
                    ps.push((format!("[{}]", self.name(decl)), ty));
                } else {
                    ps.push((self.name(decl), ty));
                }
            }
        }
        Ok(ps)
    }

    /// Starts translating a function or the init block: plans which loops
    /// need labels, those that a jump from an inner loop leaves or
    /// continues. A label takes its source name, or for a compiler-made
    /// one, `outer`, numbered only if an enclosing label has it.
    fn begin(&mut self, scope: NodeId) {
        fn visit(p: &Program, id: NodeId, loops: &mut Vec<DeclId>, labeled: &mut Vec<DeclId>) {
            match p.nodes.get(id) {
                Node::While { label, .. } | Node::DoWhile { label, .. } => {
                    loops.push(label);
                    for &kid in p.nodes.kids(id) {
                        visit(p, kid, loops, labeled);
                    }
                    loops.pop();
                }
                Node::Break { label } | Node::Continue { label } => {
                    if loops.last() != Some(&label) && !labeled.contains(&label) {
                        labeled.push(label);
                    }
                }
                _ => {
                    for &kid in p.nodes.kids(id) {
                        visit(p, kid, loops, labeled);
                    }
                }
            }
        }
        let p = self.p;
        let mut labeled = Vec::new();
        visit(p, scope, &mut Vec::new(), &mut labeled);
        self.loops.clear();
        self.labels.clear();
        let mut taken: HashSet<String> = HashSet::new();
        for l in labeled {
            let d = p.decls.get(l);
            let mut base = if d.facts.contains(Facts::COMPILER_MADE) || p.name(l).is_empty() {
                "outer".to_string()
            } else {
                p.name(l).to_string()
            };
            if KEYWORDS.contains(&base.as_str()) || SPECIAL.contains(&base.as_str()) {
                base.push('_');
            }
            let name = (1..)
                .map(|n| match n {
                    1 => base.clone(),
                    n => format!("{base}{n}"),
                })
                .find(|n| !taken.contains(n))
                .unwrap_or_default();
            taken.insert(name.clone());
            self.labels.insert(l, name);
        }
    }

    fn block(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
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

    fn stmts(&mut self, id: NodeId) -> R<Vec<Stmt>> {
        let mut out = Vec::new();
        self.block(id, &mut out)?;
        Ok(out)
    }

    fn loop_body(&mut self, label: DeclId, body: NodeId) -> R<Vec<Stmt>> {
        self.loops.push(label);
        let body = self.stmts(body);
        self.loops.pop();
        body
    }

    /// Translates a statement, giving what it becomes its span.
    fn stmt(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let start = out.len();
        let saved = std::mem::take(&mut self.pre);
        let mut here = Vec::new();
        let done = self.stmt_unspanned(id, &mut here);
        let pre = std::mem::replace(&mut self.pre, saved);
        done?;
        out.extend(pre);
        out.extend(here);
        let span = self.p.nodes.span(id);
        for s in &mut out[start..] {
            *s = std::mem::replace(s, Stmt::Return(None)).spanned(span);
        }
        Ok(())
    }

    fn stmt_unspanned(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            // A block inside a block keeps its braces: its `let`s may
            // share names with another block's (names::RULES).
            Node::Block { .. } => out.push(Stmt::Block(self.stmts(id)?)),
            Node::Let {
                local,
                init: Some(init),
            } if is_handler(p, init) => {
                let name = self.name(local);
                out.push(Stmt::Decl {
                    export: false,
                    keyword: Keyword::Let,
                    name: name.clone(),
                    ty: Some(self.ty(p.decls.get(local).ty)?),
                    value: None,
                });
                out.push(self.try_assign(Expr::name(name), init)?);
            }
            // A local function: a `function` of the local's name.
            Node::Let {
                local,
                init: Some(init),
            } if !self.assigned.contains(&local)
                && matches!(p.nodes.get(init), Node::FunctionLiteral { decl, .. }
                    if !p.decls.get(decl).facts.contains(Facts::GENERATOR)) =>
            {
                let Node::FunctionLiteral { decl, params, body } = p.nodes.get(init) else {
                    unreachable!()
                };
                out.push(self.literal_function(init, decl, params, body, Some(local))?);
            }
            // A parameter's default: `x ??= d`; for null, `x ??= null`,
            // so one left out (`undefined`) is null where it's kept, as
            // in a field.
            Node::Let {
                local,
                init: Some(init),
            } if self.param_default(init).is_some() => {
                let (param, value) = self.param_default(init).unwrap();
                self.defaulted.insert(local, param);
                if matches!(p.nodes.get(value), Node::Null {}) {
                    let target = Expr::name(self.name(param));
                    out.push(Stmt::AssignCoalesce {
                        target,
                        value: Expr::Null,
                    });
                } else {
                    let target = Expr::name(self.name(param));
                    let (mut pre, value) = self.hoisted(value)?;
                    if pre.is_empty() {
                        out.push(Stmt::AssignCoalesce { target, value });
                    } else {
                        pre.push(Stmt::Assign {
                            target: target.clone(),
                            value,
                        });
                        out.push(Stmt::If {
                            cond: Expr::bin(Bin::LooseEq, target, Expr::Null),
                            then: pre,
                            els: Vec::new(),
                        });
                    }
                }
            }
            Node::Let { local, init } => {
                let name = self.name(local);
                out.push(match init {
                    Some(init) => Stmt::Decl {
                        export: false,
                        keyword: if self.assigned.contains(&local) {
                            Keyword::Let
                        } else {
                            Keyword::Const
                        },
                        name,
                        ty: self.decl_ty(p.decls.get(local).ty, init)?,
                        value: Some(self.expr(init)?),
                    },
                    None => Stmt::Decl {
                        export: false,
                        keyword: Keyword::Let,
                        name,
                        ty: Some(self.ty(p.decls.get(local).ty)?),
                        value: None,
                    },
                });
            }
            Node::SetModuleLet { target, value } if !self.is_here(target) => {
                // Another module's `var`, which only its setter assigns.
                let value = self.expr(value)?;
                let setter = format!("{}$set", self.name(target));
                let setter = self.import_as(target, setter);
                out.push(Stmt::Expr(Expr::name(setter).call(vec![value])));
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
                let target = Expr::name(self.name(target));
                if is_handler(p, value) {
                    out.push(self.try_assign(target, value)?);
                    return Ok(());
                }
                let value = self.expr(value)?;
                out.push(Stmt::Assign { target, value });
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
                let (pre, cond) = self.hoisted(cond)?;
                let mut body = self.loop_body(label, body)?;
                // A condition with statements of its own runs them each
                // time round: `while (true) { …; if (!c) break; … }`.
                let cond = if pre.is_empty() {
                    cond
                } else {
                    let exit = Stmt::If {
                        cond: ops::apply(Spelling::Unary(tree::Unary::Not), vec![cond]),
                        then: vec![Stmt::Break(None)],
                        els: Vec::new(),
                    };
                    body.splice(0..0, pre.into_iter().chain([exit]));
                    Expr::Bool(true)
                };
                out.push(Stmt::While {
                    label: self.labels.get(&label).cloned(),
                    cond,
                    body,
                });
            }
            Node::DoWhile { label, body, cond } => {
                let body = self.loop_body(label, body)?;
                let (pre, cond) = self.hoisted(cond)?;
                if !pre.is_empty() {
                    return not_yet("a do … while condition with statements of its own");
                }
                out.push(Stmt::DoWhile {
                    label: self.labels.get(&label).cloned(),
                    body,
                    cond,
                });
            }
            Node::Break { label } | Node::Continue { label } => {
                let target = if self.loops.last() == Some(&label) {
                    None
                } else {
                    match self.labels.get(&label) {
                        Some(l) => Some(l.clone()),
                        None => return not_yet("a jump to a loop that doesn't enclose it"),
                    }
                };
                out.push(if matches!(p.nodes.get(id), Node::Break { .. }) {
                    Stmt::Break(target)
                } else {
                    Stmt::Continue(target)
                });
            }
            Node::Return { value: Some(value) } if is_handler(p, value) => {
                let (body, fallback, _) = handler_parts(p, value);
                let (mut pre, body) = self.part(body)?;
                pre.push(Stmt::Return(Some(body)));
                let (mut catch, fallback) = self.part(fallback)?;
                catch.push(Stmt::Return(Some(fallback)));
                out.push(self.try_catch(pre, catch, value)?);
            }
            Node::Return { value } => {
                let value = match value {
                    Some(v) if !matches!(p.nodes.get(v), Node::VoidLit {}) => Some(self.expr(v)?),
                    _ => None,
                };
                out.push(Stmt::Return(value));
            }
            Node::ExprStatement { expr } if is_handler(p, expr) => {
                let (body, fallback, _) = handler_parts(p, expr);
                let body = self.effects(body)?;
                let catch = self.effects(fallback)?;
                out.push(self.try_catch(body, catch, expr)?);
            }
            Node::ExprStatement { expr } => match self.setter_call(expr)? {
                Some(assign) => out.push(assign),
                None => {
                    let e = self.expr(expr)?;
                    out.push(Stmt::Expr(e));
                }
            },
            Node::SetField {
                field,
                object,
                value,
            } => {
                let target = self.field(field, object)?;
                let value = self.expr(value)?;
                out.push(Stmt::Assign { target, value });
            }
            Node::InitStatic { target, value } => {
                let target = match self.static_storage(target) {
                    Some(storage) => Expr::name(storage),
                    None => {
                        let class = self.class_ref(p.decls.get(target).owner);
                        class.member(self.name(target))
                    }
                };
                let value = self.expr(value)?;
                out.push(Stmt::Assign { target, value });
            }
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

    /// For a `let`'s initializer that's an optional parameter's value
    /// coalesced with its default, read only there: the parameter and the
    /// default.
    fn param_default(&self, init: NodeId) -> Option<(DeclId, NodeId)> {
        let p = self.p;
        let Node::Coalesce { left, right } = p.nodes.get(init) else {
            return None;
        };
        let Node::Local { local: param } = p.nodes.get(left) else {
            return None;
        };
        let d = p.decls.get(param);
        (d.kind == DeclKind::Param && d.facts.contains(Facts::OPTIONAL)).then_some((param, right))
    }

    /// An expression and the statements hoisted out of it, which must run
    /// first.
    fn hoisted(&mut self, id: NodeId) -> R<(Vec<Stmt>, Expr)> {
        let saved = std::mem::take(&mut self.pre);
        let e = self.expr(id);
        let pre = std::mem::replace(&mut self.pre, saved);
        Ok((pre, e?))
    }

    /// An `orelse`'s part, for a block of its own (a `try`'s or a
    /// `catch`'s): a block value's statements go there directly.
    fn part(&mut self, id: NodeId) -> R<(Vec<Stmt>, Expr)> {
        let p = self.p;
        if let Node::BlockValue { stmts, value } = p.nodes.get(id) {
            let stmts = p.nodes.list(stmts);
            let saved = std::mem::take(&mut self.pre);
            let coalesced = self.coalesced(stmts, value);
            let pre = std::mem::replace(&mut self.pre, saved);
            if let Some(e) = coalesced? {
                return Ok((pre, e));
            }
            let mut out = pre;
            for &s in stmts {
                self.stmt(s, &mut out)?;
            }
            let (pre, value) = self.hoisted(value)?;
            out.extend(pre);
            return Ok((out, value));
        }
        self.hoisted(id)
    }

    /// An expression run for its effects alone, as statements: none for a
    /// value without effects.
    fn effects(&mut self, id: NodeId) -> R<Vec<Stmt>> {
        let (mut pre, e) = self.part(id)?;
        if !matches!(
            e,
            Expr::Name(_)
                | Expr::Int(_)
                | Expr::Float(_)
                | Expr::BigInt(_)
                | Expr::Str(_)
                | Expr::Bool(_)
                | Expr::Undefined
                | Expr::Null
                | Expr::This
        ) {
            pre.push(Stmt::Expr(e));
        }
        Ok(pre)
    }

    /// `try { target = body; } catch { target = fallback; }`, for a
    /// handler, an `orelse` or a `catch`.
    fn try_assign(&mut self, target: Expr, handler: NodeId) -> R<Stmt> {
        let (body, fallback, _) = handler_parts(self.p, handler);
        let (mut pre, body) = self.part(body)?;
        pre.push(Stmt::Assign {
            target: target.clone(),
            value: body,
        });
        let (mut catch, value) = self.part(fallback)?;
        catch.push(Stmt::Assign { target, value });
        self.try_catch(pre, catch, handler)
    }

    /// A handler's `try` ([`Stmt::Try`]), an `orelse`'s or a `catch`'s,
    /// catching exactly what its body may fail with: a bubble, by
    /// `isBubble(e$)`, and the failure class nearest above its declared
    /// failures, by `instanceof` (a `catch`'s binding, narrowed so for
    /// TypeScript), or where that's core's `Error`, for failures of
    /// several libraries' roots, by the runtime's `isFailure(e$)`.
    /// Anything else, a panic or a host's error, passes it, even where
    /// the fallback only panics, whose panic then has what it caught as
    /// its `cause`, `panic("panic", e$)`. Not yet for a `catch` binding a
    /// failure beside a bubble, which TypeScript can't narrow to the
    /// failure's type.
    fn try_catch(&mut self, body: Vec<Stmt>, mut catch: Vec<Stmt>, handler: NodeId) -> R<Stmt> {
        let p = self.p;
        let (body_node, fallback, local) = handler_parts(p, handler);
        let var = match local {
            Some(local) => self.name(local),
            None => "e$".to_string(),
        };
        let fails: Vec<TypeId> = (temper_tmpl::failures::failures_under(p, body_node).into_iter())
            .map(|(_, f)| f)
            .collect();
        let mut tests = Vec::new();
        // A body that can't fail, as one `to_tmpl` kept: `Bubble`'s.
        if fails.is_empty() || fails.contains(&TypeId::BUBBLE) {
            self.imports.insert("isBubble");
            tests.push(Expr::name("isBubble").call(vec![Expr::name(var.clone())]));
        }
        let declared: Vec<TypeId> = fails.into_iter().filter(|&f| f != TypeId::BUBBLE).collect();
        let root = temper_tmpl::failures::root(p);
        if let Some(ty) = temper_tmpl::failures::join(p, root, &declared) {
            let class = nominal_decl(p, ty);
            if class == root {
                self.imports.insert("isFailure");
                tests.push(Expr::name("isFailure").call(vec![Expr::name(var.clone())]));
            } else {
                let Some(class) = class else {
                    return not_yet("a handler of a failure that isn't a class's");
                };
                let class = self.class_ref(class);
                tests.push(Expr::bin(Bin::InstanceOf, Expr::name(var.clone()), class));
            }
        }
        if local.is_some() && tests.len() > 1 {
            return not_yet("a catch that binds a failure beside a bubble");
        }
        let caught = (tests.into_iter())
            .reduce(|a, b| Expr::bin(Bin::Or, a, b))
            .unwrap();
        if matches!(p.nodes.get(fallback), Node::Panic {}) {
            let caused =
                Expr::name("panic").call(vec![Expr::Str("panic".into()), Expr::name(var.clone())]);
            match catch.last_mut() {
                Some(
                    Stmt::Assign { value, .. } | Stmt::Return(Some(value)) | Stmt::Expr(value),
                ) => {
                    *value = caused;
                }
                _ => catch.push(Stmt::Expr(caused)),
            }
        }
        Ok(Stmt::Try {
            body,
            var,
            caught,
            catch,
        })
    }

    /// A fresh temporary's name.
    fn temp(&mut self) -> String {
        let name = format!("t${}", self.temps);
        self.temps += 1;
        name
    }

    /// A temporary declared with `let`, in the statements hoisted before
    /// this one, initialized: untyped, as Kotlin's, since TypeScript
    /// follows the type of what's assigned.
    fn hoist_let(&mut self, value: Expr) -> R<String> {
        self.hoist_decl(None, Some(value))
    }

    /// A temporary for node `id`'s value, declared with `let` and
    /// assigned later (`let t; { …; t = v; }`): typed as the node, as
    /// other locals declared without a value are, since TypeScript
    /// can't always follow the assignments (`t` is implicitly `any`
    /// where a `panic()` comes before one).
    fn hoist_for(&mut self, id: NodeId) -> R<String> {
        let ty = self.type_of(id)?;
        self.hoist_decl(Some(ty), None)
    }

    fn hoist_decl(&mut self, ty: Option<String>, value: Option<Expr>) -> R<String> {
        let name = self.temp();
        self.pre.push(Stmt::Decl {
            export: false,
            keyword: Keyword::Let,
            name: name.clone(),
            ty,
            value,
        });
        Ok(name)
    }

    /// Expressions evaluated in order: where a later one hoists
    /// statements, earlier ones that aren't constants are kept in
    /// temporaries first, so they still run first.
    /// Whether the statements hoisted since `mark` do anything: all but a
    /// function literal's declaration, which only names a function.
    fn effects_since(&self, mark: usize) -> bool {
        (self.pre[mark..].iter()).any(|s| !matches!(s.bare(), Stmt::Function { .. }))
    }

    pub(crate) fn in_order(&mut self, ids: &[NodeId]) -> R<Vec<Expr>> {
        let mut out: Vec<Expr> = Vec::new();
        for &id in ids {
            let mark = self.pre.len();
            let e = self.expr(id)?;
            if self.effects_since(mark) {
                let mut spilled = Vec::new();
                for earlier in &mut out {
                    if is_constant(earlier) {
                        continue;
                    }
                    let name = self.temp();
                    let value = std::mem::replace(earlier, Expr::name(name.clone()));
                    spilled.push(Stmt::Decl {
                        export: false,
                        keyword: Keyword::Const,
                        name,
                        ty: None,
                        value: Some(value),
                    });
                }
                self.pre.splice(mark..mark, spilled);
            }
            out.push(e);
        }
        Ok(out)
    }

    /// `x ?? fallback` for `??`'s form in tmpl: a block value letting a
    /// local, then `if (isNull(local)) fallback else nonNull(local)`, the
    /// local read nowhere else.
    fn coalesced(&mut self, stmts: &[NodeId], value: NodeId) -> R<Option<Expr>> {
        let p = self.p;
        let [only] = stmts else {
            return Ok(None);
        };
        let Node::Let {
            local,
            init: Some(init),
        } = p.nodes.get(*only)
        else {
            return Ok(None);
        };
        let Node::IfValue { cond, then, els } = p.nodes.get(value) else {
            return Ok(None);
        };
        let reads = |id: NodeId| matches!(p.nodes.get(id), Node::Local { local: l } if l == local);
        let checks = matches!(p.nodes.get(cond), Node::IsNull { value: v } if reads(v));
        let narrows = matches!(p.nodes.get(els), Node::NonNull { value: v } if reads(v));
        let mut elsewhere = false;
        walk(p, then, &mut |id| elsewhere |= reads(id));
        if !checks || !narrows || elsewhere {
            return Ok(None);
        }
        let (pre, fallback) = self.hoisted(then)?;
        if !pre.is_empty() {
            return Ok(None);
        }
        let init = self.expr(init)?;
        Ok(Some(Expr::bin(Bin::Coalesce, init, fallback)))
    }

    /// A call of a connected declaration, by the connection table; `args`
    /// has a method's receiver first.
    fn connected(&mut self, decl: DeclId, args: &[NodeId]) -> R<Expr> {
        let qname = self.qname(decl);
        let Some(connection) = connected::lookup(&qname) else {
            return not_yet(format!("a connection for {qname}"));
        };
        match connection {
            Connection::Math(name) => {
                let args = self.args(args)?;
                Ok(Expr::name("Math").method(name, args))
            }
            Connection::Helper(name) => {
                // An argument left out is `null`, which the helpers take
                // for their defaults, and TypeScript wants (`{number?}`).
                let given: Vec<NodeId> = (args.iter().copied())
                    .filter(|&a| !matches!(self.p.nodes.get(a), Node::Omitted {}))
                    .collect();
                let mut given = self.in_order(&given)?.into_iter();
                let args = (args.iter())
                    .map(|&a| match self.p.nodes.get(a) {
                        Node::Omitted {} => Expr::Null,
                        _ => given.next().unwrap_or(Expr::Null),
                    })
                    .collect();
                self.imports.insert(name);
                Ok(Expr::name(name).call(args))
            }
            Connection::ListHelper(name) => {
                let Some((&first, rest)) = args.split_first() else {
                    return not_yet(format!("a receiver for {qname}"));
                };
                if (rest.iter()).any(|&a| matches!(self.p.nodes.get(a), Node::Omitted {})) {
                    return not_yet(format!("an argument left out of {qname}"));
                }
                // The receiver first, then the rest, in order: a
                // receiver kept in a temporary where the rest need
                // statements first (`ls[i++]`).
                let mut receiver = self.list_arg(first)?;
                let mark = self.pre.len();
                let mut rest = self.in_order(rest)?;
                if self.effects_since(mark) && !is_constant(&receiver) {
                    let name = self.temp();
                    let value = std::mem::replace(&mut receiver, Expr::name(name.clone()));
                    let spilled = Stmt::Decl {
                        export: false,
                        keyword: Keyword::Const,
                        name,
                        ty: None,
                        value: Some(value),
                    };
                    self.pre.insert(mark, spilled);
                }
                let mut all = vec![receiver];
                all.append(&mut rest);
                self.imports.insert(name);
                Ok(Expr::name(name).call(all))
            }
            Connection::Translate(f) => f(self, args),
            Connection::Erased => not_yet(format!("a value of {qname}, whose type is erased")),
        }
    }

    /// Arguments: those `Omitted` at the end left out, as Kotlin's, and
    /// any before one given `undefined`, either way the parameter's
    /// default.
    fn args(&mut self, args: &[NodeId]) -> R<Vec<Expr>> {
        let p = self.p;
        let given = (args.iter())
            .rposition(|&a| !matches!(p.nodes.get(a), Node::Omitted {}))
            .map_or(0, |i| i + 1);
        let ids: Vec<NodeId> = (args[..given].iter().copied())
            .filter(|&a| !matches!(p.nodes.get(a), Node::Omitted {}))
            .collect();
        let mut values = self.in_order(&ids)?.into_iter();
        Ok((args[..given].iter())
            .map(|&a| match p.nodes.get(a) {
                Node::Omitted {} => Expr::Undefined,
                _ => values.next().unwrap(),
            })
            .collect())
    }

    pub(crate) fn expr(&mut self, id: NodeId) -> R<Expr> {
        let p = self.p;
        Ok(match p.nodes.get(id) {
            Node::BoolLit { value } => Expr::Bool(value),
            Node::Int32Lit { value } => Expr::Int(value.into()),
            Node::Int64Lit { value } => Expr::BigInt(value),
            Node::Float64Lit { value } => Expr::Float(value),
            Node::StringLit { value } => Expr::Str(p.strings.get(value).to_string()),
            Node::VoidLit {} => Expr::Undefined,
            // JS has no value for a builtin type but its `typeof` name.
            Node::TypeValue { of } => Expr::Str(
                match p.types.get(of) {
                    TypeData::Scalar(s) => match s {
                        Scalar::Void => "undefined",
                        Scalar::Boolean => "boolean",
                        Scalar::Int32 | Scalar::Float64 => "number",
                        Scalar::Int64 => "bigint",
                        Scalar::String => "string",
                        _ => return not_yet("this type as a value"),
                    },
                    _ => return not_yet("this type as a value"),
                }
                .into(),
            ),
            Node::Local { local: decl } => Expr::name(self.name(decl)),
            Node::GetModuleLet { target } => {
                if self.erased.contains(&target) {
                    return not_yet("a value whose type is erased");
                }
                Expr::name(self.top(target))
            }
            Node::CallFunction { callee, args, .. } => {
                let args = p.nodes.list(args);
                if is_external(p, callee) && library_of(p, callee) == "core"
                    || by_connection(p, callee, crate::connected::connects_std)
                {
                    return self.connected(callee, args);
                }
                let args = self.args(args)?;
                let owner = p.decls.get(callee).owner;
                if matches!(
                    p.decls.get(owner).kind,
                    DeclKind::Class | DeclKind::Interface
                ) {
                    let class = self.class_ref(owner);
                    class.method(self.member_name(callee), args)
                } else {
                    Expr::name(self.top(callee)).call(args)
                }
            }
            Node::CallMethod {
                method,
                receiver,
                args,
                ..
            } => {
                if !by_connection(p, method, crate::connected::connects_std) {
                    return self.method_call(method, receiver, p.nodes.list(args));
                }
                let mut all = vec![receiver];
                all.extend_from_slice(p.nodes.list(args));
                self.connected(method, &all)?
            }
            Node::Op { op, args, .. } => {
                let Some(spelling) = ops::spelling(op) else {
                    return not_yet("an operator without a spelling");
                };
                if spelling == Spelling::Cat {
                    return self.cat(id);
                }
                // A negated literal is a literal, wrapped as the type
                // wraps (`-(-2147483648)` is itself).
                if let [arg] = p.nodes.list(args) {
                    match (spelling, p.nodes.get(*arg)) {
                        (Spelling::Neg32, Node::Int32Lit { value }) => {
                            return Ok(Expr::Int(value.wrapping_neg().into()));
                        }
                        (Spelling::Neg64, Node::Int64Lit { value }) => {
                            return Ok(Expr::BigInt(value.wrapping_neg()));
                        }
                        _ => {}
                    }
                }
                let args = self.in_order(p.nodes.list(args))?;
                if let Some(helper) = ops::helper(spelling) {
                    self.imports.insert(helper);
                }
                ops::apply(spelling, args)
            }
            Node::And { left, right } | Node::Or { left, right } => {
                let and = matches!(p.nodes.get(id), Node::And { .. });
                let left = self.expr(left)?;
                let (pre, right) = self.hoisted(right)?;
                if pre.is_empty() {
                    let op = if and { Bin::And } else { Bin::Or };
                    return Ok(Expr::bin(op, left, right));
                }
                // `let t = a; if (t) { …; t = b; }`, so `b`'s statements
                // run only when it does.
                let t = self.hoist_let(left)?;
                let mut then = pre;
                then.push(Stmt::Assign {
                    target: Expr::name(t.clone()),
                    value: right,
                });
                let cond = if and {
                    Expr::name(t.clone())
                } else {
                    Expr::unary(tree::Unary::Not, Expr::name(t.clone()))
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
                let is_or_else = |id: NodeId| is_handler(p, id);
                let (mut then_pre, mut then_e) = (Vec::new(), Expr::Undefined);
                let (mut els_pre, mut els_e) = (Vec::new(), Expr::Undefined);
                if !is_or_else(then) && !is_or_else(els) {
                    (then_pre, then_e) = self.hoisted(then)?;
                    (els_pre, els_e) = self.hoisted(els)?;
                    if then_pre.is_empty() && els_pre.is_empty() {
                        return Ok(Expr::Cond {
                            cond: Box::new(cond),
                            then: Box::new(then_e),
                            els: Box::new(els_e),
                        });
                    }
                }
                let t = self.hoist_for(id)?;
                // Each branch assigns the temporary; one that's an
                // `orelse` does so in its own `try`.
                let branch = |this: &mut Self, id: NodeId, pre: Vec<Stmt>, value: Expr| {
                    let target = Expr::name(t.clone());
                    if is_handler(p, id) {
                        return Ok(vec![this.try_assign(target, id)?]);
                    }
                    let (mut pre, value) = if pre.is_empty() && value == Expr::Undefined {
                        this.hoisted(id)?
                    } else {
                        (pre, value)
                    };
                    pre.push(Stmt::Assign { target, value });
                    Ok::<_, NotYet>(pre)
                };
                let then = branch(self, then, then_pre, then_e)?;
                let els = branch(self, els, els_pre, els_e)?;
                self.pre.push(Stmt::If { cond, then, els });
                Expr::name(t)
            }
            Node::BlockValue { stmts, value } => {
                let stmts = p.nodes.list(stmts);
                if let Some(e) = self.coalesced(stmts, value)? {
                    return Ok(e);
                }
                // `let t; { …; t = value; }`, the braces scoping its
                // locals.
                let t = self.hoist_for(id)?;
                let mut inner = Vec::new();
                for &s in stmts {
                    self.stmt(s, &mut inner)?;
                }
                let (pre, value) = self.hoisted(value)?;
                inner.extend(pre);
                inner.push(Stmt::Assign {
                    target: Expr::name(t.clone()),
                    value,
                });
                self.pre.push(Stmt::Block(inner));
                Expr::name(t)
            }
            Node::OrElse { .. } | Node::Catch { .. } => {
                let t = self.hoist_for(id)?;
                let made = self.try_assign(Expr::name(t.clone()), id)?;
                self.pre.push(made);
                Expr::name(t)
            }
            // A declared failure's `throw`, with its cause, the failure a
            // handler around it handles, where it's thrown there: through
            // the runtime's `withCause`, which keeps a cause it has.
            Node::Throw { value, cause, .. } => {
                let mut thrown = self.expr(value)?;
                if let Some(cause) = cause {
                    let cause = self.expr(cause)?;
                    self.imports.insert("withCause");
                    thrown = Expr::name("withCause").call(vec![thrown, cause]);
                }
                Expr::Throw(Box::new(thrown))
            }
            Node::Null {} => Expr::Null,
            Node::IsNull { value } => Expr::bin(Bin::LooseEq, self.expr(value)?, Expr::Null),
            // The failure a `catch` whose fallback jumps keeps, read where
            // it's set, which TypeScript can't tell (notes/tmpl/nodes.md,
            // "Declared failures"): cast to its type.
            Node::NonNull { value } if self.is_kept_failure(value) => {
                let ty = self.type_of(id)?;
                Expr::Cast(ty, Box::new(self.expr(value)?))
            }
            Node::NonNull { value } | Node::AsNullable { value } => self.expr(value)?,
            Node::Coalesce { left, right } => {
                let left = self.expr(left)?;
                let (pre, right) = self.hoisted(right)?;
                if !pre.is_empty() {
                    return not_yet("a coalesced value's fallback with statements");
                }
                Expr::bin(Bin::Coalesce, left, right)
            }
            Node::EqNullable { op, left, right } => {
                let args = self.in_order(&[left, right])?;
                let [a, b] = <[Expr; 2]>::try_from(args).unwrap();
                let Some(spelling) = ops::spelling(op) else {
                    return not_yet("an equality without a spelling");
                };
                if spelling == Spelling::Bin(Bin::Eq) {
                    // `===` is null-safe already.
                    return Ok(Expr::bin(Bin::Eq, a, b));
                }
                // `a == null ? b == null : b != null && eq(a, b)`, each
                // side read once.
                let once = |e: Expr, this: &mut Self| match e {
                    Expr::Name(_) => e,
                    _ => {
                        let name = this.temp();
                        this.pre.push(Stmt::Decl {
                            export: false,
                            keyword: Keyword::Const,
                            name: name.clone(),
                            ty: None,
                            value: Some(e),
                        });
                        Expr::name(name)
                    }
                };
                let (a, b) = (once(a, self), once(b, self));
                if let Some(helper) = ops::helper(spelling) {
                    self.imports.insert(helper);
                }
                let eq = ops::apply(spelling, vec![a.clone(), b.clone()]);
                Expr::Cond {
                    cond: Box::new(Expr::bin(Bin::LooseEq, a, Expr::Null)),
                    then: Box::new(Expr::bin(Bin::LooseEq, b.clone(), Expr::Null)),
                    els: Box::new(Expr::bin(
                        Bin::And,
                        Expr::bin(Bin::LooseNe, b, Expr::Null),
                        eq,
                    )),
                }
            }
            Node::Bubble { .. } => {
                self.imports.insert("bubble");
                Expr::name("bubble").call(Vec::new())
            }
            Node::Panic {} => {
                self.imports.insert("panic");
                Expr::name("panic").call(Vec::new())
            }
            Node::This {} => Expr::This,
            // `Object.freeze([…])`, as Kotlin's.
            Node::ListLit { items } => {
                let items = self.in_order(p.nodes.list(items))?;
                Expr::name("Object").method("freeze", vec![Expr::Array(items)])
            }
            Node::GetField { field, object } => self.field(field, object)?,
            Node::GetStatic { target }
                if is_external(p, target) && library_of(p, target) == "core" =>
            {
                // `String.begin`, `Float64.pi`, and the like.
                self.connected(target, &[])?
            }
            Node::GetStatic { target } => self.static_read(target),
            Node::New { ctor, args, .. }
                if is_external(p, ctor) && library_of(p, ctor) == "core"
                    || by_connection(p, ctor, crate::connected::connects_std) =>
            {
                self.connected(ctor, p.nodes.list(args))?
            }
            Node::New { ctor, args, .. } => {
                let class = self.class_ref(p.decls.get(ctor).owner);
                Expr::New(Box::new(class), self.args(p.nodes.list(args))?)
            }
            Node::Upcast { value } | Node::Downcast { value } => {
                self.converted(value, p.nodes.ty(id))?
            }
            Node::As { value, .. } if p.types.non_null(p.nodes.ty(value)) == p.nodes.ty(id) => {
                // `T?` as `T`: a null check.
                let value = self.expr(value)?;
                self.imports.insert("requireNotNull");
                Expr::name("requireNotNull").call(vec![value])
            }
            Node::As { value, .. } if self.index_check(p.nodes.ty(id)).is_some() => {
                // A string index is a number: `requireStringIndex(i)`, as
                // Kotlin's.
                let name = match self.index_check(p.nodes.ty(id)) {
                    Some(Bin::Ge) => "requireStringIndex",
                    _ => "requireNoStringIndex",
                };
                let value = self.expr(value)?;
                self.imports.insert(name);
                Expr::name(name).call(vec![value])
            }
            Node::As { value, .. } => {
                // `null instanceof C` is false, so a nullable value is
                // checked as any.
                let Some(class) = nominal_decl(p, p.nodes.ty(id)) else {
                    return not_yet("`as` to a type that isn't a class's or interface's");
                };
                let value = self.expr(value)?;
                self.imports.insert("requireInstanceOf");
                let class = self.class_ref(class);
                Expr::name("requireInstanceOf").call(vec![value, class])
            }
            Node::Is { ty, value } if self.index_check(ty).is_some() => {
                // `i >= 0` for a string index, `i < 0` for none, as
                // Kotlin's.
                let check = self.index_check(ty).unwrap_or(Bin::Ge);
                Expr::bin(check, self.expr(value)?, Expr::Int(0))
            }
            Node::Is { ty, value }
                if nominal_decl(p, ty)
                    .is_some_and(|d| self.qname(d) == "core.type ValueResult") =>
            {
                // `!r.done`, for JS's own `IteratorResult`.
                let done = self.expr(value)?.member("done");
                Expr::unary(tree::Unary::Not, done)
            }
            Node::Is { ty, value }
                if nominal_decl(p, ty).is_some_and(|d| self.qname(d) == "core.type DoneResult") =>
            {
                Expr::bin(Bin::Eq, self.expr(value)?.member("done"), Expr::Bool(true))
            }
            Node::Is { ty, value } => {
                let Some(class) = nominal_decl(p, ty) else {
                    return not_yet("`is` of a type that isn't a class's or interface's");
                };
                let value = self.expr(value)?;
                Expr::bin(Bin::InstanceOf, value, self.class_ref(class))
            }
            Node::Identical { left, right } => {
                Expr::bin(Bin::Eq, self.expr(left)?, self.expr(right)?)
            }
            Node::Error { .. } => {
                self.imports.insert("bubble");
                Expr::name("bubble").call(Vec::new())
            }
            Node::FunctionLiteral { decl, params, body } => self.literal(id, decl, params, body)?,
            Node::Await { promise, .. } => {
                let promise = self.expr(promise)?;
                let Some(awaiter) = self.awaiters.last() else {
                    return not_yet("an await outside a generator");
                };
                let call = Expr::name(awaiter.clone()).call(vec![promise]);
                Expr::Yield(Some(Box::new(call)))
            }
            Node::FunctionRef { target } => {
                let owner = p.decls.get(target).owner;
                if matches!(
                    p.decls.get(owner).kind,
                    DeclKind::Class | DeclKind::Interface
                ) {
                    let class = self.class_ref(owner);
                    class.member(self.member_name(target))
                } else {
                    Expr::name(self.top(target))
                }
            }
            Node::CallValue { callee, args, .. } => {
                let f = self.expr(callee)?;
                f.call(self.args(p.nodes.list(args))?)
            }
            Node::ToFun { value } | Node::FromFun { value } => self.expr(value)?,
            other => return not_yet(format!("a {} expression", other.kind().name())),
        })
    }

    /// A chain of `String.cat` as one template literal: its literal parts
    /// as they are, and an `Int32.toString()` or `Boolean.toString()`
    /// without a radix as the value itself, which a template writes the
    /// same way. All literals give a string; strings alone, `a + b`.
    fn cat(&mut self, id: NodeId) -> R<Expr> {
        let mut parts = Vec::new();
        let mut converted = false;
        self.cat_parts(id, &mut parts, &mut converted)?;
        let lits = parts.iter().filter(|p| matches!(p, TPart::Lit(_))).count();
        if lits == parts.len() {
            let text: String = parts
                .iter()
                .filter_map(|p| match p {
                    TPart::Lit(s) => Some(s.as_str()),
                    TPart::Expr(_) => None,
                })
                .collect();
            return Ok(Expr::Str(text));
        }
        if lits == 0 && !converted {
            let terms = parts.into_iter().map(|p| match p {
                TPart::Expr(e) => e,
                TPart::Lit(s) => Expr::Str(s),
            });
            return Ok(terms
                .reduce(|a, b| Expr::bin(Bin::Add, a, b))
                .unwrap_or(Expr::Str(String::new())));
        }
        Ok(Expr::Template(parts))
    }

    fn cat_parts(&mut self, id: NodeId, parts: &mut Vec<TPart>, converted: &mut bool) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Op { op, args, .. } if ops::spelling(op) == Some(Spelling::Cat) => {
                for &a in p.nodes.list(args) {
                    self.cat_parts(a, parts, converted)?;
                }
            }
            Node::StringLit { value } => {
                let text = p.strings.get(value);
                if let Some(TPart::Lit(last)) = parts.last_mut() {
                    last.push_str(text);
                } else {
                    parts.push(TPart::Lit(text.to_string()));
                }
            }
            Node::CallMethod {
                method,
                receiver,
                args,
                ..
            } if matches!(
                self.qname(method).as_str(),
                "core.type Int32.toString()" | "core.type Boolean.toString()"
            ) && p
                .nodes
                .list(args)
                .iter()
                .all(|&a| matches!(p.nodes.get(a), Node::Omitted {})) =>
            {
                let e = self.expr(receiver)?;
                parts.push(TPart::Expr(e));
                *converted = true;
            }
            _ => {
                let e = self.expr(id)?;
                parts.push(TPart::Expr(e));
            }
        }
        Ok(())
    }
}

/// Whether an expression is a literal, which may be evaluated at any time.
/// An array's item type as `T[]` takes it: parenthesized if a union.
impl Translator<'_> {
    /// Whether a node reads a compiler-made local holding a failure or
    /// null: a `catch`'s `~caught`, or another local tsc can't narrow
    /// (`failures::is_guarded_elsewhere`).
    fn is_kept_failure(&self, id: NodeId) -> bool {
        let p = self.p;
        let Node::Local { local } = p.nodes.get(id) else {
            return false;
        };
        let d = p.decls.get(local);
        d.facts.contains(Facts::COMPILER_MADE)
            && nominal_decl(p, p.types.non_null(d.ty)).is_some_and(|c| self.is_failure_type(c))
            || temper_tmpl::failures::is_guarded_elsewhere(p, id)
    }
}

/// Whether a node is a handler: an `orelse`, or a `catch`, which binds
/// what it catches.
fn is_handler(p: &Program, id: NodeId) -> bool {
    matches!(p.nodes.get(id), Node::OrElse { .. } | Node::Catch { .. })
}

/// A handler's body, fallback, and a `catch`'s local.
fn handler_parts(p: &Program, id: NodeId) -> (NodeId, NodeId, Option<DeclId>) {
    match p.nodes.get(id) {
        Node::OrElse { body, fallback } => (body, fallback, None),
        Node::Catch {
            local,
            body,
            fallback,
        } => (body, fallback, Some(local)),
        _ => unreachable!("not a handler"),
    }
}

fn item_in_array(item: &str) -> String {
    if item.contains(' ') && !item.starts_with("import(") {
        format!("({item})")
    } else {
        item.to_string()
    }
}

fn is_constant(e: &Expr) -> bool {
    matches!(
        e,
        Expr::Int(_)
            | Expr::Float(_)
            | Expr::BigInt(_)
            | Expr::Str(_)
            | Expr::Bool(_)
            | Expr::Undefined
            | Expr::Null
    )
}

/// A library's npm package, as imports name it: std's is
/// `@temperlang/std`, as Kotlin's, and another's is its config's
/// `jsName`, or else its own name.
pub fn package_of(p: &Program, library: &str) -> String {
    js_name(library, p.metadata_of(library))
}

/// [`package_of`], given what the library's config says.
pub fn js_name(library: &str, metadata: &temper_tmpl::Metadata) -> String {
    match library {
        "std" => "@temperlang/std".into(),
        _ => metadata.get("jsName").unwrap_or(library).into(),
    }
}

/// The names by which a connected declaration's backend list can mean this
/// backend, as Kotlin's `connectedBackendNames`.
pub const NAMES: &[&str] = &["js"];

/// What a module imports its host code as.
const CONNECTED: &str = "_connected";

/// Where a module's host code is, relative to the library's root and in
/// its package: `_connected.js` in its directory.
pub fn host_path(module: &Module) -> String {
    match module.path.as_str() {
        "" => "_connected.js".to_string(),
        dir => format!("{dir}/_connected.js"),
    }
}
