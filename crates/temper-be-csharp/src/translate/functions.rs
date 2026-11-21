//! Function values in C#, as Kotlin's C# backend writes them: a function
//! type, or a `@fun` interface, is a delegate, `Func<int, int>`, or
//! `Action<string>` for one that returns `Void`; a literal is a lambda,
//! `(int x) => f(f(x))`, or with statements, a local function declared
//! before the statement it's in; a reference to a function is its method
//! group; and a call of a value invokes it. C#'s lambdas capture locals
//! themselves, so an assigned capture needs nothing more.
//!
//! Prettier and safer than Kotlin's: a lambda's parameters are typed,
//! so C# infers a generic callee's type arguments from it (Kotlin's
//! `makeHi { (i: Int): Int => i + j }` didn't compile), and a method
//! group goes as it is, but cast to its delegate where it's an argument of
//! a generic method, which can't infer from it, as Kotlin casts every one.

use super::{R, Translator, not_yet, yield_breaks, yields};
use crate::tree::{Expr, Stmt};
use temper_tmpl::{DeclId, Facts, Kids, Node, NodeId, TypeData, TypeId};

impl Translator<'_> {
    /// A delegate type from a signature: `Func<A, R>`, or `Action<A>`
    /// (`Action` alone) for one that returns `Void`.
    pub(crate) fn delegate(&mut self, ty: TypeId) -> R<String> {
        let Some(sig) = temper_tmpl::analysis::fun_signature(self.p, ty) else {
            return not_yet("a delegate for another type");
        };
        let Some(s) = self.p.types.signature(sig) else {
            return not_yet("a function type without a signature");
        };
        if s.optional.contains(&true) {
            return not_yet("a function type with optional parameters");
        }
        let (params, result) = (s.params.to_vec(), s.result);
        let mut texts = Vec::new();
        for t in params {
            texts.push(self.ty(t)?);
        }
        let name = if result == TypeId::VOID {
            "Action"
        } else {
            texts.push(self.ty(result)?);
            "Func"
        };
        let name = match self.class(name) {
            Expr::Name(n) => n,
            _ => name.to_string(),
        };
        Ok(if texts.is_empty() {
            name
        } else {
            format!("{name}<{}>", texts.join(", "))
        })
    }

    /// A function literal: a lambda, its parameters typed, where its body
    /// is one `return` of an expression (or for `Void`, one call) that
    /// needs no statements; else a local function declared before the
    /// statement it's in, named `name` if given.
    pub(crate) fn literal(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: Kids,
        body: NodeId,
        name: Option<String>,
    ) -> R<Expr> {
        let p = self.p;
        let stmts = match p.nodes.get(body) {
            Node::Block { stmts } => p.nodes.list(stmts).to_vec(),
            _ => Vec::new(),
        };
        let void = p.signature(decl).is_some_and(|s| s.result == TypeId::VOID);
        // The function around it, whose state its own replaces.
        let saved = (
            std::mem::take(&mut self.loops),
            std::mem::take(&mut self.labels),
            self.result.clone(),
            self.in_function,
            self.in_generator,
        );
        let generator = p.decls.get(decl).facts.contains(Facts::GENERATOR);
        self.in_generator = generator;
        let result = (|| -> R<Expr> {
            if generator {
                let local = name.clone().unwrap_or_else(|| self.name(decl));
                return self.generator(id, decl, params, body, local);
            }
            if let ([ret], None) = (stmts.as_slice(), &name) {
                let value = match p.nodes.get(*ret) {
                    Node::Return { value: Some(value) } => Some(value),
                    Node::ExprStatement { expr } if void => Some(expr),
                    _ => None,
                };
                // A value with statements of its own, as an `orelse`, needs
                // a body: a local function's, below.
                if let Some(value) = value.filter(|&v| !has_statements(p, v)) {
                    let outer = std::mem::take(&mut self.pre);
                    self.in_function = true;
                    let e = self.expr(value);
                    let pre = std::mem::replace(&mut self.pre, outer);
                    let e = e?;
                    if pre.is_empty() && (!void || e.is_statement()) {
                        let mut ps = Vec::new();
                        for &param in p.nodes.list(params) {
                            if let Node::Param { decl } = p.nodes.get(param) {
                                let ty = self.ty(p.decls.get(decl).ty)?;
                                ps.push(format!("{ty} {}", self.name(decl)));
                            }
                        }
                        // Typed parameters need parentheses, even one.
                        let head = format!("({})", ps.join(", "));
                        return Ok(Expr::Lambda(vec![head], Box::new(e)));
                    }
                }
            }
            let (result, ps, body) = self.callable(id, decl, params, Some(body))?;
            let local = name.unwrap_or_else(|| self.name(decl));
            self.pre.push(Stmt::LocalFunction {
                result,
                name: local.clone(),
                params: ps,
                body: body.unwrap_or_default(),
            });
            Ok(Expr::name(local))
        })();
        let (loops, labels, result_ty, in_function, in_generator) = saved;
        self.loops = loops;
        self.labels = labels;
        self.result = result_ty;
        self.in_function = in_function;
        self.in_generator = in_generator;
        result
    }

    /// A generator's literal, as Kotlin's: an iterator, a local function
    /// whose body `yield return`s, `IEnumerable<T> fnSteps() { … }`, and
    /// the factory, `IGenerator<T> fn() { return
    /// Core.AdaptGenerator<T>(fnSteps); }`, the literal's value; with
    /// parameters, `Core.AdaptGenerator<T>(() => fnSteps(x))`.
    fn generator(
        &mut self,
        id: NodeId,
        decl: DeclId,
        params: Kids,
        body: NodeId,
        local: String,
    ) -> R<Expr> {
        let p = self.p;
        let (result, ps, stmts) = self.callable(id, decl, params, Some(body))?;
        let mut stmts = stmts.unwrap_or_default();
        yield_breaks(&mut stmts);
        if stmts.last() == Some(&Stmt::YieldBreak) {
            stmts.pop();
        }
        // An iterator yields somewhere, if only at its end, which a body
        // that never ends, as one ending in a panic, never reaches.
        if !yields(&stmts) {
            let ends = stmts.iter().all(super::completes);
            if !ends {
                stmts.push(Stmt::Raw("#pragma warning disable CS0162".into()));
            }
            stmts.push(Stmt::YieldBreak);
            if !ends {
                stmts.push(Stmt::Raw("#pragma warning restore CS0162".into()));
            }
        }
        let yielded = match p.signature(decl).map(|s| p.types.get(s.result)) {
            Some(TypeData::Nominal { args, .. }) => match p.types.list(args) {
                [t] => self.ty(*t)?,
                _ => return not_yet("a generator's yield type"),
            },
            _ => return not_yet("a generator's yield type"),
        };
        let steps = self.temporary(&format!("{local}Steps"));
        self.pre.push(Stmt::LocalFunction {
            result,
            name: steps.clone(),
            params: ps.clone(),
            body: stmts,
        });
        let make = match ps.is_empty() {
            true => Expr::name(steps),
            false => {
                let args = (ps.iter())
                    .map(|(_, n)| Expr::name(n.trim_end_matches(" = null")))
                    .collect();
                Expr::Lambda(vec!["()".into()], Box::new(Expr::name(steps).call(args)))
            }
        };
        let adapt = self
            .class("Core")
            .method(format!("AdaptGenerator<{yielded}>"), vec![make]);
        let generator = self.class("IGenerator");
        let generator = match generator {
            Expr::Name(n) => n,
            _ => "IGenerator".into(),
        };
        self.pre.push(Stmt::LocalFunction {
            result: format!("{generator}<{yielded}>"),
            name: local.clone(),
            params: ps,
            body: vec![Stmt::Return(Some(adapt))],
        });
        Ok(Expr::name(local))
    }

    /// A declared function or static method as its method group:
    /// `AddOne`, `Scaler.Triple`, or another module's through its class.
    pub(crate) fn function_ref(&mut self, target: DeclId) -> R<Expr> {
        let p = self.p;
        let owner = p.decls.get(target).owner;
        let static_member = p.decls.contains(owner)
            && matches!(
                p.decls.get(owner).kind,
                temper_tmpl::DeclKind::Class | temper_tmpl::DeclKind::Interface
            );
        if static_member {
            let name = self.member_name(target);
            if self.in_type == Some(owner) {
                return Ok(Expr::name(name));
            }
            let ty = self.type_name(owner);
            return Ok(Expr::name(ty).member(name));
        }
        Ok(match self.owner_class(target) {
            Some(class) => Expr::name(class).member(self.name(target)),
            None => Expr::name(self.name(target)),
        })
    }

    /// Arguments for a generic method, which C# can't infer type
    /// arguments from a method group for: each method group (a reference,
    /// or a literal declared as a local function) cast to its delegate.
    pub(crate) fn cast_groups(&mut self, args: &[NodeId], exprs: Vec<Expr>) -> R<Vec<Expr>> {
        let p = self.p;
        let mut out = Vec::new();
        for (i, e) in exprs.into_iter().enumerate() {
            let group = args.get(i).is_some_and(|&a| {
                matches!(
                    p.nodes.get(a),
                    Node::FunctionRef { .. } | Node::FunctionLiteral { .. }
                )
            }) && !matches!(e, Expr::Lambda(..));
            out.push(if group {
                let ty = self.delegate(p.nodes.ty(args[i]))?;
                Expr::Cast(ty, Box::new(e))
            } else {
                e
            });
        }
        Ok(out)
    }
}

/// Whether a value has an `orelse`, a block's value, or a `panic()` in
/// it, outside function literals, which C# writes as statements, as the
/// `throw` of a block `{ panic(); }`.
fn has_statements(p: &temper_tmpl::Program, id: NodeId) -> bool {
    match p.nodes.get(id) {
        Node::OrElse { .. } | Node::BlockValue { .. } | Node::Panic {} => true,
        Node::FunctionLiteral { .. } => false,
        _ => p.nodes.kids(id).iter().any(|&k| has_statements(p, k)),
    }
}
