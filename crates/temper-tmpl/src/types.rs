//! The type table: every type interned, so equal types have equal ids
//! (notes/tmpl/types.md, Level 3).
//!
//! The scalar builtin types are fixed entries, the same in every program;
//! a function declaration's type is its signature; and a class or
//! interface is a non-generic `Nominal` type naming its declaration.

use crate::ids::{DeclId, TypeId};
use std::collections::HashMap;

/// A builtin type with no type parameters.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Scalar {
    Void,
    Boolean,
    Int32,
    Int64,
    Float64,
    String,
    AnyValue,
    /// A type as a value: what `TypeValue` gives (notes/tmpl/types.md,
    /// "Types as values").
    Type,
}

impl Scalar {
    pub const ALL: [Scalar; 8] = [
        Scalar::Void,
        Scalar::Boolean,
        Scalar::Int32,
        Scalar::Int64,
        Scalar::Float64,
        Scalar::String,
        Scalar::AnyValue,
        Scalar::Type,
    ];

    /// Its name, as text writes it.
    pub fn name(self) -> &'static str {
        match self {
            Scalar::Void => "Void",
            Scalar::Boolean => "Boolean",
            Scalar::Int32 => "Int32",
            Scalar::Int64 => "Int64",
            Scalar::Float64 => "Float64",
            Scalar::String => "String",
            Scalar::AnyValue => "AnyValue",
            Scalar::Type => "Type",
        }
    }
}

impl TypeId {
    pub const VOID: TypeId = TypeId(0);
    pub const BOOLEAN: TypeId = TypeId(1);
    pub const INT32: TypeId = TypeId(2);
    pub const INT64: TypeId = TypeId(3);
    pub const FLOAT64: TypeId = TypeId(4);
    pub const STRING: TypeId = TypeId(5);
    pub const ANY_VALUE: TypeId = TypeId(6);
    pub const TYPE: TypeId = TypeId(7);
}

/// A run of types in [`TypeTable::list`].
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct TypeList {
    start: u32,
    len: u32,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum TypeData {
    Scalar(Scalar),
    /// A signature: parameter types, which are optional, and the result,
    /// a [`TypeData::Result`] if it may fail.
    Function {
        params: TypeList,
        result: TypeId,
    },
    /// `T throws E`, a signature's result where it may fail with an `E`
    /// (notes/tmpl/types.md, "Results"): `pass` is what a call gives, the
    /// type of the call's node, when it doesn't. Only ever a
    /// [`TypeData::Function`]'s result, never a value's or a node's type.
    /// Made by [`TypeTable::result`], so `pass` isn't a result and `fail`
    /// isn't a `Never`; `fail` is a failure type or a type parameter ([`crate::failures`]).
    Result {
        pass: TypeId,
        fail: TypeId,
    },
    /// A class or interface, by its declaration, with its type arguments
    /// (none for one without type parameters; a generic one's own type is
    /// it applied to its type parameters). Core's classes for scalars,
    /// such as `core.type Int32`, aren't `Nominal`: their declarations'
    /// type is the scalar.
    Nominal {
        decl: DeclId,
        args: TypeList,
    },
    /// A type parameter of a generic function, method, class, or
    /// interface, by its declaration ([`DeclKind::TypeParam`](crate::DeclKind)),
    /// whose supertypes are its bounds.
    TypeParam {
        decl: DeclId,
    },
    /// `T?`: a value of `T` or null; `T` is never nullable itself.
    Nullable(TypeId),
    /// `Never<T>`: what never completes normally, as a call of a function
    /// declaring it its result, standing for a `T`, which is neither
    /// nullable nor a `Never` itself; only a signature's result, and an
    /// expression's type. A `Never<T>` value fits where a `T`
    /// is wanted, and control doesn't go on past it (notes/tmpl/types.md,
    /// "Never"). Targets without a bottom type lower it to `T`
    /// ([`crate::nevers`]).
    Never(TypeId),
}

/// A signature, as [`TypeTable::signature`] gives it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Signature<'a> {
    pub params: &'a [TypeId],
    /// Which parameters are optional, one per parameter.
    pub optional: &'a [bool],
    /// What a call gives: the type of its node.
    pub pass: TypeId,
    /// What a call may fail with, if anything.
    pub fail: Option<TypeId>,
    /// Its whole result: `pass`, or `pass throws fail`.
    pub returns: TypeId,
}

impl Signature<'_> {
    /// Whether a call may fail.
    pub fn fails(&self) -> bool {
        self.fail.is_some()
    }
}

type FunctionKey = (Vec<TypeId>, Vec<bool>, TypeId);

#[derive(Clone, Debug)]
pub struct TypeTable {
    data: Vec<TypeData>,
    lists: Vec<TypeId>,
    /// Beside `lists`: whether each parameter is optional.
    optional: Vec<bool>,
    functions: HashMap<FunctionKey, TypeId>,
    nominals: HashMap<(DeclId, Vec<TypeId>), TypeId>,
    params: HashMap<DeclId, TypeId>,
    nullables: HashMap<TypeId, TypeId>,
    nevers: HashMap<TypeId, TypeId>,
    results: HashMap<(TypeId, TypeId), TypeId>,
    /// Calls' type arguments, by [`TypeArgs`]; the first is none.
    arg_lists: Vec<TypeList>,
    arg_ids: HashMap<Vec<TypeId>, TypeArgs>,
}

/// A call's type arguments, interned in the [`TypeTable`]; [`TypeArgs::NONE`]
/// for none.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash)]
pub struct TypeArgs(pub u32);

impl TypeArgs {
    pub const NONE: TypeArgs = TypeArgs(0);
}

impl Default for TypeTable {
    fn default() -> Self {
        TypeTable {
            data: Scalar::ALL.iter().map(|&s| TypeData::Scalar(s)).collect(),
            lists: Vec::new(),
            optional: Vec::new(),
            functions: HashMap::new(),
            nominals: HashMap::new(),
            params: HashMap::new(),
            nullables: HashMap::new(),
            nevers: HashMap::new(),
            results: HashMap::new(),
            arg_lists: vec![TypeList { start: 0, len: 0 }],
            arg_ids: HashMap::new(),
        }
    }
}

impl TypeTable {
    pub fn len(&self) -> usize {
        self.data.len()
    }

    pub fn is_empty(&self) -> bool {
        self.data.is_empty()
    }

    pub fn contains(&self, id: TypeId) -> bool {
        id.index() < self.data.len()
    }

    pub fn get(&self, id: TypeId) -> TypeData {
        self.data[id.index()]
    }

    pub fn list(&self, list: TypeList) -> &[TypeId] {
        &self.lists[list.start as usize..(list.start + list.len) as usize]
    }

    /// The scalar type with this name, as text writes it.
    pub fn scalar_named(name: &str) -> Option<TypeId> {
        let i = Scalar::ALL.iter().position(|s| s.name() == name)?;
        Some(TypeId(i as u32))
    }

    /// Interns a signature whose parameters are all required; `result`
    /// is a [`TypeData::Result`] if it may fail.
    pub fn function(&mut self, params: &[TypeId], result: TypeId) -> TypeId {
        self.function_with(params, &vec![false; params.len()], result)
    }

    /// Interns a signature; `optional` says which parameters are, one per
    /// parameter (missing ones are required).
    pub fn function_with(
        &mut self,
        params: &[TypeId],
        optional: &[bool],
        result: TypeId,
    ) -> TypeId {
        let optional: Vec<bool> = (0..params.len())
            .map(|i| optional.get(i).copied().unwrap_or(false))
            .collect();
        let key = (params.to_vec(), optional, result);
        if let Some(&id) = self.functions.get(&key) {
            return id;
        }
        let start = self.lists.len() as u32;
        self.lists.extend_from_slice(params);
        self.optional.extend_from_slice(&key.1);
        let id = TypeId(self.data.len() as u32);
        self.data.push(TypeData::Function {
            params: TypeList {
                start,
                len: params.len() as u32,
            },
            result,
        });
        self.functions.insert(key, id);
        id
    }

    /// Interns `pass throws fail` ([`TypeData::Result`]), or gives `pass`
    /// where `fail` is a `Never`, which can't happen. `pass` mustn't be a
    /// result.
    pub fn result(&mut self, pass: TypeId, fail: TypeId) -> TypeId {
        debug_assert!(self.fail(pass).is_none(), "a result of a result");
        if self.is_never(fail) {
            return pass;
        }
        if let Some(&id) = self.results.get(&(pass, fail)) {
            return id;
        }
        let id = TypeId(self.data.len() as u32);
        self.data.push(TypeData::Result { pass, fail });
        self.results.insert((pass, fail), id);
        id
    }

    /// What a call gives, for a signature whose result is `ty`: a
    /// result's pass type, or `ty` itself.
    pub fn pass(&self, ty: TypeId) -> TypeId {
        match self.data.get(ty.index()) {
            Some(&TypeData::Result { pass, .. }) => pass,
            _ => ty,
        }
    }

    /// What a signature whose result is `ty` may fail with, if anything.
    pub fn fail(&self, ty: TypeId) -> Option<TypeId> {
        match self.data.get(ty.index()) {
            Some(&TypeData::Result { fail, .. }) => Some(fail),
            _ => None,
        }
    }

    /// Interns a class's or interface's type without type arguments.
    pub fn nominal(&mut self, decl: DeclId) -> TypeId {
        self.applied(decl, &[])
    }

    /// Interns a class's or interface's type with type arguments.
    pub fn applied(&mut self, decl: DeclId, args: &[TypeId]) -> TypeId {
        let key = (decl, args.to_vec());
        if let Some(&id) = self.nominals.get(&key) {
            return id;
        }
        let start = self.lists.len() as u32;
        self.lists.extend_from_slice(args);
        self.optional.extend(args.iter().map(|_| false));
        let id = TypeId(self.data.len() as u32);
        let args = TypeList {
            start,
            len: args.len() as u32,
        };
        self.data.push(TypeData::Nominal { decl, args });
        self.nominals.insert(key, id);
        id
    }

    /// Interns a type parameter's type.
    pub fn type_param(&mut self, decl: DeclId) -> TypeId {
        if let Some(&id) = self.params.get(&decl) {
            return id;
        }
        let id = TypeId(self.data.len() as u32);
        self.data.push(TypeData::TypeParam { decl });
        self.params.insert(decl, id);
        id
    }

    /// Interns a call's type arguments.
    pub fn type_args(&mut self, args: &[TypeId]) -> TypeArgs {
        if args.is_empty() {
            return TypeArgs::NONE;
        }
        if let Some(&id) = self.arg_ids.get(args) {
            return id;
        }
        let start = self.lists.len() as u32;
        self.lists.extend_from_slice(args);
        self.optional.extend(args.iter().map(|_| false));
        let id = TypeArgs(self.arg_lists.len() as u32);
        self.arg_lists.push(TypeList {
            start,
            len: args.len() as u32,
        });
        self.arg_ids.insert(args.to_vec(), id);
        id
    }

    /// A call's type arguments; none for an id that doesn't exist.
    pub fn args_of(&self, id: TypeArgs) -> &[TypeId] {
        match self.arg_lists.get(id.0 as usize) {
            Some(&list) => self.list(list),
            None => &[],
        }
    }

    /// Whether `id` names interned type arguments.
    pub fn has_args(&self, id: TypeArgs) -> bool {
        (id.0 as usize) < self.arg_lists.len()
    }

    /// `ty` with each type parameter in `from` (their types) replaced by
    /// the type at the same place in `to`, interning what's new.
    pub fn substitute(&mut self, ty: TypeId, from: &[TypeId], to: &[TypeId]) -> TypeId {
        if from.is_empty() || !self.contains(ty) {
            return ty;
        }
        match self.get(ty) {
            TypeData::TypeParam { .. } => match from.iter().position(|&f| f == ty) {
                Some(i) => to.get(i).copied().unwrap_or(ty),
                None => ty,
            },
            TypeData::Scalar(_) => ty,
            TypeData::Nullable(inner) => {
                let inner = self.substitute(inner, from, to);
                self.nullable(inner)
            }
            TypeData::Never(inner) => {
                let inner = self.substitute(inner, from, to);
                self.never(inner)
            }
            TypeData::Nominal { decl, args } => {
                let args: Vec<TypeId> = self.list(args).to_vec();
                let args: Vec<TypeId> =
                    args.iter().map(|&a| self.substitute(a, from, to)).collect();
                self.applied(decl, &args)
            }
            TypeData::Function { result, .. } => {
                let sig = self.signature(ty).expect("a signature");
                let (params, optional) = (sig.params.to_vec(), sig.optional.to_vec());
                let params: Vec<TypeId> = params
                    .iter()
                    .map(|&p| self.substitute(p, from, to))
                    .collect();
                let result = self.substitute(result, from, to);
                self.function_with(&params, &optional, result)
            }
            TypeData::Result { pass, fail } => {
                let pass = self.substitute(pass, from, to);
                let fail = self.substitute(fail, from, to);
                self.result(pass, fail)
            }
        }
    }

    /// [`TypeTable::substitute`] without interning: `None` if the result
    /// isn't already a type here.
    pub fn substituted(&self, ty: TypeId, from: &[TypeId], to: &[TypeId]) -> Option<TypeId> {
        if from.is_empty() || !self.contains(ty) {
            return Some(ty);
        }
        match self.get(ty) {
            TypeData::TypeParam { .. } => Some(match from.iter().position(|&f| f == ty) {
                Some(i) => to.get(i).copied().unwrap_or(ty),
                None => ty,
            }),
            TypeData::Scalar(_) => Some(ty),
            TypeData::Nullable(inner) => {
                let inner = self.substituted(inner, from, to)?;
                self.nullable_of(inner)
            }
            TypeData::Never(inner) => {
                let inner = self.substituted(inner, from, to)?;
                match self.get(inner) {
                    TypeData::Never(_) => Some(inner),
                    _ => self.nevers.get(&inner).copied(),
                }
            }
            TypeData::Nominal { decl, args } => {
                let args: Option<Vec<TypeId>> = (self.list(args).iter())
                    .map(|&a| self.substituted(a, from, to))
                    .collect();
                self.nominals.get(&(decl, args?)).copied()
            }
            TypeData::Function { result, .. } => {
                let sig = self.signature(ty)?;
                let params: Option<Vec<TypeId>> = (sig.params.iter())
                    .map(|&p| self.substituted(p, from, to))
                    .collect();
                let key = (
                    params?,
                    sig.optional.to_vec(),
                    self.substituted(result, from, to)?,
                );
                self.functions.get(&key).copied()
            }
            TypeData::Result { pass, fail } => {
                let pass = self.substituted(pass, from, to)?;
                let fail = self.substituted(fail, from, to)?;
                match self.is_never(fail) {
                    true => Some(pass),
                    false => self.results.get(&(pass, fail)).copied(),
                }
            }
        }
    }

    /// Whether `ty` mentions a type parameter.
    pub fn is_generic(&self, ty: TypeId) -> bool {
        if !self.contains(ty) {
            return false;
        }
        match self.get(ty) {
            TypeData::TypeParam { .. } => true,
            TypeData::Scalar(_) => false,
            TypeData::Nullable(inner) | TypeData::Never(inner) => self.is_generic(inner),
            TypeData::Nominal { args, .. } => self.list(args).iter().any(|&a| self.is_generic(a)),
            TypeData::Function { .. } => self.signature(ty).is_some_and(|sig| {
                sig.params.iter().any(|&p| self.is_generic(p)) || self.is_generic(sig.returns)
            }),
            TypeData::Result { pass, fail } => self.is_generic(pass) || self.is_generic(fail),
        }
    }

    /// Whether `ty` mentions the type parameter whose type is `param`.
    pub fn mentions(&self, ty: TypeId, param: TypeId) -> bool {
        if ty == param {
            return true;
        }
        if !self.contains(ty) {
            return false;
        }
        match self.get(ty) {
            TypeData::TypeParam { .. } | TypeData::Scalar(_) => false,
            TypeData::Nullable(inner) | TypeData::Never(inner) => self.mentions(inner, param),
            TypeData::Nominal { args, .. } => {
                self.list(args).iter().any(|&a| self.mentions(a, param))
            }
            TypeData::Function { .. } => self.signature(ty).is_some_and(|sig| {
                sig.params.iter().any(|&p| self.mentions(p, param))
                    || self.mentions(sig.returns, param)
            }),
            TypeData::Result { pass, fail } => {
                self.mentions(pass, param) || self.mentions(fail, param)
            }
        }
    }

    /// A nominal type's type arguments; none for other types.
    pub fn args(&self, ty: TypeId) -> &[TypeId] {
        match self.data.get(ty.index()) {
            Some(&TypeData::Nominal { args, .. }) => self.list(args),
            _ => &[],
        }
    }

    /// Interns `T?`; `T?` itself if `ty` is already nullable, and for
    /// `Never<T>`, `T?`, as only null comes from a `Never<T>?`.
    pub fn nullable(&mut self, ty: TypeId) -> TypeId {
        if self.is_nullable(ty) || ty.is_none() {
            return ty;
        }
        let ty = self.fallback(ty);
        if let Some(&id) = self.nullables.get(&ty) {
            return id;
        }
        let id = TypeId(self.data.len() as u32);
        self.data.push(TypeData::Nullable(ty));
        self.nullables.insert(ty, id);
        id
    }

    /// Interns `Never<T>`; `ty` itself if it's a `Never` or nullable, as
    /// a `Never<T>?` is a `T?` that's only ever null.
    pub fn never(&mut self, ty: TypeId) -> TypeId {
        if matches!(
            self.data.get(ty.index()),
            None | Some(TypeData::Never(_) | TypeData::Nullable(_))
        ) {
            return ty;
        }
        if let Some(&id) = self.nevers.get(&ty) {
            return id;
        }
        let id = TypeId(self.data.len() as u32);
        self.data.push(TypeData::Never(ty));
        self.nevers.insert(ty, id);
        id
    }

    /// `T` for `Never<T>`, the type it stands for, else the type itself.
    pub fn fallback(&self, ty: TypeId) -> TypeId {
        match self.data.get(ty.index()) {
            Some(&TypeData::Never(t)) => t,
            _ => ty,
        }
    }

    /// Whether `ty` is `Never<T>` for some `T`: what never completes.
    pub fn is_never(&self, ty: TypeId) -> bool {
        matches!(self.data.get(ty.index()), Some(TypeData::Never(_)))
    }

    /// `T?` for `T`, if it's interned, without interning it.
    pub fn nullable_of(&self, ty: TypeId) -> Option<TypeId> {
        if self.is_nullable(ty) {
            return Some(ty);
        }
        self.nullables.get(&ty).copied()
    }

    /// Whether `ty` is `T?` for some `T`.
    pub fn is_nullable(&self, ty: TypeId) -> bool {
        matches!(self.data.get(ty.index()), Some(TypeData::Nullable(_)))
    }

    /// `T` for `T?`, else the type itself.
    pub fn non_null(&self, ty: TypeId) -> TypeId {
        match self.data.get(ty.index()) {
            Some(&TypeData::Nullable(t)) => t,
            _ => ty,
        }
    }

    /// A signature's parameters, which are optional, and its result, as
    /// what a call gives and what it may fail with.
    pub fn signature(&self, id: TypeId) -> Option<Signature<'_>> {
        match self.data.get(id.index())? {
            &TypeData::Function { params, result } => Some(Signature {
                params: self.list(params),
                optional: &self.optional
                    [params.start as usize..(params.start + params.len) as usize],
                pass: self.pass(result),
                fail: self.fail(result),
                returns: result,
            }),
            TypeData::Scalar(_)
            | TypeData::Nominal { .. }
            | TypeData::TypeParam { .. }
            | TypeData::Nullable(_)
            | TypeData::Never(_)
            | TypeData::Result { .. } => None,
        }
    }

    /// Whether `id` here and `other` in another table are the same type.
    pub fn same(&self, id: TypeId, others: &TypeTable, other: TypeId) -> bool {
        if !self.contains(id) || !others.contains(other) {
            return id == other;
        }
        match (self.get(id), others.get(other)) {
            (TypeData::Scalar(a), TypeData::Scalar(b)) => a == b,
            (TypeData::Nominal { decl: a, args: x }, TypeData::Nominal { decl: b, args: y }) => {
                let (x, y) = (self.list(x), others.list(y));
                a == b
                    && x.len() == y.len()
                    && x.iter().zip(y).all(|(&a, &b)| self.same(a, others, b))
            }
            (TypeData::TypeParam { decl: a }, TypeData::TypeParam { decl: b }) => a == b,
            (TypeData::Nullable(a), TypeData::Nullable(b))
            | (TypeData::Never(a), TypeData::Never(b)) => self.same(a, others, b),
            (TypeData::Function { .. }, TypeData::Function { .. }) => {
                let (Some(x), Some(y)) = (self.signature(id), others.signature(other)) else {
                    return false;
                };
                x.optional == y.optional
                    && x.params.len() == y.params.len()
                    && x.params
                        .iter()
                        .zip(y.params)
                        .all(|(&a, &b)| self.same(a, others, b))
                    && self.same(x.returns, others, y.returns)
            }
            (TypeData::Result { pass: a, fail: e }, TypeData::Result { pass: b, fail: f }) => {
                self.same(a, others, b) && self.same(e, others, f)
            }
            _ => false,
        }
    }

    /// A type as text writes it, with `decl` writing a nominal type's
    /// declaration.
    pub fn show_with(&self, id: TypeId, decl: &dyn Fn(DeclId) -> String) -> String {
        if id.is_none() {
            return "(no type)".into();
        }
        match self.data.get(id.index()) {
            None => format!("(missing type {})", id.0),
            Some(TypeData::Scalar(s)) => s.name().into(),
            Some(&TypeData::Nominal { decl: d, args }) => {
                let args = self.list(args);
                if args.is_empty() {
                    decl(d)
                } else {
                    let args: Vec<String> = args.iter().map(|&a| self.show_with(a, decl)).collect();
                    format!("{}<{}>", decl(d), args.join(", "))
                }
            }
            Some(&TypeData::TypeParam { decl: d }) => decl(d),
            Some(&TypeData::Never(t)) => format!("Never<{}>", self.show_with(t, decl)),
            Some(&TypeData::Nullable(t)) => {
                let inner = self.show_with(t, decl);
                if matches!(self.data.get(t.index()), Some(TypeData::Function { .. })) {
                    format!("({inner})?")
                } else {
                    format!("{inner}?")
                }
            }
            Some(TypeData::Function { .. }) => {
                let Some(sig) = self.signature(id) else {
                    return String::new();
                };
                let params: Vec<String> = sig
                    .params
                    .iter()
                    .zip(sig.optional)
                    .map(|(&t, &optional)| {
                        let ty = self.show_with(t, decl);
                        if optional { format!("_?: {ty}") } else { ty }
                    })
                    .collect();
                let result = self.show_with(sig.returns, decl);
                format!("fn ({}): {result}", params.join(", "))
            }
            Some(&TypeData::Result { pass, fail }) => {
                let (pass, fail) = (self.show_with(pass, decl), self.show_with(fail, decl));
                format!("{pass} throws {fail}")
            }
        }
    }

    /// A type as text writes it, for messages, with a nominal type's
    /// declaration as `#id`.
    pub fn show(&self, id: TypeId) -> String {
        self.show_with(id, &|d| format!("#{}", d.0))
    }
}
