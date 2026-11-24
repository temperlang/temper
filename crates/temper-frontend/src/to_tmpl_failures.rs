//! Declared failures in [`to_tmpl`](super) (RFC 164; notes/tmpl/nodes.md,
//! "Declared failures"): the library's base failure type, failure
//! classes' default messages, `throw`, and handlers that bind the failure
//! they handle, as `catch (e)` and a fallback that throws do.
//!
//! The choices people see are each in one place, as they may change:
//! the base's name ([`failure_base_name`]) and a failure's default
//! message (`interp::failures`, which the interpreter shares).

use super::{Translator, Tx};
use crate::builtins::Function;
use crate::core::{
    self, FN_LOOP_BODY, MEMBER_NO_PROPERTY, MEMBER_PUBLIC, MEMBER_STATIC, ROLE_MASK,
};
use temper_syntax::tree::{NodeId as FNode, NodeKind};
use temper_tmpl::{DeclId, DeclKind, Facts, NodeId, TypeData, TypeId};

/// The name of a library's base failure type: the library's name in
/// `PascalCase`, then `Error`, as `WherewithError` for `wherewith` and
/// `HostFailuresError` for `host-failures`. A hint, as for any
/// compiler-made declaration; Java and C# say `Exception` for `Error` in
/// every failure type's name (their naming rules'
/// [`failure_suffix`](temper_tmpl::naming::NamingRules::failure_suffix)).
/// Open for the team (RFC 164).
pub(super) fn failure_base_name(library: &str) -> String {
    let mut name = String::new();
    for word in library.split(|c: char| !c.is_alphanumeric()) {
        let mut chars = word.chars();
        if let Some(first) = chars.next() {
            name.extend(first.to_uppercase());
            name.push_str(chars.as_str());
        }
    }
    name.push_str("Error");
    name
}

/// What a handler's fallback holds of the failure it handles, if it
/// binds it: a `catch`'s local, or where the fallback runs after the
/// `catch` (as one that jumps does), a nullable local the `catch` set.
#[derive(Clone, Copy)]
pub(super) enum Caught {
    Bound(DeclId),
    Held(DeclId),
}

/// A handler's fallback being translated: whose it is, and what it holds
/// of the failure it handles, if anything, which a `throw` in it takes
/// as its cause and a `catch (e)` binding reads.
#[derive(Clone, Copy)]
pub(super) struct Handling {
    pub(super) owner: DeclId,
    pub(super) caught: Option<Caught>,
}

impl Translator<'_> {
    /// The library's base failure type, as a type: declared the first
    /// time a failure type extends core's `Error`, which it then extends
    /// in its place, in the library's first module, exported.
    pub(super) fn failure_base(&mut self, at: FNode) -> Tx<TypeId> {
        if let Some(base) = self.failure_base {
            return Ok(self.b.program().decls.get(base).ty);
        }
        let Some(root) = self.core_decl("Error") else {
            return self.not_yet(at, "core's Error");
        };
        let root = self.b.nominal(root);
        let name = failure_base_name(&self.b.program().library.name);
        let module = self.first_module;
        let base = self
            .b
            .declare_made(DeclKind::Interface, &[&name], module, TypeId::NONE);
        let ty = self.b.nominal(base);
        self.b.set_decl_type(base, ty);
        self.b
            .add_facts(base, Facts::EXPORTED | Facts::FAILURE_BASE);
        self.b.set_supers(base, &[root]);
        let span = self.span(at);
        self.b.set_decl_spans(base, span, span);
        let item = self.b.class(base, &[]);
        let item = self.made(item, at);
        self.b.add_item(module, item);
        self.failure_base = Some(base);
        Ok(ty)
    }

    /// A failure class's `message` getter, where it declares none: its
    /// default message, as the interpreter gives it
    /// (`interp::failures::default_message`): its name in lowercase
    /// words, then its public fields' names and values, as `not found
    /// (key: y)`. A string, number, or Boolean shows as its `toString()`,
    /// null as `null`, and anything else as its type's name. `None` for a
    /// class that isn't a failure type, or that has a member named
    /// `message`.
    pub(super) fn default_message(&mut self, class: FNode, decl: DeclId) -> Tx<Option<NodeId>> {
        let p = self.b.program();
        let is_class = p.decls.get(decl).kind == DeclKind::Class;
        if !is_class || !temper_tmpl::failures::is_below_root(p, decl) {
            return Ok(None);
        }
        let tree = &self.s.library.tree;
        let members = core::class_parts(tree, class).2.to_vec();
        let named_message = members.iter().any(|&m| {
            self.kind(m) == NodeKind::Let && self.name_of(core::let_parts(tree, m).0) == "message"
        });
        if named_message {
            return Ok(None);
        }
        let class_ty = self.b.program().decls.get(decl).ty;
        let sig = self.b.function_type(&[], TypeId::STRING);
        let getter = self.b.declare(DeclKind::Getter, "message", decl, sig);
        self.decl_spans(getter, class);
        let name = self.b.program().name(decl).to_string();
        let mut text = crate::interp::failures::words(&name);
        let mut joined: Option<NodeId> = None;
        let mut shown = 0;
        for m in members {
            let data = self.data(m);
            let public = data & MEMBER_PUBLIC != 0
                && data & (MEMBER_STATIC | MEMBER_NO_PROPERTY) == 0
                && matches!(data & ROLE_MASK, 0 | core::ROLE_PARAM);
            let field = match self.members.get(&m) {
                Some(super::Member::Field(d)) if public => *d,
                _ => continue,
            };
            if self.b.program().decls.get(field).kind != DeclKind::Field {
                continue;
            }
            text.push_str(if shown == 0 { " (" } else { ", " });
            text.push_str(self.b.program().name(field));
            text.push_str(": ");
            shown += 1;
            let words = self.b.string(&text);
            let words = self.made(words, m);
            joined = Some(self.joined(joined, words, m));
            text.clear();
            let value = self.field_shown(field, class_ty, m)?;
            joined = Some(self.joined(joined, value, m));
        }
        if shown > 0 {
            text.push(')');
        }
        if !text.is_empty() || joined.is_none() {
            let words = self.b.string(&text);
            let words = self.made(words, class);
            joined = Some(self.joined(joined, words, class));
        }
        let value = joined.expect("the message");
        let ret = self.b.return_stmt(Some(value));
        let ret = self.made(ret, class);
        let body = self.b.block(&[ret]);
        let body = self.made(body, class);
        let method = self.b.method(getter, &[], body);
        Ok(Some(self.made(method, class)))
    }

    /// A field of `this`, as a default message shows it.
    fn field_shown(&mut self, field: DeclId, class_ty: TypeId, m: FNode) -> Tx<NodeId> {
        let read = |t: &mut Self| {
            let this = t.b.this(class_ty);
            let this = t.made(this, m);
            let read = t.b.get_field(field, this);
            t.made(read, m)
        };
        let ty = self.b.program().decls.get(field).ty;
        let p = self.b.program();
        let shows = |ty: TypeId| {
            matches!(
                ty,
                TypeId::STRING | TypeId::BOOLEAN | TypeId::INT32 | TypeId::INT64 | TypeId::FLOAT64
            )
        };
        // Anything else, as its type's name.
        let named = |t: &mut Self, ty: TypeId| {
            let p = t.b.program();
            let name = match temper_tmpl::analysis::nominal_decl(p, ty) {
                Some(d) => p.name(d).to_string(),
                None => p.show_type(ty),
            };
            let made = t.b.string(&name);
            t.made(made, m)
        };
        if !p.types.is_nullable(ty) {
            if shows(ty) {
                let value = read(self);
                return self.part_string(value, m);
            }
            return Ok(named(self, ty));
        }
        let inner = p.types.non_null(ty);
        let value = read(self);
        let check = self.b.is_null(value);
        let check = self.made(check, m);
        let null = self.b.string("null");
        let null = self.made(null, m);
        let shown = if shows(inner) {
            let value = read(self);
            let value = self.b.non_null(value);
            let value = self.made(value, m);
            self.part_string(value, m)?
        } else {
            named(self, inner)
        };
        let made = self.b.if_value(TypeId::STRING, check, null, shown);
        Ok(self.made(made, m))
    }

    /// `throw x`, typed as its place wants, with the failure being
    /// handled, if any, as its cause.
    pub(super) fn throw(&mut self, node: FNode) -> Tx<NodeId> {
        let [value] = self.children(node).to_vec()[..] else {
            return self.not_yet(node, "a throw's form");
        };
        let value = self.expr(value)?;
        let cause = self.handled_failure(node);
        let made = self.b.throw(TypeId::NONE, value, cause);
        Ok(self.own(made, node))
    }

    /// A `catch (e)` binding's value, the hidden `caught()`: the failure
    /// the fallback it's in handles.
    pub(super) fn caught(&mut self, node: FNode) -> Tx<NodeId> {
        match self.handled_failure(node) {
            Some(read) => Ok(read),
            None => self.not_yet(node, "a catch binding what it can't bind"),
        }
    }

    /// The failure being handled where `node` is, read: that of the
    /// innermost handler's fallback it's in, in this function, if that
    /// binds it, as a `throw` there takes it as its cause.
    fn handled_failure(&mut self, node: FNode) -> Option<NodeId> {
        let handling = self.handling.iter().rev().find(|h| h.owner == self.owner)?;
        let read = match handling.caught? {
            Caught::Bound(local) => {
                let read = self.b.local(local);
                self.made(read, node)
            }
            Caught::Held(held) => {
                let read = self.b.local(held);
                let read = self.made(read, node);
                let read = self.b.non_null(read);
                self.made(read, node)
            }
        };
        Some(read)
    }

    /// Whether a handler's fallback needs the failure it handles: it binds
    /// it (`catch (e)`), or throws, in it or a `for … of` body in it, not
    /// in another function.
    pub(super) fn needs_failure(&self, fallback: FNode) -> bool {
        let mut stack = vec![fallback];
        while let Some(node) = stack.pop() {
            match self.kind(node) {
                NodeKind::Fail if !self.children(node).is_empty() => return true,
                NodeKind::Call
                    if self
                        .children(node)
                        .first()
                        .is_some_and(|&c| self.function_of(c) == Some(Function::Caught)) =>
                {
                    return true;
                }
                NodeKind::Fn if self.data(node) & FN_LOOP_BODY == 0 => continue,
                _ => {}
            }
            stack.extend_from_slice(self.children(node));
        }
        false
    }

    /// What a translated handler's body passes to its handler, as one
    /// type, if that's a failure type a `catch` binds, not `Bubble`
    /// ([`temper_tmpl::failures::join`]).
    pub(super) fn handled_type(&self, body: NodeId) -> Option<TypeId> {
        let p = self.b.program();
        let fails: Vec<TypeId> = (temper_tmpl::failures::failures_under(p, body).iter())
            .map(|&(_, f)| f)
            .collect();
        let root = temper_tmpl::failures::root(p);
        let joined = temper_tmpl::failures::join(p, root, &fails)?;
        let fits = temper_tmpl::failures::is_failure_type(p, root, joined)
            || matches!(p.types.get(joined), TypeData::TypeParam { .. });
        fits.then_some(joined)
    }

    /// The `let e = caught()` that starts a `catch (e)`'s block, if
    /// `fallback` is one.
    pub(super) fn catch_binding(&self, fallback: FNode) -> Option<FNode> {
        if self.kind(fallback) != NodeKind::Block {
            return None;
        }
        let &first = self.children(fallback).first()?;
        let init = (self.kind(first) == NodeKind::Let)
            .then(|| core::let_parts(&self.s.library.tree, first).2)
            .flatten()?;
        let callee = *self.children(init).first()?;
        let binds =
            self.kind(init) == NodeKind::Call && self.function_of(callee) == Some(Function::Caught);
        binds.then_some(first)
    }
}
