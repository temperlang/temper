//! `temper fmt` tests.
//!
//! - `cases_format_as_expected` checks each case in `tests/fmt/*.cases`
//!   (format: `tests/common/mod.rs`, separator `--- formatted`): the
//!   formatted text, or `error: ` and the message when the input is left
//!   alone. `UPDATE_SNAPSHOTS=1` accepts new output for review.
//! - `repo_files_format_safely` formats every Temper file in the repository
//!   in memory, and checks that formatting is idempotent and keeps the parse
//!   tree, comments and markdown prose (the formatter checks those itself
//!   and refuses otherwise, so a refusal fails the test).
//! - `mutated_inputs_format_safely` does the same for randomly edited
//!   copies of the formatter, parser and lexer cases (`FMT_FUZZ_SEED`,
//!   `FMT_FUZZ_ROUNDS`), which also exercises the refusal for code that
//!   doesn't parse.

mod common;

use common::{Rng, case_files, check_cases, env_number, mutate};
use std::{fs, path::Path};
use temper_syntax::fmt::{FmtError, format};
use temper_syntax::lex::SourceKind;

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
            Ok(once) => {
                formatted += 1;
                match format(&once, kind) {
                    Ok(twice) if twice == once => {}
                    Ok(_) => failures.push(format!("{}: not idempotent", path.display())),
                    Err(e) => failures.push(format!("{}: second run: {e}", path.display())),
                }
            }
            // Some test inputs are deliberately malformed.
            Err(FmtError::Unparseable(_)) => {}
            Err(e) => failures.push(format!("{}: {e}", path.display())),
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
                        match format(&text, kind) {
                            Ok(once) => {
                                let twice = format(&once, kind).unwrap_or_else(|e| {
                                    panic!("{}: second run: {e}\n{text:?}", context())
                                });
                                assert_eq!(once, twice, "{}: not idempotent\n{text:?}", context());
                            }
                            Err(FmtError::Unparseable(_)) => {}
                            Err(e) => panic!("{} as {kind:?}: {e}\ninput: {text:?}", context()),
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
        let start = std::time::Instant::now();
        for _ in 0..rounds {
            format(&text, kind).unwrap();
        }
        let ms = start.elapsed().as_secs_f64() * 1000.0 / rounds as f64;
        let name = path.strip_prefix(&root).unwrap().display();
        println!(
            "{name}: {} KiB, {} lines, {ms:.1} ms",
            text.len() / 1024,
            text.lines().count()
        );
    }
}
