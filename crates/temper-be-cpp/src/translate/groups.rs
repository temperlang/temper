//! Local functions that use each other or themselves, a group
//! (`temper_tmpl::analysis::function_groups`), since a lambda can't name
//! itself: a local struct of what the group captures, its functions its
//! `const` methods, which read the captures as members and call each
//! other as methods; a `const` instance of it; and each function the
//! code after it uses as a `std::function` calling its method.
//!
//! ```text
//! struct WalkerGroup {
//!   std::shared_ptr<std::vector<int32_t>> x;
//!   void walker(int32_t i, std::shared_ptr<std::vector<int32_t>> out) const { … putter(i, out); }
//!   void putter(…) const { … }
//! };
//! WalkerGroup const walker_group = WalkerGroup{x};
//! std::function<void(int32_t, …)> walker = [=](int32_t a0, …) -> void { walker_group.walker(a0, a1); };
//! ```
//!
//! Safer than Kotlin's, which captures the `std::function`s by reference,
//! so one that outlives its function calls a dead one: a lambda in a
//! method copies what it uses of the group, `[=, x = x, walker_group =
//! *this]`, never `this`, and calls its functions on that copy. Captured
//! cells are `shared_ptr`s, which copies share.

use super::*;
use crate::tree::GroupMethod;
use temper_tmpl::analysis::{FunctionGroup, walk};

/// The group whose method is being translated: its functions, what it
/// captures, its instance's name, and whether the code is in the method
/// itself, not a lambda in it.
#[derive(Clone, Debug)]
pub(crate) struct GroupHere {
    pub members: Vec<DeclId>,
    pub captures: Vec<DeclId>,
    pub var: String,
    pub top: bool,
}

impl Translator<'_> {
    /// A group's struct, its instance, and the `std::function`s of its
    /// functions that `rest`, the statements after it, use.
    pub(crate) fn group(
        &mut self,
        group: &FunctionGroup,
        rest: &[NodeId],
        out: &mut Vec<Stmt>,
    ) -> R<()> {
        let p = self.p;
        for &m in &group.members {
            let sig = temper_tmpl::analysis::fun_signature(p, p.decls.get(m).ty);
            let fail = sig.and_then(|s| p.types.signature(s)).and_then(|s| s.fail);
            if fail.is_some_and(|f| f != TypeId::BUBBLE) {
                return not_yet("local functions that use each other and declare failures");
            }
        }
        let first = self.name(group.members[0]);
        let mut chars = first.chars();
        let pascal: String = (chars.next().map(|c| c.to_ascii_uppercase()))
            .into_iter()
            .chain(chars)
            .collect();
        let name = self.free_name(&format!("{pascal}Group"));
        let var = self.free_name(&format!("{first}_group"));
        let mut fields = Vec::new();
        for &c in &group.captures {
            let ty = self.ty(p.decls.get(c).ty)?;
            let ty = match self.is_cell(c) {
                true => format!("std::shared_ptr<{ty}>"),
                false => ty,
            };
            fields.push((ty, self.name(c)));
        }
        let saved = self.group.replace(GroupHere {
            members: group.members.clone(),
            captures: group.captures.clone(),
            var: var.clone(),
            top: true,
        });
        let methods = (|| -> R<Vec<GroupMethod>> {
            let mut methods = Vec::new();
            for &l in &group.lets {
                let Node::Let {
                    local,
                    init: Some(init),
                } = p.nodes.get(l)
                else {
                    unreachable!()
                };
                let Node::FunctionLiteral { decl, params, body } = p.nodes.get(init) else {
                    unreachable!()
                };
                if functions::uses_this(p, body) {
                    return not_yet("`this` in a group of local functions");
                }
                if !p.decls.type_params(decl).is_empty() {
                    return not_yet("a generic local function in a group");
                }
                let (params, result, body) = self.literal_parts(decl, params, body)?;
                methods.push(GroupMethod {
                    result,
                    name: self.name(local),
                    params,
                    body,
                });
            }
            Ok(methods)
        })();
        self.group = saved;
        let methods = methods?;
        out.append(&mut self.pending);
        let inits: Vec<String> = fields.iter().map(|(_, n)| n.clone()).collect();
        out.push(Stmt::GroupStruct {
            name: name.clone(),
            fields,
            methods,
        });
        // The functions the code after it uses, as `std::function`s.
        let mut used = Vec::new();
        for &s in rest.iter().filter(|s| !group.lets.contains(s)) {
            walk(p, s, &mut |id| {
                if let Node::Local { local } = p.nodes.get(id) {
                    if group.members.contains(&local) && !used.contains(&local) {
                        used.push(local);
                    }
                }
            });
        }
        if used.is_empty() {
            return Ok(());
        }
        used.sort_by_key(|m| group.members.iter().position(|x| x == m));
        out.push(Stmt::Local {
            ty: format!("{name} const"),
            name: var.clone(),
            value: Some(Expr::name(format!("{name}{{{}}}", inits.join(", ")))),
        });
        for m in used {
            let ty = self.function_type(p.decls.get(m).ty)?;
            let value = self.member_lambda(m, &var, "=")?;
            out.push(Stmt::Local {
                ty,
                name: self.name(m),
                value: Some(value),
            });
        }
        Ok(())
    }

    /// A group's function as a value: a lambda calling its method on
    /// `var`, `[=](int32_t a0) -> int32_t { return group.f(a0); }`.
    pub(crate) fn member_lambda(&mut self, member: DeclId, var: &str, captures: &str) -> R<Expr> {
        let p = self.p;
        let sig = temper_tmpl::analysis::fun_signature(p, p.decls.get(member).ty);
        let Some(sig) = sig.and_then(|s| p.types.signature(s)) else {
            return not_yet("a local function without a signature");
        };
        let mut params = Vec::new();
        let mut args = Vec::new();
        for (i, &t) in sig.params.iter().enumerate() {
            params.push((self.ty(t)?, format!("a{i}")));
            args.push(Expr::name(format!("a{i}")));
        }
        let call = Expr::name(format!("{var}.{}", self.name(member))).call(args);
        let body = match sig.pass == TypeId::VOID {
            true => Stmt::Expr(call),
            false => Stmt::Return(Some(call)),
        };
        Ok(Expr::Lambda {
            captures: captures.to_string(),
            params,
            result: self.ty(sig.pass)?,
            body: vec![body],
        })
    }

    /// A group's function, read where the code is in a method of its
    /// group: a call's callee, `walker_group.walker` in a lambda there;
    /// else `None`.
    pub(crate) fn group_callee(&self, local: DeclId) -> Option<Expr> {
        let g = self.group.as_ref()?;
        if !g.members.contains(&local) {
            return None;
        }
        Some(match g.top {
            true => Expr::name(self.name(local)),
            false => Expr::name(format!("{}.{}", g.var, self.name(local))),
        })
    }

    /// A group's function as a value where the code is in a method of its
    /// group, else `None`.
    pub(crate) fn group_value(&mut self, local: DeclId) -> R<Option<Expr>> {
        let Some(g) = self.group.clone() else {
            return Ok(None);
        };
        if !g.members.contains(&local) {
            return Ok(None);
        }
        let captures = match g.top {
            true => format!("{} = *this", g.var),
            false => "=".to_string(),
        };
        self.member_lambda(local, &g.var, &captures).map(Some)
    }

    /// `base`, or `base2`, `base3`, …, whichever no declaration is named.
    fn free_name(&self, base: &str) -> String {
        let p = self.p;
        let taken = |n: &str| p.decls.ids().any(|d| self.names.get(d) == n);
        (1..)
            .map(|i| match i {
                1 => base.to_string(),
                i => format!("{base}{i}"),
            })
            .find(|n| !taken(n))
            .unwrap()
    }
}
