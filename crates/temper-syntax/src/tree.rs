//! The tree: nodes in a few flat arrays, addressed by [`NodeId`].
//!
//! The parser builds operator nodes, whose children are operand nodes and
//! the operator's own tokens, like `(Plus (Leaf 1) + (Leaf 2))`. A `Leaf`
//! node holds a run of value tokens: one for `1`, several for `let x`.
//! Children are stored contiguously, so a node's children are a slice.
//! Parse trees are never changed. Later steps rewrite a node in place
//! ([`Tree::replace`]): its new children are appended as a new range, and
//! the old range becomes garbage, freed with the tree.
//!
//! The grammar step ([`ast`](crate::ast)) builds a second tree of the same
//! type from the parse tree, with the node kinds after `Token`: calls,
//! blocks, declarations, functions, names and values.

use crate::operator::Operator;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct NodeId(pub u32);

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum NodeKind {
    /// An operator applied to its children.
    Op(Operator),
    /// A token; the node's data is its index in the parser's tokens.
    Token,
    /// Statements in order; the value is the last one's.
    Block,
    /// A call: the callee, then arguments. Named arguments are a symbol
    /// value followed by the argument.
    Call,
    /// A declaration: the declared name, then symbol and value pairs like
    /// `\type` and the type.
    Decl,
    /// A quoted tree, `\(...)`.
    Esc,
    /// A function: parameters, then its body.
    Fun,
    /// A name being declared; the data is its [`NameId`](crate::names::NameId).
    LeftName,
    /// A name being used.
    RightName,
    /// A name of a builtin, never shadowed, like `.` or `[]`.
    BuiltinName,
    /// A value; the data is its index in the values.
    Value,
    /// Something malformed, reported in a diagnostic.
    Error,
    // The core constructs that macro expansion in `temper-frontend` reduces
    // everything to, along with `Block`, `Call`, names, `Value` and `Error`.
    // Layouts are documented there (`temper_frontend::core`).
    Let,
    Fn,
    If,
    Loop,
    Break,
    Continue,
    Return,
    Yield,
    Await,
    Orelse,
    Fail,
    Assign,
    Class,
}

/// A byte range in the source.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Span {
    pub start: u32,
    pub end: u32,
}

impl Span {
    pub fn cover(self, other: Span) -> Span {
        Span {
            start: self.start.min(other.start),
            end: self.end.max(other.end),
        }
    }
}

#[cfg(test)]
thread_local! {
    static KINDS_READ: std::cell::Cell<u64> = const { std::cell::Cell::new(0) };
}

/// How many node kinds ([`Tree::kind`]) this thread has read: a measure of
/// the parser's work, nearly all of which asks nodes' kinds, for tests
/// that bound it without timing it.
#[cfg(test)]
pub(crate) fn kinds_read() -> u64 {
    KINDS_READ.with(std::cell::Cell::get)
}

#[derive(Clone, Debug, Default)]
pub struct Tree {
    kinds: Vec<NodeKind>,
    spans: Vec<Span>,
    first_child: Vec<u32>,
    child_count: Vec<u32>,
    data: Vec<u32>,
    children: Vec<NodeId>,
}

impl Tree {
    pub fn new() -> Tree {
        Tree::default()
    }

    pub fn len(&self) -> usize {
        self.kinds.len()
    }

    pub fn is_empty(&self) -> bool {
        self.kinds.is_empty()
    }

    fn add(&mut self, kind: NodeKind, span: Span, data: u32, children: &[NodeId]) -> NodeId {
        let id = NodeId(self.kinds.len() as u32);
        self.kinds.push(kind);
        self.spans.push(span);
        self.first_child.push(self.children.len() as u32);
        self.child_count.push(children.len() as u32);
        self.data.push(data);
        self.children.extend_from_slice(children);
        id
    }

    /// Adds a node of any kind.
    pub fn add_node(
        &mut self,
        kind: NodeKind,
        span: Span,
        data: u32,
        children: &[NodeId],
    ) -> NodeId {
        self.add(kind, span, data, children)
    }

    pub fn add_token(&mut self, token: u32, span: Span) -> NodeId {
        self.add(NodeKind::Token, span, token, &[])
    }

    pub fn add_op(&mut self, op: Operator, span: Span, children: &[NodeId]) -> NodeId {
        self.add(NodeKind::Op(op), span, 0, children)
    }

    /// Rewrites a node in place, as a macro expansion does: a new kind,
    /// data and children, appended as a new range. Its old children stay
    /// in the tree, unreachable from it.
    pub fn replace(&mut self, id: NodeId, kind: NodeKind, data: u32, children: &[NodeId]) {
        let i = id.0 as usize;
        self.kinds[i] = kind;
        self.data[i] = data;
        self.set_children(id, children);
    }

    /// Gives a node new children, appended as a new range.
    pub fn set_children(&mut self, id: NodeId, children: &[NodeId]) {
        let i = id.0 as usize;
        self.first_child[i] = self.children.len() as u32;
        self.child_count[i] = children.len() as u32;
        self.children.extend_from_slice(children);
    }

    pub fn set_span(&mut self, id: NodeId, span: Span) {
        self.spans[id.0 as usize] = span;
    }

    pub fn kind(&self, id: NodeId) -> NodeKind {
        #[cfg(test)]
        KINDS_READ.with(|n| n.set(n.get() + 1));
        self.kinds[id.0 as usize]
    }

    /// The operator, or `None` for a token.
    pub fn op(&self, id: NodeId) -> Option<Operator> {
        match self.kind(id) {
            NodeKind::Op(op) => Some(op),
            _ => None,
        }
    }

    pub fn span(&self, id: NodeId) -> Span {
        self.spans[id.0 as usize]
    }

    pub fn data(&self, id: NodeId) -> u32 {
        self.data[id.0 as usize]
    }

    /// For a token node, its index in the parser's tokens.
    pub fn token(&self, id: NodeId) -> Option<u32> {
        (self.kind(id) == NodeKind::Token).then(|| self.data(id))
    }

    pub fn children(&self, id: NodeId) -> &[NodeId] {
        let i = id.0 as usize;
        let first = self.first_child[i] as usize;
        &self.children[first..first + self.child_count[i] as usize]
    }
}
