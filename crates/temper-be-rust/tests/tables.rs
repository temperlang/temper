//! The Rust variant's tables: every operator spelled, and every
//! connection keyed by a connected declaration of core, and every type
//! connection by a type of core.

use std::fs;
use std::path::PathBuf;
use temper_be_rust::{connected, ops};
use temper_tmpl::{DeclKind, Facts, Program, QNames, text};

#[test]
fn every_operator_is_spelled() {
    assert_eq!(ops::unspelled(), Vec::<&str>::new());
}

/// temper-tmpl's core fixture, checked against core there.
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
