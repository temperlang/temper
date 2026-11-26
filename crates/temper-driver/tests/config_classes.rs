//! The config classes the backends give ([`temper_tmpl::ConfigType`]):
//! docs/backends/README.md's table of them, which is generated from them,
//! so it can't fall behind (on a difference, the test prints the table to
//! paste in); their Temper declarations and their own checks of values;
//! and the frontend's settling of configs, which never names one.

use temper_driver::Registry;
use temper_frontend::project::{Config, settle_configs};

/// The marks around the generated table.
#[allow(dead_code)] // Unless every backend is left out.
const START: &str = "<!-- Generated from each backend's config class by \
                     crates/temper-driver/tests/config_classes.rs. -->\n";
#[allow(dead_code)] // Unless every backend is left out.
const END: &str = "<!-- End of generated table. -->\n";

/// The table: a row per property, under its class, with the backend's
/// key, and its type; a class without properties says so.
#[allow(dead_code)] // Unless every backend is left out.
fn table() -> String {
    let mut out =
        String::from("| Class | Export | Property | Type | Sets |\n|---|---|---|---|---|\n");
    for t in Registry::built_in().config_classes().types {
        let class = format!("`{}`", t.class);
        let key = format!("`{}`", t.key);
        if t.properties.is_empty() {
            out.push_str(&format!("| {class} | {key} | | | Nothing yet |\n"));
        }
        for (i, p) in t.properties.iter().enumerate() {
            let (class, key) = match i {
                0 => (class.as_str(), key.as_str()),
                _ => ("", ""),
            };
            let ty = p.kind.type_name();
            out.push_str(&format!(
                "| {class} | {key} | `{}` | `{ty}` | {} |\n",
                p.name, p.doc
            ));
        }
    }
    out
}

#[test]
#[cfg(all(
    feature = "cpp",
    feature = "csharp",
    feature = "java",
    feature = "js",
    feature = "lua",
    feature = "py",
    feature = "rust"
))]
fn backends_readme_lists_each_config_class() {
    let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../docs/backends/README.md");
    let text = std::fs::read_to_string(path).unwrap().replace("\r\n", "\n");
    let start = text.find(START).expect("the table's start mark") + START.len();
    let end = start + text[start..].find(END).expect("the table's end mark");
    let expected = table();
    assert!(
        text[start..end] == expected,
        "docs/backends/README.md's config table differs; it should be:\n\n{expected}"
    );
}

/// The frontend checks configs against whatever classes it's given:
/// its config module names no backend, by key or class.
#[test]
fn frontend_names_no_backend() {
    let path = concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../temper-frontend/src/config.rs"
    );
    let text = std::fs::read_to_string(path).unwrap();
    let words: Vec<&str> = text
        .split(|c: char| !c.is_ascii_alphanumeric() && c != '_')
        .collect();
    for t in Registry::built_in().config_classes().types {
        for name in [t.key, t.class] {
            assert!(!words.contains(&name), "config.rs names {name}");
        }
    }
}

/// What each backend says std's config sets is one of its properties,
/// spelled as that property allows.
#[test]
fn std_configs_set_properties() {
    for t in Registry::built_in().config_classes().types {
        for (property, value) in t.std {
            let p = t.properties.iter().find(|p| p.name == *property);
            let Some(p) = p else {
                panic!("{} has no property {property}", t.class);
            };
            if let temper_tmpl::ConfigKind::String { valid, .. } = p.kind {
                assert!(valid(value), "{}'s {property}: {value}", t.class);
            }
        }
    }
}

/// Each problem in each config, by library: the text it's at, and its
/// message.
#[allow(dead_code)] // Unless every backend is left out.
fn problems(configs: &[Config]) -> Vec<(String, String, String)> {
    let mut found = Vec::new();
    for config in configs {
        for p in &config.problems {
            let at = &config.text[p.span.start as usize..p.span.end as usize];
            found.push((config.name.clone(), at.to_string(), p.message.clone()));
        }
    }
    found
}

/// The configs of a build's libraries, each named `NAME` and saying
/// `TEXT`, settled with `registry`'s classes.
#[allow(dead_code)] // Unless every backend is left out.
fn checked(registry: &Registry, libraries: &[(&str, &str)]) -> Vec<Config> {
    let mut configs: Vec<Config> = (libraries.iter())
        .map(|(name, text)| {
            let text = format!("    export let name = \"{name}\";\n{text}");
            Config::new(&text, name)
        })
        .collect();
    settle_configs(configs.iter_mut(), &registry.config_classes());
    configs
}

/// A build without a backend drops what configs set for it, as a build
/// with it checks it, but still finds mistakes in the rest.
#[test]
#[cfg(all(feature = "csharp", feature = "py"))]
fn configs_for_backends_a_build_lacks() {
    let others = (Registry::built_in().all().iter().copied())
        .filter(|b| b.name != "csharp")
        .collect();
    let registry = Registry::new(others);
    assert_eq!(registry.config_classes().elsewhere, ["csharp"]);
    let text = "    export let csharp: CSharpConfig = { rootNamespace: \"A.B\" };
    export let py: PyConfg = { name: \"a\" };
    export let pie = { class: JsConfg };
";
    let found = problems(&checked(&registry, &[("a", text)]));
    let expected: Vec<(String, String, String)> = [
        ("PyConfg", "PyConfg has not been declared"),
        ("JsConfg", "JsConfg has not been declared"),
    ]
    .iter()
    .map(|(at, m)| ("a".to_string(), at.to_string(), m.to_string()))
    .collect();
    assert_eq!(found, expected);
    let found = problems(&checked(&Registry::built_in(), &[("a", text)]));
    assert_eq!(found.len(), 2, "{found:?}");
}

/// Libraries whose packages would collide where they meet (names that
/// differ only in case among them), or a dependency on a package that a
/// library of the build, std, or the runtime is, are problems in each
/// config concerned, at its export for that backend.
#[test]
#[cfg(all(
    feature = "csharp",
    feature = "java",
    feature = "js",
    feature = "py",
    feature = "rust"
))]
fn names_shared_across_libraries() {
    let libraries = [
        ("a", "    export let py: PyConfig = { name: \"Shared_Name\" };\n"),
        ("b", "    export let py: PyConfig = { name: \"shared.name\" };\n"),
        (
            "c",
            "    export let js: JsConfig = { dependencies: [\"@temperlang/std@1\", \"left-pad@1\"] };
    export let rust: RustConfig = { dependencies: [\"temper_core@1.0.0\", \"D@1.0.0\"] };
",
        ),
        ("d", "    export let java: JavaConfig = { package: \"Temper.Std\" };\n"),
        (
            "e",
            "    export let csharp: CSharpConfig = { rootNamespace: \"Shared.Ns\" };\n",
        ),
        (
            "f",
            "    export let csharp: CSharpConfig = { rootNamespace: \"shared.ns\" };
    export let java: JavaConfig = { dependencies: [\"dev.temperlang:temper-core:1\"] };
",
        ),
    ];
    let found = problems(&checked(&Registry::built_in(), &libraries));
    let expected = [
        (
            "a",
            "py",
            "PyConfig names this library `shared-name`, as it names library `b`",
        ),
        (
            "b",
            "py",
            "PyConfig names this library `shared-name`, as it names library `a`",
        ),
        (
            "c",
            "js",
            "JsConfig's `dependencies` lists `@temperlang/std`, which names std",
        ),
        (
            "c",
            "rust",
            "RustConfig's `dependencies` lists `temper_core`, which names the runtime, \
             `temper-core`",
        ),
        (
            "c",
            "rust",
            "RustConfig's `dependencies` lists `d`, which names library `d`",
        ),
        (
            "d",
            "java",
            "JavaConfig names this library `package temper.std`, as it names std",
        ),
        (
            "e",
            "csharp",
            "CSharpConfig names this library `shared.ns`, as it names library `f`",
        ),
        (
            "f",
            "csharp",
            "CSharpConfig names this library `shared.ns`, as it names library `e`",
        ),
        (
            "f",
            "java",
            "JavaConfig's `dependencies` lists `dev.temperlang:temper-core`, which names the \
             runtime, `temper-core`",
        ),
    ];
    let expected: Vec<(String, String, String)> = (expected.iter())
        .map(|(l, at, m)| (l.to_string(), at.to_string(), m.to_string()))
        .collect();
    assert_eq!(found, expected);
}

/// Each backend's config class goes in the export of its name, as
/// [`Registry::config_classes`] takes a lacking backend's key to be.
#[test]
fn config_keys_are_backend_names() {
    for b in Registry::built_in().all() {
        assert_eq!(b.config.key, b.name);
    }
}

/// Each backend declares its config class in Temper as its properties
/// say, in order, each nullable and null by default; and a config may
/// set each, as settling shows.
#[test]
fn class_sources_declare_properties() {
    let registry = Registry::built_in();
    for t in registry.config_classes().types {
        let mut rest = t.source;
        let header = format!("export class {}(", t.class);
        rest = &rest[rest.find(&header).unwrap_or_else(|| panic!("{}", t.class))..];
        for p in t.properties {
            let declared = format!("public {}: {}? = null,", p.name, p.kind.type_name());
            let at = rest.find(&declared);
            rest = &rest[at.unwrap_or_else(|| panic!("{}: {declared}", t.class))..];
        }
        let sets: Vec<String> = (t.properties.iter())
            .map(|p| match p.kind {
                temper_tmpl::ConfigKind::String { .. } => format!("{}: null", p.name),
                temper_tmpl::ConfigKind::Strings { .. } => format!("{}: []", p.name),
            })
            .collect();
        let text = format!(
            "    export let {}: {} = {{ {} }};\n",
            t.key,
            t.class,
            sets.join(", ")
        );
        let configs = checked(&registry, &[("a", &text)]);
        assert_eq!(problems(&configs), [], "{text}");
        assert_eq!(configs[0].configs.len(), 1, "{text}");
    }
}

/// Each backend checks its own values' forms: no hostile name or
/// dependency gets through, and each problem says where it is.
#[test]
fn backends_check_their_own_values() {
    for t in Registry::built_in().config_classes().types {
        for p in t.properties {
            let (value, item) = match p.kind {
                temper_tmpl::ConfigKind::String { .. } => {
                    (temper_tmpl::ConfigValue::String("../x".into()), None)
                }
                temper_tmpl::ConfigKind::Strings { .. } => {
                    let items = vec!["--index-url=x".to_string()];
                    (temper_tmpl::ConfigValue::Strings(items), Some(0))
                }
            };
            let (kept, found) = t.validate(vec![(p.name, value)]);
            assert_eq!(found.len(), 1, "{}'s {}", t.class, p.name);
            assert_eq!((found[0].property, found[0].item), (p.name, item));
            let leaked = kept.iter().any(|(_, v)| match v {
                temper_tmpl::ConfigValue::String(_) => true,
                temper_tmpl::ConfigValue::Strings(items) => !items.is_empty(),
            });
            assert!(!leaked, "{}'s {}: {kept:?}", t.class, p.name);
        }
    }
}
