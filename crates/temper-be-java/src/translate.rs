//! The translation from tmpl to Java's output tree, classes and
//! interfaces in [`classes`].
//!
//! A module is one final class of statics, `NAMEGlobal`, as Kotlin's:
//! its functions are static methods, and its init block is the class's
//! static initializer, which the JVM runs once, on one thread, when the
//! class is first used. Module `let`s and `var`s that functions use, or
//! that are exported, are static fields, `final` for a `let`, declared
//! without a value and assigned by the static initializer, as Kotlin's
//! are; the rest are locals of the static initializer. A `var` that a
//! function writes is `volatile`, so every thread reads its latest
//! value, and a function's update of one from itself alone, through
//! operators (`ticks = ticks + 1`), takes the class's lock, so updates
//! from several threads aren't lost; Kotlin's are plain statics.
//!
//! Another module's functions and module `let`s are its class's statics,
//! `HelpersGlobal.twice(n)`, with the class imported (or written in full
//! where its simple name is taken); another library's the same, in its
//! package. A module's tests are a JUnit 5 class of their own, in its
//! package, with what only they reach ([`Translator::tests`]).
//!
//! Nullable types are Kotlin's: `@Nullable` before the type, a scalar's
//! box (`@Nullable Integer`). Java boxes and unboxes as it needs to, but
//! where that would go wrong: `==` on two values known not to be null
//! unboxes one (two `Integer`s compare by reference, so `1000 == 1000`
//! would be false, as in Kotlin's output for two `List<Int>` elements),
//! `==` where either may be null is
//! `Objects.equals`, and `c ? x : 1` for an `Integer` `x` boxes the `1`
//! (else Java unboxes `x`). `x as T` for `x: T?` is
//! `Core.requireNonNull(x)`, which bubbles for null, and `x as C` is
//! `Core.cast(C.class, x)` (`Translator::checked_cast`).
//!
//! What an expression needs first, an `orelse`'s `try` or a block's
//! statements, goes before the statement around it ([`Translator::emit`]),
//! with what an operand before it computes put in a temporary first, so
//! it's still computed first; `&&`, `||`, and `?:` whose later operands
//! need statements become `if` statements, and a `while` condition that
//! does a `while (true)` with a test first. Where the statement puts the
//! value somewhere, the `try` puts it there itself, as Kotlin's do:
//! `String converted; try { converted = …; } catch (Bubble ignored) {
//! converted = "failed"; }`. A bubble is the runtime's `Bubble`;
//! `panic()` throws an `AssertionError`, which no `orelse` catches, where
//! Kotlin's throws a bubble; `orelse panic()`'s has the bubble as its
//! cause.

mod classes;
mod functions;
mod groups;

use crate::NotYet;
use crate::connected::{self, Connection};
use crate::names::KEYWORDS;
use crate::ops::{self, Spelling};
use crate::tree::{self, Bin, Expr, Stmt, Unary};
use classes::is_type;
use std::collections::{BTreeSet, HashMap, HashSet};
use temper_tmpl::analysis::{
    self, TestParts, home, module_refs, module_state, module_writes, reads_only_itself, walk,
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

pub struct Translator<'a> {
    p: &'a Program,
    names: &'a NameMap,
    qnames: QNames,
    /// The module's package and class.
    package: String,
    class: String,
    imports: BTreeSet<String>,
    /// The module translated, or whose test parts and tests are.
    here: DeclId,
    /// What its test parts define and use of its library's
    /// ([`analysis::test_parts`]), and the class they're in, its test
    /// class.
    test_parts: TestParts,
    test_class: String,
    /// Whether translating its test parts, in its test class or test
    /// sources' types.
    in_tests: bool,
    /// How many functional interfaces the library's parts synthesized,
    /// before its test parts' ([`Translator::synthesized`]).
    library_sams: usize,
    /// Module `let`s and `var`s kept as static fields.
    statics: HashSet<DeclId>,
    /// Whether translating a function, not the static initializer.
    in_function: bool,
    /// The enclosing loops, innermost last.
    loops: Vec<DeclId>,
    /// Labels printed, by loop: only those a jump from an inner loop
    /// needs.
    labels: HashMap<DeclId, String>,
    /// The name of the string comparison helper
    /// ([`ops::COMPARE_CODE_POINTS`]), and whether a call needs it.
    compare_helper: String,
    compares_strings: bool,
    /// Whether a class's static block calls the module class's
    /// `initStatics()`, which it then defines.
    init_statics: bool,
    /// Whether translating the module's own class, `NAMEGlobal`, where
    /// its statics need no class before them.
    in_global: bool,
    /// The class or interface translated, or `NONE`.
    this_class: DeclId,
    /// Static fields whose values their class's own initializer gives
    /// ([`Translator::plan_statics`]), with those values, by class.
    class_statics: HashMap<DeclId, Vec<(DeclId, NodeId)>>,
    /// Every member's node, by its declaration.
    member_nodes: HashMap<DeclId, NodeId>,
    /// The type a core constructor being translated makes, for its
    /// connection: a `Deque`'s items.
    made: TypeId,
    /// Statements that an expression being translated needs first, as
    /// an `orelse`'s `try` or a block's statements: the statement around
    /// it comes after them ([`Translator::emit`]).
    pre: Vec<Stmt>,
    /// The groups of local functions whose methods are being translated,
    /// innermost last: each class's name and functions ([`groups`]).
    groups: Vec<(String, Vec<DeclId>)>,
    /// Values computed into temporaries ahead of a later operand's
    /// statements, so they're still computed first, by their nodes.
    spilled: HashMap<NodeId, Expr>,
    /// The names of the function's (or init block's) locals and
    /// parameters, and the module's statics, with how many locals have
    /// each, which temporaries' names avoid.
    taken: HashMap<String, usize>,
    /// Locals that are their parameters, as a null default makes them,
    /// read by the parameters' names.
    same: HashMap<DeclId, DeclId>,
    /// The functional interfaces the module synthesizes: each by its
    /// signature, with its name and its file's text ([`functions`]).
    sams: Vec<(TypeId, String, String)>,
    /// Locals that lambdas capture and that are assigned, which are
    /// cells.
    cells: HashSet<DeclId>,
    /// Locals that lambdas capture, which Java needs final.
    captured: HashSet<DeclId>,
}

/// Where a value goes: into a target, out of the function, into a new
/// local, or nowhere, as a statement's.
enum Place {
    Assign(Expr),
    Return,
    Declare(String, String),
    Discard,
}

/// A part of a string concatenation, before it's written.
enum Part {
    Lit(String),
    /// A string-typed expression.
    Str(Expr),
    /// An `Int32` or `Boolean` that `Integer.toString` or
    /// `Boolean.toString` converts, by its class, which a concatenation
    /// converts the same way once it's concatenating.
    Converted(&'static str, Expr),
}

impl<'a> Translator<'a> {
    pub fn new(p: &'a Program, names: &'a NameMap, package: &str, class: &str) -> Translator<'a> {
        Translator {
            p,
            names,
            qnames: QNames::derive(p),
            package: package.to_string(),
            class: class.to_string(),
            imports: BTreeSet::new(),
            here: DeclId::NONE,
            test_parts: TestParts::default(),
            test_class: String::new(),
            in_tests: false,
            library_sams: 0,
            statics: HashSet::new(),
            in_function: false,
            loops: Vec::new(),
            labels: HashMap::new(),
            compare_helper: ops::COMPARE_CODE_POINTS.to_string(),
            compares_strings: false,
            init_statics: false,
            in_global: true,
            this_class: DeclId::NONE,
            class_statics: HashMap::new(),
            member_nodes: HashMap::new(),
            made: TypeId::NONE,
            pre: Vec::new(),
            groups: Vec::new(),
            spilled: HashMap::new(),
            taken: HashMap::new(),
            same: HashMap::new(),
            sams: Vec::new(),
            cells: temper_tmpl::analysis::assigned_captures(p),
            captured: (p.decls.ids())
                .filter(|&d| p.decls.contains(d) && p.decls.get(d).kind == DeclKind::Lambda)
                .flat_map(|d| p.decls.captures(d).to_vec())
                .collect(),
        }
    }

    pub(crate) fn node(&self, id: NodeId) -> Node {
        self.p.nodes.get(id)
    }

    /// A node's type.
    pub(crate) fn node_ty(&self, id: NodeId) -> TypeId {
        self.p.nodes.ty(id)
    }

    /// The program being translated.
    pub(crate) fn program(&self) -> &Program {
        self.p
    }

    /// The module's package, which names its logger.
    pub(crate) fn package(&self) -> &str {
        &self.package
    }

    /// Imports a class, by its qualified name.
    /// Whether a list's items may be null.
    pub(crate) fn items_may_be_null(&self, list: NodeId) -> bool {
        let p = self.p;
        (p.types.args(p.nodes.ty(list)).first()).is_some_and(|&t| may_be_null(p, t))
    }

    /// Whether an item of the generic type a core constructor makes may
    /// be null.
    pub(crate) fn result_items_may_be_null(&self) -> bool {
        let p = self.p;
        (p.types.args(self.made).first()).is_some_and(|&t| may_be_null(p, t))
    }

    pub(crate) fn import(&mut self, name: &str) {
        if !name.is_empty() {
            self.imports.insert(name.to_string());
        }
    }

    fn name(&self, decl: DeclId) -> String {
        let decl = self.same.get(&decl).copied().unwrap_or(decl);
        self.names.get(decl).to_string()
    }

    fn qname(&self, decl: DeclId) -> String {
        self.qnames
            .get(decl)
            .map(|q| q.to_string())
            .unwrap_or_default()
    }

    /// A type as Java writes it, imported if it needs to be.
    fn ty(&mut self, ty: TypeId) -> R<String> {
        Ok(match self.p.types.get(ty) {
            // `temper_tmpl::nevers` made each `Never<T>` the `T` it stands
            // for, as this profile has no bottom type.
            TypeData::Never(t) => return self.ty(t),
            // Only a signature's result, never a value's (tmpl's T5).
            TypeData::Result { .. } => return not_yet("a result type as a value's"),
            TypeData::Scalar(s) => match s {
                Scalar::Void => "void",
                Scalar::Boolean => "boolean",
                Scalar::Int32 => "int",
                Scalar::Int64 => "long",
                Scalar::Float64 => "double",
                Scalar::String => "String",
                Scalar::AnyValue => "Object",
                // Not raw `Class`, which `-Xlint` warns of.
                Scalar::Type => "Class<?>",
                Scalar::Bubble => return not_yet("Bubble as a value's type"),
            }
            .to_string(),
            // A `@fun` interface is its function type's interface.
            TypeData::Nominal { decl, .. } if self.p.decls.get(decl).facts.contains(Facts::FUN) => {
                self.sam(ty)?.ty
            }
            TypeData::Nominal { decl, args } => {
                let qname = self.qname(decl);
                let mut texts = Vec::new();
                for &a in self.p.types.list(args) {
                    texts.push(self.type_arg(a)?);
                }
                // `Empty` in a generator's or its result's arguments is
                // `Optional<? super Object>`, as Kotlin's, which
                // `Core.runAsync` takes.
                if matches!(
                    qname.as_str(),
                    "core.type Generator"
                        | "core.type SafeGenerator"
                        | "core.type GeneratorResult"
                        | "core.type ValueResult"
                        | "core.type DoneResult"
                ) {
                    for t in &mut texts {
                        if t == "Optional<?>" {
                            *t = "Optional<? super Object>".into();
                        }
                    }
                }
                let name = match connected::lookup_type(&qname) {
                    Some(c) => {
                        self.import(c.import);
                        c.name.to_string()
                    }
                    None if !is_external(self.p, decl) || library_of(self.p, decl) != "core" => {
                        self.class_name(decl)
                    }
                    None => return not_yet(format!("a type for {qname}")),
                };
                if texts.is_empty() {
                    name
                } else {
                    format!("{name}<{}>", texts.join(", "))
                }
            }
            TypeData::Function { .. } => self.sam(ty)?.ty,
            // As Kotlin's: a primitive's box, annotated.
            TypeData::Nullable(inner) => {
                let inner = match self.p.types.get(inner) {
                    TypeData::Scalar(s) => match s {
                        Scalar::Boolean => "Boolean".to_string(),
                        Scalar::Int32 => "Integer".to_string(),
                        Scalar::Int64 => "Long".to_string(),
                        Scalar::Float64 => "Double".to_string(),
                        _ => self.ty(inner)?,
                    },
                    // A string index's `int`, boxed too.
                    _ => match self.ty(inner)?.as_str() {
                        "int" => "Integer".to_string(),
                        other => other.to_string(),
                    },
                };
                self.import("temper.core.Nullable");
                format!("@Nullable {inner}")
            }
            TypeData::TypeParam { decl } => self.p.name(decl).to_string(),
        })
    }

    /// A type as a type argument: a primitive boxed, as `List<Integer>`.
    pub(crate) fn type_arg(&mut self, ty: TypeId) -> R<String> {
        match boxed(self.p, ty) {
            Some((class, _)) => Ok(class.to_string()),
            None => self.ty(ty),
        }
    }

    /// A generic declaration's type parameters, `<T>` or `<T extends
    /// Named>`, with a space after, or nothing. `MapKey`, which Java's
    /// maps need nothing for, isn't a bound.
    pub(crate) fn type_params(&mut self, decl: DeclId) -> R<String> {
        let p = self.p;
        let params = p.decls.type_params(decl).to_vec();
        if params.is_empty() {
            return Ok(String::new());
        }
        let mut out = Vec::new();
        for t in params {
            let mut bounds = Vec::new();
            for &b in p.decls.supers(t) {
                let key = temper_tmpl::analysis::nominal_decl(p, b)
                    .is_some_and(|d| self.qname(d) == "core.type MapKey");
                if !key {
                    bounds.push(self.ty(b)?);
                }
            }
            out.push(match bounds.as_slice() {
                [] => p.name(t).to_string(),
                _ => format!("{} extends {}", p.name(t), bounds.join(" & ")),
            });
        }
        Ok(format!("<{}> ", out.join(", ")))
    }

    /// A class or interface of the program's own or another library's
    /// (not core's), as the code writes it: in its module's package.
    fn class_name(&mut self, decl: DeclId) -> String {
        let from = home(self.p, decl).unwrap_or(DeclId::NONE);
        let package = module_package(self.p, from);
        let name = self.name(decl);
        self.class_ref(&format!("{package}.{name}"))
    }

    /// A class by its qualified name, as the code writes it: by its
    /// simple name in its own package, else imported, unless that's the
    /// class's own or another imported class's, when it's written in
    /// full.
    fn class_ref(&mut self, qualified: &str) -> String {
        let simple = qualified.rsplit('.').next().unwrap_or(qualified);
        let same_package = qualified.strip_suffix(simple) == Some(&format!("{}.", self.package));
        if same_package {
            return simple.to_string();
        }
        let clash = simple == self.class
            || (self.imports.iter())
                .any(|i| i != qualified && i.rsplit('.').next() == Some(simple));
        if clash {
            qualified.to_string()
        } else {
            self.import(qualified);
            simple.to_string()
        }
    }

    /// A module-level declaration, as the code reaches it: through the
    /// class of statics it's in, its module's, or for what only tests
    /// reach, its test class; but by its name alone in that class.
    fn owner_class(&mut self, decl: DeclId) -> Option<String> {
        let from = home(self.p, decl)?;
        let (package, class) = match self.test_parts.own.contains(&decl) {
            true => (module_package(self.p, from), self.test_class.clone()),
            false => global_class(self.p, from),
        };
        if self.in_global && package == self.package && class == self.class {
            return None;
        }
        Some(self.class_ref(&format!("{package}.{class}")))
    }

    fn exported(&self, decl: DeclId) -> bool {
        self.p.decls.get(decl).facts.contains(Facts::EXPORTED)
    }

    /// Translates a module's library parts into its class, `NAMEGlobal`.
    /// What only tests reach is its test class's ([`Translator::tests`]),
    /// as tmpl's `reach::partition` splits the module.
    pub fn module(&mut self, module: &Module) -> R<tree::Class> {
        let p = self.p;
        self.here = module.decl;
        self.test_parts = analysis::test_parts(p, module);
        self.test_class = format!("{}Test", module_names(p, module.decl).1);
        self.in_tests = false;
        // Classes' and interfaces' uses of module values, and the test
        // parts', which are in other classes.
        let mut outside: HashSet<DeclId> = self.test_parts.used.iter().copied().collect();
        for &item in &module.items {
            if let Node::Class { .. } | Node::Interface { .. } = p.nodes.get(item) {
                outside.extend(module_refs(p, item));
            }
        }
        self.plan_statics(module);
        self.statics_class(module, &module.items, Some(module.init), &outside)
    }

    /// A class of statics, of a module's library parts or its test parts
    /// (`items`, and `init`, its static initializer's): its functions,
    /// as static methods, and its module values that functions, other
    /// classes (those of `outside`), or exports use, as static fields; the
    /// rest are its initializer's locals.
    fn statics_class(
        &mut self,
        module: &Module,
        items: &[NodeId],
        init: Option<NodeId>,
        outside: &HashSet<DeclId>,
    ) -> R<tree::Class> {
        let p = self.p;
        let state = module_state(p, module);
        let mut written = HashSet::new();
        for item in module.roots() {
            if let Node::Function { .. }
            | Node::Test { .. }
            | Node::Class { .. }
            | Node::Interface { .. } = p.nodes.get(item)
            {
                written.extend(module_writes(p, item));
            }
        }
        // Classes whose static fields this initializer assigns, whose
        // static blocks call `initStatics()`; and a test class's
        // initializer, which calls its module class's first.
        self.init_statics = (items.iter()).any(|&item| match p.nodes.get(item) {
            Node::Class { decl, .. } => (classes::members(p, decl).into_iter()).any(|(f, n)| {
                matches!(p.nodes.get(n), Node::StaticField { .. }) && !self.in_class(f)
            }),
            _ => false,
        }) || (!self.in_tests && module.test_init.is_some());
        // A `let` whose initializer assigns it in a `try` and its `catch`
        // can't be `final`, by Java's rules.
        let mut retried = HashSet::new();
        let stmts = match init.map(|i| (i, p.nodes.get(i))) {
            Some((_, Node::Block { stmts })) => p.nodes.list(stmts).to_vec(),
            Some((i, _)) => vec![i],
            None => Vec::new(),
        };
        for &s in &stmts {
            if let Node::InitLet { target, value } = p.nodes.get(s) {
                if multi_assign(p, value) {
                    retried.insert(target);
                }
            }
        }
        let mut fields = Vec::new();
        let mut init_locals = Vec::new();
        for &item in items {
            if let Node::ModuleLet { decl } = p.nodes.get(item) {
                let d = p.decls.get(decl);
                let var = d.facts.contains(Facts::VAR);
                if self.exported(decl)
                    || state.used_by_functions.contains(&decl)
                    || outside.contains(&decl)
                {
                    self.statics.insert(decl);
                    fields.push(tree::Field {
                        public: self.exported(decl),
                        is_final: !var
                            && state.initialized_at_top.contains(&decl)
                            && !retried.contains(&decl),
                        volatile: var && written.contains(&decl),
                        ty: self.ty(d.ty)?,
                        name: self.name(decl),
                    });
                } else if !state.initialized_at_top.contains(&decl) {
                    // Assigned only inside the initializer's blocks, so
                    // declared first.
                    init_locals.push(Stmt::Local {
                        ty: self.ty(d.ty)?,
                        name: self.name(decl),
                        value: None,
                    });
                }
            }
        }
        // The helper takes a name none of the class's functions has.
        let functions: HashSet<String> = (items.iter())
            .filter_map(|&item| match p.nodes.get(item) {
                Node::Function { decl, .. } | Node::BodilessFunction { decl, .. } => {
                    Some(self.name(decl))
                }
                _ => None,
            })
            .collect();
        self.compares_strings = false;
        self.compare_helper = (1..)
            .map(|n| match n {
                1 => ops::COMPARE_CODE_POINTS.to_string(),
                n => format!("{}{n}", ops::COMPARE_CODE_POINTS),
            })
            .find(|n| !functions.contains(n))
            .unwrap_or_default();
        let mut methods = Vec::new();
        for &item in items {
            match p.nodes.get(item) {
                // std's, which the connection table has (`stdNetSend`).
                Node::BodilessFunction { decl, .. }
                    if temper_tmpl::qname::by_connection(
                        p,
                        decl,
                        crate::connected::connects_std,
                    ) => {}
                Node::Function { params, .. } | Node::BodilessFunction { params, .. } => {
                    let method = self.function(item)?;
                    let k = self.trailing_optional(params);
                    methods.extend(overloads(&method, k));
                    methods.insert(methods.len() - k, method);
                }
                _ => {}
            }
        }
        self.in_function = false;
        let mut body = init_locals;
        // A test class's initializer continues its module class's, so
        // runs it first.
        if self.in_tests && init.is_some() {
            let (package, class) = global_class(p, module.decl);
            let class = self.class_ref(&format!("{package}.{class}"));
            body.push(Stmt::Expr(
                Expr::name(class).method("initStatics", Vec::new()),
            ));
        }
        if let Some(init) = init {
            self.begin(init);
            for &s in &stmts {
                match p.nodes.get(s) {
                    Node::InitLet { target, value } if !self.statics.contains(&target) => {
                        let ty = self.ty(p.decls.get(target).ty)?;
                        let into = Place::Declare(ty, self.name(target));
                        self.into(&into, value, &mut body)?;
                    }
                    _ => self.stmt(s, &mut body)?,
                }
            }
            // A static initializer must be able to complete normally, by
            // javac's rules, so what it runs from the first statement that
            // can't, a failure or a panic, however nested
            // (`semantics/broken`), goes in `if (true) { … }`, which javac
            // takes as completing, and its `static final`s as assigned
            // where they are in the block.
            if let Some(i) = body.iter().position(|s| !completes(s)) {
                let rest = reachable(body.split_off(i));
                body.push(Stmt::If {
                    cond: Expr::Bool(true),
                    then: rest,
                    els: Vec::new(),
                });
            }
        }
        Ok(tree::Class {
            package: self.package.clone(),
            imports: self.imports.iter().cloned().collect(),
            name: self.class.clone(),
            fields,
            methods,
            helpers: {
                let mut helpers = Vec::new();
                if self.compares_strings {
                    helpers.push(ops::compare_code_points(&self.compare_helper));
                }
                // Called by classes' static blocks, and the test class's,
                // loading this class, whose static initializer runs first.
                if self.init_statics {
                    helpers.push("    static void initStatics() {}\n".to_string());
                }
                helpers
            },
            init: reachable(body),
        })
    }

    fn function(&mut self, item: NodeId) -> R<tree::Method> {
        let p = self.p;
        let (decl, params, body) = match p.nodes.get(item) {
            Node::Function { decl, params, body } => (decl, params, Some(body)),
            Node::BodilessFunction { decl, params } => (decl, params, None),
            _ => return not_yet("a function that isn't one"),
        };
        let mut ps = Vec::new();
        for &param in p.nodes.list(params) {
            if let Node::Param { decl } = p.nodes.get(param) {
                ps.push((self.ty(p.decls.get(decl).ty)?, self.name(decl)));
            }
        }
        let result = match p.signature(decl) {
            Some(sig) => sig.pass,
            None => TypeId::VOID,
        };
        let result_ty = format!("{}{}", self.type_params(decl)?, self.ty(result)?);
        if self.in_tests && uses_host(p, decl, NAMES) {
            return not_yet(format!(
                "host code for what only tests reach, {}",
                p.name(decl)
            ));
        }
        self.in_function = true;
        self.begin(item);
        let mut stmts = match body {
            Some(body) if !uses_host(p, decl, NAMES) => reachable(self.stmts(body)?),
            _ => self.host_body(decl, params, body, result)?,
        };
        // A `return` without a value that ends a function says nothing.
        if result == TypeId::VOID && stmts.last() == Some(&Stmt::Return(None)) {
            stmts.pop();
        }
        Ok(tree::Method {
            public: self.exported(decl),
            result: result_ty,
            name: self.name(decl),
            params: ps,
            throws: Vec::new(),
            body: stmts,
        })
    }

    /// The body of a function whose host code runs instead of its Temper
    /// body, as Kotlin's: its parameters' defaults, then a call of the
    /// method of its name in the module's host class, `NAMEConnected`
    /// (`return WorkConnected.sum(i, j, bonus);`), passing each
    /// parameter, defaulted. The class is the library's own, in a
    /// `NAMEConnected.java` beside the module.
    fn host_body(
        &mut self,
        decl: DeclId,
        params: temper_tmpl::Kids,
        body: Option<NodeId>,
        result: TypeId,
    ) -> R<Vec<Stmt>> {
        let p = self.p;
        let class = host_class(p, self.here);
        if !self.has_host_file(&class) {
            return not_yet(format!(
                "{}'s host code, with no {}",
                p.name(decl),
                host_path(p, self.here, &class)
            ));
        }
        let defaults = body.map_or_else(Vec::new, |b| analysis::defaults(p, b));
        let mut stmts = Vec::new();
        for d in &defaults {
            self.stmt(d.stmt, &mut stmts)?;
        }
        let args = (p.nodes.list(params).iter())
            .filter_map(|&param| match p.nodes.get(param) {
                Node::Param { decl } => Some(decl),
                _ => None,
            })
            .map(|param| {
                let local = (defaults.iter()).find(|d| d.param == param);
                Expr::name(self.name(local.map_or(param, |d| d.local)))
            })
            .collect();
        let call = Expr::name(class).method(host_name(p.name(decl)), args);
        stmts.push(if result == TypeId::VOID {
            Stmt::Expr(call)
        } else {
            Stmt::Return(Some(call))
        });
        Ok(stmts)
    }

    /// Whether the library has host code in a class of this name beside
    /// the module being translated.
    fn has_host_file(&self, class: &str) -> bool {
        let path = host_path(self.p, self.here, class);
        self.p.host.files.iter().any(|f| f.path == path)
    }

    /// How many of a function's last parameters are optional, which
    /// overloads may leave out.
    pub(crate) fn trailing_optional(&self, params: temper_tmpl::Kids) -> usize {
        let p = self.p;
        (p.nodes.list(params).iter().rev())
            .take_while(|&&param| {
                matches!(p.nodes.get(param), Node::Param { decl }
                    if p.decls.get(decl).facts.contains(Facts::OPTIONAL))
            })
            .count()
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
                // A `break` in a `switch` leaves the switch, so one inside
                // leaves a loop around it by its label; a `continue` doesn't.
                Node::Switch { .. } => {
                    loops.push(DeclId::NONE);
                    for &kid in p.nodes.kids(id) {
                        visit(p, kid, loops, labeled);
                    }
                    loops.pop();
                }
                Node::Break { label } | Node::Continue { label } => {
                    let brk = matches!(p.nodes.get(id), Node::Break { .. });
                    if innermost(loops, brk) != Some(label) && !labeled.contains(&label) {
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
        self.taken.clear();
        self.spilled.clear();
        walk(p, scope, &mut |n| {
            if let Node::Let { local: d, .. } | Node::Param { decl: d } = p.nodes.get(n) {
                *self.taken.entry(self.names.get(d).to_string()).or_default() += 1;
            }
        });
        for d in p.decls.ids() {
            if p.decls.contains(d) && p.decls.get(d).kind == DeclKind::ModuleLet {
                self.taken.entry(self.names.get(d).to_string()).or_insert(2);
            }
        }
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
            if KEYWORDS.contains(&base.as_str()) {
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
                let stmts = self.p.nodes.list(stmts);
                let groups = temper_tmpl::analysis::function_groups(self.p, stmts);
                for (i, &s) in stmts.iter().enumerate() {
                    match groups.iter().find(|g| g.lets.contains(&s)) {
                        Some(g) if g.lets[0] == s => self.group(g, &stmts[i + 1..], out)?,
                        Some(_) => {}
                        None => self.stmt(s, out)?,
                    }
                }
                Ok(())
            }
            _ => self.stmt(id, out),
        }
    }

    fn stmts(&mut self, id: NodeId) -> R<Vec<Stmt>> {
        self.sub(|t, out| t.block(id, out))
    }

    /// Statements translated apart, as a nested block's, with what
    /// expressions around them need kept for those.
    fn sub(&mut self, f: impl FnOnce(&mut Self, &mut Vec<Stmt>) -> R<()>) -> R<Vec<Stmt>> {
        let saved = std::mem::take(&mut self.pre);
        let mut out = Vec::new();
        let done = f(self, &mut out);
        out.append(&mut self.pre);
        self.pre = saved;
        done?;
        Ok(reachable(out))
    }

    /// Adds a statement, after what its expressions need first.
    fn emit(&mut self, out: &mut Vec<Stmt>, stmt: Stmt) {
        out.append(&mut self.pre);
        out.push(stmt);
    }

    /// A `switch`'s cases: each one's labels, none for the default, and
    /// its body.
    fn cases(&mut self, cases: temper_tmpl::Kids) -> R<Vec<(Vec<Expr>, Vec<Stmt>)>> {
        let p = self.p;
        let mut out = Vec::new();
        for &case in p.nodes.list(cases) {
            let Node::Case { labels, body } = p.nodes.get(case) else {
                continue;
            };
            let labels = (p.nodes.list(labels).iter())
                .map(|&l| self.expr(l))
                .collect::<R<Vec<_>>>()?;
            out.push((labels, self.stmts(body)?));
        }
        Ok(out)
    }

    fn loop_body(&mut self, label: DeclId, body: NodeId) -> R<Vec<Stmt>> {
        self.loops.push(label);
        let body = self.stmts(body);
        self.loops.pop();
        body
    }

    fn stmt(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        match p.nodes.get(id) {
            // A block inside a block keeps its braces where it declares
            // locals: they may share names with another block's
            // (names::RULES).
            Node::Block { stmts } => {
                let body = self.stmts(id)?;
                let declares = (p.nodes.list(stmts).iter())
                    .any(|&s| matches!(p.nodes.get(s), Node::Let { .. }));
                if declares {
                    self.emit(out, Stmt::Block(body));
                } else {
                    out.append(&mut self.pre);
                    out.extend(body);
                }
            }
            Node::Let { local, init } if self.is_cell(local) => {
                if p.decls.get(local).kind == DeclKind::Param {
                    return not_yet("a captured parameter assigned");
                }
                let ty = self.ty(p.decls.get(local).ty)?;
                let mut decls = Vec::new();
                self.declare_cell(local, ty, &mut decls);
                for d in decls {
                    self.emit(out, d);
                }
                if let Some(v) = init {
                    let target = Expr::name(self.name(local)).member("value");
                    self.into(&Place::Assign(target), v, out)?;
                }
            }
            // A local function with statements: its lambda as the local.
            Node::Let {
                local,
                init: Some(init),
            } if matches!(p.nodes.get(init), Node::FunctionLiteral { body, .. }
                if !single_return(p, body)) =>
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
                self.same.insert(local, null_default(p, init).unwrap());
            }
            // One set in a `try` and its `catch`, which a lambda captures:
            // set in a temporary, then final, as Java's lambdas need.
            Node::Let {
                local,
                init: Some(init),
            } if self.captured.contains(&local)
                && matches!(p.nodes.get(init), Node::OrElse { .. }) =>
            {
                let ty = self.ty(p.decls.get(local).ty)?;
                let name = self.name(local);
                let temporary = self.fresh(&format!("{name}_"));
                self.into(&Place::Declare(ty.clone(), temporary.clone()), init, out)?;
                self.emit(
                    out,
                    Stmt::Local {
                        ty,
                        name,
                        value: Some(Expr::name(temporary)),
                    },
                );
            }
            Node::Let { local, init } => {
                let ty = self.ty(p.decls.get(local).ty)?;
                let name = self.name(local);
                match init {
                    Some(v) => self.into(&Place::Declare(ty, name), v, out)?,
                    None => self.emit(
                        out,
                        Stmt::Local {
                            ty,
                            name,
                            value: None,
                        },
                    ),
                }
            }
            Node::SetModuleLet { target, value }
                if self.in_function && reads_only_itself(p, target, value) =>
            {
                let value = self.expr(value)?;
                let class = self.owner_class(target).unwrap_or(self.class.clone());
                let lock = Expr::name(class).member("class");
                let assign = Stmt::Assign {
                    target: Expr::name(self.target(target)),
                    value,
                };
                self.emit(
                    out,
                    Stmt::Synchronized {
                        lock,
                        body: vec![assign],
                    },
                );
            }
            Node::Assign {
                local: target,
                value,
            }
            | Node::SetModuleLet { target, value }
            | Node::InitLet { target, value } => {
                let target = Expr::name(self.target(target));
                self.into(&Place::Assign(target), value, out)?;
            }
            Node::SetField {
                field,
                object,
                value,
            } => {
                let mut object_expr = self.expr(object)?;
                if needs_stmts(p, value) && !stable(p, object) {
                    object_expr = self.spill(object, object_expr)?;
                }
                let target = object_expr.member(self.name(field));
                self.into(&Place::Assign(target), value, out)?;
            }
            // Given by its class's own initializer, if planned so.
            Node::InitStatic { target, .. } if self.in_class(target) => {}
            Node::InitStatic { target, value } => {
                let class = self.class_name(p.decls.get(target).owner);
                let target = Expr::name(class).member(self.name(target));
                self.into(&Place::Assign(target), value, out)?;
            }
            Node::If { cond, then, els } => {
                let cond = self.expr(cond)?;
                let then = self.stmts(then)?;
                let els = match els {
                    Some(els) => self.stmts(els)?,
                    None => Vec::new(),
                };
                self.emit(out, Stmt::If { cond, then, els });
            }
            Node::While { label, cond, body } if needs_stmts(p, cond) => {
                // `while (true) { …; if (!cond) break; … }`, so what the
                // condition needs runs before each test.
                let mut test = Vec::new();
                let saved = std::mem::take(&mut self.pre);
                let c = self.expr(cond);
                test.append(&mut self.pre);
                self.pre = saved;
                let c = negate(c?);
                test.push(Stmt::If {
                    cond: c,
                    then: vec![Stmt::Break(None)],
                    els: Vec::new(),
                });
                let body = self.loop_body(label, body)?;
                test.extend(body);
                self.emit(
                    out,
                    Stmt::While {
                        label: self.labels.get(&label).cloned(),
                        cond: Expr::Bool(true),
                        body: test,
                    },
                );
            }
            Node::While { label, cond, body } => {
                let cond = self.expr(cond)?;
                let body = self.loop_body(label, body)?;
                self.emit(
                    out,
                    Stmt::While {
                        label: self.labels.get(&label).cloned(),
                        cond,
                        body,
                    },
                );
            }
            Node::DoWhile { cond, .. } if needs_stmts(p, cond) => {
                return not_yet("a do … while condition that needs statements");
            }
            Node::DoWhile { label, body, cond } => {
                let body = self.loop_body(label, body)?;
                let cond = self.expr(cond)?;
                self.emit(
                    out,
                    Stmt::DoWhile {
                        label: self.labels.get(&label).cloned(),
                        body,
                        cond,
                    },
                );
            }
            Node::Switch { scrutinee, cases } => {
                let scrutinee = self.expr(scrutinee)?;
                self.loops.push(DeclId::NONE);
                let made = self.cases(cases);
                self.loops.pop();
                let cases = made?;
                self.emit(out, Stmt::Switch { scrutinee, cases });
            }
            Node::Break { label } | Node::Continue { label } => {
                let brk = matches!(p.nodes.get(id), Node::Break { .. });
                let target = if innermost(&self.loops, brk) == Some(label) {
                    None
                } else {
                    match self.labels.get(&label) {
                        Some(l) => Some(l.clone()),
                        None => return not_yet("a jump to a loop that doesn't enclose it"),
                    }
                };
                self.emit(
                    out,
                    if matches!(p.nodes.get(id), Node::Break { .. }) {
                        Stmt::Break(target)
                    } else {
                        Stmt::Continue(target)
                    },
                );
            }
            Node::Return { value } => match value.map(|v| (v, p.nodes.get(v))) {
                None | Some((_, Node::VoidLit {})) => self.emit(out, Stmt::Return(None)),
                Some((_, Node::Error { .. })) => {
                    let throw = self.throw();
                    self.emit(out, throw);
                }
                Some((v, _)) => self.into(&Place::Return, v, out)?,
            },
            Node::ExprStatement { expr } => {
                if let Node::Error { .. } = p.nodes.get(expr) {
                    let throw = self.throw();
                    self.emit(out, throw);
                    return Ok(());
                }
                self.into(&Place::Discard, expr, out)?;
            }
            other => return not_yet(format!("a {} statement", other.kind().name())),
        }
        Ok(())
    }

    /// A value put where it goes, with an `orelse` as a `try` that puts
    /// its body's value there and a `catch` that puts its fallback's, a
    /// block's statements before its value, and an `if` whose branches
    /// need statements as an `if` statement.
    fn into(&mut self, into: &Place, value: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        match (into, p.nodes.get(value)) {
            (Place::Declare(ty, name), _) if self.compound(value) => {
                let local = Stmt::Local {
                    ty: ty.clone(),
                    name: name.clone(),
                    value: None,
                };
                self.emit(out, local);
                self.into(&Place::Assign(Expr::name(name.clone())), value, out)
            }
            (_, Node::OrElse { body, fallback }) => {
                let body = self.sub(|t, o| t.into(into, body, o))?;
                // `orelse panic()` panics with the bubble as its cause.
                let (var, handler) = match p.nodes.get(fallback) {
                    Node::Panic {} => {
                        let var = self.fresh("bubble");
                        let cause = Expr::name(var.clone());
                        (
                            var,
                            vec![Stmt::Throw(Expr::New(
                                "AssertionError".into(),
                                vec![Expr::Str("panic".into()), cause],
                            ))],
                        )
                    }
                    _ => (
                        self.fresh("ignored"),
                        self.sub(|t, o| t.into(into, fallback, o))?,
                    ),
                };
                self.import("temper.core.Bubble");
                self.emit(out, Stmt::TryCatch { body, var, handler });
                Ok(())
            }
            (_, Node::BlockValue { stmts, value: last }) => {
                if self.flattens(value) {
                    for &s in p.nodes.list(stmts) {
                        self.stmt(s, out)?;
                    }
                    return self.into(into, last, out);
                }
                let body = self.sub(|t, o| {
                    for &s in p.nodes.list(stmts) {
                        t.stmt(s, o)?;
                    }
                    t.into(into, last, o)
                })?;
                self.emit(out, Stmt::Block(body));
                Ok(())
            }
            // A default with statements: `if (x == null) { … } else { x }`.
            (_, Node::Coalesce { left, right }) if needs_stmts(p, right) => {
                if !matches!(p.nodes.get(left), Node::Local { .. }) {
                    return not_yet("a coalesced value that isn't a local");
                }
                let cond = Expr::bin(Bin::Eq, self.expr(left)?, Expr::name("null"));
                let then = self.sub(|t, o| t.into(into, right, o))?;
                let els = self.sub(|t, o| t.into(into, left, o))?;
                self.emit(out, Stmt::If { cond, then, els });
                Ok(())
            }
            (_, Node::IfValue { cond, then, els })
                if needs_stmts(p, then) || needs_stmts(p, els) =>
            {
                let cond = self.expr(cond)?;
                let then = self.sub(|t, o| t.into(into, then, o))?;
                let els = self.sub(|t, o| t.into(into, els, o))?;
                self.emit(out, Stmt::If { cond, then, els });
                Ok(())
            }
            (_, Node::Bubble { .. }) => {
                let throw = self.throw();
                self.emit(out, throw);
                Ok(())
            }
            (_, Node::Panic {}) => {
                self.emit(out, panic());
                Ok(())
            }
            // A cast for its check alone, which Java can't write as a
            // statement: the call that checks.
            (Place::Discard, Node::As { .. })
                if string_index_check(p, p.nodes.ty(value)).is_some() =>
            {
                let e = self.expr(value)?;
                self.emit(out, Stmt::Expr(e));
                Ok(())
            }
            (Place::Discard, Node::As { value: v, .. }) => {
                let check = match self.checked_cast(v, p.nodes.ty(value))? {
                    Expr::Cast(_, check) => *check,
                    check => check,
                };
                self.emit(out, Stmt::Expr(check));
                Ok(())
            }
            // `void`, as an `orelse`'s fallback, does nothing.
            (Place::Discard, Node::VoidLit {}) => Ok(()),
            (Place::Discard, _) => {
                let e = self.expr(value)?;
                if e.is_statement() {
                    self.emit(out, Stmt::Expr(e));
                } else if pure(p, value) {
                    // A value alone does nothing; what it needed runs.
                    out.append(&mut self.pre);
                } else {
                    return not_yet("an expression statement that isn't a call");
                }
                Ok(())
            }
            (Place::Assign(target), _) => {
                let e = self.expr(value)?;
                let target = target.clone();
                self.emit(out, Stmt::Assign { target, value: e });
                Ok(())
            }
            (Place::Return, _) => {
                let e = self.expr(value)?;
                self.emit(out, Stmt::Return(Some(e)));
                Ok(())
            }
            (Place::Declare(ty, name), _) => {
                let e = self.expr(value)?;
                let local = Stmt::Local {
                    ty: ty.clone(),
                    name: name.clone(),
                    value: Some(e),
                };
                self.emit(out, local);
                Ok(())
            }
        }
    }

    /// Whether a value needs statements around it, at the top: an
    /// `orelse`, an `if` whose branches need statements, or a block that
    /// keeps its braces.
    fn compound(&self, value: NodeId) -> bool {
        let p = self.p;
        match p.nodes.get(value) {
            Node::OrElse { .. } => true,
            Node::IfValue { then, els, .. } => needs_stmts(p, then) || needs_stmts(p, els),
            Node::Coalesce { right, .. } => needs_stmts(p, right),
            Node::BlockValue { .. } => !self.flattens(value),
            _ => false,
        }
    }

    /// Whether a block's statements may go among those around it, without
    /// braces: none of its locals' names is another local's.
    fn flattens(&self, block: NodeId) -> bool {
        let p = self.p;
        let Node::BlockValue { stmts, .. } = p.nodes.get(block) else {
            return false;
        };
        p.nodes.list(stmts).iter().all(|&s| match p.nodes.get(s) {
            Node::Let { local, .. } => self.taken.get(self.names.get(local)) == Some(&1),
            _ => true,
        })
    }

    /// A name for a temporary or a caught exception that no local,
    /// parameter, or module static has: `base`, else numbered.
    fn fresh(&mut self, base: &str) -> String {
        let name = (1..)
            .map(|n| match n {
                1 => base.to_string(),
                n => format!("{base}{n}"),
            })
            .find(|n| !self.taken.contains_key(n))
            .unwrap_or_default();
        self.taken.insert(name.clone(), 1);
        name
    }

    /// A value computed into a temporary first, as a later operand needs
    /// statements before it.
    fn spill(&mut self, node: NodeId, e: Expr) -> R<Expr> {
        let ty = self.ty(self.p.nodes.ty(node))?;
        let name = self.fresh("t");
        self.pre.push(Stmt::Local {
            ty,
            name: name.clone(),
            value: Some(e),
        });
        Ok(Expr::name(name))
    }

    /// Before a node's operands are translated: where a later one needs
    /// statements first, each earlier one that a statement could change
    /// goes into a temporary, so it's still computed first.
    fn spill_before(&mut self, id: NodeId) -> R<()> {
        let p = self.p;
        let kids = p.nodes.kids(id);
        let Some(last) = kids.iter().rposition(|&k| needs_stmts(p, k)) else {
            return Ok(());
        };
        for &k in &kids[..last] {
            if !stable(p, k) && !self.spilled.contains_key(&k) {
                let e = self.expr(k)?;
                let e = self.spill(k, e)?;
                self.spilled.insert(k, e);
            }
        }
        Ok(())
    }

    /// An assignment's target: a local or this module's static by its
    /// name, another module's through its class.
    fn target(&mut self, decl: DeclId) -> String {
        let name = self.name(decl);
        if self.is_cell(decl) {
            return format!("{name}.value");
        }
        if self.p.decls.get(decl).kind != DeclKind::ModuleLet {
            return name;
        }
        match self.owner_class(decl) {
            Some(class) => format!("{class}.{name}"),
            None => name,
        }
    }

    /// A module's test class, `NAMETest`, in its package, under
    /// `src/test/java`: what only its tests reach, as statics
    /// ([`Translator::statics_class`]) whose initializer runs the module
    /// class's first, after keeping bubbles' stack traces
    /// (`Bubble.keepStackTraces()`), then continues it, and its tests, as JUnit 5 tests:
    /// each a method named for it in camel case (`twice10` for "twice
    /// ${base}" made "twice 10"), which makes std's `Test`, runs the body,
    /// and then, even if the body threw, ends the test (`Core.endTest`):
    /// waits for the async work it started, throwing what failed there,
    /// and fails if the `Test` has failures (`softFailToHard`), as
    /// Kotlin's do, the body's own failure winning. Being in the module's package,
    /// they see its classes and statics that aren't exported. `None`
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
        self.in_global = true;
        self.this_class = DeclId::NONE;
        self.imports.clear();
        self.class = self.test_class.clone();
        let mut outside = HashSet::new();
        for item in module.test_items.iter().chain(&module.tests) {
            if let Node::Class { .. } | Node::Interface { .. } | Node::Test { .. } =
                p.nodes.get(*item)
            {
                outside.extend(module_refs(p, *item));
            }
        }
        let mut statics =
            self.statics_class(module, &module.test_items, module.test_init, &outside)?;
        // Tests' bubbles keep stack traces, as a program's do, so one
        // that fails a test says where it came from.
        if !module.tests.is_empty() {
            self.import("temper.core.Bubble");
            let keep = Expr::name("Bubble").method("keepStackTraces", Vec::new());
            statics.init.insert(0, Stmt::Expr(keep));
        }
        self.in_function = true;
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
            // The body's failure, kept for `Core.endTest`, which then
            // finishes its async work and `softFailToHard`.
            let failure = self.fresh("failure");
            let e = self.fresh("e");
            self.import("temper.core.Core");
            let soft_fail =
                Expr::MethodRef(Box::new(Expr::name(var.clone())), "softFailToHard".into());
            let end =
                Expr::name("Core").method("endTest", vec![Expr::name(failure.clone()), soft_fail]);
            methods.push((
                test_method_name(p.strings.get(name), &mut used),
                vec![
                    Stmt::Local {
                        ty: ty.clone(),
                        name: var,
                        value: Some(Expr::name(format!("new {ty}")).call(Vec::new())),
                    },
                    Stmt::Local {
                        ty: "Throwable".into(),
                        name: failure.clone(),
                        value: Some(Expr::name("null")),
                    },
                    Stmt::Try {
                        body: stmts,
                        catch_all: Some((
                            e.clone(),
                            vec![
                                Stmt::Assign {
                                    target: Expr::name(failure),
                                    value: Expr::name(e.clone()),
                                },
                                Stmt::Throw(Expr::name(e)),
                            ],
                        )),
                        finally: vec![Stmt::Expr(end)],
                    },
                ],
            ));
        }
        Ok(Some(tree::TestClass {
            statics: tree::Class {
                imports: self.imports.iter().cloned().collect(),
                ..statics
            },
            tests: methods,
        }))
    }

    /// `throw Core.bubble();`.
    fn throw(&mut self) -> Stmt {
        self.import("temper.core.Core");
        Stmt::Throw(Expr::name("Core").method("bubble", Vec::new()))
    }

    /// `value as ty`, bubbling where it fails, as a `Bubble`, never a
    /// host exception: `Core.requireNonNull(x)` where only null fails,
    /// `Core.cast(C.class, x)` to a class without type arguments, and
    /// `(C<A>) Core.check(C.class, x)` to one with them, javac checking
    /// that cast from `x`'s own type; to a type parameter, which Java
    /// erases, the unchecked `(T) x`.
    fn checked_cast(&mut self, value: NodeId, ty: TypeId) -> Result<Expr, NotYet> {
        let p = self.p;
        let from = p.nodes.ty(value);
        let x = self.expr(value)?;
        self.import("temper.core.Core");
        let not_null = p.types.is_nullable(from) && p.types.non_null(from) == ty;
        if not_null || matches!(p.types.get(ty), TypeData::TypeParam { .. }) {
            let x = match p.types.is_nullable(from) {
                true => Expr::name("Core").method("requireNonNull", vec![x]),
                false => x,
            };
            return Ok(match not_null {
                true => x,
                false => Expr::Cast(self.ty(ty)?, Box::new(x)),
            });
        }
        let mut class = match boxed(p, ty) {
            Some((boxed, _)) => boxed.to_string(),
            None => self.ty(ty)?,
        };
        let generic = class.contains('<');
        class.truncate(class.find('<').unwrap_or(class.len()));
        let lit = Expr::name(format!("{class}.class"));
        Ok(match generic {
            false => Expr::name("Core").method("cast", vec![lit, x]),
            true => Expr::Cast(
                self.ty(ty)?,
                Box::new(Expr::name("Core").method("check", vec![lit, x])),
            ),
        })
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
                match class {
                    "Core" => self.import("temper.core.Core"),
                    "Collections" => self.import("java.util.Collections"),
                    "LocalDate" => self.import("java.time.LocalDate"),
                    _ => {}
                }
                let args = self.args(args)?;
                Ok(Expr::name(class).method(method, args))
            }
            // By its full name, as `temper.core.Core` may be imported.
            Connection::StdStatic(class, method) => {
                let args = self.args(args)?;
                Ok(Expr::name(class).method(method, args))
            }
            Connection::Method(method) => {
                let Some((&receiver, rest)) = args.split_first() else {
                    return not_yet(format!("{qname} without its receiver"));
                };
                let receiver = self.expr(receiver)?;
                let rest = self.args(rest)?;
                Ok(receiver.method(method, rest))
            }
            Connection::Cast(ty) => {
                let [receiver] = args else {
                    return not_yet(format!("{qname} with arguments"));
                };
                let receiver = self.expr(*receiver)?;
                Ok(Expr::Cast(ty.to_string(), Box::new(receiver)))
            }
            Connection::Translate(f) => f(self, args),
        }
    }

    /// Arguments, those left out at the end dropped, as Java's overloads
    /// leave them out.
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
        if let Some(e) = self.spilled.remove(&id) {
            return Ok(e);
        }
        match p.nodes.get(id) {
            Node::And { .. }
            | Node::Or { .. }
            | Node::IfValue { .. }
            | Node::OrElse { .. }
            | Node::BlockValue { .. }
            // Its parameters and body aren't operands: the body runs
            // later, as its own function.
            | Node::FunctionLiteral { .. } => {}
            _ => self.spill_before(id)?,
        }
        Ok(match p.nodes.get(id) {
            Node::BoolLit { value } => Expr::Bool(value),
            Node::Int32Lit { value } => Expr::Int(value.into()),
            Node::Int64Lit { value } => Expr::Long(value),
            Node::Float64Lit { value } => Expr::Float(value),
            Node::StringLit { value } => Expr::Str(p.strings.get(value).to_string()),
            // The type's class, its boxed one for a primitive.
            Node::TypeValue { of } => Expr::name(match p.types.get(of) {
                TypeData::Scalar(s) => match s {
                    Scalar::Void => "Void.class",
                    Scalar::Boolean => "Boolean.class",
                    Scalar::Int32 => "Integer.class",
                    Scalar::Int64 => "Long.class",
                    Scalar::Float64 => "Double.class",
                    Scalar::String => "String.class",
                    _ => return not_yet("this type as a value"),
                },
                _ => return not_yet("this type as a value"),
            }),
            Node::Local { local: decl } if self.is_cell(decl) => {
                Expr::name(self.name(decl)).member("value")
            }
            Node::Local { local: decl } if self.group_of(decl).is_some() => {
                let class = self.group_of(decl).unwrap();
                Expr::MethodRef(
                    Box::new(Expr::name(format!("{class}.this"))),
                    self.name(decl),
                )
            }
            Node::Local { local: decl } => Expr::name(self.name(decl)),
            Node::GetModuleLet { target } => match self.owner_class(target) {
                Some(class) => Expr::name(class).member(self.name(target)),
                None => Expr::name(self.name(target)),
            },
            Node::CallFunction { callee, args, .. } => {
                let args = p.nodes.list(args);
                if by_connection(p, callee, crate::connected::connects_std) {
                    return self.connected(callee, args);
                }
                let exprs = self.args(args)?;
                let args = self.adapted_args(callee, args, exprs)?;
                let owner = p.decls.get(callee).owner;
                if is_type(p, owner) {
                    // A static method: its class's own call needs no
                    // class before it; methods aren't hidden by locals.
                    if owner == self.this_class {
                        return Ok(Expr::name(self.name(callee)).call(args));
                    }
                    let class = self.class_name(owner);
                    return Ok(Expr::name(class).method(self.name(callee), args));
                }
                match self.owner_class(callee) {
                    Some(class) => Expr::name(class).method(self.name(callee), args),
                    None => Expr::name(self.name(callee)).call(args),
                }
            }
            Node::CallMethod {
                method,
                receiver,
                args,
                ..
            } => {
                if !by_connection(p, method, crate::connected::connects_std) {
                    let receiver = self.expr(receiver)?;
                    let exprs = self.member_args(p.nodes.list(args))?;
                    let args = self.adapted_args(method, p.nodes.list(args), exprs)?;
                    return Ok(receiver.method(self.method_name(method), args));
                }
                let mut all = vec![receiver];
                all.extend_from_slice(p.nodes.list(args));
                self.connected(method, &all)?
            }
            Node::This {} => Expr::This,
            // A std field the table connects, as `Date.year`'s `getYear()`.
            Node::GetField { field, object }
                if by_connection(p, field, crate::connected::connects_std)
                    && connected::lookup(&self.qname(field)).is_some() =>
            {
                self.connected(field, &[object])?
            }
            // A step's value, the runtime's public field.
            Node::GetField { field, object }
                if self.qname(field) == "core.type ValueResult.value" =>
            {
                self.expr(object)?.member("value")
            }
            // A pair's, an `Entry`'s.
            Node::GetField { field, object }
                if by_connection(p, field, crate::connected::connects_std) =>
            {
                let getter = match p.name(field) {
                    "key" => "getKey",
                    _ => "getValue",
                };
                self.expr(object)?.method(getter, Vec::new())
            }
            Node::GetField { field, object } => self.expr(object)?.member(self.name(field)),
            // Always through its class: a local could hide its name.
            // `String.begin`, `Float64.pi`, and the like.
            Node::GetStatic { target }
                if by_connection(p, target, crate::connected::connects_std) =>
            {
                self.connected(target, &[])?
            }
            Node::GetStatic { target } => {
                let class = self.class_name(p.decls.get(target).owner);
                Expr::name(class).member(self.name(target))
            }
            Node::New { ctor, args, .. }
                if is_external(p, ctor) && library_of(p, ctor) == "core"
                    || by_connection(p, ctor, crate::connected::connects_std) =>
            {
                self.made = p.nodes.ty(id);
                self.connected(ctor, p.nodes.list(args))?
            }
            // With its type arguments, which Java may not infer.
            Node::New { ctor, args, .. } => {
                let class = self.class_name(p.decls.get(ctor).owner);
                let class = match self.p.types.get(p.nodes.ty(id)) {
                    TypeData::Nominal { args, .. } if !p.types.list(args).is_empty() => {
                        let mut texts = Vec::new();
                        for &a in p.types.list(args) {
                            texts.push(self.type_arg(a)?);
                        }
                        format!("{class}<{}>", texts.join(", "))
                    }
                    _ => class,
                };
                let exprs = self.member_args(p.nodes.list(args))?;
                let args = self.adapted_args(ctor, p.nodes.list(args), exprs)?;
                Expr::New(class, args)
            }
            // `List.of(…)`, or where an item may be null, which `List.of`
            // rejects, `Core.listOf(…)`.
            Node::ListLit { items } => {
                let items = self.member_args(p.nodes.list(items))?;
                let item = p.types.args(p.nodes.ty(id)).first().copied();
                if item.is_some_and(|t| may_be_null(p, t)) {
                    self.import("temper.core.Core");
                    Expr::name("Core").method("listOf", items)
                } else {
                    self.import("java.util.List");
                    Expr::name("List").method("of", items)
                }
            }
            Node::Upcast { value } => self.expr(value)?,
            // A string index is an int: `Core.requireStringIndex(i)`, as
            // Kotlin's, or for a downcast, the int.
            Node::As { value, .. } if string_index_check(p, p.nodes.ty(id)).is_some() => {
                let name = match string_index_check(p, p.nodes.ty(id)) {
                    Some(Bin::Ge) => "requireStringIndex",
                    _ => "requireNoStringIndex",
                };
                self.import("temper.core.Core");
                Expr::name("Core").method(name, vec![self.expr(value)?])
            }
            Node::As { value, .. } => self.checked_cast(value, p.nodes.ty(id))?,
            Node::Downcast { value } if string_index_check(p, p.nodes.ty(id)).is_some() => {
                self.expr(value)?
            }
            Node::Downcast { value } => {
                let ty = self.ty(p.nodes.ty(id))?;
                Expr::Cast(ty, Box::new(self.expr(value)?))
            }
            Node::Null {} => Expr::name("null"),
            Node::IsNull { value } => Expr::bin(Bin::Eq, self.expr(value)?, Expr::name("null")),
            // A parameter's default: `x != null ? x : d`.
            Node::Coalesce { left, right } => {
                if !matches!(p.nodes.get(left), Node::Local { .. }) {
                    return not_yet("a coalesced value that isn't a local");
                }
                let x = self.expr(left)?;
                Expr::Cond {
                    cond: Box::new(Expr::bin(Bin::Ne, x.clone(), Expr::name("null"))),
                    then: Box::new(x),
                    els: Box::new(self.expr(right)?),
                }
            }
            // Java boxes and unboxes as needed, but for these.
            Node::NonNull { value } => self.expr(value)?,
            Node::AsNullable { value } => self.expr(value)?,
            // By value, never boxes by reference;
            // `equals` is `Double.compare`'s for `Double`, as `Float64.eq`.
            Node::EqNullable { left, right, .. } => {
                self.import("java.util.Objects");
                let (left, right) = (self.expr(left)?, self.expr(right)?);
                Expr::name("Objects").method("equals", vec![left, right])
            }
            Node::Bubble { .. } => {
                self.import("temper.core.Core");
                Expr::name("Core").method("throwBubble", Vec::new())
            }
            Node::Panic {} => {
                self.import("temper.core.Core");
                Expr::name("Core").method("throwAssertionError", vec![Expr::Str("panic".into())])
            }
            Node::OrElse { .. } => {
                let name = self.temp_for(id)?;
                let mut stmts = Vec::new();
                self.into(&Place::Assign(Expr::name(name.clone())), id, &mut stmts)?;
                self.pre.extend(stmts);
                Expr::name(name)
            }
            Node::BlockValue { stmts, value } if self.flattens(id) => {
                let mut out = Vec::new();
                for &s in p.nodes.list(stmts) {
                    self.stmt(s, &mut out)?;
                }
                self.pre.extend(out);
                self.expr(value)?
            }
            Node::BlockValue { .. } => {
                let name = self.temp_for(id)?;
                let mut stmts = Vec::new();
                self.into(&Place::Assign(Expr::name(name.clone())), id, &mut stmts)?;
                self.pre.extend(stmts);
                Expr::name(name)
            }
            Node::IfValue { then, els, .. } if needs_stmts(p, then) || needs_stmts(p, els) => {
                let name = self.temp_for(id)?;
                let mut stmts = Vec::new();
                self.into(&Place::Assign(Expr::name(name.clone())), id, &mut stmts)?;
                self.pre.extend(stmts);
                Expr::name(name)
            }
            Node::And { left, right } | Node::Or { left, right } if needs_stmts(p, right) => {
                // `boolean t = left; if (t) { t = right; }`, or `if (!t)`.
                let and = matches!(p.nodes.get(id), Node::And { .. });
                let left = self.expr(left)?;
                let name = self.fresh("t");
                self.pre.push(Stmt::Local {
                    ty: "boolean".into(),
                    name: name.clone(),
                    value: Some(left),
                });
                let target = Expr::name(name.clone());
                let then = self.sub(|t, o| t.into(&Place::Assign(target), right, o))?;
                let cond = if and {
                    Expr::name(name.clone())
                } else {
                    Expr::unary(Unary::Not, Expr::name(name.clone()))
                };
                self.pre.push(Stmt::If {
                    cond,
                    then,
                    els: Vec::new(),
                });
                Expr::name(name)
            }
            // `i >= 0` for a string index, `i < 0` for none, as Kotlin's.
            Node::Is { ty, value } if string_index_check(p, ty).is_some() => {
                let check = string_index_check(p, ty).unwrap_or(Bin::Ge);
                Expr::bin(check, self.expr(value)?, Expr::Int(0))
            }
            Node::Is { ty, value } => {
                // Erased, as Java checks only the class.
                let mut ty = self.ty(ty)?;
                ty.truncate(ty.find('<').unwrap_or(ty.len()));
                Expr::InstanceOf(Box::new(self.expr(value)?), ty)
            }
            Node::Identical { left, right } => {
                Expr::bin(Bin::Eq, self.expr(left)?, self.expr(right)?)
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
                if let Spelling::Static("Core", _) = spelling {
                    self.import("temper.core.Core");
                }
                let kids = p.nodes.list(args);
                let mut args: Vec<Expr> = kids.iter().map(|&a| self.expr(a)).collect::<R<_>>()?;
                // Two boxes compare by reference: unbox one.
                if let (Spelling::Bin(Bin::Eq | Bin::Ne), [a, b]) = (spelling, kids) {
                    if let (Some((_, unbox)), Some(_)) = (unboxed(p, *a), unboxed(p, *b)) {
                        let a = std::mem::replace(&mut args[0], Expr::Bool(false));
                        args[0] = a.method(unbox, Vec::new());
                    }
                }
                let mut e = ops::apply(spelling, args);
                if let Spelling::CodePoints | Spelling::CodePointsWith(_) = spelling {
                    self.compares_strings = true;
                    rename_helper(&mut e, &self.compare_helper);
                }
                e
            }
            Node::And { left, right } => Expr::bin(Bin::And, self.expr(left)?, self.expr(right)?),
            Node::Or { left, right } => Expr::bin(Bin::Or, self.expr(left)?, self.expr(right)?),
            Node::IfValue { cond, then, els } => {
                let cond = self.expr(cond)?;
                let (then, els) = (self.branch(id, then)?, self.branch(id, els)?);
                Expr::Cond {
                    cond: Box::new(cond),
                    then: Box::new(then),
                    els: Box::new(els),
                }
            }
            Node::Error { .. } => {
                self.import("temper.core.Core");
                Expr::name("Core").method("throwBubble", Vec::new())
            }
            Node::FunctionLiteral { decl, params, body } => {
                self.literal(id, decl, params, body, None)?
            }
            Node::FunctionRef { target } => self.function_ref(target)?,
            Node::CallValue { callee, args, .. } => self.call_value(callee, args)?,
            Node::ToFun { value } | Node::FromFun { value } => {
                let e = self.expr(value)?;
                self.adapt(e, p.nodes.ty(value), p.nodes.ty(id))?
            }
            other => return not_yet(format!("a {} expression", other.kind().name())),
        })
    }

    /// A temporary declared for a value whose statements put it there.
    fn temp_for(&mut self, id: NodeId) -> R<String> {
        let ty = self.ty(self.p.nodes.ty(id))?;
        let name = self.fresh("t");
        self.pre.push(Stmt::Local {
            ty,
            name: name.clone(),
            value: None,
        });
        Ok(name)
    }

    /// A branch of `?:`: a primitive made nullable is boxed explicitly,
    /// since `c ? x : 1`, for an `Integer` `x`, unboxes `x`.
    fn branch(&mut self, cond: NodeId, branch: NodeId) -> R<Expr> {
        let p = self.p;
        let e = self.expr(branch)?;
        if let Node::AsNullable { value } = p.nodes.get(branch) {
            if let Some((class, _)) = boxed(p, p.nodes.ty(value)) {
                if p.types.is_nullable(p.nodes.ty(cond)) {
                    return Ok(Expr::name(class).method("valueOf", vec![e]));
                }
            }
        }
        Ok(e)
    }

    /// A chain of `String.cat` as one concatenation: literals merged, and
    /// an `Int32.toString()` or `Boolean.toString()` without a radix as
    /// the value itself wherever a string comes before it, or right after
    /// it at the start, so that Java's `+` concatenates there and converts
    /// it the same way.
    fn cat(&mut self, id: NodeId) -> R<Expr> {
        let mut parts = Vec::new();
        self.cat_parts(id, &mut parts)?;
        parts.retain(|p| !matches!(p, Part::Lit(s) if s.is_empty()));
        let is_string = |p: &Part| matches!(p, Part::Lit(_) | Part::Str(_));
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
        // Java's `+` concatenates once either operand is a string: from
        // the second part on, since the first is a string, or converted,
        // or (as the value itself) followed by a string.
        let second_is_string = parts.get(1).is_some_and(is_string);
        let mut out = Vec::new();
        for (i, part) in parts.into_iter().enumerate() {
            out.push(match part {
                Part::Lit(s) => Expr::Str(s),
                Part::Str(e) => e,
                Part::Converted(_, e) if i > 0 || second_is_string => e,
                Part::Converted(class, e) => Expr::name(class).method("toString", vec![e]),
            });
        }
        Ok(match <[Expr; 1]>::try_from(out) {
            Ok([one]) => one,
            Err(out) => Expr::Concat(out),
        })
    }

    fn cat_parts(&mut self, id: NodeId, parts: &mut Vec<Part>) -> R<()> {
        let p = self.p;
        if let Some(e) = self.spilled.remove(&id) {
            parts.push(Part::Str(e));
            return Ok(());
        }
        match p.nodes.get(id) {
            Node::Op { op, args, .. } if ops::spelling(op) == Some(Spelling::Cat) => {
                self.spill_before(id)?;
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
                let class = match self.qname(method).as_str() {
                    "core.type Int32.toString()" => "Integer",
                    "core.type Boolean.toString()" => "Boolean",
                    _ => {
                        let e = self.expr(id)?;
                        parts.push(Part::Str(e));
                        return Ok(());
                    }
                };
                let e = self.expr(receiver)?;
                parts.push(Part::Converted(class, e));
            }
            _ => {
                let e = self.expr(id)?;
                parts.push(Part::Str(e));
            }
        }
        Ok(())
    }
}

/// Names the helper call in a comparison by the name the module gives it.
fn rename_helper(e: &mut Expr, name: &str) {
    match e {
        Expr::Call(f, _) => {
            if let Expr::Name(n) = f.as_mut() {
                if n == ops::COMPARE_CODE_POINTS {
                    *n = name.to_string();
                }
            }
        }
        Expr::Bin(_, a, _) => rename_helper(a, name),
        _ => {}
    }
}

/// Whether translating a value needs statements before it: an `orelse`,
/// or a block's statements, anywhere in it.
fn needs_stmts(p: &Program, id: NodeId) -> bool {
    let mut found = false;
    walk(p, id, &mut |n| {
        found |= matches!(
            p.nodes.get(n),
            Node::OrElse { .. } | Node::BlockValue { .. }
        );
    });
    found
}

/// Whether putting a value somewhere assigns it in a `try` and again in
/// its `catch`, which a `final` field can't have.
fn multi_assign(p: &Program, id: NodeId) -> bool {
    match p.nodes.get(id) {
        Node::OrElse { .. } => true,
        Node::BlockValue { value, .. } => multi_assign(p, value),
        Node::IfValue { then, els, .. } => multi_assign(p, then) || multi_assign(p, els),
        _ => false,
    }
}

/// Whether a value is the same wherever among the statements around it
/// it's computed: a literal, or a read of what isn't reassigned.
fn stable(p: &Program, id: NodeId) -> bool {
    let fixed = |d: DeclId| !p.decls.get(d).facts.contains(Facts::VAR);
    match p.nodes.get(id) {
        Node::BoolLit { .. }
        | Node::Int32Lit { .. }
        | Node::Int64Lit { .. }
        | Node::Float64Lit { .. }
        | Node::StringLit { .. }
        | Node::VoidLit {}
        | Node::TypeValue { .. }
        | Node::Null {}
        | Node::This {}
        | Node::Omitted {} => true,
        Node::Local { local: d }
        | Node::GetModuleLet { target: d }
        | Node::GetStatic { target: d } => fixed(d),
        Node::GetField { field, object } => fixed(field) && stable(p, object),
        Node::NonNull { value } | Node::AsNullable { value } | Node::Upcast { value } => {
            stable(p, value)
        }
        _ => false,
    }
}

/// Whether a value does nothing but give its value, so it may be dropped.
fn pure(p: &Program, id: NodeId) -> bool {
    stable(p, id)
        || matches!(
            p.nodes.get(id),
            Node::Local { .. } | Node::GetModuleLet { .. }
        )
}

/// `!cond`, as a comparison's opposite where there is one.
fn negate(cond: Expr) -> Expr {
    ops::apply(Spelling::Unary(Unary::Not), vec![cond])
}

/// `panic()`: an `AssertionError`, which no `orelse`'s `catch (Bubble …)`
/// catches, where Kotlin's throws a bubble, which one does.
fn panic() -> Stmt {
    Stmt::Throw(Expr::New(
        "AssertionError".into(),
        vec![Expr::Str("panic".into())],
    ))
}

/// A scalar's box and the method that unboxes it, for a type that Java
/// keeps in one when nullable.
fn boxed(p: &Program, ty: TypeId) -> Option<(&'static str, &'static str)> {
    Some(match p.types.get(ty) {
        TypeData::Scalar(Scalar::Boolean) => ("Boolean", "booleanValue"),
        TypeData::Scalar(Scalar::Int32) => ("Integer", "intValue"),
        TypeData::Scalar(Scalar::Int64) => ("Long", "longValue"),
        TypeData::Scalar(Scalar::Float64) => ("Double", "doubleValue"),
        // A string index, an `int`.
        TypeData::Nominal { decl, .. }
            if is_external(p, decl)
                && library_of(p, decl) == "core"
                && matches!(
                    p.name(decl),
                    "StringIndex" | "StringIndexOption" | "NoStringIndex"
                ) =>
        {
            ("Integer", "intValue")
        }
        _ => return None,
    })
}

/// For a scalar's value that Java keeps boxed, its box and the method
/// that unboxes it: a nullable one's known not to be null, or a value of
/// a type parameter's (a generic call's result, a generic field's, or a
/// list's or map's item).
fn unboxed(p: &Program, id: NodeId) -> Option<(&'static str, &'static str)> {
    let generic = |decl: DeclId| {
        let declared = match p.decls.get(decl).kind {
            DeclKind::Field => p.decls.get(decl).ty,
            _ => match p.signature(decl) {
                Some(sig) => sig.pass,
                None => return false,
            },
        };
        matches!(p.types.get(declared), TypeData::TypeParam { .. })
    };
    match p.nodes.get(id) {
        Node::NonNull { value } => boxed(p, p.types.non_null(p.nodes.ty(value))),
        Node::CallFunction { callee: d, .. }
        | Node::CallMethod { method: d, .. }
        | Node::GetField { field: d, .. }
            if generic(d) =>
        {
            boxed(p, p.nodes.ty(id))
        }
        _ => None,
    }
}

/// Whether a value of this type may be null: a nullable type, or a type
/// parameter, which may stand for one.
pub(crate) fn may_be_null(p: &Program, ty: TypeId) -> bool {
    p.types.is_nullable(ty) || matches!(p.types.get(ty), TypeData::TypeParam { .. })
}

/// Whether a function literal's body is one `return` of a value, which
/// a lambda's expression gives.
fn single_return(p: &Program, body: NodeId) -> bool {
    match p.nodes.get(body) {
        Node::Block { stmts } => matches!(
            p.nodes.list(stmts),
            [only] if matches!(p.nodes.get(*only), Node::Return { value: Some(_) })
        ),
        _ => false,
    }
}

/// Statements up to the first that can't complete normally, by Java's
/// rules (JLS 14.22), since Java rejects a statement after one.
fn reachable(mut stmts: Vec<Stmt>) -> Vec<Stmt> {
    if let Some(i) = stmts.iter().position(|s| !completes(s)) {
        stmts.truncate(i + 1);
    }
    stmts
}

/// The loop that a `break` (or a `continue`) here jumps to unlabeled:
/// the innermost, unless a `switch` (`DeclId::NONE`) is inside it, which
/// a `break` leaves instead.
fn innermost(loops: &[DeclId], brk: bool) -> Option<DeclId> {
    match brk {
        true => loops.last().copied(),
        false => loops.iter().rev().copied().find(|l| !l.is_none()),
    }
}

/// Whether a statement can complete normally, by Java's rules.
pub(crate) fn completes(s: &Stmt) -> bool {
    match s {
        // Each case's body that completes ends with `break`.
        Stmt::Switch { cases, .. } => {
            !cases.iter().any(|(labels, _)| labels.is_empty())
                || cases.iter().any(|(_, body)| body.iter().all(completes))
        }
        Stmt::Break(_) | Stmt::Continue(_) | Stmt::Return(_) | Stmt::Throw(_) => false,
        Stmt::Block(body) | Stmt::Synchronized { body, .. } => body.iter().all(completes),
        // A catch-all here rethrows, so it never completes.
        Stmt::Try { body, finally, .. } => {
            body.iter().all(completes) && finally.iter().all(completes)
        }
        Stmt::TryCatch { body, handler, .. } => {
            body.iter().all(completes) || handler.iter().all(completes)
        }
        Stmt::If { then, els, .. } => {
            els.is_empty() || then.iter().all(completes) || els.iter().all(completes)
        }
        Stmt::While { label, cond, body } => {
            *cond != Expr::Bool(true) || breaks(body, label.as_deref(), true)
        }
        Stmt::DoWhile { label, body, cond } => {
            breaks(body, label.as_deref(), true)
                || (*cond != Expr::Bool(true)
                    && (body.iter().all(completes) || continues(body, label.as_deref(), true)))
        }
        Stmt::Expr(_)
        | Stmt::Local { .. }
        | Stmt::Assign { .. }
        | Stmt::LambdaLocal { .. }
        | Stmt::LocalClass { .. }
        | Stmt::GroupClass { .. } => true,
    }
}

/// Whether statements hold a `break` of the loop labeled `label`, or of
/// the innermost loop if `innermost`.
fn breaks(body: &[Stmt], label: Option<&str>, innermost: bool) -> bool {
    jumps(body, label, innermost, true)
}

fn continues(body: &[Stmt], label: Option<&str>, innermost: bool) -> bool {
    jumps(body, label, innermost, false)
}

fn jumps(body: &[Stmt], label: Option<&str>, innermost: bool, brk: bool) -> bool {
    body.iter().any(|s| match s {
        Stmt::Break(l) if brk => l.as_deref().map_or(innermost, |l| Some(l) == label),
        Stmt::Continue(l) if !brk => l.as_deref().map_or(innermost, |l| Some(l) == label),
        Stmt::Block(b) | Stmt::Synchronized { body: b, .. } | Stmt::Try { body: b, .. } => {
            jumps(b, label, innermost, brk)
        }
        Stmt::TryCatch { body, handler, .. } => {
            jumps(body, label, innermost, brk) || jumps(handler, label, innermost, brk)
        }
        Stmt::If { then, els, .. } => {
            jumps(then, label, innermost, brk) || jumps(els, label, innermost, brk)
        }
        Stmt::While { body, .. } | Stmt::DoWhile { body, .. } => jumps(body, label, false, brk),
        // An unlabeled `break` in it leaves it, not the loop.
        Stmt::Switch { cases, .. } => {
            (cases.iter()).any(|(_, body)| jumps(body, label, innermost && !brk, brk))
        }
        _ => false,
    })
}

/// A library's package: std's is `temper.std`, as Kotlin's; another's is
/// its config's `javaPackage`, each part as [`crate::names::package`]
/// gives it, or else its `javaName` or its own name, as that gives it.
pub fn library_package(p: &Program, library: &str) -> String {
    let meta = p.metadata_of(library);
    match (library, meta.get("javaPackage")) {
        ("std", _) => "temper.std".into(),
        (_, Some(package)) => (package.split('.'))
            .map(crate::names::package)
            .collect::<Vec<_>>()
            .join("."),
        _ => crate::names::package(meta.get("javaName").unwrap_or(library)),
    }
}

/// A module's package: its library's, then each part of its path.
pub fn module_package(p: &Program, module: DeclId) -> String {
    let d = p.decls.get(module);
    let (library, path) = match d.kind {
        DeclKind::Library => (library_package(p, p.name(module)), ""),
        _ if !d.owner.is_none() => (library_package(p, p.name(d.owner)), p.name(module)),
        _ => {
            let path = (p.modules.iter())
                .find(|m| m.decl == module)
                .map_or("", |m| m.path.as_str());
            (library_package(p, &p.library.name), path)
        }
    };
    let mut out = library;
    for part in path.split('/').filter(|s| !s.is_empty()) {
        out.push('.');
        out.push_str(&crate::names::package(part));
    }
    out
}

/// A module's package and the start of its classes' names, as Kotlin's:
/// the last part of its path in Pascal case, or for a library's root, its
/// package's (`helpers/deep` gives `Deep`).
pub fn module_names(p: &Program, module: DeclId) -> (String, String) {
    let package = module_package(p, module);
    let last = package.rsplit('.').next().unwrap_or(&package);
    let base = crate::names::class_base(last);
    (package, base)
}

/// A module's package and its statics' class, `NAMEGlobal`.
pub fn global_class(p: &Program, module: DeclId) -> (String, String) {
    let (package, base) = module_names(p, module);
    (package, format!("{base}Global"))
}

/// A test's method name: its name's words in camel case, letters and
/// digits only, numbered past those already used.
fn test_method_name(text: &str, used: &mut BTreeSet<String>) -> String {
    let mut name = String::new();
    for word in text
        .split(|c: char| !c.is_alphanumeric())
        .filter(|w| !w.is_empty())
    {
        let mut chars = word.chars();
        let first = chars.next().unwrap_or_default();
        if name.is_empty() {
            name.extend(first.to_lowercase());
        } else {
            name.extend(first.to_uppercase());
        }
        name.push_str(chars.as_str());
    }
    if name.is_empty()
        || !name.starts_with(char::is_alphabetic)
        || KEYWORDS.contains(&name.as_str())
    {
        name.insert_str(0, "test");
    }
    let mut unique = name.clone();
    let mut n = 2;
    while !used.insert(unique.clone()) {
        unique = format!("{name}{n}");
        n += 1;
    }
    unique
}

/// Overloads of a function whose last `k` parameters are optional, as
/// Kotlin's: each leaves out one more, passing null for it, which gives
/// its default. Java has no default arguments.
fn overloads(m: &tree::Method, k: usize) -> Vec<tree::Method> {
    (1..=k)
        .map(|j| {
            let n = m.params.len() - j;
            let mut args: Vec<Expr> = (m.params[..n].iter())
                .map(|(_, name)| Expr::name(name.clone()))
                .collect();
            args.extend((0..j).map(|_| Expr::name("null")));
            let call = Expr::name(m.name.clone()).call(args);
            tree::Method {
                public: m.public,
                result: m.result.clone(),
                name: m.name.clone(),
                params: m.params[..n].to_vec(),
                throws: m.throws.clone(),
                body: vec![if m.result.ends_with("void") {
                    Stmt::Expr(call)
                } else {
                    Stmt::Return(Some(call))
                }],
            }
        })
        .collect()
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
pub const NAMES: &[&str] = &["java"];

/// A module's host class, whose static methods are its connected
/// functions' host code: `NAMEConnected`, as Kotlin's.
pub fn host_class(p: &Program, module: DeclId) -> String {
    format!("{}Connected", module_names(p, module).1)
}

/// Where a host class's file is, relative to the library's root: beside
/// its module's sources.
pub fn host_path(p: &Program, module: DeclId, class: &str) -> String {
    let dir = (p.modules.iter())
        .find(|m| m.decl == module)
        .map_or("", |m| m.path.as_str());
    match dir {
        "" => format!("{class}.java"),
        dir => format!("{dir}/{class}.java"),
    }
}

/// The name of a connected function's method in host code, as Kotlin's:
/// its Temper name, with a reserved word's trailing `_`.
fn host_name(name: &str) -> String {
    if KEYWORDS.contains(&name) {
        format!("{name}_")
    } else {
        name.to_string()
    }
}
