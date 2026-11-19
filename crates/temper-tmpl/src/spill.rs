//! Spilling operands before what's hoisted: a lowering for backends that
//! write an `orelse` or a block's value as statements before the
//! statement it's in, and so can't let an operand that runs before it
//! stay in place where that operand does anything: step 1 of the
//! hoisting pass (notes/tmpl/profiles.md, "Statements out of
//! expressions"), shared.
//!
//! `f(a(), b() orelse c)` runs `a()` first, but a backend that hoists the
//! `orelse` runs `b()` first. Spilled, it's `f({ let t = a(); t }, b()
//! orelse c)`: each operand before the first that hoists that isn't
//! simple (literals, locals that what's hoisted doesn't assign, `this`,
//! functions, module `let`s and statics that aren't `var`, and operators
//! and choices on those that can't fail) becomes a block's value of its own, which
//! the backend hoists too, in order. The first operand that hoists is
//! one that has an `orelse` or a block's value in it, outside function
//! literals.
//!
//! `&&`, `||`, `if` values, and `orelse` choose what runs, so their own
//! parts are left to the backend, as are a block's value's; but where a
//! part of theirs that runs only sometimes hoists, they become statements
//! that run it only then, in a block's value: `a && b` is `{ let t = a;
//! if (t) { t = b; } t }`. A `while` loop's condition that hoists runs as
//! statements each time round: `while (true) { if (!c) break; … }`.
//!
//! A backend whose own lowering puts statements before an expression in
//! more places marks those expressions too ([`spill_with`]): C++, which
//! evaluates operands in any order, holds in temporaries those whose
//! order shows, so a choice's part or a loop's condition that needs one
//! becomes statements as an `orelse` in it would.

use crate::analysis::{Subtrees, holding};
use crate::decls::DeclKind;
use crate::ids::{DeclId, NodeId};
use crate::schema::{Family, Kind, Node};
use crate::{Builder, Program};

/// The program with operands spilled, or `None` where nothing changes.
pub fn spill(p: &Program) -> Option<Program> {
    spill_with(p, &|_| false)
}

/// [`spill`], where `also` marks the expressions that the backend puts
/// statements before, besides `orelse`s and blocks' values, so a choice's
/// part or a loop's condition holding one becomes statements too. Each
/// marks itself only, not what holds it; operands before one aren't held
/// first, since the backend holds them itself.
pub fn spill_with(p: &Program, also: &dyn Fn(NodeId) -> bool) -> Option<Program> {
    let mut sites = Vec::new();
    let mut loops = Vec::new();
    let mut choices = Vec::new();
    let (hoists, hoists_with) = (hoisting(p, &|_| false), hoisting(p, also));
    for m in &p.modules {
        for &root in (m.items.iter().chain(&m.test_items).chain(&m.tests))
            .chain([&m.init])
            .chain(&m.test_init)
        {
            find(p, root, m.decl, &hoists, &mut sites);
            find_choices(p, root, m.decl, &hoists_with, &mut choices);
            crate::analysis::walk(p, root, &mut |n| {
                if let Node::While { cond, .. } = p.nodes.get(n) {
                    if hoists_with.of(cond) {
                        loops.push(n);
                    }
                }
            });
        }
    }
    if sites.is_empty() && loops.is_empty() && choices.is_empty() {
        return None;
    }
    let mut b = Builder::from_program(p.clone());
    // `a && b`, `a || b`, and `if (c) a else b`, where a part that runs
    // only sometimes hoists, as statements that run it only then: `{ let
    // t = a; if (t) { t = b; } t }`, `{ let t = a; if (!t) { t = b; } t
    // }`, and `{ let t; if (c) { t = a; } else { t = b; } t }`.
    for (node, owner) in choices {
        let span = b.program().nodes.span(node);
        let ty = b.program().nodes.ty(node);
        let hints = crate::naming::hints(b.program(), node);
        let hints: Vec<&str> = hints.iter().map(String::as_str).collect();
        let local = b.declare_made(DeclKind::Local, &hints, owner, ty);
        b.add_facts(local, crate::decls::Facts::VAR);
        b.set_decl_spans(local, span, span);
        let mut made = Vec::new();
        let read = |b: &mut Builder, made: &mut Vec<NodeId>| {
            let read = b.local(local);
            made.push(read);
            read
        };
        let set = |b: &mut Builder, made: &mut Vec<NodeId>, value: NodeId| {
            let stmt = b.assign(local, value);
            let block = b.block(&[stmt]);
            made.extend([stmt, block]);
            block
        };
        let stmts = match b.program().nodes.get(node) {
            Node::And { left, right } | Node::Or { left, right } => {
                let and = b.program().nodes.kind(node) == Kind::And;
                let first = b.let_stmt(local, Some(left));
                let cond = read(&mut b, &mut made);
                let cond = if and {
                    cond
                } else {
                    let not = crate::ops::Op::named("Boolean.not").expect("core's Boolean.not");
                    let not = b.op(not, &[cond]);
                    made.push(not);
                    not
                };
                let then = set(&mut b, &mut made, right);
                let check = b.if_stmt(cond, then, None);
                made.extend([first, check]);
                vec![first, check]
            }
            Node::IfValue { cond, then, els } => {
                let first = b.let_stmt(local, None);
                let then = set(&mut b, &mut made, then);
                let els = set(&mut b, &mut made, els);
                let check = b.if_stmt(cond, then, Some(els));
                made.extend([first, check]);
                vec![first, check]
            }
            _ => continue,
        };
        let value = read(&mut b, &mut made);
        let whole = b.block_value(ty, &stmts, value);
        for n in made {
            b.set_span(n, span, true);
        }
        b.replace_with(node, whole);
    }
    // `while (c) { … }`, where `c` hoists, as `while (true) { if (!c)
    // break; … }`, so `c` runs as statements each time round.
    for w in loops {
        let Node::While { label, cond, body } = b.program().nodes.get(w) else {
            continue;
        };
        let span = b.program().nodes.span(w);
        let stmts = match b.program().nodes.get(body) {
            Node::Block { stmts } => b.program().nodes.list(stmts).to_vec(),
            _ => vec![body],
        };
        let not = crate::ops::Op::named("Boolean.not").expect("core's Boolean.not");
        let not = b.op(not, &[cond]);
        let stop = b.break_loop(label);
        let then = b.block(&[stop]);
        let check = b.if_stmt(not, then, None);
        let body = b.block(&[[check].as_slice(), &stmts].concat());
        let yes = b.bool_lit(true);
        let made = b.while_loop(label, yes, body);
        for n in [not, stop, then, check, body, yes] {
            b.set_span(n, span, true);
        }
        b.replace_with(w, made);
    }
    for (node, owner) in sites {
        let span = b.program().nodes.span(node);
        let ty = b.program().nodes.ty(node);
        let hints = crate::naming::hints(b.program(), node);
        let hints: Vec<&str> = hints.iter().map(String::as_str).collect();
        let value = b.moved(node);
        let local = b.declare_made(DeclKind::Local, &hints, owner, ty);
        b.set_decl_spans(local, span, span);
        let stmt = b.let_stmt(local, Some(value));
        b.set_span(stmt, span, true);
        let read = b.local(local);
        b.set_span(read, span, true);
        let made = b.block_value(ty, &[stmt], read);
        b.replace_with(node, made);
    }
    b.renumber();
    Some(b.finish())
}

/// Finds, under `id`, the operands to spill, each with the function (or
/// module) whose local will hold it.
fn find(
    p: &Program,
    id: NodeId,
    owner: DeclId,
    hoists: &Subtrees<bool>,
    sites: &mut Vec<(NodeId, DeclId)>,
) {
    let owner = match p.nodes.get(id) {
        Node::Function { decl, .. }
        | Node::Test { decl, .. }
        | Node::Method { decl, .. }
        | Node::FunctionLiteral { decl, .. } => decl,
        _ => owner,
    };
    let chooses = matches!(
        p.nodes.kind(id),
        Kind::And | Kind::Or | Kind::IfValue | Kind::OrElse | Kind::BlockValue
    );
    let kids = p.nodes.kids(id);
    if !chooses && p.nodes.kind(id) != Kind::FunctionLiteral {
        let operands: Vec<NodeId> = (kids.iter().copied())
            .filter(|&k| p.nodes.kind(k).family() == Family::Expr)
            .collect();
        // Only where operands come before the first that hoists.
        let first = operands.iter().position(|&k| hoists.of(k));
        if let Some(first) = first.filter(|&first| first > 0) {
            // A local that what's hoisted assigns isn't simple there.
            let mut assigned = Vec::new();
            crate::analysis::walk(p, operands[first], &mut |n| {
                if let Node::Assign { local, .. } = p.nodes.get(n) {
                    assigned.push(local);
                }
            });
            for &k in &operands[..first] {
                if !simple(p, k, &assigned) {
                    sites.push((k, owner));
                }
            }
        }
    }
    for &kid in kids {
        find(p, kid, owner, hoists, sites);
    }
}

/// Finds, under `id`, the `&&`, `||`, and `if` values whose parts that
/// run only sometimes hoist, each with the function (or module) whose
/// local will hold its value.
fn find_choices(
    p: &Program,
    id: NodeId,
    owner: DeclId,
    hoists: &Subtrees<bool>,
    found: &mut Vec<(NodeId, DeclId)>,
) {
    let owner = match p.nodes.get(id) {
        Node::Function { decl, .. }
        | Node::Test { decl, .. }
        | Node::Method { decl, .. }
        | Node::FunctionLiteral { decl, .. } => decl,
        _ => owner,
    };
    let chooses = match p.nodes.get(id) {
        Node::And { right, .. } | Node::Or { right, .. } => hoists.of(right),
        Node::IfValue { then, els, .. } => hoists.of(then) || hoists.of(els),
        _ => false,
    };
    if chooses {
        found.push((id, owner));
    }
    for &kid in p.nodes.kids(id) {
        find_choices(p, kid, owner, hoists, found);
    }
}

/// Whether each expression has an `orelse` or a block's value in it, or
/// an expression that `also` marks, outside function literals: worked
/// out once per node, since [`find`] asks at every level.
fn hoisting<'p>(p: &'p Program, also: &'p dyn Fn(NodeId) -> bool) -> Subtrees<'p, bool> {
    let closed = |id| p.nodes.kind(id) == Kind::FunctionLiteral;
    holding(
        p,
        move |id| match p.nodes.kind(id) {
            Kind::OrElse | Kind::BlockValue => true,
            Kind::FunctionLiteral => false,
            _ => also(id),
        },
        closed,
    )
}

/// Whether an operand does nothing that running it later could show:
/// literals, locals (but those in `assigned`), `this`, functions, module
/// `let`s and statics that aren't `var`, and operators that can't fail,
/// choices, null checks, and conversions of those. A call, or anything
/// else, may.
fn simple(p: &Program, id: NodeId, assigned: &[DeclId]) -> bool {
    let var = |d: DeclId| p.decls.get(d).facts.contains(crate::decls::Facts::VAR);
    let parts = |p: &Program| p.nodes.kids(id).iter().all(|&k| simple(p, k, assigned));
    match p.nodes.get(id) {
        Node::Local { local } => !assigned.contains(&local),
        Node::GetModuleLet { target } | Node::GetStatic { target } => !var(target),
        Node::Op { .. } => !p.nodes.flags(id).contains(crate::nodes::Flags::MAY_BUBBLE) && parts(p),
        Node::IsNull { .. }
        | Node::IfValue { .. }
        | Node::And { .. }
        | Node::Or { .. }
        | Node::NonNull { .. }
        | Node::Upcast { .. }
        | Node::AsNullable { .. } => parts(p),
        _ => matches!(
            p.nodes.kind(id),
            Kind::This
                | Kind::Null
                | Kind::BoolLit
                | Kind::Int32Lit
                | Kind::Int64Lit
                | Kind::Float64Lit
                | Kind::StringLit
                | Kind::VoidLit
                | Kind::TypeValue
                | Kind::FunctionLiteral
                | Kind::FunctionRef
        ),
    }
}
