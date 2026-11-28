//! Flow facts (notes/typing.md), from the second slice of milestone 4:
//! which uses of a local are known not to be null, or to be of a type,
//! from the null checks and `is` checks that control reaches them
//! through.
//!
//! One pass over each file, in evaluation order, carries the set of
//! declarations known not to hold null, and of which type, if an `is`
//! check says so, merging where control flow merges, with no control-flow
//! graph. Only declarations that can't be assigned again are narrowed, as
//! in Kotlin Temper, so a fact, once true, stays true: loops need no fixed
//! point, and a closure keeps the facts where it's made. What's narrowed
//! goes into `Typing::non_null` and `Typing::is_type`, so typing a use of
//! `a: String?` after `if (a != null)` gives `String`, and of `i: I` after
//! `if (i is D)`, `D`.
//!
//! Kotlin Temper narrows only in the branches of an `if` whose condition
//! is a null check or `is` check of a name, negated at most once. This
//! also narrows through `&&`, `||` and `!`, `while` conditions, and after
//! an `if` whose other branch always leaves, as `if (a == null) {
//! return; }` or `{ panic(); }`, or calls a function whose result is
//! `Never<T>`, so it accepts all Kotlin does, and more.

use crate::builtins::{Builtin, Constant, Function};
use crate::core;
use crate::scope::SymbolKind;
use crate::settle::Settle;
use std::collections::{HashMap, HashSet};
use temper_syntax::ast::Value;
use temper_syntax::tree::{NodeId, NodeKind};

/// What's known at a point: the declarations known not to hold null, or
/// `None` where control can't reach.
type Env = Option<Vec<Known>>;

/// A declaration known not to hold null, and if `is` is a type
/// expression, as in `a is T`, to hold a `T`.
#[derive(Clone, Copy)]
struct Known {
    decl: NodeId,
    is: Option<NodeId>,
}

/// What the pass finds.
#[derive(Default)]
struct Walk {
    /// Uses known not to be null.
    non_null: HashSet<NodeId>,
    /// Uses known to be of a type, by the type expression that says so.
    is_type: HashMap<NodeId, NodeId>,
    /// `Block`s that a `break` leaves, so facts from inside them don't
    /// hold after them.
    targets: HashSet<NodeId>,
    /// Declarations that are assigned after they're declared.
    assigned: HashSet<NodeId>,
}

/// `env`, also knowing that `decl` isn't null, and if `is` is given, that
/// it's of that type, in place of any type known before.
fn with(env: &Env, decl: Option<NodeId>, is: Option<NodeId>) -> Env {
    let mut env = env.clone();
    if let (Some(facts), Some(decl)) = (&mut env, decl) {
        match facts.iter_mut().find(|k| k.decl == decl) {
            Some(known) => known.is = is.or(known.is),
            None => facts.push(Known { decl, is }),
        }
    }
    env
}

impl Settle {
    /// The facts of either of two paths: what both know, with a type
    /// only where both know the same one.
    fn merge(&self, a: Env, b: Env) -> Env {
        match (a, b) {
            (None, env) | (env, None) => env,
            (Some(mut a), Some(b)) => {
                a.retain_mut(|known| {
                    let Some(other) = b.iter().find(|k| k.decl == known.decl) else {
                        return false;
                    };
                    let same = match (known.is, other.is) {
                        (Some(x), Some(y)) => self.same_type_expr(x, y),
                        _ => false,
                    };
                    if !same {
                        known.is = None;
                    }
                    true
                });
                Some(a)
            }
        }
    }

    /// Finds the uses of locals known not to be null, before typing.
    pub(crate) fn find_flow_facts(&mut self) {
        let mut walk = Walk::default();
        for root in self.round_roots() {
            self.flow(root, Some(Vec::new()), &mut walk);
        }
        self.typing.non_null.extend(walk.non_null);
        self.typing.is_type.extend(walk.is_type);
        self.typing.assigned.extend(walk.assigned);
    }

    /// Walks a node, given what's known before it, and gives what's known
    /// after it.
    fn flow(&self, node: NodeId, env: Env, walk: &mut Walk) -> Env {
        let tree = &self.lib.tree;
        let children = tree.children(node);
        match tree.kind(node) {
            NodeKind::RightName => {
                if let (Some(facts), Some(decl)) = (&env, self.narrowable(node)) {
                    if let Some(known) = facts.iter().find(|k| k.decl == decl) {
                        walk.non_null.insert(node);
                        if let Some(ty) = known.is {
                            walk.is_type.insert(node, ty);
                        }
                    }
                }
                env
            }
            NodeKind::If if children.len() == 3 => {
                let (then_env, else_env) = self.flow_condition(children[0], env, walk);
                let then_env = self.flow(children[1], then_env, walk);
                let else_env = self.flow(children[2], else_env, walk);
                self.merge(then_env, else_env)
            }
            NodeKind::Block => {
                let entry = env.clone();
                let mut env = env;
                for &child in children {
                    // Functions and classes may be used before they're
                    // declared, so only what held on entry holds in them.
                    if self.hoists(child) {
                        self.flow(child, entry.clone(), walk);
                    } else {
                        env = self.flow(child, env, walk);
                    }
                }
                if walk.targets.contains(&node) {
                    entry
                } else {
                    env
                }
            }
            // A pass may start with what the last one found, or leave by
            // any `break`, so only what held on entry holds in and after
            // a loop.
            NodeKind::Loop | NodeKind::Fn => {
                for &child in children {
                    self.flow(child, env.clone(), walk);
                }
                env
            }
            // The fallback may follow an error anywhere in the body.
            NodeKind::Orelse if children.len() == 2 => {
                let body = self.flow(children[0], env.clone(), walk);
                let fallback = self.flow(children[1], env, walk);
                self.merge(body, fallback)
            }
            NodeKind::Return | NodeKind::Break | NodeKind::Continue | NodeKind::Fail => {
                if tree.kind(node) == NodeKind::Break {
                    walk.targets.insert(NodeId(tree.data(node)));
                }
                for &child in children {
                    self.flow(child, env.clone(), walk);
                }
                None
            }
            _ => {
                if tree.kind(node) == NodeKind::Assign {
                    let target = children.first().and_then(|&t| self.scopes.resolved(t));
                    if let Some(decl) = target.and_then(|b| self.scopes.symbol(b).decl) {
                        walk.assigned.insert(decl);
                    }
                }
                let mut env = env;
                for &child in children {
                    env = self.flow(child, env, walk);
                }
                // Nothing runs after `panic()` or a `Never<T>` call.
                if self.never_returns(node) { None } else { env }
            }
        }
    }

    /// Walks a condition, and gives what's known where it's true and
    /// where it's false.
    fn flow_condition(&self, node: NodeId, env: Env, walk: &mut Walk) -> (Env, Env) {
        let tree = &self.lib.tree;
        let children = tree.children(node);
        match tree.kind(node) {
            NodeKind::Call if !children.is_empty() => {
                let f = match self.scopes.resolved_builtin(children[0]) {
                    Some(Builtin::Function(f)) => Some(f),
                    _ => None,
                };
                if let (Some(Function::Not), [_, arg]) = (f, children) {
                    let (when_true, when_false) = self.flow_condition(*arg, env, walk);
                    return (when_false, when_true);
                }
                let env = self.flow(node, env, walk);
                match (f, &children[1..]) {
                    (Some(Function::Eq), &[a, b]) => {
                        let not_null = with(&env, self.null_checked(a, b), None);
                        (env, not_null)
                    }
                    (Some(Function::Ne), &[a, b]) => {
                        let not_null = with(&env, self.null_checked(a, b), None);
                        (not_null, env)
                    }
                    (Some(Function::Is), &[value, ty]) if !self.is_nullable_type(ty) => {
                        (with(&env, self.narrowable(value), Some(ty)), env)
                    }
                    // Null passes `a is T?`, so where it's false, `a` isn't null.
                    (Some(Function::Is), &[value, _]) => {
                        let not_null = with(&env, self.narrowable(value), None);
                        (env, not_null)
                    }
                    _ => (env.clone(), env),
                }
            }
            // As `a && b`, `a || b` and `if (a) b else c` in a condition.
            NodeKind::If if children.len() == 3 => {
                let (then_env, else_env) = self.flow_condition(children[0], env, walk);
                let (then_true, then_false) = self.flow_condition(children[1], then_env, walk);
                let (else_true, else_false) = self.flow_condition(children[2], else_env, walk);
                let when_true = self.merge(then_true, else_true);
                (when_true, self.merge(then_false, else_false))
            }
            NodeKind::Block if !children.is_empty() => {
                let (&last, init) = children.split_last().unwrap();
                let entry = env.clone();
                let mut env = env;
                for &child in init {
                    env = self.flow(child, env, walk);
                }
                let result = self.flow_condition(last, env, walk);
                if walk.targets.contains(&node) {
                    (entry.clone(), entry)
                } else {
                    result
                }
            }
            _ => {
                let env = self.flow(node, env, walk);
                match self.boolean_constant(node) {
                    Some(true) => (env, None),
                    Some(false) => (None, env),
                    None => (env.clone(), env),
                }
            }
        }
    }

    /// The declaration a name reads, if it's one that a null check can
    /// narrow: a local, parameter or top-level name that isn't `var`.
    fn narrowable(&self, node: NodeId) -> Option<NodeId> {
        if self.lib.tree.kind(node) != NodeKind::RightName {
            return None;
        }
        let symbol = self.scopes.symbol(self.scopes.resolved(node)?);
        match symbol.kind {
            SymbolKind::TopLevel
            | SymbolKind::Local
            | SymbolKind::Parameter
            | SymbolKind::Synthetic
                if !symbol.mutable =>
            {
                symbol.decl
            }
            _ => None,
        }
    }

    /// For `a == null` or `null == a`, `a`'s declaration, if narrowable.
    fn null_checked(&self, a: NodeId, b: NodeId) -> Option<NodeId> {
        if self.is_null(b) {
            self.narrowable(a)
        } else if self.is_null(a) {
            self.narrowable(b)
        } else {
            None
        }
    }

    pub(crate) fn is_null(&self, node: NodeId) -> bool {
        matches!(self.lib.value(node), Some(Value::Null))
            || self.scopes.resolved_builtin(node) == Some(Builtin::Value(Constant::Null))
    }

    /// `true` or `false`, as `a || b` and `a && b` expand to.
    pub(crate) fn boolean_constant(&self, node: NodeId) -> Option<bool> {
        match (self.lib.value(node), self.scopes.resolved_builtin(node)) {
            (Some(Value::Bool(b)), _) => Some(*b),
            (_, Some(Builtin::Value(Constant::True))) => Some(true),
            (_, Some(Builtin::Value(Constant::False))) => Some(false),
            _ => None,
        }
    }

    /// Whether two type expressions name the same type, as two `is`
    /// checks of the same name and type arguments do.
    fn same_type_expr(&self, a: NodeId, b: NodeId) -> bool {
        let tree = &self.lib.tree;
        if a == b {
            return true;
        }
        match (tree.kind(a), tree.kind(b)) {
            (NodeKind::RightName, NodeKind::RightName) => {
                let resolved = self.scopes.resolved(a);
                resolved.is_some() && resolved == self.scopes.resolved(b)
            }
            (NodeKind::Call, NodeKind::Call) => {
                let (xs, ys) = (tree.children(a), tree.children(b));
                xs.len() == ys.len() && xs.iter().zip(ys).all(|(&x, &y)| self.same_type_expr(x, y))
            }
            _ => false,
        }
    }

    /// A type expression `T?`.
    fn is_nullable_type(&self, node: NodeId) -> bool {
        let tree = &self.lib.tree;
        tree.kind(node) == NodeKind::Call
            && self.scopes.resolved_builtin(tree.children(node)[0])
                == Some(Builtin::Function(Function::Nullable))
    }

    /// A function's or class's declaration, which its block hoists.
    pub(crate) fn hoists(&self, node: NodeId) -> bool {
        let tree = &self.lib.tree;
        match tree.kind(node) {
            NodeKind::Class => true,
            NodeKind::Let => core::let_parts(tree, node)
                .2
                .is_some_and(|init| tree.kind(init) == NodeKind::Fn),
            _ => false,
        }
    }
}
