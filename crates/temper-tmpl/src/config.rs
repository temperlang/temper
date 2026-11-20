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
    /// A `String?`.
    String,
    /// A `List<String>?`, each item in a form that `valid` checks, and
    /// that `form` shows, as `"name==version"`.
    Strings {
        form: &'static str,
        valid: fn(&str) -> bool,
    },
}

impl Kind {
    /// Its type, as Temper spells it.
    pub fn type_name(&self) -> &'static str {
        match self {
            Kind::String => "String",
            Kind::Strings { .. } => "List<String>",
        }
    }
}

/// A dependency on a package, as a config spells it (`"name==version"`),
/// split at each `separator` into `count` parts, each trimmed and
/// neither empty nor holding spaces; `None` if it isn't so.
pub fn dependency_parts<'a>(text: &'a str, separator: &str, count: usize) -> Option<Vec<&'a str>> {
    let parts: Vec<&str> = text.trim().split(separator).collect();
    let fine = |p: &&str| !p.is_empty() && !p.contains(char::is_whitespace);
    (parts.len() == count && parts.iter().all(fine)).then_some(parts)
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
                kind: Kind::String,
            },
            Property {
                name: "parts",
                doc: "",
                kind: Kind::Strings {
                    form: "any",
                    valid: |_| true,
                },
            },
        ],
        std: &[("name", "toy-std")],
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
}
