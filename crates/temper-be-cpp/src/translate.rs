//! The translation from tmpl to C++'s output tree, classes and
//! interfaces in [`classes`].
//!
//! **Nullables,** as Kotlin's: a class's or interface's `shared_ptr` may
//! be null itself, and another type `T?` is the runtime's
//! `core::NullableParam<T>`. A null check is `x == nullptr` (which
//! `NullableParam` defines too), a value known not to be null is `x.value`
//! (or the pointer itself), and `==` with a side that may be null is
//! `NullableParam`'s `==`, which compares as Temper's does, where the
//! type's own `==` is C++'s. `as` from `T?` checks, bubbling on null
//! (`core::not_null`, or `checked_cast` for a class).
//!
//! **Bubbling** is the runtime's `TemperBubble` exception: `bubble()` is
//! `core::bubble()`, and `orelse` is `try`/`catch`, each side giving its
//! value where the `orelse`'s goes (returned, assigned, or dropped). An
//! `orelse` or a `BlockValue` (`??`) inside an expression puts its
//! statements before the statement it's in, those before it that do
//! anything computed first; where nothing may run first (the right of
//! `&&`, a branch of `?:`, a loop's condition), it's a lambda called at
//! once. `panic()` is `core::panic()`, which stops the program: Kotlin's
//! turns `f() orelse panic()` into a bubble rethrown, which an `orelse`
//! further out would catch.
//!
//! A module is a namespace, the library's (Kotlin puts every module of a
//! library in it), with its `.cpp` and its header. Its header includes
//! the headers of the modules it imports, the library's own and other
//! libraries' (`<other/helpers.hpp>`), and its init function first calls
//! theirs, each running once. Another module's declarations of the
//! library are named as they are, in the same namespace; another
//! library's, with its namespace (`other::twice`, std's
//! `temper_std::Test`). A `var` of another module's is read where it is,
//! without its module's lock: tmpl reads one only where an import copies
//! it, in the init function, which the header's note allows.
//!
//! **Tests** are functions of the module's, `void NAME(std::shared_ptr<
//! temper_std::Test> const &test)`, as Kotlin's, but only where
//! `TEMPER_TESTS` is defined: at the end of the `.cpp`, after an include
//! of what only they import (std's `testing`), and declared in the header,
//! so the library builds and runs without std, and a test build
//! ([`crate::test_main`]) has them. What only tests reach
//! (`temper_tmpl::reach::partition`, the module's test items and init)
//! is in that `#ifdef` too ([`Item::TestsOnly`]), after the library's:
//! functions, `@keepTest` ones for tests' host code among them, which
//! the header declares there, classes' members, whose structs the
//! header defines there, storage, and the module's lock where only they
//! use it; and the overloads of a private function that only tests
//! call. The test init ends the init function there
//! ([`Stmt::TestsOnly`]), in its scope. What nothing reaches isn't
//! written. Functions are
//! functions, `static` unless exported or called by a template in the
//! header (a generic function's body), which the header declares; the
//! init block is the body of the module's init function,
//! `global_init_init()`, declared first in the header, which runs it
//! once, through the runtime's `init_once`, as Kotlin's (#636): threads
//! that call it at once wait for one run, and a call from the init
//! itself, as its calls of exported functions make, returns at once. Module
//! `let`s and `var`s that functions use, or that are exported, are
//! storage, as Kotlin's, `core::GlobalSlot`s in a namespace,
//! `temper_globals`, `static` unless exported, which the header declares
//! `extern`; static fields are too, in their class's `struct
//! temper_statics`. The init constructs them first, then runs the
//! modules it imports, then the top level, which sets them; the
//! library's code reads them there (`temper_globals::x.value`), and users
//! through accessors, `x()` and `Type::x()`, which give `const &`s. The
//! rest of the module's `let`s and `var`s are the init's locals. Entry
//! points run the init first, where it does anything: exported functions
//! and accessors, and public constructors and static functions of
//! exported classes and interfaces, as Kotlin's.
//!
//! **Module `var`s that functions write** are shared between threads, so
//! functions read and write them only under the module's lock, a
//! `std::mutex`, where Kotlin's are plain variables, a data race (which
//! C++ leaves undefined) when two threads call such functions at once. A
//! run of statements that read or write them and call nothing
//! ([`calls_nothing`]) runs under one lock, a `std::lock_guard` to the
//! end of its block (`ticks = ticks + 1`, or `bump`'s two updates and its
//! `return`), so concurrent updates are never lost; elsewhere a read is
//! `load(x)`, which takes the lock to copy the value, and a store of a
//! value that calls something is `store(x, value)`, computed first and
//! stored under the lock, since a call could take the lock again. Such an
//! exported `var`'s accessor gives a copy, through `load`, where Kotlin's
//! gives a `const &` that the functions' writes race with. The init
//! function needs no lock: it runs once, and entry points wait for it.
//!
//! **Order of evaluation:** C++ leaves the order of a call's arguments,
//! of a receiver and its method's arguments, and of most operators'
//! operands, unspecified (g++ often goes right to left), where Temper's
//! is left to right. Where the order would show ([`Effects`]: one
//! operand writes module state or a field, or logs, and another
//! writes too, reads what may be written, or may bubble), each operand
//! before the last such one is computed first into a local named by
//! [`naming::hints`] (`int32_t int32 = bump();`), in order. Kotlin's C++
//! (at 69f1a5b0) leaves the order to the compiler, so
//! `module-state`'s `report()` logs `hi 1 of 3 10` under g++.
//!
//! **Objects** are `std::shared_ptr`s; a parameter of one is a `const &`,
//! as Kotlin's, which the caller's temporary outlives, since C++ keeps a
//! temporary to the end of its full expression: so calls chain on
//! objects that calls return (`r->scaled(2)->scaled(3)`), and nothing
//! holds a reference into one past its statement. What a reference can
//! outlive is a field: an argument read from a `var` field, or through
//! one (`self->box->item`), is copied to a named local first, since the
//! callee could assign the field and free what the reference shows, and
//! a member keeps its own object (`self`) where it could drop the last
//! other `shared_ptr` to it ([`classes`]). Where Kotlin passes the field
//! itself, `this_->replace(this_->box)` sees the new box, not the one it
//! was given.
//!
//! C++ has no labeled loops: a `break` or `continue` of a loop that isn't
//! the innermost is a `goto` to a label after the loop or at the end of
//! its body, outside a block holding the body, so the jump never skips a
//! local's initialization. A self-assignment (`zero = zero`) is left out:
//! it does nothing.

mod classes;
mod functions;
mod groups;

use crate::connected::{self, Connection};
use crate::effects::{self, Effects};
use crate::names;
use crate::ops::{self, Spelling};
use crate::tree::{self, Bin, Expr, Item, Labels, Stmt, Unary};
use crate::{NAMES, NotYet};
use std::collections::{HashMap, HashSet};
use temper_tmpl::analysis::{
    self, Effect, Import, calls_nothing, module_imports, module_refs, module_state, module_writes,
    test_imports,
};
use temper_tmpl::host::uses_host;
use temper_tmpl::naming::{self, NameMap};
use temper_tmpl::qname::{by_connection, is_external, library_of};
use temper_tmpl::{
    DeclId, DeclKind, Facts, Module, Node, NodeId, Program, QNames, Scalar, TypeData, TypeId,
};

type R<T> = Result<T, NotYet>;

fn not_yet<T>(what: impl Into<String>) -> R<T> {
    Err(NotYet { what: what.into() })
}

/// The alias of `temper::core` in a module's `.cpp`, which the naming
/// rules reserve.
const CORE: &str = "core";

/// Whether a type is passed by `const &` where it isn't assigned: one
/// whose copy bumps a count or more, a `shared_ptr`, a `std::function`,
/// or a string's `core::Str`, as Kotlin's (its `isIndirectType`, and the
/// runtime's `IsIndirect`).
pub(crate) fn indirect(ty: &str) -> bool {
    ty.starts_with("std::shared_ptr<") || ty.starts_with("std::function<") || ty == "core::Str"
}

/// A module translated: its `.cpp`, and what its header declares.
pub struct Translated {
    pub source: tree::Source,
    /// Exported module values, which the header declares.
    pub exported_variables: Vec<Exported>,
    /// Storage that the header's templates use, which isn't exported, as
    /// its declaration in `temper_globals`: `temper::core::GlobalSlot<T>
    /// name`.
    pub template_storage: Vec<String>,
    /// Whether it has a lock, and so includes `<mutex>`.
    pub uses_mutex: bool,
    /// Exported functions' prototypes, without the `;`.
    pub exported_functions: Vec<String>,
    /// The headers of the modules it imports, as `<lib/helpers.hpp>`.
    pub header_includes: Vec<String>,
    /// The headers that only its tests import.
    pub test_includes: Vec<String>,
    /// Its tests' prototypes, without the `;`.
    pub test_prototypes: Vec<String>,
    /// Its `@keepTest` functions' prototypes, without the `;`, for tests'
    /// host code: declared only where the tests are.
    pub kept_for_tests: Vec<String>,
    /// Its classes' and interfaces' structs, for the header: each
    /// declared, then each defined, supertypes first.
    pub classes: String,
    /// Definitions of templates others may instantiate, for the header,
    /// after its prototypes: exported generic functions, and generic
    /// classes' members.
    pub templates: String,
    /// What the header has of what only tests reach, only where
    /// `TEMPER_TESTS` is defined.
    pub tests: TestHeader,
    /// Whether it takes a function or class from host code, and so its
    /// `.cpp` includes the module's `_connected.hpp`.
    pub host: bool,
    /// The runtime's headers beyond `core.hpp` that its `.cpp` includes,
    /// as `<temper-core/net.hpp>` ([`crate::connected::BODILESS`]).
    pub runtime_includes: Vec<String>,
}

/// What a module's header declares of what only tests reach, as
/// [`Translated`] has the library's.
#[derive(Default)]
pub struct TestHeader {
    /// Its classes' and interfaces' structs.
    pub classes: String,
    /// The storage its templates use.
    pub template_storage: Vec<String>,
    /// The private functions its templates call, as prototypes.
    pub functions: Vec<String>,
    /// Its generic classes' members, and the private generic functions
    /// its templates call.
    pub templates: String,
}

impl TestHeader {
    pub fn is_empty(&self) -> bool {
        self.classes.is_empty()
            && self.template_storage.is_empty()
            && self.functions.is_empty()
            && self.templates.is_empty()
    }
}

/// An exported module value, as the header declares it.
pub struct Exported {
    /// Its storage's type, `temper::core::GlobalSlot<T>`, and name, in
    /// `temper_globals`.
    pub slot: String,
    pub name: String,
    /// Its accessor's prototype, without the `;`.
    pub accessor: String,
    /// Whether functions write it, so its accessor gives a copy.
    pub shared: bool,
}

pub struct Translator<'a> {
    p: &'a Program,
    /// For a method that overrides one taking a type parameter's value,
    /// as `doEvent(e: EVENT)`, the parameters, by index, that it takes by
    /// value too, as the method it overrides must (`EVENT e`, where
    /// `EVENT` may be an `int32_t`), not by `const &`.
    pub(crate) by_value: Vec<bool>,
    names: &'a NameMap,
    qnames: QNames,
    effects: Effects,
    /// Module `let`s and `var`s kept at namespace scope.
    statics: HashSet<DeclId>,
    /// Those that functions write, which functions use under the lock.
    shared: HashSet<DeclId>,
    /// The storage of those, and of static fields, which the init
    /// constructs first: `temper_globals::x`, `Rect::temper_statics::unit`;
    /// each with whether only tests reach it.
    pub(crate) slots: Vec<(String, bool)>,
    /// Whether translating what only tests reach.
    pub(crate) tests_only: bool,
    /// Every name the module's declarations take, and the reserved ones.
    taken: HashSet<String>,
    /// The module's mutex and its helpers, if used, by name.
    mutex: Option<String>,
    load: Option<String>,
    store: Option<String>,
    /// Whether translating a function, not the init block.
    in_function: bool,
    /// Whether the module's lock is held where translating.
    locked: bool,
    /// The runtime's headers the module's `.cpp` needs beyond `core.hpp`.
    runtime_includes: Vec<String>,
    /// The enclosing loops, innermost last.
    loops: Vec<DeclId>,
    /// Labels printed, by loop: for a `continue` and a `break` from an
    /// inner loop, only where one needs them.
    labels: HashMap<DeclId, Labels>,
    /// Temporaries for the statement being translated, in order.
    pending: Vec<Stmt>,
    /// The group of local functions whose method is being translated
    /// ([`groups`]).
    group: Option<groups::GroupHere>,
    /// How many places enclose the expression being translated where a
    /// temporary can't go before its statement: the right of `&&` or
    /// `||`, a branch of `?:`, or a loop's condition.
    deferred: usize,
    /// The names in the function or init block being translated.
    locals: HashSet<String>,
    /// Each test's function's name, by its declaration.
    pub test_names: HashMap<DeclId, String>,
    /// The names of members that may change their object, which aren't
    /// `const` ([`mutating_members`]).
    pub mutating: HashSet<String>,
    /// For `temper run` and `temper test`, a prefix for the sources'
    /// paths in `#line` directives, before each statement
    /// ([`Stmt::Line`]).
    pub lines: Option<String>,
    /// The library's namespace.
    namespace: String,
    /// Each class's and interface's node, by its declaration.
    class_nodes: HashMap<DeclId, NodeId>,
    /// The class or interface whose member is being translated.
    class: Option<classes::ClassCx>,
    /// What `this` is in the member being translated: `this`, or the
    /// `shared_ptr` that holds it, `self`.
    this_expr: Option<Expr>,
    /// Whether translating a constructor, which returns `self`.
    in_ctor: bool,
    /// Parameters and locals renamed so as not to shadow a field.
    renamed: HashMap<DeclId, String>,
    /// The locals something reads, found when first asked.
    read_locals: Option<HashSet<DeclId>>,
    /// The names of locals that `BlockValue`s declared in the statement
    /// around them, in the function being translated, so another such
    /// local of the same name takes a fresh one.
    hoisted: HashSet<String>,
    /// How many locals each name names in the function being translated.
    let_names: HashMap<String, usize>,
    /// Whether translating a `BlockValue`'s statements into the statement
    /// around it.
    in_hoist: bool,
    /// The connected call being translated, whose type `Making` reads.
    call: NodeId,
    /// Locals that lambdas capture and that are assigned, which are
    /// cells ([`functions`]).
    cells: HashSet<DeclId>,
}

/// Where an `orelse`'s value, or any value given to
/// [`Translator::deliver`], goes.
#[derive(Clone)]
enum Sink {
    /// Returned.
    Return,
    /// Dropped.
    Discard,
    /// Assigned to a local or module-level variable, by name.
    Assign(String),
}

pub use classes::mutating_members;

/// A part of a string concatenation, before it's written.
enum Part {
    Lit(String),
    /// A string-typed expression.
    Str(NodeId),
    /// An `Int32` or `Int64` that `toString()` converts, its receiver.
    Number(NodeId),
    /// A `Boolean` that `toString()` converts, its receiver.
    Boolean(NodeId),
}

impl<'a> Translator<'a> {
    pub fn new(p: &'a Program, names: &'a NameMap, _module: &Module) -> Translator<'a> {
        let mut taken: HashSet<String> = (p.decls.ids())
            .filter(|&d| p.decls.contains(d))
            .map(|d| names.get(d).to_string())
            .filter(|n| !n.is_empty())
            .collect();
        for list in names::RULES.reserved {
            taken.extend(list.iter().map(|s| s.to_string()));
        }
        let mut class_nodes = HashMap::new();
        for m in &p.modules {
            for item in m.all_items() {
                if let Node::Class { decl, .. } | Node::Interface { decl, .. } = p.nodes.get(item) {
                    class_nodes.insert(decl, item);
                }
            }
        }
        Translator {
            p,
            names,
            qnames: QNames::derive(p),
            // Calls reach other modules' functions, so effects are the
            // library's.
            effects: Effects::of(p, names),
            by_value: Vec::new(),
            statics: HashSet::new(),
            shared: HashSet::new(),
            slots: Vec::new(),
            tests_only: false,
            taken,
            mutex: None,
            load: None,
            store: None,
            in_function: false,
            locked: false,
            runtime_includes: Vec::new(),
            loops: Vec::new(),
            labels: HashMap::new(),
            pending: Vec::new(),
            group: None,
            deferred: 0,
            locals: HashSet::new(),
            test_names: HashMap::new(),
            mutating: HashSet::new(),
            lines: None,
            namespace: names::namespace(&p.library.name),
            class_nodes,
            class: None,
            this_expr: None,
            in_ctor: false,
            renamed: HashMap::new(),
            read_locals: None,
            hoisted: HashSet::new(),
            let_names: HashMap::new(),
            in_hoist: false,
            call: NodeId(0),
            cells: temper_tmpl::analysis::assigned_captures(p),
        }
    }

    /// A generic declaration's `template <class T>` line, or nothing.
    pub(crate) fn template_line(&self, decl: DeclId) -> String {
        let params = self.p.decls.type_params(decl);
        if params.is_empty() {
            return String::new();
        }
        let names: Vec<String> = (params.iter())
            .map(|&t| format!("class {}", self.p.name(t)))
            .collect();
        format!("template <{}>", names.join(", "))
    }

    /// A class or interface as its own members name it: `Box<T>` for a
    /// generic one.
    pub(crate) fn class_ref(&self, decl: DeclId) -> String {
        let params = self.p.decls.type_params(decl);
        let name = self.name(decl);
        if params.is_empty() {
            return name;
        }
        let names: Vec<&str> = params.iter().map(|&t| self.p.name(t)).collect();
        format!("{name}<{}>", names.join(", "))
    }

    /// Type arguments as C++ writes them: `<core::Str, int32_t>`, or
    /// nothing.
    fn template_args(&self, args: &[TypeId]) -> R<String> {
        if args.is_empty() {
            return Ok(String::new());
        }
        let mut texts = Vec::new();
        for &a in args {
            texts.push(self.ty(a)?);
        }
        Ok(format!("<{}>", texts.join(", ")))
    }

    /// What evaluating an expression may do that its order would show.
    fn effect(&self, id: NodeId) -> Effect {
        self.effects.node_with(self.p, id, self.names)
    }

    /// A module-level declaration: its name, or another library's,
    /// qualified by its namespace.
    fn top(&self, decl: DeclId) -> Expr {
        Expr::name(self.top_name(decl))
    }

    /// [`top`](Self::top)'s text: another library's name with its
    /// namespace, and a class's static member or constructor with its
    /// class's name (`Rect::make`). Module values kept past the init and
    /// static fields are their storage (`temper_globals::x.value`,
    /// `Rect::temper_statics::unit.value`). Inside a class's member, a
    /// name that one of its members takes, which would hide it, is
    /// qualified too.
    fn top_name(&self, decl: DeclId) -> String {
        let p = self.p;
        let d = p.decls.get(decl);
        if p.decls.contains(d.owner)
            && matches!(
                p.decls.get(d.owner).kind,
                DeclKind::Class | DeclKind::Interface
            )
        {
            let name = match d.kind {
                DeclKind::Constructor => "make".to_string(),
                DeclKind::StaticField => format!("temper_statics::{}.value", self.name(decl)),
                _ => self.name(decl),
            };
            return format!("{}::{name}", self.top_name(d.owner));
        }
        let name = self.name(decl);
        // Module values are storage, read and set directly, as Kotlin's.
        let name = match d.kind {
            DeclKind::ModuleLet
                if self.statics.contains(&decl) || self.exported(decl) || is_external(p, decl) =>
            {
                format!("temper_globals::{name}.value")
            }
            _ => name,
        };
        match external_library(p, decl) {
            Some(library) => format!("{}::{name}", names::namespace(&library)),
            None if self
                .class
                .as_ref()
                .is_some_and(|c| c.members.contains(&name)) =>
            {
                format!("{}::{name}", self.namespace)
            }
            None => name,
        }
    }

    pub(crate) fn node(&self, id: NodeId) -> Node {
        self.p.nodes.get(id)
    }

    /// A name of the runtime's, relative to `temper::core`, or of the
    /// standard library's, qualified.
    pub(crate) fn core(&self, path: &str) -> Expr {
        if path.starts_with("std::") {
            Expr::name(path)
        } else {
            Expr::name(format!("{CORE}::{path}"))
        }
    }

    fn name(&self, decl: DeclId) -> String {
        match self.renamed.get(&decl) {
            Some(name) => name.clone(),
            None => self.names.get(decl).to_string(),
        }
    }

    /// Whether a type is a generator step's value result, or its done
    /// result.
    fn step_result(&self, ty: TypeId) -> Option<bool> {
        let decl = temper_tmpl::analysis::nominal_decl(self.p, ty)?;
        match self.qname(decl).as_str() {
            "core.type ValueResult" => Some(true),
            "core.type DoneResult" => Some(false),
            _ => None,
        }
    }

    fn qname(&self, decl: DeclId) -> String {
        self.qnames
            .get(decl)
            .map(|q| q.to_string())
            .unwrap_or_default()
    }

    /// A type as C++ writes it in a `.cpp`, `temper::core` as `core`.
    fn ty(&self, ty: TypeId) -> R<String> {
        Ok(match self.p.types.get(ty) {
            // `temper_tmpl::nevers` made each `Never<T>` the `T` it stands
            // for, as this profile has no bottom type.
            TypeData::Never(t) => return self.ty(t),
            TypeData::Scalar(s) => match s {
                Scalar::Void => "void",
                Scalar::Boolean => "bool",
                Scalar::Int32 => "int32_t",
                Scalar::Int64 => "int64_t",
                Scalar::Float64 => "double",
                Scalar::String => "core::Str",
                Scalar::AnyValue => "std::shared_ptr<core::AnyValueBase>",
                Scalar::Type => "std::type_index",
            }
            .to_string(),
            // A `@fun` interface is its function type's `std::function`.
            TypeData::Nominal { decl, .. } if self.p.decls.get(decl).facts.contains(Facts::FUN) => {
                self.function_type(ty)?
            }
            TypeData::Nominal { decl, args } => {
                let qname = self.qname(decl);
                let args = self.template_args(self.p.types.list(args))?;
                match connected::lookup_type(&qname) {
                    Some(c) => c.to_string(),
                    // Core's lists and maps, as Kotlin's.
                    None if qname == "core.type List"
                        || qname == "core.type Listed"
                        || qname == "core.type ListBuilder" =>
                    {
                        let item = (args.strip_prefix('<'))
                            .and_then(|a| a.strip_suffix('>'))
                            .unwrap_or_default();
                        format!("std::shared_ptr<std::vector<{item}>>")
                    }
                    None if qname == "core.type Map"
                        || qname == "core.type Mapped"
                        || qname == "core.type MapBuilder" =>
                    {
                        format!("std::shared_ptr<{CORE}::Mapped::Ordered{args}>")
                    }
                    None if qname == "core.type Deque" => {
                        let item = (args.strip_prefix('<'))
                            .and_then(|a| a.strip_suffix('>'))
                            .unwrap_or_default();
                        format!("std::shared_ptr<std::deque<{item}>>")
                    }
                    None if qname == "core.type Pair" => {
                        format!("std::shared_ptr<{CORE}::Pair{args}>")
                    }
                    // The runtime's generators and promises, as Kotlin's.
                    None if matches!(
                        qname.as_str(),
                        "core.type Promise"
                            | "core.type PromiseBuilder"
                            | "core.type SafeGenerator"
                            | "core.type Generator"
                            | "core.type GeneratorResult"
                    ) =>
                    {
                        let name = qname.trim_start_matches("core.type ");
                        format!("std::shared_ptr<{CORE}::{name}{args}>")
                    }
                    // A step's result, narrowed: still the runtime's
                    // `GeneratorResult`, whose `is_done` and `value` tell.
                    None if matches!(
                        qname.as_str(),
                        "core.type ValueResult" | "core.type DoneResult"
                    ) =>
                    {
                        format!("std::shared_ptr<{CORE}::GeneratorResult{args}>")
                    }
                    // A class or interface, the program's or another
                    // library's, as Kotlin's are.
                    None if matches!(
                        self.p.decls.get(decl).kind,
                        DeclKind::Class | DeclKind::Interface
                    ) && !(is_external(self.p, decl)
                        && library_of(self.p, decl) == "core") =>
                    {
                        format!("std::shared_ptr<{}{args}>", self.top_name(decl))
                    }
                    None => return not_yet(format!("a type for {qname}")),
                }
            }
            TypeData::Function { .. } => self.function_type(ty)?,
            // A class's `shared_ptr` may be null itself; another type is
            // the runtime's `NullableParam`, as Kotlin's, a type
            // parameter's too, whatever it's bound to.
            TypeData::Nullable(inner) => {
                let t = self.ty(inner)?;
                if t.starts_with("std::shared_ptr<") {
                    t
                } else {
                    format!("{CORE}::NullableParam<{t}>")
                }
            }
            TypeData::TypeParam { decl } => self.p.name(decl).to_string(),
        })
    }

    fn exported(&self, decl: DeclId) -> bool {
        self.p.decls.get(decl).facts.contains(Facts::EXPORTED)
    }

    /// A name for a helper, the first of `base`, `base2`, … that nothing
    /// in the module takes.
    fn fresh_module_name(&mut self, base: &str) -> String {
        let name = (1..)
            .map(|n| match n {
                1 => base.to_string(),
                n => format!("{base}{n}"),
            })
            .find(|n| !self.taken.contains(n))
            .unwrap_or_default();
        self.taken.insert(name.clone());
        name
    }

    fn mutex(&mut self) -> String {
        if self.mutex.is_none() {
            self.mutex = Some(self.fresh_module_name("moduleMutex"));
        }
        self.mutex.clone().unwrap_or_default()
    }

    /// Translates a module.
    pub fn module(&mut self, module: &Module) -> R<Translated> {
        let p = self.p;
        for d in p.decls.ids().filter(|&d| p.decls.contains(d)) {
            let name = self.names.get(d);
            if !is_external(p, d) && names::reserved_for_implementation(name) {
                return not_yet(format!("a name C++ reserves, {name}"));
            }
        }
        let mut state = module_state(p, module);
        let mut written = HashSet::new();
        // Functions, and classes' members, which use module state as
        // functions do.
        for (_, item) in effects::callables(p, module) {
            written.extend(module_writes(p, item));
            for d in module_refs(p, item) {
                if p.decls.get(d).kind == DeclKind::ModuleLet {
                    state.used_by_functions.insert(d);
                }
            }
        }
        // Tests are functions too.
        for &test in &module.tests {
            written.extend(module_writes(p, test));
            state.used_by_functions.extend(module_refs(p, test));
        }
        // Module values kept past the init: storage in `temper_globals`,
        // and an exported one's accessor, as Kotlin's.
        // Each with whether only tests reach it, as below.
        let mut globals = Vec::new();
        let mut accessors = Vec::new();
        let mut exported_variables = Vec::new();
        let mut init_locals = Vec::new();
        let parts: Vec<(NodeId, bool)> = (module.items.iter().map(|&i| (i, false)))
            .chain(module.test_items.iter().map(|&i| (i, true)))
            .collect();
        for &(item, tests) in &parts {
            if let Node::ModuleLet { decl } = p.nodes.get(item) {
                let d = p.decls.get(decl);
                let ty = self.ty(d.ty)?;
                let name = self.name(decl);
                if self.exported(decl) || state.used_by_functions.contains(&decl) {
                    self.statics.insert(decl);
                    if d.facts.contains(Facts::VAR) && written.contains(&decl) {
                        self.shared.insert(decl);
                    }
                    let slot = format!("{CORE}::GlobalSlot<{ty}>");
                    self.slots.push((format!("temper_globals::{name}"), tests));
                    if self.exported(decl) {
                        let accessor = self.accessor(decl, &ty, &name);
                        exported_variables.push(Exported {
                            slot: tree::spell_core(&slot),
                            name: name.clone(),
                            accessor: header_signature(&accessor),
                            shared: self.shared.contains(&decl),
                        });
                        accessors.push(Item::Function(accessor));
                    }
                    globals.push((decl, slot, name, tests));
                } else if !state.initialized_at_top.contains(&decl) {
                    // Assigned only inside the init block's blocks, so
                    // declared first.
                    let local = Stmt::Local {
                        ty,
                        name,
                        value: None,
                    };
                    init_locals.push((local, tests));
                }
            }
        }
        // Functions and classes' members, the library's in source order,
        // then what only tests reach, in theirs; and the private
        // functions called before they're defined, which need prototypes.
        let mut functions: Vec<Item> = Vec::new();
        let mut exported_functions = Vec::new();
        // `@keepTest` functions' prototypes, which the header declares
        // only where `TEMPER_TESTS` is defined.
        let mut kept_for_tests = Vec::new();
        let mut defined: HashSet<DeclId> = HashSet::new();
        let mut early: Vec<DeclId> = Vec::new();
        // Classes' and interfaces' structs, the library's and the tests'.
        let mut structs = Vec::new();
        let mut test_structs = Vec::new();
        // Exported templates and generic classes' members, defined in the
        // header, where others may instantiate them, each with whether
        // only tests reach it.
        let mut templates: Vec<(tree::Function, bool)> = Vec::new();
        // What the module's items that give templates use: its storage
        // among them goes in the header too.
        let mut template_refs: HashSet<DeclId> = HashSet::new();
        // The private generic functions those templates call, which go in
        // the header too, where the templates can call them.
        let header_private = self.header_private_templates(module);
        // How many arguments each call gives, everywhere and in the
        // library's parts, so a private function's overloads that nothing
        // calls are left out, and those that only tests call are where
        // they are; a reference may be called with any (`usize::MAX`). A
        // class's members but those connected, which aren't written
        // (std's `Date`'s, whose bodies call `isLeapYear` and `padTo`).
        let mut arities: HashSet<(DeclId, usize)> = HashSet::new();
        let mut live_arities: HashSet<(DeclId, usize)> = HashSet::new();
        let library_roots = module.items.len() + 1;
        let roots = (module.items.iter().chain([&module.init]))
            .chain(&module.test_items)
            .chain(&module.test_init)
            .chain(&module.tests);
        for (i, &root) in roots.enumerate() {
            let mut found = Vec::new();
            match p.nodes.get(root) {
                Node::Class { members, .. } | Node::Interface { members, .. } => {
                    found.extend(p.nodes.list(members).iter().copied().filter(|&m| {
                        !temper_tmpl::qname::connected_member_node(
                            p,
                            m,
                            crate::connected::connects_std,
                        )
                    }))
                }
                _ => found.push(root),
            }
            for root in found {
                temper_tmpl::analysis::walk(p, root, &mut |id| {
                    let called = match p.nodes.get(id) {
                        Node::CallFunction { callee, args, .. } => {
                            let given = (p.nodes.list(args).iter())
                                .rposition(|&a| !matches!(p.nodes.get(a), Node::Omitted {}))
                                .map_or(0, |i| i + 1);
                            (callee, given)
                        }
                        Node::FunctionRef { target } => (target, usize::MAX),
                        _ => return,
                    };
                    arities.insert(called);
                    if i < library_roots {
                        live_arities.insert(called);
                    }
                });
            }
        }
        // Host code (`_connected.hpp` beside the module's source, and the
        // `.cpp`s the library has), which the `.cpp` includes where it
        // takes a function or class from it.
        let host = (p.host.files.iter()).any(|f| f.path == host_header_path(module));
        let mut host_classes = Vec::new();
        let mut host_used = false;
        // The module's lock and its helpers, if the library's parts use
        // them, found after those parts are translated.
        let mut library_locks = None;
        for &(item, tests) in &parts {
            if tests && library_locks.is_none() {
                library_locks = Some(self.locks());
            }
            self.tests_only = tests;
            let node = p.nodes.get(item);
            if let Node::Function { decl, .. } | Node::BodilessFunction { decl, .. } = node {
                defined.insert(decl);
            }
            if let Node::Function { .. } | Node::Class { .. } | Node::Interface { .. } = node {
                temper_tmpl::analysis::walk(p, item, &mut |id| {
                    if let Node::CallFunction { callee, .. }
                    | Node::FunctionRef { target: callee } = p.nodes.get(id)
                    {
                        let owner = p.decls.get(callee).owner;
                        let member = p.decls.contains(owner)
                            && matches!(
                                p.decls.get(owner).kind,
                                DeclKind::Class | DeclKind::Interface
                            );
                        if !is_external(p, callee)
                            && !member
                            && !defined.contains(&callee)
                            && !early.contains(&callee)
                        {
                            early.push(callee);
                        }
                    }
                });
            }
            let from_host = match node {
                Node::Function { decl, .. }
                | Node::BodilessFunction { decl, .. }
                | Node::Class { decl, .. } => uses_host(p, decl, NAMES),
                _ => false,
            };
            if let (true, Some(decl)) = (from_host, node_decl(node)) {
                if !host {
                    return not_yet(format!(
                        "{}'s host code, with no {}",
                        p.name(decl),
                        host_header_path(module)
                    ));
                }
                host_used = true;
            }
            match node {
                // A user's connected class that host code defines, which
                // the header names, as Kotlin's: `namespace _connected {
                // struct Counter; }`, then `using Counter =
                // _connected::Counter;`.
                Node::Class { decl, .. } if from_host => {
                    if !p.decls.type_params(decl).is_empty() {
                        return not_yet(format!("a generic connected class, {}", p.name(decl)));
                    }
                    host_classes.push((p.name(decl).to_string(), self.name(decl)));
                }
                Node::Function { decl, params, .. } | Node::BodilessFunction { decl, params }
                    if from_host || matches!(node, Node::Function { .. }) =>
                {
                    let mut f = match from_host {
                        true => self.host_wrapper(item)?,
                        false => self.function(item)?,
                    };
                    // `@keep`: for host code, which calls it by its
                    // name, so declared in the header, as Kotlin declares
                    // every function; `@keepTest`: for tests' host code,
                    // so the same, but where the tests are, unless the
                    // library uses it too.
                    let facts = p.decls.get(decl).facts;
                    if facts.contains(Facts::KEEP) || facts.contains(Facts::KEEP_TEST) {
                        f.exported = true;
                    }
                    let k = trailing_optional(p, params);
                    let mut made = vec![(f.clone(), false)];
                    let any = arities.contains(&(decl, usize::MAX));
                    let live_any = live_arities.contains(&(decl, usize::MAX));
                    made.extend(
                        (overloads(&f, k).into_iter())
                            .filter(|o| {
                                f.exported || any || arities.contains(&(decl, o.params.len()))
                            })
                            .map(|o| {
                                // One that only tests call, of a function that
                                // others call with other arguments.
                                let n = o.params.len();
                                let tests =
                                    !f.exported && !live_any && !live_arities.contains(&(decl, n));
                                (o, tests)
                            }),
                    );
                    for (f, only) in made {
                        // An exported template, defined where others include,
                        // or a private one that one calls.
                        if (f.exported || header_private.contains(&decl)) && !f.template.is_empty()
                        {
                            template_refs.extend(module_refs(p, item));
                            templates.push((f, tests));
                            continue;
                        }
                        if f.exported && tests {
                            kept_for_tests.push(header_signature(&f));
                        } else if f.exported {
                            exported_functions.push(header_signature(&f));
                        }
                        functions.push(tests_only(Item::Function(f), only || tests));
                    }
                }
                Node::Class { decl, .. } | Node::Interface { decl, .. } => {
                    let class = self.class_item(item)?;
                    // A generic one's members, where it's defined.
                    if !p.decls.type_params(decl).is_empty() {
                        for i in class.items {
                            if let Item::Function(mut f) = i {
                                if f.template.is_empty() {
                                    f.template = self.template_line(decl);
                                }
                                template_refs.extend(module_refs(p, item));
                                templates.push((f, tests));
                            }
                        }
                    } else {
                        // A generic method, where it's declared: in the
                        // header if its class is exported.
                        let exported = self.exported(decl);
                        for i in class.items {
                            match i {
                                Item::Function(f) if exported && !f.template.is_empty() => {
                                    template_refs.extend(module_refs(p, item));
                                    templates.push((f, tests))
                                }
                                i => functions.push(tests_only(i, tests)),
                            }
                        }
                    }
                    match tests {
                        true => test_structs.push((decl, class.header)),
                        false => structs.push((decl, class.header)),
                    }
                }
                // std's, as `sendRequest`.
                Node::BodilessFunction { .. } => {
                    let f = self.bodiless(item)?;
                    if f.exported {
                        exported_functions.push(header_signature(&f));
                    }
                    functions.push(tests_only(Item::Function(f), tests));
                }
                _ => {}
            }
        }
        self.tests_only = false;
        let library_locks = library_locks.unwrap_or_else(|| self.locks());
        let mut test_functions = Vec::new();
        let mut test_prototypes = Vec::new();
        for &test in &module.tests {
            let f = self.function(test)?;
            test_prototypes.push(header_signature(&f));
            test_functions.push(f);
        }
        self.in_function = false;
        // The init block, and where the tests are, the test init, which
        // continues it.
        let blocks: Vec<NodeId> = [module.init].into_iter().chain(module.test_init).collect();
        self.begin(&blocks);
        self.locals = self.taken.clone();
        // Its storage constructed first, even before the modules it
        // imports run, so a read that comes back here while they do
        // finds a live value, as Kotlin's.
        let construct = |tests: bool| {
            (self.slots.iter())
                .filter(move |(_, t)| *t == tests)
                .map(|(slot, _)| {
                    Stmt::Expr(Expr::name(format!("{slot}.construct")).call(Vec::new()))
                })
                .collect::<Vec<Stmt>>()
        };
        let mut init = construct(false);
        let mut test_init = construct(true);
        for (local, tests) in init_locals {
            match tests {
                true => test_init.push(local),
                false => init.push(local),
            }
        }
        // Then the modules it imports, each once.
        let imports = module_imports(p, module);
        for import in &imports {
            init.push(init_call(p, import.from));
        }
        self.init_stmts(module.init, &mut init)?;
        // Then, where the tests are, what only they reach: the modules
        // only it imports, and the test init.
        if let Some(block) = module.test_init {
            for import in analysis::imports(p, module.decl, &[block]) {
                if !imports.iter().any(|i| i.from == import.from) {
                    test_init.push(init_call(p, import.from));
                }
            }
            self.init_stmts(block, &mut test_init)?;
        }
        let library_init = !init.is_empty();
        if !test_init.is_empty() {
            init.push(Stmt::TestsOnly(test_init));
        }
        let init = reachable(init);
        // The private functions that templates in the header call: not
        // `static`, and declared there, where the templates can call
        // them; where only tests reach them, where the tests are. Their
        // names are the library's alone ([`names::RULES`]).
        let header_called: HashSet<String> = (module.all_items())
            .filter_map(|item| match p.nodes.get(item) {
                Node::Function { decl, .. } => Some(decl),
                _ => None,
            })
            .filter(|d| {
                template_refs.contains(d)
                    && p.decls.type_params(*d).is_empty()
                    && !self.exported(*d)
            })
            .map(|d| self.name(d))
            .collect();
        let mut test_header_functions = Vec::new();
        for item in &mut functions {
            let (f, tests) = match item {
                Item::Function(f) => (f, false),
                Item::TestsOnly(inner) => match &mut **inner {
                    Item::Function(f) => (f, true),
                    _ => continue,
                },
                _ => continue,
            };
            if !f.exported && f.template.is_empty() && header_called.contains(&f.name) {
                f.exported = true;
                match tests {
                    true => test_header_functions.push(header_signature(f)),
                    false => exported_functions.push(header_signature(f)),
                }
            }
        }
        // Entry points run the init first, if the library's has anything
        // to run; `test.cpp` runs it before the tests.
        if library_init {
            let call = Stmt::Expr(Expr::name(init_name(p, module.decl)).call(Vec::new()));
            let entries = (functions.iter_mut().chain(&mut accessors))
                .filter_map(|i| match i {
                    Item::Function(f) => Some(f),
                    _ => None,
                })
                .chain(templates.iter_mut().map(|(f, _)| f))
                .filter(|f| f.entry);
            for f in entries {
                f.body.insert(0, call.clone());
            }
        }
        let template_storage = |tests: bool| -> Vec<String> {
            (globals.iter())
                .filter(|(d, _, _, t)| {
                    *t == tests && !self.exported(*d) && template_refs.contains(d)
                })
                .map(|(_, slot, name, _)| format!("{} {name}", tree::spell_core(slot)))
                .collect()
        };
        let (template_storage, test_template_storage) =
            (template_storage(false), template_storage(true));
        let mut items = Vec::new();
        for tests in [false, true] {
            let mine: Vec<_> = globals.iter().filter(|g| g.3 == tests).collect();
            if mine.is_empty() {
                continue;
            }
            let mut block = String::from("namespace temper_globals {\n\n");
            for (decl, slot, name, _) in mine {
                let header = self.exported(*decl) || template_refs.contains(decl);
                let storage = if header { "" } else { "static " };
                block.push_str(&format!("{storage}{slot} {name};\n"));
            }
            block.push_str("\n}  // namespace temper_globals\n");
            items.push(tests_only(Item::Raw(block), tests));
        }
        // The module's lock and its helpers, where only tests use them,
        // where the tests are.
        if let Some(mutex) = &self.mutex {
            let (lock, load, store) = library_locks;
            items.push(tests_only(
                Item::Variable {
                    exported: false,
                    ty: "std::mutex".into(),
                    name: mutex.clone(),
                },
                !lock,
            ));
            if let Some(name) = &self.load {
                let helper = format!(
                    "// Reads a variable that functions write, under the module's lock.\n\
                     template <class T>\n\
                     static T {name}(T const &variable) {{\n  \
                       std::lock_guard<std::mutex> lock({mutex});\n  \
                       return variable;\n\
                     }}\n"
                );
                items.push(tests_only(Item::Raw(helper), !load));
            }
            if let Some(name) = &self.store {
                let helper = format!(
                    "// Writes a variable that functions write, under the module's lock.\n\
                     template <class T, class V>\n\
                     static void {name}(T &variable, V &&value) {{\n  \
                       std::lock_guard<std::mutex> lock({mutex});\n  \
                       variable = std::forward<V>(value);\n\
                     }}\n"
                );
                items.push(tests_only(Item::Raw(helper), !store));
            }
        }
        for decl in early {
            let found = functions.iter().find_map(|f| match f {
                Item::Function(f) if f.name == self.name(decl) => Some((f, false)),
                Item::TestsOnly(inner) => match &**inner {
                    Item::Function(f) if f.name == self.name(decl) => Some((f, true)),
                    _ => None,
                },
                _ => None,
            });
            if let Some((f, tests)) = found {
                if !f.exported {
                    items.push(tests_only(Item::Prototype(f.clone()), tests));
                }
            }
        }
        items.extend(accessors);
        items.extend(functions);
        let templates = |tests: bool| -> String {
            (templates.iter())
                .filter(|(_, t)| *t == tests)
                .map(|(f, _)| tree::header_definition(f) + "\n")
                .collect()
        };
        let (templates, test_templates) = (templates(false), templates(true));
        // The module's lock and its helpers are the `.cpp`'s alone.
        let locks = [&self.mutex, &self.load, &self.store];
        if (locks.iter().copied().flatten())
            .any(|name| mentions(&templates, name) || mentions(&test_templates, name))
        {
            return not_yet("a template in a header that takes its module's lock");
        }
        let mut header_includes = includes(p, &imports);
        if mentions_type(p) {
            header_includes.push("<typeindex>".into());
        }
        let test_includes: Vec<String> = includes(p, &test_imports(p, module))
            .into_iter()
            .filter(|i| !header_includes.contains(i))
            .collect();
        Ok(Translated {
            source: tree::Source {
                includes: Vec::new(),
                namespace: String::new(),
                aliases: vec![(CORE.into(), "temper::core".into())],
                items,
                init: String::new(),
                init_body: init,
                tests: test_functions,
                generated: None,
            },
            exported_variables,
            template_storage,
            uses_mutex: self.mutex.is_some(),
            exported_functions,
            header_includes,
            test_includes,
            test_prototypes,
            kept_for_tests,
            classes: host_class_names(&host_classes) + &classes::structs(p, structs),
            templates,
            tests: TestHeader {
                classes: classes::structs(p, test_structs),
                template_storage: test_template_storage,
                functions: test_header_functions,
                templates: test_templates,
            },
            host: host_used,
            runtime_includes: std::mem::take(&mut self.runtime_includes),
        })
    }

    /// Whether the module's lock, and its `load` and `store` helpers, are
    /// used so far.
    fn locks(&self) -> (bool, bool, bool) {
        (
            self.mutex.is_some(),
            self.load.is_some(),
            self.store.is_some(),
        )
    }

    /// An init block's statements, or its test init's, into `init`.
    fn init_stmts(&mut self, block: NodeId, init: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        let Node::Block { stmts } = p.nodes.get(block) else {
            return self.stmt(block, init);
        };
        for &s in p.nodes.list(stmts) {
            if matches!(p.nodes.get(s), Node::InitLet { target, .. } if !self.statics.contains(&target))
            {
                self.mark(s, init);
            }
            match p.nodes.get(s) {
                Node::InitLet { target, value }
                    if !self.statics.contains(&target)
                        && matches!(p.nodes.get(value), Node::OrElse { .. }) =>
                {
                    let ty = self.ty(p.decls.get(target).ty)?;
                    let name = self.name(target);
                    init.push(Stmt::Local {
                        ty,
                        name: name.clone(),
                        value: None,
                    });
                    init.extend(self.deliver(value, &Sink::Assign(name))?);
                }
                Node::InitLet { target, value } if !self.statics.contains(&target) => {
                    let ty = self.ty(p.decls.get(target).ty)?;
                    let value = self.expr(value)?;
                    init.append(&mut self.pending);
                    let name = self.name(target);
                    let unused = unused_warns(&ty) && !self.is_read(target);
                    init.push(Stmt::Local {
                        ty,
                        name: name.clone(),
                        value: Some(value),
                    });
                    if unused {
                        let cast = format!("static_cast<void>({name})");
                        init.push(Stmt::Expr(Expr::name(cast)));
                    }
                }
                _ => self.stmt(s, init)?,
            }
        }
        Ok(())
    }

    /// A function, a test, or a class's member with a body: a method,
    /// getter, setter, constructor (`make`), or static function, named
    /// with its class (`Rect::scaled`), in the class context
    /// [`class_item`](Self::class_item) sets.
    /// A module's private generic functions that its header's templates
    /// call, directly or through each other: exported generic functions'
    /// and generic classes' and exported classes' members.
    fn header_private_templates(&self, module: &Module) -> HashSet<DeclId> {
        let p = self.p;
        let generic_fn = |d: DeclId| {
            p.decls.contains(d)
                && p.decls.get(d).kind == DeclKind::Function
                && !p.decls.type_params(d).is_empty()
        };
        let mut nodes: HashMap<DeclId, NodeId> = HashMap::new();
        let mut stack = Vec::new();
        for item in module.all_items() {
            match p.nodes.get(item) {
                Node::Function { decl, .. } => {
                    nodes.insert(decl, item);
                    if self.exported(decl) && generic_fn(decl) {
                        stack.push(item);
                    }
                }
                Node::Class { decl, .. } | Node::Interface { decl, .. }
                    if !p.decls.type_params(decl).is_empty() || self.exported(decl) =>
                {
                    stack.push(item)
                }
                _ => {}
            }
        }
        let mut found = HashSet::new();
        while let Some(item) = stack.pop() {
            for d in module_refs(p, item) {
                if generic_fn(d) && !self.exported(d) && found.insert(d) {
                    stack.extend(nodes.get(&d).copied());
                }
            }
        }
        found
    }

    fn function(&mut self, item: NodeId) -> R<tree::Function> {
        let p = self.p;
        let (decl, params, body, test) = match p.nodes.get(item) {
            Node::Function { decl, params, body } | Node::Method { decl, params, body } => {
                (decl, params, body, false)
            }
            Node::Test {
                decl, params, body, ..
            } => (decl, params, body, true),
            _ => return not_yet("a function that isn't one"),
        };
        let kind = p.decls.get(decl).kind;
        let method = matches!(kind, DeclKind::Method | DeclKind::Getter | DeclKind::Setter)
            && self.class.is_some();
        let ctor = kind == DeclKind::Constructor;
        self.renamed.clear();
        self.locals = self.taken.clone();
        // A method's parameters and locals don't shadow its object's
        // fields, which `-Wshadow` would mention.
        let fields: HashSet<String> = match (&self.class, method) {
            (Some(c), true) => c.data.clone(),
            _ => HashSet::new(),
        };
        self.locals.extend(fields.iter().cloned());
        let mut clashing = Vec::new();
        temper_tmpl::analysis::walk(p, item, &mut |id| match p.nodes.get(id) {
            Node::Param { decl: d } | Node::Let { local: d, .. } => {
                let name = self.names.get(d).to_string();
                if fields.contains(&name) {
                    clashing.push(d);
                } else {
                    self.locals.insert(name);
                }
            }
            _ => {}
        });
        for d in clashing {
            let name = self.fresh_local(&format!("{}_", self.names.get(d)));
            self.renamed.insert(d, name);
        }
        let mut used = HashSet::new();
        let mut assigned = HashSet::new();
        let mut uses_this = false;
        temper_tmpl::analysis::walk(p, body, &mut |id| match p.nodes.get(id) {
            Node::Local { local } => {
                used.insert(local);
            }
            Node::Assign { local, .. } => {
                assigned.insert(local);
            }
            Node::This {} => uses_this = true,
            _ => {}
        });
        let mut ps = Vec::new();
        let mut unused = Vec::new();
        for (i, &param) in p.nodes.list(params).iter().enumerate() {
            if let Node::Param { decl } = p.nodes.get(param) {
                let mut ty = self.ty(p.decls.get(decl).ty)?;
                let mut name = self.name(decl);
                // A `shared_ptr`, `std::function`, or `core::Str` by
                // `const &`, as Kotlin passes one that's never assigned;
                // callers name what a call could change
                // ([`operands`](Self::operands)).
                let by_ref = indirect(&ty) && !self.by_value.get(i).copied().unwrap_or(false);
                if by_ref && !assigned.contains(&decl) {
                    ty.push_str(" const &");
                }
                if !used.contains(&decl) {
                    if test {
                        // The test's `Test`, as Kotlin passes it, unnamed.
                        name.clear();
                    } else {
                        unused.push(ps.len());
                    }
                }
                ps.push((ty, name));
            }
        }
        let result = match p.signature(decl) {
            Some(sig) => sig.result,
            None => TypeId::VOID,
        };
        let result_ty = self.ty(result)?;
        self.in_function = true;
        self.begin(&[item]);
        // What `this` is: in a constructor, the object it makes; in a
        // method, `this`, or a `shared_ptr` that holds it where the method
        // gives it out or could drop the last other one.
        let mut prelude = Vec::new();
        self.in_ctor = ctor;
        self.this_expr = None;
        let constant = method && !self.mutating.contains(&self.name(decl));
        if let Some(class) = self.class.as_ref().map(|c| c.decl) {
            let class_name = self.class_ref(class);
            let generic = !p.decls.type_params(class).is_empty();
            if ctor {
                let name = self.fresh_local("self");
                prelude.push(Stmt::Local {
                    ty: format!("std::shared_ptr<{class_name}>"),
                    name: name.clone(),
                    value: Some(Expr::name(format!("std::make_shared<{class_name}>")).call(vec![])),
                });
                self.this_expr = Some(Expr::name(name));
            } else if method && uses_this {
                if classes::holds_this(p, body) {
                    let name = self.fresh_local("self");
                    let pointee = match constant {
                        true => format!("{class_name} const"),
                        false => class_name.clone(),
                    };
                    let rooted = !p.decls.supers(class).is_empty()
                        || p.decls.get(class).kind == DeclKind::Interface;
                    // A generic class's base depends on its parameters.
                    let from = match generic {
                        true => Expr::name("this->shared_from_this").call(vec![]),
                        false => Expr::name("shared_from_this").call(vec![]),
                    };
                    prelude.push(Stmt::Local {
                        ty: format!("std::shared_ptr<{pointee}>"),
                        name: name.clone(),
                        // `AnyValueBase` holds the count, so the pointer
                        // shares it, without a `dynamic_cast`.
                        value: Some(match rooted {
                            true => Expr::name(format!("std::shared_ptr<{pointee}>"))
                                .call(vec![from, Expr::name("this")]),
                            false => from,
                        }),
                    });
                    self.this_expr = Some(Expr::name(name));
                } else {
                    self.this_expr = Some(Expr::name("this"));
                }
            }
        }
        let mut stmts = reachable(self.stmts(body)?);
        // A `return` without a value that ends a function says nothing.
        if result == TypeId::VOID && stmts.last() == Some(&Stmt::Return(None)) {
            stmts.pop();
        }
        if ctor && stmts.last().is_none_or(completes) {
            let this = self.this_expr.clone().unwrap_or(Expr::name("this"));
            stmts.push(Stmt::Return(Some(this)));
        }
        prelude.append(&mut stmts);
        let stmts = prelude;
        self.in_ctor = false;
        self.this_expr = None;
        let generic_class =
            (self.class.as_ref()).is_some_and(|c| !p.decls.type_params(c.decl).is_empty());
        let name = match self.test_names.get(&decl) {
            Some(name) if test => name.clone(),
            _ if generic_class => {
                let c = self.class.as_ref().map(|c| c.decl).unwrap_or(DeclId::NONE);
                let bare = match kind {
                    DeclKind::Constructor => "make".to_string(),
                    _ => self.name(decl),
                };
                format!("{}::{bare}", self.class_ref(c))
            }
            _ if self.class.is_some() => self.top_name(decl),
            _ => self.name(decl),
        };
        let name = match (kind, &self.class) {
            (DeclKind::Getter | DeclKind::Setter, Some(c)) => {
                let prefix = if kind == DeclKind::Getter {
                    "get_"
                } else {
                    "set_"
                };
                format!("{}::{prefix}{}", self.class_ref(c.decl), self.name(decl))
            }
            (DeclKind::Method, Some(c)) => {
                format!("{}::{}", self.class_ref(c.decl), self.name(decl))
            }
            _ => name,
        };
        // A generic function's, a generic class's member's, or a generic
        // method's, after its class's, if that's generic too. C++ has no
        // virtual templates.
        let template = match &self.class {
            Some(c) if !p.decls.type_params(decl).is_empty() => {
                if p.decls.get(c.decl).kind == DeclKind::Interface {
                    return not_yet("a generic method of an interface");
                }
                let outer = self.template_line(c.decl);
                let inner = self.template_line(decl);
                match outer.is_empty() {
                    true => inner,
                    false => format!("{outer}\n{inner}"),
                }
            }
            Some(c) => self.template_line(c.decl),
            None => self.template_line(decl),
        };
        self.renamed.clear();
        let entry = match &self.class {
            None => !test && self.exported(decl),
            Some(c) => {
                matches!(kind, DeclKind::Constructor | DeclKind::Function)
                    && self.exported(c.decl)
                    && !p.decls.get(decl).facts.contains(Facts::PRIVATE)
            }
        };
        Ok(tree::Function {
            template,
            exported: test || self.exported(decl) || self.class.is_some(),
            result: result_ty,
            name,
            params: ps,
            unused,
            constant,
            entry,
            body: stmts,
        })
    }

    /// A function whose host code runs instead of its Temper body, as
    /// Kotlin's: its parameters' defaults, then a call of host code's
    /// function of its name, `return _connected::sum(i, j, bonus);`,
    /// passing each parameter, defaulted. A bodiless one's parameters are
    /// only those.
    /// std's bodiless connected function, which returns a promise: a
    /// call of the runtime's template for it, given the class the promise
    /// is of, where [`crate::connected::BODILESS`] has one (std `net`'s
    /// `sendRequest`, `return core::net::sendRequest<NetResponse>(url,
    /// …);`), or else a promise, broken, as std says a request a backend
    /// can't send gives. Kotlin's C++ has no `net` at all.
    fn bodiless(&mut self, item: NodeId) -> R<tree::Function> {
        let p = self.p;
        let Node::BodilessFunction { decl, params } = p.nodes.get(item) else {
            return not_yet("a bodiless function that isn't one");
        };
        let result = p.signature(decl).map_or(TypeId::VOID, |sig| sig.result);
        let promised = match (
            temper_tmpl::analysis::nominal_decl(p, result),
            p.types.args(result),
        ) {
            (Some(d), [t]) if self.qname(d) == "core.type Promise" => *t,
            _ => return not_yet(format!("a connected function, {}", p.name(decl))),
        };
        let mut ps = Vec::new();
        for &param in p.nodes.list(params) {
            if let Node::Param { decl: param } = p.nodes.get(param) {
                let mut ty = self.ty(p.decls.get(param).ty)?;
                if indirect(&ty) {
                    ty.push_str(" const &");
                }
                ps.push((ty, self.name(param)));
            }
        }
        let runtime = crate::connected::bodiless(&self.qname(decl));
        let class = temper_tmpl::analysis::nominal_decl(p, promised);
        if let (Some((template, header)), Some(class)) = (runtime, class) {
            if !self.runtime_includes.iter().any(|h| h == header) {
                self.runtime_includes.push(header.to_string());
            }
            let class = self.top_name(class);
            let args = ps
                .iter()
                .map(|(_, name)| Expr::name(name.clone()))
                .collect();
            let call = self.core(&format!("{template}<{class}>")).call(args);
            let exported = self.exported(decl);
            return Ok(tree::Function {
                template: String::new(),
                exported,
                result: self.ty(result)?,
                name: self.name(decl),
                unused: Vec::new(),
                params: ps,
                constant: false,
                entry: exported,
                body: vec![Stmt::Return(Some(call))],
            });
        }
        let builder = Expr::name("builder");
        let made = Expr::name(format!(
            "core::PromiseBuilderNs::make<{}>",
            self.ty(promised)?
        ));
        let body = vec![
            Stmt::Local {
                ty: "auto".into(),
                name: "builder".into(),
                value: Some(made.call(Vec::new())),
            },
            Stmt::Expr(Expr::name("core::breakpromise").call(vec![builder.clone()])),
            Stmt::Return(Some(Expr::name("core::getpromise").call(vec![builder]))),
        ];
        let exported = self.exported(decl);
        Ok(tree::Function {
            template: String::new(),
            exported,
            result: self.ty(result)?,
            name: self.name(decl),
            unused: (0..ps.len()).collect(),
            params: ps,
            constant: false,
            entry: exported,
            body,
        })
    }

    fn host_wrapper(&mut self, item: NodeId) -> R<tree::Function> {
        let p = self.p;
        let (decl, params, defaults) = match p.nodes.get(item) {
            Node::Function { decl, params, body } => {
                (decl, params, temper_tmpl::analysis::defaults(p, body))
            }
            Node::BodilessFunction { decl, params } => (decl, params, Vec::new()),
            _ => return not_yet("a connected function that isn't one"),
        };
        if !p.decls.type_params(decl).is_empty() {
            return not_yet(format!("a generic connected function, {}", p.name(decl)));
        }
        self.renamed.clear();
        self.locals = self.taken.clone();
        temper_tmpl::analysis::walk(p, item, &mut |id| {
            if let Node::Param { decl: d } | Node::Let { local: d, .. } = p.nodes.get(id) {
                self.locals.insert(self.names.get(d).to_string());
            }
        });
        let mut ps = Vec::new();
        let mut args = Vec::new();
        for &param in p.nodes.list(params) {
            if let Node::Param { decl: param } = p.nodes.get(param) {
                let mut ty = self.ty(p.decls.get(param).ty)?;
                if indirect(&ty) {
                    ty.push_str(" const &");
                }
                ps.push((ty, self.name(param)));
                let local = defaults.iter().find(|d| d.param == param);
                args.push(local.map_or(param, |d| d.local));
            }
        }
        let result = p.signature(decl).map_or(TypeId::VOID, |sig| sig.result);
        let result_ty = self.ty(result)?;
        self.in_function = true;
        self.begin(&[item]);
        let mut body = Vec::new();
        for d in &defaults {
            self.stmt(d.stmt, &mut body)?;
        }
        let args = args.into_iter().map(|a| Expr::name(self.name(a))).collect();
        let call = Expr::name(format!("_connected::{}", self.name(decl))).call(args);
        body.push(match result == TypeId::VOID {
            true => Stmt::Expr(call),
            false => Stmt::Return(Some(call)),
        });
        self.renamed.clear();
        let exported = self.exported(decl);
        Ok(tree::Function {
            template: String::new(),
            exported,
            result: result_ty,
            name: self.name(decl),
            params: ps,
            unused: Vec::new(),
            constant: false,
            entry: exported,
            body,
        })
    }

    /// Starts translating a function or the init block: plans which loops
    /// need labels, those that a jump from an inner loop leaves or
    /// continues. A label takes its loop's source name, or for a
    /// compiler-made one, `outer`, then `End` for a `break` and `Next` for
    /// a `continue`, numbered only if another label has it.
    fn begin(&mut self, scopes: &[NodeId]) {
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
                // A `break` in a `switch` leaves the switch, so one inside
                // leaves a loop around it by a label; a `continue` doesn't.
                Node::Switch { .. } => {
                    loops.push(DeclId::NONE);
                    for &kid in p.nodes.kids(id) {
                        visit(p, kid, loops, jumps);
                    }
                    loops.pop();
                }
                Node::Break { label } | Node::Continue { label } => {
                    let brk = matches!(p.nodes.get(id), Node::Break { .. });
                    if innermost(loops, brk) != Some(label) && !jumps.contains(&(label, brk)) {
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
        self.hoisted.clear();
        self.let_names.clear();
        let mut jumps = Vec::new();
        for &scope in scopes {
            temper_tmpl::analysis::walk(p, scope, &mut |id| {
                if let Node::Let { local, .. } = p.nodes.get(id) {
                    *self
                        .let_names
                        .entry(self.names.get(local).to_string())
                        .or_default() += 1;
                }
            });
            visit(p, scope, &mut Vec::new(), &mut jumps);
        }
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

    fn stmts(&mut self, id: NodeId) -> R<Vec<Stmt>> {
        let p = self.p;
        let list: Vec<NodeId> = match p.nodes.get(id) {
            Node::Block { stmts } => p.nodes.list(stmts).to_vec(),
            _ => vec![id],
        };
        let mut out = Vec::new();
        let groups = temper_tmpl::analysis::function_groups(p, &list);
        let mut i = 0;
        while i < list.len() {
            if let Some(g) = groups.iter().find(|g| g.lets.contains(&list[i])) {
                if g.lets[0] == list[i] {
                    self.group(g, &list[i + 1..], &mut out)?;
                }
                i += 1;
                continue;
            }
            // A run of statements that use shared state and call nothing
            // runs under one lock.
            let run = if self.in_function && !self.locked {
                self.lock_run(&list[i..])
            } else {
                0
            };
            if run == 0 {
                self.stmt(list[i], &mut out)?;
                i += 1;
                continue;
            }
            let mutex = self.mutex();
            let guard = Stmt::Guard {
                name: self.fresh_local("lock"),
                mutex,
            };
            let mut locked = vec![guard];
            self.locked = true;
            for &s in &list[i..i + run] {
                self.stmt(s, &mut locked)?;
            }
            self.locked = false;
            i += run;
            if i == list.len() {
                out.append(&mut locked);
            } else {
                out.push(Stmt::Block(locked));
            }
        }
        Ok(reachable(out))
    }

    /// How many statements at the start of `list` run under one lock: a
    /// run that calls nothing, of assignments, `return`s, and, if the run
    /// reaches the end of its block, locals' declarations, at least one
    /// of which reads or writes a module `var` that functions write;
    /// none if not.
    fn lock_run(&self, list: &[NodeId]) -> usize {
        let p = self.p;
        let shared = |id: NodeId| {
            let mut found = false;
            temper_tmpl::analysis::walk(p, id, &mut |n| match p.nodes.get(n) {
                Node::GetModuleLet { target } | Node::SetModuleLet { target, .. } => {
                    found |= self.shared.contains(&target);
                }
                _ => {}
            });
            found
        };
        let mut run = 0;
        let mut uses = false;
        let mut locals = false;
        for &s in list {
            let fits = match p.nodes.get(s) {
                Node::Assign { value, .. } | Node::SetModuleLet { value, .. } => {
                    calls_nothing(p, value)
                }
                Node::Let { init, .. } => {
                    locals = true;
                    init.is_none_or(|v| calls_nothing(p, v))
                }
                Node::Return { value } => value.is_none_or(|v| calls_nothing(p, v)),
                _ => false,
            };
            if !fits {
                break;
            }
            uses |= shared(s);
            run += 1;
            if let Node::Return { .. } = p.nodes.get(s) {
                break;
            }
        }
        // Trailing statements that don't use shared state stay outside.
        while run > 0 && !shared(list[run - 1]) && run < list.len() {
            run -= 1;
        }
        if run == 0 {
            return 0;
        }
        let to_end = run == list.len() || matches!(p.nodes.get(list[run - 1]), Node::Return { .. });
        if !uses || (locals && !to_end) { 0 } else { run }
    }

    /// Whether anything reads a local.
    fn is_read(&mut self, local: DeclId) -> bool {
        let p = self.p;
        let read = self.read_locals.get_or_insert_with(|| {
            (0..p.nodes.len())
                .filter_map(|i| match p.nodes.get(NodeId(i as u32)) {
                    Node::Local { local } | Node::GetModuleLet { target: local } => Some(local),
                    _ => None,
                })
                .collect()
        });
        read.contains(&local)
    }

    /// A name for a local the translator adds, the first of `base`,
    /// `base2`, … that nothing in its function takes.
    fn fresh_local(&mut self, base: &str) -> String {
        let name = (1..)
            .map(|n| match n {
                1 => base.to_string(),
                n => format!("{base}{n}"),
            })
            .find(|n| !self.locals.contains(n))
            .unwrap_or_default();
        self.locals.insert(name.clone());
        name
    }

    /// A local's name where it's declared: a `BlockValue`'s, moved into
    /// the statement around it, takes a fresh name where another local
    /// could have its own.
    fn local_name(&mut self, local: DeclId) -> String {
        let name = self.name(local);
        if !self.in_hoist {
            return name;
        }
        let alone = self.let_names.get(&name).copied().unwrap_or(0) <= 1;
        if alone && self.hoisted.insert(name.clone()) {
            return name;
        }
        let fresh = self.fresh_local(&name);
        self.renamed.insert(local, fresh.clone());
        fresh
    }

    /// Statements that compute `id` and give its value to `sink`: an
    /// `orelse` as `try`/`catch`, each side giving its value to the sink,
    /// a `BlockValue`'s statements before its value, and anything else
    /// with the temporaries it needs first.
    fn deliver(&mut self, id: NodeId, sink: &Sink) -> R<Vec<Stmt>> {
        let outer = std::mem::take(&mut self.pending);
        let result = self.deliver_in(id, sink);
        self.pending = outer;
        let mut out = result?;
        // Nothing runs after a `return` or a bubble.
        if let Some(i) = out.iter().position(|s| !completes(s)) {
            out.truncate(i + 1);
        }
        Ok(out)
    }

    fn deliver_in(&mut self, id: NodeId, sink: &Sink) -> R<Vec<Stmt>> {
        let p = self.p;
        let mut out = Vec::new();
        match p.nodes.get(id) {
            Node::OrElse { body, fallback } => {
                let body = self.deliver(body, sink)?;
                let fallback = self.deliver(fallback, sink)?;
                out.push(Stmt::Try { body, fallback });
            }
            Node::BlockValue { stmts, value } => {
                for &s in p.nodes.list(stmts) {
                    self.stmt(s, &mut out)?;
                }
                out.extend(self.deliver(value, sink)?);
            }
            Node::Bubble { .. } | Node::Error { .. } => {
                out.push(Stmt::Bubble(self.core("bubble").call(Vec::new())));
            }
            Node::Panic {} => out.push(Stmt::Bubble(self.core("panic").call(Vec::new()))),
            // `void`, as an `orelse`'s fallback, does nothing.
            Node::VoidLit {} if matches!(sink, Sink::Discard) => {}
            _ => {
                let e = self.expr(id)?;
                out.append(&mut self.pending);
                match sink {
                    Sink::Return if p.nodes.ty(id) == TypeId::VOID => {
                        if e.is_statement() {
                            out.push(Stmt::Expr(e));
                        }
                        out.push(Stmt::Return(None));
                    }
                    Sink::Return => out.push(Stmt::Return(Some(e))),
                    Sink::Discard => {
                        if e.is_statement() {
                            out.push(Stmt::Expr(e));
                        }
                    }
                    Sink::Assign(target) => out.push(Stmt::Assign {
                        target: target.clone(),
                        value: e,
                    }),
                }
            }
        }
        Ok(out)
    }

    /// A value that needs statements, an `orelse` or a `BlockValue`, as an
    /// expression: the statements go before the statement it's in, the
    /// value into a temporary, where nothing must run before it; else a
    /// lambda called at once.
    fn statement_value(&mut self, id: NodeId) -> R<Expr> {
        let p = self.p;
        let ty = self.ty(p.nodes.ty(id))?;
        if self.deferred > 0 {
            let in_hoist = std::mem::replace(&mut self.in_hoist, false);
            let body = self.deliver(id, &Sink::Return);
            self.in_hoist = in_hoist;
            return Ok(Expr::Run {
                result: ty,
                body: body?,
            });
        }
        if let Node::BlockValue { stmts, value } = p.nodes.get(id) {
            let in_hoist = std::mem::replace(&mut self.in_hoist, true);
            let mut out = Vec::new();
            let mut result = Ok(());
            for &s in p.nodes.list(stmts) {
                result = self.stmt(s, &mut out);
                if result.is_err() {
                    break;
                }
            }
            self.in_hoist = in_hoist;
            result?;
            self.pending.append(&mut out);
            return self.expr(value);
        }
        let name = self.fresh_local("value");
        self.pending.push(Stmt::Local {
            ty,
            name: name.clone(),
            value: None,
        });
        let stmts = self.deliver(id, &Sink::Assign(name.clone()))?;
        self.pending.extend(stmts);
        Ok(Expr::Name(name))
    }

    /// Whether computing `id` puts statements before the statement it's
    /// in ([`statement_value`](Self::statement_value)).
    fn needs_statements(&self, id: NodeId) -> bool {
        let p = self.p;
        let mut found = false;
        temper_tmpl::analysis::walk(p, id, &mut |n| {
            found |= matches!(
                p.nodes.get(n),
                Node::OrElse { .. } | Node::BlockValue { .. }
            );
        });
        found
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

    /// A condition evaluated again and again, where no temporary can go.
    fn condition(&mut self, cond: NodeId) -> R<Expr> {
        self.deferred += 1;
        let cond = self.expr(cond);
        self.deferred -= 1;
        cond
    }

    /// Where a node's code comes from, as a `#line` directive before it
    /// says, with [`lines`](Self::lines) on.
    fn mark(&self, id: NodeId, out: &mut Vec<Stmt>) {
        let Some(prefix) = &self.lines else {
            return;
        };
        let at = temper_tmpl::line_directives::line_at(self.p, prefix, self.p.nodes.span(id));
        out.extend(at.map(Stmt::Line));
    }

    /// Translates a statement, after where it comes from
    /// ([`mark`](Self::mark)), unless it comes to nothing.
    fn stmt(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let before = out.len();
        self.mark(id, out);
        let marked = out.len();
        self.stmt_unmarked(id, out)?;
        if out.len() == marked {
            out.truncate(before);
        }
        Ok(())
    }

    fn stmt_unmarked(&mut self, id: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        let s = match p.nodes.get(id) {
            Node::Block { .. } => Stmt::Block(self.stmts(id)?),
            Node::Let {
                local,
                init: Some(init),
            } if matches!(p.nodes.get(init), Node::OrElse { .. }) => {
                let ty = self.ty(p.decls.get(local).ty)?;
                let name = self.local_name(local);
                out.push(Stmt::Local {
                    ty,
                    name: name.clone(),
                    value: None,
                });
                out.extend(self.deliver(init, &Sink::Assign(name))?);
                return Ok(());
            }
            // A parameter's null default: the parameter itself.
            Node::Let {
                local,
                init: Some(init),
            } if null_default(p, init).is_some() && !self.is_cell(local) => {
                let param = null_default(p, init).unwrap();
                self.renamed.insert(local, self.name(param));
                return Ok(());
            }
            // A default with statements: `if (x == nullptr) { … } else …`.
            Node::Let {
                local,
                init: Some(init),
            } if !self.is_cell(local)
                && matches!(p.nodes.get(init), Node::Coalesce { right, .. }
                    if matches!(p.nodes.get(right), Node::BlockValue { .. } | Node::OrElse { .. })) =>
            {
                let Node::Coalesce { left, right } = p.nodes.get(init) else {
                    unreachable!()
                };
                let ty = self.ty(p.decls.get(local).ty)?;
                let name = self.local_name(local);
                out.push(Stmt::Local {
                    ty,
                    name: name.clone(),
                    value: None,
                });
                let then = self.deliver(right, &Sink::Assign(name.clone()))?;
                let cond = Expr::bin(Bin::Eq, self.expr(left)?, Expr::name("nullptr"));
                let given = self.coalesced(left, p.nodes.ty(init))?;
                out.push(Stmt::If {
                    cond,
                    then,
                    els: vec![Stmt::Assign {
                        target: name,
                        value: given,
                    }],
                });
                return Ok(());
            }
            // A cell: a `shared_ptr` that lambdas copy.
            Node::Let { local, init } if self.is_cell(local) => {
                let ty = self.ty(p.decls.get(local).ty)?;
                let value = match init {
                    Some(v) => vec![self.expr(v)?],
                    None => Vec::new(),
                };
                Stmt::Local {
                    ty: format!("std::shared_ptr<{ty}>"),
                    name: self.local_name(local),
                    value: Some(Expr::name(format!("std::make_shared<{ty}>")).call(value)),
                }
            }
            Node::Let { local, init } => {
                let ty = self.ty(p.decls.get(local).ty)?;
                let value = init.map(|v| self.expr(v)).transpose()?;
                let name = self.local_name(local);
                out.append(&mut self.pending);
                // A scalar local nothing reads (std's json
                // `afterExponent`), which `-Wunused-variable` mentions, as
                // it doesn't a class's with a destructor; C++14 has no
                // `[[maybe_unused]]`.
                let scalar = unused_warns(&ty);
                out.push(Stmt::Local {
                    ty,
                    name: name.clone(),
                    value,
                });
                if scalar && !self.is_read(local) {
                    out.push(Stmt::Expr(Expr::name(format!("static_cast<void>({name})"))));
                }
                return Ok(());
            }
            Node::Assign {
                local: target,
                value,
            }
            | Node::InitLet { target, value }
                if matches!(p.nodes.get(value), Node::OrElse { .. }) =>
            {
                let target = match p.decls.get(target).kind {
                    DeclKind::ModuleLet => self.top_name(target),
                    _ => self.name(target),
                };
                out.extend(self.deliver(value, &Sink::Assign(target))?);
                return Ok(());
            }
            Node::SetModuleLet { target, value }
                if self.in_function && !self.locked && self.shared.contains(&target) =>
            {
                let value = self.expr(value)?;
                if self.store.is_none() {
                    self.mutex();
                    self.store = Some(self.fresh_module_name("store"));
                }
                let store = self.store.clone().unwrap_or_default();
                Stmt::Expr(Expr::name(store).call(vec![self.top(target), value]))
            }
            Node::Assign {
                local: target,
                value,
            }
            | Node::SetModuleLet { target, value }
            | Node::InitLet { target, value } => {
                let value = self.expr(value)?;
                let target = match p.decls.get(target).kind {
                    DeclKind::ModuleLet => self.top_name(target),
                    _ if self.is_cell(target) => format!("*{}", self.name(target)),
                    _ => self.name(target),
                };
                // `x = x` does nothing.
                if value == Expr::Name(target.clone()) {
                    out.append(&mut self.pending);
                    return Ok(());
                }
                Stmt::Assign { target, value }
            }
            Node::If { cond, then, els } => {
                let cond = self.expr(cond)?;
                let hoisted = std::mem::take(&mut self.pending);
                let then = self.stmts(then)?;
                let els = match els {
                    Some(els) => self.stmts(els)?,
                    None => Vec::new(),
                };
                self.pending = hoisted;
                Stmt::If { cond, then, els }
            }
            Node::Switch { scrutinee, cases } => {
                let scrutinee = self.expr(scrutinee)?;
                let hoisted = std::mem::take(&mut self.pending);
                self.loops.push(DeclId::NONE);
                let made = self.cases(cases);
                self.loops.pop();
                self.pending = hoisted;
                Stmt::Switch {
                    scrutinee,
                    cases: made?,
                }
            }
            Node::While { label, cond, body } => {
                let cond = self.condition(cond)?;
                let body = self.loop_body(label, body)?;
                Stmt::While {
                    cond,
                    body,
                    labels: self.labels.get(&label).cloned().unwrap_or_default(),
                }
            }
            Node::DoWhile { label, body, cond } => {
                let body = self.loop_body(label, body)?;
                let cond = self.condition(cond)?;
                Stmt::DoWhile {
                    body,
                    cond,
                    labels: self.labels.get(&label).cloned().unwrap_or_default(),
                }
            }
            Node::Break { label } | Node::Continue { label } => {
                let brk = matches!(p.nodes.get(id), Node::Break { .. });
                if innermost(&self.loops, brk) == Some(label) {
                    if brk { Stmt::Break } else { Stmt::Continue }
                } else {
                    let labels = self.labels.get(&label);
                    let target = if brk {
                        labels.and_then(|l| l.end.clone())
                    } else {
                        labels.and_then(|l| l.next.clone())
                    };
                    match target {
                        Some(l) => Stmt::Goto(l),
                        None => return not_yet("a jump to a loop that doesn't enclose it"),
                    }
                }
            }
            Node::Return { value } => match value.map(|v| (v, p.nodes.get(v))) {
                // A constructor gives the object it made.
                None | Some((_, Node::VoidLit {})) if self.in_ctor => {
                    Stmt::Return(self.this_expr.clone())
                }
                None | Some((_, Node::VoidLit {})) => Stmt::Return(None),
                Some((_, Node::Error { .. } | Node::Bubble { .. })) => {
                    Stmt::Bubble(self.core("bubble").call(Vec::new()))
                }
                Some((_, Node::Panic {})) => Stmt::Bubble(self.core("panic").call(Vec::new())),
                Some((v, Node::OrElse { .. })) => {
                    out.extend(self.deliver(v, &Sink::Return)?);
                    return Ok(());
                }
                Some((v, _)) => Stmt::Return(Some(self.expr(v)?)),
            },
            Node::SetField {
                field,
                object,
                value,
            } => {
                let mut both = self.operands(&[object, value])?;
                let value = both.pop().unwrap_or(Expr::Bool(false));
                let object = both.pop().unwrap_or(Expr::Bool(false));
                Stmt::Set {
                    target: object.arrow(self.name(field)),
                    value,
                }
            }
            Node::InitStatic { target, value } => Stmt::Set {
                target: self.top(target),
                value: self.expr(value)?,
            },
            Node::ExprStatement { expr } => {
                if let Node::Error { .. } | Node::Bubble { .. } = p.nodes.get(expr) {
                    Stmt::Bubble(self.core("bubble").call(Vec::new()))
                } else if let Node::Panic {} = p.nodes.get(expr) {
                    Stmt::Bubble(self.core("panic").call(Vec::new()))
                } else if let Node::OrElse { .. } | Node::BlockValue { .. } = p.nodes.get(expr) {
                    out.append(&mut self.pending);
                    out.extend(self.deliver(expr, &Sink::Discard)?);
                    return Ok(());
                } else {
                    // A value known not to be null, discarded, is the
                    // call that makes it (`lexer.scan("\n");` for a
                    // `String?`), not its `NullableParam`'s `.value`.
                    let mut expr = expr;
                    while let Node::NonNull { value } = p.nodes.get(expr) {
                        expr = value;
                    }
                    let e = self.expr(expr)?;
                    if !e.is_statement() {
                        return not_yet(format!(
                            "an expression statement that isn't a call, `{}`",
                            tree::expr(&e, 0)
                        ));
                    }
                    Stmt::Expr(e)
                }
            }
            other => return not_yet(format!("a {} statement", other.kind().name())),
        };
        out.append(&mut self.pending);
        out.push(s);
        Ok(())
    }

    /// A call of a connected declaration, by the connection table; `args`
    /// has a method's receiver first.
    fn connected(&mut self, decl: DeclId, args: &[NodeId]) -> R<Expr> {
        let qname = self.qname(decl);
        let Some(connection) = connected::lookup(&qname) else {
            return not_yet(format!("a connection for {qname}"));
        };
        match connection {
            Connection::Call(f) => {
                let mut values = self.args(args)?;
                // A string made as a `std::string` where the runtime's
                // template deduces a type parameter, as `reduceFrom`'s
                // `initial`, is a `core::Str`, as Temper's strings are.
                let p = self.p;
                if let Some(sig) = p.types.signature(p.decls.get(decl).ty) {
                    let skip = values.len().saturating_sub(sig.params.len());
                    for (v, &t) in values.iter_mut().skip(skip).zip(sig.params) {
                        if v.is_std_string() && matches!(p.types.get(t), TypeData::TypeParam { .. })
                        {
                            *v = v.clone().str_value();
                        }
                    }
                }
                Ok(self.core(f).call(values))
            }
            Connection::Templated(f) => {
                let Some(&receiver) = args.first() else {
                    return not_yet(format!("{qname} without its receiver"));
                };
                let targs = self.template_args(self.p.types.args(self.p.nodes.ty(receiver)))?;
                let args = self.runtime_args(args)?;
                Ok(self.core(&format!("{f}{targs}")).call(args))
            }
            Connection::Making(f) => {
                let targs = self.template_args(self.p.types.args(self.p.nodes.ty(self.call)))?;
                let args = self.args(args)?;
                Ok(self.core(&format!("{f}{targs}")).call(args))
            }
            Connection::Made(f) => {
                let made = temper_tmpl::analysis::nominal_decl(self.p, self.p.nodes.ty(self.call));
                let Some(class) = made else {
                    return not_yet(format!("{qname} making no class"));
                };
                let class = self.top_name(class);
                let args = self.args(args)?;
                Ok(self.core(&format!("{f}<{class}>")).call(args))
            }
            Connection::Static(class, f) => {
                let targs = self.template_args(self.p.types.args(self.p.nodes.ty(self.call)))?;
                let args = self.args(args)?;
                Ok(self.core(&format!("{class}{targs}::{f}")).call(args))
            }
            Connection::Translate(f) => f(self, args),
        }
    }

    /// A branch of `?:`: an upcast made explicit, since C++ won't find
    /// the type two branches share.
    fn branch(&mut self, id: NodeId) -> R<Expr> {
        match self.node(id) {
            Node::AsNullable { value } if !self.ty(self.p.nodes.ty(id))?.starts_with("std::") => {
                let ty = self.ty(self.p.nodes.ty(id))?;
                Ok(Expr::name(ty).call(vec![self.expr(value)?]))
            }
            // Through `to_base`, typed already.
            Node::Upcast { value } if self.base_of(value, id)?.is_some() => self.expr(id),
            // Not between string indices, all `int32_t`s, which
            // `-Wuseless-cast` would mention.
            Node::Upcast { value }
                if self.ty(self.p.nodes.ty(value))? != self.ty(self.p.nodes.ty(id))? =>
            {
                let ty = self.ty(self.p.nodes.ty(id))?;
                Ok(Expr::name(ty).call(vec![self.expr(value)?]))
            }
            _ => self.expr(id),
        }
    }

    /// The base an upcast of a value that's never null converts its
    /// `shared_ptr` to, as `to_base` takes it: an interface the value's
    /// class or interface extends, a virtual base. The runtime's
    /// `to_base` says the value isn't null, so the conversion has no
    /// null check, whose null path g++'s `-Wnull-dereference` follows to
    /// the next call on the result. `None` for a nullable value, and for
    /// any other conversion, which C++ makes as it is.
    fn base_of(&self, value: NodeId, id: NodeId) -> R<Option<String>> {
        let p = self.p;
        if p.types.is_nullable(p.nodes.ty(value)) {
            return Ok(None);
        }
        let (from, to) = (self.ty(p.nodes.ty(value))?, self.ty(p.nodes.ty(id))?);
        let pointee = |t: &str| {
            (t.strip_prefix("std::shared_ptr<"))
                .and_then(|t| t.strip_suffix('>'))
                .map(str::to_string)
        };
        Ok(match (pointee(&from), pointee(&to)) {
            (Some(_), Some(base)) if from != to => Some(base),
            _ => None,
        })
    }

    /// A coalesced value where it isn't null, as `ty`: a `NullableParam`'s
    /// `value`, unless `ty` is nullable too; a pointer as it is.
    fn coalesced(&mut self, left: NodeId, ty: TypeId) -> R<Expr> {
        let e = self.expr(left)?;
        let pointer = self.ty(ty)?.starts_with("std::shared_ptr<");
        Ok(if pointer || self.p.types.is_nullable(ty) {
            e
        } else {
            Expr::Member(Box::new(e), "value".into())
        })
    }

    /// The struct a class or interface type points to: `Rect` for
    /// `std::shared_ptr<Rect>`.
    fn pointee(&mut self, ty: TypeId) -> R<String> {
        let p = self.p;
        let generic = |d: DeclId| !p.decls.type_params(d).is_empty();
        match temper_tmpl::analysis::nominal_decl(p, ty) {
            Some(d) if !(generic(d) || is_external(p, d) && library_of(p, d) == "core") => {
                Ok(self.top_name(d))
            }
            // A core type kept in a `shared_ptr` (a `MapBuilder`'s
            // `Mapped::Ordered`), checked as a class is: `multis as
            // MapBuilder<…>` bubbles only on null; and a generic class
            // with its type arguments, as `x is Append<E>`.
            Some(_) => {
                let text = self.ty(p.types.non_null(ty))?;
                match (text.strip_prefix("std::shared_ptr<")).and_then(|t| t.strip_suffix('>')) {
                    Some(inner) => Ok(inner.to_string()),
                    None => not_yet(format!("a conversion to {text}")),
                }
            }
            None => not_yet("a conversion to a type that isn't a class's"),
        }
    }

    /// A call of a method of the program's own, a getter as `get_x()`,
    /// a setter as `set_x(value)`, the receiver evaluated first.
    fn call_method(
        &mut self,
        method: DeclId,
        type_args: &[TypeId],
        receiver: NodeId,
        args: &[NodeId],
    ) -> R<Expr> {
        let p = self.p;
        // Those left out at the end, as its overloads leave them out.
        let args = &args[..self.given(args)?];
        // A class's value called as one of its interfaces is called as
        // the class: its members settle which it calls, with no
        // conversion (nor `to_base`'s copy of the `shared_ptr`).
        let receiver = match p.nodes.get(receiver) {
            Node::Upcast { value }
                if temper_tmpl::analysis::nominal_decl(p, p.nodes.ty(value))
                    .is_some_and(|d| p.decls.get(d).kind == DeclKind::Class) =>
            {
                value
            }
            _ => receiver,
        };
        let mut ids = vec![receiver];
        ids.extend_from_slice(args);
        let named: Vec<bool> = (ids.iter().enumerate())
            .map(|(i, &a)| i > 0 && self.unstable(a))
            .collect();
        let mut ops = self.operands_named(&ids, &named)?;
        let mut recv = ops.remove(0);
        // Called through an interface its value's interface extends, the
        // one the method's from, which C++'s lookup might find ambiguous
        // otherwise; a class's own members settle which it calls
        // ([`class_item`](Self::class_item)).
        if let Node::Upcast { value } = p.nodes.get(receiver) {
            let inner = temper_tmpl::analysis::nominal_decl(p, p.nodes.ty(value));
            let interface = inner.is_some_and(|d| p.decls.get(d).kind == DeclKind::Interface);
            // Not a temporary, which has the receiver's type already, so
            // a cast would be useless (g++ 12's `-Wuseless-cast`).
            let hoisted = matches!(&recv, Expr::Name(n) if self.pending.iter().any(
                |s| matches!(s, Stmt::Local { name, .. } if name == n)
            ));
            // Nor `to_base`'s, which has it too.
            let based = self.base_of(value, receiver)?.is_some();
            if interface && !hoisted && !based && !matches!(p.nodes.get(value), Node::This {}) {
                recv = Expr::name(self.ty(p.nodes.ty(receiver))?).call(vec![recv]);
            }
        }
        let name = self.name(method);
        let name = match p.decls.get(method).kind {
            DeclKind::Getter => format!("get_{name}"),
            DeclKind::Setter => format!("set_{name}"),
            _ => name,
        };
        // A generic method's type arguments, which C++ can't always
        // deduce; `template` before them where the receiver's type
        // depends on a template's parameters.
        let name = match type_args.is_empty() {
            true => name,
            false => {
                let dependent = depends(p, p.nodes.ty(receiver));
                let targs = self.template_args(type_args)?;
                match dependent {
                    true => format!("template {name}{targs}"),
                    false => format!("{name}{targs}"),
                }
            }
        };
        Ok(recv.arrow(name).call(ops))
    }

    /// Whether an argument for a `const &` parameter could change while
    /// the call runs: a field the call might assign, or one of an object
    /// a field holds, which C++ would refer to, not copy. Kotlin's passes
    /// it so (`this_->replace(this_->box->item)`), and the callee sees
    /// another object, or one freed, if it assigns the field; this names
    /// such an argument first, which copies the `shared_ptr`.
    fn unstable(&self, id: NodeId) -> bool {
        let p = self.p;
        fn changes(p: &Program, id: NodeId) -> bool {
            match p.nodes.get(id) {
                Node::GetField { field, object } => {
                    p.decls.get(field).facts.contains(Facts::VAR) || changes(p, object)
                }
                _ => false,
            }
        }
        matches!(p.nodes.get(id), Node::GetField { .. })
            && self.ty(p.nodes.ty(id)).is_ok_and(|t| indirect(&t))
            && changes(p, id)
    }

    /// Arguments of a function or constructor of the program's, as
    /// [`args`](Self::args), those that could change while it runs named
    /// first ([`unstable`](Self::unstable)).
    fn program_args(&mut self, args: &[NodeId]) -> R<Vec<Expr>> {
        let given = self.given(args)?;
        let named: Vec<bool> = args[..given].iter().map(|&a| self.unstable(a)).collect();
        self.operands_named(&args[..given], &named)
    }

    /// How many arguments are given, up to the last not left out.
    fn given(&self, args: &[NodeId]) -> R<usize> {
        let p = self.p;
        let given = args
            .iter()
            .rposition(|&a| !matches!(p.nodes.get(a), Node::Omitted {}))
            .map_or(0, |i| i + 1);
        if args[..given]
            .iter()
            .any(|&a| matches!(p.nodes.get(a), Node::Omitted {}))
        {
            return not_yet("an argument left out before one given");
        }
        Ok(given)
    }

    /// Arguments of a runtime function, as [`args`](Self::args), but one
    /// left out before one given is `nullptr`, which the runtime's
    /// `NullableParam`s take as none (`ListBuilder::splice(b, nullptr,
    /// 0)`), as Kotlin's.
    fn runtime_args(&mut self, args: &[NodeId]) -> R<Vec<Expr>> {
        let p = self.p;
        let omitted = |a: NodeId| matches!(p.nodes.get(a), Node::Omitted {});
        let given = args.iter().rposition(|&a| !omitted(a)).map_or(0, |i| i + 1);
        let present: Vec<NodeId> = args[..given]
            .iter()
            .copied()
            .filter(|&a| !omitted(a))
            .collect();
        let mut values = self.operands(&present)?.into_iter();
        let mut out = Vec::new();
        for &a in &args[..given] {
            out.push(match omitted(a) {
                true => Expr::name("nullptr"),
                false => values.next().unwrap(),
            });
        }
        Ok(out)
    }

    /// Arguments, those left out at the end dropped, as C++'s default
    /// arguments and overloads leave them out, in Temper's order.
    fn args(&mut self, args: &[NodeId]) -> R<Vec<Expr>> {
        let given = self.given(args)?;
        self.operands(&args[..given])
    }

    /// Operands, each before the last whose order shows computed first
    /// into a temporary, in order, since C++ evaluates them in any order.
    pub(crate) fn operands(&mut self, ids: &[NodeId]) -> R<Vec<Expr>> {
        self.operands_named(ids, &[])
    }

    /// [`operands`](Self::operands), and those `named` computed first
    /// too.
    fn operands_named(&mut self, ids: &[NodeId], named: &[bool]) -> R<Vec<Expr>> {
        let effects: Vec<Effect> = ids.iter().map(|&id| self.effect(id)).collect();
        // An operand whose statements go before the statement runs first,
        // so those before it that do anything are computed first too.
        let statements: Vec<bool> = ids.iter().map(|&id| self.needs_statements(id)).collect();
        let mut out = Vec::new();
        for (i, &id) in ids.iter().enumerate() {
            let hoist = named.get(i) == Some(&true)
                || effects[i + 1..]
                    .iter()
                    .any(|&later| effects[i].conflicts(later))
                || (self.deferred == 0
                    && effects[i].any()
                    && statements[i + 1..].iter().any(|&s| s));
            let e = self.expr(id)?;
            out.push(if hoist { self.temporary(id, e)? } else { e });
        }
        Ok(out)
    }

    /// Computes an expression first, into a local named by its hints, and
    /// gives the local.
    fn temporary(&mut self, id: NodeId, value: Expr) -> R<Expr> {
        if self.deferred > 0 {
            return not_yet("an operand whose order needs a temporary where none can go yet");
        }
        let ty = self.ty(self.p.nodes.ty(id))?;
        let mut name = None;
        for hint in naming::hints(self.p, id) {
            if !self.locals.contains(&hint) {
                name = Some(hint);
                break;
            }
        }
        let name = match name {
            Some(n) => {
                self.locals.insert(n.clone());
                n
            }
            None => self.fresh_local("value"),
        };
        self.pending.push(Stmt::Local {
            ty,
            name: name.clone(),
            value: Some(value),
        });
        Ok(Expr::Name(name))
    }

    /// A read of a module `let`: under the lock, in a function, where
    /// functions write it.
    fn read_module(&mut self, target: DeclId) -> Expr {
        let name = self.top(target);
        if !self.in_function || self.locked || !self.shared.contains(&target) {
            return name;
        }
        Expr::name(self.load()).call(vec![name])
    }

    /// The helper that reads a variable under the module's lock.
    fn load(&mut self) -> String {
        if self.load.is_none() {
            self.mutex();
            self.load = Some(self.fresh_module_name("load"));
        }
        self.load.clone().unwrap_or_default()
    }

    /// An exported module value's accessor, for users, which runs the
    /// init first: a `const &` to its storage, as Kotlin's, or for a
    /// `var` that functions write, a copy read under the module's lock,
    /// where a reference would race with their writes.
    fn accessor(&mut self, decl: DeclId, ty: &str, name: &str) -> tree::Function {
        let storage = Expr::name(format!("temper_globals::{name}.value"));
        let (result, value) = match self.shared.contains(&decl) {
            true => (ty.to_string(), Expr::name(self.load()).call(vec![storage])),
            false => (format!("{ty} const &"), storage),
        };
        tree::Function {
            exported: true,
            entry: true,
            result,
            name: name.to_string(),
            body: vec![Stmt::Return(Some(value))],
            ..tree::Function::default()
        }
    }

    pub(crate) fn expr(&mut self, id: NodeId) -> R<Expr> {
        let p = self.p;
        Ok(match p.nodes.get(id) {
            Node::BoolLit { value } => Expr::Bool(value),
            Node::Int32Lit { value } => Expr::Int(value),
            Node::Int64Lit { value } => Expr::Long(value),
            Node::Float64Lit { value } => Expr::Float(value),
            Node::StringLit { value } => Expr::Str(p.strings.get(value).to_string()),
            Node::TypeValue { of } => {
                let of = self.ty(of)?;
                Expr::name(format!("std::type_index(typeid({of}))"))
            }
            Node::Local { local: decl } if self.is_cell(decl) => {
                Expr::name(format!("(*{})", self.name(decl)))
            }
            Node::Local { local: decl } => match self.group_value(decl)? {
                Some(value) => value,
                None => Expr::name(self.name(decl)),
            },
            Node::GetModuleLet { target } => self.read_module(target),
            Node::CallFunction {
                callee,
                type_args,
                args,
                ..
            } => {
                let args = p.nodes.list(args);
                if temper_tmpl::qname::by_connection(p, callee, crate::connected::connects_std) {
                    self.call = id;
                    return self.connected(callee, args);
                }
                // With its type arguments, which C++ can't always deduce.
                let targs = self.template_args(p.types.args_of(type_args))?;
                let mut name = self.top_name(callee);
                // Qualified where an argument isn't a scalar, so
                // argument-dependent lookup can't pick a function of
                // `std` or the runtime by the same name, as for
                // Kotlin's `swap(xs, ys)` and `move(xs)`, which find
                // `std::swap` and `std::move` for `shared_ptr`s.
                let scalar = |a: NodeId| {
                    matches!(
                        p.types.get(p.nodes.ty(a)),
                        TypeData::Scalar(
                            Scalar::Boolean | Scalar::Int32 | Scalar::Int64 | Scalar::Float64
                        )
                    )
                };
                if !name.contains("::") && !args.iter().all(|&a| scalar(a)) {
                    name = format!("{}::{name}", self.namespace);
                }
                let args = self.program_args(args)?;
                Expr::name(format!("{name}{targs}")).call(args)
            }
            Node::New { ctor, args, .. }
                if is_external(p, ctor) && library_of(p, ctor) == "core"
                    || by_connection(p, ctor, crate::connected::connects_std) =>
            {
                self.call = id;
                return self.connected(ctor, p.nodes.list(args));
            }
            Node::New { ctor, args, .. } => {
                let class = p.decls.get(ctor).owner;
                let targs = self.template_args(p.types.args(p.nodes.ty(id)))?;
                let args = self.program_args(p.nodes.list(args))?;
                Expr::name(format!("{}{targs}::make", self.top_name(class))).call(args)
            }
            // `core::List::make<T>(…)`, as Kotlin's.
            Node::ListLit { items } => {
                let targs = self.template_args(p.types.args(p.nodes.ty(id)))?;
                let items = self.program_args(p.nodes.list(items))?;
                self.core(&format!("List::make{targs}")).call(items)
            }
            Node::CallMethod {
                method,
                type_args,
                receiver,
                args,
                ..
            } => {
                let args = p.nodes.list(args);
                if by_connection(p, method, crate::connected::connects_std) {
                    let mut all = vec![receiver];
                    all.extend_from_slice(args);
                    self.call = id;
                    return self.connected(method, &all);
                }
                let type_args = p.types.args_of(type_args).to_vec();
                self.call_method(method, &type_args, receiver, args)?
            }
            Node::This {} => match &self.this_expr {
                Some(e) => e.clone(),
                None => return not_yet("`this` outside a member"),
            },
            // A step's value, by the runtime's `value()`.
            Node::GetField { field, object }
                if self.qname(field) == "core.type ValueResult.value" =>
            {
                self.expr(object)?.arrow("value").call(Vec::new())
            }
            // A pair's, the runtime's `Pair`'s.
            Node::GetField { field, object }
                if by_connection(p, field, crate::connected::connects_std) =>
            {
                self.expr(object)?.arrow(p.name(field))
            }
            Node::GetField { field, object } => self.expr(object)?.arrow(self.name(field)),
            // `String.begin`, `Float64.pi`, and the like: the runtime's
            // functions, as Kotlin's.
            Node::GetStatic { target }
                if is_external(p, target) && library_of(p, target) == "core" =>
            {
                self.call = id;
                return self.connected(target, &[]);
            }
            Node::GetStatic { target } => self.top(target),
            // A class's `shared_ptr` converts to its interfaces' through
            // the runtime's `to_base` where it's never null (`base_of`),
            // and itself where it may be. A builtin value as an
            // `AnyValue`, boxed, as Kotlin's; a string as the `core::Str`
            // it is.
            Node::Upcast { value }
                if p.types.non_null(p.nodes.ty(id)) == TypeId::ANY_VALUE
                    && matches!(p.types.get(p.nodes.ty(value)), TypeData::Scalar(_)) =>
            {
                let v = self.expr(value)?.str_value();
                self.core("any_box").call(vec![v])
            }
            Node::Upcast { value } => match self.base_of(value, id)? {
                Some(base) => {
                    let v = self.expr(value)?;
                    self.core(&format!("to_base<{base}>")).call(vec![v])
                }
                None => self.expr(value)?,
            },
            // Checked as `as` is, as Kotlin's: interfaces are virtual
            // bases, which only a `dynamic_cast` leaves, and one that
            // could give null would need a check, or `-Wnull-dereference`
            // mentions it. `as` bubbles if it isn't one.
            // `x as T` for `x: T?`, for `T` not a class's: the runtime's
            // check, which bubbles on null.
            Node::As { value, .. }
                if p.types.is_nullable(p.nodes.ty(value))
                    && temper_tmpl::analysis::nominal_decl(p, p.nodes.ty(id)).is_none() =>
            {
                let value = self.expr(value)?;
                self.core("not_null").call(vec![value])
            }
            // String indices are `int32_t`s: `as` checks by the runtime,
            // which bubbles, as Kotlin's; a downcast, already checked, is
            // the value.
            Node::As { value, .. } if string_index_check(p, p.nodes.ty(id)).is_some() => {
                let check = string_index_check(p, p.nodes.ty(id)).unwrap_or_default();
                let value = self.expr(value)?;
                self.core(&format!("String::require{check}"))
                    .call(vec![value])
            }
            Node::Downcast { value }
                if string_index_check(p, p.nodes.ty(id)).is_some()
                    || self.step_result(p.nodes.ty(id)).is_some() =>
            {
                self.expr(value)?
            }
            Node::Downcast { value } | Node::As { value, .. } => {
                let class = self.pointee(p.nodes.ty(id))?;
                let value = self.expr(value)?;
                self.core(&format!("checked_cast<{class}>"))
                    .call(vec![value])
            }
            Node::Is { ty, value } if self.step_result(ty).is_some() => {
                // `!r->is_done()`, or `r->is_done()`.
                let done = self.expr(value)?.arrow("is_done").call(Vec::new());
                match self.step_result(ty) {
                    Some(true) => Expr::unary(Unary::Not, done),
                    _ => done,
                }
            }
            Node::Is { ty, value } if string_index_check(p, ty).is_some() => {
                let check = string_index_check(p, ty).unwrap_or_default();
                let value = self.expr(value)?;
                self.core(&format!("String::is{check}")).call(vec![value])
            }
            Node::Is { ty, value } => {
                let class = self.pointee(ty)?;
                let from = p.nodes.ty(value);
                let polymorphic = temper_tmpl::analysis::nominal_decl(p, from).is_some_and(|d| {
                    p.decls.get(d).kind == DeclKind::Interface || !p.decls.supers(d).is_empty()
                });
                if !polymorphic {
                    return not_yet("`is` on a class without interfaces");
                }
                // No count to change, as `dynamic_pointer_cast` would.
                let value = self.expr(value)?.method("get", vec![]);
                Expr::bin(
                    Bin::Ne,
                    Expr::name(format!("dynamic_cast<{class} const *>")).call(vec![value]),
                    Expr::name("nullptr"),
                )
            }
            Node::Identical { left, right } => {
                let mut both = self.operands(&[left, right])?;
                let right = both.pop().unwrap_or(Expr::Bool(false));
                let left = both.pop().unwrap_or(Expr::Bool(false));
                Expr::bin(Bin::Eq, left, right)
            }
            Node::Op { op, args, .. } => {
                let Some(spelling) = ops::spelling(op) else {
                    return not_yet("an operator without a spelling");
                };
                if spelling == Spelling::Cat {
                    return self.cat(id);
                }
                let arg_ids = p.nodes.list(args);
                // A negated literal is a literal, wrapped as the type
                // wraps (`-(-2147483648)` is itself).
                if let [arg] = arg_ids {
                    let neg = matches!(
                        spelling,
                        Spelling::Unary(Unary::Neg) | Spelling::Call("Int::neg" | "Int64::neg")
                    );
                    match (neg, p.nodes.get(*arg)) {
                        (true, Node::Int32Lit { value }) => {
                            return Ok(Expr::Int(value.wrapping_neg()));
                        }
                        (true, Node::Int64Lit { value }) => {
                            return Ok(Expr::Long(value.wrapping_neg()));
                        }
                        (true, Node::Float64Lit { value }) => return Ok(Expr::Float(-value)),
                        _ => {}
                    }
                }
                let literal = |i: usize| match arg_ids.get(i).map(|&a| p.nodes.get(a)) {
                    Some(Node::Int32Lit { value }) => Some(i64::from(value)),
                    Some(Node::Int64Lit { value }) => Some(value),
                    _ => None,
                };
                let strings = arg_ids.iter().any(|&a| p.nodes.ty(a) == TypeId::STRING);
                let mut args = self.operands(arg_ids)?;
                match spelling {
                    Spelling::Bin(op) => {
                        let b = args.pop().unwrap_or(Expr::Bool(false));
                        let a = args.pop().unwrap_or(Expr::Bool(false));
                        Expr::bin(op, a, b)
                    }
                    Spelling::Unary(op) => {
                        let a = args.pop().unwrap_or(Expr::Bool(false));
                        match (op, a) {
                            // `!(a < b)` reads better as `a >= b`; no
                            // comparison here has a NaN operand (floats'
                            // compare through the runtime).
                            (Unary::Not, Expr::Bin(cmp, x, y)) if cmp.negated().is_some() => {
                                Expr::Bin(cmp.negated().unwrap_or(cmp), x, y)
                            }
                            (op, a) => Expr::unary(op, a),
                        }
                    }
                    Spelling::Call(f) => {
                        // A `std::string` would deduce the runtime's
                        // templates, not take its `core::Str` overloads.
                        if strings {
                            args = args.into_iter().map(Expr::str_value).collect();
                        }
                        self.core(f).call(args)
                    }
                    Spelling::Divide(op, f) => match literal(1) {
                        Some(d) if d != 0 && d != -1 => {
                            let b = args.pop().unwrap_or(Expr::Int(1));
                            let a = args.pop().unwrap_or(Expr::Int(1));
                            Expr::bin(op, a, b)
                        }
                        _ => self.core(f).call(args),
                    },
                    Spelling::ShiftRight(f) => {
                        let width = if f.starts_with("Int64") { 64 } else { 32 };
                        match literal(1) {
                            Some(n) if (0..width).contains(&n) => {
                                let b = args.pop().unwrap_or(Expr::Int(0));
                                let a = args.pop().unwrap_or(Expr::Int(0));
                                Expr::bin(Bin::Shr, a, b)
                            }
                            _ => self.core(f).call(args),
                        }
                    }
                    Spelling::Cat => return self.cat(id),
                }
            }
            Node::And { left, right } => {
                let left = self.expr(left)?;
                self.deferred += 1;
                let right = self.expr(right);
                self.deferred -= 1;
                Expr::bin(Bin::And, left, right?)
            }
            Node::Or { left, right } => {
                let left = self.expr(left)?;
                self.deferred += 1;
                let right = self.expr(right);
                self.deferred -= 1;
                Expr::bin(Bin::Or, left, right?)
            }
            Node::IfValue { cond, then, els } => {
                let cond = self.expr(cond)?;
                self.deferred += 1;
                let branches = (self.branch(then)).and_then(|t| Ok((t, self.branch(els)?)));
                self.deferred -= 1;
                let (then, els) = branches?;
                Expr::Cond {
                    cond: Box::new(cond),
                    then: Box::new(then),
                    els: Box::new(els),
                }
            }
            Node::Error { .. } | Node::Bubble { .. } => {
                let ty = self.ty(p.nodes.ty(id))?;
                self.core(&format!("bubble<{ty}>")).call(Vec::new())
            }
            Node::Panic {} => {
                let ty = self.ty(p.nodes.ty(id))?;
                self.core(&format!("panic<{ty}>")).call(Vec::new())
            }
            Node::Null {} => Expr::name("nullptr"),
            Node::IsNull { value } => Expr::bin(Bin::Eq, self.expr(value)?, Expr::name("nullptr")),
            Node::NonNull { value } => {
                let pointer = self.ty(p.nodes.ty(id))?.starts_with("std::shared_ptr<");
                let value = self.expr(value)?;
                if pointer {
                    value
                } else {
                    Expr::Member(Box::new(value), "value".into())
                }
            }
            // `NullableParam` and `shared_ptr` take their values as they are.
            Node::AsNullable { value } => self.expr(value)?,
            Node::EqNullable { op, left, right } => self.eq_nullable(op, left, right)?,
            // A parameter's default: `x == nullptr ? d : x.value`.
            Node::Coalesce { left, right } => {
                if !matches!(p.nodes.get(left), Node::Local { .. }) {
                    return not_yet("a coalesced value that isn't a local");
                }
                let cond = Expr::bin(Bin::Eq, self.expr(left)?, Expr::name("nullptr"));
                self.deferred += 1;
                let then = self.branch(right);
                self.deferred -= 1;
                Expr::Cond {
                    cond: Box::new(cond),
                    then: Box::new(then?),
                    els: Box::new(self.coalesced(left, p.nodes.ty(id))?),
                }
            }
            Node::OrElse { .. } | Node::BlockValue { .. } => self.statement_value(id)?,
            Node::FunctionLiteral { decl, params, body } => self.literal(decl, params, body)?,
            Node::FunctionRef { target } => self.function_ref(target)?,
            Node::CallValue { callee, args, .. } => {
                let f = match p.nodes.get(callee) {
                    Node::Local { local } => self.group_callee(local),
                    _ => None,
                };
                let f = match f {
                    Some(f) => f,
                    None => self.expr(callee)?,
                };
                let args: Vec<NodeId> = p.nodes.list(args).to_vec();
                let mut out = Vec::new();
                for a in args {
                    out.push(self.expr(a)?);
                }
                f.call(out)
            }
            Node::ToFun { value } | Node::FromFun { value } => self.expr(value)?,
            other => return not_yet(format!("a {} expression", other.kind().name())),
        })
    }

    /// `==` where either side may be null. With `NullableParam`'s `==`,
    /// which compares as `EqNullable` does, where the type's own `==` is
    /// C++'s (a side known not to be null as its value, of its type, so
    /// the overload is clear); else as Kotlin's, `a == nullptr ? b ==
    /// nullptr : b != nullptr && eq(a.value, b.value)`, each side once.
    fn eq_nullable(&mut self, op: temper_tmpl::ops::Op, left: NodeId, right: NodeId) -> R<Expr> {
        let p = self.p;
        let Some(spelling) = ops::spelling(op) else {
            return not_yet("an operator without a spelling");
        };
        let plain = |id: NodeId| match p.nodes.get(id) {
            Node::AsNullable { value } => Some(value),
            _ => None,
        };
        let null = |id: NodeId| matches!(p.nodes.get(id), Node::Null {});
        if spelling == Spelling::Bin(Bin::Eq) && !(null(left) && null(right)) {
            // A side known not to be null is its value, unless the other
            // is `null`, which only `NullableParam` compares with.
            let unwrap = |id: NodeId, other: NodeId| match plain(id) {
                Some(v) if !null(other) => v,
                _ => id,
            };
            let ids = [unwrap(left, right), unwrap(right, left)];
            let mut both = self.operands(&ids)?;
            let strings = |i: usize| p.nodes.ty(ids[i]) == TypeId::STRING;
            let b = both.pop().unwrap_or(Expr::Bool(false));
            let a = both.pop().unwrap_or(Expr::Bool(false));
            let a = if strings(0) { a.str_value() } else { a };
            let b = if strings(1) { b.str_value() } else { b };
            let explicit = |t: &mut Self, id: NodeId, e: Expr| -> R<Expr> {
                Ok(match p.nodes.get(id) {
                    Node::AsNullable { .. } => Expr::name(t.ty(p.nodes.ty(id))?).call(vec![e]),
                    _ => e,
                })
            };
            let a = explicit(self, ids[0], a)?;
            let b = explicit(self, ids[1], b)?;
            return Ok(Expr::bin(Bin::Eq, a, b));
        }
        let Spelling::Call(f) = spelling else {
            return not_yet("a nullable equality of another spelling");
        };
        let mut both = self.operands(&[left, right])?;
        let mut b = both.pop().unwrap_or(Expr::Bool(false));
        let mut a = both.pop().unwrap_or(Expr::Bool(false));
        if !matches!(a, Expr::Name(_)) {
            a = self.temporary(left, a)?;
        }
        if !matches!(b, Expr::Name(_)) {
            b = self.temporary(right, b)?;
        }
        let value = |e: &Expr| Expr::Member(Box::new(e.clone()), "value".into());
        let null = Expr::name("nullptr");
        Ok(Expr::Cond {
            cond: Box::new(Expr::bin(Bin::Eq, a.clone(), null.clone())),
            then: Box::new(Expr::bin(Bin::Eq, b.clone(), null.clone())),
            els: Box::new(Expr::bin(
                Bin::And,
                Expr::bin(Bin::Ne, b.clone(), null),
                self.core(f).call(vec![value(&a), value(&b)]),
            )),
        })
    }

    /// A chain of `String.cat` as one concatenation: literals merged; an
    /// `Int32.toString()` without a radix as `std::to_string`, and a
    /// `Boolean.toString()` as the runtime's; `+` between them, literals
    /// bare (`"…"`), as a `std::string`, which converts to `core::Str`.
    fn cat(&mut self, id: NodeId) -> R<Expr> {
        let mut parts = Vec::new();
        self.cat_parts(id, &mut parts);
        parts.retain(|p| !matches!(p, Part::Lit(s) if s.is_empty()));
        // What each part evaluates, for its order.
        let nodes: Vec<Option<NodeId>> = parts
            .iter()
            .map(|p| match p {
                Part::Lit(_) => None,
                Part::Str(n) | Part::Number(n) | Part::Boolean(n) => Some(*n),
            })
            .collect();
        let effects: Vec<Effect> = (nodes.iter())
            .map(|n| n.map_or(Effect::default(), |n| self.effect(n)))
            .collect();
        let mut out = Vec::new();
        for (i, part) in parts.into_iter().enumerate() {
            let hoist = effects[i + 1..]
                .iter()
                .any(|&later| effects[i].conflicts(later));
            let (n, convert) = match part {
                Part::Lit(s) => {
                    out.push(Expr::Str(s));
                    continue;
                }
                Part::Str(n) => (n, None),
                Part::Number(n) => (n, Some("std::to_string")),
                Part::Boolean(n) => (n, Some("core::Boolean::toString")),
            };
            let e = self.expr(n)?;
            let e = if hoist { self.temporary(n, e)? } else { e };
            out.push(match convert {
                Some(f) => Expr::name(f).call(vec![e]),
                None => e,
            });
        }
        if out.is_empty() {
            return Ok(Expr::Str(String::new()));
        }
        if out.len() == 1 {
            return Ok(out.pop().unwrap_or(Expr::Str(String::new())));
        }
        Ok(Expr::Concat(out))
    }

    fn cat_parts(&mut self, id: NodeId, parts: &mut Vec<Part>) {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Op { op, args, .. } if ops::spelling(op) == Some(Spelling::Cat) => {
                for &a in p.nodes.list(args) {
                    self.cat_parts(a, parts);
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
                match self.qname(method).as_str() {
                    "core.type Int32.toString()" | "core.type Int64.toString()" => {
                        parts.push(Part::Number(receiver));
                    }
                    "core.type Boolean.toString()" => parts.push(Part::Boolean(receiver)),
                    _ => parts.push(Part::Str(id)),
                }
            }
            _ => parts.push(Part::Str(id)),
        }
    }
}

/// An exported function's prototype for the header, `temper::core`
/// spelled out.
/// The library a declaration of another library's is from, as its
/// `uses` section names it; `None` for the program's own.
pub(crate) fn external_library(p: &Program, decl: DeclId) -> Option<String> {
    let d = p.decls.get(decl);
    let of_library = d.kind == DeclKind::Library
        || (d.kind == DeclKind::Module && !d.owner.is_none())
        || is_external(p, decl);
    if !of_library {
        return None;
    }
    let mut d = decl;
    for _ in 0..p.decls.len() {
        let entry = p.decls.get(d);
        if entry.kind == DeclKind::Library {
            return Some(p.name(d).to_string());
        }
        d = entry.owner;
        if !p.decls.contains(d) {
            break;
        }
    }
    None
}

/// A module's path in its library, empty at the root: the program's own
/// or another library's (a `Library` itself is its root).
pub(crate) fn module_path(p: &Program, module: DeclId) -> String {
    let d = p.decls.get(module);
    if d.kind == DeclKind::Library {
        return String::new();
    }
    if !d.owner.is_none() {
        return p.name(module).to_string();
    }
    (p.modules.iter())
        .find(|m| m.decl == module)
        .map(|m| m.path.clone())
        .unwrap_or_default()
}

/// A module's file, without `.hpp` or `.cpp`, in its library's
/// directory: `init` at the root, else its path, as Kotlin's.
pub(crate) fn module_stem(p: &Program, module: DeclId) -> String {
    match module_path(p, module).as_str() {
        "" => "init".into(),
        path => path.into(),
    }
}

/// A module's init function, as Kotlin names it: `global_init_` and its
/// path's last part, `init` at the root.
pub(crate) fn init_name(p: &Program, module: DeclId) -> String {
    let stem = module_stem(p, module);
    let leaf = stem.rsplit('/').next().unwrap_or(&stem);
    format!(
        "global_init_{}",
        names::namespace(leaf).trim_start_matches("temper_")
    )
}

/// `item`, only where `TEMPER_TESTS` is defined if `tests`.
fn tests_only(item: Item, tests: bool) -> Item {
    match tests {
        true => Item::TestsOnly(Box::new(item)),
        false => item,
    }
}

/// A call of an imported module's init function: `global_init_helpers()`,
/// or another library's, `temper_std::global_init_testing()`.
fn init_call(p: &Program, from: DeclId) -> Stmt {
    let init_fn = match external_library(p, from) {
        Some(library) => format!("{}::{}", names::namespace(&library), init_name(p, from)),
        None => init_name(p, from),
    };
    Stmt::Expr(Expr::name(init_fn).call(Vec::new()))
}

/// Whether g++ warns of a local of this type that nothing reads, as it
/// doesn't of a class's with a destructor.
fn unused_warns(ty: &str) -> bool {
    matches!(
        ty,
        "int32_t" | "int64_t" | "double" | "bool" | "std::type_index"
    )
}

/// Whether a type mentions a type parameter, so that C++ sees it as
/// dependent in a template.
fn depends(p: &Program, t: TypeId) -> bool {
    if !p.types.contains(t) {
        return false;
    }
    match p.types.get(t) {
        TypeData::Never(inner) => depends(p, inner),
        TypeData::TypeParam { .. } => true,
        TypeData::Scalar(_) => false,
        TypeData::Nullable(inner) => depends(p, inner),
        TypeData::Function { params, result, .. } => {
            p.types.list(params).iter().any(|&t| depends(p, t)) || depends(p, result)
        }
        TypeData::Nominal { args, .. } => p.types.list(args).iter().any(|&t| depends(p, t)),
    }
}

/// Whether a program has types as values, `std::type_index`es, which
/// `<typeindex>` declares.
fn mentions_type(p: &Program) -> bool {
    fn has(p: &Program, t: TypeId) -> bool {
        if !p.types.contains(t) {
            return false;
        }
        match p.types.get(t) {
            TypeData::Never(inner) => has(p, inner),
            TypeData::Scalar(s) => s == Scalar::Type,
            TypeData::Nullable(inner) => has(p, inner),
            TypeData::Function { params, result, .. } => {
                p.types.list(params).iter().any(|&t| has(p, t)) || has(p, result)
            }
            TypeData::Nominal { args, .. } => p.types.list(args).iter().any(|&t| has(p, t)),
            TypeData::TypeParam { .. } => false,
        }
    }
    (p.decls.ids()).any(|d| p.decls.contains(d) && has(p, p.decls.get(d).ty))
        || (0..p.nodes.len()).any(|i| p.nodes.ty(NodeId(i as u32)) == TypeId::TYPE)
}

/// The headers of the modules in `imports`, as `<lib/path.hpp>`.
fn includes(p: &Program, imports: &[Import]) -> Vec<String> {
    (imports.iter())
        .map(|import| {
            let library = match external_library(p, import.from) {
                Some(library) => library,
                None => p.library.name.clone(),
            };
            format!("<{library}/{}.hpp>", module_stem(p, import.from))
        })
        .collect()
}

/// Whether C++ text mentions a name, as a whole identifier.
fn mentions(text: &str, name: &str) -> bool {
    let ident = |c: char| c == '_' || c.is_alphanumeric();
    text.match_indices(name).any(|(i, _)| {
        let before = text[..i].chars().next_back();
        let after = text[i + name.len()..].chars().next();
        !before.is_some_and(ident) && !after.is_some_and(ident)
    })
}

fn header_signature(f: &tree::Function) -> String {
    tree::spell_core(&f.signature())
}

/// Statements up to the first that can't complete normally, and the
/// statements after a bubble, which never returns.
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

/// Whether a statement can complete normally.
pub(crate) fn completes(s: &Stmt) -> bool {
    match s {
        // Each case's body that completes ends with `break`.
        Stmt::Switch { cases, .. } => {
            !cases.iter().any(|(labels, _)| labels.is_empty())
                || cases.iter().any(|(_, body)| body.iter().all(completes))
        }
        Stmt::Break | Stmt::Continue | Stmt::Goto(_) | Stmt::Return(_) | Stmt::Bubble(_) => false,
        Stmt::Block(body) | Stmt::TestsOnly(body) => body.iter().all(completes),
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
        Stmt::Try { body, fallback } => {
            body.iter().all(completes) || fallback.iter().all(completes)
        }
        Stmt::Expr(_)
        | Stmt::Local { .. }
        | Stmt::Assign { .. }
        | Stmt::Set { .. }
        | Stmt::Line(_)
        | Stmt::Guard { .. }
        | Stmt::LambdaLocal { .. }
        | Stmt::GroupStruct { .. } => true,
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
        Stmt::Block(b) => jumps(b, brk),
        Stmt::If { then, els, .. } => jumps(then, brk) || jumps(els, brk),
        // Its own `break`s are written only when it's printed.
        Stmt::Switch { cases, .. } => cases.iter().any(|(_, body)| jumps(body, brk)),
        Stmt::Try { body, fallback } => jumps(body, brk) || jumps(fallback, brk),
        _ => false,
    })
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

/// How many of a function's last parameters are optional, which its
/// overloads leave out.
pub(crate) fn trailing_optional(p: &Program, params: temper_tmpl::Kids) -> usize {
    (p.nodes.list(params).iter().rev())
        .take_while(|&&param| {
            matches!(p.nodes.get(param), Node::Param { decl }
                if p.decls.get(decl).facts.contains(Facts::OPTIONAL))
        })
        .count()
}

/// Overloads of a function whose last `k` parameters are optional, as
/// Kotlin's: each leaves out one more, passing null for it, which gives
/// its default. A member's calls the member by its own name.
pub(crate) fn overloads(f: &tree::Function, k: usize) -> Vec<tree::Function> {
    let callee = f.name.rsplit("::").next().unwrap_or(&f.name).to_string();
    (1..=k)
        .map(|j| {
            let n = f.params.len() - j;
            let mut args: Vec<Expr> = (f.params[..n].iter())
                .map(|(_, name)| Expr::name(name.clone()))
                .collect();
            args.extend((0..j).map(|_| Expr::name("nullptr")));
            tree::Function {
                params: f.params[..n].to_vec(),
                unused: Vec::new(),
                // The function it calls runs the init.
                entry: false,
                body: vec![Stmt::Return(Some(Expr::name(callee.clone()).call(args)))],
                ..f.clone()
            }
        })
        .collect()
}

/// For core's `StringIndex` or `NoStringIndex`, the runtime's name for
/// checking an `int32_t` is one: `StringIndex` (`String::isStringIndex`,
/// `String::requireStringIndex`) or `NoStringIndex`.
fn string_index_check(p: &Program, ty: TypeId) -> Option<&'static str> {
    let TypeData::Nominal { decl, .. } = p.types.get(ty) else {
        return None;
    };
    if !is_external(p, decl) || library_of(p, decl) != "core" {
        return None;
    }
    match p.name(decl) {
        "StringIndex" => Some("StringIndex"),
        "NoStringIndex" => Some("NoStringIndex"),
        _ => None,
    }
}

/// A module's host header, relative to the library's root:
/// `_connected.hpp` in its directory, as Kotlin's.
pub fn host_header_path(module: &Module) -> String {
    match module.path.as_str() {
        "" => "_connected.hpp".to_string(),
        dir => format!("{dir}/_connected.hpp"),
    }
}

/// A module-level function's or class's declaration.
fn node_decl(node: Node) -> Option<DeclId> {
    match node {
        Node::Function { decl, .. }
        | Node::BodilessFunction { decl, .. }
        | Node::Class { decl, .. } => Some(decl),
        _ => None,
    }
}

/// The header's names for host code's classes, by their host and C++
/// names, as Kotlin's: each declared in `_connected`, which
/// `_connected.hpp` defines, and named in the library's namespace.
fn host_class_names(classes: &[(String, String)]) -> String {
    let mut out = String::new();
    for (host, name) in classes {
        out.push_str(&format!(
            "namespace _connected {{\nstruct {host};\n}}  // namespace _connected\nusing {name} = _connected::{host};\n"
        ));
    }
    if !out.is_empty() {
        out.push('\n');
    }
    out
}
