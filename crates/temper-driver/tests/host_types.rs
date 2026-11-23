//! Each core type a backend translates as a host type or its runtime's
//! own is `@connected` in core, as core says where it reads its
//! declarations (`temper_frontend::typing::core_shape`), so `is` and `as`
//! can't target one that several Temper types may share the host type
//! of (notes/core-capabilities.md). Those types are what each backend's
//! sources name, as `"core.type StringBuilder"`, found by scanning them,
//! so no list is kept by hand and a type a backend starts translating
//! can't be missed; and tmpl's value scalars, as `Int32`, which every
//! backend translates as its host's. The same holds for std's types that
//! backends name, as `"std/collections.type Queue"`: each is a class or
//! interface std declares `@connected`. Backends are those the registry
//! has; the frontend lists none.

use std::path::{Path, PathBuf};
use temper_driver::Registry;
use temper_frontend::typing::core_shape;
use temper_tmpl::qname::std_key;
use temper_tmpl::{DeclKind, Facts, Scalar};

/// Core types a backend's sources name that aren't host types: `MapKey`,
/// only ever a bound, never a value's type.
const NOT_HOST_TYPES: [&str; 1] = ["MapKey"];

/// std's types a backend's sources name that aren't `@connected`:
/// `Test`, which Lua's runtime makes a table of its own, as Kotlin's
/// does, though std's `Test` isn't `@connected` there either.
const NOT_CONNECTED_STD: [&str; 1] = ["std/testing.type Test"];

/// tmpl's scalars that no backend translates as a value's host type:
/// `Void`, which has no values, `AnyValue`, every value's type, and
/// `Type`, types as values, none of which `is` and `as` confuse.
const NOT_HOST_SCALARS: [&str; 3] = ["Void", "AnyValue", "Type"];

/// The types a crate's sources name as `"PREFIXX"`, as `"core.type X"`
/// or `"std/m.type X"`, each once, with what comes before the type's
/// name: `X`, or `std/m.type X`.
fn named_types(dir: &Path, found: &mut Vec<String>) {
    for entry in std::fs::read_dir(dir).unwrap() {
        let path = entry.unwrap().path();
        if path.is_dir() {
            named_types(&path, found);
            continue;
        }
        if path.extension().is_none_or(|e| e != "rs") {
            continue;
        }
        let text = std::fs::read_to_string(&path).unwrap();
        for piece in text.split('"').skip(1).step_by(2) {
            let named = match piece.strip_prefix("core.type ") {
                Some(name) => Some(name),
                None => (piece.strip_prefix("std/"))
                    .and_then(|rest| rest.split_once(".type "))
                    .map(|(_, name)| name),
            };
            let Some(name) = named else {
                continue;
            };
            let bare = !name.is_empty() && name.chars().all(|c| c.is_ascii_alphanumeric());
            let found_as = piece.strip_prefix("core.type ").unwrap_or(piece);
            if bare && !found.iter().any(|f| f == found_as) {
                found.push(found_as.to_string());
            }
        }
    }
}

/// std's classes and interfaces that are `@connected`, by key, as
/// `std/collections.type Queue`.
fn connected_std_types() -> Vec<String> {
    let sources = temper_frontend::sources::Sources::standard();
    let p = std::thread::Builder::new()
        .stack_size(64 << 20)
        .spawn(move || temper_frontend::stdlib::program(&sources).unwrap())
        .unwrap()
        .join()
        .unwrap();
    (p.decls.ids())
        .filter(|&d| p.decls.contains(d))
        .filter(|&d| {
            let decl = p.decls.get(d);
            matches!(decl.kind, DeclKind::Class | DeclKind::Interface)
                && decl.facts.contains(Facts::CONNECTED)
        })
        .filter_map(|d| std_key(&p, d))
        .collect()
}

#[test]
fn host_types_are_connected_in_core() {
    let crates = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("..");
    let scalars = (Scalar::ALL.iter())
        .map(|s| s.name())
        .filter(|name| !NOT_HOST_SCALARS.contains(name));
    let scalars: Vec<&str> = scalars.collect();
    let std_types = connected_std_types();
    assert!(std_types.iter().any(|t| t == "std/temporal.type Date"));
    let mut wrong = Vec::new();
    let mut checked = 0;
    let mut std_named = 0;
    for backend in Registry::built_in().all() {
        let src = crates.join(format!("temper-be-{}/src", backend.name));
        if !src.is_dir() {
            continue;
        }
        checked += 1;
        let mut named = Vec::new();
        named_types(&src, &mut named);
        let (std_names, core_names): (Vec<&String>, Vec<&String>) =
            named.iter().partition(|n| n.starts_with("std/"));
        for name in std_names {
            std_named += 1;
            if !std_types.contains(name) && !NOT_CONNECTED_STD.contains(&name.as_str()) {
                wrong.push(format!("{name} ({}): not @connected in std", backend.name));
            }
        }
        let named = core_names.into_iter().map(String::as_str);
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
    assert!(std_named >= 7, "{std_named} std types named");
    assert!(!scalars.is_empty());
    assert_eq!(wrong, Vec::<String>::new());
}
