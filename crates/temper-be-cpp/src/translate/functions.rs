//! Function values in C++, as Kotlin's C++ backend writes them: a
//! function type, or a `@fun` interface, is a `std::function`, a
//! parameter of one a `const &`; a literal is a lambda that captures by
//! value, `[=](int32_t x) -> int32_t { return f(f(x)); }`, or with more
//! statements, an `auto` local declared before the statement it's in; a
//! reference to a function names it; and a call of a value calls it.
//!
//! Safer than Kotlin's, which captures a local the lambda assigns by
//! reference (`[=, &count]`), so a closure that outlives its function
//! writes a dead stack slot: a local that a lambda captures and that's
//! assigned is a cell, a `std::shared_ptr` the lambda copies, read as
//! `*count` and written as `*count = …`. And `this` in a lambda is the
//! member's `self`, a `shared_ptr` the lambda copies, never the raw
//! pointer.

use super::*;
use temper_tmpl::Kids;
use temper_tmpl::analysis::walk;

impl Translator<'_> {
    /// A function value's type, a function type's or a `@fun`
    /// interface's, as a `std::function`: `std::function<int32_t(int32_t)>`,
    /// or where an error parameter of its interface is given `Never<…>`,
    /// one returning `core::Expected<int32_t, core::Never>`, as generic
    /// code calling it expects (`declared_errors::value_error`).
    pub(crate) fn function_type(&self, ty: TypeId) -> R<String> {
        let Some(sig) = temper_tmpl::analysis::fun_signature(self.p, ty) else {
            return not_yet("a std::function for another type");
        };
        let Some(s) = self.p.types.signature(sig) else {
            return not_yet("a function type without a signature");
        };
        if s.optional.contains(&true) {
            return not_yet("a function type with optional parameters");
        }
        let mut texts = Vec::new();
        for &t in s.params {
            texts.push(self.ty(t)?);
        }
        let fail = temper_tmpl::declared_errors::value_error(self.p, ty);
        Ok(format!(
            "std::function<{}({})>",
            self.result_type(s.pass, fail)?,
            texts.join(", ")
        ))
    }

    /// Whether a local is a cell: captured by a lambda and assigned.
    pub(crate) fn is_cell(&self, decl: DeclId) -> bool {
        self.cells.contains(&decl)
    }

    /// A function literal: a one-line lambda where its body is one
    /// statement but a loop, else an `auto` local of it declared before the
    /// statement it's in.
    pub(crate) fn literal(
        &mut self,
        decl: DeclId,
        params: Kids,
        body: NodeId,
        ty: TypeId,
    ) -> R<Expr> {
        let p = self.p;
        let mut captures = if p.decls.captures(decl).is_empty() && !uses_this(p, body) {
            String::new()
        } else {
            "=".to_string()
        };
        // In a group's method, what it uses of the group, copied.
        let group = self.group.clone();
        if let Some(g) = &group {
            if g.top {
                for &c in p.decls.captures(decl) {
                    if g.captures.contains(&c) {
                        let name = self.name(c);
                        captures.push_str(&format!(", {name} = {name}"));
                    }
                }
                if p.decls.captures(decl).iter().any(|c| g.members.contains(c)) {
                    captures.push_str(&format!(", {} = *this", g.var));
                }
            }
            self.group.as_mut().unwrap().top = false;
        }
        let parts = self.literal_parts(decl, params, body, ty);
        self.group = group;
        let (ps, result, stmts) = parts?;
        // A loop gets lines of its own, as a state machine's step's does.
        let one_line = match stmts.as_slice() {
            [] => true,
            [s] => !matches!(s, Stmt::While { .. } | Stmt::DoWhile { .. }),
            _ => false,
        };
        if one_line {
            return Ok(Expr::Lambda {
                captures,
                params: ps,
                result,
                body: stmts,
            });
        }
        // The naming pass gave it a name of its own in the function.
        let name = self.names.get(decl).to_string();
        self.pending.push(Stmt::LambdaLocal {
            name: name.clone(),
            captures,
            params: ps,
            result,
            body: stmts,
            called: false,
        });
        Ok(Expr::name(name))
    }

    /// A function literal's parameters (an unused one unnamed), result
    /// type, and body, as a value of type `ty`: a literal that can't fail
    /// returns an `Expected` of `core::Never` where `ty`'s error
    /// parameter is given `Never<…>` ([`function_type`](Self::function_type)).
    #[allow(clippy::type_complexity)]
    pub(crate) fn literal_parts(
        &mut self,
        decl: DeclId,
        params: Kids,
        body: NodeId,
        ty: TypeId,
    ) -> R<(Vec<(String, String)>, String, Vec<Stmt>)> {
        let p = self.p;
        let Some(sig) = p.signature(decl) else {
            return not_yet("a function literal without a signature");
        };
        let fail = sig
            .fail
            .or_else(|| temper_tmpl::declared_errors::value_error(p, ty));
        let result = self.result_type(sig.pass, fail)?;
        let fail_to = self.fail_to_of(sig.pass, fail)?;
        // What the body reads; a parameter it doesn't is unnamed.
        let mut used = std::collections::HashSet::new();
        walk(p, body, &mut |id| {
            if let Node::Local { local } = p.nodes.get(id) {
                used.insert(local);
            }
        });
        let mut ps = Vec::new();
        for &param in p.nodes.list(params) {
            if let Node::Param { decl } = p.nodes.get(param) {
                let name = match used.contains(&decl) {
                    true => self.name(decl),
                    false => String::new(),
                };
                ps.push((self.ty(p.decls.get(decl).ty)?, name));
            }
        }
        // The function around it, whose state its own replaces.
        let saved = (
            std::mem::take(&mut self.loops),
            std::mem::take(&mut self.labels),
            std::mem::take(&mut self.pending),
            self.deferred,
            self.in_function,
            std::mem::replace(&mut self.fail_to, fail_to),
        );
        self.deferred = 0;
        self.in_function = true;
        self.begin(&[body]);
        let stmts = self.stmts(body).map(reachable);
        let (loops, labels, pending, deferred, in_function, outer) = saved;
        self.loops = loops;
        self.labels = labels;
        self.pending = pending;
        self.deferred = deferred;
        self.in_function = in_function;
        self.fail_to = outer;
        let mut stmts = stmts?;
        if sig.pass == TypeId::VOID && fail_to != FailTo::Nowhere {
            super::declared_errors::expected_void(&mut stmts);
        } else if sig.pass == TypeId::VOID && stmts.last() == Some(&Stmt::Return(None)) {
            stmts.pop();
        }
        Ok((ps, result, stmts))
    }

    /// A declared function or static method by its name, which converts
    /// to a `std::function`: `addOne`, `Scaler::triple`, where Kotlin's
    /// names a static that isn't there.
    pub(crate) fn function_ref(&self, target: DeclId) -> R<Expr> {
        if !self.p.decls.type_params(target).is_empty() {
            return not_yet("a generic function as a value");
        }
        Ok(Expr::name(self.top_name(target)))
    }
}

/// Whether a literal's body uses `this`.
pub(crate) fn uses_this(p: &Program, body: NodeId) -> bool {
    let mut found = false;
    walk(p, body, &mut |id| {
        found |= matches!(p.nodes.get(id), Node::This {})
    });
    found
}
