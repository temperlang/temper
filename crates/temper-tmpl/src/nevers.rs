//! `Never<T>` as `T`: a shared lowering, which each backend calls with its
//! own profile ([`Features::NEVER`]; notes/tmpl/types.md, "Never").
//!
//! The frontend gives a function that never returns normally its declared
//! `Never<T>` result, and each call of it that type. A target with a
//! bottom type ([`Features::NEVER`]) keeps it as the result of a function
//! or method declaration ([`may_keep`]): one that nothing overrides and
//! that overrides nothing, so its signature needn't match another's, and
//! that isn't read as a value. Everywhere else, and everywhere for a target
//! without one, `Never<T>` becomes the `T` it stands for, as core's docs
//! say. A call that ends its block as a statement then loses what told
//! the target that control doesn't go on, so a `panic()` follows it, as
//! the target's compiler may need for a local assigned only on the other
//! paths, a null check that leaves, or a function's end.

use std::collections::{HashMap, HashSet};

use crate::decls::DeclKind;
use crate::ids::{DeclId, NodeId, TypeId};
use crate::schema::{Kind, Node, Payload, Slot};
use crate::types::{TypeArgs, TypeData};
use crate::{Builder, Features, Profile, Program};

/// The program with `Never<T>` as `T` but where the profile keeps it
/// ([`may_keep`]), or `None` where it has none to change.
pub fn lower(p: &Program, profile: &Profile) -> Option<Program> {
    match profile.native.contains(Features::NEVER) {
        true => lower_where(p, &may_keep),
        false => lower_where(p, &|_, _| false),
    }
}

/// [`lower`], keeping `Never<T>` as the result of the functions and
/// methods that `keep` picks, which should be among those [`may_keep`]
/// does, as a target whose bottom type fits fewer places (Rust's `!`,
/// not in a `Result`).
pub fn lower_where(p: &Program, keep: &dyn Fn(&Program, DeclId) -> bool) -> Option<Program> {
    let decls = p.decls.ids().filter(|&d| p.decls.contains(d));
    let nodes = (0..p.nodes.len()).map(|i| p.nodes.ty(NodeId(i as u32)));
    if !(decls.map(|d| p.decls.get(d).ty).chain(nodes)).any(|t| has_never(p, t)) {
        return None;
    }
    let kept: HashSet<DeclId> = (p.decls.ids())
        .filter(|&d| p.decls.contains(d) && returns_never(p, d) && keep(p, d))
        .collect();
    let mut b = Builder::from_program(p.clone());
    let mut lowered = HashMap::new();
    for d in p.decls.ids().filter(|&d| p.decls.contains(d)) {
        let decl = p.decls.get(d);
        let types = &mut b.program_mut().types;
        let ty = match kept.contains(&d) {
            true => keep_result(types, decl.ty, &mut lowered),
            false => fallback(types, decl.ty, &mut lowered),
        };
        let supers: Vec<TypeId> = (p.decls.supers(d).iter())
            .map(|&s| fallback(types, s, &mut lowered))
            .collect();
        b.set_decl_type(d, ty);
        b.set_bounds(d, &supers);
    }
    let mut ends = Vec::new();
    for id in (0..p.nodes.len()).map(|i| NodeId(i as u32)) {
        let ty = p.nodes.ty(id);
        let types = &mut b.program_mut().types;
        let new_ty = match calls_kept(p, id, &kept) {
            true => keep_result(types, ty, &mut lowered),
            false => fallback(types, ty, &mut lowered),
        };
        let slots = payload_types(&mut b, id, &mut lowered);
        b.retype_lowered(id, new_ty, slots);
        if let Node::Block { stmts } = p.nodes.get(id) {
            let last = p.nodes.list(stmts).last().copied();
            let ends_never = last.is_some_and(|s| match p.nodes.get(s) {
                Node::ExprStatement { expr } => {
                    p.types.is_never(p.nodes.ty(expr)) && !calls_kept(p, expr, &kept)
                }
                _ => false,
            });
            if ends_never {
                ends.push(id);
            }
        }
    }
    // What told the target that control stops is gone, so a `panic()`
    // says it.
    for block in ends {
        let Node::Block { stmts } = b.program().nodes.get(block) else {
            continue;
        };
        let mut stmts = b.program().nodes.list(stmts).to_vec();
        let span = b.program().nodes.span(block);
        let panic = b.panic(TypeId::VOID);
        let stmt = b.expr_statement(panic);
        for made in [panic, stmt] {
            b.set_span(made, span, true);
        }
        stmts.push(stmt);
        let made = b.block(&stmts);
        b.replace_with(block, made);
    }
    b.renumber();
    Some(b.finish())
}

/// Whether a target with a bottom type may keep a declaration's
/// `Never<T>` result: a function or method that nothing overrides and
/// that overrides nothing, as a method of an interface or of a class
/// that implements one with a method of its name, so its signature
/// needn't match another's, which most targets require exactly; and
/// that no `FunctionRef` reads as a value, whose function type is `T`'s.
pub fn may_keep(p: &Program, decl: DeclId) -> bool {
    let d = p.decls.get(decl);
    let overriding = match d.kind {
        DeclKind::Function => false,
        DeclKind::Method => {
            let owner = p.decls.get(d.owner);
            owner.kind != DeclKind::Class
                || (crate::analysis::ancestors(p, d.owner).iter()).any(|&a| {
                    (p.decls.ids()).any(|m| {
                        p.decls.contains(m)
                            && p.decls.get(m).owner == a
                            && p.decls.get(m).kind == DeclKind::Method
                            && p.decls.get(m).name == d.name
                    })
                })
        }
        _ => return false,
    };
    let as_value = (0..p.nodes.len()).any(
        |i| matches!(p.nodes.get(NodeId(i as u32)), Node::FunctionRef { target } if target == decl),
    );
    returns_never(p, decl) && !overriding && !as_value
}

/// Whether a declaration's signature's result is `Never<T>`.
fn returns_never(p: &Program, decl: DeclId) -> bool {
    p.signature(decl)
        .is_some_and(|s| p.types.is_never(s.result))
}

/// Whether a type is or has `Never<T>`, as a signature's result.
fn has_never(p: &Program, ty: TypeId) -> bool {
    if !p.types.contains(ty) {
        return false;
    }
    match p.types.get(ty) {
        TypeData::Never(_) => true,
        TypeData::Scalar(_) | TypeData::TypeParam { .. } => false,
        TypeData::Nullable(inner) => has_never(p, inner),
        TypeData::Nominal { args, .. } => p.types.list(args).iter().any(|&a| has_never(p, a)),
        TypeData::Function { .. } => p.types.signature(ty).is_some_and(|sig| {
            sig.params
                .iter()
                .chain([&sig.result])
                .any(|&t| has_never(p, t))
        }),
    }
}

/// Whether `expr` is a call of a declaration in `kept`.
fn calls_kept(p: &Program, expr: NodeId, kept: &HashSet<DeclId>) -> bool {
    match p.nodes.get(expr) {
        Node::CallFunction { callee, .. } => kept.contains(&callee),
        Node::CallMethod { method, .. } => kept.contains(&method),
        _ => false,
    }
}

/// `ty` with each `Never<T>` in it as `T`.
fn fallback(
    types: &mut crate::TypeTable,
    ty: TypeId,
    done: &mut HashMap<TypeId, TypeId>,
) -> TypeId {
    if !types.contains(ty) {
        return ty;
    }
    if let Some(&made) = done.get(&ty) {
        return made;
    }
    let made = match types.get(ty) {
        TypeData::Scalar(_) | TypeData::TypeParam { .. } => ty,
        TypeData::Never(inner) => fallback(types, inner, done),
        TypeData::Nullable(inner) => {
            let inner = fallback(types, inner, done);
            types.nullable(inner)
        }
        TypeData::Nominal { decl, args } => {
            let args: Vec<TypeId> = types.list(args).to_vec();
            let args: Vec<TypeId> = args.iter().map(|&a| fallback(types, a, done)).collect();
            types.applied(decl, &args)
        }
        TypeData::Function { .. } => {
            let sig = types.signature(ty).expect("a signature");
            let (params, optional) = (sig.params.to_vec(), sig.optional.to_vec());
            let (result, bubbles) = (sig.result, sig.bubbles);
            let params: Vec<TypeId> = params.iter().map(|&t| fallback(types, t, done)).collect();
            let result = fallback(types, result, done);
            types.function_with(&params, &optional, result, bubbles)
        }
    };
    done.insert(ty, made);
    made
}

/// A kept call's type, or a kept declaration's signature: `Never<T>` at
/// its top, and only there, as [`fallback`] leaves the rest.
fn keep_result(
    types: &mut crate::TypeTable,
    ty: TypeId,
    done: &mut HashMap<TypeId, TypeId>,
) -> TypeId {
    match types.get(ty) {
        TypeData::Never(inner) => {
            let inner = fallback(types, inner, done);
            types.never(inner)
        }
        TypeData::Function { .. } => {
            let sig = types.signature(ty).expect("a signature");
            let (params, optional) = (sig.params.to_vec(), sig.optional.to_vec());
            let (result, bubbles) = (sig.result, sig.bubbles);
            let params: Vec<TypeId> = params.iter().map(|&t| fallback(types, t, done)).collect();
            let result = keep_result(types, result, done);
            types.function_with(&params, &optional, result, bubbles)
        }
        _ => fallback(types, ty, done),
    }
}

/// A node's payload slots, with the types and type arguments in them
/// lowered.
fn payload_types(b: &mut Builder, id: NodeId, done: &mut HashMap<TypeId, TypeId>) -> (u32, u32) {
    let kind: Kind = b.program().nodes.kind(id);
    let (mut a, mut c) = b.program().nodes.slots(id);
    for field in kind.fields() {
        let types = &mut b.program_mut().types;
        match field.slot {
            Slot::A(Payload::Type) => a = fallback(types, TypeId(a), done).0,
            Slot::B(Payload::Type) => c = fallback(types, TypeId(c), done).0,
            Slot::A(Payload::Args) => a = lowered_args(types, TypeArgs(a), done).0,
            Slot::B(Payload::Args) => c = lowered_args(types, TypeArgs(c), done).0,
            _ => {}
        }
    }
    (a, c)
}

fn lowered_args(
    types: &mut crate::TypeTable,
    args: TypeArgs,
    done: &mut HashMap<TypeId, TypeId>,
) -> TypeArgs {
    let list: Vec<TypeId> = types.args_of(args).to_vec();
    if list.is_empty() {
        return args;
    }
    let list: Vec<TypeId> = list.iter().map(|&t| fallback(types, t, done)).collect();
    types.type_args(&list)
}
