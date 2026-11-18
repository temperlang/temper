//! The C++ variant's tables: every operator spelled, every connection
//! keyed by a connected declaration of core, every type connection by a
//! type of core, and every runtime function called one that
//! `temper-core` has.

use std::fs;
use std::path::PathBuf;
use temper_be_cpp::{connected, ops};
use temper_tmpl::{DeclKind, Facts, Program, QNames, text};

#[test]
fn every_operator_is_spelled() {
    assert_eq!(ops::unspelled(), Vec::<&str>::new());
}

/// temper-tmpl's core fixture, checked against core.temper there.
fn core() -> Program {
    let path =
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../temper-tmpl/tests/fixtures/core.tmpl");
    text::read(&fs::read_to_string(path).unwrap()).unwrap()
}

fn sorted_and_unique(keys: &[&str]) {
    let mut sorted = keys.to_vec();
    sorted.sort();
    sorted.dedup();
    assert_eq!(keys, sorted, "sorted and unique, for lookup");
}

#[test]
fn connections_name_core_connected_declarations() {
    let keys: Vec<&str> = connected::TABLE.iter().map(|(k, _)| *k).collect();
    sorted_and_unique(&keys);
    let p = core();
    let qnames = QNames::derive(&p);
    let connected: Vec<String> = p
        .decls
        .ids()
        .filter(|&d| p.decls.contains(d) && p.decls.get(d).facts.contains(Facts::CONNECTED))
        .filter_map(|d| qnames.get(d).map(|q| q.to_string()))
        .collect();
    for key in keys {
        // std's members this backend connects, by their keys.
        if key.starts_with("std/") {
            assert!(connected::connects_std(key), "{key} isn't in STD");
            continue;
        }
        assert!(
            connected.iter().any(|q| q == key),
            "{key} isn't connected in core"
        );
    }
}

#[test]
fn type_connections_name_core_types() {
    let keys: Vec<&str> = connected::TYPES.iter().map(|(k, _)| *k).collect();
    sorted_and_unique(&keys);
    let p = core();
    let qnames = QNames::derive(&p);
    let types: Vec<String> = p
        .decls
        .ids()
        .filter(|&d| {
            p.decls.contains(d)
                && matches!(p.decls.get(d).kind, DeclKind::Class | DeclKind::Interface)
        })
        .filter_map(|d| qnames.get(d).map(|q| q.to_string()))
        .collect();
    for key in keys {
        assert!(types.iter().any(|q| q == key), "{key} isn't a type of core");
    }
}

/// The text of every header of the runtime.
fn runtime() -> String {
    let dir = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime/temper-core");
    let mut text = String::new();
    for entry in fs::read_dir(dir).unwrap() {
        text.push_str(&fs::read_to_string(entry.unwrap().path()).unwrap());
    }
    text
}

/// Whether the runtime defines `path` (`Int::add`): a function of that
/// name inside a namespace of that name.
fn defines(runtime: &str, path: &str) -> bool {
    let (namespace, function) = path.rsplit_once("::").unwrap_or(("", path));
    let Some(start) = runtime.find(&format!("namespace {namespace} {{")) else {
        return namespace.is_empty() && runtime.contains(&format!(" {function}("));
    };
    runtime[start..].contains(&format!(" {function}("))
}

#[test]
fn runtime_functions_are_the_runtimes() {
    let runtime = runtime();
    let mut called = ops::runtime_functions();
    // What the translator and connections call besides.
    called.extend([
        "Boolean::toString",
        "Console::get_console",
        "Console::log",
        "Int::toString",
        "bubble",
    ]);
    for path in called {
        assert!(defines(&runtime, path), "{path} isn't in temper-core");
    }
    for (_, connection) in connected::TABLE {
        if let connected::Connection::Call(path) = connection {
            assert!(
                path.starts_with("std::") || defines(&runtime, path),
                "{path} isn't in temper-core"
            );
        }
    }
    for (_, path, header) in connected::BODILESS {
        assert!(defines(&runtime, path), "{path} isn't in temper-core");
        let file = header
            .trim_start_matches("<temper-core/")
            .trim_end_matches('>');
        let dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("runtime/temper-core")
            .join(file);
        assert!(dir.exists(), "{header} isn't temper-core's");
    }
    assert!(runtime.contains("struct Type {"), "Console::Type");
}
