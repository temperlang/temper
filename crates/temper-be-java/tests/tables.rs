//! The Java variant's tables: every operator spelled, every connection
//! keyed by a connected declaration of core, every type connection by a
//! type of core, and every `Core` method called one that
//! `temper-core` has.

use std::fs;
use std::path::PathBuf;
use temper_be_java::{connected, ops};
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
        // std's connected classes (`Date`), by their keys, which the
        // backend's std table lists.
        if key.starts_with("std/") {
            assert!(connected::connects_std(key), "{key} isn't in STD");
            continue;
        }
        assert!(types.iter().any(|q| q == key), "{key} isn't a type of core");
    }
}

#[test]
fn runtime_helpers_are_the_runtimes() {
    let core = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("runtime/temper-core/src/main/java/temper/core/Core.java");
    let core = fs::read_to_string(core).unwrap();
    for i in 0..temper_tmpl::ops::OPS.len() as u32 {
        if let Some(ops::Spelling::Static("Core", method)) = ops::spelling(temper_tmpl::ops::Op(i))
        {
            assert!(
                core.contains(&format!(" {method}(")),
                "Core.{method} isn't in temper-core"
            );
        }
    }
    // What the translator and the main class call besides.
    for method in [
        "bubble",
        "getConsole",
        "initSimpleLogging",
        "throwBubble",
        "waitUntilTasksComplete",
    ] {
        assert!(
            core.contains(&format!(" {method}(")),
            "Core.{method} isn't in temper-core"
        );
    }
}
