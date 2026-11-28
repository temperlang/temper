//! Classes and interfaces, as Kotlin Temper's Python backend lays them
//! out, and their members' uses.
//!
//! A class is a Python class with `__slots__`; an interface, one with
//! `metaclass=ABCMeta` and empty `__slots__`, its bodiless members
//! `@abstractmethod`s. A class's bases are the interfaces it extends.
//! Each field is stored as `_name`; a public one is also a `@property`
//! of its own name, with a setter if it's a `var`, so code outside the
//! class reads and writes it through the property, and the class's own
//! code reaches the storage. Getters and setters are properties too.
//! The constructor is `__init__`, its parameters passable by name; a
//! static field is a `ClassVar`, set in the module's init block at the
//! class's place in source order (after every function and class is
//! defined, so it may call any); a static function is a
//! `@staticmethod`. Private and protected members start with `_`.
//!
//! Protocols (notes/tmpl/surface.md, "Operators and protocols"): a
//! plain `toString()` is also `__str__`, and an `@operator("==")` method
//! is also `__eq__`, so `==` calls it. Where a class inherits default
//! members of one name from several interfaces, Temper's choice (the
//! first breadth-first) can differ from Python's method resolution
//! order, so the class names its pick (`a = C.a`).

use crate::translate::{R, Translator, present};
use crate::tree::{Bin, Expr, Stmt};
use std::collections::BTreeSet;
use temper_tmpl::analysis::{ancestors, as_super, is_plain_to_string, nominal_decl};
use temper_tmpl::qname::{is_external, library_of};
use temper_tmpl::{DeclId, DeclKind, Facts, Node, NodeId, TypeId};

fn not_yet<T>(what: impl Into<String>) -> R<T> {
    Err(crate::NotYet { what: what.into() })
}

/// The pyright rule a private member's use outside its class breaks,
/// where tmpl allows it: a private static's initializer, or a static's
/// initializer that calls a private static function.
const PRIVATE_USAGE: &str = "pyright: ignore[reportPrivateUsage]";
/// A static method that a supertype's of the same name doesn't fit, as
/// `@json`'s `jsonAdapter()`, which Python's checkers take for an
/// override; pyright also reads `type: ignore`.
const STATIC_OVERRIDE: &str = "type: ignore[override]";

impl Translator<'_> {
    /// Whether a declaration is a class's or interface's member.
    pub(crate) fn is_member(&self, decl: DeclId) -> bool {
        let owner = self.p.decls.get(decl).owner;
        self.p.decls.contains(owner)
            && matches!(
                self.p.decls.get(owner).kind,
                DeclKind::Class | DeclKind::Interface
            )
    }

    fn hidden(&self, decl: DeclId) -> bool {
        let facts = self.p.decls.get(decl).facts;
        facts.contains(Facts::PRIVATE) || facts.contains(Facts::PROTECTED)
    }

    /// A member's Python name: `__init__` for a constructor, a field's
    /// storage `_name`, `_set_name` for a private setter of a public
    /// getter (a method, as Kotlin's, since a property's getter and
    /// setter are equally visible), and `_name` for a private or
    /// protected member.
    pub(crate) fn member_name(&self, decl: DeclId) -> String {
        let name = self.name(decl);
        match self.p.decls.get(decl).kind {
            DeclKind::Constructor => "__init__".into(),
            DeclKind::Field if !is_external(self.p, decl) => format!("_{name}"),
            DeclKind::Setter if self.is_split_setter(decl) => format!("_set_{name}"),
            _ if self.hidden(decl) => format!("_{name}"),
            _ => name,
        }
    }

    /// Whether a setter is private or protected, and its getter isn't.
    fn is_split_setter(&self, setter: DeclId) -> bool {
        let p = self.p;
        if !self.hidden(setter) {
            return false;
        }
        let owner = p.decls.get(setter).owner;
        let Some(&node) = self.class_nodes.get(&owner) else {
            return false;
        };
        let (Node::Class { members, .. } | Node::Interface { members, .. }) = p.nodes.get(node)
        else {
            return false;
        };
        p.nodes.list(members).iter().any(|&m| match p.nodes.get(m) {
            Node::Method { decl, .. } | Node::AbstractMethod { decl, .. } => {
                p.decls.get(decl).kind == DeclKind::Getter
                    && p.name(decl) == p.name(setter)
                    && !self.hidden(decl)
            }
            _ => false,
        })
    }

    /// A class or interface as a value.
    pub(crate) fn class_ref(&mut self, decl: DeclId) -> Expr {
        match self.std_type(decl) {
            Some(name) => Expr::name(name),
            None => self.top(decl),
        }
    }

    /// Whether a class is std's, connected to a Python type, as `Date`
    /// is (`qname::connected_type`).
    pub(crate) fn connected_type(&self, decl: DeclId) -> bool {
        temper_tmpl::qname::connected_type(self.p, decl, crate::connected::connects_std)
    }

    /// The Python type a connected std class is, imported: `date` from
    /// `datetime` for `Date`, as Kotlin's.
    pub(crate) fn std_type(&mut self, decl: DeclId) -> Option<String> {
        if !self.connected_type(decl) {
            return None;
        }
        let key = temper_tmpl::qname::std_key(self.p, decl)?;
        let (from, name) = crate::connected::std_type(&key)?;
        Some(self.import_type(from, name))
    }

    /// The class or interface a type names, as a value.
    pub(crate) fn type_ref(&mut self, ty: TypeId) -> R<Expr> {
        match nominal_decl(self.p, ty) {
            // Core's by its Python class, without type arguments, as
            // Kotlin's (`dict` for a `MapBuilder`).
            Some(decl) if is_external(self.p, decl) && library_of(self.p, decl) == "core" => {
                let q = self.qname(decl);
                let Some((from, name)) = crate::translate::core_type(&q) else {
                    return not_yet(format!("a type test of {q}"));
                };
                // A builtin, bare, as `dict`, or reached through
                // `builtins` where the function's own names hide it.
                let bare = name.split('[').next().unwrap_or(name);
                if from == "builtins" {
                    return Ok(Expr::builtin(bare));
                }
                Ok(Expr::name(self.import(from, bare)))
            }
            Some(decl) => Ok(self.class_ref(decl)),
            None => not_yet(format!("a type test of {}", self.p.show_type(ty))),
        }
    }

    /// A field of an object: its storage in its class's own code, else
    /// its property.
    pub(crate) fn field(&mut self, object: Expr, field: DeclId) -> Expr {
        let owner = self.p.decls.get(field).owner;
        // Core's by its own name, in snake case, as an error's
        // `code_point`; another library's by its naming's.
        if temper_tmpl::qname::by_connection(self.p, field, crate::connected::connects_std) {
            let name = self.p.name(field);
            if temper_tmpl::qname::library_of(self.p, field) == "core" {
                return object.attr(temper_tmpl::naming::snake_case(name));
            }
            return object.attr(name);
        }
        if is_external(self.p, field) {
            return object.attr(self.name(field));
        }
        if owner == self.class || self.hidden(field) {
            object.attr(self.member_name(field))
        } else {
            object.attr(self.name(field))
        }
    }

    /// A static field, through its class.
    pub(crate) fn static_ref(&mut self, target: DeclId) -> Expr {
        let owner = self.p.decls.get(target).owner;
        let name = self.member_name(target);
        self.class_ref(owner).attr(name)
    }

    /// Whether using a member here is a private use pyright reports: a
    /// private or protected one outside its class.
    pub(crate) fn private_use_needed(&self, decl: DeclId) -> bool {
        self.hidden(decl) && self.p.decls.get(decl).owner != self.class
    }

    /// A statement that uses a private member, marked for pyright where
    /// that's outside its class.
    pub(crate) fn private_use(&self, decl: DeclId, stmt: Stmt) -> Stmt {
        if self.private_use_needed(decl) {
            Stmt::Commented(Box::new(stmt), PRIVATE_USAGE.into())
        } else {
            stmt
        }
    }

    /// Whether an expression is a setter's call, which Python writes as
    /// an assignment.
    pub(crate) fn is_setter_call(&self, expr: NodeId) -> bool {
        match self.p.nodes.get(expr) {
            Node::CallMethod { method, .. } => {
                self.p.decls.get(method).kind == DeclKind::Setter
                    && !(is_external(self.p, method)
                        && temper_tmpl::qname::library_of(self.p, method) == "core")
            }
            _ => false,
        }
    }

    /// A setter's call, as `object.name = value`.
    pub(crate) fn setter_call(&mut self, expr: NodeId) -> R<Stmt> {
        let Node::CallMethod {
            method,
            receiver,
            args,
            ..
        } = self.p.nodes.get(expr)
        else {
            return not_yet("a setter's call");
        };
        let mut ids = vec![receiver];
        ids.extend_from_slice(self.p.nodes.list(args));
        let mut args = present(self.ordered(&ids)?)?;
        let object = args.remove(0);
        if self.is_split_setter(method) {
            let call = object.attr(self.member_name(method)).call(args);
            return Ok(Stmt::Expr(call));
        }
        let [value] = <[Expr; 1]>::try_from(args).map_err(|_| crate::NotYet {
            what: "a setter's call without one argument".into(),
        })?;
        Ok(Stmt::Assign {
            target: object.attr(self.member_name(method)),
            ann: None,
            value,
        })
    }

    /// Whether a method is `==` for Python: an `@operator("==")` method
    /// of a class, taking one object of a class or interface.
    fn is_eq_hook(&self, method: DeclId) -> bool {
        let d = self.p.decls.get(method);
        let owner = d.owner;
        d.kind == DeclKind::Method
            && d.facts.contains(Facts::OPERATOR_EQ)
            && self.p.decls.contains(owner)
            && self.p.decls.get(owner).kind == DeclKind::Class
            && self
                .p
                .signature(method)
                .is_some_and(|s| s.params.len() == 1 && nominal_decl(self.p, s.params[0]).is_some())
    }

    /// A call of a method, getter, or `==` of the program's own, or of
    /// another library's than core.
    pub(crate) fn method_call(&mut self, id: NodeId) -> R<Expr> {
        let Node::CallMethod {
            method,
            receiver,
            args,
            ..
        } = self.p.nodes.get(id)
        else {
            return not_yet("a method's call");
        };
        let mut ids = vec![receiver];
        ids.extend_from_slice(self.p.nodes.list(args));
        let mut args = present(self.ordered(&ids)?)?;
        let receiver = args.remove(0);
        let name = self.member_name(method);
        Ok(match self.p.decls.get(method).kind {
            DeclKind::Getter if args.is_empty() => receiver.attr(name),
            DeclKind::Method if self.is_eq_hook(method) && args.len() == 1 => {
                Expr::bin(Bin::Eq, receiver, args.remove(0))
            }
            DeclKind::Method => receiver.attr(name).call(args),
            kind => return not_yet(format!("a {} call as a value", kind.name())),
        })
    }

    /// A std member this backend connects, by its key, in std's own
    /// program or another's.
    pub(crate) fn connected_std(&self, decl: DeclId) -> Option<String> {
        let by = temper_tmpl::qname::by_connection(self.p, decl, crate::connected::connects_std);
        by.then(|| temper_tmpl::qname::std_key(self.p, decl))
            .flatten()
    }

    /// A call of a std method this backend connects as a statement:
    /// `Test.bail()` as `raise AssertionError(str(test.messages_combined()))`,
    /// so unittest reports the test's messages as its error; and
    /// a generator's step, whose result isn't needed.
    pub(crate) fn std_statement(&mut self, expr: NodeId) -> Option<R<Stmt>> {
        let Node::CallMethod {
            method, receiver, ..
        } = self.p.nodes.get(expr)
        else {
            return None;
        };
        // A step whose result goes unused: `next(g, None)`, as Kotlin's.
        let step = [
            "core.type SafeGenerator.nextSafe()",
            "core.type Generator.next()",
        ];
        if step.contains(&self.qname(method).as_str()) {
            return Some(
                self.expr(receiver)
                    .map(|g| Stmt::Expr(Expr::builtin("next").call(vec![g, Expr::None]))),
            );
        }
        match self.connected_std(method)?.as_str() {
            // No `orelse` catches it. Kotlin's `assert False, …` would let
            // `python -O` go on past a test's failure.
            "std/testing.type Test.bail()" => Some(self.expr(receiver).map(|test| {
                let combined = test.attr("messages_combined").call(Vec::new());
                let message = Expr::builtin("str").call(vec![combined]);
                Stmt::Raise(Expr::builtin("AssertionError").call(vec![message]))
            })),
            _ => None,
        }
    }

    /// A class or interface, after those of this module it extends, each
    /// once.
    pub(crate) fn class_after_supers(
        &mut self,
        item: NodeId,
        done: &mut BTreeSet<DeclId>,
        out: &mut Vec<Stmt>,
    ) -> R<()> {
        let (Node::Class { decl, .. } | Node::Interface { decl, .. }) = self.p.nodes.get(item)
        else {
            return Ok(());
        };
        if !done.insert(decl) {
            return Ok(());
        }
        // The supertypes, and the classes their type arguments name, as
        // `JsonAdapter[Point]` for `@json`'s `PointJsonAdapter`, which
        // Python reads when it makes the class.
        let mut named = Vec::new();
        let mut types: Vec<TypeId> = self.p.decls.supers(decl).to_vec();
        let mut i = 0;
        while i < types.len() && i < 64 {
            let t = types[i];
            i += 1;
            named.extend(nominal_decl(self.p, t));
            types.extend(self.p.types.args(t));
        }
        for up in named {
            if !self.is_here(up) {
                continue;
            }
            if let Some(&node) = self.class_nodes.get(&up) {
                self.class_after_supers(node, done, out)?;
            }
        }
        let class = self.class_def(item)?;
        self.defined.insert(decl);
        out.push(class);
        Ok(())
    }

    fn class_def(&mut self, item: NodeId) -> R<Stmt> {
        let saved = self.class;
        let result = self.class_body(item);
        self.class = saved;
        result
    }

    fn class_body(&mut self, item: NodeId) -> R<Stmt> {
        let p = self.p;
        let (decl, members, interface) = match p.nodes.get(item) {
            Node::Class { decl, members } => (decl, members, false),
            Node::Interface { decl, members } => (decl, members, true),
            _ => return not_yet("a class that isn't one"),
        };
        self.class = decl;
        let members = p.nodes.list(members);
        // Bases: the supertypes, but none another one is below, which
        // Python can't order.
        let supers: Vec<(DeclId, TypeId)> = (p.decls.supers(decl).iter())
            .filter_map(|&s| Some((nominal_decl(p, s)?, s)))
            .collect();
        let mut bases = Vec::new();
        for &(s, ty) in &supers {
            let below = supers
                .iter()
                .any(|&(other, _)| other != s && ancestors(p, other).contains(&s));
            if below {
                continue;
            }
            // Another library's is imported as its other names are; core's
            // types aren't classes to extend, but for `Error`, which only
            // a library's error roots extend: the runtime's `Failure`,
            // an `Exception`.
            if is_external(p, s) && temper_tmpl::qname::library_of(p, s) == "core" {
                if temper_tmpl::declared_errors::root(p) != Some(s) {
                    return not_yet("a class extending a core type");
                }
                bases.push(self.import("temper_core", "Failure"));
                continue;
            }
            // With its type arguments, as `Holder[T]`.
            bases.push(self.ann(ty)?);
        }
        // A generic class names its type parameters, in order.
        let params = temper_tmpl::declared_errors::unchecked_type_params(p, decl);
        if !params.is_empty() {
            let mut names = Vec::new();
            for t in params {
                names.push(self.ann(p.decls.get(t).ty)?);
            }
            let generic = self.import("typing", "Generic");
            bases.push(format!("{generic}[{}]", names.join(", ")));
        }
        if interface && p.decls.supers(decl).is_empty() {
            let meta = self.import("abc", "ABCMeta");
            bases.push(format!("metaclass={meta}"));
        }

        let mut body = Vec::new();
        let mut slots = Vec::new();
        let mut fields = Vec::new();
        let mut statics = Vec::new();
        for &m in members {
            match p.nodes.get(m) {
                Node::Field { decl: f } => {
                    let name = self.member_name(f);
                    slots.push(Expr::Str(name.clone()));
                    let ann = self.ann(p.decls.get(f).ty)?;
                    fields.push(Stmt::Declare { target: name, ann });
                }
                Node::StaticField { decl: f } => {
                    let ann = self.ann(p.decls.get(f).ty)?;
                    let class_var = self.import("typing", "ClassVar");
                    statics.push(Stmt::Declare {
                        target: self.member_name(f),
                        ann: format!("{class_var}[{ann}]"),
                    });
                }
                _ => {}
            }
        }
        body.push(Stmt::Assign {
            target: Expr::name("__slots__"),
            ann: None,
            value: Expr::Tuple(slots),
        });
        body.extend(fields);
        body.extend(statics);

        // The constructor first, unless Python's default does as well.
        for &m in members {
            if let Node::Method {
                decl: d,
                params,
                body: block,
            } = p.nodes.get(m)
            {
                let empty = p.nodes.list(params).is_empty()
                    && matches!(p.nodes.get(block), Node::Block { stmts } if stmts.is_empty());
                if p.decls.get(d).kind == DeclKind::Constructor && !empty {
                    let name = self.member_name(d);
                    body.push(self.def(m, d, params, Some(block), name, Vec::new())?);
                }
            }
        }
        let mut properties = BTreeSet::new();
        for &m in members {
            // A std member this backend connects, as `Test.bail()` or
            // `Regex.compiledFound()`, isn't declared, as Kotlin's.
            if temper_tmpl::qname::connected_member_node(p, m, crate::connected::connects_std) {
                continue;
            }
            match p.nodes.get(m) {
                Node::Field { decl: f } if !self.hidden(f) => {
                    let name = self.name(f);
                    self.property(members, &name, &mut properties, &mut body)?;
                }
                Node::Method {
                    decl: d,
                    params,
                    body: block,
                } => match p.decls.get(d).kind {
                    DeclKind::Constructor => {}
                    DeclKind::Setter if self.is_split_setter(d) => {
                        let name = self.member_name(d);
                        body.push(self.def(m, d, params, Some(block), name, Vec::new())?);
                    }
                    DeclKind::Getter | DeclKind::Setter => {
                        let name = self.member_name(d);
                        self.property(members, &name, &mut properties, &mut body)?;
                    }
                    _ => {
                        let name = self.member_name(d);
                        body.push(self.def(m, d, params, Some(block), name.clone(), Vec::new())?);
                        if is_plain_to_string(p, d) {
                            body.push(self.str_hook(&name));
                        }
                        if self.is_eq_hook(d) {
                            body.push(self.eq_hook(d, &name)?);
                        }
                    }
                },
                Node::AbstractMethod { decl: d, params } => match p.decls.get(d).kind {
                    DeclKind::Getter | DeclKind::Setter => {
                        let name = self.member_name(d);
                        self.property(members, &name, &mut properties, &mut body)?;
                    }
                    _ => {
                        let decorators = vec![self.import("abc", "abstractmethod")];
                        let name = self.member_name(d);
                        body.push(self.def(m, d, params, None, name, decorators)?);
                    }
                },
                Node::Function { decl: d, .. } => {
                    let name = self.member_name(d);
                    let def = self.function(m, name, vec!["staticmethod".into()])?;
                    body.push(if self.static_clash(d) {
                        Stmt::Commented(Box::new(def), STATIC_OVERRIDE.into())
                    } else {
                        def
                    });
                }
                _ => {}
            }
        }
        if !interface {
            self.picks(decl, &mut body)?;
        }
        check_unique(&body)?;
        let name = self.name(decl);
        Ok(Stmt::Class { name, bases, body })
    }

    /// The property `name`: its getter, the class's own or a public
    /// field's, then its setter, the class's own or a public `var`
    /// field's; once.
    fn property(
        &mut self,
        members: &[NodeId],
        name: &str,
        done: &mut BTreeSet<String>,
        out: &mut Vec<Stmt>,
    ) -> R<()> {
        if !done.insert(name.to_string()) {
            return Ok(());
        }
        let p = self.p;
        let mut getter = None;
        let mut setter = None;
        let mut field = None;
        for &m in members {
            match p.nodes.get(m) {
                Node::Method { decl, .. } | Node::AbstractMethod { decl, .. } => {
                    let kind = p.decls.get(decl).kind;
                    if self.member_name(decl) != name {
                        continue;
                    }
                    match kind {
                        DeclKind::Getter => getter = Some(m),
                        DeclKind::Setter => setter = Some(m),
                        _ => {}
                    }
                }
                Node::Field { decl } if !self.hidden(decl) && self.name(decl) == name => {
                    field = Some(decl);
                }
                _ => {}
            }
        }
        let accessor = |t: &mut Self, m: NodeId, decorator: String| -> R<Stmt> {
            let mut decorators = vec![decorator];
            match t.p.nodes.get(m) {
                Node::Method {
                    decl, params, body, ..
                } => t.def(m, decl, params, Some(body), name.into(), decorators),
                Node::AbstractMethod { decl, params } => {
                    decorators.push(t.import("abc", "abstractmethod"));
                    t.def(m, decl, params, None, name.into(), decorators)
                }
                _ => not_yet("an accessor"),
            }
        };
        match (getter, field) {
            (Some(g), _) => out.push(accessor(self, g, "property".into())?),
            (None, Some(f)) => {
                let ann = self.ann(p.decls.get(f).ty)?;
                let storage = Expr::name("self").attr(self.member_name(f));
                out.push(Stmt::Def {
                    decorators: vec!["property".into()],
                    name: name.into(),
                    params: vec![("self".into(), String::new())],
                    positional: 1,
                    result: ann,
                    body: vec![Stmt::Return(Some(storage))],
                });
            }
            // A getter that fails, as Kotlin's Python writes one, since a
            // property's setter needs one; Temper code never reads it.
            (None, None) => {
                let setter_decl = match setter.map(|s| p.nodes.get(s)) {
                    Some(Node::Method { decl, .. } | Node::AbstractMethod { decl, .. }) => decl,
                    _ => return not_yet(format!("a property without a getter, {name}")),
                };
                let Some(&ty) = p.signature(setter_decl).and_then(|s| s.params.first()) else {
                    return not_yet(format!("a setter without a parameter, {name}"));
                };
                let ann = self.ann(ty)?;
                let message = Expr::Str(format!("{name} getter unavailable"));
                out.push(Stmt::Def {
                    decorators: vec!["property".into()],
                    name: name.into(),
                    params: vec![("self".into(), String::new())],
                    positional: 1,
                    result: ann,
                    body: vec![Stmt::Raise(
                        Expr::builtin("AttributeError").call(vec![message]),
                    )],
                });
            }
        }
        match (setter, field) {
            (Some(s), _) => out.push(accessor(self, s, format!("{name}.setter"))?),
            (None, Some(f)) if p.decls.get(f).facts.contains(Facts::VAR) => {
                let ann = self.ann(p.decls.get(f).ty)?;
                let storage = Expr::name("self").attr(self.member_name(f));
                out.push(Stmt::Def {
                    decorators: vec![format!("{name}.setter")],
                    name: name.into(),
                    params: vec![("self".into(), String::new()), ("value".into(), ann)],
                    positional: 2,
                    result: "None".into(),
                    body: vec![Stmt::Assign {
                        target: storage,
                        ann: None,
                        value: Expr::name("value"),
                    }],
                });
            }
            _ => {}
        }
        Ok(())
    }

    /// `__str__`, by a plain `toString()`.
    fn str_hook(&self, to_string: &str) -> Stmt {
        Stmt::Def {
            decorators: Vec::new(),
            name: "__str__".into(),
            params: vec![("self".into(), String::new())],
            positional: 1,
            result: "str".into(),
            body: vec![Stmt::Return(Some(
                Expr::name("self").attr(to_string).call(Vec::new()),
            ))],
        }
    }

    /// `__eq__`, by an `@operator("==")` method: false for an object of
    /// another type, as Python's protocol has it. A generic class's casts
    /// what `isinstance` narrows to, `_Boxed[Unknown]` to pyright, to its
    /// parameter's type, `cast('_Boxed[_T]', other)`.
    fn eq_hook(&mut self, method: DeclId, name: &str) -> R<Stmt> {
        let Some(param) = self.p.signature(method).map(|s| s.params[0]) else {
            return not_yet("an `==` method's parameter");
        };
        let class = self.type_ref(param)?;
        let other = Expr::name("other");
        let test = Expr::builtin("isinstance").call(vec![other.clone(), class]);
        let arg = if self.p.types.args(param).is_empty() {
            other
        } else {
            let (ann, _) = self.ann_raw(param)?;
            let cast = self.import("typing", "cast");
            Expr::name(cast).call(vec![Expr::Str(ann), other])
        };
        let call = Expr::name("self").attr(name).call(vec![arg]);
        Ok(Stmt::Def {
            decorators: Vec::new(),
            name: "__eq__".into(),
            params: vec![
                ("self".into(), String::new()),
                ("other".into(), "object".into()),
            ],
            positional: 2,
            result: "bool".into(),
            body: vec![Stmt::Return(Some(Expr::bin(Bin::And, test, call)))],
        })
    }

    /// For each name of members a class inherits from more than one
    /// interface, with a body in at least one, and doesn't define, the
    /// one Temper picks: the first with a body, breadth-first.
    fn picks(&mut self, class: DeclId, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        let mut own = BTreeSet::new();
        for s in out.iter() {
            if let Stmt::Def { name, .. } = s {
                own.insert(name.clone());
            }
        }
        // (name, interface, has a body), in breadth-first order.
        let mut found: Vec<(String, DeclId, bool)> = Vec::new();
        for up in ancestors(p, class) {
            let Some(&node) = self.class_nodes.get(&up) else {
                continue;
            };
            let (Node::Interface { members, .. } | Node::Class { members, .. }) = p.nodes.get(node)
            else {
                continue;
            };
            for &m in p.nodes.list(members) {
                let (decl, body) = match p.nodes.get(m) {
                    Node::Method { decl, .. } => (decl, true),
                    Node::AbstractMethod { decl, .. } => (decl, false),
                    _ => continue,
                };
                let name = self.member_name(decl);
                let entry = found.iter_mut().find(|(n, i, _)| *n == name && *i == up);
                match entry {
                    Some(e) => e.2 |= body,
                    None => found.push((name, up, body)),
                }
            }
        }
        let mut picked = BTreeSet::new();
        for (name, _, _) in &found {
            if own.contains(name) || picked.contains(name) {
                continue;
            }
            let all: Vec<_> = found.iter().filter(|(n, _, _)| n == name).collect();
            let first = all.iter().find(|(_, _, body)| *body);
            if let (true, Some(&&(_, from, _))) = (all.len() > 1, first) {
                picked.insert(name.clone());
                self.pick(from, name, out)?;
            }
        }
        Ok(())
    }

    /// Whether a type is an interface with an `@abstractmethod`, its own
    /// or one it inherits, which mypy takes as abstract.
    /// Whether a static method has the name of a supertype's static
    /// method whose signature it doesn't fit: other parameters, or a
    /// result that isn't the same or a subclass of its, as
    /// `JsonAdapter[Circle]` for `JsonAdapter[Shape]`, which Python's
    /// generics don't let vary.
    fn static_clash(&self, decl: DeclId) -> bool {
        let p = self.p;
        let me = p.decls.get(decl);
        let Some(mine) = p.types.signature(me.ty) else {
            return false;
        };
        ancestors(p, me.owner).into_iter().any(|up| {
            (p.decls.ids()).any(|d| {
                let theirs = p.decls.get(d);
                if theirs.owner != up || theirs.kind != me.kind || theirs.name != me.name {
                    return false;
                }
                let Some(sig) = p.types.signature(theirs.ty) else {
                    return false;
                };
                let narrower = match (nominal_decl(p, mine.pass), nominal_decl(p, sig.pass)) {
                    (Some(a), Some(b)) => {
                        p.types.args(mine.pass).is_empty()
                            && p.types.args(sig.pass).is_empty()
                            && ancestors(p, a).contains(&b)
                    }
                    _ => false,
                };
                sig.params != mine.params || (sig.pass != mine.pass && !narrower)
            })
        })
    }

    pub(crate) fn is_abstract(&self, ty: TypeId) -> bool {
        let p = self.p;
        let Some(class) = nominal_decl(p, ty) else {
            return false;
        };
        if p.decls.get(class).kind != DeclKind::Interface {
            return false;
        }
        // Another library's, as std's `JsonNumeric`, which lists only the
        // methods used, without bodies: abstract if it has any, as an
        // interface that isn't all default methods is.
        if !self.class_nodes.contains_key(&class) && library_of(p, class) != "core" {
            return (p.decls.ids()).any(|d| {
                let d = p.decls.get(d);
                d.owner == class && d.kind == DeclKind::Method
            });
        }
        let mut ups = ancestors(p, class);
        ups.insert(0, class);
        ups.into_iter().any(|up| {
            let Some(&node) = self.class_nodes.get(&up) else {
                return false;
            };
            let Node::Interface { members, .. } = p.nodes.get(node) else {
                return false;
            };
            (p.nodes.list(members).iter())
                .any(|&m| matches!(p.nodes.get(m), Node::AbstractMethod { .. }))
        })
    }

    /// A member a class inherits from more than one interface, as the
    /// interface `from` defines it: a method calls `from`'s, as Kotlin's;
    /// a property is `from`'s again, since its getter can't be called
    /// with a type a checker can follow.
    fn pick(&mut self, from: DeclId, name: &str, out: &mut Vec<Stmt>) -> R<()> {
        let p = self.p;
        let Some(&node) = self.class_nodes.get(&from) else {
            return Ok(());
        };
        let (Node::Interface { members, .. } | Node::Class { members, .. }) = p.nodes.get(node)
        else {
            return Ok(());
        };
        let members = p.nodes.list(members);
        let saved = self.class;
        // `from`'s type parameters, as the class gives them: `List<Int>`
        // for its `List<T>`, where it extends `Holder<Int>`.
        let class_ty = p.decls.get(saved).ty;
        let saved_args = self.type_args.clone();
        let super_args = as_super(p, class_ty, from).unwrap_or_default();
        for (&tp, &arg) in p.decls.type_params(from).iter().zip(&super_args) {
            self.type_args.insert(tp, arg);
        }
        self.class = from;
        let mut result = Ok(());
        for &m in members {
            let Node::Method {
                decl, params: ps, ..
            } = p.nodes.get(m)
            else {
                continue;
            };
            if self.member_name(decl) != name {
                continue;
            }
            if p.decls.get(decl).kind != DeclKind::Method {
                result = self.property(members, name, &mut BTreeSet::new(), out);
                break;
            }
            let mut params = vec![("self".to_string(), String::new())];
            let mut args = vec![Expr::name("self")];
            for &param in p.nodes.list(ps) {
                if let Node::Param { decl } = p.nodes.get(param) {
                    let ann = self.ann(p.decls.get(decl).ty)?;
                    params.push((self.name(decl), ann));
                    args.push(Expr::name(self.name(decl)));
                }
            }
            // `Holder[int].get(self)`, so a checker knows what it gives.
            let mut callee = self.class_ref(from);
            if let (Expr::Name(n), false) = (&callee, super_args.is_empty()) {
                let mut texts = Vec::new();
                for &a in &super_args {
                    texts.push(self.ann(a)?);
                }
                callee = Expr::Type(format!("{n}[{}]", texts.join(", ")));
            }
            let call = callee.attr(name).call(args);
            let (result_ann, body) = match p.signature(decl) {
                Some(sig) if sig.pass != TypeId::VOID => {
                    (self.ann(sig.pass)?, Stmt::Return(Some(call)))
                }
                _ => ("None".to_string(), Stmt::Expr(call)),
            };
            out.push(Stmt::Def {
                decorators: Vec::new(),
                name: name.into(),
                positional: params.len(),
                params,
                result: result_ann,
                body: vec![body],
            });
            break;
        }
        self.class = saved;
        self.type_args = saved_args;
        result
    }
}

/// Fails if two members of a class print with one name, other than a
/// property's getter and setter.
fn check_unique(body: &[Stmt]) -> R<()> {
    let mut seen = BTreeSet::new();
    for s in body {
        let name = match s {
            Stmt::Def {
                name, decorators, ..
            } if !decorators.iter().any(|d| d.ends_with(".setter")) => name,
            Stmt::Declare { target, .. } => target,
            Stmt::Assign {
                target: Expr::Name(name),
                ..
            } => name,
            _ => continue,
        };
        if !seen.insert(name.clone()) {
            return not_yet(format!("two members named {name} in Python"));
        }
    }
    Ok(())
}
