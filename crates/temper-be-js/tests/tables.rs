//! The JS variant's tables: every operator spelled, and every
//! connection keyed by a connected declaration of core, every type
//! connection by a type of core, and every runtime helper exported by
//! `@temperlang/core` and reserved by the naming rules.

use std::fs;
use std::path::PathBuf;
use temper_be_js::{connected, names, ops};
use temper_tmpl::ops::{OPS, Op};
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

#[test]
fn helpers_are_the_runtimes_and_reserved() {
    let dir = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime/temper-core");
    let mut exported = String::new();
    for entry in fs::read_dir(dir).unwrap() {
        let path = entry.unwrap().path();
        if path.extension().is_some_and(|e| e == "js") {
            exported.push_str(&fs::read_to_string(path).unwrap());
        }
    }
    let mut helpers: Vec<&str> = (0..OPS.len() as u32)
        .filter_map(|i| ops::spelling(Op(i)).and_then(ops::helper))
        .collect();
    helpers.extend(["bubble", "panic", "requireInstanceOf", "requireNotNull"]);
    helpers.extend(connected::helpers());
    for helper in helpers {
        assert!(
            exported.contains(&format!("export const {helper} "))
                || exported.contains(&format!("export function {helper}(")),
            "{helper} isn't exported by @temperlang/core"
        );
        assert!(names::PRINTED.contains(&helper), "{helper} isn't reserved");
    }
}
