//! Classes and interfaces, as Kotlin's JS backend writes them: each a JS
//! class extending `type(...)` from `@temperlang/core`, a mixin of its
//! supertypes that copies their methods onto its prototype, and which
//! `instanceof` checks. A field is stored privately, as `#name`, with a
//! getter, and a setter if it's a `var`, unless it's private; private
//! methods, getters, and setters are `#name` too. A class's constructor
//! calls `super()` first. A static field is a module `let` before the
//! class, since the module's init block sets it, with a `static get` if
//! it's public, as Kotlin's private static field and getter; static
//! functions are plain, since the init block may call them, and so is
//! everything of an interface's, since
//! the mixin copies its methods to classes where its `#` names don't
//! reach.
//!
//! Where a class inherits more than one default body for a method,
//! getter, or setter, it forwards to the one Temper picks, as Kotlin's
//! does: the nearest, breadth-first, and among equally near ones, the
//! first in `extends` order (`C.prototype.a.call(this)`).
//!
//! For TypeScript's checker, an interface declares its abstract members
//! as fields with their types, initialized to an `undefined` cast to
//! `any`, which nothing stores: nothing makes an interface's instance,
//! and the mixin copies only its prototype. It declares the methods it
//! inherits from interfaces above it again, abstract or not, with the
//! type arguments it supplies them (`Labeled<L, T> extends Holder<T>`
//! declares `Holder`'s `held` as a `T`, its own). (TypeScript 7 no longer reads
//! a declaration without a value, `/** @type {number} */ this.area;`.)
//! TypeScript doesn't see what the mixin copies, so an upcast or downcast
//! to a type with members the value's type doesn't declare is cast
//! through `unknown`.
//!
//! A declared failure type (RFC 164) extends the one failure type above
//! it natively instead, each of the library's roots JS's `Error`, so
//! hosts' `instanceof`, `stack`, `name`, and `message` work: `class
//! WherewithError extends Error {}`, then `failureBase(WherewithError)`,
//! the runtime's marker and `name`; a root, or another sealed failure
//! interface, is a class that's never made itself; and `message` is a
//! getter over `Error`'s, read when asked.

use super::*;
use crate::names;
use temper_tmpl::Span;

impl Translator<'_> {
    /// Writes a class or interface, after its supertypes in this module
    /// that aren't written yet.
    pub(super) fn class_after_supers(
        &mut self,
        item: NodeId,
        done: &mut HashSet<DeclId>,
        body: &mut Vec<Stmt>,
    ) -> R<()> {
        let p = self.p;
        let (Node::Class { decl, .. } | Node::Interface { decl, .. }) = p.nodes.get(item) else {
            return Ok(());
        };
        // A `@fun` interface is only its function type.
        if p.decls.get(decl).facts.contains(temper_tmpl::Facts::FUN) {
            return Ok(());
        }
        if !done.insert(decl) {
            return Ok(());
        }
        // A std class connected to a JS type (`Date`) isn't declared, as
        // Kotlin's.
        if self.std_type(decl).is_some() {
            return Ok(());
        }
        for &t in p.decls.supers(decl) {
            let up = nominal_decl(p, t).and_then(|up| self.class_nodes.get(&up).copied());
            if let Some(node) = up {
                if self.home_is_here(node) {
                    self.class_after_supers(node, done, body)?;
                }
            }
        }
        if temper_tmpl::host::uses_host(p, decl, super::NAMES) {
            body.push(self.host_class(item, decl)?);
        } else {
            let class = self.class(item);
            body.append(&mut self.static_lets);
            body.push(class?);
            // A library's root failure class gets the runtime's marker and
            // its `name`.
            let root = temper_tmpl::failures::root(p);
            if temper_tmpl::failures::is_library_root(p, root, decl) {
                self.imports.insert("failureBase");
                let call = Expr::name("failureBase").call(vec![self.class_ref(decl)]);
                body.push(Stmt::Expr(call));
            }
        }
        Ok(())
    }

    /// Whether a class or interface is a declared failure type of the
    /// program's own (`temper_tmpl::failures`): a JS class extending the one
    /// failure type above it, natively, each of the library's roots
    /// extending JS's `Error`, so `instanceof`, `stack`, `name`, and
    /// `message` work as hosts expect; an interface (a root, or another
    /// sealed one) is a class that's never made itself, as other
    /// interfaces are.
    pub(super) fn is_failure_type(&self, decl: DeclId) -> bool {
        let p = self.p;
        matches!(
            p.decls.get(decl).kind,
            DeclKind::Class | DeclKind::Interface
        ) && !is_external(p, decl)
            && temper_tmpl::failures::is_failure_decl(p, temper_tmpl::failures::root(p), decl)
    }

    /// What a failure type extends: the one failure type above it, or
    /// JS's `Error` for a root, which core's `Error` is. Not
    /// yet for other supertypes, which JS's single inheritance can't give
    /// it beside that one, nor for a failure interface's abstract members,
    /// which, declared as fields for TypeScript, would hide its classes'.
    fn failure_extends(&mut self, decl: DeclId, members: temper_tmpl::Kids) -> R<Expr> {
        let p = self.p;
        let root = temper_tmpl::failures::root(p);
        let supers = p.decls.supers(decl);
        let [up] = supers else {
            return not_yet("a failure type with other supertypes");
        };
        let Some(up) = nominal_decl(p, *up) else {
            return not_yet("a failure type's supertype");
        };
        let abstract_member = (p.nodes.list(members).iter())
            .any(|&m| matches!(p.nodes.get(m), Node::AbstractMethod { .. }));
        if abstract_member {
            return not_yet("a failure interface with abstract members");
        }
        Ok(match Some(up) == root {
            true => Expr::name("Error"),
            false => self.class_ref(up),
        })
    }

    fn home_is_here(&self, node: NodeId) -> bool {
        match self.p.nodes.get(node) {
            Node::Class { decl, .. } | Node::Interface { decl, .. } => self.is_here(decl),
            _ => false,
        }
    }

    /// A class or interface, by its name, imported if it's another
    /// module's.
    pub(super) fn class_ref(&mut self, decl: DeclId) -> Expr {
        if let Some(name) = self.core_failure(decl) {
            self.import(name);
            return Expr::name(name);
        }
        match self.std_type(decl) {
            Some(name) => Expr::name(name),
            None => Expr::name(self.top(decl)),
        }
    }

    /// The runtime's class for one of core's own failure types, as
    /// `MissingKey`.
    pub(super) fn core_failure(&self, decl: DeclId) -> Option<&'static str> {
        let p = self.p;
        if !temper_tmpl::qname::is_external(p, decl)
            || temper_tmpl::qname::library_of(p, decl) != "core"
        {
            return None;
        }
        match p.name(decl) {
            "CoreError" => Some("CoreError"),
            "MissingKey" => Some("MissingKey"),
            "ParseError" => Some("ParseError"),
            "InvalidCodePoint" => Some("InvalidCodePoint"),
            "BrokenPromise" => Some("BrokenPromise"),
            _ => None,
        }
    }

    /// The JS type a std class this backend connects is, as Kotlin's:
    /// `globalThis.Date` for `Date`, past any `Date` of the module's own.
    pub(super) fn std_type(&self, decl: DeclId) -> Option<&'static str> {
        if !temper_tmpl::qname::connected_type(self.p, decl, crate::connected::connects_std) {
            return None;
        }
        match temper_tmpl::qname::std_key(self.p, decl)?.as_str() {
            "std/net.type NetResponse" => Some("globalThis.Response"),
            "std/temporal.type Date" => Some("globalThis.Date"),
            _ => None,
        }
    }

    /// Whether a member is `#name`: private (or protected, the same in a
    /// final class) to a class, and not static: the module's init block,
    /// outside the class, sets static fields, and its values may call
    /// static functions (`static standard = named(…)`).
    fn hidden(&self, member: DeclId) -> bool {
        let p = self.p;
        let d = p.decls.get(member);
        (d.facts.contains(Facts::PRIVATE) || d.facts.contains(Facts::PROTECTED))
            && !matches!(d.kind, DeclKind::StaticField | DeclKind::Function)
            && p.decls.get(d.owner).kind == DeclKind::Class
    }

    /// Where a static field of a class or interface written here is
    /// stored, as Kotlin's private static field `#name` is: a module
    /// `let`, `Class$name`, since the module's init block, outside the
    /// class, sets it. `$` is in no Temper name, so it's a fresh one. A
    /// public static field is a `static get` of it, so assigning one
    /// throws, as Kotlin's; a private one has none. `None` for a host
    /// class's.
    pub(super) fn static_storage(&self, field: DeclId) -> Option<String> {
        let owner = self.p.decls.get(field).owner;
        if temper_tmpl::host::uses_host(self.p, owner, super::NAMES) {
            return None;
        }
        Some(format!("{}${}", self.name(owner), self.name(field)))
    }

    /// A static field's value: its `let`, in its class if private, as
    /// nothing else may read it, and elsewhere its getter.
    pub(super) fn static_read(&mut self, field: DeclId) -> Expr {
        let d = self.p.decls.get(field);
        let private = d.facts.contains(Facts::PRIVATE) || d.facts.contains(Facts::PROTECTED);
        match self.static_storage(field) {
            Some(storage) if private => Expr::name(storage),
            _ => self.class_ref(d.owner).member(self.name(field)),
        }
    }

    /// A member's name as its uses write it: `#name` if it's hidden.
    pub(super) fn member_name(&self, member: DeclId) -> String {
        let name = self.name(member);
        if self.hidden(member) {
            format!("#{name}")
        } else {
            name
        }
    }

    /// A field as `object.name`: its storage, `#name`, in its own class,
    /// and elsewhere its getter or setter.
    pub(super) fn field(&mut self, field: DeclId, object: NodeId) -> R<Expr> {
        // A std field the table connects, as `Date.year`'s
        // `getUTCFullYear()`.
        let key = temper_tmpl::qname::std_key(self.p, field);
        if by_connection(self.p, field, crate::connected::connects_std)
            && key.is_some_and(|k| crate::connected::lookup(&k).is_some())
        {
            return self.connected(field, &[object]);
        }
        let object = self.expr(object)?;
        // Core's, as a pair's `key`, by its own name.
        if by_connection(self.p, field, crate::connected::connects_std) {
            return Ok(object.member(self.p.name(field)));
        }
        let name = self.name(field);
        Ok(if self.p.decls.get(field).owner == self.class {
            object.member(format!("#{name}"))
        } else {
            object.member(name)
        })
    }

    /// A call of a method, getter, or setter of the program's own: a
    /// getter is read as a property; a setter only assigns, as a
    /// statement ([`Translator::setter_call`]).
    pub(super) fn method_call(
        &mut self,
        method: DeclId,
        receiver: NodeId,
        args: &[NodeId],
    ) -> R<Expr> {
        let receiver = self.expr(receiver)?;
        let name = self.member_name(method);
        match self.p.decls.get(method).kind {
            DeclKind::Getter => Ok(receiver.member(name)),
            DeclKind::Setter => not_yet("a setter's call as a value"),
            _ => {
                let args = self.args(args)?;
                Ok(receiver.method(name, args))
            }
        }
    }

    /// A setter's call, `receiver.name = value;`, if `expr` is one.
    pub(super) fn setter_call(&mut self, expr: NodeId) -> R<Option<Stmt>> {
        let p = self.p;
        let Node::CallMethod {
            method,
            receiver,
            args,
            ..
        } = p.nodes.get(expr)
        else {
            return Ok(None);
        };
        if by_connection(p, method, crate::connected::connects_std)
            || p.decls.get(method).kind != DeclKind::Setter
        {
            return Ok(None);
        }
        let [value] = p.nodes.list(args)[..] else {
            return not_yet("a setter's call without one value");
        };
        let target = self.expr(receiver)?.member(self.member_name(method));
        Ok(Some(Stmt::Assign {
            target,
            value: self.expr(value)?,
        }))
    }

    /// A value as another type: itself, where TypeScript sees it's one
    /// already, or cast there through `unknown`.
    pub(super) fn converted(&mut self, value: NodeId, to: TypeId) -> R<Expr> {
        let from = self.p.nodes.ty(value);
        let e = self.expr(value)?;
        if self.assignable(from, to) {
            return Ok(e);
        }
        let ty = self.ty(to)?;
        // Failure classes extend each other natively, which TypeScript sees.
        let failure = |t: TypeId| nominal_decl(self.p, t).is_some_and(|c| self.is_failure_type(c));
        if failure(from) && failure(to) {
            return Ok(Expr::Cast(ty, Box::new(e)));
        }
        Ok(Expr::Cast(
            ty,
            Box::new(Expr::Cast("unknown".into(), Box::new(e))),
        ))
    }

    /// Whether TypeScript takes a value of type `from` as a `to`: the same
    /// class or interface, or an interface whose members TypeScript sees
    /// `from` declare, by name.
    fn assignable(&self, from: TypeId, to: TypeId) -> bool {
        let p = self.p;
        // Anything is an `unknown`, an `AnyValue`.
        if from == to || p.types.non_null(to) == TypeId::ANY_VALUE {
            return true;
        }
        // A type parameter is what its bounds are, for TypeScript too.
        if let TypeData::TypeParam { decl } = p.types.get(from) {
            return p.decls.supers(decl).iter().any(|&b| self.assignable(b, to));
        }
        let (Some(from), Some(to)) = (nominal_decl(p, from), nominal_decl(p, to)) else {
            return false;
        };
        // A failure type is a JS `Error`, core's `Error`.
        if Some(to) == temper_tmpl::failures::root(p) && self.is_failure_type(from) {
            return true;
        }
        // Core's lists as arrays, and maps as maps.
        let q = |d: DeclId| self.qname(d);
        if matches!(
            (q(from).as_str(), q(to).as_str()),
            (
                "core.type List" | "core.type ListBuilder",
                "core.type Listed"
            ) | ("core.type Map" | "core.type MapBuilder", "core.type Mapped")
                | (
                    "core.type StringIndex" | "core.type NoStringIndex",
                    "core.type StringIndexOption"
                )
        ) {
            return true;
        }
        if p.decls.get(to).kind != DeclKind::Interface {
            return false;
        }
        match (self.seen_members(from), self.seen_members(to)) {
            (Some(from), Some(to)) => to.is_subset(&from),
            _ => false,
        }
    }

    /// The instance members TypeScript sees a class or interface of the
    /// program's own declare, by name; `None` for another library's.
    fn seen_members(&self, decl: DeclId) -> Option<HashSet<String>> {
        let p = self.p;
        let &node = self.class_nodes.get(&decl)?;
        let (Node::Class { members, .. } | Node::Interface { members, .. }) = p.nodes.get(node)
        else {
            return None;
        };
        let mut names = HashSet::new();
        for &m in p.nodes.list(members) {
            let d = match p.nodes.get(m) {
                Node::Field { decl }
                | Node::Method { decl, .. }
                | Node::AbstractMethod { decl, .. } => decl,
                _ => continue,
            };
            if !self.hidden(d) && p.decls.get(d).kind != DeclKind::Constructor {
                names.insert(self.name(d));
            }
        }
        if p.decls.get(decl).kind == DeclKind::Class {
            names.extend(self.forwarded(decl).into_iter().map(|m| self.name(m)));
        } else {
            for (m, _) in self.inherited_members(decl, members) {
                if let Node::AbstractMethod { decl, .. } | Node::Method { decl, .. } =
                    p.nodes.get(m)
                {
                    names.insert(self.name(decl));
                }
            }
        }
        Some(names)
    }

    /// The inherited default bodies a class forwards to, each the one
    /// Temper picks where it inherits more than one: the nearest,
    /// breadth-first, and among equally near ones, the first in `extends`
    /// order.
    fn forwarded(&self, class: DeclId) -> Vec<DeclId> {
        let p = self.p;
        let mut own: HashSet<String> = HashSet::new();
        if let Some(&node) = self.class_nodes.get(&class) {
            if let Node::Class { members, .. } = p.nodes.get(node) {
                for &m in p.nodes.list(members) {
                    if let Node::Field { decl } | Node::Method { decl, .. } = p.nodes.get(m) {
                        own.insert(self.name(decl));
                    }
                }
            }
        }
        // By name and kind: the first found, and how many were.
        let mut found: Vec<((String, DeclKind), DeclId, usize)> = Vec::new();
        for up in ancestors(p, class) {
            let Some(&node) = self.class_nodes.get(&up) else {
                continue;
            };
            let Node::Interface { members, .. } = p.nodes.get(node) else {
                continue;
            };
            for &m in p.nodes.list(members) {
                let Node::Method { decl, .. } = p.nodes.get(m) else {
                    continue;
                };
                let key = (self.name(decl), p.decls.get(decl).kind);
                if own.contains(&key.0) {
                    continue;
                }
                match found.iter_mut().find(|(k, _, _)| *k == key) {
                    Some((_, _, count)) => *count += 1,
                    None => found.push((key, decl, 1)),
                }
            }
        }
        (found.into_iter())
            .filter(|&(_, _, count)| count > 1)
            .map(|(_, decl, _)| decl)
            .collect()
    }

    /// The methods, abstract or with default bodies, an interface
    /// inherits from the program's own interfaces above it, and doesn't
    /// declare, each with the interface it's from: which it declares
    /// again for TypeScript, which sees nothing through the runtime's
    /// `type(I)`, so `J extends I` has `I`'s properties and methods.
    fn inherited_members(
        &self,
        iface: DeclId,
        members: temper_tmpl::Kids,
    ) -> Vec<(NodeId, DeclId)> {
        let p = self.p;
        let decl_of = |m: NodeId| match p.nodes.get(m) {
            Node::Field { decl }
            | Node::Method { decl, .. }
            | Node::AbstractMethod { decl, .. } => Some(decl),
            _ => None,
        };
        let mut names: HashSet<String> = (p.nodes.list(members).iter())
            .filter_map(|&m| decl_of(m))
            .map(|d| self.name(d))
            .collect();
        let mut out = Vec::new();
        for up in ancestors(p, iface) {
            if up == iface {
                continue;
            }
            let Some(&node) = self.class_nodes.get(&up) else {
                continue;
            };
            let Node::Interface { members, .. } = p.nodes.get(node) else {
                continue;
            };
            let mut adds = Vec::new();
            for &m in p.nodes.list(members) {
                match p.nodes.get(m) {
                    Node::AbstractMethod { decl, .. } | Node::Method { decl, .. }
                        if !names.contains(&self.name(decl)) && !self.hidden(decl) =>
                    {
                        adds.push((m, self.name(decl)));
                    }
                    _ => {}
                }
            }
            // A getter and its setter share a name: both, then the name.
            for (m, name) in adds {
                out.push((m, up));
                names.insert(name);
            }
        }
        out
    }

    /// For each generic interface above `iface`, its type parameters'
    /// types as `iface` supplies them, written out: `T` for `Holder`'s
    /// `T` where `Labeled<L, T> extends Holder<T>`. The nearest path
    /// (breadth-first) supplies them, as [`ancestors`] finds each.
    fn supplied_type_args(&mut self, iface: DeclId) -> R<HashMap<DeclId, HashMap<DeclId, String>>> {
        let p = self.p;
        let mut out: HashMap<DeclId, HashMap<DeclId, String>> = HashMap::new();
        out.insert(iface, HashMap::new());
        let mut queue = vec![iface];
        let mut next = 0;
        let outer = std::mem::take(&mut self.type_args);
        let mut result = Ok(());
        'walk: while next < queue.len() {
            let d = queue[next];
            next += 1;
            if !p.decls.contains(d) {
                continue;
            }
            self.type_args = out[&d].clone();
            for &s in p.decls.supers(d) {
                let TypeData::Nominal { decl: up, args } = p.types.get(s) else {
                    continue;
                };
                if out.contains_key(&up) {
                    continue;
                }
                let mut supplied = HashMap::new();
                let args = p.types.list(args).to_vec();
                for (&param, &arg) in p.decls.type_params(up).iter().zip(&args) {
                    match self.ty(arg) {
                        Ok(text) => supplied.insert(param, text),
                        Err(e) => {
                            result = Err(e);
                            break 'walk;
                        }
                    };
                }
                out.insert(up, supplied);
                queue.push(up);
            }
        }
        self.type_args = outer;
        result.map(|()| out)
    }

    /// A class or interface.
    fn class(&mut self, item: NodeId) -> R<Stmt> {
        let p = self.p;
        let (Node::Class { decl, members } | Node::Interface { decl, members }) = p.nodes.get(item)
        else {
            return not_yet("a class that isn't one");
        };
        let members_kids = members;
        let outer = std::mem::replace(&mut self.class, decl);
        let body = self.class_body(decl, members);
        self.class = outer;
        self.type_args.clear();
        let members = body?;
        let extends = match self.is_failure_type(decl) {
            true => self.failure_extends(decl, members_kids)?,
            false => {
                self.imports.insert("type");
                let supers: Vec<Expr> = (p.decls.supers(decl).iter())
                    .filter_map(|&t| nominal_decl(p, t))
                    .map(|up| self.class_ref(up))
                    .collect();
                Expr::name("type").call(supers)
            }
        };
        let name_span = p.decls.get(decl).name_span;
        Ok(Stmt::Class {
            export: self.exported(decl),
            name: self.name(decl),
            name_span,
            source_name: p.name(decl).to_string(),
            templates: self.templates(decl)?,
            extends: Some(extends),
            members,
        }
        .spanned(p.nodes.span(item)))
    }

    fn class_body(&mut self, class: DeclId, members: temper_tmpl::Kids) -> R<Vec<Member>> {
        let p = self.p;
        // A failure interface's classes extend it natively, so they need
        // nothing declared again for TypeScript.
        let interface =
            p.decls.get(class).kind == DeclKind::Interface && !self.is_failure_type(class);
        let (mut fields, mut constructor, mut accessors, mut methods) =
            (Vec::new(), Vec::new(), Vec::new(), Vec::new());
        // An interface's abstract properties, declared once for a getter
        // and its setter.
        let mut declared = HashSet::new();
        let mut all: Vec<(NodeId, Option<DeclId>)> =
            p.nodes.list(members).iter().map(|&m| (m, None)).collect();
        let mut supplied = HashMap::new();
        if interface {
            let inherited = self.inherited_members(class, members);
            if !inherited.is_empty() {
                supplied = self.supplied_type_args(class)?;
            }
            all.extend(inherited.into_iter().map(|(m, up)| (m, Some(up))));
        }
        for (m, from) in all {
            let inherited = from.is_some();
            // An inherited member's types, in terms of this interface's
            // type parameters.
            self.type_args = (from.and_then(|up| supplied.get(&up).cloned())).unwrap_or_default();
            // A std member this backend connects, as `Test.bail()` or
            // `Regex.compiledFound()`, isn't declared, as Kotlin's.
            if temper_tmpl::qname::connected_member_node(p, m, crate::connected::connects_std) {
                continue;
            }
            let span = if inherited {
                Span::UNKNOWN
            } else {
                p.nodes.span(m)
            };
            match p.nodes.get(m) {
                Node::Field { decl } => {
                    let d = p.decls.get(decl);
                    let name = self.name(decl);
                    let ty = self.ty(d.ty)?;
                    let storage = format!("#{name}");
                    fields.push(Member::Field {
                        span,
                        is_static: false,
                        name: storage.clone(),
                        ty: ty.clone(),
                        value: None,
                    });
                    if self.hidden(decl) {
                        continue;
                    }
                    let source_name = p.name(decl).to_string();
                    accessors.push(Member::Method {
                        span,
                        is_static: false,
                        kind: MethodKind::Getter,
                        name: name.clone(),
                        name_span: d.name_span,
                        source_name: source_name.clone(),
                        params: Vec::new(),
                        result: Some(ty.clone()),
                        body: vec![Stmt::Return(Some(Expr::This.member(storage.clone())))],
                    });
                    if d.facts.contains(Facts::VAR) {
                        accessors.push(Member::Method {
                            span,
                            is_static: false,
                            kind: MethodKind::Setter,
                            name,
                            name_span: d.name_span,
                            source_name,
                            params: vec![("value".into(), ty)],
                            result: None,
                            body: vec![Stmt::Assign {
                                target: Expr::This.member(storage),
                                value: Expr::name("value"),
                            }],
                        });
                    }
                }
                Node::StaticField { decl } => {
                    let d = p.decls.get(decl);
                    let ty = self.ty(d.ty)?;
                    let Some(storage) = self.static_storage(decl) else {
                        return not_yet("a host class's static field");
                    };
                    self.static_lets.push(
                        Stmt::Decl {
                            export: false,
                            keyword: Keyword::Let,
                            name: storage.clone(),
                            ty: Some(ty.clone()),
                            value: None,
                        }
                        .spanned(span),
                    );
                    if d.facts.contains(Facts::PRIVATE) || d.facts.contains(Facts::PROTECTED) {
                        continue;
                    }
                    fields.push(Member::Method {
                        span,
                        is_static: true,
                        kind: MethodKind::Getter,
                        name: self.name(decl),
                        name_span: d.name_span,
                        source_name: p.name(decl).to_string(),
                        params: Vec::new(),
                        result: Some(ty),
                        body: vec![Stmt::Return(Some(Expr::name(storage)))],
                    });
                }
                // An inherited default body too, which the mixin copies.
                Node::AbstractMethod { decl, params } | Node::Method { decl, params, .. }
                    if inherited || matches!(p.nodes.get(m), Node::AbstractMethod { .. }) =>
                {
                    let name = self.name(decl);
                    if !interface || !declared.insert(name.clone()) {
                        continue;
                    }
                    let ty = match (p.decls.get(decl).kind, p.signature(decl)) {
                        (DeclKind::Getter, Some(sig)) => self.ty(sig.pass)?,
                        (DeclKind::Setter, Some(sig)) => match sig.params.first() {
                            Some(&t) => self.ty(t)?,
                            None => return not_yet("a setter without a value"),
                        },
                        (_, Some(sig)) => {
                            let result = self.ty(sig.pass)?;
                            let ps: Vec<String> = (self.params(params)?.into_iter())
                                .map(|(n, t)| format!("{n}: {t}"))
                                .collect();
                            format!("({}) => {result}", ps.join(", "))
                        }
                        (_, None) => return not_yet("a method without a signature"),
                    };
                    // A value TypeScript takes as any type, which nothing
                    // stores, since nothing makes an interface's instance.
                    let nothing = Expr::Cast("any".into(), Box::new(Expr::Undefined));
                    fields.push(Member::Field {
                        span,
                        is_static: false,
                        name,
                        ty,
                        value: Some(nothing),
                    });
                }
                Node::Method { decl, params, body } => {
                    let kids = params;
                    let d = p.decls.get(decl);
                    let kind = match d.kind {
                        DeclKind::Constructor => MethodKind::Constructor,
                        DeclKind::Getter => MethodKind::Getter,
                        DeclKind::Setter => MethodKind::Setter,
                        _ => MethodKind::Method,
                    };
                    let (params, result, mut body) = self.function_parts(m, decl, params, body)?;
                    let name = if kind == MethodKind::Constructor {
                        body.insert(0, Stmt::Expr(Expr::name("super").call(Vec::new())));
                        "constructor".to_string()
                    } else {
                        self.member_name(decl)
                    };
                    // A constructor's name isn't its source's, nor is a
                    // made one's.
                    // A constructor that only calls `super()` is the one JS
                    // makes when there's none.
                    let trivial = kind == MethodKind::Constructor
                        && params.is_empty()
                        && body.len() == 1
                        && !self.hidden(decl);
                    // Nor is a failure class's default `message`'s, made
                    // where the class is.
                    let made = kind == MethodKind::Constructor
                        || d.facts.contains(Facts::AUTO)
                        || d.facts.contains(Facts::COMPILER_MADE)
                        || (self.is_failure_type(class)
                            && d.name_span == p.decls.get(class).name_span);
                    let member = Member::Method {
                        span,
                        is_static: false,
                        kind,
                        name,
                        name_span: if made { Span::UNKNOWN } else { d.name_span },
                        source_name: p.name(decl).to_string(),
                        params,
                        result,
                        body,
                    };
                    if kind == MethodKind::Constructor {
                        if trivial {
                            continue;
                        }
                        if !self.hidden(decl) {
                            constructor.extend(self.factory(class, kids)?);
                        }
                        constructor.push(member);
                    } else {
                        methods.push(member);
                    }
                }
                Node::Function { decl, params, body } => {
                    let (params, result, body) = self.function_parts(m, decl, params, body)?;
                    methods.push(Member::Method {
                        span,
                        is_static: true,
                        kind: MethodKind::Method,
                        name: self.member_name(decl),
                        name_span: p.decls.get(decl).name_span,
                        source_name: p.name(decl).to_string(),
                        params,
                        result,
                        body,
                    });
                }
                other => return not_yet(format!("a {} member", other.kind().name())),
            }
        }
        self.type_args.clear();
        if !interface {
            for winner in self.forwarded(class) {
                methods.push(self.forwarder(winner)?);
            }
        }
        fields.extend(constructor);
        fields.extend(accessors);
        fields.extend(methods);
        Ok(fields)
    }

    /// A public constructor's factory, as Kotlin's: with two or more
    /// parameters, `static new(props)`, which takes them by name, as the
    /// source names them (`C.new({ x: 1, y: 2 })`).
    fn factory(&mut self, class: DeclId, params: temper_tmpl::Kids) -> R<Option<Member>> {
        let p = self.p;
        let params = p.nodes.list(params);
        if params.len() < 2 {
            return Ok(None);
        }
        let props = Expr::name("props");
        let mut args = Vec::new();
        let mut types = Vec::new();
        for &param in params {
            let Node::Param { decl } = p.nodes.get(param) else {
                return not_yet("a constructor's parameter");
            };
            let d = p.decls.get(decl);
            let source = p.name(decl);
            let key = if !source.is_empty()
                && (source.chars().enumerate()).all(|(i, c)| (names::RULES.identifier)(c, i == 0))
            {
                source.to_string()
            } else {
                self.name(decl)
            };
            let optional = if d.facts.contains(Facts::OPTIONAL) {
                "?"
            } else {
                ""
            };
            types.push(format!("{key}{optional}: {}", self.ty(d.ty)?));
            args.push(props.clone().member(key));
        }
        let name = self.name(class);
        // A generic class's factory is generic itself, since a static
        // member can't name the class's type parameters.
        let mut ps: Vec<(String, String)> = (self.templates(class)?.into_iter())
            .map(|t| ("@template".to_string(), t))
            .collect();
        let uses: Vec<String> = (p.decls.type_params(class).iter())
            .map(|&t| p.name(t).to_string())
            .collect();
        let result = match uses.as_slice() {
            [] => name.clone(),
            _ => format!("{name}<{}>", uses.join(", ")),
        };
        ps.push(("props".into(), format!("{{ {} }}", types.join(", "))));
        Ok(Some(Member::Method {
            span: Span::UNKNOWN,
            is_static: true,
            kind: MethodKind::Method,
            name: "new".into(),
            name_span: Span::UNKNOWN,
            source_name: String::new(),
            params: ps,
            result: Some(result),
            body: vec![Stmt::Return(Some(Expr::New(
                Box::new(Expr::name(name)),
                args,
            )))],
        }))
    }

    /// A class's member that forwards to an inherited default body:
    /// `C.prototype.a.call(this, x)`, or for a getter or setter,
    /// `Reflect.get(C.prototype, "a", this)` or `Reflect.set(…)`.
    fn forwarder(&mut self, winner: DeclId) -> R<Member> {
        let p = self.p;
        let d = p.decls.get(winner);
        let name = self.name(winner);
        let Some(&node) = self.class_nodes.get(&d.owner) else {
            return not_yet("a forwarder to another library's interface");
        };
        let Node::Interface { members, .. } = p.nodes.get(node) else {
            return not_yet("a forwarder to a class");
        };
        let params = (p.nodes.list(members).iter())
            .find_map(|&m| match p.nodes.get(m) {
                Node::Method { decl, params, .. } if decl == winner => Some(params),
                _ => None,
            })
            .map(|params| self.params(params))
            .transpose()?
            .unwrap_or_default();
        let result = match p.signature(winner) {
            Some(sig) if sig.pass != TypeId::VOID => Some(self.ty(sig.pass)?),
            _ => None,
        };
        let prototype = self.class_ref(d.owner).member("prototype");
        let this = Expr::This;
        let (kind, body) = match d.kind {
            DeclKind::Getter => (
                MethodKind::Getter,
                vec![Stmt::Return(Some(Expr::name("Reflect").method(
                    "get",
                    vec![prototype, Expr::Str(name.clone()), this],
                )))],
            ),
            DeclKind::Setter => {
                let value = Expr::name(params.first().map_or("value", |(n, _)| n.as_str()));
                (
                    MethodKind::Setter,
                    vec![Stmt::Expr(Expr::name("Reflect").method(
                        "set",
                        vec![prototype, Expr::Str(name.clone()), value, this],
                    ))],
                )
            }
            _ => {
                let mut args = vec![this];
                args.extend(params.iter().map(|(n, _)| Expr::name(n.clone())));
                let call = prototype.member(name.clone()).method("call", args);
                let stmt = if result.is_some() {
                    Stmt::Return(Some(call))
                } else {
                    Stmt::Expr(call)
                };
                (MethodKind::Method, vec![stmt])
            }
        };
        Ok(Member::Method {
            span: Span::UNKNOWN,
            is_static: false,
            kind,
            name,
            name_span: Span::UNKNOWN,
            source_name: String::new(),
            params,
            result,
            body,
        })
    }
}
