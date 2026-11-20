//! The Lua variant's tables: every operator spelled, every connection
//! keyed by a connected declaration of core, every type connection by a
//! type of core, every runtime helper defined by
//! `temper-core` and typed in LuaLS's view of it, and the runtime's
//! version the one the rockspec names.

use std::fs;
use std::path::PathBuf;
use temper_be_lua::{LUA, connected, names, ops};
use temper_tmpl::ops::{OPS, Op};
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

fn runtime() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime/temper-core")
}

#[test]
fn helpers_are_the_runtimes_and_typed() {
    // `init.lua` defines most; `intnew.lua` the integer ones, which
    // `init.lua` copies into its table.
    let mut defined = String::new();
    for file in ["init.lua", "intnew.lua", "intold.lua"] {
        defined.push_str(&fs::read_to_string(runtime().join(file)).unwrap());
    }
    let view = fs::read_to_string(
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/luals/temper-core.lua"),
    )
    .unwrap();
    let mut helpers: Vec<&str> = (0..OPS.len() as u32)
        .filter_map(|i| ops::spelling(Op(i)).and_then(ops::helper))
        .collect();
    helpers.extend([
        "bubble",
        "int32_tostring",
        "int64_constructor",
        "type",
        "instance_of",
        "cast_to",
        "is_null",
        "pcall",
        "cast_to_string",
        "cast_to_int",
        "cast_to_float64",
        "cast_to_boolean",
    ]);
    // The connections' helpers too.
    for (_, connection) in connected::TABLE {
        if let connected::Connection::Helper(name) = connection {
            helpers.push(name);
        }
    }
    for helper in helpers {
        assert!(
            defined.contains(&format!("function temper.{helper}("))
                || defined.contains(&format!("temper.{helper} = ")),
            "{helper} isn't defined by temper-core"
        );
        assert!(
            view.contains(&format!("function temper.{helper}(")),
            "{helper} isn't typed in tests/luals/temper-core.lua"
        );
    }
    for printed in [
        "temper",
        "print",
        "tostring",
        "math",
        "require",
        "with_tostring",
    ] {
        assert!(
            names::RULES.reserved.iter().any(|l| l.contains(&printed)),
            "{printed} isn't reserved"
        );
    }
}

/// The rockspec `temper build` writes names the version generated
/// rockspecs depend on, in its name and its text.
#[test]
fn runtime_version_is_the_rockspecs() {
    use temper_tmpl::version::{Spelling, current, fill_runtime};
    let filled = fill_runtime(
        temper_be_lua::runtime::FILES,
        &temper_be_lua::runtime::VERSION,
        current(),
    );
    let rock = current().spell(Spelling::Rockspec);
    let name = format!("temper-core-{rock}.rockspec");
    let (_, written) = (filled.iter()).find(|(p, _)| *p == name).unwrap();
    assert!(
        written.contains(&format!("version = \"{rock}\"")),
        "{written}"
    );
    let spec = fs::read_to_string(runtime().join("temper-core-dev-1.rockspec")).unwrap();
    assert!(
        spec.contains(&format!("\"lua >= {}\"", LUA.lua_version)),
        "{spec}"
    );
}
