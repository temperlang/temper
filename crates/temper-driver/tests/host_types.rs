//! Each core type a backend translates as a host type or its runtime's
//! own is `@connected` in core, as core says where it reads its
//! declarations (`temper_frontend::typing::core_shape`), so `is` and `as`
//! can't target one that several Temper types may share the host type
//! of (notes/core-capabilities.md). Those types are what each backend's
//! sources name, as `"core.type Deque"`, found by scanning them, so no
//! list is kept by hand and a type a backend starts translating can't be
//! missed; and tmpl's value scalars, as `Int32`, which every backend
//! translates as its host's. Backends are those the registry
//! has; the frontend lists none.

use std::path::{Path, PathBuf};
use temper_driver::Registry;
use temper_frontend::typing::core_shape;
use temper_tmpl::Scalar;

/// Core types a backend's sources name that aren't host types: `MapKey`,
/// only ever a bound, never a value's type.
const NOT_HOST_TYPES: [&str; 1] = ["MapKey"];

/// tmpl's scalars that no backend translates as a value's host type:
/// `Void`, which has no values, `AnyValue`, every value's type, and
/// `Type`, types as values, none of which `is` and `as` confuse.
const NOT_HOST_SCALARS: [&str; 3] = ["Void", "AnyValue", "Type"];

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
fn host_types_are_connected_in_core() {
    let crates = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("..");
    let scalars = (Scalar::ALL.iter())
        .map(|s| s.name())
        .filter(|name| !NOT_HOST_SCALARS.contains(name));
    let scalars: Vec<&str> = scalars.collect();
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
        let named = named.iter().map(String::as_str);
        let host_types = named.filter(|n| !NOT_HOST_TYPES.contains(n));
        for name in host_types.chain(scalars.iter().copied()) {
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
    assert!(checked >= 7, "{checked} backends' sources");
    assert!(!scalars.is_empty());
    assert_eq!(wrong, Vec::<String>::new());
}
