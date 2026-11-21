//! The config classes the backends inject ([`temper_tmpl::ConfigType`]):
//! docs/backends/README.md's table of them, which is generated from them,
//! so it can't fall behind (on a difference, the test prints the table to
//! paste in); and the frontend's config checking, which never names one.

use temper_driver::Registry;

/// The marks around the generated table.
const START: &str = "<!-- Generated from each backend's config class by \
                     crates/temper-driver/tests/config_classes.rs. -->\n";
const END: &str = "<!-- End of generated table. -->\n";

/// The table: a row per property, under its class, with the backend's
/// key, and its type; a class without properties says so.
fn table() -> String {
    let mut out =
        String::from("| Class | Export | Property | Type | Sets |\n|---|---|---|---|---|\n");
    for t in Registry::built_in().config_types() {
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
    for t in Registry::built_in().config_types() {
        for name in [t.key, t.class] {
            assert!(!words.contains(&name), "config.rs names {name}");
        }
    }
}

/// What each backend says std's config sets is one of its properties,
/// spelled as that property allows.
#[test]
fn std_configs_set_properties() {
    for t in Registry::built_in().config_types() {
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
