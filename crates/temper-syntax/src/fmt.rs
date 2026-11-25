//! `temper fmt`: lays out Temper source in one canonical way.
//!
//! The formatter keeps every token as written, and every line break outside
//! bracketed lists, and rewrites only the space between tokens: each
//! line's indentation, the space between tokens on a line, trailing space
//! and blank lines. Line breaks matter in Temper (a `}` ending a line or a
//! `{` starting one can end a statement), and keeping them makes the
//! result predictable. Inside a list's brackets, after its commas, a line
//! break can't end a statement, so lists are laid out as their author's
//! line breaks and trailing commas ask (notes/fmt.md). There's no line
//! width.
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
//!   continuing a statement (the one before didn't end with `;`, `;;` (a
//!   block's signature), `{`, `}`, `,`, `=>` or a label's `:`, or inside
//!   `( )` and `[ ]` with `,`, or `;` if their contents start on a line of
//!   their own; or it starts with an infix operator like `.` or `&&`) is
//!   indented from the statement's first line, once, wherever it breaks; but
//!   twice, to tell it from what follows, in a header (a class's,
//!   interface's, function's or function literal's, from its first token to
//!   the `{` of its body, as `extends` or `throws` lines, not in the
//!   header's own brackets), from the header's first line, and in `( )`
//!   whose contents start on the `(` line (`if (a ||\n    b) {`), from that
//!   line. One starting with `;`, `;;` or `,` lines up with the statement,
//!   as a close bracket does. Comments never count. A bracket opened on a
//!   continuation line indents its contents from that line, but a header's
//!   body from the header's first line. Lines starting a node right after
//!   another (statements with no `;` between them) start statements, and
//!   comment lines go with the code after them.
//! - **Spacing on a line:** one space around infix operators (not `.`,
//!   `?.`, `..`, `::`, `<`/`>` of type arguments or label colons), after
//!   `,` and `;` and type and object literals' colons, around `{ }`
//!   and `\{ }` contents, and between a word like `if` or `fn` and `(`;
//!   none before `,`, `;` and those colons, inside `( )`, `\( )` and
//!   `[ ]` and a string's `${x}`, between a called name and `(`, or
//!   after prefix operators and `@`. Runs of spaces and tabs become one.
//!   Space next to comments, inside strings (a `\u{...}` escape's too),
//!   and before `<` (which decides whether it may be an angle bracket)
//!   is left as written.
//! - **Lines:** no trailing space (after comments too, on each of their
//!   lines), blank lines as written but at most one in a row (at a
//!   markdown code block's start and end too), none at the start of a
//!   file, and one line break at its end, the file's own (CRLF if it has
//!   any); a file of only space becomes empty.
//! - **Lists** (arguments, parameters, list and object literals, type
//!   arguments, and single items in `( )`, `[ ]` and `< >`, quasi-quotes'
//!   `\( )` and `\{ }` as their plain brackets) are laid out
//!   as their author asks: with a trailing comma, one item per line; else,
//!   with a line break after the open bracket, the items together on one
//!   line between the brackets' lines ("hugged"); else on one line,
//!   however long. Commas are never added or removed. Any item may span
//!   lines, laid out by its own rules: each item starts on the line the
//!   one before ends (`}, b`), or with a trailing comma on its own. A list
//!   with a comment between items, an item that must end its line (a
//!   multi-quoted string), a line break before a comma, only literals (a
//!   data table), or in a string keeps its line breaks as written.
//!
//! Multi-line block comments and the lines of multi-quoted strings move
//! with the line they start on (a comment only if none of its lines would
//! go left of the margin); their text is otherwise kept, but a doc
//! comment's (`/** ... */`) lines starting with `*` line up one column
//! past its `/`.
//!
//! Lines after a `// fmt: off` comment on a line of its own stay as
//! written, trailing space included, up to a `// fmt: on` on a line of its
//! own, or else the end of the brackets around it, of its code block
//! (markdown) or of the file. Only those exact comments count (but for
//! space after them), not `//fmt: off` or `/* fmt: off */`.
//!
//! In a literate `.temper.md` file only Temper code blocks change, each
//! indented from its block's own margin (the fence's indentation, or four
//! columns past its list items' for indented code). Blocks continue each
//! other's brackets. Lines indented with tabs keep their indentation, as
//! do lines starting like a fence (```` ``` ````), which moving could make
//! close their block.
//!
//! **Safety:** code that doesn't parse is left alone, with an error. After
//! formatting, the result is parsed again and must have the same tokens,
//! comments (apart from trailing space and the indentation their lines
//! share), parse tree and, in markdown, prose; if not, the formatter
//! refuses rather than change meaning. Formatting is idempotent:
//! formatted code formats to itself (tested over every Temper file in the
//! repository, and fuzzed, with checks of the tests' own that only space
//! changed).
//!
//! **Speed:** linear in the source: per-token facts that need a look back
//! (where a line starts) or around (what's in a list) are found in one
//! pass beforehand, so a long line or deep nesting costs no more per
//! token (tested by `formatting_time_grows_linearly`).

use crate::lex::{SourceKind, Synthetic, Token, TokenKind};
use crate::literate::{self, Kind};
use crate::operator::{Operator, Spacing};
use crate::parse::{Parsed, parse};
use crate::tree::{NodeId, NodeKind};
use std::collections::{HashMap, HashSet};
use std::fmt;

/// Spaces per indentation level.
const UNIT: i32 = 2;

/// Words that get a space before their call's `(`, as a style choice:
/// `if (x)` and `fn (x)`, but `f(x)`. Temper has no keywords: the lexer
/// and parser read these as names, and the frontend's builtins make them
/// control flow, which this crate can't see, so the list is here. A
/// function of the user's with one of these names is spaced the same.
/// (Words the grammar reads as operators, as `return` and `new`, keep
/// the space before their `(` as written.)
const SPACED_BEFORE_PAREN: &[&str] = &["if", "while", "for", "when", "catch", "fn"];

/// Choices `temper fmt` makes, for measuring alternatives
/// (`rules_census` in tests/fmt.rs); [`format`] uses the defaults.
#[doc(hidden)]
#[derive(Clone, Copy, Debug)]
pub struct Options {
    /// Whether lists are laid out by their rules, or keep their line
    /// breaks as written.
    pub lists: bool,
    /// Whether lists of literals (data tables) keep their line breaks.
    pub keep_data: bool,
    /// Whether continuation lines are indented by the rules (once, twice
    /// in a header), or keep their offset from their
    /// statement's first line as written.
    pub continuations: bool,
}

impl Default for Options {
    fn default() -> Self {
        Options {
            lists: true,
            keep_data: true,
            continuations: true,
        }
    }
}

/// Why a source was left as written.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum FmtError {
    /// The source has lexing or parsing errors; the text says the first,
    /// with its line.
    Unparseable(String),
    /// Formatting would have changed the code's tokens, tree, comments or
    /// prose: a bug in the formatter.
    Unsafe(String),
    /// The source has what the formatter doesn't take: a lone `\r`, a
    /// line break to some editors but not to Temper.
    Unsupported(String),
}

impl fmt::Display for FmtError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            FmtError::Unparseable(why) => write!(f, "doesn't parse ({why}); left as written"),
            FmtError::Unsafe(why) => write!(
                f,
                "formatting would change the code ({why}); left as written (a bug in temper fmt)"
            ),
            FmtError::Unsupported(why) => write!(f, "{why}; left as written"),
        }
    }
}

/// Formats `source`, returning the formatted text, or why it's left alone.
pub fn format(source: &str, kind: SourceKind) -> Result<String, FmtError> {
    format_parsed(&parse(source, kind), kind)
}

/// Formats a source already parsed, as the language server keeps an open
/// document's parse; `kind` is the one it was parsed as.
pub fn format_parsed(parsed: &Parsed, kind: SourceKind) -> Result<String, FmtError> {
    format_with(parsed, kind, Options::default())
}

/// [`format_parsed`] with other [`Options`].
#[doc(hidden)]
pub fn format_with(
    parsed: &Parsed,
    kind: SourceKind,
    options: Options,
) -> Result<String, FmtError> {
    if let Some(at) = lone_carriage_return(parsed.source()) {
        let line = parsed.source()[..at].matches('\n').count() + 1;
        return Err(FmtError::Unsupported(format!(
            "line {line}: a carriage return without a line feed after it \
             (temper fmt takes line breaks as \\n or \\r\\n)"
        )));
    }
    if let Some(why) = first_error(parsed) {
        return Err(FmtError::Unparseable(why));
    }
    let ranges = code_ranges(parsed.source(), kind);
    let text = Formatter::new(parsed, &ranges, kind, options).run();
    check_same(parsed, &text, kind)?;
    Ok(text)
}

/// Where the first `\r` not followed by `\n` is, if any. Editors and
/// language clients may count it as a line break, which Temper doesn't,
/// so formatting refuses rather than disagree about lines.
fn lone_carriage_return(source: &str) -> Option<usize> {
    let bytes = source.as_bytes();
    (bytes.iter().enumerate())
        .find(|&(i, &b)| b == b'\r' && bytes.get(i + 1) != Some(&b'\n'))
        .map(|(i, _)| i)
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
    /// The column its first line must stay left of, as it would continue
    /// the list item before it from there (markdown).
    below: Option<i32>,
}

/// Where the code is: all of a `.temper` file, or a `.temper.md` file's
/// Temper code blocks.
fn code_ranges(source: &str, kind: SourceKind) -> Vec<CodeRange> {
    if kind == SourceKind::Standalone {
        return vec![CodeRange {
            start: 0,
            end: source.len() as u32,
            margin: 0,
            below: None,
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
            let indent = &fence[..fence.len() - fence.trim_start_matches([' ', '\t']).len()];
            let margin = columns(indent);
            let next = source[block.begin.end..]
                .find('\n')
                .map_or(source.len(), |i| block.begin.end + i + 1);
            (next, margin)
        } else {
            // Indented: four columns past its containers' (list items').
            (block.begin.start, columns(&source[block.begin.clone()]) + 4)
        };
        ranges.push(CodeRange {
            start: start as u32,
            end: block.end.start as u32,
            margin: margin as i32,
            below: block.item_column.map(|c| c as i32),
        });
    }
    ranges
}

/// How many columns text takes, with markdown's tabs to the next multiple
/// of four.
fn columns(text: &str) -> usize {
    text.chars()
        .fold(0, |n, c| if c == '\t' { n / 4 * 4 + 4 } else { n + 1 })
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

/// The parse tree's nodes, not its tokens, each once, every node before
/// its children.
fn nodes(parsed: &Parsed) -> impl Iterator<Item = NodeId> + '_ {
    let tree = parsed.tree();
    let mut stack = vec![parsed.root()];
    std::iter::from_fn(move || {
        let id = stack.pop()?;
        let children = tree.children(id).iter().copied();
        stack.extend(children.filter(|&c| tree.kind(c) != NodeKind::Token));
        Some(id)
    })
}

/// Whether a token's text opens a bracket: `(`, `[` or `{`, or a string's
/// `${` or an escape's `\u{`.
fn opens(text: &str) -> bool {
    text.ends_with(['(', '[', '{'])
}

/// Whether a token's text closes a bracket.
fn closes(text: &str) -> bool {
    matches!(text, ")" | "]" | "}")
}

/// Each operator token's role and operator, by its start in the source.
fn roles(parsed: &Parsed) -> HashMap<u32, (Role, Operator)> {
    let tree = parsed.tree();
    let mut roles = HashMap::new();
    for id in nodes(parsed) {
        let NodeKind::Op(op) = tree.kind(id) else {
            continue;
        };
        if op == Operator::Leaf {
            continue;
        }
        let children = tree.children(id);
        let is_node = |c: &NodeId| tree.kind(*c) != NodeKind::Token;
        // Whether a token has operands before and after it.
        let first = children.iter().position(is_node).unwrap_or(usize::MAX);
        let last = children.iter().rposition(is_node).unwrap_or(0);
        for (i, &child) in children.iter().enumerate() {
            if is_node(&child) {
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
            let role = if opens(text) || (text == "<" && op == Operator::Angle) {
                Role::Open
            } else if closes(text) || (text == ">" && op == Operator::Angle) {
                Role::Close
            } else if text == ","
                || matches!(
                    op,
                    Operator::Comma | Operator::Semi | Operator::SemiSemi | Operator::SemiSemiSemi
                )
            {
                // A comma always separates, as after `extends`.
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

/// Where the code tokens of headers start, each with where its header's
/// first token starts, and where each header's body's `{` starts, by the
/// header's first token. Headers run from a class's, interface's,
/// function's or function literal's first token to the `{` opening its
/// body (`class C extends D {`, `fn (): Int throws Bubble {`), where a
/// continuation line indents twice to tell it from the body. The first
/// token is left out (a line starting with it continues what's before),
/// and a token in nested headers gets the innermost, each token assigned
/// once, so nesting costs no more per token.
fn headers(parsed: &Parsed, tokens: &[Token]) -> (HashMap<u32, u32>, HashMap<u32, u32>) {
    use Operator::*;
    let tree = parsed.tree();
    // Each header's tokens, as a range of indices, with its first token's
    // start. Headers nest or are apart, as they're nodes of the tree.
    let mut spans: Vec<(usize, usize, u32)> = Vec::new();
    let mut braces = HashMap::new();
    for id in nodes(parsed) {
        let children = tree.children(id);
        let (NodeKind::Op(Curly), Some(&head)) = (tree.kind(id), children.first()) else {
            continue;
        };
        let brace =
            (children.iter().skip(1)).find_map(|&c| parsed.token(c).filter(|t| !t.synthetic));
        let (Some(brace), Some(start)) = (brace, first_token(parsed, head)) else {
            continue;
        };
        let from = tokens.partition_point(|t| t.start < start);
        let text = |i: usize| tokens.get(i).map(|t| &parsed.source()[t.range()]);
        let declares = match tree.kind(head) {
            NodeKind::Op(ExtendsComma | ExtendsNoComma | Throws) => true,
            // A signature's return type: `f(...): T`.
            NodeKind::Op(HighColon) => tree
                .children(head)
                .first()
                .is_some_and(|&c| tree.kind(c) == NodeKind::Op(Paren)),
            _ => matches!(
                text(from),
                Some("class" | "interface" | "let" | "var" | "fn")
            ),
        };
        if declares {
            let to = tokens.partition_point(|t| t.start <= brace.start);
            if from + 1 < to {
                spans.push((from + 1, to, start));
            }
            braces.insert(start, brace.start);
        }
    }
    // One sweep over the tokens, with the headers open at each, outer
    // ones first, so each token gets the innermost.
    spans.sort_by_key(|&(from, to, _)| (from, std::cmp::Reverse(to)));
    let mut starts = HashMap::new();
    let mut open: Vec<(usize, u32)> = Vec::new();
    let mut next = spans.iter().peekable();
    let end = spans.iter().map(|s| s.1).max().unwrap_or(0);
    for (i, token) in tokens.iter().enumerate().take(end) {
        while open.last().is_some_and(|&(to, _)| to <= i) {
            open.pop();
        }
        while let Some(&(_, to, start)) = next.next_if(|s| s.0 == i) {
            open.push((to, start));
        }
        if let Some(&(_, start)) = open.last() {
            starts.insert(token.start, start);
        }
    }
    (starts, braces)
}

/// Where nodes start that follow another node directly, or with only
/// tokens the parser inserted between them, as statements without a `;`
/// written between them do (`f()` and `g()` on lines of their own, or
/// `f(a)` and a `{ x }` on the next line, which the line break makes a
/// statement of its own, not a block argument).
fn juxtaposed(parsed: &Parsed) -> HashSet<u32> {
    let tree = parsed.tree();
    let mut starts = HashSet::new();
    for id in nodes(parsed) {
        let mut after_node = false;
        for &c in tree.children(id) {
            match parsed.token(c) {
                Some(t) if t.synthetic => {}
                Some(_) => after_node = false,
                None => {
                    if after_node {
                        starts.extend(first_token(parsed, c));
                    }
                    after_node = true;
                }
            }
        }
    }
    starts
}

/// Where a node's first token as written starts.
fn first_token(parsed: &Parsed, id: NodeId) -> Option<u32> {
    if let Some(t) = parsed.token(id) {
        return (!t.synthetic).then_some(t.start);
    }
    (parsed.tree().children(id).iter()).find_map(|&c| first_token(parsed, c))
}

/// A bracketed list: arguments, parameters, a list or object literal, or
/// type arguments, of two or more items or with a trailing comma; or a
/// single item in `( )`, `[ ]` or `< >` (in `{ }`, it may be a block's
/// statement); quasi-quotes' `\( )` and `\{ }` as `( )` and `{ }`. Its fields are indices in [`Formatter`]'s tokens.
#[derive(Debug)]
struct List {
    open: usize,
    close: usize,
    commas: Vec<usize>,
    /// Whether a comma ends it, which puts one item per line.
    trailing_comma: bool,
    /// Whether its items, two or more, are all literals, like a row of a
    /// table.
    data: bool,
}

/// Where a list may break: the gap after its open bracket, after one of
/// its commas, or before its close bracket.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Place {
    AfterOpen,
    AfterComma,
    BeforeClose,
}

/// How a list is laid out.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Layout {
    /// Its line breaks as written: it has comments between items, a line
    /// break before a comma, an item that must end its line, or is data,
    /// or is in a string.
    Kept,
    /// On one line: no line break after its open bracket.
    Flat,
    /// Its items together on one line between the brackets' lines: a line
    /// break after its open bracket, and no trailing comma.
    Hug,
    /// One item per line: a trailing comma.
    Tall,
}

impl Layout {
    /// Whether the list breaks the line at a place, given whether it's
    /// broken there as written.
    fn breaks(self, place: Place, written: bool) -> bool {
        match self {
            Layout::Kept => written,
            Layout::Flat => false,
            Layout::Hug => place != Place::AfterComma,
            Layout::Tall => true,
        }
    }
}

/// The source's lists, given each token's index by its start.
fn find_lists(parsed: &Parsed, index: &HashMap<u32, usize>) -> Vec<List> {
    use Operator::*;
    let tree = parsed.tree();
    let mut lists = Vec::new();
    for id in nodes(parsed) {
        let children = tree.children(id);
        let NodeKind::Op(
            Paren | ParenGroup | EscParen | Square | SquareGroup | CurlyGroup | EscCurly | Angle,
        ) = tree.kind(id)
        else {
            continue;
        };
        for w in children.windows(3) {
            let (Some(open), Some(close)) = (parsed.token(w[0]), parsed.token(w[2])) else {
                continue;
            };
            let brackets = (parsed.token_text(open), parsed.token_text(close));
            let list = match tree.kind(w[1]) {
                NodeKind::Op(Comma) => matches!(
                    brackets,
                    ("(" | "\\(", ")") | ("[", "]") | ("{" | "\\{", "}") | ("<", ">")
                ),
                NodeKind::Token => false,
                _ => matches!(brackets, ("(" | "\\(", ")") | ("[", "]") | ("<", ">")),
            };
            if !list || open.synthetic || close.synthetic {
                continue;
            }
            let (Some(&open), Some(&close)) = (index.get(&open.start), index.get(&close.start))
            else {
                continue;
            };
            let single = tree.kind(w[1]) != NodeKind::Op(Comma);
            let items = if single { &[][..] } else { tree.children(w[1]) };
            let commas: Vec<usize> = (items.iter())
                .filter_map(|&c| parsed.token(c))
                .filter(|t| !t.synthetic && parsed.token_text(*t) == ",")
                .filter_map(|t| index.get(&t.start).copied())
                .collect();
            lists.push(List {
                open,
                close,
                trailing_comma: commas.last() == Some(&(close - 1)),
                commas,
                data: !single && is_data(parsed, w[1]),
            });
        }
    }
    lists
}

/// Whether a node is a literal (a number, `true`, `false`, `null`, a
/// string without holes, maybe negated or tagged as `char'a'`), or a list
/// of them, or a call of a name with them, as `new Pair("a", 1)`.
fn is_data(parsed: &Parsed, id: NodeId) -> bool {
    use Operator::*;
    let tree = parsed.tree();
    let children = tree.children(id);
    let nodes = || (children.iter().copied()).filter(|&c| tree.kind(c) != NodeKind::Token);
    match tree.kind(id) {
        NodeKind::Op(Leaf) => children.iter().all(|&c| {
            parsed.token(c).is_some_and(|t| {
                t.kind == TokenKind::Number
                    || matches!(parsed.token_text(t), "true" | "false" | "null")
            })
        }),
        NodeKind::Op(QuotedGroup) => nodes().all(|c| tree.kind(c) != NodeKind::Op(DollarCurly)),
        // A string's parentheses, which the parser adds.
        NodeKind::Op(ParenGroup) => {
            (children.iter()).all(|&c| parsed.token(c).is_none_or(|t| t.synthetic))
                && nodes().all(|c| is_data(parsed, c))
        }
        NodeKind::Op(PreDash | PrePlus | SquareGroup | Comma) => {
            nodes().all(|c| is_data(parsed, c))
        }
        NodeKind::Op(Paren) => {
            let mut nodes = nodes();
            let name = |id: NodeId| {
                tree.kind(id) == NodeKind::Op(Leaf)
                    && (tree.children(id).iter())
                        .all(|&c| parsed.token(c).is_some_and(|t| t.kind == TokenKind::Word))
            };
            let callee = nodes.next().is_some_and(|c| match tree.kind(c) {
                NodeKind::Op(New) => {
                    let parts = tree.children(c);
                    parts.len() == 2 && name(parts[1])
                }
                _ => name(c),
            });
            callee && nodes.all(|c| is_data(parsed, c))
        }
        _ => false,
    }
}

/// The tokens to lay out (not space or synthetic tokens), and for each,
/// whether the space before it is code's, which the formatter may change,
/// or a string's (including a `\u{...}` escape's), which it keeps, and
/// whether it's in a string (as in a `${...}` hole).
fn code_gaps(source: &str, lexed: &[Token]) -> (Vec<Token>, Vec<bool>, Vec<bool>) {
    #[derive(PartialEq)]
    enum Frame {
        Code,
        String,
        Escape,
    }
    let mut frames = vec![Frame::Code];
    // How many of them are strings, so asking takes no scan of deep ones.
    let mut strings = 0;
    let (mut tokens, mut gaps, mut in_string) = (Vec::new(), Vec::new(), Vec::new());
    for &t in lexed.iter().filter(|t| t.kind != TokenKind::Space) {
        if t.synthetic.is_none() {
            tokens.push(t);
            gaps.push(frames.last() == Some(&Frame::Code));
            in_string.push(strings > 0);
        }
        let text = t.text(source);
        match t.kind {
            TokenKind::LeftDelimiter => {
                frames.push(Frame::String);
                strings += 1;
            }
            TokenKind::RightDelimiter => {
                while let Some(frame) = frames.pop() {
                    if frame == Frame::String {
                        strings -= 1;
                        break;
                    }
                    if frames.is_empty() {
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
            TokenKind::Punctuation if opens(&text) => frames.push(Frame::Code),
            TokenKind::Punctuation if closes(&text) && frames.len() > 1 => {
                frames.pop();
            }
            _ => {}
        }
    }
    (tokens, gaps, in_string)
}

/// A line's indentation as written and as formatted, relative to its
/// code block's margin, and where its first token starts, which tells
/// lines apart.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
struct Indent {
    actual: i32,
    formatted: i32,
    first: u32,
}

/// An open bracket: its start, the line its contents indent from, and the
/// line it's on.
#[derive(Clone, Copy, Debug)]
struct Open {
    start: u32,
    base: Indent,
    line: Indent,
    /// Whether it's `(`.
    paren: bool,
    round: bool,
    /// Whether its contents start on a line of their own.
    contents_own_line: bool,
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
    /// Where statements without a `;` before them start.
    juxtaposed: HashSet<u32>,
    /// Where headers' tokens start, with their header's first token.
    headers: HashMap<u32, u32>,
    /// Where each header's body's `{` starts, by the header's first token.
    bodies: HashMap<u32, u32>,
    /// Headers' first lines and bracket depths, by their first tokens,
    /// once written.
    header_lines: HashMap<u32, Option<(Indent, usize)>>,
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
    /// The bracket depth at the current line's start.
    line_depth: usize,
    /// How far the line that opened the last multi-quoted string moved,
    /// which its content lines move too.
    string_shift: i32,
    options: Options,
    /// The source's line break, for lines the formatter adds.
    newline: &'static str,
    /// The current token's index in `tokens`.
    index: usize,
    /// The first line of the header the current line continues, as an
    /// `extends` line does a class's, and where the header's first token
    /// starts.
    line_header: Option<(Indent, u32)>,
    // Lists and their layouts, planned by `plan`.
    lists: Vec<List>,
    layouts: Vec<Layout>,
    /// For the gap before each token, the list that may break there.
    places: Vec<Option<(usize, Place)>>,
    /// For each token, whether it's in a `// fmt: off` region, where it and
    /// the space before it stay as written, and whether it ends one.
    kept: Vec<bool>,
    region_ends: Vec<bool>,
    /// For each token, where its line starts, found in one pass, as
    /// scanning back for each token would take quadratic time on a long
    /// line.
    line_starts: Vec<u32>,
    /// For each token, the index of the first token from it on that isn't
    /// a comment (or the end).
    next_code: Vec<usize>,
    /// Where object literals' colons start, as in `{ a: 1 }`, unlike
    /// labels' (`outer: while`).
    properties: HashSet<u32>,
}

impl<'a> Formatter<'a> {
    fn new(
        parsed: &'a Parsed,
        ranges: &'a [CodeRange],
        kind: SourceKind,
        options: Options,
    ) -> Self {
        let (tokens, code_gaps, in_string) = code_gaps(parsed.source(), parsed.lexed());
        let source = parsed.source();
        let mut line_starts = Vec::with_capacity(tokens.len());
        let (mut at, mut line) = (0, 0);
        for t in &tokens {
            let start = t.start as usize;
            if let Some(i) = source[at..start].rfind('\n') {
                line = at + i + 1;
            }
            at = start;
            line_starts.push(line as u32);
        }
        let mut next_code = vec![tokens.len(); tokens.len() + 1];
        for i in (0..tokens.len()).rev() {
            next_code[i] = if tokens[i].kind == TokenKind::Comment {
                next_code[i + 1]
            } else {
                i
            };
        }
        let mut formatter = Formatter {
            source: parsed.source(),
            tokens,
            code_gaps,
            open_at_end: parsed.lexed().iter().any(|t| {
                t.kind == TokenKind::RightDelimiter
                    && t.synthetic.is_some()
                    && t.start as usize == parsed.source().len()
            }),
            roles: roles(parsed),
            juxtaposed: juxtaposed(parsed),
            headers: HashMap::new(),
            bodies: HashMap::new(),
            header_lines: HashMap::new(),
            ranges,
            standalone: kind == SourceKind::Standalone,
            out: String::with_capacity(parsed.source().len() + 64),
            stack: Vec::new(),
            statements: vec![None],
            previous: None,
            line: Indent::default(),
            line_first: u32::MAX,
            line_depth: 0,
            string_shift: 0,
            options,
            newline: if parsed.source().contains("\r\n") {
                "\r\n"
            } else {
                "\n"
            },
            index: 0,
            line_header: None,
            lists: Vec::new(),
            layouts: Vec::new(),
            places: Vec::new(),
            kept: Vec::new(),
            region_ends: Vec::new(),
            line_starts,
            next_code,
            properties: property_colons(parsed),
        };
        (formatter.kept, formatter.region_ends) = formatter.off_regions();
        (formatter.headers, formatter.bodies) = headers(parsed, &formatter.tokens);
        formatter.header_lines = formatter.headers.values().map(|&h| (h, None)).collect();
        if options.lists {
            formatter.plan(parsed, &in_string);
        }
        formatter
    }

    /// Which tokens are in `// fmt: off` regions: from the line after a
    /// `// fmt: off` on a line of its own to a `// fmt: on` on a line of its
    /// own, or else to the end of the brackets around it (a block), of its
    /// code block (in markdown) or of the file; and which tokens end one
    /// in its code block (`// fmt: on` or a close bracket), maybe one
    /// with no tokens, whose lines up to theirs stay as written too.
    fn off_regions(&self) -> (Vec<bool>, Vec<bool>) {
        let mut kept = vec![false; self.tokens.len()];
        let mut ends = vec![false; self.tokens.len()];
        // The bracket depth and code block of the `// fmt: off`.
        let mut off: Option<(usize, u32)> = None;
        let mut depth = 0;
        for (i, &t) in self.tokens.iter().enumerate() {
            let text = self.text(t);
            let range = self.range_of(t.start).map(|r| r.start);
            let line_start = self.line_starts[i] as usize;
            let own_line = (self.source[line_start..t.start as usize].bytes())
                .all(|b| b == b' ' || b == b'\t');
            let marker = (t.kind == TokenKind::Comment && own_line && range.is_some())
                .then(|| fmt_marker(text))
                .flatten();
            let closer = t.kind == TokenKind::Punctuation && closes(text);
            if let Some((d, block)) = off {
                if range != Some(block) || marker == Some(false) || (closer && depth == d) {
                    off = None;
                    ends[i] = range == Some(block);
                } else {
                    kept[i] = true;
                }
            }
            if t.kind == TokenKind::Punctuation && opens(text) {
                depth += 1;
            } else if closer {
                depth = depth.saturating_sub(1);
            }
            if off.is_none() && marker == Some(true) {
                off = range.map(|block| (depth, block));
            }
        }
        (kept, ends)
    }

    /// Finds the lists and decides their layouts, inner lists first, so
    /// an outer list sees its items' line breaks as formatted.
    fn plan(&mut self, parsed: &Parsed, in_string: &[bool]) {
        let n = self.tokens.len();
        let index: HashMap<u32, usize> = (self.tokens.iter().enumerate())
            .map(|(i, t)| (t.start, i))
            .collect();
        self.lists = find_lists(parsed, &index);
        self.places = vec![None; n + 1];
        for (id, list) in self.lists.iter().enumerate() {
            self.places[list.open + 1] = Some((id, Place::AfterOpen));
            for &comma in &list.commas {
                if comma + 1 < list.close {
                    self.places[comma + 1] = Some((id, Place::AfterComma));
                }
            }
            self.places[list.close] = Some((id, Place::BeforeClose));
        }
        // For the gap before each token: whether it has a line break as
        // written; whether the output has one there that isn't a list's to
        // change, or an inner list's; and whether prose comes before it.
        let mut newlines = vec![false; n + 1];
        let mut breaks = vec![false; n + 1];
        let mut prose = vec![false; n + 1];
        for j in 1..n {
            let (p, t) = (self.tokens[j - 1], self.tokens[j]);
            let gap = &self.source[p.end as usize..t.start as usize];
            newlines[j] = gap.contains('\n');
            prose[j] = self.range_of(t.start).is_none_or(|r| r.start > p.start);
            let ours = self.places[j].is_some() && self.code_gaps[j];
            breaks[j] = newlines[j] && !ours;
        }
        // An item may span lines, its own interior following its own
        // rules, unless a comment comes before or after it, or its last
        // token must end its line (a multi-quoted string's): then what
        // follows can't join its last line. Nor may one starting with `<`
        // move, as space before `<` decides whether it's a bracket.
        let item_fits = |first: usize, end: usize| {
            let Some(last) = end.checked_sub(1).filter(|&last| first <= last) else {
                return true;
            };
            let (a, z) = (self.tokens[first], self.tokens[last]);
            a.kind != TokenKind::Comment
                && &self.source[a.range()] != "<"
                && z.kind != TokenKind::Comment
                && !(in_string[last] && z.kind != TokenKind::RightDelimiter)
        };
        // How many gaps before each token have prose before them, and how
        // many tokens before it are kept, so each list asks in one step.
        let count = |flags: &[bool]| -> Vec<usize> {
            let mut sums = vec![0; flags.len() + 1];
            for (i, &f) in flags.iter().enumerate() {
                sums[i + 1] = sums[i] + usize::from(f);
            }
            sums
        };
        let (prose_before, kept_before) = (count(&prose), count(&self.kept));
        let mut order: Vec<usize> = (0..self.lists.len()).collect();
        order.sort_by_key(|&i| self.lists[i].close - self.lists[i].open);
        self.layouts = vec![Layout::Kept; self.lists.len()];
        for id in order {
            let list = &self.lists[id];
            // Each item runs from after the open bracket or a comma to the
            // next comma or the close bracket; the line breaks between
            // them are the list's.
            let ends = list.commas.iter().copied().chain([list.close]);
            let starts = [list.open].into_iter().chain(list.commas.iter().copied());
            let kept = (self.options.keep_data && list.data)
                || in_string[list.open]
                || prose_before[list.close + 1] > prose_before[list.open + 1]
                || kept_before[list.close + 1] > kept_before[list.open]
                || starts.zip(ends).any(|(start, end)| {
                    breaks[start + 1] || breaks[end] || !item_fits(start + 1, end)
                });
            let layout = if kept {
                Layout::Kept
            } else if list.trailing_comma {
                Layout::Tall
            } else if newlines[list.open + 1] {
                Layout::Hug
            } else {
                Layout::Flat
            };
            self.layouts[id] = layout;
            // Its line breaks, for the lists around it.
            let places = [list.open + 1, list.close].into_iter();
            for j in places.chain(list.commas.iter().map(|c| c + 1)) {
                if let Some((_, place)) = self.places[j] {
                    breaks[j] |= layout.breaks(place, newlines[j]);
                }
            }
        }
    }

    /// Whether a list's layout breaks the gap before the current token
    /// or joins it, or leaves it as written (`None`).
    fn wanted_break(&self) -> Option<bool> {
        let (id, place) = self.places.get(self.index).copied().flatten()?;
        match self.layouts[id] {
            Layout::Kept => None,
            layout => Some(layout.breaks(place, false)),
        }
    }

    fn run(mut self) -> String {
        let source = self.source;
        if self.tokens.is_empty() {
            // Only space: a `.temper` file is empty, as one with code has
            // no blank lines at its start or end.
            let space = source.trim_matches([' ', '\t', '\r', '\n']).is_empty();
            return if self.standalone && space {
                String::new()
            } else {
                source.to_string()
            };
        }
        // A copy, as other methods read `self.tokens` too.
        let tokens = self.tokens.clone();
        let mut end = 0;
        for (i, &token) in tokens.iter().enumerate() {
            self.index = i;
            let previous = i.checked_sub(1).map(|p| tokens[p]);
            let wanted = self.wanted_break();
            if self.kept[i] {
                self.kept_gap(end, token);
            } else if self.region_ends[i] {
                // Lines in a region stay as written up to the line ending it.
                let line_start = self.line_starts[i].max(end);
                self.out
                    .push_str(&source[end as usize..line_start as usize]);
                self.gap(previous, line_start, token, wanted);
            } else if self.code_gaps[i] || token.kind == TokenKind::Margin {
                self.gap(previous, end, token, wanted);
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
            // A `// fmt: off` region's last line as written, trailing space
            // too; then one line break at the end, the file's own.
            if self.kept[tokens.len() - 1] {
                self.out
                    .push_str(&rest[..rest.find(['\r', '\n']).unwrap_or(rest.len())]);
            }
            self.out.push_str(self.newline);
        } else if !in_string
            && self.is_code(tokens[tokens.len() - 1])
            && !self.kept[tokens.len() - 1]
        {
            // A code block left open at the end of a literate file.
            self.code_end(rest);
        } else {
            self.out.push_str(rest);
        }
        self.out
    }

    /// Writes the space before a token in a `// fmt: off` region as written,
    /// starting its line as written if it starts one.
    fn kept_gap(&mut self, start: u32, next: Token) {
        let source = self.source;
        self.out
            .push_str(&source[start as usize..next.start as usize]);
        let line_start = self.line_starts[self.index] as usize;
        let indent = &source[line_start..next.start as usize];
        if line_start as u32 >= start && indent.bytes().all(|b| b == b' ' || b == b'\t') {
            let margin = self.range_of(next.start).map_or(0, |r| r.margin);
            let actual = columns(indent) as i32 - margin;
            self.line_start(next, Some(actual), None);
        }
    }

    /// Writes the space after a code block's last token, up to its closing
    /// fence or the file's end: no trailing space, and at most one blank
    /// line.
    fn code_end(&mut self, gap: &str) {
        let Some(last) = gap.rfind('\n') else {
            self.out.push_str(gap);
            return;
        };
        let breaks = gap[..last].matches('\n').count() + 1;
        for _ in 0..breaks.min(2) {
            self.out.push_str(newline_of(gap));
        }
        self.out.push_str(&gap[last + 1..]);
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

    /// Writes the space between `previous` (ending at `start`) and `next`:
    /// a line break if `wanted` is true, none if false, else as written.
    fn gap(&mut self, previous: Option<Token>, start: u32, next: Token, wanted: Option<bool>) {
        let source = self.source;
        let gap = &source[start as usize..next.start as usize];
        let Some(range) = self.range_of(next.start) else {
            // Code ending, before prose (a closing fence).
            let kept = self.index > 0 && self.kept[self.index - 1];
            if previous.is_some_and(|p| self.is_code(p)) && !kept {
                self.code_end(gap);
            } else {
                self.out.push_str(gap);
            }
            return;
        };
        match wanted {
            // A list's line joined.
            Some(false) if gap.contains('\n') => {
                let space = previous.and_then(|p| self.space(p, next, " "));
                self.out.push_str(space.unwrap_or(" "));
                return;
            }
            // A list's line broken where it wasn't.
            Some(true) if !gap.contains('\n') => {
                self.out.push_str(self.newline);
                let formatted = self.line_start(next, None, None).unwrap_or(0);
                for _ in 0..range.margin + formatted {
                    self.out.push(' ');
                }
                return;
            }
            _ => {}
        }
        let both_code =
            previous.is_none_or(|p| self.range_of(p.start).is_some()) && start >= range.start;
        let line_start = self.line_starts[self.index];
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
            // Blank lines are the author's; a run counts as one.
            let blank = breaks.saturating_sub(1).min(1);
            let newline = newline_of(gap);
            // No break here when the previous token, a string's content,
            // ends with the line's.
            for _ in 0..(blank + 1) * usize::from(breaks > 0) {
                self.out.push_str(newline);
            }
        } else if both_code && self.standalone {
            // The file's start: no blank lines.
        } else if start < range.start && range.start as usize <= breaks_end {
            // A code block's start, after prose: at most one blank line.
            self.out
                .push_str(&source[start as usize..range.start as usize]);
            let blank = &source[range.start as usize..breaks_end];
            if blank.contains('\n') {
                self.out.push_str(newline_of(blank));
            }
        } else {
            self.out.push_str(&source[start as usize..breaks_end]);
        }
        // An indented code block's first line, right after a list item,
        // stays left of where it would continue the item.
        let below = (line_start == range.start)
            .then_some(range.below)
            .flatten()
            .map(|c| c - range.margin);
        match self.line_start(next, Some(actual), below) {
            Some(formatted) => {
                for _ in 0..range.margin + formatted - written {
                    self.out.push(' ');
                }
            }
            None => self.out.push_str(&indent[written as usize..]),
        }
    }

    /// Starts a line whose first token is `first`, indented `written`
    /// past its margin (`None` for a line the formatter adds), and
    /// returns its indentation as formatted, or `None` if it stays as
    /// written ([`Self::stays_as_written`]; `below` is that method's).
    fn line_start(
        &mut self,
        first: Token,
        written: Option<i32>,
        below: Option<i32>,
    ) -> Option<i32> {
        let actual = written.unwrap_or(0);
        self.line_depth = self.stack.len();
        let mut starts_statement = false;
        self.line_header = None;
        let formatted = if matches!(first.kind, TokenKind::Margin | TokenKind::QuotedString) {
            actual + self.string_shift
        } else if self.closes_bracket(self.index) {
            // Its line continues the bracket's, as `} else {` does.
            self.line_depth = self.line_depth.saturating_sub(1);
            self.stack.last().map_or(0, |o| o.base.formatted)
        } else if let Some((statement, code)) = self.continued_statement(first) {
            // Twice from a header's first line (`extends`, `throws`), to
            // tell it from the body, but not in the header's own brackets
            // (its parameters); twice in parentheses whose contents start
            // on the `(` line, from that line (`if (a ||\n    b) {`), to
            // tell it from what follows; else once from the statement's.
            self.line_header = self.headers.get(&code.start).and_then(|&h| {
                let (line, depth) = self.header_lines[&h]?;
                (depth == self.stack.len()).then_some((line, h))
            });
            let in_parens = self
                .stack
                .last()
                .is_some_and(|o| o.paren && o.line == statement);
            if !self.options.continuations {
                statement.formatted + actual - statement.actual
            } else if matches!(self.text(code), ";" | ";;" | ",")
                && self.role(code).is_some_and(|r| r.0 == Role::Separator)
            {
                // A separator on a line of its own, as after a multi-quoted
                // string, lines up with what it ends, as a close bracket
                // does.
                statement.formatted
            } else if let Some((header, _)) = self.line_header {
                header.formatted + 2 * UNIT
            } else if in_parens {
                statement.formatted + 2 * UNIT
            } else {
                statement.formatted + UNIT
            }
        } else {
            starts_statement = true;
            self.stack.last().map_or(0, |o| o.base.formatted + UNIT)
        };
        // Never left of the margin, even under a line kept there.
        let formatted = formatted.max(0);
        let stays = written.is_some() && self.stays_as_written(first, actual, formatted, below);
        let formatted = if stays { actual } else { formatted };
        self.line = Indent {
            actual: written.unwrap_or(formatted),
            formatted,
            first: first.start,
        };
        if starts_statement {
            *self.statements.last_mut().unwrap() = Some(self.line);
        }
        self.line_first = first.start;
        (!stays).then_some(formatted)
    }

    /// Whether a line starting with `first`, indented `actual` past its
    /// margin as written and `formatted` by the rules, stays as
    /// written: in a `// fmt: off` region; left of its margin (markdown,
    /// where fenced code may be less indented than its fence); indented
    /// with tabs in markdown (which counts them to the next multiple of
    /// four) or as a string's line (moving would mix tabs and spaces);
    /// starting like a fence in markdown, which moving could make close
    /// its block; starting with `<`, as space before it decides whether
    /// it may be a bracket; an indented code block's first line, after a
    /// list item, if formatting would take it to `below`, where it would
    /// continue the item; or a multi-line block comment whose lines can't
    /// all move ([`comment_moves`]).
    fn stays_as_written(
        &self,
        first: Token,
        actual: i32,
        formatted: i32,
        below: Option<i32>,
    ) -> bool {
        let indent = &self.source[self.line_starts[self.index] as usize..first.start as usize];
        let text = self.text(first);
        let rest = &self.source[first.start as usize..];
        let string_line = matches!(first.kind, TokenKind::Margin | TokenKind::QuotedString);
        let margin = self.range_of(first.start).map_or(0, |r| r.margin);
        self.kept[self.index]
            || actual < 0
            || ((!self.standalone || string_line) && indent.contains('\t'))
            || (!self.standalone && (rest.starts_with("```") || rest.starts_with("~~~")))
            || text == "<"
            || below.is_some_and(|b| formatted >= b)
            || (first.kind == TokenKind::Comment
                && text.contains('\n')
                && !comment_moves(text, formatted - actual, margin))
    }

    /// The statement a line starting with `first` continues, if the code
    /// before it didn't end one, or it starts with an infix operator like
    /// `.` or `&&`; and the line's first code token (after comments). A `;`
    /// ends one in parentheses too if their contents start on a line of
    /// their own (`for (\n  a;\n  b;`), not in `for (a;\n  b;`.
    fn continued_statement(&self, first: Token) -> Option<(Indent, Token)> {
        let previous = self.previous?;
        // A comment line goes with the code after it: it continues the
        // statement if that does, but not before a close bracket or the
        // end.
        let first = if first.kind == TokenKind::Comment {
            let next = Some(self.next_code[self.index]).filter(|&i| i < self.tokens.len());
            match next {
                Some(i) if !self.closes_bracket(i) => self.tokens[i],
                _ => return None,
            }
        } else {
            first
        };
        let statement = (*self.statements.last().unwrap()).map(|s| (s, first));
        if self.is_operator(first) && self.role(first).is_some_and(|r| r.0 == Role::Infix) {
            return statement;
        }
        if self.juxtaposed.contains(&first.start) {
            return None;
        }
        // The first line in brackets starts a statement.
        if self.stack.last().is_some_and(|o| o.start == previous.start) {
            return None;
        }
        let text = self.text(previous);
        // A label's `:` too: the statement it labels is next.
        let label = self
            .role(previous)
            .is_some_and(|r| r.1 == Operator::LowColon);
        // A comma between supertypes (`extends A,\n B`) continues the
        // header.
        let supertypes = self
            .role(previous)
            .is_some_and(|r| r.1 == Operator::ExtendsComma);
        let ends = match self.stack.last() {
            Some(o) if o.round => text == "," || (text == ";" && o.contents_own_line),
            _ => {
                label
                    || matches!(text, ";" | ";;" | "{" | "}" | "=>")
                    || (text == "," && !supertypes)
            }
        };
        if ends {
            return None;
        }
        statement
    }

    /// Whether a code token is an operator or punctuation other than a
    /// bracket, like `=`, `&&`, `.`, `->` or `,`, or a word operator in
    /// an expression, like `orelse`, `is` or `as`: a line starting with
    /// an infix one continues the statement. Comments never count.
    fn is_operator(&self, token: Token) -> bool {
        use Operator::*;
        let text = self.text(token);
        if token.kind == TokenKind::Word {
            return self.role(token).is_some_and(|r| {
                r.0 == Role::Infix && matches!(r.1, OrElse | In | Instanceof | Is | As)
            });
        }
        token.kind == TokenKind::Punctuation
            && !opens(text)
            && !closes(text)
            && !self
                .role(token)
                .is_some_and(|r| r.1 == Operator::Angle && matches!(r.0, Role::Open | Role::Close))
    }

    /// Writes a token, moving a multi-line block comment's later lines with
    /// its first, and tracks brackets and statements.
    fn token(&mut self, token: Token) {
        let text = self.text(token);
        if token.kind == TokenKind::Comment && text.contains('\n') && self.is_code(token) {
            let kept = self.kept[self.index];
            // Its first line's shift, if it starts the line.
            let shift = if self.line_first == token.start && !kept {
                self.line.formatted - self.line.actual
            } else {
                0
            };
            // A star gutter's lines put it one column past its `/`, as
            // written after it, unless a tab comes before it on its line,
            // whose width spaces can't match: then they stay as written.
            let before = &self.out[self.out.rfind('\n').map_or(0, |i| i + 1)..];
            let column = before.chars().count();
            let star = (has_gutter(text) && !kept && !before.contains('\t')).then_some(column + 1);
            // Its lines without trailing space, unless kept (or prose).
            let trim = !kept && starts_comment(text);
            let put = |out: &mut String, line: &str| match trim {
                true => push_trimmed(out, line),
                false => out.push_str(line),
            };
            let mut lines = text.split('\n');
            put(&mut self.out, lines.next().unwrap());
            for line in lines {
                self.out.push('\n');
                let indent = indentation(line);
                let rest = &line[indent.len()..];
                if let Some(star) = star.filter(|_| rest.starts_with('*')) {
                    self.out.extend(std::iter::repeat_n(' ', star));
                    put(&mut self.out, rest);
                    continue;
                }
                if shift == 0 || line.trim().is_empty() {
                    put(&mut self.out, line);
                    continue;
                }
                // Only space indents it here: a comment with tabs doesn't
                // move (`line_start`).
                for _ in 0..(indent.len() as i32 + shift).max(0) {
                    self.out.push(' ');
                }
                put(&mut self.out, rest);
            }
            return;
        }
        if token.kind == TokenKind::Comment
            && self.is_code(token)
            && starts_comment(text)
            && !self.kept[self.index]
        {
            push_trimmed(&mut self.out, text);
            return;
        }
        self.out.push_str(text);
        if !self.is_code(token) || token.kind == TokenKind::Comment {
            return;
        }
        if let Some(header) = self.header_lines.get_mut(&token.start) {
            *header = Some((self.line, self.stack.len()));
        }
        match token.kind {
            _ if self.opens_bracket(self.index) => {
                // From its own line if that started at this depth, but a
                // body's `{` on a header's continuation line from the
                // header's first line; else from the line that started its
                // statement here.
                let base = if self.line_depth != self.stack.len() {
                    self.statements.last().unwrap().unwrap_or(self.line)
                } else {
                    match self.line_header {
                        Some((header, h)) if self.bodies[&h] == token.start => header,
                        _ => self.line,
                    }
                };
                // A line break before its first code token, as written.
                let next = self.tokens.get(self.next_code[self.index + 1]);
                let contents = next.map_or(self.source.len(), |t| t.start as usize);
                let contents_own_line = self.source[token.end as usize..contents].contains('\n');
                self.stack.push(Open {
                    start: token.start,
                    base,
                    line: self.line,
                    paren: text.ends_with('('),
                    round: !text.ends_with('{'),
                    contents_own_line,
                });
                self.statements.push(Some(self.line));
            }
            _ if self.closes_bracket(self.index) => {
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

    /// Whether the token at `index` opens a bracket for indentation: `(`,
    /// `[`, `{` (`${` and `\u{` too), or the `<` of type arguments or
    /// parameters (other `<`s may be less-than).
    fn opens_bracket(&self, index: usize) -> bool {
        let token = self.tokens[index];
        (token.kind == TokenKind::Punctuation && opens(self.text(token)))
            || self.angle(index, Role::Open)
    }

    /// Whether the token at `index` closes a bracket for indentation, as
    /// [`Self::opens_bracket`] opens one.
    fn closes_bracket(&self, index: usize) -> bool {
        let token = self.tokens[index];
        (token.kind == TokenKind::Punctuation && closes(self.text(token)))
            || self.angle(index, Role::Close)
    }

    /// Whether the token at `index` is the `<` (`Role::Open`) or `>`
    /// (`Role::Close`) of type arguments or parameters.
    fn angle(&self, index: usize, role: Role) -> bool {
        self.tokens.get(index).is_some_and(|&t| {
            self.role(t) == Some((role, Operator::Angle)) && matches!(self.text(t), "<" | ">")
        })
    }

    fn text(&self, token: Token) -> &'a str {
        &self.source[token.range()]
    }

    fn role(&self, token: Token) -> Option<(Role, Operator)> {
        self.roles.get(&token.start).copied()
    }

    /// The space between two tokens on a line, or `None` to keep it.
    fn space(&self, a: Token, b: Token, gap: &str) -> Option<&'static str> {
        use Role::*;
        use Spacing::*;
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
        // Each token's role and operator, and its spacing (an object
        // literal's colon spaced as a type's).
        let (ra, rb) = (self.role(a), self.role(b));
        let spacing = |t: Token, r: Option<(Role, Operator)>| {
            let (role, op) = r?;
            let spacing = match self.properties.contains(&t.start) {
                true => After,
                false => op.spacing(self.text(t)),
            };
            Some((role, spacing))
        };
        let (sa, sb) = (spacing(a, ra), spacing(b, rb));
        let is = |s: Option<(Role, Spacing)>, role: Role, spacing| s == Some((role, spacing));
        // The first rule that applies decides; `None` keeps the space.
        let rules = [
            // A string's `${x}`: none inside, before a closer after a
            // separator too (`${a; }`), unlike other brackets.
            (
                ra == Some((Open, Operator::DollarCurly))
                    || rb == Some((Close, Operator::DollarCurly)),
                Some(""),
            ),
            // `,`, `;` and colons: none before, one after, but `,` and `;`
            // before a closer as written.
            (is(sb, Separator, After) || is(sb, Infix, After), Some("")),
            (is(sa, Separator, After), (!closes(tb)).then_some(" ")),
            (is(sa, Infix, Tight) || is(sb, Infix, Tight), Some("")),
            (is(sa, Infix, After), Some(" ")),
            (is(sa, Infix, Spaced) || is(sb, Infix, Spaced), Some(" ")),
            // `if (` and `fn (`, but `f(`.
            (
                rb == Some((Open, Operator::Paren)),
                (a.kind == Word).then_some(if SPACED_BEFORE_PAREN.contains(&ta) {
                    " "
                } else {
                    ""
                }),
            ),
            // `{ a }`, `f {`, but `({` and `{}` as written.
            (is(sb, Open, Spaced), (!opens(ta)).then_some(" ")),
            (is(sa, Open, Spaced), (!closes(tb)).then_some(" ")),
            (is(sb, Close, Spaced), Some(" ")),
            // `(a)`, `a[i]`, `List<T>`, `-a`.
            (
                is(sa, Open, Tight) || is(sb, Close, Tight) || is(sa, Prefix, Tight),
                Some(""),
            ),
            // Runs of spaces and tabs.
            (
                gap != " " && !gap.is_empty() && gap.bytes().all(|c| c == b' ' || c == b'\t'),
                Some(" "),
            ),
        ];
        let want = rules.into_iter().find(|rule| rule.0)?.1?;
        // Never respace tokens so they'd lex differently, as `- -x`.
        if want != gap && !lexes_alone(ta, want, tb) {
            return None;
        }
        Some(want)
    }
}

/// The line break a gap's line breaks are written with.
fn newline_of(gap: &str) -> &'static str {
    if gap.contains("\r\n") { "\r\n" } else { "\n" }
}

/// Whether a comment token is a comment in code, `//` or `/*`, not
/// markdown prose (in an indented code block's lines, as a list item's
/// marker).
fn starts_comment(text: &str) -> bool {
    text.starts_with("//") || text.starts_with("/*")
}

/// Writes a comment's line without the space and tabs at its end, but
/// with its `\r`.
fn push_trimmed(out: &mut String, line: &str) {
    let (body, cr) = line.strip_suffix('\r').map_or((line, ""), |b| (b, "\r"));
    out.push_str(body.trim_end_matches([' ', '\t']));
    out.push_str(cr);
}

/// Where object literals' (and object types') colons start, as in
/// `{ a: 1 }`: those of a `{ }` group's items. A block's statements, as
/// labels, aren't in one.
fn property_colons(parsed: &Parsed) -> HashSet<u32> {
    use Operator::*;
    let tree = parsed.tree();
    let mut colons = HashSet::new();
    let colon = |id: NodeId| {
        let tokens = || tree.children(id).iter().filter_map(|&c| parsed.token(c));
        (tree.kind(id) == NodeKind::Op(LowColon))
            .then(|| tokens().find(|t| !t.synthetic && parsed.token_text(*t) == ":"))
            .flatten()
            .map(|t| t.start)
    };
    for id in nodes(parsed) {
        if tree.kind(id) != NodeKind::Op(CurlyGroup) {
            continue;
        }
        for &c in tree.children(id) {
            let items = match tree.kind(c) {
                NodeKind::Op(Comma) => tree.children(c),
                _ => std::slice::from_ref(&c),
            };
            colons.extend(items.iter().filter_map(|&i| colon(i)));
        }
    }
    colons
}

/// Whether a comment is a doc comment over several lines, `/** ... */`,
/// with a star gutter: every line after its first that isn't blank starts
/// with `*` after its indentation, its closing `*/` included. Its gutter's
/// lines line up one column past its `/`; in one without a gutter, lines
/// starting with `*` (bullets, say) move with the rest.
fn has_gutter(comment: &str) -> bool {
    comment.starts_with("/**")
        && comment.contains('\n')
        && comment.split('\n').skip(1).all(|line| {
            let rest = &line[indentation(line).len()..];
            line.trim().is_empty() || rest.starts_with('*')
        })
}

/// Whether a multi-line block comment's lines can all move `shift`
/// columns with its first: none would go left of `margin` (its code
/// block's, in markdown) or is indented with tabs, which moving would mix
/// with spaces. A star gutter's lines follow its first.
fn comment_moves(comment: &str, shift: i32, margin: i32) -> bool {
    let gutter = has_gutter(comment);
    shift == 0
        || comment.split('\n').skip(1).all(|line| {
            let indent = indentation(line);
            line.trim().is_empty()
                || (gutter && line[indent.len()..].starts_with('*'))
                || (!indent.contains('\t') && indent.len() as i32 + shift >= margin)
        })
}

/// A line's indentation: the spaces and tabs it starts with, the only
/// space the formatter moves lines by (not other Unicode space).
fn indentation(line: &str) -> &str {
    &line[..line.len() - line.trim_start_matches([' ', '\t']).len()]
}

/// Whether a comment on a line of its own turns formatting off or on:
/// exactly `// fmt: off` or `// fmt: on`, but for space at its end.
fn fmt_marker(comment: &str) -> Option<bool> {
    match comment.trim_end() {
        "// fmt: off" => Some(true),
        "// fmt: on" => Some(false),
        _ => None,
    }
}

/// Whether `a`, `space` and `b` lex as just the tokens `a` and `b`. Space
/// changes only in code, where each token's lexing depends only on its
/// own text, the token before it, and whether `<` follows space (which
/// [`Formatter::space`] keeps), so the pair alone almost always tells, after
/// a value and after an operator both, as `/` divides after a value and
/// starts a regex after an operator (`x /  .y` mustn't become `x /.y`);
/// the check after formatting catches the rest.
fn lexes_alone(a: &str, space: &str, b: &str) -> bool {
    ["", "x "].iter().all(|before| {
        let text = format!("{before}{a}{space}{b}");
        let mut tokens = crate::lex::lex(&text, SourceKind::Standalone)
            .into_iter()
            .filter(|t| t.kind != TokenKind::Space && t.synthetic.is_none())
            .skip(usize::from(!before.is_empty()));
        let (Some(x), Some(y), None) = (tokens.next(), tokens.next(), tokens.next()) else {
            return false;
        };
        // Alone, brackets don't match, so inserted closers don't count.
        x.text(&text) == a && y.text(&text) == b
    })
}

/// A comment's text as formatting must keep it: its lines without
/// trailing space, and without the indentation (spaces and tabs) they
/// share, so they may move together but not apart; but a star gutter's
/// lines line up anew.
fn comment_shape(comment: &str) -> String {
    let gutter = has_gutter(comment);
    let lines: Vec<&str> = comment.split('\n').map(str::trim_end).collect();
    let starred = |l: &str| gutter && l[indentation(l).len()..].starts_with('*');
    let shared = (lines.iter().skip(1))
        .filter(|l| !l.is_empty() && !starred(l))
        .map(|l| indentation(l).len())
        .min()
        .unwrap_or(0);
    (lines.iter().enumerate())
        .map(|(i, &l)| match () {
            _ if i == 0 || l.is_empty() => l,
            _ if starred(l) => &l[indentation(l).len()..],
            _ => &l[shared..],
        })
        .collect::<Vec<_>>()
        .join("\n")
}

/// Checks that `text`, the formatted `original`, has the same tokens,
/// comments (as [`comment_shape`] has them), parse tree and markdown
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
                    comment_shape(&text)
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

/// Whether two trees have the same nodes and tokens, ignoring where they
/// are.
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
        // Code indented less, which turns it into prose.
        assert!(md("Prose.\n\n   let x = 1;\n").is_err());
    }

    /// The same tokens in a different tree: a line break before `{` ends
    /// the call, so it's no longer a block argument.
    #[test]
    fn check_refuses_a_different_tree() {
        let original = parse("f() { x }\n", SourceKind::Standalone);
        let ok = |text: &str| check_same(&original, text, SourceKind::Standalone);
        assert_eq!(ok("f() {\n  x\n}\n"), Ok(()));
        assert_eq!(
            ok("f()\n{ x }\n"),
            Err(FmtError::Unsafe("the parse tree differs".to_string()))
        );
        assert_eq!(
            ok("f() { x\n"),
            Err(FmtError::Unsafe("the result doesn't parse".to_string()))
        );
    }

    /// A comment's lines may move together, but not apart.
    #[test]
    fn check_keeps_comments_lines_relative_indentation() {
        let original = parse("/*\n  a\n    b\n*/\nx;\n", SourceKind::Standalone);
        let ok = |text: &str| check_same(&original, text, SourceKind::Standalone);
        assert_eq!(ok("  /*\n    a\n      b\n  */\nx;\n"), Ok(()));
        assert_eq!(ok("/*\n  a   \n    b\n*/\nx;\n"), Ok(()));
        assert!(ok("/*\n  a\n  b\n*/\nx;\n").is_err());
        assert!(ok("/*\n    a\n  b\n*/\nx;\n").is_err());
        // A star gutter's lines line up anew.
        let doc = parse(
            "/**\n   * a\n  *\n    * c\n */\nx;\n",
            SourceKind::Standalone,
        );
        let ok = |text: &str| check_same(&doc, text, SourceKind::Standalone);
        assert_eq!(ok("/**\n * a\n *\n * c\n */\nx;\n"), Ok(()));
        // Without a gutter (a line without a star), `*` lines are like any
        // other, as bullets.
        let doc = parse("/**\n   * a\n  b\n    c\n */\nx;\n", SourceKind::Standalone);
        let ok = |text: &str| check_same(&doc, text, SourceKind::Standalone);
        assert_eq!(ok("/**\n    * a\n   b\n     c\n  */\nx;\n"), Ok(()));
        assert!(ok("/**\n * a\n  b\n    c\n */\nx;\n").is_err());
        // Indentation is spaces and tabs only, other space is text.
        let wide = parse("/*\n\u{3000}a\n b\n*/\nx;\n", SourceKind::Standalone);
        let ok = |text: &str| check_same(&wide, text, SourceKind::Standalone);
        assert_eq!(ok("/*\n  \u{3000}a\n   b\n  */\nx;\n"), Ok(()));
        assert!(ok("/*\n\u{3000}a\nb\n*/\nx;\n").is_err());
    }
}
