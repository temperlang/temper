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
        "panic",
        "panic_caused_by",
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
    for (_, path) in connected::TRY {
        assert!(defines(&runtime, path), "{path} isn't in temper-core");
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

/// Each of core's connected members that a callback's error parameter
/// makes fail (RFC 164) has a runtime function for calls whose callback
/// returns an `Expected` ([`connected::TRY`]), and only those, beside
/// std's members this backend connects.
#[test]
fn members_with_error_parameters_have_try_forms() {
    let all: Vec<&str> = connected::TRY.iter().map(|(k, _)| *k).collect();
    sorted_and_unique(&all);
    let (std, keys): (Vec<&str>, Vec<&str>) = all.into_iter().partition(|k| k.starts_with("std/"));
    for key in std {
        assert!(connected::connects_std(key), "{key} isn't in STD");
    }
    let p = core();
    let qnames = QNames::derive(&p);
    let mut generic: Vec<String> = (p.decls.ids())
        .filter(|&d| p.decls.contains(d) && p.decls.get(d).facts.contains(Facts::CONNECTED))
        .filter(|&d| {
            (p.decls.type_params(d).iter())
                .any(|&t| temper_tmpl::declared_errors::is_error_param(&p, t))
        })
        .filter_map(|d| qnames.get(d).map(|q| q.to_string()))
        .filter(|q| connected::lookup(q).is_some())
        .collect();
    generic.sort();
    assert_eq!(keys, generic);
}
