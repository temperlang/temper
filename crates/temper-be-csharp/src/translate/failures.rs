//! Declared failures (RFC 164; notes/tmpl/nodes.md, "Declared failures")
//! as C# exceptions.
//!
//! - **Types:** each library's failure types are classes under its roots,
//!   `public abstract class WherewithException : Exception, IFailure`
//!   (the runtime's marker, for core's `Error`); a sealed failure
//!   interface is an abstract class under the one failure type above it,
//!   and a failure class a `sealed` one. C# can't seal a hierarchy across
//!   files, so each abstract one's constructor is `private protected`:
//!   only the library's own failure types extend them. The `…Error` →
//!   `…Exception` rule names them (`NamingRules::failure_suffix`), and
//!   none takes an interface's `I`.
//! - **Messages:** a failure's `message` getter is `Message`, overriding
//!   `Exception`'s; core's `message` reads it. A member named as another
//!   of `Exception`'s (`Source`, `Data`, …) is "not yet".
//! - **Causes:** `InnerException`, which C# sets only through
//!   `Exception`'s constructor, so passed as hand-written C# passes it:
//!   each failure class's public constructor calls an internal one that
//!   takes the cause too, `: this(key, null)`, and `throw new X(…)` in a
//!   handler calls that one, `throw new X(…, e)`, so only the failure
//!   thrown takes it, never one made among its arguments. A failure made
//!   before and thrown in a handler, or another library's, whose internal
//!   constructor is out of reach, keeps the `InnerException` it was made
//!   with; `Core.WithCause(x, e)` keeps the handled one beside it, for
//!   Temper's `cause` (`Core.FailureCause`) alone, as a failure thrown
//!   again keeps its first.
//! - **Handlers:** an `orelse` or `catch` catches exactly what its body
//!   may fail with, `Bubble` and the failure class nearest above its
//!   declared failures, `catch (NotFound e)`, or where both, `catch
//!   (Exception e) when (e is Bubble || e is NotFound)`, a filter, which
//!   leaves the rest unhandled, never caught and thrown again: panics
//!   and hosts' exceptions pass.
//! - **Docs:** a function or method that fails with a failure type says
//!   so in an XML doc comment, `/// <exception cref="NotFound">…`.

use super::{R, Translator, not_yet};
use crate::tree::{Access, Expr, Member};
use std::collections::HashSet;
use temper_tmpl::analysis::nominal_decl;
use temper_tmpl::qname::is_external;
use temper_tmpl::{DeclId, DeclKind, Facts, Node, NodeId, Program, TypeId};

/// `Exception`'s members that a failure type's own mustn't hide, as
/// they'd change what C#'s exceptions do or their types clash: all but
/// `Message`, which a failure's `message` overrides, and `ToString()`.
const EXCEPTION_MEMBERS: &[&str] = &[
    "Data",
    "GetBaseException",
    "GetObjectData",
    "GetType",
    "HResult",
    "HelpLink",
    "InnerException",
    "Source",
    "StackTrace",
    "TargetSite",
];

/// Whether a declaration is a declared failure type of the program's own
/// (`temper_tmpl::failures`): a class, or an interface (the library's
/// base, or a sealed one) as an abstract class, since C#'s exceptions are
/// classes.
pub(super) fn failure_class(p: &Program, decl: DeclId) -> bool {
    matches!(
        p.decls.get(decl).kind,
        DeclKind::Class | DeclKind::Interface
    ) && !is_external(p, decl)
        && temper_tmpl::failures::is_failure_decl(p, temper_tmpl::failures::root(p), decl)
}

/// Whether a node reads a compiler-made local holding a failure or null,
/// a `catch`'s `~caught`, which C# doesn't narrow, as its check is of
/// another (`~held`), so takes `!`.
pub(super) fn is_kept_failure(p: &Program, id: NodeId) -> bool {
    let Node::Local { local } = p.nodes.get(id) else {
        return false;
    };
    let d = p.decls.get(local);
    d.facts.contains(Facts::COMPILER_MADE)
        && nominal_decl(p, p.types.non_null(d.ty)).is_some_and(|c| failure_class(p, c))
}

impl Translator<'_> {
    /// A failure type's bases: the one failure type above it, or for one
    /// of the library's roots, `Exception` and the runtime's marker, `IFailure`,
    /// core's `Error`; then any others.
    pub(super) fn failure_bases(&mut self, decl: DeclId) -> R<Vec<String>> {
        let p = self.p;
        let root = temper_tmpl::failures::root(p);
        let mut parent = None;
        let mut others = Vec::new();
        for &s in p.decls.supers(decl) {
            let up = nominal_decl(p, s);
            if up.is_some() && up == root {
                if parent.is_some() {
                    return not_yet("a failure type with two failure types above it");
                }
                let Expr::Name(exception) = self.class("Exception") else {
                    unreachable!()
                };
                parent = Some(vec![exception, self.ty(s)?]);
            } else if up.is_some_and(|u| failure_class(p, u)) {
                if parent.is_some() {
                    return not_yet("a failure type with two failure types above it");
                }
                parent = Some(vec![self.ty(s)?]);
            } else {
                others.push(self.ty(s)?);
            }
        }
        let Some(mut bases) = parent else {
            return not_yet("a failure type without one above it");
        };
        bases.extend(others);
        Ok(bases)
    }

    /// A failure type's members as C# has them: its constructors, the
    /// cause passed down to `Exception`'s ([`Self::failure_constructors`]);
    /// `Message` overriding `Exception`'s; and none named as `Exception`'s
    /// others.
    pub(super) fn failure_members(
        &mut self,
        item: NodeId,
        decl: DeclId,
        abstract_class: bool,
        members: &mut Vec<Member>,
    ) -> R<()> {
        for m in members.iter_mut() {
            let member = match m {
                Member::Method { name, .. } => name,
                Member::Property {
                    name,
                    modifiers,
                    get,
                    set,
                    ..
                } => {
                    if name == "Message" {
                        if set.is_some() || get.as_ref().is_none_or(|g| g.body.is_none()) {
                            return not_yet("a failure's message that isn't a getter");
                        }
                        *modifiers = "override ";
                    }
                    name
                }
                _ => continue,
            };
            if EXCEPTION_MEMBERS.contains(&member.as_str()) {
                return not_yet(format!("a failure type's member named {member}"));
            }
        }
        if abstract_class
            && (members.iter())
                .any(|m| matches!(m, Member::Method { .. } | Member::Property { .. }))
        {
            return not_yet("a failure interface with members");
        }
        self.failure_constructors(item, decl, abstract_class, members);
        Ok(())
    }

    /// A failure type's constructors. C# sets `InnerException` only
    /// through `Exception`'s constructor, so as hand-written C# passes it:
    /// a failure class's constructor, `public NotFound(string key)`,
    /// becomes an internal one that takes the cause too and has its body,
    /// `internal NotFound(string key, Exception? cause) : base(cause)`,
    /// which the public one calls with none, `: this(key, null)`, and a
    /// `throw` in a handler with the handled failure ([`Self::thrown`]);
    /// an abstract one's, `private protected`, passes it on, and the
    /// library's base's to `Exception`'s, `base(null, cause)`, as
    /// `Message` is each class's own. The cause's parameter is `cause`,
    /// numbered where the class has a name of that already.
    fn failure_constructors(
        &mut self,
        item: NodeId,
        decl: DeclId,
        abstract_class: bool,
        members: &mut Vec<Member>,
    ) {
        let p = self.p;
        let name = self.simple_type_name(decl);
        let Expr::Name(exception) = self.class("Exception") else {
            unreachable!()
        };
        let cause_ty = format!("{exception}?");
        if abstract_class {
            let base = p.decls.supers(decl).iter().any(|&s| {
                let up = nominal_decl(p, s);
                up.is_some() && up == temper_tmpl::failures::root(p)
            });
            let init = match base {
                true => Expr::name("base").call(vec![Expr::name("null"), Expr::name("cause")]),
                false => Expr::name("base").call(vec![Expr::name("cause")]),
            };
            members.insert(
                0,
                Member::Constructor {
                    access: Access::PrivateProtected,
                    name,
                    params: vec![(cause_ty, "cause".into())],
                    init: Some(init),
                    body: Vec::new(),
                },
            );
            return;
        }
        let at = (members.iter())
            .position(|m| matches!(m, Member::Constructor { .. }))
            .unwrap_or_else(|| {
                members.insert(
                    0,
                    Member::Constructor {
                        access: Access::Public,
                        name: name.clone(),
                        params: Vec::new(),
                        init: None,
                        body: Vec::new(),
                    },
                );
                0
            });
        let Member::Constructor { params, body, .. } = &mut members[at] else {
            unreachable!()
        };
        let body = std::mem::take(body);
        // Its parameters as the internal one has them: all given.
        let mut given: Vec<(String, String)> = (params.iter())
            .map(|(t, n)| (t.clone(), n.trim_end_matches(" = null").to_string()))
            .collect();
        let cause = self.free_name(item, "cause", &given);
        let mut args: Vec<Expr> = given.iter().map(|(_, n)| Expr::name(n.clone())).collect();
        args.push(Expr::name("null"));
        if let Member::Constructor { init, .. } = &mut members[at] {
            *init = Some(Expr::name("this").call(args));
        }
        given.push((cause_ty, cause.clone()));
        members.insert(
            at + 1,
            Member::Constructor {
                access: Access::Internal,
                name,
                params: given,
                init: Some(Expr::name("base").call(vec![Expr::name(cause)])),
                body,
            },
        );
    }

    /// `base`, numbered (`cause2`) where it'd clash with a name declared
    /// in `item`, a type, or one of `params`.
    fn free_name(&self, item: NodeId, base: &str, params: &[(String, String)]) -> String {
        let p = self.p;
        let mut taken: HashSet<String> = (params.iter())
            .map(|(_, n)| n.trim_start_matches('@').to_string())
            .collect();
        temper_tmpl::analysis::walk(p, item, &mut |id| {
            let declared = match p.nodes.get(id) {
                Node::Param { decl } => decl,
                Node::Let { local, .. }
                | Node::Catch { local, .. }
                | Node::Local { local }
                | Node::Assign { local, .. } => local,
                _ => return,
            };
            taken.insert(self.name(declared).trim_start_matches('@').to_string());
        });
        (1..)
            .map(|n| match n {
                1 => base.to_string(),
                n => format!("{base}{n}"),
            })
            .find(|n| !taken.contains(n))
            .unwrap_or_default()
    }

    /// What a function or method says it throws, for its XML doc comment:
    /// the declared failure type its signature fails with, as
    /// `<exception cref="NotFound">`; nothing for one that bubbles, or
    /// fails with a type parameter.
    pub(super) fn throws_doc(&mut self, decl: DeclId) -> R<Vec<String>> {
        let p = self.p;
        let root = temper_tmpl::failures::root(p);
        match p.signature(decl).and_then(|s| s.fail) {
            Some(fail) if temper_tmpl::failures::is_failure_type(p, root, fail) => {
                let ty = self.ty(fail)?;
                Ok(vec![format!(
                    "<exception cref=\"{ty}\">Where it fails with {ty}.</exception>"
                )])
            }
            _ => Ok(Vec::new()),
        }
    }

    /// What a handler around `body` catches, its `catch` clause's
    /// classes: `Bubble`, from the runtime, if `body` may bubble, and the
    /// failure class nearest above all its declared failures, never what
    /// else C# throws, so panics and hosts' exceptions pass. Not yet where
    /// that's core's `Error`, for failures of several libraries.
    pub(super) fn handled(&mut self, body: NodeId) -> R<Vec<String>> {
        let p = self.p;
        let fails: Vec<TypeId> = (temper_tmpl::failures::failures_under(p, body).into_iter())
            .map(|(_, f)| f)
            .collect();
        let mut classes = Vec::new();
        // A body that can't fail, as one `to_tmpl` kept: `Bubble`'s.
        if fails.contains(&TypeId::BUBBLE) || fails.is_empty() {
            let Expr::Name(bubble) = self.class("Bubble") else {
                unreachable!()
            };
            classes.push(bubble);
        }
        let declared: Vec<TypeId> = fails.into_iter().filter(|&f| f != TypeId::BUBBLE).collect();
        let root = temper_tmpl::failures::root(p);
        if let Some(ty) = temper_tmpl::failures::join(p, root, &declared) {
            if nominal_decl(p, ty) == root {
                return not_yet("a handler of failures of several libraries");
            }
            classes.push(self.ty(ty)?);
        }
        Ok(classes)
    }

    /// `throw value`'s value, with its cause, the failure a handler
    /// handles, where it's thrown there: a failure of this library's made
    /// there, `new X(…)`, by the internal constructor that takes the cause
    /// too (`new X(…, e)`, every argument given); any other,
    /// `Core.WithCause(x, e)`, which keeps a cause it has. Another
    /// library's failure class's internal constructor is out of reach, so
    /// one made there takes the cause as one made before does.
    pub(super) fn thrown(&mut self, value: NodeId, cause: Option<NodeId>) -> R<Expr> {
        let p = self.p;
        let Some(cause) = cause else {
            return self.expr(value);
        };
        match p.nodes.get(value) {
            Node::New { ctor, args, .. } if failure_class(p, p.decls.get(ctor).owner) => {
                let ty = self.ty(p.nodes.ty(value))?;
                let mut args = self.args(p.nodes.list(args))?;
                let all = p.signature(ctor).map_or(0, |s| s.params.len());
                args.resize(all.max(args.len()), Expr::name("null"));
                args.push(self.expr(cause)?);
                Ok(Expr::name(format!("new {ty}")).call(args))
            }
            _ => {
                let thrown = self.expr(value)?;
                let cause = self.expr(cause)?;
                Ok((self.class("Core")).method("WithCause", vec![thrown, cause]))
            }
        }
    }
}
