//! The validator: checks a program against the model's invariants and a
//! profile (notes/tmpl/nodes.md, "Level 3: The validator's rules").
//!
//! Structure (S) runs first; the other groups run only on a structurally
//! sound program, so one mistake isn't reported many times. Problems come
//! sorted by writing order, each with its rule's code. Subset A's rules:
//!
//! - **S1** children fit the kind's fields, come before their parent, and
//!   only calls carry `MAY_BUBBLE`; **S2** one parent per node; **S3**
//!   statements, expressions, blocks, parameters, and module items in
//!   their places, `InitLet` only in an init block, and `Omitted` only as
//!   a call's argument; **S4** the
//!   profile allows each kind.
//! - **T1** typed expressions and declarations; **T2** the local rules'
//!   types; **T3** each child's type is its role's exactly, a receiver's
//!   its method's owner's, and `Omitted` only for optional parameters;
//!   **T4** (generics, subset B) a generic call's type arguments, as
//!   many as its callee has type parameters (a constructor's class's),
//!   each within its type parameter's bounds (but a builtin type's),
//!   with each argument's and the result's type put in;
//!   **T5** types well formed, `Never<T>` only as a result or a value's
//!   type, and only where the profile has
//!   [`Features::NEVER`](crate::Features::NEVER). `Error` nodes are
//!   exempt from T2 and T3, and a `Never<T>` child fits where its `T`
//!   does (T3), as it never completes (F1, R5).
//! - **R1** ids exist; **R2** references name the right kind of
//!   declaration (`CallMethod` a method or getter); **R3** locals read and assigned in scope, within their
//!   function, a run of function `let`s' in all of their literals
//!   (`analysis::function_groups`); **R4** jumps inside their loop; **R5** definite assignment;
//!   **R6** only a `var` assigned twice, a parameter only if `var`, and a
//!   module `let` initialized once.
//! - **F1** no falling off the end of a function with a result (nothing
//!   after a `return`, jump, `bubble()`, `panic()`, or a `Never<T>`
//!   value runs); **F2**
//!   `return` has a value exactly when the result isn't `Void`; **F3**
//!   `MAY_BUBBLE` exactly on calls that may bubble; **F4** a call that may
//!   bubble only in a function that may (or in an init block, where it
//!   fails the module's initialization); **F5** no reading a module `let`
//!   before its initializer; **F6** `Await` and `Yield` only in a
//!   generator literal's own body, a `Yield`'s value its `Y`; **F7** a
//!   `Switch`'s scrutinee an `Int32` or `Int64`, its labels literals of
//!   that type, all different, and its default, if any, one and last.
//! - **D1** compiler-made declarations have a hint; **D2** owners form a
//!   tree rooted at modules, or for external declarations at their `uses`
//!   section's library.
//! - **Q1** qnames unique in the library; **Q2** every declaration but
//!   compiler-made ones, labels, modules, and libraries has one; **Q3** a
//!   function without a body is connected or external; **Q4** a connected
//!   declaration with a backend list has a body, its fallback (a class,
//!   on every method).
//! - **M1** a module refers to another of the program's modules only for
//!   its exported top-level declarations, and only to one before it in
//!   `Program::modules` (imports are acyclic, so that's dependency
//!   order); it assigns or initializes only its own module `let`s; **M2**
//!   a test takes one parameter, a `Test` of std's `testing` module, and
//!   returns `Void`, and may bubble.
//! - **C1** (classes, subset B) a class's or interface's members fit it:
//!   fields only in classes, bodiless methods only in interfaces, a
//!   constructor's result its class, a setter's one parameter and `Void`
//!   result, supertypes only interfaces; `this` only in a member, typed
//!   as its class; **C2** a private member used only in its class (on
//!   any instance, and in its static fields' values), or for a private
//!   constructor, in its class's module, as Temper allows, a protected
//!   one only there and below; **C3** a field
//!   assigned only if it's a `var`, or on `this` in its class's
//!   constructor, a static field initialized only in its module's init
//!   block, and conversions only up or down the supertypes (`Upcast`,
//!   `Downcast`), with `Is` and `Identical` only on classes and
//!   interfaces.
//! - **N1** (nullable types, subset B) `Null` typed nullable; `IsNull`
//!   and `NonNull` only on nullable values; `NonNull` from `T?` to `T`
//!   and `AsNullable` from `T` to `T?`, exactly; `EqNullable` an
//!   equality operator on `T`, with both sides `T?`; `Coalesce` of a
//!   `T?` typed `T` or `T?`, as its fallback is. `OrElse`'s body
//!   and fallback have its type, unless it's `Void`, which drops their
//!   values, and calls in its body may bubble anywhere (F4); `Bubble` is
//!   marked may-bubble (F3).
//! - **N2** (parameters' defaults, subset B) an optional parameter of a
//!   function with a body is nullable, and the body starts with its
//!   default, in parameter order: `let x = coalesce(x', default)`.
//! - **V1** (function values, subset B) a `@fun` interface's one member
//!   is its abstract `apply` method, with no type parameters of its own;
//!   a `FunctionLiteral` or `FunctionRef` is typed as a `@fun` interface
//!   or function type whose signature is its declaration's (a top-level
//!   or static function, not generic, for a reference); a `CallValue`'s
//!   callee is a function value, and its arguments fit its signature
//!   (T3); `ToFun` takes a function type's value to a `@fun` interface
//!   with the same signature, and `FromFun` the reverse. **D5** a function
//!   literal's captures are what its body reads and assigns of the locals
//!   and parameters around it (R3 lets it use exactly those), and a
//!   captured local it assigns is a `var` (R6).
//! - **P1** in a program with spans, every node and declaration has one,
//!   apart from other libraries' declarations.

use crate::analysis::{named, top_level, walk};
use crate::decls::{DeclKind, Facts};
use crate::ids::{DeclId, NodeId, Span, StrId, TypeId};
use crate::nodes::{Flags, Kids};
use crate::ops::Op;
use crate::profile::Profile;
use crate::qname::{QNames, core_scalar, is_external, library_of};
use crate::schema::{Family, Kind, Node, Place, Slot, TypeRule};
use crate::text::writing_order;
use crate::types::{Signature, TypeData};
use crate::{Module, Program};
use std::collections::HashSet;
use std::fmt;

/// A validator rule, by its code.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub enum Rule {
    S1,
    S2,
    S3,
    S4,
    T1,
    T2,
    T3,
    T4,
    T5,
    R1,
    R2,
    R3,
    R4,
    R5,
    R6,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    D1,
    D2,
    D5,
    Q1,
    Q2,
    Q3,
    Q4,
    M1,
    M2,
    M3,
    C1,
    C2,
    C3,
    N1,
    N2,
    V1,
    P1,
}

impl fmt::Display for Rule {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        fmt::Debug::fmt(self, f)
    }
}

/// What a problem is about.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum At {
    /// A node, and its number in writing order.
    Node(NodeId, u32),
    Decl(DeclId),
}

/// One broken rule.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Problem {
    pub rule: Rule,
    pub at: At,
    pub message: String,
}

/// As golden tests list them: `R5 @12: …` for a node by its writing
/// order, `D1 #4: …` for a declaration.
impl fmt::Display for Problem {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self.at {
            At::Node(_, n) => write!(f, "{} @{n}: {}", self.rule, self.message),
            At::Decl(d) => write!(f, "{} #{}: {}", self.rule, d.0, self.message),
        }
    }
}

/// Checks a program against the model's invariants and a profile.
pub fn validate(p: &Program, profile: &Profile) -> Vec<Problem> {
    let order = writing_order(p);
    let mut position = vec![u32::MAX; p.nodes.len()];
    for (n, &id) in order.iter().enumerate() {
        position[id.index()] = n as u32;
    }
    let mut v = Validator {
        position,
        problems: Vec::new(),
        parents: vec![NodeId::NONE; p.nodes.len()],
        body: Body::default(),
        scope: vec![false; p.decls.len()],
        defined: vec![false; p.decls.len()],
        slot: vec![u32::MAX; p.decls.len()],
        module: (DeclId::NONE, 0),
    };
    for module in &p.modules {
        v.structure_module(p, module, profile);
    }
    if v.problems.is_empty() {
        v.bottom_types(p, profile);
        v.declarations(p);
        for (i, module) in p.modules.iter().enumerate() {
            v.module = (module.decl, i);
            v.module(p, module);
        }
        v.spans(p, &order);
    }
    v.sorted(p)
}

/// The state of checking one function body or init block.
#[derive(Default)]
struct Body {
    /// The function or module that owns its locals and labels.
    owner: DeclId,
    /// The function's result, or `NONE` in an init block.
    result: TypeId,
    /// Whether a call may bubble here.
    may_bubble: bool,
    in_init: bool,
    /// Declarations whose assignment is tracked, by slot.
    tracked: Vec<DeclId>,
    /// Locals in scope, innermost last.
    in_scope: Vec<DeclId>,
    /// Enclosing loops, innermost last, with the flow at their breaks and
    /// continues.
    loops: Vec<Loop>,
    /// The class or interface whose member this is, for `this` and
    /// private members; `NONE` outside one.
    class: DeclId,
    /// Whether it's a constructor, which may assign fields that aren't
    /// `var`s.
    constructing: bool,
    /// A function literal's captures, which it may use though another
    /// owns them.
    captures: Vec<DeclId>,
    /// Whether it's a generator literal's, which may `Await` and `Yield`.
    generator: bool,
    /// What a generator literal's body yields, `Y` for its factory's
    /// `SafeGenerator<Y>`; `NONE` elsewhere.
    yields: TypeId,
}

struct Loop {
    label: DeclId,
    breaks: Flow,
    continues: Flow,
}

/// What's assigned at a point: definitely (`done`) and possibly
/// (`maybe`), by tracked slot. A point that can't be reached is dead.
#[derive(Clone, Debug)]
struct Flow {
    live: bool,
    done: Vec<u64>,
    maybe: Vec<u64>,
}

impl Flow {
    fn new(slots: usize) -> Flow {
        let words = slots.div_ceil(64);
        Flow {
            live: true,
            done: vec![0; words],
            maybe: vec![0; words],
        }
    }

    fn dead(slots: usize) -> Flow {
        Flow {
            live: false,
            ..Flow::new(slots)
        }
    }

    fn has(bits: &[u64], slot: u32) -> bool {
        bits[slot as usize / 64] & (1 << (slot % 64)) != 0
    }

    fn set(&mut self, slot: u32, assigned: bool) {
        let (word, bit) = (slot as usize / 64, 1u64 << (slot % 64));
        if assigned {
            self.done[word] |= bit;
            self.maybe[word] |= bit;
        } else {
            self.done[word] &= !bit;
            self.maybe[word] &= !bit;
        }
    }

    /// Where two paths meet.
    fn join(&mut self, other: &Flow) {
        if !other.live {
            return;
        }
        if !self.live {
            *self = other.clone();
            return;
        }
        for (a, b) in self.done.iter_mut().zip(&other.done) {
            *a &= b;
        }
        for (a, b) in self.maybe.iter_mut().zip(&other.maybe) {
            *a |= b;
        }
    }
}

struct Validator {
    /// Each node's number in writing order.
    position: Vec<u32>,
    problems: Vec<Problem>,
    parents: Vec<NodeId>,
    body: Body,
    /// Locals in scope, by id.
    scope: Vec<bool>,
    /// Declarations already defined by a node.
    defined: Vec<bool>,
    /// Each tracked declaration's slot in `Flow`.
    slot: Vec<u32>,
    /// The module whose bodies are being checked, and its place in
    /// `Program::modules`.
    module: (DeclId, usize),
}

impl Validator {
    fn node_problem(&mut self, rule: Rule, node: NodeId, message: String) {
        let n = self.position.get(node.index()).copied().unwrap_or(u32::MAX);
        self.problems.push(Problem {
            rule,
            at: At::Node(node, n),
            message,
        });
    }

    fn decl_problem(&mut self, rule: Rule, decl: DeclId, message: String) {
        self.problems.push(Problem {
            rule,
            at: At::Decl(decl),
            message,
        });
    }

    /// Sorted by writing order; a declaration's problems come at the node
    /// that defines it.
    fn sorted(mut self, p: &Program) -> Vec<Problem> {
        let mut defined_at = vec![u32::MAX; p.decls.len()];
        for (id, &n) in self.position.iter().enumerate() {
            let id = NodeId(id as u32);
            if n == u32::MAX || p.nodes.parts(id).is_none() {
                continue;
            }
            if let Some(decl) = defined_decl(p, id) {
                if let Some(at) = defined_at.get_mut(decl.index()) {
                    *at = (*at).min(n);
                }
            }
        }
        for module in &p.modules {
            if let Some(at) = defined_at.get_mut(module.decl.index()) {
                *at = 0;
            }
        }
        // External declarations come first in writing order.
        for decl in p.decls.ids() {
            if is_external(p, decl) || p.uses.iter().any(|u| u.decl == decl) {
                defined_at[decl.index()] = 0;
            }
        }
        let key = |problem: &Problem| match problem.at {
            At::Node(_, n) => (n, 1, 0),
            At::Decl(d) => (
                defined_at.get(d.index()).copied().unwrap_or(u32::MAX),
                0,
                d.0,
            ),
        };
        self.problems.sort_by_key(key);
        self.problems
    }

    // Structure.

    fn structure_module(&mut self, p: &Program, module: &Module, profile: &Profile) {
        for item in module.all_items() {
            if self.structure(p, item, NodeId::NONE, None, profile, false) {
                let kind = p.nodes.kind(item);
                if !matches!(
                    kind,
                    Kind::Function
                        | Kind::BodilessFunction
                        | Kind::ModuleLet
                        | Kind::Class
                        | Kind::Interface
                ) {
                    let message = format!("a module item can't be a {}", kind.name());
                    self.node_problem(Rule::S3, item, message);
                }
            }
        }
        for &test in &module.tests {
            if self.structure(p, test, NodeId::NONE, None, profile, false)
                && p.nodes.kind(test) != Kind::Test
            {
                let kind = p.nodes.kind(test);
                let message = format!("a module's test can't be a {}", kind.name());
                self.node_problem(Rule::S3, test, message);
            }
        }
        for (init, which) in [(module.init, "init")]
            .into_iter()
            .chain(module.test_init.map(|t| (t, "test init")))
        {
            if self.structure(p, init, NodeId::NONE, None, profile, true)
                && p.nodes.kind(init) != Kind::Block
            {
                let message = format!("module #{}'s {which} isn't a Block", module.decl.0);
                self.node_problem(Rule::S3, init, message);
            }
        }
    }

    /// Checks a node and its children; returns whether the node exists
    /// and is new, so its own place can be checked.
    fn structure(
        &mut self,
        p: &Program,
        id: NodeId,
        parent: NodeId,
        place: Option<Place>,
        profile: &Profile,
        in_init: bool,
    ) -> bool {
        let at = if parent.is_none() { id } else { parent };
        if !p.nodes.contains(id) || (!parent.is_none() && id >= parent) {
            let message = if p.nodes.contains(id) {
                format!("child {} doesn't come before its parent", id.0)
            } else {
                format!("node {} doesn't exist", id.0)
            };
            if p.nodes.contains(at) {
                self.node_problem(Rule::S1, at, message);
            } else {
                self.problems.push(Problem {
                    rule: Rule::S1,
                    at: At::Node(at, u32::MAX),
                    message,
                });
            }
            return false;
        }
        if !self.parents[id.index()].is_none() {
            let message = format!(
                "its child {} has another parent too",
                p.nodes.kind(id).name()
            );
            self.node_problem(Rule::S2, at, message);
            return false;
        }
        self.parents[id.index()] = at;
        let kind = p.nodes.kind(id);
        if let Some(place) = place {
            let fits = match place {
                Place::Param => kind == Kind::Param,
                Place::Case => kind == Kind::Case,
                Place::Block => kind == Kind::Block,
                Place::Stmt => kind.family() == Family::Stmt,
                Place::Expr => kind.family() == Family::Expr,
                Place::Member => matches!(
                    kind,
                    Kind::Field
                        | Kind::StaticField
                        | Kind::Method
                        | Kind::AbstractMethod
                        | Kind::Function
                        | Kind::BodilessFunction
                ),
            };
            if !fits {
                let message = format!("{} can't go where {place:?} goes", kind.name());
                self.node_problem(Rule::S3, id, message);
            }
        }
        if matches!(kind, Kind::InitLet | Kind::InitStatic) && !in_init {
            let message = format!("{} outside an init block", kind.name());
            self.node_problem(Rule::S3, id, message);
        }
        if !profile.native.contains(kind.needs()) {
            let message = format!("the profile doesn't allow {}", kind.name());
            self.node_problem(Rule::S4, id, message);
        }
        let has_flag = kind.fields().iter().any(|f| f.slot == Slot::Flag);
        if p.nodes.flags(id).contains(Flags::MAY_BUBBLE) && !has_flag {
            let message = format!("{} can't be marked may-bubble", kind.name());
            self.node_problem(Rule::S1, id, message);
        }
        let Some(parts) = p.nodes.parts(id) else {
            let message = format!(
                "{} can't have {} children",
                kind.name(),
                p.nodes.kids(id).len()
            );
            self.node_problem(Rule::S1, id, message);
            return true;
        };
        let places = kind.fields().iter().filter_map(|f| match f.slot {
            Slot::Kid(_, place) => Some(place),
            _ => None,
        });
        let fields = kind
            .fields()
            .iter()
            .filter(|f| matches!(f.slot, Slot::Kid(..)));
        for ((kids, place), field) in parts.zip(places).zip(fields) {
            for &kid in p.nodes.list(kids) {
                let fresh = self.structure(p, kid, id, Some(place), profile, in_init);
                if fresh && p.nodes.kind(kid) == Kind::Omitted && field.name != "args" {
                    let message = "Omitted goes only where a call's argument does".into();
                    self.node_problem(Rule::S3, kid, message);
                }
            }
        }
        true
    }

    // Declarations.

    fn declarations(&mut self, p: &Program) {
        let used: Vec<DeclId> = p.uses.iter().map(|u| u.decl).collect();
        for &decl in &used {
            if !p.decls.contains(decl) || p.decls.get(decl).kind != DeclKind::Library {
                let message = "a uses section needs a library declaration".into();
                self.decl_problem(Rule::D2, decl, message);
            }
        }
        for decl in p.decls.ids() {
            let d = p.decls.get(decl);
            if d.kind == DeclKind::Removed {
                continue;
            }
            if d.facts.contains(Facts::COMPILER_MADE) && p.name(decl).is_empty() {
                self.decl_problem(Rule::D1, decl, "compiler-made without a hint".into());
            }
            let owner_kind = p.decls.contains(d.owner).then(|| p.decls.get(d.owner).kind);
            let types = [DeclKind::Class, DeclKind::Interface];
            let external_module = owner_kind == Some(DeclKind::Module) && is_external(p, decl);
            let owner_fits = match d.kind {
                DeclKind::Module => {
                    d.owner.is_none()
                        || (owner_kind == Some(DeclKind::Library) && used.contains(&d.owner))
                }
                DeclKind::Library => d.owner.is_none() && used.contains(&decl),
                DeclKind::Class | DeclKind::Interface => {
                    owner_kind == Some(DeclKind::Library)
                        || external_module
                        || (owner_kind == Some(DeclKind::Module)
                            && p.decls.get(d.owner).owner.is_none())
                }
                DeclKind::Function => matches!(
                    owner_kind,
                    Some(
                        DeclKind::Module
                            | DeclKind::Library
                            | DeclKind::Class
                            | DeclKind::Interface
                    )
                ),
                DeclKind::Method | DeclKind::Getter | DeclKind::Setter | DeclKind::StaticField => {
                    owner_kind.is_some_and(|k| types.contains(&k))
                }
                DeclKind::Constructor | DeclKind::Field => owner_kind == Some(DeclKind::Class),
                DeclKind::ModuleLet => owner_kind == Some(DeclKind::Module),
                DeclKind::Param => owner_kind.is_some_and(is_callable),
                DeclKind::Lambda => {
                    owner_kind.is_some_and(|k| k == DeclKind::Module || is_callable(k))
                }
                DeclKind::TypeParam => {
                    owner_kind.is_some_and(|k| types.contains(&k) || is_callable(k))
                }
                _ => owner_kind.is_some_and(|k| k == DeclKind::Module || is_callable(k)),
            };
            if !owner_fits {
                let message = if d.kind == DeclKind::Library {
                    "a library needs a uses section and no owner".into()
                } else {
                    format!("a {} can't be owned by #{}", d.kind.name(), d.owner.0)
                };
                self.decl_problem(Rule::D2, decl, message);
            }
            let ty = if p.types.contains(d.ty) {
                Some(p.types.get(d.ty))
            } else {
                None
            };
            match (d.kind, ty) {
                (DeclKind::Module | DeclKind::Label | DeclKind::Library, _) => {}
                (
                    DeclKind::Class | DeclKind::Interface,
                    Some(TypeData::Nominal { decl: of, .. }),
                ) if of == decl => {}
                (DeclKind::Class, Some(TypeData::Scalar(_)))
                    if library_of(p, decl) == "core"
                        && owner_kind == Some(DeclKind::Library)
                        && core_scalar(p.name(decl)) == Some(d.ty) => {}
                (DeclKind::Class | DeclKind::Interface, _) => {
                    let message = format!("a {}'s type is itself", d.kind.name());
                    self.decl_problem(Rule::T1, decl, message)
                }
                (
                    DeclKind::Function | DeclKind::Method | DeclKind::Lambda,
                    Some(TypeData::Function { .. }),
                ) => {}
                (DeclKind::Constructor, Some(TypeData::Function { .. })) => {
                    let owner_ty = owner_kind.map(|_| p.decls.get(d.owner).ty);
                    if p.signature(decl).map(|s| s.result) != owner_ty {
                        let message = "a constructor's result is its class".into();
                        self.decl_problem(Rule::C1, decl, message)
                    }
                }
                (DeclKind::Setter, Some(TypeData::Function { .. })) => {
                    let fits = p
                        .signature(decl)
                        .is_some_and(|s| s.params.len() == 1 && s.result == TypeId::VOID);
                    if !fits {
                        let message = "a setter takes one argument and returns Void".into();
                        self.decl_problem(Rule::C1, decl, message)
                    }
                }
                (DeclKind::Getter, Some(TypeData::Function { .. })) => {
                    if p.signature(decl).is_some_and(|s| !s.params.is_empty()) {
                        let message = "a getter takes no arguments".into();
                        self.decl_problem(Rule::T1, decl, message)
                    }
                }
                (
                    DeclKind::Function
                    | DeclKind::Method
                    | DeclKind::Getter
                    | DeclKind::Setter
                    | DeclKind::Constructor
                    | DeclKind::Lambda,
                    _,
                ) => {
                    let message = format!("a {}'s type is its signature", d.kind.name());
                    self.decl_problem(Rule::T1, decl, message)
                }
                (_, Some(TypeData::Scalar(_) | TypeData::Function { .. })) => {}
                (_, Some(TypeData::TypeParam { decl: of })) => {
                    let fits = p.decls.contains(of) && p.decls.get(of).kind == DeclKind::TypeParam;
                    if !fits {
                        let message = format!("#{} isn't a type parameter", of.0);
                        self.decl_problem(Rule::T5, decl, message)
                    }
                }
                (_, Some(TypeData::Never(_))) => {
                    let message = format!("a {} isn't typed Never, only a result", d.kind.name());
                    self.decl_problem(Rule::T5, decl, message)
                }
                (_, Some(TypeData::Nullable(inner))) => {
                    if inner == TypeId::VOID {
                        let message = "Void isn't nullable".into();
                        self.decl_problem(Rule::T5, decl, message)
                    }
                }
                (_, Some(TypeData::Nominal { decl: of, .. })) => {
                    let fits = p.decls.contains(of) && types.contains(&p.decls.get(of).kind);
                    if !fits {
                        let message = format!("#{} isn't a class or interface", of.0);
                        self.decl_problem(Rule::T5, decl, message)
                    }
                }
                (_, None) => {
                    let message = format!("a {} needs a type", d.kind.name());
                    self.decl_problem(Rule::T1, decl, message)
                }
            }
            for &s in p.decls.supers(decl) {
                if d.kind == DeclKind::TypeParam {
                    // A bound: any class or interface type.
                    if crate::analysis::nominal_decl(p, s).is_none() {
                        let message = format!("{} can't be a bound", p.show_type(s));
                        self.decl_problem(Rule::T5, decl, message);
                    }
                    continue;
                }
                let is_interface = crate::analysis::nominal_decl(p, s).is_some_and(|i| {
                    p.decls.contains(i) && p.decls.get(i).kind == DeclKind::Interface
                });
                if !types.contains(&d.kind) || !is_interface {
                    let message = format!("{} can't be its supertype", p.show_type(s));
                    self.decl_problem(Rule::C1, decl, message);
                }
            }
            let backends = !p.decls.backends(decl).is_empty();
            if backends && !d.facts.contains(Facts::CONNECTED) {
                let message = "only a connected declaration lists backends".into();
                self.decl_problem(Rule::Q4, decl, message);
            } else if backends && is_external(p, decl) {
                let message = "a connected declaration with backends needs a body".into();
                self.decl_problem(Rule::Q4, decl, message);
            }
        }
        self.qnames(p);
    }

    /// T5: `Never<T>` only where the profile keeps a bottom type
    /// ([`Features::NEVER`](crate::Features::NEVER)), which
    /// [`crate::nevers::lower`] leaves only there.
    fn bottom_types(&mut self, p: &Program, profile: &Profile) {
        if profile.native.contains(crate::Features::NEVER) {
            return;
        }
        let never = |ty: TypeId| {
            let sig = p.types.signature(ty);
            p.types.is_never(ty) || sig.is_some_and(|s| p.types.is_never(s.result))
        };
        for decl in p.decls.ids().filter(|&d| p.decls.contains(d)) {
            if never(p.decls.get(decl).ty) {
                let message = "a result is Never<T>, but the profile has no bottom type".into();
                self.decl_problem(Rule::T5, decl, message);
            }
        }
        for id in (0..p.nodes.len()).map(|i| NodeId(i as u32)) {
            if p.types.is_never(p.nodes.ty(id)) {
                let message = "a value is Never<T>, but the profile has no bottom type".into();
                self.node_problem(Rule::T5, id, message);
            }
        }
    }

    /// Q1 and Q2: every declaration that should have a qname has one, and
    /// no two share one.
    fn qnames(&mut self, p: &Program) {
        let names = QNames::derive(p);
        for (decl, why) in &names.bad_overrides {
            let message = format!("its qname override doesn't parse: {why}");
            self.decl_problem(Rule::Q2, *decl, message);
        }
        let mut seen: std::collections::HashMap<String, DeclId> = Default::default();
        for decl in p.decls.ids() {
            let d = p.decls.get(decl);
            match names.get(decl) {
                Some(q) => {
                    let text = q.to_string();
                    if let Some(first) = seen.insert(text.clone(), decl) {
                        let message = format!("its qname {text} is also #{}'s", first.0);
                        self.decl_problem(Rule::Q1, decl, message);
                    }
                }
                None => {
                    let exempt = matches!(
                        d.kind,
                        DeclKind::Removed | DeclKind::Label | DeclKind::Module | DeclKind::Library
                    ) || made_within(p, decl)
                        || names.bad_overrides.iter().any(|(b, _)| *b == decl);
                    if !exempt {
                        self.decl_problem(Rule::Q2, decl, "it has no qname".into());
                    }
                }
            }
        }
    }

    // Bodies: types, references, and flow.

    fn module(&mut self, p: &Program, module: &Module) {
        for item in module.all_items() {
            match p.nodes.get(item) {
                Node::Function { decl, params, body } => {
                    self.function(p, item, decl, params, Some(body))
                }
                Node::BodilessFunction { decl, params } => {
                    self.function(p, item, decl, params, None);
                    if p.decls.contains(decl) {
                        let d = p.decls.get(decl);
                        if !d.facts.contains(Facts::CONNECTED) {
                            let message =
                                format!("{} has no body, but isn't connected", show(p, decl));
                            self.node_problem(Rule::Q3, item, message);
                        } else if !p.decls.backends(decl).is_empty() {
                            let message = format!(
                                "{} lists backends, so it needs a body as their fallback",
                                show(p, decl)
                            );
                            self.node_problem(Rule::Q4, item, message);
                        }
                    }
                }
                Node::ModuleLet { decl } => {
                    if self.define(p, item, decl, DeclKind::ModuleLet) {
                        self.check_owner(p, item, decl, module.decl);
                    }
                }
                Node::Class { decl, members } | Node::Interface { decl, members } => {
                    self.class(p, item, decl, members, module.decl)
                }
                _ => {}
            }
        }
        for &test in &module.tests {
            if let Node::Test {
                decl, params, body, ..
            } = p.nodes.get(test)
            {
                self.function(p, test, decl, params, Some(body));
                if p.decls.contains(decl) {
                    self.check_owner(p, test, decl, module.decl);
                    self.test_signature(p, test, decl);
                }
            }
        }
        // The init block tracks this module's `let`s, for F5 and R6, and
        // its test init continues it, in its scope, as if it followed.
        let mut lets: Vec<DeclId> = (module.all_items())
            .filter_map(|item| match p.nodes.get(item) {
                Node::ModuleLet { decl } if p.decls.contains(decl) => Some(decl),
                _ => None,
            })
            .collect();
        if let Some(test_init) = module.test_init {
            body_lets(p, test_init, &mut lets);
        }
        self.start_body(p, module.decl, TypeId::NONE, true, module.init, &lets);
        self.body.in_init = true;
        let mut flow = Flow::new(self.body.tracked.len());
        let mark = self.body.in_scope.len();
        for block in [module.init].into_iter().chain(module.test_init) {
            match p.nodes.get(block) {
                Node::Block { stmts } => {
                    self.enter_function_lets(p, p.nodes.list(stmts));
                    for &stmt in p.nodes.list(stmts) {
                        self.stmt(p, stmt, &mut flow);
                    }
                }
                _ => self.stmt(p, block, &mut flow),
            }
        }
        self.leave_scopes(mark);
        self.end_body();
        self.library_parts(p, module);
    }

    /// M3: the library's parts, its items and init block, use nothing of
    /// its test parts, its test items and test init, which a build
    /// without tests leaves out.
    fn library_parts(&mut self, p: &Program, module: &Module) {
        let mut tests = HashSet::new();
        for root in module.test_items.iter().chain(&module.test_init) {
            match p.nodes.get(*root) {
                Node::Block { stmts } => {
                    for &stmt in p.nodes.list(stmts) {
                        tests.extend(defined_decl(p, stmt));
                    }
                }
                _ => tests.extend(defined_decl(p, *root)),
            }
        }
        if tests.is_empty() {
            return;
        }
        let init = match p.nodes.get(module.init) {
            Node::Block { stmts } => p.nodes.list(stmts),
            _ => &[],
        };
        for &root in module.items.iter().chain(init) {
            let mut found = None;
            let mut check = |d: DeclId| {
                if found.is_none() && top_level(p, d).is_some_and(|t| tests.contains(&t)) {
                    found = Some(d);
                }
            };
            named(p, root, &mut check);
            walk(p, root, &mut |id| match p.nodes.get(id) {
                Node::Local { local: d }
                | Node::Assign { local: d, .. }
                | Node::InitLet { target: d, .. }
                | Node::CallMethod { method: d, .. }
                | Node::GetField { field: d, .. }
                | Node::SetField { field: d, .. } => check(d),
                _ => {}
            });
            if let Some(d) = found {
                let message = format!("it uses {}, which only tests have", show(p, d));
                self.node_problem(Rule::M3, root, message);
            }
        }
    }

    /// M2: a test's signature, `fn (Test): Void throws Bubble`, with
    /// std's `Test`.
    fn test_signature(&mut self, p: &Program, test: NodeId, decl: DeclId) {
        let Some(sig) = p.signature(decl) else { return };
        let is_test = |t: TypeId| match p.types.get(t) {
            TypeData::Nominal { decl: c, .. } => {
                p.name(c) == "Test"
                    && library_of(p, c) == "std"
                    && p.decls.contains(p.decls.get(c).owner)
                    && p.name(p.decls.get(c).owner) == "testing"
            }
            _ => false,
        };
        let fits = matches!(sig.params[..], [t] if p.types.contains(t) && is_test(t))
            && sig.result == TypeId::VOID
            && sig.bubbles;
        if !fits {
            let message =
                "a test's signature is fn (Test): Void throws Bubble, with std's Test".into();
            self.node_problem(Rule::M2, test, message);
        }
    }

    /// A class or interface and its members.
    fn class(&mut self, p: &Program, id: NodeId, decl: DeclId, members: Kids, module: DeclId) {
        let kind = match p.nodes.kind(id) {
            Kind::Interface => DeclKind::Interface,
            _ => DeclKind::Class,
        };
        if !self.define(p, id, decl, kind) {
            return;
        }
        self.check_owner(p, id, decl, module);
        if p.decls.get(decl).facts.contains(Facts::FUN) {
            let only_apply = match p.nodes.list(members) {
                &[m] => matches!(p.nodes.get(m), Node::AbstractMethod { decl: d, .. }
                    if p.decls.contains(d)
                        && p.decls.get(d).kind == DeclKind::Method
                        && p.name(d) == "apply"
                        && p.decls.type_params(d).is_empty()),
                _ => false,
            };
            if kind != DeclKind::Interface || !only_apply {
                let message = "a @fun interface's one member is its abstract apply method".into();
                self.node_problem(Rule::V1, id, message);
            }
        }
        for &member in p.nodes.list(members) {
            match p.nodes.get(member) {
                Node::Field { decl: d } | Node::StaticField { decl: d } => {
                    let want = if p.nodes.kind(member) == Kind::Field {
                        DeclKind::Field
                    } else {
                        DeclKind::StaticField
                    };
                    if self.define(p, member, d, want) {
                        self.check_owner(p, member, d, decl);
                    }
                }
                Node::Method {
                    decl: d,
                    params,
                    body,
                } => self.member(p, member, d, decl, params, Some(body)),
                Node::AbstractMethod { decl: d, params } => {
                    // A class's connected method may have none: each
                    // backend's connection is its body (open question 48).
                    let connected =
                        p.decls.contains(d) && p.decls.get(d).facts.contains(Facts::CONNECTED);
                    if kind != DeclKind::Interface && !connected {
                        let message =
                            "only an interface's or connected methods may have no body".into();
                        self.node_problem(Rule::C1, member, message);
                    } else if kind == DeclKind::Class && !p.decls.backends(decl).is_empty() {
                        // A user's connected class without a Temper
                        // implementation; backends not listed would need one.
                        let message = format!(
                            "{} lists backends, so {} needs a body as their fallback",
                            show(p, decl),
                            show(p, d)
                        );
                        self.node_problem(Rule::Q4, member, message);
                    }
                    self.member(p, member, d, decl, params, None)
                }
                Node::Function {
                    decl: d,
                    params,
                    body,
                } => {
                    let f = DeclKind::Function;
                    self.function_like(p, member, d, f, params, Some(body), decl);
                    if p.decls.contains(d) {
                        self.check_owner(p, member, d, decl);
                    }
                }
                // A connected static function without a body, as std's
                // `RegexFormatter.regexCompileFormatted`.
                Node::BodilessFunction { decl: d, params } => {
                    let f = DeclKind::Function;
                    self.function_like(p, member, d, f, params, None, decl);
                    if p.decls.contains(d) {
                        self.check_owner(p, member, d, decl);
                        if !p.decls.get(d).facts.contains(Facts::CONNECTED) {
                            let message =
                                format!("{} has no body, but isn't connected", show(p, d));
                            self.node_problem(Rule::Q3, member, message);
                        }
                    }
                }
                _ => {}
            }
        }
    }

    /// A method, getter, setter, or constructor of `class`.
    fn member(
        &mut self,
        p: &Program,
        id: NodeId,
        decl: DeclId,
        class: DeclId,
        params: Kids,
        body: Option<NodeId>,
    ) {
        let kinds = [
            DeclKind::Method,
            DeclKind::Getter,
            DeclKind::Setter,
            DeclKind::Constructor,
        ];
        if !p.decls.contains(decl) || !kinds.contains(&p.decls.get(decl).kind) {
            self.reference(p, id, decl, &kinds);
            return;
        }
        let kind = p.decls.get(decl).kind;
        self.function_like(p, id, decl, kind, params, body, class);
        self.check_owner(p, id, decl, class);
    }

    fn function(
        &mut self,
        p: &Program,
        id: NodeId,
        decl: DeclId,
        params: Kids,
        body: Option<NodeId>,
    ) {
        self.function_like(p, id, decl, DeclKind::Function, params, body, DeclId::NONE)
    }

    /// A function, or a member of `class`, with its parameters and body.
    #[allow(clippy::too_many_arguments)]
    fn function_like(
        &mut self,
        p: &Program,
        id: NodeId,
        decl: DeclId,
        kind: DeclKind,
        params: Kids,
        body: Option<NodeId>,
        class: DeclId,
    ) {
        if !self.define(p, id, decl, kind) {
            return;
        }
        let Some(Signature {
            params: types,
            optional,
            result,
            bubbles,
        }) = p.signature(decl)
        else {
            return;
        };
        let params = p.nodes.list(params);
        if params.len() != types.len() {
            let message = format!(
                "the signature has {} parameters, the function {}",
                types.len(),
                params.len()
            );
            self.node_problem(Rule::T1, id, message);
        }
        // A constructor's body returns nothing; its call makes the object.
        let result = if kind == DeclKind::Constructor {
            TypeId::VOID
        } else {
            result
        };
        self.start_body(p, decl, result, bubbles, body.unwrap_or(NodeId::NONE), &[]);
        self.body.class = class;
        self.body.constructing = kind == DeclKind::Constructor;
        for (i, &param) in params.iter().enumerate() {
            let Node::Param { decl: d } = p.nodes.get(param) else {
                continue;
            };
            if !self.define(p, param, d, DeclKind::Param) {
                continue;
            }
            self.check_owner(p, param, d, decl);
            if types.get(i).is_some_and(|&t| t != p.decls.get(d).ty) {
                let message = format!("{}'s type differs from the signature's", show(p, d));
                self.node_problem(Rule::T1, param, message);
            }
            let marked = p.decls.get(d).facts.contains(Facts::OPTIONAL);
            if optional.get(i).is_some_and(|&o| o != marked) {
                let message = format!("{}'s optionality differs from the signature's", show(p, d));
                self.node_problem(Rule::T1, param, message);
            }
            self.enter_scope(d);
        }
        let Some(body) = body else {
            self.end_body();
            return;
        };
        self.defaults(p, params, body);
        let mut flow = Flow::new(self.body.tracked.len());
        self.stmt(p, body, &mut flow);
        if flow.live && result != TypeId::VOID {
            let message = format!("{} can reach its end without returning", show(p, decl));
            self.node_problem(Rule::F1, id, message);
        }
        self.end_body();
    }

    /// N2: a body starts with its optional parameters' defaults, in
    /// order, each a `let` of the parameter's value coalesced with its
    /// default ([`crate::analysis::defaults`]); each optional parameter
    /// is nullable, since null means its default.
    fn defaults(&mut self, p: &Program, params: &[NodeId], body: NodeId) {
        let optional: Vec<(NodeId, DeclId)> = (params.iter())
            .filter_map(|&param| match p.nodes.get(param) {
                Node::Param { decl } if p.decls.get(decl).facts.contains(Facts::OPTIONAL) => {
                    Some((param, decl))
                }
                _ => None,
            })
            .collect();
        if optional.is_empty() {
            return;
        }
        let found = crate::analysis::defaults(p, body);
        for (i, &(param, d)) in optional.iter().enumerate() {
            if !p.types.is_nullable(p.decls.get(d).ty) {
                let message = format!("{} is optional, so it should be nullable", show(p, d));
                self.node_problem(Rule::N2, param, message);
            }
            if found.get(i).is_none_or(|f| f.param != d) {
                let message = format!(
                    "{}'s default should be the body's statement {} after those before it",
                    show(p, d),
                    i + 1
                );
                self.node_problem(Rule::N2, param, message);
            }
        }
    }

    /// Starts checking a body: tracks its `let`s, and `extra`.
    fn start_body(
        &mut self,
        p: &Program,
        owner: DeclId,
        result: TypeId,
        may_bubble: bool,
        body: NodeId,
        extra: &[DeclId],
    ) {
        let mut tracked = extra.to_vec();
        let mut lets = Vec::new();
        body_lets(p, body, &mut lets);
        tracked.extend(
            lets.into_iter()
                .filter(|d| self.slot[d.index()] == u32::MAX),
        );
        for (slot, &decl) in tracked.iter().enumerate() {
            self.slot[decl.index()] = slot as u32;
        }
        self.body = Body {
            owner,
            result,
            may_bubble,
            in_init: false,
            tracked,
            in_scope: Vec::new(),
            loops: Vec::new(),
            class: DeclId::NONE,
            constructing: false,
            captures: Vec::new(),
            generator: false,
            yields: TypeId::NONE,
        };
    }

    fn end_body(&mut self) {
        for &decl in &self.body.tracked {
            self.slot[decl.index()] = u32::MAX;
        }
        for &decl in &self.body.in_scope {
            self.scope[decl.index()] = false;
        }
    }

    fn enter_scope(&mut self, decl: DeclId) {
        self.scope[decl.index()] = true;
        self.body.in_scope.push(decl);
    }

    /// Puts the function `let`s a block's groups use early in scope from
    /// its start, so a run of them may use each other (R3); only literals
    /// use them early, and the outer body's tracking stops at a literal.
    fn enter_function_lets(&mut self, p: &Program, stmts: &[NodeId]) {
        for group in crate::analysis::function_groups(p, stmts) {
            for local in group.early {
                if p.decls.contains(local) && p.decls.get(local).owner == self.body.owner {
                    self.enter_scope(local);
                }
            }
        }
    }

    fn leave_scopes(&mut self, to: usize) {
        for decl in self.body.in_scope.drain(to..) {
            self.scope[decl.index()] = false;
        }
    }

    /// Checks that a node's declaration exists, is of its kind, and is
    /// defined only here.
    fn define(&mut self, p: &Program, id: NodeId, decl: DeclId, kind: DeclKind) -> bool {
        if !self.reference(p, id, decl, &[kind]) {
            return false;
        }
        if std::mem::replace(&mut self.defined[decl.index()], true) {
            let message = format!("{} is defined twice", show(p, decl));
            self.node_problem(Rule::R1, id, message);
        }
        true
    }

    fn check_owner(&mut self, p: &Program, id: NodeId, decl: DeclId, owner: DeclId) {
        if p.decls.get(decl).owner != owner {
            let message = format!("{}'s owner should be #{}", show(p, decl), owner.0);
            self.node_problem(Rule::D2, id, message);
        }
    }

    /// Checks that a reference names an existing declaration of one of
    /// `kinds` (R1 and R2).
    fn reference(&mut self, p: &Program, id: NodeId, decl: DeclId, kinds: &[DeclKind]) -> bool {
        if !p.decls.contains(decl) {
            let message = format!("declaration #{} doesn't exist", decl.0);
            self.node_problem(Rule::R1, id, message);
            return false;
        }
        let kind = p.decls.get(decl).kind;
        if !kinds.contains(&kind) {
            let message = format!(
                "{} needs a {}, not {}, a {}",
                p.nodes.kind(id).name(),
                kinds[0].name(),
                show(p, decl),
                kind.name()
            );
            self.node_problem(Rule::R2, id, message);
            return false;
        }
        self.other_module(p, id, decl);
        true
    }

    /// M1: a reference to another of the program's modules' declarations.
    fn other_module(&mut self, p: &Program, id: NodeId, decl: DeclId) {
        let (here, index) = self.module;
        let d = p.decls.get(decl);
        if here.is_none()
            || !matches!(d.kind, DeclKind::Function | DeclKind::ModuleLet)
            || !p.decls.contains(d.owner)
            || d.owner == here
            || p.decls.get(d.owner).kind != DeclKind::Module
            || is_external(p, decl)
        {
            return;
        }
        let at = p.modules.iter().position(|m| m.decl == d.owner);
        let problem = if matches!(p.nodes.kind(id), Kind::SetModuleLet | Kind::InitLet) {
            Some(format!("{} is another module's", show(p, decl)))
        } else if !d.facts.contains(Facts::EXPORTED) {
            Some(format!("{} isn't exported from its module", show(p, decl)))
        } else if at.is_none_or(|at| at >= index) {
            Some(format!(
                "{} is in a module that comes later, so can't be imported",
                show(p, decl)
            ))
        } else {
            None
        };
        if let Some(message) = problem {
            self.node_problem(Rule::M1, id, message);
        }
    }

    /// Checks that a child's type is nullable.
    fn nullable_operand(&mut self, p: &Program, child: NodeId, role: &str) {
        let have = p.nodes.ty(child);
        if !p.types.is_nullable(have) && p.nodes.kind(child) != Kind::Error {
            let message = format!("{role} is {}, not a nullable type", p.show_type(have));
            self.node_problem(Rule::N1, child, message);
        }
    }

    /// Checks that a child's type is exactly what its role wants, or
    /// `Never<T>` for a `T` that is, which never completes.
    fn fits(&mut self, p: &Program, child: NodeId, want: TypeId, role: &str) {
        let have = p.nodes.ty(child);
        let fits = have == want || p.types.fallback(have) == want;
        if !fits && p.nodes.kind(child) != Kind::Error && !have.is_none() {
            let message = format!(
                "{role} is {}, but should be {}",
                p.show_type(have),
                p.show_type(want)
            );
            self.node_problem(Rule::T3, child, message);
        }
    }

    fn slot_of(&self, decl: DeclId) -> Option<u32> {
        let slot = self.slot[decl.index()];
        (slot != u32::MAX).then_some(slot)
    }

    /// Records an assignment, reporting a second one of a non-`var`.
    fn assigned(&mut self, p: &Program, id: NodeId, decl: DeclId, flow: &mut Flow) {
        let Some(slot) = self.slot_of(decl) else {
            return;
        };
        let var = p.decls.get(decl).facts.contains(Facts::VAR);
        if flow.live && !var && Flow::has(&flow.maybe, slot) {
            let what = match p.nodes.kind(id) {
                Kind::InitLet => "initialized",
                _ => "assigned",
            };
            let message = format!("{} isn't a var, but may be {what} twice", show(p, decl));
            self.node_problem(Rule::R6, id, message);
        }
        flow.set(slot, true);
    }

    fn stmt(&mut self, p: &Program, id: NodeId, flow: &mut Flow) {
        match p.nodes.get(id) {
            Node::Block { stmts } => {
                let mark = self.body.in_scope.len();
                self.enter_function_lets(p, p.nodes.list(stmts));
                for &stmt in p.nodes.list(stmts) {
                    self.stmt(p, stmt, flow);
                }
                self.leave_scopes(mark);
            }
            Node::Let { local, init } => {
                if let Some(init) = init {
                    self.expr(p, init, flow);
                }
                if !self.define(p, id, local, DeclKind::Local) {
                    return;
                }
                self.check_owner(p, id, local, self.body.owner);
                if let Some(init) = init {
                    self.fits(p, init, p.decls.get(local).ty, "the initializer");
                }
                if let Some(slot) = self.slot_of(local) {
                    // A fresh binding each time it runs, as in a loop.
                    flow.set(slot, init.is_some());
                }
                self.enter_scope(local);
            }
            Node::Assign { local, value } => {
                self.expr(p, value, flow);
                if !self.reference(p, id, local, &[DeclKind::Local, DeclKind::Param]) {
                    return;
                }
                self.in_scope(p, id, local);
                self.fits(p, value, p.decls.get(local).ty, "the value");
                let d = p.decls.get(local);
                let captured = d.owner != self.body.owner;
                if captured && !d.facts.contains(Facts::VAR) {
                    let message = format!(
                        "{} isn't a var, so a literal can't assign it",
                        show(p, local)
                    );
                    self.node_problem(Rule::R6, id, message);
                } else if d.kind == DeclKind::Param && !d.facts.contains(Facts::VAR) {
                    let message = format!("{} isn't a var", show(p, local));
                    self.node_problem(Rule::R6, id, message);
                }
                self.assigned(p, id, local, flow);
            }
            Node::SetModuleLet { target, value } => {
                self.expr(p, value, flow);
                if !self.reference(p, id, target, &[DeclKind::ModuleLet]) {
                    return;
                }
                self.fits(p, value, p.decls.get(target).ty, "the value");
                if !p.decls.get(target).facts.contains(Facts::VAR) {
                    let message = format!("{} isn't a var", show(p, target));
                    self.node_problem(Rule::R6, id, message);
                }
                if let Some(slot) = self.slot_of(target) {
                    flow.set(slot, true);
                }
            }
            Node::InitLet { target, value } => {
                self.expr(p, value, flow);
                if !self.reference(p, id, target, &[DeclKind::ModuleLet]) {
                    return;
                }
                self.fits(p, value, p.decls.get(target).ty, "the value");
                if p.decls.get(target).owner != self.body.owner {
                    let message = format!("{} is another module's", show(p, target));
                    self.node_problem(Rule::R3, id, message);
                }
                self.assigned(p, id, target, flow);
            }
            Node::If { cond, then, els } => {
                self.condition(p, cond, flow);
                let mut other = flow.clone();
                self.stmt(p, then, flow);
                if let Some(els) = els {
                    self.stmt(p, els, &mut other);
                }
                flow.join(&other);
            }
            Node::Switch { scrutinee, cases } => {
                self.expr(p, scrutinee, flow);
                self.switch_labels(p, scrutinee, cases);
                let before = flow.clone();
                let mut after = Flow::dead(flow.done.len() * 64);
                let mut has_default = false;
                for &case in p.nodes.list(cases) {
                    let Node::Case { labels, body } = p.nodes.get(case) else {
                        continue;
                    };
                    has_default |= labels.is_empty();
                    let mut inside = before.clone();
                    for &label in p.nodes.list(labels) {
                        self.expr(p, label, &mut inside);
                    }
                    self.stmt(p, body, &mut inside);
                    after.join(&inside);
                }
                if !has_default {
                    after.join(&before);
                }
                *flow = after;
            }
            Node::While { label, cond, body } => {
                self.repeat(p, id, label, flow);
                self.condition(p, cond, flow);
                let mut exit = if is_true(p, cond) {
                    Flow::dead(flow.done.len() * 64)
                } else {
                    flow.clone()
                };
                self.stmt(p, body, flow);
                self.end_loop(&mut exit);
                *flow = exit;
            }
            Node::DoWhile { label, body, cond } => {
                self.repeat(p, id, label, flow);
                self.stmt(p, body, flow);
                if let Some(l) = self.body.loops.last_mut() {
                    flow.join(&l.continues.clone());
                }
                self.condition(p, cond, flow);
                let mut exit = if is_true(p, cond) {
                    Flow::dead(flow.done.len() * 64)
                } else {
                    flow.clone()
                };
                self.end_loop(&mut exit);
                *flow = exit;
            }
            Node::Break { label } | Node::Continue { label } => {
                if self.reference(p, id, label, &[DeclKind::Label]) {
                    let is_break = p.nodes.kind(id) == Kind::Break;
                    match self.body.loops.iter_mut().rev().find(|l| l.label == label) {
                        Some(l) if is_break => l.breaks.join(flow),
                        Some(l) => l.continues.join(flow),
                        None => {
                            let message = format!("{} isn't an enclosing loop", show(p, label));
                            self.node_problem(Rule::R4, id, message);
                        }
                    }
                }
                flow.live = false;
            }
            Node::Return { value } => {
                if let Some(value) = value {
                    self.expr(p, value, flow);
                }
                let result = self.body.result;
                if self.body.in_init {
                    self.node_problem(Rule::F2, id, "a return outside a function".into());
                } else if result == TypeId::VOID && value.is_some() {
                    let message = "a Void function's return has no value".into();
                    self.node_problem(Rule::F2, id, message);
                } else if result != TypeId::VOID && value.is_none() {
                    let message = format!("the return needs a {}", p.show_type(result));
                    self.node_problem(Rule::F2, id, message);
                } else if let Some(value) = value {
                    self.fits(p, value, result, "the returned value");
                }
                flow.live = false;
            }
            Node::ExprStatement { expr } => self.expr(p, expr, flow),
            Node::Yield { value } => {
                if let Some(value) = value {
                    self.expr(p, value, flow);
                }
                if !self.body.generator {
                    let message = "a yield outside a generator's own body".into();
                    self.node_problem(Rule::F6, id, message);
                } else if let Some(value) = value {
                    self.fits(p, value, self.body.yields, "the yielded value");
                }
            }
            Node::SetField {
                field,
                object,
                value,
            } => {
                self.expr(p, object, flow);
                self.expr(p, value, flow);
                if !self.reference(p, id, field, &[DeclKind::Field]) {
                    return;
                }
                self.visible(p, id, field);
                let d = p.decls.get(field);
                self.receiver_fits(p, object, d.owner);
                let have = Some(p.nodes.ty(object));
                let (from, to) = crate::analysis::bindings(p, field, have, &[]);
                let want = p.types.substituted(d.ty, &from, &to).unwrap_or(d.ty);
                self.fits(p, value, want, "the value");
                let on_this = p.nodes.kind(object) == Kind::This;
                let constructing = self.body.constructing && self.body.class == d.owner;
                let may_set = d.facts.contains(Facts::VAR) || (on_this && constructing);
                if !may_set {
                    let message = format!(
                        "{} isn't a var, so only its class's constructor may set it, on this",
                        show(p, field)
                    );
                    self.node_problem(Rule::C3, id, message);
                }
            }
            Node::InitStatic { target, value } => {
                // Its value is its class's, which may use its private
                // members, as a private constructor.
                let outer = self.body.class;
                if p.decls.contains(target) {
                    self.body.class = p.decls.get(target).owner;
                }
                self.expr(p, value, flow);
                self.body.class = outer;
                if !self.reference(p, id, target, &[DeclKind::StaticField]) {
                    return;
                }
                self.fits(p, value, p.decls.get(target).ty, "the value");
                let home = crate::analysis::home(p, target);
                if home != Some(self.body.owner) {
                    let message = format!("{} is another module's", show(p, target));
                    self.node_problem(Rule::C3, id, message);
                }
            }
            _ => {}
        }
    }

    /// C2: a private member is used only in its class, and a protected
    /// one only there and below.
    fn visible(&mut self, p: &Program, id: NodeId, member: DeclId) {
        let d = p.decls.get(member);
        let here = self.body.class;
        let ok = if d.facts.contains(Facts::PRIVATE) {
            // A private constructor is its module's, as Temper's.
            let module = |d: DeclId| crate::analysis::home(p, d);
            here == d.owner
                || (d.kind == DeclKind::Constructor
                    && module(d.owner).is_some()
                    && module(d.owner) == module(self.body.owner))
        } else if d.facts.contains(Facts::PROTECTED) {
            here == d.owner
                || (!here.is_none() && crate::analysis::ancestors(p, here).contains(&d.owner))
        } else {
            true
        };
        if !ok {
            let which = if d.facts.contains(Facts::PRIVATE) {
                "private"
            } else {
                "protected"
            };
            let message = format!("{} is {which} to #{}", show(p, member), d.owner.0);
            self.node_problem(Rule::C2, id, message);
        }
    }

    /// C3: whether a type is a class's or interface's.
    fn nominal(&mut self, p: &Program, id: NodeId, ty: TypeId, role: &str) {
        if crate::analysis::nominal_decl(p, ty).is_none() && p.nodes.kind(id) != Kind::Error {
            let message = format!("{role} is {}, not a class or interface", p.show_type(ty));
            self.node_problem(Rule::C3, id, message);
        }
    }

    /// Starts a loop: defines its label, and since its body may run
    /// again, counts what it assigns as maybe assigned already.
    fn repeat(&mut self, p: &Program, id: NodeId, label: DeclId, flow: &mut Flow) {
        if self.define(p, id, label, DeclKind::Label) {
            self.check_owner(p, id, label, self.body.owner);
        }
        let mut stack = p.nodes.kids(id).to_vec();
        while let Some(node) = stack.pop() {
            let target = match p.nodes.get(node) {
                Node::Assign { local: d, .. }
                | Node::SetModuleLet { target: d, .. }
                | Node::InitLet { target: d, .. } => Some(d),
                _ => None,
            };
            let target = target.filter(|&d| p.decls.contains(d));
            if let Some(slot) = target.and_then(|d| self.slot_of(d)) {
                flow.maybe[slot as usize / 64] |= 1 << (slot % 64);
            }
            stack.extend(p.nodes.kids(node));
        }
        let slots = flow.done.len() * 64;
        self.body.loops.push(Loop {
            label,
            breaks: Flow::dead(slots),
            continues: Flow::dead(slots),
        });
    }

    fn end_loop(&mut self, exit: &mut Flow) {
        if let Some(l) = self.body.loops.pop() {
            exit.join(&l.breaks);
        }
    }

    /// F7: a `Switch`'s scrutinee and labels.
    fn switch_labels(&mut self, p: &Program, scrutinee: NodeId, cases: Kids) {
        let ty = p.nodes.ty(scrutinee);
        let int = matches!(ty, TypeId::INT32 | TypeId::INT64);
        if !int && p.nodes.kind(scrutinee) != Kind::Error {
            let message = format!("a switch on {}, not Int32 or Int64", p.show_type(ty));
            self.node_problem(Rule::F7, scrutinee, message);
        }
        let mut seen = std::collections::HashSet::new();
        let cases = p.nodes.list(cases);
        for (i, &case) in cases.iter().enumerate() {
            let Node::Case { labels, .. } = p.nodes.get(case) else {
                continue;
            };
            if labels.is_empty() && i + 1 != cases.len() {
                let message = "a default that isn't the last case".into();
                self.node_problem(Rule::F7, case, message);
            }
            for &label in p.nodes.list(labels) {
                let value = match p.nodes.get(label) {
                    Node::Int32Lit { value } if ty == TypeId::INT32 => i64::from(value),
                    Node::Int64Lit { value } if ty == TypeId::INT64 => value,
                    _ => {
                        if int {
                            let message =
                                format!("a case's label isn't a literal {}", p.show_type(ty));
                            self.node_problem(Rule::F7, label, message);
                        }
                        continue;
                    }
                };
                if !seen.insert(value) {
                    let message = format!("the label {value} is in another case too");
                    self.node_problem(Rule::F7, label, message);
                }
            }
        }
    }

    fn condition(&mut self, p: &Program, cond: NodeId, flow: &mut Flow) {
        self.expr(p, cond, flow);
        self.fits(p, cond, TypeId::BOOLEAN, "the condition");
    }

    fn in_scope(&mut self, p: &Program, id: NodeId, local: DeclId) {
        let own =
            p.decls.get(local).owner == self.body.owner || self.body.captures.contains(&local);
        if !self.scope[local.index()] || !own {
            let message = format!("{} isn't in scope here", show(p, local));
            self.node_problem(Rule::R3, id, message);
        }
    }

    fn expr(&mut self, p: &Program, id: NodeId, flow: &mut Flow) {
        let ty = p.nodes.ty(id);
        let kind = p.nodes.kind(id);
        if ty.is_none() || !p.types.contains(ty) {
            let message = format!("{} needs a type", kind.name());
            self.node_problem(Rule::T1, id, message);
        } else if let Some(want) = self.rule_type(p, id) {
            if want != ty && kind != Kind::Error && !want.is_none() {
                let message = format!(
                    "{} is {}, not {}",
                    kind.name(),
                    p.show_type(want),
                    p.show_type(ty)
                );
                self.node_problem(Rule::T2, id, message);
            }
        }
        match p.nodes.get(id) {
            Node::StringLit { value: s } => self.string(p, id, s),
            Node::TypeValue { of } if !p.types.contains(of) => {
                let message = format!("type {} doesn't exist", of.0);
                self.node_problem(Rule::R1, id, message);
            }
            Node::Error { message: s, .. } => {
                self.string(p, id, s);
                // It fails when run, so nothing after it runs.
                flow.live = false;
            }
            Node::Local { local } => {
                if self.reference(p, id, local, &[DeclKind::Local, DeclKind::Param]) {
                    self.in_scope(p, id, local);
                    if let Some(slot) = self.slot_of(local) {
                        if flow.live && !Flow::has(&flow.done, slot) {
                            let message =
                                format!("{} may be read before it's assigned", show(p, local));
                            self.node_problem(Rule::R5, id, message);
                        }
                    }
                }
            }
            Node::GetModuleLet { target } => {
                if self.reference(p, id, target, &[DeclKind::ModuleLet]) {
                    if let Some(slot) = self.slot_of(target) {
                        if flow.live && !Flow::has(&flow.done, slot) {
                            let message =
                                format!("{} may be read before it's initialized", show(p, target));
                            self.node_problem(Rule::F5, id, message);
                        }
                    }
                }
            }
            Node::CallFunction {
                callee,
                type_args,
                may_bubble,
                args,
            } => {
                for &arg in p.nodes.list(args) {
                    self.expr(p, arg, flow);
                }
                if !self.reference(p, id, callee, &[DeclKind::Function]) {
                    return;
                }
                self.visible(p, id, callee);
                let type_args = self.type_args(p, id, type_args, callee);
                if let Some(sig) = p.signature(callee) {
                    let what = show(p, callee);
                    let binds = crate::analysis::bindings(p, callee, None, &type_args);
                    self.call(p, id, &what, sig, (may_bubble, args), &binds);
                }
            }
            Node::CallMethod {
                method,
                type_args,
                may_bubble,
                receiver,
                args,
            } => {
                self.expr(p, receiver, flow);
                for &arg in p.nodes.list(args) {
                    self.expr(p, arg, flow);
                }
                let kinds = [DeclKind::Method, DeclKind::Getter, DeclKind::Setter];
                if !self.reference(p, id, method, &kinds) {
                    return;
                }
                self.visible(p, id, method);
                let owner = p.decls.get(method).owner;
                if p.decls.contains(owner) {
                    self.receiver_fits(p, receiver, owner);
                }
                let type_args = self.type_args(p, id, type_args, method);
                if let Some(sig) = p.signature(method) {
                    let what = show(p, method);
                    let have = Some(p.nodes.ty(receiver));
                    let binds = crate::analysis::bindings(p, method, have, &type_args);
                    self.call(p, id, &what, sig, (may_bubble, args), &binds);
                }
            }
            Node::Op {
                op,
                may_bubble,
                args,
            } => {
                for &arg in p.nodes.list(args) {
                    self.expr(p, arg, flow);
                }
                let Some(info) = op.info() else {
                    let message = format!("operator {} doesn't exist", op.0);
                    self.node_problem(Rule::R1, id, message);
                    return;
                };
                let what = format!("%{}", info.name);
                let sig = Signature {
                    params: info.params,
                    optional: &[],
                    result: info.result,
                    bubbles: info.bubbles,
                };
                self.call(
                    p,
                    id,
                    &what,
                    sig,
                    (may_bubble, args),
                    &(Vec::new(), Vec::new()),
                );
            }
            Node::And { left, right } | Node::Or { left, right } => {
                self.condition(p, left, flow);
                let mut taken = flow.clone();
                self.condition(p, right, &mut taken);
                flow.join(&taken);
            }
            Node::IfValue { cond, then, els } => {
                self.condition(p, cond, flow);
                let mut other = flow.clone();
                self.expr(p, then, flow);
                self.fits(p, then, ty, "the branch");
                self.expr(p, els, &mut other);
                self.fits(p, els, ty, "the branch");
                flow.join(&other);
            }
            Node::Null {} => {
                if !p.types.is_nullable(ty) {
                    let message = format!("null is {}, not a nullable type", p.show_type(ty));
                    self.node_problem(Rule::N1, id, message);
                }
            }
            Node::IsNull { value } => {
                self.expr(p, value, flow);
                self.nullable_operand(p, value, "the value checked");
            }
            Node::NonNull { value } | Node::AsNullable { value } => {
                self.expr(p, value, flow);
                // A `Never<T>` converts as the `T` it stands for.
                let have = p.types.fallback(p.nodes.ty(value));
                let (nullable, plain) = if p.nodes.kind(id) == Kind::NonNull {
                    (have, ty)
                } else {
                    (ty, have)
                };
                let fine = p.types.is_nullable(nullable)
                    && !p.types.is_nullable(plain)
                    && p.types.non_null(nullable) == plain;
                if !fine {
                    let message = format!(
                        "{} converts {} to {}, which isn't between T? and T",
                        p.nodes.kind(id).name(),
                        p.show_type(have),
                        p.show_type(ty)
                    );
                    self.node_problem(Rule::N1, id, message);
                }
            }
            Node::Coalesce { left, right } => {
                self.expr(p, left, flow);
                self.nullable_operand(p, left, "the value coalesced");
                let have = p.nodes.ty(left);
                if ty != have && ty != p.types.non_null(have) && p.types.is_nullable(have) {
                    let message = format!(
                        "coalesce of {} is {}, which isn't it with or without null",
                        p.show_type(have),
                        p.show_type(ty)
                    );
                    self.node_problem(Rule::N1, id, message);
                }
                // The right side runs only if the left is null.
                let mut other = flow.clone();
                self.expr(p, right, &mut other);
                self.fits(p, right, ty, "the fallback");
                flow.join(&other);
            }
            Node::EqNullable { op, left, right } => {
                for side in [left, right] {
                    self.expr(p, side, flow);
                }
                let Some(info) = op.info() else {
                    let message = format!("operator {} doesn't exist", op.0);
                    self.node_problem(Rule::R1, id, message);
                    return;
                };
                let is_eq = info.name.ends_with(".eq")
                    && info.params.len() == 2
                    && info.params[0] == info.params[1]
                    && info.result == TypeId::BOOLEAN;
                if !is_eq {
                    let message = format!("%{} isn't an equality", info.name);
                    self.node_problem(Rule::N1, id, message);
                    return;
                }
                for side in [left, right] {
                    let have = p.nodes.ty(side);
                    let fits =
                        p.types.is_nullable(have) && p.types.non_null(have) == info.params[0];
                    if !fits && p.nodes.kind(side) != Kind::Error {
                        let message = format!(
                            "a side of eqNullable is {}, but should be {}?",
                            p.show_type(have),
                            p.show_type(info.params[0])
                        );
                        self.node_problem(Rule::T3, side, message);
                    }
                }
            }
            Node::OrElse { body, fallback } => {
                let before = flow.clone();
                let may_bubble = std::mem::replace(&mut self.body.may_bubble, true);
                self.expr(p, body, flow);
                self.body.may_bubble = may_bubble;
                // A `Void` one drops its parts' values.
                if ty != TypeId::VOID {
                    self.fits(p, body, ty, "the body");
                }
                // The fallback runs after any part of the body.
                let mut other = before;
                self.expr(p, fallback, &mut other);
                if ty != TypeId::VOID {
                    self.fits(p, fallback, ty, "the fallback");
                }
                flow.join(&other);
            }
            Node::Bubble { may_bubble } => {
                if !may_bubble {
                    let message = "bubble() bubbles, but isn't marked".into();
                    self.node_problem(Rule::F3, id, message);
                } else if !self.body.may_bubble {
                    let message =
                        format!("bubble() bubbles, but {} can't", show(p, self.body.owner));
                    self.node_problem(Rule::F4, id, message);
                }
                // Nothing after it runs.
                flow.live = false;
            }
            Node::Panic {} => flow.live = false,
            Node::Await {
                may_bubble,
                promise,
            } => {
                self.expr(p, promise, flow);
                if !self.body.generator {
                    let message = "an await outside a generator's own body".into();
                    self.node_problem(Rule::F6, id, message);
                }
                if !may_bubble {
                    let message = "an await bubbles, but isn't marked".into();
                    self.node_problem(Rule::F3, id, message);
                } else if !self.body.may_bubble {
                    let message =
                        format!("an await bubbles, but {} can't", show(p, self.body.owner));
                    self.node_problem(Rule::F4, id, message);
                }
            }
            Node::BlockValue { stmts, value } => {
                let mark = self.body.in_scope.len();
                self.enter_function_lets(p, p.nodes.list(stmts));
                for &stmt in p.nodes.list(stmts) {
                    self.stmt(p, stmt, flow);
                }
                self.expr(p, value, flow);
                self.fits(p, value, ty, "the value");
                self.leave_scopes(mark);
            }
            Node::This {} => {
                let class = self.body.class;
                let owner = self.body.owner;
                let is_static =
                    p.decls.contains(owner) && p.decls.get(owner).kind == DeclKind::Function;
                if class.is_none() || is_static {
                    let message = "this outside a member".into();
                    self.node_problem(Rule::C1, id, message);
                } else if p.decls.get(class).ty != ty {
                    let message = format!("this is {}", p.show_type(p.decls.get(class).ty));
                    self.node_problem(Rule::C1, id, message);
                }
            }
            Node::ListLit { items } => {
                let element = match p.types.get(ty) {
                    TypeData::Nominal { decl, args } if p.name(decl) == "List" => {
                        p.types.list(args).first().copied()
                    }
                    _ => None,
                };
                let Some(element) = element else {
                    let message = format!("a list's literal is {}, not a List", p.show_type(ty));
                    self.node_problem(Rule::T5, id, message);
                    return;
                };
                for &item in p.nodes.list(items) {
                    self.expr(p, item, flow);
                    self.fits(p, item, element, "an item");
                }
            }
            Node::GetField { field, object } => {
                self.expr(p, object, flow);
                if self.reference(p, id, field, &[DeclKind::Field]) {
                    self.visible(p, id, field);
                    let owner = p.decls.get(field).owner;
                    self.receiver_fits(p, object, owner);
                }
            }
            Node::GetStatic { target } => {
                if self.reference(p, id, target, &[DeclKind::StaticField]) {
                    self.visible(p, id, target);
                }
            }
            Node::New {
                ctor,
                type_args,
                may_bubble,
                args,
            } => {
                for &arg in p.nodes.list(args) {
                    self.expr(p, arg, flow);
                }
                if !self.reference(p, id, ctor, &[DeclKind::Constructor]) {
                    return;
                }
                self.visible(p, id, ctor);
                let class = p.decls.get(ctor).owner;
                let type_args = self.type_args(p, id, type_args, class);
                if let Some(sig) = p.signature(ctor) {
                    let what = show(p, ctor);
                    let binds = crate::analysis::bindings(p, ctor, None, &type_args);
                    self.call(p, id, &what, sig, (may_bubble, args), &binds);
                }
            }
            Node::Upcast { value } | Node::Downcast { value } => {
                self.expr(p, value, flow);
                let have = p.types.fallback(p.nodes.ty(value));
                let (sub, sup) = if p.nodes.kind(id) == Kind::Upcast {
                    (have, ty)
                } else {
                    (ty, have)
                };
                if sub == sup || !crate::analysis::is_subtype(p, sub, sup) {
                    let message = format!(
                        "{} isn't a proper subtype of {}",
                        p.show_type(sub),
                        p.show_type(sup)
                    );
                    self.node_problem(Rule::C3, id, message);
                }
            }
            Node::As { may_bubble, value } => {
                self.expr(p, value, flow);
                let have = p.nodes.ty(value);
                if have == ty || !crate::analysis::is_subtype(p, ty, have) {
                    let message = format!(
                        "{} isn't a proper subtype of {}",
                        p.show_type(ty),
                        p.show_type(have)
                    );
                    self.node_problem(Rule::C3, id, message);
                }
                if !may_bubble {
                    let message = "as may bubble, but isn't marked".into();
                    self.node_problem(Rule::F3, id, message);
                } else if !self.body.may_bubble {
                    let message = format!("as may bubble, but {} can't", show(p, self.body.owner));
                    self.node_problem(Rule::F4, id, message);
                }
            }
            Node::Is { ty: of, value } => {
                self.expr(p, value, flow);
                self.nominal(p, id, of, "the type checked");
                self.nominal(p, value, p.nodes.ty(value), "the value checked");
            }
            Node::Identical { left, right } => {
                for side in [left, right] {
                    self.expr(p, side, flow);
                    self.nominal(p, side, p.nodes.ty(side), "an operand of identical");
                }
            }
            Node::FunctionLiteral { decl, params, body } => {
                self.literal(p, id, decl, params, body);
            }
            Node::FunctionRef { target } => {
                if !self.reference(p, id, target, &[DeclKind::Function]) {
                    return;
                }
                self.visible(p, id, target);
                if !p.decls.type_params(target).is_empty() {
                    let message = format!("{} is generic, so can't be a value", show(p, target));
                    self.node_problem(Rule::V1, id, message);
                }
                self.fun_typed(p, id, p.decls.get(target).ty);
            }
            Node::CallValue {
                may_bubble,
                callee,
                args,
            } => {
                self.expr(p, callee, flow);
                for &arg in p.nodes.list(args) {
                    self.expr(p, arg, flow);
                }
                let have = p.nodes.ty(callee);
                let sig = crate::analysis::fun_signature(p, have);
                let Some(sig) = sig.and_then(|s| p.types.signature(s)) else {
                    let message =
                        format!("the callee is {}, not a function value", p.show_type(have));
                    self.node_problem(Rule::V1, callee, message);
                    return;
                };
                let what = "the function value".to_string();
                self.call(
                    p,
                    id,
                    &what,
                    sig,
                    (may_bubble, args),
                    &(Vec::new(), Vec::new()),
                );
            }
            Node::ToFun { value } | Node::FromFun { value } => {
                self.expr(p, value, flow);
                let have = p.nodes.ty(value);
                let (fun, plain) = if p.nodes.kind(id) == Kind::ToFun {
                    (ty, have)
                } else {
                    (have, ty)
                };
                let is_fun = crate::analysis::nominal_decl(p, fun)
                    .is_some_and(|d| p.decls.get(d).facts.contains(Facts::FUN));
                let is_plain = matches!(p.types.get(plain), TypeData::Function { .. });
                let same = crate::analysis::fun_signature(p, fun) == Some(plain);
                if !(is_fun && is_plain && same) {
                    let message = format!(
                        "{} converts {} to {}, which aren't a @fun interface and its function type",
                        p.nodes.kind(id).name(),
                        p.show_type(have),
                        p.show_type(ty)
                    );
                    self.node_problem(Rule::V1, id, message);
                }
            }
            _ => {}
        }
        // A `Never<T>`, as a call of a function declaring it, never
        // completes: nothing after it runs.
        if p.types.is_never(ty) {
            flow.live = false;
        }
    }

    /// V1: a function value's type, a `@fun` interface or function type,
    /// has the signature `sig`.
    fn fun_typed(&mut self, p: &Program, id: NodeId, sig: TypeId) {
        let ty = p.nodes.ty(id);
        if crate::analysis::fun_signature(p, ty) != Some(sig) {
            let message = format!(
                "{} is {}, but its signature is {}",
                p.nodes.kind(id).name(),
                p.show_type(ty),
                p.show_type(sig)
            );
            self.node_problem(Rule::V1, id, message);
        }
    }

    /// A function literal: its type and captures, then its body, checked
    /// as a function's, with the locals it captures in scope.
    fn literal(&mut self, p: &Program, id: NodeId, decl: DeclId, params: Kids, body: NodeId) {
        if !self.define(p, id, decl, DeclKind::Lambda) {
            return;
        }
        self.check_owner(p, id, decl, self.body.owner);
        self.fun_typed(p, id, p.decls.get(decl).ty);
        let stored = p.decls.captures(decl).to_vec();
        let found = crate::analysis::captures(p, id);
        if stored != found {
            let list = |ds: &[DeclId]| {
                ds.iter()
                    .map(|&d| show(p, d))
                    .collect::<Vec<_>>()
                    .join(", ")
            };
            let message = format!(
                "its captures are [{}], not [{}]",
                list(&found),
                list(&stored)
            );
            self.node_problem(Rule::D5, id, message);
        }
        // The outer body's tracking stops at the literal, which may run
        // at any time after it's made.
        let outer = std::mem::take(&mut self.body);
        for &d in &outer.tracked {
            self.slot[d.index()] = u32::MAX;
        }
        let owner = outer.owner;
        let in_static = p.decls.contains(owner) && p.decls.get(owner).kind == DeclKind::Function;
        let class = if in_static { DeclId::NONE } else { outer.class };
        let Some(Signature {
            params: types,
            result,
            bubbles,
            ..
        }) = p.signature(decl)
        else {
            self.restore(outer);
            return;
        };
        let types = types.to_vec();
        // A generator's body returns nothing: its value is the factory.
        let generator = p.decls.get(decl).facts.contains(Facts::GENERATOR);
        let yields = match generator {
            true => p
                .types
                .args(result)
                .first()
                .copied()
                .unwrap_or(TypeId::NONE),
            false => TypeId::NONE,
        };
        let result = if generator { TypeId::VOID } else { result };
        self.start_body(p, decl, result, bubbles, body, &[]);
        self.body.generator = generator;
        self.body.yields = yields;
        self.body.class = class;
        self.body.captures = stored;
        let params = p.nodes.list(params);
        if params.len() != types.len() {
            let message = format!(
                "the signature has {} parameters, the literal {}",
                types.len(),
                params.len()
            );
            self.node_problem(Rule::T1, id, message);
        }
        for (i, &param) in params.iter().enumerate() {
            let Node::Param { decl: d } = p.nodes.get(param) else {
                continue;
            };
            if !self.define(p, param, d, DeclKind::Param) {
                continue;
            }
            self.check_owner(p, param, d, decl);
            if types.get(i).is_some_and(|&t| t != p.decls.get(d).ty) {
                let message = format!("{}'s type differs from the signature's", show(p, d));
                self.node_problem(Rule::T1, param, message);
            }
            self.enter_scope(d);
        }
        let mut flow = Flow::new(self.body.tracked.len());
        self.stmt(p, body, &mut flow);
        if flow.live && result != TypeId::VOID {
            let message = format!("{} can reach its end without returning", show(p, decl));
            self.node_problem(Rule::F1, id, message);
        }
        self.end_body();
        self.restore(outer);
    }

    /// Goes back to checking an outer body after a literal's.
    fn restore(&mut self, outer: Body) {
        for (slot, &d) in outer.tracked.iter().enumerate() {
            self.slot[d.index()] = slot as u32;
        }
        self.body = outer;
    }

    /// The type the local rules give a node, if they cover it.
    fn rule_type(&self, p: &Program, id: NodeId) -> Option<TypeId> {
        let (a, b) = p.nodes.slots(id);
        let decl_ty = |d: DeclId| {
            if p.decls.contains(d) {
                p.decls.get(d).ty
            } else {
                TypeId::NONE
            }
        };
        match p.nodes.kind(id).type_rule() {
            TypeRule::None | TypeRule::Written | TypeRule::Param => None,
            TypeRule::Fixed(ty) => Some(ty),
            TypeRule::Decl => Some(decl_ty(DeclId(a))),
            TypeRule::Field => {
                let object = p.nodes.kids(id).first().map(|&o| p.nodes.ty(o));
                let (from, to) = crate::analysis::bindings(p, DeclId(a), object, &[]);
                let ty = decl_ty(DeclId(a));
                Some(p.types.substituted(ty, &from, &to).unwrap_or(TypeId::NONE))
            }
            TypeRule::Callee => Some(match p.types.signature(decl_ty(DeclId(a))) {
                Some(sig) => {
                    let args = p.types.args_of(crate::types::TypeArgs(b)).to_vec();
                    let receiver = (p.nodes.kind(id) == Kind::CallMethod)
                        .then(|| p.nodes.kids(id).first().map(|&r| p.nodes.ty(r)))
                        .flatten();
                    let (from, to) = crate::analysis::bindings(p, DeclId(a), receiver, &args);
                    (p.types.substituted(sig.result, &from, &to)).unwrap_or(TypeId::NONE)
                }
                None => TypeId::NONE,
            }),
            TypeRule::Op => Some(Op(a).info().map_or(TypeId::NONE, |info| info.result)),
            TypeRule::ValueCall => {
                let callee = p.nodes.kids(id).first().map(|&c| p.nodes.ty(c));
                let sig = callee.and_then(|t| crate::analysis::fun_signature(p, t));
                let sig = sig.and_then(|s| p.types.signature(s));
                Some(sig.map_or(TypeId::NONE, |s| s.result))
            }
        }
    }

    fn string(&mut self, p: &Program, id: NodeId, s: StrId) {
        if !p.strings.contains(s) {
            let message = format!("string {} doesn't exist", s.0);
            self.node_problem(Rule::R1, id, message);
        }
    }

    /// T4: a call's type arguments, as many as `generic` has type
    /// parameters, and interned.
    fn type_args(
        &mut self,
        p: &Program,
        id: NodeId,
        type_args: crate::types::TypeArgs,
        generic: DeclId,
    ) -> Vec<TypeId> {
        if !p.types.has_args(type_args) {
            let message = format!("type arguments {} don't exist", type_args.0);
            self.node_problem(Rule::R1, id, message);
            return Vec::new();
        }
        let args = p.types.args_of(type_args).to_vec();
        let wanted = if p.decls.contains(generic) {
            p.decls.type_params(generic).len()
        } else {
            0
        };
        if args.len() != wanted {
            let message = format!(
                "{} takes {wanted} type arguments, not {}",
                show(p, generic),
                args.len()
            );
            self.node_problem(Rule::T4, id, message);
        }
        for &a in &args {
            if !p.types.contains(a) {
                let message = format!("type {} doesn't exist", a.0);
                self.node_problem(Rule::R1, id, message);
                return args;
            }
        }
        // Each within its type parameter's bounds, the others put in; a
        // builtin type's only as far as tmpl knows its supertypes, so not
        // checked (`String` for `T extends MapKey`).
        let generic = match p.decls.contains(generic).then(|| p.decls.get(generic).kind) {
            Some(DeclKind::Constructor) => p.decls.get(generic).owner,
            Some(_) => generic,
            None => return args,
        };
        let formals = crate::analysis::type_param_types(p, generic);
        if formals.len() != args.len() {
            return args;
        }
        for (&tp, &a) in p.decls.type_params(generic).iter().zip(&args) {
            if matches!(p.types.get(a), TypeData::Scalar(_)) {
                continue;
            }
            for &bound in p.decls.supers(tp) {
                let Some(bound) = p.types.substituted(bound, &formals, &args) else {
                    continue;
                };
                if !crate::analysis::is_subtype(p, a, bound) {
                    let message = format!(
                        "type argument {} isn't within {}'s bound {}",
                        p.show_type(a),
                        show(p, tp),
                        p.show_type(bound)
                    );
                    self.node_problem(Rule::T4, id, message);
                }
            }
        }
        args
    }

    /// T3: a receiver of its method's class or interface; of a generic
    /// one, any subtype of it, with any type arguments.
    fn receiver_fits(&mut self, p: &Program, receiver: NodeId, owner: DeclId) {
        let have = p.nodes.ty(receiver);
        let want = p.decls.get(owner).ty;
        // A generic owner's: any subtype of it, or a type parameter bounded
        // by one.
        let bounded = (matches!(p.types.get(have), TypeData::TypeParam { .. })
            || !p.decls.type_params(owner).is_empty())
            && crate::analysis::as_super(p, have, owner).is_some();
        let fits = have == want
            || bounded
            || p.nodes.kind(receiver) == Kind::Error
            || (crate::analysis::nominal_decl(p, have) == Some(owner)
                && !p.decls.type_params(owner).is_empty());
        if !fits && !have.is_none() {
            let message = format!(
                "the receiver is {}, but should be {}",
                p.show_type(have),
                p.show_type(want)
            );
            self.node_problem(Rule::T3, receiver, message);
        }
    }

    /// A call's arguments and bubbling, against its callee's signature,
    /// with the type parameters it binds put in.
    fn call(
        &mut self,
        p: &Program,
        id: NodeId,
        what: &str,
        sig: Signature,
        (marked, args): (bool, Kids),
        (from, to): &(Vec<TypeId>, Vec<TypeId>),
    ) {
        let (params, bubbles) = (sig.params, sig.bubbles);
        let args = p.nodes.list(args);
        if args.len() != params.len() {
            let message = format!(
                "{what} takes {} arguments, not {}",
                params.len(),
                args.len()
            );
            self.node_problem(Rule::T3, id, message);
        } else {
            for (i, (&arg, &param)) in args.iter().zip(params).enumerate() {
                let optional = sig.optional.get(i).copied().unwrap_or(false);
                if p.nodes.kind(arg) == Kind::Omitted && !optional {
                    let message = format!("argument {} of {what} can't be omitted", i + 1);
                    self.node_problem(Rule::T3, arg, message);
                } else {
                    let param = p.types.substituted(param, from, to).unwrap_or(param);
                    self.fits(p, arg, param, &format!("argument {}", i + 1));
                }
            }
        }
        if marked != bubbles {
            let message = if bubbles {
                format!("the call of {what} may bubble, but isn't marked")
            } else {
                format!("the call of {what} can't bubble, but is marked")
            };
            self.node_problem(Rule::F3, id, message);
        } else if bubbles && !self.body.may_bubble {
            let message = format!(
                "the call of {what} may bubble, but {} can't",
                show(p, self.body.owner)
            );
            self.node_problem(Rule::F4, id, message);
        }
    }

    // Positions.

    fn spans(&mut self, p: &Program, order: &[NodeId]) {
        if !p.has_spans {
            return;
        }
        let fits = |span: Span| {
            span.is_known() && span.file.index() < p.files.len() && span.start <= span.end
        };
        for &id in order {
            if !fits(p.nodes.span(id)) {
                let message = format!("{} has no span in a known file", p.nodes.kind(id).name());
                self.node_problem(Rule::P1, id, message);
            }
        }
        for decl in p.decls.ids() {
            let d = p.decls.get(decl);
            // Another library's declarations point into its files, if
            // anywhere.
            let elsewhere = d.kind == DeclKind::Library || is_external(p, decl);
            if d.kind != DeclKind::Removed && !elsewhere && !(fits(d.span) && fits(d.name_span)) {
                let message = "a declaration needs its spans in a known file".into();
                self.decl_problem(Rule::P1, decl, message);
            }
        }
    }
}

/// The locals a body's `let`s declare, but those in its function
/// literals, whose bodies are checked as their own.
fn body_lets(p: &Program, body: NodeId, out: &mut Vec<DeclId>) {
    let mut stack: Vec<NodeId> = [body].into_iter().filter(|b| !b.is_none()).collect();
    while let Some(id) = stack.pop() {
        match p.nodes.get(id) {
            Node::Let { local, .. } if p.decls.contains(local) => out.push(local),
            Node::FunctionLiteral { .. } => continue,
            _ => {}
        }
        stack.extend(p.nodes.kids(id));
    }
}

/// `name#id`, for messages.
fn show(p: &Program, decl: DeclId) -> String {
    let made = p.decls.get(decl).facts.contains(Facts::COMPILER_MADE);
    let tilde = if made { "~" } else { "" };
    format!("{tilde}{}#{}", p.name(decl), decl.0)
}

/// The declaration a node defines, if it defines one.
pub(crate) fn defined_decl(p: &Program, id: NodeId) -> Option<DeclId> {
    match p.nodes.get(id) {
        Node::Function { decl, .. }
        | Node::BodilessFunction { decl, .. }
        | Node::Test { decl, .. }
        | Node::Param { decl }
        | Node::ModuleLet { decl }
        | Node::Class { decl, .. }
        | Node::Interface { decl, .. }
        | Node::Field { decl }
        | Node::StaticField { decl }
        | Node::Method { decl, .. }
        | Node::AbstractMethod { decl, .. }
        | Node::FunctionLiteral { decl, .. } => Some(decl),
        Node::Let { local, .. } => Some(local),
        Node::While { label, .. } | Node::DoWhile { label, .. } => Some(label),
        _ => None,
    }
}

fn is_true(p: &Program, cond: NodeId) -> bool {
    matches!(p.nodes.get(cond), Node::BoolLit { value: true })
}

/// Whether a declaration is compiler-made, or owned through its owners by
/// one: neither has a qname, as in Kotlin.
fn made_within(p: &Program, decl: DeclId) -> bool {
    let mut d = decl;
    for _ in 0..p.decls.len() {
        if !p.decls.contains(d) {
            return false;
        }
        if p.decls.get(d).facts.contains(Facts::COMPILER_MADE) {
            return true;
        }
        d = p.decls.get(d).owner;
    }
    false
}

/// Whether a declaration of this kind owns parameters, locals, and labels.
fn is_callable(kind: DeclKind) -> bool {
    matches!(
        kind,
        DeclKind::Function
            | DeclKind::Method
            | DeclKind::Getter
            | DeclKind::Setter
            | DeclKind::Constructor
            | DeclKind::Lambda
    )
}
