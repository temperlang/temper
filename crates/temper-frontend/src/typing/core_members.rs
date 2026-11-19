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

use crate::builtins::TYPES;
use std::sync::OnceLock;
use temper_syntax::ast::{Ast, Builtin as AstBuiltin, Value, build};
use temper_syntax::lex::SourceKind;
use temper_syntax::parse::parse;
use temper_syntax::tree::{NodeId, NodeKind};

use crate::corelib::TEXT as CORE;

/// The builtin types whose members are read: first the final ones, which
/// no other type extends, then the generic ones, then the rest whose
/// members are used.
const READ_TYPES: [&str; 25] = [
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
}

#[derive(Debug, PartialEq)]
pub(super) enum CoreMemberKind {
    /// A field's or getter's value.
    Value(CoreType),
    /// A method, with its own type parameters' names, each parameter's
    /// type and whether it has a default.
    Method {
        type_params: Vec<String>,
        params: Vec<(CoreType, bool)>,
        ret: CoreType,
        throws: bool,
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
    /// Where its name is in core's text: an instance extension's
    /// function's name.
    pub at: usize,
    /// For an instance extension, the function it is, as `listedMap`
    /// for `Listed`'s `map`.
    pub function: Option<String>,
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
        // The name, or quoted, as ``nym`true` ``.
        let after = (rest.strip_prefix(name)).or_else(|| {
            let quoted = rest.strip_prefix("nym`")?.strip_prefix(name)?;
            quoted.strip_prefix('`')
        })?;
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
    members.extend(read_extensions(name));
    CoreClass {
        type_params: reader.type_params.len(),
        type_param_names: reader.type_params,
        type_param_bounds: reader.type_param_bounds,
        members,
    }
}

/// Core's instance extensions of a builtin type, the top-level functions
/// marked `@extension("name")` whose first parameter is of that type, as
/// `listedMap` for `Listed<T>`'s `map`, as members of the type: typing
/// calls `xs.map(f)` as it would a method, with the extension's other
/// type parameters as the member's own, and tmpl as a call of the
/// function, `listedMap(xs, f)`. Each starts with a line of its own,
/// `@extension("name")`, and ends with a `;` or a line `}`.
fn read_extensions(type_name: &str) -> Vec<CoreMember> {
    let mut out = Vec::new();
    let mut offset = 0;
    for line in CORE.split_inclusive('\n') {
        let start = offset;
        offset += line.len();
        if !line.starts_with("@extension(") {
            continue;
        }
        let rest = &CORE[start..];
        let end = match (rest.find(';'), rest.find('{')) {
            (Some(semi), Some(brace)) if semi < brace => semi + 1,
            (Some(semi), None) => semi + 1,
            _ => rest.find("\n}\n").map_or(rest.len(), |e| e + 2),
        };
        let ast = build(&parse(&rest[..end], SourceKind::Standalone));
        let mut reader = Reader {
            ast: &ast,
            base: start,
            type_params: Vec::new(),
            type_param_bounds: Vec::new(),
            own_params: Vec::new(),
        };
        // The declaration, or a block of it and the `;` after it.
        let root = match ast.tree().kind(ast.root()) {
            NodeKind::Block => *ast
                .tree()
                .children(ast.root())
                .first()
                .unwrap_or(&ast.root()),
            _ => ast.root(),
        };
        reader.extension(root, None, type_name, &mut out);
    }
    out
}

/// Where core declares a builtin by name, as a byte offset of its name
/// in [`crate::corelib::TEXT`]: a type (`String`, `Listed`, `Console`),
/// a function or value (`ignore`, `console`, `Int`), or a builtin that
/// only a bodiless `@connected` declaration documents (`if`, `panic`,
/// ``nym`true` ``, whose offset is the `nym`'s). None for a builtin core
/// doesn't declare, as `+` or `return`.
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

/// What core's declaration of a builtin type lets other libraries do with
/// it, read from its declaration, so that core alone says what's enabled
/// (notes/core-capabilities.md).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct CoreShape {
    /// The name core declares it by, as `Int32` for `Int`.
    pub name: &'static str,
    /// An interface, which `new` can't make.
    pub interface: bool,
    /// A `sealed` interface, which only core extends.
    pub sealed: bool,
    /// A class with a public constructor, which `new` may call. Core
    /// declares every class's constructor, `private` where only core
    /// or the host makes its values.
    pub public_constructor: bool,
    /// What its `@mayDowncastTo(…)` says, if core marks it: `false`
    /// where `is` and `as` may not target it, as for `Boolean`, whose
    /// values some backends can't tell from numbers.
    pub may_downcast: Option<bool>,
}

/// The shape of a type core declares, by name, following an alias, as
/// `export let Int = Int32;`. None for a name core doesn't declare as a
/// type. Core's text is parsed once, the first time it's wanted. It's
/// the built-in core's, also while an editor changes a checkout's
/// `lib/core/core.temper`.
pub fn core_shape(type_name: &str) -> Option<CoreShape> {
    static SHAPES: OnceLock<Vec<(&'static str, Declared)>> = OnceLock::new();
    let shapes = SHAPES.get_or_init(read_shapes);
    let mut name = type_name;
    // Each alias once, so that aliases naming each other end.
    let mut seen = Vec::new();
    loop {
        match &shapes.iter().find(|(n, _)| *n == name)?.1 {
            Declared::Type(shape) => return Some(*shape),
            Declared::Alias(target) if !seen.contains(target) => {
                seen.push(target);
                name = target;
            }
            Declared::Alias(_) => return None,
        }
    }
}

/// What core's top level declares by a name.
enum Declared {
    Type(CoreShape),
    /// An alias of the type it names, as `Int32` for `let Int = Int32`.
    Alias(&'static str),
}

/// Each class, interface, and type alias core exports, by name, read from
/// its text: `export class Name`, `export sealed interface Name<…>`, or
/// `export let Name = Other;`, under its decorations, as
/// `@mayDowncastTo(false)`.
fn read_shapes() -> Vec<(&'static str, Declared)> {
    let ast = build(&parse(CORE, SourceKind::Standalone));
    let tree = ast.tree();
    // A name as written, or where it's in core, for the names kept.
    let text = |node: NodeId| Some(ast.names().get(ast.name(node)?));
    let in_core = |node: NodeId| -> &'static str {
        let span = tree.span(node);
        &CORE[span.start as usize..span.end as usize]
    };
    let call = |node: NodeId| match tree.kind(node) {
        NodeKind::Call => tree.children(node).split_first(),
        _ => None,
    };
    // A declaration without its decorations, and theirs, each a name or
    // a call, as `@export` or `@mayDowncastTo(false)`.
    let undecorated = |mut node: NodeId| {
        let mut decorations = Vec::new();
        while let Some((&callee, &[decoration, target])) = call(node) {
            if text(callee) != Some("@") {
                break;
            }
            decorations.push(decoration);
            node = target;
        }
        (decorations, node)
    };
    let named = |node: NodeId| call(node).map_or(text(node), |(&callee, _)| text(callee));
    let mut out = Vec::new();
    for &statement in tree.children(ast.root()) {
        let (decorations, declaration) = undecorated(statement);
        let has = |name: &str| decorations.iter().any(|&d| named(d) == Some(name));
        if !has("export") {
            continue;
        }
        // `export let Int = Int32;`, `(Decl ^Int \init Int32)`.
        if tree.kind(declaration) == NodeKind::Decl {
            let parts = tree.children(declaration);
            let init = (parts.windows(2))
                .find(|pair| {
                    ast.value(pair[0])
                        .is_some_and(|v| is_symbol(&ast, v, "init"))
                })
                .map(|pair| pair[1])
                .filter(|&init| tree.kind(init) == NodeKind::RightName);
            if let (Some(&name), Some(init)) = (parts.first(), init) {
                out.push((in_core(name), Declared::Alias(in_core(init))));
            }
            continue;
        }
        let Some((&callee, args)) = call(declaration) else {
            continue;
        };
        let interface = match text(callee) {
            Some("class") => false,
            Some("interface") => true,
            _ => continue,
        };
        // `\word Name`.
        let Some(name) = (args.windows(2))
            .find(|pair| {
                ast.value(pair[0])
                    .is_some_and(|v| is_symbol(&ast, v, "word"))
            })
            .map(|pair| in_core(pair[1]))
        else {
            continue;
        };
        // `@mayDowncastTo(false)`.
        let may_downcast = (decorations.iter())
            .filter_map(|&d| call(d).filter(|(c, _)| text(**c) == Some("mayDowncastTo")))
            .find_map(|(_, args)| match args.first().and_then(|&a| text(a)) {
                Some("true") => Some(true),
                Some("false") => Some(false),
                _ => None,
            });
        // Its members, in the block of the function that ends it.
        let body = (args.last())
            .filter(|&&f| tree.kind(f) == NodeKind::Fun)
            .and_then(|&f| tree.children(f).last().copied());
        let members = body.map_or(&[][..], |b| tree.children(b));
        let public_constructor = !interface
            && members.iter().any(|&m| {
                let (decorations, member) = undecorated(m);
                let hidden = (decorations.iter())
                    .any(|&d| matches!(named(d), Some("private" | "protected" | "static")));
                !hidden && call(member).is_some_and(|(&c, _)| text(c) == Some("constructor"))
            });
        let shape = CoreShape {
            name,
            interface,
            sealed: has("sealed"),
            public_constructor,
            may_downcast,
        };
        out.push((name, Declared::Type(shape)));
    }
    out
}

/// Whether a value is the symbol `\name`.
fn is_symbol(ast: &Ast, value: &Value, name: &str) -> bool {
    matches!(value, Value::Symbol(n) if ast.names().get(*n) == name)
}

/// Whether core declares a static member `name` of a builtin type, by
/// its name in [`TYPES`], as `fromCodePoint` of `String`.
pub(super) fn has_static(type_name: &str, name: &str) -> bool {
    let declared = core_shape(type_name).map_or(type_name, |shape| shape.name);
    let class = match READ_TYPES.contains(&declared) {
        true => Some(core_class(declared)),
        false => other_class(declared),
    };
    class.is_some_and(|c| (c.members.iter()).any(|m| m.is_static && m.name == name))
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
                    throws,
                } => {
                    let params: Vec<String> = (params.iter().enumerate())
                        .map(|(i, (t, optional))| {
                            let n = m.param_names.get(i).map_or("_", String::as_str);
                            let q = if *optional { "?" } else { "" };
                            format!("{n}{q}: {}", show_core(t, &shown_args, type_params))
                        })
                        .collect();
                    let ret = show_core(ret, &shown_args, type_params);
                    let ret = crate::types::show_result(ret, *throws);
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
                let ty = self.out_type(rest).0;
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
                self.own_params = self.type_args(parts);
                self.method(tree.children(node)[0], parts, 0, is_static, out);
            }
            _ => {}
        }
    }

    /// The type parameters among a declaration's parts, `\typeArg O`,
    /// each with whether it has a bound.
    fn type_args(&self, parts: &[NodeId]) -> Vec<(String, bool)> {
        (parts.windows(2))
            .filter(|pair| self.symbol(pair[0]) == Some("typeArg"))
            .map(|pair| {
                let name = self.type_param_name(pair[1]).unwrap_or_default();
                let bounded = self.ast.tree().kind(pair[1]) == NodeKind::Block;
                (name.to_string(), bounded)
            })
            .collect()
    }

    /// Adds a method named at `name`, from its parts, `\typeArg O (Block
    /// param) … \outType R`, with its own type parameters already noted,
    /// past its first `skip` parameters.
    fn method(
        &mut self,
        name: NodeId,
        parts: &[NodeId],
        skip: usize,
        is_static: bool,
        out: &mut Vec<CoreMember>,
    ) {
        let mut params = Vec::new();
        let mut names = Vec::new();
        for p in self.param_nodes(parts) {
            params.push(self.param(p));
            names.push(self.param_name(p).unwrap_or("_").to_string());
        }
        let (ret, throws) = self.out_type(parts);
        let own_params = std::mem::take(&mut self.own_params);
        let kind = CoreMemberKind::Method {
            type_params: own_params.into_iter().map(|(name, _)| name).collect(),
            params: params.split_off(skip.min(params.len())),
            ret,
            throws,
        };
        self.push(name, is_static, kind, out);
        if let Some(m) = out.last_mut() {
            m.param_names = names.split_off(skip.min(names.len()));
        }
    }

    /// The parameters among a declaration's parts, after its type
    /// parameters and before its result's type and its body.
    fn param_nodes(&self, parts: &[NodeId]) -> Vec<NodeId> {
        let tree = self.ast.tree();
        let mut out = Vec::new();
        let mut rest = parts;
        while let [p, after @ ..] = rest {
            match self.symbol(*p) {
                Some("typeArg") => rest = after.get(1..).unwrap_or(&[]),
                Some(_) => break,
                None => {
                    if tree.kind(*p) != NodeKind::Fun {
                        out.push(*p);
                    }
                    rest = after;
                }
            }
        }
        out
    }

    /// Adds an instance extension of `type_name`, read under its
    /// decorations, `@extension("name")` among them, as a member named
    /// so, if its first parameter's type is `type_name` given the
    /// function's own type parameters, as `Listed<T>`; those are the
    /// type's, and the function's others the member's own.
    fn extension(
        &mut self,
        node: NodeId,
        named: Option<String>,
        type_name: &str,
        out: &mut Vec<CoreMember>,
    ) {
        let Some((callee, args)) = self.call_parts(node) else {
            return;
        };
        let (word, name, parts) = match (callee, args) {
            (Some("@"), [decorator, target]) => {
                let named = match self.call_parts(*decorator) {
                    Some((Some("extension"), [n])) => match self.ast.value(*n) {
                        Some(Value::String(n)) => Some(n.to_string()),
                        _ => None,
                    },
                    _ => named,
                };
                return self.extension(*target, named, type_name, out);
            }
            (Some("let"), [word, name, parts @ ..]) => (*word, *name, parts),
            _ => return,
        };
        let Some(named) = named.filter(|_| self.symbol(word) == Some("word")) else {
            return;
        };
        let all = self.type_args(parts);
        // The first parameter's type, `Listed<T>`, its arguments each a
        // type parameter of the function's.
        let first = self.param_nodes(parts).first().copied();
        let receiver = first.and_then(|p| self.param_type_node(p));
        let Some((base, targs)) = receiver.and_then(|r| self.applied_parts(r)) else {
            return;
        };
        if self.text(base) != Some(type_name) {
            return;
        }
        let receiver_params: Option<Vec<String>> = (targs.iter())
            .map(|&a| {
                let n = self.text(a)?;
                all.iter().any(|(p, _)| p == n).then(|| n.to_string())
            })
            .collect();
        let Some(receiver_params) = receiver_params else {
            return;
        };
        self.type_params = receiver_params;
        self.own_params = (all.into_iter())
            .filter(|(p, _)| !self.type_params.contains(p))
            .collect();
        let function = self.text(name).map(str::to_string);
        self.method(name, parts, 1, false, out);
        if let Some(m) = out.last_mut() {
            m.name = named;
            m.function = function;
        }
    }

    /// A parameter's type, as written: `T` in `(Block \_complexArg_ x
    /// \type T)`.
    fn param_type_node(&self, node: NodeId) -> Option<NodeId> {
        let tree = self.ast.tree();
        let parts = tree.children(node);
        (tree.kind(node) == NodeKind::Block).then_some(())?;
        (parts.windows(2))
            .find(|pair| self.symbol(pair[0]) == Some("type"))
            .map(|pair| pair[1])
    }

    /// A type given arguments, `Listed<T>`: its base and its arguments;
    /// a type without them has none.
    fn applied_parts(&self, node: NodeId) -> Option<(NodeId, &'a [NodeId])> {
        let tree = self.ast.tree();
        match tree.kind(node) {
            NodeKind::RightName => Some((node, &[])),
            NodeKind::Call
                if self.ast.value(tree.children(node)[0])
                    == Some(&Value::Builtin(AstBuiltin::Angle)) =>
            {
                let (_, [base, args @ ..]) = self.call_parts(node)? else {
                    return None;
                };
                Some((*base, args))
            }
            _ => None,
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
                function: None,
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

    /// What follows `\outType`: the type, and whether it `throws Bubble`.
    fn out_type(&self, parts: &[NodeId]) -> (CoreType, bool) {
        let Some(i) = parts
            .iter()
            .position(|&p| self.symbol(p) == Some("outType"))
        else {
            return (CoreType::Unknown, false);
        };
        let Some(&ty) = parts.get(i + 1) else {
            return (CoreType::Unknown, false);
        };
        match self.call_parts(ty) {
            Some((Some("throws"), [pass, ..])) => (self.type_of(*pass), true),
            _ => (self.type_of(ty), false),
        }
    }

    /// A type: a builtin type by name, as `String`, maybe given
    /// arguments, as `List<T>`; the method's own type parameter, or else
    /// the type's; `T?`; or `fn (A): R`.
    fn type_of(&self, node: NodeId) -> CoreType {
        let tree = self.ast.tree();
        match tree.kind(node) {
            NodeKind::RightName => {
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
                ret: int,
                throws: true,
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
        let empty = ["GeneratorResult", "Promise", "NoStringIndex"];
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
                throws: false,
            }
        );
        // A method's own type parameter, which may shadow the type's.
        let o = CoreType::Own(0);
        let list = |x: &CoreType| match builtin("List") {
            CoreType::Builtin(b) => CoreType::Applied(b, vec![x.clone()]),
            _ => panic!("List"),
        };
        assert_eq!(
            find("Listed", "map").kind,
            CoreMemberKind::Method {
                type_params: vec!["O".to_string()],
                params: vec![(CoreType::Fn(vec![t.clone()], Box::new(o.clone())), false)],
                ret: list(&o),
                throws: false,
            }
        );
        assert_eq!(find("Listed", "map").function.as_deref(), Some("listedMap"));
        assert_eq!(find("Listed", "map").param_names, ["transform"]);
        let CoreMemberKind::Method {
            type_params,
            params,
            ret,
            ..
        } = &find("Mapped", "toListWith").kind
        else {
            panic!("a method");
        };
        assert_eq!((&type_params[..], ret), (&["T".to_string()][..], &list(&o)));
        // `fn (K, V): T`, `K` and `V` the type's, as `Mapped<K, V>` gives
        // them to `mappedToListWith<K extends MapKey<K>, V, T>`.
        let kv = vec![t.clone(), CoreType::Param(1)];
        assert_eq!(params[..], [(CoreType::Fn(kv, Box::new(o.clone())), false)]);
        let CoreMemberKind::Method { params, .. } = &find("Listed", "reduceFromIndex").kind else {
            panic!("a method");
        };
        assert_eq!(params[1], (int.clone(), false));
        // Every generic method is an extension: an interface's may be
        // overridden, so it can't be generic.
        for ty in ["Listed", "Mapped"] {
            for m in &core_class(ty).members {
                if let CoreMemberKind::Method { type_params, .. } = &m.kind {
                    let generic = !type_params.is_empty();
                    assert!(!generic || m.function.is_some(), "{ty}.{}", m.name);
                }
            }
        }
        assert_eq!(
            find("Mapped", "get").kind,
            CoreMemberKind::Method {
                type_params: Vec::new(),
                params: vec![(t.clone(), false)],
                ret: CoreType::Param(1),
                throws: true,
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
        // Each member's place is its name, in its type's declaration,
        // or an instance extension's function's name, after it.
        for ty in READ_TYPES {
            let decl = declaration(ty, false).expect(ty).0;
            for m in &core_class(ty).members {
                assert!(m.at > decl, "{ty}.{}", m.name);
                assert!(
                    CORE[m.at..].starts_with(m.function.as_ref().unwrap_or(&m.name)),
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
        // Bodiless `@connected` declarations, quoted where they must be,
        // and none for operators or jumps.
        assert!(named(core_declaration("panic").unwrap(), "panic;"));
        assert!(named(core_declaration("true").unwrap(), "nym`true`;"));
        assert_eq!(core_declaration("+"), None);
        assert_eq!(core_declaration("return"), None);
        assert_eq!(core_declaration("Str"), None);
    }

    /// Every builtin type has core's declaration, read from its
    /// decorations and members: one core is silent about would enable
    /// nothing, so isn't a builtin type.
    #[test]
    fn shapes_are_cores() {
        for &t in TYPES {
            assert!(core_shape(t).is_some(), "{t}");
        }
        let shape = |t: &str| core_shape(t).unwrap();
        // `Int` is an alias.
        assert_eq!(shape("Int"), shape("Int32"));
        assert_eq!(shape("Int").name, "Int32");
        // `@mayDowncastTo(false)`, or nothing.
        for t in [
            "Boolean",
            "Int32",
            "Int64",
            "Float64",
            "String",
            "List",
            "MapBuilder",
        ] {
            assert_eq!(shape(t).may_downcast, Some(false), "{t}");
        }
        assert_eq!(shape("Void").may_downcast, None);
        assert_eq!(shape("Null").may_downcast, Some(true));
        let listed = shape("Listed");
        assert!(listed.interface && listed.sealed && !listed.public_constructor);
        // `/*sealed*/` is a comment.
        assert!(!shape("MapKey").sealed);
        assert!(shape("StringBuilder").public_constructor);
        assert!(!shape("Empty").public_constructor);
        assert_eq!(core_shape("ignore"), None);
        assert_eq!(core_shape("Nope"), None);
    }
}
