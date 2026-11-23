//! Function values in [`to_tmpl`](super): function literals with their
//! captures, references to declared functions and static methods, calls
//! of function values, and core's `forEach`, which Kotlin Temper inlines
//! (its parameter is `@inlineUnrealizedGoal`), as `for … of` loops are
//! (notes/tmpl/nodes.md, "Function values").

use super::{Translator, Tx};
use crate::core::{self, LET_VAR};
use crate::typing::Resolved;
use temper_syntax::tree::{NodeId as FNode, NodeKind};
use temper_tmpl::{DeclId, DeclKind, Facts, NodeId, TypeData, TypeId};

impl Translator<'_> {
    /// A function literal, `fn (x) { … }` or a trailing block, typed as
    /// typing gives it, a `@fun` interface or a function type, with the
    /// locals around it that it uses as its captures.
    pub(super) fn literal(&mut self, f: FNode) -> Tx<NodeId> {
        if !core::fn_supers(&self.s.library.tree, f).is_empty() {
            return self.generator_literal(f);
        }
        let ty = self.expr_ty(f)?;
        let Some(sig) = self.b.fun_signature(ty) else {
            return self.not_yet(f, "a function literal of another type");
        };
        let decl = self
            .b
            .declare_made(DeclKind::Lambda, &["fn"], self.owner, sig);
        let span = self.span(f);
        self.b.set_decl_spans(decl, span, span);
        let (params, body) = self.function_parts(f, decl)?;
        let made = self.b.function_literal(ty, decl, &params, body);
        let captures = temper_tmpl::analysis::captures(self.b.program(), made);
        self.b.set_captures(decl, &captures);
        Ok(self.own(made, f))
    }

    /// A generator's literal, `{ (x: T): GeneratorResult<Y> extends
    /// GeneratorFn => … }`: a factory, typed `fn (T): SafeGenerator<Y>`
    /// as typing gives it, marked [`Facts::GENERATOR`], whose body may
    /// `await` and `yield`.
    pub(super) fn generator_literal(&mut self, f: FNode) -> Tx<NodeId> {
        let ty = self.expr_ty(f)?;
        let made = self.b.program().types.signature(ty).map(|s| s.pass);
        let yields = made.and_then(|m| self.b.program().types.args(m).first().copied());
        if yields.is_none() {
            return self.not_yet(f, "a generator of unknown type");
        }
        // What backends that write generators as state machines call
        // (`temper_tmpl::coroutines`), declared with it.
        for helper in [
            "ValueResult.constructor",
            "doneResult",
            "empty",
            "safeAdaptGeneratorFn",
            "awakeUpon",
            "getPromiseResultSync",
        ] {
            if self.core_decl(helper).is_none() {
                return self.not_yet(f, format!("core's {helper}"));
            }
        }
        let decl = self
            .b
            .declare_made(DeclKind::Lambda, &["fn"], self.owner, ty);
        self.b.add_facts(decl, Facts::GENERATOR);
        let span = self.span(f);
        self.b.set_decl_spans(decl, span, span);
        let (params, body) = self.function_parts_with(f, decl, TypeId::VOID)?;
        // A step reads its parameters as captures, which can't change.
        let p = self.b.program();
        if (params.iter()).any(|&q| match p.nodes.get(q) {
            temper_tmpl::Node::Param { decl: d } => p.decls.get(d).facts.contains(Facts::VAR),
            _ => false,
        }) {
            return self.not_yet(f, "a generator's var parameter");
        }
        let made = self.b.function_literal(ty, decl, &params, body);
        let captures = temper_tmpl::analysis::captures(self.b.program(), made);
        self.b.set_captures(decl, &captures);
        Ok(self.own(made, f))
    }

    /// What the generator literal being translated yields, `Y`, if its
    /// factory makes a `SafeGenerator<Y>` whose `Y` isn't `Empty`.
    pub(super) fn yielded_type(&mut self) -> Option<TypeId> {
        let empty = self.core_decl("Empty");
        let p = self.b.program();
        let made = p.signature(self.owner)?.pass;
        let &y = p.types.args(made).first()?;
        match p.types.get(y) {
            TypeData::Nominal { decl, .. } if Some(decl) == empty => None,
            _ => Some(y),
        }
    }

    /// `async { … }`: core's `async`, called with the generator's factory.
    pub(super) fn async_call(&mut self, node: FNode, args: &[FNode]) -> Tx<NodeId> {
        let [f] = args[..] else {
            return self.not_yet(node, "async's arguments");
        };
        let Some(launch) = self.core_decl("async") else {
            return self.not_yet(node, "builtin async");
        };
        let factory = self.expr(f)?;
        let made = self.b.call(launch, &[factory]);
        Ok(self.own(made, node))
    }

    /// `await promise`, which may bubble, if the promise is broken.
    pub(super) fn await_expr(&mut self, node: FNode) -> Tx<NodeId> {
        let Some(&promise) = self.children(node).first() else {
            return self.not_yet(node, "an await's form");
        };
        let promise = self.expr(promise)?;
        // The promise's `T`, which typing leaves to it.
        let p = self.b.program();
        let ty = p.types.args(p.nodes.ty(promise)).first().copied();
        let Some(ty) = ty else {
            return self.not_yet(node, "an await of other than a promise");
        };
        let made = self.b.await_promise(ty, promise);
        Ok(self.own(made, node))
    }

    /// A declared function or static method as a value, typed as its place
    /// wants, as typing gives it.
    pub(super) fn function_ref(&mut self, decl: DeclId, node: FNode) -> Tx<NodeId> {
        if !self.b.program().decls.type_params(decl).is_empty() {
            return self.not_yet(node, "a generic function as a value");
        }
        let ty = self.expr_ty(node)?;
        let sig = self.b.program().decls.get(decl).ty;
        if self.b.fun_signature(ty) != Some(sig) {
            return self.not_yet(node, "a function as a value of another type");
        }
        let made = self.b.function_ref(ty, decl);
        Ok(self.own(made, node))
    }

    /// A call of a function value: a local, parameter, field, or call
    /// result whose type is a `@fun` interface or a function type.
    pub(super) fn call_value(&mut self, node: FNode, callee: FNode, args: &[FNode]) -> Tx<NodeId> {
        let args = self.plain_args(node, args)?;
        let f = self.expr(callee)?;
        self.call_value_of(node, f, &args)
    }

    /// A call of `f`, a function value already translated, with `args`.
    pub(super) fn call_value_of(&mut self, node: FNode, f: NodeId, args: &[FNode]) -> Tx<NodeId> {
        let fty = self.b.program().nodes.ty(f);
        let Some(sig) = self.b.fun_signature(fty) else {
            return self.not_yet(node, "a call of a value of another type");
        };
        let (params, optional) = (self.b.program().types.signature(sig))
            .map_or_else(Default::default, |s| {
                (s.params.to_vec(), s.optional.to_vec())
            });
        let rest = self.omit_rest(&params, &optional, args.len(), node)?;
        let mut values = Vec::new();
        for (&arg, &ty) in args.iter().zip(&params) {
            let value = self.expr(arg)?;
            values.push(self.fit(value, ty));
        }
        values.extend(rest);
        let made = self.b.call_value(f, &values);
        Ok(self.own(made, node))
    }

    /// `xs.forEach(f)`'s list (or string) and function, if `call` is a
    /// call of core's `List.forEach` or `String.forEach`.
    pub(super) fn for_each_parts(&self, call: FNode) -> Option<(FNode, FNode)> {
        if self.kind(call) != NodeKind::Call {
            return None;
        }
        let Some(Resolved::Builtin(key)) = self.s.typing.resolved(call) else {
            return None;
        };
        if key != "List.forEach" && key != "String.forEach" {
            return None;
        }
        let children = self.children(call);
        let object = self.dot_object(children[0])?;
        match children[1..] {
            [f] if self.s.library.symbol(f).is_none() => Some((object, f)),
            _ => None,
        }
    }

    /// `xs.forEach(f)`, inlined as core's own body, as Kotlin Temper does:
    /// `let n = xs.length; var i = 0; while (i < n) { let x = xs[i]; i +=
    /// 1; … }`, where a literal's parameter is `x` and its body follows,
    /// in the function around it (so `return` returns from that, as
    /// Kotlin's does), and another value is called with `x`. `xs` is
    /// held in a local first unless it's a `let`'s read. A `for … of`
    /// loop (`each`) gives its label, which its `break` and `continue`
    /// name.
    ///
    /// A string's, `s.forEach(f)`, is as its core body too, with its
    /// step before the body, as a list's is: `var i = String.begin; while
    /// (s.hasIndex(i)) { let c = s[i]; i = s.next(i); … }`.
    pub(super) fn for_each(
        &mut self,
        call: FNode,
        object: FNode,
        f: FNode,
        each: Option<FNode>,
    ) -> Tx<NodeId> {
        let at = each.unwrap_or(call);
        let list = self.expr(object)?;
        let list_ty = self.b.program().nodes.ty(list);
        let is_string = list_ty == TypeId::STRING;
        let core = |t: &mut Self, key: &str| t.core_decl(key);
        // `length` is `hasIndex` for a string. Looked up in this order,
        // which numbers them in the output.
        let (length, get) = if is_string {
            (core(self, "String.hasIndex"), core(self, "String.get"))
        } else {
            (core(self, "List.length"), core(self, "List.get"))
        };
        let (Some(length), Some(get)) = (length, get) else {
            return self.not_yet(call, "core's List.forEach");
        };
        let (begin, next) = if is_string {
            let (Some(begin), Some(next)) = (core(self, "String.begin"), core(self, "String.next"))
            else {
                return self.not_yet(call, "core's String.forEach");
            };
            (Some(begin), Some(next))
        } else {
            (None, None)
        };
        let element = match is_string {
            true => Some(TypeId::INT32),
            false => self.b.program().types.args(list_ty).first().copied(),
        };
        let Some(element) = element else {
            return self.not_yet(call, "core's List.forEach on another type");
        };
        let mut stmts = Vec::new();
        let plain_read = match self.b.program().nodes.get(list) {
            temper_tmpl::Node::Local { local } => {
                !self.b.program().decls.get(local).facts.contains(Facts::VAR)
            }
            temper_tmpl::Node::GetModuleLet { target } => !self
                .b
                .program()
                .decls
                .get(target)
                .facts
                .contains(Facts::VAR),
            _ => false,
        };
        let list_read = |t: &mut Self, held: Option<DeclId>| -> NodeId {
            match held {
                Some(d) => {
                    let read = t.b.local(d);
                    t.made(read, object)
                }
                None => t.expr(object).expect("translated once"),
            }
        };
        let held = if plain_read {
            None
        } else {
            let names: &[&str] = if is_string { &["string"] } else { &["list"] };
            let d = self
                .b
                .declare_made(DeclKind::Local, names, self.owner, list_ty);
            let span = self.span(object);
            self.b.set_decl_spans(d, span, span);
            let init = self.b.let_stmt(d, Some(list));
            stmts.push(self.made(init, object));
            Some(d)
        };
        let index_ty = match begin {
            Some(begin) => self.b.program().decls.get(begin).ty,
            None => TypeId::INT32,
        };
        let span = self.span(at);
        let n = (!is_string).then(|| {
            let n = self
                .b
                .declare_made(DeclKind::Local, &["length"], self.owner, TypeId::INT32);
            self.b.set_decl_spans(n, span, span);
            n
        });
        let i = self
            .b
            .declare_made(DeclKind::Local, &["i"], self.owner, index_ty);
        self.b.add_facts(i, Facts::VAR);
        self.b.set_decl_spans(i, span, span);
        let receiver = if held.is_some() {
            list_read(self, held)
        } else {
            list
        };
        if let Some(begin) = begin {
            // `var i = String.begin`; the string's first read is
            // `hasIndex`'s, below.
            let start = self.b.get_static(begin);
            let start = self.made(start, at);
            let init = self.b.let_stmt(i, Some(start));
            stmts.push(self.made(init, at));
        } else if let Some(n) = n {
            let len = self.b.call_method(length, receiver, &[]);
            let len = self.made(len, at);
            let init = self.b.let_stmt(n, Some(len));
            stmts.push(self.made(init, at));
            let zero = self.b.int32(0);
            let zero = self.made(zero, at);
            let init = self.b.let_stmt(i, Some(zero));
            stmts.push(self.made(init, at));
        }
        let label = self
            .b
            .declare_made(DeclKind::Label, &["loop"], self.owner, TypeId::NONE);
        self.b.set_decl_spans(label, span, span);
        if let Some(each) = each {
            self.loops.insert(each, label);
            // A `continue` in the body leaves the body's function
            // ([`FN_LOOP_BODY`](crate::core::FN_LOOP_BODY)), so it targets
            // that, and continues the loop, its step being done already.
            if self.kind(f) == NodeKind::Fn {
                self.loops.insert(f, label);
            }
        }
        let cond = if let (true, None) = (is_string, n) {
            // `s.hasIndex(i)`.
            let ri = self.b.local(i);
            let ri = self.made(ri, at);
            self.b.call_method(length, receiver, &[ri])
        } else {
            let n = n.expect("a list's length");
            let (ri, rn) = (self.b.local(i), self.b.local(n));
            let (ri, rn) = (self.made(ri, at), self.made(rn, at));
            let lt = temper_tmpl::ops::Op::named("Int32.lt").expect("an operator");
            self.b.op(lt, &[ri, rn])
        };
        let cond = self.made(cond, at);
        let mut body = Vec::new();
        // `let x = xs[i];`, `x` being the literal's parameter if it is one.
        let literal = (self.kind(f) == NodeKind::Fn).then_some(f);
        let params = literal.map(|f| core::fn_parts(&self.s.library.tree, f).0);
        let param = match params.as_deref() {
            Some(&[p]) if self.kind(p) == NodeKind::Let => Some(p),
            Some(&[]) | None => None,
            Some(_) => return self.not_yet(f, "a forEach block's parameters"),
        };
        let x = match param {
            Some(p) => {
                let name = self
                    .name_of(core::let_parts(&self.s.library.tree, p).0)
                    .to_string();
                let d = self.b.declare(DeclKind::Local, &name, self.owner, element);
                if self.data(p) & LET_VAR != 0 {
                    self.b.add_facts(d, Facts::VAR);
                }
                self.decl_spans(d, p);
                self.decls.insert(p, d);
                d
            }
            None => {
                let d = self
                    .b
                    .declare_made(DeclKind::Local, &["item"], self.owner, element);
                self.b.set_decl_spans(d, span, span);
                d
            }
        };
        let receiver = list_read(self, held);
        let ri = self.b.local(i);
        let ri = self.made(ri, at);
        let item = self.b.call_method(get, receiver, &[ri]);
        let item = self.made(item, at);
        let init = self.b.let_stmt(x, Some(item));
        body.push(self.made(init, at));
        let next = match next {
            // `i = s.next(i)`.
            Some(next) => {
                let receiver = list_read(self, held);
                let ri = self.b.local(i);
                let ri = self.made(ri, at);
                self.b.call_method(next, receiver, &[ri])
            }
            None => {
                let (ri, one) = (self.b.local(i), self.b.int32(1));
                let (ri, one) = (self.made(ri, at), self.made(one, at));
                let add = temper_tmpl::ops::Op::named("Int32.add").expect("an operator");
                self.b.op(add, &[ri, one])
            }
        };
        let next = self.made(next, at);
        let step = self.b.assign(i, next);
        body.push(self.made(step, at));
        match literal {
            Some(f) => {
                let block = core::fn_parts(&self.s.library.tree, f).2;
                let Some(block) = block else {
                    return self.not_yet(f, "a forEach block without a body");
                };
                self.stmts(block, &mut body)?;
            }
            None => {
                let value = self.expr(f)?;
                let read = self.b.local(x);
                let read = self.made(read, f);
                let fty = self.b.program().nodes.ty(value);
                let param_ty = self
                    .b
                    .fun_signature(fty)
                    .and_then(|s| {
                        self.b
                            .program()
                            .types
                            .signature(s)
                            .map(|s| s.params.to_vec())
                    })
                    .and_then(|p| p.first().copied())
                    .unwrap_or(element);
                let read = self.fit(read, param_ty);
                let called = self.b.call_value(value, &[read]);
                let called = self.made(called, f);
                let stmt = self.b.expr_statement(called);
                body.push(self.made(stmt, f));
            }
        }
        let body = self.b.block(&body);
        let body = self.made(body, at);
        let looped = self.b.while_loop(label, cond, body);
        stmts.push(self.own(looped, at));
        let block = self.b.block(&stmts);
        Ok(self.made(block, at))
    }
}
