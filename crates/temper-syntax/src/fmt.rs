//! `temper fmt`: lays out Temper source in one canonical way.
//!
//! The formatter keeps every token and every line break as written, and
//! rewrites only the space between tokens: each line's indentation, the
//! space between tokens on a line, trailing space and blank lines. Line
//! breaks matter in Temper (a `}` ending a line or a `{` starting one can
//! end a statement), and keeping them makes the result predictable;
//! breaking long lines is left for later (notes/fmt.md).
//!
//! The rules are the style the compiler's libraries and Temper's own
//! libraries already use, measured by `compat/fmt_census.py`:
//!
//! - **Indentation:** two spaces per bracket level, counted from the open
//!   bracket's line if that line started at the bracket's depth, or else
//!   from the line that started the statement there: `foo(a, fn {`,
//!   `.map { x =>` on a continuation line, and the `{` closing a `for (`
//!   header over several lines each indent their contents one level. A
//!   line starting with a close bracket lines up with that line. A line
//!   continuing a statement (the one before didn't end with `;`, `{`,
//!   `}`, `,` or `=>`, or `,` inside `( )` and `[ ]`; or it starts with
//!   `.` or `?.`) keeps its offset from the statement's first line, as
//!   there's no settled style for those yet. Lines after a decorator line
//!   like `@connected` start statements.
//! - **Spacing on a line:** one space around infix operators (not `.`,
//!   `?.`, `..`, `::`, `<`/`>` of type arguments or label colons), after
//!   `,` and `;` and type colons, around `{ }` contents, and between a
//!   keyword like `if` or `fn` and `(`; none before `,`, `;` and type
//!   colons, inside `( )` and `[ ]`, between a called name and `(`, or
//!   after prefix operators and `@`. Runs of spaces become one. Space next
//!   to comments, inside strings, and before `<` (which decides whether
//!   it may be an angle bracket) is left as written.
//! - **Lines:** no trailing space, at most one blank line in a row, none
//!   after a line ending with `{` or before a line starting with a close
//!   bracket, none at the start of a file, and one line break at its end.
//!
//! Multi-line block comments and the lines of multi-quoted strings move
//! with the line they start on; their text is otherwise kept.
//!
//! In a literate `.temper.md` file only Temper code blocks change, each
//! indented from its block's own margin (the fence's indentation, or four
//! columns past its list items' for indented code). Blocks continue each
//! other's brackets. Lines indented with tabs keep their indentation.
//!
//! **Safety:** code that doesn't parse is left alone, with an error. After
//! formatting, the result is parsed again and must have the same tokens,
//! comments (apart from indentation), parse tree and, in markdown, prose;
//! if not, the formatter refuses rather than change meaning. Formatting is
//! idempotent: formatted code formats to itself (tested over every Temper
//! file in the repository).

use crate::lex::{SourceKind, Synthetic, Token, TokenKind};
use crate::literate::{self, Kind};
use crate::operator::{OpType, Operator};
use crate::parse::{Parsed, parse};
use crate::tree::{NodeId, NodeKind};
use std::collections::HashMap;
use std::fmt;

/// Spaces per indentation level.
const UNIT: i32 = 2;

/// Why a source was left as written.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum FmtError {
    /// The source has lexing or parsing errors; the text says the first,
    /// with its line.
    Unparseable(String),
    /// Formatting would have changed the code's tokens, tree, comments or
    /// prose: a bug in the formatter.
    Unsafe(String),
}

impl fmt::Display for FmtError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            FmtError::Unparseable(why) => write!(f, "doesn't parse ({why}); left as written"),
            FmtError::Unsafe(why) => write!(
                f,
                "formatting would change the code ({why}); left as written (a bug in temper fmt)"
            ),
        }
    }
}

/// Formats `source`, returning the formatted text, or why it's left alone.
pub fn format(source: &str, kind: SourceKind) -> Result<String, FmtError> {
    let parsed = parse(source, kind);
    if let Some(why) = first_error(&parsed) {
        return Err(FmtError::Unparseable(why));
    }
    let ranges = code_ranges(source, kind);
    let text = Formatter::new(&parsed, &ranges, kind).run();
    check_same(&parsed, &text, kind)?;
    Ok(text)
}

/// The first lexing or parsing error, as `line N: what`.
fn first_error(parsed: &Parsed) -> Option<String> {
    let source = parsed.source();
    let line = |offset: u32| source[..offset as usize].matches('\n').count() + 1;
    let mut errors: Vec<(u32, String)> = Vec::new();
    if let Some(t) = parsed.lexed().iter().find(|t| t.error.is_some()) {
        let what = words(&format!("{:?}", t.error.unwrap()));
        errors.push((t.start, what));
    }
    let unclosed = |t: &&Token| {
        let text = &source[t.range()];
        t.kind == TokenKind::Comment
            && t.synthetic.is_none()
            && text.starts_with("/*")
            && (text.len() < 4 || !text.ends_with("*/"))
    };
    if let Some(t) = parsed.lexed().iter().find(unclosed) {
        errors.push((t.start, "a block comment is never closed".to_string()));
    }
    let inserted = |t: &&Token| {
        matches!(
            t.synthetic,
            Some(
                Synthetic::DoubleQuote
                    | Synthetic::SingleQuote
                    | Synthetic::Backtick
                    | Synthetic::Slash
                    | Synthetic::RightCurly
            )
        )
    };
    if let Some(t) = parsed.lexed().iter().find(inserted) {
        let what = if t.synthetic == Some(Synthetic::RightCurly) {
            "a `${` is never closed"
        } else {
            "a string is never closed"
        };
        errors.push((t.start, what.to_string()));
    }
    if let Some(e) = parsed.bracket_errors().first() {
        use crate::balance::BracketErrorKind::*;
        let what = match &e.kind {
            Unclosed { open, line, .. } => format!("`{open}` from line {line} is never closed"),
            Unmatched => "a close bracket matches no open bracket".to_string(),
            UnclosedAngle(_) => "`<` is never closed".to_string(),
        };
        errors.push((e.span.start, what));
    }
    if let Some(e) = parsed.errors().first() {
        errors.push((e.span.start, e.message()));
    }
    errors.sort();
    errors
        .into_iter()
        .next()
        .map(|(at, what)| format!("line {}: {what}", line(at)))
}

/// `UnmatchedBracket` as `unmatched bracket`.
fn words(camel: &str) -> String {
    let mut out = String::new();
    for c in camel.chars() {
        if c.is_uppercase() && !out.is_empty() {
            out.push(' ');
        }
        out.extend(c.to_lowercase());
    }
    out
}

/// A run of source that's Temper code, with the margin its lines are
/// indented from.
#[derive(Clone, Copy, Debug)]
struct CodeRange {
    start: u32,
    end: u32,
    margin: i32,
}

/// Where the code is: all of a `.temper` file, or a `.temper.md` file's
/// Temper code blocks.
fn code_ranges(source: &str, kind: SourceKind) -> Vec<CodeRange> {
    if kind == SourceKind::Standalone {
        return vec![CodeRange {
            start: 0,
            end: source.len() as u32,
            margin: 0,
        }];
    }
    let mut ranges = Vec::new();
    for block in literate::find_blocks(source) {
        if !block.is_temper_code() || !matches!(block.kind, Kind::Code(_)) {
            continue;
        }
        // A fenced block's begin is its fence line; an indented block's,
        // the prefix of its containers (list items), maybe empty.
        let fenced = source[block.begin.clone()]
            .trim_start()
            .starts_with(['`', '~']);
        let (start, margin) = if fenced {
            // Fenced: the code starts on the line after the fence, and is
            // indented as the fence is.
            let fence = &source[block.begin.clone()];
            let margin = fence.len() - fence.trim_start_matches(' ').len();
            let next = source[block.begin.end..]
                .find('\n')
                .map_or(source.len(), |i| block.begin.end + i + 1);
            (next, margin)
        } else {
            // Indented: four columns past its containers' (list items').
            (block.begin.start, block.begin.len() + 4)
        };
        ranges.push(CodeRange {
            start: start as u32,
            end: block.end.start as u32,
            margin: margin as i32,
        });
    }
    ranges
}

/// An operator token's place in its node.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Role {
    /// Between two operands, like `+` in `a + b`.
    Infix,
    Prefix,
    Postfix,
    Open,
    Close,
    /// `,` or `;`.
    Separator,
}

/// Each operator token's role and operator, by its start in the source.
fn roles(parsed: &Parsed) -> HashMap<u32, (Role, Operator)> {
    let tree = parsed.tree();
    let mut roles = HashMap::new();
    let mut stack = vec![parsed.root()];
    while let Some(id) = stack.pop() {
        let NodeKind::Op(op) = tree.kind(id) else {
            continue;
        };
        let children = tree.children(id);
        let is_node = |c: &NodeId| tree.kind(*c) != NodeKind::Token;
        // Whether a token has operands before and after it.
        let first = children.iter().position(is_node).unwrap_or(usize::MAX);
        let last = children.iter().rposition(is_node).unwrap_or(0);
        for (i, &child) in children.iter().enumerate() {
            if is_node(&child) {
                stack.push(child);
                continue;
            }
            if op == Operator::Leaf {
                continue;
            }
            let Some(token) = parsed.token(child) else {
                continue;
            };
            if token.synthetic {
                continue;
            }
            let text = parsed.token_text(token);
            let (before, after) = (first < i, i < last);
            let role = if text.ends_with(['(', '[', '{']) || (text == "<" && op == Operator::Angle)
            {
                Role::Open
            } else if matches!(text, ")" | "]" | "}") || (text == ">" && op == Operator::Angle) {
                Role::Close
            } else if matches!(
                op,
                Operator::Comma | Operator::Semi | Operator::SemiSemi | Operator::SemiSemiSemi
            ) {
                Role::Separator
            } else if before && after {
                Role::Infix
            } else if after {
                Role::Prefix
            } else {
                Role::Postfix
            };
            roles.insert(token.start, (role, op));
        }
    }
    roles
}

/// The tokens to lay out (not space or synthetic tokens), and for each,
/// whether the space before it is code's, which the formatter may change,
/// or a string's (including a `\u{...}` escape's), which it keeps.
fn code_gaps(source: &str, lexed: &[Token]) -> (Vec<Token>, Vec<bool>) {
    #[derive(PartialEq)]
    enum Frame {
        Code,
        String,
        Escape,
    }
    let mut frames = vec![Frame::Code];
    let (mut tokens, mut gaps) = (Vec::new(), Vec::new());
    for &t in lexed.iter().filter(|t| t.kind != TokenKind::Space) {
        if t.synthetic.is_none() {
            tokens.push(t);
            gaps.push(frames.last() == Some(&Frame::Code));
        }
        let text = t.text(source);
        match t.kind {
            TokenKind::LeftDelimiter => frames.push(Frame::String),
            TokenKind::RightDelimiter => {
                while let Some(frame) = frames.pop() {
                    if frame == Frame::String || frames.is_empty() {
                        break;
                    }
                }
                if frames.is_empty() {
                    frames.push(Frame::Code);
                }
            }
            // In a string, only holes and escapes open; code in a
            // multi-quoted string's `:` lines counts as the string's.
            TokenKind::Punctuation if text == "\\u{" => frames.push(Frame::Escape),
            TokenKind::Punctuation if text == "${" => frames.push(Frame::Code),
            _ if frames.last() == Some(&Frame::String) => {}
            TokenKind::Punctuation if text.ends_with(['(', '[', '{']) => frames.push(Frame::Code),
            TokenKind::Punctuation if matches!(&*text, ")" | "]" | "}") => {
                if frames.len() > 1 {
                    frames.pop();
                }
            }
            _ => {}
        }
    }
    (tokens, gaps)
}

/// A line's indentation as written and as formatted, relative to its
/// code block's margin.
#[derive(Clone, Copy, Debug, Default)]
struct Indent {
    actual: i32,
    formatted: i32,
}

/// An open bracket: its start, and the line its contents indent from.
#[derive(Clone, Copy, Debug)]
struct Open {
    start: u32,
    base: Indent,
    round: bool,
}

struct Formatter<'a> {
    source: &'a str,
    /// The source's tokens but space and synthetic ones, and for each,
    /// whether the space before it is in code rather than in a string.
    tokens: Vec<Token>,
    code_gaps: Vec<bool>,
    /// Whether a string's closer was inserted at the end.
    open_at_end: bool,
    roles: HashMap<u32, (Role, Operator)>,
    ranges: &'a [CodeRange],
    standalone: bool,
    out: String,
    stack: Vec<Open>,
    /// The line that started the statement at each bracket depth, if any.
    statements: Vec<Option<Indent>>,
    /// The last code token so far.
    previous: Option<Token>,
    line: Indent,
    /// Where the current line's first token starts.
    line_first: u32,
    /// Whether the current line has code yet, and started with `@`, and
    /// the bracket depth at its start.
    line_has_code: bool,
    line_is_decorator: bool,
    line_depth: usize,
    /// Whether the last line with code was a decorator line.
    after_decorator: bool,
    /// How far the line that opened the last multi-quoted string moved,
    /// which its content lines move too.
    string_shift: i32,
}

impl<'a> Formatter<'a> {
    fn new(parsed: &'a Parsed, ranges: &'a [CodeRange], kind: SourceKind) -> Self {
        let (tokens, code_gaps) = code_gaps(parsed.source(), parsed.lexed());
        Formatter {
            source: parsed.source(),
            tokens,
            code_gaps,
            open_at_end: parsed.lexed().iter().any(|t| {
                t.kind == TokenKind::RightDelimiter
                    && t.synthetic.is_some()
                    && t.start as usize == parsed.source().len()
            }),
            roles: roles(parsed),
            ranges,
            standalone: kind == SourceKind::Standalone,
            out: String::with_capacity(parsed.source().len() + 64),
            stack: Vec::new(),
            statements: vec![None],
            previous: None,
            line: Indent::default(),
            line_first: u32::MAX,
            line_has_code: false,
            line_is_decorator: false,
            line_depth: 0,
            after_decorator: false,
            string_shift: 0,
        }
    }

    fn run(mut self) -> String {
        let source = self.source;
        if self.tokens.is_empty() {
            return source.to_string();
        }
        let tokens = std::mem::take(&mut self.tokens);
        let mut end = 0;
        for (i, &token) in tokens.iter().enumerate() {
            let previous = i.checked_sub(1).map(|p| tokens[p]);
            if self.code_gaps[i] || token.kind == TokenKind::Margin {
                self.gap(previous, end, token);
            } else {
                self.out
                    .push_str(&source[end as usize..token.start as usize]);
            }
            self.token(token);
            end = token.end;
        }
        let rest = &source[end as usize..];
        // A line break after a string left open at the end would be its.
        let in_string = self.open_at_end
            || tokens.last().is_some_and(|t| {
                use TokenKind::*;
                matches!(
                    t.kind,
                    QuotedString | Margin | LeftDelimiter | RightDelimiter
                )
            });
        if self.standalone && !in_string {
            // One line break at the end.
            let crlf = rest.contains("\r\n");
            self.out.push_str(if crlf { "\r\n" } else { "\n" });
        } else {
            self.out.push_str(rest);
        }
        self.out
    }

    fn range_of(&self, offset: u32) -> Option<CodeRange> {
        let i = self.ranges.partition_point(|r| r.end <= offset);
        self.ranges
            .get(i)
            .filter(|r| r.start <= offset && offset < r.end)
            .copied()
    }

    fn is_code(&self, token: Token) -> bool {
        self.range_of(token.start).is_some()
    }

    /// Writes the space between `previous` (ending at `start`) and `next`.
    fn gap(&mut self, previous: Option<Token>, start: u32, next: Token) {
        let source = self.source;
        let gap = &source[start as usize..next.start as usize];
        let Some(range) = self.range_of(next.start) else {
            // Code ending, maybe before prose: drop the last line's
            // trailing space.
            let code_before = previous.is_some_and(|p| self.is_code(p));
            let trailing = gap.len() - gap.trim_start_matches([' ', '\t']).len();
            let ends_line = gap[trailing..].starts_with(['\n', '\r']);
            let skip = if code_before && ends_line {
                trailing
            } else {
                0
            };
            self.out.push_str(&gap[skip..]);
            return;
        };
        let both_code =
            previous.is_none_or(|p| self.range_of(p.start).is_some()) && start >= range.start;
        let line_start = source[..next.start as usize]
            .rfind('\n')
            .map_or(0, |i| i + 1) as u32;
        // Only space before it on its line, and not a string's content;
        // prose before it may end with a list item's prefix of spaces.
        let indent = &source[line_start as usize..next.start as usize];
        let starts_line =
            (line_start >= start || !both_code) && indent.bytes().all(|b| b == b' ' || b == b'\t');
        // What of the indentation was written with the token before.
        let written = start.saturating_sub(line_start) as i32;
        if !starts_line {
            if both_code && next.kind != TokenKind::Margin {
                let space = previous.and_then(|p| self.space(p, next, gap));
                self.out.push_str(space.unwrap_or(gap));
            } else {
                self.out.push_str(gap);
            }
            return;
        }
        // A new line: the line breaks before it, then its indentation.
        let actual = (next.start - line_start) as i32 - range.margin;
        let breaks_end = line_start.max(start) as usize;
        if both_code && previous.is_some() {
            let breaks = gap[..breaks_end - start as usize].matches('\n').count();
            let mut blank = breaks.saturating_sub(1).min(1);
            let opens = previous.is_some_and(|p| self.text(p).ends_with('{'));
            let closes = matches!(self.text(next), ")" | "]" | "}");
            if opens || closes {
                blank = 0;
            }
            let newline = if gap.contains("\r\n") { "\r\n" } else { "\n" };
            // No break here when the previous token, a string's content,
            // ends with the line's.
            for _ in 0..(blank + 1) * usize::from(breaks > 0) {
                self.out.push_str(newline);
            }
        } else if both_code && self.standalone {
            // The file's start: no blank lines.
        } else {
            self.out.push_str(&source[start as usize..breaks_end]);
        }
        // Lines of fenced code may be less indented than the fence, and
        // tabs in markdown count to the next multiple of four: keep both,
        // and space before `<`, which decides if it may be a bracket.
        let keep =
            actual < 0 || (!self.standalone && indent.contains('\t')) || self.text(next) == "<";
        let formatted = self.line_start(next, actual, keep);
        if keep {
            self.out.push_str(&indent[written as usize..]);
        } else {
            for _ in 0..range.margin + formatted - written {
                self.out.push(' ');
            }
        }
    }

    /// Starts a line whose first token is `first`, indented `actual`
    /// spaces past its margin, returning its formatted indentation, which
    /// is `actual` if `keep`.
    fn line_start(&mut self, first: Token, actual: i32, keep: bool) -> i32 {
        if self.line_has_code {
            self.after_decorator = self.line_is_decorator && self.stack.len() == self.line_depth;
        }
        self.line_has_code = false;
        self.line_is_decorator = false;
        self.line_depth = self.stack.len();
        let text = self.text(first);
        let mut starts_statement = false;
        let formatted = if keep {
            actual
        } else if matches!(first.kind, TokenKind::Margin | TokenKind::QuotedString) {
            (actual + self.string_shift).max(0)
        } else if first.kind == TokenKind::Punctuation && matches!(text, ")" | "]" | "}") {
            self.stack.last().map_or(0, |o| o.base.formatted)
        } else if let Some(statement) = self.continued_statement(text) {
            (statement.formatted + actual - statement.actual).max(0)
        } else {
            starts_statement = true;
            self.stack.last().map_or(0, |o| o.base.formatted + UNIT)
        };
        // A multi-line block comment moves only if all its lines can.
        let formatted = if first.kind == TokenKind::Comment && text.contains('\n') {
            let shift = formatted - actual;
            // In markdown, its lines must stay in the code block.
            let margin = self.range_of(first.start).map_or(0, |r| r.margin);
            let fits = text.split('\n').skip(1).all(|line| {
                let indent = line.len() - line.trim_start_matches(' ').len();
                line.trim().is_empty() || indent as i32 + shift >= margin
            });
            if fits { formatted } else { actual }
        } else {
            formatted
        };
        self.line = Indent { actual, formatted };
        if starts_statement {
            *self.statements.last_mut().unwrap() = Some(self.line);
        }
        self.line_first = first.start;
        formatted
    }

    /// The statement a line starting with `first` continues, if the code
    /// before it didn't end one, or it starts with `.` or `?.`.
    fn continued_statement(&self, first: &str) -> Option<Indent> {
        let previous = self.previous?;
        if matches!(first, "." | "?.") {
            return *self.statements.last().unwrap();
        }
        if self.after_decorator {
            return None;
        }
        let top = self.stack.last();
        if top.is_some_and(|o| o.start == previous.start) {
            return None;
        }
        let text = self.text(previous);
        let ends = if top.is_some_and(|o| o.round) {
            text == ","
        } else {
            matches!(text, ";" | "{" | "}" | "," | "=>")
        };
        if ends {
            return None;
        }
        *self.statements.last().unwrap()
    }

    /// Writes a token, moving a multi-line block comment's later lines with
    /// its first, and tracks brackets and statements.
    fn token(&mut self, token: Token) {
        let text = self.text(token);
        if token.kind == TokenKind::Comment && text.contains('\n') && self.is_code(token) {
            // Its first line's shift, if it starts the line.
            let shift = if self.line_first == token.start {
                self.line.formatted - self.line.actual
            } else {
                0
            };
            let mut lines = text.split('\n');
            self.out.push_str(lines.next().unwrap());
            for line in lines {
                self.out.push('\n');
                if shift == 0 || line.trim().is_empty() {
                    self.out.push_str(line);
                    continue;
                }
                let indent = (line.len() - line.trim_start_matches(' ').len()) as i32;
                for _ in 0..(indent + shift).max(0) {
                    self.out.push(' ');
                }
                self.out.push_str(line.trim_start_matches(' '));
            }
            return;
        }
        self.out.push_str(text);
        if !self.is_code(token) || token.kind == TokenKind::Comment {
            return;
        }
        if !self.line_has_code {
            self.line_has_code = true;
            self.line_is_decorator = text == "@";
        }
        match token.kind {
            TokenKind::Punctuation if text.ends_with(['(', '[', '{']) => {
                // From its own line if that started at this depth, else
                // from the line that started its statement here.
                let base = if self.line_depth == self.stack.len() {
                    self.line
                } else {
                    self.statements.last().unwrap().unwrap_or(self.line)
                };
                self.stack.push(Open {
                    start: token.start,
                    base,
                    round: !text.ends_with('{'),
                });
                self.statements.push(Some(self.line));
            }
            TokenKind::Punctuation if matches!(text, ")" | "]" | "}") => {
                if self.stack.pop().is_some() {
                    self.statements.pop();
                }
            }
            TokenKind::LeftDelimiter if text.starts_with("\"\"\"") => {
                self.string_shift = self.line.formatted - self.line.actual;
            }
            _ => {}
        }
        if !matches!(token.kind, TokenKind::Margin | TokenKind::QuotedString) {
            self.previous = Some(token);
        }
    }

    fn text(&self, token: Token) -> &'a str {
        &self.source[token.range()]
    }

    fn role(&self, token: Token) -> Option<(Role, Operator)> {
        self.roles.get(&token.start).copied()
    }

    /// The space between two tokens on a line, or `None` to keep it.
    fn space(&self, a: Token, b: Token, gap: &str) -> Option<&'static str> {
        use Operator::*;
        use TokenKind::*;
        if gap.contains(['\n', '\r'])
            || matches!(a.kind, Comment | LeftDelimiter | QuotedString | Margin)
            || matches!(b.kind, Comment | QuotedString | Margin | RightDelimiter)
        {
            return None;
        }
        let (ta, tb) = (self.text(a), self.text(b));
        // Whether space comes before `<` decides whether it may be an
        // angle bracket (`List<T>` against `a < b`), so it stays.
        if tb == "<" {
            return None;
        }
        let (ra, rb) = (self.role(a), self.role(b));
        let is = |r: Option<(Role, Operator)>, role: Role| r.is_some_and(|r| r.0 == role);
        let op = |r: Option<(Role, Operator)>| r.map(|r| r.1);
        let closer = |t: &str| matches!(t, ")" | "]" | "}");
        let tight = |r: Option<(Role, Operator)>| {
            matches!(
                op(r),
                Some(
                    Dot | ChainNull | DotDot | ColonColon | HighColon | LowColon | Angle | CallJoin
                )
            )
        };
        let curly = |r: Option<(Role, Operator)>| matches!(op(r), Some(Curly | CurlyGroup));
        let round = |r: Option<(Role, Operator)>| {
            matches!(op(r), Some(Paren | ParenGroup | Square | SquareGroup))
        };
        let dot = |r: Option<(Role, Operator)>| {
            is(r, Role::Infix) && matches!(op(r), Some(Dot | ChainNull))
        };
        let spaced = |r: Option<(Role, Operator)>, t: Token| {
            is(r, Role::Infix) && t.kind == Punctuation && !tight(r)
        };
        let opens = |t: &str| t.ends_with(['(', '[', '{']);
        let keyword = matches!(ta, "if" | "while" | "for" | "when" | "catch" | "fn");
        let angle = op(ra) == Some(Angle) && is(ra, Role::Open)
            || op(rb) == Some(Angle) && matches!(rb.map(|r| r.0), Some(Role::Open | Role::Close));
        let prefix = is(ra, Role::Prefix)
            && a.kind == Punctuation
            && op(ra).is_some_and(|o| o.op_type() == OpType::Prefix);
        // The first rule that applies decides; `None` keeps the space.
        let rules = [
            // `,` and `;`: none before, one after, but before a closer.
            (is(rb, Role::Separator) && matches!(tb, "," | ";"), Some("")),
            (
                is(ra, Role::Separator) && matches!(ta, "," | ";"),
                (!closer(tb)).then_some(" "),
            ),
            (dot(ra) || dot(rb), Some("")),
            // Type colons.
            (is(rb, Role::Infix) && op(rb) == Some(HighColon), Some("")),
            (is(ra, Role::Infix) && op(ra) == Some(HighColon), Some(" ")),
            (spaced(ra, a) || spaced(rb, b), Some(" ")),
            // `if (` and `fn (`, but `f(`.
            (
                is(rb, Role::Open) && tb == "(" && op(rb) == Some(Paren),
                (a.kind == Word).then_some(if keyword { " " } else { "" }),
            ),
            // `{ a }`, `f {`, but `({`, `{}` and `${x}` as written.
            (
                is(rb, Role::Open) && tb == "{" && curly(rb),
                (!opens(ta)).then_some(" "),
            ),
            (
                is(ra, Role::Open) && ta == "{" && curly(ra),
                (tb != "}").then_some(" "),
            ),
            (is(rb, Role::Close) && tb == "}" && curly(rb), Some(" ")),
            (
                is(ra, Role::Open) && round(ra) || is(rb, Role::Close) && round(rb),
                Some(""),
            ),
            (angle || prefix, Some("")),
            (gap.len() > 1 && gap.bytes().all(|c| c == b' '), Some(" ")),
        ];
        let want = rules.into_iter().find(|rule| rule.0)?.1?;
        // Never respace tokens so they'd lex differently, as `- -x`.
        if want != gap && !lexes_alone(ta, want, tb) {
            return None;
        }
        Some(want)
    }
}

/// Whether `a`, `space` and `b` lex as just the tokens `a` and `b`. Space
/// changes only in code, where each token's lexing depends only on its
/// own text, the token before it, and whether `<` follows space (which
/// [`Formatter::space`] keeps), so the pair alone almost always tells; the
/// check after formatting catches the rest.
fn lexes_alone(a: &str, space: &str, b: &str) -> bool {
    let text = format!("{a}{space}{b}");
    let mut tokens = crate::lex::lex(&text, SourceKind::Standalone)
        .into_iter()
        .filter(|t| t.kind != TokenKind::Space && t.synthetic.is_none());
    let (Some(x), Some(y), None) = (tokens.next(), tokens.next(), tokens.next()) else {
        return false;
    };
    // Alone, brackets don't match, so inserted closers don't count.
    x.text(&text) == a && y.text(&text) == b
}

/// Checks that `text`, the formatted `original`, has the same tokens,
/// comments (but for each line's indentation), parse tree and markdown
/// prose.
fn check_same(original: &Parsed, text: &str, kind: SourceKind) -> Result<(), FmtError> {
    let unsafe_ = |why: &str| Err(FmtError::Unsafe(why.to_string()));
    let again = parse(text, kind);
    if first_error(&again).is_some() {
        return unsafe_("the result doesn't parse");
    }
    let tokens = |p: &Parsed| -> Vec<(TokenKind, Option<Synthetic>, String)> {
        p.lexed()
            .iter()
            .filter(|t| t.kind != TokenKind::Space)
            .map(|t| {
                let text = t.text(p.source());
                let text = if t.kind == TokenKind::Comment {
                    text.split('\n')
                        .map(|l| l.trim())
                        .collect::<Vec<_>>()
                        .join("\n")
                } else {
                    text.into_owned()
                };
                (t.kind, t.synthetic, text)
            })
            .collect()
    };
    let (before, after) = (tokens(original), tokens(&again));
    if before != after {
        let i = before
            .iter()
            .zip(&after)
            .take_while(|(x, y)| x == y)
            .count();
        let show = |t: Option<&(TokenKind, Option<Synthetic>, String)>| {
            t.map_or("the end".to_string(), |t| format!("{:?} {:?}", t.0, t.2))
        };
        return Err(FmtError::Unsafe(format!(
            "token {i}, {}, would become {}",
            show(before.get(i)),
            show(after.get(i))
        )));
    }
    if !same_tree(original, original.root(), &again, again.root()) {
        return unsafe_("the parse tree differs");
    }
    if kind == SourceKind::Markdown {
        let prose = |source: &str| -> Vec<String> {
            let mut pieces = Vec::new();
            let mut at = 0;
            for r in code_ranges(source, kind) {
                pieces.push(source[at..r.start as usize].to_string());
                at = r.end as usize;
            }
            pieces.push(source[at..].to_string());
            pieces
        };
        if prose(original.source()) != prose(text) {
            return unsafe_("markdown outside Temper code differs");
        }
    }
    Ok(())
}

/// Whether two trees have the same nodes and tokens, ignoring where they are.
fn same_tree(a: &Parsed, x: NodeId, b: &Parsed, y: NodeId) -> bool {
    let (ta, tb) = (a.tree(), b.tree());
    let mut stack = vec![(x, y)];
    while let Some((x, y)) = stack.pop() {
        if ta.kind(x) != tb.kind(y) {
            return false;
        }
        match (a.token(x), b.token(y)) {
            (Some(p), Some(q)) => {
                if p.kind != q.kind
                    || p.synthetic != q.synthetic
                    || a.token_text(p) != b.token_text(q)
                {
                    return false;
                }
            }
            (None, None) => {
                let (cx, cy) = (ta.children(x), tb.children(y));
                if cx.len() != cy.len() {
                    return false;
                }
                stack.extend(cx.iter().copied().zip(cy.iter().copied()));
            }
            _ => return false,
        }
    }
    true
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The safety check catches what formatting must never do.
    #[test]
    fn check_refuses_changed_code() {
        let original = parse("let x = a - -b; // note\nf(x)\n", SourceKind::Standalone);
        let ok = |text: &str| check_same(&original, text, SourceKind::Standalone);
        assert_eq!(ok("let x = a - -b; // note\nf(x)\n"), Ok(()));
        assert_eq!(ok("  let x  =  a - -b;   // note\n\n\nf( x )"), Ok(()));
        // A lost comment, and merged tokens.
        assert!(ok("let x = a - -b;\nf(x)\n").is_err());
        assert!(ok("let x = a --b; // note\nf(x)\n").is_err());
        let markdown = parse("Prose.\n\n    let x = 1;\n", SourceKind::Markdown);
        let md = |text: &str| check_same(&markdown, text, SourceKind::Markdown);
        assert_eq!(md("Prose.\n\n    let x  = 1;\n"), Ok(()));
        assert!(md("Prose!\n\n    let x = 1;\n").is_err());
    }
}
