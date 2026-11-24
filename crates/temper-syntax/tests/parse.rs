//! Parser tests over the cases in `tests/parse/*.cases`.
//!
//! The cases have the format described in `tests/common/mod.rs`, with a
//! `--- tree` line before the expected tree in the form of
//! `print::write_tree`: nested lists `(Operator operand ...)`, `[a b]` for
//! runs of value tokens, then errors and comments.
//!
//! - `cases_parse_as_expected` checks each case's tree. To accept new or
//!   changed output, run with `UPDATE_SNAPSHOTS=1` and review the diff.
//! - `mutated_inputs_keep_parser_properties` parses randomly edited copies
//!   of the parser and lexer cases and checks properties that hold for any
//!   text. Set `PARSE_FUZZ_SEED` and `PARSE_FUZZ_ROUNDS` (edits per case) to
//!   vary it.
//!
//! `compat/check_parse.py` checks the same inputs against Kotlin Temper, and
//! `compat/fuzz_parse.py` compares edited copies against it.

mod common;

use common::{Rng, case_files, check_cases, env_number, mutate};
use temper_syntax::balance::BracketErrorKind;
use temper_syntax::lex::{SourceKind, TokenKind, lex};
use temper_syntax::operator::Operator;
use temper_syntax::parse::{Parsed, parse};
use temper_syntax::print::{write_tree, write_tree_lines};
use temper_syntax::tree::Span;

#[test]
fn cases_parse_as_expected() {
    check_cases("tests/parse", "tree", |case| {
        let mut actual = String::new();
        write_tree(&parse(&case.input, case.kind), 80, &mut actual);
        actual
    });
}

/// Pieces that matter to the parser, on top of the lexer's. Keep in sync
/// with `PARSE_PIECES` in `compat/fuzz_parse.py`.
#[rustfmt::skip]
const PARSE_PIECES: &[&str] = &[
    "\n{", "}\n", "} else {", ";", ",", ":", "=", "@", "var ", "const ", "public ", "let ",
    "extends ", "case ", "->", "=>", "new ", "++", "-", "!", "?", "<", ">", "</", "${", "\\{",
    "\\(", "mixin(", "of ", "in ", "given ", "return ", ".", "?.", "...", "\n  :", "\n  \"",
    "\n  ~", "\"\"\"\n", "try ", " catch ", "}\ncatch (e) {",
];

#[test]
fn mutated_inputs_keep_parser_properties() {
    let seed = env_number("PARSE_FUZZ_SEED", 1);
    let rounds = env_number("PARSE_FUZZ_ROUNDS", 20);
    let mut rng = Rng(seed.max(1));
    let pieces: Vec<&str> = common::PIECES.iter().chain(PARSE_PIECES).copied().collect();
    let mut inputs = Vec::new();
    for dir in ["tests/parse", "tests"] {
        let separator = if dir == "tests" { "tokens" } else { "tree" };
        for (path, cases) in case_files(dir, separator) {
            for case in cases {
                inputs.push((
                    format!("{} case {:?}", path.display(), case.name),
                    case.input,
                ));
            }
        }
    }
    for (context, input) in inputs {
        check_all(&input, &context);
        for round in 0..rounds {
            let text = mutate(&input, &pieces, &mut rng);
            check_all(&text, &format!("{context}, seed {seed} round {round}"));
        }
    }
}

/// Checks both source kinds, since any text is valid input to either.
fn check_all(text: &str, context: &str) {
    for kind in [SourceKind::Standalone, SourceKind::Markdown] {
        let parsed = parse(text, kind);
        let fail = |problem: String| panic!("{context} as {kind:?}: {problem}\ninput: {text:?}");
        if let Err(problem) = check_properties(text, kind, &parsed) {
            fail(problem);
        }
        let (mut once, mut again) = (String::new(), String::new());
        write_tree_lines(&parsed, &mut once);
        write_tree_lines(&parse(text, kind), &mut again);
        if once != again {
            fail("parsing again differs".to_string());
        }
    }
}

/// - Spans are in bounds, and each node's span covers its children's.
/// - Token leaves are in source order, without overlapping.
/// - Every source token that isn't ignorable is in the tree, inside a
///   string, whose space and empty holes may be dropped, or dropped as an
///   unmatched close bracket.
/// - Errors are in bounds.
fn check_properties(text: &str, kind: SourceKind, parsed: &Parsed) -> Result<(), String> {
    let len = text.len() as u32;
    let tree = parsed.tree();
    let in_bounds = |span: Span| span.start <= span.end && span.end <= len;
    let mut leaves = Vec::new();
    let mut strings = Vec::new();
    let mut stack = vec![parsed.root()];
    while let Some(id) = stack.pop() {
        let span = tree.span(id);
        if !in_bounds(span) {
            return Err(format!("node {id:?} span {span:?} out of bounds"));
        }
        match tree.op(id) {
            None => leaves.push(id),
            Some(op) => {
                if op == Operator::QuotedGroup {
                    strings.push(span);
                }
                for &child in tree.children(id).iter().rev() {
                    let inner = tree.span(child);
                    let covered = span.start <= inner.start && inner.end <= span.end;
                    if !covered {
                        return Err(format!("{op:?} {span:?} doesn't cover child at {inner:?}"));
                    }
                    stack.push(child);
                }
            }
        }
    }
    let mut last: Option<Span> = None;
    for &leaf in &leaves {
        let span = tree.span(leaf);
        if let Some(last) = last {
            if span.start < last.end || (span.start == last.start && span.end < last.end) {
                return Err(format!("token at {span:?} out of order after {last:?}"));
            }
        }
        last = Some(span);
    }
    let leaf_spans: Vec<Span> = leaves.iter().map(|&l| tree.span(l)).collect();
    for token in lex(text, kind) {
        if token.kind.is_ignorable() || token.synthetic.is_some() {
            continue;
        }
        let (start, end) = (token.start, token.end);
        let inside = |s: &Span| s.start <= start && end <= s.end;
        // Bracket recovery drops unmatched close brackets, reporting them.
        let dropped = (parsed.bracket_errors().iter())
            .any(|e| e.kind == BracketErrorKind::Unmatched && e.span == Span { start, end });
        if !leaf_spans.iter().any(inside) && !strings.iter().any(inside) && !dropped {
            let what = &text[token.range()];
            return Err(format!(
                "{:?} token {what:?} at {start}..{end} not in the tree",
                token.kind
            ));
        }
    }
    for error in parsed.errors() {
        if !in_bounds(error.span) {
            return Err(format!("error {error:?} out of bounds"));
        }
    }
    // Every token node refers to a token, and words have names.
    for &leaf in &leaves {
        let token = parsed.token(leaf).ok_or("leaf without a token")?;
        let text = parsed.token_text(token);
        let name = parsed.name(leaf).map(|n| parsed.names().get(n));
        if token.kind == TokenKind::Word && name != Some(text) {
            return Err(format!("word {text:?} has name {name:?}"));
        }
    }
    Ok(())
}

/// Inputs on which Kotlin Temper's parser fails (kotlin-differences.md,
/// "Kotlin bugs found"): nesting that overflows its stack, at about 300
/// nested calls or a 1000-term sum, and nesting that takes it exponential
/// time, about 14 s for 22 nested `(`. Each parses here, run in a thread
/// with a test's usual stack, quickly: the nested ones would take hours
/// at Kotlin's rate.
#[test]
fn deep_and_nested_inputs_parse() {
    let calls = format!("{}x{}", "f(".repeat(400), ")".repeat(400));
    let sum = vec!["a"; 1000].join("+");
    let parens = format!("{}x{}", "(".repeat(40), ")".repeat(40));
    let indices = format!("{}0{}", "a[".repeat(40), "]".repeat(40));
    let generics = format!("let x: {}T{};", "f<".repeat(40), ">".repeat(40));
    let start = std::time::Instant::now();
    for text in [&calls, &sum, &parens, &indices, &generics] {
        let parsed = std::thread::spawn({
            let text = text.clone();
            move || parse(&text, SourceKind::Standalone).errors().len()
        })
        .join();
        assert!(parsed.is_ok(), "parsing panicked: {}", &text[..20]);
        check_all(text, "deep input");
    }
    let took = start.elapsed();
    assert!(took.as_secs() < 20, "took {took:?}");
}
