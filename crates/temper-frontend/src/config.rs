//! A library's config file, `config.temper.md`, read as Temper code:
//! what it exports ([`read`]), and the config classes that a build's
//! backends inject into its scope, checked ([`check`]).
//!
//! An export is a string, as `export let license = "MIT";`, or an
//! instance of a config class, as
//! `export let KEY = { class: CLASS, PROPERTY: "VALUE", … };`. Each config
//! class is a [`ConfigType`] that the build gives, one per backend; this
//! module never names one. Kotlin runs the config module, with those
//! classes declared in it; we don't run it, so values are literals: a
//! string, or `null` for none. A class this scope doesn't have, a
//! property its class doesn't have, a value of another type, or an
//! instance under another key is a config error, at its place in the file.
//! Every other export is an ordinary value, which nothing need read.

use temper_syntax::ast::{Ast, Builtin, Level, Value as Literal};
use temper_syntax::lex::SourceKind;
use temper_syntax::tree::{NodeId, NodeKind, Span};
use temper_tmpl::ConfigType;

/// An export: its name, where that is, and its value.
#[derive(Clone, Debug, PartialEq)]
pub struct Export {
    pub key: String,
    pub span: Span,
    pub value: Value,
}

/// An export's value, or a property's.
#[derive(Clone, Debug, PartialEq)]
pub enum Value {
    String(String),
    Null,
    /// `{ class: CLASS, … }`, or `new CLASS(…)`: the class's name and
    /// where it is, and its arguments.
    Instance {
        class: String,
        span: Span,
        args: Vec<Arg>,
    },
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
        let init = (rest.chunks(2))
            .find(|pair| symbol(&ast, pair[0]) == Some("init"))
            .and_then(|pair| pair.get(1));
        if let Some(&init) = init {
            exports.push(Export {
                key: key.to_string(),
                span: tree.span(*declared),
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
        [callee, ..] if builtin(*callee) == Some(Builtin::List) => Value::Other("a List"),
        [new, class, args @ ..] if name(ast, *new) == Some("new") => {
            let Some(class_name) = name(ast, *class) else {
                return Value::Other("an object without a class");
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
            Value::Instance {
                class: class_name.to_string(),
                span: tree.span(*class),
                args: found,
            }
        }
        _ => Value::Computed,
    }
}

/// A config's exports, checked against the config classes in its scope:
/// each class's key with the properties set, in order, and the problems.
#[derive(Debug, Default, PartialEq)]
pub struct Checked {
    pub configs: Vec<(String, Vec<(String, String)>)>,
    pub problems: Vec<Problem>,
}

/// Checks a config's exports against the config classes in its scope.
pub fn check(exports: &[Export], types: &[&ConfigType]) -> Checked {
    let mut checked = Checked::default();
    let mut problem = |span: Span, message: String| {
        checked.problems.push(Problem { span, message });
    };
    let mut configs = Vec::new();
    for export in exports {
        let by_key = types.iter().find(|t| t.key == export.key);
        let Value::Instance { class, span, args } = &export.value else {
            if let Some(t) = by_key {
                let (key, class) = (t.key, t.class);
                let message = format!(
                    "`{key}` is for {class}, as `export let {key} = {{ class: {class}, … }}`"
                );
                problem(export.span, message);
            }
            continue;
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
        if t.key != export.key {
            let message = format!(
                "{} goes in `export let {}`, not `{}`",
                t.class, t.key, export.key
            );
            problem(export.span, message);
            continue;
        }
        let mut values = Vec::new();
        for Arg {
            property,
            span: at,
            value,
        } in args
        {
            let Some((property, name_span)) = property else {
                let message = format!(
                    "Name each property of {}, as `{{ class: {}, name: value }}`",
                    t.class, t.class
                );
                problem(*at, message);
                continue;
            };
            if !t.properties.iter().any(|p| p.name == property) {
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
            }
            if values.iter().any(|(p, _)| p == property) {
                let message = format!("{}'s `{property}` is set twice", t.class);
                problem(*name_span, message);
                continue;
            }
            match value {
                Value::String(s) => values.push((property.clone(), s.clone())),
                Value::Null => {}
                Value::Other(what) => {
                    let message = format!("{}'s `{property}` is a String, not {what}", t.class);
                    problem(*at, message);
                }
                Value::Instance { .. } => {
                    let message = format!("{}'s `{property}` is a String, not an object", t.class);
                    problem(*at, message);
                }
                Value::Computed => {
                    let message = format!(
                        "{}'s `{property}` needs a String literal: config files aren't run, so \
                         values aren't computed",
                        t.class
                    );
                    problem(*at, message);
                }
            }
        }
        configs.push((t.key.to_string(), values));
    }
    checked.configs = configs;
    checked
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
            },
            Property {
                name: "flavor",
                doc: "",
            },
        ],
        std: &[],
    };

    const EMPTY: ConfigType = ConfigType {
        key: "empty",
        class: "EmptyConfig",
        properties: &[],
        std: &[],
    };

    /// Each class's key and the properties set.
    type Configs = Vec<(String, Vec<(String, String)>)>;

    fn checked(text: &str) -> (Configs, Vec<String>) {
        let (exports, problems) = read(text);
        assert_eq!(problems, []);
        let checked = check(&exports, &[&TOY, &EMPTY]);
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

    #[test]
    fn instances_across_code_blocks() {
        let text = "# Toy\n\n    export let name = \"t\";\n    export let toy = {\n      \
                    class: ToyConfig,\n      name: \"toy-\\\"t\\\"\",\n\nProse.\n\n      \
                    flavor: null,\n    };\n    export let empty = new EmptyConfig();\n";
        let (configs, problems) = checked(text);
        assert_eq!(problems, Vec::<String>::new());
        let toy = ("toy".to_string(), vec![("name".into(), "toy-\"t\"".into())]);
        assert_eq!(configs, [toy, ("empty".to_string(), vec![])]);
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
                "empty: `empty` is for EmptyConfig, as `export let empty = { class: \
                 EmptyConfig, … }`",
                "toy: EmptyConfig goes in `export let empty`, not `toy`",
                "\"a${b}\": ToyConfig's `name` needs a String literal: config files aren't \
                 run, so values aren't computed",
                "[\"x\"]: ToyConfig's `flavor` is a String, not a List",
                "\"a\": Name each property of ToyConfig, as `{ class: ToyConfig, name: value }`",
            ]
        );
    }

    #[test]
    fn suggestions() {
        let names = ["name", "package", "group", "artifact"];
        assert_eq!(suggest("pacakge", names.iter().copied()), Some("package"));
        assert_eq!(suggest("Name", names.iter().copied()), Some("name"));
        assert_eq!(suggest("version", names.iter().copied()), None);
    }
}
