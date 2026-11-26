//! Backends' config classes: what a library's `config.temper.md` may set
//! for one backend, as
//!
//! ```temper
//! export let java: JavaConfig = { package: "com.example.geometry" };
//! ```
//!
//! as Kotlin Temper's backends inject theirs into config modules. Each
//! backend declares its own [`ConfigType`] beside its [`crate::Backend`]:
//! its class as Temper source, which configs are settled with as normal
//! Temper code, and the forms its values take, which it checks
//! ([`ConfigType::validate`]). The frontend does this without knowing any
//! backend, and the build gives each library's checked values to backends
//! in its [`Metadata::configs`].

use crate::Metadata;

/// A backend's config class.
#[derive(Debug)]
pub struct ConfigType {
    /// The export that holds it: the backend's name, as `java`.
    pub key: &'static str,
    /// Its class's name, as `JavaConfig`.
    pub class: &'static str,
    /// Its class's declaration, as Temper source: `export class
    /// JavaConfig(public name: String? = null, …) {}`, a constructor
    /// property for each of [`Self::properties`], in order, nullable and
    /// `null` by default.
    pub source: &'static str,
    /// Its properties, in order, each unset by default.
    pub properties: &'static [Property],
    /// What std's config sets, as each of Kotlin's backends injects std's
    /// (be-java's `temper-std/config.temper.md`), by property.
    pub std: &'static [(&'static str, &'static str)],
    /// The names a library takes among its language's packages, given
    /// its name and what its config says (std's too, by [`Self::get`]),
    /// each folded as the language's tools compare them (case and the
    /// like), so names that would collide give the same text: Python's
    /// project, npm's package, the crate, the Java package and Maven
    /// coordinates, the C# namespace. Two libraries of a build sharing
    /// one, or a `dependencies` item naming one, is a config error.
    pub names: fn(library: &str, metadata: &Metadata) -> Vec<String>,
    /// The names its runtime, `temper-core`, takes, as `names` gives them.
    pub runtime: &'static [&'static str],
}

/// [`ConfigType::names`] for a backend whose libraries take no names
/// that might clash.
pub fn no_names(_: &str, _: &Metadata) -> Vec<String> {
    Vec::new()
}

/// A property of a [`ConfigType`]: its name, what it sets, a sentence
/// for docs (docs/backends/README.md, "Configuration"), and what it holds.
#[derive(Debug)]
pub struct Property {
    pub name: &'static str,
    pub doc: &'static str,
    pub kind: Kind,
}

/// What a [`Property`] holds.
#[derive(Clone, Copy, Debug)]
pub enum Kind {
    /// A `String?`, a name in a form that `valid` checks and that `form`
    /// says, as "a dotted C# name, as `Example.Geometry`": names reach
    /// paths and package files, so each backend allows only what its
    /// language's tools allow, and nothing that leaves a directory or
    /// breaks out of a file's quoting.
    String {
        form: &'static str,
        valid: fn(&str) -> bool,
    },
    /// A `List<String>?` of packages, each item in a form that `form`
    /// shows, as `"name==version"`, and that `package` reads, giving
    /// the package it names as [`ConfigType::names`] gives names, or
    /// `None` for an item not in that form. A package listed twice is a
    /// config error.
    Strings {
        form: &'static str,
        package: fn(&str) -> Option<String>,
    },
}

impl Kind {
    /// Its type, as Temper spells it.
    pub fn type_name(&self) -> &'static str {
        match self {
            Kind::String { .. } => "String",
            Kind::Strings { .. } => "List<String>",
        }
    }
}

/// A dependency on a package, as a config spells it (`"name==version"`),
/// split at each `separator` into `count` parts, for the backend to
/// check each with [`spelled`]; `None` without `count` parts.
pub fn dependency_parts<'a>(text: &'a str, separator: &str, count: usize) -> Option<Vec<&'a str>> {
    let parts: Vec<&str> = text.split(separator).collect();
    (parts.len() == count).then_some(parts)
}

/// Whether a package's name or version, or the like, is spelled only
/// with ASCII letters, digits, and the characters in `extra`, starting
/// with a letter or digit, and without `..`: an allow-list, so nothing
/// that a tool reads as an option (a leading `-`), a variable (`$`), a
/// separator (`;`, `:`, `/`), a URL or path, quoting, or markup (`<`,
/// `&`) gets through, nor whitespace.
pub fn spelled(text: &str, extra: &str) -> bool {
    text.starts_with(|c: char| c.is_ascii_alphanumeric())
        && text
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || extra.contains(c))
        && !text.contains("..")
}

/// Whether a name is ASCII identifiers joined by `.`, as Java packages
/// and C# namespaces are, `com.example.geo`.
pub fn dotted_identifier(text: &str) -> bool {
    text.split('.').all(|part| {
        part.starts_with(|c: char| c.is_ascii_alphabetic() || c == '_')
            && part.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
    })
}

/// A property's value in a library's config, as checked.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Value {
    String(String),
    Strings(Vec<String>),
}

/// What's wrong with a value a config sets ([`ConfigType::validate`]):
/// where, as its property and, in a list, its item's index; and what.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Problem {
    pub property: &'static str,
    pub item: Option<usize>,
    pub message: String,
}

impl ConfigType {
    /// Checks the values a library's config sets for this class, by
    /// property, against the forms its properties take, giving those
    /// kept and the problems. A blank string is kept, as unset; a list's
    /// items are trimmed, and an item not in its form, or naming a
    /// package listed before it, is left out.
    pub fn validate(
        &self,
        values: Vec<(&'static str, Value)>,
    ) -> (Vec<(String, Value)>, Vec<Problem>) {
        let mut kept = Vec::new();
        let mut problems = Vec::new();
        for (property, value) in values {
            let Some(p) = self.properties.iter().find(|p| p.name == property) else {
                continue;
            };
            let whose = format!("{}'s `{property}`", self.class);
            let mut problem = |item, message| {
                problems.push(Problem {
                    property: p.name,
                    item,
                    message,
                })
            };
            match (p.kind, value) {
                (Kind::String { valid, form }, Value::String(s)) => {
                    if s.trim().is_empty() || valid(&s) {
                        kept.push((property.to_string(), Value::String(s)));
                    } else {
                        problem(None, format!("{whose} is {form}, not `{s}`"));
                    }
                }
                (Kind::Strings { form, package }, Value::Strings(items)) => {
                    let mut strings = Vec::new();
                    let mut packages = Vec::new();
                    for (i, item) in items.iter().enumerate() {
                        let item = item.trim();
                        match package(item) {
                            Some(p) if packages.contains(&p) => {
                                problem(Some(i), format!("{whose} lists `{p}` twice"))
                            }
                            Some(p) => {
                                packages.push(p);
                                strings.push(item.to_string());
                            }
                            None => {
                                problem(Some(i), format!("{whose} are each `{form}`, not `{item}`"))
                            }
                        }
                    }
                    kept.push((property.to_string(), Value::Strings(strings)));
                }
                // The type checker reports a value of another type.
                _ => {}
            }
        }
        (kept, problems)
    }

    /// What a library's config sets for a property, if it sets a value
    /// that isn't blank; for std, what std's config sets.
    pub fn get<'a>(
        &self,
        library: &str,
        metadata: &'a Metadata,
        property: &str,
    ) -> Option<&'a str> {
        debug_assert!(
            self.properties.iter().any(|p| p.name == property),
            "{} has no property {property}",
            self.class
        );
        let set = (metadata.configs.iter())
            .filter(|(key, _)| key == self.key)
            .flat_map(|(_, values)| values)
            .find_map(|(name, value)| match value {
                Value::String(s) if name == property && !s.trim().is_empty() => Some(s.as_str()),
                _ => None,
            });
        let std = || {
            (library == "std")
                .then(|| self.std.iter().find(|(name, _)| *name == property))
                .flatten()
                .map(|(_, value)| *value)
        };
        set.or_else(std)
    }

    /// What a library's config sets for a list property, or none.
    pub fn strings<'a>(&self, metadata: &'a Metadata, property: &str) -> &'a [String] {
        debug_assert!(
            (self.properties.iter())
                .any(|p| p.name == property && matches!(p.kind, Kind::Strings { .. })),
            "{} has no list property {property}",
            self.class
        );
        (metadata.configs.iter())
            .filter(|(key, _)| key == self.key)
            .flat_map(|(_, values)| values)
            .find_map(|(name, value)| match value {
                Value::Strings(items) if name == property => Some(items.as_slice()),
                _ => None,
            })
            .unwrap_or(&[])
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const TOY: ConfigType = ConfigType {
        key: "toy",
        class: "ToyConfig",
        source: "",
        properties: &[
            Property {
                name: "name",
                doc: "",
                kind: Kind::String {
                    form: "any",
                    valid: |_| true,
                },
            },
            Property {
                name: "parts",
                doc: "",
                kind: Kind::Strings {
                    form: "any",
                    package: |s| Some(s.into()),
                },
            },
        ],
        std: &[("name", "toy-std")],
        names: no_names,
        runtime: &[],
    };

    #[test]
    fn values_are_the_librarys_or_else_std_s() {
        let metadata = Metadata {
            configs: vec![(
                "toy".into(),
                vec![
                    ("name".into(), Value::String("mine".into())),
                    ("parts".into(), Value::Strings(vec!["a".into()])),
                ],
            )],
            ..Metadata::default()
        };
        let blank = Metadata {
            configs: vec![(
                "toy".into(),
                vec![("name".into(), Value::String(" ".into()))],
            )],
            ..Metadata::default()
        };
        assert_eq!(TOY.get("app", &metadata, "name"), Some("mine"));
        assert_eq!(TOY.get("std", &metadata, "name"), Some("mine"));
        assert_eq!(TOY.get("app", &blank, "name"), None);
        assert_eq!(TOY.get("std", &blank, "name"), Some("toy-std"));
        assert_eq!(TOY.get("app", &Metadata::default(), "name"), None);
        assert_eq!(TOY.strings(&metadata, "parts"), ["a"]);
        assert_eq!(TOY.strings(&blank, "parts"), [] as [String; 0]);
    }

    /// A backend checks its own values' forms, saying where each problem
    /// is, by property and item; what's wrong is left out, and a blank
    /// name is kept, as unset.
    #[test]
    fn validate_checks_forms() {
        const PARTS: ConfigType = ConfigType {
            properties: &[
                Property {
                    name: "name",
                    doc: "",
                    kind: Kind::String {
                        form: "a word",
                        valid: |s| s.chars().all(|c| c.is_ascii_alphanumeric()),
                    },
                },
                Property {
                    name: "parts",
                    doc: "",
                    kind: Kind::Strings {
                        form: "name@version",
                        package: |s| s.split_once('@').map(|(n, _)| n.to_lowercase()),
                    },
                },
            ],
            ..TOY
        };
        let strings =
            |items: &[&str]| Value::Strings(items.iter().map(|s| s.to_string()).collect());
        let (kept, problems) = PARTS.validate(vec![
            ("name", Value::String("../x".into())),
            ("parts", strings(&[" a@1 ", "b", "A@2", "c@3"])),
        ]);
        assert_eq!(kept, [("parts".to_string(), strings(&["a@1", "c@3"]))]);
        let problem = |property, item, message: &str| Problem {
            property,
            item,
            message: message.into(),
        };
        assert_eq!(
            problems,
            [
                problem("name", None, "ToyConfig's `name` is a word, not `../x`"),
                problem(
                    "parts",
                    Some(1),
                    "ToyConfig's `parts` are each `name@version`, not `b`"
                ),
                problem("parts", Some(2), "ToyConfig's `parts` lists `a` twice"),
            ]
        );
        let (kept, problems) = PARTS.validate(vec![("name", Value::String(" ".into()))]);
        assert_eq!(kept, [("name".to_string(), Value::String(" ".into()))]);
        assert_eq!(problems, []);
    }

    /// What [`spelled`] and [`dotted_identifier`] allow, and hostile
    /// spellings they don't.
    #[test]
    fn spellings() {
        for good in ["a", "A1", "roaring-wasm", "1.4.1", "Roaring.Net", "a_b"] {
            assert!(spelled(good, "._-"), "{good}");
        }
        let hostile = [
            "",
            "-e",
            "--index-url=x",
            ".hidden",
            "_x",
            "a..b",
            "a/b",
            "a\\b",
            "${X}",
            "$(X)",
            "a;b",
            "a:b",
            "a b",
            "a\nb",
            "a\"b",
            "a'b",
            "a<b",
            "a>b",
            "a&b",
            "a%b",
            "a@b",
            "a#b",
            "é",
            "a,b",
            "a=b",
        ];
        for bad in hostile {
            assert!(!spelled(bad, "._-"), "{bad}");
        }
        for good in ["a", "com.example.geo", "TemperLang.Std", "_x.y2"] {
            assert!(dotted_identifier(good), "{good}");
        }
        for bad in [
            "", "a.", ".a", "a..b", "1a", "a-b", "../x", "a/b", "a<b", "a$b", "é",
        ] {
            assert!(!dotted_identifier(bad), "{bad}");
        }
    }
}
