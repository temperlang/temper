//! Type-only errors, settled by a target's profile
//! ([`Features::RUNS_TYPE_ERRORS`](crate::Features::RUNS_TYPE_ERRORS)): a
//! shared pass, which each backend calls with its own profile
//! (notes/tmpl/nodes.md, "Level 1: Errors stay values").
//!
//! The frontend gives code that's ill-typed but runnable, as a value that
//! doesn't fit its place, as an `Error` that keeps the value as written,
//! and leaves everything around it as it would be without the error. A
//! dynamically typed target runs it as written, as Kotlin's interpreter
//! does: each such `Error` becomes its value. A statically typed target,
//! whose compiler would reject the code, fails there instead: each
//! becomes a plain `Error`, and what can't run after it goes, as the
//! frontend drops it after any other error:
//!
//! - a call whose arguments before the error are only locals and
//!   literals fails before it's made, so it's the error (for a method,
//!   only its first argument counts, as the frontend does it);
//! - a statement that is the error, or a `let` or assignment of it, ends
//!   its block, as the error's statement, or, in a block with a value, as
//!   that value;
//! - in a module's init block, what follows such a statement goes, but
//!   its module `let`s' initializers stay.

use std::collections::HashSet;

use crate::ids::{NodeId, TypeId};
use crate::schema::{Kind, Node};
use crate::{Builder, Features, Profile, Program};

/// The program with each type-only `Error` as its value, where the
/// profile has [`Features::RUNS_TYPE_ERRORS`], or else as a plain `Error`
/// that fails there; `None` where it has none.
pub fn settle(p: &Program, profile: &Profile) -> Option<Program> {
    let found = type_errors(p);
    if found.is_empty() {
        return None;
    }
    let mut b = Builder::from_program(p.clone());
    if profile.native.contains(Features::RUNS_TYPE_ERRORS) {
        for error in found {
            let Node::Error {
                value: Some(value), ..
            } = b.program().nodes.get(error)
            else {
                continue;
            };
            b.replace_with(error, value);
        }
    } else {
        let mut errors = HashSet::new();
        for &error in &found {
            let ty = b.program().nodes.ty(error);
            let plain = plain_error(&mut b, error, ty);
            b.replace_with(error, plain);
            errors.insert(error);
        }
        let modules: Vec<_> = (b.program().modules.iter())
            .map(|m| {
                let roots: Vec<NodeId> = (m.items.iter().chain(&m.test_items).chain(&m.tests))
                    .copied()
                    .collect();
                (roots, [m.init].into_iter().chain(m.test_init))
            })
            .collect();
        for (roots, inits) in modules {
            for root in roots {
                fail(&mut b, root, &mut errors, false);
            }
            for init in inits {
                fail(&mut b, init, &mut errors, true);
            }
        }
    }
    b.renumber();
    Some(b.finish())
}

/// Whether the program has type-only `Error`s, so a target that runs
/// them runs ill-typed code, which its type checkers would reject.
pub fn has_type_errors(p: &Program) -> bool {
    !type_errors(p).is_empty()
}

/// The type-only `Error`s.
fn type_errors(p: &Program) -> Vec<NodeId> {
    let mut found = Vec::new();
    for m in &p.modules {
        for &root in (m.items.iter().chain(&m.test_items).chain(&m.tests))
            .chain([&m.init])
            .chain(&m.test_init)
        {
            find(p, root, &mut found);
        }
    }
    found
}

/// Finds the type-only `Error`s under `id`.
fn find(p: &Program, id: NodeId, found: &mut Vec<NodeId>) {
    if let Node::Error { value: Some(_), .. } = p.nodes.get(id) {
        found.push(id);
    }
    for &kid in p.nodes.kids(id) {
        find(p, kid, found);
    }
}

/// A new plain `Error` with `error`'s message, typed `ty`.
fn plain_error(b: &mut Builder, error: NodeId, ty: TypeId) -> NodeId {
    let Node::Error { message, .. } = b.program().nodes.get(error) else {
        unreachable!("an error")
    };
    let message = b.program().strings.get(message).to_string();
    b.error(ty, &message)
}

/// Drops, children first, what can't run after the `errors`, as the
/// module's init block (`init`) or another root.
fn fail(b: &mut Builder, id: NodeId, errors: &mut HashSet<NodeId>, init: bool) {
    for i in 0..b.program().nodes.kids(id).len() {
        let kid = b.program().nodes.kids(id)[i];
        fail(b, kid, errors, false);
    }
    let p = b.program();
    match p.nodes.kind(id) {
        Kind::CallFunction | Kind::Op | Kind::CallValue | Kind::New | Kind::CallMethod => {
            let kids = p.nodes.kids(id);
            let args = match p.nodes.kind(id) {
                // The receiver, then only the first argument.
                Kind::CallMethod => &kids[1.min(kids.len())..2.min(kids.len())],
                _ => kids,
            };
            let quiet = |k: NodeId| {
                matches!(
                    p.nodes.kind(k),
                    Kind::Local
                        | Kind::GetModuleLet
                        | Kind::BoolLit
                        | Kind::Int32Lit
                        | Kind::Int64Lit
                        | Kind::Float64Lit
                        | Kind::StringLit
                        | Kind::VoidLit
                        | Kind::Null
                )
            };
            let mut hit = None;
            for &arg in args {
                if errors.contains(&arg) {
                    hit = Some(arg);
                    break;
                }
                if !quiet(arg) {
                    break;
                }
            }
            if let Some(error) = hit {
                let ty = p.nodes.ty(id);
                let plain = plain_error(b, error, ty);
                b.replace_with(id, plain);
                errors.insert(id);
            }
        }
        Kind::Block | Kind::BlockValue => {
            let value_kind = p.nodes.kind(id) == Kind::BlockValue;
            let kids = p.nodes.kids(id).to_vec();
            let (stmts, value) = match value_kind {
                true => (&kids[..kids.len() - 1], kids.last().copied()),
                false => (&kids[..], None),
            };
            let Some(at) = stmts
                .iter()
                .position(|&s| failed_at(b, s, errors).is_some())
            else {
                return;
            };
            let error = failed_at(b, stmts[at], errors).expect("a failing statement");
            let mut kept = stmts[..at].to_vec();
            // The error as a statement of its own, or as a block's value.
            if value.is_none() {
                let stmt = match b.program().nodes.kind(stmts[at]) {
                    Kind::ExprStatement => stmts[at],
                    _ => {
                        let made = b.expr_statement(error);
                        let span = b.program().nodes.span(stmts[at]);
                        b.set_span(made, span, true);
                        made
                    }
                };
                kept.push(stmt);
            }
            if init {
                // Module `let`s still get their values.
                let rest = stmts[at + 1..].iter().copied();
                kept.extend(rest.filter(|&s| b.program().nodes.kind(s) == Kind::InitLet));
            }
            let made = match value {
                Some(_) => {
                    let ty = b.program().nodes.ty(id);
                    let value = plain_error(b, error, ty);
                    // Without statements, just the error.
                    if kept.is_empty() {
                        b.replace_with(id, value);
                        errors.insert(id);
                        return;
                    }
                    b.block_value(ty, &kept, value)
                }
                None => b.block(&kept),
            };
            b.replace_with(id, made);
        }
        _ => {}
    }
}

/// The error a statement fails at before it completes: the error it is,
/// or that it declares or assigns.
fn failed_at(b: &Builder, stmt: NodeId, errors: &HashSet<NodeId>) -> Option<NodeId> {
    let value = match b.program().nodes.get(stmt) {
        Node::ExprStatement { expr } => expr,
        Node::Let {
            init: Some(init), ..
        } => init,
        Node::Assign { value, .. }
        | Node::SetField { value, .. }
        | Node::SetModuleLet { value, .. } => value,
        _ => return None,
    };
    errors.contains(&value).then_some(value)
}
