//! Backends' config classes: what a library's `config.temper.md` may set
//! for one backend, as
//!
//! ```temper
//! export let java: JavaConfig = { package: "com.example.geometry" };
//! ```
//!
//! as Kotlin Temper's backends inject theirs into config modules. Each
//! backend declares its own [`ConfigType`] beside its [`crate::Backend`];
//! the frontend checks configs against the types a build has, without
//! knowing any backend, and the build gives each library's checked
//! values to backends in its [`Metadata::configs`].

use crate::Metadata;

/// A backend's config class.
#[derive(Debug)]
pub struct ConfigType {
    /// The export that holds it: the backend's name, as `java`.
    pub key: &'static str,
    /// Its class's name, as `JavaConfig`.
    pub class: &'static str,
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

impl ConfigType {
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
