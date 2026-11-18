//! What each call, operator and member read resolves to, recorded once
//! checking is done (notes/typing.md, "Resolutions"), so that tmpl's
//! translation reads it rather than resolving again: a declaration (the
//! `eq` a class's `@auto("==")` makes among them, since the frontend
//! generates it as a member), a builtin by its key, a null check, or a
//! call of a function value.
//!
//! Builtin keys are named as temper-tmpl's key table names them
//! (`crates/temper-tmpl/src/builtins.rs`) where they overlap: an
//! operator's is its operands' type and its name, as `Int32.add`,
//! `String.eq`, `Float64.pow` and `Boolean.not`; a builtin type's member's
//! is the type declaring it in core and its name, as `String.toInt32`,
//! `Listed.isEmpty` or `List.get`; then `String.cat` for templates and
//! `console.log`. Other builtin functions have their own names, as
//! `panic` and `list`.
//!
//! `==` (and `!=`, the equality it negates) resolves to a class's or
//! interface's method marked `@operator("==")`, its own or the nearest
//! inherited one, as the `eq` its `@auto("==")` makes; to a builtin
//! type's own key, with either side maybe nullable; and with `null` on
//! either side, to a null check (notes/tmpl/nodes.md, "Equality"). One
//! with nothing to call but identity, as for a type parameter's value,
//! is noted as such ([`super::Typing::identity_eq`]) and left unresolved.

use super::core_members::core_class;
use super::signatures::{OperatorMethod, variants};
use crate::builtins::{Builtin, Constant, Function, TYPES};
use crate::core::{self, MEMBER_STATIC, ROLE_CONSTRUCTOR, ROLE_MASK};
use crate::scope::SymbolKind;
use crate::settle::Settle;
use crate::types::{
    ANY_VALUE, BOOLEAN, FLOAT64, INT32, INT64, NULL, STRING, TypeData, TypeId, builtin_supertype,
};
use temper_syntax::tree::{NodeId, NodeKind};

/// What a call, operator or member read resolves to.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Resolved {
    /// A declaration, by its `Let`: a function, a method (an `@operator`
    /// method for an operator), a field, getter or constructor; for `new
    /// C(…)` of a class without a constructor of its own, the class.
    Decl(NodeId),
    /// A builtin, by its key, as `Int32.add` or `console.log`.
    Builtin(String),
    /// `a == null` or `a != null`, either way round: a null check.
    NullCheck,
    /// A call of a function value, as `f(1)` for a parameter `f`, which
    /// the callee's type describes.
    Value,
}

/// A final builtin type's name, as builtin keys start with it.
fn final_name(t: TypeId) -> Option<&'static str> {
    Some(match t {
        BOOLEAN => "Boolean",
        INT32 => "Int32",
        INT64 => "Int64",
        FLOAT64 => "Float64",
        STRING => "String",
        _ => return None,
    })
}

/// An operator's name in its builtin keys, by its arity.
fn operator_name(f: Function, arity: usize) -> Option<&'static str> {
    use Function::*;
    Some(match (f, arity) {
        (Sub, 1) => "neg",
        (Add, 2) => "add",
        (Sub, 2) => "sub",
        (Mul, 2) => "mul",
        (Div, 2) => "div",
        (Mod, 2) => "mod",
        (Pow, 2) => "pow",
        (Lt, 2) => "lt",
        (Le, 2) => "le",
        (Gt, 2) => "gt",
        (Ge, 2) => "ge",
        (Cmp, 2) => "cmp",
        (Eq | Ne, 2) => "eq",
        (Not, 1) => "not",
        (BitAnd, 2) => "bitAnd",
        (BitOr, 2) => "bitOr",
        (BitXor, 2) => "bitXor",
        (BitNot, 1) => "bitNot",
        (Shl, 2) => "shl",
        (Shr, 2) => "shr",
        (UShr, 2) => "ushr",
        _ => return None,
    })
}

/// The key of another builtin function, which tmpl doesn't call by key
/// yet.
fn function_key(f: Function) -> Option<&'static str> {
    use Function::*;
    Some(match f {
        Cat => "String.cat",
        Panic => "panic",
        List => "list",
        Ignore => "ignore",
        Char => "char",
        Raw => "raw",
        Empty => "empty",
        DoPure => "doPure",
        DoneResult => "doneResult",
        Async => "async",
        Rgx => "rgx",
        _ => return None,
    })
}

impl Settle {
    /// Resolves a call, operator or member read, recording what it
    /// resolves to, if typing knows.
    pub(super) fn resolve_call(&mut self, call: NodeId) {
        let children = self.lib.tree.children(call).to_vec();
        let Some((&callee, args)) = children.split_first() else {
            return;
        };
        let resolved = match self.scopes.resolved_builtin(callee) {
            Some(Builtin::Function(f)) => self.resolve_builtin(call, f, args),
            Some(_) => None,
            None => self.resolve_callee(callee),
        };
        if let Some(resolved) = resolved {
            self.typing.resolved.insert(call, resolved);
        }
    }

    fn resolve_builtin(&mut self, call: NodeId, f: Function, args: &[NodeId]) -> Option<Resolved> {
        match f {
            Function::Dot => {
                let (object, key) = self.dot_parts(call)?;
                if let Ok(Some((m, ..))) = self.dot_member_node(object, key, false, false) {
                    return Some(Resolved::Decl(m));
                }
                let name = self.lib.names.get(self.lib.symbol(key)?).to_string();
                self.core_key(object, &name, false)
            }
            Function::Index => {
                let object = *args.first()?;
                let t = self.known_type(object);
                if let Some(receiver) = self.receiver(t) {
                    let get = self.lib.names.find("get")?;
                    let m = self.member_named(receiver.class, get, false, true)?;
                    return Some(Resolved::Decl(m));
                }
                self.core_key(object, "get", true)
            }
            Function::New => {
                let made = *args.first()?;
                let name = match self.lib.tree.kind(made) {
                    NodeKind::Call => *self.lib.tree.children(made).get(1)?,
                    _ => made,
                };
                // A builtin type's, maybe named through an alias.
                let t = self.known_type(call);
                let types = &self.typing.types;
                if let &TypeData::Builtin(i) = types.get(types.base(t)) {
                    let name = TYPES[i as usize];
                    return Some(Resolved::Builtin(format!("{name}.constructor")));
                }
                // A generic class's, maybe named through an alias.
                let class = match self.type_decl(self.scopes.resolved(name)?) {
                    Some(class) => class,
                    None => match *types.get(types.base(t)) {
                        TypeData::Class(symbol) => self.type_decl(symbol)?,
                        _ => return None,
                    },
                };
                let tree = &self.lib.tree;
                let constructor = (core::class_parts(tree, class).2.iter())
                    .copied()
                    .find(|&m| {
                        tree.data(m) & ROLE_MASK == ROLE_CONSTRUCTOR
                            && tree.data(m) & MEMBER_STATIC == 0
                    });
                Some(Resolved::Decl(constructor.unwrap_or(class)))
            }
            Function::Eq | Function::Ne if args.len() == 2 => self.resolve_eq(call, args),
            _ => match operator_name(f, args.len()) {
                Some(name) => self.resolve_operator(f, name, args),
                None => function_key(f).map(|key| Resolved::Builtin(key.into())),
            },
        }
    }

    /// A builtin type's member `object.name`, by its key, as core declares
    /// it, a method first if `callee`.
    fn core_key(&mut self, object: NodeId, name: &str, callee: bool) -> Option<Resolved> {
        let found = self.core_member_found(object, name, callee, false).ok()??;
        let key = format!("{}.{}", found.type_name, found.member.name);
        Some(Resolved::Builtin(key))
    }

    /// What a call's callee calls, if it's not a builtin.
    fn resolve_callee(&mut self, callee: NodeId) -> Option<Resolved> {
        self.resolve_callee_in(callee, 0)
    }

    /// [`Settle::resolve_callee`], `depth` aliases in.
    fn resolve_callee_in(&mut self, callee: NodeId, depth: u32) -> Option<Resolved> {
        let tree = &self.lib.tree;
        match tree.kind(callee) {
            NodeKind::RightName => {
                let b = self.scopes.resolved(callee)?;
                let symbol = *self.scopes.symbol(b);
                match symbol.kind {
                    SymbolKind::Member => {
                        let (m, ..) = self.bare_member_node(b, true)?;
                        return Some(Resolved::Decl(m));
                    }
                    SymbolKind::TopLevel | SymbolKind::Local => {
                        let decl = symbol.decl?;
                        let init = core::let_parts(tree, decl).2;
                        match init.map(|i| (i, tree.kind(i))) {
                            Some((_, NodeKind::Fn)) if !symbol.mutable => {
                                return Some(Resolved::Decl(decl));
                            }
                            // Another name for a function, as an import
                            // makes. Another name for a value (`let f =
                            // field`) is called as the value it holds,
                            // as its own type here gives it: a null check
                            // may have narrowed it, and what it was read
                            // from may have changed since.
                            Some((other, NodeKind::RightName)) if !symbol.mutable && depth < 8 => {
                                let found = self.resolve_callee_in(other, depth + 1);
                                if let Some(Resolved::Decl(d)) = found {
                                    let tree = &self.lib.tree;
                                    let init = (tree.kind(d) == NodeKind::Let)
                                        .then(|| core::let_parts(tree, d).2)
                                        .flatten();
                                    if init.is_some_and(|i| tree.kind(i) == NodeKind::Fn) {
                                        return found;
                                    }
                                }
                            }
                            _ => {}
                        }
                    }
                    _ => {}
                }
            }
            NodeKind::Call => {
                if let Some((object, key)) = self.dot_parts(callee) {
                    if let Ok(Some((m, ..))) = self.called_member_node(callee, false) {
                        return Some(Resolved::Decl(m));
                    }
                    let is_console = self.scopes.resolved_builtin(object)
                        == Some(Builtin::Value(Constant::Console));
                    let name = self.lib.names.get(self.lib.symbol(key)?).to_string();
                    if is_console {
                        return (name == "log").then(|| Resolved::Builtin("console.log".into()));
                    }
                    return self.core_key(object, &name, true);
                }
                // `f<T>(…)`: what `f` calls.
                if let [angle, inner, ..] = *tree.children(callee) {
                    if self.scopes.resolved_builtin(angle)
                        == Some(Builtin::Function(Function::Angle))
                    {
                        return self.resolve_callee_in(inner, depth);
                    }
                }
            }
            _ => {}
        }
        let t = self.known_callee_type(callee);
        matches!(self.typing.types.get(t), TypeData::Fn { .. }).then_some(Resolved::Value)
    }

    /// The value's type, not null.
    fn operand(&mut self, node: NodeId) -> TypeId {
        let t = self.known_type(node);
        match *self.typing.types.get(t) {
            TypeData::Nullable(inner) => inner,
            _ => t,
        }
    }

    /// `a == b` or `a != b`: a null check, a builtin type's equality, or
    /// a class's `@operator("==")` method, such as the `eq` that
    /// `@auto("==")` makes.
    fn resolve_eq(&mut self, call: NodeId, args: &[NodeId]) -> Option<Resolved> {
        let (a, b) = (self.known_type(args[0]), self.known_type(args[1]));
        if a == NULL || b == NULL {
            return Some(Resolved::NullCheck);
        }
        let (a, b) = (self.operand(args[0]), self.operand(args[1]));
        if let (Some(name), true) = (final_name(a), a == b) {
            return Some(Resolved::Builtin(format!("{name}.eq")));
        }
        if let Some(key) = self.core_operator(a, "==") {
            return Some(Resolved::Builtin(key));
        }
        if let Some(OperatorMethod::Method { method, .. }) = self.class_operator(Function::Eq, a) {
            return Some(Resolved::Decl(method));
        }
        let why = self.identity_reason(a, b);
        if let Some(why) = why {
            self.typing.identity_eqs.insert(call, why);
        }
        None
    }

    /// The key of a builtin type's method marked `@operator(op)` in core,
    /// its own or the nearest inherited one, as `StringIndexOption.eq`
    /// for a `StringIndex`'s `==`. Not a final type's, which have their
    /// own keys.
    fn core_operator(&self, t: TypeId, op: &str) -> Option<String> {
        let types = &self.typing.types;
        let &TypeData::Builtin(i) = types.get(types.base(t)) else {
            return None;
        };
        if final_name(t).is_some() {
            return None;
        }
        let mut name = TYPES[i as usize];
        loop {
            let class = core_class(name);
            let found = (class.members.iter()).find(|m| m.operator.as_deref() == Some(op));
            if let Some(m) = found {
                return Some(format!("{name}.{}", m.name));
            }
            name = builtin_supertype(name)?;
        }
    }

    /// Why `==` of values of types `a` and `b` would compare identities,
    /// if it would: there's no equality to call.
    fn identity_reason(&mut self, a: TypeId, b: TypeId) -> Option<&'static str> {
        let types = &self.typing.types;
        let why = |t: TypeId| match types.get(types.base(t)) {
            TypeData::Param(_) => Some("type parameter"),
            _ if t == ANY_VALUE => Some("AnyValue"),
            TypeData::Fn { .. } => Some("function"),
            TypeData::Builtin(_) if final_name(t).is_none() => Some("builtin type"),
            TypeData::Class(_) => Some("class or interface without =="),
            TypeData::Unknown => Some("unknown type"),
            _ => None,
        };
        // Mismatched final builtin types are an error already.
        if final_name(a).is_some() && final_name(b).is_some() {
            return None;
        }
        match why(a) {
            Some(w) => Some(w),
            None => why(b),
        }
    }

    /// A builtin operator other than `==`: its operands' type's key, or a
    /// class's `@operator("<=>")` method for a comparison.
    fn resolve_operator(&mut self, f: Function, name: &str, args: &[NodeId]) -> Option<Resolved> {
        let types: Vec<TypeId> = args.iter().map(|&a| self.known_type(a)).collect();
        if matches!(
            f,
            Function::Cmp | Function::Lt | Function::Le | Function::Gt | Function::Ge
        ) {
            if let Some(OperatorMethod::Method { method, .. }) = self.class_operator(f, types[0]) {
                return Some(Resolved::Decl(method));
            }
        }
        if matches!(
            f,
            Function::Cmp | Function::Lt | Function::Le | Function::Gt | Function::Ge
        ) {
            let a = self.operand(args[0]);
            if let Some(key) = self.core_operator(a, "<=>") {
                return Some(Resolved::Builtin(key));
            }
        }
        let variant = variants(f, args.len())
            .iter()
            .find(|sig| sig.params == types.as_slice())?;
        let ty = final_name(variant.params[0])?;
        Some(Resolved::Builtin(format!("{ty}.{name}")))
    }
}
