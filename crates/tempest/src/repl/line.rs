//! Line editing for `temper repl` on a terminal, through rustyline's
//! helper traits, each answering from tempest's own lexer and from
//! `temper-analysis` over the session as it stands:
//!
//! - **Inputs over lines** ([`Validator`]): Enter runs the buffer once
//!   [`Chunker::completes`] says it's a whole input, by the rules piped
//!   lines follow; until then, it starts a new line in the same buffer,
//!   indented two spaces, and two more for each bracket open
//!   ([`Indent`]), so earlier lines stay editable.
//! - **Highlighting** ([`Highlighter`]) by the lexer's tokens, in the
//!   classes the playground and the editors' grammars use, and the
//!   bracket matching the one at the cursor.
//! - **Completion** ([`Completer`]) of names in scope, earlier inputs'
//!   among them, members after `.`, and the REPL functions' arguments:
//!   `describe`'s steps, `translate`'s languages, and `help`'s names.
//! - **A hint** ([`Hinter`]): the signature of the call being typed,
//!   muted, after the cursor, never inserted.

use super::color::{sgr, style};
use rustyline::completion::{Completer, Pair};
use rustyline::highlight::{CmdKind, Highlighter};
use rustyline::hint::{Hint, Hinter};
use rustyline::validate::{ValidationContext, ValidationResult, Validator};
use rustyline::{Cmd, ConditionalEventHandler, Context, Event, EventContext, Helper, RepeatCount};
use std::borrow::Cow;
use std::cell::{Cell, RefCell};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::rc::Rc;
use temper_analysis::{Analysis, KEYWORDS};
use temper_frontend::repl::{Chunker, ReplSession, Separator};
use temper_syntax::lex::{SourceKind, TokenKind, lex};

/// The helper rustyline asks as a line is edited.
pub struct Line {
    session: Rc<RefCell<ReplSession>>,
    separator: Separator,
    color: bool,
    /// What `translate` translates into.
    languages: Vec<String>,
    /// Whether the bracket at the cursor and its match are marked, which
    /// they aren't once the line is entered.
    match_brackets: Cell<bool>,
}

impl Line {
    pub fn new(
        session: Rc<RefCell<ReplSession>>,
        separator: Separator,
        color: bool,
        languages: Vec<String>,
    ) -> Line {
        Line {
            session,
            separator,
            color,
            languages,
            match_brackets: Cell::new(true),
        }
    }

    /// Asks the session's analysis, as it stands; nothing if that panics,
    /// a bug that shouldn't end the session.
    fn analyze<R: Default>(
        &self,
        f: impl FnOnce(&Analysis, temper_frontend::scope::ScopeId) -> R,
    ) -> R {
        let Ok(mut session) = self.session.try_borrow_mut() else {
            return R::default();
        };
        let asked = catch_unwind(AssertUnwindSafe(|| {
            session.view(|s, scope| f(&Analysis::of(s), scope))
        }));
        asked.unwrap_or_default()
    }

    /// What may go at byte `pos` of `line`: where the word being typed
    /// starts, and the candidates.
    pub fn completions(&self, line: &str, pos: usize) -> (usize, Vec<String>) {
        if let Some((start, words)) = self.argument_completions(line, pos) {
            return (start, words);
        }
        let (start, found) = self.analyze(|a, scope| a.pending_completions(scope, line, pos));
        (start, found.into_iter().map(|c| c.name).collect())
    }

    /// In a string that's a REPL function's argument: `describe`'s steps,
    /// `translate`'s languages, or `help`'s names.
    fn argument_completions(&self, line: &str, pos: usize) -> Option<(usize, Vec<String>)> {
        let languages = &self.languages;
        self.analyze(|a, scope| a.pending_argument_completions(scope, line, pos, languages))
    }

    /// The signature of the call being typed at the end of `line`.
    pub fn signature(&self, line: &str, pos: usize) -> Option<String> {
        if pos != line.len() {
            return None;
        }
        let help = self.analyze(|a, scope| a.pending_signature(scope, line, pos));
        help.map(|h| h.label)
    }

    /// `line` colored by its tokens, with the bracket at byte `pos` and
    /// its match marked if `brackets`.
    pub fn colored(&self, line: &str, pos: usize, brackets: bool) -> String {
        let tokens = lex(line, SourceKind::Standalone);
        let marked = match brackets {
            true => matching_brackets(line, &tokens, pos),
            false => None,
        };
        let mut out = String::with_capacity(line.len() * 2);
        // Whether each open brace is a `${`'s, to color its `}` alike.
        let mut braces: Vec<bool> = Vec::new();
        let mut after_at = false;
        let mut at = 0;
        for t in &tokens {
            let (start, end) = (t.start as usize, t.end as usize);
            if start < at || end <= start {
                continue;
            }
            out.push_str(&line[at..start]);
            at = end;
            let text = &line[start..end];
            let mut class = match t.kind {
                _ if t.error.is_some() => Some(style::INVALID),
                TokenKind::Error => Some(style::INVALID),
                TokenKind::Comment => Some(style::COMMENT),
                TokenKind::Number => Some(style::NUMBER),
                TokenKind::LeftDelimiter
                | TokenKind::RightDelimiter
                | TokenKind::QuotedString
                | TokenKind::Margin => Some(style::STRING),
                TokenKind::Word if after_at => Some(style::DECORATOR),
                TokenKind::Word if matches!(text, "true" | "false" | "null" | "void") => {
                    Some(style::ATOM)
                }
                TokenKind::Word if KEYWORDS.contains(&text) => Some(style::KEYWORD),
                TokenKind::Word if text.starts_with(|c: char| c.is_uppercase()) => {
                    Some(style::TYPE)
                }
                TokenKind::Punctuation => match text {
                    "${" => {
                        braces.push(true);
                        Some(style::ESCAPE)
                    }
                    "{" => {
                        braces.push(false);
                        None
                    }
                    "}" => match braces.pop() {
                        Some(true) => Some(style::ESCAPE),
                        _ => None,
                    },
                    "@" => Some(style::DECORATOR),
                    _ => None,
                },
                _ => None,
            };
            after_at = t.kind == TokenKind::Punctuation && text == "@";
            if marked.is_some_and(|(a, b)| start == a || start == b) {
                class = Some(style::BRACKET);
            }
            match class {
                Some(class) => out.push_str(&sgr(true, class, text)),
                None => out.push_str(text),
            }
        }
        out.push_str(&line[at.min(line.len())..]);
        out
    }

    /// Whether the buffer is a whole input.
    pub fn completes(&self, text: &str) -> bool {
        Chunker::completes(self.separator, text)
    }
}

/// The bracket at or just before byte `pos` and its match, by their
/// offsets, if both are there; strings' and comments' brackets aren't
/// brackets.
fn matching_brackets(
    line: &str,
    tokens: &[temper_syntax::lex::Token],
    pos: usize,
) -> Option<(usize, usize)> {
    let mut stack: Vec<usize> = Vec::new();
    let mut pairs: Vec<(usize, usize)> = Vec::new();
    for t in tokens {
        if t.kind != TokenKind::Punctuation || t.synthetic.is_some() || t.start == t.end {
            continue;
        }
        let start = t.start as usize;
        match &line[t.range()] {
            "(" | "[" | "{" | "${" => stack.push(start),
            ")" | "]" | "}" => {
                if let Some(open) = stack.pop() {
                    pairs.push((open, start));
                }
            }
            _ => {}
        }
    }
    let at = |p: usize| pairs.iter().copied().find(|&(a, b)| a == p || b == p);
    let open = |p: usize| {
        line.get(p..)
            .is_some_and(|t| t.starts_with(['(', '[', '{', '$']))
    };
    let close = |p: usize| {
        line.get(p..)
            .is_some_and(|t| t.starts_with([')', ']', '}']))
    };
    // On a bracket, or just past a closing one.
    if open(pos) || close(pos) {
        if let Some(pair) = at(pos) {
            return Some(pair);
        }
    }
    let back = line[..pos.min(line.len())].char_indices().next_back();
    match back {
        Some((p, _)) if close(p) => at(p),
        _ => None,
    }
}

/// A signature shown after the cursor, never inserted.
pub struct Signature(String);

impl Hint for Signature {
    fn display(&self) -> &str {
        &self.0
    }

    fn completion(&self) -> Option<&str> {
        None
    }
}

impl Completer for Line {
    type Candidate = Pair;

    fn complete(
        &self,
        line: &str,
        pos: usize,
        _: &Context<'_>,
    ) -> rustyline::Result<(usize, Vec<Pair>)> {
        let (start, words) = self.completions(line, pos);
        let pairs = (words.into_iter())
            .map(|w| Pair {
                display: w.clone(),
                replacement: w,
            })
            .collect();
        Ok((start, pairs))
    }
}

impl Hinter for Line {
    type Hint = Signature;

    fn hint(&self, line: &str, pos: usize, _: &Context<'_>) -> Option<Signature> {
        self.signature(line, pos)
            .map(|s| Signature(format!("  {s}")))
    }
}

impl Highlighter for Line {
    fn highlight<'l>(&self, line: &'l str, pos: usize) -> Cow<'l, str> {
        match self.color {
            true => Cow::Owned(self.colored(line, pos, self.match_brackets.get())),
            false => Cow::Borrowed(line),
        }
    }

    fn highlight_prompt<'b, 's: 'b, 'p: 'b>(&'s self, prompt: &'p str, _: bool) -> Cow<'b, str> {
        Cow::Owned(sgr(self.color, style::PROMPT, prompt))
    }

    fn highlight_hint<'h>(&self, hint: &'h str) -> Cow<'h, str> {
        Cow::Owned(sgr(self.color, style::MUTED, hint))
    }

    fn highlight_char(&self, _: &str, _: usize, kind: CmdKind) -> bool {
        // Again on every change and move, as a bracket's match may come or
        // go; plainly once it's entered.
        self.match_brackets.set(kind != CmdKind::ForcedRefresh);
        self.color
    }
}

impl Validator for Line {
    fn validate(&self, ctx: &mut ValidationContext) -> rustyline::Result<ValidationResult> {
        Ok(match self.completes(ctx.input()) {
            true => ValidationResult::Valid(None),
            false => ValidationResult::Incomplete,
        })
    }
}

impl Helper for Line {}

/// Enter at the end of an input that isn't whole starts a new line in
/// it, indented two spaces, as the prompt is wide, and two more for each
/// bracket open; elsewhere, Enter does what it does by default: runs a
/// whole input, or breaks the line.
pub struct Indent {
    pub separator: Separator,
}

impl Indent {
    /// What Enter inserts at the end of `text`, if `text` isn't whole.
    pub fn after(&self, text: &str) -> Option<String> {
        if Chunker::completes(self.separator, text) || text.trim().is_empty() {
            return None;
        }
        let depth = Chunker::open_brackets(text) + 1;
        Some(format!("\n{}", "  ".repeat(depth)))
    }
}

impl ConditionalEventHandler for Indent {
    fn handle(&self, _: &Event, _: RepeatCount, _: bool, ctx: &EventContext) -> Option<Cmd> {
        if ctx.pos() != ctx.line().len() {
            return None;
        }
        self.after(ctx.line()).map(|text| Cmd::Insert(1, text))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use temper_frontend::repl::{ReplEvent, ReplOptions};

    fn line(inputs: &[&str]) -> Line {
        let options = ReplOptions {
            tools: Some(Rc::new(temper_driver::repl::ReplTools::default())),
            ..ReplOptions::default()
        };
        let mut session = ReplSession::new(options, Box::new(|_: &ReplEvent| {}));
        for input in inputs {
            session.feed(input);
        }
        let languages = vec!["js".to_string(), "py".to_string()];
        Line::new(
            Rc::new(RefCell::new(session)),
            Separator::Newline,
            true,
            languages,
        )
    }

    fn names(line: &Line, text: &str) -> (usize, Vec<String>) {
        line.completions(text, text.len())
    }

    #[test]
    fn highlights_by_token() {
        let l = line(&[]);
        let k = |s: &str, t: &str| format!("\x1b[{s}m{t}\x1b[0m");
        assert_eq!(
            l.colored("let s = \"a${x}\" // c", 0, false),
            format!(
                "{} s = {}{}{}x{}{} {}",
                k(style::KEYWORD, "let"),
                k(style::STRING, "\""),
                k(style::STRING, "a"),
                k(style::ESCAPE, "${"),
                k(style::ESCAPE, "}"),
                k(style::STRING, "\""),
                k(style::COMMENT, "// c"),
            )
        );
        assert_eq!(
            l.colored("@foo Point(1.5, true)", 0, false),
            format!(
                "{}{} {}(1.5, {})",
                k(style::DECORATOR, "@"),
                k(style::DECORATOR, "foo"),
                k(style::TYPE, "Point"),
                k(style::ATOM, "true"),
            )
            .replace("1.5", &k(style::NUMBER, "1.5"))
        );
        // A multi-line string, and a `{` that isn't a `${`.
        let text = "let t = \"\"\"\n  \"m${ {a: 1} }\n";
        let colored = l.colored(text, 0, false);
        assert!(colored.contains(&k(style::ESCAPE, "${")));
        assert!(colored.contains(&format!(
            " {{a: {}}} {}",
            k(style::NUMBER, "1"),
            k(style::ESCAPE, "}")
        )));
        // Highlighting only adds escape codes, so the width is the same.
        let plain = |s: &str| {
            let mut out = String::new();
            let mut chars = s.chars();
            while let Some(c) = chars.next() {
                if c == '\x1b' {
                    chars.by_ref().find(|&c| c == 'm');
                } else {
                    out.push(c);
                }
            }
            out
        };
        for text in [
            "let é = \"😀\u{301}\" + 1",
            "f(\"unclosed",
            "/* open",
            "1 +* \u{1}",
        ] {
            assert_eq!(plain(&l.colored(text, 3, true)), text);
        }
    }

    #[test]
    fn marks_matching_brackets() {
        let l = line(&[]);
        let b = |t: &str| format!("\x1b[{}m{t}\x1b[0m", style::BRACKET);
        assert_eq!(
            l.colored("f(g(x))", 1, true),
            format!("f{}g(x){}", b("("), b(")"))
        );
        // Just past a closing bracket.
        assert_eq!(
            l.colored("g(x) + y", 4, true),
            format!("g{}x{} + y", b("("), b(")"))
        );
        assert_eq!(
            l.colored("f(g(x))", 7, true),
            format!("f{}g(x){}", b("("), b(")"))
        );
        // A bracket in a string isn't one.
        assert_eq!(
            l.colored("(\")\")", 0, true).matches("\x1b[1;7m").count(),
            2
        );
        assert_eq!(l.colored("f(g(x))", 2, true), "f(g(x))");
    }

    #[test]
    fn completes_names_members_and_arguments() {
        let l = line(&[
            "let greeting = \"hi\"",
            "class Point(public x: Int, public y: Int) { public static origin: Int = 0; }",
            "let p = new Point(1, 2)",
        ]);
        let (start, found) = names(&l, "gree");
        assert_eq!((start, found), (0, vec!["greeting".to_string()]));
        // Std's and builtins' names, and the REPL's functions.
        assert!(names(&l, "conso").1.contains(&"console".to_string()));
        assert!(names(&l, "desc").1.contains(&"describe".to_string()));
        // Names the input itself declares before.
        assert!(
            names(&l, "let total = 1; tot")
                .1
                .contains(&"total".to_string())
        );
        // Members after `.`.
        assert_eq!(names(&l, "p.").1, ["x", "y"]);
        assert_eq!(names(&l, "1 + p.y").0, 6);
        assert_eq!(names(&l, "Point.").1, ["origin"]);
        assert!(names(&l, "greeting.").1.contains(&"split".to_string()));
        assert!(names(&l, "\"text\".spl").1.contains(&"split".to_string()));
        // Not in strings or comments.
        assert_eq!(names(&l, "\"gree").1, Vec::<String>::new());
        assert_eq!(names(&l, "// gree").1, Vec::<String>::new());
        // The REPL functions' arguments.
        assert_eq!(
            names(&l, "describe(0, \"t"),
            (13, vec!["types".into(), "tmpl".into()])
        );
        assert_eq!(names(&l, "translate(2, \"").1, ["js", "py"]);
        assert_eq!(names(&l, "help(\"gree").1, ["greeting"]);
    }

    #[test]
    fn hints_the_signature_being_typed() {
        let l = line(&[
            "let greet(name: String, times: Int): String { name }",
            "class Point(public x: Int, public y: Int) { public dist(o: Point): Int { 0 } }",
            "let p = new Point(1, 2)",
        ]);
        let hint = |text: &str| l.signature(text, text.len());
        assert_eq!(
            hint("greet(\"a\", "),
            Some("greet(name: String, times: Int32): String".into())
        );
        assert_eq!(hint("new Point("), Some("Point(x: Int32, y: Int32)".into()));
        assert_eq!(hint("p.dist("), Some("dist(o: Point): Int32".into()));
        assert_eq!(
            hint("describe("),
            Some("describe(n: Int32, step?: String): Void".into())
        );
        assert_eq!(hint("greet(\"a\")"), None);
        assert_eq!(l.signature("greet(", 2), None);
    }

    #[test]
    fn enter_continues_inputs_that_arent_whole() {
        let l = line(&[]);
        assert!(l.completes("1 + 2"));
        assert!(!l.completes("f(1,"));
        assert!(!l.completes("/** docs */"));
        let indent = Indent {
            separator: Separator::Newline,
        };
        assert_eq!(indent.after("1 + 2"), None);
        assert_eq!(indent.after("f(1,"), Some("\n    ".into()));
        assert_eq!(indent.after("f([1,"), Some("\n      ".into()));
        assert_eq!(indent.after("let s = \"\"\""), Some("\n  ".into()));
        assert_eq!(indent.after(""), None);
    }

    #[test]
    fn cursor_moves_by_grapheme() {
        use rustyline::line_buffer::{ChangeListener, DeleteListener, Direction, LineBuffer};
        struct Quiet;
        impl DeleteListener for Quiet {
            fn delete(&mut self, _: usize, _: &str, _: Direction) {}
        }
        impl ChangeListener for Quiet {
            fn insert_char(&mut self, _: usize, _: char) {}
            fn insert_str(&mut self, _: usize, _: &str) {}
            fn replace(&mut self, _: usize, _: &str, _: &str) {}
        }
        // e and a combining acute, a flag (two code points), a wide CJK
        // character, and an emoji with a skin tone.
        let text = "e\u{301}\u{1f1eb}\u{1f1f7}\u{4e2d}\u{1f44d}\u{1f3fd}";
        let mut buffer = LineBuffer::with_capacity(64);
        buffer.update(text, text.len(), &mut Quiet);
        let mut stops = vec![buffer.pos()];
        while buffer.move_backward(1) {
            stops.push(buffer.pos());
        }
        assert_eq!(stops, [22, 14, 11, 3, 0]);
    }
}
