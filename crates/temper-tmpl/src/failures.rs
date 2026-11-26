//! Declared failures (RFC 164; notes/tmpl/nodes.md, "Declared failures"):
//! what makes a failure type, what each node may fail with, and where a
//! program first uses them, for targets that don't have them yet.
//!
//! A failure type is a class or interface below core's `Error`, the root,
//! which a program has as an external interface where it uses failures.
//! `Bubble` stays apart: a scalar that's only ever a failure type, never
//! a value's, and that no failure type is below.

use crate::Program;
use crate::analysis::{ancestors, bindings, fun_signature, nominal_decl, walk};
use crate::decls::DeclKind;
use crate::ids::{DeclId, NodeId, TypeId};
use crate::qname::{is_external, library_of};
use crate::schema::Node;
use crate::types::TypeArgs;

/// Core's `Error`, the root of failure types, if the program has it.
pub fn root(p: &Program) -> Option<DeclId> {
    p.decls.ids().find(|&d| is_root(p, d))
}

/// Whether a declaration is core's `Error`.
fn is_root(p: &Program, d: DeclId) -> bool {
    p.decls.contains(d)
        && p.decls.get(d).kind == DeclKind::Interface
        && p.name(d) == "Error"
        && is_external(p, d)
        && library_of(p, d) == "core"
}

/// Core's `BrokenPromise`, what `await` of a broken promise throws, if
/// the program has it, as one that awaits does.
pub fn broken_promise(p: &Program) -> Option<TypeId> {
    let is_it = |d: DeclId| {
        p.decls.contains(d)
            && p.name(d) == "BrokenPromise"
            && is_external(p, d)
            && library_of(p, d) == "core"
    };
    p.decls.ids().find(|&d| is_it(d)).map(|d| p.decls.get(d).ty)
}

/// Whether `ty` is core's `Error`, any error: a test's failure, and
/// what std's `testing` takes, which may be a bubble too, until `Bubble`
/// goes.
pub fn is_root_type(p: &Program, ty: TypeId) -> bool {
    nominal_decl(p, ty).is_some_and(|d| is_root(p, d))
}

/// Whether a class or interface is a failure type below the root, found
/// through its supertypes alone, without the root at hand.
pub fn is_below_root(p: &Program, decl: DeclId) -> bool {
    ancestors(p, decl).iter().any(|&a| is_root(p, a))
}

/// Whether a declaration is one of a library's roots: a failure type
/// right under core's `Error` (`root`, as [`root`] finds it), a sealed
/// interface in the source, which targets make their own exception type,
/// so host code catches no Temper type (RFC 164, "host presentation").
pub fn is_library_root(p: &Program, root: Option<DeclId>, decl: DeclId) -> bool {
    let Some(root) = root else {
        return false;
    };
    let is_type = matches!(
        p.decls.get(decl).kind,
        DeclKind::Class | DeclKind::Interface
    );
    is_type && (p.decls.supers(decl).iter()).any(|&s| nominal_decl(p, s) == Some(root))
}

/// Whether a class or interface is a failure type: the root (`root`, as
/// [`root`] finds it), or below it.
pub fn is_failure_decl(p: &Program, root: Option<DeclId>, decl: DeclId) -> bool {
    let Some(root) = root else {
        return false;
    };
    decl == root || ancestors(p, decl).contains(&root)
}

/// Whether `ty` is a failure type's: a class's or interface's that's
/// [`is_failure_decl`].
pub fn is_failure_type(p: &Program, root: Option<DeclId>, ty: TypeId) -> bool {
    nominal_decl(p, ty).is_some_and(|d| is_failure_decl(p, root, d))
}

/// What a node may fail with, if anything: a failing call's callee's
/// failure type, with its type arguments put in; a `throw`'s value's
/// type; core's `BrokenPromise` for an `await`; and `Bubble` for
/// `bubble()` and for `Error` nodes, which fail as `bubble()` does.
pub fn node_failure(p: &Program, id: NodeId) -> Option<TypeId> {
    let callee = |callee: DeclId, receiver: Option<NodeId>, args: TypeArgs| {
        let fail = p.signature(callee)?.fail?;
        let receiver = receiver.map(|r| p.nodes.ty(r));
        let (from, to) = bindings(p, callee, receiver, p.types.args_of(args));
        Some(p.types.substituted(fail, &from, &to).unwrap_or(fail))
    };
    match p.nodes.get(id) {
        Node::CallFunction {
            callee: f,
            type_args,
            may_bubble: true,
            ..
        } => callee(f, None, type_args),
        Node::New {
            ctor,
            type_args,
            may_bubble: true,
            ..
        } => callee(ctor, None, type_args),
        Node::CallMethod {
            method,
            type_args,
            may_bubble: true,
            receiver,
            ..
        } => callee(method, Some(receiver), type_args),
        Node::CallValue {
            may_bubble: true,
            callee,
            ..
        } => {
            let ty = p.nodes.ty(callee);
            let sig = match p.types.signature(ty) {
                Some(_) => Some(ty),
                None => fun_signature(p, ty),
            };
            sig.and_then(|s| p.types.signature(s)?.fail)
        }
        Node::Throw { value, .. } => Some(p.nodes.ty(value)),
        Node::Await {
            may_bubble: true, ..
        } => broken_promise(p),
        Node::Bubble { .. } | Node::Error { .. } => Some(TypeId::BUBBLE),
        _ => None,
    }
}

/// What may fail under `id` and pass out of it, each node with what it
/// may fail with ([`node_failure`]): not what a handler's body passes to
/// its handler, and not what's in a function literal.
pub fn failures_under(p: &Program, id: NodeId) -> Vec<(NodeId, TypeId)> {
    let mut found = Vec::new();
    find(p, id, &mut found);
    found
}

fn find(p: &Program, id: NodeId, found: &mut Vec<(NodeId, TypeId)>) {
    if let Some(fail) = node_failure(p, id) {
        found.push((id, fail));
    }
    match p.nodes.get(id) {
        // Only the fallback passes failures on.
        Node::OrElse { fallback, .. } | Node::Catch { fallback, .. } => find(p, fallback, found),
        Node::FunctionLiteral { .. } => {}
        _ => {
            for &kid in p.nodes.kids(id) {
                find(p, kid, found);
            }
        }
    }
}

/// One type for what several places may fail with, as the frontend joins
/// them: `Bubble` if any may bubble; else the closest failure type every
/// one is (itself, or one of its supertypes, nearest first), or the root
/// (`root`'s type) where there's none closer; `None` for none.
pub fn join(p: &Program, root: Option<DeclId>, fails: &[TypeId]) -> Option<TypeId> {
    let (&first, rest) = fails.split_first()?;
    if fails.contains(&TypeId::BUBBLE) {
        return Some(TypeId::BUBBLE);
    }
    if rest.iter().all(|&f| f == first) {
        return Some(first);
    }
    let fits_all = |ty: TypeId| fails.iter().all(|&f| crate::analysis::is_subtype(p, f, ty));
    let candidates = nominal_decl(p, first).map_or(Vec::new(), |d| ancestors(p, d));
    for decl in candidates {
        let ty = p.decls.get(decl).ty;
        if is_failure_decl(p, root, decl) && fits_all(ty) {
            return Some(ty);
        }
    }
    root.map(|r| p.decls.get(r).ty)
}

/// For a target without declared failures yet, what it says of a program
/// that uses them ([`first_use`]), as "declared failures: a throw".
pub fn not_yet(p: &Program) -> Result<(), String> {
    match first_use(p) {
        Some(what) => Err(format!("declared failures: {what}")),
        None => Ok(()),
    }
}

/// Where a program first uses declared failures, for a target that
/// doesn't have them yet to say so: a failure type, a signature that
/// fails with one, a `throw`, or a `catch`; `None` for a program that
/// uses only `Bubble`.
pub fn first_use(p: &Program) -> Option<String> {
    // Every failure type is below core's `Error`.
    let root = Some(root(p)?);
    for d in p.decls.ids().filter(|&d| p.decls.contains(d)) {
        let decl = p.decls.get(d);
        let own = !is_external(p, d);
        let is_type = matches!(decl.kind, DeclKind::Class | DeclKind::Interface);
        if own && is_type && is_failure_decl(p, root, d) {
            return Some(format!("the failure type {}", p.name(d)));
        }
        let fail = p.types.signature(decl.ty).and_then(|s| s.fail);
        if let Some(fail) = fail.filter(|&f| f != TypeId::BUBBLE) {
            return Some(format!(
                "{} {}, which fails with {}",
                decl.kind.name(),
                p.name(d),
                p.show_type(fail)
            ));
        }
    }
    for module in &p.modules {
        for item in module.roots() {
            let mut found = None;
            walk(p, item, &mut |n| match p.nodes.get(n) {
                Node::Throw { .. } if found.is_none() => found = Some("a throw"),
                Node::Catch { .. } if found.is_none() => found = Some("a catch binding a failure"),
                _ => {}
            });
            if let Some(found) = found {
                return Some(found.into());
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    const PROGRAM: &str = "tmpl 1 model 1 library t 0.1.0

uses `temper-core`#20 0.1.0 {
  interface Error#21 {
    connected get message#22(): String;
    connected get cause#23(): Error#21?;
  }
}

module t#0 {
  export interface TError#1 extends Error#21 {}

  export interface SignError#2 extends TError#1 {}

  export class BadSign#3 extends SignError#2 {
    constructor constructor#6(): BadSign#3 {}
  }

  export class EmptyText#4 extends SignError#2 {
    constructor constructor#7(): EmptyText#4 {}
  }

  export class NotFound#5 extends TError#1 {
    constructor constructor#8(): NotFound#5 {}
  }

  fn f#9(): Int32 throws SignError#2 {
    return (orelse((throw(new constructor#8())^): Int32, (throw(new constructor#6())^): Int32)): Int32;
  }

  init {}
}
";

    /// A join is the nearest failure type above them all, the root where
    /// there's none closer, and `Bubble` if any may bubble; failures under
    /// a handler's body are its own, and only its fallback passes them on.
    #[test]
    fn joins_and_failures_under() {
        let p = crate::text::read(PROGRAM).unwrap();
        let ty = |d: u32| p.decls.get(DeclId(d)).ty;
        let root = root(&p);
        assert_eq!(root, Some(DeclId(21)));
        assert_eq!(join(&p, root, &[]), None);
        assert_eq!(join(&p, root, &[ty(3), ty(3)]), Some(ty(3)));
        assert_eq!(join(&p, root, &[ty(3), ty(4)]), Some(ty(2)));
        assert_eq!(join(&p, root, &[ty(3), ty(5)]), Some(ty(1)));
        assert_eq!(
            join(&p, root, &[ty(3), TypeId::BUBBLE]),
            Some(TypeId::BUBBLE)
        );
        assert!(is_failure_type(&p, root, ty(1)) && !is_failure_type(&p, root, TypeId::INT32));
        assert!(is_library_root(&p, root, DeclId(1)) && !is_library_root(&p, root, DeclId(2)));
        let module = &p.modules[0];
        let under = failures_under(&p, module.items[5]);
        let failed: Vec<TypeId> = under.iter().map(|&(_, f)| f).collect();
        assert_eq!(failed, [ty(3)]);
        assert_eq!(first_use(&p).as_deref(), Some("the failure type TError"));
    }
}
