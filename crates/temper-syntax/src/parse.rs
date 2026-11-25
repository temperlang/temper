//! An operator-precedence parser from tokens to a [`Tree`], as Kotlin
//! Temper's `Parse.kt`.
//!
//! The parser keeps a stack of partly built operator nodes. Each token
//! either continues one of them (a close bracket, a follower like the `,`
//! in `extends A, B`), takes the top as its left operand (infix and postfix
//! operators), starts a new one (prefix operators and open brackets), or is
//! a value token, added to a `Leaf`. Which of these applies depends on the
//! operators' precedence and a few special cases in `can_nest`. When a node
//! is done, it's *committed*: written to the tree as a child of the node
//! below it on the stack.
//!
//! Malformed input still gives a tree: nodes with too few or too many
//! operands are kept, with an error for each.

use crate::adapt::{Adapted, Comment, PToken, adapt};
use crate::lex::{SourceKind, Token, TokenKind, lex};
use crate::names::{NameId, Names};
use crate::operator::{Matches, OpType, Operator, is_close_bracket, is_follower, is_open_bracket};
use crate::tree::{NodeId, NodeKind, Span, Tree};

/// A parsed source file, shaped like the source and never changed after
/// parsing, for formatting, outlines, docs and finding nodes by position.
/// Later stages work on a copy of the tree, keeping links back to it.
///
/// It keeps every lexer token, so comments and blank lines are found between
/// node spans, and the tokens the parser saw, where synthetic ones (inserted
/// semicolons, call joins, string parentheses, closers) are flagged.
#[derive(Clone, Debug)]
pub struct Parsed {
    tree: Tree,
    root: NodeId,
    lexed: Vec<Token>,
    adapted: Adapted,
    names_of_tokens: Vec<Option<NameId>>,
    names: Names,
    errors: Vec<ParseError>,
}

impl Parsed {
    pub fn tree(&self) -> &Tree {
        &self.tree
    }

    pub fn root(&self) -> NodeId {
        self.root
    }

    /// The source, then texts of synthetic and changed tokens, which
    /// tokens' text spans refer to.
    pub(crate) fn adapted_text(&self) -> &str {
        &self.adapted.text
    }

    pub fn source(&self) -> &str {
        &self.adapted.text[..self.adapted.source_len as usize]
    }

    /// All the lexer's tokens, including comments and space.
    pub fn lexed(&self) -> &[Token] {
        &self.lexed
    }

    /// The tokens the parser saw, which token nodes refer to.
    pub fn tokens(&self) -> &[PToken] {
        &self.adapted.tokens
    }

    pub fn comments(&self) -> &[Comment] {
        &self.adapted.comments
    }

    pub fn errors(&self) -> &[ParseError] {
        &self.errors
    }

    pub fn names(&self) -> &Names {
        &self.names
    }

    /// Brackets that didn't balance, which the parser saw fixed.
    pub fn bracket_errors(&self) -> &[crate::balance::BracketError] {
        &self.adapted.bracket_errors
    }

    /// The token of a token node.
    pub fn token(&self, id: NodeId) -> Option<PToken> {
        self.tree.token(id).map(|i| self.adapted.tokens[i as usize])
    }

    pub fn token_text(&self, token: PToken) -> &str {
        self.adapted.token_text(token)
    }

    /// For a word's token node, its interned name.
    pub fn name(&self, id: NodeId) -> Option<NameId> {
        self.tree
            .token(id)
            .and_then(|i| self.names_of_tokens[i as usize])
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ParseErrorKind {
    TooFewOperands,
    TooManyOperands,
}

/// An operator with the wrong number of operands.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ParseError {
    pub kind: ParseErrorKind,
    pub op: Operator,
    /// The least or most operands allowed.
    pub bound: u32,
    pub operands: u32,
    pub span: Span,
}

impl ParseError {
    /// The message, as Kotlin Temper words it.
    pub fn message(&self) -> String {
        let (op, bound, got) = (self.op, self.bound, self.operands);
        match self.kind {
            ParseErrorKind::TooFewOperands => {
                format!("Operator {op:?} expects at least {bound} operands but got {got}")
            }
            ParseErrorKind::TooManyOperands => {
                format!("Operator {op:?} expects at most {bound} operands but got {got}")
            }
        }
    }
}

/// Lexes and parses `source`.
pub fn parse(source: &str, kind: SourceKind) -> Parsed {
    let lexed = lex(source, kind);
    let adapted = adapt(source, &lexed);
    let mut parser = Parser {
        stack: vec![Element {
            op: Operator::Root,
            children: Vec::new(),
            span: None,
            open: 0,
        }],
        tree: Tree::new(),
        adapted,
        errors: Vec::new(),
    };
    for i in 0..parser.adapted.tokens.len() {
        parser.place(i as u32);
        parser.add_to_top(i as u32);
    }
    parser.commit_to(1);
    let root = parser.stack.pop().expect("root");
    parser.check_arity(&root);
    let span = root.span.unwrap_or_default();
    let root = parser.tree.add_op(Operator::Root, span, &root.children);
    let mut names = Names::new();
    let names_of_tokens = parser
        .adapted
        .tokens
        .iter()
        .map(|&t| (t.kind == TokenKind::Word).then(|| names.intern(parser.adapted.token_text(t))))
        .collect();
    Parsed {
        tree: parser.tree,
        root,
        lexed,
        adapted: parser.adapted,
        names_of_tokens,
        names,
        errors: parser.errors,
    }
}

/// A partly built node on the stack.
#[derive(Debug)]
struct Element {
    op: Operator,
    children: Vec<NodeId>,
    span: Option<Span>,
    /// Open brackets among its tokens minus close brackets.
    open: i32,
}

/// What [`can_nest`] needs to know about a node, built or not.
#[derive(Clone, Copy, Debug)]
struct Shape {
    op: Operator,
    count: usize,
    /// The child count once the node above it on the stack is committed.
    eventual: usize,
    /// Whether it has an open bracket without its close bracket.
    open: bool,
    /// For `Paren`, whether it's `mixin(...)`.
    mixin: bool,
}

struct Parser {
    stack: Vec<Element>,
    tree: Tree,
    adapted: Adapted,
    errors: Vec<ParseError>,
}

impl Parser {
    fn token(&self, i: u32) -> PToken {
        self.adapted.tokens[i as usize]
    }

    fn text(&self, i: u32) -> &str {
        self.adapted.token_text(self.token(i))
    }

    fn node_text(&self, id: NodeId) -> Option<&str> {
        self.tree.token(id).map(|i| self.text(i))
    }

    fn is_token(&self, id: NodeId) -> bool {
        self.tree.kind(id) == NodeKind::Token
    }

    // Shapes.

    fn needs_close(&self, op: Operator, children: &[NodeId]) -> bool {
        if !op.closer() {
            return false;
        }
        let open: i32 = children
            .iter()
            .filter_map(|&c| self.tree.token(c))
            .map(|i| self.bracket_count(i))
            .sum();
        open > 0
    }

    /// 1 for an open bracket or quote, -1 for a close, else 0.
    fn bracket_count(&self, i: u32) -> i32 {
        let token = self.token(i);
        let text = self.text(i);
        if token.kind == TokenKind::RightDelimiter || is_close_bracket(text) {
            -1
        } else if token.kind == TokenKind::LeftDelimiter || is_open_bracket(text) {
            1
        } else {
            0
        }
    }

    fn is_mixin(&self, op: Operator, children: &[NodeId]) -> bool {
        if op != Operator::Paren || children.len() < 2 || self.node_text(children[1]) != Some("(") {
            return false;
        }
        let callee = children[0];
        let callee_children = self.tree.children(callee);
        self.tree.op(callee) == Some(Operator::Leaf)
            && callee_children.len() == 1
            && self.node_text(callee_children[0]) == Some("mixin")
    }

    fn shape(&self, op: Operator, children: &[NodeId], eventual: usize) -> Shape {
        Shape {
            op,
            count: children.len(),
            eventual,
            open: self.needs_close(op, children),
            mixin: self.is_mixin(op, children),
        }
    }

    /// The shape of the stack element at `i`.
    fn at(&self, i: usize) -> Shape {
        let el = &self.stack[i];
        let count = el.children.len();
        let top = i + 1 == self.stack.len();
        Shape {
            op: el.op,
            count,
            eventual: count + usize::from(!top),
            open: self.element_needs_close(i),
            mixin: self.is_mixin(el.op, &el.children),
        }
    }

    fn at_opt(&self, i: Option<usize>) -> Option<Shape> {
        i.map(|i| self.at(i))
    }

    fn node_shape(&self, id: NodeId) -> Shape {
        let op = self.tree.op(id).unwrap_or(Operator::Leaf);
        let children = self.tree.children(id);
        self.shape(op, children, children.len())
    }

    /// A node that isn't on the stack yet, with or without its token.
    fn candidate(&self, op: Operator, token: Option<u32>) -> Shape {
        let open = match token {
            Some(i) => {
                op.closer()
                    && (is_open_bracket(self.text(i))
                        || self.token(i).kind == TokenKind::LeftDelimiter)
            }
            None => false,
        };
        let count = usize::from(token.is_some());
        Shape {
            op,
            count,
            eventual: count,
            open,
            mixin: false,
        }
    }

    fn element_needs_close(&self, i: usize) -> bool {
        let el = &self.stack[i];
        el.op.closer() && el.open > 0
    }

    /// The innermost open bracket on the stack.
    fn bracket(&self) -> Option<Shape> {
        (0..self.stack.len())
            .rev()
            .find(|&i| self.stack[i].op.is_bracket() && self.element_needs_close(i))
            .map(|i| self.at(i))
    }

    // Stack changes.

    fn add_to_top(&mut self, i: u32) {
        let token = self.token(i);
        let span = Span {
            start: token.start,
            end: token.end,
        };
        let node = self.tree.add_token(i, span);
        let count = self.bracket_count(i);
        let top = self.stack.last_mut().expect("root");
        top.children.push(node);
        top.open += count;
        top.span = Some(top.span.map_or(span, |s| s.cover(span)));
    }

    fn push(&mut self, op: Operator, i: u32) {
        let token = self.token(i);
        let span = Span {
            start: token.start,
            end: token.end,
        };
        self.stack.push(Element {
            op,
            children: Vec::new(),
            span: Some(span),
            open: 0,
        });
    }

    /// Commits elements until the stack has `depth` of them.
    fn commit_to(&mut self, depth: usize) {
        while self.stack.len() > depth.max(1) {
            let el = self.stack.pop().unwrap();
            let span = el.span;
            let node = self.finish(el);
            let parent = self.stack.last_mut().unwrap();
            parent.children.push(node);
            if let Some(span) = span {
                parent.span = Some(parent.span.map_or(span, |s| s.cover(span)));
            }
        }
    }

    /// Makes the top the first operand of a new element for `op`, along
    /// with the last `adopt` children of the element below it.
    fn swap_top_into_new(&mut self, op: Operator, adopt: usize) {
        let old = self.stack.pop().unwrap();
        let mut span = old.span;
        let node = self.finish(old);
        let below = self.stack.last_mut().unwrap();
        let mut children = below.children.split_off(below.children.len() - adopt);
        // The span covers adopted operands too. Kotlin leaves them out.
        for &child in &children {
            let s = self.tree.span(child);
            span = Some(span.map_or(s, |span| span.cover(s)));
        }
        children.push(node);
        // Adopted children are operands, never tokens.
        self.stack.push(Element {
            op,
            children,
            span,
            open: 0,
        });
    }

    /// Writes an element to the tree, after checking its operand count and
    /// tidying string parts.
    fn finish(&mut self, el: Element) -> NodeId {
        self.check_arity(&el);
        let span = el.span.unwrap_or_default();
        let children = match el.op {
            Operator::QuotedGroup => self.tidy_quoted_group(el.children),
            Operator::UnicodeRun => self.tidy_unicode_run(el.children),
            _ => el.children,
        };
        self.tree.add_op(el.op, span, &children)
    }

    fn check_arity(&mut self, el: &Element) {
        // Operands are runs of nodes between tokens.
        let mut arity = 0;
        let mut after_token = true;
        for &child in &el.children {
            if self.is_token(child) {
                after_token = true;
            } else {
                arity += u32::from(after_token);
                after_token = false;
            }
        }
        let info = el.op.info();
        let (kind, bound) = if arity < info.min_arity {
            (ParseErrorKind::TooFewOperands, info.min_arity)
        } else if arity > info.max_arity {
            (ParseErrorKind::TooManyOperands, info.max_arity)
        } else {
            return;
        };
        let span = el.span.unwrap_or_default();
        self.errors.push(ParseError {
            kind,
            op: el.op,
            bound,
            operands: arity,
            span,
        });
    }

    // Parsing.

    /// Decides where token `i` goes, changing the stack so it's added to
    /// the top.
    fn place(&mut self, i: u32) {
        let token = self.token(i);
        // The texts that matter below, so the rest needs no copy.
        let text = match self.text(i) {
            "=" => "=",
            "," => ",",
            "|" => "|",
            ")" => ")",
            "}" => "}",
            "]" => "]",
            ">" => ">",
            _ => "",
        };

        if text == "=" {
            // `name: Type = value` in a class body: the `:` can't be a label.
            for j in (0..self.stack.len()).rev() {
                let op = self.stack[j].op;
                if op.info().precedence < Operator::LowColon.info().precedence || op.closer() {
                    break;
                }
                if op == Operator::LowColon {
                    self.stack[j].op = Operator::HighColon;
                    break;
                }
            }
        }

        if is_follower(text) && self.try_follower(i, text) {
            return;
        }

        let grammatical = matches!(
            token.kind,
            TokenKind::Punctuation
                | TokenKind::Word
                | TokenKind::LeftDelimiter
                | TokenKind::RightDelimiter
        );
        if is_close_bracket(text) && grammatical && self.try_close(text) {
            return;
        }

        if token.kind == TokenKind::RightDelimiter && self.try_close_quote() {
            return;
        }

        // The innermost open bracket stays the same until a strategy
        // changes the stack and succeeds.
        let bracket = self.bracket();
        let ops = Operator::matching_all(self.text(i), token.kind);
        let (separators, prefixes, postfixes, infixes) =
            (ops.separator, ops.prefix, ops.postfix, ops.infix);
        if self.try_separators(i, separators, bracket) {
            return;
        }

        let mut may_infix = token.may_infix;
        if may_infix && !prefixes.is_empty() {
            // Prefer a prefix operator that would complete the top, so in
            // `x - -y` the second `-` is a negation.
            let top = self.stack.len() - 1;
            if self.needs_operand(top) {
                let parent = self.at(top);
                let grand = self.at_opt(top.checked_sub(1));
                if prefixes
                    .iter()
                    .any(|op| can_nest(bracket, grand, parent, self.candidate(op, Some(i))))
                {
                    may_infix = false;
                }
            }
        }
        if may_infix
            && (self.try_after_operand(i, postfixes, bracket)
                || self.try_after_operand(i, infixes, bracket))
        {
            return;
        }
        if token.may_prefix && self.try_prefix(i, prefixes.iter(), bracket) {
            return;
        }
        if token.kind == TokenKind::LeftDelimiter
            && self.try_prefix(i, std::iter::once(Operator::QuotedGroup), bracket)
        {
            return;
        }
        self.place_leaf(i, bracket);
    }

    fn needs_operand(&self, i: usize) -> bool {
        let el = &self.stack[i];
        match el.op.op_type() {
            OpType::Infix | OpType::Prefix => el.children.last().is_none_or(|&c| self.is_token(c)),
            _ => false,
        }
    }

    /// A `,` or `|` continuing an operator like `extends A, B`.
    fn try_follower(&mut self, i: u32, text: &str) -> bool {
        let token = self.token(i);
        for j in (0..self.stack.len()).rev() {
            if self.element_needs_close(j) {
                break;
            }
            let el = &self.stack[j];
            let top = j + 1 == self.stack.len();
            // Ready for a follower when an operand comes last, or will.
            let ready = !top || el.children.last().is_some_and(|&c| !self.is_token(c));
            if !ready {
                continue;
            }
            // Past the operator's own token (asked last, as a long list's
            // element has many children).
            let may_follow = token.kind == TokenKind::Punctuation
                && el.op.info().follower == Some(text)
                && el.children.iter().any(|&c| self.is_token(c));
            if may_follow {
                self.commit_to(j + 1);
                return true;
            }
        }
        false
    }

    /// A close bracket for the innermost open bracket.
    fn try_close(&mut self, text: &str) -> bool {
        for j in (0..self.stack.len()).rev() {
            let Some(partner) = self.stack[j].op.partner() else {
                continue;
            };
            if !self.element_needs_close(j) {
                // Closed elements stay, in case they're a left operand.
                continue;
            }
            if partner == text {
                self.commit_to(j + 1);
                return true;
            }
            // Kotlin means to look past other open brackets for closers
            // other than `>`, as for the `}` in `{ f(x; }`, but it stops at
            // the innermost. A bug, kept for now so comparisons stay exact.
            break;
        }
        false
    }

    /// A close quote for the innermost quoted group, past anything without
    /// an open bracket. Kotlin looks only one level down from a leaf, so in
    /// a string ending in a scriptlet line like `: f`, the quote joins `f`.
    fn try_close_quote(&mut self) -> bool {
        let mut j = self.stack.len();
        while j > 0 {
            j -= 1;
            let op = self.stack[j].op;
            if op == Operator::QuotedGroup {
                self.commit_to(j + 1);
                return true;
            }
            if self.element_needs_close(j) {
                break;
            }
        }
        false
    }

    fn try_separators(&mut self, i: u32, ops: Matches, bracket: Option<Shape>) -> bool {
        for op in ops.iter() {
            let mut left = None;
            let mut right = None;
            let outer = self.candidate(op, None);
            let inner = self.candidate(op, Some(i));
            for j in (0..self.stack.len()).rev() {
                if self.stack[j].op == op {
                    // Continue the same separator.
                    self.commit_to(j + 1);
                    return true;
                }
                let el = self.at(j);
                let parent = self.at_opt(j.checked_sub(1));
                // A separator without a left operand.
                if can_nest(bracket, parent, el, inner) {
                    right = Some(j);
                }
                if el.open {
                    break;
                }
                // Or el as its left operand.
                if let Some(parent) = parent {
                    let grand = self.at_opt(j.checked_sub(2));
                    if can_nest(bracket, Some(parent), outer, el)
                        && can_nest(bracket, grand, parent, outer)
                    {
                        left = Some(j);
                    }
                }
            }
            if let Some(left) = left {
                self.commit_to(left + 1);
                let adopt = self.adoptable(op);
                self.swap_top_into_new(op, adopt);
                return true;
            } else if let Some(right) = right {
                self.commit_to(right + 1);
                self.push(op, i);
                return true;
            }
        }
        false
    }

    /// How many of the operands just before the top a new separator `op`
    /// should take too, as it would have had it been seen before them.
    fn adoptable(&self, op: Operator) -> usize {
        let len = self.stack.len();
        if len < 2 {
            return 0;
        }
        let below = &self.stack[len - 2];
        let bracket = self.bracket();
        let mut adopt = 0;
        while adopt < below.children.len() {
            let sibling = below.children[below.children.len() - adopt - 1];
            if self.is_token(sibling) {
                break;
            }
            let count = below.children.len() - adopt;
            let without = Shape {
                op: below.op,
                count,
                eventual: count,
                open: self.needs_close(below.op, &below.children[..count]),
                mixin: self.is_mixin(below.op, &below.children[..count]),
            };
            let parent = Shape {
                op,
                count: adopt,
                eventual: adopt + 1,
                open: false,
                mixin: false,
            };
            if can_nest(bracket, Some(without), parent, self.node_shape(sibling)) {
                adopt += 1;
            } else {
                break;
            }
        }
        adopt
    }

    /// An infix or postfix operator, taking the top as its left operand.
    fn try_after_operand(&mut self, i: u32, ops: Matches, bracket: Option<Shape>) -> bool {
        let token = self.token(i);
        for op in ops.iter() {
            if token.may_bracket != op.closer() {
                // Like `<` as less-than rather than an angle bracket.
                continue;
            }
            let candidate = self.candidate(op, None);
            let mut left = None;
            for j in (1..self.stack.len()).rev() {
                if self.element_needs_close(j) {
                    break;
                }
                let el = self.at(j);
                let parent = self.at(j - 1);
                let grand = self.at_opt(j.checked_sub(2));
                if can_nest(bracket, Some(parent), candidate, el)
                    && can_nest(bracket, grand, parent, candidate)
                {
                    left = Some(j);
                }
            }
            if let Some(left) = left {
                self.commit_to(left + 1);
                self.swap_top_into_new(op, 0);
                return true;
            }
        }
        false
    }

    /// A prefix operator, starting a new element.
    fn try_prefix(
        &mut self,
        i: u32,
        ops: impl Iterator<Item = Operator>,
        bracket: Option<Shape>,
    ) -> bool {
        for op in ops {
            let candidate = self.candidate(op, Some(i));
            for j in (0..self.stack.len()).rev() {
                let el = self.at(j);
                if el.op.op_type() != OpType::Postfix
                    && can_nest(bracket, self.at_opt(j.checked_sub(1)), el, candidate)
                {
                    self.commit_to(j + 1);
                    self.push(op, i);
                    return true;
                }
                if el.open {
                    break;
                }
            }
        }
        false
    }

    /// A value token, which starts or continues a leaf.
    fn place_leaf(&mut self, i: u32, bracket: Option<Shape>) {
        let candidate = self.candidate(Operator::Leaf, Some(i));
        let mut close_to = None;
        for j in (0..self.stack.len()).rev() {
            let el = self.at(j);
            if el.op.op_type() != OpType::Postfix
                && can_nest(bracket, self.at_opt(j.checked_sub(1)), el, candidate)
            {
                break;
            }
            close_to = Some(j);
        }
        if let Some(close_to) = close_to {
            self.commit_to(close_to);
        }
        let top = self.stack.len() - 1;
        if self.stack[top].op != Operator::Leaf {
            self.push(Operator::Leaf, i);
        } else if top >= 1 {
            // A leaf right after a prefix operator's full operand is that
            // operator's next operand, as in `@A b`.
            let below = &self.stack[top - 1];
            let info = below.op.info();
            if info.op_type == OpType::Prefix
                && below.op != Operator::Root
                && !info.closer
                && (below.children.len() as u32) < info.max_arity
            {
                self.commit_to(top);
                self.push(Operator::Leaf, i);
            }
        }
    }

    // Tidying strings, as Kotlin's `PostProcessCst.kt`.

    fn token_kind(&self, id: NodeId) -> Option<TokenKind> {
        self.tree.token(id).map(|i| self.token(i).kind)
    }

    /// `${}`, which only separates string parts.
    fn is_empty_hole(&self, id: NodeId) -> bool {
        let children = self.tree.children(id);
        self.tree.op(id) == Some(Operator::DollarCurly)
            && children.len() == 2
            && self.node_text(children[0]) == Some("${")
            && self.node_text(children[1]) == Some("}")
    }

    /// A leaf of only string content.
    fn is_chars_leaf(&self, id: NodeId) -> bool {
        self.tree.op(id) == Some(Operator::Leaf)
            && self
                .tree
                .children(id)
                .iter()
                .all(|&c| self.token_kind(c) == Some(TokenKind::QuotedString))
    }

    /// Drops empty holes and joins adjacent string content, keeping escapes
    /// separate.
    fn tidy_quoted_group(&mut self, mut children: Vec<NodeId>) -> Vec<NodeId> {
        let is_kind = |p: &Parser, c: NodeId, kind| p.token_kind(c) == Some(kind);
        let Some(left) = children
            .iter()
            .position(|&c| is_kind(self, c, TokenKind::LeftDelimiter))
        else {
            return children;
        };
        let right = children
            .iter()
            .rposition(|&c| is_kind(self, c, TokenKind::RightDelimiter));
        let right = match right {
            Some(0) => children.len(),
            Some(right) => right,
            None => return children,
        };
        let has_chars = children
            .get(left + 1..right)
            .unwrap_or(&[])
            .iter()
            .any(|&c| {
                self.tree.op(c) == Some(Operator::Leaf)
                    && self
                        .tree
                        .children(c)
                        .iter()
                        .any(|&t| self.token_kind(t) == Some(TokenKind::QuotedString))
            });
        if !has_chars {
            return children;
        }
        children.retain(|&c| !self.is_empty_hole(c));
        let mut i = children.len();
        while i > 1 {
            i -= 1;
            let (before, at) = (children[i - 1], children[i]);
            if self.is_chars_leaf(at) && self.is_chars_leaf(before) {
                let mut parts = self.tree.children(before).to_vec();
                parts.extend_from_slice(self.tree.children(at));
                let span = self.tree.span(before).cover(self.tree.span(at));
                children[i - 1] = self.tree.add_op(Operator::Leaf, span, &parts);
                children.remove(i);
            }
        }
        for child in children.iter_mut() {
            let parts = self.tree.children(*child);
            if parts.len() > 1 && self.is_chars_leaf(*child) {
                let parts = parts.to_vec();
                let merged = self.merge_chars(&parts);
                *child = self
                    .tree
                    .add_op(Operator::Leaf, self.tree.span(*child), &merged);
            }
        }
        children
    }

    /// Joins runs of string content tokens other than escapes.
    fn merge_chars(&mut self, parts: &[NodeId]) -> Vec<NodeId> {
        let mut out = Vec::new();
        let mut start = 0;
        while start < parts.len() {
            let escape =
                |p: &Parser, c: NodeId| p.node_text(c).is_some_and(|t| t.starts_with('\\'));
            let mut end = start + 1;
            if !escape(self, parts[start]) {
                while end < parts.len() && !escape(self, parts[end]) {
                    end += 1;
                }
            }
            if end == start + 1 {
                out.push(parts[start]);
            } else {
                out.push(self.join_tokens(&parts[start..end]));
            }
            start = end;
        }
        out
    }

    /// A new token node for several tokens' text, spanning them.
    fn join_tokens(&mut self, parts: &[NodeId]) -> NodeId {
        let text: String = parts.iter().filter_map(|&c| self.node_text(c)).collect();
        let first = self.token(self.tree.token(parts[0]).unwrap());
        let span = parts
            .iter()
            .fold(self.tree.span(parts[0]), |s, &c| s.cover(self.tree.span(c)));
        let text_start = self.adapted.text.len() as u32;
        self.adapted.text.push_str(&text);
        let token = PToken {
            start: span.start,
            end: span.end,
            text_start,
            text_end: self.adapted.text.len() as u32,
            ..first
        };
        let i = self.adapted.tokens.len() as u32;
        self.adapted.tokens.push(token);
        self.tree.add_token(i, span)
    }

    /// In `\u{...}` with holes, drops empty holes and joins hex digits split
    /// by them.
    fn tidy_unicode_run(&mut self, children: Vec<NodeId>) -> Vec<NodeId> {
        let middle = children.get(1).copied().filter(|&c| !self.is_token(c));
        let comma = middle.filter(|&c| self.tree.op(c) == Some(Operator::Comma));
        let inner = match comma {
            Some(comma) => self.tree.children(comma).to_vec(),
            None => children.clone(),
        };
        if !inner.iter().any(|&c| self.is_empty_hole(c)) {
            return children;
        }
        let (Some(&first), Some(&last)) = (children.first(), children.last()) else {
            return children;
        };
        let mut out = vec![first];
        match comma {
            Some(comma) if children.len() == 3 => {
                let parts = self.tidy_unicode_parts(&inner);
                let span = self.tree.span(comma);
                out.push(self.tree.add_op(Operator::Comma, span, &parts));
            }
            Some(_) => return children,
            None if children.len() >= 2 => {
                out.extend(self.tidy_unicode_parts(&children[1..children.len() - 1]));
            }
            None => return children,
        }
        out.push(last);
        out
    }

    fn tidy_unicode_parts(&mut self, parts: &[NodeId]) -> Vec<NodeId> {
        let mut out = Vec::new();
        let mut leaf: Vec<NodeId> = Vec::new();
        let hex_like = |p: &Parser, c: NodeId| {
            p.node_text(c)
                .and_then(|t| t.chars().next())
                .is_some_and(|c| {
                    c.is_ascii_digit() || crate::id_parts::contains(crate::id_parts::START, c)
                })
        };
        for &part in parts {
            if self.is_chars_leaf(part) {
                let tokens = self.tree.children(part).to_vec();
                let mut copy_from = 0;
                if let (Some(&last), Some(&current)) = (leaf.last(), tokens.first()) {
                    if hex_like(self, last) && hex_like(self, current) {
                        // Spanning both parts. Kotlin keeps the first's span.
                        let joined = self.join_tokens(&[last, current]);
                        *leaf.last_mut().unwrap() = joined;
                        copy_from = 1;
                    }
                }
                leaf.extend_from_slice(&tokens[copy_from..]);
            } else if self.is_empty_hole(part) {
            } else {
                self.flush_leaf(&mut leaf, &mut out);
                out.push(part);
            }
        }
        self.flush_leaf(&mut leaf, &mut out);
        out
    }

    fn flush_leaf(&mut self, leaf: &mut Vec<NodeId>, out: &mut Vec<NodeId>) {
        if let Some(&first) = leaf.first() {
            let span = leaf
                .iter()
                .fold(self.tree.span(first), |s, &c| s.cover(self.tree.span(c)));
            out.push(self.tree.add_op(Operator::Leaf, span, leaf));
            leaf.clear();
        }
    }
}

const EXTENDS_COMMA: [Operator; 4] = [
    Operator::ExtendsComma,
    Operator::ImplementsComma,
    Operator::ForbidsComma,
    Operator::SupportsComma,
];
const EXTENDS_NO_COMMA: [Operator; 4] = [
    Operator::ExtendsNoComma,
    Operator::ImplementsNoComma,
    Operator::ForbidsNoComma,
    Operator::SupportsNoComma,
];

fn is_block_like(op: Option<Operator>) -> bool {
    use Operator as O;
    matches!(
        op,
        None | Some(
            O::Root | O::Semi | O::SemiSemi | O::SemiSemiSemi | O::Arrow | O::Curly | O::CurlyGroup
        )
    )
}

/// Whether `child` may be an operand of `parent`, with `grand` below that
/// and `bracket` the innermost open bracket. Mostly precedence, plus cases
/// that make Temper's flexible syntax work.
fn can_nest(bracket: Option<Shape>, grand: Option<Shape>, parent: Shape, child: Shape) -> bool {
    use Operator as O;
    let (p, c) = (parent.op, child.op);
    if c == O::Root {
        return false;
    }
    // `mixin("name"):` between statements.
    if p.info().text == ":" && child.mixin {
        return p == O::SelectivePostColon;
    }
    // Labels only in blocks and at the top level.
    if p == O::LowColon && !is_block_like(grand.map(|g| g.op)) {
        return false;
    }
    // `label:` but not `let x:`, which becomes a type annotation.
    if p == O::LowColon && parent.count == 0 && parent.eventual == 0 {
        return match c {
            O::DollarCurly => true,
            O::Leaf => child.count == 1,
            _ => false,
        };
    }
    if p == O::SelectivePostColon {
        return false;
    }
    // `case` in a match block, or left of `->`.
    if c == O::PreCase {
        return match p {
            O::Curly | O::Comma => true,
            O::Semi => grand.is_some_and(|g| g.op == O::Curly),
            O::ThinArrow => parent.count == 0,
            _ => false,
        };
    }
    // `extends A, B` but not inside `<...>` or a comma list.
    if EXTENDS_COMMA.contains(&c)
        && (p == O::Comma || bracket.is_some_and(|b| b.op == O::Angle && b.count >= 2))
    {
        return false;
    }
    if EXTENDS_COMMA.contains(&c) && EXTENDS_NO_COMMA.contains(&p) {
        return false;
    }
    // Open brackets hold anything; closed ones nothing.
    if p.closer() && parent.count > 0 {
        return parent.open;
    }
    // `new C(x)` calls the whole `new C`.
    if c == O::New && child.count > 0 && matches!(p, O::Paren | O::Curly) && parent.count == 0 {
        return true;
    }
    // `@A() (x)` decorates `(x)` rather than calling `A()`.
    if grand.is_some_and(|g| g.op == O::At && g.eventual == 2) && p == O::Paren && c == O::Paren {
        return false;
    }
    // `let f(): ReturnType { body }`.
    if p == O::Curly && c == O::HighColon && parent.count == 0 && child.count >= 2 {
        return true;
    }
    // `class C extends Supers { body }`.
    if p == O::Curly && EXTENDS_COMMA.contains(&c) && parent.count == 0 && child.count >= 2 {
        return true;
    }
    if EXTENDS_COMMA.contains(&p) && c == O::Curly {
        return false;
    }
    // In `new C<T>`, `<T>` applies to `C`.
    if p == O::Angle && c == O::New && parent.count == 0 && child.count != 0 {
        return false;
    }
    let (pp, cp) = (p.info().precedence, c.info().precedence);
    pp < cp
        || (pp == cp
            && (!p.info().right_assoc || (c.op_type() == OpType::Infix && child.count == 0)))
}
