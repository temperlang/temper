//! Declared failures (RFC 164; notes/tmpl/nodes.md, "Declared failures"),
//! as values, never thrown: a function that declares a failure type
//! returns `core::Expected<T, F>` (`std::expected` from C++23, the
//! runtime's shim before, `expected.hpp`), `F` the failure type's
//! `shared_ptr`. Failure classes are structs as any class's, below the
//! library's base, `struct FailuresError : virtual core::Failure`, which is
//! core's `Error`, a `std::exception` with `what()` its message and a
//! cause (`failure.hpp`); a sealed failure interface is an abstract struct
//! between, as any interface.
//!
//! - **A failing call** is computed first, before the statement it's in,
//!   and branched on: `auto lookupResult = lookup(key); if
//!   (!lookupResult.has_value()) { return
//!   core::unexpected(lookupResult.error()); }`, then `*lookupResult` where
//!   the call was ([`FailTo`] says where the failure goes: returned, or
//!   out of a module's init as `core::uncaught_failure(…)`). One whose
//!   value and failure types are the function's is returned as it is,
//!   `return sign(text);`. `temper_tmpl::spill` makes a choice's part or a
//!   loop's condition that holds one statements first.
//! - **`throw x`** is `return core::unexpected(x);`, with
//!   `core::with_cause(x, e)` in a handler, which keeps a cause `x` has.
//! - **`catch` and `orelse`** branch on the result: where the body's one
//!   failing call runs before anything else in it, `auto r = f(…); if
//!   (r.has_value()) { …the body, *r for the call… } else { …the
//!   fallback… }`; otherwise the body is a lambda called at once, giving
//!   a `core::Expected` of exactly what it may fail with, branched on the
//!   same way. A body that may also bubble is in a `try`, whose `catch` of
//!   `TemperBubble` runs the fallback, and its failure, bubbled there,
//!   reaches it the same way (an exception on that failure path only).
//!   Panics and host code's exceptions pass every handler.
//!
//! The success path allocates nothing: a value is held in place in its
//! `Expected`. A failure costs its object (`make_shared`) and a branch per
//! call it passes through, and its message is made once, when first
//! asked.

use super::*;
use temper_tmpl::failures;

impl Translator<'_> {
    /// What a call may fail with, where that's a declared failure type,
    /// not `Bubble`.
    pub(crate) fn declared(&self, id: NodeId) -> Option<TypeId> {
        let p = self.p;
        if !matches!(
            p.nodes.get(id),
            Node::CallFunction { .. }
                | Node::CallMethod { .. }
                | Node::New { .. }
                | Node::CallValue { .. }
        ) {
            return None;
        }
        failures::node_failure(p, id).filter(|&f| f != TypeId::BUBBLE)
    }

    /// A signature's result in C++: `core::Expected<T, F>` where it fails
    /// with a declared failure type, else its value's type.
    pub(crate) fn result_type(&self, pass: TypeId, fail: Option<TypeId>) -> R<String> {
        match self.declared_fail(fail)? {
            Some(fail) => Ok(format!(
                "{CORE}::Expected<{}, {}>",
                self.ty(pass)?,
                self.ty(fail)?
            )),
            None => self.ty(pass),
        }
    }

    /// A signature's failure type, where it's a declared one.
    fn declared_fail(&self, fail: Option<TypeId>) -> R<Option<TypeId>> {
        match fail {
            Some(f) if f == TypeId::BUBBLE => Ok(None),
            Some(f) if matches!(self.p.types.get(f), TypeData::TypeParam { .. }) => {
                not_yet("a failure parameter")
            }
            fail => Ok(fail),
        }
    }

    /// A declaration's result in C++ ([`result_type`](Self::result_type)).
    pub(crate) fn decl_result(&self, decl: DeclId) -> R<String> {
        match self.p.signature(decl) {
            Some(sig) => self.result_type(sig.pass, sig.fail),
            None => Ok("void".into()),
        }
    }

    /// Where a failure goes from the body of a function of this value and
    /// failure type.
    pub(crate) fn fail_to_of(&self, pass: TypeId, fail: Option<TypeId>) -> R<FailTo> {
        Ok(match self.declared_fail(fail)? {
            Some(fail) => FailTo::Return(pass, fail),
            None => FailTo::Nowhere,
        })
    }

    /// What a failure does where it goes ([`FailTo`]).
    fn failed(&self, failure: Expr) -> R<Stmt> {
        match self.fail_to {
            FailTo::Return(..) => Ok(Stmt::Return(Some(
                self.core("unexpected").call(vec![failure]),
            ))),
            FailTo::Init => Ok(Stmt::Bubble(
                self.core("uncaught_failure").call(vec![failure]),
            )),
            FailTo::Nowhere => not_yet("a failure with nowhere to go"),
        }
    }

    /// A name for a failing call's result: `lookupResult`.
    fn result_name(&mut self, call: NodeId) -> String {
        let p = self.p;
        let base = match p.nodes.get(call) {
            Node::CallFunction { callee: d, .. } | Node::CallMethod { method: d, .. } => {
                format!("{}Result", self.names.get(d))
            }
            Node::New { ctor, .. } => {
                format!(
                    "{}Result",
                    lower_first(self.names.get(p.decls.get(ctor).owner))
                )
            }
            _ => "result".into(),
        };
        self.fresh_local(&base)
    }

    /// A failing call where it's passed on: computed first, its failure
    /// going where failures go, and its value where the call was.
    pub(crate) fn propagated(&mut self, call: NodeId) -> R<Expr> {
        if self.deferred > 0 {
            return not_yet("a failing call where nothing can go before it");
        }
        self.raw_call = Some(call);
        let value = self.expr(call)?;
        let name = self.result_name(call);
        let result = Expr::name(name.clone());
        let failed = self.failed(result.clone().method("error", Vec::new()))?;
        self.pending.push(Stmt::Local {
            ty: "auto".into(),
            name,
            value: Some(value),
        });
        self.pending.push(Stmt::If {
            cond: Expr::unary(Unary::Not, result.clone().method("has_value", Vec::new())),
            then: vec![failed],
            els: Vec::new(),
        });
        Ok(Expr::unary(Unary::Deref, result))
    }

    /// Whether a call returned at once gives the function's own result,
    /// as it is: its value and failure types are the function's.
    pub(crate) fn returned_as_is(&self, id: NodeId) -> bool {
        match self.fail_to {
            FailTo::Return(pass, fail) => {
                self.declared(id) == Some(fail) && self.p.nodes.ty(id) == pass
            }
            _ => false,
        }
    }

    /// The call itself, its result returned as it is
    /// ([`returned_as_is`](Self::returned_as_is)).
    pub(crate) fn returned_call(&mut self, id: NodeId) -> R<Vec<Stmt>> {
        self.raw_call = Some(id);
        let e = self.expr(id)?;
        let mut out = std::mem::take(&mut self.pending);
        out.push(Stmt::Return(Some(e)));
        Ok(out)
    }

    /// `throw value`, with the failure being handled as its cause, if
    /// any.
    pub(crate) fn thrown(&mut self, value: NodeId, cause: Option<NodeId>) -> R<Vec<Stmt>> {
        let ids: Vec<NodeId> = [value].into_iter().chain(cause).collect();
        let mut both = self.operands(&ids)?.into_iter();
        let mut failure = both.next().unwrap_or(Expr::Bool(false));
        if let Some(cause) = both.next() {
            failure = self.core("with_cause").call(vec![failure, cause]);
        }
        let mut out = std::mem::take(&mut self.pending);
        out.push(self.failed(failure)?);
        Ok(out)
    }

    /// An `orelse` or `catch` whose body may fail with a declared failure
    /// type, as statements giving its value to `sink`: the body's result
    /// branched on ([module docs](self)).
    pub(super) fn handled(&mut self, id: NodeId, sink: &Sink) -> R<Vec<Stmt>> {
        let p = self.p;
        let (body, fallback, local) = match p.nodes.get(id) {
            Node::OrElse { body, fallback } => (body, fallback, None),
            Node::Catch {
                local,
                body,
                fallback,
            } => (body, fallback, Some(local)),
            _ => return not_yet("a handler that isn't one"),
        };
        let under = failures::failures_under(p, body);
        let bubbles = under.iter().any(|&(_, f)| f == TypeId::BUBBLE);
        let declared: Vec<(NodeId, TypeId)> = under
            .into_iter()
            .filter(|&(_, f)| f != TypeId::BUBBLE)
            .collect();
        if bubbles && local.is_some() {
            return not_yet("a catch binding a failure beside a bubble");
        }
        let fails: Vec<TypeId> = declared.iter().map(|&(_, f)| f).collect();
        let Some(join) = failures::join(p, failures::root(p), &fails) else {
            return not_yet("a handler of declared failures without one");
        };
        let mut out = Vec::new();
        let first = match declared.as_slice() {
            &[(call, _)] if self.declared(call).is_some() && first_evaluated(p, body, call) => {
                Some(call)
            }
            _ => None,
        };
        let (name, then) = match first {
            // The call, then the body with its value.
            Some(call) => {
                self.raw_call = Some(call);
                let value = self.expr(call)?;
                out.append(&mut self.pending);
                let name = self.result_name(call);
                out.push(Stmt::Local {
                    ty: "auto".into(),
                    name: name.clone(),
                    value: Some(value),
                });
                let read = Expr::unary(Unary::Deref, Expr::name(name.clone()));
                let outer = self.substitute.replace((call, read));
                let then = self.deliver(body, sink);
                self.substitute = outer;
                (name, then?)
            }
            // The body, as a lambda called at once.
            None => {
                if jumps_out(p, body) {
                    return not_yet("a handler's body that jumps out of it");
                }
                let pass = p.nodes.ty(body);
                let result = self.result_type(pass, Some(join))?;
                let outer = std::mem::replace(&mut self.fail_to, FailTo::Return(pass, join));
                let deferred = std::mem::replace(&mut self.deferred, 0);
                let stmts = self.deliver(body, &Sink::Return);
                self.fail_to = outer;
                self.deferred = deferred;
                let mut stmts = stmts?;
                if pass == TypeId::VOID {
                    expected_void(&mut stmts);
                }
                let name = self.fresh_local("result");
                out.push(Stmt::LambdaLocal {
                    name: name.clone(),
                    captures: "&".into(),
                    params: Vec::new(),
                    result,
                    body: stmts,
                    called: true,
                });
                let value = Expr::unary(Unary::Deref, Expr::name(name.clone()));
                let then = self.give(value, pass == TypeId::VOID, sink);
                (name, then)
            }
        };
        let result = Expr::name(name);
        let mut els = Vec::new();
        if bubbles {
            // To the `catch` below, with the bubbles.
            els.push(Stmt::Bubble(self.core("bubble").call(Vec::new())));
        } else {
            if let Some(local) = local.filter(|&l| self.is_read(l)) {
                let ty = self.ty(p.decls.get(local).ty)?;
                let name = self.local_name(local);
                els.push(Stmt::Local {
                    ty,
                    name,
                    value: Some(result.clone().method("error", Vec::new())),
                });
            }
            els.extend(self.deliver_marked(fallback, sink)?);
        }
        let has = result.method("has_value", Vec::new());
        out.push(match then.is_empty() {
            true => Stmt::If {
                cond: Expr::unary(Unary::Not, has),
                then: els,
                els: Vec::new(),
            },
            false => Stmt::If {
                cond: has,
                then,
                els,
            },
        });
        if bubbles {
            let fallback = self.deliver_marked(fallback, sink)?;
            return Ok(vec![Stmt::Try {
                body: out,
                fallback,
            }]);
        }
        Ok(out)
    }

    /// Whether a class or interface is core's `Error`, which the runtime's
    /// `core::Failure` is.
    pub(crate) fn is_failure_root(&self, decl: DeclId) -> bool {
        failures::root(self.p) == Some(decl)
    }
}

/// Whether `call` is the first thing `id` runs: it, or the first of what
/// runs first in it, all the way down.
fn first_evaluated(p: &Program, id: NodeId, call: NodeId) -> bool {
    if id == call {
        return true;
    }
    let first = match p.nodes.get(id) {
        Node::CallFunction { .. }
        | Node::CallMethod { .. }
        | Node::New { .. }
        | Node::Op { .. }
        | Node::And { .. }
        | Node::Or { .. }
        | Node::IfValue { .. }
        | Node::AsNullable { .. }
        | Node::NonNull { .. }
        | Node::Upcast { .. }
        | Node::Downcast { .. }
        | Node::ToFun { .. }
        | Node::FromFun { .. }
        | Node::ExprStatement { .. }
        | Node::Return { .. }
        | Node::Assign { .. }
        | Node::Let { .. }
        | Node::BlockValue { .. } => p.nodes.kids(id).first().copied(),
        _ => None,
    };
    first.is_some_and(|f| first_evaluated(p, f, call))
}

/// Whether a handler's body jumps out of itself: a `return`, or a `break`
/// or `continue` of a loop around it, which a lambda can't.
fn jumps_out(p: &Program, id: NodeId) -> bool {
    fn visit(p: &Program, id: NodeId, inner: &mut Vec<DeclId>) -> bool {
        match p.nodes.get(id) {
            Node::FunctionLiteral { .. } => false,
            Node::Return { .. } => true,
            Node::Break { label } | Node::Continue { label } => !inner.contains(&label),
            Node::While { label, .. } | Node::DoWhile { label, .. } => {
                inner.push(label);
                let found = p.nodes.kids(id).iter().any(|&k| visit(p, k, inner));
                inner.pop();
                found
            }
            _ => p.nodes.kids(id).iter().any(|&k| visit(p, k, inner)),
        }
    }
    visit(p, id, &mut Vec::new())
}

/// A body returning `core::Expected<void, F>`: each `return;` a `return
/// {};`, and one at the end where it gets there.
pub(crate) fn expected_void(stmts: &mut Vec<Stmt>) {
    fn fix(stmts: &mut [Stmt]) {
        for s in stmts {
            match s {
                Stmt::Return(value @ None) => *value = Some(Expr::name("{}")),
                Stmt::Block(body) | Stmt::TestsOnly(body) => fix(body),
                Stmt::If { then, els, .. } => {
                    fix(then);
                    fix(els);
                }
                Stmt::While { body, .. } | Stmt::DoWhile { body, .. } => fix(body),
                Stmt::Switch { cases, .. } => {
                    for (_, body) in cases {
                        fix(body);
                    }
                }
                Stmt::Try { body, fallback } => {
                    fix(body);
                    fix(fallback);
                }
                _ => {}
            }
        }
    }
    fix(stmts);
    if stmts.last().is_none_or(completes) {
        stmts.push(Stmt::Return(Some(Expr::name("{}"))));
    }
}

/// A name with its first letter lowercase: `notFound` for `NotFound`.
fn lower_first(name: &str) -> String {
    let mut chars = name.chars();
    match chars.next() {
        Some(c) => c.to_lowercase().chain(chars).collect(),
        None => String::new(),
    }
}
