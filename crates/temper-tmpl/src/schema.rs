//! The schema: one table declaring every node kind, its family, where its
//! type comes from, and its fields, from which `schema!` generates
//! [`Kind`], its metadata, and [`Node`] with [`Nodes::get`].
//!
//! Each field says where it's stored:
//!
//! - `a` or `b`: a payload slot, holding an id or a literal's bits;
//!   `ab`: both, for 64-bit literals;
//! - `flag`: the node's [`Flags::MAY_FAIL`] bit;
//! - `one`, `opt`, or `many`, then a [`Place`]: children, in order. A kind
//!   has at most one `opt` or `many` field, which takes the children that
//!   the `one` fields leave.
//!
//! Subset A (notes/tmpl/nodes.md, Level 3), with what core's declarations
//! need: `CallMethod`, `Omitted`, and bodiless (connected) functions; and
//! from subset B, classes and interfaces with their members, `this`,
//! fields, statics, `new`, conversions, `is`, and identity; and function
//! values: literals with their captures, references, calls of values, and
//! the conversions between `@fun` interfaces and function types;
//! `Coalesce`, for parameters' defaults; and declared errors' `Throw`
//! and `Catch`.

use crate::ids::{DeclId, NodeId, StrId, TypeId};
use crate::nodes::{Flags, Kids, Nodes};
use crate::ops::Op;
use crate::profile::Features;
use crate::types::TypeArgs;

/// What a kind of node is, which says where it may go.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Family {
    /// Defines a declaration: functions, parameters, and module `let`s.
    Decl,
    /// No value and no type.
    Stmt,
    /// A typed value.
    Expr,
    /// Part of another node, only where it goes: a `Switch`'s `Case`.
    Part,
}

/// Where a kind's type comes from: the local rules that text relies on
/// to leave types out (notes/tmpl/formats.md, Level 1).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TypeRule {
    /// Untyped: statements and declarations.
    None,
    /// Always this type: literals, `&&`, and `||`.
    Fixed(TypeId),
    /// Its declaration's type (the declaration in `a`).
    Decl,
    /// Its field's type (the field in `a`), with its class's type
    /// parameters as its object's type (its first child) gives them.
    Field,
    /// Its callee's signature's result (the function or method in `a`),
    /// with its type parameters as its type arguments (in `b`) give them,
    /// and its class's as its receiver's type does (for a method) or as
    /// its type arguments do (for a constructor).
    Callee,
    /// Its operator's result.
    Op,
    /// Written in text, as `(e): T`.
    Written,
    /// Its parameter's: an `Omitted` argument's, from its call.
    Param,
    /// Its callee's (its first child's) result, as a function value's
    /// signature gives it: a function type's, or a `@fun` interface's
    /// `apply`'s with the interface's type arguments put in.
    ValueCall,
}

/// Where a field is stored.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Slot {
    A(Payload),
    B(Payload),
    AB(Payload),
    Flag,
    Kid(Arity, Place),
}

/// What a payload slot holds.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Payload {
    Decl,
    Str,
    Op,
    Bool,
    Type,
    Int32,
    Int64,
    Float64,
    /// A call's type arguments.
    Args,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Arity {
    One,
    Opt,
    Many,
}

/// What a child's place takes.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Place {
    /// A `Param` node.
    Param,
    /// A `Block` node.
    Block,
    /// Any statement.
    Stmt,
    /// Any expression.
    Expr,
    /// A class's or interface's member: a `Field`, `StaticField`,
    /// `Method`, `AbstractMethod`, or (static) `Function`.
    Member,
    /// A `Switch`'s `Case`.
    Case,
}

/// A field of a kind, as the schema declares it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Field {
    pub name: &'static str,
    pub slot: Slot,
}

/// A field's Rust type, and how it's decoded from the columns.
pub trait FieldValue: Sized {
    /// What it is as a payload; `None` for children.
    const PAYLOAD: Option<Payload> = None;
    fn from_slots(_a: u32, _b: u32) -> Self {
        unreachable!("not a payload")
    }
    fn from_kids(_nodes: &Nodes, _kids: Kids) -> Self {
        unreachable!("not a child")
    }
}

macro_rules! payload {
    ($($t:ty = $p:ident, |$a:ident, $b:ident| $e:expr;)*) => {$(
        impl FieldValue for $t {
            const PAYLOAD: Option<Payload> = Some(Payload::$p);
            fn from_slots($a: u32, $b: u32) -> Self {
                $e
            }
        }
    )*};
}

payload! {
    DeclId = Decl, |a, _b| DeclId(a);
    StrId = Str, |a, _b| StrId(a);
    Op = Op, |a, _b| Op(a);
    bool = Bool, |a, _b| a != 0;
    TypeId = Type, |a, _b| TypeId(a);
    TypeArgs = Args, |a, _b| TypeArgs(a);
    i32 = Int32, |a, _b| a as i32;
    i64 = Int64, |a, b| (a as u64 | ((b as u64) << 32)) as i64;
    f64 = Float64, |a, b| f64::from_bits(a as u64 | ((b as u64) << 32));
}

impl FieldValue for NodeId {
    fn from_kids(nodes: &Nodes, kids: Kids) -> Self {
        nodes.list(kids)[0]
    }
}

impl FieldValue for Option<NodeId> {
    fn from_kids(nodes: &Nodes, kids: Kids) -> Self {
        nodes.list(kids).first().copied()
    }
}

impl FieldValue for Kids {
    fn from_kids(_nodes: &Nodes, kids: Kids) -> Self {
        kids
    }
}

/// A 64-bit literal's payload slots.
pub(crate) fn split_bits(bits: u64) -> (u32, u32) {
    (bits as u32, (bits >> 32) as u32)
}

/// Where a field of type `$t` is stored, from its slot in the table.
macro_rules! slot {
    ($t:ty, a) => {
        Slot::A(payload::<$t>())
    };
    ($t:ty, b) => {
        Slot::B(payload::<$t>())
    };
    ($t:ty, ab) => {
        Slot::AB(payload::<$t>())
    };
    ($t:ty, flag) => {
        Slot::Flag
    };
    ($t:ty, one $place:ident) => {
        Slot::Kid(Arity::One, Place::$place)
    };
    ($t:ty, opt $place:ident) => {
        Slot::Kid(Arity::Opt, Place::$place)
    };
    ($t:ty, many $place:ident) => {
        Slot::Kid(Arity::Many, Place::$place)
    };
}

const fn payload<T: FieldValue>() -> Payload {
    match T::PAYLOAD {
        Some(p) => p,
        None => panic!("not a payload type"),
    }
}

macro_rules! decode {
    ($nodes:ident, $id:ident, $parts:ident, a) => {
        FieldValue::from_slots($nodes.slots($id).0, 0)
    };
    ($nodes:ident, $id:ident, $parts:ident, b) => {
        FieldValue::from_slots($nodes.slots($id).1, 0)
    };
    ($nodes:ident, $id:ident, $parts:ident, ab) => {
        FieldValue::from_slots($nodes.slots($id).0, $nodes.slots($id).1)
    };
    ($nodes:ident, $id:ident, $parts:ident, flag) => {
        $nodes.flags($id).contains(Flags::MAY_FAIL)
    };
    ($nodes:ident, $id:ident, $parts:ident, $arity:ident) => {
        FieldValue::from_kids($nodes, $parts.next().expect("malformed node"))
    };
}

macro_rules! schema {
    ($(
        $(#[doc = $doc:literal])*
        $kind:ident($family:ident, $rule:expr $(, $feature:ident)?) {
            $($field:ident: $t:ty = $slot:ident $($place:ident)?),* $(,)?
        }
    )*) => {
        /// A node's kind.
        #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
        #[repr(u8)]
        pub enum Kind {
            $($(#[doc = $doc])* $kind,)*
        }

        impl Kind {
            pub const ALL: &[Kind] = &[$(Kind::$kind),*];

            pub fn name(self) -> &'static str {
                match self {
                    $(Kind::$kind => stringify!($kind),)*
                }
            }

            pub fn family(self) -> Family {
                match self {
                    $(Kind::$kind => Family::$family,)*
                }
            }

            pub fn type_rule(self) -> TypeRule {
                match self {
                    $(Kind::$kind => $rule,)*
                }
            }

            /// The profile features a program needs to hold this kind.
            pub fn needs(self) -> Features {
                match self {
                    $(Kind::$kind => Features::NONE $(.with(Features::$feature))?,)*
                }
            }

            pub fn fields(self) -> &'static [Field] {
                match self {
                    $(Kind::$kind => {
                        const FIELDS: &[Field] = &[$(Field {
                            name: stringify!($field),
                            slot: slot!($t, $slot $($place)?),
                        }),*];
                        FIELDS
                    })*
                }
            }
        }

        /// A node, decoded by [`Nodes::get`].
        #[derive(Clone, Copy, Debug, PartialEq)]
        pub enum Node {
            $($(#[doc = $doc])* $kind { $($field: $t),* },)*
        }

        impl Node {
            pub fn kind(&self) -> Kind {
                match self {
                    $(Node::$kind { .. } => Kind::$kind,)*
                }
            }
        }

        impl Nodes {
            /// Decodes a node's fields. Panics if its children don't fit
            /// its kind, which the validator's S1 rules out.
            pub fn get(&self, id: NodeId) -> Node {
                let nodes = self;
                #[allow(unused_mut, unused_variables)]
                let mut parts = nodes.parts(id).expect("malformed node");
                match nodes.kind(id) {
                    $(Kind::$kind => Node::$kind {
                        $($field: decode!(nodes, id, parts, $slot)),*
                    },)*
                }
            }
        }
    };
}

use TypeRule::{Callee, Decl as DeclType, Fixed, None as Untyped, Written};

schema! {
    // Declarations, each defining the declaration in `a`.

    /// A function: its parameters, then its body.
    Function(Decl, Untyped) {
        decl: DeclId = a,
        params: Kids = many Param,
        body: NodeId = one Block,
    }
    /// A function without a body: connected, needing host code on every
    /// backend that calls it (rule Q3).
    BodilessFunction(Decl, Untyped) {
        decl: DeclId = a,
        params: Kids = many Param,
    }
    /// A test: a function, named by `decl` (compiler-made), that takes a
    /// fresh `Test` of std's `testing` module and may throw any error
    /// (core's `Error`), run after the module's init, and
    /// passing if it throws none and its `Test` has no errors when it
    /// ends. `name` is how it's reported.
    Test(Decl, Untyped) {
        decl: DeclId = a,
        name: StrId = b,
        params: Kids = many Param,
        body: NodeId = one Block,
    }
    /// A function's parameter.
    Param(Decl, Untyped) { decl: DeclId = a }
    /// A module `let` or `var`; its initializer is an `InitLet`.
    ModuleLet(Decl, Untyped) { decl: DeclId = a }
    /// A class: final, with its members; its supertypes, interfaces, are
    /// on its declaration ([`DeclTable::supers`](crate::DeclTable::supers)).
    Class(Decl, Untyped) { decl: DeclId = a, members: Kids = many Member }
    /// An interface, with its members, abstract or not.
    Interface(Decl, Untyped) { decl: DeclId = a, members: Kids = many Member }
    /// An instance field of a class; `var` if its declaration is.
    Field(Decl, Untyped) { decl: DeclId = a }
    /// A static field of a class or interface; its initializer is an
    /// `InitStatic` in its module's init block.
    StaticField(Decl, Untyped) { decl: DeclId = a }
    /// A method, getter, setter, or constructor, by its declaration's
    /// kind: its parameters (not `this`), then its body.
    Method(Decl, Untyped) {
        decl: DeclId = a,
        params: Kids = many Param,
        body: NodeId = one Block,
    }
    /// An interface's method, getter, or setter without a body, which
    /// each class below it defines.
    AbstractMethod(Decl, Untyped) { decl: DeclId = a, params: Kids = many Param }

    // Statements.

    /// Statements in order, in a scope of their own.
    Block(Stmt, Untyped) { stmts: Kids = many Stmt }
    /// Declares a local, maybe without an initializer.
    Let(Stmt, Untyped) { local: DeclId = a, init: Option<NodeId> = opt Expr }
    /// Assigns a local, or a `var` parameter.
    Assign(Stmt, Untyped) { local: DeclId = a, value: NodeId = one Expr }
    /// Assigns a module `var`.
    SetModuleLet(Stmt, Untyped) { target: DeclId = a, value: NodeId = one Expr }
    /// Initializes a module `let`; only in an init block.
    InitLet(Stmt, Untyped) { target: DeclId = a, value: NodeId = one Expr }
    If(Stmt, Untyped) {
        cond: NodeId = one Expr,
        then: NodeId = one Block,
        els: Option<NodeId> = opt Block,
    }
    /// A loop, declaring its label.
    While(Stmt, Untyped) {
        label: DeclId = a,
        cond: NodeId = one Expr,
        body: NodeId = one Block,
    }
    /// A loop whose body runs before its condition, declaring its label.
    DoWhile(Stmt, Untyped) {
        label: DeclId = a,
        body: NodeId = one Block,
        cond: NodeId = one Expr,
    }
    Break(Stmt, Untyped) { label: DeclId = a }
    Continue(Stmt, Untyped) { label: DeclId = a }
    /// Has a value exactly when the function's result isn't `Void`.
    Return(Stmt, Untyped) { value: Option<NodeId> = opt Expr }
    ExprStatement(Stmt, Untyped) { expr: NodeId = one Expr }
    /// Assigns an instance field: of `this`, in its class, while
    /// constructing, or if the field is `var`.
    SetField(Stmt, Untyped) {
        field: DeclId = a,
        object: NodeId = one Expr,
        value: NodeId = one Expr,
    }
    /// Initializes a static field; only in its module's init block.
    InitStatic(Stmt, Untyped) { target: DeclId = a, value: NodeId = one Expr }
    /// Runs the one case whose labels hold the scrutinee's value, an
    /// `Int32` or `Int64`, or else the default (a `Case` without labels,
    /// last), if any; no fallthrough. Each label is a literal of the
    /// scrutinee's type, and all are different. For targets without it,
    /// [`crate::switches::to_if_chains`] makes it `if`s
    /// (notes/tmpl/nodes.md, "Switch").
    Switch(Stmt, Untyped, SWITCH) {
        scrutinee: NodeId = one Expr,
        cases: Kids = many Case,
    }
    /// One of a `Switch`'s cases: its labels, then its body; without
    /// labels, the default.
    Case(Part, Untyped) {
        labels: Kids = many Expr,
        body: NodeId = one Block,
    }
    /// Pauses a generator, giving control back to whatever resumed it,
    /// with its value, a `Y` for a `SafeGenerator<Y>`, or with none,
    /// `Empty`; only in a generator literal's own body
    /// (notes/tmpl/nodes.md, "Coroutines").
    Yield(Stmt, Untyped) { value: Option<NodeId> = opt Expr }

    // Expressions.

    BoolLit(Expr, Fixed(TypeId::BOOLEAN)) { value: bool = a }
    Int32Lit(Expr, Fixed(TypeId::INT32)) { value: i32 = a }
    Int64Lit(Expr, Fixed(TypeId::INT64)) { value: i64 = ab }
    Float64Lit(Expr, Fixed(TypeId::FLOAT64)) { value: f64 = ab }
    StringLit(Expr, Fixed(TypeId::STRING)) { value: StrId = a }
    VoidLit(Expr, Fixed(TypeId::VOID)) {}
    /// A type as a value, as `Void` where a `Type` is wanted: each
    /// backend's own value for it (notes/tmpl/types.md, "Types as
    /// values").
    TypeValue(Expr, Fixed(TypeId::TYPE)) { of: TypeId = a }
    /// Reads a local or parameter.
    Local(Expr, DeclType) { local: DeclId = a }
    /// Reads a module `let`.
    GetModuleLet(Expr, DeclType) { target: DeclId = a }
    /// Calls a function, with its type arguments if it's generic.
    CallFunction(Expr, Callee) {
        callee: DeclId = a,
        type_args: TypeArgs = b,
        may_fail: bool = flag,
        args: Kids = many Expr,
    }
    /// An operator or member of a builtin type.
    Op(Expr, TypeRule::Op) {
        op: Op = a,
        args: Kids = many Expr,
    }
    /// Calls a method or getter on a receiver; virtual exactly when the
    /// method's owner is an interface. The receiver's type gives its
    /// class's type parameters; `type_args` the method's own, if generic.
    CallMethod(Expr, Callee) {
        method: DeclId = a,
        type_args: TypeArgs = b,
        may_fail: bool = flag,
        receiver: NodeId = one Expr,
        args: Kids = many Expr,
    }
    /// The object a method, getter, setter, or constructor runs on, typed
    /// as its class or interface.
    This(Expr, Written) {}
    /// Reads an instance field, typed with its class's type parameters as
    /// the object's type gives them.
    GetField(Expr, TypeRule::Field) { field: DeclId = a, object: NodeId = one Expr }
    /// Reads a static field.
    GetStatic(Expr, DeclType) { target: DeclId = a }
    /// Makes an instance by calling its class's constructor, with the
    /// class's type arguments if it's generic.
    New(Expr, Callee) {
        ctor: DeclId = a,
        type_args: TypeArgs = b,
        may_fail: bool = flag,
        args: Kids = many Expr,
    }
    /// A value as one of its supertypes: a class's instance as an
    /// interface it implements.
    Upcast(Expr, Written) { value: NodeId = one Expr }
    /// A value as a subtype that flow facts (an `is` check) prove it has,
    /// unchecked.
    Downcast(Expr, Written) { value: NodeId = one Expr }
    /// A value as a subtype, checked when run: it panics if the value
    /// isn't one, as Temper's `as` does.
    As(Expr, Written) { value: NodeId = one Expr }
    /// Whether a value is an instance of a class or interface.
    Is(Expr, Fixed(TypeId::BOOLEAN)) { ty: TypeId = a, value: NodeId = one Expr }
    /// Whether two values of class or interface types are the same object,
    /// where the source compares them with `==` and no `@operator("==")`
    /// method applies (notes/tmpl/nodes.md, "Equality").
    Identical(Expr, Fixed(TypeId::BOOLEAN)) { left: NodeId = one Expr, right: NodeId = one Expr }
    /// The argument for an optional parameter left out: the callee applies
    /// its default.
    Omitted(Expr, TypeRule::Param) {}
    /// `null`, typed as the nullable type its place wants.
    Null(Expr, Written) {}
    /// Whether a value of a nullable type is null.
    IsNull(Expr, Fixed(TypeId::BOOLEAN)) { value: NodeId = one Expr }
    /// A value of a nullable type `T?` as `T`, where flow facts (a null
    /// check) prove it isn't null; unchecked.
    NonNull(Expr, Written) { value: NodeId = one Expr }
    /// A value of type `T` as `T?`.
    AsNullable(Expr, Written) { value: NodeId = one Expr }
    /// `==` where either side may be null, both typed `T?`: true if both
    /// are null, false if one is, else `op`, the equality of `T`'s values
    /// (notes/tmpl/nodes.md, "Equality").
    EqNullable(Expr, Fixed(TypeId::BOOLEAN)) {
        op: Op = a,
        left: NodeId = one Expr,
        right: NodeId = one Expr,
    }
    /// `left`'s value if it isn't null, else `right`'s, which runs only
    /// then: Temper's `??`, as a parameter's default uses it. Typed as
    /// `right` is: `left`'s type without null, or with it. `to_tmpl`
    /// writes it only for defaults so far (notes/tmpl/nodes.md,
    /// "Parameters' defaults").
    Coalesce(Expr, Written) { left: NodeId = one Expr, right: NodeId = one Expr }
    /// Temper's `orelse`: `body`'s value, or `fallback`'s if `body`
    /// fails. Calls in `body` may fail wherever the `OrElse` is. One
    /// typed `Void` drops its parts' values, whatever their types, as
    /// where it stands alone.
    OrElse(Expr, Written) { body: NodeId = one Expr, fallback: NodeId = one Expr }
    /// `throw value`: fails with `value`, an object of an error type (a
    /// class below core's `Error`), to the nearest handler or the
    /// function's caller, which must take its type; typed as its place
    /// wants, since it never completes. `cause`, in a handler's fallback,
    /// is the error being handled, which becomes the thrown one's cause
    /// unless it has one already (notes/tmpl/nodes.md, "Declared
    /// errors").
    Throw(Expr, Written) {
        may_fail: bool = flag,
        value: NodeId = one Expr,
        cause: Option<NodeId> = opt Expr,
    }
    /// `catch (e)`: `body`'s value, or if it fails, `fallback`'s, with
    /// `local`, declared here and in scope only in `fallback`, holding the
    /// error. Everything `body` may fail with is of `local`'s type, an
    /// error type. Typed as `OrElse` is.
    Catch(Expr, Written) {
        local: DeclId = a,
        body: NodeId = one Expr,
        fallback: NodeId = one Expr,
    }
    /// `panic()`: stops the program, which no `orelse` handles; typed as
    /// its place wants.
    Panic(Expr, Written) {}
    And(Expr, Fixed(TypeId::BOOLEAN)) { left: NodeId = one Expr, right: NodeId = one Expr }
    Or(Expr, Fixed(TypeId::BOOLEAN)) { left: NodeId = one Expr, right: NodeId = one Expr }
    IfValue(Expr, Written) {
        cond: NodeId = one Expr,
        then: NodeId = one Expr,
        els: NodeId = one Expr,
    }
    /// Statements, then a value: statements inside an expression.
    BlockValue(Expr, Written, STATEMENT_EXPRESSIONS) {
        stmts: Kids = many Stmt,
        value: NodeId = one Expr,
    }
    /// A function literal, a closure: its declaration ([`DeclKind::Lambda`](crate::DeclKind),
    /// whose type is its signature and which lists its captures), its
    /// parameters, and its body. Typed as a `@fun` interface or a function
    /// type whose signature is its own.
    FunctionLiteral(Expr, Written) {
        decl: DeclId = a,
        params: Kids = many Param,
        body: NodeId = one Block,
    }
    /// A top-level or static function as a value, typed as a `@fun`
    /// interface or a function type whose signature is its own.
    FunctionRef(Expr, Written) { target: DeclId = a }
    /// Calls a function value: a function type's, or a `@fun`
    /// interface's, as its `apply`.
    CallValue(Expr, TypeRule::ValueCall) {
        may_fail: bool = flag,
        callee: NodeId = one Expr,
        args: Kids = many Expr,
    }
    /// A function type's value as a `@fun` interface's, whose signature
    /// is the same.
    ToFun(Expr, Written) { value: NodeId = one Expr }
    /// A `@fun` interface's value as a function type's, whose signature
    /// is the same.
    FromFun(Expr, Written) { value: NodeId = one Expr }
    /// A list's literal: a `List` of core's, its element type given by its
    /// type, `List<T>`, which each item has.
    ListLit(Expr, Written) { items: Kids = many Expr }
    /// Waits for a promise, pausing the generator whose own body it's in
    /// until the promise resolves: its value, or it throws core's
    /// `BrokenPromise` if the promise is broken. Typed as the promise's
    /// value.
    Await(Expr, Written) { may_fail: bool = flag, promise: NodeId = one Expr }
    /// Code the frontend couldn't settle; panics when run. Valid anywhere,
    /// with any type. Code that's only ill-typed, as a value that doesn't
    /// fit its place, keeps its value as written, of its own type, which
    /// a target whose profile has [`Features::RUNS_TYPE_ERRORS`] runs
    /// instead ([`crate::errors`]).
    Error(Expr, Written) { message: StrId = a, value: Option<NodeId> = opt Expr }
}
