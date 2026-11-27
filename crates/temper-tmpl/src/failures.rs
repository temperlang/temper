//! Declared failures (RFC 164; notes/tmpl/nodes.md, "Declared failures"):
//! what makes a failure type, what each node may fail with, and where a
//! program first uses them, for targets that don't have them yet.
//!
//! A failure type is a class or interface below core's `Error`, the root,
//! which a program has as an external interface where it uses failures.

use crate::Program;
use crate::analysis::{ancestors, bindings, nominal_decl, walk};
use crate::decls::DeclKind;
use crate::ids::{DeclId, NodeId, TypeId};
use crate::qname::{is_external, library_of};
use crate::schema::{Node, Payload, Slot};
use crate::types::{TypeArgs, TypeData};

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

/// What a generator literal's body may fail with, for its factory's
/// signature: core's `Error`, any error, where the factory makes core's
/// `Generator<Y>`, whose `next()` throws it; nothing where it makes a
/// `SafeGenerator<Y>`, which can't fail.
pub fn generator_failure(p: &Program, factory: DeclId) -> Option<TypeId> {
    let made = p.signature(factory)?.pass;
    let decl = nominal_decl(p, made)?;
    let is_generator =
        p.name(decl) == "Generator" && is_external(p, decl) && library_of(p, decl) == "core";
    Some(p.decls.get(root(p).filter(|_| is_generator)?).ty)
}

/// Whether a node reads a compiler-made local that a handler sets and a
/// check of another local guards, so a target's checker can't narrow it
/// from null: a `catch`'s `~caught` of core's `Error`, and a `~held`
/// `GeneratorResult`, which a flag guards, as some hosts write its
/// `DoneResult` as null. Targets read either as not null (`!`, a cast),
/// as they do their own failure types' `~caught`.
pub fn is_guarded_elsewhere(p: &Program, id: NodeId) -> bool {
    let Node::Local { local } = p.nodes.get(id) else {
        return false;
    };
    let d = p.decls.get(local);
    let core = |c: DeclId, name: &str| p.name(c) == name && library_of(p, c) == "core";
    d.facts.contains(crate::decls::Facts::COMPILER_MADE)
        && nominal_decl(p, p.types.non_null(d.ty))
            .is_some_and(|c| core(c, "Error") || core(c, "GeneratorResult"))
}

/// Whether `ty` is core's `Error`, any error: a test's failure, and
/// what std's `testing` takes.
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
/// [`is_failure_decl`], or a failure parameter ([`is_failure_param`]).
pub fn is_failure_type(p: &Program, root: Option<DeclId>, ty: TypeId) -> bool {
    match p.types.get(ty) {
        TypeData::TypeParam { decl } => root.is_some() && is_failure_param(p, decl),
        _ => nominal_decl(p, ty).is_some_and(|d| is_failure_decl(p, root, d)),
    }
}

/// Whether a type parameter is a failure parameter (RFC 164): one bound
/// by a failure type, as `E extends Error`, which the frontend gives one
/// that a signature fails with.
pub fn is_failure_param(p: &Program, decl: DeclId) -> bool {
    p.decls.contains(decl)
        && p.decls.get(decl).kind == DeclKind::TypeParam
        && p.decls
            .supers(decl)
            .iter()
            .any(|&b| is_nominal_failure(p, b))
}

/// Whether `ty` is a failure class's or interface's, core's `Error`
/// among them, found without the root at hand.
fn is_nominal_failure(p: &Program, ty: TypeId) -> bool {
    nominal_decl(p, ty).is_some_and(|d| is_root(p, d) || is_below_root(p, d))
}

/// The failure type a function or method declares, as targets document
/// it (a `throws` clause, a doc comment): a failure class's or
/// interface's, never a failure parameter, which exceptions
/// leave unchecked.
pub fn declared(p: &Program, decl: DeclId) -> Option<TypeId> {
    let fail = p.signature(decl)?.fail?;
    is_nominal_failure(p, fail).then_some(fail)
}

/// A generic declaration's type parameters as targets whose failures are
/// unchecked exceptions write them, without failure parameters
/// ([`erase_params`]).
pub fn unchecked_type_params(p: &Program, decl: DeclId) -> Vec<DeclId> {
    (p.decls.type_params(decl).iter().copied())
        .filter(|&t| !is_failure_param(p, t))
        .collect()
}

/// Type arguments for a generic declaration's type parameters, as
/// [`unchecked_type_params`] leaves them: without failure parameters'.
pub fn unchecked_args(p: &Program, decl: DeclId, args: &[TypeId]) -> Vec<TypeId> {
    let params = p.decls.type_params(decl);
    if params.len() != args.len() {
        return args.to_vec();
    }
    (params.iter().zip(args))
        .filter(|&(&t, _)| !is_failure_param(p, t))
        .map(|(_, &a)| a)
        .collect()
}

/// What a node may fail with, if anything: a failing call's callee's
/// failure type, with its type arguments put in, so nothing where a
/// failure parameter is given `Never<…>` ([`declared_failure`]); a
/// `throw`'s value's type; and core's `BrokenPromise` for an `await`.
/// `Error` nodes panic.
pub fn node_failure(p: &Program, id: NodeId) -> Option<TypeId> {
    match p.nodes.get(id) {
        Node::CallFunction {
            may_bubble: true, ..
        }
        | Node::New {
            may_bubble: true, ..
        }
        | Node::CallMethod {
            may_bubble: true, ..
        }
        | Node::CallValue {
            may_bubble: true, ..
        } => declared_failure(p, id).filter(|&f| !p.types.is_never(f)),
        Node::Throw { value, .. } => Some(p.nodes.ty(value)),
        Node::Await {
            may_bubble: true, ..
        } => broken_promise(p),
        _ => None,
    }
}

/// What a call's callee declares it fails with, its type arguments put
/// in, or a function value's type ([`value_failure`]): `Never<…>` where
/// a failure parameter is given it, so the call can't fail, but where
/// targets whose failure parameters are generic (Rust, C++) still see a
/// result, which can't be a failure.
pub fn declared_failure(p: &Program, id: NodeId) -> Option<TypeId> {
    if let Node::CallValue { callee, .. } = p.nodes.get(id) {
        return value_failure(p, p.nodes.ty(callee));
    }
    let (callee, args) = call_generic(p, id)?;
    let callee = match p.nodes.get(id) {
        Node::New { ctor, .. } => ctor,
        _ => callee,
    };
    let fail = p.signature(callee)?.fail?;
    let receiver = match p.nodes.get(id) {
        Node::CallMethod { receiver, .. } => Some(p.nodes.ty(receiver)),
        _ => None,
    };
    let (from, to) = bindings(p, callee, receiver, p.types.args_of(args));
    Some(p.types.substituted(fail, &from, &to).unwrap_or(fail))
}

/// What a function value of type `ty` declares it fails with: a function
/// type's failure, or a `@fun` interface's `apply`'s with the
/// interface's type arguments put in, as a failure parameter's
/// `Never<…>`, "can't fail", which a function type leaves out. Targets
/// whose failure parameters are generic (Rust, C++) write a value of
/// `Mapper<Int, Int, Never<Error>>` as one that returns a result, as
/// generic code calling it expects.
pub fn value_failure(p: &Program, ty: TypeId) -> Option<TypeId> {
    match p.types.contains(ty).then(|| p.types.get(ty))? {
        TypeData::Function { .. } => p.types.signature(ty)?.fail,
        TypeData::Nominal { decl, args } => {
            let apply = crate::analysis::fun_apply(p, decl)?;
            let fail = p.signature(apply)?.fail?;
            let from = crate::analysis::type_param_types(p, decl);
            let to = p.types.list(args).to_vec();
            Some(p.types.substituted(fail, &from, &to).unwrap_or(fail))
        }
        _ => None,
    }
}

/// Whether any of a call's arguments is a callback that returns a result
/// where failure parameters are generic (Rust, C++): one that may fail,
/// or a `@fun` interface's value that can't ([`value_failure`]), through
/// a conversion to or from a function type. A call of core's member that
/// takes callbacks is its form for callbacks that return results then.
pub fn passes_results(p: &Program, args: &[NodeId]) -> bool {
    args.iter().any(|&a| {
        let a = match p.nodes.get(a) {
            Node::ToFun { value } | Node::FromFun { value } => value,
            _ => a,
        };
        value_failure(p, p.nodes.ty(a)).is_some()
    })
}

/// What may fail under `id` and pass out of it, each node with what it
/// may fail with ([`node_failure`]): not what a handler's body passes to
/// its handler, and not what's in a function literal.
pub fn failures_under(p: &Program, id: NodeId) -> Vec<(NodeId, TypeId)> {
    let mut found = Vec::new();
    find(p, id, &mut found);
    found
}

/// Whether anything may fail under `id` and pass out of it
/// ([`failures_under`]).
pub fn may_fail(p: &Program, id: NodeId) -> bool {
    !failures_under(p, id).is_empty()
}

/// The program with each `orelse` and `catch` whose body can't fail
/// ([`may_fail`]) as that body alone, its fallback dead, as `to_tmpl`
/// writes one, so no backend sees one; `None` where there's none. A
/// handler's body has its type, but in a `Void` one, whose place drops
/// any value.
pub fn drop_dead_handlers(p: &Program) -> Option<Program> {
    let mut dead = Vec::new();
    for root in p.modules.iter().flat_map(|m| m.roots()) {
        walk(p, root, &mut |n| match p.nodes.get(n) {
            Node::OrElse { body, .. } | Node::Catch { body, .. } if !may_fail(p, body) => {
                dead.push((n, body));
            }
            _ => {}
        });
    }
    if dead.is_empty() {
        return None;
    }
    let mut b = crate::Builder::from_program(p.clone());
    // Inner ones first, so an outer one copies its body as it ends up.
    for &(handler, body) in dead.iter().rev() {
        b.replace_with(handler, body);
    }
    b.renumber();
    Some(b.finish())
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
/// them: the closest failure type every one is (itself, or one of its supertypes, nearest first), or the root
/// (`root`'s type) where there's none closer; `None` for none.
pub fn join(p: &Program, root: Option<DeclId>, fails: &[TypeId]) -> Option<TypeId> {
    let (&first, rest) = fails.split_first()?;
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

/// The program with failure parameters (RFC 164) erased to their bounds
/// in code, for targets whose failures are unchecked exceptions, where a
/// handler in generic code catches what its bound does, by the runtime's
/// marker for core's `Error`; `None` where it has none. Every node's
/// type, type argument, and type payload changes, and the types of what
/// code declares (locals, a `catch`'s, function literals and their
/// parameters), but not declarations' signatures, so calls of a generic
/// function still fail with what they give it: outside it, nothing
/// mentions its failure parameters, and inside it, they're their bounds.
/// Targets leave failure parameters out of what they write
/// ([`unchecked_type_params`], [`unchecked_args`]): exceptions are
/// unchecked, so no host signature needs one. Not yet, so an error: a
/// signature that names one other than as what something fails with, as
/// `e: E`, which a host would need it for.
pub fn erase_params(p: &Program) -> Result<Option<Program>, String> {
    let Some(root) = root(p) else {
        return Ok(None);
    };
    let (mut from, mut to) = (Vec::new(), Vec::new());
    for d in p.decls.ids().filter(|&d| is_failure_param(p, d)) {
        let bound =
            (p.decls.supers(d).iter().copied()).find(|&b| is_failure_type(p, Some(root), b));
        from.push(p.decls.get(d).ty);
        to.push(bound.unwrap_or(p.decls.get(root).ty));
    }
    if from.is_empty() {
        return Ok(None);
    }
    let signature = |d: DeclId| {
        p.decls.contains(d)
            && matches!(
                p.decls.get(d).kind,
                DeclKind::Function
                    | DeclKind::Method
                    | DeclKind::Getter
                    | DeclKind::Setter
                    | DeclKind::Constructor
                    | DeclKind::Field
                    | DeclKind::StaticField
            )
    };
    if let Some(d) = p
        .decls
        .ids()
        .find(|&d| signature(d) && names_as_value(p, p.decls.get(d).ty))
    {
        return Err(format!(
            "failure parameters: {}'s signature names one other than as what it fails with",
            p.name(d)
        ));
    }
    let mut b = crate::Builder::from_program(p.clone());
    let in_code = |d: DeclId| match p.decls.get(d).kind {
        DeclKind::Local | DeclKind::Lambda => true,
        DeclKind::Param => p.decls.get(p.decls.get(d).owner).kind == DeclKind::Lambda,
        _ => false,
    };
    for d in p.decls.ids().filter(|&d| p.decls.contains(d) && in_code(d)) {
        let ty = b
            .program_mut()
            .types
            .substitute(p.decls.get(d).ty, &from, &to);
        b.set_decl_type(d, ty);
    }
    for id in (0..p.nodes.len()).map(|i| NodeId(i as u32)) {
        let types = &mut b.program_mut().types;
        let ty = types.substitute(p.nodes.ty(id), &from, &to);
        let kind = p.nodes.kind(id);
        let (mut a, mut c) = p.nodes.slots(id);
        for field in kind.fields() {
            let erased = |types: &mut crate::TypeTable, slot: u32, payload: Payload| match payload {
                Payload::Type => types.substitute(TypeId(slot), &from, &to).0,
                Payload::Args => {
                    let args = types.args_of(TypeArgs(slot)).to_vec();
                    let args: Vec<TypeId> = args
                        .iter()
                        .map(|&t| types.substitute(t, &from, &to))
                        .collect();
                    types.type_args(&args).0
                }
                _ => slot,
            };
            match field.slot {
                Slot::A(payload) => a = erased(types, a, payload),
                Slot::B(payload) => c = erased(types, c, payload),
                _ => {}
            }
        }
        b.retype_lowered(id, ty, (a, c));
    }
    Ok(Some(b.finish()))
}

/// Whether a type names a failure parameter other than as what something
/// fails with, or as another failure parameter's type argument.
fn names_as_value(p: &Program, ty: TypeId) -> bool {
    if !p.types.contains(ty) {
        return false;
    }
    match p.types.get(ty) {
        TypeData::TypeParam { decl } => is_failure_param(p, decl),
        TypeData::Scalar(_) => false,
        TypeData::Nullable(inner) | TypeData::Never(inner) => names_as_value(p, inner),
        TypeData::Result { pass, .. } => names_as_value(p, pass),
        TypeData::Nominal { decl, args } => {
            let args = p.types.list(args);
            unchecked_args(p, decl, args)
                .iter()
                .any(|&a| names_as_value(p, a))
        }
        TypeData::Function { .. } => p.types.signature(ty).is_some_and(|sig| {
            (sig.params.iter().chain([&sig.returns])).any(|&t| names_as_value(p, t))
        }),
    }
}

/// A call's generic declaration, whose type parameters its type
/// arguments are for (a constructor's class), with those arguments.
pub fn call_generic(p: &Program, id: NodeId) -> Option<(DeclId, TypeArgs)> {
    match p.nodes.get(id) {
        Node::CallFunction {
            callee, type_args, ..
        } => Some((callee, type_args)),
        Node::CallMethod {
            method, type_args, ..
        } => Some((method, type_args)),
        Node::New {
            ctor, type_args, ..
        } => Some((p.decls.get(ctor).owner, type_args)),
        _ => None,
    }
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
/// uses none.
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
        if let Some(fail) = fail {
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
    /// there's none closer; failures under a handler's body are its own,
    /// and only its fallback passes them on.
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
        assert!(is_failure_type(&p, root, ty(1)) && !is_failure_type(&p, root, TypeId::INT32));
        assert!(is_library_root(&p, root, DeclId(1)) && !is_library_root(&p, root, DeclId(2)));
        let module = &p.modules[0];
        let under = failures_under(&p, module.items[5]);
        let failed: Vec<TypeId> = under.iter().map(|&(_, f)| f).collect();
        assert_eq!(failed, [ty(3)]);
        assert_eq!(first_use(&p).as_deref(), Some("the failure type TError"));
    }

    const GENERIC: &str = "tmpl 1 model 1 library t 0.1.0

uses `temper-core`#20 0.1.0 {
  interface Error#21 {
    connected get message#22(): String;
    connected get cause#23(): Error#21?;
  }
}

module t#0 {
  export interface TError#1 extends Error#21 {}

  export class NotFound#2 extends TError#1 {
    constructor constructor#5(): NotFound#2 {}
  }

  export fn again#3<E#6 extends Error#21>(f#7: fn (): Int32 throws E#6): Int32 throws E#6 {
    return (catch(f#7()^, e#8: E#6, (throw(e#8)^): Int32)): Int32;
  }

  fn lookup#4(): Int32 throws NotFound#2 {
    return (throw(new constructor#5())^): Int32;
  }

  init {
    let a#9: Int32 = (orelse(again#3<NotFound#2>((fn ~fn#10(): Int32 throws NotFound#2 {
      return lookup#4()^;
    }): fn (): Int32 throws NotFound#2)^, 0)): Int32;
  }
}
";

    /// Erasing failure parameters changes code in generic functions, which
    /// handles their bounds, but not signatures, so a call still fails
    /// with what it gives the parameter.
    #[test]
    fn erased_failure_parameters() {
        let p = crate::text::read(GENERIC).unwrap();
        assert!(crate::validate(&p, &crate::Profile::full()).is_empty());
        let root = root(&p);
        assert!(is_failure_param(&p, DeclId(6)));
        assert!(is_failure_type(&p, root, p.decls.get(DeclId(6)).ty));
        let e = erase_params(&p).unwrap().unwrap();
        let error = e.decls.get(DeclId(21)).ty;
        assert_eq!(e.decls.get(DeclId(8)).ty, error);
        assert_eq!(e.decls.get(DeclId(3)).ty, p.decls.get(DeclId(3)).ty);
        let module = &e.modules[0];
        let fails = |item: NodeId| -> Vec<TypeId> {
            failures_under(&e, item).iter().map(|&(_, f)| f).collect()
        };
        // `again`'s `throw e`, now of `Error`; the call in the init block
        // still fails with `NotFound`, and its handler's body too.
        assert_eq!(fails(module.items[2]), [error]);
        let calls: Vec<TypeId> = (0..e.nodes.len() as u32)
            .map(NodeId)
            .filter(|&n| matches!(e.nodes.get(n), Node::CallFunction { callee, .. } if callee == DeclId(3)))
            .filter_map(|n| node_failure(&e, n))
            .collect();
        assert_eq!(calls, [p.decls.get(DeclId(2)).ty]);
        assert_eq!(erase_params(&crate::text::read(PROGRAM).unwrap()), Ok(None));
        // A failure parameter as a parameter's type: not yet.
        let start = GENERIC.find("module t#0").unwrap();
        let named = format!(
            "{}module t#0 {{\n  fn keep#1<E#2 extends Error#21>(e#3: E#2): E#2 {{\n    \
             return e#3;\n  }}\n\n  init {{}}\n}}\n",
            &GENERIC[..start]
        );
        let named = crate::text::read(&named).unwrap();
        assert!(
            erase_params(&named)
                .unwrap_err()
                .contains("keep's signature")
        );
    }
}
