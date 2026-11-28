//! Classes and interfaces, laid out as Kotlin's C++ backend lays them
//! out: a `struct` each, in the module's header, with every member
//! public, and its member functions defined in the module's `.cpp`.
//!
//! - A class is a `struct` deriving `std::enable_shared_from_this` of
//!   itself, or its interfaces; an interface derives
//!   `temper::core::AnyValueBase`, or its superinterfaces. Interfaces are
//!   virtual bases, where Kotlin's are only for a class of one interface,
//!   so a class of two interfaces, one extending the other, has one of
//!   each. Objects are `std::shared_ptr`s, made only by `make`.
//! - A field is a data member, and a public one's getter `get_x()` too,
//!   as Kotlin's; a field that implements an interface's property is its
//!   getter `get_x()`, and setter `set_x(value)` if it's a `var`. Getters
//!   and setters are `get_x()` and `set_x(value)`. Static fields are
//!   storage, as Kotlin's (#636): `GlobalSlot`s in a struct of the
//!   class's, `temper_statics`, which the init function sets where the
//!   class's source puts them, and a public one's accessor, `static T
//!   const &x()`, which runs the init first; static functions are static
//!   member functions.
//! - A constructor is `static std::shared_ptr<C> make(…)`, which makes
//!   the object (`self`) and runs the constructor's body on it.
//! - An interface's members are virtual; one without a body is pure
//!   (`= 0`), where Kotlin's throws when called. A class's members that
//!   implement them, or an interface's that override others, say
//!   `override`; a class's own aren't virtual, where Kotlin's are when
//!   the class has an interface. Where a class inherits a member by more
//!   than one way, and C++ would pick another than Temper or none, it has
//!   one that calls the one Temper picks, as Kotlin's does: the first
//!   found breadth-first, in `extends` order.
//! - Members that leave their object as it is are `const`, as Kotlin
//!   decides ([`mutating_members`]).
//! - `toString()` is a method, and `==` calls `eq`, as Kotlin's: no
//!   `operator==` or `operator<<`.
//!
//! In a member, `this` is `this`, or, where the member gives its object
//! out or calls something that could drop the last other `shared_ptr` to
//! it, `self`, a `shared_ptr` that holds it to the end, as Kotlin's
//! `borrow_this` does always.

use super::*;
use temper_tmpl::TypeData;
use temper_tmpl::analysis::{ancestors, nominal_decl};
use temper_tmpl::naming::NameMap;

/// The class or interface whose members are being translated.
pub(crate) struct ClassCx {
    pub decl: DeclId,
    /// Its fields' and static fields' names, which a member's locals
    /// mustn't shadow.
    pub data: HashSet<String>,
    /// Every name its members, and its supertypes', take in C++, which
    /// hide a module-level name of the same in its members.
    pub members: HashSet<String>,
}

/// A class or interface translated.
pub(crate) struct ClassOut {
    /// Its name, and its struct's definition, for the header.
    pub header: (String, String),
    /// Its static fields' and member functions' definitions.
    pub items: Vec<Item>,
}

/// Whether a node is `this`, as itself or upcast.
fn is_this(p: &Program, id: NodeId) -> bool {
    match p.nodes.get(id) {
        Node::This {} => true,
        Node::Upcast { value } => matches!(p.nodes.get(value), Node::This {}),
        _ => false,
    }
}

/// The names of members whose object may change when they run, as
/// Kotlin's `mutatingMemberNames` finds them, by name, so one name is
/// `const` or not everywhere, as overriding needs: setters, and the
/// getters of `var` fields, which Kotlin gives setters; members that
/// assign a field of `this`, or give `this` out, which a `const` member
/// can't; and members that call such a member on `this`.
pub fn mutating_members(p: &Program, names: &NameMap) -> HashSet<String> {
    let mut direct: HashSet<String> = HashSet::new();
    let mut calls: Vec<(String, String)> = Vec::new();
    fn visit(
        p: &Program,
        names: &NameMap,
        id: NodeId,
        name: &str,
        direct: &mut HashSet<String>,
        calls: &mut Vec<(String, String)>,
    ) {
        let kids = |ids: &[NodeId], direct: &mut HashSet<String>, calls: &mut Vec<_>| {
            for &kid in ids {
                visit(p, names, kid, name, direct, calls);
            }
        };
        match p.nodes.get(id) {
            Node::SetField { object, value, .. } => {
                if is_this(p, object) {
                    direct.insert(name.to_string());
                } else {
                    kids(&[object], direct, calls);
                }
                kids(&[value], direct, calls);
            }
            Node::CallMethod {
                method,
                receiver,
                args,
                ..
            } => {
                if is_this(p, receiver) {
                    calls.push((name.to_string(), names.get(method).to_string()));
                } else {
                    kids(&[receiver], direct, calls);
                }
                kids(p.nodes.list(args), direct, calls);
            }
            Node::GetField { object, .. } => {
                if !is_this(p, object) {
                    kids(&[object], direct, calls);
                }
            }
            Node::This {} => {
                direct.insert(name.to_string());
            }
            _ => kids(p.nodes.kids(id), direct, calls),
        }
    }
    for module in &p.modules {
        for item in module.all_items() {
            let (Node::Class { members, .. } | Node::Interface { members, .. }) = p.nodes.get(item)
            else {
                continue;
            };
            for &m in p.nodes.list(members) {
                match p.nodes.get(m) {
                    Node::Field { decl } if p.decls.get(decl).facts.contains(Facts::VAR) => {
                        direct.insert(names.get(decl).to_string());
                    }
                    Node::AbstractMethod { decl, .. }
                        if p.decls.get(decl).kind == DeclKind::Setter =>
                    {
                        direct.insert(names.get(decl).to_string());
                    }
                    Node::Method { decl, body, .. }
                        if p.decls.get(decl).kind != DeclKind::Constructor =>
                    {
                        let name = names.get(decl);
                        if p.decls.get(decl).kind == DeclKind::Setter {
                            direct.insert(name.to_string());
                        }
                        visit(p, names, body, name, &mut direct, &mut calls);
                    }
                    _ => {}
                }
            }
        }
    }
    loop {
        let mut changed = false;
        for (caller, callee) in &calls {
            if direct.contains(callee) && direct.insert(caller.clone()) {
                changed = true;
            }
        }
        if !changed {
            return direct;
        }
    }
}

/// Whether a member needs a `shared_ptr` to its object (`self`): where it
/// gives `this` out, or where it calls something of the program's, or
/// assigns an object to a field or module `var`, any of which could drop
/// the last other `shared_ptr` to it while it runs.
pub(super) fn holds_this(p: &Program, body: NodeId) -> bool {
    // Whether assigning a declaration could drop an object.
    let object = |d: DeclId| nominal_decl(p, p.decls.get(d).ty).is_some();
    fn visit(p: &Program, id: NodeId, found: &mut bool, object: &dyn Fn(DeclId) -> bool) {
        let kids = |ids: &[NodeId], found: &mut bool| {
            for &kid in ids {
                visit(p, kid, found, object);
            }
        };
        match p.nodes.get(id) {
            Node::GetField { object, .. } => {
                if !is_this(p, object) {
                    kids(&[object], found);
                }
            }
            Node::SetField {
                field,
                object: of,
                value,
            } => {
                *found |= object(field);
                if !is_this(p, of) {
                    kids(&[of], found);
                }
                kids(&[value], found);
            }
            Node::CallMethod {
                method,
                receiver,
                args,
                ..
            } => {
                *found |= !is_external(p, method);
                if !is_this(p, receiver) {
                    kids(&[receiver], found);
                }
                kids(p.nodes.list(args), found);
            }
            Node::CallFunction { callee: d, .. } | Node::New { ctor: d, .. } => {
                *found |= !is_external(p, d);
                kids(p.nodes.kids(id), found);
            }
            Node::SetModuleLet { target, .. } => {
                *found |= object(target);
                kids(p.nodes.kids(id), found);
            }
            Node::This {} => *found = true,
            // A lambda holds `this` past the member, so it holds `self`.
            Node::FunctionLiteral { body, .. } => {
                *found |= super::functions::uses_this(p, body);
            }
            _ => kids(p.nodes.kids(id), found),
        }
    }
    let mut found = false;
    visit(p, body, &mut found, &object);
    found
}

/// The header's structs: each declared, then each defined, a module's
/// supertypes before what extends them.
pub(super) fn structs(p: &Program, list: Vec<(DeclId, (String, String))>) -> String {
    if list.is_empty() {
        return String::new();
    }
    let mut out = String::new();
    for (decl, (name, _)) in &list {
        let names: Vec<String> = (p.decls.type_params(*decl).iter())
            .map(|&t| format!("class {}", p.name(t)))
            .collect();
        if !names.is_empty() {
            out.push_str(&format!("template <{}>\n", names.join(", ")));
        }
        out.push_str(&format!("struct {name};\n"));
    }
    fn visit(
        p: &Program,
        list: &[(DeclId, (String, String))],
        i: usize,
        done: &mut Vec<usize>,
        order: &mut Vec<usize>,
    ) {
        if done.contains(&i) {
            return;
        }
        done.push(i);
        for &sup in p.decls.supers(list[i].0) {
            if let Some(j) = nominal_decl(p, sup).and_then(|d| list.iter().position(|e| e.0 == d)) {
                visit(p, list, j, done, order);
            }
        }
        order.push(i);
    }
    let mut done = Vec::new();
    let mut order = Vec::new();
    for i in 0..list.len() {
        visit(p, &list, i, &mut done, &mut order);
    }
    for i in order {
        out.push('\n');
        out.push_str(&list[i].1.1);
    }
    out
}

/// Spells `core::` out, for the header.
fn spelled(text: &str) -> String {
    crate::tree::spell_core(text)
}

/// A member function's declaration in its struct.
fn member_line(
    f: &tree::Function,
    bare: &str,
    virtual_: bool,
    overrides: bool,
    pure: bool,
) -> String {
    let mut g = f.clone();
    g.name = bare.to_string();
    let prefix = if virtual_ && !overrides {
        "virtual "
    } else {
        ""
    };
    let mut suffix = String::new();
    if overrides {
        suffix.push_str(" override");
    }
    if pure {
        suffix.push_str(" = 0");
    }
    format!("  {prefix}{}{suffix};\n", spelled(&g.signature()))
}

/// A member's name in C++: `get_x` for a getter, `set_x` for a setter,
/// `make` for a constructor.
fn bare(p: &Program, names: &NameMap, decl: DeclId) -> String {
    let name = names.get(decl);
    match p.decls.get(decl).kind {
        DeclKind::Getter => format!("get_{name}"),
        DeclKind::Setter => format!("set_{name}"),
        DeclKind::Constructor => "make".into(),
        _ => name.to_string(),
    }
}

impl Translator<'_> {
    /// A class's or interface's struct and definitions.
    pub(super) fn class_item(&mut self, item: NodeId) -> R<ClassOut> {
        let p = self.p;
        let (decl, members, interface) = match p.nodes.get(item) {
            Node::Class { decl, members } => (decl, members, false),
            Node::Interface { decl, members } => (decl, members, true),
            _ => return not_yet("a class that isn't one"),
        };
        let class_name = self.name(decl);
        let class_ref = self.class_ref(decl);
        let template = self.template_line(decl);
        let nodes: Vec<NodeId> = p.nodes.list(members).to_vec();
        // Inherited members, each with its owner and whether it's
        // abstract, breadth-first.
        let lineage = ancestors(p, decl);
        let mut inherited: Vec<(DeclId, DeclId, bool)> = Vec::new();
        for &a in &lineage {
            let Some(&node) = self.class_nodes.get(&a) else {
                // Another library's: its members as its `uses` section
                // declares them, taken as abstract, since nothing here
                // forwards to their bodies, which C++ inherits as they are.
                if !temper_tmpl::qname::is_external(p, a) {
                    return not_yet("a supertype without its declaration");
                }
                for d in p.decls.ids() {
                    let decl = p.decls.get(d);
                    let member = matches!(
                        decl.kind,
                        DeclKind::Method | DeclKind::Getter | DeclKind::Setter
                    );
                    if member && decl.owner == a {
                        inherited.push((a, d, true));
                    }
                }
                continue;
            };
            let (Node::Class { members, .. } | Node::Interface { members, .. }) = p.nodes.get(node)
            else {
                continue;
            };
            for &m in p.nodes.list(members) {
                match p.nodes.get(m) {
                    Node::Method { decl: d, .. }
                        if p.decls.get(d).kind != DeclKind::Constructor =>
                    {
                        inherited.push((a, d, false));
                    }
                    Node::AbstractMethod { decl: d, .. } => inherited.push((a, d, true)),
                    _ => {}
                }
            }
        }
        let key = |d: DeclId| (self.names.get(d).to_string(), p.decls.get(d).kind);
        // An error type's `message` getter overrides `core::Failure`'s,
        // whether or not core's `Error` keeps its own, which goes where
        // nothing reads it.
        let failure = lineage.iter().any(|&a| self.is_error_root(a));
        let overrides = |name: &str, kind: DeclKind| {
            (inherited.iter()).any(|&(_, d, _)| key(d) == (name.to_string(), kind))
                || (failure && kind == DeclKind::Getter && name == "message")
        };
        // What its members take, and its supertypes'.
        let mut data = HashSet::new();
        let mut taken = HashSet::new();
        for &m in &nodes {
            match p.nodes.get(m) {
                Node::Field { decl: d } | Node::StaticField { decl: d } => {
                    data.insert(self.name(d));
                    taken.insert(self.name(d));
                    taken.insert(format!("get_{}", self.name(d)));
                    taken.insert(format!("set_{}", self.name(d)));
                }
                Node::Method { decl: d, .. }
                | Node::AbstractMethod { decl: d, .. }
                | Node::Function { decl: d, .. } => {
                    taken.insert(bare(p, self.names, d));
                }
                _ => {}
            }
        }
        for &(_, d, _) in &inherited {
            taken.insert(bare(p, self.names, d));
        }
        taken.insert("make".into());
        taken.insert("shared_from_this".into());
        self.class = Some(ClassCx {
            decl,
            data: data.clone(),
            members: taken,
        });
        let out = self.class_members(decl, interface, &nodes, &inherited, &overrides);
        self.class = None;
        let (lines, items) = out?;
        // Two members of one name C++ can't tell apart, or one of a name
        // C++ gives a meaning in a struct.
        let mut seen: HashSet<String> = HashSet::new();
        for (name, _) in &lines {
            if name.is_empty() {
                continue;
            }
            if name == &class_name || !seen.insert(name.clone()) {
                return not_yet(format!("two members named {name} in {class_name}"));
            }
        }
        let mut supers: Vec<String> = Vec::new();
        for &t in p.decls.supers(decl) {
            if let Some(d) = nominal_decl(p, t) {
                let args: Vec<TypeId> = p.types.args(t).to_vec();
                let mut texts = Vec::new();
                for a in args {
                    texts.push(self.ty(a)?);
                }
                let args = match texts.is_empty() {
                    true => String::new(),
                    false => format!("<{}>", texts.join(", ")),
                };
                // Core's `Error`, the runtime's `Failure`.
                let name = match self.is_error_root(d) {
                    true => "temper::core::Failure".to_string(),
                    false => self.top_name(d),
                };
                supers.push(spelled(&format!("public virtual {name}{args}")));
            }
        }
        let bases = match (supers.is_empty(), interface) {
            (false, _) => supers.join(", "),
            (true, true) => "public virtual temper::core::AnyValueBase".to_string(),
            (true, false) => format!("public std::enable_shared_from_this<{class_ref}>"),
        };
        let body: String = lines.into_iter().map(|(_, l)| l).collect();
        let template = match template.is_empty() {
            true => template,
            false => format!("{template}\n"),
        };
        let text = match body.is_empty() {
            true => format!("{template}struct {class_name} : {bases} {{}};\n"),
            false => format!("{template}struct {class_name} : {bases} {{\n{body}}};\n"),
        };
        Ok(ClassOut {
            header: (class_name, text),
            items,
        })
    }

    /// A struct's members: each's declaration, by its name, and their
    /// definitions.
    #[allow(clippy::type_complexity)]
    fn class_members(
        &mut self,
        decl: DeclId,
        interface: bool,
        nodes: &[NodeId],
        inherited: &[(DeclId, DeclId, bool)],
        overrides: &dyn Fn(&str, DeclKind) -> bool,
    ) -> R<(Vec<(String, String)>, Vec<Item>)> {
        let p = self.p;
        let class_name = self.class_ref(decl);
        let generic = !p.decls.type_params(decl).is_empty();
        let mut lines: Vec<(String, String)> = Vec::new();
        let mut items = Vec::new();
        // Fields, then static fields.
        for &m in nodes {
            if let Node::Field { decl: d } = p.nodes.get(m) {
                let ty = self.ty(p.decls.get(d).ty)?;
                let name = self.name(d);
                lines.push((name.clone(), format!("  {} {name};\n", spelled(&ty))));
            }
        }
        // Static fields' storage, in a struct of its own, as Kotlin's, and
        // a public one's accessor, which runs the init first.
        let mut slots = String::new();
        for &m in nodes {
            if let Node::StaticField { decl: d } = p.nodes.get(m) {
                if generic {
                    return not_yet("a generic class's static field");
                }
                let ty = self.ty(p.decls.get(d).ty)?;
                let name = self.name(d);
                let slot_ty = format!("{CORE}::GlobalSlot<{ty}>");
                slots.push_str(&format!("    static {} {name};\n", spelled(&slot_ty)));
                let slot = format!("{class_name}::temper_statics::{name}");
                self.slots.push((slot.clone(), self.tests_only));
                items.push(Item::Variable {
                    exported: true,
                    ty: slot_ty,
                    name: slot.clone(),
                });
                if !p.decls.get(d).facts.contains(Facts::PRIVATE) {
                    let f = tree::Function {
                        exported: true,
                        entry: true,
                        result: format!("{ty} const &"),
                        name: format!("{class_name}::{name}"),
                        body: vec![Stmt::Return(Some(Expr::name(format!("{slot}.value"))))],
                        ..tree::Function::default()
                    };
                    let mut g = f.clone();
                    g.name = name.clone();
                    lines.push((name, format!("  static {};\n", spelled(&g.signature()))));
                    items.push(Item::Function(f));
                }
            }
        }
        if !slots.is_empty() {
            lines.insert(
                0,
                (
                    "temper_statics".into(),
                    format!("  struct temper_statics {{\n{slots}  }};\n"),
                ),
            );
        }
        // Members, in order.
        let mut own: HashSet<(String, DeclKind)> = HashSet::new();
        for &m in nodes {
            // A std member this backend connects (`Test.bail()`,
            // `Regex.compiledFind()`) isn't declared, as Kotlin's.
            if temper_tmpl::qname::connected_member_node(p, m, crate::connected::connects_std) {
                continue;
            }
            match p.nodes.get(m) {
                Node::Method {
                    decl: d, params, ..
                } => {
                    let kind = p.decls.get(d).kind;
                    let name = self.name(d);
                    own.insert((name.clone(), kind));
                    // As the method it overrides takes them
                    // ([`Translator::by_value`]).
                    self.by_value = (inherited.iter())
                        .find(|&&(_, i, _)| {
                            self.names.get(i) == self.names.get(d) && p.decls.get(i).kind == kind
                        })
                        .and_then(|&(_, i, _)| p.signature(i))
                        .map(|s| {
                            (s.params.iter())
                                .map(|&t| matches!(p.types.get(t), TypeData::TypeParam { .. }))
                                .collect()
                        })
                        .unwrap_or_default();
                    let f = self.function(m);
                    self.by_value.clear();
                    let f = f?;
                    let bare = bare(p, self.names, d);
                    let k = super::trailing_optional(p, params);
                    let more = super::overloads(&f, k);
                    // A generic method's own template line.
                    let template = match self.template_line(d) {
                        t if t.is_empty() => t,
                        t if overrides(&name, kind) => {
                            return not_yet(format!("a generic method that overrides, {t}"));
                        }
                        t => format!("  {t}\n"),
                    };
                    for (i, f) in [f].into_iter().chain(more).enumerate() {
                        let line = match kind {
                            DeclKind::Constructor => {
                                let mut g = f.clone();
                                g.name = bare.clone();
                                format!("  static {};\n", spelled(&g.signature()))
                            }
                            // An overload isn't virtual.
                            _ if i > 0 => member_line(&f, &bare, false, false, false),
                            _ => member_line(&f, &bare, interface, overrides(&name, kind), false),
                        };
                        // An overload's line has no name, so the check for
                        // two members of one name passes it.
                        let key = if i > 0 { String::new() } else { bare.clone() };
                        lines.push((key, format!("{template}{line}")));
                        items.push(Item::Function(f));
                    }
                }
                Node::AbstractMethod { decl: d, params } => {
                    if super::trailing_optional(p, params) > 0 {
                        return not_yet("an abstract method's optional parameter");
                    }
                    let kind = p.decls.get(d).kind;
                    let name = self.name(d);
                    let mut ps = Vec::new();
                    for &param in p.nodes.list(params) {
                        if let Node::Param { decl: pd } = p.nodes.get(param) {
                            let mut ty = self.ty(p.decls.get(pd).ty)?;
                            if super::indirect(&ty) {
                                ty.push_str(" const &");
                            }
                            ps.push((ty, self.name(pd)));
                        }
                    }
                    let f = tree::Function {
                        result: self.decl_result(d)?,
                        params: ps,
                        constant: !self.mutating.contains(&name),
                        ..tree::Function::default()
                    };
                    let bare = bare(p, self.names, d);
                    let line = member_line(&f, &bare, true, overrides(&name, kind), true);
                    lines.push((bare, line));
                }
                Node::Function {
                    decl: d, params, ..
                } => {
                    let f = self.function(m)?;
                    let k = super::trailing_optional(p, params);
                    let more = super::overloads(&f, k);
                    let template = match self.template_line(d) {
                        t if t.is_empty() => t,
                        t => format!("  {t}\n"),
                    };
                    for (i, f) in [f].into_iter().chain(more).enumerate() {
                        let mut g = f.clone();
                        g.name = self.name(d);
                        let key = if i > 0 { String::new() } else { g.name.clone() };
                        let line = format!("  static {};\n", spelled(&g.signature()));
                        lines.push((key, format!("{template}{line}")));
                        items.push(Item::Function(f));
                    }
                }
                _ => {}
            }
        }
        // Fields' getters and setters: a public field's getter, as
        // Kotlin's, and what implements an interface's property.
        for &m in nodes {
            let Node::Field { decl: d } = p.nodes.get(m) else {
                continue;
            };
            let facts = p.decls.get(d).facts;
            let name = self.name(d);
            let ty = self.ty(p.decls.get(d).ty)?;
            let field = Expr::name("this").arrow(name.clone());
            let getter = overrides(&name, DeclKind::Getter);
            if (getter || !facts.contains(Facts::PRIVATE))
                && !own.contains(&(name.clone(), DeclKind::Getter))
            {
                own.insert((name.clone(), DeclKind::Getter));
                let f = tree::Function {
                    exported: true,
                    result: ty.clone(),
                    name: format!("{class_name}::get_{name}"),
                    constant: !self.mutating.contains(&name),
                    body: vec![Stmt::Return(Some(field.clone()))],
                    ..tree::Function::default()
                };
                let bare = format!("get_{name}");
                lines.push((bare.clone(), member_line(&f, &bare, false, getter, false)));
                items.push(Item::Function(f));
            }
            if facts.contains(Facts::VAR)
                && overrides(&name, DeclKind::Setter)
                && !own.contains(&(name.clone(), DeclKind::Setter))
            {
                own.insert((name.clone(), DeclKind::Setter));
                let mut param = "value".to_string();
                while self.class.as_ref().is_some_and(|c| c.data.contains(&param)) {
                    param.push('_');
                }
                let mut param_ty = ty.clone();
                if super::indirect(&param_ty) {
                    param_ty.push_str(" const &");
                }
                let f = tree::Function {
                    exported: true,
                    result: "void".into(),
                    name: format!("{class_name}::set_{name}"),
                    params: vec![(param_ty, param.clone())],
                    body: vec![Stmt::Set {
                        target: field,
                        value: Expr::name(param),
                    }],
                    ..tree::Function::default()
                };
                let bare = format!("set_{name}");
                lines.push((bare.clone(), member_line(&f, &bare, false, true, false)));
                items.push(Item::Function(f));
            }
        }
        if !interface {
            self.forwarders(&class_name, inherited, &own, &mut lines, &mut items)?;
        }
        Ok((lines, items))
    }

    /// Members that call the inherited ones Temper picks, where C++ would
    /// pick another, or find two: for each member the class inherits and
    /// doesn't define, the first found breadth-first, in `extends` order,
    /// with a body, unless every other it inherits of that name is one
    /// that this one overrides.
    fn forwarders(
        &mut self,
        class_name: &str,
        inherited: &[(DeclId, DeclId, bool)],
        own: &HashSet<(String, DeclKind)>,
        lines: &mut Vec<(String, String)>,
        items: &mut Vec<Item>,
    ) -> R<()> {
        let p = self.p;
        let key = |d: DeclId| (self.names.get(d).to_string(), p.decls.get(d).kind);
        let mut keys: Vec<(String, DeclKind)> = Vec::new();
        for &(_, d, _) in inherited {
            let k = key(d);
            if !own.contains(&k) && !keys.contains(&k) {
                keys.push(k);
            }
        }
        for k in keys {
            let defs: Vec<&(DeclId, DeclId, bool)> =
                inherited.iter().filter(|e| key(e.1) == k).collect();
            let Some(&&(owner, winner, _)) = defs.iter().find(|e| !e.2) else {
                continue;
            };
            let above = ancestors(p, owner);
            if defs.iter().all(|e| e.1 == winner || above.contains(&e.0)) {
                continue;
            }
            let Some(&node) = self.class_nodes.get(&owner) else {
                continue;
            };
            let (Node::Class { members, .. } | Node::Interface { members, .. }) = p.nodes.get(node)
            else {
                continue;
            };
            let method = (p.nodes.list(members).iter())
                .find(|&&m| matches!(p.nodes.get(m), Node::Method { decl, .. } if decl == winner));
            let Some(&method) = method else {
                continue;
            };
            let Node::Method { params, .. } = p.nodes.get(method) else {
                continue;
            };
            let mut ps = Vec::new();
            let mut args = Vec::new();
            for &param in p.nodes.list(params) {
                if let Node::Param { decl: pd } = p.nodes.get(param) {
                    let mut ty = self.ty(p.decls.get(pd).ty)?;
                    if super::indirect(&ty) {
                        ty.push_str(" const &");
                    }
                    let mut name = self.name(pd);
                    while self.class.as_ref().is_some_and(|c| c.data.contains(&name)) {
                        name.push('_');
                    }
                    args.push(Expr::name(name.clone()));
                    ps.push((ty, name));
                }
            }
            let result = self.decl_result(winner)?;
            let bare = bare(p, self.names, winner);
            let call = Expr::name(format!("{}::{bare}", self.top_name(owner))).call(args);
            let f = tree::Function {
                exported: true,
                name: format!("{class_name}::{bare}"),
                params: ps,
                constant: !self.mutating.contains(&k.0),
                body: vec![match result == "void" {
                    true => Stmt::Expr(call),
                    false => Stmt::Return(Some(call)),
                }],
                result,
                ..tree::Function::default()
            };
            lines.push((bare.clone(), member_line(&f, &bare, false, true, false)));
            items.push(Item::Function(f));
        }
        Ok(())
    }
}
