//! Doc strings: the documentation attached to a declaration, from a doc
//! comment, `/** … */`, or from prose in a `.temper.md` file, as Kotlin
//! Temper's `TurnEmbeddedCommentsIntoDocStrings` and
//! `pickCommentToAttach` attach them.
//!
//! The grammar makes each comment before a statement a `REM` call, and
//! the lexer makes each paragraph of a literate file's prose a synthetic
//! `/**? … ?*/` comment. [`collect`] gathers, for each statement, the run
//! of doc comments and paragraphs right before it, ignoring the prose
//! around them, as [`Library::doc_comments`](crate::library::Library);
//! [`pick`] chooses what documents the declaration the statement
//! becomes:
//!
//! 1. A doc comment, `/** … */`, wins: the first in the run.
//! 2. Otherwise, the first paragraph whose first word is the declared
//!    name, ignoring case and `` ` `` or `*` around it, as "`` `area` ``
//!    is …" or "**Square** holds …", with every paragraph after it up
//!    to the declaration, joined by blank lines. A paragraph that
//!    doesn't start with the name, as an introduction, documents
//!    nothing.
//!
//! A [`DocString`] keeps the text as Markdown, as written, for each
//! consumer to render: hover shows it as it is, and backends may turn it
//! into their own doc comments.

use crate::library::Library;
use temper_syntax::ast::{Builtin as SyntaxBuiltin, Value};
use temper_syntax::tree::{NodeId, NodeKind, Span};

/// A doc comment or paragraph before a statement, as its `REM` call has
/// it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Rem {
    /// Its text, without comment markers.
    pub text: String,
    /// Where it is, library-wide.
    pub span: Span,
    /// Whether it's a paragraph of a literate file's prose.
    pub paragraph: bool,
}

/// What documents a declaration, as Kotlin Temper's `@docString`
/// metadata.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DocString {
    /// Its first paragraph, up to the first blank line: a summary, for a
    /// list of members or a help line.
    pub brief: String,
    /// All of it, as Markdown.
    pub long: String,
    /// Where it is, library-wide: the comment, or the paragraphs from
    /// the first to the last; links in it are relative to its file.
    pub span: Span,
    /// Whether it's from a literate file's prose rather than a doc
    /// comment.
    pub prose: bool,
}

/// Each run of doc comments, `REM` calls that associate right, before a
/// statement in a block among `nodes`, by that statement.
pub(crate) fn collect(lib: &Library, nodes: std::ops::Range<u32>) -> Vec<(NodeId, Vec<Rem>)> {
    let mut out = Vec::new();
    for id in nodes.map(NodeId) {
        if lib.tree.kind(id) != NodeKind::Block {
            continue;
        }
        let mut run = Vec::new();
        for &statement in lib.tree.children(id) {
            match rem(lib, statement) {
                Some(Some(rem)) => run.push(rem),
                // Another comment ends the run, which documents nothing.
                Some(None) => run.clear(),
                None if run.is_empty() => {}
                None => out.push((statement, std::mem::take(&mut run))),
            }
        }
    }
    out
}

/// What a `REM` call has: `Some(Some(_))` for a doc comment or
/// paragraph, `Some(None)` for another comment, and `None` for anything
/// else.
fn rem(lib: &Library, statement: NodeId) -> Option<Option<Rem>> {
    if lib.tree.kind(statement) != NodeKind::Call {
        return None;
    }
    let children = lib.tree.children(statement);
    let (&callee, args) = children.split_first()?;
    if lib.value(callee) != Some(&Value::Builtin(SyntaxBuiltin::EmbeddedComment)) {
        return None;
    }
    let values = args.iter().map(|&a| lib.value(a)).collect::<Vec<_>>();
    Some(match values[..] {
        [
            Some(Value::String(text)),
            Some(Value::Bool(true)),
            Some(&Value::Bool(paragraph)),
        ] => Some(Rem {
            text: text.clone(),
            span: lib.tree.span(args[0]),
            paragraph,
        }),
        _ => None,
    })
}

/// The doc comments of a `@docComment(target, ["doc", …], paragraphs)`
/// call, which the grammar wraps around a parameter or argument they
/// precede, each spanning all of them.
pub(crate) fn wrapped(lib: &Library, call: NodeId) -> Vec<Rem> {
    let children = lib.tree.children(call);
    let (Some(&texts), Some(&paragraphs)) = (children.get(2), children.get(3)) else {
        return Vec::new();
    };
    let (Some(Value::Strings(texts)), Some(&Value::Bool(paragraph))) =
        (lib.value(texts), lib.value(paragraphs))
    else {
        return Vec::new();
    };
    let span = lib.tree.span(children[2]);
    (texts.iter())
        .map(|text| Rem {
            text: text.clone(),
            span,
            paragraph,
        })
        .collect()
}

/// What documents a declaration named `name`, of the run of doc comments
/// before it, by the rules in this module's docs.
pub fn pick(rems: &[Rem], name: &str) -> Option<DocString> {
    if let Some(comment) = rems.iter().find(|r| !r.paragraph) {
        return Some(doc_string(comment.text.clone(), comment.span, false));
    }
    let first = rems.iter().position(|r| starts_with_name(&r.text, name))?;
    let documenting = &rems[first..];
    let text = (documenting.iter().map(|r| r.text.as_str()))
        .collect::<Vec<_>>()
        .join("\n\n");
    let span = Span {
        start: documenting[0].span.start,
        end: documenting[documenting.len() - 1].span.end,
    };
    Some(doc_string(text, span, true))
}

fn doc_string(long: String, span: Span, prose: bool) -> DocString {
    DocString {
        brief: brief(&long).to_string(),
        long,
        span,
        prose,
    }
}

/// Whether a paragraph's first word, before any space, is `name`,
/// ignoring case and the `` ` `` and `*` Markdown puts around it, as
/// Kotlin's `remStartsWithName`. A paragraph of one word documents
/// nothing.
fn starts_with_name(text: &str, name: &str) -> bool {
    let Some(space) = text.find(char::is_whitespace).filter(|&i| i > 0) else {
        return false;
    };
    let word = text[..space].trim_matches(['`', '*']);
    word.to_lowercase() == name.to_lowercase()
}

/// Text up to its first blank line, as Kotlin's `docPartsList`.
fn brief(text: &str) -> &str {
    let mut at = 0;
    for line in text.split_inclusive('\n') {
        if at > 0 && line.trim_matches([' ', '\t', '\r', '\n']).is_empty() {
            return text[..at].trim_end_matches(['\r', '\n']);
        }
        at += line.len();
    }
    text
}

impl Library {
    /// Records a `@docComment` call's doc comments for `decl`, the
    /// declaration its target becomes.
    pub(crate) fn add_wrapped_docs(&mut self, call: NodeId, decl: NodeId) {
        let rems = wrapped(self, call);
        if !rems.is_empty() {
            self.doc_comments.insert(decl, rems);
        }
    }

    /// What documents a declaration, a `Let` or `Class`, by the rules in
    /// [`crate::docs`]: a doc comment, or prose in a `.temper.md` file.
    pub fn doc_string(&self, decl: NodeId) -> Option<DocString> {
        let rems = self.doc_comments.get(&decl)?;
        let name = match self.tree.kind(decl) {
            NodeKind::Let | NodeKind::Class => *self.tree.children(decl).first()?,
            _ => return None,
        };
        let name = self.names.get(self.name(name)?);
        pick(rems, name)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rem(text: &str, paragraph: bool) -> Rem {
        Rem {
            text: text.into(),
            span: Span { start: 0, end: 1 },
            paragraph,
        }
    }

    #[test]
    fn names_start_paragraphs() {
        assert!(starts_with_name("`area` is …", "area"));
        assert!(starts_with_name("**Square** holds", "Square"));
        assert!(starts_with_name("Area is", "area"));
        assert!(!starts_with_name("area", "area"));
        assert!(!starts_with_name("The area", "area"));
        assert!(!starts_with_name("area's side", "area"));
    }

    #[test]
    fn briefs_end_at_blank_lines() {
        assert_eq!(brief("One.\nTwo.\n\nThree."), "One.\nTwo.");
        assert_eq!(brief("One.\r\n \r\nTwo."), "One.");
        assert_eq!(brief("One."), "One.");
    }

    #[test]
    fn picks_as_kotlin() {
        let rems = [
            rem("Intro.", true),
            rem("`f` is a function.", true),
            rem("More.", true),
        ];
        let doc = pick(&rems, "f").unwrap();
        assert_eq!(doc.long, "`f` is a function.\n\nMore.");
        assert_eq!(doc.brief, "`f` is a function.");
        assert!(doc.prose);
        assert_eq!(pick(&rems, "g"), None);
        let with_comment = [rem("`f` is", true), rem("Doc.", false)];
        let doc = pick(&with_comment, "f").unwrap();
        assert_eq!((doc.long.as_str(), doc.prose), ("Doc.", false));
    }
}
