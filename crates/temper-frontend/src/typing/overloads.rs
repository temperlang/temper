//! Overloads: a method marked `@overload("append")` answers to a call
//! `c.append(x)` as well as to its own name, `c.appendInt(x)`, as in
//! Kotlin Temper (notes/typing.md, "Overloads"). As Kotlin's `Typer`
//! does, only when `c`'s class has no member `append` itself; then the
//! marked methods whose parameters take the arguments are the
//! candidates, and the most specific of them, whose every parameter is a
//! subtype of each other's (`TypeContext2.overloadSpecificity`), is
//! called. The call's key then names that method, as Kotlin's
//! `SimplifyDotHelper` rewrites it, so the interpreter, tmpl, and every
//! backend see a call of `appendInt`, as Kotlin's generated code has it.

use super::members::Receiver;
use crate::builtins::{Builtin, Function};
use crate::core::{self, MEMBER_STATIC, ROLE_MASK, ROLE_METHOD};
use crate::settle::{Fact, Settle};
use crate::types::{Param, TypeData, TypeId};
use temper_syntax::ast::Value;
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind};

impl Settle {
    /// Points a call `object.name(…)` at the `@overload("name")` method
    /// that fits its arguments best, if `object`'s class has no member
    /// `name` and one fits best; otherwise leaves it as it is.
    pub(super) fn resolve_overload(&mut self, call: NodeId, forced: bool) -> Result<(), Fact> {
        if self.overloads.is_empty() {
            return Ok(());
        }
        let tree = &self.lib.tree;
        let Some((&callee, args)) = tree.children(call).split_first() else {
            return Ok(());
        };
        let (NodeKind::Call, &[dot, object, key]) = (tree.kind(callee), tree.children(callee))
        else {
            return Ok(());
        };
        let args = args.to_vec();
        let is_dot = self.scopes.resolved_builtin(dot) == Some(Builtin::Function(Function::Dot));
        let Some(name) = self.lib.symbol(key).filter(|_| is_dot) else {
            return Ok(());
        };
        if !self.overloads.values().any(|names| names.contains(&name)) {
            return Ok(());
        }
        // Not yet named arguments.
        if (args.iter()).any(|&a| matches!(self.lib.value(a), Some(Value::Symbol(_)))) {
            return Ok(());
        }
        let Some((receiver, statics)) = self.overload_receiver(object, forced)? else {
            return Ok(());
        };
        if !self.members_named(receiver.class, name, statics).is_empty() {
            return Ok(());
        }
        let mut arg_types = Vec::with_capacity(args.len());
        for &arg in &args {
            arg_types.push(self.expr_type(arg, forced)?);
        }
        let mut fitting: Vec<(NodeId, Vec<Param>)> = Vec::new();
        for m in self.overloads_named(receiver.class, name, statics) {
            let t = self.member_type_in(m, &receiver);
            let t = self.typing.types.as_fn(t).unwrap_or(t);
            let TypeData::Fn { params, .. } = self.typing.types.get(t) else {
                continue;
            };
            let params = params.clone();
            let required = params.iter().filter(|p| !p.optional).count();
            if arg_types.len() < required || arg_types.len() > params.len() {
                continue;
            }
            let fits = (arg_types.iter().zip(&params)).all(|(&a, p)| self.may_fit(a, p.ty));
            if fits {
                fitting.push((m, params));
            }
        }
        let n = arg_types.len();
        let best = (0..fitting.len()).find(|&i| {
            (0..fitting.len())
                .all(|j| i == j || self.more_specific(&fitting[i].1, &fitting[j].1, n))
        });
        let Some(best) = best else {
            return Ok(());
        };
        let m = fitting[best].0;
        let Some(method_name) = self.lib.name(self.lib.tree.children(m)[0]) else {
            return Ok(());
        };
        let span = self.lib.tree.span(key);
        let new_key = self.lib.add_value(Value::Symbol(method_name), span, key);
        self.set_children(callee, &[dot, object, new_key]);
        // Anything typed through the old name is typed again.
        self.typing.unset_expr(callee);
        Ok(())
    }

    /// The class whose members `object.name` reads, as
    /// [`Settle::dot_member_node`] finds it, and whether they're static.
    fn overload_receiver(
        &mut self,
        object: NodeId,
        forced: bool,
    ) -> Result<Option<(Receiver, bool)>, Fact> {
        if self.lib.tree.kind(object) == NodeKind::RightName {
            if let Some(class) = (self.scopes.resolved(object)).and_then(|b| self.type_decl(b)) {
                return Ok(Some((self.own_receiver(class, true), true)));
            }
        }
        let t = self.expr_type(object, forced)?;
        Ok(self.receiver(t).map(|r| (r, false)))
    }

    /// A class's methods, own and inherited, marked `@overload("name")`,
    /// or overriding one so marked, as an escaper's `applyString` does
    /// its interface's in the `content-tags` program: an override
    /// answers to the name too, as in Kotlin Temper.
    pub(super) fn overloads_named(
        &self,
        class: NodeId,
        name: NameId,
        statics: bool,
    ) -> Vec<NodeId> {
        let Some(scope) = self.scopes.of_node(class) else {
            return Vec::new();
        };
        let tree = &self.lib.tree;
        let marked = |m: NodeId| self.overloads.get(&m).is_some_and(|ns| ns.contains(&name));
        // The names of the methods marked in its supertypes.
        let mut inherited: Vec<NameId> = Vec::new();
        if !statics {
            for sup in self.ancestors(class) {
                for &m in core::class_parts(tree, sup).2 {
                    if marked(m) {
                        inherited.extend(self.lib.name(tree.children(m)[0]));
                    }
                }
            }
        }
        let mut found: Vec<NodeId> = (self.scopes.get(scope).names.iter())
            .filter_map(|&(n, b)| Some((n, self.scopes.symbol(b).decl?)))
            .filter(|&(n, m)| {
                tree.kind(m) == NodeKind::Let
                    && tree.data(m) & ROLE_MASK == ROLE_METHOD
                    && (tree.data(m) & MEMBER_STATIC != 0) == statics
                    && (marked(m) || inherited.contains(&n))
            })
            .map(|(_, m)| m)
            .collect();
        found.dedup();
        found
    }

    /// Whether an argument of type `a` may go to a parameter of type `p`:
    /// unless typing says it can't, or they're one generic type and an
    /// argument of `a`'s can't be `p`'s, as a `List<String?>` can't be a
    /// `List<String>`, where Kotlin Temper's `@out` on `List` decides
    /// ([`Settle::narrower`]).
    pub(super) fn may_fit(&mut self, a: TypeId, p: TypeId) -> bool {
        let types = &mut self.typing.types;
        if types.is_subtype(a, p) == Some(false) {
            return false;
        }
        let (TypeData::Applied { base: x, args: xs }, TypeData::Applied { base: y, args: ys }) =
            (types.get(a).clone(), types.get(p).clone())
        else {
            return true;
        };
        x != y || xs.len() != ys.len() || (xs.iter().zip(&ys)).all(|(&x, &y)| self.may_fit(x, y))
    }

    /// Whether parameters `a` are at least as specific as `b` for `n`
    /// arguments: each of the first `n` a subtype of the other's
    /// ([`Settle::narrower`]).
    pub(super) fn more_specific(&mut self, a: &[Param], b: &[Param], n: usize) -> bool {
        (a.iter().zip(b).take(n)).all(|(x, y)| self.narrower(x.ty, y.ty))
    }

    /// Whether type `x` is `y`, or a subtype of it, for choosing an
    /// overload: also where they're one generic type and each of `x`'s
    /// arguments is narrower than `y`'s, as `List<String>` than
    /// `List<String?>`, which Kotlin Temper's `@out` on `List` makes a
    /// subtype, though tempest leaves it unknown ("Type parameters are
    /// invariant").
    fn narrower(&mut self, x: TypeId, y: TypeId) -> bool {
        let types = &mut self.typing.types;
        if x == y || types.is_subtype(x, y) == Some(true) {
            return true;
        }
        let (TypeData::Applied { base: a, args: xs }, TypeData::Applied { base: b, args: ys }) =
            (types.get(x).clone(), types.get(y).clone())
        else {
            return false;
        };
        a == b && xs.len() == ys.len() && (xs.iter().zip(&ys)).all(|(&x, &y)| self.narrower(x, y))
    }
}
