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

/// Each function in `tests/luals/temper-core.lua` has, right above it,
/// one `---@param` per parameter, in order, so LuaLS checks calls of it
/// (an unannotated function is legal, so `--check` can't tell); and no
/// `---@param` or `---@return` is stranded above anything else.
#[test]
fn stub_annotations_cover_each_parameter() {
    let view = fs::read_to_string(
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/luals/temper-core.lua"),
    )
    .unwrap();
    assert_eq!(stub_problems(&view), Vec::<String>::new());
    // As `int32_tofloat64`'s were once: stranded above another
    // function's docs, leaving it unannotated.
    let stranded = "---@param n integer\n---@return number\n---Its sign.\n\
        ---@param n integer\n---@return integer\nfunction temper.int32_signum(n) end\n\n\
        function temper.int32_tofloat64(n) end\n\n---@param x integer\n\n";
    assert_eq!(
        stub_problems(stranded),
        [
            "line 6: temper.int32_signum's ---@param lines name [\"n\", \"n\"], not [\"n\"]",
            "line 8: temper.int32_tofloat64's ---@param lines name [], not [\"n\"]",
            "line 10: ---@param or ---@return above no function",
        ]
    );
}

/// What [`stub_annotations_cover_each_parameter`] finds wrong in a stub.
fn stub_problems(text: &str) -> Vec<String> {
    let lines: Vec<&str> = text.lines().map(str::trim).collect();
    let mut problems = Vec::new();
    for (i, line) in lines.iter().enumerate() {
        let block = || lines[..i].iter().rev().take_while(|l| l.starts_with("---"));
        if let Some(rest) = line.strip_prefix("function ") {
            let Some((name, params)) = rest.split_once('(') else {
                continue;
            };
            let params = params.split_once(')').map_or("", |(p, _)| p);
            let params: Vec<&str> = (params.split(',').map(str::trim))
                .filter(|p| !p.is_empty())
                .collect();
            let mut named: Vec<&str> = (block())
                .filter_map(|l| l.strip_prefix("---@param "))
                .filter_map(|l| l.split_whitespace().next())
                .map(|p| p.trim_end_matches('?'))
                .collect();
            named.reverse();
            if named != params {
                problems.push(format!(
                    "line {}: {name}'s ---@param lines name {named:?}, not {params:?}",
                    i + 1
                ));
            }
        } else if line.starts_with("---@param ") || line.starts_with("---@return ") {
            let next = lines[i..].iter().find(|l| !l.starts_with("---"));
            if !next.is_some_and(|l| l.starts_with("function ")) {
                problems.push(format!(
                    "line {}: ---@param or ---@return above no function",
                    i + 1
                ));
            }
        }
    }
    problems
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
