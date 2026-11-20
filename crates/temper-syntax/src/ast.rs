//! The grammar step: from a parse tree to the tree later stages work on, as
//! Kotlin Temper's `astbuild`.
//!
//! The result is a copy of the parse tree's meaning in a few node kinds:
//! `Call` (callee, then arguments), `Block`, `Decl`, `Fun`, `Esc`, names
//! (`LeftName` declared, `RightName` used, `BuiltinName`), `Value`, and
//! `Error` for what doesn't parse. Keywords stay ordinary names, so
//! `if (a) { b } else { c }` is a call of `if` with `a`, a function for the
//! block, and named arguments `\callJoin:` and `\else`; macros give them
//! meaning later. Each node keeps an origin link to the parse node it came
//! from, for error spans, hover and go-to-definition.
//!
//! `grammar` matches the parse tree and emits events; this
//! module builds the tree from them, reading names and values from tokens.

use crate::balance::BracketErrorKind;
use crate::grammar::{Event, Fin, Grammar, Leaf, Part, PartKind};
use crate::lex::TokenKind;
use crate::module::Json;
use crate::names::{NameId, Names};
use crate::operator::Operator;
use crate::parse::{ParseErrorKind, Parsed, jump_before_comma};
use crate::tree::{NodeId, NodeKind, Span, Tree};
use crate::unpack::{Number, NumberWarning, decode_name, unpack_number, unpack_string};

/// The grammar step's result: a tree with side tables for values, names,
/// origins and diagnostics.
#[derive(Clone, Debug)]
pub struct Ast {
    pub(crate) tree: Tree,
    pub(crate) root: NodeId,
    pub(crate) metadata: Option<NodeId>,
    pub(crate) json: Option<Json>,
    origins: Vec<NodeId>,
    values: Vec<Value>,
    pub(crate) names: Names,
    diagnostics: Vec<Diagnostic>,
}

impl Ast {
    pub fn tree(&self) -> &Tree {
        &self.tree
    }

    /// The module's code.
    pub fn root(&self) -> NodeId {
        self.root
    }

    /// The JSON metadata after `;;;`, if any.
    pub fn metadata(&self) -> Option<NodeId> {
        self.metadata
    }

    /// The metadata as JSON, once [`build_module`](crate::module::build_module)
    /// has converted it.
    pub fn json(&self) -> Option<&Json> {
        self.json.as_ref()
    }

    /// The parse node a node came from.
    pub fn origin(&self, id: NodeId) -> NodeId {
        self.origins[id.0 as usize]
    }

    /// A value node's value.
    pub fn value(&self, id: NodeId) -> Option<&Value> {
        (self.tree.kind(id) == NodeKind::Value).then(|| &self.values[self.tree.data(id) as usize])
    }

    /// A name node's name.
    pub fn name(&self, id: NodeId) -> Option<NameId> {
        matches!(
            self.tree.kind(id),
            NodeKind::LeftName | NodeKind::RightName | NodeKind::BuiltinName
        )
        .then(|| NameId(self.tree.data(id)))
    }

    /// The names in name nodes and symbols.
    pub fn names(&self) -> &Names {
        &self.names
    }

    pub fn diagnostics(&self) -> &[Diagnostic] {
        &self.diagnostics
    }

    pub(crate) fn add(
        &mut self,
        kind: NodeKind,
        span: Span,
        data: u32,
        children: &[NodeId],
        origin: NodeId,
    ) -> NodeId {
        self.origins.push(origin);
        self.tree.add_node(kind, span, data, children)
    }

    pub(crate) fn report(&mut self, level: Level, message: Message, span: Span) {
        self.diagnostics.push(Diagnostic {
            level,
            message,
            span,
        });
    }
}

/// A value in the tree.
#[derive(Clone, Debug, PartialEq)]
pub enum Value {
    Int32(i32),
    Int64(i64),
    Float64(f64),
    String(String),
    /// A symbol like `\word`, which names an argument.
    Symbol(NameId),
    Bool(bool),
    Null,
    Void,
    /// A builtin function or macro.
    Builtin(Builtin),
    /// Doc comment texts.
    Strings(Vec<String>),
}

/// Builtins the grammar step refers to directly.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Builtin {
    /// Applies an operator by its name, as in `desugarOperation(+, a, b)`.
    DesugarOperation,
    List,
    Comma,
    StringExpr,
    /// `{ x }` in an object for `{ x: x }`.
    DesugarPun,
    /// `T<A>`.
    Angle,
    This,
    PostponedCase,
    /// Attaches doc comments to what follows.
    DocComment,
    /// A comment between statements.
    EmbeddedComment,
}

impl Builtin {
    /// The name, as Kotlin Temper's.
    pub fn name(self) -> &'static str {
        match self {
            Builtin::DesugarOperation => "desugarOperation",
            Builtin::List => "list",
            Builtin::Comma => ",",
            Builtin::StringExpr => "stringExpr",
            Builtin::DesugarPun => "desugarPun",
            Builtin::Angle => "<>",
            Builtin::This => "this",
            Builtin::PostponedCase => "postponedCase",
            Builtin::DocComment => "@docComment",
            Builtin::EmbeddedComment => "REM",
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub enum Level {
    Warn,
    Error,
}

/// What a diagnostic says, named as Kotlin Temper's message templates.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Message {
    /// Expected a production, by its name.
    Unparsable(&'static str),
    SyntaxReservedForTuples,
    ClassPropertyMustAppearFirst,
    MalformedNumber,
    /// The value as written, and whether it would fit an `Int64`, which
    /// the text then suggests: ours, where Kotlin adds a separate info,
    /// MaybePromoteInt32ToInt64.
    Int32OutOfBounds(String, bool),
    Int64OutOfBounds(String),
    ExtraneousModuleContent,
    InvalidMetadata,
    /// Ours: Kotlin runs out of stack instead.
    NestedTooDeeply,
    /// Ours, from bracket recovery: a close bracket is missing, for the
    /// open bracket on a line.
    MissingCloseBracket {
        open: &'static str,
        close: &'static str,
        line: u32,
    },
    /// As Kotlin's lexer words it, for close brackets it marks as errors.
    UnmatchedCloseBracket,
    /// Ours: a `<` with no `>`, and maybe the comparison likely meant.
    UnclosedAngle(Option<String>),
    /// Ours: operands side by side with no operator between them, as in
    /// `cents 100`; the text of the first one that's stray, and what else
    /// could have gone before it, `;` or `,`.
    MissingOperator(String, &'static str),
    /// Ours: a comma after the operand of a `return`, `throw`, or `yield`,
    /// that jump, which ends before it, where Kotlin finds no argument.
    JumpBeforeComma(Operator),
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Diagnostic {
    pub level: Level,
    pub message: Message,
    pub span: Span,
}

impl Message {
    /// The template's name.
    pub fn template(&self) -> &'static str {
        match self {
            Message::Unparsable(_) => "Unparsable",
            Message::SyntaxReservedForTuples => "SyntaxReservedForTuples",
            Message::ClassPropertyMustAppearFirst => "ClassPropertyMustAppearFirst",
            Message::MalformedNumber => "MalformedNumber",
            Message::Int32OutOfBounds(..) => "Int32OutOfBounds",
            Message::Int64OutOfBounds(_) => "Int64OutOfBounds",
            Message::ExtraneousModuleContent => "ExtraneousModuleContent",
            Message::InvalidMetadata => "InvalidMetadata",
            Message::NestedTooDeeply => "NestedTooDeeply",
            Message::MissingCloseBracket { .. } => "MissingCloseBracket",
            Message::UnmatchedCloseBracket => "UnmatchedCloseBracket",
            Message::UnclosedAngle(_) => "UnclosedAngle",
            Message::MissingOperator(..) => "MissingOperator",
            Message::JumpBeforeComma(_) => "JumpBeforeComma",
        }
    }

    /// The value filled into the template, if any.
    pub fn argument(&self) -> Option<&str> {
        match self {
            Message::Unparsable(production) => Some(production),
            Message::Int32OutOfBounds(n, _) | Message::Int64OutOfBounds(n) => Some(n),
            Message::MissingOperator(stray, _) => Some(stray),
            _ => None,
        }
    }

    /// The text, as Kotlin Temper words it.
    pub fn text(&self) -> String {
        match self {
            Message::Unparsable(production) => format!("Expected a {production} here"),
            Message::SyntaxReservedForTuples => {
                "`(...,...)` syntax is reserved for tuples.  Use `do {...}` to group statements"
                    .into()
            }
            Message::ClassPropertyMustAppearFirst => {
                "In {...} object syntax, any `class` property must appear first".into()
            }
            Message::MalformedNumber => "Malformed number".into(),
            Message::Int32OutOfBounds(n, false) => {
                format!("Int32 value, {n}, out of representable bounds")
            }
            Message::Int32OutOfBounds(n, true) => format!(
                "Int32 value, {n}, out of representable bounds; add i64 at the end for an Int64"
            ),
            Message::Int64OutOfBounds(n) => {
                format!("Int64 value, {n}, out of representable bounds")
            }
            Message::ExtraneousModuleContent => "Extraneous module content".into(),
            Message::InvalidMetadata => "Module metadata is not well-formed JSON".into(),
            Message::NestedTooDeeply => "Nested too deeply to parse".into(),
            Message::MissingCloseBracket { open, close, line } => {
                format!("Expected `{close}` to close the `{open}` on line {line}")
            }
            Message::UnmatchedCloseBracket => "Close bracket matches no open bracket".into(),
            Message::UnclosedAngle(None) => "Expected `>` to close `<`".into(),
            Message::UnclosedAngle(Some(meant)) => {
                format!("Expected `>` to close `<`, or did you mean `{meant}`?")
            }
            Message::MissingOperator(stray, separator) => {
                format!("Expected an operator or `{separator}` before `{stray}`")
            }
            Message::JumpBeforeComma(op) => jump_before_comma(*op),
        }
    }
}

/// Builds the tree for a parsed module: its code, and the JSON metadata
/// after `;;;` if there is any.
pub fn build(parsed: &Parsed) -> Ast {
    let mut g = Grammar::new(parsed);
    let mut diagnostics: Vec<Diagnostic> = (parsed.bracket_errors().iter())
        .map(|e| Diagnostic {
            level: Level::Error,
            message: match &e.kind {
                BracketErrorKind::Unclosed { open, close, line } => Message::MissingCloseBracket {
                    open,
                    close,
                    line: *line,
                },
                BracketErrorKind::Unmatched => Message::UnmatchedCloseBracket,
                BracketErrorKind::UnclosedAngle(meant) => Message::UnclosedAngle(meant.clone()),
            },
            span: e.span,
        })
        .collect();
    diagnostics.extend(
        (parsed.errors().iter())
            .filter(|e| e.kind == ParseErrorKind::JumpBeforeComma)
            .map(|e| Diagnostic {
                level: Level::Error,
                message: Message::JumpBeforeComma(e.op),
                span: e.span,
            }),
    );
    let top = top(parsed);
    let (code, metadata) = split_metadata(parsed, top, &mut diagnostics);
    let mut lift = Lift {
        tree: Tree::new(),
        origins: Vec::new(),
        values: Vec::new(),
        diagnostics,
    };
    let root = run(&mut g, &mut lift, parsed, code, top, false);
    let metadata = metadata.map(|node| run(&mut g, &mut lift, parsed, Some(node), top, true));
    Ast {
        tree: lift.tree,
        root,
        metadata,
        json: None,
        origins: lift.origins,
        values: lift.values,
        names: g.names,
        diagnostics: lift.diagnostics,
    }
}

/// The node Kotlin's parser returns: the root's only child if it's an
/// operator, else the root.
fn top(parsed: &Parsed) -> NodeId {
    match parsed.tree().children(parsed.root()) {
        [only] if parsed.tree().op(*only).is_some() => *only,
        _ => parsed.root(),
    }
}

/// Splits `code ;;; metadata`, as Kotlin's `splitCodeFromMetadata`. No code
/// is `None`.
fn split_metadata(
    parsed: &Parsed,
    top: NodeId,
    diagnostics: &mut Vec<Diagnostic>,
) -> (Option<NodeId>, Option<NodeId>) {
    let tree = parsed.tree();
    if tree.op(top) != Some(Operator::SemiSemiSemi) {
        return (Some(top), None);
    }
    let children = tree.children(top);
    let is_separator = |id: NodeId| {
        parsed
            .token(id)
            .is_some_and(|t| parsed.token_text(t) == ";;;")
    };
    let (mut code, mut metadata, mut i) = (None, None, 0);
    if let Some(&first) = children.first() {
        if !is_separator(first) {
            code = Some(first);
            i += 1;
        }
    }
    if i < children.len() && is_separator(children[i]) {
        i += 1;
        if i < children.len() && !is_separator(children[i]) {
            metadata = Some(children[i]);
            i += 1;
        }
    }
    if i != children.len() {
        // Kotlin means to span the rest, but spans only the first extra.
        diagnostics.push(Diagnostic {
            level: Level::Error,
            message: Message::ExtraneousModuleContent,
            span: tree.span(children[i]),
        });
    }
    (code, metadata)
}

/// Runs the grammar from `Root`, or `Json` for metadata, over a node, or an
/// empty root at `top`'s start when there's none, and lifts the result.
fn run(
    g: &mut Grammar,
    lift: &mut Lift,
    parsed: &Parsed,
    node: Option<NodeId>,
    top: NodeId,
    json: bool,
) -> NodeId {
    g.parts.clear();
    g.events.clear();
    match node {
        Some(node) => flatten(parsed, node, &mut g.parts),
        None => {
            let at = parsed.tree().span(top).start;
            let span = Span { start: at, end: at };
            for kind in [PartKind::Open, PartKind::Close] {
                g.parts.push(Part {
                    kind,
                    op: Operator::Root,
                    token: TokenKind::Error,
                    synthetic: false,
                    may_bracket: false,
                    text: (0, 0),
                    node: top,
                    span,
                    close: 1,
                });
            }
        }
    }
    let start = g.parts.first().map_or(Span::default(), |p| Span {
        start: p.span.start,
        end: p.span.start,
    });
    let used = if json { g.json(0) } else { g.root(0) };
    let end = match used {
        Some(n) if n > 0 => {
            let at = g.parts[n - 1].span.end;
            Span { start: at, end: at }
        }
        _ => start,
    };
    let used = used.unwrap_or(0);
    if used != g.parts.len() {
        let production = if json { "Json" } else { "Root" };
        g.events.push(Event::Error(
            Message::Unparsable(production),
            used as u32,
            g.parts.len() as u32 - 1,
        ));
    }
    let mut events = std::mem::take(&mut g.events);
    events.insert(0, Event::Start(start, 0));
    events.push(Event::Finish(end, Fin::SoftBlock));
    let mut items = Vec::new();
    lift.lift_some(g, &events, 0, &mut items);
    let root = match items.as_slice() {
        [only] => *only,
        _ => lift.error(Span::default(), g.parts.first().map_or(top, |p| p.node)),
    };
    g.events = events;
    root
}

/// Appends the parts of `node`'s subtree, without recursion, since parse
/// trees may be deeper than the stack allows.
fn flatten(parsed: &Parsed, node: NodeId, parts: &mut Vec<Part>) {
    let tree = parsed.tree();
    let edge = |at| Span { start: at, end: at };
    // Nodes to visit, or with `true` to close after their children.
    let mut stack = vec![(node, false, 0)];
    while let Some((node, closing, open)) = stack.pop() {
        let op = tree.op(node);
        if let Some(op) = op {
            let span = tree.span(node);
            let part = |kind, at| Part {
                kind,
                op,
                token: TokenKind::Error,
                synthetic: false,
                may_bracket: false,
                text: (0, 0),
                node,
                span: edge(at),
                close: 0,
            };
            if closing {
                parts[open].close = parts.len() as u32;
                parts.push(part(PartKind::Close, span.end));
            } else {
                stack.push((node, true, parts.len()));
                parts.push(part(PartKind::Open, span.start));
                for &child in tree.children(node).iter().rev() {
                    stack.push((child, false, 0));
                }
            }
            continue;
        }
        let token = parsed.token(node).expect("token node");
        parts.push(Part {
            kind: PartKind::Token,
            op: Operator::Leaf,
            token: token.kind,
            synthetic: token.synthetic,
            may_bracket: token.may_bracket,
            text: (token.text_start, token.text_end),
            node,
            span: Span {
                start: token.start,
                end: token.end,
            },
            close: 0,
        });
    }
}

/// Builds trees from events, as Kotlin's `Lift`.
struct Lift {
    tree: Tree,
    origins: Vec<NodeId>,
    values: Vec<Value>,
    diagnostics: Vec<Diagnostic>,
}

impl Lift {
    fn add(
        &mut self,
        kind: NodeKind,
        span: Span,
        data: u32,
        children: &[NodeId],
        origin: NodeId,
    ) -> NodeId {
        let span = Span {
            start: span.start,
            end: span.end.max(span.start),
        };
        self.origins.push(origin);
        self.tree.add_node(kind, span, data, children)
    }

    fn error(&mut self, span: Span, origin: NodeId) -> NodeId {
        self.add(NodeKind::Error, span, u32::MAX, &[], origin)
    }

    fn value(&mut self, value: Value, span: Span, origin: NodeId) -> NodeId {
        let index = self.values.len() as u32;
        self.values.push(value);
        self.add(NodeKind::Value, span, index, &[], origin)
    }

    fn report(&mut self, level: Level, message: Message, span: Span) {
        self.diagnostics.push(Diagnostic {
            level,
            message,
            span,
        });
    }

    /// Lifts events from `i` until a finish, returning where it stopped.
    fn lift_some(
        &mut self,
        g: &mut Grammar,
        events: &[Event],
        mut i: usize,
        items: &mut Vec<NodeId>,
    ) -> usize {
        while i < events.len() {
            match self.lift_one(g, events, i, items) {
                Some(next) => i = next,
                None => return i,
            }
        }
        events.len()
    }

    fn lift_one(
        &mut self,
        g: &mut Grammar,
        events: &[Event],
        i: usize,
        items: &mut Vec<NodeId>,
    ) -> Option<usize> {
        let node_of =
            |g: &Grammar, part: u32| g.parts.get(part as usize).map_or(NodeId(0), |p| p.node);
        match &events[i] {
            Event::Finish(..) => None,
            Event::Start(start, part) => {
                let origin = node_of(g, *part);
                if let Some(Event::Leaf(leaf, at, leaf_part)) = events.get(i + 1) {
                    let (finish, fin) = match events.get(i + 2) {
                        Some(Event::Finish(finish, fin)) => (*finish, *fin),
                        _ => {
                            // Unbalanced, which Kotlin doesn't expect.
                            items.push(self.error(*start, origin));
                            return Some(i + 2);
                        }
                    };
                    let span = Span {
                        start: start.start,
                        end: start.end.max(finish.end),
                    };
                    let leaf_origin = node_of(g, *leaf_part);
                    let node = self.leaf(g, leaf, *at, span, fin, leaf_origin);
                    items.push(node);
                    return Some(i + 3);
                }
                let mut children = Vec::new();
                let j = self.lift_some(g, events, i + 1, &mut children);
                let Some(Event::Finish(finish, fin)) = events.get(j) else {
                    items.push(self.error(*start, origin));
                    return Some(j);
                };
                let span = Span {
                    start: start.start,
                    end: finish.end,
                };
                let node = match fin {
                    Fin::SoftBlock | Fin::SoftComma if children.len() == 1 => children[0],
                    Fin::SoftComma => {
                        let comma = self.value(
                            Value::Builtin(Builtin::Comma),
                            Span {
                                start: span.start,
                                end: span.start,
                            },
                            origin,
                        );
                        children.insert(0, comma);
                        self.add(NodeKind::Call, span, 0, &children, origin)
                    }
                    Fin::SoftBlock | Fin::Block => {
                        self.add(NodeKind::Block, span, 0, &children, origin)
                    }
                    Fin::Call => self.add(NodeKind::Call, span, 0, &children, origin),
                    Fin::Decl => self.add(NodeKind::Decl, span, 0, &children, origin),
                    Fin::Esc => self.add(NodeKind::Esc, span, 0, &children, origin),
                    Fin::Fun => self.add(NodeKind::Fun, span, 0, &children, origin),
                    Fin::LeftName | Fin::RightName | Fin::Value => self.error(span, origin),
                };
                items.push(node);
                Some(j + 1)
            }
            Event::ShiftLeft => {
                let n = items.len();
                if n >= 2 {
                    items.swap(n - 1, n - 2);
                }
                Some(i + 1)
            }
            Event::Leaf(Leaf::Value(value), at, part) => {
                let node = self.value(value.clone(), *at, node_of(g, *part));
                items.push(node);
                Some(i + 1)
            }
            Event::Leaf(_, at, part) => {
                items.push(self.error(*at, node_of(g, *part)));
                Some(i + 1)
            }
            Event::Error(message, first, last) => {
                let (first, last) = (&g.parts[*first as usize], &g.parts[*last as usize]);
                let span = Span {
                    start: first.span.start,
                    end: last.span.end,
                };
                let origin = first.node;
                let index = self.diagnostics.len() as u32;
                self.add(NodeKind::Error, span, index, &[], origin);
                items.push(NodeId(self.tree.len() as u32 - 1));
                self.report(Level::Error, message.clone(), span);
                Some(i + 1)
            }
        }
    }

    /// A name or value leaf. `at` is the leaf's own position, `span` the
    /// tree's around it.
    fn leaf(
        &mut self,
        g: &mut Grammar,
        leaf: &Leaf,
        at: Span,
        span: Span,
        fin: Fin,
        origin: NodeId,
    ) -> NodeId {
        match fin {
            Fin::LeftName | Fin::RightName => {
                let kind = if fin == Fin::LeftName {
                    NodeKind::LeftName
                } else {
                    NodeKind::RightName
                };
                match leaf {
                    Leaf::Name(name, builtin) => {
                        let kind = if *builtin {
                            NodeKind::BuiltinName
                        } else {
                            kind
                        };
                        self.add(kind, span, name.0, &[], origin)
                    }
                    Leaf::Token(part) => {
                        let text = g.part_text(&g.parts[*part as usize]);
                        match decode_name(text) {
                            Some(name) => {
                                let name = g.names.intern(&name);
                                self.add(kind, span, name.0, &[], origin)
                            }
                            None => self.error(at, origin),
                        }
                    }
                    Leaf::Quoted(text) => match decode_name(text) {
                        Some(name) => {
                            let name = g.names.intern(&name);
                            self.add(kind, span, name.0, &[], origin)
                        }
                        None => self.error(at, origin),
                    },
                    Leaf::Value(_) => self.error(at, origin),
                }
            }
            Fin::Value => {
                let value = match leaf {
                    Leaf::Value(value) => Some(value.clone()),
                    Leaf::Quoted(text) => unpack_string(text, false).map(Value::String),
                    Leaf::Token(part) => {
                        let part = g.parts[*part as usize];
                        let text = g.part_text(&part).to_string();
                        self.token_value(g, part.token, &text, at, span)
                    }
                    Leaf::Name(..) => None,
                };
                match value {
                    Some(value) => self.value(value, span, origin),
                    None => self.error(at, origin),
                }
            }
            _ => self.error(span, origin),
        }
    }

    /// A token's value, as Kotlin's `unpackValue`, reporting problems.
    fn token_value(
        &mut self,
        g: &mut Grammar,
        kind: TokenKind,
        text: &str,
        at: Span,
        span: Span,
    ) -> Option<Value> {
        match kind {
            TokenKind::Number => {
                let Some((number, warning)) = unpack_number(text) else {
                    self.report(Level::Error, Message::MalformedNumber, span);
                    return None;
                };
                match warning {
                    Some(NumberWarning::Int32OutOfBounds(n, fits64)) => {
                        self.report(Level::Warn, Message::Int32OutOfBounds(n, fits64), at);
                    }
                    Some(NumberWarning::Int64OutOfBounds(n)) => {
                        self.report(Level::Warn, Message::Int64OutOfBounds(n), at);
                    }
                    None => {}
                }
                Some(match number {
                    Number::Int32(n) => Value::Int32(n),
                    Number::Int64(n) => Value::Int64(n),
                    Number::Float64(f) => Value::Float64(f),
                })
            }
            TokenKind::Word => decode_name(text).map(|name| Value::Symbol(g.names.intern(&name))),
            // Bad escapes are reported later, in context.
            TokenKind::QuotedString => unpack_string(text, false).map(Value::String),
            TokenKind::Punctuation => Some(Value::String(text.to_string())),
            _ => None,
        }
    }
}
