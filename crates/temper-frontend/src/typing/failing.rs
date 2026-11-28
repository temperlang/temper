//! What may fail where it can't: each call that may fail, `throw`, and
//! `await` in the body of a function whose return type doesn't let it,
//! other than in an `orelse`'s body. A `for … of` body counts as part of
//! the function around it; other functions are checked on their own, if
//! they declare a return type, or, for a function literal without one, if
//! its place wants a function type, which gives it its own.
//!
//! What may fail: `throw`, a call of a function whose type `throws` (with
//! error parameters as the call solves them), and `await` of a broken
//! promise, which throws core's `BrokenPromise`. (`x as T` that fails and
//! `/` or `%` of integers by zero panic; floats' give IEEE 754's
//! infinities and NaN.) As Kotlin Temper drops a branch of an `if` whose
//! condition is `true` or `false`, that branch's errors aren't
//! reported.

use crate::builtins::{Builtin, Constant, Function};
use crate::core;
use crate::library::{Level, Library, Message};
use crate::scope::{Scopes, SymbolKind};
use crate::settle::{Fact, Settle};
use crate::types::{ERROR, TypeData, TypeId, UNKNOWN};
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
/// ([`Settle::allowed_error`]).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum Allowed {
    /// Not checked on its own: a `for … of` body, part of the function
    /// around it, or a function literal whose type isn't worked out.
    Unchecked,
    /// It can't fail.
    Nothing,
    /// It may fail with this: a declared error type, or an error
    /// parameter.
    Only(TypeId),
}

impl Settle {
    /// Reports what may fail in a function, in its parameters' defaults
    /// and its body, but its result type doesn't let it
    /// ([`Settle::allowed_error`]): any error where it can't fail,
    /// and otherwise one that isn't of its error type (notes/typing.md,
    /// "Declared errors").
    pub(super) fn check_errors(&mut self, f: NodeId) {
        match self.allowed_error(f) {
            Allowed::Unchecked => {}
            Allowed::Nothing => match self.unfailing_place(f) {
                Some(message) => {
                    if !self.fails_in(f).is_empty() {
                        let span = self.lib.tree.span(f);
                        self.lib.report(Level::Error, message, span);
                    }
                }
                None => self.check_no_errors(f),
            },
            Allowed::Only(allowed) => {
                for (node, fail) in self.fails_in(f) {
                    if let Some(message) = self.misfit_error(fail, allowed) {
                        let span = self.lib.tree.span(node);
                        self.lib.report(Level::Error, message, span);
                    }
                }
            }
        }
    }

    /// Reports what may fail in a function that can't, in its parameters'
    /// defaults and its body: "Cannot throw …". What fails with what's
    /// unknown is reported where that's worked out: a `throw` of what
    /// isn't an error, or a callee's `throws` of what isn't one.
    pub(super) fn check_no_errors(&mut self, f: NodeId) {
        for (node, fail) in self.fails_in(f) {
            let message = match fail {
                UNKNOWN => continue,
                ERROR => Message::UnhandledRootError,
                _ => Message::UndeclaredError(self.show(fail)),
            };
            let span = self.lib.tree.span(node);
            self.lib.report(Level::Error, message, span);
        }
    }

    /// Why an error of type `fail` can't pass through a function that
    /// may fail with `allowed`, if it can't.
    fn misfit_error(&mut self, fail: TypeId, allowed: TypeId) -> Option<Message> {
        if fail == UNKNOWN || allowed == UNKNOWN || fail == allowed || allowed == ERROR {
            return None;
        }
        let misfit = self.typing.types.is_subtype(fail, allowed) == Some(false);
        misfit.then(|| Message::ErrorMismatch(self.show(fail), self.show(allowed)))
    }

    /// What may fail in a function's parameters' defaults and body, each
    /// with what it may fail with ([`Settle::error_of`]).
    pub(super) fn fails_in(&mut self, f: NodeId) -> Vec<(NodeId, TypeId)> {
        let (params, _, body) = core::fn_parts(&self.lib.tree, f);
        let mut found = Vec::new();
        for param in params {
            if self.lib.tree.kind(param) == NodeKind::Let {
                if let Some(default) = core::let_parts(&self.lib.tree, param).2 {
                    self.find_errors(default, &mut found);
                }
            }
        }
        if let Some(body) = body {
            self.find_errors(body, &mut found);
        }
        found
    }

    /// What may fail in an expression and isn't handled in it, as a
    /// `catch`'s operand: each place, with what it may fail with.
    pub(super) fn errors_under(&mut self, node: NodeId) -> Vec<(NodeId, TypeId)> {
        let mut found = Vec::new();
        self.find_errors(node, &mut found);
        found
    }

    /// One type for what several places may fail with: declared
    /// errors' join, or the root where they
    /// have no closer common type; unknown if one is; `None` for none.
    pub(super) fn join_errors(&mut self, fails: &[(NodeId, TypeId)]) -> Option<TypeId> {
        let mut joined: Option<TypeId> = None;
        for &(_, fail) in fails {
            joined = Some(match joined {
                None => fail,
                Some(so_far) => self.typing.types.join_errors(so_far, fail),
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
    /// from its body ([`Settle::fails_in`], [`Settle::join_errors`]),
    /// if anything. Before checking, it waits for the types of its body's
    /// expressions and callees, which the search reads.
    pub(super) fn literal_error(
        &mut self,
        f: NodeId,
        forced: bool,
    ) -> Result<Option<TypeId>, Fact> {
        if let Some(&fail) = self.typing.literal_errors.get(&f) {
            return Ok(fail);
        }
        if let Some(body) = core::fn_parts(&self.lib.tree, f).2 {
            self.wait_for_failures(body, forced)?;
        }
        let failures = self.fails_in(f);
        let fail = self.join_errors(&failures);
        if !forced {
            self.typing.literal_errors.insert(f, fail);
        }
        Ok(fail)
    }

    /// The error a `catch (e)` binds, the hidden `caught()` call in the
    /// `let e = caught();` that starts its block: what the `Orelse`'s
    /// body may fail with ([`Settle::join_errors`]); unknown where
    /// nothing may fail.
    pub(super) fn caught_type(&mut self, caught: NodeId, forced: bool) -> Result<TypeId, Fact> {
        let Some(body) = self.caught_from(caught) else {
            return Ok(UNKNOWN);
        };
        self.wait_for_failures(body, forced)?;
        let failures = self.errors_under(body);
        Ok(self.join_errors(&failures).unwrap_or(UNKNOWN))
    }

    /// The operand of the `Orelse` whose error a `caught()` call binds:
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
    /// declared one's error type, or for a function literal without
    /// one, its type's ([`Settle::literal_type`]), from the function type
    /// its place wants, as Kotlin Temper types the literal, or where its
    /// place is a generic call's error parameter, from its body. A
    /// generator, as an `async` block, can't fail unless its result says
    /// `throws`; a `for … of` body isn't checked on its own.
    pub(super) fn allowed_error(&mut self, f: NodeId) -> Allowed {
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

    /// Checks a class or interface that extends an error type, or the
    /// root, against the rules for error types (notes/typing.md,
    /// "Declared errors"): it has no type parameters; an interface is
    /// sealed, so every interface between an error class and its root
    /// is; a class doesn't extend the root itself, which only a library's
    /// own roots, sealed interfaces, do; and it extends one error type
    /// or the root, of its own library, so each error type has one root,
    /// as backends make an error type a class under one other.
    pub(super) fn check_error_class(&mut self, class: NodeId) {
        let (name, supers, _) = core::class_parts(&self.lib.tree, class);
        let supers = supers.to_vec();
        let mut parents = Vec::new();
        let mut foreign = None;
        for sup in supers {
            let t = self.eval_type(sup);
            let symbol = match *self.typing.types.get(self.typing.types.base(t)) {
                TypeData::Class(symbol) if self.typing.types.is_error_class(symbol) => symbol,
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
            problems.push(Message::GenericErrorType(text.clone()));
        }
        let data = tree.data(class);
        let is_interface = data & core::CLASS_INTERFACE != 0;
        if is_interface && data & core::CLASS_SEALED == 0 {
            problems.push(Message::UnsealedErrorInterface(text.clone()));
        }
        match parents[..] {
            [(a, _), (b, _), ..] => {
                problems.push(Message::TwoErrorParents(text, source(a), source(b)));
            }
            [(_, ERROR)] if !is_interface => {
                problems.push(Message::ErrorClassExtendsRoot(text));
            }
            _ => {}
        }
        if let Some(sup) = foreign {
            problems.push(Message::ForeignErrorParent(source(sup)));
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

    fn find_errors(&mut self, node: NodeId, found: &mut Vec<(NodeId, TypeId)>) {
        let tree = &self.lib.tree;
        let children = tree.children(node).to_vec();
        match (tree.kind(node), children.as_slice()) {
            (NodeKind::Fn, _) if tree.data(node) & core::FN_LOOP_BODY != 0 => {
                if let Some(body) = core::fn_parts(tree, node).2 {
                    self.find_errors(body, found);
                }
            }
            (NodeKind::Fn, _) => {}
            // What fails in the body goes to the fallback.
            (NodeKind::Orelse, [_, fallback]) => self.find_errors(*fallback, found),
            // Kotlin drops a branch that a constant condition rules out.
            (NodeKind::If, [condition, then, otherwise]) => {
                self.find_errors(*condition, found);
                match self.constant_bool(*condition) {
                    Some(true) => self.find_errors(*then, found),
                    Some(false) => self.find_errors(*otherwise, found),
                    None => {
                        self.find_errors(*then, found);
                        self.find_errors(*otherwise, found);
                    }
                }
            }
            // `throw x` fails with `x`, if it's an error.
            (NodeKind::Fail, [value]) => {
                self.find_errors(*value, found);
                let t = self.known_type(*value);
                let is_error = self.typing.types.is_subtype(t, ERROR) == Some(true);
                found.push((node, if is_error { t } else { UNKNOWN }));
            }
            // `await` of a broken promise throws core's `BrokenPromise`.
            (NodeKind::Await, _) => {
                for child in children {
                    self.find_errors(child, found);
                }
                found.push((node, self.typing.types.broken_promise()));
            }
            _ => {
                if tree.kind(node) == NodeKind::Call {
                    if let Some(fail) = self.call_error(node) {
                        found.push((node, fail));
                        return;
                    }
                }
                for child in children {
                    self.find_errors(child, found);
                }
            }
        }
    }

    /// Whether a call may fail, other than through its arguments.
    pub(super) fn call_fails(&mut self, call: NodeId) -> bool {
        self.call_error(call).is_some()
    }

    /// What a call may fail with, other than through its arguments.
    fn call_error(&mut self, call: NodeId) -> Option<TypeId> {
        let children = self.lib.tree.children(call).to_vec();
        let (&callee, args) = children.split_first()?;
        match self.scopes.resolved_builtin(callee) {
            Some(Builtin::Function(Function::New)) => self.new_error(args),
            // `a[i]` calls `a.get(i)`.
            Some(Builtin::Function(Function::Index)) if !args.is_empty() => {
                let t = self.known_index_callee_type(args[0]);
                self.fn_call_error(t, &args[1..])
            }
            Some(_) => None,
            // A generic function's as the call solves it, so it fails as
            // its error parameters' solutions do, its function literals'
            // from their bodies.
            None => {
                let t = match self.solve_fully(call, false) {
                    Ok(Some(solved)) => self.solved_sig(&solved),
                    _ => self.known_callee_type(callee),
                };
                self.fn_call_error(t, args)
            }
        }
    }

    /// What a call of a function of type `t` with `args` may fail with.
    fn fn_call_error(&mut self, t: TypeId, args: &[NodeId]) -> Option<TypeId> {
        let TypeData::Fn { params, result } = self.typing.types.get(t) else {
            return None;
        };
        let fail = self.typing.types.fail(*result)?;
        // Kotlin can't pick a callee for the wrong number of arguments.
        let required = params.iter().filter(|p| !p.optional).count();
        (required..=params.len())
            .contains(&args.len())
            .then_some(fail)
    }

    /// What `new C(…)` may fail with: what `C`'s constructor says it
    /// `throws`, if it takes that many arguments. Kotlin reports it
    /// whatever the arguments are, constants too.
    fn new_error(&mut self, args: &[NodeId]) -> Option<TypeId> {
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
