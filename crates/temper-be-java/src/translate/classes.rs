//! Classes and interfaces, each a `.java` file of its own in its module's
//! package, as Kotlin's:
//!
//! - A class is a `final class`, `public` if exported, else its
//!   package's, implementing its interfaces; an interface is an
//!   `interface` extending its own.
//! - Fields are `public` (`final` for a `let`, unless a constructor may
//!   set it twice), with JavaBeans accessors
//!   as Kotlin's: `getX()` (`isX()` for a `Boolean`, and the name itself
//!   if it starts with `is` or `get`), and for a `var`, `setX(newX)`.
//!   Getters and setters are those methods; a field that implements an
//!   interface's property does so through its accessors. Code reads and
//!   writes fields directly, `this.x` and `other.x`, where Kotlin's calls
//!   accessors from outside the class (and private fields' accessors,
//!   which it never defines).
//! - Private members are `private`, where Kotlin's are the package's; a
//!   private constructor is the package's, as Kotlin's, and so is a
//!   private static function, which static fields' values may call.
//! - A constructor of two or more parameters gets a `Builder`, as
//!   Kotlin's: a setter per parameter, then `build()`, which throws
//!   `IllegalStateException` naming those not set. Kotlin's checks only
//!   primitive ones; this checks every one, a nullable one by a flag, as
//!   null is a value it may be given.
//! - Static fields are `static final` with their values given by the
//!   class's own initializer, as Kotlin's, where the value can't tell
//!   when it runs: it reads no module state, calls nothing that could,
//!   and reads only its own class's statics given before it
//!   ([`Translator::plan_statics`]). Any other is a `static` field that
//!   the module's static initializer assigns in Temper's order, and the
//!   class's static block calls the module class's `initStatics()`, so
//!   reading one from elsewhere runs that first. Each public one of an
//!   exported class is read through a public static getter, named as an
//!   instance field's (`getStandard()`), and its field is private, or
//!   the package's where the module's class sets it, so host code can't
//!   reassign it; Kotlin's are public fields. Code reads a field itself
//!   where Java lets it, else through its getter, as another library's
//!   always does.
//!   Static functions are static methods.
//! - An interface's members with bodies are `default` methods, and those
//!   without, abstract. Where Java wouldn't pick the method Temper does
//!   (Kotlin's `injectSuperCallMethods`), the type forwards to it,
//!   `B.super.a()`: an interface, every method with a body that it
//!   inherits, and a class, those it inherits by more than one of its
//!   supertypes ([`forwards`]).
//! - A class's plain `toString()` overrides `Object`'s, by its name
//!   alone; an `@operator` method is a method; `<=>`'s calls compare
//!   with 0 as tmpl has them.
//! - A user's connected class that host code defines (per
//!   `temper_tmpl::host::uses_host`) isn't generated: the library's own
//!   `NAME.java` beside the module is copied in its place.

use super::{R, Translator, not_yet};
use crate::tree::{self, Expr, Member, MemberField, MemberKind, Stmt, Vis};
use std::collections::HashSet;
use temper_tmpl::analysis::{ancestors, nominal_decl, walk};
use temper_tmpl::host::uses_host;
use temper_tmpl::qname::{is_external, library_of};
use temper_tmpl::{DeclId, DeclKind, Facts, Module, Node, NodeId, Program, TypeId};

/// Whether a declaration is a class or interface.
pub(super) fn is_type(p: &Program, decl: DeclId) -> bool {
    p.decls.contains(decl)
        && matches!(
            p.decls.get(decl).kind,
            DeclKind::Class | DeclKind::Interface
        )
}

/// A name with its first letter capitalized, as Kotlin's camel to Pascal.
fn pascal(name: &str) -> String {
    let mut chars = name.chars();
    match chars.next() {
        Some(first) => first.to_uppercase().chain(chars).collect(),
        None => String::new(),
    }
}

/// A getter's name, as Kotlin's `getterName`.
pub(super) fn getter_name(name: &str, boolean: bool) -> String {
    if name.starts_with("is") || name.starts_with("get") {
        name.to_string()
    } else if boolean {
        format!("is{}", pascal(name))
    } else {
        format!("get{}", pascal(name))
    }
}

/// A setter's name, as Kotlin's `setterName`.
pub(super) fn setter_name(name: &str) -> String {
    format!("set{}", pascal(name))
}

/// What a member overrides by: its name, and whether it's a getter,
/// setter, or method. A field is a getter, and a `var` a setter too.
#[derive(Clone, Copy, PartialEq, Eq, Hash)]
struct Key<'a>(&'a str, DeclKind);

fn keys(p: &Program, decl: DeclId) -> Vec<Key<'_>> {
    let d = p.decls.get(decl);
    let name = p.name(decl);
    match d.kind {
        DeclKind::Field if d.facts.contains(Facts::VAR) => {
            vec![Key(name, DeclKind::Getter), Key(name, DeclKind::Setter)]
        }
        DeclKind::Field => vec![Key(name, DeclKind::Getter)],
        DeclKind::Getter | DeclKind::Setter | DeclKind::Method => vec![Key(name, d.kind)],
        _ => Vec::new(),
    }
}

/// A type's members' declarations and nodes.
pub(super) fn members(p: &Program, decl: DeclId) -> Vec<(DeclId, NodeId)> {
    let Some(node) = type_node(p, decl) else {
        return Vec::new();
    };
    let (Node::Class { members, .. } | Node::Interface { members, .. }) = p.nodes.get(node) else {
        return Vec::new();
    };
    (p.nodes.list(members).iter())
        .filter_map(|&m| match p.nodes.get(m) {
            Node::Field { decl }
            | Node::StaticField { decl }
            | Node::Method { decl, .. }
            | Node::AbstractMethod { decl, .. }
            | Node::Function { decl, .. } => Some((decl, m)),
            _ => None,
        })
        .collect()
}

/// A class's or interface's node, from its module's items, its test
/// items' too.
fn type_node(p: &Program, decl: DeclId) -> Option<NodeId> {
    p.modules.iter().flat_map(|m| m.all_items()).find(|&i| {
        matches!(p.nodes.get(i), Node::Class { decl: d, .. } | Node::Interface { decl: d, .. } if d == decl)
    })
}

/// Whether a member has a body: a method, not an abstract one or a
/// field.
fn has_body(p: &Program, node: NodeId) -> bool {
    matches!(p.nodes.get(node), Node::Method { .. })
}

/// Whether a type's own members (with bodies, or any) include one by
/// `key`.
fn defines(p: &Program, ty: DeclId, key: Key<'_>, with_body: bool) -> bool {
    members(p, ty)
        .into_iter()
        .any(|(d, n)| keys(p, d).contains(&key) && (!with_body || has_body(p, n)))
}

/// The type's direct supertypes' declarations, in order.
fn supers(p: &Program, ty: DeclId) -> Vec<DeclId> {
    (p.decls.supers(ty).iter())
        .filter_map(|&s| nominal_decl(p, s))
        .collect()
}

/// The methods with bodies that a type inherits and must forward to
/// (with the direct supertype that reaches each), as Kotlin's Java
/// backend has them: of the methods it inherits, the first by name and
/// kind, breadth-first through its supertypes, that no method seen
/// before overrides; for an interface, each, so its own subtypes see it
/// declared there; for a class, those that more than one of its direct
/// supertypes reaches, which Java would reject as ambiguous.
fn forwards(p: &Program, ty: DeclId) -> Vec<(DeclId, DeclId)> {
    let own: Vec<Key<'_>> = (members(p, ty).into_iter())
        .flat_map(|(d, _)| keys(p, d))
        .collect();
    let ups = ancestors(p, ty);
    let mut seen: HashSet<DeclId> = HashSet::new();
    let overridden = |key: Key<'_>, from: &[DeclId], seen: &mut HashSet<DeclId>| {
        for &a in from {
            for (d, _) in members(p, a) {
                if keys(p, d).contains(&key) {
                    seen.insert(d);
                }
            }
        }
    };
    for &key in &own {
        overridden(key, &ups, &mut seen);
    }
    let mut inherited: Vec<(Key<'_>, DeclId, NodeId)> = Vec::new();
    for &up in &ups {
        for (m, node) in members(p, up) {
            let d = p.decls.get(m);
            if !matches!(
                d.kind,
                DeclKind::Getter | DeclKind::Setter | DeclKind::Method
            ) || d.facts.contains(Facts::PRIVATE)
            {
                continue;
            }
            let [key] = keys(p, m)[..] else { continue };
            overridden(key, &ancestors(p, up), &mut seen);
            if !seen.insert(m) {
                continue;
            }
            if !inherited.iter().any(|(k, ..)| *k == key) {
                inherited.push((key, m, node));
            }
        }
    }
    let interface = p.decls.get(ty).kind == DeclKind::Interface;
    let direct = supers(p, ty);
    let reaches = |s: DeclId, key: Key<'_>| {
        defines(p, s, key, true)
            || ancestors(p, s)
                .into_iter()
                .any(|a| defines(p, a, key, true))
    };
    (inherited.into_iter())
        .filter(|&(key, _, node)| {
            has_body(p, node)
                && (interface || direct.iter().filter(|&&s| reaches(s, key)).count() > 1)
        })
        .filter_map(|(key, m, _)| Some((m, reaching(p, ty, m, key)?)))
        .collect()
}

/// The direct supertype of `ty` through which it inherits `method`: one
/// that is its owner, or reaches it with no type between them defining
/// it, as Kotlin's `findImmediateSuperReaching`.
fn reaching(p: &Program, ty: DeclId, method: DeclId, key: Key<'_>) -> Option<DeclId> {
    let owner = p.decls.get(method).owner;
    fn dig(p: &Program, ty: DeclId, owner: DeclId, key: Key<'_>, depth: usize) -> bool {
        depth < 64
            && supers(p, ty).into_iter().any(|s| {
                s == owner || (!defines(p, s, key, false) && dig(p, s, owner, key, depth + 1))
            })
    }
    supers(p, ty)
        .into_iter()
        .find(|&s| s == owner || (!defines(p, s, key, false) && dig(p, s, owner, key, 0)))
}

const PRIMITIVES: &[&str] = &["boolean", "int", "long", "double"];

/// A method's parameters' declarations.
fn param_decls(p: &Program, params: temper_tmpl::Kids) -> Vec<DeclId> {
    (p.nodes.list(params).iter())
        .filter_map(|&n| match p.nodes.get(n) {
            Node::Param { decl } => Some(decl),
            _ => None,
        })
        .collect()
}

/// Which of a method's `n` parameters, and whether its result, Java must
/// box where it overrides a method that has a reference type in their
/// places, as Kotlin's `BoxedTypeAdjustments`: `Boolean
/// decodeFromJson(…)` where `JsonAdapter<Boolean>`'s returns a `T`, which
/// javac rejects as `boolean`. An optional parameter is nullable, so
/// boxed already.
fn box_adjustments(p: &Program, ty: DeclId, method: DeclId, n: usize) -> (Vec<bool>, bool) {
    let mut params = vec![false; n];
    let mut result = false;
    let Some(own) = p.signature(method) else {
        return (params, result);
    };
    let primitive = |t: TypeId| super::boxed(p, t).is_some();
    let key = Key(p.name(method), DeclKind::Method);
    for up in ancestors(p, ty) {
        for (d, _) in members(p, up) {
            if !keys(p, d).contains(&key) {
                continue;
            }
            let Some(theirs) = p.signature(d) else {
                continue;
            };
            // Parameters line up from the end, past any `this`.
            let skip = own.params.len().saturating_sub(n);
            let their_skip = theirs.params.len().saturating_sub(n);
            for (i, adjust) in params.iter_mut().enumerate() {
                let (Some(&mine), Some(&their)) =
                    (own.params.get(skip + i), theirs.params.get(their_skip + i))
                else {
                    continue;
                };
                let optional = theirs.optional.get(their_skip + i) == Some(&true);
                if !optional && primitive(mine) && !primitive(their) {
                    *adjust = true;
                }
            }
            if primitive(own.result) && !primitive(theirs.result) && theirs.result != TypeId::VOID {
                result = true;
            }
        }
    }
    (params, result)
}

impl Translator<'_> {
    /// A static field's public getter's name, if it has one: every
    /// public static of an exported class, or another library's, but
    /// core's, has one, named as an instance field's (`getStandard()`,
    /// `isStrict()`).
    pub(super) fn static_getter(&self, field: DeclId) -> Option<String> {
        let p = self.p;
        let d = p.decls.get(field);
        let owner = p.decls.get(d.owner);
        let hidden = d.facts.contains(Facts::PRIVATE) || d.facts.contains(Facts::PROTECTED);
        let reached = match is_external(p, field) {
            true => library_of(p, field) != "core",
            false => owner.facts.contains(Facts::EXPORTED),
        };
        (!hidden && reached && owner.kind == DeclKind::Class)
            .then(|| getter_name(p.name(field), d.ty == TypeId::BOOLEAN))
    }

    /// Whether code here reads a static field itself, not through its
    /// getter: in its class (a planned one, private), or its package
    /// (one the module's initializer sets).
    pub(super) fn reads_static(&mut self, field: DeclId) -> bool {
        let p = self.p;
        if self.static_getter(field).is_none() {
            return true;
        } else if is_external(p, field) {
            return false;
        }
        let owner = p.decls.get(field).owner;
        let module = temper_tmpl::analysis::home(p, owner).unwrap_or(DeclId::NONE);
        let same_package = super::module_package(p, module) == self.package;
        match self.in_class(field) {
            true => same_package && self.class == self.name(owner),
            false => same_package,
        }
    }

    /// Whether a static field's value is given by its class's initializer.
    pub(super) fn in_class(&self, field: DeclId) -> bool {
        let owner = self.p.decls.get(field).owner;
        (self.class_statics.get(&owner)).is_some_and(|fs| fs.iter().any(|(f, _)| *f == field))
    }

    /// Plans which static fields' values their classes' own initializers
    /// give: those whose values, in the init block's order, read no
    /// module state, call no function of a module, call no method but
    /// constructors, methods, and static functions of the program's own
    /// that do the same (or core's), and read only the statics of their
    /// own class planned so before them. Running them when the JVM first
    /// uses the class, rather than at their places in the module's
    /// init, then changes nothing anyone can see.
    pub(super) fn plan_statics(&mut self, module: &Module) {
        let p = self.p;
        for item in module.all_items() {
            if let Node::Class { decl, .. } | Node::Interface { decl, .. } = p.nodes.get(item) {
                for (d, n) in members(p, decl) {
                    self.member_nodes.insert(d, n);
                }
            }
        }
        let inits = [module.init].into_iter().chain(module.test_init);
        let stmts = inits.flat_map(|init| match p.nodes.get(init) {
            Node::Block { stmts } => p.nodes.list(stmts).to_vec(),
            _ => Vec::new(),
        });
        for s in stmts {
            let Node::InitStatic { target, value } = p.nodes.get(s) else {
                continue;
            };
            let class = p.decls.get(target).owner;
            if self.unseen(class, value) {
                self.class_statics
                    .entry(class)
                    .or_default()
                    .push((target, value));
            }
        }
    }

    /// Whether a value can't tell when it's computed ([`plan_statics`]).
    fn unseen(&self, class: DeclId, value: NodeId) -> bool {
        let p = self.p;
        // A field's initializer is one expression, without statements.
        if super::needs_stmts(p, value) {
            return false;
        }
        let planned: Vec<DeclId> = (self.class_statics.get(&class))
            .map(|fs| fs.iter().map(|(f, _)| *f).collect())
            .unwrap_or_default();
        let mut todo = vec![value];
        let mut visited: HashSet<NodeId> = HashSet::new();
        let mut ok = true;
        while let Some(n) = todo.pop() {
            if !visited.insert(n) {
                continue;
            }
            let mut more = Vec::new();
            walk(p, n, &mut |id| {
                let called = match p.nodes.get(id) {
                    Node::GetModuleLet { .. } | Node::SetModuleLet { .. } => {
                        ok = false;
                        return;
                    }
                    Node::GetStatic { target } => {
                        // Core's, as `String.begin`, are constants.
                        ok &= is_external(p, target) || planned.contains(&target);
                        return;
                    }
                    Node::CallFunction { callee, .. } => callee,
                    Node::CallMethod { method, .. } if !p.is_virtual(method) => method,
                    Node::CallMethod { .. } => {
                        ok = false;
                        return;
                    }
                    Node::New { ctor, .. } => ctor,
                    _ => return,
                };
                if is_external(p, called) {
                    ok &= library_of(p, called) == "core";
                } else {
                    match self.member_nodes.get(&called) {
                        Some(&node) => more.push(node),
                        None => ok = false,
                    }
                }
            });
            todo.extend(more);
        }
        ok
    }

    /// A private helper's name for a method whose override boxes its
    /// parameters ([`box_adjustments`]): `nameUnboxed`, numbered past the
    /// class's own names. Kotlin's is the name made distinct
    /// (`name__12`); a private one is the backend's to choose.
    fn unboxed_helper(&self, own: &[(DeclId, NodeId)], name: &str) -> String {
        let taken: HashSet<String> = own.iter().map(|&(d, _)| self.name(d)).collect();
        (1..)
            .map(|n| match n {
                1 => format!("{name}Unboxed"),
                n => format!("{name}Unboxed{n}"),
            })
            .find(|n| !taken.contains(n))
            .unwrap_or_default()
    }

    /// A method's, getter's, or setter's name as Java calls it.
    pub(super) fn method_name(&self, method: DeclId) -> String {
        let p = self.p;
        let name = p.name(method);
        match p.decls.get(method).kind {
            DeclKind::Getter => {
                let boolean = p
                    .signature(method)
                    .is_some_and(|s| s.result == TypeId::BOOLEAN);
                getter_name(name, boolean)
            }
            DeclKind::Setter => setter_name(name),
            _ => self.name(method),
        }
    }

    /// The arguments of a method or constructor of the program's own,
    /// which has no overloads for those left out.
    pub(super) fn member_args(&mut self, args: &[NodeId]) -> R<Vec<Expr>> {
        self.args(args)
    }

    /// Whether each parameter is optional.
    fn optional(&self, params: temper_tmpl::Kids) -> Vec<bool> {
        let p = self.p;
        (p.nodes.list(params).iter())
            .map(|&param| {
                matches!(p.nodes.get(param), Node::Param { decl }
                    if p.decls.get(decl).facts.contains(Facts::OPTIONAL))
            })
            .collect()
    }

    /// A member's parameters, as types and names.
    fn params(&mut self, params: temper_tmpl::Kids) -> R<Vec<(String, String)>> {
        let p = self.p;
        let mut out = Vec::new();
        for &param in p.nodes.list(params) {
            if let Node::Param { decl } = p.nodes.get(param) {
                out.push((self.ty(p.decls.get(decl).ty)?, self.name(decl)));
            }
        }
        Ok(out)
    }

    fn result(&mut self, decl: DeclId) -> R<(TypeId, String)> {
        let result = match self.p.signature(decl) {
            Some(sig) => sig.result,
            None => TypeId::VOID,
        };
        Ok((result, self.ty(result)?))
    }

    /// A member's body.
    fn body(&mut self, item: NodeId, body: NodeId, result: TypeId) -> R<Vec<Stmt>> {
        self.in_function = true;
        self.begin(item);
        let mut stmts = super::reachable(self.stmts(body)?);
        if result == TypeId::VOID && stmts.last() == Some(&Stmt::Return(None)) {
            stmts.pop();
        }
        Ok(stmts)
    }

    /// A module's classes and interfaces, each with its file's name: its
    /// library's, or with `tests`, those only its tests reach, which are
    /// test sources.
    pub fn types(&mut self, module: &Module, tests: bool) -> R<Vec<tree::TypeDecl>> {
        let p = self.p;
        let mut out = Vec::new();
        self.in_tests = tests;
        // Those the library's parts synthesized are its; the rest, its
        // tests'.
        if tests {
            self.library_sams = self.sams.len();
        }
        let items = if tests {
            &module.test_items
        } else {
            &module.items
        };
        for &item in items {
            if let Node::Class { decl, .. } | Node::Interface { decl, .. } = p.nodes.get(item) {
                // A `@fun` interface is only its function type's interface.
                if p.decls.get(decl).facts.contains(Facts::FUN) {
                    continue;
                }
                // A std class connected to a JDK type (`Date`, as
                // `LocalDate`) isn't declared, as Kotlin's.
                if temper_tmpl::qname::connected_type(p, decl, crate::connected::connects_std) {
                    continue;
                }
                // A user's connected class that host code defines is the
                // library's own file of its name, as Kotlin's.
                if uses_host(p, decl, super::NAMES) {
                    if tests {
                        return not_yet(format!(
                            "host code for what only tests reach, {}",
                            p.name(decl)
                        ));
                    }
                    let path = super::host_path(p, module.decl, p.name(decl));
                    if !p.host.files.iter().any(|f| f.path == path) {
                        return not_yet(format!("{}'s host code, with no {path}", p.name(decl)));
                    }
                    continue;
                }
                out.push(self.type_decl(decl)?);
            }
        }
        self.in_global = true;
        self.this_class = DeclId::NONE;
        Ok(out)
    }

    fn type_decl(&mut self, decl: DeclId) -> R<tree::TypeDecl> {
        let p = self.p;
        let interface = p.decls.get(decl).kind == DeclKind::Interface;
        let name = self.name(decl);
        self.in_global = false;
        self.this_class = decl;
        self.imports.clear();
        self.class = name.clone();
        self.compares_strings = false;
        let own = members(p, decl);
        let taken: HashSet<String> = own.iter().map(|&(d, _)| self.name(d)).collect();
        self.compare_helper = (1..)
            .map(|n| match n {
                1 => crate::ops::COMPARE_CODE_POINTS.to_string(),
                n => format!("{}{n}", crate::ops::COMPARE_CODE_POINTS),
            })
            .find(|n| !taken.contains(n))
            .unwrap_or_default();
        let supers = (p.decls.supers(decl).iter())
            .map(|&s| self.ty(s))
            .collect::<R<Vec<_>>>()?;
        let mut fields = Vec::new();
        let mut members = Vec::new();
        let mut accessors = Vec::new();
        let mut builder = None;
        // Its constructors' bodies, where a `let` field set twice on a
        // path, a default first and then in a branch, can't be `final`.
        let ctors: Vec<NodeId> = (own.iter())
            .filter(|&&(m, _)| p.decls.get(m).kind == DeclKind::Constructor)
            .map(|&(_, node)| node)
            .collect();
        for &(m, node) in &own {
            // A std member this backend connects (`Test.bail()`,
            // `Regex.compiledFind()`) isn't declared, as Kotlin's.
            if temper_tmpl::qname::connected_member_node(p, node, crate::connected::connects_std) {
                continue;
            }
            let d = p.decls.get(m);
            let private = d.facts.contains(Facts::PRIVATE) || d.facts.contains(Facts::PROTECTED);
            let vis = if private { Vis::Private } else { Vis::Public };
            match p.nodes.get(node) {
                Node::Field { decl: f } => {
                    let ty = self.ty(d.ty)?;
                    let var = d.facts.contains(Facts::VAR);
                    let fname = self.name(f);
                    let set_twice =
                        (ctors.iter()).any(|&c| temper_tmpl::analysis::most_sets(p, c, f) > 1);
                    fields.push(MemberField {
                        vis,
                        is_static: false,
                        is_final: !var && !set_twice,
                        ty: ty.clone(),
                        name: fname.clone(),
                        value: None,
                    });
                    if !private {
                        accessors.extend(self.accessors(decl, f, &ty, &fname, var));
                    }
                }
                // Planned ones come after, in the order they're given.
                Node::StaticField { .. } if self.in_class(m) => {}
                Node::StaticField { .. } if interface => {
                    return not_yet("an interface's static field that must wait for the module");
                }
                // Set by the module's class, in the package; host code
                // reads a public one through its getter.
                Node::StaticField { .. } => fields.push(MemberField {
                    vis: match private || self.static_getter(m).is_some() {
                        true => Vis::Package,
                        false => Vis::Public,
                    },
                    is_static: true,
                    is_final: false,
                    ty: self.ty(d.ty)?,
                    name: self.name(m),
                    value: None,
                }),
                Node::Method { params, body, .. } => {
                    let ps = self.params(params)?;
                    let (result, result_ty) = self.result(m)?;
                    let result_ty = format!("{}{result_ty}", self.type_params(m)?);
                    let body = self.body(node, body, result)?;
                    let constructor = d.kind == DeclKind::Constructor;
                    if constructor && !private && ps.len() > 1 {
                        // A generic class's builder takes its type
                        // parameters: `Builder<T>`, building `Change<T>`.
                        let generics = self.type_params(decl)?.trim_end().to_string();
                        let uses: Vec<String> = (p.decls.type_params(decl).iter())
                            .map(|&t| p.name(t).to_string())
                            .collect();
                        let uses = match uses.as_slice() {
                            [] => String::new(),
                            _ => format!("<{}>", uses.join(", ")),
                        };
                        let optional = self.optional(params);
                        builder = Some(builder_class(&name, (&generics, &uses), &ps, &optional));
                    }
                    let k = self.trailing_optional(params);
                    let (mut ps, mut result_ty, mut body) = (ps, result_ty, body);
                    let name_in_java = self.method_name(m);
                    if !constructor {
                        let (boxed_params, boxed_result) = box_adjustments(p, decl, m, ps.len());
                        if boxed_result {
                            let (class, _) = super::boxed(p, result).unwrap();
                            let generics = self.type_params(m)?;
                            result_ty = format!("{generics}{class}");
                        }
                        if boxed_params.contains(&true) {
                            // A private helper keeps the primitives,
                            // which the overriding method unboxes for.
                            let helper = self.unboxed_helper(&own, &name_in_java);
                            let mut args = Vec::new();
                            let mut public_ps = Vec::new();
                            let decls = param_decls(p, params);
                            for (((ty, n), &adjust), &param) in
                                ps.iter().zip(&boxed_params).zip(&decls)
                            {
                                if adjust {
                                    let param_ty = p.decls.get(param).ty;
                                    let (class, unbox) = super::boxed(p, param_ty).unwrap();
                                    public_ps.push((class.to_string(), n.clone()));
                                    args.push(Expr::name(n.clone()).method(unbox, Vec::new()));
                                } else {
                                    public_ps.push((ty.clone(), n.clone()));
                                    args.push(Expr::name(n.clone()));
                                }
                            }
                            let (_, primitive_ty) = self.result(m)?;
                            members.push(Member {
                                vis: Vis::Private,
                                kind: MemberKind::Instance,
                                result: format!("{}{primitive_ty}", self.type_params(m)?),
                                name: helper.clone(),
                                params: std::mem::take(&mut ps),
                                body: std::mem::take(&mut body),
                            });
                            let call = Expr::name(helper).call(args);
                            body = vec![if result == TypeId::VOID {
                                Stmt::Expr(call)
                            } else {
                                Stmt::Return(Some(call))
                            }];
                            ps = public_ps;
                        }
                    }
                    members.push(Member {
                        vis: match vis {
                            Vis::Private if constructor => Vis::Package,
                            vis => vis,
                        },
                        kind: if constructor {
                            MemberKind::Constructor
                        } else if interface && !private {
                            MemberKind::Default
                        } else {
                            MemberKind::Instance
                        },
                        result: result_ty,
                        name: name_in_java,
                        params: ps,
                        body,
                    });
                    let made = members.last().unwrap().clone();
                    members.extend(member_overloads(&made, k));
                }
                Node::AbstractMethod { params, .. } => {
                    let ps = self.params(params)?;
                    let (_, result_ty) = self.result(m)?;
                    let result_ty = format!("{}{result_ty}", self.type_params(m)?);
                    members.push(Member {
                        vis: Vis::Public,
                        kind: MemberKind::Abstract,
                        result: result_ty,
                        name: self.method_name(m),
                        params: ps,
                        body: Vec::new(),
                    });
                    let k = self.trailing_optional(params);
                    let made = members.last().unwrap().clone();
                    members.extend(member_overloads(&made, k));
                }
                Node::Function { params, body, .. } => {
                    let ps = self.params(params)?;
                    let (result, result_ty) = self.result(m)?;
                    let result_ty = format!("{}{result_ty}", self.type_params(m)?);
                    let body = self.body(node, body, result)?;
                    members.push(Member {
                        // A private one is the package's, as the module's
                        // class, whose static init sets static fields, may
                        // call it (`last = named("last")`).
                        vis: match vis {
                            Vis::Private => Vis::Package,
                            vis => vis,
                        },
                        kind: MemberKind::Static,
                        result: result_ty,
                        name: self.name(m),
                        params: ps,
                        body,
                    });
                    let k = self.trailing_optional(params);
                    let made = members.last().unwrap().clone();
                    members.extend(member_overloads(&made, k));
                }
                _ => {}
            }
        }
        for (field, value) in self.class_statics.get(&decl).cloned().unwrap_or_default() {
            let d = p.decls.get(field);
            let private = d.facts.contains(Facts::PRIVATE) || d.facts.contains(Facts::PROTECTED);
            self.in_function = false;
            self.begin(value);
            let getter = self.static_getter(field).is_some();
            fields.push(MemberField {
                vis: match private || getter {
                    true => Vis::Private,
                    false => Vis::Public,
                },
                is_static: true,
                is_final: true,
                ty: self.ty(d.ty)?,
                name: self.name(field),
                value: Some(self.expr(value)?),
            });
        }
        members.extend(accessors);
        members.extend(self.static_getters(decl, &own, &members)?);
        for (method, through) in forwards(p, decl) {
            members.push(self.forward(interface, method, through)?);
        }
        let mut helpers = Vec::new();
        // Static fields the module's initializer assigns: reading one
        // runs it first, as code elsewhere, another library's tests
        // among it, may load this class before the module's.
        let deferred = !interface
            && (own.iter()).any(|&(f, node)| {
                matches!(p.nodes.get(node), Node::StaticField { .. }) && !self.in_class(f)
            });
        if deferred {
            let global = match self.in_tests {
                true => self.test_class.clone(),
                false => super::global_class(p, self.here).1,
            };
            helpers.push(format!(
                "    static {{\n        {global}.initStatics();\n    }}\n"
            ));
        }
        helpers.extend(builder);
        if self.compares_strings {
            helpers.push(crate::ops::compare_code_points(&self.compare_helper));
        }
        let generics = self.type_params(decl)?.trim_end().to_string();
        Ok(tree::TypeDecl {
            package: self.package.clone(),
            imports: self.imports.iter().cloned().collect(),
            public: self.exported(decl),
            interface,
            name,
            generics,
            supers,
            fields,
            members,
            helpers,
        })
    }

    /// The getters of a class's static fields ([`Translator::static_getter`]):
    /// `public static Policy getStandard() { return Policy.standard; }`.
    fn static_getters(
        &mut self,
        class: DeclId,
        own: &[(DeclId, NodeId)],
        members: &[Member],
    ) -> R<Vec<Member>> {
        let p = self.p;
        let class_name = self.name(class);
        let mut out = Vec::new();
        for &(field, node) in own {
            let Node::StaticField { .. } = p.nodes.get(node) else {
                continue;
            };
            let Some(name) = self.static_getter(field) else {
                continue;
            };
            if members
                .iter()
                .any(|m| m.name == name && m.params.is_empty())
            {
                return not_yet(format!(
                    "a static getter, {name}(), that a method's name takes"
                ));
            }
            out.push(Member {
                vis: Vis::Public,
                kind: MemberKind::Static,
                result: self.ty(p.decls.get(field).ty)?,
                name,
                params: Vec::new(),
                body: vec![Stmt::Return(Some(
                    Expr::name(class_name.clone()).member(self.name(field)),
                ))],
            });
        }
        Ok(out)
    }

    /// A public field's getter, and for a `var`, setter, but those the
    /// class defines itself.
    fn accessors(
        &self,
        class: DeclId,
        field: DeclId,
        ty: &str,
        name: &str,
        var: bool,
    ) -> Vec<Member> {
        let p = self.p;
        let source = p.name(field);
        let mut out = Vec::new();
        let this_field = Expr::This.member(name);
        let defined = |kind| {
            members(p, class)
                .iter()
                .any(|&(d, _)| p.decls.get(d).kind == kind && p.name(d) == source)
        };
        if !defined(DeclKind::Getter) {
            out.push(Member {
                vis: Vis::Public,
                kind: MemberKind::Instance,
                result: ty.to_string(),
                name: getter_name(source, ty == "boolean"),
                params: Vec::new(),
                body: vec![Stmt::Return(Some(this_field.clone()))],
            });
        }
        if var && !defined(DeclKind::Setter) {
            let param = format!("new{}", pascal(name));
            out.push(Member {
                vis: Vis::Public,
                kind: MemberKind::Instance,
                result: "void".into(),
                name: setter_name(source),
                params: vec![(ty.to_string(), param.clone())],
                body: vec![Stmt::Assign {
                    target: this_field,
                    value: Expr::name(param),
                }],
            });
        }
        out
    }

    /// A method that forwards to the one its type inherits through
    /// `through`: `return Through.super.name(args);`.
    fn forward(&mut self, interface: bool, method: DeclId, through: DeclId) -> R<Member> {
        let p = self.p;
        let params = match self.member_nodes.get(&method).map(|&n| p.nodes.get(n)) {
            Some(Node::Method { params, .. }) => self.params(params)?,
            // Another module's: named for their places.
            _ => match p.signature(method) {
                Some(sig) => {
                    let tys = sig.params.to_vec();
                    let mut out = Vec::new();
                    for (i, t) in tys.into_iter().enumerate() {
                        out.push((self.ty(t)?, format!("arg{i}")));
                    }
                    out
                }
                None => Vec::new(),
            },
        };
        let (result, result_ty) = self.result(method)?;
        let through = self.class_name(through);
        let args = params.iter().map(|(_, n)| Expr::name(n.clone())).collect();
        let call = Expr::name(format!("{through}.super")).method(self.method_name(method), args);
        Ok(Member {
            vis: Vis::Public,
            kind: if interface {
                MemberKind::Default
            } else {
                MemberKind::Instance
            },
            result: result_ty,
            name: self.method_name(method),
            params,
            body: vec![if result == TypeId::VOID {
                Stmt::Expr(call)
            } else {
                Stmt::Return(Some(call))
            }],
        })
    }
}

/// A constructor's `Builder`, as Kotlin's: a field and setter per
/// parameter, a flag for each primitive or nullable one, and `build()`.
fn builder_class(
    class: &str,
    (generics, uses): (&str, &str),
    params: &[(String, String)],
    optional: &[bool],
) -> String {
    let mut out = format!("    public static final class Builder{generics} {{\n");
    let diamond = if uses.is_empty() { "" } else { "<>" };
    let mut missing = Vec::new();
    for (i, (ty, name)) in params.iter().enumerate() {
        out.push_str(&format!("        {ty} {name};\n"));
        // A nullable one may be set to null, so it has a flag too, as
        // Kotlin's; an optional one may be left out.
        let optional = optional.get(i).copied().unwrap_or(false);
        let flagged =
            !optional && (PRIMITIVES.contains(&ty.as_str()) || ty.starts_with("@Nullable "));
        if optional {
        } else if flagged {
            out.push_str(&format!("        boolean {name}__set;\n"));
            missing.push((format!("!{name}__set"), name));
        } else {
            missing.push((format!("{name} == null"), name));
        }
        out.push_str(&format!(
            "\n        public Builder{uses} {name}({ty} {name}) {{\n"
        ));
        if flagged {
            out.push_str(&format!("            {name}__set = true;\n"));
        }
        out.push_str(&format!(
            "            this.{name} = {name};\n            return this;\n        }}\n\n"
        ));
    }
    let names: Vec<&str> = params.iter().map(|(_, n)| n.as_str()).collect();
    if missing.is_empty() {
        out.push_str(&format!(
            "        public {class}{uses} build() {{\n            return new {class}{diamond}({});\n        }}\n    }}\n",
            names.join(", ")
        ));
        return out;
    }
    let any: Vec<&str> = missing.iter().map(|(c, _)| c.as_str()).collect();
    out.push_str(&format!(
        "        public {class}{uses} build() {{\n            if ({}) {{\n                StringBuilder _message = new StringBuilder(\"Missing required fields:\");\n",
        any.join(" || ")
    ));
    for (cond, name) in &missing {
        out.push_str(&format!(
            "                if ({cond}) {{\n                    _message.append(\" {name}\");\n                }}\n"
        ));
    }
    out.push_str(&format!(
        "                throw new IllegalStateException(_message.toString());\n            }}\n            return new {class}{diamond}({});\n        }}\n    }}\n",
        names.join(", ")
    ));
    out
}

/// Overloads of a method or constructor whose last `k` parameters are
/// optional, as Kotlin's: each leaves out one more, passing null for it,
/// which gives its default; a constructor's call `this(…)`.
fn member_overloads(m: &Member, k: usize) -> Vec<Member> {
    (1..=k)
        .map(|j| {
            let n = m.params.len() - j;
            let mut args: Vec<Expr> = (m.params[..n].iter())
                .map(|(_, name)| Expr::name(name.clone()))
                .collect();
            args.extend((0..j).map(|_| Expr::name("null")));
            let callee = match m.kind {
                MemberKind::Constructor => "this".to_string(),
                _ => m.name.clone(),
            };
            let call = Expr::name(callee).call(args);
            Member {
                vis: m.vis,
                // An interface's overloads have bodies.
                kind: match m.kind {
                    MemberKind::Abstract => MemberKind::Default,
                    kind => kind,
                },
                result: m.result.clone(),
                name: m.name.clone(),
                params: m.params[..n].to_vec(),
                body: vec![
                    if m.result.ends_with("void") || m.kind == MemberKind::Constructor {
                        Stmt::Expr(call)
                    } else {
                        Stmt::Return(Some(call))
                    },
                ],
            }
        })
        .collect()
}
