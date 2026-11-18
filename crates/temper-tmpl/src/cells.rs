//! Captured `var` parameters' copies, for targets whose closures copy
//! what they capture and keep assigned captures in cells (Rust, Java,
//! and C++; `analysis::assigned_captures`), where a parameter can't be
//! one: [`copy_captured_params`] gives each such parameter a `var` local
//! of its value that the body uses instead, as Kotlin's Java backend
//! does (`makeTemporaryIfMutablyCapturedParam`). Each backend that needs
//! it calls it; tmpl never lists backends.

use crate::analysis::{assigned_captures, captures, defaults, walk};
use crate::decls::{DeclKind, Facts};
use crate::ids::{DeclId, NodeId};
use crate::schema::Node;
use crate::{Builder, Program};

/// The program with each `var` parameter that a literal captures and
/// something assigns read and assigned through a `var` local of its
/// value, declared after its function's defaults (`let value2 = value`),
/// or `None` where there's none. The parameter's name, which may be API
/// (C#'s), stays its own; the local's is numbered where they clash.
pub fn copy_captured_params(p: &Program) -> Option<Program> {
    let mut params: Vec<DeclId> = assigned_captures(p)
        .into_iter()
        .filter(|&d| p.decls.get(d).kind == DeclKind::Param)
        .collect();
    if params.is_empty() {
        return None;
    }
    params.sort();
    // Each parameter's function: its body, by its `Param` node.
    let mut bodies = Vec::new();
    for m in &p.modules {
        for root in m.roots() {
            walk(p, root, &mut |id| {
                let (ps, body) = match p.nodes.get(id) {
                    Node::Function { params, body, .. }
                    | Node::Method { params, body, .. }
                    | Node::Test { params, body, .. }
                    | Node::FunctionLiteral { params, body, .. } => (params, body),
                    _ => return,
                };
                for &param in p.nodes.list(ps) {
                    if let Node::Param { decl } = p.nodes.get(param) {
                        if params.contains(&decl) {
                            bodies.push((decl, body));
                        }
                    }
                }
            });
        }
    }
    let mut b = Builder::from_program(p.clone());
    for (param, body) in bodies {
        let name = b.program().name(param).to_string();
        let d = b.program().decls.get(param);
        let (owner, ty) = (d.owner, d.ty);
        let local = b.declare(DeclKind::Local, &name, owner, ty);
        b.add_facts(local, Facts::VAR);
        b.remove_facts(param, Facts::VAR);
        // Its reads and assignments, the local's now.
        let mut uses = Vec::new();
        walk(
            b.program(),
            body,
            &mut |id| match b.program().nodes.get(id) {
                Node::Local { local: l } if l == param => uses.push((id, None)),
                Node::Assign { local: l, value } if l == param => uses.push((id, Some(value))),
                _ => {}
            },
        );
        for (id, value) in uses {
            let made = match value {
                None => b.local(local),
                Some(value) => b.assign(local, value),
            };
            b.replace_with(id, made);
        }
        // `let local = param;`, after the defaults.
        let Node::Block { stmts } = b.program().nodes.get(body) else {
            continue;
        };
        let mut stmts: Vec<NodeId> = b.program().nodes.list(stmts).to_vec();
        let at = defaults(b.program(), body).len();
        let read = b.local(param);
        let copy = b.let_stmt(local, Some(read));
        stmts.insert(at, copy);
        let block = b.block(&stmts);
        b.replace_with(body, block);
    }
    // Every literal's captures, which the copies change.
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
        let found = captures(b.program(), id);
        b.set_captures(decl, &found);
    }
    b.renumber();
    Some(b.finish())
}
