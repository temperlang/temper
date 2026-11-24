//! Grammar step tests over the cases in `tests/ast/*.cases`.
//!
//! The cases have the format described in `tests/common/mod.rs`, with a
//! `--- ast` line before the expected tree in the form of
//! `print::write_ast`: `(Call ...)`, `(Block ...)` and the like, names bare
//! (`^x` declared, `%x` builtin), values, then diagnostics.
//!
//! - `cases_build_as_expected` checks each case's tree. To accept new or
//!   changed output, run with `UPDATE_SNAPSHOTS=1` and review the diff.
//! - `mutated_inputs_keep_ast_properties` builds trees (the grammar step
//!   alone and with `module::build_module`) for randomly edited
//!   copies of these cases and the parser's, and checks properties that
//!   hold for any text. Set `AST_FUZZ_SEED` and `AST_FUZZ_ROUNDS` (edits per
//!   case) to vary it.
//!
//! `compat/check_ast.py` checks the same inputs against Kotlin Temper.

mod common;

use common::{Rng, case_files, check_cases, env_number, mutate};
use temper_syntax::ast::{Ast, build};
use temper_syntax::lex::SourceKind;
use temper_syntax::module::{Genre, build_module};
use temper_syntax::parse::{Parsed, parse};
use temper_syntax::print::{write_ast, write_ast_lines};
use temper_syntax::tree::{NodeKind, Span};

#[test]
fn cases_build_as_expected() {
    check_cases("tests/ast", "ast", |case| {
        let mut actual = String::new();
        write_ast(&build(&parse(&case.input, case.kind)), 80, &mut actual);
        actual
    });
}

/// Pieces that matter to the grammar, on top of the lexer's and parser's.
#[rustfmt::skip]
const AST_PIECES: &[&str] = &[
    "@", "@A ", "let ", "var ", "fn ", "class ", "if (x) ", "} else {", "(", ")", "{", "}", "[",
    "]", ",", ";", ";;", ";;;", ":", "=", "=>", "->", "...", "case ", "is ", "of ", "extends ",
    "supports ", "\\(", "\\{", "${", "\\u{", "/** doc */", "// c\n", "builtins.", "this", "super",
    "new ", "<", ">", "0x", "1e", "i64", "\"", "'", "/x/", "rgx\"", "try ", " catch (e) ",
    " catch ", "error case ",
];

#[test]
fn mutated_inputs_keep_ast_properties() {
    let seed = env_number("AST_FUZZ_SEED", 1);
    let rounds = env_number("AST_FUZZ_ROUNDS", 20);
    let mut rng = Rng(seed.max(1));
    let pieces: Vec<&str> = common::PIECES.iter().chain(AST_PIECES).copied().collect();
    let mut inputs = Vec::new();
    for (dir, separator) in [
        ("tests/ast", "ast"),
        ("tests/parse", "tree"),
        ("tests/module", "module"),
    ] {
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

type Build = fn(&Parsed) -> Ast;

fn check_all(text: &str, context: &str) {
    for kind in [SourceKind::Standalone, SourceKind::Markdown] {
        let parsed = parse(text, kind);
        let builds: [(&str, Build); 3] = [
            ("grammar step", build),
            ("library module", |p| build_module(p, Genre::Library)),
            ("documentation module", |p| {
                build_module(p, Genre::Documentation)
            }),
        ];
        for (what, build) in builds {
            let ast = build(&parsed);
            let fail = |problem: String| {
                panic!("{context} as {kind:?}, {what}: {problem}\ninput: {text:?}")
            };
            if let Err(problem) = check_properties(text, &parsed, &ast) {
                fail(problem);
            }
            let (mut once, mut again) = (String::new(), String::new());
            write_ast_lines(&ast, &mut once);
            write_ast_lines(&build(&parsed), &mut again);
            if once != again {
                fail("building again differs".to_string());
            }
        }
    }
}

/// - Every node is reachable once from the root or metadata, with a span in
///   bounds and an origin in the parse tree.
/// - Names and values are where their kinds say.
/// - Diagnostics are in bounds, and each error node with one refers to an
///   error diagnostic.
fn check_properties(text: &str, parsed: &Parsed, ast: &Ast) -> Result<(), String> {
    let len = text.len() as u32;
    let tree = ast.tree();
    let in_bounds = |span: Span| span.start <= span.end && span.end <= len;
    let mut seen = vec![false; tree.len()];
    let mut stack: Vec<_> = std::iter::once(ast.root()).chain(ast.metadata()).collect();
    while let Some(id) = stack.pop() {
        if std::mem::replace(&mut seen[id.0 as usize], true) {
            return Err(format!("node {id:?} reached twice"));
        }
        let span = tree.span(id);
        if !in_bounds(span) {
            return Err(format!("node {id:?} span {span:?} out of bounds"));
        }
        if ast.origin(id).0 as usize >= parsed.tree().len() {
            return Err(format!(
                "node {id:?} has origin {:?} outside the parse tree",
                ast.origin(id)
            ));
        }
        let kind = tree.kind(id);
        match kind {
            NodeKind::LeftName | NodeKind::RightName | NodeKind::BuiltinName => {
                ast.name(id).ok_or("name without a name")?;
            }
            NodeKind::Value => {
                ast.value(id).ok_or("value without a value")?;
            }
            NodeKind::Error => {
                let index = tree.data(id) as usize;
                if index != u32::MAX as usize && ast.diagnostics().get(index).is_none() {
                    return Err(format!("error node {id:?} without its diagnostic"));
                }
            }
            NodeKind::Op(_) | NodeKind::Token => return Err(format!("parse node kind {kind:?}")),
            _ => {}
        }
        if !tree.children(id).is_empty() && matches!(kind, NodeKind::Value | NodeKind::Error) {
            return Err(format!("{kind:?} with children"));
        }
        stack.extend_from_slice(tree.children(id));
    }
    for d in ast.diagnostics() {
        if !in_bounds(d.span) {
            return Err(format!("diagnostic {d:?} out of bounds"));
        }
    }
    Ok(())
}
