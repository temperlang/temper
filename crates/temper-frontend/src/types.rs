//! Static types, interned: each [`TypeId`] stands for one [`TypeData`],
//! so comparing types is comparing ids.
//!
//! Only some types are known well enough to reject a value: the final
//! builtin types ([`Types::is_final`]: `Boolean`, `Int32`, `Int64`,
//! `Float64`, `String` and `Void`), `AnyValue`, null, function types, and
//! classes and interfaces, whose supertypes typing registers
//! ([`Types::set_class`]), type parameters, which are subtypes of their bounds
//! only, and generic types given arguments, as `List<Int>`, as far as their
//! arguments tell them apart (notes/typing.md, "Generic types"). Anything
//! else may have subtypes that typing doesn't know about yet, so a value is
//! never rejected for it ([`Types::is_subtype`] answers `None`).
//! [`UNKNOWN`] stands for a type not worked out, and never causes an error
//! either.

use crate::builtins::TYPES;
use crate::scope::SymbolId;
use std::collections::HashMap;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct TypeId(pub u32);

/// A type.
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub enum TypeData {
    /// Not worked out: anything may be assigned to it or from it.
    Unknown,
    /// The type of what never completes, as `panic()` or `return`; a
    /// subtype of every type.
    Never,
    /// `Never<T>`, as a function that never returns declares its result:
    /// it never completes either, but stands for a `T`, so it's a subtype
    /// only of what `T` is a subtype of, and of `Never<U>` for each such
    /// `U` (core's docs), and translates to `T`. Made by
    /// [`Types::never_of`], so `T` is neither nullable nor a `Never`.
    NeverOf(TypeId),
    /// The type of `null`.
    Null,
    /// A builtin type, by its index in [`TYPES`]. `Int` is `Int32`.
    Builtin(u8),
    /// A class or interface without type parameters, by the symbol its
    /// declaration declares.
    Class(SymbolId),
    /// A type parameter, as `T` in `let f<T>(x: T)` or `class Box<T>`, by
    /// the symbol its declaration declares; its bound is in [`Types`].
    Param(SymbolId),
    /// A generic type given arguments, as `List<Int>` or `Box<String>`:
    /// `base` is a [`TypeData::Builtin`] or [`TypeData::Class`].
    Applied { base: TypeId, args: Vec<TypeId> },
    /// `T?`: a type or null. `T` isn't nullable itself.
    Nullable(TypeId),
    /// `fn (A, B): R`, or `fn (A, B): R throws Bubble` if `throws`. A
    /// parameter with a default is optional.
    Fn {
        params: Vec<Param>,
        ret: TypeId,
        throws: bool,
    },
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct Param {
    pub ty: TypeId,
    pub optional: bool,
}

pub const UNKNOWN: TypeId = TypeId(0);
pub const NEVER: TypeId = TypeId(1);
pub const NULL: TypeId = TypeId(2);
pub const VOID: TypeId = TypeId(3);
pub const BOOLEAN: TypeId = TypeId(4);
pub const INT32: TypeId = TypeId(5);
pub const INT64: TypeId = TypeId(6);
pub const FLOAT64: TypeId = TypeId(7);
pub const STRING: TypeId = TypeId(8);
pub const ANY_VALUE: TypeId = TypeId(9);

/// The builtin types with fixed ids, from [`VOID`] to [`ANY_VALUE`].
const WELL_KNOWN: [&str; 7] = [
    "Void", "Boolean", "Int32", "Int64", "Float64", "String", "AnyValue",
];

/// What subtyping needs to know of a class or interface.
#[derive(Clone, Debug, Default)]
pub struct ClassInfo {
    /// Its supertypes, theirs and so on.
    pub ancestors: Vec<SymbolId>,
    /// Its type parameters, as `T` in `class Sub<T>`.
    pub formals: Vec<SymbolId>,
    /// Which of them are marked `@out` or `@in`.
    pub variant: Vec<bool>,
    /// The types it extends, as written, naming its type parameters, as
    /// `Sup<T>` in `class Sub<T> extends Sup<T>`; [`UNKNOWN`] where not
    /// worked out.
    pub supers: Vec<TypeId>,
    /// For a `@fun` interface, its `apply`'s signature, a function type
    /// naming its type parameters, as `fn (A): B` for `@fun interface
    /// Mapper<A, B>(a: A): B;`.
    pub fun_sig: Option<TypeId>,
}

/// The interner.
#[derive(Debug)]
pub struct Types {
    data: Vec<TypeData>,
    ids: HashMap<TypeData, TypeId>,
    classes: HashMap<SymbolId, ClassInfo>,
    /// Type parameters' bounds, [`UNKNOWN`] where not worked out.
    bounds: HashMap<SymbolId, TypeId>,
}

/// Builtin generic types and the generic types they extend, with the
/// same type arguments, as `class List<T> extends Listed<T>` in core; and
/// the final types that are `MapKey`s of themselves, as `class String
/// extends MapKey<String>`; and `StringIndex` and `NoStringIndex`, which
/// extend `StringIndexOption`. Core's `Equatable` isn't a builtin type yet.
const BUILTIN_SUPERTYPES: [(&str, &str); 11] = [
    ("List", "Listed"),
    ("ListBuilder", "Listed"),
    ("Map", "Mapped"),
    ("MapBuilder", "Mapped"),
    ("SafeGenerator", "Generator"),
    ("ValueResult", "GeneratorResult"),
    ("DoneResult", "GeneratorResult"),
    ("Int32", "MapKey"),
    ("String", "MapKey"),
    ("StringIndex", "StringIndexOption"),
    ("NoStringIndex", "StringIndexOption"),
];

/// The builtin generic type that builtin generic type `name` extends with
/// the same type arguments, as `Listed` for `List`, if any.
pub fn builtin_supertype(name: &str) -> Option<&'static str> {
    (BUILTIN_SUPERTYPES.iter())
        .find(|&&(sub, _)| sub == name && !WELL_KNOWN.contains(&sub))
        .map(|&(_, sup)| sup)
}

/// Core's non-generic classes and interface beyond the final types:
/// `StringIndex` and `NoStringIndex` extend the sealed `StringIndexOption`,
/// and nothing else extends any of them, so each is a subtype of only
/// itself and what core says it extends ([`Types::is_subtype`]).
const CORE_NOMINAL: [&str; 5] = [
    "DenseBitVector",
    "NoStringIndex",
    "StringBuilder",
    "StringIndex",
    "StringIndexOption",
];

/// Builtin generic types that core declares as classes, which no class
/// extends; core marks the first four `@mayDowncastTo(false)`.
const BUILTIN_GENERIC_CLASSES: [&str; 10] = [
    "List",
    "ListBuilder",
    "Map",
    "MapBuilder",
    "Deque",
    "DoneResult",
    "Pair",
    "Promise",
    "PromiseBuilder",
    "ValueResult",
];

/// How many of [`BUILTIN_GENERIC_CLASSES`] are `@mayDowncastTo(false)`.
const NO_DOWNCAST: usize = 4;

/// The builtin generic types' type parameters, by index, that core marks
/// `@out` or `@in`, which Kotlin Temper honors and tempest doesn't
/// (notes/typing.md, "Type parameters are invariant").
const BUILTIN_VARIANT_PARAMS: [(&str, usize); 13] = [
    ("DoneResult", 0),
    ("Generator", 0),
    ("GeneratorResult", 0),
    ("List", 0),
    ("Map", 0),
    ("Map", 1),
    ("Mapped", 0),
    ("Never", 0),
    ("Pair", 0),
    ("Pair", 1),
    ("Promise", 0),
    ("SafeGenerator", 0),
    ("ValueResult", 0),
];

/// How deep [`Types::is_subtype`] looks, through type parameters' bounds
/// and type arguments, before giving up.
const MAX_SUBTYPE_DEPTH: u32 = 16;

impl Default for Types {
    fn default() -> Types {
        Types::new()
    }
}

impl Types {
    pub fn new() -> Types {
        let mut types = Types {
            data: Vec::new(),
            ids: HashMap::new(),
            classes: HashMap::new(),
            bounds: HashMap::new(),
        };
        for data in [TypeData::Unknown, TypeData::Never, TypeData::Null] {
            types.intern(data);
        }
        for name in WELL_KNOWN {
            let index = TYPES.iter().position(|&t| t == name).unwrap();
            types.intern(TypeData::Builtin(index as u8));
        }
        debug_assert_eq!(types.data.len() as u32, ANY_VALUE.0 + 1);
        types
    }

    pub fn intern(&mut self, data: TypeData) -> TypeId {
        if let Some(&id) = self.ids.get(&data) {
            return id;
        }
        let id = TypeId(self.data.len() as u32);
        self.data.push(data.clone());
        self.ids.insert(data, id);
        id
    }

    pub fn get(&self, id: TypeId) -> &TypeData {
        &self.data[id.0 as usize]
    }

    /// Tells what subtyping needs to know of a class or interface.
    pub fn set_class(&mut self, symbol: SymbolId, info: ClassInfo) {
        self.classes.insert(symbol, info);
    }

    /// What [`Types::set_class`] was told about a class.
    pub fn class_info(&self, symbol: SymbolId) -> Option<&ClassInfo> {
        self.classes.get(&symbol)
    }

    /// Whether [`Types::set_class`] has been told about a class.
    pub fn knows_class(&self, symbol: SymbolId) -> bool {
        self.classes.contains_key(&symbol)
    }

    /// A type parameter's type. Its bound is set by
    /// [`Types::set_bound`], unknown until then, however early the type is
    /// made.
    pub fn param(&mut self, symbol: SymbolId) -> TypeId {
        self.intern(TypeData::Param(symbol))
    }

    /// Sets a type parameter's bound: `AnyValue` if it has none. A bound
    /// that's a type parameter, as `U` in `<T extends U>`, is left unknown
    /// for now, so bounds can't lead around in a circle.
    pub fn set_bound(&mut self, symbol: SymbolId, bound: TypeId) {
        let bound = match self.get(bound) {
            TypeData::Param(_) => UNKNOWN,
            _ => bound,
        };
        self.bounds.insert(symbol, bound);
    }

    /// A type parameter's bound, if set ([`Types::set_bound`]).
    pub fn bound_of(&self, symbol: SymbolId) -> Option<TypeId> {
        self.bounds.get(&symbol).copied()
    }

    /// What a type is known to be a subtype of: a type parameter's bound,
    /// or the type itself.
    pub fn upper(&self, t: TypeId) -> TypeId {
        match self.get(t) {
            TypeData::Param(symbol) => self.bounds.get(symbol).copied().unwrap_or(UNKNOWN),
            _ => t,
        }
    }

    /// `t` with the type parameters `formals` unknown, as a generic
    /// function's signature is seen where its calls aren't solved.
    pub fn erase(&mut self, t: TypeId, formals: &[SymbolId]) -> TypeId {
        self.substitute(t, formals, &vec![UNKNOWN; formals.len()])
    }

    /// `t` with each of the type parameters `formals` replaced by the
    /// type at its index in `actuals`, as a generic call's solution gives
    /// them.
    pub fn substitute(&mut self, t: TypeId, formals: &[SymbolId], actuals: &[TypeId]) -> TypeId {
        if formals.is_empty() {
            return t;
        }
        match self.get(t).clone() {
            TypeData::Param(symbol) => match formals.iter().position(|&f| f == symbol) {
                Some(i) => actuals[i],
                None => t,
            },
            TypeData::Nullable(inner) => {
                let inner = self.substitute(inner, formals, actuals);
                self.nullable(inner)
            }
            TypeData::NeverOf(inner) => {
                let inner = self.substitute(inner, formals, actuals);
                self.never_of(inner)
            }
            TypeData::Applied { base, args } => {
                let args = (args.iter())
                    .map(|&a| self.substitute(a, formals, actuals))
                    .collect();
                self.intern(TypeData::Applied { base, args })
            }
            TypeData::Fn {
                params,
                ret,
                throws,
            } => {
                let params = (params.iter())
                    .map(|p| Param {
                        ty: self.substitute(p.ty, formals, actuals),
                        optional: p.optional,
                    })
                    .collect();
                let ret = self.substitute(ret, formals, actuals);
                self.intern(TypeData::Fn {
                    params,
                    ret,
                    throws,
                })
            }
            _ => t,
        }
    }

    /// Whether `t` mentions one of the type parameters `formals`.
    pub fn mentions(&self, t: TypeId, formals: &[SymbolId]) -> bool {
        match self.get(t) {
            TypeData::Param(symbol) => formals.contains(symbol),
            TypeData::Nullable(inner) | TypeData::NeverOf(inner) => self.mentions(*inner, formals),
            TypeData::Applied { args, .. } => args.iter().any(|&a| self.mentions(a, formals)),
            TypeData::Fn { params, ret, .. } => {
                params.iter().any(|p| self.mentions(p.ty, formals)) || self.mentions(*ret, formals)
            }
            _ => false,
        }
    }

    /// Solves the type parameters `formals` from a value of type `actual`
    /// passed where a `pattern` is wanted, part by part: `T` from `Int32`,
    /// `List<T>` from `List<Int32>`, or from a subtype as it sees that
    /// supertype ([`Types::supertype_as`]), as `ListBuilder<Int32>` or a
    /// `Sub<Int32>` for `class Sub<T> extends Sup<T>` where a `Sup<T>` is
    /// wanted, `T?` from `Int32?` or `Int32`, and function types'
    /// parameters and results, with a `@fun` interface's type as the
    /// function type it stands for, unless both are of one interface.
    /// Each solution goes into `actuals` at the formal's index, `Dog` and
    /// `Dog?` joined; one solved two different ways goes into `conflicts`
    /// too. Only fully known types are solutions, and not null, `Never`,
    /// or `Void`; a nullable type is one for a bare `T`, as for Kotlin
    /// Temper, whose bound check takes it as fitting only `AnyValue`. A
    /// `Never<A>` passed where no `Never` is wanted solves as its `A`, as
    /// Kotlin's reconciliation unpacks it.
    pub fn unify(
        &mut self,
        pattern: TypeId,
        actual: TypeId,
        formals: &[SymbolId],
        actuals: &mut [TypeId],
        conflicts: &mut [bool],
    ) {
        use TypeData::*;
        match (self.get(pattern).clone(), self.get(actual).clone()) {
            (NeverOf(p), NeverOf(a)) => self.unify(p, a, formals, actuals, conflicts),
            (_, NeverOf(a)) => self.unify(pattern, a, formals, actuals, conflicts),
            (Param(symbol), _) => {
                let Some(i) = formals.iter().position(|&f| f == symbol) else {
                    return;
                };
                if matches!(self.get(actual), Never | Null)
                    || actual == VOID
                    || !self.is_known(actual)
                {
                    return;
                }
                match actuals[i] {
                    UNKNOWN => actuals[i] = actual,
                    // `Dog` and `Dog?` solve it as `Dog?`, as in Kotlin.
                    solved if self.nullable(solved) == self.nullable(actual) => {
                        actuals[i] = self.join(solved, actual);
                    }
                    _ => conflicts[i] = true,
                }
            }
            (Nullable(p), Nullable(a)) => self.unify(p, a, formals, actuals, conflicts),
            (Nullable(p), _) => self.unify(p, actual, formals, actuals, conflicts),
            _ if self.base(pattern) != self.base(actual) && self.is_fun_pair(pattern, actual) => {
                if let (Some(p), Some(a)) = (self.as_fn(pattern), self.as_fn(actual)) {
                    self.unify(p, a, formals, actuals, conflicts);
                }
            }
            (Applied { base, args: x }, _) => {
                let Some(seen) = self.supertype_as(actual, base) else {
                    return;
                };
                let y = self.args(seen).to_vec();
                if x.len() == y.len() {
                    for (&x, &y) in x.iter().zip(&y) {
                        self.unify(x, y, formals, actuals, conflicts);
                    }
                }
            }
            (
                Fn {
                    params: x, ret: p, ..
                },
                Fn {
                    params: y, ret: a, ..
                },
            ) if x.len() == y.len() => {
                for (x, y) in x.iter().zip(&y) {
                    self.unify(x.ty, y.ty, formals, actuals, conflicts);
                }
                self.unify(p, a, formals, actuals, conflicts);
            }
            _ => {}
        }
    }

    /// `t` as the generic type `wanted`'s base sees it, if that's one of
    /// its supertypes ([`Types::supertype_as`]), as `Listed<T>` for a
    /// `List<T>` where a `Listed<String>` is wanted; else `t`.
    pub fn as_supertype(&mut self, t: TypeId, wanted: TypeId) -> TypeId {
        match self.get(wanted) {
            TypeData::Applied { base, .. } => self.supertype_as(t, *base).unwrap_or(t),
            _ => t,
        }
    }

    /// `t` as its supertype with base `base` sees it, with `t`'s type
    /// arguments put in for its class's type parameters, if `t` is known to
    /// have one: `t` itself if `base` is its base; `Sup<Int32>` for a
    /// `Sub<Int32>`, `class Sub<T> extends Sup<T>`, and for a `Sub4`,
    /// `class Sub4 extends Sup<Int>`; `Listed<Int32>` for a `List<Int32>`;
    /// and `MapKey<String>` for a `String`. Of several ways up, the first,
    /// depth-first in `extends` order.
    pub fn supertype_as(&mut self, t: TypeId, base: TypeId) -> Option<TypeId> {
        self.supertype_at(t, base, 0)
    }

    fn supertype_at(&mut self, t: TypeId, base: TypeId, depth: u32) -> Option<TypeId> {
        let own = self.base(t);
        if own == base {
            return Some(t);
        }
        if depth > MAX_SUBTYPE_DEPTH {
            return None;
        }
        match *self.get(own) {
            TypeData::Builtin(_) if self.builtin_extends(own, base) => {
                // `String` is a `MapKey<String>`; `List<T>`, a `Listed<T>`.
                let args = match self.is_final(own) {
                    true => vec![t],
                    false => self.args(t).to_vec(),
                };
                Some(self.intern(TypeData::Applied { base, args }))
            }
            TypeData::Class(symbol) => {
                let info = self.classes.get(&symbol)?;
                let (formals, supers) = (info.formals.clone(), info.supers.clone());
                let args = self.args(t).to_vec();
                if args.len() != formals.len() {
                    return None;
                }
                (supers.into_iter()).find_map(|sup| {
                    let sup = self.substitute(sup, &formals, &args);
                    self.supertype_at(sup, base, depth + 1)
                })
            }
            _ => None,
        }
    }

    /// Whether a type mentions a type parameter or type arguments.
    pub fn is_generic(&self, t: TypeId) -> bool {
        match self.get(t) {
            TypeData::Param(_) | TypeData::Applied { .. } => true,
            TypeData::Nullable(t) | TypeData::NeverOf(t) => self.is_generic(*t),
            TypeData::Fn { params, ret, .. } => {
                params.iter().any(|p| self.is_generic(p.ty)) || self.is_generic(*ret)
            }
            _ => false,
        }
    }

    /// A builtin type's name, if `t` is one.
    pub fn builtin_name(&self, t: TypeId) -> Option<&'static str> {
        match self.get(t) {
            TypeData::Builtin(i) => Some(TYPES[*i as usize]),
            _ => None,
        }
    }

    /// Whether `t`, or a generic type's base, is a builtin class, which no
    /// class extends: a final builtin type or one of core's generic
    /// classes, as `List`.
    pub fn is_builtin_class(&self, t: TypeId) -> bool {
        let base = self.base(t);
        self.is_final(base)
            || (self.builtin_name(base)).is_some_and(|n| BUILTIN_GENERIC_CLASSES.contains(&n))
    }

    /// Whether core marks `t`, or a generic type's base, `@mayDowncastTo(false)`:
    /// the final builtin types other than `Void`, and `List`, `Map` and
    /// their builders.
    pub fn no_downcast(&self, t: TypeId) -> bool {
        let base = self.base(t);
        (self.is_final(base) && base != VOID)
            || (self.builtin_name(base))
                .is_some_and(|n| BUILTIN_GENERIC_CLASSES[..NO_DOWNCAST].contains(&n))
    }

    /// Whether core marks a builtin generic type's `index`th type
    /// parameter `@out` or `@in`.
    pub fn is_builtin_variant(&self, base: TypeId, index: usize) -> bool {
        (self.builtin_name(base)).is_some_and(|n| BUILTIN_VARIANT_PARAMS.contains(&(n, index)))
    }

    /// Whether a generic type's `index`th type parameter is marked `@out`
    /// or `@in`, or isn't known to be invariant, as a class's whose
    /// parameters aren't worked out yet.
    fn is_variant(&self, base: TypeId, index: usize) -> bool {
        match self.get(base) {
            TypeData::Class(symbol) => (self.classes.get(symbol))
                .and_then(|info| info.variant.get(index).copied())
                .unwrap_or(true),
            _ => self.is_builtin_variant(base, index),
        }
    }

    /// A generic type's base, as `List` for `List<Int>`, or `t` itself.
    pub fn base(&self, t: TypeId) -> TypeId {
        match self.get(t) {
            TypeData::Applied { base, .. } => *base,
            _ => t,
        }
    }

    /// A generic type's arguments, none for another type.
    pub fn args(&self, t: TypeId) -> &[TypeId] {
        match self.get(t) {
            TypeData::Applied { args, .. } => args,
            _ => &[],
        }
    }

    /// A builtin type by its index in [`TYPES`].
    pub fn builtin(&mut self, index: u8) -> TypeId {
        match TYPES[index as usize] {
            "Int" => INT32,
            "Never" => NEVER,
            _ => self.intern(TypeData::Builtin(index)),
        }
    }

    /// `List<t>`.
    pub fn list_of(&mut self, t: TypeId) -> TypeId {
        let list = TYPES.iter().position(|&t| t == "List").unwrap();
        let base = self.builtin(list as u8);
        self.intern(TypeData::Applied {
            base,
            args: vec![t],
        })
    }

    /// `Never<t>` ([`TypeData::NeverOf`]), as Kotlin Temper normalizes
    /// it: `Never<T?>` is `Never<T>?`, `Never<Never<T>>` is `Never<T>`,
    /// and `Never<…>` of what never completes, that.
    pub fn never_of(&mut self, t: TypeId) -> TypeId {
        match *self.get(t) {
            TypeData::Never | TypeData::NeverOf(_) => t,
            TypeData::Nullable(inner) => {
                let never = self.never_of(inner);
                self.nullable(never)
            }
            _ => self.intern(TypeData::NeverOf(t)),
        }
    }

    /// Builtin type `base` given `args`, as `List<Int32>`, or `Never<T>`
    /// ([`Types::never_of`]); unknown for one that takes no arguments, as
    /// `AnyValue` or a final type, or for `Never` given more than one.
    pub fn apply_builtin(&mut self, base: TypeId, args: Vec<TypeId>) -> TypeId {
        match (base, &args[..]) {
            (NEVER, &[t]) => self.never_of(t),
            (NEVER | ANY_VALUE, _) => UNKNOWN,
            _ if self.is_final(base) => UNKNOWN,
            _ => self.intern(TypeData::Applied { base, args }),
        }
    }

    /// Whether a value of type `t` is used as Kotlin Temper uses `Void`'s:
    /// `Void`, or `Never<Void>`, which stands for it.
    pub fn is_void_like(&self, t: TypeId) -> bool {
        t == VOID || *self.get(t) == TypeData::NeverOf(VOID)
    }

    /// `t?`.
    pub fn nullable(&mut self, t: TypeId) -> TypeId {
        match self.get(t) {
            TypeData::Unknown | TypeData::Null | TypeData::Nullable(_) => t,
            TypeData::Never => NULL,
            _ => self.intern(TypeData::Nullable(t)),
        }
    }

    /// The type of a value that may come from either of two branches: for
    /// two classes or interfaces, the one supertype of both that's below
    /// every other, as Kotlin Temper's least upper bound finds it (`E`
    /// for an `A` and a `B` that both extend `E`), the same for both in
    /// its type arguments; else unknown.
    pub fn join(&mut self, a: TypeId, b: TypeId) -> TypeId {
        match (self.get(a).clone(), self.get(b).clone()) {
            _ if a == b => a,
            (TypeData::Unknown, _) | (_, TypeData::Unknown) => UNKNOWN,
            (TypeData::Never, _) => b,
            (_, TypeData::Never) => a,
            // As Kotlin's least upper bound unpacks a `Never<T>` beside
            // another type: `Int32` for an `Int32` and a `Never<Int32>`.
            (TypeData::NeverOf(x), TypeData::NeverOf(y)) => match self.join(x, y) {
                UNKNOWN => UNKNOWN,
                t => self.never_of(t),
            },
            (TypeData::NeverOf(x), _) => self.join(x, b),
            (_, TypeData::NeverOf(y)) => self.join(a, y),
            (TypeData::Null, _) => self.nullable(b),
            (_, TypeData::Null) => self.nullable(a),
            (TypeData::Nullable(x), _) if x == b => a,
            (_, TypeData::Nullable(x)) if x == a => b,
            (TypeData::Nullable(x), _) => match self.join(x, b) {
                UNKNOWN => UNKNOWN,
                t => self.nullable(t),
            },
            (_, TypeData::Nullable(y)) => match self.join(a, y) {
                UNKNOWN => UNKNOWN,
                t => self.nullable(t),
            },
            _ => self.common_supertype(a, b).unwrap_or(UNKNOWN),
        }
    }

    /// The least common supertype of two class, interface, or builtin
    /// types, if there's exactly one ([`Types::join`]).
    fn common_supertype(&mut self, a: TypeId, b: TypeId) -> Option<TypeId> {
        let (above_a, above_b) = (self.bases_above(a), self.bases_above(b));
        let common: Vec<TypeId> = (above_a.iter().copied())
            .filter(|t| above_b.contains(t))
            .collect();
        // Those no other common one is below.
        let least: Vec<TypeId> = (common.iter().copied())
            .filter(|&c| (common.iter()).all(|&d| d == c || !self.bases_above(d).contains(&c)))
            .collect();
        let [base] = least[..] else {
            return None;
        };
        let (x, y) = (self.supertype_as(a, base)?, self.supertype_as(b, base)?);
        (x == y).then_some(x)
    }

    /// The bases of a type and of all its supertypes: its class and those
    /// it extends, or a builtin type and the one it extends.
    fn bases_above(&self, t: TypeId) -> Vec<TypeId> {
        let base = self.base(t);
        let mut out = vec![base];
        match self.get(base) {
            TypeData::Class(symbol) => {
                for ancestor in self.classes.get(symbol).map_or(&[][..], |i| &i.ancestors) {
                    if let Some(&id) = self.ids.get(&TypeData::Class(*ancestor)) {
                        out.push(id);
                    }
                }
            }
            TypeData::Builtin(i) => {
                let sup = BUILTIN_SUPERTYPES
                    .iter()
                    .find(|&&(sub, _)| sub == TYPES[*i as usize]);
                if let Some(index) = sup.and_then(|&(_, sup)| TYPES.iter().position(|&n| n == sup))
                {
                    if let Some(&id) = self.ids.get(&TypeData::Builtin(index as u8)) {
                        out.push(id);
                    }
                }
            }
            _ => return Vec::new(),
        }
        out
    }

    /// A final builtin type, which no other type extends.
    pub fn is_final(&self, t: TypeId) -> bool {
        (VOID.0..=STRING.0).contains(&t.0)
    }

    /// Whether every part of a type is worked out.
    pub fn is_known(&self, t: TypeId) -> bool {
        match self.get(t) {
            TypeData::Unknown => false,
            TypeData::Nullable(t) | TypeData::NeverOf(t) => self.is_known(*t),
            TypeData::Applied { base, args } => {
                self.is_known(*base) && args.iter().all(|&a| self.is_known(a))
            }
            TypeData::Fn { params, ret, .. } => {
                params.iter().all(|p| self.is_known(p.ty)) && self.is_known(*ret)
            }
            _ => true,
        }
    }

    /// Whether a value of type `s` may be used as a `t`, if that's known.
    /// One generic type given arguments is another's only if, as its
    /// supertype with the other's base sees it, they're the same, but
    /// reported as not only where an argument sets them apart under any
    /// variance (notes/typing.md, "Generic types"); likewise one function
    /// type is another only if they're the same, but is reported as not
    /// only where Kotlin Temper's variance rejects it too
    /// ([`Types::fn_subtype`]). A type parameter is a subtype of its
    /// bound. A class or interface is a subtype of what it extends, and
    /// nothing else, even if a value of an interface may be of a class that
    /// extends another type too: an `is` check narrows it to that. A
    /// function is a `@fun` interface's value, and such a value a function,
    /// where the function types they are fit, but no other `@fun`
    /// interface's value ([`Types::fun_subtype`]); a function is no other
    /// class's or interface's value, nor theirs a function.
    pub fn is_subtype(&mut self, s: TypeId, t: TypeId) -> Option<bool> {
        self.subtype(s, t, 0)
    }

    fn subtype(&mut self, s: TypeId, t: TypeId, depth: u32) -> Option<bool> {
        use TypeData::*;
        if depth > MAX_SUBTYPE_DEPTH {
            return None;
        }
        let depth = depth + 1;
        match (self.get(s).clone(), self.get(t).clone()) {
            _ if s == t => Some(true),
            (Unknown, _) | (_, Unknown) => None,
            (Never, _) => Some(true),
            (Null, Nullable(_)) => Some(true),
            (Nullable(a), Nullable(b)) => self.subtype(a, b, depth),
            (_, Nullable(b)) => self.subtype(s, b, depth),
            // `Never<A>` is what an `A` is, and is a `Never<B>` if `A` is a
            // `B`, `@out` as core declares it; nothing else is a `Never<B>`
            // but what never completes.
            (NeverOf(a), NeverOf(b)) => self.subtype(a, b, depth),
            (NeverOf(a), _) => self.subtype(a, t, depth),
            (_, NeverOf(_)) => Some(false),
            // Unless its bound is nullable, `T` isn't.
            (Null | Nullable(_), Param(_))
                if self.subtype(NULL, self.upper(t), depth) != Some(false) =>
            {
                None
            }
            (Null | Nullable(_), _) => (self.is_definite(t)
                || self.is_class_like(t)
                || self.is_core_nominal(t)
                || t == ANY_VALUE)
                .then_some(false),
            // A type parameter is a subtype of its bound only, and nothing
            // but itself and `Never` is a subtype of it.
            (Param(_), _) => {
                let upper = self.upper(s);
                match (upper, self.subtype(upper, t, depth)) {
                    // What an `AnyValue` may hold, a `T` needn't.
                    (ANY_VALUE, None) => Some(false),
                    (_, known) => known,
                }
            }
            (_, Param(_)) => Some(false),
            _ if t == ANY_VALUE => {
                (s != VOID && (self.is_definite(s) || self.is_class_like(s))).then_some(true)
            }
            _ if self.is_fun_pair(s, t) => self.fun_subtype(s, t, depth),
            // Core's non-generic classes, as `StringBuilder`, are only what
            // core says they extend, and no other type is one of them.
            _ if self.is_core_nominal(s) || self.is_core_nominal(t) => {
                Some(self.builtin_extends(s, t))
            }
            (Applied { .. }, _) | (_, Applied { .. }) => self.generic_subtype(s, t, depth),
            (Class(a), Class(b)) => Some(self.classes.get(&a)?.ancestors.contains(&b)),
            (Class(_), _) if self.is_final(t) => Some(false),
            (_, Class(_)) if self.is_final(s) => Some(false),
            (Fn { .. }, Fn { .. }) => self.fn_subtype(s, t, depth),
            // A function and a class or interface that isn't `@fun`.
            (Fn { .. }, Class(_)) | (Class(_), Fn { .. }) => Some(false),
            _ => (self.is_definite(s) && self.is_definite(t)).then_some(false),
        }
    }

    /// [`Types::subtype`] for two different function types, compared part
    /// by part as Kotlin Temper does, with parameters contravariant and
    /// results covariant: `s` isn't a `t` if it may bubble and `t` doesn't,
    /// if they take different numbers of parameters (counting `s`'s
    /// optional ones), or if a parameter of `t` isn't one of `s`'s, or
    /// `s`'s result isn't `t`'s. Otherwise it may be, under that variance,
    /// as `fn (Animal): Dog` for a `fn (Dog): Animal`, which tempest leaves
    /// unreported until it's settled whether function types are invariant
    /// too (notes/typing.md, "Function types").
    fn fn_subtype(&mut self, s: TypeId, t: TypeId, depth: u32) -> Option<bool> {
        let (
            TypeData::Fn {
                params: x,
                ret: r,
                throws: x_throws,
            },
            TypeData::Fn {
                params: y,
                ret: q,
                throws: y_throws,
            },
        ) = (self.get(s).clone(), self.get(t).clone())
        else {
            return None;
        };
        if (x_throws && !y_throws) || x.len() != y.len() {
            return Some(false);
        }
        // Kotlin lets a function with optional parameters be one that
        // takes them all; one whose parameter is optional, which no type
        // written out has, isn't compared.
        if (x.iter().zip(&y)).any(|(x, y)| y.optional && !x.optional) {
            return None;
        }
        let parts = (x.iter().zip(&y)).map(|(x, y)| (y.ty, x.ty));
        let apart =
            (parts.chain([(r, q)])).any(|(sub, sup)| self.subtype(sub, sup, depth) == Some(false));
        apart.then_some(false)
    }

    /// Whether one of `s` and `t` is a `@fun` interface's type and the
    /// other is too, or is a function type.
    fn is_fun_pair(&self, s: TypeId, t: TypeId) -> bool {
        let is_fn = |x: TypeId| matches!(self.get(x), TypeData::Fn { .. });
        let (s_fun, t_fun) = (self.is_fun_interface(s), self.is_fun_interface(t));
        (s_fun && (t_fun || is_fn(t))) || (t_fun && is_fn(s))
    }

    /// [`Types::subtype`] where [`Types::is_fun_pair`] says: a `@fun`
    /// interface's type and a function type part by part
    /// ([`Types::fn_subtype`]), as the function type the first stands for
    /// ([`Types::fun_sig`]), as Kotlin Temper compares them; two `@fun`
    /// interfaces' types of one declaration as generic types are; and of
    /// two declarations, never each other's, even where they stand for
    /// the same function type, as named function types are nominal
    /// (notes/typing.md, "Fun interfaces"), where Kotlin compares them as
    /// those function types.
    fn fun_subtype(&mut self, s: TypeId, t: TypeId, depth: u32) -> Option<bool> {
        let both_fun = self.is_fun_interface(s) && self.is_fun_interface(t);
        match both_fun {
            true if self.base(s) == self.base(t) => self.generic_subtype(s, t, depth),
            true => Some(false),
            false => {
                let (x, y) = (self.as_fn(s)?, self.as_fn(t)?);
                self.fn_subtype(x, y, depth)
            }
        }
    }

    /// Whether a value of one `@fun` interface's type, `s`, would fit
    /// where another's, `t`, is wanted, if it weren't for their names:
    /// whether the function type it stands for may be the one `t` stands
    /// for, so wrapping it in a function literal adapts it.
    pub fn fun_adaptable(&mut self, s: TypeId, t: TypeId) -> bool {
        let differ = self.base(s) != self.base(t);
        if !(differ && self.is_fun_interface(s) && self.is_fun_interface(t)) {
            return false;
        }
        match (self.as_fn(s), self.as_fn(t)) {
            (Some(x), Some(y)) => self.fn_subtype(x, y, 0) != Some(false),
            _ => false,
        }
    }

    /// Whether `t` is a `@fun` interface's type, as `Pred` or
    /// `Mapper<Int32, String>`.
    pub fn is_fun_interface(&self, t: TypeId) -> bool {
        match self.get(self.base(t)) {
            TypeData::Class(symbol) => {
                (self.classes.get(symbol)).is_some_and(|c| c.fun_sig.is_some())
            }
            _ => false,
        }
    }

    /// Whether `t` is a function type or a `@fun` interface's type.
    pub fn is_functional(&self, t: TypeId) -> bool {
        matches!(self.get(t), TypeData::Fn { .. }) || self.is_fun_interface(t)
    }

    /// The function type a `@fun` interface's type stands for, its
    /// `apply`'s signature with the type's arguments put in, as `fn
    /// (Int32): String` for a `Mapper<Int32, String>`, for `@fun interface
    /// Mapper<A, B>(a: A): B;`. `None` for other types.
    pub fn fun_sig(&mut self, t: TypeId) -> Option<TypeId> {
        let TypeData::Class(symbol) = *self.get(self.base(t)) else {
            return None;
        };
        let info = self.classes.get(&symbol)?;
        let (sig, formals) = (info.fun_sig?, info.formals.clone());
        let args = self.args(t).to_vec();
        (args.len() == formals.len()).then(|| self.substitute(sig, &formals, &args))
    }

    /// The function type a value of type `t` is called as: `t` itself if
    /// it's one, or what a `@fun` interface's type stands for
    /// ([`Types::fun_sig`]).
    pub fn as_fn(&mut self, t: TypeId) -> Option<TypeId> {
        match self.get(t) {
            TypeData::Fn { .. } => Some(t),
            _ => self.fun_sig(t),
        }
    }

    /// [`Types::subtype`] where `s` or `t` is a generic type given
    /// arguments, neither of them null, nullable, a type parameter, or
    /// `AnyValue` for `t`. `s` is compared as its supertype with `t`'s base
    /// sees it ([`Types::supertype_as`]): with the same arguments, it's a
    /// `t`; with a pair of arguments neither of which is a subtype of the
    /// other, it isn't, whatever the parameters' variance; otherwise it
    /// may be, under some variance. Without such a supertype, it isn't one
    /// where its declaration says what it extends: a builtin type, which
    /// extends only builtin types core says it does, or a user's class or
    /// interface that doesn't extend `t`'s.
    fn generic_subtype(&mut self, s: TypeId, t: TypeId, depth: u32) -> Option<bool> {
        let (s_base, t_base) = (self.base(s), self.base(t));
        if let Some(seen) = self.supertype_as(s, t_base) {
            if seen == t {
                return Some(true);
            }
            let (x, y) = (self.args(seen).to_vec(), self.args(t).to_vec());
            let apart = x.len() == y.len()
                && (x.iter().zip(&y).enumerate()).any(|(i, (&x, &y))| {
                    let (up, down) = (self.subtype(x, y, depth), self.subtype(y, x, depth));
                    match self.is_variant(t_base, i) {
                        true => up == Some(false) && down == Some(false),
                        false => up == Some(false) || down == Some(false),
                    }
                });
            return apart.then_some(false);
        }
        let is_applied = |x: TypeId| matches!(self.get(x), TypeData::Applied { .. });
        match (self.get(s_base), self.get(t_base)) {
            // A builtin generic type or final type is no other type than
            // those core says it extends, and no user's class or interface.
            (TypeData::Builtin(_), TypeData::Builtin(_) | TypeData::Class(_))
                if (is_applied(s) || self.is_final(s))
                    && (is_applied(t) || matches!(self.get(t), TypeData::Class(_))) =>
            {
                Some(false)
            }
            _ if self.is_definite(s) || self.is_definite(t) => Some(false),
            (&TypeData::Class(a), &TypeData::Class(b)) => {
                let info = self.classes.get(&a)?;
                (!info.ancestors.contains(&b)).then_some(false)
            }
            // A class or interface whose every supertype is known, and
            // none of them `t`'s base, isn't a `t`: a `Name` isn't a
            // `List<Boolean>`, so of `@overload`s taking each, a `Name`
            // goes to the first.
            (&TypeData::Class(a), TypeData::Builtin(_)) => self.supers_known(a, 0).then_some(false),
            _ => None,
        }
    }

    /// Whether every type a class or interface extends, and each of
    /// those extends, and so on, is worked out.
    fn supers_known(&self, class: SymbolId, depth: u32) -> bool {
        let Some(info) = self.classes.get(&class) else {
            return false;
        };
        depth <= MAX_SUBTYPE_DEPTH
            && (info.supers.iter()).all(|&sup| match self.get(self.base(sup)) {
                TypeData::Class(c) => self.supers_known(*c, depth + 1),
                TypeData::Builtin(_) => true,
                _ => false,
            })
    }

    /// Whether values of type `x` are never null, nor of another such
    /// type: a final builtin type's or a function type's.
    fn is_definite(&self, x: TypeId) -> bool {
        self.is_final(x) || matches!(self.get(x), TypeData::Fn { .. })
    }

    /// Whether `x` is one of core's non-generic classes or interfaces
    /// beyond the final types ([`CORE_NOMINAL`]).
    fn is_core_nominal(&self, x: TypeId) -> bool {
        self.builtin_name(x)
            .is_some_and(|n| CORE_NOMINAL.contains(&n))
    }

    /// Whether `x` is a class's or interface's type, a type parameter, or
    /// a generic type given arguments.
    fn is_class_like(&self, x: TypeId) -> bool {
        matches!(
            self.get(x),
            TypeData::Class(_) | TypeData::Param(_) | TypeData::Applied { .. }
        )
    }

    /// Whether builtin type `sub` extends builtin type `sup`, both bases
    /// of generic types, and so takes the same type arguments, or is
    /// a final type that's a `MapKey` of itself, as core declares them.
    fn builtin_extends(&self, sub: TypeId, sup: TypeId) -> bool {
        let (TypeData::Builtin(sub), TypeData::Builtin(sup)) = (self.get(sub), self.get(sup))
        else {
            return false;
        };
        let (sub, sup) = (TYPES[*sub as usize], TYPES[*sup as usize]);
        BUILTIN_SUPERTYPES.contains(&(sub, sup))
    }

    /// A type as Kotlin Temper writes it in messages: `Int32`, `String?`,
    /// `fn (Int32): Boolean`, and `fn: Int32` with no parameters, but one
    /// that throws as Temper's syntax does, `fn (Int32): Int32 throws
    /// Bubble` ([`show_result`]). Classes are named by `class_name`.
    pub fn display(&self, t: TypeId, class_name: &dyn Fn(SymbolId) -> String) -> String {
        match self.get(t) {
            TypeData::Unknown => "?".into(),
            TypeData::Never => "Never".into(),
            TypeData::NeverOf(t) => format!("Never<{}>", self.display(*t, class_name)),
            TypeData::Null => "Null".into(),
            TypeData::Builtin(i) => TYPES[*i as usize].into(),
            TypeData::Class(symbol) | TypeData::Param(symbol) => class_name(*symbol),
            TypeData::Applied { base, args } => {
                let args: Vec<String> = args.iter().map(|&a| self.display(a, class_name)).collect();
                format!("{}<{}>", self.display(*base, class_name), args.join(", "))
            }
            TypeData::Nullable(inner) => match self.get(*inner) {
                TypeData::Fn { .. } => format!("({})?", self.display(*inner, class_name)),
                _ => format!("{}?", self.display(*inner, class_name)),
            },
            TypeData::Fn {
                params,
                ret,
                throws,
            } => {
                let params: Vec<String> = (params.iter())
                    .map(|p| match p.optional {
                        true => format!("_?: {}", self.display(p.ty, class_name)),
                        false => self.display(p.ty, class_name),
                    })
                    .collect();
                let ret = show_result(self.display(*ret, class_name), *throws);
                if params.is_empty() {
                    format!("fn: {ret}")
                } else {
                    format!("fn ({}): {ret}", params.join(", "))
                }
            }
        }
    }
}

/// A result type, `ret`, as Temper's syntax writes it: `Int32 throws
/// Bubble` if it may bubble. Every message uses this, where Kotlin Temper
/// writes its internal `Result<Int32, Bubble>` or `(Int32 | Bubble)`.
pub fn show_result(ret: String, throws: bool) -> String {
    match throws {
        true => format!("{ret} throws Bubble"),
        false => ret,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A class's type, with the classes it extends and theirs.
    fn class(types: &mut Types, symbol: u32, ancestors: Vec<SymbolId>) -> TypeId {
        let info = ClassInfo {
            ancestors,
            ..ClassInfo::default()
        };
        types.set_class(SymbolId(symbol), info);
        types.intern(TypeData::Class(SymbolId(symbol)))
    }

    #[test]
    fn subtypes() {
        let mut types = Types::new();
        let int_or_null = types.nullable(INT32);
        assert_eq!(types.is_subtype(INT32, int_or_null), Some(true));
        assert_eq!(types.is_subtype(NULL, int_or_null), Some(true));
        assert_eq!(types.is_subtype(int_or_null, INT32), Some(false));
        assert_eq!(types.is_subtype(INT32, FLOAT64), Some(false));
        assert_eq!(types.is_subtype(NEVER, STRING), Some(true));
        assert_eq!(types.is_subtype(STRING, ANY_VALUE), Some(true));
        assert_eq!(types.is_subtype(NULL, ANY_VALUE), Some(false));
        // AnyValue may hold an Int32.
        assert_eq!(types.is_subtype(ANY_VALUE, INT32), None);
        assert_eq!(types.is_subtype(UNKNOWN, STRING), None);
        // A class or interface is only what it extends.
        let interface = class(&mut types, 2, Vec::new());
        let other = class(&mut types, 3, Vec::new());
        let extends = class(&mut types, 4, vec![SymbolId(2)]);
        assert_eq!(types.is_subtype(extends, interface), Some(true));
        assert_eq!(types.is_subtype(extends, other), Some(false));
        assert_eq!(types.is_subtype(interface, extends), Some(false));
        assert_eq!(types.is_subtype(interface, other), Some(false));
        assert_eq!(types.is_subtype(extends, STRING), Some(false));
        assert_eq!(types.is_subtype(STRING, interface), Some(false));
        assert_eq!(types.is_subtype(NULL, interface), Some(false));
        let f = types.intern(TypeData::Fn {
            params: vec![Param {
                ty: INT32,
                optional: false,
            }],
            ret: BOOLEAN,
            throws: false,
        });
        assert_eq!(types.is_subtype(f, INT32), Some(false));
        assert_eq!(types.display(f, &|_| String::new()), "fn (Int32): Boolean");
        let g = types.intern(TypeData::Fn {
            params: Vec::new(),
            ret: INT32,
            throws: true,
        });
        assert_eq!(
            types.display(g, &|_| String::new()),
            "fn: Int32 throws Bubble"
        );
        assert_eq!(types.join(NULL, INT32), int_or_null);
        assert_eq!(types.join(NEVER, STRING), STRING);
        assert_eq!(types.join(INT32, STRING), UNKNOWN);
    }

    #[test]
    fn function_subtypes() {
        /// `fn (param): ret`, or `fn (_?: param): ret` if `optional`.
        fn fn_type(types: &mut Types, param: TypeId, optional: bool, ret: TypeId) -> TypeId {
            fn_throws(types, param, optional, ret, false)
        }
        /// The same, ending `throws Bubble` if `throws`.
        fn fn_throws(
            types: &mut Types,
            param: TypeId,
            optional: bool,
            ret: TypeId,
            throws: bool,
        ) -> TypeId {
            let params = vec![Param {
                ty: param,
                optional,
            }];
            types.intern(TypeData::Fn {
                params,
                ret,
                throws,
            })
        }
        let mut types = Types::new();
        let types = &mut types;
        let int_to_bool = fn_type(types, INT32, false, BOOLEAN);
        let string_to_bool = fn_type(types, STRING, false, BOOLEAN);
        let int_to_value = fn_type(types, INT32, false, ANY_VALUE);
        let int_to_string = fn_type(types, INT32, false, STRING);
        let maybe_int_to_bool = fn_type(types, INT32, true, BOOLEAN);
        let int_or_null = types.nullable(INT32);
        let int_or_null_to_bool = fn_type(types, int_or_null, false, BOOLEAN);
        let int_to_bool_throws = fn_throws(types, INT32, false, BOOLEAN, true);
        let no_params = types.intern(TypeData::Fn {
            params: Vec::new(),
            ret: BOOLEAN,
            throws: false,
        });
        assert_eq!(types.is_subtype(string_to_bool, int_to_bool), Some(false));
        assert_eq!(types.is_subtype(int_to_string, int_to_bool), Some(false));
        assert_eq!(
            types.is_subtype(int_to_bool_throws, int_to_bool),
            Some(false)
        );
        assert_eq!(types.is_subtype(int_to_bool, no_params), Some(false));
        assert_eq!(
            types.is_subtype(int_to_bool, int_or_null_to_bool),
            Some(false)
        );
        // What only Kotlin Temper's variance accepts isn't reported.
        assert_eq!(types.is_subtype(int_to_bool, int_to_value), None);
        assert_eq!(types.is_subtype(int_or_null_to_bool, int_to_bool), None);
        assert_eq!(types.is_subtype(int_to_bool, int_to_bool_throws), None);
        assert_eq!(types.is_subtype(maybe_int_to_bool, int_to_bool), None);
        assert_eq!(
            types.display(maybe_int_to_bool, &|_| String::new()),
            "fn (_?: Int32): Boolean"
        );
    }

    #[test]
    fn builtin_supertypes_are_cores() {
        let core = include_str!("../../../lib/core/core.temper");
        for (sub, sup) in BUILTIN_SUPERTYPES {
            let declared = core.lines().any(|line| {
                let line = line.trim_start_matches("export ");
                let declares = ["class ", "interface ", "sealed interface "]
                    .iter()
                    .any(|kind| line.starts_with(&format!("{kind}{sub}")));
                let extends = [format!(" extends {sup}<"), format!(" extends {sup} ")];
                declares && extends.iter().any(|e| line.contains(e.as_str()))
            });
            assert!(declared, "{sub} extends {sup}");
        }
    }

    #[test]
    fn builtin_generics_are_cores() {
        let core = include_str!("../../../lib/core/core.temper");
        let mut classes = Vec::new();
        let mut no_downcast = Vec::new();
        let mut variant = Vec::new();
        let mut previous = "";
        for line in core.lines() {
            let decl = line.trim_start_matches("export ");
            let decl = decl.trim_start_matches("/*sealed*/ ");
            let decl = decl.trim_start_matches("sealed ");
            let (is_class, rest) =
                match (decl.strip_prefix("class "), decl.strip_prefix("interface ")) {
                    (Some(rest), _) => (true, rest),
                    (_, Some(rest)) => (false, rest),
                    _ => {
                        previous = line;
                        continue;
                    }
                };
            let Some((name, params)) = rest.split_once('<') else {
                previous = line;
                continue;
            };
            if !line.starts_with("export") || !name.chars().all(char::is_alphanumeric) {
                previous = line;
                continue;
            }
            // The parameters, split at commas outside nested `<…>`.
            let (mut depth, mut pieces) = (0, vec![String::new()]);
            for c in params.chars() {
                match c {
                    '>' if depth == 0 => break,
                    ',' if depth == 0 => pieces.push(String::new()),
                    _ => {
                        depth += i32::from(c == '<') - i32::from(c == '>');
                        pieces.last_mut().unwrap().push(c);
                    }
                }
            }
            for (index, piece) in pieces.iter().enumerate() {
                let piece = piece.trim_start();
                if piece.starts_with("@out ") || piece.starts_with("@in ") {
                    variant.push((name, index));
                }
            }
            if is_class {
                classes.push(name);
                if previous.starts_with("@mayDowncastTo(false)") {
                    no_downcast.push(name);
                }
            }
            previous = line;
        }
        let sorted = |mut v: Vec<&'static str>| {
            v.sort();
            v
        };
        assert_eq!(sorted(classes), sorted(BUILTIN_GENERIC_CLASSES.to_vec()));
        assert_eq!(
            sorted(no_downcast),
            sorted(BUILTIN_GENERIC_CLASSES[..NO_DOWNCAST].to_vec())
        );
        variant.sort();
        assert_eq!(variant, BUILTIN_VARIANT_PARAMS.to_vec());
    }

    #[test]
    fn generic_subtypes() {
        let mut types = Types::new();
        let list = TYPES.iter().position(|&t| t == "List").unwrap();
        let list = types.builtin(list as u8);
        let applied = |types: &mut Types, arg| {
            types.intern(TypeData::Applied {
                base: list,
                args: vec![arg],
            })
        };
        let ints = applied(&mut types, INT32);
        let strings = applied(&mut types, STRING);
        let values = applied(&mut types, ANY_VALUE);
        assert_eq!(types.is_subtype(ints, strings), Some(false));
        // `List<Int32>` may be a `List<AnyValue>`, as `List` is `@out`
        // in Kotlin Temper.
        assert_eq!(types.is_subtype(ints, values), None);
        assert_eq!(types.is_subtype(ints, ANY_VALUE), Some(true));
        assert_eq!(types.is_subtype(ints, STRING), Some(false));
        assert_eq!(types.display(ints, &|_| String::new()), "List<Int32>");
        // A type parameter is a subtype of its bound only.
        let t = types.param(SymbolId(5));
        types.set_bound(SymbolId(5), ANY_VALUE);
        let u = types.param(SymbolId(6));
        assert_eq!(types.is_subtype(t, ANY_VALUE), Some(true));
        assert_eq!(types.is_subtype(t, INT32), Some(false));
        assert_eq!(types.is_subtype(INT32, t), Some(false));
        assert_eq!(types.is_subtype(NULL, t), Some(false));
        // `u`'s bound isn't known.
        assert_eq!(types.is_subtype(u, INT32), None);
        assert_eq!(types.is_subtype(u, t), None);
        let ts = applied(&mut types, t);
        assert_eq!(types.is_subtype(ts, ints), Some(false));
        // Builtin generic types extend those core says they do.
        let index = |name: &str| TYPES.iter().position(|&t| t == name).unwrap() as u8;
        let (listed, builder, map_key) = (
            types.builtin(index("Listed")),
            types.builtin(index("ListBuilder")),
            types.builtin(index("MapKey")),
        );
        let listed_strings = types.intern(TypeData::Applied {
            base: listed,
            args: vec![STRING],
        });
        let listed_ints = types.intern(TypeData::Applied {
            base: listed,
            args: vec![INT32],
        });
        let built_ints = types.intern(TypeData::Applied {
            base: builder,
            args: vec![INT32],
        });
        assert_eq!(types.is_subtype(ints, listed_ints), Some(true));
        assert_eq!(types.is_subtype(ints, listed_strings), Some(false));
        assert_eq!(types.is_subtype(built_ints, ints), Some(false));
        assert_eq!(types.is_subtype(listed_ints, ints), Some(false));
        // `Listed`'s parameter isn't `@out`, so, as in Kotlin Temper, a
        // `List<Int32>` is no `Listed<Int32?>`, though it may be a
        // `List<Int32?>`.
        let int_or_null = types.nullable(INT32);
        let (listed_maybe, maybe) = (
            types.intern(TypeData::Applied {
                base: listed,
                args: vec![int_or_null],
            }),
            applied(&mut types, int_or_null),
        );
        assert_eq!(types.is_subtype(ints, listed_maybe), Some(false));
        assert_eq!(types.is_subtype(ints, maybe), None);
        let string_key = types.intern(TypeData::Applied {
            base: map_key,
            args: vec![STRING],
        });
        let int_key = types.intern(TypeData::Applied {
            base: map_key,
            args: vec![INT32],
        });
        assert_eq!(types.is_subtype(STRING, string_key), Some(true));
        assert_eq!(types.is_subtype(STRING, int_key), Some(false));
        assert_eq!(types.is_subtype(FLOAT64, int_key), Some(false));
        assert_eq!(
            types.erase(ts, &[SymbolId(5)]),
            types.intern(TypeData::Applied {
                base: list,
                args: vec![UNKNOWN]
            })
        );
    }

    #[test]
    fn users_generic_supertypes() {
        // `interface Sup<T>`, `class Sub<U> extends Sup<U>`, `interface
        // Sup2<V, W> extends Sup<W>`, `class Sub2<X> extends Sup2<X,
        // String>`, and `class Sub4 extends Sup<String>`.
        let mut types = Types::new();
        let [sup, t, sub, u, sup2, v, w, sub2, x, sub4] = [10, 11, 12, 13, 14, 15, 16, 17, 18, 19];
        let param = |types: &mut Types, symbol: u32| {
            let p = types.param(SymbolId(symbol));
            types.set_bound(SymbolId(symbol), ANY_VALUE);
            p
        };
        let (pt, pu, pw, px) = (
            param(&mut types, t),
            param(&mut types, u),
            param(&mut types, w),
            param(&mut types, x),
        );
        let base = |types: &mut Types, symbol: u32| types.intern(TypeData::Class(SymbolId(symbol)));
        let applied = |types: &mut Types, symbol: u32, args: Vec<TypeId>| {
            let base = base(types, symbol);
            types.intern(TypeData::Applied { base, args })
        };
        let declare = |types: &mut Types, symbol: u32, formals: &[u32], supers: Vec<TypeId>| {
            let ancestors = match symbol {
                12 | 14 | 19 => vec![SymbolId(sup)],
                17 => vec![SymbolId(sup2), SymbolId(sup)],
                _ => Vec::new(),
            };
            let formals = formals.iter().map(|&f| SymbolId(f)).collect();
            let info = ClassInfo {
                ancestors,
                formals,
                supers,
                ..ClassInfo::default()
            };
            types.set_class(SymbolId(symbol), info);
        };
        declare(&mut types, sup, &[t], Vec::new());
        let sup_u = applied(&mut types, sup, vec![pu]);
        declare(&mut types, sub, &[u], vec![sup_u]);
        let sup_w = applied(&mut types, sup, vec![pw]);
        declare(&mut types, sup2, &[v, w], vec![sup_w]);
        let sup2_x_string = applied(&mut types, sup2, vec![px, STRING]);
        declare(&mut types, sub2, &[x], vec![sup2_x_string]);
        let sup_string = applied(&mut types, sup, vec![STRING]);
        declare(&mut types, sub4, &[], vec![sup_string]);
        let sub_string = applied(&mut types, sub, vec![STRING]);
        let sub_int = applied(&mut types, sub, vec![INT32]);
        let sup_int = applied(&mut types, sup, vec![INT32]);
        let sup_t = applied(&mut types, sup, vec![pt]);
        let sub2_t = applied(&mut types, sub2, vec![pt]);
        let sub4_type = base(&mut types, sub4);
        assert_eq!(types.is_subtype(sub_string, sup_string), Some(true));
        assert_eq!(types.is_subtype(sub_string, sup_int), Some(false));
        assert_eq!(types.is_subtype(sub_int, sup_int), Some(true));
        assert_eq!(types.is_subtype(sub4_type, sup_string), Some(true));
        assert_eq!(types.is_subtype(sub4_type, sup_int), Some(false));
        assert_eq!(types.is_subtype(sub4_type, sub_string), Some(false));
        assert_eq!(types.is_subtype(sup_string, sub_string), Some(false));
        assert_eq!(types.is_subtype(sup_string, sub4_type), Some(false));
        // Through `Sup2<T, String>`, a `Sub2<T>` is a `Sup<String>`.
        assert_eq!(types.is_subtype(sub2_t, sup_string), Some(true));
        assert_eq!(types.is_subtype(sub2_t, sup_t), Some(false));
        let (sup_base, sub_base) = (base(&mut types, sup), base(&mut types, sub));
        assert_eq!(types.supertype_as(sub2_t, sup_base), Some(sup_string));
        assert_eq!(types.supertype_as(sup_string, sub_base), None);
        // `T` is solved from a `Sub4` where a `Sup<T>` is wanted.
        let (mut actuals, mut conflicts) = (vec![UNKNOWN], vec![false]);
        types.unify(
            sup_t,
            sub4_type,
            &[SymbolId(t)],
            &mut actuals,
            &mut conflicts,
        );
        assert_eq!(actuals, [STRING]);
    }
}
