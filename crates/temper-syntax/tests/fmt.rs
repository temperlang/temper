//! `temper fmt` tests.
//!
//! - `cases_format_as_expected` checks each case in `tests/fmt/*.cases`
//!   (format: `tests/common/mod.rs`, separator `--- formatted`): the
//!   formatted text, or `error: ` and the message when the input is left
//!   alone. `UPDATE_SNAPSHOTS=1` accepts new output for review.
//! - `repo_files_format_safely` formats every Temper file in the repository
//!   in memory, and checks that formatting is idempotent and keeps the parse
//!   tree, comments and markdown prose (the formatter checks those itself
//!   and refuses otherwise, so a refusal fails the test), and, apart from
//!   the formatter's own check, that only space changed ([`only_space_changed`]).
//! - `mutated_inputs_format_safely` does the same for randomly edited
//!   copies of the formatter, parser and lexer cases (`FMT_FUZZ_SEED`,
//!   `FMT_FUZZ_ROUNDS`), which also exercises the refusal for code that
//!   doesn't parse; a share of the mutants must still parse, so the test
//!   can't pass by refusing them all.
//! - `formatting_time_grows_linearly` checks that formatting four times
//!   the code takes about four times as long (under eight, where quadratic
//!   work takes sixteen), on long lines and deep nesting.
//! - `rules_census` (ignored) counts the lines each rule changes over the
//!   repository and libraries, for notes/fmt.md.

mod common;

use common::{Rng, case_files, check_cases, env_number, mutate};
use std::{fs, path::Path, time::Instant};
use temper_syntax::fmt::{FmtError, Options, format, format_with};
use temper_syntax::lex::{SourceKind, TokenKind, lex};
use temper_syntax::parse::parse;

/// Formats twice, checking the second run changes nothing.
fn format_twice(text: &str, kind: SourceKind) -> Result<String, String> {
    let once = format(text, kind).map_err(|e| e.to_string())?;
    match format(&once, kind) {
        Ok(twice) if twice == once => Ok(once),
        Ok(twice) => Err(format!("not idempotent:\n{}", first_change(&once, &twice))),
        Err(e) => Err(format!("second run: {e}")),
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

/// Checks that formatting `input` as `output` changed only space, by
/// checks of the test's own, apart from the formatter's: the text without
/// spaces, tabs and line breaks is the same; the tokens are, lexed again,
/// with comments' lines compared without their trailing space and the
/// indentation they share (doc comments' `*` lines without any), so that
/// lines inside a comment keep their indentation relative to each other;
/// and lines in `// fmt: off` regions are as written (`.temper` only).
fn only_space_changed(input: &str, output: &str, kind: SourceKind) -> Result<(), String> {
    let squeeze = |s: &str| s.replace([' ', '\t', '\r', '\n'], "");
    if squeeze(input) != squeeze(output) {
        return Err("more than space changed".to_string());
    }
    let tokens = |s: &str| -> Vec<String> {
        (lex(s, kind).into_iter())
            .filter(|t| t.kind != TokenKind::Space && t.synthetic.is_none())
            .map(|t| match t.kind {
                TokenKind::Comment => comment_lines(&t.text(s)),
                _ => t.text(s).into_owned(),
            })
            .collect()
    };
    let (before, after) = (tokens(input), tokens(output));
    if before != after {
        let i = before
            .iter()
            .zip(&after)
            .take_while(|(a, b)| a == b)
            .count();
        return Err(format!(
            "token {i} differs: {:?} became {:?}",
            before.get(i),
            after.get(i)
        ));
    }
    if kind == SourceKind::Standalone {
        for region in off_regions(input) {
            if !output.contains(region) {
                return Err(format!("a fmt: off region changed: {region:?}"));
            }
        }
    }
    Ok(())
}

/// A comment's lines without trailing space or the indentation (spaces
/// and tabs) they share; a doc comment's with a star gutter (each line
/// after the first that isn't blank starting with `*`) without the
/// gutter's indentation.
fn comment_lines(comment: &str) -> String {
    let lines: Vec<&str> = comment.split('\n').map(str::trim_end).collect();
    let indent = |l: &str| l.len() - l.trim_start_matches([' ', '\t']).len();
    let gutter = comment.starts_with("/**")
        && (lines.iter().skip(1)).all(|l| l.is_empty() || l[indent(l)..].starts_with('*'));
    let starred = |l: &str| gutter && l[indent(l)..].starts_with('*');
    let shared = (lines.iter().skip(1))
        .filter(|l| !l.is_empty() && !starred(l))
        .map(|l| indent(l))
        .min()
        .unwrap_or(0);
    (lines.iter().enumerate())
        .map(|(i, l)| match () {
            _ if i == 0 || l.is_empty() => *l,
            _ if starred(l) => &l[indent(l)..],
            _ => &l[shared..],
        })
        .collect::<Vec<_>>()
        .join("\n")
}

/// The text of each `// fmt: off` region's whole lines in a `.temper`
/// source, found apart from the formatter: from the line after a
/// `// fmt: off` on a line of its own, up to the line of a `// fmt: on` on
/// a line of its own or of the close bracket ending the brackets around
/// it, or else to the end of the last line with code in it, if any.
fn off_regions(source: &str) -> Vec<&str> {
    let line_start = |at: usize| source[..at].rfind('\n').map_or(0, |i| i + 1);
    let line_end = |at: usize| source[at..].find('\n').map_or(source.len(), |i| at + i);
    let own_line = |at: usize| {
        source[line_start(at)..at]
            .trim_matches([' ', '\t'])
            .is_empty()
    };
    let tokens: Vec<_> = (lex(source, SourceKind::Standalone).into_iter())
        .filter(|t| t.kind != TokenKind::Space && t.synthetic.is_none())
        .collect();
    let mut regions = Vec::new();
    let mut depth = 0usize;
    let mut off: Option<(usize, usize)> = None; // Its depth and first line.
    let mut last = None; // Where the last token in the region ends.
    for t in &tokens {
        let text = t.text(source);
        let at = t.start as usize;
        let marker = (t.kind == TokenKind::Comment && own_line(at)).then(|| text.trim_end());
        let closer = t.kind == TokenKind::Punctuation && matches!(&*text, ")" | "]" | "}");
        if let Some((d, from)) = off {
            if marker == Some("// fmt: on") || (closer && depth == d) {
                regions.push(&source[from..line_start(at).max(from)]);
                off = None;
            } else {
                last = Some(t.end as usize);
            }
        }
        if t.kind == TokenKind::Punctuation && text.ends_with(['(', '[', '{']) {
            depth += 1;
        } else if closer {
            depth = depth.saturating_sub(1);
        }
        if off.is_none() && marker == Some("// fmt: off") {
            let from = (line_end(at) + 1).min(source.len());
            (off, last) = (Some((depth, from)), None);
        }
    }
    if let (Some((_, from)), Some(last)) = (off, last) {
        regions.push(&source[from..line_end(last).max(from)]);
    }
    regions
}

/// Formats twice, as [`format_twice`], and checks that only space changed.
fn format_checked(text: &str, kind: SourceKind) -> Result<String, String> {
    let once = format_twice(text, kind)?;
    only_space_changed(text, &once, kind)?;
    Ok(once)
}

/// The test's own checks catch what formatting must never do.
#[test]
fn only_space_changed_catches_changes() {
    let ok = |a: &str, b: &str| only_space_changed(a, b, SourceKind::Standalone);
    assert_eq!(ok("let x=1;\n", "let x = 1;\n"), Ok(()));
    assert!(ok("let x = 1;\n", "let x = 2;\n").is_err());
    // Merged tokens.
    assert!(ok("a - -b;\n", "a --b;\n").is_err());
    // A comment's lines keep their indentation relative to each other.
    let comment = "/*\n  a\n    b\n*/\nx;\n";
    assert_eq!(ok(comment, "  /*\n    a\n      b\n  */\nx;\n"), Ok(()));
    assert!(ok(comment, "/*\n  a\n  b\n*/\nx;\n").is_err());
    // A region kept as written.
    let off = "// fmt: off\nlet  x=1;\n// fmt: on\nf( y );\n";
    assert_eq!(
        ok(off, "// fmt: off\nlet  x=1;\n// fmt: on\nf(y);\n"),
        Ok(())
    );
    assert!(ok(off, "// fmt: off\nlet x = 1;\n// fmt: on\nf(y);\n").is_err());
    let open = "f(\n  // fmt: off\n  a ,b,\n  c)\n";
    assert_eq!(off_regions(open), ["  a ,b,\n"]);
    assert_eq!(off_regions("// fmt: off\nx  ;  \n\n"), ["x  ;  "]);
    assert_eq!(off_regions("// fmt: off\n  \n// fmt: on\n"), ["  \n"]);
    assert!(off_regions("x;\n// fmt: off\n  \n").is_empty());
}

/// Formatting a file with CRLF line breaks writes CRLF where it adds or
/// moves a line break, and at the end.
#[test]
fn crlf_files_keep_their_line_breaks() {
    let standalone = [
        ("a=1;\r\nb=2;", "a = 1;\r\nb = 2;\r\n"),
        ("a=1;\r\nb=2;\r\n\r\n\r\n", "a = 1;\r\nb = 2;\r\n"),
        ("f(a, b,);\r\n", "f(\r\n  a,\r\n  b,\r\n);\r\n"),
        ("f(\r\n  a, b);\r\n", "f(\r\n  a, b\r\n);\r\n"),
        ("f(a,\r\n  b);\r\n", "f(a, b);\r\n"),
        (
            "// c  \r\n/* a  \r\n b */\r\nx;",
            "// c\r\n/* a\r\n b */\r\nx;\r\n",
        ),
        (
            "if (a) {\r\n\r\n\r\ng();\r\n}",
            "if (a) {\r\n\r\n  g();\r\n}\r\n",
        ),
        (" \r\n\r\n", ""),
    ];
    for (input, expected) in standalone {
        assert_eq!(
            format_checked(input, SourceKind::Standalone).as_deref(),
            Ok(expected),
            "{input:?}"
        );
    }
    let markdown = "Prose.\r\n\r\n```temper\r\n\r\n\r\nlet x=1;\r\nf(a,b,)\r\n\r\n\r\n```\r\n";
    assert_eq!(
        format_checked(markdown, SourceKind::Markdown).as_deref(),
        Ok("Prose.\r\n\r\n```temper\r\n\r\nlet x = 1;\r\nf(\r\n  a,\r\n  b,\r\n)\r\n\r\n```\r\n")
    );
}

/// A lone `\r`, a line break to some editors but not to Temper, is
/// refused, with a message saying where, rather than formatted as one
/// line (or edited where an editor counts other lines).
#[test]
fn lone_carriage_returns_are_refused() {
    for (text, kind, line) in [
        ("let a=1;\rlet b=2;\r", SourceKind::Standalone, 1),
        ("let a = 1;\r\nlet b = 2;\r", SourceKind::Standalone, 2),
        ("let s = \"a\rb\";\n", SourceKind::Standalone, 1),
        ("Prose\rmore.\n\n    let a=1;\n", SourceKind::Markdown, 1),
    ] {
        let why = format(text, kind).map_err(|e| e.to_string());
        let expected = format!("line {line}: a carriage return without a line feed");
        assert!(
            why.as_ref().is_err_and(|e| e.contains(&expected)),
            "{text:?}: {why:?}"
        );
    }
    // CRLF alone is fine.
    assert_eq!(
        format("let a=1;\r\n", SourceKind::Standalone).as_deref(),
        Ok("let a = 1;\r\n")
    );
}

/// Formatting time grows linearly with the code, on one long line and in
/// deep nesting too. Formatting four times as much should take about four
/// times as long, and quadratic work sixteen; the bound, eight, is twice
/// from each, so timing noise on a busy machine or CI runner doesn't fail
/// linear work, and quadratic work can't pass. Each size is timed at its
/// best of five runs, the two sizes taking turns so a busy spell slows
/// both. (Deep nesting over many lines is quadratic by nature, as each
/// line's indentation grows.)
#[test]
fn formatting_time_grows_linearly() {
    let items = |n: usize, item: fn(usize) -> String, between: &str| {
        (0..n).map(item).collect::<Vec<_>>().join(between)
    };
    let make = |what: &str, n: usize| match what {
        "a list on one line" => format!("let x = f({});\n", items(n, |i| format!("a{i}"), ", ")),
        "statements on one line" => items(n, |i| format!("let x{i} = {i};"), "") + "\n",
        "nested parentheses" => format!("let x = {}1{};\n", "(".repeat(n), ")".repeat(n)),
        "nested blocks" => format!("{}{}\n", "if (a) {".repeat(n), "}".repeat(n)),
        // Headers in headers: each function literal a default value in
        // the parameters of the one around it.
        "nested headers" => format!(
            "{}{}\n",
            items(n, |i| format!("let f{i}(a: Int = fn {{ "), ""),
            "}) {} ".repeat(n)
        ),
        _ => "// c\n".repeat(4 * n) + "x;\n",
    };
    let inputs = [
        "a list on one line",
        "statements on one line",
        "nested parentheses",
        "nested blocks",
        "nested headers",
        "comment lines",
    ];
    let (n, step, bound) = (1000, 4, 8.0);
    for what in inputs {
        let (small, large) = (make(what, n), make(what, step * n));
        let time = |text: &str| {
            let start = Instant::now();
            format(text, SourceKind::Standalone).unwrap();
            start.elapsed().as_secs_f64()
        };
        let (mut once, mut more) = (f64::INFINITY, f64::INFINITY);
        for _ in 0..5 {
            once = once.min(time(&small));
            more = more.min(time(&large));
        }
        let message = format!(
            "{what}: {n}: {:.1} ms, {}: {:.1} ms",
            once * 1e3,
            step * n,
            more * 1e3
        );
        println!("{message}");
        // The floor keeps a tiny first timing from making the bound tiny.
        assert!(more < bound * once.max(0.002), "{message}");
    }
}

/// Only `UPDATE_SNAPSHOTS=1` updates the goldens, not any value.
#[test]
fn snapshots_update_only_when_asked() {
    assert!(common::updating(Some("1")));
    for value in [None, Some("0"), Some(""), Some("false")] {
        assert!(!common::updating(value), "{value:?}");
    }
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
        match format_checked(&text, kind) {
            Ok(_) => formatted += 1,
            // Some test inputs are deliberately malformed.
            Err(_)
                if matches!(
                    format(&text, kind),
                    Err(FmtError::Unparseable(_) | FmtError::Unsupported(_))
                ) => {}
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
            // Lists' line breaks and trailing commas.
            "(\n",
            "[\n",
            ",\n",
            ",)",
            ",\n]",
            // Items over several lines, in any position.
            ", fn {\n",
            "\n})",
            "(\n  x\n)",
            "fn {\n  x();\n}, ",
            ", fn {\n  x();\n}",
            "[\n  a,\n], ",
            "g(\n  a\n), ",
            "}, ",
            "\n},",
            " +\n  ",
            "\"\"\"\n  \"s\n",
            // Breaks at operators and before words, lines starting with a
            // separator, blank lines, and comments that look like code.
            " &&\n",
            "\n  .",
            "\n    extends ",
            // Control statements' conditions, continued and broken after
            // `(`, with brackets opened on their continuation lines.
            "if (a ||\n  b) {\n}",
            "} else if (a &&\n",
            "while (a &&\n  (\n    b\n  )) {\n}",
            "for (a;\n b;\n c) {\n}",
            "when (x ||\n y) {\n}",
            " && (\n",
            "if (\n",
            "\n;",
            "\n, ",
            "\n\n\n",
            "{\n\n",
            "\n\n}",
            " // x {\n",
            " // =\n",
            // Interpolations, padded, over several lines, and nested.
            "\"a ${ b } c\"",
            "\"${\n  x\n}\"",
            "\"${ f({ a: 1 }) }\"",
            "\"${ \"${ x }\" }\"",
            "${ ",
            " }",
            // `// fmt: off` regions, and doc comments' framing.
            "\n// fmt: off\n",
            "\n  // fmt: on\n",
            "\n/**\n   * d\n *  e\n f\n  */\n",
            // Whitespace other than spaces, tabs leading comment lines,
            // and doc comments with and without a star gutter.
            "\u{3000}",
            "\u{a0}",
            "\u{2003}",
            "/*\n\t x\n */",
            "\n/**\n\t * a\n\t */\n",
            "\n/**\n  a\n    * b\n */\n",
            "\t\"",
            "\r",
        ])
        .copied()
        .collect();
    // How many inputs were formatted, and how many refused as not parsing.
    let (mut formatted, mut refused) = (0, 0);
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
                        if let Err(FmtError::Unparseable(_) | FmtError::Unsupported(_)) =
                            format(&text, kind)
                        {
                            refused += 1;
                            continue;
                        }
                        if let Err(e) = format_checked(&text, kind) {
                            panic!("{} as {kind:?}: {e}\ninput: {text:?}", context());
                        }
                        formatted += 1;
                    }
                }
            }
        }
    }
    println!("seed {seed}: {formatted} inputs formatted, {refused} refused as not parsing");
    assert!(
        formatted * 4 >= formatted + refused,
        "seed {seed}: only {formatted} of {} inputs parse",
        formatted + refused
    );
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
/// server will do on save (budget: tens of milliseconds each, in release),
/// and parsing them alone: formatting parses twice (the second time to
/// check the result), and the rest is the formatter's own pass.
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
        let unlisted = time(Options {
            lists: false,
            ..Options::default()
        });
        let ms = time(Options::default());
        let start = std::time::Instant::now();
        for _ in 0..rounds {
            parse(&text, kind);
        }
        let parsing = start.elapsed().as_secs_f64() * 1000.0 / rounds as f64;
        let name = path.strip_prefix(&root).unwrap().display();
        println!(
            "{name}: {} KiB, {} lines, {ms:.1} ms ({unlisted:.1} ms without list rules; \
             parsing once {parsing:.1} ms)",
            text.len() / 1024,
            text.lines().count()
        );
    }
}

/// Counts the lines `temper fmt` changes, by group of files, and those
/// each of its rules for line breaks changes: the list rules (with data
/// lists kept as written, and with them too), and indenting continuation
/// lines twice (against keeping their offset). Over the repository's
/// Temper files (core and std in lib/, tests in crates/, docs' examples)
/// and the libraries in `FMT_CENSUS_LIBS` (paths separated by `:`). A line
/// changed is one of the input's that the output doesn't have (as a
/// multiset). Run with
/// `cargo test --release --test fmt rules_census -- --ignored --nocapture`.
#[test]
#[ignore]
fn rules_census() {
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
    println!(
        "group: files formatted (of all), code lines; lines fmt changes; of its output's, \
         lines the list rules change (data lists kept / not), and continuation indent"
    );
    let all = Options::default();
    for (name, files) in &groups {
        let (mut done, mut lines) = (0, 0);
        let [mut total, mut lists, mut data, mut continuations] = [0; 4];
        for path in files {
            let text = fs::read_to_string(path).unwrap();
            let kind = if path.extension().is_some_and(|e| e == "md") {
                SourceKind::Markdown
            } else {
                SourceKind::Standalone
            };
            let parsed = parse(&text, kind);
            let run = |options: Options| format_with(&parsed, kind, options).unwrap();
            let Ok(out) = format_with(&parsed, kind, all) else {
                continue;
            };
            done += 1;
            lines += text.lines().count();
            total += changed(&text, &out);
            let no_lists = run(Options {
                lists: false,
                ..all
            });
            lists += changed(&no_lists, &out);
            let with_data = run(Options {
                keep_data: false,
                ..all
            });
            data += changed(&no_lists, &with_data);
            let kept = run(Options {
                continuations: false,
                ..all
            });
            continuations += changed(&kept, &out);
        }
        println!(
            "{name}: {done} files (of {}), {lines} lines; {total} changed; \
             lists {lists}/{data}, continuations {continuations}",
            files.len()
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
