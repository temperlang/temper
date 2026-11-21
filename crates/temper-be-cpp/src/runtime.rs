//! This crate's runtime, `runtime/temper-core`, built in, for
//! `temper.out` beside generated libraries, as Kotlin Temper copies it
//! there. A test in `temper-driver` checks that [`FILES`] lists every file of
//! it that Kotlin copies: not its
//! dotfiles, nor its own tests and tools (JS's `package-lock.json`,
//! Python's `mypyc/`, and Java's `src/test/`). [`VERSION`] says where
//! its files name its version, which `temper build` fills in (and
//! Lua's rockspec is renamed for it, as Kotlin names it); the sources say
//! `0.0.0`, so the runtime still builds and tests where it is.

use temper_tmpl::version::{RuntimeVersion, Spelling, VersionSpot};

/// Each file, by path from the runtime's directory, with its text.
pub const FILES: &[(&str, &str)] = &[
    (
        "any_value.hpp",
        include_str!("../runtime/temper-core/any_value.hpp"),
    ),
    (
        "base_types.hpp",
        include_str!("../runtime/temper-core/base_types.hpp"),
    ),
    (
        "boolean.hpp",
        include_str!("../runtime/temper-core/boolean.hpp"),
    ),
    (
        "casting.hpp",
        include_str!("../runtime/temper-core/casting.hpp"),
    ),
    (
        "compare.hpp",
        include_str!("../runtime/temper-core/compare.hpp"),
    ),
    (
        "console.hpp",
        include_str!("../runtime/temper-core/console.hpp"),
    ),
    ("core.hpp", include_str!("../runtime/temper-core/core.hpp")),
    ("date.hpp", include_str!("../runtime/temper-core/date.hpp")),
    (
        "dense_bit_vector.hpp",
        include_str!("../runtime/temper-core/dense_bit_vector.hpp"),
    ),
    (
        "deque.hpp",
        include_str!("../runtime/temper-core/deque.hpp"),
    ),
    (
        "float64.hpp",
        include_str!("../runtime/temper-core/float64.hpp"),
    ),
    (
        "generator.hpp",
        include_str!("../runtime/temper-core/generator.hpp"),
    ),
    ("int.hpp", include_str!("../runtime/temper-core/int.hpp")),
    (
        "int64.hpp",
        include_str!("../runtime/temper-core/int64.hpp"),
    ),
    ("list.hpp", include_str!("../runtime/temper-core/list.hpp")),
    (
        "list_builder.hpp",
        include_str!("../runtime/temper-core/list_builder.hpp"),
    ),
    ("map.hpp", include_str!("../runtime/temper-core/map.hpp")),
    (
        "map_builder.hpp",
        include_str!("../runtime/temper-core/map_builder.hpp"),
    ),
    (
        "mapped.hpp",
        include_str!("../runtime/temper-core/mapped.hpp"),
    ),
    (
        "module_init.hpp",
        include_str!("../runtime/temper-core/module_init.hpp"),
    ),
    ("net.hpp", include_str!("../runtime/temper-core/net.hpp")),
    (
        "nullable_param.hpp",
        include_str!("../runtime/temper-core/nullable_param.hpp"),
    ),
    ("pair.hpp", include_str!("../runtime/temper-core/pair.hpp")),
    (
        "promise.hpp",
        include_str!("../runtime/temper-core/promise.hpp"),
    ),
    (
        "regex.hpp",
        include_str!("../runtime/temper-core/regex.hpp"),
    ),
    ("str.hpp", include_str!("../runtime/temper-core/str.hpp")),
    (
        "string.hpp",
        include_str!("../runtime/temper-core/string.hpp"),
    ),
    (
        "string_builder.hpp",
        include_str!("../runtime/temper-core/string_builder.hpp"),
    ),
    (
        "temper_bubble.hpp",
        include_str!("../runtime/temper-core/temper_bubble.hpp"),
    ),
    (
        "test_main.hpp",
        include_str!("../runtime/temper-core/test_main.hpp"),
    ),
];

/// Where the runtime names its version, which `temper build` fills with
/// the build's, in SemVer's spelling.
///
/// Nothing keeps it from being published: it has no package file of its
/// own.
pub const VERSION: RuntimeVersion = RuntimeVersion {
    spots: &[VersionSpot {
        path: "core.hpp",
        text: "#define TEMPER_CORE_VERSION \"0.0.0\"",
        spelling: Spelling::SemVer,
        rename: None,
    }],
    unpublished: &[],
};

/// The runtime's headers that generated code includes, in `core.hpp`'s
/// order, each with the names it declares in `temper::core`. A generated
/// header or `.cpp` that spells one (`core::Regex` in a `.cpp`,
/// `temper::core::Mapped` in a header) includes its header
/// ([`includes_for`]), so a user's file that includes a library's header
/// parses only the runtime that header uses. A test checks that each
/// names everything its header declares.
pub const FEATURES: &[(&str, &[&str])] = &[
    ("temper_bubble.hpp", &["TemperBubble"]),
    ("any_value.hpp", &["AnyValueBase", "AnyValueBox", "any_box"]),
    ("nullable_param.hpp", &["NullableParam"]),
    ("pair.hpp", &["Pair", "PairFactory"]),
    ("str.hpp", &["Str", "str_detail"]),
    (
        "base_types.hpp",
        &[
            "NonDeduced",
            "IsIndirect",
            "Param",
            "Never",
            "Invalid",
            "Empty",
            "empty",
            "object",
            "borrow_this",
            "cat_impl",
            "cat",
            "bubble",
            "panic",
            "is_null",
            "not_null",
            "print",
            "pure_virtual",
            "testBail",
        ],
    ),
    ("list.hpp", &["List"]),
    (
        "module_init.hpp",
        &["ModuleInitState", "init_once", "GlobalSlot"],
    ),
    (
        "casting.hpp",
        &[
            "to_base",
            "checked_cast",
            "is_box",
            "checked_cast_box",
            "temper_upcast",
            "Coercible",
            "coerce",
        ],
    ),
    ("compare.hpp", &["Compare"]),
    ("boolean.hpp", &["Boolean"]),
    ("int.hpp", &["Int"]),
    ("int64.hpp", &["Int64"]),
    ("float64.hpp", &["Float64"]),
    ("console.hpp", &["Console"]),
    ("string.hpp", &["String"]),
    ("string_builder.hpp", &["StringBuilder"]),
    ("list_builder.hpp", &["ListBuilder"]),
    ("mapped.hpp", &["Mapped"]),
    ("map_builder.hpp", &["MapBuilder"]),
    ("map.hpp", &["Map"]),
    ("deque.hpp", &["Deque"]),
    ("dense_bit_vector.hpp", &["DenseBitVector"]),
    ("date.hpp", &["Date"]),
    ("regex.hpp", &["Regex"]),
    (
        "generator.hpp",
        &[
            "GeneratorResult",
            "ValueResult",
            "DoneResult",
            "DoneResultSentinel",
            "doneResult",
            "Generator",
            "SafeGenerator",
            "adapt_generator_fn",
            "safe_adapt_generator_fn",
            "next",
            "GeneratorIterator",
            "begin",
            "end",
        ],
    ),
    (
        "promise.hpp",
        &[
            "async_task_queue",
            "async_loop_running",
            "async_enqueue",
            "AsyncInbox",
            "async_inbox",
            "async_hold",
            "async_post",
            "async_take_posted",
            "async_drain",
            "PromiseState",
            "Promise",
            "PromiseBuilder",
            "PromiseBuilderNs",
            "complete",
            "breakpromise",
            "getpromise",
            "awake_upon",
            "get_promise_result_sync",
            "PromiseAwaiter",
            "async_run",
        ],
    ),
];

/// The runtime's macros, with their headers.
const MACROS: &[(&str, &str)] = &[("TEMPER_STR", "str.hpp")];

/// Standard names that generated code spells, after `std::`, with their
/// headers. A test checks that generated code spells no others.
pub const STD: &[(&str, &str)] = &[
    ("max", "<algorithm>"),
    ("min", "<algorithm>"),
    ("fmod", "<cmath>"),
    ("deque", "<deque>"),
    ("function", "<functional>"),
    ("numeric_limits", "<limits>"),
    ("enable_shared_from_this", "<memory>"),
    ("make_shared", "<memory>"),
    ("shared_ptr", "<memory>"),
    ("lock_guard", "<mutex>"),
    ("mutex", "<mutex>"),
    ("ostringstream", "<sstream>"),
    ("string", "<string>"),
    ("to_string", "<string>"),
    ("type_index", "<typeindex>"),
    ("forward", "<utility>"),
    ("move", "<utility>"),
    ("vector", "<vector>"),
];

/// Standard names that generated code spells without `std::`, with
/// their headers. A test checks that it spells no other `_t` types.
pub const BARE_STD: &[(&str, &str)] = &[("int32_t", "<cstdint>"), ("int64_t", "<cstdint>")];

/// What a generated file's text needs included: the [`FEATURES`] headers
/// whose names it spells or whose [`MACROS`] it uses, in `core.hpp`'s
/// order, then the standard headers of the [`STD`] and [`BARE_STD`]
/// names it spells, in alphabetical order. It reads names after
/// `core::`, unless another namespace than `temper` comes before (a
/// library's own `core`), and after `std::`; one in a comment only adds
/// an include.
pub fn includes_for(text: &str) -> Vec<String> {
    let mut runtime = vec![false; FEATURES.len()];
    let mut std = std::collections::BTreeSet::new();
    for (prefix, is_core) in [("core::", true), ("std::", false)] {
        for (i, _) in text.match_indices(prefix) {
            let before = &text[..i];
            let ok = match before.strip_suffix("::") {
                Some(outer) => {
                    is_core && (outer.strip_suffix("temper")).is_some_and(|r| !r.ends_with(ident))
                }
                None => !before.ends_with(ident),
            };
            if !ok {
                continue;
            }
            let after = &text[i + prefix.len()..];
            let name = &after[..after.find(|c| !ident(c)).unwrap_or(after.len())];
            if is_core {
                if let Some(j) = FEATURES.iter().position(|(_, names)| names.contains(&name)) {
                    runtime[j] = true;
                }
            } else if let Some((_, header)) = STD.iter().find(|(n, _)| *n == name) {
                std.insert(*header);
            }
        }
    }
    for (name, header) in MACROS {
        if spells(text, name) {
            let j = FEATURES.iter().position(|(h, _)| h == header).unwrap();
            runtime[j] = true;
        }
    }
    for (name, header) in BARE_STD {
        if spells(text, name) {
            std.insert(*header);
        }
    }
    let runtime = (FEATURES.iter().zip(runtime))
        .filter(|(_, used)| *used)
        .map(|((header, _), _)| format!("<temper-core/{header}>"));
    runtime.chain(std.into_iter().map(String::from)).collect()
}

fn ident(c: char) -> bool {
    c == '_' || c.is_ascii_alphanumeric()
}

/// Whether `text` spells the identifier `name` (not as part of another).
fn spells(text: &str, name: &str) -> bool {
    text.match_indices(name)
        .any(|(i, _)| !text[..i].ends_with(ident) && !text[i + name.len()..].starts_with(ident))
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Every name a [`FEATURES`] header declares at the top of
    /// `temper::core` (its structs, namespaces, and functions), so
    /// generated code that spells it includes it.
    #[test]
    fn features_name_their_headers_declarations() {
        for (header, names) in FEATURES {
            let (_, text) = FILES.iter().find(|(p, _)| p == header).unwrap();
            for line in text.lines() {
                // Declarations in `temper::core` are indented 8 spaces.
                let Some(decl) = line.strip_prefix("        ") else {
                    continue;
                };
                if decl.starts_with([' ', '/', '}', '#']) || decl.starts_with("template") {
                    continue;
                }
                let named = ["struct ", "class ", "namespace ", "using "];
                let name = if named.iter().any(|k| decl.starts_with(k)) {
                    let rest = decl.split_once(' ').unwrap().1;
                    &rest[..rest.find(|c| !ident(c)).unwrap_or(rest.len())]
                } else {
                    // A function: the name before its first `(` outside
                    // template arguments.
                    let mut depth = 0i32;
                    let Some(paren) = decl.find(|c| {
                        depth += match c {
                            '<' => 1,
                            '>' => -1,
                            _ => 0,
                        };
                        c == '(' && depth == 0
                    }) else {
                        continue;
                    };
                    let head = &decl[..paren];
                    if head.contains("operator") {
                        continue;
                    }
                    head.rsplit(|c: char| !ident(c)).next().unwrap_or_default()
                };
                let name = name.trim_end_matches(|c: char| !ident(c));
                assert!(names.contains(&name), "{header}: {name}, in {line:?}");
            }
        }
        // Every runtime header is here, but `core.hpp`, which includes
        // them all, `net.hpp` (std net's, which its `.cpp` includes), and
        // `test_main.hpp` (`test.cpp`'s).
        let core = FILES.iter().find(|(p, _)| *p == "core.hpp").unwrap().1;
        for (path, _) in FILES {
            let featured = FEATURES.iter().any(|(h, _)| h == path);
            let other = matches!(*path, "core.hpp" | "net.hpp" | "test_main.hpp");
            assert!(featured != other, "{path}");
            let in_core = core.contains(&format!("#include \"{path}\"\n"));
            assert_eq!(in_core, featured, "{path} in core.hpp");
        }
        // In `core.hpp`'s order.
        let order: Vec<usize> = (FEATURES.iter())
            .map(|(h, _)| core.find(&format!("#include \"{h}\"\n")).unwrap())
            .collect();
        assert!(order.is_sorted(), "FEATURES out of core.hpp's order");
        for (_, header) in MACROS {
            assert!(FEATURES.iter().any(|(h, _)| h == header), "{header}");
        }
    }

    #[test]
    fn includes_for_reads_spelled_names() {
        let text = "core::Str s; temper::core::Mapped::Ordered m; core::Regex::x();\n\
                    kelpto::core::Map k; mycore::Int i; std::fmod(a, b); xstd::deque d;\n\
                    std::shared_ptr<std::vector<int32_t>> v; myint64_t w; std::thread t;";
        assert_eq!(
            includes_for(text),
            [
                "<temper-core/str.hpp>",
                "<temper-core/mapped.hpp>",
                "<temper-core/regex.hpp>",
                "<cmath>",
                "<cstdint>",
                "<memory>",
                "<vector>",
            ]
        );
        assert_eq!(
            includes_for("TEMPER_STR(\"a\") + MY_TEMPER_STR"),
            ["<temper-core/str.hpp>"]
        );
        assert!(includes_for("temper::core::Unknown x; void f();").is_empty());
    }
}
