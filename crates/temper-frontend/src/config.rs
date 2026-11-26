//! Libraries' config files, `config.temper.md`, settled as Temper modules
//! and run, all of a build's at once ([`settle_configs`]).
//!
//! Each config is a module of its own, as Kotlin's is, in one library
//! with a shared prelude: each backend's config class, as the backend
//! declares it in Temper ([`ConfigType::source`]), in a module that every
//! config imports, unseen, so the classes are in its scope. The standard
//! type checker checks configs, so a value may be computed, and a
//! mistake gets an ordinary diagnostic. Then each config's top level runs
//! once with the compile-time interpreter, and what it exports is read:
//!
//! - A string is metadata for package files, as `export let license =
//!   "MIT";`. A `version` that isn't SemVer, or text holding control
//!   characters other than tabs and line breaks, is a problem, as package
//!   files can't take them.
//! - An object of a config class, as `export let KEY: CLASS = { PROPERTY:
//!   VALUE, … };`, goes in the export of its key, once, and its values are
//!   checked by its backend ([`ConfigType::validate`]).
//!   A string or the like under a class's key is a problem.
//! - Anything else is an ordinary value, which nothing reads.
//!
//! This module never names a backend. Exports keyed to a backend this
//! build lacks ([`Classes::elsewhere`]) are dropped before settling, as
//! their classes aren't here, and so are the config's own `import(…)`
//! statements, which name its library's modules, not loaded here.

use crate::core::{class_parts, let_parts};
use crate::interp::Val;
use crate::interp::compile_time::{Session, class_node, field};
use crate::library::{FileId, Level, Library};
use crate::scope::SymbolId;
use crate::settle::{Settled, settle};
use temper_syntax::ast::{Builtin, Value};
use temper_syntax::lex::SourceKind;
use temper_syntax::tree::{NodeId, NodeKind, Span};
use temper_tmpl::{ConfigType, ConfigValue};

/// A config error: where, and what's wrong.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Problem {
    pub span: Span,
    pub message: String,
}

/// The config classes in a build's scope: those its backends give, and
/// the keys of backends that configs may set but this build doesn't have
/// (a build with fewer backends, or one that hasn't loaded them yet),
/// whose exports are dropped, unchecked, so a library's config holds
/// whichever binary builds it.
#[derive(Clone, Debug, Default)]
pub struct Classes {
    pub types: Vec<&'static ConfigType>,
    pub elsewhere: Vec<&'static str>,
}

/// What a config says, settled: its string exports, in order; its values
/// for each config class, by key, as checked; its problems; and where
/// each export's name is.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Said {
    pub exports: Vec<(String, String)>,
    pub configs: Vec<(String, Vec<(String, ConfigValue)>)>,
    pub problems: Vec<Problem>,
    pub keys: Vec<(String, Span)>,
}

/// The prelude's module, beside the configs' (`config0`, `config1`, …).
const PRELUDE: &str = "config-classes";

/// Where a top-level export is in its config's text, for problems with
/// it: its name, and each property's value that an object literal sets,
/// with its items' places if it's a list literal.
struct Place {
    key: Span,
    properties: Vec<(String, Span, Vec<Span>)>,
}

impl Place {
    /// Where a property's value, or its `item`th item, is, as far as
    /// known; else the export's name.
    fn of(&self, property: &str, item: Option<usize>) -> Span {
        let Some((_, value, items)) = self.properties.iter().find(|(p, ..)| p == property) else {
            return self.key;
        };
        item.and_then(|i| items.get(i).copied()).unwrap_or(*value)
    }
}

/// Settles configs' texts, in one library, giving what each says, in
/// order, with spans in its text.
pub fn settle_configs(texts: &[&str], classes: &Classes) -> Vec<Said> {
    let mut lib = Library::new();
    let names: Vec<&str> = classes.types.iter().map(|t| t.class).collect();
    let prelude = (!names.is_empty()).then(|| {
        let source: String = classes.types.iter().map(|t| t.source).collect();
        lib.add_file(
            &format!("{PRELUDE}/classes.temper"),
            &source,
            SourceKind::Standalone,
        )
    });
    let import = format!(
        "let {{ {} }} = import(\"../{PRELUDE}\");\n",
        names.join(", ")
    );
    let mut configs = Vec::new();
    for (i, text) in texts.iter().enumerate() {
        let file = lib.add_file(
            &format!("config{i}/config.temper.md"),
            text,
            SourceKind::Markdown,
        );
        let places = prepare(&mut lib, file, &classes.elsewhere);
        if prelude.is_some() {
            lib.add_file(
                &format!("config{i}/prelude.temper"),
                &import,
                SourceKind::Standalone,
            );
        }
        configs.push((file, places));
    }
    let s = settle(lib);
    let mut said = vec![Said::default(); texts.len()];
    let module_of = |file: FileId| s.library.files[file.0 as usize].module;
    let config_of = |file: FileId| {
        configs
            .iter()
            .position(|&(f, _)| module_of(f) == module_of(file))
    };
    // Where a span is in its config's text, or at the start for what the
    // config imports unseen.
    let in_config = |span: Span| {
        let (file, start) = s.library.locate(span.start);
        let i = config_of(file)?;
        let span = match configs[i].0 == file {
            true => Span {
                start,
                end: start + (span.end - span.start),
            },
            false => Span::default(),
        };
        Some((i, span))
    };
    let errors = || {
        s.library
            .diagnostics
            .iter()
            .filter(|d| d.level == Level::Error)
    };
    for d in errors() {
        let Some((i, span)) = in_config(d.span) else {
            debug_assert!(false, "a config class has an error: {}", d.message.text());
            continue;
        };
        let message = d.message.text();
        said[i].problems.push(Problem { span, message });
    }
    // Code with an error, which says what's wrong, isn't read.
    let has_error = |node: NodeId| {
        let span = s.library.tree.span(node);
        errors().any(|d| span.start <= d.span.start && d.span.end <= span.end)
    };
    let (session, failed) = Session::new_each(&s, crate::interp::FUEL);
    let reader = Reader {
        s: &s,
        classes,
        prelude,
    };
    for (statement, why) in failed {
        if let (false, Some((i, span))) = (
            has_error(statement),
            in_config(s.library.tree.span(statement)),
        ) {
            let message = format!("Running this config failed: {why}");
            said[i].problems.push(Problem { span, message });
        }
    }
    for (i, (file, places)) in configs.iter().enumerate() {
        let Some(m) = s.modules.iter().find(|m| m.id == module_of(*file)) else {
            continue;
        };
        // Its own exports, in order, not the prelude's classes.
        let mut exports: Vec<(u32, SymbolId, NodeId)> = (s.scopes.get(m.scope).names.iter())
            .filter_map(|&(_, symbol)| {
                let sym = s.scopes.symbol(symbol);
                let decl = sym.decl?;
                let start = s.library.tree.span(decl).start;
                (sym.exported && s.library.locate(start).0 == *file)
                    .then_some((start, symbol, decl))
            })
            .collect();
        exports.sort_by_key(|&(start, ..)| start);
        for (_, symbol, decl) in exports {
            let key = s.library.names.get(s.scopes.symbol(symbol).name);
            let name = s.library.tree.span(let_parts(&s.library.tree, decl).0);
            let Some((_, at)) = in_config(name) else {
                continue;
            };
            let unplaced = Place {
                key: at,
                properties: Vec::new(),
            };
            let place = places
                .iter()
                .find(|p| p.key.start == at.start)
                .unwrap_or(&unplaced);
            // A config class's key exported before, as a module declares
            // a name once, so which one counts would be unclear.
            let twice = classes.types.iter().any(|t| t.key == key)
                && said[i].keys.iter().any(|(k, _)| k == key);
            said[i].keys.push((key.to_string(), place.key));
            if !has_error(decl) {
                let value = session.global(&s, symbol);
                reader.read(key, value, place, twice, &mut said[i]);
            }
        }
        said[i].problems.sort_by_key(|p| p.span.start);
    }
    said
}

/// Drops a config's top-level `import(…)` statements, and its exports
/// keyed to a backend `elsewhere`; gives the places of its other exports.
fn prepare(lib: &mut Library, file: FileId, elsewhere: &[&str]) -> Vec<Place> {
    let root = lib.files[file.0 as usize].root;
    let base = lib.files[file.0 as usize].base;
    let named = |lib: &Library, id: NodeId, text: &str| {
        lib.name(id).is_some_and(|n| lib.names.get(n) == text)
    };
    let in_file = |span: Span| Span {
        start: span.start - base,
        end: span.end - base,
    };
    let mut places = Vec::new();
    // A copy, as dropping a statement changes the tree.
    let statements = lib.tree.children(root).to_vec();
    for statement in statements {
        let children = lib.tree.children(statement).to_vec();
        let drop = match children[..] {
            [callee, _] if lib.tree.kind(statement) == NodeKind::Call => {
                named(lib, callee, "import")
            }
            // `@export` applied to a declaration: `(Call @ export (Decl …))`.
            [at, export, decl]
                if lib.tree.kind(statement) == NodeKind::Call
                    && named(lib, at, "@")
                    && named(lib, export, "export")
                    && lib.tree.kind(decl) == NodeKind::Decl =>
            {
                let parts = lib.tree.children(decl).to_vec();
                let key = parts
                    .first()
                    .and_then(|&k| lib.name(k))
                    .map(|k| lib.names.get(k).to_string());
                let Some(key) = key else { continue };
                if elsewhere.contains(&key.as_str()) {
                    true
                } else {
                    // The initializer follows `\init`.
                    let init = (parts[1..].chunks(2))
                        .find(|pair| {
                            lib.symbol(pair[0])
                                .is_some_and(|n| lib.names.get(n) == "init")
                        })
                        .and_then(|pair| pair.get(1).copied());
                    let properties = init.map_or(Vec::new(), |init| literal_places(lib, init));
                    let properties = (properties.into_iter())
                        .map(|(p, v, items)| {
                            (p, in_file(v), items.into_iter().map(in_file).collect())
                        })
                        .collect();
                    places.push(Place {
                        key: in_file(lib.tree.span(parts[0])),
                        properties,
                    });
                    false
                }
            }
            _ => false,
        };
        if drop {
            lib.tree.replace(statement, NodeKind::Block, 0, &[]);
        }
    }
    places
}

/// Each property an object literal sets, `{ a: v, … }`, maybe with
/// `class: C`, with its value's span, and its items' if it's a list
/// literal.
fn literal_places(lib: &Library, node: NodeId) -> Vec<(String, Span, Vec<Span>)> {
    let tree = &lib.tree;
    let children = tree.children(node);
    let is_new = tree.kind(node) == NodeKind::Call
        && children
            .first()
            .and_then(|&c| lib.name(c))
            .is_some_and(|n| lib.names.get(n) == "new");
    if !is_new || children.len() < 2 {
        return Vec::new();
    }
    let mut out = Vec::new();
    let mut args = children[2..].iter();
    while let Some(&arg) = args.next() {
        let Some(property) = lib.symbol(arg) else {
            continue;
        };
        let Some(&value) = args.next() else { break };
        let items = match tree.children(value) {
            [callee, items @ ..]
                if tree.kind(value) == NodeKind::Call
                    && lib.value(*callee) == Some(&Value::Builtin(Builtin::List)) =>
            {
                items.iter().map(|&i| tree.span(i)).collect()
            }
            _ => Vec::new(),
        };
        out.push((lib.names.get(property).to_string(), tree.span(value), items));
    }
    out
}

/// What reads exports' values: the settled configs, the config classes,
/// and the prelude's file, which declares them.
struct Reader<'a> {
    s: &'a Settled,
    classes: &'a Classes,
    prelude: Option<FileId>,
}

impl Reader<'_> {
    /// Reads an export's value into what its config says; one of a class
    /// set `twice` is checked, but not kept.
    fn read(&self, key: &str, value: Option<Val>, place: &Place, twice: bool, said: &mut Said) {
        let Reader {
            s,
            classes,
            prelude,
        } = *self;
        let mut problem =
            |span: Span, message: String| said.problems.push(Problem { span, message });
        let due = classes.types.iter().find(|t| t.key == key);
        let is_for = |t: &ConfigType| {
            let (key, class) = (t.key, t.class);
            format!("`{key}` is for {class}, as `export let {key}: {class} = {{ … }}`")
        };
        // An object of a config class: of the prelude's, not one of the same name.
        let class = value.as_ref().and_then(class_node).filter(|&c| {
            let start = s.library.tree.span(c).start;
            prelude == Some(s.library.locate(start).0)
        });
        let class = class.and_then(|c| {
            let name = s.library.name(class_parts(&s.library.tree, c).0)?;
            let name = s.library.names.get(name);
            classes.types.iter().find(|t| t.class == name)
        });
        match (value.as_ref(), class) {
            // Not run, as its code failed, which says why; or unset.
            (None, _) | (Some(Val::Null), _) => {}
            (Some(object), Some(t)) => {
                if t.key != key {
                    problem(
                        place.key,
                        format!("{} goes in `export let {}`, not `{key}`", t.class, t.key),
                    );
                    return;
                }
                let mut values = Vec::new();
                for p in t.properties {
                    let Some(name) = s.library.names.find(p.name) else {
                        continue;
                    };
                    match field(s, object, name).as_ref() {
                        Some(Val::Str(text)) => {
                            values.push((p.name, ConfigValue::String(text.to_string())))
                        }
                        Some(Val::List(items)) => {
                            let strings = (items.iter())
                                .filter_map(|item| match item {
                                    Val::Str(text) => Some(text.to_string()),
                                    _ => None,
                                })
                                .collect();
                            values.push((p.name, ConfigValue::Strings(strings)));
                        }
                        _ => {}
                    }
                }
                let (kept, found) = t.validate(values);
                for p in found {
                    problem(place.of(p.property, p.item), p.message);
                }
                if twice {
                    problem(
                        place.key,
                        format!("`{key}` is set twice; set each config class once"),
                    );
                    return;
                }
                said.configs.push((key.to_string(), kept));
            }
            (Some(Val::Str(text)), _) => {
                if let Some(message) = string_problem(key, text) {
                    problem(place.key, message);
                }
                if let Some(t) = due {
                    problem(place.key, is_for(t));
                }
                said.exports.push((key.to_string(), text.to_string()));
            }
            (Some(_), None) => {
                if let Some(t) = due {
                    problem(place.key, is_for(t));
                }
            }
        }
    }
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

#[cfg(test)]
mod tests {
    use super::*;
    use temper_tmpl::{ConfigKind, Property};

    /// A config class that no backend has, so these tests show the
    /// frontend settles configs with whatever classes it's given.
    const TOY: ConfigType = ConfigType {
        key: "toy",
        class: "ToyConfig",
        source: "export class ToyConfig(
  public name: String? = null,
  public flavor: String? = null,
  public parts: List<String>? = null,
) {}
",
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
        source: "export class EmptyConfig() {}\n",
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

    fn said(text: &str) -> (Configs, Vec<String>) {
        said_with(text, &both())
    }

    /// The config values, and each problem as the text it's at and its
    /// message.
    fn said_with(text: &str, classes: &Classes) -> (Configs, Vec<String>) {
        let said = settle_configs(&[text], classes).remove(0);
        let problems = (said.problems.iter())
            .map(|p| {
                format!(
                    "{}: {}",
                    &text[p.span.start as usize..p.span.end as usize],
                    p.message
                )
            })
            .collect();
        (said.configs, problems)
    }

    fn string(s: &str) -> ConfigValue {
        ConfigValue::String(s.into())
    }

    fn strings(items: &[&str]) -> ConfigValue {
        ConfigValue::Strings(items.iter().map(|s| s.to_string()).collect())
    }

    #[test]
    fn instances_across_code_blocks() {
        let text = "# Toy\n\n    export let name = \"t\";\n    export let toy = {\n      \
                    class: ToyConfig,\n      name: \"toy-\\\"t\\\"\",\n\nProse.\n\n      \
                    flavor: null,\n    };\n    export let empty = new EmptyConfig();\n";
        let (configs, problems) = said(text);
        assert_eq!(problems, Vec::<String>::new());
        let toy = (
            "toy".to_string(),
            vec![("name".into(), string("toy-\"t\""))],
        );
        assert_eq!(configs, [toy, ("empty".to_string(), vec![])]);
    }

    /// The object takes its class from the declared type, or names it
    /// too, the same; lists hold strings, each trimmed; other exports are
    /// ordinary values.
    #[test]
    fn declared_types_and_lists() {
        let text = "    export let toy: ToyConfig = {
      name: \"t\",
      parts: [\"a@1\", \" b@2 \",],
    };
    export let empty: EmptyConfig = { class: EmptyConfig };
    export let license: String = \"MIT\";
    export let names: List<String> = [\"x\"];
    export let count = 1;
";
        let (configs, problems) = said(text);
        assert_eq!(problems, Vec::<String>::new());
        assert_eq!(
            configs,
            [
                (
                    "toy".to_string(),
                    vec![
                        ("name".into(), string("t")),
                        ("parts".into(), strings(&["a@1", "b@2"]))
                    ]
                ),
                ("empty".to_string(), vec![]),
            ]
        );
    }

    /// Values may be computed, as configs are run; and objects made with
    /// `new`, their properties by position.
    #[test]
    fn computed_values() {
        let text = "    let base = \"geo\";
    let parts(n: Int): List<String> {
      let out = new ListBuilder<String>();
      for (var i = 1; i <= n; i += 1) { out.add(\"p${i}@${i}.0\"); }
      out.toList()
    }
    export let name = \"${base}-lib\";
    export let toy = new ToyConfig(\"${base}-toy\", null, parts(2));
";
        let said = settle_configs(&[text], &both()).remove(0);
        assert_eq!(said.problems, []);
        assert_eq!(said.exports, [("name".to_string(), "geo-lib".to_string())]);
        let toy = vec![
            ("name".to_string(), string("geo-toy")),
            ("parts".to_string(), strings(&["p1@1.0", "p2@2.0"])),
        ];
        assert_eq!(said.configs, [("toy".to_string(), toy)]);
    }

    /// Mistakes are ordinary diagnostics where the type checker finds
    /// them, and config problems where a value is in the wrong place or
    /// form; a mistake in one export leaves the others checked, as each
    /// statement runs on its own.
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
        let (configs, problems) = said(text);
        assert_eq!(
            problems,
            [
                "nmae: ToyConfig has no property `nmae`; did you mean `name`?",
                "x: EmptyConfig has no property `x`; it has none",
                "toys: ToyConfig goes in `export let toy`, not `toys`",
                "ToyConfg: ToyConfg has not been declared",
                "Unknown: Unknown has not been declared",
                "empty: `empty` is for EmptyConfig, as `export let empty: EmptyConfig = { … }`",
                "toy: EmptyConfig goes in `export let empty`, not `toy`",
                "b: b has not been declared",
                "[\"x\"]: Expected subtype of String?, but got List<String>",
                "toy: `toy` is set twice; set each config class once",
                "toy: `toy` is set twice; set each config class once",
                "\"../a\": ToyConfig's `name` is a word, as `toy`, not `../a`",
                "toy: `toy` is set twice; set each config class once",
            ]
        );
        // Only the first of a class's exports counts, which had an error.
        assert_eq!(configs, []);
    }

    /// Declared types that aren't declared, or disagree with `class:`,
    /// or aren't objects; and lists that aren't, or hold what they
    /// shouldn't. An export with an error isn't read, so nothing more is
    /// said of it.
    #[test]
    fn declared_type_and_list_mistakes() {
        let text = "    export let toy: ToyConfg = { name: \"a\" };
    export let toy: ToyConfig = { class: EmptyConfig };
    export let toy: Other.Config = { name: \"a\" };
    export let toys: ToyConfig = { name: \"a\" };
    export let x: ToyConfig = \"a\";
    export let toy: ToyConfig = { parts: \"a@1\" };
    export let toy: ToyConfig = { parts: [\"a@1\", \"b\", \"${c}\"] };
    export let toy: ToyConfig = { name: [\"a\"] };
    export let toy: ToyConfig? = null;
";
        let (_, problems) = said(text);
        assert_eq!(
            problems,
            [
                "ToyConfg: ToyConfg has not been declared",
                "{ class: EmptyConfig }: Cannot assign to ToyConfig from EmptyConfig",
                "Other: Other has not been declared",
                "toys: ToyConfig goes in `export let toy`, not `toys`",
                "\"a\": Cannot assign to ToyConfig from String",
                "\"a@1\": Expected subtype of List<String>?, but got String",
                "c: c has not been declared",
                "[\"a\"]: Expected subtype of String?, but got List<String>",
            ]
        );
    }

    /// A package listed twice, as the class compares packages (here,
    /// ignoring case), is a problem; the list keeps the first.
    #[test]
    fn packages_listed_twice() {
        let text = "    export let toy: ToyConfig = { parts: [\"a@1\", \"b@1\", \"A@2\"] };\n";
        let (configs, problems) = said(text);
        assert_eq!(problems, ["\"A@2\": ToyConfig's `parts` lists `a` twice"]);
        let toy = vec![("parts".to_string(), strings(&["a@1", "b@1"]))];
        assert_eq!(configs, [("toy".to_string(), toy)]);
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
        let (_, problems) = said(text);
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

    /// Code that fails when run is a problem where it is, unless settling
    /// found why; the config's own imports aren't loaded, so they're left
    /// out.
    #[test]
    fn failures_when_run() {
        let text = "    import(\"./elsewhere\");
    export let name = panic();
    export let license = \"MIT\";
    export let toy = { class: ToyConfg };
";
        let said = settle_configs(&[text], &both()).remove(0);
        let problems: Vec<String> = (said.problems.iter())
            .map(|p| {
                format!(
                    "{}: {}",
                    &text[p.span.start as usize..p.span.end as usize],
                    p.message
                )
            })
            .collect();
        assert_eq!(
            problems,
            [
                "export let name = panic(): Running this config failed: Panicked: panic()",
                "ToyConfg: ToyConfg has not been declared",
            ]
        );
        assert_eq!(said.exports, [("license".to_string(), "MIT".to_string())]);
    }

    /// A build without a backend drops what configs set for it, whatever
    /// its class, as another build checks it; other mistakes are still
    /// problems.
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
        let (configs, problems) = said_with(text, &classes);
        assert_eq!(
            problems,
            [
                "EmptyConfig: EmptyConfig has not been declared",
                "ToyConfg: ToyConfg has not been declared",
            ]
        );
        let toy = vec![("name".to_string(), string("t"))];
        assert_eq!(configs, [("toy".to_string(), toy)]);
    }

    /// Configs settle together, each a module of its own, each with the
    /// prelude's classes.
    #[test]
    fn configs_settle_together() {
        let a = "    export let name = \"a\";\n    let shared = \"a@1\";\n";
        let b = "    export let toy = new ToyConfig(\"b\");\n    export let shared = 1;\n";
        let said = settle_configs(&[a, b, ""], &both());
        let problems: Vec<usize> = said.iter().map(|s| s.problems.len()).collect();
        assert_eq!(problems, [0, 0, 0]);
        assert_eq!(said[0].exports, [("name".to_string(), "a".to_string())]);
        assert_eq!(said[0].configs, []);
        let toy = vec![("name".to_string(), string("b"))];
        assert_eq!(said[1].configs, [("toy".to_string(), toy)]);
        assert_eq!(said[2], Said::default());
    }
}
