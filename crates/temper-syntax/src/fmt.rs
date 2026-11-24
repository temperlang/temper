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
//!   another (statements with no `;` between them) or after a decorator line
//!   like `@connected` start statements, and comment lines go with the code
//!   after them.
//! - **Spacing on a line:** one space around infix operators (not `.`,
//!   `?.`, `..`, `::`, `<`/`>` of type arguments or label colons), after
//!   `,` and `;` and type colons, around `{ }` contents, and between a
//!   keyword like `if` or `fn` and `(`; none before `,`, `;` and type
//!   colons, inside `( )` and `[ ]` and a string's `${x}`, between a
//!   called name and `(`, or after prefix operators and `@`. Runs of
//!   spaces become one. Space next to comments, inside strings, and
//!   before `<` (which decides whether it may be an angle bracket) is
//!   left as written.
//! - **Lines:** no trailing space, blank lines as written but at most one
//!   in a row, none at the start of a file, and one line break at its end.
//! - **Lists** (arguments, parameters, list and object literals, type
//!   arguments, and single items in `( )`, `[ ]` and `< >`) are laid out
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
//! with the line they start on; their text is otherwise kept.
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
//! comments (apart from indentation), parse tree and, in markdown, prose;
//! if not, the formatter refuses rather than change meaning. Formatting is
//! idempotent: formatted code formats to itself (tested over every Temper
//! file in the repository).

use crate::lex::{SourceKind, Synthetic, Token, TokenKind};
use crate::literate::{self, Kind};
use crate::operator::{OpType, Operator};
use crate::parse::{Parsed, parse};
use crate::tree::{NodeId, NodeKind};
use std::collections::{HashMap, HashSet};
use std::fmt;

/// Spaces per indentation level.
const UNIT: i32 = 2;

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
    if let Some(why) = first_error(parsed) {
        return Err(FmtError::Unparseable(why));
    }
    let ranges = code_ranges(parsed.source(), kind);
    let text = Formatter::new(parsed, &ranges, kind, options).run();
    check_same(parsed, &text, kind)?;
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
/// and a token in nested headers gets the innermost.
fn headers(parsed: &Parsed, tokens: &[Token]) -> (HashMap<u32, u32>, HashMap<u32, u32>) {
    use Operator::*;
    let tree = parsed.tree();
    // Parents come off the stack before their children, so inner headers
    // overwrite outer ones.
    let mut starts = HashMap::new();
    let mut braces = HashMap::new();
    let mut stack = vec![parsed.root()];
    while let Some(id) = stack.pop() {
        let children = tree.children(id);
        stack.extend(children.iter().copied());
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
            starts.extend(tokens[from + 1..to].iter().map(|t| (t.start, start)));
            braces.insert(start, brace.start);
        }
    }
    (starts, braces)
}

/// Where nodes start that follow another node directly, as statements
/// without a `;` between them do (`f()` and `g()` on lines of their own).
fn juxtaposed(parsed: &Parsed) -> HashSet<u32> {
    let tree = parsed.tree();
    let mut starts = HashSet::new();
    let mut stack = vec![parsed.root()];
    while let Some(id) = stack.pop() {
        let children = tree.children(id);
        let is_node = |c: NodeId| tree.kind(c) != NodeKind::Token;
        for w in children.windows(2) {
            if is_node(w[0]) && is_node(w[1]) {
                starts.extend(first_token(parsed, w[1]));
            }
        }
        stack.extend(children.iter().copied().filter(|&c| is_node(c)));
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
/// statement). Its fields are indices in [`Formatter`]'s tokens.
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
    let mut stack = vec![parsed.root()];
    while let Some(id) = stack.pop() {
        let children = tree.children(id);
        stack.extend((children.iter()).filter(|&&c| tree.kind(c) != NodeKind::Token));
        let NodeKind::Op(Paren | ParenGroup | Square | SquareGroup | CurlyGroup | Angle) =
            tree.kind(id)
        else {
            continue;
        };
        for w in children.windows(3) {
            let (Some(open), Some(close)) = (parsed.token(w[0]), parsed.token(w[2])) else {
                continue;
            };
            let brackets = (parsed.token_text(open), parsed.token_text(close));
            let list = match tree.kind(w[1]) {
                NodeKind::Op(Comma) => {
                    matches!(brackets, ("(", ")") | ("[", "]") | ("{", "}") | ("<", ">"))
                }
                NodeKind::Token => false,
                _ => matches!(brackets, ("(", ")") | ("[", "]") | ("<", ">")),
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
    let (mut tokens, mut gaps, mut in_string) = (Vec::new(), Vec::new(), Vec::new());
    for &t in lexed.iter().filter(|t| t.kind != TokenKind::Space) {
        if t.synthetic.is_none() {
            tokens.push(t);
            gaps.push(frames.last() == Some(&Frame::Code));
            in_string.push(frames.contains(&Frame::String));
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
    /// For each token, the list it opens or closes, if any.
    opens: Vec<Option<usize>>,
    closes: Vec<Option<usize>>,
    /// For the gap before each token, the list that may break there.
    places: Vec<Option<(usize, Place)>>,
}

impl<'a> Formatter<'a> {
    fn new(
        parsed: &'a Parsed,
        ranges: &'a [CodeRange],
        kind: SourceKind,
        options: Options,
    ) -> Self {
        let (tokens, code_gaps, in_string) = code_gaps(parsed.source(), parsed.lexed());
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
            line_has_code: false,
            line_is_decorator: false,
            line_depth: 0,
            after_decorator: false,
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
            opens: Vec::new(),
            closes: Vec::new(),
            places: Vec::new(),
        };
        (formatter.headers, formatter.bodies) = headers(parsed, &formatter.tokens);
        formatter.header_lines = formatter.headers.values().map(|&h| (h, None)).collect();
        if options.lists {
            formatter.plan(parsed, &in_string);
        }
        formatter
    }

    /// Finds the lists and decides their layouts, inner lists first, so
    /// an outer list sees its items' line breaks as formatted.
    fn plan(&mut self, parsed: &Parsed, in_string: &[bool]) {
        let n = self.tokens.len();
        let index: HashMap<u32, usize> = (self.tokens.iter().enumerate())
            .map(|(i, t)| (t.start, i))
            .collect();
        self.lists = find_lists(parsed, &index);
        (self.opens, self.closes) = (vec![None; n], vec![None; n]);
        self.places = vec![None; n + 1];
        for (id, list) in self.lists.iter().enumerate() {
            self.opens[list.open] = Some(id);
            self.closes[list.close] = Some(id);
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
                || (list.open + 1..=list.close).any(|j| prose[j])
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
            return source.to_string();
        }
        // A copy, as other methods read `self.tokens` too.
        let tokens = self.tokens.clone();
        let mut end = 0;
        for (i, &token) in tokens.iter().enumerate() {
            self.index = i;
            let previous = i.checked_sub(1).map(|p| tokens[p]);
            let wanted = self.wanted_break();
            if self.code_gaps[i] || token.kind == TokenKind::Margin {
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

    /// Writes the space between `previous` (ending at `start`) and `next`:
    /// a line break if `wanted` is true, none if false, else as written.
    fn gap(&mut self, previous: Option<Token>, start: u32, next: Token, wanted: Option<bool>) {
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
                let formatted = self.line_start(next, None, false);
                for _ in 0..range.margin + formatted {
                    self.out.push(' ');
                }
                return;
            }
            _ => {}
        }
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
            // Blank lines are the author's; a run counts as one.
            let blank = breaks.saturating_sub(1).min(1);
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
        // and lines in markdown starting like a fence, which moving could
        // make close the block, and space before `<`, which decides if it
        // may be a bracket.
        let rest = &source[next.start as usize..];
        let fence = !self.standalone && (rest.starts_with("```") || rest.starts_with("~~~"));
        let keep = actual < 0
            || (!self.standalone && indent.contains('\t'))
            || fence
            || self.text(next) == "<";
        let formatted = self.line_start(next, Some(actual), keep);
        if keep {
            self.out.push_str(&indent[written as usize..]);
        } else {
            for _ in 0..range.margin + formatted - written {
                self.out.push(' ');
            }
        }
    }

    /// Starts a line whose first token is `first`, indented `actual`
    /// spaces past its margin (`None` for a line the formatter adds),
    /// returning its formatted indentation, which is `actual` if `keep`.
    fn line_start(&mut self, first: Token, written: Option<i32>, keep: bool) -> i32 {
        let actual = written.unwrap_or(0);
        if self.line_has_code {
            self.after_decorator = self.line_is_decorator && self.stack.len() == self.line_depth;
        }
        self.line_has_code = false;
        self.line_is_decorator = false;
        self.line_depth = self.stack.len();
        let text = self.text(first);
        let mut starts_statement = false;
        self.line_header = None;
        let formatted = if keep {
            actual
        } else if matches!(first.kind, TokenKind::Margin | TokenKind::QuotedString) {
            (actual + self.string_shift).max(0)
        } else if first.kind == TokenKind::Punctuation && matches!(text, ")" | "]" | "}")
            || self.wrapped_angle(self.closes.get(self.index))
        {
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
                (statement.formatted + actual - statement.actual).max(0)
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
        // Never left of the margin, under a line kept there (markdown).
        let formatted = if keep { formatted } else { formatted.max(0) };
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
        let actual = written.unwrap_or(formatted);
        self.line = Indent {
            actual,
            formatted,
            first: first.start,
        };
        if starts_statement {
            *self.statements.last_mut().unwrap() = Some(self.line);
        }
        self.line_first = first.start;
        formatted
    }

    /// The statement a line starting with `first` continues, if the code
    /// before it didn't end one, or it starts with an infix operator like
    /// `.` or `&&`; and the line's first code token (after comments). A `;`
    /// ends one in parentheses too if their contents start on a line of
    /// their own (`for (\n  a;\n  b;`), not in `for (a;\n  b;`.
    fn continued_statement(&mut self, first: Token) -> Option<(Indent, Token)> {
        let previous = self.previous?;
        // A comment line goes with the code after it: it continues the
        // statement if that does, but not before a close bracket or the
        // end.
        let first = if first.kind == TokenKind::Comment {
            let next = (self.index..self.tokens.len())
                .find(|&i| self.tokens[i].kind != TokenKind::Comment);
            match next {
                Some(i)
                    if !matches!(self.text(self.tokens[i]), ")" | "]" | "}")
                        && !self.wrapped_angle(self.closes.get(i)) =>
                {
                    self.tokens[i]
                }
                _ => return None,
            }
        } else {
            first
        };
        let statement = (*self.statements.last().unwrap()).map(|s| (s, first));
        if self.is_operator(first) && self.role(first).is_some_and(|r| r.0 == Role::Infix) {
            return statement;
        }
        if self.after_decorator || self.juxtaposed.contains(&first.start) {
            return None;
        }
        if let Some(top) = self.stack.last_mut() {
            if top.start == previous.start {
                top.contents_own_line = true;
                return None;
            }
        }
        let text = self.text(previous);
        // A label's `:` too: the statement it labels is next.
        let label = self
            .role(previous)
            .is_some_and(|r| r.1 == Operator::LowColon);
        let ends = match self.stack.last() {
            Some(o) if o.round => text == "," || (text == ";" && o.contents_own_line),
            _ => label || matches!(text, ";" | ";;" | "{" | "}" | "," | "=>"),
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
            && !text.ends_with(['(', '[', '{'])
            && !matches!(text, ")" | "]" | "}")
            && !self
                .role(token)
                .is_some_and(|r| r.1 == Operator::Angle && matches!(r.0, Role::Open | Role::Close))
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
        if let Some(header) = self.header_lines.get_mut(&token.start) {
            *header = Some((self.line, self.stack.len()));
        }
        match token.kind {
            TokenKind::Punctuation
                if text.ends_with(['(', '[', '{'])
                    || self.wrapped_angle(self.opens.get(self.index)) =>
            {
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
                self.stack.push(Open {
                    start: token.start,
                    base,
                    line: self.line,
                    paren: text.ends_with('('),
                    round: !text.ends_with('{'),
                    contents_own_line: false,
                });
                self.statements.push(Some(self.line));
            }
            TokenKind::Punctuation
                if matches!(text, ")" | "]" | "}")
                    || self.wrapped_angle(self.closes.get(self.index)) =>
            {
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

    /// Whether a list, the current token's if any, is type arguments or
    /// parameters, whose `<` and `>` count as brackets for indentation
    /// (others don't, as `<` may be less-than).
    fn wrapped_angle(&self, list: Option<&Option<usize>>) -> bool {
        list.copied()
            .flatten()
            .is_some_and(|id| self.text(self.tokens[self.lists[id].open]) == "<")
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
        let hole =
            |r: Option<(Role, Operator)>, role: Role| is(r, role) && op(r) == Some(DollarCurly);
        // The first rule that applies decides; `None` keeps the space.
        let rules = [
            // A string's `${x}`: none inside, unlike `{ a }`.
            (hole(ra, Role::Open) || hole(rb, Role::Close), Some("")),
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
            // `{ a }`, `f {`, but `({` and `{}` as written.
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
    }
}
