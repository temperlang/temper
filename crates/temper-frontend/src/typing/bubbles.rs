//! "Cannot bubble from a function without Bubble in its return type", as
//! Kotlin Temper's `BubblesAreNotFree` reports it: each call that may
//! bubble in the body of a function whose return type doesn't say `throws
//! Bubble`, other than in an `orelse`'s body. A `for … of` body counts as
//! part of the function around it; other functions are checked on their
//! own, if they declare a return type, or, for a function literal without
//! one, if its place wants a function type, which gives it its own.
//!
//! What may bubble: `bubble()`; `await`, of a promise that may be broken;
//! a call of a function whose type `throws Bubble`; `x as T` that checks
//! something ([`Settle::cast_bubbles`]); and `/` and `%`, unless the
//! divisor is an integer constant that can't make them fail (not zero,
//! and for `%`, positive), as [`fold_int`] works it out, which
//! `to_tmpl` uses too. Kotlin folds constants first, so as for the other
//! type errors, a call it might work out while compiling isn't reported
//! (`may_fold`), nor is a branch of an `if` whose condition it might.

use super::signatures;
use crate::builtins::{Builtin, Constant, Function};
use crate::core;
use crate::library::{Level, Library, Message};
use crate::scope::{Scopes, SymbolKind};
use crate::settle::{Fact, Settle};
use crate::types::{BUBBLE, ERROR, FLOAT64, TypeData, TypeId, UNKNOWN};
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

/// What a function may fail with, as its result type says
/// ([`Settle::allowed_failure`]).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum Allowed {
    /// Not checked on its own: a `for … of` body, part of the function
    /// around it, or a function literal whose type isn't worked out.
    Unchecked,
    /// It can't fail.
    Nothing,
    /// It may fail with this: `Bubble`, a declared failure type, or a
    /// failure parameter.
    Only(TypeId),
}

impl Settle {
    /// Reports what may fail in a function, in its parameters' defaults
    /// and its body, but its result type doesn't let it
    /// ([`Settle::allowed_failure`]): any failure where it can't fail,
    /// and otherwise one that isn't of its failure type, as `Bubble` and
    /// declared failures never mix (notes/typing.md, "Declared
    /// failures").
    pub(super) fn check_failures(&mut self, f: NodeId) {
        match self.allowed_failure(f) {
            Allowed::Unchecked => {}
            Allowed::Nothing => match self.unfailing_place(f) {
                Some(message) => {
                    if !self.failures_in(f).is_empty() {
                        let span = self.lib.tree.span(f);
                        self.lib.report(Level::Error, message, span);
                    }
                }
                None => self.check_bubbles(f),
            },
            Allowed::Only(allowed) => {
                for (node, fail) in self.failures_in(f) {
                    if let Some(message) = self.misfit_failure(fail, allowed) {
                        let span = self.lib.tree.span(node);
                        self.lib.report(Level::Error, message, span);
                    }
                }
            }
        }
    }

    /// Reports what may fail in a function that can't, in its parameters'
    /// defaults and its body: "Cannot bubble …" for what may bubble, as
    /// Kotlin Temper reports it, and "Cannot throw …" for a declared
    /// failure. A `throw` of what isn't a failure is reported there.
    pub(super) fn check_bubbles(&mut self, f: NodeId) {
        for (node, fail) in self.failures_in(f) {
            let message = match fail {
                BUBBLE => Message::ExpectedNoBubble,
                UNKNOWN if self.lib.tree.kind(node) == NodeKind::Fail => continue,
                UNKNOWN => Message::ExpectedNoBubble,
                _ => Message::UndeclaredFailure(self.show(fail)),
            };
            let span = self.lib.tree.span(node);
            self.lib.report(Level::Error, message, span);
        }
    }

    /// Why a failure of type `fail` can't pass through a function that
    /// may fail with `allowed`, if it can't.
    fn misfit_failure(&mut self, fail: TypeId, allowed: TypeId) -> Option<Message> {
        if fail == UNKNOWN || allowed == UNKNOWN || fail == allowed {
            return None;
        }
        Some(match (fail, allowed) {
            (_, BUBBLE) => Message::FailureIntoBubble(self.show(fail)),
            (BUBBLE, _) => Message::BubbleIntoFailure(self.show(allowed)),
            _ if self.typing.types.is_subtype(fail, allowed) == Some(false) => {
                Message::FailureMismatch(self.show(fail), self.show(allowed))
            }
            _ => return None,
        })
    }

    /// What may fail in a function's parameters' defaults and body, each
    /// with what it may fail with ([`Settle::failure_of`]).
    pub(super) fn failures_in(&mut self, f: NodeId) -> Vec<(NodeId, TypeId)> {
        let (params, _, body) = core::fn_parts(&self.lib.tree, f);
        let mut found = Vec::new();
        for param in params {
            if self.lib.tree.kind(param) == NodeKind::Let {
                if let Some(default) = core::let_parts(&self.lib.tree, param).2 {
                    self.find_bubbles(default, &mut found);
                }
            }
        }
        if let Some(body) = body {
            self.find_bubbles(body, &mut found);
        }
        found
    }

    /// What may fail in an expression and isn't handled in it, as a
    /// `catch`'s operand: each place, with what it may fail with.
    pub(super) fn failures_under(&mut self, node: NodeId) -> Vec<(NodeId, TypeId)> {
        let mut found = Vec::new();
        self.find_bubbles(node, &mut found);
        found
    }

    /// One type for what several places may fail with: `Bubble` if any
    /// may bubble; else declared failures' join, or the root where they
    /// have no closer common type; unknown if one is; `None` for none.
    pub(super) fn join_failures(&mut self, fails: &[(NodeId, TypeId)]) -> Option<TypeId> {
        let mut joined: Option<TypeId> = None;
        for &(_, fail) in fails {
            joined = Some(match joined {
                None => fail,
                Some(BUBBLE) => BUBBLE,
                Some(_) if fail == BUBBLE => BUBBLE,
                Some(UNKNOWN) => UNKNOWN,
                Some(_) if fail == UNKNOWN => UNKNOWN,
                Some(so_far) => match self.typing.types.join(so_far, fail) {
                    UNKNOWN => ERROR,
                    t => t,
                },
            });
        }
        joined
    }

    /// Waits for the types of the expressions under `node` and of their
    /// callees, which finding what may fail there reads, unless typing's
    /// round is over.
    fn wait_for_failures(&mut self, node: NodeId, forced: bool) -> Result<(), Fact> {
        if self.typing.checking {
            return Ok(());
        }
        let exprs = super::exprs::Exprs::new(&self.lib, &self.scopes).under(node);
        for expr in exprs {
            self.expr_type(expr.node, forced)?;
            let tree = &self.lib.tree;
            if tree.kind(expr.node) == NodeKind::Call {
                let callee = tree.children(expr.node)[0];
                if self.scopes.resolved_builtin(callee).is_none() {
                    self.callee_type(callee, forced)?;
                }
            }
        }
        Ok(())
    }

    /// What a function literal without a declared result may fail with,
    /// from its body ([`Settle::failures_in`], [`Settle::join_failures`]),
    /// if anything. Before checking, it waits for the types of its body's
    /// expressions and callees, which the search reads.
    pub(super) fn literal_failure(
        &mut self,
        f: NodeId,
        forced: bool,
    ) -> Result<Option<TypeId>, Fact> {
        if let Some(&fail) = self.typing.literal_failures.get(&f) {
            return Ok(fail);
        }
        if let Some(body) = core::fn_parts(&self.lib.tree, f).2 {
            self.wait_for_failures(body, forced)?;
        }
        let failures = self.failures_in(f);
        let fail = self.join_failures(&failures);
        if !forced {
            self.typing.literal_failures.insert(f, fail);
        }
        Ok(fail)
    }

    /// The failure a `catch (e)` binds, the hidden `caught()` call in the
    /// `let e = caught();` that starts its block: what the `Orelse`'s
    /// body may fail with ([`Settle::join_failures`]); unknown where that
    /// may bubble, as a `Bubble` has no value ([`Settle::check_caught`]),
    /// or nothing may fail.
    pub(super) fn caught_type(&mut self, caught: NodeId, forced: bool) -> Result<TypeId, Fact> {
        let Some(body) = self.caught_from(caught) else {
            return Ok(UNKNOWN);
        };
        self.wait_for_failures(body, forced)?;
        let failures = self.failures_under(body);
        Ok(match self.join_failures(&failures) {
            Some(BUBBLE) | None => UNKNOWN,
            Some(t) => t,
        })
    }

    /// Reports a `catch (e)` whose operand may bubble, as a `Bubble`
    /// has no value to bind.
    pub(super) fn check_caught(&mut self, caught: NodeId) {
        let Some(body) = self.caught_from(caught) else {
            return;
        };
        let failures = self.failures_under(body);
        if self.join_failures(&failures) == Some(BUBBLE) {
            let tree = &self.lib.tree;
            let Some(name) =
                (self.typing.parent(caught)).and_then(|bind| tree.children(bind).first().copied())
            else {
                return;
            };
            let span = tree.span(name);
            let name =
                (self.lib.name(name)).map_or(String::new(), |n| self.lib.names.get(n).to_string());
            let message = Message::CatchBindsBubble(name);
            self.lib.report(Level::Error, message, span);
        }
    }

    /// The operand of the `Orelse` whose failure a `caught()` call binds:
    /// the call is the value of the `let` that starts the `Orelse`'s
    /// fallback block.
    fn caught_from(&self, caught: NodeId) -> Option<NodeId> {
        let tree = &self.lib.tree;
        let bind = self.typing.parent(caught)?;
        let block = self.typing.parent(bind)?;
        let handler = self.typing.parent(block)?;
        let fits = tree.kind(bind) == NodeKind::Let
            && tree.kind(block) == NodeKind::Block
            && tree.children(block).first() == Some(&bind)
            && tree.kind(handler) == NodeKind::Orelse
            && tree.children(handler).get(1) == Some(&block);
        fits.then(|| tree.children(handler)[0])
    }

    /// What a function may fail with, as its result type says: its
    /// declared one's failure type, or for a function literal without
    /// one, its type's ([`Settle::literal_type`]), from the function type
    /// its place wants, as Kotlin Temper types the literal, or where its
    /// place is a generic call's failure parameter, from its body. A
    /// generator, as an `async` block, can't fail, as
    /// `GeneratorResult<Y>` doesn't say `throws Bubble`; a `for … of`
    /// body isn't checked on its own.
    pub(super) fn allowed_failure(&mut self, f: NodeId) -> Allowed {
        let tree = &self.lib.tree;
        if tree.data(f) & core::FN_LOOP_BODY != 0 {
            return Allowed::Unchecked;
        }
        let result = match core::fn_parts(tree, f).1 {
            Some(out_type) if !self.is_throws(out_type) => return Allowed::Nothing,
            Some(out_type) => self.eval_result(out_type),
            None => match self.wanted_fn(f, false) {
                Ok(Some(_)) => match self.literal_type(f, false) {
                    Ok(t) => match *self.typing.types.get(t) {
                        TypeData::Fn { result, .. } => result,
                        _ => return Allowed::Unchecked,
                    },
                    Err(_) => return Allowed::Unchecked,
                },
                _ => return Allowed::Unchecked,
            },
        };
        match self.typing.types.fail(result) {
            Some(fail) => Allowed::Only(fail),
            None => Allowed::Nothing,
        }
    }

    /// Checks a class or interface that extends a failure type, or the
    /// root, against the rules for failure types (notes/typing.md,
    /// "Declared failures"): it has no type parameters; an interface is
    /// sealed, so every interface between a failure class and its root
    /// is; a class doesn't extend the root itself, which only a library's
    /// own roots, sealed interfaces, do; and it extends one failure type
    /// or the root, of its own library, so each failure type has one root,
    /// as backends make a failure type a class under one other.
    pub(super) fn check_failure_class(&mut self, class: NodeId) {
        let (name, supers, _) = core::class_parts(&self.lib.tree, class);
        let supers = supers.to_vec();
        let mut parents = Vec::new();
        let mut foreign = None;
        for sup in supers {
            let t = self.eval_type(sup);
            let symbol = match *self.typing.types.get(self.typing.types.base(t)) {
                TypeData::Class(symbol) if self.typing.types.is_failure_class(symbol) => symbol,
                _ if t == ERROR => {
                    parents.push((sup, t));
                    continue;
                }
                _ => continue,
            };
            parents.push((sup, t));
            let own = |n: Option<NodeId>| {
                n.map(|n| self.lib.library_root_at(self.lib.tree.span(n).start))
            };
            if own(self.scopes.symbol(symbol).decl) != own(Some(class)) {
                foreign = Some(sup);
            }
        }
        if parents.is_empty() {
            return;
        }
        let text = self.member_text(class).to_string();
        let tree = &self.lib.tree;
        let source = |n: NodeId| self.lib.source_text(self.lib.tree.span(n)).to_string();
        let mut problems = Vec::new();
        if !core::type_params(tree, class).is_empty() {
            problems.push(Message::GenericFailureType(text.clone()));
        }
        let data = tree.data(class);
        let is_interface = data & core::CLASS_INTERFACE != 0;
        if is_interface && data & core::CLASS_SEALED == 0 {
            problems.push(Message::UnsealedFailureInterface(text.clone()));
        }
        match parents[..] {
            [(a, _), (b, _), ..] => {
                problems.push(Message::TwoFailureParents(text, source(a), source(b)));
            }
            [(_, ERROR)] if !is_interface => {
                problems.push(Message::FailureClassExtendsRoot(text));
            }
            _ => {}
        }
        if let Some(sup) = foreign {
            problems.push(Message::ForeignFailureParent(source(sup)));
        }
        let span = self.lib.tree.span(name);
        for message in problems {
            self.lib.report(Level::Error, message, span);
        }
    }

    /// Whether a type expression is `T throws E`.
    pub(super) fn is_throws(&self, ty: NodeId) -> bool {
        self.lib.tree.kind(ty) == NodeKind::Call
            && self.lib.tree.children(ty).first().is_some_and(|&callee| {
                self.scopes.resolved_builtin(callee) == Some(Builtin::Function(Function::Throws))
            })
    }

    fn find_bubbles(&mut self, node: NodeId, found: &mut Vec<(NodeId, TypeId)>) {
        let tree = &self.lib.tree;
        let children = tree.children(node).to_vec();
        match (tree.kind(node), children.as_slice()) {
            (NodeKind::Fn, _) if tree.data(node) & core::FN_LOOP_BODY != 0 => {
                if let Some(body) = core::fn_parts(tree, node).2 {
                    self.find_bubbles(body, found);
                }
            }
            (NodeKind::Fn, _) => {}
            // What fails in the body goes to the fallback.
            (NodeKind::Orelse, [_, fallback]) => self.find_bubbles(*fallback, found),
            // Kotlin drops a branch that a constant condition rules out.
            (NodeKind::If, [condition, then, otherwise]) => {
                self.find_bubbles(*condition, found);
                match self.constant_bool(*condition) {
                    Some(true) => self.find_bubbles(*then, found),
                    Some(false) => self.find_bubbles(*otherwise, found),
                    None if self.may_fold(*condition, 0) => {}
                    None => {
                        self.find_bubbles(*then, found);
                        self.find_bubbles(*otherwise, found);
                    }
                }
            }
            (NodeKind::Fail, []) => found.push((node, BUBBLE)),
            // `throw x` fails with `x`, if it's a failure.
            (NodeKind::Fail, [value]) => {
                self.find_bubbles(*value, found);
                let t = self.known_type(*value);
                let is_failure = self.typing.types.is_subtype(t, ERROR) == Some(true);
                found.push((node, if is_failure { t } else { UNKNOWN }));
            }
            (NodeKind::Await, _) => {
                for child in children {
                    self.find_bubbles(child, found);
                }
                found.push((node, BUBBLE));
            }
            // Kotlin doesn't look into the arguments of an operator that
            // don't fit it.
            (NodeKind::Call, [callee, args @ ..]) => {
                if let Some(fail) = self.call_failure(node) {
                    found.push((node, fail));
                    return;
                }
                let unfit = self.unfit_operator_args(*callee, args);
                for &child in &children {
                    if !unfit.contains(&child) {
                        self.find_bubbles(child, found);
                    }
                }
            }
            _ => {
                for child in children {
                    self.find_bubbles(child, found);
                }
            }
        }
    }

    /// Whether a call may bubble, or fail otherwise, other than through
    /// its arguments.
    pub(super) fn call_bubbles(&mut self, call: NodeId) -> bool {
        self.call_failure(call).is_some()
    }

    /// What a call may fail with, other than through its arguments.
    fn call_failure(&mut self, call: NodeId) -> Option<TypeId> {
        let children = self.lib.tree.children(call).to_vec();
        let (&callee, args) = children.split_first()?;
        match self.scopes.resolved_builtin(callee) {
            Some(Builtin::Function(f @ (Function::Div | Function::Mod))) => {
                self.division_bubbles(call, f, args).then_some(BUBBLE)
            }
            Some(Builtin::Function(Function::As)) => self.cast_bubbles(args).then_some(BUBBLE),
            Some(Builtin::Function(Function::New)) => self.new_failure(args),
            // `a[i]` calls `a.get(i)`.
            Some(Builtin::Function(Function::Index)) if !args.is_empty() => {
                let t = self.known_index_callee_type(args[0]);
                self.fn_call_failure(call, t, &args[1..])
            }
            Some(_) => None,
            // A generic function's as the call solves it, so it fails as
            // its failure parameters' solutions do, its function literals'
            // from their bodies.
            None => {
                let t = match self.solve_fully(call, false) {
                    Ok(Some(solved)) => self.solved_sig(&solved),
                    _ => self.known_callee_type(callee),
                };
                self.fn_call_failure(call, t, args)
            }
        }
    }

    /// What a call of a function of type `t` with `args` may fail with.
    fn fn_call_failure(&mut self, call: NodeId, t: TypeId, args: &[NodeId]) -> Option<TypeId> {
        let TypeData::Fn { params, result } = self.typing.types.get(t) else {
            return None;
        };
        let fail = self.typing.types.fail(*result)?;
        // Kotlin can't pick a callee for the wrong number of arguments,
        // nor fold a call with an argument that doesn't fit.
        let params = params.clone();
        let required = params.iter().filter(|p| !p.optional).count();
        if !(required..=params.len()).contains(&args.len()) {
            return None;
        }
        // Kotlin might fold a call that bubbles; it has no declared
        // failures.
        if fail != BUBBLE {
            return Some(fail);
        }
        let misfit = (args.iter().zip(&params)).any(|(&arg, param)| {
            let t = self.known_type(arg);
            self.is_mismatch(t, param.ty)
        });
        (misfit || !self.may_fold(call, 0)).then_some(fail)
    }

    /// What `new C(…)` may fail with: what `C`'s constructor says it
    /// `throws`, if it takes that many arguments. Kotlin reports it
    /// whatever the arguments are, constants too.
    fn new_failure(&mut self, args: &[NodeId]) -> Option<TypeId> {
        let tree = &self.lib.tree;
        let (&made, args) = args.split_first()?;
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
        let class = (self.scopes.resolved(base)).and_then(|b| self.type_decl(b))?;
        let (_, _, members) = core::class_parts(tree, class);
        let constructor = members.iter().find(|&&m| {
            tree.data(m) & core::ROLE_MASK == core::ROLE_CONSTRUCTOR
                && tree.data(m) & core::MEMBER_STATIC == 0
        });
        let f = constructor.and_then(|&c| core::let_parts(tree, c).2)?;
        if tree.kind(f) != NodeKind::Fn {
            return None;
        }
        let (params, out_type, _) = core::fn_parts(tree, f);
        let out_type = out_type.filter(|&t| self.is_throws(t))?;
        let optional = |p: NodeId| core::let_parts(tree, p).2.is_some();
        let required = params.iter().filter(|&&p| !optional(p)).count();
        if !(required..=params.len()).contains(&args.len()) {
            return None;
        }
        let result = self.eval_result(out_type);
        self.typing.types.fail(result)
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
