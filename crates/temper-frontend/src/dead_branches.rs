//! Branches that never run: `if (DEBUG) { … }`, with `let DEBUG = false;`,
//! where the branch names something undeclared. Kotlin Temper folds such an
//! `if` away before it looks names up, so what's in the branch is never
//! reported, and the library builds. Tempest reports the branch's errors,
//! as errors, and recovers by dropping the branch, so the code generated
//! past them runs as Kotlin's does ([`Settle::drop_dead_branches`];
//! notes/kotlin-differences.md).
//!
//! Likewise `missing != null`, with `missing` undeclared: Kotlin types an
//! undeclared name as not null, so the check folds to `true` (and
//! `missing == null` to `false`) with nothing reported. Tempest reports the
//! name as an error and folds the check the same way
//! ([`Settle::fold_undeclared_null_checks`]).

use crate::builtins::{Builtin, Function};
use crate::core::{LET_INIT, LET_VAR, let_parts};
use crate::library::Message;
use crate::scope::SymbolKind;
use crate::settle::Settle;
use temper_syntax::ast::Value;
use temper_syntax::tree::{NodeId, NodeKind};

impl Settle {
    /// Once names are resolved, and before typing, makes each `if` whose
    /// condition is a constant, `true`, `false`, or a `let` initialized to
    /// one, its live branch, where its dead branch holds an error. The
    /// errors stay errors; this is recovery, so what runs matches Kotlin,
    /// which folds the `if` away silently. An `if` whose dead branch is
    /// fine stays as it is.
    pub(crate) fn drop_dead_branches(&mut self) {
        self.fold_undeclared_null_checks();
        let roots: Vec<NodeId> = self.round_roots();
        let mut stack = roots;
        while let Some(node) = stack.pop() {
            let tree = &self.lib.tree;
            let children = tree.children(node);
            if let (NodeKind::If, &[condition, then, otherwise]) = (tree.kind(node), children) {
                if let Some(value) = self.known_bool(condition) {
                    let (live, dead) = if value {
                        (then, otherwise)
                    } else {
                        (otherwise, then)
                    };
                    if self.error_under(dead).is_some() {
                        self.become_node(node, live);
                        stack.push(node);
                        continue;
                    }
                }
            }
            stack.extend_from_slice(self.lib.tree.children(node));
        }
    }

    /// Makes each `missing == null` or `missing != null`, where `missing`
    /// is an undeclared name, `false` or `true`, as Kotlin's typer does for
    /// a name whose type can't be null (its `simplifyNullCheck`). The
    /// name's error stays an error, though Kotlin reports nothing; the
    /// fold is recovery, so what runs matches Kotlin.
    fn fold_undeclared_null_checks(&mut self) {
        let roots: Vec<NodeId> = self.round_roots();
        let mut stack = roots;
        while let Some(node) = stack.pop() {
            let tree = &self.lib.tree;
            let children = tree.children(node);
            if let (NodeKind::Call, &[callee, a, b]) = (tree.kind(node), children) {
                let equal = match self.scopes.resolved_builtin(callee) {
                    Some(Builtin::Function(Function::Eq)) => Some(true),
                    Some(Builtin::Function(Function::Ne)) => Some(false),
                    _ => None,
                };
                let missing = match (self.is_null(a), self.is_null(b)) {
                    (false, true) => Some(a),
                    (true, false) => Some(b),
                    _ => None,
                };
                if let (Some(equal), Some(missing)) = (equal, missing) {
                    if self.is_undeclared_name(missing) {
                        let span = self.lib.tree.span(node);
                        let value = self.lib.add_value(Value::Bool(!equal), span, node);
                        self.become_node(node, value);
                        continue;
                    }
                }
            }
            stack.extend_from_slice(self.lib.tree.children(node));
        }
    }

    /// Whether `node` is an error node made of an undeclared name.
    fn is_undeclared_name(&self, node: NodeId) -> bool {
        let tree = &self.lib.tree;
        tree.kind(node) == NodeKind::Error
            && (self.lib.diagnostics.get(tree.data(node) as usize))
                .is_some_and(|d| matches!(d.message, Message::UndeclaredName(_)))
    }

    /// `true` or `false`, written so ([`Settle::constant_bool`]) or as
    /// the name of a `let`, not a `var`, initialized so.
    fn known_bool(&self, node: NodeId) -> Option<bool> {
        let tree = &self.lib.tree;
        let resolved = (tree.kind(node) == NodeKind::RightName)
            .then(|| self.scopes.resolved(node))
            .flatten();
        let Some(symbol) = resolved.map(|b| self.scopes.symbol(b)) else {
            return self.constant_bool(node);
        };
        let Some(decl) = symbol.decl else {
            return self.constant_bool(node);
        };
        // Not a parameter's default, nor a field's.
        let local = matches!(symbol.kind, SymbolKind::TopLevel | SymbolKind::Local);
        let mutable = symbol.mutable || tree.data(decl) & LET_VAR != 0;
        if !local || mutable || tree.kind(decl) != NodeKind::Let || tree.data(decl) & LET_INIT == 0
        {
            return None;
        }
        self.constant_bool(let_parts(tree, decl).2?)
    }

    /// An error node under `node`, or `node` itself, if there is one.
    pub(crate) fn error_under(&self, node: NodeId) -> Option<NodeId> {
        let tree = &self.lib.tree;
        let mut stack = vec![node];
        while let Some(n) = stack.pop() {
            if tree.kind(n) == NodeKind::Error {
                return Some(n);
            }
            stack.extend_from_slice(tree.children(n));
        }
        None
    }
}
