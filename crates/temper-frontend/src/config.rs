//! A library's config file, `config.temper.md`, read as Temper code:
//! what it exports ([`read`]), and the config classes that a build's
//! backends inject into its scope, checked ([`check`]).
//!
//! An export is a string, as `export let license = "MIT";`, or an
//! instance of a config class, as
//! `export let KEY: CLASS = { PROPERTY: VALUE, … };`, the object taking
//! its class from the declared type, or as
//! `export let KEY = { class: CLASS, … };`. Each config class is a
//! [`ConfigType`] that the build gives, one per backend; this module
//! never names one. Kotlin runs the config module, with those classes
//! declared in it; we don't run it, so values are literals: a string, a
//! list of strings, or `null` for none. A class this scope doesn't have,
//! a declared type that disagrees with `class:`, a property its class
//! doesn't have, a value of another type or form, or an instance under
//! another key is a config error, at its place in the file. Every other
//! export is an ordinary value, which nothing need read, but strings go
//! into package files as metadata: a `version` that isn't SemVer, or a
//! string holding control characters other than tabs and line breaks,
//! is an error too.

use temper_syntax::ast::{Ast, Builtin, Level, Value as Literal};
use temper_syntax::lex::SourceKind;
use temper_syntax::tree::{NodeId, NodeKind, Span};
use temper_tmpl::{ConfigKind, ConfigType, ConfigValue};

/// An export: its name, where that is, its declared type's text and
/// where that is, if it has one, and its value.
#[derive(Clone, Debug, PartialEq)]
pub struct Export {
    pub key: String,
    pub span: Span,
    pub declared: Option<(String, Span)>,
    pub value: Value,
}

/// An export's value, or a property's.
#[derive(Clone, Debug, PartialEq)]
pub enum Value {
    String(String),
    Null,
    /// `{ class: CLASS, … }`, or `new CLASS(…)`: the class's name and
    /// where it is, unless it's `{ … }`, which takes its class from the
    /// declared type; and its arguments.
    Instance {
        class: Option<(String, Span)>,
        args: Vec<Arg>,
    },
    /// `[…]`: each item, and where it is.
    List(Vec<(Span, Value)>),
    /// A literal of another type, as `an Int`.
    Other(&'static str),
    /// An expression, which would need running.
    Computed,
}

/// An argument of an instance: its property's name and where that is,
/// unless it's positional; and its value and where that is.
#[derive(Clone, Debug, PartialEq)]
pub struct Arg {
    pub property: Option<(String, Span)>,
    pub span: Span,
    pub value: Value,
}

/// A config error: where, and what's wrong.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Problem {
    pub span: Span,
    pub message: String,
}

/// A config file's exports, in order, and its syntax errors.
pub fn read(text: &str) -> (Vec<Export>, Vec<Problem>) {
    let parsed = temper_syntax::parse::parse(text, SourceKind::Markdown);
    let ast = temper_syntax::ast::build(&parsed);
    let mut problems: Vec<Problem> = (parsed.errors().iter())
        .map(|e| Problem {
            span: e.span,
            message: e.message(),
        })
        .collect();
    problems.extend(
        (ast.diagnostics().iter())
            .filter(|d| d.level == Level::Error)
            .map(|d| Problem {
                span: d.span,
                message: d.message.text(),
            }),
    );
    let tree = ast.tree();
    let mut exports = Vec::new();
    for &statement in tree.children(ast.root()) {
        // `@export` applied to a declaration: `(Call @ export (Decl …))`.
        let decl = match tree.children(statement) {
            [at, export, decl]
                if tree.kind(statement) == NodeKind::Call
                    && name(&ast, *at) == Some("@")
                    && name(&ast, *export) == Some("export")
                    && tree.kind(*decl) == NodeKind::Decl =>
            {
                *decl
            }
            _ => continue,
        };
        let [declared, rest @ ..] = tree.children(decl) else {
            continue;
        };
        let Some(key) = name(&ast, *declared) else {
            continue;
        };
        let part = |which: &str| {
            (rest.chunks(2))
                .find(|pair| symbol(&ast, pair[0]) == Some(which))
                .and_then(|pair| pair.get(1).copied())
        };
        let declared_type = part("type").map(|t| {
            let span = tree.span(t);
            let text = text.get(span.start as usize..span.end as usize);
            (text.unwrap_or_default().to_string(), span)
        });
        if let Some(init) = part("init") {
            exports.push(Export {
                key: key.to_string(),
                span: tree.span(*declared),
                declared: declared_type,
                value: value(&ast, init),
            });
        }
    }
    (exports, problems)
}

/// A name node's text.
fn name(ast: &Ast, id: NodeId) -> Option<&str> {
    ast.name(id).map(|n| ast.names().get(n))
}

/// A symbol value's text, as `init` for `\init`.
fn symbol(ast: &Ast, id: NodeId) -> Option<&str> {
    match ast.value(id) {
        Some(Literal::Symbol(n)) => Some(ast.names().get(*n)),
        _ => None,
    }
}

/// What an expression is, as far as a config needs.
fn value(ast: &Ast, id: NodeId) -> Value {
    let tree = ast.tree();
    if let Some(literal) = ast.value(id) {
        return match literal {
            Literal::String(s) => Value::String(s.clone()),
            Literal::Null => Value::Null,
            Literal::Int32(_) => Value::Other("an Int"),
            Literal::Int64(_) => Value::Other("an Int64"),
            Literal::Float64(_) => Value::Other("a Float64"),
            Literal::Bool(_) => Value::Other("a Boolean"),
            _ => Value::Computed,
        };
    }
    match name(ast, id) {
        Some("null") => return Value::Null,
        Some("true" | "false") => return Value::Other("a Boolean"),
        _ if tree.kind(id) != NodeKind::Call => return Value::Computed,
        _ => {}
    }
    let children = tree.children(id);
    let builtin = |id: NodeId| match ast.value(id) {
        Some(Literal::Builtin(b)) => Some(*b),
        _ => None,
    };
    match children {
        // A string without interpolation: its parts, each a literal.
        [callee, tag, tagged, parts @ ..]
            if builtin(*callee) == Some(Builtin::StringExpr)
                && ast.value(*tag) == Some(&Literal::Null)
                && ast.value(*tagged) == Some(&Literal::Bool(false)) =>
        {
            let mut text = String::new();
            for &part in parts {
                match ast.value(part) {
                    Some(Literal::String(s)) => text.push_str(s),
                    _ => return Value::Computed,
                }
            }
            Value::String(text)
        }
        [callee, items @ ..] if builtin(*callee) == Some(Builtin::List) => Value::List(
            (items.iter())
                .map(|&i| (tree.span(i), value(ast, i)))
                .collect(),
        ),
        [new, class, args @ ..] if name(ast, *new) == Some("new") => {
            let class = match name(ast, *class) {
                Some(n) => Some((n.to_string(), tree.span(*class))),
                None if ast.value(*class) == Some(&Literal::Void) => None,
                None => return Value::Other("an object of a class that isn't named"),
            };
            let mut found = Vec::new();
            let mut args = args.iter();
            while let Some(&arg) = args.next() {
                let (property, arg) = match symbol(ast, arg) {
                    Some(property) => {
                        let Some(&v) = args.next() else { break };
                        (Some((property.to_string(), tree.span(arg))), v)
                    }
                    None => (None, arg),
                };
                found.push(Arg {
                    property,
                    span: tree.span(arg),
                    value: value(ast, arg),
                });
            }
            Value::Instance { class, args: found }
        }
        _ => Value::Computed,
    }
}

/// The config classes in a build's scope: those its backends inject, and
/// the keys of backends that configs may set but this build doesn't have
/// (a build with fewer backends, or one that hasn't loaded them yet),
/// whose exports are skipped, unchecked, so a library's config holds
/// whichever binary builds it. Empty, configs aren't checked.
#[derive(Clone, Debug, Default)]
pub struct Classes {
    pub types: Vec<&'static ConfigType>,
    pub elsewhere: Vec<&'static str>,
}

impl Classes {
    /// Whether there are none, so configs aren't checked.
    pub fn is_empty(&self) -> bool {
        self.types.is_empty() && self.elsewhere.is_empty()
    }
}

/// A config's exports, checked against the config classes in its scope:
/// each class's key with the properties set, in order, and the problems.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Checked {
    pub configs: Vec<(String, Vec<(String, ConfigValue)>)>,
    pub problems: Vec<Problem>,
}

/// Checks a config's exports against the config classes in its scope.
pub fn check(exports: &[Export], classes: &Classes) -> Checked {
    let types = &classes.types;
    let mut checked = Checked::default();
    let mut problem = |span: Span, message: String| {
        checked.problems.push(Problem { span, message });
    };
    let mut configs = Vec::new();
    for export in exports {
        let key = export.key.as_str();
        let by_key = types.iter().find(|t| t.key == key);
        if by_key.is_none() && classes.elsewhere.contains(&key) {
            continue;
        }
        if let Value::String(s) = &export.value {
            if let Some(message) = string_problem(key, s) {
                problem(export.span, message);
            }
        }
        let declared = export.declared.as_ref();
        let Value::Instance { class, args } = &export.value else {
            // A string or the like, which is fine but where a config
            // class is due, by its key or its declared type.
            let by_type = declared.and_then(|(d, _)| types.iter().find(|t| t.class == d));
            if let Some(t) = by_key.or(by_type) {
                let (key, class) = (t.key, t.class);
                let message = match by_key {
                    Some(_) => format!("`{key}` is for {class}, as "),
                    None => format!("A {class} is an object, as "),
                };
                problem(
                    export.span,
                    message + &format!("`export let {key}: {class} = {{ … }}`"),
                );
            }
            continue;
        };
        // Its class, as `class:` names it, or else its declared type.
        let (class, span) = match (class, declared) {
            (Some((class, span)), Some((d, _))) if class != d => {
                let message = format!("`class: {class}` disagrees with the declared type, `{d}`");
                problem(*span, message);
                continue;
            }
            (Some((class, span)), _) | (None, Some((class, span))) => (class, span),
            (None, None) => {
                if let Some(t) = by_key {
                    let (key, class) = (t.key, t.class);
                    let message = format!(
                        "`{key}` needs its class, as `export let {key}: {class} = {{ … }}`"
                    );
                    problem(export.span, message);
                }
                continue;
            }
        };
        let Some(t) = types.iter().find(|t| t.class == class) else {
            let message = match (by_key, suggest(class, types.iter().map(|t| t.class))) {
                (Some(t), _) => format!("`{}` is for {}, not `{class}`", t.key, t.class),
                (None, Some(meant)) => {
                    format!("No config class `{class}`; did you mean `{meant}`?")
                }
                (None, None) => {
                    let classes = (types.iter()).map(|t| format!("`{}`", t.class));
                    format!(
                        "No config class `{class}`; those here are {}",
                        listed(classes.collect())
                    )
                }
            };
            problem(*span, message);
            continue;
        };
        if t.key != key {
            let message = format!("{} goes in `export let {}`, not `{key}`", t.class, t.key);
            problem(export.span, message);
            continue;
        }
        let mut values: Vec<(String, ConfigValue)> = Vec::new();
        for Arg {
            property,
            span: at,
            value,
        } in args
        {
            let Some((property, name_span)) = property else {
                let message = format!("Name each property of {}, as `{{ name: value }}`", t.class);
                problem(*at, message);
                continue;
            };
            let Some(p) = t.properties.iter().find(|p| p.name == property) else {
                let names = t.properties.iter().map(|p| p.name);
                let message = match (suggest(property, names.clone()), t.properties) {
                    (Some(meant), _) => format!(
                        "{} has no property `{property}`; did you mean `{meant}`?",
                        t.class
                    ),
                    (None, []) => format!("{} has no properties yet", t.class),
                    (None, _) => format!(
                        "{} has no property `{property}`; it has {}",
                        t.class,
                        listed(names.map(|n| format!("`{n}`")).collect())
                    ),
                };
                problem(*name_span, message);
                continue;
            };
            if values.iter().any(|(p, _)| p == property) {
                let message = format!("{}'s `{property}` is set twice", t.class);
                problem(*name_span, message);
                continue;
            }
            let whose = format!("{}'s `{property}`", t.class);
            match (p.kind, value) {
                (_, Value::Null) => {}
                // A blank name is unset, as `null` is.
                (ConfigKind::String { valid, .. }, Value::String(s))
                    if s.trim().is_empty() || valid(s) =>
                {
                    values.push((property.clone(), ConfigValue::String(s.clone())))
                }
                (ConfigKind::String { form, .. }, Value::String(s)) => {
                    problem(*at, format!("{whose} is {form}, not `{s}`"))
                }
                (ConfigKind::String { .. }, value) => {
                    problem(*at, wrong(&whose, value, "a String"))
                }
                (ConfigKind::Strings { form, package }, Value::List(items)) => {
                    let mut strings = Vec::new();
                    let mut packages = Vec::new();
                    for (span, item) in items {
                        match item {
                            Value::String(s) => match package(s.trim()) {
                                Some(p) if packages.contains(&p) => {
                                    let message = format!("{whose} lists `{p}` twice");
                                    problem(*span, message);
                                }
                                Some(p) => {
                                    packages.push(p);
                                    strings.push(s.trim().to_string());
                                }
                                None => {
                                    let message =
                                        format!("{whose} are each `{form}`, not `{}`", s.trim());
                                    problem(*span, message);
                                }
                            },
                            Value::Computed => problem(*span, wrong(&whose, item, "a String")),
                            _ => {
                                let message = format!("{whose} holds Strings, not {}", what(item));
                                problem(*span, message);
                            }
                        }
                    }
                    values.push((property.clone(), ConfigValue::Strings(strings)));
                }
                (ConfigKind::Strings { .. }, value) => {
                    problem(*at, wrong(&whose, value, "a List<String>"))
                }
            }
        }
        // One object per class, as a module declares a name once; a
        // second would leave which one counts unclear.
        if configs.iter().any(|(k, _)| k == t.key) {
            let message = format!("`{}` is set twice; set each config class once", t.key);
            problem(export.span, message);
            continue;
        }
        configs.push((t.key.to_string(), values));
    }
    checked.configs = configs;
    checked
}

/// What's wrong with a string the config exports, if anything. Package
/// files take them all as metadata, and a library's version goes into
/// each using library's files too: a version is SemVer, as
/// [`temper_tmpl::version::Version::parse`] takes it, and text holds no
/// control characters but tabs and line breaks, which no package file
/// needs and some (XML's) can't hold.
fn string_problem(key: &str, s: &str) -> Option<String> {
    if key == "version" && temper_tmpl::version::Version::parse(s).is_none() {
        let s = s.escape_debug();
        return Some(format!(
            "`version` is a version, as `1.2.3` or `1.2.3-beta.1`, not `{s}`"
        ));
    }
    let control = s
        .chars()
        .find(|c| c.is_control() && !matches!(c, '\t' | '\n' | '\r'))?;
    Some(format!(
        "`{key}` holds a control character, U+{:04X}; only tabs and line breaks may be in \
         config text",
        control as u32
    ))
}

/// Why a property's value is wrong, where it should be `want`, as `a
/// String`.
fn wrong(whose: &str, value: &Value, want: &str) -> String {
    match value {
        Value::Computed => format!(
            "{whose} needs {want} literal: config files aren't run, so values aren't computed"
        ),
        _ => format!("{whose} is {want}, not {}", what(value)),
    }
}

/// What a value is, as `a List`.
fn what(value: &Value) -> &'static str {
    match value {
        Value::String(_) => "a String",
        Value::Null => "null",
        Value::Instance { .. } => "an object",
        Value::List(_) => "a List",
        Value::Other(what) => what,
        Value::Computed => "a computed value",
    }
}

/// `a`, `a and b`, or `a, b, and c`.
fn listed(items: Vec<String>) -> String {
    match items.as_slice() {
        [] => "none".into(),
        [one] => one.clone(),
        [a, b] => format!("{a} and {b}"),
        [rest @ .., last] => format!("{}, and {last}", rest.join(", ")),
    }
}

/// The name among `names` that `typed` most likely meant: the same but
/// for case, or else the nearest within two edits, if one is.
fn suggest<'a>(typed: &str, names: impl Iterator<Item = &'a str> + Clone) -> Option<&'a str> {
    let lower = typed.to_lowercase();
    if let Some(same) = names.clone().find(|n| n.to_lowercase() == lower) {
        return Some(same);
    }
    (names.map(|n| (distance(&lower, &n.to_lowercase()), n)))
        .filter(|(d, _)| *d <= 2)
        .min_by_key(|(d, _)| *d)
        .map(|(_, n)| n)
}

/// Edits from one text to another: insertions, deletions, substitutions,
/// and swaps of neighbors, each one.
fn distance(a: &str, b: &str) -> usize {
    let (a, b): (Vec<char>, Vec<char>) = (a.chars().collect(), b.chars().collect());
    let mut rows = vec![(0..=b.len()).collect::<Vec<_>>(); a.len() + 1];
    for (i, row) in rows.iter_mut().enumerate() {
        row[0] = i;
    }
    for i in 1..=a.len() {
        for j in 1..=b.len() {
            let cost = usize::from(a[i - 1] != b[j - 1]);
            let mut d = (rows[i - 1][j] + 1)
                .min(rows[i][j - 1] + 1)
                .min(rows[i - 1][j - 1] + cost);
            if i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1] {
                d = d.min(rows[i - 2][j - 2] + 1);
            }
            rows[i][j] = d;
        }
    }
    rows[a.len()][b.len()]
}

#[cfg(test)]
mod tests {
    use super::*;
    use temper_tmpl::Property;

    /// A config class that no backend has, so these tests show the
    /// frontend checks whatever it's given.
    const TOY: ConfigType = ConfigType {
        key: "toy",
        class: "ToyConfig",
        properties: &[
            Property {
                name: "name",
                doc: "",
                kind: ConfigKind::String {
                    form: "a word, as `toy`",
                    valid: |s| {
                        s.chars()
                            .all(|c| c.is_ascii_alphanumeric() || "-\"".contains(c))
                    },
                },
            },
            Property {
                name: "flavor",
                doc: "",
                kind: ConfigKind::String {
                    form: "anything",
                    valid: |_| true,
                },
            },
            Property {
                name: "parts",
                doc: "",
                kind: ConfigKind::Strings {
                    form: "name@version",
                    package: |s| s.split_once('@').map(|(name, _)| name.to_lowercase()),
                },
            },
        ],
        std: &[],
        names: temper_tmpl::no_names,
        runtime: &[],
    };

    const EMPTY: ConfigType = ConfigType {
        key: "empty",
        class: "EmptyConfig",
        properties: &[],
        std: &[],
        names: temper_tmpl::no_names,
        runtime: &[],
    };

    /// [`TOY`] and [`EMPTY`].
    fn both() -> Classes {
        Classes {
            types: vec![&TOY, &EMPTY],
            elsewhere: Vec::new(),
        }
    }

    /// Each class's key and the properties set.
    type Configs = Vec<(String, Vec<(String, ConfigValue)>)>;

    fn checked(text: &str) -> (Configs, Vec<String>) {
        checked_with(text, &both())
    }

    fn checked_with(text: &str, classes: &Classes) -> (Configs, Vec<String>) {
        let (exports, problems) = read(text);
        assert_eq!(problems, []);
        let checked = check(&exports, classes);
        let problems = (checked.problems.iter())
            .map(|p| {
                format!(
                    "{}: {}",
                    &text[p.span.start as usize..p.span.end as usize],
                    p.message
                )
            })
            .collect();
        (checked.configs, problems)
    }

    fn string(s: &str) -> ConfigValue {
        ConfigValue::String(s.into())
    }

    #[test]
    fn instances_across_code_blocks() {
        let text = "# Toy\n\n    export let name = \"t\";\n    export let toy = {\n      \
                    class: ToyConfig,\n      name: \"toy-\\\"t\\\"\",\n\nProse.\n\n      \
                    flavor: null,\n    };\n    export let empty = new EmptyConfig();\n";
        let (configs, problems) = checked(text);
        assert_eq!(problems, Vec::<String>::new());
        let toy = (
            "toy".to_string(),
            vec![("name".into(), string("toy-\"t\""))],
        );
        assert_eq!(configs, [toy, ("empty".to_string(), vec![])]);
    }

    /// The object takes its class from the declared type, or names it
    /// too, the same; lists hold strings, each trimmed.
    #[test]
    fn declared_types_and_lists() {
        let text = "    export let toy: ToyConfig = {
      name: \"t\",
      parts: [\"a@1\", \" b@2 \",],
    };
    export let empty: EmptyConfig = { class: EmptyConfig };
    export let toy: ToyConfig = { parts: [], flavor: null };
    export let license: String = \"MIT\";
    export let names: List<String> = [\"x\"];
";
        let (configs, problems) = checked(text);
        // The second `toy` is checked, but one class is set once.
        assert_eq!(
            problems,
            ["toy: `toy` is set twice; set each config class once"]
        );
        let parts = |items: &[&str]| {
            let items = items.iter().map(|s| s.to_string()).collect();
            ("parts".to_string(), ConfigValue::Strings(items))
        };
        assert_eq!(
            configs,
            [
                (
                    "toy".to_string(),
                    vec![("name".into(), string("t")), parts(&["a@1", "b@2"])]
                ),
                ("empty".to_string(), vec![]),
            ]
        );
    }

    #[test]
    fn mistakes_are_problems() {
        let text = "    export let toy = { class: ToyConfig, nmae: \"a\", flavor: 1 };
    export let empty = { class: EmptyConfig, x: \"\" };
    export let toys = { class: ToyConfig };
    export let t = { class: ToyConfg };
    export let u = { class: Unknown };
    export let empty = \"e\";
    export let toy = { class: EmptyConfig };
    export let toy = { class: ToyConfig, name: \"a${b}\", flavor: [\"x\"] };
    export let toy = new ToyConfig(\"a\");
    export let other = 1;
    export let toy = { class: ToyConfig, name: \"../a\" };
    export let toy = { class: ToyConfig, name: \" \" };
";
        let (_, problems) = checked(text);
        assert_eq!(
            problems,
            [
                "nmae: ToyConfig has no property `nmae`; did you mean `name`?",
                "1: ToyConfig's `flavor` is a String, not an Int",
                "x: EmptyConfig has no properties yet",
                "toys: ToyConfig goes in `export let toy`, not `toys`",
                "ToyConfg: No config class `ToyConfg`; did you mean `ToyConfig`?",
                "Unknown: No config class `Unknown`; those here are `ToyConfig` and \
                 `EmptyConfig`",
                "empty: `empty` is for EmptyConfig, as `export let empty: EmptyConfig = { … }`",
                "toy: EmptyConfig goes in `export let empty`, not `toy`",
                "\"a${b}\": ToyConfig's `name` needs a String literal: config files aren't \
                 run, so values aren't computed",
                "[\"x\"]: ToyConfig's `flavor` is a String, not a List",
                "toy: `toy` is set twice; set each config class once",
                "\"a\": Name each property of ToyConfig, as `{ name: value }`",
                "toy: `toy` is set twice; set each config class once",
                "\"../a\": ToyConfig's `name` is a word, as `toy`, not `../a`",
                "toy: `toy` is set twice; set each config class once",
                "toy: `toy` is set twice; set each config class once",
            ]
        );
    }

    /// A version is SemVer, as package files take it, and metadata holds
    /// no control characters but tabs and line breaks: both go into
    /// package files' markup, and a library's version into those of each
    /// library using it.
    #[test]
    fn versions_and_metadata_text() {
        let text = "    export let version = \"1.2.3-beta.1\";
    export let description = \"Line one\\n\\tline two\\r\\n\";
    export let version = \"0.1.0\\\"<x>'\\n\";
    export let version = \"1.0\";
    export let version = \"\";
    export let authors = \"A\\u{1}B\";
    export let license = \"MIT\\u{0}\";
";
        let (_, problems) = checked(text);
        assert_eq!(
            problems,
            [
                "version: `version` is a version, as `1.2.3` or `1.2.3-beta.1`, not \
                 `0.1.0\\\"<x>\\'\\n`",
                "version: `version` is a version, as `1.2.3` or `1.2.3-beta.1`, not `1.0`",
                "version: `version` is a version, as `1.2.3` or `1.2.3-beta.1`, not ``",
                "authors: `authors` holds a control character, U+0001; only tabs and line \
                 breaks may be in config text",
                "license: `license` holds a control character, U+0000; only tabs and line \
                 breaks may be in config text",
            ]
        );
    }

    /// Declared types that name no config class, or disagree with
    /// `class:`, or aren't objects; objects without classes; and lists
    /// that aren't, or hold what they shouldn't.
    #[test]
    fn declared_type_and_list_mistakes() {
        let text = "    export let toy: ToyConfg = { name: \"a\" };
    export let toy: ToyConfig = { class: EmptyConfig };
    export let toy: Other.Config = { name: \"a\" };
    export let toys: ToyConfig = { name: \"a\" };
    export let toy = { name: \"a\" };
    export let x: ToyConfig = \"a\";
    export let toy: ToyConfig = { parts: \"a@1\" };
    export let toy: ToyConfig = { parts: [\"a@1\", \"b\", 3, \"${c}\"] };
    export let toy: ToyConfig = { name: [\"a\"] };
    export let thing = { name: \"a\" };
";
        let (_, problems) = checked(text);
        assert_eq!(
            problems,
            [
                "ToyConfg: `toy` is for ToyConfig, not `ToyConfg`",
                "EmptyConfig: `class: EmptyConfig` disagrees with the declared type, \
                 `ToyConfig`",
                "Other.Config: `toy` is for ToyConfig, not `Other.Config`",
                "toys: ToyConfig goes in `export let toy`, not `toys`",
                "toy: `toy` needs its class, as `export let toy: ToyConfig = { … }`",
                "x: A ToyConfig is an object, as `export let toy: ToyConfig = { … }`",
                "\"a@1\": ToyConfig's `parts` is a List<String>, not a String",
                "\"b\": ToyConfig's `parts` are each `name@version`, not `b`",
                "3: ToyConfig's `parts` holds Strings, not an Int",
                "\"${c}\": ToyConfig's `parts` needs a String literal: config files aren't \
                 run, so values aren't computed",
                "toy: `toy` is set twice; set each config class once",
                "[\"a\"]: ToyConfig's `name` is a String, not a List",
                "toy: `toy` is set twice; set each config class once",
            ]
        );
    }

    /// A package listed twice, as the class compares packages (here,
    /// ignoring case), is a problem; the list keeps the first.
    #[test]
    fn packages_listed_twice() {
        let text = "    export let toy: ToyConfig = { parts: [\"a@1\", \"b@1\", \"A@2\"] };\n";
        let (configs, problems) = checked(text);
        assert_eq!(problems, ["\"A@2\": ToyConfig's `parts` lists `a` twice"]);
        let parts = vec!["a@1".to_string(), "b@1".to_string()];
        let toy = vec![("parts".to_string(), ConfigValue::Strings(parts))];
        assert_eq!(configs, [("toy".to_string(), toy)]);
    }

    /// A build without a backend skips what configs set for it, whatever
    /// its class, as another build checks it; other mistakes are still
    /// problems, suggesting the classes it has.
    #[test]
    fn classes_of_backends_elsewhere() {
        let classes = Classes {
            types: vec![&TOY],
            elsewhere: vec!["empty"],
        };
        let text = "    export let empty: EmptyConfig = { x: \"\" };
    export let empty = { class: Whatever };
    export let toy: ToyConfig = { name: \"t\" };
    export let other = { class: EmptyConfig };
    export let t = { class: ToyConfg };
";
        let (configs, problems) = checked_with(text, &classes);
        assert_eq!(
            problems,
            [
                "EmptyConfig: No config class `EmptyConfig`; those here are `ToyConfig`",
                "ToyConfg: No config class `ToyConfg`; did you mean `ToyConfig`?",
            ]
        );
        let toy = vec![("name".to_string(), string("t"))];
        assert_eq!(configs, [("toy".to_string(), toy)]);
    }

    #[test]
    fn suggestions() {
        let names = ["name", "package", "group", "artifact"];
        assert_eq!(suggest("pacakge", names.iter().copied()), Some("package"));
        assert_eq!(suggest("Name", names.iter().copied()), Some("name"));
        assert_eq!(suggest("version", names.iter().copied()), None);
    }
}
