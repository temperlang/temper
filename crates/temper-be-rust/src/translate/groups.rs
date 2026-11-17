//! Local functions that use each other or themselves, a group
//! (`temper_tmpl::analysis::function_groups`), as Kotlin's Rust backend
//! writes them, since a closure can't name itself: a struct of what the
//! group captures, `Clone`, its functions the methods of its `impl`, and
//! an instance of it; each function the code after it uses is then a
//! local closure calling its method, as other function values are.
//!
//! ```text
//! #[derive(Clone)]
//! struct WalkerGroup {
//!     x: temper_core::List<i32>,
//! }
//! impl WalkerGroup {
//!     fn walker(&self, i: i32, out: temper_core::ListBuilder<i32>) {
//!         let x = self.x.clone();
//!         if … { self.putter(i, out.clone()); }
//!     }
//!     fn putter(…) { … }
//! }
//! let walker_group = WalkerGroup { x: x.clone() };
//! let walker = Arc::new(move |i: i32, out: …| walker_group.walker(i, out));
//! ```
//!
//! A method starts with copies of the captures it uses, so its body is
//! translated as a closure's; it calls the group's functions as methods,
//! and a closure in it that uses one gets a closure of its own calling
//! it, from a clone of the group. Readabler than Kotlin's
//! (`ClosureGroup___0`, every literal a group): only groups are structs.

use super::{R, Stmt, Translator, not_yet};
use crate::tree::{Block, Expr, Item, Member};
use std::collections::HashMap;
use temper_tmpl::analysis::{FunctionGroup, walk};
use temper_tmpl::{DeclId, Node, NodeId, TypeData, TypeId};

/// The group whose method is being translated: its functions, and how
/// the code there reaches the group (`self`).
#[derive(Clone, Debug)]
pub(crate) struct GroupHere {
    pub members: Vec<DeclId>,
    pub access: String,
}

impl Translator<'_> {
    /// The groups of a block's statements, by their first `let`s.
    pub(crate) fn groups_of(&self, stmts: &[NodeId]) -> HashMap<NodeId, FunctionGroup> {
        temper_tmpl::analysis::function_groups(self.p, stmts)
            .into_iter()
            .map(|g| (g.lets[0], g))
            .collect()
    }

    /// A group's struct, `impl`, instance, and the closures of its
    /// functions that `rest`, the statements after it, use.
    pub(crate) fn group(
        &mut self,
        group: &FunctionGroup,
        rest: &[NodeId],
        out: &mut Vec<Stmt>,
    ) -> R<()> {
        let p = self.p;
        let first = self.name(group.members[0]);
        let first = first.trim_start_matches("r#").trim_start_matches('_');
        let pascal: String = first
            .split('_')
            .filter(|s| !s.is_empty())
            .map(|s| {
                let mut c = s.chars();
                c.next()
                    .map(|f| f.to_uppercase().chain(c).collect::<String>())
                    .unwrap_or_default()
            })
            .collect();
        let name = self.free_name(&format!("{pascal}Group"));
        let var = self.free_name(&format!("{first}_group"));
        // Its fields: what its functions capture, cells as cells.
        let mut fields = Vec::new();
        let mut inits = Vec::new();
        for &c in &group.captures {
            let ty = p.decls.get(c).ty;
            if self.mentions_type_param(ty) {
                return not_yet("a group of local functions capturing a generic value");
            }
            let field = self.name(c);
            let text = match self.is_cell(c) {
                true => self.cell_type(ty)?,
                false => self.ty(ty)?,
            };
            fields.push(("", field.clone(), text));
            let value = match self.is_cell(c) || !self.is_copy(ty) {
                true => Some(Expr::path(&field).method("clone", Vec::new())),
                false => None,
            };
            inits.push((field, value));
        }
        // Its methods.
        let saved = self.group.replace(GroupHere {
            members: group.members.clone(),
            access: "self".into(),
        });
        let methods = (|| -> R<Vec<Member>> {
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
                if self.uses_this(body) {
                    return not_yet("`this` in a group of local functions");
                }
                let sig = p.signature(decl);
                if sig.is_none_or(|s| {
                    s.params
                        .iter()
                        .chain([&s.result])
                        .any(|&t| self.mentions_type_param(t))
                }) {
                    return not_yet("a generic group of local functions");
                }
                let (ps, result, block) = self.closure_parts(init, decl, params, body)?;
                // Copies of what it captures, as a closure's.
                let mut stmts = Vec::new();
                for &c in p.decls.captures(decl) {
                    if group.members.contains(&c) {
                        continue;
                    }
                    let field = self.name(c);
                    let read = Expr::path(format!("self.{field}"));
                    let ty = p.decls.get(c).ty;
                    let value = match self.is_cell(c) || !self.is_copy(ty) {
                        true => read.method("clone", Vec::new()),
                        false => read,
                    };
                    stmts.push(Stmt::Let {
                        name: field,
                        mutable: false,
                        ty: None,
                        value: Some(value),
                    });
                }
                stmts.extend(block.stmts);
                let mut params = vec!["&self".to_string()];
                params.extend(ps);
                methods.push(Member::Fn {
                    vis: "",
                    name: self.name(local),
                    params,
                    result,
                    body: Some(Block {
                        stmts,
                        tail: block.tail,
                    }),
                });
            }
            Ok(methods)
        })();
        self.group = saved;
        let methods = methods?;
        out.push(Stmt::Item(Box::new(Item::Struct {
            attrs: vec!["#[derive(Clone)]".into()],
            vis: "",
            name: name.clone(),
            fields,
        })));
        out.push(Stmt::Item(Box::new(Item::Impl {
            head: name.clone(),
            items: methods,
        })));
        // The functions the code after it uses, as closures.
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
        used.sort_by_key(|m| group.members.iter().position(|x| x == m));
        let instance = Expr::Struct(name, inits);
        if used.is_empty() {
            return Ok(());
        }
        out.push(Stmt::Let {
            name: var.clone(),
            mutable: false,
            ty: None,
            value: Some(instance),
        });
        for (i, &m) in used.iter().enumerate() {
            // The last takes the instance; the others clone it.
            let value = self.member_closure(m, &var, i + 1 < used.len())?;
            out.push(Stmt::Let {
                name: self.name(m),
                mutable: false,
                ty: None,
                value: Some(value),
            });
        }
        Ok(())
    }

    /// A group's function as a value: a closure calling its method on
    /// `access`, or on a clone of it, `{ let group = self.clone();
    /// Arc::new(move |i: i32| group.f(i)) }`.
    pub(crate) fn member_closure(&mut self, member: DeclId, access: &str, clone: bool) -> R<Expr> {
        let p = self.p;
        let sig = self.fun_sig(p.decls.get(member).ty);
        let Some(sig) = sig.and_then(|s| p.types.signature(s)) else {
            return not_yet("a local function without a signature");
        };
        let mut ps = Vec::new();
        let mut args = Vec::new();
        for (i, &t) in sig.params.iter().enumerate() {
            let a = format!("a{i}");
            ps.push(format!("{a}: {}", self.ty(t)?));
            args.push(Expr::path(a));
        }
        let holder = match clone {
            true => "group",
            false => access,
        };
        let call = Expr::path(holder).method(self.name(member), args);
        let closure = Expr::Move(Box::new(Expr::Closure(ps, Block::of(call))));
        let closure = self.arc(closure);
        Ok(match clone {
            false => closure,
            true => Expr::Block(Block::new(
                vec![Stmt::Let {
                    name: "group".into(),
                    mutable: false,
                    ty: None,
                    value: Some(Expr::path(access).method("clone", Vec::new())),
                }],
                Some(closure),
            )),
        })
    }

    /// Whether `local` is a function of the group whose method this is.
    pub(crate) fn group_member(&self, local: DeclId) -> bool {
        self.group
            .as_ref()
            .is_some_and(|g| g.members.contains(&local))
    }

    /// `base`, or `base2`, `base3`, …, whichever no declaration is named,
    /// for what the translation adds (a group's struct, a held value).
    pub(crate) fn free_name(&self, base: &str) -> String {
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

    /// Whether a type is or has a type parameter.
    fn mentions_type_param(&self, ty: TypeId) -> bool {
        let p = self.p;
        match p.types.get(ty) {
            TypeData::Never(t) => self.mentions_type_param(t),
            TypeData::TypeParam { .. } => true,
            TypeData::Nullable(t) => self.mentions_type_param(t),
            TypeData::Nominal { args, .. } => {
                (p.types.list(args).iter()).any(|&t| self.mentions_type_param(t))
            }
            TypeData::Function { params, result, .. } => (p.types.list(params).iter())
                .chain([&result])
                .any(|&t| self.mentions_type_param(t)),
            TypeData::Scalar(_) => false,
        }
    }
}
