//! The grammar: from a parse tree to events that [`ast`](crate::ast) lifts
//! into calls, blocks, declarations and functions, as Kotlin Temper's
//! `Grammar.kt` does with its grammar combinators.
//!
//! The parse tree is flattened into *parts*: an open part for each operator
//! node, its tokens and children, then a close part. Productions match parts
//! from a position and return the position after them, or `None`. They're
//! tried in order, PEG-style, and emit events: a tree starts, a leaf, a tree
//! finishes with its kind, the last two trees swap (`x + y` emits `x`, then
//! `+`, then swaps them so the call's callee comes first), or an error over
//! some parts. A failed attempt drops what it emitted.
//!
//! Each production is a method named for Kotlin's, with Kotlin's definition
//! above it in its notation: `a y b` is a sequence, `a / b` the first of `a`
//! or `b` that matches, `opt`, `any` and `many` repeat, `(` and `)` are open
//! and close parts, an operator name alone looks ahead for its open part, a
//! quoted string matches a token's text, and `callTree(...)` and the like
//! wrap what's inside in a tree.
//!
//! Where Kotlin's grammar parses a whole operand only to fail after it,
//! which takes exponential time with nesting, some productions check the
//! failing condition first, with the same results: `prefix`, `infix`,
//! `tagged_string` and `decoration`. `Expr`s nest at most [`MAX_DEPTH`]
//! deep, to stay within the stack.

use crate::adapt::CommentKind;
use crate::ast::{Builtin, Message, Value};
use crate::lex::TokenKind;
use crate::names::{NameId, Names};
use crate::operator::{OpType, Operator};
use crate::parse::Parsed;
use crate::tree::{NodeId, Span};
use crate::unpack::{block_comment_content, line_comment_content, paragraph_comment_content};

/// A flattened parse tree element.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Part {
    pub kind: PartKind,
    /// For open and close parts, the node's operator.
    pub op: Operator,
    /// For tokens, the token's kind, flags and text in [`Grammar::text`].
    pub token: TokenKind,
    pub synthetic: bool,
    pub may_bracket: bool,
    pub text: (u32, u32),
    /// The node in the parse tree.
    pub node: NodeId,
    /// For tokens, their span; for open parts, the node's start, and for
    /// close parts its end.
    pub span: Span,
    /// For open parts, the index of their close part.
    pub close: u32,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum PartKind {
    Open,
    Close,
    Token,
}

/// What a finished tree becomes.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Fin {
    Block,
    Call,
    Decl,
    Esc,
    Fun,
    /// A block, or its only child if it has one.
    SoftBlock,
    /// A call to `,`, or its only child if it has one.
    SoftComma,
    LeftName,
    RightName,
    Value,
}

#[derive(Clone, Debug)]
pub(crate) enum Leaf {
    /// A token, by part index, read as a name or value by the tree kind.
    Token(u32),
    /// String content made from tokens.
    Quoted(String),
    Value(Value),
    Name(NameId, bool),
}

#[derive(Clone, Debug)]
pub(crate) enum Event {
    /// A tree starts, at a part for its origin.
    Start(Span, u32),
    Finish(Span, Fin),
    Leaf(Leaf, Span, u32),
    /// The last two trees swap.
    ShiftLeft,
    /// Parts from `first` to `last` inclusive don't parse.
    Error(Message, u32, u32),
}

/// A comment, with what doc comments need.
struct CommentInfo {
    start: u32,
    end: u32,
    kind: CommentKind,
    content: Option<String>,
    /// Whether it documents what follows: `/** */` and paragraphs.
    right: bool,
}

/// Interned symbols the grammar emits.
struct Symbols {
    word: NameId,
    out_type: NameId,
    super_: NameId,
    type_arg: NameId,
    type_: NameId,
    init: NameId,
    complex_arg: NameId,
    label: NameId,
    case: NameId,
    case_is: NameId,
    case_case: NameId,
    default: NameId,
    surprise_me: NameId,
    interpolate: NameId,
    safe_string_part: NameId,
    fun_string: NameId,
    flow_init: NameId,
    cond: NameId,
    incr: NameId,
    reifies: NameId,
    forbids: NameId,
    supports: NameId,
    as_: NameId,
    call_join: NameId,
}

type R = Option<usize>;

/// The most `Expr`s nested in each other, which takes about half a MiB of
/// stack in an optimized build, so it fits wasm's default of one MiB.
const MAX_DEPTH: u32 = 1000;

const CLOSE_BRACKETS: &[&str] = &[")", "}", "]", ">"];
const CLOSE_OR_COMMA: &[&str] = &[")", "}", "]", ">", ","];
const CLOSE_OR_SEMI: &[&str] = &[")", "}", "]", ">", ";"];
const RESERVED: &[&str] = &["nym", "builtins", "this", "super"];

use Operator as O;
const EXTENDS_LIKE: &[Operator] = &[
    O::ExtendsComma,
    O::ImplementsComma,
    O::ForbidsComma,
    O::SupportsComma,
    O::ExtendsNoComma,
    O::ImplementsNoComma,
    O::ForbidsNoComma,
    O::SupportsNoComma,
];
const EXTENDS_IMPLEMENTS: &[Operator] = &[
    O::ExtendsComma,
    O::ExtendsNoComma,
    O::ImplementsComma,
    O::ImplementsNoComma,
];
const FORBIDS: &[Operator] = &[O::ForbidsComma, O::ForbidsNoComma];
const SUPPORTS: &[Operator] = &[O::SupportsComma, O::SupportsNoComma];
const INFIX_COLONS: &[Operator] = &[O::LowColon, O::HighColon];

/// Tries alternatives in order, each from the same position, dropping what
/// a failed one emitted.
macro_rules! first {
    ($g:expr, $p:expr, $($alt:expr),+ $(,)?) => {{
        let g: &mut Grammar = $g;
        let p: usize = $p;
        let mut found = None;
        $(if found.is_none() { found = g.attempt(p, $alt); })+
        found
    }};
}

pub(crate) struct Grammar {
    pub parts: Vec<Part>,
    pub events: Vec<Event>,
    /// The parser's token texts, which parts refer to.
    pub text: String,
    pub names: Names,
    comments: Vec<CommentInfo>,
    /// Counts of decorations in `DecoratedLet`s being matched, innermost
    /// last.
    counters: Vec<u32>,
    /// How many `Expr`s are being matched, to stay within the stack.
    depth: u32,
    sym: Symbols,
}

impl Grammar {
    pub fn new(parsed: &Parsed) -> Grammar {
        let mut names = parsed.names().clone();
        let mut s = |text| names.intern(text);
        let sym = Symbols {
            word: s("word"),
            out_type: s("outType"),
            super_: s("super"),
            type_arg: s("typeArg"),
            type_: s("type"),
            init: s("init"),
            complex_arg: s("_complexArg_"),
            label: s("label"),
            case: s("case"),
            case_is: s("case_is"),
            case_case: s("case_case"),
            default: s("default"),
            surprise_me: s("..."),
            interpolate: s("interpolate"),
            safe_string_part: s("safeStringPart"),
            fun_string: s("funString"),
            flow_init: s("__flowInit"),
            cond: s("cond"),
            incr: s("incr"),
            reifies: s("reifies"),
            forbids: s("forbids"),
            supports: s("supports"),
            as_: s("as"),
            call_join: s("callJoin:"),
        };
        let source = parsed.source();
        let comments = parsed
            .comments()
            .iter()
            .map(|c| {
                let text = &source[c.start as usize..c.end as usize];
                let content = match c.kind {
                    CommentKind::Block => Some(block_comment_content(text)),
                    CommentKind::Line => Some(line_comment_content(text)),
                    CommentKind::SemilitParagraph => {
                        crate::lex::doc_comment(text).and_then(|t| paragraph_comment_content(&t))
                    }
                    CommentKind::Semilit => None,
                };
                let right = match c.kind {
                    CommentKind::Block => text.starts_with("/**"),
                    CommentKind::SemilitParagraph => true,
                    _ => false,
                };
                let content = content.filter(|c| !c.is_empty());
                CommentInfo {
                    start: c.start,
                    end: c.end,
                    kind: c.kind,
                    content,
                    right,
                }
            })
            .collect();
        Grammar {
            parts: Vec::new(),
            events: Vec::new(),
            text: parsed.adapted_text().to_string(),
            names,
            comments,
            counters: Vec::new(),
            depth: 0,
            sym,
        }
    }

    // Parts.

    pub fn part_text(&self, part: &Part) -> &str {
        &self.text[part.text.0 as usize..part.text.1 as usize]
    }

    fn part(&self, p: usize) -> Option<&Part> {
        self.parts.get(p)
    }

    /// The token at `p`, if it is one.
    fn token(&self, p: usize) -> Option<(&Part, &str)> {
        self.part(p)
            .filter(|part| part.kind == PartKind::Token)
            .map(|part| (part, self.part_text(part)))
    }

    /// Whether `p` opens an `op` node.
    fn is(&self, p: usize, op: Operator) -> bool {
        self.part(p)
            .is_some_and(|part| part.kind == PartKind::Open && part.op == op)
    }

    fn is_any(&self, p: usize, ops: &[Operator]) -> bool {
        self.part(p)
            .is_some_and(|part| part.kind == PartKind::Open && ops.contains(&part.op))
    }

    fn is_type(&self, p: usize, op_type: OpType) -> bool {
        self.part(p)
            .is_some_and(|part| part.kind == PartKind::Open && part.op.op_type() == op_type)
    }

    fn is_token(&self, p: usize, kind: TokenKind) -> bool {
        self.token(p).is_some_and(|(part, _)| part.token == kind)
    }

    fn is_synthetic(&self, p: usize) -> bool {
        self.token(p).is_some_and(|(part, _)| part.synthetic)
    }

    fn check(&self, p: usize, ok: bool) -> R {
        ok.then_some(p)
    }

    fn open(&self, p: usize) -> R {
        self.part(p)
            .is_some_and(|part| part.kind == PartKind::Open)
            .then_some(p + 1)
    }

    fn close(&self, p: usize) -> R {
        self.part(p)
            .is_some_and(|part| part.kind == PartKind::Close)
            .then_some(p + 1)
    }

    /// `Op y (`.
    fn open_op(&self, p: usize, op: Operator) -> R {
        self.is(p, op).then_some(p + 1)
    }

    /// A token with this text, not an error.
    fn tok(&self, p: usize, text: &str) -> R {
        self.token(p)
            .is_some_and(|(part, t)| t == text && part.token != TokenKind::Error)
            .then_some(p + 1)
    }

    /// The part after the token or node at `p`.
    fn next_sibling(&self, p: usize) -> usize {
        match self.part(p) {
            Some(part) if part.kind == PartKind::Open => part.close as usize + 1,
            _ => p + 1,
        }
    }

    /// Whether the node opened at `p` ends with a close bracket token. No
    /// `Expr` takes one, so productions ending in `Expr y )` can't match.
    /// Checking this first saves parsing operands only to fail after them,
    /// which with nesting took exponential time, as it does in Kotlin.
    fn ends_in_close_bracket(&self, p: usize) -> bool {
        let Some(open) = self.part(p).filter(|part| part.kind == PartKind::Open) else {
            return false;
        };
        let last = (open.close as usize).checked_sub(1);
        last.and_then(|i| self.token(i))
            .is_some_and(|(part, text)| {
                part.token != TokenKind::QuotedString && matches!(text, ")" | "}" | "]" | ">")
            })
    }

    fn eof(&self, p: usize) -> R {
        (p == self.parts.len()).then_some(p)
    }

    // Positions.

    /// Where a tree starting at `p` starts.
    fn before(&self, p: usize) -> Span {
        if let Some(part) = self.part(p) {
            edge(part.span.start)
        } else if p > 0 {
            edge(self.parts[p - 1].span.end)
        } else {
            Span::default()
        }
    }

    /// Where a tree ending before `p` ends.
    fn after(&self, p: usize) -> Span {
        if p > 0 {
            edge(self.parts[p - 1].span.end)
        } else if let Some(part) = self.part(p) {
            edge(part.span.start)
        } else {
            Span::default()
        }
    }

    /// Where something implied at `p` goes: the next part's start, or the
    /// previous part's end.
    fn implied_at(&self, p: usize, right: bool) -> Span {
        let look_right = p == 0 || (p < self.parts.len() && right);
        if look_right {
            self.parts
                .get(p)
                .map_or(Span::default(), |part| edge(part.span.start))
        } else {
            edge(self.parts[p - 1].span.end)
        }
    }

    /// A part index for the origin of something at `p`.
    fn origin(&self, p: usize) -> u32 {
        p.min(self.parts.len().saturating_sub(1)) as u32
    }

    // Combinators.

    /// Applies `f`, dropping its events if it fails.
    fn attempt(&mut self, p: usize, f: impl FnOnce(&mut Grammar, usize) -> R) -> R {
        let mark = self.events.len();
        let result = f(self, p);
        if result.is_none() {
            self.events.truncate(mark);
        }
        result
    }

    fn opt(&mut self, p: usize, f: impl FnOnce(&mut Grammar, usize) -> R) -> usize {
        self.attempt(p, f).unwrap_or(p)
    }

    /// One or more, stopping when an application makes no progress.
    fn many(&mut self, p: usize, f: impl Fn(&mut Grammar, usize) -> R) -> R {
        let mut before = p;
        let mut after = self.attempt(p, &f)?;
        while after > before {
            before = after;
            match self.attempt(before, &f) {
                Some(next) => after = next,
                None => break,
            }
        }
        Some(before)
    }

    /// Zero or more.
    fn any(&mut self, p: usize, f: impl Fn(&mut Grammar, usize) -> R) -> usize {
        self.many(p, f).unwrap_or(p)
    }

    /// Wraps what `f` matches in a tree.
    fn tree(&mut self, p: usize, fin: Fin, f: impl FnOnce(&mut Grammar, usize) -> R) -> R {
        let mark = self.events.len();
        self.events
            .push(Event::Start(self.before(p), self.origin(p)));
        match f(self, p) {
            Some(q) => {
                self.events.push(Event::Finish(self.after(q), fin));
                Some(q)
            }
            None => {
                self.events.truncate(mark);
                None
            }
        }
    }

    /// A tree that starts here and finishes later, outside this production.
    fn start_split(&mut self, p: usize) -> R {
        self.events
            .push(Event::Start(self.before(p), self.origin(p)));
        Some(p)
    }

    fn finish_split(&mut self, p: usize, fin: Fin) -> R {
        self.events.push(Event::Finish(self.before(p), fin));
        Some(p)
    }

    fn shift_left(&mut self, p: usize) -> R {
        self.events.push(Event::ShiftLeft);
        Some(p)
    }

    /// A leaf for no input: a symbol, value or name at `p`.
    fn implied(&mut self, p: usize, leaf: Leaf, fin: Fin) -> R {
        let at = self.implied_at(p, true);
        let origin = self.origin(p);
        self.events.push(Event::Start(at, origin));
        self.events.push(Event::Leaf(leaf, at, origin));
        self.events.push(Event::Finish(at, fin));
        Some(p)
    }

    fn implied_symbol(&mut self, p: usize, symbol: NameId) -> R {
        self.implied(p, Leaf::Value(Value::Symbol(symbol)), Fin::Value)
    }

    fn implied_builtin(&mut self, p: usize, builtin: Builtin) -> R {
        self.implied(p, Leaf::Value(Value::Builtin(builtin)), Fin::Value)
    }

    fn implied_name(&mut self, p: usize, name: &str) -> R {
        let name = self.names.intern(name);
        self.implied(p, Leaf::Name(name, true), Fin::RightName)
    }

    /// A value tree around a value implied at the next part, or with `left`
    /// the previous one.
    fn implied_value(&mut self, p: usize, value: Value, left: bool) -> R {
        self.tree(p, Fin::Value, |g, p| {
            let at = g.implied_at(p, !left);
            g.events
                .push(Event::Leaf(Leaf::Value(value), at, g.origin(p)));
            Some(p)
        })
    }

    /// A token with this text, replaced by `leaf` in a tree.
    fn replace(&mut self, p: usize, text: &str, fin: Fin, leaf: Leaf) -> R {
        let q = self.tok(p, text)?;
        self.tree(p, fin, |g, p| {
            let span = g.parts[p].span;
            g.events.push(Event::Leaf(leaf, span, p as u32));
            Some(q)
        })
    }

    /// `"text".asSymbol(symbol)`.
    fn as_symbol(&mut self, p: usize, text: &str, symbol: NameId) -> R {
        self.replace(p, text, Fin::Value, Leaf::Value(Value::Symbol(symbol)))
    }

    /// `"text".revalue(value)`.
    fn revalue(&mut self, p: usize, text: &str, value: Value) -> R {
        self.replace(p, text, Fin::Value, Leaf::Value(value))
    }

    /// `"text".rename(name)`, to a builtin name or a parsed one.
    fn rename(&mut self, p: usize, text: &str, name: &str, builtin: bool) -> R {
        let name = self.names.intern(name);
        self.replace(p, text, Fin::RightName, Leaf::Name(name, builtin))
    }

    /// `"text".asValue()`.
    fn as_value(&mut self, p: usize, text: &str) -> R {
        self.replace(
            p,
            text,
            Fin::Value,
            Leaf::Value(Value::String(text.to_string())),
        )
    }

    /// A token for which `test` holds, emitted as itself.
    fn emit_if(&mut self, p: usize, test: impl Fn(&Part, &str) -> bool) -> R {
        let (part, text) = self.token(p)?;
        if !test(part, text) {
            return None;
        }
        let span = part.span;
        self.events
            .push(Event::Leaf(Leaf::Token(p as u32), span, p as u32));
        Some(p + 1)
    }

    /// A tree of `fin` around a token for which `test` holds.
    fn token_tree(&mut self, p: usize, fin: Fin, test: impl Fn(&Part, &str) -> bool) -> R {
        self.tree(p, fin, |g, p| g.emit_if(p, test))
    }

    /// `name(unreservedWordMatcher)` and the like.
    fn unreserved(&mut self, p: usize, fin: Fin) -> R {
        self.token_tree(p, fin, |part, text| {
            part.token == TokenKind::Word && !RESERVED.contains(&text)
        })
    }

    /// `name("text")`, which doesn't exclude error tokens.
    fn name_text(&mut self, p: usize, name: &str) -> R {
        self.token_tree(p, Fin::RightName, |_, text| text == name)
    }

    fn any_token(&mut self, p: usize) -> R {
        self.token_tree(p, Fin::Value, |_, _| true)
    }

    /// Unparsable parts, up to a close bracket at this depth or a token in
    /// `stop_before`, or through one in `stop_after`.
    fn garbage(
        &mut self,
        p: usize,
        message: Message,
        stop_after: &[&str],
        stop_before: &[&str],
        require_some: bool,
    ) -> R {
        let mut depth = 0;
        let mut after = p;
        while let Some(part) = self.parts.get(after) {
            match part.kind {
                PartKind::Open => depth += 1,
                PartKind::Close if depth == 0 => break,
                PartKind::Close => depth -= 1,
                PartKind::Token if depth == 0 => {
                    let text = self.part_text(part);
                    if stop_after.contains(&text) {
                        after += 1;
                        break;
                    }
                    if stop_before.contains(&text) {
                        break;
                    }
                }
                PartKind::Token => {}
            }
            after += 1;
        }
        if after > p {
            self.events
                .push(Event::Error(message, p as u32, after as u32 - 1));
            return Some(after);
        }
        (!require_some).then_some(after)
    }

    fn unparsable(&mut self, p: usize, production: &'static str, require_some: bool) -> R {
        self.garbage(
            p,
            Message::Unparsable(production),
            &[],
            CLOSE_BRACKETS,
            require_some,
        )
    }

    /// Whether `{` at `p` holds properties rather than statements, from the
    /// token before it.
    fn is_bag_preceder(&self, p: usize) -> bool {
        let previous = self.parts[..p.min(self.parts.len())]
            .iter()
            .rev()
            .find(|part| part.kind == PartKind::Token);
        let Some(previous) = previous else {
            return true;
        };
        match self.part_text(previous) {
            "orelse" | "let" | "new" | "await" | "return" | "throw" | "try" | "yield" => true,
            "=>" => false,
            ">" => !previous.may_bracket,
            text if CLOSE_BRACKETS.contains(&text) || text == "!" || text == "?" => false,
            _ => previous.token != TokenKind::Word,
        }
    }

    /// Comments with content between the token before `p` and the part at
    /// `p`, by index.
    fn comments_at(&self, p: usize) -> std::ops::Range<usize> {
        let Some(part) = self.part(p) else {
            return 0..0;
        };
        let right = part.span.start;
        let left = self.parts[..p]
            .iter()
            .rev()
            .find(|part| part.kind == PartKind::Token)
            .map_or(0, |part| part.span.end);
        if left > right {
            return 0..0;
        }
        let begin = self.comments.partition_point(|c| c.start < left);
        let mut end = begin;
        while let Some(c) = self.comments.get(end) {
            if c.start < right && c.end <= right {
                end += 1;
            } else {
                break;
            }
        }
        begin..end
    }

    /// `EmbeddedComment`: a call for each comment before `p`.
    fn embedded_comments(&mut self, p: usize) -> R {
        let origin = self.origin(p);
        for i in self.comments_at(p) {
            let c = &self.comments[i];
            let Some(content) = c.content.clone() else {
                continue;
            };
            let (left, right) = (edge(c.start), edge(c.end));
            let full = Span {
                start: c.start,
                end: c.end,
            };
            let right_value = if c.right {
                Value::Bool(true)
            } else {
                Value::Null
            };
            let paragraph = Value::Bool(c.kind == CommentKind::SemilitParagraph);
            self.events.push(Event::Start(left, origin));
            self.leaf_tree(
                left,
                left,
                Value::Builtin(Builtin::EmbeddedComment),
                left,
                origin,
            );
            self.leaf_tree(left, full, Value::String(content), right, origin);
            self.leaf_tree(left, right, right_value, right, origin);
            self.leaf_tree(left, right, paragraph, right, origin);
            self.events.push(Event::Finish(right, Fin::Call));
        }
        Some(p)
    }

    fn leaf_tree(&mut self, start: Span, at: Span, value: Value, finish: Span, origin: u32) {
        self.events.push(Event::Start(start, origin));
        self.events
            .push(Event::Leaf(Leaf::Value(value), at, origin));
        self.events.push(Event::Finish(finish, Fin::Value));
    }

    /// Wraps what `f` matches in `@docComment(..., ["doc"], paragraph)` if
    /// documentation comments precede it.
    fn with_doc_comment(&mut self, p: usize, f: impl FnOnce(&mut Grammar, usize) -> R) -> R {
        let before: Vec<usize> = self
            .comments_at(p)
            .filter(|&i| self.comments[i].content.is_some() && self.comments[i].right)
            .collect();
        let mark = self.events.len();
        let after = f(self, p)?;
        if after <= p || before.is_empty() {
            return Some(after);
        }
        let paragraphs = before
            .iter()
            .all(|&i| self.comments[i].kind == CommentKind::SemilitParagraph);
        let docs: Vec<usize> = before
            .into_iter()
            .filter(|&i| paragraphs || self.comments[i].kind != CommentKind::SemilitParagraph)
            .collect();
        let (Some(&first), Some(&last)) = (docs.first(), docs.last()) else {
            return Some(after);
        };
        let left = edge(self.comments[first].start);
        let right = edge(self.comments[last].end);
        let all = Span {
            start: self.comments[first].start,
            end: self.comments[last].end,
        };
        let origin = self.origin(p);
        let start = vec![
            Event::Start(left, origin),
            Event::Start(left, origin),
            Event::Leaf(
                Leaf::Value(Value::Builtin(Builtin::DocComment)),
                left,
                origin,
            ),
            Event::Finish(left, Fin::Value),
        ];
        self.events.splice(mark..mark, start);
        let texts = docs
            .iter()
            .map(|&i| self.comments[i].content.clone().unwrap())
            .collect();
        self.leaf_tree(left, all, Value::Strings(texts), left, origin);
        self.leaf_tree(left, left, Value::Bool(paragraphs), left, origin);
        self.events.push(Event::Finish(right, Fin::Call));
        Some(after)
    }

    // Productions.

    /// Root := ((Root y ( y TopLevels y )) / TopLevels) y Eof
    pub fn root(&mut self, p: usize) -> R {
        let p = first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Root)?;
                let p = g.top_levels(p);
                g.close(p)
            },
            |g, p| Some(g.top_levels(p)),
        )?;
        self.eof(p)
    }

    /// TopLevels := any((Semi y ( y TopLevelsInSemi y )) / TopLevel)
    fn top_levels(&mut self, p: usize) -> usize {
        self.any(p, |g, p| {
            first!(
                g,
                p,
                |g, p| {
                    let p = g.open_op(p, O::Semi)?;
                    let p = g.top_levels_in_semi(p);
                    g.close(p)
                },
                Grammar::top_level,
            )
        })
    }

    /// TopLevelsInSemi := any(any(Nop) y TopLevel) y TrailingSemi
    fn top_levels_in_semi(&mut self, p: usize) -> usize {
        let p = self.any(p, |g, p| {
            let p = g.any(p, Grammar::nop);
            g.top_level(p)
        });
        self.trailing_semi(p)
    }

    /// TopLevel := TopLevelNoGarbage
    ///   / (( y Garbage("TopLevel", stopAfter ";", stopBefore "}") y ))
    ///   / Garbage("TopLevel")
    fn top_level(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            Grammar::top_level_no_garbage,
            |g, p| {
                let p = g.open(p)?;
                let p = g.garbage(p, Message::Unparsable("TopLevel"), &[";"], &["}"], true)?;
                g.close(p)
            },
            |g, p| g.unparsable(p, "TopLevel", true),
        )
    }

    /// TopLevelNoGarbage := opt(EmbeddedComment) y TopLevelNoGarbageNoComment
    fn top_level_no_garbage(&mut self, p: usize) -> R {
        let p = self.opt(p, Grammar::embedded_comments);
        self.top_level_no_garbage_no_comment(p)
    }

    /// TopLevelNoGarbageNoComment := DecoratedTopLevel / Let / Stmt / QuasiHole
    fn top_level_no_garbage_no_comment(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            Grammar::decorated_top_level,
            Grammar::let_,
            Grammar::stmt,
            Grammar::quasi_hole
        )
    }

    /// TrailingSemi := opt(";".revalue(void)) y any(Nop)
    fn trailing_semi(&mut self, p: usize) -> usize {
        let p = self.opt(p, |g, p| g.revalue(p, ";", Value::Void));
        self.any(p, Grammar::nop)
    }

    /// Nop := ";"
    fn nop(&mut self, p: usize) -> R {
        self.tok(p, ";")
    }

    /// Stmt := LabeledStmt / MatchBranch / StmtBlock / StringGroupSynthetic / CommaExpr
    fn stmt(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            Grammar::labeled_stmt,
            Grammar::match_branch,
            Grammar::stmt_block,
            Grammar::string_group_synthetic,
            Grammar::comma_expr,
        )
    }

    /// StmtBlock := CurlyGroup y ( y RawBlock y )
    fn stmt_block(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::CurlyGroup)?;
        let p = self.raw_block(p)?;
        self.close(p)
    }

    /// LabeledStmt := blockTree(LowColon y ( y ( y (LeftLabel / QuasiHole) y ) y
    ///   ":".asSymbol(label) y shiftLeft y (Call / StmtBlock) y ))
    fn labeled_stmt(&mut self, p: usize) -> R {
        self.tree(p, Fin::Block, |g, p| {
            let p = g.open_op(p, O::LowColon)?;
            let p = g.open(p)?;
            let p = first!(
                g,
                p,
                |g, p| g.unreserved(p, Fin::LeftName),
                Grammar::quasi_hole
            )?;
            let p = g.close(p)?;
            let p = g.as_symbol(p, ":", g.sym.label)?;
            let p = g.shift_left(p)?;
            let p = first!(g, p, Grammar::call, Grammar::stmt_block)?;
            g.close(p)
        })
    }

    /// DecoratedTopLevel := DecoratedLet / decorated(TopLevelNoGarbageNoComment)
    fn decorated_top_level(&mut self, p: usize) -> R {
        first!(self, p, Grammar::decorated_let, |g, p| g
            .decorated_call(p, Grammar::top_level_no_garbage_no_comment))
    }

    /// Call := callTree((CallJoin y ( y CallHead y "callJoin:".asSymbol(callJoin) y
    ///   CallTail y )) / CallHead)
    fn call(&mut self, p: usize) -> R {
        self.tree(p, Fin::Call, |g, p| {
            first!(
                g,
                p,
                |g, p| {
                    let p = g.open_op(p, O::CallJoin)?;
                    let p = g.call_head(p)?;
                    let p = g.as_symbol(p, "callJoin:", g.sym.call_join)?;
                    let p = g.call_tail(p)?;
                    g.close(p)
                },
                Grammar::call_head,
            )
        })
    }

    /// CallHead := (Curly y ( y CalleeAndArgs y BlockLambda y ))
    ///   / ((HighColon / Paren) y CalleeAndRequiredArgs)
    fn call_head(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Curly)?;
                let p = g.callee_and_args(p)?;
                let p = g.block_lambda(p)?;
                g.close(p)
            },
            |g, p| {
                let p = g.check(p, g.is_any(p, &[O::HighColon, O::Paren]))?;
                g.callee_and_required_args(p)
            },
        )
    }

    /// `((Paren y ( y CallJoiningWords y CallArgs y )) / CallJoiningWords)`
    fn joining_words_and_args(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Paren)?;
                let p = g.call_joining_words(p)?;
                let p = g.call_args(p)?;
                g.close(p)
            },
            Grammar::call_joining_words,
        )
    }

    /// CallTail := (CallJoin y ( y Curly y ( y (Paren-words-args / CallJoiningWords) y
    ///     BlockLambda y ) y "callJoin:".asSymbol(callJoin) y CallTail y ))
    ///   / (Curly y ( y (Paren-words-args / CallJoiningWords) y BlockLambda y ))
    ///   / (Paren y ( y CallJoiningWords y CallArgs y ))
    fn call_tail(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::CallJoin)?;
                let p = g.open_op(p, O::Curly)?;
                let p = g.joining_words_and_args(p)?;
                let p = g.block_lambda(p)?;
                let p = g.close(p)?;
                let p = g.as_symbol(p, "callJoin:", g.sym.call_join)?;
                let p = g.call_tail(p)?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::Curly)?;
                let p = g.joining_words_and_args(p)?;
                let p = g.block_lambda(p)?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::Paren)?;
                let p = g.call_joining_words(p)?;
                let p = g.call_args(p)?;
                g.close(p)
            },
        )
    }

    /// `(HighColon y ( y Inner y ":".asSymbol(outType) y Type y ))`
    fn with_out_type(&mut self, p: usize, inner: fn(&mut Grammar, usize) -> R) -> R {
        let p = self.open_op(p, O::HighColon)?;
        let p = inner(self, p)?;
        let p = self.as_symbol(p, ":", self.sym.out_type)?;
        let p = self.expr(p)?;
        self.close(p)
    }

    /// `(Paren y ( y Callee y CallArgs y ))`
    fn callee_with_args(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::Paren)?;
        let p = self.callee(p)?;
        let p = self.call_args(p)?;
        self.close(p)
    }

    /// CalleeAndArgs := (HighColon y ( y CalleeAndArgs y ":".asSymbol(outType) y Type y ))
    ///   / extendsLikeModifiers(CalleeAndArgs) / (Paren y ( y Callee y CallArgs y )) / Callee
    fn callee_and_args(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| g.with_out_type(p, Grammar::callee_and_args),
            |g, p| g.extends_like_modifiers(p, Grammar::callee_and_args),
            Grammar::callee_with_args,
            Grammar::callee,
        )
    }

    /// CalleeAndRequiredArgs := (HighColon y ( y CalleeAndRequiredArgs y
    ///   ":".asSymbol(outType) y Type y )) / (Paren y ( y Callee y CallArgs y ))
    fn callee_and_required_args(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| g.with_out_type(p, Grammar::callee_and_required_args),
            Grammar::callee_with_args,
        )
    }

    /// CallJoiningWords := CompactWordsToSymbol(Leaf y ( y many(name(unreservedWordMatcher)) y ))
    ///
    /// Joins words like `else if` into the symbol `else_if`.
    fn call_joining_words(&mut self, p: usize) -> R {
        let mark = self.events.len();
        let after = {
            let p = self.open_op(p, O::Leaf)?;
            let p = self.many(p, |g, p| g.unreserved(p, Fin::RightName))?;
            self.close(p)?
        };
        let mut joined = String::new();
        let mut started = false;
        let mut span: Option<Span> = None;
        for event in &self.events[mark..] {
            let at = match event {
                Event::Start(at, _) if !started => {
                    started = true;
                    *at
                }
                Event::Finish(at, Fin::RightName) if started => {
                    started = false;
                    *at
                }
                Event::Leaf(Leaf::Token(t), at, _) if started => {
                    if !joined.is_empty() {
                        joined.push('_');
                    }
                    joined.push_str(self.part_text(&self.parts[*t as usize]));
                    *at
                }
                _ => return None,
            };
            span = Some(span.map_or(at, |s: Span| s.cover(at)));
        }
        let span = span.filter(|_| !started && !joined.is_empty())?;
        let origin = self.origin(p);
        self.events.truncate(mark);
        let symbol = self.names.intern(&joined);
        self.leaf_tree(
            edge(span.start),
            span,
            Value::Symbol(symbol),
            edge(span.end),
            origin,
        );
        Some(after)
    }

    /// Callee := (Leaf y ( y Id y wordSymbol y leftName(unreservedWordMatcher) y ))
    ///   / (Angle y ( y Callee y TypeArguments y )) / Expr
    fn callee(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            Grammar::error_case_callee,
            |g, p| {
                let p = g.open_op(p, O::Leaf)?;
                let p = g.id(p)?;
                let p = g.implied_symbol(p, g.sym.word)?;
                let p = g.unreserved(p, Fin::LeftName)?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::Angle)?;
                let p = g.callee(p)?;
                let p = g.type_arguments(p)?;
                g.close(p)
            },
            Grammar::expr,
        )
    }

    /// ErrorCaseCallee := Leaf y ( y "error".revalue(errorCase) y "case" y wordSymbol y
    ///   leftName(unreservedWordMatcher) y )
    ///
    /// Ours (RFC 164): `error case Name`, an error class declaration, as
    /// `class Name` is a class's. `error` and `case` stay words elsewhere.
    fn error_case_callee(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::Leaf)?;
        let p = self.revalue(p, "error", Value::Builtin(Builtin::ErrorCase))?;
        let p = self.tok(p, "case")?;
        let p = self.implied_symbol(p, self.sym.word)?;
        let p = self.unreserved(p, Fin::LeftName)?;
        self.close(p)
    }

    /// ErrorCase := callTree(ErrorCaseCallee), for `error case Name;`
    /// without parentheses, which a class needs.
    fn error_case(&mut self, p: usize) -> R {
        self.tree(p, Fin::Call, Grammar::error_case_callee)
    }

    /// Catch := Catch y callTree(( y Expr y "catch".rename(catch) y shiftLeft y
    ///     (Curly y ( y (ParenGroup y ( y "(" y (Leaf y ( y leftName(unreservedWordMatcher) y ))
    ///       y ")" y )) y
    ///       funTree(RawBlock) y )) y ))
    ///
    /// Ours (RFC 164): `f() catch (e) { … }`, as `catch(f(), ^e, fn { … })`.
    /// The block is a function's only as an `if`'s is: jumps in it leave
    /// the enclosing function or loop. Anything else after `catch` is an
    /// error over the whole expression, a block without a name too, which
    /// is `orelse`'s.
    fn catch(&mut self, p: usize) -> R {
        let p = self.check(p, self.is(p, O::Catch))?;
        first!(
            self,
            p,
            |g, p| {
                g.tree(p, Fin::Call, |g, p| {
                    let p = g.open(p)?;
                    let p = g.expr(p)?;
                    let p = g.rename(p, "catch", "catch", false)?;
                    let p = g.shift_left(p)?;
                    let p = g.open_op(p, O::Curly)?;
                    let p = g.open_op(p, O::ParenGroup)?;
                    let p = g.tok(p, "(")?;
                    let p = g.open_op(p, O::Leaf)?;
                    let p = g.unreserved(p, Fin::LeftName)?;
                    let p = g.close(p)?;
                    let p = g.tok(p, ")")?;
                    let p = g.close(p)?;
                    let p = g.tree(p, Fin::Fun, Grammar::raw_block)?;
                    let p = g.close(p)?;
                    g.close(p)
                })
            },
            |g, p| {
                // Only for what's after `catch`: a left operand that
                // doesn't parse, or none, is left to other productions.
                let mark = g.events.len();
                let q = g.expr(p + 1);
                g.events.truncate(mark);
                let q = g.tok(q?, "catch")?;
                let close = g.parts[p].close as usize;
                let message = match g.leftmost_block(q) {
                    Some((O::CurlyGroup, _)) => Message::CatchWithoutName,
                    Some((_, true)) => Message::CatchBlockNotLast,
                    _ => Message::CatchWithoutBlock,
                };
                g.events.push(Event::Error(message, p as u32, close as u32));
                Some(close + 1)
            },
        )
    }

    /// The block at `p`, or leftmost under infix and postfix operators
    /// there, however deep (a bare one, `CurlyGroup`, or a call ending in
    /// one, `Curly`), and whether it's under an operator: what follows
    /// `catch`, wrongly, if not a name and a block alone.
    fn leftmost_block(&self, p: usize) -> Option<(O, bool)> {
        let mut q = p;
        while let Some(part) = self.part(q).filter(|part| part.kind == PartKind::Open) {
            if matches!(part.op, O::Curly | O::CurlyGroup) {
                return Some((part.op, q != p));
            }
            if !matches!(part.op.op_type(), OpType::Infix | OpType::Postfix) {
                return None;
            }
            q += 1;
        }
        None
    }

    /// CallArgs := "(" y !QuotedGroup y (ForArgs / opt(Args)) y ")"
    fn call_args(&mut self, p: usize) -> R {
        let p = self.tok(p, "(")?;
        let p = self.check(p, !self.is(p, O::QuotedGroup))?;
        let p = first!(self, p, Grammar::for_args, |g, p| Some(
            g.opt(p, Grammar::args)
        ))?;
        self.tok(p, ")")
    }

    /// MatchBranch := (ThinArrow y ( y Leaf y ( y "else".asSymbol(default) y ) y "->" y Expr y ))
    ///   / (ThinArrow y ( y MatchCase y "->" y Expr y ))
    ///   / (ThinArrow y ( y Comma y ( y MatchCase y any("," y MatchCase) y opt(",") y ) y
    ///     "->" y Expr y ))
    fn match_branch(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::ThinArrow)?;
                let p = g.open_op(p, O::Leaf)?;
                let p = g.as_symbol(p, "else", g.sym.default)?;
                let p = g.close(p)?;
                let p = g.tok(p, "->")?;
                let p = g.expr(p)?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::ThinArrow)?;
                let p = g.match_case(p)?;
                let p = g.tok(p, "->")?;
                let p = g.expr(p)?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::ThinArrow)?;
                let p = g.open_op(p, O::Comma)?;
                let p = g.match_case(p)?;
                let p = g.any(p, |g, p| {
                    let p = g.tok(p, ",")?;
                    g.match_case(p)
                });
                let p = g.opt(p, |g, p| g.tok(p, ","));
                let p = g.close(p)?;
                let p = g.tok(p, "->")?;
                let p = g.expr(p)?;
                g.close(p)
            },
        )
    }

    /// MatchCase := (PreIs y ( y "is".asSymbol(caseIs) y Expr y ))
    ///   / (PreCase y ( y impliedValue(\case_case) y callTree("case".revalue(postponedCase) y
    ///     callTree(impliedValue(list) y BalancedTokenList) y )))
    ///   / (impliedValue(\case) y Expr)
    fn match_case(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::PreIs)?;
                let p = g.as_symbol(p, "is", g.sym.case_is)?;
                let p = g.expr(p)?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::PreCase)?;
                let p = g.implied_value(p, Value::Symbol(g.sym.case_case), false)?;
                g.tree(p, Fin::Call, |g, p| {
                    let p = g.revalue(p, "case", Value::Builtin(Builtin::PostponedCase))?;
                    let p = g.tree(p, Fin::Call, |g, p| {
                        let p = g.implied_value(p, Value::Builtin(Builtin::List), false)?;
                        Some(g.balanced_token_list(p))
                    })?;
                    g.close(p)
                })
            },
            |g, p| {
                let p = g.implied_value(p, Value::Symbol(g.sym.case), false)?;
                g.expr(p)
            },
        )
    }

    /// BalancedTokenList := any((( y BalancedTokenList y )) / value(any token))
    fn balanced_token_list(&mut self, p: usize) -> usize {
        self.any(p, |g, p| {
            first!(
                g,
                p,
                |g, p| {
                    let p = g.open(p)?;
                    let p = g.balanced_token_list(p);
                    g.close(p)
                },
                Grammar::any_token,
            )
        })
    }

    /// ForArgs := (Semi y ( y opt(ForInit) y ";" y opt(ForCond) y ";" y opt(ForIncr) y ))
    ///   / (SemiSemi y ( y opt(ForInit) y ";;" y opt(ForIncr) y ))
    fn for_args(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Semi)?;
                let p = g.opt(p, Grammar::for_init);
                let p = g.tok(p, ";")?;
                let p = g.opt(p, |g, p| g.symbol_then_comma_expr(p, g.sym.cond));
                let p = g.tok(p, ";")?;
                let p = g.opt(p, |g, p| g.symbol_then_comma_expr(p, g.sym.incr));
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::SemiSemi)?;
                let p = g.opt(p, Grammar::for_init);
                let p = g.tok(p, ";;")?;
                let p = g.opt(p, |g, p| g.symbol_then_comma_expr(p, g.sym.incr));
                g.close(p)
            },
        )
    }

    /// ForInit := flowInitSymbol y (ForInitLet / CommaExpr)
    fn for_init(&mut self, p: usize) -> R {
        let p = self.implied_symbol(p, self.sym.flow_init)?;
        first!(self, p, Grammar::for_init_let, Grammar::comma_expr)
    }

    /// ForInitLet := softCommaTree(Let) / softCommaTree(DecoratedLet)
    fn for_init_let(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| g.tree(p, Fin::SoftComma, Grammar::let_),
            |g, p| g.tree(p, Fin::SoftComma, Grammar::decorated_let),
        )
    }

    /// ForCond := condSymbol y CommaExpr, and ForIncr := incrSymbol y CommaExpr
    fn symbol_then_comma_expr(&mut self, p: usize, symbol: NameId) -> R {
        let p = self.implied_symbol(p, symbol)?;
        self.comma_expr(p)
    }

    /// AwaitReturnThrowYield := {Await, Return, Throw, Try, Yield} y callTree(( y
    ///   (name("await") / name("return") / name("throw") / name("try") / name("yield")) y
    ///   opt(Expr / Garbage("Argument", stopBefore closeBrackets + ";")) y ))
    ///
    /// The Expr recovers from a missing operator ([`operand`](Self::operand)).
    /// `try` is ours (RFC 164).
    fn await_return_throw_yield(&mut self, p: usize) -> R {
        let p = self.check(
            p,
            self.is_any(p, &[O::Await, O::Return, O::Throw, O::Try, O::Yield]),
        )?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = first!(
                g,
                p,
                |g, p| g.name_text(p, "await"),
                |g, p| g.name_text(p, "return"),
                |g, p| g.name_text(p, "throw"),
                |g, p| g.name_text(p, "try"),
                |g, p| g.name_text(p, "yield"),
            )?;
            let p = g.opt(p, |g, p| {
                first!(g, p, |g, p| g.operand(p, ";"), |g, p| g.garbage(
                    p,
                    Message::Unparsable("Argument"),
                    &[],
                    CLOSE_OR_SEMI,
                    true
                ))
            });
            g.close(p)
        })
    }

    /// Jump := callTree(( y (name("break") / name("continue")) y opt(LabelOrHole) y ))
    /// LabelOrHole := labelSymbol y (Label / QuasiHole)
    fn jump(&mut self, p: usize) -> R {
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = first!(g, p, |g, p| g.name_text(p, "break"), |g, p| g
                .name_text(p, "continue"))?;
            let p = g.opt(p, |g, p| {
                let p = g.implied_symbol(p, g.sym.label)?;
                first!(
                    g,
                    p,
                    |g, p| g.unreserved(p, Fin::RightName),
                    Grammar::quasi_hole
                )
            });
            g.close(p)
        })
    }

    /// TaggedString := {Paren, Curly} y callTree(( y impliedValue(stringExpr) y Expr y
    ///   syntheticToken y impliedValue(true) y StringGroupTagged y ))
    fn tagged_string(&mut self, p: usize) -> R {
        let p = self.check(p, self.is_any(p, &[O::Paren, O::Curly]))?;
        // A string follows the tag. Checked first, as for `prefix`.
        self.check(p, self.is_synthetic(self.next_sibling(p + 1)))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.implied_value(p, Value::Builtin(Builtin::StringExpr), false)?;
            let p = g.expr(p)?;
            let p = g.check(p, g.is_synthetic(p))?;
            let p = g.implied_value(p, Value::Bool(true), false)?;
            let p = g.string_group(p, true)?;
            g.close(p)
        })
    }

    /// BlockLambda := blockPreceder y funTree(
    ///     ("{" y Arrow y ( y BlockLambdaSignatureAndSupers y "=>" y BlockLambdaBody y ) y "}")
    ///   / ("{" y Arrow y ( y Formals y "=>" y BlockLambdaBody y ) y "}")
    ///   / RawBlock)
    fn block_lambda(&mut self, p: usize) -> R {
        let p = self.check(p, !self.is_bag_preceder(p))?;
        self.tree(p, Fin::Fun, |g, p| {
            first!(
                g,
                p,
                |g, p| g.block_lambda_with(p, Grammar::block_lambda_signature_and_supers),
                |g, p| g.block_lambda_with(p, Grammar::formals),
                Grammar::raw_block,
            )
        })
    }

    fn block_lambda_with(&mut self, p: usize, signature: fn(&mut Grammar, usize) -> R) -> R {
        let p = self.tok(p, "{")?;
        let p = self.open_op(p, O::Arrow)?;
        let p = signature(self, p)?;
        let p = self.tok(p, "=>")?;
        let p = self.block_lambda_body(p)?;
        let p = self.close(p)?;
        self.tok(p, "}")
    }

    /// BlockLambdaSignatureAndSupers := (extendsLikeOperators y ( y
    ///     BlockLambdaSignatureAndSupers y BlockLambdaSupers y ))
    ///   / BlockLambdaSignature / Garbage("BlockLambdaSignature", stopBefore "}")
    fn block_lambda_signature_and_supers(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.check(p, g.is_any(p, EXTENDS_LIKE))?;
                let p = g.open(p)?;
                let p = g.block_lambda_signature_and_supers(p)?;
                let p = g.block_lambda_supers(p)?;
                g.close(p)
            },
            Grammar::block_lambda_signature,
            |g, p| g.garbage(
                p,
                Message::Unparsable("BlockLambdaSignature"),
                &[],
                &["}"],
                false
            ),
        )
    }

    /// BlockLambdaSignature := (HighColon y ( y ParenGroup y ( y "(" y opt(Formals) y ")" y ) y
    ///     ":".asSymbol(outType) y Type y ))
    ///   / (ParenGroup y ( y "(" y ((Ellipsis y ( y "..." y )) / opt(Formals)) y ")" y ))
    fn block_lambda_signature(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::HighColon)?;
                let p = g.open_op(p, O::ParenGroup)?;
                let p = g.tok(p, "(")?;
                let p = g.opt(p, Grammar::formals);
                let p = g.tok(p, ")")?;
                let p = g.close(p)?;
                let p = g.as_symbol(p, ":", g.sym.out_type)?;
                let p = g.expr(p)?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::ParenGroup)?;
                let p = g.tok(p, "(")?;
                let p = first!(
                    g,
                    p,
                    |g, p| {
                        let p = g.open_op(p, O::Ellipsis)?;
                        let p = g.tok(p, "...")?;
                        g.close(p)
                    },
                    |g, p| Some(g.opt(p, Grammar::formals)),
                )?;
                let p = g.tok(p, ")")?;
                g.close(p)
            },
        )
    }

    /// BlockLambdaSupers := ("extends".asSymbol(super) / "implements".asSymbol(super)) y Type y
    ///   any(",".asSymbol(super) y Type) y opt(",")
    fn block_lambda_supers(&mut self, p: usize) -> R {
        let p = first!(
            self,
            p,
            |g, p| g.as_symbol(p, "extends", g.sym.super_),
            |g, p| g.as_symbol(p, "implements", g.sym.super_),
        )?;
        let p = self.expr(p)?;
        let p = self.any(p, |g, p| {
            let p = g.as_symbol(p, ",", g.sym.super_)?;
            g.expr(p)
        });
        Some(self.opt(p, |g, p| g.tok(p, ",")))
    }

    /// BlockLambdaBody := blockTree((Semi y ( y TopLevelsInSemi y )) / opt(TopLevel))
    fn block_lambda_body(&mut self, p: usize) -> R {
        self.tree(p, Fin::Block, |g, p| {
            first!(
                g,
                p,
                |g, p| {
                    let p = g.open_op(p, O::Semi)?;
                    let p = g.top_levels_in_semi(p);
                    g.close(p)
                },
                |g, p| Some(g.opt(p, Grammar::top_level)),
            )
        })
    }

    /// RawBlock := blockPreceder y blockTree("{" y !QuotedGroup y TopLevels y "}")
    fn raw_block(&mut self, p: usize) -> R {
        let p = self.check(p, !self.is_bag_preceder(p))?;
        self.tree(p, Fin::Block, |g, p| {
            let p = g.tok(p, "{")?;
            let p = g.check(p, !g.is(p, O::QuotedGroup))?;
            let p = g.top_levels(p);
            g.tok(p, "}")
        })
    }

    /// Obj := propertyBagPreceder y callTree("{".rename("new") y (Props / NoPropClass) y "}")
    fn obj(&mut self, p: usize) -> R {
        let p = self.check(p, self.is_bag_preceder(p))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.rename(p, "{", "new", false)?;
            let p = first!(g, p, Grammar::props, Grammar::no_prop_class)?;
            g.tok(p, "}")
        })
    }

    /// Props := (Comma y ( y (PropClass / (NoPropClass y Prop)) y any("," y Prop) y
    ///   opt(",") y )) / PropClass / (NoPropClass y Prop)
    fn props(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Comma)?;
                let p = first!(g, p, Grammar::prop_class, Grammar::no_prop_class_then_prop)?;
                let p = g.any(p, |g, p| {
                    let p = g.tok(p, ",")?;
                    g.prop(p)
                });
                let p = g.opt(p, |g, p| g.tok(p, ","));
                g.close(p)
            },
            Grammar::prop_class,
            Grammar::no_prop_class_then_prop,
        )
    }

    fn no_prop_class_then_prop(&mut self, p: usize) -> R {
        let p = self.no_prop_class(p)?;
        self.prop(p)
    }

    /// NoPropClass := impliedValue(void, Left)
    fn no_prop_class(&mut self, p: usize) -> R {
        self.implied_value(p, Value::Void, true)
    }

    /// PropClass := infixColons y ( y ( y "class" y ) y ":" y Type y )
    fn prop_class(&mut self, p: usize) -> R {
        let p = self.check(p, self.is_any(p, INFIX_COLONS))?;
        let p = self.open(p)?;
        let p = self.open(p)?;
        let p = self.tok(p, "class")?;
        let p = self.close(p)?;
        let p = self.tok(p, ":")?;
        let p = self.expr(p)?;
        self.close(p)
    }

    /// Prop := (infixColons y ( y PropName y ":" y Expr y ))
    ///   / (PropName y callTree(impliedValue(desugarPun, Left)))
    fn prop(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.check(p, g.is_any(p, INFIX_COLONS))?;
                let p = g.open(p)?;
                let p = g.prop_name(p)?;
                let p = g.tok(p, ":")?;
                let p = g.expr(p)?;
                g.close(p)
            },
            |g, p| {
                let p = g.prop_name(p)?;
                g.tree(p, Fin::Call, |g, p| {
                    g.implied_value(p, Value::Builtin(Builtin::DesugarPun), true)
                })
            },
        )
    }

    /// PropName := Problem(( y "class" y ), ClassPropertyMustAppearFirst)
    ///   / (( y value(unreservedWordMatcher) y ))
    fn prop_name(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let after = {
                    let q = g.open(p)?;
                    let q = g.tok(q, "class")?;
                    g.close(q)?
                };
                g.events.push(Event::Error(
                    Message::ClassPropertyMustAppearFirst,
                    p as u32,
                    after as u32 - 1,
                ));
                Some(after)
            },
            |g, p| {
                let p = g.open(p)?;
                let p = g.unreserved(p, Fin::Value)?;
                g.close(p)
            },
        )
    }

    /// DecoratedLet := Counter(
    ///     (Comma y ( y At y DecoratedLetBody y many("," y declTree(LetRest)) y
    ///       finishSplitCommaSoft y CountForEach(finishSplitCall) y ))
    ///   / (At y DecoratedLetBody y finishSplitCommaSoft y CountForEach(finishSplitCall)))
    ///
    /// Each decoration starts a call that's finished after the declarations,
    /// so decorations apply to all of them.
    fn decorated_let(&mut self, p: usize) -> R {
        self.counters.push(0);
        let result = first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Comma)?;
                let p = g.check(p, g.is(p, O::At))?;
                let p = g.decorated_let_body(p)?;
                let p = g.many(p, |g, p| {
                    let p = g.tok(p, ",")?;
                    g.tree(p, Fin::Decl, Grammar::let_rest)
                })?;
                let p = g.finish_decorated_let(p)?;
                g.close(p)
            },
            |g, p| {
                let p = g.check(p, g.is(p, O::At))?;
                let p = g.decorated_let_body(p)?;
                g.finish_decorated_let(p)
            },
        );
        self.counters.pop();
        result
    }

    fn finish_decorated_let(&mut self, p: usize) -> R {
        self.finish_split(p, Fin::SoftComma)?;
        for _ in 0..*self.counters.last().unwrap() {
            self.finish_split(p, Fin::Call)?;
        }
        Some(p)
    }

    /// DecoratedLetBody := decorated(DecoratedLetBody, NegLA(Comma) y DecoratedLetBody,
    ///     beforeAt = startSplitTree y CountUp, no wrapper tree)
    ///   / (startSplitTree y declTree(LetBody))
    fn decorated_let_body(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                g.decorated(
                    p,
                    Grammar::decorated_let_body,
                    |g, p| {
                        let p = g.check(p, !g.is(p, O::Comma))?;
                        g.decorated_let_body(p)
                    },
                    |g, p| {
                        g.start_split(p)?;
                        *g.counters.last_mut().unwrap() += 1;
                        Some(p)
                    },
                )
            },
            |g, p| {
                let p = g.start_split(p)?;
                g.tree(p, Fin::Decl, Grammar::let_body)
            },
        )
    }

    /// Let := (Comma y ( y declTree(LetBody) y many("," y declTree(LetRest)) y ))
    ///   / declTree(LetBody)
    fn let_(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Comma)?;
                let p = g.tree(p, Fin::Decl, Grammar::let_body)?;
                let p = g.many(p, |g, p| {
                    let p = g.tok(p, ",")?;
                    g.tree(p, Fin::Decl, Grammar::let_rest)
                })?;
                g.close(p)
            },
            |g, p| g.tree(p, Fin::Decl, Grammar::let_body),
        )
    }

    /// `Op y ( y Self y Rest y )` for declarations.
    fn decl_suffix(
        &mut self,
        p: usize,
        op: &[Operator],
        inner: fn(&mut Grammar, usize) -> R,
        rest: fn(&mut Grammar, usize) -> R,
    ) -> R {
        let p = self.check(p, self.is_any(p, op))?;
        let p = self.open(p)?;
        let p = inner(self, p)?;
        let p = rest(self, p)?;
        self.close(p)
    }

    /// LetBody := (Leaf y ( y "let" y DeclName y ))
    ///   / (Square y ( y Leaf y ( y "let" y ) y DeclMulti y ))
    ///   / (Curly y ( y Leaf y ( y "let" y ) y DeclMultiNamed y ))
    ///   / (Eq y ( y LetBody y DeclInit y )) / (HighColon y ( y LetBody y DeclType y ))
    fn let_body(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Leaf)?;
                let p = g.tok(p, "let")?;
                let p = g.unreserved(p, Fin::LeftName)?;
                g.close(p)
            },
            |g, p| g.let_then(p, O::Square, Grammar::decl_multi),
            |g, p| g.let_then(p, O::Curly, Grammar::decl_multi_named),
            |g, p| g.decl_suffix(p, &[O::Eq], Grammar::let_body, Grammar::decl_init),
            |g, p| g.decl_suffix(p, &[O::HighColon], Grammar::let_body, Grammar::decl_type),
        )
    }

    /// `op y ( y Leaf y ( y "let" y ) y then y )`
    fn let_then(&mut self, p: usize, op: Operator, then: fn(&mut Grammar, usize) -> R) -> R {
        let p = self.open_op(p, op)?;
        let p = self.open_op(p, O::Leaf)?;
        let p = self.tok(p, "let")?;
        let p = self.close(p)?;
        let p = then(self, p)?;
        self.close(p)
    }

    /// LetNested := (Leaf y ( y DeclName y )) / (Ellipsis y ( y "...".asSymbol(surpriseMe) y ))
    ///   / (SquareGroup y ( y DeclMulti y )) / (CurlyGroup y ( y DeclMultiNamed y ))
    ///   / (Eq y ( y LetNested y DeclInit y )) / (infixColons y ( y LetNested y DeclMultiNested y ))
    ///   / (As y ( y LetNested y "as".asSymbol(as) y ( y DeclName y ) y ))
    ///   / (Is y ( y LetNested y DeclTypeNested y ))
    fn let_nested(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            Grammar::leaf_decl_name,
            |g, p| {
                let p = g.open_op(p, O::Ellipsis)?;
                let p = g.as_symbol(p, "...", g.sym.surprise_me)?;
                g.close(p)
            },
            |g, p| g.group(p, O::SquareGroup, Grammar::decl_multi),
            |g, p| g.group(p, O::CurlyGroup, Grammar::decl_multi_named),
            |g, p| g.decl_suffix(p, &[O::Eq], Grammar::let_nested, Grammar::decl_init),
            |g, p| g.decl_suffix(
                p,
                INFIX_COLONS,
                Grammar::let_nested,
                Grammar::decl_multi_nested
            ),
            |g, p| {
                let p = g.open_op(p, O::As)?;
                let p = g.let_nested(p)?;
                let p = g.as_symbol(p, "as", g.sym.as_)?;
                let p = g.open(p)?;
                let p = g.unreserved(p, Fin::LeftName)?;
                let p = g.close(p)?;
                g.close(p)
            },
            |g, p| g.decl_suffix(p, &[O::Is], Grammar::let_nested, |g, p| {
                let p = g.as_symbol(p, "is", g.sym.type_)?;
                g.expr(p)
            }),
        )
    }

    /// `Leaf y ( y DeclName y )`
    fn leaf_decl_name(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::Leaf)?;
        let p = self.unreserved(p, Fin::LeftName)?;
        self.close(p)
    }

    /// `op y ( y inner y )`
    fn group(&mut self, p: usize, op: Operator, inner: fn(&mut Grammar, usize) -> R) -> R {
        let p = self.open_op(p, op)?;
        let p = inner(self, p)?;
        self.close(p)
    }

    /// LetRest := (Leaf y ( y DeclName y )) / (SquareGroup y ( y DeclMulti y ))
    ///   / (CurlyGroup y ( y DeclMultiNamed y )) / (Eq y ( y LetRest y DeclInit y ))
    ///   / (HighColon y ( y LetRest y DeclType y ))
    fn let_rest(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            Grammar::leaf_decl_name,
            |g, p| g.group(p, O::SquareGroup, Grammar::decl_multi),
            |g, p| g.group(p, O::CurlyGroup, Grammar::decl_multi_named),
            |g, p| g.decl_suffix(p, &[O::Eq], Grammar::let_rest, Grammar::decl_init),
            |g, p| g.decl_suffix(p, &[O::HighColon], Grammar::let_rest, Grammar::decl_type),
        )
    }

    /// LetArg := (Leaf y ( y "let" y DeclName y )) / (Eq y ( y LetArg y DeclDefault y ))
    ///   / (HighColon y ( y LetArg y DeclType y )) / (supports y ( y LetArg y DeclReifies y ))
    fn let_arg(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Leaf)?;
                let p = g.tok(p, "let")?;
                let p = g.unreserved(p, Fin::LeftName)?;
                g.close(p)
            },
            |g, p| g.decl_suffix(p, &[O::Eq], Grammar::let_arg, |g, p| {
                let p = g.as_symbol(p, "=", g.sym.default)?;
                g.expr(p)
            }),
            |g, p| g.decl_suffix(p, &[O::HighColon], Grammar::let_arg, Grammar::decl_type),
            |g, p| g.decl_suffix(p, SUPPORTS, Grammar::let_arg, |g, p| {
                let p = g.as_symbol(p, ":", g.sym.reifies)?;
                g.expr(p)
            }),
        )
    }

    /// DeclType := ":".asSymbol(type) y Type
    fn decl_type(&mut self, p: usize) -> R {
        let p = self.as_symbol(p, ":", self.sym.type_)?;
        self.expr(p)
    }

    /// DeclInit := "=".asSymbol(init) y Expr
    ///
    /// The Expr recovers from a missing operator ([`operand`](Self::operand)).
    fn decl_init(&mut self, p: usize) -> R {
        let p = self.as_symbol(p, "=", self.sym.init)?;
        self.operand(p, ";")
    }

    /// DeclMultiNested := ":".asSymbol(as) y (CurlyGroup y ( y DeclMultiNamed y ))
    fn decl_multi_nested(&mut self, p: usize) -> R {
        let p = self.as_symbol(p, ":", self.sym.as_)?;
        self.group(p, O::CurlyGroup, Grammar::decl_multi_named)
    }

    /// DeclMulti := callTree("[" y ((Comma y ( y LetNested y ",".revalue(comma) y shiftLeft y
    ///     opt(LetNested y any("," y LetNested) y opt(",")) y ))
    ///   / (impliedValue(comma, Left) y LetNested)) y "]")
    fn decl_multi(&mut self, p: usize) -> R {
        self.tree(p, Fin::Call, |g, p| {
            let p = g.tok(p, "[")?;
            let p = first!(
                g,
                p,
                |g, p| {
                    let p = g.open_op(p, O::Comma)?;
                    let p = g.let_nested(p)?;
                    let p = g.revalue(p, ",", Value::Builtin(Builtin::Comma))?;
                    let p = g.shift_left(p)?;
                    let p = g.opt(p, |g, p| g.separated(p, Grammar::let_nested));
                    g.close(p)
                },
                |g, p| {
                    let p = g.implied_value(p, Value::Builtin(Builtin::Comma), true)?;
                    g.let_nested(p)
                },
            )?;
            g.tok(p, "]")
        })
    }

    /// `item y any("," y item) y opt(",")`
    fn separated(&mut self, p: usize, item: fn(&mut Grammar, usize) -> R) -> R {
        let p = item(self, p)?;
        let p = self.any(p, |g, p| {
            let p = g.tok(p, ",")?;
            item(g, p)
        });
        Some(self.opt(p, |g, p| g.tok(p, ",")))
    }

    /// DeclMultiNamed := callTree("{".rename(curlies) y ((Comma y ( y LetNested y
    ///   any("," y LetNested) y opt(",") y )) / LetNested) y "}")
    fn decl_multi_named(&mut self, p: usize) -> R {
        self.tree(p, Fin::Call, |g, p| {
            let p = g.rename(p, "{", "{}", true)?;
            let p = first!(
                g,
                p,
                |g, p| {
                    let p = g.open_op(p, O::Comma)?;
                    let p = g.separated(p, Grammar::let_nested)?;
                    g.close(p)
                },
                Grammar::let_nested,
            )?;
            g.tok(p, "}")
        })
    }

    /// TypeArguments := "<" y ((Comma y ( y TypeArgument y any("," y TypeArgument) y ))
    ///   / TypeArgument) y ">"
    fn type_arguments(&mut self, p: usize) -> R {
        let p = self.tok(p, "<")?;
        let p = first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Comma)?;
                let p = g.type_argument(p)?;
                let p = g.any(p, |g, p| {
                    let p = g.tok(p, ",")?;
                    g.type_argument(p)
                });
                g.close(p)
            },
            Grammar::type_argument,
        )?;
        self.tok(p, ">")
    }

    /// TypeArgument := typeArgSymbol y TypeArgumentName
    fn type_argument(&mut self, p: usize) -> R {
        let p = self.implied_symbol(p, self.sym.type_arg)?;
        self.type_argument_name(p)
    }

    /// TypeArgumentName := (allExtendsLike y blockTree(impliedValue(\typeArg) y
    ///   extendsLikeModifiers(TypeArgumentName))) / decorated(TypeArgumentName) / Expr
    fn type_argument_name(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.check(p, g.is_any(p, EXTENDS_LIKE))?;
                g.tree(p, Fin::Block, |g, p| {
                    let p = g.implied_value(p, Value::Symbol(g.sym.type_arg), false)?;
                    g.extends_like_modifiers(p, Grammar::type_argument_name)
                })
            },
            |g, p| g.decorated_call(p, Grammar::type_argument_name),
            Grammar::expr,
        )
    }

    /// CommaExpr := (Comma y callTree(CommaOp)) / Expr
    ///
    /// CommaOp := Comma y ( y CommaEl y ",".revalue(comma) y shiftLeft y
    ///   opt(CommaEl y any("," y CommaEl) y opt(",")) y )
    fn comma_expr(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.check(p, g.is(p, O::Comma))?;
                g.tree(p, Fin::Call, |g, p| {
                    let p = g.open_op(p, O::Comma)?;
                    let p = g.comma_el(p)?;
                    let p = g.revalue(p, ",", Value::Builtin(Builtin::Comma))?;
                    let p = g.shift_left(p)?;
                    let p = g.opt(p, |g, p| g.separated(p, Grammar::comma_el));
                    g.close(p)
                })
            },
            Grammar::expr,
        )
    }

    /// RawCommaOp := (Comma y ( y CommaEl y any("," y CommaEl) y opt(",") y )) / CommaEl
    fn raw_comma_op(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Comma)?;
                let p = g.separated(p, Grammar::comma_el)?;
                g.close(p)
            },
            Grammar::comma_el,
        )
    }

    /// CommaEl := declTree(LetBody) / Expr / Garbage("ListElement", stopBefore closeBrackets + ",")
    fn comma_el(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| g.tree(p, Fin::Decl, Grammar::let_body),
            Grammar::expr,
            Grammar::list_element_garbage,
        )
    }

    fn list_element_garbage(&mut self, p: usize) -> R {
        self.garbage(
            p,
            Message::Unparsable("ListElement"),
            &[],
            CLOSE_OR_COMMA,
            true,
        )
    }

    /// An `Expr` where one ends a statement or argument, or, as tempest's
    /// own recovery, operands side by side with no operator between them,
    /// as in `cents 100` for `cents % 100`: the rest are one error, and the
    /// first is kept, after it in a block, so the block fails where it runs
    /// but has the first's type, and what's around it still parses, where
    /// Kotlin's grammar gives up on the whole statement.
    /// `separator` is what could have gone between them instead, for the
    /// message: `;` or `,`.
    fn operand(&mut self, p: usize, separator: &'static str) -> R {
        self.with_strays(p, separator, |g, p| {
            first!(g, p, Grammar::expr, |g, p| g.juxtaposed_words(p, separator))
        })
    }

    /// What `f` matches, and if operands follow it where none can, those
    /// as one error, in a block with it. See [`operand`](Self::operand).
    fn with_strays(
        &mut self,
        p: usize,
        separator: &'static str,
        f: impl FnOnce(&mut Grammar, usize) -> R,
    ) -> R {
        let mark = self.events.len();
        let q = f(self, p)?;
        // After `a<b, c>`, a stray is more likely `<` and `>` meant as
        // comparisons, as in `or(a < 2, a > 0)`, so that's left to fail.
        if q <= p || !self.is_stray(q) || self.is(p, O::Angle) {
            return Some(q);
        }
        let start = Event::Start(self.before(p), self.origin(p));
        let message = Message::MissingOperator(self.first_token_text(q), separator);
        let mut stop = CLOSE_OR_SEMI.to_vec();
        stop.extend([",", "=>"]);
        let Some(end) = self.garbage(q, message, &[], &stop, true) else {
            return Some(q);
        };
        // The error first, so it fails before the first operand runs, and
        // the block's type is the first operand's.
        let error = self.events.pop().unwrap();
        self.events.splice(mark..mark, [start, error]);
        self.events.push(Event::Finish(self.after(end), Fin::Block));
        Some(end)
    }

    /// Whether the part at `p` starts an operand: an operator node, or a
    /// word or number. Where an `Expr` ends a statement or argument, only a
    /// close part or a separator follows it.
    fn is_stray(&self, p: usize) -> bool {
        self.part(p).is_some_and(|part| match part.kind {
            PartKind::Open => true,
            PartKind::Token => matches!(part.token, TokenKind::Word | TokenKind::Number),
            PartKind::Close => false,
        })
    }

    /// The text of the first token at or after `p`.
    fn first_token_text(&self, p: usize) -> String {
        let token = self.parts[p..]
            .iter()
            .find(|part| part.kind == PartKind::Token);
        token.map_or_else(String::new, |part| self.part_text(part).to_string())
    }

    /// Words or numbers side by side in one leaf, as in `cents 100`, but not
    /// `let x`: the rest as one error, then the first as an `Expr`, in a
    /// block. Tempest's own recovery, for [`operand`](Self::operand).
    fn juxtaposed_words(&mut self, p: usize, separator: &'static str) -> R {
        let first = self.open_op(p, O::Leaf)?;
        let close = self.parts[p].close as usize;
        let p = self.check(p, close >= first + 2 && self.tok(first, "let").is_none())?;
        let rest = first + 1;
        self.tree(p, Fin::Block, |g, _| {
            let message = Message::MissingOperator(g.first_token_text(rest), separator);
            g.events
                .push(Event::Error(message, rest as u32, close as u32 - 1));
            let after = g.expr(first)?;
            g.check(after, after == rest)?;
            g.close(close)
        })
    }

    /// Expr := QuasiTree / QuasiAst / QuasiHole / Jump / AwaitReturnThrowYield / ErrorCase /
    ///   Catch / Id /
    ///   SymbolLiteral / List / New / Prefix / SpecialDot / RegularDot / StringExpr /
    ///   TaggedString / Call / DecoratedExpr / Parenthesized / OfExpr / Throws / Infix /
    ///   Postfix / Member / Specialize / Literal / RegExp / (CurlyGroup y ( y (Obj / RawBlock) y ))
    ///   / (( y "(" y Garbage("Expression", stopBefore ",;" + closeBrackets, stopAfter ")") y ))
    pub fn expr(&mut self, p: usize) -> R {
        if self.depth >= MAX_DEPTH {
            return self.too_deep(p);
        }
        self.depth += 1;
        let result = self.expr_alternatives(p);
        self.depth -= 1;
        result
    }

    /// An operand nested too deeply to parse without running out of stack,
    /// as an error. Kotlin overflows its stack well before this depth.
    fn too_deep(&mut self, p: usize) -> R {
        if self.part(p).is_none_or(|part| part.kind == PartKind::Close) {
            return None;
        }
        let next = self.next_sibling(p);
        self.events.push(Event::Error(
            Message::NestedTooDeeply,
            p as u32,
            next as u32 - 1,
        ));
        Some(next)
    }

    fn expr_alternatives(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            Grammar::quasi_tree,
            Grammar::quasi_ast,
            Grammar::quasi_hole,
            Grammar::jump,
            Grammar::await_return_throw_yield,
            Grammar::error_case,
            Grammar::catch,
            Grammar::id,
            Grammar::symbol_literal,
            Grammar::list,
            Grammar::new_expr,
            Grammar::prefix,
            Grammar::special_dot,
            Grammar::regular_dot,
            Grammar::string_expr,
            Grammar::tagged_string,
            Grammar::call,
            |g, p| g.decorated_call(p, Grammar::expr),
            Grammar::parenthesized,
            Grammar::of_expr,
            Grammar::throws,
            Grammar::infix,
            Grammar::postfix,
            Grammar::member,
            Grammar::specialize,
            Grammar::literal,
            Grammar::reg_exp,
            |g, p| {
                let p = g.open_op(p, O::CurlyGroup)?;
                let p = first!(g, p, Grammar::obj, Grammar::raw_block)?;
                g.close(p)
            },
            |g, p| {
                let p = g.open(p)?;
                let p = g.tok(p, "(")?;
                let p = g.garbage(
                    p,
                    Message::Unparsable("Expression"),
                    &[")"],
                    &[",", ";", ")", "}", "]", ">"],
                    false,
                )?;
                g.close(p)
            },
        )
    }

    /// `ParenGroup y ( y "(" y ((NegLA(Comma y () y TopLevelNoGarbageNoComment)
    ///   / (Comma y Garbage(SyntaxReservedForTuples, stopBefore ")"))
    ///   / Garbage("Expression", stopBefore ")")) y ")" y )`
    fn parenthesized(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::ParenGroup)?;
        let p = self.tok(p, "(")?;
        let p = first!(
            self,
            p,
            |g, p| {
                let p = g.check(p, !g.is(p, O::Comma))?;
                g.top_level_no_garbage_no_comment(p)
            },
            |g, p| {
                let p = g.check(p, g.is(p, O::Comma))?;
                g.garbage(p, Message::SyntaxReservedForTuples, &[], &[")"], false)
            },
            |g, p| g.garbage(p, Message::Unparsable("Expression"), &[], &[")"], false),
        )?;
        let p = self.tok(p, ")")?;
        self.close(p)
    }

    /// Literal := (( y value(number) y )) / value(number)
    fn literal(&mut self, p: usize) -> R {
        let number = |g: &mut Grammar, p| {
            g.token_tree(p, Fin::Value, |part, _| part.token == TokenKind::Number)
        };
        first!(
            self,
            p,
            |g, p| {
                let p = g.open(p)?;
                let p = number(g, p)?;
                g.close(p)
            },
            number,
        )
    }

    /// RegExp := callTree(( y LeftDelimiter y "/" lookahead y regexLiteralBuiltinName y
    ///   RegexToArgs y ))
    ///
    /// `/foo/g` becomes `rgx(list("(?/g)foo"), list())`, like `rgx"..."`.
    fn reg_exp(&mut self, p: usize) -> R {
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.check(p, g.is_token(p, TokenKind::LeftDelimiter))?;
            let p = g.check(p, g.tok(p, "/").is_some())?;
            let p = g.implied_name(p, "rgx")?;
            let p = g.regex_to_args(p)?;
            g.close(p)
        })
    }

    fn regex_to_args(&mut self, p: usize) -> R {
        let (left, _) = self
            .token(p)
            .filter(|(part, text)| part.token == TokenKind::LeftDelimiter && *text == "/")?;
        let start = left.span.start;
        let mut i = p + 1;
        let leaf_open = self.part(i)?;
        if leaf_open.kind != PartKind::Open || leaf_open.op != O::Leaf {
            return None;
        }
        i += 1;
        let mut pattern = String::new();
        while let Some((part, text)) = self.token(i) {
            if part.token != TokenKind::QuotedString {
                break;
            }
            pattern.push_str(text);
            i += 1;
        }
        let leaf_close = self.part(i)?;
        if leaf_close.kind != PartKind::Close || leaf_close.op != O::Leaf {
            return None;
        }
        i += 1;
        let (right, text) = self.token(i)?;
        if right.token != TokenKind::RightDelimiter || !text.starts_with('/') {
            return None;
        }
        let flags = &text[1..];
        let full = if flags.is_empty() {
            pattern
        } else {
            format!("(?/{flags}){pattern}")
        };
        let span = Span {
            start,
            end: right.span.end.max(start),
        };
        let (left, right) = (edge(span.start), edge(span.end));
        let origin = p as u32;
        self.events.push(Event::Start(left, origin));
        self.leaf_tree(left, left, Value::Builtin(Builtin::List), left, origin);
        self.leaf_tree(left, span, Value::String(full), right, origin);
        self.events.push(Event::Finish(right, Fin::Call));
        self.events.push(Event::Start(right, origin));
        self.leaf_tree(right, right, Value::Builtin(Builtin::List), right, origin);
        self.events.push(Event::Finish(right, Fin::Call));
        Some(i + 1)
    }

    /// Id := (( y name(unreservedWordMatcher) y )) / name(unreservedWordMatcher)
    fn id(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open(p)?;
                let p = g.unreserved(p, Fin::RightName)?;
                g.close(p)
            },
            |g, p| g.unreserved(p, Fin::RightName),
        )
    }

    /// List := SquareGroup y callTree(( y "[".revalue(list) y
    ///   ((Semi y ( y typeSymbol y Type y ";" y ListContent y )) / ListContent) y "]" y ))
    fn list(&mut self, p: usize) -> R {
        let p = self.check(p, self.is(p, O::SquareGroup))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.revalue(p, "[", Value::Builtin(Builtin::List))?;
            let p = first!(
                g,
                p,
                |g, p| {
                    let p = g.open_op(p, O::Semi)?;
                    let p = g.implied_symbol(p, g.sym.type_)?;
                    let p = g.expr(p)?;
                    let p = g.tok(p, ";")?;
                    let p = g.list_content(p)?;
                    g.close(p)
                },
                Grammar::list_content,
            )?;
            let p = g.tok(p, "]")?;
            g.close(p)
        })
    }

    /// ListContent := ListElements / ListElement / opt(Garbage("List", stopBefore "]"))
    fn list_content(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            Grammar::list_elements,
            Grammar::list_element,
            |g, p| {
                Some(g.opt(p, |g, p| {
                    g.garbage(p, Message::Unparsable("List"), &[], &["]"], false)
                }))
            }
        )
    }

    /// ListElements := Comma y ( y any(ListHole / (ListElement y ("," / lookahead ")))) y
    ///   opt(",") y )
    fn list_elements(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::Comma)?;
        let p = self.any(p, |g, p| {
            first!(g, p, Grammar::list_hole, |g, p| {
                let p = g.list_element(p)?;
                first!(g, p, |g, p| g.tok(p, ","), |g, p| g.check(
                    p,
                    g.part(p).is_some_and(|part| part.kind == PartKind::Close)
                ))
            })
        });
        let p = self.opt(p, |g, p| g.tok(p, ","));
        self.close(p)
    }

    /// ListElement := Spread / Expr / Garbage("ListElement", stopBefore closeBrackets + ",")
    /// Spread := Ellipsis y callTree(( y name("...") y Expr y ))
    fn list_element(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.check(p, g.is(p, O::Ellipsis))?;
                g.tree(p, Fin::Call, |g, p| {
                    let p = g.open(p)?;
                    let p = g.name_text(p, "...")?;
                    let p = g.expr(p)?;
                    g.close(p)
                })
            },
            Grammar::expr,
            Grammar::list_element_garbage,
        )
    }

    /// ListHole := callTree(",".rename(hole) y lookahead not ")")
    fn list_hole(&mut self, p: usize) -> R {
        self.tree(p, Fin::Call, |g, p| {
            let p = g.rename(p, ",", "hole", true)?;
            g.check(
                p,
                !g.part(p).is_some_and(|part| part.kind == PartKind::Close),
            )
        })
    }

    /// OfExpr := Of y callTree(( y ForInitLet y "of".rename(of) y shiftLeft y Expr y ))
    fn of_expr(&mut self, p: usize) -> R {
        let p = self.check(p, self.is(p, O::Of))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.for_init_let(p)?;
            let p = g.rename(p, "of", "of", true)?;
            let p = g.shift_left(p)?;
            let p = g.expr(p)?;
            g.close(p)
        })
    }

    /// Throws := Throws y callTree(( y Type y "throws".rename(throws) y shiftLeft y
    ///   FailureMode y any("|" y FailureModeSecondary) y ))
    fn throws(&mut self, p: usize) -> R {
        let p = self.check(p, self.is(p, O::Throws))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.expr(p)?;
            let p = g.rename(p, "throws", "throws", true)?;
            let p = g.shift_left(p)?;
            let p = g.expr(p)?;
            let p = g.any(p, |g, p| {
                let p = g.tok(p, "|")?;
                g.expr(p)
            });
            g.close(p)
        })
    }

    /// An operator token of `op_type`, not in `exclude`, as a name.
    fn operator_name(&mut self, p: usize, op_type: OpType, exclude: &[Operator]) -> R {
        self.token_tree(p, Fin::RightName, |part, text| {
            Operator::matching(text, part.token, op_type)
                .iter()
                .any(|op| !exclude.contains(&op))
        })
    }

    /// Infix := ({LowColon, HighColon, Instanceof, Is} y callTree(( y Expr y name(InfixOp) y
    ///     shiftLeft y Type y )))
    ///   / (Infix operators y callTree(( y desugarOperation y Expr y name(InfixOp) y
    ///     shiftLeft y Expr y )))
    ///
    /// The last Expr of an assignment, `=`, recovers from a missing operator
    /// ([`operand`](Self::operand)).
    fn infix(&mut self, p: usize) -> R {
        // Like `prefix`, this can't match a group ending in a close bracket,
        // as in `a[b]` and `f<T>`; checked first, as there.
        if self.ends_in_close_bracket(p) {
            return None;
        }
        first!(
            self,
            p,
            |g, p| {
                let p = g.check(
                    p,
                    g.is_any(p, &[O::LowColon, O::HighColon, O::Instanceof, O::Is]),
                )?;
                g.tree(p, Fin::Call, |g, p| {
                    let p = g.open(p)?;
                    let p = g.expr(p)?;
                    let p = g.operator_name(p, OpType::Infix, &[O::CallJoin])?;
                    let p = g.shift_left(p)?;
                    let p = g.expr(p)?;
                    g.close(p)
                })
            },
            |g, p| {
                let p = g.check(p, g.is_type(p, OpType::Infix))?;
                let assignment = g.is(p, O::Eq);
                g.tree(p, Fin::Call, |g, p| {
                    let p = g.open(p)?;
                    let p = g.implied_builtin(p, Builtin::DesugarOperation)?;
                    let p = g.expr(p)?;
                    let p = g.operator_name(p, OpType::Infix, &[O::CallJoin])?;
                    let p = g.shift_left(p)?;
                    let p = match assignment {
                        true => g.operand(p, ";")?,
                        false => g.expr(p)?,
                    };
                    g.close(p)
                })
            },
        )
    }

    /// Prefix := Prefix operators y callTree(( y desugarOperation y name(PrefixOp) y Expr y ))
    ///
    /// Bracket groups like `(x)` are prefix operators too, but this can't
    /// match one with its close bracket, so that's checked first.
    fn prefix(&mut self, p: usize) -> R {
        let p = self.check(p, self.is_type(p, OpType::Prefix))?;
        if self.ends_in_close_bracket(p) {
            return None;
        }
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.implied_builtin(p, Builtin::DesugarOperation)?;
            let p = g.operator_name(p, OpType::Prefix, &[O::New, O::PreCase])?;
            let p = g.expr(p)?;
            g.close(p)
        })
    }

    /// Postfix := Postfix operators y callTree(((PostQuest lookahead) / impliedValue(desugarOperation))
    ///   y ( y Expr y PostfixOp.rename(_--, _++, _!, ?) y shiftLeft y ))
    fn postfix(&mut self, p: usize) -> R {
        let p = self.check(p, self.is_type(p, OpType::Postfix))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = if g.is(p, O::PostQuest) {
                p
            } else {
                g.implied_value(p, Value::Builtin(Builtin::DesugarOperation), false)?
            };
            let p = g.open(p)?;
            let p = g.expr(p)?;
            let (part, text) = g.token(p)?;
            let postfix = part.token != TokenKind::Error
                && !Operator::matching(text, part.token, OpType::Postfix).is_empty();
            if !postfix {
                return None;
            }
            let (name, builtin) = match text {
                "--" => ("_--", true),
                "++" => ("_++", true),
                "!" => ("_!", true),
                "?" => ("?", true),
                _ => (text, false),
            };
            let name = name.to_string();
            let name = g.names.intern(&name);
            let p = g.tree(p, Fin::RightName, |g, p| {
                let span = g.parts[p].span;
                g.events
                    .push(Event::Leaf(Leaf::Name(name, builtin), span, p as u32));
                Some(p + 1)
            })?;
            let p = g.shift_left(p)?;
            g.close(p)
        })
    }

    /// New := Paren y callTree(( y New y ( y name("new") y Type y ) y "(" y opt(Args) y ")" y ))
    fn new_expr(&mut self, p: usize) -> R {
        let p = self.check(p, self.is(p, O::Paren))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.open_op(p, O::New)?;
            let p = g.name_text(p, "new")?;
            let p = g.expr(p)?;
            let p = g.close(p)?;
            let p = g.tok(p, "(")?;
            let p = g.opt(p, Grammar::args);
            let p = g.tok(p, ")")?;
            g.close(p)
        })
    }

    /// Member := Square y callTree(( y Expr y "[".rename([]) y shiftLeft y RawCommaOp y "]" y ))
    fn member(&mut self, p: usize) -> R {
        let p = self.check(p, self.is(p, O::Square))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.expr(p)?;
            let p = g.rename(p, "[", "[]", true)?;
            let p = g.shift_left(p)?;
            let p = g.raw_comma_op(p)?;
            let p = g.tok(p, "]")?;
            g.close(p)
        })
    }

    /// SpecialDot := (Dot y ( y ( y "builtins" y ) y "." y ( y name(builtinName) y ) y ))
    ///   / (Leaf y ( y callTree("this".revalue(this)) y ))
    ///   / (Leaf y ( y lookahead "super" y Garbage("Word (not \"super\")") y ))
    fn special_dot(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Dot)?;
                let p = g.open(p)?;
                let p = g.tok(p, "builtins")?;
                let p = g.close(p)?;
                let p = g.tok(p, ".")?;
                let p = g.open(p)?;
                let p = g.tree(p, Fin::RightName, |g, p| {
                    let (part, text) = g.token(p)?;
                    if part.token != TokenKind::Word {
                        return None;
                    }
                    let name = crate::unpack::decode_name(text)?;
                    let span = part.span;
                    let name = g.names.intern(&name);
                    g.events
                        .push(Event::Leaf(Leaf::Name(name, true), span, p as u32));
                    Some(p + 1)
                })?;
                let p = g.close(p)?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::Leaf)?;
                let p = g.tree(p, Fin::Call, |g, p| {
                    g.revalue(p, "this", Value::Builtin(Builtin::This))
                })?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::Leaf)?;
                let next = g.parts[p..]
                    .iter()
                    .find(|part| part.kind == PartKind::Token);
                let p = g.check(p, next.is_some_and(|part| g.part_text(part) == "super"))?;
                let p = g.unparsable(p, "Word (not \"super\")", false)?;
                g.close(p)
            },
        )
    }

    /// RegularDot := {Dot, ChainNull} y callTree(( y Expr y (".".rename(.) / "?.".rename(?.)) y
    ///   shiftLeft y opt(SymbolValue) y ))
    fn regular_dot(&mut self, p: usize) -> R {
        let p = self.check(p, self.is_any(p, &[O::Dot, O::ChainNull]))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.expr(p)?;
            let p = first!(g, p, |g, p| g.rename(p, ".", ".", true), |g, p| g
                .rename(p, "?.", "?.", true),)?;
            let p = g.shift_left(p)?;
            let p = g.opt(p, Grammar::symbol_value);
            g.close(p)
        })
    }

    /// Specialize := Angle y callTree(( y Expr y "<".revalue(angle) y shiftLeft y RawCommaOp y
    ///   ">" y ))
    fn specialize(&mut self, p: usize) -> R {
        let p = self.check(p, self.is(p, O::Angle))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.expr(p)?;
            let p = g.revalue(p, "<", Value::Builtin(Builtin::Angle))?;
            let p = g.shift_left(p)?;
            let p = g.raw_comma_op(p)?;
            let p = g.tok(p, ">")?;
            g.close(p)
        })
    }

    /// A string's delimiter: quotes, `'` or a backtick.
    fn delimiter(&self, p: usize) -> R {
        let (part, text) = self.token(p)?;
        let ok = part.token != TokenKind::Error
            && ((!text.is_empty() && text.bytes().all(|b| b == b'"'))
                || text == "'"
                || text == "`");
        ok.then_some(p + 1)
    }

    /// StringExpr := {ParenGroup, CurlyGroup} y ( y syntheticToken y (
    ///     ("(" y QuotedGroup y ( y LeftDelimiter y value(delimiter y
    ///       ((( y QuotedString y any token y )) / impliedValuePart("")) y
    ///       RightDelimiter y delimiter) y ) y ")")
    ///   / callTree(impliedValue(stringExpr) y impliedValue(null) y impliedValue(false) y
    ///     StringGroup)) y )
    fn string_expr(&mut self, p: usize) -> R {
        let p = self.check(p, self.is_any(p, &[O::ParenGroup, O::CurlyGroup]))?;
        let p = self.open(p)?;
        let p = self.check(p, self.is_synthetic(p))?;
        let p = first!(
            self,
            p,
            |g, p| {
                let p = g.tok(p, "(")?;
                let p = g.open_op(p, O::QuotedGroup)?;
                let p = g.check(p, g.is_token(p, TokenKind::LeftDelimiter))?;
                let p = g.tree(p, Fin::Value, |g, p| {
                    let p = g.delimiter(p)?;
                    let p = first!(
                        g,
                        p,
                        |g, p| {
                            let p = g.open(p)?;
                            let p = g.check(p, g.is_token(p, TokenKind::QuotedString))?;
                            let p = g.emit_if(p, |_, _| true)?;
                            g.close(p)
                        },
                        |g, p| {
                            let at = g.implied_at(p, true);
                            g.events.push(Event::Leaf(
                                Leaf::Value(Value::String(String::new())),
                                at,
                                g.origin(p),
                            ));
                            Some(p)
                        },
                    )?;
                    let p = g.check(p, g.is_token(p, TokenKind::RightDelimiter))?;
                    g.delimiter(p)
                })?;
                let p = g.close(p)?;
                g.tok(p, ")")
            },
            |g, p| {
                g.tree(p, Fin::Call, |g, p| {
                    let p = g.implied_value(p, Value::Builtin(Builtin::StringExpr), false)?;
                    let p = g.implied_value(p, Value::Null, false)?;
                    let p = g.implied_value(p, Value::Bool(false), false)?;
                    g.string_group(p, false)
                })
            },
        )?;
        self.close(p)
    }

    /// StringGroup := syntheticToken y (
    ///     ("(" y QuotedGroup y ( y LeftDelimiter y delimiter y any(StringPart) y
    ///       RightDelimiter y delimiter y ) y ")")
    ///   / ("{" y QuotedGroup y funStringSymbol y funTree(( y LeftDelimiter y delimiter y
    ///       funStringSymbol y impliedValue(void) y BlockLambdaBody y RightDelimiter y
    ///       delimiter y )) y "}"))
    ///
    /// StringGroupTagged is the same with StringPartRaw, when `raw`.
    fn string_group(&mut self, p: usize, raw: bool) -> R {
        let p = self.check(p, self.is_synthetic(p))?;
        first!(
            self,
            p,
            |g, p| {
                let p = g.tok(p, "(")?;
                let p = g.open_op(p, O::QuotedGroup)?;
                let p = g.check(p, g.is_token(p, TokenKind::LeftDelimiter))?;
                let p = g.delimiter(p)?;
                let p = if raw {
                    g.any(p, Grammar::string_part_raw)
                } else {
                    g.any(p, Grammar::string_part)
                };
                let p = g.check(p, g.is_token(p, TokenKind::RightDelimiter))?;
                let p = g.delimiter(p)?;
                let p = g.close(p)?;
                g.tok(p, ")")
            },
            |g, p| {
                let p = g.tok(p, "{")?;
                let p = g.check(p, g.is(p, O::QuotedGroup))?;
                let p = g.implied_symbol(p, g.sym.fun_string)?;
                let p = g.tree(p, Fin::Fun, |g, p| {
                    let p = g.open(p)?;
                    let p = g.check(p, g.is_token(p, TokenKind::LeftDelimiter))?;
                    let p = g.delimiter(p)?;
                    let p = g.implied_symbol(p, g.sym.fun_string)?;
                    let p = g.implied_value(p, Value::Void, false)?;
                    let p = g.block_lambda_body(p)?;
                    let p = g.check(p, g.is_token(p, TokenKind::RightDelimiter))?;
                    let p = g.delimiter(p)?;
                    g.close(p)
                })?;
                g.tok(p, "}")
            },
        )
    }

    /// StringGroupSynthetic := (PreIncr y ( y syntheticToken y "+++" y ( y QuotedString y
    ///   safeStringPartSymbol y value(TokenToRawString) y ) y )) / StringHoleRaw
    fn string_group_synthetic(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::PreIncr)?;
                let p = g.check(p, g.is_synthetic(p))?;
                let p = g.tok(p, "+++")?;
                let p = g.open(p)?;
                let p = g.check(p, g.is_token(p, TokenKind::QuotedString))?;
                let p = g.implied_symbol(p, g.sym.safe_string_part)?;
                let p = g.raw_string_value(p)?;
                let p = g.close(p)?;
                g.close(p)
            },
            |g, p| g.string_hole(p, true),
        )
    }

    /// `value(TokenToRawString)`: string content as is, escapes and all.
    fn raw_string_value(&mut self, p: usize) -> R {
        self.tree(p, Fin::Value, |g, p| {
            let (part, text) = g.token(p)?;
            if part.token != TokenKind::QuotedString {
                return None;
            }
            let (span, text) = (part.span, text.to_string());
            g.events.push(Event::Leaf(
                Leaf::Value(Value::String(text)),
                span,
                p as u32,
            ));
            Some(p + 1)
        })
    }

    /// StringHole := DollarCurly y ( y "${" y (CommaExpr / Garbage("Hole", stopBefore ")")) y
    ///   "}" y ), and StringHoleRaw, when `raw`, the same with "${".asSymbol(interpolate).
    fn string_hole(&mut self, p: usize, raw: bool) -> R {
        let p = self.open_op(p, O::DollarCurly)?;
        let p = if raw {
            self.as_symbol(p, "${", self.sym.interpolate)?
        } else {
            self.tok(p, "${")?
        };
        let p = first!(self, p, Grammar::comma_expr, |g, p| g.garbage(
            p,
            Message::Unparsable("Hole"),
            &[],
            &[")"],
            false
        ))?;
        let p = self.tok(p, "}")?;
        self.close(p)
    }

    /// UnicodeRun := (UnicodeRun y ( y "\u{" y argsNoTrailingComma(UnicodeRunPart) y "}" y ))
    ///   / (UnicodeRun y ( y "\u{" y "}" y ))
    fn unicode_run(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::UnicodeRun)?;
                let p = g.tok(p, "\\u{")?;
                let p = first!(
                    g,
                    p,
                    |g, p| {
                        let p = g.open_op(p, O::Comma)?;
                        let p = g.opt(p, |g, p| {
                            let p = g.unicode_run_part(p)?;
                            Some(g.any(p, |g, p| {
                                let p = g.tok(p, ",")?;
                                g.unicode_run_part(p)
                            }))
                        });
                        g.close(p)
                    },
                    Grammar::unicode_run_part,
                    |g, p| g.garbage(p, Message::Unparsable("Arguments"), &[], &[")", "=>"], true),
                )?;
                let p = g.tok(p, "}")?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::UnicodeRun)?;
                let p = g.tok(p, "\\u{")?;
                let p = g.tok(p, "}")?;
                g.close(p)
            },
        )
    }

    /// UnicodeRunPart := ( y many(QuotedString y value(TokenToCodePoint)) y )
    fn unicode_run_part(&mut self, p: usize) -> R {
        let p = self.open(p)?;
        let p = self.many(p, |g, p| {
            g.tree(p, Fin::Value, |g, p| {
                let (part, text) = g.token(p)?;
                if part.token != TokenKind::QuotedString {
                    return None;
                }
                let span = part.span;
                let leaf = match code_point(text) {
                    Some(c) => Leaf::Value(Value::String(c.to_string())),
                    // Malformed, so an error when lifted.
                    None => Leaf::Quoted(format!("\\u{{{text}}}")),
                };
                g.events.push(Event::Leaf(leaf, span, p as u32));
                Some(p + 1)
            })
        })?;
        self.close(p)
    }

    /// UnicodeRunRaw := UnicodeRun y ( y "\u{".asValue() y ((Comma y ( y any(UnicodeRunPartRaw) y ))
    ///   / UnicodeRunPartRaw / epsilon / Garbage("Arguments", stopBefore ")")) y "}".asValue() y )
    fn unicode_run_raw(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::UnicodeRun)?;
        let p = self.as_value(p, "\\u{")?;
        let p = first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Comma)?;
                let p = g.any(p, Grammar::unicode_run_part_raw);
                g.close(p)
            },
            Grammar::unicode_run_part_raw,
            |_, p| Some(p),
            |g, p| g.garbage(p, Message::Unparsable("Arguments"), &[], &[")"], true),
        )?;
        let p = self.as_value(p, "}")?;
        self.close(p)
    }

    /// UnicodeRunPartRaw := (( y many(QuotedString y value(TokenToRawString)) y ))
    ///   / ",".asValue() / StringHoleRaw
    fn unicode_run_part_raw(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open(p)?;
                let p = g.many(p, Grammar::raw_string_value)?;
                g.close(p)
            },
            |g, p| g.as_value(p, ","),
            |g, p| g.string_hole(p, true),
        )
    }

    /// StringPart := (( y many(QuotedString y value(any token)) y )) / StringHole / UnicodeRun
    fn string_part(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open(p)?;
                let p = g.many(p, |g, p| {
                    let p = g.check(p, g.is_token(p, TokenKind::QuotedString))?;
                    g.any_token(p)
                })?;
                g.close(p)
            },
            |g, p| g.string_hole(p, false),
            Grammar::unicode_run,
        )
    }

    /// StringPartRaw := (( y many(QuotedString y value(TokenToRawString)) y ))
    ///   / StringHoleRaw / UnicodeRunRaw
    fn string_part_raw(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open(p)?;
                let p = g.many(p, Grammar::raw_string_value)?;
                g.close(p)
            },
            |g, p| g.string_hole(p, true),
            Grammar::unicode_run_raw,
        )
    }

    /// Args := argsActualOrFormal(Arg)
    fn args(&mut self, p: usize) -> R {
        self.args_or_formals(p, false)
    }

    /// Formals := argsActualOrFormal(Formal)
    fn formals(&mut self, p: usize) -> R {
        self.args_or_formals(p, true)
    }

    /// argsActualOrFormal(arg) := (Comma y ( y opt(arg y any("," y arg)) y opt(",") y ))
    ///   / arg / Garbage("Arguments", stopBefore ")", "=>")
    fn args_or_formals(&mut self, p: usize, formal: bool) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Comma)?;
                let p = g.opt(p, |g, p| {
                    let p = g.arg_with_strays(p, formal)?;
                    Some(g.any(p, |g, p| {
                        let p = g.tok(p, ",")?;
                        g.arg_with_strays(p, formal)
                    }))
                });
                let p = g.opt(p, |g, p| g.tok(p, ","));
                g.close(p)
            },
            |g, p| g.arg_with_strays(p, formal),
            |g, p| g.garbage(p, Message::Unparsable("Arguments"), &[], &[")", "=>"], true),
        )
    }

    /// Arg and Formal := DecorateWithDocComment(decorated(Arg) / softBlockTree(
    ///     opt((({Eq, HighColon, LowColon, Ellipsis} + supports) / Leaf-let lookahead) y
    ///       complexArgSymbol) y
    ///     ((Eq y ( y WithDefault y "=".asSymbol(default) y Expr y )) / NoInit /
    ///      (Ellipsis y ( y "...".asSymbol(surpriseMe) y impliedValue(true, Left) y )) /
    ///      declTree(LetArg))))
    ///   / Garbage("Argument", stopBefore closeBrackets + ",")
    ///
    /// For a Formal, NoInit is FormalNoInit and WithDefault is ArgNoInit;
    /// for an Arg, both are ArgNoInit. Kotlin's `/` binds tighter than its
    /// `y`, so `declTree(LetArg)` is an alternative after the optional
    /// `\_complexArg_`, not instead of it.
    ///
    /// As tempest's own recovery, before the Garbage, an Arg that's words
    /// side by side with no operator between them, as in `f(a b)`, is the
    /// first of them and an error ([`operand`](Self::operand)).
    fn arg(&mut self, p: usize, formal: bool) -> R {
        first!(
            self,
            p,
            |g, p| {
                g.with_doc_comment(p, |g, p| {
                    first!(
                        g,
                        p,
                        |g, p| g.decorated_call(p, Grammar::arg_actual),
                        |g, p| {
                            g.tree(p, Fin::SoftBlock, |g, p| {
                                let p = g.opt(p, |g, p| {
                                    let complex = g.is_any(
                                        p,
                                        &[
                                            O::Eq,
                                            O::HighColon,
                                            O::LowColon,
                                            O::Ellipsis,
                                            O::SupportsComma,
                                            O::SupportsNoComma,
                                        ],
                                    );
                                    let let_ = g.is(p, O::Leaf) && g.tok(p + 1, "let").is_some();
                                    let p = g.check(p, complex || let_)?;
                                    g.implied_symbol(p, g.sym.complex_arg)
                                });
                                first!(
                                    g,
                                    p,
                                    |g, p| {
                                        let p = g.open_op(p, O::Eq)?;
                                        let p = g.arg_no_init(p, false)?;
                                        let p = g.as_symbol(p, "=", g.sym.default)?;
                                        let p = g.expr(p)?;
                                        g.close(p)
                                    },
                                    |g, p| g.arg_no_init(p, formal),
                                    |g, p| {
                                        let p = g.open_op(p, O::Ellipsis)?;
                                        let p = g.as_symbol(p, "...", g.sym.surprise_me)?;
                                        let p = g.implied_value(p, Value::Bool(true), true)?;
                                        g.close(p)
                                    },
                                    |g, p| g.tree(p, Fin::Decl, Grammar::let_arg),
                                )
                            })
                        }
                    )
                })
            },
            |g, p| g.recovered_arg(p, formal),
            |g, p| g.garbage(
                p,
                Message::Unparsable("Argument"),
                &[],
                CLOSE_OR_COMMA,
                true
            ),
        )
    }

    /// An Arg, and if operands follow it with no operator between, as in
    /// `f(a() b())`, those as an error ([`operand`](Self::operand)).
    fn arg_with_strays(&mut self, p: usize, formal: bool) -> R {
        match formal {
            true => self.arg(p, true),
            false => self.with_strays(p, ",", |g, p| g.arg(p, false)),
        }
    }

    /// Words side by side as an Arg, but not a Formal.
    fn recovered_arg(&mut self, p: usize, formal: bool) -> R {
        let p = self.check(p, !formal)?;
        self.juxtaposed_words(p, ",")
    }

    fn arg_actual(&mut self, p: usize) -> R {
        self.arg(p, false)
    }

    /// ArgNoInit := argNoInitActualOrFormal(Pattern), and FormalNoInit, when
    /// `formal`, := argNoInitActualOrFormal(complexArgSymbol y Pattern), where
    /// argNoInitActualOrFormal(typeless) :=
    ///     ((HighColon / LowColon) y ( y Pattern y ":".asSymbol(type) y Type y ))
    ///   / (supports y ( y (((HighColon / LowColon) y ( y Pattern y ":".asSymbol(type) y
    ///       Type y )) / Pattern) y "supports".asSymbol(reifies) y Type y ))
    ///   / typeless
    fn arg_no_init(&mut self, p: usize, formal: bool) -> R {
        first!(
            self,
            p,
            Grammar::typed_pattern,
            |g, p| {
                let p = g.check(p, g.is_any(p, SUPPORTS))?;
                let p = g.open(p)?;
                let p = first!(g, p, Grammar::typed_pattern, Grammar::pattern)?;
                let p = g.as_symbol(p, "supports", g.sym.reifies)?;
                let p = g.expr(p)?;
                g.close(p)
            },
            |g, p| {
                let p = if formal {
                    g.implied_symbol(p, g.sym.complex_arg)?
                } else {
                    p
                };
                g.pattern(p)
            },
        )
    }

    /// `(HighColon / LowColon) y ( y Pattern y ":".asSymbol(type) y Type y )`
    fn typed_pattern(&mut self, p: usize) -> R {
        let p = self.check(p, self.is_any(p, INFIX_COLONS))?;
        let p = self.open(p)?;
        let p = self.pattern(p)?;
        let p = self.as_symbol(p, ":", self.sym.type_)?;
        let p = self.expr(p)?;
        self.close(p)
    }

    /// Pattern := (Ellipsis y ( y "...".asSymbol(surpriseMe) y Expr y shiftLeft y
    ///   impliedValue(void) y )) / Expr
    fn pattern(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Ellipsis)?;
                let p = g.as_symbol(p, "...", g.sym.surprise_me)?;
                let p = g.expr(p)?;
                let p = g.shift_left(p)?;
                let p = g.implied_value(p, Value::Void, false)?;
                g.close(p)
            },
            Grammar::expr,
        )
    }

    /// SymbolLiteral := Esc y ( y "\" y SymbolValue y )
    fn symbol_literal(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::Esc)?;
        let p = self.tok(p, "\\")?;
        let p = self.symbol_value(p)?;
        self.close(p)
    }

    /// SymbolValue := Leaf y ( y value(word) y )
    fn symbol_value(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::Leaf)?;
        let p = self.token_tree(p, Fin::Value, |part, _| part.token == TokenKind::Word)?;
        self.close(p)
    }

    /// QuasiTree := EscCurly y ( y "\{" y any(QuasiHole / QuasiInner) y "}" y )
    fn quasi_tree(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::EscCurly)?;
        let p = self.tok(p, "\\{")?;
        let p = self.any(p, |g, p| {
            first!(g, p, Grammar::quasi_hole, Grammar::quasi_inner)
        });
        let p = self.tok(p, "}")?;
        self.close(p)
    }

    /// QuasiAst := EscParen y escTree(( y "\(" y Expr y ")" y ))
    fn quasi_ast(&mut self, p: usize) -> R {
        let p = self.check(p, self.is(p, O::EscParen))?;
        self.tree(p, Fin::Esc, |g, p| {
            let p = g.open(p)?;
            let p = g.tok(p, "\\(")?;
            let p = g.expr(p)?;
            let p = g.tok(p, ")")?;
            g.close(p)
        })
    }

    /// QuasiHole := DollarCurly y callTree(( y "${".rename(unhole) y funTree(CommaExpr) y "}" y ))
    fn quasi_hole(&mut self, p: usize) -> R {
        let p = self.check(p, self.is(p, O::DollarCurly))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.rename(p, "${", "unhole", true)?;
            let p = g.tree(p, Fin::Fun, Grammar::comma_expr)?;
            let p = g.tok(p, "}")?;
            g.close(p)
        })
    }

    /// QuasiInner := callTree(( y quasiInnerBuiltinName y Quasis y ))
    /// Quasis := any(QuasiHole / QuasiInner / QuasiLeaf)
    fn quasi_inner(&mut self, p: usize) -> R {
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.implied_name(p, "quasiInner")?;
            let p = g.any(p, |g, p| {
                first!(
                    g,
                    p,
                    Grammar::quasi_hole,
                    Grammar::quasi_inner,
                    Grammar::quasi_leaf
                )
            });
            g.close(p)
        })
    }

    /// QuasiLeaf := callTree(quasiLeafBuiltinName y value(any token))
    fn quasi_leaf(&mut self, p: usize) -> R {
        self.tree(p, Fin::Call, |g, p| {
            let p = g.implied_name(p, "quasiLeaf")?;
            g.any_token(p)
        })
    }

    // Helpers for decorations and supertypes.

    /// decorated(target) with a call tree, as for most decorations.
    fn decorated_call(&mut self, p: usize, target: fn(&mut Grammar, usize) -> R) -> R {
        let p = self.check(p, self.is(p, O::At))?;
        self.tree(p, Fin::Call, |g, p| {
            g.decoration(p, target, target, |_, p| Some(p))
        })
    }

    /// decorated(target, inParens, beforeAt) without a wrapping tree.
    fn decorated(
        &mut self,
        p: usize,
        target: fn(&mut Grammar, usize) -> R,
        in_parens: fn(&mut Grammar, usize) -> R,
        before_at: fn(&mut Grammar, usize) -> R,
    ) -> R {
        let p = self.check(p, self.is(p, O::At))?;
        self.decoration(p, target, in_parens, before_at)
    }

    /// decorated := At y wrapperTree(( y beforeAt y "@".rename("@") y
    ///   ((Expr y target y )) / (Paren y ( y Expr y "(" y inParens y ")" y ) y ))))
    ///
    /// `@A x` decorates `x`, and `@A(x)` too: the parser takes that for a
    /// call, but it's the decoration with its operand in parentheses.
    fn decoration(
        &mut self,
        p: usize,
        target: fn(&mut Grammar, usize) -> R,
        in_parens: fn(&mut Grammar, usize) -> R,
        before_at: fn(&mut Grammar, usize) -> R,
    ) -> R {
        let p = self.open(p)?;
        let p = before_at(self, p)?;
        let p = self.rename(p, "@", "@", false)?;
        // The first form needs two operands, and nothing takes none.
        let two = self
            .part(self.next_sibling(p))
            .is_some_and(|part| part.kind != PartKind::Close);
        first!(
            self,
            p,
            |g, p| {
                let p = g.check(p, two)?;
                let p = g.expr(p)?;
                let p = target(g, p)?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::Paren)?;
                let p = g.expr(p)?;
                let p = g.tok(p, "(")?;
                let p = in_parens(g, p)?;
                let p = g.tok(p, ")")?;
                let p = g.close(p)?;
                g.close(p)
            },
        )
    }

    /// extendsLikeModifiers(target): for `extends` and `implements` (as
    /// `\super`), `forbids` and `supports`:
    /// ops y ( y target y (word.asSymbol(key) / ...) y Type y any(",".asSymbol(key) y Type) y )
    fn extends_like_modifiers(&mut self, p: usize, target: fn(&mut Grammar, usize) -> R) -> R {
        let (super_, forbids, supports) = (self.sym.super_, self.sym.forbids, self.sym.supports);
        first!(
            self,
            p,
            |g, p| g.extends_like(
                p,
                EXTENDS_IMPLEMENTS,
                &["extends", "implements"],
                super_,
                target
            ),
            |g, p| g.extends_like(p, FORBIDS, &["forbids"], forbids, target),
            |g, p| g.extends_like(p, SUPPORTS, &["supports"], supports, target),
        )
    }

    fn extends_like(
        &mut self,
        p: usize,
        ops: &[Operator],
        words: &[&str],
        key: NameId,
        target: fn(&mut Grammar, usize) -> R,
    ) -> R {
        let p = self.check(p, self.is_any(p, ops))?;
        let p = self.open(p)?;
        let p = target(self, p)?;
        let mut q = None;
        for word in words {
            q = self.attempt(p, |g, p| g.as_symbol(p, word, key));
            if q.is_some() {
                break;
            }
        }
        let p = self.expr(q?)?;
        let p = self.any(p, |g, p| {
            let p = g.as_symbol(p, ",", key)?;
            g.expr(p)
        });
        self.close(p)
    }

    // JSON metadata, after `;;;`.

    /// Json := JsonValue / Garbage("Json")
    pub fn json(&mut self, p: usize) -> R {
        first!(self, p, Grammar::json_value, |g, p| g
            .unparsable(p, "Json", false))
    }

    /// JsonValue := JsonObject / JsonArray / JsonString / JsonNumber / JsonBoolean / JsonNull
    fn json_value(&mut self, p: usize) -> R {
        first!(
            self,
            p,
            |g, p| g.json_container(p, O::CurlyGroup, "{", "{}", "}", Grammar::json_property),
            |g, p| g.json_container(p, O::SquareGroup, "[", "[]", "]", Grammar::json_value),
            Grammar::json_string,
            Grammar::json_number,
            |g, p| {
                let p = g.open_op(p, O::Leaf)?;
                let p = first!(
                    g,
                    p,
                    |g, p| g.revalue(p, "false", Value::Bool(false)),
                    |g, p| g.revalue(p, "true", Value::Bool(true)),
                )?;
                g.close(p)
            },
            |g, p| {
                let p = g.open_op(p, O::Leaf)?;
                let p = g.revalue(p, "null", Value::Null)?;
                g.close(p)
            },
        )
    }

    /// JsonObject := CurlyGroup y callTree(( y "{".rename("{}") y ((Comma y ( y JsonProperty y
    ///   any("," y JsonProperty) y opt(",") y )) / opt(JsonProperty)) y "}" y )), and
    /// JsonArray likewise with `[`, `[]` and JsonValue.
    fn json_container(
        &mut self,
        p: usize,
        op: Operator,
        open: &str,
        name: &str,
        close: &str,
        item: fn(&mut Grammar, usize) -> R,
    ) -> R {
        let p = self.check(p, self.is(p, op))?;
        self.tree(p, Fin::Call, |g, p| {
            let p = g.open(p)?;
            let p = g.rename(p, open, name, false)?;
            let p = first!(
                g,
                p,
                |g, p| {
                    let p = g.open_op(p, O::Comma)?;
                    let p = g.separated(p, item)?;
                    g.close(p)
                },
                |g, p| Some(g.opt(p, item)),
            )?;
            let p = g.tok(p, close)?;
            g.close(p)
        })
    }

    /// JsonProperty := HighColon y ( y JsonString y ":" y JsonValue y )
    fn json_property(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::HighColon)?;
        let p = self.json_string(p)?;
        let p = self.tok(p, ":")?;
        let p = self.json_value(p)?;
        self.close(p)
    }

    /// JsonString := ParenGroup y ( y syntheticToken y "(" y QuotedGroup y ( y LeftDelimiter y
    ///   "\"" y ((Leaf y ( y value(JoinStrings) y )) / impliedValue("")) y RightDelimiter y
    ///   "\"" y ) y ")" y )
    fn json_string(&mut self, p: usize) -> R {
        let p = self.open_op(p, O::ParenGroup)?;
        let p = self.check(p, self.is_synthetic(p))?;
        let p = self.tok(p, "(")?;
        let p = self.open_op(p, O::QuotedGroup)?;
        let p = self.check(p, self.is_token(p, TokenKind::LeftDelimiter))?;
        let p = self.tok(p, "\"")?;
        let p = first!(
            self,
            p,
            |g, p| {
                let p = g.open_op(p, O::Leaf)?;
                let p = g.tree(p, Fin::Value, Grammar::join_strings)?;
                g.close(p)
            },
            |g, p| g.implied_value(p, Value::String(String::new()), false),
        )?;
        let p = self.check(p, self.is_token(p, TokenKind::RightDelimiter))?;
        let p = self.tok(p, "\"")?;
        let p = self.close(p)?;
        let p = self.tok(p, ")")?;
        self.close(p)
    }

    /// JoinStrings: one or more string content tokens as one.
    fn join_strings(&mut self, p: usize) -> R {
        let mut end = p;
        while self.is_token(end, TokenKind::QuotedString) {
            end += 1;
        }
        match end - p {
            0 => None,
            1 => self.emit_if(p, |_, _| true),
            _ => {
                let text: String = (p..end).map(|i| self.part_text(&self.parts[i])).collect();
                let span = self.parts[p].span.cover(self.parts[end - 1].span);
                self.events
                    .push(Event::Leaf(Leaf::Quoted(text), span, p as u32));
                Some(end)
            }
        }
    }

    /// JsonNumber := (PreDash y callTree(( y "-".rename("-") y ( y value(number) y ) y )))
    ///   / (Leaf y ( y value(number) y ))
    fn json_number(&mut self, p: usize) -> R {
        let number = |g: &mut Grammar, p| {
            g.token_tree(p, Fin::Value, |part, _| part.token == TokenKind::Number)
        };
        first!(
            self,
            p,
            |g, p| {
                let p = g.check(p, g.is(p, O::PreDash))?;
                g.tree(p, Fin::Call, |g, p| {
                    let p = g.open(p)?;
                    let p = g.rename(p, "-", "-", false)?;
                    let p = g.open(p)?;
                    let p = number(g, p)?;
                    let p = g.close(p)?;
                    g.close(p)
                })
            },
            |g, p| {
                let p = g.open_op(p, O::Leaf)?;
                let p = number(g, p)?;
                g.close(p)
            },
        )
    }
}

/// A zero-width span at `at`.
fn edge(at: u32) -> Span {
    Span { start: at, end: at }
}

/// A code point from hex digits, as Kotlin's `TokenToCodePoint`, whose
/// 32-bit arithmetic wraps for long runs of digits.
fn code_point(hex: &str) -> Option<char> {
    let mut code: u32 = 0;
    for c in hex.chars() {
        code = (code << 4) | c.to_digit(16).filter(|_| c.is_ascii())?;
    }
    char::from_u32(code)
}
