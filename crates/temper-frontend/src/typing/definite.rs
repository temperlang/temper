//! Definite assignment of locals, as Kotlin Temper's `UseBeforeInit`
//! checks it: a local or top-level `let` or `var` read where some path to
//! the read doesn't assign it, as after `let x; if (b) { x = 1; }`, is
//! "x is not initialized along branches". Kotlin's message adds where the
//! paths that miss it are; this one doesn't.
//!
//! One pass over each function body, and each file's top level, in
//! evaluation order, as fields first ([`super::fields_first`]) and flow
//! facts ([`crate::flow`]) walk them, carries the set of declarations
//! that may not be assigned yet: a `let` without an initializer adds its
//! declaration, an assignment takes it out, and branches merge by union.
//! A loop's parts run in order, the last (a `for`'s step, a `do … while`'s
//! condition) also after a `continue`, and what follows the loop is what
//! its `break`s carry; a pass that comes round again has what held on
//! entry, as nothing makes an assigned local unassigned. A `for … of`
//! body may run any number of times, even none. An `orelse`'s fallback
//! starts with what held where its body may fail: at each call that may
//! fail (as [`super::failing`] finds them), `throw`, `await`, or error, so
//! `(x = i) orelse …` assigns `x` on every path, as in Kotlin. A path
//! that returns, throws, panics, breaks, continues, or reaches an error
//! needs nothing.
//!
//! As in Kotlin, a function reads its enclosing function's locals
//! unchecked, wherever it's called, though its own are checked; so are a
//! class's members. A block's `let`s count as unassigned from the block's
//! start, so a top level read before its `let` runs, as a cycle among top
//! levels makes it (`let a = b + 1; let b = a + 1;`), is reported too.
//! Each read reported counts as assigning, so each path reports a local
//! once.

use crate::core::{self, FN_LOOP_BODY, MEMBER_CONNECTED};
use crate::library::{Level, Message};
use crate::scope::SymbolKind;
use crate::settle::Settle;
use std::collections::{BTreeSet, HashMap, HashSet};
use temper_syntax::tree::{NodeId, NodeKind, Span};

/// The declarations that may not be assigned at a point, or `None` where
/// control can't reach.
type Pending = Option<BTreeSet<NodeId>>;

/// One pass's state.
#[derive(Default)]
struct Cx {
    /// What reaches each `break`'s target.
    breaks: HashMap<NodeId, Pending>,
    /// What reaches each loop by `continue`.
    continues: HashMap<NodeId, Pending>,
    /// The calls in `orelse` bodies that may fail.
    failing: HashSet<NodeId>,
    /// For each `orelse` body being walked, innermost last, what reaches
    /// its fallback so far.
    failures: Vec<Pending>,
    errors: Vec<(Span, Message)>,
}

fn merge(a: Pending, b: Pending) -> Pending {
    match (a, b) {
        (None, p) | (p, None) => p,
        (Some(mut a), Some(b)) => {
            a.extend(b);
            Some(a)
        }
    }
}

impl Settle {
    /// Reports locals read before they're assigned on some path.
    pub(super) fn check_definite_assignment(&mut self) {
        let mut cx = Cx {
            failing: self.failing_calls(),
            ..Cx::default()
        };
        // Each module's top levels as they'll run.
        let first = self.round_files();
        for m in 0..self.lib.modules.len() {
            let roots: Vec<NodeId> = (self.lib.modules[m].files.iter())
                .filter(|f| f.0 as usize >= first)
                .map(|f| self.lib.files[f.0 as usize].root)
                .collect();
            if roots.is_empty() {
                continue;
            }
            let order = crate::order::top_level_order(
                &self.lib.tree,
                &self.scopes,
                self.modules[m],
                &roots,
            );
            self.statements_flow(&mut cx, &order, Some(BTreeSet::new()));
        }
        cx.errors.sort_by_key(|(span, _)| (span.start, span.end));
        for (span, message) in cx.errors {
            self.lib.report(Level::Error, message, span);
        }
    }

    /// The calls in `orelse` bodies that may fail.
    fn failing_calls(&mut self) -> HashSet<NodeId> {
        let mut calls = Vec::new();
        let mut stack: Vec<(NodeId, bool)> = (self.round_roots().into_iter())
            .map(|r| (r, false))
            .collect();
        while let Some((node, caught)) = stack.pop() {
            let tree = &self.lib.tree;
            if caught && tree.kind(node) == NodeKind::Call {
                calls.push(node);
            }
            let children = tree.children(node);
            for (i, &child) in children.iter().enumerate() {
                let body = tree.kind(node) == NodeKind::Orelse && i == 0;
                stack.push((child, caught || body));
            }
        }
        (calls.into_iter())
            .filter(|&c| self.call_fails(c))
            .collect()
    }

    /// Notes that the innermost `orelse`'s fallback may follow here.
    fn may_fail(&self, cx: &mut Cx, pending: &Pending) {
        if let Some(top) = cx.failures.last_mut() {
            *top = merge(top.take(), pending.clone());
        }
    }

    /// The declaration a `let` declares, if this checks it: a local's or
    /// top level's, not a function's.
    fn tracked_decl(&self, node: NodeId) -> Option<NodeId> {
        let tree = &self.lib.tree;
        // Nor a bodiless `@connected` function, which backends define.
        let connected = tree.data(node) & MEMBER_CONNECTED != 0;
        if tree.kind(node) != NodeKind::Let || self.hoists(node) || connected {
            return None;
        }
        let name = tree.children(node)[0];
        let symbol = self.scopes.symbol(self.scopes.resolved(name)?);
        let tracked = matches!(
            symbol.kind,
            SymbolKind::Local | SymbolKind::TopLevel | SymbolKind::Synthetic
        );
        (tracked && symbol.decl == Some(node)).then_some(node)
    }

    /// The declaration a name reads, if this checks it.
    fn tracked_read(&self, node: NodeId) -> Option<NodeId> {
        let symbol = self.scopes.symbol(self.scopes.resolved(node)?);
        let decl = symbol.decl?;
        self.tracked_decl(decl)
    }

    /// The loop a `continue` to `target` goes round, as a label's block
    /// holds it.
    fn continued_loop(&self, target: NodeId) -> NodeId {
        let tree = &self.lib.tree;
        let mut node = target;
        while tree.kind(node) == NodeKind::Block {
            match tree.children(node).last() {
                Some(&last) => node = last,
                None => return target,
            }
        }
        node
    }

    /// Walks a block's statements, whose `let`s count as unassigned from
    /// its start.
    fn statements_flow(&self, cx: &mut Cx, statements: &[NodeId], pending: Pending) -> Pending {
        let mut pending = pending;
        if let Some(p) = &mut pending {
            p.extend(statements.iter().filter_map(|&s| self.tracked_decl(s)));
        }
        for &statement in statements {
            if self.hoists(statement) {
                self.assigned_flow(cx, statement, Some(BTreeSet::new()));
            } else {
                pending = self.assigned_flow(cx, statement, pending);
            }
        }
        pending
    }

    /// Walks a node, given what may be unassigned before it, and gives
    /// what may be after it.
    fn assigned_flow(&self, cx: &mut Cx, node: NodeId, pending: Pending) -> Pending {
        let tree = &self.lib.tree;
        let children = tree.children(node);
        let Some(mut now) = pending else {
            // Code that can't run has its own locals checked still.
            self.assigned_flow(cx, node, Some(BTreeSet::new()));
            return None;
        };
        match tree.kind(node) {
            NodeKind::RightName => {
                if let Some(decl) = self.tracked_read(node) {
                    if now.remove(&decl) {
                        let name = self.lib.tree.children(decl)[0];
                        let name = (self.lib.name(name))
                            .map(|n| self.lib.names.get(n).to_string())
                            .unwrap_or_default();
                        let message = Message::UseBeforeInitialization(name);
                        cx.errors.push((tree.span(node), message));
                    }
                }
                Some(now)
            }
            NodeKind::Block => {
                let pending = self.statements_flow(cx, children, Some(now));
                let broken = cx.breaks.remove(&node).flatten();
                merge(pending, broken)
            }
            NodeKind::Let => {
                let init = core::let_parts(tree, node).2;
                let mut pending = match init {
                    Some(init) => self.assigned_flow(cx, init, Some(now)),
                    None => Some(now),
                };
                if let (Some(p), Some(decl)) = (&mut pending, self.tracked_decl(node)) {
                    if init.is_some() {
                        p.remove(&decl);
                    } else {
                        p.insert(decl);
                    }
                }
                pending
            }
            NodeKind::Assign if children.len() == 2 => {
                let (target, value) = (children[0], children[1]);
                let mut pending = self.assigned_flow(cx, value, Some(now));
                let local = (tree.kind(target) == NodeKind::RightName)
                    .then(|| self.tracked_read(target))
                    .flatten();
                match local {
                    Some(decl) => {
                        if let Some(p) = &mut pending {
                            p.remove(&decl);
                        }
                        pending
                    }
                    None => self.assigned_flow(cx, target, pending),
                }
            }
            NodeKind::If if children.len() == 3 => {
                let pending = self.assigned_flow(cx, children[0], Some(now));
                // `while (true)` checks a constant.
                let constant = self.boolean_constant(children[0]);
                let then = pending.clone().filter(|_| constant != Some(false));
                let other = pending.filter(|_| constant != Some(true));
                let then = self.assigned_flow(cx, children[1], then);
                let other = self.assigned_flow(cx, children[2], other);
                merge(then, other)
            }
            NodeKind::Loop => {
                let entry = now;
                let mut pending = Some(entry.clone());
                let last = children.len().saturating_sub(1);
                for (i, &child) in children.iter().enumerate() {
                    if i == last && i > 0 {
                        let continued = cx.continues.remove(&node).flatten();
                        pending = merge(pending, continued);
                    }
                    // Unreachable after an unconditional jump, but checked.
                    let at = pending.or_else(|| Some(entry.clone()));
                    pending = self.assigned_flow(cx, child, at);
                }
                cx.continues.remove(&node);
                cx.breaks.remove(&node).flatten()
            }
            NodeKind::Break => {
                let target = NodeId(tree.data(node));
                let before = cx.breaks.remove(&target).flatten();
                cx.breaks.insert(target, merge(before, Some(now)));
                None
            }
            NodeKind::Continue => {
                let target = self.continued_loop(NodeId(tree.data(node)));
                let before = cx.continues.remove(&target).flatten();
                cx.continues.insert(target, merge(before, Some(now)));
                None
            }
            // An error fails where it runs.
            NodeKind::Error => {
                self.may_fail(cx, &Some(now));
                None
            }
            NodeKind::Return | NodeKind::Fail => {
                let mut pending = Some(now);
                for &child in children {
                    pending = self.assigned_flow(cx, child, pending);
                }
                if tree.kind(node) == NodeKind::Fail {
                    self.may_fail(cx, &pending);
                }
                None
            }
            NodeKind::Await => {
                let mut pending = Some(now);
                for &child in children {
                    pending = self.assigned_flow(cx, child, pending);
                }
                self.may_fail(cx, &pending);
                pending
            }
            // The fallback follows each error in the body.
            NodeKind::Orelse if children.len() == 2 => {
                cx.failures.push(None);
                let body = self.assigned_flow(cx, children[0], Some(now));
                let failed = cx.failures.pop().flatten();
                let fallback = self.assigned_flow(cx, children[1], failed);
                merge(body, fallback)
            }
            // A `for … of` body runs any number of times.
            NodeKind::Fn if tree.data(node) & FN_LOOP_BODY != 0 => {
                if let Some(body) = core::fn_parts(tree, node).2 {
                    self.assigned_flow(cx, body, Some(now.clone()));
                }
                Some(now)
            }
            // A function's own locals, from its parameters' defaults on;
            // what it throws, its caller's `orelse` catches.
            NodeKind::Fn => {
                let failures = std::mem::take(&mut cx.failures);
                let (params, _, body) = core::fn_parts(tree, node);
                let mut inner = Some(BTreeSet::new());
                for param in params {
                    if let Some(default) = core::let_parts(tree, param).2 {
                        inner = self.assigned_flow(cx, default, inner);
                    }
                }
                if let Some(body) = body {
                    self.assigned_flow(cx, body, inner.or_else(|| Some(BTreeSet::new())));
                }
                cx.failures = failures;
                Some(now)
            }
            NodeKind::Class => {
                let failures = std::mem::take(&mut cx.failures);
                for &child in children {
                    self.assigned_flow(cx, child, Some(BTreeSet::new()));
                }
                cx.failures = failures;
                Some(now)
            }
            NodeKind::Call => {
                let mut pending = Some(now);
                for &child in children {
                    pending = self.assigned_flow(cx, child, pending);
                }
                if cx.failing.contains(&node) {
                    self.may_fail(cx, &pending);
                }
                if self.never_returns(node) {
                    None
                } else {
                    pending
                }
            }
            _ => {
                let mut pending = Some(now);
                for &child in children {
                    pending = self.assigned_flow(cx, child, pending);
                }
                pending
            }
        }
    }
}
