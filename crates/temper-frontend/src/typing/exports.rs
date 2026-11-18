//! Exports that expose what isn't exported, as Kotlin Temper's
//! `ExportChecker` reports them: "Export depends publicly on non-exported
//! symbol Hidden", where an exported declaration's public signature names
//! a class or interface of its own module that isn't exported. Another
//! module's types, type parameters, and builtin types are fine.
//!
//! What's checked, as Kotlin does: an exported function's type
//! parameters' bounds, parameters' types, and result type; an exported
//! value's type, as declared or inferred (`export let more = new
//! Map<String, Hidden>([])`); and of an exported class or interface, its
//! type parameters' bounds, its supertypes, and each member that isn't
//! private: a property's type (a header's `public hi: Hidden`, a field,
//! a getter's result or a setter's parameter), a method's signature, a
//! constructor's parameters, but not one a property already reported,
//! so a private header property is reported once, as the constructor's,
//! and a static member's type. Each name of such a type in a signature
//! is reported where it's written, or an inferred type's at the
//! declaration's name. The error is type-only: the code still runs and
//! is written as it is.

use crate::core::{
    self, MEMBER_PRIVATE, MEMBER_STATIC, ROLE_CONSTRUCTOR, ROLE_GETTER, ROLE_MASK, ROLE_METHOD,
    ROLE_PARAM, ROLE_SETTER,
};
use crate::library::{Level, Message};
use crate::scope::{ScopeId, SymbolId, SymbolKind};
use crate::settle::Settle;
use crate::types::TypeData;
use std::collections::HashSet;
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind, Span};

impl Settle {
    /// Reports exported declarations whose signatures name their
    /// module's types that aren't exported.
    pub(super) fn check_exports(&mut self) {
        let first = self.round_files();
        let mut errors = Vec::new();
        for m in 0..self.lib.modules.len() {
            let module = self.modules[m];
            let roots: Vec<NodeId> = (self.lib.modules[m].files.iter())
                .filter(|f| f.0 as usize >= first)
                .map(|f| self.lib.files[f.0 as usize].root)
                .collect();
            for root in roots {
                for &statement in self.lib.tree.children(root) {
                    self.check_export(module, statement, &mut errors);
                }
            }
        }
        errors.sort_by_key(|&(span, _): &(Span, String)| (span.start, span.end));
        errors.dedup();
        for (span, name) in errors {
            let message = Message::ExportNeedsNonExported(name);
            self.lib.report(Level::Error, message, span);
        }
    }

    /// Checks a top-level statement, if it's an export.
    fn check_export(&self, module: ScopeId, node: NodeId, errors: &mut Vec<(Span, String)>) {
        let tree = &self.lib.tree;
        let kind = tree.kind(node);
        if !matches!(kind, NodeKind::Let | NodeKind::Class) {
            return;
        }
        let name = tree.children(node)[0];
        let exported = (self.scopes.resolved(name)).is_some_and(|b| {
            self.scopes.symbol(b).exported && self.scopes.symbol(b).owner == module
        });
        if !exported {
            return;
        }
        if kind == NodeKind::Class {
            self.check_exported_class(module, node, errors);
            return;
        }
        let (_, ty, init) = core::let_parts(tree, node);
        match (ty, init) {
            (_, Some(f)) if tree.kind(f) == NodeKind::Fn => {
                self.check_exported_fn(module, f, None, errors);
            }
            (Some(ty), _) => self.check_type_expr(module, ty, errors),
            (None, Some(_)) => {
                if let Some(&t) = self.typing.decls.get(&node) {
                    self.check_inferred(module, t, tree.span(name), errors, 0);
                }
            }
            (None, None) => {}
        }
    }

    /// A function's or method's type parameters' bounds, parameters'
    /// types, other than those named in `skip`, and result type.
    fn check_exported_fn(
        &self,
        module: ScopeId,
        f: NodeId,
        skip: Option<&HashSet<NameId>>,
        errors: &mut Vec<(Span, String)>,
    ) {
        let tree = &self.lib.tree;
        let bounds: Vec<NodeId> = (core::type_params(tree, f).iter())
            .filter_map(|&p| core::let_parts(tree, p).1)
            .collect();
        let (params, out, _) = core::fn_parts(tree, f);
        for bound in bounds {
            self.check_type_expr(module, bound, errors);
        }
        for param in params {
            let tree = &self.lib.tree;
            let (name, ty, _) = core::let_parts(tree, param);
            let skipped =
                (self.lib.name(name)).is_some_and(|n| skip.is_some_and(|s| s.contains(&n)));
            if let (Some(ty), false) = (ty, skipped) {
                self.check_type_expr(module, ty, errors);
            }
        }
        if let Some(out) = out {
            self.check_type_expr(module, out, errors);
        }
    }

    /// An exported class's or interface's public face.
    fn check_exported_class(
        &self,
        module: ScopeId,
        class: NodeId,
        errors: &mut Vec<(Span, String)>,
    ) {
        let tree = &self.lib.tree;
        let bounds: Vec<NodeId> = (core::type_params(tree, class).iter())
            .filter_map(|&p| core::let_parts(tree, p).1)
            .collect();
        let (_, supers, members) = core::class_parts(tree, class);
        let (supers, members) = (supers.to_vec(), members.to_vec());
        for t in bounds.into_iter().chain(supers) {
            self.check_type_expr(module, t, errors);
        }
        let role = |s: &Settle, m: NodeId| s.lib.tree.data(m) & ROLE_MASK;
        let public = |s: &Settle, m: NodeId| s.lib.tree.data(m) & MEMBER_PRIVATE == 0;
        let is_static = |s: &Settle, m: NodeId| s.lib.tree.data(m) & MEMBER_STATIC != 0;
        // Properties first, so constructors don't report them again.
        let mut reported: HashSet<NameId> = HashSet::new();
        for &m in &members {
            if !public(self, m) || is_static(self, m) || tree_kind(self, m) != NodeKind::Let {
                continue;
            }
            let (name, ty, init) = core::let_parts(&self.lib.tree, m);
            let name = self.lib.name(name);
            if name.is_some_and(|n| reported.contains(&n)) {
                continue;
            }
            let before = errors.len();
            match role(self, m) {
                0 | ROLE_PARAM => match ty {
                    Some(ty) => self.check_type_expr(module, ty, errors),
                    None => {
                        if let Some(&t) = self.typing.decls.get(&m) {
                            let span = self.lib.tree.span(self.lib.tree.children(m)[0]);
                            self.check_inferred(module, t, span, errors, 0);
                        }
                    }
                },
                ROLE_GETTER => {
                    if let Some(out) = init.and_then(|f| core::fn_parts(&self.lib.tree, f).1) {
                        self.check_type_expr(module, out, errors);
                    }
                }
                ROLE_SETTER => {
                    let params = init.map(|f| core::fn_parts(&self.lib.tree, f).0);
                    let tys = (params.into_iter().flatten())
                        .filter_map(|p| core::let_parts(&self.lib.tree, p).1)
                        .collect::<Vec<_>>();
                    for ty in tys {
                        self.check_type_expr(module, ty, errors);
                    }
                }
                _ => continue,
            }
            if errors.len() > before {
                reported.extend(name);
            }
        }
        for &m in &members {
            if !public(self, m) || tree_kind(self, m) != NodeKind::Let {
                continue;
            }
            let init = core::let_parts(&self.lib.tree, m).2;
            let f = init.filter(|&f| tree_kind(self, f) == NodeKind::Fn);
            match (role(self, m), is_static(self, m), f) {
                (ROLE_CONSTRUCTOR, false, Some(f)) => {
                    self.check_exported_fn(module, f, Some(&reported), errors);
                }
                (ROLE_METHOD, _, Some(f)) => self.check_exported_fn(module, f, None, errors),
                (ROLE_GETTER | ROLE_SETTER, true, Some(f)) => {
                    self.check_exported_fn(module, f, None, errors);
                }
                // A static field.
                (0, true, _) => match core::let_parts(&self.lib.tree, m).1 {
                    Some(ty) => self.check_type_expr(module, ty, errors),
                    None => {
                        if let Some(&t) = self.typing.decls.get(&m) {
                            let span = self.lib.tree.span(self.lib.tree.children(m)[0]);
                            self.check_inferred(module, t, span, errors, 0);
                        }
                    }
                },
                _ => {}
            }
        }
        // The implied constructor takes the header's private properties.
        let has_ctor =
            (members.iter()).any(|&m| role(self, m) == ROLE_CONSTRUCTOR && !is_static(self, m));
        if !has_ctor {
            for &m in &members {
                if role(self, m) == ROLE_PARAM && !public(self, m) {
                    if let Some(ty) = core::let_parts(&self.lib.tree, m).1 {
                        self.check_type_expr(module, ty, errors);
                    }
                }
            }
        }
    }

    /// Each name in a type expression of a class or interface of this
    /// module that isn't exported.
    fn check_type_expr(&self, module: ScopeId, node: NodeId, errors: &mut Vec<(Span, String)>) {
        let tree = &self.lib.tree;
        if tree.kind(node) == NodeKind::RightName {
            if let Some(b) = self.scopes.resolved(node) {
                if let Some(name) = self.hidden_type(module, b) {
                    errors.push((tree.span(node), name));
                }
            }
            return;
        }
        for &child in tree.children(node) {
            self.check_type_expr(module, child, errors);
        }
    }

    /// An inferred type's classes and interfaces of this module that
    /// aren't exported, reported at `span`.
    fn check_inferred(
        &self,
        module: ScopeId,
        t: crate::types::TypeId,
        span: Span,
        errors: &mut Vec<(Span, String)>,
        depth: u32,
    ) {
        if depth > 16 {
            return;
        }
        match self.typing.types.get(t) {
            &TypeData::Class(b) => {
                if let Some(name) = self.hidden_type(module, b) {
                    errors.push((span, name));
                }
            }
            TypeData::Applied { base, args } => {
                for &x in std::iter::once(base).chain(args) {
                    self.check_inferred(module, x, span, errors, depth + 1);
                }
            }
            &TypeData::Nullable(x) => self.check_inferred(module, x, span, errors, depth + 1),
            TypeData::Fn { params, ret, .. } => {
                for x in params.iter().map(|p| p.ty).chain([*ret]) {
                    self.check_inferred(module, x, span, errors, depth + 1);
                }
            }
            _ => {}
        }
    }

    /// The name of the class or interface a symbol declares, if it's one
    /// of this module's top levels that isn't exported.
    fn hidden_type(&self, module: ScopeId, b: SymbolId) -> Option<String> {
        let symbol = self.scopes.symbol(b);
        let decl = symbol.decl?;
        let hidden = symbol.kind == SymbolKind::TopLevel
            && symbol.owner == module
            && !symbol.exported
            && self.lib.tree.kind(decl) == NodeKind::Class;
        hidden.then(|| self.lib.names.get(symbol.name).to_string())
    }
}

fn tree_kind(s: &Settle, node: NodeId) -> NodeKind {
    s.lib.tree.kind(node)
}
