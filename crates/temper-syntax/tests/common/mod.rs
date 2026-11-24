//! Helpers shared by the lexer and parser tests: reading and updating
//! `.cases` files, and editing inputs randomly.
//!
//! A cases file has a few comment lines, then cases. Each case is a header
//! line `=== name`, or `=== name (markdown)` for a `.temper.md` source, then
//! the input text exactly, then a separator line like `--- tokens` and the
//! expected output. The input ends with the newline before the separator,
//! so an input that ends in a newline has a blank line there.

#![allow(dead_code)] // Each test crate uses some of these.

use std::{
    env, fs,
    path::{Path, PathBuf},
};
use temper_syntax::lex::SourceKind;

pub struct Case {
    /// The cases file's name, like `edge-cases.cases`.
    pub file: String,
    pub name: String,
    pub kind: SourceKind,
    pub input: String,
    pub expected: String,
}

/// The `.cases` files in `dir`, relative to the crate, with their cases.
pub fn case_files(dir: &str, separator: &str) -> Vec<(PathBuf, Vec<Case>)> {
    let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join(dir);
    let mut paths: Vec<_> = fs::read_dir(&dir)
        .unwrap()
        .map(|entry| entry.unwrap().path())
        .filter(|path| path.extension().is_some_and(|e| e == "cases"))
        .collect();
    paths.sort();
    paths
        .into_iter()
        .map(|path| {
            let mut cases = read_cases(&fs::read_to_string(&path).unwrap(), separator);
            let file = path.file_name().unwrap().to_string_lossy().to_string();
            for case in &mut cases {
                case.file = file.clone();
            }
            (path, cases)
        })
        .collect()
}

pub fn read_cases(text: &str, separator: &str) -> Vec<Case> {
    let separator = format!("--- {separator}\n");
    let mut cases = Vec::new();
    let mut rest = text;
    while let Some(start) = find_line(rest, "=== ") {
        let header_end = rest[start..]
            .find('\n')
            .map_or(rest.len(), |i| start + i + 1);
        let header = rest[start + 4..header_end].trim_end();
        let (name, kind) = match header.strip_suffix(" (markdown)") {
            Some(name) => (name, SourceKind::Markdown),
            None => (header, SourceKind::Standalone),
        };
        rest = &rest[header_end..];
        let at = find_line(rest, &separator).unwrap_or_else(|| panic!("case without {separator}"));
        let input = rest[..at].strip_suffix('\n').unwrap_or("");
        rest = &rest[at + separator.len()..];
        let expected_end = find_line(rest, "=== ").unwrap_or(rest.len());
        let expected = rest[..expected_end].trim_end_matches('\n');
        cases.push(Case {
            file: String::new(),
            name: name.to_string(),
            kind,
            input: input.to_string(),
            expected: if expected.is_empty() {
                String::new()
            } else {
                format!("{expected}\n")
            },
        });
        rest = &rest[expected_end..];
    }
    cases
}

/// Where a line starting with `prefix` begins, if any.
fn find_line(text: &str, prefix: &str) -> Option<usize> {
    if text.starts_with(prefix) {
        return Some(0);
    }
    text.find(&format!("\n{prefix}")).map(|i| i + 1)
}

/// Rewrites a cases file with new expected output, keeping its leading
/// comments.
pub fn write_cases(path: &Path, cases: &[Case], separator: &str) {
    let old = fs::read_to_string(path).unwrap();
    let mut out = old[..find_line(&old, "=== ").unwrap_or(old.len())].to_string();
    for case in cases {
        let markdown = if case.kind == SourceKind::Markdown {
            " (markdown)"
        } else {
            ""
        };
        out.push_str(&format!("=== {}{markdown}\n", case.name));
        if !case.input.is_empty() {
            out.push_str(&case.input);
            out.push('\n');
        }
        out.push_str(&format!("--- {separator}\n"));
        out.push_str(&case.expected);
        out.push('\n');
    }
    out.truncate(out.trim_end_matches('\n').len());
    out.push('\n');
    fs::write(path, out).unwrap();
}

/// Whether `UPDATE_SNAPSHOTS`'s value asks to update goldens: only `1`
/// does, so a stray `UPDATE_SNAPSHOTS=0` can't.
pub fn updating(value: Option<&str>) -> bool {
    value == Some("1")
}

/// Checks each case's output, and with `UPDATE_SNAPSHOTS=1`, saves new
/// output for review instead of failing, saying which files it rewrote.
pub fn check_cases(dir: &str, separator: &str, output: impl Fn(&Case) -> String) {
    let update = updating(env::var("UPDATE_SNAPSHOTS").ok().as_deref());
    let mut failures = Vec::new();
    let mut count = 0;
    // Failures in the files before this one.
    let mut earlier = 0;
    for (path, mut cases) in case_files(dir, separator) {
        for case in &mut cases {
            count += 1;
            let actual = output(case);
            if actual != case.expected {
                failures.push(first_difference(&path, case, &actual));
                case.expected = actual;
            }
        }
        if update && failures.len() > earlier {
            write_cases(&path, &cases, separator);
            eprintln!("UPDATE_SNAPSHOTS=1: rewrote {}", path.display());
        }
        earlier = failures.len();
    }
    assert!(count > 0, "no cases found");
    assert!(
        update || failures.is_empty(),
        "{} of {count} cases differ (UPDATE_SNAPSHOTS=1 accepts new output):\n{}",
        failures.len(),
        failures.join("\n")
    );
}

fn first_difference(path: &Path, case: &Case, actual: &str) -> String {
    let place = format!(
        "{} case {:?}",
        path.file_name().unwrap().to_string_lossy(),
        case.name
    );
    let mut expected_lines = case.expected.lines();
    let mut actual_lines = actual.lines();
    for line in 1.. {
        match (expected_lines.next(), actual_lines.next()) {
            (None, None) => break,
            (e, a) if e == a => {}
            (e, a) => {
                let e = e.unwrap_or("(end)");
                let a = a.unwrap_or("(end)");
                return format!("{place} line {line}:\n  expected {e}\n  actual   {a}");
            }
        }
    }
    format!("{place} differs")
}

/// Characters that matter to the lexer, plus some non-ASCII ones. Keep in
/// sync with `PIECES` in `compat/fuzz_lex.py`.
#[rustfmt::skip]
pub const PIECES: &[&str] = &[
    "\"", "\"\"\"", "'", "`", "{", "}", "(", ")", "[", "]", "<", ">", "/", "*", "\\", "$", "${",
    "\\u{", ":", "~", "#", ";", ".", ",", "-", "=", "!", "0", "1e", "x", "nym`", "\n", "\r\n",
    "  ", "\t", "\n```temper\n", "\n```\n", "\n    ", "é", "ό", "\u{FE0F}", "’", "😀", "\0",
    "return /", "/*", "*/", "//",
];

/// Makes a few random edits: inserting pieces, deleting, duplicating or
/// truncating.
pub fn mutate(text: &str, pieces: &[&str], rng: &mut Rng) -> String {
    let mut text = text.to_string();
    for _ in 0..1 + rng.below(4) {
        let at = boundary(&text, rng.below(text.len() + 1));
        match rng.below(4) {
            0 | 1 => text.insert_str(at, pieces[rng.below(pieces.len())]),
            2 => {
                let end = boundary(&text, at + rng.below(8));
                text.replace_range(at..end, "");
            }
            _ => {
                let end = boundary(&text, at + rng.below(16));
                let copy = text[at..end].to_string();
                text.insert_str(end, &copy);
            }
        }
    }
    if rng.below(10) == 0 {
        let at = boundary(&text, rng.below(text.len() + 1));
        text.truncate(at);
    }
    text
}

/// The nearest char boundary at or before `i`, clamped to the text.
fn boundary(text: &str, i: usize) -> usize {
    let mut i = i.min(text.len());
    while !text.is_char_boundary(i) {
        i -= 1;
    }
    i
}

/// A small xorshift generator, so runs repeat for a given seed.
pub struct Rng(pub u64);

impl Rng {
    pub fn below(&mut self, n: usize) -> usize {
        if n == 0 {
            return 0;
        }
        self.0 ^= self.0 << 13;
        self.0 ^= self.0 >> 7;
        self.0 ^= self.0 << 17;
        (self.0 % n as u64) as usize
    }
}

pub fn env_number(name: &str, default: u64) -> u64 {
    env::var(name)
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(default)
}
