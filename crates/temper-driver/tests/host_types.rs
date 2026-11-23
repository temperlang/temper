//! Each core type a backend translates as a host type or its runtime's
//! own (`Backend::host_types`) is `@connected` in core, as core says
//! where it reads its declarations (`temper_frontend::typing::core_shape`),
//! so `is` and `as` can't target one that several Temper types may share
//! the host type of (notes/core-capabilities.md). Backends are those the
//! registry has; the frontend lists none. And the other way: each core
//! type a backend's translation names, as `"core.type Deque"`, is one of
//! its `host_types`, so a type it starts translating can't be missed.

use std::path::{Path, PathBuf};
use temper_driver::Registry;
use temper_frontend::typing::core_shape;
use temper_tmpl::TypeTable;

#[test]
fn host_types_are_connected_in_core() {
    let mut wrong = Vec::new();
    for backend in Registry::built_in().all() {
        for &name in backend.host_types {
            match core_shape(name) {
                Some(shape) if shape.connected => {}
                Some(_) => wrong.push(format!("{name} ({}): not @connected", backend.name)),
                None => wrong.push(format!(
                    "{name} ({}): not a type core exports",
                    backend.name
                )),
            }
        }
    }
    assert_eq!(wrong, Vec::<String>::new());
}

/// Core types a backend's sources name that aren't host types: `MapKey`,
/// only ever a bound, never a value's type.
const NOT_HOST_TYPES: [&str; 1] = ["MapKey"];

/// The core types a crate's sources name as `"core.type X"`, each once.
fn named_core_types(dir: &Path, found: &mut Vec<String>) {
    for entry in std::fs::read_dir(dir).unwrap() {
        let path = entry.unwrap().path();
        if path.is_dir() {
            named_core_types(&path, found);
            continue;
        }
        if path.extension().is_none_or(|e| e != "rs") {
            continue;
        }
        let text = std::fs::read_to_string(&path).unwrap();
        for piece in text.split("\"core.type ").skip(1) {
            let Some((name, _)) = piece.split_once('"') else {
                continue;
            };
            let bare = !name.is_empty() && name.chars().all(|c| c.is_ascii_alphanumeric());
            if bare && !found.iter().any(|f| f == name) {
                found.push(name.to_string());
            }
        }
    }
}

#[test]
fn translated_core_types_are_host_types() {
    let crates = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("..");
    let mut wrong = Vec::new();
    let mut checked = 0;
    for backend in Registry::built_in().all() {
        let src = crates.join(format!("temper-be-{}/src", backend.name));
        if !src.is_dir() {
            continue;
        }
        checked += 1;
        let mut named = Vec::new();
        named_core_types(&src, &mut named);
        for name in &named {
            if !backend.host_types.contains(&name.as_str())
                && !NOT_HOST_TYPES.contains(&name.as_str())
            {
                wrong.push(format!(
                    "{name} ({}): translated, not in host_types",
                    backend.name
                ));
            }
        }
        // And no stale entries: each is translated, or a scalar, which
        // tmpl's own types cover.
        for &name in backend.host_types {
            if !named.iter().any(|n| n == name) && TypeTable::scalar_named(name).is_none() {
                wrong.push(format!(
                    "{name} ({}): in host_types, never translated",
                    backend.name
                ));
            }
        }
    }
    assert!(checked >= 7, "{checked} backends' sources");
    assert_eq!(wrong, Vec::<String>::new());
}
