//! `Switch` as `if`s, for targets whose profile lacks
//! [`Features::SWITCH`](crate::Features::SWITCH): a shared lowering, which
//! each backend calls or not by its own profile (notes/tmpl/nodes.md,
//! "Switch").
//!
//! `switch (x) { case 1, 2 { A } case 3 { B } default { C } }` becomes
//! `if (x == 1 || x == 2) { A } else if (x == 3) { B } else { C }`. A
//! scrutinee that isn't a local is held in one first, so it runs once:
//! `{ let t = f(); if (t == 1) … }`.

use crate::decls::DeclKind;
use crate::ids::{DeclId, NodeId, Span, TypeId};
use crate::ops::Op;
use crate::schema::Node;
use crate::{Builder, Program};

/// The program with each `Switch` made `if`s, or `None` where it has
/// none.
pub fn to_if_chains(p: &Program) -> Option<Program> {
    to_if_chains_where(p, &|_| true)
}

/// [`to_if_chains`] for the `Switch`es that `which` picks, as a backend
/// whose own `switch` takes only some scrutinees (Java's, no `long`).
pub fn to_if_chains_where(p: &Program, which: &dyn Fn(NodeId) -> bool) -> Option<Program> {
    let mut found = Vec::new();
    for m in &p.modules {
        for &root in (m.items.iter().chain(&m.test_items).chain(&m.tests))
            .chain([&m.init])
            .chain(&m.test_init)
        {
            find(p, root, m.decl, &mut found);
        }
    }
    found.retain(|&(switch, _)| which(switch));
    if found.is_empty() {
        return None;
    }
    let mut b = Builder::from_program(p.clone());
    for (switch, owner) in found {
        lower(&mut b, switch, owner);
    }
    b.renumber();
    Some(b.finish())
}

/// Finds the `Switch`es under `id`, each with the function (or module)
/// that would own a local holding its scrutinee.
fn find(p: &Program, id: NodeId, owner: DeclId, found: &mut Vec<(NodeId, DeclId)>) {
    let owner = match p.nodes.get(id) {
        Node::Function { decl, .. }
        | Node::Test { decl, .. }
        | Node::Method { decl, .. }
        | Node::FunctionLiteral { decl, .. } => decl,
        Node::Switch { .. } => {
            found.push((id, owner));
            owner
        }
        _ => owner,
    };
    for &kid in p.nodes.kids(id) {
        find(p, kid, owner, found);
    }
}

fn lower(b: &mut Builder, switch: NodeId, owner: DeclId) {
    let Node::Switch { scrutinee, cases } = b.program().nodes.get(switch) else {
        return;
    };
    let span = b.program().nodes.span(switch);
    let cases: Vec<NodeId> = b.program().nodes.list(cases).to_vec();
    let ty = b.program().nodes.ty(scrutinee);
    let eq = Op::named(if ty == TypeId::INT64 {
        "Int64.eq"
    } else {
        "Int32.eq"
    })
    .expect("core's integer equality");
    let mut made = Vec::new();
    // What each comparison reads: the local itself, or one holding it.
    let (held, first) = match b.program().nodes.get(scrutinee) {
        Node::Local { local } => (local, None),
        _ => {
            let local = b.declare_made(DeclKind::Local, &["switched"], owner, ty);
            b.set_decl_spans(local, span, span);
            let stmt = b.let_stmt(local, Some(scrutinee));
            made.push(stmt);
            (local, Some(stmt))
        }
    };
    let mut chain: Option<NodeId> = None;
    for &case in cases.iter().rev() {
        let Node::Case { labels, body } = b.program().nodes.get(case) else {
            continue;
        };
        let labels: Vec<NodeId> = b.program().nodes.list(labels).to_vec();
        if labels.is_empty() {
            chain = Some(body);
            continue;
        }
        let mut cond: Option<NodeId> = None;
        for label in labels {
            let read = b.local(held);
            let test = b.op(eq, &[read, label]);
            made.extend([read, test]);
            cond = Some(match cond {
                Some(left) => {
                    let or = b.or(left, test);
                    made.push(or);
                    or
                }
                None => test,
            });
        }
        let cond = cond.expect("a case with labels");
        let made_if = b.if_stmt(cond, body, chain);
        let block = b.block(&[made_if]);
        made.extend([made_if, block]);
        chain = Some(block);
    }
    // The chain's `if`, not the block around it, or the default's block.
    let chain = chain.map(|c| match b.program().nodes.get(c) {
        Node::Block { stmts } if made.contains(&c) => b.program().nodes.list(stmts)[0],
        _ => c,
    });
    let whole = match (first, chain) {
        (None, Some(chain)) => chain,
        (None, None) => {
            let empty = b.block(&[]);
            made.push(empty);
            empty
        }
        (Some(first), chain) => {
            let stmts: Vec<NodeId> = [first].into_iter().chain(chain).collect();
            let block = b.block(&stmts);
            made.push(block);
            block
        }
    };
    if span != Span::UNKNOWN {
        for n in made {
            b.set_span(n, span, true);
        }
    }
    b.replace_with(switch, whole);
}
