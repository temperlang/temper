//! A user's `@connected` class, as Kotlin Temper's `TypeDeclChecker`
//! checks one (notes/kotlin-differences.md, "A user's `@connected`
//! type"). Its methods can't have default arguments, since host code
//! implements the same methods. It may have a complete Temper
//! implementation, a body for every method, which the interpreter and
//! backends without host code use instead, but not a partial one; and if
//! it lists backends, as `@connected(["cpp", "rust"])`, the others need
//! that implementation, so it must be complete. Core's and std's
//! connected classes work otherwise, and aren't checked.

use crate::core::{
    CLASS_CONNECTED, CLASS_CONNECTED_LIST, FN_NO_BODY, MEMBER_CONNECTED, MEMBER_STATIC,
    ROLE_CONSTRUCTOR, ROLE_GETTER, ROLE_MASK, ROLE_METHOD, ROLE_PARAM, ROLE_SETTER, class_parts,
    fn_parts, let_parts,
};
use crate::library::{Level, Message};
use crate::settle::Settle;
use temper_syntax::tree::{NodeId, NodeKind};

impl Settle {
    pub(super) fn check_connected_class(&mut self, class: NodeId) {
        let tree = &self.lib.tree;
        let data = tree.data(class);
        if data & CLASS_CONNECTED == 0 || self.lib.in_std(tree.span(class).start) {
            return;
        }
        let members = class_parts(tree, class).2.to_vec();
        // Core's connected classes connect members too, as
        // `@connected public complete(…)`, which a user's can't, and
        // core's files, read as a user library, shouldn't be checked.
        if members
            .iter()
            .any(|&m| tree.data(m) & MEMBER_CONNECTED != 0)
        {
            return;
        }
        let mut problems = Vec::new();
        // Defaults first, as Kotlin checks them first: a method's or an
        // explicit constructor's, or the header's, which the implied
        // constructor takes.
        let mut header_default = None;
        for &m in &members {
            let role = tree.data(m) & ROLE_MASK;
            let defaulted = match role {
                ROLE_PARAM => {
                    if let_parts(tree, m).2.is_some() && header_default.is_none() {
                        header_default = Some(m);
                    }
                    continue;
                }
                ROLE_METHOD | ROLE_GETTER | ROLE_SETTER | ROLE_CONSTRUCTOR => (let_parts(tree, m)
                    .2)
                    .filter(|&f| tree.kind(f) == NodeKind::Fn)
                    .is_some_and(|f| {
                        let params = fn_parts(tree, f).0;
                        params.iter().any(|&p| let_parts(tree, p).2.is_some())
                    }),
                _ => false,
            };
            if defaulted {
                let name = match role {
                    ROLE_CONSTRUCTOR => "constructor".to_string(),
                    _ => self.member_text(m).to_string(),
                };
                problems.push((Message::ConnectedMethodDefaults(name), m));
            }
        }
        if let Some(m) = header_default {
            problems.push((Message::ConnectedMethodDefaults("constructor".into()), m));
        }
        // Methods written in the class, other than constructors and static
        // functions: fields' implied accessors are no Temper implementation.
        let written = |m: NodeId| {
            matches!(
                tree.data(m) & ROLE_MASK,
                ROLE_METHOD | ROLE_GETTER | ROLE_SETTER
            ) && tree.data(m) & MEMBER_STATIC == 0
        };
        let bodiless = |m: NodeId| {
            let_parts(tree, m)
                .2
                .is_none_or(|f| tree.kind(f) == NodeKind::Fn && tree.data(f) & FN_NO_BODY != 0)
        };
        let methods: Vec<NodeId> = members.iter().copied().filter(|&m| written(m)).collect();
        let missing: Vec<NodeId> = methods.iter().copied().filter(|&m| bodiless(m)).collect();
        let lists = data & CLASS_CONNECTED_LIST != 0;
        let partial = missing.len() < methods.len();
        if !missing.is_empty() && (lists || partial) {
            for m in missing {
                let name = self.member_text(m).to_string();
                let message = if lists {
                    Message::UserConnectedClassBackendsNeedBodies(name)
                } else {
                    Message::UserConnectedClassPartialBodies(name)
                };
                problems.push((message, m));
            }
        }
        for (message, m) in problems {
            let span = self.lib.tree.span(self.lib.tree.children(m)[0]);
            self.lib.report(Level::Error, message, span);
        }
    }
}
