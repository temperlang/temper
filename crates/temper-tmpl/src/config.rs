//! Backends' config classes: what a library's `config.temper.md` may set
//! for one backend, as
//!
//! ```temper
//! export let java = { class: JavaConfig, package: "com.example.geometry" };
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
    /// Its properties, in order, each a `String?` that's unset by default.
    pub properties: &'static [Property],
    /// What std's config sets, as each of Kotlin's backends injects std's
    /// (be-java's `temper-std/config.temper.md`), by property.
    pub std: &'static [(&'static str, &'static str)],
}

/// A property of a [`ConfigType`]: its name, and what it sets, a
/// sentence for docs (docs/backends/README.md, "Configuration").
#[derive(Debug)]
pub struct Property {
    pub name: &'static str,
    pub doc: &'static str,
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
            .find(|(name, value)| name == property && !value.trim().is_empty())
            .map(|(_, value)| value.as_str());
        let std = || {
            (library == "std")
                .then(|| self.std.iter().find(|(name, _)| *name == property))
                .flatten()
                .map(|(_, value)| *value)
        };
        set.or_else(std)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const TOY: ConfigType = ConfigType {
        key: "toy",
        class: "ToyConfig",
        properties: &[Property {
            name: "name",
            doc: "",
        }],
        std: &[("name", "toy-std")],
    };

    #[test]
    fn values_are_the_librarys_or_else_std_s() {
        let metadata = Metadata {
            configs: vec![("toy".into(), vec![("name".into(), "mine".into())])],
            ..Metadata::default()
        };
        let blank = Metadata {
            configs: vec![("toy".into(), vec![("name".into(), " ".into())])],
            ..Metadata::default()
        };
        assert_eq!(TOY.get("app", &metadata, "name"), Some("mine"));
        assert_eq!(TOY.get("std", &metadata, "name"), Some("mine"));
        assert_eq!(TOY.get("app", &blank, "name"), None);
        assert_eq!(TOY.get("std", &blank, "name"), Some("toy-std"));
        assert_eq!(TOY.get("app", &Metadata::default(), "name"), None);
    }
}
