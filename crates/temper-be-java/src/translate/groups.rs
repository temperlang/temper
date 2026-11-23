//! Local functions that use each other or themselves, a group
//! (`temper_tmpl::analysis::function_groups`), as Kotlin's Java backend
//! writes recursive ones, since a lambda can't name itself or a local
//! declared after it: a local class, its methods the group's functions,
//! which read what they capture as lambdas do and call each other as
//! methods; an instance of it; and each function the code after it uses
//! as a method reference.
//!
//! ```text
//! class WalkerGroup {
//!     void walker(int i, List<Integer> out) { if (…) { putter(i, out); } }
//!     void putter(int i2, List<Integer> out2) { …; walker(i2 + 1, out2); }
//! }
//! WalkerGroup walkerGroup = new WalkerGroup();
//! Int32ListBuilderProcedure walker = walkerGroup::walker;
//! ```
//!
//! A function of the group as a value in its methods is a method
//! reference, `WalkerGroup.this::walker`.

use super::{R, Translator, not_yet};
use crate::tree::{Expr, Method, Stmt};
use temper_tmpl::analysis::{FunctionGroup, walk};
use temper_tmpl::{DeclId, Node, NodeId, TypeId};

impl Translator<'_> {
    /// A group's class, its instance, and the method references of its
    /// functions that `rest`, the statements after it, use.
    pub(crate) fn group(
        &mut self,
        group: &FunctionGroup,
        rest: &[NodeId],
        out: &mut Vec<Stmt>,
    ) -> R<()> {
        let p = self.p;
        let first = self.name(group.members[0]);
        let mut chars = first.chars();
        let pascal: String = (chars.next().map(|c| c.to_ascii_uppercase()))
            .into_iter()
            .chain(chars)
            .collect();
        let name = self.free_name(&format!("{pascal}Group"));
        let var = self.free_name(&format!("{first}Group"));
        self.groups.push((name.clone(), group.members.clone()));
        let methods = (|| -> R<Vec<Method>> {
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
                let Some(sig) = p.signature(decl) else {
                    return not_yet("a local function without a signature");
                };
                let result = sig.pass;
                let mut ps = Vec::new();
                for &param in p.nodes.list(params) {
                    if let Node::Param { decl } = p.nodes.get(param) {
                        ps.push((self.ty(p.decls.get(decl).ty)?, self.name(decl)));
                    }
                }
                let saved = (
                    std::mem::take(&mut self.loops),
                    std::mem::take(&mut self.labels),
                );
                let stmts = self.stmts(body);
                (self.loops, self.labels) = saved;
                let mut stmts = stmts?;
                if result == TypeId::VOID && stmts.last() == Some(&Stmt::Return(None)) {
                    stmts.pop();
                }
                methods.push(Method {
                    public: false,
                    result: self.ty(result)?,
                    name: self.name(local),
                    params: ps,
                    throws: Vec::new(),
                    body: stmts,
                });
            }
            Ok(methods)
        })();
        self.groups.pop();
        out.append(&mut self.pre);
        out.push(Stmt::GroupClass {
            name: name.clone(),
            methods: methods?,
        });
        // The functions the code after it uses, as method references.
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
            ty: name.clone(),
            name: var.clone(),
            value: Some(Expr::New(name, Vec::new())),
        });
        for m in used {
            let sam = self.sam(p.decls.get(m).ty)?;
            out.push(Stmt::Local {
                ty: sam.ty,
                name: self.name(m),
                value: Some(Expr::MethodRef(Box::new(Expr::name(&var)), self.name(m))),
            });
        }
        Ok(())
    }

    /// The class of the group, around the code being translated, that
    /// `local` is a function of.
    pub(crate) fn group_of(&self, local: DeclId) -> Option<String> {
        (self.groups.iter().rev())
            .find(|(_, members)| members.contains(&local))
            .map(|(name, _)| name.clone())
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
