//! Classes and interfaces, as Kotlin's Rust backend writes them.
//!
//! A class `Rect` is a cloneable handle, `pub struct Rect(Arc<RectStruct>)`,
//! over its fields, with its constructor `Rect::new`, its methods, getters
//! (`area()`), and setters (`set_doubled(…)`) as methods, a getter for each
//! public field of an exported class (`w()`, and `set_count(…)` for a
//! `var`), its statics as getters (`Rect::unit()`) over `OnceLock`s
//! (`RECT__UNIT`), and its static functions as associated functions. An
//! exported class whose public constructor takes two or more arguments
//! also gets a builder, `builders::RectBuilder { w, h }.build()`. An
//! interface `Shape` is a trait, `ShapeTrait`, with its default methods,
//! and a handle on a trait object, `pub struct Shape(Arc<dyn ShapeTrait>)`,
//! which also implements the trait and the traits above it; `Shape::new`
//! makes one of a class's instance (an upcast). Each class implements the
//! traits of every interface above it, a method another interface
//! overrides (a diamond's) forwarding to the override that Kotlin picks:
//! the first found breadth-first. `temper_core`'s `impl_any_value_trait!`
//! macros give `is` and `as` (`temper_core::is::<Square>(q)`).
//!
//! Where this differs from Kotlin's: a class's plain `toString()` also
//! makes it `Display`, and its `@operator("==")` method `PartialEq`, so
//! `==` compares with it (but for a side narrowed from a nullable one, a
//! reference, where generated code calls the method, as Kotlin's); only `var` fields are locked (`RwLock`), each
//! on its own, and read through a method, so no lock is held across a
//! call (a field read in an expression that also calls something, as `n +
//! peer.bump(this) + n`, can't deadlock); a non-exported class gets no
//! getters for its fields, since its module reads them directly; a
//! method an interface gives a class is called by its trait
//! (`CTrait::a(&d)`), where Kotlin also gives the class a private method
//! that does; the constructor builds its instance in one expression where
//! it can; and what Kotlin always writes but a type that isn't exported
//! may not use (`new`, `clone_boxed`) is `#[allow(dead_code)]`, where
//! Kotlin's output allows every warning.

use super::{Params, R, Translator, Want, not_yet};
use crate::names::static_name;
use crate::tree::{Bin, Block, Expr, Item, Member, Stmt, Unary};
use std::collections::{HashMap, HashSet};
use temper_tmpl::analysis::{ancestors, home, is_plain_to_string, nominal_decl, walk};
use temper_tmpl::naming::snake_case;
use temper_tmpl::qname::{is_external, library_of};
use temper_tmpl::{DeclId, DeclKind, Facts, Kids, Module, Node, NodeId, Program, TypeId};

/// What `this` is in the code being translated.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) enum This {
    /// Not in a method.
    #[default]
    None,
    /// A class's method: `self`, a `&Class`.
    Class,
    /// An interface's default method: `self`, a `&Self` of its trait.
    Trait(DeclId),
    /// A constructor after its instance is made: a local of that name.
    Local(String),
}

/// Every class and interface node of the program, by declaration.
pub(crate) fn index(p: &Program) -> HashMap<DeclId, NodeId> {
    let mut out = HashMap::new();
    for module in &p.modules {
        for item in module.all_items() {
            if let Node::Class { decl, .. } | Node::Interface { decl, .. } = p.nodes.get(item) {
                out.insert(decl, item);
            }
        }
    }
    out
}

/// The visibility of what a type that isn't exported may not use, as
/// [`Translator::plan_accessors`] says.
const UNUSED: &str = "#[allow(dead_code)]\n";
const UNUSED_PUB: &str = "#[allow(dead_code)]\npub";

/// A member's kind for overriding: a getter, a setter, or a method.
fn slot(kind: DeclKind) -> DeclKind {
    match kind {
        DeclKind::Field => DeclKind::Getter,
        k => k,
    }
}

impl<'a> Translator<'a> {
    fn kind(&self, decl: DeclId) -> DeclKind {
        self.p.decls.get(decl).kind
    }

    fn facts(&self, decl: DeclId) -> Facts {
        self.p.decls.get(decl).facts
    }

    /// Whether a declaration is the program's own, not another library's
    /// or core's.
    pub(crate) fn own(&self, decl: DeclId) -> bool {
        !is_external(self.p, decl)
    }

    /// A class or interface member's Rust name: a setter's is `set_` and
    /// its property's.
    pub(crate) fn member_name(&self, decl: DeclId) -> String {
        let name = self.name(decl);
        match self.kind(decl) {
            DeclKind::Setter => format!("set_{}", name.trim_start_matches("r#")),
            _ => name,
        }
    }

    /// A class's or interface's member nodes.
    fn member_nodes(&self, class: DeclId) -> &'a [NodeId] {
        let p = self.p;
        match self.classes.get(&class).map(|&n| p.nodes.get(n)) {
            Some(Node::Class { members, .. } | Node::Interface { members, .. }) => {
                p.nodes.list(members)
            }
            _ => &[],
        }
    }

    /// A member node's declaration, and whether it has a body.
    fn member_decl(&self, member: NodeId) -> Option<(DeclId, bool)> {
        match self.p.nodes.get(member) {
            Node::Field { decl } | Node::StaticField { decl } => Some((decl, true)),
            Node::Method { decl, .. } | Node::Function { decl, .. } => Some((decl, true)),
            Node::AbstractMethod { decl, .. } => Some((decl, false)),
            _ => None,
        }
    }

    /// The instance member of `class` that fills the slot of `member`
    /// (a method, getter, or setter, by its source name): the class's own
    /// method, or a field for a getter, or a `var` field for a setter.
    fn provider(&self, class: DeclId, member: DeclId) -> Option<DeclId> {
        let name = self.p.name(member);
        let kind = self.kind(member);
        self.member_nodes(class).iter().find_map(|&m| {
            let (d, body) = self.member_decl(m)?;
            let dk = self.kind(d);
            let fits = match dk {
                DeclKind::Field => {
                    kind == DeclKind::Getter
                        || (kind == DeclKind::Setter && self.facts(d).contains(Facts::VAR))
                }
                DeclKind::Method | DeclKind::Getter | DeclKind::Setter => dk == kind,
                _ => false,
            };
            (fits && body && self.p.name(d) == name).then_some(d)
        })
    }

    /// The default body that fills `member`'s slot in a class: the first
    /// interface above it, breadth-first, as Kotlin picks, with a method
    /// of the slot's name and kind that has a body.
    fn winner(&self, class: DeclId, member: DeclId) -> Option<DeclId> {
        let name = self.p.name(member);
        let kind = slot(self.kind(member));
        ancestors(self.p, class).into_iter().find(|&i| {
            self.member_nodes(i).iter().any(|&m| {
                matches!(self.p.nodes.get(m), Node::Method { decl, .. }
                    if self.kind(decl) == kind && self.p.name(decl) == name)
            })
        })
    }

    /// A class or interface's own instance members, by Rust name.
    fn instance_names(&self, class: DeclId) -> Vec<String> {
        self.member_nodes(class)
            .iter()
            .filter_map(|&m| match self.p.nodes.get(m) {
                Node::Method { decl, .. } | Node::AbstractMethod { decl, .. }
                    if self.kind(decl) != DeclKind::Constructor =>
                {
                    Some(self.member_name(decl))
                }
                _ => None,
            })
            .collect()
    }

    /// A type's trait: `ShapeTrait`, by its path from here.
    fn trait_path(&self, iface: DeclId) -> R<String> {
        Ok(format!("{}Trait", self.top_path(iface)?))
    }

    /// The type a class's statics are named for: `RECT` for `Rect`.
    fn static_of(&self, decl: DeclId) -> String {
        let owner = self.p.decls.get(decl).owner;
        format!(
            "{}__{}",
            static_name(&snake_case(self.p.name(owner))),
            static_name(&self.name(decl))
        )
    }

    /// Plans which fields' getters and setters a module needs: those of
    /// the public fields of an exported class, or of any class where the
    /// library has host code, which may call them (`hidden.i()`), and a `var`'s getter
    /// wherever it's read, which reads it without holding its lock past
    /// the read. And which classes it constructs, which interfaces'
    /// handles it makes, and which interfaces' default methods use
    /// `clone_boxed`: Kotlin's output always has `new` and `clone_boxed`,
    /// which in a type that isn't exported may go unused, and so are
    /// marked `#[allow(dead_code)]`.
    pub(crate) fn plan_accessors(&mut self, module: &Module) {
        let p = self.p;
        let roots: Vec<NodeId> = module.roots().collect();
        // Upcasts that are receivers, which calls leave out.
        let mut receivers = HashSet::new();
        for &root in &roots {
            walk(p, root, &mut |id| {
                if let Node::CallMethod { receiver, .. } = p.nodes.get(id) {
                    let mut r = receiver;
                    while let Node::Upcast { value } = p.nodes.get(r) {
                        receivers.insert(r);
                        r = value;
                    }
                }
            });
        }
        for &root in &roots {
            walk(p, root, &mut |id| match p.nodes.get(id) {
                Node::GetField { field, .. } if self.facts(field).contains(Facts::VAR) => {
                    self.getters.insert(field);
                }
                Node::New { ctor, .. } => {
                    self.constructed.insert(p.decls.get(ctor).owner);
                }
                Node::Upcast { .. } if !receivers.contains(&id) => {
                    if let Some(to) = nominal_decl(p, p.nodes.ty(id)) {
                        self.wrapped.insert(to);
                    }
                }
                _ => {}
            });
        }
        // Interfaces whose default methods need `this` as a value, by
        // `clone_boxed`.
        for item in module.all_items() {
            let Node::Interface { decl: iface, .. } = p.nodes.get(item) else {
                continue;
            };
            let mut thises = Vec::new();
            let mut receiving = HashSet::new();
            walk(p, item, &mut |id| match p.nodes.get(id) {
                Node::This {} => thises.push(id),
                Node::CallMethod { receiver, .. } => {
                    receiving.insert(receiver);
                }
                _ => {}
            });
            if thises.iter().any(|t| !receiving.contains(t)) {
                self.boxed.insert(iface);
            }
        }
        let host = !temper_tmpl::host::files(p, &[".rs"]).is_empty();
        for item in module.all_items() {
            let Node::Class { decl: class, .. } = p.nodes.get(item) else {
                continue;
            };
            let reachable = host || self.facts(class).contains(Facts::EXPORTED);
            for iface in ancestors(p, class) {
                self.wrapped.insert(iface);
            }
            for &m in self.member_nodes(class) {
                let Node::Field { decl } = p.nodes.get(m) else {
                    continue;
                };
                let public = !self.is_private(decl);
                if reachable && public {
                    self.getters.insert(decl);
                    if self.facts(decl).contains(Facts::VAR) {
                        self.setters.insert(decl);
                    }
                }
            }
            // Fields that implement an interface's getters and setters.
            for iface in ancestors(p, class) {
                for &m in self.member_nodes(iface) {
                    let Some((d, _)) = self.member_decl(m) else {
                        continue;
                    };
                    if let Some(f) = self.provider(class, d) {
                        if self.kind(f) == DeclKind::Field {
                            match self.kind(d) {
                                DeclKind::Getter => self.getters.insert(f),
                                _ => self.setters.insert(f),
                            };
                        }
                    }
                }
            }
        }
    }

    /// The name of a field's accessor function: its getter's, or with
    /// `set`, its setter's (`set_` and its name). A public field's are
    /// its API, so its own names; a private field's are this backend's,
    /// so they take a numbered suffix where a member of the class has the
    /// name (`passing2()` reading the field `passing` beside the getter
    /// `passing()`), as fields are apart from members (`fields_apart`).
    pub(crate) fn accessor(&self, field: DeclId, set: bool) -> String {
        let name = self.name(field);
        let want = match set {
            true => format!("set_{}", name.trim_start_matches("r#")),
            false => name,
        };
        if !self.is_private(field) {
            return want;
        }
        let class = self.p.decls.get(field).owner;
        let taken: HashSet<String> = (self.member_nodes(class).iter())
            .filter_map(|&m| self.member_decl(m))
            .filter(|&(d, _)| {
                d != field && (self.kind(d) != DeclKind::Field || !self.is_private(d))
            })
            .flat_map(|(d, _)| match self.kind(d) {
                DeclKind::Field => {
                    let n = self.name(d);
                    vec![format!("set_{}", n.trim_start_matches("r#")), n]
                }
                _ => vec![self.member_name(d)],
            })
            .collect();
        let sep = if want.ends_with(|c: char| c.is_ascii_digit()) {
            "_"
        } else {
            ""
        };
        std::iter::once(want.clone())
            .chain((2..).map(|n| format!("{want}{sep}{n}")))
            .find(|n| !taken.contains(n))
            .unwrap_or(want)
    }

    fn is_private(&self, decl: DeclId) -> bool {
        let f = self.facts(decl);
        f.contains(Facts::PRIVATE) || f.contains(Facts::PROTECTED)
    }

    fn member_vis(&self, decl: DeclId) -> &'static str {
        if self.is_private(decl) { "" } else { "pub" }
    }

    /// A trait method's parameters and result, with concrete types:
    /// its parameters written out (`&self` first), their names, and its
    /// result.
    fn trait_sig(
        &mut self,
        decl: DeclId,
        params: Kids,
    ) -> R<(Vec<String>, Vec<String>, Option<String>)> {
        let p = self.p;
        let Some(sig) = p.signature(decl) else {
            return not_yet("a method without a signature");
        };
        if !p.decls.type_params(decl).is_empty() {
            return not_yet("a generic method of an interface");
        }
        let mut ps = vec!["&self".to_string()];
        let mut names = Vec::new();
        for &param in p.nodes.list(params) {
            let Node::Param { decl: d } = p.nodes.get(param) else {
                continue;
            };
            let name = self.name(d);
            ps.push(format!("{name}: {}", self.ty(p.decls.get(d).ty)?));
            names.push(name);
        }
        let result = self.result(sig.pass, sig.fail)?;
        Ok((ps, names, result))
    }

    /// A result type, a `Result` if it may fail ([`Self::result_of`]).
    fn result(&mut self, ty: TypeId, fail: Option<TypeId>) -> R<Option<String>> {
        let result = if ty == TypeId::VOID {
            None
        } else {
            Some(self.ty(ty)?)
        };
        match fail {
            Some(fail) => Ok(Some(self.result_of(result, fail)?)),
            None => Ok(result),
        }
    }

    /// A member's trait signature, from its node.
    fn member_sig(&mut self, member: NodeId) -> R<(Vec<String>, Vec<String>, Option<String>)> {
        match self.p.nodes.get(member) {
            Node::Method { decl, params, .. } | Node::AbstractMethod { decl, params } => {
                self.trait_sig(decl, params)
            }
            _ => not_yet("a trait member that isn't a method"),
        }
    }

    /// A method's member of an `impl`: its body translated with `this`
    /// as given.
    fn method_member(&mut self, item: NodeId, this: This, style: Params) -> R<Member> {
        let (Node::Method { decl, params, body } | Node::Function { decl, params, body }) =
            self.p.nodes.get(item)
        else {
            return not_yet("a method that isn't one");
        };
        let (generics, _) = self.type_params(decl)?;
        if style == Params::Trait && !generics.is_empty() {
            return not_yet("a generic method of an interface");
        }
        let outer = std::mem::replace(&mut self.this, this);
        let made = self.callable(item, decl, params, body, style);
        self.this = outer;
        let (params, result, body) = made?;
        let vis = if style == Params::Trait {
            ""
        } else {
            self.allow_unused_type_params(decl, self.member_vis(decl))
        };
        Ok(Member::Fn {
            vis,
            name: format!("{}{generics}", self.member_name(decl)),
            params,
            result,
            body: Some(body),
        })
    }

    /// A static field's `OnceLock`, and its getter.
    fn static_field(&mut self, decl: DeclId) -> R<(Item, Member)> {
        if self.facts(decl).contains(Facts::VAR) {
            return not_yet("a static `var`");
        }
        let ty = self.ty(self.p.decls.get(decl).ty)?;
        self.uses.insert("std::sync::OnceLock");
        let stat = self.static_of(decl);
        let get = self.initialized(
            Block::of(Expr::path(&stat).method("get", Vec::new())),
            false,
        );
        let read = if self.is_copy(self.p.decls.get(decl).ty) {
            Expr::unary(Unary::Deref, get)
        } else {
            get.method("clone", Vec::new())
        };
        Ok((
            Item::Static {
                vis: "",
                name: stat,
                ty: format!("OnceLock<{ty}>"),
                value: Expr::path("OnceLock::new").call(Vec::new()),
            },
            Member::Fn {
                vis: self.member_vis(decl),
                name: self.name(decl),
                params: Vec::new(),
                result: Some(ty),
                body: Some(Block::of(read)),
            },
        ))
    }

    /// A class: its fields' struct, its handle, its statics, its
    /// methods, its traits, and its builder, if it has one.
    pub(crate) fn class(&mut self, node: NodeId) -> R<Vec<Item>> {
        let p = self.p;
        let Node::Class { decl: class, .. } = p.nodes.get(node) else {
            return not_yet("a class that isn't one");
        };
        let name = self.name(class);
        let vis = self.vis(class);
        let (gdecl, guse) = self.type_params(class)?;
        let where_ = self.extra_bounds(class)?;
        let phantoms = self.phantoms(class)?;
        self.uses.insert("std::sync::Arc");
        let mut fields = Vec::new();
        let mut statics = Vec::new();
        let mut members = Vec::new();
        let mut accessors = Vec::new();
        let mut eq_hook = None;
        let mut display = false;
        let failure = self.is_failure(class);
        for &m in self.member_nodes(class) {
            // A std member this backend connects isn't declared, as
            // Kotlin's: its uses are the connection's.
            if temper_tmpl::qname::connected_member_node(p, m, crate::connected::connects_std) {
                continue;
            }
            match p.nodes.get(m) {
                Node::Field { decl } => {
                    let ty = p.decls.get(decl).ty;
                    let rust_ty = self.ty(ty)?;
                    let var = self.facts(decl).contains(Facts::VAR);
                    let field = self.name(decl);
                    let stored = if var {
                        self.uses.insert("std::sync::RwLock");
                        format!("RwLock<{rust_ty}>")
                    } else {
                        rust_ty.clone()
                    };
                    fields.push(("", field.clone(), stored));
                    let fvis = self.member_vis(decl);
                    if self.getters.contains(&decl) {
                        let place = Expr::path("self").field("0").field(&field);
                        let read = if var {
                            let guard = place
                                .method("read", Vec::new())
                                .method("unwrap", Vec::new());
                            if self.is_copy(ty) {
                                Expr::unary(Unary::Deref, guard)
                            } else {
                                guard.method("clone", Vec::new())
                            }
                        } else if self.is_copy(ty) {
                            place
                        } else {
                            place.method("clone", Vec::new())
                        };
                        accessors.push(Member::Fn {
                            vis: fvis,
                            name: self.accessor(decl, false),
                            params: vec!["&self".into()],
                            result: Some(rust_ty.clone()),
                            body: Some(Block::of(read)),
                        });
                    }
                    if self.setters.contains(&decl) {
                        let place = Expr::path("self").field("0").field(&field);
                        let write = Expr::unary(
                            Unary::Deref,
                            place
                                .method("write", Vec::new())
                                .method("unwrap", Vec::new()),
                        );
                        let mut stmts = Vec::new();
                        let param = if ty == TypeId::STRING {
                            self.uses.insert("temper_core::ToArcString");
                            stmts.push(Stmt::Let {
                                name: "value".into(),
                                mutable: false,
                                ty: None,
                                value: Some(
                                    Expr::path("value").method("to_arc_string", Vec::new()),
                                ),
                            });
                            "value: impl ToArcString".to_string()
                        } else {
                            format!("value: {rust_ty}")
                        };
                        stmts.push(Stmt::Assign(write, Expr::path("value")));
                        accessors.push(Member::Fn {
                            vis: fvis,
                            name: self.accessor(decl, true),
                            params: vec!["&self".into(), param],
                            result: None,
                            body: Some(Block::new(stmts, None)),
                        });
                    }
                }
                Node::StaticField { decl } => {
                    let (stat, getter) = self.static_field(decl)?;
                    statics.push(stat);
                    members.push(getter);
                }
                Node::Method { decl, .. } if self.kind(decl) == DeclKind::Constructor => {
                    members.push(self.constructor(class, m)?);
                    self.builder(class, decl)?;
                }
                Node::Method { decl, .. } => {
                    // A failure's `Display` is its message.
                    if is_plain_to_string(p, decl) && !self.bubbles_decl(decl) && !failure {
                        display = true;
                    }
                    let mut member = self.method_member(m, This::Class, Params::Method)?;
                    if self.is_eq_hook(class, decl) {
                        eq_hook = Some(decl);
                    }
                    // Kotlin's API has `eq` (std's regex classes', and an
                    // eq hook's, which is `PartialEq`'s too), so clippy's
                    // wish for the trait's name is allowed where it's
                    // public API.
                    if let Member::Fn { vis, name, .. } = &mut member {
                        let exported = self.facts(class).contains(Facts::EXPORTED);
                        if name == "eq" && *vis == "pub" && exported {
                            *vis = "#[allow(clippy::should_implement_trait)]\npub";
                        }
                    }
                    members.push(member);
                }
                Node::Function { .. } => {
                    members.push(self.method_member(m, This::None, Params::Free)?);
                }
                other => return not_yet(format!("a {} in a class", other.kind().name())),
            }
        }
        members.extend(accessors);
        fields.extend(phantoms.into_iter().map(|(f, t)| ("", f, t)));
        let mut items = vec![
            Item::Struct {
                attrs: Vec::new(),
                vis: "",
                name: format!("{name}Struct{gdecl}"),
                fields,
            },
            Item::TupleStruct {
                attrs: vec!["#[derive(Clone)]".into()],
                vis,
                name: format!("{name}{gdecl}"),
                types: vec![format!("Arc<{name}Struct{guse}>")],
            },
        ];
        items.extend(statics);
        items.push(Item::Impl {
            head: format!("{gdecl} {name}{guse}").trim_start().to_string(),
            items: members,
        });
        if display {
            items.push(Item::Impl {
                head: format!("{gdecl} std::fmt::Display for {name}{guse}")
                    .trim_start()
                    .to_string(),
                items: vec![Member::Fn {
                    vis: "",
                    name: "fmt".into(),
                    params: vec!["&self".into(), "f: &mut std::fmt::Formatter<'_>".into()],
                    result: Some("std::fmt::Result".into()),
                    body: Some(Block::of(Expr::path("f").method(
                        "write_str",
                        vec![Expr::unary(
                            Unary::Ref,
                            Expr::path("self").method("to_string", Vec::new()),
                        )],
                    ))),
                }],
            });
        }
        if let Some(eq) = eq_hook {
            let call = Expr::path(format!("{name}::{}", self.name(eq))).call(vec![
                Expr::path("self"),
                Expr::path("other").method("clone", Vec::new()),
            ]);
            items.push(Item::Impl {
                head: format!("{gdecl} PartialEq for {name}{guse}")
                    .trim_start()
                    .to_string(),
                items: vec![Member::Fn {
                    vis: "",
                    name: "eq".into(),
                    params: vec!["&self".into(), format!("other: &{name}{guse}")],
                    result: Some("bool".into()),
                    body: Some(Block::of(call)),
                }],
            });
        }
        // Failure types above it are enums, which take it by `From`, and
        // core's `Error`, which it is by `impl_failure!`, not traits.
        let ups = ancestors(p, class);
        let traits: Vec<DeclId> = ups
            .iter()
            .copied()
            .filter(|&i| !self.is_failure(i))
            .collect();
        for iface in traits {
            items.push(self.class_trait_impl(class, iface)?);
        }
        let root = self.failure_root;
        let mut targets = Vec::new();
        for iface in ups.into_iter().filter(|&i| Some(i) != root) {
            let (t, _, saved) = self.super_type(class, iface)?;
            self.subst = saved;
            targets.push(t);
        }
        if failure {
            items.push(self.impl_failure(class)?);
        }
        items.push(Item::Macro(Expr::Macro(
            "temper_core::impl_any_value_trait".into(),
            vec![
                Expr::path(format!("{name}{guse}")),
                Expr::path(format!("[{}]{where_}", targets.join(", "))),
            ],
        )));
        Ok(items)
    }

    /// Whether a class or interface is a declared failure type (RFC 164),
    /// core's `Error` among them.
    pub(crate) fn is_failure(&self, decl: DeclId) -> bool {
        temper_tmpl::failures::is_failure_decl(self.p, self.failure_root, decl)
    }

    /// A failure class's field holding its cause, a `temper_core::Cause`:
    /// `cause`, or with a `_` after it where a field of its own has that
    /// name; `None` for another class.
    fn cause_field(&self, class: DeclId) -> Option<String> {
        if !self.is_failure(class) {
            return None;
        }
        let fields: Vec<String> = (self.member_nodes(class).iter())
            .filter_map(|&m| match self.p.nodes.get(m) {
                Node::Field { decl } => Some(self.name(decl)),
                _ => None,
            })
            .collect();
        let mut name = "cause".to_string();
        while fields.contains(&name) {
            name.push('_');
        }
        Some(name)
    }

    /// `temper_core::impl_failure!(NotFound, message, cause);`: a failure
    /// class's `Display` (its `message` getter, its own or the one
    /// `to_tmpl` makes), `Debug`, `std::error::Error` (its cause as
    /// `source()`), and `temper_core::Failure`.
    fn impl_failure(&mut self, class: DeclId) -> R<Item> {
        let message = (self.member_nodes(class).iter()).find_map(|&m| match self.p.nodes.get(m) {
            Node::Method { decl, .. }
                if self.kind(decl) == DeclKind::Getter && self.p.name(decl) == "message" =>
            {
                Some(decl)
            }
            _ => None,
        });
        let Some(message) = message else {
            return not_yet("a failure class without a message getter");
        };
        if self.bubbles_decl(message) {
            return not_yet("a failure's message that bubbles");
        }
        let cause = self.cause_field(class).unwrap_or_default();
        Ok(Item::Macro(Expr::Macro(
            "temper_core::impl_failure".into(),
            vec![
                Expr::path(self.name(class)),
                Expr::path(self.member_name(message)),
                Expr::path(cause),
            ],
        )))
    }

    /// A sealed failure interface, the library's roots among them: an enum
    /// of the failure types right under it, each a variant of its name,
    /// `From` each type below it, with `impl_failure_enum!`'s traits, each
    /// the variant's; an exported root's `#[non_exhaustive]`, as a library
    /// may add failure types.
    fn failure_enum(&mut self, iface: DeclId) -> R<Vec<Item>> {
        let p = self.p;
        if !self.member_nodes(iface).is_empty() {
            return not_yet("a failure interface with members");
        }
        let mut variants = Vec::new();
        let mut private = false;
        // Those further below, by the variant they're under.
        let mut deep: Vec<(String, Vec<Expr>)> = Vec::new();
        for d in p.decls.ids().filter(|&d| p.decls.contains(d) && d != iface) {
            if !matches!(self.kind(d), DeclKind::Class | DeclKind::Interface)
                || !ancestors(p, d).contains(&iface)
            {
                continue;
            }
            // The type right under `iface` that it's below, or itself,
            // through the one failure type above each.
            let failure_up = |t: &Self, d: DeclId| {
                (p.decls.supers(d).iter())
                    .filter_map(|&s| nominal_decl(p, s))
                    .find(|&s| t.is_failure(s))
            };
            let mut via = d;
            let mut above = failure_up(self, d);
            while let Some(a) = above.filter(|&a| a != iface) {
                via = a;
                above = failure_up(self, a);
            }
            let path = self.top_path(d)?;
            if via == d {
                private |= !self.facts(d).contains(Facts::EXPORTED);
                variants.push((self.name(d), path));
                continue;
            }
            let via = self.name(via);
            match deep.iter_mut().find(|(v, _)| *v == via) {
                Some((_, below)) => below.push(Expr::path(path)),
                None => deep.push((via, vec![Expr::path(path)])),
            }
        }
        let name = self.name(iface);
        let mut attrs = vec!["#[derive(Clone, Debug)]".to_string()];
        let exported = self.facts(iface).contains(Facts::EXPORTED);
        if exported && temper_tmpl::failures::is_library_root(p, self.failure_root, iface) {
            attrs.push("#[non_exhaustive]".into());
        }
        // A failure type the library doesn't export, which host code
        // sees only as the variant's value (`Display`, `source()`), not
        // by its type's name.
        if private && exported {
            attrs.push("#[allow(private_interfaces)]".into());
        }
        let mut args = vec![Expr::path(&name)];
        args.extend((variants.iter()).map(|(v, t)| Expr::path(v).call(vec![Expr::path(t)])));
        let mut items = vec![
            Item::Enum {
                attrs,
                vis: self.vis(iface),
                name: name.clone(),
                variants,
            },
            Item::Macro(Expr::Macro("temper_core::impl_failure_enum".into(), args)),
        ];
        for (via, below) in deep {
            let mut args = vec![Expr::path(&name), Expr::path(via)];
            args.extend(below);
            let from = Expr::Macro("temper_core::impl_failure_from".into(), args);
            items.push(Item::Macro(from));
        }
        Ok(items)
    }

    fn bubbles_decl(&self, decl: DeclId) -> bool {
        self.p.signature(decl).is_some_and(|s| s.bubbles())
    }

    /// Whether a class's method is its `==`, which Rust's `PartialEq`
    /// calls: marked `@operator("==")` (or made by `@auto("==")`), taking
    /// one of the class, giving a `Boolean`, without bubbling.
    fn is_eq_hook(&self, class: DeclId, decl: DeclId) -> bool {
        let d = self.p.decls.get(decl);
        d.kind == DeclKind::Method
            && d.facts.contains(Facts::OPERATOR_EQ)
            && !self.is_private(decl)
            && self.p.signature(decl).is_some_and(|s| {
                !s.bubbles()
                    && s.pass == TypeId::BOOLEAN
                    && s.params.len() == 1
                    && nominal_decl(self.p, s.params[0]) == Some(class)
            })
    }

    /// Whether a class's `==` is Rust's `==`.
    pub(crate) fn eq_hook(&self, method: DeclId) -> bool {
        let owner = self.p.decls.get(method).owner;
        self.p.decls.contains(owner)
            && self.kind(owner) == DeclKind::Class
            && self.own(method)
            && self.is_eq_hook(owner, method)
    }

    /// Whether a method is a plain `toString()` that's its class's
    /// `Display`.
    pub(crate) fn display_hook(&self, method: DeclId) -> bool {
        self.own(method) && is_plain_to_string(self.p, method) && !self.bubbles_decl(method)
    }

    /// `impl ShapeTrait for Rect`: `clone_boxed`, and each of the
    /// interface's members the class fills, or that a default elsewhere
    /// overrides.
    fn class_trait_impl(&mut self, class: DeclId, iface: DeclId) -> R<Item> {
        let (gdecl, guse) = self.type_params(class)?;
        let name = format!("{}{guse}", self.name(class));
        let (iname, iargs, saved) = self.super_type(class, iface)?;
        let made = self.class_trait_items(class, iface, &name, &iname);
        self.subst = saved;
        let items = made?;
        let path = self.top_path(iface)?;
        Ok(Item::Impl {
            head: format!("{gdecl} {path}Trait{iargs} for {name}")
                .trim_start()
                .to_string(),
            items,
        })
    }

    /// A class or interface seen as `iface`, one of its supertypes: its
    /// type as written (`Holder<T>`), its arguments (`<T>`), and the
    /// substitution as it was before this put the interface's type
    /// parameters in, which the caller restores.
    pub(crate) fn super_type(
        &mut self,
        class: DeclId,
        iface: DeclId,
    ) -> R<(String, String, HashMap<DeclId, TypeId>)> {
        let p = self.p;
        let saved = self.subst.clone();
        let ty = p.decls.get(class).ty;
        let Some(args) = temper_tmpl::analysis::as_super(p, ty, iface) else {
            return not_yet("a supertype whose type arguments aren't interned");
        };
        let mut texts = Vec::new();
        for &a in &args {
            texts.push(self.ty(a)?);
        }
        for (&tp, &a) in p.decls.type_params(iface).iter().zip(&args) {
            self.subst.insert(tp, a);
        }
        let iargs = if texts.is_empty() {
            String::new()
        } else {
            format!("<{}>", texts.join(", "))
        };
        Ok((format!("{}{iargs}", self.top_path(iface)?), iargs, saved))
    }

    /// A generic class's type parameters that no field's type has, each
    /// a `PhantomData` field's name and type, as Kotlin's, since Rust
    /// needs each in a field; and a failure class's cause, a
    /// `temper_core::Cause` ([`Self::cause_field`]).
    fn phantoms(&mut self, class: DeclId) -> R<Vec<(String, String)>> {
        let p = self.p;
        let mut out = Vec::new();
        for &tp in p.decls.type_params(class) {
            let tp_ty = p.decls.get(tp).ty;
            let used = self
                .member_nodes(class)
                .iter()
                .any(|&m| match p.nodes.get(m) {
                    Node::Field { decl } => mentions(p, p.decls.get(decl).ty, tp_ty),
                    _ => false,
                });
            if !used {
                let t = self.ty(tp_ty)?;
                let field = format!("phantom_{}", t.to_lowercase());
                out.push((field, format!("std::marker::PhantomData<{t}>")));
            }
        }
        // A failure's cause, set where it's first thrown in a handler.
        if let Some(cause) = self.cause_field(class) {
            out.push((cause, "temper_core::Cause".into()));
        }
        Ok(out)
    }

    /// The struct literal's fields that [`Self::phantoms`] adds, a
    /// failure's cause among them.
    fn phantom_lits(&mut self, class: DeclId) -> R<Vec<(String, Option<Expr>)>> {
        let cause = self.cause_field(class);
        Ok((self.phantoms(class)?.into_iter())
            .map(|(f, _)| {
                let value = match Some(&f) == cause.as_ref() {
                    true => Expr::path("temper_core::Cause::new").call(Vec::new()),
                    false => Expr::path("std::marker::PhantomData"),
                };
                (f, Some(value))
            })
            .collect())
    }

    /// The members of `impl ShapeTrait for Rect`.
    fn class_trait_items(
        &mut self,
        class: DeclId,
        iface: DeclId,
        name: &str,
        iname: &str,
    ) -> R<Vec<Member>> {
        let iname = iname.to_string();
        let mut items = vec![Member::Fn {
            vis: "",
            name: "clone_boxed".into(),
            params: vec!["&self".into()],
            result: Some(iname.clone()),
            body: Some(Block::of(
                Expr::path(format!("{}::new", self.top_path(iface)?))
                    .call(vec![Expr::path("self").method("clone", Vec::new())]),
            )),
        }];
        // Another library's interface has no nodes here: its methods
        // from its `uses` section's declarations, each filled by the
        // class's method of its name (`impl kelpto_md::RenderSinkTrait
        // for StreamRenderSink`).
        if is_external(self.p, iface) {
            return self.external_trait_items(class, iface, name, items);
        }
        for &m in self.member_nodes(iface) {
            if !matches!(
                self.p.nodes.get(m),
                Node::Method { .. } | Node::AbstractMethod { .. }
            ) {
                continue;
            }
            let Some((d, _)) = self.member_decl(m) else {
                continue;
            };
            let (params, names, result) = self.member_sig(m)?;
            let args: Vec<Expr> = names.iter().map(Expr::path).collect();
            let rust = self.member_name(d);
            let call = if self.provider(class, d).is_some() {
                Expr::path("self").method(&rust, args)
            } else {
                match self.winner(class, d) {
                    Some(w) if w == iface => continue,
                    Some(w) => {
                        let mut all = vec![Expr::path("self")];
                        all.extend(args);
                        Expr::path(format!("{}::{rust}", self.trait_path(w)?)).call(all)
                    }
                    None => {
                        return not_yet(format!("{name} without {} of {iname}", self.p.name(d)));
                    }
                }
            };
            items.push(Member::Fn {
                vis: "",
                name: rust,
                params,
                result,
                body: Some(Block::of(call)),
            });
        }
        Ok(items)
    }

    /// [`Self::class_trait_items`] for another library's interface: each
    /// method its `uses` section declares, which the class must fill,
    /// calling the class's own; that library's defaults are its trait's.
    fn external_trait_items(
        &mut self,
        class: DeclId,
        iface: DeclId,
        name: &str,
        mut items: Vec<Member>,
    ) -> R<Vec<Member>> {
        let p = self.p;
        let methods: Vec<DeclId> = (p.decls.ids())
            .filter(|&d| p.decls.get(d).owner == iface && self.kind(d) == DeclKind::Method)
            .collect();
        for d in methods {
            let Some(own) = self.provider(class, d) else {
                continue;
            };
            let Some(sig) = p.signature(d) else {
                return not_yet("another library's method without a signature");
            };
            if !p.decls.type_params(d).is_empty() {
                return not_yet("a generic method of another library's interface");
            }
            let mut params = vec!["&self".to_string()];
            let mut args = Vec::new();
            for (i, &t) in sig.params.iter().enumerate() {
                let given = p.decls.param_names(d).get(i).map(|&n| p.strings.get(n));
                let arg = match given {
                    Some(n) if !n.is_empty() => snake_case(n),
                    _ => format!("arg{i}"),
                };
                params.push(format!("{arg}: {}", self.ty(t)?));
                args.push(Expr::path(&arg));
            }
            let result = self.result(sig.pass, sig.fail)?;
            let rust = self.member_name(own);
            if rust.is_empty() {
                return not_yet(format!("{name}'s {}", p.name(d)));
            }
            items.push(Member::Fn {
                vis: "",
                name: rust.clone(),
                params,
                result,
                body: Some(Block::of(Expr::path("self").method(&rust, args))),
            });
        }
        Ok(items)
    }

    /// An interface: its trait, its handle, which implements the trait
    /// and those above it, and its statics.
    pub(crate) fn interface(&mut self, node: NodeId) -> R<Vec<Item>> {
        let p = self.p;
        let Node::Interface { decl: iface, .. } = p.nodes.get(node) else {
            return not_yet("an interface that isn't one");
        };
        if self.is_failure(iface) {
            return self.failure_enum(iface);
        }
        let bare = self.name(iface);
        let vis = self.vis(iface);
        let (gdecl, guse) = self.type_params(iface)?;
        let where_ = self.extra_bounds(iface)?;
        let name = format!("{bare}{guse}");
        self.uses.insert("std::sync::Arc");
        let mut bounds: Vec<String> = [
            "temper_core::AsAnyValue",
            "temper_core::AnyValueTrait",
            "Send",
            "Sync",
        ]
        .map(String::from)
        .to_vec();
        for &s in p.decls.supers(iface) {
            if let Some(up) = nominal_decl(p, s) {
                let (_, args, saved) = self.super_type(iface, up)?;
                self.subst = saved;
                bounds.push(format!("{}{args}", self.trait_path(up)?));
            }
        }
        let exported = self.facts(iface).contains(Facts::EXPORTED);
        let mut trait_items = vec![Member::Fn {
            vis: if exported || self.boxed.contains(&iface) {
                ""
            } else {
                UNUSED
            },
            name: "clone_boxed".into(),
            params: vec!["&self".into()],
            result: Some(name.clone()),
            body: None,
        }];
        let mut statics = Vec::new();
        let mut own = vec![Member::Fn {
            vis: if exported || self.wrapped.contains(&iface) {
                "pub"
            } else {
                UNUSED_PUB
            },
            name: "new".into(),
            params: vec![format!("selfish: impl {bare}Trait{guse} + 'static")],
            result: Some(name.clone()),
            body: Some(Block::of(Expr::path(&bare).call(vec![
                Expr::path("Arc::new").call(vec![Expr::path("selfish")]),
            ]))),
        }];
        for &m in self.member_nodes(iface) {
            match p.nodes.get(m) {
                Node::AbstractMethod { decl, .. } => {
                    let (params, _, result) = self.member_sig(m)?;
                    trait_items.push(Member::Fn {
                        vis: "",
                        name: self.member_name(decl),
                        params,
                        result,
                        body: None,
                    });
                }
                Node::Method { .. } => {
                    trait_items.push(self.method_member(m, This::Trait(iface), Params::Trait)?);
                }
                Node::StaticField { decl } => {
                    let (stat, getter) = self.static_field(decl)?;
                    statics.push(stat);
                    own.push(getter);
                }
                Node::Function { .. } => {
                    own.push(self.method_member(m, This::None, Params::Free)?);
                }
                other => return not_yet(format!("a {} in an interface", other.kind().name())),
            }
        }
        let mut items = vec![
            Item::Trait {
                vis,
                name: format!("{bare}Trait{gdecl}"),
                bounds,
                items: trait_items,
            },
            Item::TupleStruct {
                attrs: vec!["#[derive(Clone)]".into()],
                vis,
                name: format!("{bare}{gdecl}"),
                types: vec![format!("Arc<dyn {bare}Trait{guse}>")],
            },
        ];
        items.extend(statics);
        items.push(Item::Impl {
            head: format!("{gdecl} {name}").trim_start().to_string(),
            items: own,
        });
        let mut traits = vec![iface];
        traits.extend(ancestors(p, iface));
        for t in traits {
            let (_, args, saved) = self.super_type(iface, t)?;
            let made = self.forwarding_impl(&name, t, &gdecl, &args);
            self.subst = saved;
            items.push(made?);
        }
        items.push(Item::Macro(Expr::Macro(
            "temper_core::impl_any_value_trait_for_interface".into(),
            vec![Expr::path(format!("{name}{where_}"))],
        )));
        items.push(Item::Impl {
            head: format!("{gdecl} std::ops::Deref for {name}")
                .trim_start()
                .to_string(),
            items: vec![
                Member::Type {
                    name: "Target".into(),
                    ty: format!("dyn {bare}Trait{guse}"),
                },
                Member::Fn {
                    vis: "",
                    name: "deref".into(),
                    params: vec!["&self".into()],
                    result: Some("&Self::Target".into()),
                    body: Some(Block::of(Expr::unary(
                        Unary::Ref,
                        Expr::unary(Unary::Deref, Expr::path("self").field("0")),
                    ))),
                },
            ],
        });
        Ok(items)
    }

    /// `impl ATrait for B`, on an interface's handle: each of `A`'s
    /// members, `clone_boxed` first, calls the object's.
    fn forwarding_impl(
        &mut self,
        name: &str,
        iface: DeclId,
        generics: &str,
        args: &str,
    ) -> R<Item> {
        let tpath = self.trait_path(iface)?;
        let object = || {
            Expr::unary(
                Unary::Ref,
                Expr::unary(Unary::Deref, Expr::path("self").field("0")),
            )
        };
        let mut items = vec![Member::Fn {
            vis: "",
            name: "clone_boxed".into(),
            params: vec!["&self".into()],
            result: Some(format!("{}{args}", self.top_path(iface)?)),
            body: Some(Block::of(
                Expr::path(format!("{tpath}::clone_boxed")).call(vec![object()]),
            )),
        }];
        for &m in self.member_nodes(iface) {
            let (Node::Method { decl, .. } | Node::AbstractMethod { decl, .. }) =
                self.p.nodes.get(m)
            else {
                continue;
            };
            let (params, names, result) = self.member_sig(m)?;
            let mut args = vec![object()];
            args.extend(names.iter().map(Expr::path));
            let rust = self.member_name(decl);
            items.push(Member::Fn {
                vis: "",
                name: rust.clone(),
                params,
                result,
                body: Some(Block::of(Expr::path(format!("{tpath}::{rust}")).call(args))),
            });
        }
        Ok(Item::Impl {
            head: format!("{generics} {tpath}{args} for {name}")
                .trim_start()
                .to_string(),
            items,
        })
    }

    /// A class's constructor, `new`: it sets each field, in order, from
    /// values that read only parameters, locals, and fields already set,
    /// then makes the instance, then runs the rest of its body with it.
    /// The frontend's fields-first rule guarantees that every field is
    /// set before any other use of `this`, and by every normal return;
    /// fields set in branches or loops, or with other statements between
    /// them, are still not yet here.
    fn constructor(&mut self, class: DeclId, item: NodeId) -> R<Member> {
        let p = self.p;
        let Node::Method { decl, params, body } = p.nodes.get(item) else {
            return not_yet("a constructor that isn't a method");
        };
        let Some(sig) = p.signature(decl) else {
            return not_yet("a constructor without a signature");
        };
        let name = self.name(class);
        self.begin(item, sig.fail);
        let mut stmts = Vec::new();
        let mut ps = Vec::new();
        self.params_into(item, params, Params::Free, &mut ps, &mut stmts)?;
        let fields: Vec<DeclId> = (self.member_nodes(class).iter())
            .filter_map(|&m| match p.nodes.get(m) {
                Node::Field { decl } => Some(decl),
                _ => None,
            })
            .collect();
        let body_stmts: &[NodeId] = match p.nodes.get(body) {
            Node::Block { stmts } => p.nodes.list(stmts),
            _ => &[],
        };
        // Its parameters' defaults first, which can't read `this`.
        let defaults = temper_tmpl::analysis::defaults(p, body).len();
        for &s in &body_stmts[..defaults] {
            let mut this = false;
            walk(p, s, &mut |id| {
                this |= matches!(p.nodes.get(id), Node::This {})
            });
            if this {
                return not_yet("a constructor's default that reads `this`");
            }
            self.stmt(s, &mut stmts)?;
        }
        let body_stmts = &body_stmts[defaults..];
        // The fields set first, in order, each once, with statements that
        // don't touch `this` among them (std's `Regex`'s `let formatted`),
        // or read only fields already set (`JsonTextProducer`'s
        // `this.stack.add(…)`, through its local), each after how many
        // fields are set.
        let mut set: Vec<(DeclId, NodeId)> = Vec::new();
        let mut between: Vec<(usize, NodeId)> = Vec::new();
        let mut read: HashSet<DeclId> = HashSet::new();
        let mut rest = body_stmts.len();
        for (i, &s) in body_stmts.iter().enumerate() {
            let Node::SetField {
                field,
                object,
                value,
            } = p.nodes.get(s)
            else {
                let (reads, thises) = this_reads(p, s);
                let set_already = |f: DeclId| set.iter().any(|&(g, _)| g == f);
                if thises == reads.len()
                    && reads.iter().all(|&g| set_already(g))
                    && set.len() < fields.len()
                {
                    read.extend(reads);
                    between.push((set.len(), s));
                    continue;
                }
                rest = i;
                break;
            };
            let set_already = |f: DeclId| set.iter().any(|&(g, _)| g == f);
            let mut fits = matches!(p.nodes.get(object), Node::This {}) && !set_already(field);
            // `this` only as the object of a field already set.
            let (reads, thises) = this_reads(p, value);
            fits &= thises == reads.len() && reads.iter().all(|&g| set_already(g));
            if !fits {
                rest = i;
                break;
            }
            read.extend(reads);
            set.push((field, value));
        }
        // A `let` field set again after them, as `if (c) { this.carrying =
        // true; }`, can't be once the instance is made: as fields set in
        // branches.
        let sets_let_again = body_stmts[rest..].iter().any(|&s| {
            let mut again = false;
            walk(p, s, &mut |n| {
                if let Node::SetField { field, object, .. } = p.nodes.get(n) {
                    again |= matches!(p.nodes.get(object), Node::This {})
                        && !self.facts(field).contains(Facts::VAR);
                }
            });
            again
        });
        if sets_let_again
            || set.len() != fields.len()
            || fields.iter().any(|f| !set.iter().any(|(g, _)| g == f))
        {
            // Fields set in branches, as std's `Date`'s, where `this` is
            // only fields set and read: each a local, assigned where the
            // body sets it, then the instance from them, as Kotlin's.
            if fields_only(p, body_stmts) {
                let tail = self
                    .constructor_in_branches(class, &name, item, &fields, body_stmts, &mut stmts)?;
                return self.constructor_member(class, decl, sig.pass, ps, stmts, tail);
            }
            return not_yet(format!(
                "a constructor of {name} that doesn't set each field first"
            ));
        }
        // Names taken in the constructor, for locals of fields.
        let mut taken: HashSet<String> = HashSet::new();
        walk(p, item, &mut |id| {
            if let Node::Param { decl } | Node::Let { local: decl, .. } = p.nodes.get(id) {
                taken.insert(self.name(decl));
            }
        });
        // A field set before a statement among them is a local, so its
        // value comes first.
        let last_between = between.last().map_or(0, |&(at, _)| at);
        read.extend(set.iter().take(last_between).map(|&(f, _)| f));
        let mut lits = Vec::new();
        for (k, (field, value)) in set.into_iter().enumerate() {
            for &(_, s) in between.iter().filter(|&&(at, _)| at == k) {
                self.stmt(s, &mut stmts)?;
            }
            let fname = self.name(field);
            let ty = p.decls.get(field).ty;
            // A parameter read only here moves into its field.
            let moved = match p.nodes.get(value) {
                Node::Local { local } => {
                    let mut reads = 0;
                    walk(p, item, &mut |id| {
                        if matches!(p.nodes.get(id), Node::Local { local: l } if l == local) {
                            reads += 1;
                        }
                    });
                    (reads == 1).then(|| Expr::path(self.name(local)))
                }
                _ => None,
            };
            let mut e = match moved {
                Some(e) => e,
                None => self.expr(value, Want::Owned)?,
            };
            if read.contains(&field) {
                let mut local = fname.clone();
                while taken.contains(&local) {
                    local.push('_');
                }
                taken.insert(local.clone());
                stmts.push(Stmt::Let {
                    name: local.clone(),
                    mutable: false,
                    ty: super::numeric_value(&e).then(|| self.ty(ty)).transpose()?,
                    value: Some(e),
                });
                self.ctor_fields.insert(field, local.clone());
                e = if self.is_copy(ty) {
                    Expr::path(local)
                } else {
                    Expr::path(local).method("clone", Vec::new())
                };
            }
            if self.facts(field).contains(Facts::VAR) {
                e = Expr::path("RwLock::new").call(vec![e]);
            }
            lits.push(match e {
                Expr::Path(ref x) if *x == fname => (fname, None),
                e => (fname, Some(e)),
            });
        }
        self.ctor_fields.clear();
        lits.extend(self.phantom_lits(class)?);
        let made = Expr::path(&name).call(vec![
            Expr::path("Arc::new").call(vec![Expr::Struct(format!("{name}Struct"), lits)]),
        ]);
        let tail = if rest == body_stmts.len() {
            made
        } else {
            let mut local = "selfish".to_string();
            while taken.contains(&local) {
                local.push('_');
            }
            stmts.push(Stmt::Let {
                name: local.clone(),
                mutable: false,
                ty: None,
                value: Some(made),
            });
            let outer = std::mem::replace(&mut self.this, This::Local(local.clone()));
            let mut done = Ok(());
            for &s in &body_stmts[rest..] {
                done = self.stmt(s, &mut stmts);
                if done.is_err() {
                    break;
                }
            }
            self.this = outer;
            done?;
            Expr::path(local)
        };
        self.constructor_member(class, decl, sig.pass, ps, stmts, tail)
    }

    /// A constructor's `new`, from its statements and the instance it
    /// makes, `Ok(…)` where it bubbles.
    fn constructor_member(
        &mut self,
        class: DeclId,
        decl: DeclId,
        result: TypeId,
        ps: Vec<String>,
        stmts: Vec<Stmt>,
        tail: Expr,
    ) -> R<Member> {
        let tail = if self.fails.is_some() {
            Expr::path("Ok").call(vec![tail])
        } else {
            tail
        };
        let fails = self.fails;
        let result = self.result(result, fails)?;
        let exported = self.facts(class).contains(Facts::EXPORTED);
        let vis = match self.member_vis(decl) {
            // Kotlin's API has `new()` without `Default`.
            "pub" if exported && ps.is_empty() && self.fails.is_none() => {
                "#[allow(clippy::new_without_default)]\npub"
            }
            vis if exported || self.constructed.contains(&class) => vis,
            "pub" => UNUSED_PUB,
            _ => UNUSED,
        };
        Ok(Member::Fn {
            vis,
            name: "new".into(),
            params: ps,
            result,
            body: Some(Block::new(stmts, Some(tail))),
        })
    }

    /// A constructor's body whose fields are set in branches: a local
    /// declared for each field, the body with each `this.f = v` assigning
    /// `f`'s local and each read of `this.f` reading it, then the instance
    /// made from them, which this gives. Rust checks that every path that
    /// gets there has set each one, as the frontend's fields-first rule
    /// guarantees.
    fn constructor_in_branches(
        &mut self,
        class: DeclId,
        name: &str,
        item: NodeId,
        fields: &[DeclId],
        body: &[NodeId],
        stmts: &mut Vec<Stmt>,
    ) -> R<Expr> {
        let p = self.p;
        let mut taken: HashSet<String> = HashSet::new();
        walk(p, item, &mut |id| {
            if let Node::Param { decl } | Node::Let { local: decl, .. } = p.nodes.get(id) {
                taken.insert(self.name(decl));
            }
        });
        let mut lits = Vec::new();
        for &field in fields {
            let fname = self.name(field);
            let mut local = fname.clone();
            while taken.contains(&local) {
                local.push('_');
            }
            taken.insert(local.clone());
            let ty = self.ty(p.decls.get(field).ty)?;
            // `mut` where some path sets it twice, as a default first and
            // then, in a branch, another value.
            let sets: usize = (body.iter())
                .map(|&s| temper_tmpl::analysis::most_sets(p, s, field))
                .sum();
            stmts.push(Stmt::Let {
                name: local.clone(),
                mutable: sets > 1,
                ty: Some(ty),
                value: None,
            });
            self.ctor_fields.insert(field, local.clone());
            let mut e = Expr::path(local);
            if self.facts(field).contains(Facts::VAR) {
                e = Expr::path("RwLock::new").call(vec![e]);
            }
            lits.push(match e {
                Expr::Path(ref x) if *x == fname => (fname, None),
                e => (fname, Some(e)),
            });
        }
        let mut done = Ok(());
        for &s in body {
            done = self.stmt(s, stmts);
            if done.is_err() {
                break;
            }
        }
        self.ctor_fields.clear();
        done?;
        lits.extend(self.phantom_lits(class)?);
        Ok(Expr::path(name).call(vec![
            Expr::path("Arc::new").call(vec![Expr::Struct(format!("{name}Struct"), lits)]),
        ]))
    }

    /// A builder, as Kotlin's, for an exported class whose public
    /// constructor takes two or more arguments: `RectBuilder { w, h }`,
    /// whose `build()` calls `Rect::new`. With optional parameters, the
    /// builder holds the required ones, and its `options()` gives
    /// `RectOptions`, with a method to set each optional one (`.h(2)`),
    /// and `build()` on either. Kotlin passes the required ones first;
    /// these keep the constructor's order. A generic class's take its
    /// type parameters, as Kotlin's: `BoxBuilder<T>`, `BoxOptions<T>`.
    fn builder(&mut self, class: DeclId, ctor: DeclId) -> R<()> {
        let p = self.p;
        if !self.facts(class).contains(Facts::EXPORTED) || self.is_private(ctor) {
            return Ok(());
        }
        let Some(node) = self
            .member_nodes(class)
            .iter()
            .copied()
            .find(|&m| matches!(p.nodes.get(m), Node::Method { decl, .. } if decl == ctor))
        else {
            return Ok(());
        };
        let Node::Method { params, .. } = p.nodes.get(node) else {
            return Ok(());
        };
        let params = p.nodes.list(params);
        if params.len() < 2 {
            return Ok(());
        }
        let (gdecl, guse) = self.type_params(class)?;
        let name = self.name(class);
        let (builder, options) = (format!("{name}Builder"), format!("{name}Options"));
        let head = |ty: &str| format!("{gdecl} {ty}{guse}").trim_start().to_string();
        let any_optional = has_optional(p, params);
        let mut fields = Vec::new();
        let mut optional = Vec::new();
        let mut args = Vec::new();
        for &param in params {
            let Node::Param { decl } = p.nodes.get(param) else {
                continue;
            };
            let pname = self.name(decl);
            let ty = p.decls.get(decl).ty;
            if p.decls.get(decl).facts.contains(Facts::OPTIONAL) {
                let inner = self.ty(p.types.non_null(ty))?;
                optional.push((pname.clone(), self.ty(ty)?, inner));
                args.push(Expr::path("self").field(pname));
            } else {
                fields.push(("pub", pname.clone(), self.ty(ty)?));
                let selfish = if !any_optional {
                    Expr::path("self")
                } else {
                    Expr::path("self").field("selfish")
                };
                args.push(selfish.field(pname));
            }
        }
        let made = Some(format!("{name}{guse}"));
        let result = match self.p.signature(ctor).and_then(|s| s.fail) {
            Some(fail) => self.result_of(made, fail)?,
            None => format!("{name}{guse}"),
        };
        let required = !fields.is_empty();
        self.builders.push(Item::Struct {
            attrs: vec!["#[derive(Clone)]".into()],
            vis: "pub",
            name: format!("{builder}{gdecl}"),
            fields,
        });
        let build = |body: Expr| Member::Fn {
            vis: "pub",
            name: "build".into(),
            params: vec!["self".into()],
            result: Some(result.clone()),
            body: Some(Block::of(body)),
        };
        let new = Expr::path(format!("{name}::new")).call(args);
        if optional.is_empty() {
            self.builders.push(Item::Impl {
                head: head(&builder),
                items: vec![build(new)],
            });
            return Ok(());
        }
        // Kotlin keeps the builder in the options even without required
        // fields, which clippy then finds never read.
        let mut option_fields = Vec::new();
        if required {
            option_fields.push(("", "selfish".to_string(), format!("{builder}{guse}")));
        }
        option_fields.extend(optional.iter().map(|(n, t, _)| ("", n.clone(), t.clone())));
        self.builders.push(Item::Struct {
            attrs: vec!["#[derive(Clone)]".into()],
            vis: "pub",
            name: format!("{options}{gdecl}"),
            fields: option_fields,
        });
        let mut inits = Vec::new();
        if required {
            inits.push(("selfish".to_string(), None));
        }
        inits.extend(
            optional
                .iter()
                .map(|(n, ..)| (n.clone(), Some(Expr::path("None")))),
        );
        let selfish = if required { "selfish" } else { "_selfish" };
        let mut items = vec![Member::Fn {
            vis: "pub",
            name: "new".into(),
            params: vec![format!("{selfish}: {builder}{guse}")],
            result: Some("Self".into()),
            body: Some(Block::of(Expr::Struct("Self".into(), inits))),
        }];
        for (n, _, inner) in &optional {
            let set = Stmt::Assign(
                Expr::path("self").field(n.clone()),
                Expr::path("Some").call(vec![Expr::path(n.clone())]),
            );
            items.push(Member::Fn {
                vis: "pub",
                name: n.clone(),
                params: vec!["mut self".into(), format!("{n}: {inner}")],
                result: Some("Self".into()),
                body: Some(Block::new(vec![set], Some(Expr::path("self")))),
            });
        }
        items.push(build(new));
        self.builders.push(Item::Impl {
            head: head(&options),
            items,
        });
        let via_options = Expr::path("self")
            .method("options", Vec::new())
            .method("build", Vec::new());
        self.builders.push(Item::Impl {
            head: head(&builder),
            items: vec![
                build(via_options),
                Member::Fn {
                    vis: "pub",
                    name: "options".into(),
                    params: vec!["self".into()],
                    result: Some(format!("{options}{guse}")),
                    body: Some(Block::of(
                        Expr::path(format!("{options}::new")).call(vec![Expr::path("self")]),
                    )),
                },
            ],
        });
        Ok(())
    }

    /// `this` as its place wants it.
    pub(crate) fn this(&mut self, want: Want) -> R<Expr> {
        let owned = matches!(want, Want::Owned | Want::IntoArc);
        Ok(match self.this.clone() {
            This::None => return not_yet("`this` outside a method"),
            This::Class if owned => Expr::path("self").method("clone", Vec::new()),
            This::Class => Expr::path("self"),
            This::Trait(iface) if owned => {
                Expr::path(format!("{}::clone_boxed", self.trait_path(iface)?))
                    .call(vec![Expr::path("self")])
            }
            This::Trait(_) => Expr::path("self"),
            This::Local(name) if owned => Expr::path(name).method("clone", Vec::new()),
            This::Local(name) => Expr::path(name),
        })
    }

    /// Reads an instance field: a field of the class being constructed
    /// from its local; else of a class of this module directly, or a
    /// `var` through its getter, which holds its lock only for the read;
    /// else through its getter.
    pub(crate) fn get_field(
        &mut self,
        field: DeclId,
        object: NodeId,
        ty: TypeId,
        want: Want,
    ) -> R<Expr> {
        let p = self.p;
        let copy = self.is_copy(ty);
        let owned = matches!(want, Want::Owned | Want::IntoArc);
        // A step's value, which its narrowed result is.
        if is_external(p, field) && self.step_result(p.nodes.ty(object)) == Some(true) {
            return self.expr(object, want);
        }
        // A pair's, a tuple's.
        if is_external(p, field) && self.type_qname(p.nodes.ty(object)) == "core.type Pair" {
            let index = if p.name(field) == "key" { "0" } else { "1" };
            let e = self.expr(object, Want::Borrowed)?.field(index);
            return Ok(if owned && !copy {
                e.method("clone", Vec::new())
            } else {
                e
            });
        }
        if matches!(p.nodes.get(object), Node::This {}) {
            if let Some(local) = self.ctor_fields.get(&field) {
                let e = Expr::path(local.clone());
                return Ok(if owned && !copy {
                    e.method("clone", Vec::new())
                } else {
                    e
                });
            }
        }
        let obj = self.expr(object, Want::Borrowed)?;
        let name = self.name(field);
        let here = home(p, field) == Some(self.here);
        if !here || self.facts(field).contains(Facts::VAR) {
            return Ok(obj.method(self.accessor(field, false), Vec::new()));
        }
        let e = obj.field("0").field(name);
        Ok(if owned && !copy {
            e.method("clone", Vec::new())
        } else {
            e
        })
    }

    /// Writes an instance field: of a class of this module under its
    /// lock, else through its setter.
    pub(crate) fn set_field(&mut self, field: DeclId, object: NodeId, value: NodeId) -> R<Stmt> {
        let p = self.p;
        let value = self.expr(value, Want::Owned)?;
        // A constructor's field set in a branch, its local
        // (`constructor_in_branches`).
        if matches!(p.nodes.get(object), Node::This {}) {
            if let Some(local) = self.ctor_fields.get(&field) {
                return Ok(Stmt::Assign(Expr::path(local.clone()), value));
            }
        }
        let obj = self.expr(object, Want::Borrowed)?;
        if home(p, field) != Some(self.here) {
            return Ok(Stmt::Expr(
                obj.method(self.accessor(field, true), vec![value]),
            ));
        }
        if !self.facts(field).contains(Facts::VAR) {
            return not_yet("setting a `let` field after its constructor makes the instance");
        }
        let place = obj.field("0").field(self.name(field));
        let write = place
            .method("write", Vec::new())
            .method("unwrap", Vec::new());
        Ok(Stmt::Assign(Expr::unary(Unary::Deref, write), value))
    }

    /// `RECT__UNIT.set(…)`, in the init block.
    pub(crate) fn init_static(&mut self, target: DeclId, value: NodeId) -> R<Stmt> {
        if home(self.p, target) != Some(self.here) {
            return not_yet("initializing another module's static");
        }
        let v = self.expr(value, Want::Owned)?;
        let fail = Expr::Closure(
            vec!["_".into()],
            Block::of(Expr::Macro("panic".into(), Vec::new())),
        );
        Ok(Stmt::Expr(
            Expr::path(self.static_of(target))
                .method("set", vec![v])
                .method("unwrap_or_else", vec![fail]),
        ))
    }

    /// A static's getter: `Rect::unit()`.
    pub(crate) fn get_static(&mut self, target: DeclId) -> R<Expr> {
        if is_external(self.p, target) && library_of(self.p, target) == "core" {
            // `String.begin`, `Float64.pi`, and the like.
            return self.connected(target, &[], Want::Owned);
        }
        let owner = self.p.decls.get(target).owner;
        Ok(
            Expr::path(format!("{}::{}", self.top_path(owner)?, self.name(target)))
                .call(Vec::new()),
        )
    }

    /// A call of a static function, `C::say_hi()`.
    pub(crate) fn static_fn_path(&self, callee: DeclId) -> R<Option<String>> {
        let owner = self.p.decls.get(callee).owner;
        if !self.p.decls.contains(owner)
            || !matches!(self.kind(owner), DeclKind::Class | DeclKind::Interface)
        {
            return Ok(None);
        }
        Ok(Some(format!(
            "{}::{}",
            self.top_path(owner)?,
            self.name(callee)
        )))
    }

    /// A call of a method of the program's own: on a class, its method;
    /// on an interface, its trait's, as a method where that's
    /// unambiguous, else by the trait's path. An upcast receiver is the
    /// value as it is, since its class implements the trait.
    pub(crate) fn call_method(
        &mut self,
        method: DeclId,
        fish: &str,
        receiver: NodeId,
        args: &[NodeId],
    ) -> R<Expr> {
        let p = self.p;
        let name = self.member_name(method);
        let rust = format!("{name}{fish}");
        if !p.is_virtual(method) {
            // Rust's `==` where both sides are values; a side narrowed
            // from a nullable one, or `this` (`self`, a `&Self`), is a
            // reference, which `==` won't take beside a value, so then
            // the method, as Kotlin's.
            let reference =
                |n: NodeId| matches!(p.nodes.get(n), Node::NonNull { .. } | Node::This {});
            if let [other] = args {
                if self.eq_hook(method) && !reference(receiver) && !reference(*other) {
                    let a = self.expr(receiver, Want::Borrowed)?;
                    let b = self.expr(*other, Want::Borrowed)?;
                    return Ok(Expr::bin(Bin::Eq, a, b));
                }
            }
            let recv = self.expr(receiver, Want::Borrowed)?;
            let args = self.args(method, args)?;
            return Ok(recv.method(rust, args));
        }
        let iface = p.decls.get(method).owner;
        let mut recv_node = receiver;
        while let Node::Upcast { value } = p.nodes.get(recv_node) {
            recv_node = value;
        }
        let params: Vec<TypeId> =
            (p.signature(method)).map_or_else(Vec::new, |s| s.params.to_vec());
        let mut xs = Vec::new();
        for (i, &a) in args.iter().enumerate() {
            if matches!(p.nodes.get(a), Node::Omitted {}) {
                xs.push(self.omitted(params.get(i).copied())?);
                continue;
            }
            // A trait method's `String?` is an `Option<Arc<String>>`
            // (`Params::Trait`), which a `&str` won't do for.
            xs.push(self.expr(a, Want::Owned)?);
        }
        let recv = self.expr(recv_node, Want::Borrowed)?;
        let ty = nominal_decl(p, p.nodes.ty(recv_node));
        // Not in tests, whose module doesn't have the module's traits in
        // scope.
        let plain = home(p, iface) == Some(self.here)
            && !self.in_tests
            && ty.is_some_and(|t| self.unambiguous(t, &name));
        if plain {
            return Ok(recv.method(rust, xs));
        }
        // A narrowed local bound by reference (`if let Some(x) = &x`).
        let by_ref = match p.nodes.get(recv_node) {
            Node::NonNull { value } => matches!(p.nodes.get(value),
                Node::Local { local } if self.narrowed.get(&local) == Some(&false)),
            _ => false,
        };
        let recv = match recv {
            Expr::Path(ref s) if s == "self" => recv,
            recv if by_ref => recv,
            // `x.as_ref().unwrap()`, a narrowed value borrowed already, as
            // a cell's is in a state machine.
            recv if crate::translate::borrowed_unwrap(&recv) => recv,
            recv => Expr::unary(Unary::Ref, recv),
        };
        let mut all = vec![recv];
        all.extend(xs);
        Ok(Expr::path(format!("{}::{rust}", self.trait_path(iface)?)).call(all))
    }

    /// Whether `x.name(…)` on a value of class or interface `ty` calls
    /// exactly one method: the class's own, or one trait's.
    fn unambiguous(&self, ty: DeclId, name: &str) -> bool {
        let mut traits = Vec::new();
        match self.kind(ty) {
            DeclKind::Class => {
                if self.instance_names(ty).iter().any(|n| n == name) {
                    return true;
                }
            }
            DeclKind::Interface => traits.push(ty),
            _ => return false,
        }
        traits.extend(ancestors(self.p, ty));
        let defining = traits
            .iter()
            .filter(|&&t| self.instance_names(t).iter().any(|n| n == name))
            .count();
        defining == 1 && !matches!(name, "clone_boxed")
    }

    /// `Shape::new(r)`: a class's instance, or an interface's, as an
    /// interface above it.
    pub(crate) fn upcast(&mut self, id: NodeId, value: NodeId) -> R<Expr> {
        let p = self.p;
        let to = p.nodes.ty(id);
        let from = p.nodes.ty(value);
        if from == to {
            return self.expr(value, Want::Owned);
        }
        match self.type_qname(to).as_str() {
            // A string index as an option, `Some(i)`; no index, `None`.
            "core.type StringIndexOption" => {
                if self.string_index(from) == Some(false) {
                    if let Node::GetStatic { .. } = p.nodes.get(value) {
                        return Ok(Expr::path("None"));
                    }
                    let v = self.expr(value, Want::Owned)?;
                    return Ok(
                        Expr::path("temper_core::string::cast_none_as_index_option").call(vec![v])
                    );
                }
                let v = self.expr(value, Want::Owned)?;
                return Ok(Expr::path("Some").call(vec![v]));
            }
            "core.type Listed" => {
                let v = self.expr(value, Want::Owned)?;
                return Ok(Expr::path("temper_core::Listed::new").call(vec![v]));
            }
            // `Some(…)` and `None` are `Option`s already.
            "core.type GeneratorResult" => return self.expr(value, Want::Owned),
            "core.type Mapped" => {
                let v = self.expr(value, Want::Owned)?;
                return Ok(Expr::path("temper_core::Mapped::new").call(vec![v]));
            }
            _ => {}
        }
        // Anything as an `AnyValue`, as Kotlin's: `v.as_any_value()`, or
        // for an option, `v.map(|it| it.as_any_value())`.
        if p.types.non_null(to) == TypeId::ANY_VALUE {
            self.uses.insert("temper_core::AsAnyValue");
            let v = self.expr(value, Want::Owned)?;
            if !p.types.is_nullable(from) {
                return Ok(v.method("as_any_value", vec![]));
            }
            let each = Expr::path("it").method("as_any_value", vec![]);
            let each = Expr::Closure(vec!["it".into()], Block::of(each));
            return Ok(v.method("map", vec![each]));
        }
        // A type parameter as its class bound: `Into` it.
        if let (temper_tmpl::TypeData::TypeParam { .. }, Some(class)) = (
            p.types.get(from),
            nominal_decl(p, to).filter(|&d| self.kind(d) == DeclKind::Class),
        ) {
            let _ = class;
            let ty = self.ty(to)?;
            let v = self.expr(value, Want::Owned)?;
            return Ok(Expr::path(format!("Into::<{ty}>::into")).call(vec![v]));
        }
        // Core's `Error`, any library's failure: `Arc<dyn Failure>`.
        if self.is_failure_root(to) {
            let v = self.expr(value, Want::Owned)?;
            return Ok(Expr::path("temper_core::failure::failure").call(vec![v]));
        }
        let Some(iface) = nominal_decl(p, to).filter(|&d| self.kind(d) == DeclKind::Interface)
        else {
            return not_yet(format!("an upcast to {}", p.show_type(to)));
        };
        let v = self.expr(value, Want::Owned)?;
        Ok(Expr::path(format!("{}::new", self.top_path(iface)?)).call(vec![v]))
    }

    /// Whether a type is core's `StringIndex` (true) or `NoStringIndex`
    /// (false), which an `Option<usize>` is checked or cast to.
    pub(crate) fn string_index(&self, ty: TypeId) -> Option<bool> {
        match self.type_qname(ty).as_str() {
            "core.type StringIndex" => Some(true),
            "core.type NoStringIndex" => Some(false),
            _ => None,
        }
    }

    /// Whether a type is a generator step's value result (`Some`, read
    /// as its value) or its done result (`None`), as `Option`s give them.
    pub(crate) fn step_result(&self, ty: TypeId) -> Option<bool> {
        match self.type_qname(ty).as_str() {
            "core.type ValueResult" => Some(true),
            "core.type DoneResult" => Some(false),
            _ => None,
        }
    }

    /// `temper_core::cast::<Square>(q)`, an `Option`; for a string index,
    /// `temper_core::string::cast_as_index(i)`, as Kotlin's.
    pub(crate) fn cast(&mut self, id: NodeId, value: NodeId) -> R<Expr> {
        if let Some(index) = self.string_index(self.p.nodes.ty(id)) {
            let v = self.expr(value, Want::Owned)?;
            let f = if index {
                "temper_core::string::cast_as_index"
            } else {
                "temper_core::string::cast_as_no_index"
            };
            return Ok(Expr::path(f).call(vec![v]));
        }
        let ty = self.ty(self.p.nodes.ty(id))?;
        let v = self.expr(value, Want::Owned)?;
        let cast = Expr::path(format!("temper_core::cast::<{ty}>"));
        // A nullable value is cast if it's there, as Kotlin's.
        if self.p.types.is_nullable(self.p.nodes.ty(value)) {
            return Ok(v.method("and_then", vec![cast]));
        }
        Ok(cast.call(vec![v]))
    }

    /// `temper_core::is::<Square>(q)`.
    pub(crate) fn is(&mut self, ty: TypeId, value: NodeId) -> R<Expr> {
        if let Some(index) = self.string_index(ty).or(self.step_result(ty)) {
            // `i.is_some()` or `i.is_none()`, as Kotlin's.
            let v = self.expr(value, Want::Borrowed)?;
            let name = if index { "is_some" } else { "is_none" };
            return Ok(v.method(name, Vec::new()));
        }
        let ty = self.ty(ty)?;
        let v = self.expr(value, Want::Owned)?;
        Ok(Expr::path(format!("temper_core::is::<{ty}>")).call(vec![v]))
    }

    /// Whether two values are the same object, by `temper_core`'s
    /// `ptr_id`, which an interface's handle takes from its object.
    pub(crate) fn identical(&mut self, left: NodeId, right: NodeId) -> R<Expr> {
        let id = |t: &mut Translator<'a>, n: NodeId| -> R<Expr> {
            // `x!!` owned, as borrowed it's a reference already
            // (`x.as_ref().unwrap()`), or not (`if let` binding).
            let want = match t.p.nodes.get(n) {
                Node::NonNull { .. } => Want::Owned,
                _ => Want::Borrowed,
            };
            let e = t.expr(n, want)?;
            Ok(Expr::path("temper_core::AnyValueTrait::ptr_id")
                .call(vec![Expr::unary(Unary::Ref, e)]))
        };
        let a = id(self, left)?;
        let b = id(self, right)?;
        Ok(Expr::bin(Bin::Eq, a, b))
    }

    /// Adds the module refs of classes' and interfaces' methods to what
    /// functions use.
    pub(crate) fn class_refs(p: &Program, module: &Module) -> Vec<DeclId> {
        let mut out = Vec::new();
        for item in module.all_items() {
            if let Node::Class { .. } | Node::Interface { .. } = p.nodes.get(item) {
                out.extend(temper_tmpl::analysis::module_refs(p, item));
            }
        }
        out
    }
}

/// Whether type `ty` is or has `part`.
fn mentions(p: &Program, ty: TypeId, part: TypeId) -> bool {
    ty == part
        || match p.types.get(ty) {
            temper_tmpl::TypeData::Nominal { args, .. } => {
                p.types.list(args).iter().any(|&a| mentions(p, a, part))
            }
            temper_tmpl::TypeData::Nullable(inner) => mentions(p, inner, part),
            // A function type's parameters and result: Rust's `dyn Fn`
            // uses its type parameters.
            temper_tmpl::TypeData::Function { .. } => p
                .types
                .signature(ty)
                .is_some_and(|s| (s.params.iter().chain([&s.pass])).any(|&t| mentions(p, t, part))),
            _ => false,
        }
}

/// Whether any of a constructor's parameters is optional.
fn has_optional(p: &Program, params: &[NodeId]) -> bool {
    params.iter().any(|&param| {
        matches!(p.nodes.get(param), Node::Param { decl } if p.decls.get(decl).facts.contains(Facts::OPTIONAL))
    })
}

/// The fields of `this` that a node reads, and how many times it names
/// `this` at all: the same count if it reads `this` only for its fields.
/// Whether statements use `this` only to set and read its fields.
fn fields_only(p: &Program, stmts: &[NodeId]) -> bool {
    let (mut fields, mut thises) = (0, 0);
    for &s in stmts {
        walk(p, s, &mut |n| match p.nodes.get(n) {
            Node::GetField { object, .. } | Node::SetField { object, .. }
                if matches!(p.nodes.get(object), Node::This {}) =>
            {
                fields += 1;
            }
            Node::This {} => thises += 1,
            _ => {}
        });
    }
    fields == thises
}

fn this_reads(p: &Program, id: NodeId) -> (Vec<DeclId>, usize) {
    let (mut reads, mut thises) = (Vec::new(), 0);
    walk(p, id, &mut |n| match p.nodes.get(n) {
        Node::GetField { field, object } if matches!(p.nodes.get(object), Node::This {}) => {
            reads.push(field);
        }
        Node::This {} => thises += 1,
        _ => {}
    });
    (reads, thises)
}
