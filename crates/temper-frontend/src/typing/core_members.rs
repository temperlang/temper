//! Members of builtin types, with their signatures read from Kotlin
//! Temper's `core.temper` (the repository's `lib/core/`): the final
//! ones (`Boolean`, `Int32`, `Int64`, `Float64` and `String`), as
//! `s.toInt32()`, `x.toString()`, `s.isEmpty` or `String.fromCodePoint(c)`,
//! and the generic ones, as `ls.length`, `ls.get(i)` or `m.keys()`, whose
//! signatures name their type parameters.
//!
//! Tempest doesn't load core as a library yet, so this reads only those
//! types' declarations, each parsed once, when typing first needs it: each
//! public member's name, whether it's static, and its type, a field's or
//! getter's or a method's signature. A type in these signatures is a
//! builtin type, maybe given arguments, nullable, or a function type, or
//! one of the type's own type parameters or a method's own (`O` in
//! `map<O>`, which has no bound); anything else is unknown, as is a
//! parameter that isn't written `name: Type`. The type's own type
//! parameters' bounds are read the same way, as `MapKey<K>` for `K` in
//! `Map<K extends MapKey<K>, V>`.

use crate::builtins::{FAILURE_ROOT, TYPES};
use std::sync::OnceLock;
use temper_syntax::ast::{Ast, Builtin as AstBuiltin, Value, build};
use temper_syntax::lex::SourceKind;
use temper_syntax::parse::parse;
use temper_syntax::tree::{NodeId, NodeKind};

use crate::corelib::TEXT as CORE;

/// The builtin types whose members are read: first the final ones, which
/// no other type extends, then the generic ones, then the rest whose
/// members are used.
const READ_TYPES: [&str; 31] = [
    "Boolean",
    "Int32",
    "Int64",
    "Float64",
    "String",
    "Deque",
    "DoneResult",
    "Generator",
    "GeneratorResult",
    "List",
    "ListBuilder",
    "Listed",
    "Map",
    "MapBuilder",
    "Mapped",
    "Pair",
    "Promise",
    "PromiseBuilder",
    "SafeGenerator",
    "ValueResult",
    "StringBuilder",
    "StringIndex",
    "StringIndexOption",
    "NoStringIndex",
    "DenseBitVector",
    FAILURE_ROOT,
    "CoreError",
    "MissingKey",
    "ParseError",
    "InvalidCodePoint",
    "BrokenPromise",
];

/// A type in a member's signature.
#[derive(Clone, Debug, PartialEq)]
pub(super) enum CoreType {
    /// Anything typing doesn't read.
    Unknown,
    /// A builtin type, by its index in [`TYPES`].
    Builtin(u8),
    /// The declaring type's own type parameter, by index, as `T` in
    /// `Listed<T>`.
    Param(u8),
    /// A method's own type parameter, by index, as `O` in `map<O>`.
    Own(u8),
    /// A builtin generic type given arguments, as `List<T>`.
    Applied(u8, Vec<CoreType>),
    /// `T?`.
    Nullable(Box<CoreType>),
    /// `fn (A, B): R`.
    Fn(Vec<CoreType>, Box<CoreType>),
    /// `T throws E`, a method's result where it may fail (notes/typing.md,
    /// "Results").
    Throws(Box<CoreType>, Box<CoreType>),
}

#[derive(Debug, PartialEq)]
pub(super) enum CoreMemberKind {
    /// A field's or getter's value.
    Value(CoreType),
    /// A method, with its own type parameters' names, each parameter's
    /// type and whether it has a default, and its result, a
    /// [`CoreType::Throws`] where it may fail.
    Method {
        type_params: Vec<String>,
        params: Vec<(CoreType, bool)>,
        ret: CoreType,
    },
}

#[derive(Debug)]
pub(super) struct CoreMember {
    pub name: String,
    pub is_static: bool,
    pub kind: CoreMemberKind,
    /// The operator it implements, if marked so, as `==` for
    /// `@operator("==")`.
    pub operator: Option<String>,
    /// A method's parameters' names, `_` where not read.
    pub param_names: Vec<String>,
    /// Where its name is in core's text.
    pub at: usize,
}

/// A builtin type's declaration: how many type parameters it has, and its
/// public members.
#[derive(Debug, Default)]
pub(super) struct CoreClass {
    pub type_params: usize,
    /// Its type parameters' names, as `K` and `V`.
    pub type_param_names: Vec<String>,
    /// Its type parameters' bounds, as `MapKey<K>` for `K` and unknown for
    /// `V` in `Map<K extends MapKey<K>, V>`: unknown where none is
    /// written, as `T` in `Deque<T>` may be nullable.
    pub type_param_bounds: Vec<CoreType>,
    pub members: Vec<CoreMember>,
}

/// A builtin type's declaration, by its name in [`TYPES`], read the first
/// time it's wanted; empty for a type whose members aren't read.
pub(super) fn core_class(type_name: &str) -> &'static CoreClass {
    static CLASSES: [OnceLock<CoreClass>; READ_TYPES.len()] =
        [const { OnceLock::new() }; READ_TYPES.len()];
    static EMPTY: CoreClass = CoreClass {
        type_params: 0,
        type_param_names: Vec::new(),
        type_param_bounds: Vec::new(),
        members: Vec::new(),
    };
    match READ_TYPES.iter().position(|&t| t == type_name) {
        Some(i) => CLASSES[i].get_or_init(|| read_class(type_name)),
        None => &EMPTY,
    }
}

/// Where core declares an exported name at the start of a line, as
/// `export class Name`, `export sealed interface Name<…>`, or `export let
/// name(…)`: the line's offset in core, the line, and the name's offset.
fn declaration(name: &str, lets: bool) -> Option<(usize, &'static str, usize)> {
    let declared = |line: &'static str| {
        let rest = line.strip_prefix("export ")?;
        let rest = (rest.strip_prefix("sealed "))
            .or_else(|| rest.strip_prefix("/*sealed*/ "))
            .unwrap_or(rest);
        let rest = (rest.strip_prefix("class "))
            .or_else(|| rest.strip_prefix("interface "))
            .or_else(|| rest.strip_prefix("let ").filter(|_| lets))?;
        let after = rest.strip_prefix(name)?;
        let ends = [' ', '<', '(', ':', ';', '\n'];
        after.starts_with(ends).then(|| line.len() - rest.len())
    };
    let mut offset = 0;
    for line in CORE.split_inclusive('\n') {
        if let Some(at) = declared(line) {
            return Some((offset, line, offset + at));
        }
        offset += line.len();
    }
    None
}

/// Reads one type's declaration, from the line `export class Name` or
/// `export sealed interface Name<…>` to the `}` that ends a line on its
/// own, or that line alone if it ends with `{}`, parsed alone.
fn read_class(name: &str) -> CoreClass {
    let Some((start, line, _)) = declaration(name, false) else {
        return CoreClass::default();
    };
    let end = match line.trim_end().ends_with("{}") {
        true => start + line.len(),
        false => (CORE[start..].find("\n}\n")).map_or(CORE.len(), |e| start + e + 2),
    };
    let ast = build(&parse(&CORE[start..end], SourceKind::Standalone));
    let mut reader = Reader {
        ast: &ast,
        base: start,
        type_params: Vec::new(),
        type_param_bounds: Vec::new(),
        own_params: Vec::new(),
    };
    let mut members = Vec::new();
    if let Some(body) = reader.class_body(ast.root()) {
        for &m in ast.tree().children(body) {
            reader.member(m, false, &mut members);
        }
    }
    CoreClass {
        type_params: reader.type_params.len(),
        type_param_names: reader.type_params,
        type_param_bounds: reader.type_param_bounds,
        members,
    }
}

/// Where core declares a builtin by name, as a byte offset of its name
/// in [`crate::corelib::TEXT`]: a type (`String`, `Listed`, `Console`)
/// or a function or value (`ignore`, `console`, `Int`). None for a
/// builtin core doesn't declare, as `if` or `panic`.
pub fn core_declaration(name: &str) -> Option<usize> {
    declaration(name, true).map(|(_, _, at)| at)
}

/// Where core declares a builtin type's member, as byte offsets of its
/// name in [`crate::corelib::TEXT`]: in the type's own declaration, then
/// in the builtin types it extends (`Listed` for a `List`'s `isEmpty`),
/// nearest first, so an override comes before what it overrides. Covers
/// any type core declares, as `Console`'s `log`, not only those typing
/// reads.
pub fn core_member_declarations(type_name: &str, member: &str) -> Vec<usize> {
    let mut out = Vec::new();
    let mut name = Some(type_name);
    while let Some(ty) = name {
        let class = match READ_TYPES.contains(&ty) {
            true => Some(core_class(ty)),
            false => other_class(ty),
        };
        let Some(class) = class else {
            break;
        };
        out.extend(
            (class.members.iter())
                .filter(|m| m.name == member)
                .map(|m| m.at),
        );
        name = crate::types::builtin_supertype(ty);
    }
    out
}

/// A type core declares that typing doesn't read, as `Console`, read the
/// first time an editor wants one.
fn other_class(name: &str) -> Option<&'static CoreClass> {
    static OTHERS: std::sync::Mutex<Vec<(String, &'static CoreClass)>> =
        std::sync::Mutex::new(Vec::new());
    let mut others = OTHERS.lock().unwrap_or_else(|e| e.into_inner());
    if let Some((_, class)) = others.iter().find(|(n, _)| n == name) {
        return Some(class);
    }
    declaration(name, false)?;
    // A handful at most, kept for the process's life.
    let class: &'static CoreClass = Box::leak(Box::new(read_class(name)));
    others.push((name.to_string(), class));
    Some(class)
}

/// A builtin type's public member, as editors show it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct BuiltinMember {
    pub name: String,
    pub is_static: bool,
    /// Whether it's a method, rather than a field or getter.
    pub is_method: bool,
    /// A field's or getter's type, or a method's signature with its
    /// parameters' names, as `add(value: String, at?: Int32): Void`.
    pub detail: String,
    /// A method's parameters, as `at?: Int32`; empty for others.
    pub params: Vec<String>,
    /// The builtin type that declares it, as `Listed` for a `List`'s
    /// `isEmpty`.
    pub declared_in: &'static str,
}

/// The public members of a builtin type, by its name in [`TYPES`], and
/// then of the builtin types it extends with the same type arguments
/// (`Listed` for `List`), each name once, with the type's own type
/// parameters shown as `args`, as `String` for `T` in a `List<String>`
/// (or by their own names where `args` has none). Empty for a builtin
/// type whose members typing doesn't read; a type core declares outside
/// the builtins, as `Console`, has its own.
pub fn builtin_members(type_name: &str, args: &[String]) -> Vec<BuiltinMember> {
    let mut out: Vec<BuiltinMember> = Vec::new();
    let mut name = Some(type_name);
    while let Some(ty) = name {
        let (class, declared_in) = match READ_TYPES.iter().find(|&&t| t == ty) {
            Some(&t) => (core_class(t), t),
            // A type core declares outside the builtins, as `Console`.
            None if !TYPES.contains(&ty) => match (other_class(ty), declaration(ty, false)) {
                (Some(class), Some((_, _, at))) => (class, &CORE[at..at + ty.len()]),
                _ => break,
            },
            None => break,
        };
        let shown_args: Vec<String> = (class.type_param_names.iter().enumerate())
            .map(|(i, n)| args.get(i).cloned().unwrap_or_else(|| n.clone()))
            .collect();
        for m in &class.members {
            if out.iter().any(|o| o.name == m.name) || m.operator.is_some() {
                continue;
            }
            let (is_method, detail, shown_params) = match &m.kind {
                CoreMemberKind::Value(t) => (false, show_core(t, &shown_args, &[]), Vec::new()),
                CoreMemberKind::Method {
                    type_params,
                    params,
                    ret,
                } => {
                    let params: Vec<String> = (params.iter().enumerate())
                        .map(|(i, (t, optional))| {
                            let n = m.param_names.get(i).map_or("_", String::as_str);
                            let q = if *optional { "?" } else { "" };
                            format!("{n}{q}: {}", show_core(t, &shown_args, type_params))
                        })
                        .collect();
                    let ret = show_core(ret, &shown_args, type_params);
                    let own = match type_params.is_empty() {
                        true => String::new(),
                        false => format!("<{}>", type_params.join(", ")),
                    };
                    let detail = format!("{}{own}({}): {ret}", m.name, params.join(", "));
                    (true, detail, params)
                }
            };
            out.push(BuiltinMember {
                name: m.name.clone(),
                is_static: m.is_static,
                is_method,
                detail,
                params: shown_params,
                declared_in,
            });
        }
        name = crate::types::builtin_supertype(ty);
    }
    out
}

/// A member's type as messages write it, with the type's parameters as
/// `args` and a method's own by their names.
fn show_core(t: &CoreType, args: &[String], own: &[String]) -> String {
    match t {
        CoreType::Unknown => "?".into(),
        CoreType::Builtin(i) => match TYPES[*i as usize] {
            "Int" => "Int32".into(),
            name => name.into(),
        },
        CoreType::Param(i) => args.get(*i as usize).cloned().unwrap_or_else(|| "?".into()),
        CoreType::Own(i) => own.get(*i as usize).cloned().unwrap_or_else(|| "?".into()),
        CoreType::Applied(b, targs) => {
            let targs: Vec<String> = targs.iter().map(|a| show_core(a, args, own)).collect();
            let base = show_core(&CoreType::Builtin(*b), args, own);
            format!("{base}<{}>", targs.join(", "))
        }
        CoreType::Nullable(inner) => match **inner {
            CoreType::Fn(..) => format!("({})?", show_core(inner, args, own)),
            _ => format!("{}?", show_core(inner, args, own)),
        },
        CoreType::Fn(params, ret) => {
            let ret = show_core(ret, args, own);
            match params.is_empty() {
                true => format!("fn: {ret}"),
                false => {
                    let params: Vec<String> =
                        params.iter().map(|p| show_core(p, args, own)).collect();
                    format!("fn ({}): {ret}", params.join(", "))
                }
            }
        }
        CoreType::Throws(pass, fail) => {
            let (pass, fail) = (show_core(pass, args, own), show_core(fail, args, own));
            format!("{pass} throws {fail}")
        }
    }
}

struct Reader<'a> {
    ast: &'a Ast,
    /// Where the declaration being read starts in core.
    base: usize,
    /// The type's own type parameters' names, in order.
    type_params: Vec<String>,
    /// Their bounds, in order, unknown where none is written.
    type_param_bounds: Vec<CoreType>,
    /// The type parameters of the method being read, in order, and
    /// whether each has a bound, which isn't read, so it's unknown.
    own_params: Vec<(String, bool)>,
}

impl<'a> Reader<'a> {
    fn text(&self, node: NodeId) -> Option<&'a str> {
        Some(self.ast.names().get(self.ast.name(node)?))
    }

    fn symbol(&self, node: NodeId) -> Option<&'a str> {
        match self.ast.value(node)? {
            Value::Symbol(n) => Some(self.ast.names().get(*n)),
            _ => None,
        }
    }

    fn call_parts(&self, node: NodeId) -> Option<(Option<&'a str>, &'a [NodeId])> {
        let tree = self.ast.tree();
        let (&callee, args) =
            (tree.kind(node) == NodeKind::Call).then(|| tree.children(node).split_first())??;
        Some((self.text(callee), args))
    }

    /// `(Call class \word ^Name \typeArg T … (Fun (Block members)))`, or
    /// `interface`, maybe in a block or under decorations; notes its type
    /// parameters' names, and then their bounds, which may name them.
    fn class_body(&mut self, node: NodeId) -> Option<NodeId> {
        let tree = self.ast.tree();
        match (tree.kind(node), self.call_parts(node)) {
            (NodeKind::Block, _) => tree.children(node).iter().find_map(|&c| self.class_body(c)),
            (_, Some((Some("@"), [_, target]))) => self.class_body(*target),
            (_, Some((Some("class" | "interface"), args))) => {
                let params: Vec<NodeId> = (args.windows(2))
                    .filter(|pair| self.symbol(pair[0]) == Some("typeArg"))
                    .map(|pair| pair[1])
                    .collect();
                for &p in &params {
                    let name = self.type_param_name(p).unwrap_or_default();
                    self.type_params.push(name.to_string());
                }
                self.type_param_bounds = (params.iter())
                    .map(|&p| {
                        (self.type_param_bound(p)).map_or(CoreType::Unknown, |b| self.type_of(b))
                    })
                    .collect();
                let fun = *args.last()?;
                let block = *tree.children(fun).last()?;
                (tree.kind(fun) == NodeKind::Fun && tree.kind(block) == NodeKind::Block)
                    .then_some(block)
            }
            _ => None,
        }
    }

    /// A type parameter's name: `T`, `@out T`, or `K extends MapKey<K>`
    /// (`(Block \typeArg K \super …)`).
    fn type_param_name(&self, node: NodeId) -> Option<&'a str> {
        let tree = self.ast.tree();
        match (tree.kind(node), self.call_parts(node)) {
            (NodeKind::RightName | NodeKind::LeftName, _) => self.text(node),
            (_, Some((Some("@"), [_, target]))) => self.type_param_name(*target),
            (NodeKind::Block, _) => self.type_param_name(*tree.children(node).get(1)?),
            _ => None,
        }
    }

    /// A type parameter's bound, as `MapKey<K>` in `K extends MapKey<K>`
    /// (`(Block \typeArg K \super …)`), maybe decorated, if it has one.
    fn type_param_bound(&self, node: NodeId) -> Option<NodeId> {
        let tree = self.ast.tree();
        match (tree.kind(node), self.call_parts(node)) {
            (_, Some((Some("@"), [_, target]))) => self.type_param_bound(*target),
            (NodeKind::Block, _) => (tree.children(node).windows(2))
                .find(|pair| self.symbol(pair[0]) == Some("super"))
                .map(|pair| pair[1]),
            _ => None,
        }
    }

    /// Adds a member if it's public, reading its decorations.
    fn member(&mut self, node: NodeId, is_static: bool, out: &mut Vec<CoreMember>) {
        let tree = self.ast.tree();
        // `let begin: StringIndex`, `(Decl ^begin \type StringIndex)`.
        if tree.kind(node) == NodeKind::Decl {
            let parts = tree.children(node);
            let ty = (parts.windows(2)).find(|pair| self.symbol(pair[0]) == Some("type"));
            if let (Some(&name), Some(pair)) = (parts.first(), ty) {
                let ty = self.type_of(pair[1]);
                self.push(name, is_static, CoreMemberKind::Value(ty), out);
            }
            return;
        }
        let Some((callee, args)) = self.call_parts(node) else {
            return;
        };
        match (callee, args) {
            (Some("@"), [decorator, target]) => match self.text(*decorator) {
                Some("private" | "protected") => {}
                Some("static") => self.member(*target, true, out),
                _ => {
                    let before = out.len();
                    self.member(*target, is_static, out);
                    // `@operator("==")`.
                    if let Some((Some("operator"), [op])) = self.call_parts(*decorator) {
                        if let Some(Value::String(op)) = self.ast.value(*op) {
                            for m in &mut out[before..] {
                                m.operator = Some(op.to_string());
                            }
                        }
                    }
                }
            },
            // `get \word ^name \outType T { … }`
            (Some("get"), [word, name, rest @ ..]) if self.symbol(*word) == Some("word") => {
                // What a read gives, as no value has a result type.
                let ty = match self.out_type(rest) {
                    CoreType::Throws(pass, _) => *pass,
                    ty => ty,
                };
                self.push(*name, is_static, CoreMemberKind::Value(ty), out);
            }
            // `set \word ^name …`, not a method named `set`.
            (Some("set"), [word, ..]) if self.symbol(*word) == Some("word") => {}
            // `key: K`
            (Some(":"), [name, ty]) => {
                let ty = self.type_of(*ty);
                self.push(*name, is_static, CoreMemberKind::Value(ty), out);
            }
            // `static e = 2.718…`
            (None, [op, target, value]) if self.text(*op) == Some("=") => {
                let ty = match self.ast.value(*value) {
                    Some(Value::Float64(_)) => builtin("Float64"),
                    Some(Value::Int32(_)) => builtin("Int32"),
                    Some(Value::String(_)) => builtin("String"),
                    _ => CoreType::Unknown,
                };
                self.push(*target, is_static, CoreMemberKind::Value(ty), out);
            }
            // `name \typeArg O (Block param) … \outType R`.
            (Some(_), parts) => {
                self.own_params = (parts.windows(2))
                    .filter(|pair| self.symbol(pair[0]) == Some("typeArg"))
                    .map(|pair| {
                        let name = self.type_param_name(pair[1]).unwrap_or_default();
                        let bounded = self.ast.tree().kind(pair[1]) == NodeKind::Block;
                        (name.to_string(), bounded)
                    })
                    .collect();
                let mut params = Vec::new();
                let mut names = Vec::new();
                let mut rest = parts;
                while let [p, after @ ..] = rest {
                    match self.symbol(*p) {
                        Some("typeArg") => rest = after.get(1..).unwrap_or(&[]),
                        Some(_) => break,
                        None => {
                            if tree.kind(*p) != NodeKind::Fun {
                                params.push(self.param(*p));
                                names.push(self.param_name(*p).unwrap_or("_").to_string());
                            }
                            rest = after;
                        }
                    }
                }
                let ret = self.out_type(parts);
                let own_params = std::mem::take(&mut self.own_params);
                let kind = CoreMemberKind::Method {
                    type_params: own_params.into_iter().map(|(name, _)| name).collect(),
                    params,
                    ret,
                };
                self.push(tree.children(node)[0], is_static, kind, out);
                if let Some(m) = out.last_mut() {
                    m.param_names = names;
                }
            }
            _ => {}
        }
    }

    /// A parameter's name, read as [`Reader::param`] reads its type.
    fn param_name(&self, node: NodeId) -> Option<&'a str> {
        let tree = self.ast.tree();
        if let Some((Some("@"), [_, target])) = self.call_parts(node) {
            return self.param_name(*target);
        }
        let parts = tree.children(node);
        match tree.kind(node) {
            NodeKind::Decl => self.text(*parts.first()?),
            NodeKind::Block => match (parts.iter()).find(|&&p| tree.kind(p) == NodeKind::Decl) {
                Some(&decl) => self.param_name(decl),
                None => (parts.windows(2))
                    .find(|pair| self.symbol(pair[0]) == Some("_complexArg_"))
                    .and_then(|pair| self.text(pair[1])),
            },
            _ => None,
        }
    }

    fn push(&self, name: NodeId, is_static: bool, kind: CoreMemberKind, out: &mut Vec<CoreMember>) {
        if let Some(text) = self.text(name) {
            // A name's node may span more than the name, as its type.
            let start = self.base + self.ast.tree().span(name).start as usize;
            let at = start + (CORE[start..].find(text)).filter(|&i| i < 256).unwrap_or(0);
            out.push(CoreMember {
                name: text.to_string(),
                is_static,
                kind,
                operator: None,
                param_names: Vec::new(),
                at,
            });
        }
    }

    /// `(Block \_complexArg_ name \type T \default d)`: its type, and
    /// whether it has a default.
    /// Also a decorated one, as `@inlineUnrealizedGoal let body: fn (T):
    /// Void`, `(Call @ d (Block \_complexArg_ (Decl body \type …)))`.
    fn param(&self, node: NodeId) -> (CoreType, bool) {
        let tree = self.ast.tree();
        if let Some((Some("@"), [_, target])) = self.call_parts(node) {
            return self.param(*target);
        }
        let parts = tree.children(node);
        if !matches!(tree.kind(node), NodeKind::Block | NodeKind::Decl) {
            return (CoreType::Unknown, false);
        }
        if let Some(&decl) = (parts.iter()).find(|&&p| tree.kind(p) == NodeKind::Decl) {
            return self.param(decl);
        }
        let after = |key: &str| {
            (parts.windows(2))
                .find(|pair| self.symbol(pair[0]) == Some(key))
                .map(|pair| pair[1])
        };
        (
            after("type").map_or(CoreType::Unknown, |t| self.type_of(t)),
            after("default").is_some(),
        )
    }

    /// What follows `\outType`: the result type, [`CoreType::Throws`]
    /// for `T throws E` ([`Reader::type_of`]).
    fn out_type(&self, parts: &[NodeId]) -> CoreType {
        let Some(i) = parts
            .iter()
            .position(|&p| self.symbol(p) == Some("outType"))
        else {
            return CoreType::Unknown;
        };
        let Some(&ty) = parts.get(i + 1) else {
            return CoreType::Unknown;
        };
        self.type_of(ty)
    }

    /// A type: a builtin type named as `String` or `builtins.String`
    /// (`%String`), maybe given arguments, as `List<T>`; the method's own
    /// type parameter, or else the type's; `T?`; `fn (A): R`; or a
    /// result, `R throws E`.
    fn type_of(&self, node: NodeId) -> CoreType {
        let tree = self.ast.tree();
        match tree.kind(node) {
            NodeKind::RightName | NodeKind::BuiltinName => {
                let Some(name) = self.text(node) else {
                    return CoreType::Unknown;
                };
                let own = self.own_params.iter().position(|(p, _)| p == name);
                match (own, self.type_params.iter().position(|p| p == name)) {
                    (Some(i), _) if self.own_params[i].1 => CoreType::Unknown,
                    (Some(i), _) => CoreType::Own(i as u8),
                    (None, Some(i)) => CoreType::Param(i as u8),
                    (None, None) => builtin(name),
                }
            }
            NodeKind::Call => {
                let children = tree.children(node);
                let angle = self.ast.value(children[0]) == Some(&Value::Builtin(AstBuiltin::Angle));
                match (angle, self.call_parts(node)) {
                    (true, Some((_, [base, args @ ..]))) => match self.type_of(*base) {
                        CoreType::Builtin(b) => {
                            CoreType::Applied(b, args.iter().map(|&a| self.type_of(a)).collect())
                        }
                        _ => CoreType::Unknown,
                    },
                    (false, Some((Some("?"), [inner]))) => {
                        CoreType::Nullable(Box::new(self.type_of(*inner)))
                    }
                    // A result, a method's or a function type's.
                    (false, Some((Some("throws"), [pass, fail, ..]))) => CoreType::Throws(
                        Box::new(self.type_of(*pass)),
                        Box::new(self.type_of(*fail)),
                    ),
                    (false, Some((Some("fn"), parts))) => {
                        let out = parts
                            .iter()
                            .position(|&p| self.symbol(p) == Some("outType"));
                        let (Some(out), Some(&ret)) = (out, out.and_then(|i| parts.get(i + 1)))
                        else {
                            return CoreType::Unknown;
                        };
                        let params = parts[..out].iter().map(|&p| self.type_of(p)).collect();
                        CoreType::Fn(params, Box::new(self.type_of(ret)))
                    }
                    _ => CoreType::Unknown,
                }
            }
            _ => CoreType::Unknown,
        }
    }
}

/// A builtin type by name, or unknown.
fn builtin(name: &str) -> CoreType {
    match TYPES.iter().position(|&t| t == name) {
        Some(i) => CoreType::Builtin(i as u8),
        None => CoreType::Unknown,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn find(ty: &str, name: &str) -> &'static CoreMember {
        (core_class(ty).members.iter())
            .find(|m| m.name == name)
            .unwrap_or_else(|| panic!("{ty}.{name}"))
    }

    #[test]
    fn lists_builtin_members_for_editors() {
        let members = builtin_members("ListBuilder", &["String".into()]);
        let add = members.iter().find(|m| m.name == "add").unwrap();
        assert!(add.is_method);
        assert_eq!(add.detail, "add(newValue: String, at?: Int32): Void");
        // From Listed, which ListBuilder extends.
        let length = members.iter().find(|m| m.name == "length").unwrap();
        assert_eq!(length.detail, "Int32");
        assert!(members.iter().any(|m| m.declared_in == "Listed"));
        let strings = builtin_members("String", &[]);
        let split = strings.iter().find(|m| m.name == "split").unwrap();
        assert!(split.detail.starts_with("split("), "{}", split.detail);
        assert!(
            strings
                .iter()
                .any(|m| m.name == "fromCodePoint" && m.is_static)
        );
        assert!(builtin_members("Nope", &[]).is_empty());
        let console = builtin_members("Console", &[]);
        assert_eq!(console[0].detail, "log(message: String): Void");
        assert_eq!(console[0].declared_in, "Console");
    }

    #[test]
    fn reads_core_signatures() {
        let int = builtin("Int");
        let string = builtin("String");
        let to_int = find("String", "toInt32");
        assert_eq!(
            to_int.kind,
            CoreMemberKind::Method {
                type_params: Vec::new(),
                params: vec![(int.clone(), true)],
                ret: CoreType::Throws(Box::new(int.clone()), Box::new(builtin("ParseError"))),
            }
        );
        assert!(find("String", "fromCodePoint").is_static);
        let eq = find("StringIndexOption", "eq");
        assert_eq!(eq.operator.as_deref(), Some("=="));
        assert_eq!(
            find("String", "isEmpty").kind,
            CoreMemberKind::Value(builtin("Boolean"))
        );
        assert_eq!(
            find("Float64", "pi").kind,
            CoreMemberKind::Value(builtin("Float64"))
        );
        let CoreMemberKind::Method { ret, .. } = &find("Int32", "toString").kind else {
            panic!("a method");
        };
        assert_eq!(*ret, string);
        let CoreMemberKind::Method { ret, .. } = &find("String", "split").kind else {
            panic!("a method");
        };
        let CoreType::Builtin(list) = builtin("List") else {
            panic!("List");
        };
        assert_eq!(*ret, CoreType::Applied(list, vec![string]));
        // Private fields aren't members to callers.
        assert!(
            core_class("Boolean")
                .members
                .iter()
                .all(|m| m.name != "content")
        );
        // Each type is found, though some declare no members.
        let empty = [
            "DoneResult",
            "GeneratorResult",
            "Promise",
            "NoStringIndex",
            "CoreError",
        ];
        // The final types come first, the generic ones after, then the
        // rest.
        for (i, ty) in READ_TYPES.into_iter().enumerate() {
            let class = core_class(ty);
            assert_eq!(class.members.is_empty(), empty.contains(&ty), "{ty}");
            assert_eq!(class.type_params > 0, (5..20).contains(&i), "{ty}");
        }
    }

    #[test]
    fn reads_generic_signatures() {
        let t = CoreType::Param(0);
        let int = builtin("Int");
        assert_eq!(core_class("Listed").type_params, 1);
        assert_eq!(core_class("Map").type_params, 2);
        assert_eq!(core_class("DoneResult").type_params, 1);
        // Bounds as declared, which may name the type's own parameters;
        // unknown where none is written.
        let map_key = CoreType::Applied(
            TYPES.iter().position(|&n| n == "MapKey").unwrap() as u8,
            vec![t.clone()],
        );
        assert_eq!(
            core_class("Map").type_param_bounds,
            [map_key.clone(), CoreType::Unknown]
        );
        assert_eq!(core_class("MapBuilder").type_param_bounds[0], map_key);
        assert_eq!(
            core_class("PromiseBuilder").type_param_bounds,
            [builtin("AnyValue")]
        );
        assert_eq!(core_class("Deque").type_param_bounds, [CoreType::Unknown]);
        assert_eq!(
            find("Listed", "length").kind,
            CoreMemberKind::Value(int.clone())
        );
        assert_eq!(
            find("Listed", "getOr").kind,
            CoreMemberKind::Method {
                type_params: Vec::new(),
                params: vec![(int.clone(), false), (t.clone(), false)],
                ret: t.clone(),
            }
        );
        // A method's own type parameters, which may shadow the type's,
        // one a failure parameter (`E`, RFC 164).
        let (o, e) = (CoreType::Own(0), CoreType::Own(1));
        let list = |x: &CoreType| match builtin("List") {
            CoreType::Builtin(b) => CoreType::Applied(b, vec![x.clone()]),
            _ => panic!("List"),
        };
        let throws_e = |x: CoreType| CoreType::Throws(Box::new(x), Box::new(e.clone()));
        assert_eq!(
            find("Listed", "map").kind,
            CoreMemberKind::Method {
                type_params: vec!["O".to_string(), "E".to_string()],
                params: vec![(
                    CoreType::Fn(vec![t.clone()], Box::new(throws_e(o.clone()))),
                    false
                )],
                ret: throws_e(list(&o)),
            }
        );
        let CoreMemberKind::Method {
            type_params, ret, ..
        } = &find("Mapped", "toListWith").kind
        else {
            panic!("a method");
        };
        let names = ["T".to_string(), "E".to_string()];
        assert_eq!((&type_params[..], ret), (&names[..], &throws_e(list(&o))));
        assert_eq!(
            find("Mapped", "get").kind,
            CoreMemberKind::Method {
                type_params: Vec::new(),
                params: vec![(t.clone(), false)],
                ret: CoreType::Throws(
                    Box::new(CoreType::Param(1)),
                    Box::new(builtin("MissingKey"))
                ),
            }
        );
        assert_eq!(find("Pair", "key").kind, CoreMemberKind::Value(t.clone()));
        let CoreMemberKind::Method { params, .. } = &find("ListBuilder", "add").kind else {
            panic!("a method");
        };
        assert_eq!(params[..], [(t, false), (int, true)]);
    }

    #[test]
    fn members_and_builtins_are_located() {
        // Each member's place is its name, in its type's declaration.
        for ty in READ_TYPES {
            let decl = declaration(ty, false).expect(ty).0;
            for m in &core_class(ty).members {
                assert!(m.at > decl, "{ty}.{}", m.name);
                assert!(
                    CORE[m.at..].starts_with(&m.name),
                    "{ty}.{} at {}",
                    m.name,
                    m.at
                );
            }
        }
        let line = |at: usize| CORE[..at].lines().count();
        let named = |at: usize, name: &str| CORE[at..].starts_with(name);
        // Through supertypes, nearest first, and a type typing doesn't read.
        let length = core_member_declarations("List", "length");
        assert_eq!(length.len(), 2);
        assert!(named(length[0], "length") && line(length[0]) > line(length[1]));
        let log = core_member_declarations("Console", "log");
        assert!(log.len() == 1 && named(log[0], "log(message"), "{log:?}");
        assert!(core_member_declarations("Console", "nope").is_empty());
        assert!(core_member_declarations("Nope", "log").is_empty());
        for name in [
            "String", "Listed", "Console", "ignore", "console", "Int", "MapKey",
        ] {
            let at = core_declaration(name).expect(name);
            assert!(named(at, name), "{name}");
        }
        assert_eq!(core_declaration("panic"), None);
        assert_eq!(core_declaration("Str"), None);
    }
}
