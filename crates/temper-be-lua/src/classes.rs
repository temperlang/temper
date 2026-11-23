//! Classes and interfaces, as Kotlin's Lua backend makes them, with the
//! runtime's `temper.type`: a type is a table, `local Rect =
//! temper.type("lib.Rect", Shape)`, with tables of its getters (`get`),
//! setters (`set`), and methods (`methods`), copied from its supertypes
//! when it's made, and a `constructor`; calling it makes an instance, an
//! object whose metatable finds getters, setters, and methods by name.
//! So code reads a property as `r.area`, sets one as `c.doubled = 10`,
//! and calls a method as `r:scaled(2)`, as Lua's users do.
//!
//! - **Fields** are the instance's own keys, by their names (`this.w`),
//!   which Lua reads without the metatable; Kotlin's keeps each under a
//!   private name and adds a getter, and a setter for a `var`, which reads
//!   the same to users. A field whose name a getter or setter of its class
//!   has, or a default one of an interface it implements, takes `_`s
//!   until it's its own (`x_`), with a getter of the interface's name
//!   where it implements an interface's, and a setter if it's a `var`.
//! - **Statics** are the type's keys (`Rect.unit`, `Counter.zero()`), as
//!   Kotlin's, so an exported class's static fields read current through
//!   it; a static named as one of the runtime's keys ([`TYPE_KEYS`])
//!   takes a `_`, where Kotlin's would replace the runtime's table.
//! - **Interfaces' default members** are copied into each type below
//!   them by `temper.type`, with the first supertype's winning; where
//!   that isn't the member Temper picks, the nearest by a breadth-first
//!   walk of its supertypes (Kotlin's choice), the type takes Temper's
//!   (`D.methods.a = C.methods.a`). Abstract members aren't defined
//!   (Kotlin's define them to call a runtime function that doesn't
//!   exist).
//! - **The type's name**, which `temper.instance_of` and `temper.cast_to`
//!   compare, is qualified by its module (`"lib/helpers.Point"`, with `/`
//!   as `.`), so two modules' `Point`s are different types; Kotlin's is
//!   the bare name, so `is` mistakes one for the other.
//! - **A plain `toString()`** is also the instance's `__tostring`, so
//!   `tostring(r)` and `print(r)` show it: the module's `with_tostring`
//!   gives the class's instances a metatable of their own, the runtime's
//!   with `__tostring` added. Kotlin's has no `__tostring`.
//! - **LuaCATS**: each type is a LuaLS class of its module's name
//!   (`---@class lib.Rect: lib.Shape`), with its fields, properties, and
//!   methods as `---@field`s, and its table another (`lib.Rect.type`),
//!   with its static fields and its call's result.

use crate::NotYet;
use crate::ops;
use crate::translate::{R, Translator, require_path};
use crate::tree::{Bin, Expr, Signature, Stmt};
use std::collections::{HashMap, HashSet};
use temper_tmpl::analysis::{ancestors, home, is_plain_to_string, nominal_decl, walk};
use temper_tmpl::qname::{by_connection, is_external, library_of};
use temper_tmpl::{DeclId, DeclKind, Facts, Node, NodeId, Program, TypeId};

/// The keys of a type's table that the runtime uses.
pub const TYPE_KEYS: &[&str] = &["constructor", "get", "methods", "set", "super", "typename"];

/// The module helper that gives a class's instances a `__tostring`.
pub const WITH_TOSTRING: &str = "with_tostring";

/// Its definition, printed once in a module that needs it. It reaches
/// Lua's functions through `_G`, which no Temper name hides.
const WITH_TOSTRING_DEF: &[&str] = &[
    "---Gives a class's instances a `__tostring` that calls their `toString`.",
    "---@param class temper.Type",
    "local function with_tostring(class)",
    "  local construct = class.constructor",
    "  ---@type table?",
    "  local meta = nil",
    "  class.constructor = function(this, ...)",
    "    if meta == nil then",
    "      meta = {}",
    "      for k, v in _G.pairs(_G.getmetatable(this)) do",
    "        meta[k] = v",
    "      end",
    "      meta.__tostring = function(self)",
    "        return self:toString()",
    "      end",
    "    end",
    "    _G.setmetatable(this, meta)",
    "    construct(this, ...)",
    "  end",
    "end",
];

/// The definition of `with_tostring`.
pub fn with_tostring_def() -> Stmt {
    Stmt::Raw(WITH_TOSTRING_DEF.iter().map(|s| s.to_string()).collect())
}

/// Which table of a type a member's function goes in.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
enum Table {
    Get,
    Set,
    Methods,
}

impl Table {
    fn key(self) -> &'static str {
        match self {
            Table::Get => "get",
            Table::Set => "set",
            Table::Methods => "methods",
        }
    }

    fn of(kind: DeclKind) -> Option<Table> {
        match kind {
            DeclKind::Getter => Some(Table::Get),
            DeclKind::Setter => Some(Table::Set),
            DeclKind::Method => Some(Table::Methods),
            _ => None,
        }
    }
}

/// What the translation knows of the program's classes and interfaces.
#[derive(Clone, Debug, Default)]
pub struct Types {
    /// Each type's node, by its declaration.
    pub nodes: HashMap<DeclId, NodeId>,
    /// Each field's key in its instances, where it isn't its name.
    pub field_keys: HashMap<DeclId, String>,
    /// Fields that implement an interface's default getter or setter, so
    /// their class defines a getter and setter of their own for them.
    pub accessed: HashSet<DeclId>,
}

/// The class or interface that owns a declaration, if one does.
pub fn owner_type(p: &Program, decl: DeclId) -> Option<DeclId> {
    let owner = p.decls.get(decl).owner;
    (p.decls.contains(owner)
        && matches!(
            p.decls.get(owner).kind,
            DeclKind::Class | DeclKind::Interface
        ))
    .then_some(owner)
}

/// The member nodes of a type's node.
fn member_nodes(p: &Program, node: NodeId) -> Vec<NodeId> {
    match p.nodes.get(node) {
        Node::Class { members, .. } | Node::Interface { members, .. } => {
            p.nodes.list(members).to_vec()
        }
        _ => Vec::new(),
    }
}

/// The getters, setters, and methods with bodies of a type, as (table,
/// declaration).
fn defined(p: &Program, types: &Types, ty: DeclId) -> Vec<(Table, DeclId)> {
    let Some(&node) = types.nodes.get(&ty) else {
        return Vec::new();
    };
    member_nodes(p, node)
        .into_iter()
        .filter_map(|m| match p.nodes.get(m) {
            Node::Method { decl, .. } => Table::of(p.decls.get(decl).kind).map(|t| (t, decl)),
            _ => None,
        })
        .collect()
}

impl Types {
    pub fn of(p: &Program, name: &dyn Fn(DeclId) -> String) -> Types {
        let mut types = Types::default();
        for module in &p.modules {
            for &item in &module.items {
                if let Node::Class { decl, .. } | Node::Interface { decl, .. } = p.nodes.get(item) {
                    types.nodes.insert(decl, item);
                }
            }
        }
        let classes: Vec<(DeclId, NodeId)> = types.nodes.iter().map(|(&d, &n)| (d, n)).collect();
        for (class, node) in classes {
            // Names its own getters and setters, and its supertypes'
            // default ones, have; and every name a member has.
            let mut accessors: HashSet<String> = HashSet::new();
            let mut inherited: HashSet<String> = HashSet::new();
            let mut taken: HashSet<String> = HashSet::new();
            for (table, d) in defined(p, &types, class) {
                if table != Table::Methods {
                    accessors.insert(name(d));
                }
            }
            for up in ancestors(p, class) {
                for (table, d) in defined(p, &types, up) {
                    if table != Table::Methods {
                        inherited.insert(name(d));
                    }
                }
                if let Some(&n) = types.nodes.get(&up) {
                    for m in temper_tmpl::analysis::members(p, n) {
                        taken.insert(name(m));
                    }
                }
            }
            for m in temper_tmpl::analysis::members(p, node) {
                taken.insert(name(m));
            }
            for m in member_nodes(p, node) {
                let Node::Field { decl } = p.nodes.get(m) else {
                    continue;
                };
                let n = name(decl);
                let own = accessors.contains(&n);
                if !own && !inherited.contains(&n) {
                    continue;
                }
                let mut key = format!("{n}_");
                while taken.contains(&key) {
                    key.push('_');
                }
                taken.insert(key.clone());
                types.field_keys.insert(decl, key);
                if !own {
                    types.accessed.insert(decl);
                }
            }
        }
        types
    }
}

impl Translator<'_> {
    /// A type's table, as code refers to it.
    pub(crate) fn type_ref(&self, ty: DeclId) -> Expr {
        self.access(ty)
    }

    /// A type's name, for the runtime and for LuaLS: its module's path,
    /// with `.` for `/`, and its name.
    pub(crate) fn lua_type(&self, ty: DeclId) -> String {
        let module = home(self.p, ty).unwrap_or(ty);
        format!(
            "{}.{}",
            require_path(self.p, module).replace('/', "."),
            self.name(ty)
        )
    }

    /// A static member's key in its type's table.
    pub(crate) fn static_key(&self, decl: DeclId) -> String {
        let mut name = self.name(decl);
        while TYPE_KEYS.contains(&name.as_str()) {
            name.push('_');
        }
        name
    }

    /// A field's key in its instances.
    pub(crate) fn field_key(&self, field: DeclId) -> String {
        match self.types.field_keys.get(&field) {
            Some(key) => key.clone(),
            None => self.name(field),
        }
    }

    /// Whether a type is one this module defines, whose LuaCATS class it
    /// declares.
    pub(crate) fn is_program_type(&self, ty: DeclId) -> bool {
        self.types.nodes.contains_key(&ty)
    }

    /// A method's LuaCATS type, as a field of its type's class.
    fn fun_type(&self, decl: DeclId, params: &[NodeId], this: &str) -> R<String> {
        let p = self.p;
        let mut ps = vec![format!("self: {this}")];
        for &param in params {
            if let Node::Param { decl } = p.nodes.get(param) {
                let optional = if p.decls.get(decl).facts.contains(Facts::OPTIONAL) {
                    "?"
                } else {
                    ""
                };
                ps.push(format!(
                    "{}{optional}: {}",
                    self.name(decl),
                    self.ty(p.decls.get(decl).ty)?
                ));
            }
        }
        let result = match p.signature(decl) {
            Some(sig) if sig.pass != TypeId::VOID => format!(": {}", self.ty(sig.pass)?),
            _ => String::new(),
        };
        Ok(format!("fun({}){result}", ps.join(", ")))
    }

    /// A class's or interface's definition: its LuaCATS classes, its
    /// table, its members' functions, the defaults it takes from other
    /// than its first supertype, and for a class with a plain
    /// `toString()`, its `__tostring`. `local` if its name isn't declared
    /// before.
    pub(crate) fn type_def(&mut self, item: NodeId, local: bool) -> R<Vec<Stmt>> {
        let p = self.p;
        let (decl, is_class) = match p.nodes.get(item) {
            Node::Class { decl, .. } => (decl, true),
            Node::Interface { decl, .. } => (decl, false),
            _ => {
                return Err(NotYet {
                    what: "a type that isn't one".into(),
                });
            }
        };
        let q = self.lua_type(decl);
        let table = crate::tree::expr(&self.type_ref(decl), 0);
        // A std member this backend connects, as all of std's on Lua,
        // isn't declared, as Kotlin's.
        let members: Vec<NodeId> = (member_nodes(p, item).into_iter())
            .filter(|&m| {
                !temper_tmpl::qname::connected_member_node(p, m, crate::connected::connects_std)
            })
            .collect();
        let supers: Vec<DeclId> = (p.decls.supers(decl).iter())
            .filter_map(|&s| nominal_decl(p, s))
            .collect();

        // The instance's LuaCATS class.
        let mut doc = vec![match supers.is_empty() {
            true => format!("@class {q}"),
            false => format!(
                "@class {q}: {}",
                supers
                    .iter()
                    .map(|&s| self.lua_type(s))
                    .collect::<Vec<_>>()
                    .join(", ")
            ),
        }];
        let mut statics = vec![format!("@class {q}.type: temper.Type")];
        let mut properties: HashSet<String> = HashSet::new();
        for &m in &members {
            match p.nodes.get(m) {
                Node::Field { decl: f } => {
                    let ty = self.ty(p.decls.get(f).ty)?;
                    if self.types.accessed.contains(&f) {
                        properties.insert(self.name(f));
                        doc.push(format!("@field {} {ty}", self.name(f)));
                    }
                    doc.push(format!("@field {} {ty}", self.field_key(f)));
                }
                Node::StaticField { decl: f } => {
                    let ty = self.ty(p.decls.get(f).ty)?;
                    statics.push(format!("@field {} {ty}", self.static_key(f)));
                }
                Node::Method {
                    decl: d, params, ..
                }
                | Node::AbstractMethod { decl: d, params } => {
                    let kind = p.decls.get(d).kind;
                    let params = p.nodes.list(params);
                    match kind {
                        DeclKind::Getter | DeclKind::Setter => {
                            let ty = match (kind, p.signature(d)) {
                                (DeclKind::Getter, Some(sig)) => sig.pass,
                                (_, Some(sig)) => sig.params.first().copied().unwrap_or_default(),
                                _ => TypeId::NONE,
                            };
                            if properties.insert(self.name(d)) {
                                doc.push(format!("@field {} {}", self.name(d), self.ty(ty)?));
                            }
                        }
                        DeclKind::Method => {
                            let f = self.fun_type(d, params, &q)?;
                            doc.push(format!("@field {} {f}", self.name(d)));
                        }
                        _ => {}
                    }
                }
                _ => {}
            }
        }
        if is_class {
            statics.push(format!("@operator call: {q}"));
        }

        let mut out = vec![Stmt::Doc(doc), Stmt::Blank, Stmt::Doc(statics)];
        let mut args = vec![Expr::Str(q.clone())];
        args.extend(supers.iter().map(|&s| self.type_ref(s)));
        self.uses_temper = true;
        let made = ops::temper().method("type", args);
        out.push(match local {
            true => Stmt::Local {
                name: table.clone(),
                ty: None,
                value: Some(made),
            },
            false => Stmt::Assign {
                target: self.type_ref(decl),
                value: made,
            },
        });

        let mut to_string = false;
        for &m in &members {
            match p.nodes.get(m) {
                Node::Method {
                    decl: d,
                    params,
                    body,
                } => {
                    let kind = p.decls.get(d).kind;
                    let name = match kind {
                        DeclKind::Constructor => format!("{table}.constructor"),
                        _ => match Table::of(kind) {
                            Some(t) => format!("{table}.{}.{}", t.key(), self.name(d)),
                            None => {
                                return Err(NotYet {
                                    what: format!("a {} member", kind.name()),
                                });
                            }
                        },
                    };
                    to_string |= is_class && is_plain_to_string(p, d);
                    let result = match kind {
                        DeclKind::Constructor | DeclKind::Setter => None,
                        _ => match p.signature(d) {
                            Some(sig) if sig.pass != TypeId::VOID => Some(sig.pass),
                            _ => None,
                        },
                    };
                    out.push(self.function_def(
                        m,
                        name,
                        false,
                        Some(&q),
                        p.nodes.list(params),
                        body,
                        result,
                    )?);
                }
                Node::Function {
                    decl: d,
                    params,
                    body,
                } => {
                    let name = format!("{table}.{}", self.static_key(d));
                    let result = match p.signature(d) {
                        Some(sig) if sig.pass != TypeId::VOID => Some(sig.pass),
                        _ => None,
                    };
                    out.push(self.function_def(
                        m,
                        name,
                        false,
                        None,
                        p.nodes.list(params),
                        body,
                        result,
                    )?);
                }
                _ => {}
            }
        }
        // Fields that implement an interface's default getter or setter.
        for &m in &members {
            let Node::Field { decl: f } = p.nodes.get(m) else {
                continue;
            };
            if !self.types.accessed.contains(&f) {
                continue;
            }
            let (name, key) = (self.name(f), self.field_key(f));
            let ty = self.ty(p.decls.get(f).ty)?;
            let this = || Expr::name("this").member(key.clone());
            out.push(Stmt::Function {
                local: false,
                name: format!("{table}.get.{name}"),
                signature: Signature {
                    params: vec![("this".into(), q.clone())],
                    result: Some(ty.clone()),
                },
                body: vec![Stmt::Return(Some(this()))],
            });
            if !p.decls.get(f).facts.contains(Facts::VAR) {
                continue;
            }
            out.push(Stmt::Function {
                local: false,
                name: format!("{table}.set.{name}"),
                signature: Signature {
                    params: vec![("this".into(), q.clone()), ("value".into(), ty)],
                    result: None,
                },
                body: vec![Stmt::Assign {
                    target: this(),
                    value: Expr::name("value"),
                }],
            });
        }
        // Defaults that the runtime's copying doesn't pick.
        let mut fixes = Vec::new();
        for (table_kind, name, from) in self.misplaced_defaults(decl) {
            let source = self
                .type_ref(from)
                .member(table_kind.key())
                .member(name.clone());
            fixes.push(Stmt::Assign {
                target: self.type_ref(decl).member(table_kind.key()).member(name),
                value: source,
            });
        }
        if !fixes.is_empty() {
            out.push(Stmt::Blank);
            out.extend(fixes);
        }
        if to_string {
            out.push(Stmt::Blank);
            out.push(Stmt::Expr(
                Expr::name(WITH_TOSTRING).call(vec![self.type_ref(decl)]),
            ));
        }
        Ok(out)
    }

    /// The default getters, setters, and methods a type gets from its
    /// supertypes that `temper.type`'s copying doesn't give it, as
    /// (table, name, the type that defines it). Temper's is the nearest,
    /// by a breadth-first walk of its supertypes, first by their order
    /// ([`Translator::chosen`]); the runtime's, the first supertype's that
    /// has one, which is that supertype's choice, as each type's table
    /// holds its own after these.
    fn misplaced_defaults(&self, ty: DeclId) -> Vec<(Table, String, DeclId)> {
        let p = self.p;
        let own: HashSet<(Table, String)> = defined(p, &self.types, ty)
            .into_iter()
            .map(|(t, d)| (t, self.name(d)))
            .collect();
        let mut names: Vec<(Table, String)> = Vec::new();
        for up in ancestors(p, ty) {
            for (t, d) in defined(p, &self.types, up) {
                let key = (t, self.name(d));
                if !own.contains(&key) && !names.contains(&key) {
                    names.push(key);
                }
            }
        }
        let mut out = Vec::new();
        for (t, name) in names {
            let chosen = self.chosen(ty, t, &name);
            let copied = (p.decls.supers(ty).iter())
                .filter_map(|&s| nominal_decl(p, s))
                .find_map(|s| self.chosen(s, t, &name));
            if let Some(from) = chosen.filter(|&c| Some(c) != copied) {
                out.push((t, name, from));
            }
        }
        out
    }

    /// The type whose member of a name a type has, by Temper's choice: its
    /// own, else the first of its supertypes, breadth-first, that defines
    /// one.
    fn chosen(&self, ty: DeclId, table: Table, name: &str) -> Option<DeclId> {
        let p = self.p;
        std::iter::once(ty).chain(ancestors(p, ty)).find(|&t| {
            (defined(p, &self.types, t).into_iter())
                .any(|(k, d)| k == table && self.name(d) == name)
        })
    }

    /// A call of a method, getter, or setter of the program's own.
    pub(crate) fn call_method(
        &mut self,
        method: DeclId,
        receiver: NodeId,
        args: &[NodeId],
    ) -> R<Expr> {
        let p = self.p;
        let recv = self.expr(receiver)?;
        match p.decls.get(method).kind {
            DeclKind::Getter if args.is_empty() => Ok(recv.member(self.name(method))),
            DeclKind::Method => {
                let args = self.args(args)?;
                Ok(recv.invoke(self.name(method), args))
            }
            DeclKind::Setter => Err(NotYet {
                what: "a setter's call as a value".into(),
            }),
            kind => Err(NotYet {
                what: format!("a call of a {}", kind.name()),
            }),
        }
    }

    /// A setter's call as a statement: `recv.name = value`.
    pub(crate) fn setter_stmt(&mut self, expr: NodeId) -> R<Option<Stmt>> {
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
            return Err(NotYet {
                what: "a setter without one value".into(),
            });
        };
        let target = self.expr(receiver)?.member(self.name(method));
        let value = self.expr(value)?;
        Ok(Some(Stmt::Assign { target, value }))
    }

    /// The runtime's check of a type, `temper.instance_of(value, T)`.
    pub(crate) fn is_type(&mut self, ty: TypeId, value: NodeId) -> R<Expr> {
        let Some(decl) = nominal_decl(self.p, ty) else {
            return Err(NotYet {
                what: "`is` of a type that isn't a class or interface".into(),
            });
        };
        // A step's result, `{ true, value }`, or `{}` once done.
        let name = self.p.name(decl);
        if library_of(self.p, decl) == "core" && matches!(name, "ValueResult" | "DoneResult") {
            let first = Expr::Index(Box::new(self.expr(value)?), Box::new(Expr::Int(1)));
            let op = if name == "ValueResult" {
                Bin::Ne
            } else {
                Bin::Eq
            };
            return Ok(Expr::bin(op, first, Expr::Nil));
        }
        if let Some(index) = string_index(self.p, decl) {
            // `i >= 1` for a string index, `i < 1` for none, as the
            // runtime's `is_string_index` and `is_no_string_index`.
            let op = if index { Bin::Ge } else { Bin::Lt };
            return Ok(Expr::bin(op, self.expr(value)?, Expr::Int(1)));
        }
        let nullable = self.p.types.is_nullable(self.p.nodes.ty(value));
        if nullable && self.effects.node(self.p, value).any() {
            return Err(NotYet {
                what: "`is` of a nullable value with effects".into(),
            });
        }
        let value = self.expr(value)?;
        self.uses_temper = true;
        let check = ops::temper().method("instance_of", vec![value.clone(), self.type_ref(decl)]);
        // The runtime's check reads a type's tag, which null lacks.
        Ok(match nullable {
            true => Expr::bin(
                crate::tree::Bin::And,
                ops::temper().method("is_null", vec![value]).negate(),
                check,
            ),
            false => check,
        })
    }

    /// `temper.cast_to(value, T)`, which bubbles if it isn't one.
    pub(crate) fn cast(&mut self, id: NodeId, value: NodeId) -> R<Expr> {
        let ty = self.p.nodes.ty(id);
        // `x as T` for `x: T?`, of a builtin type: the runtime's check of
        // its Lua type, which `temper.null` fails.
        let scalar = match self.p.types.get(ty) {
            temper_tmpl::TypeData::Scalar(s) => match s {
                temper_tmpl::Scalar::String => Some("cast_to_string"),
                temper_tmpl::Scalar::Int32 | temper_tmpl::Scalar::Int64 => Some("cast_to_int"),
                temper_tmpl::Scalar::Float64 => Some("cast_to_float64"),
                temper_tmpl::Scalar::Boolean => Some("cast_to_boolean"),
                _ => None,
            },
            _ => None,
        };
        if let Some(helper) = scalar {
            let value = self.expr(value)?;
            self.uses_temper = true;
            return Ok(ops::temper().method(helper, vec![value]));
        }
        let Some(decl) = nominal_decl(self.p, ty) else {
            return Err(NotYet {
                what: "`as` to a type that isn't a class or interface".into(),
            });
        };
        // Core's lists and maps, by the runtime's checks of their tags, as
        // Kotlin's. Kotlin's `Mapped` calls `cast_to_mapped`, which the
        // runtime lacks; its second `cast_to_map` is that check, so a
        // `Map` takes a `MapBuilder` too.
        if is_external(self.p, decl) && library_of(self.p, decl) == "core" {
            let helper = match self.p.name(decl) {
                "List" => Some("cast_to_list"),
                "ListBuilder" => Some("cast_to_listbuilder"),
                "Listed" => Some("cast_to_listed"),
                "Map" | "Mapped" => Some("cast_to_map"),
                "MapBuilder" => Some("cast_to_mapbuilder"),
                _ => None,
            };
            if let Some(helper) = helper {
                let value = self.expr(value)?;
                self.uses_temper = true;
                return Ok(ops::temper().method(helper, vec![value]));
            }
        }
        let value = self.expr(value)?;
        self.uses_temper = true;
        if let Some(index) = string_index(self.p, decl) {
            // The runtime's check, as Kotlin's, which bubbles.
            let name = match index {
                true => "require_string_index",
                false => "require_no_string_index",
            };
            return Ok(ops::temper().method(name, vec![value]));
        }
        Ok(ops::temper().method("cast_to", vec![value, self.type_ref(decl)]))
    }
}

/// For core's `StringIndex`, true, and `NoStringIndex`, false: the types
/// an integer is checked to be.
fn string_index(p: &Program, decl: DeclId) -> Option<bool> {
    if !is_external(p, decl) || library_of(p, decl) != "core" {
        return None;
    }
    match p.name(decl) {
        "StringIndex" => Some(true),
        "NoStringIndex" => Some(false),
        _ => None,
    }
}

/// The module-level declarations a definition refers to when it runs:
/// functions it calls, module `let`s it reads or writes, and the types
/// whose tables it uses (to make an instance, read a static, call a
/// static function, or check or cast a value); the program's own.
pub fn refs(p: &Program, item: NodeId) -> Vec<DeclId> {
    let mut found = Vec::new();
    walk(p, item, &mut |id| {
        let decl = match p.nodes.get(id) {
            Node::CallFunction { callee, .. } => owner_type(p, callee).unwrap_or(callee),
            Node::GetModuleLet { target } | Node::SetModuleLet { target, .. } => target,
            Node::New { ctor: d, .. }
            | Node::GetStatic { target: d }
            | Node::InitStatic { target: d, .. } => match owner_type(p, d) {
                Some(t) => t,
                None => return,
            },
            Node::Is { ty, .. } => match nominal_decl(p, ty) {
                Some(t) => t,
                None => return,
            },
            Node::As { .. } => match nominal_decl(p, p.nodes.ty(id)) {
                Some(t) => t,
                None => return,
            },
            _ => return,
        };
        if !is_external(p, decl) && !found.contains(&decl) {
            found.push(decl);
        }
    });
    found
}

/// Whether evaluating a node may do something that tmpl's [`Effects`]
/// doesn't see in it: call a method or static function of the program's
/// own, make an instance, cast, or read a `var` field, or call one of
/// `classy`, the functions that may.
///
/// [`Effects`]: temper_tmpl::analysis::Effects
pub fn classy(p: &Program, id: NodeId, classy: &HashSet<DeclId>) -> bool {
    let mut found = false;
    walk(p, id, &mut |n| {
        found |= match p.nodes.get(n) {
            Node::CallMethod { method, .. } => {
                !by_connection(p, method, crate::connected::connects_std)
            }
            Node::CallFunction { callee, .. } => {
                classy.contains(&callee)
                    || (!is_external(p, callee) && owner_type(p, callee).is_some())
            }
            Node::New { .. } | Node::As { .. } | Node::SetField { .. } => true,
            Node::GetField { field, .. } => p.decls.get(field).facts.contains(Facts::VAR),
            _ => false,
        };
    });
    found
}

/// What `roots` import ([`temper_tmpl::analysis::imports`]), with the
/// types they cast to with `as`, which needs the type's table, though
/// tmpl's imports don't list it.
pub fn with_casts(
    p: &Program,
    module: DeclId,
    roots: &[NodeId],
    mut imports: Vec<temper_tmpl::analysis::Import>,
) -> Vec<temper_tmpl::analysis::Import> {
    for &root in roots {
        walk(p, root, &mut |id| {
            if !matches!(p.nodes.get(id), Node::As { .. }) {
                return;
            }
            let Some(ty) = nominal_decl(p, p.nodes.ty(id)) else {
                return;
            };
            let Some(from) = home(p, ty).filter(|&m| m != module) else {
                return;
            };
            if temper_tmpl::qname::library_of(p, ty) == "core" {
                return;
            }
            match imports.iter_mut().find(|i| i.from == from) {
                Some(i) if i.decls.contains(&ty) => {}
                Some(i) => i.decls.push(ty),
                None => imports.push(temper_tmpl::analysis::Import {
                    from,
                    decls: vec![ty],
                }),
            }
        });
    }
    imports
}
