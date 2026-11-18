//! `@json`: members and an adapter class that encode a type's values as
//! JSON and decode them, written as Temper against `std/json`, as Kotlin
//! Temper's `JsonInteropPass` writes them, with its shapes and names.
//!
//! When the `class` macro lowers a `@json` type ([`Settle::json_members`]),
//! it adds, by the first strategy that fits:
//!
//! - **Custom:** the type writes `encodeToJson(p)` and a static
//!   `decodeFromJson(t, ic)` itself. Only `jsonAdapter()` is added.
//! - **Concrete class:** `encodeToJson(p)` writes a JSON object with a
//!   property per backed field (`@jsonName("key")` renames one), then one
//!   per `@jsonExtra("key", value)` on the class, and the static
//!   `decodeFromJson(t, ic)` reads the constructor's fields back and
//!   calls it. A field of type `Boolean`, `Int`, `Int64`, `Float64` or
//!   `String` is read and written directly; others through adapters:
//!   `T.jsonAdapter()`, `List`'s, and `OrNullJsonAdapter` for `T?`.
//! - **Sealed interface:** `encodeToJson(p)` is abstract, as each
//!   subtype has its own (Kotlin's passes `this` to its class's adapter),
//!   and `decodeFromJson` picks the class by which properties the object
//!   has, and which `@jsonExtra` values, with a decision tree built as
//!   Kotlin's `buildDecisionTree` builds it. The subtypes are those of the
//!   module, through sealed interfaces, so the decoder is written once
//!   the module's scope is complete ([`Settle::json_sealed_body`]).
//!
//! Each also gets `jsonAdapter()`, making a `CJsonAdapter`, a class
//! declared after the type, whose methods call the type's. Names from
//! `std/json` are resolved there directly ([`Settle::resolve_std_ref`]),
//! whatever the module imports, and a module with `@json` loads it
//! ([`crate::library::Library::missing_imports`]). Not yet for a generic
//! type, which Kotlin's interpreter fails on too, nor std's `@connected`
//! `Date`, whose backends that connect it would need its other members
//! as module functions, as Kotlin's.
//!
//! The code is generated as text with placeholders, `__json3`, parsed
//! ([`crate::library::Library::add_fragment`]), and the placeholders
//! replaced by resolved names, values, and copies of the type's own type
//! expressions, then lowered as written code is.

use crate::core::{
    CLASS_INTERFACE, CLASS_SEALED, MEMBER_NO_PROPERTY, MEMBER_PRIVATE, MEMBER_PROTECTED,
    MEMBER_STATIC, ROLE_CONSTRUCTOR, ROLE_MASK, ROLE_METHOD, ROLE_PARAM, class_parts, fn_parts,
    let_parts,
};
use crate::library::{Library, Message, ModuleId};
use crate::settle::{Fact, Settle};
use std::collections::HashMap;
use std::fmt::Write;
use temper_syntax::ast::{Builtin as SyntaxBuiltin, Value};
use temper_syntax::names::NameId;
use temper_syntax::tree::{NodeId, NodeKind, Span};

/// What `@json` keeps between steps.
#[derive(Default)]
pub(crate) struct JsonState {
    /// `@jsonExtra("key", value)` decorations, by the declaration they're
    /// on, until it's a class.
    pub extras: HashMap<NodeId, Vec<(String, Value)>>,
    /// `@jsonName("key")` on a field, by its member `Let`.
    pub names: HashMap<NodeId, String>,
    /// Each `@json` type's JSON properties, in order, each with its known
    /// value if `@jsonExtra` gave it one, by its `Class`: what a sealed
    /// supertype's decoder tells its subtypes apart by.
    pub shapes: HashMap<NodeId, Vec<(String, Option<Value>)>>,
    /// Where each `@json` decoration starts, by its declaration: what
    /// generated code is spanned at.
    pub at: HashMap<NodeId, u32>,
    /// Adapter classes to declare after a type's statement.
    pub siblings: HashMap<NodeId, Vec<NodeId>>,
    /// A sealed interface's decoder to write once its module's scope is
    /// complete, by the callee of the call standing for it.
    pub sealed: HashMap<NodeId, SealedBody>,
    /// Names resolved in a std module's exports, not the scope they're
    /// in, by the module's import path.
    pub std_refs: HashMap<NodeId, &'static str>,
}

/// A sealed interface's generated decoder, waiting for its module.
#[derive(Clone, Copy, Debug)]
pub(crate) struct SealedBody {
    /// The interface's `Class`, and its name.
    class: NodeId,
    name: NameId,
    module: ModuleId,
}

/// What stands in for a placeholder in generated code.
enum Sub {
    /// A copy of the type's own node, as a field's type.
    Copy(NodeId),
    Name(NameId),
    /// An export of `std/json`.
    Std(&'static str),
    Value(Value),
    Sealed(SealedBody),
}

/// Generated source, with its placeholders.
#[derive(Default)]
struct Gen {
    subs: Vec<Sub>,
}

impl Gen {
    /// A placeholder for `sub`.
    fn ph(&mut self, sub: Sub) -> String {
        self.subs.push(sub);
        format!("__json{}", self.subs.len() - 1)
    }

    fn std(&mut self, name: &'static str) -> String {
        self.ph(Sub::Std(name))
    }

    fn string(&mut self, s: &str) -> String {
        self.ph(Sub::Value(Value::String(s.into())))
    }
}

/// A primitive type `@json` reads and writes directly, with the producer's
// method and how to read it from a syntax tree.
#[derive(Clone, Copy)]
enum Prim {
    Boolean,
    Float64,
    Int,
    Int64,
    String,
}

impl Prim {
    fn producer(self) -> &'static str {
        match self {
            Prim::Boolean => "booleanValue",
            Prim::Float64 => "float64Value",
            Prim::Int => "int32Value",
            Prim::Int64 => "int64Value",
            Prim::String => "stringValue",
        }
    }

    /// The tree type to cast to, and the method to call or (`None`) its
    /// `content`.
    fn reader(self) -> (&'static str, Option<&'static str>) {
        match self {
            Prim::Boolean => ("JsonBoolean", None),
            Prim::Float64 => ("JsonNumeric", Some("asFloat64")),
            Prim::Int => ("JsonNumeric", Some("asInt32")),
            Prim::Int64 => ("JsonNumeric", Some("asInt64")),
            Prim::String => ("JsonString", None),
        }
    }

    fn adapter(self) -> &'static str {
        match self {
            Prim::Boolean => "booleanJsonAdapter",
            Prim::Float64 => "float64JsonAdapter",
            Prim::Int => "int32JsonAdapter",
            Prim::Int64 => "int64JsonAdapter",
            Prim::String => "stringJsonAdapter",
        }
    }
}

/// A field `@json` encodes.
struct Field {
    name: NameId,
    ty: Option<NodeId>,
    key: String,
}

/// What a JSON property test says of an object ([`decision_tree`]).
#[derive(Clone, Debug, PartialEq)]
enum Test {
    Absent,
    Present,
    Exact(Value),
}

/// Cases, by index, split by what a test says of each.
type Partition = Vec<(Test, Vec<usize>)>;

/// A decision tree over a sealed type's subtypes, by index.
enum Tree {
    Leaf(Vec<usize>),
    /// A JSON property key, and the subtrees for each of its outcomes.
    Inner(String, Vec<(Test, Tree)>),
}

/// Kotlin's `buildDecisionTree`: splits `cases` greedily by the feature
/// whose outcomes have the most entropy, the first of equals, until a
/// split doesn't separate them.
fn decision_tree(
    cases: &[usize],
    features: &[String],
    categorize: &dyn Fn(usize, &str) -> Vec<Test>,
) -> Tree {
    if cases.len() <= 1 || features.is_empty() {
        return Tree::Leaf(cases.to_vec());
    }
    let mut best: Option<(usize, f64, Partition)> = None;
    for (i, feature) in features.iter().enumerate() {
        let mut partition: Partition = Vec::new();
        for &case in cases {
            for test in categorize(case, feature) {
                match partition.iter_mut().find(|(t, _)| *t == test) {
                    Some((_, members)) => {
                        if !members.contains(&case) {
                            members.push(case);
                        }
                    }
                    None => partition.push((test, vec![case])),
                }
            }
        }
        let total: usize = partition.iter().map(|(_, m)| m.len()).sum();
        let entropy: f64 = partition
            .iter()
            .map(|(_, m)| {
                let p = m.len() as f64 / total as f64;
                -p * p.log2()
            })
            .sum();
        if best.as_ref().is_none_or(|b| entropy > b.1) {
            best = Some((i, entropy, partition));
        }
    }
    let Some((index, _, split)) = best else {
        return Tree::Leaf(cases.to_vec());
    };
    if split.len() == 1 {
        return Tree::Leaf(cases.to_vec());
    }
    let rest: Vec<String> = (features.iter().enumerate())
        .filter(|&(i, _)| i != index)
        .map(|(_, f)| f.clone())
        .collect();
    let choices = split
        .into_iter()
        .map(|(test, members)| (test, decision_tree(&members, &rest, categorize)))
        .collect();
    Tree::Inner(features[index].clone(), choices)
}

/// `valueFor` and a JSON key in Pascal case, as an identifier.
fn value_for(key: &str) -> String {
    let mut name = String::from("valueFor");
    for part in key.split(|c: char| !c.is_ascii_alphanumeric()) {
        let mut chars = part.chars();
        if let Some(first) = chars.next() {
            name.push(first.to_ascii_uppercase());
            name.extend(chars);
        }
    }
    name
}

impl Settle {
    /// Records `@jsonExtra("key", value)` on a declaration, returning
    /// whether `decorator` is one.
    pub(crate) fn json_extra(&mut self, call: NodeId, decorator: NodeId) -> bool {
        let tree = &self.lib.tree;
        let (NodeKind::Call, Some(&callee)) =
            (tree.kind(decorator), tree.children(decorator).first())
        else {
            return false;
        };
        if self.lib.name(callee).map(|n| self.lib.names.get(n)) != Some("jsonExtra") {
            return false;
        }
        let args = &tree.children(decorator)[1..];
        let extra = match *args {
            [key, value] => match (self.lib.value(key), self.literal(value)) {
                (Some(Value::String(key)), Some(value)) => Some((key.to_string(), value)),
                _ => None,
            },
            _ => None,
        };
        match extra {
            Some(extra) => self.json.extras.entry(call).or_default().push(extra),
            None => self.error(
                decorator,
                Message::JsonInterop("@jsonExtra takes a property name and a literal value".into()),
            ),
        }
        true
    }

    /// A literal's value: a string, number, boolean or null.
    fn literal(&self, node: NodeId) -> Option<Value> {
        match self.lib.value(node) {
            Some(v @ (Value::String(_) | Value::Int32(_) | Value::Float64(_) | Value::Bool(_))) => {
                Some(v.clone())
            }
            Some(Value::Null) => Some(Value::Null),
            _ => match self.lib.name(node).map(|n| self.lib.names.get(n)) {
                Some("null") => Some(Value::Null),
                Some("true") => Some(Value::Bool(true)),
                Some("false") => Some(Value::Bool(false)),
                _ => None,
            },
        }
    }

    /// The key in `@jsonName("key")`, if `decorator` is one: `Err` if it
    /// isn't a string.
    pub(crate) fn json_name_of(&self, decorator: NodeId) -> Option<Result<String, ()>> {
        let tree = &self.lib.tree;
        let (NodeKind::Call, Some(&callee)) =
            (tree.kind(decorator), tree.children(decorator).first())
        else {
            return None;
        };
        if self.lib.name(callee).map(|n| self.lib.names.get(n)) != Some("jsonName") {
            return None;
        }
        Some(match tree.children(decorator)[1..] {
            [key] => match self.lib.value(key) {
                Some(Value::String(key)) => Ok(key.to_string()),
                _ => Err(()),
            },
            _ => Err(()),
        })
    }

    /// The members `@json` adds to a type the `class` macro is lowering,
    /// `call`, already lowered as `members`; the adapter class is kept to
    /// declare after it ([`JsonState::siblings`]).
    pub(crate) fn json_members(
        &mut self,
        call: NodeId,
        name: NodeId,
        type_params: &[NodeId],
        members: &[NodeId],
        interface: bool,
        sealed: bool,
    ) -> Vec<NodeId> {
        let extras = self.json.extras.remove(&call).unwrap_or_default();
        let Some(class) = self.lib.name(name) else {
            return Vec::new();
        };
        let span = self.lib.tree.span(name);
        let class_text = self.lib.names.get(class).to_string();
        let tree = &self.lib.tree;
        let flags = |m: NodeId| tree.data(m);
        let visible = |m: NodeId| flags(m) & (MEMBER_PRIVATE | MEMBER_PROTECTED) == 0;
        let named = |m: NodeId, text: &str| {
            self.lib
                .name(tree.children(m)[0])
                .is_some_and(|n| self.lib.names.get(n) == text)
        };
        let members: Vec<NodeId> = (members.iter().copied())
            .filter(|&m| tree.kind(m) == NodeKind::Let)
            .collect();
        let to_json = members.iter().any(|&m| {
            flags(m) & ROLE_MASK == ROLE_METHOD
                && flags(m) & MEMBER_STATIC == 0
                && visible(m)
                && named(m, "encodeToJson")
        });
        let from_json = members
            .iter()
            .any(|&m| flags(m) & MEMBER_STATIC != 0 && visible(m) && named(m, "decodeFromJson"));
        // Backed fields; an interface's are abstract, encoded only if
        // named with `@jsonName`.
        let mut fields = Vec::new();
        for &m in &members {
            let data = flags(m);
            let is_field = matches!(data & ROLE_MASK, 0 | ROLE_PARAM)
                && data & (MEMBER_STATIC | MEMBER_NO_PROPERTY) == 0;
            if !is_field {
                continue;
            }
            let json_name = self.json.names.remove(&m);
            if interface && json_name.is_none() {
                continue;
            }
            let Some(field) = self.lib.name(self.lib.tree.children(m)[0]) else {
                continue;
            };
            let ty = let_parts(&self.lib.tree, m).1;
            let key = json_name.unwrap_or_else(|| self.lib.names.get(field).to_string());
            fields.push(Field {
                name: field,
                ty,
                key,
            });
        }
        let mut shape: Vec<(String, Option<Value>)> =
            fields.iter().map(|f| (f.key.clone(), None)).collect();
        shape.extend(extras.iter().map(|(k, v)| (k.clone(), Some(v.clone()))));
        self.json.shapes.insert(call, shape);
        let report = |s: &mut Self, text: String| {
            s.lib.report(
                crate::library::Level::Error,
                Message::JsonInterop(text),
                span,
            );
        };
        let mut g = Gen::default();
        let mut body = String::new();
        if !type_params.is_empty() {
            // Kotlin's interpreter fails on them too; C# and C++ can't yet
            // call a generic class's static members, as `jsonAdapter`.
            report(
                self,
                format!("Temper doesn't yet derive JSON adapters for generic type {class_text}"),
            );
            return Vec::new();
        } else if from_json || to_json {
            if !from_json {
                report(
                    self,
                    format!(
                        "{class_text} declared encodeToJson but has no corresponding decodeFromJson"
                    ),
                );
            } else if !to_json {
                report(
                    self,
                    format!(
                        "{class_text} declared decodeFromJson but has no corresponding encodeToJson"
                    ),
                );
            }
        } else if !interface {
            let ctor = self.constructor_names(&members);
            match self.concrete_methods(&mut g, class, &fields, &extras, &ctor) {
                Ok(text) => body.push_str(&text),
                Err(problems) => {
                    for p in problems {
                        report(self, p);
                    }
                    return Vec::new();
                }
            }
        } else if sealed {
            let module = self.lib.files[self.lib.locate(span.start).0.0 as usize].module;
            let (producer, tree_ty, ic) = (
                g.std("JsonProducer"),
                g.std("JsonSyntaxTree"),
                g.std("InterchangeContext"),
            );
            let decode = g.ph(Sub::Sealed(SealedBody {
                class: call,
                name: class,
                module,
            }));
            let me = g.ph(Sub::Name(class));
            // Abstract, where Kotlin's switches on `this`'s class to call
            // its adapter: each subtype's own `encodeToJson` overrides it,
            // as `@json` gives every one of them, so it does the same,
            // without giving `this` out, which C++'s `const` methods can't.
            writeln!(
                body,
                "public encodeToJson(p: {producer}): Void;\n\
                 public static decodeFromJson(t: {tree_ty}, ic: {ic}): {me} throws Bubble {{ {decode}() }}"
            )
            .unwrap();
        } else {
            report(
                self,
                format!("Cannot auto-derive JSON adapter for un-sealed interface {class_text}"),
            );
            return Vec::new();
        }
        let adapter = self.lib.names.intern(&format!("{class_text}JsonAdapter"));
        body.push_str(&Self::json_adapter_method(&mut g, class, adapter));
        let adapter_class = Self::adapter_class(&mut g, class, adapter);
        let source = format!("class __jsonClass {{\n{body}\n}}\n{adapter_class}");
        // At the decoration, as Kotlin's, and empty, so each local is
        // visible from where it's declared on.
        let pos = self.json.at.remove(&call).unwrap_or(span.start);
        let at = Span {
            start: pos,
            end: pos,
        };
        let Some(root) = self.adopt(g, &source, at, call) else {
            return Vec::new();
        };
        let statements = self.lib.tree.children(root).to_vec();
        let [class_stmt, adapter_stmt] = statements[..] else {
            return Vec::new();
        };
        let block = self
            .lib
            .tree
            .children(class_stmt)
            .last()
            .and_then(|&f| self.block_of(f));
        let Some(block) = block else {
            return Vec::new();
        };
        self.json.siblings.insert(call, vec![adapter_stmt]);
        let generated = self.lib.tree.children(block).to_vec();
        generated
            .into_iter()
            .filter_map(|m| self.lower_member(m, 0, false))
            .collect()
    }

    /// The names of the parameters a class's constructor takes, its own
    /// or its header's.
    fn constructor_names(&self, members: &[NodeId]) -> Vec<NameId> {
        let tree = &self.lib.tree;
        let role = |m: NodeId| tree.data(m) & ROLE_MASK;
        let is_static = |m: NodeId| tree.data(m) & MEMBER_STATIC != 0;
        let params: Vec<NodeId> = match members
            .iter()
            .find(|&&m| role(m) == ROLE_CONSTRUCTOR && !is_static(m))
        {
            Some(&c) => let_parts(tree, c)
                .2
                .filter(|&f| tree.kind(f) == NodeKind::Fn)
                .map(|f| fn_parts(tree, f).0)
                .unwrap_or_default(),
            None => members
                .iter()
                .copied()
                .filter(|&m| role(m) == ROLE_PARAM && !is_static(m))
                .collect(),
        };
        params
            .iter()
            .filter_map(|&p| self.lib.name(tree.children(p)[0]))
            .collect()
    }

    /// A built-in type that `@json` reads and writes directly, by name.
    fn prim(&self, ty: NodeId) -> Option<Prim> {
        if self.lib.tree.kind(ty) != NodeKind::RightName {
            return None;
        }
        Some(match self.lib.names.get(self.lib.name(ty)?) {
            "Boolean" => Prim::Boolean,
            "Float64" => Prim::Float64,
            "Int" | "Int32" => Prim::Int,
            "Int64" => Prim::Int64,
            "String" => Prim::String,
            _ => return None,
        })
    }

    /// An expression for a `JsonAdapter` of a type, as Kotlin's
    /// `Subsidiaries.plantAdapterFor`: `T.jsonAdapter(…)`, std's for
    /// built-in types and `List`, or `OrNullJsonAdapter` around one for
    /// `T?`.
    fn adapter_expr(&self, g: &mut Gen, ty: NodeId) -> Option<String> {
        let tree = &self.lib.tree;
        match tree.kind(ty) {
            NodeKind::RightName => {
                if let Some(prim) = self.prim(ty) {
                    return Some(format!("{}()", g.std(prim.adapter())));
                }
                // Other built-in types have none, as `Map`.
                let name = self.lib.names.get(self.lib.name(ty)?);
                if crate::builtins::TYPES.contains(&name) {
                    return None;
                }
                Some(format!("{}.jsonAdapter()", g.ph(Sub::Copy(ty))))
            }
            NodeKind::Call => {
                let children = tree.children(ty);
                let callee = *children.first()?;
                if let [_, inner] = *children {
                    let is_nullable =
                        self.lib.name(callee).map(|n| self.lib.names.get(n)) == Some("?");
                    if is_nullable {
                        let inner_adapter = self.adapter_expr(g, inner)?;
                        let or_null = g.std("OrNullJsonAdapter");
                        let inner_ty = g.ph(Sub::Copy(inner));
                        return Some(format!("new {or_null}<{inner_ty}>({inner_adapter})"));
                    }
                }
                let is_angle =
                    self.lib.value(callee) == Some(&Value::Builtin(SyntaxBuiltin::Angle));
                let [base, args @ ..] = &children[1..] else {
                    return None;
                };
                if !is_angle || tree.kind(*base) != NodeKind::RightName {
                    return None;
                }
                let mut adapters = Vec::new();
                for &arg in args {
                    adapters.push(self.adapter_expr(g, arg)?);
                }
                let adapters = adapters.join(", ");
                let base_name = self.lib.names.get(self.lib.name(*base)?);
                if base_name == "List" {
                    return Some(format!("{}({adapters})", g.std("listJsonAdapter")));
                }
                if crate::builtins::TYPES.contains(&base_name) {
                    return None;
                }
                Some(format!(
                    "{}.jsonAdapter({adapters})",
                    g.ph(Sub::Copy(*base))
                ))
            }
            _ => None,
        }
    }

    /// A local's name for `base`, or with a number, if that's taken.
    fn fresh(taken: &mut Vec<String>, base: &str) -> String {
        let mut name = base.to_string();
        let mut n = 1;
        while taken.contains(&name) {
            n += 1;
            name = format!("{base}{n}");
        }
        taken.push(name.clone());
        name
    }

    /// The concrete class strategy's `encodeToJson` and
    /// `decodeFromJson`, or the problems that stop them.
    fn concrete_methods(
        &mut self,
        g: &mut Gen,
        class: NameId,
        fields: &[Field],
        extras: &[(String, Value)],
        ctor: &[NameId],
    ) -> Result<String, Vec<String>> {
        let class_text = self.lib.names.get(class).to_string();
        let mut problems = Vec::new();
        let producer = g.std("JsonProducer");
        let mut encode =
            format!("public encodeToJson(p: {producer}): Void {{\n  p.startObject();\n");
        // The same field name twice would make `this.x` ambiguous.
        let mut seen = Vec::new();
        for f in fields {
            let field_text = self.lib.names.get(f.name).to_string();
            if seen.contains(&f.name) {
                problems.push(format!(
                    "Cannot JSON encode ambiguous property {class_text}.{field_text}"
                ));
                continue;
            }
            seen.push(f.name);
            let Some(ty) = f.ty else {
                problems.push(format!(
                    "Missing type info for JSON adapter for property {class_text}.{field_text}"
                ));
                continue;
            };
            let key = g.string(&f.key);
            let field = g.ph(Sub::Name(f.name));
            writeln!(encode, "  p.objectKey({key});").unwrap();
            match self.prim(ty) {
                Some(prim) => {
                    writeln!(encode, "  p.{}(this.{field});", prim.producer()).unwrap();
                }
                None => match self.adapter_expr(g, ty) {
                    Some(adapter) => {
                        writeln!(encode, "  {adapter}.encodeToJson(this.{field}, p);").unwrap()
                    }
                    None => {
                        let ty_text = self.lib.source_text(self.lib.tree.span(ty)).to_string();
                        problems.push(format!(
                            "No JsonAdapter for property {field_text} with type {ty_text}"
                        ));
                    }
                },
            }
        }
        for (key, value) in extras {
            let method = match value {
                Value::String(_) => "stringValue",
                Value::Int32(_) => "int32Value",
                Value::Bool(_) => "booleanValue",
                Value::Null => "nullValue",
                Value::Float64(_) => "float64Value",
                _ => {
                    problems.push(format!(
                        "Cannot JSON encode fixed value for property {class_text}.{key}"
                    ));
                    continue;
                }
            };
            let key = g.string(key);
            writeln!(encode, "  p.objectKey({key});").unwrap();
            if let Value::Null = value {
                encode.push_str("  p.nullValue();\n");
            } else {
                let value = g.ph(Sub::Value(value.clone()));
                writeln!(encode, "  p.{method}({value});").unwrap();
            }
        }
        encode.push_str("  p.endObject();\n}\n");

        // Decoding reads each constructor parameter's field, by name,
        // into a local named for it, unless that's taken.
        let (tree_ty, ic_ty, object) = (
            g.std("JsonSyntaxTree"),
            g.std("InterchangeContext"),
            g.std("JsonObject"),
        );
        let me = g.ph(Sub::Name(class));
        let mut decode = format!(
            "public static decodeFromJson(t: {tree_ty}, ic: {ic_ty}): {me} throws Bubble {{\n  \
             let obj = t as {object};\n"
        );
        let mut taken: Vec<String> = ["t", "ic", "obj"].map(String::from).to_vec();
        let mut args = Vec::new();
        for &param in ctor {
            let param_text = self.lib.names.get(param).to_string();
            let Some(f) = fields.iter().find(|f| f.name == param) else {
                problems.push(format!(
                    "Cannot JSON decode {class_text}: constructor input {param_text} is not an encoded property"
                ));
                continue;
            };
            let Some(ty) = f.ty else { continue };
            let local = Self::fresh(&mut taken, &param_text);
            let local_name = self.lib.names.intern(&local);
            let local_ph = g.ph(Sub::Name(local_name));
            let ty_ph = g.ph(Sub::Copy(ty));
            let key = g.string(&f.key);
            let tree_expr = format!("obj.propertyValueOrBubble({key})");
            let value = match self.prim(ty) {
                Some(prim) => {
                    // The cast in a local of its own, where Kotlin reads
                    // from the cast, `(tree as JsonNumeric).asInt32()`:
                    // pyright types an inline cast as its argument.
                    let (cast, method) = prim.reader();
                    let cast = g.std(cast);
                    let cast_local = Self::fresh(&mut taken, &format!("{param_text}Json"));
                    let cast_name = self.lib.names.intern(&cast_local);
                    let (a, b) = (g.ph(Sub::Name(cast_name)), g.ph(Sub::Name(cast_name)));
                    writeln!(decode, "  let {a} = {tree_expr} as {cast};").unwrap();
                    match method {
                        Some(m) => format!("{b}.{m}()"),
                        None => format!("{b}.content"),
                    }
                }
                None => match self.adapter_expr(g, ty) {
                    Some(adapter) => format!("{adapter}.decodeFromJson({tree_expr}, ic)"),
                    // Reported for the encoder.
                    None => continue,
                },
            };
            writeln!(decode, "  let {local_ph}: {ty_ph} = {value};").unwrap();
            args.push(g.ph(Sub::Name(local_name)));
        }
        let me = g.ph(Sub::Name(class));
        writeln!(decode, "  new {me}({})\n}}", args.join(", ")).unwrap();
        if !problems.is_empty() {
            return Err(problems);
        }
        Ok(format!("{encode}{decode}"))
    }

    /// `public static jsonAdapter(): JsonAdapter<C> { new
    /// CJsonAdapter() }`.
    fn json_adapter_method(g: &mut Gen, class: NameId, adapter: NameId) -> String {
        let json_adapter = g.std("JsonAdapter");
        let me = g.ph(Sub::Name(class));
        let adapter = g.ph(Sub::Name(adapter));
        format!("public static jsonAdapter(): {json_adapter}<{me}> {{\n  new {adapter}()\n}}\n")
    }

    /// `class CJsonAdapter extends JsonAdapter<C>`, whose methods call
    /// `C`'s.
    fn adapter_class(g: &mut Gen, class: NameId, adapter: NameId) -> String {
        let adapter = g.ph(Sub::Name(adapter));
        let json_adapter = g.std("JsonAdapter");
        let [m1, m2, m3, m4] = [(); 4].map(|_| g.ph(Sub::Name(class)));
        let (producer, tree_ty, ic) = (
            g.std("JsonProducer"),
            g.std("JsonSyntaxTree"),
            g.std("InterchangeContext"),
        );
        format!(
            "class {adapter} extends {json_adapter}<{m1}> {{\n\
             public encodeToJson(x: {m2}, p: {producer}): Void {{\n  x.encodeToJson(p);\n}}\n\
             public decodeFromJson(t: {tree_ty}, ic: {ic}): {m3} throws Bubble {{\n  \
             {m4}.decodeFromJson(t, ic)\n}}\n}}\n"
        )
    }

    /// Parses generated code, replacing its placeholders, and returns its
    /// root block, or reports the bug. Its nodes are spanned at `span`,
    /// which is empty, as all of them are at one position, where each
    /// local declared there is visible.
    fn adopt(&mut self, g: Gen, source: &str, span: Span, like: NodeId) -> Option<NodeId> {
        let mut std_nodes = Vec::new();
        let mut sealed_nodes = Vec::new();
        let subs = g.subs;
        let mut subst = |lib: &mut Library, kind: NodeKind, text: &str| -> Option<NodeId> {
            let i: usize = text.strip_prefix("__json")?.parse().ok()?;
            Some(match &subs[i] {
                Sub::Copy(node) => lib.copy(*node),
                &Sub::Name(name) if kind == NodeKind::Value => {
                    lib.add_value(Value::Symbol(name), span, like)
                }
                &Sub::Name(name) => lib.add_name(kind, name, span, like),
                Sub::Std(export) => {
                    let name = lib.names.intern(export);
                    let node = lib.add_name(NodeKind::RightName, name, span, like);
                    std_nodes.push(node);
                    node
                }
                Sub::Value(value) => lib.add_value(value.clone(), span, like),
                Sub::Sealed(body) => {
                    let name = lib.names.intern("jsonSealed");
                    let node = lib.add_name(NodeKind::RightName, name, span, like);
                    sealed_nodes.push((node, *body));
                    node
                }
            })
        };
        let root = match self.lib.add_fragment(source, span, like, &mut subst) {
            Ok(root) => root,
            Err(why) => {
                let text = format!("generated JSON code doesn't parse ({why}): {source}");
                self.lib.report(
                    crate::library::Level::Error,
                    Message::JsonInterop(text),
                    span,
                );
                return None;
            }
        };
        for node in std_nodes {
            self.json.std_refs.insert(node, "std/json");
        }
        self.json.sealed.extend(sealed_nodes);
        Some(root)
    }

    /// Resolves a name in a std module's exports
    /// ([`JsonState::std_refs`]), waiting for its scope.
    pub(crate) fn resolve_std_ref(
        &mut self,
        node: NodeId,
        path: &'static str,
        forced: bool,
    ) -> Result<(), Fact> {
        let span = self.lib.tree.span(node);
        let from = self.lib.files[self.lib.locate(span.start).0.0 as usize].module;
        let target = (self.lib.import_dir(from, path)).and_then(|d| self.lib.module_at(&d));
        let Some(target) = target.filter(|&t| t != from) else {
            self.error(node, Message::ImportFailed(path.into()));
            return Ok(());
        };
        let scope = self.modules[target.0 as usize];
        if !self.scopes.get(scope).complete {
            if !forced {
                return Err(Fact::ScopeComplete(scope));
            }
            self.error(node, Message::ImportFailed(path.into()));
            return Ok(());
        }
        let name = self.lib.name(node).unwrap();
        let found = (self.scopes.get(scope).names.iter().rev())
            .find(|&&(n, s)| n == name && self.scopes.symbol(s).exported)
            .map(|&(_, s)| s);
        match found {
            Some(symbol) => {
                self.scopes.resolve(node, symbol);
                if !self.imports.contains(&(from, target)) {
                    self.imports.push((from, target));
                }
            }
            None => {
                let text = self.lib.names.get(name).to_string();
                self.error(node, Message::NotExported(path.into(), text));
            }
        }
        Ok(())
    }

    /// Writes a sealed interface's decoder in place of the call standing
    /// for it, once its module's scope is complete, so its
    /// subtypes are all lowered; returns false if `call` isn't one.
    pub(crate) fn json_sealed_body(&mut self, call: NodeId, forced: bool) -> Result<bool, Fact> {
        let Some(&callee) = self.lib.tree.children(call).first() else {
            return Ok(false);
        };
        let Some(&body) = self.json.sealed.get(&callee) else {
            return Ok(false);
        };
        let scope = self.modules[body.module.0 as usize];
        if !self.scopes.get(scope).complete && !forced {
            return Err(Fact::ScopeComplete(scope));
        }
        self.json.sealed.remove(&callee);
        let span = self.lib.tree.span(call);
        // Problems at the interface's name, as the code here has no span
        // of its own.
        let at = self
            .lib
            .tree
            .span(class_parts(&self.lib.tree, body.class).0);
        let class_text = self.lib.names.get(body.name).to_string();
        let subtypes = self.json_subtypes(body);
        let (json, not_json): (Vec<_>, Vec<_>) = subtypes
            .into_iter()
            .partition(|(c, _)| self.json.shapes.contains_key(c));
        if !not_json.is_empty() {
            let names: Vec<&str> = not_json
                .iter()
                .map(|&(_, n)| self.lib.names.get(n))
                .collect();
            let text = format!(
                "No @json decoration on [{}] which is a sub-type of @json type {class_text}",
                names.join(", ")
            );
            self.lib
                .report(crate::library::Level::Error, Message::JsonInterop(text), at);
        }
        let mut g = Gen::default();
        let source = match self.sealed_decoder(&mut g, &json, &class_text) {
            Ok(text) => text,
            Err(text) => {
                self.lib
                    .report(crate::library::Level::Error, Message::JsonInterop(text), at);
                "do { bubble() }".into()
            }
        };
        let Some(root) = self.adopt(g, &source, span, call) else {
            self.become_error(call);
            return Ok(true);
        };
        let made = self.lib.tree.children(root)[0];
        self.become_node(call, made);
        Ok(true)
    }

    fn become_error(&mut self, call: NodeId) {
        self.lib.tree.replace(call, NodeKind::Block, 0, &[]);
    }

    /// A sealed interface's subtypes in its module that aren't sealed
    /// interfaces, looking through those, as Kotlin's: each `Class` and
    /// its name, breadth first, each kind of subtype in source order.
    fn json_subtypes(&self, body: SealedBody) -> Vec<(NodeId, NameId)> {
        let tree = &self.lib.tree;
        let mut classes = Vec::new();
        for &f in &self.lib.modules[body.module.0 as usize].files {
            let root = self.lib.files[f.0 as usize].root;
            for &s in tree.children(root) {
                if tree.kind(s) == NodeKind::Class {
                    classes.push(s);
                }
            }
        }
        let super_name = |node: NodeId| -> Option<NameId> {
            match tree.kind(node) {
                NodeKind::RightName => self.lib.name(node),
                NodeKind::Call => tree
                    .children(node)
                    .iter()
                    .skip(1)
                    .find(|&&c| tree.kind(c) == NodeKind::RightName)
                    .and_then(|&c| self.lib.name(c)),
                _ => None,
            }
        };
        let mut queue = std::collections::VecDeque::from([(body.class, body.name)]);
        let mut visited = Vec::new();
        let mut found = Vec::new();
        while let Some((class, name)) = queue.pop_front() {
            if visited.contains(&name) {
                continue;
            }
            visited.push(name);
            let is_sealed = class == body.class
                || tree.data(class) & (CLASS_SEALED | CLASS_INTERFACE)
                    == CLASS_SEALED | CLASS_INTERFACE;
            if !is_sealed {
                found.push((class, name));
                continue;
            }
            for &c in &classes {
                let (class_name, supers, _) = class_parts(tree, c);
                if supers.iter().any(|&s| super_name(s) == Some(name)) {
                    if let Some(n) = self.lib.name(class_name) {
                        queue.push_back((c, n));
                    }
                }
            }
        }
        found
    }

    /// A sealed interface's decoder body: a decision tree over its
    /// `@json` subtypes' properties, as Kotlin's.
    fn sealed_decoder(
        &self,
        g: &mut Gen,
        subtypes: &[(NodeId, NameId)],
        class_text: &str,
    ) -> Result<String, String> {
        // Each JSON key's classification per subtype: present, or a
        // known value; a key that's both, or two values, is present.
        let mut keys: Vec<(String, Vec<Option<Test>>)> = Vec::new();
        for (i, (class, _)) in subtypes.iter().enumerate() {
            for (key, known) in &self.json.shapes[class] {
                let test = match known {
                    Some(v) => Test::Exact(v.clone()),
                    None => Test::Present,
                };
                let at = match keys.iter().position(|(k, _)| k == key) {
                    Some(at) => at,
                    None => {
                        keys.push((key.clone(), vec![None; subtypes.len()]));
                        keys.len() - 1
                    }
                };
                let slot = &mut keys[at].1[i];
                *slot = Some(match slot.take() {
                    Some(old) if old != test => Test::Present,
                    _ => test,
                });
            }
        }
        let (mut only_known, mut some_known, mut present) = (Vec::new(), Vec::new(), Vec::new());
        for (key, tests) in &keys {
            let known = tests.iter().any(|t| matches!(t, Some(Test::Exact(_))));
            let has_present = tests.iter().any(|t| matches!(t, Some(Test::Present)));
            match (known, has_present) {
                (true, false) => only_known.push(key.clone()),
                (true, true) => some_known.push(key.clone()),
                _ => present.push(key.clone()),
            }
        }
        let features: Vec<String> = only_known
            .iter()
            .chain(&some_known)
            .chain(&present)
            .cloned()
            .collect();
        let categorize = |case: usize, key: &str| -> Vec<Test> {
            let test = keys
                .iter()
                .find(|(k, _)| k == key)
                .and_then(|(_, tests)| tests[case].clone());
            match test {
                Some(t @ Test::Exact(_)) if only_known.iter().any(|k| k == key) => vec![t],
                Some(t @ Test::Exact(_)) => vec![t, Test::Present],
                Some(t) => vec![t],
                None => vec![Test::Absent],
            }
        };
        let cases: Vec<usize> = (0..subtypes.len()).collect();
        let tree = decision_tree(&cases, &features, &categorize);
        let object = g.std("JsonObject");
        let mut text = format!("do {{\n  let obj = t as {object};\n");
        let mut names = Vec::new();
        self.plant_tree(g, &tree, subtypes, class_text, &mut names, &mut text, 1)?;
        text.push_str("  bubble()\n}\n");
        Ok(text)
    }

    /// One node of a sealed decoder's decision tree, as statements.
    #[allow(clippy::too_many_arguments)]
    fn plant_tree(
        &self,
        g: &mut Gen,
        tree: &Tree,
        subtypes: &[(NodeId, NameId)],
        class_text: &str,
        names: &mut Vec<String>,
        out: &mut String,
        depth: usize,
    ) -> Result<(), String> {
        let indent = "  ".repeat(depth);
        match tree {
            Tree::Leaf(cases) => match cases[..] {
                [] => {}
                [case] => {
                    let sub = g.ph(Sub::Name(subtypes[case].1));
                    writeln!(
                        out,
                        "{indent}return {sub}.jsonAdapter().decodeFromJson(obj, ic);"
                    )
                    .unwrap();
                }
                _ => {
                    let names: Vec<&str> = cases
                        .iter()
                        .map(|&c| self.lib.names.get(subtypes[c].1))
                        .collect();
                    return Err(format!(
                        "Ambiguous @json: decoder for sealed type {class_text} cannot distinguish [{}]",
                        names.join(", ")
                    ));
                }
            },
            Tree::Inner(key, choices) => {
                let base = value_for(key);
                let mut local = base.clone();
                let mut n = 1;
                while names.contains(&local) {
                    n += 1;
                    local = format!("{base}{n}");
                }
                names.push(local.clone());
                let key_ph = g.string(key);
                writeln!(
                    out,
                    "{indent}let {local} = obj.propertyValueOrNull({key_ph});"
                )
                .unwrap();
                // Known values, grouped by their syntax tree's type, in
                // the order first seen.
                let mut exact: Vec<(&'static str, Vec<(&Value, &Tree)>)> = Vec::new();
                let mut present_or_absent = Vec::new();
                for (test, sub) in choices {
                    match test {
                        Test::Absent => present_or_absent.push((false, sub)),
                        Test::Present => present_or_absent.push((true, sub)),
                        Test::Exact(v) => {
                            let ty = match v {
                                Value::Bool(_) => "JsonBoolean",
                                Value::Int32(_) => "JsonInt32",
                                Value::Float64(_) => "JsonFloat64",
                                Value::String(_) => "JsonString",
                                _ => "JsonNull",
                            };
                            match exact.iter_mut().find(|(t, _)| *t == ty) {
                                Some((_, list)) => list.push((v, sub)),
                                None => exact.push((ty, vec![(v, sub)])),
                            }
                        }
                    }
                }
                let checks = |g: &mut Gen,
                              names: &mut Vec<String>,
                              out: &mut String,
                              depth: usize|
                 -> Result<(), String> {
                    let indent = "  ".repeat(depth);
                    writeln!(out, "{indent}do {{").unwrap();
                    for &(is_present, sub) in &present_or_absent {
                        let test = if is_present { "!=" } else { "==" };
                        writeln!(out, "{indent}  if ({local} {test} null) {{").unwrap();
                        self.plant_tree(g, sub, subtypes, class_text, names, out, depth + 2)?;
                        writeln!(out, "{indent}  }}").unwrap();
                    }
                    writeln!(out, "{indent}}}").unwrap();
                    Ok(())
                };
                if exact.is_empty() {
                    checks(g, names, out, depth)?;
                } else {
                    writeln!(out, "{indent}when ({local}) {{").unwrap();
                    for (ty, list) in &exact {
                        let ty_ph = g.std(ty);
                        if *ty == "JsonNull" {
                            // No content to compare: the value is null.
                            writeln!(out, "{indent}  is {ty_ph} -> do {{").unwrap();
                            for (_, sub) in list {
                                self.plant_tree(
                                    g,
                                    sub,
                                    subtypes,
                                    class_text,
                                    names,
                                    out,
                                    depth + 2,
                                )?;
                            }
                            writeln!(out, "{indent}  }};").unwrap();
                            continue;
                        }
                        writeln!(out, "{indent}  is {ty_ph} -> when ({local}.content) {{").unwrap();
                        for (v, sub) in list {
                            let v = g.ph(Sub::Value((*v).clone()));
                            writeln!(out, "{indent}    {v} -> do {{").unwrap();
                            self.plant_tree(g, sub, subtypes, class_text, names, out, depth + 3)?;
                            writeln!(out, "{indent}    }};").unwrap();
                        }
                        writeln!(out, "{indent}  }};").unwrap();
                    }
                    writeln!(out, "{indent}  else ->").unwrap();
                    checks(g, names, out, depth + 2)?;
                    writeln!(out, "{indent}}}").unwrap();
                }
                names.pop();
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn value_for_names() {
        assert_eq!(value_for("x"), "valueForX");
        assert_eq!(value_for("message-text"), "valueForMessageText");
        assert_eq!(value_for("class"), "valueForClass");
    }

    #[test]
    fn decision_tree_prefers_the_most_entropy() {
        // Kotlin's simpleSealedType: A has x and y, B x and z, C y and z.
        let has = [["x", "y"], ["x", "z"], ["y", "z"]];
        let features: Vec<String> = ["x", "y", "z"].map(String::from).to_vec();
        let categorize = |case: usize, key: &str| {
            vec![if has[case].contains(&key) {
                Test::Present
            } else {
                Test::Absent
            }]
        };
        let tree = decision_tree(&[0, 1, 2], &features, &categorize);
        let Tree::Inner(key, choices) = tree else {
            panic!("a leaf")
        };
        assert_eq!(key, "x");
        assert_eq!(choices[0].0, Test::Present);
        assert!(matches!(&choices[1], (Test::Absent, Tree::Leaf(c)) if c == &[2]));
        let Tree::Inner(key, inner) = &choices[0].1 else {
            panic!("a leaf")
        };
        assert_eq!(key, "y");
        assert!(matches!(&inner[0], (Test::Present, Tree::Leaf(c)) if c == &[0]));
        assert!(matches!(&inner[1], (Test::Absent, Tree::Leaf(c)) if c == &[1]));
    }
}
