//! Nullable values, `orelse`, and `bubble()`.
//!
//! `T?` is `Option<T>`, as Kotlin's (`String?` parameters are
//! `Option<impl ToArcString>`, mapped to `Option<Arc<String>>`, as
//! Kotlin's are): `null` is `None`, a value where a nullable one is
//! wanted `Some(x)`, a null check `x.is_none()` (`x.is_some()` negated),
//! and a value that a null check narrowed `x.unwrap()`, cloned first if
//! its place wants one of its own, else borrowed (`x.as_ref().unwrap()`);
//! an `if` on a null check of a local that reads it only narrowed is
//! `if let Some(x) = &x`, and one picking it or a fallback
//! `x.unwrap_or(d)`, as clippy wants.
//! `==` with a side that may be null compares the `Option`s, which Rust
//! does by value, or `x.is_none()` against `null`; `Float64`'s, which
//! orders NaN as Temper does, zips them.
//!
//! `orelse` runs its body where it may bubble: a single bubbling call
//! as the `Result` it returns, else a closure returning one, and takes
//! the fallback with `unwrap_or` (a literal or a name) or
//! `unwrap_or_else`; standing alone, `if body.is_err() { fallback }`.
//! Kotlin writes labeled blocks with a result local for each. A fallback
//! that bubbles or jumps, which can't run in a closure, follows `if let
//! Ok(v) = attempt { v } else`, the attempt held in a local first where
//! it takes lines.

use super::{R, Translator, Want, not_yet};
use crate::ops::{self, Spelling};
use crate::tree::{Bin, Block, Expr, Stmt, Unary};
use temper_tmpl::analysis::walk;
use temper_tmpl::{DeclId, Flags, Node, NodeId, TypeId};

impl Translator<'_> {
    /// A nullable value narrowed to its type.
    pub(crate) fn non_null(&mut self, id: NodeId, value: NodeId, want: Want) -> R<Expr> {
        let ty = self.p.nodes.ty(id);
        if let Node::Local { local } = self.p.nodes.get(value) {
            if let Some(&copy) = self.narrowed.get(&local) {
                // Bound by `if let`: the value, or a reference to it.
                let name = Expr::path(self.name(local));
                return Ok(match want {
                    Want::Owned | Want::IntoArc if !copy => name.method("clone", Vec::new()),
                    _ => name,
                });
            }
        }
        if self.is_copy(ty) {
            return Ok(self
                .expr(value, Want::Borrowed)?
                .method("unwrap", Vec::new()));
        }
        Ok(match want {
            Want::Owned | Want::IntoArc => {
                self.expr(value, Want::Owned)?.method("unwrap", Vec::new())
            }
            Want::Borrowed | Want::Display => self
                .expr(value, Want::Borrowed)?
                .method("as_ref", Vec::new())
                .method("unwrap", Vec::new()),
        })
    }

    /// An `Omitted` argument for an optional parameter of the program's
    /// own, typed `T?`, as Kotlin's: `None`, typed for an `Option<impl
    /// ToArcString>`, whose type Rust can't infer.
    pub(crate) fn omitted(&mut self, param: Option<TypeId>) -> R<Expr> {
        match param {
            Some(t) if self.is_nullable_string(t) => {
                self.uses.insert("std::sync::Arc");
                Ok(Expr::path("None::<Arc<String>>"))
            }
            Some(t) if self.p.types.is_nullable(t) => Ok(Expr::path("None")),
            _ => not_yet("an argument left out of a parameter that isn't nullable"),
        }
    }

    /// `coalesce(x, d)`, a parameter's default: `x.unwrap_or(d)` where
    /// `d` is a literal or a name, `x.unwrap_or_else(|| d)` where it may
    /// run in a closure, else `if let Some(x) = x { x } else { d }`; or
    /// for a nullable default, `x.or(d)` and so on, and `x` alone for
    /// null. A parameter's value is moved, its only read.
    pub(crate) fn coalesce(&mut self, left: NodeId, right: NodeId) -> R<Expr> {
        let p = self.p;
        let x = match p.nodes.get(left) {
            Node::Local { local } if p.decls.get(local).kind == temper_tmpl::DeclKind::Param => {
                Expr::path(self.name(local))
            }
            _ => self.expr(left, Want::Owned)?,
        };
        if matches!(p.nodes.get(right), Node::Null {}) {
            return Ok(x);
        }
        let d = self.expr(right, Want::Owned)?;
        let nullable = p.types.is_nullable(p.nodes.ty(right));
        let (eager, lazy) = if nullable {
            ("or", "or_else")
        } else {
            ("unwrap_or", "unwrap_or_else")
        };
        if simple(&d) {
            return Ok(x.method(eager, vec![d]));
        }
        if closure_safe(&d) {
            // A call of nothing is a function's name, as clippy wants.
            let f = match d {
                Expr::Call(f, args) if args.is_empty() && matches!(*f, Expr::Path(_)) => *f,
                d => Expr::Closure(Vec::new(), Block::of(d)),
            };
            return Ok(x.method(lazy, vec![f]));
        }
        let Expr::Path(name) = &x else {
            return not_yet("a coalesced value that isn't a name");
        };
        let value = if nullable {
            Expr::path("Some").call(vec![Expr::path(name)])
        } else {
            Expr::path(name)
        };
        Ok(Expr::If(
            Box::new(Expr::path(format!("let Some({name}) = {name}"))),
            Block::of(value),
            Block::of(d),
        ))
    }

    /// `left == right`, either maybe null.
    pub(crate) fn eq_nullable(
        &mut self,
        op: temper_tmpl::ops::Op,
        left: NodeId,
        right: NodeId,
    ) -> R<Expr> {
        let p = self.p;
        let null = |n: NodeId| matches!(p.nodes.get(n), Node::Null {});
        if null(left) && null(right) {
            return Ok(Expr::path("true"));
        }
        if null(right) {
            return Ok(self
                .expr(left, Want::Borrowed)?
                .method("is_none", Vec::new()));
        }
        if null(left) {
            return Ok(self
                .expr(right, Want::Borrowed)?
                .method("is_none", Vec::new()));
        }
        match ops::spelling(op) {
            Some(Spelling::Bin(Bin::Eq) | Spelling::StrBin(Bin::Eq)) => {
                let side = |t: &mut Self, n: NodeId| match p.nodes.get(n) {
                    Node::AsNullable { .. } => t.expr(n, Want::Owned),
                    _ => t.expr(n, Want::Borrowed),
                };
                let a = side(self, left)?;
                let b = side(self, right)?;
                Ok(Expr::bin(Bin::Eq, a, b))
            }
            Some(Spelling::FloatCmp(Some(Bin::Eq))) => {
                // `a.zip(b).map_or(a.is_none() && b.is_none(), |(a, b)| …)`,
                // for names, which may be read twice.
                let plain = |n: NodeId| matches!(p.nodes.get(n), Node::Local { .. });
                if !plain(left) || !plain(right) {
                    return not_yet("Float64's == of nullable values that aren't locals");
                }
                let a = self.expr(left, Want::Borrowed)?;
                let b = self.expr(right, Want::Borrowed)?;
                let both = Expr::bin(
                    Bin::And,
                    a.clone().method("is_none", Vec::new()),
                    b.clone().method("is_none", Vec::new()),
                );
                let eq = ops::apply(
                    Spelling::FloatCmp(Some(Bin::Eq)),
                    vec![Expr::path("a"), Expr::path("b")],
                )
                .unwrap_or_else(|| Expr::path("false"));
                let f = Expr::Closure(vec!["(a, b)".into()], Block::of(eq));
                Ok(a.method("zip", vec![b]).method("map_or", vec![both, f]))
            }
            _ => not_yet("== of nullable values of this type"),
        }
    }

    /// Whether anything under `id`, not `id` itself, may bubble, or
    /// returns, outside a nested `orelse`'s body.
    fn bubbles_below(&self, id: NodeId) -> bool {
        let p = self.p;
        let mut found = false;
        for &kid in p.nodes.kids(id) {
            walk(p, kid, &mut |n| {
                found |= p.nodes.flags(n).contains(Flags::MAY_BUBBLE)
                    || matches!(p.nodes.get(n), Node::Return { .. } | Node::Error { .. });
            });
        }
        found
    }

    /// Whether an `orelse`'s body may fail to its fallback: something in
    /// it bubbles, outside an `orelse` of its own and function literals.
    fn may_fail(&self, id: NodeId) -> bool {
        let p = self.p;
        if p.nodes.flags(id).contains(Flags::MAY_BUBBLE)
            || matches!(p.nodes.get(id), Node::Bubble { .. } | Node::Error { .. })
        {
            return true;
        }
        match p.nodes.get(id) {
            Node::OrElse { fallback, .. } => self.may_fail(fallback),
            Node::FunctionLiteral { .. } => false,
            _ => p.nodes.kids(id).iter().any(|&k| self.may_fail(k)),
        }
    }

    /// Whether code may bubble, return, or jump anywhere in it, so it
    /// can't run in a closure.
    fn escapes(&self, id: NodeId) -> bool {
        let p = self.p;
        let mut found = false;
        walk(p, id, &mut |n| {
            found |= p.nodes.flags(n).contains(Flags::MAY_BUBBLE)
                || matches!(
                    p.nodes.get(n),
                    Node::Return { .. }
                        | Node::Error { .. }
                        | Node::Break { .. }
                        | Node::Continue { .. }
                );
        });
        found
    }

    /// An `orelse`'s body as a `Result`.
    fn attempt(&mut self, body: NodeId) -> R<Expr> {
        let outer = std::mem::replace(&mut self.bubbles, true);
        let orelse = std::mem::take(&mut self.orelse);
        let result = self.attempt_inner(body);
        self.bubbles = outer;
        self.orelse = orelse;
        result
    }

    /// Whether `body` returns, breaks, or continues, which a closure
    /// around it can't.
    fn jumps(&self, body: NodeId) -> bool {
        // Loops in it, whose own jumps stay in it.
        let mut inner = Vec::new();
        self.jumps_from(body, &mut inner)
    }

    fn jumps_from(&self, id: NodeId, inner: &mut Vec<DeclId>) -> bool {
        let p = self.p;
        match p.nodes.get(id) {
            // A function literal's jumps are its own.
            Node::FunctionLiteral { .. } => return false,
            Node::Return { .. } => return true,
            Node::Break { label } | Node::Continue { label } => return !inner.contains(&label),
            Node::While { label, .. } | Node::DoWhile { label, .. } => inner.push(label),
            _ => {}
        }
        p.nodes.kids(id).iter().any(|&k| self.jumps_from(k, inner))
    }

    /// Whether code has an `Error`, outside function literals.
    fn has_error(&self, id: NodeId) -> bool {
        let p = self.p;
        match p.nodes.get(id) {
            Node::Error { .. } => true,
            Node::FunctionLiteral { .. } => false,
            _ => p.nodes.kids(id).iter().any(|&k| self.has_error(k)),
        }
    }

    /// A Void body's statements: a block's, its value too unless it's
    /// `void`.
    fn body_stmts(&mut self, body: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        match p.nodes.get(body) {
            Node::BlockValue { stmts, value } => {
                for &s in p.nodes.list(stmts) {
                    self.stmt(s, out)?;
                }
                match p.nodes.get(value) {
                    Node::VoidLit {} => {}
                    // `break 'orelse;`, which ends the body.
                    Node::Error { .. } | Node::Bubble { .. } if !self.orelse.is_empty() => {
                        out.push(Stmt::Break(self.orelse.last().cloned()));
                    }
                    _ => {
                        let v = self.expr(value, Want::Owned)?;
                        out.push(Stmt::Expr(v));
                    }
                }
                Ok(())
            }
            Node::Error { .. } | Node::Bubble { .. } if !self.orelse.is_empty() => {
                out.push(Stmt::Break(self.orelse.last().cloned()));
                Ok(())
            }
            _ => self.stmt(body, out),
        }
    }

    /// A statement `orelse` whose body jumps, as Kotlin's: the body in
    /// place, in a labeled block that a bubble breaks out of to the
    /// fallback, and that a body run to its end skips the fallback from:
    /// `'ok: { 'orelse: { …; break 'ok; } fallback; }`. A `?` in the body
    /// (outside its closures) is `if let Ok(v) = e { v } else { break
    /// 'orelse; }`.
    fn or_else_in_place(&mut self, body: NodeId, fallback: NodeId, out: &mut Vec<Stmt>) -> R<()> {
        let (orelse, ok) = match self.orelse.len() {
            0 => ("'orelse".to_string(), "'ok".to_string()),
            d => (format!("'orelse{}", d + 1), format!("'ok{}", d + 1)),
        };
        if !self.may_fail(body) {
            // Its fallback never runs: std's `parseJsonNumber`'s `do { … }
            // orelse panic()`, whose one failure has an `orelse` of its own.
            return self.body_stmts(body, out);
        }
        self.orelse.push(orelse.clone());
        let mut stmts = Vec::new();
        let result = self.body_stmts(body, &mut stmts);
        self.orelse.pop();
        result?;
        if !crate::tree::retarget_tries(&mut stmts, &orelse) {
            return not_yet("a bubble too long for a line in an orelse's body that jumps");
        }
        // A body that ends in a `return` on every path (std's
        // `parseJsonObject`) needs no `break` past the fallback, nor the
        // block it would break out of.
        let diverges = crate::tree::diverges(&stmts);
        if !diverges {
            stmts.push(Stmt::Break(Some(ok.clone())));
        }
        let mut fallback_stmts = vec![Stmt::Labeled {
            label: orelse,
            body: Block::new(stmts, None),
        }];
        let f = self.expr(fallback, Want::Owned)?;
        fallback_stmts.push(Stmt::Expr(f));
        if diverges {
            out.append(&mut fallback_stmts);
        } else {
            out.push(Stmt::Labeled {
                label: ok,
                body: Block::new(fallback_stmts, None),
            });
        }
        Ok(())
    }

    fn attempt_inner(&mut self, body: NodeId) -> R<Expr> {
        let p = self.p;
        if p.nodes.flags(body).contains(Flags::MAY_BUBBLE) && !self.bubbles_below(body) {
            if let Expr::Try(inner) = self.expr(body, Want::Owned)? {
                return Ok(*inner);
            }
        }
        if self.jumps(body) {
            return not_yet("a jump out of an orelse's body, as a value");
        }
        let ty = p.nodes.ty(body);
        let mut stmts = Vec::new();
        // Whether a statement before the value may bubble, with a `?`.
        let mut stmts_bubble = false;
        let value = match p.nodes.get(body) {
            Node::BlockValue { stmts: kids, value } => {
                for &s in p.nodes.list(kids) {
                    walk(p, s, &mut |n| {
                        stmts_bubble |= p.nodes.flags(n).contains(Flags::MAY_BUBBLE);
                    });
                    self.stmt(s, &mut stmts)?;
                }
                value
            }
            _ => body,
        };
        let v = if self.p.nodes.ty(value) == TypeId::VOID && ty == TypeId::VOID {
            // `void` itself is no statement (clippy's `no_effect`).
            if !matches!(p.nodes.get(value), Node::VoidLit {}) {
                let v = self.expr(value, Want::Owned)?;
                stmts.push(Stmt::Expr(v));
            }
            Expr::path("()")
        } else {
            self.expr(value, Want::Owned)?
        };
        // `Ok(e?)` is `e`, after statements too (clippy's
        // `needless_question_mark`); `e`'s type gives the closure's. With
        // no other `?`, a block will do (clippy's `redundant_closure_call`).
        // A `?` inside `e` itself, as `div(to_int(x)? as f64, 100.0)`,
        // needs the closure too, or it would be the function's.
        let inner_bubble = self.bubbles_below(value);
        let tail = match v {
            Expr::Try(e) if stmts.is_empty() && !inner_bubble => return Ok(*e),
            Expr::Try(e) if !stmts_bubble && !inner_bubble => {
                return Ok(Expr::Block(Block::new(stmts, Some(*e))));
            }
            Expr::Try(e) => *e,
            v => Expr::path("Ok::<_, temper_core::Error>").call(vec![v]),
        };
        let closure = Expr::Closure(Vec::new(), Block::new(stmts, Some(tail)));
        Ok(closure.call(Vec::new()))
    }

    /// `body orelse fallback` as a value.
    pub(crate) fn or_else(&mut self, ty: TypeId, body: NodeId, fallback: NodeId) -> R<Expr> {
        if ty == TypeId::VOID {
            return not_yet("an orelse of type Void as a value");
        }
        if matches!(
            self.p.nodes.get(body),
            Node::Bubble { .. } | Node::Error { .. }
        ) {
            return self.expr(fallback, Want::Owned);
        }
        let attempt = self.attempt(body)?;
        let f = self.expr(fallback, Want::Owned)?;
        if self.escapes(fallback) {
            // `if let Ok(v) = attempt { v } else { fallback }`, where the
            // fallback's `?` or jump is the function's, not a closure's
            // (std's `JsonNumericToken.asInt32`).
            let mut els = match f {
                Expr::Block(b) => b,
                f => Block::of(f),
            };
            // A `return` ending it is a statement, as rustfmt writes it.
            if let Some(Expr::Return(v)) = els.tail.as_deref() {
                els.stmts.push(Stmt::Return(v.as_deref().cloned()));
                els.tail = None;
            }
            if let Some(made) = crate::tree::if_ok(&attempt, els.clone()) {
                return Ok(made);
            }
            // An attempt on lines, which the `let` can't take, is held
            // first, under a name no declaration has: `{ let attempt = …;
            // if let Ok(v) = attempt { v } else { fallback } }`.
            let held = self.free_name("attempt");
            let Some(made) = crate::tree::if_ok(&Expr::path(held.as_str()), els) else {
                return not_yet("an orelse whose fallback bubbles or jumps, held");
            };
            let stmts = vec![Stmt::Let {
                name: held,
                mutable: false,
                ty: None,
                value: Some(attempt),
            }];
            return Ok(Expr::Block(Block::new(stmts, Some(made))));
        }
        Ok(if simple(&f) {
            attempt.method("unwrap_or", vec![f])
        } else {
            let f = Expr::Closure(vec!["_".into()], Block::of(f));
            attempt.method("unwrap_or_else", vec![f])
        })
    }

    /// `body orelse fallback` standing alone: `if body.is_err() {
    /// fallback }`.
    pub(crate) fn or_else_stmt(
        &mut self,
        body: NodeId,
        fallback: NodeId,
        out: &mut Vec<Stmt>,
    ) -> R<()> {
        if matches!(
            self.p.nodes.get(body),
            Node::Bubble { .. } | Node::Error { .. }
        ) {
            let f = self.expr(fallback, Want::Owned)?;
            out.push(Stmt::Expr(f));
            return Ok(());
        }
        // An error fails in place too, as `break 'orelse`, which nothing
        // after it in a closure would follow (rustc's `unreachable_code`).
        if self.jumps(body) || self.has_error(body) {
            return self.or_else_in_place(body, fallback, out);
        }
        let attempt = self.attempt(body)?;
        // A closure called in place that can't fail, as a state machine's
        // step with nothing that bubbles left, is its statements (clippy's
        // `redundant_closure_call`).
        if let Expr::Call(f, args) = &attempt {
            if let (Expr::Closure(params, block), true) = (&**f, args.is_empty()) {
                let unit = Expr::path("Ok::<_, temper_core::Error>").call(vec![Expr::path("()")]);
                if params.is_empty()
                    && block.tail.as_deref() == Some(&unit)
                    && !crate::tree::leave_early(&block.stmts)
                {
                    out.extend(block.stmts.iter().cloned());
                    return Ok(());
                }
            }
        }
        let mut then = Vec::new();
        match self.p.nodes.get(fallback) {
            Node::VoidLit {} => {
                out.push(Stmt::Let {
                    name: "_".into(),
                    mutable: false,
                    ty: None,
                    value: Some(attempt),
                });
                return Ok(());
            }
            _ => {
                let f = self.expr(fallback, Want::Owned)?;
                then.push(Stmt::Expr(f));
            }
        }
        out.push(Stmt::If {
            cond: attempt.method("is_err", Vec::new()),
            then: Block::new(then, None),
            els: None,
        });
        Ok(())
    }

    /// For an `if` on a null check of a local, `if (x == null) … else …`
    /// or `if (x != null) …`, whose branch where it isn't null reads it
    /// only narrowed and doesn't assign it: the local, and whether that
    /// branch is `then`. It's written `if let Some(x) = &x`, as clippy
    /// wants, rather than `x.unwrap()` after `x.is_none()`.
    pub(crate) fn if_let(
        &self,
        cond: NodeId,
        then: NodeId,
        els: Option<NodeId>,
    ) -> Option<(DeclId, bool)> {
        let p = self.p;
        let checked = |n: NodeId| match p.nodes.get(n) {
            Node::IsNull { value } => match p.nodes.get(value) {
                Node::Local { local } => Some(local),
                _ => None,
            },
            _ => None,
        };
        // `is StringIndex` on a string index option, an `Option<usize>`,
        // checks that it isn't `None` as a null check does, as `is
        // ValueResult` does on a generator's step's result.
        let is_index = |n: NodeId| match p.nodes.get(n) {
            Node::Is { ty, value }
                if self.string_index(ty) == Some(true) || self.step_result(ty) == Some(true) =>
            {
                match p.nodes.get(value) {
                    Node::Local { local } => Some(local),
                    _ => None,
                }
            }
            _ => None,
        };
        let (local, branch, narrowed_then) = match p.nodes.get(cond) {
            Node::IsNull { .. } => (checked(cond)?, els?, false),
            Node::Is { .. } => (is_index(cond)?, then, true),
            Node::Op { op, args, .. } if ops::spelling(op) == Some(Spelling::Unary(Unary::Not)) => {
                let [arg] = p.nodes.list(args) else {
                    return None;
                };
                match is_index(*arg) {
                    Some(local) => (local, els?, false),
                    None => (checked(*arg)?, then, true),
                }
            }
            _ => return None,
        };
        // A cell, as a state machine's locals are, is read under its lock.
        if self.narrowed.contains_key(&local) || self.is_cell(local) {
            return None;
        }
        let (mut reads, mut narrowed, mut assigned) = (0, 0, false);
        walk(p, branch, &mut |n| match p.nodes.get(n) {
            Node::Local { local: l } if l == local => reads += 1,
            Node::NonNull { value } | Node::Downcast { value } => {
                if matches!(p.nodes.get(value), Node::Local { local: l } if l == local) {
                    narrowed += 1;
                }
            }
            Node::Assign { local: l, .. } if l == local => assigned = true,
            _ => {}
        });
        (reads == narrowed && narrowed > 0 && !assigned).then_some((local, narrowed_then))
    }

    /// `let Some(x) = &x`, or `= x` for a `Copy` value.
    pub(crate) fn let_some(&self, local: DeclId) -> Expr {
        let name = self.name(local);
        let by = if self.is_copy(self.p.decls.get(local).ty) {
            ""
        } else {
            "&"
        };
        Expr::path(format!("let Some({name}) = {by}{name}"))
    }

    /// `x as T` where `x` is `T?`: `x.ok_or_else(temper_core::Error::new)?`.
    pub(crate) fn as_non_null(&mut self, value: NodeId) -> R<Expr> {
        let v = self.expr(value, Want::Owned)?;
        Ok(
            v.method("ok_or_else", vec![Expr::path("temper_core::Error::new")])
                .try_(),
        )
    }

    /// `!isNull(x)` as `x.is_some()`.
    pub(crate) fn is_some(&mut self, args: &[NodeId]) -> R<Option<Expr>> {
        if let [arg] = args {
            if let Node::IsNull { value } = self.p.nodes.get(*arg) {
                let v = self.expr(value, Want::Borrowed)?;
                return Ok(Some(v.method("is_some", Vec::new())));
            }
        }
        Ok(None)
    }
}

/// `if let Some(x) = x { x } else { d }` as `x.unwrap_or(d)`, as clippy
/// wants it (`&x` and `x.clone()` as `x.clone()…`), with
/// `unwrap_or_else(|| d)` where `d` isn't [`simple`]; else `e` as it is.
pub(crate) fn unwrap_or(e: Expr) -> Expr {
    let Expr::If(cond, then, els) = &e else {
        return e;
    };
    let Expr::Path(cond) = &**cond else {
        return e;
    };
    let Some(rest) = cond.strip_prefix("let Some(") else {
        return e;
    };
    let Some((name, scrutinee)) = rest.split_once(") = ") else {
        return e;
    };
    let by_ref = scrutinee.starts_with('&');
    let (Some(t), Some(d)) = (then.tail.as_deref(), els.tail.as_deref()) else {
        return e;
    };
    if !then.stmts.is_empty() || !els.stmts.is_empty() || !closure_safe(d) {
        return e;
    }
    // `if let Some(x) = x { Some(f(x)) } else { None }` as `x.map(|x| f(x))`.
    if let (Expr::Call(f, args), Expr::Path(none)) = (t, d) {
        if matches!(&**f, Expr::Path(s) if s == "Some")
            && none == "None"
            && args.len() == 1
            && closure_safe(&args[0])
        {
            let scrutinee = Expr::path(scrutinee.trim_start_matches('&'));
            let scrutinee = if by_ref {
                scrutinee.method("as_ref", Vec::new())
            } else {
                scrutinee
            };
            let f = Expr::Closure(vec![name.to_string()], Block::of(args[0].clone()));
            return scrutinee.method("map", vec![f]);
        }
    }
    let value = Expr::path(name);
    let fits = if by_ref {
        *t == value.clone().method("clone", Vec::new())
    } else {
        *t == value
    };
    if !fits {
        return e;
    }
    let x = if by_ref {
        value.method("clone", Vec::new())
    } else {
        value
    };
    if simple(d) {
        x.method("unwrap_or", vec![d.clone()])
    } else {
        let f = Expr::Closure(Vec::new(), Block::of(d.clone()));
        x.method("unwrap_or_else", vec![f])
    }
}

/// Whether an expression may run in a closure: it doesn't return, try,
/// or hold statements.
fn closure_safe(e: &Expr) -> bool {
    match e {
        Expr::Path(_) | Expr::Lit(_) | Expr::Str(_) => true,
        Expr::Call(f, args) => closure_safe(f) && args.iter().all(closure_safe),
        Expr::Method(r, _, args) => closure_safe(r) && args.iter().all(closure_safe),
        Expr::Macro(_, args) => args.iter().all(closure_safe),
        Expr::Bin(_, a, b) => closure_safe(a) && closure_safe(b),
        Expr::Unary(_, x) | Expr::Cast(x, _) | Expr::Field(x, _) => closure_safe(x),
        _ => false,
    }
}

/// A fallback cheap enough to compute whether it's needed or not: a
/// literal, a name, or `None`, as clippy's `unnecessary_lazy_evaluations`
/// wants them.
fn simple(e: &Expr) -> bool {
    match e {
        Expr::Lit(_) | Expr::Str(_) | Expr::Path(_) => true,
        Expr::Unary(Unary::Neg, x) => simple(x),
        Expr::Call(f, args) => {
            matches!(&**f, Expr::Path(s) if s == "Some") && args.len() == 1 && simple(&args[0])
        }
        _ => false,
    }
}
