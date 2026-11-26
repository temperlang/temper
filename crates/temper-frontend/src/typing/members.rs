//! Classes' types, the fourth slice of milestone 4: values of a class or
//! interface (`this`, `new C(…)`), its members' types, read as `c.x`,
//! called as `c.add(1)` or a static `C.make()`, or named bare in the
//! class, and what assigning a member or calling a constructor wants.
//!
//! A generic class's values are typed too (notes/typing.md, "Generic
//! types"): `new Box(1)` is solved as a generic call
//! ([`super::generic_calls`]), and a `Box<Int32>`'s members have `Int32`
//! for `T`, as do those it inherits from a type it extends with `T`, as
//! `Sup<T>` (notes/typing.md, "Users' supertypes").

use super::core_members::{CoreMember, CoreMemberKind, CoreType, core_class};
use super::signatures::OperatorMethod;
use crate::builtins::{Builtin, Function, TYPES};
use crate::core::{
    self, CLASS_FUN, CLASS_INTERFACE, MEMBER_PRIVATE, MEMBER_STATIC, ROLE_CONSTRUCTOR, ROLE_GETTER,
    ROLE_MASK, ROLE_METHOD, ROLE_PARAM, ROLE_SETTER, class_parts,
};
use crate::library::{Level, Message};
use crate::scope::{ScopeKind, SymbolId, SymbolKind};
use crate::settle::{Fact, Settle};
use crate::types::{
    ANY_VALUE, ClassInfo, Param, TypeData, TypeId, Types, UNKNOWN, builtin_supertype,
};
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind};

/// A value's class, with what its type parameters are bound to.
pub(super) struct Receiver {
    pub class: NodeId,
    formals: Vec<SymbolId>,
    actuals: Vec<TypeId>,
}

impl Settle {
    /// The type of a class's or interface's values, unless it's generic.
    /// One named as a builtin type is core's declaration of it, as in
    /// Kotlin Temper's `core.temper`, so it's that type.
    pub(super) fn class_type(&mut self, class: NodeId) -> TypeId {
        let tree = &self.lib.tree;
        if tree.kind(class) != NodeKind::Class || !core::type_params(tree, class).is_empty() {
            return UNKNOWN;
        }
        if let Some(i) = TYPES.iter().position(|&t| t == self.member_text(class)) {
            return self.typing.types.builtin(i as u8);
        }
        match self.class_symbol(class) {
            Some(symbol) => self.typing.types.intern(TypeData::Class(symbol)),
            None => UNKNOWN,
        }
    }

    /// The symbol a class's or interface's declaration declares, once
    /// [`Types`] knows what subtyping needs of it: its supertypes, and
    /// theirs, with the types it extends as written, as `Sup<T>` for
    /// `class Sub<T> extends Sup<T>`.
    pub(super) fn class_symbol(&mut self, class: NodeId) -> Option<SymbolId> {
        let symbol = self.scopes.resolved(class_parts(&self.lib.tree, class).0)?;
        if self.typing.types.knows_class(symbol) {
            return Some(symbol);
        }
        // Known first, as the types it extends may name it, as in `class
        // C extends Equatable<C>`.
        self.typing.types.set_class(symbol, ClassInfo::default());
        let ancestors = (self.ancestors(class).into_iter())
            .filter_map(|c| self.scopes.resolved(class_parts(&self.lib.tree, c).0))
            .collect();
        let formals = self.own_formals(class);
        let tree = &self.lib.tree;
        let variant = (core::type_params(tree, class).iter())
            .map(|&p| tree.data(p) & core::TYPE_PARAM_VARIANT != 0)
            .collect();
        let supers = class_parts(&self.lib.tree, class).1.to_vec();
        let supers = supers.into_iter().map(|s| self.eval_type(s)).collect();
        let info = ClassInfo {
            ancestors,
            formals,
            variant,
            supers,
            fun_sig: None,
        };
        self.typing.types.set_class(symbol, info.clone());
        // Once the rest is known, as `apply`'s signature may name the
        // interface.
        let fun_sig = self.fun_interface_sig(class);
        if fun_sig.is_some() {
            (self.typing.types).set_class(symbol, ClassInfo { fun_sig, ..info });
        }
        Some(symbol)
    }

    /// A `@fun` interface's `apply`'s signature, naming the interface's
    /// type parameters, as `fn (A): B` for `@fun interface Mapper<A,
    /// B>(a: A): B;`. `None` for other classes and interfaces.
    fn fun_interface_sig(&mut self, class: NodeId) -> Option<TypeId> {
        if self.lib.tree.data(class) & CLASS_FUN == 0 {
            return None;
        }
        let apply = self.lib.names.intern("apply");
        let [m] = *self.members_named(class, apply, false) else {
            return None;
        };
        let (_, _, f) = core::let_parts(&self.lib.tree, m);
        let f = f.filter(|_| self.lib.tree.data(m) & ROLE_MASK == ROLE_METHOD)?;
        Some(self.method_type(f))
    }

    /// The type of `this`, if `symbol` is a class's synthetic `this`: in
    /// a generic class, as `Box<T>`, given its own type parameters.
    pub(super) fn this_type(&mut self, symbol: SymbolId) -> TypeId {
        let scope = self.scopes.get(self.scopes.symbol(symbol).owner);
        if scope.kind != ScopeKind::Class || scope.names.first().map(|&(_, b)| b) != Some(symbol) {
            return UNKNOWN;
        }
        self.class_or_own_type(scope.node)
    }

    /// A class's type as its body sees it: given its own type parameters
    /// if it's generic, as `Box<T>`.
    fn class_or_own_type(&mut self, class: NodeId) -> TypeId {
        let formals = self.own_formals(class);
        if formals.is_empty() {
            return self.class_type(class);
        }
        let args = formals.iter().map(|&f| self.type_param_type(f)).collect();
        self.generic_class_type(class, args)
    }

    /// A generic class's or interface's type given arguments, as
    /// `Box<Int32>`, unless it's unknown: core's builtin types aren't
    /// typed this way.
    pub(super) fn generic_class_type(&mut self, class: NodeId, args: Vec<TypeId>) -> TypeId {
        let tree = &self.lib.tree;
        let builtin = TYPES.contains(&self.member_text(class));
        if builtin || core::type_params(tree, class).len() != args.len() {
            return UNKNOWN;
        }
        match self.class_symbol(class) {
            Some(symbol) => {
                let base = self.typing.types.intern(TypeData::Class(symbol));
                self.typing.types.intern(TypeData::Applied { base, args })
            }
            None => UNKNOWN,
        }
    }

    /// The class of a value of type `t`, with what its type parameters
    /// are bound to, as `T` to `Int32` for a `Box<Int32>`; for a type
    /// parameter's value, its bound's, as `I` for `T extends I`.
    pub(super) fn receiver(&mut self, t: TypeId) -> Option<Receiver> {
        let mut t = t;
        for _ in 0..8 {
            match self.typing.types.get(t) {
                TypeData::Param(_) => t = self.typing.types.upper(t),
                _ => break,
            }
        }
        let (symbol, actuals) = match self.typing.types.get(t) {
            &TypeData::Class(symbol) => (symbol, Vec::new()),
            TypeData::Applied { base, args } => match *self.typing.types.get(*base) {
                TypeData::Class(symbol) => (symbol, args.clone()),
                _ => return None,
            },
            _ => return None,
        };
        let class = self.scopes.symbol(symbol).decl?;
        let formals = self.own_formals(class);
        (formals.len() == actuals.len()).then_some(Receiver {
            class,
            formals,
            actuals,
        })
    }

    /// A class's receiver as its static members, or a bare name in its
    /// body, see it: its type parameters as they're written, or unknown
    /// if `statics`, as static members can't use them.
    pub(super) fn own_receiver(&mut self, class: NodeId, statics: bool) -> Receiver {
        let formals = self.own_formals(class);
        let actuals = match statics {
            true => vec![UNKNOWN; formals.len()],
            false => formals.iter().map(|&f| self.type_param_type(f)).collect(),
        };
        Receiver {
            class,
            formals,
            actuals,
        }
    }

    /// A member's type when read through `receiver`: with the receiver's
    /// type arguments put in for its class's type parameters. One
    /// inherited from a generic supertype has the arguments the receiver's
    /// class gives that supertype put in, as `String` for `T` in a member
    /// of `Holder<T>` read through a `class StrHolder extends
    /// Holder<String>`.
    pub(super) fn member_type_in(&mut self, m: NodeId, receiver: &Receiver) -> TypeId {
        let t = self.member_type(m);
        let owner = self.member_owner(m);
        self.receiver_view(t, owner, receiver)
    }

    /// A type in a member declared by `owner`, seen through `receiver`,
    /// as [`Settle::member_type_in`] sees it.
    pub(super) fn receiver_view(
        &mut self,
        t: TypeId,
        owner: Option<NodeId>,
        receiver: &Receiver,
    ) -> TypeId {
        match owner {
            Some(owner) if owner == receiver.class => {
                (self.typing.types).substitute(t, &receiver.formals, &receiver.actuals)
            }
            Some(owner) => {
                let formals = self.own_formals(owner);
                let seen = match (self.receiver_type(receiver), self.class_symbol(owner)) {
                    (Some(this), Some(symbol)) => {
                        let base = self.typing.types.intern(TypeData::Class(symbol));
                        self.typing.types.supertype_as(this, base)
                    }
                    _ => None,
                };
                let types = &mut self.typing.types;
                match seen.map(|seen| types.args(seen).to_vec()) {
                    Some(actuals) if actuals.len() == formals.len() => {
                        types.substitute(t, &formals, &actuals)
                    }
                    _ => types.erase(t, &formals),
                }
            }
            None => UNKNOWN,
        }
    }

    /// The type of a receiver's values, as `Box<Int32>`.
    fn receiver_type(&mut self, receiver: &Receiver) -> Option<TypeId> {
        let symbol = self.class_symbol(receiver.class)?;
        let base = self.typing.types.intern(TypeData::Class(symbol));
        Some(match receiver.actuals.is_empty() {
            true => base,
            false => (self.typing.types).intern(TypeData::Applied {
                base,
                args: receiver.actuals.clone(),
            }),
        })
    }

    /// The class or interface declaring a member.
    fn member_owner(&self, m: NodeId) -> Option<NodeId> {
        let b = self.scopes.resolved(*self.lib.tree.children(m).first()?)?;
        Some(self.scopes.get(self.scopes.symbol(b).owner).node)
    }

    /// The class a call `new C(…)` makes, if it's one typing knows and
    /// isn't generic, whose calls are solved ([`super::generic_calls`]).
    pub(super) fn new_class(&self, args: &[NodeId]) -> Option<NodeId> {
        let class = self.type_decl(self.scopes.resolved(*args.first()?)?)?;
        let tree = &self.lib.tree;
        (tree.data(class) & CLASS_INTERFACE == 0 && core::type_params(tree, class).is_empty())
            .then_some(class)
    }

    /// The type `new T(…)` makes for a builtin type `T` core declares a
    /// constructor for, as `StringBuilder`, or given its type arguments,
    /// as `ListBuilder<Int>`. Not yet one solved from its arguments or the
    /// type wanted, as `new Pair(1, "a")` or `new ListBuilder()`.
    pub(super) fn builtin_new_type(&mut self, args: &[NodeId]) -> TypeId {
        let Some(&made) = args.first() else {
            return UNKNOWN;
        };
        let tree = &self.lib.tree;
        let (base, given) = match tree.kind(made) {
            NodeKind::RightName => (made, 0),
            NodeKind::Call => match tree.children(made) {
                [angle, base, type_args @ ..]
                    if self.scopes.resolved_builtin(*angle)
                        == Some(Builtin::Function(Function::Angle)) =>
                {
                    (*base, type_args.len())
                }
                _ => return UNKNOWN,
            },
            _ => return UNKNOWN,
        };
        let Some(Builtin::Type(i)) = self.scopes.resolved_builtin(base) else {
            return UNKNOWN;
        };
        let class = core_class(TYPES[i as usize]);
        let constructs = (class.members.iter()).any(|m| m.name == "constructor");
        if !constructs || class.type_params != given {
            return UNKNOWN;
        }
        self.eval_type(made)
    }

    /// The parameters of a builtin type's constructor where `new` gives it
    /// its type arguments, as `entries: List<Pair<Int32, Int32>>` for `new
    /// Map<Int, Int>(…)`, so a list literal argument, as `[]`, takes its
    /// type from them, as Kotlin Temper types it.
    pub(super) fn builtin_new_params(&mut self, args: &[NodeId]) -> Option<Vec<Param>> {
        let made = self.builtin_new_type(args);
        let types = &self.typing.types;
        let &TypeData::Builtin(i) = types.get(types.base(made)) else {
            return None;
        };
        let type_args = types.args(made).to_vec();
        let class = core_class(TYPES[i as usize]);
        if type_args.len() != class.type_params || class.type_params == 0 {
            return None;
        }
        let constructor =
            (class.members.iter()).find(|m| m.name == "constructor" && !m.is_static)?;
        let types = &mut self.typing.types;
        let sig = core_signature(types, &constructor.kind, &type_args, &[]);
        match types.get(sig) {
            TypeData::Fn { params, .. } => Some(params.clone()),
            _ => None,
        }
    }

    /// The parameters of a class's constructor.
    pub(super) fn constructor_type(&mut self, class: NodeId) -> Vec<Param> {
        let params = core::constructor_params(&self.lib.tree, class);
        params.into_iter().map(|p| self.member_param(p)).collect()
    }

    /// A parameter of a method or constructor: Kotlin Temper types one
    /// with a default as nullable, as null means the default.
    pub(super) fn member_param(&mut self, param: NodeId) -> Param {
        let (_, ty, default) = core::let_parts(&self.lib.tree, param);
        let mut ty = ty.map_or(UNKNOWN, |t| self.eval_type(t));
        if default.is_some() {
            ty = self.typing.types.nullable(ty);
        }
        Param {
            ty,
            optional: default.is_some(),
        }
    }

    /// The type of `object.key` when read: a field's or getter's, or a
    /// method's signature.
    pub(super) fn dot_type(
        &mut self,
        object: NodeId,
        key: NodeId,
        forced: bool,
    ) -> Result<TypeId, Fact> {
        Ok(match self.dot_member(object, key, false, forced)? {
            Some(t) => t,
            None => (self.core_member_type(object, key, false, forced)?).unwrap_or(UNKNOWN),
        })
    }

    /// The type of a bare name for a member, in its class, when read.
    pub(super) fn bare_member_type(&mut self, symbol: SymbolId) -> TypeId {
        self.bare_member(symbol, false).unwrap_or(UNKNOWN)
    }

    /// The type of what a call calls, its callee's, or if that's a
    /// member, as `c.add` in `c.add(1)` or `add` in its class, of the
    /// member that names, a method before a field; for a `@fun`
    /// interface's value, the function type it stands for.
    pub(super) fn callee_type(&mut self, callee: NodeId, forced: bool) -> Result<TypeId, Fact> {
        let t = match self.called_member(callee, forced)? {
            Some(t) => t,
            None => match self.dot_parts(callee) {
                Some((object, key)) => match self.core_member_type(object, key, true, forced)? {
                    Some(t) => t,
                    None => self.expr_type(callee, forced)?,
                },
                None => self.expr_type(callee, forced)?,
            },
        };
        Ok(self.typing.types.as_fn(t).unwrap_or(t))
    }

    /// `object` and `key` of a callee `object.key`.
    pub(super) fn dot_parts(&self, callee: NodeId) -> Option<(NodeId, NodeId)> {
        match (self.lib.tree.kind(callee), self.lib.tree.children(callee)) {
            (NodeKind::Call, &[dot, object, key])
                if self.scopes.resolved_builtin(dot) == Some(Builtin::Function(Function::Dot)) =>
            {
                Some((object, key))
            }
            _ => None,
        }
    }

    /// The builtin type a name names, as `String`.
    pub(super) fn builtin_type_named(&self, node: NodeId) -> Option<u8> {
        match self.scopes.resolved_builtin(node) {
            Some(Builtin::Type(i)) if self.lib.tree.kind(node) == NodeKind::RightName => Some(i),
            _ => None,
        }
    }

    /// Whether a node is `T.key` for a builtin type `T`, a static member.
    pub(super) fn names_builtin_static(&self, node: NodeId) -> bool {
        self.dot_parts(node)
            .is_some_and(|(object, _)| self.builtin_type_named(object).is_some())
    }

    /// The type of `object.key` for a member of a builtin type, as core
    /// declares it ([`super::core_members`]): an instance's, or a static
    /// one's if `object` names the type, as `String.fromCodePoint`. A
    /// method first if `callee`, else a field or getter first.
    fn core_member_type(
        &mut self,
        object: NodeId,
        key: NodeId,
        callee: bool,
        forced: bool,
    ) -> Result<Option<TypeId>, Fact> {
        let Some(name) = self.lib.symbol(key) else {
            return Ok(None);
        };
        let name = self.lib.names.get(name).to_string();
        self.core_member_named(object, &name, callee, forced)
    }

    /// The type of what `object[…]` calls, `object.get` as Kotlin Temper
    /// desugars it, for a value of a class or a builtin type.
    pub(super) fn index_callee_type(
        &mut self,
        object: NodeId,
        forced: bool,
    ) -> Result<TypeId, Fact> {
        let t = self.expr_type(object, forced)?;
        if let Some(receiver) = self.receiver(t) {
            let Some(get) = self.lib.names.find("get") else {
                return Ok(UNKNOWN);
            };
            return Ok(match self.member_named(receiver.class, get, false, true) {
                Some(m) => self.member_type_in(m, &receiver),
                None => UNKNOWN,
            });
        }
        Ok((self.core_member_named(object, "get", true, forced)?).unwrap_or(UNKNOWN))
    }

    /// The type of `object.name` for a member of a builtin type, as
    /// [`Settle::core_member_found`] finds it, with a generic type's
    /// arguments put in for its type parameters, as `Int32` for `T` in a
    /// `List<Int32>`'s `get`, and a method's own type parameters unknown.
    pub(super) fn core_member_named(
        &mut self,
        object: NodeId,
        name: &str,
        callee: bool,
        forced: bool,
    ) -> Result<Option<TypeId>, Fact> {
        let Some(found) = self.core_member_found(object, name, callee, forced)? else {
            return Ok(None);
        };
        let types = &mut self.typing.types;
        Ok(Some(match &found.member.kind {
            CoreMemberKind::Value(t) => core_type(types, t, &found.args, &[]),
            kind => core_signature(types, kind, &found.args, &[]),
        }))
    }

    /// The member of a builtin type `object.name` names, as
    /// [`Settle::core_member_type`] picks it. An instance's member may be
    /// inherited from the builtin generic type it extends, as `List`'s
    /// `isEmpty` from `Listed`.
    pub(super) fn core_member_found(
        &mut self,
        object: NodeId,
        name: &str,
        callee: bool,
        forced: bool,
    ) -> Result<Option<CoreFound>, Fact> {
        let (type_index, args, this) = match self.builtin_type_named(object) {
            Some(i) => (i, Vec::new(), None),
            None => {
                let t = self.expr_type(object, forced)?;
                let types = &self.typing.types;
                match types.get(types.base(t)) {
                    &TypeData::Builtin(i) => (i, types.args(t).to_vec(), Some(t)),
                    _ => return Ok(None),
                }
            }
        };
        let statics = this.is_none();
        let mut type_name = match TYPES[type_index as usize] {
            "Int" => "Int32",
            other => other,
        };
        let named = |m: &CoreMember| m.name == name && m.is_static == statics;
        let is_method = |m: &CoreMember| matches!(m.kind, CoreMemberKind::Method { .. });
        let (class, index) = loop {
            let class = core_class(type_name);
            let members = &class.members;
            let picked = (members.iter())
                .position(|m| named(m) && is_method(m) == callee)
                .or_else(|| members.iter().position(named));
            if let Some(index) = picked {
                break (class, index);
            }
            match builtin_supertype(type_name) {
                Some(sup) if !statics => type_name = sup,
                _ => return Ok(None),
            }
        };
        // A static member can't use the type's parameters, nor can a
        // generic type given the wrong number of arguments.
        let args = match args.len() == class.type_params {
            true => args,
            false => vec![UNKNOWN; class.type_params],
        };
        Ok(Some(CoreFound {
            type_name,
            index,
            member: &class.members[index],
            args,
            this,
        }))
    }

    /// The generic method of a builtin type a callee names, as `ls.map`
    /// in `ls.map(f)` for core's `Listed.map<O>(transform: fn (T): O):
    /// List<O>`. Not a static method.
    pub(super) fn core_generic_method(
        &mut self,
        callee: NodeId,
        forced: bool,
    ) -> Result<Option<GenericMethod>, Fact> {
        let Some((object, key)) = self.dot_parts(callee) else {
            return Ok(None);
        };
        let Some(name) = self.lib.symbol(key) else {
            return Ok(None);
        };
        let name = self.lib.names.get(name).to_string();
        let Some(found) = self.core_member_found(object, &name, true, forced)? else {
            return Ok(None);
        };
        let (CoreMemberKind::Method { type_params, .. }, Some(this)) =
            (&found.member.kind, found.this)
        else {
            return Ok(None);
        };
        if type_params.is_empty() {
            return Ok(None);
        }
        let key = (found.type_name, found.index);
        let formals = match self.typing.core_formals.get(&key) {
            Some(formals) => formals.clone(),
            None => {
                let formals: Vec<SymbolId> = (type_params.iter())
                    .map(|p| {
                        let name = self.lib.names.intern(p);
                        let symbol = self.scopes.unnamed_type_param(name);
                        self.typing.types.set_bound(symbol, ANY_VALUE);
                        symbol
                    })
                    .collect();
                self.typing.core_formals.insert(key, formals.clone());
                formals
            }
        };
        let types = &mut self.typing.types;
        let own: Vec<TypeId> = formals.iter().map(|&f| types.param(f)).collect();
        let sig = core_signature(types, &found.member.kind, &found.args, &own);
        let base = (TYPES.iter().position(|&t| t == found.type_name))
            .map_or(UNKNOWN, |i| types.builtin(i as u8));
        let declarer = match found.args.is_empty() {
            true => base,
            false => types.intern(TypeData::Applied {
                base,
                args: found.args,
            }),
        };
        Ok(Some(GenericMethod {
            formals,
            sig,
            this: Some((declarer, this)),
        }))
    }

    /// The constructor of a builtin generic type that `new T(…)` calls,
    /// as `new Pair(1, "a")` or `new ListBuilder<Int>()`, maybe through
    /// an alias, as a generic
    /// function of the type's own type parameters, `<K, V>(K, V) ->
    /// Pair<K, V>`, with the call's type arguments. Their bounds are as
    /// core declares them, as `MapKey<K>` for `Map`'s `K`, so a call is
    /// checked against them, and unknown where it declares none: `T` in
    /// `Deque<T>` may be nullable, as in `new Deque<D?>()`.
    pub(super) fn core_generic_new(
        &mut self,
        args: &[NodeId],
    ) -> Option<(GenericMethod, Vec<NodeId>)> {
        let &(mut made) = args.first()?;
        let tree = &self.lib.tree;
        // Through an alias, as `TestResult` in `let TestResult = Pair<…>`.
        for _ in 0..8 {
            let alias = (self.scopes.resolved(made).map(|b| self.scopes.symbol(b)))
                .filter(|s| s.kind == SymbolKind::TopLevel && !s.mutable)
                .and_then(|s| s.decl)
                .filter(|&d| {
                    tree.kind(made) == NodeKind::RightName && tree.kind(d) == NodeKind::Let
                })
                .and_then(|d| core::let_parts(tree, d).2)
                .filter(|&i| matches!(tree.kind(i), NodeKind::RightName | NodeKind::Call));
            match alias {
                Some(init) => made = init,
                None => break,
            }
        }
        let (base, type_args) = match tree.kind(made) {
            NodeKind::RightName => (made, Vec::new()),
            NodeKind::Call => match tree.children(made) {
                [angle, base, type_args @ ..]
                    if self.scopes.resolved_builtin(*angle)
                        == Some(Builtin::Function(Function::Angle)) =>
                {
                    (*base, type_args.to_vec())
                }
                _ => return None,
            },
            _ => return None,
        };
        let Some(Builtin::Type(i)) = self.scopes.resolved_builtin(base) else {
            return None;
        };
        let type_name = TYPES[i as usize];
        let class = core_class(type_name);
        let constructor =
            (class.members.iter()).find(|m| m.name == "constructor" && !m.is_static)?;
        if class.type_params == 0 {
            return None;
        }
        let key = (type_name, usize::MAX);
        let formals = match self.typing.core_formals.get(&key) {
            Some(formals) => formals.clone(),
            None => {
                let formals: Vec<SymbolId> = (class.type_param_names.iter())
                    .map(|p| {
                        let name = self.lib.names.intern(p);
                        self.scopes.unnamed_type_param(name)
                    })
                    .collect();
                // A bound may name the type's own type parameters, as
                // `MapKey<K>` does.
                let types = &mut self.typing.types;
                let own: Vec<TypeId> = formals.iter().map(|&f| types.param(f)).collect();
                for (&formal, bound) in formals.iter().zip(&class.type_param_bounds) {
                    let bound = core_type(types, bound, &own, &[]);
                    types.set_bound(formal, bound);
                }
                self.typing.core_formals.insert(key, formals.clone());
                formals
            }
        };
        let types = &mut self.typing.types;
        let own: Vec<TypeId> = formals.iter().map(|&f| types.param(f)).collect();
        let sig = core_signature(types, &constructor.kind, &own, &[]);
        let TypeData::Fn { params, throws, .. } = types.get(sig).clone() else {
            return None;
        };
        let base = types.builtin(i);
        let ret = types.intern(TypeData::Applied { base, args: own });
        let sig = types.intern(TypeData::Fn {
            params,
            ret,
            throws,
        });
        let method = GenericMethod {
            formals,
            sig,
            this: None,
        };
        Some((method, type_args))
    }

    /// The type of the member a callee names, if it names one.
    fn called_member(&mut self, callee: NodeId, forced: bool) -> Result<Option<TypeId>, Fact> {
        Ok((self.called_member_node(callee, forced)?)
            .map(|(m, receiver, _)| self.member_type_in(m, &receiver)))
    }

    /// The generic method a callee names, as `o.first` in `o.first(xs)`
    /// for `first<T>(items: List<T>): T`.
    pub(super) fn generic_method(
        &mut self,
        callee: NodeId,
        forced: bool,
    ) -> Result<Option<GenericMethod>, Fact> {
        let Some((m, receiver, this)) = self.called_member_node(callee, forced)? else {
            return Ok(None);
        };
        let tree = &self.lib.tree;
        let f = core::let_parts(tree, m).2.filter(|&f| {
            tree.data(m) & ROLE_MASK == ROLE_METHOD
                && tree.kind(f) == NodeKind::Fn
                && !core::type_params(tree, f).is_empty()
        });
        let Some(f) = f else {
            return Ok(None);
        };
        let sig = self.method_signature(f);
        let owner = self.member_owner(m);
        let sig = self.receiver_view(sig, owner, &receiver);
        Ok(Some(GenericMethod {
            formals: self.own_formals(f),
            sig,
            this: this.map(|t| (t, t)),
        }))
    }

    /// The member a callee names, if it names one, with its receiver and,
    /// for an instance's member, the receiver's type.
    pub(super) fn called_member_node(
        &mut self,
        callee: NodeId,
        forced: bool,
    ) -> Result<Option<(NodeId, Receiver, Option<TypeId>)>, Fact> {
        let tree = &self.lib.tree;
        match (tree.kind(callee), tree.children(callee)) {
            (NodeKind::RightName, _) => Ok(self
                .scopes
                .resolved(callee)
                .filter(|&b| self.scopes.symbol(b).kind == SymbolKind::Member)
                .and_then(|b| self.bare_member_node(b, true))),
            (NodeKind::Call, &[dot, object, key])
                if self.scopes.resolved_builtin(dot) == Some(Builtin::Function(Function::Dot)) =>
            {
                self.dot_member_node(object, key, true, forced)
            }
            _ => Ok(None),
        }
    }

    /// The type of the member `object.key` names, as
    /// [`Settle::member_named`] picks it: a static one if `object` names a
    /// class, as `C.make`.
    fn dot_member(
        &mut self,
        object: NodeId,
        key: NodeId,
        callee: bool,
        forced: bool,
    ) -> Result<Option<TypeId>, Fact> {
        let found = self.dot_member_node(object, key, callee, forced)?;
        if let Some((m, ..)) = &found {
            self.wait_for_field(*m, forced)?;
        }
        Ok(found.map(|(m, receiver, _)| self.member_type_in(m, &receiver)))
    }

    /// The member `object.key` names, as [`Settle::dot_member`] picks it,
    /// with its receiver and, for an instance's member, `object`'s type.
    pub(super) fn dot_member_node(
        &mut self,
        object: NodeId,
        key: NodeId,
        callee: bool,
        forced: bool,
    ) -> Result<Option<(NodeId, Receiver, Option<TypeId>)>, Fact> {
        let Some(name) = self.lib.symbol(key) else {
            return Ok(None);
        };
        if self.lib.tree.kind(object) == NodeKind::RightName {
            if let Some(class) = (self.scopes.resolved(object)).and_then(|b| self.type_decl(b)) {
                let receiver = self.own_receiver(class, true);
                return Ok(
                    (self.member_named(class, name, true, callee)).map(|m| (m, receiver, None))
                );
            }
        }
        let t = self.expr_type(object, forced)?;
        let Some(receiver) = self.receiver(t) else {
            return Ok(None);
        };
        Ok(
            (self.member_named(receiver.class, name, false, callee))
                .map(|m| (m, receiver, Some(t))),
        )
    }

    /// The type of the member a bare name in a class means.
    fn bare_member(&mut self, symbol: SymbolId, callee: bool) -> Option<TypeId> {
        let (m, receiver, _) = self.bare_member_node(symbol, callee)?;
        Some(self.member_type_in(m, &receiver))
    }

    /// The member a bare name in a class means, as [`Settle::bare_member`]
    /// picks it, with its receiver and, for an instance's member, the
    /// type of `this`.
    pub(super) fn bare_member_node(
        &mut self,
        symbol: SymbolId,
        callee: bool,
    ) -> Option<(NodeId, Receiver, Option<TypeId>)> {
        let symbol = *self.scopes.symbol(symbol);
        let class = self.scopes.get(symbol.owner).node;
        let is_static = self.lib.tree.data(symbol.decl?) & MEMBER_STATIC != 0;
        // A `@noProperty` input is itself, not a member by its name.
        let m = match symbol.input {
            true => symbol.decl?,
            false => self.member_named(class, symbol.name, is_static, callee)?,
        };
        let receiver = self.own_receiver(class, is_static);
        let this = (!is_static).then(|| self.class_or_own_type(class));
        Some((m, receiver, this))
    }

    /// The member a bare name in a class reads, if typing knows it.
    pub(super) fn bare_member_decl(&mut self, symbol: SymbolId) -> Option<NodeId> {
        self.bare_member_node(symbol, false).map(|(m, ..)| m)
    }

    /// The member `object.key`, `node`, reads, if it's such a read and
    /// typing knows the member.
    pub(super) fn dot_member_decl(
        &mut self,
        node: NodeId,
        forced: bool,
    ) -> Result<Option<NodeId>, Fact> {
        let Some((object, key)) = self.dot_parts(node) else {
            return Ok(None);
        };
        Ok((self.dot_member_node(object, key, false, forced)?).map(|(m, ..)| m))
    }

    /// Reports calling a method that a class inherits more than once
    /// ([`Settle::tied_methods`]), as Kotlin Temper does, where its
    /// solver can't pick one: if more than one fits the arguments, as far
    /// as typing knows them. Returns whether the callee is such a method.
    pub(super) fn check_tie(&mut self, call: NodeId, callee: NodeId, args: &[NodeId]) -> bool {
        let tree = &self.lib.tree;
        let (NodeKind::Call, &[dot, object, key]) = (tree.kind(callee), tree.children(callee))
        else {
            return false;
        };
        let is_dot = self.scopes.resolved_builtin(dot) == Some(Builtin::Function(Function::Dot));
        let this = self.known_type(object);
        let (true, Some(name), &TypeData::Class(symbol)) =
            (is_dot, self.lib.symbol(key), self.typing.types.get(this))
        else {
            return false;
        };
        let Some(class) = self.scopes.symbol(symbol).decl else {
            return false;
        };
        let tied = self.tied_methods(class, name);
        if tied.len() < 2 {
            return false;
        }
        let arg_types: Vec<TypeId> = args.iter().map(|&a| self.known_type(a)).collect();
        let sigs: Vec<TypeId> = tied.iter().map(|&(_, m)| self.member_type(m)).collect();
        let types = &mut self.typing.types;
        if !arg_types.iter().chain(&sigs).all(|&t| types.is_known(t)) {
            return true;
        }
        let mut fits = |sig: TypeId| match types.get(sig).clone() {
            TypeData::Fn { params, .. } => {
                let required = params.iter().filter(|p| !p.optional).count();
                (required..=params.len()).contains(&args.len())
                    && (arg_types.iter().zip(&params))
                        .all(|(&a, p)| types.is_subtype(a, p.ty) != Some(false))
            }
            _ => false,
        };
        if sigs.iter().filter(|&&sig| fits(sig)).count() < 2 {
            return true;
        }
        let mut inputs = vec![self.show(this)];
        inputs.extend(arg_types.iter().map(|&t| self.show(t)));
        let among: Vec<String> = (tied.iter().zip(&sigs))
            .map(|(&(owner, _), &sig)| {
                let mut params = vec![self.member_text(owner).to_string()];
                let TypeData::Fn {
                    params: ps, ret, ..
                } = self.typing.types.get(sig)
                else {
                    unreachable!("checked by fits");
                };
                params.extend(ps.iter().map(|p| self.show(p.ty)));
                format!("({}) -> {}", params.join(", "), self.show(*ret))
            })
            .collect();
        let message = Message::NoCalleeMatches(inputs.join(", "), among.join(", "));
        let span = self.lib.tree.span(call);
        self.lib.report(Level::Error, message, span);
        true
    }

    /// What assigning a member wants, by the assignment's target: a bare
    /// name in its class or `object.key`.
    pub(super) fn member_target_type(&mut self, target: NodeId) -> TypeId {
        let tree = &self.lib.tree;
        let children = tree.children(target).to_vec();
        let (members, receiver) = match (tree.kind(target), children.as_slice()) {
            (NodeKind::RightName, _) => {
                let Some(b) = self.scopes.resolved(target) else {
                    return UNKNOWN;
                };
                let symbol = *self.scopes.symbol(b);
                let Some(decl) = symbol.decl.filter(|_| symbol.kind == SymbolKind::Member) else {
                    return UNKNOWN;
                };
                let class = self.scopes.get(symbol.owner).node;
                let is_static = self.lib.tree.data(decl) & MEMBER_STATIC != 0;
                let receiver = self.own_receiver(class, is_static);
                (self.members_named(class, symbol.name, is_static), receiver)
            }
            (NodeKind::Call, &[dot, object, key])
                if self.scopes.resolved_builtin(dot) == Some(Builtin::Function(Function::Dot)) =>
            {
                let object_type = self.known_type(object);
                let (Some(name), Some(receiver)) =
                    (self.lib.symbol(key), self.receiver(object_type))
                else {
                    return UNKNOWN;
                };
                (self.members_named(receiver.class, name, false), receiver)
            }
            _ => return UNKNOWN,
        };
        let tree = &self.lib.tree;
        let field = members.iter().copied().find(|&m| {
            matches!(tree.data(m) & ROLE_MASK, 0 | ROLE_PARAM) && tree.data(m) & MEMBER_STATIC == 0
        });
        if let Some(field) = field {
            return self.member_type_in(field, &receiver);
        }
        let setter = members
            .iter()
            .find(|&&m| tree.data(m) & ROLE_MASK == ROLE_SETTER);
        let Some(&setter) = setter else {
            return UNKNOWN;
        };
        let param = (core::let_parts(tree, setter).2)
            .and_then(|f| core::fn_parts(tree, f).0.first().copied());
        match param {
            Some(param) => {
                let t = self.member_param(param).ty;
                let owner = self.member_owner(setter);
                self.receiver_view(t, owner, &receiver)
            }
            None => UNKNOWN,
        }
    }

    /// A class's member named `name`, a method if `callee`, else a field
    /// or getter, or else the other. None if there's more than one method
    /// of that name, which Kotlin Temper allows for overloads, or if the
    /// class inherits more than one ([`Settle::tied_methods`]).
    pub(super) fn member_named(
        &mut self,
        class: NodeId,
        name: NameId,
        statics: bool,
        callee: bool,
    ) -> Option<NodeId> {
        let members = self.members_named(class, name, statics);
        let tree = &self.lib.tree;
        let role = |m: NodeId| tree.data(m) & ROLE_MASK;
        let mut methods = members.iter().copied().filter(|&m| role(m) == ROLE_METHOD);
        let method = methods.next();
        if methods.next().is_some() {
            return None;
        }
        let read =
            (members.iter().copied()).find(|&m| matches!(role(m), 0 | ROLE_PARAM | ROLE_GETTER));
        let picked = if callee {
            method.or(read)
        } else {
            read.or(method)
        };
        if picked.is_some()
            && picked == method
            && !statics
            && self.tied_methods(class, name).len() > 1
        {
            return None;
        }
        picked
    }

    /// The methods named `name` that a class inherits, unless it declares
    /// one itself, as Kotlin Temper finds them (`FilterOutMaskedMembers.kt`):
    /// leaving out one that another overrides, and one with the same
    /// signature as one nearer the class (fewer `extends` away). More than
    /// one left is a tie, which Kotlin reports where it's called as "No
    /// callee matches".
    pub(super) fn tied_methods(&mut self, class: NodeId, name: NameId) -> Vec<(NodeId, NodeId)> {
        let tree = &self.lib.tree;
        let named = |m: NodeId| {
            tree.kind(m) == NodeKind::Let
                && self.lib.name(tree.children(m)[0]) == Some(name)
                && tree.data(m) & (MEMBER_STATIC | MEMBER_PRIVATE) == 0
        };
        if class_parts(tree, class).2.iter().any(|&m| named(m)) {
            return Vec::new();
        }
        // Supertypes breadth-first, with how far each is.
        let mut found = vec![(class, 0)];
        let mut next = 0;
        while next < found.len() {
            let (c, depth) = found[next];
            for sup in self.super_decls(c) {
                if !found.iter().any(|&(f, _)| f == sup) {
                    found.push((sup, depth + 1));
                }
            }
            next += 1;
        }
        let mut candidates: Vec<(NodeId, NodeId, u32)> = Vec::new();
        for &(owner, depth) in &found[1..] {
            let members = class_parts(&self.lib.tree, owner).2;
            let method = members
                .iter()
                .copied()
                .find(|&m| named(m) && self.lib.tree.data(m) & ROLE_MASK == ROLE_METHOD);
            if let Some(method) = method {
                candidates.push((owner, method, depth));
            }
        }
        let ancestors: Vec<Vec<NodeId>> = (candidates.iter())
            .map(|&(owner, ..)| self.ancestors(owner))
            .collect();
        let overridden = |i: usize| {
            (0..candidates.len()).any(|j| j != i && ancestors[j].contains(&candidates[i].0))
        };
        let candidates: Vec<(NodeId, NodeId, u32)> = (0..candidates.len())
            .filter(|&i| !overridden(i))
            .map(|i| candidates[i])
            .collect();
        let types: Vec<TypeId> = (candidates.iter())
            .map(|&(_, m, _)| self.member_type(m))
            .collect();
        let known = |t: TypeId| self.typing.types.is_known(t);
        let deeper_twin = |i: usize| {
            (0..candidates.len()).any(|j| {
                types[i] == types[j] && known(types[i]) && candidates[j].2 < candidates[i].2
            })
        };
        let mut tied: Vec<(NodeId, NodeId)> = (0..candidates.len())
            .filter(|&i| !deeper_twin(i))
            .map(|i| (candidates[i].0, candidates[i].1))
            .collect();
        // Kotlin lists them depth-first, in `extends` order.
        let mut order = Vec::new();
        self.depth_first(class, &mut order);
        tied.sort_by_key(|&(owner, _)| order.iter().position(|&c| c == owner));
        tied
    }

    /// A class and its supertypes, depth-first, each once.
    fn depth_first(&self, class: NodeId, out: &mut Vec<NodeId>) {
        if out.contains(&class) {
            return;
        }
        out.push(class);
        for sup in self.super_decls(class) {
            self.depth_first(sup, out);
        }
    }

    /// The method marked with `flag` ([`MEMBER_OPERATOR_EQ`] or
    /// [`core::MEMBER_OPERATOR_CMP`]) that an operator calls for a value of
    /// `class`: its own, or else the nearest inherited one, breadth-first,
    /// such as the `eq` that `@auto("==")` makes
    /// ([`Settle::make_auto_eq`](crate::settle::Settle)).
    pub(super) fn operator_method(&self, class: NodeId, flag: u32) -> OperatorMethod {
        let tree = &self.lib.tree;
        let ancestors = self.ancestors(class);
        let lineage = || std::iter::once(class).chain(ancestors.iter().copied());
        for owner in lineage() {
            let members = class_parts(tree, owner).2;
            let marked = members.iter().copied().find(|&m| {
                tree.data(m) & (flag | MEMBER_STATIC) == flag
                    && tree.data(m) & ROLE_MASK == ROLE_METHOD
            });
            if let Some(method) = marked {
                return OperatorMethod::Method { owner, method };
            }
        }
        let unknown_super =
            lineage().any(|c| class_parts(tree, c).1.len() != self.super_decls(c).len());
        if unknown_super {
            OperatorMethod::Unsure
        } else {
            OperatorMethod::Builtin
        }
    }

    /// Whether a string template can show a value of type `t`, not null:
    /// one of a builtin type or class with a `toString()` method, its own
    /// or inherited; a type parameter's by its bound. True where typing
    /// doesn't know, so only a known lack is reported.
    pub(crate) fn has_to_string(&mut self, t: TypeId) -> bool {
        let mut t = t;
        for _ in 0..8 {
            match *self.typing.types.get(t) {
                TypeData::Nullable(inner) => t = inner,
                TypeData::Param(_) => t = self.typing.types.upper(t),
                _ => break,
            }
        }
        let types = &self.typing.types;
        match *types.get(types.base(t)) {
            TypeData::Builtin(i) => {
                let mut name = match TYPES[i as usize] {
                    "Int" => "Int32",
                    other => other,
                };
                if matches!(name, "String" | "Boolean" | "Int32" | "Int64" | "Float64") {
                    return true;
                }
                loop {
                    let shown = (core_class(name).members.iter()).any(|m| {
                        m.name == "toString"
                            && !m.is_static
                            && matches!(m.kind, CoreMemberKind::Method { .. })
                    });
                    if shown {
                        return true;
                    }
                    match builtin_supertype(name) {
                        Some(sup) => name = sup,
                        None => return false,
                    }
                }
            }
            TypeData::Class(symbol) => {
                let Some(class) = self.scopes.symbol(symbol).decl else {
                    return true;
                };
                let Some(name) = self.lib.names.find("toString") else {
                    return false;
                };
                let tree = &self.lib.tree;
                (self.members_named(class, name, false).iter())
                    .any(|&m| tree.data(m) & ROLE_MASK == ROLE_METHOD)
            }
            TypeData::Fn { .. } => false,
            _ => true,
        }
    }

    /// A class's members named `name`: its instances' own and inherited
    /// ones, or if `statics`, its own static ones. Not constructors, nor
    /// `@noProperty` inputs, which only initializers read, by name.
    pub(super) fn members_named(&self, class: NodeId, name: NameId, statics: bool) -> Vec<NodeId> {
        let Some(scope) = self.scopes.of_node(class) else {
            return Vec::new();
        };
        let tree = &self.lib.tree;
        (self.scopes.get(scope).names.iter())
            .filter(|&&(n, _)| n == name)
            .filter_map(|&(_, b)| self.scopes.symbol(b).decl)
            .filter(|&m| {
                tree.kind(m) == NodeKind::Let
                    && (tree.data(m) & MEMBER_STATIC != 0) == statics
                    && tree.data(m) & ROLE_MASK != ROLE_CONSTRUCTOR
                    && tree.data(m) & core::MEMBER_NO_PROPERTY == 0
            })
            .collect()
    }

    /// A member's type when read: a field's, a getter's result, or a
    /// method's signature.
    pub(super) fn member_type(&mut self, m: NodeId) -> TypeId {
        let tree = &self.lib.tree;
        let data = tree.data(m);
        let (_, ty, init) = core::let_parts(tree, m);
        match (data & ROLE_MASK, ty, init) {
            (0 | ROLE_PARAM | ROLE_GETTER, Some(ty), _) => self.eval_type(ty),
            // A field typed by its initializer, once inferred.
            (0, None, Some(_)) => self.typing.decls.get(&m).copied().unwrap_or(UNKNOWN),
            (ROLE_GETTER, None, Some(f)) => self.declared_result(f).unwrap_or(UNKNOWN),
            (ROLE_METHOD, None, Some(f)) => self.method_type(f),
            _ => UNKNOWN,
        }
    }

    /// A method's type, from its signature, with its own type parameters
    /// unknown, as references and unsolved calls see it.
    pub(super) fn method_type(&mut self, f: NodeId) -> TypeId {
        let t = self.method_signature(f);
        let formals = self.own_formals(f);
        self.typing.types.erase(t, &formals)
    }

    /// A method's signature, with its parameters as [`Settle::member_param`]
    /// types them, and its own type parameters as written.
    pub(super) fn method_signature(&mut self, f: NodeId) -> TypeId {
        let t = self.fn_signature(f);
        let TypeData::Fn { ret, throws, .. } = *self.typing.types.get(t) else {
            return t;
        };
        let params = core::fn_parts(&self.lib.tree, f).0;
        let params = (params.into_iter())
            .map(|p| match self.lib.tree.kind(p) {
                NodeKind::Let => self.member_param(p),
                _ => Param {
                    ty: UNKNOWN,
                    optional: false,
                },
            })
            .collect();
        self.typing.types.intern(TypeData::Fn {
            params,
            ret,
            throws,
        })
    }
}

/// A generic method a call names, as [`Settle::generic_method`] and
/// [`Settle::core_generic_method`] find it.
pub(super) struct GenericMethod {
    /// Its own type parameters.
    pub formals: Vec<SymbolId>,
    /// Its signature as the receiver sees it, with the receiver's type
    /// arguments put in and its own type parameters as written.
    pub sig: TypeId,
    /// For an instance's method, the receiver's type, which Kotlin Temper
    /// shows as its first parameter and argument: as the type declaring
    /// the method sees it, and as it is, which differ for an inherited
    /// builtin method, as `Listed<Int32>` and `List<Int32>` for `ls.map`.
    pub this: Option<(TypeId, TypeId)>,
}

/// A builtin type's member, as [`Settle::core_member_found`] finds it.
pub(super) struct CoreFound {
    /// The type declaring it, as `Listed` for `ls.isEmpty`.
    pub type_name: &'static str,
    /// Its index among that type's members.
    index: usize,
    pub member: &'static CoreMember,
    /// The receiver's type arguments, unknown for a static member.
    args: Vec<TypeId>,
    /// The receiver's type, for an instance's member.
    this: Option<TypeId>,
}

/// A builtin type's method's signature, with `args` put in for the type's
/// own type parameters and `own` for the method's, unknown where `own`
/// has none.
fn core_signature(
    types: &mut Types,
    kind: &CoreMemberKind,
    args: &[TypeId],
    own: &[TypeId],
) -> TypeId {
    let CoreMemberKind::Method {
        params,
        ret,
        throws,
        ..
    } = kind
    else {
        return UNKNOWN;
    };
    let params = (params.iter())
        .map(|(t, optional)| {
            let ty = core_type(types, t, args, own);
            Param {
                // Null means the default, as in Kotlin Temper.
                ty: if *optional { types.nullable(ty) } else { ty },
                optional: *optional,
            }
        })
        .collect();
    let ret = core_type(types, ret, args, own);
    types.intern(TypeData::Fn {
        params,
        ret,
        throws: *throws,
    })
}

/// A type in a builtin type's member's signature, with `args` put in for
/// the type's own type parameters and `own` for a method's, unknown where
/// `own` has none.
fn core_type(types: &mut Types, t: &CoreType, args: &[TypeId], own: &[TypeId]) -> TypeId {
    match t {
        CoreType::Unknown => UNKNOWN,
        CoreType::Builtin(i) => types.builtin(*i),
        CoreType::Param(i) => args.get(*i as usize).copied().unwrap_or(UNKNOWN),
        CoreType::Own(i) => own.get(*i as usize).copied().unwrap_or(UNKNOWN),
        CoreType::Applied(base, xs) => {
            let base = types.builtin(*base);
            let args = xs.iter().map(|x| core_type(types, x, args, own)).collect();
            types.apply_builtin(base, args)
        }
        CoreType::Nullable(inner) => {
            let inner = core_type(types, inner, args, own);
            types.nullable(inner)
        }
        CoreType::Fn(params, ret) => {
            let params = (params.iter())
                .map(|p| Param {
                    ty: core_type(types, p, args, own),
                    optional: false,
                })
                .collect();
            let ret = core_type(types, ret, args, own);
            types.intern(TypeData::Fn {
                params,
                ret,
                throws: false,
            })
        }
    }
}
