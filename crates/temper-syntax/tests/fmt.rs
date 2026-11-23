//! `temper fmt` tests.
//!
//! - `cases_format_as_expected` checks each case in `tests/fmt/*.cases`
//!   (format: `tests/common/mod.rs`, separator `--- formatted`): the
//!   formatted text, or `error: ` and the message when the input is left
//!   alone. `UPDATE_SNAPSHOTS=1` accepts new output for review.
//!   `wrap_cases_hold_at_nearby_widths` checks `wrap.cases` at widths 90
//!   and 110 too, so they test the rules rather than one width.
//! - `repo_files_format_safely` formats every Temper file in the repository
//!   in memory, and checks that formatting is idempotent and keeps the parse
//!   tree, comments and markdown prose (the formatter checks those itself
//!   and refuses otherwise, so a refusal fails the test), at the line width
//!   and at a narrow one, so that most lists wrap.
//! - `mutated_inputs_format_safely` does the same for randomly edited
//!   copies of the formatter, parser and lexer cases (`FMT_FUZZ_SEED`,
//!   `FMT_FUZZ_ROUNDS`), which also exercises the refusal for code that
//!   doesn't parse.
//! - `wrap_census` (ignored) counts the lines wrapping changes over the
//!   repository and libraries, for notes/fmt.md.

mod common;

use common::{Rng, case_files, check_cases, env_number, mutate};
use std::{fs, path::Path};
use temper_syntax::fmt::{FmtError, Options, format, format_with};
use temper_syntax::lex::SourceKind;
use temper_syntax::parse::parse;

/// A narrow line width, at which most lists wrap.
const NARROW: usize = 30;

/// Formats with the defaults but the line width.
fn format_at(text: &str, kind: SourceKind, width: usize) -> Result<String, FmtError> {
    let options = Options {
        width: Some(width),
        ..Options::default()
    };
    format_with(&parse(text, kind), kind, options)
}

/// Formats twice, checking the second run changes nothing.
fn format_twice(text: &str, kind: SourceKind, width: usize) -> Result<String, String> {
    let once = format_at(text, kind, width).map_err(|e| e.to_string())?;
    match format_at(&once, kind, width) {
        Ok(twice) if twice == once => Ok(once),
        Ok(twice) => Err(format!(
            "not idempotent at width {width}:\n{}",
            first_change(&once, &twice)
        )),
        Err(e) => Err(format!("second run at width {width}: {e}")),
    }
}

/// The first line that differs, for messages.
fn first_change(a: &str, b: &str) -> String {
    let i = a.lines().zip(b.lines()).take_while(|(x, y)| x == y).count();
    format!(
        "line {}:\n  once:  {:?}\n  twice: {:?}",
        i + 1,
        a.lines().nth(i),
        b.lines().nth(i)
    )
}

#[test]
fn cases_format_as_expected() {
    check_cases("tests/fmt", "formatted", |case| {
        match format(&case.input, case.kind) {
            Ok(text) => text,
            Err(error) => format!("error: {error}\n"),
        }
    });
}

#[test]
fn wrap_cases_hold_at_nearby_widths() {
    let cases = common::read_cases(
        &fs::read_to_string(Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fmt/wrap.cases"))
            .unwrap(),
        "formatted",
    );
    for width in [90, 110] {
        for case in &cases {
            let text = format_at(&case.input, case.kind, width).unwrap();
            assert_eq!(text, case.expected, "case {:?} at width {width}", case.name);
        }
    }
}

#[test]
fn formatting_twice_changes_nothing() {
    for (path, cases) in common::case_files("tests/fmt", "formatted") {
        for case in cases {
            if let Ok(once) = format(&case.input, case.kind) {
                let twice = format(&once, case.kind).unwrap_or_else(|e| {
                    panic!("{} case {:?}: second run: {e}", path.display(), case.name)
                });
                assert_eq!(once, twice, "{} case {:?}", path.display(), case.name);
            }
        }
    }
}

#[test]
fn repo_files_format_safely() {
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    let mut files = Vec::new();
    for dir in ["lib", "crates", "benchmarks", "site"] {
        temper_files(&root.join(dir), &mut files);
    }
    assert!(files.len() > 100, "found only {} files", files.len());
    let mut failures = Vec::new();
    let mut formatted = 0;
    for path in &files {
        let text = fs::read_to_string(path).unwrap();
        let kind = if path.to_string_lossy().ends_with(".md") {
            SourceKind::Markdown
        } else {
            SourceKind::Standalone
        };
        match format(&text, kind) {
            Ok(_) => formatted += 1,
            // Some test inputs are deliberately malformed.
            Err(FmtError::Unparseable(_)) => continue,
            Err(e) => failures.push(format!("{}: {e}", path.display())),
        }
        for width in [temper_syntax::fmt::WIDTH, NARROW] {
            if let Err(e) = format_twice(&text, kind, width) {
                failures.push(format!("{}: {e}", path.display()));
            }
        }
    }
    assert!(failures.is_empty(), "{}", failures.join("\n"));
    assert!(
        formatted * 10 > files.len() * 9,
        "formatted only {formatted} of {}",
        files.len()
    );
}

#[test]
fn mutated_inputs_format_safely() {
    let seed = env_number("FMT_FUZZ_SEED", 1);
    let rounds = env_number("FMT_FUZZ_ROUNDS", 20);
    let mut rng = Rng(seed.max(1));
    let pieces: Vec<&str> = common::PIECES
        .iter()
        .chain(&[
            "\n  ",
            "  ",
            "{\n",
            "\n}",
            "(",
            ")",
            ",",
            " = ",
            "// c\n",
            "/* c\n */",
        ])
        .copied()
        .collect();
    for (dir, separator) in [
        ("tests/fmt", "formatted"),
        ("tests/parse", "tree"),
        ("tests", "tokens"),
    ] {
        for (path, cases) in case_files(dir, separator) {
            for case in cases {
                for round in 0..=rounds {
                    let text = if round == 0 {
                        case.input.clone()
                    } else {
                        mutate(&case.input, &pieces, &mut rng)
                    };
                    let context = || {
                        format!(
                            "{} case {:?} round {round} (seed {seed})",
                            path.display(),
                            case.name
                        )
                    };
                    for kind in [SourceKind::Standalone, SourceKind::Markdown] {
                        if let Err(FmtError::Unparseable(_)) = format(&text, kind) {
                            continue;
                        }
                        for width in [temper_syntax::fmt::WIDTH, NARROW] {
                            if let Err(e) = format_twice(&text, kind, width) {
                                panic!("{} as {kind:?}: {e}\ninput: {text:?}", context());
                            }
                        }
                    }
                }
            }
        }
    }
}

fn temper_files(dir: &Path, out: &mut Vec<std::path::PathBuf>) {
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };
    let mut entries: Vec<_> = entries.map(|e| e.unwrap().path()).collect();
    entries.sort();
    for path in entries {
        let name = path.file_name().unwrap().to_string_lossy().to_string();
        if path.is_dir() {
            if !matches!(
                name.as_str(),
                "target" | "temper.out" | "node_modules" | "build"
            ) {
                temper_files(&path, out);
            }
        } else if name.ends_with(".temper") || name.ends_with(".temper.md") {
            out.push(path);
        }
    }
}

/// Times formatting the largest files in core and std, which the language
/// server will do on save (budget: tens of milliseconds each, in release).
/// Run with `cargo test --release --test fmt -- --ignored --nocapture`.
#[test]
#[ignore]
fn format_speed() {
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../lib");
    let mut files = Vec::new();
    temper_files(&root, &mut files);
    files.sort_by_key(|p| std::cmp::Reverse(fs::metadata(p).unwrap().len()));
    for path in files.iter().take(4) {
        let text = fs::read_to_string(path).unwrap();
        let kind = if path.to_string_lossy().ends_with(".md") {
            SourceKind::Markdown
        } else {
            SourceKind::Standalone
        };
        let rounds = 20;
        let time = |options: Options| {
            let start = std::time::Instant::now();
            for _ in 0..rounds {
                format_with(&parse(&text, kind), kind, options).unwrap();
            }
            start.elapsed().as_secs_f64() * 1000.0 / rounds as f64
        };
        let unwrapped = time(Options {
            width: None,
            ..Options::default()
        });
        let ms = time(Options::default());
        let name = path.strip_prefix(&root).unwrap().display();
        println!(
            "{name}: {} KiB, {} lines, {ms:.1} ms ({unwrapped:.1} ms without wrapping)",
            text.len() / 1024,
            text.lines().count()
        );
    }
}

/// Counts the lines wrapping lists changes, by group of files, at several
/// widths, with data lists kept as written or not, over the repository's
/// Temper files (core and std in lib/, tests in crates/, docs' examples)
/// and the libraries in `FMT_CENSUS_LIBS` (paths separated by `:`). A line
/// changed is one of the input's that the output doesn't have (as a
/// multiset). Run with
/// `cargo test --release --test fmt wrap_census -- --ignored --nocapture`.
#[test]
#[ignore]
fn wrap_census() {
    use std::collections::HashMap;
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    let mut groups: Vec<(String, Vec<std::path::PathBuf>)> = Vec::new();
    for (name, dir) in [
        ("core+std", "lib"),
        ("tests", "crates"),
        ("benchmarks", "benchmarks"),
    ] {
        let mut files = Vec::new();
        temper_files(&root.join(dir), &mut files);
        groups.push((name.to_string(), files));
    }
    let mut docs = Vec::new();
    markdown_files(&root.join("docs"), &mut docs);
    groups.push(("docs".to_string(), docs));
    for dir in std::env::var("FMT_CENSUS_LIBS")
        .unwrap_or_default()
        .split(':')
    {
        if dir.is_empty() {
            continue;
        }
        let mut files = Vec::new();
        temper_files(Path::new(dir), &mut files);
        let name = Path::new(dir)
            .file_name()
            .unwrap()
            .to_string_lossy()
            .to_string();
        groups.push((name, files));
    }
    let changed = |a: &str, b: &str| -> usize {
        let mut counts: HashMap<&str, isize> = HashMap::new();
        for line in b.lines() {
            *counts.entry(line).or_default() += 1;
        }
        let mut n = 0;
        for line in a.lines() {
            let c = counts.entry(line).or_default();
            if *c > 0 {
                *c -= 1;
            } else {
                n += 1;
            }
        }
        n
    };
    let widths = [80, 88, 100, 120];
    println!(
        "group: files formatted (of all), code lines; then for each width, lines over it \
         before wrapping, and lines wrapping changes keeping data lists / wrapping them too"
    );
    for (name, files) in &groups {
        let (mut done, mut lines) = (0, 0);
        let mut over = [0; 4];
        let mut wrapped = [[0; 2]; 4];
        for path in files {
            let text = fs::read_to_string(path).unwrap();
            let kind = if path.extension().is_some_and(|e| e == "md") {
                SourceKind::Markdown
            } else {
                SourceKind::Standalone
            };
            let parsed = parse(&text, kind);
            let options = |width, keep_data| Options { width, keep_data };
            let Ok(base) = format_with(&parsed, kind, options(None, true)) else {
                continue;
            };
            done += 1;
            lines += base.lines().count();
            for (w, &width) in widths.iter().enumerate() {
                over[w] += base.lines().filter(|l| l.chars().count() > width).count();
                for (d, keep_data) in [true, false].into_iter().enumerate() {
                    let out = format_with(&parsed, kind, options(Some(width), keep_data)).unwrap();
                    wrapped[w][d] += changed(&base, &out);
                }
            }
        }
        let cells: Vec<String> = (0..widths.len())
            .map(|w| {
                let [kept, all] = wrapped[w];
                format!("{}: {} over, {kept}/{all}", widths[w], over[w])
            })
            .collect();
        println!(
            "{name}: {done} files (of {}), {lines} lines; {}",
            files.len(),
            cells.join("; ")
        );
    }
}

fn markdown_files(dir: &Path, out: &mut Vec<std::path::PathBuf>) {
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };
    for path in entries.map(|e| e.unwrap().path()) {
        if path.is_dir() {
            markdown_files(&path, out);
        } else if path.extension().is_some_and(|e| e == "md") {
            out.push(path);
        }
    }
}
