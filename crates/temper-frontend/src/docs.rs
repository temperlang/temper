//! Doc strings: the documentation attached to a declaration, from a doc
//! comment, `/** … */`, or from prose in a `.temper.md` file, much as
//! Kotlin Temper's `TurnEmbeddedCommentsIntoDocStrings` and
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
//! 2. Otherwise, the first top-level paragraph whose first word is the
//!    declared name, ignoring case and `` ` `` or `*` around it, as
//!    "`` `area` `` is …" or "**Square** holds …", with the Markdown
//!    after it up to the declaration, as written: paragraphs, lists,
//!    block quotes, code blocks other than Temper code (examples, as
//!    ```` ```temper inert ````), and link reference definitions, each
//!    dedented and joined by blank lines. HTML blocks are left out. A
//!    heading or thematic break ends a doc, so a paragraph before one
//!    documents nothing after it. A paragraph that doesn't start with
//!    the name, as an introduction, documents nothing. Kotlin took only
//!    paragraphs, at any depth, and went past headings.
//!
//! A [`DocString`] keeps the text as Markdown, as written, for each
//! consumer to render: hover shows it as it is, and backends may turn it
//! into their own doc comments.

use crate::library::Library;
use temper_syntax::ast::{Builtin as SyntaxBuiltin, Value};
use temper_syntax::literate::{self, Kind};
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
    /// Its first paragraph: a summary, for a list of members or a help
    /// line. For a doc comment, its text up to the first blank line.
    pub brief: String,
    /// All of it, as Markdown.
    pub long: String,
    /// Where it is, library-wide: the comment, or the prose from the
    /// first block to the last; links in it are relative to its file.
    pub span: Span,
    /// Whether it's from a literate file's prose rather than a doc
    /// comment.
    pub prose: bool,
}

/// Each run of doc comments, `REM` calls that associate right, before a
/// statement in a block among `nodes`, by that statement.
pub(crate) fn collect(lib: &Library, nodes: std::ops::Range<u32>) -> Vec<(NodeId, Vec<Rem>)> {
    let mut out = statements(lib, nodes);
    out.retain(|(_, run)| !run.is_empty());
    out
}

/// Each statement in a block among `nodes`, other than a `REM` call,
/// with the run of doc comments before it, maybe none.
fn statements(lib: &Library, nodes: std::ops::Range<u32>) -> Vec<(NodeId, Vec<Rem>)> {
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
                None => out.push((statement, std::mem::take(&mut run))),
            }
        }
    }
    out
}

/// Docs found by where a declaration's name is, in files that are only
/// parsed, not settled, as core's are, which hover reads by place
/// (`temper_analysis`'s `core_doc`): by the same rules as
/// [`Library::doc_string`].
pub struct DocsByPlace {
    /// Each statement's span, with the run of doc comments before it.
    statements: Vec<(Span, Vec<Rem>)>,
    /// Each file's text, with its library-wide start.
    files: Vec<(u32, String)>,
}

impl DocsByPlace {
    /// Parses `files`, by path and text, as one library's, so offsets
    /// are library-wide, as [`Library::add_file`] has them: each file's
    /// after the one before and a newline.
    pub fn parse(files: &[(&str, &str)]) -> DocsByPlace {
        let mut lib = Library::new();
        for &(path, text) in files {
            lib.add_file(path, text, crate::project::source_kind(path));
        }
        let all = statements(&lib, 0..lib.tree.len() as u32);
        let statements = (all.into_iter())
            .map(|(statement, run)| (lib.tree.span(statement), run))
            .collect();
        let files = (lib.files.iter())
            .map(|f| (f.base, f.parsed.source().to_string()))
            .collect();
        DocsByPlace { statements, files }
    }

    /// What documents the declaration named `name` whose name is at byte
    /// `at`: the innermost statement around it, by [`pick`].
    pub fn doc_string(&self, at: u32, name: &str) -> Option<DocString> {
        let (span, run) = (self.statements.iter())
            .filter(|(span, _)| span.start <= at && at < span.end)
            .min_by_key(|(span, _)| span.end - span.start)?;
        let &(base, ref text) = (self.files.iter())
            .rev()
            .find(|(base, _)| *base <= span.start)?;
        pick(
            run,
            name,
            Source {
                text,
                base,
                at: span.start,
            },
        )
    }
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

/// The file a declaration is in, for docs from its prose.
pub struct Source<'a> {
    /// The file's text.
    pub text: &'a str,
    /// Where the file starts, library-wide.
    pub base: u32,
    /// Where the declaration's statement starts, library-wide.
    pub at: u32,
}

/// What documents a declaration named `name`, of the run of doc comments
/// before it, by the rules in this module's docs.
pub fn pick(rems: &[Rem], name: &str, source: Source) -> Option<DocString> {
    if let Some(comment) = rems.iter().find(|r| !r.paragraph) {
        let long = comment.text.clone();
        return Some(DocString {
            brief: brief(&long).to_string(),
            long,
            span: comment.span,
            prose: false,
        });
    }
    if !rems.iter().any(|r| starts_with_name(&r.text, name)) {
        return None;
    }
    let Source { text, base, at } = source;
    let at = at.checked_sub(base)? as usize;
    // The top-level blocks before the declaration's code, back to a
    // section break or other code.
    let mut blocks = literate::top_blocks(text);
    blocks.retain(|b| b.end.end <= at);
    let section = (blocks.iter())
        .rposition(|b| b.kind == Kind::Break || b.is_temper_code())
        .map_or(0, |i| i + 1);
    let blocks = &blocks[section..];
    // The first paragraph of the run that names it among those blocks.
    let first = rems.iter().find_map(|r| {
        let start = r.span.start.checked_sub(base)? as usize;
        starts_with_name(&r.text, name).then_some(())?;
        (blocks.iter()).position(|b| b.kind == Kind::Paragraph && b.begin.start == start)
    })?;
    let doc = (blocks[first..].iter())
        .filter(|b| b.kind != Kind::Html)
        .collect::<Vec<_>>();
    let texts = (doc.iter())
        .map(|b| literate::prose_text(&text[b.begin.start..b.end.end]))
        .collect::<Vec<_>>();
    Some(DocString {
        brief: texts[0].clone(),
        long: texts.join("\n\n"),
        span: Span {
            start: base + doc[0].begin.start as u32,
            end: base + doc[doc.len() - 1].end.end as u32,
        },
        prose: true,
    })
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
        let at = self.tree.span(decl).start;
        let (file, _) = self.locate(at);
        let file = self.files.get(file.0 as usize)?;
        let source = Source {
            text: file.parsed.source(),
            base: file.base,
            at,
        };
        pick(rems, self.names.get(self.name(name)?), source)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The doc of the declaration named `name` in a `.temper.md` file,
    /// found at its first `let name` or `class name`.
    fn doc(text: &str, name: &str) -> Option<DocString> {
        let docs = DocsByPlace::parse(&[("a.temper.md", text)]);
        let at = ["let ", "class "]
            .iter()
            .find_map(|k| text.find(&format!("{k}{name}")).map(|i| i + k.len()))
            .unwrap_or_else(|| panic!("{name}"));
        docs.doc_string(at as u32, name)
    }

    fn long(text: &str, name: &str) -> Option<String> {
        doc(text, name).map(|d| d.long)
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
        // Only paragraphs: Kotlin's text, joined by blank lines.
        let text = "Intro.\n\n`f` is a function.\n\n\nMore.\n\n    let f(): Void {}\n";
        let doc = doc(text, "f").unwrap();
        assert_eq!(doc.long, "`f` is a function.\n\nMore.");
        assert_eq!(doc.brief, "`f` is a function.");
        assert!(doc.prose);
        assert_eq!(long(text.replace("`f` is", "It's").as_str(), "f"), None);
        let with_comment = "`f` is\nprose.\n\n    /** Doc. */\n    let f(): Void {}\n";
        let doc = super::tests::doc(with_comment, "f").unwrap();
        assert_eq!((doc.long.as_str(), doc.prose), ("Doc.", false));
    }

    #[test]
    fn examples_are_kept_as_written() {
        let text = "\
`f` is a function.

```temper inert
f(); // ✅
//!outputs \"x\"
```

More.

```temper
let f(): Void {}
```
";
        let doc = doc(text, "f").unwrap();
        assert_eq!(
            doc.long,
            "`f` is a function.\n\n```temper inert\nf(); // ✅\n//!outputs \"x\"\n```\n\nMore."
        );
        assert_eq!(doc.brief, "`f` is a function.");
    }

    #[test]
    fn briefs_end_at_the_naming_paragraph() {
        let text = "`f` is a function.\n```json\n{}\n```\n\n    let f(): Void {}\n";
        let doc = doc(text, "f").unwrap();
        assert_eq!(doc.brief, "`f` is a function.");
        assert_eq!(doc.long, "`f` is a function.\n\n```json\n{}\n```");
    }

    #[test]
    fn other_fences_are_docs_and_temper_code_is_code() {
        let text = "`f` is a function.\n\n```text\nf\n```\n\n```\nlet g = 1;\n```\n\n```temper\nlet f(): Void {}\n```\n";
        // Code between ends the doc: `g` takes the prose, which doesn't
        // name it, and `f` has none.
        assert_eq!(long(text, "f"), None);
        let text = text.replace("```\nlet g = 1;\n```\n\n", "");
        assert_eq!(
            long(&text, "f").as_deref(),
            Some("`f` is a function.\n\n```text\nf\n```")
        );
    }

    #[test]
    fn headings_and_breaks_end_docs() {
        for heading in ["## More", "More\n----", "---", "* * *"] {
            let text =
                format!("`f` is a function.\n\n{heading}\n\nMore.\n\n    let f(): Void {{}}\n");
            assert_eq!(long(&text, "f"), None, "{heading}");
            let text =
                format!("Intro.\n\n{heading}\n\n`f` is a function.\n\n    let f(): Void {{}}\n");
            assert_eq!(
                long(&text, "f").as_deref(),
                Some("`f` is a function."),
                "{heading}"
            );
        }
        // A later naming paragraph documents, past an earlier one.
        let text = "`f` is old.\n\n## New\n\n`f` is new.\n\n    let f(): Void {}\n";
        assert_eq!(long(text, "f").as_deref(), Some("`f` is new."));
    }

    #[test]
    fn html_is_left_out() {
        let text = "`f` is a function.\n\n<table>\n<tr><td>x</td></tr>\n</table>\n\nMore.\n\n    let f(): Void {}\n";
        assert_eq!(
            long(text, "f").as_deref(),
            Some("`f` is a function.\n\nMore.")
        );
    }

    #[test]
    fn link_definitions_in_range_come_along() {
        let text = "\
[far]: https://far.example

`f` is [near][] and [far].

[near]: https://near.example

    let f(): Void {}
";
        assert_eq!(
            long(text, "f").as_deref(),
            Some("`f` is [near][] and [far].\n\n[near]: https://near.example")
        );
    }

    #[test]
    fn lists_keep_markers_and_nesting() {
        let text = "\
`f` is one of:

- a
  1. b
- c

```temper
let f(): Void {}
```

- `g` is in a list, which names nothing.

```temper
let g(): Void {}
```
";
        assert_eq!(
            long(text, "f").as_deref(),
            Some("`f` is one of:\n\n- a\n  1. b\n- c")
        );
        assert_eq!(long(text, "g"), None);
    }

    #[test]
    fn quotes_keep_their_markers() {
        let text = "`f` is quoted:\n\n> one\n> two\n\n    let f(): Void {}\n";
        assert_eq!(
            long(text, "f").as_deref(),
            Some("`f` is quoted:\n\n> one\n> two")
        );
    }

    #[test]
    fn indented_and_crlf_prose_is_dedented() {
        let text =
            "  `f` is\r\n  indented.\r\n\r\n   - a\r\n     b\r\n\r\n    let f(): Void {}\r\n";
        assert_eq!(
            long(text, "f").as_deref(),
            Some("`f` is\nindented.\n\n- a\n  b")
        );
    }
}
