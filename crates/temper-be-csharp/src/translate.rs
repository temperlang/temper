//! The translation from tmpl to C#'s output tree, for subset A, classes
//! and interfaces, and nullable types and `orelse`.
//!
//! A module is one static class, `NAMEGlobal`, as Kotlin's: its functions
//! are static methods, and its init block is the class's static
//! constructor, which .NET runs once, on one thread, before the class is
//! first used. Module `let`s and `var`s that functions use, or that are
//! exported, are static fields, `readonly` for a `let`, declared without
//! a value and assigned by the static constructor, as Kotlin's are; the
//! rest are locals of the static constructor. A `var` that a function
//! writes is `volatile` (where C# allows it: not a `long` or `double`),
//! so every thread reads its latest value, and a function's update of one
//! from itself alone, through operators (`ticks = ticks + 1`), takes the
//! module's lock, so updates from several threads aren't lost; Kotlin's
//! are plain statics.
//!
//! C# has no labeled loops: a `break` or `continue` of a loop that isn't
//! the innermost is a `goto` to a label after the loop or at the end of
//! its body. Arithmetic on constants alone is `unchecked`, since C#
//! rejects a constant that overflows. A self-assignment (`zero = zero`),
//! which C# warns of, is left out: it does nothing.
//!
//! Another module's functions and module `let`s are its class's statics,
//! `HelpersGlobal.Twice(n)`, with a `using` of its namespace (or named
//! from `global::` where a name would hide it); another library's the
//! same, in its namespace. A module's tests are an MSTest class of their
//! own, in its namespace, with what only they reach
//! ([`Translator::tests`]).
//!
//! Classes and interfaces are each a file of their own in the module's
//! namespace, as Kotlin's: `public` if exported, else `internal`, an
//! interface named with `I` first (`IShape`), and members in Pascal case.
//! A class's fields are private, in the source's case, and each that
//! isn't private has a property that gives it to others (`x` and `X`),
//! with a `set` for a `var`; code in the class uses the field itself,
//! and may on any instance of it. A getter and setter are a property, its
//! access the wider of theirs. The constructor's parameters take the
//! source's names, which C# callers may pass by name. A plain
//! `toString()` overrides `object.ToString`, so `$"{x}"` uses it. An
//! interface's default member keeps its body in a
//! `protected static` helper (`ADefault(IA @this)`), as Kotlin's, which
//! each class below it calls from a public member of its own where it
//! doesn't define the member, since C# doesn't give a class its
//! interfaces' members, so the class's member is the one Temper calls,
//! the nearest breadth-first ([`Translator::forwards`]), whichever
//! interface a call goes through. An interface's member with the name of
//! one it inherits hides it (`new`), which Kotlin's leaves to a warning.
//! A call of an interface's member casts its receiver to that interface
//! where it's of another type (`((IShape)r).Describe()`); `as` and a
//! narrowing are casts, which throw, as a bubble does.
//!
//! A nullable type is C#'s `T?`: `Nullable<T>` for `int`, `long`,
//! `double`, and `bool`, whose value is `x.Value`, and a nullable
//! reference otherwise, which C# narrows by itself after a null check of
//! a local, as Temper does (`x!` elsewhere). A bubble is an exception, so
//! `orelse` is a `try` and `catch`: where it's a statement, or gives a
//! `let`, an assignment, or a `return`, it's written so, each part given
//! to the same place ([`Translator::sink`]); inside an expression, it
//! runs first, into a fresh local, where nothing before it in its
//! statement has an effect, as does a block's value (`??`'s), whose
//! statements go first. `panic()` ends the process
//! (`Environment.FailFast`), so no `catch` stops it; Kotlin's throws, as
//! a bubble does, which an `orelse` catches.
//!
//! A static field is a static property (`Rect.Unit`), Kotlin's a field:
//! its value is in a `readonly` field of the module's class, which the
//! module's init sets where Temper initializes it, in source order, and
//! the property reads, which runs the module's init first, as Kotlin's
//! field initializers, run whenever .NET first uses the class, don't.

mod functions;
mod host;

pub use host::{NAMES, files as host_files};

use crate::NotYet;
use crate::connected::{self, Connection};
use crate::ops::{self, Spelling};
use crate::tree::{self, Access, Bin, Expr, Hole, Labels, Stmt, Unary};
use std::collections::{BTreeSet, HashMap, HashSet};
use temper_tmpl::analysis::{
    self, TestParts, ancestors, as_super, home, is_plain_to_string, members, module_refs,
    module_state, module_writes, nominal_decl, reads_only_itself,
};
use temper_tmpl::host::uses_host;
use temper_tmpl::naming::NameMap;
use temper_tmpl::qname::{by_connection, is_external, library_of};
use temper_tmpl::{
    DeclId, DeclKind, Facts, Module, Node, NodeId, Program, QNames, Scalar, TypeData, TypeId,
};

type R<T> = Result<T, NotYet>;

fn not_yet<T>(what: impl Into<String>) -> R<T> {
    Err(NotYet { what: what.into() })
}

/// The classes and namespaces the translator names, by simple name, with
/// the namespace that holds each.
const NAMESPACES: &[(&str, &str)] = &[
    (
        "AssertFailedException",
        "Microsoft.VisualStudio.TestTools.UnitTesting",
    ),
    ("Async", "TemperLang.Core"),
    ("Core", "TemperLang.Core"),
    ("CultureInfo", "System.Globalization"),
    ("Enumerable", "System.Linq"),
    ("Float64", "TemperLang.Core"),
    ("IDictionary", "System.Collections.Generic"),
    ("IEnumerable", "System.Collections.Generic"),
    ("IGenerator", "TemperLang.Core"),
    ("IList", "System.Collections.Generic"),
    ("ILoggingConsole", "TemperLang.Core"),
    ("IReadOnlyDictionary", "System.Collections.Generic"),
    ("IReadOnlyList", "System.Collections.Generic"),
    ("Environment", "System"),
    ("InvalidOperationException", "System"),
    ("KeyValuePair", "System.Collections.Generic"),
    ("List", "System.Collections.Generic"),
    ("Listed", "TemperLang.Core"),
    ("Mapped", "TemperLang.Core"),
    ("Optional", "TemperLang.Core"),
    ("Math", "System"),
    ("BitArray", "System.Collections"),
    ("Queue", "System.Collections.Generic"),
    ("RuntimeHelpers", "System.Runtime.CompilerServices"),
    ("StringBuilder", "System.Text"),
    ("StringUtil", "TemperLang.Core"),
    ("Task", "System.Threading.Tasks"),
    ("TaskCompletionSource", "System.Threading.Tasks"),
    // std's regex support (Kotlin's `RegexSupport.cs`).
    ("RegexSupport", "TemperLang.Std.Regex"),
    // std's net support (Kotlin's `NetSupport.cs`).
    ("NetSupport", "TemperLang.Std.Net"),
    ("INetResponse", "TemperLang.Std.Net"),
    // std's temporal support (Kotlin's `TemporalSupport.cs`).
    ("TemporalSupport", "TemperLang.Std.Temporal"),
    ("CultureInfo", "System.Globalization"),
];

pub struct Translator<'a> {
    p: &'a Program,
    names: &'a NameMap,
    /// The connected call being translated, whose type its hooks read.
    call: NodeId,
    qnames: QNames,
    /// The module's namespace and class.
    namespace: String,
    class: String,
    usings: BTreeSet<String>,
    /// The module translated, or whose test parts and tests are.
    here: DeclId,
    /// What its test parts define and use of its library's
    /// ([`analysis::test_parts`]), and the class they're in, its test
    /// class.
    test_parts: TestParts,
    test_class: String,
    /// Whether translating its test parts, in its test class or the
    /// tests' project's types.
    in_tests: bool,
    /// Classes of other modules named, by simple name, with their
    /// namespaces.
    classes: HashMap<String, String>,
    /// Every name the program's declarations take, and the root
    /// namespace, which would hide a class of the same name.
    taken: HashSet<String>,
    /// The namespaces of the modules the program has or uses, each
    /// once, which hide a class named as one of them or as a namespace
    /// above one, and whose module classes may share a name.
    module_namespaces: Vec<String>,
    /// Module `let`s and `var`s kept as static fields.
    statics: HashSet<DeclId>,
    /// The module's lock, if a function takes it.
    lock: Option<String>,
    /// Whether translating a function, not the static constructor, and its
    /// result type, if not `void`.
    in_function: bool,
    /// Whether it's translating a generator's body, an iterator.
    pub(crate) in_generator: bool,
    result: Option<String>,
    /// The enclosing loops, innermost last.
    loops: Vec<DeclId>,
    /// The module's class, `NAMEGlobal`, which the module's classes and
    /// interfaces reach its functions and statics through.
    global: String,
    /// The class or interface being translated, if any.
    in_type: Option<DeclId>,
    /// The names of the module's class's own members, its functions and
    /// `let`s, which hide types of those names inside it; made once
    /// needed.
    global_names: Option<HashSet<String>>,
    /// What `this` is: `@this`, the parameter of an interface's static
    /// helper that holds a default member's body.
    this_name: &'static str,
    /// Type parameters written as other types: a generic interface's, as
    /// a class that extends it gives them, in a member forwarded to the
    /// interface's helper ([`Self::forwards`]).
    type_args: HashMap<DeclId, TypeId>,
    /// Names that differ from the naming pass's where they're used: a
    /// setter's parameter, C#'s `value`.
    renamed: HashMap<DeclId, String>,
    /// Each static field of the module's classes and interfaces, by the
    /// name of the module class's field that holds it.
    static_storage: HashMap<DeclId, String>,
    /// Whether a class or interface takes the module's lock, which is then
    /// `internal`, not `private`.
    lock_shared: bool,
    /// The module `let`s the program reads, found when first wanted.
    read_lets: Option<std::collections::HashSet<DeclId>>,
    /// The module's classes and interfaces, translated.
    types: Vec<tree::TypeDecl>,
    /// Every class and interface of the program's own, by declaration.
    type_nodes: HashMap<DeclId, NodeId>,
    /// Labels printed, by loop: for a `continue` and a `break` from an
    /// inner loop, only where one needs them.
    labels: HashMap<DeclId, Labels>,
    /// Statements the statement being translated needs first: those of a
    /// `BlockValue` or an `OrElse` run ahead of it ([`Translator::hoists`]).
    pre: Vec<Stmt>,
    /// The `BlockValue`s and `OrElse`s of the statement being translated
    /// that may run ahead of it, as nothing before them in it has an
    /// effect or reads what may change.
    hoist: HashSet<NodeId>,
    /// The locals and module `var`s that a `BlockValue` or an `OrElse`
    /// assigns, which reading before one has an effect for.
    block_writes: HashSet<DeclId>,
    /// The helpers the module's class needs ([`crate::connected::HELPERS`]).
    helpers: BTreeSet<&'static str>,
    /// Expressions whose value C# never names a type for: a member's
    /// receiver, read at once, and a dropped value
    /// ([`Translator::nested_optionals`]).
    unnamed: HashSet<NodeId>,
}

/// Where a value given by statements goes ([`Translator::sink`]).
enum Sink {
    Assign(Expr),
    Return,
    Drop,
}

/// A part of a string concatenation, before it's written.
enum Part {
    Lit(String),
    /// A string-typed expression.
    Str(Expr),
    /// An `Int32` or `Int64` that `toString()` converts, by the invariant
    /// culture.
    Number(Expr),
    /// A `Boolean` that `toString()` converts.
    Boolean(Expr),
}

impl<'a> Translator<'a> {
    pub fn new(p: &'a Program, names: &'a NameMap, namespace: &str, class: &str) -> Translator<'a> {
        // The root namespace hides a class of its name too (a library
        // `math` is namespace `Math`).
        let mut taken: HashSet<String> = (p.decls.ids())
            .filter(|&d| p.decls.contains(d))
            .map(|d| names.get(d).trim_start_matches('@').to_string())
            .filter(|n| !n.is_empty())
            .collect();
        taken.insert(namespace.split('.').next().unwrap_or_default().to_string());
        let mut type_nodes = HashMap::new();
        for module in &p.modules {
            for item in module.all_items() {
                if let Node::Class { decl, .. } | Node::Interface { decl, .. } = p.nodes.get(item) {
                    type_nodes.insert(decl, item);
                    if p.decls.get(decl).kind == DeclKind::Interface {
                        taken.insert(format!("I{}", names.get(decl)));
                    }
                }
            }
        }
        let renamed = unclashed(p, names);
        for name in renamed.values() {
            taken.insert(name.clone());
        }
        let mut module_namespaces: Vec<String> = (p.decls.ids())
            .filter(|&d| p.decls.contains(d))
            .filter(|&d| matches!(p.decls.get(d).kind, DeclKind::Module | DeclKind::Library))
            .map(|d| module_namespace(p, d))
            .collect();
        module_namespaces.sort();
        module_namespaces.dedup();
        Translator {
            p,
            names,
            qnames: QNames::derive(p),
            namespace: namespace.to_string(),
            class: class.to_string(),
            usings: BTreeSet::new(),
            here: DeclId::NONE,
            test_parts: TestParts::default(),
            test_class: String::new(),
            in_tests: false,
            classes: HashMap::new(),
            taken,
            module_namespaces,
            statics: HashSet::new(),
            lock: None,
            in_function: false,
            in_generator: false,
            result: None,
            loops: Vec::new(),
            labels: HashMap::new(),
            global: class.to_string(),
            in_type: None,
            global_names: None,
            call: NodeId(0),
            this_name: "this",
            type_args: HashMap::new(),
            renamed,
            static_storage: HashMap::new(),
            lock_shared: false,
            read_lets: None,
            types: Vec::new(),
            type_nodes,
            pre: Vec::new(),
            hoist: HashSet::new(),
            block_writes: block_writes(p),
            helpers: BTreeSet::new(),
            unnamed: HashSet::new(),
        }
    }

    pub(crate) fn node(&self, id: NodeId) -> Node {
        self.p.nodes.get(id)
    }

    /// The module's namespace, which names its console.
    pub(crate) fn namespace(&self) -> &str {
        &self.namespace
    }

    /// A class or namespace the translator names, by its simple name, with
    /// a `using` for its namespace, or qualified from `global::` where a
    /// name of the program's would hide it.
    pub(crate) fn class(&mut self, name: &str) -> Expr {
        let namespace = NAMESPACES
            .iter()
            .find(|(n, _)| *n == name)
            .map_or("System", |(_, ns)| ns);
        // `Async` always in full: a library or module named `async` is
        // likely, and its namespace, which other libraries' code sees too,
        // hides the class.
        if self.taken.contains(name) || self.namespace_hides(name) || name == "Async" {
            Expr::name(format!("global::{namespace}.{name}"))
        } else {
            self.usings.insert(namespace.to_string());
            Expr::name(name)
        }
    }

    /// Whether a namespace in or beside one this code is in hides a
    /// class's simple name, since C# looks a name up in each enclosing
    /// namespace before `using`s: in std's `TemperLang.Std.Testing`,
    /// `Core` is the namespace `TemperLang.Core`, not its class `Core`; in
    /// a library's `Lib.Url`, its module `core`'s `Lib.Core`; in
    /// `Lib.Nfa`, module `nfa/engine`'s `Lib.Nfa.Engine`; and anywhere in
    /// `Lib`, `Lib.Dfa`, which holds module `dfa/dfa`'s `Lib.Dfa.Dfa`.
    fn namespace_hides(&self, name: &str) -> bool {
        let mut outer = Some(self.namespace.as_str());
        while let Some(ns) = outer {
            let within = format!("{ns}.{name}");
            let under = |n: &str| {
                n.strip_prefix(within.as_str())
                    .is_some_and(|r| r.is_empty() || r.starts_with('.'))
            };
            if NAMESPACES.iter().any(|(_, n)| under(n))
                || self.module_namespaces.iter().any(|n| under(n))
            {
                return true;
            }
            outer = ns.rsplit_once('.').map(|(parent, _)| parent);
        }
        false
    }

    /// `new C(args)`, of a class [`Translator::class`] names.
    pub(crate) fn construct(&mut self, name: &str, args: Vec<Expr>) -> Expr {
        match self.class(name) {
            Expr::Name(n) => Expr::name(format!("new {n}")).call(args),
            other => other.call(args),
        }
    }

    /// A class of the library's `Support` namespace, from its
    /// `Logging.cs`: `Support.Logging`, relative to the root namespace,
    /// since a `using` of it would make its `ILoggingConsole` and
    /// `TemperLang.Core`'s ambiguous.
    pub(crate) fn support(&mut self, name: &str) -> Expr {
        let root = self.namespace.split('.').next().unwrap_or_default();
        if self.taken.contains("Support") {
            Expr::name(format!("global::{root}.Support.{name}"))
        } else {
            Expr::name("Support").member(name)
        }
    }

    /// Another module's class, by its namespace and name: by its simple
    /// name, with a `using` of its namespace, unless a name of the
    /// program's, the class's own, or another such class's would hide it,
    /// when it's named from `global::`.
    fn other_class(&mut self, namespace: &str, name: &str) -> String {
        self.other_class_of(namespace, name, None)
    }

    /// [`Self::other_class`], for a class of another library's that's a
    /// declaration of the program's, `decl`, whose own name doesn't hide
    /// it.
    fn other_class_of(&mut self, namespace: &str, name: &str, decl: Option<DeclId>) -> String {
        let p = self.p;
        let hidden = match decl {
            Some(decl) => (p.decls.ids())
                .any(|d| d != decl && p.decls.contains(d) && self.names.get(d) == name),
            // Another module's class of this name, as `regex`'s
            // `RegexGlobal` and std's, whose `using`s would make the name
            // ambiguous; but a namespace this code is in is searched
            // before `using`s.
            None => {
                let encloses = (self.namespace.strip_prefix(namespace))
                    .is_some_and(|r| r.is_empty() || r.starts_with('.'));
                let shares = |n: &String| {
                    let base = n.rsplit('.').next().unwrap_or_default();
                    n != namespace && name.strip_suffix("Global") == Some(base)
                };
                self.taken.contains(name)
                    || (!encloses && self.module_namespaces.iter().any(shares))
            }
        };
        // A namespace this code is in hides it too: a library `match`'s
        // namespace `Match`, std's regex class `Match`; and so does one
        // in or beside it: module `state`'s `Lib.State`, in `Lib.User`,
        // its class `State`.
        let clash = hidden
            || name == self.class
            || self.namespace.split('.').any(|part| part == name)
            || self.namespace_hides(name)
            || self.classes.get(name).is_some_and(|ns| ns != namespace);
        if clash {
            return format!("global::{namespace}.{name}");
        }
        self.classes.insert(name.to_string(), namespace.to_string());
        if namespace != self.namespace {
            self.usings.insert(namespace.to_string());
        }
        name.to_string()
    }

    /// A module-level declaration, as the code reaches it: through the
    /// class of statics it's in, its module's, or for what only tests
    /// reach, its test class ([`Self::holds_tests`]); but by its name
    /// alone in that class.
    fn owner_class(&mut self, decl: DeclId) -> Option<String> {
        let from = home(self.p, decl)?;
        if from != self.here {
            let (namespace, class) = global_class(self.p, from);
            return Some(self.other_class(&namespace, &class));
        }
        let tests = self.holds_tests(decl);
        if self.in_type.is_none() && tests == self.in_tests {
            return None;
        }
        Some(self.part_class(tests))
    }

    /// Whether this module's declaration, or a static field's storage, is
    /// in its test class: what only tests reach.
    fn holds_tests(&self, decl: DeclId) -> bool {
        let p = self.p;
        let top = match p.decls.get(decl).kind {
            DeclKind::StaticField => p.decls.get(decl).owner,
            _ => decl,
        };
        self.test_parts.own.contains(&top)
    }

    /// The module's class, or with `tests`, its test class, as the code
    /// names it.
    fn part_class(&mut self, tests: bool) -> String {
        let namespace = self.namespace.clone();
        let class = match tests {
            true => self.test_class.clone(),
            false => self.global.clone(),
        };
        self.other_class(&namespace, &class)
    }

    /// A module `let`'s name where it's read or assigned, through its
    /// module's class if another's.
    fn top_name(&mut self, decl: DeclId) -> Expr {
        let name = self.name(decl);
        if self.p.decls.get(decl).kind != DeclKind::ModuleLet {
            return Expr::name(name);
        }
        match self.owner_class(decl) {
            Some(class) => Expr::name(class).member(name),
            None => Expr::name(name),
        }
    }

    /// A number's text in the invariant culture.
    pub(crate) fn invariant(&mut self, n: Expr) -> Expr {
        let culture = self.class("CultureInfo").member("InvariantCulture");
        n.method("ToString", vec![culture])
    }

    fn name(&self, decl: DeclId) -> String {
        match self.renamed.get(&decl) {
            Some(name) => name.clone(),
            None => self.names.get(decl).to_string(),
        }
    }

    /// A class's or interface's simple name: an interface's with `I`
    /// before it, as Kotlin's (`IShape`).
    fn simple_type_name(&self, decl: DeclId) -> String {
        let name = self.name(decl);
        if self.p.decls.get(decl).kind == DeclKind::Interface {
            format!("I{name}")
        } else {
            name
        }
    }

    /// A class or interface where the code names it: by its simple name
    /// in its own namespace, else as [`Self::other_class_of`] gives it.
    fn type_name(&mut self, decl: DeclId) -> String {
        let name = self.simple_type_name(decl);
        let from = home(self.p, decl).unwrap_or(DeclId::NONE);
        let namespace = module_namespace(self.p, from);
        if namespace == self.namespace {
            // In the module's class, a member of the type's name hides it,
            // as a function `casing` (`Casing`) hides `class Casing`.
            if self.in_type.is_none() && self.global_name_taken(&name) {
                return format!("global::{namespace}.{name}");
            }
            name
        } else {
            self.other_class_of(&namespace, &name, Some(decl))
        }
    }

    /// Whether the module's class, or its test class, has a member of this
    /// name.
    fn global_name_taken(&mut self, name: &str) -> bool {
        if self.global_names.is_none() {
            let p = self.p;
            let names = (p.modules.iter())
                .flat_map(|m| m.all_items())
                .filter_map(|item| match p.nodes.get(item) {
                    Node::Function { decl, .. } | Node::ModuleLet { decl } => Some(decl),
                    _ => None,
                })
                .map(|d| self.name(d))
                .collect();
            self.global_names = Some(names);
        }
        self.global_names.as_ref().is_some_and(|n| n.contains(name))
    }

    /// A member's name, with `_` after it if it would be its type's, which
    /// C# forbids, as Kotlin's (`Bar_`).
    fn member_name(&self, decl: DeclId) -> String {
        self.safe_member(decl, self.name(decl))
    }

    fn safe_member(&self, decl: DeclId, name: String) -> String {
        let owner = self.p.decls.get(decl).owner;
        if name == self.simple_type_name(owner) {
            format!("{name}_")
        } else {
            name
        }
    }

    /// The property that gives others a field: its name in Pascal case,
    /// as Kotlin's (`x` and `X`).
    fn field_property(&self, field: DeclId) -> String {
        let name = temper_tmpl::naming::pascal_case(self.p.name(field));
        self.safe_member(field, name)
    }

    /// A helper of the class of statics translated (the module's, or its
    /// test class), by name, which it then has: from that class itself,
    /// or through it.
    pub(crate) fn helper(&mut self, name: &'static str) -> Expr {
        self.helpers.insert(name);
        if self.in_type.is_some() {
            Expr::name(self.part_class(self.in_tests)).member(name)
        } else {
            Expr::name(name)
        }
    }

    /// The helpers the translation used, as methods of the module's class.
    pub fn helper_methods(&mut self) -> Vec<tree::Method> {
        let used = std::mem::take(&mut self.helpers);
        let mut methods = Vec::new();
        for name in used {
            let Some(h) = connected::HELPERS.iter().find(|h| h.name == name) else {
                continue;
            };
            methods.push(tree::Method {
                public: false,
                result: h.result.to_string(),
                name: name.to_string(),
                params: (h.params.iter())
                    .map(|(t, n)| (t.to_string(), n.to_string()))
                    .collect(),
                constraints: Vec::new(),
                body: vec![Stmt::Raw(h.body.to_string())],
            });
        }
        methods
    }

    fn qname(&self, decl: DeclId) -> String {
        self.qnames
            .get(decl)
            .map(|q| q.to_string())
            .unwrap_or_default()
    }

    /// A type as C# writes it, its namespace imported if it needs to be.
    fn ty(&mut self, ty: TypeId) -> R<String> {
        Ok(match self.p.types.get(ty) {
            // `temper_tmpl::nevers` made each `Never<T>` the `T` it stands
            // for, as this profile has no bottom type.
            TypeData::Never(t) => return self.ty(t),
            TypeData::Scalar(s) => match s {
                Scalar::Void => "void",
                Scalar::Boolean => "bool",
                Scalar::Int32 => "int",
                Scalar::Int64 => "long",
                Scalar::Float64 => "double",
                Scalar::String => "string",
                Scalar::AnyValue => "object",
                Scalar::Type => return Ok(self.other_class("System", "Type")),
            }
            .to_string(),
            // A `@fun` interface is its function type's delegate.
            TypeData::Nominal { decl, .. } if self.p.decls.get(decl).facts.contains(Facts::FUN) => {
                self.delegate(ty)?
            }
            TypeData::Nominal { decl, args } => {
                let qname = self.qname(decl);
                let mut texts = Vec::new();
                for &a in self.p.types.list(args) {
                    texts.push(self.ty(a)?);
                }
                let name = match connected::lookup_type(&qname) {
                    Some(c) if c.namespace.is_empty() => c.name.to_string(),
                    Some(c) => match self.class(c.name) {
                        Expr::Name(n) => n,
                        _ => c.name.to_string(),
                    },
                    None if !is_external(self.p, decl) || library_of(self.p, decl) != "core" => {
                        self.type_name(decl)
                    }
                    None => return not_yet(format!("a type for {qname}")),
                };
                if texts.is_empty() {
                    name
                } else {
                    format!("{name}<{}>", texts.join(", "))
                }
            }
            TypeData::Function { .. } => self.delegate(ty)?,
            TypeData::Nullable(inner) if self.is_optional(ty) => {
                // `T?` of an unconstrained `T` isn't nullable where `T` is
                // a value type, so it's the runtime's `Optional<T>`, as
                // Kotlin's. A class-bounded `T`'s is plain `T?`
                // ([`Self::class_bounded`]).
                let inner = self.ty(inner)?;
                let optional = match self.class("Optional") {
                    Expr::Name(n) => n,
                    _ => "Optional".to_string(),
                };
                format!("{optional}<{inner}>")
            }
            TypeData::Nullable(inner) => format!("{}?", self.ty(inner)?),
            TypeData::TypeParam { decl } => match self.type_args.remove(&decl) {
                // Not again, as `T` given as `List<T>`.
                Some(arg) => {
                    let text = self.ty(arg);
                    self.type_args.insert(decl, arg);
                    text?
                }
                None => self.p.name(decl).to_string(),
            },
        })
    }

    /// A class's or interface's own type, as its body names it:
    /// `IHolder<T>` for `interface Holder<T>`.
    fn own_type(&mut self, decl: DeclId) -> R<String> {
        let name = self.simple_type_name(decl);
        let params = self.p.decls.type_params(decl).to_vec();
        if params.is_empty() {
            return Ok(name);
        }
        let names: Vec<String> = params.iter().map(|&t| self.p.name(t).to_string()).collect();
        Ok(format!("{name}<{}>", names.join(", ")))
    }

    /// Interface `owner` as a value of type `of` sees it, with the type
    /// arguments `of` gives it: `IHolder<int>`, for a class that extends
    /// `Holder<Int>`; bare for one that isn't generic.
    fn owner_type(&mut self, owner: DeclId, of: TypeId) -> R<String> {
        let name = self.type_name(owner);
        if self.p.decls.type_params(owner).is_empty() {
            return Ok(name);
        }
        let Some(args) = as_super(self.p, of, owner) else {
            return not_yet("a generic interface's member on another type");
        };
        let mut texts = Vec::new();
        for a in args {
            texts.push(self.ty(a)?);
        }
        Ok(format!("{name}<{}>", texts.join(", ")))
    }

    /// The type arguments a class gives interface `owner`'s type
    /// parameters, to write `owner`'s members as the class has them.
    fn args_from(&self, class: DeclId, owner: DeclId) -> HashMap<DeclId, TypeId> {
        let p = self.p;
        let args = as_super(p, p.decls.get(class).ty, owner).unwrap_or_default();
        (p.decls.type_params(owner).iter().copied())
            .zip(args)
            .collect()
    }

    /// A generic declaration's type parameters, `<T>`, and their
    /// constraints, `where T : Named` (but `MapKey`, which C#'s maps need
    /// nothing for), with `class` first for one bounded by an interface
    /// only C# classes implement, `where T : class, IShown`, so that its
    /// `T?` is a nullable reference ([`Self::class_bounded`]).
    fn type_params(&mut self, decl: DeclId) -> R<(String, Vec<String>)> {
        let p = self.p;
        let params = p.decls.type_params(decl).to_vec();
        if params.is_empty() {
            return Ok((String::new(), Vec::new()));
        }
        let mut names = Vec::new();
        let mut constraints = Vec::new();
        for t in params {
            let name = p.name(t).to_string();
            let mut bounds = Vec::new();
            let supers = p.decls.supers(t);
            if self.class_bounded(t)
                && !(supers.iter()).any(|&b| self.reference_bound(b) == Some(DeclKind::Class))
            {
                bounds.push("class".to_string());
            }
            for &b in supers {
                let key = nominal_decl(p, b).is_some_and(|d| self.qname(d) == "core.type MapKey");
                if !key {
                    bounds.push(self.ty(b)?);
                }
            }
            if !bounds.is_empty() {
                constraints.push(format!("where {name} : {}", bounds.join(", ")));
            }
            names.push(name);
        }
        Ok((format!("<{}>", names.join(", ")), constraints))
    }

    /// A method's or function's name with its type parameters,
    /// `First<T>`, and their constraints, `where T : Named`.
    fn generic_name(&mut self, decl: DeclId, name: String) -> R<(String, Vec<String>)> {
        let (generics, constraints) = self.type_params(decl)?;
        Ok((format!("{name}{generics}"), constraints))
    }

    /// The qname of a type's class or interface, if it has one.
    fn type_qname(&self, ty: TypeId) -> String {
        match self.p.types.get(ty) {
            TypeData::Nominal { decl, .. } => self.qname(decl),
            _ => String::new(),
        }
    }

    /// The program being translated.
    pub(crate) fn program(&self) -> &'a Program {
        self.p
    }

    /// The type of the call being translated.
    pub(crate) fn call_type(&mut self) -> R<String> {
        self.ty(self.p.nodes.ty(self.call))
    }

    /// The item type of the list the call being translated makes.
    pub(crate) fn call_item_type(&mut self) -> R<String> {
        let ty = self.p.nodes.ty(self.call);
        match self.p.types.args(ty).first() {
            Some(&t) => self.ty(t),
            None => not_yet("a list without an item type"),
        }
    }

    fn exported(&self, decl: DeclId) -> bool {
        self.p.decls.get(decl).facts.contains(Facts::EXPORTED)
    }

    /// Translates a module's library parts into its class, `NAMEGlobal`,
    /// and its classes and interfaces ([`Self::take_types`]). What only
    /// tests reach is its test class's ([`Translator::tests`]), as tmpl's
    /// `reach::partition` splits the module.
    pub fn module(&mut self, module: &Module) -> R<tree::Class> {
        let p = self.p;
        self.here = module.decl;
        self.test_parts = analysis::test_parts(p, module);
        self.test_class = format!("{}Tests", module_names(p, module.decl).1);
        self.in_tests = false;
        // What the test parts use is a static too, for their class.
        let outside: HashSet<DeclId> = self.test_parts.used.iter().copied().collect();
        self.statics_class(module, &module.items, Some(module.init), &outside)
    }

    /// A class of statics, of a module's library parts or its test parts
    /// (`items`, and `init`, its static constructor's), with their
    /// classes and interfaces, each a file ([`Self::take_types`]): its
    /// functions, as static methods, its module values that functions,
    /// classes, other classes of statics (those `outside` lists), or
    /// exports use, as static fields, the rest its constructor's locals,
    /// and its classes' and interfaces' static fields' storage.
    fn statics_class(
        &mut self,
        module: &Module,
        items: &[NodeId],
        init: Option<NodeId>,
        outside: &HashSet<DeclId>,
    ) -> R<tree::Class> {
        let p = self.p;
        let mut state = module_state(p, module);
        let mut written = HashSet::new();
        // What the module's classes and interfaces use is a static too.
        for item in module.roots() {
            match p.nodes.get(item) {
                Node::Function { .. } | Node::Test { .. } => written.extend(module_writes(p, item)),
                Node::Class { .. } | Node::Interface { .. } => {
                    written.extend(module_writes(p, item));
                    for d in module_refs(p, item) {
                        if p.decls.get(d).kind == DeclKind::ModuleLet {
                            state.used_by_functions.insert(d);
                        }
                    }
                }
                _ => {}
            }
        }
        let stmts = match init.map(|i| (i, p.nodes.get(i))) {
            Some((_, Node::Block { stmts })) => p.nodes.list(stmts).to_vec(),
            Some((i, _)) => vec![i],
            None => Vec::new(),
        };
        let mut fields = Vec::new();
        let mut init_locals = Vec::new();
        // Static fields initialized directly in the init block.
        let mut statics_at_top = HashSet::new();
        for &s in &stmts {
            if let Node::InitStatic { target, .. } = p.nodes.get(s) {
                statics_at_top.insert(target);
            }
        }
        for &item in items {
            if let Node::Class {
                decl: class,
                members,
            }
            | Node::Interface {
                decl: class,
                members,
            } = p.nodes.get(item)
            {
                for &member in p.nodes.list(members) {
                    if let Node::StaticField { decl } = p.nodes.get(member) {
                        if host::is_host_class(p, class) {
                            return not_yet(format!(
                                "a static field of host code's {}",
                                p.name(class)
                            ));
                        }
                        let name = self.storage_name(decl);
                        self.static_storage.insert(decl, name.clone());
                        fields.push(tree::Field {
                            access: Access::Internal,
                            readonly: statics_at_top.contains(&decl),
                            volatile: false,
                            ty: self.ty(p.decls.get(decl).ty)?,
                            name,
                            value: None,
                        });
                    }
                }
            }
            if let Node::ModuleLet { decl } = p.nodes.get(item) {
                let d = p.decls.get(decl);
                let var = d.facts.contains(Facts::VAR);
                if self.exported(decl)
                    || state.used_by_functions.contains(&decl)
                    || outside.contains(&decl)
                {
                    self.statics.insert(decl);
                    let ty = self.ty(d.ty)?;
                    // C# has no `volatile` `long` or `double`.
                    let volatile = var && written.contains(&decl) && ty != "long" && ty != "double";
                    fields.push(tree::Field {
                        access: if self.exported(decl) {
                            Access::Public
                        } else {
                            Access::Internal
                        },
                        readonly: !var && state.initialized_at_top.contains(&decl),
                        volatile,
                        ty,
                        name: self.name(decl),
                        value: None,
                    });
                } else if !state.initialized_at_top.contains(&decl) {
                    // Assigned only inside the constructor's blocks, so
                    // declared first.
                    init_locals.push(Stmt::Local {
                        ty: self.ty(d.ty)?,
                        name: self.name(decl),
                        value: None,
                    });
                }
            }
        }
        if fields.iter().any(|f| f.name == self.class) {
            return not_yet(format!("a member named for its class, {}", self.class));
        }
        let mut methods = Vec::new();
        for &item in items {
            match p.nodes.get(item) {
                Node::Function { decl, .. } | Node::BodilessFunction { decl, .. }
                    if self.name(decl) == self.class =>
                {
                    return not_yet(format!("a member named for its class, {}", self.class));
                }
                Node::Function { decl, .. } | Node::BodilessFunction { decl, .. }
                    if self.in_tests && uses_host(p, decl, NAMES) =>
                {
                    return not_yet(format!(
                        "host code for what only tests reach, {}",
                        p.name(decl)
                    ));
                }
                Node::Function { decl, params, body } if uses_host(p, decl, NAMES) => {
                    methods.push(self.host_wrapper(module, item, decl, params, Some(body))?);
                }
                Node::Function { .. } => methods.push(self.function(item)?),
                // std's, which the connection table has (`StdNetSend`).
                Node::BodilessFunction { decl, .. }
                    if temper_tmpl::qname::by_connection(
                        p,
                        decl,
                        crate::connected::connects_std,
                    ) => {}
                // Always host code, as it has no Temper body.
                Node::BodilessFunction { decl, params } => {
                    methods.push(self.host_wrapper(module, item, decl, params, None)?);
                }
                _ => {}
            }
        }
        for &item in items {
            if let Node::Class { decl, .. } | Node::Interface { decl, .. } = p.nodes.get(item) {
                // A `@fun` interface is only its function type's delegate.
                if p.decls.get(decl).facts.contains(Facts::FUN) {
                    continue;
                }
                // A std class connected to a .NET type (`Date`, as
                // `DateTime`) isn't declared, as Kotlin's.
                if temper_tmpl::qname::connected_type(p, decl, crate::connected::connects_std) {
                    continue;
                }
                // A connected class that host code defines, with the
                // names and members Temper would give it, as Kotlin's.
                if host::is_host_class(p, decl) {
                    if self.in_tests {
                        return not_yet(format!(
                            "host code for what only tests reach, {}",
                            p.name(decl)
                        ));
                    }
                    let class = self.simple_type_name(decl);
                    self.need_host(module, decl, &class)?;
                    continue;
                }
                let t = self.type_decl(item)?;
                self.types.push(t);
            }
        }
        self.in_function = false;
        self.result = None;
        let mut body = init_locals;
        // A test class's constructor continues its module class's, so runs
        // it first.
        if self.in_tests && init.is_some() {
            let global = self.part_class(false);
            let handle = Expr::name(format!("typeof({global})")).member("TypeHandle");
            let run = (self.class("RuntimeHelpers")).method("RunClassConstructor", vec![handle]);
            body.push(Stmt::Expr(run));
        }
        if let Some(init) = init {
            self.begin(init);
        }
        for &s in &stmts {
            match p.nodes.get(s) {
                // A module `let` that's never read, holding a
                // literal, as the functional tests' unused
                // examples: C# rejects it as a local (CS0219),
                // and Kotlin's leaves it out too.
                Node::InitLet { target, value }
                    if !self.statics.contains(&target)
                        && is_literal(p, value)
                        && !self.is_read(target) => {}
                Node::InitLet { target, value } if !self.statics.contains(&target) => {
                    let ty = self.ty(p.decls.get(target).ty)?;
                    let name = self.name(target);
                    if self.needs_statements(value) {
                        body.push(Stmt::Local {
                            ty,
                            name: name.clone(),
                            value: None,
                        });
                        self.sink(value, &Sink::Assign(Expr::name(name)), &mut body)?;
                        continue;
                    }
                    let hoist = self.hoists(s);
                    let saved = std::mem::replace(&mut self.hoist, hoist);
                    let value = self.expr(value);
                    self.hoist = saved;
                    body.append(&mut self.pre);
                    body.push(Stmt::Local {
                        ty,
                        name,
                        value: Some(value?),
                    });
                }
                _ => self.stmt(s, &mut body)?,
            }
        }
        // A test class's lock and helpers wait for its tests, which may
        // use them too ([`Self::tests`]).
        if self.in_tests {
            return Ok(tree::Class {
                usings: Vec::new(),
                namespace: self.namespace.clone(),
                name: self.class.clone(),
                fields,
                methods,
                init: reachable(body),
            });
        }
        if let Some(lock) = self.lock.take() {
            fields.push(tree::Field {
                access: if self.lock_shared {
                    Access::Internal
                } else {
                    Access::Private
                },
                readonly: true,
                volatile: false,
                ty: "object".into(),
                name: lock,
                value: Some(Expr::name("new").call(Vec::new())),
            });
        }
        self.lock_shared = false;
        methods.extend(self.helper_methods());
        Ok(tree::Class {
            usings: self.usings.iter().cloned().collect(),
            namespace: self.namespace.clone(),
            name: self.class.clone(),
            fields,
            methods,
            init: reachable(body),
        })
    }

    fn function(&mut self, item: NodeId) -> R<tree::Method> {
        let p = self.p;
        let Node::Function { decl, params, body } = p.nodes.get(item) else {
            return not_yet("a function that isn't one");
        };
        let (result, params, body) = self.callable(item, decl, params, Some(body))?;
        // A module function's constraints, `where T : I`.
        let (generics, constraints) = self.type_params(decl)?;
        let name = format!("{}{generics}", self.name(decl));
        Ok(tree::Method {
            public: self.exported(decl),
            result,
            name,
            params,
            constraints,
            body: body.unwrap_or_default(),
        })
    }

    /// A function's, method's, getter's, setter's, or constructor's
    /// result type (`void` for a constructor's), parameters, and body, if
    /// it has one.
    #[allow(clippy::type_complexity)]
    fn callable(
        &mut self,
        item: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: Option<NodeId>,
    ) -> R<(String, Vec<(String, String)>, Option<Vec<Stmt>>)> {
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
                let mut name = self.name(decl);
                // An optional one defaults to null, which its body
                // replaces by its default, as Kotlin's; only those after
                // the last required one, as C# needs.
                if i + trailing >= list.len() {
                    name.push_str(" = null");
                }
                ps.push((self.ty(p.decls.get(decl).ty)?, name));
            }
        }
        let result = match p.signature(decl) {
            Some(_) if p.decls.get(decl).kind == DeclKind::Constructor => TypeId::VOID,
            Some(sig) => sig.result,
            None => TypeId::VOID,
        };
        let result_ty = self.ty(result)?;
        let Some(body) = body else {
            return Ok((result_ty, ps, None));
        };
        self.in_function = true;
        self.result = (result != TypeId::VOID).then(|| result_ty.clone());
        self.begin(item);
        let mut stmts = reachable(self.stmts(body)?);
        // A `return` without a value that ends a function says nothing.
        if result == TypeId::VOID && stmts.last() == Some(&Stmt::Return(None)) {
            stmts.pop();
        }
        Ok((result_ty, ps, Some(stmts)))
    }

    /// An interface's default member's translation, its body for its
    /// static helper, where `this` is the helper's `@this`.
    #[allow(clippy::type_complexity)]
    fn helper_body(
        &mut self,
        item: NodeId,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: NodeId,
    ) -> R<(String, Vec<(String, String)>, Option<Vec<Stmt>>)> {
        let this = std::mem::replace(&mut self.this_name, "@this");
        let translated = self.callable(item, decl, params, Some(body));
        self.this_name = this;
        translated
    }

    /// The module class's field that holds a static field of one of its
    /// classes or interfaces: the type's name and the field's, in camel
    /// case (`rectUnit`), numbered if a name of the program's has it.
    fn storage_name(&mut self, decl: DeclId) -> String {
        let owner = self.p.decls.get(decl).owner;
        let ty = self.simple_type_name(owner);
        let mut chars = ty.chars();
        let first: String = chars
            .next()
            .into_iter()
            .flat_map(char::to_lowercase)
            .collect();
        let base = format!("{first}{}{}", chars.as_str(), self.member_name(decl));
        let name = (1..)
            .map(|n| match n {
                1 => base.clone(),
                n => format!("{base}{n}"),
            })
            .find(|n| !self.taken.contains(n))
            .unwrap_or_default();
        self.taken.insert(name.clone());
        name
    }

    /// The module's classes and interfaces, translated by
    /// [`Self::module`].
    pub fn take_types(&mut self) -> Vec<tree::TypeDecl> {
        std::mem::take(&mut self.types)
    }

    /// A class or interface, as its own file in the module's namespace
    /// ([`Self::type_members`]).
    fn type_decl(&mut self, item: NodeId) -> R<tree::TypeDecl> {
        let p = self.p;
        let (Node::Class { decl, members } | Node::Interface { decl, members }) = p.nodes.get(item)
        else {
            return not_yet("a type that isn't a class or interface");
        };
        let interface = p.decls.get(decl).kind == DeclKind::Interface;
        let name = self.simple_type_name(decl);
        let usings = std::mem::take(&mut self.usings);
        let classes = std::mem::take(&mut self.classes);
        let class = std::mem::replace(&mut self.class, name.clone());
        self.in_type = Some(decl);
        let translated = self.type_members(decl, p.nodes.list(members), interface);
        let bases = (p.decls.supers(decl).iter())
            .map(|&s| self.ty(s))
            .collect::<R<Vec<_>>>();
        let generics = self.type_params(decl);
        self.in_type = None;
        let type_usings = std::mem::replace(&mut self.usings, usings);
        self.classes = classes;
        self.class = class;
        Ok(tree::TypeDecl {
            usings: type_usings.into_iter().collect(),
            namespace: self.namespace.clone(),
            access: if self.exported(decl) {
                Access::Public
            } else {
                Access::Internal
            },
            interface,
            name,
            generics: generics.as_ref().map(|g| g.0.clone()).unwrap_or_default(),
            constraints: generics?.1,
            bases: bases?,
            members: translated?,
        })
    }

    /// Who may use a member: `public` but where it's private or protected;
    /// none written for an interface's public member, which C# makes
    /// public.
    fn member_access(&self, decl: DeclId, interface: bool) -> Option<Access> {
        let facts = self.p.decls.get(decl).facts;
        if facts.contains(Facts::PRIVATE) {
            Some(Access::Private)
        } else if facts.contains(Facts::PROTECTED) {
            Some(Access::Protected)
        } else if interface {
            None
        } else {
            Some(Access::Public)
        }
    }

    /// Whether an interface's member has the name of a member of an
    /// interface it extends, which it then hides (`new`): each is its own
    /// member in C#, and a class gives both the one implementation that
    /// Temper finds for it ([`Self::forwards`]).
    fn hides(&self, interface: DeclId, member: DeclId) -> bool {
        let p = self.p;
        let property = |k: DeclKind| matches!(k, DeclKind::Getter | DeclKind::Setter);
        let kind = p.decls.get(member).kind;
        ancestors(p, interface).into_iter().any(|up| {
            self.type_nodes.get(&up).is_some_and(|&node| {
                members(p, node).into_iter().any(|m| {
                    let k = p.decls.get(m).kind;
                    p.name(m) == p.name(member) && (k == kind || (property(k) && property(kind)))
                })
            })
        })
    }

    /// A class's or interface's members: its fields, private, then its
    /// constructor, then the properties that give others its fields that
    /// aren't private (`X` for `x`, as Kotlin's), then the rest in order,
    /// a getter and setter together as a property; then, for a class, what
    /// [`Self::forwards`] adds.
    fn type_members(
        &mut self,
        decl: DeclId,
        members: &[NodeId],
        interface: bool,
    ) -> R<Vec<tree::Member>> {
        use tree::{Accessor, Member};
        let p = self.p;
        let mut out = Vec::new();
        let mut properties = Vec::new();
        for &m in members {
            let Node::Field { decl: f } = p.nodes.get(m) else {
                continue;
            };
            let d = p.decls.get(f);
            let var = d.facts.contains(Facts::VAR);
            let ty = self.ty(d.ty)?;
            out.push(Member::Field {
                readonly: !var,
                ty: ty.clone(),
                name: self.name(f),
            });
            if d.facts.contains(Facts::PRIVATE) {
                continue;
            }
            let this_field = Expr::name("this").member(self.name(f));
            properties.push(Member::Property {
                access: self.member_access(f, false),
                modifiers: "",
                ty,
                name: self.field_property(f),
                get: Some(Accessor {
                    access: None,
                    body: Some(vec![Stmt::Return(Some(this_field.clone()))]),
                }),
                set: var.then(|| Accessor {
                    access: None,
                    body: Some(vec![Stmt::Assign {
                        target: this_field,
                        value: Expr::name("value"),
                    }]),
                }),
            });
        }
        let mut properties = Some(properties);
        let mut paired = HashSet::new();
        for &m in members {
            // A std member this backend connects (`Test.bail()`,
            // `Regex.compiledFind()`) isn't declared, as Kotlin's.
            if temper_tmpl::qname::connected_member_node(p, m, crate::connected::connects_std) {
                continue;
            }
            match p.nodes.get(m) {
                Node::StaticField { decl: f } => {
                    let Some(storage) = self.static_storage.get(&f).cloned() else {
                        return not_yet("a static field outside its module");
                    };
                    let value = Expr::name(self.part_class(self.in_tests)).member(storage);
                    out.push(Member::Property {
                        access: self.member_access(f, false),
                        modifiers: "static ",
                        ty: self.ty(p.decls.get(f).ty)?,
                        name: self.member_name(f),
                        get: Some(Accessor {
                            access: None,
                            body: Some(vec![Stmt::Return(Some(value))]),
                        }),
                        set: None,
                    });
                }
                Node::Method {
                    decl: ctor,
                    params,
                    body,
                } if p.decls.get(ctor).kind == DeclKind::Constructor => {
                    let (_, params, body) = self.callable(m, ctor, params, Some(body))?;
                    out.push(Member::Constructor {
                        name: self.simple_type_name(decl),
                        params,
                        body: body.unwrap_or_default(),
                    });
                    out.extend(properties.take().unwrap_or_default());
                }
                Node::Method { decl: md, .. } | Node::AbstractMethod { decl: md, .. }
                    if matches!(p.decls.get(md).kind, DeclKind::Getter | DeclKind::Setter) =>
                {
                    let name = self.member_name(md);
                    if paired.insert(name.clone()) {
                        let (property, helpers) = self.property(decl, members, &name, interface)?;
                        out.push(property);
                        out.extend(helpers);
                    }
                }
                Node::Method {
                    decl: md,
                    params,
                    body,
                } if interface && !p.decls.get(md).facts.contains(Facts::PRIVATE) => {
                    // The body in a static helper, which a class calls
                    // where it's the one Temper calls ([`Self::forwards`]).
                    let (result, params, body) = self.helper_body(m, md, params, body)?;
                    let helper = self.default_helper(md);
                    let mut args = vec![Expr::name("this")];
                    args.extend(params.iter().map(|(_, n)| Expr::name(n.clone())));
                    let call = Expr::name(helper.clone()).call(args);
                    out.push(Member::Method {
                        access: self.member_access(md, interface),
                        modifiers: if self.hides(decl, md) { "new " } else { "" },
                        result: result.clone(),
                        name: self.member_name(md),
                        params: params.clone(),
                        constraints: Vec::new(),
                        body: Some(vec![returning(&result, call)]),
                    });
                    let mut helper_params = vec![(self.own_type(decl)?, "@this".to_string())];
                    helper_params.extend(params);
                    out.push(Member::Method {
                        access: Some(Access::Protected),
                        modifiers: "static ",
                        result,
                        name: helper,
                        params: helper_params,
                        constraints: Vec::new(),
                        body,
                    });
                }
                Node::Method {
                    decl: md,
                    params,
                    body,
                } => {
                    let (result, params, body) = self.callable(m, md, params, Some(body))?;
                    let (name, modifiers) = if is_plain_to_string(p, md) {
                        ("ToString".to_string(), "override ")
                    } else if interface && self.hides(decl, md) {
                        (self.member_name(md), "new ")
                    } else {
                        (self.member_name(md), "")
                    };
                    let (name, constraints) = self.generic_name(md, name)?;
                    out.push(Member::Method {
                        access: self.member_access(md, interface),
                        modifiers,
                        result,
                        name,
                        params,
                        constraints,
                        body,
                    });
                }
                Node::AbstractMethod { decl: md, params } => {
                    let (result, params, _) = self.callable(m, md, params, None)?;
                    let (name, constraints) = self.generic_name(md, self.member_name(md))?;
                    out.push(Member::Method {
                        access: self.member_access(md, interface),
                        modifiers: if self.hides(decl, md) { "new " } else { "" },
                        result,
                        name,
                        params,
                        constraints,
                        body: None,
                    });
                }
                Node::Function {
                    decl: f,
                    params,
                    body,
                } => {
                    let (result, params, body) = self.callable(m, f, params, Some(body))?;
                    let (name, constraints) = self.generic_name(f, self.member_name(f))?;
                    // A private one is `internal`, as the module's class,
                    // whose static constructor sets static fields, may
                    // call it (`last = Named("last")`).
                    let access = match self.member_access(f, false) {
                        Some(Access::Private) => Some(Access::Internal),
                        access => access,
                    };
                    out.push(Member::Method {
                        access,
                        modifiers: "static ",
                        result,
                        name,
                        params,
                        constraints,
                        body,
                    });
                }
                _ => {}
            }
        }
        out.extend(properties.take().unwrap_or_default());
        if !interface {
            out.extend(self.forwards(decl)?);
        }
        Ok(out)
    }

    /// A getter or setter of a class or interface, or both, by their name,
    /// as one property, as Kotlin's: its access the wider of theirs, and
    /// the narrower an accessor's own (`private set`). A setter's
    /// parameter is C#'s `value`.
    fn property(
        &mut self,
        owner: DeclId,
        members: &[NodeId],
        name: &str,
        interface: bool,
    ) -> R<(tree::Member, Vec<tree::Member>)> {
        let p = self.p;
        let mut get = None;
        let mut set = None;
        let mut helpers = Vec::new();
        let mut ty = String::new();
        let mut decls = Vec::new();
        for &m in members {
            let (Node::Method { decl, params, .. } | Node::AbstractMethod { decl, params }) =
                p.nodes.get(m)
            else {
                continue;
            };
            let kind = p.decls.get(decl).kind;
            if !matches!(kind, DeclKind::Getter | DeclKind::Setter)
                || self.member_name(decl) != name
            {
                continue;
            }
            let body = match p.nodes.get(m) {
                Node::Method { body, .. } => Some(body),
                _ => None,
            };
            // An interface's default getter or setter has its body in a
            // static helper, as a method's.
            let helper =
                interface && body.is_some() && !p.decls.get(decl).facts.contains(Facts::PRIVATE);
            if kind == DeclKind::Setter {
                let [param] = p.nodes.list(params)[..] else {
                    return not_yet("a setter without one parameter");
                };
                let Node::Param { decl: param } = p.nodes.get(param) else {
                    return not_yet("a setter's parameter");
                };
                self.renamed.insert(param, "value".into());
                let translated = match body {
                    Some(body) if helper => self.helper_body(m, decl, params, body),
                    _ => self.callable(m, decl, params, body),
                };
                self.renamed.remove(&param);
                let (_, ps, mut body) = translated?;
                ty = ps[0].0.clone();
                if helper {
                    let name = self.default_helper(decl);
                    let call = Expr::name(name.clone())
                        .call(vec![Expr::name("this"), Expr::name("value")]);
                    let this = (self.own_type(owner)?, "@this".to_string());
                    helpers.push(tree::Member::Method {
                        access: Some(Access::Protected),
                        modifiers: "static ",
                        result: "void".into(),
                        name,
                        params: vec![this, ps[0].clone()],
                        constraints: Vec::new(),
                        body: body.take(),
                    });
                    body = Some(vec![Stmt::Expr(call)]);
                }
                set = Some((decl, body));
            } else {
                let (result, _, mut body) = match body {
                    Some(body) if helper => self.helper_body(m, decl, params, body)?,
                    _ => self.callable(m, decl, params, body)?,
                };
                if helper {
                    let name = self.default_helper(decl);
                    let call = Expr::name(name.clone()).call(vec![Expr::name("this")]);
                    let this = (self.own_type(owner)?, "@this".to_string());
                    helpers.push(tree::Member::Method {
                        access: Some(Access::Protected),
                        modifiers: "static ",
                        result: result.clone(),
                        name,
                        params: vec![this],
                        constraints: Vec::new(),
                        body: body.take(),
                    });
                    body = Some(vec![Stmt::Return(Some(call))]);
                }
                ty = result;
                get = Some((decl, body));
            }
            decls.push(decl);
        }
        fn rank(a: Option<Access>) -> u8 {
            match a {
                None | Some(Access::Public) => 3,
                Some(Access::Protected) => 2,
                Some(Access::Internal) => 1,
                Some(Access::Private) => 0,
            }
        }
        let widest = (decls.iter())
            .map(|&d| self.member_access(d, interface))
            .max_by_key(|&a| rank(a))
            .flatten();
        let accessor = |this: &Self, (decl, body): (DeclId, Option<Vec<Stmt>>)| {
            let access = this.member_access(decl, interface);
            tree::Accessor {
                access: (rank(access) != rank(widest)).then_some(access).flatten(),
                body,
            }
        };
        let hides = interface && decls.iter().any(|&d| self.hides(owner, d));
        let property = tree::Member::Property {
            access: widest,
            modifiers: if hides { "new " } else { "" },
            ty,
            name: name.to_string(),
            get: get.map(|g| accessor(self, g)),
            set: set.map(|s| accessor(self, s)),
        };
        Ok((property, helpers))
    }

    /// The member that Temper calls on a class's instance by a name, of a
    /// kind (method, getter, or setter): the class's own, else the nearest
    /// of its interfaces', breadth-first, the first in `extends` order
    /// among equally near ones, as tempest's interpreter finds it.
    fn implementation(&self, class: DeclId, name: &str, kind: DeclKind) -> Option<DeclId> {
        let p = self.p;
        let mut queue = std::collections::VecDeque::from([class]);
        let mut seen = vec![class];
        while let Some(d) = queue.pop_front() {
            if let Some(&node) = self.type_nodes.get(&d) {
                for m in members(p, node) {
                    let md = p.decls.get(m);
                    let field = md.kind == DeclKind::Field && !md.facts.contains(Facts::PRIVATE);
                    let fits = md.kind == kind
                        || (field && kind == DeclKind::Getter)
                        || (field && kind == DeclKind::Setter && md.facts.contains(Facts::VAR));
                    if fits && p.name(m) == name {
                        return Some(m);
                    }
                }
            }
            for &s in p.decls.supers(d) {
                if let Some(up) = nominal_decl(p, s) {
                    if !seen.contains(&up) {
                        seen.push(up);
                        queue.push_back(up);
                    }
                }
            }
        }
        None
    }

    /// The members a class gets from its interfaces' default members,
    /// as Kotlin's: for each method, getter, or setter it doesn't define,
    /// a public one that calls the body that Temper calls
    /// ([`Self::implementation`]), its interface's static helper
    /// (`IC.ADefault(this)`), so it implements the members of that name of
    /// all its interfaces, and C# callers find it on the class.
    fn forwards(&mut self, class: DeclId) -> R<Vec<tree::Member>> {
        let p = self.p;
        let mut out = Vec::new();
        let mut done: Vec<(bool, String)> = Vec::new();
        for up in ancestors(p, class) {
            let Some(&node) = self.type_nodes.get(&up) else {
                continue;
            };
            for m in members(p, node) {
                let kind = p.decls.get(m).kind;
                if !matches!(kind, DeclKind::Method | DeclKind::Getter | DeclKind::Setter) {
                    continue;
                }
                let name = p.name(m).to_string();
                let key = (kind == DeclKind::Method, name.clone());
                if done.contains(&key) {
                    continue;
                }
                done.push(key);
                if kind == DeclKind::Method {
                    let chosen = self.implementation(class, &name, kind);
                    if let Some(chosen) = chosen.filter(|&c| self.inherited_default(class, c)) {
                        out.push(self.forward_method(class, chosen)?);
                    }
                    continue;
                }
                let get = (self.implementation(class, &name, DeclKind::Getter))
                    .filter(|&c| self.inherited_default(class, c));
                let set = (self.implementation(class, &name, DeclKind::Setter))
                    .filter(|&c| self.inherited_default(class, c));
                if get.is_some() || set.is_some() {
                    out.push(self.forward_property(class, get, set)?);
                }
            }
        }
        Ok(out)
    }

    /// Whether a member is a default one, with a body, of an interface
    /// above a class.
    fn inherited_default(&self, class: DeclId, member: DeclId) -> bool {
        let p = self.p;
        let owner = p.decls.get(member).owner;
        owner != class
            && p.decls.get(owner).kind == DeclKind::Interface
            && self.type_nodes.get(&owner).is_some_and(|&node| {
                (p.nodes.kids(node).iter())
                    .any(|&m| matches!(p.nodes.get(m), Node::Method { decl, .. } if decl == member))
            })
    }

    /// The node of a class's or interface's member.
    fn member_node(&self, member: DeclId) -> Option<NodeId> {
        let p = self.p;
        let owner = p.decls.get(member).owner;
        let node = *self.type_nodes.get(&owner)?;
        (p.nodes.kids(node).iter())
            .copied()
            .find(|&m| match p.nodes.get(m) {
                Node::Method { decl, .. } | Node::AbstractMethod { decl, .. } => decl == member,
                _ => false,
            })
    }

    /// The static helper that holds an interface's default member's body,
    /// as Kotlin's: `ADefault` for a method `a`, `GetXDefault` and
    /// `SetXDefault` for a getter and setter `x`.
    fn default_helper(&self, member: DeclId) -> String {
        let prefix = match self.p.decls.get(member).kind {
            DeclKind::Getter => "Get",
            DeclKind::Setter => "Set",
            _ => "",
        };
        format!("{prefix}{}Default", self.member_name(member))
    }

    /// A call of an interface's default member's helper, on `this` and
    /// `args`.
    fn helper_call(&mut self, class: DeclId, member: DeclId, args: Vec<Expr>) -> R<Expr> {
        let owner = self.p.decls.get(member).owner;
        let ty = self.owner_type(owner, self.p.decls.get(class).ty)?;
        let helper = self.default_helper(member);
        let mut all = vec![Expr::name("this")];
        all.extend(args);
        Ok(Expr::name(ty).method(helper, all))
    }

    /// `public R A(…) { return IC.ADefault(this, …); }`.
    fn forward_method(&mut self, class: DeclId, chosen: DeclId) -> R<tree::Member> {
        let p = self.p;
        let Some(m) = self.member_node(chosen) else {
            return not_yet("an interface's member without its node");
        };
        let Node::Method { params, .. } = p.nodes.get(m) else {
            return not_yet("an interface's default member that isn't a method");
        };
        // Its types as the class gives its interface's type parameters.
        let owner = p.decls.get(chosen).owner;
        let args = self.args_from(class, owner);
        let saved = std::mem::replace(&mut self.type_args, args);
        let translated = self.callable(m, chosen, params, None);
        self.type_args = saved;
        let (result, params, _) = translated?;
        let args = params.iter().map(|(_, n)| Expr::name(n.clone())).collect();
        let call = self.helper_call(class, chosen, args)?;
        Ok(tree::Member::Method {
            access: Some(Access::Public),
            modifiers: "",
            result: result.clone(),
            name: self.member_name(chosen),
            params,
            constraints: Vec::new(),
            body: Some(vec![returning(&result, call)]),
        })
    }

    /// A public property whose getter, setter, or both call interfaces'
    /// helpers.
    fn forward_property(
        &mut self,
        class: DeclId,
        get: Option<DeclId>,
        set: Option<DeclId>,
    ) -> R<tree::Member> {
        let p = self.p;
        let mut ty = String::new();
        let mut name = String::new();
        if let Some(g) = get {
            let owner = p.decls.get(g).owner;
            let args = self.args_from(class, owner);
            let saved = std::mem::replace(&mut self.type_args, args);
            let written = self.ty(p.signature(g).map_or(TypeId::VOID, |s| s.result));
            self.type_args = saved;
            ty = written?;
            name = self.member_name(g);
        }
        if let Some(s) = set {
            let owner = p.decls.get(s).owner;
            let param = p.signature(s).and_then(|s| s.params.first().copied());
            let args = self.args_from(class, owner);
            let saved = std::mem::replace(&mut self.type_args, args);
            let written = self.ty(param.unwrap_or(TypeId::VOID));
            self.type_args = saved;
            ty = written?;
            name = self.member_name(s);
        }
        let get = match get {
            Some(g) => Some(tree::Accessor {
                access: None,
                body: Some(vec![Stmt::Return(Some(self.helper_call(
                    class,
                    g,
                    Vec::new(),
                )?))]),
            }),
            None => None,
        };
        let set = match set {
            Some(s) => Some(tree::Accessor {
                access: None,
                body: Some(vec![Stmt::Expr(self.helper_call(
                    class,
                    s,
                    vec![Expr::name("value")],
                )?)]),
            }),
            None => None,
        };
        Ok(tree::Member::Property {
            access: Some(Access::Public),
            modifiers: "",
            ty,
            name,
            get,
            set,
        })
    }

    /// A module's test class, `NAMETests`, an MSTest class in its
    /// namespace, in the tests' project: what only its tests reach, as
    /// statics ([`Self::statics_class`]) whose static constructor runs
    /// the module class's first, then continues it, with its classes and
    /// interfaces ([`Self::take_types`]), and its tests: each a method
    /// named for it in Pascal case (`Twice10` for "twice 10"), which makes
    /// std's `Test`, runs the body, and then, even if the body threw,
    /// fails if the `Test` has failures (`SoftFailToHard`), as Kotlin's
    /// do. They see the library's `internal` members (`InternalsVisibleTo`
    /// in its project). A test's update of the library's module `var`
    /// doesn't take its lock, which is private to its class. `None`
    /// without test parts or tests.
    pub fn tests(&mut self, module: &Module) -> R<Option<tree::TestClass>> {
        let p = self.p;
        if module.tests.is_empty() && module.test_items.is_empty() {
            return Ok(None);
        }
        if let Some(&local) =
            (self.test_parts.used.iter()).find(|&&d| p.decls.get(d).kind == DeclKind::Local)
        {
            return not_yet(format!(
                "tests that use {}, a local of their module's init block",
                p.name(local)
            ));
        }
        self.in_tests = true;
        self.usings.clear();
        self.classes.clear();
        self.class = self.test_class.clone();
        let mut outside = HashSet::new();
        for &test in &module.tests {
            outside.extend(module_refs(p, test));
        }
        let statics = self.statics_class(module, &module.test_items, module.test_init, &outside)?;
        self.in_function = true;
        self.result = None;
        let mut used = BTreeSet::new();
        let mut methods = Vec::new();
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
            let ty = self.ty(p.decls.get(param).ty)?;
            let var = self.name(param);
            self.begin(test);
            let stmts = self.stmts(body)?;
            let soft_fail = Expr::name(var.clone()).method("SoftFailToHard", Vec::new());
            methods.push((
                test_method_name(p.strings.get(name), &mut used),
                vec![
                    Stmt::Local {
                        ty: ty.clone(),
                        name: var,
                        value: Some(Expr::name(format!("new {ty}")).call(Vec::new())),
                    },
                    Stmt::Try {
                        body: stmts,
                        finally: vec![Stmt::Expr(soft_fail)],
                    },
                ],
            ));
        }
        let mut statics = statics;
        // The helpers and lock of the test parts and tests.
        statics.methods.extend(self.helper_methods());
        if let Some(lock) = self.lock.take() {
            statics.fields.push(tree::Field {
                access: if std::mem::take(&mut self.lock_shared) {
                    Access::Internal
                } else {
                    Access::Private
                },
                readonly: true,
                volatile: false,
                ty: "object".into(),
                name: lock,
                value: Some(Expr::name("new").call(Vec::new())),
            });
        }
        self.usings
            .insert("Microsoft.VisualStudio.TestTools.UnitTesting".into());
        statics.usings = self.usings.iter().cloned().collect();
        Ok(Some(tree::TestClass {
            statics,
            tests: methods,
        }))
    }

    /// The module's lock, named so no name of the program's hides it.
    fn lock(&mut self) -> String {
        if let Some(lock) = &self.lock {
            return lock.clone();
        }
        let lock = (1..)
            .map(|n| match n {
                1 => "moduleLock".to_string(),
                n => format!("moduleLock{n}"),
            })
            .find(|n| !self.taken.contains(n))
            .unwrap_or_default();
        self.lock = Some(lock.clone());
        lock
    }

    /// Starts translating a function or the init block: plans which loops
    /// need labels, those that a jump from an inner loop leaves or
    /// continues. A label takes its loop's source name, or for a
    /// compiler-made one, `outer`, then `End` for a `break` and `Next` for
    /// a `continue`, numbered only if another label has it.
    fn begin(&mut self, scope: NodeId) {
        fn visit(
            p: &Program,
            id: NodeId,
            loops: &mut Vec<DeclId>,
            jumps: &mut Vec<(DeclId, bool)>,
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
                    if loops.last() != Some(&label) && !jumps.contains(&(label, brk)) {
                        jumps.push((label, brk));
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
        let mut jumps = Vec::new();
        visit(p, scope, &mut Vec::new(), &mut jumps);
        self.loops.clear();
        self.labels.clear();
        let mut taken: HashSet<String> = HashSet::new();
        for (l, brk) in jumps {
            let d = p.decls.get(l);
            let base = if d.facts.contains(Facts::COMPILER_MADE) || p.name(l).is_empty() {
                "outer".to_string()
            } else {
                p.name(l).to_string()
            };
            let base = format!("{base}{}", if brk { "End" } else { "Next" });
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
        Ok(reachable(out))
    }

    fn loop_body(&mut self, label: DeclId, body: NodeId) -> R<Vec<Stmt>> {
        self.loops.push(label);
        let body = self.stmts(body);
        self.loops.pop();
        body
    }

    /// A statement, after the statements its expressions need first.
    fn stmt(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let saved = std::mem::take(&mut self.pre);
        let hoist = self.hoists(id);
        let saved_hoist = std::mem::replace(&mut self.hoist, hoist);
        let mut mine = Vec::new();
        let done = self.stmt_alone(id, &mut mine);
        let pre = std::mem::replace(&mut self.pre, saved);
        self.hoist = saved_hoist;
        done?;
        out.extend(pre);
        out.extend(mine);
        Ok(())
    }

    /// The `BlockValue`s and `OrElse`s among a statement's own
    /// expressions that may run before it: those that run unconditionally,
    /// with nothing before them in the statement that has an effect, or
    /// that reads a `var`, which they might assign. A loop's condition runs
    /// again each time, so none of it does.
    fn hoists(&self, id: NodeId) -> HashSet<NodeId> {
        let p = self.p;
        let mut found = HashSet::new();
        if matches!(p.nodes.get(id), Node::While { .. } | Node::DoWhile { .. }) {
            return found;
        }
        let mut effect = false;
        for &kid in p.nodes.kids(id) {
            if p.nodes.ty(kid).is_none() {
                continue;
            }
            self.hoist_walk(kid, &mut effect, &mut found);
        }
        found
    }

    fn hoist_walk(&self, id: NodeId, effect: &mut bool, found: &mut HashSet<NodeId>) {
        let p = self.p;
        let reads_var = |d: DeclId| p.decls.get(d).facts.contains(Facts::VAR);
        match p.nodes.get(id) {
            // One hoisted runs before the statement, after those hoisted
            // before it, so it's no effect for the next.
            Node::BlockValue { .. } | Node::OrElse { .. } => {
                if *effect {
                    return;
                }
                found.insert(id);
                return;
            }
            // An `await` is a `yield return` before its statement, after
            // its promise, which may hoist parts of its own; what comes
            // after it waits for it.
            Node::Await { promise, .. } => {
                if !*effect {
                    found.insert(id);
                }
                self.hoist_walk(promise, effect, found);
                *effect = true;
                return;
            }
            Node::And { left, .. } | Node::Or { left, .. } => {
                self.hoist_walk(left, effect, found);
                *effect = true;
                return;
            }
            Node::IfValue { cond, .. } => {
                self.hoist_walk(cond, effect, found);
                *effect = true;
                return;
            }
            // Its fallback runs only if needed.
            Node::Coalesce { left, .. } => {
                self.hoist_walk(left, effect, found);
                *effect = true;
                return;
            }
            _ => {}
        }
        for &kid in p.nodes.kids(id) {
            self.hoist_walk(kid, effect, found);
        }
        let pure = match p.nodes.get(id) {
            // A `var` no hoisted block assigns reads the same either way.
            Node::Local { local: d } | Node::GetModuleLet { target: d } => {
                !reads_var(d) || !self.block_writes.contains(&d)
            }
            Node::GetField { field: d, .. } | Node::GetStatic { target: d } => !reads_var(d),
            Node::Op { may_bubble, .. } => !may_bubble,
            // Core's that only read.
            Node::CallMethod {
                method,
                may_bubble: false,
                ..
            } if is_external(p, method) && library_of(p, method) == "core" => {
                let q = self.qname(method);
                q.ends_with(".toString()")
                    || q.ends_with(".get length()")
                    || q.ends_with(".get isEmpty()")
                    || q == "core.type Mapped.has()"
            }
            Node::BoolLit { .. }
            | Node::Int32Lit { .. }
            | Node::Int64Lit { .. }
            | Node::Float64Lit { .. }
            | Node::StringLit { .. }
            | Node::VoidLit {}
            | Node::TypeValue { .. }
            | Node::This {}
            | Node::Null {}
            | Node::IsNull { .. }
            | Node::NonNull { .. }
            | Node::AsNullable { .. }
            | Node::EqNullable { .. }
            | Node::Upcast { .. }
            | Node::Downcast { .. }
            | Node::Is { .. }
            | Node::Identical { .. }
            | Node::Omitted {} => true,
            _ => false,
        };
        if !pure {
            *effect = true;
        }
    }

    /// A value given to a place: assigned, returned, or dropped. An
    /// `OrElse` is a `try`, a `BlockValue` its statements first, each
    /// part given to the same place.
    fn sink(&mut self, id: NodeId, place: &Sink, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::OrElse { body, fallback } => {
                let mut tried = Vec::new();
                self.sink(body, place, &mut tried)?;
                let mut caught = Vec::new();
                self.sink(fallback, place, &mut caught)?;
                if yields(&tried) || yields(&caught) {
                    out.push(self.try_around_yields(tried, caught)?);
                    return Ok(());
                }
                out.push(Stmt::TryCatch {
                    body: tried,
                    catch: caught,
                });
                return Ok(());
            }
            Node::BlockValue { stmts, value } => {
                for &s in p.nodes.list(stmts) {
                    self.stmt(s, out)?;
                }
                return self.sink(value, place, out);
            }
            Node::Panic {} => {
                out.extend(self.panic());
                return Ok(());
            }
            Node::Bubble { .. } | Node::Error { .. } if matches!(place, Sink::Drop) => {
                out.push(self.bubble());
                return Ok(());
            }
            // `void`, as an `orelse`'s fallback, does nothing, as does any
            // value without effects; a conversion that checks nothing
            // (C#'s `e!`) drops with its value.
            _ if matches!(place, Sink::Drop) && no_effects(p, id) => return Ok(()),
            Node::NonNull { value } | Node::AsNullable { value } | Node::Upcast { value }
                if matches!(place, Sink::Drop) =>
            {
                return self.sink(value, place, out);
            }
            // A conditional whose branch needs statements: an `if`, each
            // branch given to the place, as Kotlin's.
            Node::IfValue { cond, then, els } if self.needs_statements(id) => {
                let cond = self.value_after(cond, out)?;
                let mut then_out = Vec::new();
                self.sink(then, place, &mut then_out)?;
                let mut els_out = Vec::new();
                self.sink(els, place, &mut els_out)?;
                out.push(Stmt::If {
                    cond,
                    then: then_out,
                    els: els_out,
                });
                return Ok(());
            }
            _ => {}
        }
        let e = self.value_after(id, out)?;
        out.push(match place {
            Sink::Assign(target) => Stmt::Assign {
                target: target.clone(),
                value: e,
            },
            Sink::Return => Stmt::Return(Some(e)),
            Sink::Drop if e.is_statement() => Stmt::Expr(e),
            // `_ = e;`: a value whose effects alone matter.
            Sink::Drop => Stmt::Assign {
                target: Expr::name("_"),
                value: e,
            },
        });
        Ok(())
    }

    /// A value as an expression, after the statements of the `OrElse`s and
    /// `BlockValue`s in it that may run first ([`Translator::hoists`]).
    fn value_after(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<Expr> {
        let saved = std::mem::take(&mut self.pre);
        let mut found = HashSet::new();
        let mut effect = false;
        self.hoist_walk(id, &mut effect, &mut found);
        // An `await` stays, as its `yield` goes before it.
        if !matches!(self.p.nodes.get(id), Node::Await { .. }) {
            found.remove(&id);
        }
        let saved_hoist = std::mem::replace(&mut self.hoist, found);
        let e = self.expr(id);
        let pre = std::mem::replace(&mut self.pre, saved);
        self.hoist = saved_hoist;
        let e = e?;
        out.extend(pre);
        Ok(e)
    }

    /// An `orelse` whose `try` would hold a `yield return`, which C#
    /// forbids, as Kotlin's: each run of statements between `yield`s is a
    /// `try` of its own, whose `catch` jumps to the fallback, after
    /// them; its locals and local functions are declared before them all,
    /// so each run sees the others'.
    fn try_around_yields(&mut self, tried: Vec<Stmt>, caught: Vec<Stmt>) -> R<Stmt> {
        let catch_label = self.temporary("CATCH");
        let ok_label = self.temporary("OK");
        let mut split = Split {
            catch: catch_label.clone(),
            decls: Vec::new(),
            out: Vec::new(),
            run: Vec::new(),
        };
        self.split_yields(tried, &mut split)?;
        split.flush();
        let Split { mut decls, out, .. } = split;
        decls.extend(out);
        decls.push(Stmt::Goto(ok_label.clone()));
        decls.push(Stmt::Label(catch_label));
        decls.push(Stmt::Block(caught));
        decls.push(Stmt::Label(ok_label));
        Ok(Stmt::Block(decls))
    }

    /// [`Translator::try_around_yields`]'s statements, into `split`: an
    /// `if` or a block that yields is split too, an `if` by `goto`s to
    /// labels outside the `try`s, as a `try`'s `catch` can't hold a
    /// `yield return` nor a `goto` jump into a `try`.
    fn split_yields(&mut self, stmts: Vec<Stmt>, split: &mut Split) -> R<()> {
        for s in stmts {
            match s {
                Stmt::YieldReturn(_) => {
                    split.flush();
                    split.out.push(s);
                }
                Stmt::Block(b) if yields(&b) => self.split_yields(b, split)?,
                Stmt::If { cond, then, els } if yields(&then) || yields(&els) => {
                    let els_label = self.temporary("ELSE");
                    let end_label = self.temporary("END");
                    split.run.push(Stmt::If {
                        cond: Expr::Unary(Unary::Not, Box::new(cond)),
                        then: vec![Stmt::Goto(els_label.clone())],
                        els: Vec::new(),
                    });
                    self.split_yields(then, split)?;
                    split.flush();
                    split.out.push(Stmt::Goto(end_label.clone()));
                    split.out.push(Stmt::Label(els_label));
                    self.split_yields(els, split)?;
                    split.flush();
                    split.out.push(Stmt::Label(end_label));
                }
                s if yields(std::slice::from_ref(&s)) => {
                    return not_yet("an await nested in a loop in an orelse");
                }
                Stmt::Local { ty, name, value } => {
                    // Declared first, so each run sees it; a name declared
                    // twice, in blocks of their own, would clash there.
                    let declared =
                        |d: &Stmt| matches!(d, Stmt::Local { name: n, .. } if *n == name);
                    if split.decls.iter().any(declared) {
                        return not_yet("a local declared twice in an orelse that awaits");
                    }
                    if let Some(value) = value {
                        split.run.push(Stmt::Assign {
                            target: Expr::name(name.clone()),
                            value,
                        });
                    }
                    split.decls.push(Stmt::Local {
                        ty,
                        name,
                        value: None,
                    });
                }
                s @ Stmt::LocalFunction { .. } => split.decls.push(s),
                s => split.run.push(s),
            }
        }
        Ok(())
    }

    /// `panic()`: the process ends at once, as no `catch` can stop it;
    /// Kotlin's throws, which an `orelse` catches. The `throw` after it
    /// tells C# that nothing after it runs.
    fn panic(&mut self) -> Vec<Stmt> {
        let env = self.class("Environment");
        let fail = env.method("FailFast", vec![Expr::Str("panic".into())]);
        let unreachable = self.construct("InvalidOperationException", Vec::new());
        vec![Stmt::Expr(fail), Stmt::Throw(unreachable)]
    }

    /// A fresh local's name, for a value hoisted out of an expression.
    pub(crate) fn temporary(&mut self, base: &str) -> String {
        let mut n = 1;
        loop {
            let name = format!("{base}{n}");
            if self.taken.insert(name.clone()) {
                return name;
            }
            n += 1;
        }
    }

    /// Whether a value is given to its place by statements: an `OrElse`
    /// or a `BlockValue`, or a `panic()`, or a conditional with one as a
    /// branch.
    fn needs_statements(&self, id: NodeId) -> bool {
        match self.p.nodes.get(id) {
            Node::OrElse { .. } | Node::BlockValue { .. } | Node::Panic {} => true,
            Node::IfValue { then, els, .. } => {
                self.needs_statements(then) || self.needs_statements(els)
            }
            _ => false,
        }
    }

    fn stmt_alone(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Let {
                local,
                init: Some(value),
            } if self.needs_statements(value) => {
                let ty = self.ty(p.decls.get(local).ty)?;
                let name = self.name(local);
                out.push(Stmt::Local {
                    ty,
                    name: name.clone(),
                    value: None,
                });
                self.sink(value, &Sink::Assign(Expr::name(name)), out)?;
            }
            Node::Assign {
                local: target,
                value,
            }
            | Node::SetModuleLet { target, value }
            | Node::InitLet { target, value }
                if self.needs_statements(value) =>
            {
                let target = self.top_name(target);
                self.sink(value, &Sink::Assign(target), out)?;
            }
            Node::Return { value: Some(value) } if self.needs_statements(value) => {
                self.sink(value, &Sink::Return, out)?;
            }
            Node::ExprStatement { expr } if self.needs_statements(expr) => {
                self.sink(expr, &Sink::Drop, out)?;
            }
            Node::ExprStatement { expr } if matches!(p.nodes.get(expr), Node::Bubble { .. }) => {
                out.push(self.bubble());
            }
            // A block inside a block keeps its braces: its locals may
            // share names with another block's (names::RULES).
            Node::Block { .. } => out.push(Stmt::Block(self.stmts(id)?)),
            // A local function with statements, as a local function.
            Node::Let {
                local,
                init: Some(init),
            } if !p.decls.get(local).facts.contains(Facts::VAR)
                && matches!(p.nodes.get(init), Node::FunctionLiteral { body, .. }
                    if !single_value(p, body)) =>
            {
                let Node::FunctionLiteral { decl, params, body } = p.nodes.get(init) else {
                    unreachable!()
                };
                let name = self.name(local);
                self.literal(init, decl, params, body, Some(name))?;
                out.append(&mut self.pre);
            }
            // A parameter's null default: the parameter itself.
            Node::Let {
                local,
                init: Some(init),
            } if null_default(p, init).is_some() => {
                let param = null_default(p, init).unwrap();
                self.renamed.insert(local, self.name(param));
            }
            // A default with statements: `if (x == null) { … } else …`.
            Node::Let {
                local,
                init: Some(init),
            } if matches!(p.nodes.get(init), Node::Coalesce { right, .. } if needs_stmts(p, right)) =>
            {
                let Node::Coalesce { left, right } = p.nodes.get(init) else {
                    unreachable!()
                };
                let ty = self.ty(p.decls.get(local).ty)?;
                let name = self.name(local);
                out.push(Stmt::Local {
                    ty,
                    name: name.clone(),
                    value: None,
                });
                let place = Sink::Assign(Expr::name(name.clone()));
                let mut then = Vec::new();
                self.sink(right, &place, &mut then)?;
                let given = self.coalesced(left, p.nodes.ty(init))?;
                out.push(Stmt::If {
                    cond: Expr::bin(Bin::Eq, self.expr(left)?, Expr::name("null")),
                    then,
                    els: vec![Stmt::Assign {
                        target: Expr::name(name),
                        value: given,
                    }],
                });
            }
            Node::Let { local, init } => {
                let ty = self.ty(p.decls.get(local).ty)?;
                let value = init.map(|v| self.expr(v)).transpose()?;
                out.push(Stmt::Local {
                    ty,
                    name: self.name(local),
                    value,
                });
            }
            // Its class's lock, from that class or its part's types; tests
            // updating the library's don't take its lock.
            Node::SetModuleLet { target, value }
                if self.in_function
                    && home(p, target) == Some(self.here)
                    && self.holds_tests(target) == self.in_tests
                    && reads_only_itself(p, target, value) =>
            {
                let value = self.expr(value)?;
                let lock = self.lock();
                let lock = if self.in_type.is_some() {
                    self.lock_shared = true;
                    Expr::name(self.part_class(self.in_tests)).member(lock)
                } else {
                    Expr::name(lock)
                };
                let assign = Stmt::Assign {
                    target: self.top_name(target),
                    value,
                };
                out.push(Stmt::Lock {
                    lock,
                    body: vec![assign],
                });
            }
            Node::Assign {
                local: target,
                value,
            }
            | Node::SetModuleLet { target, value }
            | Node::InitLet { target, value } => {
                let value = self.expr(value)?;
                let target = self.top_name(target);
                // `x = x` does nothing, and C# warns of it.
                if value != target {
                    out.push(Stmt::Assign { target, value });
                }
            }
            Node::SetField {
                field,
                object,
                value,
            } => {
                self.nested_optionals(field, p.decls.get(field).ty, p.nodes.ty(value))?;
                let target = self.field(field, object)?;
                let value = self.expr(value)?;
                out.push(Stmt::Assign { target, value });
            }
            Node::InitStatic { target, value } => {
                let Some(storage) = self.static_storage.get(&target).cloned() else {
                    return not_yet("a static field of another module's initialized");
                };
                let value = self.expr(value)?;
                out.push(Stmt::Assign {
                    target: Expr::name(storage),
                    value,
                });
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
                out.push(Stmt::While {
                    cond,
                    body,
                    labels: self.labels.get(&label).cloned().unwrap_or_default(),
                });
            }
            Node::DoWhile { label, body, cond } => {
                let body = self.loop_body(label, body)?;
                let cond = self.expr(cond)?;
                out.push(Stmt::DoWhile {
                    body,
                    cond,
                    labels: self.labels.get(&label).cloned().unwrap_or_default(),
                });
            }
            Node::Break { label } | Node::Continue { label } => {
                let brk = matches!(p.nodes.get(id), Node::Break { .. });
                if self.loops.last() == Some(&label) {
                    out.push(if brk { Stmt::Break } else { Stmt::Continue });
                } else {
                    let labels = self.labels.get(&label);
                    let target = if brk {
                        labels.and_then(|l| l.end.clone())
                    } else {
                        labels.and_then(|l| l.next.clone())
                    };
                    match target {
                        Some(l) => out.push(Stmt::Goto(l)),
                        None => return not_yet("a jump to a loop that doesn't enclose it"),
                    }
                }
            }
            Node::Return { value } => match value.map(|v| (v, p.nodes.get(v))) {
                None | Some((_, Node::VoidLit {})) => out.push(Stmt::Return(None)),
                Some((_, Node::Error { .. }) | (_, Node::Bubble { .. })) => out.push(self.bubble()),
                Some((v, _)) => {
                    let e = self.expr(v)?;
                    out.push(Stmt::Return(Some(e)));
                }
            },
            Node::ExprStatement { expr } => {
                if let Node::Error { .. } = p.nodes.get(expr) {
                    out.push(self.bubble());
                    return Ok(());
                }
                let expr = unconverted(p, expr);
                self.unnamed.insert(expr);
                // A setter's call is an assignment of its property.
                if let Node::CallMethod {
                    method,
                    receiver,
                    args,
                    ..
                } = p.nodes.get(expr)
                {
                    if p.decls.get(method).kind == DeclKind::Setter
                        && !by_connection(p, method, crate::connected::connects_std)
                    {
                        let [value] = p.nodes.list(args)[..] else {
                            return not_yet("a setter's call without one argument");
                        };
                        let target = self
                            .receiver(method, receiver)?
                            .member(self.member_name(method));
                        let value = self.expr(value)?;
                        out.push(Stmt::Assign { target, value });
                        return Ok(());
                    }
                }
                // An object made for its constructor's effects alone, `_ =
                // new C();`, as C#'s analyzers want (CA1806); a cast for its
                // check alone, `_ = (C)x;`; or an `await`'s result, which
                // throws if its promise is broken.
                let discard = match p.nodes.get(expr) {
                    Node::New { ctor, .. } => {
                        !by_connection(p, ctor, crate::connected::connects_std)
                    }
                    Node::Await { .. } => true,
                    _ => false,
                };
                if discard || matches!(p.nodes.get(expr), Node::As { .. }) {
                    let e = self.expr(expr)?;
                    if discard || !e.is_statement() {
                        out.push(Stmt::Assign {
                            target: Expr::name("_"),
                            value: e,
                        });
                    } else {
                        out.push(Stmt::Expr(e));
                    }
                    return Ok(());
                }
                // Any other value: a call as itself, nothing for a value
                // without effects, else `_ = e;`.
                self.sink(expr, &Sink::Drop, out)?;
            }
            // What a generator yields is the runtime's business.
            Node::Yield { value } => {
                let value = match value {
                    Some(v) => self.expr(v)?,
                    None => Expr::name("default!"),
                };
                out.push(Stmt::YieldReturn(value));
            }
            other => return not_yet(format!("a {} statement", other.kind().name())),
        }
        Ok(())
    }

    /// An instance field where it's read or assigned: the field itself in
    /// its class, which may read it on any instance, else the property
    /// that gives it to others.
    fn field(&mut self, field: DeclId, object: NodeId) -> R<Expr> {
        self.unnamed.insert(object);
        // A std field the table connects, as `Date.year`'s `Year`.
        if by_connection(self.p, field, crate::connected::connects_std)
            && connected::lookup(&self.qname(field)).is_some()
        {
            return self.connected(field, &[object]);
        }
        let object = self.expr(object)?;
        // A pair's, a `KeyValuePair`'s.
        if by_connection(self.p, field, crate::connected::connects_std) {
            let name = match self.p.name(field) {
                "key" => "Key",
                _ => "Value",
            };
            return Ok(object.member(name));
        }
        let owner = self.p.decls.get(field).owner;
        Ok(if self.in_type == Some(owner) {
            object.member(self.name(field))
        } else {
            object.member(self.field_property(field))
        })
    }

    /// The class or interface an expression has in C#, as written: an
    /// upcast's is its value's, since C# converts by itself.
    fn written_type(&self, id: NodeId) -> Option<DeclId> {
        match self.p.nodes.get(id) {
            Node::Upcast { value } => self.written_type(value),
            _ => nominal_decl(self.p, self.p.nodes.ty(id)),
        }
    }

    /// A method's receiver, cast to the interface that owns the method
    /// unless it has that type already: an interface's members are found
    /// only through it (C# doesn't give a class its interfaces' default
    /// members), and an interface's member may hide another's of the same
    /// name.
    fn receiver(&mut self, method: DeclId, receiver: NodeId) -> R<Expr> {
        let owner = self.p.decls.get(method).owner;
        self.unnamed.insert(receiver);
        let e = self.expr(receiver)?;
        if self.p.is_virtual(method) && self.written_type(receiver) != Some(owner) {
            let of = self.p.nodes.ty(receiver);
            let ty = self.owner_type(owner, of)?;
            return Ok(Expr::Cast(ty, Box::new(e)));
        }
        Ok(e)
    }

    /// A call of a method or getter of the program's own: a getter's is
    /// its property's read, and a plain `toString()` is `ToString()`.
    fn call_method(
        &mut self,
        call: NodeId,
        method: DeclId,
        spelled: &str,
        receiver: NodeId,
        args: &[NodeId],
    ) -> R<Expr> {
        let p = self.p;
        self.check_call(method, call, args)?;
        let object = self.receiver(method, receiver)?;
        match p.decls.get(method).kind {
            DeclKind::Getter => Ok(object.member(self.member_name(method))),
            DeclKind::Setter => not_yet("a setter's call inside an expression"),
            _ => {
                let exprs = self.args(args)?;
                let exprs = self.optional_args(method, args, exprs)?;
                let name = if is_plain_to_string(p, method) {
                    "ToString".to_string()
                } else {
                    format!("{}{spelled}", self.member_name(method))
                };
                self.optional_result(method, call, object.method(name, exprs))
            }
        }
    }

    /// `<A, B>`, a generic call's type arguments, all of them, as C#
    /// can't leave some out, where C# can't infer them from the
    /// arguments `args`, as when a type parameter appears in no
    /// parameter's type, or only where the argument is `null` or left out
    /// (`analysis::infers_type_args_from`), as for a class-bounded `T?`,
    /// which is C#'s own `T?`; otherwise nothing, leaving them to C#.
    fn spelled_type_args(
        &mut self,
        callee: DeclId,
        type_args: temper_tmpl::TypeArgs,
        args: &[NodeId],
    ) -> R<String> {
        if temper_tmpl::analysis::infers_type_args_from(self.p, callee, args) {
            return Ok(String::new());
        }
        let mut texts = Vec::new();
        for &a in self.p.types.args_of(type_args) {
            texts.push(self.ty(a)?);
        }
        if texts.is_empty() {
            return Ok(String::new());
        }
        Ok(format!("<{}>", texts.join(", ")))
    }

    /// `Optional<T>`'s name, imported.
    fn optional_name(&mut self) -> String {
        match self.class("Optional") {
            Expr::Name(n) => n,
            _ => "Optional".to_string(),
        }
    }

    /// Arguments for parameters of a `T?`, `Optional<T>`, given values of
    /// a type that isn't, wrapped as Kotlin's `WrapAsOptional`:
    /// `Optional<A>.None` for null, else `Optional.Of<A>(x)` (an `A?` or
    /// an `A`). A value of another unconstrained type parameter's converts
    /// by itself.
    fn optional_args(
        &mut self,
        callee: DeclId,
        args: &[NodeId],
        mut exprs: Vec<Expr>,
    ) -> R<Vec<Expr>> {
        let p = self.p;
        let Some(sig) = p.signature(callee) else {
            return Ok(exprs);
        };
        let declared = sig.params.to_vec();
        let skip = declared.len().saturating_sub(args.len());
        for (i, e) in exprs.iter_mut().enumerate() {
            let (Some(&d), Some(&a)) = (declared.get(skip + i), args.get(i)) else {
                continue;
            };
            let actual = p.nodes.ty(a);
            let inner = p.types.non_null(actual);
            if !self.is_optional(d)
                || self.is_optional(actual)
                || matches!(p.types.get(inner),
                    TypeData::TypeParam { decl } if !self.class_bounded(decl))
                || matches!(p.nodes.get(a), Node::Omitted {})
            {
                continue;
            }
            let optional = self.optional_name();
            let inner = self.ty(inner)?;
            *e = if matches!(p.nodes.get(a), Node::Null {}) {
                Expr::name(format!("{optional}<{inner}>.None"))
            } else {
                let value = std::mem::replace(e, Expr::name("null"));
                Expr::name(optional).method(format!("Of<{inner}>"), vec![value])
            };
        }
        Ok(exprs)
    }

    /// A call's result, where its callee gives a `T?`, `Optional<T>`, and
    /// the call's type isn't one, unwrapped as Kotlin's `UnwrapOptional`:
    /// `Optional.ToNullable<A>(r)` for a value type's, else
    /// `Optional.OrNull<A>(r)`.
    fn optional_result(&mut self, callee: DeclId, call: NodeId, e: Expr) -> R<Expr> {
        let p = self.p;
        let declared = p.signature(callee).map(|s| s.result);
        if !declared.is_some_and(|d| self.is_optional(d)) {
            return Ok(e);
        }
        let actual = p.nodes.ty(call);
        let inner = p.types.non_null(actual);
        if self.is_optional(actual)
            || matches!(p.types.get(inner),
                TypeData::TypeParam { decl } if !self.class_bounded(decl))
        {
            return Ok(e);
        }
        let method = if self.is_value_type(inner) {
            "ToNullable"
        } else {
            "OrNull"
        };
        let optional = self.optional_name();
        let inner = self.ty(inner)?;
        Ok(Expr::name(optional).method(format!("{method}<{inner}>"), vec![e]))
    }

    /// A bubble as a statement: `Core.Bubble();`, or in a function with a
    /// result, `return Core.Bubble<T>();`, so C# sees the function end.
    fn bubble(&mut self) -> Stmt {
        let core = self.class("Core");
        match self.result.clone() {
            Some(ty) => Stmt::Return(Some(core.method(format!("Bubble<{ty}>"), Vec::new()))),
            None => Stmt::Bubble(core.method("Bubble", Vec::new())),
        }
    }

    /// A call of a connected declaration, by the connection table; `args`
    /// has a method's receiver first.
    fn connected(&mut self, decl: DeclId, args: &[NodeId]) -> R<Expr> {
        let qname = self.qname(decl);
        let Some(connection) = connected::lookup(&qname) else {
            return not_yet(format!("a connection for {qname}"));
        };
        match connection {
            Connection::Static(class, method) => {
                let exprs = self.args(args)?;
                let args = self.cast_groups(args, exprs)?;
                Ok(self.class(class).method(method, args))
            }
            Connection::Method(method) => {
                let Some((&receiver, rest)) = args.split_first() else {
                    return not_yet(format!("{qname} without its receiver"));
                };
                let receiver = self.expr(receiver)?;
                let rest = self.args(rest)?;
                Ok(receiver.method(method, rest))
            }
            Connection::Translate(f) => f(self, args),
        }
    }

    /// A connected call's arguments, method groups cast to their
    /// delegates, as a generic method needs.
    pub(crate) fn connected_args(&mut self, args: &[NodeId]) -> R<Vec<Expr>> {
        let exprs = self.args(args)?;
        self.cast_groups(args, exprs)
    }

    /// Arguments, those left out at the end dropped, as C#'s optional
    /// parameters and overloads leave them out.
    fn args(&mut self, args: &[NodeId]) -> R<Vec<Expr>> {
        let p = self.p;
        let given = args
            .iter()
            .rposition(|&a| !matches!(p.nodes.get(a), Node::Omitted {}))
            .map_or(0, |i| i + 1);
        args[..given]
            .iter()
            .map(|&a| match p.nodes.get(a) {
                // Null, which gives its parameter its default too.
                Node::Omitted {} => Ok(Expr::name("null")),
                _ => self.expr(a),
            })
            .collect()
    }

    pub(crate) fn expr(&mut self, id: NodeId) -> R<Expr> {
        let p = self.p;
        Ok(match p.nodes.get(id) {
            Node::BoolLit { value } => Expr::Bool(value),
            Node::Int32Lit { value } => Expr::Int(value.into()),
            Node::Int64Lit { value } => Expr::Long(value),
            Node::Float64Lit { value } => Expr::Float(value),
            Node::StringLit { value } => Expr::Str(p.strings.get(value).to_string()),
            Node::TypeValue { of } => Expr::name(match p.types.get(of) {
                TypeData::Scalar(s) => match s {
                    Scalar::Void => "typeof(void)",
                    Scalar::Boolean => "typeof(bool)",
                    Scalar::Int32 => "typeof(int)",
                    Scalar::Int64 => "typeof(long)",
                    Scalar::Float64 => "typeof(double)",
                    Scalar::String => "typeof(string)",
                    _ => return not_yet("this type as a value"),
                },
                _ => return not_yet("this type as a value"),
            }),
            Node::Local { local: decl } => Expr::name(self.name(decl)),
            Node::GetModuleLet { target } => self.top_name(target),
            Node::CallFunction {
                callee,
                type_args,
                args,
                ..
            } => {
                let args = p.nodes.list(args);
                if by_connection(p, callee, crate::connected::connects_std) {
                    self.call = id;
                    return self.connected(callee, args);
                }
                self.check_call(callee, id, args)?;
                let spelled = self.spelled_type_args(callee, type_args, args)?;
                let exprs = self.args(args)?;
                let exprs = self.optional_args(callee, args, exprs)?;
                // A generic function can't infer from a method group.
                let args = if p.decls.type_params(callee).is_empty() {
                    exprs
                } else {
                    self.cast_groups(args, exprs)?
                };
                let owner = p.decls.get(callee).owner;
                let static_member = p.decls.contains(owner)
                    && matches!(
                        p.decls.get(owner).kind,
                        DeclKind::Class | DeclKind::Interface
                    );
                let call = if static_member {
                    let name = format!("{}{spelled}", self.member_name(callee));
                    if self.in_type == Some(owner) {
                        Expr::name(name).call(args)
                    } else {
                        let ty = self.type_name(owner);
                        Expr::name(ty).method(name, args)
                    }
                } else {
                    let name = format!("{}{spelled}", self.name(callee));
                    match self.owner_class(callee) {
                        Some(class) => Expr::name(class).method(name, args),
                        None => Expr::name(name).call(args),
                    }
                };
                self.optional_result(callee, id, call)?
            }
            Node::CallMethod {
                method,
                type_args,
                receiver,
                args,
                ..
            } => {
                if !by_connection(p, method, crate::connected::connects_std) {
                    let args = p.nodes.list(args);
                    let spelled = self.spelled_type_args(method, type_args, args)?;
                    return self.call_method(id, method, &spelled, receiver, args);
                }
                let mut all = vec![receiver];
                all.extend_from_slice(p.nodes.list(args));
                self.unnamed.insert(receiver);
                self.call = id;
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
                // wraps (`-(-2147483648)` is itself); a negated null
                // check is `x != null`.
                if let [arg] = p.nodes.list(args) {
                    if let (Spelling::Unary(Unary::Not), Node::IsNull { value }) =
                        (spelling, p.nodes.get(*arg))
                    {
                        let e = self.expr(value)?;
                        if self.is_optional(p.nodes.ty(value)) {
                            return Ok(e.member("HasValue"));
                        }
                        return Ok(Expr::bin(Bin::Ne, e, Expr::name("null")));
                    }
                    match (spelling, p.nodes.get(*arg)) {
                        (Spelling::Unary(Unary::Neg), Node::Int32Lit { value }) => {
                            return Ok(Expr::Int(value.wrapping_neg().into()));
                        }
                        (Spelling::Unary(Unary::Neg), Node::Int64Lit { value }) => {
                            return Ok(Expr::Long(value.wrapping_neg()));
                        }
                        (Spelling::Unary(Unary::Neg), Node::Float64Lit { value }) => {
                            return Ok(Expr::Float(-value));
                        }
                        _ => {}
                    }
                }
                let args: Vec<Expr> = p
                    .nodes
                    .list(args)
                    .iter()
                    .map(|&a| self.expr(a))
                    .collect::<R<_>>()?;
                if let Some(class) = ops::class(spelling) {
                    // Imported, or qualified, once for the call.
                    let written = self.class(class);
                    let e = ops::apply(spelling, args, |_| written.clone());
                    return Ok(e);
                }
                let e = ops::apply(spelling, args, |c| Expr::name(c));
                match &e {
                    // C# checks constant arithmetic for overflow, where
                    // Temper's wraps.
                    Expr::Bin(op, ..) if op.arithmetic() && e.is_constant() => {
                        Expr::Unchecked(Box::new(e))
                    }
                    Expr::Unary(Unary::Neg, _) if e.is_constant() => Expr::Unchecked(Box::new(e)),
                    _ => e,
                }
            }
            Node::And { left, right } => Expr::bin(Bin::And, self.expr(left)?, self.expr(right)?),
            Node::Or { left, right } => Expr::bin(Bin::Or, self.expr(left)?, self.expr(right)?),
            Node::IfValue { cond, then, els } => Expr::Cond {
                cond: Box::new(self.expr(cond)?),
                then: Box::new(self.expr(then)?),
                els: Box::new(self.expr(els)?),
            },
            Node::Error { .. } => {
                let ty = self.ty(p.nodes.ty(id))?;
                self.class("Core")
                    .method(format!("Bubble<{ty}>"), Vec::new())
            }
            Node::This {} => Expr::name(self.this_name),
            Node::GetField { field, object } => {
                if !self.unnamed.contains(&id) {
                    self.nested_optionals(field, p.decls.get(field).ty, p.nodes.ty(id))?;
                }
                self.field(field, object)?
            }
            Node::GetStatic { target }
                if is_external(p, target) && library_of(p, target) == "core" =>
            {
                // `String.begin`, `Float64.pi`, and the like.
                self.call = id;
                self.connected(target, &[])?
            }
            Node::GetStatic { target } => {
                let owner = p.decls.get(target).owner;
                match self.static_storage.get(&target) {
                    Some(storage)
                        if self.in_type.is_none() && self.holds_tests(target) == self.in_tests =>
                    {
                        Expr::name(storage.clone())
                    }
                    _ => {
                        let name = self.member_name(target);
                        let ty = self.type_name(owner);
                        Expr::name(ty).member(name)
                    }
                }
            }
            Node::New { ctor, args, .. }
                if is_external(p, ctor) && library_of(p, ctor) == "core"
                    || by_connection(p, ctor, crate::connected::connects_std) =>
            {
                self.call = id;
                self.connected(ctor, p.nodes.list(args))?
            }
            // With its type arguments, as Kotlin's.
            Node::New { ctor, args, .. } => {
                self.check_call(ctor, id, p.nodes.list(args))?;
                let ty = self.ty(p.nodes.ty(id))?;
                let args = self.args(p.nodes.list(args))?;
                Expr::name(format!("new {ty}")).call(args)
            }
            // `Listed.CreateReadOnlyList<T>(…)`, as Kotlin's.
            Node::ListLit { items } => {
                let ty = p.nodes.ty(id);
                let item = match p.types.args(ty).first() {
                    Some(&t) => self.ty(t)?,
                    None => return not_yet("a list literal without an item type"),
                };
                let items = self.args(p.nodes.list(items))?;
                self.class("Listed")
                    .method(format!("CreateReadOnlyList<{item}>"), items)
            }
            // A builder as read-only, as Kotlin's.
            Node::Upcast { value }
                if matches!(
                    self.type_qname(p.nodes.ty(value)).as_str(),
                    "core.type ListBuilder" | "core.type MapBuilder"
                ) =>
            {
                let class = match self.type_qname(p.nodes.ty(value)).as_str() {
                    "core.type ListBuilder" => "Listed",
                    _ => "Mapped",
                };
                let v = self.expr(value)?;
                self.class(class).method("AsReadOnly", vec![v])
            }
            // C# converts to an interface by itself.
            Node::Upcast { value } => self.expr(value)?,
            // A failed cast throws, which is how C# bubbles.
            // A `T?` that's `Optional<T>`: its value, whose read throws
            // if it has none, cast on to another type, where boxing it
            // for `CastToNonNull` would cast the box.
            Node::As { value, .. } if self.is_optional(p.nodes.ty(value)) => {
                let ty = p.nodes.ty(id);
                let e = self.expr(value)?.member("Value");
                if ty == p.types.non_null(p.nodes.ty(value)) {
                    e
                } else {
                    Expr::Cast(self.ty(ty)?, Box::new(e))
                }
            }
            // A null cast to a class succeeds, so one from a nullable
            // type checks for null first, as the runtime's
            // `CastToNonNull` does, throwing if it's null.
            Node::As { value, .. } if p.types.is_nullable(p.nodes.ty(value)) => {
                let ty = self.ty(p.nodes.ty(id))?;
                let e = self.expr(value)?;
                self.class("Core")
                    .method(format!("CastToNonNull<{ty}>"), vec![e])
            }
            // A string index is an int: a downcast is the int itself, and
            // `as` checks it, as Kotlin's.
            Node::Downcast { value } if self.string_index_check(p.nodes.ty(id)).is_some() => {
                self.expr(value)?
            }
            Node::As { value, .. } if self.string_index_check(p.nodes.ty(id)).is_some() => {
                let method = match self.string_index_check(p.nodes.ty(id)) {
                    Some(Bin::Ge) => "RequireStringIndex",
                    _ => "RequireNoStringIndex",
                };
                let e = self.expr(value)?;
                self.class("StringUtil").method(method, vec![e])
            }
            Node::Downcast { value } | Node::As { value, .. } => {
                let ty = self.ty(p.nodes.ty(id))?;
                Expr::Cast(ty, Box::new(self.expr(value)?))
            }
            // `i >= 0` for a string index, `i < 0` for none, as Kotlin's.
            Node::Is { ty, value } if self.string_index_check(ty).is_some() => {
                let check = self.string_index_check(ty).unwrap_or(Bin::Ge);
                Expr::bin(check, self.expr(value)?, Expr::Int(0))
            }
            Node::Is { ty, value } => {
                let ty = self.ty(ty)?;
                Expr::Is(Box::new(self.expr(value)?), ty)
            }
            Node::Identical { left, right } => {
                let args = vec![self.expr(left)?, self.expr(right)?];
                Expr::name("object").method("ReferenceEquals", args)
            }
            // A `T?`'s null is `Optional<T>.None`, and its checks
            // `HasValue`, as Kotlin's.
            Node::Null {} if self.is_optional(p.nodes.ty(id)) => {
                Expr::name(format!("{}.None", self.ty(p.nodes.ty(id))?))
            }
            Node::Null {} => Expr::name("null"),
            Node::IsNull { value } if self.is_optional(p.nodes.ty(value)) => {
                Expr::Unary(Unary::Not, Box::new(self.expr(value)?.member("HasValue")))
            }
            Node::IsNull { value } => Expr::bin(Bin::Eq, self.expr(value)?, Expr::name("null")),
            // C# narrows a local or parameter of a reference type after a
            // null check, as Temper does; a value type's is `x.Value`, and
            // anything else `x!`.
            Node::NonNull { value } => {
                let e = self.expr(value)?;
                if self.is_value_type(p.nodes.ty(id)) || self.is_optional(p.nodes.ty(value)) {
                    e.member("Value")
                } else if matches!(p.nodes.get(value), Node::Local { .. })
                    || matches!(p.nodes.get(value),
                        Node::GetModuleLet { target } if !self.statics.contains(&target))
                {
                    e
                } else {
                    Expr::NotNull(Box::new(e))
                }
            }
            // C# converts to a nullable type by itself, but for a value
            // type's in a conditional, which C# 9 types only by its
            // place.
            Node::AsNullable { value } => {
                let e = self.expr(value)?;
                let ty = p.nodes.ty(id);
                let wraps = self.is_value_type(p.types.non_null(ty)) || self.is_optional(ty);
                if wraps && self.in_conditional(id) {
                    Expr::Cast(self.ty(ty)?, Box::new(e))
                } else {
                    e
                }
            }
            Node::EqNullable { op, left, right } => self.eq_nullable(op, left, right)?,
            // A parameter's default: `x ?? d`, C#'s own, which runs `d`
            // only if `x` is null.
            Node::Coalesce { left, right } => {
                let l = self.expr(left)?;
                Expr::bin(Bin::Coalesce, l, self.expr(right)?)
            }
            Node::OrElse { .. } | Node::BlockValue { .. } if self.hoist.contains(&id) => {
                self.hoisted(id)?
            }
            Node::OrElse { .. } | Node::BlockValue { .. } => {
                return not_yet("an orelse or a block's value that can't run first");
            }
            Node::Bubble { .. } => {
                let ty = p.nodes.ty(id);
                let core = self.class("Core");
                if ty == TypeId::VOID {
                    core.method("Bubble", Vec::new())
                } else {
                    let ty = self.ty(ty)?;
                    core.method(format!("Bubble<{ty}>"), Vec::new())
                }
            }
            Node::FunctionLiteral { decl, params, body } => {
                self.literal(id, decl, params, body, None)?
            }
            // `Task<T> promise = p; yield return Async.AwakeUpon(promise);`
            // before its statement, which reads `promise.Result`, as
            // Kotlin's: the runtime steps the generator again once the
            // task is done, and a broken one's `Result` throws.
            Node::Await { promise, .. } => {
                if !self.in_generator {
                    return not_yet("an await outside a generator");
                }
                if !self.hoist.contains(&id) {
                    return not_yet("an await after an effect in its statement");
                }
                let ty = self.ty(p.nodes.ty(promise))?;
                let value = self.expr(promise)?;
                let name = self.temporary("promise");
                self.pre.push(Stmt::Local {
                    ty,
                    name: name.clone(),
                    value: Some(value),
                });
                let awake =
                    (self.class("Async")).method("AwakeUpon", vec![Expr::name(name.clone())]);
                self.pre.push(Stmt::YieldReturn(awake));
                Expr::name(name).member("Result")
            }
            Node::FunctionRef { target } => self.function_ref(target)?,
            Node::CallValue { callee, args, .. } => {
                let f = self.expr(callee)?;
                f.call(self.args(p.nodes.list(args))?)
            }
            Node::ToFun { value } | Node::FromFun { value } => self.expr(value)?,
            other => return not_yet(format!("a {} expression", other.kind().name())),
        })
    }

    /// A coalesced value where it isn't null: `x.Value` of a value type.
    fn coalesced(&mut self, left: NodeId, ty: TypeId) -> R<Expr> {
        let e = self.expr(left)?;
        Ok(if !self.p.types.is_nullable(ty) && self.is_value_type(ty) {
            e.member("Value")
        } else {
            e
        })
    }

    /// Whether a type is a nullable type parameter's, `T?`, which is
    /// `Optional<T>`, `T` not class-bounded ([`Self::class_bounded`]).
    fn is_optional(&self, ty: TypeId) -> bool {
        matches!(self.p.types.get(ty), TypeData::Nullable(inner)
            if matches!(self.p.types.get(inner),
                TypeData::TypeParam { decl } if !self.class_bounded(decl)))
    }

    /// Whether a type parameter's bounds hold only of types C# writes as
    /// classes, so that its `T?` is C#'s own nullable reference: one of
    /// them is a class or interface of a library other than core, neither
    /// connected nor a `@fun` interface (a delegate). Only core declares
    /// the supertypes of its classes that C# writes as value types (`int`,
    /// `bool`, `double`), so another library's interface never holds of
    /// one, where core's may (`MapKey`, `Equatable`). C# structs that
    /// implement a Temper interface, which Temper never makes, are left
    /// out (`where T : class, IShown`).
    fn class_bounded(&self, param: DeclId) -> bool {
        let p = self.p;
        p.decls.contains(param)
            && (p.decls.supers(param).iter()).any(|&b| self.reference_bound(b).is_some())
    }

    /// A bound's declaration's kind, class or interface, if it holds only
    /// of C# classes ([`Self::class_bounded`]).
    fn reference_bound(&self, bound: TypeId) -> Option<DeclKind> {
        let p = self.p;
        let decl = nominal_decl(p, bound)?;
        let d = p.decls.get(decl);
        let core = is_external(p, decl) && library_of(p, decl) == "core";
        let host = d.facts.contains(Facts::CONNECTED)
            || connected::lookup_type(&self.qname(decl)).is_some();
        let delegate = d.facts.contains(Facts::FUN);
        let temper = matches!(d.kind, DeclKind::Class | DeclKind::Interface);
        (temper && !core && !host && !delegate).then_some(d.kind)
    }

    /// Not yet: a callee's `T?` inside a type argument or a function type,
    /// `T` unconstrained (`Pair<A, B?>`, `List<T?>`, `fn (T?): R`), where
    /// it's given a type other than such a `U?` (`declared` as the callee
    /// has it, `given` as the call does). C# writes the callee's as
    /// `Optional<T>`, and the caller's as `X?`, and nothing converts
    /// `KeyValuePair<int, Optional<int>>` to `KeyValuePair<int, int?>`,
    /// so C#'s compiler would fail (notes/kotlin-differences.md). A
    /// top-level `T?` is wrapped and unwrapped ([`Self::optional_args`]).
    fn nested_optionals(&self, callee: DeclId, declared: TypeId, given: TypeId) -> R<()> {
        let Some(t) = self.nested_optional(declared, given, false) else {
            return Ok(());
        };
        let p = self.p;
        let t = p.name(t);
        not_yet(format!(
            "`{t}?` inside a type argument or function type in `{}`'s type, given \
             another type there: C# writes an unconstrained `{t}?` as `Optional<{t}>`, \
             which the caller's type can't match; bound it by a class or interface, \
             or use a type without a nested `{t}?`",
            p.name(callee)
        ))
    }

    /// The type parameter of a `T?` that's `Optional<T>`, nested in
    /// `declared`, where `given` has another type in its place
    /// ([`Self::nested_optionals`]).
    fn nested_optional(&self, declared: TypeId, given: TypeId, nested: bool) -> Option<DeclId> {
        let types = &self.p.types;
        let pairs = |a: temper_tmpl::TypeList, b: temper_tmpl::TypeList| {
            let (a, b) = (types.list(a), types.list(b));
            (a.len() == b.len()).then(|| a.iter().copied().zip(b.iter().copied()))
        };
        match (types.get(declared), types.get(given)) {
            (TypeData::Never(d), _) => self.nested_optional(d, given, nested),
            (_, TypeData::Never(g)) => self.nested_optional(declared, g, nested),
            (TypeData::Nullable(inner), _) if nested && self.is_optional(declared) => {
                match types.get(inner) {
                    TypeData::TypeParam { decl } if !self.is_optional(given) => Some(decl),
                    _ => None,
                }
            }
            (TypeData::Nullable(d), TypeData::Nullable(g)) => self.nested_optional(d, g, nested),
            (TypeData::Nominal { decl: a, args: x }, TypeData::Nominal { decl: b, args: y })
                if a == b =>
            {
                pairs(x, y)?.find_map(|(d, g)| self.nested_optional(d, g, true))
            }
            (
                TypeData::Function {
                    params: dp,
                    result: dr,
                    ..
                },
                TypeData::Function {
                    params: gp,
                    result: gr,
                    ..
                },
            ) => (pairs(dp, gp)?.chain([(dr, gr)]))
                .find_map(|(d, g)| self.nested_optional(d, g, true)),
            _ => None,
        }
    }

    /// [`Self::nested_optionals`] for a call's arguments, and for its
    /// result unless C# never names its type.
    fn check_call(&self, callee: DeclId, call: NodeId, args: &[NodeId]) -> R<()> {
        let p = self.p;
        let Some(sig) = p.signature(callee) else {
            return Ok(());
        };
        let skip = sig.params.len().saturating_sub(args.len());
        for (&d, &a) in sig.params[skip..].iter().zip(args) {
            if !matches!(p.nodes.get(a), Node::Omitted {}) {
                self.nested_optionals(callee, d, p.nodes.ty(a))?;
            }
        }
        if !self.unnamed.contains(&call) {
            self.nested_optionals(callee, sig.result, p.nodes.ty(call))?;
        }
        Ok(())
    }

    /// Whether C# writes a type as a value type, whose nullable type is
    /// `Nullable<T>`.
    fn is_value_type(&self, ty: TypeId) -> bool {
        match self.p.types.get(ty) {
            TypeData::Scalar(s) => matches!(
                s,
                Scalar::Boolean | Scalar::Int32 | Scalar::Int64 | Scalar::Float64
            ),
            // String indices are ints.
            TypeData::Nominal { decl, .. } => {
                connected::lookup_type(&self.qname(decl)).is_some_and(|c| c.namespace.is_empty())
            }
            _ => false,
        }
    }

    /// Whether the program reads a module `let` anywhere.
    fn is_read(&mut self, target: DeclId) -> bool {
        let p = self.p;
        let read = self.read_lets.get_or_insert_with(|| {
            (0..p.nodes.len() as u32)
                .map(NodeId)
                .filter_map(|n| match p.nodes.get(n) {
                    Node::GetModuleLet { target } => Some(target),
                    _ => None,
                })
                .collect()
        });
        read.contains(&target)
    }

    /// For core's `StringIndex` or `NoStringIndex`, how an int is checked
    /// to be one: `>= 0` or `< 0`.
    fn string_index_check(&self, ty: TypeId) -> Option<Bin> {
        let TypeData::Nominal { decl, .. } = self.p.types.get(ty) else {
            return None;
        };
        match self.qname(decl).as_str() {
            "core.type StringIndex" => Some(Bin::Ge),
            "core.type NoStringIndex" => Some(Bin::Lt),
            _ => None,
        }
    }

    /// Whether a node is a branch of a conditional expression.
    fn in_conditional(&self, id: NodeId) -> bool {
        let p = self.p;
        (0..p.nodes.len() as u32).map(NodeId).any(|n| {
            matches!(p.nodes.get(n), Node::IfValue { then, els, .. } if then == id || els == id)
        })
    }

    /// `==` where either side may be null: C#'s own lifted `==` where the
    /// type's equality is `==`; otherwise checked for null first, each
    /// side read once.
    fn eq_nullable(&mut self, op: temper_tmpl::ops::Op, left: NodeId, right: NodeId) -> R<Expr> {
        let p = self.p;
        let Some(spelling) = ops::spelling(op) else {
            return not_yet("an operator without a spelling");
        };
        let (a, b) = (self.expr(left)?, self.expr(right)?);
        if spelling == Spelling::Bin(Bin::Eq) {
            return Ok(Expr::bin(Bin::Eq, a, b));
        }
        let simple = |n: NodeId| {
            matches!(
                p.nodes.get(n),
                Node::Local { .. } | Node::Null {} | Node::AsNullable { .. }
            )
        };
        if !simple(left) || !simple(right) {
            return not_yet("a nullable equality of values read twice");
        }
        let null = || Expr::name("null");
        let value = |e: Expr| match e {
            Expr::Name(ref n) if n == "null" => e,
            e => e.member("Value"),
        };
        let eq = match ops::class(spelling) {
            Some(class) => {
                let written = self.class(class);
                ops::apply(spelling, vec![value(a.clone()), value(b.clone())], |_| {
                    written.clone()
                })
            }
            None => ops::apply(spelling, vec![value(a.clone()), value(b.clone())], |c| {
                Expr::name(c)
            }),
        };
        Ok(Expr::Cond {
            cond: Box::new(Expr::bin(Bin::Eq, a, null())),
            then: Box::new(Expr::bin(Bin::Eq, b.clone(), null())),
            els: Box::new(Expr::bin(Bin::And, Expr::bin(Bin::Ne, b, null()), eq)),
        })
    }

    /// A `BlockValue` or `OrElse` that runs before its statement: a block's
    /// statements go first and its value stays in place; an `orelse` is a
    /// `try` that sets a fresh local, which takes its place.
    fn hoisted(&mut self, id: NodeId) -> R<Expr> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::BlockValue { stmts, value } => {
                let mut pre = Vec::new();
                for &s in p.nodes.list(stmts) {
                    // Two blocks' locals may share a name, apart, but not
                    // side by side.
                    if let Node::Let { local, .. } = p.nodes.get(s) {
                        let name = self.name(local);
                        let shared = (p.decls.ids())
                            .filter(|&d| d != local && p.decls.contains(d))
                            .any(|d| self.names.get(d) == name);
                        if shared {
                            let fresh = self.temporary(&name);
                            self.renamed.insert(local, fresh);
                        }
                    }
                    self.stmt(s, &mut pre)?;
                }
                self.pre.extend(pre);
                // Its value runs after its statements, and may hoist
                // statements of its own, as an `orelse` does.
                match p.nodes.get(value) {
                    Node::OrElse { .. } | Node::BlockValue { .. } => self.hoisted(value),
                    _ => {
                        let mut after = Vec::new();
                        let e = self.value_after(value, &mut after)?;
                        self.pre.extend(after);
                        Ok(e)
                    }
                }
            }
            _ => {
                let ty = self.ty(p.nodes.ty(id))?;
                let name = self.temporary("t");
                let mut pre = vec![Stmt::Local {
                    ty,
                    name: name.clone(),
                    value: None,
                }];
                self.sink(id, &Sink::Assign(Expr::name(name.clone())), &mut pre)?;
                self.pre.extend(pre);
                Ok(Expr::name(name))
            }
        }
    }

    /// A chain of `String.cat` as one string: literals merged; an
    /// `Int32.toString()` or `Boolean.toString()` without a radix
    /// converted in place, a number by the invariant culture, since C#'s
    /// `+` and `$"…"` would convert it by the current one. With two or
    /// more numbers, an interpolated string in the invariant culture,
    /// `string.Create(CultureInfo.InvariantCulture, $"…")`, reads better;
    /// otherwise `+`.
    fn cat(&mut self, id: NodeId) -> R<Expr> {
        let mut parts = Vec::new();
        self.cat_parts(id, &mut parts)?;
        parts.retain(|p| !matches!(p, Part::Lit(s) if s.is_empty()));
        if parts.iter().all(|p| matches!(p, Part::Lit(_))) {
            let text: String = parts
                .iter()
                .filter_map(|p| match p {
                    Part::Lit(s) => Some(s.as_str()),
                    _ => None,
                })
                .collect();
            return Ok(Expr::Str(text));
        }
        let numbers = parts
            .iter()
            .filter(|p| matches!(p, Part::Number(_)))
            .count();
        let boolean = |e: Expr| Expr::Cond {
            cond: Box::new(e),
            then: Box::new(Expr::Str("true".into())),
            els: Box::new(Expr::Str("false".into())),
        };
        if numbers >= 2 {
            let holes = parts
                .into_iter()
                .map(|part| match part {
                    Part::Lit(s) => Hole::Text(s),
                    Part::Str(e) | Part::Number(e) => Hole::Expr(e),
                    Part::Boolean(e) => Hole::Expr(boolean(e)),
                })
                .collect();
            let culture = self.class("CultureInfo").member("InvariantCulture");
            let text = Expr::Interpolated(holes);
            return Ok(Expr::name("string").method("Create", vec![culture, text]));
        }
        let mut out = Vec::new();
        for part in parts {
            out.push(match part {
                Part::Lit(s) => Expr::Str(s),
                Part::Str(e) => e,
                Part::Number(e) => self.invariant(e),
                Part::Boolean(e) => boolean(e),
            });
        }
        Ok(match <[Expr; 1]>::try_from(out) {
            Ok([one]) => one,
            Err(out) => Expr::Concat(out),
        })
    }

    fn cat_parts(&mut self, id: NodeId, parts: &mut Vec<Part>) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Op { op, args, .. } if ops::spelling(op) == Some(Spelling::Cat) => {
                for &a in p.nodes.list(args) {
                    self.cat_parts(a, parts)?;
                }
            }
            Node::StringLit { value } => {
                let text = p.strings.get(value);
                if let Some(Part::Lit(last)) = parts.last_mut() {
                    last.push_str(text);
                } else {
                    parts.push(Part::Lit(text.to_string()));
                }
            }
            Node::CallMethod {
                method,
                receiver,
                args,
                ..
            } if p
                .nodes
                .list(args)
                .iter()
                .all(|&a| matches!(p.nodes.get(a), Node::Omitted {})) =>
            {
                let qname = self.qname(method);
                let number = match qname.as_str() {
                    "core.type Int32.toString()" | "core.type Int64.toString()" => true,
                    "core.type Boolean.toString()" => false,
                    _ => {
                        let e = self.expr(id)?;
                        parts.push(Part::Str(e));
                        return Ok(());
                    }
                };
                let e = self.expr(receiver)?;
                parts.push(if number {
                    Part::Number(e)
                } else {
                    Part::Boolean(e)
                });
            }
            _ => {
                let e = self.expr(id)?;
                parts.push(Part::Str(e));
            }
        }
        Ok(())
    }
}

/// Module-level names that the naming pass numbered only because an
/// interface has their name, which in C# it doesn't, since it takes `I`
/// before it (a function `shape` beside an interface `Shape` is `Shape`,
/// not `Shape2`, as Kotlin's): by declaration, the name without the
/// number.
fn unclashed(p: &Program, names: &NameMap) -> HashMap<DeclId, String> {
    let mut out: HashMap<DeclId, String> = HashMap::new();
    for &d in names.suffixed() {
        if !matches!(
            p.decls.get(d).kind,
            DeclKind::Function | DeclKind::ModuleLet
        ) {
            continue;
        }
        let name = names.get(d);
        let base = name.trim_end_matches(|c: char| c.is_ascii_digit());
        if base == name || base.is_empty() || out.values().any(|n| n == base) {
            continue;
        }
        let holders: Vec<DeclId> = (p.decls.ids())
            .filter(|&o| o != d && p.decls.contains(o) && names.get(o) == base)
            .collect();
        let interfaces_only = !holders.is_empty()
            && (holders.iter()).all(|&o| p.decls.get(o).kind == DeclKind::Interface);
        if interfaces_only {
            out.insert(d, base.to_string());
        }
    }
    out
}

/// `return call;`, or for `void`, `call;`.
fn returning(result: &str, call: Expr) -> Stmt {
    if result == "void" {
        Stmt::Expr(call)
    } else {
        Stmt::Return(Some(call))
    }
}

/// Statements up to the first that can't complete normally, by C#'s rules
/// (ECMA-334 13.2), since C# warns of a statement after one, and the
/// statements after a bubble, which never return.
fn reachable(mut stmts: Vec<Stmt>) -> Vec<Stmt> {
    if let Some(i) = stmts.iter().position(|s| !completes(s)) {
        stmts.truncate(i + 1);
    }
    stmts
}

/// Whether a function literal's body is one `return` of a value, which a
/// lambda's expression gives.
fn single_value(p: &Program, body: NodeId) -> bool {
    match p.nodes.get(body) {
        Node::Block { stmts } => matches!(
            p.nodes.list(stmts),
            [only] if matches!(p.nodes.get(*only), Node::Return { value: Some(_) })
        ),
        _ => false,
    }
}

/// Whether a statement can complete normally, by C#'s rules.
fn completes(s: &Stmt) -> bool {
    match s {
        Stmt::Break
        | Stmt::Continue
        | Stmt::Goto(_)
        | Stmt::Return(_)
        | Stmt::YieldBreak
        | Stmt::Bubble(_)
        | Stmt::Throw(_)
        | Stmt::Expr(Expr::Throw(_)) => false,
        Stmt::TryCatch { body, catch } => body.iter().all(completes) || catch.iter().all(completes),
        // A label is reached by a `goto` from before it.
        Stmt::Block(body) if body.iter().any(|s| matches!(s, Stmt::Label(_))) => true,
        Stmt::Block(body) | Stmt::Lock { body, .. } => body.iter().all(completes),
        Stmt::Try { body, finally } => body.iter().all(completes) && finally.iter().all(completes),
        Stmt::If { then, els, .. } => {
            els.is_empty() || then.iter().all(completes) || els.iter().all(completes)
        }
        // A label after a loop is reached by a `goto` from inside it.
        Stmt::While { cond, body, labels } => {
            *cond != Expr::Bool(true) || breaks(body) || labels.end.is_some()
        }
        Stmt::DoWhile { body, cond, labels } => {
            breaks(body)
                || labels.end.is_some()
                || (*cond != Expr::Bool(true)
                    && (body.iter().all(completes) || continues(body) || labels.next.is_some()))
        }
        Stmt::Expr(_)
        | Stmt::Local { .. }
        | Stmt::Assign { .. }
        | Stmt::Raw(_)
        | Stmt::YieldReturn(_)
        | Stmt::Label(_)
        | Stmt::LocalFunction { .. } => true,
    }
}

/// Whether statements hold a `yield`, but in a local function's body,
/// which is its own.
pub(crate) fn yields(body: &[Stmt]) -> bool {
    body.iter().any(|s| match s {
        Stmt::YieldReturn(_) | Stmt::YieldBreak => true,
        Stmt::Block(b) | Stmt::Lock { body: b, .. } => yields(b),
        Stmt::Try { body, finally } => yields(body) || yields(finally),
        Stmt::TryCatch { body, catch } => yields(body) || yields(catch),
        Stmt::If { then, els, .. } => yields(then) || yields(els),
        Stmt::While { body, .. } | Stmt::DoWhile { body, .. } => yields(body),
        _ => false,
    })
}

/// A generator's statements, its `return`s `yield break`s, but in local
/// functions, whose `return`s are their own.
pub(crate) fn yield_breaks(body: &mut [Stmt]) {
    for s in body {
        match s {
            Stmt::Return(None) => *s = Stmt::YieldBreak,
            Stmt::Block(b) | Stmt::Lock { body: b, .. } => yield_breaks(b),
            Stmt::Try { body, finally } => {
                yield_breaks(body);
                yield_breaks(finally);
            }
            Stmt::TryCatch { body, catch } => {
                yield_breaks(body);
                yield_breaks(catch);
            }
            Stmt::If { then, els, .. } => {
                yield_breaks(then);
                yield_breaks(els);
            }
            Stmt::While { body, .. } | Stmt::DoWhile { body, .. } => yield_breaks(body),
            _ => {}
        }
    }
}

/// Whether statements hold a `break` of the innermost loop.
fn breaks(body: &[Stmt]) -> bool {
    jumps(body, true)
}

fn continues(body: &[Stmt]) -> bool {
    jumps(body, false)
}

fn jumps(body: &[Stmt], brk: bool) -> bool {
    body.iter().any(|s| match s {
        Stmt::Break => brk,
        Stmt::Continue => !brk,
        Stmt::Block(b) | Stmt::Lock { body: b, .. } | Stmt::Try { body: b, .. } => jumps(b, brk),
        Stmt::TryCatch { body, catch } => jumps(body, brk) || jumps(catch, brk),
        Stmt::If { then, els, .. } => jumps(then, brk) || jumps(els, brk),
        _ => false,
    })
}

/// A library's root namespace: std's is `TemperLang.Std`, as Kotlin's;
/// another's is its config's `csharpRootNamespace`, or else
/// [`crate::names::root_namespace`]'s.
pub fn library_namespace(p: &Program, library: &str) -> String {
    match (library, p.metadata_of(library).get("csharpRootNamespace")) {
        ("std", _) => "TemperLang.Std".into(),
        (_, Some(namespace)) => namespace.into(),
        _ => crate::names::root_namespace(library),
    }
}

/// A module's namespace: its library's root namespace, then each part of
/// its path in Pascal case, as Kotlin's (`Modules.Helpers.Deep`).
pub fn module_namespace(p: &Program, module: DeclId) -> String {
    let d = p.decls.get(module);
    let (root, path) = match d.kind {
        DeclKind::Library => (library_namespace(p, p.name(module)), ""),
        _ if !d.owner.is_none() => (library_namespace(p, p.name(d.owner)), p.name(module)),
        _ => {
            let path = (p.modules.iter())
                .find(|m| m.decl == module)
                .map_or("", |m| m.path.as_str());
            (library_namespace(p, &p.library.name), path)
        }
    };
    let mut out = root;
    for part in path.split('/').filter(|s| !s.is_empty()) {
        out.push('.');
        out.push_str(&crate::names::root_namespace(part));
    }
    out
}

/// A module's namespace and the start of its classes' names: its
/// namespace's last part (`Deep` for `Modules.Helpers.Deep`).
pub fn module_names(p: &Program, module: DeclId) -> (String, String) {
    let namespace = module_namespace(p, module);
    let base = namespace
        .rsplit('.')
        .next()
        .unwrap_or(&namespace)
        .to_string();
    (namespace, base)
}

/// A module's namespace and its statics' class, `NAMEGlobal`.
pub fn global_class(p: &Program, module: DeclId) -> (String, String) {
    let (namespace, base) = module_names(p, module);
    (namespace, format!("{base}Global"))
}

/// A test's method name: its name's words in Pascal case, letters and
/// digits only, numbered past those already used.
fn test_method_name(text: &str, used: &mut BTreeSet<String>) -> String {
    let mut name = String::new();
    for word in text
        .split(|c: char| !c.is_alphanumeric())
        .filter(|w| !w.is_empty())
    {
        let mut chars = word.chars();
        name.extend(chars.next().unwrap_or_default().to_uppercase());
        name.push_str(chars.as_str());
    }
    if !name.starts_with(char::is_alphabetic) {
        name.insert_str(0, "Test");
    }
    let mut unique = name.clone();
    let mut n = 2;
    while !used.insert(unique.clone()) {
        unique = format!("{name}{n}");
        n += 1;
    }
    unique
}

/// For a `let`'s initializer that coalesces a parameter with null, as a
/// default of null does, the parameter.
fn null_default(p: &Program, init: NodeId) -> Option<DeclId> {
    let Node::Coalesce { left, right } = p.nodes.get(init) else {
        return None;
    };
    match (p.nodes.get(left), p.nodes.get(right)) {
        (Node::Local { local }, Node::Null {}) if p.decls.get(local).kind == DeclKind::Param => {
            Some(local)
        }
        _ => None,
    }
}

/// Whether a value needs statements: a block's value or an `orelse`.
fn needs_stmts(p: &Program, id: NodeId) -> bool {
    let mut found = false;
    temper_tmpl::analysis::walk(p, id, &mut |n| {
        found |= matches!(
            p.nodes.get(n),
            Node::OrElse { .. } | Node::BlockValue { .. }
        );
    });
    found
}

/// Whether a node is a literal, which has no effects.
fn is_literal(p: &Program, id: NodeId) -> bool {
    matches!(
        p.nodes.get(id),
        Node::BoolLit { .. }
            | Node::Int32Lit { .. }
            | Node::Int64Lit { .. }
            | Node::Float64Lit { .. }
            | Node::StringLit { .. }
    )
}

/// The locals and module `var`s assigned inside a `BlockValue` or an
/// `OrElse`, as `n` in `"${n++}"`'s.
fn block_writes(p: &Program) -> HashSet<DeclId> {
    let mut out = HashSet::new();
    for i in 0..p.nodes.len() {
        let id = NodeId(i as u32);
        if !matches!(
            p.nodes.get(id),
            Node::BlockValue { .. } | Node::OrElse { .. }
        ) {
            continue;
        }
        temper_tmpl::analysis::walk(p, id, &mut |n| match p.nodes.get(n) {
            Node::Assign { local: d, .. } | Node::SetModuleLet { target: d, .. } => {
                out.insert(d);
            }
            _ => {}
        });
    }
    out
}

/// An `orelse` that awaits, as [`Translator::try_around_yields`] splits
/// it: the `catch` label, the locals declared first, the statements so
/// far, and the run of statements not yet in a `try`.
struct Split {
    catch: String,
    decls: Vec<Stmt>,
    out: Vec<Stmt>,
    run: Vec<Stmt>,
}

impl Split {
    /// The run so far, as a `try` whose `catch` jumps to the fallback.
    fn flush(&mut self) {
        if !self.run.is_empty() {
            self.out.push(Stmt::TryCatch {
                body: std::mem::take(&mut self.run),
                catch: vec![Stmt::Goto(self.catch.clone())],
            });
        }
    }
}

/// A value without the conversions around it that check nothing at run
/// time (`NonNull` is unchecked; C#'s `e!` only quiets its compiler),
/// for a value that's dropped.
fn unconverted(p: &Program, mut id: NodeId) -> NodeId {
    while let Node::NonNull { value } | Node::AsNullable { value } | Node::Upcast { value } =
        p.nodes.get(id)
    {
        id = value;
    }
    id
}

/// Whether a value, dropped, needn't be written at all: literals, reads
/// of locals, module `let`s, and fields, operators that can't fail, and
/// functions made but not called, which do nothing a statement would
/// show. Anything else, dropped, is `_ = e;`.
fn no_effects(p: &Program, id: NodeId) -> bool {
    let here = match p.nodes.get(id) {
        Node::FunctionLiteral { .. } | Node::FunctionRef { .. } => return true,
        Node::Op { may_bubble, .. } => !may_bubble,
        Node::BoolLit { .. }
        | Node::Int32Lit { .. }
        | Node::Int64Lit { .. }
        | Node::Float64Lit { .. }
        | Node::StringLit { .. }
        | Node::VoidLit {}
        | Node::TypeValue { .. }
        | Node::This {}
        | Node::Null {}
        | Node::Omitted {}
        | Node::Local { .. }
        | Node::GetModuleLet { .. }
        | Node::GetField { .. }
        | Node::IsNull { .. }
        | Node::NonNull { .. }
        | Node::AsNullable { .. }
        | Node::Upcast { .. }
        | Node::EqNullable { .. }
        | Node::Is { .. }
        | Node::Identical { .. }
        | Node::And { .. }
        | Node::Or { .. }
        | Node::IfValue { .. } => true,
        _ => false,
    };
    here && p.nodes.kids(id).iter().all(|&k| no_effects(p, k))
}
