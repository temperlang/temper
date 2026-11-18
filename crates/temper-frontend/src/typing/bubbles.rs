//! "Cannot bubble from a function without Bubble in its return type", as
//! Kotlin Temper's `BubblesAreNotFree` reports it: each call that may
//! bubble in the body of a function whose return type doesn't say `throws
//! Bubble`, other than in an `orelse`'s body. A `for … of` body counts as
//! part of the function around it; other functions are checked on their
//! own, if they declare a return type, or, for a function literal without
//! one, if its place wants a function type, which gives it its own.
//!
//! What may bubble: `bubble()`; a call of a function whose type `throws
//! Bubble`; `x as T` that checks something ([`Settle::cast_bubbles`]);
//! and `/` and `%`, unless the divisor is an integer constant that
//! can't make them fail (not zero, and for `%`, positive), as
//! [`fold_int`] works it out, which `to_tmpl` uses too. Kotlin folds
//! constants first, so as for the other type errors, a call it might work
//! out while compiling isn't reported (`may_fold`), nor is a branch of an
//! `if` whose condition it might.

use super::signatures;
use crate::builtins::{Builtin, Constant, Function};
use crate::core;
use crate::library::{Level, Library, Message};
use crate::scope::{Scopes, SymbolKind};
use crate::settle::Settle;
use crate::types::{FLOAT64, TypeData, TypeId};
use temper_syntax::ast::Value;
use temper_syntax::tree::{NodeId, NodeKind};

/// An integer constant's value, as Kotlin folds it: a literal, `+`,
/// `-`, `*`, `/`, and `%` of constants, wrapping as `Int` does, and a
/// name whose `let` is set once to a constant, as a module's `let x =
/// 3;`. What makes a division one that can't fail, to typing and to
/// `to_tmpl`.
pub(crate) fn fold_int(
    lib: &Library,
    scopes: &Scopes,
    typing: &super::Typing,
    node: NodeId,
) -> Option<i64> {
    fold(lib, scopes, typing, node, 0).map(|(n, _)| n)
}

/// [`fold_int`]'s value, and whether it's an `Int64`'s.
fn fold(
    lib: &Library,
    scopes: &Scopes,
    typing: &super::Typing,
    node: NodeId,
    depth: u32,
) -> Option<(i64, bool)> {
    let tree = &lib.tree;
    if depth > 16 {
        return None;
    }
    let again = |n: NodeId| fold(lib, scopes, typing, n, depth + 1);
    match tree.kind(node) {
        NodeKind::Value => match lib.value(node)? {
            Value::Int32(n) => Some((i64::from(*n), false)),
            Value::Int64(n) => Some((*n, true)),
            _ => None,
        },
        NodeKind::RightName => {
            let symbol = scopes.symbol(scopes.resolved(node)?);
            let decl = symbol.decl?;
            let fixed = !symbol.mutable || !typing.assigned.contains(&decl);
            let local = matches!(symbol.kind, SymbolKind::Local | SymbolKind::TopLevel);
            if !(fixed && local && tree.kind(decl) == NodeKind::Let) {
                return None;
            }
            again(core::let_parts(tree, decl).2?)
        }
        NodeKind::Call => {
            let (&callee, args) = tree.children(node).split_first()?;
            let Some(Builtin::Function(f)) = scopes.resolved_builtin(callee) else {
                return None;
            };
            let values = (args.iter().map(|&a| again(a))).collect::<Option<Vec<_>>>()?;
            let (n, wide) = match (f, &values[..]) {
                (Function::Sub, &[(a, wide)]) => (a.wrapping_neg(), wide),
                (_, &[(a, x), (b, y)]) => {
                    let n = match f {
                        Function::Add => a.wrapping_add(b),
                        Function::Sub => a.wrapping_sub(b),
                        Function::Mul => a.wrapping_mul(b),
                        Function::Div | Function::Mod if b == 0 => return None,
                        Function::Div => a.wrapping_div(b),
                        Function::Mod => a.wrapping_rem(b),
                        _ => return None,
                    };
                    (n, x || y)
                }
                _ => return None,
            };
            // `Int` wraps at 32 bits.
            Some(if wide {
                (n, true)
            } else {
                (i64::from(n as i32), false)
            })
        }
        _ => None,
    }
}

/// A `Float64` constant's value, as Kotlin folds it: a literal, `-`, and
/// `+`, `-`, `*`, `/`, and `%` of constants, and a name whose `let` is
/// set once to a constant. What makes a float division, as `1.0 / 2.4`,
/// one that can't fail, so `to_tmpl` writes its value.
pub(crate) fn fold_float(
    lib: &Library,
    scopes: &Scopes,
    typing: &super::Typing,
    node: NodeId,
) -> Option<f64> {
    fold_float_at(lib, scopes, typing, node, 0)
}

fn fold_float_at(
    lib: &Library,
    scopes: &Scopes,
    typing: &super::Typing,
    node: NodeId,
    depth: u32,
) -> Option<f64> {
    let tree = &lib.tree;
    if depth > 16 {
        return None;
    }
    let again = |n: NodeId| fold_float_at(lib, scopes, typing, n, depth + 1);
    match tree.kind(node) {
        NodeKind::Value => match lib.value(node)? {
            Value::Float64(n) => Some(*n),
            _ => None,
        },
        NodeKind::RightName => {
            let symbol = scopes.symbol(scopes.resolved(node)?);
            let decl = symbol.decl?;
            let fixed = !symbol.mutable || !typing.assigned.contains(&decl);
            let local = matches!(symbol.kind, SymbolKind::Local | SymbolKind::TopLevel);
            if !(fixed && local && tree.kind(decl) == NodeKind::Let) {
                return None;
            }
            again(core::let_parts(tree, decl).2?)
        }
        NodeKind::Call => {
            let (&callee, args) = tree.children(node).split_first()?;
            let Some(Builtin::Function(f)) = scopes.resolved_builtin(callee) else {
                return None;
            };
            let values = (args.iter().map(|&a| again(a))).collect::<Option<Vec<_>>>()?;
            match (f, &values[..]) {
                (Function::Sub, &[a]) => Some(-a),
                (_, &[a, b]) => match f {
                    Function::Add => Some(a + b),
                    Function::Sub => Some(a - b),
                    Function::Mul => Some(a * b),
                    Function::Div | Function::Mod if b == 0.0 => None,
                    Function::Div => Some(a / b),
                    Function::Mod => Some(a % b),
                    _ => None,
                },
                _ => None,
            }
        }
        _ => None,
    }
}

impl Settle {
    /// Reports what may bubble in a function that declares it doesn't,
    /// in its parameters' defaults and its body.
    pub(super) fn check_bubbles(&mut self, f: NodeId) {
        let (params, _, body) = core::fn_parts(&self.lib.tree, f);
        for param in params {
            if self.lib.tree.kind(param) == NodeKind::Let {
                if let Some(default) = core::let_parts(&self.lib.tree, param).2 {
                    self.find_bubbles(default);
                }
            }
        }
        if let Some(body) = body {
            self.find_bubbles(body);
        }
    }

    /// Whether a function's return type rules out bubbling, so
    /// [`Settle::check_bubbles`] applies: its declared one, unless it
    /// `throws Bubble`, or for a function literal without one, the
    /// function type its place wants, unless that may bubble, as Kotlin
    /// Temper types the literal. Not a generator, nor a `for … of` body.
    pub(super) fn may_not_bubble(&mut self, f: NodeId) -> bool {
        let tree = &self.lib.tree;
        if !core::fn_supers(tree, f).is_empty() || tree.data(f) & core::FN_LOOP_BODY != 0 {
            return false;
        }
        match core::fn_parts(tree, f).1 {
            Some(out_type) => !self.is_throws(out_type),
            None => match self.wanted_fn(f, false) {
                Ok(Some(t)) => {
                    matches!(self.typing.types.get(t), TypeData::Fn { throws: false, .. })
                }
                _ => false,
            },
        }
    }

    /// Whether a type expression is `T throws Bubble`.
    pub(super) fn is_throws(&self, ty: NodeId) -> bool {
        self.lib.tree.kind(ty) == NodeKind::Call
            && self.lib.tree.children(ty).first().is_some_and(|&callee| {
                self.scopes.resolved_builtin(callee) == Some(Builtin::Function(Function::Throws))
            })
    }

    fn find_bubbles(&mut self, node: NodeId) {
        let tree = &self.lib.tree;
        let children = tree.children(node).to_vec();
        match (tree.kind(node), children.as_slice()) {
            (NodeKind::Fn, _) if tree.data(node) & core::FN_LOOP_BODY != 0 => {
                if let Some(body) = core::fn_parts(tree, node).2 {
                    self.find_bubbles(body);
                }
            }
            (NodeKind::Fn, _) => {}
            // What fails in the body goes to the fallback.
            (NodeKind::Orelse, [_, fallback]) => self.find_bubbles(*fallback),
            // Kotlin drops a branch that a constant condition rules out.
            (NodeKind::If, [condition, then, otherwise]) => {
                self.find_bubbles(*condition);
                match self.constant_bool(*condition) {
                    Some(true) => self.find_bubbles(*then),
                    Some(false) => self.find_bubbles(*otherwise),
                    None if self.may_fold(*condition, 0) => {}
                    None => {
                        self.find_bubbles(*then);
                        self.find_bubbles(*otherwise);
                    }
                }
            }
            (NodeKind::Fail, _) => self.report_bubble(node),
            (NodeKind::Call, _) if self.call_bubbles(node) => self.report_bubble(node),
            // Kotlin doesn't look into the arguments of an operator that
            // don't fit it.
            (NodeKind::Call, [callee, args @ ..]) => {
                let unfit = self.unfit_operator_args(*callee, args);
                for &child in &children {
                    if !unfit.contains(&child) {
                        self.find_bubbles(child);
                    }
                }
            }
            _ => {
                for child in children {
                    self.find_bubbles(child);
                }
            }
        }
    }

    fn report_bubble(&mut self, node: NodeId) {
        let span = self.lib.tree.span(node);
        self.lib
            .report(Level::Error, Message::ExpectedNoBubble, span);
    }

    /// Whether a call may bubble, other than through its arguments.
    pub(super) fn call_bubbles(&mut self, call: NodeId) -> bool {
        let children = self.lib.tree.children(call).to_vec();
        let Some((&callee, args)) = children.split_first() else {
            return false;
        };
        match self.scopes.resolved_builtin(callee) {
            Some(Builtin::Function(f @ (Function::Div | Function::Mod))) => {
                self.division_bubbles(call, f, args)
            }
            Some(Builtin::Function(Function::As)) => self.cast_bubbles(args),
            Some(Builtin::Function(Function::New)) => self.new_bubbles(args),
            // `a[i]` calls `a.get(i)`.
            Some(Builtin::Function(Function::Index)) if !args.is_empty() => {
                let t = self.known_index_callee_type(args[0]);
                self.fn_call_bubbles(call, t, &args[1..])
            }
            Some(_) => false,
            None => {
                let t = self.known_callee_type(callee);
                self.fn_call_bubbles(call, t, args)
            }
        }
    }

    /// Whether a call of a function of type `t` with `args` may bubble.
    fn fn_call_bubbles(&mut self, call: NodeId, t: TypeId, args: &[NodeId]) -> bool {
        let TypeData::Fn {
            params,
            throws: true,
            ..
        } = self.typing.types.get(t)
        else {
            return false;
        };
        // Kotlin can't pick a callee for the wrong number of arguments,
        // nor fold a call with an argument that doesn't fit.
        let params = params.clone();
        let required = params.iter().filter(|p| !p.optional).count();
        if !(required..=params.len()).contains(&args.len()) {
            return false;
        }
        let misfit = (args.iter().zip(&params)).any(|(&arg, param)| {
            let t = self.known_type(arg);
            self.is_mismatch(t, param.ty)
        });
        misfit || !self.may_fold(call, 0)
    }

    /// Whether `new C(…)` may bubble: `C`'s constructor says `throws
    /// Bubble`, and takes that many arguments. Kotlin reports it whatever
    /// the arguments are, constants too.
    fn new_bubbles(&mut self, args: &[NodeId]) -> bool {
        let tree = &self.lib.tree;
        let Some((&made, args)) = args.split_first() else {
            return false;
        };
        // `new C<T>(…)` names `C` inside the angle brackets.
        let base = match (tree.kind(made), tree.children(made)) {
            (NodeKind::Call, [angle, base, ..])
                if self.scopes.resolved_builtin(*angle)
                    == Some(Builtin::Function(Function::Angle)) =>
            {
                *base
            }
            _ => made,
        };
        let Some(class) = (self.scopes.resolved(base)).and_then(|b| self.type_decl(b)) else {
            return false;
        };
        let (_, _, members) = core::class_parts(tree, class);
        let constructor = members.iter().find(|&&m| {
            tree.data(m) & core::ROLE_MASK == core::ROLE_CONSTRUCTOR
                && tree.data(m) & core::MEMBER_STATIC == 0
        });
        let Some(f) = constructor.and_then(|&c| core::let_parts(tree, c).2) else {
            return false;
        };
        if tree.kind(f) != NodeKind::Fn {
            return false;
        }
        let (params, out_type, _) = core::fn_parts(tree, f);
        if !out_type.is_some_and(|t| self.is_throws(t)) {
            return false;
        }
        let optional = |p: NodeId| core::let_parts(tree, p).2.is_some();
        let required = params.iter().filter(|&&p| !optional(p)).count();
        (required..=params.len()).contains(&args.len())
    }

    /// The arguments of a call of a builtin operator that don't fit the
    /// signature it picks, which Kotlin doesn't look into for bubbling.
    /// But where an operator has several variants and its first argument
    /// fits none, Kotlin looks into them all.
    fn unfit_operator_args(&mut self, callee: NodeId, args: &[NodeId]) -> Vec<NodeId> {
        let Some(Builtin::Function(f)) = self.scopes.resolved_builtin(callee) else {
            return Vec::new();
        };
        if let [only] = signatures::variants(f, args.len()) {
            let params = only.params;
            return (args.iter().zip(params))
                .filter(|&(&arg, &param)| {
                    let t = self.known_type(arg);
                    self.is_mismatch(t, param)
                })
                .map(|(&arg, _)| arg)
                .collect();
        }
        if let Some((_, unfit)) = self.class_operator_call(f, args) {
            return if unfit { vec![args[1]] } else { Vec::new() };
        }
        match self.builtin_variant(f, args, false) {
            Ok(Some((sig, types))) if types[0] == sig.params[0] => (args.iter().zip(types))
                .zip(sig.params)
                .filter(|&((_, t), p)| t != *p)
                .map(|((&arg, _), _)| arg)
                .collect(),
            _ => Vec::new(),
        }
    }

    /// Whether `a / b` or `a % b` may bubble. Kotlin Temper picks variants
    /// that can't for an integer divisor that's a constant, not zero, and
    /// for `%`, positive; dividing floats always may.
    fn division_bubbles(&mut self, call: NodeId, f: Function, args: &[NodeId]) -> bool {
        let &[a, b] = args else {
            return false;
        };
        if self.may_fold(call, 0) {
            return false;
        }
        if self.known_type(a) == FLOAT64 || self.known_type(b) == FLOAT64 {
            return true;
        }
        match self.constant_int(b) {
            Some(n) if f == Function::Div => n == 0,
            Some(n) => n <= 0,
            None => !self.may_fold(b, 0),
        }
    }

    /// An integer constant's value ([`fold_int`]).
    fn constant_int(&self, node: NodeId) -> Option<i64> {
        fold_int(&self.lib, &self.scopes, &self.typing, node)
    }

    /// `true` or `false`, written as such.
    pub(crate) fn constant_bool(&self, node: NodeId) -> Option<bool> {
        match self.lib.tree.kind(node) {
            NodeKind::Value => match self.lib.value(node)? {
                Value::Bool(b) => Some(*b),
                _ => None,
            },
            NodeKind::RightName => match self.scopes.resolved_builtin(node)? {
                Builtin::Value(Constant::True) => Some(true),
                Builtin::Value(Constant::False) => Some(false),
                _ => None,
            },
            _ => None,
        }
    }
}
