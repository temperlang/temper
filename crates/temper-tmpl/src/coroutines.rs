//! Coroutines' lowerings: how generator literals, whose bodies `Await`
//! and `Yield` ([`Facts::GENERATOR`]), become what a backend writes
//! (notes/tmpl/nodes.md, "Coroutines"). Each backend picks a
//! [`CoroutineStrategy`], as Kotlin Temper's do, and calls [`lower`];
//! tmpl never lists backends.
//!
//! A generator literal's value is a factory, `fn (): SafeGenerator<Y>`,
//! whose calls make a generator that runs its body a step at a time:
//! each step resumes after the last pause and runs until the next, a
//! `yield`, which gives the step's caller its value, or an `await` of a
//! promise not resolved yet, which resumes it once the promise is
//! resolved, or until the body ends.

use crate::analysis::walk;
use crate::decls::{DeclKind, Facts};
use crate::ids::{DeclId, NodeId, Span, TypeId};
use crate::nodes::Flags;
use crate::schema::{Kind, Node};
use crate::{Builder, Program};
use std::collections::HashMap;

/// How a backend writes generator literals, as Kotlin's
/// `CoroutineStrategy`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum CoroutineStrategy {
    /// As the target's own generators (Python's and JS's `yield`, Lua's
    /// coroutines, C#'s iterators): tmpl stays as it is.
    TranslateToGenerator,
    /// As regular functions: each literal becomes a factory whose body
    /// declares the state that lasts between steps (its locals, which
    /// step is next, and the promise awaited), and returns
    /// `safeAdaptGeneratorFn(step)`, where `step`, a state machine, runs
    /// one step each call, returning `ValueResult(x)` for a `yield(x)`
    /// (or `ValueResult(empty())`), and `doneResult()` once it's done.
    /// An `await` stores its promise, sets the next
    /// state, calls `awakeUpon(promise, generator)`, which steps the
    /// generator again once the promise is resolved, and returns; the
    /// next step reads the result by `getPromiseResultSync(promise)`,
    /// which bubbles if the promise is broken.
    TranslateToRegularFunction,
}

/// The program with its generator literals lowered for `strategy`, or
/// `None` where nothing changes. Fails, saying why, on what it doesn't
/// cover yet.
pub fn lower(p: &Program, strategy: CoroutineStrategy) -> Result<Option<Program>, String> {
    if strategy == CoroutineStrategy::TranslateToGenerator {
        return Ok(None);
    }
    let mut literals = Vec::new();
    for m in &p.modules {
        for root in m.roots() {
            post_order(p, root, &mut |id| {
                if let Node::FunctionLiteral { decl, .. } = p.nodes.get(id) {
                    if p.decls.get(decl).facts.contains(Facts::GENERATOR) {
                        literals.push(id);
                    }
                }
            });
        }
    }
    if literals.is_empty() {
        return Ok(None);
    }
    let mut b = Builder::from_program(p.clone());
    let helpers = Helpers::find(b.program())?;
    for literal in literals {
        to_state_machine(&mut b, &helpers, literal)?;
    }
    // Every literal's captures, which moving code into steps changes.
    let mut all = Vec::new();
    let p = b.program();
    for m in &p.modules {
        for root in m.roots() {
            walk(p, root, &mut |id| {
                if let Node::FunctionLiteral { decl, .. } = p.nodes.get(id) {
                    all.push((id, decl));
                }
            });
        }
    }
    for (id, decl) in all {
        let captures = crate::analysis::captures(b.program(), id);
        b.set_captures(decl, &captures);
    }
    b.renumber();
    Ok(Some(b.finish()))
}

/// Visits children before parents, so inner literals come first.
fn post_order(p: &Program, id: NodeId, visit: &mut impl FnMut(NodeId)) {
    for &kid in p.nodes.kids(id) {
        post_order(p, kid, visit);
    }
    visit(id);
}

/// Core's declarations that state machines use, which the frontend
/// declares with each generator literal.
struct Helpers {
    empty: DeclId,
    value_result: DeclId,
    generator_result: DeclId,
    done_result: DeclId,
    adapt: DeclId,
    awake_upon: DeclId,
    get_result: DeclId,
}

impl Helpers {
    fn find(p: &Program) -> Result<Helpers, String> {
        let find = |owner: Option<&str>, name: &str| -> Result<DeclId, String> {
            (p.decls.ids())
                .filter(|&d| p.decls.contains(d) && p.name(d) == name)
                .find(|&d| {
                    let o = p.decls.get(d).owner;
                    match owner {
                        Some(owner) => p.decls.contains(o) && p.name(o) == owner,
                        None => p.decls.contains(o) && p.decls.get(o).kind == DeclKind::Library,
                    }
                })
                .ok_or_else(|| format!("core's {name}, which a state machine needs"))
        };
        Ok(Helpers {
            empty: find(None, "empty")?,
            value_result: find(Some("ValueResult"), "constructor")?,
            generator_result: find(None, "GeneratorResult")?,
            done_result: find(None, "doneResult")?,
            adapt: find(None, "safeAdaptGeneratorFn")?,
            awake_upon: find(None, "awakeUpon")?,
            get_result: find(None, "getPromiseResultSync")?,
        })
    }
}

/// One state: its statements, and the state whose code handles a bubble
/// in them (an `orelse`'s fallback), if any.
struct State {
    stmts: Vec<NodeId>,
    handler: Option<usize>,
    /// Whether it ends, by a jump or a return, so nothing more goes in it.
    done: bool,
}

/// Where a value goes.
#[derive(Clone, Copy)]
enum Sink {
    Discard,
    Assign(DeclId),
    /// Assigned to a local made nullable, as `T?`.
    AssignNullable(DeclId),
}

struct Machine<'a> {
    b: &'a mut Builder,
    h: &'a Helpers,
    /// The literal, which stays the factory.
    literal: DeclId,
    step: DeclId,
    /// The step's parameter, the generator, which `awakeUpon` steps.
    generator: DeclId,
    /// Which state runs next.
    state: DeclId,
    /// The step's loop.
    label: DeclId,
    yields: TypeId,
    states: Vec<State>,
    current: usize,
    handler: Option<usize>,
    /// The loops broken up into states: where `continue` and `break` go.
    loops: HashMap<DeclId, (usize, usize)>,
    /// The locals the factory declares, with their first values.
    hoisted: Vec<(DeclId, NodeId)>,
    /// Every state number set, as its literal, and the state it names,
    /// which [`Machine::step_body`] renumbers.
    targets: Vec<(NodeId, usize)>,
}

type R<T> = Result<T, String>;

/// Whether a node, outside literals in it, may pause: an `Await` or a
/// `Yield` in it.
pub fn pauses(p: &Program, id: NodeId) -> bool {
    match p.nodes.get(id) {
        Node::Await { .. } | Node::Yield { .. } => true,
        Node::FunctionLiteral { .. } => false,
        _ => p.nodes.kids(id).iter().any(|&k| pauses(p, k)),
    }
}

/// Calls `visit` on `id` and everything under it, but not inside
/// function literals.
fn walk_own(p: &Program, id: NodeId, visit: &mut impl FnMut(NodeId)) {
    visit(id);
    if matches!(p.nodes.get(id), Node::FunctionLiteral { .. }) {
        return;
    }
    for &kid in p.nodes.kids(id) {
        walk_own(p, kid, visit);
    }
}

/// A local's first value where it's declared before it's set: its type's
/// zero, or null, or `None` for a type with neither.
fn zero(b: &mut Builder, ty: TypeId) -> Option<NodeId> {
    Some(match ty {
        TypeId::BOOLEAN => b.bool_lit(false),
        TypeId::INT32 => b.int32(0),
        TypeId::INT64 => b.int64(0),
        TypeId::FLOAT64 => b.float64(0.0),
        TypeId::STRING => b.string(""),
        _ if b.program().types.is_nullable(ty) => b.null(ty),
        _ => return None,
    })
}

fn to_state_machine(b: &mut Builder, h: &Helpers, literal: NodeId) -> R<()> {
    let Node::FunctionLiteral { decl, body, .. } = b.program().nodes.get(literal) else {
        return Err("a generator that isn't a literal".into());
    };
    let (nodes_before, decls_before) = (b.program().nodes.len(), b.program().decls.len());
    let p = b.program();
    let Some(sig) = p.signature(decl) else {
        return Err("a generator without a signature".into());
    };
    let made = sig.result;
    let Some(&yields) = p.types.args(made).first() else {
        return Err("a generator whose result isn't SafeGenerator<Y>".into());
    };
    let gen_result = b.applied(h.generator_result, &[yields]);
    let step_ty = b.function_type(&[made], gen_result, false);
    let step = b.declare_made(DeclKind::Lambda, &["convertedCoroutine"], decl, step_ty);
    let generator = b.declare_made(DeclKind::Param, &["generator"], step, made);
    let state = b.declare_made(DeclKind::Local, &["caseIndex"], decl, TypeId::INT32);
    b.add_facts(state, Facts::VAR);
    let label = b.declare_made(DeclKind::Label, &["loop"], step, TypeId::NONE);
    let mut m = Machine {
        b,
        h,
        literal: decl,
        step,
        generator,
        state,
        label,
        yields,
        states: Vec::new(),
        current: 0,
        handler: None,
        loops: HashMap::new(),
        hoisted: Vec::new(),
        targets: Vec::new(),
    };
    m.hoist_locals(body)?;
    let first = m.new_state();
    m.current = first;
    // Its statements, not the block, whose id the factory's body takes.
    let stmts: Vec<NodeId> = m.p().nodes.kids(body).to_vec();
    for s in stmts {
        m.stmt(s)?;
    }
    m.finish_done();
    let step_body = m.step_body();
    let b = m.b;
    // The step's code was the literal's: its labels and literals are the
    // step's now.
    let mut owned = Vec::new();
    walk_own(
        b.program(),
        step_body,
        &mut |id| match b.program().nodes.get(id) {
            Node::While { label, .. } | Node::DoWhile { label, .. } => owned.push(label),
            Node::FunctionLiteral { decl: d, .. } => owned.push(d),
            Node::Let { local, .. } => owned.push(local),
            _ => {}
        },
    );
    for d in owned {
        if b.program().decls.get(d).owner == decl {
            b.set_owner(d, step);
        }
    }
    let param = b.param(generator);
    let step_literal = b.function_literal(step_ty, step, &[param], step_body);
    let adapted = b.call_generic(h.adapt, &[yields], &[step_literal]);
    let mut stmts = Vec::new();
    let zero_state = b.int32(0);
    stmts.push(b.let_stmt(state, Some(zero_state)));
    for (local, init) in std::mem::take(&mut m.hoisted) {
        stmts.push(b.let_stmt(local, Some(init)));
    }
    stmts.push(b.return_stmt(Some(adapted)));
    let block = b.block(&stmts);
    b.replace_with(body, block);
    b.remove_facts(decl, Facts::GENERATOR);
    // The loops broken up into states are gone, with their labels.
    for label in m.loops.keys().copied().collect::<Vec<_>>() {
        b.remove_decl(label);
    }
    // What it made comes from the literal.
    if b.program().has_spans {
        let span = b.program().nodes.span(literal);
        for i in nodes_before..b.program().nodes.len() {
            let id = NodeId(i as u32);
            if b.program().nodes.span(id) == Span::UNKNOWN {
                b.set_span(id, span, true);
            }
        }
        for i in decls_before..b.program().decls.len() {
            let d = DeclId(i as u32);
            if b.program().decls.contains(d) {
                b.set_decl_spans(d, span, span);
            }
        }
    }
    Ok(())
}

impl Machine<'_> {
    fn p(&self) -> &Program {
        self.b.program()
    }

    /// Declares each of the body's locals in the factory instead, so they
    /// last between steps: each `let` becomes an assignment (or nothing),
    /// and a local whose type has no zero is made nullable, read through
    /// `nonNull`, where all its reads follow an assignment.
    fn hoist_locals(&mut self, body: NodeId) -> R<()> {
        let mut lets = Vec::new();
        walk_own(self.p(), body, &mut |id| {
            if let Node::Let { local, init } = self.p().nodes.get(id) {
                lets.push((id, local, init));
            }
        });
        self.copy_loop_captures(body)?;
        let mut nullable = Vec::new();
        for &(_, local, _) in &lets {
            let ty = self.p().decls.get(local).ty;
            let init = match zero(self.b, ty) {
                Some(init) => init,
                None => {
                    let ty = self.b.nullable(ty);
                    self.b.set_decl_type(local, ty);
                    nullable.push(local);
                    self.b.null(ty)
                }
            };
            self.b.add_facts(local, Facts::VAR);
            self.hoisted.push((local, init));
        }
        if !nullable.is_empty() {
            // Reads and assignments anywhere in the body, literals in it
            // too.
            let mut reads = Vec::new();
            let mut values = Vec::new();
            walk(self.p(), body, &mut |id| match self.p().nodes.get(id) {
                Node::Local { local } if nullable.contains(&local) => reads.push(id),
                Node::Assign { local, value } if nullable.contains(&local) => values.push(value),
                _ => {}
            });
            for id in reads {
                let inner = self.b.moved(id);
                let ty = self.p().nodes.ty(inner);
                let local = self.b.local(match self.p().nodes.get(inner) {
                    Node::Local { local } => local,
                    _ => unreachable!(),
                });
                let read = self.b.raw(Kind::NonNull, ty, (0, 0), Flags::NONE, &[local]);
                self.b.replace_with(id, read);
            }
            for id in values {
                self.wrap_nullable(id);
            }
        }
        for (id, local, init) in lets {
            let replacement = match init {
                Some(init) => {
                    if nullable.contains(&local) {
                        self.wrap_nullable(init);
                    }
                    self.b.assign(local, init)
                }
                None => self.b.block(&[]),
            };
            self.b.replace_with(id, replacement);
        }
        Ok(())
    }

    /// Gives each closure that captures a local `let` in a loop its own
    /// copy, made where the closure is, as a function's loop gives each
    /// pass its own binding, but the factory has only one. A copy is the
    /// same binding only where nothing assigns the local after its `let`,
    /// so it fails on others. Closures that capture none stay as they are.
    fn copy_loop_captures(&mut self, body: NodeId) -> R<()> {
        fn find(p: &Program, id: NodeId, in_loop: bool, found: &mut (Vec<DeclId>, Vec<NodeId>)) {
            match p.nodes.get(id) {
                Node::FunctionLiteral { .. } => return found.1.push(id),
                Node::Let { local, .. } if in_loop => found.0.push(local),
                _ => {}
            }
            let in_loop = in_loop || matches!(p.nodes.kind(id), Kind::While | Kind::DoWhile);
            for &kid in p.nodes.kids(id) {
                find(p, kid, in_loop, found);
            }
        }
        let mut found = (Vec::new(), Vec::new());
        find(self.p(), body, false, &mut found);
        let (in_loop, literals) = found;
        if in_loop.is_empty() || literals.is_empty() {
            return Ok(());
        }
        let mut assigned = Vec::new();
        walk(self.p(), body, &mut |id| {
            if let Node::Assign { local, .. } = self.p().nodes.get(id) {
                assigned.push(local);
            }
        });
        for literal in literals {
            let captured: Vec<DeclId> = crate::analysis::captures(self.p(), literal)
                .into_iter()
                .filter(|d| in_loop.contains(d))
                .collect();
            if captured.is_empty() {
                continue;
            }
            let mut copies = Vec::new();
            let mut renamed = HashMap::new();
            for local in captured {
                if assigned.contains(&local) {
                    return Err(format!(
                        "a closure in a generator's loop capturing {}, which is assigned after its `let`",
                        self.p().name(local)
                    ));
                }
                let ty = self.p().decls.get(local).ty;
                let name = self.p().name(local).to_string();
                let copy = self
                    .b
                    .declare_made(DeclKind::Local, &[&name], self.literal, ty);
                let read = self.b.local(local);
                copies.push(self.b.let_stmt(copy, Some(read)));
                renamed.insert(local, copy);
            }
            let mut reads = Vec::new();
            walk(self.p(), literal, &mut |id| {
                if let Node::Local { local } = self.p().nodes.get(id) {
                    if renamed.contains_key(&local) {
                        reads.push((id, local));
                    }
                }
            });
            for (id, local) in reads {
                let read = self.b.local(renamed[&local]);
                self.b.replace_with(id, read);
            }
            let ty = self.p().nodes.ty(literal);
            let inner = self.b.moved(literal);
            let wrapped = self.b.block_value(ty, &copies, inner);
            self.b.replace_with(literal, wrapped);
        }
        Ok(())
    }

    /// Makes the value at `id` nullable, where it stays.
    fn wrap_nullable(&mut self, id: NodeId) {
        let inner = self.b.moved(id);
        let wrapped = self.b.as_nullable(inner);
        self.b.replace_with(id, wrapped);
    }

    fn new_state(&mut self) -> usize {
        self.states.push(State {
            stmts: Vec::new(),
            handler: self.handler,
            done: false,
        });
        self.states.len() - 1
    }

    fn start(&mut self, state: usize) {
        self.current = state;
    }

    /// Adds a statement to the current state, or where it has ended, to a
    /// new one that nothing reaches.
    fn emit(&mut self, stmt: NodeId) {
        if self.states[self.current].done {
            let dead = self.new_state();
            self.current = dead;
        }
        self.states[self.current].stmts.push(stmt);
    }

    fn end(&mut self) {
        self.states[self.current].done = true;
    }

    fn set_state(&mut self, to: usize) -> NodeId {
        let n = self.b.int32(to as i32);
        self.targets.push((n, to));
        self.b.assign(self.state, n)
    }

    /// Goes to state `to` next.
    fn jump(&mut self, to: usize) {
        if self.states[self.current].done {
            return;
        }
        let set = self.set_state(to);
        self.emit(set);
        self.end();
    }

    /// Goes to `then` or `els` by `cond`; straight to one where `cond`
    /// is a literal, as `while (true)`'s is, which leaves the other
    /// unreached, unless something else jumps there.
    fn branch(&mut self, cond: NodeId, then: usize, els: usize) {
        if let Node::BoolLit { value } = self.p().nodes.get(cond) {
            self.jump(if value { then } else { els });
            return;
        }
        let a = self.set_state(then);
        let a = self.b.block(&[a]);
        let e = self.set_state(els);
        let e = self.b.block(&[e]);
        let made = self.b.if_stmt(cond, a, Some(e));
        self.emit(made);
        self.end();
    }

    /// `ValueResult(value)`, or with none, `ValueResult(empty())`, as a
    /// `GeneratorResult<Y>`.
    fn paused(&mut self, value: Option<NodeId>) -> NodeId {
        let value = match value {
            Some(value) => value,
            None => self.b.call(self.h.empty, &[]),
        };
        let value = self
            .b
            .new_generic(self.h.value_result, &[self.yields], &[value]);
        let ty = self.b.applied(self.h.generator_result, &[self.yields]);
        self.b.upcast(ty, value)
    }

    /// `return doneResult<Y>()`, as a `GeneratorResult<Y>`.
    fn return_done(&mut self) -> NodeId {
        let done = self.b.call_generic(self.h.done_result, &[self.yields], &[]);
        let ty = self.b.applied(self.h.generator_result, &[self.yields]);
        let done = self.b.upcast(ty, done);
        self.b.return_stmt(Some(done))
    }

    /// Ends the generator: the step says it's done, and its state stays
    /// -1, which the step set first.
    fn finish_done(&mut self) {
        if self.states[self.current].done {
            return;
        }
        let done = self.return_done();
        self.emit(done);
        self.end();
    }

    /// Pauses, yielding `value`, if any, to resume at a new state, which
    /// it starts.
    fn pause(&mut self, before_return: Option<NodeId>, value: Option<NodeId>) -> usize {
        let resume = self.new_state();
        let set = self.set_state(resume);
        self.emit(set);
        if let Some(stmt) = before_return {
            self.emit(stmt);
        }
        let value = self.paused(value);
        let ret = self.b.return_stmt(Some(value));
        self.emit(ret);
        self.end();
        self.start(resume);
        resume
    }

    fn stmt(&mut self, id: NodeId) -> R<()> {
        if !pauses(self.p(), id) {
            return self.keep(id);
        }
        match self.p().nodes.get(id) {
            Node::Block { stmts } => {
                let stmts: Vec<NodeId> = self.p().nodes.list(stmts).to_vec();
                for s in stmts {
                    self.stmt(s)?;
                }
            }
            Node::Assign { local, value } => self.value_into(value, Sink::Assign(local))?,
            Node::ExprStatement { expr } => self.value_into(expr, Sink::Discard)?,
            Node::Yield { value } => {
                let value = match value {
                    Some(v) if pauses(self.p(), v) => {
                        let ty = self.p().nodes.ty(v);
                        Some(self.paused_value(v, ty)?)
                    }
                    v => v,
                };
                self.pause(None, value);
            }
            Node::If { cond, then, els } => {
                let cond = self.condition(cond)?;
                let (a, join) = (self.new_state(), self.new_state());
                let e = if els.is_some() {
                    self.new_state()
                } else {
                    join
                };
                self.branch(cond, a, e);
                self.start(a);
                self.stmt(then)?;
                self.jump(join);
                if let Some(els) = els {
                    self.start(e);
                    self.stmt(els)?;
                    self.jump(join);
                }
                self.start(join);
            }
            Node::While { label, cond, body } => {
                let head = self.new_state();
                self.jump(head);
                self.start(head);
                let cond = self.condition(cond)?;
                let (inside, exit) = (self.new_state(), self.new_state());
                self.branch(cond, inside, exit);
                self.loops.insert(label, (head, exit));
                self.start(inside);
                self.stmt(body)?;
                self.jump(head);
                self.start(exit);
            }
            Node::DoWhile { label, body, cond } => {
                let (inside, test, exit) = (self.new_state(), self.new_state(), self.new_state());
                self.jump(inside);
                self.loops.insert(label, (test, exit));
                self.start(inside);
                self.stmt(body)?;
                self.jump(test);
                self.start(test);
                let cond = self.condition(cond)?;
                self.branch(cond, inside, exit);
                self.start(exit);
            }
            _ => return Err("a pause in this kind of statement".into()),
        }
        Ok(())
    }

    /// A condition, its pauses first put in a local.
    fn condition(&mut self, cond: NodeId) -> R<NodeId> {
        if !pauses(self.p(), cond) {
            return Ok(cond);
        }
        let t = self.temp(TypeId::BOOLEAN);
        self.value_into(cond, Sink::Assign(t))?;
        Ok(self.b.local(t))
    }

    /// A value that pauses, put in a local first, and read from it.
    fn paused_value(&mut self, e: NodeId, ty: TypeId) -> R<NodeId> {
        let t = self.temp(ty);
        let nullable = self.p().decls.get(t).ty != ty;
        let to = if nullable {
            Sink::AssignNullable(t)
        } else {
            Sink::Assign(t)
        };
        self.value_into(e, to)?;
        let read = self.b.local(t);
        Ok(if nullable {
            self.b.non_null(read)
        } else {
            read
        })
    }

    /// A local of the factory's for a value across a pause.
    fn temp(&mut self, ty: TypeId) -> DeclId {
        let (ty, init) = match zero(self.b, ty) {
            Some(init) => (ty, init),
            None => {
                let ty = self.b.nullable(ty);
                (ty, self.b.null(ty))
            }
        };
        let t = self
            .b
            .declare_made(DeclKind::Local, &["t"], self.literal, ty);
        self.b.add_facts(t, Facts::VAR);
        self.hoisted.push((t, init));
        t
    }

    /// A statement that doesn't pause, as it is, but for jumps out of it
    /// to loops broken up into states, and returns, which end the
    /// generator.
    fn keep(&mut self, id: NodeId) -> R<()> {
        match self.p().nodes.get(id) {
            Node::Break { label } if self.loops.contains_key(&label) => {
                self.jump(self.loops[&label].1);
                return Ok(());
            }
            Node::Continue { label } if self.loops.contains_key(&label) => {
                self.jump(self.loops[&label].0);
                return Ok(());
            }
            Node::Return { .. } => {
                self.finish_done();
                return Ok(());
            }
            _ => {}
        }
        let mut jumps = Vec::new();
        walk_own(self.p(), id, &mut |n| match self.p().nodes.get(n) {
            Node::Break { label } | Node::Continue { label } if self.loops.contains_key(&label) => {
                jumps.push(n)
            }
            Node::Return { .. } => jumps.push(n),
            _ => {}
        });
        for n in jumps {
            let mut stmts = Vec::new();
            match self.p().nodes.get(n) {
                Node::Break { label } => {
                    stmts.push(self.set_state(self.loops[&label].1));
                    stmts.push(self.b.continue_loop(self.label));
                }
                Node::Continue { label } => {
                    stmts.push(self.set_state(self.loops[&label].0));
                    stmts.push(self.b.continue_loop(self.label));
                }
                _ => stmts.push(self.return_done()),
            }
            let block = self.b.block(&stmts);
            self.b.replace_with(n, block);
        }
        self.emit(id);
        Ok(())
    }

    /// Puts a value where `sink` says, pausing where it awaits.
    fn value_into(&mut self, e: NodeId, sink: Sink) -> R<()> {
        if !pauses(self.p(), e) {
            self.put(e, sink);
            return Ok(());
        }
        match self.p().nodes.get(e) {
            Node::OrElse { body, fallback } => {
                let outer = self.handler;
                let (fallback_state, join) = (self.new_state(), self.new_state());
                self.handler = Some(fallback_state);
                // In the current state, if nothing's in it yet.
                let here = &mut self.states[self.current];
                if here.stmts.is_empty() && !here.done {
                    here.handler = Some(fallback_state);
                } else {
                    let tried = self.new_state();
                    self.handler = outer;
                    self.jump(tried);
                    self.start(tried);
                    self.handler = Some(fallback_state);
                }
                self.value_into(body, sink)?;
                self.jump(join);
                self.handler = outer;
                self.start(fallback_state);
                self.value_into(fallback, sink)?;
                self.jump(join);
                self.start(join);
            }
            Node::BlockValue { stmts, value } => {
                let stmts: Vec<NodeId> = self.p().nodes.list(stmts).to_vec();
                for s in stmts {
                    self.stmt(s)?;
                }
                self.value_into(value, sink)?;
            }
            Node::AsNullable { value } => match sink {
                Sink::Assign(local) => self.value_into(value, Sink::AssignNullable(local))?,
                _ => return Err("a pause in a value made nullable".into()),
            },
            Node::Await { promise, .. } => {
                let ty = self.p().nodes.ty(e);
                self.await_into(promise, ty, sink)?;
            }
            _ => {
                while let Some(found) = self.first_pause(e)? {
                    let ty = self.p().nodes.ty(found);
                    let moved = self.b.moved(found);
                    let read = self.paused_value(moved, ty)?;
                    self.b.replace_with(found, read);
                }
                self.put(e, sink);
            }
        }
        Ok(())
    }

    /// The part of a value that pauses that runs first, an `await` or an
    /// `orelse`, where what runs before it reads only what it can read
    /// after a pause: locals, module `let`s, and constants.
    fn first_pause(&self, e: NodeId) -> R<Option<NodeId>> {
        let p = self.p();
        if !pauses(p, e) {
            return Ok(None);
        }
        match p.nodes.get(e) {
            Node::Await { promise, .. } => return Ok(self.first_pause(promise)?.or(Some(e))),
            Node::OrElse { .. } | Node::BlockValue { .. } => return Ok(Some(e)),
            _ => {}
        }
        let conditional = matches!(p.nodes.kind(e), Kind::IfValue | Kind::And | Kind::Or);
        for (i, &kid) in p.nodes.kids(e).iter().enumerate() {
            if pauses(p, kid) {
                if conditional && !(p.nodes.kind(e) == Kind::Coalesce && i == 0) {
                    return Err("a pause in a conditional part of a value".into());
                }
                return self.first_pause(kid);
            }
            if p.nodes.kind(e) == Kind::Coalesce && i == 1 {
                return Err("a pause in a conditional part of a value".into());
            }
            let simple = matches!(
                p.nodes.kind(kid),
                Kind::Local
                    | Kind::GetModuleLet
                    | Kind::GetStatic
                    | Kind::This
                    | Kind::BoolLit
                    | Kind::Int32Lit
                    | Kind::Int64Lit
                    | Kind::Float64Lit
                    | Kind::StringLit
                    | Kind::VoidLit
                    | Kind::TypeValue
                    | Kind::Null
                    | Kind::Omitted
            );
            if !simple {
                return Err("a pause after a part of a value that isn't simple".into());
            }
        }
        Ok(None)
    }

    /// `await promise`, whose value goes to `sink`.
    fn await_into(&mut self, promise: NodeId, ty: TypeId, sink: Sink) -> R<()> {
        if pauses(self.p(), promise) {
            return Err("an await of a value that pauses".into());
        }
        let promise_ty = self.p().nodes.ty(promise);
        let held = self.b.nullable(promise_ty);
        let awaited = self
            .b
            .declare_made(DeclKind::Local, &["awaited"], self.literal, held);
        self.b.add_facts(awaited, Facts::VAR);
        let none = self.b.null(held);
        self.hoisted.push((awaited, none));
        let stored = self.b.as_nullable(promise);
        let stored = self.b.assign(awaited, stored);
        self.emit(stored);
        let read = self.b.local(awaited);
        let read = self.b.non_null(read);
        let generator = self.b.local(self.generator);
        let wake = self
            .b
            .call_generic(self.h.awake_upon, &[ty], &[read, generator]);
        let wake = self.b.expr_statement(wake);
        self.pause(Some(wake), None);
        let read = self.b.local(awaited);
        let read = self.b.non_null(read);
        let got = self.b.call_generic(self.h.get_result, &[ty], &[read]);
        self.put(got, sink);
        Ok(())
    }

    /// A value that doesn't pause, put where `sink` says.
    fn put(&mut self, e: NodeId, sink: Sink) {
        let stmt = match sink {
            Sink::Discard => {
                let kind = self.p().nodes.kind(e);
                if matches!(kind, Kind::Local | Kind::VoidLit | Kind::TypeValue) {
                    return;
                }
                self.b.expr_statement(e)
            }
            Sink::Assign(local) => self.b.assign(local, e),
            Sink::AssignNullable(local) => {
                let e = self.b.as_nullable(e);
                self.b.assign(local, e)
            }
        };
        let ends = matches!(self.p().nodes.get(e), Node::Panic {});
        self.emit(stmt);
        if ends {
            self.end();
        }
    }

    /// The step's body: `while (true) { let s = caseIndex; caseIndex =
    /// -1; switch (s) { case 0 { … } … default { return doneResult() } }
    /// }`, each state setting the next, and each state's code in an
    /// `orelse` whose fallback goes to its handler, if it has one. States
    /// that nothing reaches are dropped, jumps through a state that only
    /// jumps go straight on, states that only end the generator go to the
    /// default, as -1, and the rest are numbered in order.
    fn step_body(&mut self) -> NodeId {
        let s = self.b.declare_made(
            DeclKind::Local,
            &["caseIndexLocal"],
            self.step,
            TypeId::INT32,
        );
        let forwarded = self.forwarding();
        let numbers = self.numbering(&forwarded);
        let mut cases = Vec::new();
        for (i, &number) in numbers.iter().enumerate() {
            let Some(number) = number else {
                continue;
            };
            let state = std::mem::replace(
                &mut self.states[i],
                State {
                    stmts: Vec::new(),
                    handler: None,
                    done: true,
                },
            );
            if number < 0 {
                continue;
            }
            let mut stmts = state.stmts;
            if !state.done {
                // Falls off its end: the generator is done, its state
                // already -1.
                stmts.push(self.return_done());
            }
            if let Some(handler) = state.handler {
                let void = self.b.void();
                let body = self.b.block_value(TypeId::VOID, &stmts, void);
                let set = self.set_state(handler);
                let void = self.b.void();
                let fallback = self.b.block_value(TypeId::VOID, &[set], void);
                let tried = self.b.or_else(TypeId::VOID, body, fallback);
                stmts = vec![self.b.expr_statement(tried)];
            }
            let body = self.b.block(&stmts);
            let label = self.b.int32(number);
            cases.push(self.b.case(&[label], body));
        }
        let done = self.return_done();
        let done = self.b.block(&[done]);
        cases.push(self.b.case(&[], done));
        // Each state set, by its new number.
        for (lit, to) in std::mem::take(&mut self.targets) {
            let to = numbers[forwarded[to]].unwrap_or(-1);
            let n = self.b.int32(to);
            self.b.replace_with(lit, n);
        }
        let read = self.b.local(s);
        let switch = self.b.switch_stmt(read, &cases);
        let current = self.b.local(self.state);
        let read = self.b.let_stmt(s, Some(current));
        // A step that fails stays failed: stepped again, it's done.
        let failed = self.b.int32(-1);
        let failed = self.b.assign(self.state, failed);
        let stmts = vec![read, failed, switch];
        // One state, which ends by returning, needs no loop.
        if self.states.len() == 1 {
            self.b.remove_decl(self.label);
            return self.b.block(&stmts);
        }
        let body = self.b.block(&stmts);
        let yes = self.b.bool_lit(true);
        let looped = self.b.while_loop(self.label, yes, body);
        self.b.block(&[looped])
    }

    /// Where a jump to each state ends up, through states that only
    /// jump on; a ring of those stays as written.
    fn forwarding(&self) -> Vec<usize> {
        let targets: HashMap<NodeId, usize> = self.targets.iter().copied().collect();
        let only_jump: Vec<Option<usize>> = (self.states.iter())
            .map(|st| {
                let &[only] = &st.stmts[..] else {
                    return None;
                };
                if !st.done || st.handler.is_some() {
                    return None;
                }
                match self.p().nodes.get(only) {
                    Node::Assign { local, value } if local == self.state => {
                        targets.get(&value).copied()
                    }
                    _ => None,
                }
            })
            .collect();
        (0..self.states.len())
            .map(|to| {
                let mut at = to;
                for _ in 0..self.states.len() {
                    match only_jump[at] {
                        Some(next) if next != at => at = next,
                        _ => return at,
                    }
                }
                to
            })
            .collect()
    }

    /// Each state's number in the step's switch: `None` for those that
    /// nothing reaches, -1 for those that only end the generator, which
    /// the default does, and from 0 up, in order, for the rest. State 0,
    /// where the generator starts, stays 0.
    fn numbering(&self, forwarded: &[usize]) -> Vec<Option<i32>> {
        let p = self.p();
        let count = self.states.len();
        // What each state's code jumps to.
        let mut owner_of = HashMap::new();
        for (i, state) in self.states.iter().enumerate() {
            for &stmt in &state.stmts {
                walk(p, stmt, &mut |n| {
                    owner_of.insert(n, i);
                });
            }
        }
        let mut edges = vec![Vec::new(); count];
        for &(lit, to) in &self.targets {
            if let Some(&from) = owner_of.get(&lit) {
                edges[from].push(forwarded[to]);
            }
        }
        let mut reached = vec![false; count];
        let mut todo = vec![0];
        while let Some(i) = todo.pop() {
            if i >= count || std::mem::replace(&mut reached[i], true) {
                continue;
            }
            todo.extend(edges[i].iter().copied());
            todo.extend(self.states[i].handler.map(|h| forwarded[h]));
        }
        let mut next = 0;
        (0..count)
            .map(|i| {
                if !reached[i] {
                    return None;
                }
                let state = &self.states[i];
                let only_done = match state.stmts[..] {
                    [] => !state.done,
                    [one] => p.nodes.kind(one) == Kind::Return,
                    _ => false,
                };
                if only_done && state.handler.is_none() && i != 0 {
                    return Some(-1);
                }
                next += 1;
                Some(next - 1)
            })
            .collect()
    }
}
