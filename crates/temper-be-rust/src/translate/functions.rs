//! Function values in Rust, as Kotlin's Rust backend writes them: a
//! function type, or a `@fun` interface, is `Arc<dyn Fn(A) -> R + Send +
//! Sync>`; a literal is a `move` closure in an `Arc`, after clones of what
//! it captures that isn't `Copy` (so the closure is `'static`, and its
//! captures stay usable outside it); a reference to a function is the
//! function in an `Arc`; and a call of a value calls it. A local that a
//! literal captures and that's assigned is a cell, `Arc<RwLock<T>>`, read
//! with `temper_core::read_locked` and written under its lock, so the
//! closure and the function around it share it, where Kotlin's groups
//! captures in a struct.
//!
//! Prettier than Kotlin's: core's members that take a function take `&dyn
//! Fn`, so a literal there is a plain closure, borrowing what it uses,
//! and a function by name is passed as itself; where Kotlin hoists such
//! literals to top-level functions.

use super::{R, Translator, Want, not_yet};
use crate::tree::{Block, Expr, Stmt, Unary};
use temper_tmpl::{DeclId, DeclKind, Kids, Node, NodeId, TypeData, TypeId};

impl Translator<'_> {
    /// A function value's type, a function type's or a `@fun`
    /// interface's, `Arc<dyn Fn(A, B) -> R + Send + Sync>`: `R` a
    /// `Result` if it may fail, or if a failure parameter of its
    /// interface is given `Never<…>`, `Result<R, Infallible>`, as generic
    /// code calling it expects (`failures::value_failure`), and left out
    /// if `Void` and it can't.
    pub(crate) fn fn_type(&mut self, ty: TypeId) -> R<String> {
        let s = self.fun_sig(ty).and_then(|sig| self.p.types.signature(sig));
        let Some(s) = s else {
            return not_yet("a function type without a signature");
        };
        if s.optional.contains(&true) {
            return not_yet("a function type with optional parameters");
        }
        let (params, result) = (s.params.to_vec(), s.pass);
        let fail = temper_tmpl::failures::value_failure(self.p, ty);
        let mut texts = Vec::new();
        for t in params {
            texts.push(self.ty(t)?);
        }
        let pass = match result == TypeId::VOID {
            true => None,
            false => Some(self.ty(result)?),
        };
        let result = match (pass, fail) {
            (None, None) => String::new(),
            (Some(pass), None) => format!(" -> {pass}"),
            (pass, Some(fail)) => format!(" -> {}", self.result_of(pass, fail)?),
        };
        self.uses.insert("std::sync::Arc");
        Ok(format!(
            "Arc<dyn Fn({}){result} + Send + Sync>",
            texts.join(", ")
        ))
    }

    /// The signature of a function value's type, a function type or a
    /// `@fun` interface.
    pub(crate) fn fun_sig(&self, ty: TypeId) -> Option<TypeId> {
        temper_tmpl::analysis::fun_signature(self.p, ty)
    }

    /// Whether a local is a cell: captured by a literal and assigned.
    pub(crate) fn is_cell(&self, decl: DeclId) -> bool {
        self.cells.contains(&decl)
    }

    /// A cell's value: `temper_core::read_locked(&x)`.
    pub(crate) fn read_cell(&self, decl: DeclId) -> Expr {
        let name = Expr::path(self.name(decl));
        Expr::path("temper_core::read_locked").call(vec![Expr::unary(Unary::Ref, name)])
    }

    /// `*x.write().unwrap() = value;`, a cell's assignment.
    pub(crate) fn write_cell(&self, decl: DeclId, value: Expr) -> Stmt {
        let lock = Expr::path(self.name(decl))
            .method("write", Vec::new())
            .method("unwrap", Vec::new());
        Stmt::Assign(Expr::unary(Unary::Deref, lock), value)
    }

    /// A new cell holding `value`: `Arc::new(RwLock::new(value))`.
    pub(crate) fn new_cell(&mut self, value: Expr) -> Expr {
        self.uses.insert("std::sync::RwLock");
        let lock = Expr::path("RwLock::new").call(vec![value]);
        self.arc(lock)
    }

    /// A cell's type, `Arc<RwLock<T>>`.
    pub(crate) fn cell_type(&mut self, ty: TypeId) -> R<String> {
        self.uses.insert("std::sync::RwLock");
        self.uses.insert("std::sync::Arc");
        Ok(format!("Arc<RwLock<{}>>", self.ty(ty)?))
    }

    /// A function literal as a value of its own: `{ let x = x.clone();
    /// Arc::new(move |a: A| …) }`.
    pub(crate) fn literal(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: Kids,
        body: NodeId,
    ) -> R<Expr> {
        let p = self.p;
        let mut clones = Vec::new();
        for &c in p.decls.captures(decl) {
            if self.group_member(c) {
                // A group's function, from the method it's in.
                let access = self.group.as_ref().unwrap().access.clone();
                clones.push(Stmt::Let {
                    name: self.name(c),
                    mutable: false,
                    ty: None,
                    value: Some(self.member_closure(c, &access, true)?),
                });
            } else if self.is_cell(c) || !self.is_copy(p.decls.get(c).ty) {
                let name = self.name(c);
                clones.push(Stmt::Let {
                    name: name.clone(),
                    mutable: false,
                    ty: None,
                    value: Some(Expr::path(name).method("clone", Vec::new())),
                });
            }
        }
        let saved_this = self.this.clone();
        if self.uses_this(body) {
            match self.this.clone() {
                super::This::Class => {
                    clones.push(Stmt::Let {
                        name: "this".into(),
                        mutable: false,
                        ty: None,
                        value: Some(Expr::path("self").method("clone", Vec::new())),
                    });
                    self.this = super::This::Local("this".into());
                }
                super::This::Local(_) => {}
                _ => return not_yet("`this` in a function literal in an interface"),
            }
        }
        // Its body reaches a group's functions by its own closures.
        let group = self.group.take();
        let closure = self.closure(id, decl, params, body);
        self.group = group;
        self.this = saved_this;
        let closure = self.arc(Expr::Move(Box::new(closure?)));
        Ok(if clones.is_empty() {
            closure
        } else {
            Expr::Block(Block::new(clones, Some(closure)))
        })
    }

    /// Whether a literal's body uses `this`.
    pub(crate) fn uses_this(&self, body: NodeId) -> bool {
        let mut found = false;
        temper_tmpl::analysis::walk(self.p, body, &mut |id| {
            found |= matches!(self.p.nodes.get(id), Node::This {});
        });
        found
    }

    /// A function literal's closure, `|a: A| body`, its body translated
    /// as a function's of its own.
    pub(crate) fn closure(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: Kids,
        body: NodeId,
    ) -> R<Expr> {
        let (ps, _, body) = self.closure_parts(id, decl, params, body)?;
        Ok(Expr::Closure(ps, body))
    }

    /// A function literal's parameters, result type, and body, translated
    /// as a function's of its own.
    pub(crate) fn closure_parts(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: Kids,
        body: NodeId,
    ) -> R<(Vec<String>, Option<String>, Block)> {
        let p = self.p;
        let Some(sig) = p.signature(decl) else {
            return not_yet("a function literal without a signature");
        };
        // A literal that can't fail, where its type's failure parameter
        // is given `Never<…>`, returns `Ok`s of `Infallible` (`fn_type`).
        let fails = (sig.fail)
            .or_else(|| temper_tmpl::failures::value_failure(p, p.nodes.ty(id)))
            .or_else(|| self.ok_results.get(&id).copied());
        let (void, result) = (sig.pass == TypeId::VOID, sig.pass);
        // The function around it, which its body's own state replaces.
        let saved = (
            self.scope,
            self.fails,
            std::mem::take(&mut self.loops),
            std::mem::take(&mut self.labels),
            std::mem::take(&mut self.body_labels),
            self.guard.take(),
            std::mem::take(&mut self.orelse),
        );
        let scope = id;
        self.begin(scope, fails);
        let result = (|| -> R<(Vec<String>, Option<String>, Block)> {
            let mut ps = Vec::new();
            let mut stmts = Vec::new();
            self.params_into(scope, params, super::Params::Trait, &mut ps, &mut stmts)?;
            self.block_into(body, &mut stmts)?;
            let ty = if void { None } else { Some(self.ty(result)?) };
            let (result, body) = self.finish_body(stmts, ty, void)?;
            Ok((ps, result, body))
        })();
        let (scope, fails, loops, labels, body_labels, guard, orelse) = saved;
        self.orelse = orelse;
        self.scope = scope;
        self.fails = fails;
        self.loops = loops;
        self.labels = labels;
        self.body_labels = body_labels;
        self.guard = guard;
        result
    }

    /// A declared function or static method as a value of type `ty`:
    /// `Arc::new(f)`, or `Arc::new(|a: Arc<String>| f(a))` where it takes
    /// its parameters as Kotlin's do, `impl ToArcString` and the like,
    /// which a closure gives concrete types, or where it can't fail and
    /// `ty` returns a result that can't (`fn_type`): `Arc::new(|a0: i32|
    /// Ok(f(a0)))`.
    pub(crate) fn function_ref(&mut self, id: NodeId, target: DeclId, ty: TypeId) -> R<Expr> {
        let fails = temper_tmpl::failures::value_failure(self.p, ty).is_some()
            || self.ok_results.contains_key(&id);
        let f = self.function_value(target, fails)?;
        Ok(self.arc(f))
    }

    /// A function as what a value of its type calls: its path, or a
    /// closure calling it where its parameters aren't concrete, or where
    /// that value returns a result (`as_result`) and it doesn't.
    fn function_value(&mut self, target: DeclId, as_result: bool) -> R<Expr> {
        let p = self.p;
        if is_core(p, target) {
            return not_yet("core's function as a value");
        }
        let path = match self.static_fn_path(target)? {
            Some(path) => path,
            None => self.top_path(target)?,
        };
        let Some(sig) = p.signature(target) else {
            return not_yet("a function without a signature");
        };
        let params = sig.params.to_vec();
        let concrete = params.iter().all(|&t| {
            t != TypeId::STRING && !self.is_nullable_string(t) && self.list_param(t).is_none()
        });
        let ok = as_result && sig.fail.is_none();
        if concrete && !ok {
            return Ok(Expr::path(path));
        }
        let mut names = Vec::new();
        let mut args = Vec::new();
        for (i, &t) in params.iter().enumerate() {
            let name = format!("a{i}");
            names.push(format!("{name}: {}", self.ty(t)?));
            args.push(Expr::path(name));
        }
        let call = Expr::path(path).call(args);
        let call = if ok {
            Expr::path("Ok").call(vec![call])
        } else {
            call
        };
        Ok(Expr::Closure(names, Block::of(call)))
    }

    /// The failure, `Never<…>`, that makes a parameter of type `param`
    /// of a call of `callee` given `type_args` return a result, where
    /// argument `a` can't fail: the parameter's function type fails with
    /// a failure parameter (RFC 164), given `Never<…>`, as only an
    /// argument that can't fail gives it. A `@fun` value given `Never`
    /// returns a result already (`failures::value_failure`).
    pub(crate) fn result_wanted(
        &self,
        callee: DeclId,
        type_args: temper_tmpl::TypeArgs,
        param: TypeId,
        a: NodeId,
    ) -> Option<TypeId> {
        let p = self.p;
        if temper_tmpl::failures::value_failure(p, p.nodes.ty(a)).is_some() {
            return None;
        }
        let fail = temper_tmpl::failures::value_failure(p, param)?;
        let from = temper_tmpl::analysis::type_param_types(p, callee);
        let fail = p
            .types
            .substituted(fail, &from, p.types.args_of(type_args))?;
        p.types.is_never(fail).then_some(fail)
    }

    /// A function value that can't fail where one returning a result of
    /// `fail` is wanted ([`Self::result_wanted`]): a literal or a
    /// function by name returning `Ok`s, another value called by a
    /// closure that does, `{ let f = f.clone(); Arc::new(move |a0: A|
    /// Ok(f(a0))) }`.
    pub(crate) fn ok_fn_arg(&mut self, a: NodeId, fail: TypeId) -> R<Expr> {
        let p = self.p;
        if matches!(
            p.nodes.get(a),
            Node::FunctionLiteral { .. } | Node::FunctionRef { .. }
        ) {
            self.ok_results.insert(a, fail);
            return self.expr(a, Want::Owned);
        }
        let Some(sig) = self
            .fun_sig(p.nodes.ty(a))
            .and_then(|t| p.types.signature(t))
        else {
            return not_yet("a function value without a signature");
        };
        let params = sig.params.to_vec();
        let mut names = Vec::new();
        let mut args = Vec::new();
        for (i, &t) in params.iter().enumerate() {
            let name = format!("a{i}");
            names.push(format!("{name}: {}", self.ty(t)?));
            args.push(Expr::path(name));
        }
        let f = self.expr(a, Want::Owned)?;
        let call = Expr::path("Ok").call(vec![Expr::path("f").call(args)]);
        let closure = self.arc(Expr::Move(Box::new(Expr::Closure(names, Block::of(call)))));
        let bind = Stmt::Let {
            name: "f".into(),
            mutable: false,
            ty: None,
            value: Some(f),
        };
        Ok(Expr::Block(Block::new(vec![bind], Some(closure))))
    }

    /// A function value where core's member takes a `&dyn Fn`: a literal
    /// as a closure that borrows what it uses, a function by name as
    /// itself, and another value as `&*f`.
    pub(crate) fn fn_arg(&mut self, a: NodeId) -> R<Expr> {
        let p = self.p;
        match p.nodes.get(a) {
            Node::FunctionLiteral { decl, params, body } => {
                if self.uses_this(body) {
                    let closure = self.literal(a, decl, params, body)?;
                    return Ok(Expr::unary(Unary::Ref, Expr::unary(Unary::Deref, closure)));
                }
                let closure = self.closure(a, decl, params, body)?;
                Ok(Expr::unary(Unary::Ref, closure))
            }
            Node::FunctionRef { target } => {
                let f = self.function_value(target, false)?;
                Ok(Expr::unary(Unary::Ref, f))
            }
            Node::ToFun { value } | Node::FromFun { value } => self.fn_arg(value),
            _ => {
                let f = self.expr(a, Want::Borrowed)?;
                Ok(Expr::unary(Unary::Ref, Expr::unary(Unary::Deref, f)))
            }
        }
    }

    /// Whether core's `decl` takes a function, as `&dyn Fn`, for its
    /// parameter `i` (after its receiver).
    pub(crate) fn takes_fn(&self, decl: DeclId, i: usize) -> bool {
        let p = self.p;
        (p.signature(decl).and_then(|s| s.params.get(i).copied()))
            .is_some_and(|t| matches!(p.types.get(t), TypeData::Function { .. }))
    }

    /// A call of a function value: `f(a)`, or `(self.0.f)(a)` for a
    /// field's.
    pub(crate) fn call_value(&mut self, callee: NodeId, args: Kids) -> R<Expr> {
        let p = self.p;
        if let Node::Local { local } = p.nodes.get(callee) {
            if self.group_member(local) {
                // A group's function, called as a method.
                let mut out = Vec::new();
                for &a in p.nodes.list(args) {
                    out.push(self.expr(a, Want::Owned)?);
                }
                let access = self.group.as_ref().unwrap().access.clone();
                return Ok(Expr::path(access).method(self.name(local), out));
            }
        }
        let f = self.expr(callee, Want::Borrowed)?;
        let f = match f {
            e @ (Expr::Path(_) | Expr::Field(..)) => e,
            Expr::Method(r, name, a) if name == "clone" && a.is_empty() => *r,
            e => e,
        };
        let mut out = Vec::new();
        for &a in p.nodes.list(args) {
            out.push(self.expr(a, Want::Owned)?);
        }
        Ok(f.call(out))
    }
}

/// Whether a declaration is core's.
fn is_core(p: &temper_tmpl::Program, decl: DeclId) -> bool {
    temper_tmpl::qname::is_external(p, decl) && temper_tmpl::qname::library_of(p, decl) == "core"
        || p.decls.get(decl).kind == DeclKind::Removed
}
