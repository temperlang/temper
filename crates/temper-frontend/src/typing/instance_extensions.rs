//! Instance extensions: a function marked `@extension("name")` answers
//! to a method call `x.name(…)` where it's visible, declared there or
//! outside, or imported, as Kotlin Temper's `DotHelper` lets it, so
//! `"abc".isPalindrome()` calls `stringIsPalindrome("abc")`. As in
//! Kotlin, `x` is the function's first argument, so the extension's
//! first parameter's type is what it extends: a builtin type, a class or
//! interface, a generic type, or a nullable one, as `Int?`, which takes
//! a nullable value. Only calls: `x.name` alone is no member, as in
//! Kotlin.
//!
//! Kotlin types a call's members and extensions as one set of
//! overloads, preferring a member where the two fit as well; here, a
//! member of `x`'s type named `name` whose parameters may take the
//! arguments is called, else the extension that fits best, the most
//! specific of those that fit ([`Settle::narrower`]'s rule, as for
//! `@overload`). A call then becomes a plain call of that function, its
//! callee the extension's name, so the interpreter, tmpl, and every
//! backend see `stringIsPalindrome("abc")`, as Kotlin's generated code
//! has it. Among extensions that fit as well, the one declared nearest
//! is called, as Kotlin lists an inner block's first. Where none fits
//! and there's no member, a lone extension is called anyway, so the
//! call's argument error is reported, as Kotlin does ("Expected subtype
//! of Int32, but got String"); among several, no fit, or more than one
//! fitting as well in one scope, is "No callee matches" (Kotlin quietly
//! calls the first of equals; kotlin-differences.md).

use crate::builtins::Builtin;
use crate::core;
use crate::library::{Level, Message};
use crate::scope::{Lookup, ScopeId, SymbolId};
use crate::settle::{Fact, Settle};
use crate::types::{Param, TypeData, TypeId, UNKNOWN};
use temper_syntax::ast::Value;
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind};

impl Settle {
    /// Makes a call `x.name(…)` a call `f(x, …)` of the visible instance
    /// extension `f` named `name` that fits it best, unless a member of
    /// `x`'s type named `name` fits, returning true if it does. Reports,
    /// if `report`, a call that several extensions and no member might
    /// mean, but none fits or more than one fits as well.
    pub(super) fn resolve_extension(
        &mut self,
        call: NodeId,
        forced: bool,
        report: bool,
    ) -> Result<bool, Fact> {
        if self.extensions.is_empty() {
            return Ok(false);
        }
        let tree = &self.lib.tree;
        let Some((&callee, args)) = tree.children(call).split_first() else {
            return Ok(false);
        };
        let args = args.to_vec();
        let Some((object, key)) = self.dot_parts(callee) else {
            return Ok(false);
        };
        let Some(name) = self.lib.symbol(key) else {
            return Ok(false);
        };
        if !self.extensions.iter().any(|&(_, n)| n == name) {
            return Ok(false);
        }
        // Not a static member's call, `T.name()`, nor yet with named
        // arguments.
        let names_type = tree.kind(object) == NodeKind::RightName
            && (self.builtin_type_named(object).is_some()
                || (self.scopes.resolved(object)).is_some_and(|b| self.type_decl(b).is_some()));
        let is_console = self.scopes.resolved_builtin(object)
            == Some(Builtin::Value(crate::builtins::Constant::Console));
        if names_type
            || is_console
            || (args.iter()).any(|&a| matches!(self.lib.value(a), Some(Value::Symbol(_))))
        {
            return Ok(false);
        }
        let candidates = self.visible_extensions(call, name);
        if candidates.is_empty() {
            return Ok(false);
        }
        let mut arg_types = Vec::with_capacity(args.len() + 1);
        arg_types.push(self.expr_type(object, forced)?);
        for &arg in &args {
            arg_types.push(self.expr_type(arg, forced)?);
        }
        let member = self.member_call_type(object, name, forced)?;
        if let Some(member) = member {
            if self.fits(member, &arg_types[1..]) {
                return Ok(false);
            }
        }
        let mut fitting: Vec<(SymbolId, Vec<Param>)> = Vec::new();
        let mut sigs = Vec::with_capacity(candidates.len());
        for &symbol in &candidates {
            let t = match self.scopes.symbol(symbol).decl {
                Some(decl) => self.decl_type(decl, forced)?,
                None => UNKNOWN,
            };
            let t = self.typing.types.as_fn(t).unwrap_or(t);
            sigs.push(t);
            if self.fits(t, &arg_types) {
                if let TypeData::Fn { params, .. } = self.typing.types.get(t) {
                    fitting.push((symbol, params.clone()));
                }
            }
        }
        let n = arg_types.len();
        // Those at least as specific as each other one; among several
        // as specific, the one declared nearest, as an inner block's
        // shadows an outer one's.
        let top: Vec<usize> = (0..fitting.len())
            .filter(|&i| {
                (0..fitting.len())
                    .all(|j| i == j || self.more_specific(&fitting[i].1, &fitting[j].1, n))
            })
            .collect();
        let depths: Vec<usize> = top.iter().map(|&i| self.depth(fitting[i].0)).collect();
        let deepest = depths.iter().copied().max();
        let best = match (top.len(), deepest) {
            (1, _) => Some(top[0]),
            (_, Some(d)) if depths.iter().filter(|&&x| x == d).count() == 1 => {
                Some(top[depths.iter().position(|&x| x == d).unwrap()])
            }
            _ => None,
        };
        let chosen = match best {
            Some(best) => fitting[best].0,
            // A member that doesn't fit reports its own error.
            None if member.is_some() => return Ok(false),
            None if candidates.len() == 1 => candidates[0],
            None => {
                let known = arg_types.iter().all(|&t| self.typing.types.is_known(t));
                if report && !forced && known {
                    let inputs: Vec<String> = arg_types.iter().map(|&t| self.show(t)).collect();
                    let among: Vec<String> = sigs.iter().map(|&t| self.show(t)).collect();
                    let message = Message::NoCalleeMatches(inputs.join(", "), among.join(", "));
                    let span = self.lib.tree.span(call);
                    self.lib.report(Level::Error, message, span);
                }
                return Ok(false);
            }
        };
        let span = self.lib.tree.span(key);
        let symbol_name = self.scopes.symbol(chosen).name;
        let new_callee = self
            .lib
            .add_name(NodeKind::RightName, symbol_name, span, key);
        self.scopes.resolve(new_callee, chosen);
        let mut children = vec![new_callee, object];
        children.extend(args);
        self.set_children(call, &children);
        self.typing.set_parent(new_callee, call);
        self.typing.set_parent(object, call);
        // Anything typed through the method's name is typed again.
        self.typing.unset_expr(call);
        Ok(true)
    }

    /// The instance extensions named `name` visible at `node` under the
    /// names they have, declared or imported.
    fn visible_extensions(&self, node: NodeId, name: NameId) -> Vec<SymbolId> {
        let Some(scope) = self.scope_at(node) else {
            return Vec::new();
        };
        let pos = self.lib.tree.span(node).start;
        let mut found: Vec<SymbolId> = (self.extensions.iter())
            .filter(|&&(symbol, n)| {
                n == name
                    && matches!(
                        self.scopes.lookup(scope, self.scopes.symbol(symbol).name, false, pos),
                        Lookup::Found(b) if b == symbol
                    )
            })
            .map(|&(symbol, _)| symbol)
            .collect();
        found.dedup();
        found
    }

    /// How many scopes are around the one a symbol is declared in.
    fn depth(&self, symbol: SymbolId) -> usize {
        let mut depth = 0;
        let mut at = self.scopes.get(self.scopes.symbol(symbol).owner).parent;
        while let Some(scope) = at {
            depth += 1;
            at = self.scopes.get(scope).parent;
        }
        depth
    }

    /// The scope a node is in: that of the nearest block, function or
    /// loop around it, or of its file's root, its module's.
    fn scope_at(&self, node: NodeId) -> Option<ScopeId> {
        let mut at = node;
        while let Some(parent) = self.typing.parent(at) {
            if let Some(scope) = self.scopes.of_node(parent) {
                return Some(scope);
            }
            at = parent;
        }
        let file = self.lib.files.iter().find(|f| f.root == at)?;
        Some(self.modules[file.module.0 as usize])
    }

    /// The type of the member a call `object.name(…)` would call, a
    /// method's or a field's, of a class or a builtin type, with any type
    /// parameters of its own unknown; unknown if that's not known, and
    /// none if there's no such member.
    fn member_call_type(
        &mut self,
        object: NodeId,
        name: NameId,
        forced: bool,
    ) -> Result<Option<TypeId>, Fact> {
        let t = self.expr_type(object, forced)?;
        if let Some(receiver) = self.receiver(t) {
            let class = receiver.class;
            let Some(m) = self.member_named(class, name, false, true) else {
                // `@overload("name")` methods are its members by that name.
                let overloaded = !self.overloads_named(class, name, false).is_empty();
                return Ok(overloaded.then_some(UNKNOWN));
            };
            let t = self.member_type_in(m, &receiver);
            let tree = &self.lib.tree;
            let f = core::let_parts(tree, m)
                .2
                .filter(|&f| tree.kind(f) == NodeKind::Fn);
            let formals = f.map(|f| self.own_formals(f)).unwrap_or_default();
            return Ok(Some(self.typing.types.erase(t, &formals)));
        }
        let name = self.lib.names.get(name).to_string();
        self.core_member_named(object, &name, true, forced)
    }

    /// Whether a function of type `f` may take arguments of types `args`,
    /// as far as typing knows: true if `f` isn't a known function type.
    fn fits(&mut self, f: TypeId, args: &[TypeId]) -> bool {
        let f = self.typing.types.as_fn(f).unwrap_or(f);
        let TypeData::Fn { params, .. } = self.typing.types.get(f) else {
            return true;
        };
        let params = params.clone();
        let required = params.iter().filter(|p| !p.optional).count();
        (required..=params.len()).contains(&args.len())
            && (args.iter().zip(&params)).all(|(&a, p)| self.may_fit(a, p.ty))
    }
}
