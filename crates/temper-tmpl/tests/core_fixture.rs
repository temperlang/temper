//! `tests/fixtures/core.tmpl`, a hand-written `uses` section for the core
//! members that subset A's old key table had (48, plus `getConsole`), the
//! generic types' (`Listed`, `List`, `ListBuilder`, `Pair`, `Mapped`,
//! `Map`, and `MapBuilder`), those that take functions, and the string
//! indices', `StringBuilder`'s, `Empty`'s, `Deque`'s, and
//! `DenseBitVector`'s, checked against `core.temper`: each
//! member's name, kind, parameters (names, types, and which are optional),
//! result, bubbling, and `@connected` mark. Like the frontend's
//! `builtin_supertypes_are_cores`, it reads `core.temper` as text, so this
//! crate needn't depend on the frontend.

use std::collections::HashMap;
use temper_tmpl::{DeclKind, Facts, Profile, Program, text, validate};

const FIXTURE: &str = include_str!("fixtures/core.tmpl");
const CORE: &str = include_str!("../../../lib/core/core.temper");

/// A member as `core.temper` declares it.
#[derive(Debug, PartialEq)]
struct Member {
    kind: &'static str,
    params: Vec<(String, String, bool)>,
    result: String,
    bubbles: bool,
    connected: bool,
}

/// Core's spelling of a type, as tmpl writes it.
fn type_name(core: &str) -> String {
    // Each word, `Int` as `Int32`, without `builtins.`; and `fn (` spaced.
    let t = core.trim().replace("builtins.", "").replace("fn(", "fn (");
    let mut out = String::new();
    let mut word = String::new();
    for c in t.chars().chain([' ']) {
        if c.is_alphanumeric() || c == '_' {
            word.push(c);
            continue;
        }
        out.push_str(if word == "Int" { "Int32" } else { &word });
        word.clear();
        out.push(c);
    }
    out.pop();
    out
}

/// What's inside parentheses, after the `(` that `text` follows, and what
/// comes after the `)` that closes it.
fn split_parens(text: &str) -> Option<(&str, &str)> {
    let mut depth = 0;
    for (i, c) in text.char_indices() {
        match c {
            '(' => depth += 1,
            ')' if depth == 0 => return Some((&text[..i], &text[i + 1..])),
            ')' => depth -= 1,
            _ => {}
        }
    }
    None
}

/// A parameter list split at its commas outside parentheses and angle
/// brackets.
fn top_level_commas(text: &str) -> Vec<&str> {
    let (mut depth, mut start, mut out) = (0, 0, Vec::new());
    for (i, c) in text.char_indices() {
        match c {
            '(' | '<' => depth += 1,
            ')' | '>' => depth -= 1,
            ',' if depth == 0 => {
                out.push(&text[start..i]);
                start = i + 1;
            }
            _ => {}
        }
    }
    out.push(&text[start..]);
    out
}

/// Core's lines, with a declaration whose parameters span several lines
/// joined into one.
fn logical_lines() -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    let mut open = 0i32;
    for line in CORE.lines() {
        let depth = |s: &str| s.matches('(').count() as i32 - s.matches(')').count() as i32;
        if open > 0 {
            let last = out.last_mut().unwrap();
            last.push(' ');
            last.push_str(line.trim());
            open += depth(line);
            continue;
        }
        let trimmed = line.trim_start();
        if trimmed.starts_with("public ") || trimmed.starts_with("export let ") {
            open = depth(line).max(0);
        }
        out.push(line.to_string());
    }
    out
}

/// Members of core's top-level classes and interfaces, by `Type.name`,
/// and its top-level functions by name, from single-line declarations.
fn core_members() -> HashMap<String, Member> {
    let mut members = HashMap::new();
    let mut owner: Option<String> = None;
    let mut connected = false;
    for line in logical_lines() {
        let line = line.as_str();
        let trimmed = line.trim();
        if line.starts_with('}') {
            owner = None;
        }
        let top = line.trim_start_matches("export ");
        let top = top.trim_start_matches("sealed ");
        for kind in ["class ", "interface "] {
            if let Some(rest) = top.strip_prefix(kind) {
                let name: String = rest.chars().take_while(|c| c.is_alphanumeric()).collect();
                owner = Some(name);
            }
        }
        if trimmed == "@connected" {
            connected = true;
            continue;
        }
        if trimmed.starts_with('@') {
            continue;
        }
        let (decl, key) = if let Some(rest) = trimmed.strip_prefix("public ") {
            match &owner {
                Some(owner) if line.starts_with("  ") || line.starts_with("   ") => {
                    (rest, Some(owner.clone()))
                }
                _ => (rest, None),
            }
        } else if let Some(rest) = line.strip_prefix("export let ") {
            (rest, Some(String::new()))
        } else {
            connected = false;
            continue;
        };
        let Some(owner) = key else {
            connected = false;
            continue;
        };
        let (kind, decl) = if let Some(rest) = decl.strip_prefix("static ") {
            ("static", rest)
        } else if let Some(rest) = decl.strip_prefix("get ") {
            ("get", rest)
        } else if owner.is_empty() {
            ("fn", decl)
        } else {
            ("method", decl)
        };
        let Some((name, rest)) = decl.split_once('(') else {
            connected = false;
            continue;
        };
        // A generic method's name, without its type parameters.
        let name = name.split('<').next().unwrap_or(name);
        let Some((params, rest)) = split_parens(rest) else {
            connected = false;
            continue;
        };
        let params: Option<Vec<_>> = top_level_commas(params)
            .into_iter()
            .filter(|p| !p.trim().is_empty())
            .map(|p| {
                let (name, ty) = p.split_once(':')?;
                let (ty, default) = match ty.split_once('=') {
                    Some((ty, _)) => (ty, true),
                    None => (ty, false),
                };
                let name = name.trim().trim_start_matches("let ").trim();
                Some((name.to_string(), type_name(ty), default))
            })
            .collect();
        let Some(params) = params else {
            connected = false;
            continue;
        };
        let rest = rest.trim_start_matches(':');
        let rest = rest.split(['{', ';']).next().unwrap();
        let (result, bubbles) = match rest.split_once(" throws ") {
            Some((result, _)) => (result, true),
            None => (rest, false),
        };
        let key = if owner.is_empty() {
            name.to_string()
        } else {
            format!("{owner}.{name}")
        };
        members.insert(
            key,
            Member {
                kind,
                params,
                result: type_name(result),
                bubbles,
                connected,
            },
        );
        connected = false;
    }
    members
}

/// A type as tmpl writes it, without declarations' numbers: `List<T>`
/// for `List#64<T#68>`.
fn unnumbered(ty: &str) -> String {
    let mut out = String::new();
    let mut chars = ty.chars().peekable();
    while let Some(c) = chars.next() {
        if c == '#' {
            while chars.next_if(char::is_ascii_digit).is_some() {}
        } else {
            out.push(c);
        }
    }
    out
}

/// The fixture's members, as core's would read.
fn fixture_members(p: &Program) -> HashMap<String, Member> {
    let mut members = HashMap::new();
    for decl in p.decls.ids() {
        let d = p.decls.get(decl);
        let kind = match d.kind {
            DeclKind::Method => "method",
            DeclKind::Getter => "get",
            DeclKind::Function if p.decls.get(d.owner).kind == DeclKind::Library => "fn",
            DeclKind::Function => "static",
            _ => continue,
        };
        let sig = p.signature(decl).unwrap();
        let names = p.decls.param_names(decl);
        let params = sig
            .params
            .iter()
            .zip(sig.optional)
            .zip(names)
            .map(|((&t, &optional), &name)| {
                (
                    p.strings.get(name).to_string(),
                    unnumbered(&p.show_type(t)),
                    optional,
                )
            })
            .collect();
        let result = p.show_type(sig.result);
        let owner = if kind == "fn" {
            String::new()
        } else {
            format!("{}.", p.name(d.owner))
        };
        members.insert(
            format!("{owner}{}", p.name(decl)),
            Member {
                kind,
                params,
                result: unnumbered(&result),
                bubbles: sig.bubbles,
                connected: d.facts.contains(Facts::CONNECTED),
            },
        );
    }
    members
}

/// Builtins the compiler gives, which `core.temper` doesn't declare:
/// `async`, Kotlin's `BuiltinOperatorId.Async`, and the state machines'
/// helpers (Kotlin's `SafeAdaptGeneratorFn` and `CoroHelperSpecials`).
const COMPILER_BUILTINS: [&str; 4] = [
    "async",
    "awakeUpon",
    "getPromiseResultSync",
    "safeAdaptGeneratorFn",
];

#[test]
fn the_fixture_is_valid_canonical_tmpl() {
    let p = text::read(FIXTURE).unwrap();
    assert_eq!(validate(&p, &Profile::full()), []);
    assert_eq!(text::write(&p), FIXTURE);
}

#[test]
fn the_fixture_matches_core_temper() {
    let p = text::read(FIXTURE).unwrap();
    let fixture = fixture_members(&p);
    // The old key table's 48 members, `getConsole`, the generic types'
    // members without function types (subset B), those with them
    // (function values), and the core types and members that the
    // functional tests use next (core's fixture gaps), and coroutines'.
    assert_eq!(fixture.len(), 128);
    let core = core_members();
    let mut names: Vec<&String> = fixture.keys().collect();
    names.sort();
    for name in names {
        if COMPILER_BUILTINS.contains(&name.as_str()) {
            continue;
        }
        let found = core.get(name);
        assert_eq!(found, Some(&fixture[name]), "{name}");
    }
}
